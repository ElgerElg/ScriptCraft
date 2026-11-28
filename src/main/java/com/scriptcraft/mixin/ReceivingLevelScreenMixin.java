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
 * Плавное завершение прогресс-бара (88%..99%) на стадии загрузки чанков мира (ReceivingLevelScreen).
 */
@Mixin(ReceivingLevelScreen.class)
public abstract class ReceivingLevelScreenMixin extends Screen {
    protected ReceivingLevelScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void scriptcraft$renderCustomReceivingLevel(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ci.cancel();
        float progress = ClientLoadScreenRenderer.stepServerJoinProgress(0.90f, 0.99f, partialTick);
        String title = ClientLoadScreenRenderer.isRu() ? "ЗАГРУЗКА МИРА" : "LOADING WORLD";
        String stage = ClientLoadScreenRenderer.isRu()
                ? "Генерация ландшафта и синхронизация сущностей..."
                : "Receiving terrain chunks & syncing entities...";
        ClientLoadScreenRenderer.renderCard(g, this.width, this.height, progress, title, stage, "", 1.0f, false);
    }
}
