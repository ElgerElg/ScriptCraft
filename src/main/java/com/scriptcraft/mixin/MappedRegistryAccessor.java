package com.scriptcraft.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(MappedRegistry.class)
public interface MappedRegistryAccessor<T> {
    @Accessor("frozen")
    boolean scriptcraft$isFrozen();

    @Accessor("frozen")
    void scriptcraft$setFrozen(boolean frozen);

    @Accessor("unregisteredIntrusiveHolders")
    Map<T, Holder.Reference<T>> scriptcraft$getUnregisteredIntrusiveHolders();

    @Accessor("unregisteredIntrusiveHolders")
    void scriptcraft$setUnregisteredIntrusiveHolders(Map<T, Holder.Reference<T>> holders);
}
