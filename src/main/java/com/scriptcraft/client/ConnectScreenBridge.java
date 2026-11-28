package com.scriptcraft.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Мост для доступа к статусу и кнопкам ConnectScreen из GameRendererMixin поверх любых модов. */
@OnlyIn(Dist.CLIENT)
public interface ConnectScreenBridge {
    String scriptcraft$getStatusString();

    void scriptcraft$renderButtonsOnly(GuiGraphics g, int mouseX, int mouseY, float partialTick);
}
