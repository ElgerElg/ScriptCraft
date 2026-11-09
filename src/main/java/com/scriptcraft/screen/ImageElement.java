package com.scriptcraft.screen;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.UnaryOperator;

/**
 * Картинка из ресурсов (ресурспака или мода), например "minecraft:textures/item/diamond.png".
 * Рисуется целиком, растянутой до width x height; textureWidth/Height — реальный размер файла.
 */
/**
 * Картинка. action "" — только рисунок; "close" или "/команда" — клик по прямоугольнику size
 * (если size нет, 16x16). Хитбокс работает на open screen, не на HUD.
 */
public record ImageElement(Layout layout, String texture, int textureWidth, int textureHeight, String action) implements ScreenElement {
    @Override
    public ElementType type() {
        return ElementType.IMAGE;
    }

    @Override
    public void writeData(FriendlyByteBuf buf) {
        buf.writeUtf(texture);
        buf.writeVarInt(textureWidth);
        buf.writeVarInt(textureHeight);
        buf.writeUtf(action);
    }

    static ImageElement readData(Layout layout, FriendlyByteBuf buf) {
        return new ImageElement(layout, buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readUtf());
    }

    @Override
    public ScreenElement format(UnaryOperator<String> formatter) {
        return new ImageElement(layout, texture, textureWidth, textureHeight, formatter.apply(action));
    }
}
