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
 * Язык берётся из scriptcraft/config.toml (language = "en" | "ru").
 * Аргументы подставляются через String.format: %s, %d, %1$s...
 * Чтобы добавить язык, заведите ещё одну таблицу и добавьте её в {@link #table()}.
 */
public final class Messages {
    private static final Map<String, String> EN = new HashMap<>();
    private static final Map<String, String> RU = new HashMap<>();

    static {
        // --- справка (%1$s = имя корневой команды: scriptcraft / sc / script)
        add("help.header", "&6--- ScriptCraft help ---", "&6--- ScriptCraft: справка ---");
        add("help.reload", "&e/%1$s reload &7- reload all scripts", "&e/%1$s reload &7- перезагрузить все скрипты");
        add("help.reload_one", "&e/%1$s reload <script> &7- reload one script", "&e/%1$s reload <скрипт> &7- перезагрузить один скрипт");
        add("help.enable", "&e/%1$s enable <script> &7- enable a disabled script", "&e/%1$s enable <скрипт> &7- включить выключенный скрипт");
        add("help.disable", "&e/%1$s disable <script> &7- disable a script (renames it to -name.sc)", "&e/%1$s disable <скрипт> &7- выключить скрипт (переименует в -имя.sc)");
        add("help.list", "&e/%1$s list &7- show loaded scripts, commands and errors", "&e/%1$s list &7- загруженные скрипты, команды и ошибки");
        add("help.help", "&e/%1$s help &7- this help", "&e/%1$s help &7- эта справка");
        add("help.resources", "&e/%1$s resources reload &7- rescan scriptcraft/resources and send changes to online players",
                "&e/%1$s resources reload &7- перечитать scriptcraft/resources и отправить изменения игрокам онлайн");
        add("help.ids", "&e/%1$s ids &7- show which registry id each script block/item got", "&e/%1$s ids &7- какой id в реестре получил каждый блок/предмет скриптов");
        add("help.give", "&e/%1$s give <players> <id> [count] &7- give a script item (example:ruby_block)", "&e/%1$s give <игроки> <id> [кол-во] &7- выдать предмет скрипта (example:ruby_block)");
        add("help.aliases", "&7Aliases: /scriptcraft, /sc, /script", "&7Синонимы: /scriptcraft, /sc, /script");

        // --- перезагрузка
        add("reload.all", "&aReloaded %d script(s): %d command(s), %d error(s)", "&aПерезагружено скриптов: %d, команд: %d, ошибок: %d");
        add("reload.one", "&aReloaded script %s (%d error(s) total)", "&aСкрипт %s перезагружен (всего ошибок: %d)");
        add("reload.time", "Scripts compiled in %d ms", "Скрипты скомпилированы за %d мс");
        // --- привязка define к слотам пула (scriptcraft:dyn_block_N / dyn_item_N)
        add("bindings.added", "&aBlock/item %s is live as %s (temporary slot; after a server and client restart it gets its real id)",
                "&aБлок/предмет %s доступен как %s (временный слот; после перезапуска сервера и клиента — настоящий id)");
        add("bindings.pool_full", "&cNo free slot for %s: the pool has %d block and %d item slots (defines removed from this world keep their slots)",
                "&cНет свободного слота для %s: в пуле %d слотов блоков и %d слотов предметов (удалённые define сохраняют слоты в этом мире)");
        add("bindings.data_reload", "&7Reloading data (tags, loot tables, recipes) for the new ids...",
                "&7Перезагружаю данные (теги, лут, рецепты) для новых id...");
        add("pool.free_block", "Free ScriptCraft block slot %d", "Свободный слот блока ScriptCraft %d");
        add("pool.script_id", "Script id: %s", "id в скрипте: %s");
        add("pool.free_item", "Free ScriptCraft item slot %d", "Свободный слот предмета ScriptCraft %d");
        add("ids.header", "&6Script id -> registry id (F3, vanilla commands, other datapacks):",
                "&6id скрипта -> id в реестре (F3, ванильные команды, чужие датапаки):");
        add("ids.entry", "&e%s &7-> &f%s", "&e%s &7-> &f%s");
        add("ids.real", "&e%s &7- &areal id", "&e%s &7- &aнастоящий id");
        add("ids.temporary", "&e%s &7-> &f%s &7(temporary slot until restart)", "&e%s &7-> &f%s &7(временный слот до перезапуска)");
        add("bindings.restart_client", "&eNew blocks/items are in temporary slots for now: %s. Restart the client to get the real ids (the server needs a restart too).",
                "&eНовые блоки/предметы пока во временных слотах: %s. Перезапустите клиент, чтобы получить настоящие id (нужен и перезапуск сервера).");
        add("ids.none", "&7No blocks or items are defined in scripts", "&7В скриптах нет блоков и предметов (define)");
        add("give.unknown", "&cUnknown item: %s", "&cНеизвестный предмет: %s");
        add("give.done", "&aGave %d x %s to %d player(s)", "&aВыдано %d x %s игрокам: %d");

        // --- включение / выключение
        add("script.not_found", "&cScript not found: %s", "&cСкрипт не найден: %s");
        add("script.is_disabled", "&cScript %s is disabled. Use enable first.", "&cСкрипт %s выключен. Сначала включите его (enable).");
        add("script.already_disabled", "&eScript %s is already disabled", "&eСкрипт %s уже выключен");
        add("script.already_enabled", "&eScript %s is already enabled", "&eСкрипт %s уже включён");
        add("script.disabled", "&aScript %s disabled (file renamed to %s). Its commands stay registered until restart and will reply that they are disabled.",
                "&aСкрипт %s выключен (файл переименован в %s). Его команды останутся в списке до перезапуска и будут отвечать, что выключены.");
        add("script.enabled", "&aScript %s enabled and loaded", "&aСкрипт %s включён и загружен");
        add("script.rename_failed", "&cCould not rename file: %s", "&cНе удалось переименовать файл: %s");
        add("script.defines_warning", "&eNote: placed blocks/items of this script stay in the world as free slots and come back when the script is enabled again",
                "&eВнимание: поставленные блоки/предметы этого скрипта остаются в мире как свободные слоты и вернутся, когда скрипт снова включат");

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

        // --- эффект-команды (?heal player)
        add("effectcmd.no_permission", "&cYou are not allowed to use effect commands (scriptcraft.effectcommands)",
                "&cУ вас нет права на эффект-команды (scriptcraft.effectcommands)");
        add("effectcmd.parse_error", "&cEffect command error: %s", "&cОшибка в эффект-команде: %s");
        add("effectcmd.run_error", "&cEffect command failed: %s", "&cЭффект-команда не выполнилась: %s");
        add("effectcmd.no_targets", "&eNo targets: no player matched", "&eНет целей: ни один игрок не подошёл");

        // --- эффекты
        add("target.bad_selector", "bad selector %s: %s", "ошибка в селекторе %s: %s");
        add("effect.not_number", "'%s' is not a number", "'%s' — не число");
        add("effect.bad_id", "invalid id '%s'", "некорректный id '%s'");
        add("effect.unknown_effect", "unknown potion effect %s", "неизвестный эффект %s");
        add("effect.unknown_sound", "unknown sound %s", "неизвестный звук %s");
        add("effect.commands_disabled", "command effects are disabled (allowCommandEffects = false)",
                "эффекты-команды выключены (allowCommandEffects = false)");
        add("effect.commands_need_op", "running commands from chat effect commands requires operator level 2",
                "запуск команд из эффект-команд в чате доступен только операторам (уровень 2)");

        // --- HUD
        add("hud.too_many", "too many HUD elements for one player (max %d)", "слишком много элементов HUD у одного игрока (максимум %d)");

        // --- ресурсы
        add("resources.reloaded", "&aResources rescanned: %d file(s), %d KB. Manifest sent to %d online player(s); they download only changed files.",
                "&aРесурсы перечитаны: файлов %d, %d КБ. Манифест отправлен игрокам онлайн: %d; они скачают только изменённое.");
        add("resources.reloaded_nosync", "&eResources rescanned: %d file(s), %d KB. Sync to clients is off (syncResourcesToClients = false).",
                "&eРесурсы перечитаны: файлов %d, %d КБ. Отправка клиентам выключена (syncResourcesToClients = false).");
        add("resources.reload_failed", "&cResource sync disabled: %s", "&cСинхронизация ресурсов отключена: %s");
        add("resources.data_hint", "&7Loot tables/tags/recipes from scriptcraft/resources/data are reloaded by vanilla /reload",
                "&7Лут/теги/рецепты из scriptcraft/resources/data перечитывает ванильная /reload");
        add("resources.reloading", "&e[ScriptCraft] Applying server textures/models (one resource reload)...",
                "&e[ScriptCraft] Применяю текстуры/модели сервера (одна перезагрузка ресурсов)...");
        add("resources.too_big", "&c[ScriptCraft] Server resources (%d MB) exceed your maxResourceSizeMb (%d MB) - not downloaded",
                "&c[ScriptCraft] Ресурсы сервера (%d МБ) больше вашего maxResourceSizeMb (%d МБ) — не скачаны");
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
                get("help.resources", root), get("help.ids", root), get("help.give", root), get("help.help", root),
                get("help.aliases"));
    }
}
