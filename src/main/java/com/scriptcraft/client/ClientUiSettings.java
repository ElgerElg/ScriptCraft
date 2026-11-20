package com.scriptcraft.client;

import com.scriptcraft.ModPaths;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Настройки журнала, уведомлений и редактора на этом клиенте.
 * Файл scriptcraft/client-ui.properties, не Forge-конфиг и не меню «Управление».
 */
@OnlyIn(Dist.CLIENT)
public final class ClientUiSettings {
    public record KeyBind(int key, boolean ctrl, boolean shift, boolean alt) {
        public boolean matches(int k, boolean c, boolean s, boolean a) {
            return key == k && ctrl == c && shift == s && alt == a;
        }

        public String label() {
            StringBuilder sb = new StringBuilder();
            if (ctrl) sb.append("Ctrl+");
            if (alt) sb.append("Alt+");
            if (shift) sb.append("Shift+");
            sb.append(keyName(key));
            return sb.toString();
        }

        String encode() {
            return key + ":" + (ctrl ? 1 : 0) + ":" + (shift ? 1 : 0) + ":" + (alt ? 1 : 0);
        }

        static KeyBind decode(String raw, KeyBind fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            String[] p = raw.trim().split(":");
            if (p.length != 4) {
                return fallback;
            }
            try {
                return new KeyBind(Integer.parseInt(p[0]), "1".equals(p[1]), "1".equals(p[2]), "1".equals(p[3]));
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
    }

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
    private static boolean autocomplete = true;
    private static boolean autoClose = true;
    private static boolean sidebarRight = false;
    private static boolean sidebarOpen = true;
    private static int sidebarWidth = 156;
    private static final Map<String, KeyBind> DEFAULTS = defaultBinds();
    private static final Map<String, KeyBind> BINDS = new LinkedHashMap<>(DEFAULTS);

    private static Map<String, KeyBind> defaultBinds() {
        Map<String, KeyBind> m = new LinkedHashMap<>();
        m.put("save", new KeyBind(GLFW.GLFW_KEY_S, true, false, false));
        m.put("saveAll", new KeyBind(GLFW.GLFW_KEY_S, true, true, false));
        m.put("reload", new KeyBind(GLFW.GLFW_KEY_R, true, false, false));
        m.put("find", new KeyBind(GLFW.GLFW_KEY_F, true, false, false));
        m.put("replace", new KeyBind(GLFW.GLFW_KEY_H, true, false, false));
        m.put("undo", new KeyBind(GLFW.GLFW_KEY_Z, true, false, false));
        m.put("redo", new KeyBind(GLFW.GLFW_KEY_Y, true, false, false));
        m.put("comment", new KeyBind(GLFW.GLFW_KEY_SLASH, true, false, false));
        m.put("duplicate", new KeyBind(GLFW.GLFW_KEY_D, true, false, false));
        m.put("gotoLine", new KeyBind(GLFW.GLFW_KEY_G, true, false, false));
        m.put("moveUp", new KeyBind(GLFW.GLFW_KEY_UP, false, false, true));
        m.put("moveDown", new KeyBind(GLFW.GLFW_KEY_DOWN, false, false, true));
        m.put("toggleSidebar", new KeyBind(GLFW.GLFW_KEY_B, true, false, false));
        m.put("autocomplete", new KeyBind(GLFW.GLFW_KEY_SPACE, true, false, false));
        return m;
    }

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

    public static boolean autocomplete() {
        load();
        return autocomplete;
    }

    public static boolean autoClose() {
        load();
        return autoClose;
    }

    public static boolean sidebarRight() {
        load();
        return sidebarRight;
    }

    public static boolean sidebarOpen() {
        load();
        return sidebarOpen;
    }

    public static int sidebarWidth() {
        load();
        return sidebarWidth;
    }

    public static Map<String, KeyBind> keyBinds() {
        load();
        return BINDS;
    }

    public static KeyBind keyBind(String id) {
        load();
        return BINDS.getOrDefault(id, DEFAULTS.get(id));
    }

    public static boolean matches(String id, int key, boolean ctrl, boolean shift, boolean alt) {
        KeyBind bind = keyBind(id);
        return bind != null && bind.matches(key, ctrl, shift, alt);
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

    public static void setAutocomplete(boolean value) {
        load();
        autocomplete = value;
        save();
    }

    public static void setAutoClose(boolean value) {
        load();
        autoClose = value;
        save();
    }

    public static void setSidebarRight(boolean value) {
        load();
        sidebarRight = value;
        save();
    }

    public static void setSidebarOpen(boolean value) {
        load();
        sidebarOpen = value;
        save();
    }

    public static void setSidebarWidth(int value) {
        load();
        sidebarWidth = Math.max(110, Math.min(320, value));
        save();
    }

    public static void setKeyBind(String id, KeyBind bind) {
        load();
        if (bind == null || bind.key() == GLFW.GLFW_KEY_UNKNOWN) {
            return;
        }
        if (DEFAULTS.containsKey(id)) {
            BINDS.put(id, bind);
            save();
        }
    }

    public static void resetKeyBinds() {
        load();
        BINDS.clear();
        BINDS.putAll(DEFAULTS);
        save();
    }

    /** Подпись клавиши для журнала: «F3 + `». */
    public static String keyLabel() {
        load();
        String name = keyName(openKey);
        return withF3 ? "F3 + " + name : name;
    }

    public static String keyName(int key) {
        if (key == GLFW.GLFW_KEY_GRAVE_ACCENT) return "`";
        if (key == GLFW.GLFW_KEY_SLASH) return "/";
        if (key == GLFW.GLFW_KEY_UP) return "Up";
        if (key == GLFW.GLFW_KEY_DOWN) return "Down";
        if (key == GLFW.GLFW_KEY_LEFT) return "Left";
        if (key == GLFW.GLFW_KEY_RIGHT) return "Right";
        if (key == GLFW.GLFW_KEY_TAB) return "Tab";
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) return "Enter";
        if (key == GLFW.GLFW_KEY_BACKSPACE) return "Backspace";
        if (key == GLFW.GLFW_KEY_DELETE) return "Del";
        if (key == GLFW.GLFW_KEY_HOME) return "Home";
        if (key == GLFW.GLFW_KEY_END) return "End";
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
        autocomplete = Boolean.parseBoolean(p.getProperty("autocomplete", "true"));
        autoClose = Boolean.parseBoolean(p.getProperty("autoClose", "true"));
        sidebarRight = Boolean.parseBoolean(p.getProperty("sidebarRight", "false"));
        sidebarOpen = Boolean.parseBoolean(p.getProperty("sidebarOpen", "true"));
        sidebarWidth = Math.max(110, Math.min(320, parseInt(p.getProperty("sidebarWidth"), sidebarWidth)));
        for (var entry : DEFAULTS.entrySet()) {
            BINDS.put(entry.getKey(), KeyBind.decode(p.getProperty("bind." + entry.getKey()), entry.getValue()));
        }
    }

    private static void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append("openKey=").append(openKey).append('\n');
            sb.append("withF3=").append(withF3).append('\n');
            sb.append("seconds=").append(seconds).append('\n');
            sb.append("scale=").append(scale).append('\n');
            sb.append("place=").append(place.name()).append('\n');
            sb.append("autocomplete=").append(autocomplete).append('\n');
            sb.append("autoClose=").append(autoClose).append('\n');
            sb.append("sidebarRight=").append(sidebarRight).append('\n');
            sb.append("sidebarOpen=").append(sidebarOpen).append('\n');
            sb.append("sidebarWidth=").append(sidebarWidth).append('\n');
            for (var entry : BINDS.entrySet()) {
                sb.append("bind.").append(entry.getKey()).append('=').append(entry.getValue().encode()).append('\n');
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
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
