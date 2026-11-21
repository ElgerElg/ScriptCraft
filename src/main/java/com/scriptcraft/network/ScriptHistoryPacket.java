package com.scriptcraft.network;

import com.scriptcraft.config.ScriptCraftConfig;
import com.scriptcraft.lang.Feedback;
import com.scriptcraft.script.ScriptLoader;
import com.scriptcraft.script.ScriptPaths;
import com.scriptcraft.studio.ScriptHistory;
import com.scriptcraft.studio.StudioAccess;
import com.scriptcraft.studio.StudioExplorer;
import com.scriptcraft.studio.StudioRooms;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Пакеты истории версий скрипта (список, превью, откат, удаление). */
public final class ScriptHistoryPacket {
    public static final byte LIST = 0;
    public static final byte PREVIEW = 1;
    public static final byte RESTORE = 2;
    public static final byte DELETE = 3;

    private ScriptHistoryPacket() {
    }

    public record Request(byte action, String scriptName, String snapshotId) {
        public static void encode(Request p, FriendlyByteBuf buf) {
            buf.writeByte(p.action);
            buf.writeUtf(p.scriptName == null ? "" : p.scriptName, 200);
            buf.writeUtf(p.snapshotId == null ? "" : p.snapshotId, 128);
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request(buf.readByte(), buf.readUtf(200), buf.readUtf(128));
        }

        public static void handle(Request packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player == null || !StudioAccess.allow(player)) {
                    return;
                }
                String name = packet.scriptName;
                if (!ScriptPaths.contained(name)) {
                    return;
                }
                switch (packet.action) {
                    case LIST -> sendList(player, name);
                    case PREVIEW -> {
                        String text = ScriptHistory.read(name, packet.snapshotId);
                        if (text != null) {
                            ModNetwork.sendTo(player, new PreviewView(name, packet.snapshotId, text));
                        }
                    }
                    case RESTORE -> {
                        String text = ScriptHistory.read(name, packet.snapshotId);
                        if (text == null) {
                            return;
                        }
                        if (StudioRooms.forceRestore(player, name, text)) {
                            if (!ScriptPaths.isDisabled(name)) {
                                ScriptLoader.reloadOne(player.createCommandSourceStack(), name);
                            }
                            Feedback.toPlayer(player, "studio.history.restored", name);
                            StudioExplorer.broadcastScriptsAndLog(player.getServer());
                            StudioExplorer.broadcastDirs(player.getServer());
                            sendList(player, name);
                        }
                    }
                    case DELETE -> {
                        ScriptHistory.delete(name, packet.snapshotId);
                        sendList(player, name);
                    }
                    default -> {
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }

        private static void sendList(ServerPlayer player, String name) {
            List<ScriptHistory.Entry> raw = ScriptHistory.list(name);
            List<Entry> entries = new ArrayList<>(raw.size());
            for (ScriptHistory.Entry e : raw) {
                entries.add(new Entry(e.id(), e.timestampMs(), e.author(), e.bytes(), e.lines()));
            }
            ModNetwork.sendTo(player, new ListView(name, ScriptCraftConfig.scriptHistoryRetentionDays(), entries));
        }
    }

    public record Entry(String id, long timestampMs, String author, int bytes, int lines) {
    }

    public record ListView(String scriptName, int retentionDays, List<Entry> entries) {
        public static void encode(ListView p, FriendlyByteBuf buf) {
            buf.writeUtf(p.scriptName, 200);
            buf.writeVarInt(p.retentionDays);
            buf.writeVarInt(p.entries.size());
            for (Entry e : p.entries) {
                buf.writeUtf(e.id, 128);
                buf.writeLong(e.timestampMs);
                buf.writeUtf(e.author, 32);
                buf.writeVarInt(e.bytes);
                buf.writeVarInt(e.lines);
            }
        }

        public static ListView decode(FriendlyByteBuf buf) {
            String name = buf.readUtf(200);
            int days = buf.readVarInt();
            int n = Math.min(200, buf.readVarInt());
            List<Entry> entries = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                entries.add(new Entry(buf.readUtf(128), buf.readLong(), buf.readUtf(32), buf.readVarInt(), buf.readVarInt()));
            }
            return new ListView(name, days, entries);
        }

        public static void handle(ListView packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.scriptcraft.client.ClientPacketHandler.historyList(packet)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record PreviewView(String scriptName, String snapshotId, String text) {
        public static void encode(PreviewView p, FriendlyByteBuf buf) {
            buf.writeUtf(p.scriptName, 200);
            buf.writeUtf(p.snapshotId, 128);
            ScriptPayload.write(buf, p.text);
        }

        public static PreviewView decode(FriendlyByteBuf buf) {
            return new PreviewView(buf.readUtf(200), buf.readUtf(128), ScriptPayload.read(buf));
        }

        public static void handle(PreviewView packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.scriptcraft.client.ClientPacketHandler.historyPreview(packet)));
            ctx.get().setPacketHandled(true);
        }
    }
}
