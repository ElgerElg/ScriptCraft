package com.scriptcraft.registry;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.mixin.ForgeRegistryAccessor;
import com.scriptcraft.script.parser.BlockDefinition;
import com.scriptcraft.script.parser.ItemDefinition;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Динамическая регистрация настоящих modid:block и modid:item через Mixin без перезапуска клиента и сервера:
 *  1) Вариант Б (/sc reload на работающем сервере): новые define block / item / armor сразу получают настоящие ID
 *     в ACTIVE и FROZEN реестрах сервера и горячим пакетом прописываются на всех онлайн-клиентах под теми же ID!
 *  2) Вход нового игрока на сервер (GameDataMixin.onBeforeInjectSnapshot): все кастомные блоки/предметы сервера
 *     автоматически создаются на клиенте ДО сверки реестров Forge — вход с 1-й попытки без кика и без кэша слотов!
 *  3) Выход с сервера (cleanupClientSessionEntries): временные записи чужого сервера убираются из FROZEN,
 *     чтобы клиент мог заходить на любые другие серверы без конфликтов.
 */
public final class DynamicRegistryManager {
    /** Записи, динамически добавленные на клиенте только на время текущей сетевой сессии. */
    private static final Set<ResourceLocation> SESSION_CLIENT_BLOCKS = new LinkedHashSet<>();
    private static final Set<ResourceLocation> SESSION_CLIENT_ITEMS = new LinkedHashSet<>();
    private static volatile boolean clientInjectedNewEntriesOnJoin = false;

    public record HotEntry(String id, boolean isBlock, int blockIntId, int itemIntId) {
    }

    private DynamicRegistryManager() {
    }

    public static boolean consumeClientInjectedFlag() {
        boolean v = clientInjectedNewEntriesOnJoin;
        clientInjectedNewEntriesOnJoin = false;
        return v;
    }

    /**
     * Сервер (старт мира или горячий /sc reload — Вариант Б):
     * регистрирует все новые define block / define item настоящими объектами RealBlock / RealItem.
     */
    public static synchronized List<HotEntry> ensureServerRealEntries(
            Iterable<BlockDefinition> blocks,
            Iterable<ItemDefinition> items
    ) {
        List<HotEntry> added = new ArrayList<>();

        ForgeRegistry<Block> activeBlocks = RegistryManager.ACTIVE.getRegistry(ForgeRegistries.Keys.BLOCKS);
        ForgeRegistry<Item> activeItems = RegistryManager.ACTIVE.getRegistry(ForgeRegistries.Keys.ITEMS);
        ForgeRegistry<Block> frozenBlocks = RegistryManager.FROZEN.getRegistry(ForgeRegistries.Keys.BLOCKS);
        ForgeRegistry<Item> frozenItems = RegistryManager.FROZEN.getRegistry(ForgeRegistries.Keys.ITEMS);

        for (BlockDefinition b : blocks) {
            String id = b.id();
            if (!ScriptRegistries.isValidDynamicRealId(id) || ScriptRegistries.isRealBlock(id)) {
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) continue;

            RealBlock realBlock = activeBlocks.containsKey(rl) && activeBlocks.getValue(rl) instanceof RealBlock existingB
                    ? existingB : new RealBlock(id);
            int blockIntId = registerInto(activeBlocks, -1, rl, realBlock);
            if (frozenBlocks != null && !frozenBlocks.containsKey(rl)) {
                registerInto(frozenBlocks, blockIntId, rl, realBlock);
            }

            ScriptBlockItem blockItem = activeItems.containsKey(rl) && activeItems.getValue(rl) instanceof ScriptBlockItem existingBi
                    ? existingBi : new ScriptBlockItem(realBlock);
            int itemIntId = registerInto(activeItems, -1, rl, blockItem);
            if (frozenItems != null && !frozenItems.containsKey(rl)) {
                registerInto(frozenItems, itemIntId, rl, blockItem);
            }

            ScriptRegistries.recordDynamicRealBlock(id, realBlock, blockItem);
            added.add(new HotEntry(id, true, blockIntId, itemIntId));
            ScriptCraft.LOGGER.info("[ScriptCraft] Динамически зарегистрирован настоящий блок {} (blockId={}, itemId={})",
                    id, blockIntId, itemIntId);
        }

        for (ItemDefinition it : items) {
            String id = it.id();
            if (!ScriptRegistries.isValidDynamicRealId(id) || ScriptRegistries.isRealBlock(id) || ScriptRegistries.isRealItem(id)) {
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null || activeBlocks.containsKey(rl)) continue;

            RealItem realItem = activeItems.containsKey(rl) && activeItems.getValue(rl) instanceof RealItem existingIt
                    ? existingIt : new RealItem(id);
            int itemIntId = registerInto(activeItems, -1, rl, realItem);
            if (frozenItems != null && !frozenItems.containsKey(rl)) {
                registerInto(frozenItems, itemIntId, rl, realItem);
            }

            ScriptRegistries.recordDynamicRealItem(id, realItem);
            added.add(new HotEntry(id, false, -1, itemIntId));
            ScriptCraft.LOGGER.info("[ScriptCraft] Динамически зарегистрирован настоящий предмет {} (itemId={})",
                    id, itemIntId);
        }

        return added;
    }

    /**
     * Клиент (GameDataMixin.injectSnapshot, ДО сверки реестров Forge при входе на сервер):
     * смотрит в присланный сервером снимок и мгновенно регистрирует недостающие скриптовые блоки/предметы!
     */
    public static synchronized void onBeforeInjectSnapshot(
            Map<ResourceLocation, ForgeRegistry.Snapshot> snapshot,
            boolean isLocalWorld
    ) {
        if (isLocalWorld || snapshot == null) {
            return;
        }
        ForgeRegistry.Snapshot blockSnap = snapshot.get(ForgeRegistries.Keys.BLOCKS.location());
        ForgeRegistry.Snapshot itemSnap = snapshot.get(ForgeRegistries.Keys.ITEMS.location());

        ForgeRegistry<Block> activeBlocks = RegistryManager.ACTIVE.getRegistry(ForgeRegistries.Keys.BLOCKS);
        ForgeRegistry<Item> activeItems = RegistryManager.ACTIVE.getRegistry(ForgeRegistries.Keys.ITEMS);
        ForgeRegistry<Block> frozenBlocks = RegistryManager.FROZEN.getRegistry(ForgeRegistries.Keys.BLOCKS);
        ForgeRegistry<Item> frozenItems = RegistryManager.FROZEN.getRegistry(ForgeRegistries.Keys.ITEMS);

        int injected = 0;

        if (blockSnap != null && blockSnap.ids != null) {
            for (var entry : blockSnap.ids.object2IntEntrySet()) {
                ResourceLocation rl = entry.getKey();
                if (frozenBlocks.containsKey(rl) || !isScriptCandidateNamespace(rl.getNamespace())) {
                    continue;
                }
                String id = rl.toString();
                RealBlock block = new RealBlock(id);
                registerInto(activeBlocks, -1, rl, block);
                registerInto(frozenBlocks, -1, rl, block);
                SESSION_CLIENT_BLOCKS.add(rl);

                // Если у этого блока есть парный предмет в снимке ITEMS — создаём ScriptBlockItem
                if (itemSnap != null && itemSnap.ids != null && itemSnap.ids.containsKey(rl) && !frozenItems.containsKey(rl)) {
                    ScriptBlockItem blockItem = new ScriptBlockItem(block);
                    registerInto(activeItems, -1, rl, blockItem);
                    registerInto(frozenItems, -1, rl, blockItem);
                    SESSION_CLIENT_ITEMS.add(rl);
                    ScriptRegistries.recordDynamicRealBlock(id, block, blockItem);
                } else {
                    ScriptRegistries.recordDynamicRealBlock(id, block, null);
                }
                injected++;
            }
        }

        if (itemSnap != null && itemSnap.ids != null) {
            for (var entry : itemSnap.ids.object2IntEntrySet()) {
                ResourceLocation rl = entry.getKey();
                if (frozenItems.containsKey(rl) || !isScriptCandidateNamespace(rl.getNamespace())) {
                    continue;
                }
                String id = rl.toString();
                RealItem item = new RealItem(id);
                registerInto(activeItems, -1, rl, item);
                registerInto(frozenItems, -1, rl, item);
                SESSION_CLIENT_ITEMS.add(rl);
                ScriptRegistries.recordDynamicRealItem(id, item);
                injected++;
            }
        }

        if (injected > 0) {
            clientInjectedNewEntriesOnJoin = true;
            ScriptCraft.LOGGER.info("[ScriptCraft] На лету зарегистрировано {} серверных записей (modid:id) при рукопожатии", injected);
        }
    }

    /**
     * Клиент в онлайне (Вариант Б — горячий /sc reload на сервере без перезахода!):
     * регистрирует новые блоки/предметы ровно под теми же числовыми ID, что выдал сервер.
     */
    public static synchronized boolean applyHotEntriesOnClient(List<HotEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return false;
        }
        ForgeRegistry<Block> activeBlocks = RegistryManager.ACTIVE.getRegistry(ForgeRegistries.Keys.BLOCKS);
        ForgeRegistry<Item> activeItems = RegistryManager.ACTIVE.getRegistry(ForgeRegistries.Keys.ITEMS);
        ForgeRegistry<Block> frozenBlocks = RegistryManager.FROZEN.getRegistry(ForgeRegistries.Keys.BLOCKS);
        ForgeRegistry<Item> frozenItems = RegistryManager.FROZEN.getRegistry(ForgeRegistries.Keys.ITEMS);

        boolean anyNew = false;
        for (HotEntry e : entries) {
            ResourceLocation rl = ResourceLocation.tryParse(e.id());
            if (rl == null) continue;
            if (e.isBlock()) {
                RealBlock block;
                if (activeBlocks.containsKey(rl) && activeBlocks.getValue(rl) instanceof RealBlock existing) {
                    block = existing;
                } else {
                    block = new RealBlock(e.id());
                    registerInto(activeBlocks, e.blockIntId(), rl, block);
                    if (frozenBlocks != null && !frozenBlocks.containsKey(rl)) {
                        registerInto(frozenBlocks, -1, rl, block);
                        SESSION_CLIENT_BLOCKS.add(rl);
                    }
                    anyNew = true;
                }
                ScriptBlockItem bi = null;
                if (e.itemIntId() >= 0) {
                    if (activeItems.containsKey(rl) && activeItems.getValue(rl) instanceof ScriptBlockItem existingBi) {
                        bi = existingBi;
                    } else {
                        bi = new ScriptBlockItem(block);
                        registerInto(activeItems, e.itemIntId(), rl, bi);
                        if (frozenItems != null && !frozenItems.containsKey(rl)) {
                            registerInto(frozenItems, -1, rl, bi);
                            SESSION_CLIENT_ITEMS.add(rl);
                        }
                        anyNew = true;
                    }
                }
                ScriptRegistries.recordDynamicRealBlock(e.id(), block, bi);
            } else {
                if (!activeItems.containsKey(rl)) {
                    RealItem item = new RealItem(e.id());
                    registerInto(activeItems, e.itemIntId(), rl, item);
                    if (frozenItems != null && !frozenItems.containsKey(rl)) {
                        registerInto(frozenItems, -1, rl, item);
                        SESSION_CLIENT_ITEMS.add(rl);
                    }
                    ScriptRegistries.recordDynamicRealItem(e.id(), item);
                    anyNew = true;
                }
            }
        }
        return anyNew;
    }

    /**
     * Выход клиента с сервера: очищаем временные серверные записи из FROZEN,
     * чтобы при входе на другой сервер реестр клиента был чистым.
     */
    public static synchronized void cleanupClientSessionEntries() {
        if (SESSION_CLIENT_BLOCKS.isEmpty() && SESSION_CLIENT_ITEMS.isEmpty()) {
            return;
        }
        ForgeRegistry<Block> frozenBlocks = RegistryManager.FROZEN.getRegistry(ForgeRegistries.Keys.BLOCKS);
        ForgeRegistry<Item> frozenItems = RegistryManager.FROZEN.getRegistry(ForgeRegistries.Keys.ITEMS);
        for (ResourceLocation rl : SESSION_CLIENT_BLOCKS) {
            removeFromRegistry(frozenBlocks, rl);
            ScriptRegistries.removeDynamicClientEntry(rl.toString());
        }
        for (ResourceLocation rl : SESSION_CLIENT_ITEMS) {
            removeFromRegistry(frozenItems, rl);
            ScriptRegistries.removeDynamicClientEntry(rl.toString());
        }
        ScriptCraft.LOGGER.info("[ScriptCraft] Очищено временных записей сессии сервера: блоков {}, предметов {}",
                SESSION_CLIENT_BLOCKS.size(), SESSION_CLIENT_ITEMS.size());
        SESSION_CLIENT_BLOCKS.clear();
        SESSION_CLIENT_ITEMS.clear();
    }

    @SuppressWarnings("unchecked")
    private static <V> int registerInto(ForgeRegistry<V> reg, int preferredId, ResourceLocation rl, V value) {
        reg.unfreeze();
        try {
            ForgeRegistryAccessor<V> acc = (ForgeRegistryAccessor<V>) reg;
            return acc.invokeAdd(preferredId, rl, value);
        } finally {
            reg.freeze();
        }
    }

    @SuppressWarnings("unchecked")
    private static <V> void removeFromRegistry(ForgeRegistry<V> reg, ResourceLocation rl) {
        if (reg == null || !reg.containsKey(rl)) {
            return;
        }
        ForgeRegistryAccessor<V> acc = (ForgeRegistryAccessor<V>) reg;
        V val = acc.getNames().remove(rl);
        if (val == null) {
            return;
        }
        Integer intId = acc.getIds().inverse().remove(val);
        if (intId != null && intId >= 0) {
            acc.getAvailabilityMap().clear(intId);
        }
        ResourceKey<V> rKey = ResourceKey.create(reg.getRegistryKey(), rl);
        acc.getKeys().remove(rKey);
        Holder.Reference<V> holder = acc.getDelegatesByName().remove(rl);
        acc.getDelegatesByValue().remove(val);
        acc.getOwners().inverse().remove(val);
    }

    private static boolean isScriptCandidateNamespace(String ns) {
        if (ns == null || ns.isEmpty()) return false;
        String low = ns.toLowerCase(Locale.ROOT);
        if (low.equals("minecraft") || low.equals("forge") || low.equals("neoforge") || low.equals("c")) {
            return false;
        }
        // Если мод с таким modid реально установлен на клиенте и это не сам scriptcraft — не перехватываем чужой мод
        if (!low.equals(ScriptCraft.MODID) && ModList.get().isLoaded(low)) {
            return false;
        }
        return true;
    }
}
