package com.scriptcraft.script.runtime;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.scriptcraft.lang.Messages;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Цель-сущность (игроки, targeted entity, entities in radius, селектор @e).
 * Используется в эффектах, которые умеют работать не только с игроками (например, push).
 */
public sealed interface EntityTarget permits EntityTarget.Players, EntityTarget.TargetedEntity,
        EntityTarget.InRadius, EntityTarget.Selector {

    List<Entity> select(ExecutionContext ctx);

    default List<Entity> resolve(ExecutionContext ctx) {
        List<Entity> list = select(ctx);
        ctx.noteTargets(list.size());
        return list;
    }

    @Nullable
    default Entity first(ExecutionContext ctx) {
        List<Entity> list = select(ctx);
        return list.isEmpty() ? null : list.get(0);
    }

    /** Любая цель-игрок (player, targeted player, all players, {var}, ...). */
    record Players(Target target) implements EntityTarget {
        @Override
        public List<Entity> select(ExecutionContext ctx) {
            return new ArrayList<>(target.select(ctx));
        }
    }

    /** targeted entity [of <цель>] — сущность, на которую смотрит игрок (включая мобов и игроков). */
    record TargetedEntity(Target source) implements EntityTarget {
        @Override
        public List<Entity> select(ExecutionContext ctx) {
            Set<Entity> result = new LinkedHashSet<>();
            for (ServerPlayer viewer : source.select(ctx)) {
                Look.Sight sight = Look.sight(viewer);
                if (sight.entity() != null) {
                    result.add(sight.entity());
                }
            }
            return new ArrayList<>(result);
        }
    }

    /** [other] entities in radius N of <цель> — сущности в том же мире не дальше N блоков. */
    record InRadius(Expression radius, Target center, boolean excludeCenter) implements EntityTarget {
        @Override
        public List<Entity> select(ExecutionContext ctx) {
            Object raw = radius.evaluate(ctx);
            Double r = Expression.toNumber(raw);
            if (r == null) {
                throw new ScriptRuntimeException(Messages.get("effect.not_number", Expression.toText(raw)));
            }
            double dist = Math.max(0.0D, r);
            double r2 = dist * dist;
            List<ServerPlayer> centers = center.select(ctx);
            Set<Entity> result = new LinkedHashSet<>();
            for (ServerPlayer c : centers) {
                AABB box = c.getBoundingBox().inflate(dist);
                for (Entity e : c.level().getEntities(excludeCenter ? c : null, box,
                        ent -> !ent.isSpectator() && ent.distanceToSqr(c) <= r2)) {
                    result.add(e);
                }
            }
            if (excludeCenter) {
                centers.forEach(result::remove);
            }
            return new ArrayList<>(result);
        }
    }

    /** Ванильный селектор сущностей: @e, @e[type=zombie,distance=..10]. */
    final class Selector implements EntityTarget {
        private final String raw;
        @Nullable
        private EntitySelector parsed;

        public Selector(String raw) {
            this.raw = raw;
        }

        @Override
        public List<Entity> select(ExecutionContext ctx) {
            try {
                if (parsed == null) {
                    StringReader reader = new StringReader(raw);
                    EntitySelector s = new EntitySelectorParser(reader, true).parse();
                    if (reader.canRead()) {
                        throw new ScriptRuntimeException(Messages.get("target.bad_selector", raw, "trailing: " + reader.getRemaining()));
                    }
                    parsed = s;
                }
                ServerPlayer player = ctx.player();
                CommandSourceStack source = player != null
                        ? player.createCommandSourceStack()
                        : ctx.server().createCommandSourceStack();
                return new ArrayList<>(parsed.findEntities(source.withPermission(2).withSuppressedOutput()));
            } catch (CommandSyntaxException e) {
                throw new ScriptRuntimeException(Messages.get("target.bad_selector", raw, e.getMessage()));
            }
        }
    }
}
