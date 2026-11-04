package com.scriptcraft.network;

import com.scriptcraft.ScriptCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Сетевой канал мода (Forge SimpleChannel). */
public final class ModNetwork {
    /** Версия протокола: при несовпадении клиент не сможет зайти на сервер. Меняйте при изменении пакетов. */
    // 2: синхронизация ресурсов (манифест / запрос / куски файлов).
    // 3: HUD (HudPacket), слои у элементов, полосы прогресса, новые виды слотов.
    private static final String PROTOCOL_VERSION = "3";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ScriptCraft.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static boolean registered = false;

    private ModNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        int id = 0;
        // Сервер -> клиент: открыть экран, описанный в скрипте.
        CHANNEL.messageBuilder(OpenScreenPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenScreenPacket::encode)
                .decoder(OpenScreenPacket::decode)
                .consumerMainThread(OpenScreenPacket::handle)
                .add();
        // Сервер -> клиент: список ресурсов (путь + SHA-1).
        CHANNEL.messageBuilder(ResourceManifestPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ResourceManifestPacket::encode)
                .decoder(ResourceManifestPacket::decode)
                .consumerMainThread(ResourceManifestPacket::handle)
                .add();
        // Клиент -> сервер: запрос недостающих файлов.
        CHANNEL.messageBuilder(ResourceRequestPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ResourceRequestPacket::encode)
                .decoder(ResourceRequestPacket::decode)
                .consumerMainThread(ResourceRequestPacket::handle)
                .add();
        // Сервер -> клиент: кусок файла.
        CHANNEL.messageBuilder(ResourceChunkPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ResourceChunkPacket::encode)
                .decoder(ResourceChunkPacket::decode)
                .consumerMainThread(ResourceChunkPacket::handle)
                .add();
        // Сервер -> клиент: изменения HUD (только разница, а не весь HUD).
        CHANNEL.messageBuilder(HudPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HudPacket::encode)
                .decoder(HudPacket::decode)
                .consumerMainThread(HudPacket::handle)
                .add();
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendTo(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
