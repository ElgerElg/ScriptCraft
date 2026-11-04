package com.scriptcraft.script.parser;

import com.scriptcraft.script.parser.Tokenizer.Token;
import com.scriptcraft.script.runtime.Action;
import com.scriptcraft.script.runtime.Effects;
import com.scriptcraft.script.runtime.Expression;
import com.scriptcraft.script.runtime.Target;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Таблица шаблонов эффектов в стиле Skript. Один шаблон = одна строка {@code rule("...", builder)}.
 *
 * Части шаблона через пробел:
 * - слово (можно варианты через '|': "seconds|second|sec") — должно совпасть без учёта регистра и без кавычек;
 * - %target% — цель из нескольких слов (all players, players in radius 5 of player, @a[...]) — см. TargetParser;
 * - %text% — только текст в кавычках;
 * - любое другое %что-то% — ровно один токен, его забирает builder: num, id, mode, bool...
 * Строка должна совпасть с шаблоном целиком, необязательные части оформляются отдельными шаблонами.
 * Шаблоны проверяются по порядку, первый подошедший побеждает — более длинные/конкретные ставим раньше.
 */
final class EffectPatterns {
    @FunctionalInterface
    private interface Builder {
        Action build(Captures c) throws ParseException;
    }

    @FunctionalInterface
    private interface ValueBuilder {
        Action build(Target target, Token value) throws ParseException;
    }

    private record Rule(String[][] parts, Builder builder) {
    }

    private static final List<Rule> RULES = new ArrayList<>();
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+(:[a-z0-9_./-]+)?");
    private static final String SECONDS = "seconds|second|secs|sec";

    static {
        // --- режим игры и характеристики: "set X of <цель> to <значение>" и "set player's X to <значение>"
        setter("gamemode", (t, v) -> new Effects.SetGameMode(t, gameMode(v)));
        setter("game mode", (t, v) -> new Effects.SetGameMode(t, gameMode(v)));
        setter("health", (t, v) -> new Effects.SetHealth(t, num(v)));
        setter("food level", (t, v) -> new Effects.SetFood(t, num(v)));
        setter("hunger", (t, v) -> new Effects.SetFood(t, num(v)));
        setter("saturation", (t, v) -> new Effects.SetSaturation(t, num(v)));
        setter("xp level", (t, v) -> new Effects.SetXpLevel(t, num(v)));
        setter("level", (t, v) -> new Effects.SetXpLevel(t, num(v)));
        setter("flying", (t, v) -> new Effects.SetFlying(t, ScriptParser.expression(v)));
        setter("flight", (t, v) -> new Effects.SetFlying(t, ScriptParser.expression(v)));

        // --- здоровье и голод
        rule("heal %target%", c -> new Effects.Heal(c.target(0), null));
        rule("heal %target% by %num%", c -> new Effects.Heal(c.target(0), c.num(1)));
        rule("damage %target% by %num%", c -> new Effects.Damage(c.target(0), c.num(1)));
        rule("feed %target%", c -> new Effects.Feed(c.target(0), null));
        rule("feed %target% by %num%", c -> new Effects.Feed(c.target(0), c.num(1)));
        rule("kill %target%", c -> new Effects.Kill(c.target(0)));
        rule("extinguish %target%", c -> new Effects.Extinguish(c.target(0)));
        rule("ignite %target% for %num% " + SECONDS, c -> new Effects.Ignite(c.target(0), c.num(1)));
        rule("clear inventory of %target%", c -> new Effects.ClearInventory(c.target(0)));
        rule("clear player's inventory", c -> new Effects.ClearInventory(new Target.Self()));

        // --- эффекты зелий
        rule("give %target% effect %id% for %num% " + SECONDS,
                c -> new Effects.GiveEffect(c.target(0), c.id(1), c.num(2), null));
        rule("give %target% effect %id% for %num% " + SECONDS + " level|lvl %num%",
                c -> new Effects.GiveEffect(c.target(0), c.id(1), c.num(2), c.num(3)));
        rule("remove effect %id% from %target%", c -> new Effects.RemoveEffect(c.target(1), c.id(0)));
        rule("clear effects of %target%", c -> new Effects.ClearEffects(c.target(0)));
        rule("clear all effects of %target%", c -> new Effects.ClearEffects(c.target(0)));

        // --- звук, титры, опыт
        rule("play sound %id% to %target%", c -> new Effects.PlaySound(c.target(1), c.id(0)));
        rule("send title %text% to %target%", c -> new Effects.Title(c.target(1), c.text(0), null));
        rule("send title %text% with subtitle %text% to %target%", c -> new Effects.Title(c.target(2), c.text(0), c.text(1)));
        rule("send action bar %text% to %target%", c -> new Effects.ActionBar(c.target(1), c.text(0)));
        rule("send actionbar %text% to %target%", c -> new Effects.ActionBar(c.target(1), c.text(0)));
        rule("give %num% xp|experience to %target%", c -> new Effects.GiveXp(c.target(1), c.num(0)));

        // --- сообщения, предметы, телепорт (раньше были отдельными действиями только для "player")
        rule("send %text%", c -> new Effects.SendMessage(new Target.Self(), c.text(0)));
        rule("send %text% to %target%", c -> new Effects.SendMessage(c.target(1), c.text(0)));
        rule("message %text% to %target%", c -> new Effects.SendMessage(c.target(1), c.text(0)));
        // give <цель> <предмет> [кол-во] — после "give ... effect ..." и "give N xp ...", чтобы не перехватить их
        rule("give %target% %id%", c -> new Effects.GiveItem(c.target(0), c.id(1), null));
        rule("give %target% %id% %num%", c -> new Effects.GiveItem(c.target(0), c.id(1), c.num(2)));
        rule("teleport|tp %target% to %num% %num% %num%",
                c -> new Effects.TeleportToPos(c.target(0), c.num(1), c.num(2), c.num(3)));
        rule("teleport|tp %target% to %target%", c -> new Effects.TeleportToPlayer(c.target(0), c.target(1)));

        // --- команды (выключаются allowCommandEffects = false)
        rule("execute console command %text%", c -> new Effects.ConsoleCommand(c.text(0)));
        rule("make %target% execute command %text%", c -> new Effects.PlayerCommand(c.target(0), c.text(1)));
    }

    private EffectPatterns() {
    }

    private static void rule(String pattern, Builder builder) {
        String[] words = pattern.split(" ");
        String[][] parts = new String[words.length][];
        for (int i = 0; i < words.length; i++) {
            parts[i] = words[i].split("\\|");
        }
        RULES.add(new Rule(parts, builder));
    }

    /** Регистрирует обе формы: "set X of <цель> to <v>" и "set player's X to <v>". */
    private static void setter(String property, ValueBuilder builder) {
        rule("set " + property + " of %target% to %value%", c -> builder.build(c.target(0), c.raw(1)));
        rule("set player's " + property + " to %value%", c -> builder.build(new Target.Self(), c.raw(0)));
    }

    /**
     * Пробует разобрать строку как эффект из таблицы.
     * @return действие или null, если ни один шаблон не подошёл (тогда парсер пробует остальные действия)
     * @throws ParseException шаблон подошёл по словам, но значение неверное (например, не число),
     *                        или ни один не подошёл, а цель была записана с ошибкой ("players 5 of player")
     */
    static Action tryParse(List<Token> tokens) throws ParseException {
        ParseException targetError = null;
        for (Rule rule : RULES) {
            List<Object> captures = new ArrayList<>();
            try {
                if (!match(rule.parts(), 0, tokens, 0, captures)) {
                    continue;
                }
            } catch (ParseException e) {
                // Ошибка в записи цели: запомним, вдруг подойдёт другой шаблон.
                if (targetError == null) {
                    targetError = e;
                }
                continue;
            }
            return rule.builder().build(new Captures(captures));
        }
        if (targetError != null) {
            throw targetError;
        }
        return null;
    }

    /**
     * Сопоставление шаблона с токенами начиная с позиций pi/ti. %target% занимает столько токенов,
     * сколько разобрал TargetParser (разбор цели однозначный, поэтому перебор длины не нужен).
     */
    private static boolean match(String[][] parts, int pi, List<Token> tokens, int ti, List<Object> captures)
            throws ParseException {
        if (pi == parts.length) {
            return ti == tokens.size();
        }
        String[] alts = parts[pi];
        if (alts[0].equals("%target%")) {
            TargetParser.Parsed parsed = TargetParser.parse(tokens, ti);
            if (parsed == null) {
                return false;
            }
            captures.add(parsed.target());
            if (match(parts, pi + 1, tokens, parsed.end(), captures)) {
                return true;
            }
            captures.remove(captures.size() - 1);
            return false;
        }
        if (ti >= tokens.size()) {
            return false;
        }
        Token tok = tokens.get(ti);
        if (alts[0].startsWith("%")) {
            if (alts[0].equals("%text%") && !tok.quoted()) {
                return false;
            }
            captures.add(tok);
            if (match(parts, pi + 1, tokens, ti + 1, captures)) {
                return true;
            }
            captures.remove(captures.size() - 1);
            return false;
        }
        return !tok.quoted() && matchesAny(tok.value().toLowerCase(Locale.ROOT), alts)
                && match(parts, pi + 1, tokens, ti + 1, captures);
    }

    private static boolean matchesAny(String word, String[] alts) {
        for (String a : alts) {
            if (a.equals(word)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ преобразование значений

    private static Expression num(Token t) throws ParseException {
        Expression e = ScriptParser.expression(t);
        if (e instanceof Expression.Text && !t.quoted()) {
            throw new ParseException("ожидается число или {переменная}, а не '" + t.value() + "'");
        }
        return e;
    }

    private static GameType gameMode(Token t) throws ParseException {
        return switch (t.value().toLowerCase(Locale.ROOT)) {
            case "survival", "s", "0" -> GameType.SURVIVAL;
            case "creative", "c", "1" -> GameType.CREATIVE;
            case "adventure", "a", "2" -> GameType.ADVENTURE;
            case "spectator", "sp", "3" -> GameType.SPECTATOR;
            default -> throw new ParseException("неизвестный режим игры: " + t.value() + " (survival, creative, adventure, spectator)");
        };
    }

    /** Захваченные значения шаблона по порядку: Token для одиночных значений, Target для %target%. */
    private record Captures(List<Object> values) {
        Token raw(int i) {
            return (Token) values.get(i);
        }

        Target target(int i) {
            return (Target) values.get(i);
        }

        Expression num(int i) throws ParseException {
            return EffectPatterns.num(raw(i));
        }

        String text(int i) throws ParseException {
            Token t = raw(i);
            if (!t.quoted()) {
                throw new ParseException("ожидается текст в кавычках, а не '" + t.value() + "'");
            }
            return t.value();
        }

        String id(int i) throws ParseException {
            String v = raw(i).value().toLowerCase(Locale.ROOT);
            if (!ID.matcher(v).matches()) {
                throw new ParseException("некорректный id: '" + v + "' (пример: speed, minecraft:speed, entity.player.levelup)");
            }
            return v;
        }
    }
}
