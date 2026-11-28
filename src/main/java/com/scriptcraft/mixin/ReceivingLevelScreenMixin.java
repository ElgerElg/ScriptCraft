package com.scriptcraft.mixin;

import com.scriptcraft.client.ClientLoadScreenRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Плавное завершение прогресс-бара (90%..99%) на стадии загрузки чанков мира (ReceivingLevelScreen)
 * с отрисовкой поверх всех модов через GameRendererMixin.
 */
@Mixin(value = ReceivingLevelScreen.class, priority = 20000)
public abstract class ReceivingLevelScreenMixin extends Screen {
    protected ReceivingLevelScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void scriptcraft$renderCustomReceivingLevel(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ClientLoadScreenRenderer.ensureServerConnectStarted();
        ci.cancel();
    }
}
