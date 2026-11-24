package com.scriptcraft.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.scriptcraft.client.StudioAnimModel.AnimDoc;
import com.scriptcraft.client.StudioAnimModel.BoneId;
import com.scriptcraft.client.StudioAnimModel.Channel;
import com.scriptcraft.client.StudioAnimModel.EaseCurve;
import com.scriptcraft.client.StudioAnimModel.KeyPoint;
import com.scriptcraft.client.StudioAnimModel.KeyRef;
import com.scriptcraft.lang.Messages;
import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.network.StudioExplorerPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Полноценный высокопроизводительный 3D-редактор анимаций игрока в Студии:
 * — Аппаратная отрисовка всех линий 1-квадом через GPU (без попиксельных циклов g.fill) и O(1) проверка изменений;
 * — Единая матрица поворотного стола (платформа + персонаж крутятся идеально ровно без наклона/дребезга);
 * — Проекционный экранный Якобиан 3D-Гизмо: перетаскивание Поворота [R], Сдвига [G] и Изгиба [B] всегда идёт
 *   строго за курсором мыши под любым ракурсом камеры;
 * — Кнопки «▶ В игре» и «■ Стоп» для проверки и мгновенной остановки анимации на игроке вне меню.
 */
@OnlyIn(Dist.CLIENT)
public final class StudioAnimEditor {
    private static final int SURFACE = 0xF2161D29;
    private static final int BAR = 0xF518202D;
    private static final int EDGE = 0xFF2B364A;
    private static final int EDGE_HOT = 0xFF425474;
    private static final int GOLD = 0xFFF0B85B;
    private static final int ACCENT = 0xFF58A6FF;
    private static final int GREEN = 0xFF4CC38A;
    private static final int RED = 0xFFE56B6F;
    private static final int PURPLE = 0xFFC678DD;
    private static final int TEXT = 0xFFE8EDF5;
    private static final int MUTED = 0xFF8A96AA;
    private static final int ROW = 0xFF1D2636;
    private static final int ROW_HOT = 0xFF27344A;

    public enum ToolMode { ROTATE, MOVE, BEND }

    private static final AnimDoc DOC = new AnimDoc();
    private static boolean initialized = false;
    private static BoneId selectedBone = BoneId.RIGHT_ARM;
    private static ToolMode toolMode = ToolMode.ROTATE;
    /** -1 = свободная ось, 0 = X, 1 = Y, 2 = Z. */
    private static int axisLock = -1;
    private static EaseCurve activeEase = EaseCurve.INOUTSINE;

    // Воспроизведение и таймлайн
    private static boolean playing = false;
    private static float playhead = 0f;
    private static float playbackSpeed = 1.0f;
    private static long lastPlayNano = System.nanoTime();
    private static boolean expandChannels = false;
    private static float pxPerTick = 9.0f;
    private static int timelineScrollX = 0;
    private static int timelineScrollY = 0;
    private static int timelineH = 132;
    private static final Set<KeyRef> selectedKeys = new LinkedHashSet<>();

    // Буфер обмена ключей / позы
    private record CopiedKey(BoneId bone, Channel ch, int relTick, float value, EaseCurve ease) {}
    private static final List<CopiedKey> keyClipboard = new ArrayList<>();

    // Камера 3D-вьюпорта (ровный поворотный стол)
    private static float camYaw = 26f;
    private static float camPitch = 16f;
    private static float camZoom = 1.0f;
    private static float camPanX = 0f;
    private static float camPanY = 0f;
    private static boolean firstPersonPreview = false;
    private static boolean showGrid = true;

    // Кэшированные матрицы последнего кадра для точного 1:1 проекционного Гизмо
    private static final Matrix4f lastEntityToScreen = new Matrix4f();

    // Состояние перетаскивания мыши
    private enum DragKind {
        NONE, ORBIT, PAN, GIZMO_POSE, SCRUB_PLAYHEAD, MARQUEE_SELECT, MOVE_KEYS, TIMELINE_RESIZE, VALUE_SCRUB
    }
    private static DragKind dragKind = DragKind.NONE;
    private static int dragStartX, dragStartY, dragCurX, dragCurY;
    private static float dragStartYaw, dragStartPitch, dragStartPanX, dragStartPanY;
    private static int dragGizmoAxis = -1; // -1=свободно, 0=X, 1=Y, 2=Z
    private static float dragInitA, dragInitB, dragInitC;
    /** Экранные производные (px на 1 ед. параметра) в момент начала захвата Гизмо — гарантируют отсутствие инверсии! */
    private static final float[] dragJacX = new float[3];
    private static final float[] dragJacY = new float[3];
    private static Channel scrubChannel = null;
    private static float scrubInitVal = 0f;
    private static int dragKeyOriginTick = 0;
    private static final List<CopiedKey> dragMovingSnapshot = new ArrayList<>();
    private static int dragStartTimelineH = 132;

    // Поля ввода инспектора и диалогов
    private static final StudioTextField[] channelFields = new StudioTextField[8];
    private static int activeFieldIdx = -1;
    private static final StudioTextField endTickField = new StudioTextField().digitsOnly(5);
    private static final StudioTextField fadeField = new StudioTextField().digitsOnly(3);
    private static final StudioTextField returnTickField = new StudioTextField().digitsOnly(5);
    private static long lastInspectorSyncVer = -1L;
    private static float lastInspectorSyncTick = -999f;
    private static BoneId lastInspectorSyncBone = null;

    // Модальные окна
    private static boolean openModal = false;
    private static boolean newModal = false;
    private static boolean easeDropdown = false;
    private static boolean helpPopup = false;
    private static int openModalScroll = 0;
    private static int easeModalScroll = 0;
    private static final StudioTextField newNsField = new StudioTextField().filenameFilter(32);
    private static final StudioTextField newNameField = new StudioTextField().filenameFilter(48);
    private static List<StudioExplorerPacket.Entry> serverAnimFiles = List.of();
    private static long lastListReqMs = 0L;

    // Автономные модели игрока (если PlayerAnimator не загружен)
    private static PlayerModel<?> fallbackWide;
    private static PlayerModel<?> fallbackSlim;

    // Области разметки и проекции 3D-скелета
    private static int rootX, rootY, rootW, rootH;
    private static int topBarY, mainY, mainH;
    private static int leftX, leftW, vpX, vpW, rightX, rightW;
    private static int tlX, tlY, tlW, tlH, tlTrackX, tlTrackY, tlTrackW, tlTrackH;
    private static int easeBtnX, easeBtnY, easeBtnW;

    private record BtnHit(int x, int y, int w, int h, String id, String tip) {}
    private static final List<BtnHit> btns = new ArrayList<>();

    private record ProjectedBone(BoneId bone, float sx, float sy, float ex, float ey, float depth) {}
    private static final List<ProjectedBone> projectedBones = new ArrayList<>();
    private static BoneId hoveredViewportBone = null;
    private static int hoveredGizmoAxis = -1;
    private static float gizmoOriginX, gizmoOriginY;
    private static final float[] gizmoTipX = new float[3];
    private static final float[] gizmoTipY = new float[3];

    private record FieldBox(int idx, int x, int y, int w, int h, StudioTextField field, Channel ch) {}
    private static final List<FieldBox> fieldBoxes = new ArrayList<>();

    private record DiamondHit(KeyRef ref, int x, int y) {}
    private static final List<DiamondHit> diamondHits = new ArrayList<>();

    private static String hoverTip = null;
    private static int hoverTipX, hoverTipY;

    static {
        for (int i = 0; i < channelFields.length; i++) {
            channelFields[i] = new StudioTextField().maxLength(10);
        }
    }

    private StudioAnimEditor() {
    }

    private static void ensureInit() {
        if (initialized) return;
        initialized = true;
        DOC.clearTracks();
        DOC.resetHistory();
        syncInspectorFromDoc(true);
    }

    public static void reset() {
        playing = false;
        openModal = false;
        newModal = false;
        easeDropdown = false;
        helpPopup = false;
        dragKind = DragKind.NONE;
        activeFieldIdx = -1;
        selectedKeys.clear();
        if (ModList.get().isLoaded("playeranimator")) {
            PlayerAnimBridge.clearStudioPreview();
        }
    }

    public static void onLeaveTab() {
        playing = false;
        dragKind = DragKind.NONE;
        if (ModList.get().isLoaded("playeranimator")) {
            PlayerAnimBridge.clearStudioPreview();
        }
    }

    public static void requestServerFiles() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            lastListReqMs = System.currentTimeMillis();
            ModNetwork.sendToServer(new StudioExplorerPacket.Action(StudioExplorerPacket.Kind.LIST, "$animations", ""));
        }
    }

    public static void acceptServerFiles(List<StudioExplorerPacket.Entry> entries) {
        serverAnimFiles = entries == null ? List.of() : List.copyOf(entries);
    }

    public static void acceptLoadedJson(String relPath, String json) {
        ensureInit();
        AnimDoc temp = new AnimDoc();
        parseRelPathInto(relPath, temp);
        if (!StudioAnimModel.deserialize(json, temp)) {
            ClientToasts.push(Messages.toast("studio.anim.load_err", relPath));
            return;
        }
        DOC.pushUndo();
        DOC.clearTracks();
        DOC.namespace = temp.namespace;
        DOC.fileName = temp.fileName;
        DOC.displayName = temp.displayName;
        DOC.author = temp.author;
        DOC.description = temp.description;
        DOC.beginTick = temp.beginTick;
        DOC.endTick = temp.endTick;
        DOC.stopTick = temp.stopTick;
        DOC.looped = temp.looped;
        DOC.returnTick = temp.returnTick;
        DOC.easingBefore = temp.easingBefore;
        DOC.uuid = temp.uuid;
        for (BoneId b : BoneId.values()) {
            for (Channel ch : Channel.values()) {
                for (KeyPoint kp : temp.track(b, ch).values()) {
                    DOC.putKey(b, ch, kp.tick(), kp.value(), kp.ease());
                }
            }
        }
        DOC.markSaved();
        playhead = 0f;
        playing = false;
        selectedKeys.clear();
        openModal = false;
        syncInspectorFromDoc(true);
        ClientToasts.push(Messages.toast("studio.anim.loaded", DOC.fullId()));
    }

    private static void parseRelPathInto(String relPath, AnimDoc doc) {
        if (relPath == null) return;
        String norm = relPath.replace('\\', '/');
        String[] parts = norm.split("/");
        if (parts.length >= 5 && "resources".equals(parts[0]) && "assets".equals(parts[1])) {
            doc.namespace = parts[2];
            StringBuilder rest = new StringBuilder();
            for (int i = 4; i < parts.length; i++) {
                if (!rest.isEmpty()) rest.append('/');
                rest.append(parts[i]);
            }
            String fn = rest.toString();
            if (fn.toLowerCase(Locale.ROOT).endsWith(".json")) {
                fn = fn.substring(0, fn.length() - 5);
            }
            doc.fileName = fn.isEmpty() ? "anim" : fn;
            doc.displayName = doc.fileName;
        }
    }

    public static void saveCurrentToServer() {
        ensureInit();
        commitActiveField();
        String json = StudioAnimModel.serialize(DOC);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        String rel = DOC.serverRelPath();
        ModNetwork.sendToServer(new StudioExplorerPacket.Action(
                StudioExplorerPacket.Kind.WRITE_FILE, rel, "$anim_save", bytes));
        DOC.markSaved();
        ClientToasts.push(Messages.toast("studio.anim.saved", DOC.fullId()));
    }

    // =========================================================================
    //  ОТРИСОВКА
    // =========================================================================

    public static void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my) {
        ensureInit();
        tickPlayback();
        btns.clear();
        fieldBoxes.clear();
        diamondHits.clear();
        hoverTip = null;

        if (System.currentTimeMillis() - lastListReqMs > 15000L && serverAnimFiles.isEmpty()) {
            requestServerFiles();
        }

        rootX = x;
        rootY = y;
        rootW = w;
        rootH = h;

        topBarY = y;
        int topBarH = 24;
        tlH = Math.max(88, Math.min(h / 2, timelineH));
        tlX = x;
        tlW = w;
        tlY = y + h - tlH;

        mainY = y + topBarH + 3;
        mainH = Math.max(70, tlY - mainY - 3);

        leftW = Math.max(118, Math.min(148, w / 5));
        rightW = Math.max(162, Math.min(206, w / 4 + 16));
        leftX = x;
        rightX = x + w - rightW;
        vpX = leftX + leftW + 3;
        vpW = Math.max(80, rightX - vpX - 3);

        renderTopBar(g, font, x, topBarY, w, topBarH, mx, my);
        renderLeftBones(g, font, leftX, mainY, leftW, mainH, mx, my);
        renderViewport3D(g, font, vpX, mainY, vpW, mainH, mx, my);
        renderRightInspector(g, font, rightX, mainY, rightW, mainH, mx, my);
        renderTimeline(g, font, tlX, tlY, tlW, tlH, mx, my);

        boolean splitHot = dragKind == DragKind.TIMELINE_RESIZE || (Math.abs(my - tlY) <= 2 && mx >= x && mx < x + w);
        if (splitHot) {
            g.fill(x, tlY - 1, x + w, tlY + 1, GOLD);
        }

        if (openModal) {
            renderOpenModal(g, font, mx, my);
        } else if (newModal) {
            renderNewModal(g, font, mx, my);
        } else if (easeDropdown) {
            renderEaseDropdown(g, font, mx, my);
        } else if (helpPopup) {
            renderHelpPopup(g, font, mx, my);
        }

        if (hoverTip != null && !openModal && !newModal) {
            int tw = font.width(hoverTip) + 10;
            int tx = Math.max(4, Math.min(hoverTipX, x + w - tw - 4));
            int ty = Math.max(18, Math.min(hoverTipY, y + h - 4));
            g.pose().pushPose();
            g.pose().translate(0, 0, 450);
            g.fill(tx, ty - 14, tx + tw, ty, 0xF5101622);
            border(g, tx, ty - 14, tw, 14, EDGE_HOT);
            g.drawString(font, hoverTip, tx + 5, ty - 11, TEXT, false);
            g.pose().popPose();
        }
    }

    private static void tickPlayback() {
        long now = System.nanoTime();
        float dtSec = Math.max(0f, Math.min(0.1f, (now - lastPlayNano) / 1_000_000_000f));
        lastPlayNano = now;
        if (!playing) return;

        playhead += dtSec * 20f * playbackSpeed;
        int maxTick = DOC.looped ? Math.max(1, DOC.endTick) : Math.max(DOC.endTick, DOC.stopTick);
        if (playhead > maxTick) {
            if (DOC.looped) {
                int span = Math.max(1, DOC.endTick - DOC.returnTick);
                playhead = DOC.returnTick + ((playhead - DOC.returnTick) % span);
            } else {
                playhead = 0f;
                playing = false;
            }
        }
        if (activeFieldIdx < 0) {
            syncInspectorFromDoc(false);
        }
    }

    // -------------------------------------------------------------------------
    //  1. ВЕРХНЯЯ ПАНЕЛЬ ИНСТРУМЕНТОВ
    // -------------------------------------------------------------------------

    private static void renderTopBar(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my) {
        g.fill(x, y, x + w, y + h, SURFACE);
        border(g, x, y, w, h, EDGE);
        int bx = x + 5;
        int by = y + 4;

        boolean dirty = DOC.isDirty();
        String idBadge = (dirty ? "* " : "") + DOC.fullId();
        int idW = Math.min(132, font.width(idBadge) + 14);
        bx = drawBtn(g, font, mx, my, bx, by, idW, 16, idBadge, "file:open",
                Messages.get("studio.anim.tip.open"), openModal, dirty ? GOLD : ACCENT) + 3;

        bx = drawBtn(g, font, mx, my, bx, by, font.width("+") + 10, 16, "+", "file:new",
                Messages.get("studio.anim.tip.new"), newModal, GREEN) + 3;
        bx = drawBtn(g, font, mx, my, bx, by, font.width(Messages.get("studio.anim.save")) + 10, 16,
                Messages.get("studio.anim.save"), "file:save", "Ctrl+S", false, dirty ? GOLD : TEXT) + 4;

        g.fill(bx, y + 4, bx + 1, y + h - 4, EDGE);
        bx += 5;

        // Режимы гизмо: Rotate [R], Move [G], Bend [B]
        bx = drawBtn(g, font, mx, my, bx, by, font.width(Messages.get("studio.anim.tool.rot")) + 10, 16,
                Messages.get("studio.anim.tool.rot"), "tool:rot", "[R]", toolMode == ToolMode.ROTATE, GOLD) + 2;
        bx = drawBtn(g, font, mx, my, bx, by, font.width(Messages.get("studio.anim.tool.pos")) + 10, 16,
                Messages.get("studio.anim.tool.pos"), "tool:pos", "[G]", toolMode == ToolMode.MOVE, ACCENT) + 2;
        boolean bendyLoaded = ModList.get().isLoaded("bendylib");
        bx = drawBtn(g, font, mx, my, bx, by, font.width(Messages.get("studio.anim.tool.bend")) + 10, 16,
                Messages.get("studio.anim.tool.bend"), "tool:bend",
                "[B] Bendy-lib: " + Messages.get(bendyLoaded ? "studio.lib.loaded" : "studio.lib.missing"),
                toolMode == ToolMode.BEND, PURPLE) + 4;

        // Оси X / Y / Z
        bx = drawBtn(g, font, mx, my, bx, by, 14, 16, "X", "axis:0", "Axis X", axisLock == 0, 0xFFEF4444) + 1;
        bx = drawBtn(g, font, mx, my, bx, by, 14, 16, "Y", "axis:1", "Axis Y", axisLock == 1, 0xFF22C55E) + 1;
        bx = drawBtn(g, font, mx, my, bx, by, 14, 16, "Z", "axis:2", "Axis Z", axisLock == 2, 0xFF3B82F6) + 4;

        g.fill(bx, y + 4, bx + 1, y + h - 4, EDGE);
        bx += 4;

        if (bx + 80 < x + w) {
            bx = drawBtn(g, font, mx, my, bx, by, font.width("⇄") + 8, 16, "⇄", "pose:mirror",
                    Messages.get("studio.anim.tip.mirror") + " (Ctrl+M)", false, GOLD) + 2;
            bx = drawBtn(g, font, mx, my, bx, by, font.width("1P") + 8, 16, "1P", "vp:1p",
                    Messages.get("studio.anim.tip.1p"), firstPersonPreview, GREEN) + 2;
            bx = drawBtn(g, font, mx, my, bx, by, font.width("🎥") + 8, 16, "🎥", "vp:reset_cam",
                    Messages.get("studio.anim.tip.cam") + " [F]", false, TEXT) + 4;
        }

        // Правая часть топ-бара: [▶ В игре] [■ Стоп] [→ В скрипт] [?]
        int rx = x + w - 5;
        rx -= 16;
        drawBtn(g, font, mx, my, rx, by, 16, 16, "?", "ui:help", Messages.get("studio.anim.tip.help"), helpPopup, GOLD);

        String codeLbl = Messages.get("studio.anim.to_script");
        int codeW = font.width(codeLbl) + 8;
        if (rx - codeW - 3 > bx) {
            rx -= (codeW + 3);
            drawBtn(g, font, mx, my, rx, by, codeW, 16, codeLbl, "act:insert_code",
                    Messages.get("studio.anim.tip.to_script"), false, ACCENT);
        }

        String stopLbl = Messages.get("studio.anim.stop_world");
        int stopW = font.width(stopLbl) + 8;
        if (rx - stopW - 3 > bx) {
            rx -= (stopW + 2);
            drawBtn(g, font, mx, my, rx, by, stopW, 16, stopLbl, "act:stop_world",
                    Messages.get("studio.anim.tip.stop_world"), false, RED);
        }

        String testLbl = Messages.get("studio.anim.test_world");
        int testW = font.width(testLbl) + 8;
        if (rx - testW - 3 > bx) {
            rx -= (testW + 2);
            drawBtn(g, font, mx, my, rx, by, testW, 16, testLbl, "act:test_world",
                    Messages.get("studio.anim.tip.test_world"), false, GREEN);
        }
    }

    // -------------------------------------------------------------------------
    //  2. ЛЕВАЯ ПАНЕЛЬ: ДЕРЕВО КОСТЕЙ (без лишних шаблонов)
    // -------------------------------------------------------------------------

    private static void renderLeftBones(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my) {
        g.fill(x, y, x + w, y + h, SURFACE);
        border(g, x, y, w, h, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + 17, BAR);
        g.drawString(font, Messages.get("studio.anim.bones"), x + 6, y + 5, GOLD, false);

        int curTick = Math.round(playhead);
        int ry = y + 20;
        BoneId[] bones = BoneId.values();
        for (int i = 0; i < bones.length; i++) {
            BoneId b = bones[i];
            if (ry + 16 > y + h - 2) break;
            boolean sel = b == selectedBone;
            boolean hot = hover(mx, my, x + 2, ry, w - 4, 16);
            g.fill(x + 2, ry, x + w - 2, ry + 16, sel ? 0xFF24344D : (hot ? ROW_HOT : ROW));
            g.fill(x + 2, ry, x + 4, ry + 16, b.color);
            if (sel) {
                border(g, x + 2, ry, w - 4, 16, GOLD);
            }

            boolean vis = DOC.isBoneVisible(b);
            int eyeX = x + w - 15;
            g.drawString(font, vis ? "◉" : "○", eyeX, ry + 4, vis ? MUTED : 0xFF475264, false);
            btns.add(new BtnHit(eyeX - 1, ry, 13, 16, "vis:" + b.name(), Messages.get("studio.anim.tip.vis")));

            int rightLim = eyeX - 3;
            if (b.bendable) {
                rightLim -= 8;
                g.drawString(font, "⌒", rightLim, ry + 3, PURPLE, false);
            }
            if (DOC.boneHasKeyAt(b, curTick)) {
                rightLim -= 8;
                g.drawString(font, "◆", rightLim, ry + 4, GOLD, false);
            } else if (DOC.boneHasAnyKeys(b)) {
                rightLim -= 7;
                g.drawString(font, "•", rightLim, ry + 4, ACCENT, false);
            }

            String label = (i + 1) + " " + Messages.get(b.langKey);
            g.drawString(font, trim(font, label, Math.max(24, rightLim - (x + 7))), x + 7, ry + 4,
                    sel ? GOLD : (vis ? TEXT : MUTED), false);
            btns.add(new BtnHit(x + 2, ry, eyeX - x - 3, 16, "bone:" + b.name(), null));
            ry += 17;
        }
    }

    // -------------------------------------------------------------------------
    //  3. ЦЕНТРАЛЬНЫЙ 3D-ВЬЮПОРТ (РОВНЫЙ ПОВОРОТНЫЙ СТОЛ + GPU-ЛИНИИ + ГИЗМО)
    // -------------------------------------------------------------------------

    private static void renderViewport3D(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my) {
        g.fillGradient(x, y, x + w, y + h, 0xFF0E1420, 0xFF172234);
        border(g, x, y, w, h, EDGE);

        boolean paLoaded = ModList.get().isLoaded("playeranimator");
        boolean bendyLoaded = ModList.get().isLoaded("bendylib");

        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);

        float cx = x + w * 0.5f + camPanX;
        float cy = y + h * 0.54f + camPanY;
        float baseScale = Math.max(26f, Math.min(w * 0.34f, h * 0.41f));
        float pxPerBlock = baseScale * camZoom;

        float effPitch = firstPersonPreview ? -18f : camPitch;
        float effYaw = firstPersonPreview ? 180f : camYaw;
        float pivotY = firstPersonPreview ? 1.50f : 0.90f;

        // Строго ортогональный поворотный стол вокруг центра персонажа (0, pivotY, 0):
        // -effPitch даёт правильный взгляд СВЕРХУ при camPitch > 0, а +effYaw крутит платформу и модель синхронно вокруг вертикали Y!
        PoseStack camStack = new PoseStack();
        camStack.translate(cx, cy, 220f);
        camStack.mulPoseMatrix(new Matrix4f().scaling(pxPerBlock, pxPerBlock, -pxPerBlock));
        camStack.mulPose(Axis.ZP.rotationDegrees(180f));
        camStack.mulPose(Axis.XP.rotationDegrees(-effPitch));
        camStack.mulPose(Axis.YP.rotationDegrees(effYaw));
        camStack.translate(0f, -pivotY, 0f);
        lastEntityToScreen.set(camStack.last().pose());

        // 1) Сглаженная GPU-сетка платформы (1 квад на линию, субпиксельная точность без дрожания!)
        if (showGrid && !firstPersonPreview) {
            renderFloorGridSmooth(g, lastEntityToScreen);
        }

        // 2) Рендер 3D-модели игрока
        Minecraft mc = Minecraft.getInstance();
        if (paLoaded && mc.player != null) {
            renderLivePlayerWithMods(g, mc.player, cx, cy, pxPerBlock, effPitch, effYaw, pivotY);
        } else {
            renderStandalonePlayerModel(g, mc, cx, cy, pxPerBlock, effPitch, effYaw, pivotY);
        }

        // 3) Проекция скелета и точный экранно-проекционный 3D-Гизмо
        computeBoneProjections(lastEntityToScreen);
        updateViewportHover(mx, my, x, y, w, h);
        renderSkeletonOverlayAndGizmo(g, font, mx, my);

        String topBadge = firstPersonPreview
                ? Messages.get("studio.anim.vp.1p_badge")
                : String.format(Locale.ROOT, "Yaw %.0f°  Pitch %.0f°  Zoom %.0f%%", camYaw, camPitch, camZoom * 100f);
        g.drawString(font, topBadge, x + 6, y + 6, MUTED, false);

        if (!paLoaded) {
            String warn = Messages.get("studio.anim.no_pa_banner");
            g.fill(x + 4, y + h - 18, x + Math.min(w - 4, font.width(warn) + 12), y + h - 4, 0xCC2A1A1E);
            g.drawString(font, trim(font, warn, w - 16), x + 8, y + h - 15, GOLD, false);
        } else {
            String hint = bendyLoaded
                    ? "PlayerAnimator + Bendy-lib ●  |  LMB: Gizmo/Bone/Orbit  MMB: Pan  Scroll: Zoom"
                    : "PlayerAnimator ●  |  LMB: Gizmo/Bone/Orbit  MMB: Pan  Scroll: Zoom";
            g.drawString(font, trim(font, hint, w - 12), x + 6, y + h - 13, 0xAA8A96AA, false);
        }

        g.disableScissor();
    }

    private static void renderFloorGridSmooth(GuiGraphics g, Matrix4f mat) {
        float extent = 1.5f;
        float step = 0.5f;
        for (float v = -extent; v <= extent + 0.01f; v += step) {
            boolean center = Math.abs(v) < 0.01f;
            int colZ = center ? 0xAA3B82F6 : 0x40384A66;
            int colX = center ? 0xAAEF4444 : 0x40384A66;
            Vector4f a1 = project(mat, v, 0f, -extent);
            Vector4f a2 = project(mat, v, 0f, extent);
            drawSmoothLine(g, a1.x, a1.y, a2.x, a2.y, center ? 1.4f : 1.0f, colZ);

            Vector4f b1 = project(mat, -extent, 0f, v);
            Vector4f b2 = project(mat, extent, 0f, v);
            drawSmoothLine(g, b1.x, b1.y, b2.x, b2.y, center ? 1.4f : 1.0f, colX);
        }
        // Стрелка направления лица персонажа (-Z в entity space при yBodyRot=180)
        Vector4f f0 = project(mat, 0f, 0.005f, -0.75f);
        Vector4f f1 = project(mat, 0f, 0.005f, -1.25f);
        Vector4f fl = project(mat, -0.16f, 0.005f, -1.05f);
        Vector4f fr = project(mat, 0.16f, 0.005f, -1.05f);
        drawSmoothLine(g, f0.x, f0.y, f1.x, f1.y, 1.4f, 0xBB4CC38A);
        drawSmoothLine(g, f1.x, f1.y, fl.x, fl.y, 1.4f, 0xBB4CC38A);
        drawSmoothLine(g, f1.x, f1.y, fr.x, fr.y, 1.4f, 0xBB4CC38A);
        g.flush();
    }

    private static void renderLivePlayerWithMods(GuiGraphics g, AbstractClientPlayer player,
                                                 float cx, float cy, float pxPerBlock,
                                                 float effPitch, float effYaw, float pivotY) {
        PlayerAnimBridge.beginViewportPreview(DOC, playhead, firstPersonPreview);

        // Полностью изолируем позу рендера от движения, приседа, покачивания плаща и удара в мире
        float oldBodyRot = player.yBodyRot;
        float oldBodyRotO = player.yBodyRotO;
        float oldYRot = player.getYRot();
        float oldYRotO = player.yRotO;
        float oldXRot = player.getXRot();
        float oldXRotO = player.xRotO;
        float oldHeadRot = player.yHeadRot;
        float oldHeadRotO = player.yHeadRotO;
        float oldWalkSpeed = player.walkAnimation.speed();
        float oldAttack = player.attackAnim;
        float oldOAttack = player.oAttackAnim;
        Pose oldPose = player.getPose();
        double oldCloakX = player.xCloak, oldCloakOX = player.xCloakO;
        double oldCloakY = player.yCloak, oldCloakOY = player.yCloakO;
        double oldCloakZ = player.zCloak, oldCloakOZ = player.zCloakO;

        player.yBodyRot = 180f;
        player.yBodyRotO = 180f;
        player.setYRot(180f);
        player.yRotO = 180f;
        player.setXRot(0f);
        player.xRotO = 0f;
        player.yHeadRot = 180f;
        player.yHeadRotO = 180f;
        player.walkAnimation.setSpeed(0f);
        player.attackAnim = 0f;
        player.oAttackAnim = 0f;
        if (oldPose == Pose.CROUCHING) {
            player.setPose(Pose.STANDING);
        }
        player.xCloak = player.xCloakO = player.getX();
        player.yCloak = player.yCloakO = player.getY();
        player.zCloak = player.zCloakO = player.getZ();

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 220.0);
        pose.mulPoseMatrix(new Matrix4f().scaling(pxPerBlock, pxPerBlock, -pxPerBlock));
        pose.mulPose(Axis.ZP.rotationDegrees(180f));
        pose.mulPose(Axis.XP.rotationDegrees(-effPitch));
        pose.mulPose(Axis.YP.rotationDegrees(effYaw));
        pose.translate(0f, -pivotY, 0f);

        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        Quaternionf camQuat = new Quaternionf().rotationZ((float) Math.PI);
        dispatcher.overrideCameraOrientation(camQuat);
        dispatcher.setRenderShadow(false);
        dispatcher.render(player, 0.0, 0.0, 0.0, 0.0f, 0.0f, pose, g.bufferSource(), 0xF000F0);
        g.flush();
        dispatcher.setRenderShadow(true);
        pose.popPose();
        Lighting.setupFor3DItems();

        player.yBodyRot = oldBodyRot;
        player.yBodyRotO = oldBodyRotO;
        player.setYRot(oldYRot);
        player.yRotO = oldYRotO;
        player.setXRot(oldXRot);
        player.xRotO = oldXRotO;
        player.yHeadRot = oldHeadRot;
        player.yHeadRotO = oldHeadRotO;
        player.walkAnimation.setSpeed(oldWalkSpeed);
        player.attackAnim = oldAttack;
        player.oAttackAnim = oldOAttack;
        if (oldPose == Pose.CROUCHING) {
            player.setPose(oldPose);
        }
        player.xCloak = oldCloakX; player.xCloakO = oldCloakOX;
        player.yCloak = oldCloakY; player.yCloakO = oldCloakOY;
        player.zCloak = oldCloakZ; player.zCloakO = oldCloakOZ;

        PlayerAnimBridge.endViewportPreview();
    }

    private static void renderStandalonePlayerModel(GuiGraphics g, Minecraft mc,
                                                    float cx, float cy, float pxPerBlock,
                                                    float effPitch, float effYaw, float pivotY) {
        if (mc.getEntityModels() == null) return;
        boolean slim = mc.player != null && "slim".equals(mc.player.getModelName());
        ResourceLocation skin = mc.player != null
                ? mc.player.getSkinTextureLocation()
                : DefaultPlayerSkin.getDefaultSkin();
        PlayerModel<?> model = getFallbackModel(mc, slim);
        model.young = false;

        applyStandaloneBone(model.head, BoneId.HEAD);
        applyStandaloneBone(model.body, BoneId.TORSO);
        applyStandaloneBone(model.rightArm, BoneId.RIGHT_ARM);
        applyStandaloneBone(model.leftArm, BoneId.LEFT_ARM);
        applyStandaloneBone(model.rightLeg, BoneId.RIGHT_LEG);
        applyStandaloneBone(model.leftLeg, BoneId.LEFT_LEG);

        float bx = StudioAnimModel.uiPosToEngine(BoneId.BODY, Channel.POS_X, DOC.eval(BoneId.BODY, Channel.POS_X, playhead), 0f);
        float by = StudioAnimModel.uiPosToEngine(BoneId.BODY, Channel.POS_Y, DOC.eval(BoneId.BODY, Channel.POS_Y, playhead), 0f);
        float bz = StudioAnimModel.uiPosToEngine(BoneId.BODY, Channel.POS_Z, DOC.eval(BoneId.BODY, Channel.POS_Z, playhead), 0f);
        float brx = DOC.eval(BoneId.BODY, Channel.ROT_X, playhead);
        float bry = DOC.eval(BoneId.BODY, Channel.ROT_Y, playhead);
        float brz = DOC.eval(BoneId.BODY, Channel.ROT_Z, playhead);

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 220.0);
        pose.mulPoseMatrix(new Matrix4f().scaling(pxPerBlock, pxPerBlock, -pxPerBlock));
        pose.mulPose(Axis.ZP.rotationDegrees(180f));
        pose.mulPose(Axis.XP.rotationDegrees(-effPitch));
        pose.mulPose(Axis.YP.rotationDegrees(effYaw));
        pose.translate(0f, -pivotY, 0f);
        // Точная копия PlayerRendererMixin.applyBodyTransforms ДО scale(-0.9375, -0.9375, 0.9375)!
        pose.translate(bx, by + 0.7f, bz);
        pose.mulPose(Axis.ZP.rotationDegrees(brz));
        pose.mulPose(Axis.YP.rotationDegrees(bry));
        pose.mulPose(Axis.XP.rotationDegrees(brx));
        pose.translate(0f, -0.7f, 0f);
        pose.scale(-0.9375f, -0.9375f, 0.9375f);
        pose.translate(0f, -1.501f, 0f);

        Lighting.setupForEntityInInventory();
        var buf = g.bufferSource();
        setOverlayLayer(model, false);
        model.renderToBuffer(pose, buf.getBuffer(RenderType.entityCutoutNoCull(skin)),
                0xF000F0, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        setOverlayLayer(model, true);
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
        model.renderToBuffer(pose, buf.getBuffer(RenderType.entityCutoutNoCullZOffset(skin)),
                0xF000F0, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        restoreVisibility(model);
        pose.popPose();
        g.flush();
        Lighting.setupFor3DItems();
    }

    private static void applyStandaloneBone(ModelPart part, BoneId bone) {
        part.visible = DOC.isBoneVisible(bone);
        part.x = StudioAnimModel.uiPosToEngine(bone, Channel.POS_X, DOC.eval(bone, Channel.POS_X, playhead), bone.defX);
        part.y = StudioAnimModel.uiPosToEngine(bone, Channel.POS_Y, DOC.eval(bone, Channel.POS_Y, playhead), bone.defY);
        part.z = StudioAnimModel.uiPosToEngine(bone, Channel.POS_Z, DOC.eval(bone, Channel.POS_Z, playhead), bone.defZ);
        part.xRot = (float) Math.toRadians(DOC.eval(bone, Channel.ROT_X, playhead));
        part.yRot = (float) Math.toRadians(DOC.eval(bone, Channel.ROT_Y, playhead));
        part.zRot = (float) Math.toRadians(DOC.eval(bone, Channel.ROT_Z, playhead));
    }

    private static PlayerModel<?> getFallbackModel(Minecraft mc, boolean slim) {
        if (slim) {
            if (fallbackSlim == null) {
                fallbackSlim = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
            }
            return fallbackSlim;
        }
        if (fallbackWide == null) {
            fallbackWide = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        }
        return fallbackWide;
    }

    private static void setOverlayLayer(PlayerModel<?> m, boolean overlay) {
        m.head.visible = !overlay && DOC.isBoneVisible(BoneId.HEAD);
        m.body.visible = !overlay && DOC.isBoneVisible(BoneId.TORSO);
        m.rightArm.visible = !overlay && DOC.isBoneVisible(BoneId.RIGHT_ARM);
        m.leftArm.visible = !overlay && DOC.isBoneVisible(BoneId.LEFT_ARM);
        m.rightLeg.visible = !overlay && DOC.isBoneVisible(BoneId.RIGHT_LEG);
        m.leftLeg.visible = !overlay && DOC.isBoneVisible(BoneId.LEFT_LEG);
        m.hat.visible = overlay && DOC.isBoneVisible(BoneId.HEAD);
        m.jacket.visible = overlay && DOC.isBoneVisible(BoneId.TORSO);
        m.rightSleeve.visible = overlay && DOC.isBoneVisible(BoneId.RIGHT_ARM);
        m.leftSleeve.visible = overlay && DOC.isBoneVisible(BoneId.LEFT_ARM);
        m.rightPants.visible = overlay && DOC.isBoneVisible(BoneId.RIGHT_LEG);
        m.leftPants.visible = overlay && DOC.isBoneVisible(BoneId.LEFT_LEG);
    }

    private static void restoreVisibility(PlayerModel<?> m) {
        m.head.visible = m.body.visible = m.rightArm.visible = m.leftArm.visible = m.rightLeg.visible = m.leftLeg.visible = true;
        m.hat.visible = m.jacket.visible = m.rightSleeve.visible = m.leftSleeve.visible = m.rightPants.visible = m.leftPants.visible = true;
    }

    // --- 3D проецирование скелета + экранно-проекционный Гизмо (100% совпадение с движком!) ---

    /** Строит матрицу ModelPart-пространства с учётом BODY, опционально подменяя 1 канал на delta для расчёта Якобиана. */
    private static Matrix4f buildModelRootMatrix(Matrix4f entityToScreen, BoneId pertBone, Channel pertCh, float pertDelta) {
        float px = evalPert(BoneId.BODY, Channel.POS_X, pertBone, pertCh, pertDelta);
        float py = evalPert(BoneId.BODY, Channel.POS_Y, pertBone, pertCh, pertDelta);
        float pz = evalPert(BoneId.BODY, Channel.POS_Z, pertBone, pertCh, pertDelta);
        float rx = evalPert(BoneId.BODY, Channel.ROT_X, pertBone, pertCh, pertDelta);
        float ry = evalPert(BoneId.BODY, Channel.ROT_Y, pertBone, pertCh, pertDelta);
        float rz = evalPert(BoneId.BODY, Channel.ROT_Z, pertBone, pertCh, pertDelta);

        float ex = StudioAnimModel.uiPosToEngine(BoneId.BODY, Channel.POS_X, px, 0f);
        float ey = StudioAnimModel.uiPosToEngine(BoneId.BODY, Channel.POS_Y, py, 0f);
        float ez = StudioAnimModel.uiPosToEngine(BoneId.BODY, Channel.POS_Z, pz, 0f);

        return new Matrix4f(entityToScreen)
                .translate(ex, ey + 0.7f, ez)
                .rotateZ((float) Math.toRadians(rz))
                .rotateY((float) Math.toRadians(ry))
                .rotateX((float) Math.toRadians(rx))
                .translate(0f, -0.7f, 0f)
                .scale(-0.9375f, -0.9375f, 0.9375f)
                .translate(0f, -1.501f, 0f);
    }

    private static float evalPert(BoneId b, Channel c, BoneId pertBone, Channel pertCh, float pertDelta) {
        float v = DOC.eval(b, c, playhead);
        return (b == pertBone && c == pertCh) ? v + pertDelta : v;
    }

    /** Экранная точка репрезентативного конца кости при подстановке pertDelta (для 100% точного Якобиана). */
    private static Vector4f evalBoneFeatureScreen(Matrix4f entityToScreen, BoneId targetBone,
                                                  BoneId pertBone, Channel pertCh, float pertDelta,
                                                  boolean tipPoint) {
        Matrix4f modelRoot = buildModelRootMatrix(entityToScreen, pertBone, pertCh, pertDelta);
        if (targetBone == BoneId.BODY) {
            if (!tipPoint) return project(modelRoot, 0f, 11.2f / 16f, 0f);
            // Для вращения BODY берём рычаг в зависимости от оси вращения
            if (pertCh == Channel.ROT_Y) return project(modelRoot, 0f, 11.2f / 16f, -6f / 16f);
            return project(modelRoot, 0f, 3f / 16f, 0f);
        }
        if (targetBone == BoneId.RIGHT_ITEM || targetBone == BoneId.LEFT_ITEM) {
            BoneId arm = targetBone == BoneId.RIGHT_ITEM ? BoneId.RIGHT_ARM : BoneId.LEFT_ARM;
            Matrix4f armM = buildPartMatrix(modelRoot, arm, pertBone, pertCh, pertDelta);
            float ox = targetBone == BoneId.RIGHT_ITEM ? -1f : 1f;
            float px = ox + evalPert(targetBone, Channel.POS_X, pertBone, pertCh, pertDelta);
            float py = 10f - evalPert(targetBone, Channel.POS_Y, pertBone, pertCh, pertDelta);
            float pz = -2f + evalPert(targetBone, Channel.POS_Z, pertBone, pertCh, pertDelta);
            Matrix4f itemM = new Matrix4f(armM)
                    .translate(px / 16f, py / 16f, pz / 16f)
                    .rotateZ((float) Math.toRadians(evalPert(targetBone, Channel.ROT_Z, pertBone, pertCh, pertDelta)))
                    .rotateY((float) Math.toRadians(evalPert(targetBone, Channel.ROT_Y, pertBone, pertCh, pertDelta)))
                    .rotateX((float) Math.toRadians(evalPert(targetBone, Channel.ROT_X, pertBone, pertCh, pertDelta)));
            if (!tipPoint) return project(itemM, 0f, 0f, 0f);
            if (pertCh == Channel.ROT_Z) return project(itemM, 4f / 16f, 0f, 0f);
            return project(itemM, 0f, 0f, -6f / 16f);
        }

        Matrix4f m = buildPartMatrix(modelRoot, targetBone, pertBone, pertCh, pertDelta);
        if (!tipPoint) {
            return project(m, 0f, 0f, 0f);
        }
        if (pertCh == Channel.BEND_ANG || pertCh == Channel.BEND_DIR) {
            float dirRad = (float) Math.toRadians(evalPert(targetBone, Channel.BEND_DIR, pertBone, pertCh, pertDelta));
            float angRad = (float) Math.toRadians(evalPert(targetBone, Channel.BEND_ANG, pertBone, pertCh, pertDelta));
            float halfLen = 6f / 16f;
            float bendDisp = (float) Math.sin(angRad * 0.5f) * halfLen;
            float bx = (float) Math.sin(dirRad) * bendDisp;
            float bz = -(float) Math.cos(dirRad) * bendDisp;
            return project(m, bx, 10f / 16f, bz);
        }
        if (pertCh == Channel.ROT_Y) {
            return project(m, 0f, 4f / 16f, -5f / 16f);
        }
        float tipY = targetBone == BoneId.HEAD ? -7.5f : (targetBone == BoneId.TORSO ? 11f : 10.5f);
        return project(m, 0f, tipY / 16f, 0f);
    }

    private static Matrix4f buildPartMatrix(Matrix4f modelRoot, BoneId bone, BoneId pertBone, Channel pertCh, float pertDelta) {
        float ex = StudioAnimModel.uiPosToEngine(bone, Channel.POS_X, evalPert(bone, Channel.POS_X, pertBone, pertCh, pertDelta), bone.defX);
        float ey = StudioAnimModel.uiPosToEngine(bone, Channel.POS_Y, evalPert(bone, Channel.POS_Y, pertBone, pertCh, pertDelta), bone.defY);
        float ez = StudioAnimModel.uiPosToEngine(bone, Channel.POS_Z, evalPert(bone, Channel.POS_Z, pertBone, pertCh, pertDelta), bone.defZ);
        float rx = evalPert(bone, Channel.ROT_X, pertBone, pertCh, pertDelta);
        float ry = evalPert(bone, Channel.ROT_Y, pertBone, pertCh, pertDelta);
        float rz = evalPert(bone, Channel.ROT_Z, pertBone, pertCh, pertDelta);
        return new Matrix4f(modelRoot)
                .translate(ex / 16f, ey / 16f, ez / 16f)
                .rotateZ((float) Math.toRadians(rz))
                .rotateY((float) Math.toRadians(ry))
                .rotateX((float) Math.toRadians(rx));
    }

    private static void computeBoneProjections(Matrix4f entityToScreen) {
        projectedBones.clear();
        Matrix4f modelRoot = buildModelRootMatrix(entityToScreen, null, null, 0f);

        addProjectedBone(BoneId.BODY, modelRoot, 0f, 11.2f, 0f, 0f, 11.2f, -3.5f);
        addLimbProjection(BoneId.TORSO, modelRoot, 0f, 11f, 0f);
        addLimbProjection(BoneId.HEAD, modelRoot, 0f, -7.5f, 0f);
        Matrix4f rArmMat = addLimbProjection(BoneId.RIGHT_ARM, modelRoot, -1f, 10f, 0f);
        Matrix4f lArmMat = addLimbProjection(BoneId.LEFT_ARM, modelRoot, 1f, 10f, 0f);
        addProjectedItem(BoneId.RIGHT_ITEM, rArmMat, -1f);
        addProjectedItem(BoneId.LEFT_ITEM, lArmMat, 1f);
        addLimbProjection(BoneId.RIGHT_LEG, modelRoot, 0f, 12f, 0f);
        addLimbProjection(BoneId.LEFT_LEG, modelRoot, 0f, 12f, 0f);

        // Ориентация ручек Гизмо: стрелка каждой оси указывает СТРОГО туда, куда сдвинется/наклонится кость при +значении!
        Vector4f origin = evalBoneFeatureScreen(entityToScreen, selectedBone, null, null, 0f, false);
        gizmoOriginX = origin.x;
        gizmoOriginY = origin.y;

        float handleLenPx = 28f;
        Channel[] activeChs = activeToolChannels();
        for (int a = 0; a < activeChs.length; a++) {
            Channel ch = activeChs[a];
            boolean useTip = toolMode != ToolMode.MOVE;
            Vector4f p0 = evalBoneFeatureScreen(entityToScreen, selectedBone, selectedBone, ch, 0f, useTip);
            Vector4f p1 = evalBoneFeatureScreen(entityToScreen, selectedBone, selectedBone, ch, ch.angle ? 5f : 2f, useTip);
            float dx = p1.x - p0.x;
            float dy = p1.y - p0.y;
            float len = (float) Math.hypot(dx, dy);
            if (len < 0.001f) {
                dx = (a == 0) ? 1f : 0f;
                dy = (a == 1) ? -1f : 0.5f;
                len = (float) Math.hypot(dx, dy);
            }
            gizmoTipX[a] = gizmoOriginX + (dx / len) * handleLenPx;
            gizmoTipY[a] = gizmoOriginY + (dy / len) * handleLenPx;
        }
    }

    private static Channel[] activeToolChannels() {
        return switch (toolMode) {
            case ROTATE -> new Channel[]{Channel.ROT_X, Channel.ROT_Y, Channel.ROT_Z};
            case MOVE -> new Channel[]{Channel.POS_X, Channel.POS_Y, Channel.POS_Z};
            case BEND -> new Channel[]{Channel.BEND_DIR, Channel.BEND_ANG};
        };
    }

    private static Matrix4f addLimbProjection(BoneId bone, Matrix4f modelRoot, float tipDx, float tipDy, float tipDz) {
        Matrix4f m = buildPartMatrix(modelRoot, bone, null, null, 0f);
        Vector4f s = project(m, 0f, 0f, 0f);
        Vector4f e = project(m, tipDx / 16f, tipDy / 16f, tipDz / 16f);
        projectedBones.add(new ProjectedBone(bone, s.x, s.y, e.x, e.y, (s.z + e.z) * 0.5f));
        return m;
    }

    private static void addProjectedBone(BoneId bone, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1) {
        Vector4f s = project(m, x0 / 16f, y0 / 16f, z0 / 16f);
        Vector4f e = project(m, x1 / 16f, y1 / 16f, z1 / 16f);
        projectedBones.add(new ProjectedBone(bone, s.x, s.y, e.x, e.y, s.z));
    }

    private static void addProjectedItem(BoneId bone, Matrix4f armMat, float ox) {
        float px = ox + DOC.eval(bone, Channel.POS_X, playhead);
        float py = 10f - DOC.eval(bone, Channel.POS_Y, playhead);
        float pz = -2f + DOC.eval(bone, Channel.POS_Z, playhead);
        Matrix4f m = new Matrix4f(armMat).translate(px / 16f, py / 16f, pz / 16f);
        Vector4f s = project(m, 0f, 0f, 0f);
        Vector4f e = project(m, 0f, 0f, -5f / 16f);
        projectedBones.add(new ProjectedBone(bone, s.x, s.y, e.x, e.y, s.z));
    }

    private static Vector4f project(Matrix4f mat, float x, float y, float z) {
        return mat.transform(new Vector4f(x, y, z, 1.0f));
    }

    private static void updateViewportHover(int mx, int my, int x, int y, int w, int h) {
        hoveredGizmoAxis = -1;
        hoveredViewportBone = null;
        if (!hover(mx, my, x, y, w, h)) return;

        float bestAxisDist = 10f;
        int maxAxes = toolMode == ToolMode.BEND ? 2 : 3;
        for (int a = 0; a < maxAxes; a++) {
            float d = distToSegment(mx, my, gizmoOriginX, gizmoOriginY, gizmoTipX[a], gizmoTipY[a]);
            if (d < bestAxisDist) {
                bestAxisDist = d;
                hoveredGizmoAxis = a;
            }
        }
        if (hoveredGizmoAxis >= 0) return;

        float bestScore = 15f;
        for (ProjectedBone pb : projectedBones) {
            if (!DOC.isBoneVisible(pb.bone)) continue;
            float radius = (pb.bone == BoneId.BODY || pb.bone == BoneId.RIGHT_ITEM || pb.bone == BoneId.LEFT_ITEM) ? 9f : 14f;
            float d = distToSegment(mx, my, pb.sx, pb.sy, pb.ex, pb.ey);
            if (d <= radius && d < bestScore) {
                bestScore = d;
                hoveredViewportBone = pb.bone;
            }
        }
    }

    private static void renderSkeletonOverlayAndGizmo(GuiGraphics g, Font font, int mx, int my) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 310f);

        if (hoveredViewportBone != null && hoveredViewportBone != selectedBone) {
            for (ProjectedBone pb : projectedBones) {
                if (pb.bone == hoveredViewportBone) {
                    drawSmoothLine(g, pb.sx, pb.sy, pb.ex, pb.ey, 2.0f, 0xBBFFFFFF);
                    int jx = Math.round(pb.sx), jy = Math.round(pb.sy);
                    g.fill(jx - 3, jy - 3, jx + 3, jy + 3, pb.bone.color);
                    g.drawString(font, Messages.get(pb.bone.langKey), mx + 8, my - 10, pb.bone.color, true);
                    break;
                }
            }
        }

        for (ProjectedBone pb : projectedBones) {
            if (pb.bone == selectedBone) {
                drawSmoothLine(g, pb.sx, pb.sy, pb.ex, pb.ey, 2.0f, (0xAA000000) | (selectedBone.color & 0x00FFFFFF));
                break;
            }
        }

        int[] axisColors = toolMode == ToolMode.BEND
                ? new int[]{0xFFA855F7, 0xFFEC4899, 0xFF3B82F6}
                : new int[]{0xFFEF4444, 0xFF22C55E, 0xFF3B82F6};
        String[] axisNames = toolMode == ToolMode.BEND ? new String[]{"Dir", "Bend", "Z"} : new String[]{"X", "Y", "Z"};
        int maxAxes = toolMode == ToolMode.BEND ? 2 : 3;

        for (int a = 0; a < maxAxes; a++) {
            boolean active = (dragKind == DragKind.GIZMO_POSE && dragGizmoAxis == a)
                    || hoveredGizmoAxis == a || axisLock == a;
            int col = active ? 0xFFFFE066 : axisColors[a];
            drawSmoothLine(g, gizmoOriginX, gizmoOriginY, gizmoTipX[a], gizmoTipY[a], active ? 2.2f : 1.5f, col);
            int tx = Math.round(gizmoTipX[a]);
            int ty = Math.round(gizmoTipY[a]);
            g.fill(tx - 3, ty - 3, tx + 3, ty + 3, col);
            if (active) {
                g.drawString(font, axisNames[a], tx + 5, ty - 4, col, true);
            }
        }
        int ox = Math.round(gizmoOriginX);
        int oy = Math.round(gizmoOriginY);
        g.fill(ox - 3, oy - 3, ox + 3, oy + 3, GOLD);
        g.fill(ox - 1, oy - 1, ox + 1, oy + 1, 0xFF0E1420);
        g.flush();

        g.pose().popPose();
    }

    // -------------------------------------------------------------------------
    //  4. ПРАВАЯ ПАНЕЛЬ: ИНСПЕКТОР КОСТИ, ГРАФИК EASING И НАСТРОЙКИ
    // -------------------------------------------------------------------------

    private static void renderRightInspector(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my) {
        g.fill(x, y, x + w, y + h, SURFACE);
        border(g, x, y, w, h, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + 17, BAR);

        String boneTitle = Messages.get(selectedBone.langKey);
        g.fill(x + 6, y + 5, x + 10, y + 13, selectedBone.color);
        g.drawString(font, trim(font, boneTitle, w - 64), x + 14, y + 5, GOLD, false);

        int curTick = Math.round(playhead);
        drawBtn(g, font, mx, my, x + w - 50, y + 2, 24, 14, "+◆", "bone:key_all",
                Messages.get("studio.anim.tip.insert_key") + " [I]", DOC.boneHasKeyAt(selectedBone, curTick), GOLD);
        drawBtn(g, font, mx, my, x + w - 24, y + 2, 21, 14, "↺", "bone:reset",
                Messages.get("studio.anim.tip.reset_bone"), false, RED);

        if (activeFieldIdx < 0) {
            syncInspectorFromDoc(false);
        }

        g.enableScissor(x + 1, y + 18, x + w - 1, y + h - 1);
        int cy = y + 21;

        g.drawString(font, Messages.get("studio.anim.sec.rot"), x + 6, cy, MUTED, false);
        cy += 11;
        cy = renderChannelRow(g, font, mx, my, x + 6, cy, w - 12, 0, Channel.ROT_X, "X");
        cy = renderChannelRow(g, font, mx, my, x + 6, cy, w - 12, 1, Channel.ROT_Y, "Y");
        cy = renderChannelRow(g, font, mx, my, x + 6, cy, w - 12, 2, Channel.ROT_Z, "Z");
        cy += 3;

        g.drawString(font, Messages.get("studio.anim.sec.pos"), x + 6, cy, MUTED, false);
        cy += 11;
        cy = renderChannelRow(g, font, mx, my, x + 6, cy, w - 12, 3, Channel.POS_X, "X");
        cy = renderChannelRow(g, font, mx, my, x + 6, cy, w - 12, 4, Channel.POS_Y, "Y");
        cy = renderChannelRow(g, font, mx, my, x + 6, cy, w - 12, 5, Channel.POS_Z, "Z");
        cy += 3;

        if (selectedBone.bendable) {
            boolean bendy = ModList.get().isLoaded("bendylib");
            g.drawString(font, "Bendy-lib (°)", x + 6, cy, bendy ? PURPLE : MUTED, false);
            cy += 11;
            cy = renderChannelRow(g, font, mx, my, x + 6, cy, w - 12, 6, Channel.BEND_DIR, "D");
            cy = renderChannelRow(g, font, mx, my, x + 6, cy, w - 12, 7, Channel.BEND_ANG, "B");
            cy += 3;
        }

        g.fill(x + 6, cy, x + w - 6, cy + 1, EDGE);
        cy += 4;

        g.drawString(font, "Easing:", x + 6, cy + 3, MUTED, false);
        int graphW = 46;
        int graphH = 28;
        int graphX = x + w - graphW - 6;
        int graphY = cy;

        int selW = Math.max(58, graphX - (x + 6) - 4);
        easeBtnX = x + 6;
        easeBtnY = cy + 13;
        easeBtnW = selW;
        drawBtn(g, font, mx, my, easeBtnX, easeBtnY, easeBtnW, 15, activeEase.name(), "ease:open",
                Messages.get("studio.anim.tip.ease"), easeDropdown, ACCENT);

        renderEaseCurveGraph(g, graphX, graphY, graphW, graphH, activeEase);
        cy += graphH + 5;

        if (cy + 34 <= y + h) {
            g.fill(x + 6, cy, x + w - 6, cy + 1, EDGE);
            cy += 4;
            int halfW = (w - 16) / 2;
            g.drawString(font, "End:", x + 6, cy + 3, MUTED, false);
            int efX = x + 30;
            int efW = Math.max(26, halfW - 24);
            endTickField.render(g, font, efX, cy, efW, 13, activeFieldIdx == 8, "40", 0xFF0C1119,
                    activeFieldIdx == 8 ? GOLD : EDGE, TEXT, MUTED);
            fieldBoxes.add(new FieldBox(8, efX, cy, efW, 13, endTickField, null));

            int fx = x + 8 + halfW;
            g.drawString(font, "Fade:", fx, cy + 3, MUTED, false);
            int ffX = fx + 28;
            int ffW = Math.max(22, x + w - 6 - ffX);
            fadeField.render(g, font, ffX, cy, ffW, 13, activeFieldIdx == 9, "2", 0xFF0C1119,
                    activeFieldIdx == 9 ? GOLD : EDGE, TEXT, MUTED);
            fieldBoxes.add(new FieldBox(9, ffX, cy, ffW, 13, fadeField, null));
            cy += 16;

            drawBtn(g, font, mx, my, x + 6, cy, halfW, 14,
                    (DOC.looped ? "☑ " : "☐ ") + Messages.get("studio.anim.loop"),
                    "anim:loop", Messages.get("studio.anim.tip.loop"), DOC.looped, GREEN);
            g.drawString(font, "Ret:", fx, cy + 3, MUTED, false);
            int rfX = fx + 24;
            int rfW = Math.max(24, x + w - 6 - rfX);
            returnTickField.render(g, font, rfX, cy, rfW, 14, activeFieldIdx == 10, "0", 0xFF0C1119,
                    activeFieldIdx == 10 ? GOLD : EDGE, TEXT, MUTED);
            fieldBoxes.add(new FieldBox(10, rfX, cy, rfW, 14, returnTickField, null));
        }

        g.disableScissor();
    }

    private static int renderChannelRow(GuiGraphics g, Font font, int mx, int my,
                                        int x, int y, int w, int fieldIdx, Channel ch, String tag) {
        int curTick = Math.round(playhead);
        boolean hasKeyHere = DOC.track(selectedBone, ch).containsKey(curTick);

        boolean tagHot = hover(mx, my, x, y, 13, 13);
        g.fill(x, y, x + 13, y + 13, tagHot ? ROW_HOT : 0xFF101621);
        border(g, x, y, 13, 13, ch.axisColor);
        g.drawString(font, tag, x + 4, y + 3, ch.axisColor, false);
        btns.add(new BtnHit(x, y, 13, 13, "scrub:" + ch.name(), Messages.get("studio.anim.tip.scrub")));

        int minusX = x + 15;
        drawBtn(g, font, mx, my, minusX, y, 11, 13, "-", "step:-:" + ch.name(), null, false, MUTED);

        int keyBtnX = x + w - 13;
        drawBtn(g, font, mx, my, keyBtnX, y, 13, 13, "◆", "kftog:" + ch.name(),
                Messages.get("studio.anim.tip.kftog"), hasKeyHere, hasKeyHere ? GOLD : MUTED);

        int plusX = keyBtnX - 13;
        drawBtn(g, font, mx, my, plusX, y, 11, 13, "+", "step:+:" + ch.name(), null, false, MUTED);

        int fldX = minusX + 13;
        int fldW = Math.max(28, plusX - fldX - 2);
        StudioTextField tf = channelFields[fieldIdx];
        tf.render(g, font, fldX, y, fldW, 13, activeFieldIdx == fieldIdx, "0", 0xFF0C1119,
                activeFieldIdx == fieldIdx ? GOLD : (hasKeyHere ? 0xFF5A4A24 : EDGE), TEXT, MUTED);
        fieldBoxes.add(new FieldBox(fieldIdx, fldX, y, fldW, 13, tf, ch));

        return y + 14;
    }

    private static void renderEaseCurveGraph(GuiGraphics g, int x, int y, int w, int h, EaseCurve curve) {
        g.fill(x, y, x + w, y + h, 0xFF0B1018);
        border(g, x, y, w, h, EDGE);
        int padX = 4, padY = 5;
        int gw = w - padX * 2;
        int gh = h - padY * 2;
        g.fill(x + padX, y + padY, x + padX + gw, y + padY + 1, 0x33425474);
        g.fill(x + padX, y + padY + gh, x + padX + gw, y + padY + gh + 1, 0x33425474);

        float prevX = x + padX;
        float prevY = y + padY + gh;
        int steps = 16;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            float v = curve.eval(t);
            float sx = x + padX + t * gw;
            float sy = Math.max(y + 1, Math.min(y + h - 2, y + padY + gh - v * gh));
            drawSmoothLine(g, prevX, prevY, sx, sy, 1.3f, ACCENT);
            prevX = sx;
            prevY = sy;
        }
        float animT = (System.nanoTime() / 1_000_000L % 1400L) / 1400f;
        float dotX = x + padX + animT * gw;
        float dotY = Math.max(y + 1, Math.min(y + h - 2, y + padY + gh - curve.eval(animT) * gh));
        g.fill(Math.round(dotX) - 1, Math.round(dotY) - 1, Math.round(dotX) + 2, Math.round(dotY) + 2, GOLD);
    }

    // -------------------------------------------------------------------------
    //  5. НИЖНЯЯ ПАНЕЛЬ: МНОГОДОРОЖЕЧНЫЙ ТАЙМЛАЙН КЛЮЧЕВЫХ КАДРОВ
    // -------------------------------------------------------------------------

    private static void renderTimeline(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my) {
        g.fill(x, y, x + w, y + h, SURFACE);
        border(g, x, y, w, h, EDGE);

        int barH = 20;
        g.fill(x + 1, y + 1, x + w - 1, y + barH, BAR);
        g.fill(x + 1, y + barH, x + w - 1, y + barH + 1, EDGE);

        int bx = x + 5;
        int by = y + 3;
        bx = drawBtn(g, font, mx, my, bx, by, 16, 14, "|<", "tl:start", "[Home]", false, TEXT) + 2;
        bx = drawBtn(g, font, mx, my, bx, by, 15, 14, "<", "tl:prev", "[←]", false, TEXT) + 2;
        bx = drawBtn(g, font, mx, my, bx, by, 22, 14, playing ? "||" : "▶", "tl:play",
                "[Space]", playing, GREEN) + 2;
        bx = drawBtn(g, font, mx, my, bx, by, 16, 14, "■", "tl:stop", "Stop", false, RED) + 2;
        bx = drawBtn(g, font, mx, my, bx, by, 15, 14, ">", "tl:next", "[→]", false, TEXT) + 2;
        bx = drawBtn(g, font, mx, my, bx, by, 16, 14, ">|", "tl:end", "[End]", false, TEXT) + 6;

        String timeStr = "T: " + formatOneDec(playhead) + " / " + DOC.endTick + " (" + formatOneDec(playhead / 20f) + "s)";
        g.drawString(font, timeStr, bx, y + 6, GOLD, false);
        bx += font.width(timeStr) + 8;

        String spdLbl = formatSpeed(playbackSpeed);
        bx = drawBtn(g, font, mx, my, bx, by, font.width(spdLbl) + 10, 14, spdLbl, "tl:speed",
                Messages.get("studio.anim.tip.speed"), false, ACCENT) + 4;

        String modeLbl = expandChannels
                ? Messages.get("studio.anim.tl.channels")
                : Messages.get("studio.anim.tl.bones");
        bx = drawBtn(g, font, mx, my, bx, by, font.width(modeLbl) + 10, 14, modeLbl, "tl:mode",
                Messages.get("studio.anim.tip.tl_mode"), expandChannels, GOLD) + 4;

        int rx = x + w - 5;
        rx -= 16;
        drawBtn(g, font, mx, my, rx, by, 16, 14, "+", "tl:zoom_in", "Zoom +", false, TEXT);
        rx -= 18;
        drawBtn(g, font, mx, my, rx, by, 16, 14, "-", "tl:zoom_out", "Zoom -", false, TEXT);
        if (!selectedKeys.isEmpty() && rx - 30 > bx) {
            String delLbl = "🗑 " + selectedKeys.size();
            int dw = font.width(delLbl) + 10;
            rx -= (dw + 4);
            drawBtn(g, font, mx, my, rx, by, dw, 14, delLbl, "tl:del_sel", "[Delete]", false, RED);
        }

        int labelColW = 114;
        int rulerY = y + barH + 1;
        int rulerH = 14;
        tlTrackX = x + labelColW;
        tlTrackW = Math.max(40, w - labelColW - 2);
        tlTrackY = rulerY + rulerH;
        tlTrackH = Math.max(20, y + h - tlTrackY - 1);

        g.fill(x + 1, rulerY, tlTrackX, rulerY + rulerH, 0xFF121824);
        g.drawString(font, expandChannels ? trim(font, Messages.get(selectedBone.langKey), labelColW - 8) : "Track",
                x + 6, rulerY + 3, MUTED, false);
        g.fill(tlTrackX - 1, rulerY, tlTrackX, y + h - 1, EDGE);

        g.fill(tlTrackX, rulerY, x + w - 1, rulerY + rulerH, 0xFF101520);
        g.fill(x + 1, tlTrackY - 1, x + w - 1, tlTrackY, EDGE);

        int maxVisTick = Math.max(DOC.stopTick + 10, (int) ((timelineScrollX + tlTrackW) / pxPerTick) + 5);
        int tickStep = pxPerTick >= 10f ? 5 : (pxPerTick >= 5f ? 10 : 20);

        g.enableScissor(tlTrackX, rulerY, x + w - 1, y + h - 1);
        for (int t = 0; t <= maxVisTick; t++) {
            int tx = tickToX(t);
            if (tx < tlTrackX - 10 || tx > x + w + 10) continue;
            if (t % tickStep == 0) {
                g.fill(tx, rulerY + 8, tx + 1, rulerY + rulerH, EDGE_HOT);
                g.fill(tx, tlTrackY, tx + 1, y + h - 1, 0x222B364A);
                g.drawString(font, Integer.toString(t), tx + 2, rulerY + 2, t == DOC.endTick ? GOLD : MUTED, false);
            } else if (pxPerTick >= 6f) {
                g.fill(tx, rulerY + 11, tx + 1, rulerY + rulerH, 0x552B364A);
            }
        }

        if (DOC.looped) {
            int rtx = tickToX(DOC.returnTick);
            g.fill(rtx, rulerY, rtx + 1, y + h - 1, 0x884CC38A);
        }
        int etx = tickToX(DOC.endTick);
        g.fill(etx, rulerY, etx + 1, y + h - 1, 0xAAF0B85B);
        g.disableScissor();

        int rowH = 13;
        int rowCount = expandChannels ? (selectedBone.bendable ? 8 : 6) : BoneId.values().length;
        int maxScrollY = Math.max(0, rowCount * rowH - tlTrackH);
        timelineScrollY = Math.max(0, Math.min(timelineScrollY, maxScrollY));

        g.enableScissor(x + 1, tlTrackY, x + w - 1, y + h - 1);
        for (int i = 0; i < rowCount; i++) {
            int ry = tlTrackY + i * rowH - timelineScrollY;
            if (ry + rowH < tlTrackY || ry > y + h) continue;

            boolean even = (i % 2 == 0);
            boolean rowSel = !expandChannels && BoneId.values()[i] == selectedBone;
            g.fill(x + 1, ry, tlTrackX - 1, ry + rowH, rowSel ? 0xFF213047 : (even ? 0xFF141B27 : 0xFF111722));
            g.fill(tlTrackX, ry, x + w - 1, ry + rowH, rowSel ? 0xFF192538 : (even ? 0xFF0E131D : 0xFF0B1018));

            if (expandChannels) {
                Channel ch = Channel.values()[i];
                g.fill(x + 3, ry + 3, x + 6, ry + rowH - 3, ch.axisColor);
                g.drawString(font, ch.shortLabel, x + 9, ry + 3, TEXT, false);
                renderChannelDiamonds(g, selectedBone, ch, ry + rowH / 2);
            } else {
                BoneId b = BoneId.values()[i];
                g.fill(x + 3, ry + 2, x + 5, ry + rowH - 2, b.color);
                g.drawString(font, trim(font, Messages.get(b.langKey), labelColW - 10),
                        x + 8, ry + 3, rowSel ? GOLD : TEXT, false);
                btns.add(new BtnHit(x + 1, ry, labelColW - 2, rowH, "bone:" + b.name(), null));
                renderBoneSummaryDiamonds(g, b, ry + rowH / 2);
            }
        }

        if (dragKind == DragKind.MARQUEE_SELECT) {
            int bx0 = Math.min(dragStartX, dragCurX);
            int by0 = Math.min(dragStartY, dragCurY);
            int bw = Math.abs(dragCurX - dragStartX);
            int bh = Math.abs(dragCurY - dragStartY);
            g.fill(bx0, by0, bx0 + bw, by0 + bh, 0x3858A6FF);
            border(g, bx0, by0, Math.max(1, bw), Math.max(1, bh), ACCENT);
        }
        g.disableScissor();

        int phX = Math.round(tlTrackX + playhead * pxPerTick - timelineScrollX);
        if (phX >= tlTrackX && phX < x + w - 1) {
            g.fill(phX - 3, rulerY, phX + 4, rulerY + 5, RED);
            g.fill(phX, rulerY, phX + 1, y + h - 1, RED);
        }
    }

    private static void renderChannelDiamonds(GuiGraphics g, BoneId bone, Channel ch, int centerY) {
        int deltaTick = dragKind == DragKind.MOVE_KEYS ? xToTick(dragCurX) - dragKeyOriginTick : 0;
        for (KeyPoint kp : DOC.track(bone, ch).values()) {
            KeyRef ref = new KeyRef(bone, ch, kp.tick());
            boolean sel = selectedKeys.contains(ref);
            int drawTick = sel ? Math.max(0, kp.tick() + deltaTick) : kp.tick();
            int dx = tickToX(drawTick);
            if (dx < tlTrackX - 4 || dx > rootX + rootW) continue;
            drawDiamond(g, dx, centerY, sel ? GOLD : ch.axisColor, sel);
            diamondHits.add(new DiamondHit(ref, dx, centerY));
        }
    }

    private static void renderBoneSummaryDiamonds(GuiGraphics g, BoneId bone, int centerY) {
        int deltaTick = dragKind == DragKind.MOVE_KEYS ? xToTick(dragCurX) - dragKeyOriginTick : 0;
        Set<Integer> ticks = new LinkedHashSet<>();
        for (Channel ch : Channel.values()) {
            ticks.addAll(DOC.track(bone, ch).keySet());
        }
        for (int t : ticks) {
            boolean anySel = false;
            KeyRef firstRef = null;
            for (Channel ch : Channel.values()) {
                if (DOC.track(bone, ch).containsKey(t)) {
                    KeyRef r = new KeyRef(bone, ch, t);
                    if (firstRef == null) firstRef = r;
                    if (selectedKeys.contains(r)) anySel = true;
                }
            }
            int drawTick = anySel ? Math.max(0, t + deltaTick) : t;
            int dx = tickToX(drawTick);
            if (dx < tlTrackX - 4 || dx > rootX + rootW) continue;
            drawDiamond(g, dx, centerY, anySel ? GOLD : bone.color, anySel);
            if (firstRef != null) {
                diamondHits.add(new DiamondHit(firstRef, dx, centerY));
            }
        }
    }

    private static void drawDiamond(GuiGraphics g, int cx, int cy, int col, boolean sel) {
        if (sel) {
            g.fill(cx - 4, cy - 1, cx + 5, cy + 2, 0xFFFFFFFF);
            g.fill(cx - 1, cy - 4, cx + 2, cy + 5, 0xFFFFFFFF);
        }
        g.fill(cx - 3, cy - 1, cx + 4, cy + 2, col);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, col);
        g.fill(cx - 1, cy - 3, cx + 2, cy + 4, col);
    }

    // -------------------------------------------------------------------------
    //  6. МОДАЛЬНЫЕ ОКНА
    // -------------------------------------------------------------------------

    private static void renderOpenModal(GuiGraphics g, Font font, int mx, int my) {
        int mw = Math.min(310, rootW - 24);
        int mh = Math.min(210, rootH - 24);
        int mx0 = rootX + (rootW - mw) / 2;
        int my0 = rootY + (rootH - mh) / 2;

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(rootX, rootY, rootX + rootW, rootY + rootH, 0x88000000);
        g.fill(mx0, my0, mx0 + mw, my0 + mh, 0xF8131A26);
        border(g, mx0, my0, mw, mh, GOLD);
        g.fill(mx0 + 1, my0 + 1, mx0 + mw - 1, my0 + 19, BAR);
        g.drawString(font, Messages.get("studio.anim.open_title"), mx0 + 8, my0 + 6, GOLD, false);
        drawBtn(g, font, mx, my, mx0 + mw - 42, my0 + 3, 20, 14, "⟳", "modal:refresh", null, false, ACCENT);
        drawBtn(g, font, mx, my, mx0 + mw - 19, my0 + 3, 16, 14, "x", "modal:close", null, false, RED);

        int listY = my0 + 22;
        int listH = mh - 26;
        if (serverAnimFiles.isEmpty()) {
            g.drawCenteredString(font, Messages.get("studio.anim.open_empty"), mx0 + mw / 2, listY + listH / 2 - 4, MUTED);
        } else {
            int maxSc = Math.max(0, serverAnimFiles.size() * 16 - listH);
            openModalScroll = Math.max(0, Math.min(openModalScroll, maxSc));
            g.enableScissor(mx0 + 2, listY, mx0 + mw - 2, listY + listH);
            int ry = listY - openModalScroll;
            for (StudioExplorerPacket.Entry e : serverAnimFiles) {
                if (ry + 16 >= listY && ry <= listY + listH) {
                    boolean hot = hover(mx, my, mx0 + 4, ry, mw - 28, 15);
                    g.fill(mx0 + 4, ry, mx0 + mw - 24, ry + 15, hot ? ROW_HOT : ROW);
                    String shortDisp = e.name().replaceFirst("^resources/assets/", "");
                    g.drawString(font, trim(font, shortDisp, mw - 36), mx0 + 8, ry + 4, hot ? GOLD : TEXT, false);
                    btns.add(new BtnHit(mx0 + 4, ry, mw - 28, 15, "openfile:" + e.name(), null));
                    drawBtn(g, font, mx, my, mx0 + mw - 22, ry + 1, 16, 13, "×", "delfile:" + e.name(), null, false, RED);
                }
                ry += 16;
            }
            g.disableScissor();
        }
        g.pose().popPose();
    }

    private static void renderNewModal(GuiGraphics g, Font font, int mx, int my) {
        int mw = 236, mh = 104;
        int mx0 = rootX + (rootW - mw) / 2;
        int my0 = rootY + (rootH - mh) / 2;

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(rootX, rootY, rootX + rootW, rootY + rootH, 0x88000000);
        g.fill(mx0, my0, mx0 + mw, my0 + mh, 0xF8131A26);
        border(g, mx0, my0, mw, mh, GREEN);
        g.fill(mx0 + 1, my0 + 1, mx0 + mw - 1, my0 + 19, BAR);
        g.drawString(font, Messages.get("studio.anim.new_title"), mx0 + 8, my0 + 6, GREEN, false);
        drawBtn(g, font, mx, my, mx0 + mw - 19, my0 + 3, 16, 14, "x", "modal:close", null, false, RED);

        g.drawString(font, "Namespace:", mx0 + 10, my0 + 28, MUTED, false);
        newNsField.render(g, font, mx0 + 78, my0 + 25, mw - 88, 15, activeFieldIdx == 12, "scriptcraft",
                0xFF0C1119, activeFieldIdx == 12 ? GOLD : EDGE, TEXT, MUTED);
        fieldBoxes.add(new FieldBox(12, mx0 + 78, my0 + 25, mw - 88, 15, newNsField, null));

        g.drawString(font, Messages.get("studio.anim.name") + ":", mx0 + 10, my0 + 48, MUTED, false);
        newNameField.render(g, font, mx0 + 78, my0 + 45, mw - 88, 15, activeFieldIdx == 13, "my_emote",
                0xFF0C1119, activeFieldIdx == 13 ? GOLD : EDGE, TEXT, MUTED);
        fieldBoxes.add(new FieldBox(13, mx0 + 78, my0 + 45, mw - 88, 15, newNameField, null));

        String createLbl = Messages.get("studio.anim.create_btn");
        drawBtn(g, font, mx, my, mx0 + mw - 86, my0 + mh - 22, 76, 16, createLbl, "modal:create_confirm", null, true, GREEN);
        g.pose().popPose();
    }

    private static void renderEaseDropdown(GuiGraphics g, Font font, int mx, int my) {
        EaseCurve[] all = EaseCurve.values();
        int dw = Math.max(118, easeBtnW);
        int dh = 148;
        int dx = Math.min(easeBtnX, rootX + rootW - dw - 6);
        int dy = Math.max(rootY + 24, easeBtnY - dh - 2);

        g.pose().pushPose();
        g.pose().translate(0, 0, 410);
        g.fill(dx, dy, dx + dw, dy + dh, 0xF8111722);
        border(g, dx, dy, dw, dh, ACCENT);
        int maxSc = Math.max(0, all.length * 13 - (dh - 4));
        easeModalScroll = Math.max(0, Math.min(easeModalScroll, maxSc));
        g.enableScissor(dx + 1, dy + 2, dx + dw - 1, dy + dh - 2);
        int ry = dy + 2 - easeModalScroll;
        for (EaseCurve c : all) {
            if (ry + 13 >= dy && ry <= dy + dh) {
                boolean on = c == activeEase;
                boolean hot = hover(mx, my, dx + 2, ry, dw - 4, 13);
                if (on || hot) g.fill(dx + 2, ry, dx + dw - 2, ry + 13, on ? 0xFF233856 : ROW_HOT);
                g.drawString(font, c.name(), dx + 6, ry + 2, on ? GOLD : TEXT, false);
                btns.add(new BtnHit(dx + 2, ry, dw - 4, 13, "setease:" + c.name(), null));
            }
            ry += 13;
        }
        g.disableScissor();
        g.pose().popPose();
    }

    private static void renderHelpPopup(GuiGraphics g, Font font, int mx, int my) {
        String[] lines = {
                Messages.get("studio.anim.hk.title"),
                "Space — Play / Pause   |   ← / → — ±1 tick   |   Home / End",
                "R / G / B — Rotate / Move / Bend   |   X / Y / Z — Axis lock",
                "1..9 — Select bone   |   F — Reset camera   |   I / K — Keyframe",
                "LMB (3D) — Pick bone / Drag Gizmo / Orbit   |   MMB — Pan",
                "LMB (Timeline) — Box-select / Drag keys   |   Del — Remove keys",
                "Ctrl+M — Mirror Pose   |   Ctrl+C / Ctrl+V — Copy / Paste keys",
                "Ctrl+Z / Ctrl+Y — Undo / Redo   |   Ctrl+S — Save to server"
        };
        int mw = 344;
        int mh = lines.length * 13 + 18;
        int mx0 = rootX + (rootW - mw) / 2;
        int my0 = rootY + (rootH - mh) / 2;

        g.pose().pushPose();
        g.pose().translate(0, 0, 420);
        g.fill(rootX, rootY, rootX + rootW, rootY + rootH, 0x77000000);
        g.fill(mx0, my0, mx0 + mw, my0 + mh, 0xF8121926);
        border(g, mx0, my0, mw, mh, GOLD);
        drawBtn(g, font, mx, my, mx0 + mw - 18, my0 + 3, 15, 13, "x", "modal:close", null, false, RED);
        for (int i = 0; i < lines.length; i++) {
            g.drawString(font, lines[i], mx0 + 10, my0 + 8 + i * 13, i == 0 ? GOLD : TEXT, false);
        }
        g.pose().popPose();
    }

    // =========================================================================
    //  МЫШЬ И КЛАВИАТУРА
    // =========================================================================

    public static boolean mouseClicked(Font font, int mx, int my, int button) {
        ensureInit();
        commitActiveField();
        activeFieldIdx = -1;

        for (FieldBox fb : fieldBoxes) {
            if (hover(mx, my, fb.x, fb.y, fb.w, fb.h)) {
                activeFieldIdx = fb.idx;
                fb.field.mouseClicked(font, mx, my, button);
                return true;
            }
        }

        for (int i = btns.size() - 1; i >= 0; i--) {
            BtnHit b = btns.get(i);
            if (hover(mx, my, b.x, b.y, b.w, b.h)) {
                if (b.id.startsWith("scrub:") && button == 0) {
                    Channel ch = Channel.valueOf(b.id.substring("scrub:".length()));
                    DOC.pushUndo();
                    scrubChannel = ch;
                    scrubInitVal = DOC.eval(selectedBone, ch, playhead);
                    dragStartX = mx;
                    dragKind = DragKind.VALUE_SCRUB;
                    return true;
                }
                handleButton(b.id);
                return true;
            }
        }

        if (openModal || newModal || helpPopup) {
            openModal = newModal = helpPopup = false;
            return true;
        }
        if (easeDropdown) {
            easeDropdown = false;
            return true;
        }

        if (Math.abs(my - tlY) <= 3 && mx >= rootX && mx < rootX + rootW) {
            dragKind = DragKind.TIMELINE_RESIZE;
            dragStartY = my;
            dragStartTimelineH = timelineH;
            return true;
        }

        if (hover(mx, my, vpX, mainY, vpW, mainH)) {
            if (button == 2 || (button == 1 && Screen.hasShiftDown())) {
                dragKind = DragKind.PAN;
                dragStartX = mx;
                dragStartY = my;
                dragStartPanX = camPanX;
                dragStartPanY = camPanY;
                return true;
            }
            if (button == 1) {
                dragKind = DragKind.ORBIT;
                dragStartX = mx;
                dragStartY = my;
                dragStartYaw = camYaw;
                dragStartPitch = camPitch;
                return true;
            }
            if (button == 0) {
                if (hoveredGizmoAxis >= 0 || axisLock >= 0) {
                    startGizmoDrag(mx, my, hoveredGizmoAxis >= 0 ? hoveredGizmoAxis : axisLock);
                    return true;
                }
                if (hoveredViewportBone != null) {
                    if (hoveredViewportBone == selectedBone) {
                        startGizmoDrag(mx, my, -1);
                    } else {
                        selectedBone = hoveredViewportBone;
                        syncInspectorFromDoc(true);
                    }
                    return true;
                }
                dragKind = DragKind.ORBIT;
                dragStartX = mx;
                dragStartY = my;
                dragStartYaw = camYaw;
                dragStartPitch = camPitch;
                return true;
            }
        }

        int rulerY = tlY + 21;
        if (hover(mx, my, tlTrackX, rulerY, tlTrackW, 14)) {
            playing = false;
            playhead = Math.max(0f, (mx - tlTrackX + timelineScrollX) / pxPerTick);
            syncInspectorFromDoc(false);
            dragKind = DragKind.SCRUB_PLAYHEAD;
            return true;
        }

        if (hover(mx, my, tlTrackX, tlTrackY, tlTrackW, tlTrackH)) {
            if (button == 1) {
                playing = false;
                playhead = Math.max(0f, (mx - tlTrackX + timelineScrollX) / pxPerTick);
                syncInspectorFromDoc(false);
                dragKind = DragKind.SCRUB_PLAYHEAD;
                return true;
            }
            DiamondHit hit = findDiamondAt(mx, my);
            if (hit != null) {
                boolean shift = Screen.hasShiftDown();
                if (!expandChannels) {
                    List<KeyRef> group = new ArrayList<>();
                    for (Channel ch : Channel.values()) {
                        if (DOC.track(hit.ref.bone(), ch).containsKey(hit.ref.tick())) {
                            group.add(new KeyRef(hit.ref.bone(), ch, hit.ref.tick()));
                        }
                    }
                    if (!shift && !selectedKeys.containsAll(group)) {
                        selectedKeys.clear();
                    }
                    selectedKeys.addAll(group);
                } else {
                    if (!shift && !selectedKeys.contains(hit.ref)) {
                        selectedKeys.clear();
                    }
                    if (shift && selectedKeys.contains(hit.ref)) {
                        selectedKeys.remove(hit.ref);
                    } else {
                        selectedKeys.add(hit.ref);
                    }
                }
                selectedBone = hit.ref.bone();
                playhead = hit.ref.tick();
                KeyPoint kp = DOC.track(hit.ref.bone(), hit.ref.channel()).get(hit.ref.tick());
                if (kp != null) activeEase = kp.ease();
                syncInspectorFromDoc(true);

                dragKind = DragKind.MOVE_KEYS;
                dragKeyOriginTick = xToTick(mx);
                dragCurX = mx;
                dragMovingSnapshot.clear();
                for (KeyRef r : selectedKeys) {
                    KeyPoint pt = DOC.track(r.bone(), r.channel()).get(r.tick());
                    if (pt != null) {
                        dragMovingSnapshot.add(new CopiedKey(r.bone(), r.channel(), r.tick(), pt.value(), pt.ease()));
                    }
                }
                return true;
            }

            if (!Screen.hasShiftDown()) {
                selectedKeys.clear();
            }
            dragKind = DragKind.MARQUEE_SELECT;
            dragStartX = dragCurX = mx;
            dragStartY = dragCurY = my;
            return true;
        }

        return true;
    }

    public static boolean mouseDragged(Font font, int mx, int my, int button) {
        if (activeFieldIdx >= 0) {
            for (FieldBox fb : fieldBoxes) {
                if (fb.idx == activeFieldIdx) {
                    return fb.field.mouseDragged(font, mx);
                }
            }
        }
        dragCurX = mx;
        dragCurY = my;
        switch (dragKind) {
            case ORBIT -> {
                firstPersonPreview = false;
                camYaw = dragStartYaw + (mx - dragStartX) * 0.7f;
                camPitch = Math.max(-85f, Math.min(85f, dragStartPitch + (my - dragStartY) * 0.6f));
                return true;
            }
            case PAN -> {
                camPanX = dragStartPanX + (mx - dragStartX);
                camPanY = dragStartPanY + (my - dragStartY);
                return true;
            }
            case GIZMO_POSE -> {
                applyGizmoDrag(mx, my);
                return true;
            }
            case VALUE_SCRUB -> {
                if (scrubChannel != null) {
                    float factor = scrubChannel.angle ? 0.5f : 0.08f;
                    if (Screen.hasShiftDown()) factor *= 0.2f;
                    float next = scrubInitVal + (mx - dragStartX) * factor;
                    DOC.putKey(selectedBone, scrubChannel, Math.round(playhead), next, activeEase);
                    syncInspectorFromDoc(true);
                }
                return true;
            }
            case SCRUB_PLAYHEAD -> {
                playhead = Math.max(0f, (mx - tlTrackX + timelineScrollX) / pxPerTick);
                syncInspectorFromDoc(false);
                return true;
            }
            case TIMELINE_RESIZE -> {
                timelineH = Math.max(88, Math.min(rootH / 2, dragStartTimelineH - (my - dragStartY)));
                return true;
            }
            case MARQUEE_SELECT -> {
                updateMarqueeSelection();
                return true;
            }
            case MOVE_KEYS -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public static void mouseReleased() {
        for (FieldBox fb : fieldBoxes) {
            fb.field.mouseReleased();
        }
        if (dragKind == DragKind.MOVE_KEYS && !dragMovingSnapshot.isEmpty()) {
            int delta = xToTick(dragCurX) - dragKeyOriginTick;
            if (delta != 0) {
                DOC.pushUndo();
                for (CopiedKey ck : dragMovingSnapshot) {
                    DOC.removeKey(ck.bone, ck.ch, ck.relTick);
                }
                selectedKeys.clear();
                for (CopiedKey ck : dragMovingSnapshot) {
                    int nt = Math.max(0, ck.relTick + delta);
                    DOC.putKey(ck.bone, ck.ch, nt, ck.value, ck.ease);
                    selectedKeys.add(new KeyRef(ck.bone, ck.ch, nt));
                }
                syncInspectorFromDoc(true);
            }
            dragMovingSnapshot.clear();
        }
        dragKind = DragKind.NONE;
        scrubChannel = null;
    }

    public static boolean mouseScrolled(int mx, int my, double delta) {
        int step = (int) Math.signum(delta);
        if (openModal) {
            openModalScroll = Math.max(0, openModalScroll - step * 18);
            return true;
        }
        if (easeDropdown) {
            easeModalScroll = Math.max(0, easeModalScroll - step * 16);
            return true;
        }
        if (hover(mx, my, vpX, mainY, vpW, mainH)) {
            camZoom = Math.max(0.35f, Math.min(3.2f, camZoom + step * 0.12f));
            return true;
        }
        if (hover(mx, my, tlX, tlY, tlW, tlH)) {
            if (Screen.hasControlDown()) {
                pxPerTick = Math.max(2.5f, Math.min(28f, pxPerTick + step * 1.2f));
            } else if (Screen.hasShiftDown()) {
                timelineScrollX = Math.max(0, timelineScrollX - step * 28);
            } else {
                timelineScrollY = Math.max(0, timelineScrollY - step * 14);
            }
            return true;
        }
        return false;
    }

    public static boolean keyPressed(int key) {
        ensureInit();
        boolean ctrl = Screen.hasControlDown();
        boolean shift = Screen.hasShiftDown();

        if (activeFieldIdx >= 0) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                activeFieldIdx = -1;
                syncInspectorFromDoc(true);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_TAB) {
                if (newModal && key != GLFW.GLFW_KEY_TAB) {
                    confirmCreateNew();
                } else {
                    commitActiveField();
                    activeFieldIdx = -1;
                }
                return true;
            }
            for (FieldBox fb : fieldBoxes) {
                if (fb.idx == activeFieldIdx) {
                    fb.field.keyPressed(key);
                    return true;
                }
            }
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (openModal || newModal || easeDropdown || helpPopup) {
                openModal = newModal = easeDropdown = helpPopup = false;
                return true;
            }
            if (axisLock >= 0) {
                axisLock = -1;
                return true;
            }
            return false;
        }

        if (ctrl && key == GLFW.GLFW_KEY_S) {
            saveCurrentToServer();
            return true;
        }
        if (ctrl && (key == GLFW.GLFW_KEY_Y || (shift && key == GLFW.GLFW_KEY_Z))) {
            if (DOC.redo()) syncInspectorFromDoc(true);
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Z) {
            if (DOC.undo()) syncInspectorFromDoc(true);
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_M) {
            DOC.pushUndo();
            DOC.mirrorPoseAt(Math.round(playhead), activeEase);
            syncInspectorFromDoc(true);
            ClientToasts.push(Messages.toast("studio.anim.mirrored"));
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_A) {
            selectAllKeyframes();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_C) {
            copyKeyframes();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            pasteKeyframes();
            return true;
        }

        if (key == GLFW.GLFW_KEY_SPACE) {
            togglePlay();
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT) {
            playing = false;
            playhead = Math.max(0f, Math.round(playhead) - (shift ? 5 : 1));
            syncInspectorFromDoc(false);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            playing = false;
            playhead = Math.max(0f, Math.round(playhead) + (shift ? 5 : 1));
            syncInspectorFromDoc(false);
            return true;
        }
        if (key == GLFW.GLFW_KEY_HOME) {
            playing = false;
            playhead = 0f;
            syncInspectorFromDoc(false);
            return true;
        }
        if (key == GLFW.GLFW_KEY_END) {
            playing = false;
            playhead = DOC.endTick;
            syncInspectorFromDoc(false);
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
            deleteSelectedKeys();
            return true;
        }
        if (key == GLFW.GLFW_KEY_I || key == GLFW.GLFW_KEY_K) {
            insertKeyframesForBone(selectedBone);
            return true;
        }
        if (!ctrl && key == GLFW.GLFW_KEY_R) {
            toolMode = ToolMode.ROTATE;
            return true;
        }
        if (!ctrl && key == GLFW.GLFW_KEY_G) {
            toolMode = ToolMode.MOVE;
            return true;
        }
        if (!ctrl && key == GLFW.GLFW_KEY_B) {
            toolMode = ToolMode.BEND;
            return true;
        }
        if (!ctrl && key == GLFW.GLFW_KEY_X) {
            axisLock = (axisLock == 0) ? -1 : 0;
            return true;
        }
        if (!ctrl && key == GLFW.GLFW_KEY_Y) {
            axisLock = (axisLock == 1) ? -1 : 1;
            return true;
        }
        if (!ctrl && key == GLFW.GLFW_KEY_Z) {
            axisLock = (axisLock == 2) ? -1 : 2;
            return true;
        }
        if (!ctrl && key == GLFW.GLFW_KEY_F) {
            resetCamera();
            return true;
        }
        if (!ctrl && key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            int idx = key - GLFW.GLFW_KEY_1;
            if (idx < BoneId.values().length) {
                selectedBone = BoneId.values()[idx];
                syncInspectorFromDoc(true);
            }
            return true;
        }
        return true;
    }

    public static boolean charTyped(char ch) {
        if (activeFieldIdx >= 0) {
            for (FieldBox fb : fieldBoxes) {
                if (fb.idx == activeFieldIdx) {
                    fb.field.charTyped(ch);
                    return true;
                }
            }
        }
        return false;
    }

    // =========================================================================
    //  ВНУТРЕННЯЯ ЛОГИКА ДЕЙСТВИЙ И ПРОЕКЦИОННОГО 3D-ГИЗМО
    // =========================================================================

    private static void handleButton(String id) {
        if (id.startsWith("bone:")) {
            String sub = id.substring("bone:".length());
            if ("key_all".equals(sub)) {
                insertKeyframesForBone(selectedBone);
            } else if ("reset".equals(sub)) {
                resetBoneAtPlayhead(selectedBone);
            } else {
                selectedBone = BoneId.valueOf(sub);
                syncInspectorFromDoc(true);
            }
            return;
        }
        if (id.startsWith("vis:")) {
            DOC.toggleBoneVisible(BoneId.valueOf(id.substring("vis:".length())));
            return;
        }
        if (id.startsWith("step:")) {
            String[] p = id.split(":");
            float dir = "+".equals(p[1]) ? 1f : -1f;
            if (Screen.hasShiftDown()) dir *= 0.2f;
            Channel ch = Channel.valueOf(p[2]);
            DOC.pushUndo();
            float cur = DOC.eval(selectedBone, ch, playhead);
            DOC.putKey(selectedBone, ch, Math.round(playhead), cur + dir * ch.step, activeEase);
            syncInspectorFromDoc(true);
            return;
        }
        if (id.startsWith("kftog:")) {
            Channel ch = Channel.valueOf(id.substring("kftog:".length()));
            int t = Math.round(playhead);
            DOC.pushUndo();
            if (DOC.track(selectedBone, ch).containsKey(t)) {
                DOC.removeKey(selectedBone, ch, t);
            } else {
                DOC.putKey(selectedBone, ch, t, DOC.eval(selectedBone, ch, playhead), activeEase);
            }
            syncInspectorFromDoc(true);
            return;
        }
        if (id.startsWith("setease:")) {
            activeEase = EaseCurve.valueOf(id.substring("setease:".length()));
            if (!selectedKeys.isEmpty()) {
                DOC.pushUndo();
                for (KeyRef r : selectedKeys) {
                    KeyPoint kp = DOC.track(r.bone(), r.channel()).get(r.tick());
                    if (kp != null) {
                        DOC.putKey(r.bone(), r.channel(), r.tick(), kp.value(), activeEase);
                    }
                }
            }
            easeDropdown = false;
            return;
        }
        if (id.startsWith("openfile:")) {
            String rel = id.substring("openfile:".length());
            ModNetwork.sendToServer(new StudioExplorerPacket.Action(StudioExplorerPacket.Kind.READ_FILE, rel, "$anim_open"));
            return;
        }
        if (id.startsWith("delfile:")) {
            String rel = id.substring("delfile:".length());
            ModNetwork.sendToServer(new StudioExplorerPacket.Action(StudioExplorerPacket.Kind.DELETE, rel, "$anim_del"));
            return;
        }
        if (id.startsWith("axis:")) {
            int a = Integer.parseInt(id.substring("axis:".length()));
            axisLock = (axisLock == a) ? -1 : a;
            return;
        }

        switch (id) {
            case "file:open" -> {
                openModal = !openModal;
                newModal = easeDropdown = helpPopup = false;
                if (openModal) requestServerFiles();
            }
            case "file:new" -> {
                newModal = !newModal;
                openModal = easeDropdown = helpPopup = false;
                newNsField.setValue(DOC.namespace);
                newNameField.setValue("new_anim");
                activeFieldIdx = 13;
                newNameField.selectAll();
            }
            case "file:save" -> saveCurrentToServer();
            case "modal:close" -> openModal = newModal = easeDropdown = helpPopup = false;
            case "modal:refresh" -> requestServerFiles();
            case "modal:create_confirm" -> confirmCreateNew();
            case "tool:rot" -> toolMode = ToolMode.ROTATE;
            case "tool:pos" -> toolMode = ToolMode.MOVE;
            case "tool:bend" -> toolMode = ToolMode.BEND;
            case "pose:mirror" -> {
                DOC.pushUndo();
                DOC.mirrorPoseAt(Math.round(playhead), activeEase);
                syncInspectorFromDoc(true);
                ClientToasts.push(Messages.toast("studio.anim.mirrored"));
            }
            case "vp:1p" -> firstPersonPreview = !firstPersonPreview;
            case "vp:reset_cam" -> resetCamera();
            case "ui:help" -> {
                helpPopup = !helpPopup;
                openModal = newModal = easeDropdown = false;
            }
            case "ease:open" -> {
                easeDropdown = !easeDropdown;
                openModal = newModal = helpPopup = false;
            }
            case "anim:loop" -> {
                DOC.pushUndo();
                DOC.looped = !DOC.looped;
                DOC.touch();
            }
            case "tl:play" -> togglePlay();
            case "tl:stop" -> {
                playing = false;
                playhead = 0f;
                syncInspectorFromDoc(false);
            }
            case "tl:start" -> {
                playing = false;
                playhead = 0f;
                syncInspectorFromDoc(false);
            }
            case "tl:end" -> {
                playing = false;
                playhead = DOC.endTick;
                syncInspectorFromDoc(false);
            }
            case "tl:prev" -> {
                playing = false;
                playhead = Math.max(0f, Math.round(playhead) - 1);
                syncInspectorFromDoc(false);
            }
            case "tl:next" -> {
                playing = false;
                playhead = Math.round(playhead) + 1;
                syncInspectorFromDoc(false);
            }
            case "tl:speed" -> {
                float[] speeds = {0.25f, 0.5f, 1.0f, 1.5f, 2.0f};
                int next = 0;
                for (int i = 0; i < speeds.length; i++) {
                    if (Math.abs(speeds[i] - playbackSpeed) < 0.05f) {
                        next = (i + 1) % speeds.length;
                        break;
                    }
                }
                playbackSpeed = speeds[next];
            }
            case "tl:mode" -> {
                expandChannels = !expandChannels;
                timelineScrollY = 0;
            }
            case "tl:zoom_in" -> pxPerTick = Math.min(28f, pxPerTick + 2f);
            case "tl:zoom_out" -> pxPerTick = Math.max(2.5f, pxPerTick - 2f);
            case "tl:del_sel" -> deleteSelectedKeys();
            case "act:test_world" -> {
                if (ModList.get().isLoaded("playeranimator")) {
                    PlayerAnimBridge.playInMemoryOnLocalPlayer(DOC, true, playbackSpeed);
                    ClientToasts.push(Messages.toast("studio.anim.playing_world", DOC.fullId()));
                } else {
                    ClientToasts.push(Messages.toast("studio.anim.need_pa"));
                }
            }
            case "act:stop_world" -> {
                if (ModList.get().isLoaded("playeranimator")) {
                    PlayerAnimBridge.stopOnLocalPlayer();
                    ClientToasts.push(Messages.toast("studio.anim.stopped_world"));
                } else {
                    ClientToasts.push(Messages.toast("studio.anim.need_pa"));
                }
            }
            case "act:insert_code" -> insertSnippetIntoScript();
            default -> {
            }
        }
    }

    private static void confirmCreateNew() {
        String ns = newNsField.value().strip().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "");
        String nm = newNameField.value().strip().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "");
        if (ns.isEmpty()) ns = "scriptcraft";
        if (nm.endsWith(".json")) nm = nm.substring(0, nm.length() - 5);
        if (nm.isEmpty()) nm = "new_anim";

        DOC.pushUndo();
        DOC.clearTracks();
        DOC.namespace = ns;
        DOC.fileName = nm;
        DOC.displayName = nm;
        DOC.beginTick = 2;
        DOC.endTick = 40;
        DOC.stopTick = 45;
        DOC.looped = false;
        DOC.returnTick = 0;
        DOC.uuid = java.util.UUID.randomUUID();
        DOC.markSaved();
        playhead = 0f;
        playing = false;
        selectedKeys.clear();
        newModal = false;
        activeFieldIdx = -1;
        syncInspectorFromDoc(true);
    }

    private static void insertSnippetIntoScript() {
        String line = "play animation \"" + DOC.fullId() + "\" on player for all players first person";
        Minecraft mc = Minecraft.getInstance();
        if (mc.keyboardHandler != null) {
            mc.keyboardHandler.setClipboard(line);
        }
        ScriptDocument target = StudioSession.lastScriptTab != null ? StudioSession.docs.get(StudioSession.lastScriptTab) : null;
        if (target != null && target.loaded && !target.genericFile && !target.isImage) {
            target.insert(line);
            StudioSession.active = target.name;
            ClientToasts.push(Messages.toast("studio.anim.inserted_code", target.name));
        } else {
            ClientToasts.push(Messages.toast("studio.anim.copied_code", line));
        }
    }

    /**
     * Запускает перетаскивание Гизмо и фиксирует точные 2D-производные (∂экран / ∂канал)
     * в точке захвата, чтобы перетаскивание мышью ВСЕГДА двигало модель 1-в-1 за курсором!
     */
    private static void startGizmoDrag(int mx, int my, int axis) {
        DOC.pushUndo();
        dragKind = DragKind.GIZMO_POSE;
        dragStartX = mx;
        dragStartY = my;
        dragGizmoAxis = axis;

        Channel[] chs = activeToolChannels();
        dragInitA = chs.length > 0 ? DOC.eval(selectedBone, chs[0], playhead) : 0f;
        dragInitB = chs.length > 1 ? DOC.eval(selectedBone, chs[1], playhead) : 0f;
        dragInitC = chs.length > 2 ? DOC.eval(selectedBone, chs[2], playhead) : 0f;

        boolean useTip = toolMode != ToolMode.MOVE;
        for (int i = 0; i < chs.length; i++) {
            Channel ch = chs[i];
            float eps = ch.angle ? 2.0f : 1.0f;
            Vector4f p0 = evalBoneFeatureScreen(lastEntityToScreen, selectedBone, selectedBone, ch, 0f, useTip);
            Vector4f p1 = evalBoneFeatureScreen(lastEntityToScreen, selectedBone, selectedBone, ch, eps, useTip);
            dragJacX[i] = (p1.x - p0.x) / eps;
            dragJacY[i] = (p1.y - p0.y) / eps;
        }
    }

    private static void applyGizmoDrag(int mx, int my) {
        int tick = Math.round(playhead);
        float fine = Screen.hasShiftDown() ? 0.25f : 1.0f;
        float dx = (mx - dragStartX) * fine;
        float dy = (my - dragStartY) * fine;

        Channel[] chs = activeToolChannels();
        float[] inits = {dragInitA, dragInitB, dragInitC};

        if (toolMode == ToolMode.BEND && !selectedBone.bendable) {
            return;
        }

        // Одиночная выбранная ось Гизмо (0=X, 1=Y, 2=Z)
        if (dragGizmoAxis >= 0 && dragGizmoAxis < chs.length) {
            int a = dragGizmoAxis;
            float jx = dragJacX[a];
            float jy = dragJacY[a];
            float normSq = jx * jx + jy * jy;
            float deltaParam;
            if (normSq > 0.04f) {
                deltaParam = (dx * jx + dy * jy) / normSq;
            } else {
                // Ось смотрит почти точно в камеру — используем горизонтальное движение мыши
                deltaParam = dx * (chs[a].angle ? 0.6f : 0.08f);
            }
            DOC.putKey(selectedBone, chs[a], tick, inits[a] + deltaParam, activeEase);
            syncInspectorFromDoc(true);
            return;
        }

        // Свободное перетаскивание кости в 2D-плоскости экрана (без инверсии при любом угле камеры!)
        if (toolMode == ToolMode.MOVE) {
            // Решаем проекцию на X и Y (или Z, если смотрим сбоку и X вырожден)
            int horzIdx = (dragJacX[0] * dragJacX[0] + dragJacY[0] * dragJacY[0])
                    >= (dragJacX[2] * dragJacX[2] + dragJacY[2] * dragJacY[2]) ? 0 : 2;
            float[] d = solve2x2(dragJacX[horzIdx], dragJacY[horzIdx], dragJacX[1], dragJacY[1], dx, dy, 0.08f);
            DOC.putKey(selectedBone, chs[horzIdx], tick, inits[horzIdx] + d[0], activeEase);
            DOC.putKey(selectedBone, Channel.POS_Y, tick, dragInitB + d[1], activeEase);
        } else if (toolMode == ToolMode.ROTATE) {
            float[] d = solve2x2(dragJacX[0], dragJacY[0], dragJacX[2], dragJacY[2], dx, dy, 0.65f);
            DOC.putKey(selectedBone, Channel.ROT_X, tick, dragInitA + d[0], activeEase);
            DOC.putKey(selectedBone, Channel.ROT_Z, tick, dragInitC + d[1], activeEase);
        } else if (toolMode == ToolMode.BEND) {
            // В свободном Bend вертикаль/направление курсора вдоль сгиба меняет угол, поперёк — направление
            float jx = dragJacX[1], jy = dragJacY[1];
            float normSq = jx * jx + jy * jy;
            float dAng = normSq > 0.04f ? (dx * jx + dy * jy) / normSq : -dy * 0.6f;
            DOC.putKey(selectedBone, Channel.BEND_ANG, tick, dragInitB + dAng, activeEase);
        }
        syncInspectorFromDoc(true);
    }

    /** Устойчивое решение 2×2 системы J * [da, db]^T = [dx, dy]^T с защитой от вырожденности. */
    private static float[] solve2x2(float ax, float ay, float bx, float by, float dx, float dy, float fallbackScale) {
        float det = ax * by - ay * bx;
        if (Math.abs(det) > 0.08f) {
            float da = (dx * by - dy * bx) / det;
            float db = (ax * dy - ay * dx) / det;
            return new float[]{Math.max(-360f, Math.min(360f, da)), Math.max(-360f, Math.min(360f, db))};
        }
        float na = ax * ax + ay * ay;
        float nb = bx * bx + by * by;
        float da = na > 0.04f ? (dx * ax + dy * ay) / na : dy * fallbackScale;
        float db = nb > 0.04f ? (dx * bx + dy * by) / nb : dx * fallbackScale;
        return new float[]{da, db};
    }

    private static void commitActiveField() {
        if (activeFieldIdx < 0) return;
        if (activeFieldIdx < 8) {
            Channel ch = Channel.values()[activeFieldIdx];
            if (!selectedBone.bendable && ch.isBend()) return;
            Float val = parseFloatSafe(channelFields[activeFieldIdx].value());
            if (val != null) {
                float cur = DOC.eval(selectedBone, ch, playhead);
                if (Math.abs(val - cur) > 0.001f || !DOC.track(selectedBone, ch).containsKey(Math.round(playhead))) {
                    DOC.pushUndo();
                    DOC.putKey(selectedBone, ch, Math.round(playhead), val, activeEase);
                }
            }
        } else if (activeFieldIdx == 8) {
            Integer v = parseIntSafe(endTickField.value());
            if (v != null && v >= 1) {
                DOC.pushUndo();
                DOC.endTick = Math.min(2400, v);
                DOC.stopTick = Math.max(DOC.endTick + 1, DOC.endTick + Math.max(1, DOC.beginTick));
                DOC.touch();
            }
        } else if (activeFieldIdx == 9) {
            Integer v = parseIntSafe(fadeField.value());
            if (v != null && v >= 0) {
                DOC.pushUndo();
                DOC.beginTick = Math.min(100, v);
                DOC.stopTick = DOC.endTick + Math.max(1, DOC.beginTick);
                DOC.touch();
            }
        } else if (activeFieldIdx == 10) {
            Integer v = parseIntSafe(returnTickField.value());
            if (v != null && v >= 0) {
                DOC.pushUndo();
                DOC.returnTick = Math.min(DOC.endTick, v);
                DOC.touch();
            }
        }
        syncInspectorFromDoc(true);
    }

    private static void syncInspectorFromDoc(boolean force) {
        if (!force && lastInspectorSyncVer == DOC.version()
                && Math.abs(lastInspectorSyncTick - playhead) < 0.002f
                && lastInspectorSyncBone == selectedBone) {
            return;
        }
        lastInspectorSyncVer = DOC.version();
        lastInspectorSyncTick = playhead;
        lastInspectorSyncBone = selectedBone;

        for (int i = 0; i < 8; i++) {
            if (activeFieldIdx == i) continue;
            Channel ch = Channel.values()[i];
            channelFields[i].setValue(formatNum(DOC.eval(selectedBone, ch, playhead)));
        }
        if (activeFieldIdx != 8) endTickField.setValue(Integer.toString(DOC.endTick));
        if (activeFieldIdx != 9) fadeField.setValue(Integer.toString(DOC.beginTick));
        if (activeFieldIdx != 10) returnTickField.setValue(Integer.toString(DOC.returnTick));
    }

    private static void togglePlay() {
        playing = !playing;
        lastPlayNano = System.nanoTime();
        if (playing && playhead >= DOC.endTick) {
            playhead = 0f;
        }
    }

    private static void resetCamera() {
        firstPersonPreview = false;
        camYaw = 26f;
        camPitch = 16f;
        camZoom = 1.0f;
        camPanX = 0f;
        camPanY = 0f;
    }

    private static void insertKeyframesForBone(BoneId bone) {
        DOC.pushUndo();
        int t = Math.round(playhead);
        DOC.putKey(bone, Channel.ROT_X, t, DOC.eval(bone, Channel.ROT_X, playhead), activeEase);
        DOC.putKey(bone, Channel.ROT_Y, t, DOC.eval(bone, Channel.ROT_Y, playhead), activeEase);
        DOC.putKey(bone, Channel.ROT_Z, t, DOC.eval(bone, Channel.ROT_Z, playhead), activeEase);
        if (bone.bendable && (toolMode == ToolMode.BEND
                || DOC.hasKeys(bone, Channel.BEND_ANG) || DOC.hasKeys(bone, Channel.BEND_DIR))) {
            DOC.putKey(bone, Channel.BEND_DIR, t, DOC.eval(bone, Channel.BEND_DIR, playhead), activeEase);
            DOC.putKey(bone, Channel.BEND_ANG, t, DOC.eval(bone, Channel.BEND_ANG, playhead), activeEase);
        }
        if (toolMode == ToolMode.MOVE
                || DOC.hasKeys(bone, Channel.POS_X) || DOC.hasKeys(bone, Channel.POS_Y) || DOC.hasKeys(bone, Channel.POS_Z)) {
            DOC.putKey(bone, Channel.POS_X, t, DOC.eval(bone, Channel.POS_X, playhead), activeEase);
            DOC.putKey(bone, Channel.POS_Y, t, DOC.eval(bone, Channel.POS_Y, playhead), activeEase);
            DOC.putKey(bone, Channel.POS_Z, t, DOC.eval(bone, Channel.POS_Z, playhead), activeEase);
        }
        syncInspectorFromDoc(true);
    }

    private static void resetBoneAtPlayhead(BoneId bone) {
        DOC.pushUndo();
        int t = Math.round(playhead);
        for (Channel ch : Channel.values()) {
            if (DOC.hasKeys(bone, ch)) {
                DOC.putKey(bone, ch, t, 0f, activeEase);
            }
        }
        syncInspectorFromDoc(true);
    }

    private static void deleteSelectedKeys() {
        DOC.pushUndo();
        if (!selectedKeys.isEmpty()) {
            for (KeyRef r : selectedKeys) {
                DOC.removeKey(r.bone(), r.channel(), r.tick());
            }
            selectedKeys.clear();
        } else {
            int t = Math.round(playhead);
            for (Channel ch : Channel.values()) {
                DOC.removeKey(selectedBone, ch, t);
            }
        }
        syncInspectorFromDoc(true);
    }

    private static void selectAllKeyframes() {
        selectedKeys.clear();
        for (BoneId b : BoneId.values()) {
            if (expandChannels && b != selectedBone) continue;
            for (Channel ch : Channel.values()) {
                for (int t : DOC.track(b, ch).keySet()) {
                    selectedKeys.add(new KeyRef(b, ch, t));
                }
            }
        }
    }

    private static void copyKeyframes() {
        keyClipboard.clear();
        if (!selectedKeys.isEmpty()) {
            int minTick = Integer.MAX_VALUE;
            for (KeyRef r : selectedKeys) minTick = Math.min(minTick, r.tick());
            for (KeyRef r : selectedKeys) {
                KeyPoint kp = DOC.track(r.bone(), r.channel()).get(r.tick());
                if (kp != null) {
                    keyClipboard.add(new CopiedKey(r.bone(), r.channel(), r.tick() - minTick, kp.value(), kp.ease()));
                }
            }
        } else {
            int t = Math.round(playhead);
            for (BoneId b : BoneId.values()) {
                for (Channel ch : Channel.values()) {
                    if (DOC.hasKeys(b, ch)) {
                        keyClipboard.add(new CopiedKey(b, ch, 0, DOC.eval(b, ch, t), activeEase));
                    }
                }
            }
        }
        ClientToasts.push(Messages.toast("studio.copied"));
    }

    private static void pasteKeyframes() {
        if (keyClipboard.isEmpty()) return;
        DOC.pushUndo();
        int baseTick = Math.round(playhead);
        selectedKeys.clear();
        for (CopiedKey ck : keyClipboard) {
            int nt = Math.max(0, baseTick + ck.relTick);
            DOC.putKey(ck.bone, ck.ch, nt, ck.value, ck.ease);
            selectedKeys.add(new KeyRef(ck.bone, ck.ch, nt));
        }
        syncInspectorFromDoc(true);
    }

    private static void updateMarqueeSelection() {
        int x0 = Math.min(dragStartX, dragCurX);
        int x1 = Math.max(dragStartX, dragCurX);
        int y0 = Math.min(dragStartY, dragCurY);
        int y1 = Math.max(dragStartY, dragCurY);
        if (!Screen.hasShiftDown()) {
            selectedKeys.clear();
        }
        for (DiamondHit hit : diamondHits) {
            if (hit.x >= x0 - 3 && hit.x <= x1 + 3 && hit.y >= y0 - 3 && hit.y <= y1 + 3) {
                if (!expandChannels) {
                    for (Channel ch : Channel.values()) {
                        if (DOC.track(hit.ref.bone(), ch).containsKey(hit.ref.tick())) {
                            selectedKeys.add(new KeyRef(hit.ref.bone(), ch, hit.ref.tick()));
                        }
                    }
                } else {
                    selectedKeys.add(hit.ref);
                }
            }
        }
    }

    private static DiamondHit findDiamondAt(int mx, int my) {
        DiamondHit best = null;
        int bestDist = 7;
        for (DiamondHit h : diamondHits) {
            int d = Math.abs(mx - h.x) + Math.abs(my - h.y);
            if (d <= bestDist) {
                bestDist = d;
                best = h;
            }
        }
        return best;
    }

    // =========================================================================
    //  ВЫСОКОСКОРОСТНЫЕ УТИЛИТЫ РЕНДЕРА (GPU 1-QUAD LINE БЕЗ ПРОСАДКИ FPS!)
    // =========================================================================

    /** Рисует идеально ровную линию произвольного угла ОДНИМ 4-вершинным полигоном на GPU. */
    private static void drawSmoothLine(GuiGraphics g, float x0, float y0, float x1, float y1, float width, int argb) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.05f) return;
        float halfW = width * 0.5f;
        float nx = (-dy / len) * halfW;
        float ny = (dx / len) * halfW;

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int gr = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        Matrix4f pose = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        // Два порядка обхода или двусторонний RenderType.gui() (в GUI cull выключен, но даём корректный CCW)
        vc.vertex(pose, x0 + nx, y0 + ny, 0f).color(r, gr, b, a).endVertex();
        vc.vertex(pose, x0 - nx, y0 - ny, 0f).color(r, gr, b, a).endVertex();
        vc.vertex(pose, x1 - nx, y1 - ny, 0f).color(r, gr, b, a).endVertex();
        vc.vertex(pose, x1 + nx, y1 + ny, 0f).color(r, gr, b, a).endVertex();
    }

    private static int tickToX(int tick) {
        return Math.round(tlTrackX + tick * pxPerTick - timelineScrollX);
    }

    private static int xToTick(int screenX) {
        return Math.max(0, Math.round((screenX - tlTrackX + timelineScrollX) / pxPerTick));
    }

    private static int drawBtn(GuiGraphics g, Font font, int mx, int my, int x, int y, int w, int h,
                               String label, String id, String tipText, boolean active, int accentCol) {
        boolean hot = hover(mx, my, x, y, w, h);
        int bg = active ? 0xFF23344E : (hot ? ROW_HOT : ROW);
        int brd = active ? accentCol : (hot ? EDGE_HOT : EDGE);
        g.fill(x, y, x + w, y + h, bg);
        border(g, x, y, w, h, brd);
        String s = trim(font, label, Math.max(8, w - 4));
        g.drawString(font, s, x + (w - font.width(s)) / 2, y + (h - 8) / 2,
                active ? accentCol : (hot ? 0xFFFFFFFF : TEXT), false);
        btns.add(new BtnHit(x, y, w, h, id, tipText));
        if (hot && tipText != null) {
            hoverTip = tipText;
            hoverTipX = mx + 6;
            hoverTipY = my + 16;
        }
        return x + w;
    }

    private static float distToSegment(float px, float py, float x0, float y0, float x1, float y1) {
        float vx = x1 - x0, vy = y1 - y0;
        float wx = px - x0, wy = py - y0;
        float c1 = vx * wx + vy * wy;
        if (c1 <= 0f) return (float) Math.hypot(px - x0, py - y0);
        float c2 = vx * vx + vy * vy;
        if (c2 <= c1) return (float) Math.hypot(px - x1, py - y1);
        float b = c1 / c2;
        return (float) Math.hypot(px - (x0 + b * vx), py - (y0 + b * vy));
    }

    private static String formatNum(float v) {
        if (Math.abs(v) < 0.005f) return "0";
        int tenths = Math.round(v * 10f);
        if (tenths % 10 == 0) {
            return Integer.toString(tenths / 10);
        }
        int abs = Math.abs(tenths);
        return (tenths < 0 ? "-" : "") + (abs / 10) + "." + (abs % 10);
    }

    private static String formatOneDec(float v) {
        int tenths = Math.round(v * 10f);
        int abs = Math.abs(tenths);
        return (tenths < 0 ? "-" : "") + (abs / 10) + "." + (abs % 10);
    }

    private static String formatSpeed(float s) {
        if (Math.abs(s - 0.25f) < 0.01f) return "0.25x";
        if (Math.abs(s - 0.5f) < 0.01f) return "0.5x";
        if (Math.abs(s - 1.5f) < 0.01f) return "1.5x";
        if (Math.abs(s - 2.0f) < 0.01f) return "2x";
        return "1x";
    }

    private static Float parseFloatSafe(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Float.parseFloat(raw.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseIntSafe(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trim(Font font, String s, int maxW) {
        if (s == null) return "";
        if (font.width(s) <= maxW) return s;
        return font.plainSubstrByWidth(s, Math.max(6, maxW - 6)) + "…";
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
}
