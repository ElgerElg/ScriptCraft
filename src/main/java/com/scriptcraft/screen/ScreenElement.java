package com.scriptcraft.screen;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.UnaryOperator;

/**
 * Элемент экрана из скрипта. Это ОБЩАЯ модель данных (есть и на сервере, и на клиенте):
 * сервер её собирает и сериализует, клиент читает и рисует в {@code com.scriptcraft.client.ScriptScreen}.
 * Здесь нет ни одного клиентского класса, поэтому выделенный сервер не упадёт.
 */
public sealed interface ScreenElement permits TextElement, ButtonElement, SlotElement, ImageElement, BarElement, ListElement {
    ElementType type();

    Layout layout();

    /** Запись полей конкретного элемента (после типа и Layout). */
    void writeData(FriendlyByteBuf buf);

    /** Копия элемента с подставленными плейсхолдерами в текстовых полях. */
    ScreenElement format(UnaryOperator<String> formatter);

    static void write(ScreenElement element, FriendlyByteBuf buf) {
        buf.writeEnum(element.type());
        element.layout().write(buf);
        element.writeData(buf);
    }

    static ScreenElement read(FriendlyByteBuf buf) {
        ElementType type = buf.readEnum(ElementType.class);
        Layout layout = Layout.read(buf);
        return switch (type) {
            case TEXT -> TextElement.readData(layout, buf);
            case BUTTON -> ButtonElement.readData(layout, buf);
            case SLOT -> SlotElement.readData(layout, buf);
            case IMAGE -> ImageElement.readData(layout, buf);
            case BAR -> BarElement.readData(layout, buf);
            case LIST -> ListElement.readData(layout, buf);
        };
    }
}
