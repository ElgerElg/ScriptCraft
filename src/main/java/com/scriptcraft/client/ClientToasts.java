package com.scriptcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Всплывающие уведомления мода вместо чата. Рисуются своим оверлеем,
 * место, размер и время задаёт {@link ClientUiSettings}.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientToasts {
    private static final int MAX_VISIBLE = 5;
    private static final int MAX_QUEUE = 12;
    private static final ArrayDeque<String> QUEUE = new ArrayDeque<>();
    private static final List<Toast> VISIBLE = new ArrayList<>();

    private ClientToasts() {
    }

    public static void push(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        while (QUEUE.size() >= MAX_QUEUE) {
            QUEUE.removeFirst();
        }
        QUEUE.addLast(text);
    }

    public static void clear() {
        QUEUE.clear();
        VISIBLE.clear();
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        long now = System.nanoTime();
        float life = ClientUiSettings.seconds() * 1_000_000_000f;
        VISIBLE.removeIf(t -> now >= t.until);
        while (VISIBLE.size() < MAX_VISIBLE && !QUEUE.isEmpty()) {
            VISIBLE.add(new Toast(QUEUE.removeFirst(), now + (long) life));
        }
        if (VISIBLE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float scale = ClientUiSettings.scale();
        int margin = 6;
        boolean up = ClientUiSettings.place() == ClientUiSettings.Place.CHAT
                || ClientUiSettings.place() == ClientUiSettings.Place.BOTTOM_RIGHT;
        int y = switch (ClientUiSettings.place()) {
            case TOP_LEFT, TOP_RIGHT -> margin;
            case BOTTOM_RIGHT -> height - margin;
            case CHAT -> height - 58;
        };
        for (int i = 0; i < VISIBLE.size(); i++) {
            Toast toast = VISIBLE.get(i);
            int textW = Math.min(260, font.width(toast.text));
            int boxW = textW + 12;
            int boxH = 16;
            int step = Math.round((boxH + 3) * scale);
            int drawY = up ? y - step * (i + 1) : y + step * i;
            int drawX = switch (ClientUiSettings.place()) {
                case TOP_RIGHT, BOTTOM_RIGHT -> width - margin - Math.round(boxW * scale);
                default -> margin;
            };
            float alpha = fade(now, toast.until);
            int bg = withAlpha(0x141820, alpha * 0.92f);
            int edge = withAlpha(0xC4A15A, alpha);
            g.pose().pushPose();
            g.pose().translate(drawX, drawY, 0);
            g.pose().scale(scale, scale, 1f);
            g.fill(0, 0, boxW, boxH, bg);
            g.fill(0, 0, 2, boxH, edge);
            g.drawString(font, toast.text, 6, 4, withAlpha(0xFFFFFF, alpha), false);
            g.pose().popPose();
        }
    }

    private static float fade(long now, long until) {
        long left = until - now;
        if (left > 400_000_000L) {
            return 1f;
        }
        return Math.max(0f, left / 400_000_000f);
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static final class Toast {
        final String text;
        long until;

        Toast(String text, long until) {
            this.text = text;
            this.until = until;
        }
    }
}
