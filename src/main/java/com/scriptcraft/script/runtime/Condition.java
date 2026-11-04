package com.scriptcraft.script.runtime;

import java.util.Objects;

/**
 * Условие для "if ...:".
 * Поддерживается: {a} is X, {a} is not X, {a} > X, <, >=, <=, {a} is set, {a} is not set,
 * а также для целей: targeted player is set, all players is not set и т.п. (есть ли хоть один игрок).
 * TODO: and/or, "else if", условия по игроку (has permission, is op, holds item...).
 */
@FunctionalInterface
public interface Condition {
    boolean test(ExecutionContext ctx);

    /** {var} is set / is not set */
    record IsSet(Expression value, boolean negate) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            return (value.evaluate(ctx) != null) != negate;
        }
    }

    /** <цель> is set / is not set — набор игроков не пуст / пуст (например, targeted player is set). */
    record TargetSet(Target target, boolean negate) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            if (target instanceof Target.Self && ctx.player() == null) {
                return negate; // "player is set" из консоли — игрока нет, это не ошибка
            }
            return !target.select(ctx).isEmpty() != negate;
        }
    }

    /** Сравнение двух значений. op: "is", "is not", ">", "<", ">=", "<=". */
    record Compare(Expression left, String op, Expression right) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            Object a = left.evaluate(ctx);
            Object b = right.evaluate(ctx);
            Double na = Expression.toNumber(a);
            Double nb = Expression.toNumber(b);
            switch (op) {
                case "is", "is not" -> {
                    boolean equal;
                    if (na != null && nb != null) {
                        equal = na.doubleValue() == nb.doubleValue();
                    } else if (a == null || b == null) {
                        equal = Objects.equals(a, b);
                    } else {
                        // Текст сравнивается без учёта регистра, как в Skript.
                        equal = Expression.toText(a).equalsIgnoreCase(Expression.toText(b));
                    }
                    return op.equals("is") == equal;
                }
                default -> {
                    if (na == null || nb == null) {
                        return false; // сравнивать можно только числа
                    }
                    return switch (op) {
                        case ">" -> na > nb;
                        case "<" -> na < nb;
                        case ">=" -> na >= nb;
                        case "<=" -> na <= nb;
                        default -> false;
                    };
                }
            }
        }
    }
}
