package com.scriptcraft.network;

import com.scriptcraft.script.ScriptPaths;
import com.scriptcraft.script.runtime.LuckPermsLink;
import com.scriptcraft.studio.StudioAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Сервер -> клиент: имена скриптов этой сессии (папка сервера, в одиночной игре она же локальная). */
public record ScriptListPacket(List<String> names, String lpPresence) {
    public static ScriptListPacket ofServer() {
        List<String> names = new ArrayList<>();
        for (String name : ScriptPaths.listScripts()) {
            if (names.size() >= 200) {
                break;
            }
            names.add(name);
        }
        return new ScriptListPacket(names, LuckPermsLink.presence().name());
    }

    public static void encode(ScriptListPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.names.size());
        for (String name : packet.names) {
            buf.writeUtf(name, 200);
        }
        buf.writeUtf(packet.lpPresence == null ? "NONE" : packet.lpPresence, 16);
    }

    public static ScriptListPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count && i < 200; i++) {
            names.add(buf.readUtf(200));
        }
        String lp = buf.readUtf(16);
        return new ScriptListPacket(names, lp);
    }

    public static void handle(ScriptListPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        com.scriptcraft.client.ClientPacketHandler.studioCatalog(packet.names, packet.lpPresence)));
        ctx.get().setPacketHandled(true);
    }

    /** Клиент просит свежий список. Без права студии — уведомление. */
    public record Request() {
        public static void encode(Request packet, FriendlyByteBuf buf) {
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request();
        }

        public static void handle(Request packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (StudioAccess.allow(player)) {
                    ModNetwork.sendTo(player, ScriptListPacket.ofServer());
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
