package com.scriptcraft.script.runtime;

import org.jetbrains.annotations.Nullable;

/**
 * Значение в скрипте: число, текст, true/false или переменная {name}.
 * Результат: Double, String, Boolean или null (переменная не задана).
 */
@FunctionalInterface
public interface Expression {
    @Nullable
    Object evaluate(ExecutionContext ctx);

    /** Число/логическое значение как есть. */
    record Literal(Object value) implements Expression {
        @Override
        public Object evaluate(ExecutionContext ctx) {
            return value;
        }
    }

    /** Текст: плейсхолдеры, {переменные} и цвета подставляются при выполнении. */
    record Text(String raw) implements Expression {
        @Override
        public Object evaluate(ExecutionContext ctx) {
            return ctx.format(raw);
        }
    }

    /** Ссылка на переменную {rawName}; внутри имени можно использовать %player% и т.п. */
    record Variable(String rawName) implements Expression {
        @Override
        public Object evaluate(ExecutionContext ctx) {
            return ctx.getVariable(ctx.variableName(rawName));
        }
    }

    /** Пытается превратить значение в число (Double или текст "12.5"). null — если это не число. */
    @Nullable
    static Double toNumber(@Nullable Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.strip());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Значение -> текст для вывода: 5.0 -> "5", null -> "<none>" (как в Skript). */
    static String toText(@Nullable Object value) {
        if (value == null) {
            return "<none>";
        }
        if (value instanceof Double d) {
            if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                return Long.toString(d.longValue());
            }
            return Double.toString(d);
        }
        return String.valueOf(value);
    }
}
