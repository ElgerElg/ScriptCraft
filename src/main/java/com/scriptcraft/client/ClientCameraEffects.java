package com.scriptcraft.client;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.network.CameraEffectPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клиентские эффекты камеры: тряска (shake), затемнение/вспышка экрана (fade),
 * изменение FOV и наклон камеры (roll).
 */
@Mod.EventBusSubscriber(modid = ScriptCraft.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientCameraEffects {
    private static float shakeIntensity = 0f;
    private static int shakeTicksLeft = 0;
    private static int shakeTotalTicks = 0;

    private static int fadeColor = 0xFF000000;
    private static int fadeInTicks = 0;
    private static int fadeStayTicks = 0;
    private static int fadeOutTicks = 0;
    private static int fadeElapsed = 0;
    private static boolean fadeActive = false;

    private static float fovOffset = 0f;
    private static int fovTicksLeft = 0;
    private static int fovTotalTicks = 0;

    private static float rollDegrees = 0f;
    private static int rollTicksLeft = 0;
    private static int rollTotalTicks = 0;

    private static long animTick = 0L;

    private ClientCameraEffects() {
    }

    public static void apply(CameraEffectPacket p) {
        switch (p.kind()) {
            case CameraEffectPacket.SHAKE -> {
                shakeIntensity = Math.max(0f, Math.min(15f, p.f1()));
                shakeTotalTicks = Math.max(1, Math.min(20 * 60, p.i1()));
                shakeTicksLeft = shakeTotalTicks;
            }
            case CameraEffectPacket.FADE -> {
                fadeColor = p.colorArgb();
                fadeInTicks = Math.max(0, Math.min(20 * 60, p.i1()));
                fadeStayTicks = Math.max(0, Math.min(20 * 60, p.i2()));
                fadeOutTicks = Math.max(0, Math.min(20 * 60, p.i3()));
                fadeElapsed = 0;
                fadeActive = (fadeInTicks + fadeStayTicks + fadeOutTicks) > 0;
            }
            case CameraEffectPacket.FOV -> {
                fovOffset = Math.max(-50f, Math.min(60f, p.f1()));
                fovTotalTicks = Math.max(0, Math.min(20 * 300, p.i1()));
                fovTicksLeft = fovTotalTicks;
                if (fovOffset == 0f) {
                    fovTicksLeft = 0;
                    fovTotalTicks = 0;
                }
            }
            case CameraEffectPacket.ROLL -> {
                rollDegrees = Math.max(-180f, Math.min(180f, p.f1()));
                rollTotalTicks = Math.max(0, Math.min(20 * 300, p.i1()));
                rollTicksLeft = rollTotalTicks;
                if (rollDegrees == 0f) {
                    rollTicksLeft = 0;
                    rollTotalTicks = 0;
                }
            }
            case CameraEffectPacket.RESET -> reset();
            default -> {
            }
        }
    }

    public static void reset() {
        shakeIntensity = 0f;
        shakeTicksLeft = 0;
        shakeTotalTicks = 0;
        fadeActive = false;
        fadeElapsed = 0;
        fovOffset = 0f;
        fovTicksLeft = 0;
        fovTotalTicks = 0;
        rollDegrees = 0f;
        rollTicksLeft = 0;
        rollTotalTicks = 0;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        animTick++;
        if (shakeTicksLeft > 0) {
            shakeTicksLeft--;
            if (shakeTicksLeft == 0) {
                shakeIntensity = 0f;
            }
        }
        if (fadeActive) {
            fadeElapsed++;
            if (fadeElapsed >= fadeInTicks + fadeStayTicks + fadeOutTicks) {
                fadeActive = false;
            }
        }
        if (fovTotalTicks > 0 && fovTicksLeft > 0) {
            fovTicksLeft--;
            if (fovTicksLeft == 0) {
                fovOffset = 0f;
                fovTotalTicks = 0;
            }
        }
        if (rollTotalTicks > 0 && rollTicksLeft > 0) {
            rollTicksLeft--;
            if (rollTicksLeft == 0) {
                rollDegrees = 0f;
                rollTotalTicks = 0;
            }
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        double pt = event.getPartialTick();
        float totalRoll = 0f;
        if (rollDegrees != 0f) {
            float factor = rollTotalTicks > 0 ? Math.min(1f, (rollTicksLeft - (float) pt) / Math.min(10f, rollTotalTicks)) : 1f;
            totalRoll += rollDegrees * Math.max(0f, factor);
        }
        if (shakeTicksLeft > 0 && shakeIntensity > 0f) {
            float progress = Math.max(0f, (shakeTicksLeft - (float) pt) / Math.max(1f, shakeTotalTicks));
            float amp = shakeIntensity * progress;
            double t = (animTick + pt) * 1.85;
            float dyaw = (float) (Math.sin(t * 1.7) * amp * 1.6);
            float dpitch = (float) (Math.cos(t * 2.3) * amp * 1.2);
            float droll = (float) (Math.sin(t * 2.9) * amp * 2.4);
            event.setYaw(event.getYaw() + dyaw);
            event.setPitch(event.getPitch() + dpitch);
            totalRoll += droll;
        }
        if (totalRoll != 0f) {
            event.setRoll(event.getRoll() + totalRoll);
        }
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (fovOffset == 0f) {
            return;
        }
        float factor = 1f;
        if (fovTotalTicks > 0) {
            factor = Math.max(0f, Math.min(1f, (fovTicksLeft - (float) event.getPartialTick()) / Math.min(10f, fovTotalTicks)));
        }
        double next = Math.max(15.0, Math.min(150.0, event.getFOV() + fovOffset * factor));
        event.setFOV(next);
    }

    @SubscribeEvent
    public static void onRenderOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (!fadeActive) {
            return;
        }
        // Рисуем один раз поверх всех оверлеев (привязка к HOTBAR или CHAT, либо только один проход)
        if (!event.getOverlay().id().getPath().equals("chat_panel")) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float t = fadeElapsed + event.getPartialTick();
        float alpha;
        if (fadeInTicks > 0 && t < fadeInTicks) {
            alpha = t / fadeInTicks;
        } else if (t < fadeInTicks + fadeStayTicks) {
            alpha = 1f;
        } else if (fadeOutTicks > 0) {
            float outT = t - fadeInTicks - fadeStayTicks;
            alpha = Math.max(0f, 1f - outT / fadeOutTicks);
        } else {
            alpha = 0f;
        }
        int baseA = (fadeColor >>> 24) & 0xFF;
        if (baseA == 0) {
            baseA = 255;
        }
        int a = Math.max(0, Math.min(255, Math.round(baseA * alpha)));
        if (a <= 0) {
            return;
        }
        int argb = (a << 24) | (fadeColor & 0x00FFFFFF);
        GuiGraphics g = event.getGuiGraphics();
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 500f);
        g.fill(0, 0, sw, sh, argb);
        g.pose().popPose();
    }
}
