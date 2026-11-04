package com.scriptcraft.screen;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.UnaryOperator;

/**
 * Кнопка. action:
 * "close"      — закрыть экран;
 * "/команда"   — клиент отправит эту команду на сервер от имени игрока (права проверяет сервер) и закроет экран.
 * TODO: серверные колбэки по id кнопки (пакет клиент -> сервер), чтобы не светить команды.
 */
public record ButtonElement(Layout layout, String label, String action) implements ScreenElement {
    @Override
    public ElementType type() {
        return ElementType.BUTTON;
    }

    @Override
    public void writeData(FriendlyByteBuf buf) {
        buf.writeUtf(label);
        buf.writeUtf(action);
    }

    static ButtonElement readData(Layout layout, FriendlyByteBuf buf) {
        return new ButtonElement(layout, buf.readUtf(), buf.readUtf());
    }

    @Override
    public ScreenElement format(UnaryOperator<String> formatter) {
        return new ButtonElement(layout, formatter.apply(label), formatter.apply(action));
    }
}
