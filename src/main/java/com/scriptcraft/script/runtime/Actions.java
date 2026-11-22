package com.scriptcraft.script.runtime;

import com.scriptcraft.lang.Messages;
import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.network.OpenScreenPacket;
import com.scriptcraft.screen.ScreenDescription;
import com.scriptcraft.screen.ScreenElement;
import com.scriptcraft.script.variables.VariableStore;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * БЕЛЫЙ СПИСОК действий, доступных скриптам. Новое действие = новый record здесь + правило в ScriptParser.
 * Всё, чего здесь нет, скрипт сделать не может.
 */
public final class Actions {
    private Actions() {
    }

    /** Выполняет действия по очереди; каждое действие расходует один шаг бюджета (защита от зависаний). */
    public static void runAll(List<Action> actions, ExecutionContext ctx) {
        for (Action action : actions) {
            if (ctx.isStopped()) {
                return;
            }
            ctx.step();
            action.execute(ctx);
        }
    }

    // ------------------------------------------------------------------ переменные

    /** set {name} to <значение> */
    public record SetVariable(String rawName, Expression value) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            Object v = value.evaluate(ctx);
            String name = ctx.variableName(rawName);
            if (name.endsWith("::*")) {
                throw new ScriptRuntimeException("нельзя записать {" + name + "}: укажите индекс, например {list::ключ}");
            }
            if (v == null) {
                ctx.deleteVariable(name); // set {a} to {не_заданная} == delete {a}
            } else {
                ctx.setVariable(name, v);
            }
        }
    }

    /**
     * add &lt;значение&gt; to {имя} / remove &lt;значение&gt; from {имя}.
     * Число к числу — арифметика. Текст дописывается или вырезается.
     * {список::*} — добавить элемент следующим номером или убрать одно равное значение.
     */
    public record AddToVariable(String rawName, Expression amount, boolean subtract) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String name = ctx.variableName(rawName);
            Object value = amount.evaluate(ctx);
            if (value == null) {
                throw new ScriptRuntimeException(Messages.get("var.need_value"));
            }
            if (name.endsWith("::*")) {
                String prefix = name.substring(0, name.length() - 3);
                if (subtract) {
                    removeOne(prefix, value);
                } else {
                    append(prefix, value);
                }
                return;
            }
            Object current = ctx.getVariable(name);
            Double delta = Expression.toNumber(value);
            boolean currentIsNumber = current == null || current instanceof Number
                    || (current instanceof String text && Expression.toNumber(text) != null);
            if (delta != null && currentIsNumber) {
                double base = current == null ? 0d : Expression.toNumber(current);
                ctx.setVariable(name, subtract ? base - delta : base + delta);
                return;
            }
            if (current == null) {
                if (!subtract) {
                    ctx.setVariable(name, value);
                }
                return;
            }
            if (current instanceof Number) {
                throw new ScriptRuntimeException(Messages.get("var.text_to_number", name));
            }
            String text = current == null ? "" : Expression.toText(current);
            String piece = Expression.toText(value);
            if (subtract) {
                int at = text.indexOf(piece);
                if (at >= 0) {
                    text = text.substring(0, at) + text.substring(at + piece.length());
                }
            } else {
                text = text + piece;
            }
            ctx.setVariable(name, text);
        }

        private static void append(String prefix, Object value) {
            int next = 1;
            for (java.util.Map.Entry<String, Object> entry : VariableStore.children(prefix)) {
                try {
                    next = Math.max(next, Integer.parseInt(entry.getKey()) + 1);
                } catch (NumberFormatException ignored) {
                    // Ключ вроде minecraft:diamond — это не номер элемента.
                }
            }
            VariableStore.set(prefix + "::" + next, value);
        }

        private static void removeOne(String prefix, Object value) {
            String want = Expression.toText(value);
            for (java.util.Map.Entry<String, Object> entry : VariableStore.children(prefix)) {
                if (want.equals(Expression.toText(entry.getValue()))) {
                    VariableStore.delete(prefix + "::" + entry.getKey());
                    return;
                }
            }
        }
    }

    /** set nbt "путь" of targeted entity|block|player to <значение> */
    public record SetNbt(String path, String kind, Expression value) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ServerPlayer viewer = ctx.requirePlayer();
            NbtAccess.write(viewer, kind, ctx.format(path), value.evaluate(ctx));
        }
    }

    /** remove nbt "путь" of targeted entity|block|player */
    public record RemoveNbt(String path, String kind) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            NbtAccess.remove(ctx.requirePlayer(), kind, ctx.format(path));
        }
    }

    /** delete {name} */
    public record DeleteVariable(String rawName) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String name = ctx.variableName(rawName);
            if (name.endsWith("::*")) {
                com.scriptcraft.script.variables.VariableStore.deleteChildren(name.substring(0, name.length() - 3));
                return;
            }
            ctx.deleteVariable(name);
        }
    }

    /** if <условие>: ... [else if <условие>: ...] [else: ...] */
    public record If(Condition condition, List<Action> then, List<Action> otherwise) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            runAll(condition.test(ctx) ? then : otherwise, ctx);
        }
    }

    /**
     * wait &lt;n&gt; seconds|ticks|minutes — хвост текущего блока (строки ниже на том же отступе)
     * выполнится позже. Код снаружи блока не ждёт. Пауза не длиннее 5 минут.
     * Игрок внутри цикла (loop-player) после паузы не сохраняется.
     */
    public record Wait(Expression amount, int ticksPerUnit, List<Action> after) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            int ticks = (int) Math.round(Effects.number(amount, ctx) * ticksPerUnit);
            ScriptScheduler.schedule(ctx, ticks, after);
        }
    }

    /** chance of 50%: — вложенные действия с указанным шансом (0–100). */
    public record Chance(Expression percent, List<Action> body) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            double p = Effects.number(percent, ctx);
            if (p > 0 && ctx.server().overworld().getRandom().nextDouble() * 100 < p) {
                runAll(body, ctx);
            }
        }
    }

    /** Name(arg, ...) — вызов function. Тело берётся из текущей программы, чтобы reload подхватил новое. */
    public record CallFunction(String name, List<Expression> args) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            List<Object> values = new ArrayList<>(args.size());
            for (Expression arg : args) {
                values.add(arg.evaluate(ctx));
            }
            com.scriptcraft.script.ScriptLoader.callFunction(name, values, ctx);
        }
    }

    /** stop — не выполнять оставшиеся действия этого запуска. */
    public record Stop() implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ctx.stop();
        }
    }

    /** cancel event — отменить чат, ломание, установку, клик, выбрасывание, урон, смерть, смену режима. */
    public record CancelEvent() implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ctx.cancelEvent();
        }
    }

    // ------------------------------------------------------------------ игрок и чат

    /** loop <N> times: — повторяет блок N раз, задавая %loop-number% от 1 до N. */
    public record LoopTimes(Expression count, List<Action> body) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            int n = (int) Math.max(0, Math.min(10000, Math.round(Effects.number(count, ctx))));
            String prev = ctx.placeholder("loop-number");
            try {
                for (int i = 1; i <= n; i++) {
                    if (ctx.isStopped()) {
                        return;
                    }
                    ctx.step();
                    ctx.setPlaceholder("loop-number", Integer.toString(i));
                    runAll(body, ctx);
                }
            } finally {
                ctx.setPlaceholder("loop-number", prev);
            }
        }
    }

    /** while <условие>: — повторяет блок, пока условие истинно (ограничено бюджетом шагов). */
    public record While(Condition condition, List<Action> body) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            while (!ctx.isStopped() && condition.test(ctx)) {
                ctx.step();
                runAll(body, ctx);
            }
        }
    }

    /** broadcast "текст" — сообщение всем игрокам. */
    public record Broadcast(String text) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ctx.server().getPlayerList().broadcastSystemMessage(Component.literal(ctx.format(text)), false);
        }
    }

    /** reply "текст" — ответ тому, кто вызвал (работает и из консоли). */
    public record Reply(String text) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ctx.reply(ctx.format(text));
        }
    }

    /**
     * open screen "Заголовок" [to <цель>]: + вложенные элементы.
     * Сервер подставляет плейсхолдеры и отправляет описание экрана каждому игроку цели пакетом.
     * Плейсхолдеры считаются один раз (от игрока из контекста), а не для каждого получателя.
     */
    public record OpenScreen(Target target, String title, List<ScreenElement> elements) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            List<ServerPlayer> players = target.resolve(ctx);
            if (players.isEmpty()) {
                return;
            }
            List<ScreenElement> formatted = new ArrayList<>(elements.size());
            for (ScreenElement element : elements) {
                formatted.add(VariableLists.expand(element, ctx).format(ctx::format));
            }
            String formattedTitle = ctx.format(title);
            for (ServerPlayer player : players) {
                com.scriptcraft.screen.ScreenManager.open(player, formattedTitle, formatted);
            }
        }
    }

    /** update screen ["Заголовок"] [for|to <цель>]: + элементы (добавляет или заменяет по id без пересоздания экрана). */
    public record UpdateScreen(Target target, @org.jetbrains.annotations.Nullable String title, List<ScreenElement> elements) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            List<ServerPlayer> players = target.resolve(ctx);
            if (players.isEmpty()) {
                return;
            }
            List<ScreenElement> formatted = new ArrayList<>(elements.size());
            for (ScreenElement element : elements) {
                formatted.add(VariableLists.expand(element, ctx).format(ctx::format));
            }
            String formattedTitle = title == null || title.isEmpty() ? null : ctx.format(title);
            for (ServerPlayer player : players) {
                com.scriptcraft.screen.ScreenManager.updateElements(player, formattedTitle, formatted);
            }
        }
    }

    public record ScreenSetText(Target target, String id, Expression text) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String resolvedId = ctx.format(id);
            String val = Expression.toText(text.evaluate(ctx));
            for (ServerPlayer p : target.resolve(ctx)) {
                com.scriptcraft.screen.ScreenManager.mutateElement(p, resolvedId, e -> com.scriptcraft.screen.ScreenMeta.withText(e, val));
            }
        }
    }

    public record ScreenSetTexture(Target target, String id, Expression texture) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String resolvedId = ctx.format(id);
            String val = Expression.toText(texture.evaluate(ctx)).strip();
            for (ServerPlayer p : target.resolve(ctx)) {
                com.scriptcraft.screen.ScreenManager.mutateElement(p, resolvedId, e -> com.scriptcraft.screen.ScreenMeta.withTexture(e, val));
            }
        }
    }

    public record ScreenSetPosition(Target target, String id, Expression x, Expression y) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String resolvedId = ctx.format(id);
            int px = (int) Math.round(Effects.number(x, ctx));
            int py = (int) Math.round(Effects.number(y, ctx));
            for (ServerPlayer p : target.resolve(ctx)) {
                com.scriptcraft.screen.ScreenManager.mutateElement(p, resolvedId, e -> com.scriptcraft.screen.ScreenMeta.withPosition(e, px, py));
            }
        }
    }

    public record ScreenSetScale(Target target, String id, Expression scale) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String resolvedId = ctx.format(id);
            float sc = (float) Effects.number(scale, ctx);
            for (ServerPlayer p : target.resolve(ctx)) {
                com.scriptcraft.screen.ScreenManager.mutateElement(p, resolvedId, e -> com.scriptcraft.screen.ScreenMeta.withScale(e, sc));
            }
        }
    }

    public record ScreenSetRotation(Target target, String id, Expression degrees) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String resolvedId = ctx.format(id);
            float deg = (float) Effects.number(degrees, ctx);
            for (ServerPlayer p : target.resolve(ctx)) {
                com.scriptcraft.screen.ScreenManager.mutateElement(p, resolvedId, e -> com.scriptcraft.screen.ScreenMeta.withRotation(e, deg));
            }
        }
    }

    public record GuiFloatText(Target target, Expression text, Expression x, Expression y,
                               @org.jetbrains.annotations.Nullable String colorHex,
                               @org.jetbrains.annotations.Nullable Expression durationTicks) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String txt = ctx.format(Expression.toText(text.evaluate(ctx)));
            int px = (int) Math.round(Effects.number(x, ctx));
            int py = (int) Math.round(Effects.number(y, ctx));
            int col = parseColorRgb(colorHex, 0xFFFFFF);
            int durMs = durationTicks == null ? 750 : Math.max(200, Math.min(5000, (int) Math.round(Effects.number(durationTicks, ctx) * 50.0)));
            var pkt = new com.scriptcraft.network.GuiFxPacket(
                    com.scriptcraft.network.GuiFxPacket.FLOAT_TEXT, px, py, "center", txt, col, 1, durMs, 0f);
            for (ServerPlayer p : target.resolve(ctx)) {
                com.scriptcraft.network.ModNetwork.sendTo(p, pkt);
            }
        }
    }

    public record GuiParticles(Target target, String preset, @org.jetbrains.annotations.Nullable Expression count,
                               Expression x, Expression y, @org.jetbrains.annotations.Nullable String colorHex,
                               @org.jetbrains.annotations.Nullable Expression spread) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String pr = ctx.format(preset);
            int cnt = count == null ? 10 : Math.max(1, Math.min(48, (int) Math.round(Effects.number(count, ctx))));
            int px = (int) Math.round(Effects.number(x, ctx));
            int py = (int) Math.round(Effects.number(y, ctx));
            int col = parseColorRgb(colorHex, 0);
            float sp = spread == null ? 28f : (float) Math.max(4.0, Math.min(160.0, Effects.number(spread, ctx)));
            var pkt = new com.scriptcraft.network.GuiFxPacket(
                    com.scriptcraft.network.GuiFxPacket.PARTICLES, px, py, "center", pr, col, cnt, 650, sp);
            for (ServerPlayer p : target.resolve(ctx)) {
                com.scriptcraft.network.ModNetwork.sendTo(p, pkt);
            }
        }
    }

    private static int parseColorRgb(@org.jetbrains.annotations.Nullable String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String s = raw.strip();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        try {
            int parsed = (int) Long.parseLong(s, 16);
            return parsed & 0x00FFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public record ScreenSetVisible(Target target, String id, boolean visible) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String resolvedId = ctx.format(id);
            for (ServerPlayer p : target.resolve(ctx)) {
                com.scriptcraft.screen.ScreenManager.mutateElement(p, resolvedId, e -> com.scriptcraft.screen.ScreenMeta.withHidden(e, !visible));
            }
        }
    }

    public record ScreenSetBar(Target target, String id, @org.jetbrains.annotations.Nullable Expression value,
                               @org.jetbrains.annotations.Nullable Expression max) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String resolvedId = ctx.format(id);
            String v = value == null ? null : HudActions.barValue(value, ctx);
            String m = max == null ? null : HudActions.barValue(max, ctx);
            for (ServerPlayer p : target.resolve(ctx)) {
                com.scriptcraft.screen.ScreenManager.setBar(p, resolvedId, v, m);
            }
        }
    }

    public record ScreenSetSlot(Target target, String id, String item, @org.jetbrains.annotations.Nullable Expression count) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String resolvedId = ctx.format(id);
            int c = count == null ? 1 : Math.max(1, (int) Math.round(Effects.number(count, ctx)));
            String resolvedItem = ctx.format(item);
            for (ServerPlayer p : target.resolve(ctx)) {
                com.scriptcraft.screen.ScreenManager.mutateElement(p, resolvedId, e -> com.scriptcraft.screen.ScreenMeta.withSlotItem(e, resolvedItem, c));
            }
        }
    }

    public record ScreenRemove(Target target, String id) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String resolvedId = ctx.format(id);
            for (ServerPlayer p : target.resolve(ctx)) {
                com.scriptcraft.screen.ScreenManager.removeElement(p, resolvedId);
            }
        }
    }

    public record ScreenClear(Target target) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            for (ServerPlayer p : target.resolve(ctx)) {
                com.scriptcraft.screen.ScreenManager.clearElements(p);
            }
        }
    }

    public record ScreenClose(Target target) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            for (ServerPlayer p : target.resolve(ctx)) {
                com.scriptcraft.screen.ScreenManager.close(p);
            }
        }
    }

    /** set value of screen element "<id>" [for <target>] to <expr> (input, slider, toggle, bar). */
    public record ScreenSetControlValue(Target target, String id, Expression value) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String resolvedId = ctx.format(id);
            Object val = value.evaluate(ctx);
            for (ServerPlayer p : target.resolve(ctx)) {
                com.scriptcraft.screen.ScreenManager.mutateElement(p, resolvedId,
                        e -> com.scriptcraft.screen.ScreenMeta.withControlValue(e, val));
            }
        }
    }

    /** sort {list::*} [ascending|descending] — сортирует значения (числа численно, строки лексикографически) и переиндексирует 1..N. */
    public record SortList(String rawPattern, boolean descending) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String name = ctx.variableName(rawPattern);
            String prefix = name.endsWith("::*") ? name.substring(0, name.length() - 3) : name;
            List<java.util.Map.Entry<String, Object>> entries = new ArrayList<>(VariableStore.children(prefix));
            if (entries.isEmpty()) {
                return;
            }
            entries.sort((a, b) -> {
                int cmp = compareValues(a.getValue(), b.getValue());
                return descending ? -cmp : cmp;
            });
            VariableStore.deleteChildren(prefix);
            int idx = 1;
            for (java.util.Map.Entry<String, Object> e : entries) {
                VariableStore.set(prefix + "::" + (idx++), e.getValue());
            }
        }

        static int compareValues(Object a, Object b) {
            Double na = Expression.toNumber(a);
            Double nb = Expression.toNumber(b);
            if (na != null && nb != null) {
                return Double.compare(na, nb);
            }
            return Expression.toText(a).compareToIgnoreCase(Expression.toText(b));
        }
    }

    /** reverse {list::*} — переворачивает порядок элементов и переиндексирует 1..N. */
    public record ReverseList(String rawPattern) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String name = ctx.variableName(rawPattern);
            String prefix = name.endsWith("::*") ? name.substring(0, name.length() - 3) : name;
            List<java.util.Map.Entry<String, Object>> entries = new ArrayList<>(VariableStore.children(prefix));
            if (entries.size() <= 1) {
                return;
            }
            java.util.Collections.reverse(entries);
            VariableStore.deleteChildren(prefix);
            int idx = 1;
            for (java.util.Map.Entry<String, Object> e : entries) {
                VariableStore.set(prefix + "::" + (idx++), e.getValue());
            }
        }
    }

    /** remove index <key> from {list::*} */
    public record RemoveIndex(Expression key, String rawPattern) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String name = ctx.variableName(rawPattern);
            String prefix = name.endsWith("::*") ? name.substring(0, name.length() - 3) : name;
            String k = Expression.toText(key.evaluate(ctx));
            VariableStore.delete(prefix + "::" + k);
        }
    }

    /**
     * loop <цель>: + вложенные действия — выполнить блок для каждого игрока цели;
     * внутри блока цель {@code loop-player} и плейсхолдер %loop-player% — текущий игрок.
     * {@code player} внутри цикла по-прежнему игрок из контекста (как в Skript).
     * Каждое действие каждого прохода тратит шаг бюджета (maxScriptSteps).
     */
    /**
     * loop {имя::*} [sorted [ascending|descending]]: — по каждому прямому индексу.
     * %loop-index% — хвост ключа, %loop-value% — значение.
     * sortMode: 0 = по ключам, 1 = по возрастанию значений, -1 = по убыванию значений.
     */
    public record LoopIndices(String rawPattern, List<Action> body, int sortMode) implements Action {
        public LoopIndices(String rawPattern, List<Action> body) {
            this(rawPattern, body, 0);
        }

        @Override
        public void execute(ExecutionContext ctx) {
            String name = ctx.variableName(rawPattern);
            if (!name.endsWith("::*")) {
                throw new ScriptRuntimeException("нужно loop {имя::*}");
            }
            String previousIndex = ctx.placeholderOrNull("loop-index");
            String previousValue = ctx.placeholderOrNull("loop-value");
            List<java.util.Map.Entry<String, Object>> entries = new ArrayList<>(
                    com.scriptcraft.script.variables.VariableStore.children(name.substring(0, name.length() - 3)));
            if (sortMode != 0) {
                entries.sort((a, b) -> {
                    int cmp = SortList.compareValues(a.getValue(), b.getValue());
                    return sortMode < 0 ? -cmp : cmp;
                });
            }
            try {
                for (java.util.Map.Entry<String, Object> entry : entries) {
                    if (ctx.isStopped()) {
                        break;
                    }
                    ctx.setPlaceholder("loop-index", entry.getKey());
                    ctx.setPlaceholder("loop-value", Expression.toText(entry.getValue()));
                    runAll(body, ctx);
                }
            } finally {
                restorePlaceholder(ctx, "loop-index", previousIndex);
                restorePlaceholder(ctx, "loop-value", previousValue);
            }
        }

        private static void restorePlaceholder(ExecutionContext ctx, String key, @Nullable String previous) {
            if (previous == null) {
                ctx.clearPlaceholder(key);
            } else {
                ctx.setPlaceholder(key, previous);
            }
        }
    }

    public record Loop(Target target, List<Action> body) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            List<ServerPlayer> players = target.resolve(ctx);
            ServerPlayer previous = ctx.loopPlayer();
            try {
                for (ServerPlayer p : players) {
                    if (ctx.isStopped()) {
                        break;
                    }
                    ctx.setLoopPlayer(p);
                    runAll(body, ctx);
                }
            } finally {
                ctx.setLoopPlayer(previous);
            }
        }
    }
}
