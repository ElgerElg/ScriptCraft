package com.scriptcraft.script.runtime;

import org.jetbrains.annotations.Nullable;

/**
 * Точка в мире: мир, координаты и поворот. В переменной хранится строка
 * {@code loc <мир> <x> <y> <z> <yaw> <pitch>}, в чат уходит короткий вид без приставки.
 */
public record ScriptLocation(String world, double x, double y, double z, float yaw, float pitch) {
    public static final String PREFIX = "loc ";

    public String encode() {
        return PREFIX + world + " " + x + " " + y + " " + z + " " + yaw + " " + pitch;
    }

    /** Короткий вид для чата и сравнения: мир и три координаты. */
    public String display() {
        return world + " " + trim(x) + " " + trim(y) + " " + trim(z);
    }

    @Nullable
    public static ScriptLocation decode(@Nullable Object value) {
        if (!(value instanceof String text) || !text.startsWith(PREFIX)) {
            return null;
        }
        String[] parts = text.substring(PREFIX.length()).split(" ");
        if (parts.length < 4) {
            return null;
        }
        try {
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;
            return new ScriptLocation(parts[0], Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]), yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trim(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
