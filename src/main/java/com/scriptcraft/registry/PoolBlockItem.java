package com.scriptcraft.registry;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** Предмет блока-слота: имя и ключ перевода — те же, что у привязанного блока. */
public class PoolBlockItem extends BlockItem {
    private final PoolBlock block;

    public PoolBlockItem(PoolBlock block) {
        super(block, new Properties());
        this.block = block;
    }

    @Override
    public String getDescriptionId() {
        return block.getDescriptionId();
    }

    @Override
    public Component getName(ItemStack stack) {
        return block.getName();
    }
}
