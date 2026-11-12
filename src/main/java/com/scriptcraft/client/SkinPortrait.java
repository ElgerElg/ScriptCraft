package com.scriptcraft.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Крутящаяся модель автора мода. Скин качается по нику ElgerElg, а не у того, кто открыл студию.
 * Голова и тело — обычный слой, куртка и шляпа — второй проход со сдвигом по глубине, иначе оверлей тонет в голове.
 */
@OnlyIn(Dist.CLIENT)
public final class SkinPortrait {
    private static final String AUTHOR = "ElgerElg";

    private static PlayerModel<?> wide;
    private static PlayerModel<?> thin;
    private static volatile ResourceLocation skin = DefaultPlayerSkin.getDefaultSkin();
    private static volatile boolean slim;
    private static volatile boolean ready;
    private static volatile long nextTry;
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private SkinPortrait() {
    }

    /**
     * Модель стоит ногами вниз: в кубах игрока плюс по Y — это вниз.
     * Минус по Z разворачивает лицо к камере. scale — пиксели на блок, рост около 2 блоков.
     */
    public static void render(GuiGraphics graphics, int centerX, int top, float scale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getEntityModels() == null) {
            return;
        }
        ensureSkin();
        PlayerModel<?> model = model(mc, slim);
        model.young = false;
        float yaw = (System.currentTimeMillis() % 14000L) / 14000f * 360f;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, top, 120);
        pose.scale(scale, scale, -scale);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        Lighting.setupForEntityInInventory();
        base(model, false);
        var buffer = graphics.bufferSource();
        model.renderToBuffer(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(skin)),
                0xF000F0, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        base(model, true);
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
        model.renderToBuffer(pose, buffer.getBuffer(RenderType.entityCutoutNoCullZOffset(skin)),
                0xF000F0, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        showAll(model);
        pose.popPose();
        graphics.flush();
        Lighting.setupFor3DItems();
    }

    /** false — только тело и голова, true — только второй слой скина. */
    private static void base(PlayerModel<?> model, boolean overlay) {
        model.head.visible = !overlay;
        model.body.visible = !overlay;
        model.leftArm.visible = !overlay;
        model.rightArm.visible = !overlay;
        model.leftLeg.visible = !overlay;
        model.rightLeg.visible = !overlay;
        model.hat.visible = overlay;
        model.jacket.visible = overlay;
        model.leftSleeve.visible = overlay;
        model.rightSleeve.visible = overlay;
        model.leftPants.visible = overlay;
        model.rightPants.visible = overlay;
    }

    private static void showAll(PlayerModel<?> model) {
        model.head.visible = true;
        model.body.visible = true;
        model.leftArm.visible = true;
        model.rightArm.visible = true;
        model.leftLeg.visible = true;
        model.rightLeg.visible = true;
        model.hat.visible = true;
        model.jacket.visible = true;
        model.leftSleeve.visible = true;
        model.rightSleeve.visible = true;
        model.leftPants.visible = true;
        model.rightPants.visible = true;
    }

    private static void ensureSkin() {
        if (ready || System.currentTimeMillis() < nextTry || !BUSY.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                loadAuthorSkin();
            } catch (Exception ignored) {
                nextTry = System.currentTimeMillis() + 10000L;
                BUSY.set(false);
            }
        }, "ScriptCraft-Skin");
        thread.setDaemon(true);
        thread.start();
    }

    private static void loadAuthorSkin() throws Exception {
        String id = JsonParser.parseString(read("https://api.mojang.com/users/profiles/minecraft/" + AUTHOR))
                .getAsJsonObject().get("id").getAsString();
        JsonObject profile = JsonParser.parseString(read("https://sessionserver.mojang.com/session/minecraft/profile/" + id))
                .getAsJsonObject();
        String encoded = null;
        for (var element : profile.getAsJsonArray("properties")) {
            JsonObject property = element.getAsJsonObject();
            if ("textures".equals(property.get("name").getAsString())) {
                encoded = property.get("value").getAsString();
                break;
            }
        }
        if (encoded == null) {
            throw new IllegalStateException("no textures");
        }
        String decoded = new String(Base64.getMimeDecoder().decode(encoded), StandardCharsets.UTF_8);
        JsonObject skinJson = JsonParser.parseString(decoded).getAsJsonObject()
                .getAsJsonObject("textures").getAsJsonObject("SKIN");
        String url = skinJson.get("url").getAsString();
        boolean alex = skinJson.has("metadata")
                && skinJson.getAsJsonObject("metadata").has("model")
                && "slim".equals(skinJson.getAsJsonObject("metadata").get("model").getAsString());
        Map<String, String> meta = alex ? Map.of("model", "slim") : Map.of();
        Minecraft.getInstance().execute(() -> {
            ResourceLocation location = Minecraft.getInstance().getSkinManager()
                    .registerTexture(new MinecraftProfileTexture(url, meta), MinecraftProfileTexture.Type.SKIN);
            skin = location;
            slim = alex;
            ready = true;
        });
    }

    private static String read(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("User-Agent", "ScriptCraft");
        try (InputStream in = connection.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static PlayerModel<?> model(Minecraft mc, boolean slimArms) {
        if (slimArms) {
            if (thin == null) {
                thin = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
            }
            return thin;
        }
        if (wide == null) {
            wide = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        }
        return wide;
    }
}
