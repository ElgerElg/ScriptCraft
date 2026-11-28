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
    private static final String LOGIN_PROTOCOL = "3";
    /** Целевой размер одной несжатой пачки ассетов в фазе LOGIN (~384 КБ). */
    private static final int BATCH_RAW_LIMIT = 384 * 1024;
    private static final int MAX_COMPRESSED_BYTES = 950 * 1024;
    private static volatile int clientCheckedInHandshake = 0;

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
        private final int totalServerFiles;
        private final Bindings.Snapshot snapshot; // не null только в пакете 0
        private final byte[] gzipFiles;

        public LoginBundlePacket(int stepIndex, int totalSteps, int totalServerFiles, Bindings.Snapshot snapshot, byte[] gzipFiles) {
            this.stepIndex = stepIndex;
            this.totalSteps = totalSteps;
            this.totalServerFiles = totalServerFiles;
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
            buf.writeVarInt(p.totalServerFiles);
            buf.writeBoolean(p.snapshot != null);
            if (p.snapshot != null) {
                p.snapshot.encode(buf);
            }
            buf.writeByteArray(p.gzipFiles);
        }

        public static LoginBundlePacket decode(FriendlyByteBuf buf) {
            int step = buf.readVarInt();
            int total = Math.max(1, buf.readVarInt());
            int totalFiles = Math.max(0, buf.readVarInt());
            Bindings.Snapshot snap = buf.readBoolean() ? Bindings.Snapshot.decode(buf) : null;
            byte[] gz = buf.readByteArray(MAX_COMPRESSED_BYTES);
            return new LoginBundlePacket(step, total, totalFiles, snap, gz);
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
        // На стадии LOGIN передаём только компактную таблицу настоящих modid:id (~2 КБ -> вход за 50 мс без "Took too long to log in"!).
        // Сами файлы ресурсов проверяются по SHA-1 через ResourceManifestPacket сразу при входе:
        // если кэш актуален (например, все 548 файлов уже скачаны) — трафик 0 байт,
        // а если есть новые/изменённые файлы — они докачиваются с плашкой прогресса в левом верхнем углу!
        Bindings.Snapshot snapshot = Bindings.current();
        return List.of(Pair.of("sc_login_0", new LoginBundlePacket(0, 1, 0, snapshot, new byte[0])));
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
        if (pkt.stepIndex == 0) {
            clientCheckedInHandshake = 0;
            com.scriptcraft.client.ClientLoadScreenRenderer.onLoginStartReset();
        }

        if (pkt.snapshot != null) {
            Bindings.set(pkt.snapshot);
            boolean layoutChanged = com.scriptcraft.resources.AssetBindings.update(pkt.snapshot);
            if (layoutChanged) {
                com.scriptcraft.client.ClientResources.markLoginBakePending();
            }
        }

        int writtenFiles = 0;
        String lastFile = "";
        if (pkt.gzipFiles != null && pkt.gzipFiles.length > 0) {
            List<AssetFile> files = decompressBatch(pkt.gzipFiles);
            int expectedTotal = Math.max(pkt.totalServerFiles, clientCheckedInHandshake + files.size());
            Path cache = ModPaths.cache();
            for (AssetFile af : files) {
                if (!ResourceIndex.isSafePath(af.path())) {
                    clientCheckedInHandshake++;
                    continue;
                }
                Path target = cache.resolve(af.path());
                if (!target.normalize().startsWith(cache.normalize())) {
                    clientCheckedInHandshake++;
                    continue;
                }
                if (isCachedWithSameHash(target, af.sha1(), af.data().length)) {
                    clientCheckedInHandshake++;
                    continue;
                }
                try {
                    com.scriptcraft.client.ClientLoadScreenRenderer.onLoginFileProgress(
                            clientCheckedInHandshake, expectedTotal, af.path(), true);
                    Files.createDirectories(target.getParent());
                    Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
                    Files.write(tmp, af.data());
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    com.scriptcraft.client.ClientResources.recordLoginCachedFile(af.path());
                    writtenFiles++;
                    lastFile = af.path();
                } catch (IOException e) {
                    ScriptCraft.LOGGER.warn("[ScriptCraft] Не удалось сохранить login-ресурс {}: {}", af.path(), e.toString());
                }
                clientCheckedInHandshake++;
            }
        }

        com.scriptcraft.client.ClientLoadScreenRenderer.onLoginBatchDone(
                step, total, clientCheckedInHandshake, pkt.totalServerFiles, writtenFiles, lastFile);
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
