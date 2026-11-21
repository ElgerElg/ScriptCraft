package com.scriptcraft.script.runtime;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ванильные боссбары сверху экрана (ServerBossEvent).
 * Поддерживают цвета, стили делений, прогресс 0..1 (или 0..100) и показ выбранным игрокам.
 */
public final class BossBarManager {
    private static final Map<String, ServerBossEvent> BARS = new ConcurrentHashMap<>();

    private BossBarManager() {
    }

    public static void create(String id, String title, String color, String style, double progress, List<ServerPlayer> targets) {
        if (id == null || id.isBlank()) {
            return;
        }
        String key = id.strip().toLowerCase(Locale.ROOT);
        ServerBossEvent existing = BARS.get(key);
        if (existing != null) {
            existing.setName(Effects.colored(title));
            existing.setColor(parseColor(color));
            existing.setOverlay(parseOverlay(style));
            existing.setProgress(clampProgress(progress));
            if (targets != null) {
                for (ServerPlayer p : targets) {
                    existing.addPlayer(p);
                }
            }
            return;
        }
        ServerBossEvent bar = new ServerBossEvent(
                Effects.colored(title),
                parseColor(color),
                parseOverlay(style));
        bar.setProgress(clampProgress(progress));
        if (targets != null) {
            for (ServerPlayer p : targets) {
                bar.addPlayer(p);
            }
        }
        BARS.put(key, bar);
    }

    public static void setTitle(String id, String title) {
        ServerBossEvent bar = get(id);
        if (bar != null) {
            bar.setName(Effects.colored(title));
        }
    }

    public static void setProgress(String id, double progress) {
        ServerBossEvent bar = get(id);
        if (bar != null) {
            bar.setProgress(clampProgress(progress));
        }
    }

    public static void setColor(String id, String color) {
        ServerBossEvent bar = get(id);
        if (bar != null) {
            bar.setColor(parseColor(color));
        }
    }

    public static void setStyle(String id, String style) {
        ServerBossEvent bar = get(id);
        if (bar != null) {
            bar.setOverlay(parseOverlay(style));
        }
    }

    public static void show(String id, List<ServerPlayer> targets) {
        ServerBossEvent bar = get(id);
        if (bar != null && targets != null) {
            for (ServerPlayer p : targets) {
                bar.addPlayer(p);
            }
        }
    }

    public static void hide(String id, List<ServerPlayer> targets) {
        ServerBossEvent bar = get(id);
        if (bar != null && targets != null) {
            for (ServerPlayer p : targets) {
                bar.removePlayer(p);
            }
        }
    }

    public static void remove(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        ServerBossEvent removed = BARS.remove(id.strip().toLowerCase(Locale.ROOT));
        if (removed != null) {
            removed.removeAllPlayers();
        }
    }

    public static void clearAll() {
        for (ServerBossEvent bar : BARS.values()) {
            bar.removeAllPlayers();
        }
        BARS.clear();
    }

    public static void onQuit(ServerPlayer player) {
        if (player == null) {
            return;
        }
        for (ServerBossEvent bar : BARS.values()) {
            bar.removePlayer(player);
        }
    }

    private static ServerBossEvent get(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return BARS.get(id.strip().toLowerCase(Locale.ROOT));
    }

    private static float clampProgress(double raw) {
        double v = raw > 1.0 ? raw / 100.0 : raw;
        return (float) Math.max(0.0, Math.min(1.0, v));
    }

    private static BossEvent.BossBarColor parseColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return BossEvent.BossBarColor.RED;
        }
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "pink" -> BossEvent.BossBarColor.PINK;
            case "blue", "aqua", "cyan" -> BossEvent.BossBarColor.BLUE;
            case "green", "lime" -> BossEvent.BossBarColor.GREEN;
            case "yellow", "gold" -> BossEvent.BossBarColor.YELLOW;
            case "purple", "magenta" -> BossEvent.BossBarColor.PURPLE;
            case "white" -> BossEvent.BossBarColor.WHITE;
            default -> BossEvent.BossBarColor.RED;
        };
    }

    private static BossEvent.BossBarOverlay parseOverlay(String raw) {
        if (raw == null || raw.isBlank()) {
            return BossEvent.BossBarOverlay.PROGRESS;
        }
        return switch (raw.strip().toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "notched_6", "segmented_6", "6" -> BossEvent.BossBarOverlay.NOTCHED_6;
            case "notched_10", "segmented_10", "10" -> BossEvent.BossBarOverlay.NOTCHED_10;
            case "notched_12", "segmented_12", "12" -> BossEvent.BossBarOverlay.NOTCHED_12;
            case "notched_20", "segmented_20", "20" -> BossEvent.BossBarOverlay.NOTCHED_20;
            default -> BossEvent.BossBarOverlay.PROGRESS;
        };
    }
}
