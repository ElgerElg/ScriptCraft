package com.scriptcraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер -> клиент: эффекты камеры (тряска, затемнение/вспышка экрана, FOV, наклон камеры, сброс).
 */
public record CameraEffectPacket(byte kind, float f1, float f2, int i1, int i2, int i3) {
    public static final byte SHAKE = 0;
    public static final byte FADE = 1;
    public static final byte FOV = 2;
    public static final byte ROLL = 3;
    public static final byte RESET = 4;

    public static CameraEffectPacket shake(float intensity, int durationTicks) {
        return new CameraEffectPacket(SHAKE, intensity, 0f, durationTicks, 0, 0);
    }

    public static CameraEffectPacket fade(int argbColor, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        return new CameraEffectPacket(FADE, Float.intBitsToFloat(argbColor), 0f, fadeInTicks, stayTicks, fadeOutTicks);
    }

    public static CameraEffectPacket fov(float fovOffset, int durationTicks) {
        return new CameraEffectPacket(FOV, fovOffset, 0f, durationTicks, 0, 0);
    }

    public static CameraEffectPacket roll(float degrees, int durationTicks) {
        return new CameraEffectPacket(ROLL, degrees, 0f, durationTicks, 0, 0);
    }

    public static CameraEffectPacket reset() {
        return new CameraEffectPacket(RESET, 0f, 0f, 0, 0, 0);
    }

    public int colorArgb() {
        return Float.floatToRawIntBits(f1);
    }

    public static void encode(CameraEffectPacket p, FriendlyByteBuf buf) {
        buf.writeByte(p.kind);
        buf.writeFloat(p.f1);
        buf.writeFloat(p.f2);
        buf.writeVarInt(p.i1);
        buf.writeVarInt(p.i2);
        buf.writeVarInt(p.i3);
    }

    public static CameraEffectPacket decode(FriendlyByteBuf buf) {
        return new CameraEffectPacket(buf.readByte(), buf.readFloat(), buf.readFloat(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(CameraEffectPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.scriptcraft.client.ClientPacketHandler.cameraEffect(packet)));
        ctx.get().setPacketHandled(true);
    }
}
