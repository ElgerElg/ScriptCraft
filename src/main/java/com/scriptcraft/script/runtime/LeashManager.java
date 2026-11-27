package com.scriptcraft.script.runtime;

import com.scriptcraft.network.LeashPacket;
import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.script.ScriptLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверный менеджер универсальных поводков ScriptCraft.
 * Позволяет привязывать любых сущностей (включая игроков!) к другим сущностям/игрокам или к блокам
 * с гибкой настройкой длины, упругости, разрыва, двусторонней тяги, цвета, толщины и провисания.
 */
public final class LeashManager {
    public static final int DEFAULT_COLOR = 0xFF8B5A2B;

    public record Config(
            float maxLength,
            float minLength,
            float stiffness,
            float breakDistance,
            boolean bidirectional,
            int color,
            float width,
            float sag,
            boolean showKnot,
            boolean dropLead
    ) {
        public static final Config DEFAULT = new Config(
                6.0f, 0.0f, 1.0f, 0.0f, false, DEFAULT_COLOR, 1.0f, 1.0f, true, false
        );
    }

    public static final class ActiveLeash {
        public final UUID leashedUuid;
        public final boolean toBlock;
        @Nullable
        public final UUID holderUuid;
        @Nullable
        public final BlockPos blockPos;
        public String dimension;
        public boolean hadSolidBlock;

        public float maxLength;
        public float minLength;
        public float stiffness;
        public float breakDistance;
        public boolean bidirectional;
        public int color;
        public float width;
        public float sag;
        public boolean showKnot;
        public boolean dropLead;

        public int lastLeashedNetId = -1;
        public int lastHolderNetId = -1;

        ActiveLeash(UUID leashedUuid, boolean toBlock, @Nullable UUID holderUuid,
                    @Nullable BlockPos blockPos, String dimension, boolean hadSolidBlock, Config cfg) {
            this.leashedUuid = leashedUuid;
            this.toBlock = toBlock;
            this.holderUuid = holderUuid;
            this.blockPos = blockPos;
            this.dimension = dimension;
            this.hadSolidBlock = hadSolidBlock;
            applyConfig(cfg);
        }

        void applyConfig(Config cfg) {
            this.maxLength = Mth.clamp(cfg.maxLength(), 0.25f, 128.0f);
            this.minLength = Mth.clamp(cfg.minLength(), 0.0f, this.maxLength);
            this.stiffness = Mth.clamp(cfg.stiffness(), 0.0f, 5.0f);
            this.breakDistance = cfg.breakDistance() <= 0f ? 0f : Math.max(this.maxLength + 0.5f, cfg.breakDistance());
            this.bidirectional = cfg.bidirectional();
            this.color = cfg.color();
            this.width = Mth.clamp(cfg.width(), 0.2f, 6.0f);
            this.sag = Mth.clamp(cfg.sag(), 0.0f, 4.0f);
            this.showKnot = cfg.showKnot();
            this.dropLead = cfg.dropLead();
        }

        @Nullable
        LeashPacket.Entry toPacketEntry(MinecraftServer server) {
            Entity leashed = findEntity(server, leashedUuid, dimension);
            if (leashed == null || !leashed.isAlive()) {
                return null;
            }
            int lId = leashed.getId();
            int hId = -1;
            if (!toBlock) {
                Entity holder = holderUuid != null ? findEntity(server, holderUuid, leashed.level().dimension().location().toString()) : null;
                if (holder == null || !holder.isAlive()) {
                    return null;
                }
                hId = holder.getId();
            }
            lastLeashedNetId = lId;
            lastHolderNetId = hId;
            return new LeashPacket.Entry(
                    lId, toBlock, hId,
                    blockPos != null ? blockPos : BlockPos.ZERO,
                    leashed.level().dimension().location().toString(),
                    maxLength, minLength, stiffness, breakDistance, bidirectional,
                    color, width, sag, showKnot
            );
        }
    }

    private static final Map<UUID, ActiveLeash> LEASHES = new ConcurrentHashMap<>();

    private LeashManager() {
    }

    public static void attachToEntity(MinecraftServer server, Entity leashed, Entity holder, Config cfg) {
        if (leashed == null || holder == null || leashed == holder) {
            return;
        }
        if (leashed instanceof Mob mob && mob.isLeashed()) {
            mob.dropLeash(true, false);
        }
        String dim = leashed.level().dimension().location().toString();
        ActiveLeash al = new ActiveLeash(leashed.getUUID(), false, holder.getUUID(), null, dim, false, cfg);
        LEASHES.put(leashed.getUUID(), al);
        leashed.level().playSound(null, leashed.getX(), leashed.getY(), leashed.getZ(),
                SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 0.6f, 1.05f);
        broadcastUpsert(server, al);
    }

    public static void attachToBlock(MinecraftServer server, Entity leashed, ServerLevel level, BlockPos pos, Config cfg) {
        if (leashed == null || level == null || pos == null) {
            return;
        }
        if (leashed instanceof Mob mob && mob.isLeashed()) {
            mob.dropLeash(true, false);
        }
        String dim = level.dimension().location().toString();
        boolean solid = !level.getBlockState(pos).isAir();
        ActiveLeash al = new ActiveLeash(leashed.getUUID(), true, null, pos.immutable(), dim, solid, cfg);
        LEASHES.put(leashed.getUUID(), al);
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 0.7f, 1.0f);
        broadcastUpsert(server, al);
    }

    public static boolean updateProperty(MinecraftServer server, Entity leashed, String prop, Object rawVal) {
        if (leashed == null) {
            return false;
        }
        ActiveLeash al = LEASHES.get(leashed.getUUID());
        if (al == null) {
            return false;
        }
        String p = prop.toLowerCase(Locale.ROOT).replace('-', '_');
        switch (p) {
            case "length", "max_length", "distance" -> {
                Double n = Expression.toNumber(rawVal);
                if (n != null) {
                    al.maxLength = Mth.clamp(n.floatValue(), 0.25f, 128.0f);
                    if (al.minLength > al.maxLength) al.minLength = al.maxLength;
                }
            }
            case "min_length" -> {
                Double n = Expression.toNumber(rawVal);
                if (n != null) {
                    al.minLength = Mth.clamp(n.floatValue(), 0.0f, al.maxLength);
                }
            }
            case "strength", "pull", "stiffness", "force" -> {
                Double n = Expression.toNumber(rawVal);
                if (n != null) {
                    al.stiffness = Mth.clamp(n.floatValue(), 0.0f, 5.0f);
                }
            }
            case "break", "break_at", "break_distance" -> {
                String txt = Expression.toText(rawVal).strip().toLowerCase(Locale.ROOT);
                if (txt.equals("false") || txt.equals("none") || txt.equals("unbreakable") || txt.equals("0")) {
                    al.breakDistance = 0f;
                } else if (txt.equals("true") || txt.equals("breakable")) {
                    al.breakDistance = al.maxLength + 4.0f;
                } else {
                    Double n = Expression.toNumber(rawVal);
                    if (n != null) {
                        al.breakDistance = n <= 0 ? 0f : Math.max(al.maxLength + 0.5f, n.floatValue());
                    }
                }
            }
            case "color", "colour" -> al.color = parseHexColor(Expression.toText(rawVal), al.color);
            case "width", "thickness", "size" -> {
                Double n = Expression.toNumber(rawVal);
                if (n != null) {
                    al.width = Mth.clamp(n.floatValue(), 0.2f, 6.0f);
                }
            }
            case "sag", "droop", "slack" -> {
                Double n = Expression.toNumber(rawVal);
                if (n != null) {
                    al.sag = Mth.clamp(n.floatValue(), 0.0f, 4.0f);
                }
            }
            case "two_way", "bidirectional", "both" -> al.bidirectional = parseBool(rawVal, al.bidirectional);
            case "knot" -> al.showKnot = parseBool(rawVal, al.showKnot);
            case "drop", "drop_lead" -> al.dropLead = parseBool(rawVal, al.dropLead);
            default -> {
                return false;
            }
        }
        broadcastUpsert(server, al);
        return true;
    }

    public static boolean unleash(MinecraftServer server, Entity entity, boolean broken, boolean playSound) {
        if (entity == null) {
            return false;
        }
        boolean hadVanilla = false;
        if (entity instanceof Mob mob && mob.isLeashed()) {
            mob.dropLeash(true, false);
            hadVanilla = true;
        }
        ActiveLeash removed = LEASHES.remove(entity.getUUID());
        if (removed == null) {
            return hadVanilla;
        }
        int netId = removed.lastLeashedNetId != -1 ? removed.lastLeashedNetId : entity.getId();
        broadcastAll(server, LeashPacket.remove(netId));
        if (playSound && entity.level() instanceof ServerLevel sl) {
            sl.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 0.75f, broken ? 0.9f : 1.15f);
        }
        if (removed.dropLead && entity.level() instanceof ServerLevel sl) {
            sl.addFreshEntity(new ItemEntity(sl, entity.getX(), entity.getY() + 0.4, entity.getZ(), new ItemStack(Items.LEAD)));
        }
        if (broken) {
            fireBreakEvent(server, entity, removed);
        }
        return true;
    }

    public static void clearAll(MinecraftServer server) {
        LEASHES.clear();
        if (server != null) {
            broadcastAll(server, LeashPacket.clearAll());
        }
    }

    public static void onServerStopped() {
        LEASHES.clear();
    }

    public static boolean isLeashed(@Nullable Entity entity) {
        if (entity == null) {
            return false;
        }
        if (LEASHES.containsKey(entity.getUUID())) {
            return true;
        }
        return entity instanceof Mob mob && mob.isLeashed();
    }

    @Nullable
    public static ActiveLeash get(@Nullable Entity entity) {
        return entity == null ? null : LEASHES.get(entity.getUUID());
    }

    @Nullable
    public static Double currentDistance(MinecraftServer server, @Nullable Entity entity) {
        if (entity == null) {
            return null;
        }
        ActiveLeash al = LEASHES.get(entity.getUUID());
        if (al != null) {
            Vec3 anchor = resolveAnchorPos(server, al, entity);
            return anchor == null ? null : entity.position().distanceTo(anchor);
        }
        if (entity instanceof Mob mob && mob.isLeashed() && mob.getLeashHolder() != null) {
            return (double) mob.distanceTo(mob.getLeashHolder());
        }
        return null;
    }

    @Nullable
    public static Double maxLength(@Nullable Entity entity) {
        if (entity == null) {
            return null;
        }
        ActiveLeash al = LEASHES.get(entity.getUUID());
        if (al != null) {
            return (double) al.maxLength;
        }
        if (entity instanceof Mob mob && mob.isLeashed()) {
            return 10.0;
        }
        return null;
    }

    @Nullable
    public static String holderDescription(MinecraftServer server, @Nullable Entity entity) {
        if (entity == null) {
            return null;
        }
        ActiveLeash al = LEASHES.get(entity.getUUID());
        if (al != null) {
            if (al.toBlock) {
                return "block";
            }
            Entity h = al.holderUuid != null ? findEntity(server, al.holderUuid, al.dimension) : null;
            if (h instanceof Player p) {
                return p.getGameProfile().getName();
            }
            if (h != null) {
                ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(h.getType());
                return key != null ? key.toString() : h.getName().getString();
            }
            return null;
        }
        if (entity instanceof Mob mob && mob.isLeashed() && mob.getLeashHolder() != null) {
            Entity h = mob.getLeashHolder();
            return h instanceof Player p ? p.getGameProfile().getName() : "knot";
        }
        return null;
    }

    @Nullable
    public static String anchorLocation(MinecraftServer server, @Nullable Entity entity) {
        if (entity == null) {
            return null;
        }
        ActiveLeash al = LEASHES.get(entity.getUUID());
        if (al != null) {
            if (al.toBlock && al.blockPos != null) {
                return new ScriptLocation(al.dimension,
                        al.blockPos.getX() + 0.5, al.blockPos.getY() + 0.5, al.blockPos.getZ() + 0.5, 0f, 0f).encode();
            }
            Entity h = al.holderUuid != null ? findEntity(server, al.holderUuid, al.dimension) : null;
            if (h != null) {
                return new ScriptLocation(h.level().dimension().location().toString(),
                        h.getX(), h.getY(), h.getZ(), h.getYRot(), h.getXRot()).encode();
            }
        }
        return null;
    }

    public static void syncToPlayer(MinecraftServer server, ServerPlayer player) {
        if (LEASHES.isEmpty()) {
            ModNetwork.sendTo(player, LeashPacket.clearAll());
            return;
        }
        List<LeashPacket.Entry> list = new ArrayList<>();
        for (ActiveLeash al : LEASHES.values()) {
            LeashPacket.Entry entry = al.toPacketEntry(server);
            if (entry != null) {
                list.add(entry);
            }
        }
        ModNetwork.sendTo(player, LeashPacket.fullSync(list));
    }

    public static void tick(MinecraftServer server) {
        if (LEASHES.isEmpty()) {
            return;
        }
        List<UUID> toRemove = null;
        for (Map.Entry<UUID, ActiveLeash> kv : LEASHES.entrySet()) {
            ActiveLeash al = kv.getValue();
            Entity leashed = findEntity(server, al.leashedUuid, al.dimension);
            if (leashed == null) {
                // Если это не оффлайн-игрок, проверим жив ли вообще где-либо; оффлайн-игрока просто ждём
                continue;
            }
            if (!leashed.isAlive()) {
                if (!(leashed instanceof Player)) {
                    if (toRemove == null) toRemove = new ArrayList<>();
                    toRemove.add(kv.getKey());
                    if (al.lastLeashedNetId != -1) {
                        broadcastAll(server, LeashPacket.remove(al.lastLeashedNetId));
                    }
                }
                continue;
            }

            String curDim = leashed.level().dimension().location().toString();
            Entity holder = null;
            Vec3 anchorPos;

            if (al.toBlock) {
                if (!curDim.equals(al.dimension) || al.blockPos == null) {
                    continue;
                }
                BlockState bs = leashed.level().getBlockState(al.blockPos);
                if (al.hadSolidBlock && bs.isAir()) {
                    // Опорный блок сломали — поводок срывается!
                    if (toRemove == null) toRemove = new ArrayList<>();
                    toRemove.add(kv.getKey());
                    if (al.lastLeashedNetId != -1) {
                        broadcastAll(server, LeashPacket.remove(al.lastLeashedNetId));
                    }
                    leashed.level().playSound(null, al.blockPos, SoundEvents.LEASH_KNOT_BREAK, SoundSource.BLOCKS, 0.8f, 0.95f);
                    if (al.dropLead && leashed.level() instanceof ServerLevel sl) {
                        sl.addFreshEntity(new ItemEntity(sl, al.blockPos.getX() + 0.5, al.blockPos.getY() + 0.5, al.blockPos.getZ() + 0.5, new ItemStack(Items.LEAD)));
                    }
                    fireBreakEvent(server, leashed, al);
                    continue;
                }
                double ay = bs.is(BlockTags.FENCES) ? al.blockPos.getY() + 0.375 : al.blockPos.getY() + 0.5;
                anchorPos = new Vec3(al.blockPos.getX() + 0.5, ay, al.blockPos.getZ() + 0.5);
            } else {
                holder = al.holderUuid != null ? findEntity(server, al.holderUuid, curDim) : null;
                if (holder == null || !holder.isAlive()) {
                    continue;
                }
                if (holder.level() != leashed.level()) {
                    // Если держатель перешёл в другой мир или телепортировался, а поводок неразрывный:
                    continue;
                }
                al.dimension = curDim;
                anchorPos = holder.position().add(0.0, holder.getBbHeight() * 0.55, 0.0);
            }

            // Если изменился сетевой entityId (например, после респавна игрока) — обновим клиентам
            int curLId = leashed.getId();
            int curHId = holder != null ? holder.getId() : -1;
            if (curLId != al.lastLeashedNetId || (!al.toBlock && curHId != al.lastHolderNetId)) {
                broadcastUpsert(server, al);
            }

            Vec3 subPos = leashed.position().add(0.0, leashed.getBbHeight() * 0.5, 0.0);
            Vec3 delta = anchorPos.subtract(subPos);
            double dist = delta.length();

            // Проверка на разрыв поводка по максимальной дистанции
            if (al.breakDistance > 0f && dist >= al.breakDistance) {
                if (toRemove == null) toRemove = new ArrayList<>();
                toRemove.add(kv.getKey());
                if (al.lastLeashedNetId != -1) {
                    broadcastAll(server, LeashPacket.remove(al.lastLeashedNetId));
                }
                leashed.level().playSound(null, leashed.getX(), leashed.getY(), leashed.getZ(),
                        SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 0.8f, 0.9f);
                if (al.dropLead && leashed.level() instanceof ServerLevel sl) {
                    sl.addFreshEntity(new ItemEntity(sl, leashed.getX(), leashed.getY() + 0.4, leashed.getZ(), new ItemStack(Items.LEAD)));
                }
                fireBreakEvent(server, leashed, al);
                continue;
            }

            if (al.stiffness <= 0.0001f || dist < 1.0e-4) {
                continue;
            }

            Vec3 dir = delta.scale(1.0 / dist);
            boolean overMax = dist > al.maxLength;
            boolean underMin = al.minLength > 0f && dist < al.minLength;
            if (!overMax && !underMin) {
                continue;
            }

            double targetLen = overMax ? al.maxLength : al.minLength;
            double diff = dist - targetLen; // > 0 при растяжении, < 0 при сжатии штанги
            float share = (al.bidirectional && holder != null) ? 0.5f : 1.0f;

            // Если неразрывный поводок растянули слишком далеко (например, держатель телепортировался) — подтягиваем
            if (overMax && al.breakDistance <= 0f && dist > al.maxLength + 16.0) {
                Vec3 snapPos = anchorPos.subtract(dir.scale(al.maxLength * 0.85));
                leashed.teleportTo(snapPos.x, snapPos.y, snapPos.z);
                leashed.resetFallDistance();
                continue;
            }

            applyConstraintForce(leashed, dir, diff, al.stiffness * share, true);
            if (al.bidirectional && holder != null) {
                applyConstraintForce(holder, dir.scale(-1.0), diff, al.stiffness * share, true);
            }
        }
        if (toRemove != null) {
            for (UUID id : toRemove) {
                LEASHES.remove(id);
            }
        }
    }

    /**
     * Общая формула натяжения поводка (используется идентично на сервере и в ClientLeash для локального игрока).
     */
    public static void applyConstraintForce(Entity entity, Vec3 dirToAnchor, double stretch, float k, boolean serverSide) {
        Vec3 vel = entity.getDeltaMovement();
        // Проекция текущей скорости вдоль направления к якорю (< 0 означает удаление от якоря)
        double along = vel.dot(dirToAnchor);
        Vec3 newVel = vel;

        if (stretch > 0 && along < 0) {
            // Гасим компоненту скорости, уводящую дальше за предел длины поводка
            double cancel = Math.min(1.0, 0.72 * k);
            newVel = newVel.subtract(dirToAnchor.scale(along * cancel));
        } else if (stretch < 0 && along > 0) {
            double cancel = Math.min(1.0, 0.72 * k);
            newVel = newVel.subtract(dirToAnchor.scale(along * cancel));
        }

        double pullMag = Mth.clamp(stretch * 0.19 * k, -1.55, 1.55);
        newVel = newVel.add(dirToAnchor.scale(pullMag));

        // Когда сущность висит на поводке сверху — сбрасываем накопление урона от падения
        if (stretch > 0.12 && dirToAnchor.y > 0.35) {
            entity.resetFallDistance();
            if (newVel.y < -0.15) {
                newVel = new Vec3(newVel.x * 0.92, newVel.y * 0.55, newVel.z * 0.92);
            }
        }

        if (serverSide && entity instanceof ServerPlayer sp) {
            // Локальный клиент сам плавно считает натяжение каждый тик в ClientLeash;
            // сервер шлёт корректирующий импульс только при сильном превышении длины (античит / рывок).
            if (Math.abs(stretch) > 2.6) {
                sp.setDeltaMovement(newVel);
                sp.hurtMarked = true;
                sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
            }
        } else {
            entity.setDeltaMovement(newVel);
            entity.hasImpulse = true;
        }
    }

    private static void fireBreakEvent(MinecraftServer server, Entity leashed, ActiveLeash al) {
        String victimName = leashed instanceof Player p ? p.getGameProfile().getName() : entityTypeName(leashed);
        String holderName = "block";
        ServerPlayer ctxPlayer = leashed instanceof ServerPlayer sp ? sp : null;
        if (!al.toBlock && al.holderUuid != null) {
            Entity h = findEntity(server, al.holderUuid, al.dimension);
            if (h instanceof ServerPlayer hp) {
                holderName = hp.getGameProfile().getName();
                if (ctxPlayer == null) {
                    ctxPlayer = hp;
                }
            } else if (h != null) {
                holderName = entityTypeName(h);
            }
        }
        if (ctxPlayer != null) {
            ScriptLoader.fireEvent(ScriptEvent.LEASH_BREAK, ctxPlayer, Map.of(
                    "victim", victimName,
                    "holder", holderName
            ));
        }
    }

    private static String entityTypeName(Entity e) {
        ResourceLocation rl = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return rl != null ? rl.toString() : e.getName().getString();
    }

    @Nullable
    private static Vec3 resolveAnchorPos(MinecraftServer server, ActiveLeash al, Entity leashed) {
        if (al.toBlock && al.blockPos != null) {
            return new Vec3(al.blockPos.getX() + 0.5, al.blockPos.getY() + 0.5, al.blockPos.getZ() + 0.5);
        }
        if (al.holderUuid != null) {
            Entity h = findEntity(server, al.holderUuid, leashed.level().dimension().location().toString());
            if (h != null) {
                return h.position().add(0.0, h.getBbHeight() * 0.55, 0.0);
            }
        }
        return null;
    }

    @Nullable
    public static Entity findEntity(MinecraftServer server, UUID uuid, @Nullable String preferredDim) {
        ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
        if (sp != null) {
            return sp;
        }
        if (preferredDim != null) {
            ResourceLocation rl = ResourceLocation.tryParse(preferredDim);
            if (rl != null) {
                for (ServerLevel lvl : server.getAllLevels()) {
                    if (lvl.dimension().location().equals(rl)) {
                        Entity found = lvl.getEntity(uuid);
                        if (found != null) {
                            return found;
                        }
                    }
                }
            }
        }
        for (ServerLevel lvl : server.getAllLevels()) {
            Entity found = lvl.getEntity(uuid);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void broadcastUpsert(MinecraftServer server, ActiveLeash al) {
        if (server == null) {
            return;
        }
        LeashPacket.Entry entry = al.toPacketEntry(server);
        if (entry != null) {
            broadcastAll(server, LeashPacket.upsert(entry));
        }
    }

    private static void broadcastAll(MinecraftServer server, LeashPacket pkt) {
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            ModNetwork.sendTo(sp, pkt);
        }
    }

    public static int parseHexColor(@Nullable String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String s = raw.strip();
        if (s.startsWith("#")) {
            s = s.substring(1);
        } else if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "default", "leather", "brown", "rope" -> DEFAULT_COLOR;
            case "chain", "iron", "steel", "gray", "grey" -> 0xFF94A3B8;
            case "gold", "golden", "yellow" -> 0xFFFACC15;
            case "red", "crimson" -> 0xFFEF4444;
            case "blue", "cyan", "aqua" -> 0xFF38BDF8;
            case "green", "lime", "emerald" -> 0xFF22C55E;
            case "purple", "magenta", "amethyst" -> 0xFFA855F7;
            case "white" -> 0xFFF8FAFC;
            case "black", "dark" -> 0xFF1E293B;
            default -> {
                try {
                    if (s.length() == 6) {
                        yield 0xFF000000 | Integer.parseUnsignedInt(s, 16);
                    }
                    if (s.length() == 8) {
                        yield Integer.parseUnsignedInt(s, 16);
                    }
                } catch (NumberFormatException ignored) {
                }
                yield fallback;
            }
        };
    }

    private static boolean parseBool(Object raw, boolean fallback) {
        String s = Expression.toText(raw).strip().toLowerCase(Locale.ROOT);
        if (s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("on")) return true;
        if (s.equals("false") || s.equals("0") || s.equals("no") || s.equals("off")) return false;
        return fallback;
    }
}
