package com.scriptcraft.client.geo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.scriptcraft.registry.ScriptBlockItem;
import com.scriptcraft.registry.ScriptItem;
import com.scriptcraft.script.parser.BlockDefinition;
import com.scriptcraft.script.parser.ItemDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 3D-рендерер предметов, оружия и предметов-блоков ({@code define item / define block ... model: ...})
 * от 1-го лица, от 3-го лица, в инвентаре (GUI), в рамке и на земле!
 */
@OnlyIn(Dist.CLIENT)
public class ScriptGeoItemRenderer extends BlockEntityWithoutLevelRenderer {

    private record HandKey(UUID playerUuid, boolean offhand) {
    }

    private record ActiveHandAnim(String animName, int loopMode, float speed, double startSec) {
    }

    private static final Map<HandKey, ActiveHandAnim> ACTIVE_ANIMS = new ConcurrentHashMap<>();
    private static ScriptGeoItemRenderer instance;

    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return getInstance();
        }
    };

    public ScriptGeoItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static ScriptGeoItemRenderer getInstance() {
        if (instance == null) {
            instance = new ScriptGeoItemRenderer();
        }
        return instance;
    }

    public static void triggerItemAnim(UUID targetUuid, boolean offhand, String animName, int loopMode, float speed) {
        HandKey key = new HandKey(targetUuid, offhand);
        if (animName == null || animName.isBlank()) {
            ACTIVE_ANIMS.remove(key);
        } else {
            double now = (System.nanoTime() * 1e-9);
            ACTIVE_ANIMS.put(key, new ActiveHandAnim(animName, loopMode, Math.max(0.05f, speed), now));
        }
    }

    public static void clearAnims() {
        ACTIVE_ANIMS.clear();
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource buffer, int packedLight, int packedOverlay) {
        String modelSpec = null;
        String texSpec = null;
        String animSpec = null;
        String idleAnim = null;
        float geoScale = 1.0f;
        String defFolder = "item";

        if (stack.getItem() instanceof ScriptItem si) {
            ItemDefinition def = si.definition();
            if (def != null && def.isGeo()) {
                // Надетую на голову 3D-броню уже рисует GeoArmorLayer по кости головы — не дублируем через CustomHeadLayer!
                if (def.isArmor() && displayContext == ItemDisplayContext.HEAD) {
                    return;
                }
                modelSpec = def.geoModel();
                texSpec = def.geoTexture() != null ? def.geoTexture() : def.id();
                animSpec = def.geoAnimation() != null ? def.geoAnimation() : modelSpec;
                idleAnim = def.idleAnimation();
                geoScale = def.geoScale();
                defFolder = def.isArmor() ? "armor" : "item";
            }
        } else if (stack.getItem() instanceof ScriptBlockItem sbi) {
            BlockDefinition bdef = sbi.scriptBlock().definition();
            if (bdef != null && bdef.isGeo()) {
                modelSpec = bdef.geoModel();
                texSpec = bdef.geoTexture() != null ? bdef.geoTexture() : bdef.id();
                animSpec = bdef.geoAnimation() != null ? bdef.geoAnimation() : modelSpec;
                idleAnim = bdef.idleAnimation();
                geoScale = bdef.geoScale();
                defFolder = "block";
            }
        }

        if (modelSpec == null) {
            return;
        }
        GeoModelData.GeoModel model = GeoRenderer.resolveModel(modelSpec);
        if (model == null) {
            return;
        }

        ResourceLocation tex = GeoRenderer.resolveTexture(texSpec, modelSpec, defFolder);
        ResourceLocation glowTex = GeoRenderer.resolveGlowTexture(texSpec, modelSpec, defFolder);
        GeoModelData.AnimationFile animFile = GeoRenderer.resolveAnimations(animSpec);

        double nowSec = System.nanoTime() * 1e-9;
        float lifeSec = (float) (nowSec % 36000.0);
        GeoModelData.AnimationClip clip = null;
        float animTimeSec = lifeSec;

        boolean inHand = displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        boolean leftHand = displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;

        if (animFile != null) {
            Minecraft mc = Minecraft.getInstance();
            if (inHand && mc.player != null) {
                ActiveHandAnim active = ACTIVE_ANIMS.get(new HandKey(mc.player.getUUID(), leftHand));
                if (active != null) {
                    GeoModelData.AnimationClip cand = animFile.find(active.animName);
                    if (cand != null) {
                        float elapsed = (float) Math.max(0.0, (nowSec - active.startSec) * active.speed);
                        if (active.loopMode == 0 && cand.lengthSec() > 0.001f && elapsed >= cand.lengthSec()) {
                            ACTIVE_ANIMS.remove(new HandKey(mc.player.getUUID(), leftHand));
                        } else {
                            clip = active.loopMode == 1 && cand.loopMode() != GeoModelData.LoopMode.LOOP
                                    ? new GeoModelData.AnimationClip(cand.name(), GeoModelData.LoopMode.LOOP,
                                    cand.lengthSec(), cand.boneAnims(), cand.particleEffects(), cand.soundEffects())
                                    : cand;
                            animTimeSec = elapsed;
                        }
                    }
                }
            }
            if (clip == null && idleAnim != null && !idleAnim.isBlank()) {
                clip = animFile.find(idleAnim);
                animTimeSec = lifeSec;
            }
        }

        GeoModelData.MolangVars vars = new GeoModelData.MolangVars(animTimeSec, lifeSec, 0f, 0f, 0f);

        poseStack.pushPose();
        // Центрируем относительно куба предмета [0..1, 0..1, 0..1], как принято в GeckoLib / Blockbench item
        poseStack.translate(0.5F, 0.5F, 0.5F);
        applyDisplayContextTransform(displayContext, poseStack, stack.getItem() instanceof ScriptBlockItem);
        poseStack.translate(0.0F, -0.5F, 0.0F);
        if (geoScale != 1.0f) {
            poseStack.scale(geoScale, geoScale, geoScale);
        }

        GeoRenderer.renderModel(model, clip, animTimeSec, vars, poseStack, buffer,
                tex, glowTex, packedLight, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f, null);
        poseStack.popPose();
    }

    private static void applyDisplayContextTransform(ItemDisplayContext ctx, PoseStack pose, boolean isBlock) {
        switch (ctx) {
            case GUI -> {
                pose.mulPose(Axis.XP.rotationDegrees(25f));
                pose.mulPose(Axis.YP.rotationDegrees(isBlock ? 225f : 135f));
                float s = isBlock ? 0.625f : 0.72f;
                pose.scale(s, s, s);
            }
            case GROUND -> {
                pose.translate(0f, -0.15f, 0f);
                pose.scale(0.5f, 0.5f, 0.5f);
            }
            case FIXED -> {
                pose.mulPose(Axis.YP.rotationDegrees(180f));
                pose.scale(0.65f, 0.65f, 0.65f);
            }
            case FIRST_PERSON_RIGHT_HAND -> {
                pose.translate(0.08f, 0.05f, -0.05f);
                pose.mulPose(Axis.YP.rotationDegrees(180f));
                pose.scale(0.8f, 0.8f, 0.8f);
            }
            case FIRST_PERSON_LEFT_HAND -> {
                pose.translate(-0.08f, 0.05f, -0.05f);
                pose.mulPose(Axis.YP.rotationDegrees(180f));
                pose.scale(0.8f, 0.8f, 0.8f);
            }
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> {
                pose.translate(0f, 0.1f, -0.05f);
                pose.mulPose(Axis.YP.rotationDegrees(180f));
                pose.scale(0.75f, 0.75f, 0.75f);
            }
            default -> {
            }
        }
    }
}
