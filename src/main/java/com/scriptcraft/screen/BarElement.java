package com.scriptcraft.screen;

import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * Полоса прогресса (HP, голод, мана...). Работает и в HUD, и на экранах (open screen).
 *
 * value и max — СТРОКИ, которые клиент понимает так:
 * - число ("15", "20.5") — фиксированное значение (переменные {x} и плейсхолдеры сервер уже подставил в format());
 * - имя источника из {@link #SOURCES} ("health", "food"...) — клиент каждый кадр берёт значение у своего игрока,
 *   поэтому полоса здоровья обновляется без единого пакета;
 * - всё остальное считается нулём.
 *
 * @param id                имя полосы (для HUD: по нему её обновляют; на экране не используется, может быть "")
 * @param backgroundColor   цвет фона ARGB (прозрачный = без фона)
 * @param fillTexture       картинка заполнения ("" = залить цветом layout.color); обрезается по прогрессу
 * @param backgroundTexture картинка фона ("" = нет), рисуется под заполнением на весь размер полосы
 * @param textureWidth      реальный размер картинок (0 = равен размеру полосы)
 * @param text              надпись по центру полосы; плейсхолдеры %value%, %max%, %percent% и источники считает клиент
 */
public record BarElement(Layout layout, String id, String value, String max, BarDirection direction,
                         int backgroundColor, String fillTexture, String backgroundTexture,
                         int textureWidth, int textureHeight, String text, boolean shadow) implements ScreenElement {

    /** Значения, которые клиент берёт у своего игрока каждый кадр (имена латиницей). */
    public static final List<String> SOURCES = List.of("health", "max_health", "absorption", "food", "saturation",
            "armor", "air", "max_air", "xp", "level");

    public static final int DEFAULT_WIDTH = 80;
    public static final int DEFAULT_HEIGHT = 8;

    public static boolean isSource(String raw) {
        return SOURCES.contains(raw.strip().toLowerCase(Locale.ROOT));
    }

    @Override
    public ElementType type() {
        return ElementType.BAR;
    }

    @Override
    public void writeData(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(value);
        buf.writeUtf(max);
        buf.writeEnum(direction);
        buf.writeInt(backgroundColor);
        buf.writeUtf(fillTexture);
        buf.writeUtf(backgroundTexture);
        buf.writeVarInt(textureWidth);
        buf.writeVarInt(textureHeight);
        buf.writeUtf(text);
        buf.writeBoolean(shadow);
    }

    static BarElement readData(Layout layout, FriendlyByteBuf buf) {
        return new BarElement(layout, buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readEnum(BarDirection.class),
                buf.readInt(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readBoolean());
    }

    /** Сервер подставляет {переменные} и %плейсхолдеры% в value/max/text; %value%, %health%... остаются клиенту. */
    @Override
    public ScreenElement format(UnaryOperator<String> formatter) {
        return new BarElement(layout, id, formatter.apply(value), formatter.apply(max), direction, backgroundColor,
                fillTexture, backgroundTexture, textureWidth, textureHeight, formatter.apply(text), shadow);
    }

    public BarElement withValues(String newValue, String newMax) {
        return new BarElement(layout, id, newValue, newMax, direction, backgroundColor, fillTexture, backgroundTexture,
                textureWidth, textureHeight, text, shadow);
    }

    public BarElement withText(String newText) {
        return new BarElement(layout, id, value, max, direction, backgroundColor, fillTexture, backgroundTexture,
                textureWidth, textureHeight, newText, shadow);
    }
}
