package com.scriptcraft.script.parser;

/** Ошибка разбора одной строки скрипта. */
public class ParseException extends Exception {
    public ParseException(String message) {
        super(com.scriptcraft.lang.Phrase.localize(message));
    }

    public ParseException(String en, String ru) {
        super(com.scriptcraft.lang.Phrase.pick(en, ru));
    }
}
