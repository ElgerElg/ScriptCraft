package com.scriptcraft.mixin;

import com.scriptcraft.client.ClientLoadScreenRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Наивысший приоритет отрисовки (priority = 20000, прямо перед финальным GuiGraphics.flush()):
 * гарантирует, что загрузочные экраны ScriptCraft с прогресс-баром и процентами рисуются ПОВЕРХ
 * ForgeLoadingOverlay, FancyMenu, DrippyLoadingScreen и любых других модов, а также плавно доводят
 * прогресс входа на сервер до 100% при мгновенном локальном коннекте!
 */
@Mixin(value = GameRenderer.class, priority = 20000)
public abstract class GameRendererMixin {
    @Shadow @Final Minecraft minecraft;
    @Shadow @Final private RenderBuffers renderBuffers;

    @Inject(
            method = "render(FJZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V")
    )
    private void scriptcraft$renderTopPriorityLoadingScreens(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        ClientLoadScreenRenderer.renderTopMostHook(this.minecraft, this.renderBuffers.bufferSource(), partialTick);
    }
}
