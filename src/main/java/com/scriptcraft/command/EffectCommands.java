package com.scriptcraft.command;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.config.ScriptCraftConfig;
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
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

import java.util.List;

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

    /** Регистрация узла прав (Forge требует объявить все узлы заранее, иначе getPermission бросает исключение). */
    @SubscribeEvent
    public static void onPermissionNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(PERMISSION);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onChat(ServerChatEvent event) {
        if (!ScriptCraftConfig.enableEffectCommands()) {
            return;
        }
        String token = ScriptCraftConfig.effectCommandToken();
        String raw = event.getRawText();
        if (raw == null || !raw.startsWith(token)) {
            return;
        }
        // Сообщение не попадёт в чат в любом случае (даже если прав нет) — так делает и Skript.
        event.setCanceled(true);
        ServerPlayer player = event.getPlayer();
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        String line = raw.substring(token.length()).strip();
        // ServerChatEvent может прийти не из главного потока: всё дальнейшее — в потоке сервера.
        server.execute(() -> run(server, player, line));
    }

    private static void run(MinecraftServer server, ServerPlayer player, String line) {
        if (!PermissionAPI.getPermission(player, PERMISSION)) {
            player.sendSystemMessage(Messages.component("effectcmd.no_permission"));
            return;
        }
        ScriptCraft.LOGGER.info("[ScriptCraft] {} выполнил эффект-команду: {}", player.getGameProfile().getName(), line);
        Action action;
        try {
            action = ScriptParser.parseEffectLine(line);
        } catch (ParseException e) {
            player.sendSystemMessage(Messages.component("effectcmd.parse_error", e.getMessage()));
            return;
        }
        ExecutionContext ctx = new ExecutionContext(server, player, player.createCommandSourceStack()).markEffectCommand();
        try {
            Actions.runAll(List.of(action), ctx);
            if (ctx.hadOnlyEmptyTargets()) {
                // В скриптах пустой набор целей — тихо ничего не делать; в чате скажем об этом.
                player.sendSystemMessage(Messages.component("effectcmd.no_targets"));
            }
        } catch (RuntimeException e) {
            player.sendSystemMessage(Messages.component("effectcmd.run_error", e.getMessage()));
        }
    }
}
