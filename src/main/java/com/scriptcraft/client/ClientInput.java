package com.scriptcraft.client;

import com.scriptcraft.network.InputSyncPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashSet;
import java.util.Set;

/** Что клиент должен присылать на сервер. Пусто, пока не пришёл пакет и после выхода. */
@OnlyIn(Dist.CLIENT)
public final class ClientInput {
    private static Set<String> keys = Set.of();
    private static Set<String> releaseKeys = Set.of();
    private static Set<String> holdKeys = Set.of();
    private static Set<String> menus = Set.of();
    private static Set<String> cancel = Set.of();

    private ClientInput() {
    }

    public static void apply(InputSyncPacket packet) {
        Set<String> press = new HashSet<>();
        Set<String> rel = new HashSet<>();
        Set<String> hold = new HashSet<>();
        for (String k : packet.keys()) {
            if (k.startsWith("^")) {
                rel.add(k.substring(1));
            } else if (k.startsWith("~")) {
                hold.add(k.substring(1));
            } else {
                press.add(k);
            }
        }
        keys = Set.copyOf(press);
        releaseKeys = Set.copyOf(rel);
        holdKeys = Set.copyOf(hold);
        menus = new HashSet<>(packet.menus());
        cancel = new HashSet<>(packet.cancel());
    }

    public static void reset() {
        keys = Set.of();
        releaseKeys = Set.of();
        holdKeys = Set.of();
        menus = Set.of();
        cancel = Set.of();
    }

    public static boolean watchesKey(String name) {
        return keys.contains(name) || keys.contains("any");
    }

    public static boolean watchesRelease(String name) {
        return releaseKeys.contains(name) || releaseKeys.contains("any");
    }

    public static boolean watchesHold(String name) {
        return holdKeys.contains(name) || holdKeys.contains("any");
    }

    public static boolean watchesMenu(String name) {
        return menus.contains(name);
    }

    public static boolean cancels(String name) {
        return cancel.contains(name);
    }
}
