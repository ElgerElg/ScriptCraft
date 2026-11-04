package com.scriptcraft.screen;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.UnaryOperator;

/** Текст на экране. Ширина вычисляется на клиенте по шрифту. */
public record TextElement(Layout layout, String text, boolean shadow) implements ScreenElement {
    @Override
    public ElementType type() {
        return ElementType.TEXT;
    }

    @Override
    public void writeData(FriendlyByteBuf buf) {
        buf.writeUtf(text);
        buf.writeBoolean(shadow);
    }

    static TextElement readData(Layout layout, FriendlyByteBuf buf) {
        return new TextElement(layout, buf.readUtf(), buf.readBoolean());
    }

    @Override
    public ScreenElement format(UnaryOperator<String> formatter) {
        return new TextElement(layout, formatter.apply(text), shadow);
    }
}
