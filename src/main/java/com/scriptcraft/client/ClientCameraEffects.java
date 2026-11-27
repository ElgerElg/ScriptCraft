package com.scriptcraft.client;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.network.CameraEffectPacket;
import com.scriptcraft.network.CameraPathPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Клиентские эффекты камеры: тряска (shake), затемнение/вспышка экрана (fade),
 * изменение FOV, наклон камеры (roll) и плавные катсцены / пролёты по сплайну Catmull-Rom!
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

    // --- Сплайн-пролёт камеры (катсцена) ---
    private static boolean pathActive = false;
    private static List<CameraPathPacket.Waypoint> pathPoints = List.of();
    private static int pathDurationTicks = 0;
    private static int pathElapsedTicks = 0;
    private static boolean pathHasLook = false;
    private static double pathLookX = 0, pathLookY = 0, pathLookZ = 0;
    private static int pathLookEntityId = -1;
    private static boolean pathLockInput = true;
    private static CutsceneCameraEntity cutsceneEntity = null;
    private static float cutsceneYaw = 0f;
    private static float cutscenePitch = 0f;

    private static long animTick = 0L;

    /** Невидимая клиентская сущность-якорь камеры с нулевой высотой глаз — игрок видит своё тело со стороны! */
    private static final class CutsceneCameraEntity extends Entity {
        CutsceneCameraEntity(Level level) {
            super(EntityType.ITEM_DISPLAY, level);
            this.noPhysics = true;
            this.setInvisible(true);
            this.refreshDimensions();
        }

        @Override
        protected float getEyeHeight(Pose pose, EntityDimensions dimensions) {
            return 0.0F;
        }

        @Override
        protected void defineSynchedData() {
        }

        @Override
        protected void readAdditionalSaveData(CompoundTag tag) {
        }

        @Override
        protected void addAdditionalSaveData(CompoundTag tag) {
        }

        @Override
        public Packet<ClientGamePacketListener> getAddEntityPacket() {
            throw new UnsupportedOperationException("Client-only camera anchor");
        }
    }

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

    public static void applyPath(CameraPathPacket p) {
        if (p.stop() || p.waypoints().isEmpty()) {
            stopCutscene();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        pathPoints = List.copyOf(p.waypoints());
        pathDurationTicks = Math.max(1, Math.min(20 * 600, p.durationTicks()));
        pathElapsedTicks = 0;
        pathHasLook = p.hasLookTarget();
        pathLookX = p.lookX();
        pathLookY = p.lookY();
        pathLookZ = p.lookZ();
        pathLookEntityId = p.lookEntityId();
        pathLockInput = p.lockInput();
        pathActive = true;

        if (cutsceneEntity == null || cutsceneEntity.level() != mc.level) {
            cutsceneEntity = new CutsceneCameraEntity(mc.level);
        }
        CameraPathPacket.Waypoint first = pathPoints.get(0);
        cutsceneEntity.setPos(first.x(), first.y(), first.z());
        cutsceneEntity.xo = first.x();
        cutsceneEntity.yo = first.y();
        cutsceneEntity.zo = first.z();
        updateCutsceneFrame(0.0f);
        mc.setCameraEntity(cutsceneEntity);
    }

    public static void stopCutscene() {
        pathActive = false;
        pathPoints = List.of();
        pathElapsedTicks = 0;
        pathDurationTicks = 0;
        Minecraft mc = Minecraft.getInstance();
        if (cutsceneEntity != null && mc.getCameraEntity() == cutsceneEntity && mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
        cutsceneEntity = null;
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
        stopCutscene();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (pathActive && pathLockInput) {
            event.getInput().forwardImpulse = 0f;
            event.getInput().leftImpulse = 0f;
            event.getInput().up = false;
            event.getInput().down = false;
            event.getInput().left = false;
            event.getInput().right = false;
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) {
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
        if (pathActive) {
            if (mc.level == null || mc.player == null) {
                stopCutscene();
            } else {
                pathElapsedTicks++;
                if (pathElapsedTicks >= pathDurationTicks) {
                    stopCutscene();
                } else if (cutsceneEntity != null && mc.getCameraEntity() != cutsceneEntity) {
                    mc.setCameraEntity(cutsceneEntity);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START && pathActive) {
            updateCutsceneFrame(event.renderTickTime);
        }
    }

    private static void updateCutsceneFrame(float partialTick) {
        if (!pathActive || pathPoints.isEmpty() || cutsceneEntity == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float rawU = Mth.clamp((pathElapsedTicks + partialTick) / Math.max(1f, (float) pathDurationTicks), 0f, 1f);
        // Мягкий старт и мягкая остановка (smoothstep-смесь 25% + линейная 75%) для кинематографичной плавности
        float smooth = rawU * rawU * (3f - 2f * rawU);
        float u = Mth.lerp(0.35f, rawU, smooth);

        double px, py, pz;
        float yaw = cutsceneYaw;
        float pitch = cutscenePitch;

        int n = pathPoints.size();
        if (n == 1) {
            CameraPathPacket.Waypoint w = pathPoints.get(0);
            px = w.x();
            py = w.y();
            pz = w.z();
            if (w.hasAngles()) {
                yaw = w.yaw();
                pitch = w.pitch();
            }
        } else {
            float scaled = u * (n - 1);
            int idx = Math.min(n - 2, (int) Math.floor(scaled));
            float segT = scaled - idx;

            CameraPathPacket.Waypoint p0 = pathPoints.get(Math.max(0, idx - 1));
            CameraPathPacket.Waypoint p1 = pathPoints.get(idx);
            CameraPathPacket.Waypoint p2 = pathPoints.get(idx + 1);
            CameraPathPacket.Waypoint p3 = pathPoints.get(Math.min(n - 1, idx + 2));

            px = catmullRom(segT, p0.x(), p1.x(), p2.x(), p3.x());
            py = catmullRom(segT, p0.y(), p1.y(), p2.y(), p3.y());
            pz = catmullRom(segT, p0.z(), p1.z(), p2.z(), p3.z());

            if (!pathHasLook) {
                if (p1.hasAngles() && p2.hasAngles()) {
                    yaw = Mth.rotLerp(segT, p1.yaw(), p2.yaw());
                    pitch = Mth.lerp(segT, p1.pitch(), p2.pitch());
                } else if (p1.hasAngles()) {
                    yaw = p1.yaw();
                    pitch = p1.pitch();
                } else {
                    // Автоматический поворот камеры вдоль касательной движения!
                    double aheadT = Math.min(1f, segT + 0.02f);
                    double ax = catmullRom((float) aheadT, p0.x(), p1.x(), p2.x(), p3.x()) - px;
                    double ay = catmullRom((float) aheadT, p0.y(), p1.y(), p2.y(), p3.y()) - py;
                    double az = catmullRom((float) aheadT, p0.z(), p1.z(), p2.z(), p3.z()) - pz;
                    double horiz = Math.sqrt(ax * ax + az * az);
                    if (horiz > 1e-5 || Math.abs(ay) > 1e-5) {
                        yaw = (float) (Mth.atan2(-ax, az) * Mth.RAD_TO_DEG);
                        pitch = (float) (Mth.atan2(-ay, horiz) * Mth.RAD_TO_DEG);
                    }
                }
            }
        }

        if (pathHasLook) {
            double tx = pathLookX, ty = pathLookY, tz = pathLookZ;
            if (pathLookEntityId >= 0 && mc.level != null) {
                Entity targetEnt = mc.level.getEntity(pathLookEntityId);
                if (targetEnt != null) {
                    tx = Mth.lerp(partialTick, targetEnt.xo, targetEnt.getX());
                    ty = Mth.lerp(partialTick, targetEnt.yo, targetEnt.getY()) + targetEnt.getBbHeight() * 0.65;
                    tz = Mth.lerp(partialTick, targetEnt.zo, targetEnt.getZ());
                }
            }
            double dx = tx - px;
            double dy = ty - py;
            double dz = tz - pz;
            double horiz = Math.sqrt(dx * dx + dz * dz);
            yaw = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
            pitch = (float) (Mth.atan2(-dy, horiz) * Mth.RAD_TO_DEG);
        }

        cutsceneEntity.xo = px;
        cutsceneEntity.yo = py;
        cutsceneEntity.zo = pz;
        cutsceneEntity.setPosRaw(px, py, pz);
        cutsceneEntity.setYRot(yaw);
        cutsceneEntity.yRotO = yaw;
        cutsceneEntity.setXRot(pitch);
        cutsceneEntity.xRotO = pitch;
        cutsceneYaw = yaw;
        cutscenePitch = pitch;
    }

    private static double catmullRom(float t, double p0, double p1, double p2, double p3) {
        double t2 = (double) t * t;
        double t3 = t2 * t;
        return 0.5 * ((2.0 * p1)
                + (-p0 + p2) * t
                + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2
                + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (pathActive) {
            event.setYaw(cutsceneYaw);
            event.setPitch(cutscenePitch);
        }
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
