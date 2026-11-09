package com.scriptcraft.script.runtime;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import com.scriptcraft.script.variables.VariableStore;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Контекст одного запуска скрипта: сервер, игрок (если есть), источник команды,
 * плейсхолдеры (%player%, %args%...), доступ к переменным {name} и лимит выполнения.
 */
public final class ExecutionContext {
    /** Плейсхолдеры вида %name%. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-zA-Z0-9_-]+)%");
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
    /** Текущий игрок цикла "loop <цель>:" (loop-player), null вне цикла. */
    @Nullable
    private ServerPlayer loopPlayer;
    /** Сколько раз действия выбирали цели и сколько игроков всего нашлось (для "нет целей" в эффект-командах). */
    /** Файл скрипта, чей код сейчас выполняется (владелец созданного HUD); null — эффект-команда в чате. */
    @Nullable
    private String script;
    /** Разобранные аргументы команды и их типы. Пусто вне команды и у команды без списка аргументов. */
    private List<Object> commandArgs = List.of();
    private List<ArgType> commandArgTypes = List.of();
    private int targetQueries;
    private int targetHits;
    /** stop — прервать остальные действия этого запуска. */
    private boolean stopped;
    /** cancel event — отменить отменяемое событие Forge (чат, ломание блока, урон...). */
    private boolean cancelled;

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

    @Nullable
    public String script() {
        return script;
    }

    /** Ставится движком перед запуском обработчика события или команды из файла. */
    public ExecutionContext setScript(@Nullable String script) {
        this.script = script;
        variables.put("script", scriptNameOf(script));
        return this;
    }

    /** Имя скрипта для выражения script и %script%: путь и .sc отброшены. */
    public String scriptName() {
        return scriptNameOf(script);
    }

    private static String scriptNameOf(@Nullable String script) {
        if (script == null || script.isEmpty()) {
            return "";
        }
        String name = script;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.endsWith(".sc")) {
            name = name.substring(0, name.length() - 3);
        }
        return name;
    }

    /** Кладёт arg-1, %args% и значения для player-argument и остальных. */
    public void setCommandArguments(List<ArgType> types, List<Object> values) {
        commandArgTypes = List.copyOf(types);
        commandArgs = List.copyOf(values);
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (value == null) {
                continue;
            }
            String text = Expression.toText(value);
            if (!all.isEmpty()) {
                all.append(' ');
            }
            all.append(text);
            setPlaceholder("arg-" + (i + 1), text);
            setPlaceholder("arg" + (i + 1), text);
        }
        setPlaceholder("args", all.toString());
    }

    @Nullable
    public Object commandArg(int index) {
        if (index < 1 || index > commandArgs.size()) {
            return null;
        }
        return commandArgs.get(index - 1);
    }

    @Nullable
    public Object commandArgOfType(ArgType type) {
        for (int i = 0; i < commandArgTypes.size() && i < commandArgs.size(); i++) {
            if (commandArgTypes.get(i) == type && commandArgs.get(i) != null) {
                return commandArgs.get(i);
            }
        }
        return null;
    }

    @Nullable
    public ServerPlayer loopPlayer() {
        return loopPlayer;
    }

    /** Ставит игрока цикла (null — выход из цикла); возвращает предыдущего, чтобы вложенные циклы восстанавливали его. */
    @Nullable
    public ServerPlayer setLoopPlayer(@Nullable ServerPlayer player) {
        ServerPlayer previous = loopPlayer;
        loopPlayer = player;
        return previous;
    }

    /** Отметка от Target.resolve: действие выбрало цели, нашлось count игроков. */
    public void noteTargets(int count) {
        targetQueries++;
        targetHits += count;
    }

    /** true, если действия выбирали цели, но ни разу никого не нашли. */
    public boolean hadOnlyEmptyTargets() {
        return targetQueries > 0 && targetHits == 0;
    }

    /** Задаёт плейсхолдер %name% (например, %args% для команд, %message% в on chat). */
    public void setPlaceholder(String name, String value) {
        variables.put(name.toLowerCase(Locale.ROOT), value);
    }

    /** Копия плейсхолдеров для "wait" (продолжение увидит %message% и остальные). */
    public Map<String, String> snapshotPlaceholders() {
        return new HashMap<>(variables);
    }

    /** Возвращает плейсхолдеры события после паузы и обновляет %online% и имя игрока. */
    public void restorePlaceholders(Map<String, String> saved) {
        variables.putAll(saved);
        variables.put("online", Integer.toString(server.getPlayerCount()));
        if (player != null) {
            variables.put("player", player.getGameProfile().getName());
            variables.put("uuid", player.getStringUUID());
        }
    }

    public void stop() {
        stopped = true;
    }

    public boolean isStopped() {
        return stopped;
    }

    public void cancelEvent() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /** Имя переменной из скрипта -> настоящее имя: подставляются плейсхолдеры, {coins::%player%} -> coins::Steve. */
    public String variableName(String rawName) {
        return expandPlaceholders(rawName.strip());
    }

    /** Плейсхолдеры %player% и остальные, без подстановки {переменных}. Нужно, чтобы HUD запомнил шаблон. */
    public String expandPlaceholders(String raw) {
        return expandPlaceholders0(raw);
    }

    /**
     * Подставляет {переменные} и цветовые коды в уже развёрнутый шаблон HUD.
     * Плейсхолдеры вроде %health% не трогает — их считает клиент.
     */
    public static String fillVariables(String template) {
        Matcher m = VARIABLE.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = Expression.toText(VariableStore.get(m.group(1).strip()));
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return COLOR.matcher(sb.toString()).replaceAll("\u00a7$1");
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
        String text = expandPlaceholders0(raw);
        Matcher m = VARIABLE.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = Expression.toText(getVariable(m.group(1).strip()));
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return COLOR.matcher(sb.toString()).replaceAll("\u00a7$1");
    }

    private String expandPlaceholders0(String raw) {
        Matcher m = PLACEHOLDER.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1).toLowerCase(Locale.ROOT);
            String value = variables.get(key);
            if (value == null) {
                value = dynamicPlaceholder(key, m.group(0));
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Плейсхолдеры, которые считаются только при использовании:
     * %targeted% — имя игрока, на которого смотрит игрок из контекста (или &lt;none&gt;),
     * %loop-player% — имя текущего игрока цикла (или &lt;none&gt;).
     */
    private String dynamicPlaceholder(String key, String fallback) {
        switch (key) {
            case "targeted" -> {
                ServerPlayer p = player == null ? null : Target.Targeted.lookedAt(player);
                return p == null ? "<none>" : p.getGameProfile().getName();
            }
            case "loop-player" -> {
                return loopPlayer == null ? "<none>" : loopPlayer.getGameProfile().getName();
            }
            case "script" -> {
                return scriptName();
            }
            default -> {
                return fallback;
            }
        }
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
