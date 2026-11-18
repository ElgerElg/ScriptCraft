package com.scriptcraft.script.parser;

import com.scriptcraft.script.parser.Tokenizer.Token;
import com.scriptcraft.script.runtime.Action;
import com.scriptcraft.script.runtime.Expression;
import com.scriptcraft.script.runtime.LuckPermsActions;
import com.scriptcraft.script.runtime.Target;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** set/add/remove/clear permission, group, prefix, suffix, meta. null — это не строка LuckPerms. */
final class LuckPermsSyntax {
    private LuckPermsSyntax() {
    }

    @Nullable
    static Action parse(List<Token> tokens) throws ParseException {
        String verb = word(tokens, 0);
        String what = word(tokens, 1);
        if (!verb.equals("set") && !verb.equals("add") && !verb.equals("give") && !verb.equals("remove")
                && !verb.equals("unset") && !verb.equals("clear") && !verb.equals("reset")) {
            return null;
        }
        if (verb.equals("set") && what.equals("primary") && word(tokens, 2).equals("group")) {
            return setGroup(tokens, 3);
        }
        String noun = switch (what) {
            case "permission", "permissions" -> "permission";
            case "group", "groups" -> "group";
            case "prefix", "prefixes" -> "prefix";
            case "suffix", "suffixes" -> "suffix";
            case "meta" -> "meta";
            default -> "";
        };
        if (noun.isEmpty()) {
            return null;
        }
        int i = 2;
        if (verb.equals("clear") || verb.equals("reset")) {
            return clear(tokens, noun, i);
        }
        return switch (noun) {
            case "permission" -> permission(tokens, verb, i);
            case "group" -> group(tokens, verb, i);
            case "prefix" -> decoration(tokens, verb, i, true);
            case "suffix" -> decoration(tokens, verb, i, false);
            default -> meta(tokens, verb, i);
        };
    }

    private static Action permission(List<Token> t, String verb, int i) throws ParseException {
        ExprParser.Parsed node = ExprParser.parse(t, i);
        i = node.end();
        return switch (verb) {
            case "set" -> {
                i = expect(t, i, "of");
                TargetParser.Parsed target = target(t, i);
                i = expect(t, target.end(), "to");
                ExprParser.Parsed value = ExprParser.parse(t, i);
                Tail tail = tail(t, value.end());
                yield change(LuckPermsActions.Kind.SET_PERM, target.target(), node.expression(), value.expression(), tail);
            }
            case "add", "give" -> {
                i = expect(t, i, "to");
                TargetParser.Parsed target = target(t, i);
                Tail tail = tail(t, target.end());
                yield change(LuckPermsActions.Kind.ADD_PERM, target.target(), node.expression(), null, tail);
            }
            case "remove", "unset" -> {
                String link = word(t, i);
                if (!link.equals("from") && !link.equals("of")) {
                    throw need("remove permission <узел> from <цель>");
                }
                TargetParser.Parsed target = target(t, i + 1);
                finish(t, target.end());
                yield change(LuckPermsActions.Kind.REMOVE_PERM, target.target(), node.expression(), null, Tail.NONE);
            }
            default -> throw need("set/add/remove permission");
        };
    }

    private static Action group(List<Token> t, String verb, int i) throws ParseException {
        if (verb.equals("set")) {
            return setGroup(t, i);
        }
        ExprParser.Parsed name = ExprParser.parse(t, i);
        i = name.end();
        if (verb.equals("add") || verb.equals("give")) {
            i = expect(t, i, "to");
            TargetParser.Parsed target = target(t, i);
            Tail tail = tail(t, target.end());
            return change(LuckPermsActions.Kind.ADD_GROUP, target.target(), name.expression(), null, tail);
        }
        if (verb.equals("remove")) {
            i = expect(t, i, "from");
            TargetParser.Parsed target = target(t, i);
            finish(t, target.end());
            return change(LuckPermsActions.Kind.REMOVE_GROUP, target.target(), name.expression(), null, Tail.NONE);
        }
        throw need("add/remove/set group");
    }

    private static Action setGroup(List<Token> t, int i) throws ParseException {
        i = expect(t, i, "of");
        TargetParser.Parsed target = target(t, i);
        i = expect(t, target.end(), "to");
        ExprParser.Parsed name = ExprParser.parse(t, i);
        Tail tail = tail(t, name.end());
        return change(LuckPermsActions.Kind.SET_GROUP, target.target(), name.expression(), null, tail);
    }

    private static Action decoration(List<Token> t, String verb, int i, boolean prefix) throws ParseException {
        LuckPermsActions.Kind set = prefix ? LuckPermsActions.Kind.SET_PREFIX : LuckPermsActions.Kind.SET_SUFFIX;
        LuckPermsActions.Kind add = prefix ? LuckPermsActions.Kind.ADD_PREFIX : LuckPermsActions.Kind.ADD_SUFFIX;
        LuckPermsActions.Kind remove = prefix ? LuckPermsActions.Kind.REMOVE_PREFIX : LuckPermsActions.Kind.REMOVE_SUFFIX;
        if (verb.equals("set")) {
            i = expect(t, i, "of");
            TargetParser.Parsed target = target(t, i);
            i = expect(t, target.end(), "to");
            ExprParser.Parsed text = ExprParser.parse(t, i);
            return change(set, target.target(), text.expression(), null, tail(t, text.end()));
        }
        if (verb.equals("add") || verb.equals("give")) {
            ExprParser.Parsed text = ExprParser.parse(t, i);
            i = expect(t, text.end(), "to");
            TargetParser.Parsed target = target(t, i);
            return change(add, target.target(), text.expression(), null, tail(t, target.end()));
        }
        if (verb.equals("remove")) {
            i = expect(t, i, "of");
            TargetParser.Parsed target = target(t, i);
            return change(remove, target.target(), null, null, tail(t, target.end()));
        }
        throw need(prefix ? "set/add/remove prefix" : "set/add/remove suffix");
    }

    private static Action meta(List<Token> t, String verb, int i) throws ParseException {
        if (verb.equals("set")) {
            ExprParser.Parsed key = ExprParser.parse(t, i);
            i = expect(t, key.end(), "of");
            TargetParser.Parsed target = target(t, i);
            i = expect(t, target.end(), "to");
            ExprParser.Parsed value = ExprParser.parse(t, i);
            finish(t, value.end());
            return change(LuckPermsActions.Kind.SET_META, target.target(), key.expression(), value.expression(), Tail.NONE);
        }
        if (verb.equals("remove")) {
            ExprParser.Parsed key = ExprParser.parse(t, i);
            String link = word(t, key.end());
            if (!link.equals("of") && !link.equals("from")) {
                throw need("remove meta <ключ> of <цель>");
            }
            TargetParser.Parsed target = target(t, key.end() + 1);
            finish(t, target.end());
            return change(LuckPermsActions.Kind.REMOVE_META, target.target(), key.expression(), null, Tail.NONE);
        }
        throw need("set/remove meta");
    }

    private static Action clear(List<Token> t, String noun, int i) throws ParseException {
        LuckPermsActions.Kind kind = switch (noun) {
            case "permission" -> LuckPermsActions.Kind.CLEAR_PERMS;
            case "group" -> LuckPermsActions.Kind.CLEAR_GROUPS;
            case "prefix" -> LuckPermsActions.Kind.CLEAR_PREFIX;
            case "suffix" -> LuckPermsActions.Kind.CLEAR_SUFFIX;
            case "meta" -> LuckPermsActions.Kind.CLEAR_META;
            default -> throw need("clear permissions/groups/prefixes/suffixes/meta");
        };
        i = expect(t, i, "of");
        TargetParser.Parsed target = target(t, i);
        finish(t, target.end());
        return change(kind, target.target(), null, null, Tail.NONE);
    }

    private static Action change(LuckPermsActions.Kind kind, Target target, @Nullable Expression a,
                                 @Nullable Expression b, Tail tail) {
        return new LuckPermsActions.Change(kind, target, a, b, tail.time, tail.unit, tail.priority, tail.prioritySet);
    }

    private static TargetParser.Parsed target(List<Token> t, int i) throws ParseException {
        TargetParser.Parsed parsed = TargetParser.parse(t, i);
        if (parsed == null) {
            throw need("<цель>");
        }
        return parsed;
    }

    private static Tail tail(List<Token> t, int i) throws ParseException {
        Expression time = null;
        int unit = 1;
        Expression priority = null;
        boolean prioritySet = false;
        while (i < t.size()) {
            if (word(t, i).equals("for")) {
                ExprParser.Parsed amount = ExprParser.parse(t, i + 1);
                unit = unitOf(word(t, amount.end()));
                time = amount.expression();
                i = amount.end() + 1;
                continue;
            }
            if (word(t, i).equals("with") && word(t, i + 1).equals("priority")) {
                ExprParser.Parsed value = ExprParser.parse(t, i + 2);
                priority = value.expression();
                prioritySet = true;
                i = value.end();
                continue;
            }
            break;
        }
        finish(t, i);
        return new Tail(time, unit, priority, prioritySet);
    }

    private static int unitOf(String word) throws ParseException {
        return switch (word) {
            case "second", "seconds", "sec", "secs" -> 1;
            case "minute", "minutes", "min", "mins" -> 60;
            case "hour", "hours" -> 3600;
            case "day", "days" -> 86400;
            default -> throw need("for <число> seconds|minutes|hours|days");
        };
    }

    private static int expect(List<Token> t, int i, String word) throws ParseException {
        if (!word(t, i).equals(word)) {
            throw need(word);
        }
        return i + 1;
    }

    private static void finish(List<Token> t, int i) throws ParseException {
        if (i != t.size()) {
            throw new ParseException("extra text: " + t.get(i).value(), "лишний текст: " + t.get(i).value());
        }
    }

    private static ParseException need(String hint) {
        return new ParseException(hint, hint);
    }

    private static String word(List<Token> t, int i) {
        return i < t.size() && !t.get(i).quoted() ? t.get(i).value().toLowerCase(Locale.ROOT) : "";
    }

    private record Tail(@Nullable Expression time, int unit, @Nullable Expression priority, boolean prioritySet) {
        static final Tail NONE = new Tail(null, 1, null, false);
    }
}
