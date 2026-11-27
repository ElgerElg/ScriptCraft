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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Текст одного скрипта: чтение с сервера и запись обратно (поддерживает чанковую передачу больших скриптов).
 * revision — свежесть копии, которую клиент открыл. flags — живая правка, принудительная замена, диск, устаревшая копия.
 */
public record ScriptDataPacket(String name, String text, long revision, int flags) {
    public static final int LIVE = 1;
    public static final int FORCE = 2;
    public static final int DISK = 4;
    public static final int STALE = 8;
    public static final int CHUNK_PART = 16;
    public static final int CHUNK_FINAL = 32;
    public static final int READ_ERROR = 64;

    private static final Map<String, StringBuilder> CLIENT_CHUNKS = new ConcurrentHashMap<>();
    private static final Map<String, StringBuilder> SERVER_SAVE_CHUNKS = new ConcurrentHashMap<>();

    public static void clearPlayerBuffers(UUID playerId) {
        if (playerId == null) return;
        String prefix = playerId + ":";
        SERVER_SAVE_CHUNKS.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public static void clearClientBuffers() {
        CLIENT_CHUNKS.clear();
    }

    /** Безопасная отправка скрипта любого размера с сервера клиенту (автоматически режет на S2C-чанки). */
    public static void sendToClient(ServerPlayer player, String name, String text, long revision, int flags) {
        if (player == null) return;
        List<String> parts = ScriptPayload.splitUtf8(text, ScriptPayload.MAX_S2C_SLICE_BYTES);
        if (parts.size() <= 1) {
            ModNetwork.sendTo(player, new ScriptDataPacket(name, parts.get(0), revision, flags));
            return;
        }
        int baseFlags = flags & ~(CHUNK_PART | CHUNK_FINAL);
        for (int i = 0; i < parts.size(); i++) {
            int f = baseFlags | (i + 1 < parts.size() ? CHUNK_PART : CHUNK_FINAL);
            ModNetwork.sendTo(player, new ScriptDataPacket(name, parts.get(i), revision, f));
        }
    }

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
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    if ((packet.flags & CHUNK_PART) != 0) {
                        StringBuilder sb = CLIENT_CHUNKS.computeIfAbsent(packet.name, k -> new StringBuilder());
                        if (sb.length() + packet.text.length() <= ScriptPayload.MAX_BYTES) {
                            sb.append(packet.text);
                        } else {
                            CLIENT_CHUNKS.remove(packet.name);
                        }
                        return;
                    }
                    String fullText = packet.text;
                    if ((packet.flags & CHUNK_FINAL) != 0) {
                        StringBuilder sb = CLIENT_CHUNKS.remove(packet.name);
                        if (sb != null) {
                            sb.append(packet.text);
                            fullText = sb.toString();
                        }
                    } else {
                        CLIENT_CHUNKS.remove(packet.name);
                    }
                    int cleanFlags = packet.flags & ~(CHUNK_PART | CHUNK_FINAL);
                    com.scriptcraft.client.ClientPacketHandler.studioText(
                            packet.name, fullText, packet.revision, cleanFlags);
                }));
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
                if (player == null) {
                    return;
                }
                if (!StudioAccess.allow(player)) {
                    sendToClient(player, packet.name, "", 0L, READ_ERROR);
                    return;
                }
                StudioRooms.read(player, packet.name);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record Save(String name, String text, long revision, int partIndex, int partTotal) {
        public Save(String name, String text, long revision) {
            this(name, text, revision, 0, 1);
        }

        /** Безопасная отправка сохранения любого размера с клиента на сервер (режет на C2S-куски <= 20 КБ). */
        public static void sendToServerChunked(String name, String text, long revision) {
            List<String> parts = ScriptPayload.splitUtf8(text, ScriptPayload.MAX_C2S_SLICE_BYTES);
            int total = parts.size();
            for (int i = 0; i < total; i++) {
                ModNetwork.sendToServer(new Save(name, parts.get(i), revision, i, total));
            }
        }

        public static void encode(Save packet, FriendlyByteBuf buf) {
            buf.writeUtf(packet.name, 200);
            ScriptPayload.write(buf, packet.text);
            buf.writeLong(packet.revision);
            buf.writeVarInt(packet.partIndex);
            buf.writeVarInt(packet.partTotal);
        }

        public static Save decode(FriendlyByteBuf buf) {
            return new Save(buf.readUtf(200), ScriptPayload.read(buf), buf.readLong(),
                    buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(Save packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player == null || !StudioAccess.allow(player)) {
                    return;
                }
                String key = player.getUUID() + ":" + packet.name;
                String fullText;
                int total = Math.max(1, packet.partTotal);
                if (total > 1) {
                    if (packet.partIndex <= 0) {
                        SERVER_SAVE_CHUNKS.put(key, new StringBuilder(packet.text.length() * Math.min(total, 16)));
                    }
                    StringBuilder sb = SERVER_SAVE_CHUNKS.get(key);
                    if (sb == null) {
                        Feedback.toPlayer(player, "studio.save_failed", "missing chunk 0");
                        return;
                    }
                    sb.append(packet.text);
                    if (sb.length() > ScriptPayload.MAX_BYTES
                            || sb.toString().getBytes(StandardCharsets.UTF_8).length > ScriptPayload.MAX_BYTES) {
                        SERVER_SAVE_CHUNKS.remove(key);
                        Feedback.toPlayer(player, "studio.too_large", packet.name, ScriptPayload.MAX_BYTES / (1024 * 1024));
                        return;
                    }
                    if (packet.partIndex + 1 < total) {
                        return;
                    }
                    SERVER_SAVE_CHUNKS.remove(key);
                    fullText = sb.toString();
                } else {
                    SERVER_SAVE_CHUNKS.remove(key);
                    fullText = packet.text;
                }
                if (!StudioRooms.save(player, packet.name, fullText, packet.revision)) {
                    return;
                }
                if (ScriptPaths.isDisabled(packet.name)) {
                    Feedback.toPlayer(player, "studio.saved_disabled", packet.name);
                } else {
                    ScriptLoader.reloadOne(player.createCommandSourceStack(), packet.name);
                }
                com.scriptcraft.studio.StudioExplorer.broadcastScriptsAndLog(player.getServer());
                com.scriptcraft.studio.StudioExplorer.broadcastDirs(player.getServer());
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
