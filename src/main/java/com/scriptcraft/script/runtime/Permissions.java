package com.scriptcraft.script.runtime;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

/**
 * Узел прав. Сначала LuckPerms — и мод Forge, и плагин Bukkit.
 * Если LuckPerms не ответил, смотрит узел, зарегистрированный в Forge.
 * Иначе узел есть только у оператора (уровень 2).
 * Числовой уровень 0–4 по-прежнему проверяет сама игра, не этот класс.
 */
public final class Permissions {
    private Permissions() {
    }

    public static boolean hasNode(ServerPlayer player, String node) {
        if (player == null || node == null || node.isBlank()) {
            return false;
        }
        String key = node.strip();
        if (LuckPermsLink.available()) {
            Boolean result = LuckPermsLink.check(player, key);
            if (result != null) {
                return result;
            }
        }
        Boolean forge = forgeNode(player, key);
        if (forge != null) {
            return forge;
        }
        return player.hasPermissions(2);
    }

    /** Узел, который мод объявил в Forge. Незарегистрированное имя — null. */
    @SuppressWarnings("unchecked")
    private static Boolean forgeNode(ServerPlayer player, String node) {
        try {
            for (PermissionNode<?> registered : PermissionAPI.getRegisteredNodes()) {
                if (!node.equals(registered.getNodeName()) || registered.getType() != PermissionTypes.BOOLEAN) {
                    continue;
                }
                return PermissionAPI.getPermission(player, (PermissionNode<Boolean>) registered);
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }
}
