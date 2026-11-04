package com.scriptcraft.script.runtime;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Контекст одного запуска скрипта: сервер, игрок (если есть), источник команды,
 * переменные-плейсхолдеры (%player%, %args%...) и лимит выполнения.
 */
public final class ExecutionContext {
    /** Плейсхолдеры вида %name%. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-zA-Z0-9_]+)%");
    /** Цветовые коды в стиле Bukkit: &a, &l ... -> §a, §l. */
    private static final Pattern COLOR = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    private final MinecraftServer server;
    @Nullable
    private final ServerPlayer player;
    @Nullable
    private final CommandSourceStack source;
    private final Map<String, String> variables = new HashMap<>();
    private final ExecutionBudget budget = ExecutionBudget.defaults();

    public ExecutionContext(MinecraftServer server, @Nullable ServerPlayer player, @Nullable CommandSourceStack source) {
        this.server = server;
        this.player = player;
        this.source = source;
        if (player != null) {
            variables.put("player", player.getGameProfile().getName());
            variables.put("uuid", player.getStringUUID());
        }
        variables.put("online", Integer.toString(server.getPlayerCount()));
    }

    public MinecraftServer server() {
        return server;
    }

    @Nullable
    public ServerPlayer player() {
        return player;
    }

    /** Возвращает игрока или бросает ошибку (например, команду вызвали из консоли). */
    public ServerPlayer requirePlayer() {
        if (player == null) {
            throw new ScriptRuntimeException("Это действие требует игрока (команда вызвана не игроком?)");
        }
        return player;
    }

    public void setVariable(String name, String value) {
        variables.put(name, value);
    }

    /** Один шаг выполнения (проверка лимитов). */
    public void step() {
        budget.step();
    }

    /** Подставляет плейсхолдеры и цветовые коды в строку из скрипта. */
    public String format(String raw) {
        Matcher m = PLACEHOLDER.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = variables.getOrDefault(m.group(1).toLowerCase(), m.group(0));
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return COLOR.matcher(sb.toString()).replaceAll("\u00a7$1");
    }

    /** Ответ тому, кто запустил скрипт (игроку или консоли). */
    public void reply(String message) {
        Component text = Component.literal(message);
        if (source != null) {
            source.sendSuccess(() -> text, false);
        } else if (player != null) {
            player.sendSystemMessage(text);
        }
    }
}
