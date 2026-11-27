package com.scriptcraft.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.scriptcraft.ModPaths;
import com.scriptcraft.ScriptCraft;
import com.scriptcraft.studio.StudioRooms;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Настройки мода (Forge config, тип COMMON) -> файл scriptcraft/config.toml (см. ModPaths.CONFIG_FILE).
 * Параметры похожи на config.sk из Skript.
 *
 * ВАЖНО: Forge загружает конфиг ПОСЛЕ конструктора мода. Поэтому все геттеры безопасные:
 * пока файл не загружен (например, во время предварительного разбора скриптов), возвращаются значения по умолчанию.
 */
public final class ScriptCraftConfig {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.ConfigValue<String> LANGUAGE;
    private static final ForgeConfigSpec.BooleanValue ENABLE_TEMPORARY_VARIABLES;
    private static final ForgeConfigSpec.IntValue VARIABLES_SAVE_INTERVAL;
    private static final ForgeConfigSpec.IntValue MAX_SCRIPT_STEPS;
    private static final ForgeConfigSpec.IntValue MAX_SCRIPT_MILLIS;
    private static final ForgeConfigSpec.BooleanValue LOG_SCRIPT_LOAD_TIMES;
    private static final ForgeConfigSpec.BooleanValue ALLOW_COMMAND_EFFECTS;
    private static final ForgeConfigSpec.IntValue TARGETED_PLAYER_RANGE;
    private static final ForgeConfigSpec.BooleanValue REGISTER_REAL_IDS;
    private static final ForgeConfigSpec.IntValue VARIABLE_BACKUPS;
    private static final ForgeConfigSpec.BooleanValue SYNC_RESOURCES;
    private static final ForgeConfigSpec.IntValue RESOURCE_MAX_MB;
    private static final ForgeConfigSpec.IntValue RESOURCE_CHUNK_KB;
    private static final ForgeConfigSpec.BooleanValue ENABLE_EFFECT_COMMANDS;
    private static final ForgeConfigSpec.ConfigValue<String> EFFECT_COMMAND_TOKEN;
    private static final ForgeConfigSpec.BooleanValue EFFECT_COMMANDS_REQUIRE_OP;
    private static final ForgeConfigSpec.BooleanValue STUDIO_LIVE_EDIT;
    private static final ForgeConfigSpec.BooleanValue STUDIO_REQUIRE_OP;
    private static final ForgeConfigSpec.BooleanValue SCRIPT_HISTORY_ENABLED;
    private static final ForgeConfigSpec.IntValue SCRIPT_HISTORY_RETENTION_DAYS;
    private static final ForgeConfigSpec.BooleanValue PROFILER_ENABLED;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("ScriptCraft settings / Настройки ScriptCraft").push("general");

        LANGUAGE = b.comment("Language of command messages: en or ru / Язык сообщений команд: en или ru")
                .defineInList("language", "en", List.of("en", "ru"));

        ENABLE_TEMPORARY_VARIABLES = b.comment(
                        "Variables whose name starts with '-' ({-var}) are kept in memory only and wiped on server start/stop.",
                        "If false, they are saved like normal variables.",
                        "Переменные с именем на '-' ({-var}) живут только в памяти и очищаются при старте/остановке сервера.")
                .define("enableTemporaryVariables", true);

        VARIABLES_SAVE_INTERVAL = b.comment("How often variables are saved to disk, in seconds / Как часто сохранять переменные (секунды)")
                .defineInRange("variablesSaveIntervalSeconds", 300, 10, 86400);

        VARIABLE_BACKUPS = b.comment("How many timestamped backups of variables.json to keep (made at server start), 0 = none",
                        "Сколько резервных копий variables.json хранить (делаются при старте сервера), 0 = не делать")
                .defineInRange("variableBackups", 5, 0, 100);

        MAX_SCRIPT_STEPS = b.comment("Max actions per single script run / Максимум действий за один запуск скрипта")
                .defineInRange("maxScriptSteps", 10_000, 100, 10_000_000);

        MAX_SCRIPT_MILLIS = b.comment("Max time per single script run in ms / Максимум времени на один запуск скрипта (мс)")
                .defineInRange("maxScriptMillis", 50, 1, 10_000);

        LOG_SCRIPT_LOAD_TIMES = b.comment("Log how long script loading takes / Писать в лог время загрузки скриптов")
                .define("logScriptLoadTimes", false);

        ALLOW_COMMAND_EFFECTS = b.comment(
                        "Allow effects 'execute console command \"...\"' and 'make player execute command \"...\"'.",
                        "Разрешить эффекты выполнения команд от консоли и от имени игрока.")
                .define("allowCommandEffects", true);

        TARGETED_PLAYER_RANGE = b.comment(
                        "How far 'targeted player' / %targeted% looks (blocks; the ray stops at blocks).",
                        "Как далеко ищется 'targeted player' / %targeted% (блоков; сквозь блоки луч не проходит).")
                .defineInRange("targetedPlayerRange", 50, 1, 256);

        REGISTER_REAL_IDS = b.comment(
                        "false (default): all 'define block / define item' use ScriptCraft's pre-registered pool slots (dyn_block_N / dyn_item_N).",
                        "Players join on the VERY FIRST attempt with zero disconnect/restart, while custom names, models, textures, tools, food, loot, recipes and commands all work.",
                        "true: register real Forge registry IDs at startup (new players without cached IDs will be disconnected once by Forge handshake and asked to restart).",
                        "false (по умолчанию): все блоки и предметы из скриптов работают через готовый пул слотов — вход с первого раза БЕЗ вылета и перезапуска клиента.",
                        "true: регистрировать отдельные Forge ID при запуске (при первом входе на новый сервер Forge потребует один перезапуск игры).")
                .define("registerRealIds", false);

        b.pop();

        // --- Ресурсы из scriptcraft/resources/assets: сервер сам отправляет их клиентам.
        b.comment("Resources from scriptcraft/resources", "Ресурсы из scriptcraft/resources").push("resources");

        SYNC_RESOURCES = b.comment(
                        "Send scriptcraft/resources/assets to clients through the mod network channel (only missing/changed files).",
                        "No vanilla server resource pack, no prompt: the client keeps them in scriptcraft/cache/assets.",
                        "Отправлять scriptcraft/resources/assets клиентам через сетевой канал мода (только новые/изменённые файлы).",
                        "Без ванильного серверного ресурспака и без запроса: клиент хранит их в scriptcraft/cache/assets.")
                .define("syncResourcesToClients", true);

        RESOURCE_MAX_MB = b.comment("Max total size of synced assets in MB (server and client both check it)",
                        "Максимальный общий размер синхронизируемых ресурсов в МБ (проверяют и сервер, и клиент)")
                .defineInRange("maxResourceSizeMb", 32, 1, 512);

        RESOURCE_CHUNK_KB = b.comment("Size of one network chunk in KB (packet limit is 1 MB)",
                        "Размер одного сетевого куска в КБ (лимит пакета 1 МБ)")
                .defineInRange("resourceChunkSizeKb", 256, 16, 900);

        b.pop();

        // --- Эффект-команды (как в Skript): сообщение в чат "?heal player" выполняется как строка скрипта.
        b.comment("Effect commands: chat messages starting with the token are executed as one script line",
                "Эффект-команды: сообщение в чате, начинающееся с токена, выполняется как одна строка скрипта").push("effectCommands");

        ENABLE_EFFECT_COMMANDS = b.comment("Enable effect commands / Включить эффект-команды")
                .define("enableEffectCommands", true);

        EFFECT_COMMAND_TOKEN = b.comment("Prefix of effect commands in chat / Префикс эффект-команд в чате")
                .define("effectCommandToken", "?", v -> v instanceof String str && !str.isBlank() && str.length() <= 8 && !str.startsWith("/"));

        EFFECT_COMMANDS_REQUIRE_OP = b.comment(
                        "true: op level 2 (or permission node scriptcraft.effectcommands from a permission mod) is required.",
                        "false: everyone may use them (DANGEROUS on public servers).",
                        "true: нужен уровень оператора 2 (или право scriptcraft.effectcommands из мода прав). false: доступно всем (ОПАСНО).")
                .define("effectCommandsRequireOp", true);

        b.pop();

        // --- Студия: совместная правка и кто может её открыть.
        b.comment("Studio: shared editing and who may open it",
                "Студия: совместная правка и кто может её открыть").push("studio");

        STUDIO_LIVE_EDIT = b.comment(
                        "When true, people in the same script see each other's caret, selection and typing.",
                        "Off by default. A save is accepted only if it is based on the latest copy: the last save or the last accepted edit wins.",
                        "Когда true, в одном скрипте видны чужой курсор, выделение и набор.",
                        "По умолчанию выключено. Сохранение принимается только от последней копии: побеждает последнее сохранение или последняя принятая правка.")
                .define("liveEdit", false);

        STUDIO_REQUIRE_OP = b.comment(
                        "Who may open and edit the studio. The permission node is scriptcraft.studio.",
                        "true (default): operators, and anyone granted scriptcraft.studio (Forge Permission API or LuckPerms, mod or plugin).",
                        "false: every player.",
                        "Кто может открывать и править студию. Узел права: scriptcraft.studio.",
                        "true (по умолчанию): операторы и те, кому выдано scriptcraft.studio (Forge или LuckPerms, мод или плагин).",
                        "false: любой игрок.")
                .define("requireOp", true);

        SCRIPT_HISTORY_ENABLED = b.comment(
                        "Automatically save a snapshot of each script before saving changes in Studio.",
                        "Автоматически сохранять снимок скрипта перед каждым сохранением в студии.")
                .define("scriptHistoryEnabled", true);

        SCRIPT_HISTORY_RETENTION_DAYS = b.comment(
                        "Auto-delete script version snapshots older than N days (default: 3, 0 = keep forever).",
                        "Автоудаление старых версий скриптов старше N дней (по умолчанию: 3, 0 = не удалять).")
                .defineInRange("scriptHistoryRetentionDays", 3, 0, 365);

        PROFILER_ENABLED = b.comment(
                        "Enable server script profiler by default (can also be toggled live in Studio).",
                        "Включить профайлер скриптов на сервере по умолчанию (можно переключать на лету в студии).")
                .define("profilerEnabled", true);

        b.pop();
        SPEC = b.build();
    }

    private ScriptCraftConfig() {
    }

    /** Безопасное чтение: до загрузки конфига или при ошибке — значение по умолчанию. */
    private static <T> T safe(Supplier<T> getter, T fallback) {
        try {
            return SPEC.isLoaded() ? getter.get() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static String language() {
        return safe(LANGUAGE::get, "en");
    }

    public static boolean temporaryVariablesEnabled() {
        return safe(ENABLE_TEMPORARY_VARIABLES::get, true);
    }

    public static int variablesSaveIntervalSeconds() {
        return safe(VARIABLES_SAVE_INTERVAL::get, 300);
    }

    public static int maxScriptSteps() {
        return safe(MAX_SCRIPT_STEPS::get, 10_000);
    }

    public static int maxScriptMillis() {
        return safe(MAX_SCRIPT_MILLIS::get, 50);
    }

    public static boolean logScriptLoadTimes() {
        return safe(LOG_SCRIPT_LOAD_TIMES::get, false);
    }

    public static int targetedPlayerRange() {
        return safe(TARGETED_PLAYER_RANGE::get, 50);
    }

    public static boolean registerRealIds() {
        return safe(REGISTER_REAL_IDS::get, false);
    }

    /** Чтение registerRealIds в конструкторе мода (до того как Forge загрузил SPEC). */
    public static boolean registerRealIdsAtBoot() {
        if (SPEC.isLoaded()) {
            return registerRealIds();
        }
        Path path = ModPaths.root().resolve("config.toml");
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try {
            for (String line : Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8)) {
                String trimmed = line.strip();
                if (trimmed.startsWith("#")) continue;
                if (trimmed.startsWith("registerRealIds")) {
                    int eq = trimmed.indexOf('=');
                    if (eq >= 0) {
                        String val = trimmed.substring(eq + 1).strip();
                        int hash = val.indexOf('#');
                        if (hash >= 0) val = val.substring(0, hash).strip();
                        return "true".equalsIgnoreCase(val);
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    public static boolean allowCommandEffects() {
        return safe(ALLOW_COMMAND_EFFECTS::get, true);
    }

    public static boolean enableEffectCommands() {
        return safe(ENABLE_EFFECT_COMMANDS::get, true);
    }

    public static String effectCommandToken() {
        return safe(EFFECT_COMMAND_TOKEN::get, "?");
    }

    public static boolean effectCommandsRequireOp() {
        return safe(EFFECT_COMMANDS_REQUIRE_OP::get, true);
    }

    /** Совместная правка студии. Пока ключа нет в старом файле, остаётся выключенной. */
    public static boolean studioLiveEdit() {
        return safe(STUDIO_LIVE_EDIT::get, false);
    }

    /** true: студия у операторов и у тех, кому выдано scriptcraft.studio. */
    public static boolean studioRequireOp() {
        return safe(STUDIO_REQUIRE_OP::get, true);
    }

    public static boolean scriptHistoryEnabled() {
        return safe(SCRIPT_HISTORY_ENABLED::get, true);
    }

    public static int scriptHistoryRetentionDays() {
        return safe(SCRIPT_HISTORY_RETENTION_DAYS::get, 3);
    }

    public static boolean profilerEnabled() {
        return safe(PROFILER_ENABLED::get, true);
    }

    public static int variableBackups() {
        return safe(VARIABLE_BACKUPS::get, 5);
    }

    public static boolean syncResourcesToClients() {
        return safe(SYNC_RESOURCES::get, true);
    }

    public static int maxResourceSizeMb() {
        return safe(RESOURCE_MAX_MB::get, 32);
    }

    public static int resourceChunkSizeKb() {
        return safe(RESOURCE_CHUNK_KB::get, 256);
    }

    private static long lastConfigModified = -1L;
    private static int watchTicks;

    /**
     * Перечитать scriptcraft/config.toml с диска в уже загруженный конфиг.
     * Вызывает SPEC.afterReload(), чтобы сбросить кэш ForgeConfigSpec.ConfigValue без перезапуска сервера.
     *
     * @return null, если получилось, иначе короткий текст ошибки
     */
    public static String reloadFromDisk() {
        Set<ModConfig> configs = ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.COMMON);
        if (configs == null) {
            return "common configs are not tracked";
        }
        for (ModConfig config : configs) {
            if (!ScriptCraft.MODID.equals(config.getModId())) {
                continue;
            }
            if (!(config.getConfigData() instanceof CommentedFileConfig file)) {
                return "config is not loaded yet";
            }
            try {
                file.load();
                SPEC.afterReload();
                lastConfigModified = configModified();
                return null;
            } catch (RuntimeException e) {
                String message = e.getMessage();
                return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
            }
        }
        return "scriptcraft config is not registered";
    }

    /** Проверяет раз в секунду, не изменился ли scriptcraft/config.toml на диске, и применяет без перезапуска. */
    public static void tick(MinecraftServer server) {
        if (server == null || ++watchTicks < 20) {
            return;
        }
        watchTicks = 0;
        long modified = configModified();
        if (modified <= 0L) {
            return;
        }
        if (lastConfigModified < 0L) {
            lastConfigModified = modified;
            return;
        }
        if (modified != lastConfigModified) {
            lastConfigModified = modified;
            String err = reloadFromDisk();
            if (err == null) {
                StudioRooms.onConfigChange(server);
            } else {
                ScriptCraft.LOGGER.warn("[ScriptCraft] Не удалось автоматически перечитать config.toml: {}", err);
            }
        }
    }

    private static long configModified() {
        Path path = ModPaths.root().resolve("config.toml");
        try {
            return Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }
}
