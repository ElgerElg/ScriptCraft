package com.scriptcraft.client;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.client.geo.GeoArmorLayer;
import com.scriptcraft.client.geo.PlayerPropLayer;
import com.scriptcraft.client.geo.ScriptFakeNpcRenderer;
import com.scriptcraft.client.geo.ScriptGeoBlockRenderer;
import com.scriptcraft.client.geo.ScriptGeoEntityRenderer;
import com.scriptcraft.registry.ScriptRegistries;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
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
        event.registerAboveAll("notices", ClientToasts::render);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ScriptRegistries.GEO_ENTITY.get(), ScriptGeoEntityRenderer::new);
        event.registerEntityRenderer(ScriptRegistries.FAKE_NPC.get(), ScriptFakeNpcRenderer::new);
        event.registerBlockEntityRenderer(ScriptRegistries.SCRIPT_BLOCK_ENTITY.get(), ScriptGeoBlockRenderer::new);
    }

    @SubscribeEvent
    public static void onAddPlayerLayers(EntityRenderersEvent.AddLayers event) {
        for (String skinName : event.getSkins()) {
            if (event.getSkin(skinName) instanceof PlayerRenderer playerRenderer) {
                playerRenderer.addLayer(new GeoArmorLayer<>(playerRenderer));
                playerRenderer.addLayer(new PlayerPropLayer<>(playerRenderer));
            }
        }
    }
}
