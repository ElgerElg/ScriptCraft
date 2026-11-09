package com.scriptcraft.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Текст одного скрипта в студии: курсор, выделение, отмена и возврат. */
@OnlyIn(Dist.CLIENT)
public final class ScriptDocument {
    public final String name;
    public final List<String> lines = new ArrayList<>();
    public boolean loaded;
    public String saved = "";
    public int line;
    public int col;
    public int anchorLine = -1;
    public int anchorCol;
    public int scroll;
    public int horiz;
    private final ArrayDeque<Snap> undo = new ArrayDeque<>();
    private final ArrayDeque<Snap> redo = new ArrayDeque<>();

    public ScriptDocument(String name) {
        this.name = name;
        this.lines.add("");
    }

    public boolean dirty() {
        return loaded && !text().equals(saved);
    }

    public String text() {
        return String.join("\n", lines);
    }

    public void load(String raw) {
        saved = raw.replace("\r\n", "\n").replace('\r', '\n');
        lines.clear();
        if (saved.isEmpty()) {
            lines.add("");
        } else {
            int from = 0;
            for (int i = 0; i <= saved.length(); i++) {
                if (i == saved.length() || saved.charAt(i) == '\n') {
                    lines.add(saved.substring(from, i));
                    from = i + 1;
                }
            }
        }
        loaded = true;
        line = 0;
        col = 0;
        anchorLine = -1;
        scroll = 0;
        horiz = 0;
        undo.clear();
        redo.clear();
    }

    public void insert(String value) {
        if (!loaded || value.isEmpty()) {
            return;
        }
        snapshot();
        deleteSelection();
        String chunk = value.replace("\r\n", "\n").replace('\r', '\n');
        String current = lines.get(line);
        String left = current.substring(0, col);
        String right = current.substring(col);
        String[] parts = chunk.split("\n", -1);
        if (parts.length == 1) {
            lines.set(line, left + parts[0] + right);
            col += parts[0].length();
            return;
        }
        lines.set(line, left + parts[0]);
        for (int i = 1; i < parts.length - 1; i++) {
            lines.add(line + i, parts[i]);
        }
        line += parts.length - 1;
        col = parts[parts.length - 1].length();
        lines.add(line, parts[parts.length - 1] + right);
    }

    public void backspace() {
        if (!loaded) {
            return;
        }
        if (anchorLine >= 0) {
            snapshot();
            deleteSelection();
            return;
        }
        if (col == 0 && line == 0) {
            return;
        }
        snapshot();
        if (col > 0) {
            String current = lines.get(line);
            lines.set(line, current.substring(0, col - 1) + current.substring(col));
            col--;
            return;
        }
        String current = lines.remove(line);
        line--;
        col = lines.get(line).length();
        lines.set(line, lines.get(line) + current);
    }

    public void deleteForward() {
        if (!loaded) {
            return;
        }
        if (anchorLine >= 0) {
            snapshot();
            deleteSelection();
            return;
        }
        String current = lines.get(line);
        if (col < current.length()) {
            snapshot();
            lines.set(line, current.substring(0, col) + current.substring(col + 1));
            return;
        }
        if (line + 1 >= lines.size()) {
            return;
        }
        snapshot();
        String next = lines.remove(line + 1);
        lines.set(line, current + next);
    }

    public void selectAll() {
        anchorLine = 0;
        anchorCol = 0;
        line = lines.size() - 1;
        col = lines.get(line).length();
    }

    /** Кусок выделения на строке: [от, до) или null. */
    public int[] selectionOn(int row) {
        Range range = range();
        if (range == null || row < range.fromLine || row > range.toLine) {
            return null;
        }
        int from = row == range.fromLine ? range.fromCol : 0;
        int to = row == range.toLine ? range.toCol : lines.get(row).length();
        return from == to ? null : new int[]{from, to};
    }

    public String selectedText() {
        Range range = range();
        if (range == null) {
            return "";
        }
        if (range.fromLine == range.toLine) {
            return lines.get(range.fromLine).substring(range.fromCol, range.toCol);
        }
        StringBuilder out = new StringBuilder(lines.get(range.fromLine).substring(range.fromCol));
        for (int i = range.fromLine + 1; i < range.toLine; i++) {
            out.append('\n').append(lines.get(i));
        }
        out.append('\n').append(lines.get(range.toLine), 0, range.toCol);
        return out.toString();
    }

    public void move(int newLine, int newCol, boolean keep) {
        newLine = Math.max(0, Math.min(lines.size() - 1, newLine));
        newCol = Math.max(0, Math.min(lines.get(newLine).length(), newCol));
        if (keep) {
            if (anchorLine < 0) {
                anchorLine = line;
                anchorCol = col;
            }
        } else {
            anchorLine = -1;
        }
        line = newLine;
        col = newCol;
    }

    public void undo() {
        if (undo.isEmpty()) {
            return;
        }
        redo.addLast(capture());
        restore(undo.removeLast());
    }

    public void redo() {
        if (redo.isEmpty()) {
            return;
        }
        undo.addLast(capture());
        restore(redo.removeLast());
    }

    public void ensureVisible(int viewLines, int lineHeight) {
        if (line < scroll) {
            scroll = line;
        }
        int bottom = scroll + Math.max(1, viewLines) - 1;
        if (line > bottom) {
            scroll = line - viewLines + 1;
        }
        if (scroll < 0) {
            scroll = 0;
        }
    }

    private void deleteSelection() {
        Range range = range();
        if (range == null) {
            return;
        }
        String left = lines.get(range.fromLine).substring(0, range.fromCol);
        String right = lines.get(range.toLine).substring(range.toCol);
        if (range.fromLine == range.toLine) {
            lines.set(range.fromLine, left + right);
        } else {
            List<String> next = new ArrayList<>(lines.subList(0, range.fromLine));
            next.add(left + right);
            next.addAll(lines.subList(range.toLine + 1, lines.size()));
            lines.clear();
            lines.addAll(next);
        }
        line = range.fromLine;
        col = range.fromCol;
        anchorLine = -1;
    }

    private Range range() {
        if (anchorLine < 0) {
            return null;
        }
        int al = Math.max(0, Math.min(lines.size() - 1, anchorLine));
        int ac = Math.max(0, Math.min(lines.get(al).length(), anchorCol));
        if (al == line && ac == col) {
            return null;
        }
        if (al < line || (al == line && ac < col)) {
            return new Range(al, ac, line, col);
        }
        return new Range(line, col, al, ac);
    }

    private void snapshot() {
        Snap snap = capture();
        if (!undo.isEmpty() && undo.peekLast().text.equals(snap.text)) {
            return;
        }
        undo.addLast(snap);
        while (undo.size() > 60) {
            undo.removeFirst();
        }
        redo.clear();
    }

    private Snap capture() {
        return new Snap(text(), line, col, anchorLine, anchorCol);
    }

    private void restore(Snap snap) {
        lines.clear();
        int from = 0;
        String text = snap.text;
        if (text.isEmpty()) {
            lines.add("");
        } else {
            for (int i = 0; i <= text.length(); i++) {
                if (i == text.length() || text.charAt(i) == '\n') {
                    lines.add(text.substring(from, i));
                    from = i + 1;
                }
            }
        }
        line = Math.max(0, Math.min(lines.size() - 1, snap.line));
        col = Math.max(0, Math.min(lines.get(line).length(), snap.col));
        anchorLine = snap.anchorLine;
        anchorCol = snap.anchorCol;
    }

    private record Snap(String text, int line, int col, int anchorLine, int anchorCol) {
    }

    private record Range(int fromLine, int fromCol, int toLine, int toCol) {
    }
}
