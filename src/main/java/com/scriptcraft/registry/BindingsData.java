package com.scriptcraft.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Привязки конкретного мира (world/data/scriptcraft_bindings.dat): id из скрипта -> номер слота.
 * Главный источник правды: в чанках мира хранятся слоты (scriptcraft:dyn_block_N), поэтому один раз выданный
 * слот за id закрепляется навсегда — даже если define удалили, слот не отдаётся другому блоку,
 * и поставленные блоки не превращаются во что-то другое.
 */
public final class BindingsData extends SavedData {
    public static final String NAME = "scriptcraft_bindings";

    final Map<String, Integer> blocks = new LinkedHashMap<>();
    final Map<String, Integer> items = new LinkedHashMap<>();

    public static BindingsData load(CompoundTag tag) {
        BindingsData data = new BindingsData();
        read(tag.getCompound("blocks"), data.blocks);
        read(tag.getCompound("items"), data.items);
        return data;
    }

    private static void read(CompoundTag tag, Map<String, Integer> into) {
        for (String id : tag.getAllKeys()) {
            into.put(id, tag.getInt(id));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("blocks", write(blocks));
        tag.put("items", write(items));
        return tag;
    }

    private static CompoundTag write(Map<String, Integer> map) {
        CompoundTag tag = new CompoundTag();
        map.forEach(tag::putInt);
        return tag;
    }
}
