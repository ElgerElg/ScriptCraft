package com.scriptcraft.script;

import com.scriptcraft.ScriptCraft;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Работа с папкой скриптов: <папка игры>/scripts/*.sk */
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
     * Читает все *.sk файлы (включая подпапки) в алфавитном порядке.
     * Ошибки чтения добавляются в {@code errors}, остальные файлы всё равно загружаются.
     */
    public static List<ScriptSource> readAll(List<String> errors) {
        List<ScriptSource> result = new ArrayList<>();
        Path dir = scriptsDir();
        if (!Files.isDirectory(dir)) {
            return result;
        }
        List<Path> files;
        try (Stream<Path> stream = Files.walk(dir, 4)) {
            files = stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(EXTENSION))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            errors.add("Не удалось прочитать папку скриптов: " + e.getMessage());
            return result;
        }
        for (Path file : files) {
            try {
                // Файлы читаются в UTF-8, чтобы русский текст отображался правильно.
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                result.add(new ScriptSource(dir.relativize(file).toString().replace('\\', '/'), lines));
            } catch (IOException e) {
                errors.add(file.getFileName() + ": ошибка чтения: " + e.getMessage());
            }
        }
        return result;
    }
}
