package com.scriptcraft.script;

import com.scriptcraft.lang.Messages;
import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.network.NotifyPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Журнал ошибок скриптов: разбор при загрузке и ошибки во время игры.
 * Журнал читает только оператор (пакет по запросу). Повтор одной и той же ошибки
 * во время игры не сыпет уведомлениями чаще, чем раз в несколько секунд.
 */
public final class ScriptLog {
    public record Issue(String file, int line, String reason, boolean runtime) {
    }

    /** Фрагмент файла. {@code firstLine} — номер первой строки в файле (с 1). */
    public record Slice(String name, int firstLine, List<String> lines) {
    }

    public record View(List<Issue> issues, List<Slice> files) {
    }

    private static final Pattern PARSE = Pattern.compile("^(.+):(\\d+): (.*)$");
    private static final int MAX_RUNTIME = 40;
    private static final int MAX_ISSUES = 60;
    private static final int MAX_FILES = 16;
    private static final int WINDOW = 400;
    private static final int LINE_CHARS = 180;
    private static final long TOAST_GAP_MS = 8_000;

    private static final ArrayDeque<Issue> RUNTIME = new ArrayDeque<>();
    private static final Map<String, Long> TOAST_AT = new LinkedHashMap<>();

    private ScriptLog() {
    }

    /** Ошибка выполнения. line = 0: строку в блокноте подсветить нельзя, причина всё равно видна. */
    public static void runtime(String file, @Nullable String message, @Nullable ServerPlayer player, @Nullable MinecraftServer server) {
        String name = file == null ? "" : file;
        String reason = message == null || message.isBlank() ? "error" : clip(message, 300);
        Issue issue = new Issue(name, 0, reason, true);
        synchronized (RUNTIME) {
            Issue last = RUNTIME.peekLast();
            if (last != null && last.equals(issue)) {
                // та же ошибка подряд — в журнале одна запись
            } else {
                RUNTIME.addLast(issue);
                while (RUNTIME.size() > MAX_RUNTIME) {
                    RUNTIME.removeFirst();
                }
            }
        }
        MinecraftServer host = server != null ? server : player == null ? null : player.getServer();
        if (host == null && player == null) {
            return;
        }
        String key = name + "\0" + reason;
        long now = System.currentTimeMillis();
        synchronized (TOAST_AT) {
            Long prev = TOAST_AT.get(key);
            if (prev != null && now - prev < TOAST_GAP_MS) {
                return;
            }
            TOAST_AT.put(key, now);
            if (TOAST_AT.size() > 80) {
                TOAST_AT.keySet().remove(TOAST_AT.keySet().iterator().next());
            }
        }
        NotifyPacket packet = new NotifyPacket(Messages.toast("log.runtime_toast", name.isEmpty() ? "?" : name, clip(reason, 120)));
        if (player != null) {
            ModNetwork.sendTo(player, packet);
            return;
        }
        if (host == null) {
            return;
        }
        for (ServerPlayer op : host.getPlayerList().getPlayers()) {
            if (op.hasPermissions(2)) {
                ModNetwork.sendTo(op, packet);
            }
        }
    }

    public static void clearRuntime() {
        synchronized (RUNTIME) {
            RUNTIME.clear();
        }
    }

    public static View capture(Map<String, ScriptSource> sources, List<String> parseErrors) {
        List<Issue> issues = new ArrayList<>();
        for (String raw : parseErrors) {
            if (issues.size() >= MAX_ISSUES) {
                break;
            }
            Matcher m = PARSE.matcher(raw);
            if (m.matches()) {
                issues.add(new Issue(m.group(1), Integer.parseInt(m.group(2)), m.group(3), false));
            } else {
                int colon = raw.indexOf(':');
                if (colon > 0) {
                    issues.add(new Issue(raw.substring(0, colon), 0, raw.substring(colon + 1).strip(), false));
                } else {
                    issues.add(new Issue("", 0, raw, false));
                }
            }
        }
        synchronized (RUNTIME) {
            for (Issue issue : RUNTIME) {
                if (issues.size() >= MAX_ISSUES) {
                    break;
                }
                issues.add(issue);
            }
        }
        Map<String, List<Integer>> linesByFile = new LinkedHashMap<>();
        for (Issue issue : issues) {
            if (!issue.file().isEmpty()) {
                linesByFile.computeIfAbsent(issue.file(), k -> new ArrayList<>()).add(issue.line());
            }
        }
        List<Slice> files = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> e : linesByFile.entrySet()) {
            if (files.size() >= MAX_FILES) {
                break;
            }
            ScriptSource source = sources.get(e.getKey());
            if (source == null) {
                files.add(new Slice(e.getKey(), 1, List.of()));
                continue;
            }
            files.add(window(e.getKey(), source.lines(), e.getValue()));
        }
        return new View(List.copyOf(issues), List.copyOf(files));
    }

    private static Slice window(String name, List<String> all, List<Integer> errorLines) {
        if (all.isEmpty()) {
            return new Slice(name, 1, List.of());
        }
        int start = 1;
        if (all.size() > WINDOW) {
            int focus = 1;
            for (int line : errorLines) {
                if (line > 0) {
                    focus = line;
                    break;
                }
            }
            start = Math.max(1, focus - 30);
            if (start + WINDOW - 1 > all.size()) {
                start = Math.max(1, all.size() - WINDOW + 1);
            }
        }
        int end = Math.min(all.size(), start + WINDOW - 1);
        List<String> lines = new ArrayList<>(end - start + 1);
        for (int i = start; i <= end; i++) {
            lines.add(clip(all.get(i - 1).replace('\t', ' '), LINE_CHARS));
        }
        return new Slice(name, start, lines);
    }

    private static String clip(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 1) + "…";
    }
}
