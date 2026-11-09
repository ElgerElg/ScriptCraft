package com.scriptcraft.script.runtime;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Имена клавиш для "on key e:". Латиница, без привязки к раскладке: физическая клавиша. */
public final class KeyNames {
    private static final Set<String> NAMES = new HashSet<>();

    static {
        for (char c = 'a'; c <= 'z'; c++) {
            NAMES.add(String.valueOf(c));
        }
        for (char c = '0'; c <= '9'; c++) {
            NAMES.add(String.valueOf(c));
        }
        for (int i = 1; i <= 12; i++) {
            NAMES.add("f" + i);
        }
        NAMES.addAll(List.of(
                "space", "escape", "tab", "enter", "backspace", "delete",
                "up", "down", "left", "right",
                "lshift", "rshift", "lctrl", "rctrl", "lalt", "ralt", "grave"));
    }

    private KeyNames() {
    }

    public static boolean known(String name) {
        return NAMES.contains(name.toLowerCase(Locale.ROOT));
    }

    public static String names() {
        return "a-z, 0-9, f1-f12, space, escape, tab, enter, backspace, delete, стрелки, lshift, rshift, lctrl, rctrl, lalt, ralt, grave";
    }
}
