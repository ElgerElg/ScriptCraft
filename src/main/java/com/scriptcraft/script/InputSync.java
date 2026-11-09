package com.scriptcraft.script;

import com.scriptcraft.network.InputSyncPacket;
import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.script.engine.ScriptProgram;
import com.scriptcraft.script.parser.EventHandler;
import com.scriptcraft.script.runtime.Actions;
import com.scriptcraft.script.runtime.MenuNames;
import com.scriptcraft.script.runtime.ScriptEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Собирает из скриптов клавиши и меню, которые клиент должен присылать,
 * и меню, которые клиент закрывает ещё до открытия.
 * Имя, которого нет в этом списке, с клиента не принимается.
 */
public final class InputSync {
    private static Set<String> keys = Set.of();
    private static Set<String> menus = Set.of();
    private static InputSyncPacket packet = new InputSyncPacket(List.of(), List.of(), List.of());

    private InputSync() {
    }

    public static void sync(MinecraftServer server) {
        Set<String> keySet = new LinkedHashSet<>();
        Set<String> watch = new LinkedHashSet<>();
        Set<String> cancel = new LinkedHashSet<>();
        ScriptProgram program = ScriptLoader.getProgram();
        for (EventHandler handler : program.eventHandlers(ScriptEvent.KEY)) {
            if (handler.filter() != null) {
                keySet.add(handler.filter());
            }
        }
        for (EventHandler handler : program.eventHandlers(ScriptEvent.OPEN)) {
            if (handler.filter() == null) {
                continue;
            }
            boolean blocks = containsCancel(handler.actions());
            if (handler.filter().equals("any")) {
                for (String name : MenuNames.NAMES) {
                    if (name.equals("any")) {
                        continue;
                    }
                    watch.add(name);
                    if (blocks) {
                        cancel.add(name);
                    }
                }
            } else {
                watch.add(handler.filter());
                if (blocks) {
                    cancel.add(handler.filter());
                }
            }
        }
        keys = Set.copyOf(keySet);
        menus = Set.copyOf(watch);
        packet = new InputSyncPacket(List.copyOf(keySet), List.copyOf(watch), List.copyOf(cancel));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ModNetwork.sendTo(player, packet);
        }
    }

    public static boolean watchesKey(String name) {
        return keys.contains(name);
    }

    public static boolean watchesMenu(String name) {
        return menus.contains(name);
    }

    /**
     * cancel event где угодно в теле (в том числе внутри if, loop, chance, wait)
     * заставляет клиент не открывать это меню всегда: круг до сервера уже не успеет.
     */
    public static boolean containsCancel(List<com.scriptcraft.script.runtime.Action> actions) {
        for (com.scriptcraft.script.runtime.Action action : actions) {
            if (action instanceof Actions.CancelEvent) {
                return true;
            }
            if (action instanceof Actions.If branch
                    && (containsCancel(branch.then()) || containsCancel(branch.otherwise()))) {
                return true;
            }
            if (action instanceof Actions.Chance chance && containsCancel(chance.body())) {
                return true;
            }
            if (action instanceof Actions.Loop loop && containsCancel(loop.body())) {
                return true;
            }
            if (action instanceof Actions.Wait wait && containsCancel(wait.after())) {
                return true;
            }
        }
        return false;
    }
}
