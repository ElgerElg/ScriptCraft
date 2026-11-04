package com.scriptcraft.registry;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Предмет для блока из скрипта. Берёт отображаемое имя у самого блока. */
public class ScriptBlockItem extends BlockItem {
    public ScriptBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        if (getBlock() instanceof ScriptBlock scriptBlock && scriptBlock.customName() != null) {
            return Component.literal(scriptBlock.customName());
        }
        return super.getName(stack);
    }
}
