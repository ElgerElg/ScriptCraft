package com.scriptcraft.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Чистая Java-модель 3D-анимации игрока для редактора в Студии.
 * Использует интуитивные координаты стандарта Blockbench:
 * — Позиция в пикселях (16 px = 1 блок) для всех костей включая {@code body};
 * — {@code +X} вправо на экране (спереди), {@code +Y} ВВЕРХ, {@code +Z} назад (от зрителя), {@code -Z} вперёд.
 */
@OnlyIn(Dist.CLIENT)
public final class StudioAnimModel {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private StudioAnimModel() {
    }

    public enum BoneId {
        BODY("body", "studio.anim.bone.body", 0f, 0f, 0f, true, 0xFFF0B85B),
        TORSO("torso", "studio.anim.bone.torso", 0f, 0f, 0f, true, 0xFF58A6FF),
        HEAD("head", "studio.anim.bone.head", 0f, 0f, 0f, false, 0xFF4CC38A),
        RIGHT_ARM("rightArm", "studio.anim.bone.rarm", -5f, 2f, 0f, true, 0xFFE06C75),
        LEFT_ARM("leftArm", "studio.anim.bone.larm", 5f, 2f, 0f, true, 0xFF61AFEF),
        RIGHT_LEG("rightLeg", "studio.anim.bone.rleg", -1.9f, 12f, 0.1f, true, 0xFFE5C07B),
        LEFT_LEG("leftLeg", "studio.anim.bone.lleg", 1.9f, 12f, 0.1f, true, 0xFFC678DD),
        RIGHT_ITEM("rightItem", "studio.anim.bone.ritem", 0f, 0f, 0f, false, 0xFFF97316),
        LEFT_ITEM("leftItem", "studio.anim.bone.litem", 0f, 0f, 0f, false, 0xFF22D3EE);

        public final String partKey;
        public final String langKey;
        public final float defX;
        public final float defY;
        public final float defZ;
        public final boolean bendable;
        public final int color;

        BoneId(String partKey, String langKey, float defX, float defY, float defZ, boolean bendable, int color) {
            this.partKey = partKey;
            this.langKey = langKey;
            this.defX = defX;
            this.defY = defY;
            this.defZ = defZ;
            this.bendable = bendable;
            this.color = color;
        }

        public static BoneId byPartKey(String key) {
            if (key == null) return null;
            String clean = key.trim().replace("_", "").replace("-", "");
            for (BoneId b : values()) {
                if (b.partKey.equalsIgnoreCase(key) || b.partKey.equalsIgnoreCase(clean)) {
                    return b;
                }
            }
            return null;
        }
    }

    public enum Channel {
        ROT_X("pitch", "Rot X", true, 0xFFEF4444, 5f),
        ROT_Y("yaw", "Rot Y", true, 0xFF22C55E, 5f),
        ROT_Z("roll", "Rot Z", true, 0xFF3B82F6, 5f),
        POS_X("x", "Pos X", false, 0xFFEF4444, 0.5f),
        POS_Y("y", "Pos Y", false, 0xFF22C55E, 0.5f),
        POS_Z("z", "Pos Z", false, 0xFF3B82F6, 0.5f),
        BEND_DIR("axis", "Bend Dir", true, 0xFFA855F7, 5f),
        BEND_ANG("bend", "Bend °", true, 0xFFEC4899, 5f);

        public final String jsonKey;
        public final String shortLabel;
        public final boolean angle;
        public final int axisColor;
        public final float step;

        Channel(String jsonKey, String shortLabel, boolean angle, int axisColor, float step) {
            this.jsonKey = jsonKey;
            this.shortLabel = shortLabel;
            this.angle = angle;
            this.axisColor = axisColor;
            this.step = step;
        }

        public boolean isBend() {
            return this == BEND_DIR || this == BEND_ANG;
        }

        public static Channel byAnyKey(String raw) {
            if (raw == null) return null;
            String k = raw.trim().toLowerCase(Locale.ROOT);
            return switch (k) {
                case "pitch", "rot_x", "rotx", "rx" -> ROT_X;
                case "yaw", "rot_y", "roty", "ry" -> ROT_Y;
                case "roll", "rot_z", "rotz", "rz" -> ROT_Z;
                case "x", "pos_x", "posx", "px" -> POS_X;
                case "y", "pos_y", "posy", "py" -> POS_Y;
                case "z", "pos_z", "posz", "pz" -> POS_Z;
                case "axis", "bend_dir", "benddir", "bend_direction", "bend_axis" -> BEND_DIR;
                case "bend", "bend_ang", "bendang", "bend_angle" -> BEND_ANG;
                default -> null;
            };
        }
    }

    public enum EaseCurve {
        LINEAR, CONSTANT,
        INOUTSINE, INSINE, OUTSINE,
        INQUAD, OUTQUAD, INOUTQUAD,
        INCUBIC, OUTCUBIC, INOUTCUBIC,
        INQUART, OUTQUART, INOUTQUART,
        INQUINT, OUTQUINT, INOUTQUINT,
        INEXPO, OUTEXPO, INOUTEXPO,
        INCIRC, OUTCIRC, INOUTCIRC,
        INBACK, OUTBACK, INOUTBACK,
        INELASTIC, OUTELASTIC, INOUTELASTIC,
        INBOUNCE, OUTBOUNCE, INOUTBOUNCE;

        public static EaseCurve parse(String raw) {
            if (raw == null || raw.isBlank()) return LINEAR;
            String clean = raw.trim().toUpperCase(Locale.ROOT).replace("_", "").replace("-", "");
            if (clean.startsWith("EASE")) clean = clean.substring(4);
            for (EaseCurve e : values()) {
                if (e.name().equals(clean)) return e;
            }
            return LINEAR;
        }

        public float eval(float t) {
            float x = Math.max(0f, Math.min(1f, t));
            final float c1 = 1.70158f;
            final float c2 = c1 * 1.525f;
            final float c3 = c1 + 1f;
            final float c4 = (float) ((2.0 * Math.PI) / 3.0);
            final float c5 = (float) ((2.0 * Math.PI) / 4.5);
            return switch (this) {
                case LINEAR -> x;
                case CONSTANT -> x >= 1f ? 1f : 0f;
                case INSINE -> 1f - (float) Math.cos((x * Math.PI) / 2.0);
                case OUTSINE -> (float) Math.sin((x * Math.PI) / 2.0);
                case INOUTSINE -> -((float) Math.cos(Math.PI * x) - 1f) / 2f;
                case INQUAD -> x * x;
                case OUTQUAD -> 1f - (1f - x) * (1f - x);
                case INOUTQUAD -> x < 0.5f ? 2f * x * x : 1f - (float) Math.pow(-2f * x + 2f, 2) / 2f;
                case INCUBIC -> x * x * x;
                case OUTCUBIC -> 1f - (float) Math.pow(1f - x, 3);
                case INOUTCUBIC -> x < 0.5f ? 4f * x * x * x : 1f - (float) Math.pow(-2f * x + 2f, 3) / 2f;
                case INQUART -> x * x * x * x;
                case OUTQUART -> 1f - (float) Math.pow(1f - x, 4);
                case INOUTQUART -> x < 0.5f ? 8f * x * x * x * x : 1f - (float) Math.pow(-2f * x + 2f, 4) / 2f;
                case INQUINT -> x * x * x * x * x;
                case OUTQUINT -> 1f - (float) Math.pow(1f - x, 5);
                case INOUTQUINT -> x < 0.5f ? 16f * x * x * x * x * x : 1f - (float) Math.pow(-2f * x + 2f, 5) / 2f;
                case INEXPO -> x == 0f ? 0f : (float) Math.pow(2.0, 10.0 * x - 10.0);
                case OUTEXPO -> x == 1f ? 1f : 1f - (float) Math.pow(2.0, -10.0 * x);
                case INOUTEXPO -> x == 0f ? 0f : x == 1f ? 1f
                        : x < 0.5f ? (float) Math.pow(2.0, 20.0 * x - 10.0) / 2f
                        : (2f - (float) Math.pow(2.0, -20.0 * x + 10.0)) / 2f;
                case INCIRC -> 1f - (float) Math.sqrt(Math.max(0f, 1f - x * x));
                case OUTCIRC -> (float) Math.sqrt(Math.max(0f, 1f - (x - 1f) * (x - 1f)));
                case INOUTCIRC -> x < 0.5f
                        ? (1f - (float) Math.sqrt(Math.max(0f, 1f - 4f * x * x))) / 2f
                        : ((float) Math.sqrt(Math.max(0f, 1f - (float) Math.pow(-2f * x + 2f, 2))) + 1f) / 2f;
                case INBACK -> c3 * x * x * x - c1 * x * x;
                case OUTBACK -> 1f + c3 * (float) Math.pow(x - 1f, 3) + c1 * (float) Math.pow(x - 1f, 2);
                case INOUTBACK -> x < 0.5f
                        ? ((float) Math.pow(2f * x, 2) * ((c2 + 1f) * 2f * x - c2)) / 2f
                        : ((float) Math.pow(2f * x - 2f, 2) * ((c2 + 1f) * (x * 2f - 2f) + c2) + 2f) / 2f;
                case INELASTIC -> x == 0f ? 0f : x == 1f ? 1f
                        : -(float) Math.pow(2.0, 10.0 * x - 10.0) * (float) Math.sin((x * 10.0 - 10.75) * c4);
                case OUTELASTIC -> x == 0f ? 0f : x == 1f ? 1f
                        : (float) Math.pow(2.0, -10.0 * x) * (float) Math.sin((x * 10.0 - 0.75) * c4) + 1f;
                case INOUTELASTIC -> x == 0f ? 0f : x == 1f ? 1f
                        : x < 0.5f
                        ? -((float) Math.pow(2.0, 20.0 * x - 10.0) * (float) Math.sin((20.0 * x - 11.125) * c5)) / 2f
                        : ((float) Math.pow(2.0, -20.0 * x + 10.0) * (float) Math.sin((20.0 * x - 11.125) * c5)) / 2f + 1f;
                case INBOUNCE -> 1f - bounceOut(1f - x);
                case OUTBOUNCE -> bounceOut(x);
                case INOUTBOUNCE -> x < 0.5f
                        ? (1f - bounceOut(1f - 2f * x)) / 2f
                        : (1f + bounceOut(2f * x - 1f)) / 2f;
            };
        }

        private static float bounceOut(float x) {
            final float n1 = 7.5625f;
            final float d1 = 2.75f;
            if (x < 1f / d1) {
                return n1 * x * x;
            } else if (x < 2f / d1) {
                float t = x - 1.5f / d1;
                return n1 * t * t + 0.75f;
            } else if (x < 2.5f / d1) {
                float t = x - 2.25f / d1;
                return n1 * t * t + 0.9375f;
            } else {
                float t = x - 2.625f / d1;
                return n1 * t * t + 0.984375f;
            }
        }
    }

    public record KeyPoint(int tick, float value, EaseCurve ease) {
    }

    public record KeyRef(BoneId bone, Channel channel, int tick) {
    }

    public static final class AnimDoc {
        public String namespace = "scriptcraft";
        public String fileName = "new_anim";
        public String displayName = "New Animation";
        public String author = "ScriptCraft";
        public String description = "";
        public int beginTick = 2;
        public int endTick = 40;
        public int stopTick = 45;
        public boolean looped = false;
        public int returnTick = 0;
        public boolean easingBefore = false;
        public UUID uuid = UUID.randomUUID();

        private final EnumMap<BoneId, EnumMap<Channel, TreeMap<Integer, KeyPoint>>> tracks = new EnumMap<>(BoneId.class);
        private final EnumMap<BoneId, Boolean> boneVisible = new EnumMap<>(BoneId.class);
        private final Deque<String> undoStack = new ArrayDeque<>();
        private final Deque<String> redoStack = new ArrayDeque<>();
        /** O(1) отслеживание правок без сериализации JSON на каждом кадре рендера! */
        private long editVersion = 0L;
        private long savedVersion = 0L;

        public AnimDoc() {
            for (BoneId b : BoneId.values()) {
                EnumMap<Channel, TreeMap<Integer, KeyPoint>> chMap = new EnumMap<>(Channel.class);
                for (Channel c : Channel.values()) {
                    chMap.put(c, new TreeMap<>());
                }
                tracks.put(b, chMap);
                boneVisible.put(b, true);
            }
        }

        public void touch() {
            editVersion++;
        }

        public long version() {
            return editVersion;
        }

        public void clearTracks() {
            for (BoneId b : BoneId.values()) {
                for (Channel c : Channel.values()) {
                    tracks.get(b).get(c).clear();
                }
                boneVisible.put(b, true);
            }
            touch();
        }

        public String fullId() {
            return namespace + ":" + fileName;
        }

        public String serverRelPath() {
            return "resources/assets/" + namespace + "/player_animation/" + fileName + ".json";
        }

        public boolean isBoneVisible(BoneId bone) {
            return boneVisible.getOrDefault(bone, true);
        }

        public void toggleBoneVisible(BoneId bone) {
            boneVisible.put(bone, !isBoneVisible(bone));
        }

        public TreeMap<Integer, KeyPoint> track(BoneId bone, Channel ch) {
            return tracks.get(bone).get(ch);
        }

        public boolean hasKeys(BoneId bone, Channel ch) {
            return !tracks.get(bone).get(ch).isEmpty();
        }

        public boolean boneHasAnyKeys(BoneId bone) {
            for (Channel c : Channel.values()) {
                if (!tracks.get(bone).get(c).isEmpty()) return true;
            }
            return false;
        }

        public boolean boneHasKeyAt(BoneId bone, int tick) {
            for (Channel c : Channel.values()) {
                if (tracks.get(bone).get(c).containsKey(tick)) return true;
            }
            return false;
        }

        public void putKey(BoneId bone, Channel ch, int tick, float value, EaseCurve ease) {
            if (!bone.bendable && ch.isBend()) return;
            int clampedTick = Math.max(0, tick);
            float rounded = Math.round(value * 100f) / 100f;
            if (ch == Channel.BEND_ANG) {
                rounded = Math.max(-180f, Math.min(180f, rounded));
            }
            tracks.get(bone).get(ch).put(clampedTick, new KeyPoint(clampedTick, rounded, ease == null ? EaseCurve.INOUTSINE : ease));
            if (clampedTick > endTick) {
                endTick = clampedTick;
                if (stopTick <= endTick) stopTick = endTick + 5;
            }
            touch();
        }

        public void removeKey(BoneId bone, Channel ch, int tick) {
            if (tracks.get(bone).get(ch).remove(tick) != null) {
                touch();
            }
        }

        public float eval(BoneId bone, Channel ch, float tick) {
            if (!isBoneVisible(bone)) return 0f;
            if (!bone.bendable && ch.isBend()) return 0f;
            TreeMap<Integer, KeyPoint> map = tracks.get(bone).get(ch);
            if (map.isEmpty()) return 0f;

            float t = Math.max(0f, tick);
            if (looped && endTick > returnTick && t > endTick) {
                int span = Math.max(1, endTick - returnTick);
                t = returnTick + ((t - returnTick) % span);
            }

            int floorTick = (int) Math.floor(t);
            Map.Entry<Integer, KeyPoint> beforeEntry = map.floorEntry(floorTick);
            Map.Entry<Integer, KeyPoint> afterEntry = map.higherEntry(floorTick);

            if (beforeEntry == null && afterEntry != null) {
                KeyPoint first = afterEntry.getValue();
                if (first.tick == 0) return first.value;
                float frac = Math.max(0f, Math.min(1f, t / Math.max(1f, first.tick)));
                EaseCurve ease = easingBefore ? first.ease : EaseCurve.INOUTSINE;
                return lerp(0f, first.value, ease.eval(frac));
            }
            if (beforeEntry != null && afterEntry == null) {
                KeyPoint last = beforeEntry.getValue();
                if (looped) {
                    Map.Entry<Integer, KeyPoint> loopTarget = map.ceilingEntry(returnTick);
                    if (loopTarget != null && last.tick < endTick) {
                        int totalSpan = (endTick - last.tick) + (loopTarget.getKey() - returnTick);
                        if (totalSpan > 0) {
                            float frac = Math.max(0f, Math.min(1f, (t - last.tick) / totalSpan));
                            EaseCurve ease = easingBefore ? loopTarget.getValue().ease : last.ease;
                            return lerp(last.value, loopTarget.getValue().value, ease.eval(frac));
                        }
                    }
                    return last.value;
                }
                if (t <= endTick) {
                    return last.value;
                }
                int fadeSpan = Math.max(1, stopTick - endTick);
                if (t >= stopTick) return 0f;
                float frac = Math.max(0f, Math.min(1f, (t - endTick) / fadeSpan));
                return lerp(last.value, 0f, EaseCurve.INOUTSINE.eval(frac));
            }
            if (beforeEntry != null) {
                KeyPoint k0 = beforeEntry.getValue();
                KeyPoint k1 = afterEntry.getValue();
                if (k1.tick <= k0.tick) return k0.value;
                float frac = Math.max(0f, Math.min(1f, (t - k0.tick) / (k1.tick - k0.tick)));
                EaseCurve ease = easingBefore ? k1.ease : k0.ease;
                return lerp(k0.value, k1.value, ease.eval(frac));
            }
            return 0f;
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }

        public void mirrorPoseAt(int tick, EaseCurve defaultEase) {
            swapAndMirrorPair(BoneId.RIGHT_ARM, BoneId.LEFT_ARM, tick, defaultEase);
            swapAndMirrorPair(BoneId.RIGHT_LEG, BoneId.LEFT_LEG, tick, defaultEase);
            swapAndMirrorPair(BoneId.RIGHT_ITEM, BoneId.LEFT_ITEM, tick, defaultEase);
            mirrorSingleBone(BoneId.BODY, tick, defaultEase);
            mirrorSingleBone(BoneId.TORSO, tick, defaultEase);
            mirrorSingleBone(BoneId.HEAD, tick, defaultEase);
        }

        private void swapAndMirrorPair(BoneId a, BoneId b, int tick, EaseCurve defaultEase) {
            for (Channel ch : Channel.values()) {
                if (!a.bendable && ch.isBend()) continue;
                boolean hasA = hasKeys(a, ch);
                boolean hasB = hasKeys(b, ch);
                if (!hasA && !hasB) continue;
                float valA = eval(a, ch, tick);
                float valB = eval(b, ch, tick);
                float sign = mirrorSign(ch);
                KeyPoint kpA = track(a, ch).get(tick);
                KeyPoint kpB = track(b, ch).get(tick);
                EaseCurve easeA = kpA != null ? kpA.ease : defaultEase;
                EaseCurve easeB = kpB != null ? kpB.ease : defaultEase;
                putKey(a, ch, tick, valB * sign, easeB);
                putKey(b, ch, tick, valA * sign, easeA);
            }
        }

        private void mirrorSingleBone(BoneId bone, int tick, EaseCurve defaultEase) {
            for (Channel ch : Channel.values()) {
                if (!bone.bendable && ch.isBend()) continue;
                if (!hasKeys(bone, ch)) continue;
                float sign = mirrorSign(ch);
                if (sign < 0f) {
                    float val = eval(bone, ch, tick);
                    KeyPoint kp = track(bone, ch).get(tick);
                    putKey(bone, ch, tick, -val, kp != null ? kp.ease : defaultEase);
                }
            }
        }

        private static float mirrorSign(Channel ch) {
            return switch (ch) {
                case ROT_Y, ROT_Z, POS_X, BEND_DIR -> -1f;
                default -> 1f;
            };
        }

        // --- Undo / Redo ---

        public String snapshot() {
            return toStandaloneJson(this);
        }

        public void pushUndo() {
            String snap = snapshot();
            if (!undoStack.isEmpty() && undoStack.peekLast().equals(snap)) {
                return;
            }
            undoStack.addLast(snap);
            while (undoStack.size() > 80) {
                undoStack.removeFirst();
            }
            redoStack.clear();
        }

        public boolean undo() {
            if (undoStack.isEmpty()) return false;
            String cur = snapshot();
            String prev = undoStack.removeLast();
            redoStack.addLast(cur);
            fromStandaloneJson(prev, this);
            touch();
            return true;
        }

        public boolean redo() {
            if (redoStack.isEmpty()) return false;
            String cur = snapshot();
            String next = redoStack.removeLast();
            undoStack.addLast(cur);
            fromStandaloneJson(next, this);
            touch();
            return true;
        }

        public void resetHistory() {
            undoStack.clear();
            redoStack.clear();
            savedVersion = editVersion;
        }

        public void markSaved() {
            savedVersion = editVersion;
        }

        public boolean isDirty() {
            return editVersion != savedVersion;
        }
    }

    // --- Конвертация UI-координат <-> координаты StateCollection движка PlayerAnimator ---

    /** Преобразует смещение UI (в пикселях, +X вправо, +Y вверх) в сырое значение StateCollection. */
    public static float uiPosToEngine(BoneId bone, Channel ch, float uiVal, float stateDefault) {
        if (bone == BoneId.BODY) {
            return switch (ch) {
                case POS_X -> -uiVal / 16f;
                case POS_Y -> uiVal / 16f;
                case POS_Z -> uiVal / 16f;
                default -> uiVal;
            };
        }
        return switch (ch) {
            case POS_X -> stateDefault + uiVal;
            case POS_Y -> stateDefault - uiVal;
            case POS_Z -> stateDefault + uiVal;
            default -> uiVal;
        };
    }

    /** Обратное преобразование из StateCollection движка в UI-координаты (пиксели, +Y вверх). */
    public static float enginePosToUi(BoneId bone, Channel ch, float rawVal, float stateDefault) {
        if (bone == BoneId.BODY) {
            return switch (ch) {
                case POS_X -> -rawVal * 16f;
                case POS_Y -> rawVal * 16f;
                case POS_Z -> rawVal * 16f;
                default -> rawVal;
            };
        }
        return switch (ch) {
            case POS_X -> rawVal - stateDefault;
            case POS_Y -> stateDefault - rawVal;
            case POS_Z -> rawVal - stateDefault;
            default -> rawVal;
        };
    }

    /**
     * Гарантирует допустимый путь ResourceLocation ([a-z0-9/._-]), чтобы PlayerAnimationRegistry
     * никогда не падал с ResourceLocationException при кириллице или пробелах в поле "name".
     */
    public static String safeResourceSlug(String raw, String fallback) {
        String fb = sanitizeSlugOnly(fallback);
        if (fb.isEmpty()) fb = "anim";
        String res = sanitizeSlugOnly(raw);
        return res.isEmpty() ? fb : res;
    }

    private static String sanitizeSlugOnly(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        boolean prevSep = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '/') {
                sb.append(c);
                prevSep = false;
            } else if (c == '_' || c == ' ') {
                if (!prevSep && !sb.isEmpty()) {
                    sb.append('_');
                    prevSep = true;
                }
            } else {
                String tr = translitRu(c);
                if (!tr.isEmpty()) {
                    sb.append(tr);
                    prevSep = false;
                }
            }
        }
        String out = sb.toString();
        while (out.endsWith("_")) out = out.substring(0, out.length() - 1);
        return out;
    }

    private static String translitRu(char c) {
        return switch (c) {
            case 'а' -> "a"; case 'б' -> "b"; case 'в' -> "v"; case 'г' -> "g"; case 'д' -> "d";
            case 'е', 'ё', 'э' -> "e"; case 'ж' -> "zh"; case 'з' -> "z"; case 'и', 'й', 'ы' -> "i";
            case 'к' -> "k"; case 'л' -> "l"; case 'м' -> "m"; case 'н' -> "n"; case 'о' -> "o";
            case 'п' -> "p"; case 'р' -> "r"; case 'с' -> "s"; case 'т' -> "t"; case 'у' -> "u";
            case 'ф' -> "f"; case 'х' -> "h"; case 'ц' -> "ts"; case 'ч' -> "ch"; case 'ш' -> "sh";
            case 'щ' -> "sch"; case 'ю' -> "yu"; case 'я' -> "ya";
            default -> "";
        };
    }

    // --- Сериализация / десериализация (PlayerAnimator + формат channels/tablurseat + автономный резерв) ---

    public static String serialize(AnimDoc doc) {
        if (ModList.get().isLoaded("playeranimator")) {
            try {
                return PlayerAnimBridge.serializeDoc(doc);
            } catch (Throwable ignored) {
            }
        }
        return toStandaloneJson(doc);
    }

    public static boolean deserialize(String json, AnimDoc out) {
        if (json == null || json.isBlank()) return false;
        // 1) Сначала проверяем канальный формат (типа tablurseat.json: formatVersion / bones.*.channels.*)
        if (fromChannelsJsonString(json, out)) {
            return true;
        }
        // 2) Штатный десериализатор PlayerAnimator (Emotecraft v1..v3 + Blockbench/GeckoLib animations)
        if (ModList.get().isLoaded("playeranimator")) {
            try {
                if (PlayerAnimBridge.deserializeInto(json, out)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        // 3) Автономный парсер Emotecraft JSON и Blockbench/GeckoLib JSON
        return fromStandaloneJson(json, out);
    }

    public static String toStandaloneJson(AnimDoc doc) {
        JsonObject root = new JsonObject();
        String safeName = safeResourceSlug(doc.fileName, safeResourceSlug(doc.displayName, "anim"));
        root.addProperty("name", safeName);
        if (doc.displayName != null && !doc.displayName.isBlank()) {
            root.addProperty("title", doc.displayName);
        }
        root.addProperty("author", doc.author == null || doc.author.isBlank() ? "ScriptCraft" : doc.author);
        root.addProperty("description", doc.description == null ? "" : doc.description);
        root.addProperty("version", 3);
        if (doc.uuid != null) {
            root.addProperty("uuid", doc.uuid.toString());
        }

        JsonObject emote = new JsonObject();
        emote.addProperty("beginTick", Math.max(0, doc.beginTick));
        emote.addProperty("endTick", Math.max(1, doc.endTick));
        emote.addProperty("stopTick", Math.max(doc.endTick + 1, doc.stopTick));
        emote.addProperty("isLoop", doc.looped);
        emote.addProperty("returnTick", Math.max(0, Math.min(doc.endTick, doc.returnTick)));
        emote.addProperty("nsfw", false);
        emote.addProperty("degrees", true);

        JsonArray moves = new JsonArray();
        for (BoneId bone : BoneId.values()) {
            for (Channel ch : Channel.values()) {
                if (!bone.bendable && ch.isBend()) continue;
                for (KeyPoint kp : doc.track(bone, ch).values()) {
                    JsonObject m = new JsonObject();
                    m.addProperty("tick", kp.tick);
                    m.addProperty("easing", kp.ease.name());
                    m.addProperty("turn", 0);
                    JsonObject partObj = new JsonObject();
                    float def = ch == Channel.POS_X ? bone.defX : (ch == Channel.POS_Y ? bone.defY : bone.defZ);
                    float rawVal = ch.angle ? kp.value : uiPosToEngine(bone, ch, kp.value, def);
                    partObj.addProperty(ch.jsonKey, rawVal);
                    m.add(bone.partKey, partObj);
                    moves.add(m);
                }
            }
        }
        emote.add("moves", moves);
        root.add("emote", emote);
        return GSON.toJson(root);
    }

    /**
     * Читает формат канальных анимаций (например tablurseat.json):
     * {@code { "formatVersion": 1, "id": "...", "lengthTicks": 40, "loop": "hold_last", "bones": { "right_leg": { "channels": { "rot_x": [{"t": 26, "v": -1.64}] } } } }}
     */
    public static boolean fromChannelsJsonString(String json, AnimDoc out) {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) return false;
            JsonObject root = parsed.getAsJsonObject();
            if (!root.has("bones") || !root.get("bones").isJsonObject() || root.has("emote") || root.has("animations")) {
                return false;
            }
            return fromChannelsJson(root, out);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean fromChannelsJson(JsonObject root, AnimDoc out) {
        out.clearTracks();
        if (root.has("id") && root.get("id").isJsonPrimitive()) {
            String rawId = root.get("id").getAsString();
            if (rawId != null && !rawId.isBlank()) {
                int colon = rawId.indexOf(':');
                if (colon >= 0) {
                    out.namespace = safeResourceSlug(rawId.substring(0, colon), out.namespace);
                    out.fileName = safeResourceSlug(rawId.substring(colon + 1), out.fileName);
                } else if ("new_anim".equals(out.fileName) || out.fileName == null || out.fileName.isBlank()) {
                    out.fileName = safeResourceSlug(rawId, "anim");
                }
                out.displayName = rawId;
            }
        }
        if (root.has("displayName") && root.get("displayName").isJsonPrimitive()) {
            out.displayName = root.get("displayName").getAsString();
        } else if (root.has("title") && root.get("title").isJsonPrimitive()) {
            out.displayName = root.get("title").getAsString();
        } else if (root.has("name") && root.get("name").isJsonPrimitive()) {
            out.displayName = root.get("name").getAsString();
        }
        if (root.has("author") && root.get("author").isJsonPrimitive()) {
            out.author = root.get("author").getAsString();
        }
        if (root.has("description") && root.get("description").isJsonPrimitive()) {
            out.description = root.get("description").getAsString();
        }

        boolean degrees = root.has("degrees") && root.get("degrees").isJsonPrimitive() && root.get("degrees").getAsBoolean();
        int len = root.has("lengthTicks") && root.get("lengthTicks").isJsonPrimitive()
                ? Math.max(1, root.get("lengthTicks").getAsInt())
                : (root.has("endTick") && root.get("endTick").isJsonPrimitive() ? Math.max(1, root.get("endTick").getAsInt()) : 40);
        out.beginTick = root.has("beginTick") && root.get("beginTick").isJsonPrimitive()
                ? Math.max(0, root.get("beginTick").getAsInt()) : 0;
        out.endTick = len;
        out.stopTick = root.has("stopTick") && root.get("stopTick").isJsonPrimitive()
                ? Math.max(len + 1, root.get("stopTick").getAsInt()) : len + 5;
        out.looped = false;
        out.returnTick = root.has("returnTick") && root.get("returnTick").isJsonPrimitive()
                ? Math.max(0, root.get("returnTick").getAsInt()) : 0;
        out.easingBefore = false;

        if (root.has("loop") && root.get("loop").isJsonPrimitive()) {
            var lp = root.get("loop").getAsJsonPrimitive();
            if (lp.isBoolean()) {
                out.looped = lp.getAsBoolean();
            } else if (lp.isString()) {
                String mode = lp.getAsString().trim().toLowerCase(Locale.ROOT);
                if (mode.equals("hold_last") || mode.equals("hold_on_last_frame") || mode.equals("hold")) {
                    out.looped = true;
                    out.returnTick = out.endTick;
                } else if (mode.equals("true") || mode.equals("loop") || mode.equals("repeat")) {
                    out.looped = true;
                }
            }
        }

        boolean holdLast = out.looped && out.returnTick == out.endTick;
        JsonObject bonesObj = root.getAsJsonObject("bones");
        for (Map.Entry<String, JsonElement> boneEntry : bonesObj.entrySet()) {
            BoneId bone = BoneId.byPartKey(boneEntry.getKey());
            if (bone == null || !boneEntry.getValue().isJsonObject()) continue;
            JsonObject boneObj = boneEntry.getValue().getAsJsonObject();
            JsonObject chContainer = boneObj.has("channels") && boneObj.get("channels").isJsonObject()
                    ? boneObj.getAsJsonObject("channels") : boneObj;
            for (Map.Entry<String, JsonElement> chEntry : chContainer.entrySet()) {
                Channel ch = Channel.byAnyKey(chEntry.getKey());
                if (ch == null || !chEntry.getValue().isJsonArray()) continue;
                for (JsonElement kfEl : chEntry.getValue().getAsJsonArray()) {
                    if (!kfEl.isJsonObject()) continue;
                    JsonObject kf = kfEl.getAsJsonObject();
                    int tick = kf.has("t") ? Math.round(kf.get("t").getAsFloat())
                            : (kf.has("tick") ? Math.round(kf.get("tick").getAsFloat()) : 0);
                    float rawVal = kf.has("v") ? kf.get("v").getAsFloat()
                            : (kf.has("value") ? kf.get("value").getAsFloat() : 0f);
                    EaseCurve ease = kf.has("ease") ? EaseCurve.parse(kf.get("ease").getAsString())
                            : (kf.has("easing") ? EaseCurve.parse(kf.get("easing").getAsString()) : EaseCurve.INOUTSINE);
                    float uiVal;
                    if (ch.angle) {
                        uiVal = degrees ? rawVal : (float) Math.toDegrees(rawVal);
                    } else {
                        float def = ch == Channel.POS_X ? bone.defX : (ch == Channel.POS_Y ? bone.defY : bone.defZ);
                        uiVal = enginePosToUi(bone, ch, rawVal, def);
                    }
                    out.putKey(bone, ch, tick, uiVal, ease);
                }
            }
        }
        if (holdLast) {
            out.returnTick = out.endTick;
        }
        return true;
    }

    public static boolean fromStandaloneJson(String json, AnimDoc out) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("bones") && root.get("bones").isJsonObject() && !root.has("emote") && !root.has("animations")) {
                return fromChannelsJson(root, out);
            }
            if (!root.has("emote")) {
                return false;
            }
            out.clearTracks();
            if (root.has("title") && root.get("title").isJsonPrimitive()) {
                out.displayName = root.get("title").getAsString();
            } else if (root.has("name") && root.get("name").isJsonPrimitive()) {
                out.displayName = root.get("name").getAsString();
            }
            if (root.has("author") && root.get("author").isJsonPrimitive()) {
                out.author = root.get("author").getAsString();
            }
            if (root.has("description") && root.get("description").isJsonPrimitive()) {
                out.description = root.get("description").getAsString();
            }
            JsonObject emote = root.getAsJsonObject("emote");
            boolean degrees = !emote.has("degrees") || emote.get("degrees").getAsBoolean();
            out.beginTick = emote.has("beginTick") ? emote.get("beginTick").getAsInt() : 0;
            out.endTick = emote.has("endTick") ? Math.max(1, emote.get("endTick").getAsInt()) : 40;
            out.stopTick = emote.has("stopTick") ? Math.max(out.endTick + 1, emote.get("stopTick").getAsInt()) : out.endTick + 5;
            out.looped = emote.has("isLoop") && emote.get("isLoop").getAsBoolean();
            out.returnTick = emote.has("returnTick") ? emote.get("returnTick").getAsInt() : 0;
            out.easingBefore = false;

            if (emote.has("moves") && emote.get("moves").isJsonArray()) {
                for (JsonElement el : emote.getAsJsonArray("moves")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject m = el.getAsJsonObject();
                    int tick = m.has("tick") ? m.get("tick").getAsInt() : 0;
                    EaseCurve ease = m.has("easing") ? EaseCurve.parse(m.get("easing").getAsString()) : EaseCurve.LINEAR;
                    for (BoneId bone : BoneId.values()) {
                        if (!m.has(bone.partKey) || !m.get(bone.partKey).isJsonObject()) continue;
                        JsonObject p = m.getAsJsonObject(bone.partKey);
                        for (Channel ch : Channel.values()) {
                            if (!p.has(ch.jsonKey)) continue;
                            float v = p.get(ch.jsonKey).getAsFloat();
                            if (ch.angle) {
                                if (!degrees) v = (float) Math.toDegrees(v);
                            } else {
                                float def = ch == Channel.POS_X ? bone.defX : (ch == Channel.POS_Y ? bone.defY : bone.defZ);
                                v = enginePosToUi(bone, ch, v, def);
                            }
                            out.putKey(bone, ch, tick, v, ease);
                        }
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
