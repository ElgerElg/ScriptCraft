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
    // 4: пул блоков/предметов и таблица привязок (BindingsPacket).
    // 6: журнал ошибок (LogRequest/Log) и всплывающие уведомления (Notify).
    // 7: студия — список скриптов, чтение, запись и перезагрузка.
    // 8: у блока в таблице привязок появились render и full.
    // 9: удаление скрипта из студии.
    // 10: клик по тексту/картинке/слоту, элемент list, пакеты клавиш и меню.
    private static final String PROTOCOL_VERSION = "10";

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
        // Сервер -> клиент: привязки define к слотам пула (при входе и после /sc reload).
        CHANNEL.messageBuilder(BindingsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BindingsPacket::encode)
                .decoder(BindingsPacket::decode)
                .consumerMainThread(BindingsPacket::handle)
                .add();
        CHANNEL.messageBuilder(NotifyPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(NotifyPacket::encode)
                .decoder(NotifyPacket::decode)
                .consumerMainThread(NotifyPacket::handle)
                .add();
        CHANNEL.messageBuilder(LogRequestPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LogRequestPacket::encode)
                .decoder(LogRequestPacket::decode)
                .consumerMainThread(LogRequestPacket::handle)
                .add();
        CHANNEL.messageBuilder(LogPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LogPacket::encode)
                .decoder(LogPacket::decode)
                .consumerMainThread(LogPacket::handle)
                .add();
        CHANNEL.messageBuilder(ScriptListPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ScriptListPacket::encode)
                .decoder(ScriptListPacket::decode)
                .consumerMainThread(ScriptListPacket::handle)
                .add();
        CHANNEL.messageBuilder(ScriptListPacket.Request.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ScriptListPacket.Request::encode)
                .decoder(ScriptListPacket.Request::decode)
                .consumerMainThread(ScriptListPacket.Request::handle)
                .add();
        CHANNEL.messageBuilder(ScriptDataPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ScriptDataPacket::encode)
                .decoder(ScriptDataPacket::decode)
                .consumerMainThread(ScriptDataPacket::handle)
                .add();
        CHANNEL.messageBuilder(ScriptDataPacket.Read.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ScriptDataPacket.Read::encode)
                .decoder(ScriptDataPacket.Read::decode)
                .consumerMainThread(ScriptDataPacket.Read::handle)
                .add();
        CHANNEL.messageBuilder(ScriptDataPacket.Save.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ScriptDataPacket.Save::encode)
                .decoder(ScriptDataPacket.Save::decode)
                .consumerMainThread(ScriptDataPacket.Save::handle)
                .add();
        CHANNEL.messageBuilder(ScriptReloadPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ScriptReloadPacket::encode)
                .decoder(ScriptReloadPacket::decode)
                .consumerMainThread(ScriptReloadPacket::handle)
                .add();
        CHANNEL.messageBuilder(ScriptDeletePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ScriptDeletePacket::encode)
                .decoder(ScriptDeletePacket::decode)
                .consumerMainThread(ScriptDeletePacket::handle)
                .add();
        CHANNEL.messageBuilder(InputSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(InputSyncPacket::encode)
                .decoder(InputSyncPacket::decode)
                .consumerMainThread(InputSyncPacket::handle)
                .add();
        CHANNEL.messageBuilder(InputPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(InputPacket::encode)
                .decoder(InputPacket::decode)
                .consumerMainThread(InputPacket::handle)
                .add();
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendTo(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
