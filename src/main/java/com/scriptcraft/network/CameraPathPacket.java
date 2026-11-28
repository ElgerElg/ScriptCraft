package com.scriptcraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Сервер -> клиент: плавный пролёт камеры / катсцена по точкам сплайна (Catmull-Rom).
 */
public record CameraPathPacket(
        boolean stop,
        List<Waypoint> waypoints,
        int durationTicks,
        boolean hasLookTarget,
        double lookX,
        double lookY,
        double lookZ,
        int lookEntityId,
        boolean lockInput,
        boolean lockLook,
        boolean hideHand
) {
    public record Waypoint(double x, double y, double z, float yaw, float pitch, boolean hasAngles) {
    }

    public static CameraPathPacket stopCutscene() {
        return new CameraPathPacket(true, List.of(), 0, false, 0, 0, 0, -1, false, false, false);
    }

    public static void encode(CameraPathPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.stop);
        if (p.stop) {
            return;
        }
        int count = Math.min(128, p.waypoints.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Waypoint w = p.waypoints.get(i);
            buf.writeDouble(w.x);
            buf.writeDouble(w.y);
            buf.writeDouble(w.z);
            buf.writeFloat(w.yaw);
            buf.writeFloat(w.pitch);
            buf.writeBoolean(w.hasAngles);
        }
        buf.writeVarInt(p.durationTicks);
        buf.writeBoolean(p.hasLookTarget);
        if (p.hasLookTarget) {
            buf.writeDouble(p.lookX);
            buf.writeDouble(p.lookY);
            buf.writeDouble(p.lookZ);
            buf.writeVarInt(p.lookEntityId);
        }
        buf.writeBoolean(p.lockInput);
        buf.writeBoolean(p.lockLook);
        buf.writeBoolean(p.hideHand);
    }

    public static CameraPathPacket decode(FriendlyByteBuf buf) {
        boolean stop = buf.readBoolean();
        if (stop) {
            return stopCutscene();
        }
        int count = Math.max(0, Math.min(128, buf.readVarInt()));
        List<Waypoint> pts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            pts.add(new Waypoint(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat(), buf.readBoolean()));
        }
        int dur = buf.readVarInt();
        boolean hasLook = buf.readBoolean();
        double lx = 0, ly = 0, lz = 0;
        int entId = -1;
        if (hasLook) {
            lx = buf.readDouble();
            ly = buf.readDouble();
            lz = buf.readDouble();
            entId = buf.readVarInt();
        }
        boolean lock = buf.readBoolean();
        boolean lockLook = buf.readBoolean();
        boolean hideHand = buf.readBoolean();
        return new CameraPathPacket(false, pts, dur, hasLook, lx, ly, lz, entId, lock, lockLook, hideHand);
    }

    public static void handle(CameraPathPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.scriptcraft.client.ClientPacketHandler.cameraPath(packet)));
        ctx.get().setPacketHandled(true);
    }
}
