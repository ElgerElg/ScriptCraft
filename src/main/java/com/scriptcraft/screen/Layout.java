package com.scriptcraft.screen;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Общие параметры расположения элемента экрана.
 *
 * @param x      смещение по X от точки привязки
 * @param y      смещение по Y от точки привязки
 * @param width  ширина (0 = вычислить автоматически, например по ширине текста)
 * @param height высота
 * @param anchor точка привязки
 * @param color  цвет ARGB (для текста — цвет текста, для картинки — оттенок)
 */
public record Layout(int x, int y, int width, int height, Anchor anchor, int color) {
    public void write(FriendlyByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeVarInt(width);
        buf.writeVarInt(height);
        buf.writeEnum(anchor);
        buf.writeInt(color);
    }

    public static Layout read(FriendlyByteBuf buf) {
        return new Layout(buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readEnum(Anchor.class), buf.readInt());
    }
}
