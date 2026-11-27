package com.scriptcraft.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import com.scriptcraft.client.ClientUiSettings.AnimPanelState;
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
import org.lwjgl.opengl.GL11;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Полноценный 3D-редактор анимаций игрока в Студии:
 * — Строгий ортогональный 3D-Гизмо в суставе (X вправо, Y строго вверх, Z вперёд) + 2 отдельные
 *   стрелки изгиба Bendy-lib (B — угол, D — направление) посередине сгибаемых костей без слияния;
 * — Настоящий перспективный предпросмотр от 1-го лица (1P, FOV 75°) из глаз игрока;
 * — Отсоединяемые, сворачиваемые и масштабируемые панели (Скелет, Инспектор, Таймлайн)
 *   с сохранением всех пропорций на клиенте (client-ui.properties).
 */
@OnlyIn(Dist.CLIENT)
public final class StudioAnimEditor {
    private static final int SURFACE = 0xF2161D29;
    private static final int SURFACE_FLOAT = 0xF7141B27;
    private static final int BAR = 0xF518202D;
    private static final int EDGE = 0xFF2B364A;
    private static final int EDGE_HOT = 0xFF425474;
    private static final int GOLD = 0xFFF0B85B;
    private static final int ACCENT = 0xFF58A6FF;
    private static final int GREEN = 0xFF4CC38A;
    private static final int RED = 0xFFE56B6F;
    private static final int PURPLE = 0xFFA855F7;
    private static final int PINK = 0xFFEC4899;
    private static final int TEXT = 0xFFE8EDF5;
    private static final int MUTED = 0xFF8A96AA;
    private static final int ROW = 0xFF1D2636;
    private static final int ROW_HOT = 0xFF27344A;

    public enum ToolMode { ROTATE, MOVE }

    public enum PanelId {
        LEFT("left"), RIGHT("right"), TIMELINE("tl");
        public final String key;
        PanelId(String key) { this.key = key; }
        public AnimPanelState state() { return ClientUiSettings.animPanel(key); }
    }

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
    private static int leftBonesScroll = 0;
    private static int leftBonesMaxScroll = 0;
    private static int inspectorScroll = 0;
    private static int inspectorMaxScroll = 0;
    private static final Set<KeyRef> selectedKeys = new LinkedHashSet<>();

    private record CopiedKey(BoneId bone, Channel ch, int relTick, float value, EaseCurve ease) {}
    private static final List<CopiedKey> keyClipboard = new ArrayList<>();

    // Камера 3-го лица и 1-го лица
    private static float camYaw = 26f;
    private static float camPitch = 16f;
    private static float camZoom = 1.0f;
    private static float camPanX = 0f;
    private static float camPanY = 0f;
    private static boolean firstPersonPreview = false;
    private static float fpYaw = 0f;
    private static float fpPitch = 20f;
    private static boolean showGrid = true;

    private static final Matrix4f lastEntityToScreen = new Matrix4f();

    // Прямоугольники панелей и порядок Z плавающих панелей (последний — самый верхний)
    private static final int[][] panelRects = new int[3][4]; // [PanelId.ordinal()][x, y, w, h]
    private static final List<PanelId> floatOrder = new ArrayList<>(List.of(PanelId.LEFT, PanelId.RIGHT, PanelId.TIMELINE));
    private static int vpX, vpY, vpW, vpH;

    // Состояние перетаскивания мыши
    private enum DragKind {
        NONE, ORBIT, PAN, GIZMO_POSE, SCRUB_PLAYHEAD, MARQUEE_SELECT, MOVE_KEYS, VALUE_SCRUB,
        DOCK_RESIZE, FLOAT_MOVE, FLOAT_RESIZE
    }
    private static DragKind dragKind = DragKind.NONE;
    private static PanelId dragPanel = null;
    private static int dragResizeMask = 0; // 1=W, 2=E, 4=N, 8=S
    private static float dragPanelOrigRatio, dragPanelOrigFx, dragPanelOrigFy, dragPanelOrigFw, dragPanelOrigFh;

    private static int dragStartX, dragStartY, dragCurX, dragCurY;
    private static float dragStartYaw, dragStartPitch, dragStartPanX, dragStartPanY;
    /** Индекс ручки Гизмо: -1=свободно, 0=X, 1=Y, 2=Z, 3=BendAngle(B), 4=BendDir(D). */
    private static int dragGizmoHandle = -1;
    private static float dragInitA, dragInitB, dragInitC;
    private static final float[] dragJacX = new float[5];
    private static final float[] dragJacY = new float[5];
    private static Channel scrubChannel = null;
    private static float scrubInitVal = 0f;
    private static int dragKeyOriginTick = 0;
    private static final List<CopiedKey> dragMovingSnapshot = new ArrayList<>();

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

    private static PlayerModel<?> fallbackWide;
    private static PlayerModel<?> fallbackSlim;

    private static int rootX, rootY, rootW, rootH, workY, workH;
    private static int tlTrackX, tlTrackY, tlTrackW, tlTrackH, tlRulerY;
    private static int easeBtnX, easeBtnY, easeBtnW;

    /** ownerLayer: 0 = топ-бар / привязанная панель, 1..3 = плавающая панель, 10 = модальное окно. */
    private static int currentLayerTag = 0;
    private record BtnHit(int x, int y, int w, int h, String id, String tip, int layer) {}
    private static final List<BtnHit> btns = new ArrayList<>();

    private record FieldBox(int idx, int x, int y, int w, int h, StudioTextField field, Channel ch, int layer) {}
    private static final List<FieldBox> fieldBoxes = new ArrayList<>();

    private record ProjectedBone(BoneId bone, float sx, float sy, float ex, float ey, float depth) {}
    private static final List<ProjectedBone> projectedBones = new ArrayList<>();
    private static BoneId hoveredViewportBone = null;
    /** -1 = ничего, 0=X, 1=Y, 2=Z, 3=BendAngle(B), 4=BendDir(D). */
    private static int hoveredGizmoHandle = -1;

    // Координаты суставного X/Y/Z Гизмо и срединного Гизмо Изгиба (B / D)
    private static float gizmoOriginX, gizmoOriginY;
    private static final float[] gizmoTipX = new float[3];
    private static final float[] gizmoTipY = new float[3];
    private static boolean hasBendGizmo = false;
    private static float bendOriginX, bendOriginY;
    private static final float[] bendTipX = new float[2]; // 0 = Bend Angle (B), 1 = Bend Dir (D)
    private static final float[] bendTipY = new float[2];

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
        if (relPath == null || relPath.isBlank()) return;
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
            return;
        }
        String last = parts[parts.length - 1];
        if (last.toLowerCase(Locale.ROOT).endsWith(".json")) {
            last = last.substring(0, last.length() - 5);
        }
        String clean = StudioAnimModel.safeResourceSlug(last, "anim");
        doc.fileName = clean;
        doc.displayName = last.isEmpty() ? clean : last;
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
    //  РАСЧЁТ КОМПОНОВКИ ПАНЕЛЕЙ (ПРИВЯЗАННЫЕ И ПЛАВАЮЩИЕ С СОХРАНЕНИЕМ ПРОПОРЦИЙ)
    // =========================================================================

    private static void computeLayout(int x, int y, int w, int h) {
        rootX = x;
        rootY = y;
        rootW = Math.max(200, w);
        rootH = Math.max(120, h);

        int topBarH = 24;
        workY = y + topBarH + 3;
        workH = Math.max(80, h - topBarH - 3);

        AnimPanelState stLeft = PanelId.LEFT.state();
        AnimPanelState stRight = PanelId.RIGHT.state();
        AnimPanelState stTl = PanelId.TIMELINE.state();

        // 1) Привязанный снизу Таймлайн
        int midH = workH;
        if (stTl.docked) {
            int th = stTl.collapsed ? 18 : clampInt(Math.round(rootH * stTl.dockedRatio), 78, Math.max(80, workH * 60 / 100));
            setPanelRect(PanelId.TIMELINE, x, workY + workH - th, w, th);
            midH = Math.max(54, workH - th - 3);
        }

        // 2) Привязанные слева и справа панели
        int curVpX = x;
        int curVpRight = x + w;
        if (stLeft.docked) {
            int lw = stLeft.collapsed ? 18 : clampInt(Math.round(rootW * stLeft.dockedRatio), 108, Math.max(110, w * 40 / 100));
            setPanelRect(PanelId.LEFT, x, workY, lw, midH);
            curVpX = x + lw + 3;
        }
        if (stRight.docked) {
            int rw = stRight.collapsed ? 18 : clampInt(Math.round(rootW * stRight.dockedRatio), 156, Math.max(158, w * 45 / 100));
            if (curVpRight - rw - 3 < curVpX + 60) {
                rw = Math.max(120, curVpRight - curVpX - 63);
            }
            setPanelRect(PanelId.RIGHT, x + w - rw, workY, rw, midH);
            curVpRight = x + w - rw - 3;
        }

        // 3) 3D-Вьюпорт занимает всё оставшееся место!
        vpX = curVpX;
        vpY = workY;
        vpW = Math.max(60, curVpRight - curVpX);
        vpH = Math.max(54, midH);

        // 4) Плавающие (отсоединённые) панели
        for (PanelId pid : PanelId.values()) {
            AnimPanelState st = pid.state();
            if (st.docked) continue;
            int minW = pid == PanelId.LEFT ? 112 : (pid == PanelId.RIGHT ? 160 : 220);
            int minH = pid == PanelId.TIMELINE ? 78 : 96;
            int fw = clampInt(Math.round(rootW * st.fw), minW, Math.max(minW, rootW - 6));
            int fh = st.collapsed ? 18 : clampInt(Math.round(rootH * st.fh), minH, Math.max(minH, workH - 2));
            int fx = clampInt(Math.round(rootX + rootW * st.fx), rootX + 2, Math.max(rootX + 2, rootX + rootW - fw - 2));
            int fy = clampInt(Math.round(rootY + rootH * st.fy), workY, Math.max(workY, rootY + rootH - fh - 2));
            setPanelRect(pid, fx, fy, fw, fh);
        }
    }

    private static void setPanelRect(PanelId pid, int x, int y, int w, int h) {
        int[] r = panelRects[pid.ordinal()];
        r[0] = x; r[1] = y; r[2] = w; r[3] = h;
    }

    private static int[] rectOf(PanelId pid) {
        return panelRects[pid.ordinal()];
    }

    private static PanelId topmostFloatingAt(int mx, int my) {
        for (int i = floatOrder.size() - 1; i >= 0; i--) {
            PanelId pid = floatOrder.get(i);
            if (!pid.state().docked) {
                int[] r = rectOf(pid);
                if (hover(mx, my, r[0], r[1], r[2], r[3])) {
                    return pid;
                }
            }
        }
        return null;
    }

    private static void bringFloatingToFront(PanelId pid) {
        floatOrder.remove(pid);
        floatOrder.add(pid);
    }

    private static void togglePanelDock(PanelId pid) {
        AnimPanelState st = pid.state();
        int[] cur = rectOf(pid);
        if (st.docked) {
            // При отсоединении берём текущие экранные координаты и чуть сдвигаем внутрь
            int nx = clampInt(cur[0] + 6, rootX + 4, Math.max(rootX + 4, rootX + rootW - cur[2] - 4));
            int ny = clampInt(cur[1] + 6, workY + 2, Math.max(workY + 2, rootY + rootH - cur[3] - 4));
            int nw = Math.max(pid == PanelId.TIMELINE ? 240 : 140, Math.min(cur[2], rootW - 16));
            int nh = st.collapsed ? Math.round(rootH * 0.34f) : Math.max(90, cur[3]);
            st.fx = (nx - rootX) / (float) Math.max(1, rootW);
            st.fy = (ny - rootY) / (float) Math.max(1, rootH);
            st.fw = nw / (float) Math.max(1, rootW);
            st.fh = nh / (float) Math.max(1, rootH);
            st.docked = false;
            st.collapsed = false;
            bringFloatingToFront(pid);
        } else {
            st.docked = true;
        }
        ClientUiSettings.saveAnimPanels();
    }

    private static void togglePanelCollapse(PanelId pid) {
        AnimPanelState st = pid.state();
        st.collapsed = !st.collapsed;
        ClientUiSettings.saveAnimPanels();
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

        computeLayout(x, y, w, h);

        boolean popupActive = openModal || newModal || easeDropdown || helpPopup;
        PanelId hoveredFloat = popupActive ? null : topmostFloatingAt(mx, my);

        // 1) Верхняя панель
        currentLayerTag = 0;
        renderTopBar(g, font, x, y, w, 24, popupActive ? -10000 : mx, popupActive ? -10000 : my);

        // 2) Центральный 3D-вьюпорт (если мышь над плавающей панелью — маскируем hover во вьюпорте)
        int vpMx = (popupActive || hoveredFloat != null) ? -10000 : mx;
        int vpMy = (popupActive || hoveredFloat != null) ? -10000 : my;
        renderViewport3D(g, font, vpX, vpY, vpW, vpH, vpMx, vpMy);

        // 3) Привязанные панели + подсветка разделителей ресайза
        for (PanelId pid : PanelId.values()) {
            AnimPanelState st = pid.state();
            if (!st.docked) continue;
            currentLayerTag = 0;
            int pmx = (popupActive || hoveredFloat != null) ? -10000 : mx;
            int pmy = (popupActive || hoveredFloat != null) ? -10000 : my;
            renderPanelById(g, font, pid, pmx, pmy, false);
            renderDockedSplitterHighlight(g, pid, pmx, pmy);
        }

        // 4) Плавающие панели (в порядке Z, поверх 3D-сцены и привязанных панелей!)
        for (int i = 0; i < floatOrder.size(); i++) {
            PanelId pid = floatOrder.get(i);
            if (pid.state().docked) continue;
            currentLayerTag = i + 1;
            int pmx = (popupActive || hoveredFloat != pid) ? -10000 : mx;
            int pmy = (popupActive || hoveredFloat != pid) ? -10000 : my;
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 340f + i * 18f);
            renderPanelById(g, font, pid, pmx, pmy, true);
            g.flush();
            g.pose().popPose();
        }

        if (popupActive) {
            btns.clear();
            fieldBoxes.clear();
            hoverTip = null;
        }

        currentLayerTag = 10;
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
            g.pose().translate(0, 0, 460);
            g.fill(tx, ty - 14, tx + tw, ty, 0xF5101622);
            border(g, tx, ty - 14, tw, 14, EDGE_HOT);
            g.drawString(font, hoverTip, tx + 5, ty - 11, TEXT, false);
            g.pose().popPose();
        }
    }

    private static void renderPanelById(GuiGraphics g, Font font, PanelId pid, int mx, int my, boolean floating) {
        int[] r = rectOf(pid);
        switch (pid) {
            case LEFT -> renderLeftBones(g, font, r[0], r[1], r[2], r[3], mx, my, floating);
            case RIGHT -> renderRightInspector(g, font, r[0], r[1], r[2], r[3], mx, my, floating);
            case TIMELINE -> renderTimeline(g, font, r[0], r[1], r[2], r[3], mx, my, floating);
        }
        if (floating && !pid.state().collapsed) {
            // Уголок масштабирования плавающего окна справа-снизу
            int gx = r[0] + r[2] - 6;
            int gy = r[1] + r[3] - 6;
            g.fill(gx + 3, gy + 1, gx + 5, gy + 5, GOLD);
            g.fill(gx + 1, gy + 3, gx + 5, gy + 5, GOLD);
        }
    }

    private static void renderDockedSplitterHighlight(GuiGraphics g, PanelId pid, int mx, int my) {
        AnimPanelState st = pid.state();
        if (!st.docked || st.collapsed) return;
        int[] r = rectOf(pid);
        boolean active = (dragKind == DragKind.DOCK_RESIZE && dragPanel == pid);
        if (pid == PanelId.LEFT) {
            int sx = r[0] + r[2] + 1;
            if (active || (Math.abs(mx - sx) <= 2 && my >= r[1] && my < r[1] + r[3])) {
                g.fill(sx - 1, r[1], sx + 1, r[1] + r[3], GOLD);
            }
        } else if (pid == PanelId.RIGHT) {
            int sx = r[0] - 2;
            if (active || (Math.abs(mx - sx) <= 2 && my >= r[1] && my < r[1] + r[3])) {
                g.fill(sx, r[1], sx + 2, r[1] + r[3], GOLD);
            }
        } else if (pid == PanelId.TIMELINE) {
            int sy = r[1] - 2;
            if (active || (Math.abs(my - sy) <= 2 && mx >= r[0] && mx < r[0] + r[2])) {
                g.fill(r[0], sy, r[0] + r[2], sy + 2, GOLD);
            }
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
    //  1. ВЕРХНЯЯ ПАНЕЛЬ ИНСТРУМЕНТОВ (без лишней кнопки Bend — изгиб прямо на костях!)
    // -------------------------------------------------------------------------

    private static void renderTopBar(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my) {
        g.fill(x, y, x + w, y + h, SURFACE);
        border(g, x, y, w, h, EDGE);
        int bx = x + 5;
        int by = y + 4;

        boolean dirty = DOC.isDirty();
        String idBadge = (dirty ? "* " : "") + DOC.fullId();
        int idW = Math.min(136, font.width(idBadge) + 14);
        bx = drawBtn(g, font, mx, my, bx, by, idW, 16, idBadge, "file:open",
                Messages.get("studio.anim.tip.open"), openModal, dirty ? GOLD : ACCENT) + 3;

        bx = drawBtn(g, font, mx, my, bx, by, font.width("+") + 10, 16, "+", "file:new",
                Messages.get("studio.anim.tip.new"), newModal, GREEN) + 3;
        bx = drawBtn(g, font, mx, my, bx, by, font.width(Messages.get("studio.anim.save")) + 10, 16,
                Messages.get("studio.anim.save"), "file:save", "Ctrl+S", false, dirty ? GOLD : TEXT) + 4;

        g.fill(bx, y + 4, bx + 1, y + h - 4, EDGE);
        bx += 5;

        // Режимы суставного Гизмо: Поворот [R] и Сдвиг [G] (стрелки Изгиба B/D всегда доступны прямо на кости!)
        bx = drawBtn(g, font, mx, my, bx, by, font.width(Messages.get("studio.anim.tool.rot")) + 10, 16,
                Messages.get("studio.anim.tool.rot"), "tool:rot", "[R]", toolMode == ToolMode.ROTATE, GOLD) + 2;
        bx = drawBtn(g, font, mx, my, bx, by, font.width(Messages.get("studio.anim.tool.pos")) + 10, 16,
                Messages.get("studio.anim.tool.pos"), "tool:pos", "[G]", toolMode == ToolMode.MOVE, ACCENT) + 4;

        // Фиксация осей X / Y / Z
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
            bx = drawBtn(g, font, mx, my, bx, by, font.width("◎") + 8, 16, "◎", "vp:reset_cam",
                    Messages.get("studio.anim.tip.cam") + " [F]", false, TEXT) + 4;
        }

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
    //  2. ЛЕВАЯ ПАНЕЛЬ: ДЕРЕВО КОСТЕЙ (привязанная / плавающая / сворачиваемая)
    // -------------------------------------------------------------------------

    private static void renderLeftBones(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my, boolean floating) {
        AnimPanelState st = PanelId.LEFT.state();
        if (floating) {
            g.fill(x + 2, y + 2, x + w + 3, y + h + 3, 0x66000000);
        }
        g.fill(x, y, x + w, y + h, floating ? SURFACE_FLOAT : SURFACE);
        border(g, x, y, w, h, floating ? EDGE_HOT : EDGE);

        // Вертикальная узкая полоска, когда панель привязана слева и свёрнута
        if (st.docked && st.collapsed) {
            drawBtn(g, font, mx, my, x + 2, y + 2, 14, 14, "▸", "panel:collapse:LEFT",
                    Messages.get("studio.anim.panel.expand"), false, GOLD);
            drawBtn(g, font, mx, my, x + 2, y + 18, 14, 14, "⧉", "panel:dock:LEFT",
                    Messages.get("studio.anim.panel.detach"), false, ACCENT);
            return;
        }

        g.fill(x + 1, y + 1, x + w - 1, y + 17, BAR);
        g.drawString(font, trim(font, Messages.get("studio.anim.bones"), Math.max(20, w - 38)), x + 6, y + 5, GOLD, false);
        drawPanelHeaderControls(g, font, mx, my, x + w - 32, y + 2, PanelId.LEFT, st);

        if (st.collapsed) return;

        int listTop = y + 18;
        int listH = Math.max(16, h - 19);
        BoneId[] bones = BoneId.values();
        int contentH = bones.length * 16 + 4;
        leftBonesMaxScroll = Math.max(0, contentH - listH);
        leftBonesScroll = Math.max(0, Math.min(leftBonesScroll, leftBonesMaxScroll));

        int curTick = Math.round(playhead);
        g.enableScissor(x + 1, listTop, x + w - 1, y + h - 1);
        int ry = listTop + 2 - leftBonesScroll;
        for (int i = 0; i < bones.length; i++) {
            BoneId b = bones[i];
            if (ry + 15 >= listTop && ry <= y + h - 1) {
                boolean sel = b == selectedBone;
                boolean hot = hover(mx, my, x + 2, ry, w - 5, 15);
                g.fill(x + 2, ry, x + w - 3, ry + 15, sel ? 0xFF24344D : (hot ? ROW_HOT : ROW));
                g.fill(x + 2, ry, x + 4, ry + 15, b.color);
                if (sel) {
                    border(g, x + 2, ry, w - 5, 15, GOLD);
                }

                boolean vis = DOC.isBoneVisible(b);
                int eyeX = x + w - 16;
                g.drawString(font, vis ? "◉" : "○", eyeX, ry + 4, vis ? MUTED : 0xFF475264, false);
                if (ry >= listTop - 4 && ry + 11 <= y + h) {
                    btns.add(new BtnHit(eyeX - 1, ry, 13, 15, "vis:" + b.name(), Messages.get("studio.anim.tip.vis"), currentLayerTag));
                }

                int rightLim = eyeX - 3;
                if (b.bendable) {
                    rightLim -= 8;
                    g.drawString(font, "⌒", rightLim, ry + 3, PINK, false);
                }
                if (DOC.boneHasKeyAt(b, curTick)) {
                    rightLim -= 8;
                    g.drawString(font, "◆", rightLim, ry + 4, GOLD, false);
                } else if (DOC.boneHasAnyKeys(b)) {
                    rightLim -= 7;
                    g.drawString(font, "•", rightLim, ry + 4, ACCENT, false);
                }

                String label = (i + 1) + " " + Messages.get(b.langKey);
                g.drawString(font, trim(font, label, Math.max(20, rightLim - (x + 7))), x + 7, ry + 4,
                        sel ? GOLD : (vis ? TEXT : MUTED), false);
                if (ry >= listTop - 4 && ry + 11 <= y + h) {
                    btns.add(new BtnHit(x + 2, ry, eyeX - x - 3, 15, "bone:" + b.name(), null, currentLayerTag));
                }
            }
            ry += 16;
        }
        g.disableScissor();
        drawMiniScrollbar(g, x + w - 3, listTop, listH, leftBonesScroll, leftBonesMaxScroll);
    }

    private static void drawPanelHeaderControls(GuiGraphics g, Font font, int mx, int my,
                                                int bx, int by, PanelId pid, AnimPanelState st) {
        String colSym = st.collapsed ? "▸" : "▾";
        String colTip = Messages.get(st.collapsed ? "studio.anim.panel.expand" : "studio.anim.panel.collapse");
        drawBtn(g, font, mx, my, bx, by, 14, 13, colSym, "panel:collapse:" + pid.name(), colTip, false, GOLD);

        String dockSym = st.docked ? "⧉" : "▣";
        String dockTip = Messages.get(st.docked ? "studio.anim.panel.detach" : "studio.anim.panel.dock");
        drawBtn(g, font, mx, my, bx + 15, by, 14, 13, dockSym, "panel:dock:" + pid.name(), dockTip, !st.docked, ACCENT);
    }

    // -------------------------------------------------------------------------
    //  3. ЦЕНТРАЛЬНЫЙ 3D-ВЬЮПОРТ (3-Е ЛИЦО С ОРТОГОНАЛЬНЫМ + BEND ГИЗМО И НАСТОЯЩИЙ 1P FOV 75°)
    // -------------------------------------------------------------------------

    private static void renderViewport3D(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my) {
        g.fillGradient(x, y, x + w, y + h, firstPersonPreview ? 0xFF142033 : 0xFF0E1420,
                firstPersonPreview ? 0xFF1E2D44 : 0xFF172234);
        border(g, x, y, w, h, EDGE);

        boolean paLoaded = ModList.get().isLoaded("playeranimator");
        boolean bendyLoaded = ModList.get().isLoaded("bendylib");
        Minecraft mc = Minecraft.getInstance();

        if (firstPersonPreview) {
            hoveredGizmoHandle = -1;
            hoveredViewportBone = null;
            renderTrueFirstPersonPerspective(g, mc, paLoaded, x + 1, y + 1, Math.max(2, w - 2), Math.max(2, h - 2));

            // Прицел и подпись режима от 1-го лица поверх 3D
            g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 325f);
            int chX = x + w / 2;
            int chY = y + h / 2;
            g.fill(chX - 4, chY, chX + 5, chY + 1, 0x99FFFFFF);
            g.fill(chX, chY - 4, chX + 1, chY + 5, 0x99FFFFFF);
            g.drawString(font, Messages.get("studio.anim.vp.1p_badge"), x + 6, y + 6, GREEN, true);
            g.pose().popPose();
            g.disableScissor();
            return;
        }

        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);

        float cx = x + w * 0.5f + camPanX;
        float cy = y + h * 0.54f + camPanY;
        float baseScale = Math.max(26f, Math.min(w * 0.34f, h * 0.41f));
        float pxPerBlock = baseScale * camZoom;
        float pivotY = 0.90f;

        PoseStack camStack = new PoseStack();
        camStack.translate(cx, cy, 220f);
        camStack.mulPoseMatrix(new Matrix4f().scaling(pxPerBlock, pxPerBlock, -pxPerBlock));
        camStack.mulPose(Axis.ZP.rotationDegrees(180f));
        camStack.mulPose(Axis.XP.rotationDegrees(-camPitch));
        camStack.mulPose(Axis.YP.rotationDegrees(camYaw));
        camStack.translate(0f, -pivotY, 0f);
        lastEntityToScreen.set(camStack.last().pose());

        if (showGrid) {
            renderFloorGridSmooth(g, lastEntityToScreen);
        }

        if (paLoaded && mc.player != null) {
            renderLivePlayerWithMods(g, mc.player, cx, cy, pxPerBlock, camPitch, camYaw, pivotY);
        } else {
            renderStandalonePlayerModel(g, mc, cx, cy, pxPerBlock, camPitch, camYaw, pivotY);
        }

        computeBoneProjections(lastEntityToScreen);
        updateViewportHover(mx, my, x, y, w, h);
        renderSkeletonOverlayAndGizmo(g, font, mx, my);

        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 330f);
        String topBadge = String.format(Locale.ROOT, "Yaw %.0f°  Pitch %.0f°  Zoom %.0f%%", camYaw, camPitch, camZoom * 100f);
        g.drawString(font, topBadge, x + 6, y + 6, MUTED, false);

        if (!paLoaded) {
            String warn = Messages.get("studio.anim.no_pa_banner");
            g.fill(x + 4, y + h - 18, x + Math.min(w - 4, font.width(warn) + 12), y + h - 4, 0xCC2A1A1E);
            g.drawString(font, trim(font, warn, w - 16), x + 8, y + h - 15, GOLD, false);
        } else {
            String hint = bendyLoaded
                    ? "PlayerAnimator + Bendy-lib ●  |  XYZ: Сустав  |  B/D: Изгиб  |  LMB: Орбита  MMB: Сдвиг"
                    : "PlayerAnimator ●  |  XYZ: Сустав  |  B/D: Изгиб  |  LMB: Орбита  MMB: Сдвиг";
            g.drawString(font, trim(font, hint, w - 12), x + 6, y + h - 13, 0xAA8A96AA, false);
        }
        g.pose().popPose();

        g.disableScissor();
    }

    /**
     * Настоящий перспективный рендер от 1-го лица (FOV 75°) прямо из глаз игрока!
     * Запускает штатный FirstPersonMode проход PlayerAnimator (скрывает голову/шляпу и рендерит руки + предметы в перспективе).
     */
    private static void renderTrueFirstPersonPerspective(GuiGraphics g, Minecraft mc, boolean paLoaded,
                                                         int vx, int vy, int vw, int vh) {
        g.flush();
        var win = mc.getWindow();
        double guiScale = win.getGuiScale();
        int glX = Math.max(0, (int) Math.round(vx * guiScale));
        int glW = Math.max(1, (int) Math.round(vw * guiScale));
        int glH = Math.max(1, (int) Math.round(vh * guiScale));
        int glY = Math.max(0, win.getHeight() - (int) Math.round((vy + vh) * guiScale));

        RenderSystem.backupProjectionMatrix();
        try {
            RenderSystem.viewport(glX, glY, glW, glH);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.enableDepthTest();

            Matrix4f persp = new Matrix4f().perspective(
                    (float) Math.toRadians(75.0f),
                    (float) vw / Math.max(1.0f, (float) vh),
                    0.04f,
                    32.0f);
            RenderSystem.setProjectionMatrix(persp, VertexSorting.DISTANCE_TO_ORIGIN);

            // Позиция глаз с учётом смещения корневой кости BODY
            float bodyOffX = StudioAnimModel.uiPosToEngine(BoneId.BODY, Channel.POS_X, DOC.eval(BoneId.BODY, Channel.POS_X, playhead), 0f);
            float bodyOffY = StudioAnimModel.uiPosToEngine(BoneId.BODY, Channel.POS_Y, DOC.eval(BoneId.BODY, Channel.POS_Y, playhead), 0f);
            float bodyOffZ = StudioAnimModel.uiPosToEngine(BoneId.BODY, Channel.POS_Z, DOC.eval(BoneId.BODY, Channel.POS_Z, playhead), 0f);

            PoseStack viewPose = new PoseStack();
            viewPose.mulPose(Axis.XP.rotationDegrees(fpPitch));
            viewPose.mulPose(Axis.YP.rotationDegrees(fpYaw));
            // Глаза находятся на высоте 1.54 блока и чуть позади линии плеч (+0.10 по Z), чтобы руки были прямо перед глазами
            viewPose.translate(-bodyOffX, -(1.54f + bodyOffY), -(0.10f + bodyOffZ));

            // 3D-сетка пола в перспективе для ощущения пространства
            if (showGrid) {
                VertexConsumer lvc = g.bufferSource().getBuffer(RenderType.lines());
                Matrix4f lm = viewPose.last().pose();
                org.joml.Matrix3f nm = viewPose.last().normal();
                for (float d = -3f; d <= 3.01f; d += 0.5f) {
                    boolean c = Math.abs(d) < 0.01f;
                    int r = c ? 88 : 50, gr = c ? 166 : 68, b = c ? 255 : 98, a = c ? 170 : 95;
                    lvc.vertex(lm, d, 0f, -3f).color(r, gr, b, a).normal(nm, 0f, 1f, 0f).endVertex();
                    lvc.vertex(lm, d, 0f, 3f).color(r, gr, b, a).normal(nm, 0f, 1f, 0f).endVertex();
                    lvc.vertex(lm, -3f, 0f, d).color(r, gr, b, a).normal(nm, 1f, 0f, 0f).endVertex();
                    lvc.vertex(lm, 3f, 0f, d).color(r, gr, b, a).normal(nm, 1f, 0f, 0f).endVertex();
                }
                g.flush();
            }

            Lighting.setupForEntityInInventory();
            if (paLoaded && mc.player != null) {
                AbstractClientPlayer player = mc.player;
                PlayerAnimBridge.beginViewportPreview(DOC, playhead, true);
                PlayerAnimBridge.setFirstPersonPass(true);

                float oldBodyRot = player.yBodyRot, oldBodyRotO = player.yBodyRotO;
                float oldYRot = player.getYRot(), oldYRotO = player.yRotO;
                float oldXRot = player.getXRot(), oldXRotO = player.xRotO;
                float oldHeadRot = player.yHeadRot, oldHeadRotO = player.yHeadRotO;
                float oldWalk = player.walkAnimation.speed();
                float oldAtk = player.attackAnim, oldOAtk = player.oAttackAnim;
                Pose oldPose = player.getPose();

                player.yBodyRot = player.yBodyRotO = 180f;
                player.setYRot(180f); player.yRotO = 180f;
                player.setXRot(0f); player.xRotO = 0f;
                player.yHeadRot = player.yHeadRotO = 180f;
                player.walkAnimation.setSpeed(0f);
                player.attackAnim = player.oAttackAnim = 0f;
                if (oldPose == Pose.CROUCHING) player.setPose(Pose.STANDING);

                EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
                dispatcher.setRenderShadow(false);
                try {
                    dispatcher.render(player, 0.0, 0.0, 0.0, 0.0f, 0.0f, viewPose, g.bufferSource(), 0xF000F0);
                    g.flush();
                } finally {
                    dispatcher.setRenderShadow(true);
                    PlayerAnimBridge.setFirstPersonPass(false);
                    PlayerAnimBridge.endViewportPreview();
                    player.yBodyRot = oldBodyRot; player.yBodyRotO = oldBodyRotO;
                    player.setYRot(oldYRot); player.yRotO = oldYRotO;
                    player.setXRot(oldXRot); player.xRotO = oldXRotO;
                    player.yHeadRot = oldHeadRot; player.yHeadRotO = oldHeadRotO;
                    player.walkAnimation.setSpeed(oldWalk);
                    player.attackAnim = oldAtk; player.oAttackAnim = oldOAtk;
                    if (oldPose == Pose.CROUCHING) player.setPose(oldPose);
                }
            } else if (mc.getEntityModels() != null) {
                boolean slim = mc.player != null && "slim".equals(mc.player.getModelName());
                ResourceLocation skin = mc.player != null ? mc.player.getSkinTextureLocation() : DefaultPlayerSkin.getDefaultSkin();
                PlayerModel<?> model = getFallbackModel(mc, slim);
                model.young = false;
                applyStandaloneBone(model.rightArm, BoneId.RIGHT_ARM);
                applyStandaloneBone(model.leftArm, BoneId.LEFT_ARM);

                viewPose.pushPose();
                viewPose.translate(bodyOffX, bodyOffY + 0.7f, bodyOffZ);
                viewPose.mulPose(Axis.ZP.rotationDegrees(DOC.eval(BoneId.BODY, Channel.ROT_Z, playhead)));
                viewPose.mulPose(Axis.YP.rotationDegrees(DOC.eval(BoneId.BODY, Channel.ROT_Y, playhead)));
                viewPose.mulPose(Axis.XP.rotationDegrees(DOC.eval(BoneId.BODY, Channel.ROT_X, playhead)));
                viewPose.translate(0f, -0.7f, 0f);
                viewPose.scale(-0.9375f, -0.9375f, 0.9375f);
                viewPose.translate(0f, -1.501f, 0f);

                model.setAllVisible(false);
                model.rightArm.visible = DOC.isBoneVisible(BoneId.RIGHT_ARM);
                model.leftArm.visible = DOC.isBoneVisible(BoneId.LEFT_ARM);
                model.renderToBuffer(viewPose, g.bufferSource().getBuffer(RenderType.entityCutoutNoCull(skin)),
                        0xF000F0, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
                model.rightArm.visible = false;
                model.leftArm.visible = false;
                model.rightSleeve.visible = DOC.isBoneVisible(BoneId.RIGHT_ARM);
                model.leftSleeve.visible = DOC.isBoneVisible(BoneId.LEFT_ARM);
                model.rightSleeve.copyFrom(model.rightArm);
                model.leftSleeve.copyFrom(model.leftArm);
                model.renderToBuffer(viewPose, g.bufferSource().getBuffer(RenderType.entityCutoutNoCullZOffset(skin)),
                        0xF000F0, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
                restoreVisibility(model);
                viewPose.popPose();
                g.flush();
            }
        } finally {
            RenderSystem.viewport(0, 0, win.getWidth(), win.getHeight());
            RenderSystem.restoreProjectionMatrix();
            Lighting.setupForFlatItems();
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
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
        PlayerAnimBridge.beginViewportPreview(DOC, playhead, false);

        float oldBodyRot = player.yBodyRot, oldBodyRotO = player.yBodyRotO;
        float oldYRot = player.getYRot(), oldYRotO = player.yRotO;
        float oldXRot = player.getXRot(), oldXRotO = player.xRotO;
        float oldHeadRot = player.yHeadRot, oldHeadRotO = player.yHeadRotO;
        float oldWalkSpeed = player.walkAnimation.speed();
        float oldAttack = player.attackAnim, oldOAttack = player.oAttackAnim;
        Pose oldPose = player.getPose();
        double oldCloakX = player.xCloak, oldCloakOX = player.xCloakO;
        double oldCloakY = player.yCloak, oldCloakOY = player.yCloakO;
        double oldCloakZ = player.zCloak, oldCloakOZ = player.zCloakO;

        player.yBodyRot = player.yBodyRotO = 180f;
        player.setYRot(180f); player.yRotO = 180f;
        player.setXRot(0f); player.xRotO = 0f;
        player.yHeadRot = player.yHeadRotO = 180f;
        player.walkAnimation.setSpeed(0f);
        player.attackAnim = player.oAttackAnim = 0f;
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
        Lighting.setupForFlatItems();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        player.yBodyRot = oldBodyRot; player.yBodyRotO = oldBodyRotO;
        player.setYRot(oldYRot); player.yRotO = oldYRotO;
        player.setXRot(oldXRot); player.xRotO = oldXRotO;
        player.yHeadRot = oldHeadRot; player.yHeadRotO = oldHeadRotO;
        player.walkAnimation.setSpeed(oldWalkSpeed);
        player.attackAnim = oldAttack; player.oAttackAnim = oldOAttack;
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
        Lighting.setupForFlatItems();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
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

    // --- 3D проецирование скелета + ортогональный X/Y/Z Гизмо + 2 отдельные стрелки изгиба (B / D) ---

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

    private static Vector4f evalBoneFeatureScreen(Matrix4f entityToScreen, BoneId targetBone,
                                                  BoneId pertBone, Channel pertCh, float pertDelta,
                                                  boolean tipPoint) {
        Matrix4f modelRoot = buildModelRootMatrix(entityToScreen, pertBone, pertCh, pertDelta);
        if (targetBone == BoneId.BODY) {
            if (!tipPoint) return project(modelRoot, 0f, 11.2f / 16f, 0f);
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

        // 1) Ортогональный 3D-Гизмо в суставе выбранной кости:
        //    X (красный) = Вправо, Y (зелёный) = СТРОГО ВВЕРХ, Z (синий) = Вперёд (перпендикулярно X и Y — никогда не сливается с Y!)
        Vector4f origin = evalBoneFeatureScreen(entityToScreen, selectedBone, null, null, 0f, false);
        gizmoOriginX = origin.x;
        gizmoOriginY = origin.y;

        // Проецируем строгие ортогональные 3D-направления сцены через верхнюю 3×3 часть entityToScreen
        Vector4f wZero = project(entityToScreen, 0f, 0f, 0f);
        Vector4f wRight = project(entityToScreen, -1f, 0f, 0f); // +X сцены (вправо при взгляде спереди)
        Vector4f wUp = project(entityToScreen, 0f, 1f, 0f);     // +Y сцены (СТРОГО ВВЕРХ!)
        Vector4f wFwd = project(entityToScreen, 0f, 0f, -1f);   // Вперёд к зрителю (ортогонально X и Y)

        float handleLen = 28f;
        setAxisTip(0, wRight.x - wZero.x, wRight.y - wZero.y, 1f, 0f, handleLen);
        setAxisTip(1, wUp.x - wZero.x, wUp.y - wZero.y, 0f, -1f, handleLen);

        float zDx = wFwd.x - wZero.x;
        float zDy = wFwd.y - wZero.y;
        float zLen = (float) Math.hypot(zDx, zDy);
        // Защита от слияния Z с Y при взгляде строго анфас (Yaw≈0) или сверху: уводим Z в чистую изометрическую диагональ!
        float yDx = gizmoTipX[1] - gizmoOriginX;
        float yDy = gizmoTipY[1] - gizmoOriginY;
        float yNorm = Math.max(1f, (float) Math.hypot(yDx, yDy));
        float cosZY = zLen > 0.001f ? Math.abs((zDx * yDx + zDy * yDy) / (zLen * yNorm)) : 1f;
        if (zLen < 7f || cosZY > 0.90f) {
            zDx = -0.76f;
            zDy = 0.65f;
        }
        setAxisTip(2, zDx, zDy, -0.76f, 0.65f, handleLen);

        // 2) Две отдельные изгибочные стрелки Bendy-lib (B = угол, D = направление) посередине сгибаемой кости!
        hasBendGizmo = selectedBone.bendable;
        if (hasBendGizmo) {
            ProjectedBone selSeg = null;
            for (ProjectedBone pb : projectedBones) {
                if (pb.bone == selectedBone) {
                    selSeg = pb;
                    break;
                }
            }
            if (selSeg != null) {
                float sx = selSeg.ex - selSeg.sx;
                float sy = selSeg.ey - selSeg.sy;
                float segLen = (float) Math.hypot(sx, sy);
                if (segLen >= 26f) {
                    // Размещаем узел изгиба в локте / колене / середине спины (62% длины кости)
                    bendOriginX = selSeg.sx + sx * 0.62f;
                    bendOriginY = selSeg.sy + sy * 0.62f;
                } else {
                    // Если кость короткая (например BODY) или смотрит в камеру — выносим узел вправо-вниз на 26px от сустава
                    bendOriginX = gizmoOriginX + 22f;
                    bendOriginY = gizmoOriginY + 18f;
                }
            } else {
                bendOriginX = gizmoOriginX + 22f;
                bendOriginY = gizmoOriginY + 18f;
            }

            // Направление стрелки B (угол изгиба) — по реальной 2D-производной изгиба кости
            Vector4f bp0 = evalBoneFeatureScreen(entityToScreen, selectedBone, selectedBone, Channel.BEND_ANG, 0f, true);
            Vector4f bp1 = evalBoneFeatureScreen(entityToScreen, selectedBone, selectedBone, Channel.BEND_ANG, 5f, true);
            float bdx = bp1.x - bp0.x;
            float bdy = bp1.y - bp0.y;
            float blen = (float) Math.hypot(bdx, bdy);
            if (blen < 0.4f) {
                bdx = -0.85f;
                bdy = -0.52f;
                blen = (float) Math.hypot(bdx, bdy);
            }
            float ux = bdx / blen;
            float uy = bdy / blen;
            bendTipX[0] = bendOriginX + ux * 23f;
            bendTipY[0] = bendOriginY + uy * 23f;

            // Стрелка D (направление изгиба) — строго под +90° к стрелке B, поэтому они НИКОГДА не сливаются!
            bendTipX[1] = bendOriginX - uy * 21f;
            bendTipY[1] = bendOriginY + ux * 21f;
        }
    }

    private static void setAxisTip(int idx, float dx, float dy, float defX, float defY, float lenPx) {
        float l = (float) Math.hypot(dx, dy);
        if (l < 0.001f) {
            dx = defX;
            dy = defY;
            l = (float) Math.hypot(dx, dy);
        }
        gizmoTipX[idx] = gizmoOriginX + (dx / l) * lenPx;
        gizmoTipY[idx] = gizmoOriginY + (dy / l) * lenPx;
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
        hoveredGizmoHandle = -1;
        hoveredViewportBone = null;
        if (!hover(mx, my, x, y, w, h)) return;

        float bestDist = 9.5f;
        for (int a = 0; a < 3; a++) {
            float d = distToSegment(mx, my, gizmoOriginX, gizmoOriginY, gizmoTipX[a], gizmoTipY[a]);
            if (d < bestDist) {
                bestDist = d;
                hoveredGizmoHandle = a;
            }
        }
        if (hasBendGizmo) {
            for (int b = 0; b < 2; b++) {
                float d = distToSegment(mx, my, bendOriginX, bendOriginY, bendTipX[b], bendTipY[b]);
                if (d < bestDist) {
                    bestDist = d;
                    hoveredGizmoHandle = 3 + b;
                }
            }
        }
        if (hoveredGizmoHandle >= 0) return;

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

        // 1) Ортогональные оси X, Y (вверх), Z в суставе
        int[] axisColors = {0xFFEF4444, 0xFF22C55E, 0xFF3B82F6};
        String[] axisNames = {"X", "Y", "Z"};
        for (int a = 0; a < 3; a++) {
            boolean active = (dragKind == DragKind.GIZMO_POSE && dragGizmoHandle == a)
                    || hoveredGizmoHandle == a || axisLock == a;
            int col = active ? 0xFFFFE066 : axisColors[a];
            drawArrowHandle(g, gizmoOriginX, gizmoOriginY, gizmoTipX[a], gizmoTipY[a],
                    active ? 2.2f : 1.5f, col, toolMode == ToolMode.ROTATE);
            if (active || a == 1) {
                g.drawString(font, axisNames[a], Math.round(gizmoTipX[a]) + 4, Math.round(gizmoTipY[a]) - 4, col, true);
            }
        }
        int ox = Math.round(gizmoOriginX);
        int oy = Math.round(gizmoOriginY);
        g.fill(ox - 3, oy - 3, ox + 3, oy + 3, GOLD);
        g.fill(ox - 1, oy - 1, ox + 1, oy + 1, 0xFF0E1420);

        // 2) Две отдельные стрелки Изгиба (B — угол, D — направление) в локте / колене / центре кости
        if (hasBendGizmo) {
            int[] bendCols = {PINK, PURPLE};
            String[] bendNames = {"B⌒", "D⟳"};
            for (int b = 0; b < 2; b++) {
                int hIdx = 3 + b;
                boolean active = (dragKind == DragKind.GIZMO_POSE && dragGizmoHandle == hIdx)
                        || hoveredGizmoHandle == hIdx;
                int col = active ? 0xFFFFE066 : bendCols[b];
                drawArrowHandle(g, bendOriginX, bendOriginY, bendTipX[b], bendTipY[b],
                        active ? 2.2f : 1.5f, col, false);
                g.drawString(font, active ? bendNames[b] : (b == 0 ? "B" : "D"),
                        Math.round(bendTipX[b]) + 4, Math.round(bendTipY[b]) - 4, col, true);
            }
            int bx = Math.round(bendOriginX);
            int by = Math.round(bendOriginY);
            drawDiamond(g, bx, by, PINK, false);
        }

        g.flush();
        g.pose().popPose();
    }

    /** Рисует стрелку Гизмо с острым наконечником (Move / Bend) или кольцом вращения (Rotate). */
    private static void drawArrowHandle(GuiGraphics g, float x0, float y0, float x1, float y1,
                                        float width, int col, boolean ringTip) {
        drawSmoothLine(g, x0, y0, x1, y1, width, col);
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        if (len > 4f) {
            float ux = dx / len, uy = dy / len;
            float px = -uy, py = ux;
            if (ringTip) {
                int tx = Math.round(x1), ty = Math.round(y1);
                g.fill(tx - 3, ty - 3, tx + 3, ty + 3, col);
                g.fill(tx - 1, ty - 1, tx + 1, ty + 1, 0xFF0E1420);
            } else {
                drawSmoothLine(g, x1, y1, x1 - ux * 5.5f + px * 3.2f, y1 - uy * 5.5f + py * 3.2f, width, col);
                drawSmoothLine(g, x1, y1, x1 - ux * 5.5f - px * 3.2f, y1 - uy * 5.5f - py * 3.2f, width, col);
            }
        }
    }

    // -------------------------------------------------------------------------
    //  4. ПРАВАЯ ПАНЕЛЬ: ИНСПЕКТОР КОСТИ, ГРАФИК EASING И НАСТРОЙКИ
    // -------------------------------------------------------------------------

    private static void renderRightInspector(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my, boolean floating) {
        AnimPanelState st = PanelId.RIGHT.state();
        if (floating) {
            g.fill(x + 2, y + 2, x + w + 3, y + h + 3, 0x66000000);
        }
        g.fill(x, y, x + w, y + h, floating ? SURFACE_FLOAT : SURFACE);
        border(g, x, y, w, h, floating ? EDGE_HOT : EDGE);

        if (st.docked && st.collapsed) {
            drawBtn(g, font, mx, my, x + 2, y + 2, 14, 14, "◂", "panel:collapse:RIGHT",
                    Messages.get("studio.anim.panel.expand"), false, GOLD);
            drawBtn(g, font, mx, my, x + 2, y + 18, 14, 14, "⧉", "panel:dock:RIGHT",
                    Messages.get("studio.anim.panel.detach"), false, ACCENT);
            return;
        }

        g.fill(x + 1, y + 1, x + w - 1, y + 17, BAR);
        String boneTitle = Messages.get(selectedBone.langKey);
        g.fill(x + 5, y + 5, x + 9, y + 13, selectedBone.color);
        g.drawString(font, trim(font, boneTitle, Math.max(24, w - 92)), x + 12, y + 5, GOLD, false);

        int curTick = Math.round(playhead);
        drawBtn(g, font, mx, my, x + w - 76, y + 2, 22, 13, "+◆", "bone:key_all",
                Messages.get("studio.anim.tip.insert_key") + " [I]", DOC.boneHasKeyAt(selectedBone, curTick), GOLD);
        drawBtn(g, font, mx, my, x + w - 53, y + 2, 19, 13, "↺", "bone:reset",
                Messages.get("studio.anim.tip.reset_bone"), false, RED);
        drawPanelHeaderControls(g, font, mx, my, x + w - 32, y + 2, PanelId.RIGHT, st);

        if (st.collapsed) return;

        if (activeFieldIdx < 0) {
            syncInspectorFromDoc(false);
        }

        int bodyTop = y + 18;
        int bodyBottom = y + h - 1;
        int bodyH = Math.max(20, bodyBottom - bodyTop);
        int neededH = (selectedBone.bendable ? 208 : 168);
        inspectorMaxScroll = Math.max(0, neededH - bodyH);
        inspectorScroll = Math.max(0, Math.min(inspectorScroll, inspectorMaxScroll));

        int effMx = (my >= bodyTop && my < bodyBottom) ? mx : -10000;
        int effMy = (my >= bodyTop && my < bodyBottom) ? my : -10000;

        g.enableScissor(x + 1, bodyTop, x + w - 1, bodyBottom);
        int cy = bodyTop + 3 - inspectorScroll;
        int innerW = w - 13;

        g.drawString(font, Messages.get("studio.anim.sec.rot"), x + 5, cy, MUTED, false);
        cy += 10;
        cy = renderChannelRow(g, font, effMx, effMy, x + 5, cy, innerW, 0, Channel.ROT_X, "X", bodyTop, bodyBottom);
        cy = renderChannelRow(g, font, effMx, effMy, x + 5, cy, innerW, 1, Channel.ROT_Y, "Y", bodyTop, bodyBottom);
        cy = renderChannelRow(g, font, effMx, effMy, x + 5, cy, innerW, 2, Channel.ROT_Z, "Z", bodyTop, bodyBottom);
        cy += 2;

        g.drawString(font, Messages.get("studio.anim.sec.pos"), x + 5, cy, MUTED, false);
        cy += 10;
        cy = renderChannelRow(g, font, effMx, effMy, x + 5, cy, innerW, 3, Channel.POS_X, "X", bodyTop, bodyBottom);
        cy = renderChannelRow(g, font, effMx, effMy, x + 5, cy, innerW, 4, Channel.POS_Y, "Y", bodyTop, bodyBottom);
        cy = renderChannelRow(g, font, effMx, effMy, x + 5, cy, innerW, 5, Channel.POS_Z, "Z", bodyTop, bodyBottom);
        cy += 2;

        if (selectedBone.bendable) {
            boolean bendy = ModList.get().isLoaded("bendylib");
            g.drawString(font, "Bendy-lib (°)", x + 5, cy, bendy ? PINK : MUTED, false);
            cy += 10;
            cy = renderChannelRow(g, font, effMx, effMy, x + 5, cy, innerW, 6, Channel.BEND_DIR, "D", bodyTop, bodyBottom);
            cy = renderChannelRow(g, font, effMx, effMy, x + 5, cy, innerW, 7, Channel.BEND_ANG, "B", bodyTop, bodyBottom);
            cy += 2;
        }

        g.fill(x + 5, cy, x + 5 + innerW, cy + 1, EDGE);
        cy += 4;

        g.drawString(font, "Easing:", x + 5, cy + 2, GOLD, false);
        int graphW = 48;
        int graphH = 30;
        int graphX = x + 5 + innerW - graphW;
        int graphY = cy;

        int selW = Math.max(54, graphX - (x + 5) - 4);
        easeBtnX = x + 5;
        easeBtnY = cy + 13;
        easeBtnW = selW;
        drawClippedBtn(g, font, effMx, effMy, easeBtnX, easeBtnY, easeBtnW, 16, activeEase.name(), "ease:open",
                Messages.get("studio.anim.tip.ease"), easeDropdown, ACCENT, bodyTop, bodyBottom);

        renderEaseCurveGraph(g, graphX, graphY, graphW, graphH, activeEase);
        cy += graphH + 4;

        g.fill(x + 5, cy, x + 5 + innerW, cy + 1, EDGE);
        cy += 4;
        int halfW = (innerW - 4) / 2;
        g.drawString(font, "End:", x + 5, cy + 3, MUTED, false);
        int efX = x + 28;
        int efW = Math.max(24, halfW - 23);
        endTickField.render(g, font, efX, cy, efW, 13, activeFieldIdx == 8, "40", 0xFF0C1119,
                activeFieldIdx == 8 ? GOLD : EDGE, TEXT, MUTED);
        if (cy + 13 > bodyTop && cy < bodyBottom) {
            fieldBoxes.add(new FieldBox(8, efX, cy, efW, 13, endTickField, null, currentLayerTag));
        }

        int fx = x + 7 + halfW;
        g.drawString(font, "Fade:", fx, cy + 3, MUTED, false);
        int ffX = fx + 27;
        int ffW = Math.max(20, x + 5 + innerW - ffX);
        fadeField.render(g, font, ffX, cy, ffW, 13, activeFieldIdx == 9, "2", 0xFF0C1119,
                activeFieldIdx == 9 ? GOLD : EDGE, TEXT, MUTED);
        if (cy + 13 > bodyTop && cy < bodyBottom) {
            fieldBoxes.add(new FieldBox(9, ffX, cy, ffW, 13, fadeField, null, currentLayerTag));
        }
        cy += 15;

        drawClippedBtn(g, font, effMx, effMy, x + 5, cy, halfW, 14,
                (DOC.looped ? "☑ " : "☐ ") + Messages.get("studio.anim.loop"),
                "anim:loop", Messages.get("studio.anim.tip.loop"), DOC.looped, GREEN, bodyTop, bodyBottom);
        g.drawString(font, "Ret:", fx, cy + 3, MUTED, false);
        int rfX = fx + 23;
        int rfW = Math.max(22, x + 5 + innerW - rfX);
        returnTickField.render(g, font, rfX, cy, rfW, 14, activeFieldIdx == 10, "0", 0xFF0C1119,
                activeFieldIdx == 10 ? GOLD : EDGE, TEXT, MUTED);
        if (cy + 14 > bodyTop && cy < bodyBottom) {
            fieldBoxes.add(new FieldBox(10, rfX, cy, rfW, 14, returnTickField, null, currentLayerTag));
        }

        g.disableScissor();
        drawMiniScrollbar(g, x + w - 3, bodyTop, bodyH, inspectorScroll, inspectorMaxScroll);
    }

    private static int renderChannelRow(GuiGraphics g, Font font, int mx, int my,
                                        int x, int y, int w, int fieldIdx, Channel ch, String tag,
                                        int clipTop, int clipBottom) {
        int curTick = Math.round(playhead);
        boolean hasKeyHere = DOC.track(selectedBone, ch).containsKey(curTick);
        boolean visible = (y + 12 > clipTop && y < clipBottom);

        boolean tagHot = visible && hover(mx, my, x, y, 12, 12);
        g.fill(x, y, x + 12, y + 12, tagHot ? ROW_HOT : 0xFF101621);
        border(g, x, y, 12, 12, ch.axisColor);
        g.drawString(font, tag, x + 3, y + 2, ch.axisColor, false);
        if (visible) {
            btns.add(new BtnHit(x, y, 12, 12, "scrub:" + ch.name(), Messages.get("studio.anim.tip.scrub"), currentLayerTag));
        }

        int minusX = x + 14;
        drawClippedBtn(g, font, mx, my, minusX, y, 11, 12, "-", "step:-:" + ch.name(), null, false, MUTED, clipTop, clipBottom);

        int keyBtnX = x + w - 12;
        drawClippedBtn(g, font, mx, my, keyBtnX, y, 12, 12, "◆", "kftog:" + ch.name(),
                Messages.get("studio.anim.tip.kftog"), hasKeyHere, hasKeyHere ? GOLD : MUTED, clipTop, clipBottom);

        int plusX = keyBtnX - 12;
        drawClippedBtn(g, font, mx, my, plusX, y, 11, 12, "+", "step:+:" + ch.name(), null, false, MUTED, clipTop, clipBottom);

        int fldX = minusX + 12;
        int fldW = Math.max(24, plusX - fldX - 1);
        StudioTextField tf = channelFields[fieldIdx];
        tf.render(g, font, fldX, y, fldW, 12, activeFieldIdx == fieldIdx, "0", 0xFF0C1119,
                activeFieldIdx == fieldIdx ? GOLD : (hasKeyHere ? 0xFF5A4A24 : EDGE), TEXT, MUTED);
        if (visible) {
            fieldBoxes.add(new FieldBox(fieldIdx, fldX, y, fldW, 12, tf, ch, currentLayerTag));
        }

        return y + 13;
    }

    private static void renderEaseCurveGraph(GuiGraphics g, int x, int y, int w, int h, EaseCurve curve) {
        g.fill(x, y, x + w, y + h, 0xFF0B1018);
        border(g, x, y, w, h, ACCENT);
        int padX = 4, padY = 5;
        int gw = w - padX * 2;
        int gh = h - padY * 2;
        g.fill(x + padX, y + padY, x + padX + gw, y + padY + 1, 0x44425474);
        g.fill(x + padX, y + padY + gh, x + padX + gw, y + padY + gh + 1, 0x44425474);

        float prevX = x + padX;
        float prevY = y + padY + gh;
        int steps = 18;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            float v = curve.eval(t);
            float sx = x + padX + t * gw;
            float sy = Math.max(y + 2, Math.min(y + h - 2, y + padY + gh - v * gh));
            drawSmoothLine(g, prevX, prevY, sx, sy, 1.5f, ACCENT);
            prevX = sx;
            prevY = sy;
        }
        g.flush();
        float animT = (System.nanoTime() / 1_000_000L % 1400L) / 1400f;
        float dotX = x + padX + animT * gw;
        float dotY = Math.max(y + 2, Math.min(y + h - 2, y + padY + gh - curve.eval(animT) * gh));
        g.fill(Math.round(dotX) - 2, Math.round(dotY) - 2, Math.round(dotX) + 2, Math.round(dotY) + 2, GOLD);
    }

    // -------------------------------------------------------------------------
    //  5. НИЖНЯЯ ПАНЕЛЬ: ТАЙМЛАЙН КЛЮЧЕВЫХ КАДРОВ (привязанная / плавающая / сворачиваемая)
    // -------------------------------------------------------------------------

    private static void renderTimeline(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my, boolean floating) {
        AnimPanelState st = PanelId.TIMELINE.state();
        if (floating) {
            g.fill(x + 2, y + 2, x + w + 3, y + h + 3, 0x66000000);
        }
        g.fill(x, y, x + w, y + h, floating ? SURFACE_FLOAT : SURFACE);
        border(g, x, y, w, h, floating ? EDGE_HOT : EDGE);

        int barH = st.collapsed ? 16 : 20;
        g.fill(x + 1, y + 1, x + w - 1, y + barH, BAR);
        if (!st.collapsed) {
            g.fill(x + 1, y + barH, x + w - 1, y + barH + 1, EDGE);
        }

        int bx = x + 5;
        int by = y + (st.collapsed ? 2 : 3);
        int bh = st.collapsed ? 13 : 14;
        bx = drawBtn(g, font, mx, my, bx, by, 16, bh, "|<", "tl:start", "[Home]", false, TEXT) + 2;
        bx = drawBtn(g, font, mx, my, bx, by, 14, bh, "<", "tl:prev", "[←]", false, TEXT) + 2;
        bx = drawBtn(g, font, mx, my, bx, by, 20, bh, playing ? "||" : "▶", "tl:play",
                "[Space]", playing, GREEN) + 2;
        bx = drawBtn(g, font, mx, my, bx, by, 15, bh, "■", "tl:stop", "Stop", false, RED) + 2;
        bx = drawBtn(g, font, mx, my, bx, by, 14, bh, ">", "tl:next", "[→]", false, TEXT) + 2;
        bx = drawBtn(g, font, mx, my, bx, by, 16, bh, ">|", "tl:end", "[End]", false, TEXT) + 6;

        String timeStr = "T: " + formatOneDec(playhead) + "/" + DOC.endTick;
        if (bx + font.width(timeStr) < x + w - 42) {
            g.drawString(font, timeStr, bx, y + (st.collapsed ? 5 : 6), GOLD, false);
            bx += font.width(timeStr) + 6;
        }

        if (!st.collapsed && bx + 90 < x + w - 40) {
            String spdLbl = formatSpeed(playbackSpeed);
            bx = drawBtn(g, font, mx, my, bx, by, font.width(spdLbl) + 8, bh, spdLbl, "tl:speed",
                    Messages.get("studio.anim.tip.speed"), false, ACCENT) + 3;

            String modeLbl = expandChannels
                    ? Messages.get("studio.anim.tl.channels")
                    : Messages.get("studio.anim.tl.bones");
            bx = drawBtn(g, font, mx, my, bx, by, font.width(modeLbl) + 8, bh, modeLbl, "tl:mode",
                    Messages.get("studio.anim.tip.tl_mode"), expandChannels, GOLD) + 4;
        }

        // Кнопки сворачивания и отсоединения таймлайна справа
        int rx = x + w - 33;
        drawPanelHeaderControls(g, font, mx, my, rx, y + (st.collapsed ? 2 : 3), PanelId.TIMELINE, st);

        if (st.collapsed) {
            tlTrackW = 0;
            tlTrackH = 0;
            return;
        }

        rx -= 17;
        if (rx > bx) drawBtn(g, font, mx, my, rx, by, 15, bh, "+", "tl:zoom_in", "Zoom +", false, TEXT);
        rx -= 17;
        if (rx > bx) drawBtn(g, font, mx, my, rx, by, 15, bh, "-", "tl:zoom_out", "Zoom -", false, TEXT);
        if (!selectedKeys.isEmpty() && rx - 34 > bx) {
            String delLbl = "× " + selectedKeys.size();
            int dw = font.width(delLbl) + 8;
            rx -= (dw + 3);
            drawBtn(g, font, mx, my, rx, by, dw, bh, delLbl, "tl:del_sel", "[Delete]", false, RED);
        }

        int labelColW = Math.min(114, Math.max(76, w / 4));
        tlRulerY = y + barH + 1;
        int rulerH = 14;
        tlTrackX = x + labelColW;
        tlTrackW = Math.max(30, w - labelColW - 2);
        tlTrackY = tlRulerY + rulerH;
        tlTrackH = Math.max(16, y + h - tlTrackY - 1);

        g.fill(x + 1, tlRulerY, tlTrackX, tlRulerY + rulerH, 0xFF121824);
        g.drawString(font, expandChannels ? trim(font, Messages.get(selectedBone.langKey), labelColW - 8) : "Track",
                x + 6, tlRulerY + 3, MUTED, false);
        g.fill(tlTrackX - 1, tlRulerY, tlTrackX, y + h - 1, EDGE);

        g.fill(tlTrackX, tlRulerY, x + w - 1, tlRulerY + rulerH, 0xFF101520);
        g.fill(x + 1, tlTrackY - 1, x + w - 1, tlTrackY, EDGE);

        int maxVisTick = Math.max(DOC.stopTick + 10, (int) ((timelineScrollX + tlTrackW) / pxPerTick) + 5);
        int tickStep = pxPerTick >= 10f ? 5 : (pxPerTick >= 5f ? 10 : 20);

        g.enableScissor(tlTrackX, tlRulerY, x + w - 1, y + h - 1);
        for (int t = 0; t <= maxVisTick; t++) {
            int tx = tickToX(t);
            if (tx < tlTrackX - 10 || tx > x + w + 10) continue;
            if (t % tickStep == 0) {
                g.fill(tx, tlRulerY + 8, tx + 1, tlRulerY + rulerH, EDGE_HOT);
                g.fill(tx, tlTrackY, tx + 1, y + h - 1, 0x222B364A);
                g.drawString(font, Integer.toString(t), tx + 2, tlRulerY + 2, t == DOC.endTick ? GOLD : MUTED, false);
            } else if (pxPerTick >= 6f) {
                g.fill(tx, tlRulerY + 11, tx + 1, tlRulerY + rulerH, 0x552B364A);
            }
        }

        if (DOC.looped) {
            int rtx = tickToX(DOC.returnTick);
            g.fill(rtx, tlRulerY, rtx + 1, y + h - 1, 0x884CC38A);
        }
        int etx = tickToX(DOC.endTick);
        g.fill(etx, tlRulerY, etx + 1, y + h - 1, 0xAAF0B85B);
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
                renderChannelDiamonds(g, selectedBone, ch, ry + rowH / 2, x + w - 1);
            } else {
                BoneId b = BoneId.values()[i];
                g.fill(x + 3, ry + 2, x + 5, ry + rowH - 2, b.color);
                g.drawString(font, trim(font, Messages.get(b.langKey), labelColW - 10),
                        x + 8, ry + 3, rowSel ? GOLD : TEXT, false);
                if (ry >= tlTrackY - 3 && ry + 10 <= y + h) {
                    btns.add(new BtnHit(x + 1, ry, labelColW - 2, rowH, "bone:" + b.name(), null, currentLayerTag));
                }
                renderBoneSummaryDiamonds(g, b, ry + rowH / 2, x + w - 1);
            }
        }

        if (dragKind == DragKind.MARQUEE_SELECT) {
            int bx0 = Math.min(dragStartX, dragCurX);
            int by0 = Math.min(dragStartY, dragCurY);
            int bw = Math.abs(dragCurX - dragStartX);
            int bh2 = Math.abs(dragCurY - dragStartY);
            g.fill(bx0, by0, bx0 + bw, by0 + bh2, 0x3858A6FF);
            border(g, bx0, by0, Math.max(1, bw), Math.max(1, bh2), ACCENT);
        }
        g.disableScissor();

        int phX = Math.round(tlTrackX + playhead * pxPerTick - timelineScrollX);
        if (phX >= tlTrackX && phX < x + w - 1) {
            g.fill(phX - 3, tlRulerY, phX + 4, tlRulerY + 5, RED);
            g.fill(phX, tlRulerY, phX + 1, y + h - 1, RED);
        }
    }

    private static void renderChannelDiamonds(GuiGraphics g, BoneId bone, Channel ch, int centerY, int rightEdge) {
        int deltaTick = dragKind == DragKind.MOVE_KEYS ? xToTick(dragCurX) - dragKeyOriginTick : 0;
        for (KeyPoint kp : DOC.track(bone, ch).values()) {
            KeyRef ref = new KeyRef(bone, ch, kp.tick());
            boolean sel = selectedKeys.contains(ref);
            int drawTick = sel ? Math.max(0, kp.tick() + deltaTick) : kp.tick();
            int dx = tickToX(drawTick);
            if (dx < tlTrackX - 2 || dx > rightEdge) continue;
            drawDiamond(g, dx, centerY, sel ? GOLD : ch.axisColor, sel);
            diamondHits.add(new DiamondHit(ref, dx, centerY));
        }
    }

    private static void renderBoneSummaryDiamonds(GuiGraphics g, BoneId bone, int centerY, int rightEdge) {
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
            if (dx < tlTrackX - 2 || dx > rightEdge) continue;
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
        g.pose().translate(0, 0, 410);
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
                    int visTop = Math.max(listY, ry);
                    int visBot = Math.min(listY + listH, ry + 15);
                    boolean hot = visBot > visTop && hover(mx, my, mx0 + 4, visTop, mw - 28, visBot - visTop);
                    g.fill(mx0 + 4, ry, mx0 + mw - 24, ry + 15, hot ? ROW_HOT : ROW);
                    String shortDisp = e.name().replaceFirst("^resources/assets/", "");
                    g.drawString(font, trim(font, shortDisp, mw - 36), mx0 + 8, ry + 4, hot ? GOLD : TEXT, false);
                    if (visBot > visTop) {
                        btns.add(new BtnHit(mx0 + 4, visTop, mw - 28, visBot - visTop, "openfile:" + e.name(), null, currentLayerTag));
                        drawClippedBtn(g, font, mx, my, mx0 + mw - 22, ry + 1, 16, 13, "×",
                                "delfile:" + e.name(), null, false, RED, listY, listY + listH);
                    }
                }
                ry += 16;
            }
            g.disableScissor();
            drawMiniScrollbar(g, mx0 + mw - 4, listY, listH, openModalScroll, maxSc);
        }
        g.pose().popPose();
    }

    private static void renderNewModal(GuiGraphics g, Font font, int mx, int my) {
        int mw = 236, mh = 104;
        int mx0 = rootX + (rootW - mw) / 2;
        int my0 = rootY + (rootH - mh) / 2;

        g.pose().pushPose();
        g.pose().translate(0, 0, 410);
        g.fill(rootX, rootY, rootX + rootW, rootY + rootH, 0x88000000);
        g.fill(mx0, my0, mx0 + mw, my0 + mh, 0xF8131A26);
        border(g, mx0, my0, mw, mh, GREEN);
        g.fill(mx0 + 1, my0 + 1, mx0 + mw - 1, my0 + 19, BAR);
        g.drawString(font, Messages.get("studio.anim.new_title"), mx0 + 8, my0 + 6, GREEN, false);
        drawBtn(g, font, mx, my, mx0 + mw - 19, my0 + 3, 16, 14, "x", "modal:close", null, false, RED);

        g.drawString(font, "Namespace:", mx0 + 10, my0 + 28, MUTED, false);
        newNsField.render(g, font, mx0 + 78, my0 + 25, mw - 88, 15, activeFieldIdx == 12, "scriptcraft",
                0xFF0C1119, activeFieldIdx == 12 ? GOLD : EDGE, TEXT, MUTED);
        fieldBoxes.add(new FieldBox(12, mx0 + 78, my0 + 25, mw - 88, 15, newNsField, null, currentLayerTag));

        g.drawString(font, Messages.get("studio.anim.name") + ":", mx0 + 10, my0 + 48, MUTED, false);
        newNameField.render(g, font, mx0 + 78, my0 + 45, mw - 88, 15, activeFieldIdx == 13, "my_emote",
                0xFF0C1119, activeFieldIdx == 13 ? GOLD : EDGE, TEXT, MUTED);
        fieldBoxes.add(new FieldBox(13, mx0 + 78, my0 + 45, mw - 88, 15, newNameField, null, currentLayerTag));

        String createLbl = Messages.get("studio.anim.create_btn");
        drawBtn(g, font, mx, my, mx0 + mw - 86, my0 + mh - 22, 76, 16, createLbl, "modal:create_confirm", null, true, GREEN);
        g.pose().popPose();
    }

    private static void renderEaseDropdown(GuiGraphics g, Font font, int mx, int my) {
        EaseCurve[] all = EaseCurve.values();
        int dw = Math.max(126, easeBtnW);
        int dh = Math.min(152, Math.max(78, rootH - 40));
        int dx = Math.max(rootX + 4, Math.min(easeBtnX, rootX + rootW - dw - 6));
        int dy = (easeBtnY - dh - 2 >= rootY + 24)
                ? (easeBtnY - dh - 2)
                : Math.min(rootY + rootH - dh - 4, easeBtnY + 17);

        g.pose().pushPose();
        g.pose().translate(0, 0, 420);
        g.fill(dx - 2, dy - 2, dx + dw + 2, dy + dh + 2, 0x77000000);
        g.fill(dx, dy, dx + dw, dy + dh, 0xF8111722);
        border(g, dx, dy, dw, dh, ACCENT);
        int listTop = dy + 2;
        int listBot = dy + dh - 2;
        int listH = listBot - listTop;
        int maxSc = Math.max(0, all.length * 13 - listH);
        easeModalScroll = Math.max(0, Math.min(easeModalScroll, maxSc));
        g.enableScissor(dx + 1, listTop, dx + dw - 1, listBot);
        int ry = listTop - easeModalScroll;
        for (EaseCurve c : all) {
            if (ry + 13 > listTop && ry < listBot) {
                int visTop = Math.max(listTop, ry);
                int visBot = Math.min(listBot, ry + 13);
                boolean on = c == activeEase;
                boolean hot = visBot > visTop && hover(mx, my, dx + 2, visTop, dw - 7, visBot - visTop);
                if (on || hot) g.fill(dx + 2, ry, dx + dw - 5, ry + 13, on ? 0xFF233856 : ROW_HOT);
                g.drawString(font, c.name(), dx + 6, ry + 2, on ? GOLD : TEXT, false);
                if (visBot > visTop) {
                    btns.add(new BtnHit(dx + 2, visTop, dw - 7, visBot - visTop, "setease:" + c.name(), null, currentLayerTag));
                }
            }
            ry += 13;
        }
        g.disableScissor();
        drawMiniScrollbar(g, dx + dw - 4, listTop, listH, easeModalScroll, maxSc);
        g.pose().popPose();
    }

    private static void renderHelpPopup(GuiGraphics g, Font font, int mx, int my) {
        String[] lines = {
                Messages.get("studio.anim.hk.title"),
                "Space — Play / Pause   |   ← / → — ±1 tick   |   Home / End",
                "R / G — Rotate / Move   |   X / Y / Z — Axis lock   |   B / D — Bend arrows",
                "1..9 — Select bone   |   F — Reset camera   |   I / K — Keyframe",
                "LMB (3D) — Pick bone / Drag XYZ or Bend / Orbit   |   MMB — Pan",
                "Panels [▾] Collapse   |   [⧉ / ▣] Detach / Dock & Resize edges",
                "Ctrl+M — Mirror Pose   |   Ctrl+C / Ctrl+V — Copy / Paste keys",
                "Ctrl+Z / Ctrl+Y — Undo / Redo   |   Ctrl+S — Save to server"
        };
        int mw = 356;
        int mh = lines.length * 13 + 18;
        int mx0 = rootX + (rootW - mw) / 2;
        int my0 = rootY + (rootH - mh) / 2;

        g.pose().pushPose();
        g.pose().translate(0, 0, 430);
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
    //  МЫШЬ И КЛАВИАТУРА (С УЧЁТОМ ПЛАВАЮЩИХ ПАНЕЛЕЙ И Z-ПОРЯДКА)
    // =========================================================================

    public static boolean mouseClicked(Font font, int mx, int my, int button) {
        ensureInit();
        commitActiveField();
        activeFieldIdx = -1;

        boolean popupActive = openModal || newModal || easeDropdown || helpPopup;
        if (popupActive) {
            for (int i = fieldBoxes.size() - 1; i >= 0; i--) {
                FieldBox fb = fieldBoxes.get(i);
                if (hover(mx, my, fb.x, fb.y, fb.w, fb.h)) {
                    activeFieldIdx = fb.idx;
                    fb.field.mouseClicked(font, mx, my, button);
                    return true;
                }
            }
            for (int i = btns.size() - 1; i >= 0; i--) {
                BtnHit b = btns.get(i);
                if (hover(mx, my, b.x, b.y, b.w, b.h)) {
                    handleButton(b.id);
                    return true;
                }
            }
            openModal = newModal = easeDropdown = helpPopup = false;
            return true;
        }

        // 1) Проверяем плавающие панели сверху вниз по Z-порядку (ресайз краёв, поля, кнопки, таймлайн, перетаскивание шапки)
        for (int z = floatOrder.size() - 1; z >= 0; z--) {
            PanelId pid = floatOrder.get(z);
            AnimPanelState st = pid.state();
            if (st.docked) continue;
            int[] r = rectOf(pid);
            int edgeMask = st.collapsed ? 0 : floatResizeMaskAt(mx, my, r[0], r[1], r[2], r[3]);
            if (edgeMask != 0 && button == 0) {
                bringFloatingToFront(pid);
                startFloatResize(pid, mx, my, edgeMask);
                return true;
            }
            if (hover(mx, my, r[0], r[1], r[2], r[3])) {
                int layer = z + 1;
                bringFloatingToFront(pid);
                if (dispatchLayerControls(font, mx, my, button, layer)) {
                    return true;
                }
                if (pid == PanelId.TIMELINE && !st.collapsed && dispatchTimelineArea(mx, my, button)) {
                    return true;
                }
                if (button == 0 && my < r[1] + 18) {
                    dragKind = DragKind.FLOAT_MOVE;
                    dragPanel = pid;
                    dragStartX = mx;
                    dragStartY = my;
                    dragPanelOrigFx = st.fx;
                    dragPanelOrigFy = st.fy;
                }
                return true; // клик поглощён плавающей панелью!
            }
        }

        // 2) Проверяем разделители привязанных панелей (ресайз ширины/высоты)
        if (button == 0) {
            PanelId splitPid = dockedSplitterAt(mx, my);
            if (splitPid != null) {
                dragKind = DragKind.DOCK_RESIZE;
                dragPanel = splitPid;
                dragStartX = mx;
                dragStartY = my;
                dragPanelOrigRatio = splitPid.state().dockedRatio;
                return true;
            }
        }

        // 3) Кнопки и поля топ-бара и привязанных панелей (layer == 0)
        if (dispatchLayerControls(font, mx, my, button, 0)) {
            return true;
        }

        // 4) Область дорожек привязанного Таймлайна
        AnimPanelState tlSt = PanelId.TIMELINE.state();
        if (tlSt.docked && !tlSt.collapsed && dispatchTimelineArea(mx, my, button)) {
            return true;
        }

        // 5) Поглощаем клики внутри привязанных панелей, чтобы они не проваливались в 3D-вьюпорт
        for (PanelId pid : PanelId.values()) {
            if (pid.state().docked) {
                int[] r = rectOf(pid);
                if (hover(mx, my, r[0], r[1], r[2], r[3])) {
                    return true;
                }
            }
        }

        // 6) Клик в 3D-вьюпорте
        if (hover(mx, my, vpX, vpY, vpW, vpH)) {
            if (firstPersonPreview) {
                dragKind = DragKind.ORBIT;
                dragStartX = mx;
                dragStartY = my;
                dragStartYaw = fpYaw;
                dragStartPitch = fpPitch;
                return true;
            }
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
                if (hoveredGizmoHandle >= 0 || axisLock >= 0) {
                    startGizmoDrag(mx, my, hoveredGizmoHandle >= 0 ? hoveredGizmoHandle : axisLock);
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

        return true;
    }

    private static boolean dispatchLayerControls(Font font, int mx, int my, int button, int layer) {
        for (int i = fieldBoxes.size() - 1; i >= 0; i--) {
            FieldBox fb = fieldBoxes.get(i);
            if (fb.layer == layer && hover(mx, my, fb.x, fb.y, fb.w, fb.h)) {
                activeFieldIdx = fb.idx;
                fb.field.mouseClicked(font, mx, my, button);
                return true;
            }
        }
        for (int i = btns.size() - 1; i >= 0; i--) {
            BtnHit b = btns.get(i);
            if (b.layer == layer && hover(mx, my, b.x, b.y, b.w, b.h)) {
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
        return false;
    }

    private static boolean dispatchTimelineArea(int mx, int my, int button) {
        if (tlTrackW <= 0 || tlTrackH <= 0) return false;
        if (hover(mx, my, tlTrackX, tlRulerY, tlTrackW, 14)) {
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
        return false;
    }

    private static PanelId dockedSplitterAt(int mx, int my) {
        for (PanelId pid : PanelId.values()) {
            AnimPanelState st = pid.state();
            if (!st.docked || st.collapsed) continue;
            int[] r = rectOf(pid);
            if (pid == PanelId.LEFT && Math.abs(mx - (r[0] + r[2] + 1)) <= 3 && my >= r[1] && my < r[1] + r[3]) {
                return PanelId.LEFT;
            }
            if (pid == PanelId.RIGHT && Math.abs(mx - (r[0] - 2)) <= 3 && my >= r[1] && my < r[1] + r[3]) {
                return PanelId.RIGHT;
            }
            if (pid == PanelId.TIMELINE && Math.abs(my - (r[1] - 2)) <= 3 && mx >= r[0] && mx < r[0] + r[2]) {
                return PanelId.TIMELINE;
            }
        }
        return null;
    }

    private static int floatResizeMaskAt(int mx, int my, int x, int y, int w, int h) {
        if (mx < x - 3 || mx > x + w + 3 || my < y - 3 || my > y + h + 3) return 0;
        if (mx >= x + w - 8 && mx <= x + w + 3 && my >= y + h - 8 && my <= y + h + 3) {
            return 2 | 8; // правый нижний уголок SE
        }
        int mask = 0;
        if (Math.abs(mx - x) <= 3) mask |= 1;
        else if (Math.abs(mx - (x + w)) <= 3) mask |= 2;
        if (Math.abs(my - y) <= 3) mask |= 4;
        else if (Math.abs(my - (y + h)) <= 3) mask |= 8;
        // Верхний край над заголовком отдаём ресайзу только на самых верхних 2px
        if (mask == 4 && Math.abs(my - y) > 2) return 0;
        return mask;
    }

    private static void startFloatResize(PanelId pid, int mx, int my, int mask) {
        AnimPanelState st = pid.state();
        int[] r = rectOf(pid);
        dragKind = DragKind.FLOAT_RESIZE;
        dragPanel = pid;
        dragResizeMask = mask;
        dragStartX = mx;
        dragStartY = my;
        dragPanelOrigFx = (r[0] - rootX) / (float) Math.max(1, rootW);
        dragPanelOrigFy = (r[1] - rootY) / (float) Math.max(1, rootH);
        dragPanelOrigFw = r[2] / (float) Math.max(1, rootW);
        dragPanelOrigFh = r[3] / (float) Math.max(1, rootH);
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
            case DOCK_RESIZE -> {
                if (dragPanel != null) {
                    AnimPanelState st = dragPanel.state();
                    if (dragPanel == PanelId.LEFT) {
                        st.dockedRatio = clampFloat(dragPanelOrigRatio + (mx - dragStartX) / (float) Math.max(1, rootW), 0.12f, 0.42f);
                    } else if (dragPanel == PanelId.RIGHT) {
                        st.dockedRatio = clampFloat(dragPanelOrigRatio - (mx - dragStartX) / (float) Math.max(1, rootW), 0.16f, 0.46f);
                    } else if (dragPanel == PanelId.TIMELINE) {
                        st.dockedRatio = clampFloat(dragPanelOrigRatio - (my - dragStartY) / (float) Math.max(1, rootH), 0.14f, 0.60f);
                    }
                }
                return true;
            }
            case FLOAT_MOVE -> {
                if (dragPanel != null) {
                    AnimPanelState st = dragPanel.state();
                    st.fx = clampFloat(dragPanelOrigFx + (mx - dragStartX) / (float) Math.max(1, rootW), 0.0f, 0.94f);
                    st.fy = clampFloat(dragPanelOrigFy + (my - dragStartY) / (float) Math.max(1, rootH), 0.04f, 0.94f);
                }
                return true;
            }
            case FLOAT_RESIZE -> {
                if (dragPanel != null) {
                    AnimPanelState st = dragPanel.state();
                    float dxf = (mx - dragStartX) / (float) Math.max(1, rootW);
                    float dyf = (my - dragStartY) / (float) Math.max(1, rootH);
                    float minWf = (dragPanel == PanelId.LEFT ? 112f : (dragPanel == PanelId.RIGHT ? 160f : 220f)) / Math.max(1f, rootW);
                    float minHf = (dragPanel == PanelId.TIMELINE ? 78f : 96f) / Math.max(1f, rootH);
                    float fx = dragPanelOrigFx, fy = dragPanelOrigFy, fw = dragPanelOrigFw, fh = dragPanelOrigFh;
                    if ((dragResizeMask & 2) != 0) fw = Math.max(minWf, dragPanelOrigFw + dxf);
                    if ((dragResizeMask & 8) != 0) fh = Math.max(minHf, dragPanelOrigFh + dyf);
                    if ((dragResizeMask & 1) != 0) {
                        float nw = Math.max(minWf, dragPanelOrigFw - dxf);
                        fx = dragPanelOrigFx + (dragPanelOrigFw - nw);
                        fw = nw;
                    }
                    if ((dragResizeMask & 4) != 0) {
                        float nh = Math.max(minHf, dragPanelOrigFh - dyf);
                        fy = dragPanelOrigFy + (dragPanelOrigFh - nh);
                        fh = nh;
                    }
                    st.fx = clampFloat(fx, 0f, 0.95f);
                    st.fy = clampFloat(fy, 0.04f, 0.95f);
                    st.fw = clampFloat(fw, minWf, 0.98f);
                    st.fh = clampFloat(fh, minHf, 0.95f);
                }
                return true;
            }
            case ORBIT -> {
                if (firstPersonPreview) {
                    fpYaw = clampFloat(dragStartYaw + (mx - dragStartX) * 0.45f, -110f, 110f);
                    fpPitch = clampFloat(dragStartPitch + (my - dragStartY) * 0.45f, -75f, 85f);
                } else {
                    camYaw = dragStartYaw + (mx - dragStartX) * 0.7f;
                    camPitch = clampFloat(dragStartPitch + (my - dragStartY) * 0.6f, -85f, 85f);
                }
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
        if (dragKind == DragKind.DOCK_RESIZE || dragKind == DragKind.FLOAT_MOVE || dragKind == DragKind.FLOAT_RESIZE) {
            ClientUiSettings.saveAnimPanels();
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
        dragPanel = null;
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

        // Сначала проверяем плавающую панель под курсором
        PanelId fpid = topmostFloatingAt(mx, my);
        if (fpid != null) {
            return scrollPanel(fpid, step);
        }
        // Затем привязанные панели
        for (PanelId pid : PanelId.values()) {
            if (!pid.state().docked) continue;
            int[] r = rectOf(pid);
            if (hover(mx, my, r[0], r[1], r[2], r[3])) {
                return scrollPanel(pid, step);
            }
        }
        if (hover(mx, my, vpX, vpY, vpW, vpH) && !firstPersonPreview) {
            camZoom = clampFloat(camZoom + step * 0.12f, 0.35f, 3.2f);
            return true;
        }
        return false;
    }

    private static boolean scrollPanel(PanelId pid, int step) {
        if (pid == PanelId.RIGHT) {
            inspectorScroll = clampInt(inspectorScroll - step * 14, 0, inspectorMaxScroll);
            return true;
        }
        if (pid == PanelId.LEFT) {
            leftBonesScroll = clampInt(leftBonesScroll - step * 14, 0, leftBonesMaxScroll);
            return true;
        }
        if (pid == PanelId.TIMELINE) {
            if (Screen.hasControlDown()) {
                pxPerTick = clampFloat(pxPerTick + step * 1.2f, 2.5f, 28f);
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
    //  ДЕЙСТВИЯ КНОПОК И ПРОЕКЦИОННЫЙ 3D-ГИЗМО (XYZ + 2 СТРЕЛКИ ИЗГИБА)
    // =========================================================================

    private static void handleButton(String id) {
        if (id.startsWith("panel:dock:")) {
            togglePanelDock(PanelId.valueOf(id.substring("panel:dock:".length())));
            return;
        }
        if (id.startsWith("panel:collapse:")) {
            togglePanelCollapse(PanelId.valueOf(id.substring("panel:collapse:".length())));
            return;
        }
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

    private static Channel[] activeJointChannels() {
        return toolMode == ToolMode.ROTATE
                ? new Channel[]{Channel.ROT_X, Channel.ROT_Y, Channel.ROT_Z}
                : new Channel[]{Channel.POS_X, Channel.POS_Y, Channel.POS_Z};
    }

    private static void startGizmoDrag(int mx, int my, int handleIdx) {
        DOC.pushUndo();
        dragKind = DragKind.GIZMO_POSE;
        dragStartX = mx;
        dragStartY = my;
        dragGizmoHandle = handleIdx;

        // Ручки 3 (B = BEND_ANG) и 4 (D = BEND_DIR) работают напрямую независимо от режима Rotate/Move!
        if (handleIdx == 3 || handleIdx == 4) {
            dragInitA = DOC.eval(selectedBone, Channel.BEND_ANG, playhead);
            dragInitB = DOC.eval(selectedBone, Channel.BEND_DIR, playhead);
            // Проецируем вдоль самой нарисованной стрелки B или D, чтобы мышь тянула строго вдоль стрелки!
            int bi = handleIdx - 3;
            float vx = bendTipX[bi] - bendOriginX;
            float vy = bendTipY[bi] - bendOriginY;
            float len = Math.max(1f, (float) Math.hypot(vx, vy));
            dragJacX[handleIdx] = vx / len;
            dragJacY[handleIdx] = vy / len;
            return;
        }

        Channel[] chs = activeJointChannels();
        dragInitA = DOC.eval(selectedBone, chs[0], playhead);
        dragInitB = DOC.eval(selectedBone, chs[1], playhead);
        dragInitC = DOC.eval(selectedBone, chs[2], playhead);

        boolean useTip = toolMode == ToolMode.ROTATE;
        for (int i = 0; i < 3; i++) {
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

        if (dragGizmoHandle == 3 && selectedBone.bendable) {
            float along = dx * dragJacX[3] + dy * dragJacY[3];
            DOC.putKey(selectedBone, Channel.BEND_ANG, tick, dragInitA + along * 1.15f, activeEase);
            syncInspectorFromDoc(true);
            return;
        }
        if (dragGizmoHandle == 4 && selectedBone.bendable) {
            float along = dx * dragJacX[4] + dy * dragJacY[4];
            DOC.putKey(selectedBone, Channel.BEND_DIR, tick, dragInitB + along * 1.4f, activeEase);
            syncInspectorFromDoc(true);
            return;
        }

        Channel[] chs = activeJointChannels();
        float[] inits = {dragInitA, dragInitB, dragInitC};

        if (dragGizmoHandle >= 0 && dragGizmoHandle < 3) {
            int a = dragGizmoHandle;
            float jx = dragJacX[a];
            float jy = dragJacY[a];
            float normSq = jx * jx + jy * jy;
            float deltaParam;
            if (normSq > 0.04f) {
                deltaParam = (dx * jx + dy * jy) / normSq;
            } else {
                // Если ось смотрит прямо в камеру — тянем вдоль визуальной стрелки Гизмо!
                float gx = gizmoTipX[a] - gizmoOriginX;
                float gy = gizmoTipY[a] - gizmoOriginY;
                float gl = Math.max(1f, (float) Math.hypot(gx, gy));
                float along = (dx * gx + dy * gy) / gl;
                // В Move синяя стрелка Z направлена вперёд (-POS_Z), поэтому +along уменьшает POS_Z (приближает к зрителю)
                float sign = (toolMode == ToolMode.MOVE && a == 2) ? -1f : 1f;
                deltaParam = along * sign * (chs[a].angle ? 1.1f : 0.14f);
            }
            DOC.putKey(selectedBone, chs[a], tick, inits[a] + deltaParam, activeEase);
            syncInspectorFromDoc(true);
            return;
        }

        if (toolMode == ToolMode.MOVE) {
            int horzIdx = (dragJacX[0] * dragJacX[0] + dragJacY[0] * dragJacY[0])
                    >= (dragJacX[2] * dragJacX[2] + dragJacY[2] * dragJacY[2]) ? 0 : 2;
            float[] d = solve2x2(dragJacX[horzIdx], dragJacY[horzIdx], dragJacX[1], dragJacY[1], dx, dy, 0.08f);
            DOC.putKey(selectedBone, chs[horzIdx], tick, inits[horzIdx] + d[0], activeEase);
            DOC.putKey(selectedBone, Channel.POS_Y, tick, dragInitB + d[1], activeEase);
        } else {
            float[] d = solve2x2(dragJacX[0], dragJacY[0], dragJacX[2], dragJacY[2], dx, dy, 0.65f);
            DOC.putKey(selectedBone, Channel.ROT_X, tick, dragInitA + d[0], activeEase);
            DOC.putKey(selectedBone, Channel.ROT_Z, tick, dragInitC + d[1], activeEase);
        }
        syncInspectorFromDoc(true);
    }

    private static float[] solve2x2(float ax, float ay, float bx, float by, float dx, float dy, float fallbackScale) {
        float det = ax * by - ay * bx;
        if (Math.abs(det) > 0.08f) {
            float da = (dx * by - dy * bx) / det;
            float db = (ax * dy - ay * dx) / det;
            return new float[]{clampFloat(da, -360f, 360f), clampFloat(db, -360f, 360f)};
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
        if (firstPersonPreview) {
            fpYaw = 0f;
            fpPitch = 20f;
        } else {
            camYaw = 26f;
            camPitch = 16f;
            camZoom = 1.0f;
            camPanX = 0f;
            camPanY = 0f;
        }
    }

    private static void insertKeyframesForBone(BoneId bone) {
        DOC.pushUndo();
        int t = Math.round(playhead);
        DOC.putKey(bone, Channel.ROT_X, t, DOC.eval(bone, Channel.ROT_X, playhead), activeEase);
        DOC.putKey(bone, Channel.ROT_Y, t, DOC.eval(bone, Channel.ROT_Y, playhead), activeEase);
        DOC.putKey(bone, Channel.ROT_Z, t, DOC.eval(bone, Channel.ROT_Z, playhead), activeEase);
        if (bone.bendable && (DOC.hasKeys(bone, Channel.BEND_ANG) || DOC.hasKeys(bone, Channel.BEND_DIR))) {
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
    //  УТИЛИТЫ РЕНДЕРА
    // =========================================================================

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
        vc.vertex(pose, x0 - nx, y0 - ny, 0f).color(r, gr, b, a).endVertex();
        vc.vertex(pose, x0 + nx, y0 + ny, 0f).color(r, gr, b, a).endVertex();
        vc.vertex(pose, x1 + nx, y1 + ny, 0f).color(r, gr, b, a).endVertex();
        vc.vertex(pose, x1 - nx, y1 - ny, 0f).color(r, gr, b, a).endVertex();
    }

    private static void drawMiniScrollbar(GuiGraphics g, int x, int y, int h, int scroll, int maxScroll) {
        if (maxScroll <= 0 || h < 12) return;
        g.fill(x, y, x + 2, y + h, 0x332B364A);
        int thumbH = Math.max(10, Math.round(h * (h / (float) (h + maxScroll))));
        int track = Math.max(1, h - thumbH);
        int thumbY = y + Math.round((scroll / (float) maxScroll) * track);
        g.fill(x, thumbY, x + 2, thumbY + thumbH, 0xAA8A96AA);
    }

    private static int drawClippedBtn(GuiGraphics g, Font font, int mx, int my, int x, int y, int w, int h,
                                      String label, String id, String tipText, boolean active, int accentCol,
                                      int clipTop, int clipBottom) {
        int visTop = Math.max(clipTop, y);
        int visBot = Math.min(clipBottom, y + h);
        boolean hot = visBot > visTop && hover(mx, my, x, visTop, w, visBot - visTop);
        int bg = active ? 0xFF23344E : (hot ? ROW_HOT : ROW);
        int brd = active ? accentCol : (hot ? EDGE_HOT : EDGE);
        g.fill(x, y, x + w, y + h, bg);
        border(g, x, y, w, h, brd);
        String s = trim(font, label, Math.max(6, w - 4));
        g.drawString(font, s, x + (w - font.width(s)) / 2, y + (h - 8) / 2,
                active ? accentCol : (hot ? 0xFFFFFFFF : TEXT), false);
        if (visBot > visTop) {
            btns.add(new BtnHit(x, visTop, w, visBot - visTop, id, tipText, currentLayerTag));
            if (hot && tipText != null) {
                hoverTip = tipText;
                hoverTipX = mx + 6;
                hoverTipY = my + 16;
            }
        }
        return x + w;
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
        String s = trim(font, label, Math.max(6, w - 4));
        g.drawString(font, s, x + (w - font.width(s)) / 2, y + (h - 8) / 2,
                active ? accentCol : (hot ? 0xFFFFFFFF : TEXT), false);
        btns.add(new BtnHit(x, y, w, h, id, tipText, currentLayerTag));
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

    private static int clampInt(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    private static float clampFloat(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
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
