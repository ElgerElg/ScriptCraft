package com.scriptcraft.screen;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.UnaryOperator;

/**
 * Список, строки которого берутся из {имя::*} в момент открытия.
 * На клиент уходит уже обычный {@link ListElement}: этот тип по сети не пишется.
 *
 * @param pattern  имя с хвостом ::*, плейсхолдеры ещё не подставлены (pouch::%player%::*)
 * @param rowText  шаблон строки, внутри %loop-index% и %loop-value%
 * @param rowAction шаблон команды, "" — строка только рисуется
 */
public record VariableListElement(Layout layout, String header, boolean horizontal, boolean select,
                                  String pattern, String rowText, String rowAction) implements ScreenElement {
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
