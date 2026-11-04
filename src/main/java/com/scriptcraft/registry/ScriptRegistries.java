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
import java.util.List;

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
            POOL_BLOCK_ITEMS.add(ITEMS.register(BLOCK_PREFIX + i, () -> new PoolBlockItem((PoolBlock) block.get())));
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
                b.blockSlots().values().forEach(slot -> output.accept(blockItem(slot)));
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

    /** Подключает DeferredRegister к шине событий мода (блоки Forge и так регистрирует раньше предметов). */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
