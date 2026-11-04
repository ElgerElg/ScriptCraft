package com.scriptcraft.registry;

import com.scriptcraft.lang.Messages;
import com.scriptcraft.script.parser.BlockDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Блок-слот пула (scriptcraft:dyn_block_N): временное место для define, добавленных ПОСЛЕ запуска
 * (/sc reload). Всё берёт из define, привязанного к слоту прямо сейчас ({@link Bindings}).
 * После перезапуска сервера и клиента define получает настоящий id ({@link RealBlock}), а поставленные
 * слот-блоки переводятся на него ({@link SlotMigration}).
 * Привязки нет (define удалён/выключен) — блок остаётся в мире и называется "свободный слот".
 */
public class PoolBlock extends ScriptBlock {
    private final int slot;

    public PoolBlock(int slot) {
        this.slot = slot;
    }

    public int slot() {
        return slot;
    }

    @Nullable
    @Override
    public BlockDefinition definition() {
        return Bindings.current().block(slot);
    }

    @Override
    protected MutableComponent unboundName() {
        return Component.literal(Messages.get("pool.free_block", slot));
    }

    @Nullable
    @Override
    protected ResourceLocation fallbackLootTable() {
        return null;
    }
}
