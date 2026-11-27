package com.scriptcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.scriptcraft.ScriptCraft;
import com.scriptcraft.network.LeashPacket;
import com.scriptcraft.script.runtime.LeashManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Клиентский рендерер 3D-поводка (24-сегментная провисающая верёвка в стиле ванильного Minecraft + узел на блоке)
 * и плавная локальная физика натяжения для клиентского игрока без рывков сети.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = ScriptCraft.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientLeash {
    private static final int SEGMENTS = 24;
    private static final Map<Integer, LeashPacket.Entry> ACTIVE = new ConcurrentHashMap<>();

    private ClientLeash() {
    }

    public static void reset() {
        ACTIVE.clear();
    }

    public static void handlePacket(LeashPacket pkt) {
        switch (pkt.op()) {
            case LeashPacket.OP_UPSERT -> {
                if (!pkt.entries().isEmpty()) {
                    LeashPacket.Entry e = pkt.entries().get(0);
                    ACTIVE.put(e.leashedEntityId(), e);
                }
            }
            case LeashPacket.OP_REMOVE -> ACTIVE.remove(pkt.targetEntityId());
            case LeashPacket.OP_CLEAR -> ACTIVE.clear();
            case LeashPacket.OP_FULL_SYNC -> {
                ACTIVE.clear();
                for (LeashPacket.Entry e : pkt.entries()) {
                    ACTIVE.put(e.leashedEntityId(), e);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Локальная физика натяжения для самого игрока — обеспечивает 100% плавное удержание без rubber-banding.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = mc.player;
        ClientLevel level = mc.level;
        if (self == null || level == null || mc.isPaused()) {
            return;
        }
        String curDim = level.dimension().location().toString();
        int selfId = self.getId();

        for (LeashPacket.Entry e : ACTIVE.values()) {
            if (!curDim.equals(e.dimension()) || e.stiffness() <= 0.0001f) {
                continue;
            }
            boolean selfIsLeashed = e.leashedEntityId() == selfId;
            boolean selfIsBidirHolder = !e.toBlock() && e.bidirectional() && e.holderEntityId() == selfId;
            if (!selfIsLeashed && !selfIsBidirHolder) {
                continue;
            }

            Vec3 anchorPos;
            float share = 1.0f;
            if (selfIsLeashed) {
                if (e.toBlock()) {
                    BlockPos bp = e.blockPos();
                    BlockState bs = level.getBlockState(bp);
                    double ay = bs.is(BlockTags.FENCES) ? bp.getY() + 0.375 : bp.getY() + 0.5;
                    anchorPos = new Vec3(bp.getX() + 0.5, ay, bp.getZ() + 0.5);
                } else {
                    Entity holder = level.getEntity(e.holderEntityId());
                    if (holder == null || !holder.isAlive()) {
                        continue;
                    }
                    anchorPos = holder.position().add(0.0, holder.getBbHeight() * 0.55, 0.0);
                    if (e.bidirectional()) {
                        share = 0.5f;
                    }
                }
            } else {
                Entity other = level.getEntity(e.leashedEntityId());
                if (other == null || !other.isAlive()) {
                    continue;
                }
                anchorPos = other.position().add(0.0, other.getBbHeight() * 0.5, 0.0);
                share = 0.5f;
            }

            Vec3 myPos = self.position().add(0.0, self.getBbHeight() * 0.5, 0.0);
            Vec3 delta = anchorPos.subtract(myPos);
            double dist = delta.length();
            if (dist < 1.0e-4) {
                continue;
            }
            boolean overMax = dist > e.maxLength();
            boolean underMin = e.minLength() > 0f && dist < e.minLength();
            if (!overMax && !underMin) {
                continue;
            }
            double targetLen = overMax ? e.maxLength() : e.minLength();
            double stretch = dist - targetLen;
            Vec3 dir = delta.scale(1.0 / dist);
            LeashManager.applyConstraintForce(self, dir, stretch, e.stiffness() * share, false);
        }
    }

    /**
     * 3D-рендер поводка и узла на блоке в мире (от 1-го и от 3-го лица).
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || ACTIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        String curDim = level.dimension().location().toString();
        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = poseStack.last().pose();

        for (LeashPacket.Entry e : ACTIVE.values()) {
            if (!curDim.equals(e.dimension())) {
                continue;
            }
            Entity leashed = level.getEntity(e.leashedEntityId());
            if (leashed == null || !leashed.isAlive()) {
                continue;
            }

            Vec3 start = resolveLeashedAttachPoint(mc, leashed, pt);
            Vec3 end;
            if (e.toBlock()) {
                BlockPos bp = e.blockPos();
                BlockState bs = level.getBlockState(bp);
                double ay = bs.is(BlockTags.FENCES) ? bp.getY() + 0.375 : bp.getY() + 0.5;
                end = new Vec3(bp.getX() + 0.5, ay, bp.getZ() + 0.5);
                if (e.showKnot() && end.distanceToSqr(cam) < 96.0 * 96.0) {
                    renderBlockKnot(buffers, mat, level, bp, end, e.color());
                }
            } else {
                Entity holder = level.getEntity(e.holderEntityId());
                if (holder == null || !holder.isAlive()) {
                    continue;
                }
                end = holder.getRopeHoldPosition(pt);
            }

            if (start.distanceToSqr(cam) > 128.0 * 128.0 && end.distanceToSqr(cam) > 128.0 * 128.0) {
                continue;
            }
            renderRope(buffers, mat, level, start, end, e);
        }

        poseStack.popPose();
    }

    private static Vec3 resolveLeashedAttachPoint(Minecraft mc, Entity leashed, float pt) {
        if (leashed == mc.player && mc.options.getCameraType().isFirstPerson()) {
            Vec3 p = leashed.getPosition(pt);
            return new Vec3(p.x, p.y + leashed.getBbHeight() * 0.52, p.z);
        }
        Vec3 p = leashed.getPosition(pt);
        return new Vec3(p.x, p.y + leashed.getBbHeight() * 0.58, p.z);
    }

    private static void renderRope(MultiBufferSource.BufferSource buffers, Matrix4f mat, ClientLevel level,
                                   Vec3 start, Vec3 end, LeashPacket.Entry entry) {
        float dx = (float) (end.x - start.x);
        float dy = (float) (end.y - start.y);
        float dz = (float) (end.z - start.z);
        float dist = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 1.0e-3f) {
            return;
        }

        float maxLen = Math.max(0.5f, entry.maxLength());
        float slack = Mth.clamp(1.0f - (dist / maxLen), 0.0f, 1.0f);
        float droop = entry.sag() * (0.04f + slack * Math.min(2.3f, maxLen * 0.21f));
        float hw = 0.025f * Mth.clamp(entry.width(), 0.2f, 6.0f);

        float horiz = Mth.sqrt(dx * dx + dz * dz);
        float nx;
        float nz;
        if (horiz < 1.0e-4f) {
            nx = hw;
            nz = 0.0f;
        } else {
            nx = (-dz / horiz) * hw;
            nz = (dx / horiz) * hw;
        }

        int argb = entry.color();
        float baseR = ((argb >> 16) & 0xFF) / 255.0f;
        float baseG = ((argb >> 8) & 0xFF) / 255.0f;
        float baseB = (argb & 0xFF) / 255.0f;

        BlockPos startPos = BlockPos.containing(start);
        BlockPos endPos = BlockPos.containing(end);
        int startBlock = level.getBrightness(LightLayer.BLOCK, startPos);
        int endBlock = level.getBrightness(LightLayer.BLOCK, endPos);
        int startSky = level.getBrightness(LightLayer.SKY, startPos);
        int endSky = level.getBrightness(LightLayer.SKY, endPos);

        // Лента 1: вертикально-диагональная (классический профиль поводка Minecraft)
        VertexConsumer vc1 = buffers.getBuffer(RenderType.leash());
        for (int i = 0; i <= SEGMENTS; i++) {
            float t = (float) i / (float) SEGMENTS;
            int packedLight = LightTexture.pack(
                    (int) Mth.lerp(t, startBlock, endBlock),
                    (int) Mth.lerp(t, startSky, endSky));
            float shade = (i % 2 == 0) ? 1.0f : 0.72f;
            float r = baseR * shade;
            float g = baseG * shade;
            float b = baseB * shade;

            float px = (float) start.x + dx * t;
            float py = (float) start.y + dy * t - Mth.sin(t * (float) Math.PI) * droop;
            float pz = (float) start.z + dz * t;

            vc1.vertex(mat, px + nx * 0.5f, py + hw, pz + nz * 0.5f).color(r, g, b, 1.0f).uv2(packedLight).endVertex();
            vc1.vertex(mat, px - nx * 0.5f, py - hw, pz - nz * 0.5f).color(r, g, b, 1.0f).uv2(packedLight).endVertex();
        }
        buffers.endBatch(RenderType.leash());

        // Лента 2: перекрёстная плоскость для полноценного 3D-объёма с любого ракурса камеры
        VertexConsumer vc2 = buffers.getBuffer(RenderType.leash());
        for (int i = 0; i <= SEGMENTS; i++) {
            float t = (float) i / (float) SEGMENTS;
            int packedLight = LightTexture.pack(
                    (int) Mth.lerp(t, startBlock, endBlock),
                    (int) Mth.lerp(t, startSky, endSky));
            float shade = (i % 2 == 0) ? 0.92f : 0.66f;
            float r = baseR * shade;
            float g = baseG * shade;
            float b = baseB * shade;

            float px = (float) start.x + dx * t;
            float py = (float) start.y + dy * t - Mth.sin(t * (float) Math.PI) * droop;
            float pz = (float) start.z + dz * t;

            vc2.vertex(mat, px - nx, py + hw * 0.45f, pz - nz).color(r, g, b, 1.0f).uv2(packedLight).endVertex();
            vc2.vertex(mat, px + nx, py - hw * 0.45f, pz + nz).color(r, g, b, 1.0f).uv2(packedLight).endVertex();
        }
        buffers.endBatch(RenderType.leash());
    }

    /**
     * Компактный 3D-узел поводка вокруг точки крепления на блоке / заборе.
     */
    private static void renderBlockKnot(MultiBufferSource.BufferSource buffers, Matrix4f mat,
                                        ClientLevel level, BlockPos bp, Vec3 center, int argb) {
        int packedLight = LightTexture.pack(
                level.getBrightness(LightLayer.BLOCK, bp),
                level.getBrightness(LightLayer.SKY, bp));
        float r1 = ((argb >> 16) & 0xFF) / 255.0f;
        float g1 = ((argb >> 8) & 0xFF) / 255.0f;
        float b1 = (argb & 0xFF) / 255.0f;
        float r2 = r1 * 0.72f;
        float g2 = g1 * 0.72f;
        float b2 = b1 * 0.72f;

        float cx = (float) center.x;
        float cy = (float) center.y;
        float cz = (float) center.z;
        float rad = 0.16f;
        float kh = 0.10f;

        float[][] corners = {
                {cx - rad, cz - rad},
                {cx + rad, cz - rad},
                {cx + rad, cz + rad},
                {cx - rad, cz + rad},
                {cx - rad, cz - rad}
        };
        VertexConsumer vc = buffers.getBuffer(RenderType.leash());
        for (int i = 0; i < corners.length; i++) {
            boolean alt = (i % 2 == 1);
            float cr = alt ? r2 : r1;
            float cg = alt ? g2 : g1;
            float cb = alt ? b2 : b1;
            vc.vertex(mat, corners[i][0], cy + kh, corners[i][1]).color(cr, cg, cb, 1.0f).uv2(packedLight).endVertex();
            vc.vertex(mat, corners[i][0], cy - kh, corners[i][1]).color(cr, cg, cb, 1.0f).uv2(packedLight).endVertex();
        }
        buffers.endBatch(RenderType.leash());
    }
}
