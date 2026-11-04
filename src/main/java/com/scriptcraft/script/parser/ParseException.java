package com.scriptcraft.script.parser;

/** Ошибка разбора одной строки скрипта. */
public class ParseException extends Exception {
    public ParseException(String message) {
        super(message);
    }
}
