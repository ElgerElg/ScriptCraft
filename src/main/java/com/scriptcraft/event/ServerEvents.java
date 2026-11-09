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
import com.scriptcraft.script.runtime.ExecutionContext;
import com.scriptcraft.script.runtime.Expression;
import com.scriptcraft.script.runtime.ScriptScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;

import java.util.Map;

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
            if (player.getStats().getValue(Stats.CUSTOM.get(Stats.LEAVE_GAME)) == 0) {
                ScriptLoader.fireEvent(ScriptEvent.FIRST_JOIN, player);
            }
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
        ScriptScheduler.clear(null);
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
            ScriptLoader.tick(event.getServer());
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

    // ------------------------------------------------------------------ события скриптов

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            run(ScriptEvent.DEATH, player, event, Map.of(
                    "attacker", attackerName(event.getSource().getEntity()),
                    "cause", event.getSource().getMsgId()));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ScriptLoader.fireEvent(ScriptEvent.RESPAWN, player);
        }
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        run(ScriptEvent.CHAT, event.getPlayer(), event, Map.of("message", event.getRawText()));
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            run(ScriptEvent.BREAK, player, event, blockPlaceholders(event.getState(), event.getPos()));
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            run(ScriptEvent.PLACE, player, event, blockPlaceholders(event.getPlacedBlock(), event.getPos()));
        }
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        run(ScriptEvent.RIGHT_CLICK, player, event, blockPlaceholders(state, event.getPos()));
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        run(ScriptEvent.LEFT_CLICK, player, event, blockPlaceholders(state, event.getPos()));
    }

    @SubscribeEvent
    public static void onDrop(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            run(ScriptEvent.DROP, player, event, Map.of("item", itemId(event.getEntity().getItem())));
        }
    }

    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            run(ScriptEvent.PICKUP, player, event, Map.of("item", itemId(event.getItem().getItem())));
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            run(ScriptEvent.DAMAGE, player, event, Map.of(
                    "damage", Expression.toText((double) event.getAmount()),
                    "attacker", attackerName(event.getSource().getEntity()),
                    "cause", event.getSource().getMsgId()));
        }
    }

    @SubscribeEvent
    public static void onWorldChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ScriptLoader.fireEvent(ScriptEvent.WORLD_CHANGE, player, Map.of(
                    "from", event.getFrom().location().toString(),
                    "world", event.getTo().location().toString()));
        }
    }

    @SubscribeEvent
    public static void onGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            run(ScriptEvent.GAMEMODE_CHANGE, player, event, Map.of(
                    "from", event.getCurrentGameMode().getName(),
                    "gamemode", event.getNewGameMode().getName()));
        }
    }

    private static void run(ScriptEvent type, ServerPlayer player, Event event, Map<String, String> placeholders) {
        ExecutionContext ctx = ScriptLoader.fireEvent(type, player, placeholders);
        if (ctx != null && ctx.isCancelled() && event.isCancelable()) {
            event.setCanceled(true);
        }
    }

    private static Map<String, String> blockPlaceholders(BlockState state, BlockPos pos) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return Map.of(
                "block", id == null ? "minecraft:air" : id.toString(),
                "x", Integer.toString(pos.getX()),
                "y", Integer.toString(pos.getY()),
                "z", Integer.toString(pos.getZ()));
    }

    private static String itemId(ItemStack stack) {
        if (stack.isEmpty()) {
            return "minecraft:air";
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "minecraft:air" : id.toString();
    }

    private static String attackerName(Entity entity) {
        if (entity == null) {
            return "<none>";
        }
        if (entity instanceof Player player) {
            return player.getGameProfile().getName();
        }
        return entity.getName().getString();
    }
}
