package com.scriptcraft.script.runtime;

import com.scriptcraft.lang.Messages;
import com.scriptcraft.script.parser.FuncType;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Приводит аргумент вызова к типу параметра.
 * player ищется по имени среди тех, кто онлайн. offline player — только имя.
 * entity и block — это id (как %targeted entity% и %targeted block%), location — закодированная точка.
 */
public final class Functions {
    private Functions() {
    }

    public static Object coerce(FuncType type, @Nullable Object raw, ExecutionContext ctx, String param) {
        return switch (type) {
            case PLAYER -> player(raw, ctx, param);
            case OFFLINE -> offlineName(raw, param);
            case ENTITY, BLOCK, ITEM -> id(raw, param, type);
            case WORLD -> world(raw, param);
            case TEXT -> Expression.toText(raw);
            case LOCATION -> Places.require(raw).encode();
            case NUMBER -> number(raw, param);
            case INTEGER -> (double) Math.round(number(raw, param));
            case BOOLEAN -> bool(raw);
        };
    }

    private static ServerPlayer player(@Nullable Object raw, ExecutionContext ctx, String param) {
        if (raw instanceof ServerPlayer online) {
            ServerPlayer live = ctx.server().getPlayerList().getPlayer(online.getUUID());
            if (live != null) {
                return live;
            }
            raw = online.getGameProfile().getName();
        }
        String name = Expression.toText(raw).strip();
        if (name.isEmpty() || name.equals("<none>")) {
            throw new ScriptRuntimeException(Messages.get("fn.player", param));
        }
        ServerPlayer found = ctx.server().getPlayerList().getPlayerByName(name);
        if (found == null) {
            throw new ScriptRuntimeException(Messages.get("fn.player", param));
        }
        return found;
    }

    private static String offlineName(@Nullable Object raw, String param) {
        if (raw instanceof ServerPlayer online) {
            return online.getGameProfile().getName();
        }
        String name = Expression.toText(raw).strip();
        if (name.isEmpty() || name.equals("<none>")) {
            throw new ScriptRuntimeException(Messages.get("fn.type", param, "offline player"));
        }
        return name;
    }

    private static String id(@Nullable Object raw, String param, FuncType type) {
        String text = Expression.toText(raw).strip();
        if (text.isEmpty() || text.equals("<none>")) {
            throw new ScriptRuntimeException(Messages.get("fn.type", param, type.label()));
        }
        return text;
    }

    private static String world(@Nullable Object raw, String param) {
        String text = id(raw, param, FuncType.WORLD);
        return Expression.worldId(text);
    }

    private static double number(@Nullable Object raw, String param) {
        Double value = Expression.toNumber(raw);
        if (value == null || value.isNaN() || value.isInfinite()) {
            throw new ScriptRuntimeException(Messages.get("fn.type", param, "number"));
        }
        return value;
    }

    private static boolean bool(@Nullable Object raw) {
        if (raw instanceof Boolean value) {
            return value;
        }
        String text = Expression.toText(raw).strip().toLowerCase(java.util.Locale.ROOT);
        return text.equals("true") || text.equals("yes") || text.equals("on") || text.equals("1");
    }
}
