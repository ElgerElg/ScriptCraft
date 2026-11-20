package com.scriptcraft.script;

import com.scriptcraft.network.InputSyncPacket;
import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.screen.ButtonElement;
import com.scriptcraft.screen.ImageElement;
import com.scriptcraft.screen.ListElement;
import com.scriptcraft.screen.ScreenElement;
import com.scriptcraft.screen.SlotElement;
import com.scriptcraft.screen.TextElement;
import com.scriptcraft.script.engine.ScriptProgram;
import com.scriptcraft.script.parser.EventHandler;
import com.scriptcraft.script.runtime.Actions;
import com.scriptcraft.script.runtime.MenuNames;
import com.scriptcraft.script.runtime.ScriptEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Собирает из скриптов клавиши и меню, которые клиент должен присылать,
 * и меню (или прыжок "$jump"), которые клиент блокирует сразу на своей стороне.
 * Также хранит разрешённые вызовы функций ("fn:...") с открытого игроку экрана.
 */
public final class InputSync {
    public static final String JUMP_TOKEN = "$jump";
    private static Set<String> keys = Set.of();
    private static Set<String> menus = Set.of();
    private static boolean cancelJump;
    private static InputSyncPacket packet = new InputSyncPacket(List.of(), List.of(), List.of());
    private static final Map<UUID, Set<String>> SCREEN_FUNCS = new ConcurrentHashMap<>();

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
        boolean jumpBlocks = false;
        for (EventHandler handler : program.eventHandlers(ScriptEvent.JUMP)) {
            if (containsCancel(handler.actions())) {
                jumpBlocks = true;
                break;
            }
        }
        if (jumpBlocks) {
            cancel.add(JUMP_TOKEN);
        }
        cancelJump = jumpBlocks;
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

    public static boolean cancelsJump() {
        return cancelJump;
    }

    /** Запоминает вызовы функций ("fn:..."), доступные игроку на открытом экране. */
    public static void allowScreenElements(ServerPlayer player, List<ScreenElement> elements) {
        Set<String> allowed = new LinkedHashSet<>();
        for (ScreenElement element : elements) {
            collectFuncAction(allowed, element);
        }
        if (allowed.isEmpty()) {
            SCREEN_FUNCS.remove(player.getUUID());
        } else {
            SCREEN_FUNCS.put(player.getUUID(), Set.copyOf(allowed));
        }
    }

    private static void collectFuncAction(Set<String> out, ScreenElement element) {
        String action = null;
        if (element instanceof ButtonElement b) action = b.action();
        else if (element instanceof TextElement t) action = t.action();
        else if (element instanceof ImageElement img) action = img.action();
        else if (element instanceof SlotElement s) action = s.action();
        else if (element instanceof ListElement list) {
            for (ListElement.Row row : list.rows()) {
                if (row.action() != null && row.action().startsWith("fn:")) {
                    out.add(row.action().substring(3));
                }
            }
        }
        if (action != null && action.startsWith("fn:")) {
            out.add(action.substring(3));
        }
    }

    public static boolean mayRunScreenFunc(ServerPlayer player, String call) {
        Set<String> allowed = SCREEN_FUNCS.get(player.getUUID());
        return allowed != null && allowed.contains(call);
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
