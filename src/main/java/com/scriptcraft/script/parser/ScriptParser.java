package com.scriptcraft.script.parser;

import com.scriptcraft.script.ScriptSource;
import com.scriptcraft.script.parser.Tokenizer.Token;
import com.scriptcraft.script.runtime.Action;
import com.scriptcraft.script.runtime.Actions;
import com.scriptcraft.script.runtime.Condition;
import com.scriptcraft.script.runtime.Expression;
import com.scriptcraft.script.runtime.ScriptEvent;
import com.scriptcraft.screen.Anchor;
import com.scriptcraft.screen.ButtonElement;
import com.scriptcraft.screen.ImageElement;
import com.scriptcraft.screen.Layout;
import com.scriptcraft.screen.ScreenElement;
import com.scriptcraft.screen.SlotElement;
import com.scriptcraft.screen.TextElement;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Построчный парсер Skript-подобного языка.
 *
 * Структура: блоки верхнего уровня заканчиваются двоеточием, их содержимое — с отступом (пробелы или табы).
 * <pre>
 * on join:
 *     send "Привет, %player%!" to player
 *
 * command /hello:
 *     send "Hello!" to player
 *
 * define block ruby_block:
 *     hardness: 3
 *
 * command /coins:
 *     add 1 to {coins::%player%}
 *     if {coins::%player%} >= 10:
 *         send "Богач!" to player
 *     else:
 *         send "Монет: {coins::%player%}" to player
 * </pre>
 * Ошибка в одной строке не ломает остальные: строка пропускается, ошибка попадает в список.
 * Парсер не создаёт никаких объектов Minecraft (кроме описаний), поэтому безопасно работает при запуске мода.
 */
public final class ScriptParser {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,64}");
    private static final Pattern COMMAND_HEADER = Pattern.compile("command\\s+/([a-z0-9_]{1,32})(?:\\s+permission\\s+([0-4]))?");
    /** Корневые команды мода — скрипты не могут их перекрыть. */
    private static final java.util.Set<String> RESERVED_COMMANDS = java.util.Set.of("scriptcraft", "sk", "script");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern SIZE = Pattern.compile("(\\d{1,4})x(\\d{1,4})");

    /** Строка после препроцессинга. */
    private record Line(int number, int indent, String text) {
    }

    private final Map<ScriptEvent, List<List<Action>>> events = new EnumMap<>(ScriptEvent.class);
    private final Map<String, CommandDefinition> commands = new LinkedHashMap<>();
    private final Map<String, BlockDefinition> blocks = new LinkedHashMap<>();
    private final Map<String, ItemDefinition> items = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();
    private String currentFile = "?";

    public ParseResult parse(List<ScriptSource> sources) {
        for (ScriptSource source : sources) {
            currentFile = source.fileName();
            parseFile(preprocess(source.lines()));
        }
        return new ParseResult(events, commands, List.copyOf(blocks.values()), List.copyOf(items.values()), List.copyOf(errors));
    }

    // ------------------------------------------------------------------ структура файла

    private List<Line> preprocess(List<String> raw) {
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            String s = raw.get(i);
            if (i == 0 && s.startsWith("\uFEFF")) {
                s = s.substring(1); // BOM от Блокнота Windows
            }
            String stripped = s.strip();
            if (stripped.isEmpty() || stripped.startsWith("#")) {
                continue; // пустые строки и комментарии
            }
            int indent = 0;
            for (int j = 0; j < s.length(); j++) {
                char c = s.charAt(j);
                if (c == ' ') {
                    indent++;
                } else if (c == '\t') {
                    indent += 4;
                } else {
                    break;
                }
            }
            lines.add(new Line(i + 1, indent, stripped));
        }
        return lines;
    }

    /** Индекс первой строки после блока, начинающегося в {@code start} (у которой отступ <= отступа заголовка). */
    private static int blockEnd(List<Line> lines, int start) {
        int indent = lines.get(start).indent();
        int i = start + 1;
        while (i < lines.size() && lines.get(i).indent() > indent) {
            i++;
        }
        return i;
    }

    private void parseFile(List<Line> lines) {
        int i = 0;
        while (i < lines.size()) {
            Line line = lines.get(i);
            int end = blockEnd(lines, i);
            if (line.indent() != 0) {
                error(line, "неожиданный отступ");
            } else if (!line.text().endsWith(":")) {
                error(line, "ожидается заголовок блока с двоеточием, например 'on join:'");
            } else {
                String header = line.text().substring(0, line.text().length() - 1).strip();
                parseTopLevel(line, header, lines.subList(i + 1, end));
            }
            i = end;
        }
    }

    private void parseTopLevel(Line headerLine, String header, List<Line> body) {
        String lower = header.toLowerCase(Locale.ROOT);

        if (lower.startsWith("on ")) {
            ScriptEvent event = ScriptEvent.byName(lower.substring(3).strip());
            if (event == null) {
                error(headerLine, "неизвестное событие: " + header.substring(3).strip());
                return;
            }
            events.computeIfAbsent(event, e -> new ArrayList<>()).add(parseActions(body));
            return;
        }

        Matcher cmd = COMMAND_HEADER.matcher(lower);
        if (cmd.matches()) {
            String name = cmd.group(1);
            int permission = cmd.group(2) != null ? Integer.parseInt(cmd.group(2)) : 0;
            if (RESERVED_COMMANDS.contains(name)) {
                error(headerLine, "имя /" + name + " зарезервировано модом (scriptcraft, sk, script)");
                return;
            }
            if (commands.containsKey(name)) {
                error(headerLine, "команда /" + name + " уже объявлена в другом месте");
                return;
            }
            commands.put(name, new CommandDefinition(name, permission, parseActions(body)));
            return;
        }

        if (lower.startsWith("define block ") || lower.startsWith("define item ")) {
            boolean isBlock = lower.startsWith("define block ");
            String id = lower.substring(isBlock ? 13 : 12).strip();
            if (!ID.matcher(id).matches()) {
                error(headerLine, "некорректный id '" + id + "' (только a-z, 0-9 и _)");
                return;
            }
            if (blocks.containsKey(id) || items.containsKey(id)) {
                error(headerLine, "id '" + id + "' уже объявлен");
                return;
            }
            Map<String, String> props = parseProperties(body);
            try {
                if (isBlock) {
                    blocks.put(id, new BlockDefinition(id, props.get("name"),
                            floatProp(props, "hardness", 1.5f), floatProp(props, "resistance", 6f),
                            props.getOrDefault("sound", "stone").toLowerCase(Locale.ROOT),
                            (int) floatProp(props, "light", 0), boolProp(props, "requires_tool")));
                } else {
                    items.put(id, new ItemDefinition(id, props.get("name"),
                            (int) floatProp(props, "stack_size", 64), boolProp(props, "fire_resistant"),
                            props.getOrDefault("rarity", "common").toLowerCase(Locale.ROOT)));
                }
            } catch (NumberFormatException e) {
                error(headerLine, "неверное число в свойствах: " + e.getMessage());
            }
            return;
        }

        error(headerLine, "неизвестный блок: " + header);
    }

    // ------------------------------------------------------------------ свойства define

    /** Разбирает строки вида "ключ: значение" (значение может быть в кавычках). */
    private Map<String, String> parseProperties(List<Line> body) {
        Map<String, String> props = new LinkedHashMap<>();
        for (Line line : body) {
            int colon = line.text().indexOf(':');
            if (colon <= 0) {
                error(line, "ожидается 'ключ: значение'");
                continue;
            }
            String key = line.text().substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String value = line.text().substring(colon + 1).strip();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            props.put(key, value);
        }
        return props;
    }

    private static float floatProp(Map<String, String> props, String key, float def) {
        String v = props.get(key);
        return v == null ? def : Float.parseFloat(v);
    }

    private static boolean boolProp(Map<String, String> props, String key) {
        return "true".equalsIgnoreCase(props.get(key));
    }

    // ------------------------------------------------------------------ действия

    private List<Action> parseActions(List<Line> body) {
        List<Action> actions = new ArrayList<>();
        if (body.isEmpty()) {
            return actions;
        }
        int baseIndent = body.get(0).indent();
        // Индекс последнего "if" без "else" в списке actions (-1, если предыдущая строка — не if).
        int openIf = -1;
        int i = 0;
        while (i < body.size()) {
            Line line = body.get(i);
            int end = blockEnd(body, i);
            List<Line> children = body.subList(i + 1, end);
            String lower = line.text().toLowerCase(Locale.ROOT);
            if (line.indent() != baseIndent) {
                error(line, "неверный отступ");
                openIf = -1;
            } else if (lower.startsWith("if ") && lower.endsWith(":")) {
                // if <условие>:  + вложенные действия
                try {
                    Condition condition = parseCondition(line.text().substring(3, line.text().length() - 1));
                    actions.add(new Actions.If(condition, parseActions(children), List.of()));
                    openIf = actions.size() - 1;
                } catch (ParseException e) {
                    error(line, e.getMessage());
                    openIf = -1;
                }
            } else if (lower.equals("else:")) {
                // else:  — относится к if прямо над ним
                if (openIf < 0) {
                    error(line, "'else' без 'if' прямо над ним");
                } else {
                    Actions.If prev = (Actions.If) actions.get(openIf);
                    actions.set(openIf, new Actions.If(prev.condition(), prev.then(), parseActions(children)));
                }
                openIf = -1;
            } else {
                openIf = -1;
                try {
                    actions.add(parseAction(line, children));
                } catch (ParseException e) {
                    error(line, e.getMessage());
                }
            }
            i = end;
        }
        return actions;
    }

    /**
     * Условие (текст между "if" и двоеточием):
     * {a} is X | {a} is not X | {a} > X | {a} < X | {a} >= X | {a} <= X | {a} is set | {a} is not set
     */
    private Condition parseCondition(String text) throws ParseException {
        List<Token> t = Tokenizer.tokenize(text);
        if (t.size() < 2) {
            throw new ParseException("неполное условие, пример: if {coins::%player%} >= 10:");
        }
        Expression left = expression(t.get(0));
        String op = word(t, 1);
        if (op.equals("is")) {
            boolean negate = word(t, 2).equals("not");
            int next = negate ? 3 : 2;
            if (word(t, next).equals("set") && t.size() == next + 1) {
                return new Condition.IsSet(left, negate);
            }
            if (t.size() != next + 1) {
                throw new ParseException("после 'is' ожидается одно значение (текст бери в кавычки)");
            }
            return new Condition.Compare(left, negate ? "is not" : "is", expression(t.get(next)));
        }
        if (op.equals(">") || op.equals("<") || op.equals(">=") || op.equals("<=")) {
            if (t.size() != 3) {
                throw new ParseException("ожидается: if {a} " + op + " число:");
            }
            return new Condition.Compare(left, op, expression(t.get(2)));
        }
        if (op.equals("=") || op.equals("==") || op.equals("!=")) {
            if (t.size() != 3) {
                throw new ParseException("ожидается: if {a} " + op + " значение:");
            }
            return new Condition.Compare(left, op.equals("!=") ? "is not" : "is", expression(t.get(2)));
        }
        throw new ParseException("неизвестный оператор условия: " + op + " (is, is not, >, <, >=, <=, is set)");
    }

    /** Токен -> значение: "текст", {переменная}, число, true/false, иначе слово как текст. */
    private static Expression expression(Token token) {
        String v = token.value();
        if (token.quoted()) {
            return new Expression.Text(v);
        }
        if (v.length() > 2 && v.startsWith("{") && v.endsWith("}")) {
            return new Expression.Variable(v.substring(1, v.length() - 1));
        }
        if (NUMBER.matcher(v).matches()) {
            return new Expression.Literal(Double.parseDouble(v));
        }
        if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false")) {
            return new Expression.Literal(Boolean.parseBoolean(v.toLowerCase(Locale.ROOT)));
        }
        return new Expression.Text(v);
    }

    /** Токен {имя} -> "имя" (без скобок, регистр сохраняется). */
    private static String variableName(List<Token> t, int i) throws ParseException {
        if (i < t.size() && !t.get(i).quoted()) {
            String v = t.get(i).value();
            if (v.length() > 2 && v.startsWith("{") && v.endsWith("}")) {
                return v.substring(1, v.length() - 1);
            }
        }
        throw new ParseException("ожидается переменная в фигурных скобках, например {coins::%player%}");
    }

    private Action parseAction(Line line, List<Line> children) throws ParseException {
        String text = line.text();
        boolean hasBlock = text.endsWith(":");
        if (hasBlock) {
            text = text.substring(0, text.length() - 1);
        }
        List<Token> t = Tokenizer.tokenize(text);
        if (t.isEmpty()) {
            throw new ParseException("пустое действие");
        }
        String head = word(t, 0);

        if (hasBlock && !head.equals("open")) {
            throw new ParseException("двоеточие допустимо только у 'open screen', 'if' и 'else'");
        }
        if (!hasBlock && !children.isEmpty()) {
            throw new ParseException("у этого действия не может быть вложенных строк");
        }

        switch (head) {
            case "set" -> {
                // set {name} to <значение>
                String name = variableName(t, 1);
                expectWord(t, 2, "to");
                if (t.size() != 4) {
                    throw new ParseException("нужно: set {имя} to <значение> (текст с пробелами — в кавычках)");
                }
                return new Actions.SetVariable(name, expression(t.get(3)));
            }
            case "add", "remove" -> {
                // add <n> to {name} / remove <n> from {name}
                boolean remove = head.equals("remove");
                if (t.size() != 4) {
                    throw new ParseException(remove ? "нужно: remove <число> from {имя}" : "нужно: add <число> to {имя}");
                }
                expectWord(t, 2, remove ? "from" : "to");
                return new Actions.AddToVariable(variableName(t, 3), expression(t.get(1)), remove);
            }
            case "delete", "clear" -> {
                // delete {name}
                return new Actions.DeleteVariable(variableName(t, 1));
            }
            case "send" -> {
                // send "текст" [to player]
                return new Actions.SendMessage(quoted(t, 1));
            }
            case "broadcast" -> {
                return new Actions.Broadcast(quoted(t, 1));
            }
            case "reply" -> {
                return new Actions.Reply(quoted(t, 1));
            }
            case "give" -> {
                // give player <предмет> [количество]
                expectWord(t, 1, "player");
                String item = word(t, 2);
                if (item.isEmpty()) {
                    throw new ParseException("не указан предмет: give player diamond 1");
                }
                int count = t.size() > 3 ? parseInt(t.get(3).value()) : 1;
                return new Actions.GiveItem(item, count);
            }
            case "heal" -> {
                expectWord(t, 1, "player");
                return new Actions.HealPlayer();
            }
            case "teleport" -> {
                // teleport player to x, y, z
                expectWord(t, 1, "player");
                expectWord(t, 2, "to");
                if (t.size() < 6) {
                    throw new ParseException("нужно: teleport player to x, y, z");
                }
                return new Actions.Teleport(parseDouble(t.get(3).value()), parseDouble(t.get(4).value()), parseDouble(t.get(5).value()));
            }
            case "open" -> {
                // open screen "Заголовок" [to player]:
                expectWord(t, 1, "screen");
                String title = quoted(t, 2);
                List<ScreenElement> elements = new ArrayList<>();
                for (Line child : children) {
                    try {
                        elements.add(parseElement(child));
                    } catch (ParseException e) {
                        error(child, e.getMessage());
                    }
                }
                return new Actions.OpenScreen(title, elements);
            }
            default -> throw new ParseException("неизвестное действие: " + head);
        }
    }

    // ------------------------------------------------------------------ элементы экрана

    /**
     * text "Текст" [at X Y] [anchor A] [color #RRGGBB] [shadow]
     * button "Надпись" [at X Y] [size WxH] [anchor A] (run "/команда" | close)
     * slot <предмет> [количество] [at X Y] [anchor A]
     * image "ns:textures/....png" [at X Y] [size WxH] [texture WxH] [anchor A] [color #RRGGBB]
     */
    private ScreenElement parseElement(Line line) throws ParseException {
        List<Token> t = Tokenizer.tokenize(line.text());
        String kind = word(t, 0);
        int idx;
        String main;
        int count = 1;
        switch (kind) {
            case "text", "button", "image" -> {
                main = quoted(t, 1);
                idx = 2;
            }
            case "slot" -> {
                main = word(t, 1);
                if (main.isEmpty()) {
                    throw new ParseException("не указан предмет слота");
                }
                idx = 2;
                if (t.size() > 2 && !t.get(2).quoted() && t.get(2).value().matches("\\d+")) {
                    count = parseInt(t.get(2).value());
                    idx = 3;
                }
            }
            default -> throw new ParseException("неизвестный элемент экрана: " + kind);
        }

        int x = 0, y = 0, w = 0, h = 0, texW = 0, texH = 0;
        int color = 0xFFFFFFFF;
        Anchor anchor = Anchor.CENTER;
        boolean shadow = false;
        String action = "close";

        while (idx < t.size()) {
            String opt = word(t, idx);
            switch (opt) {
                case "at" -> {
                    if (idx + 2 >= t.size()) throw new ParseException("нужно: at X Y");
                    x = parseInt(t.get(idx + 1).value());
                    y = parseInt(t.get(idx + 2).value());
                    idx += 3;
                }
                case "size", "texture" -> {
                    Matcher m = SIZE.matcher(idx + 1 < t.size() ? t.get(idx + 1).value() : "");
                    if (!m.matches()) throw new ParseException("нужно: " + opt + " ШИРИНАxВЫСОТА, например 100x20");
                    if (opt.equals("size")) {
                        w = Integer.parseInt(m.group(1));
                        h = Integer.parseInt(m.group(2));
                    } else {
                        texW = Integer.parseInt(m.group(1));
                        texH = Integer.parseInt(m.group(2));
                    }
                    idx += 2;
                }
                case "anchor" -> {
                    Anchor a = Anchor.byName(idx + 1 < t.size() ? t.get(idx + 1).value() : "");
                    if (a == null) throw new ParseException("неизвестный anchor (top_left, top, center, bottom_right...)");
                    anchor = a;
                    idx += 2;
                }
                case "color" -> {
                    color = parseColor(idx + 1 < t.size() ? t.get(idx + 1).value() : "");
                    idx += 2;
                }
                case "shadow" -> {
                    shadow = true;
                    idx++;
                }
                case "run" -> {
                    action = quoted(t, idx + 1);
                    if (!action.startsWith("/")) action = "/" + action;
                    idx += 2;
                }
                case "close" -> {
                    action = "close";
                    idx++;
                }
                default -> throw new ParseException("неизвестный параметр элемента: " + opt);
            }
        }

        Layout layout = new Layout(x, y, w, h, anchor, color);
        return switch (kind) {
            case "text" -> new TextElement(layout, main, shadow);
            case "button" -> new ButtonElement(layout, main, action);
            case "slot" -> new SlotElement(layout, main, count);
            default -> new ImageElement(layout, main, texW, texH);
        };
    }

    // ------------------------------------------------------------------ helpers

    private static String word(List<Token> t, int i) {
        return i < t.size() && !t.get(i).quoted() ? t.get(i).value().toLowerCase(Locale.ROOT) : "";
    }

    private static String quoted(List<Token> t, int i) throws ParseException {
        if (i >= t.size() || !t.get(i).quoted()) {
            throw new ParseException("ожидается текст в кавычках");
        }
        return t.get(i).value();
    }

    private static void expectWord(List<Token> t, int i, String expected) throws ParseException {
        if (!word(t, i).equals(expected)) {
            throw new ParseException("ожидается '" + expected + "'");
        }
    }

    private static int parseInt(String s) throws ParseException {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new ParseException("ожидается целое число, а не '" + s + "'");
        }
    }

    private static double parseDouble(String s) throws ParseException {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new ParseException("ожидается число, а не '" + s + "'");
        }
    }

    /** #RRGGBB или #AARRGGBB -> ARGB. */
    private static int parseColor(String s) throws ParseException {
        if (!s.matches("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})")) {
            throw new ParseException("цвет должен быть #RRGGBB или #AARRGGBB");
        }
        long v = Long.parseLong(s.substring(1), 16);
        if (s.length() == 7) {
            v |= 0xFF000000L;
        }
        return (int) v;
    }

    private void error(Line line, String message) {
        errors.add(currentFile + ":" + line.number() + ": " + message);
    }
}
