package com.scriptcraft.client;

import com.scriptcraft.script.ScriptLog;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Окно студии живёт, пока игрок на сервере: вкладки, размер и какая страница открыта.
 * Выход из мира всё сбрасывает, следующий заход снова начинается с главной.
 */
@OnlyIn(Dist.CLIENT)
public final class StudioSession {
    public static final String HOME = "home";
    public static final String JOURNAL = "journal";

    public static boolean maximized = true;
    public static boolean minimized;
    public static boolean placed;
    public static int x;
    public static int y;
    public static int w = 780;
    public static int h = 460;
    public static String active = HOME;
    public static boolean opened;
    public static List<String> catalog = List.of();
    public static List<ScriptLog.Issue> issues = List.of();
    public static List<ScriptLog.Slice> slices = List.of();
    /** Строки под активными ошибками: перезагрузки, старые ошибки, уведомления. */
    public static final List<Note> history = new ArrayList<>();

    /** Одна строка истории. Код хранится, чтобы старую ошибку можно было открыть справа. */
    public static final class Note {
        public final String title;
        public final String body;
        public final String file;
        public final int line;
        public final int firstLine;
        public final List<String> lines;

        public Note(String title, String body, String file, int line, int firstLine, List<String> lines) {
            this.title = title;
            this.body = body;
            this.file = file == null ? "" : file;
            this.line = line;
            this.firstLine = firstLine;
            this.lines = lines == null ? List.of() : lines;
        }
    }
    public static final Map<String, ScriptDocument> docs = new LinkedHashMap<>();

    private StudioSession() {
    }

    public static void reset() {
        maximized = true;
        minimized = false;
        placed = false;
        active = HOME;
        opened = false;
        catalog = List.of();
        issues = List.of();
        slices = List.of();
        history.clear();
        docs.clear();
    }

    public static void journal(List<ScriptLog.Issue> nextIssues, List<ScriptLog.Slice> nextSlices) {
        for (ScriptLog.Issue old : issues) {
            boolean still = false;
            for (ScriptLog.Issue next : nextIssues) {
                if (next.file().equals(old.file()) && next.line() == old.line() && next.reason().equals(old.reason())) {
                    still = true;
                    break;
                }
            }
            if (!still) {
                ScriptLog.Slice slice = null;
                for (ScriptLog.Slice candidate : slices) {
                    if (candidate.name().equals(old.file())) {
                        slice = candidate;
                        break;
                    }
                }
                String title = old.file().isEmpty() ? old.reason() : old.file() + ":" + old.line();
                history.add(new Note(title, old.reason(), old.file(), old.line(),
                        slice == null ? 1 : slice.firstLine(),
                        slice == null ? List.of() : List.copyOf(slice.lines())));
                while (history.size() > 40) {
                    history.remove(0);
                }
            }
        }
        issues = List.copyOf(nextIssues);
        slices = List.copyOf(nextSlices);
        if (!opened) {
            opened = true;
            active = issues.isEmpty() ? HOME : JOURNAL;
        }
    }

    public static ScriptDocument openScript(String name) {
        ScriptDocument existing = docs.get(name);
        if (existing != null) {
            active = name;
            return existing;
        }
        ScriptDocument created = new ScriptDocument(name);
        docs.put(name, created);
        active = name;
        return created;
    }

    public static void acceptText(String name, String text) {
        ScriptDocument doc = docs.get(name);
        if (doc == null || doc.dirty()) {
            return;
        }
        doc.load(text);
    }

    public static void catalog(List<String> names) {
        catalog = new ArrayList<>(names);
    }

    /** Одна строка истории. Повтор подряд не копится. */
    public static void remember(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String plain = text.replaceAll("(?i)§.", "").replaceAll("(?i)&[0-9a-fk-or]", "").strip();
        if (plain.isEmpty()) {
            return;
        }
        if (!history.isEmpty() && history.get(history.size() - 1).body.equals(plain)) {
            return;
        }
        history.add(new Note(plain, plain, "", 0, 1, List.of()));
        while (history.size() > 40) {
            history.remove(0);
        }
    }
}
