package com.scriptcraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Сервер -> клиент: привязка / отвязка 3D-моделей (.geo.json) или предметов к костям игрока или NPC (Props).
 */
public record PropAttachPacket(
        byte action,
        UUID targetUuid,
        String slotId,
        String bone,
        boolean itemMode,
        String modelOrItem,
        String texture,
        String animation,
        float ox, float oy, float oz,
        float rx, float ry, float rz,
        float scale
) {
    public static final byte ATTACH = 0;
    public static final byte DETACH = 1;
    public static final byte CLEAR_ENTITY = 2;
    public static final byte CLEAR_ALL = 3;

    public static PropAttachPacket detach(UUID targetUuid, String slotId) {
        return new PropAttachPacket(DETACH, targetUuid, slotId, "", false, "", "", "", 0, 0, 0, 0, 0, 0, 1f);
    }

    public static PropAttachPacket clearEntity(UUID targetUuid) {
        return new PropAttachPacket(CLEAR_ENTITY, targetUuid, "", "", false, "", "", "", 0, 0, 0, 0, 0, 0, 1f);
    }

    public static PropAttachPacket clearAll() {
        return new PropAttachPacket(CLEAR_ALL, new UUID(0L, 0L), "", "", false, "", "", "", 0, 0, 0, 0, 0, 0, 1f);
    }

    public static void encode(PropAttachPacket p, FriendlyByteBuf buf) {
        buf.writeByte(p.action);
        if (p.action == CLEAR_ALL) {
            return;
        }
        buf.writeUUID(p.targetUuid);
        if (p.action == CLEAR_ENTITY) {
            return;
        }
        buf.writeUtf(p.slotId, 128);
        if (p.action == DETACH) {
            return;
        }
        buf.writeUtf(p.bone, 64);
        buf.writeBoolean(p.itemMode);
        buf.writeUtf(p.modelOrItem, 256);
        buf.writeUtf(p.texture, 256);
        buf.writeUtf(p.animation, 256);
        buf.writeFloat(p.ox);
        buf.writeFloat(p.oy);
        buf.writeFloat(p.oz);
        buf.writeFloat(p.rx);
        buf.writeFloat(p.ry);
        buf.writeFloat(p.rz);
        buf.writeFloat(p.scale);
    }

    public static PropAttachPacket decode(FriendlyByteBuf buf) {
        byte action = buf.readByte();
        if (action == CLEAR_ALL) {
            return clearAll();
        }
        UUID uuid = buf.readUUID();
        if (action == CLEAR_ENTITY) {
            return clearEntity(uuid);
        }
        String slot = buf.readUtf(128);
        if (action == DETACH) {
            return detach(uuid, slot);
        }
        String bone = buf.readUtf(64);
        boolean itemMode = buf.readBoolean();
        String modelOrItem = buf.readUtf(256);
        String tex = buf.readUtf(256);
        String anim = buf.readUtf(256);
        float ox = buf.readFloat(), oy = buf.readFloat(), oz = buf.readFloat();
        float rx = buf.readFloat(), ry = buf.readFloat(), rz = buf.readFloat();
        float scale = buf.readFloat();
        return new PropAttachPacket(ATTACH, uuid, slot, bone, itemMode, modelOrItem, tex, anim,
                ox, oy, oz, rx, ry, rz, scale);
    }

    public static void handle(PropAttachPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.scriptcraft.client.ClientPacketHandler.propAttach(packet)));
        ctx.get().setPacketHandled(true);
    }
}
