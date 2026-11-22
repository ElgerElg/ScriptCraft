package com.scriptcraft.network;

import com.scriptcraft.client.ClientGuiFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер -> клиент: лёгкие клиентские эффекты поверх GUI/HUD (всплывающие цифры урона и GUI-частицы).
 * Вся анимация (подъём, разлёт, гравитация, затухание) считается на клиенте в 60+ FPS без нагрузки на сеть.
 */
public final class GuiFxPacket {
    public static final byte FLOAT_TEXT = 0;
    public static final byte PARTICLES = 1;

    private final byte kind;
    private final int x;
    private final int y;
    private final String anchor;
    private final String text;
    private final int color;
    private final int count;
    private final int durationMs;
    private final float spread;

    public GuiFxPacket(byte kind, int x, int y, String anchor, String text, int color, int count, int durationMs, float spread) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.anchor = anchor == null ? "center" : anchor;
        this.text = text == null ? "" : text;
        this.color = color;
        this.count = count;
        this.durationMs = durationMs;
        this.spread = spread;
    }

    public byte kind() { return kind; }
    public int x() { return x; }
    public int y() { return y; }
    public String anchor() { return anchor; }
    public String text() { return text; }
    public int color() { return color; }
    public int count() { return count; }
    public int durationMs() { return durationMs; }
    public float spread() { return spread; }

    public static void encode(GuiFxPacket p, FriendlyByteBuf buf) {
        buf.writeByte(p.kind);
        buf.writeVarInt(p.x);
        buf.writeVarInt(p.y);
        buf.writeUtf(p.anchor, 32);
        buf.writeUtf(p.text, 160);
        buf.writeInt(p.color);
        buf.writeVarInt(p.count);
        buf.writeVarInt(p.durationMs);
        buf.writeFloat(p.spread);
    }

    public static GuiFxPacket decode(FriendlyByteBuf buf) {
        return new GuiFxPacket(
                buf.readByte(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(32),
                buf.readUtf(160),
                buf.readInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readFloat()
        );
    }

    public static void handle(GuiFxPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientGuiFx.accept(packet)));
        ctx.get().setPacketHandled(true);
    }
}
