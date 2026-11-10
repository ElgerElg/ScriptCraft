package com.scriptcraft.script.runtime;

import com.scriptcraft.lang.Messages;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Локация игрока, блока и сущности в прицеле, спавн.
 * Строка локации пишется в переменную и читается обратно через {@link ScriptLocation}.
 */
public final class Places {
    private Places() {
    }

    public static String tool(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return "minecraft:air";
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "minecraft:air" : id.toString();
    }

    public static String ofPlayer(ServerPlayer player) {
        return new ScriptLocation(player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()).encode();
    }

    /** Id блока в прицеле. Сущность ближе — блока нет. */
    @Nullable
    public static String blockId(ServerPlayer viewer) {
        BlockHitResult hit = Look.sight(viewer).block();
        if (hit == null) {
            return null;
        }
        BlockState state = viewer.level().getBlockState(hit.getBlockPos());
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return key == null ? null : key.toString();
    }

    /** Id типа сущности в прицеле. */
    @Nullable
    public static String entityId(ServerPlayer viewer) {
        Entity entity = Look.sight(viewer).entity();
        if (entity == null) {
            return null;
        }
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return key == null ? null : key.toString();
    }

    @Nullable
    public static String playerName(ServerPlayer viewer) {
        Entity entity = Look.sight(viewer).entity();
        return entity instanceof ServerPlayer player ? player.getGameProfile().getName() : null;
    }

    @Nullable
    public static String blockLocation(ServerPlayer viewer) {
        BlockHitResult hit = Look.sight(viewer).block();
        if (hit == null) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        return new ScriptLocation(viewer.level().dimension().location().toString(),
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, viewer.getYRot(), viewer.getXRot()).encode();
    }

    @Nullable
    public static String entityLocation(ServerPlayer viewer) {
        Entity entity = Look.sight(viewer).entity();
        if (entity == null) {
            return null;
        }
        return new ScriptLocation(entity.level().dimension().location().toString(),
                entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot()).encode();
    }

    /** Ближе сущность — её точка, иначе точка блока. */
    @Nullable
    public static String targetedLocation(ServerPlayer viewer) {
        String entity = entityLocation(viewer);
        return entity != null ? entity : blockLocation(viewer);
    }

    /** Точка возрождения игрока или общий спавн обычного мира. */
    public static String spawn(MinecraftServer server, @Nullable ServerPlayer player, boolean worldSpawn) {
        if (worldSpawn || player == null) {
            ServerLevel overworld = server.overworld();
            BlockPos pos = overworld.getSharedSpawnPos();
            return new ScriptLocation(overworld.dimension().location().toString(),
                    pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, overworld.getSharedSpawnAngle(), 0f).encode();
        }
        ResourceKey<Level> dim = player.getRespawnDimension();
        BlockPos bed = player.getRespawnPosition();
        ServerLevel level = server.getLevel(dim);
        if (bed == null || level == null) {
            return spawn(server, player, true);
        }
        return new ScriptLocation(level.dimension().location().toString(),
                bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, player.getYRot(), player.getXRot()).encode();
    }

    /** {переменная} с локацией внутри цели-имени. Обычное имя игрока — null. */
    @Nullable
    public static ScriptLocation fromNamed(Target target, ExecutionContext ctx) {
        if (target instanceof Target.Named named) {
            return ScriptLocation.decode(named.name().evaluate(ctx));
        }
        return null;
    }

    public static ScriptLocation require(Object value) {
        ScriptLocation loc = ScriptLocation.decode(value);
        if (loc == null) {
            throw new ScriptRuntimeException(Messages.get("var.not_location", Expression.toText(value)));
        }
        return loc;
    }

    public static ServerLevel level(MinecraftServer server, ScriptLocation loc) {
        ResourceLocation id = ResourceLocation.tryParse(loc.world());
        if (id != null) {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.dimension().location().equals(id)) {
                    return level;
                }
            }
        }
        throw new ScriptRuntimeException(Messages.get("var.unknown_world", loc.world()));
    }

    public static void teleport(ServerPlayer player, ScriptLocation loc, MinecraftServer server) {
        player.teleportTo(level(server, loc), loc.x(), loc.y(), loc.z(), loc.yaw(), loc.pitch());
    }

    public static void drop(MinecraftServer server, ScriptLocation loc, ItemStack stack) {
        ServerLevel level = level(server, loc);
        level.addFreshEntity(new ItemEntity(level, loc.x(), loc.y(), loc.z(), stack));
    }
}
