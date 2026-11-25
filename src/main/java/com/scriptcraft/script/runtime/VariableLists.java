package com.scriptcraft.script.runtime;

import com.scriptcraft.screen.ListElement;
import com.scriptcraft.screen.ScreenElement;
import com.scriptcraft.screen.VariableListElement;
import com.scriptcraft.script.variables.VariableStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Раскрывает {@code list from {имя::*}} в богатые строки {@link ListElement.Row} перед отправкой экрана. */
public final class VariableLists {
    private VariableLists() {
    }

    public static ScreenElement expand(ScreenElement element, ExecutionContext ctx) {
        if (!(element instanceof VariableListElement list)) {
            return element;
        }
        String name = ctx.variableName(list.pattern());
        if (!name.endsWith("::*")) {
            throw new ScriptRuntimeException("нужно list from {имя::*}");
        }
        String previousIndex = ctx.placeholderOrNull("loop-index");
        String previousValue = ctx.placeholderOrNull("loop-value");
        List<ListElement.Row> rows = new ArrayList<>();
        try {
            for (Map.Entry<String, Object> entry : VariableStore.children(name.substring(0, name.length() - 3))) {
                ctx.setPlaceholder("loop-index", entry.getKey());
                ctx.setPlaceholder("loop-value", Expression.toText(entry.getValue()));
                ListElement.Row formatted = list.templateRow().format(ctx::format);
                if (!list.rawBarExpr().isEmpty()) {
                    float prog = parseBarSpec(ctx.format(list.rawBarExpr()));
                    formatted = new ListElement.Row(
                            formatted.kind(), formatted.text(), formatted.rightText(),
                            formatted.icon(), formatted.iconCount(), formatted.head(),
                            formatted.badge(), formatted.badgeColor(), prog, formatted.barColor(),
                            formatted.buttonLabel(), formatted.buttonAction(),
                            formatted.customWidth(), formatted.customHeight(),
                            formatted.align(), formatted.state(), formatted.rowColor(),
                            formatted.tooltip(), formatted.action()
                    );
                }
                rows.add(formatted);
            }
        } finally {
            restore(ctx, "loop-index", previousIndex);
            restore(ctx, "loop-value", previousValue);
        }
        return new ListElement(
                list.layout(),
                list.header(),
                list.horizontal(),
                list.select(),
                list.disabled(),
                list.rowHeight(),
                list.spacing(),
                list.rowScale(),
                list.align(),
                list.bgColor(),
                list.borderColor(),
                list.emptyText(),
                list.columnWidths(),
                List.copyOf(rows)
        );
    }

    public static float parseBarSpec(String raw) {
        if (raw == null || raw.isBlank()) return -1f;
        String s = raw.strip().replace("%", "");
        int slash = s.indexOf('/');
        try {
            if (slash > 0) {
                double v = Double.parseDouble(s.substring(0, slash).strip());
                double m = Double.parseDouble(s.substring(slash + 1).strip());
                return m > 0 ? (float) Math.max(0.0, Math.min(1.0, v / m)) : 0f;
            }
            double v = Double.parseDouble(s);
            if (v > 1.0001) v /= 100.0;
            return (float) Math.max(0.0, Math.min(1.0, v));
        } catch (NumberFormatException e) {
            return -1f;
        }
    }

    private static void restore(ExecutionContext ctx, String key, String previous) {
        if (previous == null) {
            ctx.clearPlaceholder(key);
        } else {
            ctx.setPlaceholder(key, previous);
        }
    }
}
