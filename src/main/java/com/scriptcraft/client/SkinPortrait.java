package com.scriptcraft.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.scriptcraft.ModPaths;
import com.scriptcraft.ScriptCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Медленно крутящаяся модель скина ElgerElg. Пока файл не скачан, стоит стандартный скин.
 */
@OnlyIn(Dist.CLIENT)
public final class SkinPortrait {
    private static final String NAME = "ElgerElg";
    private static final ResourceLocation FALLBACK = DefaultPlayerSkin.getDefaultSkin();
    private static final ResourceLocation CUSTOM = new ResourceLocation(ScriptCraft.MODID, "studio/elgerelg");
    private static volatile boolean started;
    private static volatile boolean custom;
    private static volatile boolean slim;
    private static PlayerModel<?> wide;
    private static PlayerModel<?> thin;

    private SkinPortrait() {
    }

    /**
     * Модель стоит ногами вниз: в кубах игрока плюс по Y — это вниз, и для экрана
     * дополнительный переворот не нужен. Минус по Z разворачивает лицо к камере.
     */
    public static void render(GuiGraphics graphics, int centerX, int top, float scale) {
        ensure();
        PlayerModel<?> model = model();
        if (model == null) {
            return;
        }
        float yaw = (System.currentTimeMillis() % 14000L) / 14000f * 360f;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, top, 120);
        pose.scale(scale, scale, -scale);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        Lighting.setupForEntityInInventory();
        var buffer = graphics.bufferSource().getBuffer(RenderType.entityTranslucent(custom ? CUSTOM : FALLBACK));
        model.renderToBuffer(pose, buffer, 0xF000F0, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        pose.popPose();
        graphics.flush();
        Lighting.setupFor3DItems();
    }

    private static PlayerModel<?> model() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getEntityModels() == null) {
            return null;
        }
        boolean useSlim = custom && slim;
        if (useSlim) {
            if (thin == null) {
                thin = prepare(new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true));
            }
            return thin;
        }
        if (wide == null) {
            wide = prepare(new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false));
        }
        return wide;
    }

    private static PlayerModel<?> prepare(PlayerModel<?> model) {
        model.hat.visible = true;
        model.jacket.visible = true;
        model.leftSleeve.visible = true;
        model.rightSleeve.visible = true;
        model.leftPants.visible = true;
        model.rightPants.visible = true;
        return model;
    }

    private static void ensure() {
        if (started) {
            return;
        }
        started = true;
        Thread thread = new Thread(SkinPortrait::download, "scriptcraft-skin");
        thread.setDaemon(true);
        thread.start();
    }

    private static void download() {
        try {
            Path cache = ModPaths.cache().resolve("skins").resolve("elgerelg.png");
            if (Files.isRegularFile(cache) && Files.size(cache) < 100) {
                Files.deleteIfExists(cache);
            }
            if (!Files.isRegularFile(cache)) {
                String profile = http("https://api.mojang.com/users/profiles/minecraft/" + NAME);
                String id = between(profile, "\"id\":\"", "\"");
                if (id == null || id.isBlank()) {
                    return;
                }
                String session = http("https://sessionserver.mojang.com/session/minecraft/profile/" + id);
                String value = between(session, "\"value\":\"", "\"");
                if (value == null) {
                    return;
                }
                String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
                slim = decoded.contains("\"model\":\"slim\"") || decoded.contains("\"model\": \"slim\"");
                String url = skinUrl(decoded);
                if (url == null) {
                    return;
                }
                byte[] png = bytes(url);
                Files.createDirectories(cache.getParent());
                Files.write(cache, png);
            }
            byte[] png = Files.readAllBytes(cache);
            Minecraft.getInstance().execute(() -> upload(png));
        } catch (Exception e) {
            ScriptCraft.LOGGER.info("[ScriptCraft] Скин {} не загрузился, остаётся стандартная модель: {}", NAME, e.getMessage());
        }
    }

    private static void upload(byte[] png) {
        try {
            NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(png));
            Minecraft.getInstance().getTextureManager().register(CUSTOM, new DynamicTexture(image));
            custom = true;
            wide = null;
            thin = null;
        } catch (Exception e) {
            ScriptCraft.LOGGER.info("[ScriptCraft] Не разобрать скин {}: {}", NAME, e.getMessage());
        }
    }

    private static String http(String url) throws Exception {
        return new String(bytes(url), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(4000);
        connection.setRequestProperty("User-Agent", "ScriptCraft");
        try (InputStream in = connection.getInputStream()) {
            return in.readAllBytes();
        }
    }

    /** Адрес скина с textures.minecraft.net. В JSON слэши иногда экранированы. */
    private static String skinUrl(String decoded) {
        String flat = decoded.replace("\\/", "/");
        int skin = flat.indexOf("\"SKIN\"");
        String area = skin >= 0 ? flat.substring(skin) : flat;
        String url = between(area, "\"url\":\"", "\"");
        if (url == null) {
            return null;
        }
        url = url.replace("\\/", "/");
        if (url.startsWith("http://textures.minecraft.net/") || url.startsWith("https://textures.minecraft.net/")) {
            return url;
        }
        return null;
    }

    private static String between(String text, String from, String to) {
        int start = text.indexOf(from);
        if (start < 0) {
            return null;
        }
        start += from.length();
        int end = text.indexOf(to, start);
        if (end < 0) {
            return null;
        }
        return text.substring(start, end);
    }
}
