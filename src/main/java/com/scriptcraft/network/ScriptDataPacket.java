package com.scriptcraft.network;

import com.scriptcraft.lang.Feedback;
import com.scriptcraft.script.ScriptLoader;
import com.scriptcraft.script.ScriptPaths;
import com.scriptcraft.studio.StudioAccess;
import com.scriptcraft.studio.StudioRooms;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Текст одного скрипта: чтение с сервера и запись обратно.
 * revision — свежесть копии, которую клиент открыл. flags — живая правка, принудительная замена, диск, устаревшая копия.
 */
public record ScriptDataPacket(String name, String text, long revision, int flags) {
    public static final int LIVE = 1;
    public static final int FORCE = 2;
    public static final int DISK = 4;
    public static final int STALE = 8;

    public static void encode(ScriptDataPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.name, 200);
        ScriptPayload.write(buf, packet.text);
        buf.writeLong(packet.revision);
        buf.writeInt(packet.flags);
    }

    public static ScriptDataPacket decode(FriendlyByteBuf buf) {
        return new ScriptDataPacket(buf.readUtf(200), ScriptPayload.read(buf), buf.readLong(), buf.readInt());
    }

    public static void handle(ScriptDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        com.scriptcraft.client.ClientPacketHandler.studioText(
                                packet.name, packet.text, packet.revision, packet.flags)));
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
                if (player != null && StudioAccess.allow(player)) {
                    StudioRooms.read(player, packet.name);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record Save(String name, String text, long revision) {
        public static void encode(Save packet, FriendlyByteBuf buf) {
            buf.writeUtf(packet.name, 200);
            ScriptPayload.write(buf, packet.text);
            buf.writeLong(packet.revision);
        }

        public static Save decode(FriendlyByteBuf buf) {
            return new Save(buf.readUtf(200), ScriptPayload.read(buf), buf.readLong());
        }

        public static void handle(Save packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player == null || !StudioAccess.allow(player)) {
                    return;
                }
                if (!StudioRooms.save(player, packet.name, packet.text, packet.revision)) {
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
