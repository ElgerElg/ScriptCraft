package com.scriptcraft.mixin;

import com.scriptcraft.registry.DynamicRegistryManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.registries.RegistryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(value = GameData.class, remap = false)
public abstract class GameDataMixin {

    @Inject(method = "injectSnapshot", at = @At("HEAD"))
    private static void scriptcraft$beforeInjectSnapshot(
            Map<ResourceLocation, ForgeRegistry.Snapshot> snapshot,
            boolean injectFrozenData,
            boolean isLocalWorld,
            CallbackInfoReturnable<List<ResourceLocation>> cir
    ) {
        DynamicRegistryManager.onBeforeInjectSnapshot(snapshot, isLocalWorld);
    }

    @Inject(method = "revertTo", at = @At("HEAD"))
    private static void scriptcraft$beforeRevertTo(
            RegistryManager target,
            boolean fireEvents,
            CallbackInfo ci
    ) {
        if (target == RegistryManager.FROZEN) {
            DynamicRegistryManager.cleanupClientSessionEntries();
        }
    }
}
