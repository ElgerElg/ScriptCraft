package com.scriptcraft.client;

import com.scriptcraft.ModPaths;
import com.scriptcraft.ScriptCraft;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.data.gson.AnimationSerializing;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Изолированный мост к PlayerAnimator (+ Bendy-lib).
 * Класс загружается ТОЛЬКО если установлен мод {@code playeranimator}.
 */
@OnlyIn(Dist.CLIENT)
public final class PlayerAnimBridge {
    private static final ResourceLocation LAYER_ID = new ResourceLocation(ScriptCraft.MODID, "script_anim");
    private static final Map<ResourceLocation, KeyframeAnimation> CACHE = new HashMap<>();
    private static final Map<ModifierLayer<?>, SpeedModifier> SPEED_MODIFIERS = new WeakHashMap<>();
    private static boolean registered = false;

    private PlayerAnimBridge() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER_ID,
                42,
                player -> {
                    SpeedModifier speed = new SpeedModifier(1.0f);
                    ModifierLayer<IAnimation> layer = new ModifierLayer<>(null, speed);
                    SPEED_MODIFIERS.put(layer, speed);
                    return layer;
                });
    }

    public static void invalidateCache() {
        CACHE.clear();
    }

    public static void play(UUID targetUuid, String rawId, boolean firstPerson, float speed, int fadeTicks) {
        ResourceLocation id = normalizeId(rawId);
        if (id == null) {
            return;
        }
        KeyframeAnimation anim = resolveAnimation(id);
        if (anim == null) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Анимация '{}' не найдена в ресурсах или реестре PlayerAnimator", id);
            return;
        }
        AbstractClientPlayer target = findPlayer(targetUuid);
        if (target == null) {
            return;
        }
        ModifierLayer<IAnimation> layer = getLayer(target);
        if (layer == null) {
            return;
        }
        SpeedModifier speedMod = SPEED_MODIFIERS.get(layer);
        if (speedMod != null) {
            speedMod.speed = Math.max(0.05f, Math.min(10.0f, speed));
        }

        KeyframeAnimationPlayer playerAnim = new KeyframeAnimationPlayer(anim);
        playerAnim.setFirstPersonMode(firstPerson ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.DISABLED);
        playerAnim.setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));

        if (fadeTicks > 0) {
            layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fadeTicks, Ease.INOUTSINE), playerAnim);
        } else {
            layer.setAnimation(playerAnim);
        }
    }

    public static void stop(UUID targetUuid, int fadeTicks) {
        AbstractClientPlayer target = findPlayer(targetUuid);
        if (target == null) {
            return;
        }
        ModifierLayer<IAnimation> layer = getLayer(target);
        if (layer == null) {
            return;
        }
        if (fadeTicks > 0) {
            layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fadeTicks, Ease.INOUTSINE), null);
        } else {
            layer.setAnimation(null);
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static ModifierLayer<IAnimation> getLayer(AbstractClientPlayer player) {
        IAnimation anim = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER_ID);
        if (anim instanceof ModifierLayer<?> ml) {
            return (ModifierLayer<IAnimation>) ml;
        }
        return null;
    }

    @Nullable
    private static AbstractClientPlayer findPlayer(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (AbstractClientPlayer p : mc.level.players()) {
                if (p.getUUID().equals(uuid)) {
                    return p;
                }
            }
        }
        if (mc.player != null && mc.player.getUUID().equals(uuid)) {
            return mc.player;
        }
        return null;
    }

    @Nullable
    private static ResourceLocation normalizeId(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.strip().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return null;
        }
        int colon = s.indexOf(':');
        String ns = colon >= 0 ? s.substring(0, colon) : ScriptCraft.MODID;
        String path = colon >= 0 ? s.substring(colon + 1) : s;
        if (path.startsWith("player_animation/")) {
            path = path.substring("player_animation/".length());
        } else if (path.startsWith("player_animations/")) {
            path = path.substring("player_animations/".length());
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - 5);
        }
        return ResourceLocation.tryBuild(ns, path);
    }

    @Nullable
    private static KeyframeAnimation resolveAnimation(ResourceLocation id) {
        KeyframeAnimation cached = CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        KeyframeAnimation reg = PlayerAnimationRegistry.getAnimation(id);
        if (reg != null) {
            CACHE.put(id, reg);
            return reg;
        }
        Path cacheRoot = ModPaths.cache().resolve("assets").resolve(id.getNamespace());
        for (String folder : List.of("player_animation", "player_animations")) {
            Path file = cacheRoot.resolve(folder).resolve(id.getPath() + ".json");
            if (Files.isRegularFile(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    List<KeyframeAnimation> list = AnimationSerializing.deserializeAnimation(reader);
                    if (list != null && !list.isEmpty()) {
                        KeyframeAnimation loaded = list.get(0);
                        CACHE.put(id, loaded);
                        return loaded;
                    }
                } catch (Exception e) {
                    ScriptCraft.LOGGER.warn("[ScriptCraft] Ошибка чтения анимации {}: {}", file, e.toString());
                }
            }
        }
        return null;
    }
}
