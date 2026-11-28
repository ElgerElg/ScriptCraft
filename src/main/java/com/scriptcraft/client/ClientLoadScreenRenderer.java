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
 * Компактная плашка прогресса загрузки в левом верхнем углу экрана (в стиле уведомлений {@link ClientToasts}),
 * НЕ перекрывающая и НЕ изменяющая ванильные/модовые меню, экран подключения к серверу или загрузку мира.
 *
 * Показывает:
 *  - процент выполнения (0%..100%) и полосу прогресса;
 *  - текущий скачиваемый файл;
 *  - сколько файлов осталось скачать (Осталось файлов: X из Y).
 * Не исчезает по таймеру, пока все файлы полностью не докачаются и не применятся!
 */
@OnlyIn(Dist.CLIENT)
public final class ClientLoadScreenRenderer {
    /** Активна ли сейчас докачка файлов (LoginSyncNetwork или потоковые чанки ClientResources). */
    private static volatile boolean downloadingActive = false;
    /** Завершилась ли докачка — ждём короткую паузу (900 мс) + плавное затухание (400 мс), как у ClientToasts. */
    private static volatile long finishTimestampMs = -1L;

    private static volatile int totalFiles = 0;
    private static volatile int doneFiles = 0;
    private static volatile int newlyWrittenFiles = 0;
    private static volatile float targetProgress01 = 0.0f;
    private static volatile String currentFilePath = "";
    private static volatile String reloadCustomReason = "";

    private static float smoothedProgress01 = 0.0f;
    private static Overlay trackedOverlay = null;
    private static float trackedOverlayProgress = 0.0f;
    private static volatile int lastRenderedScaledHeightPx = 0;

    private static final long COMPLETE_HOLD_MS = 900L;
    private static final long COMPLETE_FADE_MS = 400L;

    private ClientLoadScreenRenderer() {
    }

    /** Высота активной плашки сверху слева (в GUI-пикселях), чтобы обычные уведомления ClientToasts рисовались ниже неё. */
    public static int getActiveTopLeftOffsetPx() {
        return lastRenderedScaledHeightPx;
    }

    // ------------------------------------------------------------------ события фазы LOGIN (LoginSyncNetwork)

    public static void onLoginStartReset() {
        newlyWrittenFiles = 0;
    }

    public static void onLoginFileProgress(int doneCount, int totalCount, String rawPath, boolean actuallyDownloading) {
        if (actuallyDownloading) {
            newlyWrittenFiles++;
            downloadingActive = true;
            finishTimestampMs = -1L;
        }
        totalFiles = Math.max(totalCount, doneCount);
        doneFiles = Math.max(0, Math.min(totalFiles, doneCount));
        if (rawPath != null && !rawPath.isBlank()) {
            currentFilePath = shortenAssetPath(rawPath);
        }
        if (totalFiles > 0) {
            targetProgress01 = Mth.clamp((float) doneFiles / (float) totalFiles, 0.0f, 0.99f);
        }
    }

    public static void onLoginBatchDone(
            int step,
            int totalSteps,
            int checkedFiles,
            int totalServerFiles,
            int writtenInBatch,
            String lastFile
    ) {
        if (writtenInBatch > 0 || newlyWrittenFiles > 0) {
            downloadingActive = true;
            finishTimestampMs = -1L;
            totalFiles = Math.max(1, totalServerFiles);
            doneFiles = Math.max(0, Math.min(totalFiles, checkedFiles));
            if (lastFile != null && !lastFile.isBlank()) {
                currentFilePath = shortenAssetPath(lastFile);
            }
            targetProgress01 = totalFiles > 0
                    ? Mth.clamp((float) doneFiles / (float) totalFiles, 0.0f, 0.98f)
                    : Mth.clamp((float) step / (float) Math.max(1, totalSteps), 0.0f, 0.98f);
        }
    }

    // ------------------------------------------------------------------ события фазы PLAY (ClientResources)

    /** Потоковая докачка новых/крупных файлов в мире или после /sc reload. */
    public static void onPlayDownloadProgress(int completedFiles, int totalToDownload, float byteProgress01, String rawFile) {
        if (totalToDownload <= 0) {
            return;
        }
        if (!downloadingActive || finishTimestampMs > 0L) {
            smoothedProgress01 = 0.0f;
        }
        downloadingActive = true;
        finishTimestampMs = -1L;
        totalFiles = totalToDownload;
        doneFiles = Math.max(0, Math.min(totalToDownload, completedFiles));
        targetProgress01 = Mth.clamp(byteProgress01, 0.0f, 1.0f);
        if (rawFile != null && !rawFile.isBlank()) {
            currentFilePath = shortenAssetPath(rawFile);
        }
    }

    /** Манифест проверен, все файлы в кэше актуальны (ничего докачивать по чанкам не нужно). */
    public static void onManifestUpToDate() {
        if (downloadingActive) {
            onAllDownloadsComplete();
        }
    }

    /** Все файлы полностью скачаны в кэш! Если не запущена перезагрузка ресурспаков — фиксируем 100% и запускаем плавное скрытие. */
    public static void onAllDownloadsComplete() {
        if (!downloadingActive) {
            return;
        }
        doneFiles = totalFiles;
        targetProgress01 = 1.0f;
        smoothedProgress01 = 1.0f;
        if (finishTimestampMs < 0L) {
            finishTimestampMs = Util.getMillis();
        }
    }

    public static void setReloadReason(String reasonRu, String reasonEn) {
        reloadCustomReason = isRu() ? reasonRu : reasonEn;
    }

    public static void clearReloadReason() {
        reloadCustomReason = "";
    }

    public static void resetJoinState() {
        downloadingActive = false;
        finishTimestampMs = -1L;
        totalFiles = 0;
        doneFiles = 0;
        newlyWrittenFiles = 0;
        targetProgress01 = 0.0f;
        smoothedProgress01 = 0.0f;
        currentFilePath = "";
        reloadCustomReason = "";
        lastRenderedScaledHeightPx = 0;
    }

    // ------------------------------------------------------------------ отрисовка плашки сверху слева

    public static void renderTopMostHook(Minecraft mc, MultiBufferSource.BufferSource buffers, float partialTick) {
        Overlay overlay = mc.getOverlay();
        Screen screen = mc.screen;

        // Если соединение оборвалось / нажали «Отмена» и вышли в главное меню или список серверов — сбрасываем плашку докачки
        if (downloadingActive && mc.level == null && overlay == null
                && !(screen instanceof ConnectScreen) && !(screen instanceof ReceivingLevelScreen)) {
            String sName = screen == null ? "" : screen.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
            if (sName.contains("disconnected") || sName.contains("multiplayer") || sName.contains("title")) {
                resetJoinState();
            }
        }

        boolean hasReloadOverlay = overlay instanceof LoadingOverlay;
        if (!hasReloadOverlay) {
            trackedOverlay = null;
            if (!reloadCustomReason.isEmpty()) {
                reloadCustomReason = "";
            }
        }

        // Проверяем, не истекло ли плавное затухание после полного окончания докачки
        long nowMs = Util.getMillis();
        if (downloadingActive && !hasReloadOverlay && finishTimestampMs > 0L) {
            if (nowMs - finishTimestampMs >= COMPLETE_HOLD_MS + COMPLETE_FADE_MS) {
                resetJoinState();
            }
        }

        boolean isConnectingScreen = (screen instanceof ConnectScreen) || (screen instanceof ReceivingLevelScreen);

        if (!downloadingActive && !hasReloadOverlay && !isConnectingScreen) {
            lastRenderedScaledHeightPx = 0;
            return;
        }

        boolean ru = isRu();
        String headerTitle;
        String lineFile = null;
        String lineRemaining = null;
        float shownProgress01;
        float alpha = 1.0f;
        boolean finished100 = false;

        if (downloadingActive) {
            if (hasReloadOverlay) {
                // Файлы скачались, идёт запекание моделей / применение кэша — плашка НЕ пропадает до конца reload!
                finishTimestampMs = -1L;
                float reloadProg = readLoadingOverlayProgress((LoadingOverlay) overlay);
                shownProgress01 = reloadProg;
                headerTitle = ru ? "ScriptCraft · Применение ресурсов" : "ScriptCraft · Applying resources";
                lineFile = !currentFilePath.isEmpty()
                        ? ((ru ? "Файл: " : "File: ") + currentFilePath)
                        : (ru ? "Запекание моделей и текстур..." : "Baking models & textures...");
                lineRemaining = ru
                        ? ("Файлы скачаны (" + totalFiles + ") · загрузка в игру...")
                        : ("Files downloaded (" + totalFiles + ") · reloading...");
            } else {
                // Идёт активное скачивание файлов (или финальное удержание 100%)
                if (finishTimestampMs > 0L) {
                    shownProgress01 = 1.0f;
                    finished100 = true;
                    long elapsed = nowMs - finishTimestampMs;
                    if (elapsed > COMPLETE_HOLD_MS) {
                        alpha = 1.0f - Mth.clamp((float) (elapsed - COMPLETE_HOLD_MS) / (float) COMPLETE_FADE_MS, 0.0f, 1.0f);
                    }
                } else {
                    if (smoothedProgress01 < targetProgress01) {
                        smoothedProgress01 = Math.min(targetProgress01,
                                smoothedProgress01 + Math.max(0.008f, (targetProgress01 - smoothedProgress01) * 0.22f));
                    } else {
                        smoothedProgress01 = targetProgress01;
                    }
                    shownProgress01 = Mth.clamp(smoothedProgress01, 0.0f, 1.0f);
                }

                int remaining = Math.max(0, totalFiles - doneFiles);
                headerTitle = finished100
                        ? (ru ? "ScriptCraft · Ресурсы загружены" : "ScriptCraft · Assets ready")
                        : (ru ? "ScriptCraft · Скачивание ресурсов" : "ScriptCraft · Downloading assets");
                if (!currentFilePath.isEmpty()) {
                    lineFile = (ru ? "Файл: " : "File: ") + currentFilePath;
                }
                if (totalFiles > 0) {
                    lineRemaining = remaining == 0
                            ? (ru ? ("Осталось файлов: 0 из " + totalFiles + " (готово)") : ("Remaining: 0 / " + totalFiles + " (done)"))
                            : (ru ? ("Осталось файлов: " + remaining + " из " + totalFiles) : ("Remaining files: " + remaining + " / " + totalFiles));
                }
            }
        } else if (hasReloadOverlay) {
            // Перезагрузка ресурспаков игрока (F3+T / меню пакетов / горячее запекание)
            shownProgress01 = readLoadingOverlayProgress((LoadingOverlay) overlay);
            long fadeOutStart = ((LoadingOverlayAccessor) overlay).scriptcraft$getFadeOutStart();
            if (fadeOutStart > -1L) {
                shownProgress01 = 1.0f;
                finished100 = true;
                alpha = 1.0f - Mth.clamp((float) (nowMs - fadeOutStart) / 500.0f, 0.0f, 1.0f);
            }
            headerTitle = ru ? "ScriptCraft · Перезагрузка ресурсов" : "ScriptCraft · Reloading resources";
            if (!reloadCustomReason.isBlank()) {
                lineFile = reloadCustomReason;
            } else {
                lineFile = currentReloadStageShort(shownProgress01, ru);
            }
        } else {
            // Подключение к серверу / загрузка мира (когда все файлы уже были в кэше)
            if (screen instanceof ConnectScreen) {
                smoothedProgress01 = Mth.clamp(Math.max(0.15f, smoothedProgress01 + partialTick * 0.01f), 0.15f, 0.75f);
                shownProgress01 = smoothedProgress01;
                headerTitle = ru ? "ScriptCraft · Вход на сервер" : "ScriptCraft · Connecting";
                lineFile = ru ? "Проверка реестров modid:item и кэша..." : "Checking modid:item registries & cache...";
            } else {
                smoothedProgress01 = Mth.clamp(Math.max(0.82f, smoothedProgress01 + partialTick * 0.015f), 0.82f, 0.98f);
                shownProgress01 = smoothedProgress01;
                headerTitle = ru ? "ScriptCraft · Загрузка мира" : "ScriptCraft · Loading world";
                lineFile = ru ? "Синхронизация данных..." : "Syncing world data...";
            }
        }

        if (alpha <= 0.02f) {
            lastRenderedScaledHeightPx = 0;
            return;
        }

        final String fHeader = headerTitle;
        final String fLine1 = lineFile;
        final String fLine2 = lineRemaining;
        final float fProg = Mth.clamp(shownProgress01, 0.0f, 1.0f);
        final float fAlpha = alpha;
        final boolean fDone = finished100;

        GuiGraphics g = new GuiGraphics(mc, buffers);
        g.pose().pushPose();
        g.pose().translate(0.0f, 0.0f, 800.0f);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        lastRenderedScaledHeightPx = drawToastStyleWidget(mc.font, g, fHeader, fLine1, fLine2, fProg, fAlpha, fDone);

        g.flush();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        g.pose().popPose();
    }

    /**
     * Рисует компактную карточку в левом верхнем углу (x=6, y=6) ровно в стиле уведомлений {@link ClientToasts}:
     * тёмная подложка 0x141820, золотая полоска 2px слева (0xC4A15A), процент справа и прогресс-бар снизу.
     *
     * @return итоговая высота виджета в масштабированных GUI-пикселях
     */
    private static int drawToastStyleWidget(
            Font font,
            GuiGraphics g,
            String title,
            String fileLine,
            String remainingLine,
            float progress01,
            float alpha,
            boolean done
    ) {
        float scale = ClientUiSettings.scale();
        int margin = 6;
        int maxTextWidth = 236;

        String pctText = Math.round(progress01 * 100.0f) + "%";
        int pctW = font.width(pctText);

        String trimmedTitle = font.plainSubstrByWidth(title, maxTextWidth - pctW - 10);
        String trimmedFile = fileLine == null || fileLine.isEmpty() ? null : ellipsizeMiddleOrLeft(font, fileLine, maxTextWidth);
        String trimmedRem = remainingLine == null || remainingLine.isEmpty() ? null : font.plainSubstrByWidth(remainingLine, maxTextWidth);

        int contentW = Math.max(176, font.width(trimmedTitle) + 10 + pctW);
        if (trimmedFile != null) {
            contentW = Math.max(contentW, font.width(trimmedFile));
        }
        if (trimmedRem != null) {
            contentW = Math.max(contentW, font.width(trimmedRem));
        }

        int lineCount = 1 + (trimmedFile != null ? 1 : 0) + (trimmedRem != null ? 1 : 0);
        int boxW = contentW + 12;
        int boxH = 5 + lineCount * 10 + 6; // + место под нижний прогресс-бар

        int accentRgb = done ? 0x4ADE80 : 0xC4A15A; // как в ClientToasts (0xC4A15A), при 100% — мягкий зелёный
        int bg = withAlpha(0x141820, alpha * 0.93f);
        int border = withAlpha(0x262E3D, alpha * 0.75f);
        int edge = withAlpha(accentRgb, alpha);
        int trackBg = withAlpha(0x232A38, alpha * 0.95f);
        int barFill = withAlpha(accentRgb, alpha);

        g.pose().pushPose();
        g.pose().translate(margin, margin, 0.0f);
        g.pose().scale(scale, scale, 1.0f);

        // Фон и тонкая рамка + фирменная 2px полоска слева (как в ClientToasts)
        g.fill(0, 0, boxW, boxH, bg);
        g.fill(2, 0, boxW, 1, border);
        g.fill(boxW - 1, 0, boxW, boxH - 3, border);
        g.fill(0, 0, 2, boxH, edge);

        // Строка 1: Заголовок слева + Процент справа
        int textY = 4;
        g.drawString(font, trimmedTitle, 6, textY, withAlpha(0xFFFFFF, alpha), false);
        g.drawString(font, pctText, boxW - 5 - pctW, textY, withAlpha(done ? 0x86EFAC : 0xF3CF7A, alpha), false);
        textY += 10;

        // Строка 2: Текущий скачиваемый файл (или этап)
        if (trimmedFile != null) {
            g.drawString(font, trimmedFile, 6, textY, withAlpha(0xCBD5E1, alpha), false);
            textY += 10;
        }

        // Строка 3: Сколько осталось файлов
        if (trimmedRem != null) {
            g.drawString(font, trimmedRem, 6, textY, withAlpha(0x94A3B8, alpha), false);
        }

        // Нижний прогресс-бар (3px высотой во всю ширину уведомления)
        int barY0 = boxH - 3;
        g.fill(2, barY0, boxW, boxH, trackBg);
        int fillW = Math.max(0, Math.round((boxW - 2) * progress01));
        if (fillW > 0) {
            g.fill(2, barY0, 2 + fillW, boxH, barFill);
        }

        g.pose().popPose();
        return Math.round(boxH * scale);
    }

    private static float readLoadingOverlayProgress(LoadingOverlay lo) {
        if (trackedOverlay != lo) {
            trackedOverlay = lo;
            trackedOverlayProgress = 0.02f;
        }
        ReloadInstance reload = ((LoadingOverlayAccessor) lo).scriptcraft$getReload();
        float actual = reload != null ? reload.getActualProgress() : 0.5f;
        trackedOverlayProgress = Mth.clamp(trackedOverlayProgress * 0.85f + actual * 0.15f, 0.0f, 1.0f);
        return reload != null && reload.isDone() ? 1.0f : trackedOverlayProgress;
    }

    private static String currentReloadStageShort(float p, boolean ru) {
        if (ru) {
            if (p < 0.30f) return "Чтение пакетов и кэша...";
            if (p < 0.70f) return "Запекание моделей и текстур...";
            if (p < 0.92f) return "Загрузка звуков .ogg и шейдеров...";
            return "Завершение...";
        } else {
            if (p < 0.30f) return "Reading resource packs & cache...";
            if (p < 0.70f) return "Baking models & textures...";
            if (p < 0.92f) return "Loading .ogg sounds & shaders...";
            return "Finalizing...";
        }
    }

    /** "assets/gecko/geo/plasma_rifle.geo.json" -> "gecko/geo/plasma_rifle.geo.json" */
    private static String shortenAssetPath(String raw) {
        String s = raw.replace('\\', '/');
        if (s.startsWith("assets/")) {
            s = s.substring("assets/".length());
        }
        return s;
    }

    /** Чтобы конец пути (имя файла!) всегда оставался виден: "Файл: ...item/plasma_rifle.png" */
    private static String ellipsizeMiddleOrLeft(Font font, String text, int maxW) {
        if (font.width(text) <= maxW) {
            return text;
        }
        int colon = text.indexOf(": ");
        String prefix = colon >= 0 && colon < 12 ? text.substring(0, colon + 2) : "";
        String body = prefix.isEmpty() ? text : text.substring(prefix.length());
        String dots = "...";
        int avail = maxW - font.width(prefix) - font.width(dots);
        if (avail <= 16) {
            return font.plainSubstrByWidth(text, maxW);
        }
        // Берём хвост строки body, чтобы имя файла всегда читалось полностью
        StringBuilder tail = new StringBuilder();
        for (int i = body.length() - 1; i >= 0; i--) {
            char c = body.charAt(i);
            if (font.width(c + tail.toString()) > avail) {
                break;
            }
            tail.insert(0, c);
        }
        return prefix + dots + tail;
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255.0f), 0, 255);
        return (a << 24) | (rgb & 0x00FFFFFF);
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
