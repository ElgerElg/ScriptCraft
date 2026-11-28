package com.scriptcraft.mixin;

import com.scriptcraft.client.ClientLoadScreenRenderer;
import com.scriptcraft.client.ConnectScreenBridge;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Кастомный экран подключения к серверу с прогресс-баром, процентами (0%..100%)
 * и наивысшим приоритетом (дублируется из GameRendererMixin поверх любых сторонних модов).
 */
@Mixin(value = ConnectScreen.class, priority = 20000)
public abstract class ConnectScreenMixin extends Screen implements ConnectScreenBridge {
    @Shadow private Component status;

    protected ConnectScreenMixin(Component title) {
        super(title);
    }

    @Override
    public String scriptcraft$getStatusString() {
        return this.status == null ? "" : this.status.getString();
    }

    @Override
    public void scriptcraft$renderButtonsOnly(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        for (Renderable renderable : this.renderables) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void scriptcraft$onConnectInit(CallbackInfo ci) {
        ClientLoadScreenRenderer.beginServerConnect();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void scriptcraft$renderCustomConnect(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ci.cancel();
        // Сама карточка и кнопки гарантированно рисуются поверх всех слоёв в GameRendererMixin.renderTopMostHook!
    }
}
