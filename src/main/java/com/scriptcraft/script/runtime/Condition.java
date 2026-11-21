package com.scriptcraft.script.runtime;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;

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

    /** player has screen ["Title"] open / player is [not] in screen ["Title"]. */
    record ScreenOpen(Target target, @Nullable String expectedTitle, boolean negate) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            return every(target, ctx, p -> com.scriptcraft.screen.ScreenManager.hasScreenOpen(p, expectedTitle), negate);
        }
    }

    /** player is [not] online. Пустая цель (игрока нет в сети) — это offline. */
    record Online(Target target, boolean wantOnline) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            return !target.select(ctx).isEmpty() == wantOnline;
        }
    }

    /** player is [not] holding &lt;предмет&gt; — предмет в основной руке. Nbt в id тоже сравнивается. */
    record Holding(Target target, String itemId, boolean negate) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            return every(target, ctx, p -> ItemStacks.matches(p.getMainHandItem(), itemId), negate);
        }
    }

    /** player has &lt;предмет&gt; / player does not have &lt;предмет&gt; — есть в инвентаре. */
    record HasItem(Target target, String itemId, boolean negate) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            return every(target, ctx, p -> {
                for (ItemStack stack : p.getInventory().items) {
                    if (ItemStacks.matches(stack, itemId)) {
                        return true;
                    }
                }
                for (ItemStack stack : p.getInventory().armor) {
                    if (ItemStacks.matches(stack, itemId)) {
                        return true;
                    }
                }
                return ItemStacks.matches(p.getInventory().offhand.get(0), itemId);
            }, negate);
        }
    }

    /** player has permission &lt;0-4&gt; или узел LuckPerms. node == null — числовой уровень. */
    record Permission(Target target, int level, @Nullable String node, boolean negate) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            return every(target, ctx, p -> node != null ? Permissions.hasNode(p, node) : p.hasPermissions(level), negate);
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
     * player is banned / "Steve" is banned.
     * Пустая цель без имени — ложь. Имя, которого нет в сети, проверяется по бан-листу.
     */
    record Banned(Target target, boolean negate) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            if (target instanceof Target.Local local) {
                Object value = ctx.local(local.name());
                if (value instanceof ServerPlayer player) {
                    return Effects.isBanned(ctx, player.getGameProfile()) != negate;
                }
            }
            List<ServerPlayer> players = target.select(ctx);
            if (!players.isEmpty()) {
                for (ServerPlayer player : players) {
                    if (Effects.isBanned(ctx, player.getGameProfile()) == negate) {
                        return false;
                    }
                }
                return true;
            }
            String name = Effects.storedName(target, ctx);
            if (name != null) {
                return Effects.isBannedName(ctx, name) != negate;
            }
            return false;
        }
    }

    /** player is in group "vip". Нет LuckPerms — ложь. Имя оффлайн проверяется, если профиль уже загружен. */
    record InGroup(Target target, Expression group, boolean negate) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
        if (!LuckPermsLink.available()) {
            return false;
        }
        String name = Expression.toText(group.evaluate(ctx));
        List<ServerPlayer> players = target.select(ctx);
        if (!players.isEmpty()) {
            for (ServerPlayer player : players) {
                if (LuckPermsLink.inGroup(player, name) == negate) {
                    return false;
                }
            }
            return true;
        }
        if (Effects.storedName(target, ctx) == null) {
            return false;
        }
        return LuckPermsLink.member(ctx, target, name) != negate;
        }
    }

    /** {list::*} contains <val> / does not contain <val>. */
    record ListContains(String rawPattern, Expression needle, boolean negate) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            String name = ctx.variableName(rawPattern);
            String prefix = name.endsWith("::*") ? name.substring(0, name.length() - 3) : name;
            Object want = needle.evaluate(ctx);
            Double wantNum = Expression.toNumber(want);
            String wantStr = Expression.toText(want);
            boolean found = false;
            for (java.util.Map.Entry<String, Object> e : com.scriptcraft.script.variables.VariableStore.children(prefix)) {
                Object v = e.getValue();
                Double vNum = Expression.toNumber(v);
                if (wantNum != null && vNum != null) {
                    if (Double.compare(wantNum, vNum) == 0) {
                        found = true;
                        break;
                    }
                } else if (wantStr.equalsIgnoreCase(Expression.toText(v))) {
                    found = true;
                    break;
                }
            }
            return found != negate;
        }
    }

    /** {list::*} has key <key> / does not have key <key>. */
    record ListHasKey(String rawPattern, Expression key, boolean negate) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            String name = ctx.variableName(rawPattern);
            String prefix = name.endsWith("::*") ? name.substring(0, name.length() - 3) : name;
            String k = Expression.toText(key.evaluate(ctx));
            boolean exists = com.scriptcraft.script.variables.VariableStore.get(prefix + "::" + k) != null;
            return exists != negate;
        }
    }

    /** {list::*} is [not] empty. */
    record ListEmpty(String rawPattern, boolean negate) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            String name = ctx.variableName(rawPattern);
            String prefix = name.endsWith("::*") ? name.substring(0, name.length() - 3) : name;
            boolean empty = com.scriptcraft.script.variables.VariableStore.children(prefix).isEmpty();
            return empty != negate;
        }
    }

    /** Проверка текста: <текст> contains / starts with / ends with <подстрока> (без учёта регистра, как в Skript). */
    record TextMatch(Expression text, Expression part, String mode, boolean negate) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            Object a = text.evaluate(ctx);
            Object b = part.evaluate(ctx);
            if (a == null || b == null) {
                return negate;
            }
            String hay = Expression.toText(a).toLowerCase(java.util.Locale.ROOT);
            String needle = Expression.toText(b).toLowerCase(java.util.Locale.ROOT);
            boolean match = switch (mode) {
                case "starts" -> hay.startsWith(needle);
                case "ends" -> hay.endsWith(needle);
                default -> hay.contains(needle);
            };
            return match != negate;
        }
    }

    /** <текст/переменная> is [not] empty */
    record TextEmpty(Expression text, boolean negate) implements Condition {
        @Override
        public boolean test(ExecutionContext ctx) {
            Object a = text.evaluate(ctx);
            boolean empty = a == null || Expression.toText(a).isEmpty() || "<none>".equals(Expression.toText(a));
            return empty != negate;
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
