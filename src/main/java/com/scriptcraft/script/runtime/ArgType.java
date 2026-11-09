package com.scriptcraft.script.runtime;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Тип аргумента своей команды: player, text, integer и остальные. */
public enum ArgType {
    TEXT,
    PLAYER,
    OFFLINE,
    INTEGER,
    NUMBER,
    BOOLEAN,
    DATE,
    WORLD,
    ITEM;

    /** Разбирает имя типа из скрипта. null — неизвестное имя. */
    @Nullable
    public static ArgType parse(String raw) {
        String s = raw.strip().toLowerCase(Locale.ROOT).replace('-', ' ');
        return switch (s) {
            case "text", "string", "strings" -> TEXT;
            case "player", "online player" -> PLAYER;
            case "offline player", "offlineplayer" -> OFFLINE;
            case "integer", "int" -> INTEGER;
            case "number", "double", "float" -> NUMBER;
            case "boolean", "bool" -> BOOLEAN;
            case "date", "time" -> DATE;
            case "world", "dimension" -> WORLD;
            case "item" -> ITEM;
            default -> null;
        };
    }
}
