package com.scriptcraft.screen;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Список строк на экране. vertical — столбец, horizontal — ряд.
 * select рисует подсветку строки под курсором. У строки action "" — она не нажимается;
 * "close" или "/команда" — клик по хитбоксу строки (только open screen, не HUD).
 *
 * @param header     заголовок над строками, "" — без заголовка
 * @param horizontal true — строки слева направо
 */
public record ListElement(Layout layout, String header, boolean horizontal, boolean select, List<ListElement.Row> rows)
        implements ScreenElement {

    /** Высота одной строки и заголовка, в пикселях. */
    public static final int ROW = 14;

    public record Row(String text, String action) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(text);
            buf.writeUtf(action);
        }

        public static Row read(FriendlyByteBuf buf) {
            return new Row(buf.readUtf(), buf.readUtf());
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
        buf.writeVarInt(rows.size());
        for (Row row : rows) {
            row.write(buf);
        }
    }

    static ListElement readData(Layout layout, FriendlyByteBuf buf) {
        String header = buf.readUtf();
        boolean horizontal = buf.readBoolean();
        boolean select = buf.readBoolean();
        int n = buf.readVarInt();
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(Row.read(buf));
        }
        return new ListElement(layout, header, horizontal, select, List.copyOf(rows));
    }

    @Override
    public ScreenElement format(UnaryOperator<String> formatter) {
        List<Row> formatted = new ArrayList<>(rows.size());
        for (Row row : rows) {
            formatted.add(new Row(formatter.apply(row.text()), formatter.apply(row.action())));
        }
        return new ListElement(layout, formatter.apply(header), horizontal, select, List.copyOf(formatted));
    }
}
