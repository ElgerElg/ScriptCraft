package com.scriptcraft.client.geo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.scriptcraft.ModPaths;
import com.scriptcraft.ScriptCraft;
import com.scriptcraft.client.GuiEntityRenderer;
import com.scriptcraft.client.StudioAnimModel;
import com.scriptcraft.entity.ScriptFakeNpcEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Рендерер фейковых игроков-NPC ({@link ScriptFakeNpcEntity}):
 * - авто-выбор модели Steve (wide) / Alex (slim) и скинов по нику / URL / из ресурсов;
 * - полная экипировка (ванильная броня, 3D Geo-броня, предметы и 3D-оружие в руках, аттачи на костях);
 * - воспроизведение анимаций PlayerAnimator / Emotecraft / Studio из {@code player_animation/*.json}!
 */
@OnlyIn(Dist.CLIENT)
public class ScriptFakeNpcRenderer extends MobRenderer<ScriptFakeNpcEntity, PlayerModel<ScriptFakeNpcEntity>> {

    private static final Map<String, Optional<StudioAnimModel.AnimDoc>> NPC_ANIM_CACHE = new ConcurrentHashMap<>();

    private final PlayerModel<ScriptFakeNpcEntity> wideModel;
    private final PlayerModel<ScriptFakeNpcEntity> slimModel;

    public ScriptFakeNpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new NpcPlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wideModel = this.model;
        this.slimModel = new NpcPlayerModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);

        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
        this.addLayer(new GeoArmorLayer<>(this));
        this.addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
        this.addLayer(new ElytraLayer<>(this, ctx.getModelSet()));
        this.addLayer(new CustomHeadLayer<>(this, ctx.getModelSet(), ctx.getItemInHandRenderer()));
        this.addLayer(new PlayerPropLayer<>(this));
    }

    public static void invalidateCache() {
        NPC_ANIM_CACHE.clear();
    }

    @Override
    public void render(ScriptFakeNpcEntity npc, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        GuiEntityRenderer.SkinInfo skin = GuiEntityRenderer.resolveSkin(npc.getSkinSource());
        String style = npc.getModelStyle().toLowerCase(Locale.ROOT);
        boolean slim = switch (style) {
            case "slim", "alex" -> true;
            case "wide", "steve", "default" -> false;
            default -> skin.slim();
        };
        this.model = slim ? slimModel : wideModel;
        this.model.crouching = npc.isShiftKeyDown();

        super.render(npc, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    protected void scale(ScriptFakeNpcEntity entity, PoseStack poseStack, float partialTickTime) {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Override
    public ResourceLocation getTextureLocation(ScriptFakeNpcEntity npc) {
        return GuiEntityRenderer.resolveSkin(npc.getSkinSource()).texture();
    }

    /** Модель игрока с наложением кастомных анимаций {@link StudioAnimModel.AnimDoc} поверх ванильной позы. */
    private static final class NpcPlayerModel extends PlayerModel<ScriptFakeNpcEntity> {
        NpcPlayerModel(ModelPart root, boolean slim) {
            super(root, slim);
        }

        @Override
        public void setupAnim(ScriptFakeNpcEntity npc, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch) {
            // Сбрасываем смещения пивотов к стандартным ванильным значениям перед расчётом позы
            resetPartPos(this.head, 0f, 0f, 0f);
            resetPartPos(this.body, 0f, 0f, 0f);
            resetPartPos(this.rightArm, -5f, 2f, 0f);
            resetPartPos(this.leftArm, 5f, 2f, 0f);
            resetPartPos(this.rightLeg, -1.9f, 12f, 0f);
            resetPartPos(this.leftLeg, 1.9f, 12f, 0f);

            super.setupAnim(npc, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

            String animId = npc.getActiveAnim();
            if (animId != null && !animId.isBlank()) {
                StudioAnimModel.AnimDoc doc = resolveNpcAnim(animId);
                if (doc != null) {
                    float partial = ageInTicks - npc.tickCount;
                    float rawTicks = Math.max(0f, (npc.tickCount - npc.clientAnimStartTick + partial) * npc.getAnimSpeed());
                    int endTick = Math.max(1, doc.endTick);
                    boolean finished = !doc.looped && rawTicks > doc.stopTick;
                    if (!finished) {
                        float evalTick;
                        if (doc.looped && rawTicks > endTick) {
                            int span = Math.max(1, endTick - doc.returnTick);
                            evalTick = doc.returnTick + ((rawTicks - doc.returnTick) % span);
                        } else {
                            evalTick = Math.min(rawTicks, endTick);
                        }
                        applyBone(doc, StudioAnimModel.BoneId.HEAD, this.head, evalTick);
                        applyBone(doc, StudioAnimModel.BoneId.TORSO, this.body, evalTick);
                        applyBone(doc, StudioAnimModel.BoneId.BODY, this.body, evalTick);
                        applyBone(doc, StudioAnimModel.BoneId.RIGHT_ARM, this.rightArm, evalTick);
                        applyBone(doc, StudioAnimModel.BoneId.LEFT_ARM, this.leftArm, evalTick);
                        applyBone(doc, StudioAnimModel.BoneId.RIGHT_LEG, this.rightLeg, evalTick);
                        applyBone(doc, StudioAnimModel.BoneId.LEFT_LEG, this.leftLeg, evalTick);

                        this.hat.copyFrom(this.head);
                        this.jacket.copyFrom(this.body);
                        this.rightSleeve.copyFrom(this.rightArm);
                        this.leftSleeve.copyFrom(this.leftArm);
                        this.rightPants.copyFrom(this.rightLeg);
                        this.leftPants.copyFrom(this.leftLeg);
                    }
                }
            }
        }

        private static void resetPartPos(ModelPart p, float x, float y, float z) {
            p.x = x;
            p.y = y;
            p.z = z;
            p.xRot = 0f;
            p.yRot = 0f;
            p.zRot = 0f;
        }

        private static void applyBone(StudioAnimModel.AnimDoc doc, StudioAnimModel.BoneId bone, ModelPart part, float tick) {
            if (doc.hasKeys(bone, StudioAnimModel.Channel.ROT_X)) {
                part.xRot = (float) Math.toRadians(doc.eval(bone, StudioAnimModel.Channel.ROT_X, tick));
            }
            if (doc.hasKeys(bone, StudioAnimModel.Channel.ROT_Y)) {
                part.yRot = (float) Math.toRadians(doc.eval(bone, StudioAnimModel.Channel.ROT_Y, tick));
            }
            if (doc.hasKeys(bone, StudioAnimModel.Channel.ROT_Z)) {
                part.zRot = (float) Math.toRadians(doc.eval(bone, StudioAnimModel.Channel.ROT_Z, tick));
            }
            if (doc.hasKeys(bone, StudioAnimModel.Channel.POS_X)) {
                part.x += (bone == StudioAnimModel.BoneId.BODY ? -1f : 1f) * doc.eval(bone, StudioAnimModel.Channel.POS_X, tick);
            }
            if (doc.hasKeys(bone, StudioAnimModel.Channel.POS_Y)) {
                part.y -= doc.eval(bone, StudioAnimModel.Channel.POS_Y, tick);
            }
            if (doc.hasKeys(bone, StudioAnimModel.Channel.POS_Z)) {
                part.z += doc.eval(bone, StudioAnimModel.Channel.POS_Z, tick);
            }
        }
    }

    @Nullable
    private static StudioAnimModel.AnimDoc resolveNpcAnim(String rawId) {
        String key = rawId.strip().toLowerCase(Locale.ROOT);
        Optional<StudioAnimModel.AnimDoc> cached = NPC_ANIM_CACHE.get(key);
        if (cached != null) return cached.orElse(null);

        int colon = key.indexOf(':');
        String ns = colon >= 0 ? key.substring(0, colon) : ScriptCraft.MODID;
        String path = colon >= 0 ? key.substring(colon + 1) : key;
        if (path.startsWith("player_animation/")) path = path.substring("player_animation/".length());
        else if (path.startsWith("player_animations/")) path = path.substring("player_animations/".length());
        if (path.endsWith(".json")) path = path.substring(0, path.length() - 5);

        String[] candidates = {
                "player_animation/" + path + ".json",
                "player_animations/" + path + ".json",
                path + ".json"
        };

        String json = null;
        for (Path base : new Path[]{ModPaths.cacheAssets(), ModPaths.resources().resolve("assets")}) {
            if (!Files.isDirectory(base)) continue;
            for (String c : candidates) {
                Path f = base.resolve(ns).resolve(c);
                if (Files.isRegularFile(f)) {
                    try {
                        json = Files.readString(f, StandardCharsets.UTF_8);
                        break;
                    } catch (Exception ignored) {
                    }
                }
            }
            if (json != null) break;
        }
        if (json == null && Minecraft.getInstance().getResourceManager() != null) {
            for (String c : candidates) {
                ResourceLocation rl = ResourceLocation.tryBuild(ns, c);
                if (rl != null) {
                    Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(rl);
                    if (res.isPresent()) {
                        try (InputStream in = res.get().open()) {
                            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                            break;
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }

        StudioAnimModel.AnimDoc doc = null;
        if (json != null) {
            StudioAnimModel.AnimDoc parsed = new StudioAnimModel.AnimDoc();
            parsed.namespace = ns;
            parsed.fileName = path;
            if (StudioAnimModel.deserialize(json, parsed)) {
                doc = parsed;
            }
        }
        NPC_ANIM_CACHE.put(key, Optional.ofNullable(doc));
        return doc;
    }
}
