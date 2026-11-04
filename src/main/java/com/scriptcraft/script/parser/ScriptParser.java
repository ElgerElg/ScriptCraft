package com.scriptcraft.script.parser;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.hud.HudEntry;
import com.scriptcraft.script.ScriptSource;
import com.scriptcraft.script.parser.Tokenizer.Token;
import com.scriptcraft.script.runtime.Action;
import com.scriptcraft.script.runtime.Actions;
import com.scriptcraft.script.runtime.Condition;
import com.scriptcraft.script.runtime.Expression;
import com.scriptcraft.script.runtime.HudActions;
import com.scriptcraft.script.runtime.ScriptEvent;
import com.scriptcraft.script.runtime.Target;
import com.scriptcraft.screen.Anchor;
import com.scriptcraft.screen.BarDirection;
import com.scriptcraft.screen.BarElement;
import com.scriptcraft.screen.ButtonElement;
import com.scriptcraft.screen.ImageElement;
import com.scriptcraft.screen.Layout;
import com.scriptcraft.screen.ScreenElement;
import com.scriptcraft.screen.SlotElement;
import com.scriptcraft.screen.TextElement;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
 * define block ruby_block:          (id: scriptcraft:ruby_block)
 *     hardness: 3
 *
 * namespace example                  (все следующие define в файле — в example:)
 * define block ruby_block:          (id: example:ruby_block)
 * define item othermod:gem:         (полный id указан явно)
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
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,64}");
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]{1,64}");
    /** Пространства имён, которые скрипты занимать не могут. */
    private static final java.util.Set<String> RESERVED_NAMESPACES = java.util.Set.of("minecraft", "forge", "neoforge", "c");
    private static final Pattern COMMAND_HEADER = Pattern.compile("command\\s+/([a-z0-9_]{1,32})(?:\\s+permission\\s+([0-4]))?");
    /** Корневые команды мода — скрипты не могут их перекрыть. */
    private static final java.util.Set<String> RESERVED_COMMANDS = java.util.Set.of("scriptcraft", "sc", "script");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern SIZE = Pattern.compile("(\\d{1,4})x(\\d{1,4})");

    /** Строка после препроцессинга. */
    private record Line(int number, int indent, String text) {
    }

    private final Map<ScriptEvent, List<EventHandler>> events = new EnumMap<>(ScriptEvent.class);
    private final Map<String, CommandDefinition> commands = new LinkedHashMap<>();
    private final Map<String, BlockDefinition> blocks = new LinkedHashMap<>();
    private final Map<String, ItemDefinition> items = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();
    private String currentFile = "?";
    /** Текущее пространство имён для define (сбрасывается на scriptcraft в начале каждого файла). */
    private String currentNamespace = ScriptCraft.MODID;

    public ParseResult parse(List<ScriptSource> sources) {
        for (ScriptSource source : sources) {
            currentFile = source.fileName();
            currentNamespace = ScriptCraft.MODID;
            parseFile(preprocess(source.lines()));
        }
        return new ParseResult(events, commands, List.copyOf(blocks.values()), List.copyOf(items.values()), List.copyOf(errors));
    }

    /**
     * Разбор ОДНОЙ строки-эффекта для эффект-команд в чате (?heal player).
     * Блоки (if, open screen с элементами) здесь не поддерживаются.
     */
    public static Action parseEffectLine(String text) throws ParseException {
        String stripped = text.strip();
        if (stripped.isEmpty()) {
            throw new ParseException("пустая эффект-команда");
        }
        if (stripped.endsWith(":")) {
            throw new ParseException("блоки (if/open screen) в эффект-командах не поддерживаются");
        }
        return new ScriptParser().parseAction(new Line(1, 0, stripped), List.of());
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
            String lowerText = line.text().toLowerCase(Locale.ROOT);
            if (line.indent() == 0 && (lowerText.startsWith("namespace ") || lowerText.startsWith("modid "))) {
                // "namespace example", "namespace example:" или "modid example:" — двоеточие не обязательно.
                String ns = lowerText.substring(lowerText.indexOf(' ') + 1).strip();
                if (ns.endsWith(":")) {
                    ns = ns.substring(0, ns.length() - 1).strip();
                }
                String problem = namespaceProblem(ns);
                if (problem != null) {
                    error(line, problem);
                } else {
                    currentNamespace = ns;
                }
                if (end > i + 1) {
                    error(lines.get(i + 1), "у 'namespace' не бывает вложенных строк: пишите define без отступа ниже");
                }
                i = end;
                continue;
            }
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
            // "on join:", "on load:", несколько событий — "on join or load:" (одно тело на все).
            List<ScriptEvent> list = new ArrayList<>();
            for (String part : lower.substring(3).split("\\s+or\\s+")) {
                ScriptEvent event = ScriptEvent.byName(part);
                if (event == null) {
                    error(headerLine, "неизвестное событие: " + part.strip() + " (join, quit, load, unload)");
                    return;
                }
                if (list.contains(event)) {
                    error(headerLine, "событие '" + event.scriptName() + "' указано дважды");
                    return;
                }
                list.add(event);
            }
            // load/unload вместе с событием игрока (join/quit) выполняются для КАЖДОГО игрока онлайн:
            // "on join or load:" = при входе игрока + сразу всем, кто уже на сервере, когда скрипт загрузился.
            boolean withPlayerEvent = list.stream().anyMatch(ScriptEvent::playerEvent);
            List<Action> actions = parseActions(body);
            for (ScriptEvent event : list) {
                events.computeIfAbsent(event, e -> new ArrayList<>())
                        .add(new EventHandler(currentFile, actions, !event.playerEvent() && withPlayerEvent));
            }
            return;
        }

        Matcher cmd = COMMAND_HEADER.matcher(lower);
        if (cmd.matches()) {
            String name = cmd.group(1);
            int permission = cmd.group(2) != null ? Integer.parseInt(cmd.group(2)) : 0;
            if (RESERVED_COMMANDS.contains(name)) {
                error(headerLine, "имя /" + name + " зарезервировано модом (scriptcraft, sc, script)");
                return;
            }
            if (commands.containsKey(name)) {
                error(headerLine, "команда /" + name + " уже объявлена в другом месте");
                return;
            }
            commands.put(name, new CommandDefinition(name, permission, parseActions(body), currentFile));
            return;
        }

        if (lower.startsWith("define block ") || lower.startsWith("define item ")) {
            boolean isBlock = lower.startsWith("define block ");
            // id приводится к нижнему регистру, как и раньше (Ruby_Block -> ruby_block).
            String raw = lower.substring(isBlock ? 13 : 12).strip();
            String ns = currentNamespace;
            String path = raw;
            int colon = raw.indexOf(':');
            if (colon >= 0) {
                ns = raw.substring(0, colon).strip();
                path = raw.substring(colon + 1).strip();
                String problem = namespaceProblem(ns);
                if (problem != null) {
                    error(headerLine, problem);
                    return;
                }
            }
            if (!ID.matcher(path).matches()) {
                error(headerLine, hasCyrillic(path)
                        ? "id '" + path + "' на кириллице невозможен: Minecraft разрешает в id только a-z, 0-9, _ . - "
                        + "(пишите транслитом, например rubinovyj_blok, а русское название задайте через name:)"
                        : "некорректный id '" + path + "' (только строчные a-z, 0-9, _ . -)");
                return;
            }
            String id = ns + ":" + path;
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

    /** null, если пространство имён допустимо, иначе текст ошибки. */
    private static String namespaceProblem(String ns) {
        if (hasCyrillic(ns)) {
            return "namespace '" + ns + "' на кириллице невозможен: только a-z, 0-9, _ . - "
                    + "(например svoymod вместо своймод)";
        }
        if (!NAMESPACE.matcher(ns).matches()) {
            return "некорректный namespace '" + ns + "' (только строчные a-z, 0-9, _ . -)";
        }
        if (RESERVED_NAMESPACES.contains(ns)) {
            return "namespace '" + ns + "' зарезервирован (minecraft, forge, neoforge, c)";
        }
        return null;
    }

    private static boolean hasCyrillic(String s) {
        return s.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.CYRILLIC);
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
            } else if (lower.startsWith("loop ") && lower.endsWith(":")) {
                // loop <цель>:  + вложенные действия, внутри loop-player / %loop-player%
                openIf = -1;
                try {
                    List<Token> t = Tokenizer.tokenize(line.text().substring(5, line.text().length() - 1));
                    Target target = TargetParser.parseWhole(t, 0, "loop all players:");
                    if (children.isEmpty()) {
                        throw new ParseException("у 'loop' нет вложенных действий");
                    }
                    actions.add(new Actions.Loop(target, parseActions(children)));
                } catch (ParseException e) {
                    error(line, e.getMessage());
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
     * <цель> is set | <цель> is not set (targeted player is set, players in radius 5 of player is not set...)
     */
    private Condition parseCondition(String text) throws ParseException {
        List<Token> t = Tokenizer.tokenize(text);
        if (t.size() < 2) {
            throw new ParseException("неполное условие, пример: if {coins::%player%} >= 10:");
        }
        // <цель> is [not] set — "targeted player is set", "other players in radius 5 of player is set".
        // Только для настоящих целей: {var}/"текст"/слово по-прежнему значения (как раньше).
        if (!t.get(0).quoted() && !t.get(0).value().startsWith("{")) {
            TargetParser.Parsed parsed = TargetParser.parse(t, 0);
            if (parsed != null && !(parsed.target() instanceof Target.Named)) {
                int k = parsed.end();
                boolean negate = word(t, k + 1).equals("not");
                int setAt = negate ? k + 2 : k + 1;
                if (word(t, k).equals("is") && word(t, setAt).equals("set") && t.size() == setAt + 1) {
                    return new Condition.TargetSet(parsed.target(), negate);
                }
                // иначе это обычное сравнение (например, if %player% is "Notch":) — разбираем как раньше
            }
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
    static Expression expression(Token token) {
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

        if (hasBlock && !head.equals("open") && !head.equals("create")) {
            throw new ParseException("двоеточие допустимо только у 'open screen', 'create hud', 'loop', 'if' и 'else'");
        }
        if (!hasBlock && !children.isEmpty()) {
            throw new ParseException("у этого действия не может быть вложенных строк");
        }

        // Сначала таблица эффектов (set gamemode of player..., heal player by 2, send title ...).
        Action effect = EffectPatterns.tryParse(t);
        if (effect != null) {
            return effect;
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
            case "send", "message" -> {
                // Правильные формы разобраны таблицей эффектов; сюда попадают только ошибки.
                throw new ParseException("нужно: send \"текст\" [to <цель>], например send \"Привет\" to all players");
            }
            case "broadcast" -> {
                return new Actions.Broadcast(quoted(t, 1));
            }
            case "reply" -> {
                return new Actions.Reply(quoted(t, 1));
            }
            case "give" -> throw new ParseException("нужно: give <цель> <предмет> [количество], например give player diamond 3");
            case "heal" -> throw new ParseException("нужно: heal <цель> [by N], например heal all players");
            case "teleport", "tp" -> throw new ParseException("нужно: teleport <цель> to x, y, z  или  teleport <цель> to <цель>");
            case "open" -> {
                // open screen "Заголовок" [to <цель>]:
                expectWord(t, 1, "screen");
                String title = quoted(t, 2);
                Target target = new Target.Self();
                if (t.size() > 3) {
                    expectWord(t, 3, "to");
                    target = TargetParser.parseWhole(t, 4, "open screen \"Меню\" to players in radius 5 of player:");
                }
                List<ScreenElement> elements = new ArrayList<>();
                for (Line child : children) {
                    try {
                        for (ParsedElement pe : parseElements(child)) {
                            elements.add(pe.element());
                        }
                    } catch (ParseException e) {
                        error(child, e.getMessage());
                    }
                }
                return new Actions.OpenScreen(target, title, elements);
            }
            case "create" -> {
                // create hud "имя" [for|to <цель>]:  + элементы (как у open screen, кроме кнопок)
                expectWord(t, 1, "hud");
                String group = EffectPatterns.hudName(quoted(t, 2));
                Target target = new Target.Self();
                if (t.size() > 3) {
                    String w = word(t, 3);
                    if (!w.equals("for") && !w.equals("to")) {
                        throw new ParseException("ожидается 'for <цель>', например create hud \"stats\" for player:");
                    }
                    target = TargetParser.parseWhole(t, 4, "create hud \"stats\" for all players:");
                }
                if (!hasBlock || children.isEmpty()) {
                    throw new ParseException("у 'create hud' нет элементов: допишите двоеточие и элементы с отступом");
                }
                List<HudEntry> entries = new ArrayList<>();
                Set<String> ids = new HashSet<>();
                int auto = 0;
                for (Line child : children) {
                    try {
                        for (ParsedElement pe : parseElements(child)) {
                            if (pe.element() instanceof ButtonElement) {
                                throw new ParseException("кнопки в HUD не поддерживаются (HUD не принимает клики) — используйте open screen");
                            }
                            String id = pe.id();
                            if (id.isEmpty()) {
                                // Безымянный элемент: обновлять его нельзя, но скрыть/удалить можно вместе с группой.
                                do {
                                    id = group + "#" + (++auto);
                                } while (ids.contains(id));
                            }
                            if (!ids.add(id)) {
                                throw new ParseException("элемент с именем '" + id + "' уже есть в этом HUD");
                            }
                            entries.add(new HudEntry(id, group, true, pe.element()));
                        }
                    } catch (ParseException e) {
                        error(child, e.getMessage());
                    }
                }
                if (entries.size() > com.scriptcraft.hud.HudManager.MAX_ELEMENTS) {
                    throw new ParseException("слишком много элементов в одном HUD (максимум "
                            + com.scriptcraft.hud.HudManager.MAX_ELEMENTS + ")");
                }
                return new HudActions.CreateHud(target, group, entries);
            }
            default -> throw new ParseException("неизвестное действие: " + head);
        }
    }

    // ------------------------------------------------------------------ элементы экрана и HUD

    /** Разобранный элемент + имя для HUD ("" = без имени). */
    private record ParsedElement(String id, ScreenElement element) {
    }

    /** Какие параметры допустимы у каждого вида элемента (лишний параметр — понятная ошибка, а не тихое игнорирование). */
    private static final Map<String, Set<String>> ELEMENT_OPTIONS = Map.of(
            "text", Set.of("at", "anchor", "color", "shadow", "layer", "id"),
            "button", Set.of("at", "size", "anchor", "run", "close", "layer", "id"),
            "image", Set.of("at", "size", "texture", "anchor", "color", "layer", "id"),
            "slot", Set.of("at", "size", "anchor", "color", "no", "layer", "id"),
            "bar", Set.of("at", "size", "texture", "anchor", "color", "layer", "id", "value", "max", "direction",
                    "background", "fill", "text", "shadow"),
            "hotbar", Set.of("at", "size", "anchor", "color", "no", "layer", "id", "spacing"));

    /**
     * Элемент экрана или HUD (одна строка; "hotbar" раскладывается в 9 слотов):
     * text "Текст" [at X Y] [anchor A] [color #RRGGBB] [shadow]
     * button "Надпись" [at X Y] [size WxH] [anchor A] (run "/команда" | close)
     * slot <предмет> [количество] | slot hotbar N | slot image "картинка" | slot text "Текст"  [at X Y] [size WxH] [no frame]
     * image "ns:textures/....png" [at X Y] [size WxH] [texture WxH] [anchor A] [color #RRGGBB]
     * bar ["имя"] [value V] [max V] [direction D] [size WxH] [color #заполнение] [background #цвет|"картинка"|none]
     *     [fill "картинка"] [texture WxH] [text "надпись"] [shadow]
     * hotbar [at X Y] [anchor A] [size WxH] [spacing N] [color #выделение] [no frame]
     * У всех: [layer N] — слой (больше = выше), [id имя] — имя в HUD.
     */
    private List<ParsedElement> parseElements(Line line) throws ParseException {
        List<Token> t = Tokenizer.tokenize(line.text());
        String kind = word(t, 0);
        int idx;
        String main = "";
        String id = "";
        int count = 1;
        SlotElement.Content slotContent = SlotElement.Content.ITEM;
        switch (kind) {
            case "text", "button", "image" -> {
                main = quoted(t, 1);
                idx = 2;
            }
            case "slot" -> {
                String what = word(t, 1);
                idx = 2;
                if (what.equals("hotbar") && t.size() > 2 && !t.get(2).quoted() && t.get(2).value().matches("\\d+")) {
                    // slot hotbar 3 — настоящий предмет из 3-го слота хотбара игрока
                    slotContent = SlotElement.Content.HOTBAR;
                    count = parseInt(t.get(2).value());
                    if (count < 1 || count > 9) {
                        throw new ParseException("номер слота хотбара — от 1 до 9");
                    }
                    idx = 3;
                } else if ((what.equals("image") || what.equals("text")) && t.size() > 2 && t.get(2).quoted()) {
                    slotContent = what.equals("image") ? SlotElement.Content.IMAGE : SlotElement.Content.TEXT;
                    main = t.get(2).value();
                    idx = 3;
                } else {
                    main = what;
                    if (main.isEmpty()) {
                        throw new ParseException("не указан предмет слота");
                    }
                    if (t.size() > 2 && !t.get(2).quoted() && t.get(2).value().matches("\\d+")) {
                        count = parseInt(t.get(2).value());
                        idx = 3;
                    }
                }
            }
            case "bar", "progress" -> {
                kind = "bar";
                idx = 1;
                if (t.size() > 1 && t.get(1).quoted()) {
                    id = EffectPatterns.hudName(t.get(1).value());
                    idx = 2;
                }
            }
            case "hotbar" -> idx = 1;
            default -> throw new ParseException("неизвестный элемент: " + kind + " (text, button, image, slot, bar, hotbar)");
        }

        int x = 0, y = 0, w = 0, h = 0, texW = 0, texH = 0, layer = 0, spacing = -1;
        int color = 0xFFFFFFFF;
        boolean colorSet = false, atSet = false, anchorSet = false;
        Anchor anchor = Anchor.CENTER;
        boolean shadow = false, frame = true;
        String action = "close";
        // Полоса
        String value = "0", max = null, fillTexture = "", backgroundTexture = "", barText = "";
        BarDirection direction = BarDirection.LEFT_TO_RIGHT;
        int backgroundColor = 0x80000000;

        Set<String> allowed = ELEMENT_OPTIONS.get(kind);
        while (idx < t.size()) {
            String opt = word(t, idx);
            if (!allowed.contains(opt)) {
                throw new ParseException(opt.isEmpty() ? "лишний текст в кавычках: \"" + t.get(idx).value() + "\""
                        : "у элемента '" + kind + "' нет параметра '" + opt + "'");
            }
            switch (opt) {
                case "at" -> {
                    if (idx + 2 >= t.size()) throw new ParseException("нужно: at X Y");
                    x = parseInt(t.get(idx + 1).value());
                    y = parseInt(t.get(idx + 2).value());
                    atSet = true;
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
                    anchorSet = true;
                    idx += 2;
                }
                case "color" -> {
                    color = parseColor(idx + 1 < t.size() ? t.get(idx + 1).value() : "");
                    colorSet = true;
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
                case "layer" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: layer N (целое число, больше = выше)");
                    layer = parseInt(t.get(idx + 1).value());
                    idx += 2;
                }
                case "id" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: id имя");
                    id = EffectPatterns.hudName(t.get(idx + 1).value());
                    idx += 2;
                }
                case "no" -> {
                    expectWord(t, idx + 1, "frame");
                    frame = false;
                    idx += 2;
                }
                case "spacing" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: spacing N");
                    spacing = parseInt(t.get(idx + 1).value());
                    idx += 2;
                }
                case "value", "max" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: " + opt + " <число | {переменная} | health | food ...>");
                    String v = barValueRaw(t.get(idx + 1));
                    if (opt.equals("value")) {
                        value = v;
                    } else {
                        max = v;
                    }
                    idx += 2;
                }
                case "direction" -> {
                    BarDirection d = BarDirection.byName(word(t, idx + 1));
                    if (d == null) {
                        throw new ParseException("направление: left_to_right, right_to_left, bottom_to_top, top_to_bottom");
                    }
                    direction = d;
                    idx += 2;
                }
                case "background" -> {
                    // background #80000000 | background "ns:textures/gui/bar_bg.png" | background none
                    if (idx + 1 < t.size() && t.get(idx + 1).quoted()) {
                        backgroundTexture = t.get(idx + 1).value();
                    } else if (word(t, idx + 1).equals("none")) {
                        backgroundColor = 0;
                        backgroundTexture = "";
                    } else {
                        backgroundColor = parseColor(idx + 1 < t.size() ? t.get(idx + 1).value() : "");
                    }
                    idx += 2;
                }
                case "fill" -> {
                    // fill "ns:textures/gui/bar_fill.png" | fill #RRGGBB (то же, что color)
                    if (idx + 1 < t.size() && t.get(idx + 1).quoted()) {
                        fillTexture = t.get(idx + 1).value();
                    } else {
                        color = parseColor(idx + 1 < t.size() ? t.get(idx + 1).value() : "");
                        colorSet = true;
                    }
                    idx += 2;
                }
                case "text" -> {
                    barText = quoted(t, idx + 1);
                    idx += 2;
                }
                default -> throw new ParseException("неизвестный параметр элемента: " + opt);
            }
        }

        if (kind.equals("hotbar")) {
            return expandHotbar(id, x, y, atSet, anchor, anchorSet, w, h, spacing, color, frame, layer);
        }
        if (kind.equals("bar") && !colorSet) {
            color = 0xFF4CAF50;
        }
        Layout layout = new Layout(x, y, w, h, anchor, color, layer);
        ScreenElement element = switch (kind) {
            case "text" -> new TextElement(layout, main, shadow);
            case "button" -> new ButtonElement(layout, main, action);
            case "slot" -> new SlotElement(layout, slotContent, main, count, frame);
            case "bar" -> new BarElement(layout, id, value, max != null ? max : defaultMax(value), direction,
                    backgroundColor, fillTexture, backgroundTexture, texW, texH, barText, shadow);
            default -> new ImageElement(layout, main, texW, texH);
        };
        return List.of(new ParsedElement(id, element));
    }

    /** Значение полосы в описании: число, {переменная}, плейсхолдер или источник клиента. */
    private static String barValueRaw(Token token) throws ParseException {
        String v = token.value().strip();
        if (token.quoted() || NUMBER.matcher(v).matches() || v.startsWith("{") || v.contains("%")
                || BarElement.isSource(v)) {
            return BarElement.isSource(v) ? v.toLowerCase(Locale.ROOT) : v;
        }
        throw new ParseException("значение полосы: число, {переменная} или " + String.join(", ", BarElement.SOURCES)
                + ", а не '" + v + "'");
    }

    /** Максимум по умолчанию подбирается под источник: health -> max_health, food -> 20, air -> max_air... */
    private static String defaultMax(String value) {
        return switch (value) {
            case "health" -> "max_health";
            case "air" -> "max_air";
            case "xp" -> "1";
            case "food", "saturation", "armor", "absorption" -> "20";
            default -> "100";
        };
    }

    /**
     * hotbar -> 9 слотов "slot hotbar 1..9" в ряд. По умолчанию — внизу по центру, как ванильный хотбар.
     * spacing — расстояние между началами слотов (по умолчанию размер слота + 2).
     */
    private static List<ParsedElement> expandHotbar(String id, int x, int y, boolean atSet, Anchor anchor, boolean anchorSet,
                                                    int w, int h, int spacing, int color, boolean frame, int layer) {
        if (!anchorSet) {
            anchor = Anchor.BOTTOM;
        }
        if (!atSet && anchor == Anchor.BOTTOM) {
            y = -3;
        }
        int size = w > 0 ? w : SlotElement.SIZE;
        int step = spacing > 0 ? spacing : size + 2;
        int total = step * 8 + size;
        List<ParsedElement> slots = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            // Сдвиг i-го слота относительно точки привязки ряда целиком.
            int offset = switch (anchor.horizontal()) {
                case 0 -> i * step;
                case 1 -> i * step - (total - size) / 2;
                default -> -(8 - i) * step;
            };
            Layout layout = new Layout(x + offset, y, w, h, anchor, color, layer);
            slots.add(new ParsedElement(id.isEmpty() ? "" : id + (i + 1),
                    new SlotElement(layout, SlotElement.Content.HOTBAR, "", i + 1, frame)));
        }
        return slots;
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
