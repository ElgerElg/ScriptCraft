package com.scriptcraft.network;

import com.scriptcraft.lang.Feedback;
import com.scriptcraft.script.ScriptLoader;
import com.scriptcraft.script.ScriptPaths;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Supplier;

/** Текст одного скрипта: чтение с сервера и запись обратно. */
public record ScriptDataPacket(String name, String text) {
    public static void encode(ScriptDataPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.name, 200);
        ScriptPayload.write(buf, packet.text);
    }

    public static ScriptDataPacket decode(FriendlyByteBuf buf) {
        return new ScriptDataPacket(buf.readUtf(200), ScriptPayload.read(buf));
    }

    public static void handle(ScriptDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        com.scriptcraft.client.ClientPacketHandler.studioText(packet.name, packet.text)));
        ctx.get().setPacketHandled(true);
    }

    public record Read(String name) {
        public static void encode(Read packet, FriendlyByteBuf buf) {
            buf.writeUtf(packet.name, 200);
        }

        public static Read decode(FriendlyByteBuf buf) {
            return new Read(buf.readUtf(200));
        }

        public static void handle(Read packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player == null || !player.hasPermissions(2) || !ScriptPaths.contained(packet.name)) {
                    return;
                }
                try {
                    String text = Files.readString(ScriptPaths.resolve(packet.name), StandardCharsets.UTF_8);
                    ModNetwork.sendTo(player, new ScriptDataPacket(packet.name, text));
                } catch (IOException e) {
                    Feedback.toPlayer(player, "studio.read_failed", packet.name);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record Save(String name, String text) {
        public static void encode(Save packet, FriendlyByteBuf buf) {
            buf.writeUtf(packet.name, 200);
            ScriptPayload.write(buf, packet.text);
        }

        public static Save decode(FriendlyByteBuf buf) {
            return new Save(buf.readUtf(200), ScriptPayload.read(buf));
        }

        public static void handle(Save packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player == null || !player.hasPermissions(2)) {
                    return;
                }
                if (!ScriptPaths.contained(packet.name) || packet.text.getBytes(StandardCharsets.UTF_8).length > ScriptPayload.MAX_BYTES) {
                    Feedback.toPlayer(player, "studio.bad_name", packet.name);
                    return;
                }
                try {
                    ScriptPaths.write(packet.name, packet.text);
                } catch (IOException e) {
                    Feedback.toPlayer(player, "studio.save_failed", e.getMessage());
                    return;
                }
                if (ScriptPaths.isDisabled(packet.name)) {
                    Feedback.toPlayer(player, "studio.saved_disabled", packet.name);
                } else {
                    ScriptLoader.reloadOne(player.createCommandSourceStack(), packet.name);
                }
                ModNetwork.sendTo(player, ScriptListPacket.ofServer());
                ModNetwork.sendTo(player, LogPacket.from(ScriptLoader.journal(), false));
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
