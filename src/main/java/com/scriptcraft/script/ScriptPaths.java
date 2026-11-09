package com.scriptcraft.script;

import com.scriptcraft.ModPaths;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Работа с папкой скриптов: &lt;папка игры&gt;/scriptcraft/scripts/*.sc (создаётся в {@link ModPaths#ensureLayout()}).
 * Выключенные скрипты (как в Skript) — файлы с минусом в начале имени: -welcome.sc. Они не загружаются.
 * Старые файлы *.sk при запуске переименовываются в *.sc ({@link #migrateLegacyExtension()}).
 */
public final class ScriptPaths {
    /** Расширение файлов скриптов. */
    public static final String EXTENSION = ".sc";
    /** Прежнее расширение. Читается, только если файла *.sc с тем же именем ещё нет. */
    public static final String LEGACY_EXTENSION = ".sk";

    private ScriptPaths() {
    }

    /** Папка скриптов: <папка игры>/scriptcraft/scripts (в dev-режиме — run/scriptcraft/scripts). */
    public static Path scriptsDir() {
        return ModPaths.scripts();
    }

    /**
     * Все файлы скриптов (включая выключенные "-имя.sc"), пути относительно папки scripts, через '/'.
     * Отсортированы по алфавиту. Старый *.sk попадает в список, только если рядом нет такого же *.sc.
     */
    public static List<String> listScripts() {
        Path dir = scriptsDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(dir, 4)) {
            return stream.filter(p -> Files.isRegularFile(p) && isScriptFile(p))
                    .map(p -> dir.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * welcome.sk → welcome.sc (и -welcome.sk → -welcome.sc), если файла *.sc ещё нет.
     * Вызывается при старте мода, чтобы старые скрипты не пропали после смены расширения.
     */
    public static void migrateLegacyExtension() {
        Path dir = scriptsDir();
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<Path> legacy;
        try (Stream<Path> stream = Files.walk(dir, 4)) {
            legacy = stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(LEGACY_EXTENSION)).toList();
        } catch (IOException e) {
            com.scriptcraft.ScriptCraft.LOGGER.warn("[ScriptCraft] Не удалось проверить старые скрипты *.sk: {}", e.getMessage());
            return;
        }
        for (Path file : legacy) {
            String name = file.getFileName().toString();
            Path dest = file.resolveSibling(name.substring(0, name.length() - LEGACY_EXTENSION.length()) + EXTENSION);
            if (Files.exists(dest)) {
                continue;
            }
            try {
                Files.move(file, dest);
                com.scriptcraft.ScriptCraft.LOGGER.info("[ScriptCraft] Скрипт {} переименован в {}", name, dest.getFileName());
            } catch (IOException e) {
                com.scriptcraft.ScriptCraft.LOGGER.warn("[ScriptCraft] Не удалось переименовать {} в {}: {}",
                        name, dest.getFileName(), e.getMessage());
            }
        }
    }

    private static boolean isScriptFile(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(EXTENSION)) {
            return true;
        }
        if (!name.endsWith(LEGACY_EXTENSION)) {
            return false;
        }
        String sc = name.substring(0, name.length() - LEGACY_EXTENSION.length()) + EXTENSION;
        return !Files.exists(file.resolveSibling(sc));
    }

    /** Выключен ли скрипт: имя файла (не папки) начинается с '-', как в Skript. */
    public static boolean isDisabled(String relName) {
        return fileNameOf(relName).startsWith("-");
    }

    /** "sub/-a.sc" -> "sub/a.sc" */
    public static String enabledName(String relName) {
        int slash = relName.lastIndexOf('/');
        String file = fileNameOf(relName);
        while (file.startsWith("-")) {
            file = file.substring(1);
        }
        return relName.substring(0, slash + 1) + file;
    }

    /** "sub/a.sc" -> "sub/-a.sc" */
    public static String disabledName(String relName) {
        String enabled = enabledName(relName);
        int slash = enabled.lastIndexOf('/');
        return enabled.substring(0, slash + 1) + "-" + enabled.substring(slash + 1);
    }

    /**
     * Ввод игрока -> нормальное (включённое) имя скрипта: "welcome", "welcome.sc", "-welcome.sc" -> "welcome.sc".
     * Старое "welcome.sk" тоже принимается. Если на диске остался только *.sk (переименовать не вышло) — вернётся он.
     * Возвращает null, если путь выходит за пределы папки scripts (защита от "../").
     */
    @Nullable
    public static String normalize(String input) {
        String name = input.strip().replace('\\', '/');
        if (name.isEmpty() || name.contains("..") || name.startsWith("/") || name.contains(":")) {
            return null;
        }
        if (name.endsWith(LEGACY_EXTENSION)) {
            name = name.substring(0, name.length() - LEGACY_EXTENSION.length()) + EXTENSION;
        } else if (!name.endsWith(EXTENSION)) {
            name = name + EXTENSION;
        }
        String enabled = enabledName(name);
        if (Files.isRegularFile(resolve(enabled)) || Files.isRegularFile(resolve(disabledName(enabled)))) {
            return enabled;
        }
        String legacy = enabled.substring(0, enabled.length() - EXTENSION.length()) + LEGACY_EXTENSION;
        if (Files.isRegularFile(resolve(legacy)) || Files.isRegularFile(resolve(disabledName(legacy)))) {
            return enabledName(legacy);
        }
        return enabled;
    }

    /** Абсолютный путь файла скрипта по относительному имени. */
    public static Path resolve(String relName) {
        return scriptsDir().resolve(relName.replace('/', java.io.File.separatorChar));
    }

    /** Переименование (используется для enable/disable). */
    public static void rename(String fromRel, String toRel) throws IOException {
        Files.move(resolve(fromRel), resolve(toRel));
    }

    /** Читает один файл; null + запись в errors при ошибке. */
    @Nullable
    public static ScriptSource readOne(String relName, List<String> errors) {
        try {
            // Файлы читаются в UTF-8, чтобы русский текст отображался правильно.
            return new ScriptSource(relName, Files.readAllLines(resolve(relName), StandardCharsets.UTF_8));
        } catch (IOException e) {
            errors.add(relName + ": ошибка чтения: " + e.getMessage());
            return null;
        }
    }

    /**
     * Читает все ВКЛЮЧЁННЫЕ *.sc файлы (выключенные "-имя.sc" пропускаются) в алфавитном порядке.
     * Ошибки чтения добавляются в {@code errors}, остальные файлы всё равно загружаются.
     */
    public static List<ScriptSource> readAll(List<String> errors) {
        List<ScriptSource> result = new ArrayList<>();
        for (String rel : listScripts()) {
            if (isDisabled(rel)) {
                continue;
            }
            ScriptSource source = readOne(rel, errors);
            if (source != null) {
                result.add(source);
            }
        }
        return result;
    }

    private static String fileNameOf(String relName) {
        return relName.substring(relName.lastIndexOf('/') + 1);
    }
}
