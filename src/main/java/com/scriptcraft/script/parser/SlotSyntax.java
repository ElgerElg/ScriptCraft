package com.scriptcraft.script.parser;

import com.scriptcraft.script.parser.Tokenizer.Token;
import com.scriptcraft.script.runtime.Effects;
import com.scriptcraft.script.runtime.Expression;
import com.scriptcraft.script.runtime.Target;

import java.util.List;
import java.util.Locale;

/**
 * set slot 0 of player to 1 diamond named "тест" with nbt {Unbreakable:1b}
 * clear slot hotbar 1 of player's inventory
 */
final class SlotSyntax {
    private SlotSyntax() {
    }

    static Effects.SetSlot parseSet(List<Token> t) throws ParseException {
        Cursor slot = readSlot(t, 2);
        int i = expect(t, slot.end, "of");
        Held target = readTarget(t, i);
        i = expect(t, target.end, "to");
        ExprParser.Parsed first = ExprParser.parse(t, i);
        Expression count = null;
        Expression item;
        if (stops(word(t, first.end()))) {
            item = first.expression();
            i = first.end();
        } else {
            count = first.expression();
            ExprParser.Parsed next = ExprParser.parse(t, first.end());
            item = next.expression();
            i = next.end();
        }
        Expression name = null;
        Expression nbt = null;
        while (i < t.size()) {
            if (word(t, i).equals("named")) {
                ExprParser.Parsed parsed = ExprParser.parse(t, i + 1);
                name = parsed.expression();
                i = parsed.end();
                continue;
            }
            if (word(t, i).equals("with") && word(t, i + 1).equals("nbt")) {
                if (i + 2 >= t.size()) {
                    throw new ParseException("need: with nbt <tag>", "нужно: with nbt <тег>");
                }
                Token tag = t.get(i + 2);
                if (rawNbt(tag)) {
                    nbt = new Expression.Literal(tag.value());
                    i += 3;
                    continue;
                }
                ExprParser.Parsed parsed = ExprParser.parse(t, i + 2);
                nbt = parsed.expression();
                i = parsed.end();
                continue;
            }
            throw new ParseException("extra text: " + t.get(i).value(), "лишний текст: " + t.get(i).value());
        }
        return new Effects.SetSlot(slot.expression, slot.hotbar, target.target, count, item, name, nbt);
    }

    static Effects.SetSlot parseClear(List<Token> t) throws ParseException {
        Cursor slot = readSlot(t, 2);
        int i = expect(t, slot.end, "of");
        Held target = readTarget(t, i);
        if (target.end != t.size()) {
            throw new ParseException("extra text: " + t.get(target.end).value(), "лишний текст: " + t.get(target.end).value());
        }
        return new Effects.SetSlot(slot.expression, slot.hotbar, target.target, null, null, null, null);
    }

    private static Cursor readSlot(List<Token> t, int i) throws ParseException {
        if (word(t, i).equals("hotbar")) {
            ExprParser.Parsed n = ExprParser.parse(t, i + 1);
            return new Cursor(n.expression(), true, n.end());
        }
        ExprParser.Parsed n = ExprParser.parse(t, i);
        return new Cursor(n.expression(), false, n.end());
    }

    private static Held readTarget(List<Token> t, int i) throws ParseException {
        if (word(t, i).equals("player's") && word(t, i + 1).equals("inventory")) {
            return new Held(new Target.Self(), i + 2);
        }
        TargetParser.Parsed parsed = TargetParser.parse(t, i);
        if (parsed == null) {
            throw new ParseException("need: set slot <слот> of <цель> to <предмет>",
                    "нужно: set slot <слот> of <цель> to <предмет>");
        }
        int end = parsed.end();
        if (word(t, end).equals("inventory")) {
            end++;
        }
        return new Held(parsed.target(), end);
    }

    private static boolean rawNbt(Token tag) {
        String value = tag.value();
        return !tag.quoted() && value.startsWith("{") && value.endsWith("}") && value.contains(":") && !value.contains("::");
    }

    private static boolean stops(String word) {
        return word.isEmpty() || word.equals("named") || word.equals("with");
    }

    private static int expect(List<Token> t, int i, String word) throws ParseException {
        if (!word(t, i).equals(word)) {
            throw new ParseException("need '" + word + "'", "нужно '" + word + "'");
        }
        return i + 1;
    }

    private static String word(List<Token> t, int i) {
        return i < t.size() && !t.get(i).quoted() ? t.get(i).value().toLowerCase(Locale.ROOT) : "";
    }

    private record Cursor(Expression expression, boolean hotbar, int end) {
    }

    private record Held(Target target, int end) {
    }
}
