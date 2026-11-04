package com.scriptcraft.registry;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.lang.Messages;
import com.scriptcraft.network.BindingsPacket;
import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.script.engine.ScriptProgram;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Связка привязок с сервером: старт мира, /sc reload, вход игрока, старые миры.
 * После каждого изменения define сервер заново раздаёт слоты, рассылает таблицу всем игрокам
 * (клиенты подгружают модели одной перезагрузкой ресурсов) и, если поменялись id в data-паке,
 * перезагружает данные (теги, лут, рецепты) — как ванильная /reload.
 */
public final class BindingSync {
    private BindingSync() {
    }

    /** Старт сервера: мир уже загружен — привязываем define из скриптов к слотам этого мира. */
    public static void onServerStarting(MinecraftServer server, ScriptProgram program) {
        apply(server, program, null);
    }

    /** После /sc reload|enable|disable. source — кому писать о новых блоках и нехватке слотов (или null). */
    public static void apply(MinecraftServer server, ScriptProgram program, @Nullable CommandSourceStack source) {
        BindingStore.Result r = BindingStore.bind(server, program);
        if (r.changed()) {
            BindingsPacket packet = new BindingsPacket(r.snapshot());
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ModNetwork.sendTo(p, packet);
            }
        }
        if (r.dataChanged()) {
            // id в тегах/луте/рецептах перезаписываются при чтении data-пака — перечитываем его.
            ScriptCraft.LOGGER.info("[ScriptCraft] Привязки изменились — перезагружаю data-паки");
            server.reloadResources(server.getPackRepository().getSelectedIds()).exceptionally(e -> {
                ScriptCraft.LOGGER.warn("[ScriptCraft] Перезагрузка data-паков не удалась: {}", e.getMessage());
                return null;
            });
        }
        r.added().forEach((id, poolId) -> {
            ScriptCraft.LOGGER.info("[ScriptCraft] {} -> {}", id, poolId);
            if (source != null) {
                source.sendSuccess(() -> Messages.component("bindings.added", id, poolId), false);
            }
        });
        for (String id : r.full()) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Нет свободного слота для {}", id);
            if (source != null) {
                source.sendFailure(Messages.component("bindings.pool_full", id,
                        ScriptRegistries.BLOCK_SLOTS, ScriptRegistries.ITEM_SLOTS));
            }
        }
        if (r.dataChanged() && source != null) {
            source.sendSuccess(() -> Messages.component("bindings.data_reload"), false);
        }
    }

    /** Вход игрока: таблица привязок (после манифеста ресурсов — модели скачиваются параллельно). */
    public static void onLogin(ServerPlayer player) {
        ModNetwork.sendTo(player, new BindingsPacket(Bindings.current()));
    }

    /**
     * Мир из версии 0.1.6 и раньше хранит блоки под id из скриптов (example:ruby_block), которых теперь нет
     * в реестре. Forge спрашивает, что с ними делать, — переназначаем на слот-подсказку из scriptcraft/bindings.json.
     * Если подсказки нет, Forge предупредит, и такие блоки исчезнут (как при удалении мода).
     */
    public static void onMissingMappings(MissingMappingsEvent event) {
        for (MissingMappingsEvent.Mapping<Block> m : event.getAllMappings(ForgeRegistries.Keys.BLOCKS)) {
            Integer slot = BindingStore.hintBlockSlot(m.getKey().toString());
            if (slot != null && slot < ScriptRegistries.BLOCK_SLOTS) {
                m.remap(ScriptRegistries.block(slot));
            }
        }
        for (MissingMappingsEvent.Mapping<Item> m : event.getAllMappings(ForgeRegistries.Keys.ITEMS)) {
            String id = m.getKey().toString();
            Integer slot = BindingStore.hintBlockSlot(id);
            if (slot != null && slot < ScriptRegistries.BLOCK_SLOTS) {
                m.remap(ScriptRegistries.blockItem(slot));
                continue;
            }
            slot = BindingStore.hintItemSlot(id);
            if (slot != null && slot < ScriptRegistries.ITEM_SLOTS) {
                m.remap(ScriptRegistries.item(slot));
            }
        }
    }
}
