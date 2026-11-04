package com.scriptcraft.registry;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Замена id из скриптов на технические id слотов пула ("example:ruby_block" -> "scriptcraft:dyn_block_3").
 * Меняются только ЦЕЛЫЕ id с namespace, которые есть в таблице: "example:ruby_block_2", "#example:ruby_block"
 * (тег) и "example:blocks/ruby_block" (путь лут-таблицы) не трогаются. Короткие id без namespace — тоже.
 */
public final class IdRewriter {
    /** id в кавычках внутри JSON / SNBT. */
    private static final Pattern QUOTED_ID = Pattern.compile("\"([a-z0-9_.-]+:[a-z0-9_./-]+)\"");
    /**
     * id как отдельный токен команды: слева не буква/цифра/_.-:/# (то есть пробел, начало, [ { , = " ' и т.п.),
     * справа — не продолжение id (дальше может идти [состояние] или {nbt}).
     */
    private static final Pattern TOKEN_ID = Pattern.compile("(?<![A-Za-z0-9_.:/#-])([a-z0-9_.-]+:[a-z0-9_./-]+)(?![A-Za-z0-9_./:-])");
    /** Команды с произвольным текстом: в сообщениях игроков id не подменяем. */
    private static final Set<String> TEXT_COMMANDS = Set.of("say", "me", "msg", "tell", "w", "tellraw", "teammsg", "tm");

    private IdRewriter() {
    }

    /** JSON (теги, лут, рецепты): только id в кавычках. */
    public static String rewriteJson(String text, Map<String, String> map) {
        return replace(QUOTED_ID, text, map);
    }

    /**
     * Строка команды (без или с ведущим "/"). Возвращает ту же строку (тот же объект), если менять нечего.
     * Команды чата (say, msg, tellraw...) не трогаются.
     */
    public static String rewriteCommand(String command, Map<String, String> map) {
        if (map.isEmpty() || command.indexOf(':') < 0) {
            return command;
        }
        String s = command.startsWith("/") ? command.substring(1) : command;
        int space = s.indexOf(' ');
        String root = (space < 0 ? s : s.substring(0, space)).toLowerCase(Locale.ROOT);
        if (TEXT_COMMANDS.contains(root)) {
            return command;
        }
        String out = replace(TOKEN_ID, command, map);
        return out.equals(command) ? command : out;
    }

    /** Файл функции (.mcfunction): каждая строка — команда, комментарии (#) не трогаются. */
    public static String rewriteFunction(String text, Map<String, String> map) {
        if (map.isEmpty()) {
            return text;
        }
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            out.append(line.stripLeading().startsWith("#") ? line : rewriteCommand(line, map));
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static String replace(Pattern pattern, String text, Map<String, String> map) {
        if (map.isEmpty()) {
            return text;
        }
        Matcher m = pattern.matcher(text);
        StringBuilder out = null;
        int last = 0;
        while (m.find()) {
            String replacement = map.get(m.group(1));
            if (replacement == null) {
                continue;
            }
            if (out == null) {
                out = new StringBuilder(text.length() + 16);
            }
            out.append(text, last, m.start(1)).append(replacement);
            last = m.end(1);
        }
        if (out == null) {
            return text;
        }
        return out.append(text, last, text.length()).toString();
    }
}
