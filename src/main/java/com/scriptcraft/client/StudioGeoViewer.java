package com.scriptcraft.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.scriptcraft.client.geo.GeoModelData;
import com.scriptcraft.client.geo.GeoRenderer;
import com.scriptcraft.lang.Messages;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Интерактивный 3D-превьюер моделей Blockbench / GeckoLib (.geo.json + .animation.json) прямо в Студии:
 * - свободное вращение 360°, панорамирование, зум колесом мыши;
 * - проигрывание и скраббинг любых анимаций из соседнего .animation.json (+ Molang);
 * - переключение светящегося слоя *_glowmask.png, инспектор костей/locators и генерация кода define!
 */
@OnlyIn(Dist.CLIENT)
public final class StudioGeoViewer {
    private static String parsedKey = "";
    private static GeoModelData.GeoModel currentModel = null;
    private static GeoModelData.AnimationFile currentAnims = null;
    private static List<String> clipNames = List.of();
    private static String selectedClip = "";
    private static String resolvedModelId = "";
    private static String parseError = null;

    private static float camYaw = 35f;
    private static float camPitch = 18f;
    private static float camZoom = 1.0f;
    private static float camPanY = 0f;
    private static boolean autoSpin = false;
    private static boolean showGlow = true;
    private static boolean playing = true;
    private static float animSpeed = 1.0f;
    private static float scrubSec = 0f;
    private static long lastFrameNs = System.nanoTime();

    private static int dragMode = 0; // 1 = orbit, 2 = pan, 3 = timeline
    private static int lastMx, lastMy;
    private static int boneScroll = 0;

    // Геометрия зон последнего кадра
    private static int vpX, vpY, vpW, vpH;
    private static int tlX, tlY, tlW;
    private static int playBtnX, playBtnY, spinBtnX, glowBtnX, resetBtnX, speedBtnX;
    private static int insEntX, insItemX, insArmorX, insBlockX, insY, insBtnW;
    private static final List<ClipHit> clipHits = new ArrayList<>();

    private record ClipHit(int x, int y, int w, int h, String name) {
    }

    private StudioGeoViewer() {
    }

    public static boolean isGeoOrAnimFile(String docName) {
        if (docName == null) return false;
        String low = docName.toLowerCase(Locale.ROOT);
        return low.endsWith(".geo.json") || low.endsWith(".animation.json")
                || low.contains("/geo/") || low.contains("/animations/");
    }

    private static void ensureParsed(ScriptDocument doc) {
        String key = doc.name + "#" + doc.editStamp;
        if (key.equals(parsedKey)) {
            return;
        }
        parsedKey = key;
        parseError = null;
        currentModel = null;
        currentAnims = null;
        clipNames = List.of();

        String rel = doc.name.startsWith("@") ? doc.name.substring(1) : doc.name;
        resolvedModelId = deriveSpec(rel);
        String text = doc.text();
        boolean isAnimDoc = rel.toLowerCase(Locale.ROOT).endsWith(".animation.json") || rel.contains("/animations/");

        try {
            if (isAnimDoc) {
                currentAnims = GeoModelData.parseAnimationJson(text);
                currentModel = GeoRenderer.resolveModel(resolvedModelId);
            } else {
                currentModel = GeoModelData.parseGeoJson(text);
                currentAnims = GeoRenderer.resolveAnimations(resolvedModelId);
            }
            if (currentAnims != null && !currentAnims.clips().isEmpty()) {
                clipNames = new ArrayList<>(currentAnims.clips().keySet());
                if (selectedClip.isEmpty() || !currentAnims.clips().containsKey(selectedClip)) {
                    selectedClip = clipNames.get(0);
                }
            } else {
                selectedClip = "";
            }
        } catch (Exception e) {
            parseError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    public static String deriveSpec(String relPath) {
        String p = relPath.replace('\\', '/');
        if (p.startsWith("resources/assets/")) {
            String rest = p.substring("resources/assets/".length());
            int sl = rest.indexOf('/');
            if (sl > 0) {
                String ns = rest.substring(0, sl);
                String sub = rest.substring(sl + 1);
                if (sub.startsWith("geo/")) sub = sub.substring(4);
                else if (sub.startsWith("animations/")) sub = sub.substring("animations/".length());
                if (sub.endsWith(".geo.json")) sub = sub.substring(0, sub.length() - ".geo.json".length());
                else if (sub.endsWith(".animation.json")) sub = sub.substring(0, sub.length() - ".animation.json".length());
                else if (sub.endsWith(".json")) sub = sub.substring(0, sub.length() - 5);
                return ns + ":" + sub;
            }
        }
        return "scriptcraft:model";
    }

    public static void render(GuiGraphics g, Font font, ScriptDocument doc,
                              int x, int y, int w, int h, int mx, int my) {
        ensureParsed(doc);
        long nowNs = System.nanoTime();
        float dt = Math.max(0.001f, Math.min(0.1f, (nowNs - lastFrameNs) * 1e-9f));
        lastFrameNs = nowNs;

        if (autoSpin && dragMode != 1) {
            camYaw = (camYaw + dt * 35f) % 360f;
        }

        GeoModelData.AnimationClip clip = (currentAnims != null && !selectedClip.isEmpty())
                ? currentAnims.clips().get(selectedClip) : null;
        float clipLen = clip != null && clip.lengthSec() > 0.01f ? clip.lengthSec() : 2.0f;
        if (playing) {
            scrubSec += dt * animSpeed;
            if (scrubSec > clipLen) {
                scrubSec = scrubSec % clipLen;
            }
        }

        int sideW = Math.min(200, Math.max(145, w / 3));
        vpX = x + 4;
        vpY = y + 4;
        vpW = Math.max(80, w - sideW - 12);
        vpH = Math.max(60, h - 54);

        // Фон 3D-вьюпорта
        g.fillGradient(vpX, vpY, vpX + vpW, vpY + vpH, 0xFF0A1019, 0xFF131D2E);
        border(g, vpX, vpY, vpW, vpH, 0xFF2B3B54);

        if (parseError != null) {
            g.drawString(font, "Geo JSON Error: " + parseError, vpX + 10, vpY + 12, 0xFFE56B6F, false);
        } else if (currentModel == null) {
            g.drawString(font, Messages.get("studio.geo.no_model"), vpX + 10, vpY + 12, 0xFF8A96AA, false);
        } else {
            ResourceLocation tex = GeoRenderer.resolveTexture(resolvedModelId, resolvedModelId, "entity");
            ResourceLocation glow = showGlow ? GeoRenderer.resolveGlowTexture(resolvedModelId, resolvedModelId, "entity") : null;

            g.enableScissor(vpX + 1, vpY + 1, vpX + vpW - 1, vpY + vpH - 1);
            renderModelInViewport(g, currentModel, clip, scrubSec, tex, glow);
            g.disableScissor();

            // Верхние мини-кнопки вьюпорта
            int bx = vpX + 6;
            int by = vpY + 6;
            spinBtnX = bx;
            drawPill(g, font, spinBtnX, by, 44, "↻ Spin", autoSpin, mx, my, 0xFFF0B85B);
            glowBtnX = spinBtnX + 48;
            boolean hasGlowFile = GeoRenderer.resolveGlowTexture(resolvedModelId, resolvedModelId, "entity") != null;
            drawPill(g, font, glowBtnX, by, 68, hasGlowFile ? "✦ Glow ON" : "✦ Glow", showGlow && hasGlowFile, mx, my, 0xFF4CC38A);
            resetBtnX = glowBtnX + 72;
            drawPill(g, font, resetBtnX, by, 48, "⟲ Cam", false, mx, my, 0xFF58A6FF);

            String hint = "LMB: Orbit · RMB: Pan · Scroll: Zoom";
            g.drawString(font, hint, vpX + 8, vpY + vpH - 12, 0x888A96AA, false);
        }

        // Нижняя панель таймлайна и клипов
        int botY = vpY + vpH + 4;
        g.fill(vpX, botY, vpX + vpW, y + h - 4, 0xF5121926);
        border(g, vpX, botY, vpW, y + h - 4 - botY, 0xFF28354B);

        playBtnX = vpX + 6;
        playBtnY = botY + 5;
        drawPill(g, font, playBtnX, playBtnY, 22, playing ? "⏸" : "▶", playing, mx, my, 0xFF4CC38A);

        speedBtnX = playBtnX + 26;
        String spdTxt = String.format(Locale.ROOT, "%.1fx", animSpeed);
        drawPill(g, font, speedBtnX, playBtnY, 32, spdTxt, animSpeed != 1.0f, mx, my, 0xFFF0B85B);

        tlX = speedBtnX + 38;
        tlY = botY + 8;
        int timeTxtW = 68;
        tlW = Math.max(40, vpW - (tlX - vpX) - timeTxtW - 8);
        g.fill(tlX, tlY, tlX + tlW, tlY + 6, 0xFF233044);
        float prog = clipLen > 0.001f ? Mth.clamp(scrubSec / clipLen, 0f, 1f) : 0f;
        g.fill(tlX, tlY, tlX + Math.round(prog * tlW), tlY + 6, 0xFF58A6FF);
        int kx = tlX + Math.min(tlW - 4, Math.max(0, Math.round(prog * tlW) - 2));
        g.fill(kx, tlY - 2, kx + 5, tlY + 8, 0xFFF0B85B);
        g.drawString(font, String.format(Locale.ROOT, "%.2f/%.2fs", scrubSec, clipLen),
                tlX + tlW + 6, botY + 7, 0xFF8A96AA, false);

        // Чипы выбора анимаций
        clipHits.clear();
        int chipX = vpX + 6;
        int chipY = botY + 23;
        if (clipNames.isEmpty()) {
            g.drawString(font, "No .animation.json clips", chipX, chipY + 2, 0xFF637085, false);
        } else {
            for (String cn : clipNames) {
                String shortLbl = cn.contains(".") ? cn.substring(cn.lastIndexOf('.') + 1) : cn;
                int cw = font.width(shortLbl) + 10;
                if (chipX + cw > vpX + vpW - 4) break;
                boolean sel = cn.equals(selectedClip);
                drawPill(g, font, chipX, chipY, cw, shortLbl, sel, mx, my, 0xFFF0B85B);
                clipHits.add(new ClipHit(chipX, chipY, cw, 14, cn));
                chipX += cw + 4;
            }
        }

        // Правая панель инспектора костей и генератора кода
        int rx = vpX + vpW + 4;
        int ry = y + 4;
        int rh = h - 8;
        g.fill(rx, ry, rx + sideW, ry + rh, 0xF5131B28);
        border(g, rx, ry, sideW, rh, 0xFF28354B);
        g.drawString(font, "◈ 3D Geo Inspector", rx + 8, ry + 6, 0xFFF0B85B, false);
        g.fill(rx + 1, ry + 18, rx + sideW - 1, ry + 19, 0xFF28354B);

        int iy = ry + 23;
        g.drawString(font, trim(font, resolvedModelId, sideW - 14), rx + 8, iy, 0xFF58A6FF, false);
        iy += 12;
        if (currentModel != null) {
            int cubeCnt = currentModel.bonesByName.values().stream().mapToInt(b -> b.cubes.size()).sum();
            String st = currentModel.bonesByName.size() + " bones · " + cubeCnt + " cubes · "
                    + currentModel.texWidth + "×" + currentModel.texHeight;
            g.drawString(font, trim(font, st, sideW - 14), rx + 8, iy, 0xFF8A96AA, false);
            iy += 14;

            // Кнопки генерации сниппета в скрипт (Entity / Item / Armor / Block)
            g.drawString(font, "→ " + Messages.get("studio.audio.insert") + ":", rx + 8, iy, 0xFFE8EDF5, false);
            iy += 11;
            insY = iy;
            insBtnW = (sideW - 20) / 2;
            insEntX = rx + 6;
            insItemX = insEntX + insBtnW + 6;
            drawPill(g, font, insEntX, insY, insBtnW, "+ Entity", false, mx, my, 0xFF4CC38A);
            drawPill(g, font, insItemX, insY, insBtnW, "+ Item", false, mx, my, 0xFF58A6FF);
            drawPill(g, font, insEntX, insY + 16, insBtnW, "+ Armor", false, mx, my, 0xFFF0B85B);
            drawPill(g, font, insItemX, insY + 16, insBtnW, "+ Block", false, mx, my, 0xFFC678DD);
            insArmorX = insEntX;
            insBlockX = insItemX;
            iy += 36;

            g.fill(rx + 4, iy, rx + sideW - 4, iy + 1, 0xFF28354B);
            iy += 5;
            g.drawString(font, "Bones & Locators:", rx + 8, iy, 0xFFF0B85B, false);
            iy += 12;

            int listBottom = ry + rh - 4;
            g.enableScissor(rx + 2, iy, rx + sideW - 2, listBottom);
            int rowY = iy - boneScroll;
            for (GeoModelData.Bone bone : currentModel.bonesByName.values()) {
                if (rowY + 12 >= iy && rowY <= listBottom) {
                    String bTxt = "• " + bone.name + " (" + bone.cubes.size() + ")";
                    g.drawString(font, trim(font, bTxt, sideW - 14), rx + 8, rowY, 0xFFE8EDF5, false);
                }
                rowY += 12;
                for (String loc : bone.locators.keySet()) {
                    if (rowY + 12 >= iy && rowY <= listBottom) {
                        g.drawString(font, trim(font, "  ✦ " + loc, sideW - 14), rx + 8, rowY, 0xFF4CC38A, false);
                    }
                    rowY += 12;
                }
            }
            g.disableScissor();
        }
    }

    private static void renderModelInViewport(GuiGraphics g, GeoModelData.GeoModel model,
                                              GeoModelData.AnimationClip clip, float timeSec,
                                              ResourceLocation tex, ResourceLocation glow) {
        float h = Math.max(1.0f, model.visibleBoundsHeight);
        float baseScale = Math.min(vpW * 0.42f, (vpH * 0.62f) / Math.max(1.0f, h * 0.7f)) * camZoom;
        float cx = vpX + vpW * 0.5f;
        float footY = vpY + vpH * 0.80f + camPanY;

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, footY, 220.0f);
        pose.mulPoseMatrix(new Matrix4f().scaling(baseScale, -baseScale, baseScale));
        pose.mulPose(Axis.XP.rotationDegrees(camPitch));
        pose.mulPose(Axis.YP.rotationDegrees(camYaw));

        Lighting.setupForEntityInInventory();
        RenderSystem.enableDepthTest();
        GeoModelData.MolangVars vars = new GeoModelData.MolangVars(timeSec, timeSec, 0f, 0f, 0f);
        GeoRenderer.renderModel(model, clip, timeSec, vars, pose, g.bufferSource(),
                tex, glow, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f, null);
        g.flush();
        pose.popPose();

        Lighting.setupForFlatItems();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    public static boolean mouseClicked(int mx, int my, int button) {
        lastMx = mx;
        lastMy = my;
        if (hover(mx, my, spinBtnX, vpY + 6, 44, 14)) { autoSpin = !autoSpin; return true; }
        if (hover(mx, my, glowBtnX, vpY + 6, 68, 14)) { showGlow = !showGlow; return true; }
        if (hover(mx, my, resetBtnX, vpY + 6, 48, 14)) {
            camYaw = 35f; camPitch = 18f; camZoom = 1.0f; camPanY = 0f;
            return true;
        }
        if (hover(mx, my, playBtnX, playBtnY, 22, 14)) { playing = !playing; return true; }
        if (hover(mx, my, speedBtnX, playBtnY, 32, 14)) {
            animSpeed = animSpeed < 0.9f ? 1.0f : (animSpeed < 1.5f ? 2.0f : 0.5f);
            return true;
        }
        if (hover(mx, my, tlX, tlY - 4, tlW, 14)) {
            dragMode = 3;
            scrubToMouse(mx);
            return true;
        }
        for (ClipHit ch : clipHits) {
            if (hover(mx, my, ch.x, ch.y, ch.w, ch.h)) {
                selectedClip = ch.name;
                scrubSec = 0f;
                playing = true;
                return true;
            }
        }
        if (insBtnW > 0) {
            if (hover(mx, my, insEntX, insY, insBtnW, 14)) { insertDefineSnippet("entity"); return true; }
            if (hover(mx, my, insItemX, insY, insBtnW, 14)) { insertDefineSnippet("item"); return true; }
            if (hover(mx, my, insArmorX, insY + 16, insBtnW, 14)) { insertDefineSnippet("armor"); return true; }
            if (hover(mx, my, insBlockX, insY + 16, insBtnW, 14)) { insertDefineSnippet("block"); return true; }
        }
        if (hover(mx, my, vpX, vpY, vpW, vpH)) {
            dragMode = button == 1 ? 2 : 1;
            return true;
        }
        return false;
    }

    public static boolean mouseDragged(int mx, int my, int button) {
        int dx = mx - lastMx;
        int dy = my - lastMy;
        lastMx = mx;
        lastMy = my;
        if (dragMode == 1) {
            camYaw = (camYaw + dx * 0.85f) % 360f;
            camPitch = Mth.clamp(camPitch + dy * 0.65f, -85f, 85f);
            return true;
        }
        if (dragMode == 2) {
            camPanY = Mth.clamp(camPanY + dy, -160f, 160f);
            return true;
        }
        if (dragMode == 3) {
            scrubToMouse(mx);
            return true;
        }
        return false;
    }

    public static void mouseReleased() {
        dragMode = 0;
    }

    public static boolean mouseScrolled(int mx, int my, double delta) {
        if (hover(mx, my, vpX, vpY, vpW, vpH)) {
            camZoom = Mth.clamp(camZoom + (float) delta * 0.12f, 0.25f, 4.0f);
            return true;
        }
        boneScroll = Math.max(0, boneScroll + (delta < 0 ? 18 : -18));
        return true;
    }

    private static void scrubToMouse(int mx) {
        GeoModelData.AnimationClip clip = (currentAnims != null && !selectedClip.isEmpty())
                ? currentAnims.clips().get(selectedClip) : null;
        float len = clip != null && clip.lengthSec() > 0.01f ? clip.lengthSec() : 2.0f;
        float t = Mth.clamp((mx - tlX) / (float) Math.max(1, tlW), 0f, 1f);
        scrubSec = t * len;
        playing = false;
    }

    private static void insertDefineSnippet(String kind) {
        String spec = resolvedModelId;
        String snippet = switch (kind) {
            case "item" -> "define item " + spec + ":\n"
                    + "\tname: \"&b3D Item\"\n"
                    + "\tmodel: \"" + spec + "\"\n"
                    + "\ttexture: \"" + spec + "\"\n"
                    + "\tidle_animation: \"idle\"\n";
            case "armor" -> "define armor " + spec + ":\n"
                    + "\tname: \"&d3D Helmet\"\n"
                    + "\tslot: helmet\n"
                    + "\tdefense: 4\n"
                    + "\tmodel: \"" + spec + "\"\n"
                    + "\ttexture: \"" + spec + "\"\n";
            case "block" -> "define block " + spec + ":\n"
                    + "\tname: \"&e3D Block\"\n"
                    + "\tfacing: horizontal\n"
                    + "\tmodel: \"" + spec + "\"\n"
                    + "\ttexture: \"" + spec + "\"\n"
                    + "\tidle_animation: \"idle\"\n";
            default -> "define entity " + spec + ":\n"
                    + "\tname: \"&63D Guardian\"\n"
                    + "\tai: hostile\n"
                    + "\thealth: 80\n"
                    + "\tdamage: 8\n"
                    + "\tmodel: \"" + spec + "\"\n"
                    + "\ttexture: \"" + spec + "\"\n"
                    + "\tidle_animation: \"idle\"\n"
                    + "\twalk_animation: \"walk\"\n"
                    + "\tattack_animation: \"attack\"\n";
        };
        StudioAudioPlayer.insertIntoLastScriptOrClipboard(snippet);
    }

    private static void drawPill(GuiGraphics g, Font font, int x, int y, int w, String label,
                                 boolean active, int mx, int my, int accent) {
        boolean hot = hover(mx, my, x, y, w, 14);
        g.fill(x, y, x + w, y + 14, active ? 0xFF1F3248 : (hot ? 0xFF27344A : 0xFF1A2232));
        border(g, x, y, w, 14, active || hot ? accent : 0xFF2B364A);
        String shown = trim(font, label, w - 4);
        g.drawString(font, shown, x + (w - font.width(shown)) / 2, y + 3, active || hot ? accent : 0xFFE8EDF5, false);
    }

    private static String trim(Font font, String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        return font.plainSubstrByWidth(s, Math.max(8, maxW - 6)) + "..";
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private static boolean hover(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
