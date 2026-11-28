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

    record AtLine(int line, Action inner) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ctx.setLine(line);
            inner.execute(ctx);
        }
    }

    static Action atLine(int line, Action action) {
        if (action == null || line <= 0 || action instanceof AtLine) {
            return action;
        }
        return new AtLine(line, action);
    }

    static Action unwrap(Action action) {
        while (action instanceof AtLine at) {
            action = at.inner();
        }
        return action;
    }
}
