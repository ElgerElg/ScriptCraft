package com.scriptcraft.script.runtime;

import com.scriptcraft.config.ScriptCraftConfig;

/**
 * Лимит на одно выполнение скрипта: максимум шагов и максимум времени.
 * Значения берутся из scriptcraft/config.toml (maxScriptSteps, maxScriptMillis).
 * Каждое действие, включая действия внутри if, расходует один шаг.
 */
public final class ExecutionBudget {
    private final int maxSteps;
    private final long deadlineNanos;
    private int steps;

    public ExecutionBudget(int maxSteps, long maxMillis) {
        this.maxSteps = maxSteps;
        this.deadlineNanos = System.nanoTime() + maxMillis * 1_000_000L;
    }

    /** Лимиты из конфига (до загрузки конфига — значения по умолчанию 10000 шагов / 50 мс). */
    public static ExecutionBudget fromConfig() {
        return new ExecutionBudget(ScriptCraftConfig.maxScriptSteps(), ScriptCraftConfig.maxScriptMillis());
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
