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
 * @param color  цвет ARGB (для текста — цвет текста, для картинки — оттенок, для полосы — цвет заполнения)
 * @param layer  слой (z-приоритет): элементы рисуются по возрастанию слоя, при равном слое — в порядке
 *               объявления. Больший слой — выше. Может быть отрицательным (в HUD: отрицательный слой
 *               рисуется ПОД ванильным интерфейсом, остальные — поверх).
 */
public record Layout(int x, int y, int width, int height, Anchor anchor, int color, int layer) {
    public void write(FriendlyByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeVarInt(width);
        buf.writeVarInt(height);
        buf.writeEnum(anchor);
        buf.writeInt(color);
        buf.writeInt(layer);
    }

    /** Копия с другим смещением (нужна, например, чтобы разложить "hotbar" на 9 слотов). */
    public Layout withOffset(int newX, int newY) {
        return new Layout(newX, newY, width, height, anchor, color, layer);
    }

    public static Layout read(FriendlyByteBuf buf) {
        return new Layout(buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readEnum(Anchor.class),
                buf.readInt(), buf.readInt());
    }
}
