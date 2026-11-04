package com.scriptcraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер -> клиент: кусок файла ресурса. Файл режется на куски (resourceChunkSizeKb), чтобы
 * не упереться в лимит пакета (1 МБ) и не забивать канал: сервер отправляет несколько кусков за тик.
 *
 * @param path   путь assets/<ns>/...
 * @param sha1   ожидаемый SHA-1 всего файла (клиент проверяет после сборки)
 * @param total  размер всего файла
 * @param offset смещение этого куска
 * @param data   байты куска
 */
public record ResourceChunkPacket(String path, String sha1, int total, int offset, byte[] data) {
    /** Максимальный размер куска при чтении (чуть меньше лимита пакета). */
    public static final int MAX_CHUNK = 960 * 1024;

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(path, ResourceManifestPacket.MAX_PATH);
        buf.writeUtf(sha1, 40);
        buf.writeVarInt(total);
        buf.writeVarInt(offset);
        buf.writeByteArray(data);
    }

    public static ResourceChunkPacket decode(FriendlyByteBuf buf) {
        return new ResourceChunkPacket(buf.readUtf(ResourceManifestPacket.MAX_PATH), buf.readUtf(40),
                buf.readVarInt(), buf.readVarInt(), buf.readByteArray(MAX_CHUNK));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.scriptcraft.client.ClientResources.onChunk(this));
        ctx.get().setPacketHandled(true);
    }
}
