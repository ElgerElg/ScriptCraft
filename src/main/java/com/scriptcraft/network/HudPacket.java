package com.scriptcraft.network;

import com.scriptcraft.hud.HudOp;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Сервер -> клиент: пачка изменений HUD (добавить/обновить/скрыть/удалить элементы, скрыть части ванильного HUD). */
public record HudPacket(List<HudOp> ops) {
    private static final int MAX_OPS = 1024;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(ops.size());
        for (HudOp op : ops) {
            HudOp.write(op, buf);
        }
    }

    public static HudPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_OPS) {
            throw new IllegalArgumentException("Слишком много операций HUD: " + n);
        }
        List<HudOp> ops = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ops.add(HudOp.read(buf));
        }
        return new HudPacket(ops);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        // Клиентский код — через DistExecutor, чтобы клиентские классы не грузились на выделенном сервере.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.scriptcraft.client.ClientHud.apply(ops));
        ctx.get().setPacketHandled(true);
    }
}
