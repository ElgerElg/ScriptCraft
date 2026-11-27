package com.scriptcraft.studio;

import com.scriptcraft.ModPaths;
import com.scriptcraft.config.ScriptCraftConfig;
import com.scriptcraft.lang.Feedback;
import com.scriptcraft.network.LogPacket;
import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.network.ScriptListPacket;
import com.scriptcraft.network.StudioExplorerPacket;
import com.scriptcraft.resources.ResourceSync;
import com.scriptcraft.script.ScriptLoader;
import com.scriptcraft.script.ScriptPaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Серверный проводник по всей папке scriptcraft/: навигация, создание папок/файлов,
 * удаление, переименование, включение/выключение скриптов, загрузка файлов (картинок и др.),
 * а также рассылка обновлений и присутствия редакторов в реальном времени.
 */
public final class StudioExplorer {
    public static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
    /** Какую папку сейчас просматривает каждый редактор (для авто-рассылки обновлений). */
    private static final Map<UUID, String> WATCHING_DIR = new ConcurrentHashMap<>();
    /** Буферы чанковой загрузки файлов C2S (ключ: uuid:relPath). */
    private static final Map<String, java.io.ByteArrayOutputStream> UPLOAD_CHUNKS = new ConcurrentHashMap<>();
    /** Изменялись ли файлы в resources/ через студию с момента последней перезагрузки ресурсов. */
    private static volatile boolean resourcesDirty = false;

    private StudioExplorer() {
    }

    public static void onQuit(ServerPlayer player) {
        if (player != null) {
            WATCHING_DIR.remove(player.getUUID());
            String prefix = player.getUUID() + ":";
            UPLOAD_CHUNKS.keySet().removeIf(k -> k.startsWith(prefix));
        }
    }

    public static void clear() {
        WATCHING_DIR.clear();
        UPLOAD_CHUNKS.clear();
        resourcesDirty = false;
    }

    public static void markResourcesDirty(MinecraftServer server) {
        resourcesDirty = true;
        broadcastResourcesDirty(server);
    }

    public static void clearResourcesDirty(MinecraftServer server) {
        resourcesDirty = false;
        broadcastResourcesDirty(server);
    }

    private static void broadcastResourcesDirty(MinecraftServer server) {
        if (server == null) return;
        StudioExplorerPacket.FileView notice = new StudioExplorerPacket.FileView(
                resourcesDirty ? "1" : "0", false, new byte[0], "$res_dirty");
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (StudioAccess.may(p)) {
                ModNetwork.sendTo(p, notice);
            }
        }
    }

    public static String normalizeRel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.replace('\\', '/').trim();
        while (s.startsWith("/")) s = s.substring(1);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static Path resolveSafe(String rel) {
        Path root = ModPaths.root().toAbsolutePath().normalize();
        String norm = normalizeRel(rel);
        Path target = norm.isEmpty() ? root : root.resolve(norm).normalize();
        if (!target.startsWith(root)) {
            return null;
        }
        return target;
    }

    public static boolean isImageName(String path) {
        String low = path.toLowerCase(Locale.ROOT);
        return low.endsWith(".png") || low.endsWith(".jpg") || low.endsWith(".jpeg");
    }

    public static boolean isAudioName(String path) {
        return path.toLowerCase(Locale.ROOT).endsWith(".ogg");
    }

    public static void handle(ServerPlayer player, StudioExplorerPacket.Action action) {
        MinecraftServer server = player.getServer();
        String path = normalizeRel(action.path());
        switch (action.kind()) {
            case LIST -> {
                if ("$animations".equals(action.path())) {
                    sendAnimationsList(player);
                    return;
                }
                WATCHING_DIR.put(player.getUUID(), path);
                sendDir(player, path);
            }
            case MKDIR -> {
                Path target = resolveSafe(path);
                if (target == null || path.isEmpty()) {
                    Feedback.toPlayer(player, "studio.bad_name", path);
                    return;
                }
                try {
                    Files.createDirectories(target);
                    broadcastDirs(server);
                } catch (IOException e) {
                    Feedback.toPlayer(player, "studio.save_failed", safeMsg(e));
                }
            }
            case DELETE -> {
                Path target = resolveSafe(path);
                if (target == null || path.isEmpty() || target.equals(ModPaths.root().toAbsolutePath().normalize())) {
                    Feedback.toPlayer(player, "studio.bad_name", path);
                    return;
                }
                try {
                    boolean underScripts = path.startsWith("scripts/");
                    String scriptRel = underScripts ? path.substring("scripts/".length()) : null;
                    deleteRecursively(target);
                    if (scriptRel != null) {
                        StudioRooms.forget(server, scriptRel, player.getUUID());
                        ScriptLoader.reloadAll(player.createCommandSourceStack());
                        broadcastScriptsAndLog(server);
                    } else if (path.startsWith("resources/")) {
                        if ("$anim_del".equals(action.extra()) && server != null) {
                            ResourceSync.reloadAndPush(server);
                            clearResourcesDirty(server);
                            sendAnimationsList(player);
                        } else {
                            markResourcesDirty(server);
                        }
                    }
                    broadcastRename(server, path, "");
                    broadcastDirs(server);
                } catch (IOException e) {
                    Feedback.toPlayer(player, "studio.delete_failed", safeMsg(e));
                }
            }
            case RENAME -> {
                String dst = normalizeRel(action.extra());
                Path srcPath = resolveSafe(path);
                Path dstPath = resolveSafe(dst);
                if (srcPath == null || dstPath == null || path.isEmpty() || dst.isEmpty() || !Files.exists(srcPath)
                        || (Files.isDirectory(srcPath) && dstPath.startsWith(srcPath))) {
                    Feedback.toPlayer(player, "studio.bad_name", dst);
                    return;
                }
                try {
                    if (dstPath.getParent() != null) {
                        Files.createDirectories(dstPath.getParent());
                    }
                    Files.move(srcPath, dstPath);
                    onPathRenamed(server, player, path, dst);
                } catch (IOException e) {
                    Feedback.toPlayer(player, "studio.save_failed", safeMsg(e));
                }
            }
            case MOVE_BATCH -> handleBatchMoveOrCopy(server, player, action.path(), action.extra(), true);
            case COPY_BATCH -> handleBatchMoveOrCopy(server, player, action.path(), action.extra(), false);
            case DELETE_BATCH -> handleBatchDelete(server, player, action.path());
            case TOGGLE_SCRIPT -> {
                if (!path.startsWith("scripts/") || !path.endsWith(".sc")) {
                    return;
                }
                Path srcPath = resolveSafe(path);
                if (srcPath == null || !Files.isRegularFile(srcPath)) {
                    return;
                }
                int slash = path.lastIndexOf('/');
                String dir = path.substring(0, slash + 1);
                String file = path.substring(slash + 1);
                String nextFile = file.startsWith("-") ? file.substring(1) : "-" + file;
                String dst = dir + nextFile;
                Path dstPath = resolveSafe(dst);
                if (dstPath == null || Files.exists(dstPath)) {
                    Feedback.toPlayer(player, "studio.bad_name", dst);
                    return;
                }
                try {
                    Files.move(srcPath, dstPath);
                    onPathRenamed(server, player, path, dst);
                } catch (IOException e) {
                    Feedback.toPlayer(player, "studio.save_failed", safeMsg(e));
                }
            }
            case READ_FILE -> {
                Path target = resolveSafe(path);
                if (target == null || !Files.isRegularFile(target)) {
                    Feedback.toPlayer(player, "studio.read_failed", path);
                    ModNetwork.sendTo(player, new StudioExplorerPacket.FileView(path, false, new byte[0], "$read_error"));
                    return;
                }
                try {
                    long size = Files.size(target);
                    if (size > MAX_FILE_BYTES) {
                        Feedback.toPlayer(player, "studio.too_large", path, MAX_FILE_BYTES / (1024 * 1024));
                        ModNetwork.sendTo(player, new StudioExplorerPacket.FileView(path, false, new byte[0], "$read_error"));
                        return;
                    }
                    byte[] bytes = Files.readAllBytes(target);
                    boolean bin = isImageName(path) || isAudioName(path);
                    String tag = "$anim_open".equals(action.extra()) ? "$anim_open" : "";
                    StudioExplorerPacket.sendFileViewToClient(player, path, bin, bytes, tag);
                } catch (Throwable e) {
                    Feedback.toPlayer(player, "studio.read_failed", path);
                    ModNetwork.sendTo(player, new StudioExplorerPacket.FileView(path, false, new byte[0], "$read_error"));
                }
            }
            case WRITE_FILE_PART -> {
                Path target = resolveSafe(path);
                String upKey = player.getUUID() + ":" + path;
                if (target == null || path.isEmpty()) {
                    UPLOAD_CHUNKS.remove(upKey);
                    return;
                }
                if ("$reset".equals(action.extra())) {
                    UPLOAD_CHUNKS.put(upKey, new java.io.ByteArrayOutputStream(Math.max(4096, action.data().length * 2)));
                }
                java.io.ByteArrayOutputStream baos = UPLOAD_CHUNKS.computeIfAbsent(upKey,
                        k -> new java.io.ByteArrayOutputStream(Math.max(4096, action.data().length * 2)));
                if (baos.size() + action.data().length > MAX_FILE_BYTES) {
                    UPLOAD_CHUNKS.remove(upKey);
                    Feedback.toPlayer(player, "studio.too_large", path, MAX_FILE_BYTES / (1024 * 1024));
                    return;
                }
                baos.write(action.data(), 0, action.data().length);
            }
            case WRITE_FILE -> {
                String upKey = player.getUUID() + ":" + path;
                java.io.ByteArrayOutputStream prefix = UPLOAD_CHUNKS.remove(upKey);
                byte[] fullBytes;
                if (prefix != null && prefix.size() > 0) {
                    if (prefix.size() + action.data().length > MAX_FILE_BYTES) {
                        Feedback.toPlayer(player, "studio.too_large", path, MAX_FILE_BYTES / (1024 * 1024));
                        return;
                    }
                    prefix.write(action.data(), 0, action.data().length);
                    fullBytes = prefix.toByteArray();
                } else {
                    fullBytes = action.data();
                }
                Path target = resolveSafe(path);
                if (target == null || path.isEmpty()) {
                    Feedback.toPlayer(player, "studio.bad_name", path);
                    return;
                }
                if (fullBytes.length > MAX_FILE_BYTES) {
                    Feedback.toPlayer(player, "studio.too_large", path, MAX_FILE_BYTES / (1024 * 1024));
                    return;
                }
                try {
                    if (target.getParent() != null) {
                        Files.createDirectories(target.getParent());
                    }
                    Files.write(target, fullBytes);
                    if (path.equals("config.toml")) {
                        ScriptCraftConfig.reloadFromDisk();
                        StudioRooms.onConfigChange(server);
                    } else if (path.startsWith("scripts/") && path.endsWith(".sc")) {
                        String scriptName = path.substring("scripts/".length());
                        if (!ScriptPaths.isDisabled(scriptName)) {
                            ScriptLoader.reloadOne(player.createCommandSourceStack(), scriptName);
                        }
                        broadcastScriptsAndLog(server);
                    } else if (path.startsWith("resources/") && server != null) {
                        if ("$anim_save".equals(action.extra())) {
                            ResourceSync.reloadAndPush(server);
                            clearResourcesDirty(server);
                            sendAnimationsList(player);
                        } else {
                            markResourcesDirty(server);
                        }
                    }
                    broadcastDirs(server);
                } catch (IOException e) {
                    Feedback.toPlayer(player, "studio.save_failed", safeMsg(e));
                }
            }
        }
    }

    private static void onPathRenamed(MinecraftServer server, ServerPlayer player, String oldPath, String newPath) {
        boolean oldInScripts = oldPath.startsWith("scripts/");
        boolean newInScripts = newPath.startsWith("scripts/");
        if (oldInScripts && newInScripts) {
            String oldSc = oldPath.substring("scripts/".length());
            String newSc = newPath.substring("scripts/".length());
            StudioRooms.renameRoom(oldSc, newSc);
            ScriptLoader.reloadAll(player.createCommandSourceStack());
            broadcastScriptsAndLog(server);
        } else if (oldInScripts || newInScripts) {
            if (oldInScripts) {
                StudioRooms.forget(server, oldPath.substring("scripts/".length()), player.getUUID());
            }
            ScriptLoader.reloadAll(player.createCommandSourceStack());
            broadcastScriptsAndLog(server);
        }
        if (oldPath.startsWith("resources/") || newPath.startsWith("resources/")) {
            markResourcesDirty(server);
        }
        broadcastRename(server, oldPath, newPath);
        broadcastDirs(server);
    }

    private static void handleBatchMoveOrCopy(MinecraftServer server, ServerPlayer player, String rawList, String rawDstDir, boolean move) {
        if (rawList == null || rawList.isBlank()) return;
        String dstDir = normalizeRel(rawDstDir);
        Path dstDirPath = resolveSafe(dstDir);
        if (dstDirPath == null) {
            Feedback.toPlayer(player, "studio.bad_name", dstDir);
            return;
        }
        Path root = ModPaths.root().toAbsolutePath().normalize();
        boolean scriptsTouched = false;
        boolean resourcesTouched = false;
        boolean anyChanged = false;
        try {
            Files.createDirectories(dstDirPath);
            for (String item : rawList.split("\n")) {
                String srcRel = normalizeRel(item);
                if (srcRel.isEmpty()) continue;
                Path srcPath = resolveSafe(srcRel);
                if (srcPath == null || srcPath.equals(root) || !Files.exists(srcPath)) continue;
                String baseName = srcPath.getFileName().toString();
                String candidateRel = dstDir.isEmpty() ? baseName : dstDir + "/" + baseName;
                if (move && candidateRel.equals(srcRel)) {
                    continue; // уже лежит в этой папке
                }
                if (Files.isDirectory(srcPath) && dstDirPath.startsWith(srcPath)) {
                    Feedback.toPlayer(player, "studio.bad_name", candidateRel);
                    continue;
                }
                Path finalDst = uniqueTarget(dstDirPath, baseName, !move && candidateRel.equals(srcRel));
                String finalRel = root.relativize(finalDst).toString().replace('\\', '/');
                if (move) {
                    Files.move(srcPath, finalDst);
                    if (srcRel.startsWith("scripts/") && finalRel.startsWith("scripts/")) {
                        StudioRooms.renameRoom(srcRel.substring("scripts/".length()), finalRel.substring("scripts/".length()));
                    } else if (srcRel.startsWith("scripts/")) {
                        StudioRooms.forget(server, srcRel.substring("scripts/".length()), player.getUUID());
                    }
                    broadcastRename(server, srcRel, finalRel);
                } else {
                    copyRecursively(srcPath, finalDst);
                }
                scriptsTouched |= srcRel.startsWith("scripts/") || finalRel.startsWith("scripts/");
                resourcesTouched |= srcRel.startsWith("resources/") || finalRel.startsWith("resources/");
                anyChanged = true;
            }
            if (scriptsTouched) {
                ScriptLoader.reloadAll(player.createCommandSourceStack());
                broadcastScriptsAndLog(server);
            }
            if (resourcesTouched) {
                markResourcesDirty(server);
            }
            if (anyChanged) {
                broadcastDirs(server);
            }
        } catch (IOException e) {
            Feedback.toPlayer(player, "studio.save_failed", safeMsg(e));
        }
    }

    private static void handleBatchDelete(MinecraftServer server, ServerPlayer player, String rawList) {
        if (rawList == null || rawList.isBlank()) return;
        Path root = ModPaths.root().toAbsolutePath().normalize();
        boolean scriptsTouched = false;
        boolean resourcesTouched = false;
        boolean anyDeleted = false;
        try {
            for (String item : rawList.split("\n")) {
                String rel = normalizeRel(item);
                if (rel.isEmpty()) continue;
                Path target = resolveSafe(rel);
                if (target == null || target.equals(root) || !Files.exists(target)) continue;
                deleteRecursively(target);
                if (rel.startsWith("scripts/")) {
                    StudioRooms.forget(server, rel.substring("scripts/".length()), player.getUUID());
                    scriptsTouched = true;
                }
                if (rel.startsWith("resources/")) {
                    resourcesTouched = true;
                }
                broadcastRename(server, rel, "");
                anyDeleted = true;
            }
            if (scriptsTouched) {
                ScriptLoader.reloadAll(player.createCommandSourceStack());
                broadcastScriptsAndLog(server);
            }
            if (resourcesTouched) {
                markResourcesDirty(server);
            }
            if (anyDeleted) {
                broadcastDirs(server);
            }
        } catch (IOException e) {
            Feedback.toPlayer(player, "studio.delete_failed", safeMsg(e));
        }
    }

    private static Path uniqueTarget(Path dstDir, String baseName, boolean forceCopySuffix) {
        Path direct = dstDir.resolve(baseName).normalize();
        if (!forceCopySuffix && !Files.exists(direct)) {
            return direct;
        }
        int dot = baseName.lastIndexOf('.');
        String stem = dot > 0 ? baseName.substring(0, dot) : baseName;
        String ext = dot > 0 ? baseName.substring(dot) : "";
        for (int i = 1; i < 500; i++) {
            String suffix = i == 1 && forceCopySuffix ? "_copy" : "_" + (i + 1);
            Path cand = dstDir.resolve(stem + suffix + ext).normalize();
            if (!Files.exists(cand)) {
                return cand;
            }
        }
        return dstDir.resolve(stem + "_" + System.currentTimeMillis() + ext).normalize();
    }

    private static void copyRecursively(Path src, Path dst) throws IOException {
        if (Files.isDirectory(src)) {
            try (Stream<Path> walk = Files.walk(src)) {
                for (Path p : walk.toList()) {
                    Path rel = src.relativize(p);
                    Path target = dst.resolve(rel);
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(target);
                    } else {
                        if (target.getParent() != null) Files.createDirectories(target.getParent());
                        Files.copy(p, target);
                    }
                }
            }
        } else {
            if (dst.getParent() != null) Files.createDirectories(dst.getParent());
            Files.copy(src, dst);
        }
    }

    public static void sendDir(ServerPlayer player, String dir) {
        String norm = normalizeRel(dir);
        Path target = resolveSafe(norm);
        if (target == null || !Files.isDirectory(target)) {
            norm = "";
            target = ModPaths.root().toAbsolutePath().normalize();
        }
        List<StudioExplorerPacket.Entry> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(target)) {
            List<Path> sorted = stream.sorted(Comparator
                    .comparing((Path p) -> !Files.isDirectory(p))
                    .thenComparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
            for (Path p : sorted) {
                if (entries.size() >= 250) break;
                String name = p.getFileName().toString();
                boolean isDir = Files.isDirectory(p);
                long size = isDir ? 0L : safeSize(p);
                entries.add(new StudioExplorerPacket.Entry(name, isDir, size));
            }
        } catch (IOException ignored) {
        }
        ModNetwork.sendTo(player, new StudioExplorerPacket.DirView(norm, entries, StudioRooms.activeEditors()));
        if (resourcesDirty) {
            ModNetwork.sendTo(player, new StudioExplorerPacket.FileView("1", false, new byte[0], "$res_dirty"));
        }
    }

    /** Собирает все .json файлы анимаций игрока из resources/assets/<ns>/player_animation(s)/ для редактора анимаций. */
    public static void sendAnimationsList(ServerPlayer player) {
        Path root = ModPaths.root().toAbsolutePath().normalize();
        Path assetsRoot = ModPaths.resources().resolve("assets").toAbsolutePath().normalize();
        List<StudioExplorerPacket.Entry> entries = new ArrayList<>();
        if (Files.isDirectory(assetsRoot)) {
            try (Stream<Path> nsStream = Files.list(assetsRoot)) {
                List<Path> namespaces = nsStream.filter(Files::isDirectory)
                        .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .toList();
                for (Path nsDir : namespaces) {
                    for (String sub : List.of("player_animation", "player_animations")) {
                        Path animDir = nsDir.resolve(sub);
                        if (!Files.isDirectory(animDir)) continue;
                        try (Stream<Path> walk = Files.walk(animDir, 4)) {
                            List<Path> files = walk.filter(Files::isRegularFile)
                                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                                    .sorted(Comparator.comparing(p -> p.toString().toLowerCase(Locale.ROOT)))
                                    .toList();
                            for (Path f : files) {
                                if (entries.size() >= 250) break;
                                String rel = root.relativize(f).toString().replace('\\', '/');
                                entries.add(new StudioExplorerPacket.Entry(rel, false, safeSize(f)));
                            }
                        } catch (IOException ignored) {
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
        ModNetwork.sendTo(player, new StudioExplorerPacket.DirView("$animations", entries, List.of()));
    }

    /** Рассылает свежее содержимое открытых папок и активных редакторов всем студийцам онлайн. */
    public static void broadcastDirs(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (StudioAccess.may(p)) {
                String dir = WATCHING_DIR.getOrDefault(p.getUUID(), "scripts");
                sendDir(p, dir);
            }
        }
    }

    public static void broadcastRename(MinecraftServer server, String oldPath, String newPath) {
        if (server == null) return;
        StudioExplorerPacket.FileView notice = new StudioExplorerPacket.FileView(newPath, false, new byte[0], oldPath);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (StudioAccess.may(p)) {
                ModNetwork.sendTo(p, notice);
            }
        }
    }

    public static void broadcastScriptsAndLog(MinecraftServer server) {
        if (server == null) return;
        ScriptListPacket list = ScriptListPacket.ofServer();
        LogPacket log = LogPacket.from(ScriptLoader.journal(), false);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (StudioAccess.may(p)) {
                ModNetwork.sendTo(p, list);
                ModNetwork.sendTo(p, log);
            }
        }
    }

    private static void deleteRecursively(Path target) throws IOException {
        if (Files.isDirectory(target)) {
            try (Stream<Path> walk = Files.walk(target)) {
                List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
                for (Path p : paths) {
                    Files.deleteIfExists(p);
                }
            }
        } else {
            Files.deleteIfExists(target);
        }
    }

    private static long safeSize(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0L;
        }
    }

    private static String safeMsg(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
