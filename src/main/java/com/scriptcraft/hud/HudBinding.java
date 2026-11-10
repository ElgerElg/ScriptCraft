package com.scriptcraft.hud;

import com.scriptcraft.screen.BarElement;
import com.scriptcraft.screen.ScreenElement;
import com.scriptcraft.screen.SlotElement;
import com.scriptcraft.screen.TextElement;
import com.scriptcraft.script.runtime.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Какие поля элемента HUD следят за переменными.
 * В строке уже подставлены плейсхолдеры игрока ({coins::%player%} -> {coins::Steve}),
 * а сами {переменные} ещё на месте: их подставляют заново, когда переменную записывают.
 * null — это поле не следит (число, источник health/food или текст без переменных).
 */
public record HudBinding(String id, @Nullable String text, @Nullable String value, @Nullable String max) {
    private static final Pattern VARIABLE = Pattern.compile("%\\{([^{}]+)}%|\\{([^{}]+)}");

    public boolean isEmpty() {
        return text == null && value == null && max == null;
    }

    /** Имена переменных, от которых зависит элемент. */
    public void collectNames(Set<String> out) {
        collect(text, out);
        collect(value, out);
        collect(max, out);
    }

    /** Шаблон из сырого элемента. Без фигурных скобок после подстановки плейсхолдеров — null. */
    public static HudBinding capture(HudEntry raw, ExecutionContext ctx) {
        ScreenElement element = raw.element();
        if (element instanceof TextElement t) {
            return new HudBinding(raw.id(), live(t.text(), ctx, false), null, null);
        }
        if (element instanceof BarElement b) {
            return new HudBinding(raw.id(), live(b.text(), ctx, false), live(b.value(), ctx, true), live(b.max(), ctx, true));
        }
        if (element instanceof SlotElement s && s.content() == SlotElement.Content.TEXT) {
            return new HudBinding(raw.id(), live(s.value(), ctx, false), null, null);
        }
        return new HudBinding(raw.id(), null, null, null);
    }

    @Nullable
    private static String live(String raw, ExecutionContext ctx, boolean bar) {
        if (!raw.contains("{")) {
            return null;
        }
        String expanded = ctx.expandPlaceholders(raw);
        if (expanded.contains("%{")) {
            return expanded;
        }
        return bar && expanded.contains("{") ? expanded : null;
    }

    private static void collect(@Nullable String template, Set<String> out) {
        if (template == null) {
            return;
        }
        Matcher m = VARIABLE.matcher(template);
        while (m.find()) {
            String name = m.group(1) != null ? m.group(1) : m.group(2);
            out.add(name.strip());
        }
    }
}
