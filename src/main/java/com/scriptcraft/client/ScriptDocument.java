package com.scriptcraft.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Текст одного скрипта или файла в студии (или просмотр картинки): курсор, выделение, отмена и возврат. */
@OnlyIn(Dist.CLIENT)
public final class ScriptDocument {
    public String name;
    /** true — файл вне scripts/ (сохраняется через StudioExplorerPacket.Action.WRITE_FILE). */
    public boolean genericFile;
    /** true — открыт файл картинки (.png/.jpg) для просмотра. */
    public boolean isImage;
    public ResourceLocation imageId;
    public int imageW;
    public int imageH;
    public final List<String> lines = new ArrayList<>();
    public boolean loaded;
    public String saved = "";
    /** Номер копии, которую сервер считает последней. */
    public long revision;
    /** Сервер включил совместную правку этого скрипта. */
    public boolean live;
    public int line;
    public int col;
    public int anchorLine = -1;
    public int anchorCol;
    /** Прямоугольное выделение (Alt и кнопка мыши): правка идёт в каждую строку диапазона. */
    public boolean box;
    public int scroll;
    public int horiz;
    /** Курсор сдвинулся правкой или стрелками: следующий кадр покажет его строку. Колесо этот флаг не ставит. */
    public boolean followCaret;
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

    /** Общее количество символов в документе (с учётом переносов строк). */
    public int charCount() {
        int total = Math.max(0, lines.size() - 1);
        for (String s : lines) {
            total += s.length();
        }
        return total;
    }

    /** true, если в файле преобладают отступы пробелами (иначе TAB). */
    public boolean usesSpacesIndent() {
        int tabs = 0;
        int spaces = 0;
        for (String s : lines) {
            if (s.isEmpty()) continue;
            char c = s.charAt(0);
            if (c == '\t') tabs++;
            else if (c == ' ') spaces++;
        }
        return spaces > tabs;
    }

    /**
     * Статистика выделения: {chars, selectedLines}, либо null если ничего не выделено.
     */
    public int[] selectionStats() {
        if (anchorLine < 0) {
            return null;
        }
        if (box) {
            int[] span = boxSpan();
            int rows = span[1] - span[0] + 1;
            int cols = span[3] - span[2];
            if (rows <= 1 && cols <= 0) {
                return null;
            }
            int chars = 0;
            for (int r = span[0]; r <= span[1]; r++) {
                String s = lines.get(r);
                int a = Math.min(span[2], s.length());
                int b = Math.min(span[3], s.length());
                chars += Math.max(0, b - a);
            }
            return new int[]{chars, rows};
        }
        Range r = range();
        if (r == null) {
            return null;
        }
        int selLines = r.toLine - r.fromLine + 1;
        int chars;
        if (r.fromLine == r.toLine) {
            chars = Math.max(0, r.toCol - r.fromCol);
        } else {
            chars = Math.max(0, lines.get(r.fromLine).length() - r.fromCol);
            for (int i = r.fromLine + 1; i < r.toLine; i++) {
                chars += 1 + lines.get(i).length();
            }
            chars += 1 + r.toCol;
        }
        return new int[]{chars, selLines};
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

    /** Подмена текста с сервера. Прокрутка и курсор остаются, если документ уже был открыт. */
    public void replaceFromServer(String raw, boolean disk) {
        List<String> prevLines = new ArrayList<>(lines);
        int keepLine = line;
        int keepCol = col;
        int keepAnchorLine = anchorLine;
        int keepAnchorCol = anchorCol;
        boolean keepBox = box;
        int keepScroll = scroll;
        int keepHoriz = horiz;
        String keepSaved = saved;
        boolean wasLoaded = loaded;
        load(raw);
        scroll = keepScroll;
        horiz = keepHoriz;
        if (!disk) {
            saved = wasLoaded ? keepSaved : "";
        }
        if (!wasLoaded) {
            return;
        }
        int delta = lineDelta(prevLines, lines, keepLine);
        keepLine += delta;
        line = Math.max(0, Math.min(keepLine, lines.size() - 1));
        col = Math.max(0, Math.min(keepCol, lines.get(line).length()));
        if (keepAnchorLine >= 0) {
            int anchorDelta = lineDelta(prevLines, lines, keepAnchorLine);
            anchorLine = Math.max(0, Math.min(keepAnchorLine + anchorDelta, lines.size() - 1));
            anchorCol = Math.max(0, Math.min(keepAnchorCol, lines.get(anchorLine).length()));
            box = keepBox;
        }
    }

    /** Сдвиг строки курсора, если выше (или прямо рядом) вставили или удалили строки. */
    private static int lineDelta(List<String> oldLines, List<String> newLines, int targetLine) {
        int diff = newLines.size() - oldLines.size();
        if (diff == 0) {
            return 0;
        }
        int prefix = 0;
        int limit = Math.min(oldLines.size(), newLines.size());
        while (prefix < limit && oldLines.get(prefix).equals(newLines.get(prefix))) {
            prefix++;
        }
        if (targetLine < prefix) {
            return 0;
        }
        if (diff > 0 && targetLine == prefix && prefix + diff < newLines.size()
                && oldLines.get(prefix).equals(newLines.get(prefix + diff))) {
            return diff;
        }
        if (targetLine > prefix) {
            return diff;
        }
        int suffix = 0;
        while (suffix < limit - prefix
                && oldLines.get(oldLines.size() - 1 - suffix).equals(newLines.get(newLines.size() - 1 - suffix))) {
            suffix++;
        }
        if (targetLine >= oldLines.size() - suffix) {
            return diff;
        }
        return 0;
    }

    /** Заменить ведущие пробелы (по 4 пробела) на TAB во всём документе. */
    public int spacesToTabs() {
        if (!loaded || isImage) return 0;
        snapshot();
        int changed = 0;
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            int lead = 0;
            while (lead < s.length() && (s.charAt(lead) == ' ' || s.charAt(lead) == '\t')) lead++;
            if (lead == 0) continue;
            String prefix = s.substring(0, lead);
            StringBuilder out = new StringBuilder();
            int sp = 0;
            for (int k = 0; k < prefix.length(); k++) {
                char c = prefix.charAt(k);
                if (c == ' ') {
                    sp++;
                    if (sp == 4) {
                        out.append('\t');
                        sp = 0;
                    }
                } else {
                    if (sp > 0) {
                        out.append('\t');
                        sp = 0;
                    }
                    out.append('\t');
                }
            }
            while (sp-- > 0) out.append(' ');
            String next = out + s.substring(lead);
            if (!next.equals(s)) {
                lines.set(i, next);
                changed++;
            }
        }
        col = Math.min(col, lines.get(line).length());
        return changed;
    }

    /** Заменить ведущие TAB на 4 пробела во всём документе. */
    public int tabsToSpaces() {
        if (!loaded || isImage) return 0;
        snapshot();
        int changed = 0;
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            int lead = 0;
            while (lead < s.length() && (s.charAt(lead) == ' ' || s.charAt(lead) == '\t')) lead++;
            if (lead == 0) continue;
            String next = s.substring(0, lead).replace("\t", "    ") + s.substring(lead);
            if (!next.equals(s)) {
                lines.set(i, next);
                changed++;
            }
        }
        col = Math.min(col, lines.get(line).length());
        return changed;
    }

    /** Перевод строки с сохранением отступа (пробелы и TAB) текущей строки. */
    public void insertNewLine() {
        insertNewLine(true);
    }

    public void insertNewLine(boolean autoIndent) {
        if (!loaded) {
            return;
        }
        if (!autoIndent || (box && anchorLine >= 0)) {
            insert("\n");
            return;
        }
        followCaret = true;
        snapshot();
        deleteSelection();
        String current = lines.get(line);
        int limit = Math.min(col, current.length());
        int indentEnd = 0;
        while (indentEnd < limit && isGap(current.charAt(indentEnd))) {
            indentEnd++;
        }
        String indent = current.substring(0, indentEnd);
        String left = current.substring(0, col);
        String right = current.substring(col);
        lines.set(line, left);
        line++;
        lines.add(line, indent + right);
        col = indent.length();
    }

    /** Закомментировать или раскомментировать текущую строку / выделенные строки (#). */
    public void toggleComment() {
        if (!loaded || lines.isEmpty()) {
            return;
        }
        followCaret = true;
        snapshot();
        Range r = range();
        int from = r == null ? line : r.fromLine;
        int to = r == null ? line : r.toLine;
        boolean allCommented = true;
        for (int i = from; i <= to; i++) {
            String trimmed = lines.get(i).stripLeading();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                allCommented = false;
                break;
            }
        }
        for (int i = from; i <= to; i++) {
            String s = lines.get(i);
            int lead = 0;
            while (lead < s.length() && isGap(s.charAt(lead))) {
                lead++;
            }
            if (allCommented) {
                if (lead < s.length() && s.charAt(lead) == '#') {
                    int drop = (lead + 1 < s.length() && s.charAt(lead + 1) == ' ') ? 2 : 1;
                    lines.set(i, s.substring(0, lead) + s.substring(lead + drop));
                    if (i == line) col = Math.max(0, col - drop);
                }
            } else {
                lines.set(i, s.substring(0, lead) + "# " + s.substring(lead));
                if (i == line) col += 2;
            }
        }
    }

    /** Дублирует текущую строку или выделенный диапазон строк ниже. */
    public void duplicateLine() {
        if (!loaded || lines.isEmpty()) {
            return;
        }
        followCaret = true;
        snapshot();
        Range r = range();
        int from = r == null ? line : r.fromLine;
        int to = r == null ? line : r.toLine;
        List<String> copy = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            copy.add(lines.get(i));
        }
        lines.addAll(to + 1, copy);
        int shift = copy.size();
        line += shift;
        if (anchorLine >= 0) {
            anchorLine += shift;
        }
    }

    /** Перемещает текущую строку или выделенные строки вверх (-1) или вниз (+1). */
    public void moveLines(int dir) {
        if (!loaded || lines.isEmpty() || dir == 0) {
            return;
        }
        Range r = range();
        int from = r == null ? line : r.fromLine;
        int to = r == null ? line : r.toLine;
        if (dir < 0 && from <= 0) return;
        if (dir > 0 && to >= lines.size() - 1) return;
        followCaret = true;
        snapshot();
        if (dir < 0) {
            String moved = lines.remove(from - 1);
            lines.add(to, moved);
            line--;
            if (anchorLine >= 0) anchorLine--;
        } else {
            String moved = lines.remove(to + 1);
            lines.add(from, moved);
            line++;
            if (anchorLine >= 0) anchorLine++;
        }
    }

    /** Сдвиг отступа выделенных строк (Tab / Shift+Tab). Возвращает true, если обработано. */
    public boolean indentSelection(boolean unindent) {
        if (!loaded || lines.isEmpty()) {
            return false;
        }
        Range r = range();
        if (!unindent && (r == null || r.fromLine == r.toLine)) {
            return false;
        }
        followCaret = true;
        snapshot();
        int from = r == null ? line : r.fromLine;
        int to = r == null ? line : r.toLine;
        for (int i = from; i <= to; i++) {
            String s = lines.get(i);
            if (unindent) {
                if (s.startsWith("\t")) {
                    lines.set(i, s.substring(1));
                    if (i == line) col = Math.max(0, col - 1);
                    if (i == anchorLine) anchorCol = Math.max(0, anchorCol - 1);
                } else {
                    int spaces = 0;
                    while (spaces < 4 && spaces < s.length() && s.charAt(spaces) == ' ') {
                        spaces++;
                    }
                    if (spaces > 0) {
                        lines.set(i, s.substring(spaces));
                        if (i == line) col = Math.max(0, col - spaces);
                        if (i == anchorLine) anchorCol = Math.max(0, anchorCol - spaces);
                    }
                }
            } else {
                lines.set(i, "\t" + s);
                if (i == line) col++;
                if (i == anchorLine) anchorCol++;
            }
        }
        return true;
    }

    /** Автозакрытие парных символов ("", {}, (), []) или пропуск закрывающего символа. */
    public boolean typeWithAutoClose(char ch) {
        if (!loaded || box) {
            return false;
        }
        String current = lines.get(line);
        // Если нажимаем закрывающий символ и он уже стоит прямо перед курсором — просто перешагиваем
        if ((ch == ')' || ch == ']' || ch == '}' || ch == '"') && anchorLine < 0
                && col < current.length() && current.charAt(col) == ch) {
            col++;
            followCaret = true;
            return true;
        }
        char close = switch (ch) {
            case '(' -> ')';
            case '[' -> ']';
            case '{' -> '}';
            case '"' -> '"';
            default -> 0;
        };
        if (close == 0) {
            return false;
        }
        followCaret = true;
        snapshot();
        if (anchorLine >= 0) {
            String sel = selectedText();
            deleteSelection();
            String cur = lines.get(line);
            String left = cur.substring(0, col);
            String right = cur.substring(col);
            lines.set(line, left + ch + sel + close + right);
            col += 1 + sel.length();
            return true;
        }
        String left = current.substring(0, col);
        String right = current.substring(col);
        lines.set(line, left + ch + close + right);
        col++;
        return true;
    }

    /** Backspace удаляет сразу обе пустые парные скобки/кавычки: () -> пусто. */
    public boolean backspaceEmptyPair() {
        if (!loaded || box || anchorLine >= 0 || col <= 0) {
            return false;
        }
        String current = lines.get(line);
        if (col >= current.length()) {
            return false;
        }
        char a = current.charAt(col - 1);
        char b = current.charAt(col);
        boolean pair = (a == '(' && b == ')') || (a == '[' && b == ']') || (a == '{' && b == '}') || (a == '"' && b == '"');
        if (!pair) {
            return false;
        }
        followCaret = true;
        snapshot();
        lines.set(line, current.substring(0, col - 1) + current.substring(col + 1));
        col--;
        return true;
    }

    /**
     * Ищет парную скобку или кавычку рядом с курсором.
     * Возвращает [line1, col1, line2, col2] или null.
     */
    public int[] matchingBracket() {
        if (!loaded || lines.isEmpty()) {
            return null;
        }
        String row = lines.get(line);
        int pos = -1;
        if (col < row.length() && isPairChar(row.charAt(col))) {
            pos = col;
        } else if (col > 0 && col - 1 < row.length() && isPairChar(row.charAt(col - 1))) {
            pos = col - 1;
        }
        if (pos < 0) {
            return null;
        }
        char ch = row.charAt(pos);
        if (ch == '"') {
            // Парная кавычка в этой же строке
            int countBefore = 0;
            for (int i = 0; i < pos; i++) {
                if (row.charAt(i) == '"' && (i == 0 || row.charAt(i - 1) != '\\')) {
                    countBefore++;
                }
            }
            if (countBefore % 2 == 0) {
                for (int i = pos + 1; i < row.length(); i++) {
                    if (row.charAt(i) == '"' && row.charAt(i - 1) != '\\') {
                        return new int[]{line, pos, line, i};
                    }
                }
            } else {
                for (int i = pos - 1; i >= 0; i--) {
                    if (row.charAt(i) == '"' && (i == 0 || row.charAt(i - 1) != '\\')) {
                        return new int[]{line, i, line, pos};
                    }
                }
            }
            return null;
        }
        boolean forward = ch == '(' || ch == '[' || ch == '{';
        char target = switch (ch) {
            case '(' -> ')';
            case '[' -> ']';
            case '{' -> '}';
            case ')' -> '(';
            case ']' -> '[';
            default -> '{';
        };
        int depth = 0;
        if (forward) {
            for (int r = line; r < lines.size(); r++) {
                String s = lines.get(r);
                int start = r == line ? pos : 0;
                for (int c = start; c < s.length(); c++) {
                    char d = s.charAt(c);
                    if (d == ch) depth++;
                    else if (d == target && --depth == 0) return new int[]{line, pos, r, c};
                }
            }
        } else {
            for (int r = line; r >= 0; r--) {
                String s = lines.get(r);
                int start = r == line ? pos : s.length() - 1;
                for (int c = start; c >= 0; c--) {
                    char d = s.charAt(c);
                    if (d == ch) depth++;
                    else if (d == target && --depth == 0) return new int[]{r, c, line, pos};
                }
            }
        }
        return null;
    }

    private static boolean isPairChar(char c) {
        return c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}' || c == '"';
    }

    /** Все совпадения в документе: [line, fromCol, toCol]. */
    public List<int[]> findAllMatches(String needle, boolean matchCase, boolean wholeWord) {
        List<int[]> out = new ArrayList<>();
        if (!loaded || needle == null || needle.isEmpty()) {
            return out;
        }
        String q = matchCase ? needle : needle.toLowerCase(java.util.Locale.ROOT);
        int len = q.length();
        for (int r = 0; r < lines.size(); r++) {
            String raw = lines.get(r);
            String hay = matchCase ? raw : raw.toLowerCase(java.util.Locale.ROOT);
            int from = 0;
            while (from <= hay.length() - len) {
                int at = hay.indexOf(q, from);
                if (at < 0) {
                    break;
                }
                if (!wholeWord || isWholeWord(raw, at, at + len)) {
                    out.add(new int[]{r, at, at + len});
                    from = at + len;
                } else {
                    from = at + 1;
                }
            }
        }
        return out;
    }

    /** Заменяет текущее выделение, если оно совпадает с needle. */
    public boolean replaceCurrent(String needle, String replacement, boolean matchCase, boolean wholeWord) {
        if (!loaded || needle == null || needle.isEmpty()) {
            return false;
        }
        String sel = selectedText();
        boolean matches = matchCase ? sel.equals(needle) : sel.equalsIgnoreCase(needle);
        if (!matches) {
            return false;
        }
        if (wholeWord) {
            Range r = range();
            if (r == null || r.fromLine != r.toLine || !isWholeWord(lines.get(r.fromLine), r.fromCol, r.toCol)) {
                return false;
            }
        }
        String rep = replacement == null ? "" : replacement;
        if (rep.isEmpty()) {
            backspace();
        } else {
            insert(rep);
        }
        return true;
    }

    /** Заменяет все совпадения одним шагом отмены (Ctrl+Z). */
    public int replaceAll(String needle, String replacement, boolean matchCase, boolean wholeWord) {
        if (!loaded || needle == null || needle.isEmpty()) {
            return 0;
        }
        String q = matchCase ? needle : needle.toLowerCase(java.util.Locale.ROOT);
        String rep = replacement == null ? "" : replacement;
        int len = q.length();
        int count = 0;
        List<String> next = new ArrayList<>(lines.size());
        for (String raw : lines) {
            String hay = matchCase ? raw : raw.toLowerCase(java.util.Locale.ROOT);
            StringBuilder sb = null;
            int pos = 0;
            while (pos <= hay.length() - len) {
                int at = hay.indexOf(q, pos);
                if (at < 0) {
                    break;
                }
                if (!wholeWord || isWholeWord(raw, at, at + len)) {
                    if (sb == null) {
                        sb = new StringBuilder(raw.length());
                    }
                    sb.append(raw, pos, at).append(rep);
                    count++;
                    pos = at + len;
                } else {
                    if (sb != null) {
                        sb.append(raw, pos, at + 1);
                    }
                    pos = at + 1;
                }
            }
            if (sb != null) {
                sb.append(raw, pos, raw.length());
                next.add(sb.toString());
            } else {
                next.add(raw);
            }
        }
        if (count == 0) {
            return 0;
        }
        followCaret = true;
        snapshot();
        lines.clear();
        lines.addAll(next);
        line = Math.max(0, Math.min(line, lines.size() - 1));
        col = Math.max(0, Math.min(col, lines.get(line).length()));
        anchorLine = -1;
        box = false;
        return count;
    }

    private static boolean isWholeWord(String s, int from, int to) {
        boolean leftOk = from <= 0 || !isWordChar(s.charAt(from - 1));
        boolean rightOk = to >= s.length() || !isWordChar(s.charAt(to));
        return leftOk && rightOk;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    public void insert(String value) {
        if (!loaded || value.isEmpty()) {
            return;
        }
        followCaret = true;
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
        followCaret = true;
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
        followCaret = true;
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

    /** Стирает вперёд до пробела или отступа. В конце строки убирает перевод, как обычный Delete. */
    public void deleteWordForward() {
        if (!loaded) {
            return;
        }
        followCaret = true;
        if (box || anchorLine >= 0) {
            deleteForward();
            return;
        }
        String current = lines.get(line);
        if (col >= current.length()) {
            deleteForward();
            return;
        }
        int i = col;
        if (isGap(current.charAt(i))) {
            while (i < current.length() && isGap(current.charAt(i))) {
                i++;
            }
        } else {
            while (i < current.length() && !isGap(current.charAt(i))) {
                i++;
            }
        }
        if (i == col) {
            return;
        }
        snapshot();
        lines.set(line, current.substring(0, col) + current.substring(i));
    }

    public void deleteForward() {
        if (!loaded) {
            return;
        }
        followCaret = true;
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
        followCaret = true;
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

    /** Ctrl+стрелки: предыдущее или следующее слово. Пробел и таб — граница, через строки тоже. */
    public void moveWord(boolean forward, boolean keep) {
        if (!loaded || lines.isEmpty()) {
            return;
        }
        int row = Math.max(0, Math.min(lines.size() - 1, line));
        int at = Math.max(0, Math.min(lines.get(row).length(), col));
        int[] next = forward ? nextWord(row, at) : prevWord(row, at);
        move(next[0], next[1], keep);
    }

    public void move(int newLine, int newCol, boolean keep) {
        followCaret = true;
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
        followCaret = true;
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
        followCaret = true;
        redo.addLast(capture());
        restore(undo.removeLast());
    }

    public void redo() {
        if (redo.isEmpty()) {
            return;
        }
        followCaret = true;
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

    private int[] nextWord(int row, int at) {
        String text = lines.get(row);
        if (at < text.length()) {
            if (!isGap(text.charAt(at))) {
                while (at < text.length() && !isGap(text.charAt(at))) {
                    at++;
                }
            }
            while (at < text.length() && isGap(text.charAt(at))) {
                at++;
            }
            if (at < text.length()) {
                return new int[]{row, at};
            }
        }
        while (row + 1 < lines.size()) {
            row++;
            text = lines.get(row);
            int i = 0;
            while (i < text.length() && isGap(text.charAt(i))) {
                i++;
            }
            if (i < text.length() || row + 1 >= lines.size()) {
                return new int[]{row, i};
            }
        }
        return new int[]{row, lines.get(row).length()};
    }

    private int[] prevWord(int row, int at) {
        String text = lines.get(row);
        while (at == 0) {
            if (row == 0) {
                return new int[]{0, 0};
            }
            row--;
            text = lines.get(row);
            at = text.length();
        }
        if (at > 0 && isGap(text.charAt(at - 1))) {
            while (at > 0 && isGap(text.charAt(at - 1))) {
                at--;
            }
        }
        while (at > 0 && !isGap(text.charAt(at - 1))) {
            at--;
        }
        return new int[]{row, at};
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
