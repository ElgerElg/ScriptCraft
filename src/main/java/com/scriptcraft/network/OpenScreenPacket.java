package com.scriptcraft.network;

import com.scriptcraft.screen.ScreenDescription;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Пакет "открой экран": сервер отправляет клиенту сериализованный список элементов. */
public record OpenScreenPacket(ScreenDescription description) {
    public void encode(FriendlyByteBuf buf) {
        description.write(buf);
    }

    public static OpenScreenPacket decode(FriendlyByteBuf buf) {
        return new OpenScreenPacket(ScreenDescription.read(buf));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        // consumerMainThread уже выполняет нас в главном потоке клиента.
        // Клиентский код вызываем через DistExecutor, чтобы класс ScriptScreen не загружался на выделенном сервере.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.scriptcraft.client.ClientPacketHandler.openScreen(description));
        ctx.get().setPacketHandled(true);
    }
}
