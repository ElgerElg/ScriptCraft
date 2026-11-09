package com.scriptcraft.script.runtime;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Значение в скрипте: число, текст, true/false, переменная {name},
 * свойство игрока (health of player) или random integer between A and B.
 * Результат: Double, String, Boolean или null (переменная не задана, игрока нет).
 */
@FunctionalInterface
public interface Expression {
    @Nullable
    Object evaluate(ExecutionContext ctx);

    /** Свойство игрока в духе Skript: health of player, gamemode of player, x of player... */
    enum Prop {
        HEALTH, MAX_HEALTH, FOOD, SATURATION, LEVEL, XP, AIR, MAX_AIR, ABSORPTION,
        WALK_SPEED, FLY_SPEED, PING, X, Y, Z, YAW, PITCH, GAMEMODE, WORLD, NAME, UUID, HELD, ONLINE
    }

    /** Число/логическое значение как есть. */
    record Literal(Object value) implements Expression {
        @Override
        public Object evaluate(ExecutionContext ctx) {
            return value;
        }
    }

    /** Текст: плейсхолдеры, {переменные} и цвета подставляются при выполнении. */
    record Text(String raw) implements Expression {
        @Override
        public Object evaluate(ExecutionContext ctx) {
            return ctx.format(raw);
        }
    }

    /** Ссылка на переменную {rawName}; внутри имени можно использовать %player% и т.п. */
    record Variable(String rawName) implements Expression {
        @Override
        public Object evaluate(ExecutionContext ctx) {
            return ctx.getVariable(ctx.variableName(rawName));
        }
    }

    /** health of player / x of loop-player / number of all players (target = null, prop = ONLINE). */
    record Property(Prop prop, @Nullable Target target) implements Expression {
        @Override
        public Object evaluate(ExecutionContext ctx) {
            if (prop == Prop.ONLINE) {
                return (double) ctx.server().getPlayerCount();
            }
            ServerPlayer p = target == null ? null : target.first(ctx);
            if (p == null) {
                return null;
            }
            return switch (prop) {
                case HEALTH -> (double) p.getHealth();
                case MAX_HEALTH -> (double) p.getMaxHealth();
                case FOOD -> (double) p.getFoodData().getFoodLevel();
                case SATURATION -> (double) p.getFoodData().getSaturationLevel();
                case LEVEL -> (double) p.experienceLevel;
                case XP -> (double) p.totalExperience;
                case AIR -> (double) p.getAirSupply();
                case MAX_AIR -> (double) p.getMaxAirSupply();
                case ABSORPTION -> (double) p.getAbsorptionAmount();
                case WALK_SPEED -> (double) p.getAbilities().getWalkingSpeed();
                case FLY_SPEED -> (double) p.getAbilities().getFlyingSpeed();
                case PING -> (double) p.latency;
                case X -> p.getX();
                case Y -> p.getY();
                case Z -> p.getZ();
                case YAW -> (double) p.getYRot();
                case PITCH -> (double) p.getXRot();
                case GAMEMODE -> p.gameMode.getGameModeForPlayer().getName();
                case WORLD -> p.level().dimension().location().toString();
                case NAME -> p.getGameProfile().getName();
                case UUID -> p.getStringUUID();
                case HELD -> itemId(p.getMainHandItem());
                case ONLINE -> (double) ctx.server().getPlayerCount();
            };
        }

        private static String itemId(ItemStack stack) {
            if (stack.isEmpty()) {
                return "minecraft:air";
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return id == null ? "minecraft:air" : id.toString();
        }
    }

    /** random integer between A and B — целое от меньшего к большему, включительно. */
    record RandomInt(Expression min, Expression max) implements Expression {
        @Override
        public Object evaluate(ExecutionContext ctx) {
            int[] bounds = integerBounds(min, max, ctx);
            if (bounds == null) {
                return null;
            }
            int lo = bounds[0];
            int hi = bounds[1];
            if (hi <= lo) {
                return (double) lo;
            }
            return (double) (lo + ctx.server().overworld().getRandom().nextInt(hi - lo + 1));
        }
    }

    /** random number between A and B — дробное число в промежутке. */
    record RandomNum(Expression min, Expression max) implements Expression {
        @Override
        public Object evaluate(ExecutionContext ctx) {
            Double a = toNumber(min.evaluate(ctx));
            Double b = toNumber(max.evaluate(ctx));
            if (a == null || b == null) {
                return null;
            }
            double lo = Math.min(a, b);
            double hi = Math.max(a, b);
            return lo + ctx.server().overworld().getRandom().nextDouble() * (hi - lo);
        }
    }

    /**
     * Имя мира к виду minecraft:overworld / the_nether / the_end.
     * Принимает overworld, nether, the_end, minecraft:the_nether.
     */
    static String worldId(String raw) {
        String s = raw.strip().toLowerCase(Locale.ROOT);
        if (s.startsWith("minecraft:")) {
            s = s.substring("minecraft:".length());
        }
        return switch (s) {
            case "overworld", "world", "normal" -> "minecraft:overworld";
            case "nether", "the_nether" -> "minecraft:the_nether";
            case "end", "the_end" -> "minecraft:the_end";
            default -> raw.contains(":") ? raw.strip().toLowerCase(Locale.ROOT) : "minecraft:" + s;
        };
    }

    private static int[] integerBounds(Expression min, Expression max, ExecutionContext ctx) {
        Double a = toNumber(min.evaluate(ctx));
        Double b = toNumber(max.evaluate(ctx));
        if (a == null || b == null) {
            return null;
        }
        int lo = (int) Math.ceil(Math.min(a, b));
        int hi = (int) Math.floor(Math.max(a, b));
        return new int[] {lo, hi};
    }

    /** Пытается превратить значение в число (Double или текст "12.5"). null — если это не число. */
    @Nullable
    static Double toNumber(@Nullable Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.strip());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Значение -> текст для вывода: 5.0 -> "5", null -> "<none>" (как в Skript). */
    static String toText(@Nullable Object value) {
        if (value == null) {
            return "<none>";
        }
        if (value instanceof Double d) {
            if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                return Long.toString(d.longValue());
            }
            return Double.toString(d);
        }
        return String.valueOf(value);
    }
}
