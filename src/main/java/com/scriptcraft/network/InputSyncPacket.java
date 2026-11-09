package com.scriptcraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Сервер -> клиент: какие клавиши слушать и какие ванильные меню смотреть или не открывать. */
public record InputSyncPacket(List<String> keys, List<String> menus, List<String> cancel) {
    public static void encode(InputSyncPacket packet, FriendlyByteBuf buf) {
        write(buf, packet.keys);
        write(buf, packet.menus);
        write(buf, packet.cancel);
    }

    public static InputSyncPacket decode(FriendlyByteBuf buf) {
        return new InputSyncPacket(read(buf), read(buf), read(buf));
    }

    public static void handle(InputSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        com.scriptcraft.client.ClientInput.apply(packet)));
        ctx.get().setPacketHandled(true);
    }

    private static void write(FriendlyByteBuf buf, List<String> names) {
        buf.writeVarInt(names.size());
        for (String name : names) {
            buf.writeUtf(name, 32);
        }
    }

    private static List<String> read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> names = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            names.add(buf.readUtf(32));
        }
        return List.copyOf(names);
    }
}
