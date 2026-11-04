package com.scriptcraft.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.scriptcraft.ModPaths;
import com.scriptcraft.ScriptCraft;
import com.scriptcraft.registry.Bindings;
import com.scriptcraft.registry.ScriptRegistries;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Клиент: какие модели подставлять слотам пула (слот -> id из скрипта) и генерация этих JSON "на лету"
 * для скрытого ресурспака (см. {@link GeneratedAssetsPack}).
 *
 * Таблица сохраняется в scriptcraft/cache/bindings.json и читается при запуске игры: при повторном входе
 * на тот же сервер модели уже правильные — перезагрузка ресурсов не нужна.
 *
 * Что генерируется (assets/scriptcraft/...):
 * - blockstates/dyn_block_N.json — модель блока из ресурсов скрипта (assets/ns/blockstates/path.json
 *   из кэша, первый вариант; иначе модель ns:block/path; нет и её — куб с текстурой ns:block/path);
 * - models/item/dyn_block_N.json — модель ns:item/path, если есть, иначе модель блока;
 * - models/item/dyn_item_N.json — модель ns:item/path, иначе плоская иконка с текстурой ns:item/path;
 * - свободные слоты — "пустая" модель (чёрно-фиолетовый куб / иконка барьера).
 */
public final class AssetBindings {
    private static final String UNBOUND_BLOCK_MODEL = ScriptCraft.MODID + ":block/dyn_unbound";

    /** слот -> "ns:path". Заменяются целиком. */
    private static volatile Map<Integer, String> blocks = Map.of();
    private static volatile Map<Integer, String> items = Map.of();
    private static boolean loaded = false;

    private AssetBindings() {
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
            blocks = read(root.getAsJsonObject("blocks"), ScriptRegistries.BLOCK_SLOTS);
            items = read(root.getAsJsonObject("items"), ScriptRegistries.ITEM_SLOTS);
        } catch (IOException | RuntimeException e) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Не удалось прочитать {}: {}", file, e.getMessage());
        }
    }

    private static Map<Integer, String> read(@Nullable JsonObject obj, int size) {
        Map<Integer, String> map = new HashMap<>();
        if (obj != null) {
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                try {
                    int slot = Integer.parseInt(e.getKey());
                    if (slot >= 0 && slot < size && isId(e.getValue().getAsString())) {
                        map.put(slot, e.getValue().getAsString());
                    }
                } catch (RuntimeException ignored) {
                    // мусорная запись — пропускаем
                }
            }
        }
        return Map.copyOf(map);
    }

    private static boolean isId(String id) {
        return id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }

    private static Path file() {
        return ModPaths.cache().resolve("bindings.json");
    }

    /**
     * Новая таблица с сервера. Возвращает true, если раскладка моделей изменилась (нужна одна перезагрузка
     * ресурсов, чтобы модели "запеклись"); тогда же таблица сохраняется на диск.
     */
    public static synchronized boolean update(Bindings.Snapshot snapshot) {
        ensureLoaded();
        Map<Integer, String> b = new HashMap<>();
        snapshot.blockSlots().forEach((id, slot) -> b.put(slot, id));
        Map<Integer, String> i = new HashMap<>();
        snapshot.itemSlots().forEach((id, slot) -> i.put(slot, id));
        if (b.equals(blocks) && i.equals(items)) {
            return false;
        }
        blocks = Map.copyOf(b);
        items = Map.copyOf(i);
        JsonObject root = new JsonObject();
        JsonObject jb = new JsonObject();
        b.forEach((slot, id) -> jb.addProperty(Integer.toString(slot), id));
        JsonObject ji = new JsonObject();
        i.forEach((slot, id) -> ji.addProperty(Integer.toString(slot), id));
        root.add("blocks", jb);
        root.add("items", ji);
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), root.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Не удалось записать {}: {}", file(), e.getMessage());
        }
        return true;
    }

    // ------------------------------------------------------------------ генерация JSON

    /** Все пути (внутри assets/scriptcraft/), которые генерирует пак. */
    static List<String> generatedPaths() {
        List<String> paths = new ArrayList<>();
        paths.add("models/block/dyn_unbound.json");
        for (int s = 0; s < ScriptRegistries.BLOCK_SLOTS; s++) {
            paths.add("blockstates/" + ScriptRegistries.BLOCK_PREFIX + s + ".json");
            paths.add("models/block/" + ScriptRegistries.BLOCK_PREFIX + s + ".json");
            paths.add("models/item/" + ScriptRegistries.BLOCK_PREFIX + s + ".json");
        }
        for (int s = 0; s < ScriptRegistries.ITEM_SLOTS; s++) {
            paths.add("models/item/" + ScriptRegistries.ITEM_PREFIX + s + ".json");
        }
        return paths;
    }

    /** JSON для пути внутри assets/scriptcraft/ или null, если путь не наш. */
    @Nullable
    static byte[] generate(String path) {
        ensureLoaded();
        if (!path.endsWith(".json")) {
            return null;
        }
        String json;
        if (path.equals("models/block/dyn_unbound.json")) {
            json = "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"minecraft:missingno\"}}";
        } else if (path.startsWith("blockstates/")) {
            int slot = slot(path, "blockstates/", ScriptRegistries.BLOCK_PREFIX, ScriptRegistries.BLOCK_SLOTS);
            if (slot < 0) {
                return null;
            }
            json = blockstate(slot);
        } else if (path.startsWith("models/block/")) {
            int slot = slot(path, "models/block/", ScriptRegistries.BLOCK_PREFIX, ScriptRegistries.BLOCK_SLOTS);
            if (slot < 0) {
                return null;
            }
            String id = blocks.get(slot);
            String texture = id == null ? "minecraft:missingno" : ns(id) + ":block/" + path(id);
            json = "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"" + texture + "\"}}";
        } else if (path.startsWith("models/item/" + ScriptRegistries.BLOCK_PREFIX)) {
            int slot = slot(path, "models/item/", ScriptRegistries.BLOCK_PREFIX, ScriptRegistries.BLOCK_SLOTS);
            if (slot < 0) {
                return null;
            }
            String id = blocks.get(slot);
            String parent = id == null ? UNBOUND_BLOCK_MODEL
                    : hasAsset(id, "models/item/") ? ns(id) + ":item/" + path(id) : blockModel(slot, id);
            json = "{\"parent\":\"" + parent + "\"}";
        } else if (path.startsWith("models/item/" + ScriptRegistries.ITEM_PREFIX)) {
            int slot = slot(path, "models/item/", ScriptRegistries.ITEM_PREFIX, ScriptRegistries.ITEM_SLOTS);
            if (slot < 0) {
                return null;
            }
            String id = items.get(slot);
            if (id == null) {
                json = "{\"parent\":\"minecraft:item/barrier\"}";
            } else if (hasAsset(id, "models/item/")) {
                json = "{\"parent\":\"" + ns(id) + ":item/" + path(id) + "\"}";
            } else {
                json = "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + ns(id) + ":item/" + path(id) + "\"}}";
            }
        } else {
            return null;
        }
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /** Блок-слот рисуется одним вариантом "" (подходит ко всем состояниям, в т.ч. к любому light). */
    private static String blockstate(int slot) {
        String id = blocks.get(slot);
        if (id == null) {
            return variant("{\"model\":\"" + UNBOUND_BLOCK_MODEL + "\"}");
        }
        Path file = assetFile(id, "blockstates/");
        if (Files.isRegularFile(file)) {
            try {
                JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject variants = root.getAsJsonObject("variants");
                if (variants != null && !variants.entrySet().isEmpty()) {
                    // Свойств (facing и т.п.) у блока-слота нет — берём вариант "" или первый попавшийся.
                    JsonElement chosen = variants.has("") ? variants.get("") : variants.entrySet().iterator().next().getValue();
                    return variant(chosen.toString());
                }
            } catch (IOException | RuntimeException e) {
                ScriptCraft.LOGGER.warn("[ScriptCraft] Не удалось прочитать {}: {}", file, e.getMessage());
            }
        }
        return variant("{\"model\":\"" + blockModel(slot, id) + "\"}");
    }

    private static String variant(String value) {
        return "{\"variants\":{\"\":" + value + "}}";
    }

    /** Модель блока: своя ns:block/path, если есть в кэше, иначе сгенерированный куб с текстурой ns:block/path. */
    private static String blockModel(int slot, String id) {
        return hasAsset(id, "models/block/") ? ns(id) + ":block/" + path(id)
                : ScriptCraft.MODID + ":block/" + ScriptRegistries.BLOCK_PREFIX + slot;
    }

    private static boolean hasAsset(String id, String folder) {
        return Files.isRegularFile(assetFile(id, folder));
    }

    private static Path assetFile(String id, String folder) {
        return ModPaths.cacheAssets().resolve(ns(id)).resolve(folder + path(id) + ".json");
    }

    private static int slot(String path, String folder, String prefix, int size) {
        String name = path.substring(folder.length(), path.length() - ".json".length());
        return ScriptRegistries.parseSlot(name, prefix, size);
    }

    private static String ns(String id) {
        return id.substring(0, id.indexOf(':'));
    }

    private static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }
}
