package com.scriptcraft.network;

import com.scriptcraft.script.ScriptLoader;
import com.scriptcraft.studio.StudioAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Клиент -> сервер: открыть студию. Без права scriptcraft.studio и без оператора — уведомление и тишина. */
public record LogRequestPacket() {
    public static void encode(LogRequestPacket packet, FriendlyByteBuf buf) {
    }

    public static LogRequestPacket decode(FriendlyByteBuf buf) {
        return new LogRequestPacket();
    }

    public static void handle(LogRequestPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (!StudioAccess.allow(player)) {
                return;
            }
            ModNetwork.sendTo(player, LogPacket.from(ScriptLoader.journal(), true));
            ModNetwork.sendTo(player, ScriptListPacket.ofServer());
            com.scriptcraft.studio.StudioRooms.sendAllViews(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
