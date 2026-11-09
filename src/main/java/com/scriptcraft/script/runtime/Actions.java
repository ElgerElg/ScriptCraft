package com.scriptcraft.script.runtime;

import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.network.OpenScreenPacket;
import com.scriptcraft.screen.ScreenDescription;
import com.scriptcraft.screen.ScreenElement;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

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
            if (v == null) {
                ctx.deleteVariable(name); // set {a} to {не_заданная} == delete {a}
            } else {
                ctx.setVariable(name, v);
            }
        }
    }

    /** add <n> to {name} / remove <n> from {name}. Незаданная переменная считается равной 0. */
    public record AddToVariable(String rawName, Expression amount, boolean subtract) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String name = ctx.variableName(rawName);
            Object current = ctx.getVariable(name);
            Double base = current == null ? Double.valueOf(0) : Expression.toNumber(current);
            Double delta = Expression.toNumber(amount.evaluate(ctx));
            if (base == null) {
                throw new ScriptRuntimeException("{" + name + "} не число: " + Expression.toText(current));
            }
            if (delta == null) {
                throw new ScriptRuntimeException("ожидается число в add/remove");
            }
            ctx.setVariable(name, subtract ? base - delta : base + delta);
        }
    }

    /** delete {name} */
    public record DeleteVariable(String rawName) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ctx.deleteVariable(ctx.variableName(rawName));
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
                formatted.add(element.format(ctx::format));
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
