package com.scriptcraft.script.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Простейший токенизатор: слова через пробел, строки в двойных кавычках ("..." с \" внутри),
 * запятые вне кавычек считаются пробелами (поэтому "at 0, -20" == "at 0 -20").
 */
final class Tokenizer {
    record Token(String value, boolean quoted) {
    }

    private Tokenizer() {
    }

    static List<Token> tokenize(String line) throws ParseException {
        List<Token> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '"') {
                flush(tokens, cur);
                StringBuilder str = new StringBuilder();
                i++;
                boolean closed = false;
                while (i < line.length()) {
                    char d = line.charAt(i);
                    if (d == '\\' && i + 1 < line.length()) {
                        str.append(line.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (d == '"') {
                        closed = true;
                        i++;
                        break;
                    }
                    str.append(d);
                    i++;
                }
                if (!closed) {
                    throw new ParseException("незакрытая кавычка");
                }
                tokens.add(new Token(str.toString(), true));
                continue;
            }
            if (Character.isWhitespace(c) || c == ',') {
                flush(tokens, cur);
            } else {
                cur.append(c);
            }
            i++;
        }
        flush(tokens, cur);
        return tokens;
    }

    private static void flush(List<Token> tokens, StringBuilder cur) {
        if (!cur.isEmpty()) {
            tokens.add(new Token(cur.toString(), false));
            cur.setLength(0);
        }
    }
}
