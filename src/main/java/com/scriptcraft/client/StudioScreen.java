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
import com.scriptcraft.network.StudioExplorerPacket;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private enum Drag { NONE, MOVE, N, S, E, W, NE, NW, SE, SW, TEXT, JOURNAL, VSCROLL, HSCROLL,
        SIDEBAR_RESIZE,
        PANEL_MOVE, PANEL_N, PANEL_S, PANEL_E, PANEL_W, PANEL_NE, PANEL_NW, PANEL_SE, PANEL_SW }

    private String menu;
    private boolean catalog;
    private boolean catalogRequested;
    private boolean naming;
    private final StudioTextField nameField = new StudioTextField().filenameFilter(64);
    private boolean gotoOpen;
    private final StudioTextField gotoField = new StudioTextField().digitsOnly(6);
    private final StudioTextField findField = new StudioTextField().maxLength(256);
    private final StudioTextField replaceField = new StudioTextField().maxLength(256);
    private int setColorX, setColorY;
    /** 0 — нет фокуса, 1 — поле "Найти", 2 — поле "Заменить". */
    private int panelFocus;
    private long panelOpenedAt;
    private boolean ctxOpen;
    private int ctxX, ctxY;
    private long ctxOpenedAt;
    private boolean settings;
    private boolean listening;
    private String listeningBind;
    private int hotkeyScroll;
    private int hotkeyMaxScroll;
    private int sidebarScroll;
    private int sidebarMaxScroll;
    private int sbX, sbY, sbW, sbH;
    private int sbToggleX, sbToggleY, sbBackX, sbFwdX, sbNewX, sbMkdirX, sbRenX, sbDelX, sbBarY;
    /** Режим диалога ввода имени: "file", "mkdir", "rename". */
    private String namingMode = "file";
    private String renameTarget = "";
    private boolean explorerFocused;
    private StudioAutocomplete.Match acMatch;
    private int acSel;
    private int acX, acY, acW, acH;
    private final List<SidebarHit> sidebarHits = new ArrayList<>();
    private final List<PeerHeadHit> peerHeadHits = new ArrayList<>();
    private final List<HotkeyHit> hotkeyHits = new ArrayList<>();
    private int hkResetX, hkResetY, hkResetW;
    private int setAcX, setAcY, setAutoX, setAutoY, setSbLeftX, setSbRightX, setSbY, setLangRuX, setLangEnX, setLangY;

    private record SidebarHit(int y, int h, String fullPath, boolean dir, boolean parent, int toggleX) {
    }

    private record PeerHeadHit(int x, int y, int w, int h, int line, int col) {
    }

    private record HotkeyHit(int x, int y, int w, int h, String id) {
    }
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
    private final List<PanelCheck> panelChecks = new ArrayList<>();
    private int pFindX, pFindY, pFindW, pRepX, pRepY, pRepW;
    private int pPrevX, pNextX, pNavY;
    private int pRepBtnX, pRepBtnW, pAllBtnX, pAllBtnW, pActY;
    private int pCloseX, pCloseY;
    private TexturePreview.Image preview;
    private int previewX, previewY;

    private record PanelCheck(int x, int y, int w, int h, String id) {
    }

    // --- Анимации UI ---
    private final long openedAt = System.currentTimeMillis();
    private long lastFrameNano = System.nanoTime();
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
        StudioAnimEditor.onLeaveTab();
        StudioSync.leave();
        super.onClose();
    }

    /** Перетаскивание файлов с рабочего стола прямо в окно Майнкрафта -> загрузка на сервер в текущую папку проводника. */
    @Override
    public void onFilesDrop(List<Path> paths) {
        if (paths == null || paths.isEmpty() || this.minecraft == null || this.minecraft.getConnection() == null) {
            return;
        }
        String dir = StudioSession.explorerDir == null ? "" : StudioSession.explorerDir;
        for (Path p : paths) {
            try {
                if (!Files.isRegularFile(p)) continue;
                byte[] bytes = Files.readAllBytes(p);
                if (bytes.length > com.scriptcraft.studio.StudioExplorer.MAX_FILE_BYTES) {
                    ClientToasts.push(p.getFileName() + " > 900KB");
                    continue;
                }
                String fileName = p.getFileName().toString();
                String rel = dir.isEmpty() ? fileName : dir + "/" + fileName;
                ModNetwork.sendToServer(new StudioExplorerPacket.Action(StudioExplorerPacket.Kind.WRITE_FILE, rel, "", bytes));
                ClientToasts.push(Messages.toast("studio.exp.uploaded", fileName));
            } catch (Exception e) {
                ClientToasts.push("Upload error: " + e.getMessage());
            }
        }
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
        long nowNano = System.nanoTime();
        dt = Math.max(0.0005f, Math.min(0.08f, (nowNano - lastFrameNano) / 1_000_000_000f));
        lastFrameNano = nowNano;
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
        } else if (StudioSession.ANIMATOR.equals(StudioSession.active)) {
            StudioAnimEditor.render(g, this.font, px + 6, py + TITLE + MENU_H + 4,
                    pw - 12, ph - TITLE - MENU_H - 10, mouseX, mouseY - slideY);
        } else if (StudioSession.PROFILER.equals(StudioSession.active) && ClientUiSettings.showProfiler()) {
            renderProfiler(g, mouseX, mouseY - slideY);
        } else if (script() != null) {
            renderEditor(g, mouseX, mouseY - slideY);
            if (!StudioSession.guiEditorMode) {
                if (StudioSession.toolPanelOpen) {
                    renderToolPanel(g, mouseX, mouseY - slideY);
                }
                if (ctxOpen) {
                    renderCtxMenu(g, mouseX, mouseY - slideY);
                }
            }
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
        if (gotoOpen) {
            renderGotoBox(g);
        }
        if (ask != null) {
            renderAsk(g, mouseX, mouseY - slideY);
        }
        if (StudioSession.historyOpen) {
            renderHistoryModal(g, mouseX, mouseY - slideY);
        }
        g.pose().popPose();
        drawTip(g);
        trackPresence();
    }

    /** Курсор уходит на сервер, пока открыт скрипт (или редактор временно заглянул в Журнал/Главную, не закрыв вкладку). */
    private void trackPresence() {
        ScriptDocument doc = settings ? null : script();
        if (doc == null && !settings && StudioSession.lastScriptTab != null) {
            doc = StudioSession.docs.get(StudioSession.lastScriptTab);
        }
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
        x = tab(g, mx, my, x, Messages.get("studio.animator"), StudioSession.ANIMATOR, false);
        if (ClientUiSettings.showProfiler()) {
            x = tab(g, mx, my, x, Messages.get("studio.profiler"), StudioSession.PROFILER, false);
        }
        if (!catalogRequested && this.minecraft != null && this.minecraft.getConnection() != null) {
            catalogRequested = true;
            ModNetwork.sendToServer(new ScriptListPacket.Request());
            ModNetwork.sendToServer(new StudioExplorerPacket.Action(StudioExplorerPacket.Kind.LIST, StudioSession.explorerDir, ""));
        }
        List<String> names = new ArrayList<>(StudioSession.docs.keySet());
        for (String name : names) {
            if (x > limit - 70) {
                break;
            }
            x = tab(g, mx, my, x, shortName(name), name, true);
        }
        plusX = -1000;
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
            int bg = h > 0.02f ? lerpColor(BAR, 0xFF33435E, h * 0.8f) : BAR;
            drawGear(g, x + 2, y + 2, color, bg);
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
        x = menuWord(g, mx, my, x, y, "view", Messages.get("studio.m.view"));
        menuWord(g, mx, my, x, y, "reload", Messages.get("studio.m.reload"));
    }

    private int menuWord(GuiGraphics g, int mx, int my, int x, int y, String id, String label) {
        int w = this.font.width(label) + 12;
        boolean on = id.equals(menu) || hover(mx, my, x, y, w, MENU_H);
        float h = animHover("menu:" + id, on);
        boolean resBlink = "reload".equals(id) && StudioSession.resourcesDirty;
        if (resBlink) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 220.0);
            g.fill(x + 1, y + 1, x + w - 1, y + MENU_H - 1, withAlpha(GREEN, 0.16f + 0.28f * pulse));
            g.fill(x + 1, y + MENU_H - 2, x + w - 1, y + MENU_H - 1, withAlpha(0xFF7EE787, 0.5f + 0.5f * pulse));
            int textCol = lerpColor(0xFF56D364, 0xFFB4FFBF, Math.max(h, pulse));
            g.drawString(this.font, label, x + 6, y + 4, textCol, false);
            return x + w;
        }
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
        x = tool(g, mx, my, x, y, "redo", Messages.get("studio.m.redo"));
        g.fill(x + 2, y + 4, x + 3, y + TOOL_H - 4, EDGE);
        x += 8;
        x = tool(g, mx, my, x, y, "find", Messages.get("studio.find_replace"));
        histBtnX = -1000;
        guiBtnX = -1000;
        ScriptDocument curDoc = script();
        if (curDoc != null && !curDoc.genericFile && !curDoc.isImage) {
            g.fill(x + 2, y + 4, x + 3, y + TOOL_H - 4, EDGE);
            x += 8;
            String histLbl = Messages.get("studio.history");
            histBtnW = this.font.width(histLbl) + 12;
            histBtnX = x;
            histBtnY = y + 3;
            paintMiniBtn(g, mx, my, histBtnX, histBtnY, histBtnW, 16, histLbl, "tool:history");
            x += histBtnW + 4;

            int scrCnt = StudioGuiEditor.countScreensIn(curDoc);
            String guiLbl = StudioSession.guiEditorMode
                    ? Messages.get("studio.gui.code_btn")
                    : (Messages.get("studio.gui.btn") + (scrCnt > 0 ? " (" + scrCnt + ")" : ""));
            guiBtnW = this.font.width(guiLbl) + 12;
            guiBtnX = x;
            guiBtnY = y + 3;
            boolean gHot = hover(mx, my, guiBtnX, guiBtnY, guiBtnW, 16);
            int gBg = StudioSession.guiEditorMode ? 0xFF233856 : (gHot ? ROW_HOT : ROW);
            int gBrd = StudioSession.guiEditorMode ? GOLD : (gHot ? ACCENT : EDGE);
            g.fill(guiBtnX, guiBtnY, guiBtnX + guiBtnW, guiBtnY + 16, gBg);
            border(g, guiBtnX, guiBtnY, guiBtnW, 16, gBrd);
            g.drawString(this.font, guiLbl, guiBtnX + 6, guiBtnY + 4,
                    StudioSession.guiEditorMode ? GOLD : (gHot ? GOLD : ACCENT), false);
            x += guiBtnW + 4;
        }

        // Аватары участников совместной правки в правой части тулбара (клик -> переход на их строку)
        peerHeadHits.clear();
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
                int pc = peerColor(peer);
                int hy = y + 6;
                g.fill(rx - 1, hy - 1, rx + 11, hy + 11, pc);
                blitHead(g, peer.id(), rx, hy, 10);
                peerHeadHits.add(new PeerHeadHit(rx - 1, hy - 1, 12, 12, peer.line(), peer.col()));
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
            case "find" -> {
                g.fill(x + 2, y + 1, x + 7, y + 2, c);
                g.fill(x + 2, y + 6, x + 7, y + 7, c);
                g.fill(x + 1, y + 2, x + 2, y + 6, c);
                g.fill(x + 7, y + 2, x + 8, y + 6, c);
                g.fill(x + 7, y + 7, x + 9, y + 9, c);
                g.fill(x + 9, y + 9, x + 11, y + 11, c);
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

        boolean sbOpen = ClientUiSettings.sidebarOpen();
        boolean sbRight = ClientUiSettings.sidebarRight();
        int sbWidth = sbOpen ? Math.min(ClientUiSettings.sidebarWidth(), Math.max(110, pw / 2)) : 20;
        sbY = y + 4;
        sbH = Math.max(24, h - 8);
        sbW = sbWidth;
        int homeX, homeW;
        if (sbRight) {
            homeX = px + 6;
            homeW = Math.max(120, pw - 12 - sbWidth - 4);
            sbX = homeX + homeW + 4;
        } else {
            sbX = px + 6;
            homeX = sbX + sbWidth + 4;
            homeW = Math.max(120, pw - 12 - sbWidth - 4);
        }
        int cx = homeX + homeW / 2;

        // Мягкий вертикальный фон на главной
        g.fillGradient(px + 1, y, px + pw - 1, y + h, 0xFF111722, 0xFF0D111A);
        renderSidebar(g, mx, my, sbOpen, sbRight);

        // Заголовок + бейдж версии + анимированный слоган
        int titleY = y + Math.max(12, Math.min(26, h / 14));
        float titleScale = homeW > 420 ? 2.2f : 1.7f;
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
        int gap = 14;
        int devW = Math.max(124, Math.min(168, (homeW - 36) / 2));
        int btnW = Math.max(150, Math.min(236, homeW - devW - gap - 28));
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
        openEdX = -1;
        journalTextX = -1;
        int y = contentY();
        int h = contentH();

        boolean sbOpen = ClientUiSettings.sidebarOpen();
        boolean sbRight = ClientUiSettings.sidebarRight();
        int sbWidth = sbOpen ? Math.min(ClientUiSettings.sidebarWidth(), Math.max(110, pw / 3)) : 20;
        sbY = y + 4;
        sbH = Math.max(24, h - 8);
        sbW = sbWidth;
        int areaX, areaW;
        if (sbRight) {
            areaX = px + 6;
            areaW = Math.max(160, pw - 12 - sbWidth - 4);
            sbX = areaX + areaW + 4;
        } else {
            sbX = px + 6;
            areaX = sbX + sbWidth + 4;
            areaW = Math.max(160, pw - 12 - sbWidth - 4);
        }
        renderSidebar(g, mx, my, sbOpen, sbRight);

        int listW = Math.max(140, Math.min(240, (areaW - 6) * 2 / 5));
        journalListX = areaX;
        g.fill(areaX, y, areaX + listW, y + h, 0xF0181E28);
        g.fill(areaX + 4 + listW, y, areaX + areaW, y + h, 0xF0181E28);
        int content = 4 + 16 + (StudioSession.issues.isEmpty() ? 16 : StudioSession.issues.size() * 24)
                + 6 + 14 + Math.max(1, StudioSession.history.size()) * 22 + 8;
        listMax = Math.max(0, content - h);
        listScroll = Math.max(0, Math.min(listScroll, listMax));
        if (!StudioSession.issues.isEmpty()) {
            journal = Math.max(0, Math.min(StudioSession.issues.size() - 1, journal));
        }
        journalHits.clear();
        UUID selfId = this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getUUID() : null;
        g.enableScissor(areaX, y, areaX + listW, y + h);
        int row = y + 4 - listScroll;
        g.drawString(this.font, Messages.get("studio.active"), areaX + 8, row, GOLD, false);
        row += 16;
        if (StudioSession.issues.isEmpty()) {
            g.drawString(this.font, Messages.get("log.empty"), areaX + 8, row, MUTED, false);
            row += 16;
        }
        for (int i = 0; i < StudioSession.issues.size(); i++) {
            ScriptLog.Issue issue = StudioSession.issues.get(i);
            if (row + 24 >= y && row <= y + h) {
                journalHits.add(new JournalHit(row, 22, 0, i));
                boolean on = historySel < 0 && i == journal;
                g.fill(areaX + 4, row, areaX + listW - 4, row + 22, on ? 0xFF2E3A4E : ROW);
                String where = issue.file().isEmpty() ? "—" : issue.file();
                if (issue.line() > 0) {
                    where = where + ":" + issue.line();
                }
                // Головы редакторов, сидящих в этом проблемном скрипте
                int headRight = areaX + listW - 6;
                if (!issue.file().isEmpty()) {
                    String fullPath = "scripts/" + issue.file();
                    for (StudioExplorerPacket.ActiveEditor ed : StudioSession.activeEditors) {
                        if (!ed.path().equals(fullPath)) continue;
                        if (headRight - 11 < areaX + 48) break;
                        headRight -= 10;
                        int pc = peerColor(ed.name());
                        g.fill(headRight - 1, row + 2, headRight + 9, row + 12, pc);
                        blitHead(g, ed.id(), headRight, row + 3, 8);
                        if (hover(mx, my, headRight - 1, row + 2, 10, 10)) {
                            tip = ed.name();
                            tipX = mx + 8;
                            tipY = my + 12;
                        }
                        headRight -= 2;
                    }
                }
                int titleMaxW = Math.max(24, headRight - (areaX + 8));
                g.drawString(this.font, trim(where, titleMaxW), areaX + 8, row + 2, on ? GOLD : TEXT, false);
                g.drawString(this.font, trim(com.scriptcraft.lang.Phrase.forClient(issue.reason()), listW - 18), areaX + 8, row + 12, MUTED, false);
            }
            row += 24;
        }
        row += 6;
        g.drawString(this.font, Messages.get("studio.history"), areaX + 8, row, GOLD, false);
        row += 14;
        if (StudioSession.history.isEmpty()) {
            g.drawString(this.font, "—", areaX + 8, row, MUTED, false);
        }
        for (int i = StudioSession.history.size() - 1; i >= 0; i--) {
            if (row + 22 >= y && row <= y + h) {
                journalHits.add(new JournalHit(row, 22, 1, i));
                boolean on = i == historySel;
                if (on) {
                    g.fill(areaX + 4, row, areaX + listW - 4, row + 20, 0xFF2E3A4E);
                }
                g.drawString(this.font, trim(com.scriptcraft.lang.Phrase.forClient(StudioSession.history.get(i).title), listW - 18), areaX + 8, row + 4, on ? GOLD : MUTED, false);
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
            String locBody = com.scriptcraft.lang.Phrase.forClient(note.body);
            List<String> rows = code ? note.lines : noteBody(locBody);
            drawJournalDetail(g, mx, my, y, h, areaX, areaW, listW, note.file, rows, code ? note.firstLine : 0,
                    code ? note.line : -1, code ? locBody : "", code, historySel + 1);
            return;
        }
        if (StudioSession.issues.isEmpty()) {
            g.drawString(this.font, Messages.get("log.empty"), areaX + 8 + listW, y + 8, MUTED, false);
            codeMax = 0;
            return;
        }
        ScriptLog.Issue current = StudioSession.issues.get(journal);
        List<String> live = current.file().isEmpty() ? null : StudioSession.liveLines(current.file());
        ScriptLog.Slice slice = slice(current.file());
        List<String> rows = live != null && !live.isEmpty() ? live : (slice == null ? List.of() : slice.lines());
        int firstLine = live != null && !live.isEmpty() ? 1 : (slice == null ? 0 : slice.firstLine());
        drawJournalDetail(g, mx, my, y, h, areaX, areaW, listW, current.file(), rows, firstLine, current.line(),
                com.scriptcraft.lang.Phrase.forClient(current.reason()), !rows.isEmpty(), -journal - 1);
    }

    private void drawJournalDetail(GuiGraphics g, int mx, int my, int y, int h, int areaX, int areaW, int listW,
                                   String file, List<String> rows,
                                   int firstNumber, int highlight, String reason, boolean numbers, int stamp) {
        if (journalStamp != stamp) {
            journalStamp = stamp;
            clearJournalSelection();
        }
        journalLines.clear();
        int codeX = areaX + 8 + listW;
        int codeW = Math.max(40, areaW - listW - 12);
        journalTextX = codeX;
        journalTextW = codeW;
        drawCopyButtons(g, mx, my, codeX, y, codeW, file, highlight);
        journalViewTop = y + 18;
        journalViewBottom = Math.max(journalViewTop + LINE_H, y + h - 22);
        int viewH = journalViewBottom - journalViewTop;
        codeMax = Math.max(0, rows.size() * LINE_H - viewH);
        codeScroll = Math.max(0, Math.min(codeScroll, codeMax));
        List<StudioLivePacket.Peer> filePeers = (file == null || file.isEmpty()) ? List.of() : StudioSession.peers(file);
        UUID selfId = this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getUUID() : null;
        g.enableScissor(codeX, journalViewTop, codeX + codeW, journalViewBottom);
        if (rows.isEmpty()) {
            journalLine(g, journalViewTop + 4, codeX + 6, Messages.get("log.no_file"), MUTED, true, true, false);
        } else {
            int ly = journalViewTop - codeScroll;
            for (int i = 0; i < rows.size(); i++) {
                int number = firstNumber + i;
                int zeroLine = number - 1;
                boolean visible = ly + LINE_H > journalViewTop && ly < journalViewBottom;
                String lineStr = rows.get(i);
                if (visible && numbers && number == highlight) {
                    g.fill(codeX, ly - 1, codeX + codeW, ly + LINE_H - 1, 0x66D25A5A);
                }
                int textX = codeX + (numbers ? 38 : 6);
                // Живое выделение других редакторов в этой строке
                if (visible && numbers && !filePeers.isEmpty()) {
                    for (StudioLivePacket.Peer peer : filePeers) {
                        if (selfId != null && selfId.equals(peer.id())) continue;
                        int[] span = peerSpan(peer, zeroLine, lineStr.length());
                        if (span != null && span[1] > span[0]) {
                            int x0 = textX + journalWidth(lineStr, span[0], false);
                            int x1 = textX + journalWidth(lineStr, span[1], false);
                            g.fill(x0, ly - 1, Math.max(x1, x0 + 2), ly + LINE_H - 1, (peerColor(peer) & 0xFFFFFF) | 0x55000000);
                        }
                    }
                }
                if (visible && numbers) {
                    String num = Integer.toString(number);
                    g.drawString(this.font, num, codeX + 32 - this.font.width(num), ly, number == highlight ? RED : MUTED, false);
                    for (StudioLivePacket.Peer peer : filePeers) {
                        if (peer.line() != zeroLine) continue;
                        int pc = peerColor(peer);
                        g.fill(codeX + 1, ly, codeX + 11, ly + 10, pc);
                        blitHead(g, peer.id(), codeX + 2, ly + 1, 8);
                        if (hover(mx, my, codeX + 1, ly, 10, 10)) {
                            tip = peer.name() + " (Ln " + number + ")";
                            tipX = mx + 8;
                            tipY = my + 12;
                        }
                        break;
                    }
                }
                journalLine(g, ly, textX, lineStr, TEXT, !numbers, visible, false);
                // Живые курсоры редакторов в строке
                if (visible && numbers && !filePeers.isEmpty()) {
                    for (StudioLivePacket.Peer peer : filePeers) {
                        if (peer.line() != zeroLine) continue;
                        int pc = peerColor(peer);
                        int cx = textX + journalWidth(lineStr, Math.min(Math.max(0, peer.col()), lineStr.length()), false);
                        g.fill(cx, ly - 1, cx + 2, ly + LINE_H - 1, pc);
                        g.fill(cx - 1, ly - 2, cx + 3, ly, pc);
                    }
                }
                ly += LINE_H;
            }
        }
        g.disableScissor();
        if (reason != null && !reason.isBlank()) {
            String line = trim(Messages.get("log.reason") + ": " + reason, codeW);
            journalLine(g, y + h - 14, codeX, line, 0xFFF0C2C2, true, true, true);
        }
    }

    private void drawCopyButtons(GuiGraphics g, int mx, int my, int x, int y, int width, String file, int highlightLine) {
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
            next += copyAllW + 4;
        } else {
            copyAllX = -1;
        }
        openEdX = -1;
        openEdFile = file;
        openEdLine = highlightLine;
        if (file != null && !file.isEmpty()) {
            String editLbl = "✎ " + Messages.get("studio.open_in_editor");
            openEdW = this.font.width(editLbl) + 10;
            if (next + openEdW <= x + width) {
                openEdX = next;
                paintJournalButton(g, mx, my, openEdX, copyBtnY, openEdW, editLbl, Messages.get("studio.open_in_editor_hint"));
                next += openEdW + 6;
            }
            // Головы редакторов справа сверху над кодом в Журнале
            int rx = x + width - 2;
            String fullPath = "scripts/" + file;
            for (StudioExplorerPacket.ActiveEditor ed : StudioSession.activeEditors) {
                if (!ed.path().equals(fullPath)) continue;
                rx -= 13;
                if (rx < next + 4) break;
                int pc = peerColor(ed.name());
                g.fill(rx - 1, copyBtnY, rx + 11, copyBtnY + 12, pc);
                blitHead(g, ed.id(), rx, copyBtnY + 1, 10);
                if (hover(mx, my, rx - 1, copyBtnY, 12, 12)) {
                    tip = ed.name();
                    tipX = mx - this.font.width(tip) - 6;
                    tipY = my + 14;
                }
            }
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
    private int openEdX = -1;
    private int openEdW;
    private String openEdFile;
    private int openEdLine;
    private int journalListX;
    private long lastJournalClickAt;
    private int lastJournalClickIdx = -1;
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

    private int gutterW() {
        return StudioSession.optLineNumbers ? GUTTER_W : 18;
    }

    private void renderEditor(GuiGraphics g, int mx, int my) {
        ScriptDocument doc = script();
        int y = contentY() + TOOL_H;
        int status = 18;
        int h = py + ph - y - status;
        int gw = gutterW();
        boolean sbOpen = ClientUiSettings.sidebarOpen();
        boolean sbRight = ClientUiSettings.sidebarRight();
        int sbWidth = sbOpen ? Math.min(ClientUiSettings.sidebarWidth(), Math.max(110, pw / 2)) : 20;
        sbY = y + 2;
        sbH = Math.max(24, h - 4);
        sbW = sbWidth;
        if (sbRight) {
            editorX = px + 6;
            editorW = Math.max(80, pw - 12 - sbWidth - 4);
            sbX = editorX + editorW + 4;
        } else {
            sbX = px + 6;
            editorX = sbX + sbWidth + 4;
            editorW = Math.max(80, pw - 12 - sbWidth - 4);
        }
        editorY = y + 2;
        editorH = Math.max(24, h - 4);
        renderSidebar(g, mx, my, sbOpen, sbRight);
        g.fill(editorX, editorY, editorX + editorW, editorY + editorH, 0xF50C1018);
        border(g, editorX, editorY, editorW, editorH, 0xFF212B3C);
        if (!doc.loaded) {
            g.drawString(this.font, Messages.get("studio.loading"), editorX + 10, editorY + 10, MUTED, false);
        } else if (StudioSession.guiEditorMode && !doc.genericFile && !doc.isImage) {
            StudioGuiEditor.render(g, this.font, doc, editorX, editorY, editorW, editorH, this.width, this.height, mx, my);
        } else if (doc.isImage && doc.imageId != null) {
            int maxW = Math.max(32, editorW - 32);
            int maxH = Math.max(32, editorH - 40);
            float scale = Math.min(4f, Math.min(maxW / (float) Math.max(1, doc.imageW), maxH / (float) Math.max(1, doc.imageH)));
            int dw = Math.max(1, Math.round(doc.imageW * scale));
            int dh = Math.max(1, Math.round(doc.imageH * scale));
            int ix = editorX + (editorW - dw) / 2;
            int iy = editorY + (editorH - dh - 14) / 2;
            g.fill(ix - 3, iy - 3, ix + dw + 3, iy + dh + 3, 0xFF1A2232);
            border(g, ix - 3, iy - 3, dw + 6, dh + 6, EDGE_HOT);
            g.blit(doc.imageId, ix, iy, dw, dh, 0, 0, doc.imageW, doc.imageH, doc.imageW, doc.imageH);
            String dim = doc.imageW + " × " + doc.imageH + " px";
            g.drawString(this.font, dim, editorX + (editorW - this.font.width(dim)) / 2, iy + dh + 8, MUTED, false);
        } else {
            int textW = Math.max(40, editorW - gw - 8 - BAR_W);
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
            g.fill(editorX + 1, editorY + 1, editorX + gw - 4, textBottom, 0xFF111722);
            g.fill(editorX + gw - 4, editorY + 1, editorX + gw - 3, textBottom, 0xFF222C3E);

            // Собираем ошибки текущего скрипта по номеру строки (0-based)
            Map<Integer, String> lineErrors = new HashMap<>();
            for (ScriptLog.Issue issue : StudioSession.issues) {
                if (issue.file().equals(doc.name) && issue.line() > 0) {
                    lineErrors.putIfAbsent(issue.line() - 1, com.scriptcraft.lang.Phrase.forClient(issue.reason()));
                }
            }
            int[] pairMatch = ClientUiSettings.autoClose() ? doc.matchingBracket() : null;
            String errorHover = null;

            for (int i = doc.scroll; i < doc.lines.size(); i++) {
                int ly = editorY + (i - doc.scroll) * LINE_H + 2;
                if (ly > textBottom) {
                    break;
                }
                if (lineErrors.containsKey(i)) {
                    g.fill(editorX + 1, ly - 1, textRight, ly + LINE_H - 1, 0x33E55050);
                } else if (i == doc.line && StudioSession.optActiveLine) {
                    g.fill(editorX + 1, ly - 1, textRight, ly + LINE_H - 1, 0xFF162031);
                    g.fill(editorX + 1, ly - 1, editorX + 3, ly + LINE_H - 1, GOLD);
                }
                if (lineErrors.containsKey(i) && my >= ly - 1 && my < ly + LINE_H - 1 && mx >= editorX && mx < textRight) {
                    errorHover = lineErrors.get(i);
                }
            }
            List<int[]> searchMatches = (StudioSession.toolPanelOpen && !findField.value().isEmpty())
                    ? doc.findAllMatches(findField.value(), StudioSession.optMatchCase, StudioSession.optWholeWord)
                    : List.of();
            List<StudioLivePacket.Peer> roomPeers = StudioSession.peers(doc.name);
            UUID selfId = this.minecraft.player == null ? null : this.minecraft.player.getUUID();
            StudioLivePacket.Peer hoveredPeer = null;
            String peerHover = null;
            g.enableScissor(editorX + gw - 2, editorY + 1, textRight, textBottom);
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
                        g.fill(x0, ly - 1, Math.max(x1, x0 + 2), ly + LINE_H - 1, (peerColor(peer) & 0xFFFFFF) | 0x55000000);
                    }
                }
                if (!searchMatches.isEmpty()) {
                    for (int[] m : searchMatches) {
                        if (m[0] == i) {
                            int x0 = textX(line, m[1]);
                            int x1 = textX(line, m[2]);
                            g.fill(x0, ly - 1, Math.max(x1, x0 + 2), ly + LINE_H - 1, 0x66F0B85B);
                        }
                    }
                }
                int[] sel = doc.selectionOn(i);
                if (sel != null) {
                    int x0 = textX(line, sel[0]);
                    int x1 = textX(line, sel[1]);
                    g.fill(x0, ly - 1, Math.max(x1, x0 + 2), ly + LINE_H - 1, 0x883A6EA5);
                }
                if (StudioSession.optSyntax) {
                    for (ScriptMarkup.Mark mark : ScriptMarkup.of(line)) {
                        int x0 = textX(line, mark.from());
                        int x1 = textX(line, mark.to());
                        int color = switch (mark.kind()) {
                            case EVENT -> YELLOW;
                            case QUOTE -> QUOTE;
                            default -> BLUE;
                        };
                        g.fill(x0, ly - 1, Math.max(x1, x0 + 1), ly + LINE_H - 1, color);
                        if (StudioSession.optTexturePreview && mark.kind() == ScriptMarkup.Kind.RESOURCE
                                && hover(mx, my, x0, ly - 1, Math.max(1, x1 - x0), LINE_H)) {
                            TexturePreview.Image image = TexturePreview.lookup(mark.token());
                            if (image != null) {
                                preview = image;
                                previewX = mx + 12;
                                previewY = my + 12;
                            }
                        }
                    }
                }
                ScriptColors.draw(g, this.font, line, editorX + gw - doc.horiz, ly, TEXT, doc.horiz + textW, StudioSession.optQuoteColors);
                // Подсветка парных скобок и кавычек
                if (pairMatch != null) {
                    if (pairMatch[0] == i && pairMatch[1] < line.length()) {
                        int bx0 = textX(line, pairMatch[1]);
                        int bx1 = textX(line, pairMatch[1] + 1);
                        g.fill(bx0, ly - 1, Math.max(bx1, bx0 + 5), ly + LINE_H - 1, 0x44F0B85B);
                        border(g, bx0, ly - 1, Math.max(5, bx1 - bx0), LINE_H, 0xAAF0B85B);
                    }
                    if (pairMatch[2] == i && pairMatch[3] < line.length()) {
                        int bx0 = textX(line, pairMatch[3]);
                        int bx1 = textX(line, pairMatch[3] + 1);
                        g.fill(bx0, ly - 1, Math.max(bx1, bx0 + 5), ly + LINE_H - 1, 0x4458A6FF);
                        border(g, bx0, ly - 1, Math.max(5, bx1 - bx0), LINE_H, 0xAA58A6FF);
                    }
                }
                // Красное подчёркивание строки с ошибкой
                if (lineErrors.containsKey(i)) {
                    int lead = 0;
                    while (lead < line.length() && (line.charAt(lead) == ' ' || line.charAt(lead) == '\t')) lead++;
                    int ux0 = textX(line, lead);
                    int ux1 = Math.max(ux0 + 18, textX(line, line.length()));
                    g.fill(ux0, ly + LINE_H - 2, ux1, ly + LINE_H - 1, RED);
                }
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
                    int pc = peerColor(peer);
                    int cx = textX(line, Math.min(Math.max(0, peer.col()), line.length()));
                    g.fill(cx, ly - 1, cx + 2, ly + LINE_H - 1, pc);
                    g.fill(cx - 1, ly - 2, cx + 3, ly, pc);
                }
            }
            g.disableScissor();

            // Номера строк и головы участников в левом gutter (никогда не перекрывают код)
            g.enableScissor(editorX + 1, editorY + 1, editorX + gw - 4, textBottom);
            for (int i = doc.scroll; i < doc.lines.size(); i++) {
                int ly = editorY + (i - doc.scroll) * LINE_H + 2;
                if (ly > textBottom) {
                    break;
                }
                if (StudioSession.optLineNumbers) {
                    String num = Integer.toString(i + 1);
                    int numCol = lineErrors.containsKey(i) ? RED : (i == doc.line ? GOLD : MUTED);
                    g.drawString(this.font, num, editorX + gw - 8 - this.font.width(num), ly, numCol, false);
                }
                boolean drewPeer = false;
                for (StudioLivePacket.Peer peer : roomPeers) {
                    if ((selfId != null && selfId.equals(peer.id())) || peer.line() != i) {
                        continue;
                    }
                    int pc = peerColor(peer);
                    g.fill(editorX + 2, ly, editorX + 12, ly + 10, pc);
                    blitHead(g, peer.id(), editorX + 3, ly + 1, 8);
                    drewPeer = true;
                    break;
                }
                if (lineErrors.containsKey(i)) {
                    g.drawString(this.font, "!", editorX + (drewPeer ? 13 : 4), ly, RED, false);
                }
            }
            g.disableScissor();

            if (errorHover != null && !inToolPanel(mx, my)) {
                tip = errorHover;
                tipX = mx + 10;
                tipY = my - 4;
            }
            if (acMatch != null && !acMatch.items().isEmpty()) {
                renderAutocomplete(g, mx, my, doc);
            }
            if (peerHover != null && !inToolPanel(mx, my)) {
                int tw = this.font.width(peerHover) + (hoveredPeer != null ? 14 : 0);
                int nameX = Math.min(mx + 10, px + pw - tw - 10);
                int nameY = Math.max(py + 2, errorHover != null ? my - 31 : my - 15);
                int accent = hoveredPeer != null ? peerColor(hoveredPeer) : ACCENT;
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
        String statusText = statusText(doc);
        int stY = py + ph - status;
        g.fill(px + 1, stY, px + pw - 1, py + ph - 1, BAR);
        g.fill(px + 1, stY, px + pw - 1, stY + 1, EDGE);

        // Правая часть статусбара: LIVE, UTF-8, отступы, размер документа, курсор, выделение
        int rightEdge = px + pw - 10;
        int minRightX = px + (statusBad ? 140 : 12);
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

        // UTF-8 и тип отступов (TAB / SPC)
        String metaLabel = (doc.usesSpacesIndent() ? "SPC" : "TAB") + "  UTF-8";
        int metaW = this.font.width(metaLabel);
        if (rightEdge - metaW > minRightX) {
            g.drawString(this.font, metaLabel, rightEdge - metaW, stY + 5, 0xFF6B778D, false);
            rightEdge -= metaW + 8;
            g.fill(rightEdge, stY + 4, rightEdge + 1, stY + status - 4, EDGE);
            rightEdge -= 8;
        }

        // Общее число строк и символов в документе
        int totalChars = doc.charCount();
        String charsStr = totalChars >= 10_000 ? String.format(Locale.ROOT, "%.1fk", totalChars / 1000.0) : String.valueOf(totalChars);
        String docStats = doc.lines.size() + " ln · " + charsStr + " ch";
        int docStatsW = this.font.width(docStats);
        if (rightEdge - docStatsW > minRightX) {
            g.drawString(this.font, docStats, rightEdge - docStatsW, stY + 5, MUTED, false);
            rightEdge -= docStatsW + 8;
            g.fill(rightEdge, stY + 4, rightEdge + 1, stY + status - 4, EDGE);
            rightEdge -= 8;
        }

        // Позиция курсора
        String posLabel = "Ln " + (doc.line + 1) + ", Col " + (doc.col + 1);
        int posW = this.font.width(posLabel);
        if (rightEdge - posW > minRightX) {
            g.drawString(this.font, posLabel, rightEdge - posW, stY + 5, TEXT, false);
            rightEdge -= posW + 8;
        }

        // Информация о выделении (символы и строки), если есть выделенный текст
        int[] sel = doc.selectionStats();
        if (sel != null) {
            String selLabel = (doc.box ? "Box " : "Sel ") + sel[0] + " ch" + (sel[1] > 1 ? " (" + sel[1] + " ln)" : "");
            int selW = this.font.width(selLabel);
            if (rightEdge - selW - 10 > minRightX) {
                g.fill(rightEdge, stY + 4, rightEdge + 1, stY + status - 4, EDGE);
                rightEdge -= 8;
                g.drawString(this.font, selLabel, rightEdge - selW, stY + 5, GOLD, false);
                rightEdge -= selW + 10;
            }
        }

        // Левая часть статусбара: показываем только при ошибке в текущей строке
        if (statusBad && !statusText.isEmpty()) {
            g.fill(px + 8, stY + 7, px + 12, stY + 11, RED);
            int maxStatusW = Math.max(40, rightEdge - (px + 18));
            g.drawString(this.font, trim(statusText, maxStatusW), px + 16, stY + 5, 0xFFF4B8BA, false);
        }
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
                return com.scriptcraft.lang.Phrase.forClient(issue.reason());
            }
        }
        statusBad = false;
        return "";
    }

    private void renderSidebar(GuiGraphics g, int mx, int my, boolean open, boolean right) {
        sidebarHits.clear();
        g.fill(sbX, sbY, sbX + sbW, sbY + sbH, SURFACE);
        border(g, sbX, sbY, sbW, sbH, explorerFocused ? ACCENT : EDGE);
        // Подсветка перетаскиваемого края проводника
        int resizeEdgeX = right ? sbX : sbX + sbW - 2;
        boolean edgeHot = open && (drag == Drag.SIDEBAR_RESIZE || (Math.abs(mx - resizeEdgeX) <= 3 && my >= sbY && my <= sbY + sbH));
        if (edgeHot) {
            g.fill(resizeEdgeX, sbY + 1, resizeEdgeX + 2, sbY + sbH - 1, GOLD);
        }

        sbToggleY = sbY + 3;
        if (!open) {
            sbToggleX = sbX + 3;
            boolean hot = hover(mx, my, sbToggleX, sbToggleY, 14, 14);
            if (hot) g.fill(sbToggleX, sbToggleY, sbToggleX + 14, sbToggleY + 14, ROW_HOT);
            g.drawString(this.font, right ? "«" : "»", sbToggleX + 4, sbToggleY + 3, hot ? GOLD : MUTED, false);
            sbBackX = sbFwdX = sbNewX = sbMkdirX = sbRenX = sbDelX = -1000;
            return;
        }

        // Заголовок: Проводник + кнопка сворачивания
        g.fill(sbX + 1, sbY + 1, sbX + sbW - 1, sbY + 19, BAR);
        g.drawString(this.font, trim(Messages.get("studio.sidebar.files"), sbW - 26), sbX + 7, sbY + 6, GOLD, false);
        sbToggleX = sbX + sbW - 17;
        boolean tHot = hover(mx, my, sbToggleX, sbToggleY, 14, 14);
        if (tHot) g.fill(sbToggleX, sbToggleY, sbToggleX + 14, sbToggleY + 14, ROW_HOT);
        g.drawString(this.font, right ? "»" : "«", sbToggleX + 4, sbToggleY + 3, tHot ? GOLD : MUTED, false);

        // Панель действий проводника: ← → + папка ✎ − (без кнопки обновить — авто-синхрон!)
        sbBarY = sbY + 20;
        g.fill(sbX + 1, sbBarY, sbX + sbW - 1, sbBarY + 17, 0xFF131924);
        g.fill(sbX + 1, sbBarY + 17, sbX + sbW - 1, sbBarY + 18, EDGE);
        int bx = sbX + 4;
        sbBackX = bx; bx += 16;
        sbFwdX = bx; bx += 18;
        sbNewX = bx; bx += 16;
        sbMkdirX = bx; bx += 16;
        sbRenX = bx; bx += 16;
        sbDelX = bx;
        paintExpBtn(g, mx, my, sbBackX, sbBarY + 2, "←", !StudioSession.dirBack.isEmpty(), null);
        paintExpBtn(g, mx, my, sbFwdX, sbBarY + 2, "→", !StudioSession.dirForward.isEmpty(), null);
        paintExpBtn(g, mx, my, sbNewX, sbBarY + 2, "+", true, Messages.get("studio.m.new"));
        paintExpBtn(g, mx, my, sbMkdirX, sbBarY + 2, "D", true, Messages.get("studio.exp.mkdir"));
        paintExpBtn(g, mx, my, sbRenX, sbBarY + 2, "✎", StudioSession.selectedExplorerPath != null, Messages.get("studio.exp.rename"));
        paintExpBtn(g, mx, my, sbDelX, sbBarY + 2, "-", StudioSession.selectedExplorerPath != null, Messages.get("studio.exp.delete"));

        // Текущий путь
        int pathY = sbBarY + 19;
        String curDir = StudioSession.explorerDir == null ? "" : StudioSession.explorerDir;
        String shownPath = curDir.isEmpty() ? "scriptcraft/" : "scriptcraft/" + curDir + "/";
        g.fill(sbX + 1, pathY, sbX + sbW - 1, pathY + 14, 0xFF0F141D);
        g.drawString(this.font, trim(shownPath, sbW - 10), sbX + 5, pathY + 3, MUTED, false);

        int listTop = pathY + 15;
        int listH = Math.max(10, sbY + sbH - listTop - 2);
        boolean hasParent = !curDir.isEmpty();
        int rowCount = (hasParent ? 1 : 0) + StudioSession.explorerEntries.size();
        int totalH = Math.max(1, rowCount) * 16 + 4;
        sidebarMaxScroll = Math.max(0, totalH - listH);
        sidebarScroll = Math.max(0, Math.min(sidebarScroll, sidebarMaxScroll));

        UUID selfId = this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getUUID() : null;
        g.enableScissor(sbX + 1, listTop, sbX + sbW - 1, sbY + sbH - 1);
        int ry = listTop + 2 - sidebarScroll;
        if (hasParent) {
            if (ry + 16 >= listTop && ry <= sbY + sbH) {
                boolean hot = hover(mx, my, sbX + 2, ry, sbW - 4, 15);
                if (hot) g.fill(sbX + 2, ry, sbX + sbW - 2, ry + 15, ROW);
                g.drawString(this.font, "..", sbX + 8, ry + 4, GOLD, false);
                sidebarHits.add(new SidebarHit(ry, 15, parentOf(curDir), true, true, -1));
            }
            ry += 16;
        }
        for (StudioExplorerPacket.Entry entry : StudioSession.explorerEntries) {
            if (ry + 16 >= listTop && ry <= sbY + sbH) {
                String full = curDir.isEmpty() ? entry.name() : curDir + "/" + entry.name();
                String tabKey = full.startsWith("scripts/") && full.endsWith(".sc")
                        ? full.substring("scripts/".length()) : "@" + full;
                boolean active = tabKey.equals(StudioSession.active);
                boolean selected = full.equals(StudioSession.selectedExplorerPath);
                boolean hot = hover(mx, my, sbX + 2, ry, sbW - 4, 15);
                if (active || selected) {
                    g.fill(sbX + 2, ry, sbX + sbW - 2, ry + 15, active ? ROW_HOT : 0xFF202B3D);
                    g.fill(sbX + 2, ry, sbX + 4, ry + 15, active ? GOLD : ACCENT);
                } else if (hot) {
                    g.fill(sbX + 2, ry, sbX + sbW - 2, ry + 15, ROW);
                }

                // Справа: переключатель вкл/выкл для .sc скриптов + головы редакторов в этом файле
                int rightLim = sbX + sbW - 5;
                int toggleX = -1;
                boolean isScript = !entry.dir() && full.startsWith("scripts/") && entry.name().endsWith(".sc");
                if (isScript) {
                    boolean disabled = entry.name().startsWith("-");
                    toggleX = rightLim - 11;
                    boolean togHot = hover(mx, my, toggleX, ry + 2, 11, 11);
                    g.fill(toggleX + 2, ry + 5, toggleX + 8, ry + 10, disabled ? 0xFF475264 : GREEN);
                    if (togHot) {
                        border(g, toggleX, ry + 2, 11, 11, GOLD);
                        tip = Messages.get("studio.exp.toggle");
                        tipX = mx + 8;
                        tipY = my + 12;
                    }
                    rightLim = toggleX - 3;
                }
                // Головы редакторов, сидящих в этом файле
                for (StudioExplorerPacket.ActiveEditor ed : StudioSession.activeEditors) {
                    if (selfId != null && selfId.equals(ed.id())) continue;
                    if (!ed.path().equals(full)) continue;
                    if (rightLim - 11 < sbX + 26) break;
                    rightLim -= 10;
                    int pc = peerColor(ed.name());
                    g.fill(rightLim - 1, ry + 2, rightLim + 9, ry + 12, pc);
                    blitHead(g, ed.id(), rightLim, ry + 3, 8);
                    if (hover(mx, my, rightLim - 1, ry + 2, 10, 10)) {
                        tip = ed.name();
                        tipX = mx + 8;
                        tipY = my + 12;
                    }
                    rightLim -= 2;
                }

                int tx = sbX + 7;
                if (entry.dir()) {
                    // Маленькая иконка папки
                    g.fill(tx, ry + 5, tx + 8, ry + 11, GOLD);
                    g.fill(tx, ry + 4, tx + 4, ry + 5, GOLD);
                    tx += 11;
                } else {
                    boolean hasErr = false;
                    if (isScript) {
                        for (ScriptLog.Issue issue : StudioSession.issues) {
                            if (issue.file().equals(tabKey)) {
                                hasErr = true;
                                break;
                            }
                        }
                    }
                    if (hasErr) {
                        g.fill(tx, ry + 5, tx + 4, ry + 9, RED);
                        tx += 7;
                    } else if (dirty(tabKey)) {
                        g.fill(tx, ry + 5, tx + 4, ry + 9, GOLD);
                        tx += 7;
                    } else if (com.scriptcraft.studio.StudioExplorer.isImageName(entry.name())) {
                        g.fill(tx, ry + 4, tx + 6, ry + 10, ACCENT);
                        tx += 9;
                    }
                }
                boolean disabled = isScript && entry.name().startsWith("-");
                int color = entry.dir() ? GOLD : (active ? TEXT : (disabled ? 0xFF636E80 : (hot ? TEXT : MUTED)));
                int maxW = Math.max(12, rightLim - tx);
                g.drawString(this.font, trim(entry.name(), maxW), tx, ry + 4, color, false);
                sidebarHits.add(new SidebarHit(ry, 15, full, entry.dir(), false, toggleX));
            }
            ry += 16;
        }
        g.disableScissor();
    }

    private void paintExpBtn(GuiGraphics g, int mx, int my, int x, int y, String sym, boolean enabled, String hint) {
        boolean hot = enabled && hover(mx, my, x, y, 14, 13);
        if (hot) {
            g.fill(x, y, x + 14, y + 13, ROW_HOT);
            if (hint != null) {
                tip = hint;
                tipX = mx + 6;
                tipY = my + 14;
            }
        }
        int col = !enabled ? 0xFF424D60 : (hot ? GOLD : TEXT);
        g.drawString(this.font, sym, x + (14 - this.font.width(sym)) / 2, y + 3, col, false);
    }

    private static String parentOf(String dir) {
        if (dir == null || dir.isEmpty()) return "";
        int slash = dir.lastIndexOf('/');
        return slash < 0 ? "" : dir.substring(0, slash);
    }

    private void navigateDir(String next, boolean recordHistory) {
        String cur = StudioSession.explorerDir == null ? "" : StudioSession.explorerDir;
        String norm = com.scriptcraft.studio.StudioExplorer.normalizeRel(next);
        if (recordHistory && !norm.equals(cur)) {
            StudioSession.dirBack.push(cur);
            StudioSession.dirForward.clear();
        }
        StudioSession.explorerDir = norm;
        StudioSession.selectedExplorerPath = null;
        sidebarScroll = 0;
        ModNetwork.sendToServer(new StudioExplorerPacket.Action(StudioExplorerPacket.Kind.LIST, norm, ""));
    }

    private void renderAutocomplete(GuiGraphics g, int mx, int my, ScriptDocument doc) {
        List<String> items = acMatch.items();
        int maxW = 120;
        for (String item : items) {
            maxW = Math.max(maxW, this.font.width(item) + 16);
        }
        acW = Math.min(260, maxW);
        acH = items.size() * 14 + 6;
        String row = doc.lines.get(Math.max(0, Math.min(doc.lines.size() - 1, doc.line)));
        int cx = textX(row, Math.min(doc.col, row.length()));
        int cy = editorY + (doc.line - doc.scroll) * LINE_H + LINE_H + 3;
        if (cy + acH > editorY + editorH) {
            cy = Math.max(editorY + 2, cy - acH - LINE_H - 4);
        }
        acX = Math.max(editorX + 4, Math.min(cx, editorX + editorW - acW - 10));
        acY = cy;
        acSel = Math.max(0, Math.min(acSel, items.size() - 1));

        g.fill(acX + 2, acY + 2, acX + acW + 2, acY + acH + 2, 0x66000000);
        g.fill(acX, acY, acX + acW, acY + acH, 0xF8121824);
        border(g, acX, acY, acW, acH, ACCENT);
        for (int i = 0; i < items.size(); i++) {
            int iy = acY + 3 + i * 14;
            boolean sel = i == acSel;
            boolean hot = hover(mx, my, acX + 1, iy, acW - 2, 14);
            if (sel || hot) {
                g.fill(acX + 2, iy, acX + acW - 2, iy + 14, sel ? ROW_HOT : ROW);
                if (sel) g.fill(acX + 2, iy + 1, acX + 4, iy + 13, GOLD);
            }
            g.drawString(this.font, trim(items.get(i), acW - 12), acX + 7, iy + 3, sel ? GOLD : TEXT, false);
        }
    }

    private void refreshAutocomplete(ScriptDocument doc, boolean manual) {
        if (doc == null || !doc.loaded || (!manual && !ClientUiSettings.autocomplete())) {
            acMatch = null;
            return;
        }
        String row = doc.lines.get(Math.max(0, Math.min(doc.lines.size() - 1, doc.line)));
        acMatch = StudioAutocomplete.suggest(row, doc.col, manual);
        acSel = 0;
    }

    private void applyAutocomplete(ScriptDocument doc) {
        if (doc == null || acMatch == null || acMatch.items().isEmpty()) {
            acMatch = null;
            return;
        }
        String pick = acMatch.items().get(Math.max(0, Math.min(acSel, acMatch.items().size() - 1)));
        String row = doc.lines.get(doc.line);
        int curCol = Math.max(0, Math.min(row.length(), doc.col));
        int from = Math.max(0, Math.min(acMatch.replaceFrom(), curCol));
        doc.box = false;
        doc.anchorLine = doc.line;
        doc.anchorCol = from;
        doc.col = curCol;
        doc.insert(pick);
        acMatch = null;
    }

    private void renderGotoBox(GuiGraphics g) {
        int x = px + pw / 2 - 84;
        int y = py + ph / 2 - 18;
        g.fill(x + 2, y + 2, x + 170, y + 38, 0x55000000);
        g.fill(x, y, x + 168, y + 36, 0xF7121824);
        border(g, x, y, 168, 36, ACCENT);
        gotoField.render(g, this.font, x + 8, y + 10, 152, 16, true, Messages.get("studio.goto") + "...", 0xFF0B1018, ACCENT, TEXT, MUTED);
    }

    private void renderSettings(GuiGraphics g, int mx, int my) {
        hotkeyHits.clear();
        int y0 = contentY() + 8;
        int availH = Math.max(120, contentH() - 12);
        int midX = px + Math.max(255, Math.min(315, pw / 2));

        // --- Левая колонка: Уведомления + Настройки редактора ---
        int x = px + 14;
        int y = y0;
        g.drawString(this.font, Messages.get("log.settings"), x, y, GOLD, false);
        y += 16;
        g.drawString(this.font, Messages.get("log.key"), x, y + 2, MUTED, false);
        int kbW = Math.max(100, midX - x - 120);
        g.fill(x + 96, y, x + 96 + kbW, y + 14, listening ? ROW_HOT : ROW);
        border(g, x + 96, y, kbW, 14, listening ? GOLD : EDGE);
        g.drawString(this.font, trim(listening ? Messages.get("log.key_wait") : ClientUiSettings.keyLabel(), kbW - 8), x + 101, y + 3, listening ? GOLD : TEXT, false);
        keyBoxX = x + 96;
        keyBoxY = y;
        y += 20;
        slider1Y = y + 12;
        g.drawString(this.font, Messages.get("log.time") + "  " + String.format(Locale.ROOT, "%.1f s", ClientUiSettings.seconds()), x, y, MUTED, false);
        drawSlider(g, x, slider1Y, (ClientUiSettings.seconds() - 1.5f) / 8.5f);
        y += 24;
        slider2Y = y + 12;
        g.drawString(this.font, Messages.get("log.size") + "  " + Math.round(ClientUiSettings.scale() * 100) + "%", x, y, MUTED, false);
        drawSlider(g, x, slider2Y, (ClientUiSettings.scale() - 0.7f) / 0.8f);
        y += 24;
        g.drawString(this.font, Messages.get("log.place"), x, y, MUTED, false);
        y += 12;
        place(g, mx, my, x, y, ClientUiSettings.Place.CHAT, Messages.get("log.place.chat"));
        place(g, mx, my, x + 116, y, ClientUiSettings.Place.TOP_LEFT, Messages.get("log.place.tl"));
        place(g, mx, my, x, y + 18, ClientUiSettings.Place.TOP_RIGHT, Messages.get("log.place.tr"));
        place(g, mx, my, x + 116, y + 18, ClientUiSettings.Place.BOTTOM_RIGHT, Messages.get("log.place.br"));
        sliderX = x;

        y += 44;
        g.fill(x, y, midX - 14, y + 1, EDGE);
        y += 8;
        g.drawString(this.font, Messages.get("studio.set.editor"), x, y, GOLD, false);
        y += 16;

        setAcX = x;
        setAcY = y;
        drawSettingsCheck(g, mx, my, x, y, midX - x - 18, "set:ac", Messages.get("studio.set.autocomplete"), ClientUiSettings.autocomplete());
        y += 17;
        setAutoX = x;
        setAutoY = y;
        drawSettingsCheck(g, mx, my, x, y, midX - x - 18, "set:auto", Messages.get("studio.set.autoclose"), ClientUiSettings.autoClose());
        y += 20;
        g.drawString(this.font, Messages.get("studio.sidebar.side"), x, y + 3, MUTED, false);
        setSbY = y;
        setSbLeftX = x + 104;
        setSbRightX = x + 166;
        drawSideBtn(g, mx, my, setSbLeftX, setSbY, 58, Messages.get("studio.sidebar.left"), !ClientUiSettings.sidebarRight());
        drawSideBtn(g, mx, my, setSbRightX, setSbY, 58, Messages.get("studio.sidebar.right"), ClientUiSettings.sidebarRight());
        y += 19;
        g.drawString(this.font, Messages.get("studio.set.language"), x, y + 3, MUTED, false);
        setLangY = y;
        setLangRuX = x + 104;
        setLangEnX = x + 166;
        boolean isRu = "ru".equals(ClientUiSettings.language());
        drawSideBtn(g, mx, my, setLangRuX, setLangY, 58, "РУС", isRu);
        drawSideBtn(g, mx, my, setLangEnX, setLangY, 58, "ENG", !isRu);
        y += 20;
        setProfX = x;
        setProfY = y;
        drawSettingsCheck(g, mx, my, x, y, midX - x - 18, "set:prof", Messages.get("studio.set.profiler"), ClientUiSettings.showProfiler());
        y += 20;

        // Палитра цвета редактора для совместной работы
        g.drawString(this.font, Messages.get("studio.set.editor_color"), x, y, MUTED, false);
        y += 12;
        setColorX = x;
        setColorY = y;
        int curEdCol = ClientUiSettings.editorColor();
        int[] pal = ClientUiSettings.EDITOR_COLORS;
        for (int i = 0; i < pal.length; i++) {
            int sx = x + i * 18;
            int sc = pal[i];
            boolean on = curEdCol == sc;
            boolean hot = hover(mx, my, sx, y, 15, 14);
            g.fill(sx, y, sx + 15, y + 14, sc == 0 ? 0xFF1F2937 : sc);
            if (sc == 0) {
                g.drawString(this.font, "A", sx + 5, y + 3, on ? GOLD : TEXT, false);
            }
            border(g, sx, y, 15, 14, on ? GOLD : (hot ? 0xFFFFFFFF : EDGE));
            if (on && sc != 0) {
                g.fill(sx + 5, y + 5, sx + 10, y + 9, 0xFF0A0E15);
            }
        }

        // --- Правая колонка: Горячие клавиши + Статус библиотек ---
        int rx = midX + 6;
        int rw = Math.max(160, px + pw - rx - 14);
        int libsH = 74;
        int rh = Math.max(96, availH - libsH - 8);
        g.fill(rx, y0 - 2, rx + rw, y0 + rh, SURFACE);
        border(g, rx, y0 - 2, rw, rh + 2, EDGE);
        g.drawString(this.font, Messages.get("studio.set.hotkeys"), rx + 10, y0 + 5, GOLD, false);

        String resetLbl = Messages.get("studio.set.reset_keys");
        hkResetW = this.font.width(resetLbl) + 12;
        hkResetX = rx + rw - hkResetW - 8;
        hkResetY = y0 + 2;
        paintMiniBtn(g, mx, my, hkResetX, hkResetY, hkResetW, 14, resetLbl, "set:reset");
        g.fill(rx + 1, y0 + 19, rx + rw - 1, y0 + 20, EDGE);

        int listTop = y0 + 23;
        int listH = Math.max(30, rh - 26);
        Map<String, ClientUiSettings.KeyBind> binds = ClientUiSettings.keyBinds();
        int totalH = binds.size() * 18 + 4;
        hotkeyMaxScroll = Math.max(0, totalH - listH);
        hotkeyScroll = Math.max(0, Math.min(hotkeyScroll, hotkeyMaxScroll));

        g.enableScissor(rx + 1, listTop, rx + rw - 1, y0 + rh - 1);
        int hy = listTop + 2 - hotkeyScroll;
        int keyW = Math.min(106, Math.max(76, rw / 3));
        for (var entry : binds.entrySet()) {
            String id = entry.getKey();
            ClientUiSettings.KeyBind bind = entry.getValue();
            if (hy + 17 >= listTop && hy <= y0 + rh) {
                boolean active = id.equals(listeningBind);
                int bx = rx + rw - keyW - 8;
                boolean hot = hover(mx, my, bx, hy, keyW, 15);
                g.drawString(this.font, trim(Messages.get("studio.hk." + id), bx - rx - 14), rx + 10, hy + 4, TEXT, false);
                g.fill(bx, hy, bx + keyW, hy + 15, active || hot ? ROW_HOT : ROW);
                border(g, bx, hy, keyW, 15, active ? GOLD : (hot ? ACCENT : EDGE));
                String lbl = active ? Messages.get("studio.set.press_key") : bind.label();
                g.drawString(this.font, trim(lbl, keyW - 8), bx + 5, hy + 4, active ? GOLD : (hot ? TEXT : MUTED), false);
                hotkeyHits.add(new HotkeyHit(bx, hy, keyW, 15, id));
            }
            hy += 18;
        }
        g.disableScissor();

        // Панель статуса библиотек (PlayerAnimator, Bendy-lib, LuckPerms)
        int ly = y0 + rh + 6;
        g.fill(rx, ly, rx + rw, ly + libsH, SURFACE);
        border(g, rx, ly, rw, libsH, EDGE);
        g.drawString(this.font, Messages.get("studio.set.libs"), rx + 10, ly + 5, GOLD, false);
        g.fill(rx + 1, ly + 17, rx + rw - 1, ly + 18, EDGE);
        drawLibStatusRow(g, rx, ly + 22, rw, "PlayerAnimator", modStatusText("playeranimator"),
                net.minecraftforge.fml.ModList.get().isLoaded("playeranimator"));
        drawLibStatusRow(g, rx, ly + 38, rw, "Bendy-lib", modStatusText("bendylib"),
                net.minecraftforge.fml.ModList.get().isLoaded("bendylib"));
        String lpCode = StudioSession.serverLuckPerms;
        boolean lpOk = lpCode != null && !lpCode.equals("NONE");
        String lpKey = switch (lpCode == null ? "NONE" : lpCode) {
            case "MOD" -> "lp.mod";
            case "PLUGIN" -> "lp.plugin";
            case "BOTH" -> "lp.both";
            case "API" -> "lp.api";
            default -> "lp.none";
        };
        drawLibStatusRow(g, rx, ly + 54, rw, "LuckPerms", Messages.get(lpKey).replaceFirst("^LuckPerms:\\s*", ""), lpOk);
    }

    private static String modStatusText(String modId) {
        var mod = net.minecraftforge.fml.ModList.get().getModContainerById(modId);
        if (mod.isPresent()) {
            return Messages.get("studio.lib.loaded") + " (v" + mod.get().getModInfo().getVersion() + ")";
        }
        return Messages.get("studio.lib.missing");
    }

    private void drawLibStatusRow(GuiGraphics g, int rx, int y, int rw, String name, String status, boolean ok) {
        g.fill(rx + 10, y + 3, rx + 15, y + 8, ok ? GREEN : 0xFFEF4444);
        g.drawString(this.font, name, rx + 20, y + 1, TEXT, false);
        int sw = this.font.width(status);
        int sx = Math.max(rx + 120, rx + rw - sw - 10);
        g.drawString(this.font, trim(status, rx + rw - sx - 8), sx, y + 1, ok ? GREEN : MUTED, false);
    }

    private void drawSettingsCheck(GuiGraphics g, int mx, int my, int x, int y, int w, String key, String label, boolean on) {
        boolean hot = hover(mx, my, x, y, w, 14);
        float t = animHover(key, hot);
        g.fill(x, y + 1, x + 12, y + 13, on ? 0xFF1F382E : lerpColor(0xFF0E131D, ROW, t));
        border(g, x, y + 1, 12, 12, on ? GREEN : lerpColor(EDGE, ACCENT, t));
        if (on) {
            g.fill(x + 2, y + 6, x + 4, y + 8, GREEN);
            g.fill(x + 4, y + 7, x + 6, y + 9, GREEN);
            g.fill(x + 5, y + 5, x + 7, y + 7, GREEN);
            g.fill(x + 7, y + 3, x + 9, y + 5, GREEN);
        }
        g.drawString(this.font, trim(label, Math.max(20, w - 18)), x + 17, y + 3, on ? TEXT : lerpColor(MUTED, TEXT, t), false);
    }

    private void drawSideBtn(GuiGraphics g, int mx, int my, int x, int y, int w, String label, boolean on) {
        boolean hot = hover(mx, my, x, y, w, 15);
        g.fill(x, y, x + w, y + 15, on || hot ? ROW_HOT : ROW);
        border(g, x, y, w, 15, on ? GOLD : (hot ? ACCENT : EDGE));
        g.drawString(this.font, label, x + (w - this.font.width(label)) / 2, y + 4, on ? GOLD : TEXT, false);
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
            boolean resItemBlink = "reload".equals(menu) && i == 1 && StudioSession.resourcesDirty;
            if (resItemBlink) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 220.0);
                g.fill(x + 2, iy, x + w - 2, iy + 15, withAlpha(GREEN, 0.20f + 0.30f * pulse));
                g.fill(x + 2, iy + 2, x + 4, iy + 13, withAlpha(0xFF7EE787, 0.55f + 0.45f * pulse));
                g.drawString(this.font, items.get(i), x + 8, iy + 4, lerpColor(0xFF56D364, 0xFFC2FFD0, Math.max(ht, pulse)), false);
                continue;
            }
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
        String hint = "mkdir".equals(namingMode) ? Messages.get("studio.exp.folder_name")
                : ("rename".equals(namingMode) ? Messages.get("studio.exp.rename") : Messages.get("studio.new_name"));
        nameField.render(g, this.font, x + 8, y + 10, 176, 16, true, hint, 0xFF0B1018, GOLD, TEXT, MUTED);
    }

    private void layoutToolPanel() {
        int minW = 210, minH = 176;
        int maxW = Math.max(minW, pw - 20);
        int maxH = Math.max(minH, ph - 50);
        StudioSession.toolPanelW = Math.max(minW, Math.min(maxW, StudioSession.toolPanelW));
        StudioSession.toolPanelH = Math.max(minH, Math.min(maxH, StudioSession.toolPanelH));
        if (!StudioSession.toolPanelPlaced) {
            StudioSession.toolPanelX = px + pw - StudioSession.toolPanelW - 18;
            StudioSession.toolPanelY = editorY + 8;
            StudioSession.toolPanelPlaced = true;
        }
        StudioSession.toolPanelX = Math.max(px + 4, Math.min(StudioSession.toolPanelX, px + pw - StudioSession.toolPanelW - 4));
        StudioSession.toolPanelY = Math.max(py + TITLE, Math.min(StudioSession.toolPanelY, py + ph - StudioSession.toolPanelH - 4));
    }

    private boolean inToolPanel(int mx, int my) {
        return StudioSession.toolPanelOpen && script() != null && !settings
                && hover(mx, my, StudioSession.toolPanelX, StudioSession.toolPanelY, StudioSession.toolPanelW, StudioSession.toolPanelH);
    }

    private Drag panelEdgeAt(int mx, int my) {
        if (!StudioSession.toolPanelOpen || script() == null || settings) {
            return Drag.NONE;
        }
        int x = StudioSession.toolPanelX;
        int y = StudioSession.toolPanelY;
        int w = StudioSession.toolPanelW;
        int h = StudioSession.toolPanelH;
        // Уголок ресайза справа-снизу 10×10
        if (mx >= x + w - 10 && mx <= x + w + 3 && my >= y + h - 10 && my <= y + h + 3) {
            return Drag.PANEL_SE;
        }
        boolean left = Math.abs(mx - x) <= 4 && my >= y && my <= y + h;
        boolean right = Math.abs(mx - (x + w)) <= 4 && my >= y && my <= y + h;
        boolean top = Math.abs(my - y) <= 4 && mx >= x && mx <= x + w;
        boolean bottom = Math.abs(my - (y + h)) <= 4 && mx >= x && mx <= x + w;
        if (top && left) return Drag.PANEL_NW;
        if (top && right) return Drag.PANEL_NE;
        if (bottom && left) return Drag.PANEL_SW;
        if (bottom && right) return Drag.PANEL_SE;
        if (left) return Drag.PANEL_W;
        if (right) return Drag.PANEL_E;
        if (top) return Drag.PANEL_N;
        if (bottom) return Drag.PANEL_S;
        return Drag.NONE;
    }

    private void openToolPanel(int focus) {
        StudioSession.toolPanelOpen = true;
        panelFocus = focus;
        panelOpenedAt = System.currentTimeMillis();
        ctxOpen = false;
    }

    private void renderToolPanel(GuiGraphics g, int mx, int my) {
        layoutToolPanel();
        panelChecks.clear();
        int x = StudioSession.toolPanelX;
        int y = StudioSession.toolPanelY;
        int w = StudioSession.toolPanelW;
        int h = StudioSession.toolPanelH;
        float t = easeOutCubic((System.currentTimeMillis() - panelOpenedAt) / 140f);
        int dy = Math.round((1f - t) * 6f);
        y += dy;

        g.fill(x + 3, y + 3, x + w + 3, y + h + 3, withAlpha(0x66000000, t));
        g.fill(x, y, x + w, y + h, 0xF7131A26);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + 2, withAlpha(GOLD, 0.8f), withAlpha(ACCENT, 0.7f));
        border(g, x, y, w, h, EDGE_HOT);

        // Заголовок панели (перетаскиваемый)
        int hdrH = 18;
        g.fill(x + 1, y + 2, x + w - 1, y + hdrH, BAR);
        g.fill(x + 1, y + hdrH, x + w - 1, y + hdrH + 1, EDGE);
        g.drawString(this.font, Messages.get("studio.panel.title"), x + 8, y + 6, GOLD, false);
        pCloseX = x + w - 16;
        pCloseY = y + 3;
        boolean closeHot = hover(mx, my, pCloseX, pCloseY, 13, 13);
        if (closeHot) {
            g.fill(pCloseX, pCloseY, pCloseX + 13, pCloseY + 13, withAlpha(RED, 0.3f));
        }
        g.drawString(this.font, "x", pCloseX + 4, pCloseY + 2, closeHot ? RED : MUTED, false);

        g.enableScissor(x + 1, y + hdrH + 1, x + w - 1, y + h - 1);
        int innerX = x + 8;
        int innerW = w - 16;
        int cy = y + hdrH + 7;

        // Поле "Найти" + счётчик совпадений + кнопки ↑ ↓
        ScriptDocument doc = script();
        String findVal = findField.value();
        List<int[]> matches = doc != null && !findVal.isEmpty()
                ? doc.findAllMatches(findVal, StudioSession.optMatchCase, StudioSession.optWholeWord)
                : List.of();
        String countText = findVal.isEmpty() ? "" : (matches.isEmpty() ? "0" : Integer.toString(matches.size()));
        int navW = 36;
        pFindX = innerX;
        pFindY = cy;
        pFindW = Math.max(60, innerW - navW - 4);
        int cntW = countText.isEmpty() ? 0 : this.font.width(countText) + 6;
        findField.render(g, this.font, pFindX, pFindY, Math.max(30, pFindW - cntW), 15,
                panelFocus == 1, Messages.get("studio.find"), 0xFF0D121B, panelFocus == 1 ? ACCENT : EDGE, TEXT, MUTED);
        if (!countText.isEmpty()) {
            g.fill(pFindX + pFindW - cntW, pFindY, pFindX + pFindW, pFindY + 15, 0xFF0D121B);
            border(g, pFindX + pFindW - cntW, pFindY, cntW, 15, panelFocus == 1 ? ACCENT : EDGE);
            g.drawString(this.font, countText, pFindX + pFindW - cntW + 3, pFindY + 4, matches.isEmpty() ? RED : GOLD, false);
        }
        pPrevX = pFindX + pFindW + 4;
        pNextX = pPrevX + 18;
        pNavY = pFindY;
        paintMiniBtn(g, mx, my, pPrevX, pNavY, 16, 15, "↑", "p:prev");
        paintMiniBtn(g, mx, my, pNextX, pNavY, 16, 15, "↓", "p:next");
        cy += 19;

        // Поле "Заменить"
        pRepX = innerX;
        pRepY = cy;
        pRepW = innerW;
        replaceField.render(g, this.font, pRepX, pRepY, pRepW, 15,
                panelFocus == 2, Messages.get("studio.replace"), 0xFF0D121B, panelFocus == 2 ? ACCENT : EDGE, TEXT, MUTED);
        cy += 19;

        // Кнопки "Заменить" и "Все"
        pActY = cy;
        String repLabel = Messages.get("studio.replace");
        String allLabel = Messages.get("studio.replace_all");
        pRepBtnX = innerX;
        pRepBtnW = Math.max(60, (innerW - 6) / 2);
        pAllBtnX = pRepBtnX + pRepBtnW + 6;
        pAllBtnW = innerW - pRepBtnW - 6;
        paintMiniBtn(g, mx, my, pRepBtnX, pActY, pRepBtnW, 15, repLabel, "p:rep");
        paintMiniBtn(g, mx, my, pAllBtnX, pActY, pAllBtnW, 15, allLabel, "p:all");
        cy += 21;

        // Разделитель и галочки поиска + синтаксиса/редактора
        g.fill(innerX, cy, innerX + innerW, cy + 1, EDGE);
        cy += 6;

        boolean twoCols = innerW >= 220;
        int colW = twoCols ? (innerW - 6) / 2 : innerW;
        Object[][] opts = {
                {"case", Messages.get("studio.opt.case"), StudioSession.optMatchCase},
                {"word", Messages.get("studio.opt.word"), StudioSession.optWholeWord},
                {"syntax", Messages.get("studio.opt.syntax"), StudioSession.optSyntax},
                {"colors", Messages.get("studio.opt.colors"), StudioSession.optQuoteColors},
                {"indent", Messages.get("studio.opt.indent"), StudioSession.optAutoIndent},
                {"lines", Messages.get("studio.opt.lines"), StudioSession.optLineNumbers},
                {"curline", Messages.get("studio.opt.curline"), StudioSession.optActiveLine},
                {"preview", Messages.get("studio.opt.preview"), StudioSession.optTexturePreview}
        };
        for (int i = 0; i < opts.length; i++) {
            int cx = twoCols && (i % 2 == 1) ? innerX + colW + 6 : innerX;
            int rowY = twoCols ? cy + (i / 2) * 16 : cy + i * 15;
            String id = (String) opts[i][0];
            String label = (String) opts[i][1];
            boolean on = (Boolean) opts[i][2];
            drawCheck(g, mx, my, cx, rowY, colW, id, label, on);
        }
        g.disableScissor();

        // Индикатор уголка изменения размера (правый нижний угол)
        g.fill(x + w - 7, y + h - 3, x + w - 3, y + h - 2, MUTED);
        g.fill(x + w - 3, y + h - 7, x + w - 2, y + h - 3, MUTED);
        g.fill(x + w - 5, y + h - 5, x + w - 3, y + h - 4, GOLD);
    }

    private void paintMiniBtn(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String label, String key) {
        boolean hot = hover(mx, my, x, y, w, h);
        float t = animHover(key, hot);
        g.fill(x, y, x + w, y + h, lerpColor(ROW, ROW_HOT, t));
        border(g, x, y, w, h, lerpColor(EDGE, GOLD, t * 0.8f));
        String shown = trim(label, w - 4);
        g.drawString(this.font, shown, x + (w - this.font.width(shown)) / 2, y + 4, lerpColor(TEXT, GOLD, t), false);
    }

    private void drawCheck(GuiGraphics g, int mx, int my, int x, int y, int w, String id, String label, boolean on) {
        boolean hot = hover(mx, my, x, y, w, 13);
        float t = animHover("chk:" + id, hot);
        g.fill(x, y + 1, x + 11, y + 12, on ? 0xFF1F382E : lerpColor(0xFF0E131D, ROW, t));
        border(g, x, y + 1, 11, 11, on ? GREEN : lerpColor(EDGE, ACCENT, t));
        if (on) {
            g.fill(x + 2, y + 6, x + 4, y + 8, GREEN);
            g.fill(x + 4, y + 7, x + 6, y + 9, GREEN);
            g.fill(x + 5, y + 5, x + 7, y + 7, GREEN);
            g.fill(x + 7, y + 3, x + 9, y + 5, GREEN);
        }
        g.drawString(this.font, trim(label, Math.max(16, w - 16)), x + 15, y + 3, on ? TEXT : lerpColor(MUTED, TEXT, t), false);
        panelChecks.add(new PanelCheck(x, y, w, 13, id));
    }

    private void togglePanelCheck(String id) {
        switch (id) {
            case "case" -> StudioSession.optMatchCase = !StudioSession.optMatchCase;
            case "word" -> StudioSession.optWholeWord = !StudioSession.optWholeWord;
            case "syntax" -> StudioSession.optSyntax = !StudioSession.optSyntax;
            case "colors" -> StudioSession.optQuoteColors = !StudioSession.optQuoteColors;
            case "indent" -> StudioSession.optAutoIndent = !StudioSession.optAutoIndent;
            case "lines" -> StudioSession.optLineNumbers = !StudioSession.optLineNumbers;
            case "curline" -> StudioSession.optActiveLine = !StudioSession.optActiveLine;
            case "preview" -> StudioSession.optTexturePreview = !StudioSession.optTexturePreview;
            default -> {
            }
        }
    }

    private List<String> ctxItems() {
        return List.of(
                Messages.get("studio.m.cut"),
                Messages.get("studio.m.copy"),
                Messages.get("studio.m.paste"),
                Messages.get("studio.m.all"),
                Messages.get("studio.find_replace"),
                Messages.get("studio.m.tools")
        );
    }

    private void renderCtxMenu(GuiGraphics g, int mx, int my) {
        List<String> items = ctxItems();
        int w = 152;
        int fullH = items.size() * 15 + 6;
        int x = Math.max(px + 4, Math.min(ctxX, px + pw - w - 4));
        int y = Math.max(py + TITLE, Math.min(ctxY, py + ph - fullH - 4));
        ctxX = x;
        ctxY = y;
        float t = easeOutCubic((System.currentTimeMillis() - ctxOpenedAt) / 110f);
        int h = Math.max(8, Math.round(fullH * t));
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x55000000);
        g.fill(x, y, x + w, y + h, 0xF7121824);
        border(g, x, y, w, h, EDGE_HOT);
        g.enableScissor(x, y, x + w, y + h);
        for (int i = 0; i < items.size(); i++) {
            int iy = y + 3 + i * 15;
            boolean hot = hover(mx, my, x + 1, iy, w - 2, 15);
            float ht = animHover("ctx:" + i, hot);
            if (ht > 0.02f) {
                g.fill(x + 2, iy, x + w - 2, iy + 15, withAlpha(ROW_HOT, ht));
                g.fill(x + 2, iy + 2, x + 4, iy + 13, withAlpha(GOLD, ht));
            }
            g.drawString(this.font, items.get(i), x + 8, iy + 4, lerpColor(TEXT, 0xFFFFFFFF, ht), false);
        }
        g.disableScissor();
    }

    private void runCtx(int index) {
        ScriptDocument doc = script();
        switch (index) {
            case 0 -> runTool("cut");
            case 1 -> runTool("copy");
            case 2 -> runTool("paste");
            case 3 -> {
                if (doc != null) doc.selectAll();
            }
            case 4 -> openToolPanel(1);
            case 5 -> {
                StudioSession.toolPanelOpen = !StudioSession.toolPanelOpen;
                if (StudioSession.toolPanelOpen) {
                    panelOpenedAt = System.currentTimeMillis();
                }
            }
            default -> {
            }
        }
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
        if (StudioSession.historyOpen) {
            historyClick(mx, my);
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
            nameField.mouseClicked(this.font, mx, my, button);
            return true;
        }
        if (gotoOpen) {
            gotoField.mouseClicked(this.font, mx, my, button);
            return true;
        }
        if (acMatch != null && !acMatch.items().isEmpty() && hover(mx, my, acX, acY, acW, acH)) {
            int idx = (my - (acY + 3)) / 14;
            if (idx >= 0 && idx < acMatch.items().size()) {
                acSel = idx;
                applyAutocomplete(script());
            }
            return true;
        }
        acMatch = null;
        if (ctxOpen) {
            List<String> items = ctxItems();
            int cw = 152;
            int ch = items.size() * 15 + 6;
            if (mx >= ctxX && mx < ctxX + cw && my >= ctxY && my < ctxY + ch) {
                int idx = (my - (ctxY + 3)) / 15;
                ctxOpen = false;
                if (idx >= 0 && idx < items.size()) {
                    runCtx(idx);
                }
                return true;
            }
            ctxOpen = false;
        }
        if (StudioSession.toolPanelOpen && script() != null && !settings) {
            Drag pEdge = panelEdgeAt(mx, my);
            if (pEdge != Drag.NONE) {
                drag = pEdge;
                grabX = mx;
                grabY = my;
                originX = StudioSession.toolPanelX;
                originY = StudioSession.toolPanelY;
                originW = StudioSession.toolPanelW;
                originH = StudioSession.toolPanelH;
                return true;
            }
            if (inToolPanel(mx, my)) {
                if (hover(mx, my, pCloseX, pCloseY, 13, 13)) {
                    StudioSession.toolPanelOpen = false;
                    panelFocus = 0;
                    return true;
                }
                if (my < StudioSession.toolPanelY + 19) {
                    drag = Drag.PANEL_MOVE;
                    grabX = mx - StudioSession.toolPanelX;
                    grabY = my - StudioSession.toolPanelY;
                    return true;
                }
                if (hover(mx, my, pFindX, pFindY, pFindW, 15)) {
                    panelFocus = 1;
                    findField.mouseClicked(this.font, mx, my, button);
                    return true;
                }
                if (hover(mx, my, pRepX, pRepY, pRepW, 15)) {
                    panelFocus = 2;
                    replaceField.mouseClicked(this.font, mx, my, button);
                    return true;
                }
                if (hover(mx, my, pPrevX, pNavY, 16, 15)) {
                    findPrev(script());
                    return true;
                }
                if (hover(mx, my, pNextX, pNavY, 16, 15)) {
                    findNext(script());
                    return true;
                }
                if (hover(mx, my, pRepBtnX, pActY, pRepBtnW, 15)) {
                    replaceOne(script());
                    return true;
                }
                if (hover(mx, my, pAllBtnX, pActY, pAllBtnW, 15)) {
                    replaceAll(script());
                    return true;
                }
                for (PanelCheck chk : panelChecks) {
                    if (hover(mx, my, chk.x, chk.y, chk.w, chk.h)) {
                        togglePanelCheck(chk.id);
                        return true;
                    }
                }
                panelFocus = 0;
                return true;
            }
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
                    if (StudioSession.ANIMATOR.equals(StudioSession.active) && !StudioSession.ANIMATOR.equals(tab)) {
                        StudioAnimEditor.onLeaveTab();
                    }
                    StudioSession.active = tab;
                    if (StudioSession.PROFILER.equals(tab)) {
                        lastProfFetchMs = 0L;
                    } else if (StudioSession.ANIMATOR.equals(tab)) {
                        StudioAnimEditor.requestServerFiles();
                    } else if (!StudioSession.HOME.equals(tab) && !StudioSession.JOURNAL.equals(tab)) {
                        StudioSession.lastScriptTab = tab;
                    }
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
        if (StudioSession.ANIMATOR.equals(StudioSession.active) && my >= py + TITLE + MENU_H) {
            StudioAnimEditor.mouseClicked(this.font, mx, my, button);
            return true;
        }
        if (StudioSession.PROFILER.equals(StudioSession.active) && ClientUiSettings.showProfiler()
                && my >= py + TITLE + MENU_H) {
            profilerClick(mx, my);
            return true;
        }
        if (script() != null && my >= py + TITLE + MENU_H && my < py + TITLE + MENU_H + TOOL_H) {
            for (PeerHeadHit hit : peerHeadHits) {
                if (hover(mx, my, hit.x, hit.y, hit.w, hit.h)) {
                    ScriptDocument d = script();
                    if (d != null && d.loaded && !d.isImage) {
                        d.move(hit.line, hit.col, false);
                        d.followCaret = true;
                    }
                    return true;
                }
            }
            if (histBtnX >= 0 && hover(mx, my, histBtnX, histBtnY, histBtnW, 16)) {
                ScriptDocument d = script();
                if (d != null && !d.genericFile && !d.isImage) {
                    StudioSession.historyOpen = true;
                    StudioSession.historyScript = d.name;
                    StudioSession.historySelectedId = "";
                    StudioSession.historyPreviewText = "";
                    histListScroll = 0;
                    histPreviewScroll = 0;
                    ModNetwork.sendToServer(new com.scriptcraft.network.ScriptHistoryPacket.Request(
                            com.scriptcraft.network.ScriptHistoryPacket.LIST, d.name, ""));
                }
                return true;
            }
            if (guiBtnX >= 0 && hover(mx, my, guiBtnX, guiBtnY, guiBtnW, 16)) {
                ScriptDocument d = script();
                if (d != null && !d.genericFile && !d.isImage) {
                    StudioSession.guiEditorMode = !StudioSession.guiEditorMode;
                    if (StudioSession.guiEditorMode) {
                        StudioGuiEditor.reloadFromDocument(d);
                    }
                }
                return true;
            }
            toolClick(mx);
            return true;
        }
        ScriptDocument doc = script();
        boolean hasSidebar = !settings && (doc != null
                || StudioSession.HOME.equals(StudioSession.active)
                || StudioSession.JOURNAL.equals(StudioSession.active));
        if (hasSidebar) {
            // Сайдбар файлов: перетаскивание границы и клики по файлам
            int resizeEdgeX = ClientUiSettings.sidebarRight() ? sbX : sbX + sbW - 2;
            if (ClientUiSettings.sidebarOpen() && Math.abs(mx - resizeEdgeX) <= 3 && my >= sbY && my <= sbY + sbH) {
                drag = Drag.SIDEBAR_RESIZE;
                grabX = mx;
                originW = ClientUiSettings.sidebarWidth();
                return true;
            }
            if (hover(mx, my, sbX, sbY, sbW, sbH)) {
                explorerFocused = true;
                if (hover(mx, my, sbToggleX, sbToggleY, 14, 14)) {
                    ClientUiSettings.setSidebarOpen(!ClientUiSettings.sidebarOpen());
                    return true;
                }
                if (sbBackX >= 0 && hover(mx, my, sbBackX, sbBarY + 2, 14, 13)) {
                    if (!StudioSession.dirBack.isEmpty()) {
                        StudioSession.dirForward.push(StudioSession.explorerDir == null ? "" : StudioSession.explorerDir);
                        navigateDir(StudioSession.dirBack.pop(), false);
                    }
                    return true;
                }
                if (sbFwdX >= 0 && hover(mx, my, sbFwdX, sbBarY + 2, 14, 13)) {
                    if (!StudioSession.dirForward.isEmpty()) {
                        StudioSession.dirBack.push(StudioSession.explorerDir == null ? "" : StudioSession.explorerDir);
                        navigateDir(StudioSession.dirForward.pop(), false);
                    }
                    return true;
                }
                if (sbNewX >= 0 && hover(mx, my, sbNewX, sbBarY + 2, 14, 13)) {
                    naming = true;
                    namingMode = "file";
                    nameField.setValue("");
                    return true;
                }
                if (sbMkdirX >= 0 && hover(mx, my, sbMkdirX, sbBarY + 2, 14, 13)) {
                    naming = true;
                    namingMode = "mkdir";
                    nameField.setValue("");
                    return true;
                }
                if (sbRenX >= 0 && hover(mx, my, sbRenX, sbBarY + 2, 14, 13) && StudioSession.selectedExplorerPath != null) {
                    startRenameSelected();
                    return true;
                }
                if (sbDelX >= 0 && hover(mx, my, sbDelX, sbBarY + 2, 14, 13) && StudioSession.selectedExplorerPath != null) {
                    ask = "delete_exp";
                    askName = StudioSession.selectedExplorerPath;
                    return true;
                }
                for (SidebarHit hit : sidebarHits) {
                    if (my >= hit.y && my < hit.y + hit.h) {
                        if (hit.parent) {
                            navigateDir(hit.fullPath, true);
                            return true;
                        }
                        if (hit.toggleX >= 0 && mx >= hit.toggleX && mx < hit.toggleX + 12) {
                            ModNetwork.sendToServer(new StudioExplorerPacket.Action(StudioExplorerPacket.Kind.TOGGLE_SCRIPT, hit.fullPath, ""));
                            return true;
                        }
                        StudioSession.selectedExplorerPath = hit.fullPath;
                        if (hit.dir) {
                            navigateDir(hit.fullPath, true);
                        } else {
                            openExplorerFile(hit.fullPath);
                        }
                        return true;
                    }
                }
                return true;
            }
            explorerFocused = false;
        }
        if (StudioSession.JOURNAL.equals(StudioSession.active)) {
            journalClick(mx, my);
            return true;
        }
        if (doc != null && doc.loaded && hover(mx, my, editorX, editorY, editorW, editorH)) {
            if (StudioSession.guiEditorMode && !doc.genericFile && !doc.isImage) {
                StudioGuiEditor.mouseClicked(this.font, doc, mx, my, button);
                return true;
            }
            panelFocus = 0;
            if (button == 1) {
                ctxOpen = true;
                ctxX = mx;
                ctxY = my;
                ctxOpenedAt = System.currentTimeMillis();
                return true;
            }
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
        if (naming && nameField.mouseDragged(this.font, mx)) return true;
        if (gotoOpen && gotoField.mouseDragged(this.font, mx)) return true;
        if (StudioSession.toolPanelOpen && panelFocus == 1 && findField.mouseDragged(this.font, mx)) return true;
        if (StudioSession.toolPanelOpen && panelFocus == 2 && replaceField.mouseDragged(this.font, mx)) return true;
        if (StudioSession.ANIMATOR.equals(StudioSession.active) && !settings && drag == Drag.NONE) {
            if (StudioAnimEditor.mouseDragged(this.font, mx, my, button)) {
                return true;
            }
        }
        if (StudioSession.guiEditorMode && script() != null && !script().genericFile && !script().isImage && !settings) {
            if (StudioGuiEditor.mouseDragged(this.font, mx, my, button)) {
                return true;
            }
        }
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
        if (drag == Drag.SIDEBAR_RESIZE) {
            int delta = mx - grabX;
            int nextW = ClientUiSettings.sidebarRight() ? originW - delta : originW + delta;
            ClientUiSettings.setSidebarWidth(nextW);
            return true;
        }
        if (drag == Drag.PANEL_MOVE) {
            StudioSession.toolPanelX = mx - grabX;
            StudioSession.toolPanelY = my - grabY;
            StudioSession.toolPanelPlaced = true;
            layoutToolPanel();
            return true;
        }
        if (drag == Drag.PANEL_N || drag == Drag.PANEL_S || drag == Drag.PANEL_E || drag == Drag.PANEL_W
                || drag == Drag.PANEL_NE || drag == Drag.PANEL_NW || drag == Drag.PANEL_SE || drag == Drag.PANEL_SW) {
            int ddx = mx - grabX;
            int ddy = my - grabY;
            int x = originX;
            int y = originY;
            int w = originW;
            int h = originH;
            if (drag == Drag.PANEL_E || drag == Drag.PANEL_NE || drag == Drag.PANEL_SE) w = originW + ddx;
            if (drag == Drag.PANEL_S || drag == Drag.PANEL_SE || drag == Drag.PANEL_SW) h = originH + ddy;
            if (drag == Drag.PANEL_W || drag == Drag.PANEL_NW || drag == Drag.PANEL_SW) {
                x = originX + ddx;
                w = originW - ddx;
            }
            if (drag == Drag.PANEL_N || drag == Drag.PANEL_NE || drag == Drag.PANEL_NW) {
                y = originY + ddy;
                h = originH - ddy;
            }
            StudioSession.toolPanelX = x;
            StudioSession.toolPanelY = y;
            StudioSession.toolPanelW = w;
            StudioSession.toolPanelH = h;
            StudioSession.toolPanelPlaced = true;
            layoutToolPanel();
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
        nameField.mouseReleased();
        gotoField.mouseReleased();
        findField.mouseReleased();
        replaceField.mouseReleased();
        StudioAnimEditor.mouseReleased();
        StudioGuiEditor.mouseReleased(script());
        drag = Drag.NONE;
        slider = 0;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (StudioSession.ANIMATOR.equals(StudioSession.active) && !settings) {
            if (StudioAnimEditor.mouseScrolled((int) mouseX, (int) mouseY, delta)) {
                return true;
            }
        }
        if (StudioSession.guiEditorMode && script() != null && !script().genericFile && !script().isImage && !settings) {
            if (StudioGuiEditor.mouseScrolled((int) mouseX, (int) mouseY, delta)) {
                return true;
            }
        }
        int step = (int) Math.signum(delta) * -3;
        if (settings) {
            hotkeyScroll = Math.max(0, Math.min(hotkeyMaxScroll, hotkeyScroll + step * 12));
            return true;
        }
        if (StudioSession.historyOpen) {
            if (mouseX < histSplitX) {
                histListScroll = Math.max(0, Math.min(histListMax, histListScroll + step * 12));
            } else {
                histPreviewScroll = Math.max(0, Math.min(histPreviewMax, histPreviewScroll + step * 3));
            }
            return true;
        }
        if (StudioSession.PROFILER.equals(StudioSession.active) && ClientUiSettings.showProfiler()) {
            profScroll = Math.max(0, Math.min(profMaxScroll, profScroll + step * 14));
            return true;
        }
        ScriptDocument doc = script();
        if (!settings && (doc != null || StudioSession.HOME.equals(StudioSession.active) || StudioSession.JOURNAL.equals(StudioSession.active))
                && hover((int) mouseX, (int) mouseY, sbX, sbY, sbW, sbH)) {
            sidebarScroll = Math.max(0, Math.min(sidebarMaxScroll, sidebarScroll + step * 10));
            return true;
        }
        if (doc != null && !settings) {
            if (hasShiftDown()) {
                doc.horiz = Math.max(0, Math.min(horizMax, doc.horiz + step * 12));
            } else {
                doc.scroll = Math.max(0, Math.min(scrollMax, doc.scroll + step));
            }
            doc.followCaret = false;
        } else if (StudioSession.JOURNAL.equals(StudioSession.active)) {
            if (mouseX < journalListX + journalListW) {
                listScroll = Math.max(0, Math.min(listMax, listScroll + step * 8));
            } else {
                codeScroll = Math.max(0, Math.min(codeMax, codeScroll + step * LINE_H));
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (listeningBind != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                listeningBind = null;
                return true;
            }
            if (key != GLFW.GLFW_KEY_LEFT_CONTROL && key != GLFW.GLFW_KEY_RIGHT_CONTROL
                    && key != GLFW.GLFW_KEY_LEFT_SHIFT && key != GLFW.GLFW_KEY_RIGHT_SHIFT
                    && key != GLFW.GLFW_KEY_LEFT_ALT && key != GLFW.GLFW_KEY_RIGHT_ALT) {
                ClientUiSettings.setKeyBind(listeningBind,
                        new ClientUiSettings.KeyBind(key, hasControlDown(), hasShiftDown(), hasAltDown()));
                listeningBind = null;
            }
            return true;
        }
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
        if (gotoOpen) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                gotoOpen = false;
                gotoField.setValue("");
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                ScriptDocument d = script();
                if (d != null && !gotoField.value().isEmpty()) {
                    try {
                        int target = Math.max(1, Integer.parseInt(gotoField.value())) - 1;
                        d.move(target, 0, false);
                    } catch (NumberFormatException ignored) {
                    }
                }
                gotoOpen = false;
                gotoField.setValue("");
            } else {
                gotoField.keyPressed(key);
            }
            return true;
        }
        if (naming) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                naming = false;
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                createScript();
            } else {
                nameField.keyPressed(key);
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
        if (StudioSession.ANIMATOR.equals(StudioSession.active) && !settings && menu == null && !catalog) {
            if (StudioAnimEditor.keyPressed(key)) {
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (acMatch != null) {
                acMatch = null;
                return true;
            }
            if (menu != null || catalog || ctxOpen || StudioSession.toolPanelOpen) {
                menu = null;
                catalog = false;
                ctxOpen = false;
                StudioSession.toolPanelOpen = false;
                panelFocus = 0;
                return true;
            }
            onClose();
            return true;
        }
        boolean ctrl = hasControlDown();
        boolean shift = hasShiftDown();
        boolean alt = hasAltDown();
        ScriptDocument doc = script();
        if (ClientUiSettings.matches("saveAll", key, ctrl, shift, alt)) {
            saveAll();
            return true;
        }
        if (ClientUiSettings.matches("save", key, ctrl, shift, alt)) {
            save(doc);
            return true;
        }
        if (ClientUiSettings.matches("reload", key, ctrl, shift, alt)) {
            reload(doc);
            return true;
        }
        if (ClientUiSettings.matches("find", key, ctrl, shift, alt) && doc != null && !settings) {
            openToolPanel(1);
            return true;
        }
        if (ClientUiSettings.matches("replace", key, ctrl, shift, alt) && doc != null && !settings) {
            openToolPanel(2);
            return true;
        }
        if (ClientUiSettings.matches("toggleSidebar", key, ctrl, shift, alt) && !settings
                && (doc != null || StudioSession.HOME.equals(StudioSession.active))) {
            ClientUiSettings.setSidebarOpen(!ClientUiSettings.sidebarOpen());
            return true;
        }
        if (StudioSession.toolPanelOpen && panelFocus != 0 && doc != null && !settings) {
            if (key == GLFW.GLFW_KEY_TAB) {
                panelFocus = panelFocus == 1 ? 2 : 1;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (panelFocus == 2) {
                    replaceOne(doc);
                } else if (hasShiftDown()) {
                    findPrev(doc);
                } else {
                    findNext(doc);
                }
                return true;
            }
            if (panelFocus == 1) {
                findField.keyPressed(key);
            } else {
                replaceField.keyPressed(key);
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
        if (explorerFocused && StudioSession.selectedExplorerPath != null && !settings) {
            if (key == GLFW.GLFW_KEY_DELETE) {
                ask = "delete_exp";
                askName = StudioSession.selectedExplorerPath;
                return true;
            }
            if (key == GLFW.GLFW_KEY_F2) {
                startRenameSelected();
                return true;
            }
        }
        if (doc == null || !doc.loaded || doc.isImage || settings) {
            return true;
        }
        if (StudioSession.guiEditorMode && !doc.genericFile) {
            if (StudioGuiEditor.keyPressed(doc, key)) {
                return true;
            }
        }
        if (acMatch != null && !acMatch.items().isEmpty()) {
            if (key == GLFW.GLFW_KEY_DOWN) {
                acSel = (acSel + 1) % acMatch.items().size();
                return true;
            }
            if (key == GLFW.GLFW_KEY_UP) {
                acSel = (acSel - 1 + acMatch.items().size()) % acMatch.items().size();
                return true;
            }
            if (key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                applyAutocomplete(doc);
                return true;
            }
        }
        if (ClientUiSettings.matches("autocomplete", key, ctrl, shift, alt)) {
            refreshAutocomplete(doc, true);
            return true;
        }
        if (ClientUiSettings.matches("undo", key, ctrl, shift, alt)) {
            doc.undo();
            acMatch = null;
            return true;
        }
        if (ClientUiSettings.matches("redo", key, ctrl, shift, alt)) {
            doc.redo();
            acMatch = null;
            return true;
        }
        if (ClientUiSettings.matches("comment", key, ctrl, shift, alt)) {
            doc.toggleComment();
            acMatch = null;
            return true;
        }
        if (ClientUiSettings.matches("duplicate", key, ctrl, shift, alt)) {
            doc.duplicateLine();
            acMatch = null;
            return true;
        }
        if (ClientUiSettings.matches("gotoLine", key, ctrl, shift, alt)) {
            gotoOpen = true;
            gotoField.setValue("");
            acMatch = null;
            return true;
        }
        if (ClientUiSettings.matches("moveUp", key, ctrl, shift, alt)) {
            doc.moveLines(-1);
            acMatch = null;
            return true;
        }
        if (ClientUiSettings.matches("moveDown", key, ctrl, shift, alt)) {
            doc.moveLines(1);
            acMatch = null;
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_A) {
            doc.selectAll();
            acMatch = null;
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_C) {
            copy(doc);
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_X) {
            copy(doc);
            doc.backspace();
            acMatch = null;
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            doc.insert(clipboard());
            acMatch = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (ctrl) {
                doc.deleteWord();
            } else if (!ClientUiSettings.autoClose() || !doc.backspaceEmptyPair()) {
                doc.backspace();
            }
            refreshAutocomplete(doc, false);
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            if (ctrl) {
                doc.deleteWordForward();
            } else {
                doc.deleteForward();
            }
            acMatch = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            doc.insertNewLine(StudioSession.optAutoIndent);
            acMatch = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            if (!doc.indentSelection(shift)) {
                doc.insert("\t");
            }
            acMatch = null;
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
        if (listening || listeningBind != null) {
            return true;
        }
        if (gotoOpen) {
            gotoField.charTyped(ch);
            return true;
        }
        if (naming) {
            nameField.charTyped(ch);
            return true;
        }
        if (StudioSession.toolPanelOpen && panelFocus != 0) {
            if (panelFocus == 1) {
                findField.charTyped(ch);
            } else {
                replaceField.charTyped(ch);
            }
            return true;
        }
        if (StudioSession.ANIMATOR.equals(StudioSession.active) && !settings) {
            StudioAnimEditor.charTyped(ch);
            return true;
        }
        ScriptDocument doc = script();
        if (StudioSession.guiEditorMode && doc != null && !doc.genericFile && !doc.isImage && !settings) {
            if (StudioGuiEditor.charTyped(ch)) {
                return true;
            }
        }
        if (doc != null && doc.loaded && !settings && !hasControlDown() && ch >= 32) {
            if (!ClientUiSettings.autoClose() || !doc.typeWithAutoClose(ch)) {
                doc.insert(String.valueOf(ch));
            }
            refreshAutocomplete(doc, false);
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
        if (StudioSession.HOME.equals(StudioSession.active)
                || StudioSession.JOURNAL.equals(StudioSession.active)
                || StudioSession.ANIMATOR.equals(StudioSession.active)
                || StudioSession.PROFILER.equals(StudioSession.active)) {
            return null;
        }
        return StudioSession.docs.get(StudioSession.active);
    }

    private void openScript(String name) {
        if (StudioSession.ANIMATOR.equals(StudioSession.active)) {
            StudioAnimEditor.onLeaveTab();
        }
        ScriptDocument doc = StudioSession.openScript(name);
        catalog = false;
        settings = false;
        if (!doc.loaded) {
            ModNetwork.sendToServer(new ScriptDataPacket.Read(name));
        }
    }

    private void openExplorerFile(String relPath) {
        if (relPath.startsWith("scripts/") && relPath.endsWith(".sc")) {
            openScript(relPath.substring("scripts/".length()));
            return;
        }
        if (relPath.startsWith("resources/assets/") && relPath.toLowerCase(Locale.ROOT).endsWith(".json")
                && (relPath.contains("/player_animation/") || relPath.contains("/player_animations/"))) {
            catalog = false;
            settings = false;
            StudioSession.active = StudioSession.ANIMATOR;
            ModNetwork.sendToServer(new StudioExplorerPacket.Action(StudioExplorerPacket.Kind.READ_FILE, relPath, "$anim_open"));
            return;
        }
        ScriptDocument doc = StudioSession.openGenericFile(relPath);
        catalog = false;
        settings = false;
        if (!doc.loaded) {
            ModNetwork.sendToServer(new StudioExplorerPacket.Action(StudioExplorerPacket.Kind.READ_FILE, relPath, ""));
        }
    }

    private void startRenameSelected() {
        String sel = StudioSession.selectedExplorerPath;
        if (sel == null || sel.isEmpty()) return;
        int slash = sel.lastIndexOf('/');
        renameTarget = sel;
        nameField.setValue(slash < 0 ? sel : sel.substring(slash + 1));
        nameField.selectAll();
        namingMode = "rename";
        naming = true;
    }

    private void save(ScriptDocument doc) {
        if (doc == null || !doc.loaded || doc.isImage) {
            return;
        }
        if (StudioSession.guiEditorMode && !doc.genericFile && StudioGuiEditor.isDirty()) {
            StudioGuiEditor.applyToDocument(doc);
        }
        if (doc.genericFile) {
            String rel = doc.name.startsWith("@") ? doc.name.substring(1) : doc.name;
            byte[] bytes = doc.text().getBytes(StandardCharsets.UTF_8);
            ModNetwork.sendToServer(new StudioExplorerPacket.Action(StudioExplorerPacket.Kind.WRITE_FILE, rel, "", bytes));
            doc.saved = doc.text();
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
        String raw = nameField.value().strip();
        naming = false;
        nameField.setValue("");
        if (raw.isEmpty()) {
            return;
        }
        String dir = StudioSession.explorerDir == null ? "" : StudioSession.explorerDir;
        if ("mkdir".equals(namingMode)) {
            String rel = dir.isEmpty() ? raw : dir + "/" + raw;
            ModNetwork.sendToServer(new StudioExplorerPacket.Action(StudioExplorerPacket.Kind.MKDIR, rel, ""));
            return;
        }
        if ("rename".equals(namingMode)) {
            String parent = parentOf(renameTarget);
            String dst = parent.isEmpty() ? raw : parent + "/" + raw;
            ModNetwork.sendToServer(new StudioExplorerPacket.Action(StudioExplorerPacket.Kind.RENAME, renameTarget, dst));
            StudioSession.selectedExplorerPath = dst;
            return;
        }
        if (dir.equals("scripts") || dir.startsWith("scripts/")) {
            String file = raw.endsWith(".sc") ? raw : raw + ".sc";
            String sub = dir.equals("scripts") ? "" : dir.substring("scripts/".length()) + "/";
            String name = sub + file;
            String body = "# \n\non load:\n\t\n";
            ScriptDocument doc = StudioSession.openScript(name);
            doc.load(body);
            ModNetwork.sendToServer(new ScriptDataPacket.Save(name, body, doc.revision));
            return;
        }
        String rel = dir.isEmpty() ? raw : dir + "/" + raw;
        ModNetwork.sendToServer(new StudioExplorerPacket.Action(StudioExplorerPacket.Kind.WRITE_FILE, rel, "", new byte[0]));
        openExplorerFile(rel);
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
        String find = findField.value();
        if (doc == null || find.isEmpty()) {
            return;
        }
        List<int[]> matches = doc.findAllMatches(find, StudioSession.optMatchCase, StudioSession.optWholeWord);
        if (matches.isEmpty()) {
            ClientToasts.push(Messages.toast("studio.none"));
            return;
        }
        int[] target = matches.get(0);
        for (int[] m : matches) {
            if (m[0] > doc.line || (m[0] == doc.line && m[1] >= doc.col)) {
                target = m;
                break;
            }
        }
        doc.move(target[0], target[1], false);
        doc.move(target[0], target[2], true);
    }

    private void findPrev(ScriptDocument doc) {
        String find = findField.value();
        if (doc == null || find.isEmpty()) {
            return;
        }
        List<int[]> matches = doc.findAllMatches(find, StudioSession.optMatchCase, StudioSession.optWholeWord);
        if (matches.isEmpty()) {
            ClientToasts.push(Messages.toast("studio.none"));
            return;
        }
        int curLine = doc.anchorLine >= 0 ? Math.min(doc.anchorLine, doc.line) : doc.line;
        int curCol = doc.anchorLine >= 0 && doc.anchorLine == doc.line ? Math.min(doc.anchorCol, doc.col) : doc.col;
        int[] target = matches.get(matches.size() - 1);
        for (int i = matches.size() - 1; i >= 0; i--) {
            int[] m = matches.get(i);
            if (m[0] < curLine || (m[0] == curLine && m[1] < curCol)) {
                target = m;
                break;
            }
        }
        doc.move(target[0], target[1], false);
        doc.move(target[0], target[2], true);
    }

    private void replaceOne(ScriptDocument doc) {
        String find = findField.value();
        String replace = replaceField.value();
        if (doc == null || find.isEmpty()) {
            return;
        }
        if (doc.replaceCurrent(find, replace, StudioSession.optMatchCase, StudioSession.optWholeWord)) {
            findNext(doc);
            return;
        }
        findNext(doc);
        doc.replaceCurrent(find, replace, StudioSession.optMatchCase, StudioSession.optWholeWord);
    }

    private void replaceAll(ScriptDocument doc) {
        String find = findField.value();
        String replace = replaceField.value();
        if (doc == null || find.isEmpty()) {
            return;
        }
        int count = doc.replaceAll(find, replace, StudioSession.optMatchCase, StudioSession.optWholeWord);
        if (count == 0) {
            ClientToasts.push(Messages.toast("studio.none"));
        } else {
            ClientToasts.push(Messages.toast("studio.replaced", count));
        }
    }

    private void placeCursor(ScriptDocument doc, int mx, int my, boolean keep) {
        int rel = my - editorY - 2;
        int row = doc.scroll + (rel <= 0 ? 0 : rel / LINE_H);
        row = Math.max(0, Math.min(doc.lines.size() - 1, row));
        String line = doc.lines.get(row);
        int local = mx - editorX - gutterW() + doc.horiz;
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

    public static int peerColor(StudioLivePacket.Peer peer) {
        if (peer != null && peer.color() != 0) {
            return 0xFF000000 | peer.color();
        }
        return peerColor(peer == null ? "" : peer.name());
    }

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
        return editorX + gutterW() + spanWidth(line, col) - horiz;
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
        String[] ids = {"new", "open", "save", "saveall", "closetab", "delete", "", "cut", "copy", "paste", "", "undo", "redo", "", "find"};
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
                namingMode = "file";
                nameField.setValue("");
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
                if (StudioSession.guiEditorMode && doc != null && !doc.genericFile && !doc.isImage) {
                    StudioGuiEditor.undo(doc);
                } else if (doc != null) {
                    doc.undo();
                }
            }
            case "redo" -> {
                if (StudioSession.guiEditorMode && doc != null && !doc.genericFile && !doc.isImage) {
                    StudioGuiEditor.redo(doc);
                } else if (doc != null) {
                    doc.redo();
                }
            }
            case "find" -> {
                if (StudioSession.toolPanelOpen) {
                    StudioSession.toolPanelOpen = false;
                    panelFocus = 0;
                } else {
                    openToolPanel(1);
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
                    Messages.get("studio.m.copy"), Messages.get("studio.m.paste"), Messages.get("studio.m.all"),
                    Messages.get("studio.m.spaces_to_tabs"), Messages.get("studio.m.tabs_to_spaces"));
            case "search" -> List.of(Messages.get("studio.find_replace"));
            case "view" -> List.of(StudioSession.maximized ? Messages.get("studio.m.window") : Messages.get("studio.m.full"),
                    Messages.get("studio.journal"), Messages.get("studio.animator"),
                    Messages.get("studio.m.tools"), Messages.get("studio.m.gui_editor"));
            case "reload" -> List.of(
                    Messages.get("studio.m.reload_scripts"),
                    Messages.get("studio.m.reload_resources"),
                    Messages.get("studio.m.reload_config"));
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
                    namingMode = "file";
                    nameField.setValue("");
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
                ScriptDocument doc = script();
                if (index == 5) {
                    if (doc != null) doc.selectAll();
                } else if (index == 6) {
                    if (doc != null) ClientToasts.push(Messages.toast("studio.converted_indent", doc.spacesToTabs()));
                } else if (index == 7) {
                    if (doc != null) ClientToasts.push(Messages.toast("studio.converted_indent", doc.tabsToSpaces()));
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
                if (script() != null) {
                    openToolPanel(1);
                }
            }
            case "view" -> {
                if (index == 0) {
                    toggleMax();
                } else if (index == 1) {
                    if (StudioSession.ANIMATOR.equals(StudioSession.active)) StudioAnimEditor.onLeaveTab();
                    StudioSession.active = StudioSession.JOURNAL;
                } else if (index == 2) {
                    StudioSession.active = StudioSession.ANIMATOR;
                    StudioAnimEditor.requestServerFiles();
                } else if (index == 3 && script() != null) {
                    if (StudioSession.toolPanelOpen) {
                        StudioSession.toolPanelOpen = false;
                        panelFocus = 0;
                    } else {
                        openToolPanel(0);
                    }
                } else if (index == 4 && script() != null && !script().genericFile && !script().isImage) {
                    StudioSession.guiEditorMode = !StudioSession.guiEditorMode;
                    if (StudioSession.guiEditorMode) {
                        StudioGuiEditor.reloadFromDocument(script());
                    }
                }
            }
            case "reload" -> {
                if (index == 0) {
                    ModNetwork.sendToServer(new ScriptReloadPacket("$scripts"));
                } else if (index == 1) {
                    StudioSession.resourcesDirty = false;
                    ModNetwork.sendToServer(new ScriptReloadPacket("$resources"));
                } else if (index == 2) {
                    ModNetwork.sendToServer(new ScriptReloadPacket("$config"));
                }
            }
            default -> {
            }
        }
    }

    private int menuX() {
        int x = px + 6;
        String[] ids = {"file", "edit", "search", "view", "reload"};
        String[] labels = {Messages.get("studio.m.file"), Messages.get("studio.m.edit"), Messages.get("studio.m.search"),
                Messages.get("studio.m.view"), Messages.get("studio.m.reload")};
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
        String[] ids = {"file", "edit", "search", "view", "reload"};
        String[] labels = {Messages.get("studio.m.file"), Messages.get("studio.m.edit"), Messages.get("studio.m.search"),
                Messages.get("studio.m.view"), Messages.get("studio.m.reload")};
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
        String animLabel = Messages.get("studio.animator");
        if (hitTab(mx, x, animLabel, false) != null) {
            return StudioSession.ANIMATOR;
        }
        x += tabWidth(animLabel, false, false);
        if (ClientUiSettings.showProfiler()) {
            String profLabel = Messages.get("studio.profiler");
            if (hitTab(mx, x, profLabel, false) != null) {
                return StudioSession.PROFILER;
            }
            x += tabWidth(profLabel, false, false);
        }
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
        if (openEdX >= 0 && openEdFile != null && !openEdFile.isEmpty()
                && hover(mx, my, openEdX, copyBtnY, openEdW, 12)) {
            openScriptAtLine(openEdFile, openEdLine);
            return;
        }
        if (journalTextX >= 0 && mx >= journalTextX && mx < journalTextX + journalTextW
                && my >= journalViewTop - 2 && my < journalY + journalH) {
            placeJournalCaret(mx, my, false);
            drag = Drag.JOURNAL;
            return;
        }
        if (mx < journalListX || mx >= journalListX + journalListW || my < journalY || my >= journalY + journalH) {
            return;
        }
        clearJournalSelection();
        long now = System.currentTimeMillis();
        for (JournalHit hit : journalHits) {
            if (my < hit.y || my >= hit.y + hit.h) {
                continue;
            }
            if (hit.kind == 0) {
                boolean dbl = lastJournalClickIdx == hit.index && (now - lastJournalClickAt) < 380L;
                lastJournalClickIdx = hit.index;
                lastJournalClickAt = now;
                historySel = -1;
                journal = hit.index;
                ScriptLog.Issue issue = StudioSession.issues.get(hit.index);
                if (dbl && !issue.file().isEmpty()) {
                    openScriptAtLine(issue.file(), issue.line());
                    return;
                }
                List<String> live = issue.file().isEmpty() ? null : StudioSession.liveLines(issue.file());
                int first = live != null && !live.isEmpty() ? 1 : (slice(issue.file()) == null ? 1 : slice(issue.file()).firstLine());
                if (issue.line() > 0) {
                    codeScroll = Math.max(0, (issue.line() - first) * LINE_H - 20);
                }
            } else {
                lastJournalClickIdx = -1;
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

    private void openScriptAtLine(String file, int line1Based) {
        if (file == null || file.isEmpty()) {
            return;
        }
        openScript(file);
        ScriptDocument doc = StudioSession.docs.get(file);
        if (doc != null && line1Based > 0) {
            doc.move(Math.max(0, line1Based - 1), 0, false);
            doc.followCaret = true;
        }
    }

    private void settingsClick(int mx, int my) {
        listeningBind = null;
        if (hover(mx, my, keyBoxX, keyBoxY, 170, 14)) {
            listening = true;
            return;
        }
        listening = false;
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
        int y = slider2Y + 12 + 12;
        clickPlace(mx, my, sliderX, y, ClientUiSettings.Place.CHAT);
        clickPlace(mx, my, sliderX + 116, y, ClientUiSettings.Place.TOP_LEFT);
        clickPlace(mx, my, sliderX, y + 18, ClientUiSettings.Place.TOP_RIGHT);
        clickPlace(mx, my, sliderX + 116, y + 18, ClientUiSettings.Place.BOTTOM_RIGHT);

        if (hover(mx, my, setAcX, setAcY, 220, 14)) {
            ClientUiSettings.setAutocomplete(!ClientUiSettings.autocomplete());
            return;
        }
        if (hover(mx, my, setAutoX, setAutoY, 220, 14)) {
            ClientUiSettings.setAutoClose(!ClientUiSettings.autoClose());
            return;
        }
        if (hover(mx, my, setSbLeftX, setSbY, 58, 15)) {
            ClientUiSettings.setSidebarRight(false);
            return;
        }
        if (hover(mx, my, setSbRightX, setSbY, 58, 15)) {
            ClientUiSettings.setSidebarRight(true);
            return;
        }
        if (hover(mx, my, setLangRuX, setLangY, 58, 15)) {
            ClientUiSettings.setLanguage("ru");
            return;
        }
        if (hover(mx, my, setLangEnX, setLangY, 58, 15)) {
            ClientUiSettings.setLanguage("en");
            return;
        }
        if (hover(mx, my, setProfX, setProfY, 220, 14)) {
            boolean next = !ClientUiSettings.showProfiler();
            ClientUiSettings.setShowProfiler(next);
            if (!next && StudioSession.PROFILER.equals(StudioSession.active)) {
                StudioSession.active = StudioSession.HOME;
            }
            return;
        }
        int[] pal = ClientUiSettings.EDITOR_COLORS;
        for (int i = 0; i < pal.length; i++) {
            int sx = setColorX + i * 18;
            if (hover(mx, my, sx, setColorY, 15, 14)) {
                ClientUiSettings.setEditorColor(pal[i]);
                return;
            }
        }
        if (hover(mx, my, hkResetX, hkResetY, hkResetW, 14)) {
            ClientUiSettings.resetKeyBinds();
            return;
        }
        for (HotkeyHit hit : hotkeyHits) {
            if (hover(mx, my, hit.x, hit.y, hit.w, hit.h)) {
                listeningBind = hit.id;
                return;
            }
        }
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
        String clean = name.startsWith("@") ? name.substring(1) : name;
        int slash = clean.lastIndexOf('/');
        String file = slash < 0 ? clean : clean.substring(slash + 1);
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
        } else if ("delete_exp".equals(ask)) {
            ModNetwork.sendToServer(new StudioExplorerPacket.Action(StudioExplorerPacket.Kind.DELETE, askName, ""));
            StudioSession.selectedExplorerPath = null;
        } else if ("close".equals(ask)) {
            dropTab(askName);
        }
        ask = null;
    }

    private void dropTab(String name) {
        StudioSync.leaveFile(name);
        StudioSession.docs.remove(name);
        if (name.equals(StudioSession.lastScriptTab)) {
            StudioSession.lastScriptTab = StudioSession.docs.keySet().stream().findFirst().orElse(null);
        }
        if (name.equals(StudioSession.active)) {
            StudioSession.active = StudioSession.HOME;
        }
    }

    private void renderAsk(GuiGraphics g, int mx, int my) {
        String key = "delete".equals(ask) ? "studio.ask.delete"
                : ("delete_exp".equals(ask) ? "studio.exp.ask_delete" : "studio.ask.close");
        String text = Messages.get(key, askName);
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
        return ScriptColors.width(this.font, line, count, StudioSession.optQuoteColors);
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

    /** Пиксельная шестерёнка 10×10 без искажений от поворота на 45°. */
    private static void drawGear(GuiGraphics g, int x, int y, int color, int holeBg) {
        // Кольцо
        g.fill(x + 2, y + 2, x + 8, y + 8, color);
        // Кардинальные зубцы
        g.fill(x + 4, y, x + 6, y + 2, color);
        g.fill(x + 4, y + 8, x + 6, y + 10, color);
        g.fill(x, y + 4, x + 2, y + 6, color);
        g.fill(x + 8, y + 4, x + 10, y + 6, color);
        // Диагональные зубцы
        g.fill(x + 1, y + 1, x + 3, y + 3, color);
        g.fill(x + 7, y + 1, x + 9, y + 3, color);
        g.fill(x + 1, y + 7, x + 3, y + 9, color);
        g.fill(x + 7, y + 7, x + 9, y + 9, color);
        // Внутреннее отверстие
        g.fill(x + 4, y + 4, x + 6, y + 6, holeBg);
    }

    // ------------------------------------------------------------------ Профайлер и история версий

    private int setProfX, setProfY;
    private int histBtnX = -1000, histBtnY, histBtnW;
    private int guiBtnX = -1000, guiBtnY, guiBtnW;
    private long lastProfFetchMs;
    private int profScroll, profMaxScroll;
    private int profToggleX, profToggleY, profToggleW;
    private int profResetX, profResetY, profResetW;
    private final List<ProfRowHit> profHits = new ArrayList<>();
    private final int[] profSortX = new int[4];
    private int profSortY;

    private record ProfRowHit(int x, int y, int w, int h, String script) {
    }

    private int histModalX, histModalY, histModalW, histModalH, histSplitX;
    private int histCloseX, histCloseY;
    private int histRestoreX, histRestoreY, histRestoreW;
    private int histDeleteX, histDeleteY, histDeleteW;
    private int histListScroll, histListMax, histPreviewScroll, histPreviewMax;
    private final List<HistEntryHit> histHits = new ArrayList<>();

    private record HistEntryHit(int x, int y, int w, int h, String id) {
    }

    private void renderProfiler(GuiGraphics g, int mx, int my) {
        long now = System.currentTimeMillis();
        if (now - lastProfFetchMs >= 1000L && this.minecraft != null && this.minecraft.getConnection() != null) {
            lastProfFetchMs = now;
            ModNetwork.sendToServer(new com.scriptcraft.network.ProfilerPacket.Request(com.scriptcraft.network.ProfilerPacket.FETCH));
        }
        int x0 = px + 10;
        int y0 = py + TITLE + MENU_H + 8;
        int w = pw - 20;
        int h = ph - TITLE - MENU_H - 16;

        com.scriptcraft.network.ProfilerPacket.Data data = StudioSession.profilerData;
        boolean srvOn = data == null || data.enabled();

        // Верхняя панель управления профайлером
        g.fill(x0, y0, x0 + w, y0 + 24, SURFACE);
        border(g, x0, y0, w, 24, EDGE);

        String togLbl = Messages.get(srvOn ? "studio.profiler.on" : "studio.profiler.off");
        profToggleW = this.font.width(togLbl) + 14;
        profToggleX = x0 + 6;
        profToggleY = y0 + 4;
        g.fill(profToggleX, profToggleY, profToggleX + profToggleW, profToggleY + 16, srvOn ? 0xFF1F382E : 0xFF3A1E24);
        border(g, profToggleX, profToggleY, profToggleW, 16, srvOn ? GREEN : 0xFFEF4444);
        g.drawString(this.font, togLbl, profToggleX + 7, profToggleY + 4, srvOn ? GREEN : 0xFFFCA5A5, false);

        String rstLbl = Messages.get("studio.profiler.reset");
        profResetW = this.font.width(rstLbl) + 12;
        profResetX = profToggleX + profToggleW + 6;
        profResetY = y0 + 4;
        paintMiniBtn(g, mx, my, profResetX, profResetY, profResetW, 16, rstLbl, "prof:reset");

        if (data != null) {
            String summary = String.format(java.util.Locale.ROOT, "MSPT: %.2f ms  |  Scripts: %.3f ms/t  |  %d",
                    data.serverMspt(), data.scriptTickMs(), data.rows().size());
            g.drawString(this.font, summary, profResetX + profResetW + 12, y0 + 8, TEXT, false);
        }

        // Заголовки таблицы
        int tblY = y0 + 30;
        int tblH = Math.max(40, h - 30);
        g.fill(x0, tblY, x0 + w, tblY + tblH, 0xFF0E131D);
        border(g, x0, tblY, w, tblH, EDGE);

        int hdrY = tblY + 1;
        g.fill(x0 + 1, hdrY, x0 + w - 1, hdrY + 16, BAR);
        g.drawString(this.font, "Script / Section", x0 + 8, hdrY + 4, MUTED, false);

        profSortY = hdrY;
        int colCalls = x0 + w - 62;
        int colMax = colCalls - 64;
        int colAvg = colMax - 64;
        int colRecent = colAvg - 72;
        profSortX[0] = colRecent;
        profSortX[1] = colAvg;
        profSortX[2] = colMax;
        profSortX[3] = colCalls;
        String[] hdrs = {"ms/s", "avg ms", "max ms", "calls"};
        for (int i = 0; i < 4; i++) {
            boolean active = StudioSession.profilerSort == i;
            String lbl = active ? hdrs[i] + " v" : hdrs[i];
            g.drawString(this.font, lbl, profSortX[i], hdrY + 4, active ? GOLD : MUTED, false);
        }

        profHits.clear();
        int bodyY = hdrY + 17;
        int bodyH = Math.max(10, tblH - 18);
        if (data == null || data.rows().isEmpty()) {
            String msg = Messages.get(srvOn ? "studio.profiler.empty" : "studio.profiler.disabled");
            g.drawCenteredString(this.font, msg, x0 + w / 2, bodyY + bodyH / 2 - 4, MUTED);
            profMaxScroll = 0;
            profScroll = 0;
            return;
        }

        List<com.scriptcraft.network.ProfilerPacket.Row> rows = new ArrayList<>(data.rows());
        rows.sort((a, b) -> switch (StudioSession.profilerSort) {
            case 1 -> Float.compare(b.avgMs(), a.avgMs());
            case 2 -> Float.compare(b.maxMs(), a.maxMs());
            case 3 -> Long.compare(b.calls(), a.calls());
            default -> Float.compare(b.recentMsPerSec(), a.recentMsPerSec());
        });

        int rowH = 17;
        int totalH = rows.size() * rowH + 4;
        profMaxScroll = Math.max(0, totalH - bodyH);
        profScroll = Math.max(0, Math.min(profScroll, profMaxScroll));

        g.enableScissor(x0 + 1, bodyY, x0 + w - 1, bodyY + bodyH);
        int ry = bodyY + 2 - profScroll;
        for (var r : rows) {
            if (ry + rowH >= bodyY && ry <= bodyY + bodyH) {
                boolean hot = hover(mx, my, x0 + 2, ry, w - 4, rowH - 1);
                g.fill(x0 + 2, ry, x0 + w - 2, ry + rowH - 1, hot ? ROW_HOT : ROW);
                int barColor = r.recentMsPerSec() > 10f ? 0x60EF4444 : (r.recentMsPerSec() > 1f ? 0x55F59E0B : 0x4522C55E);
                float frac = Math.min(1f, r.recentMsPerSec() / 20f);
                int barW = Math.round((w - 4) * frac);
                if (barW > 0) {
                    g.fill(x0 + 2, ry, x0 + 2 + barW, ry + rowH - 1, barColor);
                }
                String name = r.script() + "  ::  " + r.section();
                g.drawString(this.font, trim(name, Math.max(40, colRecent - x0 - 16)), x0 + 8, ry + 4, TEXT, false);
                g.drawString(this.font, String.format(java.util.Locale.ROOT, "%.2f", r.recentMsPerSec()), colRecent, ry + 4,
                        r.recentMsPerSec() > 10f ? 0xFFFCA5A5 : (r.recentMsPerSec() > 1f ? GOLD : GREEN), false);
                g.drawString(this.font, String.format(java.util.Locale.ROOT, "%.3f", r.avgMs()), colAvg, ry + 4, TEXT, false);
                g.drawString(this.font, String.format(java.util.Locale.ROOT, "%.2f", r.maxMs()), colMax, ry + 4, MUTED, false);
                g.drawString(this.font, Long.toString(r.calls()), colCalls, ry + 4, MUTED, false);
                profHits.add(new ProfRowHit(x0 + 2, ry, w - 4, rowH - 1, r.script()));
            }
            ry += rowH;
        }
        g.disableScissor();
    }

    private void profilerClick(int mx, int my) {
        if (hover(mx, my, profToggleX, profToggleY, profToggleW, 16)) {
            ModNetwork.sendToServer(new com.scriptcraft.network.ProfilerPacket.Request(com.scriptcraft.network.ProfilerPacket.TOGGLE));
            return;
        }
        if (hover(mx, my, profResetX, profResetY, profResetW, 16)) {
            ModNetwork.sendToServer(new com.scriptcraft.network.ProfilerPacket.Request(com.scriptcraft.network.ProfilerPacket.RESET));
            return;
        }
        for (int i = 0; i < 4; i++) {
            if (hover(mx, my, profSortX[i] - 2, profSortY, 62, 16)) {
                StudioSession.profilerSort = i;
                return;
            }
        }
        for (ProfRowHit hit : profHits) {
            if (hover(mx, my, hit.x, hit.y, hit.w, hit.h)) {
                if (hit.script != null && !hit.script.isEmpty() && !hit.script.startsWith("<")) {
                    openScript(hit.script);
                }
                return;
            }
        }
    }

    private void renderHistoryModal(GuiGraphics g, int mx, int my) {
        g.fill(px, py, px + pw, py + ph, 0x99000000);
        histModalW = Math.min(pw - 32, Math.max(360, pw - 90));
        histModalH = Math.min(ph - 32, Math.max(220, ph - 70));
        histModalX = px + (pw - histModalW) / 2;
        histModalY = py + (ph - histModalH) / 2;
        g.fill(histModalX, histModalY, histModalX + histModalW, histModalY + histModalH, 0xFA131924);
        border(g, histModalX, histModalY, histModalW, histModalH, ACCENT);

        // Шапка модалки
        g.fill(histModalX + 1, histModalY + 1, histModalX + histModalW - 1, histModalY + 22, BAR);
        String title = StudioSession.historyRetentionDays > 0
                ? Messages.get("studio.history.title", StudioSession.historyScript, StudioSession.historyRetentionDays)
                : Messages.get("studio.history.title_forever", StudioSession.historyScript);
        g.drawString(this.font, trim(title, histModalW - 32), histModalX + 8, histModalY + 7, GOLD, false);
        histCloseX = histModalX + histModalW - 18;
        histCloseY = histModalY + 4;
        boolean closeHot = hover(mx, my, histCloseX, histCloseY, 14, 14);
        g.fill(histCloseX, histCloseY, histCloseX + 14, histCloseY + 14, closeHot ? 0xFF7F1D1D : ROW);
        g.drawCenteredString(this.font, "x", histCloseX + 7, histCloseY + 3, TEXT);

        int contentY = histModalY + 25;
        int contentH = histModalH - 54;
        int leftW = Math.min(200, Math.max(130, histModalW / 3));
        histSplitX = histModalX + leftW;

        // Левая колонка: список снимков
        g.fill(histModalX + 6, contentY, histSplitX - 4, contentY + contentH, 0xFF0E131D);
        border(g, histModalX + 6, contentY, leftW - 10, contentH, EDGE);
        histHits.clear();
        if (StudioSession.historyEntries.isEmpty()) {
            g.drawCenteredString(this.font, Messages.get("studio.history.empty"), histModalX + leftW / 2, contentY + contentH / 2 - 4, MUTED);
            histListMax = 0;
        } else {
            int rowH = 26;
            histListMax = Math.max(0, StudioSession.historyEntries.size() * rowH - contentH + 6);
            histListScroll = Math.max(0, Math.min(histListScroll, histListMax));
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm:ss");
            g.enableScissor(histModalX + 7, contentY + 1, histSplitX - 5, contentY + contentH - 1);
            int ry = contentY + 3 - histListScroll;
            for (var e : StudioSession.historyEntries) {
                if (ry + rowH >= contentY && ry <= contentY + contentH) {
                    boolean sel = e.id().equals(StudioSession.historySelectedId);
                    boolean hot = hover(mx, my, histModalX + 8, ry, leftW - 14, rowH - 2);
                    g.fill(histModalX + 8, ry, histSplitX - 6, ry + rowH - 2, sel ? 0xFF23354D : (hot ? ROW_HOT : ROW));
                    if (sel) {
                        g.fill(histModalX + 8, ry, histModalX + 10, ry + rowH - 2, ACCENT);
                    }
                    String ts = java.time.Instant.ofEpochMilli(e.timestampMs())
                            .atZone(java.time.ZoneId.systemDefault()).format(fmt);
                    g.drawString(this.font, ts, histModalX + 13, ry + 3, sel ? TEXT : MUTED, false);
                    String sub = e.author() + " • " + Messages.get("studio.history.lines", e.lines());
                    g.drawString(this.font, trim(sub, leftW - 22), histModalX + 13, ry + 13, 0xFF8B99B0, false);
                    histHits.add(new HistEntryHit(histModalX + 8, ry, leftW - 14, rowH - 2, e.id()));
                }
                ry += rowH;
            }
            g.disableScissor();
        }

        // Правая колонка: превью кода
        int rightX = histSplitX + 2;
        int rightW = histModalX + histModalW - rightX - 6;
        g.fill(rightX, contentY, rightX + rightW, contentY + contentH, 0xFF0B0F17);
        border(g, rightX, contentY, rightW, contentH, EDGE);
        String[] lines = StudioSession.historyPreviewText.isEmpty() ? new String[0] : StudioSession.historyPreviewText.split("\n", -1);
        int visibleLines = Math.max(1, (contentH - 8) / LINE_H);
        histPreviewMax = Math.max(0, lines.length - visibleLines);
        histPreviewScroll = Math.max(0, Math.min(histPreviewScroll, histPreviewMax));
        g.enableScissor(rightX + 2, contentY + 2, rightX + rightW - 2, contentY + contentH - 2);
        for (int i = histPreviewScroll; i < Math.min(lines.length, histPreviewScroll + visibleLines + 1); i++) {
            int ly = contentY + 4 + (i - histPreviewScroll) * LINE_H;
            g.drawString(this.font, String.valueOf(i + 1), rightX + 6, ly, 0xFF566275, false);
            g.drawString(this.font, trim(lines[i].replace("\t", "    "), rightW - 34), rightX + 28, ly, TEXT, false);
        }
        g.disableScissor();

        // Нижние кнопки: Восстановить / Удалить
        int footY = histModalY + histModalH - 23;
        String resLbl = Messages.get("studio.history.restore");
        histRestoreW = this.font.width(resLbl) + 16;
        histRestoreX = histModalX + histModalW - histRestoreW - 8;
        histRestoreY = footY;
        boolean hasSel = !StudioSession.historySelectedId.isEmpty();
        if (hasSel) {
            boolean rHot = hover(mx, my, histRestoreX, histRestoreY, histRestoreW, 17);
            g.fill(histRestoreX, histRestoreY, histRestoreX + histRestoreW, histRestoreY + 17, rHot ? 0xFF1E4D38 : 0xFF183B2C);
            border(g, histRestoreX, histRestoreY, histRestoreW, 17, GREEN);
            g.drawString(this.font, resLbl, histRestoreX + 8, histRestoreY + 5, GREEN, false);

            String delLbl = Messages.get("studio.history.delete");
            histDeleteW = this.font.width(delLbl) + 14;
            histDeleteX = histRestoreX - histDeleteW - 6;
            histDeleteY = footY;
            boolean dHot = hover(mx, my, histDeleteX, histDeleteY, histDeleteW, 17);
            g.fill(histDeleteX, histDeleteY, histDeleteX + histDeleteW, histDeleteY + 17, dHot ? 0xFF5C1E26 : 0xFF3A1A20);
            border(g, histDeleteX, histDeleteY, histDeleteW, 17, 0xFFEF4444);
            g.drawString(this.font, delLbl, histDeleteX + 7, histDeleteY + 5, 0xFFFCA5A5, false);
        } else {
            histRestoreX = -1000;
            histDeleteX = -1000;
        }
    }

    private void historyClick(int mx, int my) {
        if (!hover(mx, my, histModalX, histModalY, histModalW, histModalH)
                || hover(mx, my, histCloseX, histCloseY, 14, 14)) {
            StudioSession.historyOpen = false;
            return;
        }
        if (histRestoreX >= 0 && hover(mx, my, histRestoreX, histRestoreY, histRestoreW, 17)
                && !StudioSession.historySelectedId.isEmpty()) {
            ModNetwork.sendToServer(new com.scriptcraft.network.ScriptHistoryPacket.Request(
                    com.scriptcraft.network.ScriptHistoryPacket.RESTORE,
                    StudioSession.historyScript, StudioSession.historySelectedId));
            StudioSession.historyOpen = false;
            return;
        }
        if (histDeleteX >= 0 && hover(mx, my, histDeleteX, histDeleteY, histDeleteW, 17)
                && !StudioSession.historySelectedId.isEmpty()) {
            ModNetwork.sendToServer(new com.scriptcraft.network.ScriptHistoryPacket.Request(
                    com.scriptcraft.network.ScriptHistoryPacket.DELETE,
                    StudioSession.historyScript, StudioSession.historySelectedId));
            return;
        }
        for (HistEntryHit hit : histHits) {
            if (hover(mx, my, hit.x, hit.y, hit.w, hit.h)) {
                StudioSession.historySelectedId = hit.id;
                StudioSession.historyPreviewText = "";
                histPreviewScroll = 0;
                ModNetwork.sendToServer(new com.scriptcraft.network.ScriptHistoryPacket.Request(
                        com.scriptcraft.network.ScriptHistoryPacket.PREVIEW,
                        StudioSession.historyScript, hit.id));
                return;
            }
        }
    }
}
