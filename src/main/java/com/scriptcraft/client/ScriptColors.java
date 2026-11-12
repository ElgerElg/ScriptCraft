package com.scriptcraft.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Цвета текста редактора: {@code &7} красит сам код и текст после него, пока цвет не сменят.
 * {@code &7&l} — серый и жирный. Цветовой код сбрасывает жирность, как в игре.
 * Ширина считается тем же стилем, которым строка рисуется, чтобы курсор попадал в букву.
 */
@OnlyIn(Dist.CLIENT)
public final class ScriptColors {
    private ScriptColors() {
    }

    public static int width(Font font, String line, int count) {
        int end = Math.max(0, Math.min(count, line.length()));
        int width = 0;
        int i = 0;
        Run run = Run.base();
        int tab = font.width("    ");
        while (i < end) {
            if (line.charAt(i) == '\t') {
                width += tab;
                i++;
                continue;
            }
            int j = i;
            while (j < end && line.charAt(j) != '\t' && codeAt(line, j) < 0) {
                j++;
            }
            if (j > i) {
                width += font.width(styled(line.substring(i, j), run));
            }
            int code = j < end ? codeAt(line, j) : -1;
            if (code >= 0 && j + 1 < end) {
                run = run.apply((char) code);
                width += font.width(styled(line.substring(j, j + 2), run));
                i = j + 2;
            } else if (code >= 0) {
                width += font.width(styled(line.substring(j, j + 1), run));
                i = j + 1;
            } else {
                i = j;
            }
        }
        return width;
    }

    public static void draw(GuiGraphics g, Font font, String line, int x, int y, int baseRgb, int max) {
        int drawn = 0;
        int i = 0;
        Run run = Run.base(baseRgb);
        int tab = font.width("    ");
        while (i < line.length() && drawn < max) {
            if (line.charAt(i) == '\t') {
                if (drawn + 2 < max) {
                    g.fill(x + drawn + 1, y, x + drawn + 2, y + 9, 0xFF5C6A80);
                }
                drawn += tab;
                i++;
                continue;
            }
            int j = i;
            while (j < line.length() && line.charAt(j) != '\t' && codeAt(line, j) < 0) {
                j++;
            }
            if (j > i) {
                drawn = paint(g, font, line.substring(i, j), run, x, y, drawn, max);
                if (drawn >= max) {
                    return;
                }
            }
            int code = j < line.length() ? codeAt(line, j) : -1;
            if (code >= 0) {
                run = run.apply((char) code);
                int to = Math.min(line.length(), j + 2);
                drawn = paint(g, font, line.substring(j, to), run, x, y, drawn, max);
                i = to;
            } else {
                i = j;
            }
        }
    }

    private static int paint(GuiGraphics g, Font font, String text, Run run, int x, int y, int drawn, int max) {
        MutableComponent part = styled(text, run);
        int partW = font.width(part);
        if (drawn + partW > max) {
            String fit = font.plainSubstrByWidth(text, Math.max(0, max - drawn));
            if (!fit.isEmpty()) {
                g.drawString(font, styled(fit, run), x + drawn, y, 0xFFFFFFFF, false);
            }
            return max;
        }
        g.drawString(font, part, x + drawn, y, 0xFFFFFFFF, false);
        return drawn + partW;
    }

    private static MutableComponent styled(String text, Run run) {
        Style style = Style.EMPTY.withColor(TextColor.fromRgb(run.rgb & 0xFFFFFF))
                .withBold(run.bold)
                .withItalic(run.italic)
                .withUnderlined(run.under)
                .withStrikethrough(run.strike);
        return Component.literal(text).withStyle(style);
    }

    /** Код формата в этой позиции или -1. Второе значение — сам код (буква). */
    private static int codeAt(String line, int i) {
        if (line.charAt(i) != '&' || i + 1 >= line.length()) {
            return -1;
        }
        char c = Character.toLowerCase(line.charAt(i + 1));
        if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || c == 'k' || c == 'l' || c == 'm' || c == 'n' || c == 'o' || c == 'r') {
            return c;
        }
        return -1;
    }

    private record Run(int rgb, boolean bold, boolean italic, boolean under, boolean strike) {
        static Run base() {
            return new Run(0xE7E9EF, false, false, false, false);
        }

        static Run base(int rgb) {
            return new Run(rgb, false, false, false, false);
        }

        Run apply(char code) {
            return switch (code) {
                case '0' -> color(0x000000);
                case '1' -> color(0x0000AA);
                case '2' -> color(0x00AA00);
                case '3' -> color(0x00AAAA);
                case '4' -> color(0xAA0000);
                case '5' -> color(0xAA00AA);
                case '6' -> color(0xFFAA00);
                case '7' -> color(0xAAAAAA);
                case '8' -> color(0x555555);
                case '9' -> color(0x5555FF);
                case 'a' -> color(0x55FF55);
                case 'b' -> color(0x55FFFF);
                case 'c' -> color(0xFF5555);
                case 'd' -> color(0xFF55FF);
                case 'e' -> color(0xFFFF55);
                case 'f' -> color(0xFFFFFF);
                case 'l' -> new Run(rgb, true, italic, under, strike);
                case 'm' -> new Run(rgb, bold, italic, under, true);
                case 'n' -> new Run(rgb, bold, italic, true, strike);
                case 'o' -> new Run(rgb, bold, true, under, strike);
                case 'r' -> base();
                default -> this;
            };
        }

        /** Цвет в чате сбрасывает жирность и прочие флаги. */
        private Run color(int next) {
            return new Run(next, false, false, false, false);
        }
    }
}
