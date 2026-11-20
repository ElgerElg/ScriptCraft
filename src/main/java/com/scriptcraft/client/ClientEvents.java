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
     * Студия: F3 + ` по умолчанию. Клавиша не регистрируется в «Управлении» —
     * её меняет шестерёнка. Без права scriptcraft.studio и без оператора сервер отвечает отказом.
     */
    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        int key = event.getKey();
        if (event.getAction() == GLFW.GLFW_PRESS && key == ClientUiSettings.openKey()) {
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
        if (name == null) {
            return;
        }
        if (event.getAction() == GLFW.GLFW_PRESS && ClientInput.watchesKey(name)) {
            ModNetwork.sendToServer(new InputPacket(InputPacket.KEY, name));
        } else if (event.getAction() == GLFW.GLFW_RELEASE && ClientInput.watchesRelease(name)) {
            ModNetwork.sendToServer(new InputPacket(InputPacket.KEY_RELEASE, name));
        } else if (event.getAction() == GLFW.GLFW_REPEAT && ClientInput.watchesHold(name)) {
            ModNetwork.sendToServer(new InputPacket(InputPacket.KEY_HOLD, name));
        }
    }

    /** Скрытие частей ванильного HUD по команде скрипта (hide vanilla hotbar for player). */
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (ClientHud.isHidden(event.getOverlay().id())) {
            event.setCanceled(true);
        }
    }

    /**
     * Если на сервере в on jump есть cancel event, гасим прыжок прямо на клиенте в момент отрыва от земли,
     * чтобы игрок вообще не подпрыгивал на полблока, и уведомляем сервер для выполнения тела on jump.
     */
    @SubscribeEvent
    public static void onLivingJump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || event.getEntity() != mc.player) {
            return;
        }
        if (!ClientInput.cancels(com.scriptcraft.script.InputSync.JUMP_TOKEN)) {
            return;
        }
        net.minecraft.world.phys.Vec3 v = mc.player.getDeltaMovement();
        double vx = v.x;
        double vz = v.z;
        if (mc.player.isSprinting()) {
            float rad = mc.player.getYRot() * ((float) Math.PI / 180F);
            vx += net.minecraft.util.Mth.sin(rad) * 0.2D;
            vz -= net.minecraft.util.Mth.cos(rad) * 0.2D;
        }
        mc.player.setDeltaMovement(vx, 0.0D, vz);
        mc.player.hasImpulse = false;
        mc.player.setOnGround(true);
        ModNetwork.sendToServer(new InputPacket(InputPacket.OPEN, com.scriptcraft.script.InputSync.JUMP_TOKEN));
    }
}
