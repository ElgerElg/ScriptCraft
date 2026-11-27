package com.scriptcraft.client.geo;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.scriptcraft.ModPaths;
import com.scriptcraft.ScriptCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Высокопроизводительный рендерер и кэш 3D-моделей Blockbench / GeckoLib (.geo.json + .animation.json):
 * - мгновенно подхватывает файлы из {@code scriptcraft/cache/assets/} (без перезапуска клиента!) и из ResourceManager;
 * - автоматически проверяет и рисует светящийся слой {@code *_glowmask.png} (RenderType.eyes при 0xF000F0);
 * - вычисляет мировые/локальные координаты костей и locators для спавна костных партиклов.
 */
@OnlyIn(Dist.CLIENT)
public final class GeoRenderer {

    private static final Map<String, Optional<GeoModelData.GeoModel>> MODEL_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Optional<GeoModelData.AnimationFile>> ANIM_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ResourceLocation> DYN_TEX_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Optional<ResourceLocation>> GLOW_TEX_CACHE = new ConcurrentHashMap<>();
    private static int dynTexSeq = 0;

    private GeoRenderer() {
    }

    public static void invalidateCaches() {
        MODEL_CACHE.clear();
        ANIM_CACHE.clear();
        DYN_TEX_CACHE.clear();
        GLOW_TEX_CACHE.clear();
    }

    // ==================== ЗАГРУЗКА И РАЗРЕШЕНИЕ ПУТЕЙ ====================

    @Nullable
    public static GeoModelData.GeoModel resolveModel(@Nullable String rawSpec) {
        if (rawSpec == null || rawSpec.isBlank()) return null;
        String key = rawSpec.strip().toLowerCase(java.util.Locale.ROOT);
        Optional<GeoModelData.GeoModel> cached = MODEL_CACHE.get(key);
        if (cached != null) return cached.orElse(null);

        String[] nsPath = splitSpec(key);
        String ns = nsPath[0];
        String p = stripSuffix(nsPath[1], ".geo.json", ".json");
        if (p.startsWith("geo/")) p = p.substring(4);

        String[] candidates = {
                "geo/" + p + ".geo.json",
                "geo/entity/" + p + ".geo.json",
                "geo/item/" + p + ".geo.json",
                "geo/block/" + p + ".geo.json",
                "geo/armor/" + p + ".geo.json",
                "geo/" + p + ".json",
                p + ".geo.json",
                p + ".json"
        };

        String text = readAssetText(ns, candidates);
        GeoModelData.GeoModel parsed = null;
        if (text != null) {
            try {
                parsed = GeoModelData.parseGeoJson(text);
            } catch (Exception e) {
                ScriptCraft.LOGGER.warn("[ScriptCraft] Ошибка разбора .geo.json '{}': {}", rawSpec, e.getMessage());
            }
        }
        MODEL_CACHE.put(key, Optional.ofNullable(parsed));
        return parsed;
    }

    @Nullable
    public static GeoModelData.AnimationFile resolveAnimations(@Nullable String rawSpec) {
        if (rawSpec == null || rawSpec.isBlank()) return null;
        String key = rawSpec.strip().toLowerCase(java.util.Locale.ROOT);
        Optional<GeoModelData.AnimationFile> cached = ANIM_CACHE.get(key);
        if (cached != null) return cached.orElse(null);

        String[] nsPath = splitSpec(key);
        String ns = nsPath[0];
        String p = stripSuffix(nsPath[1], ".animation.json", ".json");
        if (p.startsWith("animations/")) p = p.substring("animations/".length());

        String[] candidates = {
                "animations/" + p + ".animation.json",
                "animations/entity/" + p + ".animation.json",
                "animations/item/" + p + ".animation.json",
                "animations/block/" + p + ".animation.json",
                "animations/armor/" + p + ".animation.json",
                "animations/" + p + ".json",
                p + ".animation.json"
        };

        String text = readAssetText(ns, candidates);
        GeoModelData.AnimationFile parsed = null;
        if (text != null) {
            try {
                parsed = GeoModelData.parseAnimationJson(text);
            } catch (Exception e) {
                ScriptCraft.LOGGER.warn("[ScriptCraft] Ошибка разбора .animation.json '{}': {}", rawSpec, e.getMessage());
            }
        }
        ANIM_CACHE.put(key, Optional.ofNullable(parsed));
        return parsed;
    }

    @SuppressWarnings("removal")
    public static ResourceLocation resolveTexture(@Nullable String rawSpec, String fallbackModelSpec, String defaultFolder) {
        String spec = (rawSpec == null || rawSpec.isBlank()) ? fallbackModelSpec : rawSpec;
        if (spec == null || spec.isBlank()) {
            return new ResourceLocation("minecraft", "textures/misc/unknown_server.png");
        }
        String key = spec.strip().toLowerCase(java.util.Locale.ROOT) + "|" + defaultFolder;
        ResourceLocation cached = DYN_TEX_CACHE.get(key);
        if (cached != null) return cached;

        String[] nsPath = splitSpec(spec.strip().toLowerCase(java.util.Locale.ROOT));
        String ns = nsPath[0];
        String p = stripSuffix(nsPath[1], ".geo.json", ".png");
        if (p.startsWith("textures/")) p = p.substring("textures/".length());
        if (p.startsWith("geo/")) p = p.substring("geo/".length());

        String[] candidates = {
                "textures/" + p + ".png",
                "textures/" + defaultFolder + "/" + p + ".png",
                "textures/entity/" + p + ".png",
                "textures/item/" + p + ".png",
                "textures/block/" + p + ".png",
                "textures/armor/" + p + ".png",
                p + ".png"
        };

        ResourceLocation loaded = findOrLoadTexture(ns, candidates);
        if (loaded == null) {
            loaded = new ResourceLocation(ns, "textures/" + defaultFolder + "/" + p + ".png");
        }
        DYN_TEX_CACHE.put(key, loaded);
        return loaded;
    }

    /** Автоматический поиск светящейся маски {@code *_glowmask.png} рядом с основной текстурой. */
    @Nullable
    public static ResourceLocation resolveGlowTexture(@Nullable String rawSpec, String fallbackModelSpec, String defaultFolder) {
        String spec = (rawSpec == null || rawSpec.isBlank()) ? fallbackModelSpec : rawSpec;
        if (spec == null || spec.isBlank()) return null;
        String key = spec.strip().toLowerCase(java.util.Locale.ROOT) + "|" + defaultFolder;
        Optional<ResourceLocation> cached = GLOW_TEX_CACHE.get(key);
        if (cached != null) return cached.orElse(null);

        String[] nsPath = splitSpec(spec.strip().toLowerCase(java.util.Locale.ROOT));
        String ns = nsPath[0];
        String p = stripSuffix(nsPath[1], ".geo.json", ".png");
        if (p.startsWith("textures/")) p = p.substring("textures/".length());
        if (p.startsWith("geo/")) p = p.substring("geo/".length());

        String[] candidates = {
                "textures/" + p + "_glowmask.png",
                "textures/" + defaultFolder + "/" + p + "_glowmask.png",
                "textures/entity/" + p + "_glowmask.png",
                "textures/item/" + p + "_glowmask.png",
                "textures/block/" + p + "_glowmask.png",
                "textures/armor/" + p + "_glowmask.png",
                p + "_glowmask.png"
        };

        ResourceLocation glow = findOrLoadTexture(ns, candidates);
        GLOW_TEX_CACHE.put(key, Optional.ofNullable(glow));
        return glow;
    }

    @Nullable
    @SuppressWarnings("removal")
    private static ResourceLocation findOrLoadTexture(String ns, String[] candidates) {
        Minecraft mc = Minecraft.getInstance();
        for (Path base : new Path[]{ModPaths.cacheAssets(), ModPaths.resources().resolve("assets")}) {
            if (!Files.isDirectory(base)) continue;
            for (String cand : candidates) {
                Path file = base.resolve(ns).resolve(cand);
                if (Files.isRegularFile(file)) {
                    try {
                        byte[] bytes = Files.readAllBytes(file);
                        NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes));
                        ResourceLocation dynId = new ResourceLocation(ScriptCraft.MODID, "geo_tex/" + (++dynTexSeq));
                        mc.getTextureManager().register(dynId, new DynamicTexture(img));
                        return dynId;
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        if (mc.getResourceManager() != null) {
            for (String cand : candidates) {
                ResourceLocation rl = ResourceLocation.tryBuild(ns, cand);
                if (rl != null && mc.getResourceManager().getResource(rl).isPresent()) {
                    return rl;
                }
            }
        }
        return null;
    }

    @Nullable
    private static String readAssetText(String ns, String[] candidates) {
        for (Path base : new Path[]{ModPaths.cacheAssets(), ModPaths.resources().resolve("assets")}) {
            if (!Files.isDirectory(base)) continue;
            for (String cand : candidates) {
                Path f = base.resolve(ns).resolve(cand);
                if (Files.isRegularFile(f)) {
                    try {
                        return Files.readString(f, StandardCharsets.UTF_8);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getResourceManager() != null) {
            for (String cand : candidates) {
                ResourceLocation rl = ResourceLocation.tryBuild(ns, cand);
                if (rl == null) continue;
                Optional<Resource> res = mc.getResourceManager().getResource(rl);
                if (res.isPresent()) {
                    try (InputStream in = res.get().open()) {
                        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return null;
    }

    public static String[] splitSpec(String spec) {
        int colon = spec.indexOf(':');
        if (colon >= 0) {
            return new String[]{spec.substring(0, colon), spec.substring(colon + 1)};
        }
        return new String[]{ScriptCraft.MODID, spec};
    }

    private static String stripSuffix(String s, String... suffixes) {
        for (String suf : suffixes) {
            if (s.endsWith(suf)) {
                return s.substring(0, s.length() - suf.length());
            }
        }
        return s;
    }

    // ==================== РЕНДЕР ИЕРАРХИИ КОСТЕЙ И КУБОВ ====================

    public static void renderModel(
            GeoModelData.GeoModel model,
            @Nullable GeoModelData.AnimationClip clip,
            float animTimeSec,
            GeoModelData.MolangVars vars,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            ResourceLocation texture,
            @Nullable ResourceLocation glowTexture,
            int packedLight,
            int packedOverlay,
            float r, float g, float b, float a,
            @Nullable Map<String, Vector4f> outBonePositions
    ) {
        float effectiveT = clip != null ? clip.effectiveTime(animTimeSec) : animTimeSec;
        vars.animTime = effectiveT;

        VertexConsumer baseVc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture));
        for (GeoModelData.Bone root : model.rootBones) {
            renderBoneRecursive(root, clip, effectiveT, vars, poseStack, baseVc, packedLight, packedOverlay,
                    r, g, b, a, outBonePositions);
        }

        if (glowTexture != null) {
            VertexConsumer glowVc = bufferSource.getBuffer(RenderType.eyes(glowTexture));
            for (GeoModelData.Bone root : model.rootBones) {
                renderBoneRecursive(root, clip, effectiveT, vars, poseStack, glowVc,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, a, null);
            }
        }
    }

    /** Рендерит только одну конкретную кость (и её дочерние кости) с сброшенным пивотом — идеально для 3D-брони! */
    public static void renderSingleArmorBone(
            GeoModelData.GeoModel model,
            String boneName,
            float anchorPivotX, float anchorPivotY, float anchorPivotZ,
            @Nullable GeoModelData.AnimationClip clip,
            float animTimeSec,
            GeoModelData.MolangVars vars,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            ResourceLocation texture,
            @Nullable ResourceLocation glowTexture,
            int packedLight,
            int packedOverlay
    ) {
        GeoModelData.Bone target = findBoneIgnoreCase(model, boneName);
        if (target == null) return;
        float effectiveT = clip != null ? clip.effectiveTime(animTimeSec) : animTimeSec;
        vars.animTime = effectiveT;

        poseStack.pushPose();
        // Переводим из координат ModelPart игрока (X/Y отражены LivingEntityRenderer'ом) в правое пространство GeoRenderer
        poseStack.scale(-1f, -1f, 1f);
        poseStack.translate(anchorPivotX / 16f, -anchorPivotY / 16f, -anchorPivotZ / 16f);

        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture));
        renderBoneRecursive(target, clip, effectiveT, vars, poseStack, vc, packedLight, packedOverlay,
                1f, 1f, 1f, 1f, null);
        if (glowTexture != null) {
            VertexConsumer glowVc = bufferSource.getBuffer(RenderType.eyes(glowTexture));
            renderBoneRecursive(target, clip, effectiveT, vars, poseStack, glowVc,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f, null);
        }
        poseStack.popPose();
    }

    @Nullable
    public static GeoModelData.Bone findBoneIgnoreCase(GeoModelData.GeoModel model, String... aliases) {
        for (String alias : aliases) {
            GeoModelData.Bone exact = model.bonesByName.get(alias);
            if (exact != null) return exact;
            for (var entry : model.bonesByName.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(alias)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static void renderBoneRecursive(
            GeoModelData.Bone bone,
            @Nullable GeoModelData.AnimationClip clip,
            float tSec,
            GeoModelData.MolangVars vars,
            PoseStack pose,
            VertexConsumer vc,
            int light,
            int overlay,
            float r, float g, float b, float a,
            @Nullable Map<String, Vector4f> outPositions
    ) {
        pose.pushPose();

        float animRx = 0f, animRy = 0f, animRz = 0f;
        float animPx = 0f, animPy = 0f, animPz = 0f;
        float animSx = 1f, animSy = 1f, animSz = 1f;

        if (clip != null) {
            GeoModelData.BoneAnim ba = clip.boneAnims().get(bone.name);
            if (ba != null) {
                if (ba.rotation() != null) {
                    float[] rv = ba.rotation().sample(tSec, vars, 0f);
                    animRx = rv[0];
                    animRy = rv[1];
                    animRz = rv[2];
                }
                if (ba.position() != null) {
                    float[] pv = ba.position().sample(tSec, vars, 0f);
                    animPx = pv[0];
                    animPy = pv[1];
                    animPz = pv[2];
                }
                if (ba.scale() != null) {
                    float[] sv = ba.scale().sample(tSec, vars, 1f);
                    animSx = sv[0];
                    animSy = sv[1];
                    animSz = sv[2];
                }
            }
        }

        // В Bedrock смещение кости: X инвертирован относительно правой системы координат MC, Y вверх, Z вперёд
        pose.translate(-animPx / 16f, animPy / 16f, animPz / 16f);

        float px = -bone.pivot[0] / 16f;
        float py = bone.pivot[1] / 16f;
        float pz = bone.pivot[2] / 16f;

        pose.translate(px, py, pz);

        // Порядок вращения Bedrock Z -> Y -> X (с учётом инверсии осей Bedrock -> OpenGL)
        float totRx = -(bone.bindRotDeg[0] + animRx);
        float totRy = -(bone.bindRotDeg[1] + animRy);
        float totRz = (bone.bindRotDeg[2] + animRz);
        if (totRz != 0f) pose.mulPose(Axis.ZP.rotationDegrees(totRz));
        if (totRy != 0f) pose.mulPose(Axis.YP.rotationDegrees(totRy));
        if (totRx != 0f) pose.mulPose(Axis.XP.rotationDegrees(totRx));

        if (animSx != 1f || animSy != 1f || animSz != 1f) {
            pose.scale(animSx, animSy, animSz);
        }

        if (outPositions != null) {
            Matrix4f mat = pose.last().pose();
            outPositions.put(bone.name.toLowerCase(java.util.Locale.ROOT), mat.transform(new Vector4f(0f, 0f, 0f, 1f)));
        }

        pose.translate(-px, -py, -pz);

        if (outPositions != null && !bone.locators.isEmpty()) {
            Matrix4f mat = pose.last().pose();
            for (var le : bone.locators.entrySet()) {
                float[] lp = le.getValue();
                outPositions.put(le.getKey().toLowerCase(java.util.Locale.ROOT),
                        mat.transform(new Vector4f(-lp[0] / 16f, lp[1] / 16f, lp[2] / 16f, 1f)));
            }
        }

        for (GeoModelData.Cube cube : bone.cubes) {
            emitCube(pose, vc, cube, light, overlay, r, g, b, a);
        }

        for (GeoModelData.Bone child : bone.children) {
            renderBoneRecursive(child, clip, tSec, vars, pose, vc, light, overlay, r, g, b, a, outPositions);
        }

        pose.popPose();
    }

    private static void emitCube(PoseStack pose, VertexConsumer vc, GeoModelData.Cube cube,
                                 int light, int overlay, float r, float g, float b, float a) {
        boolean hasCubeRot = cube.rotationDeg()[0] != 0f || cube.rotationDeg()[1] != 0f || cube.rotationDeg()[2] != 0f;
        if (hasCubeRot) {
            pose.pushPose();
            float cpx = -cube.pivot()[0] / 16f;
            float cpy = cube.pivot()[1] / 16f;
            float cpz = cube.pivot()[2] / 16f;
            pose.translate(cpx, cpy, cpz);
            if (cube.rotationDeg()[2] != 0f) pose.mulPose(Axis.ZP.rotationDegrees(cube.rotationDeg()[2]));
            if (cube.rotationDeg()[1] != 0f) pose.mulPose(Axis.YP.rotationDegrees(-cube.rotationDeg()[1]));
            if (cube.rotationDeg()[0] != 0f) pose.mulPose(Axis.XP.rotationDegrees(-cube.rotationDeg()[0]));
            pose.translate(-cpx, -cpy, -cpz);
        }

        float inf = cube.inflate();
        // В Bedrock origin — левый-нижний-передний угол в системе, где +X влево модели, поэтому X инвертируется:
        float x1 = -(cube.origin()[0] + cube.size()[0] + inf) / 16f;
        float x2 = -(cube.origin()[0] - inf) / 16f;
        float y1 = (cube.origin()[1] - inf) / 16f;
        float y2 = (cube.origin()[1] + cube.size()[1] + inf) / 16f;
        float z1 = (cube.origin()[2] - inf) / 16f;
        float z2 = (cube.origin()[2] + cube.size()[2] + inf) / 16f;

        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        Matrix3f n = last.normal();

        // 0: North (-Z)
        quad(vc, m, n, cube.faces()[0],
                x2, y2, z1, x1, y2, z1, x1, y1, z1, x2, y1, z1,
                0f, 0f, -1f, light, overlay, r, g, b, a);
        // 1: South (+Z)
        quad(vc, m, n, cube.faces()[1],
                x1, y2, z2, x2, y2, z2, x2, y1, z2, x1, y1, z2,
                0f, 0f, 1f, light, overlay, r, g, b, a);
        // 2: East (+X в системе после инверсии -> x2)
        quad(vc, m, n, cube.faces()[2],
                x2, y2, z2, x2, y2, z1, x2, y1, z1, x2, y1, z2,
                1f, 0f, 0f, light, overlay, r, g, b, a);
        // 3: West (-X -> x1)
        quad(vc, m, n, cube.faces()[3],
                x1, y2, z1, x1, y2, z2, x1, y1, z2, x1, y1, z1,
                -1f, 0f, 0f, light, overlay, r, g, b, a);
        // 4: Up (+Y -> y2)
        quad(vc, m, n, cube.faces()[4],
                x2, y2, z2, x1, y2, z2, x1, y2, z1, x2, y2, z1,
                0f, 1f, 0f, light, overlay, r, g, b, a);
        // 5: Down (-Y -> y1)
        quad(vc, m, n, cube.faces()[5],
                x2, y1, z1, x1, y1, z1, x1, y1, z2, x2, y1, z2,
                0f, -1f, 0f, light, overlay, r, g, b, a);

        if (hasCubeRot) {
            pose.popPose();
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, @Nullable GeoModelData.FaceUv uv,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float nx, float ny, float nz,
                             int light, int overlay, float r, float g, float b, float a) {
        if (uv == null) return;
        vert(vc, m, n, x0, y0, z0, uv.u1(), uv.v1(), nx, ny, nz, light, overlay, r, g, b, a);
        vert(vc, m, n, x1, y1, z1, uv.u2(), uv.v1(), nx, ny, nz, light, overlay, r, g, b, a);
        vert(vc, m, n, x2, y2, z2, uv.u2(), uv.v2(), nx, ny, nz, light, overlay, r, g, b, a);
        vert(vc, m, n, x3, y3, z3, uv.u1(), uv.v2(), nx, ny, nz, light, overlay, r, g, b, a);
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Matrix3f n,
                             float x, float y, float z, float u, float v,
                             float nx, float ny, float nz,
                             int light, int overlay, float r, float g, float b, float a) {
        vc.vertex(m, x, y, z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(n, nx, ny, nz)
                .endVertex();
    }
}
