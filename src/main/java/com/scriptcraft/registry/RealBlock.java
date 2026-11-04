package com.scriptcraft.registry;

import com.scriptcraft.script.parser.BlockDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * "Настоящий" блок с id из скрипта (example:ruby_block): регистрируется при запуске игры для define, которые
 * эта сторона знает заранее (сервер — из своих скриптов, клиент — из своих скриптов и кэша define серверов).
 * Работают ванильные /give, /setblock, автодополнение, F3, свойства состояния (facing, light).
 * Свойства (прочность, звук, имя) — из define на сервере "на лету", как у слотов.
 * define нет (удалён или клиент знает id по другому серверу) — обычный блок с переводом block.ns.path.
 */
public class RealBlock extends ScriptBlock {
    private final String id;

    public RealBlock(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    @Nullable
    @Override
    public BlockDefinition definition() {
        return Bindings.current().realBlock(id);
    }

    @Override
    protected MutableComponent unboundName() {
        return Component.translatable(getDescriptionId());
    }

    @Nullable
    @Override
    protected ResourceLocation fallbackLootTable() {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : ResourceLocation.tryBuild(rl.getNamespace(), "blocks/" + rl.getPath());
    }
}
