package com.scriptcraft.screen;

import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.network.OpenScreenPacket;
import com.scriptcraft.script.InputSync;
import com.scriptcraft.script.ScriptLoader;
import com.scriptcraft.script.runtime.ScriptEvent;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Серверное состояние открытых кастомных экранов (open screen):
 * - знает, какой экран сейчас открыт у каждого игрока;
 * - не отправляет пакеты, если содержимое экрана не изменилось;
 * - обновляет, добавляет и удаляет элементы на лету без пересоздания экрана на клиенте;
 * - очищает колбэки и вызывает on screen open / on screen close.
 */
public final class ScreenManager {
    public static final String PATCH_PREFIX = "\0upd:";
    public static final String CLOSE_TOKEN = "\0close";

    private static final class PlayerScreen {
        String title;
        final List<ScreenElement> elements = new ArrayList<>();
        final Map<String, Object> values = new java.util.concurrent.ConcurrentHashMap<>();

        PlayerScreen(String title, List<ScreenElement> initial) {
            this.title = title;
            this.elements.addAll(initial);
            seedValues(initial);
        }

        void seedValues(List<ScreenElement> list) {
            for (ScreenElement e : list) {
                String id = ScreenMeta.elementId(e);
                if (id.isEmpty()) continue;
                String key = id.toLowerCase(java.util.Locale.ROOT);
                if (e instanceof InputElement in) values.put(key, in.value());
                else if (e instanceof SliderElement sl) values.put(key, sl.value());
                else if (e instanceof ToggleElement tg) values.put(key, tg.value());
            }
        }
    }

    private static final Map<UUID, PlayerScreen> OPEN = new ConcurrentHashMap<>();

    private ScreenManager() {
    }

    @Nullable
    public static String activeScreen(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        PlayerScreen ps = OPEN.get(player.getUUID());
        return ps == null ? null : ps.title;
    }

    public static boolean hasScreenOpen(ServerPlayer player, @Nullable String expectedTitle) {
        String active = activeScreen(player);
        if (active == null) {
            return false;
        }
        return expectedTitle == null || expectedTitle.isEmpty() || active.equalsIgnoreCase(expectedTitle);
    }

    /**
     * Открывает экран или обновляет уже открытый экран с тем же заголовком.
     * Если у игрока уже открыт экран с точно таким же заголовком и элементами — пакет не отправляется.
     */
    public static void open(ServerPlayer player, String title, List<ScreenElement> elements) {
        if (player == null) {
            return;
        }
        PlayerScreen prev = OPEN.get(player.getUUID());
        boolean sameTitle = prev != null && Objects.equals(prev.title, title);
        if (sameTitle && prev.elements.equals(elements)) {
            return;
        }
        OPEN.put(player.getUUID(), new PlayerScreen(title, elements));
        InputSync.allowScreenElements(player, elements);
        if (sameTitle) {
            ModNetwork.sendTo(player, new OpenScreenPacket(new ScreenDescription(PATCH_PREFIX + title, List.copyOf(elements))));
        } else {
            ModNetwork.sendTo(player, new OpenScreenPacket(new ScreenDescription(title, List.copyOf(elements))));
            ScriptLoader.fireEvent(ScriptEvent.SCREEN_OPEN, player, Map.of("screen", title));
        }
    }

    /**
     * Добавляет или заменяет по id элементы уже открытого экрана без его пересоздания.
     */
    public static void updateElements(ServerPlayer player, @Nullable String titleFilter, List<ScreenElement> patch) {
        if (player == null || patch.isEmpty()) {
            return;
        }
        PlayerScreen ps = OPEN.get(player.getUUID());
        if (ps == null) {
            if (titleFilter != null && !titleFilter.isEmpty()) {
                open(player, titleFilter, patch);
            }
            return;
        }
        if (titleFilter != null && !titleFilter.isEmpty() && !ps.title.equalsIgnoreCase(titleFilter)) {
            return;
        }
        boolean changed = false;
        for (ScreenElement item : patch) {
            String id = ScreenMeta.elementId(item);
            if (!id.isEmpty()) {
                int idx = findIndex(ps.elements, id);
                if (idx >= 0) {
                    if (!ps.elements.get(idx).equals(item)) {
                        ps.elements.set(idx, item);
                        changed = true;
                    }
                    continue;
                }
            }
            if (ps.elements.size() < ScreenDescription.MAX_ELEMENTS) {
                ps.elements.add(item);
                changed = true;
            }
        }
        if (changed) {
            ps.seedValues(patch);
            syncInPlace(player, ps);
        }
    }

    @Nullable
    public static Object controlValue(ServerPlayer player, String id) {
        if (player == null || id == null || id.isEmpty()) {
            return null;
        }
        PlayerScreen ps = OPEN.get(player.getUUID());
        if (ps == null) {
            return null;
        }
        return ps.values.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    /** Клиент изменил значение input / slider / toggle на открытом экране. */
    public static void onClientControl(ServerPlayer player, byte kind, String payload) {
        if (player == null || payload == null) {
            return;
        }
        PlayerScreen ps = OPEN.get(player.getUUID());
        if (ps == null) {
            return;
        }
        int sep = payload.indexOf('\0');
        if (sep <= 0) {
            return;
        }
        String id = payload.substring(0, sep);
        String rawVal = payload.substring(sep + 1);
        int idx = findIndex(ps.elements, id);
        if (idx < 0) {
            return;
        }
        ScreenElement old = ps.elements.get(idx);
        Object parsedVal;
        ScreenElement updated;
        if (kind == com.scriptcraft.network.InputPacket.GUI_INPUT && old instanceof InputElement in) {
            String trimmed = rawVal.length() > in.maxLength() ? rawVal.substring(0, in.maxLength()) : rawVal;
            parsedVal = trimmed;
            updated = in.withValue(trimmed);
        } else if (kind == com.scriptcraft.network.InputPacket.GUI_SLIDER && old instanceof SliderElement sl) {
            double num;
            try {
                num = Double.parseDouble(rawVal);
                if (!Double.isFinite(num)) return;
            } catch (NumberFormatException e) {
                return;
            }
            SliderElement nextSl = sl.withValue(num);
            parsedVal = nextSl.value();
            updated = nextSl;
        } else if (kind == com.scriptcraft.network.InputPacket.GUI_TOGGLE && old instanceof ToggleElement tg) {
            boolean b = "true".equalsIgnoreCase(rawVal) || "1".equals(rawVal);
            parsedVal = b;
            updated = tg.withValue(b);
        } else {
            return;
        }
        ps.elements.set(idx, updated);
        ps.values.put(id.toLowerCase(java.util.Locale.ROOT), parsedVal);
        String valStr = com.scriptcraft.script.runtime.Expression.toText(parsedVal);
        ScriptLoader.fireEvent(ScriptEvent.GUI_CHANGE, player, Map.of(
                "element", id,
                "value", valStr,
                "screen", ps.title));
        String packedAct = ScreenMeta.elementPackedAction(updated);
        String rawAct = ScreenMeta.rawAction(packedAct);
        if (rawAct.startsWith("fn:")) {
            String call = rawAct.substring(3).replace("%value%", valStr).replace("%element%", id);
            if (InputSync.mayRunScreenFunc(player, rawAct.substring(3))) {
                ScriptLoader.runScreenFunction(player, call);
            }
        } else if (rawAct.startsWith("/") && player.getServer() != null) {
            String cmd = rawAct.substring(1).replace("%value%", valStr).replace("%element%", id);
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), cmd);
        }
    }

    public static void animatePosition(ServerPlayer player, String id, int x, int y, int durationMs) {
        if (player == null || id == null || id.isEmpty()) {
            return;
        }
        PlayerScreen ps = OPEN.get(player.getUUID());
        if (ps == null) {
            return;
        }
        int idx = findIndex(ps.elements, id);
        if (idx < 0) {
            return;
        }
        ScreenElement old = ps.elements.get(idx);
        ScreenElement moved = ScreenMeta.withPosition(old, x, y);
        if (moved.equals(old)) {
            return;
        }
        ps.elements.set(idx, moved);
        List<ScreenElement> packetList = new ArrayList<>(ps.elements);
        if (durationMs > 0) {
            packetList.set(idx, ScreenMeta.withTween(moved, durationMs));
        }
        ModNetwork.sendTo(player, new OpenScreenPacket(new ScreenDescription(PATCH_PREFIX + ps.title, packetList)));
    }

    public static void mutateElement(ServerPlayer player, String id, UnaryOperator<ScreenElement> mutator) {
        if (player == null || id == null || id.isEmpty()) {
            return;
        }
        PlayerScreen ps = OPEN.get(player.getUUID());
        if (ps == null) {
            return;
        }
        int idx = findIndex(ps.elements, id);
        if (idx < 0) {
            return;
        }
        ScreenElement old = ps.elements.get(idx);
        ScreenElement next = mutator.apply(old);
        if (next != null && !next.equals(old)) {
            ps.elements.set(idx, next);
            ps.seedValues(List.of(next));
            syncInPlace(player, ps);
        }
    }

    public static void setBar(ServerPlayer player, String id, @Nullable String value, @Nullable String max) {
        mutateElement(player, id, e -> e instanceof BarElement b ? b.withValues(value, max) : e);
    }

    public static void removeElement(ServerPlayer player, String id) {
        if (player == null || id == null || id.isEmpty()) {
            return;
        }
        PlayerScreen ps = OPEN.get(player.getUUID());
        if (ps == null) {
            return;
        }
        int idx = findIndex(ps.elements, id);
        if (idx >= 0) {
            ps.elements.remove(idx);
            syncInPlace(player, ps);
        }
    }

    public static void clearElements(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerScreen ps = OPEN.get(player.getUUID());
        if (ps != null && !ps.elements.isEmpty()) {
            ps.elements.clear();
            syncInPlace(player, ps);
        }
    }

    public static void close(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerScreen removed = OPEN.remove(player.getUUID());
        InputSync.clearScreen(player);
        ModNetwork.sendTo(player, new OpenScreenPacket(new ScreenDescription(CLOSE_TOKEN, List.of())));
        if (removed != null) {
            ScriptLoader.fireEvent(ScriptEvent.SCREEN_CLOSE, player, Map.of("screen", removed.title));
        }
    }

    /** Клиент сообщил о закрытии экрана (Esc, кнопка close и т.д.). */
    public static void onClientClosed(ServerPlayer player, String closedTitle) {
        if (player == null) {
            return;
        }
        PlayerScreen current = OPEN.get(player.getUUID());
        if (current != null && closedTitle != null && !closedTitle.isEmpty() && !Objects.equals(current.title, closedTitle)) {
            // Устаревший пакет закрытия предыдущего экрана после перехода на новый — игнорируем
            return;
        }
        PlayerScreen removed = OPEN.remove(player.getUUID());
        InputSync.clearScreen(player);
        String title = removed != null ? removed.title : (closedTitle == null ? "" : closedTitle);
        if (!title.isEmpty()) {
            ScriptLoader.fireEvent(ScriptEvent.SCREEN_CLOSE, player, Map.of("screen", title));
        }
    }

    public static void onQuit(ServerPlayer player) {
        if (player == null) {
            return;
        }
        OPEN.remove(player.getUUID());
        InputSync.clearScreen(player);
    }

    public static void clearAll() {
        OPEN.clear();
    }

    private static void syncInPlace(ServerPlayer player, PlayerScreen ps) {
        InputSync.allowScreenElements(player, ps.elements);
        ModNetwork.sendTo(player, new OpenScreenPacket(new ScreenDescription(PATCH_PREFIX + ps.title, List.copyOf(ps.elements))));
    }

    private static int findIndex(List<ScreenElement> elements, String id) {
        for (int i = 0; i < elements.size(); i++) {
            if (id.equalsIgnoreCase(ScreenMeta.elementId(elements.get(i)))) {
                return i;
            }
        }
        return -1;
    }
}
