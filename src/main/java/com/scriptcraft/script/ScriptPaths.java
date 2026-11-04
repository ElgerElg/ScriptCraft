package com.scriptcraft.script;

import com.scriptcraft.ScriptCraft;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Работа с папкой скриптов: <папка игры>/scripts/*.sk
 * Выключенные скрипты (как в Skript) — файлы с минусом в начале имени: -welcome.sk. Они не загружаются.
 */
public final class ScriptPaths {
    /** Расширение файлов скриптов. */
    public static final String EXTENSION = ".sk";

    /** Примеры, которые лежат внутри jar (см. build.gradle -> processResources) и копируются при первом запуске. */
    private static final String[] DEFAULT_SCRIPTS = {"welcome.sk", "ruby.sk", "menu.sk"};

    private ScriptPaths() {
    }

    /** Папка скриптов. На сервере — рядом с server.properties, в dev-режиме — run/scripts. */
    public static Path scriptsDir() {
        return FMLPaths.GAMEDIR.get().resolve("scripts");
    }

    /** Создаёт папку scripts. Если её не было — копирует туда примеры из jar. */
    public static void ensureScriptsFolder() {
        Path dir = scriptsDir();
        if (Files.isDirectory(dir)) {
            return;
        }
        try {
            Files.createDirectories(dir);
            for (String name : DEFAULT_SCRIPTS) {
                try (InputStream in = ScriptPaths.class.getResourceAsStream("/scriptcraft_defaults/" + name)) {
                    if (in != null) {
                        Files.copy(in, dir.resolve(name));
                    }
                }
            }
            ScriptCraft.LOGGER.info("[ScriptCraft] Создана папка скриптов с примерами: {}", dir);
        } catch (IOException e) {
            ScriptCraft.LOGGER.error("[ScriptCraft] Не удалось создать папку скриптов {}", dir, e);
        }
    }

    /**
     * Все файлы скриптов (включая выключенные "-имя.sk"), пути относительно папки scripts, через '/'.
     * Отсортированы по алфавиту.
     */
    public static List<String> listScripts() {
        Path dir = scriptsDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(dir, 4)) {
            return stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(EXTENSION))
                    .map(p -> dir.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Выключен ли скрипт: имя файла (не папки) начинается с '-', как в Skript. */
    public static boolean isDisabled(String relName) {
        return fileNameOf(relName).startsWith("-");
    }

    /** "sub/-a.sk" -> "sub/a.sk" */
    public static String enabledName(String relName) {
        int slash = relName.lastIndexOf('/');
        String file = fileNameOf(relName);
        while (file.startsWith("-")) {
            file = file.substring(1);
        }
        return relName.substring(0, slash + 1) + file;
    }

    /** "sub/a.sk" -> "sub/-a.sk" */
    public static String disabledName(String relName) {
        String enabled = enabledName(relName);
        int slash = enabled.lastIndexOf('/');
        return enabled.substring(0, slash + 1) + "-" + enabled.substring(slash + 1);
    }

    /**
     * Ввод игрока -> нормальное (включённое) имя скрипта: "welcome", "welcome.sk", "-welcome.sk" -> "welcome.sk".
     * Возвращает null, если путь выходит за пределы папки scripts (защита от "../").
     */
    @Nullable
    public static String normalize(String input) {
        String name = input.strip().replace('\\', '/');
        if (name.isEmpty() || name.contains("..") || name.startsWith("/") || name.contains(":")) {
            return null;
        }
        if (!name.endsWith(EXTENSION)) {
            name = name + EXTENSION;
        }
        return enabledName(name);
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
     * Читает все ВКЛЮЧЁННЫЕ *.sk файлы (выключенные "-имя.sk" пропускаются) в алфавитном порядке.
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
