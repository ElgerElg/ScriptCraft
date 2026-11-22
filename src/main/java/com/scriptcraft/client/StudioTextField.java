package com.scriptcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/**
 * Полноценное однострочное текстовое поле для всех системных полей ScriptCraft Studio:
 * - перемещение курсора мышкой (клик, перетаскивание выделения, двойной клик по слову);
 * - стрелки Влево/Вправо, Ctrl+Стрелки по словам, Home/End, Shift+выделение;
 * - Ctrl+A, Ctrl+C, Ctrl+X, Ctrl+V, Backspace/Delete и Ctrl+Backspace/Delete;
 * - автоматическая горизонтальная прокрутка длинных строк.
 */
@OnlyIn(Dist.CLIENT)
public final class StudioTextField {
    private String text = "";
    private int cursor = 0;
    private int selAnchor = -1;
    private int scrollPx = 0;
    private boolean dragging = false;
    private int lastX, lastY, lastW, lastH;
    private long lastClickMs = 0L;
    private int lastClickPos = -1;
    private boolean digitsOnly = false;
    private boolean filenameFilter = false;
    private int maxLen = 512;

    public StudioTextField() {
    }

    public StudioTextField digitsOnly(int maxLen) {
        this.digitsOnly = true;
        this.maxLen = maxLen;
        return this;
    }

    public StudioTextField filenameFilter(int maxLen) {
        this.filenameFilter = true;
        this.maxLen = maxLen;
        return this;
    }

    public StudioTextField maxLength(int maxLen) {
        this.maxLen = Math.max(1, maxLen);
        return this;
    }

    public String value() {
        return text;
    }

    public void setValue(String next) {
        String s = next == null ? "" : next;
        if (s.length() > maxLen) {
            s = s.substring(0, maxLen);
        }
        this.text = s;
        this.cursor = s.length();
        this.selAnchor = -1;
        this.scrollPx = 0;
    }

    public void selectAll() {
        this.selAnchor = 0;
        this.cursor = text.length();
    }

    public void clearSelection() {
        this.selAnchor = -1;
    }

    public boolean hasSelection() {
        return selAnchor >= 0 && selAnchor != cursor;
    }

    public String selectedText() {
        if (!hasSelection()) return "";
        int a = Math.min(selAnchor, cursor);
        int b = Math.max(selAnchor, cursor);
        return text.substring(a, b);
    }

    private void deleteSelection() {
        if (!hasSelection()) return;
        int a = Math.min(selAnchor, cursor);
        int b = Math.max(selAnchor, cursor);
        text = text.substring(0, a) + text.substring(b);
        cursor = a;
        selAnchor = -1;
    }

    public void insert(String s) {
        if (s == null || s.isEmpty()) return;
        StringBuilder filtered = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch < 32) continue;
            if (digitsOnly && (ch < '0' || ch > '9')) continue;
            if (filenameFilter && (ch == '/' || ch == '\\' || ch == ':')) continue;
            filtered.append(ch);
        }
        if (filtered.isEmpty()) return;
        deleteSelection();
        int room = maxLen - text.length();
        if (room <= 0) return;
        String ins = filtered.length() > room ? filtered.substring(0, room) : filtered.toString();
        text = text.substring(0, cursor) + ins + text.substring(cursor);
        cursor += ins.length();
    }

    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       boolean focused, String placeholder, int bgCol, int borderCol, int textCol, int mutedCol) {
        this.lastX = x;
        this.lastY = y;
        this.lastW = w;
        this.lastH = h;
        cursor = Math.max(0, Math.min(cursor, text.length()));
        if (selAnchor > text.length()) selAnchor = text.length();

        g.fill(x, y, x + w, y + h, bgCol);
        g.fill(x, y, x + w, y + 1, borderCol);
        g.fill(x, y + h - 1, x + w, y + h, borderCol);
        g.fill(x, y, x + 1, y + h, borderCol);
        g.fill(x + w - 1, y, x + w, y + h, borderCol);

        int padX = 4;
        int innerW = Math.max(8, w - padX * 2);
        int caretPx = font.width(text.substring(0, cursor));
        if (focused) {
            if (caretPx - scrollPx > innerW - 2) {
                scrollPx = caretPx - innerW + 6;
            } else if (caretPx - scrollPx < 0) {
                scrollPx = Math.max(0, caretPx - 6);
            }
        } else {
            scrollPx = 0;
        }
        int totalW = font.width(text);
        int maxScroll = Math.max(0, totalW - innerW + 4);
        scrollPx = Math.max(0, Math.min(scrollPx, maxScroll));

        int ty = y + (h - 8) / 2;
        g.enableScissor(x + 2, y + 1, x + w - 2, y + h - 1);
        if (text.isEmpty() && !focused && placeholder != null && !placeholder.isEmpty()) {
            g.drawString(font, placeholder, x + padX, ty, mutedCol, false);
        } else {
            if (focused && hasSelection()) {
                int a = Math.min(selAnchor, cursor);
                int b = Math.max(selAnchor, cursor);
                int x0 = x + padX + font.width(text.substring(0, a)) - scrollPx;
                int x1 = x + padX + font.width(text.substring(0, b)) - scrollPx;
                g.fill(x0, y + 2, Math.max(x0 + 1, x1), y + h - 2, 0x993A6EA5);
            }
            g.drawString(font, text, x + padX - scrollPx, ty, textCol, false);
            if (focused) {
                float pulse = 0.25f + 0.75f * (0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 180.0));
                int a = Math.round(255 * pulse);
                int cx = x + padX + caretPx - scrollPx;
                g.fill(cx, y + 2, cx + 1, y + h - 2, (a << 24) | 0x00F0B85B);
            }
        }
        g.disableScissor();
    }

    public boolean mouseClicked(Font font, int mx, int my, int button) {
        if (button != 0 || mx < lastX || mx >= lastX + lastW || my < lastY || my >= lastY + lastH) {
            dragging = false;
            return false;
        }
        int pos = indexAt(font, mx);
        long now = System.currentTimeMillis();
        if (pos == lastClickPos && now - lastClickMs < 350L && !text.isEmpty()) {
            // Двойной клик: выделяем слово целиком
            int a = pos;
            int b = pos;
            while (a > 0 && isWord(text.charAt(a - 1))) a--;
            while (b < text.length() && isWord(text.charAt(b))) b++;
            selAnchor = a;
            cursor = b;
            dragging = false;
        } else {
            if (Screen.hasShiftDown()) {
                if (selAnchor < 0) selAnchor = cursor;
                cursor = pos;
            } else {
                cursor = pos;
                selAnchor = pos;
            }
            dragging = true;
        }
        lastClickMs = now;
        lastClickPos = pos;
        return true;
    }

    public boolean mouseDragged(Font font, int mx) {
        if (!dragging) return false;
        cursor = indexAt(font, mx);
        return true;
    }

    public void mouseReleased() {
        dragging = false;
        if (selAnchor == cursor) {
            selAnchor = -1;
        }
    }

    public boolean keyPressed(int key) {
        boolean ctrl = Screen.hasControlDown();
        boolean shift = Screen.hasShiftDown();

        if (ctrl && key == GLFW.GLFW_KEY_A) {
            selectAll();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_C) {
            if (hasSelection()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
            }
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_X) {
            if (hasSelection()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
                deleteSelection();
            }
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null) {
                insert(clip.replace("\r", "").replace("\n", ""));
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT) {
            int next = ctrl ? prevWord(cursor) : Math.max(0, cursor - 1);
            moveCursor(next, shift);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            int next = ctrl ? nextWord(cursor) : Math.min(text.length(), cursor + 1);
            moveCursor(next, shift);
            return true;
        }
        if (key == GLFW.GLFW_KEY_HOME) {
            moveCursor(0, shift);
            return true;
        }
        if (key == GLFW.GLFW_KEY_END) {
            moveCursor(text.length(), shift);
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (hasSelection()) {
                deleteSelection();
            } else if (cursor > 0) {
                int from = ctrl ? prevWord(cursor) : cursor - 1;
                text = text.substring(0, from) + text.substring(cursor);
                cursor = from;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            if (hasSelection()) {
                deleteSelection();
            } else if (cursor < text.length()) {
                int to = ctrl ? nextWord(cursor) : cursor + 1;
                text = text.substring(0, cursor) + text.substring(to);
            }
            return true;
        }
        return false;
    }

    public boolean charTyped(char ch) {
        if (ch >= 32 && !Screen.hasControlDown()) {
            insert(String.valueOf(ch));
            return true;
        }
        return false;
    }

    private void moveCursor(int next, boolean extend) {
        if (extend) {
            if (selAnchor < 0) selAnchor = cursor;
            cursor = next;
            if (selAnchor == cursor) selAnchor = -1;
        } else {
            if (hasSelection()) {
                // Без Shift стрелка снимает выделение в нужную сторону
                cursor = next < cursor ? Math.min(selAnchor, cursor) : Math.max(selAnchor, cursor);
            } else {
                cursor = next;
            }
            selAnchor = -1;
        }
    }

    private int indexAt(Font font, int mx) {
        int local = mx - (lastX + 4) + scrollPx;
        if (local <= 0) return 0;
        int best = 0;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i <= text.length(); i++) {
            int dist = Math.abs(font.width(text.substring(0, i)) - local);
            if (dist > bestDist) break;
            bestDist = dist;
            best = i;
        }
        return best;
    }

    private int prevWord(int from) {
        int i = Math.max(0, from);
        while (i > 0 && !isWord(text.charAt(i - 1))) i--;
        while (i > 0 && isWord(text.charAt(i - 1))) i--;
        return i;
    }

    private int nextWord(int from) {
        int i = Math.min(text.length(), from);
        while (i < text.length() && isWord(text.charAt(i))) i++;
        while (i < text.length() && !isWord(text.charAt(i))) i++;
        return i;
    }

    private static boolean isWord(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
