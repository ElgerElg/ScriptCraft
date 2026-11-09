package com.scriptcraft.client;

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
        Minecraft.getInstance().setScreen(new ScriptScreen(description));
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

    public static void studioText(String name, String text) {
        StudioSession.acceptText(name, text);
    }

    public static void toast(String text) {
        ClientToasts.push(text);
    }
}
