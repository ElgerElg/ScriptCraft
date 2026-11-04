package com.scriptcraft.registry;

import com.scriptcraft.ScriptCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Все DeferredRegister мода.
 *
 * Почему пул, а не "по записи на каждый define":
 * реестры блоков/предметов Forge замораживаются после загрузки мода, а при входе на сервер Forge сверяет
 * реестры клиента и сервера (handshake). Раньше блоки регистрировались из скриптов НА ДИСКЕ каждой стороны
 * при запуске игры — новый define на сервере не мог появиться ни на сервере (реестр заморожен), ни на клиенте
 * (у клиента своих скриптов с этим define нет) без перезапуска обоих.
 *
 * Теперь при запуске на ОБЕИХ сторонах регистрируется одинаковый фиксированный пул технических записей:
 * scriptcraft:dyn_block_0..N-1 (блок + предмет-блок) и scriptcraft:dyn_item_0..M-1. Скрипты ничего не
 * регистрируют — сервер во время работы "привязывает" define к свободному слоту ({@link BindingStore}),
 * а свойства, имя, модель, текстура, лут и теги берутся из привязки ({@link Bindings}).
 *
 * Гибрид (0.1.9): define, которые сторона знает уже при запуске, регистрируются НАСТОЯЩИМИ записями со своим
 * id ({@link #registerReal}): сервер — из своих скриптов, клиент — из своих скриптов и кэша define серверов
 * ({@link com.scriptcraft.resources.DefineCache}). Пул остаётся для define, добавленных после запуска.
 * Forge при входе берёт набор записей СЕРВЕРА: лишние записи клиента на это время просто отключаются, а вот
 * если у клиента нет записи сервера — Forge не пустит ("missing registry entries"); клиент при этом запоминает
 * недостающие id в кэш, и после перезапуска клиента вход работает.
 *
 * Размер пула — константа, а не настройка конфига: конфиг Forge читается уже ПОСЛЕ регистрации, а число
 * записей обязано совпадать у клиента и сервера (иначе Forge не пустит на сервер).
 */
public final class ScriptRegistries {
    /** Слотов для блоков (каждый — блок + его предмет). Менять только вместе с версией мода: сломает миры. */
    public static final int BLOCK_SLOTS = 64;
    /** Слотов для простых предметов. */
    public static final int ITEM_SLOTS = 64;

    public static final String BLOCK_PREFIX = "dyn_block_";
    public static final String ITEM_PREFIX = "dyn_item_";

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ScriptCraft.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ScriptCraft.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ScriptCraft.MODID);

    private static final List<RegistryObject<Block>> POOL_BLOCKS = new ArrayList<>();
    private static final List<RegistryObject<Item>> POOL_BLOCK_ITEMS = new ArrayList<>();
    private static final List<RegistryObject<Item>> POOL_ITEMS = new ArrayList<>();

    static {
        for (int i = 0; i < BLOCK_SLOTS; i++) {
            final int slot = i;
            RegistryObject<Block> block = BLOCKS.register(BLOCK_PREFIX + i, () -> new PoolBlock(slot));
            POOL_BLOCKS.add(block);
            POOL_BLOCK_ITEMS.add(ITEMS.register(BLOCK_PREFIX + i, () -> new ScriptBlockItem((PoolBlock) block.get())));
        }
        for (int i = 0; i < ITEM_SLOTS; i++) {
            final int slot = i;
            POOL_ITEMS.add(ITEMS.register(ITEM_PREFIX + i, () -> new PoolItem(slot)));
        }
    }

    /**
     * Вкладка творческого режима: только привязанные сейчас блоки/предметы. Регистрируется всегда (число
     * вкладок тоже сверяется при входе), но пустую вкладку игра сама не показывает (shouldDisplay = есть предметы).
     * Содержимое пересобирается на клиенте, когда приходит новая таблица привязок.
     */
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("scripted", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.scriptcraft"))
            .icon(() -> new ItemStack(Items.WRITABLE_BOOK))
            .displayItems((params, output) -> {
                Bindings.Snapshot b = Bindings.current();
                b.realBlocks().keySet().forEach(id -> {
                    Item item = realBlockItem(id);
                    if (item != null) {
                        output.accept(item);
                    }
                });
                b.blockSlots().values().forEach(slot -> output.accept(blockItem(slot)));
                b.realItems().keySet().forEach(id -> {
                    Item item = realItem(id);
                    if (item != null) {
                        output.accept(item);
                    }
                });
                b.itemSlots().values().forEach(slot -> output.accept(item(slot)));
            })
            .build());

    private ScriptRegistries() {
    }

    public static Block block(int slot) {
        return POOL_BLOCKS.get(slot).get();
    }

    public static Item blockItem(int slot) {
        return POOL_BLOCK_ITEMS.get(slot).get();
    }

    public static Item item(int slot) {
        return POOL_ITEMS.get(slot).get();
    }

    /** Технический id слота блока: scriptcraft:dyn_block_3. */
    public static ResourceLocation blockSlotId(int slot) {
        return new ResourceLocation(ScriptCraft.MODID, BLOCK_PREFIX + slot);
    }

    /** Технический id слота предмета: scriptcraft:dyn_item_3. */
    public static ResourceLocation itemSlotId(int slot) {
        return new ResourceLocation(ScriptCraft.MODID, ITEM_PREFIX + slot);
    }

    /** "dyn_block_3" -> 3 (для префикса prefix и размера size), иначе -1. */
    public static int parseSlot(String path, String prefix, int size) {
        if (!path.startsWith(prefix)) {
            return -1;
        }
        try {
            int slot = Integer.parseInt(path.substring(prefix.length()));
            return slot >= 0 && slot < size ? slot : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ настоящие id

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    /** namespace -> DeferredRegister (Forge 47 разрешает регистрировать чужие namespace своим DeferredRegister). */
    private static final Map<String, DeferredRegister<Block>> REAL_BLOCK_REGISTERS = new LinkedHashMap<>();
    private static final Map<String, DeferredRegister<Item>> REAL_ITEM_REGISTERS = new LinkedHashMap<>();
    private static final Map<String, RegistryObject<Block>> REAL_BLOCKS = new LinkedHashMap<>();
    private static final Map<String, RegistryObject<Item>> REAL_BLOCK_ITEMS = new LinkedHashMap<>();
    private static final Map<String, RegistryObject<Item>> REAL_ITEMS = new LinkedHashMap<>();
    private static boolean realRegistered = false;

    /**
     * Создаёт настоящие записи для id, известных при запуске. Вызывать один раз из конструктора мода, ДО
     * {@link #register}. Если id есть и среди блоков, и среди предметов — побеждает блок.
     */
    public static synchronized void registerReal(Set<String> blockIds, Set<String> itemIds) {
        if (realRegistered) {
            return;
        }
        realRegistered = true;
        for (String id : blockIds) {
            if (!validRealId(id)) {
                continue;
            }
            ResourceLocation rl = new ResourceLocation(id);
            RegistryObject<Block> block = REAL_BLOCK_REGISTERS
                    .computeIfAbsent(rl.getNamespace(), ns -> DeferredRegister.create(ForgeRegistries.BLOCKS, ns))
                    .register(rl.getPath(), () -> new RealBlock(id));
            REAL_BLOCKS.put(id, block);
            REAL_BLOCK_ITEMS.put(id, realItems(rl.getNamespace())
                    .register(rl.getPath(), () -> new ScriptBlockItem((ScriptBlock) block.get())));
        }
        for (String id : itemIds) {
            if (!validRealId(id) || REAL_BLOCKS.containsKey(id)) {
                continue;
            }
            ResourceLocation rl = new ResourceLocation(id);
            REAL_ITEMS.put(id, realItems(rl.getNamespace()).register(rl.getPath(), () -> new RealItem(id)));
        }
        ScriptCraft.LOGGER.info("[ScriptCraft] Настоящие id при запуске: блоков {}, предметов {}", REAL_BLOCKS.size(), REAL_ITEMS.size());
    }

    private static DeferredRegister<Item> realItems(String namespace) {
        return REAL_ITEM_REGISTERS.computeIfAbsent(namespace, ns -> DeferredRegister.create(ForgeRegistries.ITEMS, ns));
    }

    /** Не регистрируем мусор и id, совпадающие со слотами пула или зарезервированными namespace. */
    private static boolean validRealId(String id) {
        if (!ID.matcher(id).matches()) {
            return false;
        }
        String ns = id.substring(0, id.indexOf(':'));
        if (ns.equals("minecraft") || ns.equals("forge") || ns.equals("neoforge") || ns.equals("c")) {
            return false;
        }
        if (ns.equals(ScriptCraft.MODID)) {
            String path = id.substring(id.indexOf(':') + 1);
            if (path.startsWith(BLOCK_PREFIX) || path.startsWith(ITEM_PREFIX) || path.equals("dyn_unbound")) {
                ScriptCraft.LOGGER.warn("[ScriptCraft] id {} совпадает со слотами пула — настоящая запись не создаётся", id);
                return false;
            }
        }
        return true;
    }

    /** Зарегистрирован ли при запуске настоящий блок с этим id. */
    public static boolean isRealBlock(String id) {
        return REAL_BLOCKS.containsKey(id);
    }

    public static boolean isRealItem(String id) {
        return REAL_ITEMS.containsKey(id);
    }

    public static Set<String> realBlockIds() {
        return Collections.unmodifiableSet(REAL_BLOCKS.keySet());
    }

    public static Set<String> realItemIds() {
        return Collections.unmodifiableSet(REAL_ITEMS.keySet());
    }

    @org.jetbrains.annotations.Nullable
    public static Block realBlock(String id) {
        RegistryObject<Block> o = REAL_BLOCKS.get(id);
        return o == null || !o.isPresent() ? null : o.get();
    }

    @org.jetbrains.annotations.Nullable
    public static Item realBlockItem(String id) {
        RegistryObject<Item> o = REAL_BLOCK_ITEMS.get(id);
        return o == null || !o.isPresent() ? null : o.get();
    }

    @org.jetbrains.annotations.Nullable
    public static Item realItem(String id) {
        RegistryObject<Item> o = REAL_ITEMS.get(id);
        return o == null || !o.isPresent() ? null : o.get();
    }

    /** Подключает DeferredRegister к шине событий мода (блоки Forge и так регистрирует раньше предметов). */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        REAL_BLOCK_REGISTERS.values().forEach(dr -> dr.register(modBus));
        REAL_ITEM_REGISTERS.values().forEach(dr -> dr.register(modBus));
        TABS.register(modBus);
    }

}
