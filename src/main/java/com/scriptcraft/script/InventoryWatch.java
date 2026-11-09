package com.scriptcraft.script;

import com.scriptcraft.script.runtime.ExecutionContext;
import com.scriptcraft.script.runtime.ScriptEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Считает предметы в инвентаре (основной, броня, левая рука — без курсора и эндер-сундука).
 * Рост суммы по id — событие receive. Первый снимок при входе молчит, чтобы вещи с диска не считались добычей.
 */
public final class InventoryWatch {
    private static final Map<UUID, Map<String, Integer>> SNAP = new HashMap<>();

    private InventoryWatch() {
    }

    /** Снимок до скриптов входа: то, что уже лежит у игрока, событие не поднимает. */
    public static void baseline(ServerPlayer player) {
        SNAP.put(player.getUUID(), counts(player));
    }

    public static void forget(ServerPlayer player) {
        SNAP.remove(player.getUUID());
    }

    public static void clear() {
        SNAP.clear();
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Map<String, Integer> now = counts(player);
            Map<String, Integer> prev = SNAP.get(player.getUUID());
            if (prev == null) {
                SNAP.put(player.getUUID(), now);
                continue;
            }
            for (Map.Entry<String, Integer> entry : now.entrySet()) {
                int delta = entry.getValue() - prev.getOrDefault(entry.getKey(), 0);
                if (delta <= 0) {
                    continue;
                }
                ExecutionContext ctx = ScriptLoader.fireEvent(ScriptEvent.RECEIVE, player, Map.of(
                        "item", entry.getKey(),
                        "amount", Integer.toString(delta)));
                if (ctx != null && ctx.isCancelled()) {
                    remove(player, entry.getKey(), delta);
                }
            }
            // После обработчиков: и отмена, и предметы, которые скрипт выдал сам, уже в снимке.
            // Иначе give внутри on receive крутился бы каждый тик.
            SNAP.put(player.getUUID(), counts(player));
        }
    }

    private static Map<String, Integer> counts(ServerPlayer player) {
        Map<String, Integer> totals = new HashMap<>();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id == null) {
                continue;
            }
            totals.merge(id.toString(), stack.getCount(), Integer::sum);
        }
        return totals;
    }

    private static void remove(ServerPlayer player, String id, int amount) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return;
        }
        Item item = ForgeRegistries.ITEMS.getValue(key);
        if (item == null || item == Items.AIR) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && amount > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !stack.is(item)) {
                continue;
            }
            int take = Math.min(amount, stack.getCount());
            stack.shrink(take);
            amount -= take;
        }
        player.containerMenu.broadcastChanges();
    }
}
