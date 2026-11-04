package com.scriptcraft.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;
import java.util.function.Supplier;

/**
 * Настройки мода (Forge config, тип COMMON) -> файл config/scriptcraft-common.toml.
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

        MAX_SCRIPT_STEPS = b.comment("Max actions per single script run / Максимум действий за один запуск скрипта")
                .defineInRange("maxScriptSteps", 10_000, 100, 10_000_000);

        MAX_SCRIPT_MILLIS = b.comment("Max time per single script run in ms / Максимум времени на один запуск скрипта (мс)")
                .defineInRange("maxScriptMillis", 50, 1, 10_000);

        LOG_SCRIPT_LOAD_TIMES = b.comment("Log how long script loading takes / Писать в лог время загрузки скриптов")
                .define("logScriptLoadTimes", false);

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
}
