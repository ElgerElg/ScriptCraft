package com.scriptcraft.command;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.config.ScriptCraftConfig;
import com.scriptcraft.lang.Feedback;
import com.scriptcraft.lang.Messages;
import com.scriptcraft.script.parser.ParseException;
import com.scriptcraft.script.parser.ScriptParser;
import com.scriptcraft.script.runtime.Action;
import com.scriptcraft.script.runtime.Actions;
import com.scriptcraft.script.runtime.ExecutionContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

/**
 * Эффект-команды как в Skript: сообщение в чате вида "?heal player" не уходит в чат,
 * а выполняется как одна строка скрипта от имени написавшего игрока.
 *
 * Включается в конфиге (effectCommands.enableEffectCommands = true). Доступ: право
 * scriptcraft.effectcommands (Forge PermissionAPI — его можно выдать через LuckPerms и т.п.);
 * по умолчанию — только операторам (уровень 2), если effectCommandsRequireOp = true.
 */
public final class EffectCommands {
    public static final PermissionNode<Boolean> PERMISSION = new PermissionNode<>(ScriptCraft.MODID, "effectcommands",
            PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null
                    && (!ScriptCraftConfig.effectCommandsRequireOp() || player.hasPermissions(2)));

    private EffectCommands() {
    }

    private static boolean hooked;
    private static boolean toldDisabled;
    private static String recent = "";
    private static long recentAt;

    /** Регистрация узла прав (Forge требует объявить все узлы заранее, иначе getPermission бросает исключение). */
    @SubscribeEvent
    public static void onPermissionNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(PERMISSION);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onChat(ServerChatEvent event) {
        String raw = chatText(event.getRawText(), event.getMessage().getString());
        if (raw == null) {
            return;
        }
        if (!ScriptCraftConfig.enableEffectCommands()) {
            noteDisabled();
            return;
        }
        ServerPlayer player = event.getPlayer();
        if (!claim(player.getUUID() + "|" + raw)) {
            event.setCanceled(true);
            return;
        }
        event.setCanceled(true);
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        String line = raw.substring(ScriptCraftConfig.effectCommandToken().length()).strip();
        server.execute(() -> run(server, player, line));
    }

    /**
     * На гибриде чат часто идёт через Bukkit и не доходит до Forge.
     * Слушаем оба пути и не выполняем одну и ту же строку дважды.
     */
    public static void hookChat() {
        if (hooked) {
            return;
        }
        hooked = true;
        try {
            Class<?> eventClass = Class.forName("org.bukkit.event.player.AsyncPlayerChatEvent");
            Class<?> listenerClass = Class.forName("org.bukkit.event.Listener");
            Class<?> executorClass = Class.forName("org.bukkit.plugin.EventExecutor");
            Class<?> priorityClass = Class.forName("org.bukkit.event.EventPriority");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object priority = Enum.valueOf((Class<? extends Enum>) priorityClass.asSubclass(Enum.class), "LOWEST");
            Object listener = Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class<?>[]{listenerClass},
                    (proxy, method, args) -> proxyValue(method));
            Object executor = Proxy.newProxyInstance(executorClass.getClassLoader(), new Class<?>[]{executorClass},
                    (proxy, method, args) -> {
                        if ("execute".equals(method.getName()) && args != null && args.length >= 2 && args[1] != null) {
                            onBukkitChat(args[1]);
                        }
                        return proxyValue(method);
                    });
            Object plugin = anyPlugin();
            if (plugin == null) {
                return;
            }
            Object manager = Class.forName("org.bukkit.Bukkit").getMethod("getPluginManager").invoke(null);
            for (Method method : manager.getClass().getMethods()) {
                if (method.getName().equals("registerEvent") && method.getParameterCount() == 6) {
                    method.invoke(manager, eventClass, listener, priority, executor, plugin, true);
                    return;
                }
            }
        } catch (ClassNotFoundException ignored) {
            // чистый Forge, без Bukkit
        } catch (ReflectiveOperationException e) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] effect command chat: {}", e.toString());
        }
    }

    private static void onBukkitChat(Object event) {
        try {
            String raw = chatText(String.valueOf(event.getClass().getMethod("getMessage").invoke(event)), null);
            if (raw == null) {
                return;
            }
            if (!ScriptCraftConfig.enableEffectCommands()) {
                noteDisabled();
                return;
            }
            Object bukkitPlayer = event.getClass().getMethod("getPlayer").invoke(event);
            UUID id = (UUID) bukkitPlayer.getClass().getMethod("getUniqueId").invoke(bukkitPlayer);
            event.getClass().getMethod("setCancelled", boolean.class).invoke(event, true);
            if (!claim(id + "|" + raw)) {
                return;
            }
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return;
            }
            String line = raw.substring(ScriptCraftConfig.effectCommandToken().length()).strip();
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    run(server, player, line);
                }
            });
        } catch (ReflectiveOperationException e) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] effect command chat: {}", e.toString());
        }
    }

    private static String chatText(String raw, String plain) {
        String token = ScriptCraftConfig.effectCommandToken();
        String text = raw == null ? "" : raw.strip();
        if (!text.startsWith(token) && plain != null) {
            text = plain.strip();
        }
        return text.startsWith(token) ? text : null;
    }

    private static boolean claim(String key) {
        long now = System.nanoTime();
        if (key.equals(recent) && now - recentAt < 1_000_000_000L) {
            return false;
        }
        recent = key;
        recentAt = now;
        return true;
    }

    private static void noteDisabled() {
        if (toldDisabled) {
            return;
        }
        toldDisabled = true;
        ScriptCraft.LOGGER.info("[ScriptCraft] {}", Messages.get("effectcmd.disabled"));
    }

    private static Object anyPlugin() throws ReflectiveOperationException {
        Object manager = Class.forName("org.bukkit.Bukkit").getMethod("getPluginManager").invoke(null);
        Object array = null;
        for (Method method : manager.getClass().getMethods()) {
            if (method.getName().equals("getPlugins") && method.getParameterCount() == 0) {
                array = method.invoke(manager);
                break;
            }
        }
        if (array instanceof Object[] plugins) {
            for (Object plugin : plugins) {
                if (plugin != null && Boolean.TRUE.equals(plugin.getClass().getMethod("isEnabled").invoke(plugin))) {
                    return plugin;
                }
            }
        }
        return null;
    }

    private static Object proxyValue(Method method) {
        if ("toString".equals(method.getName())) {
            return "ScriptCraft";
        }
        if ("hashCode".equals(method.getName())) {
            return 0;
        }
        if ("equals".equals(method.getName())) {
            return false;
        }
        Class<?> type = method.getReturnType();
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        return null;
    }

    private static boolean allowed(ServerPlayer player) {
        if (PermissionAPI.getPermission(player, PERMISSION)) {
            return true;
        }
        return com.scriptcraft.script.runtime.Permissions.hasNode(player, ScriptCraft.MODID + ".effectcommands");
    }

    private static void run(MinecraftServer server, ServerPlayer player, String line) {
        if (!allowed(player)) {
            Feedback.toPlayer(player, "effectcmd.no_permission");
            return;
        }
        ScriptCraft.LOGGER.info("[ScriptCraft] {} выполнил эффект-команду: {}", player.getGameProfile().getName(), line);
        Action action;
        try {
            action = ScriptParser.parseEffectLine(line);
        } catch (ParseException e) {
            Feedback.toPlayer(player, "effectcmd.parse_error", e.getMessage());
            return;
        }
        ExecutionContext ctx = new ExecutionContext(server, player, player.createCommandSourceStack()).markEffectCommand();
        try {
            Actions.runAll(List.of(action), ctx);
            if (ctx.hadOnlyEmptyTargets()) {
                // В скриптах пустой набор целей — тихо ничего не делать; в чате скажем об этом.
                Feedback.toPlayer(player, "effectcmd.no_targets");
            }
        } catch (RuntimeException e) {
            Feedback.toPlayer(player, "effectcmd.run_error", e.getMessage());
        }
    }
}
