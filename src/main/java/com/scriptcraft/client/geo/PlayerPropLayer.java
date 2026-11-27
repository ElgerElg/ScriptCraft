package com.scriptcraft.client.geo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.scriptcraft.network.PropAttachPacket;
import com.scriptcraft.script.runtime.ItemStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Слой рендера 3D-аттачей (Props) на костях игроков и фейковых NPC:
 * привязывает любые .geo.json модели (с анимациями и _glowmask.png) или предметы к голове, спине/торсу, рукам и ногам!
 */
@OnlyIn(Dist.CLIENT)
public class PlayerPropLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    public record ClientProp(
            String slotId,
            String bone,
            boolean itemMode,
            String modelOrItem,
            String texture,
            String animation,
            float ox, float oy, float oz,
            float rx, float ry, float rz,
            float scale
    ) {
    }

    private static final Map<UUID, Map<String, ClientProp>> CLIENT_PROPS = new ConcurrentHashMap<>();

    public PlayerPropLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    public static void handlePacket(PropAttachPacket p) {
        switch (p.action()) {
            case PropAttachPacket.CLEAR_ALL -> CLIENT_PROPS.clear();
            case PropAttachPacket.CLEAR_ENTITY -> CLIENT_PROPS.remove(p.targetUuid());
            case PropAttachPacket.DETACH -> {
                Map<String, ClientProp> m = CLIENT_PROPS.get(p.targetUuid());
                if (m != null) {
                    m.remove(p.slotId());
                    if (m.isEmpty()) CLIENT_PROPS.remove(p.targetUuid());
                }
            }
            case PropAttachPacket.ATTACH -> CLIENT_PROPS
                    .computeIfAbsent(p.targetUuid(), u -> new ConcurrentHashMap<>())
                    .put(p.slotId(), new ClientProp(
                            p.slotId(), p.bone(), p.itemMode(), p.modelOrItem(), p.texture(), p.animation(),
                            p.ox(), p.oy(), p.oz(), p.rx(), p.ry(), p.rz(), p.scale()));
            default -> {
            }
        }
    }

    public static void clearAll() {
        CLIENT_PROPS.clear();
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        Map<String, ClientProp> props = CLIENT_PROPS.get(entity.getUUID());
        if (props == null || props.isEmpty()) {
            return;
        }
        M humanoid = this.getParentModel();
        float lifeSec = (entity.tickCount + partialTick) / 20.0f;

        for (ClientProp prop : props.values()) {
            ModelPart part = switch (prop.bone()) {
                case "head" -> humanoid.head;
                case "right_arm" -> humanoid.rightArm;
                case "left_arm" -> humanoid.leftArm;
                case "right_leg" -> humanoid.rightLeg;
                case "left_leg" -> humanoid.leftLeg;
                default -> humanoid.body;
            };
            if (!part.visible) continue;

            poseStack.pushPose();
            part.translateAndRotate(poseStack);
            // Переходим в правую систему координат GeoRenderer (+X влево модели / вправо на экране, +Y вверх, -Z вперёд)
            poseStack.scale(-1f, -1f, 1f);
            // Смещение задаётся в пикселях (16 px = 1 блок): +X вправо от центра, +Y вверх, +Z назад (к спине), -Z вперёд
            poseStack.translate(-prop.ox() / 16f, prop.oy() / 16f, prop.oz() / 16f);
            if (prop.rz() != 0f) poseStack.mulPose(Axis.ZP.rotationDegrees(prop.rz()));
            if (prop.ry() != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(prop.ry()));
            if (prop.rx() != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(prop.rx()));
            if (prop.scale() != 1.0f) {
                poseStack.scale(prop.scale(), prop.scale(), prop.scale());
            }

            if (prop.itemMode()) {
                ItemStack stack = ItemStacks.tryCreate(prop.modelOrItem(), 1);
                if (!stack.isEmpty()) {
                    Minecraft.getInstance().getItemRenderer().renderStatic(
                            entity, stack, ItemDisplayContext.FIXED, false,
                            poseStack, buffer, entity.level(), packedLight, OverlayTexture.NO_OVERLAY, entity.getId());
                }
            } else {
                GeoModelData.GeoModel model = GeoRenderer.resolveModel(prop.modelOrItem());
                if (model != null) {
                    String texSpec = prop.texture().isBlank() ? prop.modelOrItem() : prop.texture();
                    ResourceLocation tex = GeoRenderer.resolveTexture(texSpec, prop.modelOrItem(), "entity");
                    ResourceLocation glowTex = GeoRenderer.resolveGlowTexture(texSpec, prop.modelOrItem(), "entity");
                    GeoModelData.AnimationClip clip = null;
                    if (!prop.animation().isBlank()) {
                        GeoModelData.AnimationFile af = GeoRenderer.resolveAnimations(prop.modelOrItem());
                        if (af != null) clip = af.find(prop.animation());
                    }
                    GeoModelData.MolangVars vars = new GeoModelData.MolangVars(lifeSec, lifeSec, 0f, headPitch, netHeadYaw);
                    GeoRenderer.renderModel(model, clip, lifeSec, vars, poseStack, buffer,
                            tex, glowTex, packedLight, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f, null);
                }
            }
            poseStack.popPose();
        }
    }
}
