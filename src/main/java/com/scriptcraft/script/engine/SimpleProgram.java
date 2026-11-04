package com.scriptcraft.script.engine;

import com.scriptcraft.script.parser.BlockDefinition;
import com.scriptcraft.script.parser.CommandDefinition;
import com.scriptcraft.script.parser.ItemDefinition;
import com.scriptcraft.script.parser.ParseResult;
import com.scriptcraft.script.runtime.Action;
import com.scriptcraft.script.runtime.Actions;
import com.scriptcraft.script.runtime.ExecutionContext;
import com.scriptcraft.script.runtime.ScriptEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Программа встроенного движка. Неизменяема: при /sk reload создаётся новая и
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
    public void fireEvent(ScriptEvent event, ExecutionContext ctx) {
        for (List<Action> handler : parsed.events().getOrDefault(event, List.of())) {
            runActions(handler, ctx);
        }
    }

    @Override
    public boolean runCommand(String name, ExecutionContext ctx) {
        Map<String, CommandDefinition> commands = parsed.commands();
        CommandDefinition def = commands.get(name);
        if (def == null) {
            return false;
        }
        runActions(def.actions(), ctx);
        return true;
    }

    private static void runActions(List<Action> actions, ExecutionContext ctx) {
        Actions.runAll(actions, ctx);
    }
}
