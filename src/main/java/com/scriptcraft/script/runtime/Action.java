package com.scriptcraft.script.runtime;

/**
 * Одно действие скрипта (send, give, broadcast...).
 *
 * Песочница: скрипт может вызывать ТОЛЬКО действия, реализованные в {@link Actions}.
 * Никакой рефлексии, доступа к файлам или произвольным Java-классам у скриптов нет.
 */
@FunctionalInterface
public interface Action {
    void execute(ExecutionContext ctx);
}
