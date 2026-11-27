package com.scriptcraft.client.geo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.scriptcraft.registry.ScriptBlock;
import com.scriptcraft.registry.ScriptBlockEntity;
import com.scriptcraft.script.parser.BlockDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;

/**
 * 3D-рендерер анимированных блоков и мебели ({@code define block ... model: ...}):
 * поддерживает поворот по сторонам света (facing), idle/триггерные анимации, _glowmask.png и партиклы!
 */
@OnlyIn(Dist.CLIENT)
public class ScriptGeoBlockRenderer implements BlockEntityRenderer<ScriptBlockEntity> {

    public ScriptGeoBlockRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(ScriptBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof ScriptBlock sb)) {
            return;
        }
        BlockDefinition def = sb.definition();
        if (def == null || !def.isGeo()) {
            return;
        }

        String modelSpec = def.geoModel();
        GeoModelData.GeoModel model = GeoRenderer.resolveModel(modelSpec);
        if (model == null) {
            return;
        }

        String texSpec = def.geoTexture() != null ? def.geoTexture() : def.id();
        ResourceLocation tex = GeoRenderer.resolveTexture(texSpec, modelSpec, "block");
        ResourceLocation glowTex = GeoRenderer.resolveGlowTexture(texSpec, modelSpec, "block");

        String animSpec = def.geoAnimation() != null ? def.geoAnimation() : modelSpec;
        GeoModelData.AnimationFile animFile = GeoRenderer.resolveAnimations(animSpec);

        Level level = be.getLevel();
        long gameTime = level != null ? level.getGameTime() : (System.currentTimeMillis() / 50L);
        float lifeSec = (gameTime + partialTick) / 20.0f;

        if (be.animSerial() != be.clientLastSeenSerial) {
            be.clientLastSeenSerial = be.animSerial();
            be.clientStartTick = gameTime;
        }

        GeoModelData.AnimationClip clip = null;
        float animTimeSec = lifeSec;
        if (animFile != null) {
            if (!be.activeAnim().isEmpty()) {
                GeoModelData.AnimationClip cand = animFile.find(be.activeAnim());
                if (cand != null) {
                    long start = be.clientStartTick >= 0 ? be.clientStartTick : gameTime;
                    float elapsed = Math.max(0f, (gameTime - start + partialTick) / 20.0f) * be.speed();
                    int loop = be.loopMode();
                    boolean finished = loop == 0 && cand.lengthSec() > 0.001f && elapsed >= cand.lengthSec();
                    if (!finished) {
                        clip = loop == 1 && cand.loopMode() != GeoModelData.LoopMode.LOOP
                                ? new GeoModelData.AnimationClip(cand.name(), GeoModelData.LoopMode.LOOP,
                                cand.lengthSec(), cand.boneAnims(), cand.particleEffects(), cand.soundEffects())
                                : loop == 2 && cand.loopMode() != GeoModelData.LoopMode.HOLD_LAST
                                ? new GeoModelData.AnimationClip(cand.name(), GeoModelData.LoopMode.HOLD_LAST,
                                cand.lengthSec(), cand.boneAnims(), cand.particleEffects(), cand.soundEffects())
                                : cand;
                        animTimeSec = elapsed;
                    }
                }
            }
            if (clip == null && def.idleAnimation() != null && !def.idleAnimation().isBlank()) {
                clip = animFile.find(def.idleAnimation());
                animTimeSec = lifeSec;
            }
        }

        GeoModelData.MolangVars vars = new GeoModelData.MolangVars(animTimeSec, lifeSec, 0f, 0f, 0f);
        Direction facing = state.hasProperty(ScriptBlock.FACING) ? state.getValue(ScriptBlock.FACING) : Direction.NORTH;
        float yDeg = switch (facing) {
            case SOUTH -> 180f;
            case WEST -> 90f;
            case EAST -> -90f;
            default -> 0f;
        };
        float scale = def.geoScale();

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        if (yDeg != 0f) {
            poseStack.mulPose(Axis.YP.rotationDegrees(yDeg));
        }
        if (facing == Direction.UP) {
            poseStack.mulPose(Axis.XP.rotationDegrees(90f));
        } else if (facing == Direction.DOWN) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
        }
        if (scale != 1.0f) {
            poseStack.scale(scale, scale, scale);
        }

        GeoRenderer.renderModel(model, clip, animTimeSec, vars, poseStack, bufferSource,
                tex, glowTex, packedLight, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f, null);
        poseStack.popPose();

        if (clip != null && (!clip.particleEffects().isEmpty() || !clip.soundEffects().isEmpty())) {
            PoseStack calc = new PoseStack();
            calc.translate(0.5D, 0.0D, 0.5D);
            if (yDeg != 0f) calc.mulPose(Axis.YP.rotationDegrees(yDeg));
            if (scale != 1.0f) calc.scale(scale, scale, scale);
            Map<String, Vector4f> offsets = new HashMap<>();
            ScriptGeoEntityRenderer.collectWorldBoneOffsets(model, clip, animTimeSec, vars, calc, offsets);
            int key = be.getBlockPos().hashCode() ^ 0x5A3C91;
            GeoParticleHelper.emitClipKeyframes(key,
                    be.getBlockPos().getX(), be.getBlockPos().getY(), be.getBlockPos().getZ(),
                    clip, clip.effectiveTime(animTimeSec), offsets);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(ScriptBlockEntity blockEntity) {
        return true;
    }
}
