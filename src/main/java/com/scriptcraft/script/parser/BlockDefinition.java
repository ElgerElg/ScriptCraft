package com.scriptcraft.script.parser;

import net.minecraft.world.level.block.SoundType;
import org.jetbrains.annotations.Nullable;

/**
 * Блок из "define block <id>:".
 *
 * @param id           полный id "namespace:path", например example:ruby_block (без строки namespace — scriptcraft:...)
 * @param displayName  отображаемое имя или null (тогда используется перевод block.<namespace>.<path>)
 * @param hardness     прочность (камень = 1.5, обсидиан = 50)
 * @param resistance   взрывоустойчивость
 * @param sound        звук: stone, wood, metal, glass, grass, sand, wool, gravel
 * @param lightLevel   свечение 0..15
 * @param requiresTool нужен ли подходящий инструмент для дропа
 * @param facing       поворот при установке: none (нет), horizontal (4 стороны, как печь), all (6 сторон, как раздатчик)
 * @param render       как рисовать текстуру: solid (куб без дырок), cutout (дырки, как листва), translucent (стекло)
 * @param full         true — блок прячет грани соседей; false — нет (3D-модель, пол под ним виден); null — само из render
 */
public record BlockDefinition(String id, @Nullable String displayName, float hardness, float resistance,
                              String sound, int lightLevel, boolean requiresTool, String facing,
                              String render, @Nullable Boolean full) {
    public static final String FACING_NONE = "none";
    public static final String FACING_HORIZONTAL = "horizontal";
    public static final String FACING_ALL = "all";
    public static final String RENDER_SOLID = "solid";
    public static final String RENDER_CUTOUT = "cutout";
    public static final String RENDER_TRANSLUCENT = "translucent";

    /** Нормализует значение свойства facing из скрипта; null — значение неизвестно. */
    @Nullable
    public static String parseFacing(@Nullable String raw) {
        if (raw == null) {
            return FACING_NONE;
        }
        return switch (raw.replace("\"", "").strip().toLowerCase(java.util.Locale.ROOT)) {
            case "none", "false", "no" -> FACING_NONE;
            case "horizontal", "true", "yes" -> FACING_HORIZONTAL;
            case "all" -> FACING_ALL;
            default -> null;
        };
    }

    /** Нормализует render; null — значение неизвестно. */
    @Nullable
    public static String parseRender(@Nullable String raw) {
        if (raw == null) {
            return RENDER_SOLID;
        }
        return switch (raw.replace("\"", "").strip().toLowerCase(java.util.Locale.ROOT)) {
            case "solid", "opaque", "cube" -> RENDER_SOLID;
            case "cutout", "transparent", "cutout_mipped" -> RENDER_CUTOUT;
            case "translucent", "glass" -> RENDER_TRANSLUCENT;
            default -> null;
        };
    }

    /** Поворачивается ли блок вообще. */
    public boolean rotatable() {
        return !FACING_NONE.equals(facing);
    }

    /**
     * Прячет ли блок грани соседей. У полного непрозрачного куба — да.
     * У дырявой текстуры и у 3D-модели — нет, иначе пол под блоком пропадает.
     */
    public boolean occludes() {
        if (full != null) {
            return full;
        }
        return RENDER_SOLID.equals(render);
    }

    /** Пространство имён (часть id до двоеточия). */
    public String namespace() {
        return id.substring(0, id.indexOf(':'));
    }

    /** Путь (часть id после двоеточия). */
    public String path() {
        return id.substring(id.indexOf(':') + 1);
    }

    /** Свечение, ограниченное 0..15 (значение свойства состояния LIGHT у блока-слота). */
    public int light() {
        return Math.max(0, Math.min(15, lightLevel));
    }

    /** Звук блока по имени из скрипта (неизвестное имя — камень). */
    public SoundType soundType() {
        return switch (sound) {
            case "wood" -> SoundType.WOOD;
            case "metal" -> SoundType.METAL;
            case "glass" -> SoundType.GLASS;
            case "grass" -> SoundType.GRASS;
            case "sand" -> SoundType.SAND;
            case "wool" -> SoundType.WOOL;
            case "gravel" -> SoundType.GRAVEL;
            default -> SoundType.STONE;
        };
    }
}
