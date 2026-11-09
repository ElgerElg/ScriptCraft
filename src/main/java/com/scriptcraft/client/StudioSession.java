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
        docs.clear();
    }

    public static void journal(List<ScriptLog.Issue> nextIssues, List<ScriptLog.Slice> nextSlices) {
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
}
