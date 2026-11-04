package com.scriptcraft.script.runtime;

/**
 * Лимит на одно выполнение скрипта: максимум шагов и максимум времени.
 * Сейчас в языке нет циклов, но лимит заложен заранее — когда появятся loop/while/wait,
 * плохой скрипт не сможет "повесить" сервер.
 */
public final class ExecutionBudget {
    public static final int DEFAULT_MAX_STEPS = 10_000;
    public static final long DEFAULT_MAX_MILLIS = 50;

    private final int maxSteps;
    private final long deadlineNanos;
    private int steps;

    public ExecutionBudget(int maxSteps, long maxMillis) {
        this.maxSteps = maxSteps;
        this.deadlineNanos = System.nanoTime() + maxMillis * 1_000_000L;
    }

    public static ExecutionBudget defaults() {
        return new ExecutionBudget(DEFAULT_MAX_STEPS, DEFAULT_MAX_MILLIS);
    }

    /** Вызывается перед каждым действием. Бросает исключение, если лимит превышен. */
    public void step() {
        if (++steps > maxSteps) {
            throw new ScriptRuntimeException("Превышен лимит действий (" + maxSteps + ")");
        }
        if (System.nanoTime() > deadlineNanos) {
            throw new ScriptRuntimeException("Превышен лимит времени выполнения");
        }
    }
}
