package com.scriptcraft.event;

import com.scriptcraft.command.ScriptCommands;
import com.scriptcraft.hud.HudManager;
import com.scriptcraft.registry.BindingStore;
import com.scriptcraft.registry.BindingSync;
import com.scriptcraft.registry.SlotMigration;
import com.scriptcraft.resources.ResourceSync;
import com.scriptcraft.script.ScriptLoader;
import com.scriptcraft.script.runtime.ScriptEvent;
import com.scriptcraft.script.variables.VariableStore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.MissingMappingsEvent;

/**
 * Мост между событиями Forge и событиями скриптов.
 * Регистрируется в конструкторе мода: MinecraftForge.EVENT_BUS.register(ServerEvents.class) — поэтому методы static.
 */
public final class ServerEvents {
    private ServerEvents() {
    }

    /** Регистрация команд. Forge вызывает это при старте сервера и при ванильном /reload. */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ScriptCommands.registerAll(event.getDispatcher());
    }

    /** "on join:" */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Предметы из временных слотов, чьи define теперь настоящие, — на настоящие id (до отправки привязок).
            SlotMigration.onPlayerLogin(player);
            // Сначала манифест ресурсов: клиент начнёт докачку, пока скрипт приветствует игрока.
            ResourceSync.onLogin(player);
            // Таблица привязок define -> слоты пула: клиент сразу знает новые блоки/предметы (без перезапуска).
            BindingSync.onLogin(player);
            // HUD, созданный до выхода игрока, отправляется заново (клиент при отключении его стёр).
            // Если скрипт "on join" создаст тот же HUD ещё раз, повторной отправки не будет — сервер сравнит.
            HudManager.onLogin(player);
            ScriptLoader.fireEvent(ScriptEvent.JOIN, player);
        }
    }

    /** "on quit:" */
    @SubscribeEvent
    public static void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ScriptLoader.fireEvent(ScriptEvent.QUIT, player);
            ResourceSync.onLogout(player);
        }
    }

    // ------------------------------------------------------------------ переменные

    /**
     * Старт сервера (мира): загружаем scriptcraft/variables/variables.json (с резервной копией),
     * временные переменные очищаются; строим индекс ресурсов для клиентов.
     */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        VariableStore.load(event.getServer());
        ResourceSync.onServerStarting();
        // Мир загружен: define из скриптов получают слоты пула (запоминаются в данных мира).
        BindingSync.onServerStarting(event.getServer(), ScriptLoader.getProgram());
    }

    /** Ванильные команды с id из скриптов: /give @s example:ruby_block -> scriptcraft:dyn_block_N. */
    @SubscribeEvent
    public static void onCommand(net.minecraftforge.event.CommandEvent event) {
        BindingSync.onCommand(event);
    }

    /** Мир из старой версии мода: блоки под id скриптов (example:ruby_block) переназначаются на слоты пула. */
    @SubscribeEvent
    public static void onMissingMappings(MissingMappingsEvent event) {
        BindingSync.onMissingMappings(event);
    }

    /** Сервер полностью запущен: "on load:" всех включённых скриптов. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ScriptLoader.onServerStarted(event.getServer());
    }

    /** Остановка сервера: "on unload:" скриптов, затем сохраняем переменные, пока мир ещё доступен. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ScriptLoader.onServerStopping(event.getServer());
        VariableStore.save();
    }

    /** Сервер остановлен: финальное сохранение и очистка памяти (важно для одиночной игры — следующий мир чистый). */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        VariableStore.shutdown();
        ResourceSync.onServerStopped();
        HudManager.onServerStopped();
        SlotMigration.onServerStopped();
        BindingStore.onServerStopped();
    }

    /** Периодическое автосохранение (интервал — variablesSaveIntervalSeconds в конфиге). */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            VariableStore.tick();
            ResourceSync.tick(event.getServer());
            SlotMigration.tick(event.getServer());
        }
    }

    // ------------------------------------------------------------------ перевод слотов на настоящие id

    /** Чанк загружен: блоки временных слотов -> настоящие блоки (если define теперь зарегистрирован настоящим). */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            SlotMigration.onChunkLoad(level, chunk);
        }
    }

    /** Сущность появилась в мире: предметы на земле, в рамках, у стоек и мобов, в вагонетках. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            SlotMigration.onEntityJoin(level, event.getEntity());
        }
    }
}
