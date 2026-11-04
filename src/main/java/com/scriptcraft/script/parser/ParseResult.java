package com.scriptcraft.script.parser;

import com.scriptcraft.script.runtime.ScriptEvent;

import java.util.List;
import java.util.Map;

/** Итог разбора всех файлов. */
public record ParseResult(Map<ScriptEvent, List<EventHandler>> events,
                          Map<String, CommandDefinition> commands,
                          List<BlockDefinition> blocks,
                          List<ItemDefinition> items,
                          List<String> errors) {
}
