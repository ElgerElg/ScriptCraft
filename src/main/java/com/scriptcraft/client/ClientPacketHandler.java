package com.scriptcraft.client;

import com.scriptcraft.screen.ScreenDescription;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Клиентская обработка пакетов. Этот пакет (com.scriptcraft.client) используется ТОЛЬКО на клиенте. */
@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void openScreen(ScreenDescription description) {
        Minecraft.getInstance().setScreen(new ScriptScreen(description));
    }
}
