package com.scriptcraft.client;

import com.scriptcraft.lang.Messages;
import com.scriptcraft.network.StudioLivePacket;
import com.scriptcraft.screen.Anchor;
import com.scriptcraft.screen.BarDirection;
import com.scriptcraft.screen.BarElement;
import com.scriptcraft.screen.ButtonElement;
import com.scriptcraft.screen.ImageElement;
import com.scriptcraft.screen.InputElement;
import com.scriptcraft.screen.Layout;
import com.scriptcraft.screen.ListElement;
import com.scriptcraft.screen.ScreenElement;
import com.scriptcraft.screen.ScreenMeta;
import com.scriptcraft.screen.SliderElement;
import com.scriptcraft.screen.SlotElement;
import com.scriptcraft.screen.TextElement;
import com.scriptcraft.screen.ToggleElement;
import com.scriptcraft.script.parser.Tokenizer;
import com.scriptcraft.script.parser.Tokenizer.Token;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Визуальный редактор экранов (GUI Builder) внутри ScriptCraft Studio:
 * - автоматически находит все блоки {@code open screen} и {@code create hud} в открытом .sc скрипте
 *   и разделяет их на отдельные экраны для просмотра и редактирования;
 * - рендерит элементы 1-в-1 через {@link ElementRenderer};
 * - поддерживает перетаскивание мышкой, ресайз за 8 маркеров, сетку и привязку;
 * - содержит перемещаемые и сворачиваемые/скрываемые панели:
 *   1) Палитра элементов (+ Box, + Text, + Button, + Image, + Bar, + Slot, + Input, + Slider, + Toggle, + List),
 *   2) Слои в стиле Photoshop (видимость 👁, блокировка 🔒, фильтр по номеру слоя, порядок ▲/▼, дублирование/удаление),
 *   3) Инспектор свойств справа (3×3 якорь anchor, палитра цветов, градиент, скругление, тень, blur, run, tooltip и т.д.);
 * - генерирует чистый код {@code open screen} и обновляет блок прямо в активном скрипте.
 */
@OnlyIn(Dist.CLIENT)
public final class StudioGuiEditor {
    private static final int BG_DARK = 0xFF0A0E15;
    private static final int PANEL_BG = 0xF4131A26;
    private static final int PANEL_HDR = 0xF8182130;
    private static final int ROW_BG = 0xFF1B2434;
    private static final int ROW_HOT = 0xFF263349;
    private static final int ROW_SEL = 0xFF233856;
    private static final int EDGE = 0xFF2B364A;
    private static final int EDGE_HOT = 0xFF4A6084;
    private static final int GOLD = 0xFFF0B85B;
    private static final int ACCENT = 0xFF58A6FF;
    private static final int GREEN = 0xFF4CC38A;
    private static final int RED = 0xFFE56B6F;
    private static final int TEXT = 0xFFE8EDF5;
    private static final int MUTED = 0xFF8A96AA;

    private static final int TOP_BAR_H = 22;
    private static final Pattern SIZE_PAT = Pattern.compile("(\\d{1,4})x(\\d{1,4})");
    private static final Pattern OPTION_REF = Pattern.compile("\\{@([A-Za-z0-9_-]+)}");

    private static final int[] SWATCHES = {
            0xE60D1322, 0xCC162032, 0xFF1E293B, 0xCC000000,
            0xFFFFFFFF, 0xFF94A3B8, 0xFFFACC15, 0xFFF59E0B,
            0xFF38BDF8, 0xFF3B82F6, 0xFF4ADE80, 0xFF10B981,
            0xFFEF4444, 0xFFF43F5E, 0xFFA855F7, 0x00000000
    };

    /** Описание одного элемента на холсте. */
    public static final class GuiNode {
        public String kind = "box"; // box, text, button, image, bar, slot, input, slider, toggle, list, raw
        public String id = "";
        public int x = 0;
        public int y = 0;
        public int w = 100;
        public int h = 40;
        public Anchor anchor = Anchor.CENTER;
        public int layer = 0;
        public int color = 0xFFFFFFFF;
        public boolean colorSet = false;

        // Primary content
        public String text = ""; // text, button label, image texture, slot item, slider/toggle label, bar text
        public boolean shadow = false;

        // Box properties
        public int boxGrad = 0;
        public int boxBorder = 0;
        public int boxBorderW = 1;
        public int boxRadius = 0;
        public boolean boxHoriz = false;
        public boolean boxBlur = false;

        // Image / Sprite properties
        public int texW = 0;
        public int texH = 0;
        public int frames = 1;
        public int fps = 8;
        public double scale = 1.0;
        public double hoverScale = 1.0;
        public double rotation = 0.0;
        public boolean followCursor = false;
        public int hoverCircle = 0;
        public int hoverCircleColor = 0x38BDF8;

        // Slot properties
        public SlotElement.Content slotContent = SlotElement.Content.ITEM;
        public int slotCount = 1;
        public boolean slotFrame = true;

        // Bar properties
        public String barValue = "75";
        public String barMax = "100";
        public BarDirection barDir = BarDirection.LEFT_TO_RIGHT;
        public int barBgColor = 0x80000000;
        public String barFillTex = "";
        public String barBgTex = "";

        // Input / Slider / Toggle
        public String inputValue = "";
        public String placeholder = "";
        public int maxLength = 64;
        public double sliderMin = 0;
        public double sliderMax = 100;
        public double sliderVal = 50;
        public double sliderStep = 1;
        public boolean toggleChecked = false;

        // List properties
        public String listHeader = "";
        public String listFrom = "";
        public boolean listHoriz = false;
        public boolean listSelect = false;
        public boolean listNoScrollbar = false;
        public final List<String> listRawRows = new ArrayList<>();
        public final List<ListElement.Row> listPreviewRows = new ArrayList<>();

        // Actions & meta
        public String runAction = ""; // "", "close", "/cmd", "Func(player)"
        public boolean isFunctionCall = false;
        public boolean keepOpen = false;
        public String hotkey = "";
        public String tooltip = "";
        public boolean scriptHidden = false;

        // Photoshop Layer states (editor-only)
        public boolean editorVisible = true;
        public boolean editorLocked = false;

        // Preserved script comments above this element or raw unparsed line
        public final List<String> leadingComments = new ArrayList<>();
        public String rawUnparsedLine = null;
        /** 0-based индекс строки этого элемента в открытом ScriptDocument (для совместного редактирования). */
        public int sourceLine = -1;

        public GuiNode copy() {
            GuiNode c = new GuiNode();
            c.kind = kind;
            c.id = id;
            c.x = x;
            c.y = y;
            c.w = w;
            c.h = h;
            c.anchor = anchor;
            c.layer = layer;
            c.color = color;
            c.colorSet = colorSet;
            c.text = text;
            c.shadow = shadow;
            c.boxGrad = boxGrad;
            c.boxBorder = boxBorder;
            c.boxBorderW = boxBorderW;
            c.boxRadius = boxRadius;
            c.boxHoriz = boxHoriz;
            c.boxBlur = boxBlur;
            c.texW = texW;
            c.texH = texH;
            c.frames = frames;
            c.fps = fps;
            c.scale = scale;
            c.hoverScale = hoverScale;
            c.rotation = rotation;
            c.followCursor = followCursor;
            c.hoverCircle = hoverCircle;
            c.hoverCircleColor = hoverCircleColor;
            c.slotContent = slotContent;
            c.slotCount = slotCount;
            c.slotFrame = slotFrame;
            c.barValue = barValue;
            c.barMax = barMax;
            c.barDir = barDir;
            c.barBgColor = barBgColor;
            c.barFillTex = barFillTex;
            c.barBgTex = barBgTex;
            c.inputValue = inputValue;
            c.placeholder = placeholder;
            c.maxLength = maxLength;
            c.sliderMin = sliderMin;
            c.sliderMax = sliderMax;
            c.sliderVal = sliderVal;
            c.sliderStep = sliderStep;
            c.toggleChecked = toggleChecked;
            c.listHeader = listHeader;
            c.listFrom = listFrom;
            c.listHoriz = listHoriz;
            c.listSelect = listSelect;
            c.listNoScrollbar = listNoScrollbar;
            c.listRawRows.addAll(listRawRows);
            c.listPreviewRows.addAll(listPreviewRows);
            c.runAction = runAction;
            c.isFunctionCall = isFunctionCall;
            c.keepOpen = keepOpen;
            c.hotkey = hotkey;
            c.tooltip = tooltip;
            c.scriptHidden = scriptHidden;
            c.editorVisible = editorVisible;
            c.editorLocked = editorLocked;
            c.rawUnparsedLine = rawUnparsedLine;
            c.sourceLine = sourceLine;
            return c;
        }
    }

    /** Один найденный (или созданный) экран внутри скрипта. */
    public static final class ParsedScreenBlock {
        public boolean isHud = false;
        public String title = "Меню";
        public String targetClause = "to player";
        public String contextName = ""; // например "function TdOpenMenu" или "command /menu"
        public int headerLine = -1;     // 0-based индекс строки "open screen ...:" (-1 для нового экрана)
        public int bodyStartLine = -1;  // 0-based первая строка тела
        public int bodyEndLine = -1;    // 0-based индекс строки после тела (exclusive)
        public String headerIndent = "\t";
        public String bodyIndent = "\t\t";
        public final List<GuiNode> nodes = new ArrayList<>();

        public String displayName(int index, int total) {
            String kindTag = isHud ? "HUD " : "";
            String ctx = contextName.isEmpty() ? "" : " · " + contextName;
            String ln = headerLine >= 0 ? " (:" + (headerLine + 1) + ")" : " (new)";
            return "#" + (index + 1) + "/" + total + " " + kindTag + "\"" + title + "\"" + ctx + ln;
        }
    }

    /** Плавающее окно инструментария (перемещаемое и сворачиваемое/скрываемое). */
    private static final class FloatingDock {
        final String id;
        int x, y, w, h;
        boolean open = true;
        boolean collapsed = false;
        boolean placed = false;
        int scroll = 0;
        int maxScroll = 0;

        FloatingDock(String id, int w, int h) {
            this.id = id;
            this.w = w;
            this.h = h;
        }

        int currentH() {
            return collapsed ? 18 : h;
        }
    }

    private static final class HitBtn {
        final int x, y, w, h;
        final String id;
        final String tip;

        HitBtn(int x, int y, int w, int h, String id, String tip) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.id = id;
            this.tip = tip;
        }
    }

    private static final class LayerRowHit {
        final int x, y, w, h;
        final int nodeIndex;
        final int eyeX, lockX, upX, downX;

        LayerRowHit(int x, int y, int w, int h, int nodeIndex, int eyeX, int lockX, int upX, int downX) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.nodeIndex = nodeIndex;
            this.eyeX = eyeX;
            this.lockX = lockX;
            this.upX = upX;
            this.downX = downX;
        }
    }

    private enum DragMode {
        NONE,
        DOCK_MOVE,
        DOCK_RESIZE_H,
        CANVAS_PAN,
        NODE_MOVE,
        NODE_N, NODE_S, NODE_E, NODE_W, NODE_NE, NODE_NW, NODE_SE, NODE_SW
    }

    // Состояние редактора
    private static String parsedDocName = "";
    private static String parsedDocSnapshot = "";
    private static final List<ParsedScreenBlock> screens = new ArrayList<>();
    private static int activeScreenIdx = 0;
    private static int selectedNodeIdx = -1;
    private static boolean dirtyOnCanvas = false;
    private static final Map<String, String> fileOptions = new LinkedHashMap<>();

    // Undo / Redo стек для текущего экрана на холсте
    private static final Deque<List<GuiNode>> undoStack = new ArrayDeque<>();
    private static final Deque<List<GuiNode>> redoStack = new ArrayDeque<>();

    // Настройки холста
    private static float canvasZoom = 1.0f;
    private static float panX = 0f;
    private static float panY = 0f;
    private static boolean zoomInitialized = false;
    private static int gridSize = 4; // 0 = off, 2, 4, 8, 16
    private static boolean snapToGrid = true;
    private static boolean showScriptHidden = false;
    private static boolean showAnchorGuides = true;
    private static boolean allPanelsHiddenTab = false;
    private static boolean screenDropdownOpen = false;
    private static Integer layerFilter = null; // null = все слои, иначе конкретный номер layer
    private static int colorTarget = 0; // 0 = color, 1 = gradient/barBg, 2 = border/hoverColor

    // Три плавающие панели
    private static final FloatingDock paletteDock = new FloatingDock("palette", 148, 224);
    private static final FloatingDock layersDock = new FloatingDock("layers", 184, 186);
    private static final FloatingDock inspectorDock = new FloatingDock("inspector", 198, 330);

    // Ввод текста в инспекторе (полноценное поле с курсором, мышкой и выделением)
    private static String focusedField = null;
    private static boolean fieldUndoPushed = false;
    private static final StudioTextField activeField = new StudioTextField().maxLength(1024);
    // Раскрываемый редактор полной строки для длинных текстов (текст, тултип, run и т.д.)
    private static String expandedField = null;
    private static String expandedBeforeVal = "";
    private static final StudioTextField expandedInput = new StudioTextField().maxLength(2048);
    private static int expX, expY, expW, expH, expOkX, expOkY, expCancelX;

    // Перетаскивание
    private static DragMode dragMode = DragMode.NONE;
    private static FloatingDock draggedDock = null;
    private static int dragStartMouseX, dragStartMouseY;
    private static float dragStartPanX, dragStartPanY;
    private static int dragStartX, dragStartY, dragStartW, dragStartH;

    // Геометрия последнего кадра
    private static int viewX, viewY, viewW, viewH;
    private static int virtualW = 480, virtualH = 270;
    private static float effScale = 1f;
    private static int stageX, stageY, stageW, stageH;
    private static String hoverTip = null;
    private static int hoverTipX, hoverTipY;

    private static final List<HitBtn> topHits = new ArrayList<>();
    private static final List<HitBtn> dropdownHits = new ArrayList<>();
    private static final List<HitBtn> paletteHits = new ArrayList<>();
    private static final List<HitBtn> layerBtnHits = new ArrayList<>();
    private static final List<LayerRowHit> layerRowHits = new ArrayList<>();
    private static final List<HitBtn> inspectorHits = new ArrayList<>();

    private StudioGuiEditor() {
    }

    private static boolean isRu() {
        return "ru".equals(Messages.language());
    }

    private static String tr(String ru, String en) {
        return isRu() ? ru : en;
    }

    /** Подсчитывает количество блоков open screen / create hud в документе (для бейджа на кнопке тулбара). */
    public static int countScreensIn(ScriptDocument doc) {
        if (doc == null || !doc.loaded || doc.isImage) return 0;
        int count = 0;
        for (String line : doc.lines) {
            String s = line.strip();
            if (s.startsWith("#") || !s.endsWith(":")) continue;
            String lower = s.toLowerCase(Locale.ROOT);
            if (lower.startsWith("open screen ") || lower.startsWith("create hud ")) {
                count++;
            }
        }
        return count;
    }

    /** Синхронизирует список экранов с открытым документом при входе в GUI-редактор или смене вкладки. */
    public static void ensureSynced(ScriptDocument doc) {
        if (doc == null || !doc.loaded || doc.isImage) {
            screens.clear();
            parsedDocName = "";
            parsedDocSnapshot = "";
            return;
        }
        String text = doc.text();
        boolean sameDoc = doc.name.equals(parsedDocName);
        if (sameDoc && text.equals(parsedDocSnapshot) && !screens.isEmpty()) {
            return;
        }
        // Во время перетаскивания, ввода в поле или локальных неприменённых правок не откатываем холст из-за чужих правок
        if (sameDoc && !screens.isEmpty() && (dragMode != DragMode.NONE || focusedField != null || expandedField != null || dirtyOnCanvas)) {
            return;
        }
        if (sameDoc && !screens.isEmpty()) {
            // Аккуратная пересинхронизация от удалённой правки другого игрока с сохранением выбора, undo и слоёв
            String keepId = selectedNode() != null ? selectedNode().id : "";
            int keepScreen = activeScreenIdx;
            int keepNode = selectedNodeIdx;
            Integer keepFilter = layerFilter;
            Map<String, boolean[]> keepLayerFlags = new LinkedHashMap<>();
            ParsedScreenBlock curScr = activeScreen();
            if (curScr != null) {
                for (int i = 0; i < curScr.nodes.size(); i++) {
                    GuiNode n = curScr.nodes.get(i);
                    String k = !n.id.isEmpty() ? "id:" + n.id : "idx:" + i;
                    keepLayerFlags.put(k, new boolean[]{n.editorVisible, n.editorLocked});
                }
            }
            parseDocument(doc, false);
            activeScreenIdx = Math.max(0, Math.min(keepScreen, screens.size() - 1));
            layerFilter = keepFilter;
            ParsedScreenBlock nextScr = activeScreen();
            if (nextScr != null) {
                for (int i = 0; i < nextScr.nodes.size(); i++) {
                    GuiNode n = nextScr.nodes.get(i);
                    boolean[] fl = !n.id.isEmpty() ? keepLayerFlags.get("id:" + n.id) : keepLayerFlags.get("idx:" + i);
                    if (fl != null) {
                        n.editorVisible = fl[0];
                        n.editorLocked = fl[1];
                    }
                }
                int matched = -1;
                if (!keepId.isEmpty()) {
                    for (int i = 0; i < nextScr.nodes.size(); i++) {
                        if (keepId.equals(nextScr.nodes.get(i).id)) {
                            matched = i;
                            break;
                        }
                    }
                }
                if (matched < 0 && keepNode >= 0 && keepNode < nextScr.nodes.size()) {
                    matched = keepNode;
                }
                selectedNodeIdx = matched;
            }
            return;
        }
        int prevLine = doc.line;
        parseDocument(doc, true);
        // Если курсор в текстовом редакторе стоял внутри какого-то open screen, выбираем его автоматически
        for (int i = 0; i < screens.size(); i++) {
            ParsedScreenBlock b = screens.get(i);
            if (b.headerLine >= 0 && prevLine >= b.headerLine && prevLine <= b.bodyEndLine) {
                activeScreenIdx = i;
                break;
            }
        }
    }

    /** Принудительно перечитывает экраны из текста документа, выбирая блок рядом со строкой курсора. */
    public static void reloadFromDocument(ScriptDocument doc) {
        parsedDocSnapshot = "";
        dirtyOnCanvas = false;
        ensureSynced(doc);
    }

    private static void selectNode(ScriptDocument doc, int idx) {
        selectedNodeIdx = idx;
        ParsedScreenBlock scr = activeScreen();
        if (doc != null && scr != null && idx >= 0 && idx < scr.nodes.size()) {
            GuiNode n = scr.nodes.get(idx);
            if (n.sourceLine >= 0 && n.sourceLine < doc.lines.size()) {
                doc.line = n.sourceLine;
                doc.col = 0;
                doc.anchorLine = -1;
                doc.box = false;
            }
        }
    }

    private static void syncLiveIfNeeded(ScriptDocument doc) {
        if (doc != null && doc.live && dirtyOnCanvas) {
            applyToDocumentInternal(doc, false);
        }
    }

    public static ParsedScreenBlock activeScreen() {
        if (screens.isEmpty()) return null;
        activeScreenIdx = Math.max(0, Math.min(activeScreenIdx, screens.size() - 1));
        return screens.get(activeScreenIdx);
    }

    public static GuiNode selectedNode() {
        ParsedScreenBlock scr = activeScreen();
        if (scr == null || selectedNodeIdx < 0 || selectedNodeIdx >= scr.nodes.size()) return null;
        return scr.nodes.get(selectedNodeIdx);
    }

    public static boolean isDirty() {
        return dirtyOnCanvas;
    }

    private static void pushUndo() {
        ParsedScreenBlock scr = activeScreen();
        if (scr == null) return;
        dirtyOnCanvas = true;
        List<GuiNode> snap = new ArrayList<>(scr.nodes.size());
        for (GuiNode n : scr.nodes) snap.add(n.copy());
        undoStack.addLast(snap);
        while (undoStack.size() > 50) undoStack.removeFirst();
        redoStack.clear();
    }

    public static void undo(ScriptDocument doc) {
        ParsedScreenBlock scr = activeScreen();
        if (scr == null || undoStack.isEmpty()) return;
        List<GuiNode> cur = new ArrayList<>(scr.nodes.size());
        for (GuiNode n : scr.nodes) cur.add(n.copy());
        redoStack.addLast(cur);
        List<GuiNode> prev = undoStack.removeLast();
        scr.nodes.clear();
        scr.nodes.addAll(prev);
        selectedNodeIdx = Math.min(selectedNodeIdx, scr.nodes.size() - 1);
        focusedField = null;
        dirtyOnCanvas = true;
        syncLiveIfNeeded(doc);
    }

    public static void redo(ScriptDocument doc) {
        ParsedScreenBlock scr = activeScreen();
        if (scr == null || redoStack.isEmpty()) return;
        List<GuiNode> cur = new ArrayList<>(scr.nodes.size());
        for (GuiNode n : scr.nodes) cur.add(n.copy());
        undoStack.addLast(cur);
        List<GuiNode> next = redoStack.removeLast();
        scr.nodes.clear();
        scr.nodes.addAll(next);
        selectedNodeIdx = Math.min(selectedNodeIdx, scr.nodes.size() - 1);
        focusedField = null;
        dirtyOnCanvas = true;
        syncLiveIfNeeded(doc);
    }

    // =========================================================================
    // 1. ПАРСЕР СУЩЕСТВУЮЩИХ СКРИПТОВ (находит все open screen / create hud)
    // =========================================================================

    private static void parseDocument(ScriptDocument doc, boolean resetHistory) {
        parsedDocName = doc.name;
        parsedDocSnapshot = doc.text();
        screens.clear();
        fileOptions.clear();
        if (resetHistory) {
            undoStack.clear();
            redoStack.clear();
            selectedNodeIdx = -1;
            layerFilter = null;
        }
        focusedField = null;
        dirtyOnCanvas = false;

        List<String> lines = doc.lines;
        // 1. Собираем options: для подстановки {@name} при предпросмотре
        int i = 0;
        while (i < lines.size()) {
            String raw = lines.get(i);
            String stripped = raw.strip();
            if (indentOf(raw) == 0 && stripped.equalsIgnoreCase("options:")) {
                i++;
                while (i < lines.size()) {
                    String optRaw = lines.get(i);
                    String optStrip = optRaw.strip();
                    if (optStrip.isEmpty() || optStrip.startsWith("#")) {
                        i++;
                        continue;
                    }
                    if (indentOf(optRaw) == 0) break;
                    int colon = optStrip.indexOf(':');
                    if (colon > 0) {
                        fileOptions.put(optStrip.substring(0, colon).strip().toLowerCase(Locale.ROOT),
                                optStrip.substring(colon + 1).strip());
                    }
                    i++;
                }
                continue;
            }
            i++;
        }
        for (int pass = 0; pass < 5; pass++) {
            for (Map.Entry<String, String> e : fileOptions.entrySet()) {
                e.setValue(expandOptions(e.getValue()));
            }
        }

        // 2. Ищем все блоки open screen "..." ...: и create hud "..." ...:
        String currentTopContext = "";
        int lineIdx = 0;
        while (lineIdx < lines.size()) {
            String raw = lines.get(lineIdx);
            String stripped = raw.strip();
            if (stripped.isEmpty() || stripped.startsWith("#")) {
                lineIdx++;
                continue;
            }
            int ind = indentOf(raw);
            if (ind == 0 && (stripped.endsWith(":") || stripped.toLowerCase(Locale.ROOT).startsWith("function "))) {
                String hdr = stripped.endsWith(":") ? stripped.substring(0, stripped.length() - 1).strip() : stripped;
                int paren = hdr.indexOf('(');
                if (paren > 0) hdr = hdr.substring(0, paren).strip();
                if (hdr.toLowerCase(Locale.ROOT).startsWith("function ")) {
                    currentTopContext = hdr.substring(9).strip() + "()";
                } else {
                    currentTopContext = hdr;
                }
            }

            String expandedHeader = expandOptions(stripped);
            String lower = expandedHeader.toLowerCase(Locale.ROOT);
            if (expandedHeader.endsWith(":") && (lower.startsWith("open screen ") || lower.startsWith("create hud "))) {
                boolean isHud = lower.startsWith("create hud ");
                String withoutColon = expandedHeader.substring(0, expandedHeader.length() - 1).strip();
                try {
                    List<Token> tokens = Tokenizer.tokenize(withoutColon);
                    if (tokens.size() >= 3 && tokens.get(2).quoted()) {
                        ParsedScreenBlock block = new ParsedScreenBlock();
                        block.isHud = isHud;
                        block.title = tokens.get(2).value();
                        block.contextName = currentTopContext;
                        block.headerLine = lineIdx;
                        block.headerIndent = leadingWhitespace(raw);
                        block.bodyIndent = block.headerIndent + (doc.usesSpacesIndent() ? "    " : "\t");
                        if (tokens.size() > 3) {
                            int targetPos = withoutColon.toLowerCase(Locale.ROOT).indexOf(
                                    " " + tokens.get(3).value().toLowerCase(Locale.ROOT) + " ",
                                     withoutColon.indexOf('"') + block.title.length() + 1);
                            if (targetPos > 0) {
                                block.targetClause = withoutColon.substring(targetPos + 1).strip();
                            } else {
                                StringBuilder tb = new StringBuilder();
                                for (int ti = 3; ti < tokens.size(); ti++) {
                                    if (!tb.isEmpty()) tb.append(' ');
                                    tb.append(tokens.get(ti).quoted() ? "\"" + tokens.get(ti).value() + "\"" : tokens.get(ti).value());
                                }
                                block.targetClause = tb.toString();
                            }
                        } else {
                            block.targetClause = isHud ? "for player" : "to player";
                        }

                        // Находим конец блока open screen по отступу
                        int bodyStart = lineIdx + 1;
                        int bodyEnd = bodyStart;
                        int lastElementEnd = bodyStart;
                        String firstElemIndent = null;
                        while (bodyEnd < lines.size()) {
                            String bRaw = lines.get(bodyEnd);
                            String bStrip = bRaw.strip();
                            if (bStrip.isEmpty() || bStrip.startsWith("#")) {
                                bodyEnd++;
                                continue;
                            }
                            int bInd = indentOf(bRaw);
                            if (bInd <= ind) {
                                break;
                            }
                            if (firstElemIndent == null) {
                                firstElemIndent = leadingWhitespace(bRaw);
                            }
                            bodyEnd++;
                            lastElementEnd = bodyEnd;
                        }
                        if (firstElemIndent != null) {
                            block.bodyIndent = firstElemIndent;
                        }
                        block.bodyStartLine = bodyStart;
                        block.bodyEndLine = lastElementEnd;
                        parseScreenBlockLines(lines, bodyStart, lastElementEnd, block);
                        screens.add(block);
                        lineIdx = lastElementEnd;
                        continue;
                    }
                } catch (Exception ignored) {
                }
            }
            lineIdx++;
        }

        if (screens.isEmpty()) {
            // Создаём стартовый экран-шаблон, если в скрипте ещё нет ни одного open screen
            ParsedScreenBlock def = createDefaultScreen(doc);
            screens.add(def);
        }
        activeScreenIdx = Math.max(0, Math.min(activeScreenIdx, screens.size() - 1));
    }

    private static ParsedScreenBlock createDefaultScreen(ScriptDocument doc) {
        ParsedScreenBlock b = new ParsedScreenBlock();
        b.title = "Меню";
        b.targetClause = "to player";
        b.contextName = tr("Новый экран", "New Screen");
        b.headerLine = -1;
        b.headerIndent = doc != null && doc.usesSpacesIndent() ? "    " : "\t";
        b.bodyIndent = b.headerIndent + b.headerIndent;

        GuiNode bg = new GuiNode();
        bg.kind = "box";
        bg.id = "bg_card";
        bg.x = 0;
        bg.y = 0;
        bg.w = 240;
        bg.h = 150;
        bg.anchor = Anchor.CENTER;
        bg.color = 0xE60D1322;
        bg.colorSet = true;
        bg.boxGrad = 0xE6162034;
        bg.boxBorder = 0xFF38BDF8;
        bg.boxBorderW = 1;
        bg.boxRadius = 6;
        bg.shadow = true;
        bg.boxBlur = true;
        bg.layer = 0;
        b.nodes.add(bg);

        GuiNode title = new GuiNode();
        title.kind = "text";
        title.id = "lbl_title";
        title.text = "&b&l✦ НОВОЕ МЕНЮ ✦";
        title.x = 0;
        title.y = -52;
        title.anchor = Anchor.CENTER;
        title.color = 0xFFFFFFFF;
        title.shadow = true;
        title.layer = 2;
        b.nodes.add(title);

        GuiNode btn = new GuiNode();
        btn.kind = "button";
        btn.id = "btn_close";
        btn.text = "Закрыть";
        btn.x = 0;
        btn.y = 48;
        btn.w = 96;
        btn.h = 20;
        btn.anchor = Anchor.CENTER;
        btn.runAction = "close";
        btn.layer = 2;
        b.nodes.add(btn);

        return b;
    }

    private static void parseScreenBlockLines(List<String> lines, int start, int end, ParsedScreenBlock block) {
        List<String> pendingComments = new ArrayList<>();
        int i = start;
        while (i < end) {
            String raw = lines.get(i);
            String stripped = raw.strip();
            if (stripped.isEmpty()) {
                i++;
                continue;
            }
            if (stripped.startsWith("#")) {
                pendingComments.add(stripped);
                i++;
                continue;
            }
            int elemIndent = indentOf(raw);
            String expanded = expandOptions(stripped);
            boolean isListBlock = expanded.toLowerCase(Locale.ROOT).startsWith("list ")
                    || expanded.toLowerCase(Locale.ROOT).startsWith("list:");
            List<String> nestedRows = new ArrayList<>();
            int nextI = i + 1;
            if (isListBlock && expanded.endsWith(":")) {
                while (nextI < end) {
                    String nRaw = lines.get(nextI);
                    String nStrip = nRaw.strip();
                    if (nStrip.isEmpty() || nStrip.startsWith("#")) {
                        nextI++;
                        continue;
                    }
                    if (indentOf(nRaw) <= elemIndent) break;
                    nestedRows.add(expandOptions(nStrip));
                    nextI++;
                }
            }
            GuiNode node = parseSingleElementLine(expanded, nestedRows);
            node.sourceLine = i;
            node.leadingComments.addAll(pendingComments);
            pendingComments.clear();
            // Автоблокировка гигантских прозрачных фонов-ловушек клика (как 2000x2000 в td.sc), чтобы они не мешали кликать на холсте
            if ("box".equals(node.kind) && node.w >= 900 && node.h >= 900 && ((node.color >>> 24) == 0)) {
                node.editorLocked = true;
            }
            block.nodes.add(node);
            i = nextI;
        }
    }

    private static GuiNode parseSingleElementLine(String lineText, List<String> nestedRows) {
        GuiNode n = new GuiNode();
        String clean = lineText.endsWith(":") ? lineText.substring(0, lineText.length() - 1).strip() : lineText;
        try {
            List<Token> t = Tokenizer.tokenize(clean);
            if (t.isEmpty()) {
                n.kind = "raw";
                n.rawUnparsedLine = lineText;
                return n;
            }
            String kind = word(t, 0);
            int idx = 1;
            switch (kind) {
                case "text" -> {
                    n.kind = "text";
                    n.text = t.size() > 1 ? t.get(1).value() : "Текст";
                    n.w = 0;
                    n.h = 10;
                    idx = 2;
                }
                case "button" -> {
                    n.kind = "button";
                    n.text = t.size() > 1 ? t.get(1).value() : "Кнопка";
                    n.w = 100;
                    n.h = 20;
                    n.runAction = "close";
                    idx = 2;
                }
                case "image" -> {
                    n.kind = "image";
                    n.text = t.size() > 1 ? t.get(1).value() : "minecraft:textures/item/diamond.png";
                    n.w = 16;
                    n.h = 16;
                    idx = 2;
                }
                case "box", "rect" -> {
                    n.kind = "box";
                    n.w = 100;
                    n.h = 60;
                    n.color = 0xCC111827;
                    idx = 1;
                }
                case "slot" -> {
                    n.kind = "slot";
                    n.w = 18;
                    n.h = 18;
                    String what = word(t, 1);
                    idx = 2;
                    if (what.equals("hotbar") && t.size() > 2 && !t.get(2).quoted() && t.get(2).value().matches("\\d+")) {
                        n.slotContent = SlotElement.Content.HOTBAR;
                        n.slotCount = parseIntSafe(t.get(2).value(), 1);
                        idx = 3;
                    } else if ((what.equals("image") || what.equals("text")) && t.size() > 2 && t.get(2).quoted()) {
                        n.slotContent = what.equals("image") ? SlotElement.Content.IMAGE : SlotElement.Content.TEXT;
                        n.text = t.get(2).value();
                        idx = 3;
                    } else {
                        n.slotContent = SlotElement.Content.ITEM;
                        n.text = t.size() > 1 ? t.get(1).value() : "diamond";
                        if (t.size() > 2 && !t.get(2).quoted() && t.get(2).value().matches("\\d+")) {
                            n.slotCount = parseIntSafe(t.get(2).value(), 1);
                            idx = 3;
                        }
                    }
                }
                case "bar", "progress" -> {
                    n.kind = "bar";
                    n.w = 80;
                    n.h = 8;
                    n.color = 0xFF4CAF50;
                    if (t.size() > 1 && t.get(1).quoted()) {
                        n.id = t.get(1).value();
                        idx = 2;
                    }
                }
                case "input" -> {
                    n.kind = "input";
                    n.w = 140;
                    n.h = 20;
                    if (t.size() > 1 && t.get(1).quoted()) {
                        n.id = t.get(1).value();
                        idx = 2;
                    }
                }
                case "slider" -> {
                    n.kind = "slider";
                    n.w = 150;
                    n.h = 20;
                    if (t.size() > 1 && t.get(1).quoted()) {
                        n.text = t.get(1).value();
                        idx = 2;
                    }
                }
                case "toggle", "checkbox" -> {
                    n.kind = "toggle";
                    n.w = 130;
                    n.h = 20;
                    if (t.size() > 1 && t.get(1).quoted()) {
                        n.text = t.get(1).value();
                        idx = 2;
                    }
                }
                case "list" -> {
                    n.kind = "list";
                    n.w = 120;
                    n.h = 140;
                    n.listRawRows.addAll(nestedRows);
                    for (String rLine : nestedRows) {
                        try {
                            List<Token> rt = Tokenizer.tokenize(rLine);
                            if (rt.size() >= 2 && word(rt, 0).equals("row")) {
                                n.listPreviewRows.add(new ListElement.Row(rt.get(1).value(), ""));
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    if (n.listPreviewRows.isEmpty()) {
                        n.listPreviewRows.add(new ListElement.Row("&7Строка 1", ""));
                        n.listPreviewRows.add(new ListElement.Row("&7Строка 2", ""));
                    }
                }
                default -> {
                    n.kind = "raw";
                    n.rawUnparsedLine = lineText;
                    return n;
                }
            }

            while (idx < t.size()) {
                String opt = word(t, idx);
                switch (opt) {
                    case "at" -> {
                        if (idx + 2 < t.size()) {
                            n.x = parseIntSafe(t.get(idx + 1).value(), 0);
                            n.y = parseIntSafe(t.get(idx + 2).value(), 0);
                        }
                        idx += 3;
                    }
                    case "size", "texture" -> {
                        if (idx + 1 < t.size()) {
                            Matcher m = SIZE_PAT.matcher(t.get(idx + 1).value());
                            if (m.matches()) {
                                int sw = Integer.parseInt(m.group(1));
                                int sh = Integer.parseInt(m.group(2));
                                if (opt.equals("size")) {
                                    n.w = sw;
                                    n.h = sh;
                                } else {
                                    n.texW = sw;
                                    n.texH = sh;
                                }
                            }
                        }
                        idx += 2;
                    }
                    case "anchor" -> {
                        if (idx + 1 < t.size()) {
                            Anchor a = Anchor.byName(t.get(idx + 1).value());
                            if (a != null) n.anchor = a;
                        }
                        idx += 2;
                    }
                    case "layer" -> {
                        if (idx + 1 < t.size()) n.layer = parseIntSafe(t.get(idx + 1).value(), 0);
                        idx += 2;
                    }
                    case "id" -> {
                        if (idx + 1 < t.size()) n.id = t.get(idx + 1).value();
                        idx += 2;
                    }
                    case "color" -> {
                        if (idx + 1 < t.size()) {
                            n.color = parseColorSafe(t.get(idx + 1).value(), n.color);
                            n.colorSet = true;
                        }
                        idx += 2;
                    }
                    case "gradient" -> {
                        if (idx + 1 < t.size()) n.boxGrad = parseColorSafe(t.get(idx + 1).value(), 0);
                        idx += 2;
                    }
                    case "border" -> {
                        if (idx + 1 < t.size()) n.boxBorder = parseColorSafe(t.get(idx + 1).value(), 0);
                        idx += 2;
                    }
                    case "border_width", "border-width" -> {
                        if (idx + 1 < t.size()) n.boxBorderW = Math.max(0, Math.min(16, parseIntSafe(t.get(idx + 1).value(), 1)));
                        idx += 2;
                    }
                    case "radius" -> {
                        if (idx + 1 < t.size()) n.boxRadius = Math.max(0, Math.min(32, parseIntSafe(t.get(idx + 1).value(), 0)));
                        idx += 2;
                    }
                    case "shadow" -> {
                        n.shadow = true;
                        idx++;
                    }
                    case "blur" -> {
                        n.boxBlur = true;
                        idx++;
                    }
                    case "horizontal" -> {
                        if ("list".equals(n.kind)) n.listHoriz = true;
                        else n.boxHoriz = true;
                        idx++;
                    }
                    case "vertical" -> {
                        if ("list".equals(n.kind)) n.listHoriz = false;
                        else n.boxHoriz = false;
                        idx++;
                    }
                    case "select" -> {
                        n.listSelect = true;
                        idx++;
                    }
                    case "header" -> {
                        if (idx + 1 < t.size()) n.listHeader = t.get(idx + 1).value();
                        idx += 2;
                    }
                    case "from" -> {
                        if ("list".equals(n.kind) && idx + 1 < t.size()) {
                            String raw = t.get(idx + 1).value();
                            if (raw.startsWith("{") && raw.endsWith("}")) raw = raw.substring(1, raw.length() - 1);
                            n.listFrom = raw;
                        } else if (idx + 1 < t.size()) {
                            n.sliderMin = parseDoubleSafe(t.get(idx + 1).value(), 0);
                        }
                        idx += 2;
                    }
                    case "min" -> {
                        if (idx + 1 < t.size()) n.sliderMin = parseDoubleSafe(t.get(idx + 1).value(), 0);
                        idx += 2;
                    }
                    case "to" -> {
                        if (idx + 1 < t.size()) {
                            if ("box".equals(n.kind)) n.boxGrad = parseColorSafe(t.get(idx + 1).value(), 0);
                            else n.sliderMax = parseDoubleSafe(t.get(idx + 1).value(), 100);
                        }
                        idx += 2;
                    }
                    case "step" -> {
                        if (idx + 1 < t.size()) n.sliderStep = parseDoubleSafe(t.get(idx + 1).value(), 1);
                        idx += 2;
                    }
                    case "value", "max" -> {
                        if (idx + 1 < t.size()) {
                            String v = t.get(idx + 1).value();
                            if ("input".equals(n.kind)) {
                                if (opt.equals("value")) n.inputValue = v;
                                else n.maxLength = parseIntSafe(v, 64);
                            } else if ("slider".equals(n.kind)) {
                                if (opt.equals("value")) n.sliderVal = parseDoubleSafe(v, 50);
                                else n.sliderMax = parseDoubleSafe(v, 100);
                            } else if ("toggle".equals(n.kind)) {
                                n.toggleChecked = "true".equalsIgnoreCase(v) || "on".equalsIgnoreCase(v) || "1".equals(v);
                            } else {
                                if (opt.equals("value")) n.barValue = v;
                                else n.barMax = v;
                            }
                        }
                        idx += 2;
                    }
                    case "checked" -> {
                        if (idx + 1 < t.size() && (word(t, idx + 1).equals("true") || word(t, idx + 1).equals("false"))) {
                            n.toggleChecked = word(t, idx + 1).equals("true");
                            idx += 2;
                        } else {
                            n.toggleChecked = true;
                            idx++;
                        }
                    }
                    case "placeholder", "hint" -> {
                        if (idx + 1 < t.size()) n.placeholder = t.get(idx + 1).value();
                        idx += 2;
                    }
                    case "length" -> {
                        if (idx + 1 < t.size()) n.maxLength = parseIntSafe(t.get(idx + 1).value(), 64);
                        idx += 2;
                    }
                    case "direction" -> {
                        if (idx + 1 < t.size()) {
                            BarDirection d = BarDirection.byName(t.get(idx + 1).value());
                            if (d != null) n.barDir = d;
                        }
                        idx += 2;
                    }
                    case "background" -> {
                        if (idx + 1 < t.size()) {
                            if (t.get(idx + 1).quoted()) n.barBgTex = t.get(idx + 1).value();
                            else if (word(t, idx + 1).equals("none")) {
                                n.barBgColor = 0;
                                n.barBgTex = "";
                            } else n.barBgColor = parseColorSafe(t.get(idx + 1).value(), 0x80000000);
                        }
                        idx += 2;
                    }
                    case "fill" -> {
                        if (idx + 1 < t.size()) {
                            if (t.get(idx + 1).quoted()) n.barFillTex = t.get(idx + 1).value();
                            else {
                                n.color = parseColorSafe(t.get(idx + 1).value(), n.color);
                                n.colorSet = true;
                            }
                        }
                        idx += 2;
                    }
                    case "text" -> {
                        if (idx + 1 < t.size()) n.text = t.get(idx + 1).value();
                        idx += 2;
                    }
                    case "no" -> {
                        String nw = word(t, idx + 1);
                        if (nw.equals("frame")) n.slotFrame = false;
                        else if (nw.equals("scrollbar") || nw.equals("slider")) n.listNoScrollbar = true;
                        idx += 2;
                    }
                    case "scrollbar", "slider" -> {
                        n.listNoScrollbar = false;
                        idx++;
                    }
                    case "scale" -> {
                        if (idx + 1 < t.size()) n.scale = parseDoubleSafe(t.get(idx + 1).value(), 1.0);
                        idx += 2;
                    }
                    case "hscale", "hover_scale", "hover-scale" -> {
                        if (idx + 1 < t.size()) n.hoverScale = parseDoubleSafe(t.get(idx + 1).value(), 1.0);
                        idx += 2;
                    }
                    case "rotate", "rotation" -> {
                        if (idx + 1 < t.size()) n.rotation = parseDoubleSafe(t.get(idx + 1).value(), 0.0);
                        idx += 2;
                    }
                    case "frames" -> {
                        if (idx + 1 < t.size()) n.frames = Math.max(1, parseIntSafe(t.get(idx + 1).value(), 1));
                        idx += 2;
                    }
                    case "fps" -> {
                        if (idx + 1 < t.size()) n.fps = Math.max(1, parseIntSafe(t.get(idx + 1).value(), 8));
                        idx += 2;
                    }
                    case "follow" -> {
                        n.followCursor = true;
                        idx += 2;
                    }
                    case "hover_circle", "hover-circle" -> {
                        if (idx + 1 < t.size()) n.hoverCircle = Math.max(0, parseIntSafe(t.get(idx + 1).value(), 0));
                        idx += 2;
                    }
                    case "hover_color", "hover-color" -> {
                        if (idx + 1 < t.size()) n.hoverCircleColor = parseColorSafe(t.get(idx + 1).value(), 0x38BDF8) & 0x00FFFFFF;
                        idx += 2;
                    }
                    case "tooltip", "hover" -> {
                        if (idx + 1 < t.size()) n.tooltip = t.get(idx + 1).value();
                        idx += 2;
                    }
                    case "key", "hotkey" -> {
                        if (idx + 1 < t.size()) n.hotkey = t.get(idx + 1).value();
                        idx += 2;
                    }
                    case "keep", "stay" -> {
                        n.keepOpen = true;
                        idx += 2;
                    }
                    case "close" -> {
                        n.runAction = "close";
                        n.isFunctionCall = false;
                        idx++;
                    }
                    case "hidden" -> {
                        n.scriptHidden = true;
                        idx++;
                    }
                    case "run", "function" -> {
                        boolean forceFn = opt.equals("function");
                        int cur = idx + 1;
                        if (!forceFn && cur < t.size() && word(t, cur).equals("function")) {
                            forceFn = true;
                            cur++;
                        }
                        if (cur < t.size()) {
                            if (t.get(cur).quoted()) {
                                n.runAction = t.get(cur).value();
                                n.isFunctionCall = forceFn;
                                idx = cur + 1;
                            } else {
                                String fnName = t.get(cur).value();
                                int p = cur + 1;
                                if (p < t.size() && word(t, p).equals("(")) {
                                    int depth = 0;
                                    int closeIdx = p;
                                    StringBuilder args = new StringBuilder();
                                    for (int k = p; k < t.size(); k++) {
                                        String v = t.get(k).value();
                                        if (!t.get(k).quoted() && v.equals("(")) {
                                            depth++;
                                            if (depth == 1) continue;
                                        } else if (!t.get(k).quoted() && v.equals(")")) {
                                            depth--;
                                            if (depth == 0) {
                                                closeIdx = k;
                                                break;
                                            }
                                        }
                                        if (!args.isEmpty()) args.append(", ");
                                        args.append(t.get(k).quoted() ? "\"" + v + "\"" : v);
                                    }
                                    n.runAction = fnName + "(" + args + ")";
                                    n.isFunctionCall = true;
                                    idx = closeIdx + 1;
                                } else {
                                    n.runAction = fnName + "()";
                                    n.isFunctionCall = true;
                                    idx = p;
                                }
                            }
                        } else {
                            idx++;
                        }
                    }
                    default -> idx++;
                }
            }
        } catch (Exception e) {
            n.kind = "raw";
            n.rawUnparsedLine = lineText;
        }
        return n;
    }

    // =========================================================================
    // 2. ГЕНЕРАЦИЯ КОДА OPEN SCREEN И ЗАПИСЬ ОБРАТНО В СКРИПТ
    // =========================================================================

    public static List<String> generateScreenCodeLines(ParsedScreenBlock block) {
        List<String> out = new ArrayList<>();
        String hInd = block.headerIndent == null ? "\t" : block.headerIndent;
        String bInd = block.bodyIndent == null ? hInd + "\t" : block.bodyIndent;
        String verb = block.isHud ? "create hud" : "open screen";
        String target = block.targetClause == null || block.targetClause.isBlank()
                ? "" : " " + block.targetClause.strip();
        out.add(hInd + verb + " \"" + escapeQuotes(block.title) + "\"" + target + ":");

        for (GuiNode n : block.nodes) {
            for (String comment : n.leadingComments) {
                out.add(bInd + comment);
            }
            if ("raw".equals(n.kind) && n.rawUnparsedLine != null) {
                out.add(bInd + n.rawUnparsedLine);
                continue;
            }
            out.add(bInd + formatNodeLine(n));
            if ("list".equals(n.kind)) {
                String rInd = bInd + (bInd.startsWith(" ") ? "    " : "\t");
                if (!n.listRawRows.isEmpty()) {
                    for (String r : n.listRawRows) {
                        out.add(rInd + r);
                    }
                } else if (n.listFrom.isEmpty()) {
                    out.add(rInd + "row \"&fСтрока 1\"");
                }
            }
        }
        return out;
    }

    private static String formatNodeLine(GuiNode n) {
        StringBuilder sb = new StringBuilder();
        switch (n.kind) {
            case "box" -> {
                sb.append("box at ").append(n.x).append(' ').append(n.y);
                sb.append(" size ").append(Math.max(1, n.w)).append('x').append(Math.max(1, n.h));
                sb.append(" anchor ").append(n.anchor.name().toLowerCase(Locale.ROOT));
                sb.append(" color ").append(formatHexColor(n.color, true));
                if (n.boxGrad != 0) {
                    sb.append(" gradient ").append(formatHexColor(n.boxGrad, true));
                    if (n.boxHoriz) sb.append(" horizontal");
                }
                if (n.boxBorder != 0) {
                    sb.append(" border ").append(formatHexColor(n.boxBorder, true));
                    if (n.boxBorderW != 1) sb.append(" border_width ").append(n.boxBorderW);
                }
                if (n.boxRadius > 0) sb.append(" radius ").append(n.boxRadius);
                if (n.shadow) sb.append(" shadow");
                if (n.boxBlur) sb.append(" blur");
            }
            case "text" -> {
                sb.append("text \"").append(escapeQuotes(n.text)).append("\"");
                sb.append(" at ").append(n.x).append(' ').append(n.y);
                sb.append(" anchor ").append(n.anchor.name().toLowerCase(Locale.ROOT));
                if (n.colorSet || (n.color & 0x00FFFFFF) != 0x00FFFFFF) {
                    sb.append(" color ").append(formatHexColor(n.color, false));
                }
                if (n.shadow) sb.append(" shadow");
            }
            case "button" -> {
                sb.append("button \"").append(escapeQuotes(n.text)).append("\"");
                sb.append(" at ").append(n.x).append(' ').append(n.y);
                sb.append(" size ").append(Math.max(1, n.w)).append('x').append(Math.max(1, n.h));
                sb.append(" anchor ").append(n.anchor.name().toLowerCase(Locale.ROOT));
            }
            case "image" -> {
                sb.append("image \"").append(escapeQuotes(n.text)).append("\"");
                sb.append(" at ").append(n.x).append(' ').append(n.y);
                sb.append(" size ").append(Math.max(1, n.w)).append('x').append(Math.max(1, n.h));
                if (n.texW > 0 && n.texH > 0) {
                    sb.append(" texture ").append(n.texW).append('x').append(n.texH);
                }
                sb.append(" anchor ").append(n.anchor.name().toLowerCase(Locale.ROOT));
                if (n.colorSet || n.color != 0xFFFFFFFF) {
                    sb.append(" color ").append(formatHexColor(n.color, (n.color >>> 24) < 255));
                }
                if (n.frames > 1) {
                    sb.append(" frames ").append(n.frames).append(" fps ").append(n.fps);
                }
            }
            case "bar" -> {
                sb.append("bar");
                if (!n.id.isEmpty()) sb.append(" \"").append(escapeQuotes(n.id)).append("\"");
                sb.append(" value ").append(n.barValue.isEmpty() ? "0" : n.barValue);
                if (!n.barMax.isEmpty()) sb.append(" max ").append(n.barMax);
                if (n.barDir != BarDirection.LEFT_TO_RIGHT) {
                    sb.append(" direction ").append(n.barDir.name().toLowerCase(Locale.ROOT));
                }
                sb.append(" at ").append(n.x).append(' ').append(n.y);
                sb.append(" size ").append(Math.max(1, n.w)).append('x').append(Math.max(1, n.h));
                sb.append(" anchor ").append(n.anchor.name().toLowerCase(Locale.ROOT));
                sb.append(" color ").append(formatHexColor(n.color, false));
                if (!n.barFillTex.isEmpty()) sb.append(" fill \"").append(escapeQuotes(n.barFillTex)).append("\"");
                if (!n.barBgTex.isEmpty()) sb.append(" background \"").append(escapeQuotes(n.barBgTex)).append("\"");
                else if (n.barBgColor == 0) sb.append(" background none");
                else if (n.barBgColor != 0x80000000) sb.append(" background ").append(formatHexColor(n.barBgColor, true));
                if (!n.text.isEmpty()) sb.append(" text \"").append(escapeQuotes(n.text)).append("\"");
                if (n.shadow) sb.append(" shadow");
            }
            case "slot" -> {
                sb.append("slot ");
                if (n.slotContent == SlotElement.Content.HOTBAR) {
                    sb.append("hotbar ").append(Math.max(1, Math.min(9, n.slotCount)));
                } else if (n.slotContent == SlotElement.Content.IMAGE) {
                    sb.append("image \"").append(escapeQuotes(n.text)).append("\"");
                } else if (n.slotContent == SlotElement.Content.TEXT) {
                    sb.append("text \"").append(escapeQuotes(n.text)).append("\"");
                } else {
                    sb.append(n.text.isEmpty() ? "diamond" : n.text);
                    if (n.slotCount > 1) sb.append(' ').append(n.slotCount);
                }
                sb.append(" at ").append(n.x).append(' ').append(n.y);
                if (n.w > 0 && n.h > 0 && (n.w != 18 || n.h != 18)) {
                    sb.append(" size ").append(n.w).append('x').append(n.h);
                }
                sb.append(" anchor ").append(n.anchor.name().toLowerCase(Locale.ROOT));
                if (!n.slotFrame) sb.append(" no frame");
            }
            case "input" -> {
                sb.append("input");
                if (!n.id.isEmpty()) sb.append(" \"").append(escapeQuotes(n.id)).append("\"");
                sb.append(" at ").append(n.x).append(' ').append(n.y);
                sb.append(" size ").append(Math.max(1, n.w)).append('x').append(Math.max(1, n.h));
                sb.append(" anchor ").append(n.anchor.name().toLowerCase(Locale.ROOT));
                if (!n.placeholder.isEmpty()) sb.append(" placeholder \"").append(escapeQuotes(n.placeholder)).append("\"");
                if (!n.inputValue.isEmpty()) sb.append(" value \"").append(escapeQuotes(n.inputValue)).append("\"");
                if (n.maxLength != 64) sb.append(" length ").append(n.maxLength);
            }
            case "slider" -> {
                sb.append("slider \"").append(escapeQuotes(n.text)).append("\"");
                sb.append(" from ").append(fmtNum(n.sliderMin)).append(" to ").append(fmtNum(n.sliderMax));
                sb.append(" value ").append(fmtNum(n.sliderVal)).append(" step ").append(fmtNum(n.sliderStep));
                sb.append(" at ").append(n.x).append(' ').append(n.y);
                sb.append(" size ").append(Math.max(1, n.w)).append('x').append(Math.max(1, n.h));
                sb.append(" anchor ").append(n.anchor.name().toLowerCase(Locale.ROOT));
            }
            case "toggle" -> {
                sb.append("toggle \"").append(escapeQuotes(n.text)).append("\"");
                if (n.toggleChecked) sb.append(" checked");
                sb.append(" at ").append(n.x).append(' ').append(n.y);
                sb.append(" size ").append(Math.max(1, n.w)).append('x').append(Math.max(1, n.h));
                sb.append(" anchor ").append(n.anchor.name().toLowerCase(Locale.ROOT));
            }
            case "list" -> {
                sb.append("list");
                if (!n.listFrom.isEmpty()) sb.append(" from {").append(n.listFrom).append("}");
                sb.append(n.listHoriz ? " horizontal" : " vertical");
                if (n.listSelect) sb.append(" select");
                if (n.listNoScrollbar) sb.append(" no scrollbar");
                if (!n.listHeader.isEmpty()) sb.append(" header \"").append(escapeQuotes(n.listHeader)).append("\"");
                sb.append(" at ").append(n.x).append(' ').append(n.y);
                sb.append(" size ").append(Math.max(1, n.w)).append('x').append(Math.max(1, n.h));
                sb.append(" anchor ").append(n.anchor.name().toLowerCase(Locale.ROOT));
            }
        }

        if (Math.abs(n.scale - 1.0) > 0.001) sb.append(" scale ").append(fmtNum(n.scale));
        if (Math.abs(n.hoverScale - 1.0) > 0.001) sb.append(" hover_scale ").append(fmtNum(n.hoverScale));
        if (Math.abs(n.rotation) > 0.01) sb.append(" rotate ").append(fmtNum(n.rotation));
        if (n.followCursor) sb.append(" follow cursor");
        if (n.hoverCircle > 0) {
            sb.append(" hover_circle ").append(n.hoverCircle);
            if ((n.hoverCircleColor & 0x00FFFFFF) != 0x38BDF8) {
                sb.append(" hover_color ").append(formatHexColor(0xFF000000 | n.hoverCircleColor, false));
            }
        }
        if (n.layer != 0 || "box".equals(n.kind) || "image".equals(n.kind) || "text".equals(n.kind)) {
            sb.append(" layer ").append(n.layer);
        }
        if (!n.id.isEmpty() && !"bar".equals(n.kind) && !"input".equals(n.kind)) {
            sb.append(" id ").append(n.id);
        }
        if (!n.runAction.isEmpty()) {
            if ("close".equalsIgnoreCase(n.runAction)) {
                sb.append(" close");
            } else if (n.isFunctionCall || (!n.runAction.startsWith("/") && n.runAction.contains("(") && n.runAction.endsWith(")"))) {
                sb.append(" run ").append(n.runAction);
            } else {
                sb.append(" run \"").append(escapeQuotes(n.runAction)).append("\"");
            }
        }
        if (n.keepOpen) sb.append(" keep open");
        if (!n.hotkey.isEmpty()) sb.append(" key ").append(n.hotkey);
        if (!n.tooltip.isEmpty()) sb.append(" tooltip \"").append(escapeQuotes(n.tooltip)).append("\"");
        if (n.scriptHidden) sb.append(" hidden");
        if ("list".equals(n.kind) && (!n.listRawRows.isEmpty() || n.listFrom.isEmpty())) {
            sb.append(':');
        }
        return sb.toString();
    }

    /** Применяет текущий экран обратно в открытый {@link ScriptDocument} (с поддержкой Ctrl+Z в документе). */
    public static void applyToDocument(ScriptDocument doc) {
        applyToDocumentInternal(doc, true);
    }

    private static void applyToDocumentInternal(ScriptDocument doc, boolean notifyToast) {
        ParsedScreenBlock block = activeScreen();
        if (doc == null || !doc.loaded || doc.isImage || block == null) return;
        List<String> generated = generateScreenCodeLines(block);
        String joined = String.join("\n", generated);

        if (block.headerLine >= 0 && block.headerLine < doc.lines.size()
                && block.bodyEndLine >= block.headerLine && block.bodyEndLine <= doc.lines.size()) {
            // Заменяем диапазон строк существующего open screen
            doc.box = false;
            doc.anchorLine = block.headerLine;
            doc.anchorCol = 0;
            doc.line = Math.max(block.headerLine, block.bodyEndLine - 1);
            doc.col = doc.lines.get(doc.line).length();
            doc.insert(joined);
            int newEnd = block.headerLine + generated.size();
            int delta = newEnd - block.bodyEndLine;
            block.bodyStartLine = block.headerLine + 1;
            block.bodyEndLine = newEnd;
            for (int i = activeScreenIdx + 1; i < screens.size(); i++) {
                ParsedScreenBlock other = screens.get(i);
                if (other.headerLine >= 0) {
                    other.headerLine += delta;
                    other.bodyStartLine += delta;
                    other.bodyEndLine += delta;
                    for (GuiNode gn : other.nodes) {
                        if (gn.sourceLine >= 0) gn.sourceLine += delta;
                    }
                }
            }
        } else {
            // Вставляем новый блок open screen в конец файла или в команду
            String prefix = doc.lines.isEmpty() || doc.lines.get(doc.lines.size() - 1).isBlank() ? "" : "\n";
            boolean atTop = doc.line >= 0 && doc.line < doc.lines.size() && indentOf(doc.lines.get(doc.line)) == 0
                    && doc.lines.get(doc.line).strip().isEmpty();
            doc.box = false;
            doc.anchorLine = -1;
            doc.line = doc.lines.size() - 1;
            doc.col = doc.lines.get(doc.line).length();
            if (atTop || doc.text().isBlank()) {
                String cmdWrap = prefix + "command /gui_menu:\n" + joined + "\n";
                int startLine = doc.lines.size() + (prefix.isEmpty() ? 0 : 1);
                doc.insert(cmdWrap);
                block.headerLine = startLine;
                block.bodyStartLine = startLine + 1;
                block.bodyEndLine = startLine + generated.size();
                block.contextName = "command /gui_menu";
            } else {
                int insertAt = doc.line + (prefix.isEmpty() ? 0 : 1);
                doc.insert(prefix + joined);
                block.headerLine = insertAt;
                block.bodyStartLine = insertAt + 1;
                block.bodyEndLine = insertAt + generated.size();
            }
        }
        // Обновляем sourceLine каждого элемента, чтобы совместное редактирование показывало точный элемент
        int curLine = block.headerLine + 1;
        for (GuiNode n : block.nodes) {
            curLine += n.leadingComments.size();
            n.sourceLine = curLine;
            curLine++;
            if ("list".equals(n.kind)) {
                curLine += !n.listRawRows.isEmpty() ? n.listRawRows.size() : (n.listFrom.isEmpty() ? 1 : 0);
            }
        }
        // Ставим курсор документа на выбранный элемент, чтобы пиры видели, что мы редактируем
        GuiNode sel = selectedNode();
        if (sel != null && sel.sourceLine >= 0 && sel.sourceLine < doc.lines.size()) {
            doc.line = sel.sourceLine;
            doc.col = 0;
            doc.anchorLine = -1;
        }
        parsedDocSnapshot = doc.text();
        dirtyOnCanvas = false;
        if (notifyToast) {
            ClientToasts.push(tr("&aЭкран обновлён в скрипте (" + generated.size() + " стр.)",
                    "&aScreen applied to script (" + generated.size() + " lines)"));
        }
    }

    // =========================================================================
    // 3. ПРЕОБРАЗОВАНИЕ GuiNode -> ScreenElement ДЛЯ 1-В-1 РЕНДЕРА НА ХОЛСТЕ
    // =========================================================================

    private static ScreenElement toPreviewElement(GuiNode n) {
        Layout layout = new Layout(n.x, n.y, n.w, n.h, n.anchor, n.color, n.layer);
        StringBuilder extra = new StringBuilder();
        if (Math.abs(n.scale - 1.0) > 0.001) {
            extra.append("sc").append(String.format(Locale.ROOT, "%.3f", n.scale));
        }
        if (Math.abs(n.hoverScale - 1.0) > 0.001) {
            if (!extra.isEmpty()) extra.append(';');
            extra.append("hs").append(String.format(Locale.ROOT, "%.3f", n.hoverScale));
        }
        if (Math.abs(n.rotation) > 0.01) {
            if (!extra.isEmpty()) extra.append(';');
            extra.append("rt").append(String.format(Locale.ROOT, "%.2f", n.rotation));
        }
        if (n.frames > 1) {
            if (!extra.isEmpty()) extra.append(';');
            extra.append("fr").append(n.frames).append('x').append(n.fps);
        }
        if (n.hoverCircle > 0) {
            if (!extra.isEmpty()) extra.append(';');
            extra.append("hc").append(n.hoverCircle).append('x').append(Integer.toHexString(n.hoverCircleColor & 0x00FFFFFF));
        }
        String packed = ScreenMeta.pack(n.id, n.keepOpen, n.hotkey, false, colorize(n.tooltip), extra.toString(), n.runAction);
        return switch (n.kind) {
            case "box" -> {
                String spec = "box:" + (n.boxGrad == 0 ? "0" : Integer.toHexString(n.boxGrad))
                        + ":" + (n.boxBorder == 0 ? "0" : Integer.toHexString(n.boxBorder))
                        + ":" + (n.boxHoriz ? "h" : "v") + ":" + n.boxBorderW
                        + ":" + n.boxRadius + ":" + (n.shadow ? "1" : "0") + ":" + (n.boxBlur ? "1" : "0");
                yield new ImageElement(layout, spec, 0, 0, packed);
            }
            case "text" -> new TextElement(layout, colorize(n.text), n.shadow, packed);
            case "button" -> new ButtonElement(layout, colorize(n.text), packed);
            case "image" -> new ImageElement(layout, n.text, n.texW, n.texH, packed);
            case "bar" -> new BarElement(layout, ScreenMeta.packBarId(n.id, false, colorize(n.tooltip), extra.toString()),
                    previewBarNum(n.barValue, "75"), previewBarNum(n.barMax, "100"), n.barDir,
                    n.barBgColor, n.barFillTex, n.barBgTex, n.texW, n.texH, colorize(n.text), n.shadow);
            case "slot" -> new SlotElement(layout, n.slotContent,
                    n.slotContent == SlotElement.Content.TEXT ? colorize(n.text) : n.text,
                    n.slotCount, n.slotFrame, packed);
            case "input" -> new InputElement(layout, colorize(n.inputValue), colorize(n.placeholder), n.maxLength, packed);
            case "slider" -> new SliderElement(layout, colorize(n.text), n.sliderMin, n.sliderMax, n.sliderVal, n.sliderStep, packed);
            case "toggle" -> new ToggleElement(layout, colorize(n.text), n.toggleChecked, packed);
            case "list" -> {
                String pHdr = ScreenMeta.pack(n.id, false, n.listNoScrollbar ? "noslider" : "", false, colorize(n.tooltip), colorize(n.listHeader));
                yield new ListElement(layout, pHdr, n.listHoriz, n.listSelect, n.listPreviewRows);
            }
            default -> new TextElement(layout, colorize(n.rawUnparsedLine == null ? "?" : n.rawUnparsedLine), true, packed);
        };
    }

    private static String previewBarNum(String raw, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        if (raw.startsWith("{") || raw.startsWith("%")) return fallback;
        return raw;
    }

    /** Вычисляет реальный прямоугольник элемента на виртуальном экране virtualW × virtualH. */
    private static int[] nodeBounds(Font font, GuiNode n, int sw, int sh) {
        int ew = n.w;
        int eh = n.h;
        if ("text".equals(n.kind)) {
            String s = colorize(n.text).replace("%nl%", "\n").replace("%newline%", "\n");
            String[] lines = s.split("\n", -1);
            int maxW = 8;
            for (String ln : lines) maxW = Math.max(maxW, font.width(ln));
            ew = maxW;
            eh = lines.length * (font.lineHeight + 2) - 2;
        } else {
            if (ew <= 0) ew = "slot".equals(n.kind) ? 18 : ("bar".equals(n.kind) ? 80 : 100);
            if (eh <= 0) eh = "slot".equals(n.kind) ? 18 : ("bar".equals(n.kind) ? 8 : 20);
        }
        int rx = n.anchor.resolveX(sw, ew, n.x);
        int ry = n.anchor.resolveY(sh, eh, n.y);
        return new int[]{rx, ry, Math.max(4, ew), Math.max(4, eh)};
    }

    // =========================================================================
    // 4. ОТРИСОВКА ХОЛСТА И ПЕРЕМЕЩАЕМЫХ ПАНЕЛЕЙ
    // =========================================================================

    public static void render(GuiGraphics g, Font font, ScriptDocument doc,
                              int x, int y, int w, int h,
                              int mcScreenW, int mcScreenH, int mx, int my) {
        ensureSynced(doc);
        viewX = x;
        viewY = y;
        viewW = w;
        viewH = h;
        virtualW = Math.max(320, mcScreenW);
        virtualH = Math.max(200, mcScreenH);
        hoverTip = null;
        topHits.clear();
        dropdownHits.clear();
        paletteHits.clear();
        layerBtnHits.clear();
        layerRowHits.clear();
        inspectorHits.clear();

        int canvasAreaY = y + TOP_BAR_H;
        int canvasAreaH = Math.max(40, h - TOP_BAR_H);

        // Инициализация масштаба холста и позиций плавающих доков
        float fitScale = Math.min((w - 24f) / virtualW, (canvasAreaH - 20f) / virtualH);
        fitScale = Math.max(0.35f, Math.min(1.0f, fitScale));
        if (!zoomInitialized) {
            canvasZoom = 1.0f;
            panX = 0f;
            panY = 0f;
            zoomInitialized = true;
        }
        effScale = fitScale * canvasZoom;
        stageW = Math.round(virtualW * effScale);
        stageH = Math.round(virtualH * effScale);
        stageX = x + Math.round((w - stageW) / 2f + panX);
        stageY = canvasAreaY + Math.round((canvasAreaH - stageH) / 2f + panY);

        layoutDocks(x, canvasAreaY, w, canvasAreaH);

        // 1. Фон холста
        g.fill(x, canvasAreaY, x + w, y + h, BG_DARK);
        g.enableScissor(x + 1, canvasAreaY, x + w - 1, y + h - 1);

        // Тень и подложка виртуального экрана Minecraft
        g.fill(stageX + 4, stageY + 4, stageX + stageW + 4, stageY + stageH + 4, 0x77000000);
        g.fillGradient(stageX, stageY, stageX + stageW, stageY + stageH, 0xFF101520, 0xFF0C1018);

        // Сетка на виртуальном экране
        if (gridSize > 0) {
            int stepPx = Math.max(4, Math.round(gridSize * 2 * effScale));
            int gridCol = 0x16FFFFFF;
            for (int gx = stageX + stepPx; gx < stageX + stageW; gx += stepPx) {
                g.fill(gx, stageY, gx + 1, stageY + stageH, gridCol);
            }
            for (int gy = stageY + stepPx; gy < stageY + stageH; gy += stepPx) {
                g.fill(stageX, gy, stageX + stageW, gy + 1, gridCol);
            }
        }
        // Оси центра экрана (Anchor guides)
        if (showAnchorGuides) {
            int cx = stageX + stageW / 2;
            int cy = stageY + stageH / 2;
            g.fill(cx, stageY, cx + 1, stageY + stageH, 0x3338BDF8);
            g.fill(stageX, cy, stageX + stageW, cy + 1, 0x3338BDF8);
        }
        border(g, stageX - 1, stageY - 1, stageW + 2, stageH + 2, 0xFF334155);

        // 2. Рендер всех элементов текущего экрана 1-в-1 в виртуальных координатах virtualW × virtualH
        ParsedScreenBlock scr = activeScreen();
        int vmx = Math.round((mx - stageX) / effScale);
        int vmy = Math.round((my - stageY) / effScale);

        if (scr != null) {
            g.pose().pushPose();
            g.pose().translate(stageX, stageY, 0f);
            g.pose().scale(effScale, effScale, 1f);

            // Заголовок экрана сверху по центру, как в ScriptScreen
            g.drawCenteredString(font, colorize(scr.title), virtualW / 2, 8, 0xFFFFFF);

            List<Integer> drawOrder = getSortedDrawOrder(scr);
            float z = 0f;
            for (int idx : drawOrder) {
                GuiNode node = scr.nodes.get(idx);
                if (!node.editorVisible) continue;
                if (node.scriptHidden && !showScriptHidden && idx != selectedNodeIdx) continue;
                if (layerFilter != null && node.layer != layerFilter && idx != selectedNodeIdx) continue;

                ScreenElement el = toPreviewElement(node);
                g.pose().pushPose();
                g.pose().translate(0f, 0f, z);
                renderPreviewElement(g, font, node, el, virtualW, virtualH, vmx, vmy);
                g.pose().popPose();
                if (ElementRenderer.drawsItem(el)) {
                    z = Math.min(z + ElementRenderer.ITEM_Z_STEP, ElementRenderer.MAX_Z);
                }
            }

            // Рамка выделенного элемента + 8 маркеров ресайза + линия к точке привязки Anchor
            GuiNode sel = selectedNode();
            if (sel != null && sel.editorVisible) {
                g.pose().pushPose();
                g.pose().translate(0f, 0f, z + 20f);
                int[] b = nodeBounds(font, sel, virtualW, virtualH);
                int bx = b[0], by = b[1], bw = b[2], bh = b[3];

                // Если у элемента есть hover_circle — показываем его пунктирный радиус
                if (sel.hoverCircle > 0) {
                    drawPreviewCircle(g, bx + bw / 2, by + bh / 2, sel.hoverCircle, sel.hoverCircleColor);
                }

                // Линия от точки Anchor к элементу
                int ancX = sel.anchor.resolveX(virtualW, 0, 0);
                int ancY = sel.anchor.resolveY(virtualH, 0, 0);
                g.fill(ancX - 2, ancY - 2, ancX + 3, ancY + 3, GOLD);

                // Контур выделения
                int selCol = sel.editorLocked ? RED : GOLD;
                border(g, bx - 1, by - 1, bw + 2, bh + 2, selCol);

                // 8 маркеров изменения размера (если слой не заблокирован)
                if (!sel.editorLocked && !"text".equals(sel.kind)) {
                    drawHandle(g, bx - 2, by - 2);
                    drawHandle(g, bx + bw / 2 - 2, by - 2);
                    drawHandle(g, bx + bw - 2, by - 2);
                    drawHandle(g, bx + bw - 2, by + bh / 2 - 2);
                    drawHandle(g, bx + bw - 2, by + bh - 2);
                    drawHandle(g, bx + bw / 2 - 2, by + bh - 2);
                    drawHandle(g, bx - 2, by + bh - 2);
                    drawHandle(g, bx - 2, by + bh / 2 - 2);
                }

                // Бейдж координат и размера над выделенным элементом
                String badge = (sel.id.isEmpty() ? sel.kind : "#" + sel.id)
                        + " (" + sel.x + ", " + sel.y + ") " + bw + "×" + bh + " L:" + sel.layer;
                int badgeW = font.width(badge) + 8;
                int badgeX = Math.max(2, Math.min(bx, virtualW - badgeW - 2));
                int badgeY = by - 14 >= 2 ? by - 14 : by + bh + 3;
                g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 12, 0xE6101724);
                border(g, badgeX, badgeY, badgeW, 12, selCol);
                g.drawString(font, badge, badgeX + 4, badgeY + 2, selCol, false);
                g.pose().popPose();
            }

            // Совместное редактирование: рамки и бейджи других редакторов на их выбранных элементах
            if (doc != null && doc.live) {
                java.util.UUID selfId = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
                List<StudioLivePacket.Peer> peers = StudioSession.peers(doc.name);
                if (!peers.isEmpty()) {
                    g.pose().pushPose();
                    g.pose().translate(0f, 0f, z + 25f);
                    for (StudioLivePacket.Peer peer : peers) {
                        if (selfId != null && selfId.equals(peer.id())) continue;
                        GuiNode peerNode = findPeerNode(scr, peer.line());
                        if (peerNode == null || !peerNode.editorVisible) continue;
                        int pc = StudioScreen.peerColor(peer);
                        int[] pb = nodeBounds(font, peerNode, virtualW, virtualH);
                        int pbx = pb[0], pby = pb[1], pbw = pb[2], pbh = pb[3];
                        // Двойная цветная рамка вокруг элемента, который редактирует пир
                        border(g, pbx - 2, pby - 2, pbw + 4, pbh + 4, pc);
                        border(g, pbx - 1, pby - 1, pbw + 2, pbh + 2, pc);
                        String pTag = peer.name();
                        int pTagW = font.width(pTag) + 16;
                        int pTagX = Math.max(2, Math.min(pbx, virtualW - pTagW - 2));
                        int pTagY = pby - 15 >= 2 ? pby - 15 : pby + pbh + 4;
                        g.fill(pTagX, pTagY, pTagX + pTagW, pTagY + 12, 0xF0101724);
                        border(g, pTagX, pTagY, pTagW, 12, pc);
                        blitPeerHead(g, peer.id(), pTagX + 2, pTagY + 2, 8);
                        g.drawString(font, pTag, pTagX + 12, pTagY + 2, pc, false);
                    }
                    g.pose().popPose();
                }
            }
            g.pose().popPose();
        }

        // Инфо-бейдж разрешения холста внизу
        String resInfo = virtualW + "×" + virtualH + " px  ·  " + Math.round(canvasZoom * 100) + "%  ·  "
                + tr("Колесо: зум | ПКМ/СКМ: панорама | Tab: скрыть панели", "Wheel: zoom | RMB/MMB: pan | Tab: toggle panels");
        g.drawString(font, resInfo, x + 8, y + h - 12, 0x998A96AA, false);

        // 3. Плавающие панели инструментов (Палитра, Слои Photoshop, Инспектор)
        if (!allPanelsHiddenTab) {
            if (paletteDock.open) renderPaletteDock(g, font, mx, my);
            if (layersDock.open) renderLayersDock(g, font, doc, mx, my);
            if (inspectorDock.open) renderInspectorDock(g, font, mx, my);
        }

        g.disableScissor();

        // 4. Верхняя панель управления GUI-редактором + выпадающий список экранов
        renderTopBar(g, font, doc, x, y, w, mx, my);
        if (screenDropdownOpen && scr != null) {
            renderScreenDropdown(g, font, doc, x + 6, y + TOP_BAR_H + 1, mx, my);
        }
        if (expandedField != null) {
            renderExpandedEditor(g, font, mx, my);
        }

        // Тултип поверх всего
        if (hoverTip != null && !hoverTip.isEmpty()) {
            int tw = font.width(hoverTip) + 10;
            int tx = Math.max(x + 4, Math.min(hoverTipX, x + w - tw - 4));
            int ty = Math.max(y + 18, Math.min(hoverTipY, y + h - 4));
            g.fill(tx, ty - 14, tx + tw, ty, 0xF8101622);
            border(g, tx, ty - 14, tw, 14, EDGE_HOT);
            g.drawString(font, hoverTip, tx + 5, ty - 11, TEXT, false);
        }
    }

    private static void renderPreviewElement(GuiGraphics g, Font font, GuiNode n, ScreenElement el,
                                             int sw, int sh, int vmx, int vmy) {
        if (el instanceof ButtonElement btn) {
            int[] b = nodeBounds(font, n, sw, sh);
            int bx = b[0], by = b[1], bw = b[2], bh = b[3];
            boolean hot = vmx >= bx && vmy >= by && vmx < bx + bw && vmy < by + bh;
            g.fill(bx, by, bx + bw, by + bh, 0xFF000000);
            g.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, hot ? 0xFF5A6578 : 0xFF49505C);
            g.fill(bx + 1, by + 1, bx + bw - 2, by + 2, hot ? 0xFF8B9BB4 : 0xFF6E7785);
            g.fill(bx + 1, by + bh - 2, bx + bw - 1, by + bh - 1, 0xFF2B3038);
            g.drawCenteredString(font, btn.label(), bx + bw / 2, by + (bh - 8) / 2, 0xFFFFFF);
        } else if (el instanceof InputElement in) {
            int[] b = nodeBounds(font, n, sw, sh);
            int bx = b[0], by = b[1], bw = b[2], bh = b[3];
            g.fill(bx, by, bx + bw, by + bh, 0xFF000000);
            border(g, bx, by, bw, bh, 0xFFA0A0A0);
            String shown = !in.value().isEmpty() ? in.value() : in.placeholder();
            int col = !in.value().isEmpty() ? 0xFFE0E0E0 : 0xFF707070;
            g.drawString(font, trimWidth(font, shown, bw - 8), bx + 4, by + (bh - 8) / 2, col, false);
        } else if (el instanceof SliderElement sl) {
            int[] b = nodeBounds(font, n, sw, sh);
            int bx = b[0], by = b[1], bw = b[2], bh = b[3];
            double range = sl.max() - sl.min();
            double norm = range > 0 ? Math.max(0.0, Math.min(1.0, (sl.value() - sl.min()) / range)) : 0.5;
            int accent = (n.color == 0xFFFFFFFF || n.color == 0) ? 0xFF3B82F6 : n.color;
            g.fill(bx, by, bx + bw, by + bh, 0xCC1E1E24);
            border(g, bx, by, bw, bh, 0xFF45475A);
            int fillW = (int) Math.round((bw - 2) * norm);
            if (fillW > 0) g.fill(bx + 1, by + 1, bx + 1 + fillW, by + bh - 1, (accent & 0x00FFFFFF) | 0x99000000);
            int knobX = bx + 1 + (int) Math.round((bw - 8) * norm);
            g.fill(knobX, by + 1, knobX + 6, by + bh - 1, 0xFFE2E8F0);
            String txt = sl.label().isEmpty() ? fmtNum(sl.value()) : (sl.label() + ": " + fmtNum(sl.value()));
            g.drawCenteredString(font, txt, bx + bw / 2, by + (bh - 8) / 2, 0xFFFFFFFF);
        } else if (el instanceof ToggleElement tg) {
            int[] b = nodeBounds(font, n, sw, sh);
            int bx = b[0], by = b[1], bw = b[2], bh = b[3];
            boolean on = tg.value();
            int onColor = (n.color == 0xFFFFFFFF || n.color == 0) ? 0xFF22C55E : n.color;
            g.fill(bx, by, bx + bw, by + bh, 0xB0181920);
            border(g, bx, by, bw, bh, 0xFF45475A);
            int swW = 24, swH = Math.min(12, bh - 4);
            int swX = bx + 5, swY = by + (bh - swH) / 2;
            g.fill(swX, swY, swX + swW, swY + swH, on ? onColor : 0xFF52525B);
            int knob = swH - 2;
            int kx = on ? (swX + swW - knob - 1) : (swX + 1);
            g.fill(kx, swY + 1, kx + knob, swY + 1 + knob, 0xFFFFFFFF);
            String lbl = tg.label().isEmpty() ? (on ? "ON" : "OFF") : tg.label();
            g.drawString(font, lbl, swX + swW + 6, by + (bh - 8) / 2, on ? 0xFFFFFFFF : 0xFFB4B4C2, true);
        } else {
            ElementRenderer.render(g, font, el, sw, sh, vmx, vmy, 0);
            // Если это картинка и текстура ещё не загружена, рисуем аккуратный плейсхолдер, чтобы элемент было видно на холсте
            if ("image".equals(n.kind)) {
                int[] b = nodeBounds(font, n, sw, sh);
                border(g, b[0], b[1], b[2], b[3], 0x3358A6FF);
            }
        }
    }

    private static void drawPreviewCircle(GuiGraphics g, int cx, int cy, int r, int rgb) {
        if (r <= 0) return;
        int rimCol = 0xAA000000 | (rgb & 0x00FFFFFF);
        int segs = Math.max(36, Math.min(72, r));
        for (int i = 0; i < segs; i += 2) {
            double a = (2.0 * Math.PI * i) / segs;
            int px = cx + (int) Math.round(Math.cos(a) * r);
            int py = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(px - 1, py - 1, px + 1, py + 1, rimCol);
        }
    }

    private static void drawHandle(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 4, y + 4, 0xFF0F172A);
        g.fill(x + 1, y + 1, x + 3, y + 3, GOLD);
    }

    // =========================================================================
    // 5. ВЕРХНЯЯ ПАНЕЛЬ И ВЫПАДАЮЩИЙ СПИСОК ЭКРАНОВ СКРИПТА
    // =========================================================================

    private static void renderTopBar(GuiGraphics g, Font font, ScriptDocument doc,
                                     int x, int y, int w, int mx, int my) {
        g.fill(x, y, x + w, y + TOP_BAR_H, PANEL_HDR);
        g.fill(x, y + TOP_BAR_H - 1, x + w, y + TOP_BAR_H, EDGE);

        int cx = x + 6;
        int by = y + 3;
        ParsedScreenBlock scr = activeScreen();

        // Переключатель экранов: ◀ [Экран 1/N: "Title" ▾] ▶ [+ Экран]
        cx = addTopBtn(g, font, mx, my, cx, by, 15, 16, "◀", "scr:prev",
                tr("Предыдущий экран в скрипте", "Previous screen in script"), false) + 2;
        String scrTitle = scr == null ? tr("Нет экранов", "No screens")
                : trimWidth(font, scr.displayName(activeScreenIdx, screens.size()) + " ▾", Math.min(210, Math.max(100, w / 4)));
        int dropW = font.width(scrTitle) + 12;
        cx = addTopBtn(g, font, mx, my, cx, by, dropW, 16, scrTitle, "scr:drop",
                tr("Выбрать блок open screen / create hud в этом скрипте", "Select open screen / create hud block in script"),
                screenDropdownOpen) + 2;
        cx = addTopBtn(g, font, mx, my, cx, by, 15, 16, "▶", "scr:next",
                tr("Следующий экран в скрипте", "Next screen in script"), false) + 3;
        cx = addTopBtn(g, font, mx, my, cx, by, font.width("+") + 10, 16, "+", "scr:new",
                tr("Создать новый экран open screen", "Create new open screen block"), false) + 6;

        g.fill(cx, y + 4, cx + 1, y + TOP_BAR_H - 4, EDGE);
        cx += 6;

        // Сетка, привязка, скрытые элементы, масштаб
        String gridLbl = (gridSize == 0 ? tr("Сетка: ВЫКЛ", "Grid: OFF") : tr("Сетка: ", "Grid: ") + gridSize + "px");
        cx = addTopBtn(g, font, mx, my, cx, by, font.width(gridLbl) + 10, 16, gridLbl, "opt:grid",
                tr("Шаг сетки (Выкл / 2 / 4 / 8 / 16 px)", "Cycle grid size"), gridSize > 0) + 3;

        String snapLbl = tr("Привязка", "Snap");
        cx = addTopBtn(g, font, mx, my, cx, by, font.width(snapLbl) + 10, 16, snapLbl, "opt:snap",
                tr("Привязка координат и размеров к сетке", "Snap position & size to grid"), snapToGrid) + 3;

        String hidLbl = tr("Hidden", "Hidden");
        cx = addTopBtn(g, font, mx, my, cx, by, font.width(hidLbl) + 10, 16, hidLbl, "opt:hidden",
                tr("Показывать элементы со свойством hidden из скрипта", "Show script elements marked hidden"), showScriptHidden) + 3;

        cx = addTopBtn(g, font, mx, my, cx, by, 26, 16, "1:1", "opt:reset_zoom",
                tr("Сбросить масштаб и центрировать холст", "Reset zoom & center canvas"), false) + 6;

        g.fill(cx, y + 4, cx + 1, y + TOP_BAR_H - 4, EDGE);
        cx += 6;

        // Кнопки показа/скрытия плавающих панелей
        cx = addTopBtn(g, font, mx, my, cx, by, font.width(tr("Палитра", "Palette")) + 10, 16,
                tr("Палитра", "Palette"), "dock:palette",
                tr("Показать / скрыть палитру элементов", "Toggle Element Palette panel"),
                paletteDock.open && !allPanelsHiddenTab) + 3;
        cx = addTopBtn(g, font, mx, my, cx, by, font.width(tr("Слои", "Layers")) + 10, 16,
                tr("Слои", "Layers"), "dock:layers",
                tr("Показать / скрыть панель слоёв (Photoshop)", "Toggle Photoshop Layers panel"),
                layersDock.open && !allPanelsHiddenTab) + 3;
        cx = addTopBtn(g, font, mx, my, cx, by, font.width(tr("Инспектор", "Inspector")) + 10, 16,
                tr("Инспектор", "Inspector"), "dock:inspector",
                tr("Показать / скрыть инспектор свойств", "Toggle Property Inspector panel"),
                inspectorDock.open && !allPanelsHiddenTab) + 4;

        // Кнопки справа: [</> К коду] [⎘ Копия] [✔ В скрипт]
        int rx = x + w - 6;
        String applyLbl = tr("✔ В скрипт", "✔ Apply") + (dirtyOnCanvas ? "*" : "");
        int applyW = font.width(applyLbl) + 12;
        rx -= applyW;
        drawActionBtn(g, font, mx, my, rx, by, applyW, 16, applyLbl, "act:apply",
                tr("Записать изменения этого экрана обратно в скрипт", "Write this screen back into the script"), GREEN);

        String copyLbl = tr("⎘ Код", "⎘ Copy");
        int copyW = font.width(copyLbl) + 10;
        rx -= copyW + 3;
        if (rx > cx + 20) {
            addTopBtn(g, font, mx, my, rx, by, copyW, 16, copyLbl, "act:copy",
                    tr("Скопировать сгенерированный блок open screen в буфер обмена", "Copy generated open screen block"), false);
        }

        String jumpLbl = "</>";
        int jumpW = font.width(jumpLbl) + 10;
        rx -= jumpW + 3;
        if (rx > cx + 4) {
            addTopBtn(g, font, mx, my, rx, by, jumpW, 16, jumpLbl, "act:jump",
                    tr("Перейти к строке этого open screen в текстовом редакторе", "Jump to this open screen in code editor"), false);
        }
    }

    private static void renderScreenDropdown(GuiGraphics g, Font font, ScriptDocument doc, int x, int y, int mx, int my) {
        int w = 310;
        int rowH = 16;
        int h = screens.size() * rowH + 6;
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x77000000);
        g.fill(x, y, x + w, y + h, 0xFA121926);
        border(g, x, y, w, h, ACCENT);
        List<StudioLivePacket.Peer> peers = doc != null && doc.live ? StudioSession.peers(doc.name) : List.of();
        java.util.UUID selfId = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
        for (int i = 0; i < screens.size(); i++) {
            ParsedScreenBlock b = screens.get(i);
            int ry = y + 3 + i * rowH;
            boolean sel = i == activeScreenIdx;
            boolean hot = hover(mx, my, x + 2, ry, w - 4, rowH);
            if (sel || hot) {
                g.fill(x + 2, ry, x + w - 2, ry + rowH, sel ? ROW_SEL : ROW_HOT);
                if (sel) g.fill(x + 2, ry + 1, x + 4, ry + rowH - 1, GOLD);
            }
            int rightLim = x + w - 6;
            for (StudioLivePacket.Peer p : peers) {
                if (selfId != null && selfId.equals(p.id())) continue;
                if (b.headerLine >= 0 && p.line() >= b.headerLine && p.line() <= b.bodyEndLine) {
                    rightLim -= 12;
                    int pc = StudioScreen.peerColor(p);
                    g.fill(rightLim - 1, ry + 3, rightLim + 9, ry + 13, pc);
                    blitPeerHead(g, p.id(), rightLim, ry + 4, 8);
                    rightLim -= 2;
                }
            }
            String lbl = b.displayName(i, screens.size()) + "  [" + b.nodes.size() + "]";
            g.drawString(font, trimWidth(font, lbl, Math.max(40, rightLim - x - 12)), x + 8, ry + 4, sel ? GOLD : TEXT, false);
            dropdownHits.add(new HitBtn(x + 2, ry, w - 4, rowH, "pick_scr:" + i, null));
        }
    }

    // =========================================================================
    // 6. ПАНЕЛЬ 1: ПАЛИТРА ЭЛЕМЕНТОВ (+ Box, + Text, + Button, + Image, ...)
    // =========================================================================

    private static void renderPaletteDock(GuiGraphics g, Font font, int mx, int my) {
        int x = paletteDock.x, y = paletteDock.y, w = paletteDock.w, h = paletteDock.currentH();
        drawDockFrame(g, font, paletteDock, tr("❖ Палитра", "❖ Palette"), mx, my, paletteHits);
        if (paletteDock.collapsed) return;

        ParsedScreenBlock scr = activeScreen();
        int ix = x + 6;
        int iw = w - 12;
        int cy = y + 22;

        // Заголовок экрана и цель
        if (scr != null) {
            g.drawString(font, tr("Экран:", "Title:"), ix, cy + 3, MUTED, false);
            cy = drawField(g, font, mx, my, ix + 36, cy, iw - 36, "scr_title", scr.title, paletteHits) + 3;
            g.drawString(font, tr("Цель:", "Target:"), ix, cy + 3, MUTED, false);
            cy = drawField(g, font, mx, my, ix + 36, cy, iw - 36, "scr_target", scr.targetClause, paletteHits) + 5;
        }

        g.fill(ix, cy, ix + iw, cy + 1, EDGE);
        cy += 4;
        g.drawString(font, tr("Добавить элемент:", "Add element:"), ix, cy, GOLD, false);
        cy += 11;

        String[][] types = {
                {"box", "+ Box", tr("Прямоугольная панель / карточка (градиент, рамка, скругление, blur)", "Box / card panel")},
                {"text", "+ Text", tr("Текстовая надпись", "Text label")},
                {"button", "+ Button", tr("Интерактивная кнопка", "Interactive button")},
                {"image", "+ Image", tr("Картинка / спрайт из текстур", "Texture image / sprite")},
                {"bar", "+ Bar", tr("Полоса прогресса / здоровья", "Progress bar")},
                {"slot", "+ Slot", tr("Слот с предметом / иконкой", "Item or icon slot")},
                {"input", "+ Input", tr("Поле ввода текста", "Text input field")},
                {"slider", "+ Slider", tr("Ползунок выбора числа", "Numeric slider")},
                {"toggle", "+ Toggle", tr("Чекбокс / переключатель", "Toggle switch")},
                {"list", "+ List", tr("Прокручиваемый список строк", "Scrollable list")}
        };
        int colW = (iw - 4) / 2;
        for (int i = 0; i < types.length; i++) {
            int bx = (i % 2 == 0) ? ix : ix + colW + 4;
            int by = cy + (i / 2) * 17;
            drawDockBtn(g, font, mx, my, bx, by, colW, 15, types[i][1], "add:" + types[i][0], types[i][2], false, paletteHits);
        }
        cy += (types.length / 2) * 17 + 4;

        g.fill(ix, cy, ix + iw, cy + 1, EDGE);
        cy += 4;
        int half = (iw - 4) / 2;
        drawDockBtn(g, font, mx, my, ix, cy, half, 15, tr("⧉ Дубл.", "⧉ Dupl"), "node:dup",
                tr("Дублировать выбранный элемент (Ctrl+D)", "Duplicate selected element (Ctrl+D)"), false, paletteHits);
        drawDockBtn(g, font, mx, my, ix + half + 4, cy, half, 15, tr("🗑 Удал.", "🗑 Del"), "node:del",
                tr("Удалить выбранный элемент (Delete)", "Delete selected element (Delete)"), false, paletteHits);
        cy += 17;
        drawDockBtn(g, font, mx, my, ix, cy, half, 15, "↶ Undo", "edit:undo",
                tr("Отменить действие на холсте (Ctrl+Z)", "Undo canvas edit (Ctrl+Z)"), false, paletteHits);
        drawDockBtn(g, font, mx, my, ix + half + 4, cy, half, 15, "↷ Redo", "edit:redo",
                tr("Вернуть действие на холсте (Ctrl+Y)", "Redo canvas edit (Ctrl+Y)"), false, paletteHits);
    }

    // =========================================================================
    // 7. ПАНЕЛЬ 2: СЛОИ В СТИЛЕ PHOTOSHOP (👁 Видимость, 🔒 Блокировка, Фильтр L:N)
    // =========================================================================

    private static void renderLayersDock(GuiGraphics g, Font font, ScriptDocument doc, int mx, int my) {
        ParsedScreenBlock scr = activeScreen();
        String title = tr("◫ Слои (Photoshop)", "◫ Layers") + (scr == null ? "" : " [" + scr.nodes.size() + "]");
        drawDockFrame(g, font, layersDock, title, mx, my, layerBtnHits);
        if (layersDock.collapsed || scr == null) return;

        int x = layersDock.x, y = layersDock.y, w = layersDock.w, h = layersDock.currentH();
        int ix = x + 5;
        int iw = w - 10;
        int cy = y + 20;

        // Верхняя полоса фильтрации по реальным номерам слоёв (layer N), как группы слоёв в Photoshop
        Set<Integer> distinctLayers = new LinkedHashSet<>();
        List<Integer> sortedNums = new ArrayList<>();
        for (GuiNode n : scr.nodes) {
            if (distinctLayers.add(n.layer)) sortedNums.add(n.layer);
        }
        sortedNums.sort(Comparator.reverseOrder());

        int fx = ix;
        String allChip = tr("Все", "All");
        int allW = font.width(allChip) + 8;
        drawDockBtn(g, font, mx, my, fx, cy, allW, 13, allChip, "lfilt:all",
                tr("Показать все слои", "Show all layers"), layerFilter == null, layerBtnHits);
        fx += allW + 2;
        for (int ln : sortedNums) {
            String chip = "L:" + ln;
            int cw = font.width(chip) + 6;
            if (fx + cw > ix + iw) break;
            drawDockBtn(g, font, mx, my, fx, cy, cw, 13, chip, "lfilt:" + ln,
                    tr("Изолировать слой layer " + ln, "Isolate layer " + ln),
                    layerFilter != null && layerFilter == ln, layerBtnHits);
            fx += cw + 2;
        }
        cy += 16;

        // Список слоёв сверху вниз (верхние по z-order идут первыми, как в Photoshop)
        List<Integer> drawOrder = getSortedDrawOrder(scr);
        List<Integer> psOrder = new ArrayList<>(drawOrder.size());
        for (int i = drawOrder.size() - 1; i >= 0; i--) {
            int idx = drawOrder.get(i);
            if (layerFilter != null && scr.nodes.get(idx).layer != layerFilter) continue;
            psOrder.add(idx);
        }

        int listTop = cy;
        int footH = 19;
        int listH = Math.max(24, y + h - listTop - footH - 2);
        int rowH = 15;
        int totalH = psOrder.size() * rowH + 4;
        layersDock.maxScroll = Math.max(0, totalH - listH);
        layersDock.scroll = Math.max(0, Math.min(layersDock.scroll, layersDock.maxScroll));

        g.fill(ix, listTop, ix + iw, listTop + listH, 0xFF0D121B);
        border(g, ix, listTop, iw, listH, EDGE);

        g.enableScissor(ix + 1, listTop + 1, ix + iw - 1, listTop + listH - 1);
        int ry = listTop + 2 - layersDock.scroll;
        for (int nodeIdx : psOrder) {
            GuiNode n = scr.nodes.get(nodeIdx);
            if (ry + rowH >= listTop && ry <= listTop + listH) {
                boolean sel = nodeIdx == selectedNodeIdx;
                boolean hot = hover(mx, my, ix + 1, ry, iw - 2, rowH - 1);
                g.fill(ix + 1, ry, ix + iw - 1, ry + rowH - 1, sel ? ROW_SEL : (hot ? ROW_HOT : ROW_BG));
                if (sel) {
                    g.fill(ix + 1, ry, ix + 3, ry + rowH - 1, GOLD);
                }

                // 1) Иконка глаза (видимость слоя 👁)
                int eyeX = ix + 4;
                boolean vis = n.editorVisible && (!n.scriptHidden || showScriptHidden);
                g.drawString(font, n.editorVisible ? "◉" : "○", eyeX, ry + 3,
                        vis ? GREEN : 0xFF525E72, false);

                // 2) Иконка замка (блокировка слоя 🔒)
                int lockX = ix + 15;
                g.drawString(font, n.editorLocked ? "🔒" : "·", lockX, ry + 3,
                        n.editorLocked ? RED : 0xFF525E72, false);

                // 3) Номер слоя L:N
                String lBadge = "L" + n.layer;
                int lbX = ix + 25;
                g.drawString(font, lBadge, lbX, ry + 3, ACCENT, false);

                // 4) Тип и имя/ID элемента (+ голова пира, если кто-то редактирует этот слой)
                int nameX = lbX + font.width(lBadge) + 4;
                int upX = ix + iw - 24;
                int downX = ix + iw - 13;
                int nameRight = upX - 3;
                if (doc != null && doc.live && n.sourceLine >= 0) {
                    java.util.UUID selfId = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
                    for (StudioLivePacket.Peer p : StudioSession.peers(doc.name)) {
                        if (selfId != null && selfId.equals(p.id())) continue;
                        if (p.line() == n.sourceLine) {
                            nameRight -= 11;
                            int pc = StudioScreen.peerColor(p);
                            g.fill(nameRight - 1, ry + 2, nameRight + 9, ry + 12, pc);
                            blitPeerHead(g, p.id(), nameRight, ry + 3, 8);
                            if (hover(mx, my, nameRight - 1, ry + 2, 10, 10)) {
                                hoverTip = p.name();
                                hoverTipX = mx + 8;
                                hoverTipY = my + 14;
                            }
                            nameRight -= 2;
                        }
                    }
                }
                String label = "[" + shortKind(n.kind) + "] " + (!n.id.isEmpty() ? "#" + n.id : n.text);
                if (n.scriptHidden) label = "(hid) " + label;
                g.drawString(font, trimWidth(font, label, Math.max(16, nameRight - nameX)),
                        nameX, ry + 3, sel ? GOLD : (vis ? TEXT : MUTED), false);

                // 5) Кнопки перемещения слоя вверх/вниз (▲ / ▼)
                g.drawString(font, "▲", upX, ry + 3, hover(mx, my, upX, ry, 10, rowH) ? GOLD : MUTED, false);
                g.drawString(font, "▼", downX, ry + 3, hover(mx, my, downX, ry, 10, rowH) ? GOLD : MUTED, false);

                layerRowHits.add(new LayerRowHit(ix + 1, ry, iw - 2, rowH - 1, nodeIdx, eyeX, lockX, upX, downX));
            }
            ry += rowH;
        }
        g.disableScissor();

        // Нижняя панель управления слоями: [+Layer] [-Layer] [👁 Все] [🔒 Фон]
        int fy = listTop + listH + 3;
        int bw = (iw - 6) / 4;
        drawDockBtn(g, font, mx, my, ix, fy, bw, 14, "+L", "layer:inc",
                tr("Поднять выбранный элемент на слой выше (+1 к layer)", "Increase layer (+1)"), false, layerBtnHits);
        drawDockBtn(g, font, mx, my, ix + bw + 2, fy, bw, 14, "-L", "layer:dec",
                tr("Опустить выбранный элемент на слой ниже (-1 к layer)", "Decrease layer (-1)"), false, layerBtnHits);
        drawDockBtn(g, font, mx, my, ix + (bw + 2) * 2, fy, bw, 14, "👁", "layer:vis_all",
                tr("Показать / скрыть все слои", "Show / hide all layers"), false, layerBtnHits);
        drawDockBtn(g, font, mx, my, ix + (bw + 2) * 3, fy, iw - (bw + 2) * 3, 14, "🔒", "layer:lock_sel",
                tr("Заблокировать / разблокировать выбранный слой", "Lock / unlock selected layer"),
                selectedNode() != null && selectedNode().editorLocked, layerBtnHits);
    }

    private static String shortKind(String kind) {
        return switch (kind) {
            case "box" -> "Box";
            case "text" -> "Txt";
            case "button" -> "Btn";
            case "image" -> "Img";
            case "bar" -> "Bar";
            case "slot" -> "Slt";
            case "input" -> "Inp";
            case "slider" -> "Sld";
            case "toggle" -> "Tgl";
            case "list" -> "Lst";
            default -> "Raw";
        };
    }

    // =========================================================================
    // 8. ПАНЕЛЬ 3: ИНСПЕКТОР СВОЙСТВ СПРАВА
    // =========================================================================

    private static void renderInspectorDock(GuiGraphics g, Font font, int mx, int my) {
        GuiNode n = selectedNode();
        String hdr = tr("⚙ Свойства", "⚙ Inspector") + (n == null ? "" : " [" + shortKind(n.kind) + "]");
        drawDockFrame(g, font, inspectorDock, hdr, mx, my, inspectorHits);
        if (inspectorDock.collapsed) return;

        int x = inspectorDock.x, y = inspectorDock.y, w = inspectorDock.w, h = inspectorDock.currentH();
        int ix = x + 6;
        int iw = w - 12;
        int topY = y + 20;
        int viewH = h - 24;

        if (n == null) {
            g.drawCenteredString(font, tr("Кликните элемент", "Click an element"), x + w / 2, topY + viewH / 2 - 10, MUTED);
            g.drawCenteredString(font, tr("на холсте или в Слоях", "on canvas or Layers"), x + w / 2, topY + viewH / 2 + 2, MUTED);
            return;
        }

        g.enableScissor(x + 1, topY, x + w - 1, y + h - 2);
        int cy = topY + 2 - inspectorDock.scroll;

        // 1. ID и Слой (Layer)
        int half = (iw - 4) / 2;
        g.drawString(font, "ID:", ix, cy + 3, MUTED, false);
        drawField(g, font, mx, my, ix + 18, cy, half - 18, "id", n.id, inspectorHits);

        int lx = ix + half + 4;
        g.drawString(font, "L:", lx, cy + 3, MUTED, false);
        drawDockBtn(g, font, mx, my, lx + 12, cy, 13, 14, "-", "step:layer:-1", null, false, inspectorHits);
        drawField(g, font, mx, my, lx + 26, cy, half - 40, "layer", Integer.toString(n.layer), inspectorHits);
        drawDockBtn(g, font, mx, my, lx + half - 13, cy, 13, 14, "+", "step:layer:1", null, false, inspectorHits);
        cy += 17;

        // 2. Координаты X, Y и размеры W, H
        cy = drawStepperRow(g, font, mx, my, ix, cy, iw, "X", "x", n.x, "Y", "y", n.y) + 3;
        if (!"text".equals(n.kind)) {
            cy = drawStepperRow(g, font, mx, my, ix, cy, iw, "W", "w", n.w, "H", "h", n.h) + 3;
        }

        // 3. Якорь привязки (Anchor 3×3 сетка + название)
        g.drawString(font, "Anchor:", ix, cy + 3, MUTED, false);
        g.drawString(font, n.anchor.name().toLowerCase(Locale.ROOT), ix + 44, cy + 3, GOLD, false);
        int gridX = ix + iw - 42;
        Anchor[] anchors = {
                Anchor.TOP_LEFT, Anchor.TOP, Anchor.TOP_RIGHT,
                Anchor.LEFT, Anchor.CENTER, Anchor.RIGHT,
                Anchor.BOTTOM_LEFT, Anchor.BOTTOM, Anchor.BOTTOM_RIGHT
        };
        for (int ai = 0; ai < 9; ai++) {
            int ax = gridX + (ai % 3) * 14;
            int ay = cy + (ai / 3) * 9;
            boolean on = n.anchor == anchors[ai];
            drawDockBtn(g, font, mx, my, ax, ay, 13, 8, "•", "anchor:" + anchors[ai].name(),
                    "Anchor: " + anchors[ai].name().toLowerCase(Locale.ROOT) + tr(" (пересчитывает X/Y на месте)", " (keeps visual pos)"),
                    on, inspectorHits);
        }
        cy += 29;

        g.fill(ix, cy, ix + iw, cy + 1, EDGE);
        cy += 4;

        // 4. Специфичные свойства по типу элемента
        switch (n.kind) {
            case "box" -> {
                // Вкладки выбора цели для палитры цветов: Заливка / Градиент / Рамка
                int tabW = (iw - 4) / 3;
                drawDockBtn(g, font, mx, my, ix, cy, tabW, 13, tr("Цвет", "Fill"), "ctarg:0", null, colorTarget == 0, inspectorHits);
                drawDockBtn(g, font, mx, my, ix + tabW + 2, cy, tabW, 13, tr("Град.", "Grad"), "ctarg:1", null, colorTarget == 1, inspectorHits);
                drawDockBtn(g, font, mx, my, ix + (tabW + 2) * 2, cy, iw - (tabW + 2) * 2, 13, tr("Рамка", "Bord"), "ctarg:2", null, colorTarget == 2, inspectorHits);
                cy += 15;

                String cField = colorTarget == 0 ? "color" : (colorTarget == 1 ? "boxGrad" : "boxBorder");
                int cVal = colorTarget == 0 ? n.color : (colorTarget == 1 ? n.boxGrad : n.boxBorder);
                cy = drawColorSection(g, font, mx, my, ix, cy, iw, cField, cVal) + 3;

                // Скругление (Radius) и Толщина рамки (Border W)
                cy = drawStepperRow(g, font, mx, my, ix, cy, iw, "Rad", "boxRadius", n.boxRadius, "Brd", "boxBorderW", n.boxBorderW) + 3;

                // Флаги Box: Shadow, Blur, Horiz
                int third = (iw - 4) / 3;
                drawDockBtn(g, font, mx, my, ix, cy, third, 14, "Shadow", "tog:shadow", null, n.shadow, inspectorHits);
                drawDockBtn(g, font, mx, my, ix + third + 2, cy, third, 14, "Blur", "tog:blur", null, n.boxBlur, inspectorHits);
                drawDockBtn(g, font, mx, my, ix + (third + 2) * 2, cy, iw - (third + 2) * 2, 14,
                        n.boxHoriz ? "Horiz" : "Vert", "tog:boxHoriz", null, n.boxHoriz, inspectorHits);
                cy += 17;
            }
            case "text" -> {
                g.drawString(font, tr("Текст:", "Text:"), ix, cy + 3, MUTED, false);
                cy = drawField(g, font, mx, my, ix + 36, cy, iw - 36, "text", n.text, inspectorHits) + 3;
                cy = drawColorSection(g, font, mx, my, ix, cy, iw, "color", n.color) + 3;
                drawDockBtn(g, font, mx, my, ix, cy, half, 14, "Shadow", "tog:shadow", null, n.shadow, inspectorHits);
                g.drawString(font, "Scale:", ix + half + 6, cy + 3, MUTED, false);
                cy = drawField(g, font, mx, my, ix + half + 38, cy, half - 38, "scale", fmtNum(n.scale), inspectorHits) + 3;
            }
            case "button" -> {
                g.drawString(font, tr("Текст:", "Label:"), ix, cy + 3, MUTED, false);
                cy = drawField(g, font, mx, my, ix + 36, cy, iw - 36, "text", n.text, inspectorHits) + 3;
            }
            case "image" -> {
                g.drawString(font, "Tex:", ix, cy + 3, MUTED, false);
                cy = drawField(g, font, mx, my, ix + 26, cy, iw - 26, "text", n.text, inspectorHits) + 3;
                cy = drawStepperRow(g, font, mx, my, ix, cy, iw, "TW", "texW", n.texW, "TH", "texH", n.texH) + 3;
                cy = drawStepperRow(g, font, mx, my, ix, cy, iw, "Frm", "frames", n.frames, "FPS", "fps", n.fps) + 3;
                cy = drawStepperRow(g, font, mx, my, ix, cy, iw, "Cir", "hoverCircle", n.hoverCircle, "Rot", "rotation", (int) Math.round(n.rotation)) + 3;
                drawDockBtn(g, font, mx, my, ix, cy, iw, 14, tr("Следовать за курсором (follow cursor)", "Follow cursor"),
                        "tog:followCursor", null, n.followCursor, inspectorHits);
                cy += 17;
                cy = drawColorSection(g, font, mx, my, ix, cy, iw, "color", n.color) + 3;
            }
            case "bar" -> {
                g.drawString(font, "Val:", ix, cy + 3, MUTED, false);
                drawField(g, font, mx, my, ix + 22, cy, half - 22, "barValue", n.barValue, inspectorHits);
                g.drawString(font, "Max:", ix + half + 4, cy + 3, MUTED, false);
                cy = drawField(g, font, mx, my, ix + half + 28, cy, half - 24, "barMax", n.barMax, inspectorHits) + 3;

                g.drawString(font, tr("Текст:", "Text:"), ix, cy + 3, MUTED, false);
                cy = drawField(g, font, mx, my, ix + 36, cy, iw - 36, "text", n.text, inspectorHits) + 3;

                drawDockBtn(g, font, mx, my, ix, cy, iw - 50, 14, "Dir: " + n.barDir.name().toLowerCase(Locale.ROOT),
                        "cycle:barDir", null, false, inspectorHits);
                drawDockBtn(g, font, mx, my, ix + iw - 48, cy, 48, 14, "Shadow", "tog:shadow", null, n.shadow, inspectorHits);
                cy += 17;

                int tabW = (iw - 2) / 2;
                drawDockBtn(g, font, mx, my, ix, cy, tabW, 13, tr("Полоса", "Fill Col"), "ctarg:0", null, colorTarget == 0, inspectorHits);
                drawDockBtn(g, font, mx, my, ix + tabW + 2, cy, iw - tabW - 2, 13, tr("Фон", "Bg Col"), "ctarg:1", null, colorTarget != 0, inspectorHits);
                cy += 15;
                cy = drawColorSection(g, font, mx, my, ix, cy, iw, colorTarget == 0 ? "color" : "barBgColor",
                        colorTarget == 0 ? n.color : n.barBgColor) + 3;
            }
            case "slot" -> {
                drawDockBtn(g, font, mx, my, ix, cy, half, 14, "Kind: " + n.slotContent.name(), "cycle:slotContent", null, false, inspectorHits);
                drawDockBtn(g, font, mx, my, ix + half + 4, cy, half, 14, "Frame", "tog:slotFrame", null, n.slotFrame, inspectorHits);
                cy += 17;
                g.drawString(font, "Item:", ix, cy + 3, MUTED, false);
                cy = drawField(g, font, mx, my, ix + 30, cy, iw - 30, "text", n.text, inspectorHits) + 3;
                cy = drawStepperRow(g, font, mx, my, ix, cy, iw, "Cnt", "slotCount", n.slotCount, "L", "layer", n.layer) + 3;
            }
            case "input" -> {
                g.drawString(font, "Val:", ix, cy + 3, MUTED, false);
                cy = drawField(g, font, mx, my, ix + 26, cy, iw - 26, "inputValue", n.inputValue, inspectorHits) + 3;
                g.drawString(font, "Hint:", ix, cy + 3, MUTED, false);
                cy = drawField(g, font, mx, my, ix + 26, cy, iw - 26, "placeholder", n.placeholder, inspectorHits) + 3;
            }
            case "slider" -> {
                g.drawString(font, "Lbl:", ix, cy + 3, MUTED, false);
                cy = drawField(g, font, mx, my, ix + 26, cy, iw - 26, "text", n.text, inspectorHits) + 3;
                g.drawString(font, "Min:", ix, cy + 3, MUTED, false);
                drawField(g, font, mx, my, ix + 24, cy, half - 24, "sliderMin", fmtNum(n.sliderMin), inspectorHits);
                g.drawString(font, "Max:", ix + half + 4, cy + 3, MUTED, false);
                cy = drawField(g, font, mx, my, ix + half + 28, cy, half - 24, "sliderMax", fmtNum(n.sliderMax), inspectorHits) + 3;
                g.drawString(font, "Val:", ix, cy + 3, MUTED, false);
                drawField(g, font, mx, my, ix + 24, cy, half - 24, "sliderVal", fmtNum(n.sliderVal), inspectorHits);
                g.drawString(font, "Stp:", ix + half + 4, cy + 3, MUTED, false);
                cy = drawField(g, font, mx, my, ix + half + 28, cy, half - 24, "sliderStep", fmtNum(n.sliderStep), inspectorHits) + 3;
            }
            case "toggle" -> {
                g.drawString(font, "Lbl:", ix, cy + 3, MUTED, false);
                cy = drawField(g, font, mx, my, ix + 26, cy, iw - 26, "text", n.text, inspectorHits) + 3;
                drawDockBtn(g, font, mx, my, ix, cy, iw, 14, "Checked: " + n.toggleChecked, "tog:toggleChecked", null, n.toggleChecked, inspectorHits);
                cy += 17;
            }
            case "list" -> {
                g.drawString(font, "Hdr:", ix, cy + 3, MUTED, false);
                cy = drawField(g, font, mx, my, ix + 26, cy, iw - 26, "listHeader", n.listHeader, inspectorHits) + 3;
                g.drawString(font, "From:", ix, cy + 3, MUTED, false);
                cy = drawField(g, font, mx, my, ix + 30, cy, iw - 30, "listFrom", n.listFrom, inspectorHits) + 3;
                int third = (iw - 4) / 3;
                drawDockBtn(g, font, mx, my, ix, cy, third, 14, n.listHoriz ? "Horiz" : "Vert", "tog:listHoriz", null, n.listHoriz, inspectorHits);
                drawDockBtn(g, font, mx, my, ix + third + 2, cy, third, 14, "Select", "tog:listSelect", null, n.listSelect, inspectorHits);
                drawDockBtn(g, font, mx, my, ix + (third + 2) * 2, cy, iw - (third + 2) * 2, 14, "Scroll", "tog:listScroll", null, !n.listNoScrollbar, inspectorHits);
                cy += 17;
            }
        }

        g.fill(ix, cy, ix + iw, cy + 1, EDGE);
        cy += 4;

        // 5. Действие клика (Run / Function / Close), Тултип, Клавиша, Keep Open, Hidden
        g.drawString(font, "Run:", ix, cy + 3, MUTED, false);
        cy = drawField(g, font, mx, my, ix + 26, cy, iw - 26, "runAction", n.runAction, inspectorHits) + 3;

        g.drawString(font, "Tip:", ix, cy + 3, MUTED, false);
        cy = drawField(g, font, mx, my, ix + 26, cy, iw - 26, "tooltip", n.tooltip, inspectorHits) + 3;

        g.drawString(font, "Key:", ix, cy + 3, MUTED, false);
        drawField(g, font, mx, my, ix + 26, cy, half - 26, "hotkey", n.hotkey, inspectorHits);
        drawDockBtn(g, font, mx, my, ix + half + 4, cy, half, 14, "Keep Open", "tog:keepOpen", null, n.keepOpen, inspectorHits);
        cy += 17;

        drawDockBtn(g, font, mx, my, ix, cy, half, 14, "Hidden", "tog:scriptHidden",
                tr("Скрыть элемент при старте экрана (флаг hidden в скрипте)", "Start element hidden in script"),
                n.scriptHidden, inspectorHits);
        drawDockBtn(g, font, mx, my, ix + half + 4, cy, half, 14, tr("🔒 Блок.", "🔒 Lock"), "layer:lock_sel",
                tr("Заблокировать перемещение мышкой на холсте", "Lock dragging on canvas"),
                n.editorLocked, inspectorHits);
        cy += 18;

        int contentH = (cy + inspectorDock.scroll) - (topY + 2);
        inspectorDock.maxScroll = Math.max(0, contentH - viewH + 6);
        inspectorDock.scroll = Math.max(0, Math.min(inspectorDock.scroll, inspectorDock.maxScroll));
        g.disableScissor();
    }

    private static int drawStepperRow(GuiGraphics g, Font font, int mx, int my,
                                      int x, int y, int w,
                                      String lbl1, String field1, int val1,
                                      String lbl2, String field2, int val2) {
        int half = (w - 4) / 2;
        drawSingleStepper(g, font, mx, my, x, y, half, lbl1, field1, val1);
        drawSingleStepper(g, font, mx, my, x + half + 4, y, half, lbl2, field2, val2);
        return y + 14;
    }

    private static void drawSingleStepper(GuiGraphics g, Font font, int mx, int my,
                                          int x, int y, int w, String lbl, String field, int val) {
        g.drawString(font, lbl + ":", x, y + 3, MUTED, false);
        int lw = font.width(lbl + ":") + 2;
        drawDockBtn(g, font, mx, my, x + lw, y, 12, 14, "-", "step:" + field + ":-1", null, false, inspectorHits);
        int fw = Math.max(20, w - lw - 26);
        drawField(g, font, mx, my, x + lw + 13, y, fw, field, Integer.toString(val), inspectorHits);
        drawDockBtn(g, font, mx, my, x + w - 12, y, 12, 14, "+", "step:" + field + ":1", null, false, inspectorHits);
    }

    private static int drawColorSection(GuiGraphics g, Font font, int mx, int my,
                                        int x, int y, int w, String field, int colorVal) {
        // Квадратик текущего цвета + текстовое поле #AARRGGBB
        g.fill(x, y, x + 14, y + 14, 0xFF000000);
        if (colorVal != 0) {
            g.fill(x + 1, y + 1, x + 13, y + 13, (colorVal >>> 24) == 0 ? (colorVal | 0xFF000000) : colorVal);
        }
        border(g, x, y, 14, 14, EDGE_HOT);
        String hexStr = colorVal == 0 ? "#00000000" : formatHexColor(colorVal, true);
        drawField(g, font, mx, my, x + 17, y, w - 17, field, hexStr, inspectorHits);
        int cy = y + 16;

        // 16 быстрых образцов цвета (Swatches)
        int cols = 8;
        int sw = (w - (cols - 1) * 2) / cols;
        for (int i = 0; i < SWATCHES.length; i++) {
            int sx = x + (i % cols) * (sw + 2);
            int sy = cy + (i / cols) * 11;
            int sc = SWATCHES[i];
            g.fill(sx, sy, sx + sw, sy + 9, sc == 0 ? 0xFF111827 : ((sc >>> 24) == 0 ? (sc | 0xFF000000) : sc));
            if (sc == 0) {
                g.drawString(font, "×", sx + (sw - 5) / 2, sy + 1, RED, false);
            }
            boolean hot = hover(mx, my, sx, sy, sw, 9);
            border(g, sx, sy, sw, 9, hot ? GOLD : EDGE);
            inspectorHits.add(new HitBtn(sx, sy, sw, 9, "swatch:" + field + ":" + sc, formatHexColor(sc, true)));
        }
        return cy + 22;
    }

    private static boolean isExpandableField(String fieldId) {
        return switch (fieldId) {
            case "text", "tooltip", "runAction", "placeholder", "inputValue",
                    "listHeader", "listFrom", "scr_title", "scr_target" -> true;
            default -> false;
        };
    }

    private static int drawField(GuiGraphics g, Font font, int mx, int my,
                                 int x, int y, int w, String fieldId, String value, List<HitBtn> hits) {
        boolean expandable = isExpandableField(fieldId) && w >= 42;
        int fw = expandable ? w - 15 : w;
        boolean focused = fieldId.equals(focusedField);
        boolean hot = hover(mx, my, x, y, fw, 14);
        if (focused) {
            activeField.render(g, font, x, y, fw, 14, true, "", 0xFF0B1018, GOLD, GOLD, MUTED);
        } else {
            g.fill(x, y, x + fw, y + 14, 0xFF0B1018);
            border(g, x, y, fw, 14, hot ? ACCENT : EDGE);
            String shown = trimWidth(font, value == null ? "" : value, fw - 6);
            g.drawString(font, shown, x + 3, y + 3, TEXT, false);
        }
        hits.add(new HitBtn(x, y, fw, 14, "field:" + fieldId, null));
        if (expandable) {
            int ex = x + fw + 1;
            drawDockBtn(g, font, mx, my, ex, y, 14, 14, "⤢", "expand:" + fieldId,
                    tr("Раскрыть и редактировать всю строку", "Expand full text editor"), false, hits);
        }
        return y + 14;
    }

    private static void renderExpandedEditor(GuiGraphics g, Font font, int mx, int my) {
        g.fill(viewX, viewY, viewX + viewW, viewY + viewH, 0x99000000);
        expW = Math.min(540, Math.max(260, viewW - 40));
        expH = 114;
        expX = viewX + (viewW - expW) / 2;
        expY = viewY + (viewH - expH) / 2;

        g.fill(expX + 3, expY + 3, expX + expW + 3, expY + expH + 3, 0x77000000);
        g.fill(expX, expY, expX + expW, expY + expH, 0xFA131B28);
        border(g, expX, expY, expW, expH, GOLD);
        g.fill(expX + 1, expY + 1, expX + expW - 1, expY + 18, PANEL_HDR);
        g.drawString(font, tr("Редактирование поля: ", "Edit field: ") + expandedField, expX + 8, expY + 5, GOLD, false);

        // Полноценное широкое поле ввода с курсором, мышкой и выделением
        expandedInput.render(g, font, expX + 8, expY + 24, expW - 16, 18, true, "", 0xFF0B1018, ACCENT, TEXT, MUTED);

        // Предпросмотр полной строки с переносом и цветовыми кодами &
        int pvX = expX + 8;
        int pvY = expY + 46;
        int pvW = expW - 16;
        int pvH = 42;
        g.fill(pvX, pvY, pvX + pvW, pvY + pvH, 0xFF0D121B);
        border(g, pvX, pvY, pvW, pvH, EDGE);
        String preview = colorize(expandedInput.value()).replace("%nl%", "\n").replace("%newline%", "\n");
        g.enableScissor(pvX + 2, pvY + 2, pvX + pvW - 2, pvY + pvH - 2);
        int py = pvY + 4;
        for (String line : preview.split("\n", -1)) {
            for (var seq : font.split(net.minecraft.network.chat.Component.literal(line), Math.max(40, pvW - 8))) {
                if (py > pvY + pvH - 8) break;
                g.drawString(font, seq, pvX + 4, py, TEXT, false);
                py += 10;
            }
        }
        g.disableScissor();

        // Кнопки Готово / Отмена
        expOkY = expY + expH - 21;
        String okLbl = tr("✔ Готово (Enter)", "✔ Done (Enter)");
        String canLbl = tr("Отмена (Esc)", "Cancel (Esc)");
        int okW = font.width(okLbl) + 14;
        int canW = font.width(canLbl) + 14;
        expOkX = expX + expW - okW - 8;
        expCancelX = expOkX - canW - 6;
        boolean okHot = hover(mx, my, expOkX, expOkY, okW, 15);
        boolean canHot = hover(mx, my, expCancelX, expOkY, canW, 15);
        g.fill(expOkX, expOkY, expOkX + okW, expOkY + 15, okHot ? 0xFF1F4E38 : 0xFF173A2B);
        border(g, expOkX, expOkY, okW, 15, GREEN);
        g.drawString(font, okLbl, expOkX + 7, expOkY + 4, GREEN, false);

        g.fill(expCancelX, expOkY, expCancelX + canW, expOkY + 15, canHot ? ROW_HOT : ROW_BG);
        border(g, expCancelX, expOkY, canW, 15, EDGE_HOT);
        g.drawString(font, canLbl, expCancelX + 7, expOkY + 4, TEXT, false);
    }

    private static GuiNode findPeerNode(ParsedScreenBlock scr, int peerLine) {
        if (scr == null || scr.headerLine < 0 || peerLine < scr.headerLine || peerLine > scr.bodyEndLine) {
            return null;
        }
        GuiNode best = null;
        int bestDist = Integer.MAX_VALUE;
        for (GuiNode n : scr.nodes) {
            if (n.sourceLine == peerLine) return n;
            if (n.sourceLine >= 0) {
                int d = Math.abs(n.sourceLine - peerLine);
                if (d < bestDist && d <= 2) {
                    bestDist = d;
                    best = n;
                }
            }
        }
        return best;
    }

    private static void blitPeerHead(GuiGraphics g, java.util.UUID id, int x, int y, int size) {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation skin = DefaultPlayerSkin.getDefaultSkin(id);
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(id);
            if (info != null) skin = info.getSkinLocation();
        }
        g.blit(skin, x, y, size, size, 8, 8, 8, 8, 64, 64);
        g.blit(skin, x, y, size, size, 40, 8, 8, 8, 64, 64);
    }

    // =========================================================================
    // 9. ОБРАБОТКА МЫШИ И КЛАВИАТУРЫ НА ХОЛСТЕ И В ПАНЕЛЯХ
    // =========================================================================

    public static boolean mouseClicked(Font font, ScriptDocument doc, int mx, int my, int button) {
        ensureSynced(doc);
        if (mx < viewX || mx >= viewX + viewW || my < viewY || my >= viewY + viewH) {
            return false;
        }

        // 0. Раскрытое модальное окно редактирования строки
        if (expandedField != null) {
            if (!hover(mx, my, expX, expY, expW, expH)) {
                closeExpandedEditor(doc, true);
                return true;
            }
            String okLbl = tr("✔ Готово (Enter)", "✔ Done (Enter)");
            String canLbl = tr("Отмена (Esc)", "Cancel (Esc)");
            int okW = font.width(okLbl) + 14;
            int canW = font.width(canLbl) + 14;
            if (hover(mx, my, expOkX, expOkY, okW, 15)) {
                closeExpandedEditor(doc, true);
                return true;
            }
            if (hover(mx, my, expCancelX, expOkY, canW, 15)) {
                closeExpandedEditor(doc, false);
                return true;
            }
            expandedInput.mouseClicked(font, mx, my, button);
            return true;
        }

        // Если кликнули внутри уже активного поля — перемещаем курсор мышкой / выделяем
        if (focusedField != null && activeField.mouseClicked(font, mx, my, button)) {
            return true;
        }
        commitFocusedField(doc);

        // 1. Выпадающий список экранов
        if (screenDropdownOpen) {
            for (HitBtn hit : dropdownHits) {
                if (hover(mx, my, hit.x, hit.y, hit.w, hit.h) && hit.id.startsWith("pick_scr:")) {
                    activeScreenIdx = Integer.parseInt(hit.id.substring("pick_scr:".length()));
                    selectedNodeIdx = -1;
                    layerFilter = null;
                    undoStack.clear();
                    redoStack.clear();
                    screenDropdownOpen = false;
                    return true;
                }
            }
            screenDropdownOpen = false;
            return true;
        }

        // 2. Верхняя панель управления
        if (my < viewY + TOP_BAR_H) {
            for (HitBtn hit : topHits) {
                if (hover(mx, my, hit.x, hit.y, hit.w, hit.h)) {
                    handleActionId(doc, hit.id);
                    return true;
                }
            }
            return true;
        }

        // 3. Плавающие панели инструментов (проверяем в обратном порядке отрисовки: Инспектор -> Слои -> Палитра)
        if (!allPanelsHiddenTab) {
            if (inspectorDock.open && hover(mx, my, inspectorDock.x, inspectorDock.y, inspectorDock.w, inspectorDock.currentH())) {
                return clickDock(inspectorDock, inspectorHits, doc, mx, my);
            }
            if (layersDock.open && hover(mx, my, layersDock.x, layersDock.y, layersDock.w, layersDock.currentH())) {
                return clickLayersDock(doc, mx, my);
            }
            if (paletteDock.open && hover(mx, my, paletteDock.x, paletteDock.y, paletteDock.w, paletteDock.currentH())) {
                return clickDock(paletteDock, paletteHits, doc, mx, my);
            }
        }

        // 4. Клик по холсту: ПКМ или СКМ — панорамирование
        if (button == 1 || button == 2) {
            dragMode = DragMode.CANVAS_PAN;
            dragStartMouseX = mx;
            dragStartMouseY = my;
            dragStartPanX = panX;
            dragStartPanY = panY;
            return true;
        }

        ParsedScreenBlock scr = activeScreen();
        if (scr == null) return true;

        int vmx = Math.round((mx - stageX) / effScale);
        int vmy = Math.round((my - stageY) / effScale);

        // Проверяем маркеры ресайза выделенного элемента
        GuiNode sel = selectedNode();
        if (sel != null && sel.editorVisible && !sel.editorLocked && !"text".equals(sel.kind)) {
            int[] b = nodeBounds(font, sel, virtualW, virtualH);
            DragMode handle = hitResizeHandle(vmx, vmy, b[0], b[1], b[2], b[3]);
            if (handle != DragMode.NONE) {
                pushUndo();
                dragMode = handle;
                dragStartMouseX = vmx;
                dragStartMouseY = vmy;
                dragStartX = sel.x;
                dragStartY = sel.y;
                dragStartW = b[2];
                dragStartH = b[3];
                return true;
            }
        }

        // Находим все видимые незаблокированные элементы под курсором (сверху вниз по слоям)
        List<Integer> drawOrder = getSortedDrawOrder(scr);
        List<Integer> hitsUnderMouse = new ArrayList<>();
        for (int i = drawOrder.size() - 1; i >= 0; i--) {
            int idx = drawOrder.get(i);
            GuiNode node = scr.nodes.get(idx);
            if (!node.editorVisible || node.editorLocked) continue;
            if (node.scriptHidden && !showScriptHidden && idx != selectedNodeIdx) continue;
            if (layerFilter != null && node.layer != layerFilter && idx != selectedNodeIdx) continue;
            int[] b = nodeBounds(font, node, virtualW, virtualH);
            if (vmx >= b[0] && vmy >= b[1] && vmx < b[0] + b[2] && vmy < b[1] + b[3]) {
                hitsUnderMouse.add(idx);
            }
        }

        if (!hitsUnderMouse.isEmpty()) {
            int pickIdx = hitsUnderMouse.get(0);
            // Alt+Клик циклически выбирает элементы, лежащие друг под другом
            if (Screen.hasAltDown() && hitsUnderMouse.size() > 1) {
                int curPos = hitsUnderMouse.indexOf(selectedNodeIdx);
                if (curPos >= 0) {
                    pickIdx = hitsUnderMouse.get((curPos + 1) % hitsUnderMouse.size());
                }
            }
            selectNode(doc, pickIdx);
            GuiNode picked = scr.nodes.get(pickIdx);
            if (!picked.editorLocked) {
                pushUndo();
                dragMode = DragMode.NODE_MOVE;
                dragStartMouseX = vmx;
                dragStartMouseY = vmy;
                dragStartX = picked.x;
                dragStartY = picked.y;
            }
            return true;
        }

        // Клик в пустое место снимает выделение
        selectedNodeIdx = -1;
        return true;
    }

    private static boolean clickDock(FloatingDock dock, List<HitBtn> hits, ScriptDocument doc, int mx, int my) {
        // Кнопки в шапке и внутри панели
        for (HitBtn hit : hits) {
            if (hover(mx, my, hit.x, hit.y, hit.w, hit.h)) {
                handleActionId(doc, hit.id, mx, my);
                return true;
            }
        }
        // Перетаскивание за шапку панели
        if (my < dock.y + 18) {
            dragMode = DragMode.DOCK_MOVE;
            draggedDock = dock;
            dragStartMouseX = mx - dock.x;
            dragStartMouseY = my - dock.y;
            return true;
        }
        // Ресайз высоты панели за нижний край
        if (!dock.collapsed && my >= dock.y + dock.h - 5) {
            dragMode = DragMode.DOCK_RESIZE_H;
            draggedDock = dock;
            dragStartMouseY = my;
            dragStartH = dock.h;
            return true;
        }
        return true;
    }

    private static boolean clickLayersDock(ScriptDocument doc, int mx, int my) {
        for (HitBtn hit : layerBtnHits) {
            if (hover(mx, my, hit.x, hit.y, hit.w, hit.h)) {
                handleActionId(doc, hit.id);
                return true;
            }
        }
        ParsedScreenBlock scr = activeScreen();
        if (scr != null && !layersDock.collapsed) {
            for (LayerRowHit row : layerRowHits) {
                if (hover(mx, my, row.x, row.y, row.w, row.h)) {
                    GuiNode n = scr.nodes.get(row.nodeIndex);
                    if (mx >= row.eyeX - 1 && mx < row.eyeX + 10) {
                        n.editorVisible = !n.editorVisible;
                        return true;
                    }
                    if (mx >= row.lockX - 1 && mx < row.lockX + 10) {
                        n.editorLocked = !n.editorLocked;
                        return true;
                    }
                    if (mx >= row.upX - 1 && mx < row.upX + 10) {
                        moveNodeInStack(doc, scr, row.nodeIndex, true);
                        return true;
                    }
                    if (mx >= row.downX - 1 && mx < row.downX + 10) {
                        moveNodeInStack(doc, scr, row.nodeIndex, false);
                        return true;
                    }
                    selectNode(doc, row.nodeIndex);
                    return true;
                }
            }
        }
        return clickDock(layersDock, List.of(), doc, mx, my);
    }

    private static void moveNodeInStack(ScriptDocument doc, ParsedScreenBlock scr, int idx, boolean upVisual) {
        if (idx < 0 || idx >= scr.nodes.size()) return;
        pushUndo();
        GuiNode node = scr.nodes.get(idx);
        if (upVisual) {
            // Ищем следующий элемент с тем же или большим layer
            if (idx + 1 < scr.nodes.size()) {
                GuiNode next = scr.nodes.get(idx + 1);
                if (next.layer > node.layer) node.layer = next.layer;
                scr.nodes.set(idx, next);
                scr.nodes.set(idx + 1, node);
                selectedNodeIdx = idx + 1;
            } else {
                node.layer++;
                selectedNodeIdx = idx;
            }
        } else {
            if (idx - 1 >= 0) {
                GuiNode prev = scr.nodes.get(idx - 1);
                if (prev.layer < node.layer) node.layer = prev.layer;
                scr.nodes.set(idx, prev);
                scr.nodes.set(idx - 1, node);
                selectedNodeIdx = idx - 1;
            } else {
                node.layer--;
                selectedNodeIdx = idx;
            }
        }
        syncLiveIfNeeded(doc);
    }

    public static boolean mouseDragged(Font font, int mx, int my, int button) {
        if (expandedField != null && expandedInput.mouseDragged(font, mx)) return true;
        if (focusedField != null && activeField.mouseDragged(font, mx)) return true;
        if (dragMode == DragMode.NONE) return false;
        if (dragMode == DragMode.DOCK_MOVE && draggedDock != null) {
            draggedDock.x = mx - dragStartMouseX;
            draggedDock.y = my - dragStartMouseY;
            draggedDock.placed = true;
            return true;
        }
        if (dragMode == DragMode.DOCK_RESIZE_H && draggedDock != null) {
            draggedDock.h = Math.max(90, Math.min(viewH - 30, dragStartH + (my - dragStartMouseY)));
            return true;
        }
        if (dragMode == DragMode.CANVAS_PAN) {
            panX = dragStartPanX + (mx - dragStartMouseX);
            panY = dragStartPanY + (my - dragStartMouseY);
            return true;
        }

        GuiNode sel = selectedNode();
        if (sel == null || sel.editorLocked) return true;

        int vmx = Math.round((mx - stageX) / effScale);
        int vmy = Math.round((my - stageY) / effScale);
        int dx = vmx - dragStartMouseX;
        int dy = vmy - dragStartMouseY;
        int step = (snapToGrid && gridSize > 0 && !Screen.hasAltDown()) ? gridSize : 1;

        if (dragMode == DragMode.NODE_MOVE) {
            sel.x = snap(dragStartX + dx, step);
            sel.y = snap(dragStartY + dy, step);
            return true;
        }

        // Ресайз за 8 маркеров с учётом Anchor элемента
        int newW = dragStartW;
        int newH = dragStartH;
        int shiftLeft = 0;
        int shiftTop = 0;
        if (dragMode == DragMode.NODE_E || dragMode == DragMode.NODE_NE || dragMode == DragMode.NODE_SE) {
            newW = Math.max(4, snap(dragStartW + dx, step));
        }
        if (dragMode == DragMode.NODE_W || dragMode == DragMode.NODE_NW || dragMode == DragMode.NODE_SW) {
            newW = Math.max(4, snap(dragStartW - dx, step));
            shiftLeft = dragStartW - newW;
        }
        if (dragMode == DragMode.NODE_S || dragMode == DragMode.NODE_SE || dragMode == DragMode.NODE_SW) {
            newH = Math.max(4, snap(dragStartH + dy, step));
        }
        if (dragMode == DragMode.NODE_N || dragMode == DragMode.NODE_NE || dragMode == DragMode.NODE_NW) {
            newH = Math.max(4, snap(dragStartH - dy, step));
            shiftTop = dragStartH - newH;
        }

        int oldScreenX = sel.anchor.resolveX(virtualW, dragStartW, dragStartX);
        int oldScreenY = sel.anchor.resolveY(virtualH, dragStartH, dragStartY);
        int nextScreenX = oldScreenX + shiftLeft;
        int nextScreenY = oldScreenY + shiftTop;
        int baseAncX = sel.anchor.resolveX(virtualW, newW, 0);
        int baseAncY = sel.anchor.resolveY(virtualH, newH, 0);

        sel.w = newW;
        sel.h = newH;
        sel.x = nextScreenX - baseAncX;
        sel.y = nextScreenY - baseAncY;
        return true;
    }

    public static boolean mouseReleased(ScriptDocument doc) {
        activeField.mouseReleased();
        expandedInput.mouseReleased();
        if (dragMode != DragMode.NONE) {
            boolean wasNodeEdit = dragMode != DragMode.DOCK_MOVE && dragMode != DragMode.DOCK_RESIZE_H && dragMode != DragMode.CANVAS_PAN;
            dragMode = DragMode.NONE;
            draggedDock = null;
            if (wasNodeEdit) {
                syncLiveIfNeeded(doc);
            }
            return true;
        }
        return false;
    }

    public static boolean mouseScrolled(int mx, int my, double delta) {
        int step = (int) Math.signum(delta) * -14;
        if (!allPanelsHiddenTab) {
            if (inspectorDock.open && !inspectorDock.collapsed
                    && hover(mx, my, inspectorDock.x, inspectorDock.y, inspectorDock.w, inspectorDock.currentH())) {
                inspectorDock.scroll = Math.max(0, Math.min(inspectorDock.maxScroll, inspectorDock.scroll + step));
                return true;
            }
            if (layersDock.open && !layersDock.collapsed
                    && hover(mx, my, layersDock.x, layersDock.y, layersDock.w, layersDock.currentH())) {
                layersDock.scroll = Math.max(0, Math.min(layersDock.maxScroll, layersDock.scroll + step));
                return true;
            }
        }
        if (mx >= viewX && mx < viewX + viewW && my >= viewY + TOP_BAR_H && my < viewY + viewH) {
            float factor = delta > 0 ? 1.12f : (1f / 1.12f);
            canvasZoom = Math.max(0.35f, Math.min(2.75f, canvasZoom * factor));
            return true;
        }
        return false;
    }

    public static boolean keyPressed(ScriptDocument doc, int key) {
        if (expandedField != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                closeExpandedEditor(doc, false);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                closeExpandedEditor(doc, true);
                return true;
            }
            if (expandedInput.keyPressed(key)) {
                applyFieldLive(expandedField, expandedInput.value());
            }
            return true;
        }
        if (focusedField != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                focusedField = null;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_TAB) {
                commitFocusedField(doc);
                return true;
            }
            if (activeField.keyPressed(key)) {
                if (!fieldUndoPushed) {
                    pushUndo();
                    fieldUndoPushed = true;
                }
                applyFieldLive(focusedField, activeField.value());
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_TAB) {
            allPanelsHiddenTab = !allPanelsHiddenTab;
            return true;
        }
        boolean ctrl = Screen.hasControlDown();
        boolean shift = Screen.hasShiftDown();
        boolean alt = Screen.hasAltDown();

        if (ClientUiSettings.matches("undo", key, ctrl, shift, alt) || (ctrl && !shift && key == GLFW.GLFW_KEY_Z)) {
            undo(doc);
            return true;
        }
        if (ClientUiSettings.matches("redo", key, ctrl, shift, alt) || (ctrl && key == GLFW.GLFW_KEY_Y) || (ctrl && shift && key == GLFW.GLFW_KEY_Z)) {
            redo(doc);
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_D) {
            duplicateSelected(doc);
            return true;
        }
        GuiNode sel = selectedNode();
        if (sel != null && !sel.editorLocked) {
            int step = shift ? 5 : 1;
            if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                deleteSelected(doc);
                return true;
            }
            if (key == GLFW.GLFW_KEY_LEFT) {
                pushUndo();
                sel.x -= step;
                syncLiveIfNeeded(doc);
                return true;
            }
            if (key == GLFW.GLFW_KEY_RIGHT) {
                pushUndo();
                sel.x += step;
                syncLiveIfNeeded(doc);
                return true;
            }
            if (key == GLFW.GLFW_KEY_UP) {
                pushUndo();
                sel.y -= step;
                syncLiveIfNeeded(doc);
                return true;
            }
            if (key == GLFW.GLFW_KEY_DOWN) {
                pushUndo();
                sel.y += step;
                syncLiveIfNeeded(doc);
                return true;
            }
        }
        return false;
    }

    public static boolean charTyped(char ch) {
        if (expandedField != null) {
            if (expandedInput.charTyped(ch)) {
                applyFieldLive(expandedField, expandedInput.value());
            }
            return true;
        }
        if (focusedField != null) {
            if (activeField.charTyped(ch)) {
                if (!fieldUndoPushed) {
                    pushUndo();
                    fieldUndoPushed = true;
                }
                applyFieldLive(focusedField, activeField.value());
            }
            return true;
        }
        return false;
    }

    private static void closeExpandedEditor(ScriptDocument doc, boolean save) {
        if (expandedField == null) return;
        if (save) {
            applyFieldLive(expandedField, expandedInput.value());
            syncLiveIfNeeded(doc);
        } else {
            applyFieldLive(expandedField, expandedBeforeVal);
        }
        expandedField = null;
    }

    // =========================================================================
    // 10. ДЕЙСТВИЯ КНОПОК, СОЗДАНИЕ ЭЛЕМЕНТОВ И ОБНОВЛЕНИЕ ПОЛЕЙ ИНСПЕКТОРА
    // =========================================================================

    private static void handleActionId(ScriptDocument doc, String id) {
        handleActionId(doc, id, -1, -1);
    }

    private static void handleActionId(ScriptDocument doc, String id, int clickX, int clickY) {
        ParsedScreenBlock scr = activeScreen();
        GuiNode sel = selectedNode();

        if (id.startsWith("expand:")) {
            String f = id.substring("expand:".length());
            focusedField = null;
            expandedField = f;
            expandedBeforeVal = readFieldString(scr, sel, f);
            pushUndo();
            expandedInput.setValue(expandedBeforeVal);
            return;
        }
        if (id.startsWith("field:")) {
            String f = id.substring("field:".length());
            focusedField = f;
            fieldUndoPushed = false;
            activeField.setValue(readFieldString(scr, sel, f));
            if (clickX >= 0 && clickY >= 0) {
                activeField.mouseClicked(Minecraft.getInstance().font, clickX, clickY, 0);
            }
            return;
        }
        if (id.startsWith("add:")) {
            addNewNode(doc, id.substring("add:".length()));
            return;
        }
        if (id.startsWith("lfilt:")) {
            String v = id.substring("lfilt:".length());
            layerFilter = "all".equals(v) ? null : parseIntSafe(v, 0);
            return;
        }
        if (id.startsWith("ctarg:")) {
            colorTarget = parseIntSafe(id.substring("ctarg:".length()), 0);
            return;
        }
        if (id.startsWith("swatch:") && sel != null) {
            String[] parts = id.split(":");
            if (parts.length == 3) {
                pushUndo();
                int col = Integer.parseInt(parts[2]);
                applyColorField(sel, parts[1], col);
                syncLiveIfNeeded(doc);
            }
            return;
        }
        if (id.startsWith("anchor:") && sel != null) {
            Anchor nextAnc = Anchor.byName(id.substring("anchor:".length()));
            if (nextAnc != null && nextAnc != sel.anchor) {
                pushUndo();
                if (Screen.hasShiftDown()) {
                    sel.anchor = nextAnc;
                    sel.x = 0;
                    sel.y = 0;
                } else {
                    // Пересчитываем x, y так, чтобы элемент остался на том же пикселе экрана
                    Font font = Minecraft.getInstance().font;
                    int[] b = nodeBounds(font, sel, virtualW, virtualH);
                    int baseX = nextAnc.resolveX(virtualW, b[2], 0);
                    int baseY = nextAnc.resolveY(virtualH, b[3], 0);
                    sel.anchor = nextAnc;
                    sel.x = b[0] - baseX;
                    sel.y = b[1] - baseY;
                }
                syncLiveIfNeeded(doc);
            }
            return;
        }
        if (id.startsWith("step:") && sel != null) {
            String[] parts = id.split(":");
            if (parts.length == 3) {
                pushUndo();
                int delta = parseIntSafe(parts[2], 0);
                if (Screen.hasShiftDown()) delta *= 5;
                switch (parts[1]) {
                    case "x" -> sel.x += delta;
                    case "y" -> sel.y += delta;
                    case "w" -> sel.w = Math.max(1, sel.w + delta);
                    case "h" -> sel.h = Math.max(1, sel.h + delta);
                    case "layer" -> sel.layer += delta;
                    case "boxRadius" -> sel.boxRadius = Math.max(0, Math.min(32, sel.boxRadius + delta));
                    case "boxBorderW" -> sel.boxBorderW = Math.max(0, Math.min(16, sel.boxBorderW + delta));
                    case "texW" -> sel.texW = Math.max(0, sel.texW + delta);
                    case "texH" -> sel.texH = Math.max(0, sel.texH + delta);
                    case "frames" -> sel.frames = Math.max(1, sel.frames + delta);
                    case "fps" -> sel.fps = Math.max(1, Math.min(60, sel.fps + delta));
                    case "hoverCircle" -> sel.hoverCircle = Math.max(0, sel.hoverCircle + delta);
                    case "rotation" -> sel.rotation += delta * 5;
                    case "slotCount" -> sel.slotCount = Math.max(1, Math.min(64, sel.slotCount + delta));
                }
                syncLiveIfNeeded(doc);
            }
            return;
        }
        if (id.startsWith("tog:") && sel != null) {
            pushUndo();
            switch (id.substring("tog:".length())) {
                case "shadow" -> sel.shadow = !sel.shadow;
                case "blur" -> sel.boxBlur = !sel.boxBlur;
                case "boxHoriz" -> sel.boxHoriz = !sel.boxHoriz;
                case "followCursor" -> sel.followCursor = !sel.followCursor;
                case "slotFrame" -> sel.slotFrame = !sel.slotFrame;
                case "toggleChecked" -> sel.toggleChecked = !sel.toggleChecked;
                case "listHoriz" -> sel.listHoriz = !sel.listHoriz;
                case "listSelect" -> sel.listSelect = !sel.listSelect;
                case "listScroll" -> sel.listNoScrollbar = !sel.listNoScrollbar;
                case "keepOpen" -> sel.keepOpen = !sel.keepOpen;
                case "scriptHidden" -> sel.scriptHidden = !sel.scriptHidden;
            }
            syncLiveIfNeeded(doc);
            return;
        }
        if (id.startsWith("cycle:") && sel != null) {
            pushUndo();
            String what = id.substring("cycle:".length());
            if ("barDir".equals(what)) {
                BarDirection[] dirs = BarDirection.values();
                sel.barDir = dirs[(sel.barDir.ordinal() + 1) % dirs.length];
            } else if ("slotContent".equals(what)) {
                SlotElement.Content[] cs = SlotElement.Content.values();
                sel.slotContent = cs[(sel.slotContent.ordinal() + 1) % cs.length];
            }
            syncLiveIfNeeded(doc);
            return;
        }

        switch (id) {
            case "scr:prev" -> {
                if (!screens.isEmpty()) {
                    activeScreenIdx = (activeScreenIdx - 1 + screens.size()) % screens.size();
                    selectedNodeIdx = -1;
                    layerFilter = null;
                    undoStack.clear();
                    redoStack.clear();
                }
            }
            case "scr:next" -> {
                if (!screens.isEmpty()) {
                    activeScreenIdx = (activeScreenIdx + 1) % screens.size();
                    selectedNodeIdx = -1;
                    layerFilter = null;
                    undoStack.clear();
                    redoStack.clear();
                }
            }
            case "scr:drop" -> screenDropdownOpen = !screenDropdownOpen;
            case "scr:new" -> {
                ParsedScreenBlock created = createDefaultScreen(doc);
                created.title = "Меню " + (screens.size() + 1);
                screens.add(created);
                activeScreenIdx = screens.size() - 1;
                selectedNodeIdx = 0;
                layerFilter = null;
                undoStack.clear();
                redoStack.clear();
            }
            case "opt:grid" -> gridSize = switch (gridSize) {
                case 0 -> 2;
                case 2 -> 4;
                case 4 -> 8;
                case 8 -> 16;
                default -> 0;
            };
            case "opt:snap" -> snapToGrid = !snapToGrid;
            case "opt:hidden" -> showScriptHidden = !showScriptHidden;
            case "opt:reset_zoom" -> {
                canvasZoom = 1.0f;
                panX = 0f;
                panY = 0f;
            }
            case "dock:palette" -> {
                allPanelsHiddenTab = false;
                paletteDock.open = !paletteDock.open;
                if (paletteDock.open) paletteDock.collapsed = false;
            }
            case "dock:layers" -> {
                allPanelsHiddenTab = false;
                layersDock.open = !layersDock.open;
                if (layersDock.open) layersDock.collapsed = false;
            }
            case "dock:inspector" -> {
                allPanelsHiddenTab = false;
                inspectorDock.open = !inspectorDock.open;
                if (inspectorDock.open) inspectorDock.collapsed = false;
            }
            case "dock_col:palette" -> paletteDock.collapsed = !paletteDock.collapsed;
            case "dock_col:layers" -> layersDock.collapsed = !layersDock.collapsed;
            case "dock_col:inspector" -> inspectorDock.collapsed = !inspectorDock.collapsed;
            case "dock_close:palette" -> paletteDock.open = false;
            case "dock_close:layers" -> layersDock.open = false;
            case "dock_close:inspector" -> inspectorDock.open = false;
            case "node:dup" -> duplicateSelected(doc);
            case "node:del" -> deleteSelected(doc);
            case "edit:undo" -> undo(doc);
            case "edit:redo" -> redo(doc);
            case "layer:inc" -> {
                if (sel != null) {
                    pushUndo();
                    sel.layer++;
                    syncLiveIfNeeded(doc);
                }
            }
            case "layer:dec" -> {
                if (sel != null) {
                    pushUndo();
                    sel.layer--;
                    syncLiveIfNeeded(doc);
                }
            }
            case "layer:vis_all" -> {
                if (scr != null) {
                    boolean anyHidden = false;
                    for (GuiNode n : scr.nodes) if (!n.editorVisible) anyHidden = true;
                    for (GuiNode n : scr.nodes) n.editorVisible = anyHidden;
                }
            }
            case "layer:lock_sel" -> {
                if (sel != null) sel.editorLocked = !sel.editorLocked;
            }
            case "act:apply" -> applyToDocument(doc);
            case "act:copy" -> {
                if (scr != null) {
                    String code = String.join("\n", generateScreenCodeLines(scr));
                    Minecraft.getInstance().keyboardHandler.setClipboard(code);
                    ClientToasts.push(tr("&aКод экрана скопирован в буфер обмена", "&aScreen code copied to clipboard"));
                }
            }
            case "act:jump" -> {
                if (doc != null && scr != null) {
                    StudioSession.guiEditorMode = false;
                    if (scr.headerLine >= 0 && scr.headerLine < doc.lines.size()) {
                        doc.move(scr.headerLine, 0, false);
                        doc.followCaret = true;
                    }
                }
            }
        }
    }

    private static void addNewNode(ScriptDocument doc, String kind) {
        ParsedScreenBlock scr = activeScreen();
        if (scr == null) return;
        pushUndo();
        GuiNode n = new GuiNode();
        n.kind = kind;
        int topLayer = 0;
        for (GuiNode existing : scr.nodes) topLayer = Math.max(topLayer, existing.layer);
        n.layer = layerFilter != null ? layerFilter : Math.max(1, topLayer);
        n.id = kind + "_" + (scr.nodes.size() + 1);
        switch (kind) {
            case "box" -> {
                n.w = 120;
                n.h = 70;
                n.color = 0xE60D1322;
                n.colorSet = true;
                n.boxGrad = 0xE6162034;
                n.boxBorder = 0xFF38BDF8;
                n.boxRadius = 5;
                n.shadow = true;
                n.boxBlur = true;
            }
            case "text" -> {
                n.text = "&eНовый текст";
                n.w = 0;
                n.h = 10;
                n.shadow = true;
            }
            case "button" -> {
                n.text = "Кнопка";
                n.w = 90;
                n.h = 20;
                n.runAction = "close";
            }
            case "image" -> {
                n.text = "minecraft:textures/item/diamond.png";
                n.w = 24;
                n.h = 24;
                n.texW = 16;
                n.texH = 16;
            }
            case "bar" -> {
                n.w = 100;
                n.h = 10;
                n.color = 0xFF22C55E;
                n.barBgColor = 0xAA000000;
                n.barValue = "75";
                n.barMax = "100";
                n.text = "%value% / %max%";
                n.shadow = true;
            }
            case "slot" -> {
                n.text = "diamond";
                n.w = 18;
                n.h = 18;
                n.slotCount = 1;
                n.slotFrame = true;
            }
            case "input" -> {
                n.w = 130;
                n.h = 20;
                n.placeholder = "Введите текст...";
            }
            case "slider" -> {
                n.text = "Громкость";
                n.w = 140;
                n.h = 20;
            }
            case "toggle" -> {
                n.text = "Включить";
                n.w = 120;
                n.h = 20;
                n.toggleChecked = true;
            }
            case "list" -> {
                n.w = 130;
                n.h = 110;
                n.listHeader = "&6Список";
                n.listSelect = true;
                n.listPreviewRows.add(new ListElement.Row("&fПункт 1", ""));
                n.listPreviewRows.add(new ListElement.Row("&fПункт 2", ""));
            }
        }
        scr.nodes.add(n);
        selectedNodeIdx = scr.nodes.size() - 1;
        syncLiveIfNeeded(doc);
    }

    private static void duplicateSelected(ScriptDocument doc) {
        ParsedScreenBlock scr = activeScreen();
        GuiNode sel = selectedNode();
        if (scr == null || sel == null) return;
        pushUndo();
        GuiNode copy = sel.copy();
        copy.x += 8;
        copy.y += 8;
        if (!copy.id.isEmpty()) copy.id = copy.id + "_copy";
        scr.nodes.add(selectedNodeIdx + 1, copy);
        selectedNodeIdx = selectedNodeIdx + 1;
        syncLiveIfNeeded(doc);
    }

    private static void deleteSelected(ScriptDocument doc) {
        ParsedScreenBlock scr = activeScreen();
        if (scr == null || selectedNodeIdx < 0 || selectedNodeIdx >= scr.nodes.size()) return;
        pushUndo();
        scr.nodes.remove(selectedNodeIdx);
        selectedNodeIdx = Math.min(selectedNodeIdx, scr.nodes.size() - 1);
        syncLiveIfNeeded(doc);
    }

    private static String readFieldString(ParsedScreenBlock scr, GuiNode n, String field) {
        if ("scr_title".equals(field)) return scr == null ? "" : scr.title;
        if ("scr_target".equals(field)) return scr == null ? "" : scr.targetClause;
        if (n == null) return "";
        return switch (field) {
            case "id" -> n.id;
            case "layer" -> Integer.toString(n.layer);
            case "x" -> Integer.toString(n.x);
            case "y" -> Integer.toString(n.y);
            case "w" -> Integer.toString(n.w);
            case "h" -> Integer.toString(n.h);
            case "text" -> n.text;
            case "color" -> formatHexColor(n.color, true);
            case "boxGrad" -> n.boxGrad == 0 ? "#00000000" : formatHexColor(n.boxGrad, true);
            case "boxBorder" -> n.boxBorder == 0 ? "#00000000" : formatHexColor(n.boxBorder, true);
            case "boxRadius" -> Integer.toString(n.boxRadius);
            case "boxBorderW" -> Integer.toString(n.boxBorderW);
            case "scale" -> fmtNum(n.scale);
            case "texW" -> Integer.toString(n.texW);
            case "texH" -> Integer.toString(n.texH);
            case "frames" -> Integer.toString(n.frames);
            case "fps" -> Integer.toString(n.fps);
            case "hoverCircle" -> Integer.toString(n.hoverCircle);
            case "rotation" -> Integer.toString((int) Math.round(n.rotation));
            case "barValue" -> n.barValue;
            case "barMax" -> n.barMax;
            case "barBgColor" -> formatHexColor(n.barBgColor, true);
            case "slotCount" -> Integer.toString(n.slotCount);
            case "inputValue" -> n.inputValue;
            case "placeholder" -> n.placeholder;
            case "sliderMin" -> fmtNum(n.sliderMin);
            case "sliderMax" -> fmtNum(n.sliderMax);
            case "sliderVal" -> fmtNum(n.sliderVal);
            case "sliderStep" -> fmtNum(n.sliderStep);
            case "listHeader" -> n.listHeader;
            case "listFrom" -> n.listFrom;
            case "runAction" -> n.runAction;
            case "tooltip" -> n.tooltip;
            case "hotkey" -> n.hotkey;
            default -> "";
        };
    }

    private static void commitFocusedField(ScriptDocument doc) {
        if (focusedField != null) {
            applyFieldLive(focusedField, activeField.value());
            focusedField = null;
            fieldUndoPushed = false;
            syncLiveIfNeeded(doc);
        }
    }

    private static void applyFieldLive(String field, String raw) {
        dirtyOnCanvas = true;
        ParsedScreenBlock scr = activeScreen();
        if ("scr_title".equals(field)) {
            if (scr != null) scr.title = raw;
            return;
        }
        if ("scr_target".equals(field)) {
            if (scr != null) scr.targetClause = raw;
            return;
        }
        GuiNode n = selectedNode();
        if (n == null) return;
        switch (field) {
            case "id" -> n.id = raw.strip().replace(' ', '_');
            case "layer" -> n.layer = parseIntSafe(raw, n.layer);
            case "x" -> n.x = parseIntSafe(raw, n.x);
            case "y" -> n.y = parseIntSafe(raw, n.y);
            case "w" -> n.w = Math.max(1, parseIntSafe(raw, n.w));
            case "h" -> n.h = Math.max(1, parseIntSafe(raw, n.h));
            case "text" -> n.text = raw;
            case "color", "boxGrad", "boxBorder", "barBgColor" -> {
                if (raw.matches("#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})")) {
                    String hex = raw.startsWith("#") ? raw : "#" + raw;
                    applyColorField(n, field, parseColorSafe(hex, 0));
                }
            }
            case "boxRadius" -> n.boxRadius = Math.max(0, Math.min(32, parseIntSafe(raw, n.boxRadius)));
            case "boxBorderW" -> n.boxBorderW = Math.max(0, Math.min(16, parseIntSafe(raw, n.boxBorderW)));
            case "scale" -> n.scale = Math.max(0.1, Math.min(10.0, parseDoubleSafe(raw, n.scale)));
            case "texW" -> n.texW = Math.max(0, parseIntSafe(raw, n.texW));
            case "texH" -> n.texH = Math.max(0, parseIntSafe(raw, n.texH));
            case "frames" -> n.frames = Math.max(1, parseIntSafe(raw, n.frames));
            case "fps" -> n.fps = Math.max(1, Math.min(60, parseIntSafe(raw, n.fps)));
            case "hoverCircle" -> n.hoverCircle = Math.max(0, parseIntSafe(raw, n.hoverCircle));
            case "rotation" -> n.rotation = parseDoubleSafe(raw, n.rotation);
            case "barValue" -> n.barValue = raw.strip();
            case "barMax" -> n.barMax = raw.strip();
            case "slotCount" -> n.slotCount = Math.max(1, Math.min(64, parseIntSafe(raw, n.slotCount)));
            case "inputValue" -> n.inputValue = raw;
            case "placeholder" -> n.placeholder = raw;
            case "sliderMin" -> n.sliderMin = parseDoubleSafe(raw, n.sliderMin);
            case "sliderMax" -> n.sliderMax = parseDoubleSafe(raw, n.sliderMax);
            case "sliderVal" -> n.sliderVal = parseDoubleSafe(raw, n.sliderVal);
            case "sliderStep" -> n.sliderStep = Math.max(0.01, parseDoubleSafe(raw, n.sliderStep));
            case "listHeader" -> n.listHeader = raw;
            case "listFrom" -> n.listFrom = raw.strip().replace("{", "").replace("}", "");
            case "runAction" -> {
                n.runAction = raw.strip();
                n.isFunctionCall = !n.runAction.startsWith("/") && n.runAction.contains("(") && n.runAction.endsWith(")");
            }
            case "tooltip" -> n.tooltip = raw;
            case "hotkey" -> n.hotkey = raw.strip().toLowerCase(Locale.ROOT);
        }
    }

    private static void applyColorField(GuiNode n, String field, int col) {
        switch (field) {
            case "color" -> {
                n.color = col;
                n.colorSet = true;
            }
            case "boxGrad" -> n.boxGrad = col;
            case "boxBorder" -> n.boxBorder = col;
            case "barBgColor" -> n.barBgColor = col;
        }
    }

    // =========================================================================
    // 11. ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ UI И ГЕОМЕТРИИ
    // =========================================================================

    private static void layoutDocks(int areaX, int areaY, int areaW, int areaH) {
        if (!paletteDock.placed) {
            paletteDock.x = areaX + 6;
            paletteDock.y = areaY + 6;
            paletteDock.placed = true;
        }
        if (!layersDock.placed) {
            layersDock.x = areaX + 6;
            layersDock.y = areaY + paletteDock.h + 12;
            layersDock.placed = true;
        }
        if (!inspectorDock.placed) {
            inspectorDock.x = areaX + areaW - inspectorDock.w - 6;
            inspectorDock.y = areaY + 6;
            inspectorDock.placed = true;
        }
        clampDock(paletteDock, areaX, areaY, areaW, areaH);
        clampDock(layersDock, areaX, areaY, areaW, areaH);
        clampDock(inspectorDock, areaX, areaY, areaW, areaH);
    }

    private static void clampDock(FloatingDock d, int ax, int ay, int aw, int ah) {
        d.h = Math.min(d.h, Math.max(90, ah - 8));
        int ch = d.currentH();
        d.x = Math.max(ax + 2, Math.min(d.x, ax + aw - Math.min(d.w, 60) - 2));
        d.y = Math.max(ay + 2, Math.min(d.y, ay + ah - ch - 2));
    }

    private static void drawDockFrame(GuiGraphics g, Font font, FloatingDock dock, String title,
                                      int mx, int my, List<HitBtn> hits) {
        int x = dock.x, y = dock.y, w = dock.w, h = dock.currentH();
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x66000000);
        g.fill(x, y, x + w, y + h, PANEL_BG);
        g.fill(x + 1, y + 1, x + w - 1, y + 18, PANEL_HDR);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + 2, 0xCCF0B85B, 0xAA58A6FF);
        border(g, x, y, w, h, EDGE_HOT);
        if (!dock.collapsed) {
            g.fill(x + 1, y + 18, x + w - 1, y + 19, EDGE);
        }
        g.drawString(font, trimWidth(font, title, w - 36), x + 6, y + 5, GOLD, false);

        // Кнопка сворачивания [– / +] и закрытия [×]
        int colX = x + w - 28;
        int clsX = x + w - 14;
        boolean colHot = hover(mx, my, colX, y + 2, 12, 14);
        boolean clsHot = hover(mx, my, clsX, y + 2, 12, 14);
        if (colHot) g.fill(colX, y + 3, colX + 12, y + 15, ROW_HOT);
        if (clsHot) g.fill(clsX, y + 3, clsX + 12, y + 15, 0x55E56B6F);
        g.drawString(font, dock.collapsed ? "+" : "–", colX + 3, y + 5, colHot ? GOLD : MUTED, false);
        g.drawString(font, "×", clsX + 3, y + 5, clsHot ? RED : MUTED, false);
        hits.add(new HitBtn(colX, y + 2, 12, 14, "dock_col:" + dock.id,
                tr("Свернуть / развернуть панель", "Collapse / expand panel")));
        hits.add(new HitBtn(clsX, y + 2, 12, 14, "dock_close:" + dock.id,
                tr("Скрыть панель (включается обратно сверху)", "Hide panel")));
    }

    private static int addTopBtn(GuiGraphics g, Font font, int mx, int my,
                                 int x, int y, int w, int h, String label, String id, String tip, boolean active) {
        boolean hot = hover(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, active ? ROW_SEL : (hot ? ROW_HOT : ROW_BG));
        border(g, x, y, w, h, active ? GOLD : (hot ? ACCENT : EDGE));
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 4, active ? GOLD : (hot ? TEXT : MUTED), false);
        if (hot && tip != null) {
            hoverTip = tip;
            hoverTipX = mx + 6;
            hoverTipY = my + 18;
        }
        topHits.add(new HitBtn(x, y, w, h, id, tip));
        return x + w;
    }

    private static void drawActionBtn(GuiGraphics g, Font font, int mx, int my,
                                      int x, int y, int w, int h, String label, String id, String tip, int accentCol) {
        boolean hot = hover(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hot ? 0xFF1F4E38 : 0xFF173A2B);
        border(g, x, y, w, h, accentCol);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 4, accentCol, false);
        if (hot && tip != null) {
            hoverTip = tip;
            hoverTipX = mx - font.width(tip) - 8;
            hoverTipY = my + 18;
        }
        topHits.add(new HitBtn(x, y, w, h, id, tip));
    }

    private static void drawDockBtn(GuiGraphics g, Font font, int mx, int my,
                                    int x, int y, int w, int h, String label, String id, String tip,
                                    boolean active, List<HitBtn> hits) {
        boolean hot = hover(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, active ? ROW_SEL : (hot ? ROW_HOT : ROW_BG));
        border(g, x, y, w, h, active ? GOLD : (hot ? ACCENT : EDGE));
        String shown = trimWidth(font, label, w - 4);
        g.drawString(font, shown, x + (w - font.width(shown)) / 2, y + (h - 8) / 2,
                active ? GOLD : (hot ? TEXT : MUTED), false);
        if (hot && tip != null) {
            hoverTip = tip;
            hoverTipX = mx + 8;
            hoverTipY = my + 16;
        }
        hits.add(new HitBtn(x, y, w, h, id, tip));
    }

    private static DragMode hitResizeHandle(int vmx, int vmy, int bx, int by, int bw, int bh) {
        int r = 4;
        if (Math.abs(vmx - bx) <= r && Math.abs(vmy - by) <= r) return DragMode.NODE_NW;
        if (Math.abs(vmx - (bx + bw)) <= r && Math.abs(vmy - by) <= r) return DragMode.NODE_NE;
        if (Math.abs(vmx - (bx + bw)) <= r && Math.abs(vmy - (by + bh)) <= r) return DragMode.NODE_SE;
        if (Math.abs(vmx - bx) <= r && Math.abs(vmy - (by + bh)) <= r) return DragMode.NODE_SW;
        if (Math.abs(vmx - (bx + bw / 2)) <= r && Math.abs(vmy - by) <= r) return DragMode.NODE_N;
        if (Math.abs(vmx - (bx + bw / 2)) <= r && Math.abs(vmy - (by + bh)) <= r) return DragMode.NODE_S;
        if (Math.abs(vmx - (bx + bw)) <= r && Math.abs(vmy - (by + bh / 2)) <= r) return DragMode.NODE_E;
        if (Math.abs(vmx - bx) <= r && Math.abs(vmy - (by + bh / 2)) <= r) return DragMode.NODE_W;
        return DragMode.NONE;
    }

    private static List<Integer> getSortedDrawOrder(ParsedScreenBlock scr) {
        List<Integer> order = new ArrayList<>(scr.nodes.size());
        for (int i = 0; i < scr.nodes.size(); i++) order.add(i);
        order.sort(Comparator.comparingInt(i -> scr.nodes.get(i).layer));
        return order;
    }

    private static int snap(int val, int step) {
        if (step <= 1) return val;
        return Math.round(val / (float) step) * step;
    }

    private static String expandOptions(String text) {
        if (fileOptions.isEmpty() || !text.contains("{@")) return text;
        Matcher m = OPTION_REF.matcher(text);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(text, last, m.start());
            String rep = fileOptions.get(m.group(1).toLowerCase(Locale.ROOT));
            sb.append(rep != null ? rep : m.group());
            last = m.end();
        }
        sb.append(text.substring(last));
        return sb.toString();
    }

    private static int indentOf(String raw) {
        int ind = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ') ind++;
            else if (c == '\t') ind += 4;
            else break;
        }
        return ind;
    }

    private static String leadingWhitespace(String raw) {
        int i = 0;
        while (i < raw.length() && (raw.charAt(i) == ' ' || raw.charAt(i) == '\t')) i++;
        return raw.substring(0, i);
    }

    private static String word(List<Token> t, int i) {
        return i < t.size() && !t.get(i).quoted() ? t.get(i).value().toLowerCase(Locale.ROOT) : "";
    }

    private static int parseIntSafe(String raw, int def) {
        if (raw == null) return def;
        try {
            return Integer.parseInt(raw.replace(",", "").strip());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double parseDoubleSafe(String raw, double def) {
        if (raw == null) return def;
        try {
            double d = Double.parseDouble(raw.replace(",", ".").strip());
            return Double.isFinite(d) ? d : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int parseColorSafe(String raw, int def) {
        if (raw == null) return def;
        String s = raw.strip();
        if (s.startsWith("#")) s = s.substring(1);
        if (!s.matches("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}")) return def;
        long v = Long.parseLong(s, 16);
        if (s.length() == 6) v |= 0xFF000000L;
        return (int) v;
    }

    private static String formatHexColor(int argb, boolean includeAlpha) {
        int a = (argb >>> 24) & 0xFF;
        if (!includeAlpha && (a == 0xFF || a == 0)) {
            return String.format(Locale.ROOT, "#%06X", argb & 0x00FFFFFF);
        }
        return String.format(Locale.ROOT, "#%08X", argb);
    }

    private static String fmtNum(double d) {
        if (Math.abs(d - Math.rint(d)) < 1e-6) {
            return Long.toString(Math.round(d));
        }
        return String.format(Locale.ROOT, "%.2f", d).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String escapeQuotes(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }

    private static String colorize(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.replaceAll("&([0-9a-fk-orA-FK-OR])", "\u00a7$1");
    }

    private static String trimWidth(Font font, String text, int maxW) {
        if (text == null) return "";
        if (font.width(text) <= maxW) return text;
        return font.plainSubstrByWidth(text, Math.max(6, maxW - 6)) + "…";
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
