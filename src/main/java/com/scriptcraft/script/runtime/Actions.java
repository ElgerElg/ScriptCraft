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
            ScreenDescription screen = new ScreenDescription(ctx.format(title), formatted);
            for (ServerPlayer player : players) {
                ModNetwork.sendTo(player, new OpenScreenPacket(screen));
            }
        }
    }

    /**
     * loop <цель>: + вложенные действия — выполнить блок для каждого игрока цели;
     * внутри блока цель {@code loop-player} и плейсхолдер %loop-player% — текущий игрок.
     * {@code player} внутри цикла по-прежнему игрок из контекста (как в Skript).
     * Каждое действие каждого прохода тратит шаг бюджета (maxScriptSteps).
     */
    /**
     * loop {имя::*}: — по каждому прямому индексу. %loop-index% — хвост ключа, %loop-value% — значение.
     */
    public record LoopIndices(String rawPattern, List<Action> body) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String name = ctx.variableName(rawPattern);
            if (!name.endsWith("::*")) {
                throw new ScriptRuntimeException("нужно loop {имя::*}");
            }
            String previousIndex = ctx.placeholderOrNull("loop-index");
            String previousValue = ctx.placeholderOrNull("loop-value");
            try {
                for (java.util.Map.Entry<String, Object> entry : com.scriptcraft.script.variables.VariableStore.children(
                        name.substring(0, name.length() - 3))) {
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
