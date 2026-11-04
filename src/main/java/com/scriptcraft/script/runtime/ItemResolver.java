package com.scriptcraft.script.runtime;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.registry.Bindings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * Поиск предмета по id из скрипта.
 * - "ns:path": сначала привязки define (example:ruby_block -> предмет слота scriptcraft:dyn_block_N), затем реестр;
 * - короткий "diamond": minecraft:diamond, затем scriptcraft:<id>, затем привязанный define с таким путём (ruby -> example:ruby).
 * Работает и на клиенте (экраны/HUD): клиент получает ту же таблицу привязок от сервера.
 */
public final class ItemResolver {
    private ItemResolver() {
    }

    public static Optional<Item> resolve(String id) {
        Bindings.Snapshot bindings = Bindings.current();
        if (id.contains(":")) {
            Item bound = bindings.resolveItem(id);
            return bound != null ? Optional.of(bound) : lookup(ResourceLocation.tryParse(id));
        }
        Optional<Item> vanilla = lookup(ResourceLocation.tryParse("minecraft:" + id));
        if (vanilla.isPresent()) {
            return vanilla;
        }
        Optional<Item> own = lookup(ResourceLocation.tryParse(ScriptCraft.MODID + ":" + id));
        if (own.isPresent()) {
            return own;
        }
        return Optional.ofNullable(bindings.resolveByPath(id));
    }

    private static Optional<Item> lookup(ResourceLocation rl) {
        if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) {
            return Optional.empty();
        }
        return Optional.ofNullable(ForgeRegistries.ITEMS.getValue(rl));
    }
}
