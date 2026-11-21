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
 * Цвета текста редактора действуют только внутри кавычек: {@code &7} красит код и текст
 * до закрывающей кавычки или до другого цвета. {@code &7&l} — серый и жирный.
 * За кавычками цвет снова обычный. Ширина совпадает с тем, как строка рисуется.
 */
@OnlyIn(Dist.CLIENT)
public final class ScriptColors {
    private ScriptColors() {
    }

    public static int width(Font font, String line, int count) {
        return width(font, line, count, true);
    }

    public static int width(Font font, String line, int count, boolean quoteColors) {
        int end = Math.max(0, Math.min(count, line.length()));
        int width = 0;
        int tab = font.width("    ");
        for (Piece piece : pieces(line, end, 0xE7E9EF, quoteColors)) {
            width += piece.tab ? tab : font.width(styled(piece.text, piece.run));
        }
        return width;
    }

    public static void draw(GuiGraphics g, Font font, String line, int x, int y, int baseRgb, int max) {
        draw(g, font, line, x, y, baseRgb, max, true);
    }

    public static void draw(GuiGraphics g, Font font, String line, int x, int y, int baseRgb, int max, boolean quoteColors) {
        int drawn = 0;
        int tab = font.width("    ");
        for (Piece piece : pieces(line, line.length(), baseRgb, quoteColors)) {
            if (drawn >= max) {
                return;
            }
            if (piece.tab) {
                if (drawn + 2 < max) {
                    g.fill(x + drawn + 1, y, x + drawn + 2, y + 9, 0xFF5C6A80);
                }
                drawn += tab;
                continue;
            }
            drawn = paint(g, font, piece.text, piece.run, x, y, drawn, max);
        }
    }

    /** Серый цвет комментариев (# ...) в редакторе студии. */
    private static final int COMMENT_RGB = 0x858F9E;

    /** Куски строки с одним стилем. Цветовые коды читаются только внутри кавычек, комментарии (#...) — серым. */
    private static java.util.List<Piece> pieces(String line, int end, int baseRgb, boolean quoteColors) {
        java.util.List<Piece> out = new java.util.ArrayList<>();
        boolean inside = false;
        boolean comment = false;
        Run run = Run.base(baseRgb);
        StringBuilder cur = new StringBuilder();
        int i = 0;
        while (i < end) {
            char c = line.charAt(i);
            if (c == '\t') {
                flush(out, cur, run);
                out.add(new Piece("", run, true));
                i++;
                continue;
            }
            if (!comment && !inside && c == '#' && isCommentHash(line, i)) {
                flush(out, cur, run);
                comment = true;
                run = Run.base(COMMENT_RGB);
                cur.append(c);
                i++;
                continue;
            }
            if (comment) {
                cur.append(c);
                i++;
                continue;
            }
            if (c == '\\' && inside && i + 1 < end) {
                cur.append(c).append(line.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '"') {
                if (inside && i + 1 < line.length() && line.charAt(i + 1) == '"' && i + 1 < end) {
                    cur.append('"');
                    i += 2;
                    continue;
                }
                cur.append(c);
                flush(out, cur, run);
                inside = !inside;
                if (!inside) {
                    run = Run.base(baseRgb);
                }
                i++;
                continue;
            }
            int code = (quoteColors && inside) ? codeAt(line, i) : -1;
            if (code >= 0 && i + 1 < end) {
                flush(out, cur, run);
                run = run.apply((char) code, baseRgb);
                cur.append(line, i, i + 2);
                i += 2;
                continue;
            }
            cur.append(c);
            i++;
        }
        flush(out, cur, run);
        return out;
    }

    /** '#' — начало комментария, если стоит в начале строки или после пробела/таба и не является hex-цветом (#RRGGBB / #AARRGGBB). */
    private static boolean isCommentHash(String line, int pos) {
        if (pos > 0) {
            char prev = line.charAt(pos - 1);
            if (prev != ' ' && prev != '\t') {
                return false;
            }
        }
        // Если это начало строки (после отступа) — всегда комментарий
        boolean onlySpaceBefore = true;
        for (int k = 0; k < pos; k++) {
            char ch = line.charAt(k);
            if (ch != ' ' && ch != '\t') {
                onlySpaceBefore = false;
                break;
            }
        }
        if (onlySpaceBefore) {
            return true;
        }
        // Для встроенного '#' проверяем, не является ли это hex-цветом #RRGGBB / #AARRGGBB
        int hex = 0;
        for (int k = pos + 1; k < line.length(); k++) {
            char ch = line.charAt(k);
            if ((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F')) {
                hex++;
            } else {
                break;
            }
        }
        if ((hex == 6 || hex == 8) && (pos + 1 + hex == line.length() || Character.isWhitespace(line.charAt(pos + 1 + hex)))) {
            return false;
        }
        return true;
    }

    private static void flush(java.util.List<Piece> out, StringBuilder cur, Run run) {
        if (!cur.isEmpty()) {
            out.add(new Piece(cur.toString(), run, false));
            cur.setLength(0);
        }
    }

    private record Piece(String text, Run run, boolean tab) {
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

        Run apply(char code, int baseRgb) {
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
                case 'r' -> base(baseRgb);
                default -> this;
            };
        }

        /** Цвет в чате сбрасывает жирность и прочие флаги. */
        private Run color(int next) {
            return new Run(next, false, false, false, false);
        }
    }
}
