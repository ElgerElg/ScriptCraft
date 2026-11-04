package com.scriptcraft.registry;

import com.scriptcraft.script.parser.ItemDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Общая основа предметов из скриптов: слотов пула ({@link PoolItem}) и настоящих ({@link RealItem}).
 * Размер стака, редкость, огнестойкость и имя — из define "на лету" ({@link Bindings}); NBT не используется.
 * TODO: событие "on use" из скрипта, анимации (аддон PlayerAnimator / GeckoLib).
 */
public abstract class ScriptItem extends Item {
    protected ScriptItem() {
        super(new Properties().stacksTo(64));
    }

    @Nullable
    public abstract ItemDefinition definition();

    /** Имя, когда define нет. */
    protected abstract Component unboundName();

    @Override
    public String getDescriptionId() {
        ItemDefinition def = definition();
        return def == null ? super.getDescriptionId() : "item." + def.namespace() + "." + def.path();
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return getDescriptionId();
    }

    @Override
    public Component getName(ItemStack stack) {
        ItemDefinition def = definition();
        if (def == null) {
            return unboundName();
        }
        return def.displayName() != null ? Component.literal(def.displayName()) : Component.translatable(getDescriptionId());
    }

    /** Подробные подсказки (F3+H) у слота: рядом с техническим id показываем id из скрипта. */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ItemDefinition def = definition();
        if (flag.isAdvanced() && def != null && this instanceof PoolItem) {
            tooltip.add(ScriptBlockItem.scriptIdLine(def.id()));
        }
    }

    /** Forge спрашивает размер стака у предмета для каждого стака — так он меняется без перезапуска. */
    @Override
    public int getMaxStackSize(ItemStack stack) {
        ItemDefinition def = definition();
        return def == null ? 64 : def.clampedStackSize();
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        ItemDefinition def = definition();
        return def == null ? Rarity.COMMON : def.rarityValue();
    }

    @Override
    public boolean isFireResistant() {
        ItemDefinition def = definition();
        return def != null && def.fireResistant();
    }
}
