package com.scriptcraft.script;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.command.ScriptCommands;
import com.scriptcraft.registry.ScriptRegistries;
import com.scriptcraft.script.engine.ScriptEngine;
import com.scriptcraft.script.engine.ScriptProgram;
import com.scriptcraft.script.engine.SimpleScriptEngine;
import com.scriptcraft.script.parser.BlockDefinition;
import com.scriptcraft.script.parser.ItemDefinition;
import com.scriptcraft.script.runtime.ExecutionContext;
import com.scriptcraft.script.runtime.ScriptEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Загрузчик скриптов: читает папку, компилирует движком, хранит текущую программу,
 * запускает события и команды, делает горячую перезагрузку.
 */
public final class ScriptLoader {
    /** Текущий движок. Сюда можно подставить другой (например, на Rhino) — см. TODO в README. */
    private static final ScriptEngine ENGINE = new SimpleScriptEngine();

    /** Текущая программа. volatile + замена целиком = безопасная горячая перезагрузка. */
    private static volatile ScriptProgram program = ENGINE.compile(List.of(), new ArrayList<>());
    private static volatile List<String> lastErrors = List.of();

    private ScriptLoader() {
    }

    public static ScriptProgram getProgram() {
        return program;
    }

    public static List<String> lastErrors() {
        return lastErrors;
    }

    /** Первая загрузка — из конструктора мода (до регистрации блоков). */
    public static void initialLoad() {
        loadFromDisk();
        ScriptCraft.LOGGER.info("[ScriptCraft] Загружено: команд {}, блоков {}, предметов {}, ошибок {}",
                program.commandNames().size(), program.blocks().size(), program.items().size(), lastErrors.size());
        lastErrors.forEach(e -> ScriptCraft.LOGGER.warn("[ScriptCraft] {}", e));
    }

    private static void loadFromDisk() {
        List<String> errors = new ArrayList<>();
        List<ScriptSource> sources = ScriptPaths.readAll(errors);
        ScriptProgram compiled = ENGINE.compile(sources, errors);
        program = compiled;
        lastErrors = List.copyOf(errors);
    }

    /**
     * /scriptcraft reload — горячая перезагрузка ЛОГИКИ.
     * Блоки/предметы НЕ перерегистрируются (реестры заморожены) — о новых выводится предупреждение.
     */
    public static int reload(CommandSourceStack source) {
        loadFromDisk();
        MinecraftServer server = source.getServer();

        // Новые команды добавляем в существующее дерево и заново рассылаем его игрокам.
        ScriptCommands.registerScriptCommands(server.getCommands().getDispatcher());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            server.getCommands().sendCommands(player);
        }

        int errors = lastErrors.size();
        source.sendSuccess(() -> Component.literal("[ScriptCraft] Скрипты перезагружены: команд "
                + program.commandNames().size() + ", ошибок " + errors), true);
        for (String e : lastErrors) {
            source.sendFailure(Component.literal(e));
        }

        for (BlockDefinition b : program.blocks()) {
            if (!ScriptRegistries.registeredBlockIds().contains(b.id())) {
                warnRestart(source, "блок " + b.id());
            }
        }
        for (ItemDefinition i : program.items()) {
            if (!ScriptRegistries.registeredItemIds().contains(i.id())) {
                warnRestart(source, "предмет " + i.id());
            }
        }
        return errors == 0 ? 1 : 0;
    }

    private static void warnRestart(CommandSourceStack source, String what) {
        source.sendSuccess(() -> Component.literal("[ScriptCraft] Новый " + what
                + " появится только после перезапуска сервера и клиентов").withStyle(ChatFormatting.YELLOW), false);
    }

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

    /** Запуск команды из скрипта. Возвращает результат для Brigadier (1 — успех). */
    public static int runCommand(String name, CommandSourceStack source, @Nullable String args) {
        ExecutionContext ctx = new ExecutionContext(source.getServer(), source.getPlayer(), source);
        ctx.setVariable("args", args == null ? "" : args);
        try {
            if (!program.runCommand(name, ctx)) {
                // Команда была удалена из скриптов, но узел Brigadier остался до перезапуска.
                source.sendFailure(Component.literal("Команда /" + name + " удалена из скриптов"));
                return 0;
            }
            return 1;
        } catch (RuntimeException e) {
            source.sendFailure(Component.literal("[ScriptCraft] Ошибка в /" + name + ": " + e.getMessage()));
            return 0;
        }
    }
}
