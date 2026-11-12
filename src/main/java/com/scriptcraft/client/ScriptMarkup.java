package com.scriptcraft.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Подсветка-маркер: события жёлтым, слова if и else синим, весь текст в кавычках зелёным. */
@OnlyIn(Dist.CLIENT)
public final class ScriptMarkup {
    public enum Kind {
        EVENT, RESOURCE, CONDITION, QUOTE
    }

    public record Mark(int from, int to, Kind kind, String token) {
    }

    private static final Pattern HEADER = Pattern.compile("(?i)^(\\s*)((?:on|every|command)\\b[^:\\n]*:|define\\b)");
    private static final Pattern ELSE_WORD = Pattern.compile("(?i)^\\s*(else)\\b(?:\\s+(if)\\b)?");
    private static final Pattern IF_WORD = Pattern.compile("(?i)^\\s*(if)\\b");
    private static final Pattern RESOURCE = Pattern.compile("(?i)(?:[a-z0-9_.-]+:[a-z0-9_./-]+)|(?:[a-z0-9_.-]+(?:/[a-z0-9_.-]+)+\\.png)");

    private ScriptMarkup() {
    }

    public static List<Mark> of(String line) {
        List<Mark> marks = new ArrayList<>();
        Matcher header = HEADER.matcher(line);
        if (header.find()) {
            add(marks, header.start(2), header.end(2), Kind.EVENT, header.group(2));
        }
        Matcher elseWord = ELSE_WORD.matcher(line);
        if (elseWord.find()) {
            add(marks, elseWord.start(1), elseWord.end(1), Kind.CONDITION, elseWord.group(1));
            if (elseWord.group(2) != null) {
                add(marks, elseWord.start(2), elseWord.end(2), Kind.CONDITION, elseWord.group(2));
            }
        } else {
            Matcher ifWord = IF_WORD.matcher(line);
            if (ifWord.find()) {
                add(marks, ifWord.start(1), ifWord.end(1), Kind.CONDITION, ifWord.group(1));
            }
        }
        Matcher resource = RESOURCE.matcher(line);
        while (resource.find()) {
            add(marks, resource.start(), resource.end(), Kind.RESOURCE, resource.group());
        }
        quotes(line, marks);
        return marks;
    }

    private static void quotes(String line, List<Mark> marks) {
        boolean inside = false;
        int start = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && inside && i + 1 < line.length()) {
                i++;
                continue;
            }
            if (c != '"') {
                continue;
            }
            if (inside && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                i++;
                continue;
            }
            if (!inside) {
                start = i;
                inside = true;
            } else {
                add(marks, start, i + 1, Kind.QUOTE, line.substring(start, i + 1));
                inside = false;
            }
        }
        if (inside) {
            add(marks, start, line.length(), Kind.QUOTE, line.substring(start));
        }
    }

    private static void add(List<Mark> marks, int from, int to, Kind kind, String token) {
        if (from >= to) {
            return;
        }
        if (kind != Kind.QUOTE) {
            for (Mark mark : marks) {
                if (from < mark.to && to > mark.from) {
                    return;
                }
            }
        }
        marks.add(new Mark(from, to, kind, token));
    }
}
