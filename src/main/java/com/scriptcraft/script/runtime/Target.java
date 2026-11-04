package com.scriptcraft.script.runtime;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.scriptcraft.config.ScriptCraftConfig;
import com.scriptcraft.lang.Messages;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Над кем выполняется эффект. Любая цель — это НАБОР игроков (может быть пустым):
 * действия выполняются для каждого игрока по очереди, пустой набор = ничего не делать (без ошибки).
 *
 * Синтаксис целей разбирает {@code TargetParser}; здесь только выполнение.
 */
public sealed interface Target permits Target.Self, Target.Named, Target.LoopPlayer, Target.All,
        Target.Targeted, Target.InRadius, Target.Selector {

    /** Сам выбор игроков (без учёта статистики). */
    List<ServerPlayer> select(ExecutionContext ctx);

    /**
     * Выбрать игроков и отметить в контексте, сколько нашлось
     * (эффект-команда в чате по этой статистике пишет "нет целей").
     */
    default List<ServerPlayer> resolve(ExecutionContext ctx) {
        List<ServerPlayer> players = select(ctx);
        ctx.noteTargets(players.size());
        return players;
    }

    /** Первый игрок набора или null (для "teleport X to <цель>"). */
    @Nullable
    default ServerPlayer first(ExecutionContext ctx) {
        List<ServerPlayer> players = select(ctx);
        return players.isEmpty() ? null : players.get(0);
    }

    /** player / %player% / @s-подобная запись — игрок из контекста (вызвал команду, зашёл, написал эффект-команду). */
    record Self() implements Target {
        @Override
        public List<ServerPlayer> select(ExecutionContext ctx) {
            return List.of(ctx.requirePlayer());
        }
    }

    /**
     * Игрок по имени: "Steve", {var}, "%args%". Если такого игрока нет в сети — пустой набор
     * (раньше была ошибка). Селекторы (@a и т.п.) внутри ТЕКСТА не разбираются — только написанные в скрипте.
     */
    record Named(Expression name) implements Target {
        @Override
        public List<ServerPlayer> select(ExecutionContext ctx) {
            String n = Expression.toText(name.evaluate(ctx)).strip();
            if (n.isEmpty()) {
                return List.of();
            }
            ServerPlayer p = ctx.server().getPlayerList().getPlayerByName(n);
            return p == null ? List.of() : List.of(p);
        }
    }

    /** loop-player — текущий игрок внутри "loop <цель>:". Вне цикла — пустой набор. */
    record LoopPlayer() implements Target {
        @Override
        public List<ServerPlayer> select(ExecutionContext ctx) {
            ServerPlayer p = ctx.loopPlayer();
            return p == null ? List.of() : List.of(p);
        }
    }

    /** all players — все игроки онлайн. */
    record All() implements Target {
        @Override
        public List<ServerPlayer> select(ExecutionContext ctx) {
            return new ArrayList<>(ctx.server().getPlayerList().getPlayers());
        }
    }

    /**
     * targeted player [of <цель>] / target of <цель> — игрок, на которого смотрит каждый игрок из source
     * (луч из глаз на config targetedPlayerRange блоков, сквозь блоки не видит).
     */
    record Targeted(Target source) implements Target {
        @Override
        public List<ServerPlayer> select(ExecutionContext ctx) {
            Set<ServerPlayer> result = new LinkedHashSet<>();
            for (ServerPlayer viewer : source.select(ctx)) {
                ServerPlayer hit = lookedAt(viewer);
                if (hit != null) {
                    result.add(hit);
                }
            }
            return new ArrayList<>(result);
        }

        /** Луч из глаз игрока: сначала блоки (ClipContext), потом сущности (ProjectileUtil), только игроки. */
        @Nullable
        public static ServerPlayer lookedAt(ServerPlayer viewer) {
            double range = ScriptCraftConfig.targetedPlayerRange();
            Vec3 eye = viewer.getEyePosition();
            Vec3 look = viewer.getViewVector(1.0F);
            Vec3 end = eye.add(look.scale(range));
            BlockHitResult block = viewer.level().clip(
                    new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
            if (block.getType() != HitResult.Type.MISS) {
                end = block.getLocation(); // дальше стены не смотрим
            }
            AABB box = viewer.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(viewer, eye, end, box,
                    e -> e instanceof ServerPlayer && e != viewer && !e.isSpectator() && e.isPickable(),
                    eye.distanceToSqr(end));
            if (hit == null) {
                return null;
            }
            Entity e = hit.getEntity();
            return e instanceof ServerPlayer sp ? sp : null;
        }
    }

    /**
     * [other] players in radius N of <цель> — игроки в том же мире не дальше N блоков от каждого игрока-центра.
     * Центр входит в набор, если не написано "other".
     */
    record InRadius(Expression radius, Target center, boolean excludeCenter) implements Target {
        @Override
        public List<ServerPlayer> select(ExecutionContext ctx) {
            Object raw = radius.evaluate(ctx);
            Double r = Expression.toNumber(raw);
            if (r == null) {
                throw new ScriptRuntimeException(Messages.get("effect.not_number", Expression.toText(raw)));
            }
            double r2 = Math.max(0.0D, r) * Math.max(0.0D, r);
            List<ServerPlayer> centers = center.select(ctx);
            Set<ServerPlayer> result = new LinkedHashSet<>();
            for (ServerPlayer c : centers) {
                for (ServerPlayer p : ctx.server().getPlayerList().getPlayers()) {
                    if (p.level() == c.level() && p.distanceToSqr(c) <= r2) {
                        result.add(p);
                    }
                }
            }
            if (excludeCenter) {
                centers.forEach(result::remove);
            }
            return new ArrayList<>(result);
        }
    }

    /**
     * Ванильный селектор игроков: @a, @p, @r, @s, в том числе с аргументами (@a[distance=..10,limit=3]).
     * Разбирается EntitySelectorParser при первом запуске и кэшируется; выполняется от лица игрока из контекста
     * (его позиция и мир) или от консоли, с уровнем прав 2 — селектор написал автор скрипта, а не игрок.
     */
    final class Selector implements Target {
        private final String raw;
        @Nullable
        private EntitySelector parsed;

        public Selector(String raw) {
            this.raw = raw;
        }

        public String raw() {
            return raw;
        }

        @Override
        public List<ServerPlayer> select(ExecutionContext ctx) {
            try {
                if (parsed == null) {
                    StringReader reader = new StringReader(raw);
                    EntitySelector s = new EntitySelectorParser(reader, true).parse();
                    if (reader.canRead()) {
                        throw new ScriptRuntimeException(Messages.get("target.bad_selector", raw, "trailing: " + reader.getRemaining()));
                    }
                    if (s.includesEntities()) {
                        throw new ScriptRuntimeException(Messages.get("target.bad_selector", raw, "players only"));
                    }
                    parsed = s;
                }
                ServerPlayer player = ctx.player();
                CommandSourceStack source = player != null
                        ? player.createCommandSourceStack()
                        : ctx.server().createCommandSourceStack();
                return new ArrayList<>(parsed.findPlayers(source.withPermission(2).withSuppressedOutput()));
            } catch (CommandSyntaxException e) {
                throw new ScriptRuntimeException(Messages.get("target.bad_selector", raw, e.getMessage()));
            }
        }

        @Override
        public String toString() {
            return "Selector[" + raw + "]";
        }
    }
}
