package com.scriptcraft.client;

import com.scriptcraft.ModPaths;
import com.scriptcraft.ScriptCraft;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.data.AnimationFormat;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.data.gson.AnimationSerializing;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
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
import java.io.StringReader;
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
    private static final ResourceLocation STUDIO_LAYER_ID = new ResourceLocation(ScriptCraft.MODID, "studio_preview");
    private static final Map<ResourceLocation, KeyframeAnimation> CACHE = new HashMap<>();
    private static final Map<ModifierLayer<?>, SpeedModifier> SPEED_MODIFIERS = new WeakHashMap<>();
    private static final StudioLivePreview STUDIO_PREVIEW = new StudioLivePreview();
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
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                STUDIO_LAYER_ID,
                1000,
                player -> new ModifierLayer<>(null));
    }

    public static void invalidateCache() {
        CACHE.clear();
    }

    // --- Живой предпросмотр 3D-редактора в Студии (с поддержкой Bendy-lib) ---

    public static void applyStudioPreview(@Nullable StudioAnimModel.AnimDoc doc, float tick, boolean firstPerson) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ModifierLayer<IAnimation> layer = getLayerById(mc.player, STUDIO_LAYER_ID);
        if (layer == null) return;
        if (doc == null) {
            STUDIO_PREVIEW.active = false;
            layer.setAnimation(null);
            return;
        }
        STUDIO_PREVIEW.doc = doc;
        STUDIO_PREVIEW.tick = tick;
        STUDIO_PREVIEW.firstPerson = firstPerson;
        STUDIO_PREVIEW.active = true;
        if (layer.getAnimation() != STUDIO_PREVIEW) {
            layer.setAnimation(STUDIO_PREVIEW);
        }
    }

    public static void clearStudioPreview() {
        STUDIO_PREVIEW.active = false;
        STUDIO_PREVIEW.doc = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ModifierLayer<IAnimation> layer = getLayerById(mc.player, STUDIO_LAYER_ID);
            if (layer != null && layer.getAnimation() == STUDIO_PREVIEW) {
                layer.setAnimation(null);
            }
        }
    }

    /** Проиграть текущую редактируемую в Студии анимацию прямо на локальном игроке (для проверки в мире). */
    public static void playInMemoryOnLocalPlayer(StudioAnimModel.AnimDoc doc, boolean firstPerson, float speed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || doc == null) return;
        clearStudioPreview();
        KeyframeAnimation anim = buildKeyframeAnimation(doc);
        ResourceLocation id = ResourceLocation.tryBuild(doc.namespace, doc.fileName);
        if (id != null) {
            CACHE.put(id, anim);
        }
        ModifierLayer<IAnimation> layer = getLayerById(mc.player, LAYER_ID);
        if (layer == null) return;
        SpeedModifier speedMod = SPEED_MODIFIERS.get(layer);
        if (speedMod != null) {
            speedMod.speed = Math.max(0.05f, Math.min(10.0f, speed));
        }
        KeyframeAnimationPlayer playerAnim = new KeyframeAnimationPlayer(anim);
        playerAnim.setFirstPersonMode(firstPerson ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.DISABLED);
        playerAnim.setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));
        layer.setAnimation(playerAnim);
    }

    // --- Конвертация StudioAnimModel.AnimDoc <-> KeyframeAnimation / JSON ---

    public static KeyframeAnimation buildKeyframeAnimation(StudioAnimModel.AnimDoc doc) {
        KeyframeAnimation.AnimationBuilder builder = new KeyframeAnimation.AnimationBuilder(AnimationFormat.JSON_EMOTECRAFT);
        builder.name = doc.displayName == null || doc.displayName.isBlank() ? doc.fileName : doc.displayName;
        builder.author = doc.author == null || doc.author.isBlank() ? "ScriptCraft" : doc.author;
        builder.description = doc.description == null ? "" : doc.description;
        builder.uuid = doc.uuid != null ? doc.uuid : UUID.randomUUID();
        builder.beginTick = Math.max(0, doc.beginTick);
        builder.endTick = Math.max(1, doc.endTick);
        builder.stopTick = Math.max(builder.endTick + 1, doc.stopTick);
        builder.isLooped = doc.looped;
        builder.returnTick = Math.max(0, Math.min(builder.endTick, doc.returnTick));
        builder.isEasingBefore = doc.easingBefore;

        for (StudioAnimModel.BoneId bone : StudioAnimModel.BoneId.values()) {
            KeyframeAnimation.StateCollection sc = builder.getPart(bone.partKey);
            if (sc == null) continue;
            populateState(sc.pitch, doc, bone, StudioAnimModel.Channel.ROT_X, true);
            populateState(sc.yaw, doc, bone, StudioAnimModel.Channel.ROT_Y, true);
            populateState(sc.roll, doc, bone, StudioAnimModel.Channel.ROT_Z, true);
            populateState(sc.x, doc, bone, StudioAnimModel.Channel.POS_X, false);
            populateState(sc.y, doc, bone, StudioAnimModel.Channel.POS_Y, false);
            populateState(sc.z, doc, bone, StudioAnimModel.Channel.POS_Z, false);
            if (sc.isBendable() && bone.bendable) {
                populateState(sc.bendDirection, doc, bone, StudioAnimModel.Channel.BEND_DIR, true);
                populateState(sc.bend, doc, bone, StudioAnimModel.Channel.BEND_ANG, true);
            }
        }
        return builder.build();
    }

    private static void populateState(KeyframeAnimation.StateCollection.State state,
                                      StudioAnimModel.AnimDoc doc,
                                      StudioAnimModel.BoneId bone,
                                      StudioAnimModel.Channel ch,
                                      boolean angle) {
        if (state == null) return;
        var map = doc.track(bone, ch);
        if (map.isEmpty()) {
            state.setEnabled(false);
            return;
        }
        state.setEnabled(true);
        for (StudioAnimModel.KeyPoint kp : map.values()) {
            float raw = angle ? (float) Math.toRadians(kp.value()) : (state.defaultValue + kp.value());
            Ease ease = toKosmxEase(kp.ease());
            state.addKeyFrame(kp.tick(), raw, ease);
        }
    }

    public static String serializeDoc(StudioAnimModel.AnimDoc doc) {
        KeyframeAnimation anim = buildKeyframeAnimation(doc);
        ResourceLocation id = ResourceLocation.tryBuild(doc.namespace, doc.fileName);
        if (id != null) {
            CACHE.put(id, anim);
        }
        return AnimationSerializing.serializeAnimation(anim);
    }

    public static boolean deserializeInto(String json, StudioAnimModel.AnimDoc out) {
        List<KeyframeAnimation> list = AnimationSerializing.deserializeAnimation(new StringReader(json));
        if (list == null || list.isEmpty()) {
            return false;
        }
        KeyframeAnimation anim = list.get(0);
        out.clearTracks();
        out.beginTick = Math.max(0, anim.beginTick);
        out.endTick = Math.max(1, anim.endTick);
        out.stopTick = Math.max(out.endTick + 1, anim.stopTick);
        out.looped = anim.isInfinite;
        out.returnTick = Math.max(0, anim.returnToTick);
        out.easingBefore = anim.isEasingBefore;
        out.uuid = anim.getUuid();
        if (anim.extraData != null) {
            Object n = anim.extraData.get("name");
            if (n instanceof String s && !s.isBlank()) out.displayName = s;
            Object a = anim.extraData.get("author");
            if (a instanceof String s && !s.isBlank()) out.author = s;
            Object d = anim.extraData.get("description");
            if (d instanceof String s) out.description = s;
        }
        for (StudioAnimModel.BoneId bone : StudioAnimModel.BoneId.values()) {
            KeyframeAnimation.StateCollection sc = anim.getPart(bone.partKey);
            if (sc == null) continue;
            extractState(sc.pitch, out, bone, StudioAnimModel.Channel.ROT_X, true);
            extractState(sc.yaw, out, bone, StudioAnimModel.Channel.ROT_Y, true);
            extractState(sc.roll, out, bone, StudioAnimModel.Channel.ROT_Z, true);
            extractState(sc.x, out, bone, StudioAnimModel.Channel.POS_X, false);
            extractState(sc.y, out, bone, StudioAnimModel.Channel.POS_Y, false);
            extractState(sc.z, out, bone, StudioAnimModel.Channel.POS_Z, false);
            if (bone.bendable) {
                extractState(sc.bendDirection, out, bone, StudioAnimModel.Channel.BEND_DIR, true);
                extractState(sc.bend, out, bone, StudioAnimModel.Channel.BEND_ANG, true);
            }
        }
        return true;
    }

    private static void extractState(KeyframeAnimation.StateCollection.State state,
                                     StudioAnimModel.AnimDoc out,
                                     StudioAnimModel.BoneId bone,
                                     StudioAnimModel.Channel ch,
                                     boolean angle) {
        if (state == null || !state.isEnabled()) return;
        List<KeyframeAnimation.KeyFrame> kfs = state.getKeyFrames();
        if (kfs == null || kfs.isEmpty()) return;
        for (KeyframeAnimation.KeyFrame kf : kfs) {
            float uiVal = angle ? (float) Math.toDegrees(kf.value) : (kf.value - state.defaultValue);
            StudioAnimModel.EaseCurve ease = kf.ease == null
                    ? StudioAnimModel.EaseCurve.LINEAR
                    : StudioAnimModel.EaseCurve.parse(kf.ease.name());
            out.putKey(bone, ch, kf.tick, uiVal, ease);
        }
    }

    private static Ease toKosmxEase(StudioAnimModel.EaseCurve curve) {
        if (curve == null) return Ease.LINEAR;
        try {
            return Ease.valueOf(curve.name());
        } catch (IllegalArgumentException e) {
            return Ease.LINEAR;
        }
    }

    private static final class StudioLivePreview implements IAnimation {
        volatile StudioAnimModel.AnimDoc doc;
        volatile float tick;
        volatile boolean active;
        volatile boolean firstPerson = true;

        @Override
        public boolean isActive() {
            return active && doc != null;
        }

        @Override
        public void setupAnim(float tickDelta) {
        }

        @Override
        public FirstPersonMode getFirstPersonMode(float tickDelta) {
            return firstPerson ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.DISABLED;
        }

        @Override
        public FirstPersonConfiguration getFirstPersonConfiguration(float tickDelta) {
            return new FirstPersonConfiguration(true, true, true, true);
        }

        @Override
        public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value0) {
            StudioAnimModel.AnimDoc d = this.doc;
            if (!active || d == null) return value0;
            StudioAnimModel.BoneId bone = StudioAnimModel.BoneId.byPartKey(modelName);
            if (bone == null || !d.isBoneVisible(bone)) return value0;
            float t = this.tick;
            return switch (type) {
                case POSITION -> {
                    float x = d.hasKeys(bone, StudioAnimModel.Channel.POS_X)
                            ? bone.defX + d.eval(bone, StudioAnimModel.Channel.POS_X, t) : value0.getX();
                    float y = d.hasKeys(bone, StudioAnimModel.Channel.POS_Y)
                            ? bone.defY + d.eval(bone, StudioAnimModel.Channel.POS_Y, t) : value0.getY();
                    float z = d.hasKeys(bone, StudioAnimModel.Channel.POS_Z)
                            ? bone.defZ + d.eval(bone, StudioAnimModel.Channel.POS_Z, t) : value0.getZ();
                    yield new Vec3f(x, y, z);
                }
                case ROTATION -> {
                    float rx = d.hasKeys(bone, StudioAnimModel.Channel.ROT_X)
                            ? (float) Math.toRadians(d.eval(bone, StudioAnimModel.Channel.ROT_X, t)) : value0.getX();
                    float ry = d.hasKeys(bone, StudioAnimModel.Channel.ROT_Y)
                            ? (float) Math.toRadians(d.eval(bone, StudioAnimModel.Channel.ROT_Y, t)) : value0.getY();
                    float rz = d.hasKeys(bone, StudioAnimModel.Channel.ROT_Z)
                            ? (float) Math.toRadians(d.eval(bone, StudioAnimModel.Channel.ROT_Z, t)) : value0.getZ();
                    yield new Vec3f(rx, ry, rz);
                }
                case BEND -> {
                    if (!bone.bendable) yield value0;
                    float dir = d.hasKeys(bone, StudioAnimModel.Channel.BEND_DIR)
                            ? (float) Math.toRadians(d.eval(bone, StudioAnimModel.Channel.BEND_DIR, t)) : value0.getX();
                    float ang = d.hasKeys(bone, StudioAnimModel.Channel.BEND_ANG)
                            ? (float) Math.toRadians(d.eval(bone, StudioAnimModel.Channel.BEND_ANG, t)) : value0.getY();
                    yield new Vec3f(dir, ang, 0f);
                }
            };
        }
    }

    // --- Штатное воспроизведение анимаций из скриптов ---

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
        ModifierLayer<IAnimation> layer = getLayerById(target, LAYER_ID);
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
        ModifierLayer<IAnimation> layer = getLayerById(target, LAYER_ID);
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
    private static ModifierLayer<IAnimation> getLayerById(AbstractClientPlayer player, ResourceLocation layerId) {
        IAnimation anim = PlayerAnimationAccess.getPlayerAssociatedData(player).get(layerId);
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
