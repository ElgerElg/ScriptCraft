package com.scriptcraft.network;

import com.scriptcraft.studio.StudioAccess;
import com.scriptcraft.studio.StudioExplorer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Пакеты проводника студии (папка scriptcraft/, картинки, загрузка файлов, присутствие редакторов). */
public final class StudioExplorerPacket {
    private static final int C2S_CHUNK_BYTES = 20_000;
    private static final int S2C_CHUNK_BYTES = 512_000;
    private static final Map<String, ByteArrayOutputStream> CLIENT_FILE_CHUNKS = new ConcurrentHashMap<>();

    private StudioExplorerPacket() {
    }

    public static void clearClientBuffers() {
        CLIENT_FILE_CHUNKS.clear();
    }

    public enum Kind {
        LIST, MKDIR, DELETE, RENAME, TOGGLE_SCRIPT, READ_FILE, WRITE_FILE, WRITE_FILE_PART,
        MOVE_BATCH, COPY_BATCH, DELETE_BATCH
    }

    /** Безопасная отправка файла любого размера (до StudioExplorer.MAX_FILE_BYTES) с клиента на сервер кусками <= 20 КБ. */
    public static void sendWriteFileToServer(String path, String extra, byte[] data) {
        byte[] bytes = data == null ? new byte[0] : data;
        if (bytes.length <= C2S_CHUNK_BYTES) {
            ModNetwork.sendToServer(new Action(Kind.WRITE_FILE, path, extra, bytes));
            return;
        }
        int offset = 0;
        while (offset < bytes.length) {
            int end = Math.min(bytes.length, offset + C2S_CHUNK_BYTES);
            byte[] slice = Arrays.copyOfRange(bytes, offset, end);
            boolean last = (end >= bytes.length);
            if (last) {
                ModNetwork.sendToServer(new Action(Kind.WRITE_FILE, path, extra, slice));
            } else {
                String partTag = offset == 0 ? "$reset" : "";
                ModNetwork.sendToServer(new Action(Kind.WRITE_FILE_PART, path, partTag, slice));
            }
            offset = end;
        }
    }

    /** Безопасная отправка файла любого размера с сервера клиенту кусками <= 512 КБ. */
    public static void sendFileViewToClient(ServerPlayer player, String path, boolean binary, byte[] data, String renamedFrom) {
        if (player == null) return;
        byte[] bytes = data == null ? new byte[0] : data;
        if (bytes.length <= S2C_CHUNK_BYTES) {
            ModNetwork.sendTo(player, new FileView(path, binary, bytes, renamedFrom, 0, 1));
            return;
        }
        int total = (bytes.length + S2C_CHUNK_BYTES - 1) / S2C_CHUNK_BYTES;
        for (int i = 0; i < total; i++) {
            int from = i * S2C_CHUNK_BYTES;
            int to = Math.min(bytes.length, from + S2C_CHUNK_BYTES);
            byte[] slice = Arrays.copyOfRange(bytes, from, to);
            ModNetwork.sendTo(player, new FileView(path, binary, slice, renamedFrom, i, total));
        }
    }

    public record Entry(String name, boolean dir, long size) {
    }

    public record ActiveEditor(UUID id, String name, String path) {
    }

    /** Клиент -> сервер: действие в проводнике. */
    public record Action(Kind kind, String path, String extra, byte[] data) {
        public Action(Kind kind, String path, String extra) {
            this(kind, path, extra, new byte[0]);
        }

        public static void encode(Action p, FriendlyByteBuf buf) {
            buf.writeEnum(p.kind);
            buf.writeUtf(p.path == null ? "" : p.path, 32767);
            buf.writeUtf(p.extra == null ? "" : p.extra, 32767);
            byte[] bytes = p.data == null ? new byte[0] : p.data;
            buf.writeByteArray(bytes);
        }

        public static Action decode(FriendlyByteBuf buf) {
            return new Action(buf.readEnum(Kind.class), buf.readUtf(32767), buf.readUtf(32767),
                    buf.readByteArray(StudioExplorer.MAX_FILE_BYTES + 1024));
        }

        public static void handle(Action packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player == null) {
                    return;
                }
                if (!StudioAccess.allow(player)) {
                    if (packet.kind == Kind.READ_FILE) {
                        ModNetwork.sendTo(player, new FileView(packet.path, false, new byte[0], "$read_error", 0, 1));
                    }
                    return;
                }
                StudioExplorer.handle(player, packet);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Сервер -> клиент: список файлов текущей папки + кто какой файл сейчас правит. */
    public record DirView(String dir, List<Entry> entries, List<ActiveEditor> editors) {
        public static void encode(DirView p, FriendlyByteBuf buf) {
            buf.writeUtf(p.dir == null ? "" : p.dir, 300);
            buf.writeVarInt(p.entries.size());
            for (Entry e : p.entries) {
                buf.writeUtf(e.name, 200);
                buf.writeBoolean(e.dir);
                buf.writeLong(e.size);
            }
            buf.writeVarInt(p.editors.size());
            for (ActiveEditor ed : p.editors) {
                buf.writeUUID(ed.id);
                buf.writeUtf(ed.name, 64);
                buf.writeUtf(ed.path, 300);
            }
        }

        public static DirView decode(FriendlyByteBuf buf) {
            String dir = buf.readUtf(300);
            int ec = buf.readVarInt();
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < ec && i < 500; i++) {
                entries.add(new Entry(buf.readUtf(200), buf.readBoolean(), buf.readLong()));
            }
            int pc = buf.readVarInt();
            List<ActiveEditor> editors = new ArrayList<>();
            for (int i = 0; i < pc && i < 64; i++) {
                editors.add(new ActiveEditor(buf.readUUID(), buf.readUtf(64), buf.readUtf(300)));
            }
            return new DirView(dir, entries, editors);
        }

        public static void handle(DirView packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            com.scriptcraft.client.ClientPacketHandler.studioExplorerDir(packet)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Сервер -> клиент: содержимое файла (текст или картинка) либо уведомление о переименовании/удалении. */
    public record FileView(String path, boolean binary, byte[] data, String renamedFrom, int partIndex, int partTotal) {
        public FileView(String path, boolean binary, byte[] data, String renamedFrom) {
            this(path, binary, data, renamedFrom, 0, 1);
        }

        public static void encode(FileView p, FriendlyByteBuf buf) {
            buf.writeUtf(p.path == null ? "" : p.path, 300);
            buf.writeBoolean(p.binary);
            buf.writeByteArray(p.data == null ? new byte[0] : p.data);
            buf.writeUtf(p.renamedFrom == null ? "" : p.renamedFrom, 300);
            buf.writeVarInt(p.partIndex);
            buf.writeVarInt(p.partTotal);
        }

        public static FileView decode(FriendlyByteBuf buf) {
            return new FileView(buf.readUtf(300), buf.readBoolean(),
                    buf.readByteArray(StudioExplorer.MAX_FILE_BYTES + 1024), buf.readUtf(300),
                    buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(FileView packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                        int total = Math.max(1, packet.partTotal);
                        String key = (packet.renamedFrom == null ? "" : packet.renamedFrom) + ":" + packet.path;
                        if (total > 1) {
                            if (packet.partIndex <= 0) {
                                CLIENT_FILE_CHUNKS.put(key, new ByteArrayOutputStream(packet.data.length * Math.min(total, 8)));
                            }
                            ByteArrayOutputStream baos = CLIENT_FILE_CHUNKS.get(key);
                            if (baos == null) {
                                return;
                            }
                            if (baos.size() + packet.data.length <= StudioExplorer.MAX_FILE_BYTES) {
                                baos.write(packet.data, 0, packet.data.length);
                            } else {
                                CLIENT_FILE_CHUNKS.remove(key);
                                return;
                            }
                            if (packet.partIndex + 1 < total) {
                                return;
                            }
                            CLIENT_FILE_CHUNKS.remove(key);
                            FileView assembled = new FileView(packet.path, packet.binary, baos.toByteArray(), packet.renamedFrom, 0, 1);
                            com.scriptcraft.client.ClientPacketHandler.studioExplorerFile(assembled);
                            return;
                        }
                        CLIENT_FILE_CHUNKS.remove(key);
                        com.scriptcraft.client.ClientPacketHandler.studioExplorerFile(packet);
                    }));
            ctx.get().setPacketHandled(true);
        }
    }
}
