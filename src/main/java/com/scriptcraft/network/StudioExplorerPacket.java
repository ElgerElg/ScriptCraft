package com.scriptcraft.network;

import com.scriptcraft.studio.StudioAccess;
import com.scriptcraft.studio.StudioExplorer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Пакеты проводника студии (папка scriptcraft/, картинки, загрузка файлов, присутствие редакторов). */
public final class StudioExplorerPacket {
    private StudioExplorerPacket() {
    }

    public enum Kind {
        LIST, MKDIR, DELETE, RENAME, TOGGLE_SCRIPT, READ_FILE, WRITE_FILE
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
            buf.writeUtf(p.path == null ? "" : p.path, 300);
            buf.writeUtf(p.extra == null ? "" : p.extra, 300);
            byte[] bytes = p.data == null ? new byte[0] : p.data;
            buf.writeByteArray(bytes);
        }

        public static Action decode(FriendlyByteBuf buf) {
            return new Action(buf.readEnum(Kind.class), buf.readUtf(300), buf.readUtf(300),
                    buf.readByteArray(StudioExplorer.MAX_FILE_BYTES + 1024));
        }

        public static void handle(Action packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player != null && StudioAccess.allow(player)) {
                    StudioExplorer.handle(player, packet);
                }
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
            for (int i = 0; i < ec && i < 300; i++) {
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
    public record FileView(String path, boolean binary, byte[] data, String renamedFrom) {
        public static void encode(FileView p, FriendlyByteBuf buf) {
            buf.writeUtf(p.path == null ? "" : p.path, 300);
            buf.writeBoolean(p.binary);
            buf.writeByteArray(p.data == null ? new byte[0] : p.data);
            buf.writeUtf(p.renamedFrom == null ? "" : p.renamedFrom, 300);
        }

        public static FileView decode(FriendlyByteBuf buf) {
            return new FileView(buf.readUtf(300), buf.readBoolean(),
                    buf.readByteArray(StudioExplorer.MAX_FILE_BYTES + 1024), buf.readUtf(300));
        }

        public static void handle(FileView packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            com.scriptcraft.client.ClientPacketHandler.studioExplorerFile(packet)));
            ctx.get().setPacketHandled(true);
        }
    }
}
