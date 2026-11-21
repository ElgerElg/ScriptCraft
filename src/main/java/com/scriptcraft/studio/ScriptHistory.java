package com.scriptcraft.studio;

import com.scriptcraft.ModPaths;
import com.scriptcraft.ScriptCraft;
import com.scriptcraft.config.ScriptCraftConfig;
import com.scriptcraft.script.ScriptPaths;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * История версий скриптов (scriptcraft/history/<script>/<timestamp>_<author>.sc).
 * Перед каждым сохранением скрипта сохраняет предыдущую версию.
 * Старые версии автоматически удаляются через scriptHistoryRetentionDays дней (по умолчанию 3 дня).
 */
public final class ScriptHistory {
    public record Entry(String id, long timestampMs, String author, int bytes, int lines) {
    }

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final long CLEANUP_INTERVAL_MS = 10L * 60L * 1000L;
    private static long lastCleanupMs = 0L;
    private static int tickCounter = 0;

    private ScriptHistory() {
    }

    /** Сохраняет предыдущий текст скрипта перед перезаписью. */
    public static void recordBeforeSave(String scriptName, String previousText, @Nullable String author) {
        if (!ScriptCraftConfig.scriptHistoryEnabled() || scriptName == null || scriptName.isBlank()) {
            return;
        }
        if (previousText == null || previousText.isEmpty()) {
            return;
        }
        Path dir = dirFor(scriptName);
        if (dir == null) {
            return;
        }
        String cleanAuthor = sanitizeAuthor(author);
        String stamp = LocalDateTime.now().format(STAMP);
        String fileName = stamp + "_" + cleanAuthor + ".sc";
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(fileName), previousText, StandardCharsets.UTF_8);
            maybeCleanup();
        } catch (IOException e) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Не удалось сохранить снимок истории {}: {}", scriptName, e.getMessage());
        }
    }

    /** Список сохранённых версий для скрипта (от новых к старым). */
    public static List<Entry> list(String scriptName) {
        Path dir = dirFor(scriptName);
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        List<Entry> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> files = s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".sc"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .toList();
            for (Path file : files) {
                String id = file.getFileName().toString();
                long ts = parseTimestamp(id, file);
                String author = parseAuthor(id);
                int bytes = 0;
                int lines = 0;
                try {
                    byte[] raw = Files.readAllBytes(file);
                    bytes = raw.length;
                    lines = countLines(raw);
                } catch (IOException ignored) {
                }
                out.add(new Entry(id, ts, author, bytes, lines));
                if (out.size() >= 100) {
                    break;
                }
            }
        } catch (IOException e) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Не удалось прочитать историю {}: {}", scriptName, e.getMessage());
        }
        return out;
    }

    @Nullable
    public static String read(String scriptName, String snapshotId) {
        Path file = resolveSnapshot(scriptName, snapshotId);
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public static boolean delete(String scriptName, String snapshotId) {
        Path file = resolveSnapshot(scriptName, snapshotId);
        if (file == null || !Files.isRegularFile(file)) {
            return false;
        }
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            return false;
        }
    }

    /** Периодическая проверка из тика сервера (раз в час). */
    public static void tick() {
        if (++tickCounter >= 20 * 3600) {
            tickCounter = 0;
            cleanup();
        }
    }

    public static void maybeCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupMs >= CLEANUP_INTERVAL_MS) {
            cleanup();
        }
    }

    /** Удаляет снимки старше scriptHistoryRetentionDays дней (0 = автоудаление отключено). */
    public static void cleanup() {
        lastCleanupMs = System.currentTimeMillis();
        int days = ScriptCraftConfig.scriptHistoryRetentionDays();
        if (days <= 0) {
            return;
        }
        Path root = ModPaths.history();
        if (!Files.isDirectory(root)) {
            return;
        }
        long cutoff = System.currentTimeMillis() - days * 86_400_000L;
        int deleted = 0;
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> all = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path p : all) {
                if (p.equals(root)) {
                    continue;
                }
                if (Files.isRegularFile(p)) {
                    long ts = parseTimestamp(p.getFileName().toString(), p);
                    if (ts > 0 && ts < cutoff) {
                        if (Files.deleteIfExists(p)) {
                            deleted++;
                        }
                    }
                } else if (Files.isDirectory(p)) {
                    try (Stream<Path> children = Files.list(p)) {
                        if (children.findAny().isEmpty()) {
                            Files.deleteIfExists(p);
                        }
                    } catch (IOException ignored) {
                    }
                }
            }
        } catch (IOException e) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Ошибка очистки истории скриптов: {}", e.getMessage());
        }
        if (deleted > 0) {
            ScriptCraft.LOGGER.info("[ScriptCraft] Удалено устаревших версий скриптов (>{} дн.): {}", days, deleted);
        }
    }

    @Nullable
    private static Path dirFor(String scriptName) {
        if (!ScriptPaths.contained(scriptName)) {
            return null;
        }
        String clean = scriptName.replace('\\', '/');
        if (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        if (clean.contains("..")) {
            return null;
        }
        Path root = ModPaths.history().normalize();
        Path dir = root.resolve(clean).normalize();
        return dir.startsWith(root) ? dir : null;
    }

    @Nullable
    private static Path resolveSnapshot(String scriptName, String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank() || snapshotId.contains("/") || snapshotId.contains("\\") || snapshotId.contains("..")) {
            return null;
        }
        Path dir = dirFor(scriptName);
        if (dir == null) {
            return null;
        }
        Path file = dir.resolve(snapshotId).normalize();
        return file.startsWith(dir) ? file : null;
    }

    private static String sanitizeAuthor(@Nullable String author) {
        if (author == null || author.isBlank()) {
            return "server";
        }
        String clean = author.replaceAll("[^A-Za-z0-9_.-]", "");
        return clean.isEmpty() ? "server" : clean.substring(0, Math.min(24, clean.length()));
    }

    private static long parseTimestamp(String fileName, Path file) {
        int under = fileName.indexOf('_');
        if (under >= 15) {
            String raw = fileName.substring(0, under);
            try {
                LocalDateTime dt = LocalDateTime.parse(raw, STAMP);
                return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (RuntimeException ignored) {
            }
        }
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return Instant.now().toEpochMilli();
        }
    }

    private static String parseAuthor(String fileName) {
        int under = fileName.indexOf('_');
        int dot = fileName.lastIndexOf('.');
        if (under > 0 && dot > under + 1) {
            return fileName.substring(under + 1, dot);
        }
        return "server";
    }

    private static int countLines(byte[] raw) {
        if (raw.length == 0) {
            return 0;
        }
        int lines = 1;
        for (byte b : raw) {
            if (b == '\n') {
                lines++;
            }
        }
        return lines;
    }
}
