package com.scriptcraft.script.parser;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * Описание кастомной 3D-сущности из "define entity <id>:" (моб, босс, турель или декор без лимита слотов!).
 */
public record EntityDefinition(
        String id,
        @Nullable String displayName,
        String ai,
        @Nullable String model,
        @Nullable String texture,
        @Nullable String animation,
        String idleAnimation,
        String walkAnimation,
        String attackAnimation,
        String deathAnimation,
        @Nullable String spawnAnimation,
        float health,
        float speed,
        float damage,
        float attackRange,
        float followRange,
        float width,
        float height,
        float scale,
        boolean glow,
        boolean nametag,
        boolean pushable,
        boolean invulnerable,
        boolean gravity,
        Map<String, String> particleBones
) {
    public static final String AI_HOSTILE = "hostile";
    public static final String AI_PASSIVE = "passive";
    public static final String AI_TURRET = "turret";
    public static final String AI_NONE = "none";

    public String namespace() {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(0, colon) : "scriptcraft";
    }

    public String path() {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    public String effectiveModel() {
        return (model != null && !model.isBlank()) ? model : id;
    }

    public String effectiveTexture() {
        return (texture != null && !texture.isBlank()) ? texture : (namespace() + ":textures/entity/" + path() + ".png");
    }

    public String effectiveAnimationFile() {
        return (animation != null && !animation.isBlank()) ? animation : effectiveModel();
    }

    @Nullable
    public static String parseAi(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return AI_PASSIVE;
        }
        return switch (raw.replace("\"", "").strip().toLowerCase(Locale.ROOT)) {
            case "hostile", "monster", "boss", "aggressive", "enemy" -> AI_HOSTILE;
            case "passive", "mob", "wander", "animal", "friendly" -> AI_PASSIVE;
            case "turret", "tower", "sentry", "guard" -> AI_TURRET;
            case "none", "prop", "static", "decor", "statue", "dummy", "false" -> AI_NONE;
            default -> null;
        };
    }
}
