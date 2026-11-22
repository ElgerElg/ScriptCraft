package com.scriptcraft.script.runtime;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import com.scriptcraft.script.variables.VariableStore;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    /** Плейсхолдеры, nbt и targeted. Более длинные формы стоят первыми. */
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "%nbt ([^%]+?) of targeted (entity|block|player)%"
                    + "|%nbt of targeted (entity|block|player)%"
                    + "|%targeted (player|entity|block)%"
                    + "|%([a-zA-Z0-9_-]+)%");
    /** Инвентарь и LuckPerms. Отдельный шаблон, чтобы номера групп выше не сдвинулись. */
    private static final Pattern EXTRA = Pattern.compile(
            "%nbt from items in player's inventory%"
                    + "|%nbt of items in player's inventory%"
                    + "|%nbt of player's inventory%"
                    + "|%nbt ([^%]+?) of slot (\\d+) of player(?:'s inventory)?%"
                    + "|%nbt of slot (\\d+) of player(?:'s inventory)?%"
                    + "|%(prefix|suffix|group|groups) of player%");
    /** В кавычках значение переменной только так: %{имя}%. Голые {имя} в тексте остаются как есть. */
    private static final Pattern TEXT_VARIABLE = Pattern.compile("%\\{([^{}]+)}%");
    /** Шаблоны полосы HUD по-прежнему хранят {имя} целиком. */
    private static final Pattern VARIABLE = Pattern.compile("\\{([^{}]+)}");
    /** Цветовые коды в стиле Bukkit: &a, &l ... -> §a, §l. */
    private static final Pattern COLOR = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    /**
     * Предкомпиляция шаблонов строк с плейсхолдерами (%player%, %{-var}%) и цветами (&a).
     * Кэширует разобранные сегменты, чтобы в горячих циклах (every N ticks) не гонять 4 regex Matcher'а на каждую строку.
     */
    private static final int MAX_TEMPLATE_CACHE = 4096;
    private static final java.util.concurrent.ConcurrentHashMap<String, CompiledTemplate> TEMPLATE_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final class CompiledTemplate {
        static final byte SEG_LITERAL = 0;
        static final byte SEG_PLACEHOLDER = 1;
        static final byte SEG_SIMPLE_VAR = 2;

        final boolean fast;
        final boolean hasPercent;
        final boolean hasAmp;
        final String coloredStatic; // если нет % — готовая строка с §
        final byte[] segKinds;
        final String[] segValues;

        CompiledTemplate(boolean fast, boolean hasPercent, boolean hasAmp,
                         @Nullable String coloredStatic, byte[] segKinds, String[] segValues) {
            this.fast = fast;
            this.hasPercent = hasPercent;
            this.hasAmp = hasAmp;
            this.coloredStatic = coloredStatic;
            this.segKinds = segKinds;
            this.segValues = segValues;
        }

        static CompiledTemplate compile(String raw) {
            boolean hasPercent = raw.indexOf('%') >= 0;
            boolean hasAmp = raw.indexOf('&') >= 0;
            if (!hasPercent) {
                String colored = hasAmp ? applyColorsFast(raw) : raw;
                return new CompiledTemplate(true, false, hasAmp, colored, null, null);
            }
            // Если есть сложные EXTRA-формы (%nbt..., %targeted..., %prefix of player%) — fallback на regex
            if (raw.contains("%nbt ") || raw.contains("%nbt of") || raw.contains("%targeted ")
                    || raw.contains(" of player%")) {
                return new CompiledTemplate(false, true, hasAmp, null, null, null);
            }
            java.util.ArrayList<Byte> kinds = new java.util.ArrayList<>();
            java.util.ArrayList<String> vals = new java.util.ArrayList<>();
            int i = 0;
            int n = raw.length();
            int litStart = 0;
            while (i < n) {
                int p = raw.indexOf('%', i);
                if (p < 0) {
                    break;
                }
                // Проверяем %{...}% (простая переменная без вложенных %)
                if (p + 1 < n && raw.charAt(p + 1) == '{') {
                    int close = raw.indexOf("}%", p + 2);
                    if (close > p + 2) {
                        String inner = raw.substring(p + 2, close);
                        if (inner.indexOf('{') < 0 && inner.indexOf('}') < 0 && inner.indexOf('%') < 0) {
                            if (p > litStart) {
                                kinds.add(SEG_LITERAL);
                                vals.add(raw.substring(litStart, p));
                            }
                            kinds.add(SEG_SIMPLE_VAR);
                            vals.add(inner.strip());
                            i = close + 2;
                            litStart = i;
                            continue;
                        }
                        // Вложенные плейсхолдеры/переменные вроде %{-td-gold::%{-tp}%}% — fallback на полный путь
                        return new CompiledTemplate(false, true, hasAmp, null, null, null);
                    }
                }
                // Проверяем простой плейсхолдер %name%
                int end = raw.indexOf('%', p + 1);
                if (end > p + 1) {
                    boolean validName = true;
                    for (int k = p + 1; k < end; k++) {
                        char c = raw.charAt(k);
                        if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                                || (c >= '0' && c <= '9') || c == '_' || c == '-')) {
                            validName = false;
                            break;
                        }
                    }
                    if (validName) {
                        if (p > litStart) {
                            kinds.add(SEG_LITERAL);
                            vals.add(raw.substring(litStart, p));
                        }
                        kinds.add(SEG_PLACEHOLDER);
                        vals.add(raw.substring(p + 1, end).toLowerCase(Locale.ROOT));
                        i = end + 1;
                        litStart = i;
                        continue;
                    }
                }
                i = p + 1;
            }
            if (litStart < n) {
                kinds.add(SEG_LITERAL);
                vals.add(raw.substring(litStart));
            }
            byte[] kArr = new byte[kinds.size()];
            for (int j = 0; j < kinds.size(); j++) {
                kArr[j] = kinds.get(j);
            }
            return new CompiledTemplate(true, true, hasAmp, null, kArr, vals.toArray(String[]::new));
        }
    }

    private static CompiledTemplate templateFor(String raw) {
        CompiledTemplate cached = TEMPLATE_CACHE.get(raw);
        if (cached != null) {
            return cached;
        }
        CompiledTemplate compiled = CompiledTemplate.compile(raw);
        if (TEMPLATE_CACHE.size() < MAX_TEMPLATE_CACHE) {
            TEMPLATE_CACHE.put(raw, compiled);
        }
        return compiled;
    }

    private static String applyColorsFast(String s) {
        int amp = s.indexOf('&');
        if (amp < 0) {
            return s;
        }
        char[] chars = s.toCharArray();
        boolean changed = false;
        for (int i = amp; i < chars.length - 1; i++) {
            if (chars[i] == '&') {
                char c = chars[i + 1];
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                        || (c >= 'k' && c <= 'o') || (c >= 'K' && c <= 'O') || c == 'r' || c == 'R') {
                    chars[i] = '\u00a7';
                    changed = true;
                    i++;
                }
            }
        }
        return changed ? new String(chars) : s;
    }

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
    /** wait — приостановить выполнение и продолжить через waitTicks на главном потоке сервера. */
    private boolean waiting;
    private int waitTicks;
    private int runDepth;
    private final List<Action> continuation = new java.util.ArrayList<>();
    /** Кадры параметров функций. Верхний кадр — текущий вызов. */
    private final ArrayDeque<Map<String, Object>> frames = new ArrayDeque<>();
    private final ArrayDeque<Boolean> stopMarks = new ArrayDeque<>();
    private int functionDepth;
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

    @Nullable
    public CommandSourceStack source() {
        return source;
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
        commandArgs = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(values));
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

    /** Плейсхолдер события (%key%, %menu%, %item%) или пустая строка. */
    public String placeholder(String name) {
        String value = variables.get(name.toLowerCase(Locale.ROOT));
        return value == null ? "" : value;
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

    public boolean hasLocal(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        for (Map<String, Object> frame : frames) {
            if (frame.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public Object local(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        for (Map<String, Object> frame : frames) {
            if (frame.containsKey(key)) {
                return frame.get(key);
            }
        }
        return null;
    }

    public void pushFrame(Map<String, Object> frame) {
        frames.push(frame);
    }

    public void popFrame() {
        if (!frames.isEmpty()) {
            frames.pop();
        }
    }

    /** Копия параметров для wait: продолжение видит те же аргументы функции. */
    public Map<String, Object> snapshotLocals() {
        Map<String, Object> all = new LinkedHashMap<>();
        var oldestFirst = frames.descendingIterator();
        while (oldestFirst.hasNext()) {
            all.putAll(oldestFirst.next());
        }
        return all;
    }

    public void restoreLocals(@Nullable Map<String, Object> saved) {
        frames.clear();
        if (saved != null && !saved.isEmpty()) {
            frames.push(new LinkedHashMap<>(saved));
        }
    }

    /** false — вызовы вложены глубже 32. stop внутри функции не останавливает вызывающего. */
    public boolean enterFunction() {
        if (functionDepth >= 32) {
            return false;
        }
        functionDepth++;
        stopMarks.push(stopped);
        stopped = false;
        return true;
    }

    public void exitFunction() {
        functionDepth = Math.max(0, functionDepth - 1);
        stopped = stopMarks.isEmpty() ? stopped : stopMarks.pop();
    }

    public List<ArgType> snapshotCommandArgTypes() {
        return commandArgTypes;
    }

    public List<Object> snapshotCommandArgs() {
        return commandArgs;
    }

    public void restoreCommandArguments(List<ArgType> types, List<Object> values) {
        this.commandArgTypes = types == null ? List.of() : List.copyOf(types);
        this.commandArgs = values == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(values));
    }

    /** Копия всех кадров параметров функций (от старого к новому) для wait. */
    public List<Map<String, Object>> snapshotFrames() {
        List<Map<String, Object>> out = new java.util.ArrayList<>(frames.size());
        var it = frames.descendingIterator();
        while (it.hasNext()) {
            out.add(new LinkedHashMap<>(it.next()));
        }
        return out;
    }

    public void restoreFrames(@Nullable List<Map<String, Object>> saved, MinecraftServer srv) {
        frames.clear();
        stopMarks.clear();
        functionDepth = 0;
        if (saved == null) {
            return;
        }
        for (Map<String, Object> frame : saved) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : frame.entrySet()) {
                Object val = e.getValue();
                if (val instanceof ServerPlayer sp) {
                    ServerPlayer live = srv.getPlayerList().getPlayer(sp.getUUID());
                    val = live != null ? live : sp;
                }
                copy.put(e.getKey(), val);
            }
            frames.push(copy);
            stopMarks.push(false);
            functionDepth++;
        }
    }

    public void requestWait(int ticks) {
        this.waiting = true;
        this.waitTicks = Math.max(1, ticks);
        this.continuation.clear();
    }

    public boolean isWaiting() {
        return waiting;
    }

    public void appendContinuation(Action action) {
        this.continuation.add(action);
    }

    public void appendContinuation(List<Action> actions) {
        this.continuation.addAll(actions);
    }

    public void enterRun() {
        this.runDepth++;
    }

    public boolean exitRun() {
        this.runDepth = Math.max(0, this.runDepth - 1);
        return this.runDepth == 0;
    }

    public int consumeWaitTicks() {
        int t = this.waitTicks;
        this.waitTicks = 0;
        this.waiting = false;
        return t;
    }

    public List<Action> consumeContinuation() {
        List<Action> copy = List.copyOf(this.continuation);
        this.continuation.clear();
        return copy;
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

    /**
     * Имя переменной из скрипта -> настоящее имя: подставляются плейсхолдеры (%player%, %who%)
     * и вложенные переменные (%{-rank::%who%}%), например:
     * {snake::lb::%who%::%{-snake-lb-rank::%who%}%::name} -> snake::lb::Steve::1::name.
     */
    public String variableName(String rawName) {
        String stripped = rawName.strip();
        if (stripped.indexOf('%') < 0) {
            return stripped;
        }
        CompiledTemplate tpl = templateFor(stripped);
        if (tpl.fast) {
            return evalTemplate(tpl, false);
        }
        String text = expandPlaceholders0(stripped);
        return replaceTextVariables(text, name -> Expression.toText(getVariable(name)));
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
        String text = replaceTextVariables(template, name -> Expression.toText(VariableStore.get(name)));
        if (text.matches("\\{[^{}]+}")) {
            text = Expression.toText(VariableStore.get(text.substring(1, text.length() - 1).strip()));
        }
        return COLOR.matcher(text).replaceAll("\u00a7$1");
    }

    public Object getVariable(String name) {
        // {list::*} — сколько прямых индексов, а не одно значение.
        if (name.endsWith("::*")) {
            return (double) VariableStore.children(name.substring(0, name.length() - 3)).size();
        }
        return VariableStore.get(name);
    }

    /** Плейсхолдер, если он задан в этом запуске, иначе null. */
    @Nullable
    public String placeholderOrNull(String name) {
        return variables.get(name.toLowerCase(Locale.ROOT));
    }

    public void clearPlaceholder(String name) {
        variables.remove(name.toLowerCase(Locale.ROOT));
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
        if (raw.isEmpty()) {
            return raw;
        }
        CompiledTemplate tpl = templateFor(raw);
        if (tpl.fast) {
            if (!tpl.hasPercent) {
                return tpl.coloredStatic;
            }
            return evalTemplate(tpl, true);
        }
        String text = expandPlaceholders0(raw);
        text = replaceTextVariables(text, name -> Expression.toText(getVariable(name)));
        return applyColorsFast(text);
    }

    private String evalTemplate(CompiledTemplate tpl, boolean applyColors) {
        StringBuilder sb = new StringBuilder(32);
        byte[] kinds = tpl.segKinds;
        String[] vals = tpl.segValues;
        for (int i = 0; i < kinds.length; i++) {
            byte k = kinds[i];
            if (k == CompiledTemplate.SEG_LITERAL) {
                sb.append(vals[i]);
            } else if (k == CompiledTemplate.SEG_SIMPLE_VAR) {
                sb.append(Expression.toText(getVariable(vals[i])));
            } else {
                String key = vals[i];
                sb.append(resolveSimplePlaceholder(key));
            }
        }
        String res = sb.toString();
        return applyColors ? applyColorsFast(res) : res;
    }

    private String resolveSimplePlaceholder(String key) {
        if (hasLocal(key)) {
            Object local = local(key);
            if (local instanceof ServerPlayer sp) {
                return sp.getGameProfile().getName();
            }
            return Expression.toText(local);
        }
        String value = variables.get(key);
        if (value == null) {
            value = dynamicPlaceholder(key, "%" + key + "%");
        }
        return value;
    }

    private static String replaceTextVariables(String raw, java.util.function.Function<String, String> values) {
        String current = raw;
        for (int depth = 0; depth < 8; depth++) {
            Matcher m = TEXT_VARIABLE.matcher(current);
            if (!m.find()) {
                break;
            }
            StringBuilder sb = new StringBuilder();
            do {
                m.appendReplacement(sb, Matcher.quoteReplacement(values.apply(m.group(1).strip())));
            } while (m.find());
            m.appendTail(sb);
            current = sb.toString();
        }
        return current;
    }

    private String expandPlaceholders0(String raw) {
        raw = expand(raw, EXTRA, this::extraHit);
        return expand(raw, PLACEHOLDER, this::placeholderHit);
    }

    private String expand(String raw, Pattern pattern, java.util.function.Function<Matcher, String> hit) {
        Matcher m = pattern.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(hit.apply(m)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String extraHit(Matcher m) {
        if (m.group(4) != null) {
            return lpText(m.group(4), null);
        }
        if (player == null) {
            return m.group(2) != null || m.group(3) != null ? "<none>" : "[]";
        }
        if (m.group(2) != null) {
            return InventoryItems.slot(player, Integer.parseInt(m.group(2)), m.group(1).strip());
        }
        if (m.group(3) != null) {
            return InventoryItems.slot(player, Integer.parseInt(m.group(3)), null);
        }
        return InventoryItems.inventory(player);
    }

    private String lpText(String kind, @Nullable String key) {
        if (player == null || !LuckPermsLink.available()) {
            return "<none>";
        }
        String value = LuckPermsLink.value(this, new Target.Self(), kind, key);
        return value == null || value.isBlank() ? "<none>" : value;
    }

    private String placeholderHit(Matcher m) {
        if (m.group(2) != null) {
            return nbtText(m.group(1).strip(), m.group(2));
        }
        if (m.group(3) != null) {
            return nbtText("", m.group(3));
        }
        if (m.group(4) != null) {
            return lookText(m.group(4));
        }
        String key = m.group(5).toLowerCase(Locale.ROOT);
        if (hasLocal(key)) {
            Object local = local(key);
            if (local instanceof ServerPlayer player) {
                return player.getGameProfile().getName();
            }
            return Expression.toText(local);
        }
        String value = variables.get(key);
        if (value == null) {
            value = dynamicPlaceholder(key, m.group(0));
        }
        return value;
    }

    private String nbtText(String path, String kind) {
        if (player == null) {
            return "<none>";
        }
        try {
            return NbtAccess.read(player, kind, path);
        } catch (RuntimeException e) {
            return "<none>";
        }
    }

    private String lookText(String kind) {
        if (player == null) {
            return "<none>";
        }
        return switch (kind) {
            case "player" -> {
                ServerPlayer p = Target.Targeted.lookedAt(player);
                yield p == null ? "<none>" : p.getGameProfile().getName();
            }
            case "entity", "block" -> NbtAccess.describe(player, kind);
            default -> "<none>";
        };
    }

    /**
     * Плейсхолдеры, которые считаются только при использовании:
     * %targeted% — имя игрока, на которого смотрит игрок из контекста (или &lt;none&gt;),
     * %loop-player% — имя текущего игрока цикла (или &lt;none&gt;).
     */
    private String dynamicPlaceholder(String key, String fallback) {
        return switch (key) {
            case "nl", "newline" -> "\n";
            case "targeted" -> lookText("player");
            case "location" -> player == null ? "<none>" : Expression.toText(Places.ofPlayer(player));
            case "tool" -> player == null ? "<none>" : Places.tool(player);
            case "loop-player" -> loopPlayer == null ? "<none>" : loopPlayer.getGameProfile().getName();
            case "script" -> scriptName();
            case "prefix", "suffix", "group", "groups" -> lpText(key, null);
            default -> fallback;
        };
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
