package com.scriptcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.scriptcraft.screen.BarDirection;
import com.scriptcraft.screen.BarElement;
import com.scriptcraft.screen.EntityElement;
import com.scriptcraft.screen.ImageElement;
import com.scriptcraft.screen.Layout;
import com.scriptcraft.screen.ListElement;
import com.scriptcraft.screen.ScreenElement;
import com.scriptcraft.screen.ScreenMeta;
import com.scriptcraft.screen.SlotElement;
import com.scriptcraft.screen.TextElement;
import com.scriptcraft.script.runtime.ItemResolver;
import com.scriptcraft.script.runtime.ItemStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Общая отрисовка элементов (текст, картинка, прямоугольник, слот, полоса, список, 3D-сущность/игрок)
 * для экранов из скриптов ({@link ScriptScreen}) и для HUD ({@link ClientHud}).
 */
@OnlyIn(Dist.CLIENT)
public final class ElementRenderer {
    public static final int SLOT_SIZE = SlotElement.SIZE;
    public static final float ITEM_Z_STEP = 250f;
    /** Предел подъёма по z (видимая глубина GUI намного больше, но оставляем запас для подсказок и чата). */
    public static final float MAX_Z = 8000f;

    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-z_]+)%");
    /** Кэши, чтобы не разбирать строки каждый кадр. */
    private static final Map<String, Optional<ResourceLocation>> TEXTURES = new HashMap<>();
    private static final Map<String, Optional<Item>> ITEMS = new HashMap<>();

    private ElementRenderer() {
    }

    /** Стабильная сортировка по слою: при равном слое сохраняется порядок объявления. */
    public static <T> List<T> sortedByLayer(Collection<T> items, ToIntFunction<T> layer) {
        List<T> copy = new ArrayList<>(items);
        copy.sort(Comparator.comparingInt(layer));
        return copy;
    }

    /** Нужно ли поднять следующие элементы по z (слот с предметом, 3D-сущность или список с предметами). */
    public static boolean drawsItem(ScreenElement e) {
        if (e instanceof EntityElement) {
            return true;
        }
        if (e instanceof SlotElement s && (s.content() == SlotElement.Content.ITEM || s.content() == SlotElement.Content.HOTBAR)) {
            return true;
        }
        if (e instanceof ListElement list) {
            for (ListElement.Row r : list.rows()) {
                if (!r.icon().isEmpty() && !isTextureIcon(r.icon())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static ItemStack render(GuiGraphics g, Font font, ScreenElement e, int screenW, int screenH, int mouseX, int mouseY) {
        return render(g, font, e, screenW, screenH, mouseX, mouseY, 0);
    }

    public static ItemStack render(GuiGraphics g, Font font, ScreenElement e, int screenW, int screenH,
                                   int mouseX, int mouseY, int listScroll) {
        if (ScreenMeta.isHidden(e)) {
            return ItemStack.EMPTY;
        }
        if (e instanceof TextElement text) {
            renderText(g, font, text, screenW, screenH, mouseX, mouseY);
        } else if (e instanceof ImageElement image) {
            renderImage(g, image, screenW, screenH, mouseX, mouseY);
        } else if (e instanceof BarElement bar) {
            renderBar(g, font, bar, screenW, screenH);
        } else if (e instanceof SlotElement slot) {
            return renderSlot(g, font, slot, screenW, screenH, mouseX, mouseY);
        } else if (e instanceof ListElement list) {
            return renderList(g, font, list, screenW, screenH, mouseX, mouseY, listScroll);
        } else if (e instanceof EntityElement entity) {
            GuiEntityRenderer.render(g, entity, screenW, screenH, mouseX, mouseY);
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    public static String clickAction(Font font, ScreenElement e, int screenW, int screenH, double mouseX, double mouseY, int listScroll) {
        if (ScreenMeta.isHidden(e)) {
            return null;
        }
        if (ScreenMeta.isDisabled(e) && !(e instanceof ListElement)) {
            return null;
        }
        if (e instanceof TextElement text) {
            return hit(text.action(), textBounds(font, text, screenW, screenH), mouseX, mouseY);
        }
        if (e instanceof ImageElement image) {
            Layout l = image.layout();
            int w = l.width() > 0 ? l.width() : 16;
            int h = l.height() > 0 ? l.height() : 16;
            return hit(image.action(), box(l, screenW, screenH, w, h), mouseX, mouseY);
        }
        if (e instanceof SlotElement slot) {
            Layout l = slot.layout();
            int w = l.width() > 0 ? l.width() : SLOT_SIZE;
            int h = l.height() > 0 ? l.height() : SLOT_SIZE;
            return hit(slot.action(), box(l, screenW, screenH, w, h), mouseX, mouseY);
        }
        if (e instanceof EntityElement entity) {
            Layout l = entity.layout();
            int w = l.width() > 0 ? l.width() : EntityElement.DEFAULT_WIDTH;
            int h = l.height() > 0 ? l.height() : EntityElement.DEFAULT_HEIGHT;
            return hit(entity.action(), box(l, screenW, screenH, w, h), mouseX, mouseY);
        }
        if (e instanceof ListElement list) {
            return listAction(font, list, screenW, screenH, mouseX, mouseY, listScroll);
        }
        return null;
    }

    /** Тултип под курсором с учётом индивидуальных тултипов строк внутри {@link ListElement}. */
    public static String hoveredTooltip(Font font, ScreenElement e, int screenW, int screenH,
                                        double mouseX, double mouseY, int listScroll) {
        if (!isHovered(font, e, screenW, screenH, mouseX, mouseY)) {
            return "";
        }
        if (e instanceof ListElement list) {
            ListElement.Row row = hoveredListRow(font, list, screenW, screenH, mouseX, mouseY, listScroll);
            if (row != null && !row.tooltip().isEmpty()) {
                return row.tooltip();
            }
        }
        return ScreenMeta.elementTooltip(e);
    }

    /** Хитбокс элемента для тултипа (без требования action). */
    public static boolean isHovered(Font font, ScreenElement e, int screenW, int screenH, double mouseX, double mouseY) {
        if (ScreenMeta.isHidden(e)) {
            return false;
        }
        Layout l = e.layout();
        Box b;
        if (e instanceof TextElement text) {
            b = textBounds(font, text, screenW, screenH);
        } else if (e instanceof ImageElement) {
            b = box(l, screenW, screenH, l.width() > 0 ? l.width() : 16, l.height() > 0 ? l.height() : 16);
        } else if (e instanceof SlotElement) {
            int w = l.width() > 0 ? l.width() : SLOT_SIZE;
            b = box(l, screenW, screenH, w, l.height() > 0 ? l.height() : w);
        } else if (e instanceof BarElement) {
            b = box(l, screenW, screenH, l.width() > 0 ? l.width() : BarElement.DEFAULT_WIDTH, l.height() > 0 ? l.height() : BarElement.DEFAULT_HEIGHT);
        } else if (e instanceof ListElement list) {
            b = listBox(list, screenW, screenH);
        } else if (e instanceof EntityElement) {
            b = box(l, screenW, screenH, l.width() > 0 ? l.width() : EntityElement.DEFAULT_WIDTH,
                    l.height() > 0 ? l.height() : EntityElement.DEFAULT_HEIGHT);
        } else if (e instanceof com.scriptcraft.screen.ButtonElement) {
            b = box(l, screenW, screenH, l.width() > 0 ? l.width() : 100, l.height() > 0 ? l.height() : 20);
        } else if (e instanceof com.scriptcraft.screen.InputElement) {
            b = box(l, screenW, screenH, l.width() > 0 ? l.width() : com.scriptcraft.screen.InputElement.DEFAULT_WIDTH,
                    l.height() > 0 ? l.height() : com.scriptcraft.screen.InputElement.DEFAULT_HEIGHT);
        } else if (e instanceof com.scriptcraft.screen.SliderElement) {
            b = box(l, screenW, screenH, l.width() > 0 ? l.width() : com.scriptcraft.screen.SliderElement.DEFAULT_WIDTH,
                    l.height() > 0 ? l.height() : com.scriptcraft.screen.SliderElement.DEFAULT_HEIGHT);
        } else if (e instanceof com.scriptcraft.screen.ToggleElement) {
            b = box(l, screenW, screenH, l.width() > 0 ? l.width() : com.scriptcraft.screen.ToggleElement.DEFAULT_WIDTH,
                    l.height() > 0 ? l.height() : com.scriptcraft.screen.ToggleElement.DEFAULT_HEIGHT);
        } else {
            return false;
        }
        return mouseX >= b.x && mouseY >= b.y && mouseX < b.x + b.w && mouseY < b.y + b.h;
    }

    /** На сколько пикселей список вообще можно сдвинуть. */
    public static int listMaxScroll(Font font, ListElement list, int screenW, int screenH) {
        Box box = listBox(list, screenW, screenH);
        int header = listHeaderHeight(list);
        int view = list.horizontal() ? box.w : Math.max(0, box.h - header);
        int content = listTotalSpan(font, list, box.w, Math.max(0, box.h - header));
        return Math.max(0, content - view);
    }

    // ------------------------------------------------------------------ текст и картинки

    private static void renderText(GuiGraphics g, Font font, TextElement text, int sw, int sh, int mouseX, int mouseY) {
        Layout l = text.layout();
        String s = placeholders(text.text(), null, 0, 0).replace("%nl%", "\n").replace("%newline%", "\n");
        String[] lines = s.indexOf('\n') >= 0 ? s.split("\n", -1) : null;
        int w = 0;
        int lineStep = font.lineHeight + 2;
        int h;
        if (lines == null) {
            w = font.width(s);
            h = font.lineHeight;
        } else {
            for (String ln : lines) {
                w = Math.max(w, font.width(ln));
            }
            h = lines.length * lineStep - 2;
        }
        int x = l.anchor().resolveX(sw, w, l.x());
        int y = l.anchor().resolveY(sh, h, l.y());
        float scale = ScreenMeta.elementScale(text);
        float hScale = ScreenMeta.elementHoverScale(text);
        float rot = ScreenMeta.elementRotation(text);
        if (hScale != 1f && mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h) {
            scale *= hScale;
        }
        boolean xform = Math.abs(scale - 1f) > 0.001f || Math.abs(rot) > 0.01f;
        if (xform) {
            float cx = x + w / 2f;
            float cy = y + h / 2f;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0f);
            if (Math.abs(rot) > 0.01f) {
                g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rot));
            }
            if (Math.abs(scale - 1f) > 0.001f) {
                g.pose().scale(scale, scale, 1f);
            }
            g.pose().translate(-cx, -cy, 0f);
        }
        if (lines == null) {
            g.drawString(font, s, x, y, l.color(), text.shadow());
        } else {
            for (int i = 0; i < lines.length; i++) {
                int lw = font.width(lines[i]);
                int lx = l.anchor().resolveX(sw, lw, l.x());
                g.drawString(font, lines[i], lx, y + i * lineStep, l.color(), text.shadow());
            }
        }
        if (xform) {
            g.pose().popPose();
        }
    }

    private static void renderImage(GuiGraphics g, ImageElement image, int sw, int sh, int mouseX, int mouseY) {
        Layout l = image.layout();
        int w = l.width() > 0 ? l.width() : 16;
        int h = l.height() > 0 ? l.height() : 16;
        int x = l.anchor().resolveX(sw, w, l.x());
        int y = l.anchor().resolveY(sh, h, l.y());
        float scale = ScreenMeta.elementScale(image);
        float hScale = ScreenMeta.elementHoverScale(image);
        float rot = ScreenMeta.elementRotation(image);
        if (hScale != 1f && mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h) {
            scale *= hScale;
        }
        boolean xform = Math.abs(scale - 1f) > 0.001f || Math.abs(rot) > 0.01f;
        if (xform) {
            float cx = x + w / 2f;
            float cy = y + h / 2f;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0f);
            if (Math.abs(rot) > 0.01f) {
                g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rot));
            }
            if (Math.abs(scale - 1f) > 0.001f) {
                g.pose().scale(scale, scale, 1f);
            }
            g.pose().translate(-cx, -cy, 0f);
        }
        if (image.texture() != null && image.texture().startsWith("box:")) {
            renderBox(g, image.texture(), l.color(), x, y, w, h);
            if (xform) g.pose().popPose();
            return;
        }
        ResourceLocation texture = texture(image.texture());
        if (texture == null) {
            if (xform) g.pose().popPose();
            return;
        }
        int texW = image.textureWidth() > 0 ? image.textureWidth() : w;
        int texH = image.textureHeight() > 0 ? image.textureHeight() : h;
        int frames = ScreenMeta.spriteFrames(image);
        setTint(l.color());
        if (frames > 1) {
            int fps = ScreenMeta.spriteFps(image);
            int frame = (int) ((System.currentTimeMillis() / Math.max(1L, 1000L / fps)) % frames);
            if (texW >= texH) {
                int fw = Math.max(1, texW / frames);
                g.blit(texture, x, y, w, h, frame * (float) fw, 0f, fw, texH, texW, texH);
            } else {
                int fh = Math.max(1, texH / frames);
                g.blit(texture, x, y, w, h, 0f, frame * (float) fh, texW, fh, texW, texH);
            }
        } else {
            g.blit(texture, x, y, w, h, 0f, 0f, texW, texH, texW, texH);
        }
        resetTint();
        if (xform) {
            g.pose().popPose();
        }
    }

    private static void renderBox(GuiGraphics g, String spec, int color1, int x, int y, int w, int h) {
        String[] parts = spec.substring(4).split(":", -1);
        int color2 = parts.length > 0 ? parseHexArgb(parts[0]) : 0;
        int border = parts.length > 1 ? parseHexArgb(parts[1]) : 0;
        boolean horiz = parts.length > 2 && "h".equals(parts[2]);
        int bw = 1;
        if (parts.length > 3 && !parts[3].isEmpty()) {
            try { bw = Math.max(0, Math.min(16, Integer.parseInt(parts[3]))); } catch (NumberFormatException ignored) {}
        }
        int radius = 0;
        if (parts.length > 4 && !parts[4].isEmpty()) {
            try { radius = Math.max(0, Math.min(Math.min(w, h) / 2, Integer.parseInt(parts[4]))); } catch (NumberFormatException ignored) {}
        }
        boolean shadow = parts.length > 5 && "1".equals(parts[5]);
        boolean blur = parts.length > 6 && "1".equals(parts[6]);

        if (shadow) {
            fillRoundedSolid(g, x + 3, y + 4, w, h, radius, 0x26000000);
            fillRoundedSolid(g, x + 2, y + 3, w, h, radius, 0x30000000);
            fillRoundedSolid(g, x + 1, y + 2, w, h, radius, 0x28000000);
        }
        if (blur) {
            fillRoundedSolid(g, x - 1, y - 1, w + 2, h + 2, radius + 1, 0x280B1220);
            fillRoundedSolid(g, x, y, w, h, radius, 0x440F172A);
        }
        if (radius <= 0) {
            if (color2 == 0) {
                g.fill(x, y, x + w, y + h, color1);
            } else if (!horiz) {
                g.fillGradient(x, y, x + w, y + h, color1, color2);
            } else {
                fillHorizontalGradient(g, x, y, w, h, color1, color2);
            }
        } else {
            fillRounded(g, x, y, w, h, radius, color1, color2, horiz);
        }
        if (blur && w > 6 && h > 4) {
            int inset = Math.max(1, radius / 2);
            g.fill(x + inset, y + 1, x + w - inset, y + 2, 0x26FFFFFF);
        }
        if (border != 0 && bw > 0) {
            if (radius <= 0) {
                g.fill(x, y, x + w, y + bw, border);
                g.fill(x, y + h - bw, x + w, y + h, border);
                g.fill(x, y + bw, x + bw, y + h - bw, border);
                g.fill(x + w - bw, y + bw, x + w, y + h - bw, border);
            } else {
                drawRoundedBorder(g, x, y, w, h, radius, bw, border);
            }
        }
    }

    private static int lerpArgb(int c1, int c2, float t) {
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >>> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return (Math.round(a1 + (a2 - a1) * t) << 24)
                | (Math.round(r1 + (r2 - r1) * t) << 16)
                | (Math.round(g1 + (g2 - g1) * t) << 8)
                | Math.round(b1 + (b2 - b1) * t);
    }

    private static int cornerInset(int dyFromEdge, int r) {
        double d = r - dyFromEdge - 0.5;
        if (d <= 0) return 0;
        return Math.max(0, (int) Math.floor(r - Math.sqrt(Math.max(0.0, r * r - d * d))));
    }

    private static void fillHorizontalGradient(GuiGraphics g, int x, int y, int w, int h, int c1, int c2) {
        int step = w > 180 ? 2 : 1;
        int denom = Math.max(1, w - 1);
        for (int dx = 0; dx < w; dx += step) {
            g.fill(x + dx, y, Math.min(x + w, x + dx + step), y + h, lerpArgb(c1, c2, (float) dx / denom));
        }
    }

    private static void fillRoundedSolid(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        int cr = Math.min(r, Math.min(w, h) / 2);
        if (h > cr * 2) {
            g.fill(x, y + cr, x + w, y + h - cr, color);
        }
        for (int row = 0; row < cr; row++) {
            int inset = cornerInset(row, cr);
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
            g.fill(x + inset, y + h - 1 - row, x + w - inset, y + h - row, color);
        }
    }

    private static void fillRounded(GuiGraphics g, int x, int y, int w, int h, int r, int c1, int c2, boolean horiz) {
        if (c2 == 0) {
            fillRoundedSolid(g, x, y, w, h, r, c1);
            return;
        }
        int cr = Math.min(r, Math.min(w, h) / 2);
        if (!horiz) {
            int denom = Math.max(1, h - 1);
            for (int row = 0; row < h; row++) {
                int inset = 0;
                if (row < cr) inset = cornerInset(row, cr);
                else if (row >= h - cr) inset = cornerInset(h - 1 - row, cr);
                g.fill(x + inset, y + row, x + w - inset, y + row + 1, lerpArgb(c1, c2, (float) row / denom));
            }
        } else {
            int step = w > 160 ? 2 : 1;
            int denom = Math.max(1, w - 1);
            for (int dx = 0; dx < w; dx += step) {
                int col = lerpArgb(c1, c2, (float) dx / denom);
                int yInset = 0;
                if (dx < cr) yInset = cornerInset(dx, cr);
                else if (dx >= w - cr) yInset = cornerInset(w - 1 - dx, cr);
                g.fill(x + dx, y + yInset, Math.min(x + w, x + dx + step), y + h - yInset, col);
            }
        }
    }

    private static void drawRoundedBorder(GuiGraphics g, int x, int y, int w, int h, int r, int bw, int border) {
        int cr = Math.min(r, Math.min(w, h) / 2);
        for (int row = 0; row < h; row++) {
            int inset = 0;
            if (row < cr) inset = cornerInset(row, cr);
            else if (row >= h - cr) inset = cornerInset(h - 1 - row, cr);
            if (row < bw || row >= h - bw) {
                g.fill(x + inset, y + row, x + w - inset, y + row + 1, border);
            } else {
                int leftW = Math.max(bw, (row < cr ? cornerInset(Math.max(0, row - 1), cr) - inset + bw : bw));
                g.fill(x + inset, y + row, Math.min(x + w - inset, x + inset + leftW), y + row + 1, border);
                g.fill(Math.max(x + inset, x + w - inset - leftW), y + row, x + w - inset, y + row + 1, border);
            }
        }
    }

    private static int parseHexArgb(String raw) {
        if (raw == null || raw.isEmpty() || "0".equals(raw)) {
            return 0;
        }
        try {
            return (int) Long.parseLong(raw, 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void setTint(int c) {
        float a = ((c >>> 24) & 0xFF) / 255f;
        if (a == 0f) {
            a = 1f;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, a);
    }

    private static void resetTint() {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------ полоса прогресса

    private static void renderBar(GuiGraphics g, Font font, BarElement bar, int sw, int sh) {
        Layout l = bar.layout();
        int w = l.width() > 0 ? l.width() : BarElement.DEFAULT_WIDTH;
        int h = l.height() > 0 ? l.height() : BarElement.DEFAULT_HEIGHT;
        int x = l.anchor().resolveX(sw, w, l.x());
        int y = l.anchor().resolveY(sh, h, l.y());
        double value = number(bar.value());
        double max = number(bar.max());
        double progress = max > 0 ? Math.max(0, Math.min(1, value / max)) : 0;
        int texW = bar.textureWidth() > 0 ? bar.textureWidth() : w;
        int texH = bar.textureHeight() > 0 ? bar.textureHeight() : h;

        ResourceLocation bg = texture(bar.backgroundTexture());
        if (bg != null) {
            setTint(0xFFFFFFFF);
            g.blit(bg, x, y, w, h, 0f, 0f, texW, texH, texW, texH);
            resetTint();
        } else if ((bar.backgroundColor() >>> 24) != 0) {
            g.fill(x, y, x + w, y + h, bar.backgroundColor());
        }

        int fx = x, fy = y, fw = w, fh = h;
        BarDirection dir = bar.direction();
        if (dir.vertical()) {
            fh = (int) Math.round(h * progress);
            if (dir == BarDirection.BOTTOM_TO_TOP) {
                fy = y + h - fh;
            }
        } else {
            fw = (int) Math.round(w * progress);
            if (dir == BarDirection.RIGHT_TO_LEFT) {
                fx = x + w - fw;
            }
        }
        if (fw > 0 && fh > 0) {
            ResourceLocation fill = texture(bar.fillTexture());
            if (fill != null) {
                float u = (float) (fx - x) * texW / w;
                float v = (float) (fy - y) * texH / h;
                int uw = Math.max(1, Math.round((float) fw * texW / w));
                int vh = Math.max(1, Math.round((float) fh * texH / h));
                setTint(0xFFFFFFFF);
                g.blit(fill, fx, fy, fw, fh, u, v, uw, vh, texW, texH);
                resetTint();
            } else {
                g.fill(fx, fy, fx + fw, fy + fh, l.color());
            }
        }

        if (!bar.text().isEmpty()) {
            String s = placeholders(bar.text(), bar, value, max);
            int tx = x + (w - font.width(s)) / 2;
            int ty = y + (h - 8) / 2;
            g.drawString(font, s, tx, ty, 0xFFFFFFFF, bar.shadow());
        }
    }

    // ------------------------------------------------------------------ слоты

    private static ItemStack renderSlot(GuiGraphics g, Font font, SlotElement slot, int sw, int sh, int mouseX, int mouseY) {
        Layout l = slot.layout();
        int w = l.width() > 0 ? l.width() : SLOT_SIZE;
        int h = l.height() > 0 ? l.height() : w;
        int x = l.anchor().resolveX(sw, w, l.x());
        int y = l.anchor().resolveY(sh, h, l.y());

        if (slot.frame()) {
            g.fill(x, y, x + w, y + h, 0xFF373737);
            g.fill(x + 1, y + 1, x + w, y + h, 0xFFFFFFFF);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF8B8B8B);
        }

        ItemStack stack = ItemStack.EMPTY;
        boolean selected = false;
        switch (slot.content()) {
            case ITEM -> stack = ItemStacks.tryCreate(slot.value(), Math.max(1, slot.count()));
            case HOTBAR -> {
                LocalPlayer p = Minecraft.getInstance().player;
                int index = slot.count() - 1;
                if (p != null && index >= 0 && index < 9) {
                    stack = p.getInventory().getItem(index);
                    selected = p.getInventory().selected == index;
                }
            }
            case IMAGE -> {
                ResourceLocation tex = texture(slot.value());
                if (tex != null) {
                    setTint(0xFFFFFFFF);
                    g.blit(tex, x + 1, y + 1, w - 2, h - 2, 0f, 0f, 16, 16, 16, 16);
                    resetTint();
                }
            }
            case TEXT -> {
                String s = placeholders(slot.value(), null, 0, 0);
                g.drawString(font, s, x + (w - font.width(s)) / 2, y + (h - 8) / 2, l.color(), true);
            }
        }

        if (!stack.isEmpty()) {
            float scale = w / (float) SLOT_SIZE;
            g.pose().pushPose();
            g.pose().translate((float) x, (float) y, 0f);
            g.pose().scale(scale, scale, 1f);
            g.renderItem(stack, 1, 1);
            g.renderItemDecorations(font, stack, 1, 1);
            g.pose().popPose();
        }
        if (selected) {
            int c = (l.color() >>> 24) == 0 ? (l.color() | 0xFF000000) : l.color();
            g.fill(x - 1, y - 1, x + w + 1, y + 1, c);
            g.fill(x - 1, y + h - 1, x + w + 1, y + h + 1, c);
            g.fill(x - 1, y + 1, x + 1, y + h - 1, c);
            g.fill(x + w - 1, y + 1, x + w + 1, y + h - 1, c);
        }

        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        return hover ? stack : ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------ значения и плейсхолдеры

    static double number(String raw) {
        String s = raw.strip();
        if (s.isEmpty()) {
            return 0;
        }
        try {
            double d = Double.parseDouble(s);
            return Double.isFinite(d) ? d : 0;
        } catch (NumberFormatException e) {
            Double src = source(s.toLowerCase(Locale.ROOT));
            return src == null ? 0 : src;
        }
    }

    @Nullable
    static Double source(String name) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            return BarElement.isSource(name) ? 0.0 : null;
        }
        return switch (name) {
            case "health" -> (double) p.getHealth();
            case "max_health" -> (double) p.getMaxHealth();
            case "absorption" -> (double) p.getAbsorptionAmount();
            case "food" -> (double) p.getFoodData().getFoodLevel();
            case "saturation" -> (double) p.getFoodData().getSaturationLevel();
            case "armor" -> (double) p.getArmorValue();
            case "air" -> (double) Math.max(0, p.getAirSupply());
            case "max_air" -> (double) p.getMaxAirSupply();
            case "xp" -> (double) p.experienceProgress;
            case "level" -> (double) p.experienceLevel;
            default -> null;
        };
    }

    static String placeholders(String text, @Nullable BarElement bar, double value, double max) {
        if (text.indexOf('%') < 0) {
            return text;
        }
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String replacement;
            if (bar != null && key.equals("value")) {
                replacement = formatNumber(value);
            } else if (bar != null && key.equals("max")) {
                replacement = formatNumber(max);
            } else if (bar != null && key.equals("percent")) {
                replacement = formatNumber(max > 0 ? Math.round(Math.max(0, Math.min(1, value / max)) * 100) : 0);
            } else {
                Double src = source(key);
                replacement = src == null ? m.group(0) : formatNumber(src);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static String formatNumber(double d) {
        double r = Math.round(d * 10) / 10.0;
        if (r == Math.rint(r)) {
            return Long.toString((long) r);
        }
        return String.format(Locale.ROOT, "%.1f", r);
    }

    // ------------------------------------------------------------------ продвинутый список (ListElement)

    private static int listHeaderHeight(ListElement list) {
        String headerText = ScreenMeta.rawAction(list.header());
        if (headerText.isEmpty()) return 0;
        return Math.max(ListElement.ROW, list.rowHeight());
    }

    private static boolean isTextureIcon(String icon) {
        return icon.endsWith(".png") || icon.contains("/textures/") || icon.startsWith("textures/");
    }

    private static int itemHeightOf(ListElement list, ListElement.Row row, int availViewH) {
        if (list.horizontal()) {
            return row.customHeight() > 0 ? row.customHeight() : availViewH;
        }
        if (row.customHeight() > 0) {
            return Math.max(4, row.customHeight());
        }
        if (row.kind() == ListElement.RowKind.SEPARATOR) {
            return row.text().isEmpty() ? 7 : Math.max(12, list.rowHeight());
        }
        if (row.kind() == ListElement.RowKind.EMPTY) {
            return Math.max(4, list.rowHeight() / 2);
        }
        return Math.max(10, list.rowHeight());
    }

    private static int itemWidthOf(Font font, ListElement list, ListElement.Row row, int listW) {
        if (!list.horizontal()) {
            return row.customWidth() > 0 ? Math.min(listW, Math.max(16, row.customWidth())) : listW;
        }
        if (row.customWidth() > 0) {
            return Math.max(8, row.customWidth());
        }
        if (row.kind() == ListElement.RowKind.SEPARATOR) {
            return row.text().isEmpty() ? 9 : Math.max(24, font.width(codes(row.text())) + 12);
        }
        if (row.kind() == ListElement.RowKind.EMPTY) {
            return 16;
        }
        float sc = Math.max(0.5f, list.rowScale());
        int w = Math.round(font.width(codes(row.text())) * sc) + 12;
        if (!row.head().isEmpty() || !row.icon().isEmpty()) w += 16;
        if (!row.badge().isEmpty()) w += font.width(codes(row.badge())) + 10;
        if (!row.rightText().isEmpty()) w += Math.round(font.width(codes(row.rightText())) * sc) + 8;
        if (!row.buttonLabel().isEmpty()) w += font.width(codes(row.buttonLabel())) + 12;
        if (row.barProgress() >= 0f) w += 36;
        return Math.max(36, w);
    }

    private static int listTotalSpan(Font font, ListElement list, int listW, int availViewH) {
        int n = list.rows().size();
        if (n == 0) return 0;
        int gap = Math.max(0, list.spacing());
        int sum = 0;
        for (int i = 0; i < n; i++) {
            ListElement.Row r = list.rows().get(i);
            sum += list.horizontal() ? itemWidthOf(font, list, r, listW) : itemHeightOf(list, r, availViewH);
            if (i + 1 < n) sum += gap;
        }
        return sum;
    }

    private static int alignedRowX(int boxX, int boxW, int rw, byte align) {
        if (rw >= boxW) return boxX;
        return switch (align) {
            case ListElement.ALIGN_CENTER -> boxX + (boxW - rw) / 2;
            case ListElement.ALIGN_RIGHT -> boxX + boxW - rw;
            default -> boxX;
        };
    }

    private static ItemStack renderList(GuiGraphics g, Font font, ListElement list,
                                        int sw, int sh, int mouseX, int mouseY, int scroll) {
        Box box = listBox(list, sw, sh);
        boolean listDisabled = ScreenMeta.isDisabled(list);
        String headerText = ScreenMeta.rawAction(list.header());
        int headerH = listHeaderHeight(list);
        int viewY = box.y + headerH;
        int viewH = Math.max(0, box.h - headerH);

        // 1. Фон и рамка списка
        if ((list.bgColor() >>> 24) != 0) {
            g.fill(box.x, box.y, box.x + box.w, box.y + box.h, list.bgColor());
        }
        if ((list.borderColor() >>> 24) != 0) {
            int bc = listDisabled ? 0xFF2B2D35 : list.borderColor();
            g.fill(box.x, box.y, box.x + box.w, box.y + 1, bc);
            g.fill(box.x, box.y + box.h - 1, box.x + box.w, box.y + box.h, bc);
            g.fill(box.x, box.y, box.x + 1, box.y + box.h, bc);
            g.fill(box.x + box.w - 1, box.y, box.x + box.w, box.y + box.h, bc);
        }

        // 2. Заголовок с выравниванием
        if (headerH > 0) {
            g.fill(box.x + 1, box.y + 1, box.x + box.w - 1, box.y + headerH, 0x44000000);
            g.fill(box.x + 1, box.y + headerH - 1, box.x + box.w - 1, box.y + headerH, 0x6645475A);
            String hdr = codes(headerText);
            int hw = font.width(hdr);
            int hx = switch (list.align()) {
                case ListElement.ALIGN_CENTER -> box.x + (box.w - hw) / 2;
                case ListElement.ALIGN_RIGHT -> box.x + box.w - hw - 5;
                default -> box.x + 5;
            };
            int hy = box.y + (headerH - font.lineHeight) / 2;
            g.drawString(font, hdr, hx, hy, listDisabled ? 0xFF8A8F9E : 0xFFFFD700, false);
        }

        ItemStack hoveredInlineStack = ItemStack.EMPTY;
        int maxScroll = listMaxScroll(font, list, sw, sh);
        boolean hasScrollbar = maxScroll > 0 && !list.horizontal() && ScreenMeta.hasListScrollbar(list);
        int innerW = hasScrollbar ? Math.max(12, box.w - 6) : box.w;

        g.enableScissor(box.x + 1, viewY, box.x + box.w - 1, box.y + box.h - 1);

        // 3. Пустой список — надпись emptyText
        if (list.rows().isEmpty()) {
            if (!list.emptyText().isEmpty()) {
                String emp = codes(list.emptyText());
                int ex = box.x + (box.w - font.width(emp)) / 2;
                int ey = viewY + (viewH - font.lineHeight) / 2;
                g.drawString(font, emp, ex, ey, 0xFF8A96AA, false);
            }
        } else {
            int[] colWidths = parseColWidths(list.columnWidths());
            int gap = Math.max(0, list.spacing());
            int cursor = -scroll;
            int accentCol = (list.layout().color() == 0 || list.layout().color() == 0xFFFFFFFF)
                    ? 0xFF38BDF8 : list.layout().color();

            for (int i = 0; i < list.rows().size(); i++) {
                ListElement.Row row = list.rows().get(i);
                int rw = itemWidthOf(font, list, row, innerW);
                int rh = itemHeightOf(list, row, viewH);
                byte effAlign = row.align() == ListElement.ALIGN_INHERIT ? list.align() : row.align();

                int rx = list.horizontal() ? (box.x + cursor) : alignedRowX(box.x, innerW, rw, effAlign);
                int ry = list.horizontal() ? (viewY + (viewH - rh) / 2) : (viewY + cursor);
                cursor += (list.horizontal() ? rw : rh) + gap;

                if (list.horizontal() ? (rx + rw < box.x || rx > box.x + box.w) : (ry + rh < viewY || ry > box.y + box.h)) {
                    continue;
                }

                if (row.kind() == ListElement.RowKind.EMPTY) {
                    continue;
                }

                if (row.kind() == ListElement.RowKind.SEPARATOR) {
                    renderSeparatorRow(g, font, list, row, rx, ry, rw, rh, effAlign);
                    continue;
                }

                boolean rowDisabled = listDisabled || row.state() == ListElement.STATE_DISABLED;
                boolean rowActive = row.state() == ListElement.STATE_ACTIVE;
                boolean rowHovered = !rowDisabled
                        && mouseX >= rx && mouseX < rx + rw && mouseY >= ry && mouseY < ry + rh
                        && mouseX >= box.x && mouseX < box.x + innerW && mouseY >= viewY && mouseY < box.y + box.h;

                // Фон строки (кастомный, активный или hover)
                if (row.rowColor() != 0) {
                    g.fill(rx, ry, rx + rw, ry + rh, row.rowColor());
                }
                if (rowActive) {
                    g.fill(rx, ry, rx + rw, ry + rh, (accentCol & 0x00FFFFFF) | 0x38000000);
                    if (list.horizontal()) {
                        g.fill(rx, ry + rh - 2, rx + rw, ry + rh, accentCol | 0xFF000000);
                    } else {
                        g.fill(rx, ry + 1, rx + 2, ry + rh - 1, accentCol | 0xFF000000);
                    }
                } else if (list.select() && rowHovered) {
                    g.fill(rx, ry, rx + rw, ry + rh, 0x33FFFFFF);
                }

                ItemStack rowStack = renderRichRowContent(g, font, list, row, rx, ry, rw, rh,
                        effAlign, rowDisabled, colWidths, mouseX, mouseY);
                if (!rowStack.isEmpty()) {
                    hoveredInlineStack = rowStack;
                }
            }
        }
        g.disableScissor();

        // Полупрозрачная вуаль неактивного списка
        if (listDisabled) {
            g.fill(box.x + 1, box.y + 1, box.x + box.w - 1, box.y + box.h - 1, 0x48090B10);
        }

        // 4. Скроллбар (вертикальный или горизонтальный)
        if (maxScroll > 0 && ScreenMeta.hasListScrollbar(list)) {
            if (!list.horizontal()) {
                int trackX = box.x + box.w - 4;
                int trackY = viewY + 1;
                int trackH = Math.max(8, viewH - 2);
                int contentH = viewH + maxScroll;
                int thumbH = Math.max(12, Math.min(trackH, Math.round((float) trackH * trackH / Math.max(1, contentH))));
                int thumbY = trackY + Math.round(((float) Math.max(0, Math.min(maxScroll, scroll)) / maxScroll) * (trackH - thumbH));
                boolean hoverBar = mouseX >= trackX - 2 && mouseX <= box.x + box.w && mouseY >= trackY && mouseY <= trackY + trackH;
                g.fill(trackX, trackY, trackX + 3, trackY + trackH, 0x66000000);
                g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, hoverBar ? 0xFF60A5FA : 0xFFB0BAC9);
            } else {
                int trackX = box.x + 1;
                int trackY = box.y + box.h - 4;
                int trackW = Math.max(8, box.w - 2);
                int contentW = box.w + maxScroll;
                int thumbW = Math.max(12, Math.min(trackW, Math.round((float) trackW * trackW / Math.max(1, contentW))));
                int thumbX = trackX + Math.round(((float) Math.max(0, Math.min(maxScroll, scroll)) / maxScroll) * (trackW - thumbW));
                g.fill(trackX, trackY, trackX + trackW, trackY + 3, 0x66000000);
                g.fill(thumbX, trackY, thumbX + thumbW, trackY + 3, 0xFFB0BAC9);
            }
        }
        return hoveredInlineStack;
    }

    private static void renderSeparatorRow(GuiGraphics g, Font font, ListElement list, ListElement.Row row,
                                           int rx, int ry, int rw, int rh, byte align) {
        int lineCol = row.rowColor() != 0 ? row.rowColor() : 0x884B5563;
        if (list.horizontal()) {
            int cx = rx + rw / 2;
            g.fill(cx, ry + 2, cx + 1, ry + rh - 2, lineCol);
            return;
        }
        int midY = ry + rh / 2;
        if (row.text().isEmpty()) {
            g.fill(rx + 4, midY, rx + rw - 4, midY + 1, lineCol);
            return;
        }
        String lbl = codes(row.text());
        int lw = font.width(lbl);
        int ty = ry + (rh - font.lineHeight) / 2;
        if (align == ListElement.ALIGN_LEFT) {
            g.drawString(font, lbl, rx + 5, ty, 0xFF94A3B8, false);
            int lx = rx + 9 + lw;
            if (lx < rx + rw - 4) g.fill(lx, midY, rx + rw - 4, midY + 1, lineCol);
        } else if (align == ListElement.ALIGN_RIGHT) {
            int tx = rx + rw - lw - 5;
            if (rx + 4 < tx - 4) g.fill(rx + 4, midY, tx - 4, midY + 1, lineCol);
            g.drawString(font, lbl, tx, ty, 0xFF94A3B8, false);
        } else {
            int tx = rx + (rw - lw) / 2;
            if (rx + 4 < tx - 4) g.fill(rx + 4, midY, tx - 4, midY + 1, lineCol);
            if (tx + lw + 4 < rx + rw - 4) g.fill(tx + lw + 4, midY, rx + rw - 4, midY + 1, lineCol);
            g.drawString(font, lbl, tx, ty, 0xFF94A3B8, false);
        }
    }

    private static ItemStack renderRichRowContent(GuiGraphics g, Font font, ListElement list, ListElement.Row row,
                                                  int rx, int ry, int rw, int rh, byte align, boolean disabled,
                                                  int[] colWidths, int mouseX, int mouseY) {
        ItemStack hoveredStack = ItemStack.EMPTY;
        int leftX = rx + 4;
        int rightX = rx + rw - 4;
        float sc = Math.max(0.5f, Math.min(3.0f, list.rowScale()));
        int textColor = disabled ? 0xFF7A8191
                : ((list.layout().color() == 0) ? 0xFFFFFFFF : list.layout().color());

        // 1. Справа: вложенная мини-кнопка (buttonLabel)
        if (!row.buttonLabel().isEmpty()) {
            String bl = codes(row.buttonLabel());
            int btnW = font.width(bl) + 8;
            int btnH = Math.min(rh - 2, Math.max(11, font.lineHeight + 3));
            int btnX = rightX - btnW;
            int btnY = ry + (rh - btnH) / 2;
            boolean btnHot = !disabled && mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            g.fill(btnX, btnY, btnX + btnW, btnY + btnH, disabled ? 0x8820242E : (btnHot ? 0xFF2563EB : 0xCC1E293B));
            int bBorder = disabled ? 0xFF3F4452 : (btnHot ? 0xFF60A5FA : 0xFF475569);
            g.fill(btnX, btnY, btnX + btnW, btnY + 1, bBorder);
            g.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, bBorder);
            g.fill(btnX, btnY, btnX + 1, btnY + btnH, bBorder);
            g.fill(btnX + btnW - 1, btnY, btnX + btnW, btnY + btnH, bBorder);
            g.drawString(font, bl, btnX + 4, btnY + (btnH - font.lineHeight) / 2 + 1, disabled ? 0xFF888E9E : 0xFFFFFFFF, false);
            rightX = btnX - 4;
        }

        // 2. Справа: дополнительный текст / цена (rightText)
        if (!row.rightText().isEmpty() && rightX > leftX + 16) {
            String rt = codes(row.rightText());
            int rtw = Math.round(font.width(rt) * sc);
            int rtx = rightX - rtw;
            drawScaledRowString(g, font, rt, rtx, ry, rh, sc, disabled ? 0xFF7A8191 : 0xFFE2E8F0);
            rightX = rtx - 5;
        }

        // 3. Справа: встроенная мини-полоса прогресса (barProgress 0..1)
        if (row.barProgress() >= 0f && rightX > leftX + 34) {
            int barW = Math.min(44, Math.max(22, rw / 4));
            int barH = Math.min(6, Math.max(4, rh - 6));
            int barX = rightX - barW;
            int barY = ry + (rh - barH) / 2;
            g.fill(barX, barY, barX + barW, barY + barH, 0xAA0B0F17);
            int fillW = Math.round(barW * Math.max(0f, Math.min(1f, row.barProgress())));
            if (fillW > 0) {
                int bc = disabled ? 0xFF525B6E : (row.barColor() | 0xFF000000);
                g.fill(barX, barY, barX + fillW, barY + barH, bc);
            }
            rightX = barX - 5;
        }

        // 4. Слева: 2D-голова скина игрока (head)
        if (!row.head().isEmpty()) {
            int hs = Math.min(rh - 2, Math.max(10, Math.min(20, rh - 3)));
            int hy = ry + (rh - hs) / 2;
            GuiEntityRenderer.renderHead2D(g, row.head(), leftX, hy, hs, true);
            leftX += hs + 4;
        }

        // 5. Слева: иконка предмета или текстуры (icon)
        if (!row.icon().isEmpty()) {
            int isz = Math.min(rh - 2, Math.max(10, Math.min(20, rh - 2)));
            int iy = ry + (rh - isz) / 2;
            if (isTextureIcon(row.icon())) {
                ResourceLocation tex = texture(row.icon());
                if (tex != null) {
                    setTint(disabled ? 0x88FFFFFF : 0xFFFFFFFF);
                    g.blit(tex, leftX, iy, isz, isz, 0f, 0f, isz, isz, isz, isz);
                    resetTint();
                }
            } else {
                ItemStack stack = ItemStacks.tryCreate(row.icon(), Math.max(1, row.iconCount()));
                if (!stack.isEmpty()) {
                    float itemSc = isz / 16f;
                    g.pose().pushPose();
                    g.pose().translate(leftX, iy, 0f);
                    g.pose().scale(itemSc, itemSc, 1f);
                    g.renderItem(stack, 0, 0);
                    if (row.iconCount() > 1) {
                        g.renderItemDecorations(font, stack, 0, 0);
                    }
                    g.pose().popPose();
                    if (mouseX >= leftX && mouseX < leftX + isz && mouseY >= iy && mouseY < iy + isz) {
                        hoveredStack = stack;
                    }
                }
            }
            leftX += isz + 4;
        }

        // 6. Основной текст (либо таблица колонок через || / | при заданном columns) + бейдж
        String main = codes(row.text());
        if (colWidths.length > 0 && (main.contains("||") || main.contains("|"))) {
            String[] cells = main.contains("||") ? main.split("\\|\\|", -1) : main.split("\\|", -1);
            int cx = leftX;
            for (int c = 0; c < cells.length && cx < rightX; c++) {
                int cw = c < colWidths.length ? colWidths[c] : Math.max(24, (rightX - cx) / Math.max(1, cells.length - c));
                String cell = font.plainSubstrByWidth(cells[c].strip(), Math.max(8, (int) ((cw - 3) / sc)));
                drawScaledRowString(g, font, cell, cx, ry, rh, sc, textColor);
                cx += cw;
            }
            leftX = cx;
        } else {
            int badgeTotalW = 0;
            String bTxt = "";
            if (!row.badge().isEmpty()) {
                bTxt = codes(row.badge());
                badgeTotalW = font.width(bTxt) + 8;
            }
            int availW = Math.max(8, rightX - leftX - badgeTotalW);
            int textPxW = Math.round(font.width(main) * sc);
            String clipped = textPxW > availW
                    ? font.plainSubstrByWidth(main, Math.max(6, (int) (availW / sc)))
                    : main;
            int drawnW = Math.round(font.width(clipped) * sc);
            int groupW = drawnW + badgeTotalW;
            int tx = switch (align) {
                case ListElement.ALIGN_CENTER -> leftX + Math.max(0, (rightX - leftX - groupW) / 2);
                case ListElement.ALIGN_RIGHT -> Math.max(leftX, rightX - groupW);
                default -> leftX;
            };
            drawScaledRowString(g, font, clipped, tx, ry, rh, sc, textColor);
            if (badgeTotalW > 0 && tx + drawnW + 3 + badgeTotalW - 3 <= rightX + 2) {
                int bx = tx + drawnW + 4;
                int bh = Math.min(rh - 2, font.lineHeight + 2);
                int by = ry + (rh - bh) / 2;
                int bCol = disabled ? 0xFF475569 : (row.badgeColor() == 0 ? 0xFFF59E0B : row.badgeColor());
                g.fill(bx, by, bx + badgeTotalW - 4, by + bh, (bCol & 0x00FFFFFF) | 0x44000000);
                g.fill(bx, by, bx + badgeTotalW - 4, by + 1, (bCol & 0x00FFFFFF) | 0xCC000000);
                g.fill(bx, by + bh - 1, bx + badgeTotalW - 4, by + bh, (bCol & 0x00FFFFFF) | 0xCC000000);
                g.drawString(font, bTxt, bx + 2, by + (bh - font.lineHeight) / 2 + 1,
                        disabled ? 0xFF94A3B8 : ((bCol & 0x00FFFFFF) | 0xFF000000), false);
            }
        }
        return hoveredStack;
    }

    private static void drawScaledRowString(GuiGraphics g, Font font, String text, int x, int ry, int rh, float scale, int color) {
        if (Math.abs(scale - 1f) < 0.01f) {
            int ty = ry + (rh - font.lineHeight) / 2 + 1;
            g.drawString(font, text, x, ty, color, false);
        } else {
            float ty = ry + (rh - font.lineHeight * scale) * 0.5f;
            g.pose().pushPose();
            g.pose().translate(x, ty, 0f);
            g.pose().scale(scale, scale, 1f);
            g.drawString(font, text, 0, 0, color, false);
            g.pose().popPose();
        }
    }

    private static int[] parseColWidths(String spec) {
        if (spec == null || spec.isBlank()) return new int[0];
        String[] parts = spec.split("[,;\\s]+");
        List<Integer> list = new ArrayList<>(parts.length);
        for (String p : parts) {
            if (p.isEmpty()) continue;
            try {
                int v = Integer.parseInt(p.strip());
                if (v > 0) list.add(v);
            } catch (NumberFormatException ignored) {
            }
        }
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    /** Возвращает новый scroll (0..maxScroll), если клик/перетаскивание попало в полосу прокрутки списка, иначе -1. */
    public static int listScrollbarDrag(Font font, ListElement list, int sw, int sh, double mouseX, double mouseY, boolean ignoreX) {
        if (!ScreenMeta.hasListScrollbar(list)) {
            return -1;
        }
        int maxScroll = listMaxScroll(font, list, sw, sh);
        if (maxScroll <= 0) {
            return -1;
        }
        Box box = listBox(list, sw, sh);
        int header = listHeaderHeight(list);
        if (list.horizontal()) {
            int trackX = box.x + 1;
            int trackY = box.y + box.h - 7;
            int trackW = Math.max(8, box.w - 2);
            if (!ignoreX && (mouseY < trackY || mouseY > box.y + box.h || mouseX < trackX || mouseX > trackX + trackW)) {
                return -1;
            }
            double norm = Math.max(0.0, Math.min(1.0, (mouseX - trackX) / Math.max(1.0, trackW)));
            return (int) Math.round(norm * maxScroll);
        }
        int trackX = box.x + box.w - 7;
        int trackY = box.y + header + 1;
        int trackH = Math.max(8, box.h - header - 2);
        if (!ignoreX && (mouseX < trackX || mouseX > box.x + box.w || mouseY < trackY || mouseY > trackY + trackH)) {
            return -1;
        }
        double norm = Math.max(0.0, Math.min(1.0, (mouseY - trackY) / Math.max(1.0, trackH)));
        return (int) Math.round(norm * maxScroll);
    }

    @Nullable
    private static ListElement.Row hoveredListRow(Font font, ListElement list, int sw, int sh,
                                                  double mouseX, double mouseY, int scroll) {
        RowHit hit = findListRowHit(font, list, sw, sh, mouseX, mouseY, scroll);
        return hit == null ? null : hit.row;
    }

    private record RowHit(ListElement.Row row, int rx, int ry, int rw, int rh) {
    }

    @Nullable
    private static RowHit findListRowHit(Font font, ListElement list, int sw, int sh,
                                         double mouseX, double mouseY, int scroll) {
        Box box = listBox(list, sw, sh);
        if (mouseX < box.x || mouseY < box.y || mouseX >= box.x + box.w || mouseY >= box.y + box.h) {
            return null;
        }
        int header = listHeaderHeight(list);
        int viewY = box.y + header;
        int viewH = Math.max(0, box.h - header);
        if (mouseY < viewY) {
            return null;
        }
        int maxScroll = listMaxScroll(font, list, sw, sh);
        boolean hasScrollbar = maxScroll > 0 && !list.horizontal() && ScreenMeta.hasListScrollbar(list);
        int innerW = hasScrollbar ? Math.max(12, box.w - 6) : box.w;
        int gap = Math.max(0, list.spacing());
        int cursor = -scroll;
        for (int i = 0; i < list.rows().size(); i++) {
            ListElement.Row row = list.rows().get(i);
            int rw = itemWidthOf(font, list, row, innerW);
            int rh = itemHeightOf(list, row, viewH);
            byte effAlign = row.align() == ListElement.ALIGN_INHERIT ? list.align() : row.align();
            int rx = list.horizontal() ? (box.x + cursor) : alignedRowX(box.x, innerW, rw, effAlign);
            int ry = list.horizontal() ? (viewY + (viewH - rh) / 2) : (viewY + cursor);
            cursor += (list.horizontal() ? rw : rh) + gap;
            if (mouseX >= rx && mouseX < rx + rw && mouseY >= ry && mouseY < ry + rh) {
                return new RowHit(row, rx, ry, rw, rh);
            }
        }
        return null;
    }

    @Nullable
    private static String listAction(Font font, ListElement list, int sw, int sh, double mouseX, double mouseY, int scroll) {
        Box box = listBox(list, sw, sh);
        if (mouseX < box.x || mouseY < box.y || mouseX >= box.x + box.w || mouseY >= box.y + box.h) {
            return null;
        }
        if (ScreenMeta.isDisabled(list)) {
            return "";
        }
        RowHit hit = findListRowHit(font, list, sw, sh, mouseX, mouseY, scroll);
        if (hit == null || !hit.row.isInteractive()) {
            return "";
        }
        if (!hit.row.buttonLabel().isEmpty()) {
            String bl = codes(hit.row.buttonLabel());
            int btnW = font.width(bl) + 8;
            int btnH = Math.min(hit.rh - 2, Math.max(11, font.lineHeight + 3));
            int btnX = hit.rx + hit.rw - 4 - btnW;
            int btnY = hit.ry + (hit.rh - btnH) / 2;
            if (mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                return !ScreenMeta.rawAction(hit.row.buttonAction()).isEmpty()
                        ? hit.row.buttonAction()
                        : hit.row.action();
            }
        }
        return hit.row.action();
    }

    private static Box listBox(ListElement list, int sw, int sh) {
        Layout l = list.layout();
        int w = l.width() > 0 ? l.width() : 120;
        int h = l.height() > 0 ? l.height() : 140;
        return box(l, sw, sh, w, h);
    }

    private static Box textBounds(Font font, TextElement text, int sw, int sh) {
        Layout l = text.layout();
        String s = placeholders(text.text(), null, 0, 0);
        int w = l.width() > 0 ? l.width() : font.width(s);
        int h = l.height() > 0 ? l.height() : font.lineHeight;
        return box(l, sw, sh, w, h);
    }

    private static Box box(Layout l, int sw, int sh, int w, int h) {
        return new Box(l.anchor().resolveX(sw, w, l.x()), l.anchor().resolveY(sh, h, l.y()), Math.max(1, w), Math.max(1, h));
    }

    @Nullable
    private static String hit(String action, Box box, double mouseX, double mouseY) {
        if (ScreenMeta.rawAction(action).isEmpty()) {
            return null;
        }
        if (mouseX < box.x || mouseY < box.y || mouseX >= box.x + box.w || mouseY >= box.y + box.h) {
            return null;
        }
        return action;
    }

    private static String codes(String text) {
        return text.replace('&', '\u00a7');
    }

    private record Box(int x, int y, int w, int h) {
    }

    @Nullable
    private static ResourceLocation texture(String raw) {
        if (raw.isEmpty()) {
            return null;
        }
        ResourceLocation id = TEXTURES.computeIfAbsent(raw, r -> Optional.ofNullable(ResourceLocation.tryParse(r))).orElse(null);
        if (id != null) {
            ClientResources.ensureGuiTexture(id);
        }
        return id;
    }

    public static void clearItemCache() {
        ITEMS.clear();
    }

    @Nullable
    private static Item item(String id) {
        return ITEMS.computeIfAbsent(id, ItemResolver::resolve).orElse(null);
    }
}
