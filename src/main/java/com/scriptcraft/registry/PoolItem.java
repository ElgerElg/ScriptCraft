package com.scriptcraft.registry;

import com.scriptcraft.lang.Messages;
import com.scriptcraft.script.parser.ItemDefinition;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Предмет-слот пула (scriptcraft:dyn_item_N): временное место для define item, добавленных после запуска.
 * После перезапуска сервера и клиента стаки переводятся на настоящий id ({@link SlotMigration}).
 */
public class PoolItem extends ScriptItem {
    private final int slot;

    public PoolItem(int slot) {
        this.slot = slot;
    }

    public int slot() {
        return slot;
    }

    @Nullable
    @Override
    public ItemDefinition definition() {
        return Bindings.current().item(slot);
    }

    @Override
    protected Component unboundName() {
        return Component.literal(Messages.get("pool.free_item", slot));
    }
}
