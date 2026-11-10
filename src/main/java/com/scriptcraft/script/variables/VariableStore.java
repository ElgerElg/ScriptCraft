package com.scriptcraft.script.variables;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.scriptcraft.ModPaths;
import com.scriptcraft.ScriptCraft;
import com.scriptcraft.config.ScriptCraftConfig;
import com.scriptcraft.hud.HudManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TreeMap;

/**
 * Хранилище переменных скриптов ({name}).
 *
 * Где лежат: ОДИН общий файл <папка игры>/scriptcraft/variables/variables.json (не зависит от мира).
 * Так все данные мода в одной папке, и их удобно переносить и бэкапить. Минус: в одиночной игре
 * все миры видят одни и те же переменные (если нужно разделять — добавляйте имя мира в имя переменной).
 * При каждом старте сервера делается резервная копия variables-ГГГГММДД-ЧЧММСС.json,
 * хранятся последние variableBackups штук.
 * Миграция: если общего файла ещё нет, а в мире лежит старый <мир>/scriptcraft/variables.json (v0.1) — он копируется.
 *
 * Когда сохраняются: раз в variablesSaveIntervalSeconds (если что-то менялось) и при остановке сервера.
 * Временные переменные (имя начинается с '-', например {-var} или {--var}) при enableTemporaryVariables=true
 * живут только в памяти и очищаются при старте и остановке сервера.
 *
 * Значения: число (Double), текст (String) или логическое (Boolean).
 * Все методы вызываются из главного потока сервера, поэтому синхронизация не нужна.
 */
public final class VariableStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final Map<String, Object> PERSISTENT = new HashMap<>();
    private static final Map<String, Object> TEMPORARY = new HashMap<>();

    @Nullable
    private static Path file;
    private static boolean dirty;
    private static int ticksSinceSave;

    private VariableStore() {
    }

    /** Временная ли переменная (только память). */
    public static boolean isTemporary(String name) {
        return name.startsWith("-") && ScriptCraftConfig.temporaryVariablesEnabled();
    }

    @Nullable
    public static Object get(String name) {
        return isTemporary(name) ? TEMPORARY.get(name) : PERSISTENT.get(name);
    }

    public static void set(String name, Object value) {
        boolean temporary = isTemporary(name);
        Map<String, Object> map = temporary ? TEMPORARY : PERSISTENT;
        if (Objects.equals(map.get(name), value)) {
            return;
        }
        map.put(name, value);
        if (!temporary) {
            dirty = true;
        }
        // HUD с {этой переменной} обновляется здесь же. Нет подписчиков — один поиск в таблице и выход.
        HudManager.onVariable(name);
    }

    /**
     * Прямые индексы {prefix::*}. Ключ prefix::minecraft:diamond даёт индекс minecraft:diamond.
     * Глубже (ещё один ::) не входит. prefix без хвостовых ::.
     */
    public static List<Map.Entry<String, Object>> children(String prefix) {
        String head = prefix.endsWith("::") ? prefix : prefix + "::";
        List<Map.Entry<String, Object>> out = new ArrayList<>();
        collectChildren(PERSISTENT, head, out);
        collectChildren(TEMPORARY, head, out);
        out.sort(Comparator.comparing(Map.Entry::getKey));
        return out;
    }

    /** Удаляет прямые индексы {prefix::*}. */
    public static void deleteChildren(String prefix) {
        String head = prefix.endsWith("::") ? prefix : prefix + "::";
        List<String> keys = new ArrayList<>();
        for (String key : PERSISTENT.keySet()) {
            if (isChild(key, head)) {
                keys.add(key);
            }
        }
        for (String key : TEMPORARY.keySet()) {
            if (isChild(key, head)) {
                keys.add(key);
            }
        }
        for (String key : keys) {
            delete(key);
        }
    }

    private static void collectChildren(Map<String, Object> map, String head, List<Map.Entry<String, Object>> out) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (isChild(entry.getKey(), head)) {
                out.add(Map.entry(entry.getKey().substring(head.length()), entry.getValue()));
            }
        }
    }

    private static boolean isChild(String key, String head) {
        if (!key.startsWith(head)) {
            return false;
        }
        String rest = key.substring(head.length());
        return !rest.isEmpty() && !rest.contains("::");
    }

    public static void delete(String name) {
        boolean temporary = isTemporary(name);
        Object removed = temporary ? TEMPORARY.remove(name) : PERSISTENT.remove(name);
        if (removed == null) {
            return;
        }
        if (!temporary) {
            dirty = true;
        }
        HudManager.onVariable(name);
    }

    public static int size() {
        return PERSISTENT.size() + TEMPORARY.size();
    }

    // ------------------------------------------------------------------ жизненный цикл

    /** Старт сервера: очищаем всё, делаем резервную копию и читаем общий файл переменных. */
    public static void load(MinecraftServer server) {
        PERSISTENT.clear();
        TEMPORARY.clear();
        dirty = false;
        ticksSinceSave = 0;
        file = ModPaths.variables().resolve("variables.json");
        migrateFromWorld(server);
        if (!Files.isRegularFile(file)) {
            return;
        }
        backup();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
                    Object value = fromJson(e.getValue());
                    if (value == null) {
                        continue;
                    }
                    // Если временные переменные выключены, '-'-переменные из файла тоже загружаются как обычные.
                    if (isTemporary(e.getKey())) {
                        continue;
                    }
                    PERSISTENT.put(e.getKey(), value);
                }
            }
            ScriptCraft.LOGGER.info("[ScriptCraft] Загружено переменных: {} ({})", PERSISTENT.size(), file);
        } catch (IOException | RuntimeException e) {
            ScriptCraft.LOGGER.error("[ScriptCraft] Не удалось прочитать переменные из {}", file, e);
        }
    }

    /** Старый файл v0.1 из папки мира -> общий файл (только если общего ещё нет). */
    private static void migrateFromWorld(MinecraftServer server) {
        Path old = server.getWorldPath(LevelResource.ROOT).resolve("scriptcraft").resolve("variables.json");
        if (file == null || Files.exists(file) || !Files.isRegularFile(old)) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.copy(old, file);
            ScriptCraft.LOGGER.info("[ScriptCraft] Переменные перенесены из {} в {}", old, file);
        } catch (IOException e) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Не удалось перенести переменные из {}", old, e);
        }
    }

    /** Резервная копия текущего файла + удаление старых копий сверх лимита variableBackups. */
    private static void backup() {
        int keep = ScriptCraftConfig.variableBackups();
        if (keep <= 0 || file == null) {
            return;
        }
        Path dir = file.getParent();
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        try {
            Files.copy(file, dir.resolve("variables-" + stamp + ".json"), StandardCopyOption.REPLACE_EXISTING);
            List<Path> backups;
            try (Stream<Path> list = Files.list(dir)) {
                // Имена с датой сортируются по алфавиту в хронологическом порядке.
                backups = list.filter(p -> {
                    String n = p.getFileName().toString();
                    return n.startsWith("variables-") && n.endsWith(".json");
                }).sorted().toList();
            }
            for (int i = 0; i < backups.size() - keep; i++) {
                Files.deleteIfExists(backups.get(i));
            }
        } catch (IOException e) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Не удалось сделать резервную копию переменных", e);
        }
    }

    /** Вызывается каждый тик сервера: периодическое сохранение. */
    public static void tick() {
        if (++ticksSinceSave >= ScriptCraftConfig.variablesSaveIntervalSeconds() * 20) {
            ticksSinceSave = 0;
            if (dirty) {
                save();
            }
        }
    }

    /** Остановка сервера: сохраняем и очищаем память (важно для одиночной игры — следующий мир начнёт с чистого листа). */
    public static void shutdown() {
        save();
        PERSISTENT.clear();
        TEMPORARY.clear();
        file = null;
        dirty = false;
    }

    /** Сохраняет постоянные переменные. Пишем во временный файл и переименовываем, чтобы не испортить файл при сбое. */
    public static void save() {
        if (file == null) {
            return;
        }
        JsonObject root = new JsonObject();
        new TreeMap<>(PERSISTENT).forEach((name, value) -> root.add(name, toJson(value)));
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("variables.json.tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException e) {
            ScriptCraft.LOGGER.error("[ScriptCraft] Не удалось сохранить переменные в {}", file, e);
        }
    }

    private static JsonElement toJson(Object value) {
        if (value instanceof Number n) {
            return new JsonPrimitive(n.doubleValue());
        }
        if (value instanceof Boolean b) {
            return new JsonPrimitive(b);
        }
        return new JsonPrimitive(String.valueOf(value));
    }

    @Nullable
    private static Object fromJson(JsonElement e) {
        if (!e.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isBoolean()) {
            return p.getAsBoolean();
        }
        if (p.isNumber()) {
            return p.getAsDouble();
        }
        return p.getAsString();
    }
}
