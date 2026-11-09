package com.scriptcraft.script.engine;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.script.ScriptLoader;
import com.scriptcraft.script.parser.BlockDefinition;
import com.scriptcraft.script.parser.CommandDefinition;
import com.scriptcraft.script.parser.EventHandler;
import com.scriptcraft.script.parser.ItemDefinition;
import com.scriptcraft.script.parser.ParseResult;
import com.scriptcraft.script.parser.PeriodHandler;
import com.scriptcraft.script.runtime.Action;
import com.scriptcraft.script.runtime.Actions;
import com.scriptcraft.script.runtime.ExecutionContext;
import com.scriptcraft.script.runtime.ScriptEvent;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Программа встроенного движка. Неизменяема: при /sc reload создаётся новая и
 * атомарно подменяет старую, поэтому выполняющийся код никогда не видит "полузагруженные" скрипты.
 */
final class SimpleProgram implements ScriptProgram {
    private final ParseResult parsed;

    SimpleProgram(ParseResult parsed) {
        this.parsed = parsed;
    }

    @Override
    public List<BlockDefinition> blocks() {
        return parsed.blocks();
    }

    @Override
    public List<ItemDefinition> items() {
        return parsed.items();
    }

    @Override
    public Set<String> commandNames() {
        return parsed.commands().keySet();
    }

    @Override
    public int commandPermission(String name) {
        CommandDefinition def = parsed.commands().get(name);
        return def == null ? -1 : def.permissionLevel();
    }

    @Override
    public CommandDefinition command(String name) {
        return parsed.commands().get(name);
    }

    @Override
    public java.util.List<EventHandler> eventHandlers(ScriptEvent event) {
        return parsed.events().getOrDefault(event, List.of());
    }

    @Override
    public void fireEvent(ScriptEvent event, ExecutionContext ctx) {
        String filter = switch (event) {
            case KEY -> ctx.placeholder("key");
            case OPEN -> ctx.placeholder("menu");
            default -> null;
        };
        for (EventHandler handler : parsed.events().getOrDefault(event, List.of())) {
            if (handler.filter() != null && filter != null
                    && !handler.filter().equals(filter) && !handler.filter().equals("any")) {
                continue;
            }
            ctx.setScript(handler.file());
            runActions(handler.actions(), ctx);
        }
    }

    @Override
    public void fireScriptEvent(ScriptEvent event, Predicate<String> files, MinecraftServer server) {
        for (EventHandler handler : parsed.events().getOrDefault(event, List.of())) {
            if (!files.test(handler.file())) {
                continue;
            }
            if (handler.eachPlayer()) {
                // Копия списка: обработчик может кого-то кикнуть/телепортировать между мирами.
                for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
                    runSafely(event, handler, new ExecutionContext(server, player, null));
                }
            } else {
                runSafely(event, handler, new ExecutionContext(server, null, null));
            }
        }
    }

    /** Каждый запуск — свой контекст и лимит; ошибка не прерывает остальные обработчики и игроков. */
    private static void runSafely(ScriptEvent event, EventHandler handler, ExecutionContext ctx) {
        ctx.setScript(handler.file());
        try {
            runActions(handler.actions(), ctx);
        } catch (RuntimeException e) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Ошибка в 'on {}' ({}): {}", event.scriptName(), handler.file(), e.getMessage());
            ScriptLoader.noteRuntime(handler.file(), e.getMessage(), ctx.player(), ctx.server());
        }
    }

    @Override
    public boolean runCommand(String name, ExecutionContext ctx) {
        Map<String, CommandDefinition> commands = parsed.commands();
        CommandDefinition def = commands.get(name);
        if (def == null) {
            return false;
        }
        ctx.setScript(def.file());
        runActions(def.actions(), ctx);
        return true;
    }

    @Override
    public void tickEvery(MinecraftServer server) {
        int tick = server.getTickCount();
        for (PeriodHandler handler : parsed.periods()) {
            if (tick < 1 || handler.periodTicks() < 1 || tick % handler.periodTicks() != 0) {
                continue;
            }
            ExecutionContext ctx = new ExecutionContext(server, null, null);
            ctx.setScript(handler.file());
            try {
                runActions(handler.actions(), ctx);
            } catch (RuntimeException e) {
                ScriptCraft.LOGGER.warn("[ScriptCraft] Ошибка в 'every' ({}): {}", handler.file(), e.getMessage());
                ScriptLoader.noteRuntime(handler.file(), e.getMessage(), null, server);
            }
        }
    }

    private static void runActions(List<Action> actions, ExecutionContext ctx) {
        Actions.runAll(actions, ctx);
    }
}
