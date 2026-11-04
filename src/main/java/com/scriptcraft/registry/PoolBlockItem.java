package com.scriptcraft.registry;

import com.scriptcraft.lang.Messages;
import com.scriptcraft.script.parser.BlockDefinition;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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

    /** Подробные подсказки (F3+H): рядом с техническим id показываем id из скрипта. */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        BlockDefinition def = block.definition();
        if (flag.isAdvanced() && def != null) {
            tooltip.add(scriptIdLine(def.id()));
        }
    }

    static Component scriptIdLine(String id) {
        return Component.literal(Messages.get("pool.script_id", id)).withStyle(ChatFormatting.DARK_GRAY);
    }
}
