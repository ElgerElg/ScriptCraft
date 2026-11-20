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

    /** Отправляет состояние всех активных комнат (курсоры и живой текст) игроку при открытии студии. */
    public static void sendAllViews(ServerPlayer player) {
        if (player == null || !ScriptCraftConfig.studioLiveEdit()) {
            return;
        }
        for (Map.Entry<String, Room> entry : ROOMS.entrySet()) {
            Room room = entry.getValue();
            if (!room.peers.isEmpty() || !room.text.equals(room.saved)) {
                sendView(player, entry.getKey(), room, true, false, false);
            }
        }
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
            sendView(player, name, room, false, false, false);
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
                if (behind) {
                    int flags = ScriptDataPacket.FORCE | ScriptDataPacket.LIVE;
                    if (room.text.equals(room.saved)) {
                        flags |= ScriptDataPacket.DISK;
                    }
                    ModNetwork.sendTo(player, new ScriptDataPacket(edit.file(), room.text, room.revision, flags));
                }
                for (UUID id : studioListeners(server, room, player.getUUID())) {
                    ServerPlayer other = server.getPlayerList().getPlayer(id);
                    if (other != null) {
                        sendView(other, edit.file(), room, false, false, false);
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
        for (UUID id : studioListeners(server, room, player.getUUID())) {
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
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (!other.getUUID().equals(saver) && !StudioAccess.may(other)) {
                continue;
            }
            ModNetwork.sendTo(other, new ScriptDataPacket(name, text, revision, base));
        }
        broadcastPeers(server, name, room);
    }

    private static void broadcastPeers(MinecraftServer server, String file, Room room) {
        for (UUID id : studioListeners(server, room, null)) {
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

    private static List<UUID> studioListeners(MinecraftServer server, Room room, UUID extra) {
        List<UUID> ids = new ArrayList<>();
        for (Seat seat : room.peers.values()) {
            ids.add(seat.id);
        }
        if (extra != null && !ids.contains(extra)) {
            ids.add(extra);
        }
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!ids.contains(p.getUUID()) && StudioAccess.may(p)) {
                    ids.add(p.getUUID());
                }
            }
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

    /** Трёхстороннее построчное и внутристрочное слияние: позволяет двум редакторам печатать одновременно. */
    private static String merge3Way(String base, String current, String incoming) {
        if (base.equals(incoming)) return current;
        if (base.equals(current)) return incoming;
        if (current.equals(incoming)) return current;
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

        int[] span = locateBaseSpan(b, c, bFrom, bTo);
        if (span != null) {
            List<String> out = new ArrayList<>(c.size() + (iTo - iFrom));
            out.addAll(c.subList(0, span[0]));
            out.addAll(inc.subList(iFrom, iTo));
            out.addAll(c.subList(span[1], c.size()));
            return String.join("\n", out);
        }

        if (bTo - bFrom == 1 && iTo - iFrom == 1) {
            int row = Math.max(0, Math.min(c.size() - 1, bFrom));
            c.set(row, mergeLine3Way(b.get(bFrom), c.get(row), inc.get(iFrom)));
            return String.join("\n", c);
        }
        return incoming;
    }

    private static int[] locateBaseSpan(List<String> b, List<String> c, int bFrom, int bTo) {
        int len = bTo - bFrom;
        if (len > 0) {
            List<String> needle = b.subList(bFrom, bTo);
            int best = -1;
            int bestScore = Integer.MIN_VALUE;
            for (int idx = 0; idx + len <= c.size(); idx++) {
                if (c.subList(idx, idx + len).equals(needle)) {
                    int score = -Math.abs(idx - bFrom);
                    if (bFrom > 0 && idx > 0 && c.get(idx - 1).equals(b.get(bFrom - 1))) score += 100;
                    if (bTo < b.size() && idx + len < c.size() && c.get(idx + len).equals(b.get(bTo))) score += 100;
                    if (score > bestScore) {
                        bestScore = score;
                        best = idx;
                    }
                }
            }
            return best >= 0 ? new int[]{best, best + len} : null;
        }
        String prev = bFrom > 0 ? b.get(bFrom - 1) : null;
        String next = bFrom < b.size() ? b.get(bFrom) : null;
        int best = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int idx = 0; idx <= c.size(); idx++) {
            boolean prevOk = (prev == null && idx == 0) || (prev != null && idx > 0 && c.get(idx - 1).equals(prev));
            boolean nextOk = (next == null && idx == c.size()) || (next != null && idx < c.size() && c.get(idx).equals(next));
            if (prevOk || nextOk) {
                int score = (prevOk ? 100 : 0) + (nextOk ? 100 : 0) - Math.abs(idx - bFrom);
                if (score > bestScore) {
                    bestScore = score;
                    best = idx;
                }
            }
        }
        return best >= 0 ? new int[]{best, best} : null;
    }

    private static String mergeLine3Way(String base, String cur, String inc) {
        if (base.equals(inc)) return cur;
        if (base.equals(cur)) return inc;
        int p = 0, minBI = Math.min(base.length(), inc.length());
        while (p < minBI && base.charAt(p) == inc.charAt(p)) p++;
        int s = 0;
        while (s < minBI - p && base.charAt(base.length() - 1 - s) == inc.charAt(inc.length() - 1 - s)) s++;
        int cp = 0, minBC = Math.min(base.length(), cur.length());
        while (cp < minBC && base.charAt(cp) == cur.charAt(cp)) cp++;
        int cs = 0;
        while (cs < minBC - cp && base.charAt(base.length() - 1 - cs) == cur.charAt(cur.length() - 1 - cs)) cs++;
        int bTo = base.length() - s;
        if (bTo <= cp) {
            return inc.substring(0, inc.length() - s) + cur.substring(bTo);
        }
        if (p >= base.length() - cs) {
            int shift = cur.length() - base.length();
            int at = Math.max(0, Math.min(cur.length(), p + shift));
            int end = Math.max(at, Math.min(cur.length(), bTo + shift));
            return cur.substring(0, at) + inc.substring(p, inc.length() - s) + cur.substring(end);
        }
        return inc;
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
