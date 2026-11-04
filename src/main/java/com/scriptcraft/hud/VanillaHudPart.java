package com.scriptcraft.hud;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Части ванильного HUD, которые скрипт может спрятать (hide vanilla hotbar for player).
 * Только имена и биты — сопоставление с оверлеями Forge (VanillaGuiOverlay) живёт на клиенте,
 * поэтому класс безопасен для выделенного сервера. Порядок важен (бит = ordinal) — новые добавлять в конец.
 */
public enum VanillaHudPart {
    HOTBAR("hotbar"),
    HEALTH("health"),
    FOOD("food"),
    ARMOR("armor"),
    AIR("air"),
    EXPERIENCE("experience"),
    MOUNT_HEALTH("mount_health"),
    JUMP_BAR("jump_bar"),
    CROSSHAIR("crosshair"),
    EFFECTS("effects"),
    ITEM_NAME("item_name"),
    SCOREBOARD("scoreboard"),
    BOSSBAR("bossbar");

    /** Маска "всё сразу" (hide vanilla hud). */
    public static final int ALL = (1 << values().length) - 1;

    private final String scriptName;

    VanillaHudPart(String scriptName) {
        this.scriptName = scriptName;
    }

    public String scriptName() {
        return scriptName;
    }

    public int bit() {
        return 1 << ordinal();
    }

    /** Имя из скрипта -> маска. "hud"/"all" = всё; -1, если имя неизвестно. */
    public static int maskByName(String name) {
        String n = name.toLowerCase(Locale.ROOT).replace('-', '_');
        switch (n) {
            case "hud", "all", "everything" -> {
                return ALL;
            }
            case "xp", "experience_bar", "exp" -> {
                return EXPERIENCE.bit();
            }
            case "hunger", "food_level" -> {
                return FOOD.bit();
            }
            case "hearts", "health_bar" -> {
                return HEALTH.bit();
            }
            case "potion_effects", "potions" -> {
                return EFFECTS.bit();
            }
            case "boss_bar", "bossbars" -> {
                return BOSSBAR.bit();
            }
            default -> {
            }
        }
        for (VanillaHudPart part : values()) {
            if (part.scriptName.equals(n)) {
                return part.bit();
            }
        }
        return -1;
    }

    /** Список имён для сообщений об ошибках и документации. */
    public static String names() {
        return Arrays.stream(values()).map(VanillaHudPart::scriptName).collect(Collectors.joining(", ")) + ", hud";
    }
}
