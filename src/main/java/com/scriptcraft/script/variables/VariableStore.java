package com.scriptcraft.script.variables;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.scriptcraft.ScriptCraft;
import com.scriptcraft.config.ScriptCraftConfig;
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
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Хранилище переменных скриптов ({name}).
 *
 * Где лежат: <папка мира>/scriptcraft/variables.json (на сервере: world/scriptcraft/variables.json).
 * Выбран JSON в папке мира, а не SavedData: файл легко читать и править руками, а в одиночной игре
 * у каждого мира свои переменные.
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
        if (isTemporary(name)) {
            TEMPORARY.put(name, value);
        } else {
            PERSISTENT.put(name, value);
            dirty = true;
        }
    }

    public static void delete(String name) {
        if (isTemporary(name)) {
            TEMPORARY.remove(name);
        } else if (PERSISTENT.remove(name) != null) {
            dirty = true;
        }
    }

    public static int size() {
        return PERSISTENT.size() + TEMPORARY.size();
    }

    // ------------------------------------------------------------------ жизненный цикл

    /** Старт сервера: очищаем всё и читаем файл мира. */
    public static void load(MinecraftServer server) {
        PERSISTENT.clear();
        TEMPORARY.clear();
        dirty = false;
        ticksSinceSave = 0;
        file = server.getWorldPath(LevelResource.ROOT).resolve("scriptcraft").resolve("variables.json");
        if (!Files.isRegularFile(file)) {
            return;
        }
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
