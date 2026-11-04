package com.scriptcraft.registry;

import com.scriptcraft.lang.Messages;
import com.scriptcraft.script.parser.ItemDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import org.jetbrains.annotations.Nullable;

/**
 * Предмет-слот пула (scriptcraft:dyn_item_N). Размер стака, редкость, огнестойкость и имя —
 * из define, привязанного к слоту сейчас ({@link Bindings}); меняются при /sc reload без перезапуска.
 * TODO: событие "on use" из скрипта, анимации (аддон PlayerAnimator / GeckoLib).
 */
public class PoolItem extends Item {
    private final int slot;

    public PoolItem(int slot) {
        super(new Properties().stacksTo(64));
        this.slot = slot;
    }

    public int slot() {
        return slot;
    }

    @Nullable
    public ItemDefinition definition() {
        return Bindings.current().item(slot);
    }

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
            return Component.literal(Messages.get("pool.free_item", slot));
        }
        return def.displayName() != null ? Component.literal(def.displayName()) : Component.translatable(getDescriptionId());
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
