package com.scriptcraft.script.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Простейший токенизатор: слова через пробел, строки в двойных кавычках ("..." с \" внутри),
 * запятые вне кавычек считаются пробелами (поэтому "at 0, -20" == "at 0 -20"),
 * переменная в фигурных скобках — один токен целиком, даже с пробелами и запятыми: {my var::%player%},
 * ванильный селектор с аргументами — тоже один токен как есть: @a[distance=..10,name="Steve"].
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
            if (c == '{' && cur.isEmpty()) {
                int close = line.indexOf('}', i);
                if (close < 0) {
                    throw new ParseException("незакрытая фигурная скобка '{'");
                }
                tokens.add(new Token(line.substring(i, close + 1), false));
                i = close + 1;
                continue;
            }
            if (c == '[' && cur.length() == 2 && cur.charAt(0) == '@') {
                // Аргументы селектора: копируем до парной ']' (с учётом кавычек), запятые внутри не режем.
                int depth = 0;
                boolean inQuote = false;
                int j = i;
                for (; j < line.length(); j++) {
                    char d = line.charAt(j);
                    if (inQuote) {
                        if (d == '\\') {
                            j++;
                        } else if (d == '"') {
                            inQuote = false;
                        }
                    } else if (d == '"') {
                        inQuote = true;
                    } else if (d == '[') {
                        depth++;
                    } else if (d == ']' && --depth == 0) {
                        break;
                    }
                }
                if (j >= line.length()) {
                    throw new ParseException("незакрытая скобка '[' в селекторе " + cur);
                }
                cur.append(line, i, j + 1);
                i = j + 1;
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
