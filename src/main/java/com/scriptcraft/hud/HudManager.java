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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Серверное состояние HUD каждого игрока (что у него сейчас нарисовано) и отправка ИЗМЕНЕНИЙ клиенту.
 *
 * - Если новое значение совпадает с уже отправленным, пакет не уходит (можно спокойно обновлять полосу
 *   из частых событий — сеть не забивается).
 * - {переменная} в тексте или в value/max полосы запоминается. Запись этой переменной сразу пересчитывает
 *   элемент и шлёт пакет только если строка изменилась. Нет подписчиков — запись переменной ничего не шлёт.
 * - Состояние хранится в памяти сервера: при выходе игрока оно сохраняется, а при входе отправляется
 *   заново (клиент свой HUD при отключении стирает). Очищается при остановке сервера.
 * - /sc reload заново выполняет HUD из on load и on join этого скрипта для игроков онлайн.
 * - У каждой группы и у каждого "hide vanilla" запоминается скрипт-владелец (файл). /sc disable этого
 *   скрипта убирает его группы и возвращает ванильные части, которые прятал только он ({@link #unloadScript}).
 *
 * Все методы вызываются из главного потока сервера (скрипты выполняются в нём).
 */
public final class HudManager {
    /** Максимум элементов HUD у одного игрока (защита клиента от "бесконечных" скриптов). */
    public static final int MAX_ELEMENTS = 128;

    private static final Map<UUID, PlayerHud> HUDS = new HashMap<>();

    /** Имя переменной -> элементы HUD, которые её показывают. Пусто для переменных вне HUD. */
    private static final Map<String, Set<Watch>> BY_VAR = new HashMap<>();

    /** Сервер, чтобы при записи переменной найти игрока и отправить ему пакет. */
    @Nullable
    private static MinecraftServer server;

    /** Элемент HUD одного игрока, который следит за переменной. */
    private record Watch(UUID player, String elementId) {
    }

    /** Владелец для действий не из файла (эффект-команды в чате): автоматически не убирается. */
    private static final String NO_OWNER = "";

    private static final class PlayerHud {
        final LinkedHashMap<String, HudEntry> entries = new LinkedHashMap<>();
        /** Группа -> файл скрипта, который её создал последним. */
        final Map<String, String> groupOwners = new HashMap<>();
        /** Файл скрипта -> какие ванильные части он спрятал. Итоговая маска — объединение. */
        final Map<String, Integer> vanillaByOwner = new HashMap<>();
        /** id элемента -> шаблон с {переменными}, если элемент за ними следит. */
        final Map<String, HudBinding> bindings = new HashMap<>();
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
    public static void create(ServerPlayer player, String group, List<HudEntry> newEntries, List<HudBinding> bindings,
                               @Nullable String script) {
        server = player.getServer();
        PlayerHud hud = hud(player);
        hud.groupOwners.put(group, owner(script));
        List<HudEntry> old = new ArrayList<>();
        for (HudEntry e : hud.entries.values()) {
            if (e.group().equals(group)) {
                old.add(e);
            }
        }
        if (old.equals(newEntries)) {
            install(player.getUUID(), hud, bindings);
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
        for (HudEntry e : old) {
            if (newEntries.stream().noneMatch(n -> n.id().equals(e.id()))) {
                dropElement(hud, player.getUUID(), e.id());
            }
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
        install(player.getUUID(), hud, bindings);
        send(player, ops);
    }

    /**
     * Переменная записана или удалена. Обновляет только те элементы HUD, которые её показывают,
     * и только если текст на экране действительно другой.
     */
    public static void onVariable(String name) {
        Set<Watch> watches = BY_VAR.get(name);
        if (watches == null || watches.isEmpty()) {
            return;
        }
        for (Watch watch : List.copyOf(watches)) {
            refresh(watch);
        }
    }

    /** set text заново привязывает текст к шаблону. null — в тексте больше нет {переменных}. */
    public static void rebindText(ServerPlayer player, String id, @Nullable String textTemplate) {
        PlayerHud hud = HUDS.get(player.getUUID());
        if (hud == null || !hud.entries.containsKey(id)) {
            return;
        }
        HudBinding old = hud.bindings.get(id);
        installOne(player.getUUID(), hud, new HudBinding(id, textTemplate,
                old == null ? null : old.value(), old == null ? null : old.max()));
    }

    /** set value/max. touch=false — это поле не трогали. template null — поле больше не следит за переменной. */
    public static void rebindBar(ServerPlayer player, String id, boolean touchValue, @Nullable String valueTemplate,
                                 boolean touchMax, @Nullable String maxTemplate) {
        PlayerHud hud = HUDS.get(player.getUUID());
        if (hud == null || !hud.entries.containsKey(id)) {
            return;
        }
        HudBinding old = hud.bindings.get(id);
        String value = touchValue ? valueTemplate : (old == null ? null : old.value());
        String max = touchMax ? maxTemplate : (old == null ? null : old.max());
        installOne(player.getUUID(), hud, new HudBinding(id, old == null ? null : old.text(), value, max));
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
        List<String> gone = new ArrayList<>();
        if (hud.entries.remove(name) != null) {
            gone.add(name);
            ops.add(new HudOp.Remove(name));
        }
        List<String> groupIds = new ArrayList<>();
        for (HudEntry e : hud.entries.values()) {
            if (e.group().equals(name)) {
                groupIds.add(e.id());
            }
        }
        if (!groupIds.isEmpty()) {
            groupIds.forEach(hud.entries::remove);
            gone.addAll(groupIds);
            ops.add(new HudOp.RemoveGroup(name));
        }
        for (String id : gone) {
            dropElement(hud, player.getUUID(), id);
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
        dropAll(player.getUUID(), hud);
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
                    List<String> ids = new ArrayList<>();
                    for (HudEntry e : hud.entries.values()) {
                        if (e.group().equals(group)) {
                            ids.add(e.id());
                        }
                    }
                    if (!ids.isEmpty()) {
                        ids.forEach(hud.entries::remove);
                        for (String id : ids) {
                            dropElement(hud, me.getKey(), id);
                        }
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
        server = player.getServer();
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
        BY_VAR.clear();
        server = null;
    }

    private static void refresh(Watch watch) {
        PlayerHud hud = HUDS.get(watch.player());
        HudBinding binding = hud == null ? null : hud.bindings.get(watch.elementId());
        if (hud == null || binding == null || !hud.entries.containsKey(watch.elementId())) {
            if (hud != null && binding != null) {
                dropElement(hud, watch.player(), watch.elementId());
            } else {
                BY_VAR.values().forEach(set -> set.remove(watch));
                BY_VAR.values().removeIf(Set::isEmpty);
            }
            return;
        }
        if (binding.text() != null) {
            applyText(watch.player(), watch.elementId(), com.scriptcraft.script.runtime.ExecutionContext.fillVariables(binding.text()));
        }
        if (binding.value() != null || binding.max() != null) {
            String value = binding.value() == null ? null
                    : com.scriptcraft.script.runtime.ExecutionContext.fillVariables(binding.value());
            String max = binding.max() == null ? null
                    : com.scriptcraft.script.runtime.ExecutionContext.fillVariables(binding.max());
            applyBar(watch.player(), watch.elementId(), value, max);
        }
    }

    private static void applyText(UUID uuid, String id, String text) {
        PlayerHud hud = HUDS.get(uuid);
        HudEntry entry = hud == null ? null : hud.entries.get(id);
        if (entry == null) {
            return;
        }
        HudEntry updated = entry.withText(text);
        if (updated == null || updated.equals(entry)) {
            return;
        }
        hud.entries.put(id, updated);
        ServerPlayer online = server == null ? null : server.getPlayerList().getPlayer(uuid);
        if (online != null) {
            send(online, List.of(new HudOp.Text(id, text)));
        }
    }

    private static void applyBar(UUID uuid, String id, @Nullable String value, @Nullable String max) {
        PlayerHud hud = HUDS.get(uuid);
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
        ServerPlayer online = server == null ? null : server.getPlayerList().getPlayer(uuid);
        if (online != null) {
            send(online, List.of(new HudOp.Value(id, v, m)));
        }
    }

    private static void install(UUID uuid, PlayerHud hud, List<HudBinding> bindings) {
        for (HudBinding binding : bindings) {
            installOne(uuid, hud, binding);
        }
    }

    private static void installOne(UUID uuid, PlayerHud hud, HudBinding binding) {
        dropElement(hud, uuid, binding.id());
        if (binding.isEmpty()) {
            return;
        }
        hud.bindings.put(binding.id(), binding);
        Set<String> names = new HashSet<>();
        binding.collectNames(names);
        for (String name : names) {
            BY_VAR.computeIfAbsent(name, k -> new HashSet<>()).add(new Watch(uuid, binding.id()));
        }
    }

    private static void dropAll(UUID uuid, PlayerHud hud) {
        for (String id : List.copyOf(hud.bindings.keySet())) {
            dropElement(hud, uuid, id);
        }
    }

    private static void dropElement(PlayerHud hud, UUID uuid, String id) {
        HudBinding binding = hud.bindings.remove(id);
        if (binding == null) {
            return;
        }
        Set<String> names = new HashSet<>();
        binding.collectNames(names);
        Watch watch = new Watch(uuid, id);
        for (String name : names) {
            Set<Watch> set = BY_VAR.get(name);
            if (set == null) {
                continue;
            }
            set.remove(watch);
            if (set.isEmpty()) {
                BY_VAR.remove(name);
            }
        }
    }
}
