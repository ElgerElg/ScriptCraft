package com.scriptcraft.mixin;

import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Увеличивает ванильный лимит ожидания рукопожатия входа (по умолчанию 600 тиков = 30 секунд)
 * только на фазе NEGOTIATING (передача реестров и пакетов модов), чтобы игроки с медленным интернетом
 * не кикались с ошибкой "Took too long to log in", не затрагивая фазу авторизации Yggdrasil.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerMixin {
    @Shadow
    private int tick;

    @Shadow
    private Enum<?> state;

    @Unique
    private int scriptcraft$totalLoginTicks;

    @Inject(method = "tick", at = @At("HEAD"))
    private void scriptcraft$extendLoginTimeout(CallbackInfo ci) {
        if (this.state != null && "NEGOTIATING".equals(this.state.name())) {
            scriptcraft$totalLoginTicks++;
            if (this.tick >= 500 && scriptcraft$totalLoginTicks < 6000) {
                this.tick = 400;
            }
        }
    }
}
