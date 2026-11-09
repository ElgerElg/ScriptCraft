package com.scriptcraft.client;

import com.scriptcraft.ModPaths;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Настройки журнала и уведомлений на этом клиенте.
 * Файл scriptcraft/client-ui.properties, не Forge-конфиг и не меню «Управление»:
 * клавиша там не появляется, её меняет шестерёнка в журнале.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientUiSettings {
    public enum Place {
        CHAT, TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT;

        static Place parse(String raw) {
            if (raw == null) {
                return CHAT;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return CHAT;
            }
        }
    }

    private static boolean loaded;
    private static int openKey = GLFW.GLFW_KEY_GRAVE_ACCENT;
    private static boolean withF3 = true;
    private static float seconds = 4f;
    private static float scale = 1f;
    private static Place place = Place.CHAT;

    private ClientUiSettings() {
    }

    public static int openKey() {
        load();
        return openKey;
    }

    public static boolean withF3() {
        load();
        return withF3;
    }

    public static float seconds() {
        load();
        return seconds;
    }

    public static float scale() {
        load();
        return scale;
    }

    public static Place place() {
        load();
        return place;
    }

    public static void setOpenKey(int key, boolean f3) {
        load();
        if (key == GLFW.GLFW_KEY_UNKNOWN || key == GLFW.GLFW_KEY_F3) {
            return;
        }
        openKey = key;
        withF3 = f3;
        save();
    }

    public static void setSeconds(float value) {
        load();
        seconds = clamp(value, 1.5f, 10f);
        save();
    }

    public static void setScale(float value) {
        load();
        scale = clamp(value, 0.7f, 1.5f);
        save();
    }

    public static void setPlace(Place value) {
        load();
        place = value;
        save();
    }

    /** Подпись клавиши для журнала: «F3 + `». */
    public static String keyLabel() {
        load();
        String name = keyName(openKey);
        return withF3 ? "F3 + " + name : name;
    }

    public static String keyName(int key) {
        if (key == GLFW.GLFW_KEY_GRAVE_ACCENT) {
            return "`";
        }
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) {
            return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        }
        if (key == GLFW.GLFW_KEY_SPACE) {
            return "Space";
        }
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            return String.valueOf((char) ('A' + (key - GLFW.GLFW_KEY_A)));
        }
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            return String.valueOf((char) ('0' + (key - GLFW.GLFW_KEY_0)));
        }
        String named = GLFW.glfwGetKeyName(key, 0);
        return named == null ? "#" + key : named.toUpperCase(Locale.ROOT);
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return;
        }
        Properties p = new Properties();
        try (var in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            return;
        }
        openKey = parseInt(p.getProperty("openKey"), openKey);
        withF3 = Boolean.parseBoolean(p.getProperty("withF3", "true"));
        seconds = clamp(parseFloat(p.getProperty("seconds"), seconds), 1.5f, 10f);
        scale = clamp(parseFloat(p.getProperty("scale"), scale), 0.7f, 1.5f);
        place = Place.parse(p.getProperty("place"));
    }

    private static void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            String text = "openKey=" + openKey + "\n"
                    + "withF3=" + withF3 + "\n"
                    + "seconds=" + seconds + "\n"
                    + "scale=" + scale + "\n"
                    + "place=" + place.name() + "\n";
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // журнал всё равно открывается, просто настройка не переживёт перезапуск
        }
    }

    private static Path file() {
        return ModPaths.root().resolve("client-ui.properties");
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static float parseFloat(String raw, float fallback) {
        try {
            return Float.parseFloat(raw.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
