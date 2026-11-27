package com.scriptcraft.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Сервер -> клиент: запуск / остановка 3D Geo-анимаций на предметах в руках и на 3D-блоках.
 */
public record GeoSyncPacket(
        byte kind,
        UUID targetUuid,
        boolean offhand,
        BlockPos blockPos,
        String animName,
        int loopMode,
        float speed
) {
    public static final byte ITEM_ANIM = 0;
    public static final byte BLOCK_ANIM = 1;

    public static GeoSyncPacket itemAnim(UUID targetUuid, boolean offhand, String animName, int loopMode, float speed) {
        return new GeoSyncPacket(ITEM_ANIM, targetUuid, offhand, BlockPos.ZERO,
                animName == null ? "" : animName, loopMode, speed);
    }

    public static GeoSyncPacket blockAnim(BlockPos pos, String animName, int loopMode, float speed) {
        return new GeoSyncPacket(BLOCK_ANIM, new UUID(0L, 0L), false, pos,
                animName == null ? "" : animName, loopMode, speed);
    }

    public static void encode(GeoSyncPacket p, FriendlyByteBuf buf) {
        buf.writeByte(p.kind);
        if (p.kind == ITEM_ANIM) {
            buf.writeUUID(p.targetUuid);
            buf.writeBoolean(p.offhand);
        } else {
            buf.writeBlockPos(p.blockPos);
        }
        buf.writeUtf(p.animName, 256);
        buf.writeVarInt(p.loopMode);
        buf.writeFloat(p.speed);
    }

    public static GeoSyncPacket decode(FriendlyByteBuf buf) {
        byte kind = buf.readByte();
        UUID uuid = new UUID(0L, 0L);
        boolean offhand = false;
        BlockPos pos = BlockPos.ZERO;
        if (kind == ITEM_ANIM) {
            uuid = buf.readUUID();
            offhand = buf.readBoolean();
        } else {
            pos = buf.readBlockPos();
        }
        String anim = buf.readUtf(256);
        int loop = buf.readVarInt();
        float speed = buf.readFloat();
        return new GeoSyncPacket(kind, uuid, offhand, pos, anim, loop, speed);
    }

    public static void handle(GeoSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.scriptcraft.client.ClientPacketHandler.geoSync(packet)));
        ctx.get().setPacketHandled(true);
    }
}
