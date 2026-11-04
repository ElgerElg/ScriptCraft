package com.scriptcraft.script.runtime;

/**
 * События, на которые могут подписываться скрипты ("on join:", "on quit:").
 * TODO: on death, on respawn, on chat, on block break/place, on right click...
 */
public enum ScriptEvent {
    JOIN("join"),
    QUIT("quit");

    private final String scriptName;

    ScriptEvent(String scriptName) {
        this.scriptName = scriptName;
    }

    public String scriptName() {
        return scriptName;
    }

    /** Ищет событие по имени из скрипта, например "join". Возвращает null, если не найдено. */
    public static ScriptEvent byName(String name) {
        for (ScriptEvent e : values()) {
            if (e.scriptName.equalsIgnoreCase(name)) {
                return e;
            }
        }
        return null;
    }
}
