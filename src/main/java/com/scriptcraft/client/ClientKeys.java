package com.scriptcraft.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** GLFW-код -> имя из on key. Раскладка не важна: это физическая клавиша. */
@OnlyIn(Dist.CLIENT)
public final class ClientKeys {
    private ClientKeys() {
    }

    @Nullable
    public static String name(int key) {
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            return String.valueOf((char) ('a' + (key - GLFW.GLFW_KEY_A)));
        }
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            return String.valueOf((char) ('0' + (key - GLFW.GLFW_KEY_0)));
        }
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12) {
            return "f" + (key - GLFW.GLFW_KEY_F1 + 1);
        }
        return switch (key) {
            case GLFW.GLFW_KEY_SPACE -> "space";
            case GLFW.GLFW_KEY_ESCAPE -> "escape";
            case GLFW.GLFW_KEY_TAB -> "tab";
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> "enter";
            case GLFW.GLFW_KEY_BACKSPACE -> "backspace";
            case GLFW.GLFW_KEY_DELETE -> "delete";
            case GLFW.GLFW_KEY_UP -> "up";
            case GLFW.GLFW_KEY_DOWN -> "down";
            case GLFW.GLFW_KEY_LEFT -> "left";
            case GLFW.GLFW_KEY_RIGHT -> "right";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "lshift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "rshift";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "lctrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "rctrl";
            case GLFW.GLFW_KEY_LEFT_ALT -> "lalt";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "ralt";
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> "grave";
            default -> null;
        };
    }
}
