package com.scriptcraft.client;

import com.scriptcraft.lang.Messages;
import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.network.ScriptDataPacket;
import com.scriptcraft.network.ScriptPayload;
import com.scriptcraft.network.StudioLivePacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Отправляет курсор и текст открытого скрипта, когда сервер включил совместную правку.
 * Дифференциальная синхронизация (shadow + inFlight): локальный набор второго редактора
 * никогда не затирается приходящими пакетами, а сливается построчно и внутри строки.
 */
@OnlyIn(Dist.CLIENT)
public final class StudioSync {
    private static String watching;
    /** Последняя подтверждённая общая база с сервером (shadow). */
    private static String shadow = "";
    /** Текст пакета, который сейчас летит на сервер и ждёт ack (null — ничего не летит). */
    private static String inFlight;
    private static String lastSent = "";
    private static int lastLine;
    private static int lastCol;
    private static int lastAnchorLine = -1;
    private static int lastAnchorCol;
    private static boolean lastBox;
    private static int lastColor;
    private static boolean announced;
    private static long lastSend;
    private static long inFlightSince;

    private StudioSync() {
    }

    public static void reset() {
        watching = null;
        shadow = "";
        inFlight = null;
        lastSent = "";
        lastLine = 0;
        lastCol = 0;
        lastAnchorLine = -1;
        lastAnchorCol = 0;
        lastBox = false;
        announced = false;
        inFlightSince = 0;
    }

    public static void watch(ScriptDocument doc) {
        if (doc == null || !doc.loaded || doc.genericFile || doc.isImage) {
            leave();
            return;
        }
        if (!doc.name.equals(watching)) {
            leave();
            watching = doc.name;
            shadow = doc.text();
            inFlight = null;
            lastSent = shadow;
            remember(doc);
            announced = false;
        }
        if (!doc.live) {
            return;
        }
        flush(doc);
    }

    public static void leave() {
        if (watching == null) {
            return;
        }
        ScriptDocument doc = StudioSession.docs.get(watching);
        String name = watching;
        boolean tell = announced || (doc != null && doc.live);
        watching = null;
        announced = false;
        if (!tell || !online()) {
            return;
        }
        if (doc != null && doc.live && doc.loaded && !doc.text().equals(lastSent)) {
            send(doc, true);
            lastSent = doc.text();
        }
        ModNetwork.sendToServer(new StudioLivePacket.Edit(name, 0, -1, 0, -1, 0, false, false, "", 0));
    }

    public static void leaveFile(String name) {
        if (name == null) {
            return;
        }
        if (name.equals(watching)) {
            leave();
            return;
        }
        if (online()) {
            ModNetwork.sendToServer(new StudioLivePacket.Edit(name, 0, -1, 0, -1, 0, false, false, "", 0));
        }
    }

    /** Добрать несохранённый набор перед записью файла. */
    public static void flushText(ScriptDocument doc) {
        if (doc == null || !doc.live || !doc.loaded || !doc.name.equals(watching)) {
            return;
        }
        String current = doc.text();
        if (current.equals(lastSent)) {
            return;
        }
        send(doc, true);
        inFlight = current;
        inFlightSince = System.currentTimeMillis();
        lastSent = current;
        remember(doc);
        announced = true;
        lastSend = inFlightSince;
    }

    /** Текст на экране уже совпадает с тем, что сервер прислал: не отправлять его обратно. */
    public static void arm(ScriptDocument doc) {
        if (doc != null && doc.name.equals(watching)) {
            shadow = doc.text();
            inFlight = null;
            lastSent = shadow;
            remember(doc);
        }
    }

    public static void onView(StudioLivePacket.View view) {
        if (view.file().isEmpty()) {
            boolean enabled = view.revision() != 0L;
            if (!enabled) {
                StudioSession.clearPeers();
                for (ScriptDocument doc : StudioSession.docs.values()) {
                    doc.live = false;
                }
                announced = false;
            } else {
                for (ScriptDocument doc : StudioSession.docs.values()) {
                    if (!doc.loaded || doc.live) {
                        continue;
                    }
                    if (!doc.dirty() && online()) {
                        ModNetwork.sendToServer(new ScriptDataPacket.Read(doc.name));
                    } else {
                        doc.live = true;
                    }
                }
                announced = false;
            }
            return;
        }
        StudioSession.setPeers(view.file(), view.peers());
        if (view.replace()) {
            StudioSession.setLiveText(view.file(), view.text());
        }
        ScriptDocument doc = StudioSession.docs.get(view.file());
        if (doc == null) {
            return;
        }
        doc.live = true;
        boolean isWatching = doc.name.equals(watching);

        if (view.ack()) {
            String ackBase = inFlight != null ? inFlight : shadow;
            inFlight = null;
            if (view.replace()) {
                String serverText = view.text();
                String merged = isWatching ? merge3Way(ackBase, doc.text(), serverText) : serverText;
                if (!merged.equals(doc.text())) {
                    doc.replaceFromServer(merged, false);
                }
                shadow = serverText;
                if (view.stale()) {
                    ClientToasts.push(Messages.toast("studio.stale"));
                }
            } else {
                shadow = ackBase;
            }
            lastSent = shadow;
            remember(doc);
            doc.revision = view.revision();
            return;
        }

        if (view.replace()) {
            String serverText = view.text();
            String base = shadow;
            String merged = isWatching ? merge3Way(base, doc.text(), serverText) : serverText;
            if (!merged.equals(doc.text())) {
                doc.replaceFromServer(merged, false);
            }
            if (inFlight != null) {
                inFlight = merge3Way(base, inFlight, serverText);
            }
            shadow = serverText;
            lastSent = shadow;
            remember(doc);
            doc.revision = view.revision();
            return;
        }

        if (!isWatching || (inFlight == null && doc.text().equals(shadow))) {
            doc.revision = view.revision();
        }
    }

    private static void flush(ScriptDocument doc) {
        long now = System.currentTimeMillis();
        if (!announced) {
            send(doc, false);
            remember(doc);
            announced = true;
            lastSend = now;
            return;
        }
        if (inFlight != null && now - inFlightSince > 1500) {
            // Защита от потерянного ack
            shadow = inFlight;
            inFlight = null;
        }
        String current = doc.text();
        boolean textChanged = !current.equals(inFlight != null ? inFlight : shadow);
        boolean caretChanged = doc.line != lastLine || doc.col != lastCol
                || doc.anchorLine != lastAnchorLine || doc.anchorCol != lastAnchorCol || doc.box != lastBox
                || ClientUiSettings.editorColor() != lastColor;
        if (textChanged && inFlight == null) {
            if (now - lastSend < 55) {
                return;
            }
            send(doc, true);
            inFlight = current;
            inFlightSince = now;
            lastSent = current;
            remember(doc);
            lastSend = now;
            return;
        }
        if (caretChanged && inFlight == null && now - lastSend >= 40) {
            send(doc, false);
            remember(doc);
            lastSend = now;
        }
    }

    /**
     * Трёхстороннее слияние на клиенте: base (shadow), local (текущий текст в редакторе), incoming (с сервера).
     * Если оба редактора печатают в разных строках (или в разных частях одной строки), сохраняются оба набора.
     */
    private static String merge3Way(String base, String local, String incoming) {
        if (base == null || base.equals(incoming)) return local;
        if (base.equals(local)) return incoming;
        if (local.equals(incoming)) return local;

        List<String> b = Arrays.asList(base.split("\n", -1));
        List<String> c = new ArrayList<>(Arrays.asList(local.split("\n", -1)));
        List<String> inc = Arrays.asList(incoming.split("\n", -1));

        int p = 0;
        int minBI = Math.min(b.size(), inc.size());
        while (p < minBI && b.get(p).equals(inc.get(p))) p++;
        int s = 0;
        while (s < minBI - p && b.get(b.size() - 1 - s).equals(inc.get(inc.size() - 1 - s))) s++;

        int bFrom = p, bTo = b.size() - s;
        int iFrom = p, iTo = inc.size() - s;

        int[] span = locateBaseSpan(b, c, bFrom, bTo);
        if (span != null) {
            List<String> out = new ArrayList<>(c.size() + (iTo - iFrom));
            out.addAll(c.subList(0, span[0]));
            out.addAll(inc.subList(iFrom, iTo));
            out.addAll(c.subList(span[1], c.size()));
            return String.join("\n", out);
        }

        // Оба редактора изменили одну и ту же строку — сливаем посимвольно, сохраняя локальный набор
        if (bTo - bFrom == 1 && iTo - iFrom == 1) {
            int row = Math.max(0, Math.min(c.size() - 1, bFrom));
            c.set(row, mergeLine3Way(b.get(bFrom), c.get(row), inc.get(iFrom)));
            return String.join("\n", c);
        }
        return local;
    }

    private static int[] locateBaseSpan(List<String> b, List<String> c, int bFrom, int bTo) {
        int len = bTo - bFrom;
        if (len > 0) {
            List<String> needle = b.subList(bFrom, bTo);
            int best = -1;
            int bestScore = Integer.MIN_VALUE;
            for (int idx = 0; idx + len <= c.size(); idx++) {
                if (c.subList(idx, idx + len).equals(needle)) {
                    int score = -Math.abs(idx - bFrom);
                    if (bFrom > 0 && idx > 0 && c.get(idx - 1).equals(b.get(bFrom - 1))) score += 100;
                    if (bTo < b.size() && idx + len < c.size() && c.get(idx + len).equals(b.get(bTo))) score += 100;
                    if (score > bestScore) {
                        bestScore = score;
                        best = idx;
                    }
                }
            }
            if (best >= 0) {
                return new int[]{best, best + len};
            }
            return null;
        }
        // Чистая вставка строк (bFrom == bTo): ищем границу между b[bFrom-1] и b[bFrom]
        String prev = bFrom > 0 ? b.get(bFrom - 1) : null;
        String next = bFrom < b.size() ? b.get(bFrom) : null;
        int best = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int idx = 0; idx <= c.size(); idx++) {
            boolean prevOk = (prev == null && idx == 0) || (prev != null && idx > 0 && c.get(idx - 1).equals(prev));
            boolean nextOk = (next == null && idx == c.size()) || (next != null && idx < c.size() && c.get(idx).equals(next));
            if (prevOk || nextOk) {
                int score = (prevOk ? 100 : 0) + (nextOk ? 100 : 0) - Math.abs(idx - bFrom);
                if (score > bestScore) {
                    bestScore = score;
                    best = idx;
                }
            }
        }
        return best >= 0 ? new int[]{best, best} : null;
    }

    private static String mergeLine3Way(String base, String local, String inc) {
        if (base.equals(inc)) return local;
        if (base.equals(local)) return inc;
        int p = 0, minBI = Math.min(base.length(), inc.length());
        while (p < minBI && base.charAt(p) == inc.charAt(p)) p++;
        int s = 0;
        while (s < minBI - p && base.charAt(base.length() - 1 - s) == inc.charAt(inc.length() - 1 - s)) s++;
        int cp = 0, minBC = Math.min(base.length(), local.length());
        while (cp < minBC && base.charAt(cp) == local.charAt(cp)) cp++;
        int cs = 0;
        while (cs < minBC - cp && base.charAt(base.length() - 1 - cs) == local.charAt(local.length() - 1 - cs)) cs++;
        int bTo = base.length() - s;
        if (bTo <= cp) {
            return inc.substring(0, inc.length() - s) + local.substring(bTo);
        }
        if (p >= base.length() - cs) {
            int shift = local.length() - base.length();
            int at = Math.max(0, Math.min(local.length(), p + shift));
            int end = Math.max(at, Math.min(local.length(), bTo + shift));
            return local.substring(0, at) + inc.substring(p, inc.length() - s) + local.substring(end);
        }
        return local;
    }

    private static boolean online() {
        return Minecraft.getInstance().getConnection() != null;
    }

    private static void send(ScriptDocument doc, boolean text) {
        if (!online()) {
            return;
        }
        String body = text ? doc.text() : "";
        if (text && body.getBytes(StandardCharsets.UTF_8).length > ScriptPayload.MAX_BYTES) {
            return;
        }
        ModNetwork.sendToServer(new StudioLivePacket.Edit(doc.name, doc.revision, doc.line, doc.col,
                doc.anchorLine, doc.anchorCol, doc.box, text, body, ClientUiSettings.editorColor()));
    }

    private static void remember(ScriptDocument doc) {
        lastLine = doc.line;
        lastCol = doc.col;
        lastAnchorLine = doc.anchorLine;
        lastAnchorCol = doc.anchorCol;
        lastBox = doc.box;
        lastColor = ClientUiSettings.editorColor();
    }
}
