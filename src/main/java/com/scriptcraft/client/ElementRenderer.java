package com.scriptcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.scriptcraft.screen.BarDirection;
import com.scriptcraft.screen.BarElement;
import com.scriptcraft.screen.ImageElement;
import com.scriptcraft.screen.Layout;
import com.scriptcraft.screen.ListElement;
import com.scriptcraft.screen.ScreenElement;
import com.scriptcraft.screen.SlotElement;
import com.scriptcraft.screen.TextElement;
import com.scriptcraft.script.runtime.ItemResolver;
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
 * Общая отрисовка элементов (текст, картинка, слот, полоса) для экранов из скриптов и для HUD.
 * Кнопки сюда не входят — это виджеты, их рисует {@link ScriptScreen}.
 *
 * Слои: вызывающий код сортирует элементы {@link #sortedByLayer} и рисует по порядку. GUI рисуется с проверкой
 * глубины, а предмет в слоте рисуется "выше" (z ≈ 150..232), поэтому после каждого слота с предметом
 * следующие элементы поднимаются на {@link #ITEM_Z_STEP} — иначе картинка с бОльшим слоем оказалась бы под предметом.
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

    /** Нужно ли поднять следующие элементы по z (слот рисует предмет). */
    public static boolean drawsItem(ScreenElement e) {
        return e instanceof SlotElement s && (s.content() == SlotElement.Content.ITEM || s.content() == SlotElement.Content.HOTBAR);
    }

    /**
     * Рисует один элемент (кроме кнопок).
     * @return предмет под курсором (для подсказки на экране) или ItemStack.EMPTY
     */
    public static ItemStack render(GuiGraphics g, Font font, ScreenElement e, int screenW, int screenH, int mouseX, int mouseY) {
        return render(g, font, e, screenW, screenH, mouseX, mouseY, 0);
    }

    /**
     * @param listScroll сдвиг списка в пикселях (колёсико на open screen). Для HUD всегда 0.
     */
    public static ItemStack render(GuiGraphics g, Font font, ScreenElement e, int screenW, int screenH,
                                   int mouseX, int mouseY, int listScroll) {
        if (com.scriptcraft.screen.ScreenMeta.isHidden(e)) {
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
            renderList(g, font, list, screenW, screenH, mouseX, mouseY, listScroll);
        }
        return ItemStack.EMPTY;
    }

    /**
     * Хитбокс ЛКМ. null — мимо. Пустая строка — попали, но команда не задана (клик всё равно съедается у списка).
     * Кнопки сюда не входят: их нажимает виджет.
     */
    @Nullable
    public static String clickAction(Font font, ScreenElement e, int screenW, int screenH, double mouseX, double mouseY, int listScroll) {
        if (com.scriptcraft.screen.ScreenMeta.isHidden(e)) {
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
        if (e instanceof ListElement list) {
            return listAction(font, list, screenW, screenH, mouseX, mouseY, listScroll);
        }
        return null;
    }

    /** Хитбокс элемента для тултипа (без требования action). */
    public static boolean isHovered(Font font, ScreenElement e, int screenW, int screenH, double mouseX, double mouseY) {
        if (com.scriptcraft.screen.ScreenMeta.isHidden(e)) {
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
        String headerText = com.scriptcraft.screen.ScreenMeta.rawAction(list.header());
        int header = headerText.isEmpty() ? 0 : ListElement.ROW;
        int view = list.horizontal() ? box.w : Math.max(0, box.h - header);
        int content = list.horizontal()
                ? list.rows().size() * rowSpan(font, list)
                : list.rows().size() * ListElement.ROW;
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
        float scale = com.scriptcraft.screen.ScreenMeta.elementScale(text);
        float hScale = com.scriptcraft.screen.ScreenMeta.elementHoverScale(text);
        float rot = com.scriptcraft.screen.ScreenMeta.elementRotation(text);
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
        float scale = com.scriptcraft.screen.ScreenMeta.elementScale(image);
        float hScale = com.scriptcraft.screen.ScreenMeta.elementHoverScale(image);
        float rot = com.scriptcraft.screen.ScreenMeta.elementRotation(image);
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
        int frames = com.scriptcraft.screen.ScreenMeta.spriteFrames(image);
        setTint(l.color());
        if (frames > 1) {
            int fps = com.scriptcraft.screen.ScreenMeta.spriteFps(image);
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

    /**
     * Прямоугольник / карточка без текстуры с поддержкой:
     * градиента (вертикальный/горизонтальный), рамки, скруглённых углов (radius), тени (shadow) и blur-эффекта (blur).
     */
    private static void renderBox(GuiGraphics g, String spec, int color1, int x, int y, int w, int h) {
        // Формат: box:<gradHex>:<borderHex>:<v|h>:<borderW>[:<radius>:<shadow 0|1>:<blur 0|1>]
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

        // 1. Мягкая тень под панелью
        if (shadow) {
            fillRoundedSolid(g, x + 3, y + 4, w, h, radius, 0x26000000);
            fillRoundedSolid(g, x + 2, y + 3, w, h, radius, 0x30000000);
            fillRoundedSolid(g, x + 1, y + 2, w, h, radius, 0x28000000);
        }
        // 2. Эффект матового стекла / blur-подложки
        if (blur) {
            fillRoundedSolid(g, x - 1, y - 1, w + 2, h + 2, radius + 1, 0x280B1220);
            fillRoundedSolid(g, x, y, w, h, radius, 0x440F172A);
        }

        // 3. Основная заливка (сплошная или градиент, скруглённая или обычная)
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

        // Тонкий стеклянный блик сверху для blur-панелей
        if (blur && w > 6 && h > 4) {
            int inset = Math.max(1, radius / 2);
            g.fill(x + inset, y + 1, x + w - inset, y + 2, 0x26FFFFFF);
        }

        // 4. Рамка
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
        // Центральная прямоугольная часть одним вызовом fill
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

    /** Оттенок ARGB для следующих blit; альфа 0 считается непрозрачным (цвет без альфы). */
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

        // 1. Фон: картинка или цвет.
        ResourceLocation bg = texture(bar.backgroundTexture());
        if (bg != null) {
            setTint(0xFFFFFFFF);
            g.blit(bg, x, y, w, h, 0f, 0f, texW, texH, texW, texH);
            resetTint();
        } else if ((bar.backgroundColor() >>> 24) != 0) {
            g.fill(x, y, x + w, y + h, bar.backgroundColor());
        }

        // 2. Заполнение: прямоугольник нужного размера с нужной стороны.
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
                // Картинка заполнения НЕ растягивается, а обрезается: берём ту же часть текстуры, что и часть полосы.
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

        // 3. Надпись по центру.
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
            // Простая "ванильная" рамка слота.
            g.fill(x, y, x + w, y + h, 0xFF373737);
            g.fill(x + 1, y + 1, x + w, y + h, 0xFFFFFFFF);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF8B8B8B);
        }

        ItemStack stack = ItemStack.EMPTY;
        boolean selected = false;
        switch (slot.content()) {
            case ITEM -> stack = com.scriptcraft.script.runtime.ItemStacks.tryCreate(slot.value(), Math.max(1, slot.count()));
            case HOTBAR -> {
                // Настоящий предмет из хотбара игрока (номер 1..9) — обновляется сам, без пакетов.
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
                    // texW = uWidth -> рисуется вся картинка, вписанная в слот (с отступом 1 пиксель).
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
            // Предмет 16x16 масштабируется под размер слота (18 = как в инвентаре).
            float scale = w / (float) SLOT_SIZE;
            g.pose().pushPose();
            g.pose().translate((float) x, (float) y, 0f);
            g.pose().scale(scale, scale, 1f);
            g.renderItem(stack, 1, 1);
            g.renderItemDecorations(font, stack, 1, 1);
            g.pose().popPose();
        }
        if (selected) {
            // Выбранный слот хотбара: рамка 2 пикселя цветом элемента (по умолчанию белая).
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

    /** "15" -> 15; "health" -> здоровье своего игрока; иначе 0. */
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

    /** Значение источника у своего игрока (имена — {@link BarElement#SOURCES}); null — неизвестное имя. */
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

    /**
     * Клиентские плейсхолдеры (считаются каждый кадр): %health%, %food%, ... (все источники),
     * а у полосы ещё %value%, %max%, %percent%. Остальные %...% остаются как есть.
     */
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

    /** 20.0 -> "20", 17.5 -> "17.5", 0.333 -> "0.3". */
    static String formatNumber(double d) {
        double r = Math.round(d * 10) / 10.0;
        if (r == Math.rint(r)) {
            return Long.toString((long) r);
        }
        return String.format(Locale.ROOT, "%.1f", r);
    }

    private static void renderList(GuiGraphics g, Font font, ListElement list, int sw, int sh, int mouseX, int mouseY, int scroll) {
        Box box = listBox(list, sw, sh);
        String headerText = com.scriptcraft.screen.ScreenMeta.rawAction(list.header());
        int header = headerText.isEmpty() ? 0 : ListElement.ROW;
        g.fill(box.x, box.y, box.x + box.w, box.y + box.h, 0xC0101010);
        g.fill(box.x, box.y, box.x + box.w, box.y + 1, 0xFF3A3A3A);
        if (header > 0) {
            g.drawString(font, codes(headerText), box.x + 4, box.y + 3, 0xFFFFD700, false);
        }
        int span = rowSpan(font, list);
        g.enableScissor(box.x, box.y + header, box.x + box.w, box.y + box.h);
        for (int i = 0; i < list.rows().size(); i++) {
            ListElement.Row row = list.rows().get(i);
            int rx = list.horizontal() ? box.x - scroll + i * span : box.x;
            int ry = list.horizontal() ? box.y + header : box.y + header - scroll + i * ListElement.ROW;
            int rw = list.horizontal() ? span : box.w;
            if (list.select() && mouseX >= rx && mouseX < rx + rw && mouseY >= ry && mouseY < ry + ListElement.ROW
                    && mouseY >= box.y + header && mouseY < box.y + box.h) {
                g.fill(rx, ry, rx + rw, ry + ListElement.ROW, 0x40FFFFFF);
            }
            g.drawString(font, codes(row.text()), rx + 4, ry + 3, 0xFFFFFFFF, false);
        }
        g.disableScissor();

        // Визуальный ползунок (скроллбар-слайдер) списка, если контент длиннее видимой области и не отключён (no scrollbar / no slider)
        int maxScroll = listMaxScroll(font, list, sw, sh);
        if (maxScroll > 0 && !list.horizontal() && com.scriptcraft.screen.ScreenMeta.hasListScrollbar(list)) {
            int trackX = box.x + box.w - 4;
            int trackY = box.y + header + 1;
            int trackH = Math.max(8, box.h - header - 2);
            int contentH = list.rows().size() * ListElement.ROW;
            int thumbH = Math.max(12, Math.min(trackH, Math.round((float) trackH * trackH / Math.max(1, contentH))));
            int thumbY = trackY + Math.round(((float) Math.max(0, Math.min(maxScroll, scroll)) / maxScroll) * (trackH - thumbH));
            boolean hoverBar = mouseX >= trackX - 2 && mouseX <= box.x + box.w && mouseY >= trackY && mouseY <= trackY + trackH;
            g.fill(trackX, trackY, trackX + 3, trackY + trackH, 0x66000000);
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, hoverBar ? 0xFF60A5FA : 0xFFB0BAC9);
        }
    }

    /** Возвращает новый scroll (0..maxScroll), если клик/перетаскивание попало в полосу прокрутки списка, иначе -1. */
    public static int listScrollbarDrag(Font font, ListElement list, int sw, int sh, double mouseX, double mouseY, boolean ignoreX) {
        if (list.horizontal() || !com.scriptcraft.screen.ScreenMeta.hasListScrollbar(list)) {
            return -1;
        }
        int maxScroll = listMaxScroll(font, list, sw, sh);
        if (maxScroll <= 0) {
            return -1;
        }
        Box box = listBox(list, sw, sh);
        String headerText = com.scriptcraft.screen.ScreenMeta.rawAction(list.header());
        int header = headerText.isEmpty() ? 0 : ListElement.ROW;
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
    private static String listAction(Font font, ListElement list, int sw, int sh, double mouseX, double mouseY, int scroll) {
        Box box = listBox(list, sw, sh);
        if (mouseX < box.x || mouseY < box.y || mouseX >= box.x + box.w || mouseY >= box.y + box.h) {
            return null;
        }
        String headerText = com.scriptcraft.screen.ScreenMeta.rawAction(list.header());
        int header = headerText.isEmpty() ? 0 : ListElement.ROW;
        if (mouseY < box.y + header) {
            return "";
        }
        int span = rowSpan(font, list);
        int index = list.horizontal()
                ? (int) ((mouseX - box.x + scroll) / span)
                : (int) ((mouseY - box.y - header + scroll) / ListElement.ROW);
        if (index < 0 || index >= list.rows().size()) {
            return "";
        }
        return list.rows().get(index).action();
    }

    private static int rowSpan(Font font, ListElement list) {
        if (!list.horizontal()) {
            return ListElement.ROW;
        }
        int span = 48;
        for (ListElement.Row row : list.rows()) {
            span = Math.max(span, font.width(codes(row.text())) + 8);
        }
        return span;
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

    /** null — курсор мимо. Пустая строка — попали в элемент без команды. */
    @Nullable
    private static String hit(String action, Box box, double mouseX, double mouseY) {
        if (com.scriptcraft.screen.ScreenMeta.rawAction(action).isEmpty()) {
            return null;
        }
        if (mouseX < box.x || mouseY < box.y || mouseX >= box.x + box.w || mouseY >= box.y + box.h) {
            return null;
        }
        return action;
    }

    /** &a в строке списка -> §a, как в чате. */
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
            // Картинка, докачанная после первой попытки отрисовки, подхватится сама — без перезахода.
            ClientResources.ensureGuiTexture(id);
        }
        return id;
    }

    /** Сброс кэша предметов: после новой таблицы привязок example:ruby может указывать на другой слот. */
    public static void clearItemCache() {
        ITEMS.clear();
    }

    @Nullable
    private static Item item(String id) {
        // Тот же поиск предмета, что и на сервере: "diamond" -> minecraft:diamond, иначе scriptcraft:<id>.
        return ITEMS.computeIfAbsent(id, ItemResolver::resolve).orElse(null);
    }
}
