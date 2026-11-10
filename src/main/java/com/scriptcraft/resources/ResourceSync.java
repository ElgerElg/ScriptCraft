package com.scriptcraft.resources;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.config.ScriptCraftConfig;
import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.network.ResourceChunkPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Раздача ресурсов клиентам по сетевому каналу мода (без ванильного серверного ресурспака и без запроса):
 * 1) при входе сервер шлёт манифест (путь + SHA-1);
 * 2) клиент просит только недостающие/изменённые файлы;
 * 3) сервер отправляет их кусками — несколько кусков за тик на игрока, чтобы не забивать соединение.
 * Все методы вызываются в главном потоке сервера.
 */
public final class ResourceSync {
    /** Сколько байт максимум отправлять одному игроку за тик (~10 МБ/с при 20 TPS). */
    private static final int BYTES_PER_TICK = 512 * 1024;

    private static final Map<UUID, ArrayDeque<ResourceChunkPacket>> QUEUES = new HashMap<>();

    private ResourceSync() {
    }

    /** Старт сервера: строим индекс. */
    public static void onServerStarting() {
        QUEUES.clear();
        ResourceIndex.rescan();
    }

    public static void onServerStopped() {
        QUEUES.clear();
    }

    /** Вход игрока: отправляем манифест. */
    public static void onLogin(ServerPlayer player) {
        if (ScriptCraftConfig.syncResourcesToClients()) {
            ModNetwork.sendTo(player, ResourceIndex.manifest());
        }
    }

    public static void onLogout(ServerPlayer player) {
        QUEUES.remove(player.getUUID());
    }

    /**
     * /sc reload resources: перечитать папку и разослать новый манифест всем онлайн-игрокам
     * (каждый клиент докачает только изменённое).
     * @return null при успехе или текст ошибки
     */
    public static String reloadAndPush(MinecraftServer server) {
        String error = ResourceIndex.rescan();
        QUEUES.clear(); // старые куски могли устареть
        if (ScriptCraftConfig.syncResourcesToClients()) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ModNetwork.sendTo(p, ResourceIndex.manifest());
            }
        }
        return error;
    }

    /** Запрос файлов от клиента: ставим их куски в очередь. Неизвестные пути молча игнорируются. */
    public static void handleRequest(ServerPlayer player, List<String> paths) {
        if (!ScriptCraftConfig.syncResourcesToClients()) {
            return;
        }
        int chunkSize = Math.min(ResourceChunkPacket.MAX_CHUNK, ScriptCraftConfig.resourceChunkSizeKb() * 1024);
        ArrayDeque<ResourceChunkPacket> queue = QUEUES.computeIfAbsent(player.getUUID(), u -> new ArrayDeque<>());
        Map<String, ResourceIndex.Entry> index = ResourceIndex.entries();
        int files = 0;
        for (String path : new LinkedHashSet<>(paths)) {
            ResourceIndex.Entry e = index.get(path);
            if (e == null) {
                continue;
            }
            files++;
            byte[] data = e.data();
            if (data.length == 0) {
                queue.add(new ResourceChunkPacket(e.path(), e.sha1(), 0, 0, new byte[0]));
                continue;
            }
            for (int off = 0; off < data.length; off += chunkSize) {
                int len = Math.min(chunkSize, data.length - off);
                byte[] part = new byte[len];
                System.arraycopy(data, off, part, 0, len);
                queue.add(new ResourceChunkPacket(e.path(), e.sha1(), data.length, off, part));
            }
        }
        if (files > 0) {
            ScriptCraft.LOGGER.info("[ScriptCraft] {} получает ресурсов: {} файлов", player.getGameProfile().getName(), files);
        }
    }

    /** Каждый тик сервера: отправляем следующую порцию кусков. */
    public static void tick(MinecraftServer server) {
        if (QUEUES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, ArrayDeque<ResourceChunkPacket>>> it = QUEUES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ArrayDeque<ResourceChunkPacket>> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            ArrayDeque<ResourceChunkPacket> queue = entry.getValue();
            if (player == null || queue.isEmpty()) {
                it.remove();
                continue;
            }
            int sent = 0;
            // Хотя бы один кусок за тик, даже если он больше бюджета.
            while (!queue.isEmpty() && (sent == 0 || sent + queue.peek().data().length <= BYTES_PER_TICK)) {
                ResourceChunkPacket packet = queue.poll();
                sent += Math.max(1, packet.data().length);
                ModNetwork.sendTo(player, packet);
            }
        }
    }
}
