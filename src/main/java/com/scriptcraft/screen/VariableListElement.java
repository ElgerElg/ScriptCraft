package com.scriptcraft.screen;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.UnaryOperator;

/**
 * Список, строки которого берутся из {@code {имя::*}} в момент открытия/обновления экрана.
 * На клиент уходит уже обычный {@link ListElement}: этот тип по сети не пишется.
 */
public record VariableListElement(
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
        String pattern,
        ListElement.Row templateRow,
        String rawBarExpr
) implements ScreenElement {

    public VariableListElement(Layout layout, String header, boolean horizontal, boolean select,
                               String pattern, String rowText, String rowAction) {
        this(layout, header, horizontal, select, false, ListElement.ROW, 0, 1.0f,
                ListElement.ALIGN_LEFT, ListElement.DEFAULT_BG, ListElement.DEFAULT_BORDER,
                "", "", pattern, new ListElement.Row(rowText, rowAction), "");
    }

    @Override
    public ElementType type() {
        return ElementType.LIST;
    }

    @Override
    public void writeData(FriendlyByteBuf buf) {
        throw new IllegalStateException("list from раскрывается на сервере до отправки");
    }

    @Override
    public ScreenElement format(UnaryOperator<String> formatter) {
        return this;
    }
}
