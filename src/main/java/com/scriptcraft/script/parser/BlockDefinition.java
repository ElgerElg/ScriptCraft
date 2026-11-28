package com.scriptcraft.script.parser;

import net.minecraft.world.level.block.SoundType;
import org.jetbrains.annotations.Nullable;

/**
 * Блок из "define block <id>:" (поддерживает как обычные кубы/модели, так и анимированные 3D Geo-модели).
 */
public record BlockDefinition(String id, @Nullable String displayName, float hardness, float resistance,
                              String sound, int lightLevel, boolean requiresTool, String facing,
                              String render, @Nullable Boolean full, float friction, float speed, float jump,
                              boolean gravity, boolean collision, int burn, int spread,
                              String geoModel, String geoTexture, String geoAnimation,
                              String idleAnimation, float geoScale,
                              @Nullable String lore, boolean glow) {
    public static final String FACING_NONE = "none";
    public static final String FACING_HORIZONTAL = "horizontal";
    public static final String FACING_ALL = "all";
    public static final String RENDER_SOLID = "solid";
    public static final String RENDER_CUTOUT = "cutout";
    public static final String RENDER_TRANSLUCENT = "translucent";

    public BlockDefinition(String id, @Nullable String displayName, float hardness, float resistance,
                           String sound, int lightLevel, boolean requiresTool, String facing,
                           String render, @Nullable Boolean full, float friction, float speed, float jump,
                           boolean gravity, boolean collision, int burn, int spread) {
        this(id, displayName, hardness, resistance, sound, lightLevel, requiresTool, facing, render, full,
                friction, speed, jump, gravity, collision, burn, spread, "", "", "", "idle", 1.0f, null, false);
    }

    public BlockDefinition(String id, @Nullable String displayName, float hardness, float resistance,
                           String sound, int lightLevel, boolean requiresTool, String facing,
                           String render, @Nullable Boolean full, float friction, float speed, float jump,
                           boolean gravity, boolean collision, int burn, int spread,
                           String geoModel, String geoTexture, String geoAnimation,
                           String idleAnimation, float geoScale) {
        this(id, displayName, hardness, resistance, sound, lightLevel, requiresTool, facing, render, full,
                friction, speed, jump, gravity, collision, burn, spread,
                geoModel, geoTexture, geoAnimation, idleAnimation, geoScale, null, false);
    }

    public boolean isGeo() {
        return geoModel != null && !geoModel.isBlank();
    }

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
     * У дырявой текстуры и у 3D Geo-модели — нет, иначе пол под блоком пропадает.
     */
    public boolean occludes() {
        if (full != null) {
            return full;
        }
        if (isGeo()) {
            return false;
        }
        return RENDER_SOLID.equals(render);
    }

    public String namespace() {
        return id.substring(0, id.indexOf(':'));
    }

    public String path() {
        return id.substring(id.indexOf(':') + 1);
    }

    public int light() {
        return Math.max(0, Math.min(15, lightLevel));
    }

    public SoundType soundType() {
        return switch (sound) {
            case "wood" -> SoundType.WOOD;
            case "metal" -> SoundType.METAL;
            case "glass" -> SoundType.GLASS;
            case "grass" -> SoundType.GRASS;
            case "sand" -> SoundType.SAND;
            case "wool" -> SoundType.WOOL;
            case "gravel" -> SoundType.GRAVEL;
            case "honey" -> SoundType.HONEY_BLOCK;
            case "slime" -> SoundType.SLIME_BLOCK;
            case "snow" -> SoundType.SNOW;
            default -> SoundType.STONE;
        };
    }
}
