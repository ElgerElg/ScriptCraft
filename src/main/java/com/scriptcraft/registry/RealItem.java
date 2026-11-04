package com.scriptcraft.registry;

import com.scriptcraft.script.parser.ItemDefinition;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Настоящий предмет с id из скрипта (example:ruby), зарегистрированный при запуске. См. {@link RealBlock}. */
public class RealItem extends ScriptItem {
    private final String id;

    public RealItem(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    @Nullable
    @Override
    public ItemDefinition definition() {
        return Bindings.current().realItem(id);
    }

    @Override
    protected Component unboundName() {
        return Component.translatable(getDescriptionId());
    }
}
