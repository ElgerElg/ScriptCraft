package com.scriptcraft.script;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.command.ScriptCommands;
import com.scriptcraft.config.ScriptCraftConfig;
import com.scriptcraft.hud.HudManager;
import com.scriptcraft.lang.Messages;
import com.scriptcraft.registry.BindingSync;
import com.scriptcraft.script.engine.ScriptEngine;
import com.scriptcraft.script.engine.ScriptProgram;
import com.scriptcraft.script.engine.SimpleScriptEngine;
import com.scriptcraft.script.runtime.ExecutionContext;
import com.scriptcraft.script.runtime.ScriptEvent;
import com.scriptcraft.script.variables.VariableStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Загрузчик скриптов: хранит исходники включённых скриптов, компилирует их движком в одну программу,
 * запускает события и команды, делает горячую перезагрузку, включает и выключает скрипты.
 *
 * Как работает перезагрузка одного скрипта: заново читается только этот файл, остальные берутся из памяти,
 * затем вся программа пересобирается (разбор быстрый). Так нет "полузагруженных" состояний.
 *
 * Жизненный цикл скрипта: "on unload:" старой версии -> пересборка -> "on load:" новой версии.
 * load срабатывает при старте сервера, /sc reload, /sc reload &lt;скрипт&gt; и /sc enable — только у затронутых
 * файлов. Поэтому HUD из "on join or load:" появляется у игроков онлайн сразу, без перезахода.
 */
public final class ScriptLoader {
    /** Текущий движок. Сюда можно подставить другой (например, на Rhino) — см. TODO в README. */
    private static final ScriptEngine ENGINE = new SimpleScriptEngine();

    /** Исходники включённых скриптов: относительное имя -> текст. */
    private static final Map<String, ScriptSource> SOURCES = new TreeMap<>();

    /** Текущая программа. volatile + замена целиком = безопасная горячая перезагрузка. */
    private static volatile ScriptProgram program = ENGINE.compile(List.of(), new ArrayList<>());
    private static volatile List<String> lastErrors = List.of();
    /** Ошибки чтения файлов (накапливаются до следующей полной перезагрузки). */
    private static final List<String> READ_ERRORS = new ArrayList<>();

    private ScriptLoader() {
    }

    public static ScriptProgram getProgram() {
        return program;
    }

    public static List<String> lastErrors() {
        return lastErrors;
    }

    /** Имена загруженных (включённых) скриптов. */
    public static List<String> loadedScripts() {
        return List.copyOf(SOURCES.keySet());
    }

    // ------------------------------------------------------------------ загрузка

    /**
     * Первая загрузка — из конструктора мода (до регистрации реестров; define отсюда задают только подсказки слотов).
     * Конфиг в этот момент ещё не загружен, поэтому используются настройки по умолчанию.
     */
    public static void initialLoad() {
        loadAllFromDisk();
        ScriptCraft.LOGGER.info("[ScriptCraft] Загружено скриптов {}: команд {}, блоков {}, предметов {}, ошибок {}",
                SOURCES.size(), program.commandNames().size(), program.blocks().size(), program.items().size(), lastErrors.size());
        lastErrors.forEach(e -> ScriptCraft.LOGGER.warn("[ScriptCraft] {}", e));
    }

    private static void loadAllFromDisk() {
        SOURCES.clear();
        READ_ERRORS.clear();
        for (ScriptSource source : ScriptPaths.readAll(READ_ERRORS)) {
            SOURCES.put(source.fileName(), source);
        }
        recompile();
    }

    /** Пересобирает программу из SOURCES и атомарно подменяет текущую. */
    private static void recompile() {
        long start = System.nanoTime();
        List<String> errors = new ArrayList<>(READ_ERRORS);
        ScriptProgram compiled = ENGINE.compile(new ArrayList<>(SOURCES.values()), errors);
        program = compiled;
        lastErrors = List.copyOf(errors);
        if (ScriptCraftConfig.logScriptLoadTimes()) {
            ScriptCraft.LOGGER.info("[ScriptCraft] {}", Messages.get("reload.time", (System.nanoTime() - start) / 1_000_000L));
        }
    }

    // ------------------------------------------------------------------ команды /sc

    /** /sc reload — перезагрузить все скрипты с диска. */
    public static int reloadAll(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        fireScriptEvent(ScriptEvent.UNLOAD, f -> true, server);
        loadAllFromDisk();
        afterChange(source);
        fireScriptEvent(ScriptEvent.LOAD, f -> true, server);
        int errors = lastErrors.size();
        source.sendSuccess(() -> Messages.component("reload.all", SOURCES.size(), program.commandNames().size(), errors), true);
        sendErrors(source);
        return errors == 0 ? 1 : 0;
    }

    /** /sc reload <скрипт> — перезагрузить один файл. */
    public static int reloadOne(CommandSourceStack source, String input) {
        if (isAll(input)) {
            return reloadAll(source);
        }
        String name = ScriptPaths.normalize(input);
        if (name == null || !Files.isRegularFile(ScriptPaths.resolve(name))) {
            if (name != null && Files.isRegularFile(ScriptPaths.resolve(ScriptPaths.disabledName(name)))) {
                source.sendFailure(Messages.component("script.is_disabled", name));
            } else {
                source.sendFailure(Messages.component("script.not_found", input));
            }
            return 0;
        }
        List<String> errors = new ArrayList<>();
        ScriptSource fresh = ScriptPaths.readOne(name, errors);
        if (fresh == null) {
            errors.forEach(e -> source.sendFailure(Component.literal(e)));
            return 0;
        }
        fireScriptEvent(ScriptEvent.UNLOAD, name::equals, source.getServer());
        SOURCES.put(name, fresh);
        recompile();
        afterChange(source);
        fireScriptEvent(ScriptEvent.LOAD, name::equals, source.getServer());
        source.sendSuccess(() -> Messages.component("reload.one", name, lastErrors.size()), true);
        sendErrors(source);
        return 1;
    }

    /** /sc disable <скрипт> — выгрузить и переименовать в "-имя.sk". */
    public static int disable(CommandSourceStack source, String input) {
        String name = ScriptPaths.normalize(input);
        if (name == null) {
            source.sendFailure(Messages.component("script.not_found", input));
            return 0;
        }
        String disabled = ScriptPaths.disabledName(name);
        if (!Files.isRegularFile(ScriptPaths.resolve(name))) {
            source.sendFailure(Files.isRegularFile(ScriptPaths.resolve(disabled))
                    ? Messages.component("script.already_disabled", name)
                    : Messages.component("script.not_found", input));
            return 0;
        }
        try {
            ScriptPaths.rename(name, disabled);
        } catch (IOException e) {
            source.sendFailure(Messages.component("script.rename_failed", e.getMessage()));
            return 0;
        }
        boolean hadDefines = hasDefines(SOURCES.get(name));
        // Сначала "on unload:" ещё загруженной версии, потом убираем всё, что скрипт оставил в HUD.
        fireScriptEvent(ScriptEvent.UNLOAD, name::equals, source.getServer());
        HudManager.unloadScript(name, source.getServer());
        SOURCES.remove(name);
        recompile();
        afterChange(source);
        source.sendSuccess(() -> Messages.component("script.disabled", name, disabled), true);
        if (hadDefines) {
            source.sendSuccess(() -> Messages.component("script.defines_warning"), false);
        }
        return 1;
    }

    /** /sc enable <скрипт> — переименовать "-имя.sk" обратно и загрузить. */
    public static int enable(CommandSourceStack source, String input) {
        String name = ScriptPaths.normalize(input);
        if (name == null) {
            source.sendFailure(Messages.component("script.not_found", input));
            return 0;
        }
        String disabled = ScriptPaths.disabledName(name);
        if (!Files.isRegularFile(ScriptPaths.resolve(disabled))) {
            source.sendFailure(Files.isRegularFile(ScriptPaths.resolve(name))
                    ? Messages.component("script.already_enabled", name)
                    : Messages.component("script.not_found", input));
            return 0;
        }
        try {
            ScriptPaths.rename(disabled, name);
        } catch (IOException e) {
            source.sendFailure(Messages.component("script.rename_failed", e.getMessage()));
            return 0;
        }
        List<String> errors = new ArrayList<>();
        ScriptSource fresh = ScriptPaths.readOne(name, errors);
        if (fresh != null) {
            SOURCES.put(name, fresh);
        }
        recompile();
        afterChange(source);
        // "on load:" / "on join or load:" — HUD и прочая подготовка сразу для игроков онлайн.
        fireScriptEvent(ScriptEvent.LOAD, name::equals, source.getServer());
        source.sendSuccess(() -> Messages.component("script.enabled", name), true);
        errors.forEach(e -> source.sendFailure(Component.literal(e)));
        sendErrors(source);
        return 1;
    }

    /** /sc list */
    public static int list(CommandSourceStack source) {
        List<String> disabled = ScriptPaths.listScripts().stream().filter(ScriptPaths::isDisabled).toList();
        String none = Messages.get("list.none");
        source.sendSuccess(() -> Messages.component("list.scripts", SOURCES.isEmpty() ? none : String.join(", ", SOURCES.keySet())), false);
        if (!disabled.isEmpty()) {
            source.sendSuccess(() -> Messages.component("list.disabled", String.join(", ", disabled)), false);
        }
        String commands = program.commandNames().isEmpty() ? none : "/" + String.join(", /", program.commandNames());
        source.sendSuccess(() -> Messages.component("list.summary", commands, program.blocks().size(),
                program.items().size(), VariableStore.size(), lastErrors.size()), false);
        sendErrors(source);
        return 1;
    }

    private static boolean isAll(String input) {
        String s = input.strip().toLowerCase();
        return s.equals("all") || s.equals("*") || s.equals("scripts");
    }

    /** Есть ли в скрипте define (при выключении его блоки остаются в мире "свободными слотами"). Грубая, но достаточная проверка. */
    private static boolean hasDefines(@Nullable ScriptSource source) {
        return source != null && source.lines().stream().anyMatch(l -> l.strip().toLowerCase().startsWith("define "));
    }

    /**
     * После любого изменения: новые команды добавляются в дерево Brigadier и дерево заново рассылается игрокам;
     * define заново привязываются к слотам пула — новые блоки/предметы появляются у всех игроков сразу,
     * без перезапуска (см. {@link BindingSync}).
     */
    private static void afterChange(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ScriptCommands.registerScriptCommands(server.getCommands().getDispatcher());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            server.getCommands().sendCommands(player);
        }
        BindingSync.apply(server, program, source);
    }

    private static void sendErrors(CommandSourceStack source) {
        for (String e : lastErrors) {
            source.sendFailure(Component.literal(e));
        }
    }

    // ------------------------------------------------------------------ выполнение

    /** Запуск обработчиков события ("on join:" и т.д.). Ошибки пишутся в лог и не роняют сервер. */
    public static void fireEvent(ScriptEvent event, ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ExecutionContext ctx = new ExecutionContext(server, player, null);
        try {
            program.fireEvent(event, ctx);
        } catch (RuntimeException e) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Ошибка в 'on {}': {}", event.scriptName(), e.getMessage());
        }
    }

    /** "on load:"/"on unload:" для файлов, прошедших фильтр. Ошибки — в лог, сервер не падает. */
    private static void fireScriptEvent(ScriptEvent event, Predicate<String> files, MinecraftServer server) {
        try {
            program.fireScriptEvent(event, files, server);
        } catch (RuntimeException e) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Ошибка в 'on {}': {}", event.scriptName(), e.getMessage());
        }
    }

    /** Сервер запущен (мир загружен, переменные прочитаны): "on load:" всех включённых скриптов. */
    public static void onServerStarted(MinecraftServer server) {
        fireScriptEvent(ScriptEvent.LOAD, f -> true, server);
    }

    /** Сервер останавливается (игроки ещё на месте, переменные ещё не сохранены): "on unload:" всех скриптов. */
    public static void onServerStopping(MinecraftServer server) {
        fireScriptEvent(ScriptEvent.UNLOAD, f -> true, server);
    }

    /** Запуск команды из скрипта. Возвращает результат для Brigadier (1 — успех). */
    public static int runCommand(String name, CommandSourceStack source, @Nullable String args) {
        ExecutionContext ctx = new ExecutionContext(source.getServer(), source.getPlayer(), source);
        ctx.setPlaceholder("args", args == null ? "" : args);
        try {
            if (!program.runCommand(name, ctx)) {
                // Скрипт выключен или команда удалена, но узел Brigadier остаётся до перезапуска.
                source.sendFailure(Messages.component("command.disabled", name));
                return 0;
            }
            return 1;
        } catch (RuntimeException e) {
            source.sendFailure(Messages.component("command.error", name, e.getMessage()));
            return 0;
        }
    }
}
