package com.scriptcraft.resources;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.scriptcraft.ModPaths;
import com.scriptcraft.ScriptCraft;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Клиент: кэш id define со всех серверов, на которые заходил игрок (scriptcraft/cache/defines.json).
 * При запуске игры эти id регистрируются настоящими записями (как у сервера), поэтому после одного перезапуска
 * клиента блоки нового сервера видны под своими id: F3, ванильные /give и автодополнение.
 *
 * Объединение по всем серверам, а не по адресу: регистрация происходит до выбора сервера, а лишние записи
 * клиента Forge при входе просто отключает на время сессии (набор записей задаёт сервер). Цена — лишние id
 * других серверов в автодополнении ванильных команд.
 *
 * Файл только дописывается. Удалить лишнее — удалить файл (он восстановится при следующих входах).
 */
public final class DefineCache {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    /** Защита от разрастания файла мусором. */
    private static final int MAX_IDS = 4096;

    private static final Set<String> BLOCKS = new LinkedHashSet<>();
    private static final Set<String> ITEMS = new LinkedHashSet<>();
    private static boolean loaded = false;

    private DefineCache() {
    }

    private static Path file() {
        return ModPaths.cache().resolve("defines.json");
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            read(root.getAsJsonArray("blocks"), BLOCKS);
            read(root.getAsJsonArray("items"), ITEMS);
        } catch (IOException | RuntimeException e) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Не удалось прочитать {}: {}", file, e.getMessage());
        }
    }

    private static void read(@Nullable JsonArray arr, Set<String> into) {
        if (arr == null) {
            return;
        }
        for (JsonElement e : arr) {
            try {
                String id = e.getAsString();
                if (ID.matcher(id).matches()) {
                    into.add(id);
                }
            } catch (RuntimeException ignored) {
                // мусор — пропускаем
            }
        }
    }

    public static synchronized Set<String> blocks() {
        ensureLoaded();
        return Collections.unmodifiableSet(new LinkedHashSet<>(BLOCKS));
    }

    public static synchronized Set<String> items() {
        ensureLoaded();
        return Collections.unmodifiableSet(new LinkedHashSet<>(ITEMS));
    }

    /** Дописывает id (с сервера или из MissingMappings). true — файл изменился (нужен перезапуск клиента). */
    public static synchronized boolean add(Collection<String> blockIds, Collection<String> itemIds) {
        ensureLoaded();
        boolean changed = false;
        for (String id : blockIds) {
            if (ID.matcher(id).matches() && BLOCKS.size() + ITEMS.size() < MAX_IDS) {
                changed |= BLOCKS.add(id);
            }
        }
        for (String id : itemIds) {
            if (ID.matcher(id).matches() && !BLOCKS.contains(id) && BLOCKS.size() + ITEMS.size() < MAX_IDS) {
                changed |= ITEMS.add(id);
            }
        }
        if (changed) {
            save();
        }
        return changed;
    }

    private static void save() {
        JsonObject root = new JsonObject();
        root.addProperty("_comment", "ScriptCraft: ids of script blocks/items from servers you joined; registered as real ids at game start");
        JsonArray b = new JsonArray();
        BLOCKS.forEach(b::add);
        JsonArray i = new JsonArray();
        ITEMS.forEach(i::add);
        root.add("blocks", b);
        root.add("items", i);
        try {
            Files.createDirectories(file().getParent());
            Path tmp = file().resolveSibling("defines.json.tmp");
            Files.writeString(tmp, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, file(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Не удалось записать {}: {}", file(), e.getMessage());
        }
    }
}
