package com.scriptcraft.network;

import com.scriptcraft.registry.Bindings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер -> клиент: таблица привязок "id из скрипта -> слот пула" со свойствами define.
 * Отправляется при входе и после каждого изменения define (/sc reload, enable, disable).
 */
public record BindingsPacket(Bindings.Snapshot snapshot) {
    public void encode(FriendlyByteBuf buf) {
        snapshot.encode(buf);
    }

    public static BindingsPacket decode(FriendlyByteBuf buf) {
        return new BindingsPacket(Bindings.Snapshot.decode(buf));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.scriptcraft.client.ClientBindings.apply(snapshot));
        ctx.get().setPacketHandled(true);
    }
}
