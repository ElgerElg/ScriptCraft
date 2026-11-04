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
 */
public record BlockDefinition(String id, @Nullable String displayName, float hardness, float resistance,
                              String sound, int lightLevel, boolean requiresTool) {

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
