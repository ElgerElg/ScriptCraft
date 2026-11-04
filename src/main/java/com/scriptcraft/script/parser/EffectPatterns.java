package com.scriptcraft.script.parser;

import com.scriptcraft.script.parser.Tokenizer.Token;
import com.scriptcraft.script.runtime.Action;
import com.scriptcraft.script.runtime.Effects;
import com.scriptcraft.script.runtime.Expression;
import com.scriptcraft.script.runtime.HudActions;
import com.scriptcraft.script.runtime.Target;
import com.scriptcraft.hud.VanillaHudPart;
import com.scriptcraft.screen.BarElement;
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
    private static final Pattern HUD_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
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

        // --- HUD (create hud "..." — блок с элементами, разбирается в ScriptParser)
        // Обновление полосы: значение — число, {переменная} или источник клиента (health, food...).
        rule("set value|progress of bar %text% for|of %target% to %value%",
                c -> new HudActions.SetBar(c.target(1), hudName(c.text(0)), barValue(c.raw(2)), null));
        rule("set value|progress of bar %text% to %value%",
                c -> new HudActions.SetBar(new Target.Self(), hudName(c.text(0)), barValue(c.raw(1)), null));
        rule("set max of bar %text% for|of %target% to %value%",
                c -> new HudActions.SetBar(c.target(1), hudName(c.text(0)), null, barValue(c.raw(2))));
        rule("set max of bar %text% to %value%",
                c -> new HudActions.SetBar(new Target.Self(), hudName(c.text(0)), null, barValue(c.raw(1))));
        rule("set text of hud %text% for|of %target% to %text%",
                c -> new HudActions.SetText(c.target(1), hudName(c.text(0)), c.text(2)));
        rule("set text of hud %text% to %text%",
                c -> new HudActions.SetText(new Target.Self(), hudName(c.text(0)), c.text(1)));
        // show/hide/remove hud "имя": имя элемента или целой группы (create hud "группа")
        rule("show hud %text% to|for %target%", c -> new HudActions.Visibility(c.target(1), hudName(c.text(0)), true));
        rule("show hud %text%", c -> new HudActions.Visibility(new Target.Self(), hudName(c.text(0)), true));
        rule("hide hud %text% from|for %target%", c -> new HudActions.Visibility(c.target(1), hudName(c.text(0)), false));
        rule("hide hud %text%", c -> new HudActions.Visibility(new Target.Self(), hudName(c.text(0)), false));
        rule("remove hud %text% from|for %target%", c -> new HudActions.Remove(c.target(1), hudName(c.text(0))));
        rule("remove hud %text%", c -> new HudActions.Remove(new Target.Self(), hudName(c.text(0))));
        rule("clear hud of|for %target%", c -> new HudActions.Clear(c.target(0)));
        rule("clear hud", c -> new HudActions.Clear(new Target.Self()));
        // Части ванильного HUD: hide vanilla hotbar for player / show vanilla health for all players
        rule("hide vanilla %part% for|from %target%", c -> new HudActions.Vanilla(c.target(1), vanillaPart(c.raw(0)), true));
        rule("hide vanilla %part%", c -> new HudActions.Vanilla(new Target.Self(), vanillaPart(c.raw(0)), true));
        rule("show vanilla %part% for|to %target%", c -> new HudActions.Vanilla(c.target(1), vanillaPart(c.raw(0)), false));
        rule("show vanilla %part%", c -> new HudActions.Vanilla(new Target.Self(), vanillaPart(c.raw(0)), false));

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

    /** Имя элемента/группы HUD: латиница, цифры, _ . - (до 64 символов). */
    static String hudName(String raw) throws ParseException {
        String v = raw.strip();
        if (!HUD_NAME.matcher(v).matches()) {
            throw new ParseException("имя HUD должно быть латиницей (буквы, цифры, _ . -), а не '" + raw + "'");
        }
        return v;
    }

    /** Значение полосы: число, {переменная} или источник клиента (health, food...). */
    private static Expression barValue(Token t) throws ParseException {
        Expression e = ScriptParser.expression(t);
        if (e instanceof Expression.Text && !t.quoted() && !BarElement.isSource(t.value())) {
            throw new ParseException("ожидается число, {переменная} или источник (" + String.join(", ", BarElement.SOURCES)
                    + "), а не '" + t.value() + "'");
        }
        return e;
    }

    private static int vanillaPart(Token t) throws ParseException {
        int mask = t.quoted() ? -1 : VanillaHudPart.maskByName(t.value());
        if (mask < 0) {
            throw new ParseException("неизвестная часть ванильного HUD: '" + t.value() + "' (" + VanillaHudPart.names() + ")");
        }
        return mask;
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
