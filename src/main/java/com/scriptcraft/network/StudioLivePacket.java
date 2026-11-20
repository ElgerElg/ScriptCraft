package com.scriptcraft.network;

import com.scriptcraft.studio.StudioAccess;
import com.scriptcraft.studio.StudioRooms;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Живая правка студии: курсор, выделение и текст, пока файл открыт. */
public final class StudioLivePacket {
    private StudioLivePacket() {
    }

    /** Чужой курсор в открытом скрипте. */
    public record Peer(UUID id, String name, int line, int col, int anchorLine, int anchorCol, boolean box) {
    }

    /** Клиент -> сервер: курсор или весь текст. line < 0 — ушёл из файла. */
    public record Edit(String file, long revision, int line, int col, int anchorLine, int anchorCol, boolean box,
                       boolean text, String body) {
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
        }

        public static Edit decode(FriendlyByteBuf buf) {
            return new Edit(buf.readUtf(200), buf.readLong(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readBoolean(), buf.readBoolean(), ScriptPayload.read(buf));
        }

        public static void handle(Edit packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player != null && StudioAccess.may(player)) {
                    StudioRooms.edit(player, packet);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Сервер -> клиент: чужие курсоры и, если нужно, текст комнаты. */
    public record View(String file, long revision, boolean replace, boolean stale, boolean ack, String text, List<Peer> peers) {
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
            }
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
                        buf.readBoolean()));
            }
            return new View(file, revision, replace, stale, ack, text, peers);
        }

        public static void handle(View packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            com.scriptcraft.client.ClientPacketHandler.studioLive(packet)));
            ctx.get().setPacketHandled(true);
        }
    }
}
