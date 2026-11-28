package com.scriptcraft.mixin;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Holder.Reference.class)
public interface HolderReferenceAccessor<T> {
    @Invoker("bindKey")
    void scriptcraft$bindKey(ResourceKey<T> key);

    @Invoker("bindValue")
    void scriptcraft$bindValue(T value);
}
