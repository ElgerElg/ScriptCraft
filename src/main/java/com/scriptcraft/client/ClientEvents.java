package com.scriptcraft.client;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.network.InputPacket;
import com.scriptcraft.network.LogRequestPacket;
import com.scriptcraft.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
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
        ClientInput.reset();
    }

    /**
     * Ванильное меню ещё не открылось. Если в on open есть cancel event, экран не показывается.
     * Свой экран скрипта не трогаем: его открывают как раз после отмены инвентаря.
     * InputEvent.Key отменять нельзя — клавиша E сама по себе инвентарь не открывает, это делает экран.
     */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen screen = event.getNewScreen();
        if (screen instanceof ScriptScreen) {
            return;
        }
        String menu = ClientMenus.name(screen);
        if (menu == null) {
            return;
        }
        if (ClientInput.cancels(menu)) {
            event.setCanceled(true);
        }
        if (ClientInput.watchesMenu(menu)) {
            ModNetwork.sendToServer(new InputPacket(InputPacket.OPEN, menu));
        }
    }

    /**
     * Журнал: F3 + ` по умолчанию. Клавиша не регистрируется в «Управлении» —
     * её меняет шестерёнка журнала. Без прав оператора сервер запрос молча отбрасывает.
     */
    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        int key = event.getKey();
        if (key == ClientUiSettings.openKey()) {
            boolean studio = true;
            if (ClientUiSettings.withF3()) {
                long window = mc.getWindow().getWindow();
                studio = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_F3) == GLFW.GLFW_PRESS;
            }
            if (studio) {
                ModNetwork.sendToServer(new LogRequestPacket());
            }
        }
        // Клавиша не проглатывается: setCanceled на InputEvent.Key роняет игру.
        String name = ClientKeys.name(key);
        if (name != null && ClientInput.watchesKey(name)) {
            ModNetwork.sendToServer(new InputPacket(InputPacket.KEY, name));
        }
    }

    /** Скрытие частей ванильного HUD по команде скрипта (hide vanilla hotbar for player). */
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (ClientHud.isHidden(event.getOverlay().id())) {
            event.setCanceled(true);
        }
    }
}
