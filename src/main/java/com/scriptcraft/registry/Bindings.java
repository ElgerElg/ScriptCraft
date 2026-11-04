package com.scriptcraft.registry;

import com.scriptcraft.script.parser.BlockDefinition;
import com.scriptcraft.script.parser.ItemDefinition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Текущая таблица define из скриптов: настоящие id (зарегистрированы при запуске, см.
 * {@link ScriptRegistries#registerReal}) и временные привязки "id из скрипта -> слот пула" вместе с описаниями.
 * Сервер выставляет её при старте мира и после /sc reload|enable|disable, клиент — из BindingsPacket.
 * Снимок неизменяемый и заменяется целиком (volatile), поэтому читать его можно из любого потока.
 */
public final class Bindings {
    private static volatile Snapshot current = Snapshot.EMPTY;

    private Bindings() {
    }

    public static Snapshot current() {
        return current;
    }

    public static void set(Snapshot snapshot) {
        current = snapshot;
    }

    /** Неизменяемый снимок привязок. */
    public static final class Snapshot {
        public static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of());
        /** Защита от мусорных пакетов. */
        private static final int MAX_TEXT = 256;
        private static final int MAX_REAL = 8192;

        private final BlockDefinition[] blocks = new BlockDefinition[ScriptRegistries.BLOCK_SLOTS];
        private final ItemDefinition[] items = new ItemDefinition[ScriptRegistries.ITEM_SLOTS];
        /** id -> слот, в порядке слотов. */
        private final Map<String, Integer> blockSlots;
        private final Map<String, Integer> itemSlots;
        /** id из скрипта -> технический id ("example:ruby_block" -> "scriptcraft:dyn_block_3"). */
        private final Map<String, String> poolIds;
        /** Настоящие id -> описание, в порядке скриптов. */
        private final Map<String, BlockDefinition> realBlocks;
        private final Map<String, ItemDefinition> realItems;

        public Snapshot(Map<Integer, BlockDefinition> blockDefs, Map<Integer, ItemDefinition> itemDefs) {
            this(blockDefs, itemDefs, Map.of(), Map.of());
        }

        public Snapshot(Map<Integer, BlockDefinition> blockDefs, Map<Integer, ItemDefinition> itemDefs,
                        Map<String, BlockDefinition> realBlockDefs, Map<String, ItemDefinition> realItemDefs) {
            realBlocks = Collections.unmodifiableMap(new LinkedHashMap<>(realBlockDefs));
            realItems = Collections.unmodifiableMap(new LinkedHashMap<>(realItemDefs));
            Map<String, Integer> b = new LinkedHashMap<>();
            new TreeMap<>(blockDefs).forEach((slot, def) -> {
                if (slot >= 0 && slot < blocks.length) {
                    blocks[slot] = def;
                    b.put(def.id(), slot);
                }
            });
            Map<String, Integer> i = new LinkedHashMap<>();
            new TreeMap<>(itemDefs).forEach((slot, def) -> {
                if (slot >= 0 && slot < items.length) {
                    items[slot] = def;
                    i.put(def.id(), slot);
                }
            });
            blockSlots = Collections.unmodifiableMap(b);
            itemSlots = Collections.unmodifiableMap(i);
            Map<String, String> ids = new LinkedHashMap<>();
            // Строки собираются из констант — без загрузки ScriptRegistries (и реестров Forge).
            b.forEach((id, slot) -> ids.put(id, com.scriptcraft.ScriptCraft.MODID + ":" + ScriptRegistries.BLOCK_PREFIX + slot));
            i.forEach((id, slot) -> ids.putIfAbsent(id, com.scriptcraft.ScriptCraft.MODID + ":" + ScriptRegistries.ITEM_PREFIX + slot));
            poolIds = Collections.unmodifiableMap(ids);
        }

        @Nullable
        public BlockDefinition block(int slot) {
            return slot >= 0 && slot < blocks.length ? blocks[slot] : null;
        }

        @Nullable
        public ItemDefinition item(int slot) {
            return slot >= 0 && slot < items.length ? items[slot] : null;
        }

        public Map<String, Integer> blockSlots() {
            return blockSlots;
        }

        public Map<String, Integer> itemSlots() {
            return itemSlots;
        }

        /** Описание настоящего блока (id из скрипта, зарегистрированный при запуске) или null. */
        @Nullable
        public BlockDefinition realBlock(String id) {
            return realBlocks.get(id);
        }

        @Nullable
        public ItemDefinition realItem(String id) {
            return realItems.get(id);
        }

        public Map<String, BlockDefinition> realBlocks() {
            return realBlocks;
        }

        public Map<String, ItemDefinition> realItems() {
            return realItems;
        }

        /** Описание блока по id из скрипта — настоящего или во временном слоте. */
        @Nullable
        public BlockDefinition blockById(String id) {
            BlockDefinition d = realBlocks.get(id);
            if (d != null) {
                return d;
            }
            Integer slot = blockSlots.get(id);
            return slot == null ? null : blocks[slot];
        }

        /** Все id из скриптов: сначала настоящие, потом временные (в слотах). */
        public List<String> allIds() {
            List<String> all = new ArrayList<>(realBlocks.keySet());
            all.addAll(realItems.keySet());
            all.addAll(blockSlots.keySet());
            all.addAll(itemSlots.keySet());
            return all;
        }

        public boolean isEmpty() {
            return blockSlots.isEmpty() && itemSlots.isEmpty() && realBlocks.isEmpty() && realItems.isEmpty();
        }

        /** Предмет по полному id из скрипта: настоящий (example:ruby_block) или предмет слота, иначе null. */
        @Nullable
        public Item resolveItem(String fullId) {
            if (realBlocks.containsKey(fullId)) {
                Item real = ScriptRegistries.realBlockItem(fullId);
                if (real != null) {
                    return real;
                }
            }
            if (realItems.containsKey(fullId)) {
                Item real = ScriptRegistries.realItem(fullId);
                if (real != null) {
                    return real;
                }
            }
            Integer slot = blockSlots.get(fullId);
            if (slot != null) {
                return ScriptRegistries.blockItem(slot);
            }
            slot = itemSlots.get(fullId);
            return slot != null ? ScriptRegistries.item(slot) : null;
        }

        /** Предмет по короткому пути ("ruby" -> первый привязанный *:ruby) или null. */
        @Nullable
        public Item resolveByPath(String path) {
            String suffix = ":" + path;
            for (String id : allIds()) {
                if (id.endsWith(suffix)) {
                    return resolveItem(id);
                }
            }
            return null;
        }

        /**
         * Id в реестре для id из скрипта: сам id, если он настоящий; "scriptcraft:dyn_block_0", если во временном
         * слоте; null — неизвестен.
         */
        @Nullable
        public String registryId(String fullId) {
            if (realBlocks.containsKey(fullId) || realItems.containsKey(fullId)) {
                return fullId;
            }
            return poolIds.get(fullId);
        }

        /** Технический id слота для id из скрипта ("scriptcraft:dyn_block_0") или null (в т.ч. для настоящих id). */
        @Nullable
        public String poolId(String fullId) {
            return poolIds.get(fullId);
        }

        /** Все пары "id из скрипта -> технический id" ТОЛЬКО для временных слотов (для перезаписи команд). */
        public Map<String, String> poolIds() {
            return poolIds;
        }

        /** Совпадает ли раскладка id по слотам (от неё зависят модели на клиенте; свойства — нет). */
        public boolean sameLayout(Snapshot other) {
            return blockSlots.equals(other.blockSlots) && itemSlots.equals(other.itemSlots);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Snapshot s && Arrays.equals(blocks, s.blocks) && Arrays.equals(items, s.items)
                    && realBlocks.equals(s.realBlocks) && realItems.equals(s.realItems);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * Arrays.hashCode(blocks) + Arrays.hashCode(items)) + realBlocks.hashCode() * 7 + realItems.hashCode();
        }

        // ------------------------------------------------------------------ сеть

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(blockSlots.size());
            for (int slot : blockSlots.values()) {
                buf.writeVarInt(slot);
                writeBlock(buf, blocks[slot]);
            }
            buf.writeVarInt(itemSlots.size());
            for (int slot : itemSlots.values()) {
                buf.writeVarInt(slot);
                writeItem(buf, items[slot]);
            }
            buf.writeVarInt(realBlocks.size());
            realBlocks.values().forEach(d -> writeBlock(buf, d));
            buf.writeVarInt(realItems.size());
            realItems.values().forEach(d -> writeItem(buf, d));
        }

        public static Snapshot decode(FriendlyByteBuf buf) {
            Map<Integer, BlockDefinition> b = new LinkedHashMap<>();
            int n = checkCount(buf.readVarInt(), ScriptRegistries.BLOCK_SLOTS);
            for (int k = 0; k < n; k++) {
                int slot = buf.readVarInt();
                b.put(slot, readBlock(buf));
            }
            Map<Integer, ItemDefinition> i = new LinkedHashMap<>();
            n = checkCount(buf.readVarInt(), ScriptRegistries.ITEM_SLOTS);
            for (int k = 0; k < n; k++) {
                int slot = buf.readVarInt();
                i.put(slot, readItem(buf));
            }
            Map<String, BlockDefinition> rb = new LinkedHashMap<>();
            n = checkCount(buf.readVarInt(), MAX_REAL);
            for (int k = 0; k < n; k++) {
                BlockDefinition d = readBlock(buf);
                rb.put(d.id(), d);
            }
            Map<String, ItemDefinition> ri = new LinkedHashMap<>();
            n = checkCount(buf.readVarInt(), MAX_REAL);
            for (int k = 0; k < n; k++) {
                ItemDefinition d = readItem(buf);
                ri.put(d.id(), d);
            }
            return new Snapshot(b, i, rb, ri);
        }

        private static void writeBlock(FriendlyByteBuf buf, BlockDefinition d) {
            buf.writeUtf(d.id());
            writeName(buf, d.displayName());
            buf.writeFloat(d.hardness());
            buf.writeFloat(d.resistance());
            buf.writeUtf(d.sound());
            buf.writeVarInt(d.lightLevel());
            buf.writeBoolean(d.requiresTool());
            buf.writeUtf(d.facing());
        }

        private static BlockDefinition readBlock(FriendlyByteBuf buf) {
            String id = buf.readUtf(MAX_TEXT);
            String name = readName(buf);
            float hardness = buf.readFloat();
            float resistance = buf.readFloat();
            String sound = buf.readUtf(MAX_TEXT);
            int light = buf.readVarInt();
            boolean requiresTool = buf.readBoolean();
            String facing = BlockDefinition.parseFacing(buf.readUtf(MAX_TEXT));
            return new BlockDefinition(id, name, hardness, resistance, sound, light, requiresTool,
                    facing == null ? BlockDefinition.FACING_NONE : facing);
        }

        private static void writeItem(FriendlyByteBuf buf, ItemDefinition d) {
            buf.writeUtf(d.id());
            writeName(buf, d.displayName());
            buf.writeVarInt(d.stackSize());
            buf.writeBoolean(d.fireResistant());
            buf.writeUtf(d.rarity());
        }

        private static ItemDefinition readItem(FriendlyByteBuf buf) {
            String id = buf.readUtf(MAX_TEXT);
            String name = readName(buf);
            int stack = buf.readVarInt();
            boolean fire = buf.readBoolean();
            return new ItemDefinition(id, name, stack, fire, buf.readUtf(MAX_TEXT));
        }

        private static int checkCount(int n, int max) {
            if (n < 0 || n > max) {
                throw new IllegalArgumentException("Неверное число привязок: " + n);
            }
            return n;
        }

        private static void writeName(FriendlyByteBuf buf, @Nullable String name) {
            buf.writeBoolean(name != null);
            if (name != null) {
                buf.writeUtf(name);
            }
        }

        @Nullable
        private static String readName(FriendlyByteBuf buf) {
            return buf.readBoolean() ? buf.readUtf(MAX_TEXT) : null;
        }
    }
}
