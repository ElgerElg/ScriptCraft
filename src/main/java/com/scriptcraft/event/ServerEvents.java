package com.scriptcraft.event;

import com.scriptcraft.command.ScriptCommands;
import com.scriptcraft.hud.HudManager;
import com.scriptcraft.registry.BindingStore;
import com.scriptcraft.registry.BindingSync;
import com.scriptcraft.registry.SlotMigration;
import com.scriptcraft.resources.ResourceSync;
import com.scriptcraft.script.InputSync;
import com.scriptcraft.script.InventoryWatch;
import com.scriptcraft.script.ScriptLoader;
import com.scriptcraft.script.runtime.ScriptEvent;
import com.scriptcraft.script.variables.VariableStore;
import com.scriptcraft.script.runtime.ExecutionContext;
import com.scriptcraft.script.runtime.Expression;
import com.scriptcraft.script.runtime.ScoreboardManager;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Мост между событиями Forge и событиями скриптов.
 * Регистрируется в конструкторе мода: MinecraftForge.EVENT_BUS.register(ServerEvents.class) — поэтому методы static.
 */
public final class ServerEvents {
    private static final Map<UUID, Boolean> SNEAK_STATE = new HashMap<>();
    private static final Map<UUID, Boolean> SPRINT_STATE = new HashMap<>();

    private ServerEvents() {
    }

    /** Регистрация команд. Forge вызывает это при старте сервера и при ванильном /reload. */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ScriptCommands.registerAll(event.getDispatcher(), event.getBuildContext());
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
            // До скриптов входа: вещи с диска не считаются получением. give из on join — уже следующий тик.
            InventoryWatch.baseline(player);
            if (player.getServer() != null) {
                InputSync.sync(player.getServer());
            }
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
            InventoryWatch.forget(player);
            ResourceSync.onLogout(player);
            com.scriptcraft.studio.StudioRooms.onQuit(player);
            com.scriptcraft.screen.ScreenManager.onQuit(player);
            com.scriptcraft.script.runtime.BossBarManager.onQuit(player);
            ScoreboardManager.onQuit(player);
            SNEAK_STATE.remove(player.getUUID());
            SPRINT_STATE.remove(player.getUUID());
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
        com.scriptcraft.script.ScriptProfiler.onServerStarting();
        com.scriptcraft.studio.ScriptHistory.cleanup();
        // Мир загружен: define из скриптов получают слоты пула (запоминаются в данных мира).
        BindingSync.onServerStarting(event.getServer(), ScriptLoader.getProgram());
    }

    /** Ванильные команды с id из скриптов: /give @s example:ruby_block -> scriptcraft:dyn_block_N. */
    @SubscribeEvent
    public static void onCommand(net.minecraftforge.event.CommandEvent event) {
        BindingSync.onCommand(event);
        if (event.isCanceled()) {
            return;
        }
        if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player) {
            String raw = event.getParseResults().getReader().getString().strip();
            if (raw.startsWith("/")) {
                raw = raw.substring(1);
            }
            int sp = raw.indexOf(' ');
            String cmd = sp < 0 ? raw : raw.substring(0, sp);
            String args = sp < 0 ? "" : raw.substring(sp + 1).strip();
            run(ScriptEvent.COMMAND, player, event, Map.of("command", cmd, "args", args));
        }
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
        InventoryWatch.clear();
        SlotMigration.onServerStopped();
        BindingStore.onServerStopped();
        com.scriptcraft.studio.StudioRooms.clear();
        com.scriptcraft.script.ScriptProfiler.onServerStopped();
        ScoreboardManager.onServerStopped();
        SNEAK_STATE.clear();
        SPRINT_STATE.clear();
    }

    /** Периодическое автосохранение (интервал — variablesSaveIntervalSeconds в конфиге). */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            VariableStore.tick();
            com.scriptcraft.config.ScriptCraftConfig.tick(event.getServer());
            ResourceSync.tick(event.getServer());
            SlotMigration.tick(event.getServer());
            ScriptLoader.tick(event.getServer());
            InventoryWatch.tick(event.getServer());
            com.scriptcraft.script.ScriptProfiler.tick(event.getServer());
            com.scriptcraft.studio.ScriptHistory.tick();
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
        if (!event.isCanceled() && event.getSource().getEntity() instanceof ServerPlayer killer) {
            Entity victim = event.getEntity();
            String vName = victim instanceof Player p ? p.getGameProfile().getName() : entityId(victim);
            ScriptLoader.fireEvent(ScriptEvent.KILL, killer, Map.of(
                    "victim", vName,
                    "attacker", killer.getGameProfile().getName(),
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
    public static void onRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity target = event.getTarget();
        Map<String, String> ph = new HashMap<>();
        ph.put("clicked_entity", entityId(target));
        ph.put("clicked_entity_name", target.getName().getString());
        if (target instanceof Player targetPlayer) {
            ph.put("clicked_player", targetPlayer.getGameProfile().getName());
        }
        BlockPos pos = target.blockPosition();
        ph.put("x", Integer.toString(pos.getX()));
        ph.put("y", Integer.toString(pos.getY()));
        ph.put("z", Integer.toString(pos.getZ()));
        run(ScriptEvent.RIGHT_CLICK, player, event, ph);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Entity target = event.getTarget();
            String vName = target instanceof Player p ? p.getGameProfile().getName() : entityId(target);
            run(ScriptEvent.ATTACK, player, event, Map.of(
                    "victim", vName,
                    "attacker", player.getGameProfile().getName()));
        }
    }

    @SubscribeEvent
    public static void onShoot(ArrowLooseEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            run(ScriptEvent.SHOOT, player, event, Map.of("projectile", "minecraft:arrow"));
        }
    }

    @SubscribeEvent
    public static void onProjectileHit(ProjectileImpactEvent event) {
        if (!(event.getProjectile().getOwner() instanceof ServerPlayer shooter)) {
            return;
        }
        Map<String, String> ph = new HashMap<>();
        ph.put("projectile", entityId(event.getProjectile()));
        HitResult hit = event.getRayTraceResult();
        if (hit instanceof EntityHitResult ehr) {
            Entity v = ehr.getEntity();
            ph.put("victim", v instanceof Player p ? p.getGameProfile().getName() : entityId(v));
            BlockPos pos = v.blockPosition();
            ph.put("x", Integer.toString(pos.getX()));
            ph.put("y", Integer.toString(pos.getY()));
            ph.put("z", Integer.toString(pos.getZ()));
        } else if (hit instanceof BlockHitResult bhr) {
            BlockPos pos = bhr.getBlockPos();
            BlockState st = event.getProjectile().level().getBlockState(pos);
            ph.putAll(blockPlaceholders(st, pos));
        }
        run(ScriptEvent.PROJECTILE_HIT, shooter, event, ph);
    }

    @SubscribeEvent
    public static void onConsume(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ScriptLoader.fireEvent(ScriptEvent.CONSUME, player, Map.of("item", itemId(event.getItem())));
        }
    }

    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ItemStack stack = event.getCrafting();
            ScriptLoader.fireEvent(ScriptEvent.CRAFT, player, Map.of(
                    "item", itemId(stack),
                    "amount", Integer.toString(stack.getCount())));
        }
    }

    @SubscribeEvent
    public static void onFish(ItemFishedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String item = event.getDrops().isEmpty() ? "minecraft:air" : itemId(event.getDrops().get(0));
            run(ScriptEvent.FISH, player, event, Map.of("item", item));
        }
    }

    private static final Map<UUID, Integer> LAST_JUMP_TICK = new java.util.concurrent.ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            handleJump(player, false);
        }
    }

    public static void handleJump(ServerPlayer player, boolean fromClientPacket) {
        if (player == null || player.getServer() == null) {
            return;
        }
        int tick = player.getServer().getTickCount();
        Integer prev = LAST_JUMP_TICK.put(player.getUUID(), tick);
        if (prev != null && prev == tick) {
            return;
        }
        ExecutionContext ctx = ScriptLoader.fireEvent(ScriptEvent.JUMP, player);
        if (ctx != null && ctx.isCancelled()) {
            net.minecraft.world.phys.Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, Math.min(0.0, v.y), v.z);
            player.hurtMarked = true;
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
        } else if (fromClientPacket) {
            // Клиент заранее погасил прыжок, но условие if в on jump не сработало — разрешаем прыжок с сервера
            net.minecraft.world.phys.Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, 0.42D, v.z);
            player.hurtMarked = true;
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
        }
    }

    @SubscribeEvent
    public static void onItemBreak(PlayerDestroyItemEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ScriptLoader.fireEvent(ScriptEvent.ITEM_BREAK, player, Map.of("item", itemId(event.getOriginal())));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        boolean sneaking = player.isShiftKeyDown();
        Boolean prevSneak = SNEAK_STATE.put(id, sneaking);
        if (prevSneak != null && prevSneak != sneaking) {
            ExecutionContext ctx = ScriptLoader.fireEvent(ScriptEvent.SNEAK, player, Map.of("sneaking", Boolean.toString(sneaking)));
            if (ctx != null && ctx.isCancelled()) {
                player.setShiftKeyDown(prevSneak);
                if (!prevSneak) {
                    player.setPose(net.minecraft.world.entity.Pose.STANDING);
                }
                SNEAK_STATE.put(id, prevSneak);
            }
        }
        boolean sprinting = player.isSprinting();
        Boolean prevSprint = SPRINT_STATE.put(id, sprinting);
        if (prevSprint != null && prevSprint != sprinting) {
            ExecutionContext ctx = ScriptLoader.fireEvent(ScriptEvent.SPRINT, player, Map.of("sprinting", Boolean.toString(sprinting)));
            if (ctx != null && ctx.isCancelled()) {
                player.setSprinting(prevSprint);
                if (!prevSprint) {
                    net.minecraft.world.phys.Vec3 v = player.getDeltaMovement();
                    player.setDeltaMovement(v.x * 0.6, v.y, v.z * 0.6);
                    player.hurtMarked = true;
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
                }
                SPRINT_STATE.put(id, prevSprint);
            }
        }
    }

    private static String entityId(Entity entity) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id == null ? entity.getName().getString() : id.toString();
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
