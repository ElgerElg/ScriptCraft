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
 * Как и у open screen, тексты форматируются ОДИН раз в контексте скрипта (%player% — игрок события),
 * а результат отправляется всем целям. Для своего текста каждому игроку — loop all players + %loop-player%.
 */
public final class HudActions {
    private HudActions() {
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
            List<ServerPlayer> players = target.resolve(ctx);
            if (players.isEmpty()) {
                return;
            }
            List<HudEntry> formatted = new ArrayList<>(entries.size());
            for (HudEntry e : entries) {
                formatted.add(e.withElement(e.element().format(ctx::format)));
            }
            for (ServerPlayer p : players) {
                HudManager.create(p, group, formatted);
            }
        }
    }

    /** set value of bar "id" for <цель> to V  /  set max of bar "id" for <цель> to V (null — не менять). */
    public record SetBar(Target target, String id, @Nullable Expression value, @Nullable Expression max) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            List<ServerPlayer> players = target.resolve(ctx);
            if (players.isEmpty()) {
                return;
            }
            String v = value == null ? null : barValue(value, ctx);
            String m = max == null ? null : barValue(max, ctx);
            for (ServerPlayer p : players) {
                HudManager.setBar(p, id, v, m);
            }
        }
    }

    /** set text of hud "id" for <цель> to "текст". */
    public record SetText(Target target, String id, String text) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            List<ServerPlayer> players = target.resolve(ctx);
            if (players.isEmpty()) {
                return;
            }
            String formatted = ctx.format(text);
            for (ServerPlayer p : players) {
                HudManager.setText(p, id, formatted);
            }
        }
    }

    /** show hud "имя" to <цель>  /  hide hud "имя" from <цель> — элемент или вся группа. */
    public record Visibility(Target target, String name, boolean visible) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            for (ServerPlayer p : target.resolve(ctx)) {
                HudManager.setVisible(p, name, visible);
            }
        }
    }

    /** remove hud "имя" from <цель> — элемент или вся группа. */
    public record Remove(Target target, String name) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            for (ServerPlayer p : target.resolve(ctx)) {
                HudManager.remove(p, name);
            }
        }
    }

    /** clear hud of <цель> — весь HUD из скриптов + вернуть ванильный. */
    public record Clear(Target target) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            for (ServerPlayer p : target.resolve(ctx)) {
                HudManager.clear(p);
            }
        }
    }

    /** hide vanilla hotbar for <цель>  /  show vanilla hotbar for <цель>. */
    public record Vanilla(Target target, int mask, boolean hide) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            for (ServerPlayer p : target.resolve(ctx)) {
                HudManager.setVanilla(p, mask, hide);
            }
        }
    }
}
