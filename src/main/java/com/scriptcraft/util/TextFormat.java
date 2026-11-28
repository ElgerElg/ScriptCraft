package com.scriptcraft.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Общий (сервер + клиент) конвертер цветов и форматирования для предметов, блоков, lore и 3D-сущностей:
 * поддерживает коды &0..&9, &a..&f, &k..&o, &r, секции §, а также HEX-цвета &#RRGGBB и &x&R&R&G&G&B&B.
 */
public final class TextFormat {
    private TextFormat() {
    }

    /** Проверяет, остались ли в строке неразобранные цветовые коды через '&'. */
    public static boolean containsAmpersandCodes(@Nullable String raw) {
        if (raw == null || raw.length() < 2) {
            return false;
        }
        for (int i = 0; i < raw.length() - 1; i++) {
            if (raw.charAt(i) == '&') {
                char c = Character.toLowerCase(raw.charAt(i + 1));
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                        || (c >= 'k' && c <= 'o') || c == 'r' || c == '#' || c == 'x') {
                    return true;
                }
            }
        }
        return false;
    }

    /** Форматирует текст (для имён мобов, NPC, сообщений и заголовков). */
    public static MutableComponent format(@Nullable String raw) {
        return parseInternal(raw, Style.EMPTY);
    }

    /**
     * Форматирует имя кастомного предмета или блока:
     * по умолчанию выключает ванильный курсив переименованных предметов (если явно не указан &o).
     */
    public static MutableComponent formatItemName(@Nullable String raw) {
        return parseInternal(raw, Style.EMPTY.withItalic(false));
    }

    /**
     * Разбирает многострочный lore предмета/блока (разделители: \n, \\n, %nl%, |)
     * с серым цветом по умолчанию и без принудительного курсива.
     */
    public static List<Component> formatLore(@Nullable String rawLore) {
        if (rawLore == null || rawLore.isBlank()) {
            return List.of();
        }
        String normalized = rawLore
                .replace("\\n", "\n")
                .replace("%nl%", "\n");
        String[] parts = normalized.split("\n", -1);
        List<Component> lines = new ArrayList<>(parts.length);
        Style baseLoreStyle = Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false);
        for (String line : parts) {
            lines.add(parseInternal(line, baseLoreStyle));
        }
        return lines;
    }

    private static MutableComponent parseInternal(@Nullable String raw, Style baseStyle) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty().withStyle(baseStyle);
        }
        MutableComponent root = Component.empty().withStyle(baseStyle);
        StringBuilder seg = new StringBuilder();
        Style current = baseStyle;
        int len = raw.length();

        for (int i = 0; i < len; i++) {
            char ch = raw.charAt(i);
            if ((ch == '&' || ch == '\u00a7') && i + 1 < len) {
                char next = raw.charAt(i + 1);
                // 1) &#RRGGBB
                if (next == '#' && i + 7 < len) {
                    int rgb = parseHex6(raw, i + 2);
                    if (rgb >= 0) {
                        flushSegment(root, seg, current);
                        current = baseStyle.withColor(TextColor.fromRgb(rgb));
                        i += 7;
                        continue;
                    }
                }
                // 2) &x&R&R&G&G&B&B (Spigot/Bungee hex)
                if ((next == 'x' || next == 'X') && i + 13 < len) {
                    int rgb = parseBungeeHex(raw, i + 2);
                    if (rgb >= 0) {
                        flushSegment(root, seg, current);
                        current = baseStyle.withColor(TextColor.fromRgb(rgb));
                        i += 13;
                        continue;
                    }
                }
                // 3) Стандартные коды 0-9, a-f, k-o, r
                ChatFormatting fmt = ChatFormatting.getByCode(Character.toLowerCase(next));
                if (fmt != null) {
                    flushSegment(root, seg, current);
                    if (fmt == ChatFormatting.RESET) {
                        current = baseStyle;
                    } else if (fmt.isColor()) {
                        current = baseStyle.withColor(fmt);
                    } else {
                        current = applyFormatting(current, fmt);
                    }
                    i++;
                    continue;
                }
            }
            seg.append(ch);
        }
        flushSegment(root, seg, current);
        return root;
    }

    private static void flushSegment(MutableComponent root, StringBuilder seg, Style style) {
        if (seg.length() > 0) {
            root.append(Component.literal(seg.toString()).withStyle(style));
            seg.setLength(0);
        }
    }

    private static Style applyFormatting(Style style, ChatFormatting fmt) {
        return switch (fmt) {
            case BOLD -> style.withBold(true);
            case ITALIC -> style.withItalic(true);
            case UNDERLINE -> style.withUnderlined(true);
            case STRIKETHROUGH -> style.withStrikethrough(true);
            case OBFUSCATED -> style.withObfuscated(true);
            default -> style.applyFormat(fmt);
        };
    }

    private static int parseHex6(String s, int start) {
        int val = 0;
        for (int k = 0; k < 6; k++) {
            int d = Character.digit(s.charAt(start + k), 16);
            if (d < 0) {
                return -1;
            }
            val = (val << 4) | d;
        }
        return val;
    }

    private static int parseBungeeHex(String s, int start) {
        int val = 0;
        for (int k = 0; k < 6; k++) {
            char marker = s.charAt(start + k * 2);
            if (marker != '&' && marker != '\u00a7') {
                return -1;
            }
            int d = Character.digit(s.charAt(start + k * 2 + 1), 16);
            if (d < 0) {
                return -1;
            }
            val = (val << 4) | d;
        }
        return val;
    }
}
