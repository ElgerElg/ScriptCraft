package com.scriptcraft.client;

import com.scriptcraft.ScriptCraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Клиентские события на шине МОДА (класс загружается только на клиенте). */
@Mod.EventBusSubscriber(modid = ScriptCraft.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    /** HUD из скриптов: слой < 0 — под ванильным HUD, слой >= 0 — поверх всего. */
    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerBelowAll("hud_below", ClientHud::renderBelow);
        event.registerAboveAll("hud", ClientHud::renderAbove);
    }
}
