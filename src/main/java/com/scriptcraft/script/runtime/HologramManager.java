package com.scriptcraft.script.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 3D-голограммы в мире на базе нативных сущностей TextDisplay (Minecraft 1.20.1).
 * Позволяет создавать, обновлять текст, перемещать и удалять голограммы по строковому id.
 */
public final class HologramManager {
    private static final Map<String, UUID> HOLOGRAMS = new ConcurrentHashMap<>();

    private HologramManager() {
    }

    public static void create(MinecraftServer server, ServerLevel level, String id, String text,
                              double x, double y, double z, float scale, String billboard) {
        if (server == null || level == null || id == null || id.isBlank()) {
            return;
        }
        String key = id.strip().toLowerCase(Locale.ROOT);
        remove(server, key);

        Display.TextDisplay display = EntityType.TEXT_DISPLAY.create(level);
        if (display == null) {
            return;
        }
        display.setPos(x, y, z);
        display.addTag("sc_holo");
        display.addTag("sc_holo:" + key);
        display.setNoGravity(true);
        display.setInvulnerable(true);

        CompoundTag nbt = new CompoundTag();
        display.saveWithoutId(nbt);
        applyTextNbt(nbt, text);
        nbt.putString("billboard", normalizeBillboard(billboard));
        nbt.putBoolean("shadow", true);
        nbt.putBoolean("see_through", false);
        nbt.putInt("line_width", 260);
        nbt.putInt("background", 0x66000000);
        applyScaleNbt(nbt, scale <= 0 ? 1.0f : Math.min(10.0f, scale));
        display.load(nbt);

        level.addFreshEntity(display);
        HOLOGRAMS.put(key, display.getUUID());
    }

    public static void setText(MinecraftServer server, String id, String text) {
        Display.TextDisplay display = find(server, id);
        if (display == null) {
            return;
        }
        CompoundTag nbt = new CompoundTag();
        display.saveWithoutId(nbt);
        applyTextNbt(nbt, text);
        display.load(nbt);
    }

    public static void move(MinecraftServer server, ServerLevel targetLevel, String id, double x, double y, double z) {
        Display.TextDisplay display = find(server, id);
        if (display == null) {
            return;
        }
        if (targetLevel != null && display.level() != targetLevel) {
            CompoundTag nbt = new CompoundTag();
            display.saveWithoutId(nbt);
            display.discard();
            Display.TextDisplay copy = EntityType.TEXT_DISPLAY.create(targetLevel);
            if (copy != null) {
                copy.load(nbt);
                copy.setPos(x, y, z);
                targetLevel.addFreshEntity(copy);
                HOLOGRAMS.put(id.strip().toLowerCase(Locale.ROOT), copy.getUUID());
            }
            return;
        }
        display.teleportTo(x, y, z);
    }

    public static void remove(MinecraftServer server, String id) {
        if (server == null || id == null || id.isBlank()) {
            return;
        }
        String key = id.strip().toLowerCase(Locale.ROOT);
        UUID uuid = HOLOGRAMS.remove(key);
        if (uuid != null) {
            for (ServerLevel lvl : server.getAllLevels()) {
                Entity e = lvl.getEntity(uuid);
                if (e != null) {
                    e.discard();
                }
            }
        }
        // На случай, если голограмма осталась после перезапуска мира — ищем по тегу
        String tag = "sc_holo:" + key;
        for (ServerLevel lvl : server.getAllLevels()) {
            for (Entity e : lvl.getAllEntities()) {
                if (e instanceof Display.TextDisplay && e.getTags().contains(tag)) {
                    e.discard();
                }
            }
        }
    }

    public static void clearAll(MinecraftServer server) {
        HOLOGRAMS.clear();
        if (server == null) {
            return;
        }
        for (ServerLevel lvl : server.getAllLevels()) {
            for (Entity e : lvl.getAllEntities()) {
                if (e instanceof Display.TextDisplay && e.getTags().contains("sc_holo")) {
                    e.discard();
                }
            }
        }
    }

    private static Display.TextDisplay find(MinecraftServer server, String id) {
        if (server == null || id == null || id.isBlank()) {
            return null;
        }
        String key = id.strip().toLowerCase(Locale.ROOT);
        UUID uuid = HOLOGRAMS.get(key);
        if (uuid != null) {
            for (ServerLevel lvl : server.getAllLevels()) {
                Entity e = lvl.getEntity(uuid);
                if (e instanceof Display.TextDisplay td && !td.isRemoved()) {
                    return td;
                }
            }
        }
        String tag = "sc_holo:" + key;
        for (ServerLevel lvl : server.getAllLevels()) {
            for (Entity e : lvl.getAllEntities()) {
                if (e instanceof Display.TextDisplay td && td.getTags().contains(tag)) {
                    HOLOGRAMS.put(key, td.getUUID());
                    return td;
                }
            }
        }
        return null;
    }

    private static void applyTextNbt(CompoundTag nbt, String rawText) {
        String normalized = (rawText == null ? "" : rawText).replace("\\n", "\n");
        Component comp = Effects.colored(normalized);
        nbt.putString("text", Component.Serializer.toJson(comp));
    }

    private static void applyScaleNbt(CompoundTag nbt, float s) {
        CompoundTag tr = nbt.contains("transformation") ? nbt.getCompound("transformation") : new CompoundTag();
        ListTag scaleList = new ListTag();
        scaleList.add(FloatTag.valueOf(s));
        scaleList.add(FloatTag.valueOf(s));
        scaleList.add(FloatTag.valueOf(s));
        tr.put("scale", scaleList);
        if (!tr.contains("translation")) {
            ListTag t = new ListTag();
            t.add(FloatTag.valueOf(0f));
            t.add(FloatTag.valueOf(0f));
            t.add(FloatTag.valueOf(0f));
            tr.put("translation", t);
        }
        if (!tr.contains("left_rotation")) {
            ListTag r = new ListTag();
            r.add(FloatTag.valueOf(0f));
            r.add(FloatTag.valueOf(0f));
            r.add(FloatTag.valueOf(0f));
            r.add(FloatTag.valueOf(1f));
            tr.put("left_rotation", r);
        }
        if (!tr.contains("right_rotation")) {
            ListTag r = new ListTag();
            r.add(FloatTag.valueOf(0f));
            r.add(FloatTag.valueOf(0f));
            r.add(FloatTag.valueOf(0f));
            r.add(FloatTag.valueOf(1f));
            tr.put("right_rotation", r);
        }
        nbt.put("transformation", tr);
    }

    private static String normalizeBillboard(String raw) {
        if (raw == null || raw.isBlank()) {
            return "center";
        }
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "fixed", "vertical", "horizontal", "center" -> raw.strip().toLowerCase(Locale.ROOT);
            default -> "center";
        };
    }
}
