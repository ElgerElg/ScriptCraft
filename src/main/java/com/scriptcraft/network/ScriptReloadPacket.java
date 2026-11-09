package com.scriptcraft.network;

import com.scriptcraft.lang.Feedback;
import com.scriptcraft.script.ScriptLoader;
import com.scriptcraft.script.ScriptPaths;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Перезагрузить уже сохранённый скрипт, не затирая несохранённый текст в редакторе. */
public record ScriptReloadPacket(String name) {
    public static void encode(ScriptReloadPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.name, 200);
    }

    public static ScriptReloadPacket decode(FriendlyByteBuf buf) {
        return new ScriptReloadPacket(buf.readUtf(200));
    }

    public static void handle(ScriptReloadPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (player == null || !player.hasPermissions(2) || !ScriptPaths.contained(packet.name)) {
                return;
            }
            if (ScriptPaths.isDisabled(packet.name)) {
                Feedback.toPlayer(player, "studio.reload_disabled", packet.name);
                return;
            }
            ScriptLoader.reloadOne(player.createCommandSourceStack(), packet.name);
            ModNetwork.sendTo(player, LogPacket.from(ScriptLoader.journal(), false));
        });
        ctx.get().setPacketHandled(true);
    }
}
