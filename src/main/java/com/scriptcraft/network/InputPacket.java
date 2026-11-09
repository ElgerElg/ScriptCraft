package com.scriptcraft.network;

import com.scriptcraft.script.InputSync;
import com.scriptcraft.script.ScriptLoader;
import com.scriptcraft.script.runtime.ScriptEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Supplier;

/** Клиент -> сервер: нажата слушаемая клавиша или открывается ванильное меню. */
public record InputPacket(byte kind, String name) {
    public static final byte KEY = 0;
    public static final byte OPEN = 1;

    public static void encode(InputPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.kind);
        buf.writeUtf(packet.name, 32);
    }

    public static InputPacket decode(FriendlyByteBuf buf) {
        return new InputPacket(buf.readByte(), buf.readUtf(32));
    }

    public static void handle(InputPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (player == null) {
                return;
            }
            if (packet.kind == KEY && InputSync.watchesKey(packet.name)) {
                ScriptLoader.fireEvent(ScriptEvent.KEY, player, Map.of("key", packet.name));
            } else if (packet.kind == OPEN && InputSync.watchesMenu(packet.name)) {
                ScriptLoader.fireEvent(ScriptEvent.OPEN, player, Map.of("menu", packet.name));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
