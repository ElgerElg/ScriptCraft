package com.scriptcraft.script.parser;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
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

    /** Превращает описание из скрипта в свойства блока Minecraft. */
    public BlockBehaviour.Properties toProperties() {
        final int light = Math.max(0, Math.min(15, lightLevel));
        BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                .strength(hardness, resistance)
                .sound(soundType())
                .lightLevel(state -> light);
        if (requiresTool) {
            props = props.requiresCorrectToolForDrops();
        }
        return props;
    }

    private SoundType soundType() {
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
