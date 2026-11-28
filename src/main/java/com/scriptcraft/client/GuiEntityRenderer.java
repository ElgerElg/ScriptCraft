package com.scriptcraft.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.scriptcraft.ModPaths;
import com.scriptcraft.ScriptCraft;
import com.scriptcraft.screen.EntityElement;
import com.scriptcraft.screen.Layout;
import com.scriptcraft.screen.ScreenMeta;
import com.scriptcraft.script.runtime.ItemStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Клиентский 3D-рендерер сущностей, игроков и кастомных скинов для {@code open screen}, {@code create hud} и GUI-студии:
 * <ul>
 *     <li>загрузка скинов по нику (онлайн таблист + Mojang API), прямой HTTP/HTTPS ссылке или из ресурсов мода;</li>
 *     <li>автоопределение модели Стив ({@code wide}, 4px) / Алекс ({@code slim}, 3px) по метаданным профиля или пикселям PNG;</li>
 *     <li>конвертация старых скинов 64×32 в 64×64 и поддержка второго слоя скина (overlay);</li>
 *     <li>рендер любых ванильных и модовых сущностей с NBT, экипировкой, baby/sneak/walk;</li>
 *     <li>вращение в любую сторону с заданной скоростью ({@code spinSpeed}) и слежение взглядом за курсором или точкой ({@code lookMode}).</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class GuiEntityRenderer {

    public record SkinInfo(ResourceLocation texture, boolean slim, boolean ready) {
    }

    private static final class CachedSkin {
        volatile ResourceLocation texture;
        volatile boolean slim;
        volatile boolean ready;
        volatile long retryAfterMs;

        CachedSkin(ResourceLocation texture, boolean slim) {
            this.texture = texture;
            this.slim = slim;
        }
    }

    private static final ExecutorService SKIN_POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "ScriptCraft-GuiSkinLoader");
        t.setDaemon(true);
        return t;
    });

    private static final Map<String, CachedSkin> SKIN_CACHE = new ConcurrentHashMap<>();
    private static int dynamicSkinCounter = 0;

    private static PlayerModel<?> fallbackWide;
    private static PlayerModel<?> fallbackSlim;

    private static ClientLevel cachedLevel;
    private static GuiPreviewPlayer previewPlayer;
    private static final Map<String, Entity> ENTITY_CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entity> eldest) {
            return size() > 64;
        }
    };

    private static final class GuiPreviewPlayer extends AbstractClientPlayer {
        ResourceLocation customSkin = DefaultPlayerSkin.getDefaultSkin();
        boolean slimModel = false;
        boolean showOverlayParts = true;
        boolean previewBaby = false;

        GuiPreviewPlayer(ClientLevel level) {
            super(level, new GameProfile(UUID.fromString("00000000-0000-4000-8000-000000005c01"), "SCPreview"));
        }

        @Override
        public ResourceLocation getSkinTextureLocation() {
            return customSkin != null ? customSkin : super.getSkinTextureLocation();
        }

        @Override
        public String getModelName() {
            return slimModel ? "slim" : "default";
        }

        @Override
        public boolean isModelPartShown(PlayerModelPart part) {
            return showOverlayParts;
        }

        @Override
        public boolean isBaby() {
            return previewBaby;
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        @Override
        public boolean shouldShowName() {
            return false;
        }
    }

    private GuiEntityRenderer() {
    }

    public static void invalidateCache() {
        SKIN_CACHE.clear();
        ENTITY_CACHE.clear();
    }

    /** Рисует 2D-голову игрока (лицо + второй слой шляпы) по нику, ссылке или ресурсу — используется в списках. */
    public static void renderHead2D(GuiGraphics g, String rawSource, int x, int y, int size, boolean overlay) {
        SkinInfo info = resolveSkin(rawSource);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(info.texture(), x, y, size, size, 8f, 8f, 8, 8, 64, 64);
        if (overlay) {
            g.blit(info.texture(), x, y, size, size, 40f, 8f, 8, 8, 64, 64);
        }
        RenderSystem.disableBlend();
    }

    /** Отрисовывает 3D-элемент {@link EntityElement} на экране или HUD. */
    public static void render(GuiGraphics g, EntityElement ent, int sw, int sh, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Layout l = ent.layout();
        int bw = l.width() > 0 ? l.width() : EntityElement.DEFAULT_WIDTH;
        int bh = l.height() > 0 ? l.height() : EntityElement.DEFAULT_HEIGHT;
        int bx = l.anchor().resolveX(sw, bw, l.x());
        int by = l.anchor().resolveY(sh, bh, l.y());

        float hScale = ScreenMeta.elementHoverScale(ent);
        boolean hovered = mouseX >= bx && mouseY >= by && mouseX < bx + bw && mouseY < by + bh;
        float zoomMul = (hovered && hScale != 1f) ? hScale : 1f;

        if (ent.clip()) {
            g.enableScissor(bx, by, bx + bw, by + bh);
        }

        boolean isPlayer = ent.playerMode()
                || !ent.skin().isEmpty()
                || "player".equalsIgnoreCase(ent.target())
                || "minecraft:player".equalsIgnoreCase(ent.target())
                || "self".equalsIgnoreCase(ent.target());

        try {
            if (isPlayer) {
                renderPlayerElement(g, mc, ent, bx, by, bw, bh, sw, sh, mouseX, mouseY, zoomMul);
            } else {
                renderMobElement(g, mc, ent, bx, by, bw, bh, sw, sh, mouseX, mouseY, zoomMul);
            }
        } catch (Throwable t) {
            // Защита от падения кадра при некорректном модовом рендерере сущности
        } finally {
            if (ent.clip()) {
                g.disableScissor();
            }
        }
    }

    private static void renderPlayerElement(GuiGraphics g, Minecraft mc, EntityElement ent,
                                            int bx, int by, int bw, int bh, int sw, int sh,
                                            int mouseX, int mouseY, float zoomMul) {
        String skinSpec = !ent.skin().isEmpty() ? ent.skin() : ent.target();
        SkinInfo skinInfo = resolveSkin(skinSpec);
        boolean slim = switch (ent.modelStyle()) {
            case SLIM -> true;
            case WIDE -> false;
            case AUTO -> skinInfo.slim();
        };

        float baseScale = ent.scale() > 0f ? ent.scale() : Math.min(bw * 0.62f, bh * 0.42f);
        float pxScale = Math.max(4f, baseScale * zoomMul);
        float cx = bx + bw * 0.5f;
        float footY = by + bh * 0.88f;
        float eyeY = footY - (ent.baby() ? 0.85f : 1.62f) * pxScale;

        Angles ang = computeAngles(ent, cx, eyeY, sw, sh, mouseX, mouseY);

        if (mc.level != null) {
            ensureLevelCache(mc.level);
            if (previewPlayer != null) {
                previewPlayer.customSkin = skinInfo.texture();
                previewPlayer.slimModel = slim;
                previewPlayer.showOverlayParts = ent.showOverlay();
                previewPlayer.previewBaby = ent.baby();
                previewPlayer.setPose(ent.sneaking() ? Pose.CROUCHING : Pose.STANDING);
                previewPlayer.setShiftKeyDown(ent.sneaking());
                applyEquipment(previewPlayer, ent.equipment());
                applyWalkAndTick(previewPlayer, ent.walk());
                renderLivingViaDispatcher(g, mc, previewPlayer, cx, footY, pxScale, ang, ent.roll(), ent.layout().color());
                return;
            }
        }

        // Резервный прямой рендер через PlayerModel (если мир не загружен)
        renderStandalonePlayer(g, mc, skinInfo.texture(), slim, ent.showOverlay(), ent.baby(), ent.sneaking(), ent.walk(),
                cx, footY, pxScale, ang, ent.roll(), ent.layout().color());
    }

    private static void renderMobElement(GuiGraphics g, Minecraft mc, EntityElement ent,
                                         int bx, int by, int bw, int bh, int sw, int sh,
                                         int mouseX, int mouseY, float zoomMul) {
        if (mc.level == null) return;
        ensureLevelCache(mc.level);
        Entity entity = getOrCreateEntity(mc.level, ent);
        if (entity == null) return;

        float bbW = Math.max(0.5f, entity.getBbWidth());
        float bbH = Math.max(0.5f, entity.getBbHeight());
        float baseScale = ent.scale() > 0f
                ? ent.scale()
                : Math.min((bw * 0.68f) / bbW, (bh * 0.78f) / bbH);
        float pxScale = Math.max(2f, baseScale * zoomMul);

        float cx = bx + bw * 0.5f;
        float footY = by + bh * 0.88f;
        float eyeY = footY - entity.getEyeHeight() * pxScale;

        Angles ang = computeAngles(ent, cx, eyeY, sw, sh, mouseX, mouseY);
        entity.tickCount = (int) ((System.currentTimeMillis() / 50L) & 0x7FFFFFFF);

        if (entity instanceof LivingEntity living) {
            living.setPose(ent.sneaking() ? Pose.CROUCHING : Pose.STANDING);
            living.setShiftKeyDown(ent.sneaking());
            applyWalkAndTick(living, ent.walk());
            renderLivingViaDispatcher(g, mc, living, cx, footY, pxScale, ang, ent.roll(), ent.layout().color());
        } else {
            renderGenericEntityViaDispatcher(g, mc, entity, cx, footY, pxScale, ang, ent.roll());
        }
    }

    private record Angles(float bodyYaw, float headYaw, float headPitch, float camTiltX) {
    }

    private static Angles computeAngles(EntityElement ent, float cx, float eyeY, int sw, int sh, int mouseX, int mouseY) {
        float spinDeg = 0f;
        if (Math.abs(ent.spinSpeed()) > 0.001f) {
            double sec = System.nanoTime() * 1e-9;
            spinDeg = (float) ((sec * ent.spinSpeed()) % 360.0);
        }
        float baseYaw = ent.yaw() + spinDeg;
        float basePitch = ent.pitch();

        boolean hasTarget = false;
        float tx = cx, ty = eyeY;
        if (ent.lookMode() == EntityElement.LookMode.CURSOR && mouseX >= 0 && mouseY >= 0) {
            tx = mouseX;
            ty = mouseY;
            hasTarget = true;
        } else if (ent.lookMode() == EntityElement.LookMode.POINT) {
            tx = sw * 0.5f + ent.lookX();
            ty = sh * 0.5f + ent.lookY();
            hasTarget = true;
        }

        if (hasTarget) {
            float atanX = (float) Math.atan((cx - tx) / 40.0f);
            float atanY = (float) Math.atan((eyeY - ty) / 40.0f);
            float bodyY = 180.0f + baseYaw + atanX * 22.0f;
            float headY = 180.0f + baseYaw + atanX * 42.0f;
            float headP = basePitch - atanY * 22.0f;
            float tiltX = atanY * 14.0f;
            return new Angles(bodyY, headY, headP, tiltX);
        }
        return new Angles(180.0f + baseYaw, 180.0f + baseYaw, basePitch, -basePitch * 0.35f);
    }

    private static void renderLivingViaDispatcher(GuiGraphics g, Minecraft mc, LivingEntity entity,
                                                  float cx, float footY, float scale,
                                                  Angles ang, float roll, int tint) {
        float oldBody = entity.yBodyRot, oldBodyO = entity.yBodyRotO;
        float oldY = entity.getYRot(), oldYO = entity.yRotO;
        float oldX = entity.getXRot(), oldXO = entity.xRotO;
        float oldHead = entity.yHeadRot, oldHeadO = entity.yHeadRotO;

        entity.yBodyRot = entity.yBodyRotO = ang.bodyYaw;
        entity.setYRot(ang.headYaw);
        entity.yRotO = ang.headYaw;
        entity.setXRot(ang.headPitch);
        entity.xRotO = ang.headPitch;
        entity.yHeadRot = entity.yHeadRotO = ang.headYaw;

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, footY, 130.0);
        pose.mulPoseMatrix(new Matrix4f().scaling(scale, scale, -scale));
        Quaternionf zRot = Axis.ZP.rotationDegrees(180.0f + roll);
        Quaternionf xRot = Axis.XP.rotationDegrees(ang.camTiltX);
        zRot.mul(xRot);
        pose.mulPose(zRot);

        applyTint(tint);
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        xRot.conjugate();
        dispatcher.overrideCameraOrientation(xRot);
        dispatcher.setRenderShadow(false);
        RenderSystem.runAsFancy(() ->
                dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0f, 1.0f, pose, g.bufferSource(), 0xF000F0));
        g.flush();
        dispatcher.setRenderShadow(true);
        pose.popPose();

        restoreGlAfter3D();

        entity.yBodyRot = oldBody;
        entity.yBodyRotO = oldBodyO;
        entity.setYRot(oldY);
        entity.yRotO = oldYO;
        entity.setXRot(oldX);
        entity.xRotO = oldXO;
        entity.yHeadRot = oldHead;
        entity.yHeadRotO = oldHeadO;
    }

    private static void renderGenericEntityViaDispatcher(GuiGraphics g, Minecraft mc, Entity entity,
                                                         float cx, float footY, float scale,
                                                         Angles ang, float roll) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, footY, 130.0);
        pose.mulPoseMatrix(new Matrix4f().scaling(scale, scale, -scale));
        Quaternionf zRot = Axis.ZP.rotationDegrees(180.0f + roll);
        Quaternionf xRot = Axis.XP.rotationDegrees(ang.camTiltX);
        zRot.mul(xRot);
        pose.mulPose(zRot);
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - ang.bodyYaw));

        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        xRot.conjugate();
        dispatcher.overrideCameraOrientation(xRot);
        dispatcher.setRenderShadow(false);
        RenderSystem.runAsFancy(() ->
                dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0f, 1.0f, pose, g.bufferSource(), 0xF000F0));
        g.flush();
        dispatcher.setRenderShadow(true);
        pose.popPose();
        restoreGlAfter3D();
    }

    private static void renderStandalonePlayer(GuiGraphics g, Minecraft mc, ResourceLocation skin,
                                               boolean slim, boolean overlay, boolean baby, boolean sneak, boolean walk,
                                               float cx, float footY, float scale, Angles ang, float roll, int tint) {
        if (mc.getEntityModels() == null) return;
        PlayerModel<?> model = getFallbackModel(mc, slim);
        model.young = baby;
        model.crouching = sneak;

        float netHeadYaw = ang.headYaw - ang.bodyYaw;
        model.head.yRot = (float) Math.toRadians(netHeadYaw);
        model.head.xRot = (float) Math.toRadians(ang.headPitch);
        model.body.xRot = sneak ? 0.5f : 0f;
        float swing = walk ? (float) Math.sin((System.nanoTime() * 1e-9) * 6.0) * 0.65f : 0f;
        model.rightArm.xRot = swing;
        model.leftArm.xRot = -swing;
        model.rightLeg.xRot = -swing;
        model.leftLeg.xRot = swing;

        float r = 1f, gr = 1f, b = 1f, a = 1f;
        if (tint != 0 && tint != 0xFFFFFFFF) {
            int al = (tint >>> 24) & 0xFF;
            a = al == 0 ? 1f : al / 255f;
            r = ((tint >> 16) & 0xFF) / 255f;
            gr = ((tint >> 8) & 0xFF) / 255f;
            b = (tint & 0xFF) / 255f;
        }

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, footY, 130.0);
        pose.mulPoseMatrix(new Matrix4f().scaling(scale, scale, -scale));
        pose.mulPose(Axis.ZP.rotationDegrees(180.0f + roll));
        pose.mulPose(Axis.XP.rotationDegrees(ang.camTiltX));
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - ang.bodyYaw));
        pose.scale(-0.9375f, -0.9375f, 0.9375f);
        pose.translate(0f, -1.501f, 0f);

        Lighting.setupForEntityInInventory();
        var buf = g.bufferSource();
        setModelLayerVisibility(model, false);
        model.renderToBuffer(pose, buf.getBuffer(RenderType.entityCutoutNoCull(skin)),
                0xF000F0, OverlayTexture.NO_OVERLAY, r, gr, b, a);
        if (overlay) {
            setModelLayerVisibility(model, true);
            model.hat.copyFrom(model.head);
            model.jacket.copyFrom(model.body);
            model.leftSleeve.copyFrom(model.leftArm);
            model.rightSleeve.copyFrom(model.rightArm);
            model.leftPants.copyFrom(model.leftLeg);
            model.rightPants.copyFrom(model.rightLeg);
            model.renderToBuffer(pose, buf.getBuffer(RenderType.entityTranslucent(skin)),
                    0xF000F0, OverlayTexture.NO_OVERLAY, r, gr, b, a);
        }
        resetModelVisibility(model);
        pose.popPose();
        g.flush();
        restoreGlAfter3D();
    }

    private static void applyTint(int c) {
        if (c == 0 || c == 0xFFFFFFFF) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            return;
        }
        float a = ((c >>> 24) & 0xFF) / 255f;
        if (a == 0f) a = 1f;
        RenderSystem.setShaderColor(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, a);
    }

    private static void restoreGlAfter3D() {
        Lighting.setupForFlatItems();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void applyWalkAndTick(LivingEntity entity, boolean walk) {
        entity.attackAnim = entity.oAttackAnim = 0f;
        if (walk) {
            float pos = (float) ((System.nanoTime() * 1e-9) * 5.0);
            entity.walkAnimation.setSpeed(0.75f);
            entity.walkAnimation.position(pos);
        } else {
            entity.walkAnimation.setSpeed(0f);
        }
    }

    private static void ensureLevelCache(ClientLevel level) {
        if (cachedLevel != level) {
            cachedLevel = level;
            ENTITY_CACHE.clear();
            try {
                previewPlayer = new GuiPreviewPlayer(level);
            } catch (Throwable t) {
                previewPlayer = null;
            }
        }
    }

    private static Entity getOrCreateEntity(ClientLevel level, EntityElement ent) {
        String rawId = ent.target().strip().toLowerCase(Locale.ROOT);
        if (rawId.isEmpty()) rawId = "minecraft:zombie";
        if (!rawId.contains(":")) rawId = "minecraft:" + rawId;
        String key = rawId + "|" + ent.baby() + "|" + ent.nametag() + "|" + ent.nbt() + "|" + ent.equipment();
        Entity cached = ENTITY_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        ResourceLocation rl = ResourceLocation.tryParse(rawId);
        if (rl == null) return null;
        Entity created = null;
        var scriptEntDef = com.scriptcraft.registry.Bindings.current().entityById(rawId);
        if (scriptEntDef == null) {
            scriptEntDef = com.scriptcraft.script.ScriptLoader.getProgram().entity(rawId);
        }
        if (scriptEntDef != null) {
            var geoEnt = com.scriptcraft.registry.ScriptRegistries.GEO_ENTITY.get().create(level);
            if (geoEnt != null) {
                geoEnt.applyDefinition(scriptEntDef);
                created = geoEnt;
            }
        }
        if (created == null && !ForgeRegistries.ENTITY_TYPES.containsKey(rl)) {
            // Если это прямой путь к 3D Geo-модели (даже без define entity) — рендерим как GEO_ENTITY, а не дефолтную свинью!
            if (com.scriptcraft.client.geo.GeoRenderer.resolveModel(rawId) != null) {
                var geoEnt = com.scriptcraft.registry.ScriptRegistries.GEO_ENTITY.get().create(level);
                if (geoEnt != null) {
                    geoEnt.setDefId(rawId);
                    geoEnt.setModelPath(rawId);
                    geoEnt.setTexturePath(rawId);
                    geoEnt.setAnimFilePath(rawId);
                    created = geoEnt;
                }
            } else {
                return null;
            }
        }
        if (created == null) {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
            if (type == null || type == EntityType.PLAYER) return null;
            created = type.create(level);
        }
        if (created == null) return null;

        if (!ent.nbt().isBlank()) {
            try {
                CompoundTag parsed = TagParser.parseTag(ent.nbt());
                CompoundTag saved = created.saveWithoutId(new CompoundTag());
                saved.merge(parsed);
                created.load(saved);
            } catch (Exception ignored) {
            }
        }
        if (ent.baby()) {
            if (created instanceof AgeableMob ageable) {
                ageable.setBaby(true);
            } else if (created instanceof net.minecraft.world.entity.Mob mob) {
                mob.setBaby(true);
            }
        }
        if (created instanceof LivingEntity living) {
            applyEquipment(living, ent.equipment());
        }
        created.setCustomNameVisible(ent.nametag() && created.hasCustomName());
        ENTITY_CACHE.put(key, created);
        return created;
    }

    private static void applyEquipment(LivingEntity living, String spec) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            living.setItemSlot(slot, ItemStack.EMPTY);
        }
        if (spec == null || spec.isBlank()) return;
        for (String part : spec.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String slotKey = part.substring(0, eq).strip().toLowerCase(Locale.ROOT);
            String itemId = part.substring(eq + 1).strip();
            if (itemId.isEmpty()) continue;
            EquipmentSlot slot = switch (slotKey) {
                case "mainhand", "weapon", "hand" -> EquipmentSlot.MAINHAND;
                case "offhand", "shield" -> EquipmentSlot.OFFHAND;
                case "head", "helmet" -> EquipmentSlot.HEAD;
                case "chest", "chestplate" -> EquipmentSlot.CHEST;
                case "legs", "leggings" -> EquipmentSlot.LEGS;
                case "feet", "boots" -> EquipmentSlot.FEET;
                default -> null;
            };
            if (slot != null) {
                ItemStack stack = ItemStacks.tryCreate(itemId, 1);
                if (!stack.isEmpty()) {
                    living.setItemSlot(slot, stack);
                }
            }
        }
    }

    // =========================================================================
    // РАЗРЕШЕНИЕ СКИНОВ (по нику, по URL, из ресурсов) + АВТОПОДБОР ALEX / STEVE
    // =========================================================================

    public static SkinInfo resolveSkin(String rawSource) {
        Minecraft mc = Minecraft.getInstance();
        String src = rawSource == null ? "" : rawSource.strip();
        if (src.isEmpty() || "self".equalsIgnoreCase(src) || "player".equalsIgnoreCase(src)) {
            if (mc.player != null) {
                return new SkinInfo(mc.player.getSkinTextureLocation(), "slim".equals(mc.player.getModelName()), true);
            }
            return new SkinInfo(DefaultPlayerSkin.getDefaultSkin(), false, true);
        }

        // 1. Быстрая проверка онлайн-игроков в текущей сессии по нику
        if (!src.contains("/") && !src.contains(":") && !src.endsWith(".png") && mc.getConnection() != null) {
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                if (info.getProfile() != null && src.equalsIgnoreCase(info.getProfile().getName())) {
                    return new SkinInfo(info.getSkinLocation(), "slim".equals(info.getModelName()), true);
                }
            }
        }

        String cacheKey = src.startsWith("http://") || src.startsWith("https://")
                ? src
                : src.toLowerCase(Locale.ROOT);
        CachedSkin cached = SKIN_CACHE.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && (cached.ready || now < cached.retryAfterMs)) {
            return new SkinInfo(cached.texture, cached.slim, cached.ready);
        }

        // 2. Ресурс из scriptcraft/resources/assets/... или встроенных ресурспаков
        if (!src.startsWith("http://") && !src.startsWith("https://")
                && (src.contains(":") || src.contains("/") || src.endsWith(".png"))) {
            CachedSkin resSkin = loadResourceSkinSync(mc, src);
            resSkin.retryAfterMs = now + 2000L;
            SKIN_CACHE.put(cacheKey, resSkin);
            return new SkinInfo(resSkin.texture, resSkin.slim, resSkin.ready);
        }

        // 3. HTTP / HTTPS ссылка или никнейм игрока (асинхронная загрузка без фризов GUI)
        UUID fallbackUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + src).getBytes(StandardCharsets.UTF_8));
        boolean defSlim = "slim".equals(DefaultPlayerSkin.getSkinModelName(fallbackUuid));
        CachedSkin entry = cached != null ? cached : new CachedSkin(DefaultPlayerSkin.getDefaultSkin(fallbackUuid), defSlim);
        entry.retryAfterMs = now + 15000L;
        SKIN_CACHE.put(cacheKey, entry);

        boolean isUrl = src.startsWith("http://") || src.startsWith("https://");
        SKIN_POOL.submit(() -> {
            try {
                if (isUrl) {
                    loadUrlSkinAsync(mc, src, entry);
                } else {
                    loadNicknameSkinAsync(mc, src, entry);
                }
            } catch (Exception e) {
                entry.retryAfterMs = System.currentTimeMillis() + 15000L;
            }
        });
        return new SkinInfo(entry.texture, entry.slim, entry.ready);
    }

    private static CachedSkin loadResourceSkinSync(Minecraft mc, String rawPath) {
        String ns = ScriptCraft.MODID;
        String path = rawPath;
        int colon = rawPath.indexOf(':');
        if (colon >= 0) {
            ns = rawPath.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            path = rawPath.substring(colon + 1).strip().toLowerCase(Locale.ROOT);
        } else {
            path = path.toLowerCase(Locale.ROOT);
        }
        if (!path.endsWith(".png")) {
            path = path + ".png";
        }

        String[] candidates = path.startsWith("textures/")
                ? new String[]{path}
                : new String[]{path, "textures/" + path, "textures/skins/" + path, "textures/gui/" + path, "textures/entity/" + path};

        // Сначала проверяем синхронизированный кэш ресурсов ScriptCraft (работает без перезагрузки ресурспаков!)
        Path cacheRoot = ModPaths.cache();
        for (String cand : candidates) {
            Path file = cacheRoot.resolve("assets/" + ns + "/" + cand);
            if (Files.isRegularFile(file)) {
                try {
                    byte[] bytes = Files.readAllBytes(file);
                    NativeImage rawImg = NativeImage.read(new ByteArrayInputStream(bytes));
                    NativeImage normalized = normalizeSkinImage(rawImg);
                    boolean slim = detectSlimFromImage(normalized);
                    ResourceLocation dynId = registerDynamicSkin(mc, "res_" + sha1Short(ns + ":" + cand), normalized);
                    CachedSkin ok = new CachedSkin(dynId, slim);
                    ok.ready = true;
                    return ok;
                } catch (Exception ignored) {
                }
            }
        }

        // Затем проверяем ResourceManager Minecraft (ванильные или ресурспаковые скины)
        for (String cand : candidates) {
            ResourceLocation rl = ResourceLocation.tryBuild(ns, cand);
            if (rl == null) continue;
            Optional<Resource> res = mc.getResourceManager().getResource(rl);
            if (res.isPresent()) {
                try (InputStream in = res.get().open()) {
                    NativeImage rawImg = NativeImage.read(in);
                    NativeImage normalized = normalizeSkinImage(rawImg);
                    boolean slim = detectSlimFromImage(normalized);
                    ResourceLocation outLoc = (normalized != rawImg)
                            ? registerDynamicSkin(mc, "res64_" + sha1Short(rl.toString()), normalized)
                            : rl;
                    if (normalized == rawImg) {
                        normalized.close();
                    }
                    CachedSkin ok = new CachedSkin(outLoc, slim);
                    ok.ready = true;
                    return ok;
                } catch (Exception ignored) {
                }
            }
        }

        CachedSkin miss = new CachedSkin(DefaultPlayerSkin.getDefaultSkin(), false);
        miss.ready = false;
        return miss;
    }

    private static void loadUrlSkinAsync(Minecraft mc, String url, CachedSkin target) throws Exception {
        byte[] bytes = downloadBytes(url, 2 * 1024 * 1024);
        NativeImage rawImg = NativeImage.read(new ByteArrayInputStream(bytes));
        NativeImage normalized = normalizeSkinImage(rawImg);
        boolean slim = detectSlimFromImage(normalized);
        mc.execute(() -> {
            ResourceLocation loc = registerDynamicSkin(mc, "url_" + sha1Short(url), normalized);
            target.texture = loc;
            target.slim = slim;
            target.ready = true;
        });
    }

    private static void loadNicknameSkinAsync(Minecraft mc, String nick, CachedSkin target) throws Exception {
        String cleanNick = nick.replaceAll("[^A-Za-z0-9_]", "");
        if (cleanNick.isEmpty() || cleanNick.length() > 16) {
            target.ready = true;
            return;
        }
        String profileResp = new String(downloadBytes("https://api.mojang.com/users/profiles/minecraft/" + cleanNick, 65536), StandardCharsets.UTF_8);
        JsonObject idObj = JsonParser.parseString(profileResp).getAsJsonObject();
        if (!idObj.has("id")) {
            target.ready = true;
            return;
        }
        String uuidHex = idObj.get("id").getAsString();
        String sessionResp = new String(downloadBytes("https://sessionserver.mojang.com/session/minecraft/profile/" + uuidHex, 131072), StandardCharsets.UTF_8);
        JsonObject sessionObj = JsonParser.parseString(sessionResp).getAsJsonObject();
        String encoded = null;
        if (sessionObj.has("properties")) {
            for (var el : sessionObj.getAsJsonArray("properties")) {
                JsonObject prop = el.getAsJsonObject();
                if ("textures".equals(prop.get("name").getAsString())) {
                    encoded = prop.get("value").getAsString();
                    break;
                }
            }
        }
        if (encoded == null) {
            target.ready = true;
            return;
        }
        String decoded = new String(Base64.getMimeDecoder().decode(encoded), StandardCharsets.UTF_8);
        JsonObject texRoot = JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures");
        if (texRoot == null || !texRoot.has("SKIN")) {
            target.ready = true;
            return;
        }
        JsonObject skinObj = texRoot.getAsJsonObject("SKIN");
        String skinUrl = skinObj.get("url").getAsString();
        boolean alex = skinObj.has("metadata")
                && skinObj.getAsJsonObject("metadata").has("model")
                && "slim".equalsIgnoreCase(skinObj.getAsJsonObject("metadata").get("model").getAsString());
        Map<String, String> meta = alex ? Map.of("model", "slim") : Map.of();
        mc.execute(() -> {
            ResourceLocation loc = mc.getSkinManager()
                    .registerTexture(new MinecraftProfileTexture(skinUrl, meta), MinecraftProfileTexture.Type.SKIN);
            target.texture = loc;
            target.slim = alex;
            target.ready = true;
        });
    }

    private static byte[] downloadBytes(String address, int maxBytes) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(7000);
        conn.setReadTimeout(7000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "ScriptCraft/1.20.1");
        try (InputStream in = conn.getInputStream()) {
            byte[] data = in.readNBytes(maxBytes + 1);
            if (data.length > maxBytes) {
                throw new IllegalStateException("Skin file exceeds size limit");
            }
            return data;
        } finally {
            conn.disconnect();
        }
    }

    @SuppressWarnings("removal")
    private static ResourceLocation registerDynamicSkin(Minecraft mc, String key, NativeImage img) {
        ResourceLocation id = new ResourceLocation(ScriptCraft.MODID, "gui_skin/" + key + "_" + (++dynamicSkinCounter));
        mc.getTextureManager().register(id, new DynamicTexture(img));
        return id;
    }

    /** Конвертирует старый формат скина 64×32 в современный 64×64, чтобы обе руки и ноги рендерились корректно. */
    private static NativeImage normalizeSkinImage(NativeImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (w == 64 && h == 32) {
            NativeImage dst = new NativeImage(64, 64, true);
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 64; x++) {
                    dst.setPixelRGBA(x, y, img.getPixelRGBA(x, y));
                }
            }
            dst.copyRect(4, 16, 16, 32, 4, 4, true, false);
            dst.copyRect(8, 16, 16, 32, 4, 4, true, false);
            dst.copyRect(0, 20, 24, 32, 4, 12, true, false);
            dst.copyRect(4, 20, 16, 32, 4, 12, true, false);
            dst.copyRect(8, 20, 8, 32, 4, 12, true, false);
            dst.copyRect(12, 20, 16, 32, 4, 12, true, false);
            dst.copyRect(44, 16, -8, 32, 4, 4, true, false);
            dst.copyRect(48, 16, -8, 32, 4, 4, true, false);
            dst.copyRect(40, 20, 0, 32, 4, 12, true, false);
            dst.copyRect(44, 20, -8, 32, 4, 12, true, false);
            dst.copyRect(48, 20, -16, 32, 4, 12, true, false);
            dst.copyRect(52, 20, -8, 32, 4, 12, true, false);
            img.close();
            return dst;
        }
        return img;
    }

    /**
     * Автоматически определяет модель Алекс (slim, 3px руки) по прозрачности полосы (54..55, 20..31) на 64×64 PNG-скине.
     */
    public static boolean detectSlimFromImage(NativeImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (w < 64 || h < 64 || (w % 64) != 0 || w != h) {
            return false;
        }
        int k = w / 64;
        boolean hasArmPixels = ((img.getPixelRGBA(44 * k, 20 * k) >>> 24) & 0xFF) > 0;
        if (!hasArmPixels) {
            return false;
        }
        for (int y = 20 * k; y < 32 * k; y++) {
            for (int x = 54 * k; x < 56 * k; x++) {
                if (((img.getPixelRGBA(x, y) >>> 24) & 0xFF) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String sha1Short(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private static PlayerModel<?> getFallbackModel(Minecraft mc, boolean slim) {
        if (slim) {
            if (fallbackSlim == null) {
                fallbackSlim = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
            }
            return fallbackSlim;
        }
        if (fallbackWide == null) {
            fallbackWide = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        }
        return fallbackWide;
    }

    private static void setModelLayerVisibility(PlayerModel<?> m, boolean overlay) {
        m.head.visible = !overlay;
        m.body.visible = !overlay;
        m.leftArm.visible = !overlay;
        m.rightArm.visible = !overlay;
        m.leftLeg.visible = !overlay;
        m.rightLeg.visible = !overlay;
        m.hat.visible = overlay;
        m.jacket.visible = overlay;
        m.leftSleeve.visible = overlay;
        m.rightSleeve.visible = overlay;
        m.leftPants.visible = overlay;
        m.rightPants.visible = overlay;
    }

    private static void resetModelVisibility(PlayerModel<?> m) {
        m.head.visible = m.body.visible = m.leftArm.visible = m.rightArm.visible = m.leftLeg.visible = m.rightLeg.visible = true;
        m.hat.visible = m.jacket.visible = m.leftSleeve.visible = m.rightSleeve.visible = m.leftPants.visible = m.rightPants.visible = true;
    }
}
