package com.scriptcraft.screen;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * 3D-рендер сущности, игрока или кастомного скина в GUI ({@code open screen}) и HUD ({@code create hud}).
 * Общая модель данных без клиентских классов Minecraft (безопасна на выделенном сервере).
 */
public record EntityElement(
        Layout layout,
        boolean playerMode,
        String target,
        String skin,
        ModelStyle modelStyle,
        boolean showOverlay,
        float scale,
        float yaw,
        float pitch,
        float roll,
        float spinSpeed,
        LookMode lookMode,
        int lookX,
        int lookY,
        boolean clip,
        boolean baby,
        boolean sneaking,
        boolean walk,
        boolean nametag,
        String nbt,
        String equipment,
        String action
) implements ScreenElement {

    public static final int DEFAULT_WIDTH = 64;
    public static final int DEFAULT_HEIGHT = 96;

    public enum ModelStyle {
        AUTO, WIDE, SLIM;

        public static ModelStyle byName(String raw) {
            if (raw == null) return null;
            return switch (raw.strip().toLowerCase(Locale.ROOT)) {
                case "auto", "detect" -> AUTO;
                case "wide", "steve", "classic", "default" -> WIDE;
                case "slim", "alex", "thin" -> SLIM;
                default -> null;
            };
        }
    }

    public enum LookMode {
        NONE, CURSOR, POINT;

        public static LookMode byName(String raw) {
            if (raw == null) return null;
            return switch (raw.strip().toLowerCase(Locale.ROOT)) {
                case "none", "fixed", "off" -> NONE;
                case "cursor", "mouse" -> CURSOR;
                case "point", "at", "target" -> POINT;
                default -> null;
            };
        }
    }

    @Override
    public ElementType type() {
        return ElementType.ENTITY;
    }

    @Override
    public void writeData(FriendlyByteBuf buf) {
        buf.writeBoolean(playerMode);
        buf.writeUtf(target);
        buf.writeUtf(skin);
        buf.writeEnum(modelStyle);
        buf.writeBoolean(showOverlay);
        buf.writeFloat(scale);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeFloat(roll);
        buf.writeFloat(spinSpeed);
        buf.writeEnum(lookMode);
        buf.writeVarInt(lookX);
        buf.writeVarInt(lookY);
        buf.writeBoolean(clip);
        buf.writeBoolean(baby);
        buf.writeBoolean(sneaking);
        buf.writeBoolean(walk);
        buf.writeBoolean(nametag);
        buf.writeUtf(nbt);
        buf.writeUtf(equipment);
        buf.writeUtf(action);
    }

    public static EntityElement readData(Layout layout, FriendlyByteBuf buf) {
        boolean playerMode = buf.readBoolean();
        String target = buf.readUtf();
        String skin = buf.readUtf();
        ModelStyle modelStyle = buf.readEnum(ModelStyle.class);
        boolean showOverlay = buf.readBoolean();
        float scale = buf.readFloat();
        float yaw = buf.readFloat();
        float pitch = buf.readFloat();
        float roll = buf.readFloat();
        float spinSpeed = buf.readFloat();
        LookMode lookMode = buf.readEnum(LookMode.class);
        int lookX = buf.readVarInt();
        int lookY = buf.readVarInt();
        boolean clip = buf.readBoolean();
        boolean baby = buf.readBoolean();
        boolean sneaking = buf.readBoolean();
        boolean walk = buf.readBoolean();
        boolean nametag = buf.readBoolean();
        String nbt = buf.readUtf();
        String equipment = buf.readUtf();
        String action = buf.readUtf();
        return new EntityElement(layout, playerMode, target, skin, modelStyle, showOverlay,
                scale, yaw, pitch, roll, spinSpeed, lookMode, lookX, lookY,
                clip, baby, sneaking, walk, nametag, nbt, equipment, action);
    }

    @Override
    public ScreenElement format(UnaryOperator<String> formatter) {
        return new EntityElement(
                layout,
                playerMode,
                formatter.apply(target),
                formatter.apply(skin),
                modelStyle,
                showOverlay,
                scale,
                yaw,
                pitch,
                roll,
                spinSpeed,
                lookMode,
                lookX,
                lookY,
                clip,
                baby,
                sneaking,
                walk,
                nametag,
                formatter.apply(nbt),
                formatter.apply(equipment),
                formatter.apply(action)
        );
    }

    public EntityElement withLayout(Layout next) {
        return new EntityElement(next, playerMode, target, skin, modelStyle, showOverlay,
                scale, yaw, pitch, roll, spinSpeed, lookMode, lookX, lookY,
                clip, baby, sneaking, walk, nametag, nbt, equipment, action);
    }

    public EntityElement withAction(String nextAction) {
        return new EntityElement(layout, playerMode, target, skin, modelStyle, showOverlay,
                scale, yaw, pitch, roll, spinSpeed, lookMode, lookX, lookY,
                clip, baby, sneaking, walk, nametag, nbt, equipment, nextAction);
    }

    public EntityElement withSkin(String nextSkin) {
        return new EntityElement(layout, true, target.isEmpty() ? nextSkin : target, nextSkin, modelStyle, showOverlay,
                scale, yaw, pitch, roll, spinSpeed, lookMode, lookX, lookY,
                clip, baby, sneaking, walk, nametag, nbt, equipment, action);
    }

    public EntityElement withTarget(String nextTarget, boolean nextPlayerMode) {
        return new EntityElement(layout, nextPlayerMode, nextTarget, nextPlayerMode ? skin : "", modelStyle, showOverlay,
                scale, yaw, pitch, roll, spinSpeed, lookMode, lookX, lookY,
                clip, baby, sneaking, walk, nametag, nbt, equipment, action);
    }

    public EntityElement withScaleVal(float nextScale) {
        return new EntityElement(layout, playerMode, target, skin, modelStyle, showOverlay,
                Math.max(1f, Math.min(400f, nextScale)), yaw, pitch, roll, spinSpeed, lookMode, lookX, lookY,
                clip, baby, sneaking, walk, nametag, nbt, equipment, action);
    }

    public EntityElement withYawVal(float nextYaw) {
        return new EntityElement(layout, playerMode, target, skin, modelStyle, showOverlay,
                scale, nextYaw, pitch, roll, spinSpeed, lookMode, lookX, lookY,
                clip, baby, sneaking, walk, nametag, nbt, equipment, action);
    }

    public EntityElement withSpin(float nextSpinSpeed) {
        return new EntityElement(layout, playerMode, target, skin, modelStyle, showOverlay,
                scale, yaw, pitch, roll, nextSpinSpeed, lookMode, lookX, lookY,
                clip, baby, sneaking, walk, nametag, nbt, equipment, action);
    }

    public EntityElement withLook(LookMode nextMode, int lx, int ly) {
        return new EntityElement(layout, playerMode, target, skin, modelStyle, showOverlay,
                scale, yaw, pitch, roll, spinSpeed, nextMode, lx, ly,
                clip, baby, sneaking, walk, nametag, nbt, equipment, action);
    }
}
