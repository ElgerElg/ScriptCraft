package com.scriptcraft.script.runtime;

import com.scriptcraft.ScriptCraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/**
 * Узел прав. Если стоит LuckPerms, спрашивает его. Без мода узел есть только у оператора (уровень 2).
 * Числовой уровень 0–4 по-прежнему проверяет сама игра, не этот класс.
 */
public final class Permissions {
    private Permissions() {
    }

    public static boolean hasNode(ServerPlayer player, String node) {
        if (player == null || node == null || node.isBlank()) {
            return false;
        }
        if (ModList.get().isLoaded("luckperms")) {
            Boolean result = luckperms(player, node);
            if (result != null) {
                return result;
            }
        }
        return player.hasPermissions(2);
    }

    /** null — LuckPerms не ответил, тогда решает запасной уровень оператора. */
    private static Boolean luckperms(ServerPlayer player, String node) {
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = provider.getMethod("get").invoke(null);
            Object adapter = api.getClass().getMethod("getPlayerAdapter", Class.class).invoke(api, ServerPlayer.class);
            java.lang.reflect.Method getUser = null;
            for (java.lang.reflect.Method method : adapter.getClass().getMethods()) {
                if (method.getName().equals("getUser") && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isAssignableFrom(ServerPlayer.class)) {
                    getUser = method;
                    break;
                }
            }
            if (getUser == null) {
                return null;
            }
            Object user = getUser.invoke(adapter, player);
            if (user == null) {
                return null;
            }
            Object cached = user.getClass().getMethod("getCachedData").invoke(user);
            Object data = cached.getClass().getMethod("getPermissionData").invoke(cached);
            Object tri = data.getClass().getMethod("checkPermission", String.class).invoke(data, node);
            return (Boolean) tri.getClass().getMethod("asBoolean").invoke(tri);
        } catch (ReflectiveOperationException | ClassCastException e) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] LuckPerms: {}", e.toString());
            return null;
        }
    }
}
