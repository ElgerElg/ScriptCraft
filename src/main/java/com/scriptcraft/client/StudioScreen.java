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
import com.scriptcraft.network.StudioLivePacket;
import com.scriptcraft.script.ScriptLog;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Окно мода на время сессии: главная, журнал и вкладки скриптов сервера.
 * По умолчанию на весь экран. Свернуть оставляет заголовок, закрытие — только кнопкой или Esc;
 * снова открывается тем же биндом. В окне его двигают за заголовок и тянут за края.
 */
@OnlyIn(Dist.CLIENT)
public class StudioScreen extends Screen {
    private static final int BG = 0xF210151E;
    private static final int SURFACE = 0xF2161D29;
    private static final int BAR = 0xF518202D;
    private static final int EDGE = 0xFF2B364A;
    private static final int EDGE_HOT = 0xFF425474;
    private static final int GOLD = 0xFFF0B85B;
    private static final int ACCENT = 0xFF58A6FF;
    private static final int GREEN = 0xFF4CC38A;
    private static final int RED = 0xFFE56B6F;
    private static final int TEXT = 0xFFE8EDF5;
    private static final int MUTED = 0xFF8A96AA;
    private static final int ROW = 0xFF1D2636;
    private static final int ROW_HOT = 0xFF27344A;
    private static final int YELLOW = 0x55D2A84B;
    private static final int BLUE = 0x553B7DD8;
    private static final int QUOTE = 0x4438B070;
    private static final int BAR_W = 8;
    private static final int GUTTER_W = 46;
    private static final int LINE_H = 12;
    private static final int TITLE = 24;
    private static final int MENU_H = 17;
    private static final int TOOL_H = 22;

    private enum Drag { NONE, MOVE, N, S, E, W, NE, NW, SE, SW, TEXT, JOURNAL, VSCROLL, HSCROLL }

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

    // --- Анимации UI ---
    private final long openedAt = System.currentTimeMillis();
    private long lastFrame = System.currentTimeMillis();
    private float dt = 0.016f;
    private float gearAngle;
    private long menuOpenedAt;
    private long catalogOpenedAt;
    private String lastActiveView = "";
    private long viewChangedAt = System.currentTimeMillis();
    private final Map<String, Float> hoverAnim = new HashMap<>();

    private int px, py, pw, ph;

    public StudioScreen() {
        super(Component.literal("ScriptCraft"));
    }

    @Override
    public void onClose() {
        StudioSync.leave();
        super.onClose();
    }

    private float easeOutCubic(float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        float inv = 1f - clamped;
        return 1f - inv * inv * inv;
    }

    private float animHover(String key, boolean hot) {
        float cur = hoverAnim.getOrDefault(key, 0f);
        float target = hot ? 1f : 0f;
        float next = cur + (target - cur) * Math.min(1f, dt * 14f);
        if (Math.abs(next - target) < 0.005f) {
            next = target;
        }
        hoverAnim.put(key, next);
        return next;
    }

    private static int lerpColor(int a, int b, float t) {
        float c = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * c) << 24)
                | ((int) (ar + (br - ar) * c) << 16)
                | ((int) (ag + (bg - ag) * c) << 8)
                | (int) (ab + (bb - ab) * c);
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)))));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        dt = Math.max(0.001f, Math.min(0.08f, (now - lastFrame) / 1000f));
        lastFrame = now;
        float openT = easeOutCubic((now - openedAt) / 190f);
        String currentView = settings ? "settings" : String.valueOf(StudioSession.active);
        if (!currentView.equals(lastActiveView)) {
            lastActiveView = currentView;
            viewChangedAt = now;
        }

        tip = null;
        preview = null;
        layout();
        g.fillGradient(0, 0, this.width, this.height, withAlpha(0x78070B12, openT), withAlpha(0x96090D16, openT));

        int slideY = Math.round((1f - openT) * 10f);
        g.pose().pushPose();
        g.pose().translate(0, slideY, 0);

        // Тень окна в оконном режиме
        if (!StudioSession.maximized && !StudioSession.minimized) {
            g.fill(px - 3, py - 2, px + pw + 3, py + ph + 4, withAlpha(0x55000000, openT));
        }
        g.fill(px, py, px + pw, py + ph, BG);
        // Тонкая акцентная линия сверху окна
        g.fillGradient(px, py, px + pw, py + 1, withAlpha(GOLD, 0.75f), withAlpha(ACCENT, 0.55f));
        border(g, px, py, pw, ph, EDGE);
        renderTitle(g, mouseX, mouseY - slideY);
        if (StudioSession.minimized) {
            g.pose().popPose();
            drawTip(g);
            trackPresence();
            return;
        }
        renderMenuRow(g, mouseX, mouseY - slideY);
        if (script() != null && !settings) {
            renderToolbar(g, mouseX, mouseY - slideY);
        }
        if (settings) {
            renderSettings(g, mouseX, mouseY - slideY);
        } else if (StudioSession.HOME.equals(StudioSession.active)) {
            renderHome(g, mouseX, mouseY - slideY);
        } else if (StudioSession.JOURNAL.equals(StudioSession.active)) {
            renderJournal(g, mouseX, mouseY - slideY);
        } else if (script() != null) {
            renderEditor(g, mouseX, mouseY - slideY);
        }
        if (menu != null) {
            renderMenu(g, mouseX, mouseY - slideY);
        }
        if (catalog) {
            renderCatalog(g, mouseX, mouseY - slideY);
        }
        if (naming) {
            renderNameBox(g);
        }
        if (ask != null) {
            renderAsk(g, mouseX, mouseY - slideY);
        }
        g.pose().popPose();
        drawTip(g);
        trackPresence();
    }

    /** Курсор уходит на сервер, только пока открыт сам скрипт и сервер включил совместную правку. */
    private void trackPresence() {
        ScriptDocument doc = settings ? null : script();
        if (doc == null || !doc.loaded) {
            StudioSync.leave();
        } else {
            StudioSync.watch(doc);
        }
    }

    private void renderTitle(GuiGraphics g, int mx, int my) {
        g.fill(px, py + 1, px + pw, py + TITLE, BAR);
        g.fill(px, py + TITLE - 1, px + pw, py + TITLE, EDGE);
        g.drawString(this.font, "ScriptCraft", px + 10, py + 8, GOLD, false);
        int x = px + 10 + this.font.width("ScriptCraft") + 12;
        int limit = px + pw - 82;
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
        boolean plusHot = hover(mx, my, x, py + 4, 16, 16);
        float plusT = animHover("tab:plus", plusHot || catalog);
        int plusBg = lerpColor(0xFF1D2534, 0xFF2D3B52, plusT);
        g.fill(x, py + 4, x + 16, py + 20, plusBg);
        border(g, x, py + 4, 16, 16, lerpColor(EDGE, GOLD, plusT * 0.7f));
        g.drawString(this.font, "+", x + 5, py + 8, lerpColor(MUTED, GOLD, plusT), false);
        plusX = x;
        int by = py + 5;
        windowButton(g, mx, my, px + pw - 70, by, "gear");
        windowButton(g, mx, my, px + pw - 52, by, "min");
        windowButton(g, mx, my, px + pw - 34, by, StudioSession.maximized ? "restore" : "max");
        windowButton(g, mx, my, px + pw - 18, by, "close");
    }

    private int plusX;

    private int tab(GuiGraphics g, int mx, int my, int x, String label, String id, boolean closable) {
        boolean dirty = dirty(id);
        int tw = tabInner(label, closable, dirty);
        boolean on = id.equals(StudioSession.active) && !settings;
        boolean hot = hover(mx, my, x, py + 3, tw, TITLE - 4);
        float h = animHover("tab:" + id, hot);
        int bg = on ? 0xFF232E40 : lerpColor(0xFF161D29, 0xFF202A3B, h);
        g.fill(x, py + 3, x + tw, py + TITLE - 1, bg);
        if (on) {
            float tabT = easeOutCubic((System.currentTimeMillis() - viewChangedAt) / 180f);
            int barW = Math.max(4, Math.round(tw * tabT));
            int bx = x + (tw - barW) / 2;
            g.fill(bx, py + TITLE - 2, bx + barW, py + TITLE, GOLD);
            g.fill(x, py + 3, x + tw, py + 4, withAlpha(GOLD, 0.25f));
        } else if (h > 0.02f) {
            g.fill(x, py + TITLE - 2, x + tw, py + TITLE - 1, withAlpha(ACCENT, h * 0.65f));
        }
        int textX = x + 6;
        if (dirty) {
            drawPencil(g, x + 5, py + 8, GOLD);
            textX += 10;
        }
        int textColor = on ? TEXT : lerpColor(MUTED, TEXT, h);
        g.drawString(this.font, trim(label, tw - (closable ? 20 : 10) - (dirty ? 10 : 0)), textX, py + 8, textColor, false);
        if (closable) {
            boolean closeHot = hover(mx, my, x + tw - 12, py + 4, 11, TITLE - 6);
            g.drawString(this.font, "x", x + tw - 10, py + 8, closeHot ? RED : (hot ? GOLD : MUTED), false);
        }
        return x + tw + 3;
    }

    private static boolean dirty(String id) {
        ScriptDocument doc = StudioSession.docs.get(id);
        return doc != null && doc.dirty();
    }

    private int tabInner(String label, boolean closable, boolean dirty) {
        return Math.min(156, this.font.width(label) + (closable ? 24 : 14) + (dirty ? 10 : 0));
    }

    private void windowButton(GuiGraphics g, int mx, int my, int x, int y, String kind) {
        boolean hot = hover(mx, my, x, y, 14, 14);
        float h = animHover("win:" + kind, hot || ("gear".equals(kind) && settings));
        if (h > 0.02f) {
            int hoverBg = "close".equals(kind) ? withAlpha(RED, h * 0.28f) : withAlpha(0xFF33435E, h * 0.8f);
            g.fill(x, y, x + 14, y + 14, hoverBg);
        }
        int color = "close".equals(kind) ? lerpColor(MUTED, RED, h) : lerpColor(MUTED, GOLD, h);
        if ("gear".equals(kind)) {
            if (settings || hot) {
                gearAngle = (gearAngle + dt * 95f) % 360f;
            }
            drawGear(g, x + 7, y + 7, color, gearAngle);
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
        g.fill(px + 1, y, px + pw - 1, y + MENU_H, 0xF2141A24);
        g.fill(px + 1, y + MENU_H - 1, px + pw - 1, y + MENU_H, 0xFF212A3A);
        int x = px + 6;
        x = menuWord(g, mx, my, x, y, "file", Messages.get("studio.m.file"));
        x = menuWord(g, mx, my, x, y, "edit", Messages.get("studio.m.edit"));
        x = menuWord(g, mx, my, x, y, "search", Messages.get("studio.m.search"));
        menuWord(g, mx, my, x, y, "view", Messages.get("studio.m.view"));
    }

    private int menuWord(GuiGraphics g, int mx, int my, int x, int y, String id, String label) {
        int w = this.font.width(label) + 12;
        boolean on = id.equals(menu) || hover(mx, my, x, y, w, MENU_H);
        float h = animHover("menu:" + id, on);
        if (h > 0.02f) {
            g.fill(x, y + 1, x + w, y + MENU_H - 1, withAlpha(ROW_HOT, h));
        }
        g.drawString(this.font, label, x + 6, y + 4, lerpColor(MUTED, TEXT, h), false);
        return x + w;
    }

    private void renderToolbar(GuiGraphics g, int mx, int my) {
        int y = py + TITLE + MENU_H;
        g.fill(px + 1, y, px + pw - 1, y + TOOL_H, 0xF211161F);
        g.fill(px + 1, y + TOOL_H - 1, px + pw - 1, y + TOOL_H, 0xFF202938);
        int x = px + 8;
        x = tool(g, mx, my, x, y, "new", Messages.get("studio.m.new"));
        x = tool(g, mx, my, x, y, "open", Messages.get("studio.m.open"));
        x = tool(g, mx, my, x, y, "save", Messages.get("studio.m.save"));
        x = tool(g, mx, my, x, y, "saveall", Messages.get("studio.m.save_all"));
        x = tool(g, mx, my, x, y, "closetab", Messages.get("studio.m.close"));
        x = tool(g, mx, my, x, y, "delete", Messages.get("studio.m.delete"));
        g.fill(x + 2, y + 4, x + 3, y + TOOL_H - 4, EDGE);
        x += 8;
        x = tool(g, mx, my, x, y, "cut", Messages.get("studio.m.cut"));
        x = tool(g, mx, my, x, y, "copy", Messages.get("studio.m.copy"));
        x = tool(g, mx, my, x, y, "paste", Messages.get("studio.m.paste"));
        g.fill(x + 2, y + 4, x + 3, y + TOOL_H - 4, EDGE);
        x += 8;
        x = tool(g, mx, my, x, y, "undo", Messages.get("studio.m.undo"));
        tool(g, mx, my, x, y, "redo", Messages.get("studio.m.redo"));

        // Аватары участников совместной правки в правой части тулбара
        ScriptDocument doc = script();
        if (doc != null && doc.live) {
            List<StudioLivePacket.Peer> roomPeers = StudioSession.peers(doc.name);
            UUID selfId = this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getUUID() : null;
            int rx = px + pw - 12;
            for (int i = roomPeers.size() - 1; i >= 0; i--) {
                StudioLivePacket.Peer peer = roomPeers.get(i);
                if (selfId != null && selfId.equals(peer.id())) {
                    continue;
                }
                rx -= 14;
                if (rx < x + 30) {
                    break;
                }
                int pc = peerColor(peer.name());
                int hy = y + 6;
                g.fill(rx - 1, hy - 1, rx + 11, hy + 11, pc);
                blitHead(g, peer.id(), rx, hy, 10);
                if (hover(mx, my, rx - 1, hy - 1, 12, 12)) {
                    tip = peer.name() + " (Ln " + (peer.line() + 1) + ")";
                    tipX = mx - this.font.width(tip) - 6;
                    tipY = my + 14;
                }
            }
        }
    }

    private int tool(GuiGraphics g, int mx, int my, int x, int y, String id, String label) {
        boolean hot = hover(mx, my, x, y + 2, 18, 18);
        float h = animHover("tool:" + id, hot);
        if (h > 0.02f) {
            g.fill(x, y + 2, x + 18, y + 20, withAlpha(ROW_HOT, h));
            border(g, x, y + 2, 18, 18, withAlpha(EDGE_HOT, h));
        }
        if (hot) {
            tip = label;
            tipX = mx + 6;
            tipY = my + 16;
        }
        drawToolIcon(g, x + 3, y + 5, id, lerpColor(MUTED, GOLD, h));
        return x + 20;
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
        int cx = px + pw / 2;

        // Мягкий вертикальный фон на главной
        g.fillGradient(px + 1, y, px + pw - 1, y + h, 0xFF111722, 0xFF0D111A);

        // Заголовок + бейдж версии + анимированный слоган
        int titleY = y + Math.max(12, Math.min(26, h / 14));
        float titleScale = pw > 520 ? 2.2f : 1.7f;
        g.pose().pushPose();
        g.pose().translate(cx, titleY, 0);
        g.pose().scale(titleScale, titleScale, 1f);
        String title = "ScriptCraft";
        g.drawString(this.font, title, -this.font.width(title) / 2, 0, GOLD, false);
        g.pose().popPose();

        int accentY = titleY + Math.round(10 * titleScale) + 3;
        g.fillGradient(cx - 46, accentY, cx + 46, accentY + 1, withAlpha(GOLD, 0.85f), withAlpha(ACCENT, 0.75f));

        String ver = "v" + modVersion();
        int verW = this.font.width(ver) + 10;
        int verX = cx - verW / 2;
        int verY = accentY + 6;
        g.fill(verX, verY, verX + verW, verY + 13, 0xFF1C2536);
        border(g, verX, verY, verW, 13, EDGE);
        g.drawString(this.font, ver, verX + 5, verY + 3, GOLD, false);

        String[] tags = {"studio.tag.1", "studio.tag.2", "studio.tag.3", "studio.tag.4", "studio.tag.5"};
        long cycle = 4200L;
        long now = System.currentTimeMillis();
        int tagIdx = (int) ((now / cycle) % tags.length);
        float phase = (now % cycle) / (float) cycle;
        float tagAlpha = phase < 0.1f ? phase / 0.1f : (phase > 0.9f ? (1f - phase) / 0.1f : 1f);
        String tag = Messages.get(tags[tagIdx]);
        int tagY = verY + 18;
        g.drawString(this.font, tag, cx - this.font.width(tag) / 2, tagY, withAlpha(TEXT, Math.max(0.15f, tagAlpha)), false);

        // Центральный блок: слева карточка разработчика (уменьшенная моделька), справа кнопки-ссылки
        int heroTop = tagY + 18;
        int availH = Math.max(120, y + h - heroTop - 12);
        int gap = 16;
        int devW = Math.max(136, Math.min(176, (pw - 48) / 3));
        int btnW = Math.max(176, Math.min(250, pw - devW - gap - 40));
        int totalW = devW + gap + btnW;
        int heroX = cx - totalW / 2;
        int cardH = Math.max(124, Math.min(188, availH));
        int heroY = heroTop + Math.max(0, (availH - cardH) / 2);

        // --- Карточка разработчика ---
        g.fill(heroX, heroY, heroX + devW, heroY + cardH, SURFACE);
        border(g, heroX, heroY, devW, cardH, EDGE);
        g.fillGradient(heroX + 1, heroY + 1, heroX + devW - 1, heroY + 2, withAlpha(GOLD, 0.7f), withAlpha(GOLD, 0.15f));

        int devCenter = heroX + devW / 2;
        int nameBlock = 30;
        int modelAreaH = Math.max(64, cardH - nameBlock - 12);
        // Моделька аккуратная и компактная
        float scale = Math.max(24f, Math.min(38f, modelAreaH / 2.25f));
        int modelTop = heroY + 10 + Math.round((modelAreaH - scale * 1.95f) / 2f) + Math.round(scale * 0.48f);
        // Мягкая подставка-тень под ногами
        int pedY = modelTop + Math.round(scale * 1.48f);
        g.fill(devCenter - 26, pedY, devCenter + 26, pedY + 2, withAlpha(GOLD, 0.18f));
        SkinPortrait.render(g, devCenter, modelTop, scale);

        String dev = "ElgerElg";
        int foot = heroY + cardH - 25;
        g.drawString(this.font, dev, devCenter - this.font.width(dev) / 2, foot, GOLD, false);
        String role = Messages.get("studio.role");
        g.drawString(this.font, role, devCenter - this.font.width(role) / 2, foot + 11, MUTED, false);

        // --- Колонка кнопок-ссылок ---
        int rightX = heroX + devW + gap;
        g.fill(rightX, heroY, rightX + btnW, heroY + cardH, SURFACE);
        border(g, rightX, heroY, btnW, cardH, EDGE);
        g.fillGradient(rightX + 1, heroY + 1, rightX + btnW - 1, heroY + 2, withAlpha(ACCENT, 0.7f), withAlpha(ACCENT, 0.15f));

        int bx = rightX + 12;
        int bw = btnW - 24;
        g.drawString(this.font, Messages.get("studio.links"), bx, heroY + 10, GOLD, false);
        int btnAreaTop = heroY + 26;
        int btnAreaH = cardH - 36;
        int btnGap = 6;
        int btnH = Math.max(20, Math.min(28, (btnAreaH - btnGap * 3) / 4));
        int row = btnAreaTop + Math.max(0, (btnAreaH - (btnH * 4 + btnGap * 3)) / 2);

        row = linkButton(g, mx, my, bx, row, bw, btnH, "github", Messages.get("studio.link.github"),
                "https://github.com/ElgerElg/ScriptCraft", false) + btnGap;
        row = linkButton(g, mx, my, bx, row, bw, btnH, "docs", Messages.get("studio.link.docs"),
                "https://elgerelg.github.io/ScriptCraft/", false) + btnGap;
        row = linkButton(g, mx, my, bx, row, bw, btnH, "examples", Messages.get("studio.link.examples"),
                "https://github.com/ElgerElg/ScriptCraft/tree/main/examples", false) + btnGap;
        linkButton(g, mx, my, bx, row, bw, btnH, "folder", Messages.get("studio.link.folder"),
                ModPaths.scripts().toString(), true);
    }

    private int linkButton(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String icon, String label, String target, boolean file) {
        boolean hot = hover(mx, my, x, y, w, h);
        float t = animHover("home:" + icon, hot);
        int bg = lerpColor(ROW, ROW_HOT, t);
        int brd = lerpColor(EDGE, file ? GOLD : ACCENT, t * 0.85f);
        g.fill(x, y, x + w, y + h, bg);
        border(g, x, y, w, h, brd);
        // Левый акцентный индикатор
        int accentColor = file ? GOLD : ACCENT;
        int barH = Math.max(4, Math.round((h - 4) * (0.35f + 0.65f * t)));
        int barY = y + (h - barH) / 2;
        g.fill(x + 1, barY, x + 3, barY + barH, withAlpha(accentColor, 0.5f + 0.5f * t));

        // Иконка
        int ix = x + 10;
        int iy = y + (h - 10) / 2;
        drawHomeIcon(g, ix, iy, icon, lerpColor(MUTED, accentColor, t));

        // Текст кнопки
        int ty = y + (h - 8) / 2;
        g.drawString(this.font, trim(label, w - 38), ix + 16, ty, lerpColor(TEXT, 0xFFFFFFFF, t), false);

        // Стрелка справа с плавным сдвигом при наведении
        int arrowX = x + w - 14 + Math.round(t * 3f);
        g.drawString(this.font, "→", arrowX, ty, lerpColor(MUTED, accentColor, t), false);

        homeLinks.add(new HomeLink(x, y, w, h, target, file));
        return y + h;
    }

    private void drawHomeIcon(GuiGraphics g, int x, int y, String icon, int c) {
        switch (icon) {
            case "github" -> {
                g.fill(x + 2, y + 1, x + 8, y + 9, c);
                g.fill(x + 1, y + 2, x + 9, y + 8, c);
                g.fill(x + 3, y + 3, x + 7, y + 7, ROW);
                g.fill(x + 4, y + 6, x + 6, y + 9, c);
            }
            case "docs" -> {
                g.fill(x + 1, y + 1, x + 9, y + 9, c);
                g.fill(x + 2, y + 2, x + 8, y + 8, ROW);
                g.fill(x + 3, y + 3, x + 7, y + 4, c);
                g.fill(x + 3, y + 5, x + 7, y + 6, c);
                g.fill(x + 3, y + 7, x + 6, y + 8, c);
            }
            case "examples" -> {
                g.fill(x + 1, y + 4, x + 3, y + 6, c);
                g.fill(x + 2, y + 3, x + 4, y + 4, c);
                g.fill(x + 2, y + 6, x + 4, y + 7, c);
                g.fill(x + 7, y + 4, x + 9, y + 6, c);
                g.fill(x + 6, y + 3, x + 8, y + 4, c);
                g.fill(x + 6, y + 6, x + 8, y + 7, c);
                g.fill(x + 4, y + 2, x + 6, y + 8, c);
            }
            default -> {
                g.fill(x, y + 3, x + 10, y + 9, c);
                g.fill(x + 1, y + 4, x + 9, y + 8, ROW);
                g.fill(x, y + 1, x + 5, y + 3, c);
            }
        }
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
        copyBtnY = -1;
        journalTextX = -1;
        int y = contentY();
        int h = contentH();
        int listW = Math.max(180, (pw - 16) / 2);
        g.fill(px + 8, y, px + 8 + listW, y + h, 0xF0181E28);
        g.fill(px + 12 + listW, y, px + pw - 8, y + h, 0xF0181E28);
        int content = 4 + 16 + (StudioSession.issues.isEmpty() ? 16 : StudioSession.issues.size() * 24)
                + 6 + 14 + Math.max(1, StudioSession.history.size()) * 22 + 8;
        listMax = Math.max(0, content - h);
        listScroll = Math.max(0, Math.min(listScroll, listMax));
        if (!StudioSession.issues.isEmpty()) {
            journal = Math.max(0, Math.min(StudioSession.issues.size() - 1, journal));
        }
        journalHits.clear();
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
                journalHits.add(new JournalHit(row, 22, 0, i));
                boolean on = historySel < 0 && i == journal;
                g.fill(px + 12, row, px + 4 + listW, row + 22, on ? 0xFF2E3A4E : ROW);
                String where = issue.file().isEmpty() ? "—" : issue.file();
                if (issue.line() > 0) {
                    where = where + ":" + issue.line();
                }
                g.drawString(this.font, trim(where, listW - 20), px + 16, row + 2, on ? GOLD : TEXT, false);
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
            if (row + 22 >= y && row <= y + h) {
                journalHits.add(new JournalHit(row, 22, 1, i));
                boolean on = i == historySel;
                if (on) {
                    g.fill(px + 12, row, px + 4 + listW, row + 20, 0xFF2E3A4E);
                }
                g.drawString(this.font, trim(StudioSession.history.get(i).title, listW - 20), px + 16, row + 4, on ? GOLD : MUTED, false);
            }
            row += 22;
        }
        g.disableScissor();
        journalListW = listW;
        journalY = y;
        journalH = h;
        if (historySel >= 0 && historySel < StudioSession.history.size()) {
            StudioSession.Note note = StudioSession.history.get(historySel);
            boolean code = !note.lines.isEmpty();
            List<String> rows = code ? note.lines : noteBody(note.body);
            drawJournalDetail(g, mx, my, y, h, listW, rows, code ? note.firstLine : 0,
                    code ? note.line : -1, code ? note.body : "", code, historySel + 1);
            return;
        }
        if (StudioSession.issues.isEmpty()) {
            g.drawString(this.font, Messages.get("log.empty"), px + 16 + listW, y + 8, MUTED, false);
            codeMax = 0;
            return;
        }
        ScriptLog.Issue current = StudioSession.issues.get(journal);
        ScriptLog.Slice slice = slice(current.file());
        List<String> rows = slice == null ? List.of() : slice.lines();
        drawJournalDetail(g, mx, my, y, h, listW, rows, slice == null ? 0 : slice.firstLine(), current.line(),
                current.reason(), slice != null && !slice.lines().isEmpty(), -journal - 1);
    }

    private void drawJournalDetail(GuiGraphics g, int mx, int my, int y, int h, int listW, List<String> rows,
                                   int firstNumber, int highlight, String reason, boolean numbers, int stamp) {
        if (journalStamp != stamp) {
            journalStamp = stamp;
            clearJournalSelection();
        }
        journalLines.clear();
        int codeX = px + 16 + listW;
        int codeW = Math.max(40, pw - listW - 28);
        journalTextX = codeX;
        journalTextW = codeW;
        drawCopyButtons(g, mx, my, codeX, y, codeW);
        journalViewTop = y + 18;
        journalViewBottom = Math.max(journalViewTop + LINE_H, y + h - 22);
        int viewH = journalViewBottom - journalViewTop;
        codeMax = Math.max(0, rows.size() * LINE_H - viewH);
        codeScroll = Math.max(0, Math.min(codeScroll, codeMax));
        g.enableScissor(codeX, journalViewTop, codeX + codeW, journalViewBottom);
        if (rows.isEmpty()) {
            journalLine(g, journalViewTop + 4, codeX + 6, Messages.get("log.no_file"), MUTED, true, true, false);
        } else {
            int ly = journalViewTop - codeScroll;
            for (int i = 0; i < rows.size(); i++) {
                int number = firstNumber + i;
                boolean visible = ly + LINE_H > journalViewTop && ly < journalViewBottom;
                if (visible && numbers && number == highlight) {
                    g.fill(codeX, ly - 1, codeX + codeW, ly + LINE_H - 1, 0xAAD25A5A);
                }
                if (visible && numbers) {
                    String num = Integer.toString(number);
                    g.drawString(this.font, num, codeX + 28 - this.font.width(num), ly, MUTED, false);
                }
                int textX = codeX + (numbers ? 34 : 6);
                journalLine(g, ly, textX, rows.get(i), TEXT, !numbers, visible, false);
                ly += LINE_H;
            }
        }
        g.disableScissor();
        if (reason != null && !reason.isBlank()) {
            String line = trim(Messages.get("log.reason") + ": " + reason, codeW);
            journalLine(g, y + h - 14, codeX, line, 0xFFF0C2C2, true, true, true);
        }
    }

    private void drawCopyButtons(GuiGraphics g, int mx, int my, int x, int y, int width) {
        String copy = Messages.get("studio.copy");
        String all = Messages.get("studio.copy_all");
        copyBtnW = this.font.width(copy) + 10;
        copyAllW = this.font.width(all) + 10;
        copyBtnX = x;
        copyBtnY = y + 3;
        paintJournalButton(g, mx, my, copyBtnX, copyBtnY, copyBtnW, copy, Messages.get("studio.copy_hint"));
        int next = copyBtnX + copyBtnW + 4;
        if (next + copyAllW <= x + width) {
            copyAllX = next;
            paintJournalButton(g, mx, my, copyAllX, copyBtnY, copyAllW, all, Messages.get("studio.copy_all_hint"));
        } else {
            copyAllX = -1;
        }
    }

    private void paintJournalButton(GuiGraphics g, int mx, int my, int x, int y, int w, String label, String hint) {
        boolean hot = hover(mx, my, x, y, w, 12);
        g.fill(x, y, x + w, y + 12, hot ? 0xFF2E3A4E : ROW);
        g.drawString(this.font, label, x + 5, y + 2, hot ? GOLD : TEXT, false);
        if (hot) {
            tip = hint;
            tipX = mx + 8;
            tipY = my;
        }
    }

    private void journalLine(GuiGraphics g, int y, int x, String text, int color, boolean plain, boolean visible, boolean footer) {
        int index = journalLines.size();
        String shown = text == null ? "" : text;
        journalLines.add(new JLine(y, x, shown, plain, footer));
        if (!visible) {
            return;
        }
        paintJournalSelection(g, index, y, x, shown, plain);
        if (plain) {
            g.drawString(this.font, shown, x, y, color, false);
        } else {
            ScriptColors.draw(g, this.font, shown, x, y, color, 8000);
        }
    }

    private void paintJournalSelection(GuiGraphics g, int index, int y, int x, String text, boolean plain) {
        if (selA < 0 || selB < 0) {
            return;
        }
        int a = selA;
        int b = selB;
        int ca = colA;
        int cb = colB;
        if (a > b || (a == b && ca > cb)) {
            int swap = a;
            a = b;
            b = swap;
            swap = ca;
            ca = cb;
            cb = swap;
        }
        if (index < a || index > b) {
            return;
        }
        int from = index == a ? ca : 0;
        int to = index == b ? cb : text.length();
        from = Math.max(0, Math.min(from, text.length()));
        to = Math.max(from, Math.min(to, text.length()));
        if (from == to) {
            return;
        }
        int x0 = x + journalWidth(text, from, plain);
        int x1 = x + journalWidth(text, to, plain);
        g.fill(x0, y, Math.max(x0 + 1, x1), y + LINE_H - 1, 0x883A6EA8);
    }

    private int journalWidth(String text, int count, boolean plain) {
        count = Math.max(0, Math.min(count, text.length()));
        if (plain) {
            return this.font.width(text.substring(0, count));
        }
        return ScriptColors.width(this.font, text, count);
    }

    private int historySel = -1;
    private final java.util.ArrayList<JournalHit> journalHits = new java.util.ArrayList<>();
    private final List<JLine> journalLines = new ArrayList<>();
    private int selA = -1;
    private int selB = -1;
    private int colA;
    private int colB;
    private int journalStamp = Integer.MIN_VALUE;
    private int copyBtnX;
    private int copyBtnY = -1;
    private int copyBtnW;
    private int copyAllX = -1;
    private int copyAllW;
    private int journalTextX = -1;
    private int journalTextW;
    private int journalViewTop;
    private int journalViewBottom;

    private record JLine(int y, int x, String text, boolean plain, boolean footer) {
    }

    private static List<String> noteBody(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.asList(body.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1));
    }

    private record JournalHit(int y, int h, int kind, int index) {
    }

    private int journalListW, journalY, journalH, listMax, codeMax;

    private void renderEditor(GuiGraphics g, int mx, int my) {
        ScriptDocument doc = script();
        int y = contentY() + TOOL_H;
        int status = 18;
        int findH = findOn ? 18 : 0;
        int h = py + ph - y - status - findH;
        editorX = px + 6;
        editorY = y + 2;
        editorW = pw - 12;
        editorH = Math.max(24, h - 4);
        g.fill(editorX, editorY, editorX + editorW, editorY + editorH, 0xF50C1018);
        border(g, editorX, editorY, editorW, editorH, 0xFF212B3C);
        if (!doc.loaded) {
            g.drawString(this.font, Messages.get("studio.loading"), editorX + 10, editorY + 10, MUTED, false);
        } else {
            int textW = Math.max(40, editorW - GUTTER_W - 8 - BAR_W);
            horizMax = 0;
            for (String row : doc.lines) {
                horizMax = Math.max(horizMax, spanWidth(row, row.length()) - textW + 8);
            }
            horizMax = Math.max(0, horizMax);
            boolean wide = horizMax > 0;
            int view = Math.max(1, (editorH - (wide ? BAR_W : 0)) / LINE_H);
            if (doc.followCaret) {
                doc.ensureVisible(view, LINE_H);
                revealCaret(doc, textW);
                doc.followCaret = false;
            }
            int maxScroll = Math.max(0, doc.lines.size() - view);
            scrollMax = maxScroll;
            if (doc.scroll > maxScroll) {
                doc.scroll = maxScroll;
            }
            if (doc.horiz > horizMax) {
                doc.horiz = horizMax;
            }
            int textRight = editorX + editorW - BAR_W;
            int textBottom = editorY + editorH - (wide ? BAR_W : 0);

            // Фон левой полосы номеров строк (gutter) и разделитель
            g.fill(editorX + 1, editorY + 1, editorX + GUTTER_W - 4, textBottom, 0xFF111722);
            g.fill(editorX + GUTTER_W - 4, editorY + 1, editorX + GUTTER_W - 3, textBottom, 0xFF222C3E);

            for (int i = doc.scroll; i < doc.lines.size(); i++) {
                int ly = editorY + (i - doc.scroll) * LINE_H + 2;
                if (ly > textBottom) {
                    break;
                }
                if (i == doc.line) {
                    g.fill(editorX + 1, ly - 1, textRight, ly + LINE_H - 1, 0xFF162031);
                    g.fill(editorX + 1, ly - 1, editorX + 3, ly + LINE_H - 1, GOLD);
                }
            }
            List<StudioLivePacket.Peer> roomPeers = StudioSession.peers(doc.name);
            UUID selfId = this.minecraft.player == null ? null : this.minecraft.player.getUUID();
            StudioLivePacket.Peer hoveredPeer = null;
            String peerHover = null;
            g.enableScissor(editorX + GUTTER_W - 2, editorY + 1, textRight, textBottom);
            for (int i = doc.scroll; i < doc.lines.size(); i++) {
                int ly = editorY + (i - doc.scroll) * LINE_H + 2;
                if (ly > textBottom) {
                    break;
                }
                String line = doc.lines.get(i);
                if (peerHover == null && my >= ly - 1 && my < ly + LINE_H - 1 && mx >= editorX && mx < textRight) {
                    peerHover = peerNames(roomPeers, selfId, i);
                    if (peerHover != null) {
                        for (StudioLivePacket.Peer p : roomPeers) {
                            if (p.line() == i && (selfId == null || !selfId.equals(p.id()))) {
                                hoveredPeer = p;
                                break;
                            }
                        }
                    }
                }
                for (StudioLivePacket.Peer peer : roomPeers) {
                    if (selfId != null && selfId.equals(peer.id())) {
                        continue;
                    }
                    int[] span = peerSpan(peer, i, line.length());
                    if (span != null && span[1] > span[0]) {
                        int x0 = textX(line, span[0]);
                        int x1 = textX(line, span[1]);
                        g.fill(x0, ly - 1, Math.max(x1, x0 + 2), ly + LINE_H - 1, (peerColor(peer.name()) & 0xFFFFFF) | 0x55000000);
                    }
                }
                int[] sel = doc.selectionOn(i);
                if (sel != null) {
                    int x0 = textX(line, sel[0]);
                    int x1 = textX(line, sel[1]);
                    g.fill(x0, ly - 1, Math.max(x1, x0 + 2), ly + LINE_H - 1, 0x883A6EA5);
                }
                for (ScriptMarkup.Mark mark : ScriptMarkup.of(line)) {
                    int x0 = textX(line, mark.from());
                    int x1 = textX(line, mark.to());
                    int color = switch (mark.kind()) {
                        case EVENT -> YELLOW;
                        case QUOTE -> QUOTE;
                        default -> BLUE;
                    };
                    g.fill(x0, ly - 1, Math.max(x1, x0 + 1), ly + LINE_H - 1, color);
                    if (mark.kind() == ScriptMarkup.Kind.RESOURCE
                            && hover(mx, my, x0, ly - 1, Math.max(1, x1 - x0), LINE_H)) {
                        TexturePreview.Image image = TexturePreview.lookup(mark.token());
                        if (image != null) {
                            preview = image;
                            previewX = mx + 12;
                            previewY = my + 12;
                        }
                    }
                }
                ScriptColors.draw(g, this.font, line, editorX + GUTTER_W - doc.horiz, ly, TEXT, doc.horiz + textW);
                if (i == doc.line) {
                    float pulse = 0.25f + 0.75f * (0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 180.0));
                    int cx = textX(line, Math.min(doc.col, line.length()));
                    g.fill(cx, ly - 1, cx + 1, ly + LINE_H - 2, withAlpha(TEXT, pulse));
                }
                // Курсор второго разработчика — только тонкая цветная полоска с шапочкой (голова рисуется в gutter и не перекрывает текст)
                for (StudioLivePacket.Peer peer : roomPeers) {
                    if ((selfId != null && selfId.equals(peer.id())) || peer.line() != i) {
                        continue;
                    }
                    int pc = peerColor(peer.name());
                    int cx = textX(line, Math.min(Math.max(0, peer.col()), line.length()));
                    g.fill(cx, ly - 1, cx + 2, ly + LINE_H - 1, pc);
                    g.fill(cx - 1, ly - 2, cx + 3, ly, pc);
                }
            }
            g.disableScissor();

            // Номера строк и головы участников в левом gutter (никогда не перекрывают код)
            g.enableScissor(editorX + 1, editorY + 1, editorX + GUTTER_W - 4, textBottom);
            for (int i = doc.scroll; i < doc.lines.size(); i++) {
                int ly = editorY + (i - doc.scroll) * LINE_H + 2;
                if (ly > textBottom) {
                    break;
                }
                String num = Integer.toString(i + 1);
                g.drawString(this.font, num, editorX + GUTTER_W - 8 - this.font.width(num), ly, i == doc.line ? GOLD : MUTED, false);
                for (StudioLivePacket.Peer peer : roomPeers) {
                    if ((selfId != null && selfId.equals(peer.id())) || peer.line() != i) {
                        continue;
                    }
                    int pc = peerColor(peer.name());
                    g.fill(editorX + 3, ly, editorX + 13, ly + 10, pc);
                    blitHead(g, peer.id(), editorX + 4, ly + 1, 8);
                    break;
                }
            }
            g.disableScissor();

            if (peerHover != null) {
                int tw = this.font.width(peerHover) + (hoveredPeer != null ? 14 : 0);
                int nameX = Math.min(mx + 10, px + pw - tw - 10);
                int nameY = Math.max(py + 2, my - 15);
                int accent = hoveredPeer != null ? peerColor(hoveredPeer.name()) : ACCENT;
                g.fill(nameX - 4, nameY - 2, nameX + tw + 4, nameY + 11, 0xF5111722);
                border(g, nameX - 4, nameY - 2, tw + 8, 13, accent);
                int tx = nameX;
                if (hoveredPeer != null) {
                    blitHead(g, hoveredPeer.id(), tx, nameY, 8);
                    tx += 11;
                }
                g.drawString(this.font, peerHover, tx, nameY + 1, TEXT, false);
            }

            vBarX = editorX + editorW - BAR_W;
            vBarY = editorY + 1;
            vBarH = textBottom - vBarY;
            thumbH = maxScroll == 0 ? vBarH : Math.max(14, vBarH * view / Math.max(view, doc.lines.size()));
            int track = Math.max(1, vBarH - thumbH);
            thumbY = vBarY + (maxScroll == 0 ? 0 : (int) (doc.scroll / (float) maxScroll * track));
            boolean vHot = drag == Drag.VSCROLL || hover(mx, my, vBarX, vBarY, BAR_W, vBarH);
            float vT = animHover("scroll:v", vHot);
            g.fill(vBarX, vBarY, vBarX + BAR_W, vBarY + vBarH, 0xFF10151F);
            g.fill(vBarX + 1, thumbY, vBarX + BAR_W - 1, Math.min(vBarY + vBarH, thumbY + thumbH), lerpColor(0xFF3A4860, 0xFF637A9E, vT));
            hBar = wide;
            if (wide) {
                hBarX = editorX + 1;
                hBarY = editorY + editorH - BAR_W;
                hBarW = editorW - BAR_W - 1;
                int content = textW + horizMax;
                hThumbW = Math.max(14, hBarW * textW / Math.max(textW, content));
                int hTrack = Math.max(1, hBarW - hThumbW);
                hThumbX = hBarX + (int) (doc.horiz / (float) Math.max(1, horizMax) * hTrack);
                boolean hHot = drag == Drag.HSCROLL || hover(mx, my, hBarX, hBarY, hBarW, BAR_W);
                float hT = animHover("scroll:h", hHot);
                g.fill(hBarX, hBarY, hBarX + hBarW, hBarY + BAR_W, 0xFF10151F);
                g.fill(hThumbX, hBarY + 1, Math.min(hBarX + hBarW, hThumbX + hThumbW), hBarY + BAR_W - 1, lerpColor(0xFF3A4860, 0xFF637A9E, hT));
            }
        }
        int sy = editorY + editorH + 2;
        if (findOn) {
            g.fill(editorX, sy, editorX + editorW, sy + 16, BAR);
            border(g, editorX, sy, editorW, 16, findFocus ? ACCENT : EDGE);
            g.drawString(this.font, Messages.get("studio.find") + ": " + find + (findFocus ? "_" : ""), editorX + 6, sy + 4, TEXT, false);
        }
        String statusText = statusText(doc);
        int stY = py + ph - status;
        g.fill(px + 1, stY, px + pw - 1, py + ph - 1, BAR);
        g.fill(px + 1, stY, px + pw - 1, stY + 1, EDGE);

        // Правая часть статусбара: позиция курсора и индикатор LIVE
        int rightEdge = px + pw - 10;
        if (doc.live) {
            String liveLabel = "LIVE";
            int lw = this.font.width(liveLabel) + 14;
            int lx = rightEdge - lw;
            float lp = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 320.0);
            g.fill(lx, stY + 3, lx + lw, stY + status - 3, 0xFF162C22);
            border(g, lx, stY + 3, lw, status - 6, withAlpha(GREEN, 0.55f + 0.45f * lp));
            g.fill(lx + 4, stY + 7, lx + 7, stY + 10, withAlpha(GREEN, 0.45f + 0.55f * lp));
            g.drawString(this.font, liveLabel, lx + 10, stY + 5, GREEN, false);
            rightEdge = lx - 8;
        }
        String posLabel = "Ln " + (doc.line + 1) + ", Col " + (doc.col + 1);
        int posW = this.font.width(posLabel);
        g.drawString(this.font, posLabel, rightEdge - posW, stY + 5, MUTED, false);
        rightEdge -= posW + 14;

        // Левая часть статусбара: точка статуса и текст
        g.fill(px + 8, stY + 7, px + 12, stY + 11, statusBad ? RED : GREEN);
        int maxStatusW = Math.max(40, rightEdge - (px + 18));
        g.drawString(this.font, trim(statusText, maxStatusW), px + 16, stY + 5, statusBad ? 0xFFF4B8BA : MUTED, false);
        if (preview != null) {
            int w = Math.min(72, preview.width());
            int ph = Math.min(72, preview.height());
            int x = Math.min(previewX, px + pw - w - 8);
            int pyy = Math.min(previewY, this.py + this.ph - ph - 24);
            g.fill(x - 3, pyy - 3, x + w + 3, pyy + ph + 15, 0xF510151E);
            border(g, x - 3, pyy - 3, w + 6, ph + 18, EDGE_HOT);
            g.blit(preview.id(), x, pyy, w, ph, 0, 0, preview.width(), preview.height(), preview.width(), preview.height());
            g.drawString(this.font, preview.width() + "×" + preview.height(), x, pyy + ph + 3, TEXT, false);
        }
    }

    private int editorX, editorY, editorW, editorH;
    private int vBarX, vBarY, vBarH, thumbY, thumbH;
    private int hBarX, hBarY, hBarW, hThumbX, hThumbW;
    private boolean hBar;
    private int scrollMax, horizMax;
    private int pressX, pressY, scrollGrab;
    private boolean textDrag;

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
        int w = 156;
        int fullH = items.size() * 15 + 6;
        float t = easeOutCubic((System.currentTimeMillis() - menuOpenedAt) / 120f);
        int h = Math.max(8, Math.round(fullH * t));
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x44000000);
        g.fill(x, y, x + w, y + h, 0xF7121824);
        border(g, x, y, w, h, EDGE_HOT);
        g.enableScissor(x, y, x + w, y + h);
        for (int i = 0; i < items.size(); i++) {
            int iy = y + 3 + i * 15;
            boolean hot = hover(mx, my, x + 1, iy, w - 2, 15);
            float ht = animHover("drop:" + menu + ":" + i, hot);
            if (ht > 0.02f) {
                g.fill(x + 2, iy, x + w - 2, iy + 15, withAlpha(ROW_HOT, ht));
                g.fill(x + 2, iy + 2, x + 4, iy + 13, withAlpha(GOLD, ht));
            }
            g.drawString(this.font, items.get(i), x + 8, iy + 4, lerpColor(TEXT, 0xFFFFFFFF, ht), false);
        }
        g.disableScissor();
    }

    private void renderCatalog(GuiGraphics g, int mx, int my) {
        int x = Math.min(plusX, px + pw - 190);
        int y = py + TITLE;
        int w = 186;
        int rows = Math.max(1, Math.min(12, StudioSession.catalog.size()));
        int fullH = rows * 15 + 6;
        float t = easeOutCubic((System.currentTimeMillis() - catalogOpenedAt) / 130f);
        int h = Math.max(8, Math.round(fullH * t));
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x44000000);
        g.fill(x, y, x + w, y + h, 0xF7121824);
        border(g, x, y, w, h, EDGE_HOT);
        g.enableScissor(x, y, x + w, y + h);
        if (StudioSession.catalog.isEmpty()) {
            g.drawString(this.font, Messages.get("studio.empty"), x + 8, y + 5, MUTED, false);
            g.disableScissor();
            return;
        }
        for (int i = 0; i < rows; i++) {
            String name = StudioSession.catalog.get(i);
            int iy = y + 3 + i * 15;
            boolean hot = hover(mx, my, x + 1, iy, w - 2, 15);
            float ht = animHover("cat:" + i, hot);
            if (ht > 0.02f) {
                g.fill(x + 2, iy, x + w - 2, iy + 15, withAlpha(ROW_HOT, ht));
                g.fill(x + 2, iy + 2, x + 4, iy + 13, withAlpha(ACCENT, ht));
            }
            g.drawString(this.font, trim(name, w - 16), x + 8, iy + 4, lerpColor(TEXT, 0xFFFFFFFF, ht), false);
        }
        g.disableScissor();
    }

    private void renderNameBox(GuiGraphics g) {
        int x = px + pw / 2 - 96;
        int y = py + ph / 2 - 18;
        g.fill(x + 2, y + 2, x + 194, y + 38, 0x55000000);
        g.fill(x, y, x + 192, y + 36, 0xF7121824);
        border(g, x, y, 192, 36, GOLD);
        g.drawString(this.font, nameBuf.isEmpty() ? Messages.get("studio.new_name") : nameBuf + "_", x + 10, y + 14, nameBuf.isEmpty() ? MUTED : TEXT, false);
    }

    private void drawTip(GuiGraphics g) {
        if (tip == null) {
            return;
        }
        int w = this.font.width(tip) + 10;
        int tx = Math.max(4, Math.min(tipX, this.width - w - 4));
        int ty = Math.max(16, Math.min(tipY, this.height - 6));
        g.fill(tx, ty - 14, tx + w, ty, 0xF5111722);
        border(g, tx, ty - 14, w, 14, EDGE_HOT);
        g.drawString(this.font, tip, tx + 5, ty - 11, TEXT, false);
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
        if (menu != null && my >= py + TITLE + MENU_H && my < py + TITLE + MENU_H + menuItems().size() * 15 + 6
                && mx >= menuX() && mx < menuX() + 156) {
            int index = (my - (py + TITLE + MENU_H + 3)) / 15;
            runMenu(index);
            menu = null;
            return true;
        }
        menu = null;
        int catX = Math.min(plusX, px + pw - 190);
        if (catalog && my >= py + TITLE && mx >= catX && mx < catX + 186) {
            int index = (my - (py + TITLE + 3)) / 15;
            if (index >= 0 && index < StudioSession.catalog.size() && index < 12) {
                openScript(StudioSession.catalog.get(index));
            }
            catalog = false;
            return true;
        }
        catalog = false;
        if (my >= py && my < py + TITLE) {
            if (hover(mx, my, px + pw - 18, py + 5, 14, 14)) {
                onClose();
                return true;
            }
            if (hover(mx, my, px + pw - 34, py + 5, 14, 14)) {
                toggleMax();
                return true;
            }
            if (hover(mx, my, px + pw - 52, py + 5, 14, 14)) {
                StudioSession.minimized = !StudioSession.minimized;
                return true;
            }
            if (hover(mx, my, px + pw - 70, py + 5, 14, 14)) {
                settings = !settings;
                return true;
            }
            if (hover(mx, my, plusX, py + 4, 16, 16)) {
                catalog = true;
                catalogOpenedAt = System.currentTimeMillis();
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
            if (menu != null) {
                menuOpenedAt = System.currentTimeMillis();
            }
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
            findFocus = false;
            if (mx >= vBarX && mx < vBarX + BAR_W && my >= vBarY && my < vBarY + vBarH) {
                drag = Drag.VSCROLL;
                scrollGrab = my >= thumbY && my < thumbY + thumbH ? my - thumbY : thumbH / 2;
                moveVScroll(doc, my);
                return true;
            }
            if (hBar && my >= hBarY && my < hBarY + BAR_W && mx >= hBarX && mx < hBarX + hBarW) {
                drag = Drag.HSCROLL;
                scrollGrab = mx >= hThumbX && mx < hThumbX + hThumbW ? mx - hThumbX : hThumbW / 2;
                moveHScroll(doc, mx);
                return true;
            }
            pressX = mx;
            pressY = my;
            textDrag = false;
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
        if (drag == Drag.VSCROLL) {
            ScriptDocument doc = script();
            if (doc != null) {
                moveVScroll(doc, my);
            }
            return true;
        }
        if (drag == Drag.HSCROLL) {
            ScriptDocument doc = script();
            if (doc != null) {
                moveHScroll(doc, mx);
            }
            return true;
        }
        if (drag == Drag.JOURNAL) {
            placeJournalCaret((int) mouseX, (int) mouseY, true);
            return true;
        }
        if (drag == Drag.TEXT) {
            ScriptDocument doc = script();
            if (doc != null) {
                if (!textDrag && Math.abs(mx - pressX) + Math.abs(my - pressY) < 4) {
                    return true;
                }
                textDrag = true;
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
            if (hasShiftDown()) {
                doc.horiz = Math.max(0, Math.min(horizMax, doc.horiz + step * 12));
            } else {
                doc.scroll = Math.max(0, Math.min(scrollMax, doc.scroll + step));
            }
            doc.followCaret = false;
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
        if (StudioSession.JOURNAL.equals(StudioSession.active) && !settings) {
            if (ctrl && key == GLFW.GLFW_KEY_A) {
                selectAllJournal();
                return true;
            }
            if (ctrl && key == GLFW.GLFW_KEY_C) {
                copyJournalKey();
                return true;
            }
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
            if (ctrl) {
                doc.deleteWordForward();
            } else {
                doc.deleteForward();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            doc.insertNewLine();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            doc.insert("\t");
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT) {
            if (ctrl) {
                doc.moveWord(false, shift);
                return true;
            }
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
            if (ctrl) {
                doc.moveWord(true, shift);
                return true;
            }
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
            if (ctrl) {
                doc.moveWord(false, shift);
            } else {
                doc.move(doc.line - 1, doc.col, shift);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN) {
            if (ctrl) {
                doc.moveWord(true, shift);
            } else {
                doc.move(doc.line + 1, doc.col, shift);
            }
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
        StudioSync.flushText(doc);
        ModNetwork.sendToServer(new ScriptDataPacket.Save(doc.name, doc.text(), doc.revision));
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
        ModNetwork.sendToServer(new ScriptDataPacket.Save(name, body, doc.revision));
    }

    private void placeJournalCaret(int mx, int my, boolean extend) {
        int line = journalHitLine(my);
        if (line < 0) {
            return;
        }
        JLine row = journalLines.get(line);
        int col = journalColumn(row, mx);
        if (!extend || selA < 0) {
            selA = line;
            selB = line;
            colA = col;
            colB = col;
        } else {
            selB = line;
            colB = col;
        }
    }

    private int journalHitLine(int my) {
        int best = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < journalLines.size(); i++) {
            JLine line = journalLines.get(i);
            boolean inView = line.y + LINE_H > journalViewTop && line.y < journalViewBottom;
            if (!line.footer && !inView) {
                continue;
            }
            int dist = Math.abs(line.y + LINE_H / 2 - my);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    private int journalColumn(JLine line, int mx) {
        int best = 0;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i <= line.text.length(); i++) {
            int dist = Math.abs(line.x + journalWidth(line.text, i, line.plain) - mx);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    private String journalSelection() {
        if (selA < 0 || selB < 0 || journalLines.isEmpty()) {
            return "";
        }
        int a = selA;
        int b = selB;
        int ca = colA;
        int cb = colB;
        if (a > b || (a == b && ca > cb)) {
            int swap = a;
            a = b;
            b = swap;
            swap = ca;
            ca = cb;
            cb = swap;
        }
        a = Math.max(0, Math.min(a, journalLines.size() - 1));
        b = Math.max(0, Math.min(b, journalLines.size() - 1));
        StringBuilder sb = new StringBuilder();
        for (int i = a; i <= b; i++) {
            String text = journalLines.get(i).text;
            int from = i == a ? Math.min(Math.max(ca, 0), text.length()) : 0;
            int to = i == b ? Math.min(Math.max(cb, from), text.length()) : text.length();
            if (i > a) {
                sb.append('\n');
            }
            sb.append(text, from, to);
        }
        return sb.toString();
    }

    private void clearJournalSelection() {
        selA = -1;
        selB = -1;
        colA = 0;
        colB = 0;
    }

    private void selectAllJournal() {
        if (journalLines.isEmpty()) {
            return;
        }
        selA = 0;
        colA = 0;
        selB = journalLines.size() - 1;
        colB = journalLines.get(selB).text.length();
    }

    private void copyJournalKey() {
        String selected = journalSelection();
        if (!selected.isEmpty()) {
            copyJournalText(selected);
        } else {
            copyJournalText(currentDetailText());
        }
    }

    private void copyJournalText(String text) {
        if (text == null || text.isBlank() || this.minecraft == null) {
            return;
        }
        this.minecraft.keyboardHandler.setClipboard(text);
        ClientToasts.push(Messages.toast("studio.copied"));
    }

    private String currentDetailText() {
        if (historySel >= 0 && historySel < StudioSession.history.size()) {
            StudioSession.Note note = StudioSession.history.get(historySel);
            StringBuilder sb = new StringBuilder();
            if (note.title != null && !note.title.isBlank()) {
                sb.append(note.title);
            }
            if (note.body != null && !note.body.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(note.body);
            }
            return sb.toString();
        }
        if (StudioSession.issues.isEmpty()) {
            return "";
        }
        int index = Math.max(0, Math.min(journal, StudioSession.issues.size() - 1));
        return issueText(StudioSession.issues.get(index), true);
    }

    private String allIssuesText() {
        StringBuilder sb = new StringBuilder();
        for (ScriptLog.Issue issue : StudioSession.issues) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(issueText(issue, false));
        }
        return sb.toString();
    }

    private String issueText(ScriptLog.Issue issue, boolean withLine) {
        String where = issue.file() == null || issue.file().isEmpty() ? "—" : issue.file();
        if (issue.line() > 0) {
            where = where + ":" + issue.line();
        }
        StringBuilder sb = new StringBuilder(where).append(": ").append(issue.reason() == null ? "" : issue.reason());
        if (withLine) {
            ScriptLog.Slice slice = slice(issue.file());
            if (slice != null && issue.line() > 0) {
                int idx = issue.line() - slice.firstLine();
                if (idx >= 0 && idx < slice.lines().size()) {
                    sb.append('\n').append(slice.lines().get(idx));
                }
            }
        }
        return sb.toString();
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
        int rel = my - editorY - 2;
        int row = doc.scroll + (rel <= 0 ? 0 : rel / LINE_H);
        row = Math.max(0, Math.min(doc.lines.size() - 1, row));
        String line = doc.lines.get(row);
        int local = mx - editorX - GUTTER_W + doc.horiz;
        int col = 0;
        int best = Integer.MAX_VALUE;
        for (int i = 0; i <= line.length(); i++) {
            int dist = Math.abs(spanWidth(line, i) - local);
            if (dist > best) {
                break;
            }
            best = dist;
            col = i;
        }
        if (keep && (hasAltDown() || doc.box)) {
            doc.moveBox(row, col);
        } else {
            doc.move(row, col, keep);
        }
    }

    private void moveVScroll(ScriptDocument doc, int my) {
        int track = Math.max(1, vBarH - thumbH);
        int pos = Math.max(0, Math.min(track, my - vBarY - scrollGrab));
        doc.scroll = scrollMax == 0 ? 0 : Math.round(pos / (float) track * scrollMax);
        doc.followCaret = false;
    }

    private void moveHScroll(ScriptDocument doc, int mx) {
        int track = Math.max(1, hBarW - hThumbW);
        int pos = Math.max(0, Math.min(track, mx - hBarX - scrollGrab));
        doc.horiz = horizMax == 0 ? 0 : Math.round(pos / (float) track * horizMax);
        doc.followCaret = false;
    }

    private static final int[] PEER_COLORS = {
            0xFFE06C75, 0xFF61AFEF, 0xFF98C379, 0xFFE5C07B, 0xFFC678DD, 0xFF56B6C2
    };

    private static int peerColor(String name) {
        int index = (name == null ? 0 : name.hashCode() & 0x7fffffff) % PEER_COLORS.length;
        return PEER_COLORS[index];
    }

    private static String peerNames(List<StudioLivePacket.Peer> peers, UUID self, int line) {
        StringBuilder names = new StringBuilder();
        for (StudioLivePacket.Peer peer : peers) {
            if (peer.line() != line || (self != null && self.equals(peer.id()))) {
                continue;
            }
            if (!names.isEmpty()) {
                names.append(", ");
            }
            names.append(peer.name());
        }
        return names.isEmpty() ? null : names.toString();
    }

    /** Чужое выделение на одной строке: пара колонок или null. */
    private static int[] peerSpan(StudioLivePacket.Peer peer, int line, int length) {
        if (peer.anchorLine() < 0) {
            return null;
        }
        int fromLine = Math.min(peer.anchorLine(), peer.line());
        int toLine = Math.max(peer.anchorLine(), peer.line());
        if (line < fromLine || line > toLine) {
            return null;
        }
        int anchor = Math.max(0, peer.anchorCol());
        int caret = Math.max(0, peer.col());
        if (peer.box()) {
            int start = Math.min(anchor, caret);
            int end = Math.max(anchor, caret);
            if (start == end) {
                return null;
            }
            return new int[]{Math.min(start, length), Math.min(end, length)};
        }
        if (fromLine == toLine) {
            if (anchor == caret) {
                return null;
            }
            return new int[]{Math.min(Math.min(anchor, caret), length), Math.min(Math.max(anchor, caret), length)};
        }
        if (line == fromLine) {
            int start = peer.anchorLine() < peer.line() ? anchor : caret;
            return new int[]{Math.min(start, length), length};
        }
        if (line == toLine) {
            int end = peer.anchorLine() < peer.line() ? caret : anchor;
            return new int[]{0, Math.min(end, length)};
        }
        return new int[]{0, length};
    }

    private void blitHead(GuiGraphics g, UUID id, int x, int y, int size) {
        ResourceLocation skin = skinOf(id);
        g.blit(skin, x, y, size, size, 8, 8, 8, 8, 64, 64);
        g.blit(skin, x, y, size, size, 40, 8, 8, 8, 64, 64);
    }

    private ResourceLocation skinOf(UUID id) {
        if (this.minecraft.getConnection() != null) {
            PlayerInfo info = this.minecraft.getConnection().getPlayerInfo(id);
            if (info != null) {
                return info.getSkinLocation();
            }
        }
        return DefaultPlayerSkin.getDefaultSkin(id);
    }

    private int textX(String line, int col) {
        ScriptDocument doc = script();
        int horiz = doc == null ? 0 : doc.horiz;
        return editorX + GUTTER_W + spanWidth(line, col) - horiz;
    }

    /** Подкручивает строку вбок, чтобы каретка была в видимой части. */
    private void revealCaret(ScriptDocument doc, int textW) {
        String line = doc.lines.get(Math.max(0, Math.min(doc.lines.size() - 1, doc.line)));
        int caret = spanWidth(line, Math.min(doc.col, line.length()));
        if (caret < doc.horiz + 4) {
            doc.horiz = Math.max(0, caret - 8);
        } else if (caret > doc.horiz + textW - 8) {
            doc.horiz = Math.min(horizMax, Math.max(0, caret - textW + 16));
        }
    }

    private void toolClick(int mx) {
        int x = px + 8;
        String[] ids = {"new", "open", "save", "saveall", "closetab", "delete", "", "cut", "copy", "paste", "", "undo", "redo"};
        for (String id : ids) {
            if (id.isEmpty()) {
                x += 8;
                continue;
            }
            if (mx >= x && mx < x + 18) {
                runTool(id);
                return;
            }
            x += 20;
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
                catalogOpenedAt = System.currentTimeMillis();
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
                    catalogOpenedAt = System.currentTimeMillis();
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
            int w = this.font.width(labels[i]) + 12;
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
            int w = this.font.width(labels[i]) + 12;
            if (mx >= x && mx < x + w) {
                return ids[i];
            }
            x += w;
        }
        return null;
    }

    private String tabAt(int mx) {
        int x = px + 10 + this.font.width("ScriptCraft") + 12;
        int limit = px + pw - 82;
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
        if (copyBtnY >= 0 && hover(mx, my, copyBtnX, copyBtnY, copyBtnW, 12)) {
            copyJournalText(currentDetailText());
            return;
        }
        if (copyAllX >= 0 && hover(mx, my, copyAllX, copyBtnY, copyAllW, 12)) {
            copyJournalText(allIssuesText());
            return;
        }
        if (journalTextX >= 0 && mx >= journalTextX && mx < journalTextX + journalTextW
                && my >= journalViewTop - 2 && my < journalY + journalH) {
            placeJournalCaret(mx, my, false);
            drag = Drag.JOURNAL;
            return;
        }
        if (mx < px + 8 || mx >= px + 8 + journalListW || my < journalY || my >= journalY + journalH) {
            return;
        }
        clearJournalSelection();
        for (JournalHit hit : journalHits) {
            if (my < hit.y || my >= hit.y + hit.h) {
                continue;
            }
            if (hit.kind == 0) {
                historySel = -1;
                journal = hit.index;
                ScriptLog.Issue issue = StudioSession.issues.get(hit.index);
                ScriptLog.Slice slice = slice(issue.file());
                if (slice != null && issue.line() > 0) {
                    codeScroll = Math.max(0, (issue.line() - slice.firstLine()) * LINE_H - 20);
                }
            } else {
                historySel = hit.index;
                StudioSession.Note note = StudioSession.history.get(hit.index);
                if (note.line > 0) {
                    codeScroll = Math.max(0, (note.line - note.firstLine) * LINE_H - 20);
                } else {
                    codeScroll = 0;
                }
            }
            return;
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
        StudioSync.leaveFile(name);
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
        return ScriptColors.width(this.font, line, count);
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

    private static void drawGear(GuiGraphics g, int cx, int cy, int color, float angle) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
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
