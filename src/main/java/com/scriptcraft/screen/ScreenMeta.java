package com.scriptcraft.screen;

import org.jetbrains.annotations.Nullable;

/**
 * Упаковка дополнительных свойств элемента экрана (id, keep open, горячая клавиша, скрытость)
 * внутрь существующих строковых полей элемента, чтобы сохранить 100% совместимость сетевого протокола.
 *
 * Формат: "\u0001id\u0002keep(0/1)\u0002key\u0002hidden(0/1)\u0001rawAction"
 */
public record ScreenMeta(String id, boolean keepOpen, String hotkey, boolean hidden, String rawAction) {
    private static final char MARK = '\u0001';
    private static final char SEP = '\u0002';

    public static String pack(@Nullable String id, boolean keepOpen, @Nullable String hotkey, boolean hidden, @Nullable String rawAction) {
        String cleanId = id == null ? "" : id.strip();
        String cleanKey = hotkey == null ? "" : hotkey.strip().toLowerCase(java.util.Locale.ROOT);
        String cleanAct = rawAction == null ? "" : rawAction;
        if (cleanId.isEmpty() && !keepOpen && cleanKey.isEmpty() && !hidden) {
            return cleanAct;
        }
        return MARK + cleanId + SEP + (keepOpen ? '1' : '0') + SEP + cleanKey + SEP + (hidden ? '1' : '0') + MARK + cleanAct;
    }

    public static ScreenMeta unpack(@Nullable String packed) {
        if (packed == null || packed.isEmpty()) {
            return new ScreenMeta("", false, "", false, "");
        }
        if (packed.charAt(0) != MARK) {
            return new ScreenMeta("", false, "", false, packed);
        }
        int end = packed.indexOf(MARK, 1);
        if (end < 0) {
            return new ScreenMeta("", false, "", false, packed);
        }
        String header = packed.substring(1, end);
        String raw = packed.substring(end + 1);
        String[] parts = header.split(String.valueOf(SEP), -1);
        String id = parts.length > 0 ? parts[0] : "";
        boolean keep = parts.length > 1 && "1".equals(parts[1]);
        String key = parts.length > 2 ? parts[2] : "";
        boolean hid = parts.length > 3 && "1".equals(parts[3]);
        return new ScreenMeta(id, keep, key, hid, raw);
    }

    public static String rawAction(@Nullable String packed) {
        return unpack(packed).rawAction();
    }

    public static String elementId(ScreenElement e) {
        if (e instanceof ButtonElement b) return unpack(b.action()).id();
        if (e instanceof TextElement t) return unpack(t.action()).id();
        if (e instanceof ImageElement i) return unpack(i.action()).id();
        if (e instanceof SlotElement s) return unpack(s.action()).id();
        if (e instanceof BarElement bar) return unpackBarId(bar.id());
        if (e instanceof ListElement list) return unpack(list.header()).id();
        if (e instanceof VariableListElement v) return unpack(v.header()).id();
        return "";
    }

    public static boolean isHidden(ScreenElement e) {
        if (e instanceof ButtonElement b) return unpack(b.action()).hidden();
        if (e instanceof TextElement t) return unpack(t.action()).hidden();
        if (e instanceof ImageElement i) return unpack(i.action()).hidden();
        if (e instanceof SlotElement s) return unpack(s.action()).hidden();
        if (e instanceof BarElement bar) return isBarHidden(bar.id());
        if (e instanceof ListElement list) return unpack(list.header()).hidden();
        return false;
    }

    public static String packBarId(String id, boolean hidden) {
        String clean = unpackBarId(id);
        return hidden ? MARK + "H" + MARK + clean : clean;
    }

    public static String unpackBarId(@Nullable String raw) {
        if (raw != null && raw.startsWith(MARK + "H" + MARK)) {
            return raw.substring(3);
        }
        return raw == null ? "" : raw;
    }

    public static boolean isBarHidden(@Nullable String raw) {
        return raw != null && raw.startsWith(MARK + "H" + MARK);
    }

    private static String repackAction(String packed, @Nullable Boolean newHidden) {
        ScreenMeta m = unpack(packed);
        return pack(m.id(), m.keepOpen(), m.hotkey(), newHidden != null ? newHidden : m.hidden(), m.rawAction());
    }

    public static ScreenElement withHidden(ScreenElement e, boolean hidden) {
        if (e instanceof ButtonElement b) return new ButtonElement(b.layout(), b.label(), repackAction(b.action(), hidden));
        if (e instanceof TextElement t) return new TextElement(t.layout(), t.text(), t.shadow(), repackAction(t.action(), hidden));
        if (e instanceof ImageElement i) return new ImageElement(i.layout(), i.texture(), i.textureWidth(), i.textureHeight(), repackAction(i.action(), hidden));
        if (e instanceof SlotElement s) return new SlotElement(s.layout(), s.content(), s.value(), s.count(), s.frame(), repackAction(s.action(), hidden));
        if (e instanceof BarElement bar) {
            return new BarElement(bar.layout(), packBarId(bar.id(), hidden), bar.value(), bar.max(), bar.direction(),
                    bar.backgroundColor(), bar.fillTexture(), bar.backgroundTexture(), bar.textureWidth(), bar.textureHeight(), bar.text(), bar.shadow());
        }
        if (e instanceof ListElement list) {
            return new ListElement(list.layout(), repackAction(list.header(), hidden), list.horizontal(), list.select(), list.rows());
        }
        return e;
    }

    public static ScreenElement withPosition(ScreenElement e, int x, int y) {
        Layout old = e.layout();
        Layout next = new Layout(x, y, old.width(), old.height(), old.anchor(), old.color(), old.layer());
        return withLayout(e, next);
    }

    public static ScreenElement withColor(ScreenElement e, int color) {
        Layout old = e.layout();
        Layout next = new Layout(old.x(), old.y(), old.width(), old.height(), old.anchor(), color, old.layer());
        return withLayout(e, next);
    }

    public static ScreenElement withLayout(ScreenElement e, Layout next) {
        if (e instanceof ButtonElement b) return new ButtonElement(next, b.label(), b.action());
        if (e instanceof TextElement t) return new TextElement(next, t.text(), t.shadow(), t.action());
        if (e instanceof ImageElement i) return new ImageElement(next, i.texture(), i.textureWidth(), i.textureHeight(), i.action());
        if (e instanceof SlotElement s) return new SlotElement(next, s.content(), s.value(), s.count(), s.frame(), s.action());
        if (e instanceof BarElement bar) {
            return new BarElement(next, bar.id(), bar.value(), bar.max(), bar.direction(),
                    bar.backgroundColor(), bar.fillTexture(), bar.backgroundTexture(), bar.textureWidth(), bar.textureHeight(), bar.text(), bar.shadow());
        }
        if (e instanceof ListElement list) {
            return new ListElement(next, list.header(), list.horizontal(), list.select(), list.rows());
        }
        return e;
    }

    public static ScreenElement withText(ScreenElement e, String text) {
        if (e instanceof TextElement t) return t.withText(text);
        if (e instanceof ButtonElement b) return new ButtonElement(b.layout(), text, b.action());
        if (e instanceof BarElement bar) return bar.withText(text);
        if (e instanceof SlotElement s && s.content() == SlotElement.Content.TEXT) return s.withValue(text);
        if (e instanceof ListElement list) {
            ScreenMeta m = unpack(list.header());
            return new ListElement(list.layout(), pack(m.id(), m.keepOpen(), m.hotkey(), m.hidden(), text), list.horizontal(), list.select(), list.rows());
        }
        return e;
    }

    public static ScreenElement withTexture(ScreenElement e, String texture) {
        if (e instanceof ImageElement i) return new ImageElement(i.layout(), texture, i.textureWidth(), i.textureHeight(), i.action());
        if (e instanceof SlotElement s && s.content() == SlotElement.Content.IMAGE) return s.withValue(texture);
        return e;
    }

    public static ScreenElement withSlotItem(ScreenElement e, String item, int count) {
        if (e instanceof SlotElement s) {
            return new SlotElement(s.layout(), SlotElement.Content.ITEM, item, Math.max(1, count), s.frame(), s.action());
        }
        return e;
    }
}
