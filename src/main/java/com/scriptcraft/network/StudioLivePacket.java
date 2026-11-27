package com.scriptcraft.network;

import com.scriptcraft.studio.StudioAccess;
import com.scriptcraft.studio.StudioRooms;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Живая правка студии: курсор, выделение и текст, пока файл открыт (с чанковой передачей для больших файлов). */
public final class StudioLivePacket {
    private static final Map<String, StringBuilder> SERVER_EDIT_CHUNKS = new ConcurrentHashMap<>();
    private static final Map<String, StringBuilder> CLIENT_VIEW_CHUNKS = new ConcurrentHashMap<>();

    private StudioLivePacket() {
    }

    public static void clearPlayerBuffers(UUID playerId) {
        if (playerId == null) return;
        String prefix = playerId + ":";
        SERVER_EDIT_CHUNKS.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public static void clearClientBuffers() {
        CLIENT_VIEW_CHUNKS.clear();
    }

    /** Чужой курсор в открытом скрипте. color == 0 означает авто-цвет по нику. */
    public record Peer(UUID id, String name, int line, int col, int anchorLine, int anchorCol, boolean box, int color) {
    }

    /** Клиент -> сервер: курсор или весь текст. line < 0 — ушёл из файла. */
    public record Edit(String file, long revision, int line, int col, int anchorLine, int anchorCol, boolean box,
                       boolean text, String body, int color, int partIndex, int partTotal) {
        public Edit(String file, long revision, int line, int col, int anchorLine, int anchorCol, boolean box,
                    boolean text, String body, int color) {
            this(file, revision, line, col, anchorLine, anchorCol, box, text, body, color, 0, 1);
        }

        public static void sendToServerChunked(String file, long revision, int line, int col,
                                               int anchorLine, int anchorCol, boolean box,
                                               boolean text, String body, int color) {
            if (!text || body == null || body.isEmpty()) {
                ModNetwork.sendToServer(new Edit(file, revision, line, col, anchorLine, anchorCol, box, text, "", color, 0, 1));
                return;
            }
            List<String> parts = ScriptPayload.splitUtf8(body, ScriptPayload.MAX_C2S_SLICE_BYTES);
            int total = parts.size();
            for (int i = 0; i < total; i++) {
                ModNetwork.sendToServer(new Edit(file, revision, line, col, anchorLine, anchorCol, box,
                        true, parts.get(i), color, i, total));
            }
        }

        public static void encode(Edit packet, FriendlyByteBuf buf) {
            buf.writeUtf(packet.file, 200);
            buf.writeLong(packet.revision);
            buf.writeInt(packet.line);
            buf.writeInt(packet.col);
            buf.writeInt(packet.anchorLine);
            buf.writeInt(packet.anchorCol);
            buf.writeBoolean(packet.box);
            buf.writeBoolean(packet.text);
            ScriptPayload.write(buf, packet.body == null ? "" : packet.body);
            buf.writeInt(packet.color);
            buf.writeVarInt(packet.partIndex);
            buf.writeVarInt(packet.partTotal);
        }

        public static Edit decode(FriendlyByteBuf buf) {
            return new Edit(buf.readUtf(200), buf.readLong(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readBoolean(), buf.readBoolean(), ScriptPayload.read(buf), buf.readInt(),
                    buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(Edit packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player == null || !StudioAccess.may(player)) {
                    return;
                }
                String key = player.getUUID() + ":" + packet.file;
                int total = Math.max(1, packet.partTotal);
                if (packet.text && total > 1) {
                    if (packet.partIndex <= 0) {
                        SERVER_EDIT_CHUNKS.put(key, new StringBuilder(packet.body.length() * Math.min(total, 16)));
                    }
                    StringBuilder sb = SERVER_EDIT_CHUNKS.get(key);
                    if (sb == null) {
                        return;
                    }
                    sb.append(packet.body);
                    if (sb.length() > ScriptPayload.MAX_BYTES
                            || sb.toString().getBytes(StandardCharsets.UTF_8).length > ScriptPayload.MAX_BYTES) {
                        SERVER_EDIT_CHUNKS.remove(key);
                        return;
                    }
                    if (packet.partIndex + 1 < total) {
                        return;
                    }
                    SERVER_EDIT_CHUNKS.remove(key);
                    Edit assembled = new Edit(packet.file, packet.revision, packet.line, packet.col,
                            packet.anchorLine, packet.anchorCol, packet.box, true, sb.toString(), packet.color, 0, 1);
                    StudioRooms.edit(player, assembled);
                    return;
                }
                if (packet.line < 0 || packet.text) {
                    SERVER_EDIT_CHUNKS.remove(key);
                }
                StudioRooms.edit(player, packet);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Сервер -> клиент: чужие курсоры и, если нужно, текст комнаты. */
    public record View(String file, long revision, boolean replace, boolean stale, boolean ack,
                       String text, List<Peer> peers, int partIndex, int partTotal) {
        public View(String file, long revision, boolean replace, boolean stale, boolean ack, String text, List<Peer> peers) {
            this(file, revision, replace, stale, ack, text, peers, 0, 1);
        }

        public static void sendToClientChunked(ServerPlayer player, String file, long revision,
                                               boolean replace, boolean stale, boolean ack,
                                               String text, List<Peer> peers) {
            if (player == null) return;
            if (!replace || text == null || text.isEmpty()) {
                ModNetwork.sendTo(player, new View(file, revision, replace, stale, ack, text == null ? "" : text, peers, 0, 1));
                return;
            }
            List<String> parts = ScriptPayload.splitUtf8(text, ScriptPayload.MAX_S2C_SLICE_BYTES);
            int total = parts.size();
            if (total <= 1) {
                ModNetwork.sendTo(player, new View(file, revision, true, stale, ack, parts.get(0), peers, 0, 1));
                return;
            }
            for (int i = 0; i < total; i++) {
                List<Peer> slicePeers = (i + 1 == total) ? peers : List.of();
                ModNetwork.sendTo(player, new View(file, revision, true, stale, ack, parts.get(i), slicePeers, i, total));
            }
        }

        public static void encode(View packet, FriendlyByteBuf buf) {
            buf.writeUtf(packet.file, 200);
            buf.writeLong(packet.revision);
            buf.writeBoolean(packet.replace);
            buf.writeBoolean(packet.stale);
            buf.writeBoolean(packet.ack);
            ScriptPayload.write(buf, packet.text == null ? "" : packet.text);
            buf.writeVarInt(packet.peers.size());
            for (Peer peer : packet.peers) {
                buf.writeUUID(peer.id);
                buf.writeUtf(peer.name, 64);
                buf.writeInt(peer.line);
                buf.writeInt(peer.col);
                buf.writeInt(peer.anchorLine);
                buf.writeInt(peer.anchorCol);
                buf.writeBoolean(peer.box);
                buf.writeInt(peer.color);
            }
            buf.writeVarInt(packet.partIndex);
            buf.writeVarInt(packet.partTotal);
        }

        public static View decode(FriendlyByteBuf buf) {
            String file = buf.readUtf(200);
            long revision = buf.readLong();
            boolean replace = buf.readBoolean();
            boolean stale = buf.readBoolean();
            boolean ack = buf.readBoolean();
            String text = ScriptPayload.read(buf);
            int count = buf.readVarInt();
            List<Peer> peers = new ArrayList<>();
            for (int i = 0; i < count && i < 32; i++) {
                peers.add(new Peer(buf.readUUID(), buf.readUtf(64), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                        buf.readBoolean(), buf.readInt()));
            }
            int partIndex = buf.readVarInt();
            int partTotal = buf.readVarInt();
            return new View(file, revision, replace, stale, ack, text, peers, partIndex, partTotal);
        }

        public static void handle(View packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                        int total = Math.max(1, packet.partTotal);
                        if (packet.replace && total > 1) {
                            if (packet.partIndex <= 0) {
                                CLIENT_VIEW_CHUNKS.put(packet.file, new StringBuilder(packet.text.length() * Math.min(total, 16)));
                            }
                            StringBuilder sb = CLIENT_VIEW_CHUNKS.get(packet.file);
                            if (sb == null) {
                                return;
                            }
                            if (sb.length() + packet.text.length() <= ScriptPayload.MAX_BYTES) {
                                sb.append(packet.text);
                            } else {
                                CLIENT_VIEW_CHUNKS.remove(packet.file);
                                return;
                            }
                            if (packet.partIndex + 1 < total) {
                                return;
                            }
                            CLIENT_VIEW_CHUNKS.remove(packet.file);
                            View assembled = new View(packet.file, packet.revision, true, packet.stale, packet.ack,
                                    sb.toString(), packet.peers, 0, 1);
                            com.scriptcraft.client.ClientPacketHandler.studioLive(assembled);
                            return;
                        }
                        if (packet.replace) {
                            CLIENT_VIEW_CHUNKS.remove(packet.file);
                        }
                        com.scriptcraft.client.ClientPacketHandler.studioLive(packet);
                    }));
            ctx.get().setPacketHandled(true);
        }
    }
}
