package com.scriptcraft.script.runtime;

/** Ошибка во время выполнения скрипта (нет игрока, неизвестный предмет, превышен лимит и т.п.). */
public class ScriptRuntimeException extends RuntimeException {
    public ScriptRuntimeException(String message) {
        super(message);
    }
}
