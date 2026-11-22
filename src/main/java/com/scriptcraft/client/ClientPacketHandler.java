package com.scriptcraft.client;

import com.scriptcraft.network.StudioLivePacket;
import com.scriptcraft.screen.ScreenDescription;
import com.scriptcraft.script.ScriptLog;
import net.minecraft.client.Minecraft;

import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Клиентская обработка пакетов. Этот пакет (com.scriptcraft.client) используется ТОЛЬКО на клиенте. */
@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void openScreen(ScreenDescription description) {
        Minecraft mc = Minecraft.getInstance();
        String title = description.title();
        if (com.scriptcraft.screen.ScreenManager.CLOSE_TOKEN.equals(title)) {
            if (mc.screen instanceof ScriptScreen current) {
                current.markServerClosed();
                mc.setScreen(null);
            }
            return;
        }
        if (title.startsWith(com.scriptcraft.screen.ScreenManager.DELTA_PREFIX)) {
            if (mc.screen instanceof ScriptScreen current) {
                current.applyDelta(description.elements());
            }
            return;
        }
        if (title.startsWith(com.scriptcraft.screen.ScreenManager.PATCH_PREFIX)) {
            String realTitle = title.substring(com.scriptcraft.screen.ScreenManager.PATCH_PREFIX.length());
            ScreenDescription clean = new ScreenDescription(realTitle, description.elements());
            if (mc.screen instanceof ScriptScreen current) {
                current.applyUpdate(clean);
            } else {
                mc.setScreen(new ScriptScreen(clean));
            }
            return;
        }
        if (mc.screen instanceof ScriptScreen current) {
            if (current.titleText().equals(title)) {
                current.applyUpdate(description);
                return;
            }
            // Сервер заменяет один экран другим — не отправляем устаревший SCREEN_CLOSE для старого экрана
            current.markServerClosed();
        }
        mc.setScreen(new ScriptScreen(description));
    }

    public static void openLog(List<ScriptLog.Issue> issues, List<ScriptLog.Slice> files, boolean open) {
        StudioSession.journal(issues, files);
        if (!open) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof StudioScreen)) {
            minecraft.setScreen(new StudioScreen());
        }
    }

    public static void studioCatalog(List<String> names) {
        StudioSession.catalog(names);
    }

    public static void studioText(String name, String text, long revision, int flags) {
        StudioSession.acceptText(name, text, revision, flags);
    }

    public static void studioLive(StudioLivePacket.View view) {
        StudioSync.onView(view);
    }

    public static void studioExplorerDir(com.scriptcraft.network.StudioExplorerPacket.DirView view) {
        StudioSession.acceptExplorerDir(view);
    }

    public static void studioExplorerFile(com.scriptcraft.network.StudioExplorerPacket.FileView view) {
        StudioSession.acceptExplorerFile(view);
    }

    public static void toast(String text) {
        ClientToasts.push(text);
    }

    public static void cameraEffect(com.scriptcraft.network.CameraEffectPacket packet) {
        ClientCameraEffects.apply(packet);
    }

    public static void profilerData(com.scriptcraft.network.ProfilerPacket.Data data) {
        StudioSession.acceptProfiler(data);
    }

    public static void historyList(com.scriptcraft.network.ScriptHistoryPacket.ListView view) {
        StudioSession.acceptHistoryList(view);
    }

    public static void historyPreview(com.scriptcraft.network.ScriptHistoryPacket.PreviewView view) {
        StudioSession.acceptHistoryPreview(view);
    }
}
