package com.scriptcraft.screen;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * Продвинутый список строк на экране ({@code open screen} и {@code create hud}).
 * Поддерживает:
 * - настраиваемую высоту строк ({@code rowHeight}), интервал ({@code spacing}), масштаб контента ({@code rowScale});
 * - индивидуальную ширину ({@code customWidth}) и высоту ({@code customHeight}) для конкретных позиций списка;
 * - разделители ({@code SEPARATOR}) и пустые отступы ({@code EMPTY}), а также текст пустого списка ({@code emptyText});
 * - активные/неактивные списки ({@code disabled}) и активные/выделенные или отключённые строки ({@code state});
 * - тултипы как ко всему списку, так и к каждой строке отдельно ({@code tooltip});
 * - выравнивание ({@code align}: left / center / right) списка и отдельных строк + колонки ({@code columnWidths});
 * - вложенные элементы внутри строки: иконка/предмет ({@code icon}), голова скина ({@code head}),
 *   правый текст ({@code rightText}), бейдж ({@code badge}), мини-полоса прогресса ({@code barProgress}),
 *   кнопка действия ({@code buttonLabel}).
 */
public record ListElement(
        Layout layout,
        String header,
        boolean horizontal,
        boolean select,
        boolean disabled,
        int rowHeight,
        int spacing,
        float rowScale,
        byte align,
        int bgColor,
        int borderColor,
        String emptyText,
        String columnWidths,
        List<ListElement.Row> rows
) implements ScreenElement {

    /** Стандартная высота одной строки и заголовка в пикселях. */
    public static final int ROW = 14;

    public static final byte ALIGN_INHERIT = -1;
    public static final byte ALIGN_LEFT = 0;
    public static final byte ALIGN_CENTER = 1;
    public static final byte ALIGN_RIGHT = 2;

    public static final byte STATE_NORMAL = 0;
    public static final byte STATE_ACTIVE = 1;
    public static final byte STATE_DISABLED = 2;

    public static final int DEFAULT_BG = 0xC0101010;
    public static final int DEFAULT_BORDER = 0xFF3A3A3A;

    public ListElement(Layout layout, String header, boolean horizontal, boolean select, List<Row> rows) {
        this(layout, header, horizontal, select, false, ROW, 0, 1.0f, ALIGN_LEFT, DEFAULT_BG, DEFAULT_BORDER, "", "", rows);
    }

    public static byte parseAlign(String raw, byte def) {
        if (raw == null) return def;
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "left", "start" -> ALIGN_LEFT;
            case "center", "centre", "middle" -> ALIGN_CENTER;
            case "right", "end" -> ALIGN_RIGHT;
            default -> def;
        };
    }

    public static String alignName(byte a) {
        return switch (a) {
            case ALIGN_CENTER -> "center";
            case ALIGN_RIGHT -> "right";
            default -> "left";
        };
    }

    public enum RowKind {
        NORMAL, SEPARATOR, EMPTY
    }

    public record Row(
            RowKind kind,
            String text,
            String rightText,
            String icon,
            int iconCount,
            String head,
            String badge,
            int badgeColor,
            float barProgress,
            int barColor,
            String buttonLabel,
            String buttonAction,
            int customWidth,
            int customHeight,
            byte align,
            byte state,
            int rowColor,
            String tooltip,
            String action
    ) {
        public Row(String text, String action) {
            this(RowKind.NORMAL, text, "", "", 1, "", "", 0xFFF59E0B, -1f, 0xFF3B82F6,
                    "", "", 0, 0, ALIGN_INHERIT, STATE_NORMAL, 0, "", action);
        }

        public static Row separator(String label, int color, int height, byte align) {
            return new Row(RowKind.SEPARATOR, label == null ? "" : label, "", "", 1, "", "", 0,
                    -1f, 0, "", "", 0, height, align, STATE_NORMAL, color, "", "");
        }

        public static Row empty(int width, int height) {
            return new Row(RowKind.EMPTY, "", "", "", 1, "", "", 0,
                    -1f, 0, "", "", width, height, ALIGN_INHERIT, STATE_NORMAL, 0, "", "");
        }

        public boolean isInteractive() {
            return kind == RowKind.NORMAL && state != STATE_DISABLED;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeEnum(kind);
            buf.writeUtf(text);
            buf.writeUtf(rightText);
            buf.writeUtf(icon);
            buf.writeVarInt(iconCount);
            buf.writeUtf(head);
            buf.writeUtf(badge);
            buf.writeInt(badgeColor);
            buf.writeFloat(barProgress);
            buf.writeInt(barColor);
            buf.writeUtf(buttonLabel);
            buf.writeUtf(buttonAction);
            buf.writeVarInt(customWidth);
            buf.writeVarInt(customHeight);
            buf.writeByte(align);
            buf.writeByte(state);
            buf.writeInt(rowColor);
            buf.writeUtf(tooltip);
            buf.writeUtf(action);
        }

        public static Row read(FriendlyByteBuf buf) {
            RowKind kind = buf.readEnum(RowKind.class);
            String text = buf.readUtf();
            String rightText = buf.readUtf();
            String icon = buf.readUtf();
            int iconCount = buf.readVarInt();
            String head = buf.readUtf();
            String badge = buf.readUtf();
            int badgeColor = buf.readInt();
            float barProgress = buf.readFloat();
            int barColor = buf.readInt();
            String buttonLabel = buf.readUtf();
            String buttonAction = buf.readUtf();
            int customWidth = buf.readVarInt();
            int customHeight = buf.readVarInt();
            byte align = buf.readByte();
            byte state = buf.readByte();
            int rowColor = buf.readInt();
            String tooltip = buf.readUtf();
            String action = buf.readUtf();
            return new Row(kind, text, rightText, icon, iconCount, head, badge, badgeColor,
                    barProgress, barColor, buttonLabel, buttonAction, customWidth, customHeight,
                    align, state, rowColor, tooltip, action);
        }

        public Row format(UnaryOperator<String> f) {
            float nextBar = barProgress;
            String dynBar = ScreenMeta.dynamicBarExprOfPacked(action);
            if (!dynBar.isEmpty()) {
                float parsed = parseDynamicBar(f.apply(dynBar));
                if (parsed >= 0f) {
                    nextBar = parsed;
                }
            }
            return new Row(
                    kind,
                    f.apply(text),
                    f.apply(rightText),
                    f.apply(icon),
                    iconCount,
                    f.apply(head),
                    f.apply(badge),
                    badgeColor,
                    nextBar,
                    barColor,
                    f.apply(buttonLabel),
                    f.apply(buttonAction),
                    customWidth,
                    customHeight,
                    align,
                    state,
                    rowColor,
                    f.apply(tooltip),
                    f.apply(action)
            );
        }

        private static float parseDynamicBar(String raw) {
            if (raw == null || raw.isBlank()) return -1f;
            String s = raw.strip().replace("%", "");
            int slash = s.indexOf('/');
            try {
                if (slash > 0) {
                    double v = Double.parseDouble(s.substring(0, slash).strip());
                    double m = Double.parseDouble(s.substring(slash + 1).strip());
                    return m > 0 ? (float) Math.max(0.0, Math.min(1.0, v / m)) : 0f;
                }
                double v = Double.parseDouble(s);
                if (v > 1.0001) v /= 100.0;
                return (float) Math.max(0.0, Math.min(1.0, v));
            } catch (NumberFormatException e) {
                return -1f;
            }
        }
    }

    @Override
    public ElementType type() {
        return ElementType.LIST;
    }

    @Override
    public void writeData(FriendlyByteBuf buf) {
        buf.writeUtf(header);
        buf.writeBoolean(horizontal);
        buf.writeBoolean(select);
        buf.writeBoolean(disabled);
        buf.writeVarInt(rowHeight);
        buf.writeVarInt(spacing);
        buf.writeFloat(rowScale);
        buf.writeByte(align);
        buf.writeInt(bgColor);
        buf.writeInt(borderColor);
        buf.writeUtf(emptyText);
        buf.writeUtf(columnWidths);
        buf.writeVarInt(rows.size());
        for (Row row : rows) {
            row.write(buf);
        }
    }

    static ListElement readData(Layout layout, FriendlyByteBuf buf) {
        String header = buf.readUtf();
        boolean horizontal = buf.readBoolean();
        boolean select = buf.readBoolean();
        boolean disabled = buf.readBoolean();
        int rowHeight = buf.readVarInt();
        int spacing = buf.readVarInt();
        float rowScale = buf.readFloat();
        byte align = buf.readByte();
        int bgColor = buf.readInt();
        int borderColor = buf.readInt();
        String emptyText = buf.readUtf();
        String columnWidths = buf.readUtf();
        int n = buf.readVarInt();
        List<Row> rows = new ArrayList<>(Math.max(0, Math.min(n, 2048)));
        for (int i = 0; i < n; i++) {
            rows.add(Row.read(buf));
        }
        return new ListElement(layout, header, horizontal, select, disabled, rowHeight, spacing, rowScale,
                align, bgColor, borderColor, emptyText, columnWidths, List.copyOf(rows));
    }

    @Override
    public ScreenElement format(UnaryOperator<String> formatter) {
        List<Row> formatted = new ArrayList<>(rows.size());
        for (Row row : rows) {
            formatted.add(row.format(formatter));
        }
        return new ListElement(layout, formatter.apply(header), horizontal, select, disabled,
                rowHeight, spacing, rowScale, align, bgColor, borderColor,
                formatter.apply(emptyText), columnWidths, List.copyOf(formatted));
    }

    public ListElement withLayout(Layout next) {
        return new ListElement(next, header, horizontal, select, disabled, rowHeight, spacing, rowScale,
                align, bgColor, borderColor, emptyText, columnWidths, rows);
    }

    public ListElement withHeader(String nextHeader) {
        return new ListElement(layout, nextHeader, horizontal, select, disabled, rowHeight, spacing, rowScale,
                align, bgColor, borderColor, emptyText, columnWidths, rows);
    }

    public ListElement withDisabled(boolean nextDisabled) {
        return new ListElement(layout, header, horizontal, select, nextDisabled, rowHeight, spacing, rowScale,
                align, bgColor, borderColor, emptyText, columnWidths, rows);
    }
}
