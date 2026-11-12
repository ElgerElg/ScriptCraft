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
        if (e instanceof TextElement text) {
            renderText(g, font, text, screenW, screenH);
        } else if (e instanceof ImageElement image) {
            renderImage(g, image, screenW, screenH);
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

    /** На сколько пикселей список вообще можно сдвинуть. */
    public static int listMaxScroll(Font font, ListElement list, int screenW, int screenH) {
        Box box = listBox(list, screenW, screenH);
        int header = list.header().isEmpty() ? 0 : ListElement.ROW;
        int view = list.horizontal() ? box.w : Math.max(0, box.h - header);
        int content = list.horizontal()
                ? list.rows().size() * rowSpan(font, list)
                : list.rows().size() * ListElement.ROW;
        return Math.max(0, content - view);
    }

    // ------------------------------------------------------------------ текст и картинки

    private static void renderText(GuiGraphics g, Font font, TextElement text, int sw, int sh) {
        Layout l = text.layout();
        String s = placeholders(text.text(), null, 0, 0);
        int w = font.width(s);
        int h = font.lineHeight;
        int x = l.anchor().resolveX(sw, w, l.x());
        int y = l.anchor().resolveY(sh, h, l.y());
        g.drawString(font, s, x, y, l.color(), text.shadow());
    }

    private static void renderImage(GuiGraphics g, ImageElement image, int sw, int sh) {
        ResourceLocation texture = texture(image.texture());
        if (texture == null) {
            return;
        }
        Layout l = image.layout();
        int w = l.width() > 0 ? l.width() : 16;
        int h = l.height() > 0 ? l.height() : 16;
        int x = l.anchor().resolveX(sw, w, l.x());
        int y = l.anchor().resolveY(sh, h, l.y());
        int texW = image.textureWidth() > 0 ? image.textureWidth() : w;
        int texH = image.textureHeight() > 0 ? image.textureHeight() : h;
        // Цвет элемента используется как оттенок картинки (белый = без изменений).
        setTint(l.color());
        // Рисуем всю текстуру (texW x texH), растянутую до w x h.
        g.blit(texture, x, y, w, h, 0f, 0f, texW, texH, texW, texH);
        resetTint();
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
        int header = list.header().isEmpty() ? 0 : ListElement.ROW;
        g.fill(box.x, box.y, box.x + box.w, box.y + box.h, 0xC0101010);
        g.fill(box.x, box.y, box.x + box.w, box.y + 1, 0xFF3A3A3A);
        if (header > 0) {
            g.drawString(font, codes(list.header()), box.x + 4, box.y + 3, 0xFFFFD700, false);
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
    }

    @Nullable
    private static String listAction(Font font, ListElement list, int sw, int sh, double mouseX, double mouseY, int scroll) {
        Box box = listBox(list, sw, sh);
        if (mouseX < box.x || mouseY < box.y || mouseX >= box.x + box.w || mouseY >= box.y + box.h) {
            return null;
        }
        int header = list.header().isEmpty() ? 0 : ListElement.ROW;
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
        if (action.isEmpty()) {
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
