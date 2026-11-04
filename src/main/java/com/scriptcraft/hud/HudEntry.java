package com.scriptcraft.hud;

import com.scriptcraft.screen.BarElement;
import com.scriptcraft.screen.ScreenElement;
import com.scriptcraft.screen.SlotElement;
import com.scriptcraft.screen.TextElement;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Элемент HUD игрока: обычный элемент экрана + имя, группа и видимость. Общая модель (сервер и клиент).
 *
 * @param id      уникальное имя элемента у игрока (по нему обновляют: set value of bar "hp"...)
 * @param group   имя HUD из "create hud "имя"" — им можно скрыть/удалить сразу все элементы группы
 * @param visible скрыт ли элемент (hide hud ...): скрытый остаётся у клиента и показывается без повторной отправки
 */
public record HudEntry(String id, String group, boolean visible, ScreenElement element) {
    public HudEntry withVisible(boolean v) {
        return new HudEntry(id, group, v, element);
    }

    public HudEntry withElement(ScreenElement e) {
        return new HudEntry(id, group, visible, e);
    }

    /**
     * Копия с новым текстом: текстовый элемент, надпись на полосе или текстовый слот.
     * null — у этого элемента нет текста (картинка, предмет).
     */
    public HudEntry withText(String text) {
        ScreenElement e = element;
        if (e instanceof TextElement t) {
            return withElement(t.withText(text));
        }
        if (e instanceof BarElement b) {
            return withElement(b.withText(text));
        }
        if (e instanceof SlotElement s && s.content() == SlotElement.Content.TEXT) {
            return withElement(s.withValue(text));
        }
        return null;
    }

    /** Копия полосы с новыми value/max; null — это не полоса. */
    public HudEntry withValues(String value, String max) {
        return element instanceof BarElement b ? withElement(b.withValues(value, max)) : null;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(group);
        buf.writeBoolean(visible);
        ScreenElement.write(element, buf);
    }

    public static HudEntry read(FriendlyByteBuf buf) {
        return new HudEntry(buf.readUtf(), buf.readUtf(), buf.readBoolean(), ScreenElement.read(buf));
    }
}
