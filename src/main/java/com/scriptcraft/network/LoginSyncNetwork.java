package com.scriptcraft.network;

import com.scriptcraft.ModPaths;
import com.scriptcraft.ScriptCraft;
import com.scriptcraft.config.ScriptCraftConfig;
import com.scriptcraft.registry.Bindings;
import com.scriptcraft.resources.ResourceIndex;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.HandshakeHandler;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.commons.lang3.tuple.Pair;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Канал рукопожатия (ConnectionProtocol.LOGIN — ДО загрузки мира и чанков!):
 * пока игрок находится на экране подключения (ConnectScreen), сервер передаёт таблицу настоящих modid:id
 * и сжатые пачки ресурсов в scriptcraft/cache/assets/, обновляя прогресс-бар и % на экране входа!
 */
public final class LoginSyncNetwork {
    private static final String LOGIN_PROTOCOL = "1";
    /** Целевой размер одной несжатой пачки ассетов в фазе LOGIN (~384 КБ). */
    private static final int BATCH_RAW_LIMIT = 384 * 1024;
    private static final int MAX_COMPRESSED_BYTES = 950 * 1024;

    public static final SimpleChannel LOGIN_CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ScriptCraft.MODID, "login_sync"),
            () -> LOGIN_PROTOCOL,
            LOGIN_PROTOCOL::equals,
            LOGIN_PROTOCOL::equals
    );

    private static boolean registered = false;

    private LoginSyncNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        LOGIN_CHANNEL.messageBuilder(LoginBundlePacket.class, 0, NetworkDirection.LOGIN_TO_CLIENT)
                .loginIndex(LoginBundlePacket::getLoginIndex, LoginBundlePacket::setLoginIndex)
                .encoder(LoginBundlePacket::encode)
                .decoder(LoginBundlePacket::decode)
                .buildLoginPacketList(LoginSyncNetwork::buildServerLoginPackets)
                .consumerNetworkThread(HandshakeHandler.biConsumerFor(LoginSyncNetwork::handleBundleOnClient))
                .add();

        LOGIN_CHANNEL.messageBuilder(LoginAckPacket.class, 1, NetworkDirection.LOGIN_TO_SERVER)
                .loginIndex(LoginAckPacket::getLoginIndex, LoginAckPacket::setLoginIndex)
                .encoder(LoginAckPacket::encode)
                .decoder(LoginAckPacket::decode)
                .consumerNetworkThread(HandshakeHandler.indexFirst((hh, msg, ctx) -> ctx.get().setPacketHandled(true)))
                .add();
    }

    public record AssetFile(String path, String sha1, byte[] data) {
    }

    public static final class LoginBundlePacket implements IntSupplier {
        private int loginIndex;
        private final int stepIndex;
        private final int totalSteps;
        private final Bindings.Snapshot snapshot; // не null только в пакете 0
        private final byte[] gzipFiles;

        public LoginBundlePacket(int stepIndex, int totalSteps, Bindings.Snapshot snapshot, byte[] gzipFiles) {
            this.stepIndex = stepIndex;
            this.totalSteps = totalSteps;
            this.snapshot = snapshot;
            this.gzipFiles = gzipFiles;
        }

        public int getLoginIndex() {
            return loginIndex;
        }

        public void setLoginIndex(int loginIndex) {
            this.loginIndex = loginIndex;
        }

        @Override
        public int getAsInt() {
            return loginIndex;
        }

        public static void encode(LoginBundlePacket p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.stepIndex);
            buf.writeVarInt(p.totalSteps);
            buf.writeBoolean(p.snapshot != null);
            if (p.snapshot != null) {
                p.snapshot.encode(buf);
            }
            buf.writeByteArray(p.gzipFiles);
        }

        public static LoginBundlePacket decode(FriendlyByteBuf buf) {
            int step = buf.readVarInt();
            int total = Math.max(1, buf.readVarInt());
            Bindings.Snapshot snap = buf.readBoolean() ? Bindings.Snapshot.decode(buf) : null;
            byte[] gz = buf.readByteArray(MAX_COMPRESSED_BYTES);
            return new LoginBundlePacket(step, total, snap, gz);
        }
    }

    public static final class LoginAckPacket implements IntSupplier {
        private int loginIndex;

        public int getLoginIndex() {
            return loginIndex;
        }

        public void setLoginIndex(int loginIndex) {
            this.loginIndex = loginIndex;
        }

        @Override
        public int getAsInt() {
            return loginIndex;
        }

        public static void encode(LoginAckPacket p, FriendlyByteBuf buf) {
        }

        public static LoginAckPacket decode(FriendlyByteBuf buf) {
            return new LoginAckPacket();
        }
    }

    private static List<Pair<String, LoginBundlePacket>> buildServerLoginPackets(boolean isLocal) {
        if (isLocal) {
            return List.of();
        }
        Bindings.Snapshot snapshot = Bindings.current();
        List<List<AssetFile>> batches = new ArrayList<>();

        if (ScriptCraftConfig.syncResourcesToClients()) {
            long totalBytes = ResourceIndex.totalBytes();
            long maxAllowed = ScriptCraftConfig.maxResourceSizeMb() * 1024L * 1024L;
            if (totalBytes <= maxAllowed) {
                List<AssetFile> cur = new ArrayList<>();
                int curBytes = 0;
                for (ResourceIndex.Entry e : ResourceIndex.entries().values()) {
                    byte[] d = e.data();
                    if (d.length > BATCH_RAW_LIMIT * 2) {
                        // Очень крупные одиночные файлы докачаются потоково через ResourceSync после коннекта
                        continue;
                    }
                    if (!cur.isEmpty() && curBytes + d.length > BATCH_RAW_LIMIT) {
                        batches.add(cur);
                        cur = new ArrayList<>();
                        curBytes = 0;
                    }
                    cur.add(new AssetFile(e.path(), e.sha1(), d));
                    curBytes += d.length;
                }
                if (!cur.isEmpty()) {
                    batches.add(cur);
                }
            }
        }

        int totalSteps = Math.max(1, batches.size());
        List<Pair<String, LoginBundlePacket>> result = new ArrayList<>(totalSteps);
        if (batches.isEmpty()) {
            result.add(Pair.of("sc_login_0", new LoginBundlePacket(0, 1, snapshot, new byte[0])));
        } else {
            for (int i = 0; i < batches.size(); i++) {
                byte[] gz = compressBatch(batches.get(i));
                result.add(Pair.of("sc_login_" + i, new LoginBundlePacket(i, totalSteps, i == 0 ? snapshot : null, gz)));
            }
        }
        return result;
    }

    private static void handleBundleOnClient(
            HandshakeHandler hh,
            LoginBundlePacket pkt,
            Supplier<NetworkEvent.Context> ctxSupplier
    ) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> applyBundleClientSide(pkt));
        ctx.setPacketHandled(true);
        LOGIN_CHANNEL.reply(new LoginAckPacket(), ctx);
    }

    private static void applyBundleClientSide(LoginBundlePacket pkt) {
        int step = pkt.stepIndex + 1;
        int total = Math.max(1, pkt.totalSteps);
        float progress = 0.20f + 0.62f * ((float) step / (float) total);

        if (pkt.snapshot != null) {
            Bindings.set(pkt.snapshot);
            boolean layoutChanged = com.scriptcraft.resources.AssetBindings.update(pkt.snapshot);
            if (layoutChanged) {
                com.scriptcraft.client.ClientResources.markLoginBakePending();
            }
        }

        int writtenFiles = 0;
        int totalFilesInBatch = 0;
        if (pkt.gzipFiles != null && pkt.gzipFiles.length > 0) {
            List<AssetFile> files = decompressBatch(pkt.gzipFiles);
            totalFilesInBatch = files.size();
            Path cache = ModPaths.cache();
            for (AssetFile af : files) {
                if (!ResourceIndex.isSafePath(af.path())) continue;
                Path target = cache.resolve(af.path());
                if (!target.normalize().startsWith(cache.normalize())) continue;
                if (isCachedWithSameHash(target, af.sha1(), af.data().length)) {
                    continue;
                }
                try {
                    Files.createDirectories(target.getParent());
                    Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
                    Files.write(tmp, af.data());
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    com.scriptcraft.client.ClientResources.recordLoginCachedFile(af.path());
                    writtenFiles++;
                } catch (IOException e) {
                    ScriptCraft.LOGGER.warn("[ScriptCraft] Не удалось сохранить login-ресурс {}: {}", af.path(), e.toString());
                }
            }
        }

        String detail = total > 1
                ? ("Пакет " + step + " / " + total + (writtenFiles > 0 ? " · обновлено файлов: " + writtenFiles : " · кэш актуален"))
                : (totalFilesInBatch > 0 ? ("Файлов проверено: " + totalFilesInBatch + ", скачано: " + writtenFiles) : "");

        com.scriptcraft.client.ClientLoadScreenRenderer.updateJoinProgress(
                progress,
                "Синхронизация ресурсов и modid:item в кэш...",
                "Syncing server assets & modid:item registries...",
                detail
        );
    }

    private static boolean isCachedWithSameHash(Path file, String sha1, int expectedLen) {
        try {
            return Files.isRegularFile(file)
                    && Files.size(file) == expectedLen
                    && ResourceIndex.sha1(Files.readAllBytes(file)).equals(sha1);
        } catch (IOException e) {
            return false;
        }
    }

    private static byte[] compressBatch(List<AssetFile> batch) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(baos);
                 DataOutputStream dos = new DataOutputStream(gz)) {
                dos.writeInt(batch.size());
                for (AssetFile f : batch) {
                    dos.writeUTF(f.path());
                    dos.writeUTF(f.sha1());
                    dos.writeInt(f.data().length);
                    dos.write(f.data());
                }
            }
            byte[] out = baos.toByteArray();
            return out.length <= MAX_COMPRESSED_BYTES ? out : new byte[0];
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private static List<AssetFile> decompressBatch(byte[] compressed) {
        List<AssetFile> list = new ArrayList<>();
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(compressed));
             DataInputStream dis = new DataInputStream(gz)) {
            int count = Math.max(0, Math.min(4096, dis.readInt()));
            for (int i = 0; i < count; i++) {
                String path = dis.readUTF();
                String sha1 = dis.readUTF();
                int len = Math.max(0, Math.min(16 * 1024 * 1024, dis.readInt()));
                byte[] data = dis.readNBytes(len);
                list.add(new AssetFile(path, sha1, data));
            }
        } catch (IOException ignored) {
        }
        return list;
    }
}
