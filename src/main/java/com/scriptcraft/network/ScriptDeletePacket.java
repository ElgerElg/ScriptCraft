package com.scriptcraft.network;

import com.scriptcraft.script.ScriptLoader;
import com.scriptcraft.script.ScriptPaths;
import com.scriptcraft.studio.StudioAccess;
import com.scriptcraft.studio.StudioRooms;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Удалить скрипт на сервере. Без права студии — уведомление. */
public record ScriptDeletePacket(String name) {
    public static void encode(ScriptDeletePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.name, 200);
    }

    public static ScriptDeletePacket decode(FriendlyByteBuf buf) {
        return new ScriptDeletePacket(buf.readUtf(200));
    }

    public static void handle(ScriptDeletePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (player == null || !StudioAccess.allow(player) || !ScriptPaths.contained(packet.name)) {
                return;
            }
            if (ScriptLoader.removeScript(player.createCommandSourceStack(), packet.name) != 0) {
                StudioRooms.forget(player.getServer(), packet.name, player.getUUID());
            }
            ModNetwork.sendTo(player, ScriptListPacket.ofServer());
            ModNetwork.sendTo(player, LogPacket.from(ScriptLoader.journal(), false));
        });
        ctx.get().setPacketHandled(true);
    }
}
