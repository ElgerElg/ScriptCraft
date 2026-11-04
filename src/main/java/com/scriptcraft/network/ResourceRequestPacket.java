package com.scriptcraft.network;

import com.scriptcraft.resources.ResourceSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Клиент -> сервер: "пришли мне эти файлы" (только те, которых нет в кэше или которые изменились). */
public record ResourceRequestPacket(List<String> paths) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(paths.size());
        for (String p : paths) {
            buf.writeUtf(p, ResourceManifestPacket.MAX_PATH);
        }
    }

    public static ResourceRequestPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > ResourceManifestPacket.MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many requested resources: " + count);
        }
        List<String> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(buf.readUtf(ResourceManifestPacket.MAX_PATH));
        }
        return new ResourceRequestPacket(list);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            ResourceSync.handleRequest(player, paths);
        }
        ctx.get().setPacketHandled(true);
    }
}
