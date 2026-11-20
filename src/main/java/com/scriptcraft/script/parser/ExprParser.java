package com.scriptcraft.script.parser;

import com.scriptcraft.script.parser.Tokenizer.Token;
import com.scriptcraft.script.runtime.Expression;
import com.scriptcraft.script.runtime.Target;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Значение из нескольких слов: health of player, random integer between 1 and 10,
 * number of all players, либо один токен (число, "текст", {переменная}).
 * Останавливается перед оператором условия (is, and, or, &gt;, ...), не съедая его.
 */
final class ExprParser {
    record Parsed(Expression expression, int end) {
    }

    private static final Set<String> STOP = Set.of("is", "and", "or", ">", "<", ">=", "<=", "=", "==", "!=");

    /** Имена свойств от длинных к коротким, чтобы "food level" не разобралось как "food". */
    private static final Object[][] PROPS = {
            {words("max", "health"), Expression.Prop.MAX_HEALTH},
            {words("max", "air"), Expression.Prop.MAX_AIR},
            {words("food", "level"), Expression.Prop.FOOD},
            {words("xp", "level"), Expression.Prop.LEVEL},
            {words("walk", "speed"), Expression.Prop.WALK_SPEED},
            {words("fly", "speed"), Expression.Prop.FLY_SPEED},
            {words("flying", "speed"), Expression.Prop.FLY_SPEED},
            {words("held", "item"), Expression.Prop.HELD},
            {words("game", "mode"), Expression.Prop.GAMEMODE},
            {words("x-coordinate"), Expression.Prop.X},
            {words("y-coordinate"), Expression.Prop.Y},
            {words("z-coordinate"), Expression.Prop.Z},
            {words("x-coord"), Expression.Prop.X},
            {words("y-coord"), Expression.Prop.Y},
            {words("z-coord"), Expression.Prop.Z},
            {words("gamemode"), Expression.Prop.GAMEMODE},
            {words("absorption"), Expression.Prop.ABSORPTION},
            {words("saturation"), Expression.Prop.SATURATION},
            {words("health"), Expression.Prop.HEALTH},
            {words("hunger"), Expression.Prop.FOOD},
            {words("food"), Expression.Prop.FOOD},
            {words("oxygen"), Expression.Prop.AIR},
            {words("level"), Expression.Prop.LEVEL},
            {words("tool"), Expression.Prop.HELD},
            {words("location"), Expression.Prop.LOCATION},
            {words("world"), Expression.Prop.WORLD},
            {words("name"), Expression.Prop.NAME},
            {words("uuid"), Expression.Prop.UUID},
            {words("ping"), Expression.Prop.PING},
            {words("yaw"), Expression.Prop.YAW},
            {words("pitch"), Expression.Prop.PITCH},
            {words("air"), Expression.Prop.AIR},
            {words("experience"), Expression.Prop.XP},
            {words("xp"), Expression.Prop.XP},
            {words("x"), Expression.Prop.X},
            {words("y"), Expression.Prop.Y},
            {words("z"), Expression.Prop.Z},
    };

    private ExprParser() {
    }

    static Parsed parse(List<Token> t, int i) throws ParseException {
        return parseAdd(t, i);
    }

    private static Parsed parseAdd(List<Token> t, int i) throws ParseException {
        Parsed left = parseMul(t, i);
        while (left.end() < t.size()) {
            String op = word(t, left.end());
            if (!op.equals("+") && !op.equals("-")) {
                break;
            }
            Parsed right = parseMul(t, left.end() + 1);
            left = new Parsed(new Expression.MathBinary(left.expression(), op, right.expression()), right.end());
        }
        return left;
    }

    private static Parsed parseMul(List<Token> t, int i) throws ParseException {
        Parsed left = parsePow(t, i);
        while (left.end() < t.size()) {
            String op = word(t, left.end());
            if (!op.equals("*") && !op.equals("/") && !op.equals("%")) {
                break;
            }
            Parsed right = parsePow(t, left.end() + 1);
            left = new Parsed(new Expression.MathBinary(left.expression(), op, right.expression()), right.end());
        }
        return left;
    }

    private static Parsed parsePow(List<Token> t, int i) throws ParseException {
        Parsed left = parseAtom(t, i);
        if (left.end() < t.size() && word(t, left.end()).equals("^")) {
            Parsed right = parsePow(t, left.end() + 1);
            return new Parsed(new Expression.MathBinary(left.expression(), "^", right.expression()), right.end());
        }
        return left;
    }

    private static Parsed parseAtom(List<Token> t, int i) throws ParseException {
        if (i >= t.size()) {
            throw new ParseException("ожидается значение");
        }
        // word() для кавычек возвращает "" (это не слово команды). Текст в кавычках — готовое значение,
        // иначе set {a} to "сегодня" падает с «ожидается значение».
        if (t.get(i).quoted()) {
            return new Parsed(ScriptParser.expression(t.get(i)), i + 1);
        }
        String w = word(t, i);
        if (w.equals("(")) {
            Parsed inner = parseAdd(t, i + 1);
            if (!word(t, inner.end()).equals(")")) {
                throw new ParseException("ожидается закрывающая скобка ')'");
            }
            return new Parsed(inner.expression(), inner.end() + 1);
        }
        if (w.equals("round") || w.equals("floor") || w.equals("ceil") || w.equals("abs")
                || w.equals("sqrt") || w.equals("sin") || w.equals("cos") || w.equals("tan")) {
            Parsed arg = parseAtom(t, i + 1);
            return new Parsed(new Expression.MathUnary(w, arg.expression()), arg.end());
        }
        if (w.equals("min") || w.equals("max") || w.equals("pow")) {
            Parsed a = parseAtom(t, i + 1);
            int next = word(t, a.end()).equals("and") ? a.end() + 1 : a.end();
            Parsed b = parseAtom(t, next);
            return new Parsed(new Expression.MathBinary(a.expression(), w, b.expression()), b.end());
        }
        if (w.equals("clamp")) {
            Parsed v = parseAtom(t, i + 1);
            Parsed lo = parseAtom(t, v.end());
            Parsed hi = parseAtom(t, lo.end());
            return new Parsed(new Expression.MathClamp(v.expression(), lo.expression(), hi.expression()), hi.end());
        }
        if (w.equals("random")) {
            return parseRandom(t, i);
        }
        if (w.equals("number") && word(t, i + 1).equals("of")
                && (word(t, i + 2).equals("all") || word(t, i + 2).equals("online"))
                && word(t, i + 3).equals("players")) {
            return new Parsed(new Expression.Property(Expression.Prop.ONLINE, null), i + 4);
        }
        Parsed element = tryElement(t, i);
        if (element != null) {
            return element;
        }
        Parsed prop = tryProperty(t, i);
        if (prop != null) {
            return prop;
        }
        Parsed own = tryPossessive(t, i);
        if (own != null) {
            return own;
        }
        Parsed arg = tryArgument(t, i);
        if (arg != null) {
            return arg;
        }
        if (w.isEmpty() || STOP.contains(w)) {
            throw new ParseException("ожидается значение, а не '" + (i < t.size() ? t.get(i).value() : "") + "'");
        }
        return new Parsed(ScriptParser.expression(t.get(i)), i + 1);
    }

    private static Parsed parseRandom(List<Token> t, int i) throws ParseException {
        String kind = word(t, i + 1);
        if (kind.equals("element")) {
            int at = i + 2;
            if (word(t, at).equals("out")) at++;
            if (word(t, at).equals("of") || word(t, at).equals("from")) at++;
            if (at >= t.size() || !t.get(at).value().startsWith("{") || !t.get(at).value().endsWith("::*}")) {
                throw new ParseException("нужно: random element out of {список::*}");
            }
            String raw = t.get(at).value();
            return new Parsed(new Expression.RandomElement(raw.substring(1, raw.length() - 1)), at + 1);
        }
        boolean integer = kind.equals("integer") || kind.equals("int");
        boolean number = kind.equals("number");
        if (!integer && !number) {
            throw new ParseException("нужно: random integer between 1 and 10  или  random number between 0 and 1  или  random element out of {list::*}");
        }
        if (!word(t, i + 2).equals("between")) {
            throw new ParseException("нужно: random " + kind + " between <число> and <число>");
        }
        Parsed a = parseAtom(t, i + 3);
        if (!word(t, a.end()).equals("and")) {
            throw new ParseException("нужно: random " + kind + " between <число> and <число>");
        }
        Parsed b = parseAtom(t, a.end() + 1);
        Expression value = integer
                ? new Expression.RandomInt(a.expression(), b.expression())
                : new Expression.RandomNum(a.expression(), b.expression());
        return new Parsed(value, b.end());
    }

    /**
     * targeted block|entity|player|location, location of targeted ..., location at x y z,
     * spawn of &lt;цель&gt;, world spawn.
     */
    private static Parsed tryElement(List<Token> t, int i) throws ParseException {
        String w = word(t, i);
        FuncType local = ScriptParser.localType(w);
        if (local != null) {
            String next = word(t, i + 1);
            if (!next.equals("of") && !next.equals("at") && !next.equals("between")) {
                String key = w;
                if (key.length() > 2 && key.startsWith("%") && key.endsWith("%")) {
                    key = key.substring(1, key.length() - 1);
                }
                return new Parsed(new Expression.Local(key), i + 1);
            }
        }
        Parsed luck = tryLuckPerms(t, i);
        if (luck != null) {
            return luck;
        }
        Parsed items = tryItemNbt(t, i);
        if (items != null) {
            return items;
        }
        if (w.equals("player") || w.equals("%player%")) {
            return new Parsed(new Expression.Property(Expression.Prop.NAME, new Target.Self()), i + 1);
        }
        if (w.equals("loop-player")) {
            return new Parsed(new Expression.Property(Expression.Prop.NAME, new Target.LoopPlayer()), i + 1);
        }
        if (w.equals("clicked")) {
            String kind = word(t, i + 1);
            if (kind.equals("entity")) {
                return new Parsed(new Expression.EventValue("clicked_entity"), i + 2);
            }
            if (kind.equals("player")) {
                return new Parsed(new Expression.EventValue("clicked_player"), i + 2);
            }
            if (kind.equals("block")) {
                return new Parsed(new Expression.EventValue("block"), i + 2);
            }
        }
        if (w.equals("targeted")) {
            String kind = word(t, i + 1);
            if (kind.equals("player") && word(t, i + 2).equals("of")) {
                return null;
            }
            if (kind.equals("block") || kind.equals("entity") || kind.equals("player") || kind.equals("location")) {
                return new Parsed(new Expression.Look(kind), i + 2);
            }
        }
        if (w.equals("location") && word(t, i + 1).equals("of") && word(t, i + 2).equals("targeted")) {
            String kind = word(t, i + 3);
            if (kind.equals("block") || kind.equals("entity") || kind.equals("player")) {
                return new Parsed(new Expression.Look("loc-" + kind), i + 4);
            }
        }
        if (w.equals("location") && word(t, i + 1).equals("at")) {
            Parsed x = parse(t, i + 2);
            Parsed y = parse(t, x.end());
            Parsed z = parse(t, y.end());
            return new Parsed(new Expression.LocationAt(x.expression(), y.expression(), z.expression()), z.end());
        }
        if (w.equals("world") && word(t, i + 1).equals("spawn")) {
            return new Parsed(new Expression.Spawn(null, true), i + 2);
        }
        if (w.equals("spawn") && word(t, i + 1).equals("of")) {
            TargetParser.Parsed target = TargetParser.parse(t, i + 2);
            if (target == null) {
                throw new ParseException("нужно: spawn of <цель>");
            }
            return new Parsed(new Expression.Spawn(target.target(), false), target.end());
        }
        return null;
    }

    private static Parsed tryLuckPerms(List<Token> t, int i) throws ParseException {
        String w = word(t, i);
        if (w.equals("prefix") || w.equals("suffix") || w.equals("group") || w.equals("groups")) {
            if (!word(t, i + 1).equals("of")) {
                return null;
            }
            TargetParser.Parsed target = TargetParser.parse(t, i + 2);
            if (target == null) {
                throw new ParseException("need: " + w + " of <target>", "нужно: " + w + " of <цель>");
            }
            return new Parsed(new Expression.LpValue(w, target.target(), null), target.end());
        }
        if (!w.equals("meta") || i + 1 >= t.size() || word(t, i + 1).equals("of")) {
            if (w.equals("meta") && word(t, i + 1).equals("of")) {
                throw new ParseException("need: meta <key> of <target>", "нужно: meta <ключ> of <цель>");
            }
            return null;
        }
        Parsed key = parse(t, i + 1);
        if (!word(t, key.end()).equals("of")) {
            return null;
        }
        TargetParser.Parsed target = TargetParser.parse(t, key.end() + 1);
        if (target == null) {
            throw new ParseException("need: meta <key> of <target>", "нужно: meta <ключ> of <цель>");
        }
        return new Parsed(new Expression.LpValue("meta", target.target(), key.expression()), target.end());
    }

    private static Parsed tryItemNbt(List<Token> t, int i) throws ParseException {
        if (!word(t, i).equals("nbt")) {
            return null;
        }
        if (word(t, i + 1).equals("of") || word(t, i + 1).equals("from")) {
            return itemNbt(t, i + 1, null);
        }
        if (i + 1 >= t.size()) {
            return null;
        }
        Parsed path = parse(t, i + 1);
        if (word(t, path.end()).equals("of") && word(t, path.end() + 1).equals("slot")) {
            return slotNbt(t, path.end() + 2, path.expression());
        }
        return null;
    }

    private static Parsed itemNbt(List<Token> t, int i, Expression path) throws ParseException {
        if (word(t, i).equals("from") || (word(t, i).equals("of") && word(t, i + 1).equals("items") && word(t, i + 2).equals("in"))) {
            int at = word(t, i).equals("from") ? i + 1 : i + 1;
            if (word(t, i).equals("from") && !(word(t, i + 1).equals("items") && word(t, i + 2).equals("in"))) {
                throw new ParseException("need: nbt from items in player's inventory",
                        "нужно: nbt from items in player's inventory");
            }
            return inventoryTarget(t, at + 2);
        }
        if (word(t, i).equals("of") && word(t, i + 1).equals("slot")) {
            return slotNbt(t, i + 2, path);
        }
        if (word(t, i).equals("of") && word(t, i + 1).equals("player's") && word(t, i + 2).equals("inventory")) {
            return new Parsed(new Expression.InventoryNbt(playerTarget()), i + 3);
        }
        throw new ParseException("need: nbt of player's inventory or nbt of slot <slot> of <target>",
                "нужно: nbt of player's inventory или nbt of slot <слот> of <цель>");
    }

    private static Parsed inventoryTarget(List<Token> t, int i) throws ParseException {
        if (word(t, i).equals("player's") && word(t, i + 1).equals("inventory")) {
            return new Parsed(new Expression.InventoryNbt(playerTarget()), i + 2);
        }
        throw new ParseException("need: nbt from items in player's inventory",
                "нужно: nbt from items in player's inventory");
    }

    private static Parsed slotNbt(List<Token> t, int i, Expression path) throws ParseException {
        boolean hotbar = word(t, i).equals("hotbar");
        if (hotbar) {
            i++;
        }
        Parsed slot = parse(t, i);
        if (!word(t, slot.end()).equals("of")) {
            throw new ParseException("need: nbt of slot <slot> of <target>", "нужно: nbt of slot <слот> of <цель>");
        }
        int at = slot.end() + 1;
        if (word(t, at).equals("player's") && word(t, at + 1).equals("inventory")) {
            return new Parsed(new Expression.SlotNbt(playerTarget(), slot.expression(), hotbar, path), at + 2);
        }
        TargetParser.Parsed target = TargetParser.parse(t, at);
        if (target == null) {
            throw new ParseException("need: nbt of slot <slot> of <target>", "нужно: nbt of slot <слот> of <цель>");
        }
        int end = target.end();
        if (word(t, end).equals("inventory")) {
            end++;
        }
        return new Parsed(new Expression.SlotNbt(target.target(), slot.expression(), hotbar, path), end);
    }

    private static Target playerTarget() {
        FuncType local = ScriptParser.localType("player");
        if (local == FuncType.PLAYER || local == FuncType.OFFLINE) {
            return new Target.Local("player");
        }
        return new Target.Self();
    }

    /** player's tool, loop-player's location, targeted player's health. */
    private static Parsed tryPossessive(List<Token> t, int i) throws ParseException {
        String w = word(t, i);
        Target target = null;
        int from = i + 1;
        if (w.equals("player's")) {
            FuncType local = ScriptParser.localType("player");
            target = local == FuncType.PLAYER || local == FuncType.OFFLINE
                    ? new Target.Local("player") : new Target.Self();
        } else if (w.equals("loop-player's")) {
            target = new Target.LoopPlayer();
        } else if (w.equals("targeted") && word(t, i + 1).equals("player's")) {
            target = new Target.Targeted(new Target.Self());
            from = i + 2;
        }
        if (target == null) {
            return null;
        }
        for (Object[] row : PROPS) {
            String[] name = (String[]) row[0];
            if (!matches(t, from, name)) {
                continue;
            }
            return new Parsed(new Expression.Property((Expression.Prop) row[1], target), from + name.length);
        }
        return null;
    }

    /** arg 1, argument 2, offline player-argument. Одно слово (arg-1, script) разбирает ScriptParser.expression. */
    private static Parsed tryArgument(List<Token> t, int i) {
        String w = word(t, i);
        if ((w.equals("arg") || w.equals("argument")) && word(t, i + 1).matches("[1-9]\\d*")) {
            return new Parsed(new Expression.Arg(Integer.parseInt(word(t, i + 1)), null), i + 2);
        }
        if (w.equals("offline") && word(t, i + 1).equals("player-argument")) {
            return new Parsed(new Expression.Arg(0, com.scriptcraft.script.runtime.ArgType.OFFLINE), i + 2);
        }
        return null;
    }

    private static Parsed tryProperty(List<Token> t, int i) throws ParseException {
        for (Object[] row : PROPS) {
            String[] name = (String[]) row[0];
            if (!matches(t, i, name) || !word(t, i + name.length).equals("of")) {
                continue;
            }
            TargetParser.Parsed target = TargetParser.parse(t, i + name.length + 1);
            if (target == null) {
                throw new ParseException("после 'of' нужна цель, например health of player");
            }
            return new Parsed(new Expression.Property((Expression.Prop) row[1], target.target()), target.end());
        }
        return null;
    }

    private static boolean matches(List<Token> t, int i, String[] name) {
        for (int k = 0; k < name.length; k++) {
            if (!word(t, i + k).equals(name[k])) {
                return false;
            }
        }
        return true;
    }

    private static String[] words(String... parts) {
        return parts;
    }

    private static String word(List<Token> t, int i) {
        return i < t.size() && !t.get(i).quoted() ? t.get(i).value().toLowerCase(Locale.ROOT) : "";
    }
}
