package com.scriptcraft.script.parser;

import com.scriptcraft.script.parser.Tokenizer.Token;
import com.scriptcraft.script.runtime.Action;
import com.scriptcraft.script.runtime.Actions;
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
    private static final Pattern ID = Pattern.compile("(?:%[a-z0-9_-]+%|[a-z0-9_.-])+(?::(?:%[a-z0-9_-]+%|[a-z0-9_./-])+)?");
    private static final Pattern HUD_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final String SECONDS = "seconds|second|secs|sec";

    static {
        // --- режим игры и характеристики: "set X of <цель> to <значение>" и "set player's X to <значение>"
        setter("gamemode", (t, v) -> new Effects.SetGameMode(t, gameMode(v)));
        setter("game mode", (t, v) -> new Effects.SetGameMode(t, gameMode(v)));
        setterExpr("health", Effects.SetHealth::new);
        setterExpr("food level", Effects.SetFood::new);
        setterExpr("hunger", Effects.SetFood::new);
        setterExpr("saturation", Effects.SetSaturation::new);
        setterExpr("xp level", Effects.SetXpLevel::new);
        setterExpr("level", Effects.SetXpLevel::new);
        setterExpr("flying", Effects.SetFlying::new);
        setterExpr("flight", Effects.SetFlying::new);
        setterExpr("walk speed", (t, v) -> new Effects.SetSpeed(t, false, v));
        setterExpr("fly speed", (t, v) -> new Effects.SetSpeed(t, true, v));
        setterExpr("flying speed", (t, v) -> new Effects.SetSpeed(t, true, v));
        setterExpr("air", Effects.SetAir::new);
        setterExpr("oxygen", Effects.SetAir::new);
        setterExpr("absorption", Effects.SetAbsorption::new);

        // --- здоровье и голод
        rule("heal %target%", c -> new Effects.Heal(c.target(0), null));
        rule("heal %target% by %expr%", c -> new Effects.Heal(c.target(0), c.expr(1)));
        rule("damage %target% by %expr%", c -> new Effects.Damage(c.target(0), c.expr(1)));
        rule("feed %target%", c -> new Effects.Feed(c.target(0), null));
        rule("feed %target% by %expr%", c -> new Effects.Feed(c.target(0), c.expr(1)));
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
        rule("set value|progress of bar %text% for|of %target% to %expr%",
                c -> new HudActions.SetBar(c.target(1), hudName(c.text(0)), barValue(c.expr(2)), null));
        rule("set value|progress of bar %text% to %expr%",
                c -> new HudActions.SetBar(new Target.Self(), hudName(c.text(0)), barValue(c.expr(1)), null));
        rule("set max of bar %text% for|of %target% to %expr%",
                c -> new HudActions.SetBar(c.target(1), hudName(c.text(0)), null, barValue(c.expr(2))));
        rule("set max of bar %text% to %expr%",
                c -> new HudActions.SetBar(new Target.Self(), hudName(c.text(0)), null, barValue(c.expr(1))));
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
        rule("play sound %id% to %target% with volume %num% pitch %expr%",
                c -> new Effects.PlaySound(c.target(1), c.id(0), c.num(2), c.expr(3)));
        rule("play sound %id% to %target% with volume %expr%",
                c -> new Effects.PlaySound(c.target(1), c.id(0), c.expr(2), null));
        rule("play sound %id% to %target%", c -> new Effects.PlaySound(c.target(1), c.id(0), null, null));
        rule("send title %text% to %target%", c -> new Effects.Title(c.target(1), c.text(0), null));
        rule("send title %text% with subtitle %text% to %target%", c -> new Effects.Title(c.target(2), c.text(0), c.text(1)));
        rule("send action bar %text% to %target%", c -> new Effects.ActionBar(c.target(1), c.text(0)));
        rule("send actionbar %text% to %target%", c -> new Effects.ActionBar(c.target(1), c.text(0)));
        rule("give %num% level|levels to %target%", c -> new Effects.GiveLevels(c.target(1), c.num(0)));
        rule("give %num% xp|experience to %target%", c -> new Effects.GiveXp(c.target(1), c.num(0)));

        // --- сообщения, предметы, телепорт (раньше были отдельными действиями только для "player")
        rule("send %text%", c -> new Effects.SendMessage(new Target.Self(), c.text(0)));
        rule("send %text% to %target%", c -> new Effects.SendMessage(c.target(1), c.text(0)));
        rule("message %text% to %target%", c -> new Effects.SendMessage(c.target(1), c.text(0)));
        // give <цель> <предмет> [кол-во] — после "give ... effect ..." и "give N xp ...", чтобы не перехватить их
        rule("give %target% %id% %num%", c -> new Effects.GiveItem(c.target(0), c.id(1), c.num(2)));
        rule("give %target% %id%", c -> new Effects.GiveItem(c.target(0), c.id(1), null));
        rule("give %target% %piece% %num%", c -> new Effects.GiveExpr(c.target(0), c.exprValue(1), c.num(2)));
        rule("give %target% %piece%", c -> new Effects.GiveExpr(c.target(0), c.exprValue(1), null));
        rule("teleport|tp %target% to world spawn", c -> new Effects.TeleportSpawn(c.target(0), true));
        rule("teleport|tp %target% to spawn", c -> new Effects.TeleportSpawn(c.target(0), false));
        rule("teleport|tp %target% to %num% %num% %num%",
                c -> new Effects.TeleportToPos(c.target(0), c.num(1), c.num(2), c.num(3)));
        rule("teleport|tp %target% to %target%", c -> new Effects.TeleportToPlayer(c.target(0), c.target(1)));
        rule("teleport|tp %target% to %expr%", c -> new Effects.TeleportToLocation(c.target(0), c.expr(1)));

        // --- как в Skript: кик, права, мир, предметы, движение
        rule("kick %target% due to %text%", c -> new Effects.Kick(c.target(0), c.text(1)));
        rule("kick %target% because %text%", c -> new Effects.Kick(c.target(0), c.text(1)));
        rule("kick %target%", c -> new Effects.Kick(c.target(0), null));
        rule("op %target%", c -> new Effects.ChangeOp(c.target(0), true));
        rule("deop %target%", c -> new Effects.ChangeOp(c.target(0), false));
        rule("make %target% say|chat %text%", c -> new Effects.Say(c.target(0), c.text(1)));
        rule("log %text%", c -> new Effects.Log(c.text(0)));
        rule("close inventory of %target%", c -> new Effects.CloseInventory(c.target(0)));
        rule("wake up %target%", c -> new Effects.Wake(c.target(0)));
        rule("wake %target% up", c -> new Effects.Wake(c.target(0)));
        rule("strike lightning at %num% %num% %num%", c -> new Effects.Lightning(null, c.num(0), c.num(1), c.num(2)));
        rule("strike lightning at %target%", c -> new Effects.Lightning(c.target(0), null, null, null));
        rule("strike lightning at %expr%", c -> new Effects.LightningAt(c.expr(0)));
        rule("set time to %expr%", c -> new Effects.SetTime(c.expr(0)));
        rule("set weather to clear|sun", c -> new Effects.SetWeather("clear", null));
        rule("set weather to thunder|storm for %expr% " + SECONDS, c -> new Effects.SetWeather("thunder", c.expr(0)));
        rule("set weather to rain for %expr% " + SECONDS, c -> new Effects.SetWeather("rain", c.expr(0)));
        rule("set weather to thunder|storm", c -> new Effects.SetWeather("thunder", null));
        rule("set weather to rain", c -> new Effects.SetWeather("rain", null));
        rule("drop %num% %id% at %target%", c -> new Effects.DropItem(c.target(2), c.id(1), c.num(0)));
        rule("drop %id% at %target%", c -> new Effects.DropItem(c.target(1), c.id(0), null));
        rule("drop %num% %piece% at %target%", c -> new Effects.DropExpr(c.exprValue(1), c.num(0), c.target(2), null));
        rule("drop %piece% at %target%", c -> new Effects.DropExpr(c.exprValue(0), null, c.target(1), null));
        rule("drop %num% %piece% at %expr%", c -> new Effects.DropExpr(c.exprValue(1), c.num(0), null, c.expr(2)));
        rule("drop %piece% at %expr%", c -> new Effects.DropExpr(c.exprValue(0), null, null, c.expr(1)));
        rule("set spawn of %target% to %num% %num% %num%",
                c -> new Effects.SetSpawn(c.target(0), c.num(1), c.num(2), c.num(3)));
        rule("set spawn of %target% to %expr%", c -> new Effects.SetSpawnAt(c.target(0), c.expr(1)));
        rule("set spawn of %target%", c -> new Effects.SetSpawn(c.target(0), null, null, null));
        rule("set velocity of %target% to %num% %num% %num%",
                c -> new Effects.SetVelocity(c.target(0), c.num(1), c.num(2), c.num(3)));
        rule("launch %target% forwards|forward by|with speed|force %expr%", c -> new Effects.Launch(c.target(0), true, c.expr(1)));
        rule("launch %target% forwards|forward by|with %expr%", c -> new Effects.Launch(c.target(0), true, c.expr(1)));
        rule("launch %target% upwards|up by|with speed|force %expr%", c -> new Effects.Launch(c.target(0), false, c.expr(1)));
        rule("launch %target% upwards|up by|with %expr%", c -> new Effects.Launch(c.target(0), false, c.expr(1)));
        rule("launch %target% forwards|forward", c -> new Effects.Launch(c.target(0), true, null));
        rule("launch %target% upwards|up", c -> new Effects.Launch(c.target(0), false, null));
        rule("explode at %num% %num% %num% with force|power %expr%",
                c -> new Effects.Explode(null, c.num(0), c.num(1), c.num(2), c.expr(3)));
        rule("explode at %num% %num% %num%", c -> new Effects.Explode(null, c.num(0), c.num(1), c.num(2), null));
        rule("explode at %target% with force|power %expr%", c -> new Effects.Explode(c.target(0), null, null, null, c.expr(1)));
        rule("explode at %target%", c -> new Effects.Explode(c.target(0), null, null, null, null));
        rule("explode at %piece% with force|power %expr%", c -> new Effects.ExplodeAt(c.exprValue(0), c.expr(1)));
        rule("explode at %expr%", c -> new Effects.ExplodeAt(c.expr(0), null));
        rule("stop", c -> new Actions.Stop());
        rule("cancel event", c -> new Actions.CancelEvent());
        rule("cancel the event", c -> new Actions.CancelEvent());

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

    /** Регистрирует обе формы: "set X of <цель> to <v>" и "set player's X to <v>" (одно слово). */
    private static void setter(String property, ValueBuilder builder) {
        rule("set " + property + " of %target% to %value%", c -> builder.build(c.target(0), c.raw(1)));
        rule("set player's " + property + " to %value%", c -> builder.build(new Target.Self(), c.raw(0)));
    }

    /** То же, но значение — выражение до конца строки (число, {переменная} или health of player). */
    private static void setterExpr(String property, ExprBuilder builder) {
        rule("set " + property + " of %target% to %expr%", c -> builder.build(c.target(0), c.expr(1)));
        rule("set player's " + property + " to %expr%", c -> builder.build(new Target.Self(), c.expr(0)));
    }

    @FunctionalInterface
    private interface ExprBuilder {
        Action build(Target target, Expression value) throws ParseException;
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
            try {
                return rule.builder().build(new Captures(captures));
            } catch (ParseException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ParseException(e.getMessage() == null ? e.toString() : e.getMessage());
            }
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
        if (alts[0].equals("%expr%")) {
            // Выражение только в конце шаблона: съедает все оставшиеся токены.
            if (pi != parts.length - 1) {
                return false;
            }
            ExprParser.Parsed parsed = ExprParser.parse(tokens, ti);
            if (parsed.end() != tokens.size()) {
                return false;
            }
            captures.add(parsed.expression());
            return true;
        }
        if (alts[0].equals("%piece%")) {
            // Одно выражение не в конце строки (drop player's tool at player).
            // %value% так не называется: его занимает set gamemode ... to <слово> и ждёт один токен.
            ExprParser.Parsed parsed = ExprParser.parse(tokens, ti);
            captures.add(parsed.expression());
            if (match(parts, pi + 1, tokens, parsed.end(), captures)) {
                return true;
            }
            captures.remove(captures.size() - 1);
            return false;
        }
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

    /** Значение полосы: число, {переменная}, выражение или источник клиента (health, food...). */
    private static Expression barValue(Expression e) throws ParseException {
        if (e instanceof Expression.Text text && !BarElement.isSource(text.raw())) {
            throw new ParseException("ожидается число, {переменная}, выражение или источник (" + String.join(", ", BarElement.SOURCES)
                    + "), а не '" + text.raw() + "'");
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

        Expression expr(int i) {
            return (Expression) values.get(i);
        }

        String text(int i) throws ParseException {
            Token t = raw(i);
            if (!t.quoted()) {
                throw new ParseException("ожидается текст в кавычках, а не '" + t.value() + "'");
            }
            return t.value();
        }

        String id(int i) throws ParseException {
            String raw = raw(i).value();
            if (raw.length() > 2 && raw.startsWith("{") && raw.endsWith("}")) {
                return raw;
            }
            int brace = raw.indexOf('{');
            String id = brace <= 0 ? raw : raw.substring(0, brace);
            String v = id.toLowerCase(Locale.ROOT);
            if (!ID.matcher(v).matches()) {
                throw new ParseException("некорректный id: '" + v + "' (пример: speed, minecraft:speed, entity.player.levelup)");
            }
            return brace <= 0 ? v : v + raw.substring(brace);
        }

        /** Выражение, пойманное %piece% (не в конце строки). */
        Expression exprValue(int i) {
            return (Expression) values.get(i);
        }
    }
}
