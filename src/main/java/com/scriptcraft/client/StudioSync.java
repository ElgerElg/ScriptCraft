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

/**
 * Отправляет курсор и текст открытого скрипта, когда сервер включил совместную правку.
 * Полный текст уходит не чаще, чем раз в 70 мс, и ещё раз сразу перед сохранением или уходом из файла.
 */
@OnlyIn(Dist.CLIENT)
public final class StudioSync {
    private static String watching;
    private static String lastSent = "";
    private static int lastLine;
    private static int lastCol;
    private static int lastAnchorLine = -1;
    private static int lastAnchorCol;
    private static boolean lastBox;
    private static boolean announced;
    private static long lastSend;

    private StudioSync() {
    }

    public static void reset() {
        watching = null;
        lastSent = "";
        lastLine = 0;
        lastCol = 0;
        lastAnchorLine = -1;
        lastAnchorCol = 0;
        lastBox = false;
        announced = false;
    }

    public static void watch(ScriptDocument doc) {
        if (doc == null || !doc.loaded || doc.genericFile || doc.isImage) {
            leave();
            return;
        }
        if (!doc.name.equals(watching)) {
            leave();
            watching = doc.name;
            lastSent = doc.text();
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
        ModNetwork.sendToServer(new StudioLivePacket.Edit(name, 0, -1, 0, -1, 0, false, false, ""));
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
            ModNetwork.sendToServer(new StudioLivePacket.Edit(name, 0, -1, 0, -1, 0, false, false, ""));
        }
    }

    /** Добрать несохранённый набор перед записью файла. */
    public static void flushText(ScriptDocument doc) {
        if (doc == null || !doc.live || !doc.loaded || !doc.name.equals(watching)) {
            return;
        }
        if (doc.text().equals(lastSent)) {
            return;
        }
        send(doc, true);
        lastSent = doc.text();
        remember(doc);
        announced = true;
        lastSend = System.currentTimeMillis();
    }

    /** Текст на экране уже совпадает с тем, что сервер прислал: не отправлять его обратно. */
    public static void arm(ScriptDocument doc) {
        if (doc != null && doc.name.equals(watching)) {
            lastSent = doc.text();
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
        ScriptDocument doc = StudioSession.docs.get(view.file());
        if (doc == null) {
            return;
        }
        doc.live = true;
        if (view.replace() && !view.text().equals(doc.text())) {
            doc.replaceFromServer(view.text(), false);
            if (view.stale()) {
                ClientToasts.push(Messages.toast("studio.stale"));
            }
            arm(doc);
            doc.revision = view.revision();
            return;
        }
        if (view.replace() || view.ack() || !doc.name.equals(watching) || doc.text().equals(lastSent)) {
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
        boolean textChanged = !doc.text().equals(lastSent);
        boolean caretChanged = doc.line != lastLine || doc.col != lastCol
                || doc.anchorLine != lastAnchorLine || doc.anchorCol != lastAnchorCol || doc.box != lastBox;
        if (textChanged) {
            if (now - lastSend < 70) {
                return;
            }
            send(doc, true);
            lastSent = doc.text();
            remember(doc);
            lastSend = now;
            return;
        }
        if (caretChanged && now - lastSend >= 40) {
            send(doc, false);
            remember(doc);
            lastSend = now;
        }
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
                doc.anchorLine, doc.anchorCol, doc.box, text, body));
    }

    private static void remember(ScriptDocument doc) {
        lastLine = doc.line;
        lastCol = doc.col;
        lastAnchorLine = doc.anchorLine;
        lastAnchorCol = doc.anchorCol;
        lastBox = doc.box;
    }
}
