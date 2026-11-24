package com.scriptcraft.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Полноценный 3D-редактор анимаций игрока в Студии (уровня Blockbench):
 * — Интерактивный 3D-вьюпорт с орбитой, панорамированием, зумом, сеткой пола и режимом от 1-го лица;
 * — Живой рендер локального игрока через PlayerAnimator + Bendy-lib (плавный изгиб конечностей, броня, предметы в руках)
 *   с автономным фоллбэком на PlayerModel, если моды не установлены;
 * — Выбор костей кликом мыши прямо по 3D-модели + интерактивный 3D-гизмо (Поворот [R], Позиция [G], Изгиб [B], оси X/Y/Z);
 * — Многодорожечный таймлайн с перемоткой, рамкой выделения (box-select), перетаскиванием ключей, копированием/зеркалом позы;
 * — Инспектор численных полей со скраббингом мышью, превью графика кривой сглаживания (31 Ease), Undo/Redo и сохранение на сервер.
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
    private static boolean expandChannels = false; // false = 9 костей, true = 8 каналов выбранной кости
    private static float pxPerTick = 9.0f;
    private static int timelineScrollX = 0;
    private static int timelineScrollY = 0;
    private static int timelineH = 132;
    private static final Set<KeyRef> selectedKeys = new LinkedHashSet<>();

    // Буфер обмена ключей / позы
    private record CopiedKey(BoneId bone, Channel ch, int relTick, float value, EaseCurve ease) {}
    private static final List<CopiedKey> keyClipboard = new ArrayList<>();

    // Камера 3D-вьюпорта
    private static float camYaw = -26f;
    private static float camPitch = 14f;
    private static float camZoom = 1.0f;
    private static float camPanX = 0f;
    private static float camPanY = 0f;
    private static boolean firstPersonPreview = false;
    private static boolean showGrid = true;

    // Состояние перетаскивания мыши
    private enum DragKind {
        NONE, ORBIT, PAN, GIZMO_POSE, SCRUB_PLAYHEAD, MARQUEE_SELECT, MOVE_KEYS, TIMELINE_RESIZE, VALUE_SCRUB
    }
    private static DragKind dragKind = DragKind.NONE;
    private static int dragStartX, dragStartY, dragCurX, dragCurY;
    private static float dragStartYaw, dragStartPitch, dragStartPanX, dragStartPanY;
    private static int dragGizmoAxis = -1; // 0=X, 1=Y, 2=Z
    private static float dragInitA, dragInitB, dragInitC;
    private static Channel scrubChannel = null;
    private static float scrubInitVal = 0f;
    private static int dragKeyOriginTick = 0;
    private static final List<CopiedKey> dragMovingSnapshot = new ArrayList<>();
    private static int dragStartTimelineH = 132;

    // Поля ввода инспектора и диалогов
    private static final StudioTextField[] channelFields = new StudioTextField[8];
    private static int activeFieldIdx = -1; // 0..7 = каналы, 8 = endTick, 9 = beginTick, 10 = returnTick, 11 = author, 12 = newNs, 13 = newName
    private static final StudioTextField endTickField = new StudioTextField().digitsOnly(5);
    private static final StudioTextField fadeField = new StudioTextField().digitsOnly(3);
    private static final StudioTextField returnTickField = new StudioTextField().digitsOnly(5);
    private static final StudioTextField authorField = new StudioTextField().maxLength(32);

    // Модальные окна: Открыть / Создать / Выбор Ease / Справка клавиш
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

    // Кэшированные области разметки и проекции 3D-скелета на экран
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
        StudioAnimModel.applyPreset(DOC, "wave");
        DOC.undo(); // очистим лишний шаг, оставив чистый пресет? Нет: лучше применим и сбросим историю
        DOC.clearTracks();
        StudioAnimModel.applyPreset(DOC, "wave");
        DOC.markSaved();
        syncInspectorFromDoc();
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
        // Извлекаем namespace и fileName из resources/assets/<ns>/player_animation(s)/<name>.json
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
        syncInspectorFromDoc();
        ClientToasts.push(Messages.toast("studio.anim.loaded", DOC.fullId()));
    }

    private static void parseRelPathInto(String relPath, AnimDoc doc) {
        if (relPath == null) return;
        String norm = relPath.replace('\\', '/');
        // Ожидается resources/assets/<ns>/player_animation/<name>.json
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

        if (System.currentTimeMillis() - lastListReqMs > 12000L && serverAnimFiles.isEmpty()) {
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

        leftW = Math.max(118, Math.min(152, w / 5));
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

        // Полоска ресайза таймлайна
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
            syncInspectorFromDoc();
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

        // Текущий файл анимации + индикатор изменений
        String idBadge = (DOC.isDirty() ? "* " : "") + DOC.fullId();
        int idW = Math.min(138, font.width(idBadge) + 14);
        bx = drawBtn(g, font, mx, my, bx, by, idW, 16, idBadge, "file:open",
                Messages.get("studio.anim.tip.open"), openModal, DOC.isDirty() ? GOLD : ACCENT) + 3;

        bx = drawBtn(g, font, mx, my, bx, by, font.width("+") + 10, 16, "+", "file:new",
                Messages.get("studio.anim.tip.new"), newModal, GREEN) + 3;
        bx = drawBtn(g, font, mx, my, bx, by, font.width(Messages.get("studio.anim.save")) + 10, 16,
                Messages.get("studio.anim.save"), "file:save", "Ctrl+S", false, DOC.isDirty() ? GOLD : TEXT) + 4;

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
        bx = drawBtn(g, font, mx, my, bx, by, 15, 16, "X", "axis:0", "Axis X", axisLock == 0, 0xFFEF4444) + 1;
        bx = drawBtn(g, font, mx, my, bx, by, 15, 16, "Y", "axis:1", "Axis Y", axisLock == 1, 0xFF22C55E) + 1;
        bx = drawBtn(g, font, mx, my, bx, by, 15, 16, "Z", "axis:2", "Axis Z", axisLock == 2, 0xFF3B82F6) + 5;

        g.fill(bx, y + 4, bx + 1, y + h - 4, EDGE);
        bx += 5;

        // Зеркало позы, Копировать/Вставить, В игру, В скрипт
        if (bx + 120 < x + w) {
            bx = drawBtn(g, font, mx, my, bx, by, font.width("⇄") + 10, 16, "⇄", "pose:mirror",
                    Messages.get("studio.anim.tip.mirror") + " (Ctrl+M)", false, GOLD) + 2;
            bx = drawBtn(g, font, mx, my, bx, by, font.width("1P") + 10, 16, "1P", "vp:1p",
                    Messages.get("studio.anim.tip.1p"), firstPersonPreview, GREEN) + 2;
            bx = drawBtn(g, font, mx, my, bx, by, font.width("🎥") + 10, 16, "🎥", "vp:reset_cam",
                    Messages.get("studio.anim.tip.cam") + " [F]", false, TEXT) + 4;
        }

        // Правая часть топ-бара: Тест в игре + Вставить в .sc + Справка [?]
        int rx = x + w - 5;
        rx -= 18;
        drawBtn(g, font, mx, my, rx, by, 18, 16, "?", "ui:help", Messages.get("studio.anim.tip.help"), helpPopup, GOLD);

        String codeLbl = Messages.get("studio.anim.to_script");
        int codeW = font.width(codeLbl) + 10;
        if (rx - codeW - 4 > bx) {
            rx -= (codeW + 3);
            drawBtn(g, font, mx, my, rx, by, codeW, 16, codeLbl, "act:insert_code",
                    Messages.get("studio.anim.tip.to_script"), false, ACCENT);
        }

        String testLbl = Messages.get("studio.anim.test_world");
        int testW = font.width(testLbl) + 10;
        if (rx - testW - 4 > bx) {
            rx -= (testW + 3);
            drawBtn(g, font, mx, my, rx, by, testW, 16, testLbl, "act:test_world",
                    Messages.get("studio.anim.tip.test_world"), false, GREEN);
        }
    }

    // -------------------------------------------------------------------------
    //  2. ЛЕВАЯ ПАНЕЛЬ: ДЕРЕВО КОСТЕЙ + ПРЕСЕТЫ
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
            if (ry + 15 > y + h - 38) break;
            boolean sel = b == selectedBone;
            boolean hot = hover(mx, my, x + 2, ry, w - 4, 15);
            g.fill(x + 2, ry, x + w - 2, ry + 15, sel ? 0xFF24344D : (hot ? ROW_HOT : ROW));
            g.fill(x + 2, ry, x + 4, ry + 15, b.color);
            if (sel) {
                border(g, x + 2, ry, w - 4, 15, GOLD);
            }

            // Глаз видимости кости
            boolean vis = DOC.isBoneVisible(b);
            int eyeX = x + w - 15;
            g.drawString(font, vis ? "◉" : "○", eyeX, ry + 4, vis ? MUTED : 0xFF475264, false);
            btns.add(new BtnHit(eyeX - 1, ry, 13, 15, "vis:" + b.name(), Messages.get("studio.anim.tip.vis")));

            // Индикатор ключа на текущем кадре / вообще на кости + значок Bend
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
            btns.add(new BtnHit(x + 2, ry, eyeX - x - 3, 15, "bone:" + b.name(), null));
            ry += 16;
        }

        // Блок быстрых пресетов внизу левой панели
        int py = y + h - 36;
        if (py > ry + 4) {
            g.fill(x + 4, py - 3, x + w - 4, py - 2, EDGE);
            g.drawString(font, Messages.get("studio.anim.presets"), x + 6, py, MUTED, false);
            int pbx = x + 4;
            int pby = py + 11;
            int cellW = (w - 14) / 5;
            String[][] presets = {
                    {"wave", "👋", Messages.get("studio.anim.preset.wave")},
                    {"slash", "⚔", Messages.get("studio.anim.preset.slash")},
                    {"backflip", "↻", Messages.get("studio.anim.preset.flip")},
                    {"dance", "♪", Messages.get("studio.anim.preset.dance")},
                    {"bow", "🙇", Messages.get("studio.anim.preset.bow")}
            };
            for (String[] pr : presets) {
                drawBtn(g, font, mx, my, pbx, pby, Math.max(18, cellW), 16, pr[1], "preset:" + pr[0], pr[2], false, GOLD);
                pbx += Math.max(18, cellW) + 1;
            }
        }
    }

    // -------------------------------------------------------------------------
    //  3. ЦЕНТРАЛЬНЫЙ 3D-ВЬЮПОРТ + СЕТКА + ВЫДЕЛЕНИЕ МЫШЬЮ + 3D-ГИЗМО
    // -------------------------------------------------------------------------

    private static void renderViewport3D(GuiGraphics g, Font font, int x, int y, int w, int h, int mx, int my) {
        g.fillGradient(x, y, x + w, y + h, 0xFF0E1420, 0xFF172234);
        border(g, x, y, w, h, EDGE);

        boolean paLoaded = ModList.get().isLoaded("playeranimator");
        boolean bendyLoaded = ModList.get().isLoaded("bendylib");

        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);

        float cx = x + w / 2f + camPanX;
        float cy = y + h * 0.54f + camPanY;
        float baseScale = Math.max(26f, Math.min(w * 0.34f, h * 0.42f));
        float pxPerBlock = baseScale * camZoom;

        float effPitch = firstPersonPreview ? 22f : camPitch;
        float effYaw = firstPersonPreview ? 180f : camYaw;
        float pivotY = firstPersonPreview ? 1.52f : 0.90f;

        // Единая матрица проекции Entity-пространства -> Экран
        PoseStack camStack = new PoseStack();
        camStack.translate(cx, cy, 220f);
        camStack.mulPoseMatrix(new Matrix4f().scaling(pxPerBlock, pxPerBlock, -pxPerBlock));
        camStack.mulPose(Axis.ZP.rotationDegrees(180f));
        camStack.mulPose(Axis.XP.rotationDegrees(effPitch));
        camStack.mulPose(Axis.YP.rotationDegrees(effYaw));
        camStack.translate(0f, -pivotY, 0f);
        Matrix4f entityToScreen = new Matrix4f(camStack.last().pose());

        // 1) 3D-сетка пола и оси мира X/Z
        if (showGrid && !firstPersonPreview) {
            renderFloorGrid(g, entityToScreen);
        }

        // 2) Рендер 3D-модели игрока (реальный клиентский игрок с PlayerAnimator + Bendy-lib либо чистый PlayerModel)
        Minecraft mc = Minecraft.getInstance();
        if (paLoaded && mc.player != null) {
            renderLivePlayerWithMods(g, mc.player, cx, cy, pxPerBlock, effPitch, effYaw, pivotY);
        } else {
            renderStandalonePlayerModel(g, mc, cx, cy, pxPerBlock, effPitch, effYaw, pivotY);
        }

        // 3) Проекция скелета на 2D, подсветка кости под мышью и интерактивный 3D-Гизмо
        computeBoneProjections(entityToScreen);
        updateViewportHover(mx, my, x, y, w, h);
        renderSkeletonOverlayAndGizmo(g, font, x, y, w, h, mx, my, entityToScreen);

        // Инфо-строки по углам вьюпорта
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
                    ? "PlayerAnimator + Bendy-lib ●  |  LMB: Gizmo/Bone  RMB: Orbit  Scroll: Zoom"
                    : "PlayerAnimator ●  |  LMB: Gizmo/Bone  RMB: Orbit  Scroll: Zoom";
            g.drawString(font, trim(font, hint, w - 12), x + 6, y + h - 13, 0xAA8A96AA, false);
        }

        g.disableScissor();
    }

    private static void renderFloorGrid(GuiGraphics g, Matrix4f mat) {
        float extent = 1.5f;
        float step = 0.5f;
        for (float v = -extent; v <= extent + 0.01f; v += step) {
            boolean center = Math.abs(v) < 0.01f;
            int colX = center ? 0x993B82F6 : 0x38384A66; // Вдоль Z (синяя центральная ось Z)
            int colZ = center ? 0x99EF4444 : 0x38384A66; // Вдоль X (красная центральная ось X)
            Vector4f a1 = project(mat, v, 0f, -extent);
            Vector4f a2 = project(mat, v, 0f, extent);
            drawLine2D(g, a1.x, a1.y, a2.x, a2.y, colX, 1);

            Vector4f b1 = project(mat, -extent, 0f, v);
            Vector4f b2 = project(mat, extent, 0f, v);
            drawLine2D(g, b1.x, b1.y, b2.x, b2.y, colZ, 1);
        }
        // Стрелка направления взгляда персонажа (-Z в entity space при yBodyRot=180)
        Vector4f f0 = project(mat, 0f, 0.01f, -0.75f);
        Vector4f f1 = project(mat, 0f, 0.01f, -1.25f);
        Vector4f fl = project(mat, -0.16f, 0.01f, -1.05f);
        Vector4f fr = project(mat, 0.16f, 0.01f, -1.05f);
        drawLine2D(g, f0.x, f0.y, f1.x, f1.y, 0xAA4CC38A, 1);
        drawLine2D(g, f1.x, f1.y, fl.x, fl.y, 0xAA4CC38A, 1);
        drawLine2D(g, f1.x, f1.y, fr.x, fr.y, 0xAA4CC38A, 1);
    }

    private static void renderLivePlayerWithMods(GuiGraphics g, AbstractClientPlayer player,
                                                 float cx, float cy, float pxPerBlock,
                                                 float effPitch, float effYaw, float pivotY) {
        PlayerAnimBridge.applyStudioPreview(DOC, playhead, firstPersonPreview);

        float oldBodyRot = player.yBodyRot;
        float oldBodyRotO = player.yBodyRotO;
        float oldYRot = player.getYRot();
        float oldYRotO = player.yRotO;
        float oldXRot = player.getXRot();
        float oldXRotO = player.xRotO;
        float oldHeadRot = player.yHeadRot;
        float oldHeadRotO = player.yHeadRotO;
        boolean oldCustomName = player.isCustomNameVisible();

        player.yBodyRot = 180f;
        player.yBodyRotO = 180f;
        player.setYRot(180f);
        player.yRotO = 180f;
        player.setXRot(0f);
        player.xRotO = 0f;
        player.yHeadRot = 180f;
        player.yHeadRotO = 180f;

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 220.0);
        pose.mulPoseMatrix(new Matrix4f().scaling(pxPerBlock, pxPerBlock, -pxPerBlock));
        pose.mulPose(Axis.ZP.rotationDegrees(180f));
        pose.mulPose(Axis.XP.rotationDegrees(effPitch));
        pose.mulPose(Axis.YP.rotationDegrees(effYaw));
        pose.translate(0f, -pivotY, 0f);

        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        Quaternionf camQuat = new Quaternionf().rotationZ((float) Math.PI);
        dispatcher.overrideCameraOrientation(camQuat);
        dispatcher.setRenderShadow(false);
        RenderSystem.runAsFancy(() ->
                dispatcher.render(player, 0.0, 0.0, 0.0, 0.0f, 1.0f, pose, g.bufferSource(), 0xF000F0));
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
        player.setCustomNameVisible(oldCustomName);

        PlayerAnimBridge.clearStudioPreview();
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

        // Применяем позу из DOC к частям PlayerModel
        applyStandaloneBone(model.head, BoneId.HEAD, 0f, 0f, 0f);
        applyStandaloneBone(model.body, BoneId.TORSO, 0f, 0f, 0f);
        applyStandaloneBone(model.rightArm, BoneId.RIGHT_ARM, -5f, 2f, 0f);
        applyStandaloneBone(model.leftArm, BoneId.LEFT_ARM, 5f, 2f, 0f);
        applyStandaloneBone(model.rightLeg, BoneId.RIGHT_LEG, -1.9f, 12f, 0.1f);
        applyStandaloneBone(model.leftLeg, BoneId.LEFT_LEG, 1.9f, 12f, 0.1f);

        float bodyPxX = DOC.eval(BoneId.BODY, Channel.POS_X, playhead);
        float bodyPxY = DOC.eval(BoneId.BODY, Channel.POS_Y, playhead);
        float bodyPxZ = DOC.eval(BoneId.BODY, Channel.POS_Z, playhead);
        float bodyRx = DOC.eval(BoneId.BODY, Channel.ROT_X, playhead);
        float bodyRy = DOC.eval(BoneId.BODY, Channel.ROT_Y, playhead);
        float bodyRz = DOC.eval(BoneId.BODY, Channel.ROT_Z, playhead);

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 220.0);
        pose.mulPoseMatrix(new Matrix4f().scaling(pxPerBlock, pxPerBlock, -pxPerBlock));
        pose.mulPose(Axis.ZP.rotationDegrees(180f));
        pose.mulPose(Axis.XP.rotationDegrees(effPitch));
        pose.mulPose(Axis.YP.rotationDegrees(effYaw));
        pose.translate(0f, -pivotY, 0f);
        // Точно повторяем матрицу LivingEntityRenderer, чтобы проекции костей совпадали 1-в-1
        pose.scale(-0.9375f, -0.9375f, 0.9375f);
        pose.translate(0f, -1.501f, 0f);
        pose.translate(bodyPxX / 16f, bodyPxY / 16f, bodyPxZ / 16f);
        pose.translate(0f, 12f / 16f, 0f);
        pose.mulPose(Axis.ZP.rotationDegrees(bodyRz));
        pose.mulPose(Axis.YP.rotationDegrees(bodyRy));
        pose.mulPose(Axis.XP.rotationDegrees(bodyRx));
        pose.translate(0f, -12f / 16f, 0f);

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

    private static void applyStandaloneBone(ModelPart part, BoneId bone, float dx, float dy, float dz) {
        part.visible = DOC.isBoneVisible(bone);
        part.x = dx + DOC.eval(bone, Channel.POS_X, playhead);
        part.y = dy + DOC.eval(bone, Channel.POS_Y, playhead);
        part.z = dz + DOC.eval(bone, Channel.POS_Z, playhead);
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

    // --- 3D проецирование скелета для клика мышкой и отрисовки Гизмо ---

    private static void computeBoneProjections(Matrix4f entityToScreen) {
        projectedBones.clear();
        // Базовая трансформация BODY (корень всего тела вокруг поясницы y=12 в ModelPart)
        Matrix4f bodyMat = new Matrix4f(entityToScreen)
                .scale(-0.9375f, -0.9375f, 0.9375f)
                .translate(0f, -1.501f, 0f)
                .translate(
                        DOC.eval(BoneId.BODY, Channel.POS_X, playhead) / 16f,
                        DOC.eval(BoneId.BODY, Channel.POS_Y, playhead) / 16f,
                        DOC.eval(BoneId.BODY, Channel.POS_Z, playhead) / 16f)
                .translate(0f, 12f / 16f, 0f)
                .rotateZ((float) Math.toRadians(DOC.eval(BoneId.BODY, Channel.ROT_Z, playhead)))
                .rotateY((float) Math.toRadians(DOC.eval(BoneId.BODY, Channel.ROT_Y, playhead)))
                .rotateX((float) Math.toRadians(DOC.eval(BoneId.BODY, Channel.ROT_X, playhead)))
                .translate(0f, -12f / 16f, 0f);

        // BODY узел (на поясе y=12)
        addProjectedBone(BoneId.BODY, bodyMat, 0f, 12f, 0f, 0f, 12f, -3f);
        // TORSO (от шеи y=0 до пояса y=12)
        addLimbProjection(BoneId.TORSO, bodyMat, 0f, 0f, 0f, 0f, 11f, 0f);
        // HEAD (от шеи y=0 вверх до макушки y=-8)
        addLimbProjection(BoneId.HEAD, bodyMat, 0f, 0f, 0f, 0f, -7.5f, 0f);
        // РУКИ И ПРЕДМЕТЫ
        Matrix4f rArmMat = addLimbProjection(BoneId.RIGHT_ARM, bodyMat, -5f, 2f, 0f, -1f, 10f, 0f);
        Matrix4f lArmMat = addLimbProjection(BoneId.LEFT_ARM, bodyMat, 5f, 2f, 0f, 1f, 10f, 0f);
        addProjectedItem(BoneId.RIGHT_ITEM, rArmMat, -1f, 10f, -2f);
        addProjectedItem(BoneId.LEFT_ITEM, lArmMat, 1f, 10f, -2f);
        // НОГИ
        addLimbProjection(BoneId.RIGHT_LEG, bodyMat, -1.9f, 12f, 0.1f, 0f, 12f, 0f);
        addLimbProjection(BoneId.LEFT_LEG, bodyMat, 1.9f, 12f, 0.1f, 0f, 12f, 0f);

        // Считаем 3D-гизмо для выбранной кости
        Matrix4f selMat = bonePivotMatrix(selectedBone, bodyMat, rArmMat, lArmMat);
        Vector4f o = project(selMat, 0f, 0f, 0f);
        gizmoOriginX = o.x;
        gizmoOriginY = o.y;
        float axisLen = 0.42f; // в блоках ModelPart-масштаба
        Vector4f tx = project(selMat, axisLen, 0f, 0f);
        Vector4f ty = project(selMat, 0f, axisLen, 0f);
        Vector4f tz = project(selMat, 0f, 0f, axisLen);
        gizmoTipX[0] = tx.x; gizmoTipY[0] = tx.y;
        gizmoTipX[1] = ty.x; gizmoTipY[1] = ty.y;
        gizmoTipX[2] = tz.x; gizmoTipY[2] = tz.y;
    }

    private static Matrix4f bonePivotMatrix(BoneId bone, Matrix4f bodyMat, Matrix4f rArmMat, Matrix4f lArmMat) {
        if (bone == BoneId.BODY) {
            return new Matrix4f(bodyMat).translate(0f, 12f / 16f, 0f);
        }
        if (bone == BoneId.RIGHT_ITEM) {
            return new Matrix4f(rArmMat).translate(-1f / 16f, 10f / 16f, -2f / 16f);
        }
        if (bone == BoneId.LEFT_ITEM) {
            return new Matrix4f(lArmMat).translate(1f / 16f, 10f / 16f, -2f / 16f);
        }
        float px = (bone.defX + DOC.eval(bone, Channel.POS_X, playhead)) / 16f;
        float py = (bone.defY + DOC.eval(bone, Channel.POS_Y, playhead)) / 16f;
        float pz = (bone.defZ + DOC.eval(bone, Channel.POS_Z, playhead)) / 16f;
        return new Matrix4f(bodyMat)
                .translate(px, py, pz)
                .rotateZ((float) Math.toRadians(DOC.eval(bone, Channel.ROT_Z, playhead)))
                .rotateY((float) Math.toRadians(DOC.eval(bone, Channel.ROT_Y, playhead)))
                .rotateX((float) Math.toRadians(DOC.eval(bone, Channel.ROT_X, playhead)));
    }

    private static Matrix4f addLimbProjection(BoneId bone, Matrix4f parent,
                                              float defX, float defY, float defZ,
                                              float tipDx, float tipDy, float tipDz) {
        float px = (defX + DOC.eval(bone, Channel.POS_X, playhead)) / 16f;
        float py = (defY + DOC.eval(bone, Channel.POS_Y, playhead)) / 16f;
        float pz = (defZ + DOC.eval(bone, Channel.POS_Z, playhead)) / 16f;
        Matrix4f m = new Matrix4f(parent)
                .translate(px, py, pz)
                .rotateZ((float) Math.toRadians(DOC.eval(bone, Channel.ROT_Z, playhead)))
                .rotateY((float) Math.toRadians(DOC.eval(bone, Channel.ROT_Y, playhead)))
                .rotateX((float) Math.toRadians(DOC.eval(bone, Channel.ROT_X, playhead)));
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

    private static void addProjectedItem(BoneId bone, Matrix4f armMat, float ox, float oy, float oz) {
        Matrix4f m = new Matrix4f(armMat)
                .translate(
                        (ox + DOC.eval(bone, Channel.POS_X, playhead)) / 16f,
                        (oy + DOC.eval(bone, Channel.POS_Y, playhead)) / 16f,
                        (oz + DOC.eval(bone, Channel.POS_Z, playhead)) / 16f);
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

        // Сначала проверяем ручки 3D-гизмо X / Y / Z
        float bestAxisDist = 11f;
        int maxAxes = toolMode == ToolMode.BEND ? 2 : 3;
        for (int a = 0; a < maxAxes; a++) {
            float d = distToSegment(mx, my, gizmoOriginX, gizmoOriginY, gizmoTipX[a], gizmoTipY[a]);
            if (d < bestAxisDist) {
                bestAxisDist = d;
                hoveredGizmoAxis = a;
            }
        }
        if (hoveredGizmoAxis >= 0) return;

        // Иначе проверяем попадание мыши по капсулам костей (предпочитая ближайшую к камере)
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

    private static void renderSkeletonOverlayAndGizmo(GuiGraphics g, Font font, int x, int y, int w, int h,
                                                      int mx, int my, Matrix4f entityToScreen) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 310f);

        // Подсветка кости при наведении мыши
        if (hoveredViewportBone != null && hoveredViewportBone != selectedBone) {
            for (ProjectedBone pb : projectedBones) {
                if (pb.bone == hoveredViewportBone) {
                    drawLine2D(g, pb.sx, pb.sy, pb.ex, pb.ey, 0xAAFFFFFF, 2);
                    int jx = Math.round(pb.sx), jy = Math.round(pb.sy);
                    g.fill(jx - 3, jy - 3, jx + 3, jy + 3, pb.bone.color);
                    g.drawString(font, Messages.get(pb.bone.langKey), mx + 8, my - 10, pb.bone.color, true);
                    break;
                }
            }
        }

        // Линия выбранной кости + 3D-Гизмо X/Y/Z
        for (ProjectedBone pb : projectedBones) {
            if (pb.bone == selectedBone) {
                drawLine2D(g, pb.sx, pb.sy, pb.ex, pb.ey, (0xAA000000) | (selectedBone.color & 0x00FFFFFF), 2);
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
            drawLine2D(g, gizmoOriginX, gizmoOriginY, gizmoTipX[a], gizmoTipY[a], col, active ? 2 : 1);
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

        // Кнопки "+ Ключ [I]" и "Сброс кости"
        int curTick = Math.round(playhead);
        drawBtn(g, font, mx, my, x + w - 50, y + 2, 24, 14, "+◆", "bone:key_all",
                Messages.get("studio.anim.tip.insert_key") + " [I]", DOC.boneHasKeyAt(selectedBone, curTick), GOLD);
        drawBtn(g, font, mx, my, x + w - 24, y + 2, 21, 14, "↺", "bone:reset",
                Messages.get("studio.anim.tip.reset_bone"), false, RED);

        g.enableScissor(x + 1, y + 18, x + w - 1, y + h - 1);
        int cy = y + 21;

        // Поворот (Pitch, Yaw, Roll)
        g.drawString(font, Messages.get("studio.anim.sec.rot"), x + 6, cy, MUTED, false);
        cy += 11;
        cy = renderChannelRow(g, font, mx, my, x + 6, cy, w - 12, 0, Channel.ROT_X, "X");
        cy = renderChannelRow(g, font, mx, my, x + 6, cy, w - 12, 1, Channel.ROT_Y, "Y");
        cy = renderChannelRow(g, font, mx, my, x + 6, cy, w - 12, 2, Channel.ROT_Z, "Z");
        cy += 3;

        // Позиция (X, Y, Z)
        g.drawString(font, Messages.get("studio.anim.sec.pos"), x + 6, cy, MUTED, false);
        cy += 11;
        cy = renderChannelRow(g, font, mx, my, x + 6, cy, w - 12, 3, Channel.POS_X, "X");
        cy = renderChannelRow(g, font, mx, my, x + 6, cy, w - 12, 4, Channel.POS_Y, "Y");
        cy = renderChannelRow(g, font, mx, my, x + 6, cy, w - 12, 5, Channel.POS_Z, "Z");
        cy += 3;

        // Изгиб Bendy-lib
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

        // Блок Easing + мини-график кривой
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

        // Компактные настройки анимации (EndTick, Loop, Return, Fade)
        if (cy + 34 <= y + h) {
            g.fill(x + 6, cy, x + w - 6, cy + 1, EDGE);
            cy += 4;
            int halfW = (w - 16) / 2;
            g.drawString(font, "End:", x + 6, cy + 3, MUTED, false);
            int efX = x + 30;
            int efW = Math.max(26, halfW - 24);
            if (activeFieldIdx != 8) endTickField.setValue(Integer.toString(DOC.endTick));
            endTickField.render(g, font, efX, cy, efW, 13, activeFieldIdx == 8, "40", 0xFF0C1119,
                    activeFieldIdx == 8 ? GOLD : EDGE, TEXT, MUTED);
            fieldBoxes.add(new FieldBox(8, efX, cy, efW, 13, endTickField, null));

            int fx = x + 8 + halfW;
            g.drawString(font, "Fade:", fx, cy + 3, MUTED, false);
            int ffX = fx + 28;
            int ffW = Math.max(22, x + w - 6 - ffX);
            if (activeFieldIdx != 9) fadeField.setValue(Integer.toString(DOC.beginTick));
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
            if (activeFieldIdx != 10) returnTickField.setValue(Integer.toString(DOC.returnTick));
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

        // Цветная метка оси (за неё можно тянуть мышкой влево-вправо для плавного скраббинга!)
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
        if (activeFieldIdx != fieldIdx) {
            float val = DOC.eval(selectedBone, ch, playhead);
            tf.setValue(formatNum(val));
        }
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
        // Пунктир 0 и 1
        g.fill(x + padX, y + padY, x + padX + gw, y + padY + 1, 0x33425474);
        g.fill(x + padX, y + padY + gh, x + padX + gw, y + padY + gh + 1, 0x33425474);

        float prevX = x + padX;
        float prevY = y + padY + gh;
        int steps = Math.max(12, gw);
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            float v = curve.eval(t);
            float sx = x + padX + t * gw;
            float sy = y + padY + gh - v * gh;
            sy = Math.max(y + 1, Math.min(y + h - 2, sy));
            drawLine2D(g, prevX, prevY, sx, sy, ACCENT, 1);
            prevX = sx;
            prevY = sy;
        }
        float animT = (System.currentTimeMillis() % 1400L) / 1400f;
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

        // Панель транспорта (20px)
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

        String timeStr = String.format(Locale.ROOT, "T: %.1f / %d  (%.2fs)", playhead, DOC.endTick, playhead / 20f);
        g.drawString(font, timeStr, bx, y + 6, GOLD, false);
        bx += font.width(timeStr) + 8;

        String spdLbl = String.format(Locale.ROOT, "%.2gx", playbackSpeed);
        bx = drawBtn(g, font, mx, my, bx, by, font.width(spdLbl) + 10, 14, spdLbl, "tl:speed",
                Messages.get("studio.anim.tip.speed"), false, ACCENT) + 4;

        String modeLbl = expandChannels
                ? Messages.get("studio.anim.tl.channels")
                : Messages.get("studio.anim.tl.bones");
        bx = drawBtn(g, font, mx, my, bx, by, font.width(modeLbl) + 10, 14, modeLbl, "tl:mode",
                Messages.get("studio.anim.tip.tl_mode"), expandChannels, GOLD) + 4;

        // Справа на таймлайне: Вставить ключ, Копировать/Вставить, Удалить выбранные, Зум
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

        // Разметка сетки дорожек
        int labelColW = 114;
        int rulerY = y + barH + 1;
        int rulerH = 14;
        tlTrackX = x + labelColW;
        tlTrackW = Math.max(40, w - labelColW - 2);
        tlTrackY = rulerY + rulerH;
        tlTrackH = Math.max(20, y + h - tlTrackY - 1);

        // Заголовок колонки дорожек
        g.fill(x + 1, rulerY, tlTrackX, rulerY + rulerH, 0xFF121824);
        g.drawString(font, expandChannels ? trim(font, Messages.get(selectedBone.langKey), labelColW - 8) : "Track",
                x + 6, rulerY + 3, MUTED, false);
        g.fill(tlTrackX - 1, rulerY, tlTrackX, y + h - 1, EDGE);

        // Линейка времени (Ruler)
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

        // Маркеры returnTick и endTick
        if (DOC.looped) {
            int rtx = tickToX(DOC.returnTick);
            g.fill(rtx, rulerY, rtx + 1, y + h - 1, 0x884CC38A);
        }
        int etx = tickToX(DOC.endTick);
        g.fill(etx, rulerY, etx + 1, y + h - 1, 0xAAF0B85B);
        g.disableScissor();

        // Дорожки и ромбики ключевых кадров
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

        // Рамка выделения мышкой (Marquee Box-Selection)
        if (dragKind == DragKind.MARQUEE_SELECT) {
            int bx0 = Math.min(dragStartX, dragCurX);
            int by0 = Math.min(dragStartY, dragCurY);
            int bw = Math.abs(dragCurX - dragStartX);
            int bh = Math.abs(dragCurY - dragStartY);
            g.fill(bx0, by0, bx0 + bw, by0 + bh, 0x3858A6FF);
            border(g, bx0, by0, Math.max(1, bw), Math.max(1, bh), ACCENT);
        }
        g.disableScissor();

        // Красный ползунок Playhead поверх линейки и дорожек
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
    //  6. МОДАЛЬНЫЕ ОКНА (ОТКРЫТЬ ФАЙЛ, НОВАЯ АНИМАЦИЯ, ВЫБОР EASE, СПРАВКА)
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
                "LMB (3D) — Pick bone / Drag Gizmo   |   RMB / MMB — Orbit / Pan",
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

        // 1) Клики по текстовым полям (включая модалку создания)
        for (FieldBox fb : fieldBoxes) {
            if (hover(mx, my, fb.x, fb.y, fb.w, fb.h)) {
                activeFieldIdx = fb.idx;
                fb.field.mouseClicked(font, mx, my, button);
                return true;
            }
        }

        // 2) Клики по зарегистрированным кнопкам (с конца — верхние слои/модалки первыми!)
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

        // 3) Разделитель высоты таймлайна
        if (Math.abs(my - tlY) <= 3 && mx >= rootX && mx < rootX + rootW) {
            dragKind = DragKind.TIMELINE_RESIZE;
            dragStartY = my;
            dragStartTimelineH = timelineH;
            return true;
        }

        // 4) Клик в 3D-вьюпорте (выбор кости, перетаскивание Гизмо, орбита, панорама)
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
                        syncInspectorFromDoc();
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

        // 5) Линейка таймлайна -> перемотка Playhead
        int rulerY = tlY + 21;
        if (hover(mx, my, tlTrackX, rulerY, tlTrackW, 14)) {
            playing = false;
            playhead = Math.max(0f, (mx - tlTrackX + timelineScrollX) / pxPerTick);
            syncInspectorFromDoc();
            dragKind = DragKind.SCRUB_PLAYHEAD;
            return true;
        }

        // 6) Поле ключей таймлайна -> клик по ромбику (перетаскивание) или рамка выделения (Marquee)
        if (hover(mx, my, tlTrackX, tlTrackY, tlTrackW, tlTrackH)) {
            if (button == 1) {
                playing = false;
                playhead = Math.max(0f, (mx - tlTrackX + timelineScrollX) / pxPerTick);
                syncInspectorFromDoc();
                dragKind = DragKind.SCRUB_PLAYHEAD;
                return true;
            }
            DiamondHit hit = findDiamondAt(mx, my);
            if (hit != null) {
                boolean shift = Screen.hasShiftDown();
                if (!expandChannels) {
                    // В сводном режиме кости выбираем все каналы этой кости на данном тике
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
                syncInspectorFromDoc();

                // Готовим перетаскивание выбранных ключей по времени
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
                camYaw = dragStartYaw + (mx - dragStartX) * 0.75f;
                camPitch = Math.max(-85f, Math.min(85f, dragStartPitch + (my - dragStartY) * 0.65f));
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
                    float factor = scrubChannel.angle ? 0.5f : 0.05f;
                    if (Screen.hasShiftDown()) factor *= 0.2f;
                    float next = scrubInitVal + (mx - dragStartX) * factor;
                    DOC.putKey(selectedBone, scrubChannel, Math.round(playhead), next, activeEase);
                    syncInspectorFromDoc();
                }
                return true;
            }
            case SCRUB_PLAYHEAD -> {
                playhead = Math.max(0f, (mx - tlTrackX + timelineScrollX) / pxPerTick);
                syncInspectorFromDoc();
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
                syncInspectorFromDoc();
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
                syncInspectorFromDoc();
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
            return false; // позволяем StudioScreen закрыть окно
        }

        if (ctrl && key == GLFW.GLFW_KEY_S) {
            saveCurrentToServer();
            return true;
        }
        if (ctrl && (key == GLFW.GLFW_KEY_Y || (shift && key == GLFW.GLFW_KEY_Z))) {
            if (DOC.redo()) syncInspectorFromDoc();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Z) {
            if (DOC.undo()) syncInspectorFromDoc();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_M) {
            DOC.pushUndo();
            DOC.mirrorPoseAt(Math.round(playhead), activeEase);
            syncInspectorFromDoc();
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
            syncInspectorFromDoc();
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            playing = false;
            playhead = Math.max(0f, Math.round(playhead) + (shift ? 5 : 1));
            syncInspectorFromDoc();
            return true;
        }
        if (key == GLFW.GLFW_KEY_HOME) {
            playing = false;
            playhead = 0f;
            syncInspectorFromDoc();
            return true;
        }
        if (key == GLFW.GLFW_KEY_END) {
            playing = false;
            playhead = DOC.endTick;
            syncInspectorFromDoc();
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
                syncInspectorFromDoc();
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
    //  ВНУТРЕННЯЯ ЛОГИКА ДЕЙСТВИЙ
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
                syncInspectorFromDoc();
            }
            return;
        }
        if (id.startsWith("vis:")) {
            DOC.toggleBoneVisible(BoneId.valueOf(id.substring("vis:".length())));
            return;
        }
        if (id.startsWith("preset:")) {
            StudioAnimModel.applyPreset(DOC, id.substring("preset:".length()));
            playhead = 0f;
            playing = true;
            lastPlayNano = System.nanoTime();
            selectedKeys.clear();
            syncInspectorFromDoc();
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
            syncInspectorFromDoc();
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
            syncInspectorFromDoc();
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
                syncInspectorFromDoc();
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
            }
            case "tl:play" -> togglePlay();
            case "tl:stop" -> {
                playing = false;
                playhead = 0f;
                syncInspectorFromDoc();
            }
            case "tl:start" -> {
                playing = false;
                playhead = 0f;
                syncInspectorFromDoc();
            }
            case "tl:end" -> {
                playing = false;
                playhead = DOC.endTick;
                syncInspectorFromDoc();
            }
            case "tl:prev" -> {
                playing = false;
                playhead = Math.max(0f, Math.round(playhead) - 1);
                syncInspectorFromDoc();
            }
            case "tl:next" -> {
                playing = false;
                playhead = Math.round(playhead) + 1;
                syncInspectorFromDoc();
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
        playhead = 0f;
        playing = false;
        selectedKeys.clear();
        newModal = false;
        activeFieldIdx = -1;
        syncInspectorFromDoc();
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

    private static void startGizmoDrag(int mx, int my, int axis) {
        DOC.pushUndo();
        dragKind = DragKind.GIZMO_POSE;
        dragStartX = mx;
        dragStartY = my;
        dragGizmoAxis = axis;
        if (toolMode == ToolMode.ROTATE) {
            dragInitA = DOC.eval(selectedBone, Channel.ROT_X, playhead);
            dragInitB = DOC.eval(selectedBone, Channel.ROT_Y, playhead);
            dragInitC = DOC.eval(selectedBone, Channel.ROT_Z, playhead);
        } else if (toolMode == ToolMode.MOVE) {
            dragInitA = DOC.eval(selectedBone, Channel.POS_X, playhead);
            dragInitB = DOC.eval(selectedBone, Channel.POS_Y, playhead);
            dragInitC = DOC.eval(selectedBone, Channel.POS_Z, playhead);
        } else {
            dragInitA = DOC.eval(selectedBone, Channel.BEND_DIR, playhead);
            dragInitB = DOC.eval(selectedBone, Channel.BEND_ANG, playhead);
        }
    }

    private static void applyGizmoDrag(int mx, int my) {
        int tick = Math.round(playhead);
        float dx = mx - dragStartX;
        float dy = my - dragStartY;
        float fine = Screen.hasShiftDown() ? 0.25f : 1.0f;

        if (toolMode == ToolMode.ROTATE) {
            float scale = 0.65f * fine;
            if (dragGizmoAxis == 0) {
                DOC.putKey(selectedBone, Channel.ROT_X, tick, dragInitA + (dx + dy) * scale, activeEase);
            } else if (dragGizmoAxis == 1) {
                DOC.putKey(selectedBone, Channel.ROT_Y, tick, dragInitB + dx * scale, activeEase);
            } else if (dragGizmoAxis == 2) {
                DOC.putKey(selectedBone, Channel.ROT_Z, tick, dragInitC + dx * scale, activeEase);
            } else {
                DOC.putKey(selectedBone, Channel.ROT_X, tick, dragInitA + dy * scale, activeEase);
                DOC.putKey(selectedBone, Channel.ROT_Z, tick, dragInitC - dx * scale, activeEase);
            }
        } else if (toolMode == ToolMode.MOVE) {
            float scale = 0.06f * fine;
            if (dragGizmoAxis == 0) {
                DOC.putKey(selectedBone, Channel.POS_X, tick, dragInitA - dx * scale, activeEase);
            } else if (dragGizmoAxis == 1) {
                DOC.putKey(selectedBone, Channel.POS_Y, tick, dragInitB + dy * scale, activeEase);
            } else if (dragGizmoAxis == 2) {
                DOC.putKey(selectedBone, Channel.POS_Z, tick, dragInitC + (dx - dy) * scale, activeEase);
            } else {
                DOC.putKey(selectedBone, Channel.POS_X, tick, dragInitA - dx * scale, activeEase);
                DOC.putKey(selectedBone, Channel.POS_Y, tick, dragInitB + dy * scale, activeEase);
            }
        } else if (toolMode == ToolMode.BEND && selectedBone.bendable) {
            float scale = 0.55f * fine;
            if (dragGizmoAxis == 0) {
                DOC.putKey(selectedBone, Channel.BEND_DIR, tick, dragInitA + dx * scale, activeEase);
            } else if (dragGizmoAxis == 1) {
                DOC.putKey(selectedBone, Channel.BEND_ANG, tick, dragInitB + (dx - dy) * scale, activeEase);
            } else {
                DOC.putKey(selectedBone, Channel.BEND_DIR, tick, dragInitA + dx * scale, activeEase);
                DOC.putKey(selectedBone, Channel.BEND_ANG, tick, dragInitB - dy * scale, activeEase);
            }
        }
        syncInspectorFromDoc();
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
            }
        } else if (activeFieldIdx == 9) {
            Integer v = parseIntSafe(fadeField.value());
            if (v != null && v >= 0) {
                DOC.pushUndo();
                DOC.beginTick = Math.min(100, v);
                DOC.stopTick = DOC.endTick + Math.max(1, DOC.beginTick);
            }
        } else if (activeFieldIdx == 10) {
            Integer v = parseIntSafe(returnTickField.value());
            if (v != null && v >= 0) {
                DOC.pushUndo();
                DOC.returnTick = Math.min(DOC.endTick, v);
            }
        }
    }

    private static void syncInspectorFromDoc() {
        for (int i = 0; i < 8; i++) {
            Channel ch = Channel.values()[i];
            channelFields[i].setValue(formatNum(DOC.eval(selectedBone, ch, playhead)));
        }
        endTickField.setValue(Integer.toString(DOC.endTick));
        fadeField.setValue(Integer.toString(DOC.beginTick));
        returnTickField.setValue(Integer.toString(DOC.returnTick));
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
        camYaw = -26f;
        camPitch = 14f;
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
        syncInspectorFromDoc();
    }

    private static void resetBoneAtPlayhead(BoneId bone) {
        DOC.pushUndo();
        int t = Math.round(playhead);
        for (Channel ch : Channel.values()) {
            if (DOC.hasKeys(bone, ch)) {
                DOC.putKey(bone, ch, t, 0f, activeEase);
            }
        }
        syncInspectorFromDoc();
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
        syncInspectorFromDoc();
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
        syncInspectorFromDoc();
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

    private static void drawLine2D(GuiGraphics g, float x0, float y0, float x1, float y1, int color, int thickness) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        int steps = Math.max(1, Math.min(320, Math.round(len * 1.15f)));
        int r = Math.max(1, thickness);
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int px = Math.round(x0 + dx * t);
            int py = Math.round(y0 + dy * t);
            g.fill(px, py, px + r, py + r, color);
        }
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
        if (Math.abs(v - Math.round(v)) < 0.01f) {
            return Integer.toString(Math.round(v));
        }
        return String.format(Locale.ROOT, "%.1f", v);
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
