package com.scriptcraft.registry;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.mixin.ForgeRegistryAccessor;
import com.scriptcraft.mixin.HolderReferenceAccessor;
import com.scriptcraft.mixin.MappedRegistryAccessor;
import com.scriptcraft.script.parser.BlockDefinition;
import com.scriptcraft.script.parser.ItemDefinition;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Динамическая регистрация настоящих modid:block и modid:item через Mixin без перезапуска клиента и сервера.
 *
 * ВАЖНО для Minecraft 1.20.1:
 * Конструкторы {@link Block} и {@link Item} вызывают {@code BuiltInRegistries.BLOCK/ITEM.createIntrusiveHolder(this)}.
 * После завершения загрузки модов {@code MappedRegistry.freeze()} выставляет {@code frozen = true} и зануляет
 * {@code unregisteredIntrusiveHolders = null}, из-за чего создание нового {@code Block}/{@code Item} бросало бы
 * {@code IllegalStateException} (в том числе внутри {@code HandshakeHandler.handleRegistryLoading}, блокируя
 * {@code CountDownLatch.await()} на сетевом потоке Netty!).
 * Поэтому перед конструированием любых динамических блоков/предметов мы временно открываем и {@link ForgeRegistry},
 * и {@link MappedRegistry} ({@code unregisteredIntrusiveHolders}), привязываем intrusive holder и корректно
 * возвращаем заморозку в {@code finally}.
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

        boolean anyNewBlock = false;
        unlockAll(activeBlocks, frozenBlocks, BuiltInRegistries.BLOCK);
        unlockAll(activeItems, frozenItems, BuiltInRegistries.ITEM);
        try {
            for (BlockDefinition b : blocks) {
                String id = b.id();
                if (!ScriptRegistries.isValidDynamicRealId(id) || ScriptRegistries.isRealBlock(id)) {
                    continue;
                }
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl == null) continue;

                // Защита от перезаписи блоков чужих установленных модов (например lhs:printer),
                // иначе Forge помещает дубликат в FROZEN.overrides и падает при выходе с сервера!
                if (activeBlocks.containsKey(rl) && !(activeBlocks.getValue(rl) instanceof RealBlock)) {
                    ScriptCraft.LOGGER.warn("[ScriptCraft] define block '{}' пропущен: этот ID уже зарегистрирован другим модом ({})",
                            id, activeBlocks.getValue(rl).getClass().getName());
                    continue;
                }
                if (activeItems.containsKey(rl) && !(activeItems.getValue(rl) instanceof ScriptBlockItem)) {
                    ScriptCraft.LOGGER.warn("[ScriptCraft] define block '{}' пропущен: предмет с таким ID уже зарегистрирован другим модом ({})",
                            id, activeItems.getValue(rl).getClass().getName());
                    continue;
                }

                RealBlock realBlock = activeBlocks.containsKey(rl) && activeBlocks.getValue(rl) instanceof RealBlock existingB
                        ? existingB : new RealBlock(id);
                int blockIntId = addUnlocked(activeBlocks, -1, rl, realBlock);
                bindIntrusiveBlockHolder(rl, realBlock);
                if (frozenBlocks != null && !frozenBlocks.containsKey(rl)) {
                    addUnlocked(frozenBlocks, blockIntId, rl, realBlock);
                }
                anyNewBlock = true;

                ScriptBlockItem blockItem = activeItems.containsKey(rl) && activeItems.getValue(rl) instanceof ScriptBlockItem existingBi
                        ? existingBi : new ScriptBlockItem(realBlock);
                int itemIntId = addUnlocked(activeItems, -1, rl, blockItem);
                bindIntrusiveItemHolder(rl, blockItem);
                if (frozenItems != null && !frozenItems.containsKey(rl)) {
                    addUnlocked(frozenItems, itemIntId, rl, blockItem);
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

                if (activeItems.containsKey(rl) && !(activeItems.getValue(rl) instanceof RealItem)) {
                    ScriptCraft.LOGGER.warn("[ScriptCraft] define item '{}' пропущен: этот ID уже зарегистрирован другим модом ({})",
                            id, activeItems.getValue(rl).getClass().getName());
                    continue;
                }

                RealItem realItem = activeItems.containsKey(rl) && activeItems.getValue(rl) instanceof RealItem existingIt
                        ? existingIt : new RealItem(id);
                int itemIntId = addUnlocked(activeItems, -1, rl, realItem);
                bindIntrusiveItemHolder(rl, realItem);
                if (frozenItems != null && !frozenItems.containsKey(rl)) {
                    addUnlocked(frozenItems, itemIntId, rl, realItem);
                }

                ScriptRegistries.recordDynamicRealItem(id, realItem);
                added.add(new HotEntry(id, false, -1, itemIntId));
                ScriptCraft.LOGGER.info("[ScriptCraft] Динамически зарегистрирован настоящий предмет {} (itemId={})",
                        id, itemIntId);
            }

            if (anyNewBlock) {
                activeBlocks.bake();
            }
        } catch (Throwable t) {
            ScriptCraft.LOGGER.error("[ScriptCraft] Ошибка динамической регистрации на сервере", t);
        } finally {
            lockAll(activeItems, frozenItems, BuiltInRegistries.ITEM);
            lockAll(activeBlocks, frozenBlocks, BuiltInRegistries.BLOCK);
        }

        return added;
    }

    /**
     * Клиент (GameDataMixin.injectSnapshot, ДО сверки реестров Forge при входе на сервер):
     * смотрит в присланный сервером снимок и мгновенно регистрирует недостающие скриптовые блоки/предметы!
     * Обязательно перехватывает любые исключения, чтобы никогда не заблокировать CountDownLatch в HandshakeHandler!
     */
    public static synchronized void onBeforeInjectSnapshot(
            Map<ResourceLocation, ForgeRegistry.Snapshot> snapshot,
            boolean isLocalWorld
    ) {
        if (isLocalWorld || snapshot == null) {
            return;
        }
        ForgeRegistry<Block> activeBlocks = RegistryManager.ACTIVE.getRegistry(ForgeRegistries.Keys.BLOCKS);
        ForgeRegistry<Item> activeItems = RegistryManager.ACTIVE.getRegistry(ForgeRegistries.Keys.ITEMS);
        ForgeRegistry<Block> frozenBlocks = RegistryManager.FROZEN.getRegistry(ForgeRegistries.Keys.BLOCKS);
        ForgeRegistry<Item> frozenItems = RegistryManager.FROZEN.getRegistry(ForgeRegistries.Keys.ITEMS);

        ForgeRegistry.Snapshot blockSnap = snapshot.get(ForgeRegistries.Keys.BLOCKS.location());
        ForgeRegistry.Snapshot itemSnap = snapshot.get(ForgeRegistries.Keys.ITEMS.location());

        // Быстрая проверка: есть ли вообще недостающие скриптовые записи?
        boolean needBlocks = false;
        if (blockSnap != null && blockSnap.ids != null) {
            for (ResourceLocation rl : blockSnap.ids.keySet()) {
                if (!frozenBlocks.containsKey(rl) && isClientInjectableNamespace(rl.getNamespace())) {
                    needBlocks = true;
                    break;
                }
            }
        }
        boolean needItems = false;
        if (itemSnap != null && itemSnap.ids != null) {
            for (ResourceLocation rl : itemSnap.ids.keySet()) {
                if (!frozenItems.containsKey(rl) && isClientInjectableNamespace(rl.getNamespace())) {
                    needItems = true;
                    break;
                }
            }
        }
        if (!needBlocks && !needItems) {
            return;
        }

        int injected = 0;
        unlockAll(activeBlocks, frozenBlocks, BuiltInRegistries.BLOCK);
        unlockAll(activeItems, frozenItems, BuiltInRegistries.ITEM);
        try {
            if (blockSnap != null && blockSnap.ids != null) {
                for (var entry : blockSnap.ids.object2IntEntrySet()) {
                    ResourceLocation rl = entry.getKey();
                    if (frozenBlocks.containsKey(rl) || !isClientInjectableNamespace(rl.getNamespace())) {
                        continue;
                    }
                    // Если у сервера включён старый режим слотов (dyn_block_N), а на клиенте он отключён — создаём временный PoolBlock на сессию
                    int legacyBlockSlot = rl.getNamespace().equals(ScriptCraft.MODID)
                            ? ScriptRegistries.parseSlot(rl.getPath(), ScriptRegistries.BLOCK_PREFIX, ScriptRegistries.BLOCK_SLOTS) : -1;
                    if (legacyBlockSlot >= 0) {
                        PoolBlock pb = activeBlocks.containsKey(rl) && activeBlocks.getValue(rl) instanceof PoolBlock exPb
                                ? exPb : new PoolBlock(legacyBlockSlot);
                        if (!activeBlocks.containsKey(rl)) {
                            addUnlocked(activeBlocks, -1, rl, pb);
                        }
                        bindIntrusiveBlockHolder(rl, pb);
                        addUnlocked(frozenBlocks, -1, rl, pb);
                        SESSION_CLIENT_BLOCKS.add(rl);
                        if (itemSnap != null && itemSnap.ids != null && itemSnap.ids.containsKey(rl) && !frozenItems.containsKey(rl)) {
                            ScriptBlockItem pbi = activeItems.containsKey(rl) && activeItems.getValue(rl) instanceof ScriptBlockItem exPbi
                                    ? exPbi : new ScriptBlockItem(pb);
                            if (!activeItems.containsKey(rl)) {
                                addUnlocked(activeItems, -1, rl, pbi);
                            }
                            bindIntrusiveItemHolder(rl, pbi);
                            addUnlocked(frozenItems, -1, rl, pbi);
                            SESSION_CLIENT_ITEMS.add(rl);
                        }
                        injected++;
                        continue;
                    }

                    String id = rl.toString();
                    if (activeBlocks.containsKey(rl) && !(activeBlocks.getValue(rl) instanceof RealBlock)) {
                        continue;
                    }
                    RealBlock block = activeBlocks.containsKey(rl) && activeBlocks.getValue(rl) instanceof RealBlock exB
                            ? exB : new RealBlock(id);
                    if (!activeBlocks.containsKey(rl)) {
                        addUnlocked(activeBlocks, -1, rl, block);
                    }
                    bindIntrusiveBlockHolder(rl, block);
                    addUnlocked(frozenBlocks, -1, rl, block);
                    SESSION_CLIENT_BLOCKS.add(rl);

                    if (itemSnap != null && itemSnap.ids != null && itemSnap.ids.containsKey(rl) && !frozenItems.containsKey(rl)) {
                        ScriptBlockItem blockItem = activeItems.containsKey(rl) && activeItems.getValue(rl) instanceof ScriptBlockItem exBi
                                ? exBi : new ScriptBlockItem(block);
                        if (!activeItems.containsKey(rl)) {
                            addUnlocked(activeItems, -1, rl, blockItem);
                        }
                        bindIntrusiveItemHolder(rl, blockItem);
                        addUnlocked(frozenItems, -1, rl, blockItem);
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
                    if (frozenItems.containsKey(rl) || !isClientInjectableNamespace(rl.getNamespace())) {
                        continue;
                    }
                    int legacyItemSlot = rl.getNamespace().equals(ScriptCraft.MODID)
                            ? ScriptRegistries.parseSlot(rl.getPath(), ScriptRegistries.ITEM_PREFIX, ScriptRegistries.ITEM_SLOTS) : -1;
                    if (legacyItemSlot >= 0) {
                        PoolItem pi = activeItems.containsKey(rl) && activeItems.getValue(rl) instanceof PoolItem exPi
                                ? exPi : new PoolItem(legacyItemSlot);
                        if (!activeItems.containsKey(rl)) {
                            addUnlocked(activeItems, -1, rl, pi);
                        }
                        bindIntrusiveItemHolder(rl, pi);
                        addUnlocked(frozenItems, -1, rl, pi);
                        SESSION_CLIENT_ITEMS.add(rl);
                        injected++;
                        continue;
                    }

                    String id = rl.toString();
                    if (activeItems.containsKey(rl) && !(activeItems.getValue(rl) instanceof RealItem)) {
                        continue;
                    }
                    RealItem item = activeItems.containsKey(rl) && activeItems.getValue(rl) instanceof RealItem exIt
                            ? exIt : new RealItem(id);
                    if (!activeItems.containsKey(rl)) {
                        addUnlocked(activeItems, -1, rl, item);
                    }
                    bindIntrusiveItemHolder(rl, item);
                    addUnlocked(frozenItems, -1, rl, item);
                    SESSION_CLIENT_ITEMS.add(rl);
                    ScriptRegistries.recordDynamicRealItem(id, item);
                    injected++;
                }
            }
        } catch (Throwable t) {
            ScriptCraft.LOGGER.error("[ScriptCraft] Ошибка инъекции серверных modid:id при входе", t);
        } finally {
            lockAll(activeItems, frozenItems, BuiltInRegistries.ITEM);
            lockAll(activeBlocks, frozenBlocks, BuiltInRegistries.BLOCK);
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
        boolean anyNewBlock = false;
        unlockAll(activeBlocks, frozenBlocks, BuiltInRegistries.BLOCK);
        unlockAll(activeItems, frozenItems, BuiltInRegistries.ITEM);
        try {
            for (HotEntry e : entries) {
                ResourceLocation rl = ResourceLocation.tryParse(e.id());
                if (rl == null) continue;
                if (e.isBlock()) {
                    RealBlock block;
                    if (activeBlocks.containsKey(rl) && activeBlocks.getValue(rl) instanceof RealBlock existing) {
                        block = existing;
                    } else {
                        block = new RealBlock(e.id());
                        addUnlocked(activeBlocks, e.blockIntId(), rl, block);
                        bindIntrusiveBlockHolder(rl, block);
                        if (frozenBlocks != null && !frozenBlocks.containsKey(rl)) {
                            addUnlocked(frozenBlocks, -1, rl, block);
                            SESSION_CLIENT_BLOCKS.add(rl);
                        }
                        anyNew = true;
                        anyNewBlock = true;
                    }
                    ScriptBlockItem bi = null;
                    if (e.itemIntId() >= 0) {
                        if (activeItems.containsKey(rl) && activeItems.getValue(rl) instanceof ScriptBlockItem existingBi) {
                            bi = existingBi;
                        } else {
                            bi = new ScriptBlockItem(block);
                            addUnlocked(activeItems, e.itemIntId(), rl, bi);
                            bindIntrusiveItemHolder(rl, bi);
                            if (frozenItems != null && !frozenItems.containsKey(rl)) {
                                addUnlocked(frozenItems, -1, rl, bi);
                                SESSION_CLIENT_ITEMS.add(rl);
                            }
                            anyNew = true;
                        }
                    }
                    ScriptRegistries.recordDynamicRealBlock(e.id(), block, bi);
                } else {
                    if (!activeItems.containsKey(rl)) {
                        RealItem item = new RealItem(e.id());
                        addUnlocked(activeItems, e.itemIntId(), rl, item);
                        bindIntrusiveItemHolder(rl, item);
                        if (frozenItems != null && !frozenItems.containsKey(rl)) {
                            addUnlocked(frozenItems, -1, rl, item);
                            SESSION_CLIENT_ITEMS.add(rl);
                        }
                        ScriptRegistries.recordDynamicRealItem(e.id(), item);
                        anyNew = true;
                    }
                }
            }
            if (anyNewBlock) {
                activeBlocks.bake();
            }
        } catch (Throwable t) {
            ScriptCraft.LOGGER.error("[ScriptCraft] Ошибка горячей регистрации HotEntry на клиенте", t);
        } finally {
            lockAll(activeItems, frozenItems, BuiltInRegistries.ITEM);
            lockAll(activeBlocks, frozenBlocks, BuiltInRegistries.BLOCK);
        }
        return anyNew;
    }

    /**
     * Выход клиента с сервера: очищаем временные серверные записи из FROZEN,
     * чтобы при входе на другой сервер реестр клиента был чистым.
     */
    public static synchronized void cleanupClientSessionEntries() {
        try {
            ForgeRegistry<Block> activeBlocks = RegistryManager.ACTIVE.getRegistry(ForgeRegistries.Keys.BLOCKS);
            ForgeRegistry<Item> activeItems = RegistryManager.ACTIVE.getRegistry(ForgeRegistries.Keys.ITEMS);
            ForgeRegistry<Block> frozenBlocks = RegistryManager.FROZEN.getRegistry(ForgeRegistries.Keys.BLOCKS);
            ForgeRegistry<Item> frozenItems = RegistryManager.FROZEN.getRegistry(ForgeRegistries.Keys.ITEMS);
            if (!SESSION_CLIENT_BLOCKS.isEmpty()) {
                for (ResourceLocation rl : SESSION_CLIENT_BLOCKS) {
                    removeFromRegistry(frozenBlocks, rl);
                    ScriptRegistries.removeDynamicClientEntry(rl.toString());
                }
            }
            if (!SESSION_CLIENT_ITEMS.isEmpty()) {
                for (ResourceLocation rl : SESSION_CLIENT_ITEMS) {
                    removeFromRegistry(frozenItems, rl);
                    ScriptRegistries.removeDynamicClientEntry(rl.toString());
                }
            }
            // Защита от ванильного бага ForgeRegistry.sync: очищаем overrides и серверные blocked-биты перед revertToFrozen
            sanitizeRegistryBeforeRevert(activeBlocks, frozenBlocks);
            sanitizeRegistryBeforeRevert(activeItems, frozenItems);
            if (!SESSION_CLIENT_BLOCKS.isEmpty() || !SESSION_CLIENT_ITEMS.isEmpty()) {
                ScriptCraft.LOGGER.info("[ScriptCraft] Очищено временных записей сессии сервера: блоков {}, предметов {}",
                        SESSION_CLIENT_BLOCKS.size(), SESSION_CLIENT_ITEMS.size());
            }
        } catch (Throwable t) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Не удалось очистить временные записи сессии: {}", t.toString());
        } finally {
            SESSION_CLIENT_BLOCKS.clear();
            SESSION_CLIENT_ITEMS.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private static <V> void sanitizeRegistryBeforeRevert(ForgeRegistry<V> active, ForgeRegistry<V> frozen) {
        if (active != null) {
            ForgeRegistryAccessor<V> aAcc = (ForgeRegistryAccessor<V>) active;
            if (aAcc.getOverrides() != null) {
                aAcc.getOverrides().clear();
            }
            clearBlockedField(active);
        }
        if (frozen != null) {
            ForgeRegistryAccessor<V> fAcc = (ForgeRegistryAccessor<V>) frozen;
            if (fAcc.getOverrides() != null && !fAcc.getOverrides().isEmpty()) {
                fAcc.getOverrides().clear();
            }
            clearBlockedField(frozen);
        }
    }

    private static void clearBlockedField(ForgeRegistry<?> reg) {
        try {
            java.lang.reflect.Field f = ForgeRegistry.class.getDeclaredField("blocked");
            f.setAccessible(true);
            Object val = f.get(reg);
            if (val instanceof java.util.Collection<?> col) {
                col.clear();
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static <V> void unlockAll(ForgeRegistry<V> active, ForgeRegistry<V> frozen, Registry<V> builtIn) {
        if (active != null) {
            active.unfreeze();
        }
        if (frozen != null) {
            frozen.unfreeze();
        }
        if (builtIn instanceof MappedRegistry<V> mr) {
            mr.unfreeze(); // снимает NamespacedWrapper.frozen
            MappedRegistryAccessor<V> acc = (MappedRegistryAccessor<V>) mr;
            acc.scriptcraft$setFrozen(false); // снимает MappedRegistry.frozen
            if (acc.scriptcraft$getUnregisteredIntrusiveHolders() == null) {
                acc.scriptcraft$setUnregisteredIntrusiveHolders(new IdentityHashMap<>());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <V> void lockAll(ForgeRegistry<V> active, ForgeRegistry<V> frozen, Registry<V> builtIn) {
        try {
            if (builtIn instanceof MappedRegistry<V> mr) {
                MappedRegistryAccessor<V> acc = (MappedRegistryAccessor<V>) mr;
                acc.scriptcraft$setUnregisteredIntrusiveHolders(null);
                acc.scriptcraft$setFrozen(true);
            }
        } catch (Throwable ignored) {
        }
        if (active != null) {
            active.freeze();
        }
        if (frozen != null) {
            frozen.freeze();
        }
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    private static void bindIntrusiveBlockHolder(ResourceLocation rl, Block block) {
        Holder.Reference<Block> holder = block.builtInRegistryHolder();
        if (holder != null && !holder.isBound()) {
            HolderReferenceAccessor<Block> hAcc = (HolderReferenceAccessor<Block>) holder;
            hAcc.scriptcraft$bindKey(ResourceKey.create(ForgeRegistries.Keys.BLOCKS, rl));
            hAcc.scriptcraft$bindValue(block);
        }
        if (BuiltInRegistries.BLOCK instanceof MappedRegistry<Block> mr) {
            Map<Block, Holder.Reference<Block>> map = ((MappedRegistryAccessor<Block>) mr).scriptcraft$getUnregisteredIntrusiveHolders();
            if (map != null) {
                map.remove(block);
            }
        }
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    private static void bindIntrusiveItemHolder(ResourceLocation rl, Item item) {
        Holder.Reference<Item> holder = item.builtInRegistryHolder();
        if (holder != null && !holder.isBound()) {
            HolderReferenceAccessor<Item> hAcc = (HolderReferenceAccessor<Item>) holder;
            hAcc.scriptcraft$bindKey(ResourceKey.create(ForgeRegistries.Keys.ITEMS, rl));
            hAcc.scriptcraft$bindValue(item);
        }
        if (BuiltInRegistries.ITEM instanceof MappedRegistry<Item> mr) {
            Map<Item, Holder.Reference<Item>> map = ((MappedRegistryAccessor<Item>) mr).scriptcraft$getUnregisteredIntrusiveHolders();
            if (map != null) {
                map.remove(item);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <V> int addUnlocked(ForgeRegistry<V> reg, int preferredId, ResourceLocation rl, V value) {
        ForgeRegistryAccessor<V> acc = (ForgeRegistryAccessor<V>) reg;
        return acc.invokeAdd(preferredId, rl, value);
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
        if (acc.getOverrides() != null) {
            acc.getOverrides().removeAll(rl);
        }
        acc.getDelegatesByName().remove(rl);
        acc.getDelegatesByValue().remove(val);
        acc.getOwners().inverse().remove(val);
    }

    private static boolean isClientInjectableNamespace(String ns) {
        if (ns == null || ns.isEmpty()) return false;
        String low = ns.toLowerCase(Locale.ROOT);
        return !low.equals("minecraft") && !low.equals("forge") && !low.equals("neoforge") && !low.equals("c");
    }
}
