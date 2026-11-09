package com.scriptcraft.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.nio.charset.StandardCharsets;

/** Текст скрипта длиннее лимита writeUtf (32 КБ). */
final class ScriptPayload {
    static final int MAX_BYTES = 262144;

    private ScriptPayload() {
    }

    static void write(FriendlyByteBuf buf, String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_BYTES) {
            throw new IllegalArgumentException("script is too large");
        }
        buf.writeVarInt(data.length);
        buf.writeBytes(data);
    }

    static String read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_BYTES) {
            throw new DecoderException("script payload " + size);
        }
        byte[] data = new byte[size];
        buf.readBytes(data);
        return new String(data, StandardCharsets.UTF_8);
    }
}
