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
 * - %что-то% — любое значение, которое забирает builder: target, num, text, id, mode, bool.
 * Длина строки должна совпасть с длиной шаблона, поэтому необязательные части оформляются отдельными шаблонами.
 *
 * Цель (%target%): player — игрок из контекста; "Steve", {var} или слово — игрок по имени (должен быть онлайн).
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
     * @throws ParseException шаблон подошёл по словам, но значение неверное (например, не число)
     */
    static Action tryParse(List<Token> tokens) throws ParseException {
        for (Rule rule : RULES) {
            if (rule.parts().length != tokens.size()) {
                continue;
            }
            List<Token> captures = new ArrayList<>();
            boolean ok = true;
            for (int i = 0; i < tokens.size() && ok; i++) {
                String[] alts = rule.parts()[i];
                Token tok = tokens.get(i);
                if (alts[0].startsWith("%")) {
                    captures.add(tok);
                } else {
                    ok = !tok.quoted() && matchesAny(tok.value().toLowerCase(Locale.ROOT), alts);
                }
            }
            if (ok) {
                return rule.builder().build(new Captures(captures));
            }
        }
        return null;
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

    /** Захваченные значения шаблона по порядку. */
    private record Captures(List<Token> tokens) {
        Token raw(int i) {
            return tokens.get(i);
        }

        Target target(int i) {
            Token t = tokens.get(i);
            if (!t.quoted() && (t.value().equalsIgnoreCase("player") || t.value().equalsIgnoreCase("%player%"))) {
                return new Target.Self();
            }
            return new Target.Named(ScriptParser.expression(t));
        }

        Expression num(int i) throws ParseException {
            return EffectPatterns.num(tokens.get(i));
        }

        String text(int i) throws ParseException {
            Token t = tokens.get(i);
            if (!t.quoted()) {
                throw new ParseException("ожидается текст в кавычках, а не '" + t.value() + "'");
            }
            return t.value();
        }

        String id(int i) throws ParseException {
            String v = tokens.get(i).value().toLowerCase(Locale.ROOT);
            if (!ID.matcher(v).matches()) {
                throw new ParseException("некорректный id: '" + v + "' (пример: speed, minecraft:speed, entity.player.levelup)");
            }
            return v;
        }
    }
}
