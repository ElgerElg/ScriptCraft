package com.scriptcraft.client.geo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Спавнит частицы и звуки в точных мировых координатах костей и locators 3D-моделей (.geo.json + .animation.json).
 */
@OnlyIn(Dist.CLIENT)
public final class GeoParticleHelper {
    private static final Map<Integer, Float> LAST_KEYFRAME_TIME = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> LAST_CONTINUOUS_TICK = new ConcurrentHashMap<>();

    private GeoParticleHelper() {
    }

    public static void emitContinuousBoneParticles(
            int instanceKey,
            double baseX, double baseY, double baseZ,
            Map<String, String> boneParticles,
            Map<String, Vector4f> boneOffsets
    ) {
        if (boneParticles.isEmpty() || boneOffsets.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;

        long gameTick = level.getGameTime();
        Long prev = LAST_CONTINUOUS_TICK.get(instanceKey);
        if (prev != null && prev == gameTick) {
            return;
        }
        LAST_CONTINUOUS_TICK.put(instanceKey, gameTick);
        if ((gameTick & 1L) != 0L) {
            return; // 10 раз в секунду для аккуратного шлейфа без спама
        }

        for (Map.Entry<String, String> entry : boneParticles.entrySet()) {
            Vector4f offset = boneOffsets.get(entry.getKey().toLowerCase(Locale.ROOT));
            if (offset == null) continue;
            ParticleOptions p = resolveParticle(entry.getValue());
            if (p != null) {
                level.addParticle(p,
                        baseX + offset.x, baseY + offset.y, baseZ + offset.z,
                        (level.random.nextDouble() - 0.5) * 0.02,
                        level.random.nextDouble() * 0.02,
                        (level.random.nextDouble() - 0.5) * 0.02);
            }
        }
    }

    public static void emitClipKeyframes(
            int instanceKey,
            double baseX, double baseY, double baseZ,
            @Nullable GeoModelData.AnimationClip clip,
            float effectiveTimeSec,
            Map<String, Vector4f> boneOffsets
    ) {
        if (clip == null || (clip.particleEffects().isEmpty() && clip.soundEffects().isEmpty())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;

        Float prevT = LAST_KEYFRAME_TIME.put(instanceKey, effectiveTimeSec);
        if (prevT == null) {
            return;
        }
        boolean wrapped = effectiveTimeSec < prevT;

        for (GeoModelData.ParticleKeyframe pk : clip.particleEffects()) {
            boolean hit = wrapped
                    ? (pk.timeSec() > prevT || pk.timeSec() <= effectiveTimeSec)
                    : (pk.timeSec() > prevT && pk.timeSec() <= effectiveTimeSec);
            if (hit) {
                ParticleOptions p = resolveParticle(pk.effect());
                if (p != null) {
                    Vector4f off = pk.locator().isEmpty() ? null : boneOffsets.get(pk.locator().toLowerCase(Locale.ROOT));
                    double px = baseX + (off != null ? off.x : 0.0);
                    double py = baseY + (off != null ? off.y : 0.8);
                    double pz = baseZ + (off != null ? off.z : 0.0);
                    for (int i = 0; i < 4; i++) {
                        level.addParticle(p, px, py, pz,
                                (level.random.nextDouble() - 0.5) * 0.08,
                                (level.random.nextDouble() - 0.2) * 0.06,
                                (level.random.nextDouble() - 0.5) * 0.08);
                    }
                }
            }
        }

        for (GeoModelData.SoundKeyframe sk : clip.soundEffects()) {
            boolean hit = wrapped
                    ? (sk.timeSec() > prevT || sk.timeSec() <= effectiveTimeSec)
                    : (sk.timeSec() > prevT && sk.timeSec() <= effectiveTimeSec);
            if (hit) {
                ResourceLocation rl = ResourceLocation.tryParse(sk.effect().contains(":")
                        ? sk.effect() : "minecraft:" + sk.effect());
                if (rl != null) {
                    SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(rl);
                    if (se == null) {
                        se = SoundEvent.createVariableRangeEvent(rl);
                    }
                    level.playLocalSound(baseX, baseY, baseZ, se, SoundSource.HOSTILE, 1.0f, 1.0f, false);
                }
            }
        }
    }

    @Nullable
    public static ParticleOptions resolveParticle(String rawName) {
        if (rawName == null || rawName.isBlank()) return null;
        String clean = rawName.strip().toLowerCase(Locale.ROOT);
        if (!clean.contains(":")) {
            clean = "minecraft:" + clean;
        }
        ResourceLocation rl = ResourceLocation.tryParse(clean);
        if (rl != null) {
            ParticleType<?> pt = ForgeRegistries.PARTICLE_TYPES.getValue(rl);
            if (pt instanceof ParticleOptions simple) {
                return simple;
            }
        }
        return switch (rawName.strip().toLowerCase(Locale.ROOT)) {
            case "flame", "fire" -> ParticleTypes.FLAME;
            case "soul_fire_flame", "soul" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "electric_spark", "spark", "plasma" -> ParticleTypes.ELECTRIC_SPARK;
            case "end_rod", "glow" -> ParticleTypes.END_ROD;
            case "portal" -> ParticleTypes.PORTAL;
            case "smoke" -> ParticleTypes.SMOKE;
            case "large_smoke" -> ParticleTypes.LARGE_SMOKE;
            case "enchant" -> ParticleTypes.ENCHANT;
            case "crit" -> ParticleTypes.CRIT;
            case "heart" -> ParticleTypes.HEART;
            case "cloud" -> ParticleTypes.CLOUD;
            default -> ParticleTypes.ELECTRIC_SPARK;
        };
    }

    public static void clear() {
        LAST_KEYFRAME_TIME.clear();
        LAST_CONTINUOUS_TICK.clear();
    }
}
