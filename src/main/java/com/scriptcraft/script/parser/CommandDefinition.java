package com.scriptcraft.script.parser;

import com.scriptcraft.script.runtime.Action;

import java.util.List;

/**
 * Команда из "command /<name> [permission N]:".
 *
 * @param permissionLevel 0 — все игроки, 2 — операторы (как у ванильных команд)
 */
public record CommandDefinition(String name, int permissionLevel, List<Action> actions) {
}
