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

/** Предмет блока из скрипта (слота или настоящего): имя и ключ перевода — те же, что у блока. */
public class ScriptBlockItem extends BlockItem {
    private final ScriptBlock block;

    public ScriptBlockItem(ScriptBlock block) {
        super(block, new Properties());
        this.block = block;
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(com.scriptcraft.client.geo.ScriptGeoItemRenderer.EXTENSIONS);
    }

    public ScriptBlock scriptBlock() {
        return block;
    }

    @Override
    public String getDescriptionId() {
        return block.getDescriptionId();
    }

    @Override
    public Component getName(ItemStack stack) {
        return block.getName();
    }

    /** Описание (lore) блока + подробные подсказки (F3+H) у временного слота. */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        BlockDefinition def = block.definition();
        if (def != null && def.lore() != null && !def.lore().isBlank()) {
            tooltip.addAll(com.scriptcraft.util.TextFormat.formatLore(def.lore()));
        }
        if (flag.isAdvanced() && def != null && block instanceof PoolBlock) {
            tooltip.add(scriptIdLine(def.id()));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        BlockDefinition def = block.definition();
        return (def != null && def.glow()) || super.isFoil(stack);
    }

    static Component scriptIdLine(String id) {
        return Component.literal(Messages.get("pool.script_id", id)).withStyle(ChatFormatting.DARK_GRAY);
    }
}
