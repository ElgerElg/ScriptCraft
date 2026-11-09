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
        if (i >= t.size()) {
            throw new ParseException("ожидается значение");
        }
        // word() для кавычек возвращает "" (это не слово команды). Текст в кавычках — готовое значение,
        // иначе set {a} to "сегодня" падает с «ожидается значение».
        if (t.get(i).quoted()) {
            return new Parsed(ScriptParser.expression(t.get(i)), i + 1);
        }
        String w = word(t, i);
        if (w.equals("random")) {
            return parseRandom(t, i);
        }
        if (w.equals("number") && word(t, i + 1).equals("of")
                && (word(t, i + 2).equals("all") || word(t, i + 2).equals("online"))
                && word(t, i + 3).equals("players")) {
            return new Parsed(new Expression.Property(Expression.Prop.ONLINE, null), i + 4);
        }
        Parsed prop = tryProperty(t, i);
        if (prop != null) {
            return prop;
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
        boolean integer = kind.equals("integer") || kind.equals("int");
        boolean number = kind.equals("number");
        if (!integer && !number) {
            throw new ParseException("нужно: random integer between 1 and 10  или  random number between 0 and 1");
        }
        if (!word(t, i + 2).equals("between")) {
            throw new ParseException("нужно: random " + kind + " between <число> and <число>");
        }
        Parsed a = parse(t, i + 3);
        if (!word(t, a.end()).equals("and")) {
            throw new ParseException("нужно: random " + kind + " between <число> and <число>");
        }
        Parsed b = parse(t, a.end() + 1);
        Expression value = integer
                ? new Expression.RandomInt(a.expression(), b.expression())
                : new Expression.RandomNum(a.expression(), b.expression());
        return new Parsed(value, b.end());
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
