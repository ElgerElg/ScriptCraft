package com.scriptcraft.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Сетевая синхронизация универсальных настраиваемых поводков (сущность/игрок ↔ сущность/игрок или блок).
 */
public record LeashPacket(byte op, List<Entry> entries, int targetEntityId) {
    public static final byte OP_UPSERT = 0;
    public static final byte OP_REMOVE = 1;
    public static final byte OP_CLEAR = 2;
    public static final byte OP_FULL_SYNC = 3;

    public record Entry(
            int leashedEntityId,
            boolean toBlock,
            int holderEntityId,
            BlockPos blockPos,
            String dimension,
            float maxLength,
            float minLength,
            float stiffness,
            float breakDistance,
            boolean bidirectional,
            int color,
            float width,
            float sag,
            boolean showKnot
    ) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(leashedEntityId);
            buf.writeBoolean(toBlock);
            if (toBlock) {
                buf.writeBlockPos(blockPos != null ? blockPos : BlockPos.ZERO);
            } else {
                buf.writeVarInt(holderEntityId);
            }
            buf.writeUtf(dimension != null ? dimension : "minecraft:overworld", 128);
            buf.writeFloat(maxLength);
            buf.writeFloat(minLength);
            buf.writeFloat(stiffness);
            buf.writeFloat(breakDistance);
            buf.writeBoolean(bidirectional);
            buf.writeInt(color);
            buf.writeFloat(width);
            buf.writeFloat(sag);
            buf.writeBoolean(showKnot);
        }

        public static Entry read(FriendlyByteBuf buf) {
            int leashedId = buf.readVarInt();
            boolean toBlock = buf.readBoolean();
            int holderId = -1;
            BlockPos pos = BlockPos.ZERO;
            if (toBlock) {
                pos = buf.readBlockPos();
            } else {
                holderId = buf.readVarInt();
            }
            String dim = buf.readUtf(128);
            float maxLen = buf.readFloat();
            float minLen = buf.readFloat();
            float stiff = buf.readFloat();
            float breakDist = buf.readFloat();
            boolean bidir = buf.readBoolean();
            int col = buf.readInt();
            float width = buf.readFloat();
            float sag = buf.readFloat();
            boolean knot = buf.readBoolean();
            return new Entry(leashedId, toBlock, holderId, pos, dim,
                    maxLen, minLen, stiff, breakDist, bidir, col, width, sag, knot);
        }
    }

    public static LeashPacket upsert(Entry entry) {
        return new LeashPacket(OP_UPSERT, List.of(entry), entry.leashedEntityId());
    }

    public static LeashPacket remove(int leashedEntityId) {
        return new LeashPacket(OP_REMOVE, List.of(), leashedEntityId);
    }

    public static LeashPacket clearAll() {
        return new LeashPacket(OP_CLEAR, List.of(), -1);
    }

    public static LeashPacket fullSync(List<Entry> all) {
        return new LeashPacket(OP_FULL_SYNC, List.copyOf(all), -1);
    }

    public static void encode(LeashPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.op);
        switch (msg.op) {
            case OP_UPSERT -> msg.entries.get(0).write(buf);
            case OP_REMOVE -> buf.writeVarInt(msg.targetEntityId);
            case OP_FULL_SYNC -> {
                buf.writeVarInt(msg.entries.size());
                for (Entry e : msg.entries) {
                    e.write(buf);
                }
            }
            default -> {
            }
        }
    }

    public static LeashPacket decode(FriendlyByteBuf buf) {
        byte op = buf.readByte();
        return switch (op) {
            case OP_UPSERT -> {
                Entry e = Entry.read(buf);
                yield new LeashPacket(op, List.of(e), e.leashedEntityId());
            }
            case OP_REMOVE -> new LeashPacket(op, List.of(), buf.readVarInt());
            case OP_FULL_SYNC -> {
                int n = Math.max(0, Math.min(4096, buf.readVarInt()));
                List<Entry> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(Entry.read(buf));
                }
                yield new LeashPacket(op, list, -1);
            }
            default -> new LeashPacket(OP_CLEAR, List.of(), -1);
        };
    }

    public static void handle(LeashPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.scriptcraft.client.ClientLeash.handlePacket(msg)));
        ctx.get().setPacketHandled(true);
    }
}
