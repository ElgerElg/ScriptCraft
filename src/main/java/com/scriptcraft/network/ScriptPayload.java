package com.scriptcraft.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Передача текста скриптов любого размера (до 8 МБ) с прозрачным GZIP-сжатием
 * и безопасной нарезкой по UTF-8 границам для C2S (<= 32 КБ) и S2C (<= 1 МБ) пакетов.
 */
public final class ScriptPayload {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    /** Безопасный размер одного C2S-куска (лимит ванильного ServerboundCustomPayloadPacket = 32767 байт). */
    public static final int MAX_C2S_SLICE_BYTES = 20_000;
    /** Безопасный размер одного S2C-куска (лимит ванильного ClientboundCustomPayloadPacket = 1048576 байт). */
    public static final int MAX_S2C_SLICE_BYTES = 262_144;

    private ScriptPayload() {
    }

    static void write(FriendlyByteBuf buf, String text) {
        String safe = text == null ? "" : text;
        byte[] data = safe.getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_BYTES) {
            throw new IllegalArgumentException("script is too large (" + data.length + " bytes)");
        }
        if (data.length >= 2048) {
            byte[] gz = gzip(data);
            if (gz != null && gz.length > 0 && gz.length < data.length) {
                buf.writeVarInt(-gz.length);
                buf.writeBytes(gz);
                return;
            }
        }
        buf.writeVarInt(data.length);
        buf.writeBytes(data);
    }

    static String read(FriendlyByteBuf buf) {
        int rawSize = buf.readVarInt();
        if (rawSize < 0) {
            int gzSize = -rawSize;
            if (gzSize <= 0 || gzSize > MAX_BYTES) {
                throw new DecoderException("compressed script payload " + gzSize);
            }
            byte[] gz = new byte[gzSize];
            buf.readBytes(gz);
            return gunzipToString(gz);
        }
        if (rawSize > MAX_BYTES) {
            throw new DecoderException("script payload " + rawSize);
        }
        byte[] data = new byte[rawSize];
        buf.readBytes(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    /**
     * Разрезает строку на части так, чтобы каждая часть в UTF-8 занимала не более {@code maxSliceBytes} байт,
     * не разрывая суррогатные пары UTF-16. Всегда возвращает хотя бы один элемент.
     */
    public static List<String> splitUtf8(String text, int maxSliceBytes) {
        String safe = text == null ? "" : text;
        if (safe.isEmpty()) {
            return List.of("");
        }
        int limit = Math.max(1024, maxSliceBytes);
        // Быстрая проверка: если вся строка заведомо помещается (макс. 3 байта на char без суррогатов / 4 с ними)
        if ((long) safe.length() * 3L <= limit) {
            byte[] full = safe.getBytes(StandardCharsets.UTF_8);
            if (full.length <= limit) {
                return List.of(safe);
            }
        }
        List<String> out = new ArrayList<>();
        int len = safe.length();
        int start = 0;
        int curBytes = 0;
        int i = 0;
        while (i < len) {
            char c = safe.charAt(i);
            int charLen = 1;
            int utf8Bytes;
            if (c < 0x80) {
                utf8Bytes = 1;
            } else if (c < 0x800) {
                utf8Bytes = 2;
            } else if (Character.isHighSurrogate(c) && i + 1 < len && Character.isLowSurrogate(safe.charAt(i + 1))) {
                charLen = 2;
                utf8Bytes = 4;
            } else {
                utf8Bytes = 3;
            }
            if (curBytes + utf8Bytes > limit && i > start) {
                out.add(safe.substring(start, i));
                start = i;
                curBytes = 0;
            }
            curBytes += utf8Bytes;
            i += charLen;
        }
        if (start < len || out.isEmpty()) {
            out.add(safe.substring(start, len));
        }
        return out;
    }

    private static byte[] gzip(byte[] raw) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.min(raw.length, 16384));
            try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
                gzos.write(raw);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private static String gunzipToString(byte[] gz) {
        try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(gz));
             ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(MAX_BYTES, gz.length * 4))) {
            byte[] buf = new byte[8192];
            int total = 0;
            int n;
            while ((n = gzis.read(buf)) != -1) {
                total += n;
                if (total > MAX_BYTES) {
                    throw new DecoderException("decompressed script exceeds MAX_BYTES");
                }
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DecoderException("invalid compressed script payload", e);
        }
    }
}
