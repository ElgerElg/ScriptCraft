package com.scriptcraft.resources;

import com.scriptcraft.ModPaths;
import com.scriptcraft.ScriptCraft;
import com.scriptcraft.config.ScriptCraftConfig;
import com.scriptcraft.network.ResourceManifestPacket;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Серверный индекс ресурсов: каждый файл из scriptcraft/resources/assets/** — путь + SHA-1 + байты.
 * Строится при старте сервера и по /sc reload resources. Байты держим в памяти (размер ограничен
 * maxResourceSizeMb), чтобы раздача кусков не трогала диск.
 */
public final class ResourceIndex {
    /** Допустимый путь: assets/<namespace>/<путь>, только символы, разрешённые Minecraft в ResourceLocation. */
    private static final Pattern SAFE_PATH = Pattern.compile("assets/[a-z0-9_.-]+/[a-z0-9_.\\-/]+");

    public record Entry(String path, String sha1, byte[] data) {
    }

    private static volatile Map<String, Entry> entries = Map.of();
    private static volatile long totalBytes = 0;

    private ResourceIndex() {
    }

    /**
     * Безопасен ли путь (и на сервере, и на клиенте перед записью в кэш):
     * без "..", без пустых сегментов, только a-z0-9_.-/ — как в ResourceLocation.
     */
    public static boolean isSafePath(String path) {
        return path.length() <= ResourceManifestPacket.MAX_PATH
                && SAFE_PATH.matcher(path).matches()
                && !path.contains("..") && !path.contains("//") && !path.endsWith("/");
    }

    /**
     * Перечитывает папку scriptcraft/resources/assets.
     * @return null при успехе или текст ошибки (тогда индекс пуст и ничего не раздаётся)
     */
    public static synchronized String rescan() {
        Path root = ModPaths.resources();
        Path assets = root.resolve("assets");
        Map<String, Entry> map = new LinkedHashMap<>();
        long total = 0;
        long limit = ScriptCraftConfig.maxResourceSizeMb() * 1024L * 1024L;
        if (Files.isDirectory(assets)) {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(assets)) {
                files = walk.filter(Files::isRegularFile).sorted().toList();
            } catch (IOException e) {
                entries = Map.of();
                totalBytes = 0;
                ScriptCraft.LOGGER.error("[ScriptCraft] Не удалось прочитать {}", assets, e);
                return e.toString();
            }
            for (Path file : files) {
                String rel = root.relativize(file).toString().replace('\\', '/');
                if (!isSafePath(rel)) {
                    // Например, заглавные буквы или кириллица — Minecraft такой файл всё равно не загрузит.
                    ScriptCraft.LOGGER.warn("[ScriptCraft] Ресурс пропущен (недопустимое имя, нужны a-z0-9_.-): {}", rel);
                    continue;
                }
                if (map.size() >= ResourceManifestPacket.MAX_ENTRIES) {
                    return fail("too many files (max " + ResourceManifestPacket.MAX_ENTRIES + ")");
                }
                try {
                    byte[] data = Files.readAllBytes(file);
                    total += data.length;
                    if (total > limit) {
                        return fail("assets > maxResourceSizeMb (" + ScriptCraftConfig.maxResourceSizeMb() + " MB)");
                    }
                    map.put(rel, new Entry(rel, sha1(data), data));
                } catch (IOException e) {
                    ScriptCraft.LOGGER.warn("[ScriptCraft] Не удалось прочитать ресурс {}: {}", rel, e.toString());
                }
            }
        }
        entries = Collections.unmodifiableMap(map);
        totalBytes = total;
        ScriptCraft.LOGGER.info("[ScriptCraft] Ресурсы для клиентов: {} файлов, {} КБ", map.size(), total / 1024);
        return null;
    }

    private static String fail(String reason) {
        entries = Map.of();
        totalBytes = 0;
        ScriptCraft.LOGGER.error("[ScriptCraft] Синхронизация ресурсов отключена: {}", reason);
        return reason;
    }

    public static Map<String, Entry> entries() {
        return entries;
    }

    public static long totalBytes() {
        return totalBytes;
    }

    public static ResourceManifestPacket manifest() {
        List<ResourceManifestPacket.Entry> list = new ArrayList<>();
        for (Entry e : entries.values()) {
            list.add(new ResourceManifestPacket.Entry(e.path(), e.sha1(), e.data().length));
        }
        return new ResourceManifestPacket(list);
    }

    public static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-1 есть в любой JVM
        }
    }
}
