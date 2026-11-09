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
    private static Set<String> menus = Set.of();
    private static Set<String> cancel = Set.of();

    private ClientInput() {
    }

    public static void apply(InputSyncPacket packet) {
        keys = new HashSet<>(packet.keys());
        menus = new HashSet<>(packet.menus());
        cancel = new HashSet<>(packet.cancel());
    }

    public static void reset() {
        keys = Set.of();
        menus = Set.of();
        cancel = Set.of();
    }

    public static boolean watchesKey(String name) {
        return keys.contains(name);
    }

    public static boolean watchesMenu(String name) {
        return menus.contains(name);
    }

    public static boolean cancels(String name) {
        return cancel.contains(name);
    }
}
