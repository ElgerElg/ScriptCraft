package com.scriptcraft.script.runtime;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Условие для "if ...:".
 * Сравнения, is set, and/or, и проверки игрока: is sneaking, is op, is holding, has item, gamemode, world.
 */
@FunctionalInterface
public interface Condition {
    boolean test(ExecutionContext ctx);

    /** {var} is set / is not set */
    record IsSet(Expression value, boolean negate) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            return (value.evaluate(ctx) != null) != negate;
        }
    }

    /** <цель> is set / is not set — набор игроков не пуст / пуст (например, targeted player is set). */
    record TargetSet(Target target, boolean negate) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            if (target instanceof Target.Self && ctx.player() == null) {
                return negate; // "player is set" из консоли — игрока нет, это не ошибка
            }
            return !target.select(ctx).isEmpty() != negate;
        }
    }

    /** Сравнение двух значений. op: "is", "is not", ">", "<", ">=", "<=". */
    record Compare(Expression left, String op, Expression right) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            Object a = left.evaluate(ctx);
            Object b = right.evaluate(ctx);
            Double na = Expression.toNumber(a);
            Double nb = Expression.toNumber(b);
            switch (op) {
                case "is", "is not" -> {
                    boolean equal;
                    if (na != null && nb != null) {
                        equal = na.doubleValue() == nb.doubleValue();
                    } else if (a == null || b == null) {
                        equal = Objects.equals(a, b);
                    } else {
                        // Текст сравнивается без учёта регистра, как в Skript.
                        equal = Expression.toText(a).equalsIgnoreCase(Expression.toText(b));
                    }
                    return op.equals("is") == equal;
                }
                default -> {
                    if (na == null || nb == null) {
                        return false; // сравнивать можно только числа
                    }
                    return switch (op) {
                        case ">" -> na > nb;
                        case "<" -> na < nb;
                        case ">=" -> na >= nb;
                        case "<=" -> na <= nb;
                        default -> false;
                    };
                }
            }
        }
    }

    record And(Condition left, Condition right) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            return left.test(ctx) && right.test(ctx);
        }
    }

    record Or(Condition left, Condition right) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            return left.test(ctx) || right.test(ctx);
        }
    }

    /** player is [not] sneaking / sprinting / flying / sleeping / burning / op / alive / blocking / gliding / swimming. */
    enum Check {
        SNEAKING, SPRINTING, FLYING, SLEEPING, ON_FIRE, OP, ALIVE, BLOCKING, GLIDING, SWIMMING
    }

    record State(Target target, Check check, boolean negate) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            return every(target, ctx, p -> matches(p, check), negate);
        }

        private static boolean matches(ServerPlayer p, Check check) {
            return switch (check) {
                case SNEAKING -> p.isCrouching();
                case SPRINTING -> p.isSprinting();
                case FLYING -> p.getAbilities().flying;
                case SLEEPING -> p.isSleeping();
                case ON_FIRE -> p.isOnFire();
                case OP -> p.getServer() != null && p.getServer().getPlayerList().isOp(p.getGameProfile());
                case ALIVE -> p.isAlive();
                case BLOCKING -> p.isBlocking();
                case GLIDING -> p.isFallFlying();
                case SWIMMING -> p.isSwimming();
            };
        }
    }

    /** player is [not] in creative|survival|adventure|spectator [mode]. */
    record GameModeIs(Target target, GameType mode, boolean negate) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            return every(target, ctx, p -> p.gameMode.getGameModeForPlayer() == mode, negate);
        }
    }

    /** player is [not] online. Пустая цель (игрока нет в сети) — это offline. */
    record Online(Target target, boolean wantOnline) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            return !target.select(ctx).isEmpty() == wantOnline;
        }
    }

    /** player is [not] holding &lt;предмет&gt; — предмет в основной руке. */
    record Holding(Target target, String itemId, boolean negate) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            Item want = ItemResolver.resolve(itemId).orElse(null);
            if (want == null) {
                return false;
            }
            return every(target, ctx, p -> p.getMainHandItem().is(want), negate);
        }
    }

    /** player has &lt;предмет&gt; / player does not have &lt;предмет&gt; — есть в инвентаре. */
    record HasItem(Target target, String itemId, boolean negate) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            Item want = ItemResolver.resolve(itemId).orElse(null);
            if (want == null) {
                return false;
            }
            ItemStack probe = new ItemStack(want);
            return every(target, ctx, p -> p.getInventory().contains(probe), negate);
        }
    }

    /** player has permission &lt;0-4&gt;. */
    record Permission(Target target, int level, boolean negate) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            return every(target, ctx, p -> p.hasPermissions(level), negate);
        }
    }

    /** player is [not] in world overworld|nether|the_end|namespace:path. */
    record InWorld(Target target, String worldId, boolean negate) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            String want = Expression.worldId(worldId);
            return every(target, ctx, p -> p.level().dimension().location().toString().equals(want), negate);
        }
    }

    /**
     * Условие верно, если оно верно для каждого игрока цели.
     * Никого нет — условие ложно (и для "is", и для "is not"): оффлайн — это не "не крадётся".
     */
    private static boolean every(Target target, ExecutionContext ctx, Predicate<ServerPlayer> test, boolean negate) {
        List<ServerPlayer> players = target.select(ctx);
        if (players.isEmpty()) {
            return false;
        }
        for (ServerPlayer p : players) {
            if (test.test(p) == negate) {
                return false;
            }
        }
        return true;
    }
}
