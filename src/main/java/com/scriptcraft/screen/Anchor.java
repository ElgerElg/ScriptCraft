package com.scriptcraft.screen;

/**
 * Точка привязки элемента к экрану. Итоговая позиция = край/центр экрана + смещение (x, y).
 * Например, CENTER и (0, -20) — элемент по центру и на 20 пикселей выше.
 */
public enum Anchor {
    TOP_LEFT(0, 0), TOP(1, 0), TOP_RIGHT(2, 0),
    LEFT(0, 1), CENTER(1, 1), RIGHT(2, 1),
    BOTTOM_LEFT(0, 2), BOTTOM(1, 2), BOTTOM_RIGHT(2, 2);

    /** 0 — левый край, 1 — центр, 2 — правый край. */
    private final int horizontal;
    /** 0 — верх, 1 — центр, 2 — низ. */
    private final int vertical;

    Anchor(int horizontal, int vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    /** 0 — левый край, 1 — центр, 2 — правый край (нужно для раскладки рядов слотов). */
    public int horizontal() {
        return horizontal;
    }

    public int resolveX(int screenWidth, int elementWidth, int offsetX) {
        return align(horizontal, screenWidth, elementWidth) + offsetX;
    }

    public int resolveY(int screenHeight, int elementHeight, int offsetY) {
        return align(vertical, screenHeight, elementHeight) + offsetY;
    }

    private static int align(int mode, int screenSize, int elementSize) {
        return switch (mode) {
            case 0 -> 0;
            case 1 -> (screenSize - elementSize) / 2;
            default -> screenSize - elementSize;
        };
    }

    /** "top_left", "center"... -> enum; null, если имя неизвестно. Синонимы: top_center, bottom_center, middle. */
    public static Anchor byName(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT).replace('-', '_');
        switch (n) {
            case "top_center", "center_top" -> {
                return TOP;
            }
            case "bottom_center", "center_bottom" -> {
                return BOTTOM;
            }
            case "middle" -> {
                return CENTER;
            }
            default -> {
            }
        }
        try {
            return valueOf(n.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
