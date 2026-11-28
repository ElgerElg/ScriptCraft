package com.scriptcraft.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.scriptcraft.client.ClientLoadScreenRenderer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Заменяет стандартный красный экран перезагрузки ресурсов Mojang на стильное меню ScriptCraft
 * с плавным прогресс-баром, точным процентом (0%..100%) и описанием текущего этапа!
 */
@Mixin(value = LoadingOverlay.class, priority = 20000)
public abstract class LoadingOverlayMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ReloadInstance reload;
    @Shadow @Final private Consumer<Optional<Throwable>> onFinish;
    @Shadow @Final private boolean fadeIn;
    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;
    @Shadow private long fadeInStart;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void scriptcraft$renderCustomReloadOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ci.cancel();
        ClientLoadScreenRenderer.markOverlayRenderedDirectly();

        int width = g.guiWidth();
        int height = g.guiHeight();
        long now = Util.getMillis();

        if (this.fadeIn && this.fadeInStart == -1L) {
            this.fadeInStart = now;
        }

        float fadeOutFrac = this.fadeOutStart > -1L ? (float) (now - this.fadeOutStart) / 600.0F : -1.0F;
        float fadeInFrac = this.fadeInStart > -1L ? (float) (now - this.fadeInStart) / 250.0F : -1.0F;

        // Во время плавного растворения рисуем игровой мир или текущий экран позади оверлея
        float cardAlpha;
        if (fadeOutFrac >= 1.0F) {
            if (this.minecraft.screen != null) {
                this.minecraft.screen.render(g, 0, 0, partialTick);
            }
            cardAlpha = 0.0F;
        } else if (fadeOutFrac >= 0.0F) {
            if (this.minecraft.screen != null && this.fadeIn) {
                this.minecraft.screen.render(g, mouseX, mouseY, partialTick);
            }
            cardAlpha = 1.0F - Mth.clamp(fadeOutFrac, 0.0F, 1.0F);
        } else if (this.fadeIn) {
            if (this.minecraft.screen != null && fadeInFrac < 1.0F) {
                this.minecraft.screen.render(g, mouseX, mouseY, partialTick);
            }
            cardAlpha = Mth.clamp(fadeInFrac, 0.0F, 1.0F);
        } else {
            cardAlpha = 1.0F;
        }

        float actual = this.reload.getActualProgress();
        this.currentProgress = Mth.clamp(this.currentProgress * 0.90F + actual * 0.10F, 0.0F, 1.0F);
        float shownProgress = this.fadeOutStart > -1L ? 1.0F : this.currentProgress;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        String title = ClientLoadScreenRenderer.isRu() ? "ЗАГРУЗКА РЕСУРСОВ" : "LOADING RESOURCES";
        String stage = ClientLoadScreenRenderer.currentReloadStage(shownProgress);
        ClientLoadScreenRenderer.renderCard(g, width, height, shownProgress, title, stage, "", cardAlpha, false);

        RenderSystem.disableBlend();

        if (fadeOutFrac >= 1.0F) {
            ClientLoadScreenRenderer.clearReloadReason();
            this.minecraft.setOverlay(null);
        }

        if (this.fadeOutStart == -1L && this.reload.isDone() && (!this.fadeIn || fadeInFrac >= 1.0F)) {
            try {
                this.reload.checkExceptions();
                this.onFinish.accept(Optional.empty());
            } catch (Throwable t) {
                this.onFinish.accept(Optional.of(t));
            }
            this.fadeOutStart = Util.getMillis();
            if (this.minecraft.screen != null) {
                this.minecraft.screen.init(this.minecraft, g.guiWidth(), g.guiHeight());
            }
        }
    }
}
