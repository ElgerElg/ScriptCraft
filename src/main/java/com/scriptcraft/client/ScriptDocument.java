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
    /** Прямоугольное выделение (Alt и кнопка мыши): правка идёт в каждую строку диапазона. */
    public boolean box;
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
        box = false;
        scroll = 0;
        horiz = 0;
        undo.clear();
        redo.clear();
    }

    public void insert(String value) {
        if (!loaded || value.isEmpty()) {
            return;
        }
        if (box && anchorLine >= 0) {
            snapshot();
            if (!value.contains("\n") && !value.contains("\r")) {
                writeBox(value);
                return;
            }
            int[] span = boxSpan();
            writeBox("");
            box = false;
            anchorLine = -1;
            line = span[0];
            col = Math.min(span[2], lines.get(line).length());
        } else {
            snapshot();
            deleteSelection();
        }
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
        if (box && anchorLine >= 0) {
            snapshot();
            int[] span = boxSpan();
            if (span[2] != span[3]) {
                writeBox("");
            } else if (span[2] > 0) {
                eraseBoxColumn(span[0], span[1], span[2] - 1);
            }
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

    /** Стирает назад до пробела или отступа, сам разделитель не задевает. */
    public void deleteWord() {
        if (!loaded) {
            return;
        }
        if (box || anchorLine >= 0) {
            backspace();
            return;
        }
        if (col == 0 && line == 0) {
            return;
        }
        if (col == 0) {
            backspace();
            return;
        }
        String current = lines.get(line);
        int i = col;
        if (isGap(current.charAt(i - 1))) {
            while (i > 0 && isGap(current.charAt(i - 1))) {
                i--;
            }
        } else {
            while (i > 0 && !isGap(current.charAt(i - 1))) {
                i--;
            }
        }
        if (i == col) {
            return;
        }
        snapshot();
        lines.set(line, current.substring(0, i) + current.substring(col));
        col = i;
    }

    public void deleteForward() {
        if (!loaded) {
            return;
        }
        if (box && anchorLine >= 0) {
            backspace();
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
        if (box && anchorLine >= 0) {
            int[] span = boxSpan();
            if (row < span[0] || row > span[1] || span[2] == span[3]) {
                return null;
            }
            String text = lines.get(row);
            int from = Math.min(span[2], text.length());
            int to = Math.min(span[3], text.length());
            return from >= to ? null : new int[]{from, to};
        }
        Range range = range();
        if (range == null || row < range.fromLine || row > range.toLine) {
            return null;
        }
        int from = row == range.fromLine ? range.fromCol : 0;
        int to = row == range.toLine ? range.toCol : lines.get(row).length();
        return from == to ? null : new int[]{from, to};
    }

    public String selectedText() {
        if (box && anchorLine >= 0) {
            int[] span = boxSpan();
            StringBuilder out = new StringBuilder();
            for (int row = span[0]; row <= span[1]; row++) {
                if (row > span[0]) {
                    out.append('\n');
                }
                int[] piece = selectionOn(row);
                if (piece != null) {
                    out.append(lines.get(row), piece[0], piece[1]);
                }
            }
            return out.toString();
        }
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
            box = false;
        }
        line = newLine;
        col = newCol;
    }

    /** Начать или растянуть прямоугольник. Столбец может выходить за конец короткой строки. */
    public void moveBox(int newLine, int newCol) {
        newLine = Math.max(0, Math.min(lines.size() - 1, newLine));
        newCol = Math.max(0, newCol);
        if (anchorLine < 0 || !box) {
            anchorLine = line;
            anchorCol = col;
        }
        box = true;
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

    private static boolean isGap(char ch) {
        return ch == ' ' || ch == '\t';
    }

    /** fromLine, toLine, fromCol, toCol. */
    private int[] boxSpan() {
        int r1 = Math.max(0, Math.min(anchorLine, line));
        int r2 = Math.min(lines.size() - 1, Math.max(anchorLine, line));
        int c1 = Math.min(anchorCol, col);
        int c2 = Math.max(anchorCol, col);
        return new int[]{r1, r2, c1, c2};
    }

    /** Вписывает один и тот же кусок в каждую строку прямоугольника. */
    private void writeBox(String value) {
        int[] span = boxSpan();
        for (int row = span[0]; row <= span[1]; row++) {
            String text = lines.get(row);
            int from = span[2];
            int to = span[3];
            if (text.length() < from) {
                text = text + " ".repeat(from - text.length());
            }
            int a = Math.min(from, text.length());
            int b = Math.min(to, text.length());
            lines.set(row, text.substring(0, a) + value + text.substring(b));
        }
        int next = span[2] + value.length();
        anchorLine = span[0];
        line = span[1];
        anchorCol = next;
        col = next;
        box = true;
    }

    private void eraseBoxColumn(int fromLine, int toLine, int index) {
        for (int row = fromLine; row <= toLine; row++) {
            String text = lines.get(row);
            if (index < text.length()) {
                lines.set(row, text.substring(0, index) + text.substring(index + 1));
            }
        }
        anchorLine = fromLine;
        line = toLine;
        anchorCol = index;
        col = index;
        box = true;
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
        box = false;
    }

    private record Snap(String text, int line, int col, int anchorLine, int anchorCol) {
    }

    private record Range(int fromLine, int fromCol, int toLine, int toCol) {
    }
}
