package com.scriptcraft.network;

import com.scriptcraft.script.ScriptLog;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Сервер -> клиент: ошибки и фрагменты файлов для журнала. */
public record LogPacket(List<ScriptLog.Issue> issues, List<ScriptLog.Slice> files, boolean open) {
    public static LogPacket from(ScriptLog.View view, boolean open) {
        return new LogPacket(view.issues(), view.files(), open);
    }

    public static void encode(LogPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.issues.size());
        for (ScriptLog.Issue issue : packet.issues) {
            buf.writeUtf(issue.file(), 240);
            buf.writeVarInt(Math.max(0, issue.line()));
            buf.writeUtf(issue.reason(), 400);
            buf.writeBoolean(issue.runtime());
        }
        buf.writeVarInt(packet.files.size());
        for (ScriptLog.Slice file : packet.files) {
            buf.writeUtf(file.name(), 240);
            buf.writeVarInt(Math.max(1, file.firstLine()));
            buf.writeVarInt(file.lines().size());
            for (String line : file.lines()) {
                buf.writeUtf(line, 200);
            }
        }
        buf.writeBoolean(packet.open);
    }

    public static LogPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<ScriptLog.Issue> issues = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            issues.add(new ScriptLog.Issue(buf.readUtf(240), buf.readVarInt(), buf.readUtf(400), buf.readBoolean()));
        }
        int f = buf.readVarInt();
        List<ScriptLog.Slice> files = new ArrayList<>(f);
        for (int i = 0; i < f; i++) {
            String name = buf.readUtf(240);
            int first = buf.readVarInt();
            int lines = buf.readVarInt();
            List<String> text = new ArrayList<>(lines);
            for (int j = 0; j < lines; j++) {
                text.add(buf.readUtf(200));
            }
            files.add(new ScriptLog.Slice(name, first, text));
        }
        return new LogPacket(issues, files, buf.readBoolean());
    }

    public static void handle(LogPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        com.scriptcraft.client.ClientPacketHandler.openLog(packet.issues, packet.files, packet.open)));
        ctx.get().setPacketHandled(true);
    }
}
