package com.scriptcraft.client;

import com.scriptcraft.ScriptCraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Клиентские события на шине Forge (класс загружается только на клиенте). */
@Mod.EventBusSubscriber(modid = ScriptCraft.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientResources.reset();
        ClientHud.reset();
        ClientBindings.onLoggingOut();
    }

    /** Скрытие частей ванильного HUD по команде скрипта (hide vanilla hotbar for player). */
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (ClientHud.isHidden(event.getOverlay().id())) {
            event.setCanceled(true);
        }
    }
}
