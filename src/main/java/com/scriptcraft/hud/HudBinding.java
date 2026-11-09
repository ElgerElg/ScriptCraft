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
    private static final Pattern VARIABLE = Pattern.compile("\\{([^{}]+)}");

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
            return new HudBinding(raw.id(), live(t.text(), ctx), null, null);
        }
        if (element instanceof BarElement b) {
            return new HudBinding(raw.id(), live(b.text(), ctx), live(b.value(), ctx), live(b.max(), ctx));
        }
        if (element instanceof SlotElement s && s.content() == SlotElement.Content.TEXT) {
            return new HudBinding(raw.id(), live(s.value(), ctx), null, null);
        }
        return new HudBinding(raw.id(), null, null, null);
    }

    @Nullable
    private static String live(String raw, ExecutionContext ctx) {
        if (raw.indexOf('{') < 0) {
            return null;
        }
        String expanded = ctx.expandPlaceholders(raw);
        return expanded.indexOf('{') >= 0 ? expanded : null;
    }

    private static void collect(@Nullable String template, Set<String> out) {
        if (template == null) {
            return;
        }
        Matcher m = VARIABLE.matcher(template);
        while (m.find()) {
            out.add(m.group(1).strip());
        }
    }
}
