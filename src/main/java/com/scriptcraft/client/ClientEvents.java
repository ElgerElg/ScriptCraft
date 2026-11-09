package com.scriptcraft.client;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.network.LogRequestPacket;
import com.scriptcraft.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

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
        ClientToasts.clear();
        StudioSession.reset();
    }

    /**
     * Журнал: F3 + ` по умолчанию. Клавиша не регистрируется в «Управлении» —
     * её меняет шестерёнка журнала. Без прав оператора сервер запрос молча отбрасывает.
     */
    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS || event.getKey() != ClientUiSettings.openKey()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        if (ClientUiSettings.withF3()) {
            long window = mc.getWindow().getWindow();
            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_F3) != GLFW.GLFW_PRESS) {
                return;
            }
        }
        ModNetwork.sendToServer(new LogRequestPacket());
    }

    /** Скрытие частей ванильного HUD по команде скрипта (hide vanilla hotbar for player). */
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (ClientHud.isHidden(event.getOverlay().id())) {
            event.setCanceled(true);
        }
    }
}
