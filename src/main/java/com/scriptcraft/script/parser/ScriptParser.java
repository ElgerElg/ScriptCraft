package com.scriptcraft.script.parser;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.hud.HudEntry;
import com.scriptcraft.script.ScriptSource;
import com.scriptcraft.script.parser.Tokenizer.Token;
import com.scriptcraft.script.runtime.Action;
import com.scriptcraft.script.runtime.Actions;
import com.scriptcraft.script.runtime.Condition;
import com.scriptcraft.script.runtime.Effects;
import com.scriptcraft.script.runtime.EntityTarget;
import com.scriptcraft.script.runtime.Expression;
import com.scriptcraft.script.runtime.KeyNames;
import com.scriptcraft.script.runtime.MenuNames;
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
import com.scriptcraft.screen.ListElement;
import com.scriptcraft.screen.VariableListElement;
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
    private final Map<String, EntityDefinition> entities = new LinkedHashMap<>();
    private final List<PeriodHandler> periods = new ArrayList<>();
    private final Map<String, FunctionDefinition> functions = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();
    /** Параметры функции, пока разбирается её тело. Пусто вне функции. */
    private static final ThreadLocal<Map<String, FuncType>> LOCALS = new ThreadLocal<>();
    private String currentFile = "?";
    /** Текущее пространство имён для define (сбрасывается на scriptcraft в начале каждого файла). */
    private String currentNamespace = ScriptCraft.MODID;
    /** Заготовки файла: {@name} подставляется в текст до разбора строк. */
    private final Map<String, String> options = new LinkedHashMap<>();
    private static final Pattern OPTION_REF = Pattern.compile("\\{@([A-Za-z0-9_-]+)}");

    public ParseResult parse(List<ScriptSource> sources) {
        for (ScriptSource source : sources) {
            currentFile = source.fileName();
            currentNamespace = ScriptCraft.MODID;
            try {
                parseFile(preprocess(source.lines()));
            } catch (RuntimeException e) {
                errors.add(currentFile + ":1: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
            }
        }
        return new ParseResult(events, commands, List.copyOf(blocks.values()), List.copyOf(items.values()),
                Map.copyOf(entities), List.copyOf(periods), Map.copyOf(functions), List.copyOf(errors));
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
        options.clear();
        collectOptions(lines);
        settleOptions();
        List<Line> expanded = new ArrayList<>(lines.size());
        for (Line line : lines) {
            expanded.add(new Line(line.number(), line.indent(), applyOptions(line.text(), line, true)));
        }
        lines = expanded;
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
            boolean functionHeader = lowerText.startsWith("function ") && lowerText.contains("(");
            if (line.indent() != 0) {
                error(line, "неожиданный отступ");
            } else if (!line.text().endsWith(":") && !functionHeader) {
                error(line, "ожидается заголовок блока с двоеточием, например 'on join:'");
            } else {
                String header = line.text().strip();
                if (header.endsWith(":")) {
                    header = header.substring(0, header.length() - 1).strip();
                }
                parseTopLevel(line, header, lines.subList(i + 1, end));
            }
            i = end;
        }
    }

    private void parseTopLevel(Line headerLine, String header, List<Line> body) {
        String lower = header.toLowerCase(Locale.ROOT);

        if (lower.equals("options")) {
            return;
        }

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
            String rawRest = header.substring(3).strip();
            String rest = rawRest.toLowerCase(Locale.ROOT);
            if (rest.startsWith("key release ") || rest.startsWith("release key ")) {
                String tail = rawRest.substring(12).strip();
                parseKeyed(headerLine, ScriptEvent.KEY_RELEASE, keyFilter(headerLine, tail), body);
                return;
            }
            if (rest.startsWith("key hold ") || rest.startsWith("hold key ")) {
                String tail = rawRest.substring(9).strip();
                parseKeyed(headerLine, ScriptEvent.KEY_HOLD, keyFilter(headerLine, tail), body);
                return;
            }
            if (rest.startsWith("any key")) {
                String tail = "any" + rawRest.substring(7);
                parseKeyed(headerLine, ScriptEvent.KEY, keyFilter(headerLine, tail), body);
                return;
            }
            if (rest.startsWith("key ")) {
                parseKeyed(headerLine, ScriptEvent.KEY, keyFilter(headerLine, rawRest.substring(4).strip()), body);
                return;
            }
            if (rest.startsWith("screen open") || rest.startsWith("open screen")
                    || rest.startsWith("gui open") || rest.startsWith("open gui")) {
                parseKeyed(headerLine, ScriptEvent.SCREEN_OPEN, screenEventFilter(headerLine, rawRest), body);
                return;
            }
            if (rest.startsWith("screen close") || rest.startsWith("close screen")
                    || rest.startsWith("gui close") || rest.startsWith("close gui")) {
                parseKeyed(headerLine, ScriptEvent.SCREEN_CLOSE, screenEventFilter(headerLine, rawRest), body);
                return;
            }
            if (rest.equals("gui change") || rest.startsWith("gui change ")
                    || rest.equals("screen change") || rest.startsWith("screen change ")
                    || rest.equals("gui input") || rest.startsWith("gui input ")
                    || rest.equals("input") || rest.startsWith("input ")
                    || rest.equals("slider") || rest.startsWith("slider ")
                    || rest.equals("toggle") || rest.startsWith("toggle ")) {
                parseKeyed(headerLine, ScriptEvent.GUI_CHANGE, guiChangeFilter(headerLine, rawRest), body);
                return;
            }
            if (rest.startsWith("open ")) {
                parseKeyed(headerLine, ScriptEvent.OPEN, menuFilter(headerLine, rest.substring(5).strip()), body);
                return;
            }
            if (rest.equals("npc click") || rest.startsWith("npc click ")
                    || rest.equals("click npc") || rest.startsWith("click npc ")
                    || rest.equals("right click npc") || rest.startsWith("right click npc ")) {
                parseKeyed(headerLine, ScriptEvent.NPC_CLICK, npcEventFilter(headerLine, rawRest), body);
                return;
            }
            if (rest.equals("npc attack") || rest.startsWith("npc attack ")
                    || rest.equals("attack npc") || rest.startsWith("attack npc ")
                    || rest.equals("hit npc") || rest.startsWith("hit npc ")) {
                parseKeyed(headerLine, ScriptEvent.NPC_ATTACK, npcEventFilter(headerLine, rawRest), body);
                return;
            }
            // "on join:", "on load:", несколько событий — "on join or load:" (одно тело на все).
            // Сначала проверяем целое имя события (например, "move or turning around"), чтобы не разбить его по "or".
            List<ScriptEvent> list = new ArrayList<>();
            ScriptEvent wholeEvent = ScriptEvent.byName(lower.substring(3));
            if (wholeEvent != null) {
                list.add(wholeEvent);
            } else {
                for (String part : lower.substring(3).split("\\s+or\\s+")) {
                    ScriptEvent event = ScriptEvent.byName(part);
                    if (event == null) {
                        error(headerLine, "неизвестное событие: " + part.strip()
                                + " (join, quit, death, respawn, chat, break, place, right click, left click, drop, pickup, receive, damage, move, turning around, move or turning around, first join, world change, gamemode change, load, unload, key <клавиша>, open <меню>)");
                        return;
                    }
                    if (list.contains(event)) {
                        error(headerLine, "событие '" + event.scriptName() + "' указано дважды");
                        return;
                    }
                    list.add(event);
                }
            }
            // load/unload вместе с событием игрока (join/quit) выполняются для КАЖДОГО игрока онлайн:
            // "on join or load:" = при входе игрока + сразу всем, кто уже на сервере, когда скрипт загрузился.
            boolean withPlayerEvent = list.stream().anyMatch(ScriptEvent::playerEvent);
            List<Action> actions = parseActions(body);
            for (ScriptEvent event : list) {
                events.computeIfAbsent(event, e -> new ArrayList<>())
                        .add(new EventHandler(currentFile, actions, !event.playerEvent() && withPlayerEvent, null));
            }
            return;
        }

        Matcher cmd = COMMAND_HEADER.matcher(lower);
        if (cmd.matches()) {
            parseCommand(headerLine, cmd.group(1), cmd.group(2), body);
            return;
        }

        if (lower.startsWith("define entity ") || lower.startsWith("define mob ") || lower.startsWith("define boss ")) {
            int pfx = lower.startsWith("define entity ") ? 14 : 12;
            String raw = lower.substring(pfx).strip();
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
                error(headerLine, "некорректный id сущности '" + path + "' (только строчные a-z, 0-9, _ . -)");
                return;
            }
            String id = ns + ":" + path;
            if (entities.containsKey(id)) {
                error(headerLine, "сущность '" + id + "' уже объявлена");
                return;
            }
            Map<String, String> props = parseProperties(body);
            try {
                String ai = EntityDefinition.parseAi(props.get("ai"));
                if (ai == null) {
                    error(headerLine, "ai: ожидается hostile, passive, turret или none");
                    return;
                }
                float w = floatProp(props, "width", 0.8f);
                float h = floatProp(props, "height", 1.9f);
                String hb = props.get("hitbox");
                if (hb != null && hb.contains("x")) {
                    String[] wh = hb.toLowerCase(Locale.ROOT).split("x", 2);
                    w = Float.parseFloat(wh[0].strip());
                    h = Float.parseFloat(wh[1].strip());
                }
                float defRange = EntityDefinition.AI_TURRET.equals(ai) ? 12.0f : 2.5f;
                Map<String, String> pBones = new LinkedHashMap<>();
                String pbRaw = props.getOrDefault("particle_bone", props.get("particles"));
                if (pbRaw != null && !pbRaw.isBlank()) {
                    for (String pair : pbRaw.split("[;,]")) {
                        int eq = pair.indexOf('=');
                        if (eq > 0) {
                            pBones.put(pair.substring(0, eq).strip(), pair.substring(eq + 1).strip());
                        }
                    }
                }
                boolean glow = !props.containsKey("glow") || boolProp(props, "glow");
                boolean nametag = props.containsKey("nametag") ? boolProp(props, "nametag") : props.containsKey("name");
                boolean pushable = props.containsKey("pushable") ? boolProp(props, "pushable")
                        : (!EntityDefinition.AI_TURRET.equals(ai) && !EntityDefinition.AI_NONE.equals(ai));
                boolean gravity = !props.containsKey("gravity") || boolProp(props, "gravity");
                entities.put(id, new EntityDefinition(
                        id, props.get("name"), ai,
                        props.get("model"), props.get("texture"),
                        props.getOrDefault("animation", props.get("animations")),
                        props.getOrDefault("idle_animation", props.getOrDefault("idle", "idle")),
                        props.getOrDefault("walk_animation", props.getOrDefault("walk", "walk")),
                        props.getOrDefault("attack_animation", props.getOrDefault("attack", "attack")),
                        props.getOrDefault("death_animation", props.getOrDefault("death", "death")),
                        props.getOrDefault("spawn_animation", props.get("spawn")),
                        floatProp(props, "health", floatProp(props, "hp", 20f)),
                        floatProp(props, "speed", 0.25f),
                        floatProp(props, "damage", 4.0f),
                        floatProp(props, "attack_range", floatProp(props, "range", defRange)),
                        floatProp(props, "follow_range", 24.0f),
                        w, h, floatProp(props, "scale", 1.0f),
                        glow, nametag, pushable, boolProp(props, "invulnerable"), gravity, pBones));
            } catch (NumberFormatException e) {
                error(headerLine, "неверное число в свойствах define entity: " + e.getMessage());
            }
            return;
        }

        if (lower.startsWith("define block ") || lower.startsWith("define item ") || lower.startsWith("define armor ")) {
            boolean isBlock = lower.startsWith("define block ");
            boolean isArmorKw = lower.startsWith("define armor ");
            // id приводится к нижнему регистру, как и раньше (Ruby_Block -> ruby_block).
            String raw = lower.substring(isBlock ? 13 : (isArmorKw ? 13 : 12)).strip();
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
                    float friction = floatProp(props, "friction", 0.6f);
                    float speed = floatProp(props, "speed", 1f);
                    float jump = floatProp(props, "jump", 1f);
                    if (friction < 0 || friction > 1 || speed < 0 || speed > 8 || jump < 0 || jump > 2) {
                        error(headerLine, "friction is 0..1, speed is 0..8, jump is 0..2",
                                "friction 0..1, speed 0..8, jump 0..2");
                        return;
                    }
                    int burn = (int) floatProp(props, "burn", boolProp(props, "flammable") ? 5 : 0);
                    int spread = (int) floatProp(props, "spread", boolProp(props, "flammable") ? 20 : 0);
                    boolean collision = !props.containsKey("collision") || boolProp(props, "collision");
                    blocks.put(id, new BlockDefinition(id, props.get("name"),
                            floatProp(props, "hardness", 1.5f), floatProp(props, "resistance", 6f),
                            props.getOrDefault("sound", "stone").toLowerCase(Locale.ROOT),
                            (int) floatProp(props, "light", 0), boolProp(props, "requires_tool"), facing, render, full,
                            friction, speed, jump, boolProp(props, "gravity"), collision, Math.max(0, burn), Math.max(0, spread),
                            props.get("model"), props.get("texture"),
                            props.getOrDefault("animation", props.get("animations")),
                            props.getOrDefault("idle_animation", props.get("idle")),
                            floatProp(props, "scale", 1.0f),
                            props.get("lore"), boolProp(props, "glow")));
                } else {
                    String rawType = isArmorKw
                            ? props.getOrDefault("slot", props.getOrDefault("type", "helmet"))
                            : props.getOrDefault("type", props.getOrDefault("slot", "item"));
                    String type = ItemDefinition.normalizeType(rawType);
                    if (type == null) {
                        error(headerLine, "type: item, food, sword, pickaxe, axe, shovel, hoe, fishing_rod, shears, helmet, chestplate, leggings or boots",
                                "type: item, food, sword, pickaxe, axe, shovel, hoe, fishing_rod, shears, helmet, chestplate, leggings или boots");
                        return;
                    }
                    String tier = ItemDefinition.normalizeTier(props.getOrDefault("tier", "iron"));
                    if (tier == null) {
                        error(headerLine, "tier: wood, stone, iron, gold, diamond or netherite",
                                "tier: wood, stone, iron, gold, diamond или netherite");
                        return;
                    }
                    boolean singleStack = switch (type) {
                        case "sword", "pickaxe", "axe", "shovel", "hoe", "fishing_rod", "shears",
                             "helmet", "chestplate", "leggings", "boots" -> true;
                        default -> props.containsKey("model");
                    };
                    int stack = (int) floatProp(props, "stack_size", singleStack ? 1 : 64);
                    int durability = props.containsKey("durability")
                            ? Math.max(0, (int) floatProp(props, "durability", 0))
                            : ItemDefinition.defaultDurability(type, tier);
                    int defArmor = switch (type) {
                        case "helmet" -> 3;
                        case "chestplate" -> 8;
                        case "leggings" -> 6;
                        case "boots" -> 3;
                        default -> 0;
                    };
                    int defense = (int) floatProp(props, "defense", floatProp(props, "armor", defArmor));
                    float toughness = floatProp(props, "toughness", 0f);
                    float customDamage = props.containsKey("damage") ? floatProp(props, "damage", -1f) : -1f;
                    items.put(id, new ItemDefinition(id, props.get("name"), stack, boolProp(props, "fire_resistant"),
                            props.getOrDefault("rarity", "common").toLowerCase(Locale.ROOT),
                            type, Math.max(0, Math.min(20, (int) floatProp(props, "nutrition", 4))),
                            Math.max(0f, Math.min(2f, floatProp(props, "saturation", 0.6f))),
                            boolProp(props, "always_eat"), tier, durability,
                            defense, toughness,
                            props.get("model"), props.get("texture"),
                            props.getOrDefault("animation", props.get("animations")),
                            props.getOrDefault("idle_animation", props.get("idle")),
                            floatProp(props, "scale", 1.0f),
                            props.get("lore"), boolProp(props, "glow"), customDamage));
                }
            } catch (NumberFormatException e) {
                error(headerLine, "неверное число в свойствах: " + e.getMessage());
            }
            return;
        }

        if (lower.startsWith("function ") || lower.startsWith("function(")) {
            parseFunction(headerLine, header, body);
            return;
        }

        error(headerLine, "неизвестный блок: " + header);
    }

    /**
     * function Test(who: player):
     *     send "hi" to who
     * Двоеточие у заголовка можно не писать. Вызов — отдельная строка: Test(targeted player).
     */
    private void parseFunction(Line headerLine, String header, List<Line> body) {
        String rest = header.substring("function".length()).strip();
        int open = rest.indexOf('(');
        int close = rest.lastIndexOf(')');
        if (open <= 0 || close < open || !rest.substring(close + 1).isBlank()) {
            error(headerLine, "need: function Name(who: player):", "нужно: function Name(who: player):");
            return;
        }
        String name = rest.substring(0, open).strip();
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            error(headerLine, "function name must be letters, digits or _",
                    "имя функции — буквы, цифры или _");
            return;
        }
        String key = name.toLowerCase(Locale.ROOT);
        if (functions.containsKey(key)) {
            error(headerLine, "function '" + name + "' is already defined",
                    "функция '" + name + "' уже объявлена");
            return;
        }
        List<FunctionDefinition.Param> params = new ArrayList<>();
        Map<String, FuncType> scope = new LinkedHashMap<>();
        String inside = rest.substring(open + 1, close).strip();
        if (!inside.isEmpty()) {
            for (String part : splitArgs(inside)) {
                String piece = part.strip();
                int colon = piece.indexOf(':');
                if (colon <= 0) {
                    error(headerLine, "parameter needs a type: name: player",
                            "параметру нужен тип: имя: player");
                    continue;
                }
                String param = piece.substring(0, colon).strip();
                String typeRaw = piece.substring(colon + 1).strip();
                if (!param.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    error(headerLine, "parameter name must be letters, digits or _",
                            "имя параметра — буквы, цифры или _");
                    continue;
                }
                FuncType type = FuncType.parse(typeRaw);
                if (type == null) {
                    error(headerLine, "unknown type '" + typeRaw + "' (player, offline player, entity, block, location, text, integer, number, boolean, world, item)",
                            "неизвестный тип '" + typeRaw + "' (player, offline player, entity, block, location, text, integer, number, boolean, world, item)");
                    continue;
                }
                if (scope.put(param.toLowerCase(Locale.ROOT), type) != null) {
                    error(headerLine, "parameter '" + param + "' is listed twice",
                            "параметр '" + param + "' указан дважды");
                    continue;
                }
                params.add(new FunctionDefinition.Param(param, type));
            }
        }
        Map<String, FuncType> previous = LOCALS.get();
        LOCALS.set(scope);
        List<Action> actions;
        try {
            actions = parseActions(body);
        } finally {
            if (previous == null) {
                LOCALS.remove();
            } else {
                LOCALS.set(previous);
            }
        }
        functions.put(key, new FunctionDefinition(name, List.copyOf(params), actions, currentFile));
    }

    /** Тип параметра функции с таким именем, пока разбирается её тело. */
    @Nullable
    static FuncType localType(String word) {
        if (word == null || word.isEmpty()) {
            return null;
        }
        Map<String, FuncType> map = LOCALS.get();
        if (map == null) {
            return null;
        }
        String key = word.toLowerCase(Locale.ROOT);
        if (key.length() > 2 && key.startsWith("%") && key.endsWith("%")) {
            key = key.substring(1, key.length() - 1);
        }
        return map.get(key);
    }

    /** Аргументы вызова и параметры заголовка: запятая вне кавычек и скобок. */
    private static List<String> splitArgs(String inside) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quote = false;
        int depth = 0;
        for (int i = 0; i < inside.length(); i++) {
            char c = inside.charAt(i);
            if (c == '"' && (i == 0 || inside.charAt(i - 1) != '\\')) {
                quote = !quote;
                current.append(c);
                continue;
            }
            if (!quote && (c == '(' || c == '{' || c == '[')) {
                depth++;
            } else if (!quote && (c == ')' || c == '}' || c == ']') && depth > 0) {
                depth--;
            }
            if (!quote && depth == 0 && c == ',') {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (!current.isEmpty() || !parts.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    /**
     * Test(player) / Mark(location of targeted block, stone).
     * Запятые токенайзер съедает, поэтому строка разбирается целиком, до таблицы эффектов.
     */
    @Nullable
    private static Action tryFunctionCall(String text) throws ParseException {
        int open = text.indexOf('(');
        if (open <= 0 || !text.endsWith(")") || text.substring(0, open).indexOf('"') >= 0) {
            return null;
        }
        String name = text.substring(0, open).strip();
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return null;
        }
        String inside = text.substring(open + 1, text.length() - 1).strip();
        List<Expression> args = new ArrayList<>();
        if (!inside.isEmpty()) {
            for (String part : splitArgs(inside)) {
                String piece = part.strip();
                if (piece.isEmpty()) {
                    throw new ParseException("empty function argument", "пустой аргумент функции");
                }
                List<Token> tokens = Tokenizer.tokenize(piece);
                ExprParser.Parsed parsed = ExprParser.parse(tokens, 0);
                if (parsed.end() != tokens.size()) {
                    throw new ParseException("extra text in function argument: " + piece,
                            "лишний текст в аргументе функции: " + piece);
                }
                args.add(parsed.expression());
            }
        }
        return new Actions.CallFunction(name, args);
    }

    /** Разбор строки вызова функции из кнопки/элемента экрана: "Buy(player, 10)" или "Heal()". */
    public static Action parseScreenFunctionCall(String text) throws ParseException {
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        if (!trimmed.contains("(")) {
            trimmed = trimmed + "()";
        }
        return tryFunctionCall(trimmed);
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
        String permissionNode = null;
        Matcher legacy = LEGACY_PERMISSION.matcher(rest);
        if (legacy.find()) {
            permission = Integer.parseInt(legacy.group(1));
            rest = (rest.substring(0, legacy.start()) + " " + rest.substring(legacy.end())).strip();
        }
        List<ArgSpec> arguments = new ArrayList<>();
        Matcher args = COMMAND_ARG.matcher(rest);
        int cursor = 0;
        while (args.find()) {
            addCommandWords(headerLine, rest.substring(cursor, args.start()), arguments);
            addTypedArgument(headerLine, arguments, args.group(2) != null, args.group(2) != null ? args.group(2) : args.group(1));
            cursor = args.end();
        }
        addCommandWords(headerLine, rest.substring(cursor), arguments);
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
                        permissionNode = null;
                    } else {
                        String node = permissionNode(line);
                        if (node != null) {
                            permissionNode = node;
                        }
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
                players, console, parseActions(actions), currentFile, permissionNode);
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
            if (permissionValue(line) == null && permissionNode(line) == null) {
                error(line, "permission: a level 0-4 or a LuckPerms node",
                        "permission: уровень 0–4 или узел LuckPerms");
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
            if (!optional && !piece.startsWith("<") && piece.matches("[a-zA-Z0-9_]+")
                    && com.scriptcraft.script.runtime.ArgType.parse(piece) == null) {
                addCommandWords(line, piece, arguments);
                continue;
            }
            addTypedArgument(line, arguments, optional, piece);
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

    /** Узел LuckPerms или null, если это уровень 0–4 либо строка не похожа на узел. */
    @Nullable
    private String permissionNode(Line line) {
        String text = line.text();
        int colon = text.indexOf(':');
        String value = (colon >= 0 ? text.substring(colon + 1) : text.replaceFirst("(?i)permission", "")).strip();
        if (value.matches("[0-4]") || !value.matches("[A-Za-z0-9_.*-]+")) {
            return null;
        }
        return value;
    }

    private void collectOptions(List<Line> lines) {
        int i = 0;
        while (i < lines.size()) {
            Line line = lines.get(i);
            int end = blockEnd(lines, i);
            if (line.indent() == 0 && line.text().equalsIgnoreCase("options:")) {
                for (Line row : lines.subList(i + 1, end)) {
                    String text = row.text();
                    int colon = text.indexOf(':');
                    if (colon <= 0) {
                        error(row, "option: name: value", "заготовка: имя: значение");
                        continue;
                    }
                    String name = text.substring(0, colon).strip();
                    if (!name.matches("[A-Za-z0-9_-]+")) {
                        error(row, "an option name is letters, digits, _ and -", "имя заготовки: буквы, цифры, _ и -");
                        continue;
                    }
                    options.put(name.toLowerCase(Locale.ROOT), text.substring(colon + 1).strip());
                }
            }
            i = end;
        }
    }

    /** Заготовки могут ссылаться друг на друга. Несколько проходов, без бесконечной подстановки. */
    private void settleOptions() {
        for (int pass = 0; pass < 8; pass++) {
            boolean changed = false;
            for (Map.Entry<String, String> entry : options.entrySet()) {
                String next = applyOptions(entry.getValue(), null, false);
                if (!next.equals(entry.getValue())) {
                    entry.setValue(next);
                    changed = true;
                }
            }
            if (!changed) {
                return;
            }
        }
    }

    private String applyOptions(String text, @Nullable Line line, boolean report) {
        Matcher matcher = OPTION_REF.matcher(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            out.append(text, last, matcher.start());
            String value = options.get(matcher.group(1).toLowerCase(Locale.ROOT));
            if (value == null) {
                if (report && line != null) {
                    error(line, "unknown option {@" + matcher.group(1) + "}",
                            "неизвестная заготовка {@" + matcher.group(1) + "}");
                }
                out.append(matcher.group());
            } else {
                out.append(value);
            }
            last = matcher.end();
        }
        out.append(text.substring(last));
        return out.toString();
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
        List<Integer> actionLines = new ArrayList<>();
        if (body.isEmpty()) {
            return actions;
        }
        int baseIndent = body.get(0).indent();
        // Индекс последнего "if" без "else" в списке actions (-1, если предыдущая строка — не if).
        int openIf = -1;
        int i = 0;
        while (i < body.size()) {
            Line line = body.get(i);
            int prevSize = actions.size();
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
            } else if (lower.startsWith("while ") && lower.endsWith(":")) {
                openIf = -1;
                try {
                    if (children.isEmpty()) {
                        throw new ParseException("у 'while' нет вложенных действий");
                    }
                    Condition cond = parseCondition(line.text().substring(6, line.text().length() - 1).strip());
                    actions.add(new Actions.While(cond, parseActions(children)));
                } catch (ParseException e) {
                    error(line, e.getMessage());
                }
            } else if (lower.startsWith("loop ") && lower.endsWith(":")) {
                // loop <N> times: | loop <цель>: | loop {имя::*}:
                openIf = -1;
                try {
                    String inner = line.text().substring(5, line.text().length() - 1).strip();
                    List<Token> lt = Tokenizer.tokenize(inner);
                    if (!lt.isEmpty() && !lt.get(0).quoted() && lt.get(0).value().startsWith("{") && lt.get(0).value().endsWith("::*}")) {
                        if (children.isEmpty()) {
                            throw new ParseException("у 'loop' нет вложенных действий");
                        }
                        int sortMode = 0;
                        if (lt.size() > 1) {
                            if (word(lt, 1).equals("sorted")) {
                                String dir = word(lt, 2);
                                if (dir.equals("descending") || dir.equals("desc")) {
                                    sortMode = -1;
                                } else if (dir.isEmpty() || dir.equals("ascending") || dir.equals("asc")) {
                                    sortMode = 1;
                                } else {
                                    throw new ParseException("нужно: loop {имя::*} sorted [ascending|descending]:");
                                }
                            } else {
                                throw new ParseException("нужно: loop {имя::*} [sorted [ascending|descending]]:");
                            }
                        }
                        String raw = lt.get(0).value();
                        actions.add(new Actions.LoopIndices(raw.substring(1, raw.length() - 1), parseActions(children), sortMode));
                        while (actionLines.size() < actions.size()) actionLines.add(line.number());
                        i = end;
                        continue;
                    }
                    List<Token> t = Tokenizer.tokenize(inner);
                    if (t.size() >= 2 && (word(t, t.size() - 1).equals("times") || word(t, t.size() - 1).equals("time"))) {
                        if (children.isEmpty()) {
                            throw new ParseException("у 'loop' нет вложенных действий");
                        }
                        List<Token> countTokens = t.subList(0, t.size() - 1);
                        ExprParser.Parsed count = ExprParser.parse(countTokens, 0);
                        if (count.end() != countTokens.size()) {
                            throw new ParseException("нужно: loop <число> times:");
                        }
                        actions.add(new Actions.LoopTimes(count.expression(), parseActions(children)));
                        while (actionLines.size() < actions.size()) actionLines.add(line.number());
                        i = end;
                        continue;
                    }
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
                    actions.add(parseWait(line));
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
                } catch (RuntimeException e) {
                    error(line, e.getMessage() == null ? e.toString() : e.getMessage());
                }
            }
            while (actionLines.size() < actions.size()) {
                actionLines.add(line.number());
            }
            i = end;
        }
        for (int k = 0; k < actions.size(); k++) {
            actions.set(k, Action.atLine(actionLines.get(k), actions.get(k)));
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
        if (i < t.size() && !t.get(i).quoted() && t.get(i).value().startsWith("{") && t.get(i).value().endsWith("::*}")) {
            String rawList = t.get(i).value().substring(1, t.get(i).value().length() - 1);
            String w1 = word(t, i + 1);
            if (w1.equals("contains")) {
                return parseMultiExprCond(t, i + 2, false, r -> new Condition.ListContains(rawList, r, false));
            }
            if (w1.equals("does") && word(t, i + 2).equals("not") && word(t, i + 3).equals("contain")) {
                return parseMultiExprCond(t, i + 4, true, r -> new Condition.ListContains(rawList, r, true));
            }
            if (w1.equals("has") && (word(t, i + 2).equals("key") || word(t, i + 2).equals("index"))) {
                return parseMultiExprCond(t, i + 3, false, r -> new Condition.ListHasKey(rawList, r, false));
            }
            if (w1.equals("does") && word(t, i + 2).equals("not") && word(t, i + 3).equals("have")
                    && (word(t, i + 4).equals("key") || word(t, i + 4).equals("index"))) {
                return parseMultiExprCond(t, i + 5, true, r -> new Condition.ListHasKey(rawList, r, true));
            }
            if (w1.equals("is")) {
                boolean neg = word(t, i + 2).equals("not");
                int next = neg ? i + 3 : i + 2;
                if (word(t, next).equals("empty")) {
                    return new ParsedCond(new Condition.ListEmpty(rawList, neg), next + 1);
                }
            }
        }
        if (i < t.size() && !t.get(i).quoted() && word(t, i).equals("group") && !word(t, i + 1).equals("of") && i + 1 < t.size()) {
            ExprParser.Parsed g = ExprParser.parse(List.of(t.get(i + 1)), 0);
            int k = i + 2;
            String h = word(t, k);
            if ((h.equals("has") && word(t, k + 1).equals("permission"))
                    || (h.equals("does") && word(t, k + 1).equals("not") && word(t, k + 2).equals("have") && word(t, k + 3).equals("permission"))) {
                boolean neg = h.equals("does");
                int pAt = neg ? k + 4 : k + 2;
                if (pAt >= t.size()) {
                    throw new ParseException("нужно: group \"имя\" has permission \"узел\"");
                }
                ExprParser.Parsed nodeExpr = ExprParser.parse(List.of(t.get(pAt)), 0);
                return new ParsedCond(new Condition.GroupPermission(g.expression(), nodeExpr.expression(), neg), finish(t, pAt + 1));
            }
        }
        if (i < t.size() && !t.get(i).quoted()) {
            TargetParser.ParsedEntity entCond = TargetParser.parseEntity(t, i);
            if (entCond != null && word(t, entCond.end()).equals("is")) {
                int k = entCond.end();
                boolean neg = word(t, k + 1).equals("not");
                int n = neg ? k + 2 : k + 1;
                String st = word(t, n);
                if (st.equals("leashed") || st.equals("tethered")) {
                    return new ParsedCond(new Condition.Leashed(entCond.target(), neg), finish(t, n + 1));
                }
            }
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
            if (word(t, next).equals("empty") && (next + 1 == t.size() || isJoin(word(t, next + 1)))) {
                return new ParsedCond(new Condition.TextEmpty(left.expression(), negate), next + 1);
            }
            return parseMultiExprCond(t, next, negate,
                    r -> new Condition.Compare(left.expression(), negate ? "is not" : "is", r));
        }
        if (op.equals("contains")) {
            return parseMultiExprCond(t, opAt + 1, false,
                    r -> new Condition.TextMatch(left.expression(), r, "contains", false));
        }
        if (op.equals("starts") && word(t, opAt + 1).equals("with")) {
            return parseMultiExprCond(t, opAt + 2, false,
                    r -> new Condition.TextMatch(left.expression(), r, "starts", false));
        }
        if (op.equals("ends") && word(t, opAt + 1).equals("with")) {
            return parseMultiExprCond(t, opAt + 2, false,
                    r -> new Condition.TextMatch(left.expression(), r, "ends", false));
        }
        if (op.equals("does") && word(t, opAt + 1).equals("not")) {
            String verb = word(t, opAt + 2);
            if (verb.equals("contain")) {
                return parseMultiExprCond(t, opAt + 3, true,
                        r -> new Condition.TextMatch(left.expression(), r, "contains", true));
            }
            if (verb.equals("start") && word(t, opAt + 3).equals("with")) {
                return parseMultiExprCond(t, opAt + 4, true,
                        r -> new Condition.TextMatch(left.expression(), r, "starts", true));
            }
            if (verb.equals("end") && word(t, opAt + 3).equals("with")) {
                return parseMultiExprCond(t, opAt + 4, true,
                        r -> new Condition.TextMatch(left.expression(), r, "ends", true));
            }
        }
        if (op.equals(">") || op.equals("<") || op.equals(">=") || op.equals("<=") || op.equals("=") || op.equals("==") || op.equals("!=")) {
            String cmp = op.equals("!=") ? "is not" : (op.equals("=") || op.equals("==") ? "is" : op);
            boolean neg = cmp.equals("is not");
            return parseMultiExprCond(t, opAt + 1, neg,
                    r -> new Condition.Compare(left.expression(), cmp, r));
        }
        throw new ParseException("неизвестный оператор условия: " + (op.isEmpty() ? t.get(Math.min(opAt, t.size() - 1)).value() : op)
                + " (is, is not, >, <, >=, <=, is set, contains, starts with, ends with, and, or)");
    }

    /**
     * Разбирает правую часть условия с поддержкой перечисления значений через or / and:
     * - if arg-1 is "on" or "off":
     * - if arg-1 is not "or" or "on":   (при отрицании "is not A or B" означает "не A и не B")
     * - if message contains "a" or "b" or "c":
     * Останавливается, если после or / and идёт полноценное новое условие со своим оператором.
     */
    private ParsedCond parseMultiExprCond(List<Token> t, int start, boolean negate,
                                          java.util.function.Function<Expression, Condition> factory) throws ParseException {
        ExprParser.Parsed first = ExprParser.parse(t, start);
        Condition acc = factory.apply(first.expression());
        int cur = first.end();
        while (cur < t.size()) {
            String conj = word(t, cur);
            if (!conj.equals("or") && !conj.equals("and")) {
                break;
            }
            if (cur + 1 >= t.size() || isStandaloneCondition(t, cur + 1)) {
                break;
            }
            ExprParser.Parsed next = ExprParser.parse(t, cur + 1);
            Condition nextCond = factory.apply(next.expression());
            // Для отрицаний ("is not A or B") оба союза or/and означают конъюнкцию ("ни A, ни B").
            boolean useAnd = negate || conj.equals("and");
            acc = useAnd ? new Condition.And(acc, nextCond) : new Condition.Or(acc, nextCond);
            cur = next.end();
        }
        return new ParsedCond(acc, cur);
    }

    /** Проверяет, начинается ли с индекса pos самостоятельное условие (со своим оператором is, has, >, contains...). */
    private boolean isStandaloneCondition(List<Token> t, int pos) {
        if (pos >= t.size()) {
            return false;
        }
        if (!t.get(pos).quoted() && t.get(pos).value().startsWith("{") && t.get(pos).value().endsWith("::*}")) {
            String w1 = word(t, pos + 1);
            if (w1.equals("contains") || w1.equals("does") || w1.equals("has") || w1.equals("is")) {
                return true;
            }
        }
        if (!t.get(pos).quoted() && word(t, pos).equals("group") && !word(t, pos + 1).equals("of") && pos + 2 < t.size()) {
            String h = word(t, pos + 2);
            if (h.equals("has") || h.equals("does")) {
                return true;
            }
        }
        if (!t.get(pos).quoted()) {
            try {
                TargetParser.Parsed tp = TargetParser.parse(t, pos);
                if (tp != null) {
                    String h = word(t, tp.end());
                    if (h.equals("is") || h.equals("has") || h.equals("does")) {
                        return true;
                    }
                }
            } catch (ParseException ignored) {
            }
        }
        try {
            ExprParser.Parsed exp = ExprParser.parse(t, pos);
            String op = word(t, exp.end());
            return op.equals("is") || op.equals("contains") || op.equals("starts") || op.equals("ends")
                    || op.equals("does") || op.equals(">") || op.equals("<") || op.equals(">=")
                    || op.equals("<=") || op.equals("=") || op.equals("==") || op.equals("!=");
        } catch (ParseException e) {
            return false;
        }
    }

    /** player is sneaking / is holding / has item / is in creative / is in world ... или null, если это обычное сравнение. */
    private ParsedCond tryTargetCondition(List<Token> t, TargetParser.Parsed target) throws ParseException {
        int k = target.end();
        String head = word(t, k);
        if (head.equals("has") || (head.equals("does") && word(t, k + 1).equals("not") && word(t, k + 2).equals("have"))) {
            boolean negate = head.equals("does");
            int at = negate ? k + 3 : k + 1;
            if (!negate && word(t, at).equals("no")) {
                negate = true;
                at++;
            }
            if (word(t, at).equals("a") || word(t, at).equals("an") || word(t, at).equals("any")) {
                at++;
            }
            if (word(t, at).equals("screen") || word(t, at).equals("gui")) {
                int next = at + 1;
                String title = null;
                if (next < t.size() && t.get(next).quoted()) {
                    title = t.get(next).value();
                    next++;
                }
                if (word(t, next).equals("open") || word(t, next).equals("opened")) {
                    next++;
                }
                return new ParsedCond(new Condition.ScreenOpen(target.target(), title, negate), finish(t, next));
            }
            if (word(t, at).equals("open") && (word(t, at + 1).equals("screen") || word(t, at + 1).equals("gui"))) {
                int next = at + 2;
                String title = null;
                if (next < t.size() && t.get(next).quoted()) {
                    title = t.get(next).value();
                    next++;
                }
                return new ParsedCond(new Condition.ScreenOpen(target.target(), title, negate), finish(t, next));
            }
            if (word(t, at).equals("permission")) {
                if (at + 1 >= t.size()) {
                    throw new ParseException("need: player has permission <0-4 or \"node\">",
                            "нужно: player has permission <0-4 или \"узел\">");
                }
                Token perm = t.get(at + 1);
                if (!perm.quoted() && perm.value().matches("[0-4]")) {
                    return new ParsedCond(new Condition.Permission(target.target(), Integer.parseInt(perm.value()), null, negate),
                            finish(t, at + 2));
                }
                String node = perm.value();
                if (!node.matches("[A-Za-z0-9_.*-]+")) {
                    throw new ParseException("the permission level is 0-4, or a LuckPerms node",
                            "уровень прав — число от 0 до 4, либо узел LuckPerms");
                }
                return new ParsedCond(new Condition.Permission(target.target(), 0, node, negate), finish(t, at + 2));
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
        if (s.equals("banned")) {
            return new ParsedCond(new Condition.Banned(target.target(), negate), finish(t, n + 1));
        }
        if (s.equals("holding")) {
            if (n + 1 >= t.size()) {
                throw new ParseException("нужно: player is holding <предмет>");
            }
            return new ParsedCond(new Condition.Holding(target.target(), t.get(n + 1).value(), negate), finish(t, n + 2));
        }
        if (s.equals("in")) {
            String next = word(t, n + 1);
            if (next.equals("screen") || next.equals("gui")) {
                int after = n + 2;
                String title = null;
                if (after < t.size() && t.get(after).quoted()) {
                    title = t.get(after).value();
                    after++;
                }
                return new ParsedCond(new Condition.ScreenOpen(target.target(), title, negate), finish(t, after));
            }
            if (next.equals("group")) {
                ParsedCond multi = parseMultiExprCond(t, n + 2, negate,
                        r -> new Condition.InGroup(target.target(), r, negate));
                return new ParsedCond(multi.condition(), finish(t, multi.end()));
            }
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

    /** wait 2 seconds / wait 10 ticks / wait 1 minute — приостанавливает выполнение блока. */
    private Action parseWait(Line line) throws ParseException {
        List<Token> t = Tokenizer.tokenize(line.text());
        if (t.size() < 2) {
            throw new ParseException("нужно: wait <число> seconds|ticks|minutes");
        }
        String unit = word(t, t.size() - 1);
        int per = switch (unit) {
            case "t", "tick", "ticks" -> 1;
            case "s", "second", "seconds", "sec", "secs" -> 20;
            case "m", "minute", "minutes", "min", "mins" -> 1200;
            default -> throw new ParseException("единица времени: ticks, seconds или minutes");
        };
        ExprParser.Parsed amount = ExprParser.parse(t, 1);
        if (amount.end() != t.size() - 1) {
            throw new ParseException("нужно: wait <число> seconds|ticks|minutes");
        }
        return new Actions.Wait(amount.expression(), per);
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

    /** script, arg-1, player-argument, параметр текущей функции — одно слово, которое не является текстом. */
    @Nullable
    static Expression namedWord(String raw) {
        String w = raw.toLowerCase(Locale.ROOT);
        if (localType(w) != null) {
            String key = w;
            if (key.length() > 2 && key.startsWith("%") && key.endsWith("%")) {
                key = key.substring(1, key.length() - 1);
            }
            return new Expression.Local(key);
        }
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

    private static boolean isVariableToken(Token token) {
        if (token.quoted()) {
            return false;
        }
        String value = token.value();
        return value.length() > 2 && value.startsWith("{") && value.endsWith("}");
    }

    private static ParseException addSyntax(boolean remove) {
        return remove
                ? new ParseException("need: remove <value> from {name}", "нужно: remove <значение> from {имя}")
                : new ParseException("need: add <value> to {name}", "нужно: add <значение> to {имя}");
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

        if (hasBlock && !head.equals("open") && !head.equals("create") && !head.equals("update")) {
            throw new ParseException("двоеточие допустимо только у 'open screen', 'update screen', 'create hud', 'loop', 'if' и 'else'");
        }
        if (!hasBlock && !children.isEmpty()) {
            throw new ParseException("у этого действия не может быть вложенных строк");
        }

        if (head.equals("leash") || head.equals("tether") || head.equals("tie")) {
            return parseLeashAction(t);
        }
        if (head.equals("unleash") || head.equals("untie")) {
            TargetParser.ParsedEntity ent = TargetParser.parseEntity(t, 1);
            if (ent == null || ent.end() != t.size()) {
                throw new ParseException("нужно: unleash <сущность/игрок>");
            }
            return new Effects.UnleashEntity(ent.target());
        }
        if ((head.equals("release") || head.equals("remove") || head.equals("break")) && word(t, 1).equals("leash")) {
            int at = 2;
            if (word(t, at).equals("of") || word(t, at).equals("from")) {
                at++;
            }
            TargetParser.ParsedEntity ent = TargetParser.parseEntity(t, at);
            if (ent == null || ent.end() != t.size()) {
                throw new ParseException("нужно: release leash of <сущность/игрок>");
            }
            return new Effects.UnleashEntity(ent.target());
        }
        if (head.equals("clear") && (word(t, 1).equals("leashes") || (word(t, 1).equals("all") && word(t, 2).equals("leashes")))) {
            return new Effects.ClearLeashes();
        }
        if (head.equals("set") && word(t, 1).equals("leash") && t.size() >= 6) {
            return parseSetLeashProp(t);
        }
        if (head.equals("spawn") && word(t, 1).equals("entity")) {
            return parseSpawnEntity(t);
        }
        if (head.equals("spawn") && word(t, 1).equals("geo")) {
            return parseSpawnGeoEntity(t);
        }
        if (head.equals("spawn") && word(t, 1).equals("npc")) {
            return parseSpawnNpc(t);
        }
        if ((head.equals("remove") || head.equals("delete")) && word(t, 1).equals("npc") && t.size() >= 3) {
            ExprParser.Parsed id = ExprParser.parse(t, 2);
            return new Effects.NpcAction("remove", id.expression(), null, null, null, null, null, null, null, null, null, null, null, null);
        }
        if ((head.equals("clear") || head.equals("remove"))
                && ((word(t, 1).equals("all") && word(t, 2).equals("npcs")) || word(t, 1).equals("npcs"))) {
            return new Effects.NpcAction("clear", null, null, null, null, null, null, null, null, null, null, null, null, null);
        }
        if ((head.equals("clear") || head.equals("remove"))
                && ((word(t, 1).equals("all") && word(t, 2).equals("geo") && word(t, 3).equals("entities"))
                || (word(t, 1).equals("geo") && word(t, 2).equals("entities")))) {
            return new Effects.GeoEntityControl("clear_all", null, null, 0, null);
        }
        if (head.equals("equip") && word(t, 1).equals("npc")) {
            return parseEquipNpc(t);
        }
        if (head.equals("set") && (word(t, 1).equals("skin") || word(t, 1).equals("name"))
                && word(t, 2).equals("of") && word(t, 3).equals("npc")) {
            return parseSetNpcProp(t);
        }
        if (head.equals("make") && word(t, 1).equals("npc")) {
            return parseMakeNpc(t);
        }
        if ((head.equals("play") || head.equals("stop")) && word(t, 1).equals("npc") && word(t, 2).equals("animation")) {
            return parseNpcAnimation(t, head.equals("stop"));
        }
        if ((head.equals("play") || head.equals("stop")) && word(t, 1).equals("geo") && word(t, 2).equals("animation")) {
            return parseGeoEntityAnimation(t, head.equals("stop"));
        }
        if (head.equals("set") && word(t, 1).equals("geo") && (word(t, 2).equals("texture") || word(t, 2).equals("scale"))) {
            return parseSetGeoProp(t);
        }
        if ((head.equals("play") || head.equals("stop")) && word(t, 1).equals("item") && word(t, 2).equals("animation")) {
            return parseGeoItemAnimation(t, head.equals("stop"));
        }
        if ((head.equals("play") || head.equals("stop")) && word(t, 1).equals("block") && word(t, 2).equals("animation")) {
            return parseGeoBlockAnimation(t, head.equals("stop"));
        }
        if (head.equals("attach") && word(t, 1).equals("prop")) {
            return parseAttachProp(t);
        }
        if ((head.equals("detach") || head.equals("remove")) && word(t, 1).equals("prop")) {
            return parseDetachProp(t);
        }
        if (head.equals("clear") && (word(t, 1).equals("props") || (word(t, 1).equals("all") && word(t, 2).equals("props")))) {
            return parseClearProps(t);
        }
        if ((head.equals("play") || head.equals("start")) && (word(t, 1).equals("cutscene") || word(t, 1).equals("camera"))) {
            return parsePlayCutscene(t);
        }
        if (head.equals("move") && word(t, 1).equals("camera")) {
            return parseMoveCamera(t);
        }
        if (head.equals("stop") && word(t, 1).equals("cutscene")) {
            return parseStopCutscene(t);
        }
        if ((head.equals("play") || head.equals("stop"))
                && (word(t, 1).equals("animation") || (word(t, 1).equals("player") && word(t, 2).equals("animation")))) {
            return head.equals("play") ? parsePlayAnimation(t) : parseStopAnimation(t);
        }
        // Сначала таблица эффектов (set gamemode of player..., heal player by 2, send title ...).
        Action effect = EffectPatterns.tryParse(t);
        if (effect != null) {
            return effect;
        }
        if (!hasBlock) {
            Action call = tryFunctionCall(text.strip());
            if (call != null) {
                return call;
            }
        }
        if (word(t, 1).equals("nbt") && (head.equals("set") || head.equals("remove") || head.equals("delete") || head.equals("clear"))) {
            return parseNbt(t, head.equals("set"));
        }
        if ((head.equals("set") || head.equals("clear") || head.equals("delete")) && word(t, 1).equals("slot")) {
            return head.equals("set") ? SlotSyntax.parseSet(t) : SlotSyntax.parseClear(t);
        }
        Action luck = LuckPermsSyntax.parse(t);
        if (luck != null) {
            return luck;
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
                // remove index <key> from {list::*}
                if (head.equals("remove") && (word(t, 1).equals("index") || word(t, 1).equals("key"))) {
                    int fromIdx = -1;
                    for (int k = 2; k < t.size(); k++) {
                        if (word(t, k).equals("from")) {
                            fromIdx = k;
                            break;
                        }
                    }
                    if (fromIdx < 3 || fromIdx + 2 != t.size()) {
                        throw new ParseException("нужно: remove index <ключ> from {список::*}");
                    }
                    ExprParser.Parsed keyExpr = ExprParser.parse(t, 2);
                    if (keyExpr.end() != fromIdx) {
                        throw new ParseException("нужно: remove index <ключ> from {список::*}");
                    }
                    return new Actions.RemoveIndex(keyExpr.expression(), variableName(t, fromIdx + 1));
                }
                // add <значение> to {имя} / remove <значение> from {имя}
                // Слово to/from можно не писать, если последнее — переменная: add targeted block {list::*}
                boolean remove = head.equals("remove");
                String sep = remove ? "from" : "to";
                int at = -1;
                for (int i = 1; i < t.size(); i++) {
                    if (word(t, i).equals(sep)) {
                        at = i;
                        break;
                    }
                }
                boolean bare = false;
                if (at < 0 && t.size() >= 3 && isVariableToken(t.get(t.size() - 1))) {
                    at = t.size() - 1;
                    bare = true;
                }
                if (at < 0) {
                    throw addSyntax(remove);
                }
                ExprParser.Parsed amount = ExprParser.parse(t, 1);
                int valueEnd = bare ? at : at;
                if (amount.end() != valueEnd || (!bare && at + 2 != t.size()) || (bare && at + 1 != t.size())) {
                    throw addSyntax(remove);
                }
                return new Actions.AddToVariable(variableName(t, bare ? at : at + 1), amount.expression(), remove);
            }
            case "delete", "clear" -> {
                // delete {name} / clear {list::*}
                return new Actions.DeleteVariable(variableName(t, 1));
            }
            case "sort" -> {
                String listName = variableName(t, 1);
                boolean desc = false;
                if (t.size() > 2) {
                    String dir = word(t, 2);
                    if (dir.equals("descending") || dir.equals("desc")) {
                        desc = true;
                    } else if (!dir.equals("ascending") && !dir.equals("asc")) {
                        throw new ParseException("нужно: sort {список::*} [ascending|descending]");
                    }
                }
                return new Actions.SortList(listName, desc);
            }
            case "reverse" -> {
                return new Actions.ReverseList(variableName(t, 1));
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
                for (ParsedElement pe : parseScreenBody(children)) {
                    elements.add(pe.element());
                }
                return new Actions.OpenScreen(target, title, elements);
            }
            case "update" -> {
                // update screen ["Заголовок"] [for|to <цель>]:
                expectWord(t, 1, "screen");
                int idx = 2;
                String title = null;
                if (idx < t.size() && t.get(idx).quoted()) {
                    title = t.get(idx).value();
                    idx++;
                }
                Target target = new Target.Self();
                if (idx < t.size()) {
                    String w = word(t, idx);
                    if (!w.equals("for") && !w.equals("to")) {
                        throw new ParseException("ожидается 'for <цель>', например update screen for player:");
                    }
                    target = TargetParser.parseWhole(t, idx + 1, "update screen for player:");
                }
                if (!hasBlock || children.isEmpty()) {
                    throw new ParseException("у 'update screen' нет элементов: допишите двоеточие и элементы с отступом");
                }
                List<ScreenElement> elements = new ArrayList<>();
                for (ParsedElement pe : parseScreenBody(children)) {
                    elements.add(pe.element());
                }
                return new Actions.UpdateScreen(target, title, elements);
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
                for (ParsedElement pe : parseScreenBody(children)) {
                    if (pe.element() instanceof ButtonElement
                            || pe.element() instanceof com.scriptcraft.screen.InputElement
                            || pe.element() instanceof com.scriptcraft.screen.SliderElement
                            || pe.element() instanceof com.scriptcraft.screen.ToggleElement) {
                        throw new ParseException("интерактивные элементы (button, input, slider, toggle) в HUD не поддерживаются — используйте open screen");
                    }
                    String id = pe.id();
                    if (id.isEmpty()) {
                        do {
                            id = group + "#" + (++auto);
                        } while (ids.contains(id));
                    }
                    if (!ids.add(id)) {
                        throw new ParseException("элемент с именем '" + id + "' уже есть в этом HUD");
                    }
                    entries.add(new HudEntry(id, group, true, pe.element()));
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

    /** on key e: / on open inventory: — одно имя, фильтр хранится в обработчике. */
    private void parseKeyed(Line headerLine, ScriptEvent event, @Nullable String filter, List<Line> body) {
        if (filter == null) {
            return;
        }
        events.computeIfAbsent(event, e -> new ArrayList<>())
                .add(new EventHandler(currentFile, parseActions(body), false, filter));
    }

    @Nullable
    private String keyFilter(Line header, String rest) {
        try {
            List<Token> t = Tokenizer.tokenize(rest);
            int idx = 0;
            String w0 = word(t, idx);
            if (w0.equals("press") || w0.equals("release") || w0.equals("hold")) {
                idx++;
            }
            if (idx >= t.size()) {
                error(header, "не указана клавиша (" + KeyNames.names() + ")");
                return null;
            }
            String key = word(t, idx);
            idx++;
            if (key.equals("any") && idx < t.size() && word(t, idx).equals("key")) {
                idx++;
            }
            if (!KeyNames.known(key)) {
                error(header, "неизвестная клавиша '" + key + "' (" + KeyNames.names() + ")");
                return null;
            }
            String screenFilter = null;
            if (idx < t.size()) {
                if (word(t, idx).equals("in") && (word(t, idx + 1).equals("screen") || word(t, idx + 1).equals("gui"))) {
                    idx += 2;
                    if (idx < t.size() && t.get(idx).quoted()) {
                        screenFilter = t.get(idx).value();
                        idx++;
                    } else {
                        screenFilter = "*";
                    }
                }
                if (idx < t.size()) {
                    error(header, "лишний текст в заголовке события клавиши: " + t.get(idx).value());
                    return null;
                }
            }
            return screenFilter == null ? key : key + "@" + screenFilter;
        } catch (ParseException e) {
            error(header, e.getMessage());
            return null;
        }
    }

    @Nullable
    private String screenEventFilter(Line header, String rawRest) {
        try {
            List<Token> t = Tokenizer.tokenize(rawRest);
            // screen open ["Title"] | open screen ["Title"] | gui open ["Title"]
            if (t.size() == 2) {
                return "*";
            }
            if (t.size() == 3 && t.get(2).quoted()) {
                return t.get(2).value();
            }
            error(header, "нужно: on screen open [\"Заголовок\"]:  или  on screen close [\"Заголовок\"]:");
            return null;
        } catch (ParseException e) {
            error(header, e.getMessage());
            return null;
        }
    }

    @Nullable
    private String guiChangeFilter(Line header, String rawRest) {
        try {
            List<Token> t = Tokenizer.tokenize(rawRest);
            // gui change ["id"] | input ["id"] | slider ["id"] | toggle ["id"]
            int start = (word(t, 0).equals("gui") || word(t, 0).equals("screen")) ? 2 : 1;
            if (t.size() == start) {
                return "*";
            }
            if (t.size() == start + 1) {
                return t.get(start).value().strip();
            }
            error(header, "нужно: on gui change [\"id\"]:  или  on input [\"id\"]:  или  on slider [\"id\"]:  или  on toggle [\"id\"]:");
            return null;
        } catch (ParseException e) {
            error(header, e.getMessage());
            return null;
        }
    }

    @Nullable
    private String menuFilter(Line header, String rest) {
        String name = rest.toLowerCase(Locale.ROOT);
        if (name.startsWith("menu ")) {
            name = name.substring(5).strip();
        }
        if (!MenuNames.known(name)) {
            error(header, "неизвестное меню '" + name + "' (inventory, chest, barrel, crafting, furnace, anvil, enchanting, villager, any)");
            return null;
        }
        return name;
    }

    @Nullable
    private String npcEventFilter(Line header, String rawRest) {
        try {
            List<Token> t = Tokenizer.tokenize(rawRest);
            int start = word(t, 0).equals("right") || word(t, 0).equals("left") ? 3 : 2;
            if (t.size() <= start) {
                return "*";
            }
            if (t.size() == start + 1) {
                return t.get(start).value().strip();
            }
            error(header, "нужно: on npc click [\"id\"]:  или  on npc attack [\"id\"]:");
            return null;
        } catch (ParseException e) {
            error(header, e.getMessage());
            return null;
        }
    }

    /** Строки элементов open screen / create hud. Вложенные строки есть только у list. */
    private List<ParsedElement> parseScreenBody(List<Line> body) {
        List<ParsedElement> out = new ArrayList<>();
        if (body.isEmpty()) {
            return out;
        }
        int base = body.get(0).indent();
        int i = 0;
        while (i < body.size()) {
            Line line = body.get(i);
            int end = blockEnd(body, i);
            List<Line> nested = body.subList(i + 1, end);
            if (line.indent() != base) {
                error(line, "неверный отступ");
            } else {
                try {
                    String text = line.text();
                    if (text.endsWith(":")) {
                        text = text.substring(0, text.length() - 1).strip();
                    }
                    String kind = text.toLowerCase(Locale.ROOT);
                    int space = kind.indexOf(' ');
                    if (space >= 0) {
                        kind = kind.substring(0, space);
                    }
                    if (kind.equals("list")) {
                        out.add(parseList(line, text, nested));
                    } else if (!nested.isEmpty()) {
                        error(nested.get(0), "вложенные строки бывают только у list");
                    } else {
                        out.addAll(parseElements(line));
                    }
                } catch (ParseException e) {
                    error(line, e.getMessage());
                }
            }
            i = end;
        }
        return out;
    }

    private ParsedElement parseList(Line line, String text, List<Line> nested) throws ParseException {
        List<Token> t = Tokenizer.tokenize(text);
        int idx = 1;
        boolean horizontal = false;
        boolean select = false;
        boolean hidden = false;
        boolean disabled = false;
        boolean noScrollbar = false;
        String header = "";
        String from = "";
        String id = "";
        String tooltip = "";
        String emptyText = "";
        String columnWidths = "";
        String hoverSound = "";
        float hoverVolume = 0.45f;
        float hoverPitch = 1.0f;
        String hoverAction = "";
        int rowHeight = ListElement.ROW;
        int spacing = 0;
        float rowScale = 1.0f;
        byte align = ListElement.ALIGN_LEFT;
        int bgColor = ListElement.DEFAULT_BG;
        int borderColor = ListElement.DEFAULT_BORDER;
        int x = 0, y = 0, w = 120, h = 140, layer = 0;
        int color = 0xFFFFFFFF;
        Anchor anchor = Anchor.CENTER;
        while (idx < t.size()) {
            String opt = word(t, idx);
            switch (opt) {
                case "vertical" -> {
                    horizontal = false;
                    idx++;
                }
                case "horizontal" -> {
                    horizontal = true;
                    idx++;
                }
                case "select" -> {
                    select = true;
                    idx++;
                }
                case "disabled", "inactive" -> {
                    if (idx + 1 < t.size() && (word(t, idx + 1).equals("true") || word(t, idx + 1).equals("false"))) {
                        disabled = word(t, idx + 1).equals("true");
                        idx += 2;
                    } else {
                        disabled = true;
                        idx++;
                    }
                }
                case "active", "enabled" -> {
                    if (idx + 1 < t.size() && (word(t, idx + 1).equals("true") || word(t, idx + 1).equals("false"))) {
                        disabled = word(t, idx + 1).equals("false");
                        idx += 2;
                    } else {
                        disabled = false;
                        idx++;
                    }
                }
                case "from" -> {
                    if (idx + 1 >= t.size() || !t.get(idx + 1).value().endsWith("::*}")) {
                        throw new ParseException("нужно: from {имя::*}");
                    }
                    String token = t.get(idx + 1).value();
                    from = token.substring(1, token.length() - 1);
                    idx += 2;
                }
                case "header" -> {
                    header = quoted(t, idx + 1);
                    idx += 2;
                }
                case "empty_text", "empty-text", "empty", "placeholder" -> {
                    emptyText = quoted(t, idx + 1);
                    idx += 2;
                }
                case "columns", "widths" -> {
                    columnWidths = quoted(t, idx + 1);
                    idx += 2;
                }
                case "row_height", "row-height", "item_height", "item-height", "row_size", "row-size" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: row_height <число>");
                    rowHeight = Math.max(8, Math.min(120, parseInt(t.get(idx + 1).value())));
                    idx += 2;
                }
                case "spacing", "gap" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: spacing <число>");
                    spacing = Math.max(0, Math.min(32, parseInt(t.get(idx + 1).value())));
                    idx += 2;
                }
                case "scale", "row_scale", "row-scale", "text_scale", "text-scale" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: scale <число>");
                    rowScale = (float) Math.max(0.5, Math.min(3.0, parseDouble(t.get(idx + 1).value())));
                    idx += 2;
                }
                case "align" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: align left|center|right");
                    align = ListElement.parseAlign(t.get(idx + 1).value(), ListElement.ALIGN_LEFT);
                    idx += 2;
                }
                case "centered" -> {
                    align = ListElement.ALIGN_CENTER;
                    idx++;
                }
                case "background", "bg" -> {
                    if (idx + 1 < t.size() && word(t, idx + 1).equals("none")) {
                        bgColor = 0;
                    } else {
                        bgColor = parseColor(idx + 1 < t.size() ? t.get(idx + 1).value() : "");
                    }
                    idx += 2;
                }
                case "border" -> {
                    if (idx + 1 < t.size() && word(t, idx + 1).equals("none")) {
                        borderColor = 0;
                    } else {
                        borderColor = parseColor(idx + 1 < t.size() ? t.get(idx + 1).value() : "");
                    }
                    idx += 2;
                }
                case "at" -> {
                    if (idx + 2 >= t.size()) {
                        throw new ParseException("нужно: at X Y");
                    }
                    x = parseInt(t.get(idx + 1).value());
                    y = parseInt(t.get(idx + 2).value());
                    idx += 3;
                }
                case "size" -> {
                    Matcher m = SIZE.matcher(idx + 1 < t.size() ? t.get(idx + 1).value() : "");
                    if (!m.matches()) {
                        throw new ParseException("нужно: size ШИРИНАxВЫСОТА, например 130x160");
                    }
                    w = Integer.parseInt(m.group(1));
                    h = Integer.parseInt(m.group(2));
                    idx += 2;
                }
                case "anchor" -> {
                    Anchor a = Anchor.byName(idx + 1 < t.size() ? t.get(idx + 1).value() : "");
                    if (a == null) {
                        throw new ParseException("неизвестный anchor");
                    }
                    anchor = a;
                    idx += 2;
                }
                case "layer" -> {
                    if (idx + 1 >= t.size()) {
                        throw new ParseException("нужно: layer N");
                    }
                    layer = parseInt(t.get(idx + 1).value());
                    idx += 2;
                }
                case "color" -> {
                    color = parseColor(idx + 1 < t.size() ? t.get(idx + 1).value() : "");
                    idx += 2;
                }
                case "id" -> {
                    if (idx + 1 >= t.size()) {
                        throw new ParseException("нужно: id имя");
                    }
                    id = EffectPatterns.hudName(t.get(idx + 1).value());
                    idx += 2;
                }
                case "hidden" -> {
                    hidden = true;
                    idx++;
                }
                case "scrollbar", "slider" -> {
                    noScrollbar = false;
                    idx++;
                }
                case "no" -> {
                    String nextOpt = word(t, idx + 1);
                    if (!nextOpt.equals("scrollbar") && !nextOpt.equals("slider")) {
                        throw new ParseException("ожидается 'no scrollbar' или 'no slider'");
                    }
                    noScrollbar = true;
                    idx += 2;
                }
                case "tooltip", "hover" -> {
                    tooltip = quoted(t, idx + 1);
                    idx += 2;
                }
                case "hover_sound", "hover-sound", "hsound" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: hover_sound <звук>");
                    hoverSound = t.get(idx + 1).value().strip();
                    int cur = idx + 2;
                    if (cur < t.size() && word(t, cur).equals("with")) cur++;
                    if (cur + 1 < t.size() && (word(t, cur).equals("volume") || word(t, cur).equals("vol"))) {
                        hoverVolume = (float) parseDouble(t.get(cur + 1).value());
                        cur += 2;
                        if (cur + 1 < t.size() && word(t, cur).equals("pitch")) {
                            hoverPitch = (float) parseDouble(t.get(cur + 1).value());
                            cur += 2;
                        }
                    } else if (cur + 1 < t.size() && word(t, cur).equals("pitch")) {
                        hoverPitch = (float) parseDouble(t.get(cur + 1).value());
                        cur += 2;
                    }
                    idx = cur;
                }
                case "hover_volume", "hover-volume", "hvol" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: hover_volume <число>");
                    hoverVolume = (float) parseDouble(t.get(idx + 1).value());
                    idx += 2;
                }
                case "hover_pitch", "hover-pitch", "hpitch" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: hover_pitch <число>");
                    hoverPitch = (float) parseDouble(t.get(idx + 1).value());
                    idx += 2;
                }
                case "on_hover", "on-hover", "hover_run", "hover-run" -> {
                    ParsedScreenRun parsedHover = parseScreenRun(line, t, idx, false);
                    hoverAction = parsedHover.action();
                    idx = parsedHover.nextIdx();
                }
                default -> throw new ParseException("у list нет параметра '" + opt + "' (from, vertical, horizontal, select, header, row_height, spacing, scale, align, disabled, active, empty_text, columns, background, border, at, size, anchor, layer, color, id, hidden, tooltip, hover_sound, on_hover, no scrollbar)");
            }
        }
        Layout layout = new Layout(x, y, w, h, anchor, color, layer);
        String listExtra = com.scriptcraft.screen.ScreenMeta.appendHoverTags(
                disabled ? "dis1" : "", hoverSound, hoverVolume, hoverPitch, hoverAction);
        String packedHeader = com.scriptcraft.screen.ScreenMeta.pack(id, false, noScrollbar ? "noslider" : "", hidden, tooltip,
                listExtra, header);
        if (!from.isEmpty()) {
            if (nested.size() > 1) {
                throw new ParseException("у list from одна строка-шаблон: row \"%loop-index%\"");
            }
            ParsedRowResult template = nested.isEmpty()
                    ? new ParsedRowResult(new ListElement.Row("%loop-index% x%loop-value%", ""), "")
                    : parseRow(nested.get(0));
            return new ParsedElement(id, new VariableListElement(
                    layout, packedHeader, horizontal, select, disabled, rowHeight, spacing, rowScale,
                    align, bgColor, borderColor, emptyText, columnWidths,
                    from, template.row(), template.rawBarExpr()));
        }
        List<ListElement.Row> rows = new ArrayList<>();
        if (!nested.isEmpty()) {
            int base = nested.get(0).indent();
            int i = 0;
            while (i < nested.size()) {
                Line rowLine = nested.get(i);
                int rowEnd = blockEnd(nested, i);
                if (rowLine.indent() != base) {
                    error(rowLine, "неверный отступ");
                } else if (rowEnd > i + 1) {
                    error(nested.get(i + 1), "у строки списка нет вложенных строк");
                } else {
                    rows.add(parseRow(rowLine).row());
                }
                i = rowEnd;
            }
        }
        return new ParsedElement(id, new ListElement(layout, packedHeader, horizontal, select, disabled,
                rowHeight, spacing, rowScale, align, bgColor, borderColor, emptyText, columnWidths, List.copyOf(rows)));
    }

    private record ParsedRowResult(ListElement.Row row, String rawBarExpr) {
    }

    private ParsedRowResult parseRow(Line line) throws ParseException {
        List<Token> t = Tokenizer.tokenize(line.text());
        String head = word(t, 0);
        if (head.equals("separator") || head.equals("divider")) {
            int idx = 1;
            String label = "";
            if (idx < t.size() && t.get(idx).quoted()) {
                label = t.get(idx).value();
                idx++;
            }
            int sepColor = 0;
            int customH = 0;
            int customW = 0;
            byte align = ListElement.ALIGN_INHERIT;
            while (idx < t.size()) {
                String opt = word(t, idx);
                switch (opt) {
                    case "color" -> {
                        sepColor = parseColor(idx + 1 < t.size() ? t.get(idx + 1).value() : "");
                        idx += 2;
                    }
                    case "height", "h" -> {
                        if (idx + 1 >= t.size()) throw new ParseException("нужно: height <число>");
                        customH = Math.max(2, Math.min(80, parseInt(t.get(idx + 1).value())));
                        idx += 2;
                    }
                    case "width", "w" -> {
                        if (idx + 1 >= t.size()) throw new ParseException("нужно: width <число>");
                        customW = Math.max(2, Math.min(1000, parseInt(t.get(idx + 1).value())));
                        idx += 2;
                    }
                    case "align" -> {
                        if (idx + 1 >= t.size()) throw new ParseException("нужно: align left|center|right");
                        align = ListElement.parseAlign(t.get(idx + 1).value(), ListElement.ALIGN_CENTER);
                        idx += 2;
                    }
                    case "left", "center", "centered", "right" -> {
                        align = ListElement.parseAlign(opt.equals("centered") ? "center" : opt, ListElement.ALIGN_CENTER);
                        idx++;
                    }
                    default -> throw new ParseException("у separator нет параметра '" + opt + "' (color, height, width, align)");
                }
            }
            ListElement.Row sep = new ListElement.Row(
                    ListElement.RowKind.SEPARATOR, label, "", "", 1, "", "", 0,
                    -1f, 0, "", "", customW, customH, align, ListElement.STATE_NORMAL, sepColor, "", "");
            return new ParsedRowResult(sep, "");
        }
        if (head.equals("empty") || head.equals("spacer") || head.equals("blank")) {
            int idx = 1;
            int customH = 0;
            int customW = 0;
            if (idx < t.size() && !t.get(idx).quoted() && t.get(idx).value().matches("\\d+")) {
                customH = Math.max(2, Math.min(160, parseInt(t.get(idx).value())));
                idx++;
            }
            while (idx < t.size()) {
                String opt = word(t, idx);
                switch (opt) {
                    case "height", "h" -> {
                        if (idx + 1 >= t.size()) throw new ParseException("нужно: height <число>");
                        customH = Math.max(2, Math.min(160, parseInt(t.get(idx + 1).value())));
                        idx += 2;
                    }
                    case "width", "w" -> {
                        if (idx + 1 >= t.size()) throw new ParseException("нужно: width <число>");
                        customW = Math.max(2, Math.min(1000, parseInt(t.get(idx + 1).value())));
                        idx += 2;
                    }
                    default -> throw new ParseException("у empty/spacer нет параметра '" + opt + "' (height, width)");
                }
            }
            return new ParsedRowResult(ListElement.Row.empty(customW, customH), "");
        }
        if (!head.equals("row")) {
            throw new ParseException("внутри list пишется row \"текст\" [...], separator [\"текст\"] или empty [высота]");
        }
        int idx = 1;
        String label = "";
        if (idx < t.size() && t.get(idx).quoted()) {
            label = t.get(idx).value();
            idx++;
        }
        String rightText = "";
        String icon = "";
        int iconCount = 1;
        String headSkin = "";
        String badge = "";
        int badgeColor = 0xFFF59E0B;
        float barProgress = -1f;
        String rawBarExpr = "";
        int barColor = 0xFF3B82F6;
        String buttonLabel = "";
        String buttonRawAction = "";
        int customW = 0;
        int customH = 0;
        byte align = ListElement.ALIGN_INHERIT;
        byte state = ListElement.STATE_NORMAL;
        int rowColor = 0;
        String tooltip = "";
        String action = "";
        String rowHoverSound = "";
        float rowHoverVol = 0.45f;
        float rowHoverPitch = 1.0f;
        String rowHoverAct = "";
        boolean keepOpen = false;

        while (idx < t.size()) {
            String opt = word(t, idx);
            switch (opt) {
                case "run", "function" -> {
                    ParsedScreenRun parsedRun = parseScreenRun(line, t, idx, opt.equals("function"));
                    action = parsedRun.action();
                    idx = parsedRun.nextIdx();
                }
                case "close" -> {
                    action = "close";
                    idx++;
                }
                case "keep", "stay" -> {
                    expectWord(t, idx + 1, "open");
                    keepOpen = true;
                    idx += 2;
                }
                case "width", "w" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: width <число>");
                    customW = Math.max(8, Math.min(1000, parseInt(t.get(idx + 1).value())));
                    idx += 2;
                }
                case "height", "h" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: height <число>");
                    customH = Math.max(8, Math.min(160, parseInt(t.get(idx + 1).value())));
                    idx += 2;
                }
                case "align" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: align left|center|right");
                    align = ListElement.parseAlign(t.get(idx + 1).value(), ListElement.ALIGN_LEFT);
                    idx += 2;
                }
                case "left", "center", "centered" -> {
                    align = ListElement.parseAlign(opt.equals("centered") ? "center" : opt, ListElement.ALIGN_LEFT);
                    idx++;
                }
                case "right" -> {
                    if (idx + 1 < t.size() && t.get(idx + 1).quoted()) {
                        rightText = t.get(idx + 1).value();
                        idx += 2;
                    } else {
                        align = ListElement.ALIGN_RIGHT;
                        idx++;
                    }
                }
                case "active", "selected", "current" -> {
                    state = ListElement.STATE_ACTIVE;
                    idx++;
                }
                case "disabled", "inactive" -> {
                    state = ListElement.STATE_DISABLED;
                    idx++;
                }
                case "color", "background", "bg" -> {
                    rowColor = parseColor(idx + 1 < t.size() ? t.get(idx + 1).value() : "");
                    idx += 2;
                }
                case "tooltip", "hover" -> {
                    tooltip = quoted(t, idx + 1);
                    idx += 2;
                }
                case "subtext", "right_text", "right-text" -> {
                    rightText = quoted(t, idx + 1);
                    idx += 2;
                }
                case "icon", "image" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: icon \"<текстура или предмет>\"");
                    icon = t.get(idx + 1).value();
                    idx += 2;
                }
                case "item" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: item <предмет> [количество]");
                    icon = t.get(idx + 1).value();
                    idx += 2;
                    if (idx < t.size() && !t.get(idx).quoted() && t.get(idx).value().matches("\\d+")) {
                        iconCount = Math.max(1, Math.min(64, parseInt(t.get(idx).value())));
                        idx++;
                    }
                }
                case "head", "skin" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: head \"<ник|ссылка|ресурс>\"");
                    headSkin = t.get(idx + 1).value();
                    idx += 2;
                }
                case "badge" -> {
                    badge = quoted(t, idx + 1);
                    idx += 2;
                }
                case "badge_color", "badge-color" -> {
                    badgeColor = parseColor(idx + 1 < t.size() ? t.get(idx + 1).value() : "");
                    idx += 2;
                }
                case "bar", "progress" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: bar <0..100 или V/M>");
                    rawBarExpr = t.get(idx + 1).value();
                    barProgress = com.scriptcraft.script.runtime.VariableLists.parseBarSpec(rawBarExpr);
                    if (barProgress < 0f && !rawBarExpr.contains("%") && !rawBarExpr.contains("{")) {
                        throw new ParseException("ожидается число 0..100 или дробь V/M для bar в row");
                    }
                    if (barProgress < 0f) barProgress = 0f;
                    idx += 2;
                }
                case "bar_color", "bar-color" -> {
                    barColor = parseColor(idx + 1 < t.size() ? t.get(idx + 1).value() : "");
                    idx += 2;
                }
                case "button", "btn" -> {
                    buttonLabel = quoted(t, idx + 1);
                    idx += 2;
                }
                case "button_run", "button-run" -> {
                    ParsedScreenRun parsedRun = parseScreenRun(line, t, idx, false);
                    buttonRawAction = parsedRun.action();
                    idx = parsedRun.nextIdx();
                }
                case "hover_sound", "hover-sound", "hsound" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: hover_sound <звук>");
                    rowHoverSound = t.get(idx + 1).value().strip();
                    int cur = idx + 2;
                    if (cur < t.size() && word(t, cur).equals("with")) cur++;
                    if (cur + 1 < t.size() && (word(t, cur).equals("volume") || word(t, cur).equals("vol"))) {
                        rowHoverVol = (float) parseDouble(t.get(cur + 1).value());
                        cur += 2;
                        if (cur + 1 < t.size() && word(t, cur).equals("pitch")) {
                            rowHoverPitch = (float) parseDouble(t.get(cur + 1).value());
                            cur += 2;
                        }
                    } else if (cur + 1 < t.size() && word(t, cur).equals("pitch")) {
                        rowHoverPitch = (float) parseDouble(t.get(cur + 1).value());
                        cur += 2;
                    }
                    idx = cur;
                }
                case "hover_volume", "hover-volume", "hvol" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: hover_volume <число>");
                    rowHoverVol = (float) parseDouble(t.get(idx + 1).value());
                    idx += 2;
                }
                case "hover_pitch", "hover-pitch", "hpitch" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: hover_pitch <число>");
                    rowHoverPitch = (float) parseDouble(t.get(idx + 1).value());
                    idx += 2;
                }
                case "on_hover", "on-hover", "hover_run", "hover-run" -> {
                    ParsedScreenRun parsedHover = parseScreenRun(line, t, idx, false);
                    rowHoverAct = parsedHover.action();
                    idx = parsedHover.nextIdx();
                }
                default -> throw new ParseException("у row нет параметра '" + opt + "' (run, on_hover, hover_sound, keep open, close, width, height, align, active, disabled, tooltip, icon, item, head, right, badge, bar, button, color)");
            }
        }
        String rowExtra = com.scriptcraft.screen.ScreenMeta.appendHoverTags("", rowHoverSound, rowHoverVol, rowHoverPitch, rowHoverAct);
        rowExtra = com.scriptcraft.screen.ScreenMeta.appendDynamicBarTag(rowExtra, rawBarExpr);
        String packedAct = com.scriptcraft.screen.ScreenMeta.pack("", keepOpen, "", false, "", rowExtra, action);
        String packedBtn = buttonRawAction.isEmpty() ? "" : com.scriptcraft.screen.ScreenMeta.pack("", keepOpen, "", false, buttonRawAction);
        ListElement.Row row = new ListElement.Row(
                ListElement.RowKind.NORMAL, label, rightText, icon, iconCount, headSkin,
                badge, badgeColor, barProgress, barColor, buttonLabel, packedBtn,
                customW, customH, align, state, rowColor, tooltip, packedAct);
        return new ParsedRowResult(row, rawBarExpr);
    }

    private record ParsedScreenRun(String action, int nextIdx) {
    }

    /**
     * Разбирает действие клика элемента экрана (button, text, image, slot, row):
     * - run "/cmd" или run "cmd" -> "/cmd"
     * - run "Func(a, b)" -> "fn:Func(a, b)"
     * - run Func(a, b) или function Func(a, b) или function "Func(a, b)" -> "fn:Func(a, b)"
     */
    private ParsedScreenRun parseScreenRun(Line line, List<Token> t, int optIdx, boolean forceFunction) throws ParseException {
        int i = optIdx + 1;
        if (!forceFunction && i < t.size() && !t.get(i).quoted() && word(t, i).equals("function")) {
            forceFunction = true;
            i++;
        }
        if (i >= t.size()) {
            throw new ParseException("после '" + word(t, optIdx) + "' ожидается команда в кавычках или вызов функции Name(...)");
        }
        if (t.get(i).quoted()) {
            String raw = t.get(i).value().strip();
            if (forceFunction || (!raw.startsWith("/") && raw.matches("[A-Za-z_][A-Za-z0-9_]*\\s*\\(.*\\)"))) {
                String call = raw.contains("(") ? raw : raw + "()";
                validateScreenFunctionCall(call);
                return new ParsedScreenRun("fn:" + call, i + 1);
            }
            return new ParsedScreenRun(raw.startsWith("/") ? raw : "/" + raw, i + 1);
        }
        // Некавычечный вызов функции: run Name(arg1, arg2) или function Name(arg1, arg2)
        String fnName = t.get(i).value().strip();
        if (!fnName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new ParseException("ожидается текст в кавычках или вызов функции Name(...)");
        }
        int cur = i + 1;
        if (cur >= t.size() || !word(t, cur).equals("(")) {
            if (forceFunction) {
                return new ParsedScreenRun("fn:" + fnName + "()", cur);
            }
            throw new ParseException("ожидается '(' после имени функции '" + fnName + "' (или возьмите команду в кавычки)");
        }
        int depth = 0;
        int closeTokenIdx = -1;
        for (int k = cur; k < t.size(); k++) {
            if (!t.get(k).quoted()) {
                String w = t.get(k).value();
                if (w.equals("(")) depth++;
                else if (w.equals(")")) {
                    depth--;
                    if (depth == 0) {
                        closeTokenIdx = k;
                        break;
                    }
                }
            }
        }
        if (closeTokenIdx < 0) {
            throw new ParseException("незакрытая скобка ')' в вызове функции " + fnName);
        }
        String rawCall = extractRawCallFromLine(line.text(), fnName, cur + 1, closeTokenIdx, t);
        validateScreenFunctionCall(rawCall);
        return new ParsedScreenRun("fn:" + rawCall, closeTokenIdx + 1);
    }

    private static String extractRawCallFromLine(String lineText, String fnName, int argStartIdx, int closeIdx, List<Token> t) {
        if (argStartIdx >= closeIdx) {
            return fnName + "()";
        }
        // Находим вызов fnName(...) в исходной строке, чтобы сохранить запятые и кавычки
        Pattern p = Pattern.compile("\\b" + Pattern.quote(fnName) + "\\s*\\(");
        Matcher m = p.matcher(lineText);
        if (m.find()) {
            int openPos = m.end() - 1;
            int depth = 0;
            boolean inQ = false;
            for (int k = openPos; k < lineText.length(); k++) {
                char c = lineText.charAt(k);
                if (c == '"' && (k == 0 || lineText.charAt(k - 1) != '\\')) {
                    inQ = !inQ;
                } else if (!inQ) {
                    if (c == '(') depth++;
                    else if (c == ')') {
                        depth--;
                        if (depth == 0) {
                            return fnName + lineText.substring(openPos, k + 1);
                        }
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder(fnName).append('(');
        for (int k = argStartIdx; k < closeIdx; k++) {
            if (k > argStartIdx) sb.append(", ");
            if (t.get(k).quoted()) {
                sb.append('"').append(t.get(k).value()).append('"');
            } else {
                sb.append(t.get(k).value());
            }
        }
        sb.append(')');
        return sb.toString();
    }

    private static void validateScreenFunctionCall(String call) throws ParseException {
        int open = call.indexOf('(');
        if (open <= 0 || !call.endsWith(")")) {
            throw new ParseException("некорректный вызов функции: " + call);
        }
        String name = call.substring(0, open).strip();
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new ParseException("некорректное имя функции: " + name);
        }
    }

    // ------------------------------------------------------------------ элементы экрана и HUD

    /** Разобранный элемент + имя для HUD ("" = без имени). */
    private record ParsedElement(String id, ScreenElement element) {
    }

    /** Какие параметры допустимы у каждого вида элемента (лишний параметр — понятная ошибка, а не тихое игнорирование). */
    private static final Set<String> ENTITY_OPT_SET = Set.of(
            "at", "size", "anchor", "color", "layer", "id", "run", "function", "close", "keep", "stay", "key", "hotkey",
            "hidden", "disabled", "inactive", "tooltip", "hover", "scale", "entity_size", "entity-size",
            "hscale", "hover_scale", "hover-scale", "follow", "hover_circle", "hover-circle", "hover_color", "hover-color",
            "hover_sound", "hover-sound", "hsound", "hover_volume", "hover-volume", "hvol", "hover_pitch", "hover-pitch", "hpitch",
            "on_hover", "on-hover", "hover_run", "hover-run",
            "skin", "model", "auto", "slim", "alex", "wide", "steve", "classic", "overlay", "no", "without",
            "yaw", "rotate", "rotation", "pitch", "roll", "spin", "look", "clip",
            "baby", "child", "sneak", "sneaking", "crouch", "crouching", "walk", "walking", "nametag", "nbt",
            "with", "weapon", "hand", "mainhand", "offhand", "shield", "helmet", "head", "chestplate", "chest", "leggings", "legs", "boots", "feet"
    );

    private static final Map<String, Set<String>> ELEMENT_OPTIONS = Map.ofEntries(
            Map.entry("text", Set.of("at", "anchor", "color", "shadow", "layer", "id", "run", "function", "close", "keep", "stay", "key", "hotkey", "hidden", "disabled", "inactive", "tooltip", "hover",
                    "scale", "rotate", "rotation", "hscale", "hover_scale", "hover-scale", "follow", "hover_circle", "hover-circle", "hover_color", "hover-color",
                    "hover_sound", "hover-sound", "hsound", "hover_volume", "hover-volume", "hvol", "hover_pitch", "hover-pitch", "hpitch", "on_hover", "on-hover", "hover_run", "hover-run")),
            Map.entry("button", Set.of("at", "size", "anchor", "run", "function", "close", "keep", "stay", "key", "hotkey", "hidden", "disabled", "inactive", "layer", "id", "tooltip", "hover",
                    "hover_sound", "hover-sound", "hsound", "hover_volume", "hover-volume", "hvol", "hover_pitch", "hover-pitch", "hpitch", "on_hover", "on-hover", "hover_run", "hover-run")),
            Map.entry("image", Set.of("at", "size", "texture", "anchor", "color", "layer", "id", "run", "function", "close", "keep", "stay", "key", "hotkey", "hidden", "disabled", "inactive", "tooltip", "hover",
                    "scale", "rotate", "rotation", "hscale", "hover_scale", "hover-scale", "frames", "fps", "follow", "hover_circle", "hover-circle", "hover_color", "hover-color",
                    "hover_sound", "hover-sound", "hsound", "hover_volume", "hover-volume", "hvol", "hover_pitch", "hover-pitch", "hpitch", "on_hover", "on-hover", "hover_run", "hover-run")),
            Map.entry("box", Set.of("at", "size", "anchor", "color", "gradient", "to", "horizontal", "vertical", "border", "border_width", "border-width",
                    "radius", "shadow", "blur", "scale", "rotate", "rotation", "hscale", "hover_scale", "hover-scale", "follow", "hover_circle", "hover-circle", "hover_color", "hover-color",
                    "hover_sound", "hover-sound", "hsound", "hover_volume", "hover-volume", "hvol", "hover_pitch", "hover-pitch", "hpitch", "on_hover", "on-hover", "hover_run", "hover-run",
                    "layer", "id", "run", "function", "close", "keep", "stay", "key", "hotkey", "hidden", "disabled", "inactive", "tooltip", "hover")),
            Map.entry("slot", Set.of("at", "size", "anchor", "color", "no", "layer", "id", "run", "function", "close", "keep", "stay", "key", "hotkey", "hidden", "disabled", "inactive", "tooltip", "hover",
                    "follow", "hover_circle", "hover-circle", "hover_color", "hover-color",
                    "hover_sound", "hover-sound", "hsound", "hover_volume", "hover-volume", "hvol", "hover_pitch", "hover-pitch", "hpitch", "on_hover", "on-hover", "hover_run", "hover-run")),
            Map.entry("bar", Set.of("at", "size", "texture", "anchor", "color", "layer", "id", "value", "max", "direction",
                    "background", "fill", "text", "shadow", "hidden", "tooltip", "hover",
                    "hover_sound", "hover-sound", "hsound", "hover_volume", "hover-volume", "hvol", "hover_pitch", "hover-pitch", "hpitch", "on_hover", "on-hover", "hover_run", "hover-run")),
            Map.entry("hotbar", Set.of("at", "size", "anchor", "color", "no", "layer", "id", "spacing")),
            Map.entry("input", Set.of("at", "size", "anchor", "color", "layer", "id", "value", "placeholder", "hint", "max", "length", "run", "function", "hidden", "disabled", "inactive", "tooltip", "hover",
                    "hover_sound", "hover-sound", "hsound", "hover_volume", "hover-volume", "hvol", "hover_pitch", "hover-pitch", "hpitch", "on_hover", "on-hover", "hover_run", "hover-run")),
            Map.entry("slider", Set.of("at", "size", "anchor", "color", "layer", "id", "from", "to", "min", "max", "value", "step", "run", "function", "hidden", "disabled", "inactive", "tooltip", "hover",
                    "hover_sound", "hover-sound", "hsound", "hover_volume", "hover-volume", "hvol", "hover_pitch", "hover-pitch", "hpitch", "on_hover", "on-hover", "hover_run", "hover-run")),
            Map.entry("toggle", Set.of("at", "size", "anchor", "color", "layer", "id", "value", "checked", "run", "function", "hidden", "disabled", "inactive", "tooltip", "hover",
                    "hover_sound", "hover-sound", "hsound", "hover_volume", "hover-volume", "hvol", "hover_pitch", "hover-pitch", "hpitch", "on_hover", "on-hover", "hover_run", "hover-run")),
            Map.entry("entity", ENTITY_OPT_SET));

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
        boolean entPlayerMode = false;
        switch (kind) {
            case "text", "button", "image" -> {
                main = quoted(t, 1);
                idx = 2;
            }
            case "entity", "player", "skin" -> {
                entPlayerMode = !kind.equals("entity");
                kind = "entity";
                idx = 1;
                if (t.size() > 1) {
                    Token first = t.get(1);
                    if (first.quoted() || !ENTITY_OPT_SET.contains(first.value().toLowerCase(Locale.ROOT))) {
                        main = first.value();
                        idx = 2;
                    }
                }
                if (!entPlayerMode && ("player".equalsIgnoreCase(main) || "minecraft:player".equalsIgnoreCase(main) || "self".equalsIgnoreCase(main))) {
                    entPlayerMode = true;
                }
            }
            case "box", "rect" -> {
                kind = "box";
                idx = 1;
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
            case "input" -> {
                idx = 1;
                if (t.size() > 1 && t.get(1).quoted()) {
                    id = EffectPatterns.hudName(t.get(1).value());
                    idx = 2;
                }
            }
            case "slider" -> {
                idx = 1;
                if (t.size() > 1 && t.get(1).quoted()) {
                    main = t.get(1).value();
                    idx = 2;
                }
            }
            case "toggle", "checkbox" -> {
                kind = "toggle";
                idx = 1;
                if (t.size() > 1 && t.get(1).quoted()) {
                    main = t.get(1).value();
                    idx = 2;
                }
            }
            default -> throw new ParseException("неизвестный элемент: " + kind + " (text, button, image, box, slot, bar, hotbar, list, entity, player, skin, input, slider, toggle)");
        }

        int x = 0, y = 0, w = 0, h = 0, texW = 0, texH = 0, layer = 0, spacing = -1;
        int color = 0xFFFFFFFF;
        boolean colorSet = false, atSet = false, anchorSet = false;
        Anchor anchor = Anchor.CENTER;
        boolean shadow = false, frame = true, keepOpen = false, hidden = false, disabled = false;
        String hotkey = "";
        String tooltip = "";
        // У кнопки клик без run всё равно закрывает экран. У текста, картинки и слота клика нет, пока не написать run или close.
        String action = kind.equals("button") ? "close" : "";
        // Полоса и контролы
        String value = kind.equals("input") ? "" : "0", max = null, fillTexture = "", backgroundTexture = "", barText = "";
        String placeholder = "";
        int inputMaxLen = 64;
        double sliderMin = 0.0, sliderMax = 100.0, sliderStep = 1.0;
        boolean toggleVal = false;
        BarDirection direction = BarDirection.LEFT_TO_RIGHT;
        int backgroundColor = 0x80000000;

        Set<String> allowed = ELEMENT_OPTIONS.get(kind);
        int boxGrad = 0;
        int boxBorder = 0;
        int boxBorderW = 1;
        int boxRadius = 0;
        boolean boxHoriz = false;
        boolean boxBlur = false;
        double elScale = kind.equals("entity") ? 0.0 : 1.0;
        double elHoverScale = 1.0;
        double elRotation = 0.0;
        int spriteFrames = 1;
        int spriteFps = 8;
        boolean followCursor = false;
        int hoverCircle = 0;
        int hoverCircleColor = 0x38BDF8;
        String elHoverSound = "";
        float elHoverVol = 0.45f;
        float elHoverPitch = 1.0f;
        String elHoverAct = "";

        // Параметры 3D-рендера сущности / игрока / скина
        String entSkin = "";
        com.scriptcraft.screen.EntityElement.ModelStyle entModelStyle = com.scriptcraft.screen.EntityElement.ModelStyle.AUTO;
        boolean entOverlay = true;
        float entPitch = 0f, entRoll = 0f, entSpinSpeed = 0f;
        com.scriptcraft.screen.EntityElement.LookMode entLookMode = com.scriptcraft.screen.EntityElement.LookMode.NONE;
        int entLookX = 0, entLookY = 0;
        boolean entClip = false, entBaby = false, entSneaking = false, entWalk = false, entNametag = false;
        String entNbt = "";
        Map<String, String> entEquip = new LinkedHashMap<>();
        while (idx < t.size()) {
            String opt = word(t, idx);
            if (allowed == null || !allowed.contains(opt)) {
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
                case "run", "function" -> {
                    ParsedScreenRun parsedRun = parseScreenRun(line, t, idx, opt.equals("function"));
                    action = parsedRun.action();
                    idx = parsedRun.nextIdx();
                }
                case "close" -> {
                    action = "close";
                    idx++;
                }
                case "keep", "stay" -> {
                    expectWord(t, idx + 1, "open");
                    keepOpen = true;
                    idx += 2;
                }
                case "key", "hotkey" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: key <клавиша>");
                    String k = t.get(idx + 1).value().strip().toLowerCase(Locale.ROOT);
                    if (!KeyNames.known(k) || "any".equals(k)) {
                        throw new ParseException("неизвестная клавиша '" + k + "' (" + KeyNames.names() + ")");
                    }
                    hotkey = k;
                    idx += 2;
                }
                case "hidden" -> {
                    hidden = true;
                    idx++;
                }
                case "disabled", "inactive" -> {
                    disabled = true;
                    idx++;
                }
                case "tooltip", "hover" -> {
                    tooltip = quoted(t, idx + 1);
                    idx += 2;
                }
                case "placeholder", "hint" -> {
                    placeholder = quoted(t, idx + 1);
                    idx += 2;
                }
                case "length" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: length N");
                    inputMaxLen = Math.max(1, Math.min(1024, parseInt(t.get(idx + 1).value())));
                    idx += 2;
                }
                case "from", "min" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: from <число>");
                    sliderMin = parseDouble(t.get(idx + 1).value());
                    idx += 2;
                }
                case "to" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: to <значение>");
                    if (kind.equals("box")) {
                        boxGrad = parseColor(t.get(idx + 1).value());
                    } else {
                        sliderMax = parseDouble(t.get(idx + 1).value());
                    }
                    idx += 2;
                }
                case "step" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: step <число>");
                    sliderStep = Math.max(0.0, parseDouble(t.get(idx + 1).value()));
                    idx += 2;
                }
                case "checked" -> {
                    if (idx + 1 < t.size() && (word(t, idx + 1).equals("true") || word(t, idx + 1).equals("false"))) {
                        toggleVal = word(t, idx + 1).equals("true");
                        idx += 2;
                    } else {
                        toggleVal = true;
                        idx++;
                    }
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
                case "no", "without" -> {
                    String nw = word(t, idx + 1);
                    if (nw.equals("frame") && !kind.equals("entity")) {
                        frame = false;
                        idx += 2;
                    } else if (nw.equals("overlay") && kind.equals("entity")) {
                        entOverlay = false;
                        idx += 2;
                    } else {
                        throw new ParseException(kind.equals("entity") ? "ожидается 'no overlay'" : "ожидается 'no frame'");
                    }
                }
                case "spacing" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: spacing N");
                    spacing = parseInt(t.get(idx + 1).value());
                    idx += 2;
                }
                case "value", "max" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: " + opt + " <значение>");
                    if (kind.equals("input")) {
                        if (opt.equals("value")) {
                            value = t.get(idx + 1).value();
                        } else {
                            inputMaxLen = Math.max(1, Math.min(1024, parseInt(t.get(idx + 1).value())));
                        }
                    } else if (kind.equals("slider")) {
                        if (opt.equals("value")) {
                            value = t.get(idx + 1).value();
                        } else {
                            sliderMax = parseDouble(t.get(idx + 1).value());
                        }
                    } else if (kind.equals("toggle")) {
                        toggleVal = "true".equalsIgnoreCase(t.get(idx + 1).value()) || "on".equalsIgnoreCase(t.get(idx + 1).value()) || "1".equals(t.get(idx + 1).value());
                    } else {
                        String v = barValueRaw(t.get(idx + 1));
                        if (opt.equals("value")) {
                            value = v;
                        } else {
                            max = v;
                        }
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
                case "gradient" -> {
                    boxGrad = parseColor(idx + 1 < t.size() ? t.get(idx + 1).value() : "");
                    idx += 2;
                }
                case "horizontal" -> {
                    boxHoriz = true;
                    idx++;
                }
                case "vertical" -> {
                    boxHoriz = false;
                    idx++;
                }
                case "border" -> {
                    boxBorder = parseColor(idx + 1 < t.size() ? t.get(idx + 1).value() : "");
                    idx += 2;
                }
                case "border_width", "border-width" -> {
                    boxBorderW = parseInt(idx + 1 < t.size() ? t.get(idx + 1).value() : "1");
                    idx += 2;
                }
                case "radius" -> {
                    boxRadius = Math.max(0, Math.min(32, parseInt(idx + 1 < t.size() ? t.get(idx + 1).value() : "0")));
                    idx += 2;
                }
                case "blur" -> {
                    boxBlur = true;
                    idx++;
                }
                case "scale", "entity_size", "entity-size" -> {
                    elScale = parseDouble(idx + 1 < t.size() ? t.get(idx + 1).value() : "1");
                    idx += 2;
                }
                case "hscale", "hover_scale", "hover-scale" -> {
                    elHoverScale = parseDouble(idx + 1 < t.size() ? t.get(idx + 1).value() : "1");
                    idx += 2;
                }
                case "yaw", "rotate", "rotation" -> {
                    elRotation = parseDouble(idx + 1 < t.size() ? t.get(idx + 1).value() : "0");
                    idx += 2;
                }
                case "pitch" -> {
                    entPitch = (float) parseDouble(idx + 1 < t.size() ? t.get(idx + 1).value() : "0");
                    idx += 2;
                }
                case "roll" -> {
                    entRoll = (float) parseDouble(idx + 1 < t.size() ? t.get(idx + 1).value() : "0");
                    idx += 2;
                }
                case "skin" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: skin \"<ник|ссылка|ресурс>\"");
                    entSkin = t.get(idx + 1).value();
                    entPlayerMode = true;
                    idx += 2;
                }
                case "model" -> {
                    com.scriptcraft.screen.EntityElement.ModelStyle ms =
                            com.scriptcraft.screen.EntityElement.ModelStyle.byName(idx + 1 < t.size() ? t.get(idx + 1).value() : "");
                    if (ms == null) throw new ParseException("ожидается model auto, slim (alex) или wide (steve)");
                    entModelStyle = ms;
                    idx += 2;
                }
                case "auto" -> {
                    entModelStyle = com.scriptcraft.screen.EntityElement.ModelStyle.AUTO;
                    idx++;
                }
                case "slim", "alex" -> {
                    entModelStyle = com.scriptcraft.screen.EntityElement.ModelStyle.SLIM;
                    entPlayerMode = true;
                    idx++;
                }
                case "wide", "steve", "classic" -> {
                    entModelStyle = com.scriptcraft.screen.EntityElement.ModelStyle.WIDE;
                    entPlayerMode = true;
                    idx++;
                }
                case "overlay" -> {
                    if (idx + 1 < t.size() && (word(t, idx + 1).equals("true") || word(t, idx + 1).equals("false"))) {
                        entOverlay = word(t, idx + 1).equals("true");
                        idx += 2;
                    } else {
                        entOverlay = true;
                        idx++;
                    }
                }
                case "spin" -> {
                    int cur = idx + 1;
                    float sign = 1f;
                    if (cur < t.size()) {
                        String dir = word(t, cur);
                        if (dir.equals("left") || dir.equals("ccw")) {
                            sign = -1f;
                            cur++;
                        } else if (dir.equals("right") || dir.equals("cw")) {
                            sign = 1f;
                            cur++;
                        } else if (dir.equals("off") || dir.equals("none")) {
                            entSpinSpeed = 0f;
                            idx = cur + 1;
                            break;
                        }
                    }
                    if (cur < t.size() && word(t, cur).equals("speed")) {
                        cur++;
                    }
                    if (cur < t.size() && !t.get(cur).quoted() && NUMBER.matcher(t.get(cur).value()).matches()) {
                        entSpinSpeed = (float) parseDouble(t.get(cur).value()) * sign;
                        idx = cur + 1;
                    } else {
                        entSpinSpeed = 35f * sign;
                        idx = cur;
                    }
                }
                case "look" -> {
                    int cur = idx + 1;
                    if (cur < t.size() && word(t, cur).equals("at")) {
                        cur++;
                    }
                    String lw = word(t, cur);
                    if (lw.equals("cursor") || lw.equals("mouse")) {
                        entLookMode = com.scriptcraft.screen.EntityElement.LookMode.CURSOR;
                        idx = cur + 1;
                    } else if (lw.equals("none") || lw.equals("fixed") || lw.equals("off")) {
                        entLookMode = com.scriptcraft.screen.EntityElement.LookMode.NONE;
                        idx = cur + 1;
                    } else {
                        if (lw.equals("point") || lw.equals("target")) {
                            cur++;
                        }
                        if (cur + 1 >= t.size()) {
                            throw new ParseException("нужно: look cursor  или  look at <X> <Y>");
                        }
                        entLookMode = com.scriptcraft.screen.EntityElement.LookMode.POINT;
                        entLookX = parseInt(t.get(cur).value());
                        entLookY = parseInt(t.get(cur + 1).value());
                        idx = cur + 2;
                    }
                }
                case "clip" -> {
                    entClip = true;
                    idx++;
                }
                case "baby", "child" -> {
                    entBaby = true;
                    idx++;
                }
                case "sneak", "sneaking", "crouch", "crouching" -> {
                    entSneaking = true;
                    idx++;
                }
                case "walk", "walking" -> {
                    entWalk = true;
                    idx++;
                }
                case "nametag" -> {
                    entNametag = true;
                    idx++;
                }
                case "nbt" -> {
                    entNbt = quoted(t, idx + 1);
                    idx += 2;
                }
                case "with" -> {
                    idx++;
                }
                case "weapon", "hand", "mainhand" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: weapon <предмет>");
                    entEquip.put("mainhand", t.get(idx + 1).value());
                    idx += 2;
                }
                case "offhand", "shield" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: offhand <предмет>");
                    entEquip.put("offhand", t.get(idx + 1).value());
                    idx += 2;
                }
                case "helmet", "head" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: helmet <предмет>");
                    entEquip.put("head", t.get(idx + 1).value());
                    idx += 2;
                }
                case "chestplate", "chest" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: chestplate <предмет>");
                    entEquip.put("chest", t.get(idx + 1).value());
                    idx += 2;
                }
                case "leggings", "legs" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: leggings <предмет>");
                    entEquip.put("legs", t.get(idx + 1).value());
                    idx += 2;
                }
                case "boots", "feet" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: boots <предмет>");
                    entEquip.put("feet", t.get(idx + 1).value());
                    idx += 2;
                }
                case "frames" -> {
                    spriteFrames = Math.max(1, Math.min(64, parseInt(idx + 1 < t.size() ? t.get(idx + 1).value() : "1")));
                    idx += 2;
                }
                case "fps" -> {
                    spriteFps = Math.max(1, Math.min(60, parseInt(idx + 1 < t.size() ? t.get(idx + 1).value() : "8")));
                    idx += 2;
                }
                case "follow" -> {
                    expectWord(t, idx + 1, "cursor");
                    followCursor = true;
                    idx += 2;
                }
                case "hover_circle", "hover-circle" -> {
                    hoverCircle = Math.max(0, Math.min(500, parseInt(idx + 1 < t.size() ? t.get(idx + 1).value() : "0")));
                    idx += 2;
                }
                case "hover_color", "hover-color" -> {
                    hoverCircleColor = parseColor(idx + 1 < t.size() ? t.get(idx + 1).value() : "") & 0x00FFFFFF;
                    idx += 2;
                }
                case "hover_sound", "hover-sound", "hsound" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: hover_sound <звук>");
                    elHoverSound = t.get(idx + 1).value().strip();
                    int cur = idx + 2;
                    if (cur < t.size() && word(t, cur).equals("with")) cur++;
                    if (cur + 1 < t.size() && (word(t, cur).equals("volume") || word(t, cur).equals("vol"))) {
                        elHoverVol = (float) parseDouble(t.get(cur + 1).value());
                        cur += 2;
                        if (cur + 1 < t.size() && word(t, cur).equals("pitch")) {
                            elHoverPitch = (float) parseDouble(t.get(cur + 1).value());
                            cur += 2;
                        }
                    } else if (!kind.equals("entity") && cur + 1 < t.size() && word(t, cur).equals("pitch")) {
                        elHoverPitch = (float) parseDouble(t.get(cur + 1).value());
                        cur += 2;
                    }
                    idx = cur;
                }
                case "hover_volume", "hover-volume", "hvol" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: hover_volume <число>");
                    elHoverVol = (float) parseDouble(t.get(idx + 1).value());
                    idx += 2;
                }
                case "hover_pitch", "hover-pitch", "hpitch" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: hover_pitch <число>");
                    elHoverPitch = (float) parseDouble(t.get(idx + 1).value());
                    idx += 2;
                }
                case "on_hover", "on-hover", "hover_run", "hover-run" -> {
                    ParsedScreenRun parsedHover = parseScreenRun(line, t, idx, false);
                    elHoverAct = parsedHover.action();
                    idx = parsedHover.nextIdx();
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
        if (kind.equals("box")) {
            if (!colorSet) {
                color = 0xCC111827;
            }
            if (w <= 0) w = 100;
            if (h <= 0) h = 60;
        }
        if (kind.equals("entity")) {
            if (w <= 0) w = com.scriptcraft.screen.EntityElement.DEFAULT_WIDTH;
            if (h <= 0) h = com.scriptcraft.screen.EntityElement.DEFAULT_HEIGHT;
        }
        StringBuilder extraTags = new StringBuilder();
        if (disabled) {
            extraTags.append("dis1");
        }
        if (!kind.equals("entity") && Math.abs(elScale - 1.0) > 0.001) {
            if (!extraTags.isEmpty()) extraTags.append(';');
            extraTags.append("sc").append(String.format(Locale.ROOT, "%.3f", Math.max(0.1, Math.min(10.0, elScale))));
        }
        if (Math.abs(elHoverScale - 1.0) > 0.001) {
            if (!extraTags.isEmpty()) extraTags.append(';');
            extraTags.append("hs").append(String.format(Locale.ROOT, "%.3f", Math.max(0.1, Math.min(10.0, elHoverScale))));
        }
        if (!kind.equals("entity") && Math.abs(elRotation) > 0.01) {
            if (!extraTags.isEmpty()) extraTags.append(';');
            extraTags.append("rt").append(String.format(Locale.ROOT, "%.2f", elRotation));
        }
        if (spriteFrames > 1) {
            if (!extraTags.isEmpty()) extraTags.append(';');
            extraTags.append("fr").append(spriteFrames).append('x').append(spriteFps);
        }
        if (followCursor) {
            if (!extraTags.isEmpty()) extraTags.append(';');
            extraTags.append("fc1");
        }
        if (hoverCircle > 0) {
            if (!extraTags.isEmpty()) extraTags.append(';');
            extraTags.append("hc").append(hoverCircle).append('x').append(Integer.toHexString(hoverCircleColor & 0x00FFFFFF));
        }
        Layout layout = new Layout(x, y, w, h, anchor, color, layer);
        boolean effectiveKeep = keepOpen || kind.equals("input") || kind.equals("slider") || kind.equals("toggle");
        String finalExtra = com.scriptcraft.screen.ScreenMeta.appendHoverTags(
                extraTags.toString(), elHoverSound, elHoverVol, elHoverPitch, elHoverAct);
        String packedAction = com.scriptcraft.screen.ScreenMeta.pack(id, effectiveKeep, hotkey, hidden, tooltip, finalExtra, action);
        ScreenElement element = switch (kind) {
            case "text" -> new TextElement(layout, main, shadow, packedAction);
            case "button" -> new ButtonElement(layout, main, packedAction);
            case "slot" -> new SlotElement(layout, slotContent, main, count, frame, packedAction);
            case "box" -> {
                String spec = "box:" + (boxGrad == 0 ? "0" : Integer.toHexString(boxGrad))
                        + ":" + (boxBorder == 0 ? "0" : Integer.toHexString(boxBorder))
                        + ":" + (boxHoriz ? "h" : "v") + ":" + boxBorderW
                        + ":" + boxRadius + ":" + (shadow ? "1" : "0") + ":" + (boxBlur ? "1" : "0");
                yield new ImageElement(layout, spec, 0, 0, packedAction);
            }
            case "bar" -> new BarElement(layout, com.scriptcraft.screen.ScreenMeta.packBarId(id, hidden, tooltip, finalExtra),
                    value, max != null ? max : defaultMax(value), direction,
                    backgroundColor, fillTexture, backgroundTexture, texW, texH, barText, shadow);
            case "input" -> new com.scriptcraft.screen.InputElement(layout, value, placeholder, inputMaxLen, packedAction);
            case "slider" -> {
                double initVal;
                try {
                    initVal = Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    initVal = sliderMin;
                }
                yield new com.scriptcraft.screen.SliderElement(layout, main, sliderMin, sliderMax, initVal, sliderStep, packedAction).withValue(initVal);
            }
            case "toggle" -> new com.scriptcraft.screen.ToggleElement(layout, main, toggleVal, packedAction);
            case "entity" -> {
                StringBuilder eqStr = new StringBuilder();
                for (Map.Entry<String, String> ee : entEquip.entrySet()) {
                    if (!eqStr.isEmpty()) eqStr.append(';');
                    eqStr.append(ee.getKey()).append('=').append(ee.getValue());
                }
                yield new com.scriptcraft.screen.EntityElement(
                        layout, entPlayerMode, main, entSkin, entModelStyle, entOverlay,
                        (float) Math.max(0.0, Math.min(400.0, elScale)),
                        (float) elRotation, entPitch, entRoll, entSpinSpeed,
                        entLookMode, entLookX, entLookY,
                        entClip, entBaby, entSneaking, entWalk, entNametag,
                        entNbt, eqStr.toString(), packedAction);
            }
            default -> new ImageElement(layout, main, texW, texH, packedAction);
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
                    new SlotElement(layout, SlotElement.Content.HOTBAR, "", i + 1, frame, "")));
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

    private static double parseDouble(String s) throws ParseException {
        try {
            double v = Double.parseDouble(s);
            if (!Double.isFinite(v)) {
                throw new ParseException("ожидается число, а не '" + s + "'");
            }
            return v;
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

    /** Слова между &lt;типами&gt; — часть пути команды: command /bag take &lt;text&gt;. В %arg-N% они не входят. */
    private void addCommandWords(Line line, String chunk, List<ArgSpec> arguments) {
        String stripped = chunk.strip();
        if (stripped.isEmpty()) {
            return;
        }
        for (String word : stripped.split("\\s+")) {
            if (!word.matches("[a-zA-Z0-9_]+")) {
                error(line, "after the command name write <type>, [type] or a word like take, not '" + word + "'",
                        "после имени команды пишите <тип>, [тип] или слово вроде take, а не '" + word + "'");
                continue;
            }
            if (!arguments.isEmpty() && !arguments.get(arguments.size() - 1).isLiteral()
                    && arguments.get(arguments.size() - 1).optional()) {
                error(line, "a word cannot follow an optional argument",
                        "слово не может стоять после необязательного аргумента");
                continue;
            }
            arguments.add(ArgSpec.literal(word.toLowerCase(Locale.ROOT)));
        }
    }

    private void addTypedArgument(Line line, List<ArgSpec> arguments, boolean optional, String typeName) {
        com.scriptcraft.script.runtime.ArgType type = com.scriptcraft.script.runtime.ArgType.parse(typeName);
        if (type == null) {
            error(line, "unknown argument type '" + typeName.strip()
                            + "' (text, player, offline player, integer, number, boolean, date, world, item)",
                    "неизвестный тип аргумента '" + typeName.strip()
                            + "' (text, player, offline player, integer, number, boolean, date, world, item)");
        } else if (!arguments.isEmpty() && !arguments.get(arguments.size() - 1).isLiteral()
                && arguments.get(arguments.size() - 1).optional() && !optional) {
            error(line, "a required argument cannot follow an optional one",
                    "обязательный аргумент не может стоять после необязательного");
        } else {
            arguments.add(new ArgSpec(type, optional));
        }
    }

    /**
     * play [player] animation "&lt;id&gt;" [on|of &lt;цель&gt;] [for|to &lt;зрители&gt;]
     * [first person | no first person | first person &lt;bool&gt;] [speed N] [fade N]
     */
    private Action parsePlayAnimation(List<Token> t) throws ParseException {
        int idx = word(t, 1).equals("player") ? 3 : 2;
        if (idx >= t.size()) {
            throw new ParseException("need: play animation \"id\" [on player] [for all players] [first person] [speed N] [fade N]",
                    "нужно: play animation \"id\" [on player] [for all players] [first person] [speed N] [fade N]");
        }
        ExprParser.Parsed animParsed = ExprParser.parseAtom(t, idx);
        idx = animParsed.end();
        Target target = null;
        Target viewers = null;
        Expression firstPersonExpr = null;
        boolean firstPersonDefault = true;
        Expression speed = null;
        Expression fade = null;

        while (idx < t.size()) {
            String opt = word(t, idx);
            switch (opt) {
                case "on", "of" -> {
                    TargetParser.Parsed p = TargetParser.parse(t, idx + 1);
                    if (p == null) {
                        throw new ParseException("expected target after '" + opt + "'", "ожидается цель после '" + opt + "'");
                    }
                    target = p.target();
                    idx = p.end();
                }
                case "for", "to" -> {
                    TargetParser.Parsed p = TargetParser.parse(t, idx + 1);
                    if (p == null) {
                        throw new ParseException("expected viewer target after '" + opt + "'", "ожидается цель-зритель после '" + opt + "'");
                    }
                    viewers = p.target();
                    idx = p.end();
                }
                case "speed" -> {
                    if (idx + 1 >= t.size()) {
                        throw new ParseException("need: speed <number>", "нужно: speed <число>");
                    }
                    ExprParser.Parsed p = ExprParser.parseAtom(t, idx + 1);
                    speed = p.expression();
                    idx = p.end();
                }
                case "fade" -> {
                    if (idx + 1 >= t.size()) {
                        throw new ParseException("need: fade <ticks>", "нужно: fade <тики>");
                    }
                    ExprParser.Parsed p = ExprParser.parseAtom(t, idx + 1);
                    fade = p.expression();
                    idx = p.end();
                }
                case "in", "with" -> {
                    if (word(t, idx + 1).equals("first") && word(t, idx + 2).equals("person")) {
                        firstPersonDefault = true;
                        firstPersonExpr = null;
                        idx += 3;
                    } else {
                        throw new ParseException("unknown option in play animation: '" + t.get(idx).value() + "'",
                                "неизвестный параметр play animation: '" + t.get(idx).value() + "'");
                    }
                }
                case "no", "without" -> {
                    if (word(t, idx + 1).equals("first") && word(t, idx + 2).equals("person")) {
                        firstPersonDefault = false;
                        firstPersonExpr = null;
                        idx += 3;
                    } else {
                        throw new ParseException("unknown option in play animation: '" + t.get(idx).value() + "'",
                                "неизвестный параметр play animation: '" + t.get(idx).value() + "'");
                    }
                }
                case "first" -> {
                    if (!word(t, idx + 1).equals("person")) {
                        throw new ParseException("expected 'first person'", "ожидается 'first person'");
                    }
                    idx += 2;
                    if (idx < t.size()) {
                        String nxt = word(t, idx);
                        if (!nxt.equals("on") && !nxt.equals("of") && !nxt.equals("for") && !nxt.equals("to")
                                && !nxt.equals("speed") && !nxt.equals("fade")) {
                            ExprParser.Parsed p = ExprParser.parseAtom(t, idx);
                            firstPersonExpr = p.expression();
                            idx = p.end();
                            continue;
                        }
                    }
                    firstPersonDefault = true;
                    firstPersonExpr = null;
                }
                default -> throw new ParseException("unknown option in play animation: '" + t.get(idx).value() + "'",
                        "неизвестный параметр play animation: '" + t.get(idx).value() + "'");
            }
        }
        return new com.scriptcraft.script.runtime.Effects.PlayAnimation(
                animParsed.expression(), target, viewers, firstPersonExpr, firstPersonDefault, speed, fade);
    }

    /**
     * stop [player] animation [on|of &lt;цель&gt;] [for|to &lt;зрители&gt;] [fade N]
     */
    private Action parseStopAnimation(List<Token> t) throws ParseException {
        int idx = word(t, 1).equals("player") ? 3 : 2;
        Target target = null;
        Target viewers = null;
        Expression fade = null;
        while (idx < t.size()) {
            String opt = word(t, idx);
            switch (opt) {
                case "on", "of" -> {
                    TargetParser.Parsed p = TargetParser.parse(t, idx + 1);
                    if (p == null) {
                        throw new ParseException("expected target after '" + opt + "'", "ожидается цель после '" + opt + "'");
                    }
                    target = p.target();
                    idx = p.end();
                }
                case "for", "to" -> {
                    TargetParser.Parsed p = TargetParser.parse(t, idx + 1);
                    if (p == null) {
                        throw new ParseException("expected viewer target after '" + opt + "'", "ожидается цель-зритель после '" + opt + "'");
                    }
                    viewers = p.target();
                    idx = p.end();
                }
                case "fade" -> {
                    if (idx + 1 >= t.size()) {
                        throw new ParseException("need: fade <ticks>", "нужно: fade <тики>");
                    }
                    ExprParser.Parsed p = ExprParser.parseAtom(t, idx + 1);
                    fade = p.expression();
                    idx = p.end();
                }
                default -> throw new ParseException("unknown option in stop animation: '" + t.get(idx).value() + "'",
                        "неизвестный параметр stop animation: '" + t.get(idx).value() + "'");
            }
        }
        return new com.scriptcraft.script.runtime.Effects.StopAnimation(target, viewers, fade);
    }

    /**
     * spawn entity "<id>" at <x y z | target | expr> [named "..."] [with health N] [with nbt "..."]
     * [with weapon/helmet/chestplate/leggings/boots <item>]
     */
    /**
     * leash <entity> to <entity | targeted block | block [at] x y z | location <expr>> [length N] [min_length N]
     * [strength|pull|stiffness N] [break|break_at N | breakable | unbreakable] [color #HEX] [width|thickness N]
     * [sag N] [two_way|bidirectional [true|false]] [knot [true|false]] [drop_lead [true|false]]
     */
    private Action parseLeashAction(List<Token> t) throws ParseException {
        if (t.size() < 4) {
            throw new ParseException("нужно: leash <сущность/игрок> to <сущность/игрок | targeted block | block at x y z> [length N] [color #HEX] ...");
        }
        TargetParser.ParsedEntity subject = TargetParser.parseEntity(t, 1);
        if (subject == null) {
            throw new ParseException("не указана сущность/игрок для привязки: leash <сущность> to ...");
        }
        int idx = subject.end();
        if (!word(t, idx).equals("to")) {
            throw new ParseException("ожидается 'to' после цели: leash <сущность> to <держатель или блок>");
        }
        idx++;
        if (idx >= t.size()) {
            throw new ParseException("укажите к кому или к какому блоку привязать поводок");
        }

        EntityTarget holderEntity = null;
        boolean toTargetedBlock = false;
        Expression bx = null, by = null, bz = null;
        Expression locExpr = null;

        String w = word(t, idx);
        if (w.equals("targeted") && word(t, idx + 1).equals("block")) {
            toTargetedBlock = true;
            idx += 2;
        } else if (w.equals("block")) {
            int c = idx + 1;
            if (word(t, c).equals("at")) {
                c++;
            }
            if (c + 2 < t.size() && !isLeashOption(word(t, c)) && looksNumericOrVar(t.get(c))
                    && looksNumericOrVar(t.get(c + 1)) && looksNumericOrVar(t.get(c + 2))) {
                bx = expression(t.get(c));
                by = expression(t.get(c + 1));
                bz = expression(t.get(c + 2));
                idx = c + 3;
            } else {
                ExprParser.Parsed ep = ExprParser.parse(t, c);
                locExpr = ep.expression();
                idx = ep.end();
            }
        } else if (w.equals("location") || w.equals("pos") || w.equals("position")) {
            if (word(t, idx + 1).equals("(") || word(t, idx + 1).equals("at") || word(t, idx + 1).equals("of")) {
                ExprParser.Parsed ep = ExprParser.parse(t, idx);
                locExpr = ep.expression();
                idx = ep.end();
            } else {
                ExprParser.Parsed ep = ExprParser.parse(t, idx + 1);
                locExpr = ep.expression();
                idx = ep.end();
            }
        } else if (idx + 2 < t.size() && looksNumericOrVar(t.get(idx)) && looksNumericOrVar(t.get(idx + 1)) && looksNumericOrVar(t.get(idx + 2))) {
            bx = expression(t.get(idx));
            by = expression(t.get(idx + 1));
            bz = expression(t.get(idx + 2));
            idx += 3;
        } else {
            TargetParser.ParsedEntity he = TargetParser.parseEntity(t, idx);
            if (he == null) {
                throw new ParseException("не удалось разобрать точку крепления поводка");
            }
            holderEntity = he.target();
            idx = he.end();
        }

        Expression maxLen = null;
        Expression minLen = null;
        Expression stiffness = null;
        Expression breakDist = null;
        Boolean breakableFlag = null;
        Expression colorExpr = null;
        Expression widthExpr = null;
        Expression sagExpr = null;
        Expression twoWayExpr = null;
        Expression knotExpr = null;
        Expression dropLeadExpr = null;

        while (idx < t.size()) {
            String opt = word(t, idx);
            if (opt.equals("with") || opt.equals("and")) {
                idx++;
                continue;
            }
            switch (opt) {
                case "length", "max_length", "max-length", "distance" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: length <число>");
                    maxLen = expression(t.get(idx + 1));
                    idx += 2;
                }
                case "min_length", "min-length" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: min_length <число>");
                    minLen = expression(t.get(idx + 1));
                    idx += 2;
                }
                case "strength", "pull", "stiffness", "force", "tension" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: strength <число>");
                    stiffness = expression(t.get(idx + 1));
                    idx += 2;
                }
                case "break", "break_at", "break-at", "break_distance", "break-distance" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: break <дистанция>");
                    breakDist = expression(t.get(idx + 1));
                    idx += 2;
                }
                case "breakable" -> {
                    breakableFlag = true;
                    idx++;
                    if (idx < t.size() && (word(t, idx).equals("true") || word(t, idx).equals("false"))) {
                        breakableFlag = word(t, idx).equals("true");
                        idx++;
                    }
                }
                case "unbreakable" -> {
                    breakableFlag = false;
                    breakDist = new Expression.Literal(0.0);
                    idx++;
                }
                case "color", "colour" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: color <#RRGGBB>");
                    Token ct = t.get(idx + 1);
                    colorExpr = ct.quoted() || ct.value().startsWith("{") ? expression(ct) : new Expression.Text(ct.value());
                    idx += 2;
                }
                case "width", "thickness" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: width <число>");
                    widthExpr = expression(t.get(idx + 1));
                    idx += 2;
                }
                case "sag", "droop", "slack" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: sag <число>");
                    sagExpr = expression(t.get(idx + 1));
                    idx += 2;
                }
                case "two_way", "two-way", "bidirectional", "both" -> {
                    idx++;
                    if (idx < t.size() && (word(t, idx).equals("true") || word(t, idx).equals("false"))) {
                        twoWayExpr = new Expression.Literal(Boolean.parseBoolean(word(t, idx)));
                        idx++;
                    } else {
                        twoWayExpr = new Expression.Literal(true);
                    }
                }
                case "knot" -> {
                    idx++;
                    if (idx < t.size() && (word(t, idx).equals("true") || word(t, idx).equals("false"))) {
                        knotExpr = new Expression.Literal(Boolean.parseBoolean(word(t, idx)));
                        idx++;
                    } else {
                        knotExpr = new Expression.Literal(true);
                    }
                }
                case "no_knot", "no-knot" -> {
                    knotExpr = new Expression.Literal(false);
                    idx++;
                }
                case "drop_lead", "drop-lead", "drop" -> {
                    idx++;
                    if (idx < t.size() && (word(t, idx).equals("true") || word(t, idx).equals("false"))) {
                        dropLeadExpr = new Expression.Literal(Boolean.parseBoolean(word(t, idx)));
                        idx++;
                    } else {
                        dropLeadExpr = new Expression.Literal(true);
                    }
                }
                default -> throw new ParseException("неизвестный параметр поводка '" + t.get(idx).value()
                        + "' (доступны: length, min_length, strength, break, unbreakable, color, width, sag, two_way, knot, drop_lead)");
            }
        }

        return new Effects.AttachLeash(
                subject.target(), holderEntity, toTargetedBlock, bx, by, bz, locExpr,
                maxLen, minLen, stiffness, breakDist, breakableFlag,
                colorExpr, widthExpr, sagExpr, twoWayExpr, knotExpr, dropLeadExpr);
    }

    private static boolean isLeashOption(String w) {
        return switch (w) {
            case "with", "length", "max_length", "max-length", "min_length", "min-length",
                 "strength", "pull", "stiffness", "force", "tension",
                 "break", "break_at", "break-at", "break_distance", "break-distance",
                 "breakable", "unbreakable", "color", "colour", "width", "thickness",
                 "sag", "droop", "slack", "two_way", "two-way", "bidirectional",
                 "knot", "no_knot", "no-knot", "drop_lead", "drop-lead", "drop" -> true;
            default -> false;
        };
    }

    private Action parseSetLeashProp(List<Token> t) throws ParseException {
        String prop = word(t, 2);
        if (!word(t, 3).equals("of")) {
            throw new ParseException("нужно: set leash <length|strength|break|color|width|sag|two_way> of <сущность> to <значение>");
        }
        TargetParser.ParsedEntity ent = TargetParser.parseEntity(t, 4);
        if (ent == null || !word(t, ent.end()).equals("to") || ent.end() + 1 >= t.size()) {
            throw new ParseException("нужно: set leash " + prop + " of <сущность> to <значение>");
        }
        int valIdx = ent.end() + 1;
        Expression valExpr;
        if ((prop.equals("color") || prop.equals("colour")) && valIdx + 1 == t.size() && !t.get(valIdx).quoted()
                && !t.get(valIdx).value().startsWith("{")) {
            valExpr = new Expression.Text(t.get(valIdx).value());
        } else {
            ExprParser.Parsed parsed = ExprParser.parse(t, valIdx);
            if (parsed.end() != t.size()) {
                throw new ParseException("лишние слова после значения в set leash " + prop);
            }
            valExpr = parsed.expression();
        }
        return new Effects.SetLeashProperty(prop, ent.target(), valExpr);
    }

    private Action parseSpawnEntity(List<Token> t) throws ParseException {
        if (t.size() < 5) {
            throw new ParseException("нужно: spawn entity \"minecraft:zombie\" at <локация> [named \"...\"] [with health N] [with nbt \"{...}\"]");
        }
        String entityId = t.get(2).value();
        expectWord(t, 3, "at");
        int idx = 4;
        Target target = null;
        Expression place = null;
        Expression x = null, y = null, z = null;

        // Вариант 1: три числа (x y z)
        if (idx + 2 < t.size() && looksNumericOrVar(t.get(idx)) && looksNumericOrVar(t.get(idx + 1)) && looksNumericOrVar(t.get(idx + 2))) {
            x = expression(t.get(idx));
            y = expression(t.get(idx + 1));
            z = expression(t.get(idx + 2));
            idx += 3;
        } else {
            TargetParser.Parsed tp = TargetParser.parse(t, idx);
            String afterTarget = tp == null ? "" : word(t, tp.end());
            if (tp != null && (tp.end() == t.size() || afterTarget.equals("named")
                    || afterTarget.equals("name") || afterTarget.equals("with"))) {
                target = tp.target();
                idx = tp.end();
            } else {
                ExprParser.Parsed ep = ExprParser.parse(t, idx);
                place = ep.expression();
                idx = ep.end();
            }
        }

        String customName = null;
        Expression health = null;
        String snbt = null;
        java.util.Map<net.minecraft.world.entity.EquipmentSlot, String> equip = new java.util.EnumMap<>(net.minecraft.world.entity.EquipmentSlot.class);

        int boxGrad = 0;
        int boxBorder = 0;
        int boxBorderW = 1;
        boolean boxHoriz = false;
        while (idx < t.size()) {
            String w = word(t, idx);
            if (w.equals("named") || w.equals("name")) {
                customName = quoted(t, idx + 1);
                idx += 2;
                continue;
            }
            if (w.equals("with")) {
                idx++;
                w = word(t, idx);
            }
            switch (w) {
                case "name", "named" -> {
                    customName = quoted(t, idx + 1);
                    idx += 2;
                }
                case "health", "hp" -> {
                    ExprParser.Parsed hp = ExprParser.parse(t, idx + 1);
                    health = hp.expression();
                    idx = hp.end();
                }
                case "nbt" -> {
                    if (idx + 1 >= t.size()) {
                        throw new ParseException("нужно: with nbt \"{...}\"");
                    }
                    snbt = t.get(idx + 1).value();
                    idx += 2;
                }
                case "weapon", "hand", "mainhand" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("укажите предмет для weapon");
                    equip.put(net.minecraft.world.entity.EquipmentSlot.MAINHAND, t.get(idx + 1).value());
                    idx += 2;
                }
                case "offhand" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("укажите предмет для offhand");
                    equip.put(net.minecraft.world.entity.EquipmentSlot.OFFHAND, t.get(idx + 1).value());
                    idx += 2;
                }
                case "helmet", "head" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("укажите предмет для helmet");
                    equip.put(net.minecraft.world.entity.EquipmentSlot.HEAD, t.get(idx + 1).value());
                    idx += 2;
                }
                case "chestplate", "chest" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("укажите предмет для chestplate");
                    equip.put(net.minecraft.world.entity.EquipmentSlot.CHEST, t.get(idx + 1).value());
                    idx += 2;
                }
                case "leggings", "legs" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("укажите предмет для leggings");
                    equip.put(net.minecraft.world.entity.EquipmentSlot.LEGS, t.get(idx + 1).value());
                    idx += 2;
                }
                case "boots", "feet" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("укажите предмет для boots");
                    equip.put(net.minecraft.world.entity.EquipmentSlot.FEET, t.get(idx + 1).value());
                    idx += 2;
                }
                default -> throw new ParseException("неизвестный параметр spawn entity: '" + t.get(idx).value()
                        + "' (named, with health, with nbt, with weapon/helmet/chestplate/leggings/boots)");
            }
        }
        return new com.scriptcraft.script.runtime.Effects.SpawnEntity(
                entityId, target, place, x, y, z, customName, health, snbt, equip.isEmpty() ? null : equip);
    }

    private static boolean looksNumericOrVar(Token tok) {
        if (tok.quoted()) return false;
        String v = tok.value();
        if (v.startsWith("{") && v.endsWith("}")) return true;
        try {
            Double.parseDouble(v);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** set/remove nbt "путь" of targeted entity|block|player|player */
    private Action parseNbt(List<Token> t, boolean set) throws ParseException {
        int idx = 2;
        if (idx >= t.size()) {
            throw new ParseException("nbt path is missing", "не указан путь nbt");
        }
        String path = t.get(idx).quoted() ? t.get(idx).value() : t.get(idx).value();
        idx++;
        if (!word(t, idx).equals("of")) {
            throw new ParseException("need: nbt \"path\" of targeted entity", "нужно: nbt \"путь\" of targeted entity");
        }
        idx++;
        String kind;
        if (word(t, idx).equals("targeted") && (word(t, idx + 1).equals("entity") || word(t, idx + 1).equals("block")
                || word(t, idx + 1).equals("player"))) {
            kind = word(t, idx + 1);
            idx += 2;
        } else if (word(t, idx).equals("player")) {
            kind = "self";
            idx++;
        } else {
            throw new ParseException("nbt target: targeted entity, targeted block, targeted player or player",
                    "цель nbt: targeted entity, targeted block, targeted player или player");
        }
        if (!set) {
            if (idx != t.size()) {
                throw new ParseException("extra text after remove nbt", "лишний текст после remove nbt");
            }
            return new Actions.RemoveNbt(path, kind);
        }
        if (!word(t, idx).equals("to")) {
            throw new ParseException("need: set nbt \"path\" of targeted entity to <value>",
                    "нужно: set nbt \"путь\" of targeted entity to <значение>");
        }
        ExprParser.Parsed value = ExprParser.parse(t, idx + 1);
        if (value.end() != t.size()) {
            throw new ParseException("extra text after the nbt value", "лишний текст после значения nbt");
        }
        return new Actions.SetNbt(path, kind, value.expression());
    }

    // ------------------------------------------------------------------ разбор 3D Geo, NPC, Props и Cutscenes

    private int parsePositionClause(List<Token> t, int idx, Target[] outTarget, Expression[] outPlace,
                                    Expression[] outXyz, java.util.Set<String> optionKeywords) throws ParseException {
        if (idx + 2 < t.size() && looksNumericOrVar(t.get(idx)) && looksNumericOrVar(t.get(idx + 1)) && looksNumericOrVar(t.get(idx + 2))) {
            outXyz[0] = expression(t.get(idx));
            outXyz[1] = expression(t.get(idx + 1));
            outXyz[2] = expression(t.get(idx + 2));
            return idx + 3;
        }
        TargetParser.Parsed tp = TargetParser.parse(t, idx);
        String nextW = tp == null ? "" : word(t, tp.end());
        if (tp != null && (tp.end() == t.size() || optionKeywords.contains(nextW))) {
            outTarget[0] = tp.target();
            return tp.end();
        }
        ExprParser.Parsed ep = ExprParser.parseAtom(t, idx);
        outPlace[0] = ep.expression();
        return ep.end();
    }

    private Action parseSpawnGeoEntity(List<Token> t) throws ParseException {
        int idx = 2;
        String sub = word(t, idx);
        if (sub.equals("entity") || sub.equals("mob") || sub.equals("boss") || sub.equals("turret") || sub.equals("prop")) {
            idx++;
        } else {
            sub = "entity";
        }
        if (idx >= t.size()) {
            throw new ParseException("нужно: spawn geo entity \"gecko:cyber_guardian\" at player");
        }
        ExprParser.Parsed idExpr = ExprParser.parseAtom(t, idx);
        idx = idExpr.end();
        expectWord(t, idx, "at");
        idx++;

        Set<String> opts = Set.of("model", "texture", "animation", "named", "name", "with", "health", "hp", "damage", "scale", "hitbox", "ai");
        Target[] atTarget = new Target[1];
        Expression[] atPlace = new Expression[1];
        Expression[] xyz = new Expression[3];
        idx = parsePositionClause(t, idx, atTarget, atPlace, xyz, opts);

        Expression modelOv = null, texOv = null, animOv = null, hp = null, dmg = null, scl = null;
        String name = null;
        String ai = sub.equals("turret") ? "turret" : (sub.equals("prop") ? "none" : null);
        float hbw = 0f, hbh = 0f;

        while (idx < t.size()) {
            String w = word(t, idx);
            if (w.equals("with")) { idx++; w = word(t, idx); }
            switch (w) {
                case "model" -> { ExprParser.Parsed p = ExprParser.parseAtom(t, idx + 1); modelOv = p.expression(); idx = p.end(); }
                case "texture" -> { ExprParser.Parsed p = ExprParser.parseAtom(t, idx + 1); texOv = p.expression(); idx = p.end(); }
                case "animation" -> { ExprParser.Parsed p = ExprParser.parseAtom(t, idx + 1); animOv = p.expression(); idx = p.end(); }
                case "named", "name" -> { name = quoted(t, idx + 1); idx += 2; }
                case "health", "hp" -> { ExprParser.Parsed p = ExprParser.parseAtom(t, idx + 1); hp = p.expression(); idx = p.end(); }
                case "damage" -> { ExprParser.Parsed p = ExprParser.parseAtom(t, idx + 1); dmg = p.expression(); idx = p.end(); }
                case "scale" -> { ExprParser.Parsed p = ExprParser.parseAtom(t, idx + 1); scl = p.expression(); idx = p.end(); }
                case "ai" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("нужно: ai hostile|passive|turret|none");
                    ai = word(t, idx + 1);
                    idx += 2;
                }
                case "hitbox" -> {
                    if (idx + 1 >= t.size() || !t.get(idx + 1).value().contains("x")) {
                        throw new ParseException("нужно: hitbox ШИРИНАxВЫСОТА, например hitbox 1.4x2.8");
                    }
                    String[] parts = t.get(idx + 1).value().toLowerCase(Locale.ROOT).split("x", 2);
                    hbw = (float) parseDouble(parts[0]);
                    hbh = (float) parseDouble(parts[1]);
                    idx += 2;
                }
                default -> throw new ParseException("неизвестный параметр spawn geo: '" + t.get(idx).value() + "'");
            }
        }
        return new Effects.SpawnGeoEntity(idExpr.expression(), atTarget[0], atPlace[0], xyz[0], xyz[1], xyz[2],
                modelOv, texOv, animOv, name, hp, dmg, scl, hbw, hbh, ai);
    }

    private Action parseGeoEntityAnimation(List<Token> t, boolean stop) throws ParseException {
        int idx = 3;
        Expression anim = null;
        if (!stop) {
            ExprParser.Parsed ap = ExprParser.parseAtom(t, idx);
            anim = ap.expression();
            idx = ap.end();
        }
        if (!word(t, idx).equals("on") && !word(t, idx).equals("of")) {
            throw new ParseException("нужно: " + (stop ? "stop geo animation on <сущность>" : "play geo animation \"attack\" on <сущность>"));
        }
        TargetParser.ParsedEntity ent = TargetParser.parseEntity(t, idx + 1);
        if (ent == null) throw new ParseException("ожидается сущность (например last spawned entity или targeted entity)");
        idx = ent.end();
        int loop = 0;
        Expression speed = null;
        while (idx < t.size()) {
            String w = word(t, idx);
            switch (w) {
                case "loop", "looped" -> { loop = 1; idx++; }
                case "once" -> { loop = 0; idx++; }
                case "hold" -> { loop = 2; idx++; }
                case "speed" -> { ExprParser.Parsed p = ExprParser.parseAtom(t, idx + 1); speed = p.expression(); idx = p.end(); }
                default -> throw new ParseException("неизвестный параметр geo animation: '" + t.get(idx).value() + "'");
            }
        }
        return new Effects.GeoEntityControl(stop ? "stop" : "play", ent.target(), anim, loop, speed);
    }

    private Action parseSetGeoProp(List<Token> t) throws ParseException {
        String prop = word(t, 2); // texture | scale
        expectWord(t, 3, "of");
        TargetParser.ParsedEntity ent = TargetParser.parseEntity(t, 4);
        if (ent == null || !word(t, ent.end()).equals("to") || ent.end() + 1 >= t.size()) {
            throw new ParseException("нужно: set geo " + prop + " of <сущность> to <значение>");
        }
        ExprParser.Parsed val = ExprParser.parse(t, ent.end() + 1);
        return new Effects.GeoEntityControl(prop, ent.target(), val.expression(), 0, null);
    }

    private Action parseGeoItemAnimation(List<Token> t, boolean stop) throws ParseException {
        int idx = 3;
        Expression anim = null;
        if (!stop) {
            ExprParser.Parsed ap = ExprParser.parseAtom(t, idx);
            anim = ap.expression();
            idx = ap.end();
        }
        Target target = null;
        boolean offhand = false;
        int loop = 0;
        Expression speed = null;
        while (idx < t.size()) {
            String w = word(t, idx);
            switch (w) {
                case "on", "for", "of", "to" -> {
                    TargetParser.Parsed tp = TargetParser.parse(t, idx + 1);
                    if (tp == null) throw new ParseException("ожидается игрок после '" + w + "'");
                    target = tp.target();
                    idx = tp.end();
                }
                case "offhand" -> { offhand = true; idx++; }
                case "mainhand", "hand" -> { offhand = false; idx++; }
                case "loop", "looped" -> { loop = 1; idx++; }
                case "once" -> { loop = 0; idx++; }
                case "speed" -> { ExprParser.Parsed p = ExprParser.parseAtom(t, idx + 1); speed = p.expression(); idx = p.end(); }
                default -> throw new ParseException("неизвестный параметр item animation: '" + t.get(idx).value() + "'");
            }
        }
        return new Effects.GeoItemAnim(anim, target, offhand, loop, speed);
    }

    private Action parseGeoBlockAnimation(List<Token> t, boolean stop) throws ParseException {
        int idx = 3;
        Expression anim = null;
        if (!stop) {
            ExprParser.Parsed ap = ExprParser.parseAtom(t, idx);
            anim = ap.expression();
            idx = ap.end();
        }
        expectWord(t, idx, "at");
        idx++;
        boolean targetedBlock = false;
        Expression place = null, x = null, y = null, z = null;
        if (word(t, idx).equals("targeted") && word(t, idx + 1).equals("block")) {
            targetedBlock = true;
            idx += 2;
        } else if (idx + 2 < t.size() && looksNumericOrVar(t.get(idx)) && looksNumericOrVar(t.get(idx + 1)) && looksNumericOrVar(t.get(idx + 2))) {
            x = expression(t.get(idx));
            y = expression(t.get(idx + 1));
            z = expression(t.get(idx + 2));
            idx += 3;
        } else {
            ExprParser.Parsed ep = ExprParser.parseAtom(t, idx);
            place = ep.expression();
            idx = ep.end();
        }
        int loop = 0;
        Expression speed = null;
        while (idx < t.size()) {
            String w = word(t, idx);
            switch (w) {
                case "loop", "looped" -> { loop = 1; idx++; }
                case "once" -> { loop = 0; idx++; }
                case "hold" -> { loop = 2; idx++; }
                case "speed" -> { ExprParser.Parsed p = ExprParser.parseAtom(t, idx + 1); speed = p.expression(); idx = p.end(); }
                default -> throw new ParseException("неизвестный параметр block animation: '" + t.get(idx).value() + "'");
            }
        }
        return new Effects.GeoBlockAnim(anim, targetedBlock, place, x, y, z, loop, speed);
    }

    private Action parseSpawnNpc(List<Token> t) throws ParseException {
        if (t.size() < 5) {
            throw new ParseException("нужно: spawn npc \"commander\" at player [named \"...\"] [skin \"...\"] [slim|wide]");
        }
        ExprParser.Parsed idExpr = ExprParser.parseAtom(t, 2);
        int idx = idExpr.end();
        expectWord(t, idx, "at");
        idx++;

        Set<String> opts = Set.of("named", "name", "skin", "slim", "alex", "wide", "steve", "auto", "look", "vulnerable", "sneak", "sneaking", "with");
        Target[] atTarget = new Target[1];
        Expression[] atPlace = new Expression[1];
        Expression[] xyz = new Expression[3];
        idx = parsePositionClause(t, idx, atTarget, atPlace, xyz, opts);

        Expression skinExpr = null;
        Expression nameExpr = null;
        String style = "auto";
        Boolean look = true;
        Boolean vuln = false;
        Boolean sneak = false;

        while (idx < t.size()) {
            String w = word(t, idx);
            if (w.equals("with")) { idx++; w = word(t, idx); }
            switch (w) {
                case "named", "name" -> { ExprParser.Parsed p = ExprParser.parseAtom(t, idx + 1); nameExpr = p.expression(); idx = p.end(); }
                case "skin" -> { ExprParser.Parsed p = ExprParser.parseAtom(t, idx + 1); skinExpr = p.expression(); idx = p.end(); }
                case "slim", "alex" -> { style = "slim"; idx++; }
                case "wide", "steve" -> { style = "wide"; idx++; }
                case "auto" -> { style = "auto"; idx++; }
                case "look" -> {
                    idx++;
                    if (idx < t.size() && (word(t, idx).equals("true") || word(t, idx).equals("false"))) {
                        look = Boolean.parseBoolean(word(t, idx));
                        idx++;
                    } else {
                        look = true;
                    }
                }
                case "vulnerable" -> {
                    idx++;
                    if (idx < t.size() && (word(t, idx).equals("true") || word(t, idx).equals("false"))) {
                        vuln = Boolean.parseBoolean(word(t, idx));
                        idx++;
                    } else {
                        vuln = true;
                    }
                }
                case "sneak", "sneaking" -> {
                    idx++;
                    if (idx < t.size() && (word(t, idx).equals("true") || word(t, idx).equals("false"))) {
                        sneak = Boolean.parseBoolean(word(t, idx));
                        idx++;
                    } else {
                        sneak = true;
                    }
                }
                default -> throw new ParseException("неизвестный параметр spawn npc: '" + t.get(idx).value() + "'");
            }
        }
        return new Effects.NpcAction("spawn", idExpr.expression(), atTarget[0], atPlace[0], xyz[0], xyz[1], xyz[2],
                skinExpr, style, look, vuln, sneak, null, nameExpr);
    }

    private Action parseEquipNpc(List<Token> t) throws ParseException {
        ExprParser.Parsed idExpr = ExprParser.parseAtom(t, 2);
        int idx = idExpr.end();
        if (word(t, idx).equals("with")) idx++;
        if (idx + 1 >= t.size()) {
            throw new ParseException("нужно: equip npc \"commander\" with <mainhand|offhand|helmet|chestplate|leggings|boots> <предмет>");
        }
        net.minecraft.world.entity.EquipmentSlot slot = switch (word(t, idx)) {
            case "mainhand", "weapon", "hand", "tool" -> net.minecraft.world.entity.EquipmentSlot.MAINHAND;
            case "offhand", "shield" -> net.minecraft.world.entity.EquipmentSlot.OFFHAND;
            case "helmet", "head" -> net.minecraft.world.entity.EquipmentSlot.HEAD;
            case "chestplate", "chest" -> net.minecraft.world.entity.EquipmentSlot.CHEST;
            case "leggings", "legs" -> net.minecraft.world.entity.EquipmentSlot.LEGS;
            case "boots", "feet" -> net.minecraft.world.entity.EquipmentSlot.FEET;
            default -> throw new ParseException("неизвестный слот экипировки NPC: '" + t.get(idx).value() + "'");
        };
        ExprParser.Parsed itemExpr = ExprParser.parse(t, idx + 1);
        return new Effects.NpcAction("equip", idExpr.expression(), null, null, null, null, null,
                itemExpr.expression(), null, null, null, null, slot, null);
    }

    private Action parseSetNpcProp(List<Token> t) throws ParseException {
        String prop = word(t, 1); // skin | name
        ExprParser.Parsed idExpr = ExprParser.parseAtom(t, 4);
        int idx = idExpr.end();
        expectWord(t, idx, "to");
        ExprParser.Parsed val = ExprParser.parse(t, idx + 1);
        return new Effects.NpcAction(prop, idExpr.expression(), null, null, null, null, null,
                val.expression(), null, null, null, null, null, null);
    }

    private Action parseNpcAnimation(List<Token> t, boolean stop) throws ParseException {
        int idx = 3;
        Expression anim = null;
        if (!stop) {
            ExprParser.Parsed ap = ExprParser.parseAtom(t, idx);
            anim = ap.expression();
            idx = ap.end();
        }
        if (word(t, idx).equals("on") || word(t, idx).equals("of")) idx++;
        if (word(t, idx).equals("npc")) idx++;
        ExprParser.Parsed idExpr = ExprParser.parseAtom(t, idx);
        idx = idExpr.end();
        Expression speed = null;
        while (idx < t.size()) {
            if (word(t, idx).equals("speed")) {
                ExprParser.Parsed sp = ExprParser.parseAtom(t, idx + 1);
                speed = sp.expression();
                idx = sp.end();
            } else {
                throw new ParseException("неизвестный параметр npc animation: '" + t.get(idx).value() + "'");
            }
        }
        return new Effects.NpcAction(stop ? "stop_anim" : "anim", idExpr.expression(), null, null, null, null, null,
                anim, null, null, null, null, null, speed);
    }

    private Action parseMakeNpc(List<Token> t) throws ParseException {
        ExprParser.Parsed idExpr = ExprParser.parseAtom(t, 2);
        int idx = idExpr.end();
        String verb = word(t, idx);
        if (verb.equals("look") && word(t, idx + 1).equals("at")) {
            idx += 2;
            Target[] atTarget = new Target[1];
            Expression[] atPlace = new Expression[1];
            Expression[] xyz = new Expression[3];
            parsePositionClause(t, idx, atTarget, atPlace, xyz, Set.of());
            return new Effects.NpcAction("look", idExpr.expression(), atTarget[0], atPlace[0], xyz[0], xyz[1], xyz[2],
                    null, null, null, null, null, null, null);
        }
        if ((verb.equals("walk") || verb.equals("move")) && word(t, idx + 1).equals("to")) {
            idx += 2;
            Target[] atTarget = new Target[1];
            Expression[] atPlace = new Expression[1];
            Expression[] xyz = new Expression[3];
            idx = parsePositionClause(t, idx, atTarget, atPlace, xyz, Set.of("speed", "with"));
            Expression spd = null;
            if (idx < t.size()) {
                if (word(t, idx).equals("with")) idx++;
                if (word(t, idx).equals("speed")) {
                    ExprParser.Parsed sp = ExprParser.parseAtom(t, idx + 1);
                    spd = sp.expression();
                }
            }
            return new Effects.NpcAction("walk", idExpr.expression(), atTarget[0], atPlace[0], xyz[0], xyz[1], xyz[2],
                    null, null, null, null, null, null, spd);
        }
        throw new ParseException("нужно: make npc \"id\" look at <цель>  или  make npc \"id\" walk to <x y z> [speed N]");
    }

    private Action parseAttachProp(List<Token> t) throws ParseException {
        if (t.size() < 6) {
            throw new ParseException("нужно: attach prop \"drone\" model \"gecko:shoulder_drone\" to body of player");
        }
        ExprParser.Parsed slotExpr = ExprParser.parseAtom(t, 2);
        int idx = slotExpr.end();
        boolean itemMode = false;
        if (word(t, idx).equals("item")) {
            itemMode = true;
            idx++;
        } else if (word(t, idx).equals("model")) {
            idx++;
        }
        ExprParser.Parsed mainExpr = ExprParser.parseAtom(t, idx);
        idx = mainExpr.end();

        Expression texExpr = null;
        Expression animExpr = null;
        String bone = "body";
        EntityTarget entTarget = null;
        Expression npcTarget = null;
        Expression ox = null, oy = null, oz = null;
        Expression rx = null, ry = null, rz = null;
        Expression scale = null;

        while (idx < t.size()) {
            String w = word(t, idx);
            switch (w) {
                case "texture" -> { ExprParser.Parsed p = ExprParser.parseAtom(t, idx + 1); texExpr = p.expression(); idx = p.end(); }
                case "animation" -> { ExprParser.Parsed p = ExprParser.parseAtom(t, idx + 1); animExpr = p.expression(); idx = p.end(); }
                case "to" -> {
                    if (idx + 1 >= t.size()) throw new ParseException("укажите кость (head, body, right_arm, left_arm, right_leg, left_leg)");
                    bone = com.scriptcraft.script.runtime.PropManager.normalizeBone(word(t, idx + 1));
                    idx += 2;
                    if (idx < t.size() && word(t, idx).equals("of")) {
                        idx++;
                        if (word(t, idx).equals("npc")) {
                            ExprParser.Parsed np = ExprParser.parseAtom(t, idx + 1);
                            npcTarget = np.expression();
                            idx = np.end();
                        } else {
                            TargetParser.ParsedEntity ep = TargetParser.parseEntity(t, idx);
                            if (ep == null) throw new ParseException("ожидается player или сущность после 'of'");
                            entTarget = ep.target();
                            idx = ep.end();
                        }
                    }
                }
                case "offset" -> {
                    if (idx + 3 >= t.size()) throw new ParseException("нужно: offset X Y Z");
                    ox = expression(t.get(idx + 1));
                    oy = expression(t.get(idx + 2));
                    oz = expression(t.get(idx + 3));
                    idx += 4;
                }
                case "rotation", "rot" -> {
                    if (idx + 3 >= t.size()) throw new ParseException("нужно: rotation RX RY RZ");
                    rx = expression(t.get(idx + 1));
                    ry = expression(t.get(idx + 2));
                    rz = expression(t.get(idx + 3));
                    idx += 4;
                }
                case "scale" -> { ExprParser.Parsed p = ExprParser.parseAtom(t, idx + 1); scale = p.expression(); idx = p.end(); }
                default -> throw new ParseException("неизвестный параметр attach prop: '" + t.get(idx).value() + "'");
            }
        }
        return new Effects.PropAction("attach", slotExpr.expression(), bone, itemMode, mainExpr.expression(),
                texExpr, animExpr, entTarget, npcTarget, ox, oy, oz, rx, ry, rz, scale);
    }

    private Action parseDetachProp(List<Token> t) throws ParseException {
        ExprParser.Parsed slotExpr = ExprParser.parseAtom(t, 2);
        int idx = slotExpr.end();
        EntityTarget entTarget = null;
        Expression npcTarget = null;
        if (idx < t.size() && (word(t, idx).equals("from") || word(t, idx).equals("of"))) {
            idx++;
            if (word(t, idx).equals("npc")) {
                ExprParser.Parsed np = ExprParser.parseAtom(t, idx + 1);
                npcTarget = np.expression();
            } else {
                TargetParser.ParsedEntity ep = TargetParser.parseEntity(t, idx);
                if (ep != null) entTarget = ep.target();
            }
        }
        return new Effects.PropAction("detach", slotExpr.expression(), null, false, null, null, null,
                entTarget, npcTarget, null, null, null, null, null, null, null);
    }

    private Action parseClearProps(List<Token> t) throws ParseException {
        int idx = word(t, 1).equals("all") ? 3 : 2;
        EntityTarget entTarget = null;
        Expression npcTarget = null;
        if (idx < t.size() && (word(t, idx).equals("of") || word(t, idx).equals("from"))) {
            idx++;
            if (word(t, idx).equals("npc")) {
                ExprParser.Parsed np = ExprParser.parseAtom(t, idx + 1);
                npcTarget = np.expression();
            } else {
                TargetParser.ParsedEntity ep = TargetParser.parseEntity(t, idx);
                if (ep != null) entTarget = ep.target();
            }
        }
        return new Effects.PropAction("clear", null, null, false, null, null, null,
                entTarget, npcTarget, null, null, null, null, null, null, null);
    }

    private Action parsePlayCutscene(List<Token> t) throws ParseException {
        int idx = 2;
        if (word(t, 1).equals("camera") && word(t, 2).equals("path")) idx = 3;
        Target target = null;
        Expression points = null;
        Expression duration = null;
        int ticksPerUnit = 1;
        EntityTarget lookEnt = null;
        Expression lookNpc = null;
        Expression lookPlace = null;
        Expression lx = null, ly = null, lz = null;
        boolean lock = true;
        boolean lockLook = true;
        boolean hideHand = true;

        while (idx < t.size()) {
            String w = word(t, idx);
            switch (w) {
                case "for", "of", "to" -> {
                    // Проверяем, не длительность ли это ("for 5 seconds")
                    if (idx + 2 < t.size() && looksNumericOrVar(t.get(idx + 1)) && isTimeUnitWord(word(t, idx + 2))) {
                        duration = expression(t.get(idx + 1));
                        ticksPerUnit = timeUnitMultiplier(word(t, idx + 2));
                        idx += 3;
                    } else {
                        TargetParser.Parsed tp = TargetParser.parse(t, idx + 1);
                        if (tp == null) throw new ParseException("ожидается цель после '" + w + "'");
                        target = tp.target();
                        idx = tp.end();
                    }
                }
                case "points", "waypoints", "path" -> {
                    ExprParser.Parsed p = ExprParser.parseAtom(t, idx + 1);
                    points = p.expression();
                    idx = p.end();
                }
                case "in", "duration" -> {
                    ExprParser.Parsed p = ExprParser.parseAtom(t, idx + 1);
                    duration = p.expression();
                    idx = p.end();
                    if (idx < t.size() && isTimeUnitWord(word(t, idx))) {
                        ticksPerUnit = timeUnitMultiplier(word(t, idx));
                        idx++;
                    }
                }
                case "looking" -> {
                    expectWord(t, idx + 1, "at");
                    idx += 2;
                    if (word(t, idx).equals("npc")) {
                        ExprParser.Parsed np = ExprParser.parseAtom(t, idx + 1);
                        lookNpc = np.expression();
                        idx = np.end();
                    } else if (idx + 2 < t.size() && looksCoordOrRel(t.get(idx))
                            && looksCoordOrRel(t.get(idx + 1)) && looksCoordOrRel(t.get(idx + 2))) {
                        lx = t.get(idx).value().startsWith("~") ? new Expression.Text(t.get(idx).value()) : expression(t.get(idx));
                        ly = t.get(idx + 1).value().startsWith("~") ? new Expression.Text(t.get(idx + 1).value()) : expression(t.get(idx + 1));
                        lz = t.get(idx + 2).value().startsWith("~") ? new Expression.Text(t.get(idx + 2).value()) : expression(t.get(idx + 2));
                        idx += 3;
                    } else {
                        TargetParser.ParsedEntity ep = TargetParser.parseEntity(t, idx);
                        if (ep != null) {
                            lookEnt = ep.target();
                            idx = ep.end();
                        } else {
                            ExprParser.Parsed lp = ExprParser.parseAtom(t, idx);
                            lookPlace = lp.expression();
                            idx = lp.end();
                        }
                    }
                }
                case "lock", "lock_move" -> {
                    idx++;
                    if (idx < t.size() && (word(t, idx).equals("true") || word(t, idx).equals("false"))) {
                        lock = Boolean.parseBoolean(word(t, idx));
                        idx++;
                    } else {
                        lock = true;
                    }
                }
                case "unlock", "free_move" -> { lock = false; idx++; }
                case "free" -> { lock = false; lockLook = false; idx++; }
                case "lock_look", "lock_head" -> {
                    idx++;
                    if (idx < t.size() && (word(t, idx).equals("true") || word(t, idx).equals("false"))) {
                        lockLook = Boolean.parseBoolean(word(t, idx));
                        idx++;
                    } else {
                        lockLook = true;
                    }
                }
                case "free_look", "free_head", "unlock_look", "unlock_head" -> { lockLook = false; idx++; }
                case "hide_hand" -> {
                    idx++;
                    if (idx < t.size() && (word(t, idx).equals("true") || word(t, idx).equals("false"))) {
                        hideHand = Boolean.parseBoolean(word(t, idx));
                        idx++;
                    } else {
                        hideHand = true;
                    }
                }
                case "show_hand" -> { hideHand = false; idx++; }
                case "hand" -> {
                    idx++;
                    if (idx < t.size() && (word(t, idx).equals("true") || word(t, idx).equals("false") || word(t, idx).equals("show") || word(t, idx).equals("hide"))) {
                        String hw = word(t, idx);
                        hideHand = hw.equals("false") || hw.equals("hide");
                        idx++;
                    } else {
                        hideHand = false;
                    }
                }
                default -> {
                    if (points == null && t.get(idx).quoted()) {
                        points = new Expression.Text(t.get(idx).value());
                        idx++;
                    } else {
                        throw new ParseException("неизвестный параметр cutscene: '" + t.get(idx).value() + "'");
                    }
                }
            }
        }
        if (points == null) {
            throw new ParseException("укажите точки маршрута: play cutscene for player points \"~0 ~3 ~6; ~5 ~4 ~0; ~0 ~2 ~-4\" for 6 seconds");
        }
        return new Effects.CameraCutscene(false, target, points, null, null, duration, ticksPerUnit,
                lookEnt, lookNpc, lookPlace, lx, ly, lz, lock, lockLook, hideHand);
    }

    private Action parseMoveCamera(List<Token> t) throws ParseException {
        int idx = 2;
        Target target = null;
        if (word(t, idx).equals("of") || word(t, idx).equals("for")) {
            TargetParser.Parsed tp = TargetParser.parse(t, idx + 1);
            if (tp == null) throw new ParseException("ожидается цель после '" + word(t, idx) + "'");
            target = tp.target();
            idx = tp.end();
        }
        expectWord(t, idx, "from");
        ExprParser.Parsed fromP = ExprParser.parseAtom(t, idx + 1);
        idx = fromP.end();
        expectWord(t, idx, "to");
        ExprParser.Parsed toP = ExprParser.parseAtom(t, idx + 1);
        idx = toP.end();

        Expression duration = null;
        int ticksPerUnit = 1;
        EntityTarget lookEnt = null;
        Expression lookNpc = null;
        Expression lookPlace = null;
        Expression lx = null, ly = null, lz = null;
        boolean lock = true;
        boolean lockLook = true;
        boolean hideHand = true;

        while (idx < t.size()) {
            String w = word(t, idx);
            if (w.equals("in") || w.equals("for") || w.equals("duration")) {
                ExprParser.Parsed dp = ExprParser.parseAtom(t, idx + 1);
                duration = dp.expression();
                idx = dp.end();
                if (idx < t.size() && isTimeUnitWord(word(t, idx))) {
                    ticksPerUnit = timeUnitMultiplier(word(t, idx));
                    idx++;
                }
            } else if (w.equals("looking") && word(t, idx + 1).equals("at")) {
                idx += 2;
                if (word(t, idx).equals("npc")) {
                    ExprParser.Parsed np = ExprParser.parseAtom(t, idx + 1);
                    lookNpc = np.expression();
                    idx = np.end();
                } else if (idx + 2 < t.size() && looksCoordOrRel(t.get(idx))
                        && looksCoordOrRel(t.get(idx + 1)) && looksCoordOrRel(t.get(idx + 2))) {
                    lx = t.get(idx).value().startsWith("~") ? new Expression.Text(t.get(idx).value()) : expression(t.get(idx));
                    ly = t.get(idx + 1).value().startsWith("~") ? new Expression.Text(t.get(idx + 1).value()) : expression(t.get(idx + 1));
                    lz = t.get(idx + 2).value().startsWith("~") ? new Expression.Text(t.get(idx + 2).value()) : expression(t.get(idx + 2));
                    idx += 3;
                } else {
                    TargetParser.ParsedEntity ep = TargetParser.parseEntity(t, idx);
                    if (ep != null) { lookEnt = ep.target(); idx = ep.end(); }
                    else { ExprParser.Parsed lp = ExprParser.parseAtom(t, idx); lookPlace = lp.expression(); idx = lp.end(); }
                }
            } else if (w.equals("lock") || w.equals("lock_move")) {
                idx++;
                if (idx < t.size() && (word(t, idx).equals("true") || word(t, idx).equals("false"))) {
                    lock = Boolean.parseBoolean(word(t, idx));
                    idx++;
                } else lock = true;
            } else if (w.equals("unlock") || w.equals("free_move")) {
                lock = false; idx++;
            } else if (w.equals("free")) {
                lock = false; lockLook = false; idx++;
            } else if (w.equals("lock_look") || w.equals("lock_head")) {
                idx++;
                if (idx < t.size() && (word(t, idx).equals("true") || word(t, idx).equals("false"))) {
                    lockLook = Boolean.parseBoolean(word(t, idx));
                    idx++;
                } else lockLook = true;
            } else if (w.equals("free_look") || w.equals("free_head") || w.equals("unlock_look") || w.equals("unlock_head")) {
                lockLook = false; idx++;
            } else if (w.equals("hide_hand")) {
                idx++;
                if (idx < t.size() && (word(t, idx).equals("true") || word(t, idx).equals("false"))) {
                    hideHand = Boolean.parseBoolean(word(t, idx));
                    idx++;
                } else hideHand = true;
            } else if (w.equals("show_hand")) {
                hideHand = false; idx++;
            } else if (w.equals("hand")) {
                idx++;
                if (idx < t.size() && (word(t, idx).equals("true") || word(t, idx).equals("false") || word(t, idx).equals("show") || word(t, idx).equals("hide"))) {
                    String hw = word(t, idx);
                    hideHand = hw.equals("false") || hw.equals("hide");
                    idx++;
                } else hideHand = false;
            } else {
                throw new ParseException("неизвестный параметр move camera: '" + t.get(idx).value() + "'");
            }
        }
        return new Effects.CameraCutscene(false, target, null, fromP.expression(), toP.expression(), duration, ticksPerUnit,
                lookEnt, lookNpc, lookPlace, lx, ly, lz, lock, lockLook, hideHand);
    }

    private Action parseStopCutscene(List<Token> t) throws ParseException {
        Target target = null;
        if (t.size() > 2 && (word(t, 2).equals("of") || word(t, 2).equals("for"))) {
            TargetParser.Parsed tp = TargetParser.parse(t, 3);
            if (tp != null) target = tp.target();
        }
        return new Effects.CameraCutscene(true, target, null, null, null, null, 1, null, null, null, null, null, null, false, false, false);
    }

    private static boolean looksCoordOrRel(Token tok) {
        if (tok.quoted()) return false;
        if (tok.value().startsWith("~")) return true;
        return looksNumericOrVar(tok);
    }

    private static boolean isTimeUnitWord(String w) {
        return switch (w) {
            case "t", "tick", "ticks", "s", "sec", "secs", "second", "seconds" -> true;
            default -> false;
        };
    }

    private static int timeUnitMultiplier(String w) {
        return switch (w) {
            case "s", "sec", "secs", "second", "seconds" -> 20;
            default -> 1;
        };
    }

    private void error(Line line, String en, String ru) {
        errors.add(currentFile + ":" + line.number() + ": " + com.scriptcraft.lang.Phrase.pick(en, ru));
    }

    private void error(Line line, String message) {
        errors.add(currentFile + ":" + line.number() + ": " + com.scriptcraft.lang.Phrase.localize(message));
    }
}
