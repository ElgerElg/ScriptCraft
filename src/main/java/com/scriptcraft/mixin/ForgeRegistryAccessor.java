package com.scriptcraft.mixin;

import com.google.common.collect.BiMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.BitSet;
import java.util.Map;

@Mixin(value = ForgeRegistry.class, remap = false)
public interface ForgeRegistryAccessor<V> {
    @Invoker("add")
    int invokeAdd(int id, ResourceLocation key, V value);

    @Accessor("ids")
    BiMap<Integer, V> getIds();

    @Accessor("names")
    BiMap<ResourceLocation, V> getNames();

    @Accessor("keys")
    BiMap<ResourceKey<V>, V> getKeys();

    @Accessor("availabilityMap")
    BitSet getAvailabilityMap();

    @Accessor("delegatesByName")
    Map<ResourceLocation, Holder.Reference<V>> getDelegatesByName();

    @Accessor("delegatesByValue")
    Map<V, Holder.Reference<V>> getDelegatesByValue();

    @Accessor("owners")
    BiMap<?, V> getOwners();
}
