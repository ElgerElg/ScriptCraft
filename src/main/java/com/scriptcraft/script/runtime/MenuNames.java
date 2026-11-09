package com.scriptcraft.script.runtime;

import java.util.Locale;
import java.util.Set;

/** Имена ванильных экранов для "on open inventory:". any — любой из списка, кроме экрана самого скрипта. */
public final class MenuNames {
    public static final Set<String> NAMES = Set.of(
            "inventory", "chest", "shulker", "barrel", "crafting", "furnace", "anvil", "enchanting",
            "brewing", "villager", "hopper", "horse", "beacon", "smithing", "grindstone", "loom",
            "cartography", "stonecutter", "any");

    private MenuNames() {
    }

    public static boolean known(String name) {
        return NAMES.contains(name.toLowerCase(Locale.ROOT));
    }
}
