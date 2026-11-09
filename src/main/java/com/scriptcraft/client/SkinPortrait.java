package com.scriptcraft.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Крутящаяся модель скина игрока, который уже открыл студию.
 * Текстуру берём у клиента: её скачал сам Minecraft, отдельный запрос к Mojang не нужен.
 */
@OnlyIn(Dist.CLIENT)
public final class SkinPortrait {
    private static PlayerModel<?> wide;
    private static PlayerModel<?> thin;

    private SkinPortrait() {
    }

    /**
     * Модель стоит ногами вниз: в кубах игрока плюс по Y — это вниз.
     * Минус по Z разворачивает лицо к камере. scale — пиксели на блок, рост модели около 2 блоков.
     */
    public static void render(GuiGraphics graphics, int centerX, int top, float scale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getEntityModels() == null) {
            return;
        }
        boolean slim = false;
        ResourceLocation skin = DefaultPlayerSkin.getDefaultSkin();
        if (mc.player != null) {
            AbstractClientPlayer player = mc.player;
            skin = player.getSkinTextureLocation();
            slim = "slim".equals(player.getModelName());
        }
        PlayerModel<?> model = model(mc, slim);
        float yaw = (System.currentTimeMillis() % 14000L) / 14000f * 360f;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, top, 120);
        pose.scale(scale, scale, -scale);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        Lighting.setupForEntityInInventory();
        var buffer = graphics.bufferSource().getBuffer(RenderType.entityTranslucent(skin));
        model.renderToBuffer(pose, buffer, 0xF000F0, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        pose.popPose();
        graphics.flush();
        Lighting.setupFor3DItems();
    }

    private static PlayerModel<?> model(Minecraft mc, boolean slim) {
        if (slim) {
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
}
