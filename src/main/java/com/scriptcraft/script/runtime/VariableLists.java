package com.scriptcraft.script.runtime;

import com.scriptcraft.screen.ListElement;
import com.scriptcraft.screen.ScreenElement;
import com.scriptcraft.screen.VariableListElement;
import com.scriptcraft.script.variables.VariableStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Раскрывает list from {имя::*} в обычные строки перед отправкой экрана. */
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
                rows.add(new ListElement.Row(ctx.format(list.rowText()), ctx.format(list.rowAction())));
            }
        } finally {
            restore(ctx, "loop-index", previousIndex);
            restore(ctx, "loop-value", previousValue);
        }
        return new ListElement(list.layout(), list.header(), list.horizontal(), list.select(), List.copyOf(rows));
    }

    private static void restore(ExecutionContext ctx, String key, String previous) {
        if (previous == null) {
            ctx.clearPlaceholder(key);
        } else {
            ctx.setPlaceholder(key, previous);
        }
    }
}
