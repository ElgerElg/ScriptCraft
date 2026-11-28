package com.scriptcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Единый рендерер кастомных загрузочных экранов ScriptCraft с плавным прогресс-баром и процентами (0%..100%):
 *  1) При перезагрузке ресурспаков / F3+T / запекании новых моделей (LoadingOverlayMixin)
 *  2) При подключении к серверу, докачке кэша в фазе LOGIN и загрузке мира (ConnectScreenMixin + ReceivingLevelScreenMixin)
 */
@OnlyIn(Dist.CLIENT)
public final class ClientLoadScreenRenderer {
    private static volatile float serverJoinProgress = 0.08f;
    private static volatile String serverJoinStage = "";
    private static volatile String serverJoinDetail = "";
    private static volatile String reloadCustomReason = "";
    private static float smoothedJoinProgress = 0.05f;

    private ClientLoadScreenRenderer() {
    }

    public static void beginServerConnect() {
        serverJoinProgress = 0.08f;
        smoothedJoinProgress = 0.05f;
        serverJoinStage = isRu() ? "Подключение к серверу..." : "Connecting to server...";
        serverJoinDetail = "";
    }

    public static void updateJoinProgress(float target01, String stageRu, String stageEn, String detail) {
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
            smoothedJoinProgress = Math.min(target, smoothedJoinProgress + Math.max(0.004f, (target - smoothedJoinProgress) * 0.14f));
        } else if (smoothedJoinProgress < maxCeil) {
            smoothedJoinProgress = Math.min(maxCeil, smoothedJoinProgress + deltaTick * 0.0025f);
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
     * Отрисовка фирменного экрана загрузки с карточкой, прогресс-баром, свечением и точным процентом (0%..100%).
     *
     * @param alpha глобальная прозрачность (1.0f во время загрузки, 1.0 -> 0.0 при плавном затухании LoadingOverlay)
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
