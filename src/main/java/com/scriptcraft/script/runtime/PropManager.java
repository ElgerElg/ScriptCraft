package com.scriptcraft.script.runtime;

import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.network.PropAttachPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверный менеджер 3D-аттачей (Props) на костях игроков и NPC.
 * Автоматически синхронизирует активные аттачи всем игрокам, в том числе при входе на сервер.
 */
public final class PropManager {
    public record PropSpec(
            String slotId,
            String bone,
            boolean itemMode,
            String modelOrItem,
            String texture,
            String animation,
            float ox, float oy, float oz,
            float rx, float ry, float rz,
            float scale
    ) {
    }

    private static final Map<UUID, Map<String, PropSpec>> SERVER_PROPS = new ConcurrentHashMap<>();

    private PropManager() {
    }

    public static String normalizeBone(String raw) {
        if (raw == null) return "body";
        return switch (raw.strip().toLowerCase(Locale.ROOT).replace("-", "_")) {
            case "head", "helmet", "hat" -> "head";
            case "body", "torso", "chest", "back", "spine" -> "body";
            case "right_arm", "rightarm", "rarm", "right_hand", "mainhand" -> "right_arm";
            case "left_arm", "leftarm", "larm", "left_hand", "offhand" -> "left_arm";
            case "right_leg", "rightleg", "rleg", "right_foot" -> "right_leg";
            case "left_leg", "leftleg", "lleg", "left_foot" -> "left_leg";
            default -> "body";
        };
    }

    public static void attach(MinecraftServer server, UUID targetUuid, PropSpec spec) {
        if (targetUuid == null || spec == null) return;
        String key = spec.slotId().strip().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) key = "default";
        PropSpec clean = new PropSpec(key, normalizeBone(spec.bone()), spec.itemMode(),
                spec.modelOrItem(), spec.texture(), spec.animation(),
                spec.ox(), spec.oy(), spec.oz(), spec.rx(), spec.ry(), spec.rz(), spec.scale());
        SERVER_PROPS.computeIfAbsent(targetUuid, u -> new ConcurrentHashMap<>()).put(key, clean);
        if (server != null) {
            ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new PropAttachPacket(
                    PropAttachPacket.ATTACH, targetUuid, clean.slotId(), clean.bone(), clean.itemMode(),
                    clean.modelOrItem(), clean.texture(), clean.animation(),
                    clean.ox(), clean.oy(), clean.oz(), clean.rx(), clean.ry(), clean.rz(), clean.scale()));
        }
    }

    public static void detach(MinecraftServer server, UUID targetUuid, String slotId) {
        if (targetUuid == null || slotId == null) return;
        String key = slotId.strip().toLowerCase(Locale.ROOT);
        Map<String, PropSpec> map = SERVER_PROPS.get(targetUuid);
        if (map != null) {
            map.remove(key);
            if (map.isEmpty()) SERVER_PROPS.remove(targetUuid);
        }
        if (server != null) {
            ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), PropAttachPacket.detach(targetUuid, key));
        }
    }

    public static void clearEntity(MinecraftServer server, UUID targetUuid) {
        if (targetUuid == null) return;
        SERVER_PROPS.remove(targetUuid);
        if (server != null) {
            ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), PropAttachPacket.clearEntity(targetUuid));
        }
    }

    public static void syncToPlayer(ServerPlayer player) {
        if (player == null) return;
        SERVER_PROPS.forEach((uuid, slots) -> slots.values().forEach(spec ->
                ModNetwork.sendTo(player, new PropAttachPacket(
                        PropAttachPacket.ATTACH, uuid, spec.slotId(), spec.bone(), spec.itemMode(),
                        spec.modelOrItem(), spec.texture(), spec.animation(),
                        spec.ox(), spec.oy(), spec.oz(), spec.rx(), spec.ry(), spec.rz(), spec.scale()))));
    }

    public static void onServerStopped() {
        SERVER_PROPS.clear();
    }
}
