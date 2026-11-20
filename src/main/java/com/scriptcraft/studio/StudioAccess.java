package com.scriptcraft.studio;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.config.ScriptCraftConfig;
import com.scriptcraft.lang.Feedback;
import com.scriptcraft.script.runtime.Permissions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

/**
 * Кто может открыть студию и править скрипты.
 * Узел scriptcraft.studio: по умолчанию операторы, если studio.requireOp = true,
 * и любой игрок, если false. Выданное право (Forge или LuckPerms, мод или плагин) тоже пускает.
 */
public final class StudioAccess {
    public static final String NODE = ScriptCraft.MODID + ".studio";

    public static final PermissionNode<Boolean> PERMISSION = new PermissionNode<>(ScriptCraft.MODID, "studio",
            PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null
                    && (!ScriptCraftConfig.studioRequireOp() || player.hasPermissions(2)));

    private StudioAccess() {
    }

    @SubscribeEvent
    public static void onPermissionNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(PERMISSION);
    }

    public static boolean may(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        try {
            if (PermissionAPI.getPermission(player, PERMISSION)) {
                return true;
            }
        } catch (Throwable ignored) {
            // Узел ещё не собран — ниже проверка LuckPerms и запасной уровень оператора.
        }
        return Permissions.hasNode(player, NODE);
    }

    /** Нет права — одно уведомление и отказ. */
    public static boolean allow(ServerPlayer player) {
        if (may(player)) {
            return true;
        }
        if (player != null) {
            Feedback.toPlayer(player, "studio.denied");
        }
        return false;
    }
}
