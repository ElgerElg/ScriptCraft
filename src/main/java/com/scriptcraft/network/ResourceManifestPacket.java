package com.scriptcraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Сервер -> клиент: список ресурсов сервера (путь + SHA-1 + размер).
 * Клиент сравнивает его со своим кэшем scriptcraft/cache/ и запрашивает только недостающие/изменённые файлы.
 */
public record ResourceManifestPacket(List<Entry> entries) {
    /** Максимум файлов в манифесте (защита от огромного пакета; лимит пакета сервер->клиент — 1 МБ). */
    public static final int MAX_ENTRIES = 8000;
    public static final int MAX_PATH = 256;

    /** @param path путь от корня ресурсов: assets/<ns>/... */
    public record Entry(String path, String sha1, int size) {
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeUtf(e.path(), MAX_PATH);
            buf.writeUtf(e.sha1(), 40);
            buf.writeVarInt(e.size());
        }
    }

    public static ResourceManifestPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many resource entries: " + count);
        }
        List<Entry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new Entry(buf.readUtf(MAX_PATH), buf.readUtf(40), buf.readVarInt()));
        }
        return new ResourceManifestPacket(list);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.scriptcraft.client.ClientResources.onManifest(entries));
        ctx.get().setPacketHandled(true);
    }
}
