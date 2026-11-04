package com.scriptcraft.script.parser;

import com.scriptcraft.script.parser.Tokenizer.Token;
import com.scriptcraft.script.runtime.Expression;
import com.scriptcraft.script.runtime.Target;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Разбор цели (кому применять действие). Цель может занимать несколько слов, поэтому парсер
 * получает токены и позицию и возвращает цель + позицию после неё.
 *
 * <pre>
 * player | %player%                           — игрок из контекста
 * loop-player                                 — текущий игрок цикла "loop ...:"
 * all players | @a                            — все онлайн
 * @p | @r | @s | @a[distance=..10]            — ванильные селекторы (только игроки, @e нельзя)
 * targeted player [of &lt;цель&gt;]              — на кого смотрит игрок (луч до targetedPlayerRange блоков)
 * target of &lt;цель&gt;                           — то же самое
 * [other] players in radius N of &lt;цель&gt;      — игроки в радиусе N (тот же мир); other — без самих центров
 * [other] players within N [blocks] of &lt;цель&gt; — то же самое
 * "Steve" | {var} | Steve                     — игрок по имени
 * </pre>
 * Разбор детерминированный (без перебора): каждая форма однозначно определяется первыми словами.
 */
final class TargetParser {
    /** Результат: цель и индекс первого токена после неё. */
    record Parsed(Target target, int end) {
    }

    private static final Pattern SELECTOR = Pattern.compile("@[aprs](\\[.*])?", Pattern.DOTALL);

    private TargetParser() {
    }

    /**
     * @return цель или null, если с позиции i цель не начинается
     * @throws ParseException начало формы узнано ("players ...", "target of ..."), но дальше ошибка
     */
    @Nullable
    static Parsed parse(List<Token> t, int i) throws ParseException {
        if (i >= t.size()) {
            return null;
        }
        Token tok = t.get(i);
        if (tok.quoted()) {
            return new Parsed(new Target.Named(new Expression.Text(tok.value())), i + 1);
        }
        String v = tok.value();
        String w = v.toLowerCase(Locale.ROOT);
        if (v.length() > 2 && v.startsWith("{") && v.endsWith("}")) {
            return new Parsed(new Target.Named(ScriptParser.expression(tok)), i + 1);
        }
        switch (w) {
            case "player", "%player%" -> {
                return new Parsed(new Target.Self(), i + 1);
            }
            case "loop-player" -> {
                return new Parsed(new Target.LoopPlayer(), i + 1);
            }
            default -> {
            }
        }
        if (v.startsWith("@")) {
            if (!SELECTOR.matcher(v).matches()) {
                throw new ParseException("неподдерживаемый селектор '" + v + "': можно @a, @p, @r, @s (и с [аргументами]), @e нельзя — только игроки");
            }
            if (v.equals("@a")) {
                return new Parsed(new Target.All(), i + 1);
            }
            return new Parsed(new Target.Selector(v), i + 1);
        }
        if (w.equals("all") && word(t, i + 1).equals("players")) {
            return new Parsed(new Target.All(), i + 2);
        }
        if (w.equals("targeted") && word(t, i + 1).equals("player")) {
            if (word(t, i + 2).equals("of")) {
                Parsed base = required(t, i + 3, "targeted player of <цель>");
                return new Parsed(new Target.Targeted(base.target()), base.end());
            }
            return new Parsed(new Target.Targeted(new Target.Self()), i + 2);
        }
        if (w.equals("target") && word(t, i + 1).equals("of")) {
            Parsed base = required(t, i + 2, "target of <цель>");
            return new Parsed(new Target.Targeted(base.target()), base.end());
        }
        boolean other = w.equals("other") && word(t, i + 1).equals("players");
        int j = other ? i + 1 : i;
        if (word(t, j).equals("players")) {
            int n;
            if (word(t, j + 1).equals("in") && word(t, j + 2).equals("radius")) {
                n = j + 3;
            } else if (word(t, j + 1).equals("within")) {
                n = j + 2;
            } else {
                throw new ParseException("ожидается: players in radius N of <цель>");
            }
            if (n >= t.size()) {
                throw new ParseException("не указан радиус: players in radius 10 of player");
            }
            Expression radius = ScriptParser.expression(t.get(n));
            if (radius instanceof Expression.Text) {
                throw new ParseException("радиус должен быть числом или {переменной}, а не '" + t.get(n).value() + "'");
            }
            int k = n + 1;
            if (word(t, k).equals("blocks") || word(t, k).equals("block")) {
                k++;
            }
            if (!word(t, k).equals("of") && !word(t, k).equals("around")) {
                throw new ParseException("ожидается 'of <цель>': players in radius 10 of player");
            }
            Parsed center = required(t, k + 1, "players in radius N of <цель>");
            return new Parsed(new Target.InRadius(radius, center.target(), other), center.end());
        }
        // Любое другое слово — имя игрока (как раньше: heal Steve, heal %args%).
        return new Parsed(new Target.Named(ScriptParser.expression(tok)), i + 1);
    }

    /** Цель, которая должна занять ровно токены [from, size). */
    static Target parseWhole(List<Token> t, int from, String example) throws ParseException {
        Parsed p = required(t, from, example);
        if (p.end() != t.size()) {
            throw new ParseException("лишние слова после цели: '" + t.get(p.end()).value() + "' (пример: " + example + ")");
        }
        return p.target();
    }

    private static Parsed required(List<Token> t, int i, String example) throws ParseException {
        Parsed p = parse(t, i);
        if (p == null) {
            throw new ParseException("не указана цель (пример: " + example + ")");
        }
        return p;
    }

    private static String word(List<Token> t, int i) {
        return i < t.size() && !t.get(i).quoted() ? t.get(i).value().toLowerCase(Locale.ROOT) : "";
    }
}
