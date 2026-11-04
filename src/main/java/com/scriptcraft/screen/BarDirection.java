package com.scriptcraft.screen;

import java.util.Locale;

/**
 * Направление заполнения полосы прогресса. Горизонтальная/вертикальная полоса задаётся именно им:
 * left_to_right / right_to_left — горизонтальные, bottom_to_top / top_to_bottom — вертикальные.
 * Порядок констант важен для сети — новые добавлять в конец.
 */
public enum BarDirection {
    LEFT_TO_RIGHT, RIGHT_TO_LEFT, BOTTOM_TO_TOP, TOP_TO_BOTTOM;

    public boolean vertical() {
        return this == BOTTOM_TO_TOP || this == TOP_TO_BOTTOM;
    }

    /** "left_to_right", "right_to_left", "bottom_to_top", "top_to_bottom" (и короткие: right, left, up, down). */
    public static BarDirection byName(String name) {
        return switch (name.toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "left_to_right", "right", "horizontal" -> LEFT_TO_RIGHT;
            case "right_to_left", "left" -> RIGHT_TO_LEFT;
            case "bottom_to_top", "up", "vertical" -> BOTTOM_TO_TOP;
            case "top_to_bottom", "down" -> TOP_TO_BOTTOM;
            default -> null;
        };
    }
}
