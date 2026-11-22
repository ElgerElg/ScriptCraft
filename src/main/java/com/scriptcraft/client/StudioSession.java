package com.scriptcraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.scriptcraft.ScriptCraft;
import com.scriptcraft.lang.Messages;
import com.scriptcraft.network.ScriptDataPacket;
import com.scriptcraft.network.StudioExplorerPacket;
import com.scriptcraft.network.StudioLivePacket;
import com.scriptcraft.script.ScriptLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Окно студии живёт, пока игрок на сервере: вкладки, размер и какая страница открыта.
 * Выход из мира всё сбрасывает, следующий заход снова начинается с главной.
 */
@OnlyIn(Dist.CLIENT)
public final class StudioSession {
    public static final String HOME = "home";
    public static final String JOURNAL = "journal";
    public static final String PROFILER = "profiler";

    public static boolean maximized = true;
    public static boolean minimized;
    public static boolean placed;
    public static int x;
    public static int y;
    public static int w = 780;
    public static int h = 460;
    /** Настройки редактора (переключатели в плавающей панели). */
    public static boolean optSyntax = true;
    public static boolean optQuoteColors = true;
    public static boolean optAutoIndent = true;
    public static boolean optLineNumbers = true;
    public static boolean optActiveLine = true;
    public static boolean optTexturePreview = true;
    public static boolean optMatchCase = false;
    public static boolean optWholeWord = false;
    /** Плавающая панель «Найти и заменить / Синтаксис»: позиция и размер. */
    public static boolean toolPanelOpen = false;
    public static boolean toolPanelPlaced = false;
    public static int toolPanelX = 40;
    public static int toolPanelY = 72;
    public static int toolPanelW = 248;
    public static int toolPanelH = 224;
    public static String active = HOME;
    public static boolean opened;
    public static List<String> catalog = List.of();
    public static List<ScriptLog.Issue> issues = List.of();
    public static List<ScriptLog.Slice> slices = List.of();
    /** Строки под активными ошибками: перезагрузки, старые ошибки, уведомления. */
    public static final List<Note> history = new ArrayList<>();

    /** Одна строка истории. Код хранится, чтобы старую ошибку можно было открыть справа. */
    public static final class Note {
        public final String title;
        public final String body;
        public final String file;
        public final int line;
        public final int firstLine;
        public final List<String> lines;

        public Note(String title, String body, String file, int line, int firstLine, List<String> lines) {
            this.title = title;
            this.body = body;
            this.file = file == null ? "" : file;
            this.line = line;
            this.firstLine = firstLine;
            this.lines = lines == null ? List.of() : lines;
        }
    }
    public static final Map<String, ScriptDocument> docs = new LinkedHashMap<>();
    private static final Map<String, List<StudioLivePacket.Peer>> peers = new LinkedHashMap<>();
    /** Живые тексты комнат с сервера (чтобы в Журнале видеть правки других редакторов в реальном времени). */
    private static final Map<String, List<String>> liveLines = new LinkedHashMap<>();
    /** Напоминание: ресурсы в scriptcraft/resources/ были изменены и ждут перезагрузки. */
    public static boolean resourcesDirty = false;
    /** Последний открытый скрипт (чтобы при переходе в Журнал редактор не выпадал из комнаты). */
    public static String lastScriptTab;
    /** Состояние проводника scriptcraft/. */
    public static String explorerDir = "scripts";
    public static List<StudioExplorerPacket.Entry> explorerEntries = List.of();
    public static List<StudioExplorerPacket.ActiveEditor> activeEditors = List.of();
    public static final Deque<String> dirBack = new ArrayDeque<>();
    public static final Deque<String> dirForward = new ArrayDeque<>();
    public static String selectedExplorerPath;
    private static int imageSeq;
    /** Данные профайлера с сервера. */
    public static com.scriptcraft.network.ProfilerPacket.Data profilerData;
    public static int profilerSort = 0; // 0 = ms/s, 1 = avg, 2 = max, 3 = calls
    /** История версий открытого скрипта. */
    public static boolean historyOpen = false;
    /** Режим визуального конструктора экранов (GUI Builder) для активного .sc скрипта. */
    public static boolean guiEditorMode = false;
    public static String historyScript = "";
    public static int historyRetentionDays = 3;
    public static List<com.scriptcraft.network.ScriptHistoryPacket.Entry> historyEntries = List.of();
    public static String historySelectedId = "";
    public static String historyPreviewText = "";

    private StudioSession() {
    }

    public static void reset() {
        maximized = true;
        minimized = false;
        placed = false;
        active = HOME;
        opened = false;
        catalog = List.of();
        issues = List.of();
        slices = List.of();
        history.clear();
        docs.clear();
        peers.clear();
        liveLines.clear();
        resourcesDirty = false;
        lastScriptTab = null;
        explorerDir = "scripts";
        explorerEntries = List.of();
        activeEditors = List.of();
        dirBack.clear();
        dirForward.clear();
        selectedExplorerPath = null;
        profilerData = null;
        historyOpen = false;
        guiEditorMode = false;
        historyScript = "";
        historyEntries = List.of();
        historySelectedId = "";
        historyPreviewText = "";
        StudioSync.reset();
    }

    public static void journal(List<ScriptLog.Issue> nextIssues, List<ScriptLog.Slice> nextSlices) {
        for (ScriptLog.Issue old : issues) {
            boolean still = false;
            for (ScriptLog.Issue next : nextIssues) {
                if (next.file().equals(old.file()) && next.line() == old.line() && next.reason().equals(old.reason())) {
                    still = true;
                    break;
                }
            }
            if (!still) {
                ScriptLog.Slice slice = null;
                for (ScriptLog.Slice candidate : slices) {
                    if (candidate.name().equals(old.file())) {
                        slice = candidate;
                        break;
                    }
                }
                String title = old.file().isEmpty() ? old.reason() : old.file() + ":" + old.line();
                history.add(new Note(title, old.reason(), old.file(), old.line(),
                        slice == null ? 1 : slice.firstLine(),
                        slice == null ? List.of() : List.copyOf(slice.lines())));
                while (history.size() > 40) {
                    history.remove(0);
                }
            }
        }
        issues = List.copyOf(nextIssues);
        slices = List.copyOf(nextSlices);
        if (!opened) {
            opened = true;
            active = issues.isEmpty() ? HOME : JOURNAL;
        }
    }

    public static ScriptDocument openScript(String name) {
        lastScriptTab = name;
        ScriptDocument existing = docs.get(name);
        if (existing != null) {
            active = name;
            return existing;
        }
        ScriptDocument created = new ScriptDocument(name);
        docs.put(name, created);
        active = name;
        return created;
    }

    public static void acceptText(String name, String text, long revision, int flags) {
        ScriptDocument doc = docs.get(name);
        if (doc == null) {
            return;
        }
        boolean live = (flags & ScriptDataPacket.LIVE) != 0;
        boolean force = (flags & ScriptDataPacket.FORCE) != 0;
        boolean disk = (flags & ScriptDataPacket.DISK) != 0;
        boolean stale = (flags & ScriptDataPacket.STALE) != 0;
        doc.live = live;
        if (!force && doc.loaded && doc.dirty()) {
            return;
        }
        boolean changed = !doc.loaded || !text.equals(doc.text());
        if (changed) {
            doc.replaceFromServer(text, disk);
            if (stale) {
                ClientToasts.push(Messages.toast("studio.stale"));
            }
        } else if (disk) {
            doc.saved = doc.text();
        }
        doc.revision = revision;
        doc.live = live;
        StudioSync.arm(doc);
    }

    public static void setPeers(String file, List<StudioLivePacket.Peer> list) {
        if (list == null || list.isEmpty()) {
            peers.remove(file);
            return;
        }
        peers.put(file, List.copyOf(list));
    }

    public static List<StudioLivePacket.Peer> peers(String file) {
        return peers.getOrDefault(file, List.of());
    }

    public static void clearPeers() {
        peers.clear();
        liveLines.clear();
    }

    public static void setLiveText(String file, String text) {
        if (file == null || file.isEmpty() || text == null) {
            return;
        }
        String norm = text.replace("\r\n", "\n").replace('\r', '\n');
        liveLines.put(file, List.of(norm.split("\n", -1)));
    }

    public static List<String> liveLines(String file) {
        ScriptDocument doc = docs.get(file);
        if (doc != null && doc.loaded && !doc.lines.isEmpty()) {
            return doc.lines;
        }
        return liveLines.get(file);
    }

    public static void catalog(List<String> names) {
        catalog = new ArrayList<>(names);
    }

    public static void acceptExplorerDir(StudioExplorerPacket.DirView view) {
        explorerDir = view.dir() == null ? "" : view.dir();
        explorerEntries = List.copyOf(view.entries());
        activeEditors = List.copyOf(view.editors());
    }

    public static ScriptDocument openGenericFile(String relPath) {
        String key = "@" + relPath;
        ScriptDocument existing = docs.get(key);
        if (existing != null) {
            active = key;
            return existing;
        }
        ScriptDocument created = new ScriptDocument(key);
        created.genericFile = true;
        docs.put(key, created);
        active = key;
        return created;
    }

    public static void acceptExplorerFile(StudioExplorerPacket.FileView view) {
        if ("$res_dirty".equals(view.renamedFrom())) {
            boolean nextDirty = "1".equals(view.path());
            boolean becameDirty = nextDirty && !resourcesDirty;
            resourcesDirty = nextDirty;
            if (becameDirty) {
                ClientToasts.push(Messages.toast("studio.res_changed_hint"));
            }
            return;
        }
        // Уведомление о переименовании / включении-выключении / удалении файла
        if (view.renamedFrom() != null && !view.renamedFrom().isEmpty()) {
            handleRemoteRename(view.renamedFrom(), view.path());
            return;
        }
        String key = "@" + view.path();
        ScriptDocument doc = docs.get(key);
        if (doc == null) {
            return;
        }
        if (view.binary()) {
            try {
                NativeImage img = NativeImage.read(new ByteArrayInputStream(view.data()));
                DynamicTexture tex = new DynamicTexture(img);
                ResourceLocation id = new ResourceLocation(ScriptCraft.MODID, "explorer_img/" + (++imageSeq));
                Minecraft.getInstance().getTextureManager().register(id, tex);
                doc.isImage = true;
                doc.imageId = id;
                doc.imageW = img.getWidth();
                doc.imageH = img.getHeight();
                doc.loaded = true;
                doc.saved = "";
            } catch (Exception e) {
                ClientToasts.push("Image error: " + e.getMessage());
            }
            return;
        }
        String text = new String(view.data(), StandardCharsets.UTF_8);
        doc.load(text);
    }

    private static void handleRemoteRename(String oldRel, String newRel) {
        String oldKey = oldRel.startsWith("scripts/") && oldRel.endsWith(".sc")
                ? oldRel.substring("scripts/".length()) : "@" + oldRel;
        String newKey = newRel == null || newRel.isEmpty() ? null
                : (newRel.startsWith("scripts/") && newRel.endsWith(".sc")
                ? newRel.substring("scripts/".length()) : "@" + newRel);

        ScriptDocument doc = docs.get(oldKey);
        if (doc == null) {
            return;
        }
        if (newKey == null) {
            // Файл удалён: оставляем как черновик, если есть несохранённые правки, иначе закрываем
            if (!doc.dirty()) {
                docs.remove(oldKey);
                if (oldKey.equals(active)) active = HOME;
            }
            return;
        }
        // Переименовываем вкладку в реальном времени с сохранением порядка вкладок и текста
        Map<String, ScriptDocument> rebuilt = new LinkedHashMap<>();
        for (Map.Entry<String, ScriptDocument> entry : docs.entrySet()) {
            if (entry.getKey().equals(oldKey)) {
                doc.name = newKey;
                rebuilt.put(newKey, doc);
            } else {
                rebuilt.put(entry.getKey(), entry.getValue());
            }
        }
        docs.clear();
        docs.putAll(rebuilt);
        if (oldKey.equals(active)) {
            active = newKey;
        }
        List<StudioLivePacket.Peer> oldPeers = peers.remove(oldKey);
        if (oldPeers != null) {
            peers.put(newKey, oldPeers);
        }
    }

    /** Одна строка истории. Повтор подряд не копится. */
    public static void remember(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String plain = text.replaceAll("(?i)§.", "").replaceAll("(?i)&[0-9a-fk-or]", "").strip();
        if (plain.isEmpty()) {
            return;
        }
        if (!history.isEmpty() && history.get(history.size() - 1).body.equals(plain)) {
            return;
        }
        history.add(new Note(plain, plain, "", 0, 1, List.of()));
        while (history.size() > 40) {
            history.remove(0);
        }
    }

    public static void acceptProfiler(com.scriptcraft.network.ProfilerPacket.Data data) {
        profilerData = data;
    }

    public static void acceptHistoryList(com.scriptcraft.network.ScriptHistoryPacket.ListView view) {
        historyScript = view.scriptName();
        historyRetentionDays = view.retentionDays();
        historyEntries = List.copyOf(view.entries());
        if (historyEntries.isEmpty()) {
            historySelectedId = "";
            historyPreviewText = "";
        } else {
            boolean still = false;
            for (var e : historyEntries) {
                if (e.id().equals(historySelectedId)) {
                    still = true;
                    break;
                }
            }
            if (!still) {
                historySelectedId = historyEntries.get(0).id();
                historyPreviewText = "";
                com.scriptcraft.network.ModNetwork.sendToServer(
                        new com.scriptcraft.network.ScriptHistoryPacket.Request(
                                com.scriptcraft.network.ScriptHistoryPacket.PREVIEW, historyScript, historySelectedId));
            }
        }
    }

    public static void acceptHistoryPreview(com.scriptcraft.network.ScriptHistoryPacket.PreviewView view) {
        if (view.scriptName().equals(historyScript) && view.snapshotId().equals(historySelectedId)) {
            historyPreviewText = view.text();
        }
    }
}
