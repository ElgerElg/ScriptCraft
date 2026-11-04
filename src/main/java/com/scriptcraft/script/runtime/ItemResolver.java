package com.scriptcraft.script.runtime;

import com.scriptcraft.ScriptCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/** Поиск предмета по id из скрипта: "diamond" -> minecraft:diamond, иначе scriptcraft:<id>; "mod:item" — как есть. */
public final class ItemResolver {
    private ItemResolver() {
    }

    public static Optional<Item> resolve(String id) {
        if (id.contains(":")) {
            return lookup(ResourceLocation.tryParse(id));
        }
        Optional<Item> vanilla = lookup(ResourceLocation.tryParse("minecraft:" + id));
        return vanilla.isPresent() ? vanilla : lookup(ResourceLocation.tryParse(ScriptCraft.MODID + ":" + id));
    }

    private static Optional<Item> lookup(ResourceLocation rl) {
        if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) {
            return Optional.empty();
        }
        return Optional.ofNullable(ForgeRegistries.ITEMS.getValue(rl));
    }
}
