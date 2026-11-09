package com.scriptcraft.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.scriptcraft.ScriptCraft;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

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
    private static final ForgeConfigSpec.IntValue VARIABLE_BACKUPS;
    private static final ForgeConfigSpec.BooleanValue SYNC_RESOURCES;
    private static final ForgeConfigSpec.IntValue RESOURCE_MAX_MB;
    private static final ForgeConfigSpec.IntValue RESOURCE_CHUNK_KB;
    private static final ForgeConfigSpec.BooleanValue ENABLE_EFFECT_COMMANDS;
    private static final ForgeConfigSpec.ConfigValue<String> EFFECT_COMMAND_TOKEN;
    private static final ForgeConfigSpec.BooleanValue EFFECT_COMMANDS_REQUIRE_OP;

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
                .define("enableEffectCommands", false);

        EFFECT_COMMAND_TOKEN = b.comment("Prefix of effect commands in chat / Префикс эффект-команд в чате")
                .define("effectCommandToken", "?", v -> v instanceof String str && !str.isBlank() && str.length() <= 8 && !str.startsWith("/"));

        EFFECT_COMMANDS_REQUIRE_OP = b.comment(
                        "true: op level 2 (or permission node scriptcraft.effectcommands from a permission mod) is required.",
                        "false: everyone may use them (DANGEROUS on public servers).",
                        "true: нужен уровень оператора 2 (или право scriptcraft.effectcommands из мода прав). false: доступно всем (ОПАСНО).")
                .define("effectCommandsRequireOp", true);

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

    public static boolean allowCommandEffects() {
        return safe(ALLOW_COMMAND_EFFECTS::get, true);
    }

    public static boolean enableEffectCommands() {
        return safe(ENABLE_EFFECT_COMMANDS::get, false);
    }

    public static String effectCommandToken() {
        return safe(EFFECT_COMMAND_TOKEN::get, "?");
    }

    public static boolean effectCommandsRequireOp() {
        return safe(EFFECT_COMMANDS_REQUIRE_OP::get, true);
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

    /**
     * Перечитать scriptcraft/config.toml с диска в уже загруженный конфиг.
     * Геттеры читают файл при каждом вызове, поэтому язык, лимиты и токен эффект-команд
     * применяются сразу. Копии variables.json по-прежнему делаются только при старте сервера.
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
                return null;
            } catch (RuntimeException e) {
                String message = e.getMessage();
                return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
            }
        }
        return "scriptcraft config is not registered";
    }
}
