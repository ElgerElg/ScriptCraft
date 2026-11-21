package com.scriptcraft.screen;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.UnaryOperator;

/**
 * Слайдер (ползунок) на кастомном экране (open screen).
 */
public record SliderElement(Layout layout, String label, double min, double max, double value, double step, String action) implements ScreenElement {
    public static final int DEFAULT_WIDTH = 150;
    public static final int DEFAULT_HEIGHT = 20;

    @Override
    public ElementType type() {
        return ElementType.SLIDER;
    }

    @Override
    public void writeData(FriendlyByteBuf buf) {
        buf.writeUtf(label == null ? "" : label, 256);
        buf.writeDouble(min);
        buf.writeDouble(max);
        buf.writeDouble(value);
        buf.writeDouble(step);
        buf.writeUtf(action == null ? "" : action, 1024);
    }

    static SliderElement readData(Layout layout, FriendlyByteBuf buf) {
        return new SliderElement(layout, buf.readUtf(256), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readUtf(1024));
    }

    @Override
    public ScreenElement format(UnaryOperator<String> formatter) {
        return new SliderElement(layout, formatter.apply(label), min, max, value, step, formatter.apply(action));
    }

    public SliderElement withValue(double nextValue) {
        double lo = Math.min(min, max);
        double hi = Math.max(min, max);
        double clamped = Math.max(lo, Math.min(hi, nextValue));
        if (step > 0) {
            clamped = lo + Math.round((clamped - lo) / step) * step;
            clamped = Math.max(lo, Math.min(hi, clamped));
        }
        return new SliderElement(layout, label, min, max, clamped, step, action);
    }
}
