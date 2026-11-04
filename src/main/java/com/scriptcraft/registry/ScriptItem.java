package com.scriptcraft.registry;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Предмет, объявленный в скрипте.
 * TODO: событие "on use" из скрипта, анимации (аддон PlayerAnimator / GeckoLib).
 */
public class ScriptItem extends Item {
    @Nullable
    private final String customName;

    public ScriptItem(Properties properties, @Nullable String customName) {
        super(properties);
        this.customName = customName;
    }

    @Override
    public Component getName(ItemStack stack) {
        return customName != null ? Component.literal(customName) : super.getName(stack);
    }
}
