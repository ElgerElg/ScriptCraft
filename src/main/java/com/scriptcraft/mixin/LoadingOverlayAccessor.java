package com.scriptcraft.mixin;

import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LoadingOverlay.class)
public interface LoadingOverlayAccessor {
    @Accessor("reload")
    ReloadInstance scriptcraft$getReload();

    @Accessor("fadeOutStart")
    long scriptcraft$getFadeOutStart();

    @Accessor("fadeIn")
    boolean scriptcraft$isFadeIn();
}
