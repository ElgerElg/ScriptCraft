package com.scriptcraft.registry;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.script.parser.BlockDefinition;
import com.scriptcraft.script.parser.ItemDefinition;
import com.scriptcraft.script.engine.ScriptProgram;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Все DeferredRegister мода.
 *
 * Блоки и предметы берутся из скриптов ("define block ..." / "define item ...").
 * Для каждого пространства имён из скриптов (namespace example / define block example:ruby_block)
 * создаётся свой DeferredRegister — Forge 47.x разрешает регистрировать чужие namespace
 * (пишет только debug-сообщение), поэтому отдельный мод не нужен.
 * Регистрация возможна только во время запуска (до заморозки реестров),
 * поэтому {@link #registerFromScripts} вызывается из конструктора мода и только один раз.
 */
public final class ScriptRegistries {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ScriptCraft.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ScriptCraft.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ScriptCraft.MODID);

    /** namespace -> DeferredRegister блоков/предметов (scriptcraft — общие BLOCKS/ITEMS). */
    private static final Map<String, DeferredRegister<Block>> BLOCK_REGISTERS = new LinkedHashMap<>();
    private static final Map<String, DeferredRegister<Item>> ITEM_REGISTERS = new LinkedHashMap<>();

    static {
        BLOCK_REGISTERS.put(ScriptCraft.MODID, BLOCKS);
        ITEM_REGISTERS.put(ScriptCraft.MODID, ITEMS);
    }

    /** Полный id ("ns:path") -> зарегистрированный блок из скрипта. */
    private static final Map<String, RegistryObject<Block>> SCRIPT_BLOCKS = new LinkedHashMap<>();
    /** Полный id -> зарегистрированный предмет из скрипта (включая предметы-блоки). */
    private static final Map<String, RegistryObject<Item>> SCRIPT_ITEMS = new LinkedHashMap<>();

    /** Вкладка творческого режима со всем, что объявлено в скриптах. */
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("scripted", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.scriptcraft"))
            .icon(() -> SCRIPT_ITEMS.values().stream().findFirst()
                    .map(ro -> new ItemStack(ro.get()))
                    .orElseGet(() -> new ItemStack(Items.WRITABLE_BOOK)))
            .displayItems((params, output) -> SCRIPT_ITEMS.values().forEach(ro -> output.accept(ro.get())))
            .build());

    private static boolean registered = false;

    private ScriptRegistries() {
    }

    /**
     * Создаёт записи DeferredRegister для блоков/предметов из скриптов.
     * Вызывать строго ДО {@link #register(IEventBus)} и только в конструкторе мода.
     */
    public static void registerFromScripts(ScriptProgram program) {
        if (registered) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Повторная регистрация блоков проигнорирована: нужен перезапуск игры.");
            return;
        }
        registered = true;

        for (BlockDefinition def : program.blocks()) {
            if (SCRIPT_ITEMS.containsKey(def.id())) {
                ScriptCraft.LOGGER.warn("[ScriptCraft] id '{}' уже занят, блок пропущен", def.id());
                continue;
            }
            RegistryObject<Block> block = blocks(def.namespace())
                    .register(def.path(), () -> new ScriptBlock(def.toProperties(), def.displayName()));
            SCRIPT_BLOCKS.put(def.id(), block);
            // У каждого блока есть предмет, чтобы его можно было держать в руке и ставить.
            SCRIPT_ITEMS.put(def.id(), items(def.namespace())
                    .register(def.path(), () -> new ScriptBlockItem(block.get(), new Item.Properties())));
            ScriptCraft.LOGGER.info("[ScriptCraft] Блок из скрипта: {}", def.id());
        }

        for (ItemDefinition def : program.items()) {
            if (SCRIPT_ITEMS.containsKey(def.id())) {
                ScriptCraft.LOGGER.warn("[ScriptCraft] id '{}' уже занят, предмет пропущен", def.id());
                continue;
            }
            SCRIPT_ITEMS.put(def.id(), items(def.namespace())
                    .register(def.path(), () -> new ScriptItem(def.toProperties(), def.displayName())));
            ScriptCraft.LOGGER.info("[ScriptCraft] Предмет из скрипта: {}", def.id());
        }
    }

    private static DeferredRegister<Block> blocks(String namespace) {
        return BLOCK_REGISTERS.computeIfAbsent(namespace, ns -> DeferredRegister.create(ForgeRegistries.BLOCKS, ns));
    }

    private static DeferredRegister<Item> items(String namespace) {
        return ITEM_REGISTERS.computeIfAbsent(namespace, ns -> DeferredRegister.create(ForgeRegistries.ITEMS, ns));
    }

    /** Все пространства имён, в которых скрипты что-то зарегистрировали (scriptcraft всегда первый). */
    public static Set<String> namespaces() {
        Set<String> all = new java.util.LinkedHashSet<>(BLOCK_REGISTERS.keySet());
        all.addAll(ITEM_REGISTERS.keySet());
        return Collections.unmodifiableSet(all);
    }

    /** Подключает все DeferredRegister к шине событий мода. */
    public static void register(IEventBus modBus) {
        // Блоки раньше предметов: предмет-блок ссылается на блок (Forge и так регистрирует блоки первыми).
        BLOCK_REGISTERS.values().forEach(dr -> dr.register(modBus));
        ITEM_REGISTERS.values().forEach(dr -> dr.register(modBus));
        TABS.register(modBus);
    }

    /** Полные id блоков, реально зарегистрированных при запуске (нужно, чтобы предупреждать о новых блоках при reload). */
    public static Set<String> registeredBlockIds() {
        return Collections.unmodifiableSet(SCRIPT_BLOCKS.keySet());
    }

    /** id всех зарегистрированных предметов (включая предметы-блоки). */
    public static Set<String> registeredItemIds() {
        return Collections.unmodifiableSet(SCRIPT_ITEMS.keySet());
    }
}
