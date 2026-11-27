package com.scriptcraft.client.geo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.scriptcraft.registry.ScriptItem;
import com.scriptcraft.script.parser.ItemDefinition;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Слой рендера 3D Geo-брони, одежды и РП-косметики ({@code define armor / define item ... model: ...}):
 * синхронизирует каждую кость брони напрямую с частями {@link HumanoidModel}, поэтому 3D-броня идеально
 * повторяет любые движения игрока и анимации {@code PlayerAnimator}!
 */
@OnlyIn(Dist.CLIENT)
public class GeoArmorLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    public GeoArmorLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        float lifeSec = (entity.tickCount + partialTick) / 20.0f;
        renderSlot(poseStack, buffer, packedLight, entity, EquipmentSlot.HEAD, lifeSec, netHeadYaw, headPitch);
        renderSlot(poseStack, buffer, packedLight, entity, EquipmentSlot.CHEST, lifeSec, netHeadYaw, headPitch);
        renderSlot(poseStack, buffer, packedLight, entity, EquipmentSlot.LEGS, lifeSec, netHeadYaw, headPitch);
        renderSlot(poseStack, buffer, packedLight, entity, EquipmentSlot.FEET, lifeSec, netHeadYaw, headPitch);
    }

    private void renderSlot(PoseStack pose, MultiBufferSource buffer, int light, T entity,
                            EquipmentSlot slot, float lifeSec, float netHeadYaw, float headPitch) {
        ItemStack stack = entity.getItemBySlot(slot);
        if (stack.isEmpty() || !(stack.getItem() instanceof ScriptItem si)) {
            return;
        }
        ItemDefinition def = si.definition();
        if (def == null || !def.isGeo() || def.armorSlot() != slot) {
            return;
        }

        String modelSpec = def.geoModel();
        GeoModelData.GeoModel model = GeoRenderer.resolveModel(modelSpec);
        if (model == null) return;

        String texSpec = def.geoTexture() != null ? def.geoTexture() : def.id();
        ResourceLocation tex = GeoRenderer.resolveTexture(texSpec, modelSpec, "armor");
        ResourceLocation glowTex = GeoRenderer.resolveGlowTexture(texSpec, modelSpec, "armor");

        String animSpec = def.geoAnimation() != null ? def.geoAnimation() : modelSpec;
        GeoModelData.AnimationFile animFile = GeoRenderer.resolveAnimations(animSpec);
        GeoModelData.AnimationClip clip = (animFile != null && def.idleAnimation() != null)
                ? animFile.find(def.idleAnimation()) : null;

        GeoModelData.MolangVars vars = new GeoModelData.MolangVars(lifeSec, lifeSec, 0f, headPitch, netHeadYaw);
        M humanoid = this.getParentModel();
        int overlay = OverlayTexture.NO_OVERLAY;

        switch (slot) {
            case HEAD -> renderPartBone(pose, buffer, light, overlay, humanoid.head,
                    model, new String[]{"armorHead", "head", "helmet"}, 0f, 24f, 0f, clip, lifeSec, vars, tex, glowTex);
            case CHEST -> {
                renderPartBone(pose, buffer, light, overlay, humanoid.body,
                        model, new String[]{"armorBody", "body", "chestplate", "torso"}, 0f, 24f, 0f, clip, lifeSec, vars, tex, glowTex);
                renderPartBone(pose, buffer, light, overlay, humanoid.rightArm,
                        model, new String[]{"armorRightArm", "right_arm", "rightArm"}, -5f, 22f, 0f, clip, lifeSec, vars, tex, glowTex);
                renderPartBone(pose, buffer, light, overlay, humanoid.leftArm,
                        model, new String[]{"armorLeftArm", "left_arm", "leftArm"}, 5f, 22f, 0f, clip, lifeSec, vars, tex, glowTex);
            }
            case LEGS -> {
                renderPartBone(pose, buffer, light, overlay, humanoid.body,
                        model, new String[]{"armorPelvis", "pelvis", "waist"}, 0f, 24f, 0f, clip, lifeSec, vars, tex, glowTex);
                renderPartBone(pose, buffer, light, overlay, humanoid.rightLeg,
                        model, new String[]{"armorRightLeg", "right_leg", "rightLeg"}, -2f, 12f, 0f, clip, lifeSec, vars, tex, glowTex);
                renderPartBone(pose, buffer, light, overlay, humanoid.leftLeg,
                        model, new String[]{"armorLeftLeg", "left_leg", "leftLeg"}, 2f, 12f, 0f, clip, lifeSec, vars, tex, glowTex);
            }
            case FEET -> {
                renderPartBone(pose, buffer, light, overlay, humanoid.rightLeg,
                        model, new String[]{"armorRightBoot", "right_boot", "rightBoot", "armorRightFoot"}, -2f, 12f, 0f, clip, lifeSec, vars, tex, glowTex);
                renderPartBone(pose, buffer, light, overlay, humanoid.leftLeg,
                        model, new String[]{"armorLeftBoot", "left_boot", "leftBoot", "armorLeftFoot"}, 2f, 12f, 0f, clip, lifeSec, vars, tex, glowTex);
            }
            default -> {
            }
        }
    }

    private static void renderPartBone(
            PoseStack pose, MultiBufferSource buffer, int light, int overlay,
            ModelPart humanoidPart, GeoModelData.GeoModel model, String[] aliases,
            float anchorX, float anchorY, float anchorZ,
            GeoModelData.AnimationClip clip, float lifeSec, GeoModelData.MolangVars vars,
            ResourceLocation tex, ResourceLocation glowTex
    ) {
        if (!humanoidPart.visible) return;
        GeoModelData.Bone found = GeoRenderer.findBoneIgnoreCase(model, aliases);
        if (found == null) return;
        pose.pushPose();
        humanoidPart.translateAndRotate(pose);
        GeoRenderer.renderSingleArmorBone(model, found.name, anchorX, anchorY, anchorZ,
                clip, lifeSec, vars, pose, buffer, tex, glowTex, light, overlay);
        pose.popPose();
    }
}
