package com.scriptcraft.registry;

import com.scriptcraft.script.parser.BlockDefinition;
import com.scriptcraft.script.parser.ItemDefinition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Текущая таблица привязок "id из скрипта -> слот пула" вместе с описаниями define.
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

        private final BlockDefinition[] blocks = new BlockDefinition[ScriptRegistries.BLOCK_SLOTS];
        private final ItemDefinition[] items = new ItemDefinition[ScriptRegistries.ITEM_SLOTS];
        /** id -> слот, в порядке слотов. */
        private final Map<String, Integer> blockSlots;
        private final Map<String, Integer> itemSlots;
        /** id из скрипта -> технический id ("example:ruby_block" -> "scriptcraft:dyn_block_3"). */
        private final Map<String, String> poolIds;

        public Snapshot(Map<Integer, BlockDefinition> blockDefs, Map<Integer, ItemDefinition> itemDefs) {
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

        public boolean isEmpty() {
            return blockSlots.isEmpty() && itemSlots.isEmpty();
        }

        /** Предмет пула по полному id из скрипта (example:ruby_block -> предмет scriptcraft:dyn_block_N) или null. */
        @Nullable
        public Item resolveItem(String fullId) {
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
            for (String id : blockSlots.keySet()) {
                if (id.endsWith(suffix)) {
                    return resolveItem(id);
                }
            }
            for (String id : itemSlots.keySet()) {
                if (id.endsWith(suffix)) {
                    return resolveItem(id);
                }
            }
            return null;
        }

        /** Технический id для id из скрипта ("scriptcraft:dyn_block_0") или null. */
        @Nullable
        public String poolId(String fullId) {
            return poolIds.get(fullId);
        }

        /** Все пары "id из скрипта -> технический id" (для перезаписи команд и данных). */
        public Map<String, String> poolIds() {
            return poolIds;
        }

        /** Совпадает ли раскладка id по слотам (от неё зависят модели на клиенте; свойства — нет). */
        public boolean sameLayout(Snapshot other) {
            return blockSlots.equals(other.blockSlots) && itemSlots.equals(other.itemSlots);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Snapshot s && Arrays.equals(blocks, s.blocks) && Arrays.equals(items, s.items);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(blocks) + Arrays.hashCode(items);
        }

        // ------------------------------------------------------------------ сеть

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(blockSlots.size());
            for (int slot : blockSlots.values()) {
                BlockDefinition d = blocks[slot];
                buf.writeVarInt(slot);
                buf.writeUtf(d.id());
                writeName(buf, d.displayName());
                buf.writeFloat(d.hardness());
                buf.writeFloat(d.resistance());
                buf.writeUtf(d.sound());
                buf.writeVarInt(d.lightLevel());
                buf.writeBoolean(d.requiresTool());
            }
            buf.writeVarInt(itemSlots.size());
            for (int slot : itemSlots.values()) {
                ItemDefinition d = items[slot];
                buf.writeVarInt(slot);
                buf.writeUtf(d.id());
                writeName(buf, d.displayName());
                buf.writeVarInt(d.stackSize());
                buf.writeBoolean(d.fireResistant());
                buf.writeUtf(d.rarity());
            }
        }

        public static Snapshot decode(FriendlyByteBuf buf) {
            Map<Integer, BlockDefinition> b = new LinkedHashMap<>();
            int n = checkCount(buf.readVarInt(), ScriptRegistries.BLOCK_SLOTS);
            for (int k = 0; k < n; k++) {
                int slot = buf.readVarInt();
                b.put(slot, new BlockDefinition(buf.readUtf(MAX_TEXT), readName(buf), buf.readFloat(), buf.readFloat(),
                        buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readBoolean()));
            }
            Map<Integer, ItemDefinition> i = new LinkedHashMap<>();
            n = checkCount(buf.readVarInt(), ScriptRegistries.ITEM_SLOTS);
            for (int k = 0; k < n; k++) {
                int slot = buf.readVarInt();
                i.put(slot, new ItemDefinition(buf.readUtf(MAX_TEXT), readName(buf), buf.readVarInt(), buf.readBoolean(),
                        buf.readUtf(MAX_TEXT)));
            }
            return new Snapshot(b, i);
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
