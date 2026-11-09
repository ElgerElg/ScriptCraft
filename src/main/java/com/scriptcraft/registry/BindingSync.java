package com.scriptcraft.registry;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.lang.Feedback;
import com.scriptcraft.network.BindingsPacket;
import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.script.engine.ScriptProgram;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.CommandEvent;
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
                Feedback.ok(source, "bindings.added", id, poolId);
            }
        });
        for (String id : r.full()) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Нет свободного слота для {}", id);
            if (source != null) {
                Feedback.fail(source, "bindings.pool_full", id,
                        ScriptRegistries.BLOCK_SLOTS, ScriptRegistries.ITEM_SLOTS);
            }
        }
        if (r.dataChanged() && source != null) {
            Feedback.ok(source, "bindings.data_reload");
        }
    }

    /** Вход игрока: таблица привязок (после манифеста ресурсов — модели скачиваются параллельно). */
    public static void onLogin(ServerPlayer player) {
        ModNetwork.sendTo(player, new BindingsPacket(Bindings.current()));
    }

    /**
     * Любая команда на сервере (чат игрока, консоль, командный блок, "execute console command" из скрипта):
     * id из скриптов подменяются на id слотов, и команда разбирается заново. Так работают ванильные
     * /give @s example:ruby_block, /setblock, /fill, /clear, /item, /execute if block, /summon item {Item:{id:"..."}}.
     * Клиент 1.20.1 отправляет команду, даже если сам подсветил её красным (неизвестный предмет), — разбирает сервер.
     */
    public static void onCommand(CommandEvent event) {
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        String command = parse.getReader().getString();
        String rewritten = IdRewriter.rewriteCommand(command, Bindings.current().poolIds());
        if (rewritten.equals(command)) {
            return; // id скриптов в команде нет
        }
        CommandSourceStack source = parse.getContext().getSource();
        event.setParseResults(source.getServer().getCommands().getDispatcher().parse(rewritten, source));
    }

    /**
     * Forge не нашёл записи реестра.
     *
     * Сервер (загрузка мира):
     * - мир из версии 0.1.6 и раньше хранит блоки под id скриптов, которых нет в реестре, — переназначаем на
     *   слот-подсказку из scriptcraft/bindings.json;
     * - настоящий id исчез (define удалили и перезапустили сервер) — тоже во временный слот (подсказка или новая),
     *   слот закрепляется в мире, и при возвращении define блоки снова переедут на настоящий id.
     * Чужие id (удалённые моды) не трогаем — Forge предупредит, как обычно.
     *
     * Клиент при входе на сервер (синхронизация реестров): здесь переназначать НЕЛЬЗЯ — номера записей задаёт
     * сервер. У клиента нет настоящих id сервера (их не было в кэше при запуске) — запоминаем их в кэш define;
     * Forge покажет экран "не хватает записей" и отключит, а после перезапуска клиента вход пройдёт.
     */
    public static void onMissingMappings(MissingMappingsEvent event) {
        if (isNetworkHandshake()) {
            rememberServerIds(event);
            return;
        }
        for (MissingMappingsEvent.Mapping<Block> m : event.getAllMappings(ForgeRegistries.Keys.BLOCKS)) {
            String id = m.getKey().toString();
            Integer slot = BindingStore.hintBlockSlot(id) != null || BindingStore.wasRealBlock(id)
                    ? BindingStore.reserveForMissing(id, true) : null;
            if (slot != null && slot < ScriptRegistries.BLOCK_SLOTS) {
                m.remap(ScriptRegistries.block(slot));
            }
        }
        for (MissingMappingsEvent.Mapping<Item> m : event.getAllMappings(ForgeRegistries.Keys.ITEMS)) {
            String id = m.getKey().toString();
            if (BindingStore.hintBlockSlot(id) != null || BindingStore.wasRealBlock(id)) {
                Integer slot = BindingStore.reserveForMissing(id, true);
                if (slot != null && slot < ScriptRegistries.BLOCK_SLOTS) {
                    m.remap(ScriptRegistries.blockItem(slot));
                }
                continue;
            }
            if (BindingStore.hintItemSlot(id) != null || BindingStore.wasRealItem(id)) {
                Integer slot = BindingStore.reserveForMissing(id, false);
                if (slot != null && slot < ScriptRegistries.ITEM_SLOTS) {
                    m.remap(ScriptRegistries.item(slot));
                }
            }
        }
    }

    /** Идёт ли синхронизация реестров при входе на сервер (а не загрузка своего мира). */
    private static boolean isNetworkHandshake() {
        return StackWalker.getInstance().walk(frames -> frames.anyMatch(
                f -> f.getClassName().startsWith("net.minecraftforge.network.HandshakeHandler")));
    }

    /** Клиент: недостающие id сервера (не ванилла и не загруженные моды) — в кэш define до перезапуска. */
    private static void rememberServerIds(MissingMappingsEvent event) {
        java.util.List<String> blocks = new java.util.ArrayList<>();
        java.util.List<String> items = new java.util.ArrayList<>();
        for (MissingMappingsEvent.Mapping<Block> m : event.getAllMappings(ForgeRegistries.Keys.BLOCKS)) {
            if (isScriptNamespace(m.getKey().getNamespace())) {
                blocks.add(m.getKey().toString());
            }
        }
        for (MissingMappingsEvent.Mapping<Item> m : event.getAllMappings(ForgeRegistries.Keys.ITEMS)) {
            String id = m.getKey().toString();
            if (isScriptNamespace(m.getKey().getNamespace()) && !blocks.contains(id)) {
                items.add(id);
            }
        }
        if (com.scriptcraft.resources.DefineCache.add(blocks, items)) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] У сервера есть блоки/предметы, которых не было при запуске клиента: {} {}."
                    + " Они сохранены в scriptcraft/cache/defines.json — перезапустите клиент и зайдите снова", blocks, items);
        }
    }

    private static boolean isScriptNamespace(String namespace) {
        return !namespace.equals("minecraft") && !namespace.equals(ScriptCraft.MODID)
                && !net.minecraftforge.fml.ModList.get().isLoaded(namespace);
    }
}
