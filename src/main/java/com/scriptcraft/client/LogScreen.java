package com.scriptcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.scriptcraft.lang.Messages;
import com.scriptcraft.script.ScriptLog;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Журнал ошибок: слева список, справа исходник только для чтения.
 * Строка с ошибкой подсвечена, снизу — причина. Шестерёнка открывает настройки
 * клавиши и всплывающих уведомлений. Ванильных кнопок нет.
 */
@OnlyIn(Dist.CLIENT)
public class LogScreen extends Screen {
    private static final int BG = 0xF010141C;
    private static final int PANEL = 0xF0181E28;
    private static final int EDGE = 0xFF3A4254;
    private static final int GOLD = 0xFFE2B657;
    private static final int TEXT = 0xFFE7E9EF;
    private static final int MUTED = 0xFF8E97A8;
    private static final int ROW = 0xFF222A38;
    private static final int ROW_ON = 0xFF2E3A4E;
    private static final int BAD = 0x66C45252;
    private static final int BAD_ON = 0xAAD25A5A;
    private static final int LINE_H = 11;
    private static final int ITEM_H = 26;

    private final List<ScriptLog.Issue> issues;
    private final List<ScriptLog.Slice> files;
    private int selected;
    private int listScroll;
    private int codeScroll;
    private boolean settings;
    private boolean listening;
    private int dragging;

    private int px, py, pw, ph;
    private int listX, listY, listW, listH;
    private int codeX, codeY, codeW, codeH;
    private int gearX, gearY, closeX, closeY;

    public LogScreen(List<ScriptLog.Issue> issues, List<ScriptLog.Slice> files) {
        super(Component.literal("ScriptCraft"));
        this.issues = issues;
        this.files = files;
        this.selected = issues.isEmpty() ? -1 : 0;
    }

    @Override
    protected void init() {
        if (selected >= 0) {
            layout();
            select(selected);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        layout();
        g.fill(0, 0, this.width, this.height, 0xA0080A10);
        g.fill(px, py, px + pw, py + ph, BG);
        border(g, px, py, pw, ph, EDGE);
        g.drawString(this.font, Messages.get("log.title"), px + 12, py + 10, GOLD, false);
        g.drawString(this.font, ClientUiSettings.keyLabel(), px + 12 + this.font.width(Messages.get("log.title")) + 10, py + 10, MUTED, false);
        drawGear(g, gearX + 8, gearY + 8, hit(mouseX, mouseY, gearX, gearY, 16, 16) || settings ? GOLD : MUTED);
        drawClose(g, closeX, closeY, hit(mouseX, mouseY, closeX, closeY, 16, 16));
        if (settings) {
            renderSettings(g, mouseX, mouseY);
        } else {
            renderJournal(g, mouseX, mouseY);
        }
    }

    private void renderJournal(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(listX, listY, listX + listW, listY + listH, PANEL);
        g.fill(codeX, codeY, codeX + codeW, codeY + codeH + 28, PANEL);
        if (issues.isEmpty()) {
            g.drawString(this.font, Messages.get("log.empty"), listX + 10, listY + 12, MUTED, false);
            return;
        }
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        int y = listY + 4 - listScroll;
        for (int i = 0; i < issues.size(); i++) {
            ScriptLog.Issue issue = issues.get(i);
            if (y + ITEM_H >= listY && y <= listY + listH) {
                g.fill(listX + 4, y, listX + listW - 4, y + ITEM_H - 2, i == selected ? ROW_ON : ROW);
                if (i == selected) {
                    g.fill(listX + 4, y, listX + 6, y + ITEM_H - 2, GOLD);
                }
                String where = issue.file().isEmpty() ? "—" : issue.file();
                if (issue.line() > 0) {
                    where = where + ":" + issue.line();
                }
                g.drawString(this.font, trim(where, listW - 20), listX + 12, y + 3, i == selected ? GOLD : TEXT, false);
                String kind = (issue.runtime() ? Messages.get("log.runtime") + ": " : "") + issue.reason();
                g.drawString(this.font, trim(kind, listW - 20), listX + 12, y + 13, MUTED, false);
            }
            y += ITEM_H;
        }
        g.disableScissor();

        ScriptLog.Issue current = issues.get(selected);
        ScriptLog.Slice slice = slice(current.file());
        int reasonY = codeY + codeH;
        g.enableScissor(codeX, codeY, codeX + codeW, codeY + codeH);
        if (slice == null || slice.lines().isEmpty()) {
            g.drawString(this.font, Messages.get("log.no_file"), codeX + 10, codeY + 10, MUTED, false);
        } else {
            int lineY = codeY + 4 - codeScroll;
            for (int i = 0; i < slice.lines().size(); i++) {
                int number = slice.firstLine() + i;
                if (lineY + LINE_H >= codeY && lineY <= codeY + codeH) {
                    boolean bad = number == current.line();
                    boolean any = isErrorLine(current.file(), number);
                    if (any) {
                        g.fill(codeX + 2, lineY - 1, codeX + codeW - 2, lineY + LINE_H - 1, bad ? BAD_ON : BAD);
                    }
                    String num = Integer.toString(number);
                    g.drawString(this.font, num, codeX + 36 - this.font.width(num), lineY, bad ? GOLD : MUTED, false);
                    String body = slice.lines().get(i);
                    int color = body.stripLeading().startsWith("#") ? MUTED : TEXT;
                    g.drawString(this.font, this.font.plainSubstrByWidth(body, codeW - 52), codeX + 44, lineY, color, false);
                }
                lineY += LINE_H;
            }
        }
        g.disableScissor();
        g.fill(codeX, reasonY, codeX + codeW, reasonY + 28, 0xFF24181A);
        String reason = Messages.get("log.reason") + ": " + current.reason();
        if (current.line() > 0 && (slice == null || current.line() < slice.firstLine()
                || current.line() >= slice.firstLine() + slice.lines().size())) {
            reason = reason + "  " + Messages.get("log.line_missing");
        }
        g.drawString(this.font, trim(reason, codeW - 16), codeX + 8, reasonY + 10, 0xFFF0C2C2, false);
    }

    private void renderSettings(GuiGraphics g, int mouseX, int mouseY) {
        int x = px + 16;
        int y = py + 36;
        g.drawString(this.font, Messages.get("log.settings"), x, y, GOLD, false);
        y += 20;
        g.drawString(this.font, Messages.get("log.key"), x, y, MUTED, false);
        int boxX = x + 110;
        boolean hot = listening || hit(mouseX, mouseY, boxX, y - 3, 160, 16);
        g.fill(boxX, y - 3, boxX + 160, y + 13, hot ? ROW_ON : ROW);
        String label = listening ? Messages.get("log.key_wait") : ClientUiSettings.keyLabel();
        g.drawString(this.font, label, boxX + 6, y, listening ? GOLD : TEXT, false);
        y += 28;
        y = slider(g, mouseX, mouseY, x, y, 1, Messages.get("log.time"),
                (ClientUiSettings.seconds() - 1.5f) / (10f - 1.5f),
                String.format(java.util.Locale.ROOT, "%.1f s", ClientUiSettings.seconds()));
        y = slider(g, mouseX, mouseY, x, y, 2, Messages.get("log.size"),
                (ClientUiSettings.scale() - 0.7f) / (1.5f - 0.7f),
                Math.round(ClientUiSettings.scale() * 100f) + "%");
        y += 6;
        g.drawString(this.font, Messages.get("log.place"), x, y, MUTED, false);
        y += 16;
        int chip = 0;
        chip = placeChip(g, mouseX, mouseY, x, y, chip, ClientUiSettings.Place.CHAT, Messages.get("log.place.chat"));
        chip = placeChip(g, mouseX, mouseY, x + 120, y, chip, ClientUiSettings.Place.TOP_LEFT, Messages.get("log.place.tl"));
        int row2 = pw < 520 ? y + 22 : y;
        int x3 = pw < 520 ? x : x + 240;
        int x4 = pw < 520 ? x + 120 : x + 360;
        chip = placeChip(g, mouseX, mouseY, x3, row2, chip, ClientUiSettings.Place.TOP_RIGHT, Messages.get("log.place.tr"));
        placeChip(g, mouseX, mouseY, x4, row2, chip, ClientUiSettings.Place.BOTTOM_RIGHT, Messages.get("log.place.br"));
        y += 36;
        drawSample(g, x, y);
    }

    private int slider(GuiGraphics g, int mouseX, int mouseY, int x, int y, int id, String caption, float t, String value) {
        g.drawString(this.font, caption, x, y, MUTED, false);
        g.drawString(this.font, value, x + pw - 90, y, TEXT, false);
        int barX = x;
        int barY = y + 14;
        int barW = Math.min(360, pw - 48);
        g.fill(barX, barY, barX + barW, barY + 4, EDGE);
        int knob = barX + Math.round(t * (barW - 8));
        g.fill(knob, barY - 3, knob + 8, barY + 7, dragging == id || hit(mouseX, mouseY, barX, barY - 4, barW, 12) ? GOLD : TEXT);
        rememberSlider(id, barX, barY, barW);
        return y + 28;
    }

    private int slider1X, slider1W, slider1Y, slider2X, slider2W, slider2Y;

    private void rememberSlider(int id, int x, int y, int w) {
        if (id == 1) {
            slider1X = x;
            slider1Y = y;
            slider1W = w;
        } else {
            slider2X = x;
            slider2Y = y;
            slider2W = w;
        }
    }

    private final int[] chipX = new int[4];
    private final int[] chipY = new int[4];

    private int placeChip(GuiGraphics g, int mouseX, int mouseY, int x, int y, int index, ClientUiSettings.Place place, String label) {
        chipX[index] = x;
        chipY[index] = y;
        boolean on = ClientUiSettings.place() == place;
        g.fill(x, y, x + 110, y + 16, on || hit(mouseX, mouseY, x, y, 110, 16) ? ROW_ON : ROW);
        if (on) {
            g.fill(x, y, x + 2, y + 16, GOLD);
        }
        g.drawString(this.font, trim(label, 100), x + 6, y + 4, on ? GOLD : TEXT, false);
        return index + 1;
    }

    private void drawSample(GuiGraphics g, int x, int y) {
        float scale = ClientUiSettings.scale();
        String text = Messages.branded(Messages.get("log.sample"));
        int textW = Math.min(260, this.font.width(text));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.fill(0, 0, textW + 12, 16, 0xF0141820);
        g.fill(0, 0, 2, 16, GOLD);
        g.drawString(this.font, text, 6, 4, TEXT, false);
        g.pose().popPose();
    }

    private void layout() {
        pw = Math.min(this.width - 32, 760);
        ph = Math.min(this.height - 32, 440);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        gearX = px + pw - 44;
        closeX = px + pw - 24;
        gearY = closeY = py + 6;
        listX = px + 10;
        listY = py + 30;
        listW = 220;
        listH = ph - 42;
        codeX = listX + listW + 8;
        codeY = listY;
        codeW = px + pw - 10 - codeX;
        codeH = listH - 28;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        layout();
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (hit(mx, my, closeX, closeY, 16, 16)) {
            onClose();
            return true;
        }
        if (hit(mx, my, gearX, gearY, 16, 16)) {
            settings = !settings;
            listening = false;
            dragging = 0;
            return true;
        }
        if (!settings) {
            if (hit(mx, my, listX, listY, listW, listH)) {
                int index = (my - listY + listScroll - 4) / ITEM_H;
                if (index >= 0 && index < issues.size()) {
                    select(index);
                }
                return true;
            }
            return true;
        }
        int boxX = px + 16 + 110;
        int boxY = py + 36 + 20 - 3;
        if (hit(mx, my, boxX, boxY, 160, 16)) {
            listening = true;
            return true;
        }
        if (hit(mx, my, slider1X, slider1Y - 4, slider1W, 12)) {
            dragging = 1;
            applySlider(1, mx);
            return true;
        }
        if (hit(mx, my, slider2X, slider2Y - 4, slider2W, 12)) {
            dragging = 2;
            applySlider(2, mx);
            return true;
        }
        clickPlace(mx, my, 0, ClientUiSettings.Place.CHAT);
        clickPlace(mx, my, 1, ClientUiSettings.Place.TOP_LEFT);
        clickPlace(mx, my, 2, ClientUiSettings.Place.TOP_RIGHT);
        clickPlace(mx, my, 3, ClientUiSettings.Place.BOTTOM_RIGHT);
        return true;
    }

    private void clickPlace(int mx, int my, int index, ClientUiSettings.Place place) {
        if (hit(mx, my, chipX[index], chipY[index], 110, 16)) {
            ClientUiSettings.setPlace(place);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging != 0) {
            applySlider(dragging, (int) mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = 0;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void applySlider(int id, int mouseX) {
        if (id == 1 && slider1W > 8) {
            float t = (mouseX - slider1X) / (float) (slider1W - 8);
            ClientUiSettings.setSeconds(1.5f + clamp01(t) * (10f - 1.5f));
        } else if (id == 2 && slider2W > 8) {
            float t = (mouseX - slider2X) / (float) (slider2W - 8);
            ClientUiSettings.setScale(0.7f + clamp01(t) * (1.5f - 0.7f));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        layout();
        int step = (int) Math.signum(delta) * -LINE_H * 3;
        if (!settings && mouseX >= codeX) {
            codeScroll = clampScroll(codeScroll + step, codeHeight(), codeH);
        } else if (!settings) {
            listScroll = clampScroll(listScroll + step, issues.size() * ITEM_H, listH);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listening) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                listening = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_F3) {
                return true;
            }
            long window = this.minecraft != null ? this.minecraft.getWindow().getWindow() : 0L;
            boolean f3 = window != 0L && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_F3) == GLFW.GLFW_PRESS;
            ClientUiSettings.setOpenKey(keyCode, f3);
            listening = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (settings) {
                settings = false;
                return true;
            }
            onClose();
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void select(int index) {
        selected = index;
        ScriptLog.Issue issue = issues.get(index);
        ScriptLog.Slice slice = slice(issue.file());
        if (slice != null && issue.line() > 0) {
            int row = issue.line() - slice.firstLine();
            codeScroll = clampScroll(row * LINE_H - codeH / 3, codeHeight(), codeH);
        } else {
            codeScroll = 0;
        }
    }

    private int codeHeight() {
        ScriptLog.Issue current = selected < 0 ? null : issues.get(selected);
        ScriptLog.Slice slice = current == null ? null : slice(current.file());
        return slice == null ? 0 : slice.lines().size() * LINE_H;
    }

    private ScriptLog.Slice slice(String file) {
        for (ScriptLog.Slice slice : files) {
            if (slice.name().equals(file)) {
                return slice;
            }
        }
        return null;
    }

    private boolean isErrorLine(String file, int number) {
        for (ScriptLog.Issue issue : issues) {
            if (issue.line() == number && issue.file().equals(file)) {
                return true;
            }
        }
        return false;
    }

    private String trim(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(8, width - 8)) + "…";
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void drawClose(GuiGraphics g, int x, int y, boolean hot) {
        int c = hot ? 0xFFE2B657 : 0xFF8E97A8;
        g.pose().pushPose();
        g.pose().translate(x + 8, y + 8, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(45));
        g.fill(-5, -1, 5, 1, c);
        g.fill(-1, -5, 1, 5, c);
        g.pose().popPose();
    }

    private static void drawGear(GuiGraphics g, int cx, int cy, int color) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        for (int i = 0; i < 8; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 45f));
            g.fill(-1, -8, 1, -4, color);
            pose.popPose();
        }
        g.fill(-3, -3, 3, 3, color);
        g.fill(-1, -1, 1, 1, BG);
        pose.popPose();
    }

    private static boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private static int clampScroll(int value, int content, int view) {
        int max = Math.max(0, content - view + 8);
        return Math.max(0, Math.min(max, value));
    }

    private static float clamp01(float t) {
        return Math.max(0f, Math.min(1f, t));
    }
}
