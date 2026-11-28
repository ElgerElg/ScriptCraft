package com.scriptcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.scriptcraft.mixin.LoadingOverlayAccessor;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Единый рендерер кастомных загрузочных экранов ScriptCraft с плавным прогресс-баром и процентами (0%..100%)
 * и наивысшим приоритетом поверх ForgeLoadingOverlay и любых сторонних модов (GameRendererMixin):
 *  1) При старте игры и перезагрузке ресурспаков / F3+T / запекании моделей (LoadingOverlay + ForgeLoadingOverlay)
 *  2) При входе на сервер, синхронизации кэша/реестров и загрузке мира (включая плавный довод до 100% в мире!)
 */
@OnlyIn(Dist.CLIENT)
public final class ClientLoadScreenRenderer {
    private static final int JOIN_IDLE = 0;
    private static final int JOIN_CONNECTING = 1;
    private static final int JOIN_POST_WORLD_FADE = 2;

    private static final long POST_JOIN_HOLD_MS = 500L;
    private static final long POST_JOIN_FADE_MS = 650L;

    private static volatile int joinPhase = JOIN_IDLE;
    private static volatile long postJoinStartMs = 0L;
    private static volatile float serverJoinProgress = 0.08f;
    private static volatile String serverJoinStage = "";
    private static volatile String serverJoinDetail = "";
    private static volatile String reloadCustomReason = "";
    private static float smoothedJoinProgress = 0.05f;

    // Отслеживание оверлея перезагрузки ресурсов (в т.ч. ForgeLoadingOverlay)
    private static boolean overlayRenderedDirectlyThisFrame = false;
    private static Overlay trackedOverlay = null;
    private static float trackedOverlayProgress = 0.0f;
    private static long trackedOverlayDoneMs = -1L;

    private ClientLoadScreenRenderer() {
    }

    public static void beginServerConnect() {
        joinPhase = JOIN_CONNECTING;
        postJoinStartMs = 0L;
        serverJoinProgress = 0.10f;
        smoothedJoinProgress = 0.06f;
        serverJoinStage = isRu() ? "Подключение к серверу..." : "Connecting to server...";
        serverJoinDetail = "";
    }

    public static void ensureServerConnectStarted() {
        if (joinPhase == JOIN_IDLE) {
            beginServerConnect();
        }
    }

    public static void onWorldLoggedIn() {
        // Даже если локальный вход прошёл за 50 мс, плавно доводим шкалу до 100% и растворяем карточку
        if (joinPhase == JOIN_IDLE) {
            smoothedJoinProgress = Math.max(smoothedJoinProgress, 0.72f);
        }
        joinPhase = JOIN_POST_WORLD_FADE;
        postJoinStartMs = Util.getMillis();
        serverJoinProgress = 1.0f;
        serverJoinStage = isRu()
                ? "Синхронизация завершена — добро пожаловать!"
                : "Sync complete — entering world!";
        serverJoinDetail = "";
    }

    public static void resetJoinState() {
        joinPhase = JOIN_IDLE;
        postJoinStartMs = 0L;
        serverJoinProgress = 0.08f;
        smoothedJoinProgress = 0.05f;
        serverJoinStage = "";
        serverJoinDetail = "";
    }

    public static void markOverlayRenderedDirectly() {
        overlayRenderedDirectlyThisFrame = true;
    }

    public static void updateJoinProgress(float target01, String stageRu, String stageEn, String detail) {
        ensureServerConnectStarted();
        serverJoinProgress = Mth.clamp(target01, 0.0f, 1.0f);
        serverJoinStage = isRu() ? stageRu : stageEn;
        serverJoinDetail = detail == null ? "" : detail;
    }

    public static void setReloadReason(String reasonRu, String reasonEn) {
        reloadCustomReason = isRu() ? reasonRu : reasonEn;
    }

    public static void clearReloadReason() {
        reloadCustomReason = "";
    }

    public static float stepServerJoinProgress(float minFloor, float maxCeil, float deltaTick) {
        float target = Math.max(minFloor, Math.min(maxCeil, serverJoinProgress));
        if (smoothedJoinProgress < target) {
            smoothedJoinProgress = Math.min(target, smoothedJoinProgress + Math.max(0.006f, (target - smoothedJoinProgress) * 0.16f));
        } else if (smoothedJoinProgress < maxCeil) {
            smoothedJoinProgress = Math.min(maxCeil, smoothedJoinProgress + deltaTick * 0.0035f);
        }
        return Mth.clamp(smoothedJoinProgress, 0.0f, 1.0f);
    }

    public static String currentJoinStage(String fallbackVanillaStatus) {
        if (serverJoinStage != null && !serverJoinStage.isBlank()) {
            return serverJoinStage;
        }
        return fallbackVanillaStatus == null ? "" : fallbackVanillaStatus;
    }

    public static String currentJoinDetail() {
        return serverJoinDetail == null ? "" : serverJoinDetail;
    }

    public static String currentReloadStage(float progress) {
        if (reloadCustomReason != null && !reloadCustomReason.isBlank()) {
            return reloadCustomReason;
        }
        if (isRu()) {
            if (progress < 0.25f) return "Чтение пакетов ресурсов и кэша ScriptCraft...";
            if (progress < 0.65f) return "Запекание моделей, текстурных атласов и 3D-геометрии...";
            if (progress < 0.90f) return "Инициализация звуков .ogg и шейдеров...";
            return "Завершение загрузки...";
        } else {
            if (progress < 0.25f) return "Scanning resource packs & ScriptCraft cache...";
            if (progress < 0.65f) return "Baking block/item models, atlases & 3D geometry...";
            if (progress < 0.90f) return "Initializing .ogg audio & shaders...";
            return "Finalizing reload...";
        }
    }

    /**
     * Вызывается из GameRendererMixin с наивысшим приоритетом (20000) непосредственно перед GuiGraphics.flush():
     * перекрывает любые экраны загрузки других модов (ForgeLoadingOverlay, FancyMenu, FastLoad и др.).
     */
    public static void renderTopMostHook(Minecraft mc, MultiBufferSource.BufferSource buffers, float partialTick) {
        boolean directOverlay = overlayRenderedDirectlyThisFrame;
        overlayRenderedDirectlyThisFrame = false;

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int mouseX = (int) (mc.mouseHandler.xpos() * (double) width / (double) Math.max(1, mc.getWindow().getScreenWidth()));
        int mouseY = (int) (mc.mouseHandler.ypos() * (double) height / (double) Math.max(1, mc.getWindow().getScreenHeight()));

        Overlay overlay = mc.getOverlay();
        if (overlay instanceof LoadingOverlay lo) {
            // Если активен оверлей перезагрузки ресурсов, он главнее пост-логин анимации
            if (joinPhase == JOIN_POST_WORLD_FADE) {
                joinPhase = JOIN_IDLE;
            }
            if (directOverlay) {
                // LoadingOverlayMixin уже отрисовал карточку в этом кадре
                return;
            }
            renderFallbackLoadingOverlay(mc, buffers, lo, width, height);
            return;
        } else {
            trackedOverlay = null;
            trackedOverlayDoneMs = -1L;
        }

        Screen screen = mc.screen;
        if (screen instanceof ConnectScreen) {
            ensureServerConnectStarted();
            String vanillaStatus = (screen instanceof ConnectScreenBridge bridge) ? bridge.scriptcraft$getStatusString() : "";
            float progress = stepServerJoinProgress(0.10f, 0.86f, partialTick);
            String title = isRu() ? "ВХОД НА СЕРВЕР" : "JOINING SERVER";
            String stage = currentJoinStage(vanillaStatus);
            String rawDetail = currentJoinDetail();
            final String detail = (rawDetail.isEmpty() && !vanillaStatus.isEmpty() && !vanillaStatus.equals(stage))
                    ? vanillaStatus : rawDetail;
            drawTopMostLayer(mc, buffers, g -> {
                renderCard(g, width, height, progress, title, stage, detail, 1.0f, true);
                if (screen instanceof ConnectScreenBridge bridge) {
                    g.pose().pushPose();
                    g.pose().translate(0f, 0f, 20f);
                    bridge.scriptcraft$renderButtonsOnly(g, mouseX, mouseY, partialTick);
                    g.pose().popPose();
                }
            });
            return;
        }

        if (screen instanceof ReceivingLevelScreen) {
            ensureServerConnectStarted();
            float progress = stepServerJoinProgress(0.90f, 0.99f, partialTick);
            String title = isRu() ? "ЗАГРУЗКА МИРА" : "LOADING WORLD";
            String stage = isRu()
                    ? "Генерация ландшафта и синхронизация сущностей..."
                    : "Receiving terrain chunks & syncing entities...";
            drawTopMostLayer(mc, buffers, g -> renderCard(g, width, height, progress, title, stage, "", 1.0f, false));
            return;
        }

        if (joinPhase == JOIN_POST_WORLD_FADE) {
            // Если игрок открыл меню смерти/отключения — сразу убираем оверлей
            if (mc.level == null) {
                joinPhase = JOIN_IDLE;
                return;
            }
            long elapsed = Math.max(0L, Util.getMillis() - postJoinStartMs);
            if (elapsed >= POST_JOIN_HOLD_MS + POST_JOIN_FADE_MS) {
                joinPhase = JOIN_IDLE;
                return;
            }
            float progress;
            float alpha;
            if (elapsed < POST_JOIN_HOLD_MS) {
                float t = (float) elapsed / (float) POST_JOIN_HOLD_MS;
                smoothedJoinProgress = Mth.lerp(Math.min(1.0f, 0.22f + t * 0.5f), smoothedJoinProgress, 1.0f);
                progress = Mth.clamp(Math.max(smoothedJoinProgress, 0.85f + 0.15f * t), 0.0f, 1.0f);
                alpha = 1.0f;
            } else {
                progress = 1.0f;
                float fadeT = (float) (elapsed - POST_JOIN_HOLD_MS) / (float) POST_JOIN_FADE_MS;
                float inv = 1.0f - Mth.clamp(fadeT, 0.0f, 1.0f);
                alpha = inv * inv;
            }
            String title = isRu() ? "ВХОД НА СЕРВЕР" : "JOINING SERVER";
            String stage = currentJoinStage("");
            drawTopMostLayer(mc, buffers, g -> renderCard(g, width, height, progress, title, stage, "", alpha, false));
        } else if (joinPhase == JOIN_CONNECTING && screen != null) {
            // Защита на случай, если сторонний мод (например FancyMenu) подменил ConnectScreen своим классом Screen!
            String screenName = screen.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
            if (screenName.contains("connect") || screenName.contains("login") || screenName.contains("load") || screenName.contains("receiving")) {
                float progress = stepServerJoinProgress(0.12f, 0.92f, partialTick);
                String title = isRu() ? "ВХОД НА СЕРВЕР" : "JOINING SERVER";
                String stage = currentJoinStage("");
                String detail = currentJoinDetail();
                drawTopMostLayer(mc, buffers, g -> renderCard(g, width, height, progress, title, stage, detail, 1.0f, true));
            } else if (screenName.contains("disconnected") || screenName.contains("multiplayer") || screenName.contains("title")) {
                joinPhase = JOIN_IDLE;
            }
        }
    }

    private static void renderFallbackLoadingOverlay(
            Minecraft mc,
            MultiBufferSource.BufferSource buffers,
            LoadingOverlay lo,
            int width,
            int height
    ) {
        if (trackedOverlay != lo) {
            trackedOverlay = lo;
            trackedOverlayProgress = 0.02f;
            trackedOverlayDoneMs = -1L;
        }
        LoadingOverlayAccessor acc = (LoadingOverlayAccessor) lo;
        ReloadInstance reload = acc.scriptcraft$getReload();
        long now = Util.getMillis();

        float actual = reload != null ? reload.getActualProgress() : 0.5f;
        trackedOverlayProgress = Mth.clamp(trackedOverlayProgress * 0.88f + actual * 0.12f, 0.0f, 1.0f);

        long baseFadeOut = acc.scriptcraft$getFadeOutStart();
        if (baseFadeOut > -1L) {
            trackedOverlayDoneMs = baseFadeOut;
        } else if (reload != null && reload.isDone() && trackedOverlayDoneMs == -1L) {
            trackedOverlayDoneMs = now;
        }

        float shownProgress = trackedOverlayDoneMs > -1L ? 1.0f : trackedOverlayProgress;
        float alpha = 1.0f;
        if (trackedOverlayDoneMs > -1L) {
            float fadeFrac = (float) (now - trackedOverlayDoneMs) / 750.0f;
            alpha = 1.0f - Mth.clamp(fadeFrac, 0.0f, 1.0f);
        }
        if (alpha <= 0.01f) {
            return;
        }
        String title = isRu() ? "ЗАГРУЗКА РЕСУРСОВ" : "LOADING RESOURCES";
        String stage = currentReloadStage(shownProgress);
        float finalAlpha = alpha;
        drawTopMostLayer(mc, buffers, g -> renderCard(g, width, height, shownProgress, title, stage, "", finalAlpha, false));
    }

    private static void drawTopMostLayer(
            Minecraft mc,
            MultiBufferSource.BufferSource buffers,
            java.util.function.Consumer<GuiGraphics> painter
    ) {
        GuiGraphics g = new GuiGraphics(mc, buffers);
        g.pose().pushPose();
        // Поднимаем по Z выше всех экранов, оверлеев и тостов
        g.pose().translate(0.0f, 0.0f, 800.0f);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        painter.accept(g);
        g.flush();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        g.pose().popPose();
    }

    /**
     * Отрисовка фирменного экрана загрузки с карточкой, прогресс-баром, свечением и точным процентом (0%..100%).
     */
    public static void renderCard(
            GuiGraphics g,
            int width,
            int height,
            float progress01,
            String title,
            String stageText,
            String detailText,
            float alpha,
            boolean leaveBottomSpaceForButton
    ) {
        int a255 = Mth.clamp(Math.round(alpha * 255.0f), 0, 255);
        if (a255 <= 2) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float clamped = Mth.clamp(progress01, 0.0f, 1.0f);
        int percent = Mth.clamp(Math.round(clamped * 100.0f), 0, 100);
        long nowMs = System.currentTimeMillis();

        // 1. Глубокий тёмно-синий градиентный фон + мягкая техническая сетка
        int bgTop = withAlpha(0x070B14, a255);
        int bgBot = withAlpha(0x0F172A, a255);
        g.fillGradient(0, 0, width, height, bgTop, bgBot);

        int gridAlpha = Math.round(alpha * 16.0f);
        if (gridAlpha > 1) {
            int gridCol = (gridAlpha << 24) | 0x38BDF8;
            int step = 28;
            int shift = (int) ((nowMs / 45L) % step);
            for (int x = -step + shift; x < width; x += step) {
                g.fill(x, 0, x + 1, height, gridCol);
            }
            for (int y = 0; y < height; y += step) {
                g.fill(0, y, width, y + 1, gridCol);
            }
        }

        // 2. Центральная карточка
        int cardW = Math.min(340, Math.max(230, width - 40));
        int cardH = detailText != null && !detailText.isBlank() ? 124 : 110;
        int cx = width / 2;
        int cy = leaveBottomSpaceForButton ? (height / 2 - 16) : (height / 2);
        int x0 = cx - cardW / 2;
        int y0 = cy - cardH / 2;
        int x1 = x0 + cardW;
        int y1 = y0 + cardH;

        // Тень и стеклянная подложка
        g.fill(x0 - 3, y0 - 3, x1 + 3, y1 + 3, withAlpha(0x020617, Math.round(alpha * 140f)));
        g.fillGradient(x0, y0, x1, y1, withAlpha(0x111827, Math.round(alpha * 238f)), withAlpha(0x0B1120, Math.round(alpha * 245f)));

        // Неоновая обводка карточки
        int borderTop = withAlpha(0x38BDF8, Math.round(alpha * 185f));
        int borderSide = withAlpha(0x1E293B, Math.round(alpha * 210f));
        g.fill(x0, y0, x1, y0 + 1, borderTop);
        g.fill(x0, y1 - 1, x1, y1, borderSide);
        g.fill(x0, y0, x0 + 1, y1, borderSide);
        g.fill(x1 - 1, y0, x1, y1, borderSide);

        // Верхняя акцентная полоска
        g.fillGradient(x0 + 1, y0 + 1, x1 - 1, y0 + 22,
                withAlpha(0x0EA5E9, Math.round(alpha * 55f)),
                withAlpha(0x0EA5E9, 0));

        // 3. Заголовок и анимированный индикатор
        String dots = switch ((int) ((nowMs / 320L) % 4)) {
            case 1 -> " ◈";
            case 2 -> " ◈◈";
            case 3 -> " ◈◈◈";
            default -> "";
        };
        String header = "◈ SCRIPTCRAFT  ·  " + title + dots;
        int textWhite = withAlpha(0xF8FAFC, a255);
        int textCyan = withAlpha(0x38BDF8, a255);
        int textMuted = withAlpha(0x94A3B8, a255);
        int textDim = withAlpha(0x64748B, a255);

        g.drawString(font, header, x0 + 14, y0 + 12, textCyan, true);

        // 4. Крупный процент справа сверху карточки (например "74%")
        String pctStr = percent + "%";
        g.pose().pushPose();
        float scale = 1.45f;
        int pctW = Math.round(font.width(pctStr) * scale);
        g.pose().translate(x1 - 14 - pctW, y0 + 28, 0f);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, pctStr, 0, 0, textWhite, true);
        g.pose().popPose();

        // 5. Текст текущего этапа
        String trimmedStage = font.plainSubstrByWidth(stageText == null ? "" : stageText, cardW - 76);
        g.drawString(font, trimmedStage, x0 + 14, y0 + 34, textMuted, false);

        if (detailText != null && !detailText.isBlank()) {
            String trimmedDetail = font.plainSubstrByWidth(detailText, cardW - 28);
            g.drawString(font, trimmedDetail, x0 + 14, y0 + 48, textDim, false);
        }

        // 6. Современный многослойный Прогресс-Бар
        int barX0 = x0 + 14;
        int barX1 = x1 - 14;
        int barW = barX1 - barX0;
        int barY0 = y1 - 30;
        int barY1 = barY0 + 12;

        // Фон трека и рамка прогресс-бара
        g.fill(barX0 - 1, barY0 - 1, barX1 + 1, barY1 + 1, withAlpha(0x1E293B, a255));
        g.fill(barX0, barY0, barX1, barY1, withAlpha(0x090D16, a255));

        int fillW = Math.round(barW * clamped);
        if (fillW > 0) {
            int fx1 = barX0 + fillW;
            // Основная заливка с бликом сверху
            g.fillGradient(barX0, barY0, fx1, barY1,
                    withAlpha(0x38BDF8, a255),
                    withAlpha(0x0284C7, a255));
            g.fill(barX0, barY0, fx1, barY0 + 2, withAlpha(0xBAE6FD, Math.round(alpha * 170f)));

            // Бегущий световой блик (shimmer) внутри заполненной части
            if (fillW > 18) {
                int shimmerW = Math.min(34, fillW / 2);
                int travel = fillW + shimmerW;
                int offset = (int) ((nowMs / 8L) % Math.max(1, travel)) - shimmerW;
                int sx0 = Math.max(barX0, barX0 + offset);
                int sx1 = Math.min(fx1, barX0 + offset + shimmerW);
                if (sx1 > sx0) {
                    g.fill(sx0, barY0, sx1, barY1, withAlpha(0xE0F2FE, Math.round(alpha * 95f)));
                }
            }
        }

        // Нижняя строка с сегментной шкалой
        String bottomHint = (isRu() ? "Прогресс выполнения: " : "Completion: ") + percent + "%";
        g.drawString(font, bottomHint, barX0, barY1 + 5, textDim, false);
    }

    private static int withAlpha(int rgb, int a255) {
        return ((a255 & 0xFF) << 24) | (rgb & 0x00FFFFFF);
    }

    public static boolean isRu() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null && mc.options.languageCode != null) {
                if (mc.options.languageCode.toLowerCase(java.util.Locale.ROOT).startsWith("ru")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return "ru".equalsIgnoreCase(com.scriptcraft.config.ScriptCraftConfig.language());
    }
}
