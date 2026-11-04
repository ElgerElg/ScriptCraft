package com.scriptcraft.screen;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.UnaryOperator;

/**
 * Слот с предметом (только отображение + подсказка при наведении).
 * TODO: настоящие слоты через AbstractContainerMenu, чтобы предметы можно было брать/класть.
 */
public record SlotElement(Layout layout, String itemId, int count) implements ScreenElement {
    @Override
    public ElementType type() {
        return ElementType.SLOT;
    }

    @Override
    public void writeData(FriendlyByteBuf buf) {
        buf.writeUtf(itemId);
        buf.writeVarInt(count);
    }

    static SlotElement readData(Layout layout, FriendlyByteBuf buf) {
        return new SlotElement(layout, buf.readUtf(), buf.readVarInt());
    }

    @Override
    public ScreenElement format(UnaryOperator<String> formatter) {
        return this;
    }
}
