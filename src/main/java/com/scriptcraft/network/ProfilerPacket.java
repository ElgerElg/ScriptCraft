package com.scriptcraft.network;

import com.scriptcraft.script.ScriptProfiler;
import com.scriptcraft.studio.StudioAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Пакеты профайлера скриптов для студии. */
public final class ProfilerPacket {
    public static final byte FETCH = 0;
    public static final byte TOGGLE = 1;
    public static final byte RESET = 2;

    private ProfilerPacket() {
    }

    public record Request(byte action) {
        public static void encode(Request p, FriendlyByteBuf buf) {
            buf.writeByte(p.action);
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request(buf.readByte());
        }

        public static void handle(Request packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player == null || !StudioAccess.allow(player)) {
                    return;
                }
                if (packet.action == TOGGLE) {
                    ScriptProfiler.toggle();
                } else if (packet.action == RESET) {
                    ScriptProfiler.reset();
                }
                ScriptProfiler.Snapshot snap = ScriptProfiler.snapshot(player.getServer());
                ModNetwork.sendTo(player, Data.from(snap));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record Row(String script, String section, long calls, float totalMs, float avgMs,
                      float maxMs, float lastMs, long recentCalls, float recentMsPerSec) {
    }

    public record Data(boolean enabled, float serverMspt, float scriptTickMs, List<Row> rows) {
        public static Data from(ScriptProfiler.Snapshot snap) {
            List<Row> list = new ArrayList<>(snap.rows().size());
            for (ScriptProfiler.Row r : snap.rows()) {
                list.add(new Row(r.script(), r.section(), r.calls(), r.totalMs(), r.avgMs(),
                        r.maxMs(), r.lastMs(), r.recentCalls(), r.recentMsPerSec()));
            }
            return new Data(snap.enabled(), snap.serverMspt(), snap.scriptTickMs(), List.copyOf(list));
        }

        public static void encode(Data p, FriendlyByteBuf buf) {
            buf.writeBoolean(p.enabled);
            buf.writeFloat(p.serverMspt);
            buf.writeFloat(p.scriptTickMs);
            buf.writeVarInt(p.rows.size());
            for (Row r : p.rows) {
                buf.writeUtf(r.script, 128);
                buf.writeUtf(r.section, 64);
                buf.writeVarLong(r.calls);
                buf.writeFloat(r.totalMs);
                buf.writeFloat(r.avgMs);
                buf.writeFloat(r.maxMs);
                buf.writeFloat(r.lastMs);
                buf.writeVarLong(r.recentCalls);
                buf.writeFloat(r.recentMsPerSec);
            }
        }

        public static Data decode(FriendlyByteBuf buf) {
            boolean en = buf.readBoolean();
            float mspt = buf.readFloat();
            float tickMs = buf.readFloat();
            int n = Math.min(200, buf.readVarInt());
            List<Row> rows = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                rows.add(new Row(
                        buf.readUtf(128),
                        buf.readUtf(64),
                        buf.readVarLong(),
                        buf.readFloat(),
                        buf.readFloat(),
                        buf.readFloat(),
                        buf.readFloat(),
                        buf.readVarLong(),
                        buf.readFloat()));
            }
            return new Data(en, mspt, tickMs, rows);
        }

        public static void handle(Data packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.scriptcraft.client.ClientPacketHandler.profilerData(packet)));
            ctx.get().setPacketHandled(true);
        }
    }
}
