package com.scriptcraft.script.runtime;

import com.scriptcraft.hud.HudEntry;
import com.scriptcraft.hud.HudManager;
import com.scriptcraft.lang.Messages;
import com.scriptcraft.screen.BarElement;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Действия HUD (create hud, set value of bar, show/hide/remove hud, hide vanilla ...).
 * Сами действия только вычисляют значения; состояние и отправку изменений клиенту ведёт {@link HudManager}.
 *
 * Плейсхолдеры считаются один раз в контексте скрипта (%player% — игрок события).
 * {переменная} в тексте или в value/max полосы остаётся связью: запись переменной сама обновляет HUD.
 * Для своего текста каждому игроку — loop all players + %loop-player%.
 */
public final class HudActions {
    private HudActions() {
    }

    /** {coins::%player%} -> "{coins::Steve}". Число и источник health за связью не следят. */
    @Nullable
    private static String liveVariable(Expression expression, ExecutionContext ctx) {
        if (expression instanceof Expression.Variable variable) {
            return "{" + ctx.variableName(variable.rawName()) + "}";
        }
        return null;
    }

    /**
     * Значение полосы: имя источника (health, food...) — как есть, иначе число.
     * Текстовые источники клиент считает сам каждый кадр, числа фиксированы до следующего set.
     */
    static String barValue(Expression e, ExecutionContext ctx) {
        Object v = e.evaluate(ctx);
        if (v instanceof String s && BarElement.isSource(s)) {
            return s.strip().toLowerCase(Locale.ROOT);
        }
        Double d = Expression.toNumber(v);
        if (d == null || d.isNaN() || d.isInfinite()) {
            throw new ScriptRuntimeException(Messages.get("effect.not_number", Expression.toText(v)));
        }
        return Expression.toText(d);
    }

    /** create hud "группа" for <цель>: + элементы. */
    public record CreateHud(Target target, String group, List<HudEntry> entries) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            if (target instanceof Target.Self && ctx.player() == null) {
                List<ServerPlayer> players = new ArrayList<>(ctx.server().getPlayerList().getPlayers());
                ctx.noteTargets(players.size());
                for (ServerPlayer player : players) {
                    send(playerContext(ctx, player), List.of(player));
                }
                return;
            }
            send(ctx, target.resolve(ctx));
        }

        private void send(ExecutionContext ctx, List<ServerPlayer> players) {
            if (players.isEmpty()) {
                return;
            }
            List<HudEntry> formatted = new ArrayList<>(entries.size());
            List<com.scriptcraft.hud.HudBinding> bindings = new ArrayList<>(entries.size());
            for (HudEntry e : entries) {
                bindings.add(com.scriptcraft.hud.HudBinding.capture(e, ctx));
                formatted.add(e.withElement(VariableLists.expand(e.element(), ctx).format(ctx::format)));
            }
            for (ServerPlayer p : players) {
                HudManager.create(p, group, formatted, bindings, ctx.script());
            }
        }
    }

    /** set value of bar "id" for <цель> to V  /  set max of bar "id" for <цель> to V (null — не менять). */
    public record SetBar(Target target, String id, @Nullable Expression value, @Nullable Expression max) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            if (target instanceof Target.Self && ctx.player() == null) {
                List<ServerPlayer> players = new ArrayList<>(ctx.server().getPlayerList().getPlayers());
                ctx.noteTargets(players.size());
                for (ServerPlayer player : players) {
                    send(playerContext(ctx, player), List.of(player));
                }
                return;
            }
            send(ctx, target.resolve(ctx));
        }

        private void send(ExecutionContext ctx, List<ServerPlayer> players) {
            if (players.isEmpty()) {
                return;
            }
            String v = value == null ? null : barValue(value, ctx);
            String m = max == null ? null : barValue(max, ctx);
            // {переменная} остаётся живой связью. Число или health/food эту связь снимает.
            String liveValue = value == null ? null : liveVariable(value, ctx);
            String liveMax = max == null ? null : liveVariable(max, ctx);
            for (ServerPlayer p : players) {
                HudManager.setBar(p, id, v, m);
                HudManager.rebindBar(p, id, value != null, liveValue, max != null, liveMax);
            }
        }
    }

    /** set text of hud "id" for <цель> to "текст". */
    public record SetText(Target target, String id, String text) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            if (target instanceof Target.Self && ctx.player() == null) {
                List<ServerPlayer> players = new ArrayList<>(ctx.server().getPlayerList().getPlayers());
                ctx.noteTargets(players.size());
                for (ServerPlayer player : players) {
                    send(playerContext(ctx, player), List.of(player));
                }
                return;
            }
            send(ctx, target.resolve(ctx));
        }

        private void send(ExecutionContext ctx, List<ServerPlayer> players) {
            if (players.isEmpty()) {
                return;
            }
            String formatted = ctx.format(text);
            String template = ctx.expandPlaceholders(text);
            String live = template.contains("%{") ? template : null;
            for (ServerPlayer p : players) {
                HudManager.setText(p, id, formatted);
                HudManager.rebindText(p, id, live);
            }
        }
    }

    /** show hud "имя" to <цель>  /  hide hud "имя" from <цель> — элемент или вся группа. */
    public record Visibility(Target target, String name, boolean visible) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            for (ServerPlayer p : target.resolveHud(ctx)) {
                HudManager.setVisible(p, name, visible);
            }
        }
    }

    /** remove hud "имя" from <цель> — элемент или вся группа. */
    public record Remove(Target target, String name) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            for (ServerPlayer p : target.resolveHud(ctx)) {
                HudManager.remove(p, name);
            }
        }
    }

    /** clear hud of <цель> — весь HUD из скриптов + вернуть ванильный. */
    public record Clear(Target target) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            for (ServerPlayer p : target.resolveHud(ctx)) {
                HudManager.clear(p);
            }
        }
    }

    /** Контекст одного игрока, когда on load шлёт HUD без игрока в событии. */
    private static ExecutionContext playerContext(ExecutionContext ctx, ServerPlayer player) {
        ExecutionContext one = new ExecutionContext(ctx.server(), player, null);
        one.setScript(ctx.script());
        return one;
    }

    /** hide vanilla hotbar for <цель>  /  show vanilla hotbar for <цель>. */
    public record Vanilla(Target target, int mask, boolean hide) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            for (ServerPlayer p : target.resolveHud(ctx)) {
                HudManager.setVanilla(p, mask, hide, ctx.script());
            }
        }
    }
}
