package com.scriptcraft.screen;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** Полное описание экрана: заголовок + список элементов. Именно это летит по сети. */
public record ScreenDescription(String title, List<ScreenElement> elements) {
    /** Защита от слишком больших пакетов. */
    public static final int MAX_ELEMENTS = 256;

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(title);
        int count = Math.min(elements.size(), MAX_ELEMENTS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            ScreenElement.write(elements.get(i), buf);
        }
    }

    public static ScreenDescription read(FriendlyByteBuf buf) {
        String title = buf.readUtf();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ELEMENTS) {
            throw new IllegalArgumentException("Слишком много элементов экрана: " + count);
        }
        List<ScreenElement> elements = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            elements.add(ScreenElement.read(buf));
        }
        return new ScreenDescription(title, elements);
    }
}
