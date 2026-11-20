package com.scriptcraft.studio;

import com.scriptcraft.config.ScriptCraftConfig;
import com.scriptcraft.lang.Feedback;
import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.network.ScriptDataPacket;
import com.scriptcraft.network.ScriptPayload;
import com.scriptcraft.network.StudioExplorerPacket;
import com.scriptcraft.network.StudioLivePacket;
import com.scriptcraft.script.ScriptPaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Комната одного скрипта: общая копия, номер ревизии и курсоры.
 * Ревизия — это свежесть. Правка или сохранение принимаются, если номер совпал
 * или это тот же человек, чья правка уже последняя (его пакеты могли обогнать ответ).
 * Чужой более новый текст не затирается.
 */
public final class StudioRooms {
    private static final Map<String, Room> ROOMS = new HashMap<>();
    private static Boolean lastLive;

    private StudioRooms() {
    }

    public static void clear() {
        ROOMS.clear();
        lastLive = null;
        StudioExplorer.clear();
    }

    /** Все активные редакторы по комнатам (путь относительно scriptcraft/, напр. "scripts/welcome.sc"). */
    public static List<StudioExplorerPacket.ActiveEditor> activeEditors() {
        List<StudioExplorerPacket.ActiveEditor> out = new ArrayList<>();
        for (Map.Entry<String, Room> entry : ROOMS.entrySet()) {
            String fullPath = "scripts/" + entry.getKey();
            for (Seat seat : entry.getValue().peers.values()) {
                out.add(new StudioExplorerPacket.ActiveEditor(seat.id, seat.name, fullPath));
            }
        }
        return out;
    }

    /** Перенос комнаты при переименовании или включении/выключении скрипта. */
    public static void renameRoom(String oldName, String newName) {
        Room room = ROOMS.remove(oldName);
        if (room != null) {
            ROOMS.put(newName, room);
        }
    }

    /** При изменении config.toml (вручную или через /sc reload config) уведомляет открытые студии без перезапуска. */
    public static void onConfigChange(MinecraftServer server) {
        if (server == null) {
            return;
        }
        boolean live = ScriptCraftConfig.studioLiveEdit();
        if (lastLive != null && lastLive == live) {
            return;
        }
        lastLive = live;
        if (!live) {
            ROOMS.clear();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ModNetwork.sendTo(player, new StudioLivePacket.View("", 0L, false, false, false, "", List.of()));
            }
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (StudioAccess.may(player)) {
                ModNetwork.sendTo(player, new StudioLivePacket.View("", 1L, false, false, false, "", List.of()));
            }
        }
    }

    public static void onQuit(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUUID();
        List<String> touched = new ArrayList<>();
        for (Map.Entry<String, Room> entry : ROOMS.entrySet()) {
            if (entry.getValue().peers.remove(id) != null) {
                touched.add(entry.getKey());
            }
        }
        MinecraftServer server = player.getServer();
        if (server != null && !touched.isEmpty()) {
            for (String name : touched) {
                Room room = ROOMS.get(name);
                if (room != null) {
                    broadcastPeers(server, name, room);
                }
            }
            StudioExplorer.broadcastDirs(server);
        }
        StudioExplorer.onQuit(player);
        sweep();
    }

    /** Файл удалён: комната закрывается, у остальных текст пропадает. */
    public static void forget(MinecraftServer server, String name, UUID except) {
        Room room = ROOMS.remove(name);
        if (room == null || server == null) {
            return;
        }
        for (Seat seat : room.peers.values()) {
            if (seat.id.equals(except)) {
                continue;
            }
            ServerPlayer other = server.getPlayerList().getPlayer(seat.id);
            if (other == null) {
                continue;
            }
            Feedback.toPlayer(other, "studio.deleted", name);
            ModNetwork.sendTo(other, new ScriptDataPacket(name, "", 0, ScriptDataPacket.FORCE | ScriptDataPacket.DISK));
        }
    }

    public static void read(ServerPlayer player, String name) {
        if (!ScriptPaths.contained(name)) {
            return;
        }
        try {
            if (!Files.exists(ScriptPaths.resolve(name))) {
                Feedback.toPlayer(player, "studio.read_failed", name);
                return;
            }
            String disk = diskText(name);
            long stamp = ScriptPaths.modified(name);
            boolean live = ScriptCraftConfig.studioLiveEdit();
            if (!live) {
                ROOMS.remove(name);
                ModNetwork.sendTo(player, new ScriptDataPacket(name, disk, stamp, ScriptDataPacket.DISK));
                return;
            }
            Room room = ROOMS.get(name);
            if (room == null) {
                room = room(name, disk, stamp);
            }
            int flags = ScriptDataPacket.LIVE;
            if (room.text.equals(room.saved)) {
                flags |= ScriptDataPacket.DISK;
            }
            ModNetwork.sendTo(player, new ScriptDataPacket(name, room.text, room.revision, flags));
            MinecraftServer server = player.getServer();
            if (server != null) {
                broadcastPeers(server, name, room);
            }
        } catch (IOException e) {
            Feedback.toPlayer(player, "studio.read_failed", name);
        }
    }

    /**
     * Записать файл, если клиент держал последнюю копию.
     *
     * @return true, если файл записан и скрипт можно перезагрузить
     */
    public static boolean save(ServerPlayer player, String name, String text, long revision) {
        text = norm(text);
        if (!ScriptPaths.contained(name) || text.getBytes(StandardCharsets.UTF_8).length > ScriptPayload.MAX_BYTES) {
            Feedback.toPlayer(player, "studio.bad_name", name);
            return false;
        }
        boolean live = ScriptCraftConfig.studioLiveEdit();
        try {
            String disk = diskText(name);
            long stamp = ScriptPaths.modified(name);
            if (!live) {
                ROOMS.remove(name);
            }
            Room room = live ? ROOMS.get(name) : null;
            if (live && room == null) {
                room = room(name, disk, stamp);
            }
            if (!fresh(room, stamp, player, revision)) {
                long rev = room == null ? stamp : room.revision;
                String current = room == null ? disk : room.text;
                int flags = ScriptDataPacket.FORCE | ScriptDataPacket.STALE;
                if (live) {
                    flags |= ScriptDataPacket.LIVE;
                }
                if (room == null || room.text.equals(room.saved)) {
                    flags |= ScriptDataPacket.DISK;
                }
                ModNetwork.sendTo(player, new ScriptDataPacket(name, current, rev, flags));
                return false;
            }
            ScriptPaths.write(name, text);
            long now = ScriptPaths.modified(name);
            if (room != null) {
                room.text = text;
                room.saved = text;
                room.lastEditor = player.getUUID();
                room.revision = room.revision + 1;
                room.rememberRevision();
                now = room.revision;
                tell(player.getServer(), name, room, player.getUUID(), text, now, true);
            } else {
                ModNetwork.sendTo(player, new ScriptDataPacket(name, text, now, ScriptDataPacket.FORCE | ScriptDataPacket.DISK));
            }
            return true;
        } catch (IOException e) {
            Feedback.toPlayer(player, "studio.save_failed", e.getMessage() == null ? "" : e.getMessage());
            return false;
        }
    }

    public static void edit(ServerPlayer player, StudioLivePacket.Edit edit) {
        if (!ScriptCraftConfig.studioLiveEdit() || !ScriptPaths.contained(edit.file())) {
            return;
        }
        Room room = ROOMS.get(edit.file());
        if (edit.line() < 0) {
            if (room == null) {
                return;
            }
            boolean removed = room.peers.remove(player.getUUID()) != null;
            MinecraftServer server = player.getServer();
            if (server != null) {
                broadcastPeers(server, edit.file(), room);
                if (removed) {
                    StudioExplorer.broadcastDirs(server);
                }
            }
            sweep();
            return;
        }
        if (room == null) {
            if (!Files.exists(ScriptPaths.resolve(edit.file()))) {
                return;
            }
            try {
                room = room(edit.file(), diskText(edit.file()), ScriptPaths.modified(edit.file()));
            } catch (IOException e) {
                return;
            }
        }
        boolean joined = !room.peers.containsKey(player.getUUID());
        seat(room, player, edit);
        MinecraftServer server = player.getServer();
        if (joined && server != null) {
            StudioExplorer.broadcastDirs(server);
        }
        if (!edit.text()) {
            if (server != null) {
                boolean behind = edit.revision() != room.revision;
                for (UUID id : listeners(room, player.getUUID())) {
                    ServerPlayer other = server.getPlayerList().getPlayer(id);
                    if (other != null) {
                        boolean syncSelf = behind && other.getUUID().equals(player.getUUID());
                        sendView(other, edit.file(), room, syncSelf, false, false);
                    }
                }
            }
            return;
        }
        String body = norm(edit.body());
        if (body.getBytes(StandardCharsets.UTF_8).length > ScriptPayload.MAX_BYTES) {
            return;
        }
        String nextText = body;
        boolean mergedDiffered = false;
        if (!fresh(room, room.revision, player, edit.revision())) {
            String base = room.revTexts.get(edit.revision());
            if (base != null) {
                nextText = merge3Way(base, room.text, body);
                mergedDiffered = !nextText.equals(body);
            } else {
                sendView(player, edit.file(), room, true, true, true);
                if (server != null) {
                    broadcastPeers(server, edit.file(), room);
                }
                return;
            }
        }
        boolean changed = !nextText.equals(room.text);
        if (changed) {
            shiftOtherSeats(room, player.getUUID(), room.text, nextText);
            room.text = nextText;
            room.lastEditor = player.getUUID();
            room.revision = room.revision + 1;
            room.rememberRevision();
        }
        if (server == null) {
            return;
        }
        for (UUID id : listeners(room, player.getUUID())) {
            ServerPlayer other = server.getPlayerList().getPlayer(id);
            if (other == null) {
                continue;
            }
            boolean self = other.getUUID().equals(player.getUUID());
            boolean replace = changed && (!self || mergedDiffered);
            sendView(other, edit.file(), room, replace, false, self);
        }
    }

    private static boolean fresh(Room room, long stamp, ServerPlayer player, long revision) {
        if (room == null) {
            return revision == stamp || (stamp == 0 && revision == 0);
        }
        if (revision == room.revision) {
            return true;
        }
        return room.lastEditor != null && room.lastEditor.equals(player.getUUID());
    }

    private static void tell(MinecraftServer server, String name, Room room, UUID saver, String text, long revision, boolean disk) {
        if (server == null) {
            return;
        }
        int base = ScriptDataPacket.FORCE | ScriptDataPacket.LIVE;
        if (disk) {
            base |= ScriptDataPacket.DISK;
        }
        for (UUID id : listeners(room, saver)) {
            ServerPlayer other = server.getPlayerList().getPlayer(id);
            if (other == null) {
                continue;
            }
            int flags = base;
            if (!other.getUUID().equals(saver)) {
                flags |= ScriptDataPacket.STALE;
            }
            ModNetwork.sendTo(other, new ScriptDataPacket(name, text, revision, flags));
        }
        broadcastPeers(server, name, room);
    }

    private static void broadcastPeers(MinecraftServer server, String file, Room room) {
        for (UUID id : listeners(room, null)) {
            ServerPlayer other = server.getPlayerList().getPlayer(id);
            if (other != null) {
                sendView(other, file, room, false, false, false);
            }
        }
    }

    private static void sendView(ServerPlayer player, String file, Room room, boolean replace, boolean stale, boolean ack) {
        List<StudioLivePacket.Peer> peers = new ArrayList<>();
        for (Seat seat : room.peers.values()) {
            if (seat.id.equals(player.getUUID()) || peers.size() >= 32) {
                continue;
            }
            peers.add(new StudioLivePacket.Peer(seat.id, seat.name, seat.line, seat.col, seat.anchorLine, seat.anchorCol, seat.box));
        }
        String text = replace ? room.text : "";
        ModNetwork.sendTo(player, new StudioLivePacket.View(file, room.revision, replace, stale, ack, text, peers));
    }

    private static List<UUID> listeners(Room room, UUID extra) {
        List<UUID> ids = new ArrayList<>();
        for (Seat seat : room.peers.values()) {
            ids.add(seat.id);
        }
        if (extra != null && !ids.contains(extra)) {
            ids.add(extra);
        }
        return ids;
    }

    private static void seat(Room room, ServerPlayer player, StudioLivePacket.Edit edit) {
        Seat seat = room.peers.computeIfAbsent(player.getUUID(), id -> new Seat(id, name(player)));
        seat.name = name(player);
        seat.line = clamp(edit.line());
        seat.col = clamp(edit.col());
        seat.anchorLine = edit.anchorLine() < 0 ? -1 : clamp(edit.anchorLine());
        seat.anchorCol = clamp(edit.anchorCol());
        seat.box = edit.box();
    }

    private static int clamp(int value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, 1_000_000);
    }

    private static Room room(String name, String disk, long stamp) {
        Room created = new Room();
        created.text = disk;
        created.saved = disk;
        created.revision = stamp;
        created.rememberRevision();
        ROOMS.put(name, created);
        return created;
    }

    /** Трёхстороннее построчное слияние: позволяет двум редакторам печатать одновременно. */
    private static String merge3Way(String base, String current, String incoming) {
        if (base.equals(incoming)) return current;
        if (base.equals(current)) return incoming;
        List<String> b = Arrays.asList(base.split("\n", -1));
        List<String> c = new ArrayList<>(Arrays.asList(current.split("\n", -1)));
        List<String> inc = Arrays.asList(incoming.split("\n", -1));

        // Находим диапазон изменённых строк в incoming относительно base
        int p = 0;
        int minBI = Math.min(b.size(), inc.size());
        while (p < minBI && b.get(p).equals(inc.get(p))) p++;
        int s = 0;
        while (s < minBI - p && b.get(b.size() - 1 - s).equals(inc.get(inc.size() - 1 - s))) s++;

        int bFrom = p, bTo = b.size() - s;
        int iFrom = p, iTo = inc.size() - s;

        // Находим, где этот же диапазон [bFrom..bTo) оказался в current
        int cp = 0;
        int minBC = Math.min(b.size(), c.size());
        while (cp < minBC && b.get(cp).equals(c.get(cp))) cp++;
        int cs = 0;
        while (cs < minBC - cp && b.get(b.size() - 1 - cs).equals(c.get(c.size() - 1 - cs))) cs++;
        int cDiff = c.size() - b.size();

        int targetFrom, targetTo;
        if (bTo <= cp) {
            // Правка incoming строго выше правки current
            targetFrom = bFrom;
            targetTo = bTo;
        } else if (bFrom >= b.size() - cs) {
            // Правка incoming строго ниже правки current
            targetFrom = bFrom + cDiff;
            targetTo = bTo + cDiff;
        } else {
            // Обе правки в одной и той же строке — сохраняем входящую, если не совпадает
            return incoming;
        }
        targetFrom = Math.max(0, Math.min(targetFrom, c.size()));
        targetTo = Math.max(targetFrom, Math.min(targetTo, c.size()));
        List<String> out = new ArrayList<>(c.size() + (iTo - iFrom));
        out.addAll(c.subList(0, targetFrom));
        out.addAll(inc.subList(iFrom, iTo));
        out.addAll(c.subList(targetTo, c.size()));
        return String.join("\n", out);
    }

    /** Сдвигает сохранённую строку курсора остальных участников при вставке/удалении строк. */
    private static void shiftOtherSeats(Room room, UUID editor, String oldText, String newText) {
        String[] oldL = oldText.split("\n", -1);
        String[] newL = newText.split("\n", -1);
        int diff = newL.length - oldL.length;
        if (diff == 0) return;
        int prefix = 0;
        int limit = Math.min(oldL.length, newL.length);
        while (prefix < limit && oldL[prefix].equals(newL[prefix])) prefix++;
        for (Seat s : room.peers.values()) {
            if (s.id.equals(editor)) continue;
            if (s.line > prefix || (diff > 0 && s.line == prefix && prefix + diff < newL.length && oldL[prefix].equals(newL[prefix + diff]))) {
                s.line = Math.max(0, Math.min(newL.length - 1, s.line + diff));
            }
            if (s.anchorLine >= 0 && (s.anchorLine > prefix || (diff > 0 && s.anchorLine == prefix))) {
                s.anchorLine = Math.max(0, Math.min(newL.length - 1, s.anchorLine + diff));
            }
        }
    }

    private static void sweep() {
        ROOMS.entrySet().removeIf(entry -> entry.getValue().peers.isEmpty() && entry.getValue().text.equals(entry.getValue().saved));
    }

    private static String diskText(String name) throws IOException {
        if (!Files.exists(ScriptPaths.resolve(name))) {
            return "";
        }
        return norm(Files.readString(ScriptPaths.resolve(name), StandardCharsets.UTF_8));
    }

    private static String norm(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String name(ServerPlayer player) {
        String value = player.getGameProfile().getName();
        if (value == null || value.isBlank()) {
            return player.getScoreboardName();
        }
        return value;
    }

    private static final class Room {
        private String text = "";
        private String saved = "";
        private long revision;
        private UUID lastEditor;
        private final Map<UUID, Seat> peers = new LinkedHashMap<>();
        private final LinkedHashMap<Long, String> revTexts = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, String> eldest) {
                return size() > 40;
            }
        };

        private void rememberRevision() {
            revTexts.put(revision, text);
        }
    }

    private static final class Seat {
        private final UUID id;
        private String name;
        private int line;
        private int col;
        private int anchorLine = -1;
        private int anchorCol;
        private boolean box;

        private Seat(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
