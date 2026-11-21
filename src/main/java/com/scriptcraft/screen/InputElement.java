package com.scriptcraft.screen;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.UnaryOperator;

/**
 * Поле ввода текста на кастомном экране (open screen).
 * action хранит упакованную мету (ScreenMeta: id, keepOpen, hotkey, hidden, tooltip, rawAction).
 */
public record InputElement(Layout layout, String value, String placeholder, int maxLength, String action) implements ScreenElement {
    public static final int DEFAULT_WIDTH = 140;
    public static final int DEFAULT_HEIGHT = 20;

    @Override
    public ElementType type() {
        return ElementType.INPUT;
    }

    @Override
    public void writeData(FriendlyByteBuf buf) {
        buf.writeUtf(value == null ? "" : value, 1024);
        buf.writeUtf(placeholder == null ? "" : placeholder, 256);
        buf.writeVarInt(maxLength);
        buf.writeUtf(action == null ? "" : action, 1024);
    }

    static InputElement readData(Layout layout, FriendlyByteBuf buf) {
        return new InputElement(layout, buf.readUtf(1024), buf.readUtf(256), buf.readVarInt(), buf.readUtf(1024));
    }

    @Override
    public ScreenElement format(UnaryOperator<String> formatter) {
        return new InputElement(layout, formatter.apply(value), formatter.apply(placeholder), maxLength, formatter.apply(action));
    }

    public InputElement withValue(String nextValue) {
        return new InputElement(layout, nextValue == null ? "" : nextValue, placeholder, maxLength, action);
    }
}
