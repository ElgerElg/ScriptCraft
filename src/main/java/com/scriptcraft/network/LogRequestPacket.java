package com.scriptcraft.network;

import com.scriptcraft.script.ScriptLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Клиент -> сервер: открыть журнал ошибок. Молча игнорируется, если игрок не оператор. */
public record LogRequestPacket() {
    public static void encode(LogRequestPacket packet, FriendlyByteBuf buf) {
    }

    public static LogRequestPacket decode(FriendlyByteBuf buf) {
        return new LogRequestPacket();
    }

    public static void handle(LogRequestPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (player == null || !player.hasPermissions(2)) {
                return;
            }
            ModNetwork.sendTo(player, LogPacket.from(ScriptLoader.journal()));
        });
        ctx.get().setPacketHandled(true);
    }
}
