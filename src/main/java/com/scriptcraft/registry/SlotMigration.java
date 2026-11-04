package com.scriptcraft.registry;

import com.scriptcraft.ScriptCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Перевод временных слотов на настоящие id (сервер).
 *
 * Сценарий: define добавили через /sc reload — он получил слот (scriptcraft:dyn_block_3), игроки поставили блоки
 * и набрали предметы. После перезапуска сервера define регистрируется настоящим id (example:ruby_block), а в мире
 * лежат слоты. Запись "example:ruby_block -> 3" в данных мира ({@link BindingsData}) остаётся навсегда — это и есть
 * постоянная таблица "слот -> настоящий id": слот 3 больше никому не выдаётся, поэтому чанк, загруженный хоть через
 * год, конвертируется правильно.
 *
 * Что и когда конвертируется (лениво, по мере загрузки):
 * - блоки — при загрузке чанка (ChunkEvent.Load): состояние слота -> состояние настоящего блока с теми же light
 *   и facing (набор свойств у всех блоков скриптов одинаковый);
 * - содержимое контейнеров (сундуки, бочки, шалкеры, печи — всё, что Container) — в конце тика после загрузки чанка;
 * - предметы на земле, в рамках, броня/руки стоек и мобов, вагонетки и лодки с сундуком — при появлении сущности
 *   в мире (EntityJoinLevelEvent);
 * - инвентарь, броня, вторая рука и эндер-сундук игрока — при входе.
 * Стак конвертируется вместе с вложенным NBT (шалкер в инвентаре, мешок): любая строка "id" со слотом заменяется
 * настоящим id; количество и прочие теги сохраняются.
 *
 * Надёжность: конвертация идемпотентна (слот -> настоящий id; настоящий id не трогается), таблица в мире никогда
 * не очищается. Падение сервера посередине безопасно: несохранённые изменения просто повторятся при следующей
 * загрузке чанка. Число конвертаций пишется в лог (не чаще раза в 10 секунд и при остановке).
 */
public final class SlotMigration {
    private static final long LOG_INTERVAL_MS = 10_000;

    /** План на текущий сервер (null — ещё не построен). Неизменяем до остановки сервера. */
    @Nullable
    private static volatile Plan plan;
    /** Чанки, у которых осталось проверить контейнеры (уровень -> позиции). Только главный поток. */
    private static final Map<ServerLevel, Set<Long>> PENDING_CHUNKS = new IdentityHashMap<>();

    private static long blocks;
    private static long stacks;
    private static long lastLog;

    private SlotMigration() {
    }

    /** Что на что менять. itemIds — строковые id для вложенного NBT. */
    private record Plan(Map<Block, Block> blocks, Map<Item, Item> items, Map<String, String> itemIds) {
        boolean isEmpty() {
            return blocks.isEmpty() && items.isEmpty();
        }
    }

    private static Plan plan(MinecraftServer server) {
        Plan p = plan;
        if (p == null) {
            p = build(BindingStore.data(server));
            plan = p;
            if (!p.isEmpty()) {
                ScriptCraft.LOGGER.info("[ScriptCraft] Перевод временных слотов на настоящие id: блоков {}, предметов {}",
                        p.blocks().size(), p.items().size());
            }
        }
        return p;
    }

    /** Строит план из записей мира: слоты, чей id (или "id#слот") теперь зарегистрирован настоящим. */
    private static Plan build(BindingsData data) {
        Map<Block, Block> b = new IdentityHashMap<>();
        Map<Item, Item> i = new IdentityHashMap<>();
        synchronized (BindingStore.class) {
            for (Map.Entry<String, Integer> e : data.blocks.entrySet()) {
                int slot = e.getValue();
                if (slot < 0 || slot >= ScriptRegistries.BLOCK_SLOTS) {
                    continue;
                }
                String id = baseId(e.getKey());
                Block realBlock = ScriptRegistries.realBlock(id);
                if (realBlock != null) {
                    b.put(ScriptRegistries.block(slot), realBlock);
                    putItem(i, ScriptRegistries.blockItem(slot), ScriptRegistries.realBlockItem(id));
                } else {
                    // define сменил вид (был блоком, стал предметом): блоки в мире не трогаем, предметы — переводим.
                    putItem(i, ScriptRegistries.blockItem(slot), ScriptRegistries.realItem(id));
                }
            }
            for (Map.Entry<String, Integer> e : data.items.entrySet()) {
                int slot = e.getValue();
                if (slot < 0 || slot >= ScriptRegistries.ITEM_SLOTS) {
                    continue;
                }
                String id = baseId(e.getKey());
                Item real = ScriptRegistries.realItem(id);
                putItem(i, ScriptRegistries.item(slot), real != null ? real : ScriptRegistries.realBlockItem(id));
            }
        }
        Map<String, String> ids = new HashMap<>();
        i.forEach((from, to) -> {
            var fromId = ForgeRegistries.ITEMS.getKey(from);
            var toId = ForgeRegistries.ITEMS.getKey(to);
            if (fromId != null && toId != null) {
                ids.put(fromId.toString(), toId.toString());
            }
        });
        return new Plan(b, i, ids);
    }

    private static void putItem(Map<Item, Item> map, Item from, @Nullable Item to) {
        if (to != null && from != to) {
            map.put(from, to);
        }
    }

    /** "example:ruby#3" (отставная запись) -> "example:ruby". */
    private static String baseId(String key) {
        int hash = key.indexOf('#');
        return hash < 0 ? key : key.substring(0, hash);
    }

    // ------------------------------------------------------------------ блоки

    /** Чанк загружен (главный поток сервера): блоки — сразу, контейнеры — в конце тика. */
    public static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        MinecraftServer server = level.getServer();
        if (!server.isSameThread()) {
            ChunkPos pos = chunk.getPos();
            server.execute(() -> {
                LevelChunk now = level.getChunkSource().getChunkNow(pos.x, pos.z);
                if (now != null) {
                    onChunkLoad(level, now);
                }
            });
            return;
        }
        Plan p = plan(server);
        if (p.isEmpty()) {
            return;
        }
        int converted = 0;
        for (LevelChunkSection section : chunk.getSections()) {
            if (section.hasOnlyAir() || !section.maybeHas(state -> p.blocks().containsKey(state.getBlock()))) {
                continue;
            }
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        Block to = p.blocks().get(state.getBlock());
                        if (to != null) {
                            section.setBlockState(x, y, z, convert(state, to), false);
                            converted++;
                        }
                    }
                }
            }
        }
        if (converted > 0) {
            chunk.setUnsaved(true);
            blocks += converted;
        }
        if (!chunk.getBlockEntities().isEmpty()) {
            PENDING_CHUNKS.computeIfAbsent(level, l -> new LinkedHashSet<>()).add(chunk.getPos().toLong());
        }
    }

    /** То же состояние у настоящего блока: светимость и поворот переносятся. */
    private static BlockState convert(BlockState from, Block to) {
        BlockState state = to.defaultBlockState();
        if (from.hasProperty(ScriptBlock.LIGHT) && state.hasProperty(ScriptBlock.LIGHT)) {
            state = state.setValue(ScriptBlock.LIGHT, from.getValue(ScriptBlock.LIGHT));
        }
        if (from.hasProperty(ScriptBlock.FACING) && state.hasProperty(ScriptBlock.FACING)) {
            state = state.setValue(ScriptBlock.FACING, from.getValue(ScriptBlock.FACING));
        }
        return state;
    }

    // ------------------------------------------------------------------ тик

    /** Конец тика сервера: контейнеры в загруженных чанках и запись в лог. */
    public static void tick(MinecraftServer server) {
        if (!PENDING_CHUNKS.isEmpty()) {
            Plan p = plan(server);
            List<Map.Entry<ServerLevel, Set<Long>>> work = new ArrayList<>(PENDING_CHUNKS.entrySet());
            PENDING_CHUNKS.clear();
            if (!p.isEmpty() && !p.items().isEmpty()) {
                for (Map.Entry<ServerLevel, Set<Long>> e : work) {
                    for (long packed : e.getValue()) {
                        ChunkPos pos = new ChunkPos(packed);
                        LevelChunk chunk = e.getKey().getChunkSource().getChunkNow(pos.x, pos.z);
                        if (chunk != null) {
                            migrateBlockEntities(chunk, p);
                        }
                    }
                }
            }
        }
        long now = System.currentTimeMillis();
        if (now - lastLog >= LOG_INTERVAL_MS) {
            flushLog();
            lastLog = now;
        }
    }

    private static void migrateBlockEntities(LevelChunk chunk, Plan p) {
        for (Map.Entry<BlockPos, BlockEntity> e : new ArrayList<>(chunk.getBlockEntities().entrySet())) {
            if (e.getValue() instanceof Container container && migrateContainer(container, p) > 0) {
                e.getValue().setChanged();
            }
        }
    }

    // ------------------------------------------------------------------ сущности и игроки

    /** Сущность появилась в мире (загружена с чанком или создана). */
    public static void onEntityJoin(ServerLevel level, Entity entity) {
        if (entity instanceof Player) {
            return; // игроки — при входе (onPlayerLogin)
        }
        Plan p = plan(level.getServer());
        if (p.items().isEmpty()) {
            return;
        }
        if (entity instanceof ItemEntity item) {
            ItemStack to = convert(item.getItem(), p);
            if (to != null) {
                item.setItem(to);
            }
        } else if (entity instanceof ItemFrame frame) {
            ItemStack to = convert(frame.getItem(), p);
            if (to != null) {
                frame.setItem(to, false);
            }
        }
        if (entity instanceof LivingEntity living) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack to = convert(living.getItemBySlot(slot), p);
                if (to != null) {
                    living.setItemSlot(slot, to);
                }
            }
        }
        if (entity instanceof Container container) {
            migrateContainer(container, p); // вагонетки и лодки с сундуком/воронкой
        }
    }

    /** Вход игрока: инвентарь (с бронёй и второй рукой) и эндер-сундук. */
    public static void onPlayerLogin(ServerPlayer player) {
        Plan p = plan(player.getServer());
        if (p.items().isEmpty()) {
            return;
        }
        int n = migrateContainer(player.getInventory(), p) + migrateContainer(player.getEnderChestInventory(), p);
        if (n > 0) {
            player.inventoryMenu.broadcastChanges();
            ScriptCraft.LOGGER.info("[ScriptCraft] {}: {} стаков переведено на настоящие id", player.getGameProfile().getName(), n);
        }
    }

    // ------------------------------------------------------------------ стаки

    private static int migrateContainer(Container container, Plan p) {
        int n = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack to = convert(container.getItem(i), p);
            if (to != null) {
                container.setItem(i, to);
                n++;
            }
        }
        return n;
    }

    /** Новый стак вместо стака со слотом (в нём самом или во вложенном NBT) или null — менять нечего. */
    @Nullable
    private static ItemStack convert(ItemStack stack, Plan p) {
        if (stack.isEmpty()) {
            return null;
        }
        CompoundTag tag = stack.getTag();
        if (!p.items().containsKey(stack.getItem()) && (tag == null || !mentions(tag, p.itemIds()))) {
            return null;
        }
        // Через полное NBT стака: сохраняются количество, теги и capabilities Forge.
        CompoundTag saved = stack.save(new CompoundTag());
        if (!rewrite(saved, p.itemIds())) {
            return null;
        }
        ItemStack result = ItemStack.of(saved);
        if (result.isEmpty()) {
            return null; // на всякий случай: не теряем предмет, если что-то пошло не так
        }
        stacks++;
        return result;
    }

    /** Есть ли во вложенном NBT строка "id" со слотом. */
    private static boolean mentions(Tag tag, Map<String, String> ids) {
        if (tag instanceof CompoundTag c) {
            for (String key : c.getAllKeys()) {
                Tag t = c.get(key);
                if (key.equals("id") && t instanceof StringTag s && ids.containsKey(s.getAsString())) {
                    return true;
                }
                if ((t instanceof CompoundTag || t instanceof ListTag) && mentions(t, ids)) {
                    return true;
                }
            }
        } else if (tag instanceof ListTag l) {
            for (Tag t : l) {
                if (mentions(t, ids)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Заменяет все "id" со слотами на настоящие id. true — что-то поменялось. */
    private static boolean rewrite(Tag tag, Map<String, String> ids) {
        boolean changed = false;
        if (tag instanceof CompoundTag c) {
            for (String key : new ArrayList<>(c.getAllKeys())) {
                Tag t = c.get(key);
                if (key.equals("id") && t instanceof StringTag s) {
                    String to = ids.get(s.getAsString());
                    if (to != null) {
                        c.putString(key, to);
                        changed = true;
                    }
                } else if (t instanceof CompoundTag || t instanceof ListTag) {
                    changed |= rewrite(t, ids);
                }
            }
        } else if (tag instanceof ListTag l) {
            for (Tag t : l) {
                changed |= rewrite(t, ids);
            }
        }
        return changed;
    }

    // ------------------------------------------------------------------ лог и остановка

    private static void flushLog() {
        if (blocks > 0 || stacks > 0) {
            ScriptCraft.LOGGER.info("[ScriptCraft] Переведено на настоящие id: блоков {}, стаков {}", blocks, stacks);
            blocks = 0;
            stacks = 0;
        }
    }

    /** Остановка сервера: итог в лог, план сбрасывается (следующий мир — свои записи). */
    public static void onServerStopped() {
        flushLog();
        plan = null;
        PENDING_CHUNKS.clear();
        lastLog = 0;
    }
}
