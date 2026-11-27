package com.scriptcraft.client.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Полноценный парсер и математическое ядро моделей Blockbench / GeckoLib:
 * - Bedrock Geometry (.geo.json версий 1.12.0 / 1.16.0 / 1.21.0): иерархия костей, пивоты, вращения кубов и костей,
 *   inflate, mirror, классический Box-UV и погранный Per-Face UV, а также locators для спавна партиклов;
 * - Bedrock Animations (.animation.json версии 1.8.0): loop true/false/"hold_on_last_frame",
 *   ключевые кадры rotation/position/scale (linear, step, catmullrom, pre/post), встроенный вычислитель Molang
 *   (math.sin/cos/abs/clamp/lerp + query.anim_time/life_time/ground_speed/head_x_rotation/head_y_rotation),
 *   а также треки particle_effects и sound_effects!
 */
@OnlyIn(Dist.CLIENT)
public final class GeoModelData {

    public record FaceUv(float u1, float v1, float u2, float v2) {
    }

    public record Cube(
            float[] origin,
            float[] size,
            float[] pivot,
            float[] rotationDeg,
            float inflate,
            boolean mirror,
            /** 6 граней в порядке: north(0), south(1), east(2), west(3), up(4), down(5). */
            FaceUv[] faces
    ) {
    }

    public static final class Bone {
        public final String name;
        @Nullable
        public final String parentName;
        public final float[] pivot;       // [x, y, z] в пикселях Bedrock (16 px = 1 блок)
        public final float[] bindRotDeg;  // [rx, ry, rz] в градусах
        public final List<Cube> cubes;
        public final Map<String, float[]> locators;
        public final List<Bone> children = new ArrayList<>();

        public Bone(String name, @Nullable String parentName, float[] pivot, float[] bindRotDeg,
                    List<Cube> cubes, Map<String, float[]> locators) {
            this.name = name;
            this.parentName = parentName;
            this.pivot = pivot;
            this.bindRotDeg = bindRotDeg;
            this.cubes = cubes;
            this.locators = locators;
        }
    }

    public static final class GeoModel {
        public final String identifier;
        public final int texWidth;
        public final int texHeight;
        public final float visibleBoundsWidth;
        public final float visibleBoundsHeight;
        public final Map<String, Bone> bonesByName;
        public final List<Bone> rootBones;
        public final Map<String, float[]> allLocators;

        public GeoModel(String identifier, int texWidth, int texHeight,
                        float visibleBoundsWidth, float visibleBoundsHeight,
                        Map<String, Bone> bonesByName, List<Bone> rootBones,
                        Map<String, float[]> allLocators) {
            this.identifier = identifier;
            this.texWidth = Math.max(1, texWidth);
            this.texHeight = Math.max(1, texHeight);
            this.visibleBoundsWidth = visibleBoundsWidth;
            this.visibleBoundsHeight = visibleBoundsHeight;
            this.bonesByName = Collections.unmodifiableMap(bonesByName);
            this.rootBones = Collections.unmodifiableList(rootBones);
            this.allLocators = Collections.unmodifiableMap(allLocators);
        }
    }

    // ==================== АНИМАЦИИ (.animation.json + Molang) ====================

    public enum LoopMode {
        ONCE, LOOP, HOLD_LAST
    }

    @FunctionalInterface
    public interface ScalarExpr {
        float eval(MolangVars vars);
    }

    public record Vec3Expr(ScalarExpr x, ScalarExpr y, ScalarExpr z) {
        public float[] eval(MolangVars vars) {
            return new float[]{x.eval(vars), y.eval(vars), z.eval(vars)};
        }
    }

    public record Keyframe(float timeSec, Vec3Expr pre, Vec3Expr post, String lerpMode) {
    }

    public record ChannelTrack(List<Keyframe> keyframes) {
        public float[] sample(float tSec, MolangVars vars, float defVal) {
            if (keyframes.isEmpty()) {
                return new float[]{defVal, defVal, defVal};
            }
            if (keyframes.size() == 1 || tSec <= keyframes.get(0).timeSec) {
                return keyframes.get(0).post.eval(vars);
            }
            int lastIdx = keyframes.size() - 1;
            if (tSec >= keyframes.get(lastIdx).timeSec) {
                return keyframes.get(lastIdx).pre.eval(vars);
            }
            int idx = 0;
            while (idx + 1 < keyframes.size() && keyframes.get(idx + 1).timeSec < tSec) {
                idx++;
            }
            Keyframe k0 = keyframes.get(idx);
            Keyframe k1 = keyframes.get(idx + 1);
            float span = Math.max(1e-5f, k1.timeSec - k0.timeSec);
            float alpha = Mth.clamp((tSec - k0.timeSec) / span, 0f, 1f);
            float[] a = k0.post.eval(vars);
            float[] b = k1.pre.eval(vars);
            if ("step".equalsIgnoreCase(k0.lerpMode)) {
                return a;
            }
            if ("catmullrom".equalsIgnoreCase(k0.lerpMode) || "catmullrom".equalsIgnoreCase(k1.lerpMode)) {
                float[] p0 = idx > 0 ? keyframes.get(idx - 1).post.eval(vars) : a;
                float[] p3 = idx + 2 < keyframes.size() ? keyframes.get(idx + 2).pre.eval(vars) : b;
                return new float[]{
                        catmullRom(alpha, p0[0], a[0], b[0], p3[0]),
                        catmullRom(alpha, p0[1], a[1], b[1], p3[1]),
                        catmullRom(alpha, p0[2], a[2], b[2], p3[2])
                };
            }
            return new float[]{
                    Mth.lerp(alpha, a[0], b[0]),
                    Mth.lerp(alpha, a[1], b[1]),
                    Mth.lerp(alpha, a[2], b[2])
            };
        }

        private static float catmullRom(float t, float p0, float p1, float p2, float p3) {
            float t2 = t * t;
            float t3 = t2 * t;
            return 0.5f * ((2f * p1)
                    + (-p0 + p2) * t
                    + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2
                    + (-p0 + 3f * p1 - 3f * p2 + p3) * t3);
        }
    }

    public record BoneAnim(@Nullable ChannelTrack rotation, @Nullable ChannelTrack position, @Nullable ChannelTrack scale) {
    }

    public record ParticleKeyframe(float timeSec, String effect, String locator) {
    }

    public record SoundKeyframe(float timeSec, String effect) {
    }

    public record AnimationClip(
            String name,
            LoopMode loopMode,
            float lengthSec,
            Map<String, BoneAnim> boneAnims,
            List<ParticleKeyframe> particleEffects,
            List<SoundKeyframe> soundEffects
    ) {
        public float effectiveTime(float elapsedSec) {
            if (lengthSec <= 0.0001f) {
                return elapsedSec;
            }
            return switch (loopMode) {
                case LOOP -> elapsedSec % lengthSec;
                case HOLD_LAST, ONCE -> Math.min(elapsedSec, lengthSec);
            };
        }

        public boolean isFinished(float elapsedSec) {
            return loopMode == LoopMode.ONCE && lengthSec > 0.0001f && elapsedSec >= lengthSec;
        }
    }

    public record AnimationFile(Map<String, AnimationClip> clips) {
        @Nullable
        public AnimationClip find(String query) {
            if (clips.isEmpty()) return null;
            if (query == null || query.isBlank()) {
                return clips.values().iterator().next();
            }
            String q = query.strip();
            AnimationClip exact = clips.get(q);
            if (exact != null) return exact;
            String low = q.toLowerCase(Locale.ROOT);
            for (var e : clips.entrySet()) {
                String k = e.getKey().toLowerCase(Locale.ROOT);
                if (k.equals(low) || k.endsWith("." + low) || k.endsWith("/" + low) || k.endsWith(":" + low)) {
                    return e.getValue();
                }
            }
            for (var e : clips.entrySet()) {
                if (e.getKey().toLowerCase(Locale.ROOT).contains(low)) {
                    return e.getValue();
                }
            }
            return null;
        }
    }

    public static final class MolangVars {
        public float animTime;
        public float lifeTime;
        public float groundSpeed;
        public float headPitch;
        public float headYaw;
        public boolean isOnGround = true;

        public MolangVars(float animTime, float lifeTime, float groundSpeed, float headPitch, float headYaw) {
            this.animTime = animTime;
            this.lifeTime = lifeTime;
            this.groundSpeed = groundSpeed;
            this.headPitch = headPitch;
            this.headYaw = headYaw;
        }
    }

    private GeoModelData() {
    }

    // ==================== ПАРСИНГ .geo.json ====================

    @Nullable
    public static GeoModel parseGeoJson(String jsonText) {
        JsonObject root = JsonParser.parseString(jsonText).getAsJsonObject();
        JsonObject geoObj = null;

        if (root.has("minecraft:geometry")) {
            JsonElement mg = root.get("minecraft:geometry");
            if (mg.isJsonArray() && !mg.getAsJsonArray().isEmpty()) {
                geoObj = mg.getAsJsonArray().get(0).getAsJsonObject();
            } else if (mg.isJsonObject()) {
                geoObj = mg.getAsJsonObject();
            }
        } else {
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (entry.getKey().startsWith("geometry.") && entry.getValue().isJsonObject()) {
                    geoObj = entry.getValue().getAsJsonObject();
                    if (!geoObj.has("description")) {
                        JsonObject desc = new JsonObject();
                        desc.addProperty("identifier", entry.getKey());
                        if (geoObj.has("texturewidth")) desc.add("texture_width", geoObj.get("texturewidth"));
                        if (geoObj.has("textureheight")) desc.add("texture_height", geoObj.get("textureheight"));
                        geoObj.add("description", desc);
                    }
                    break;
                }
            }
        }
        if (geoObj == null) {
            return null;
        }

        String identifier = "geometry.unknown";
        int texW = 64, texH = 64;
        float boundsW = 1.0f, boundsH = 1.8f;
        if (geoObj.has("description") && geoObj.get("description").isJsonObject()) {
            JsonObject desc = geoObj.getAsJsonObject("description");
            if (desc.has("identifier")) identifier = desc.get("identifier").getAsString();
            if (desc.has("texture_width")) texW = Math.max(1, desc.get("texture_width").getAsInt());
            if (desc.has("texture_height")) texH = Math.max(1, desc.get("texture_height").getAsInt());
            if (desc.has("visible_bounds_width")) boundsW = desc.get("visible_bounds_width").getAsFloat();
            if (desc.has("visible_bounds_height")) boundsH = desc.get("visible_bounds_height").getAsFloat();
        } else {
            if (geoObj.has("texturewidth")) texW = Math.max(1, geoObj.get("texturewidth").getAsInt());
            if (geoObj.has("textureheight")) texH = Math.max(1, geoObj.get("textureheight").getAsInt());
        }

        Map<String, Bone> bones = new LinkedHashMap<>();
        Map<String, float[]> allLocators = new LinkedHashMap<>();

        if (geoObj.has("bones") && geoObj.get("bones").isJsonArray()) {
            for (JsonElement be : geoObj.getAsJsonArray("bones")) {
                if (!be.isJsonObject()) continue;
                JsonObject bo = be.getAsJsonObject();
                String name = bo.has("name") ? bo.get("name").getAsString() : "bone_" + bones.size();
                String parent = bo.has("parent") && bo.get("parent").isJsonPrimitive() ? bo.get("parent").getAsString() : null;
                float[] pivot = vec3(bo.get("pivot"), 0f, 0f, 0f);
                float[] rot = vec3(bo.get("rotation"), 0f, 0f, 0f);
                boolean boneMirror = bo.has("mirror") && bo.get("mirror").getAsBoolean();
                float boneInflate = bo.has("inflate") ? bo.get("inflate").getAsFloat() : 0f;

                List<Cube> cubes = new ArrayList<>();
                if (bo.has("cubes") && bo.get("cubes").isJsonArray()) {
                    for (JsonElement ce : bo.getAsJsonArray("cubes")) {
                        if (!ce.isJsonObject()) continue;
                        Cube cube = parseCube(ce.getAsJsonObject(), pivot, boneInflate, boneMirror, texW, texH);
                        if (cube != null) {
                            cubes.add(cube);
                        }
                    }
                }

                Map<String, float[]> locators = new LinkedHashMap<>();
                if (bo.has("locators") && bo.get("locators").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> le : bo.getAsJsonObject("locators").entrySet()) {
                        float[] locPos;
                        if (le.getValue().isJsonArray()) {
                            locPos = vec3(le.getValue(), pivot[0], pivot[1], pivot[2]);
                        } else if (le.getValue().isJsonObject() && le.getValue().getAsJsonObject().has("offset")) {
                            locPos = vec3(le.getValue().getAsJsonObject().get("offset"), pivot[0], pivot[1], pivot[2]);
                        } else {
                            locPos = new float[]{pivot[0], pivot[1], pivot[2]};
                        }
                        locators.put(le.getKey(), locPos);
                        allLocators.put(le.getKey(), locPos);
                    }
                }

                bones.put(name, new Bone(name, parent, pivot, rot, cubes, locators));
            }
        }

        List<Bone> roots = new ArrayList<>();
        for (Bone b : bones.values()) {
            if (b.parentName != null && bones.containsKey(b.parentName) && !b.parentName.equals(b.name)) {
                bones.get(b.parentName).children.add(b);
            } else {
                roots.add(b);
            }
        }
        return new GeoModel(identifier, texW, texH, boundsW, boundsH, bones, roots, allLocators);
    }

    private static Cube parseCube(JsonObject co, float[] bonePivot, float boneInflate, boolean boneMirror, int texW, int texH) {
        float[] origin = vec3(co.get("origin"), 0f, 0f, 0f);
        float[] size = vec3(co.get("size"), 1f, 1f, 1f);
        float[] pivot = co.has("pivot") ? vec3(co.get("pivot"), bonePivot[0], bonePivot[1], bonePivot[2])
                : new float[]{bonePivot[0], bonePivot[1], bonePivot[2]};
        float[] rot = vec3(co.get("rotation"), 0f, 0f, 0f);
        float inflate = co.has("inflate") ? co.get("inflate").getAsFloat() : boneInflate;
        boolean mirror = co.has("mirror") ? co.get("mirror").getAsBoolean() : boneMirror;

        FaceUv[] faces = new FaceUv[6];
        JsonElement uvEl = co.get("uv");
        if (uvEl != null && uvEl.isJsonObject()) {
            JsonObject uvo = uvEl.getAsJsonObject();
            faces[0] = parsePerFace(uvo.get("north"), texW, texH);
            faces[1] = parsePerFace(uvo.get("south"), texW, texH);
            faces[2] = parsePerFace(uvo.get("east"), texW, texH);
            faces[3] = parsePerFace(uvo.get("west"), texW, texH);
            faces[4] = parsePerFace(uvo.get("up"), texW, texH);
            faces[5] = parsePerFace(uvo.get("down"), texW, texH);
        } else {
            float u = 0f, v = 0f;
            if (uvEl != null && uvEl.isJsonArray() && uvEl.getAsJsonArray().size() >= 2) {
                u = uvEl.getAsJsonArray().get(0).getAsFloat();
                v = uvEl.getAsJsonArray().get(1).getAsFloat();
            }
            float dx = (float) Math.floor(size[0]);
            float dy = (float) Math.floor(size[1]);
            float dz = (float) Math.floor(size[2]);
            float tw = (float) texW;
            float th = (float) texH;
            // Стандартная развёртка Bedrock Box-UV:
            // north (-Z), south (+Z), east (+X), west (-X), up (+Y), down (-Y)
            faces[0] = boxUv(u + dz, v + dz, dx, dy, tw, th, mirror);                // north
            faces[1] = boxUv(u + dz + dx + dz, v + dz, dx, dy, tw, th, mirror);      // south
            if (mirror) {
                faces[2] = boxUv(u, v + dz, dz, dy, tw, th, true);                   // east
                faces[3] = boxUv(u + dz + dx, v + dz, dz, dy, tw, th, true);         // west
            } else {
                faces[2] = boxUv(u + dz + dx, v + dz, dz, dy, tw, th, false);        // east (+X в Bedrock смотрим слева)
                faces[3] = boxUv(u, v + dz, dz, dy, tw, th, false);                  // west
            }
            faces[4] = new FaceUv((u + dz + (mirror ? dx : 0)) / tw, (v + dz) / th,
                    (u + dz + (mirror ? 0 : dx)) / tw, v / th);                      // up
            faces[5] = new FaceUv((u + dz + dx + (mirror ? dx : 0)) / tw, v / th,
                    (u + dz + dx + (mirror ? 0 : dx)) / tw, (v + dz) / th);          // down
        }
        return new Cube(origin, size, pivot, rot, inflate, mirror, faces);
    }

    private static FaceUv boxUv(float u, float v, float w, float h, float tw, float th, boolean mirror) {
        float u1 = u / tw;
        float u2 = (u + w) / tw;
        float v1 = v / th;
        float v2 = (v + h) / th;
        return mirror ? new FaceUv(u2, v1, u1, v2) : new FaceUv(u1, v1, u2, v2);
    }

    @Nullable
    private static FaceUv parsePerFace(@Nullable JsonElement el, int texW, int texH) {
        if (el == null || !el.isJsonObject()) return null;
        JsonObject o = el.getAsJsonObject();
        if (!o.has("uv") || !o.get("uv").isJsonArray()) return null;
        JsonArray uv = o.getAsJsonArray("uv");
        if (uv.size() < 2) return null;
        float u = uv.get(0).getAsFloat();
        float v = uv.get(1).getAsFloat();
        float su = 0f, sv = 0f;
        if (o.has("uv_size") && o.get("uv_size").isJsonArray() && o.getAsJsonArray("uv_size").size() >= 2) {
            su = o.getAsJsonArray("uv_size").get(0).getAsFloat();
            sv = o.getAsJsonArray("uv_size").get(1).getAsFloat();
        }
        return new FaceUv(u / texW, v / texH, (u + su) / texW, (v + sv) / texH);
    }

    private static float[] vec3(@Nullable JsonElement el, float dx, float dy, float dz) {
        if (el != null && el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            if (arr.size() >= 3) {
                return new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat()};
            }
        }
        return new float[]{dx, dy, dz};
    }

    // ==================== ПАРСИНГ .animation.json ====================

    public static AnimationFile parseAnimationJson(String jsonText) {
        Map<String, AnimationClip> clips = new LinkedHashMap<>();
        JsonObject root = JsonParser.parseString(jsonText).getAsJsonObject();
        JsonObject anims = root.has("animations") && root.get("animations").isJsonObject()
                ? root.getAsJsonObject("animations") : root;

        for (Map.Entry<String, JsonElement> entry : anims.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            String name = entry.getKey();
            JsonObject ao = entry.getValue().getAsJsonObject();

            LoopMode loop = LoopMode.ONCE;
            if (ao.has("loop") && ao.get("loop").isJsonPrimitive()) {
                var lp = ao.get("loop").getAsJsonPrimitive();
                if (lp.isBoolean()) {
                    loop = lp.getAsBoolean() ? LoopMode.LOOP : LoopMode.ONCE;
                } else if (lp.isString()) {
                    String s = lp.getAsString().strip().toLowerCase(Locale.ROOT);
                    if (s.contains("hold")) loop = LoopMode.HOLD_LAST;
                    else if (s.equals("true") || s.equals("loop")) loop = LoopMode.LOOP;
                }
            }
            float maxKeyframeTime = 0f;
            Map<String, BoneAnim> boneAnims = new LinkedHashMap<>();
            if (ao.has("bones") && ao.get("bones").isJsonObject()) {
                for (Map.Entry<String, JsonElement> be : ao.getAsJsonObject("bones").entrySet()) {
                    if (!be.getValue().isJsonObject()) continue;
                    JsonObject bo = be.getValue().getAsJsonObject();
                    ChannelTrack rot = bo.has("rotation") ? parseChannel(bo.get("rotation"), 0f) : null;
                    ChannelTrack pos = bo.has("position") ? parseChannel(bo.get("position"), 0f) : null;
                    ChannelTrack scl = bo.has("scale") ? parseChannel(bo.get("scale"), 1f) : null;
                    maxKeyframeTime = Math.max(maxKeyframeTime, maxTimeOf(rot));
                    maxKeyframeTime = Math.max(maxKeyframeTime, maxTimeOf(pos));
                    maxKeyframeTime = Math.max(maxKeyframeTime, maxTimeOf(scl));
                    boneAnims.put(be.getKey(), new BoneAnim(rot, pos, scl));
                }
            }

            List<ParticleKeyframe> pfx = new ArrayList<>();
            if (ao.has("particle_effects") && ao.get("particle_effects").isJsonObject()) {
                for (Map.Entry<String, JsonElement> pe : ao.getAsJsonObject("particle_effects").entrySet()) {
                    float t = safeFloat(pe.getKey(), 0f);
                    maxKeyframeTime = Math.max(maxKeyframeTime, t);
                    parseParticleEntry(t, pe.getValue(), pfx);
                }
                pfx.sort((a, b) -> Float.compare(a.timeSec, b.timeSec));
            }

            List<SoundKeyframe> sfx = new ArrayList<>();
            if (ao.has("sound_effects") && ao.get("sound_effects").isJsonObject()) {
                for (Map.Entry<String, JsonElement> se : ao.getAsJsonObject("sound_effects").entrySet()) {
                    float t = safeFloat(se.getKey(), 0f);
                    maxKeyframeTime = Math.max(maxKeyframeTime, t);
                    parseSoundEntry(t, se.getValue(), sfx);
                }
                sfx.sort((a, b) -> Float.compare(a.timeSec, b.timeSec));
            }

            float lengthSec = ao.has("animation_length") ? ao.get("animation_length").getAsFloat() : maxKeyframeTime;
            clips.put(name, new AnimationClip(name, loop, Math.max(lengthSec, maxKeyframeTime), boneAnims, pfx, sfx));
        }
        return new AnimationFile(clips);
    }

    private static void parseParticleEntry(float t, JsonElement val, List<ParticleKeyframe> out) {
        if (val.isJsonArray()) {
            for (JsonElement el : val.getAsJsonArray()) {
                parseParticleEntry(t, el, out);
            }
        } else if (val.isJsonObject()) {
            JsonObject o = val.getAsJsonObject();
            String eff = o.has("effect") ? o.get("effect").getAsString() : "";
            String loc = o.has("locator") ? o.get("locator").getAsString() : "";
            if (!eff.isBlank()) {
                out.add(new ParticleKeyframe(t, eff.strip(), loc.strip()));
            }
        }
    }

    private static void parseSoundEntry(float t, JsonElement val, List<SoundKeyframe> out) {
        if (val.isJsonArray()) {
            for (JsonElement el : val.getAsJsonArray()) {
                parseSoundEntry(t, el, out);
            }
        } else if (val.isJsonObject() && val.getAsJsonObject().has("effect")) {
            String eff = val.getAsJsonObject().get("effect").getAsString().strip();
            if (!eff.isBlank()) {
                out.add(new SoundKeyframe(t, eff));
            }
        }
    }

    private static float maxTimeOf(@Nullable ChannelTrack tr) {
        return tr == null || tr.keyframes.isEmpty() ? 0f : tr.keyframes.get(tr.keyframes.size() - 1).timeSec;
    }

    private static ChannelTrack parseChannel(JsonElement el, float defScalar) {
        List<Keyframe> kfs = new ArrayList<>();
        if (el.isJsonArray() || el.isJsonPrimitive()) {
            Vec3Expr expr = parseVec3Expr(el, defScalar);
            kfs.add(new Keyframe(0f, expr, expr, "linear"));
            return new ChannelTrack(kfs);
        }
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("vector") || obj.has("post") || obj.has("pre")) {
                Vec3Expr pre = obj.has("pre") ? parseVec3Expr(obj.get("pre"), defScalar)
                        : parseVec3Expr(obj.has("post") ? obj.get("post") : obj.get("vector"), defScalar);
                Vec3Expr post = obj.has("post") ? parseVec3Expr(obj.get("post"), defScalar) : pre;
                String lerp = obj.has("lerp_mode") ? obj.get("lerp_mode").getAsString() : "linear";
                kfs.add(new Keyframe(0f, pre, post, lerp));
                return new ChannelTrack(kfs);
            }
            for (Map.Entry<String, JsonElement> ke : obj.entrySet()) {
                float t = safeFloat(ke.getKey(), 0f);
                JsonElement kv = ke.getValue();
                if (kv.isJsonObject()) {
                    JsonObject ko = kv.getAsJsonObject();
                    String lerp = ko.has("lerp_mode") ? ko.get("lerp_mode").getAsString() : "linear";
                    JsonElement postSrc = ko.has("post") ? ko.get("post") : ko.get("vector");
                    JsonElement preSrc = ko.has("pre") ? ko.get("pre") : postSrc;
                    Vec3Expr pre = parseVec3Expr(preSrc, defScalar);
                    Vec3Expr post = parseVec3Expr(postSrc, defScalar);
                    kfs.add(new Keyframe(t, pre, post, lerp));
                } else {
                    Vec3Expr expr = parseVec3Expr(kv, defScalar);
                    kfs.add(new Keyframe(t, expr, expr, "linear"));
                }
            }
            kfs.sort((a, b) -> Float.compare(a.timeSec, b.timeSec));
        }
        return new ChannelTrack(kfs);
    }

    private static Vec3Expr parseVec3Expr(@Nullable JsonElement el, float defScalar) {
        if (el == null) {
            ScalarExpr c = v -> defScalar;
            return new Vec3Expr(c, c, c);
        }
        if (el.isJsonObject() && el.getAsJsonObject().has("vector")) {
            return parseVec3Expr(el.getAsJsonObject().get("vector"), defScalar);
        }
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            if (arr.size() >= 3) {
                return new Vec3Expr(parseScalar(arr.get(0), defScalar),
                        parseScalar(arr.get(1), defScalar),
                        parseScalar(arr.get(2), defScalar));
            }
            if (arr.size() == 1) {
                ScalarExpr s = parseScalar(arr.get(0), defScalar);
                return new Vec3Expr(s, s, s);
            }
        }
        if (el.isJsonPrimitive()) {
            ScalarExpr s = parseScalar(el, defScalar);
            return new Vec3Expr(s, s, s);
        }
        ScalarExpr c = v -> defScalar;
        return new Vec3Expr(c, c, c);
    }

    private static ScalarExpr parseScalar(JsonElement el, float fallback) {
        if (!el.isJsonPrimitive()) return v -> fallback;
        var p = el.getAsJsonPrimitive();
        if (p.isNumber()) {
            float val = p.getAsFloat();
            return v -> val;
        }
        if (p.isBoolean()) {
            float val = p.getAsBoolean() ? 1f : 0f;
            return v -> val;
        }
        String raw = p.getAsString().strip();
        if (raw.isEmpty()) return v -> fallback;
        try {
            float val = Float.parseFloat(raw);
            return v -> val;
        } catch (NumberFormatException ignored) {
        }
        return compileMolang(raw, fallback);
    }

    private static float safeFloat(String s, float def) {
        try {
            return Float.parseFloat(s.strip());
        } catch (Exception e) {
            return def;
        }
    }

    // ==================== ЛЁГКИЙ КОМПИЛЯТОР MOLANG ====================

    private static ScalarExpr compileMolang(String expr, float fallback) {
        try {
            MolangParser parser = new MolangParser(expr);
            ScalarExpr compiled = parser.parseExpr();
            return vars -> {
                try {
                    float r = compiled.eval(vars);
                    return Float.isFinite(r) ? r : fallback;
                } catch (Exception e) {
                    return fallback;
                }
            };
        } catch (Exception e) {
            return v -> fallback;
        }
    }

    private static final class MolangParser {
        private final String s;
        private int pos;

        MolangParser(String src) {
            this.s = src.replace(";", "").strip();
        }

        private void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        ScalarExpr parseExpr() {
            ScalarExpr cond = parseAddSub();
            skipWs();
            if (pos < s.length() && s.charAt(pos) == '?') {
                pos++;
                ScalarExpr ifTrue = parseExpr();
                skipWs();
                ScalarExpr ifFalse = (pos < s.length() && s.charAt(pos) == ':') ? (++pos >= 0 ? parseExpr() : (v -> 0f)) : (v -> 0f);
                return v -> cond.eval(v) != 0f ? ifTrue.eval(v) : ifFalse.eval(v);
            }
            return cond;
        }

        private ScalarExpr parseAddSub() {
            ScalarExpr left = parseMulDiv();
            while (true) {
                skipWs();
                if (pos >= s.length()) break;
                char c = s.charAt(pos);
                if (c != '+' && c != '-') break;
                pos++;
                ScalarExpr right = parseMulDiv();
                ScalarExpr prev = left;
                left = c == '+' ? (v -> prev.eval(v) + right.eval(v)) : (v -> prev.eval(v) - right.eval(v));
            }
            return left;
        }

        private ScalarExpr parseMulDiv() {
            ScalarExpr left = parseUnary();
            while (true) {
                skipWs();
                if (pos >= s.length()) break;
                char c = s.charAt(pos);
                if (c != '*' && c != '/') break;
                pos++;
                ScalarExpr right = parseUnary();
                ScalarExpr prev = left;
                left = c == '*' ? (v -> prev.eval(v) * right.eval(v))
                        : (v -> {
                    float d = right.eval(v);
                    return Math.abs(d) < 1e-6f ? 0f : prev.eval(v) / d;
                });
            }
            return left;
        }

        private ScalarExpr parseUnary() {
            skipWs();
            if (pos < s.length() && s.charAt(pos) == '-') {
                pos++;
                ScalarExpr inner = parseUnary();
                return v -> -inner.eval(v);
            }
            if (pos < s.length() && s.charAt(pos) == '+') {
                pos++;
                return parseUnary();
            }
            if (pos < s.length() && s.charAt(pos) == '!') {
                pos++;
                ScalarExpr inner = parseUnary();
                return v -> inner.eval(v) == 0f ? 1f : 0f;
            }
            return parsePrimary();
        }

        private ScalarExpr parsePrimary() {
            skipWs();
            if (pos < s.length() && s.charAt(pos) == '(') {
                pos++;
                ScalarExpr inner = parseExpr();
                skipWs();
                if (pos < s.length() && s.charAt(pos) == ')') pos++;
                return inner;
            }
            int start = pos;
            if (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
                while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.'
                        || s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                    pos++;
                }
                float num = Float.parseFloat(s.substring(start, pos));
                return v -> num;
            }
            while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_' || s.charAt(pos) == '.')) {
                pos++;
            }
            String ident = s.substring(start, pos).toLowerCase(Locale.ROOT);
            skipWs();
            if (pos < s.length() && s.charAt(pos) == '(') {
                pos++;
                List<ScalarExpr> args = new ArrayList<>();
                skipWs();
                if (pos < s.length() && s.charAt(pos) != ')') {
                    while (true) {
                        args.add(parseExpr());
                        skipWs();
                        if (pos < s.length() && s.charAt(pos) == ',') {
                            pos++;
                        } else {
                            break;
                        }
                    }
                }
                if (pos < s.length() && s.charAt(pos) == ')') pos++;
                return buildFunc(ident, args);
            }
            return switch (ident) {
                case "query.anim_time", "q.anim_time", "anim_time" -> v -> v.animTime;
                case "query.life_time", "q.life_time", "life_time" -> v -> v.lifeTime;
                case "query.ground_speed", "q.ground_speed", "query.modified_move_speed" -> v -> v.groundSpeed;
                case "query.head_x_rotation", "q.head_x_rotation" -> v -> v.headPitch;
                case "query.head_y_rotation", "q.head_y_rotation" -> v -> v.headYaw;
                case "query.is_on_ground", "q.is_on_ground" -> v -> v.isOnGround ? 1f : 0f;
                case "math.pi" -> v -> (float) Math.PI;
                case "true" -> v -> 1f;
                case "false" -> v -> 0f;
                default -> v -> 0f;
            };
        }

        private static ScalarExpr buildFunc(String fn, List<ScalarExpr> a) {
            ScalarExpr a0 = a.size() > 0 ? a.get(0) : (v -> 0f);
            ScalarExpr a1 = a.size() > 1 ? a.get(1) : (v -> 0f);
            ScalarExpr a2 = a.size() > 2 ? a.get(2) : (v -> 0f);
            // В Bedrock Molang math.sin и math.cos принимают ГРАДУСЫ!
            return switch (fn) {
                case "math.sin" -> v -> Mth.sin(a0.eval(v) * Mth.DEG_TO_RAD);
                case "math.cos" -> v -> Mth.cos(a0.eval(v) * Mth.DEG_TO_RAD);
                case "math.abs" -> v -> Math.abs(a0.eval(v));
                case "math.sqrt" -> v -> Mth.sqrt(Math.max(0f, a0.eval(v)));
                case "math.floor" -> v -> (float) Math.floor(a0.eval(v));
                case "math.ceil" -> v -> (float) Math.ceil(a0.eval(v));
                case "math.round" -> v -> Math.round(a0.eval(v));
                case "math.min" -> v -> Math.min(a0.eval(v), a1.eval(v));
                case "math.max" -> v -> Math.max(a0.eval(v), a1.eval(v));
                case "math.clamp" -> v -> Mth.clamp(a0.eval(v), a1.eval(v), a2.eval(v));
                case "math.lerp" -> v -> Mth.lerp(a2.eval(v), a0.eval(v), a1.eval(v));
                case "math.mod" -> v -> {
                    float b = a1.eval(v);
                    return Math.abs(b) < 1e-6f ? 0f : a0.eval(v) % b;
                };
                default -> a0;
            };
        }
    }
}
