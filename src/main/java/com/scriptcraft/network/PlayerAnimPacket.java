package com.scriptcraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Сервер -> клиент: проиграть или остановить анимацию игрока (PlayerAnimator + Bendy-lib).
 */
public record PlayerAnimPacket(boolean stop, UUID targetUuid, String animationId,
                               boolean firstPerson, float speed, int fadeTicks) {

    public static PlayerAnimPacket play(UUID targetUuid, String animationId, boolean firstPerson, float speed, int fadeTicks) {
        return new PlayerAnimPacket(false, targetUuid, animationId == null ? "" : animationId, firstPerson, speed, fadeTicks);
    }

    public static PlayerAnimPacket stop(UUID targetUuid, int fadeTicks) {
        return new PlayerAnimPacket(true, targetUuid, "", true, 1.0f, fadeTicks);
    }

    public static void encode(PlayerAnimPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.stop);
        buf.writeUUID(p.targetUuid);
        buf.writeUtf(p.animationId, 256);
        buf.writeBoolean(p.firstPerson);
        buf.writeFloat(p.speed);
        buf.writeVarInt(p.fadeTicks);
    }

    public static PlayerAnimPacket decode(FriendlyByteBuf buf) {
        return new PlayerAnimPacket(
                buf.readBoolean(),
                buf.readUUID(),
                buf.readUtf(256),
                buf.readBoolean(),
                buf.readFloat(),
                buf.readVarInt());
    }

    public static void handle(PlayerAnimPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.scriptcraft.client.ClientPacketHandler.playerAnim(packet)));
        ctx.get().setPacketHandled(true);
    }
}
