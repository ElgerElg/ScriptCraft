package com.scriptcraft.network;

import com.scriptcraft.registry.Bindings;
import com.scriptcraft.registry.DynamicRegistryManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Сервер -> онлайн-клиенты (Вариант Б при горячем /sc reload):
 * прописывает только что созданные настоящие modid:block / modid:item ровно под серверными числовыми ID
 * и передаёт обновлённый снимок Bindings.Snapshot для мгновенного запекания моделей!
 */
public record HotRegistryPacket(
        List<DynamicRegistryManager.HotEntry> entries,
        Bindings.Snapshot snapshot
) {
    private static final int MAX_ENTRIES = 2048;

    public static void encode(HotRegistryPacket p, FriendlyByteBuf buf) {
        int count = Math.min(MAX_ENTRIES, p.entries.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            DynamicRegistryManager.HotEntry e = p.entries.get(i);
            buf.writeUtf(e.id(), 256);
            buf.writeBoolean(e.isBlock());
            buf.writeVarInt(e.blockIntId());
            buf.writeVarInt(e.itemIntId());
        }
        p.snapshot.encode(buf);
    }

    public static HotRegistryPacket decode(FriendlyByteBuf buf) {
        int count = Math.max(0, Math.min(MAX_ENTRIES, buf.readVarInt()));
        List<DynamicRegistryManager.HotEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf(256);
            boolean isBlock = buf.readBoolean();
            int blockId = buf.readVarInt();
            int itemId = buf.readVarInt();
            list.add(new DynamicRegistryManager.HotEntry(id, isBlock, blockId, itemId));
        }
        Bindings.Snapshot snap = Bindings.Snapshot.decode(buf);
        return new HotRegistryPacket(list, snap);
    }

    public static void handle(HotRegistryPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.scriptcraft.client.ClientPacketHandler.hotRegistry(packet)));
        ctx.get().setPacketHandled(true);
    }
}
