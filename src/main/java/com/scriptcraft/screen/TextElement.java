package com.scriptcraft.screen;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.UnaryOperator;

/**
 * Текст на экране. Ширина вычисляется на клиенте по шрифту, если size не задан.
 * action: "" — просто надпись; "close" или "/команда" — клик по хитбоксу текста (только open screen).
 */
public record TextElement(Layout layout, String text, boolean shadow, String action) implements ScreenElement {
    @Override
    public ElementType type() {
        return ElementType.TEXT;
    }

    @Override
    public void writeData(FriendlyByteBuf buf) {
        buf.writeUtf(text);
        buf.writeBoolean(shadow);
        buf.writeUtf(action);
    }

    static TextElement readData(Layout layout, FriendlyByteBuf buf) {
        return new TextElement(layout, buf.readUtf(), buf.readBoolean(), buf.readUtf());
    }

    @Override
    public ScreenElement format(UnaryOperator<String> formatter) {
        return new TextElement(layout, formatter.apply(text), shadow, formatter.apply(action));
    }

    public TextElement withText(String newText) {
        return new TextElement(layout, newText, shadow, action);
    }
}
