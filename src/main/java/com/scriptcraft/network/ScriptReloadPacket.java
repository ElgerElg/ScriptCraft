package com.scriptcraft.network;

import com.scriptcraft.lang.Feedback;
import com.scriptcraft.script.ScriptLoader;
import com.scriptcraft.script.ScriptPaths;
import com.scriptcraft.studio.StudioAccess;
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
            if (player == null || !StudioAccess.allow(player)) {
                return;
            }
            if ("$scripts".equals(packet.name) || "$all".equals(packet.name)) {
                ScriptLoader.reloadAll(player.createCommandSourceStack());
                com.scriptcraft.studio.StudioExplorer.broadcastScriptsAndLog(player.getServer());
                com.scriptcraft.studio.StudioExplorer.broadcastDirs(player.getServer());
                return;
            }
            if ("$resources".equals(packet.name)) {
                com.scriptcraft.command.ScriptCommands.reloadResources(player.createCommandSourceStack());
                com.scriptcraft.studio.StudioExplorer.broadcastScriptsAndLog(player.getServer());
                return;
            }
            if ("$config".equals(packet.name)) {
                com.scriptcraft.command.ScriptCommands.reloadConfig(player.createCommandSourceStack());
                com.scriptcraft.studio.StudioExplorer.broadcastScriptsAndLog(player.getServer());
                return;
            }
            if (!ScriptPaths.contained(packet.name)) {
                return;
            }
            if (ScriptPaths.isDisabled(packet.name)) {
                Feedback.toPlayer(player, "studio.reload_disabled", packet.name);
                return;
            }
            ScriptLoader.reloadOne(player.createCommandSourceStack(), packet.name);
            com.scriptcraft.studio.StudioExplorer.broadcastScriptsAndLog(player.getServer());
        });
        ctx.get().setPacketHandled(true);
    }
}
