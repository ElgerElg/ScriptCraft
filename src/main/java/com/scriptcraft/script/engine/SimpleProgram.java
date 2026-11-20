package com.scriptcraft.script.engine;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.script.ScriptLoader;
import com.scriptcraft.script.parser.BlockDefinition;
import com.scriptcraft.script.parser.CommandDefinition;
import com.scriptcraft.script.parser.EventHandler;
import com.scriptcraft.script.parser.ItemDefinition;
import com.scriptcraft.lang.Messages;
import com.scriptcraft.script.parser.FunctionDefinition;
import com.scriptcraft.script.parser.ParseResult;
import com.scriptcraft.script.parser.PeriodHandler;
import com.scriptcraft.script.runtime.Action;
import com.scriptcraft.script.runtime.Actions;
import com.scriptcraft.script.runtime.ExecutionContext;
import com.scriptcraft.script.runtime.Functions;
import com.scriptcraft.script.runtime.HudActions;
import com.scriptcraft.script.runtime.ScriptEvent;
import com.scriptcraft.script.runtime.ScriptRuntimeException;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
        if (ctx.player() != null && ctx.placeholderOrNull("screen") == null) {
            String active = com.scriptcraft.screen.ScreenManager.activeScreen(ctx.player());
            if (active != null) {
                ctx.setPlaceholder("screen", active);
            }
        }
        for (EventHandler handler : parsed.events().getOrDefault(event, List.of())) {
            if (!matchesFilter(event, handler.filter(), ctx)) {
                continue;
            }
            ctx.setScript(handler.file());
            try {
                runActions(handler.actions(), ctx);
            } catch (RuntimeException e) {
                ScriptCraft.LOGGER.warn("[ScriptCraft] Ошибка в 'on {}' ({}): {}", event.scriptName(), handler.file(), e.getMessage());
                ScriptLoader.noteRuntime(handler.file(), e.getMessage(), ctx.player(), ctx.server());
            }
        }
    }

    private static boolean matchesFilter(ScriptEvent event, String handlerFilter, ExecutionContext ctx) {
        if (handlerFilter == null || handlerFilter.isEmpty()) {
            return true;
        }
        return switch (event) {
            case KEY, KEY_RELEASE, KEY_HOLD -> {
                String key = ctx.placeholder("key");
                String screen = ctx.placeholderOrNull("screen");
                int at = handlerFilter.indexOf('@');
                String wantKey = at >= 0 ? handlerFilter.substring(0, at) : handlerFilter;
                String wantScreen = at >= 0 ? handlerFilter.substring(at + 1) : null;
                boolean keyOk = wantKey.equals("any") || wantKey.equalsIgnoreCase(key);
                if (!keyOk) {
                    yield false;
                }
                if (wantScreen == null) {
                    yield true;
                }
                boolean hasScreen = screen != null && !screen.isEmpty() && !screen.equals("none");
                if (wantScreen.equals("*")) {
                    yield hasScreen;
                }
                yield hasScreen && wantScreen.equalsIgnoreCase(screen);
            }
            case OPEN -> {
                String menu = ctx.placeholder("menu");
                yield handlerFilter.equals("any") || handlerFilter.equalsIgnoreCase(menu);
            }
            case SCREEN_OPEN, SCREEN_CLOSE -> {
                String screen = ctx.placeholder("screen");
                yield handlerFilter.equals("*") || handlerFilter.equals("any") || handlerFilter.equalsIgnoreCase(screen);
            }
            default -> true;
        };
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

    @Override
    public void replayHud(Predicate<String> files, MinecraftServer server) {
        List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
        if (players.isEmpty()) {
            return;
        }
        for (ScriptEvent event : List.of(ScriptEvent.LOAD, ScriptEvent.JOIN)) {
            for (EventHandler handler : parsed.events().getOrDefault(event, List.of())) {
                if (!files.test(handler.file())) {
                    continue;
                }
                List<Action> hud = hudActions(handler.actions());
                if (hud.isEmpty()) {
                    continue;
                }
                for (ServerPlayer player : players) {
                    ExecutionContext ctx = new ExecutionContext(server, player, null);
                    ctx.setScript(handler.file());
                    try {
                        Actions.runAll(hud, ctx);
                    } catch (RuntimeException e) {
                        String message = e.getMessage() == null ? e.toString() : e.getMessage();
                        ScriptCraft.LOGGER.warn("[ScriptCraft] HUD {} не обновился: {}", handler.file(), message);
                        ScriptLoader.noteRuntime(handler.file(), message, player, server);
                    }
                }
            }
        }
    }

    /** create hud, полосы, текст и hide/show vanilla. Вложенные if не повторяются. */
    private static List<Action> hudActions(List<Action> actions) {
        List<Action> hud = new ArrayList<>();
        for (Action action : actions) {
            if (action instanceof HudActions.CreateHud || action instanceof HudActions.Vanilla
                    || action instanceof HudActions.SetBar || action instanceof HudActions.SetText
                    || action instanceof HudActions.Visibility || action instanceof HudActions.Remove
                    || action instanceof HudActions.Clear) {
                hud.add(action);
            }
        }
        return hud;
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
    public void callFunction(String name, List<Object> args, ExecutionContext ctx) {
        FunctionDefinition def = parsed.functions().get(name.toLowerCase(Locale.ROOT));
        if (def == null) {
            throw new ScriptRuntimeException(Messages.get("fn.unknown", name));
        }
        if (args.size() != def.params().size()) {
            throw new ScriptRuntimeException(Messages.get("fn.arity", def.name(), def.params().size(), args.size()));
        }
        if (!ctx.enterFunction()) {
            throw new ScriptRuntimeException(Messages.get("fn.depth"));
        }
        Map<String, Object> frame = new LinkedHashMap<>();
        String previous = ctx.script();
        boolean pushed = false;
        try {
            for (int i = 0; i < args.size(); i++) {
                FunctionDefinition.Param param = def.params().get(i);
                frame.put(param.key(), Functions.coerce(param.type(), args.get(i), ctx, param.name()));
            }
            ctx.pushFrame(frame);
            pushed = true;
            ctx.setScript(def.file());
            Actions.runAll(def.actions(), ctx);
        } finally {
            if (pushed) {
                ctx.popFrame();
            }
            ctx.setScript(previous);
            ctx.exitFunction();
        }
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
