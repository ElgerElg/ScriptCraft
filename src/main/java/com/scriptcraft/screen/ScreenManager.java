package com.scriptcraft.screen;

import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.network.OpenScreenPacket;
import com.scriptcraft.script.InputSync;
import com.scriptcraft.script.ScriptLoader;
import com.scriptcraft.script.runtime.ScriptEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Серверное состояние открытых кастомных экранов (open screen):
 * - знает, какой экран сейчас открыт у каждого игрока;
 * - не отправляет пакеты, если содержимое экрана не изменилось;
 * - батчит все изменения элементов за тик в один дельта-пакет (отправляются только изменённые id);
 * - пересобирает InputSync только при изменении действий элементов;
 * - очищает колбэки и вызывает on screen open / on screen close.
 */
public final class ScreenManager {
    public static final String PATCH_PREFIX = "\0upd:";
    public static final String DELTA_PREFIX = "\0dlt:";
    public static final String CLOSE_TOKEN = "\0close";

    private static final class PlayerScreen {
        String title;
        final List<ScreenElement> elements = new ArrayList<>();
        final Map<String, Integer> idIndex = new HashMap<>();
        final Map<String, Object> values = new ConcurrentHashMap<>();
        final Map<String, ScreenElement> dirtyDelta = new LinkedHashMap<>();
        boolean fullDirty;
        boolean actionsDirty;

        PlayerScreen(String title, List<ScreenElement> initial) {
            this.title = title;
            this.elements.addAll(initial);
            rebuildIndex();
            seedValues(initial);
        }

        void rebuildIndex() {
            idIndex.clear();
            for (int i = 0; i < elements.size(); i++) {
                String id = ScreenMeta.elementId(elements.get(i));
                if (!id.isEmpty()) {
                    idIndex.putIfAbsent(id.toLowerCase(Locale.ROOT), i);
                }
            }
        }

        int indexOf(String id) {
            if (id == null || id.isEmpty()) {
                return -1;
            }
            Integer idx = idIndex.get(id.toLowerCase(Locale.ROOT));
            if (idx != null && idx >= 0 && idx < elements.size()
                    && id.equalsIgnoreCase(ScreenMeta.elementId(elements.get(idx)))) {
                return idx;
            }
            return -1;
        }

        void seedValues(List<ScreenElement> list) {
            for (ScreenElement e : list) {
                String id = ScreenMeta.elementId(e);
                if (id.isEmpty()) continue;
                String key = id.toLowerCase(Locale.ROOT);
                if (e instanceof InputElement in) values.put(key, in.value());
                else if (e instanceof SliderElement sl) values.put(key, sl.value());
                else if (e instanceof ToggleElement tg) values.put(key, tg.value());
            }
        }
    }

    private static final Map<UUID, PlayerScreen> OPEN = new ConcurrentHashMap<>();
    private static final Set<UUID> DIRTY_PLAYERS = ConcurrentHashMap.newKeySet();

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
        UUID uuid = player.getUUID();
        PlayerScreen prev = OPEN.get(uuid);
        boolean sameTitle = prev != null && Objects.equals(prev.title, title);
        if (sameTitle && prev.elements.equals(elements)) {
            return;
        }
        DIRTY_PLAYERS.remove(uuid);
        OPEN.put(uuid, new PlayerScreen(title, elements));
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
     * Изменения батчатся до конца серверного тика и отправляются одним дельта-пакетом.
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
                int idx = ps.indexOf(id);
                if (idx >= 0) {
                    ScreenElement old = ps.elements.get(idx);
                    if (!old.equals(item)) {
                        ps.elements.set(idx, item);
                        if (!Objects.equals(ScreenMeta.rawAction(ScreenMeta.elementPackedAction(old)),
                                ScreenMeta.rawAction(ScreenMeta.elementPackedAction(item)))) {
                            ps.actionsDirty = true;
                        }
                        ps.dirtyDelta.put(id.toLowerCase(Locale.ROOT), item);
                        changed = true;
                    }
                    continue;
                }
            }
            if (ps.elements.size() < ScreenDescription.MAX_ELEMENTS) {
                int nextIdx = ps.elements.size();
                ps.elements.add(item);
                if (!id.isEmpty()) {
                    ps.idIndex.putIfAbsent(id.toLowerCase(Locale.ROOT), nextIdx);
                }
                ps.fullDirty = true;
                ps.actionsDirty = true;
                changed = true;
            }
        }
        if (changed) {
            ps.seedValues(patch);
            DIRTY_PLAYERS.add(player.getUUID());
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
        return ps.values.get(id.toLowerCase(Locale.ROOT));
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
        int idx = ps.indexOf(id);
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
        ps.values.put(id.toLowerCase(Locale.ROOT), parsedVal);
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
        int idx = ps.indexOf(id);
        if (idx < 0) {
            return;
        }
        ScreenElement old = ps.elements.get(idx);
        ScreenElement moved = ScreenMeta.withPosition(old, x, y);
        if (moved.equals(old)) {
            return;
        }
        ps.elements.set(idx, moved);
        ScreenElement packetEl = durationMs > 0 ? ScreenMeta.withTween(moved, durationMs) : moved;
        ps.dirtyDelta.put(id.toLowerCase(Locale.ROOT), packetEl);
        DIRTY_PLAYERS.add(player.getUUID());
    }

    public static void mutateElement(ServerPlayer player, String id, UnaryOperator<ScreenElement> mutator) {
        if (player == null || id == null || id.isEmpty()) {
            return;
        }
        PlayerScreen ps = OPEN.get(player.getUUID());
        if (ps == null) {
            return;
        }
        int idx = ps.indexOf(id);
        if (idx < 0) {
            return;
        }
        ScreenElement old = ps.elements.get(idx);
        ScreenElement next = mutator.apply(old);
        if (next != null && !next.equals(old)) {
            ps.elements.set(idx, next);
            ps.seedValues(List.of(next));
            ps.dirtyDelta.put(id.toLowerCase(Locale.ROOT), next);
            DIRTY_PLAYERS.add(player.getUUID());
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
        int idx = ps.indexOf(id);
        if (idx >= 0) {
            ps.elements.remove(idx);
            ps.rebuildIndex();
            ps.fullDirty = true;
            ps.actionsDirty = true;
            DIRTY_PLAYERS.add(player.getUUID());
        }
    }

    public static void clearElements(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerScreen ps = OPEN.get(player.getUUID());
        if (ps != null && !ps.elements.isEmpty()) {
            ps.elements.clear();
            ps.idIndex.clear();
            ps.dirtyDelta.clear();
            ps.fullDirty = true;
            ps.actionsDirty = true;
            DIRTY_PLAYERS.add(player.getUUID());
        }
    }

    /**
     * Вызывается раз в серверный тик (ServerTickEvent.END):
     * отправляет накопленные за тик изменения одним компактным дельта-пакетом (или полным патчем при добавлении/удалении).
     */
    public static void flushDirty(@Nullable MinecraftServer server) {
        if (server == null || DIRTY_PLAYERS.isEmpty()) {
            return;
        }
        List<UUID> snapshot = new ArrayList<>(DIRTY_PLAYERS);
        DIRTY_PLAYERS.clear();
        for (UUID uuid : snapshot) {
            PlayerScreen ps = OPEN.get(uuid);
            if (ps == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                continue;
            }
            if (ps.actionsDirty) {
                InputSync.allowScreenElements(player, ps.elements);
                ps.actionsDirty = false;
            }
            if (ps.fullDirty) {
                ps.fullDirty = false;
                ps.dirtyDelta.clear();
                ModNetwork.sendTo(player, new OpenScreenPacket(
                        new ScreenDescription(PATCH_PREFIX + ps.title, List.copyOf(ps.elements))));
            } else if (!ps.dirtyDelta.isEmpty()) {
                List<ScreenElement> delta = List.copyOf(ps.dirtyDelta.values());
                ps.dirtyDelta.clear();
                ModNetwork.sendTo(player, new OpenScreenPacket(
                        new ScreenDescription(DELTA_PREFIX + ps.title, delta)));
            }
        }
    }

    public static void close(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUUID();
        DIRTY_PLAYERS.remove(uuid);
        PlayerScreen removed = OPEN.remove(uuid);
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
        UUID uuid = player.getUUID();
        PlayerScreen current = OPEN.get(uuid);
        if (current != null && closedTitle != null && !closedTitle.isEmpty() && !Objects.equals(current.title, closedTitle)) {
            // Устаревший пакет закрытия предыдущего экрана после перехода на новый — игнорируем
            return;
        }
        DIRTY_PLAYERS.remove(uuid);
        PlayerScreen removed = OPEN.remove(uuid);
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
        UUID uuid = player.getUUID();
        DIRTY_PLAYERS.remove(uuid);
        OPEN.remove(uuid);
        InputSync.clearScreen(player);
    }

    public static void clearAll() {
        DIRTY_PLAYERS.clear();
        OPEN.clear();
    }
}
