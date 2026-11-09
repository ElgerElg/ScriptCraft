package com.scriptcraft.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.scriptcraft.ModPaths;
import com.scriptcraft.ScriptCraft;
import com.scriptcraft.registry.Bindings;
import com.scriptcraft.registry.ScriptRegistries;
import com.scriptcraft.script.parser.BlockDefinition;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 *
 * Поворот (define block ... facing: horizontal|all): blockstate слота получает варианты facing=north..down
 * с поворотом выбранной модели (x/y), как у печи и раздатчика. Вид поворота каждого id тоже хранится в файле.
 * Свой blockstate автора без ключей facing= поворачивается так же. Если ключи facing= уже написаны — файл не трогаем.
 *
 * Прозрачность (render: cutout | translucent) пишется в модель как render_type. У сгенерированного куба — сразу,
 * у модели автора — дописывается, если своего render_type в файле нет.
 *
 * Настоящие id (example:ruby_block, зарегистрированы при запуске): если в ресурсах скрипта нет своего
 * blockstate / модели блока / модели предмета, они генерируются в namespace самого id по тем же правилам.
 */
public final class AssetBindings {
    private static final String UNBOUND_BLOCK_MODEL = ScriptCraft.MODID + ":block/dyn_unbound";

    /** слот -> "ns:path". Заменяются целиком. */
    private static volatile Map<Integer, String> blocks = Map.of();
    private static volatile Map<Integer, String> items = Map.of();
    /** id блока -> вид поворота (none | horizontal | all). */
    private static volatile Map<String, String> facings = Map.of();
    /** id блока -> слой текстуры (solid | cutout | translucent). */
    private static volatile Map<String, String> renders = Map.of();
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
            JsonObject f = root.getAsJsonObject("facing");
            if (f != null) {
                Map<String, String> map = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : f.entrySet()) {
                    String kind = BlockDefinition.parseFacing(e.getValue().getAsString());
                    if (isId(e.getKey()) && kind != null) {
                        map.put(e.getKey(), kind);
                    }
                }
                facings = Map.copyOf(map);
            }
            renders = readRenders(root.getAsJsonObject("render"));
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

    private static Map<String, String> readRenders(@Nullable JsonObject obj) {
        Map<String, String> map = new HashMap<>();
        if (obj != null) {
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                String kind = BlockDefinition.parseRender(e.getValue().getAsString());
                if (isId(e.getKey()) && kind != null) {
                    map.put(e.getKey(), kind);
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
        Map<String, String> f = new HashMap<>();
        snapshot.realBlocks().forEach((id, def) -> f.put(id, def.facing()));
        snapshot.blockSlots().keySet().forEach(id -> {
            BlockDefinition def = snapshot.blockById(id);
            if (def != null) {
                f.put(id, def.facing());
            }
        });
        // Виды поворота других серверов (настоящие id из кэша) не забываем — дописываем.
        Map<String, String> r = new HashMap<>();
        snapshot.realBlocks().forEach((id, def) -> r.put(id, def.render()));
        snapshot.blockSlots().keySet().forEach(id -> {
            BlockDefinition def = snapshot.blockById(id);
            if (def != null) {
                r.put(id, def.render());
            }
        });
        Map<String, String> mergedFacings = new HashMap<>(facings);
        mergedFacings.putAll(f);
        Map<String, String> mergedRenders = new HashMap<>(renders);
        mergedRenders.putAll(r);
        if (b.equals(blocks) && i.equals(items) && mergedFacings.equals(facings) && mergedRenders.equals(renders)) {
            return false;
        }
        blocks = Map.copyOf(b);
        items = Map.copyOf(i);
        facings = Map.copyOf(mergedFacings);
        renders = Map.copyOf(mergedRenders);
        JsonObject root = new JsonObject();
        JsonObject jb = new JsonObject();
        b.forEach((slot, id) -> jb.addProperty(Integer.toString(slot), id));
        JsonObject ji = new JsonObject();
        i.forEach((slot, id) -> ji.addProperty(Integer.toString(slot), id));
        root.add("blocks", jb);
        root.add("items", ji);
        JsonObject jf = new JsonObject();
        mergedFacings.forEach(jf::addProperty);
        root.add("facing", jf);
        JsonObject jr = new JsonObject();
        mergedRenders.forEach(jr::addProperty);
        root.add("render", jr);
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
            json = cube(texture, renderOf(id));
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

    /**
     * Блок-слот: один вариант "" (подходит ко всем состояниям, в т.ч. к любому light) или, у поворотных,
     * варианты facing=... с поворотом той же модели.
     */
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
                    // Варианты автора (facing=east и т.п.) не переносим: берём "" (или facing=north, или первый)
                    // и поворачиваем сами — так работает любой blockstate.
                    JsonElement chosen = variants.has("") ? variants.get("")
                            : variants.has("facing=north") ? variants.get("facing=north")
                            : variants.entrySet().iterator().next().getValue();
                    return rotated(chosen, facings.getOrDefault(id, BlockDefinition.FACING_NONE));
                }
            } catch (IOException | RuntimeException e) {
                ScriptCraft.LOGGER.warn("[ScriptCraft] Не удалось прочитать {}: {}", file, e.getMessage());
            }
        }
        return rotated(JsonParser.parseString("{\"model\":\"" + blockModel(slot, id) + "\"}"),
                facings.getOrDefault(id, BlockDefinition.FACING_NONE));
    }

    private static String variant(String value) {
        return "{\"variants\":{\"\":" + value + "}}";
    }

    /** Направление -> поворот модели {x, y}, как у ванильных печи/раздатчика (модель смотрит на север). */
    private static final String[][] FACING_ROTATIONS = {
            {"north", "0", "0"}, {"east", "0", "90"}, {"south", "0", "180"}, {"west", "0", "270"},
            {"up", "270", "0"}, {"down", "90", "0"}};

    /** blockstate из одного варианта: без поворота — "", иначе 6 вариантов facing=... (horizontal: up/down как north). */
    static String rotated(JsonElement model, String kind) {
        if (BlockDefinition.FACING_NONE.equals(kind)) {
            return variant(model.toString());
        }
        JsonObject variants = new JsonObject();
        for (String[] r : FACING_ROTATIONS) {
            boolean vertical = r[0].equals("up") || r[0].equals("down");
            int x = vertical && BlockDefinition.FACING_HORIZONTAL.equals(kind) ? 0 : Integer.parseInt(r[1]);
            int y = Integer.parseInt(r[2]);
            variants.add("facing=" + r[0], rotate(model, x, y));
        }
        JsonObject root = new JsonObject();
        root.add("variants", variants);
        return root.toString();
    }

    /** Добавляет x/y к варианту (объект или массив случайных вариантов), складывая с уже заданными. */
    private static JsonElement rotate(JsonElement model, int x, int y) {
        if (model.isJsonArray()) {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            model.getAsJsonArray().forEach(e -> arr.add(rotate(e, x, y)));
            return arr;
        }
        JsonObject o = model.getAsJsonObject().deepCopy();
        int nx = (o.has("x") ? o.get("x").getAsInt() : 0) + x;
        int ny = (o.has("y") ? o.get("y").getAsInt() : 0) + y;
        o.remove("x");
        o.remove("y");
        if (nx % 360 != 0) {
            o.addProperty("x", ((nx % 360) + 360) % 360);
        }
        if (ny % 360 != 0) {
            o.addProperty("y", ((ny % 360) + 360) % 360);
        }
        return o;
    }

    // ------------------------------------------------------------------ настоящие id

    /** Namespace настоящих id клиента (для пака: ему нужно объявить их). */
    static Set<String> realNamespaces() {
        Set<String> out = new HashSet<>();
        ScriptRegistries.realBlockIds().forEach(id -> out.add(ns(id)));
        ScriptRegistries.realItemIds().forEach(id -> out.add(ns(id)));
        return out;
    }

    /** Пути (внутри assets/<namespace>/), которые генерируются для настоящих id: только отсутствующие в кэше. */
    static List<String> generatedRealPaths(String namespace) {
        List<String> paths = new ArrayList<>();
        for (String id : ScriptRegistries.realBlockIds()) {
            if (ns(id).equals(namespace)) {
                for (String folder : List.of("blockstates/", "models/block/", "models/item/")) {
                    if (!hasAsset(id, folder)) {
                        paths.add(folder + path(id) + ".json");
                    }
                }
            }
        }
        for (String id : ScriptRegistries.realItemIds()) {
            if (ns(id).equals(namespace) && !hasAsset(id, "models/item/")) {
                paths.add("models/item/" + path(id) + ".json");
            }
        }
        return paths;
    }

    /** JSON для настоящего id (assets/<namespace>/<path>) или null — не наш путь или файл автора есть в кэше. */
    @Nullable
    static byte[] generateReal(String namespace, String path) {
        ensureLoaded();
        if (!path.endsWith(".json")) {
            return null;
        }
        String json = null;
        for (String folder : List.of("blockstates/", "models/block/", "models/item/")) {
            if (!path.startsWith(folder)) {
                continue;
            }
            String id = namespace + ":" + path.substring(folder.length(), path.length() - ".json".length());
            boolean block = ScriptRegistries.isRealBlock(id);
            boolean item = ScriptRegistries.isRealItem(id);
            if ((!block && !item) || hasAsset(id, folder)) {
                return null;
            }
            String texture = namespace + ":" + (block ? "block/" : "item/") + path(id);
            if (folder.equals("blockstates/") && block) {
                String model = namespace + ":block/" + path(id);
                json = rotated(JsonParser.parseString("{\"model\":\"" + model + "\"}"),
                        facings.getOrDefault(id, BlockDefinition.FACING_NONE));
            } else if (folder.equals("models/block/") && block) {
                json = cube(texture, renderOf(id));
            } else if (folder.equals("models/item/")) {
                json = block ? "{\"parent\":\"" + namespace + ":block/" + path(id) + "\"}"
                        : "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + texture + "\"}}";
            }
        }
        return json == null ? null : json.getBytes(StandardCharsets.UTF_8);
    }

    /** Слой текстуры для модели или null, если куб обычный непрозрачный. */
    @Nullable
    private static String renderOf(@Nullable String id) {
        if (id == null) {
            return null;
        }
        String render = renders.get(id);
        return render == null || BlockDefinition.RENDER_SOLID.equals(render) ? null : render;
    }

    private static String cube(String texture, @Nullable String render) {
        String extra = render == null ? "" : ",\"render_type\":\"" + render + "\"";
        return "{\"parent\":\"minecraft:block/cube_all\"" + extra + ",\"textures\":{\"all\":\"" + texture + "\"}}";
    }

    /**
     * Файл автора, который надо отдать иначе, чем он лежит на диске.
     * Blockstate без ключей facing= поворачивается сам. В модель без render_type дописывается слой из скрипта.
     * null — отдавать файл как есть.
     */
    @Nullable
    static byte[] patchAuthor(String namespace, String path) {
        ensureLoaded();
        if (!path.endsWith(".json")) {
            return null;
        }
        boolean blockstate = path.startsWith("blockstates/");
        boolean model = path.startsWith("models/block/");
        if (!blockstate && !model) {
            return null;
        }
        String folder = blockstate ? "blockstates/" : "models/block/";
        String name = path.substring(folder.length(), path.length() - ".json".length());
        String id = namespace + ":" + name;
        if (!isId(id) || (!facings.containsKey(id) && !renders.containsKey(id))) {
            return null;
        }
        Path file = ModPaths.cacheAssets().resolve(namespace).resolve(path);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            String facing = facings.getOrDefault(id, BlockDefinition.FACING_NONE);
            String render = renderOf(id);
            boolean rewriteFacing = blockstate && !BlockDefinition.FACING_NONE.equals(facing) && !text.contains("facing=");
            boolean rewriteRender = model && render != null && !text.contains("\"render_type\"");
            if (!rewriteFacing && !rewriteRender) {
                return null;
            }
            if (rewriteFacing) {
                JsonObject root = JsonParser.parseString(text).getAsJsonObject();
                JsonObject variants = root.getAsJsonObject("variants");
                if (variants == null || variants.entrySet().isEmpty()) {
                    return null;
                }
                JsonElement chosen = variants.has("") ? variants.get("")
                        : variants.has("facing=north") ? variants.get("facing=north")
                        : variants.entrySet().iterator().next().getValue();
                return rotated(chosen, facing).getBytes(StandardCharsets.UTF_8);
            }
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            root.addProperty("render_type", render);
            return root.toString().getBytes(StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Не удалось поправить {}: {}", file, e.getMessage());
            return null;
        }
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

    /**
     * Blockbench пишет текстуру как {@code textures/block/имя} без namespace.
     * Minecraft тогда ищет {@code minecraft:textures/block/имя} и рисует отсутствующую текстуру.
     * Здесь путь приводится к {@code namespace:block/имя}.
     */
    static byte[] dressTextures(String namespace, String path, byte[] data) {
        if (data == null || !path.startsWith("models/") || !path.endsWith(".json")) {
            return data;
        }
        try {
            JsonElement parsed = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return data;
            }
            JsonObject textures = parsed.getAsJsonObject().getAsJsonObject("textures");
            if (textures == null) {
                return data;
            }
            boolean changed = false;
            for (String key : new ArrayList<>(textures.keySet())) {
                JsonElement value = textures.get(key);
                if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    continue;
                }
                String fixed = textureRef(namespace, value.getAsString());
                if (!fixed.equals(value.getAsString())) {
                    textures.addProperty(key, fixed);
                    changed = true;
                }
            }
            return changed ? parsed.toString().getBytes(StandardCharsets.UTF_8) : data;
        } catch (RuntimeException e) {
            return data;
        }
    }

    private static String textureRef(String namespace, String value) {
        if (value.startsWith("#")) {
            return value;
        }
        String ns = namespace;
        String body = value;
        int colon = value.indexOf(':');
        if (colon >= 0) {
            ns = value.substring(0, colon);
            body = value.substring(colon + 1);
        }
        if (body.startsWith("textures/")) {
            body = body.substring("textures/".length());
        }
        if (body.endsWith(".png")) {
            body = body.substring(0, body.length() - 4);
        }
        if (ns.isEmpty()) {
            ns = namespace;
        }
        return ns + ":" + body;
    }
}
