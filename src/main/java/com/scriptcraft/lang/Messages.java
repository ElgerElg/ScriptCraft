package com.scriptcraft.lang;

import com.scriptcraft.config.ScriptCraftConfig;
import net.minecraft.ChatFormatting;
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
        add("help.config", "&e/%1$s config reload &7- reread scriptcraft/config.toml",
                "&e/%1$s config reload &7- перечитать scriptcraft/config.toml");
        add("help.ids", "&e/%1$s ids &7- show which registry id each script block/item got", "&e/%1$s ids &7- какой id в реестре получил каждый блок/предмет скриптов");
        add("help.give", "&e/%1$s give <players> <id> [count] &7- give a script item (example:ruby_block)", "&e/%1$s give <игроки> <id> [кол-во] &7- выдать предмет скрипта (example:ruby_block)");
        add("help.aliases", "&7Aliases: /scriptcraft, /sc, /script", "&7Синонимы: /scriptcraft, /sc, /script");

        // --- перезагрузка
        add("reload.all", "&aReloaded %d script(s): %d command(s), %d error(s)", "&aПерезагружено скриптов: %d, команд: %d, ошибок: %d");
        add("reload.one", "&aReloaded script %s (%d error(s) total)", "&aСкрипт %s перезагружен (всего ошибок: %d)");
        add("reload.time", "Scripts compiled in %d ms", "Скрипты скомпилированы за %d мс");
        add("config.reloaded", "&aConfig reloaded (language: %s)", "&aКонфиг перечитан (язык: %s)");
        add("config.reload_failed", "&cCould not reload config: %s", "&cНе удалось перечитать конфиг: %s");
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
        add("command.error", "&cError in /%s: %s", "&cОшибка в /%s: %s");

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
        add("resources.reloading", "&eApplying server textures/models (one resource reload)...",
                "&eПрименяю текстуры/модели сервера (одна перезагрузка ресурсов)...");
        add("resources.too_big", "&cServer resources (%d MB) exceed your maxResourceSizeMb (%d MB) - not downloaded",
                "&cРесурсы сервера (%d МБ) больше вашего maxResourceSizeMb (%d МБ) — не скачаны");

        add("log.title", "Journal", "Журнал");
        add("log.empty", "No script errors", "Ошибок в скриптах нет");
        add("log.reason", "Reason", "Причина");
        add("log.runtime", "while running", "во время игры");
        add("log.no_file", "The file text is not available", "Текста файла нет");
        add("log.line_missing", "(line is outside the shown fragment)", "(строка за пределами фрагмента)");
        add("log.settings", "Notifications", "Уведомления");
        add("log.key", "Open key", "Клавиша");
        add("log.key_wait", "Press a key. Esc cancels", "Нажмите клавишу. Esc — отмена");
        add("log.time", "Hide after", "Скрывать через");
        add("log.size", "Size", "Размер");
        add("log.place", "Position", "Позиция");
        add("log.place.chat", "By the chat", "У чата");
        add("log.place.tl", "Top left", "Сверху слева");
        add("log.place.tr", "Top right", "Сверху справа");
        add("log.place.br", "Bottom right", "Снизу справа");
        add("log.sample", "&7reloaded welcome.sc", "&7скрипт welcome.sc перезагружен");
        add("log.errors_toast", "&e%d script error(s). Open the journal.", "&eОшибок в скриптах: %d. Откройте журнал.");
        add("log.runtime_toast", "&cError in %s: %s", "&cОшибка в %s: %s");

        add("studio.home", "Home", "Главная");
        add("studio.journal", "Journal", "Журнал");
        add("studio.patches", "Patches and versions", "Патчи и версии");
        add("studio.p1", "0.3.7 — item receive, keys, menu cancel, list", "0.3.7 — получение предмета, клавиши, отмена меню, список");
        add("studio.links", "Links", "Ссылки");
        add("studio.link.github", "GitHub", "GitHub");
        add("studio.link.docs", "Documentation", "Документация");
        add("studio.link.examples", "Script examples", "Примеры скриптов");
        add("studio.link.folder", "Open the scripts folder", "Открыть папку скриптов");
        add("studio.about", "Mod", "О моде");
        add("studio.tag.1", "ready to craft?", "готовы крафтить?");
        add("studio.tag.2", "scripts instead of plugins", "скрипты вместо плагинов");
        add("studio.tag.3", "your server, your rules", "свой сервер, свои правила");
        add("studio.tag.4", "write a .sc file and it works", "напиши .sc — и оно работает");
        add("studio.tag.5", "Forge, but in plain words", "Forge, но человеческим языком");
        add("studio.active", "Active errors", "Активные ошибки");
        add("studio.history", "History", "История");
        add("studio.ask.close", "Close %s without saving?", "Закрыть %s без сохранения?");
        add("studio.ask.delete", "Delete %s from the server?", "Удалить %s с сервера?");
        add("studio.ask.yes", "Yes", "Да");
        add("studio.ask.no", "No", "Нет");
        add("studio.deleted", "Deleted %s", "Удалено %s");
        add("studio.delete_failed", "&cCould not delete: %s", "&cНе удалось удалить: %s");
        add("studio.p2", "0.3.6 — HUD updates when a variable changes", "0.3.6 — HUD обновляется вместе с переменной");
        add("studio.p3", "0.3.5 — example scripts to learn from, Tab indent", "0.3.5 — примеры, на которых можно учиться, отступ Tab");
        add("studio.p4", "0.3.4 — command arguments, aliases, script", "0.3.4 — аргументы команд, псевдонимы, script");
        add("studio.p5", "0.3.3 — the player's own skin in the studio", "0.3.3 — в студии скин самого игрока");
        add("studio.dev", "Developer", "Разработчик");
        add("studio.role", "author of the mod", "автор мода");
        add("studio.hint", "Open a script with + or File. On a server these are the server's scripts; in singleplayer, the local ones.", "Откройте скрипт кнопкой + или меню «Файл». На сервере это его скрипты, в одиночной игре — локальные.");
        add("studio.parser", "Parser: events — yellow marker, textures and ids — blue. Hover a blue marker for the image and its size.", "Парсер: события — жёлтый маркер, текстуры и id — синий. Наведите на синий, чтобы увидеть картинку и размер.");
        add("studio.loading", "Loading...", "Загрузка...");
        add("studio.empty", "No scripts on this server yet", "На этом сервере пока нет скриптов");
        add("studio.find", "Find", "Найти");
        add("studio.none", "not found", "не найдено");
        add("studio.unsaved", "&eUnsaved edits stayed in the editor", "&eНесохранённые правки остались в редакторе");
        add("studio.read_failed", "&cCould not read %s", "&cНе удалось прочитать %s");
        add("studio.bad_name", "&cBad script name: %s", "&cНеподходящее имя скрипта: %s");
        add("studio.save_failed", "&cSave failed: %s", "&cНе удалось сохранить: %s");
        add("studio.saved_disabled", "&eSaved %s (the script is disabled, so it was not reloaded)", "&eСохранено %s (скрипт выключен, поэтому не перезагружался)");
        add("studio.reload_disabled", "&eScript %s is disabled", "&eСкрипт %s выключен");
        add("studio.new_name", "name.sc", "имя.sc");
        add("studio.m.file", "File", "Файл");
        add("studio.m.edit", "Edit", "Правка");
        add("studio.m.search", "Search", "Поиск");
        add("studio.m.view", "View", "Вид");
        add("studio.m.enc", "Encoding", "Кодировки");
        add("studio.m.new", "New script", "Новый скрипт");
        add("studio.m.open", "Open", "Открыть");
        add("studio.m.save", "Save", "Сохранить");
        add("studio.m.save_all", "Save all", "Сохранить всё");
        add("studio.m.close", "Close tab", "Закрыть вкладку");
        add("studio.m.delete", "Delete script", "Удалить скрипт");
        add("studio.m.exit", "Close window", "Закрыть окно");
        add("studio.m.undo", "Undo", "Отменить");
        add("studio.m.redo", "Redo", "Вернуть");
        add("studio.m.cut", "Cut", "Вырезать");
        add("studio.m.copy", "Copy", "Копировать");
        add("studio.m.paste", "Paste", "Вставить");
        add("studio.m.all", "Select all", "Выделить всё");
        add("studio.m.find", "Find", "Найти");
        add("studio.m.full", "Fullscreen", "На весь экран");
        add("studio.m.window", "Window", "Окно");
        add("studio.m.utf", "UTF-8", "UTF-8");
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

    /** Строка по ключу на текущем языке; цветовые коды &x превращаются в §x. Без префикса — для лога и предметов. */
    public static String get(String key, Object... args) {
        String pattern = table().getOrDefault(key, EN.getOrDefault(key, key));
        String text = args.length == 0 ? pattern : String.format(Locale.ROOT, pattern, args);
        return colorize(text);
    }

    /** &7[&6ScriptCraft&7] — общий заголовок сообщений мода в чат. */
    public static MutableComponent brand() {
        return Component.literal("[").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("ScriptCraft").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("] ").withStyle(ChatFormatting.GRAY));
    }

    /** Цветной текст с заголовком &7[&6ScriptCraft&7], без компонента чата. */
    public static String branded(String colored) {
        return colorize("&7[&6ScriptCraft&7]&r ") + colored;
    }

    /** Текст всплывающего уведомления. */
    public static String toast(String key, Object... args) {
        return branded(get(key, args));
    }

    /** Сообщение в чат: префикс и текст ключа. */
    public static MutableComponent component(String key, Object... args) {
        return withBrand(get(key, args));
    }

    /** Уже готовый текст (коды §) с тем же префиксом. */
    public static MutableComponent withBrand(String colored) {
        return brand().append(Component.literal(colored));
    }

    /** Ошибка скрипта без своего цвета: префикс и красный текст. */
    public static MutableComponent errorLine(String plain) {
        return withBrand("\u00a7c" + plain);
    }

    private static String colorize(String text) {
        return text.replaceAll("&([0-9a-fk-or])", "\u00a7$1");
    }

    /** Строки справки для корневой команды {@code root}. */
    public static List<String> helpLines(String root) {
        return List.of(get("help.header"), get("help.reload", root), get("help.reload_one", root),
                get("help.config", root),
                get("help.enable", root), get("help.disable", root), get("help.list", root),
                get("help.resources", root), get("help.ids", root), get("help.give", root), get("help.help", root),
                get("help.aliases"));
    }
}
