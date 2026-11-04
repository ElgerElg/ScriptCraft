package com.scriptcraft.screen;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.UnaryOperator;

/**
 * Слот (только отображение + подсказка при наведении на экране).
 * Содержимое задаёт {@link Content}:
 * - ITEM: value = id предмета, count = количество;
 * - HOTBAR: count = номер слота хотбара игрока 1..9 (клиент показывает его настоящий предмет и подсвечивает выбранный);
 * - IMAGE: value = картинка, вписанная в слот;
 * - TEXT: value = текст по центру слота.
 *
 * @param frame рисовать ли стандартную рамку слота (в HUD часто выключают и подкладывают свою картинку слоем ниже)
 * TODO: настоящие слоты через AbstractContainerMenu, чтобы предметы можно было брать/класть.
 */
public record SlotElement(Layout layout, Content content, String value, int count, boolean frame) implements ScreenElement {
    /** Размер слота по умолчанию (как в ванильных инвентарях). */
    public static final int SIZE = 18;

    /** Что лежит в слоте. Порядок важен для сети — новые добавлять в конец. */
    public enum Content {
        ITEM, HOTBAR, IMAGE, TEXT
    }

    @Override
    public ElementType type() {
        return ElementType.SLOT;
    }

    @Override
    public void writeData(FriendlyByteBuf buf) {
        buf.writeEnum(content);
        buf.writeUtf(value);
        buf.writeVarInt(count);
        buf.writeBoolean(frame);
    }

    static SlotElement readData(Layout layout, FriendlyByteBuf buf) {
        return new SlotElement(layout, buf.readEnum(Content.class), buf.readUtf(), buf.readVarInt(), buf.readBoolean());
    }

    @Override
    public ScreenElement format(UnaryOperator<String> formatter) {
        return content == Content.TEXT ? withValue(formatter.apply(value)) : this;
    }

    public SlotElement withValue(String newValue) {
        return new SlotElement(layout, content, newValue, count, frame);
    }
}
