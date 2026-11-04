package com.scriptcraft.hud;

import com.scriptcraft.lang.Messages;
import com.scriptcraft.network.HudPacket;
import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.script.runtime.ScriptRuntimeException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Серверное состояние HUD каждого игрока (что у него сейчас нарисовано) и отправка ИЗМЕНЕНИЙ клиенту.
 *
 * - Если новое значение совпадает с уже отправленным, пакет не уходит (можно спокойно обновлять полосу
 *   из частых событий — сеть не забивается).
 * - Состояние хранится в памяти сервера: при выходе игрока оно сохраняется, а при входе отправляется
 *   заново (клиент свой HUD при отключении стирает). Очищается при остановке сервера.
 * - /sc reload HUD не трогает: повторный create hud с тем же именем заменит группу (обычно из "on join or load:").
 * - У каждой группы и у каждого "hide vanilla" запоминается скрипт-владелец (файл). /sc disable этого
 *   скрипта убирает его группы и возвращает ванильные части, которые прятал только он ({@link #unloadScript}).
 *
 * Все методы вызываются из главного потока сервера (скрипты выполняются в нём).
 */
public final class HudManager {
    /** Максимум элементов HUD у одного игрока (защита клиента от "бесконечных" скриптов). */
    public static final int MAX_ELEMENTS = 128;

    private static final Map<UUID, PlayerHud> HUDS = new HashMap<>();

    /** Владелец для действий не из файла (эффект-команды в чате): автоматически не убирается. */
    private static final String NO_OWNER = "";

    private static final class PlayerHud {
        final LinkedHashMap<String, HudEntry> entries = new LinkedHashMap<>();
        /** Группа -> файл скрипта, который её создал последним. */
        final Map<String, String> groupOwners = new HashMap<>();
        /** Файл скрипта -> какие ванильные части он спрятал. Итоговая маска — объединение. */
        final Map<String, Integer> vanillaByOwner = new HashMap<>();
        int vanillaMask;

        int computeVanillaMask() {
            int m = 0;
            for (int v : vanillaByOwner.values()) {
                m |= v;
            }
            return m;
        }

        /** Забыть владельцев групп, от которых не осталось элементов. */
        void pruneOwners() {
            groupOwners.keySet().removeIf(g -> entries.values().stream().noneMatch(e -> e.group().equals(g)));
        }
    }

    private static String owner(@Nullable String script) {
        return script == null ? NO_OWNER : script;
    }

    private HudManager() {
    }

    private static PlayerHud hud(ServerPlayer player) {
        return HUDS.computeIfAbsent(player.getUUID(), u -> new PlayerHud());
    }

    private static void send(ServerPlayer player, List<HudOp> ops) {
        if (!ops.isEmpty()) {
            ModNetwork.sendTo(player, new HudPacket(ops));
        }
    }

    /**
     * create hud "группа": заменяет все элементы группы новыми. Элементы с теми же id из ДРУГИХ групп
     * тоже заменяются (id уникален у игрока). Если группа не изменилась — ничего не отправляется.
     */
    public static void create(ServerPlayer player, String group, List<HudEntry> newEntries, @Nullable String script) {
        PlayerHud hud = hud(player);
        hud.groupOwners.put(group, owner(script));
        List<HudEntry> old = new ArrayList<>();
        for (HudEntry e : hud.entries.values()) {
            if (e.group().equals(group)) {
                old.add(e);
            }
        }
        if (old.equals(newEntries)) {
            return;
        }
        // Проверка лимита ДО изменения состояния.
        int remaining = 0;
        for (HudEntry e : hud.entries.values()) {
            if (!e.group().equals(group) && newEntries.stream().noneMatch(n -> n.id().equals(e.id()))) {
                remaining++;
            }
        }
        if (remaining + newEntries.size() > MAX_ELEMENTS) {
            throw new ScriptRuntimeException(Messages.get("hud.too_many", MAX_ELEMENTS));
        }
        List<HudOp> ops = new ArrayList<>();
        if (!old.isEmpty()) {
            hud.entries.values().removeIf(e -> e.group().equals(group));
            ops.add(new HudOp.RemoveGroup(group));
        }
        for (HudEntry e : newEntries) {
            hud.entries.remove(e.id());
            hud.entries.put(e.id(), e);
        }
        ops.add(new HudOp.Put(List.copyOf(newEntries)));
        hud.pruneOwners();
        send(player, ops);
    }

    /** set value/max of bar: null — оставить как было. Неизвестный id или не полоса — тихо пропускается. */
    public static void setBar(ServerPlayer player, String id, String value, String max) {
        PlayerHud hud = HUDS.get(player.getUUID());
        HudEntry entry = hud == null ? null : hud.entries.get(id);
        if (entry == null || !(entry.element() instanceof com.scriptcraft.screen.BarElement bar)) {
            return;
        }
        String v = value != null ? value : bar.value();
        String m = max != null ? max : bar.max();
        if (v.equals(bar.value()) && m.equals(bar.max())) {
            return;
        }
        hud.entries.put(id, Objects.requireNonNull(entry.withValues(v, m)));
        send(player, List.of(new HudOp.Value(id, v, m)));
    }

    /** set text of hud "id". */
    public static void setText(ServerPlayer player, String id, String text) {
        PlayerHud hud = HUDS.get(player.getUUID());
        HudEntry entry = hud == null ? null : hud.entries.get(id);
        if (entry == null) {
            return;
        }
        HudEntry updated = entry.withText(text);
        if (updated == null || updated.equals(entry)) {
            return;
        }
        hud.entries.put(id, updated);
        send(player, List.of(new HudOp.Text(id, text)));
    }

    /** show/hide hud "имя": имя элемента ИЛИ группы (тогда все её элементы). */
    public static void setVisible(ServerPlayer player, String name, boolean visible) {
        PlayerHud hud = HUDS.get(player.getUUID());
        if (hud == null) {
            return;
        }
        List<HudOp> ops = new ArrayList<>();
        for (Map.Entry<String, HudEntry> me : hud.entries.entrySet()) {
            HudEntry e = me.getValue();
            if ((e.id().equals(name) || e.group().equals(name)) && e.visible() != visible) {
                me.setValue(e.withVisible(visible));
                ops.add(new HudOp.Visible(e.id(), visible));
            }
        }
        send(player, ops);
    }

    /** remove hud "имя": элемент или вся группа. */
    public static void remove(ServerPlayer player, String name) {
        PlayerHud hud = HUDS.get(player.getUUID());
        if (hud == null) {
            return;
        }
        List<HudOp> ops = new ArrayList<>();
        if (hud.entries.remove(name) != null) {
            ops.add(new HudOp.Remove(name));
        }
        if (hud.entries.values().removeIf(e -> e.group().equals(name))) {
            ops.add(new HudOp.RemoveGroup(name));
        }
        hud.pruneOwners();
        send(player, ops);
    }

    /** clear hud: убрать все элементы и вернуть ванильный HUD. */
    public static void clear(ServerPlayer player) {
        PlayerHud hud = HUDS.remove(player.getUUID());
        if (hud == null) {
            return;
        }
        List<HudOp> ops = new ArrayList<>();
        if (!hud.entries.isEmpty()) {
            ops.add(new HudOp.Clear());
        }
        if (hud.vanillaMask != 0) {
            ops.add(new HudOp.Vanilla(0));
        }
        send(player, ops);
    }

    /** hide/show vanilla <часть>: маска из {@link VanillaHudPart}. */
    public static void setVanilla(ServerPlayer player, int mask, boolean hide, @Nullable String script) {
        PlayerHud hud = hud(player);
        if (hide) {
            hud.vanillaByOwner.merge(owner(script), mask, (a, b) -> a | b);
        } else {
            // show vanilla — явная просьба вернуть часть: снимаем её у всех скриптов.
            hud.vanillaByOwner.replaceAll((o, m) -> m & ~mask);
            hud.vanillaByOwner.values().removeIf(m -> m == 0);
        }
        int updated = hud.computeVanillaMask();
        if (updated == hud.vanillaMask) {
            return;
        }
        hud.vanillaMask = updated;
        send(player, List.of(new HudOp.Vanilla(updated)));
    }

    /**
     * /sc disable &lt;скрипт&gt;: у всех игроков (и онлайн, и сохранённых офлайн) убрать группы, созданные этим
     * скриптом, и вернуть ванильные части, которые он прятал (если их не прячет другой скрипт).
     * Онлайн-игрокам сразу уходят изменения, офлайн получат уже чистое состояние при входе.
     */
    public static void unloadScript(String script, MinecraftServer server) {
        for (Map.Entry<UUID, PlayerHud> me : HUDS.entrySet()) {
            PlayerHud hud = me.getValue();
            List<HudOp> ops = new ArrayList<>();
            for (Map.Entry<String, String> go : List.copyOf(hud.groupOwners.entrySet())) {
                if (go.getValue().equals(script)) {
                    String group = go.getKey();
                    hud.groupOwners.remove(group);
                    if (hud.entries.values().removeIf(e -> e.group().equals(group))) {
                        ops.add(new HudOp.RemoveGroup(group));
                    }
                }
            }
            if (hud.vanillaByOwner.remove(script) != null) {
                int updated = hud.computeVanillaMask();
                if (updated != hud.vanillaMask) {
                    hud.vanillaMask = updated;
                    ops.add(new HudOp.Vanilla(updated));
                }
            }
            ServerPlayer player = server.getPlayerList().getPlayer(me.getKey());
            if (player != null) {
                send(player, ops);
            }
        }
        HUDS.values().removeIf(h -> h.entries.isEmpty() && h.vanillaMask == 0);
    }

    /** Вход игрока: клиент начинает с пустого HUD, поэтому отправляем всё сохранённое состояние целиком. */
    public static void onLogin(ServerPlayer player) {
        PlayerHud hud = HUDS.get(player.getUUID());
        if (hud == null) {
            return;
        }
        List<HudOp> ops = new ArrayList<>();
        if (!hud.entries.isEmpty()) {
            ops.add(new HudOp.Put(List.copyOf(hud.entries.values())));
        }
        if (hud.vanillaMask != 0) {
            ops.add(new HudOp.Vanilla(hud.vanillaMask));
        }
        send(player, ops);
    }

    /** Остановка сервера (в одиночной игре — выход из мира): следующий мир начинает с чистого HUD. */
    public static void onServerStopped() {
        HUDS.clear();
    }
}
