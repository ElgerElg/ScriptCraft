package com.scriptcraft.registry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.scriptcraft.ModPaths;
import com.scriptcraft.ScriptCraft;
import com.scriptcraft.script.engine.ScriptProgram;
import com.scriptcraft.script.parser.BlockDefinition;
import com.scriptcraft.script.parser.ItemDefinition;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Раздача слотов пула id из скриптов (сервер).
 *
 * Два уровня хранения:
 * 1) мир — {@link BindingsData} (главное: в чанках лежат номера слотов, менять их нельзя);
 * 2) "подсказки" scriptcraft/bindings.json — id -> слот для всей папки сервера, только дописываются.
 *    Нужны до загрузки мира: чтобы перезаписать id в data-паке (теги, лут, рецепты) и чтобы переназначить
 *    блоки из старых миров (example:ruby_block из версии 0.1.6 и раньше) через MissingMappingsEvent.
 * Новый id получает свой слот-подсказку, если он свободен в мире, иначе первый свободный.
 */
public final class BindingStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, Integer> HINT_BLOCKS = new LinkedHashMap<>();
    private static final Map<String, Integer> HINT_ITEMS = new LinkedHashMap<>();
    @Nullable
    private static BindingsData world;
    /** id из скрипта -> технический id: для перезаписи JSON в data-паке. Заменяется целиком. */
    private static volatile Map<String, String> dataRewrite = Map.of();

    private BindingStore() {
    }

    /** Результат привязки. added — новые привязки (id -> технический id), full — id, которым не хватило слотов. */
    public record Result(Bindings.Snapshot snapshot, boolean changed, boolean dataChanged,
                         Map<String, String> added, List<String> full) {
    }

    // ------------------------------------------------------------------ запуск

    /**
     * Из конструктора мода (до загрузки мира): читаем подсказки и сразу закрепляем подсказки для define
     * из скриптов на диске, чтобы data-пак и переназначение старых миров знали слоты заранее.
     */
    public static synchronized void prepare(ScriptProgram program) {
        loadHints();
        boolean dirty = false;
        for (BlockDefinition b : program.blocks()) {
            dirty |= hint(b.id(), HINT_BLOCKS, ScriptRegistries.BLOCK_SLOTS);
        }
        for (ItemDefinition i : program.items()) {
            if (!HINT_BLOCKS.containsKey(i.id())) {
                dirty |= hint(i.id(), HINT_ITEMS, ScriptRegistries.ITEM_SLOTS);
            }
        }
        if (dirty) {
            saveHints();
        }
        dataRewrite = rewriteMap(HINT_BLOCKS, HINT_ITEMS);
    }

    private static boolean hint(String id, Map<String, Integer> hints, int size) {
        if (hints.containsKey(id)) {
            return false;
        }
        Set<Integer> used = new HashSet<>(hints.values());
        for (int s = 0; s < size; s++) {
            if (!used.contains(s)) {
                hints.put(id, s);
                return true;
            }
        }
        return false; // подсказок больше нет — слот всё равно выдаст мир
    }

    /** Слот-подсказка для старого id блока (MissingMappingsEvent) или null. */
    @Nullable
    public static synchronized Integer hintBlockSlot(String id) {
        return HINT_BLOCKS.get(id);
    }

    @Nullable
    public static synchronized Integer hintItemSlot(String id) {
        return HINT_ITEMS.get(id);
    }

    public static Map<String, String> dataRewrite() {
        return dataRewrite;
    }

    // ------------------------------------------------------------------ мир

    /** Привязать define текущей программы к слотам мира сервера и выставить {@link Bindings}. */
    public static synchronized Result bind(MinecraftServer server, ScriptProgram program) {
        if (world == null) {
            ServerLevel overworld = server.overworld();
            world = overworld != null
                    ? overworld.getDataStorage().computeIfAbsent(BindingsData::load, BindingsData::new, BindingsData.NAME)
                    : new BindingsData(); // мира ещё нет (не должно случаться) — хотя бы в памяти
        }
        boolean[] hintsDirty = {false};
        boolean[] worldDirty = {false};
        List<String> full = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        Map<Integer, BlockDefinition> blocks = new HashMap<>();
        for (BlockDefinition b : program.blocks()) {
            if (!seen.add(b.id())) {
                continue; // дубликаты id уже отмечены парсером/предупреждением
            }
            int slot = allocate(b.id(), world.blocks, HINT_BLOCKS, ScriptRegistries.BLOCK_SLOTS, hintsDirty, worldDirty);
            if (slot < 0) {
                full.add(b.id());
            } else {
                blocks.put(slot, b);
            }
        }
        Map<Integer, ItemDefinition> items = new HashMap<>();
        for (ItemDefinition i : program.items()) {
            if (!seen.add(i.id())) {
                ScriptCraft.LOGGER.warn("[ScriptCraft] id '{}' уже занят, предмет пропущен", i.id());
                continue;
            }
            int slot = allocate(i.id(), world.items, HINT_ITEMS, ScriptRegistries.ITEM_SLOTS, hintsDirty, worldDirty);
            if (slot < 0) {
                full.add(i.id());
            } else {
                items.put(slot, i);
            }
        }
        if (hintsDirty[0]) {
            saveHints();
        }
        if (worldDirty[0]) {
            world.setDirty();
        }

        Bindings.Snapshot old = Bindings.current();
        Bindings.Snapshot snapshot = new Bindings.Snapshot(blocks, items);
        Bindings.set(snapshot);

        // Новые привязки (или переехавшие в другой слот) — для сообщения администратору.
        Map<String, String> added = new LinkedHashMap<>();
        List<String> ids = new ArrayList<>(snapshot.blockSlots().keySet());
        ids.addAll(snapshot.itemSlots().keySet());
        for (String id : ids) {
            String now = snapshot.poolId(id);
            if (now != null && !now.equals(old.poolId(id))) {
                added.put(id, now);
            }
        }

        // Перезаписываем в data-паке все id мира (и удалённые: их слоты зарезервированы, вреда нет).
        Map<String, String> rewrite = rewriteMap(world.blocks, world.items);
        boolean dataChanged = !rewrite.equals(dataRewrite);
        dataRewrite = rewrite;
        return new Result(snapshot, !snapshot.equals(old), dataChanged, added, full);
    }

    private static int allocate(String id, Map<String, Integer> worldMap, Map<String, Integer> hints, int size,
                                boolean[] hintsDirty, boolean[] worldDirty) {
        Integer existing = worldMap.get(id);
        if (existing != null && existing >= 0 && existing < size) {
            return existing;
        }
        Set<Integer> usedInWorld = new HashSet<>(worldMap.values());
        int chosen = -1;
        Integer hint = hints.get(id);
        if (hint != null && hint >= 0 && hint < size && !usedInWorld.contains(hint)) {
            chosen = hint;
        } else {
            Set<Integer> hinted = new HashSet<>(hints.values());
            for (int s = 0; s < size && chosen < 0; s++) {
                if (!usedInWorld.contains(s) && !hinted.contains(s)) {
                    chosen = s; // свободен и в мире, и в подсказках — лучший вариант
                }
            }
            for (int s = 0; s < size && chosen < 0; s++) {
                if (!usedInWorld.contains(s)) {
                    chosen = s;
                }
            }
        }
        if (chosen < 0) {
            return -1;
        }
        worldMap.put(id, chosen);
        worldDirty[0] = true;
        if (!hints.containsKey(id) && !hints.containsValue(chosen)) {
            hints.put(id, chosen);
            hintsDirty[0] = true;
        }
        ScriptCraft.LOGGER.info("[ScriptCraft] {} привязан к слоту {}", id, chosen);
        return chosen;
    }

    /** Остановка сервера: мир выгружен, привязки сбрасываются (следующий мир — свои). */
    public static synchronized void onServerStopped() {
        world = null;
        Bindings.set(Bindings.Snapshot.EMPTY);
        dataRewrite = rewriteMap(HINT_BLOCKS, HINT_ITEMS);
    }

    private static Map<String, String> rewriteMap(Map<String, Integer> blocks, Map<String, Integer> items) {
        Map<String, String> map = new HashMap<>();
        items.forEach((id, slot) -> {
            if (slot >= 0 && slot < ScriptRegistries.ITEM_SLOTS) {
                map.put(id, ScriptRegistries.itemSlotId(slot).toString());
            }
        });
        blocks.forEach((id, slot) -> {
            if (slot >= 0 && slot < ScriptRegistries.BLOCK_SLOTS) {
                map.put(id, ScriptRegistries.blockSlotId(slot).toString());
            }
        });
        return Map.copyOf(map);
    }

    // ------------------------------------------------------------------ файл подсказок

    private static Path hintsFile() {
        return ModPaths.root().resolve("bindings.json");
    }

    private static void loadHints() {
        HINT_BLOCKS.clear();
        HINT_ITEMS.clear();
        Path file = hintsFile();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            readHints(root.getAsJsonObject("blocks"), HINT_BLOCKS);
            readHints(root.getAsJsonObject("items"), HINT_ITEMS);
        } catch (IOException | RuntimeException e) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Не удалось прочитать {}: {}", file, e.getMessage());
        }
    }

    private static void readHints(@Nullable JsonObject obj, Map<String, Integer> into) {
        if (obj == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            into.put(e.getKey(), e.getValue().getAsInt());
        }
    }

    private static void saveHints() {
        JsonObject root = new JsonObject();
        root.addProperty("_comment", "ScriptCraft: id -> slot hints (scriptcraft:dyn_block_N / dyn_item_N). The world keeps its own copy; do not edit.");
        JsonObject b = new JsonObject();
        HINT_BLOCKS.forEach(b::addProperty);
        JsonObject i = new JsonObject();
        HINT_ITEMS.forEach(i::addProperty);
        root.add("blocks", b);
        root.add("items", i);
        try {
            Files.writeString(hintsFile(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Не удалось записать {}: {}", hintsFile(), e.getMessage());
        }
    }
}
