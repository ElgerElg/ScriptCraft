package com.scriptcraft.mixin;

import com.scriptcraft.client.ClientLoadScreenRenderer;
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
 * и отображением докачки ресурсов кэша и синхронизации настоящих modid:id до входа в мир!
 */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin extends Screen {
    @Shadow private Component status;

    protected ConnectScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void scriptcraft$onConnectInit(CallbackInfo ci) {
        ClientLoadScreenRenderer.beginServerConnect();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void scriptcraft$renderCustomConnect(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ci.cancel();

        String vanillaStatus = this.status == null ? "" : this.status.getString();
        float progress = ClientLoadScreenRenderer.stepServerJoinProgress(0.08f, 0.85f, partialTick);
        String title = ClientLoadScreenRenderer.isRu() ? "ВХОД НА СЕРВЕР" : "JOINING SERVER";
        String stage = ClientLoadScreenRenderer.currentJoinStage(vanillaStatus);
        String detail = ClientLoadScreenRenderer.currentJoinDetail();
        if (detail.isEmpty() && !vanillaStatus.equals(stage)) {
            detail = vanillaStatus;
        }

        ClientLoadScreenRenderer.renderCard(g, this.width, this.height, progress, title, stage, detail, 1.0f, true);

        // Отрисовываем кнопку «Отмена» поверх кастомного фона
        for (Renderable renderable : this.renderables) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }
    }
}
