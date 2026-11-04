package com.scriptcraft.script.runtime;

import java.util.Locale;

/**
 * События, на которые могут подписываться скрипты ("on join:", "on quit:", "on load:", "on unload:").
 * Несколько событий у одного обработчика: "on join or load:" (см. ScriptParser).
 * TODO: on death, on respawn, on chat, on block break/place, on right click...
 */
public enum ScriptEvent {
    /** Игрок зашёл на сервер (в контексте — этот игрок). */
    JOIN("join", true),
    /** Игрок вышел (в контексте — этот игрок). */
    QUIT("quit", true),
    /**
     * Скрипт загружен: старт сервера, /sc reload, /sc reload &lt;скрипт&gt;, /sc enable &lt;скрипт&gt;.
     * Срабатывает только у ЭТОГО скрипта (перезагрузка другого файла его не вызывает). Игрока в контексте нет.
     */
    LOAD("load", false),
    /** Скрипт выгружается: /sc disable, перед /sc reload, остановка сервера. Игрока в контексте нет. */
    UNLOAD("unload", false);

    private final String scriptName;
    private final boolean playerEvent;

    ScriptEvent(String scriptName, boolean playerEvent) {
        this.scriptName = scriptName;
        this.playerEvent = playerEvent;
    }

    public String scriptName() {
        return scriptName;
    }

    /** true — событие про конкретного игрока (join/quit), false — про сам скрипт (load/unload). */
    public boolean playerEvent() {
        return playerEvent;
    }

    /** Ищет событие по имени из скрипта, например "join" или "script load". Возвращает null, если не найдено. */
    public static ScriptEvent byName(String name) {
        String n = name.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        // Как в Skript: "on script load" / "on script unload" — то же, что "on load" / "on unload".
        if (n.startsWith("script ")) {
            n = n.substring(7);
        }
        for (ScriptEvent e : values()) {
            if (e.scriptName.equals(n)) {
                return e;
            }
        }
        return null;
    }
}
