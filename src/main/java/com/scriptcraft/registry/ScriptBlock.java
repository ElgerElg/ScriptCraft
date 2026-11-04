package com.scriptcraft.registry;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Блок, объявленный в скрипте. Пока это обычный блок с настраиваемыми свойствами
 * (прочность, звук, свет) и необязательным отображаемым именем.
 * TODO: события блока из скриптов (on click / on break), форма (VoxelShape), BlockEntity.
 */
public class ScriptBlock extends Block {
    @Nullable
    private final String customName;

    public ScriptBlock(Properties properties, @Nullable String customName) {
        super(properties);
        this.customName = customName;
    }

    @Nullable
    public String customName() {
        return customName;
    }

    @Override
    public MutableComponent getName() {
        // Если в скрипте задано name: "...", показываем его вместо ключа перевода.
        return customName != null ? Component.literal(customName) : super.getName();
    }
}
