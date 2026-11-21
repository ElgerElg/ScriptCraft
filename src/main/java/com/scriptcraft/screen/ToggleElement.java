package com.scriptcraft.screen;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.UnaryOperator;

/**
 * Чекбокс / тумблер на кастомном экране (open screen).
 */
public record ToggleElement(Layout layout, String label, boolean value, String action) implements ScreenElement {
    public static final int DEFAULT_WIDTH = 130;
    public static final int DEFAULT_HEIGHT = 20;

    @Override
    public ElementType type() {
        return ElementType.TOGGLE;
    }

    @Override
    public void writeData(FriendlyByteBuf buf) {
        buf.writeUtf(label == null ? "" : label, 256);
        buf.writeBoolean(value);
        buf.writeUtf(action == null ? "" : action, 1024);
    }

    static ToggleElement readData(Layout layout, FriendlyByteBuf buf) {
        return new ToggleElement(layout, buf.readUtf(256), buf.readBoolean(), buf.readUtf(1024));
    }

    @Override
    public ScreenElement format(UnaryOperator<String> formatter) {
        return new ToggleElement(layout, formatter.apply(label), value, formatter.apply(action));
    }

    public ToggleElement withValue(boolean nextValue) {
        return new ToggleElement(layout, label, nextValue, action);
    }
}
