package com.scriptcraft.script.parser;

import com.scriptcraft.script.runtime.Action;

import java.util.List;

/**
 * Команда из "command /имя:" и строк под ней: permission, aliases, arguments.
 *
 * @param permissionLevel 0 — все, 2 — операторы
 * @param permissionNode  узел LuckPerms; null — только числовой уровень
 * @param players         можно вызывать игроком
 * @param console         можно вызывать из консоли
 * @param file            файл скрипта, в котором объявлена команда
 */
public record CommandDefinition(String name, int permissionLevel, List<String> aliases, List<ArgSpec> arguments,
                                boolean players, boolean console, List<Action> actions, String file,
                                @org.jetbrains.annotations.Nullable String permissionNode) {
}
