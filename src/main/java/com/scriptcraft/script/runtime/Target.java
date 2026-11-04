package com.scriptcraft.script.runtime;

import com.scriptcraft.lang.Messages;
import net.minecraft.server.level.ServerPlayer;

/**
 * Над кем выполняется эффект: "player" (игрок из контекста) или игрок по имени ("Steve", {target}, "%args%").
 */
public sealed interface Target permits Target.Self, Target.Named {
    ServerPlayer resolve(ExecutionContext ctx);

    /** player — игрок, который вызвал команду / зашёл / написал эффект-команду. */
    record Self() implements Target {
        @Override
        public ServerPlayer resolve(ExecutionContext ctx) {
            return ctx.requirePlayer();
        }
    }

    /** Игрок по имени: "Steve", {var}, "%args%". Должен быть онлайн. */
    record Named(Expression name) implements Target {
        @Override
        public ServerPlayer resolve(ExecutionContext ctx) {
            String n = Expression.toText(name.evaluate(ctx)).strip();
            ServerPlayer p = ctx.server().getPlayerList().getPlayerByName(n);
            if (p == null) {
                throw new ScriptRuntimeException(Messages.get("effect.player_offline", n));
            }
            return p;
        }
    }
}
