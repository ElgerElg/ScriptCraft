package com.scriptcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.scriptcraft.ModPaths;
import com.scriptcraft.lang.Messages;
import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.network.ScriptDataPacket;
import com.scriptcraft.network.ScriptDeletePacket;
import com.scriptcraft.network.ScriptReloadPacket;
import com.scriptcraft.network.LogRequestPacket;
import com.scriptcraft.network.ScriptListPacket;
import com.scriptcraft.script.ScriptLog;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Окно мода на время сессии: главная, журнал и вкладки скриптов сервера.
 * По умолчанию на весь экран. Свернуть оставляет заголовок, закрытие — только кнопкой или Esc;
 * снова открывается тем же биндом. В окне его двигают за заголовок и тянут за края.
 */
@OnlyIn(Dist.CLIENT)
public class StudioScreen extends Screen {
    private static final int BG = 0xF0141820;
    private static final int BAR = 0xF01C2430;
    private static final int EDGE = 0xFF3C4658;
    private static final int GOLD = 0xFFE2B657;
    private static final int TEXT = 0xFFE7E9EF;
    private static final int MUTED = 0xFF8E97A8;
    private static final int ROW = 0xFF222A38;
    private static final int YELLOW = 0x66C4A24A;
    private static final int BLUE = 0x663A6EA5;
    private static final int LINE_H = 11;
    private static final int TITLE = 22;
    private static final int MENU_H = 16;
    private static final int TOOL_H = 20;

    private enum Drag { NONE, MOVE, N, S, E, W, NE, NW, SE, SW, TEXT }

    private String menu;
    private boolean catalog;
    private boolean naming;
    private String nameBuf = "";
    private boolean findOn;
    private boolean findFocus;
    private String find = "";
    private boolean settings;
    private boolean listening;
    private Drag drag = Drag.NONE;
    private int grabX, grabY, originX, originY, originW, originH;
    private int journal = 0;
    private int listScroll;
    private int codeScroll;
    private int slider;
    private String tip;
    private int tipX, tipY;
    /** close или delete: подтверждение. Закрытие всего окна его не спрашивает. */
    private String ask;
    private String askName;
    private int askYesX, askNoX, askY;
    /** Ссылки главной, собранные в последнем кадре. */
    private final List<HomeLink> homeLinks = new ArrayList<>();
    private TexturePreview.Image preview;
    private int previewX, previewY;

    private int px, py, pw, ph;

    public StudioScreen() {
        super(Component.literal("ScriptCraft"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        tip = null;
        preview = null;
        layout();
        g.fill(0, 0, this.width, this.height, 0x66080A10);
        g.fill(px, py, px + pw, py + ph, BG);
        border(g, px, py, pw, ph, EDGE);
        renderTitle(g, mouseX, mouseY);
        if (StudioSession.minimized) {
            drawTip(g);
            return;
        }
        renderMenuRow(g, mouseX, mouseY);
        if (script() != null && !settings) {
            renderToolbar(g, mouseX, mouseY);
        }
        if (settings) {
            renderSettings(g, mouseX, mouseY);
        } else if (StudioSession.HOME.equals(StudioSession.active)) {
            renderHome(g, mouseX, mouseY);
        } else if (StudioSession.JOURNAL.equals(StudioSession.active)) {
            renderJournal(g, mouseX, mouseY);
        } else if (script() != null) {
            renderEditor(g, mouseX, mouseY);
        }
        if (menu != null) {
            renderMenu(g, mouseX, mouseY);
        }
        if (catalog) {
            renderCatalog(g, mouseX, mouseY);
        }
        if (naming) {
            renderNameBox(g);
        }
        if (ask != null) {
            renderAsk(g, mouseX, mouseY);
        }
        drawTip(g);
    }

    private void renderTitle(GuiGraphics g, int mx, int my) {
        g.fill(px, py, px + pw, py + TITLE, BAR);
        g.drawString(this.font, "ScriptCraft", px + 8, py + 7, GOLD, false);
        int x = px + 8 + this.font.width("ScriptCraft") + 10;
        int limit = px + pw - 78;
        x = tab(g, mx, my, x, Messages.get("studio.home"), StudioSession.HOME, false);
        String journalLabel = Messages.get("studio.journal");
        if (!StudioSession.issues.isEmpty()) {
            journalLabel = journalLabel + " " + StudioSession.issues.size();
        }
        x = tab(g, mx, my, x, journalLabel, StudioSession.JOURNAL, false);
        List<String> names = new ArrayList<>(StudioSession.docs.keySet());
        for (String name : names) {
            if (x > limit - 70) {
                break;
            }
            x = tab(g, mx, my, x, shortName(name), name, true);
        }
        boolean plusHot = hover(mx, my, x, py + 4, 14, 14);
        g.fill(x, py + 4, x + 14, py + 14, plusHot || catalog ? 0xFF2E3A4E : 0xFF222A38);
        g.drawString(this.font, "+", x + 4, py + 6, GOLD, false);
        plusX = x;
        int by = py + 4;
        windowButton(g, mx, my, px + pw - 66, by, "gear");
        windowButton(g, mx, my, px + pw - 48, by, "min");
        windowButton(g, mx, my, px + pw - 30, by, StudioSession.maximized ? "restore" : "max");
        windowButton(g, mx, my, px + pw - 16, by, "close");
    }

    private int plusX;

    private int tab(GuiGraphics g, int mx, int my, int x, String label, String id, boolean closable) {
        boolean dirty = dirty(id);
        int tw = tabInner(label, closable, dirty);
        boolean on = id.equals(StudioSession.active);
        boolean hot = hover(mx, my, x, py + 3, tw, TITLE - 5);
        g.fill(x, py + 3, x + tw, py + TITLE - 1, on ? 0xFF2C3544 : hot ? 0xFF26303C : 0xFF1A212C);
        if (on) {
            g.fill(x, py + TITLE - 2, x + tw, py + TITLE - 1, GOLD);
        }
        int textX = x + 4;
        if (dirty) {
            drawPencil(g, x + 4, py + 7, GOLD);
            textX += 10;
        }
        g.drawString(this.font, trim(label, tw - (closable ? 18 : 8) - (dirty ? 10 : 0)), textX, py + 7, on ? TEXT : MUTED, false);
        if (closable) {
            g.drawString(this.font, "x", x + tw - 10, py + 7, hot ? GOLD : MUTED, false);
        }
        return x + tw + 3;
    }

    private static boolean dirty(String id) {
        ScriptDocument doc = StudioSession.docs.get(id);
        return doc != null && doc.dirty();
    }

    private int tabInner(String label, boolean closable, boolean dirty) {
        return Math.min(150, this.font.width(label) + (closable ? 22 : 12) + (dirty ? 10 : 0));
    }

    private void windowButton(GuiGraphics g, int mx, int my, int x, int y, String kind) {
        boolean hot = hover(mx, my, x, y, 14, 14);
        int color = "close".equals(kind) && hot ? 0xFFE26A6A : hot ? GOLD : MUTED;
        if ("gear".equals(kind)) {
            drawGear(g, x + 7, y + 7, settings || hot ? GOLD : MUTED);
            return;
        }
        if ("min".equals(kind)) {
            g.fill(x + 3, y + 9, x + 11, y + 11, color);
        } else if ("max".equals(kind)) {
            g.fill(x + 3, y + 3, x + 11, y + 11, color);
            g.fill(x + 4, y + 4, x + 10, y + 10, BAR);
        } else if ("restore".equals(kind)) {
            g.fill(x + 5, y + 3, x + 12, y + 10, color);
            g.fill(x + 6, y + 4, x + 11, y + 9, BAR);
            g.fill(x + 2, y + 6, x + 9, y + 12, color);
            g.fill(x + 3, y + 7, x + 8, y + 11, BAR);
        } else {
            g.pose().pushPose();
            g.pose().translate(x + 7, y + 7, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(45));
            g.fill(-4, -1, 4, 1, color);
            g.fill(-1, -4, 1, 4, color);
            g.pose().popPose();
        }
    }

    private void renderMenuRow(GuiGraphics g, int mx, int my) {
        int y = py + TITLE;
        g.fill(px, y, px + pw, y + MENU_H, 0xF0181E28);
        int x = px + 6;
        x = menuWord(g, mx, my, x, y, "file", Messages.get("studio.m.file"));
        x = menuWord(g, mx, my, x, y, "edit", Messages.get("studio.m.edit"));
        x = menuWord(g, mx, my, x, y, "search", Messages.get("studio.m.search"));
        menuWord(g, mx, my, x, y, "view", Messages.get("studio.m.view"));
    }

    private int menuWord(GuiGraphics g, int mx, int my, int x, int y, String id, String label) {
        int w = this.font.width(label) + 10;
        boolean on = id.equals(menu) || hover(mx, my, x, y, w, MENU_H);
        if (on) {
            g.fill(x, y + 1, x + w, y + MENU_H - 1, 0xFF2A3344);
        }
        g.drawString(this.font, label, x + 5, y + 4, on ? TEXT : MUTED, false);
        return x + w;
    }

    private void renderToolbar(GuiGraphics g, int mx, int my) {
        int y = py + TITLE + MENU_H;
        g.fill(px, y, px + pw, y + TOOL_H, 0xF012161C);
        int x = px + 6;
        x = tool(g, mx, my, x, y, "new", Messages.get("studio.m.new"));
        x = tool(g, mx, my, x, y, "open", Messages.get("studio.m.open"));
        x = tool(g, mx, my, x, y, "save", Messages.get("studio.m.save"));
        x = tool(g, mx, my, x, y, "saveall", Messages.get("studio.m.save_all"));
        x = tool(g, mx, my, x, y, "closetab", Messages.get("studio.m.close"));
        x = tool(g, mx, my, x, y, "delete", Messages.get("studio.m.delete"));
        x += 6;
        x = tool(g, mx, my, x, y, "cut", Messages.get("studio.m.cut"));
        x = tool(g, mx, my, x, y, "copy", Messages.get("studio.m.copy"));
        x = tool(g, mx, my, x, y, "paste", Messages.get("studio.m.paste"));
        x += 6;
        x = tool(g, mx, my, x, y, "undo", Messages.get("studio.m.undo"));
        tool(g, mx, my, x, y, "redo", Messages.get("studio.m.redo"));
    }

    private int tool(GuiGraphics g, int mx, int my, int x, int y, String id, String label) {
        boolean hot = hover(mx, my, x, y + 2, 16, 16);
        if (hot) {
            g.fill(x, y + 2, x + 16, y + 18, 0xFF2A3344);
            tip = label;
            tipX = mx;
            tipY = my;
        }
        drawToolIcon(g, x + 2, y + 4, id, hot ? GOLD : MUTED);
        return x + 18;
    }

    /** Маленькие значки 12×12, чтобы их можно было отличить друг от друга. */
    private void drawToolIcon(GuiGraphics g, int x, int y, String id, int c) {
        switch (id) {
            case "new" -> {
                g.fill(x + 2, y, x + 9, y + 11, c);
                g.fill(x + 3, y + 1, x + 8, y + 10, BG);
                g.fill(x + 5, y + 3, x + 6, y + 8, c);
                g.fill(x + 3, y + 5, x + 8, y + 6, c);
            }
            case "open" -> {
                g.fill(x, y + 3, x + 12, y + 11, c);
                g.fill(x + 1, y + 4, x + 11, y + 10, BG);
                g.fill(x, y + 1, x + 6, y + 4, c);
            }
            case "save", "saveall" -> {
                g.fill(x + 1, y, x + 11, y + 11, c);
                g.fill(x + 2, y + 1, x + 10, y + 10, BG);
                g.fill(x + 3, y + 1, x + 9, y + 4, c);
                g.fill(x + 4, y + 6, x + 8, y + 9, c);
                if ("saveall".equals(id)) {
                    g.fill(x + 4, y + 2, x + 12, y + 11, c);
                    g.fill(x + 5, y + 3, x + 11, y + 10, BG);
                }
            }
            case "closetab" -> {
                g.fill(x + 2, y, x + 10, y + 11, c);
                g.fill(x + 3, y + 1, x + 9, y + 10, BG);
                g.fill(x + 4, y + 4, x + 8, y + 5, c);
                g.fill(x + 5, y + 3, x + 6, y + 8, c);
            }
            case "delete" -> {
                g.fill(x + 2, y + 3, x + 10, y + 11, c);
                g.fill(x + 3, y + 4, x + 9, y + 10, BG);
                g.fill(x + 1, y + 2, x + 11, y + 3, c);
                g.fill(x + 4, y, x + 8, y + 2, c);
            }
            case "cut" -> {
                g.fill(x + 1, y + 1, x + 4, y + 4, c);
                g.fill(x + 8, y + 1, x + 11, y + 4, c);
                g.fill(x + 5, y + 5, x + 7, y + 7, c);
                g.fill(x + 2, y + 8, x + 5, y + 11, c);
                g.fill(x + 7, y + 8, x + 10, y + 11, c);
            }
            case "copy" -> {
                g.fill(x + 3, y, x + 11, y + 9, c);
                g.fill(x + 4, y + 1, x + 10, y + 8, BG);
                g.fill(x, y + 3, x + 8, y + 12, c);
                g.fill(x + 1, y + 4, x + 7, y + 11, BG);
            }
            case "paste" -> {
                g.fill(x + 2, y + 2, x + 10, y + 12, c);
                g.fill(x + 3, y + 3, x + 9, y + 11, BG);
                g.fill(x + 4, y, x + 8, y + 3, c);
                g.fill(x + 5, y + 1, x + 7, y + 2, BG);
            }
            case "undo" -> {
                g.fill(x + 2, y + 4, x + 8, y + 6, c);
                g.fill(x + 2, y + 4, x + 4, y + 8, c);
                g.fill(x + 1, y + 2, x + 5, y + 4, c);
            }
            default -> {
                g.fill(x + 4, y + 4, x + 10, y + 6, c);
                g.fill(x + 8, y + 4, x + 10, y + 8, c);
                g.fill(x + 7, y + 2, x + 11, y + 4, c);
            }
        }
    }

    private void renderHome(GuiGraphics g, int mx, int my) {
        homeLinks.clear();
        int y = contentY();
        int h = contentH();
        int col = Math.max(128, Math.min(210, pw / 4));
        int leftX = px + 8;
        int rightX = px + pw - 8 - col;
        g.fill(leftX, y, leftX + col, y + h, 0xF0181E28);
        g.fill(rightX, y, rightX + col, y + h, 0xF0181E28);
        drawPatchColumn(g, leftX + 8, y + 8, col - 16, h - 16);
        drawLinkColumn(g, mx, my, rightX + 8, y + 8, col - 16, h - 16);
        int center = (leftX + col + rightX) / 2;
        int centerW = rightX - (leftX + col);
        int titleY = y + 6;
        float titleScale = centerW > 260 ? 2.6f : 1.8f;
        g.pose().pushPose();
        g.pose().translate(center, titleY, 0);
        g.pose().scale(titleScale, titleScale, 1f);
        String title = "ScriptCraft";
        g.drawString(this.font, title, -this.font.width(title) / 2, 0, GOLD, false);
        g.pose().popPose();
        String[] tags = {"studio.tag.1", "studio.tag.2", "studio.tag.3", "studio.tag.4", "studio.tag.5"};
        String tag = Messages.get(tags[(int) ((System.currentTimeMillis() / 4200L) % tags.length)]);
        int tagY = titleY + Math.round(11 * titleScale) + 8;
        g.drawString(this.font, tag, center - this.font.width(tag) / 2, tagY, TEXT, false);
        int caption = 28;
        int modelTop = tagY + 14;
        int room = y + h - modelTop - caption - 4;
        // Рост модели — два блока, поэтому делитель 2. Центр теперь на всю высоту окна
        // (патчи ушли в колонку), и фигура выходит примерно вдвое крупнее прежней.
        float scale = Math.max(36f, Math.min(room / 2.05f, (centerW - 12) / 1.05f));
        SkinPortrait.render(g, center, modelTop, scale);
        String dev = "ElgerElg";
        int foot = modelTop + Math.round(scale * 2.0f) + 4;
        g.drawString(this.font, dev, center - this.font.width(dev) / 2, foot, GOLD, false);
        String role = Messages.get("studio.role");
        g.drawString(this.font, role, center - this.font.width(role) / 2, foot + 12, MUTED, false);
    }

    /** Патчи сверху вниз на всю высоту левой колонки, а не одной кучкой. */
    private void drawPatchColumn(GuiGraphics g, int x, int y, int w, int h) {
        g.drawString(this.font, Messages.get("studio.patches"), x, y, GOLD, false);
        String[] keys = {"studio.p1", "studio.p2", "studio.p3", "studio.p4", "studio.p5"};
        int top = y + 16;
        int slot = Math.max(LINE_H, (h - 16) / keys.length);
        for (int i = 0; i < keys.length; i++) {
            int row = top + i * slot;
            drawWrapped(g, Messages.get(keys[i]), x, row, w, TEXT, row + slot - 2);
        }
    }

    private void drawLinkColumn(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
        g.drawString(this.font, Messages.get("studio.links"), x, y, GOLD, false);
        int row = y + 18;
        row = linkRow(g, mx, my, x, row, w, Messages.get("studio.link.github"), "https://github.com/ElgerElg/ScriptCraft", false);
        row = linkRow(g, mx, my, x, row, w, Messages.get("studio.link.docs"), "https://elgerelg.github.io/ScriptCraft/", false);
        row = linkRow(g, mx, my, x, row, w, Messages.get("studio.link.examples"),
                "https://github.com/ElgerElg/ScriptCraft/tree/main/examples", false);
        row += 6;
        g.drawString(this.font, Messages.get("studio.about"), x, row, GOLD, false);
        row += 16;
        String version = modVersion();
        row = drawWrapped(g, version, x, row, w, TEXT, y + h);
        row += 4;
        linkRow(g, mx, my, x, row, w, Messages.get("studio.link.folder"), ModPaths.scripts().toString(), true);
    }

    private int linkRow(GuiGraphics g, int mx, int my, int x, int y, int w, String label, String target, boolean file) {
        boolean hot = hover(mx, my, x, y - 1, w, 12);
        g.drawString(this.font, trim(label, w), x, y, hot ? GOLD : 0xFF8FB4E8, false);
        homeLinks.add(new HomeLink(x, y - 1, w, 12, target, file));
        return y + 14;
    }

    private int drawWrapped(GuiGraphics g, String text, int x, int y, int width, int color, int maxY) {
        for (var line : this.font.split(Component.literal(text), Math.max(40, width))) {
            if (y > maxY) {
                break;
            }
            g.drawString(this.font, line, x, y, color, false);
            y += LINE_H;
        }
        return y;
    }

    private static String modVersion() {
        return net.minecraftforge.fml.ModList.get().getModContainerById("scriptcraft")
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("ScriptCraft");
    }

    private void openHomeLink(HomeLink link) {
        try {
            if (link.file) {
                Util.getPlatform().openFile(java.nio.file.Path.of(link.target).toFile());
            } else {
                Util.getPlatform().openUri(URI.create(link.target));
            }
        } catch (Exception e) {
            ClientToasts.push(e.getMessage() == null ? "link" : e.getMessage());
        }
    }

    private record HomeLink(int x, int y, int w, int h, String target, boolean file) {
    }

    private void renderJournal(GuiGraphics g, int mx, int my) {
        int y = contentY();
        int h = contentH();
        int listW = Math.min(220, pw / 3);
        g.fill(px + 8, y, px + 8 + listW, y + h, 0xF0181E28);
        g.fill(px + 12 + listW, y, px + pw - 8, y + h, 0xF0181E28);
        int content = 4 + 16 + (StudioSession.issues.isEmpty() ? 16 : StudioSession.issues.size() * 24)
                + 6 + 14 + Math.max(1, StudioSession.history.size()) * 14 + 8;
        listMax = Math.max(0, content - h);
        listScroll = Math.max(0, Math.min(listScroll, listMax));
        if (!StudioSession.issues.isEmpty()) {
            journal = Math.max(0, Math.min(StudioSession.issues.size() - 1, journal));
        }
        g.enableScissor(px + 8, y, px + 8 + listW, y + h);
        int row = y + 4 - listScroll;
        g.drawString(this.font, Messages.get("studio.active"), px + 16, row, GOLD, false);
        row += 16;
        if (StudioSession.issues.isEmpty()) {
            g.drawString(this.font, Messages.get("log.empty"), px + 16, row, MUTED, false);
            row += 16;
        }
        for (int i = 0; i < StudioSession.issues.size(); i++) {
            ScriptLog.Issue issue = StudioSession.issues.get(i);
            if (row + 24 >= y && row <= y + h) {
                g.fill(px + 12, row, px + 4 + listW, row + 22, i == journal ? 0xFF2E3A4E : ROW);
                String where = issue.file().isEmpty() ? "—" : issue.file();
                if (issue.line() > 0) {
                    where = where + ":" + issue.line();
                }
                g.drawString(this.font, trim(where, listW - 20), px + 16, row + 2, i == journal ? GOLD : TEXT, false);
                g.drawString(this.font, trim(issue.reason(), listW - 20), px + 16, row + 12, MUTED, false);
            }
            row += 24;
        }
        row += 6;
        g.drawString(this.font, Messages.get("studio.history"), px + 16, row, GOLD, false);
        row += 14;
        if (StudioSession.history.isEmpty()) {
            g.drawString(this.font, "—", px + 16, row, MUTED, false);
        }
        for (int i = StudioSession.history.size() - 1; i >= 0; i--) {
            if (row + 14 >= y && row <= y + h) {
                g.drawString(this.font, trim(StudioSession.history.get(i), listW - 20), px + 16, row, MUTED, false);
            }
            row += 14;
        }
        g.disableScissor();
        if (StudioSession.issues.isEmpty()) {
            g.drawString(this.font, Messages.get("log.empty"), px + 16 + listW, y + 8, MUTED, false);
            codeMax = 0;
            journalListW = listW;
            journalY = y;
            journalH = h;
            return;
        }
        ScriptLog.Issue current = StudioSession.issues.get(journal);
        ScriptLog.Slice slice = slice(current.file());
        int codeX = px + 16 + listW;
        int codeW = pw - listW - 28;
        g.enableScissor(codeX, y, codeX + codeW, y + h - 22);
        int lines = slice == null ? 0 : slice.lines().size();
        codeMax = Math.max(0, lines * LINE_H - (h - 22));
        codeScroll = Math.max(0, Math.min(codeScroll, codeMax));
        if (slice == null || slice.lines().isEmpty()) {
            g.drawString(this.font, Messages.get("log.no_file"), codeX + 6, y + 8, MUTED, false);
        } else {
            int ly = y + 4 - codeScroll;
            for (int i = 0; i < slice.lines().size(); i++) {
                int number = slice.firstLine() + i;
                if (ly > y - LINE_H && ly < y + h) {
                    if (number == current.line()) {
                        g.fill(codeX, ly - 1, codeX + codeW, ly + LINE_H - 1, 0xAAD25A5A);
                    }
                    String num = Integer.toString(number);
                    g.drawString(this.font, num, codeX + 28 - this.font.width(num), ly, MUTED, false);
                    drawCode(g, slice.lines().get(i), codeX + 34, ly, TEXT, codeW - 40);
                }
                ly += LINE_H;
            }
        }
        g.disableScissor();
        g.drawString(this.font, trim(Messages.get("log.reason") + ": " + current.reason(), codeW), codeX, y + h - 14, 0xFFF0C2C2, false);
        journalListW = listW;
        journalY = y;
        journalH = h;
    }

    private int journalListW, journalY, journalH, listMax, codeMax;

    private void renderEditor(GuiGraphics g, int mx, int my) {
        ScriptDocument doc = script();
        int y = contentY() + TOOL_H;
        int status = 16;
        int findH = findOn ? 16 : 0;
        int h = py + ph - y - status - findH;
        editorX = px + 8;
        editorY = y;
        editorW = pw - 16;
        editorH = h;
        g.fill(editorX, editorY, editorX + editorW, editorY + editorH, 0xF010141C);
        if (!doc.loaded) {
            g.drawString(this.font, Messages.get("studio.loading"), editorX + 8, editorY + 8, MUTED, false);
        } else {
            g.enableScissor(editorX, editorY, editorX + editorW, editorY + editorH);
            int view = Math.max(1, editorH / LINE_H);
            doc.ensureVisible(view, LINE_H);
            for (int i = doc.scroll; i < doc.lines.size(); i++) {
                int ly = editorY + (i - doc.scroll) * LINE_H + 2;
                if (ly > editorY + editorH) {
                    break;
                }
                String line = doc.lines.get(i);
                if (i == doc.line) {
                    g.fill(editorX, ly - 1, editorX + editorW, ly + LINE_H - 1, 0xFF1A2230);
                }
                int[] sel = doc.selectionOn(i);
                if (sel != null) {
                    int x0 = editorX + 36 + spanWidth(line, sel[0]);
                    int x1 = editorX + 36 + spanWidth(line, sel[1]);
                    g.fill(x0, ly - 1, Math.max(x1, x0 + 2), ly + LINE_H - 1, 0x883A6EA5);
                }
                for (ScriptMarkup.Mark mark : ScriptMarkup.of(line)) {
                    int x0 = editorX + 36 + spanWidth(line, mark.from());
                    int x1 = editorX + 36 + spanWidth(line, mark.to());
                    g.fill(x0, ly - 1, x1, ly + LINE_H - 1, mark.event() ? YELLOW : BLUE);
                    if (!mark.event() && hover(mx, my, x0, ly - 1, Math.max(1, x1 - x0), LINE_H)) {
                        TexturePreview.Image image = TexturePreview.lookup(mark.token());
                        if (image != null) {
                            preview = image;
                            previewX = mx + 12;
                            previewY = my + 12;
                        }
                    }
                }
                String num = Integer.toString(i + 1);
                g.drawString(this.font, num, editorX + 30 - this.font.width(num), ly, MUTED, false);
                drawCode(g, line, editorX + 36, ly, TEXT, editorW - 44);
                if (i == doc.line && (System.currentTimeMillis() / 500) % 2 == 0) {
                    int cx = editorX + 36 + spanWidth(line, Math.min(doc.col, line.length()));
                    g.fill(cx, ly - 1, cx + 1, ly + LINE_H - 2, TEXT);
                }
            }
            g.disableScissor();
        }
        int sy = editorY + editorH;
        if (findOn) {
            g.fill(editorX, sy, editorX + editorW, sy + 16, BAR);
            g.drawString(this.font, Messages.get("studio.find") + ": " + find + (findFocus ? "_" : ""), editorX + 4, sy + 4, TEXT, false);
            sy += 16;
        }
        String statusText = statusText(doc);
        g.fill(px, py + ph - 16, px + pw, py + ph, BAR);
        g.drawString(this.font, trim(statusText, pw - 16), px + 8, py + ph - 12, statusBad ? 0xFFF0C2C2 : MUTED, false);
        if (preview != null) {
            int w = Math.min(72, preview.width());
            int ph = Math.min(72, preview.height());
            int x = Math.min(previewX, px + pw - w - 8);
            int pyy = Math.min(previewY, this.py + this.ph - ph - 24);
            g.fill(x - 2, pyy - 2, x + w + 2, pyy + ph + 14, 0xF0101014);
            g.blit(preview.id(), x, pyy, w, ph, 0, 0, preview.width(), preview.height(), preview.width(), preview.height());
            g.drawString(this.font, preview.width() + "×" + preview.height(), x, pyy + ph + 2, TEXT, false);
        }
    }

    private int editorX, editorY, editorW, editorH;

    private boolean statusBad;

    private String statusText(ScriptDocument doc) {
        for (ScriptLog.Issue issue : StudioSession.issues) {
            if (issue.file().equals(doc.name) && issue.line() == doc.line + 1) {
                statusBad = true;
                return issue.reason();
            }
        }
        statusBad = false;
        return Messages.get("studio.parser");
    }

    private void renderSettings(GuiGraphics g, int mx, int my) {
        int x = px + 16;
        int y = contentY() + 8;
        g.drawString(this.font, Messages.get("log.settings"), x, y, GOLD, false);
        y += 18;
        g.drawString(this.font, Messages.get("log.key"), x, y, MUTED, false);
        g.fill(x + 110, y - 2, x + 280, y + 12, listening ? 0xFF2E3A4E : ROW);
        g.drawString(this.font, listening ? Messages.get("log.key_wait") : ClientUiSettings.keyLabel(), x + 116, y, TEXT, false);
        keyBoxX = x + 110;
        keyBoxY = y - 2;
        y += 22;
        slider1Y = y + 12;
        g.drawString(this.font, Messages.get("log.time") + "  " + String.format(Locale.ROOT, "%.1f s", ClientUiSettings.seconds()), x, y, MUTED, false);
        drawSlider(g, x, slider1Y, (ClientUiSettings.seconds() - 1.5f) / 8.5f);
        y += 28;
        slider2Y = y + 12;
        g.drawString(this.font, Messages.get("log.size") + "  " + Math.round(ClientUiSettings.scale() * 100) + "%", x, y, MUTED, false);
        drawSlider(g, x, slider2Y, (ClientUiSettings.scale() - 0.7f) / 0.8f);
        y += 28;
        g.drawString(this.font, Messages.get("log.place"), x, y, MUTED, false);
        y += 14;
        place(g, mx, my, x, y, ClientUiSettings.Place.CHAT, Messages.get("log.place.chat"));
        place(g, mx, my, x + 120, y, ClientUiSettings.Place.TOP_LEFT, Messages.get("log.place.tl"));
        place(g, mx, my, x, y + 20, ClientUiSettings.Place.TOP_RIGHT, Messages.get("log.place.tr"));
        place(g, mx, my, x + 120, y + 20, ClientUiSettings.Place.BOTTOM_RIGHT, Messages.get("log.place.br"));
        sliderX = x;
    }

    private int keyBoxX, keyBoxY, slider1Y, slider2Y, sliderX;

    private void drawSlider(GuiGraphics g, int x, int y, float t) {
        g.fill(x, y, x + 180, y + 4, EDGE);
        int knob = x + Math.round(Math.max(0, Math.min(1, t)) * 172);
        g.fill(knob, y - 3, knob + 8, y + 7, GOLD);
    }

    private void place(GuiGraphics g, int mx, int my, int x, int y, ClientUiSettings.Place place, String label) {
        boolean on = ClientUiSettings.place() == place;
        g.fill(x, y, x + 110, y + 16, on || hover(mx, my, x, y, 110, 16) ? 0xFF2E3A4E : ROW);
        g.drawString(this.font, label, x + 4, y + 4, on ? GOLD : TEXT, false);
    }

    private void renderMenu(GuiGraphics g, int mx, int my) {
        List<String> items = menuItems();
        int x = menuX();
        int y = py + TITLE + MENU_H;
        int w = 150;
        g.fill(x, y, x + w, y + items.size() * 14 + 4, 0xF0101418);
        border(g, x, y, w, items.size() * 14 + 4, EDGE);
        for (int i = 0; i < items.size(); i++) {
            int iy = y + 2 + i * 14;
            if (hover(mx, my, x, iy, w, 14)) {
                g.fill(x + 1, iy, x + w - 1, iy + 14, 0xFF2A3344);
            }
            g.drawString(this.font, items.get(i), x + 6, iy + 3, TEXT, false);
        }
    }

    private void renderCatalog(GuiGraphics g, int mx, int my) {
        int x = plusX;
        int y = py + TITLE;
        int w = 180;
        int rows = Math.max(1, Math.min(12, StudioSession.catalog.size()));
        int h = rows * 14 + 4;
        g.fill(x, y, x + w, y + h, 0xF0101418);
        border(g, x, y, w, h, EDGE);
        if (StudioSession.catalog.isEmpty()) {
            g.drawString(this.font, Messages.get("studio.empty"), x + 6, y + 4, MUTED, false);
            return;
        }
        for (int i = 0; i < rows; i++) {
            String name = StudioSession.catalog.get(i);
            int iy = y + 2 + i * 14;
            if (hover(mx, my, x, iy, w, 14)) {
                g.fill(x + 1, iy, x + w - 1, iy + 14, 0xFF2A3344);
            }
            g.drawString(this.font, trim(name, w - 12), x + 6, iy + 3, TEXT, false);
        }
    }

    private void renderNameBox(GuiGraphics g) {
        int x = px + pw / 2 - 90;
        int y = py + ph / 2 - 16;
        g.fill(x, y, x + 180, y + 32, 0xF0101418);
        border(g, x, y, 180, 32, GOLD);
        g.drawString(this.font, nameBuf.isEmpty() ? Messages.get("studio.new_name") : nameBuf + "_", x + 8, y + 12, nameBuf.isEmpty() ? MUTED : TEXT, false);
    }

    private void drawTip(GuiGraphics g) {
        if (tip == null) {
            return;
        }
        int w = this.font.width(tip) + 6;
        g.fill(tipX, tipY - 12, tipX + w, tipY - 1, 0xF0101014);
        g.drawString(this.font, tip, tipX + 3, tipY - 11, TEXT, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        layout();
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (ask != null) {
            if (hover(mx, my, askYesX, askY, 48, 16)) {
                confirmAsk();
            } else             if (hover(mx, my, askNoX, askY, 48, 16)) {
                ask = null;
            }
            return true;
        }
        if (StudioSession.HOME.equals(StudioSession.active) && !settings) {
            for (HomeLink link : homeLinks) {
                if (hover(mx, my, link.x, link.y, link.w, link.h)) {
                    openHomeLink(link);
                    return true;
                }
            }
        }
        Drag edge = edgeAt(mx, my);
        if (edge != Drag.NONE) {
            drag = edge;
            grabX = mx;
            grabY = my;
            originX = StudioSession.x;
            originY = StudioSession.y;
            originW = StudioSession.w;
            originH = StudioSession.h;
            return true;
        }
        if (naming) {
            return true;
        }
        if (menu != null && my >= py + TITLE + MENU_H && my < py + TITLE + MENU_H + menuItems().size() * 14 + 4
                && mx >= menuX() && mx < menuX() + 150) {
            int index = (my - (py + TITLE + MENU_H + 2)) / 14;
            runMenu(index);
            menu = null;
            return true;
        }
        menu = null;
        if (catalog && my >= py + TITLE && mx >= plusX && mx < plusX + 180) {
            int index = (my - (py + TITLE + 2)) / 14;
            if (index >= 0 && index < StudioSession.catalog.size() && index < 12) {
                openScript(StudioSession.catalog.get(index));
            }
            catalog = false;
            return true;
        }
        catalog = false;
        if (my >= py && my < py + TITLE) {
            if (hover(mx, my, px + pw - 16, py + 4, 14, 14)) {
                onClose();
                return true;
            }
            if (hover(mx, my, px + pw - 30, py + 4, 14, 14)) {
                toggleMax();
                return true;
            }
            if (hover(mx, my, px + pw - 48, py + 4, 14, 14)) {
                StudioSession.minimized = !StudioSession.minimized;
                return true;
            }
            if (hover(mx, my, px + pw - 66, py + 4, 14, 14)) {
                settings = !settings;
                return true;
            }
            if (hover(mx, my, plusX, py + 4, 14, 14)) {
                catalog = true;
                ModNetwork.sendToServer(new ScriptListPacket.Request());
                return true;
            }
            String tab = tabAt(mx);
            if (tab != null) {
                if (tab.startsWith("x:")) {
                    requestClose(tab.substring(2));
                } else {
                    StudioSession.active = tab;
                    settings = false;
                }
                return true;
            }
            if (!StudioSession.maximized) {
                drag = Drag.MOVE;
                grabX = mx - px;
                grabY = my - py;
            }
            return true;
        }
        if (StudioSession.minimized) {
            return true;
        }
        if (my >= py + TITLE && my < py + TITLE + MENU_H) {
            menu = menuAt(mx);
            return true;
        }
        if (settings) {
            settingsClick(mx, my);
            return true;
        }
        if (script() != null && my >= py + TITLE + MENU_H && my < py + TITLE + MENU_H + TOOL_H) {
            toolClick(mx);
            return true;
        }
        if (StudioSession.JOURNAL.equals(StudioSession.active)) {
            journalClick(mx, my);
            return true;
        }
        ScriptDocument doc = script();
        if (doc != null && doc.loaded && hover(mx, my, editorX, editorY, editorW, editorH)) {
            placeCursor(doc, mx, my, false);
            drag = Drag.TEXT;
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (drag == Drag.TEXT) {
            ScriptDocument doc = script();
            if (doc != null) {
                placeCursor(doc, mx, my, true);
            }
            return true;
        }
        if (drag == Drag.MOVE) {
            StudioSession.maximized = false;
            StudioSession.x = mx - grabX;
            StudioSession.y = my - grabY;
            StudioSession.placed = true;
            return true;
        }
        if (drag != Drag.NONE && !StudioSession.maximized) {
            int ddx = mx - grabX;
            int ddy = my - grabY;
            int x = originX;
            int y = originY;
            int w = originW;
            int h = originH;
            if (drag == Drag.E || drag == Drag.NE || drag == Drag.SE) {
                w = originW + ddx;
            }
            if (drag == Drag.S || drag == Drag.SE || drag == Drag.SW) {
                h = originH + ddy;
            }
            if (drag == Drag.W || drag == Drag.NW || drag == Drag.SW) {
                x = originX + ddx;
                w = originW - ddx;
            }
            if (drag == Drag.N || drag == Drag.NE || drag == Drag.NW) {
                y = originY + ddy;
                h = originH - ddy;
            }
            StudioSession.x = x;
            StudioSession.y = y;
            StudioSession.w = Math.max(360, w);
            StudioSession.h = Math.max(220, h);
            StudioSession.placed = true;
            return true;
        }
        if (slider != 0) {
            applySlider(mx);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        drag = Drag.NONE;
        slider = 0;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int step = (int) Math.signum(delta) * -3;
        ScriptDocument doc = script();
        if (doc != null && !settings) {
            doc.scroll = Math.max(0, doc.scroll + step);
        } else if (StudioSession.JOURNAL.equals(StudioSession.active)) {
            if (mouseX < px + 8 + journalListW) {
                listScroll = Math.max(0, Math.min(listMax, listScroll + step * 8));
            } else {
                codeScroll = Math.max(0, Math.min(codeMax, codeScroll + step * LINE_H));
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (listening) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                listening = false;
                return true;
            }
            if (key != GLFW.GLFW_KEY_F3) {
                long window = this.minecraft != null ? this.minecraft.getWindow().getWindow() : 0L;
                boolean f3 = window != 0L && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_F3) == GLFW.GLFW_PRESS;
                ClientUiSettings.setOpenKey(key, f3);
                listening = false;
            }
            return true;
        }
        if (naming) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                naming = false;
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                createScript();
            } else if (key == GLFW.GLFW_KEY_BACKSPACE && !nameBuf.isEmpty()) {
                nameBuf = nameBuf.substring(0, nameBuf.length() - 1);
            }
            return true;
        }
        if (ask != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                ask = null;
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                confirmAsk();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (menu != null || catalog || findFocus) {
                menu = null;
                catalog = false;
                findFocus = false;
                return true;
            }
            onClose();
            return true;
        }
        boolean ctrl = hasControlDown();
        ScriptDocument doc = script();
        if (ctrl && key == GLFW.GLFW_KEY_S) {
            if (hasShiftDown()) {
                saveAll();
            } else {
                save(doc);
            }
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_R) {
            reload(doc);
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_F) {
            findOn = true;
            findFocus = true;
            return true;
        }
        if (findFocus) {
            if (key == GLFW.GLFW_KEY_ENTER) {
                findNext(doc);
            } else if (key == GLFW.GLFW_KEY_BACKSPACE && !find.isEmpty()) {
                find = find.substring(0, find.length() - 1);
            }
            return true;
        }
        if (doc == null || !doc.loaded || settings) {
            return true;
        }
        boolean shift = hasShiftDown();
        if (ctrl && key == GLFW.GLFW_KEY_Z) {
            doc.undo();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Y) {
            doc.redo();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_A) {
            doc.selectAll();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_C) {
            copy(doc);
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_X) {
            copy(doc);
            doc.backspace();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            doc.insert(clipboard());
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (ctrl) {
                doc.deleteWord();
            } else {
                doc.backspace();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            doc.deleteForward();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            doc.insert("\n");
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            doc.insert("\t");
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT) {
            int col = doc.col - 1;
            int line = doc.line;
            if (col < 0 && line > 0) {
                line--;
                col = doc.lines.get(line).length();
            }
            doc.move(line, Math.max(0, col), shift);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            int col = doc.col + 1;
            int line = doc.line;
            if (col > doc.lines.get(line).length() && line + 1 < doc.lines.size()) {
                line++;
                col = 0;
            }
            doc.move(line, col, shift);
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP) {
            doc.move(doc.line - 1, doc.col, shift);
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN) {
            doc.move(doc.line + 1, doc.col, shift);
            return true;
        }
        if (key == GLFW.GLFW_KEY_HOME) {
            doc.move(doc.line, 0, shift);
            return true;
        }
        if (key == GLFW.GLFW_KEY_END) {
            doc.move(doc.line, doc.lines.get(doc.line).length(), shift);
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (listening) {
            return true;
        }
        if (naming) {
            if (ch >= 32 && nameBuf.length() < 64 && ch != '/' && ch != '\\' && ch != ':') {
                nameBuf += ch;
            }
            return true;
        }
        if (findFocus) {
            if (ch >= 32) {
                find += ch;
            }
            return true;
        }
        ScriptDocument doc = script();
        if (doc != null && doc.loaded && !settings && !hasControlDown() && ch >= 32) {
            doc.insert(String.valueOf(ch));
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void layout() {
        if (!StudioSession.maximized && StudioSession.placed && this.width > 0) {
            int shownH = StudioSession.minimized ? TITLE : Math.max(220, StudioSession.h);
            StudioSession.w = Math.max(360, Math.min(StudioSession.w, this.width));
            StudioSession.h = Math.max(220, Math.min(StudioSession.h, this.height));
            StudioSession.x = Math.max(0, Math.min(StudioSession.x, Math.max(0, this.width - Math.min(StudioSession.w, 120))));
            StudioSession.y = Math.max(0, Math.min(StudioSession.y, Math.max(0, this.height - Math.min(shownH, TITLE))));
        }
        if (StudioSession.minimized) {
            if (StudioSession.maximized || !StudioSession.placed) {
                px = 0;
                py = 0;
                pw = this.width;
            } else {
                px = StudioSession.x;
                py = StudioSession.y;
                pw = StudioSession.w;
            }
            ph = TITLE;
            return;
        }
        if (StudioSession.maximized || !StudioSession.placed) {
            px = 0;
            py = 0;
            pw = this.width;
            ph = this.height;
            return;
        }
        px = StudioSession.x;
        py = StudioSession.y;
        pw = Math.max(360, StudioSession.w);
        ph = Math.max(220, StudioSession.h);
    }

    private int contentY() {
        return py + TITLE + MENU_H;
    }

    private int contentH() {
        return ph - TITLE - MENU_H - 8;
    }

    private ScriptDocument script() {
        if (StudioSession.HOME.equals(StudioSession.active) || StudioSession.JOURNAL.equals(StudioSession.active)) {
            return null;
        }
        return StudioSession.docs.get(StudioSession.active);
    }

    private void openScript(String name) {
        ScriptDocument doc = StudioSession.openScript(name);
        catalog = false;
        settings = false;
        if (!doc.loaded) {
            ModNetwork.sendToServer(new ScriptDataPacket.Read(name));
        }
    }

    private void save(ScriptDocument doc) {
        if (doc == null || !doc.loaded) {
            return;
        }
        ModNetwork.sendToServer(new ScriptDataPacket.Save(doc.name, doc.text()));
        doc.saved = doc.text();
    }

    private void saveAll() {
        for (ScriptDocument doc : StudioSession.docs.values()) {
            if (doc.dirty()) {
                save(doc);
            }
        }
    }

    private void reload(ScriptDocument doc) {
        if (doc == null) {
            ModNetwork.sendToServer(new LogRequestPacket());
            return;
        }
        if (doc.dirty()) {
            ClientToasts.push(Messages.toast("studio.unsaved"));
        } else {
            ModNetwork.sendToServer(new ScriptDataPacket.Read(doc.name));
        }
        ModNetwork.sendToServer(new ScriptReloadPacket(doc.name));
    }

    private void createScript() {
        String name = nameBuf.strip();
        if (!name.endsWith(".sc")) {
            name = name + ".sc";
        }
        naming = false;
        nameBuf = "";
        String body = "# \n\non load:\n    \n";
        ScriptDocument doc = StudioSession.openScript(name);
        doc.load(body);
        ModNetwork.sendToServer(new ScriptDataPacket.Save(name, body));
    }

    private void copy(ScriptDocument doc) {
        String text = doc.selectedText();
        if (!text.isEmpty() && this.minecraft != null) {
            this.minecraft.keyboardHandler.setClipboard(text);
        }
    }

    private String clipboard() {
        if (this.minecraft == null) {
            return "";
        }
        String text = this.minecraft.keyboardHandler.getClipboard();
        return text == null ? "" : text;
    }

    private void findNext(ScriptDocument doc) {
        if (doc == null || find.isEmpty()) {
            return;
        }
        String needle = find.toLowerCase(Locale.ROOT);
        int line = doc.line;
        int col = doc.col;
        for (int step = 0; step < doc.lines.size() + 1; step++) {
            String text = doc.lines.get(line).toLowerCase(Locale.ROOT);
            int at = text.indexOf(needle, col);
            if (at >= 0) {
                doc.move(line, at, false);
                doc.move(line, at + needle.length(), true);
                return;
            }
            line = (line + 1) % doc.lines.size();
            col = 0;
        }
        ClientToasts.push(Messages.toast("studio.none"));
    }

    private void placeCursor(ScriptDocument doc, int mx, int my, boolean keep) {
        int row = doc.scroll + (my - editorY - 2) / LINE_H;
        row = Math.max(0, Math.min(doc.lines.size() - 1, row));
        String line = doc.lines.get(row);
        int local = mx - editorX - 36;
        int col = 0;
        while (col < line.length() && spanWidth(line, col + 1) < local + 2) {
            col++;
        }
        if (hasAltDown() || (keep && doc.box)) {
            doc.moveBox(row, col);
        } else {
            doc.move(row, col, keep);
        }
    }

    private void toolClick(int mx) {
        int x = px + 6;
        String[] ids = {"new", "open", "save", "saveall", "closetab", "delete", "", "cut", "copy", "paste", "", "undo", "redo"};
        for (String id : ids) {
            if (id.isEmpty()) {
                x += 6;
                continue;
            }
            if (mx >= x && mx < x + 16) {
                runTool(id);
                return;
            }
            x += 18;
        }
    }

    private void runTool(String id) {
        ScriptDocument doc = script();
        switch (id) {
            case "new" -> {
                naming = true;
                nameBuf = "";
            }
            case "open" -> {
                catalog = true;
                ModNetwork.sendToServer(new ScriptListPacket.Request());
            }
            case "save" -> save(doc);
            case "saveall" -> saveAll();
            case "closetab" -> {
                if (doc != null) {
                    requestClose(doc.name);
                }
            }
            case "delete" -> {
                if (doc != null) {
                    ask = "delete";
                    askName = doc.name;
                }
            }
            case "cut" -> {
                if (doc != null) {
                    copy(doc);
                    doc.backspace();
                }
            }
            case "copy" -> {
                if (doc != null) {
                    copy(doc);
                }
            }
            case "paste" -> {
                if (doc != null && this.minecraft != null) {
                    doc.insert(clipboard());
                }
            }
            case "undo" -> {
                if (doc != null) {
                    doc.undo();
                }
            }
            case "redo" -> {
                if (doc != null) {
                    doc.redo();
                }
            }
            default -> {
            }
        }
    }

    private List<String> menuItems() {
        return switch (menu == null ? "" : menu) {
            case "file" -> List.of(Messages.get("studio.m.new"), Messages.get("studio.m.open"), Messages.get("studio.m.save"),
                    Messages.get("studio.m.save_all"), Messages.get("studio.m.delete"), Messages.get("studio.m.close"),
                    Messages.get("studio.m.exit"));
            case "edit" -> List.of(Messages.get("studio.m.undo"), Messages.get("studio.m.redo"), Messages.get("studio.m.cut"),
                    Messages.get("studio.m.copy"), Messages.get("studio.m.paste"), Messages.get("studio.m.all"));
            case "search" -> List.of(Messages.get("studio.m.find"));
            case "view" -> List.of(StudioSession.maximized ? Messages.get("studio.m.window") : Messages.get("studio.m.full"),
                    Messages.get("studio.journal"));
            default -> List.of();
        };
    }

    private void runMenu(int index) {
        if (index < 0) {
            return;
        }
        switch (menu) {
            case "file" -> {
                if (index == 0) {
                    naming = true;
                    nameBuf = "";
                } else if (index == 1) {
                    catalog = true;
                    ModNetwork.sendToServer(new ScriptListPacket.Request());
                } else if (index == 2) {
                    save(script());
                } else if (index == 3) {
                    saveAll();
                } else if (index == 4 && script() != null) {
                    ask = "delete";
                    askName = script().name;
                } else if (index == 5 && script() != null) {
                    requestClose(script().name);
                } else if (index == 6) {
                    onClose();
                }
            }
            case "edit" -> {
                if (index == 5) {
                    ScriptDocument doc = script();
                    if (doc != null) {
                        doc.selectAll();
                    }
                } else {
                    runTool(switch (index) {
                        case 0 -> "undo";
                        case 1 -> "redo";
                        case 2 -> "cut";
                        case 3 -> "copy";
                        default -> "paste";
                    });
                }
            }
            case "search" -> {
                findOn = true;
                findFocus = true;
            }
            case "view" -> {
                if (index == 0) {
                    toggleMax();
                } else {
                    StudioSession.active = StudioSession.JOURNAL;
                }
            }
            default -> {
            }
        }
    }

    private int menuX() {
        int x = px + 6;
        String[] ids = {"file", "edit", "search", "view"};
        String[] labels = {Messages.get("studio.m.file"), Messages.get("studio.m.edit"), Messages.get("studio.m.search"),
                Messages.get("studio.m.view")};
        for (int i = 0; i < ids.length; i++) {
            int w = this.font.width(labels[i]) + 10;
            if (ids[i].equals(menu)) {
                return x;
            }
            x += w;
        }
        return px + 6;
    }

    private String menuAt(int mx) {
        int x = px + 6;
        String[] ids = {"file", "edit", "search", "view"};
        String[] labels = {Messages.get("studio.m.file"), Messages.get("studio.m.edit"), Messages.get("studio.m.search"),
                Messages.get("studio.m.view")};
        for (int i = 0; i < ids.length; i++) {
            int w = this.font.width(labels[i]) + 10;
            if (mx >= x && mx < x + w) {
                return ids[i];
            }
            x += w;
        }
        return null;
    }

    private String tabAt(int mx) {
        int x = px + 8 + this.font.width("ScriptCraft") + 10;
        int limit = px + pw - 78;
        String home = hitTab(mx, x, Messages.get("studio.home"), false);
        x += tabWidth(Messages.get("studio.home"), false, false);
        if (home != null) {
            return StudioSession.HOME;
        }
        String journalLabel = Messages.get("studio.journal");
        if (!StudioSession.issues.isEmpty()) {
            journalLabel = journalLabel + " " + StudioSession.issues.size();
        }
        if (hitTab(mx, x, journalLabel, false) != null) {
            return StudioSession.JOURNAL;
        }
        x += tabWidth(journalLabel, false, false);
        for (String name : new ArrayList<>(StudioSession.docs.keySet())) {
            if (x > limit - 70) {
                break;
            }
            int w = tabWidth(shortName(name), true, dirty(name));
            if (mx >= x + w - 12 && mx < x + w) {
                return "x:" + name;
            }
            if (mx >= x && mx < x + w) {
                return name;
            }
            x += w;
        }
        return null;
    }

    private String hitTab(int mx, int x, String label, boolean closable) {
        int w = tabWidth(label, closable, false);
        return mx >= x && mx < x + w ? label : null;
    }

    private int tabWidth(String label, boolean closable, boolean dirty) {
        return tabInner(label, closable, dirty) + 3;
    }

    private void journalClick(int mx, int my) {
        if (mx < px + 8 + journalListW) {
            int index = (my - journalY - 4 + listScroll - 16) / 24;
            if (index >= 0 && index < StudioSession.issues.size()) {
                journal = index;
                ScriptLog.Issue issue = StudioSession.issues.get(index);
                ScriptLog.Slice slice = slice(issue.file());
                if (slice != null && issue.line() > 0) {
                    codeScroll = Math.max(0, (issue.line() - slice.firstLine()) * LINE_H - 20);
                }
            }
        }
    }

    private void settingsClick(int mx, int my) {
        if (hover(mx, my, keyBoxX, keyBoxY, 170, 14)) {
            listening = true;
            return;
        }
        if (hover(mx, my, sliderX, slider1Y - 4, 180, 12)) {
            slider = 1;
            applySlider(mx);
            return;
        }
        if (hover(mx, my, sliderX, slider2Y - 4, 180, 12)) {
            slider = 2;
            applySlider(mx);
            return;
        }
        int y = slider2Y + 16 + 14;
        clickPlace(mx, my, sliderX, y, ClientUiSettings.Place.CHAT);
        clickPlace(mx, my, sliderX + 120, y, ClientUiSettings.Place.TOP_LEFT);
        clickPlace(mx, my, sliderX, y + 20, ClientUiSettings.Place.TOP_RIGHT);
        clickPlace(mx, my, sliderX + 120, y + 20, ClientUiSettings.Place.BOTTOM_RIGHT);
    }

    private void clickPlace(int mx, int my, int x, int y, ClientUiSettings.Place place) {
        if (hover(mx, my, x, y, 110, 16)) {
            ClientUiSettings.setPlace(place);
        }
    }

    private void applySlider(int mx) {
        float t = Math.max(0f, Math.min(1f, (mx - sliderX) / 172f));
        if (slider == 1) {
            ClientUiSettings.setSeconds(1.5f + t * 8.5f);
        } else if (slider == 2) {
            ClientUiSettings.setScale(0.7f + t * 0.8f);
        }
    }

    private void toggleMax() {
        if (StudioSession.maximized) {
            StudioSession.maximized = false;
            StudioSession.minimized = false;
            if (!StudioSession.placed) {
                StudioSession.w = Math.min(800, Math.max(360, this.width - 80));
                StudioSession.h = Math.min(480, Math.max(220, this.height - 80));
                StudioSession.x = (this.width - StudioSession.w) / 2;
                StudioSession.y = (this.height - StudioSession.h) / 2;
                StudioSession.placed = true;
            }
        } else {
            StudioSession.maximized = true;
            StudioSession.minimized = false;
        }
    }

    private Drag edgeAt(int mx, int my) {
        if (StudioSession.maximized || StudioSession.minimized || !StudioSession.placed) {
            return Drag.NONE;
        }
        boolean left = Math.abs(mx - px) <= 4 && my >= py && my <= py + ph;
        boolean right = Math.abs(mx - (px + pw)) <= 4 && my >= py && my <= py + ph;
        boolean top = Math.abs(my - py) <= 4 && mx >= px && mx <= px + pw;
        boolean bottom = Math.abs(my - (py + ph)) <= 4 && mx >= px && mx <= px + pw;
        if (top && left) {
            return Drag.NW;
        }
        if (top && right) {
            return Drag.NE;
        }
        if (bottom && left) {
            return Drag.SW;
        }
        if (bottom && right) {
            return Drag.SE;
        }
        if (left) {
            return Drag.W;
        }
        if (right) {
            return Drag.E;
        }
        if (top) {
            return Drag.N;
        }
        if (bottom) {
            return Drag.S;
        }
        return Drag.NONE;
    }

    private ScriptLog.Slice slice(String file) {
        for (ScriptLog.Slice slice : StudioSession.slices) {
            if (slice.name().equals(file)) {
                return slice;
            }
        }
        return null;
    }

    private String shortName(String name) {
        int slash = name.lastIndexOf('/');
        String file = slash < 0 ? name : name.substring(slash + 1);
        return file.endsWith(".sc") ? file.substring(0, file.length() - 3) : file;
    }

    private String trim(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(8, width - 8)) + "…";
    }

    private void requestClose(String name) {
        ScriptDocument doc = StudioSession.docs.get(name);
        if (doc != null && doc.dirty()) {
            ask = "close";
            askName = name;
            return;
        }
        dropTab(name);
    }

    private void confirmAsk() {
        if (askName == null) {
            ask = null;
            return;
        }
        if ("delete".equals(ask)) {
            ModNetwork.sendToServer(new ScriptDeletePacket(askName));
            dropTab(askName);
        } else if ("close".equals(ask)) {
            dropTab(askName);
        }
        ask = null;
    }

    private void dropTab(String name) {
        StudioSession.docs.remove(name);
        if (name.equals(StudioSession.active)) {
            StudioSession.active = StudioSession.HOME;
        }
    }

    private void renderAsk(GuiGraphics g, int mx, int my) {
        String text = Messages.get("delete".equals(ask) ? "studio.ask.delete" : "studio.ask.close", askName);
        int w = Math.max(200, this.font.width(text) + 24);
        int x = px + (pw - w) / 2;
        int y = py + ph / 2 - 28;
        g.fill(x, y, x + w, y + 52, 0xF0101418);
        border(g, x, y, w, 52, GOLD);
        g.drawString(this.font, text, x + 12, y + 8, TEXT, false);
        askYesX = x + 12;
        askNoX = x + 70;
        askY = y + 28;
        g.fill(askYesX, askY, askYesX + 48, askY + 16, hover(mx, my, askYesX, askY, 48, 16) ? 0xFF3A4A30 : 0xFF243028);
        g.fill(askNoX, askY, askNoX + 48, askY + 16, hover(mx, my, askNoX, askY, 48, 16) ? 0xFF4A3030 : 0xFF2A2224);
        String yes = Messages.get("studio.ask.yes");
        String no = Messages.get("studio.ask.no");
        g.drawString(this.font, yes, askYesX + (48 - this.font.width(yes)) / 2, askY + 4, TEXT, false);
        g.drawString(this.font, no, askNoX + (48 - this.font.width(no)) / 2, askY + 4, TEXT, false);
    }

    private void drawPencil(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y + 6, x + 6, y + 8, color);
        g.fill(x + 4, y + 1, x + 6, y + 6, color);
        g.fill(x + 5, y, x + 7, y + 2, color);
    }

    private int spanWidth(String line, int count) {
        int width = 0;
        int start = 0;
        int end = Math.max(0, Math.min(count, line.length()));
        int tab = this.font.width("    ");
        for (int i = 0; i < end; i++) {
            if (line.charAt(i) == '\t') {
                width += this.font.width(line.substring(start, i)) + tab;
                start = i + 1;
            }
        }
        return width + this.font.width(line.substring(start, end));
    }

    /** Рисует строку кода: таб — тонкая палка, а не квадрат отсутствующего глифа. */
    private void drawCode(GuiGraphics g, String line, int x, int y, int color, int max) {
        int drawn = 0;
        int start = 0;
        int tab = this.font.width("    ");
        for (int i = 0; i <= line.length(); i++) {
            boolean isTab = i < line.length() && line.charAt(i) == '\t';
            if (!isTab && i < line.length()) {
                continue;
            }
            if (start < i) {
                String part = line.substring(start, i);
                int partW = this.font.width(part);
                if (drawn + partW > max) {
                    g.drawString(this.font, this.font.plainSubstrByWidth(part, Math.max(0, max - drawn)), x + drawn, y, color, false);
                    return;
                }
                g.drawString(this.font, part, x + drawn, y, color, false);
                drawn += partW;
            }
            if (!isTab) {
                return;
            }
            if (drawn + 2 < max) {
                g.fill(x + drawn + 1, y, x + drawn + 2, y + 9, 0xFF5C6A80);
            }
            drawn += tab;
            start = i + 1;
            if (drawn >= max) {
                return;
            }
        }
    }

    private static boolean hover(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void drawGear(GuiGraphics g, int cx, int cy, int color) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        for (int i = 0; i < 8; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 45f));
            g.fill(-1, -6, 1, -3, color);
            pose.popPose();
        }
        g.fill(-2, -2, 2, 2, color);
        pose.popPose();
    }
}
