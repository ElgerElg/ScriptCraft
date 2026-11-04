package com.scriptcraft.lang;

import com.scriptcraft.config.ScriptCraftConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Все сообщения для игроков и консоли на двух языках (EN и RU).
 * Язык берётся из config/scriptcraft-common.toml (language = "en" | "ru").
 * Аргументы подставляются через String.format: %s, %d, %1$s...
 * Чтобы добавить язык, заведите ещё одну таблицу и добавьте её в {@link #table()}.
 */
public final class Messages {
    private static final Map<String, String> EN = new HashMap<>();
    private static final Map<String, String> RU = new HashMap<>();

    static {
        // --- справка (%1$s = имя корневой команды: scriptcraft / sk / script)
        add("help.header", "&6--- ScriptCraft help ---", "&6--- ScriptCraft: справка ---");
        add("help.reload", "&e/%1$s reload &7- reload all scripts", "&e/%1$s reload &7- перезагрузить все скрипты");
        add("help.reload_one", "&e/%1$s reload <script> &7- reload one script", "&e/%1$s reload <скрипт> &7- перезагрузить один скрипт");
        add("help.enable", "&e/%1$s enable <script> &7- enable a disabled script", "&e/%1$s enable <скрипт> &7- включить выключенный скрипт");
        add("help.disable", "&e/%1$s disable <script> &7- disable a script (renames it to -name.sk)", "&e/%1$s disable <скрипт> &7- выключить скрипт (переименует в -имя.sk)");
        add("help.list", "&e/%1$s list &7- show loaded scripts, commands and errors", "&e/%1$s list &7- загруженные скрипты, команды и ошибки");
        add("help.help", "&e/%1$s help &7- this help", "&e/%1$s help &7- эта справка");
        add("help.aliases", "&7Aliases: /scriptcraft, /sk, /script", "&7Синонимы: /scriptcraft, /sk, /script");

        // --- перезагрузка
        add("reload.all", "&aReloaded %d script(s): %d command(s), %d error(s)", "&aПерезагружено скриптов: %d, команд: %d, ошибок: %d");
        add("reload.one", "&aReloaded script %s (%d error(s) total)", "&aСкрипт %s перезагружен (всего ошибок: %d)");
        add("reload.time", "Scripts compiled in %d ms", "Скрипты скомпилированы за %d мс");
        add("restart.block", "&eNew block %s will appear only after restarting the server and clients", "&eНовый блок %s появится только после перезапуска сервера и клиентов");
        add("restart.item", "&eNew item %s will appear only after restarting the server and clients", "&eНовый предмет %s появится только после перезапуска сервера и клиентов");

        // --- включение / выключение
        add("script.not_found", "&cScript not found: %s", "&cСкрипт не найден: %s");
        add("script.is_disabled", "&cScript %s is disabled. Use enable first.", "&cСкрипт %s выключен. Сначала включите его (enable).");
        add("script.already_disabled", "&eScript %s is already disabled", "&eСкрипт %s уже выключен");
        add("script.already_enabled", "&eScript %s is already enabled", "&eСкрипт %s уже включён");
        add("script.disabled", "&aScript %s disabled (file renamed to %s). Its commands stay registered until restart and will reply that they are disabled.",
                "&aСкрипт %s выключен (файл переименован в %s). Его команды останутся в списке до перезапуска и будут отвечать, что выключены.");
        add("script.enabled", "&aScript %s enabled and loaded", "&aСкрипт %s включён и загружен");
        add("script.rename_failed", "&cCould not rename file: %s", "&cНе удалось переименовать файл: %s");
        add("script.defines_warning", "&eNote: blocks/items (define) of this script change only after restart", "&eВнимание: блоки/предметы (define) этого скрипта изменятся только после перезапуска");

        // --- список
        add("list.scripts", "&6Scripts: &f%s", "&6Скрипты: &f%s");
        add("list.disabled", "&7Disabled: %s", "&7Выключены: %s");
        add("list.summary", "&7Commands: %s | blocks: %d | items: %d | variables: %d | errors: %d",
                "&7Команды: %s | блоков: %d | предметов: %d | переменных: %d | ошибок: %d");
        add("list.none", "-", "-");

        // --- выполнение
        add("command.disabled", "&cCommand /%s is disabled (its script was disabled or removed). It disappears after restart.",
                "&cКоманда /%s выключена (её скрипт выключен или удалён). Она исчезнет после перезапуска.");
        add("command.error", "&c[ScriptCraft] Error in /%s: %s", "&c[ScriptCraft] Ошибка в /%s: %s");
    }

    private Messages() {
    }

    private static void add(String key, String en, String ru) {
        EN.put(key, en);
        RU.put(key, ru);
    }

    private static Map<String, String> table() {
        return "ru".equals(ScriptCraftConfig.language()) ? RU : EN;
    }

    /** Строка по ключу на текущем языке; цветовые коды &x превращаются в §x. */
    public static String get(String key, Object... args) {
        String pattern = table().getOrDefault(key, EN.getOrDefault(key, key));
        String text = args.length == 0 ? pattern : String.format(Locale.ROOT, pattern, args);
        return text.replaceAll("&([0-9a-fk-or])", "\u00a7$1");
    }

    public static MutableComponent component(String key, Object... args) {
        return Component.literal(get(key, args));
    }

    /** Строки справки для корневой команды {@code root}. */
    public static List<String> helpLines(String root) {
        return List.of(get("help.header"), get("help.reload", root), get("help.reload_one", root),
                get("help.enable", root), get("help.disable", root), get("help.list", root),
                get("help.help", root), get("help.aliases"));
    }
}
