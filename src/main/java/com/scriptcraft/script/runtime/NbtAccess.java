package com.scriptcraft.script.runtime;

import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Чтение и запись NBT у сущности, игрока или блока, на который смотрят. */
public final class NbtAccess {
    private NbtAccess() {
    }

    public static String describe(ServerPlayer viewer, String kind) {
        return switch (kind) {
            case "entity" -> entityText(Look.sight(viewer).entity());
            case "block" -> blockText(viewer, Look.sight(viewer).block());
            case "player" -> {
                Entity e = Look.sight(viewer).entity();
                yield e instanceof ServerPlayer sp ? sp.getGameProfile().getName() : "<none>";
            }
            default -> "<none>";
        };
    }

    public static String read(ServerPlayer viewer, String kind, String path) {
        Holder holder = holder(viewer, kind);
        CompoundTag tag = holder.read();
        if (path == null || path.isBlank()) {
            return tag.getAsString();
        }
        try {
            List<Tag> found = path(path).get(tag);
            if (found.isEmpty()) {
                return "<none>";
            }
            StringBuilder sb = new StringBuilder();
            for (Tag one : found) {
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                sb.append(show(one));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new ScriptRuntimeException(fail("nbt.bad_path", path));
        }
    }

    public static void write(ServerPlayer viewer, String kind, String path, Object value) {
        if (path == null || path.isBlank()) {
            throw new ScriptRuntimeException(fail("nbt.need_path", ""));
        }
        Holder holder = holder(viewer, kind);
        CompoundTag tag = holder.read();
        try {
            path(path).set(tag, toTag(value));
        } catch (Exception e) {
            throw new ScriptRuntimeException(fail("nbt.bad_path", path));
        }
        holder.write(tag);
    }

    public static void remove(ServerPlayer viewer, String kind, String path) {
        if (path == null || path.isBlank()) {
            throw new ScriptRuntimeException(fail("nbt.need_path", ""));
        }
        Holder holder = holder(viewer, kind);
        CompoundTag tag = holder.read();
        try {
            path(path).remove(tag);
        } catch (Exception e) {
            throw new ScriptRuntimeException(fail("nbt.bad_path", path));
        }
        holder.write(tag);
    }

    private static String entityText(Entity entity) {
        if (entity == null) {
            return "<none>";
        }
        var key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        String id = key == null ? entity.getType().toString() : key.toString();
        return entity.hasCustomName() ? entity.getName().getString() + " (" + id + ")" : id;
    }

    private static String blockText(ServerPlayer viewer, net.minecraft.world.phys.BlockHitResult hit) {
        if (hit == null) {
            return "<none>";
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = viewer.level().getBlockState(pos);
        var key = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());
        String id = key == null ? state.getBlock().toString() : key.toString();
        return id + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static NbtPathArgument.NbtPath path(String raw) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return NbtPathArgument.nbtPath().parse(new StringReader(raw));
    }

    private static Tag toTag(Object value) {
        if (value instanceof Boolean b) {
            return net.minecraft.nbt.ByteTag.valueOf(b);
        }
        Double number = Expression.toNumber(value);
        if (number != null && !(value instanceof String)) {
            if (number == Math.rint(number) && !number.isInfinite()) {
                return IntTag.valueOf(number.intValue());
            }
            return DoubleTag.valueOf(number);
        }
        String text = Expression.toText(value);
        if (text.startsWith("{") || text.startsWith("[")) {
            try {
                CompoundTag wrapped = TagParser.parseTag(text.startsWith("{") ? text : "{v:" + text + "}");
                if (text.startsWith("{")) {
                    return wrapped;
                }
                Tag inner = wrapped.get("v");
                if (inner != null) {
                    return inner;
                }
            } catch (Exception ignored) {
                // обычная строка
            }
        }
        return StringTag.valueOf(text);
    }

    private static String show(Tag tag) {
        if (tag instanceof StringTag s) {
            return s.getAsString();
        }
        if (tag instanceof NumericTag n) {
            return n.getAsNumber().toString();
        }
        return tag.toString();
    }

    private static Holder holder(ServerPlayer viewer, String kind) {
        Look.Sight sight = Look.sight(viewer);
        return switch (kind) {
            case "player" -> {
                if (sight.entity() instanceof ServerPlayer sp) {
                    yield new EntityHolder(sp);
                }
                throw new ScriptRuntimeException(fail("nbt.no_player", ""));
            }
            case "entity" -> {
                if (sight.entity() != null) {
                    yield new EntityHolder(sight.entity());
                }
                throw new ScriptRuntimeException(fail("nbt.no_entity", ""));
            }
            case "self" -> new EntityHolder(viewer);
            case "block" -> {
                if (sight.block() == null) {
                    throw new ScriptRuntimeException(fail("nbt.no_block", ""));
                }
                BlockPos pos = sight.block().getBlockPos();
                BlockEntity be = viewer.level().getBlockEntity(pos);
                if (be == null) {
                    throw new ScriptRuntimeException(fail("nbt.no_data", blockText(viewer, sight.block())));
                }
                yield new BlockHolder(be);
            }
            default -> throw new ScriptRuntimeException(fail("nbt.bad_target", kind));
        };
    }

    private static String fail(String key, String extra) {
        return com.scriptcraft.lang.Messages.get(key, extra);
    }

    private interface Holder {
        CompoundTag read();

        void write(CompoundTag tag);
    }

    private record EntityHolder(Entity entity) implements Holder {
        @Override
        public CompoundTag read() {
            CompoundTag tag = new CompoundTag();
            entity.saveWithoutId(tag);
            return tag;
        }

        @Override
        public void write(CompoundTag tag) {
            entity.load(tag);
        }
    }

    private record BlockHolder(BlockEntity block) implements Holder {
        @Override
        public CompoundTag read() {
            return block.saveWithFullMetadata();
        }

        @Override
        public void write(CompoundTag tag) {
            block.load(tag);
            block.setChanged();
            BlockState state = block.getBlockState();
            block.getLevel().sendBlockUpdated(block.getBlockPos(), state, state, 3);
        }
    }
}
