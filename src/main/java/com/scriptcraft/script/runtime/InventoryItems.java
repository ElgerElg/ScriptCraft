package com.scriptcraft.script.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** NBT предметов в инвентаре игрока: весь инвентарь или один слот. */
public final class InventoryItems {
    private InventoryItems() {
    }

    /** SNBT списка непустых слотов. Пустой инвентарь — {@code []}. */
    public static String inventory(ServerPlayer player) {
        ListTag list = new ListTag();
        int size = player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag tag = new CompoundTag();
            tag.putInt("Slot", i);
            stack.save(tag);
            list.add(tag);
        }
        return list.toString();
    }

    public static String slot(ServerPlayer player, int index, @Nullable String path) {
        if (index < 0 || index >= player.getInventory().getContainerSize()) {
            return "<none>";
        }
        ItemStack stack = player.getInventory().getItem(index);
        if (stack.isEmpty()) {
            return "<none>";
        }
        if (path == null || path.isBlank()) {
            CompoundTag tag = new CompoundTag();
            stack.save(tag);
            return tag.getAsString();
        }
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return "<none>";
        }
        return NbtAccess.format(tag, path);
    }
}
