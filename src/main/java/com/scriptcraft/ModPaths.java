package com.scriptcraft;

import com.scriptcraft.script.ScriptPaths;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/**
 * Единая папка мода в папке игры/сервера:
 * <pre>
 * scriptcraft/
 *   config.toml          настройки (Forge config)
 *   scripts/             *.sc (выключенные: -имя.sc; старые *.sk переименовываются при запуске)
 *   variables/           variables.json + резервные копии variables-ГГГГММДД-ЧЧММСС.json
 *   resources/           assets/&lt;ns&gt;/... (текстуры, модели, lang) и data/&lt;ns&gt;/... (лут, теги, рецепты)
 *   cache/assets/        (клиент) ресурсы, полученные с сервера; подключены скрытым встроенным ресурспаком мода
 * </pre>
 */
public final class ModPaths {
    /** Имя файла конфига относительно папки config/ (Forge разрешает путь от неё). */
    public static final String CONFIG_FILE = "../scriptcraft/config.toml";

    /** Примеры внутри jar (build.gradle -> processResources -> scriptcraft_defaults/). Копируются при первом запуске. */
    private static final List<String> DEFAULT_FILES = List.of(
            "scripts/welcome.sc",
            "scripts/ruby.sc",
            "scripts/menu.sc",
            "scripts/hud.sc",
            "resources/assets/example/blockstates/ruby_block.json",
            "resources/assets/example/models/block/ruby_block.json",
            "resources/assets/example/models/item/ruby_block.json",
            "resources/assets/example/models/item/ruby.json",
            "resources/assets/example/textures/block/ruby_block.png",
            "resources/assets/example/textures/item/ruby.png",
            "resources/assets/example/textures/gui/menu_banner.png",
            "resources/assets/example/textures/gui/bar_frame.png",
            "resources/assets/example/textures/gui/bar_fill_hp.png",
            "resources/assets/example/textures/gui/bar_fill_food.png",
            "resources/assets/example/textures/gui/coin.png",
            "resources/assets/example/lang/en_us.json",
            "resources/assets/example/lang/ru_ru.json",
            "resources/data/example/loot_tables/blocks/ruby_block.json",
            "resources/data/minecraft/tags/blocks/mineable/pickaxe.json");

    /**
     * Примеры, которые копируются ВЫКЛЮЧЕННЫМИ ("-имя.sc"): hud.sc прячет ванильный HUD всем игрокам при входе,
     * поэтому включать его по умолчанию нельзя — только командой /sc enable hud.
     */
    private static final java.util.Set<String> DISABLED_DEFAULTS = java.util.Set.of("scripts/hud.sc");

    private ModPaths() {
    }

    public static Path root() {
        return FMLPaths.GAMEDIR.get().resolve("scriptcraft");
    }

    public static Path scripts() {
        return root().resolve("scripts");
    }

    public static Path variables() {
        return root().resolve("variables");
    }

    public static Path resources() {
        return root().resolve("resources");
    }

    public static Path cache() {
        return root().resolve("cache");
    }

    /** Клиентский кэш ресурсов сервера: cache/assets/<ns>/... (корень встроенного ресурспака — cache/). */
    public static Path cacheAssets() {
        return cache().resolve("assets");
    }

    /**
     * Создаёт структуру папок. Вызывается первым делом в конструкторе мода (до регистрации конфига).
     * 1) Если есть старая папка <игра>/scripts (прежние версии мода), а новой ещё нет — переносим её.
     * 2) Если папки scripts нет совсем — копируем примеры скриптов из jar.
     * 3) Если папки resources нет — копируем примеры ресурсов (namespace example) из jar.
     */
    public static void ensureLayout() {
        try {
            Files.createDirectories(root());
            Files.createDirectories(variables());
            Files.createDirectories(cacheAssets());

            Path oldScripts = FMLPaths.GAMEDIR.get().resolve("scripts");
            if (!Files.exists(scripts()) && Files.isDirectory(oldScripts)) {
                migrateOldScripts(oldScripts);
            }
            if (!Files.exists(scripts())) {
                Files.createDirectories(scripts());
                copyDefaults("scripts/");
            }
            if (!Files.exists(resources())) {
                Files.createDirectories(resources());
                copyDefaults("resources/");
            }
            ScriptPaths.migrateLegacyExtension();
        } catch (IOException e) {
            ScriptCraft.LOGGER.error("[ScriptCraft] Не удалось создать папку {}", root(), e);
        }
    }

    /** Переносит старую папку scripts/ в scriptcraft/scripts/ (перемещение, а если не вышло — копирование). */
    private static void migrateOldScripts(Path oldScripts) throws IOException {
        try {
            Files.move(oldScripts, scripts());
            ScriptCraft.LOGGER.info("[ScriptCraft] Старая папка {} перенесена в {}", oldScripts, scripts());
        } catch (IOException moveFailed) {
            // Например, другой диск или файл занят — копируем, старую папку оставляем как есть.
            try (Stream<Path> walk = Files.walk(oldScripts)) {
                for (Path p : (Iterable<Path>) walk::iterator) {
                    Path target = scripts().resolve(oldScripts.relativize(p).toString());
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(target);
                    } else {
                        Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            ScriptCraft.LOGGER.warn("[ScriptCraft] Папка {} скопирована в {}. Старую папку можно удалить вручную.", oldScripts, scripts());
        }
    }

    private static void copyDefaults(String prefix) throws IOException {
        for (String rel : DEFAULT_FILES) {
            if (!rel.startsWith(prefix)) {
                continue;
            }
            Path target = root().resolve(rel);
            if (DISABLED_DEFAULTS.contains(rel)) {
                target = target.resolveSibling("-" + target.getFileName());
            }
            if (Files.exists(target)) {
                continue;
            }
            try (InputStream in = ModPaths.class.getResourceAsStream("/scriptcraft_defaults/" + rel)) {
                if (in != null) {
                    Files.createDirectories(target.getParent());
                    Files.copy(in, target);
                }
            }
        }
        ScriptCraft.LOGGER.info("[ScriptCraft] Созданы примеры в {}", root().resolve(prefix));
    }
}
