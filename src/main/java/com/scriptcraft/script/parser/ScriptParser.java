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
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;
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
    private static final Pattern COMMAND_HEADER = Pattern.compile("command\\s+/([a-z0-9_]{1,32})(.*)");
    private static final Pattern COMMAND_ARG = Pattern.compile("<([^>]+)>|\\[([^\\]]+)]");
    private static final Pattern LEGACY_PERMISSION = Pattern.compile("\\s*permission\\s+([0-4])\\s*");
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
    private final List<PeriodHandler> periods = new ArrayList<>();
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
        return new ParseResult(events, commands, List.copyOf(blocks.values()), List.copyOf(items.values()),
                List.copyOf(periods), List.copyOf(errors));
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

        if (lower.startsWith("every ")) {
            try {
                int ticks = parsePeriod(header.substring(6).strip());
                if (body.isEmpty()) {
                    error(headerLine, "у 'every' нет вложенных действий");
                } else {
                    periods.add(new PeriodHandler(currentFile, ticks, parseActions(body)));
                }
            } catch (ParseException e) {
                error(headerLine, e.getMessage());
            }
            return;
        }

        if (lower.startsWith("on ")) {
            // "on join:", "on load:", несколько событий — "on join or load:" (одно тело на все).
            List<ScriptEvent> list = new ArrayList<>();
            for (String part : lower.substring(3).split("\\s+or\\s+")) {
                ScriptEvent event = ScriptEvent.byName(part);
                if (event == null) {
                    error(headerLine, "неизвестное событие: " + part.strip()
                            + " (join, quit, death, respawn, chat, break, place, right click, left click, drop, pickup, damage, first join, world change, gamemode change, load, unload)");
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
            parseCommand(headerLine, cmd.group(1), cmd.group(2), body);
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
                    String facing = BlockDefinition.parseFacing(props.get("facing"));
                    if (facing == null) {
                        error(headerLine, "facing: ожидается none, horizontal или all, а не '" + props.get("facing") + "'");
                        return;
                    }
                    String render = BlockDefinition.parseRender(props.get("render"));
                    if (render == null) {
                        error(headerLine, "render: ожидается solid, cutout или translucent, а не '" + props.get("render") + "'");
                        return;
                    }
                    Boolean full = fullProp(props.get("full"));
                    if (props.containsKey("full") && full == null) {
                        error(headerLine, "full: ожидается true или false, а не '" + props.get("full") + "'");
                        return;
                    }
                    blocks.put(id, new BlockDefinition(id, props.get("name"),
                            floatProp(props, "hardness", 1.5f), floatProp(props, "resistance", 6f),
                            props.getOrDefault("sound", "stone").toLowerCase(Locale.ROOT),
                            (int) floatProp(props, "light", 0), boolProp(props, "requires_tool"), facing, render, full));
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

    /** null — свойства нет или оно непонятное (вызывающий различает это сам). */
    @Nullable
    private static Boolean fullProp(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.replace("\"", "").strip().toLowerCase(Locale.ROOT);
        if (v.equals("true") || v.equals("yes")) {
            return Boolean.TRUE;
        }
        if (v.equals("false") || v.equals("no")) {
            return Boolean.FALSE;
        }
        return null;
    }

    // ------------------------------------------------------------------ действия

    private void parseCommand(Line headerLine, String name, String rest, List<Line> body) {
        if (RESERVED_COMMANDS.contains(name)) {
            error(headerLine, "имя /" + name + " зарезервировано модом (scriptcraft, sc, script)");
            return;
        }
        if (commands.containsKey(name)) {
            error(headerLine, "команда /" + name + " уже объявлена в другом месте");
            return;
        }
        int permission = 0;
        Matcher legacy = LEGACY_PERMISSION.matcher(rest);
        if (legacy.find()) {
            permission = Integer.parseInt(legacy.group(1));
            rest = (rest.substring(0, legacy.start()) + " " + rest.substring(legacy.end())).strip();
        }
        List<ArgSpec> arguments = new ArrayList<>();
        StringBuilder unknown = new StringBuilder();
        Matcher args = COMMAND_ARG.matcher(rest);
        int cursor = 0;
        while (args.find()) {
            unknown.append(rest, cursor, args.start());
            boolean optional = args.group(2) != null;
            String typeName = optional ? args.group(2) : args.group(1);
            com.scriptcraft.script.runtime.ArgType type = com.scriptcraft.script.runtime.ArgType.parse(typeName);
            if (type == null) {
                error(headerLine, "неизвестный тип аргумента '" + typeName.strip()
                        + "' (text, player, offline player, integer, number, boolean, date, world, item)");
            } else if (!arguments.isEmpty() && arguments.get(arguments.size() - 1).optional() && !optional) {
                error(headerLine, "обязательный аргумент не может стоять после необязательного");
            } else {
                arguments.add(new ArgSpec(type, optional));
            }
            cursor = args.end();
        }
        unknown.append(rest.substring(cursor));
        if (!unknown.toString().strip().isEmpty()) {
            error(headerLine, "после имени команды ожидаются аргументы <тип> или [тип], а не '" + unknown.toString().strip() + "'");
        }
        List<String> aliases = new ArrayList<>();
        boolean players = true;
        boolean console = true;
        List<Line> actions = new ArrayList<>();
        int base = body.isEmpty() ? 0 : body.stream().mapToInt(Line::indent).min().orElse(0);
        for (Line line : body) {
            if (line.indent() == base && commandProperty(line.text())) {
                applyCommandProperty(line, name, aliases, arguments);
                if (line.text().toLowerCase(Locale.ROOT).startsWith("permission")) {
                    Integer level = permissionValue(line);
                    if (level != null) {
                        permission = level;
                    }
                }
                if (line.text().toLowerCase(Locale.ROOT).startsWith("executable")) {
                    boolean[] who = executableBy(line);
                    if (who != null) {
                        players = who[0];
                        console = who[1];
                    }
                }
            } else {
                actions.add(line);
            }
        }
        CommandDefinition def = new CommandDefinition(name, permission, List.copyOf(aliases), List.copyOf(arguments),
                players, console, parseActions(actions), currentFile);
        commands.put(name, def);
        for (String alias : aliases) {
            commands.put(alias, def);
        }
    }

    private static boolean commandProperty(String text) {
        String l = text.toLowerCase(Locale.ROOT);
        return l.startsWith("permission:") || l.startsWith("permission ")
                || l.startsWith("aliases:") || l.startsWith("alias:")
                || l.startsWith("arguments:") || l.startsWith("argument:")
                || l.startsWith("executable by:") || l.startsWith("executable:");
    }

    private void applyCommandProperty(Line line, String command, List<String> aliases, List<ArgSpec> arguments) {
        String text = line.text();
        String lower = text.toLowerCase(Locale.ROOT);
        int colon = text.indexOf(':');
        String value = colon >= 0 ? text.substring(colon + 1).strip() : "";
        if (lower.startsWith("permission")) {
            if (permissionValue(line) == null) {
                error(line, "permission: число от 0 до 4");
            }
            return;
        }
        if (lower.startsWith("executable")) {
            if (executableBy(line) == null) {
                error(line, "executable by: players, console или players and console");
            }
            return;
        }
        if (lower.startsWith("alias")) {
            if (value.isEmpty()) {
                error(line, "aliases: короткое имя, несколько через запятую");
                return;
            }
            for (String part : value.split(",")) {
                String alias = part.strip().toLowerCase(Locale.ROOT);
                if (alias.startsWith("/")) {
                    alias = alias.substring(1);
                }
                if (!alias.matches("[a-z0-9_]{1,32}")) {
                    error(line, "псевдоним '" + part.strip() + "' — только a-z, 0-9 и _");
                } else if (alias.equals(command) || RESERVED_COMMANDS.contains(alias) || commands.containsKey(alias)
                        || aliases.contains(alias)) {
                    error(line, "псевдоним /" + alias + " уже занят");
                } else {
                    aliases.add(alias);
                }
            }
            return;
        }
        if (value.isEmpty()) {
            error(line, "arguments: типы через запятую, необязательные в [скобках]");
            return;
        }
        for (String part : value.split(",")) {
            String piece = part.strip();
            if (piece.isEmpty()) {
                continue;
            }
            boolean optional = piece.startsWith("[") && piece.endsWith("]");
            if (piece.startsWith("<") && piece.endsWith(">")) {
                piece = piece.substring(1, piece.length() - 1).strip();
            } else if (optional) {
                piece = piece.substring(1, piece.length() - 1).strip();
            }
            com.scriptcraft.script.runtime.ArgType type = com.scriptcraft.script.runtime.ArgType.parse(piece);
            if (type == null) {
                error(line, "неизвестный тип аргумента '" + piece + "'");
            } else if (!arguments.isEmpty() && arguments.get(arguments.size() - 1).optional() && !optional) {
                error(line, "обязательный аргумент не может стоять после необязательного");
            } else {
                arguments.add(new ArgSpec(type, optional));
            }
        }
    }

    @Nullable
    private Integer permissionValue(Line line) {
        String text = line.text();
        int colon = text.indexOf(':');
        String value = (colon >= 0 ? text.substring(colon + 1) : text.replaceFirst("(?i)permission", "")).strip();
        if (value.matches("[0-4]")) {
            return Integer.parseInt(value);
        }
        return null;
    }

    /** [игроки, консоль] или null, если строка не разобралась. */
    @Nullable
    private boolean[] executableBy(Line line) {
        String text = line.text();
        int colon = text.indexOf(':');
        String value = (colon >= 0 ? text.substring(colon + 1) : "").strip().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "players", "player" -> new boolean[]{true, false};
            case "console" -> new boolean[]{false, true};
            case "players and console", "console and players", "everyone", "all" -> new boolean[]{true, true};
            default -> null;
        };
    }

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
            } else if (lower.startsWith("chance of ") && lower.endsWith(":")) {
                openIf = -1;
                try {
                    String inner = line.text().substring("chance of ".length(), line.text().length() - 1).strip();
                    if (inner.endsWith("%")) {
                        inner = inner.substring(0, inner.length() - 1).strip();
                    }
                    List<Token> ct = Tokenizer.tokenize(inner);
                    if (ct.isEmpty()) {
                        throw new ParseException("нужно: chance of 50%:");
                    }
                    ExprParser.Parsed ex = ExprParser.parse(ct, 0);
                    if (ex.end() != ct.size()) {
                        throw new ParseException("лишний текст в 'chance of'");
                    }
                    actions.add(new Actions.Chance(ex.expression(), parseActions(children)));
                } catch (ParseException e) {
                    error(line, e.getMessage());
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
            } else if ((lower.startsWith("else if ") || lower.startsWith("elseif ")) && lower.endsWith(":")) {
                if (openIf < 0) {
                    error(line, "'else if' без 'if' прямо над ним");
                } else {
                    try {
                        int prefix = lower.startsWith("elseif ") ? "elseif ".length() : "else if ".length();
                        String condText = line.text().substring(prefix, line.text().length() - 1).strip();
                        Actions.If root = (Actions.If) actions.get(openIf);
                        if (!canAttachElse(root)) {
                            error(line, "'else if' после 'else'");
                            openIf = -1;
                        } else {
                            Actions.If next = new Actions.If(parseCondition(condText), parseActions(children), List.of());
                            actions.set(openIf, attachElseIf(root, next));
                        }
                    } catch (ParseException e) {
                        error(line, e.getMessage());
                        openIf = -1;
                    }
                }
            } else if (lower.equals("else:")) {
                // else:  — относится к if (или к последнему else if) прямо над ним
                if (openIf < 0) {
                    error(line, "'else' без 'if' прямо над ним");
                } else {
                    Actions.If prev = (Actions.If) actions.get(openIf);
                    if (!canAttachElse(prev)) {
                        error(line, "у этого if уже есть else");
                    } else {
                        actions.set(openIf, attachElse(prev, parseActions(children)));
                    }
                }
                openIf = -1;
            } else if (lower.startsWith("wait ") && !lower.endsWith(":")) {
                openIf = -1;
                if (!children.isEmpty()) {
                    error(children.get(0), "у 'wait' не бывает вложенных строк — следующие действия пишите на том же отступе");
                }
                try {
                    actions.add(parseWait(line, body.subList(end, body.size())));
                    break;
                } catch (ParseException e) {
                    error(line, e.getMessage());
                }
            } else if (lower.startsWith("wait ") && lower.endsWith(":")) {
                openIf = -1;
                error(line, "'wait' пишется без двоеточия: строки ниже на том же отступе выполнятся после паузы");
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
     * Условие между "if" и двоеточием.
     * Сравнения, is set, проверки игрока (is sneaking, is holding, has, gamemode, world)
     * и связки and / or (and связывается теснее).
     */
    private Condition parseCondition(String text) throws ParseException {
        List<Token> t = Tokenizer.tokenize(text);
        if (t.size() < 2) {
            throw new ParseException("неполное условие, пример: if {coins::%player%} >= 10:");
        }
        ParsedCond parsed = parseOr(t, 0);
        if (parsed.end() != t.size()) {
            throw new ParseException("лишний текст в условии: '" + t.get(parsed.end()).value() + "'");
        }
        return parsed.condition();
    }

    private record ParsedCond(Condition condition, int end) {
    }

    private ParsedCond parseOr(List<Token> t, int i) throws ParseException {
        ParsedCond left = parseAnd(t, i);
        while (word(t, left.end()).equals("or")) {
            ParsedCond right = parseAnd(t, left.end() + 1);
            left = new ParsedCond(new Condition.Or(left.condition(), right.condition()), right.end());
        }
        return left;
    }

    private ParsedCond parseAnd(List<Token> t, int i) throws ParseException {
        ParsedCond left = parsePrimary(t, i);
        while (word(t, left.end()).equals("and")) {
            ParsedCond right = parsePrimary(t, left.end() + 1);
            left = new ParsedCond(new Condition.And(left.condition(), right.condition()), right.end());
        }
        return left;
    }

    private ParsedCond parsePrimary(List<Token> t, int i) throws ParseException {
        if (i < t.size() && !t.get(i).quoted()) {
            TargetParser.Parsed target = TargetParser.parse(t, i);
            if (target != null) {
                ParsedCond state = tryTargetCondition(t, target);
                if (state != null) {
                    return state;
                }
            }
        }
        ExprParser.Parsed left = ExprParser.parse(t, i);
        int opAt = left.end();
        String op = word(t, opAt);
        if (op.equals("is")) {
            boolean negate = word(t, opAt + 1).equals("not");
            int next = negate ? opAt + 2 : opAt + 1;
            if (word(t, next).equals("set") && (next + 1 == t.size() || isJoin(word(t, next + 1)))) {
                return new ParsedCond(new Condition.IsSet(left.expression(), negate), next + 1);
            }
            ExprParser.Parsed right = ExprParser.parse(t, next);
            return new ParsedCond(new Condition.Compare(left.expression(), negate ? "is not" : "is", right.expression()), right.end());
        }
        if (op.equals(">") || op.equals("<") || op.equals(">=") || op.equals("<=") || op.equals("=") || op.equals("==") || op.equals("!=")) {
            String cmp = op.equals("!=") ? "is not" : (op.equals("=") || op.equals("==") ? "is" : op);
            ExprParser.Parsed right = ExprParser.parse(t, opAt + 1);
            return new ParsedCond(new Condition.Compare(left.expression(), cmp, right.expression()), right.end());
        }
        throw new ParseException("неизвестный оператор условия: " + (op.isEmpty() ? t.get(Math.min(opAt, t.size() - 1)).value() : op)
                + " (is, is not, >, <, >=, <=, is set, and, or)");
    }

    /** player is sneaking / is holding / has item / is in creative / is in world ... или null, если это обычное сравнение. */
    private ParsedCond tryTargetCondition(List<Token> t, TargetParser.Parsed target) throws ParseException {
        int k = target.end();
        String head = word(t, k);
        if (head.equals("has") || (head.equals("does") && word(t, k + 1).equals("not") && word(t, k + 2).equals("have"))) {
            boolean negate = head.equals("does");
            int at = negate ? k + 3 : k + 1;
            if (word(t, at).equals("permission")) {
                if (at + 1 >= t.size()) {
                    throw new ParseException("нужно: player has permission <0-4>");
                }
                int level;
                try {
                    level = Integer.parseInt(t.get(at + 1).value());
                } catch (NumberFormatException e) {
                    throw new ParseException("уровень прав — число от 0 до 4");
                }
                if (level < 0 || level > 4) {
                    throw new ParseException("уровень прав — число от 0 до 4");
                }
                return new ParsedCond(new Condition.Permission(target.target(), level, negate), finish(t, at + 2));
            }
            if (at >= t.size()) {
                throw new ParseException("нужно: player has <предмет>");
            }
            return new ParsedCond(new Condition.HasItem(target.target(), t.get(at).value(), negate), finish(t, at + 1));
        }
        if (!head.equals("is")) {
            return null;
        }
        boolean negate = word(t, k + 1).equals("not");
        int n = negate ? k + 2 : k + 1;
        String s = word(t, n);
        if (s.equals("set") && (n + 1 >= t.size() || isJoin(word(t, n + 1)))) {
            // "Steve is set" остаётся сравнением текста, как раньше: только настоящие цели.
            if (target.target() instanceof Target.Named) {
                return null;
            }
            return new ParsedCond(new Condition.TargetSet(target.target(), negate), n + 1);
        }
        if (s.equals("online") || s.equals("offline")) {
            boolean want = s.equals("online");
            if (negate) {
                want = !want;
            }
            return new ParsedCond(new Condition.Online(target.target(), want), finish(t, n + 1));
        }
        if (s.equals("holding")) {
            if (n + 1 >= t.size()) {
                throw new ParseException("нужно: player is holding <предмет>");
            }
            return new ParsedCond(new Condition.Holding(target.target(), t.get(n + 1).value(), negate), finish(t, n + 2));
        }
        if (s.equals("in")) {
            String next = word(t, n + 1);
            if (next.equals("world")) {
                if (n + 2 >= t.size()) {
                    throw new ParseException("нужно: player is in world overworld");
                }
                return new ParsedCond(new Condition.InWorld(target.target(), t.get(n + 2).value(), negate), finish(t, n + 3));
            }
            int modeAt = n + 1;
            if (next.equals("gamemode") || (next.equals("game") && word(t, n + 2).equals("mode"))) {
                modeAt = next.equals("gamemode") ? n + 2 : n + 3;
            }
            GameType mode = modeOf(word(t, modeAt));
            if (mode == null) {
                throw new ParseException("ожидается creative, survival, adventure, spectator или world <мир>");
            }
            int end = modeAt + 1;
            if (word(t, end).equals("mode")) {
                end++;
            }
            return new ParsedCond(new Condition.GameModeIs(target.target(), mode, negate), finish(t, end));
        }
        if (s.equals("on") && word(t, n + 1).equals("fire")) {
            return new ParsedCond(new Condition.State(target.target(), Condition.Check.ON_FIRE, negate), finish(t, n + 2));
        }
        Condition.Check check = switch (s) {
            case "sneaking", "crouching" -> Condition.Check.SNEAKING;
            case "sprinting" -> Condition.Check.SPRINTING;
            case "flying" -> Condition.Check.FLYING;
            case "sleeping" -> Condition.Check.SLEEPING;
            case "burning" -> Condition.Check.ON_FIRE;
            case "op" -> Condition.Check.OP;
            case "alive" -> Condition.Check.ALIVE;
            case "blocking" -> Condition.Check.BLOCKING;
            case "gliding", "elytra" -> Condition.Check.GLIDING;
            case "swimming" -> Condition.Check.SWIMMING;
            default -> null;
        };
        if (check == null) {
            return null;
        }
        return new ParsedCond(new Condition.State(target.target(), check, negate), finish(t, n + 1));
    }

    /** Конец условия игрока: дальше только and/or или конец строки. */
    private static int finish(List<Token> t, int end) throws ParseException {
        if (end < t.size() && !isJoin(word(t, end))) {
            throw new ParseException("лишний текст в условии: '" + t.get(end).value() + "'");
        }
        return end;
    }

    private static boolean isJoin(String w) {
        return w.equals("and") || w.equals("or");
    }

    private static GameType modeOf(String w) {
        return switch (w) {
            case "survival" -> GameType.SURVIVAL;
            case "creative" -> GameType.CREATIVE;
            case "adventure" -> GameType.ADVENTURE;
            case "spectator" -> GameType.SPECTATOR;
            default -> null;
        };
    }

    /** every 5 seconds / every minute / every tick / every 10 ticks → тики. */
    private static int parsePeriod(String rest) throws ParseException {
        List<Token> t = Tokenizer.tokenize(rest);
        if (t.size() == 1) {
            return switch (word(t, 0)) {
                case "tick", "ticks" -> 1;
                case "second", "seconds", "sec", "secs" -> 20;
                case "minute", "minutes", "min", "mins" -> 1200;
                default -> throw new ParseException("нужно: every <число> seconds|minutes|ticks  или  every second|minute|tick");
            };
        }
        if (t.size() != 2) {
            throw new ParseException("нужно: every 5 seconds");
        }
        ExprParser.Parsed amount = ExprParser.parse(t, 0);
        if (amount.end() != 1) {
            throw new ParseException("нужно: every <число> seconds|minutes|ticks");
        }
        if (!(amount.expression() instanceof Expression.Literal lit) || !(lit.value() instanceof Double)) {
            throw new ParseException("период 'every' задаётся числом, например every 5 seconds");
        }
        int per = switch (word(t, 1)) {
            case "tick", "ticks" -> 1;
            case "second", "seconds", "sec", "secs" -> 20;
            case "minute", "minutes", "min", "mins" -> 1200;
            default -> throw new ParseException("единица времени: ticks, seconds или minutes");
        };
        int ticks = (int) Math.round(((Double) lit.value()) * per);
        if (ticks < 1) {
            throw new ParseException("период должен быть хотя бы 1 тик");
        }
        if (ticks > 20 * 3600) {
            throw new ParseException("период 'every' не длиннее часа");
        }
        return ticks;
    }

    /** wait 2 seconds — хвост блока выполнится после паузы. */
    private Action parseWait(Line line, List<Line> rest) throws ParseException {
        List<Token> t = Tokenizer.tokenize(line.text());
        if (t.size() < 2) {
            throw new ParseException("нужно: wait <число> seconds|ticks|minutes");
        }
        String unit = word(t, t.size() - 1);
        int per = switch (unit) {
            case "tick", "ticks" -> 1;
            case "second", "seconds", "sec", "secs" -> 20;
            case "minute", "minutes", "min", "mins" -> 1200;
            default -> throw new ParseException("единица времени: ticks, seconds или minutes");
        };
        ExprParser.Parsed amount = ExprParser.parse(t, 1);
        if (amount.end() != t.size() - 1) {
            throw new ParseException("нужно: wait <число> seconds|ticks|minutes");
        }
        return new Actions.Wait(amount.expression(), per, parseActions(rest));
    }

    private static boolean canAttachElse(Actions.If root) {
        if (root.otherwise().isEmpty()) {
            return true;
        }
        if (root.otherwise().size() == 1 && root.otherwise().get(0) instanceof Actions.If child) {
            return canAttachElse(child);
        }
        return false;
    }

    private static Actions.If attachElseIf(Actions.If root, Actions.If next) {
        if (root.otherwise().size() == 1 && root.otherwise().get(0) instanceof Actions.If child) {
            return new Actions.If(root.condition(), root.then(), List.of(attachElseIf(child, next)));
        }
        return new Actions.If(root.condition(), root.then(), List.of(next));
    }

    private static Actions.If attachElse(Actions.If root, List<Action> elseBody) {
        if (root.otherwise().size() == 1 && root.otherwise().get(0) instanceof Actions.If child) {
            return new Actions.If(root.condition(), root.then(), List.of(attachElse(child, elseBody)));
        }
        return new Actions.If(root.condition(), root.then(), elseBody);
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
        Expression named = namedWord(v);
        return named != null ? named : new Expression.Text(v);
    }

    /** script, arg-1, player-argument — одно слово, которое не является текстом. */
    @Nullable
    static Expression namedWord(String raw) {
        String w = raw.toLowerCase(Locale.ROOT);
        if (w.equals("script")) {
            return new Expression.Script();
        }
        java.util.regex.Matcher index = java.util.regex.Pattern.compile("arg(?:ument)?-(\\d+)").matcher(w);
        if (index.matches()) {
            int n = Integer.parseInt(index.group(1));
            return n > 0 ? new Expression.Arg(n, null) : null;
        }
        if (w.endsWith("-argument")) {
            com.scriptcraft.script.runtime.ArgType type = com.scriptcraft.script.runtime.ArgType.parse(
                    w.substring(0, w.length() - "-argument".length()));
            if (type != null) {
                return new Expression.Arg(0, type);
            }
        }
        return null;
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
                // set {name} to <значение>  (значение может быть выражением: health of player)
                String name = variableName(t, 1);
                expectWord(t, 2, "to");
                ExprParser.Parsed value = ExprParser.parse(t, 3);
                if (value.end() != t.size()) {
                    throw new ParseException("нужно: set {имя} to <значение> (текст с пробелами — в кавычках)");
                }
                return new Actions.SetVariable(name, value.expression());
            }
            case "add", "remove" -> {
                // add <выражение> to {name} / remove <выражение> from {name}
                boolean remove = head.equals("remove");
                String sep = remove ? "from" : "to";
                int at = -1;
                for (int i = 1; i < t.size(); i++) {
                    if (word(t, i).equals(sep)) {
                        at = i;
                        break;
                    }
                }
                if (at < 0) {
                    throw new ParseException(remove ? "нужно: remove <число> from {имя}" : "нужно: add <число> to {имя}");
                }
                ExprParser.Parsed amount = ExprParser.parse(t, 1);
                if (amount.end() != at || at + 2 != t.size()) {
                    throw new ParseException(remove ? "нужно: remove <число> from {имя}" : "нужно: add <число> to {имя}");
                }
                return new Actions.AddToVariable(variableName(t, at + 1), amount.expression(), remove);
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
