package com.scriptcraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер -> клиент: одно всплывающее уведомление вместо строки чата. */
public record NotifyPacket(String text) {
    public static void encode(NotifyPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.text, 512);
    }

    public static NotifyPacket decode(FriendlyByteBuf buf) {
        return new NotifyPacket(buf.readUtf(512));
    }

    public static void handle(NotifyPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        com.scriptcraft.client.ClientPacketHandler.toast(packet.text)));
        ctx.get().setPacketHandled(true);
    }
}
