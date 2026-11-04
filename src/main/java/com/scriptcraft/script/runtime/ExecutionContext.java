package com.scriptcraft.script.runtime;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import com.scriptcraft.script.variables.VariableStore;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Контекст одного запуска скрипта: сервер, игрок (если есть), источник команды,
 * плейсхолдеры (%player%, %args%...), доступ к переменным {name} и лимит выполнения.
 */
public final class ExecutionContext {
    /** Плейсхолдеры вида %name%. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-zA-Z0-9_]+)%");
    /** Переменные в тексте: {name}, {coins::%player%}. */
    private static final Pattern VARIABLE = Pattern.compile("\\{([^{}]+)}");
    /** Цветовые коды в стиле Bukkit: &a, &l ... -> §a, §l. */
    private static final Pattern COLOR = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    private final MinecraftServer server;
    @Nullable
    private final ServerPlayer player;
    @Nullable
    private final CommandSourceStack source;
    private final Map<String, String> variables = new HashMap<>();
    private final ExecutionBudget budget = ExecutionBudget.fromConfig();
    /** Запуск из эффект-команды в чате (?heal player): некоторые эффекты здесь строже. */
    private boolean effectCommand;

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

    public boolean isEffectCommand() {
        return effectCommand;
    }

    public ExecutionContext markEffectCommand() {
        this.effectCommand = true;
        return this;
    }

    /** Задаёт плейсхолдер %name% (например, %args% для команд). */
    public void setPlaceholder(String name, String value) {
        variables.put(name, value);
    }

    /** Имя переменной из скрипта -> настоящее имя: подставляются плейсхолдеры, {coins::%player%} -> coins::Steve. */
    public String variableName(String rawName) {
        return expandPlaceholders(rawName.strip());
    }

    public Object getVariable(String name) {
        return VariableStore.get(name);
    }

    public void setVariable(String name, Object value) {
        VariableStore.set(name, value);
    }

    public void deleteVariable(String name) {
        VariableStore.delete(name);
    }

    /** Один шаг выполнения (проверка лимитов). */
    public void step() {
        budget.step();
    }

    /**
     * Подставляет в строку из скрипта: 1) плейсхолдеры %name% (в том числе внутри имён переменных),
     * 2) значения переменных {name}, 3) цветовые коды &x.
     * Значения переменных повторно не разбираются — так текст из переменной не может "подставить" что-то лишнее.
     */
    public String format(String raw) {
        String text = expandPlaceholders(raw);
        Matcher m = VARIABLE.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = Expression.toText(getVariable(m.group(1).strip()));
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return COLOR.matcher(sb.toString()).replaceAll("\u00a7$1");
    }

    private String expandPlaceholders(String raw) {
        Matcher m = PLACEHOLDER.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = variables.getOrDefault(m.group(1).toLowerCase(), m.group(0));
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
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
