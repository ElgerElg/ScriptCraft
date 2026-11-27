package com.scriptcraft.client.geo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.scriptcraft.entity.ScriptGeoEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;

/**
 * 3D-рендерер универсальной Geo-сущности ({@link ScriptGeoEntity}):
 * мобы, боссы, турели и декор с анимациями .animation.json, Molang, свечением _glowmask.png и костными партиклами.
 */
@OnlyIn(Dist.CLIENT)
public class ScriptGeoEntityRenderer extends EntityRenderer<ScriptGeoEntity> {

    public ScriptGeoEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.5f;
    }

    @Override
    public void render(ScriptGeoEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        String modelSpec = entity.getModelPath();
        GeoModelData.GeoModel model = GeoRenderer.resolveModel(modelSpec);
        if (model == null) {
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }

        float scale = entity.getGeoScale();
        this.shadowRadius = Math.max(0.15f, entity.getBbWidth() * 0.45f);

        ResourceLocation tex = getTextureLocation(entity);
        ResourceLocation glowTex = entity.isGlowEnabled()
                ? GeoRenderer.resolveGlowTexture(entity.getTexturePath(), modelSpec, "entity")
                : null;

        GeoModelData.AnimationFile animFile = GeoRenderer.resolveAnimations(
                entity.getAnimFilePath().isEmpty() ? modelSpec : entity.getAnimFilePath());

        float lifeSec = (entity.tickCount + partialTick) / 20.0f;
        float walkSpeed = entity.walkAnimation.speed(partialTick);
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot) - bodyYaw;
        float headPitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        GeoModelData.AnimationClip clip = null;
        float animTimeSec = lifeSec;

        if (animFile != null) {
            if (entity.isDeadOrDying() && !entity.getDeathAnim().isEmpty()) {
                clip = animFile.find(entity.getDeathAnim());
                animTimeSec = (entity.deathTime + partialTick) / 20.0f;
            }
            if (clip == null && !entity.getActiveAnim().isEmpty()) {
                GeoModelData.AnimationClip candidate = animFile.find(entity.getActiveAnim());
                if (candidate != null) {
                    float elapsed = Math.max(0f, (entity.tickCount - entity.clientAnimStartTick + partialTick) / 20.0f)
                            * entity.getActiveSpeed();
                    int loopOverride = entity.getActiveLoop();
                    boolean finished = loopOverride == 0 && candidate.lengthSec() > 0.001f && elapsed >= candidate.lengthSec();
                    if (!finished) {
                        clip = loopOverride == 1 && candidate.loopMode() != GeoModelData.LoopMode.LOOP
                                ? new GeoModelData.AnimationClip(candidate.name(), GeoModelData.LoopMode.LOOP,
                                candidate.lengthSec(), candidate.boneAnims(), candidate.particleEffects(), candidate.soundEffects())
                                : loopOverride == 2 && candidate.loopMode() != GeoModelData.LoopMode.HOLD_LAST
                                ? new GeoModelData.AnimationClip(candidate.name(), GeoModelData.LoopMode.HOLD_LAST,
                                candidate.lengthSec(), candidate.boneAnims(), candidate.particleEffects(), candidate.soundEffects())
                                : candidate;
                        animTimeSec = elapsed;
                    }
                }
            }
            if (clip == null && walkSpeed > 0.05f && !entity.getWalkAnim().isEmpty()) {
                clip = animFile.find(entity.getWalkAnim());
                animTimeSec = lifeSec;
            }
            if (clip == null && !entity.getIdleAnim().isEmpty()) {
                clip = animFile.find(entity.getIdleAnim());
                animTimeSec = lifeSec;
            }
        }

        GeoModelData.MolangVars vars = new GeoModelData.MolangVars(animTimeSec, lifeSec, walkSpeed, headPitch, headYaw);
        vars.isOnGround = entity.onGround();

        boolean hurt = entity.hurtTime > 0 || entity.deathTime > 0;
        int overlay = OverlayTexture.pack(OverlayTexture.u(0f), OverlayTexture.v(hurt));

        Map<String, String> pBones = ScriptGeoEntity.decodeParticleBones(entity.getParticleBonesRaw());
        boolean needBoneCoords = !pBones.isEmpty()
                || (clip != null && (!clip.particleEffects().isEmpty() || !clip.soundEffects().isEmpty()));
        Map<String, Vector4f> bonePositions = needBoneCoords ? new HashMap<>() : null;

        poseStack.pushPose();
        // Базовый поворот сущности в мире: фронт модели (-Z в GeoRenderer) смотрит по направлению bodyYaw
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        if (entity.deathTime > 0 && (clip == null || !clip.name().toLowerCase().contains("death"))) {
            float fall = ((float) entity.deathTime + partialTick - 1.0F) / 20.0F * 1.6F;
            fall = Math.min(1.0F, Mth.sqrt(Math.max(0f, fall)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(fall * 90.0F));
        }
        if (scale != 1.0f) {
            poseStack.scale(scale, scale, scale);
        }

        // Чтобы матрица внутри bonePositions была относительно (0,0,0) сущности в мировых осях, снимаем копию при пустом стеке
        PoseStack calcPose = null;
        if (needBoneCoords) {
            calcPose = new PoseStack();
            calcPose.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
            if (scale != 1.0f) calcPose.scale(scale, scale, scale);
        }

        GeoRenderer.renderModel(model, clip, animTimeSec, vars, poseStack, buffer,
                tex, glowTex, packedLight, overlay, 1f, 1f, 1f, 1f, null);
        poseStack.popPose();

        if (needBoneCoords && calcPose != null) {
            collectWorldBoneOffsets(model, clip, animTimeSec, vars, calcPose, bonePositions);
            double ex = Mth.lerp(partialTick, entity.xo, entity.getX());
            double ey = Mth.lerp(partialTick, entity.yo, entity.getY());
            double ez = Mth.lerp(partialTick, entity.zo, entity.getZ());
            int key = entity.getId() * 31 + 1;
            GeoParticleHelper.emitContinuousBoneParticles(key, ex, ey, ez, pBones, bonePositions);
            if (clip != null) {
                GeoParticleHelper.emitClipKeyframes(key, ex, ey, ez, clip, clip.effectiveTime(animTimeSec), bonePositions);
            }
        }

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    static void collectWorldBoneOffsets(GeoModelData.GeoModel model, GeoModelData.AnimationClip clip,
                                        float animTimeSec, GeoModelData.MolangVars vars,
                                        PoseStack pose, Map<String, Vector4f> out) {
        float tSec = clip != null ? clip.effectiveTime(animTimeSec) : animTimeSec;
        vars.animTime = tSec;
        for (GeoModelData.Bone root : model.rootBones) {
            collectBoneOffsetsRec(root, clip, tSec, vars, pose, out);
        }
    }

    private static void collectBoneOffsetsRec(GeoModelData.Bone bone, GeoModelData.AnimationClip clip,
                                              float tSec, GeoModelData.MolangVars vars,
                                              PoseStack pose, Map<String, Vector4f> out) {
        pose.pushPose();
        float animRx = 0f, animRy = 0f, animRz = 0f;
        float animPx = 0f, animPy = 0f, animPz = 0f;
        float animSx = 1f, animSy = 1f, animSz = 1f;
        if (clip != null) {
            GeoModelData.BoneAnim ba = clip.boneAnims().get(bone.name);
            if (ba != null) {
                if (ba.rotation() != null) { float[] v = ba.rotation().sample(tSec, vars, 0f); animRx = v[0]; animRy = v[1]; animRz = v[2]; }
                if (ba.position() != null) { float[] v = ba.position().sample(tSec, vars, 0f); animPx = v[0]; animPy = v[1]; animPz = v[2]; }
                if (ba.scale() != null) { float[] v = ba.scale().sample(tSec, vars, 1f); animSx = v[0]; animSy = v[1]; animSz = v[2]; }
            }
        }
        pose.translate(-animPx / 16f, animPy / 16f, animPz / 16f);
        float px = -bone.pivot[0] / 16f, py = bone.pivot[1] / 16f, pz = bone.pivot[2] / 16f;
        pose.translate(px, py, pz);
        float totRx = -(bone.bindRotDeg[0] + animRx), totRy = -(bone.bindRotDeg[1] + animRy), totRz = (bone.bindRotDeg[2] + animRz);
        if (totRz != 0f) pose.mulPose(Axis.ZP.rotationDegrees(totRz));
        if (totRy != 0f) pose.mulPose(Axis.YP.rotationDegrees(totRy));
        if (totRx != 0f) pose.mulPose(Axis.XP.rotationDegrees(totRx));
        if (animSx != 1f || animSy != 1f || animSz != 1f) pose.scale(animSx, animSy, animSz);

        out.put(bone.name.toLowerCase(java.util.Locale.ROOT), pose.last().pose().transform(new Vector4f(0f, 0f, 0f, 1f)));
        pose.translate(-px, -py, -pz);
        for (var le : bone.locators.entrySet()) {
            float[] lp = le.getValue();
            out.put(le.getKey().toLowerCase(java.util.Locale.ROOT),
                    pose.last().pose().transform(new Vector4f(-lp[0] / 16f, lp[1] / 16f, lp[2] / 16f, 1f)));
        }
        for (GeoModelData.Bone child : bone.children) {
            collectBoneOffsetsRec(child, clip, tSec, vars, pose, out);
        }
        pose.popPose();
    }

    @Override
    protected boolean shouldShowName(ScriptGeoEntity entity) {
        return entity.hasCustomName() && (entity.isCustomNameVisible() || entity == this.entityRenderDispatcher.crosshairPickEntity);
    }

    @Override
    public ResourceLocation getTextureLocation(ScriptGeoEntity entity) {
        return GeoRenderer.resolveTexture(entity.getTexturePath(), entity.getModelPath(), "entity");
    }
}
