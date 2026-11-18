package com.scriptcraft.script.parser;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Тип параметра функции. Это не типы аргументов команды: у функции нет date и есть entity, block, location. */
public enum FuncType {
    PLAYER, OFFLINE, ENTITY, BLOCK, LOCATION, TEXT, INTEGER, NUMBER, BOOLEAN, WORLD, ITEM;

    @Nullable
    public static FuncType parse(String raw) {
        String s = raw.strip().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "player" -> PLAYER;
            case "offline player", "offline" -> OFFLINE;
            case "entity" -> ENTITY;
            case "block" -> BLOCK;
            case "location" -> LOCATION;
            case "text", "string" -> TEXT;
            case "integer", "int" -> INTEGER;
            case "number" -> NUMBER;
            case "boolean", "bool" -> BOOLEAN;
            case "world" -> WORLD;
            case "item" -> ITEM;
            default -> null;
        };
    }

    public String label() {
        return switch (this) {
            case PLAYER -> "player";
            case OFFLINE -> "offline player";
            case ENTITY -> "entity";
            case BLOCK -> "block";
            case LOCATION -> "location";
            case TEXT -> "text";
            case INTEGER -> "integer";
            case NUMBER -> "number";
            case BOOLEAN -> "boolean";
            case WORLD -> "world";
            case ITEM -> "item";
        };
    }
}
