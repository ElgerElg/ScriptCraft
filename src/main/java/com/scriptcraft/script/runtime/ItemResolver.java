package com.scriptcraft.script.runtime;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.registry.ScriptRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * Поиск предмета по id из скрипта: "mod:item" — как есть; короткий "diamond" -> minecraft:diamond,
 * иначе scriptcraft:<id>, иначе первый предмет с таким путём из других namespace скриптов (example:ruby).
 */
public final class ItemResolver {
    private ItemResolver() {
    }

    public static Optional<Item> resolve(String id) {
        if (id.contains(":")) {
            return lookup(ResourceLocation.tryParse(id));
        }
        Optional<Item> vanilla = lookup(ResourceLocation.tryParse("minecraft:" + id));
        if (vanilla.isPresent()) {
            return vanilla;
        }
        Optional<Item> own = lookup(ResourceLocation.tryParse(ScriptCraft.MODID + ":" + id));
        if (own.isPresent()) {
            return own;
        }
        for (String ns : ScriptRegistries.namespaces()) {
            Optional<Item> found = lookup(ResourceLocation.tryParse(ns + ":" + id));
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static Optional<Item> lookup(ResourceLocation rl) {
        if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) {
            return Optional.empty();
        }
        return Optional.ofNullable(ForgeRegistries.ITEMS.getValue(rl));
    }
}
