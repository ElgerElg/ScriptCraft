package com.scriptcraft.screen;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.UnaryOperator;

/**
 * Картинка из ресурсов (ресурспака или мода), например "minecraft:textures/item/diamond.png".
 * Рисуется целиком, растянутой до width x height; textureWidth/Height — реальный размер файла.
 */
public record ImageElement(Layout layout, String texture, int textureWidth, int textureHeight) implements ScreenElement {
    @Override
    public ElementType type() {
        return ElementType.IMAGE;
    }

    @Override
    public void writeData(FriendlyByteBuf buf) {
        buf.writeUtf(texture);
        buf.writeVarInt(textureWidth);
        buf.writeVarInt(textureHeight);
    }

    static ImageElement readData(Layout layout, FriendlyByteBuf buf) {
        return new ImageElement(layout, buf.readUtf(), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public ScreenElement format(UnaryOperator<String> formatter) {
        return this;
    }
}
