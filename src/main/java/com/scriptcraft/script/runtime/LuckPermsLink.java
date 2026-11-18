package com.scriptcraft.script.runtime;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.lang.Messages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * LuckPerms без жёсткой зависимости: мод Forge (luckperms) или плагин Bukkit на гибриде.
 * Правки онлайн-игрока применяются сразу, оффлайн — через load/save LuckPerms.
 */
public final class LuckPermsLink {
    public enum Presence {
        MOD, PLUGIN, BOTH, API, NONE
    }

    private static boolean warned;

    private LuckPermsLink() {
    }

    public static Presence presence() {
        boolean mod = ModList.get().isLoaded("luckperms");
        boolean plugin = bukkitPlugin();
        if (mod && plugin) {
            return Presence.BOTH;
        }
        if (mod) {
            return Presence.MOD;
        }
        if (plugin) {
            return Presence.PLUGIN;
        }
        return apiClass() ? Presence.API : Presence.NONE;
    }

    public static boolean available() {
        return presence() != Presence.NONE;
    }

    public static void logPresence() {
        String key = switch (presence()) {
            case MOD -> "lp.mod";
            case PLUGIN -> "lp.plugin";
            case BOTH -> "lp.both";
            case API -> "lp.api";
            case NONE -> "lp.none";
        };
        ScriptCraft.LOGGER.info("[ScriptCraft] {}", Messages.get(key));
    }

    /**
     * null — LuckPerms не ответил. Ответ есть и у мода Forge, и у плагина Bukkit:
     * пользователь ищется по UUID, затем через адаптер ServerPlayer, затем через игрока Bukkit.
     */
    @Nullable
    public static Boolean check(ServerPlayer player, String node) {
        if (player == null || node == null || node.isBlank() || !available()) {
            return null;
        }
        Object user = user(player);
        if (user == null) {
            return null;
        }
        try {
            Object cached = call(user, "getCachedData");
            Object data = permissionData(cached);
            Object tri = call(data, "checkPermission", node.strip());
            Object value = call(tri, "asBoolean");
            return value instanceof Boolean flag ? flag : null;
        } catch (RuntimeException e) {
            warn(e);
            return null;
        }
    }

    private static Object permissionData(Object cached) {
        try {
            return call(cached, "getPermissionData");
        } catch (RuntimeException first) {
            Object options = call(cached, "getQueryOptions");
            if (options instanceof java.util.Optional<?> optional) {
                options = optional.orElse(null);
            }
            if (options == null) {
                throw first;
            }
            return call(cached, "getPermissionData", options);
        }
    }

    public static void edit(ServerPlayer player, Consumer<Object> action) {
        edit(player.getUUID(), player, action);
    }

    public static void edit(UUID uuid, @Nullable ServerPlayer online, Consumer<Object> action) {
        Object api = requireApi();
        if (online != null) {
            Object user = user(online);
            if (user != null) {
                action.accept(user);
                invalidate(user);
                save(api, user);
                return;
            }
        }
        Object manager = call(api, "getUserManager");
        call(manager, "modifyUser", uuid, (Consumer<Object>) user -> {
            try {
                action.accept(user);
                invalidate(user);
            } catch (RuntimeException e) {
                ScriptCraft.LOGGER.warn("[ScriptCraft] LuckPerms: {}", e.getMessage());
            }
        });
    }

    public static void setPermission(Object user, String node, boolean value, long seconds) {
        String key = node.strip();
        clearKey(user, key);
        addNode(user, node(key, value, seconds));
    }

    public static void removePermission(Object user, String node) {
        clearKey(user, node.strip());
    }

    public static void clearPermissions(Object user) {
        clearType(user, "PERMISSION");
        clearType(user, "REGEX_PERMISSION");
    }

    public static void addGroup(Object user, String group, long seconds) {
        String name = group(group);
        clearKey(user, "group." + name);
        addNode(user, node("group." + name, true, seconds));
    }

    public static void removeGroup(Object user, String group) {
        clearKey(user, "group." + group(group));
    }

    /** Делает группу основной и добавляет её игроку. */
    public static void setPrimaryGroup(Object user, String group, long seconds) {
        String name = group(group);
        addGroup(user, name, seconds);
        call(user, "setPrimaryGroup", name);
    }

    public static void clearGroups(Object user) {
        clearType(user, "INHERITANCE");
        try {
            call(user, "setPrimaryGroup", "default");
        } catch (RuntimeException ignored) {
            // старый API без setPrimaryGroup — группы уже сняты
        }
    }

    public static void setPrefix(Object user, String text, int priority, long seconds, boolean unused) {
        addTyped(user, "net.luckperms.api.node.types.PrefixNode", text, priority, seconds);
    }

    public static void clearPrefix(Object user, int priority, boolean onlyPriority) {
        clearTyped(user, "PREFIX", onlyPriority ? priority : null);
    }

    public static void setSuffix(Object user, String text, int priority, long seconds, boolean unused) {
        addTyped(user, "net.luckperms.api.node.types.SuffixNode", text, priority, seconds);
    }

    public static void clearSuffix(Object user, int priority, boolean onlyPriority) {
        clearTyped(user, "SUFFIX", onlyPriority ? priority : null);
    }

    public static void setMeta(Object user, String key, String value) {
        removeMeta(user, key);
        Object builder = callStatic("net.luckperms.api.node.types.MetaNode", "builder", key, value);
        addNode(user, call(builder, "build"));
    }

    public static void removeMeta(Object user, String key) {
        String want = key.strip();
        clearIf(user, node -> "META".equals(typeName(node)) && want.equalsIgnoreCase(metaKey(node)));
    }

    public static void clearMeta(Object user) {
        clearType(user, "META");
    }

    @Nullable
    public static String prefix(ServerPlayer player) {
        return text(meta(user(player)), "getPrefix");
    }

    @Nullable
    public static String suffix(ServerPlayer player) {
        return text(meta(user(player)), "getSuffix");
    }

    @Nullable
    public static String primaryGroup(ServerPlayer player) {
        return primaryOf(user(player));
    }

    @Nullable
    public static String groups(ServerPlayer player) {
        return groupsOf(user(player));
    }

    @Nullable
    public static String meta(ServerPlayer player, String key) {
        return metaValue(user(player), key);
    }

    /**
     * Префикс, суффикс, основная группа, список групп или meta.
     * Онлайн — первый игрок цели. Оффлайн-имя — уже загруженный пользователь LuckPerms.
     */
    @Nullable
    public static String value(ExecutionContext ctx, Target target, String kind, @Nullable String key) {
        if (!available()) {
            return null;
        }
        Object user = subject(ctx, target);
        return switch (kind) {
            case "prefix" -> text(meta(user), "getPrefix");
            case "suffix" -> text(meta(user), "getSuffix");
            case "group" -> primaryOf(user);
            case "groups" -> groupsOf(user);
            case "meta" -> metaValue(user, key);
            default -> null;
        };
    }

    /** Каждый онлайн-игрок цели в группе. Пустая цель с именем проверяется по загруженному профилю. */
    public static boolean member(ExecutionContext ctx, Target target, String group) {
        if (!available() || group == null || group.isBlank()) {
            return false;
        }
        List<ServerPlayer> players = target.select(ctx);
        if (!players.isEmpty()) {
            for (ServerPlayer player : players) {
                if (!inGroup(player, group)) {
                    return false;
                }
            }
            return true;
        }
        return inGroupUser(subject(ctx, target), group);
    }

    public static boolean inGroup(ServerPlayer player, String group) {
        if (!available() || player == null || group == null || group.isBlank()) {
            return false;
        }
        return inGroupUser(user(player), group);
    }

    private static boolean inGroupUser(@Nullable Object user, String group) {
        if (user == null) {
            return false;
        }
        String want = LuckPermsLink.group(group);
        String primary = primaryOf(user);
        if (want.equalsIgnoreCase(primary == null ? "" : primary)) {
            return true;
        }
        String all = groupsOf(user);
        if (all == null) {
            return false;
        }
        for (String one : all.split(", ")) {
            if (one.equalsIgnoreCase(want)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static String primaryOf(@Nullable Object user) {
        if (user == null) {
            return null;
        }
        Object value = call(user, "getPrimaryGroup");
        return value == null ? null : value.toString();
    }

    @Nullable
    private static String groupsOf(@Nullable Object user) {
        if (user == null) {
            return null;
        }
        List<String> names = new ArrayList<>();
        Object data = call(user, "data");
        Object collection = call(data, "toCollection");
        if (collection instanceof Iterable<?> nodes) {
            for (Object node : nodes) {
                String key = String.valueOf(call(node, "getKey"));
                if (key.startsWith("group.")) {
                    names.add(key.substring("group.".length()));
                }
            }
        }
        String primary = primaryOf(user);
        if (primary != null && !primary.isBlank() && names.stream().noneMatch(primary::equalsIgnoreCase)) {
            names.add(0, primary);
        }
        return names.isEmpty() ? null : String.join(", ", names);
    }

    @Nullable
    private static String metaValue(@Nullable Object user, @Nullable String key) {
        Object data = meta(user);
        if (data == null || key == null || key.isBlank()) {
            return null;
        }
        Object value = call(data, "getMetaValue", key);
        return value == null ? null : value.toString();
    }

    @Nullable
    private static Object subject(ExecutionContext ctx, Target target) {
        List<ServerPlayer> players = target.select(ctx);
        if (!players.isEmpty()) {
            return user(players.get(0));
        }
        String name = Effects.storedName(target, ctx);
        if (name == null) {
            return null;
        }
        UUID id = profileId(ctx, name);
        return id == null ? null : loadedUser(id);
    }

    @Nullable
    private static Object loadedUser(UUID id) {
        try {
            Object api = api();
            if (api == null) {
                return null;
            }
            return call(call(api, "getUserManager"), "getUser", id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    public static UUID profileId(ExecutionContext ctx, String name) {
        var profile = Effects.profileByName(ctx, name);
        return profile == null ? null : profile.getId();
    }

    private static void addTyped(Object user, String className, String text, int priority, long seconds) {
        Object builder = callStatic(className, "builder", text, priority);
        if (seconds > 0) {
            builder = call(builder, "expiry", Duration.ofSeconds(seconds));
        }
        addNode(user, call(builder, "build"));
    }

    private static Object node(String key, boolean value, long seconds) {
        Object builder = callStatic("net.luckperms.api.node.Node", "builder", key);
        if (!value) {
            builder = call(builder, "value", false);
        }
        if (seconds > 0) {
            builder = call(builder, "expiry", Duration.ofSeconds(seconds));
        }
        return call(builder, "build");
    }

    private static void addNode(Object user, Object node) {
        call(call(user, "data"), "add", node);
    }

    private static void clearKey(Object user, String key) {
        clearIf(user, node -> key.equalsIgnoreCase(String.valueOf(call(node, "getKey"))));
    }

    private static void clearType(Object user, String type) {
        clearIf(user, node -> type.equals(typeName(node)));
    }

    private static void clearTyped(Object user, String type, @Nullable Integer priority) {
        clearIf(user, node -> type.equals(typeName(node)) && (priority == null || priority == priorityOf(node)));
    }

    private static void clearIf(Object user, Predicate<Object> test) {
        call(call(user, "data"), "clear", test);
    }

    private static int priorityOf(Object node) {
        try {
            Object value = call(node, "getPriority");
            return value instanceof Number n ? n.intValue() : Integer.MIN_VALUE;
        } catch (RuntimeException e) {
            return Integer.MIN_VALUE;
        }
    }

    private static String metaKey(Object node) {
        try {
            Object value = call(node, "getMetaKey");
            return value == null ? "" : value.toString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String typeName(Object node) {
        Object type = call(node, "getType");
        if (type instanceof Enum<?> e) {
            return e.name();
        }
        return type == null ? "" : type.toString().toUpperCase(Locale.ROOT);
    }

    @Nullable
    private static Object user(ServerPlayer player) {
        if (!available() || player == null) {
            return null;
        }
        Object loaded = loadedUser(player.getUUID());
        if (loaded != null) {
            return loaded;
        }
        Object forge = forgeUser(player);
        return forge != null ? forge : bukkitUser(player);
    }

    /** Адаптер мода Forge: ServerPlayer. */
    @Nullable
    private static Object forgeUser(ServerPlayer player) {
        try {
            Object api = api();
            if (api == null) {
                return null;
            }
            Object adapter = call(api, "getPlayerAdapter", ServerPlayer.class);
            return call(adapter, "getUser", player);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Адаптер плагина Bukkit, если LuckPerms стоит не модом. */
    @Nullable
    private static Object bukkitUser(ServerPlayer player) {
        try {
            Class<?> playerClass = Class.forName("org.bukkit.entity.Player");
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object bukkitPlayer = bukkit.getMethod("getPlayer", UUID.class).invoke(null, player.getUUID());
            if (bukkitPlayer == null) {
                return null;
            }
            Object api = api();
            if (api == null) {
                return null;
            }
            Object adapter = call(api, "getPlayerAdapter", playerClass);
            return call(adapter, "getUser", bukkitPlayer);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static Object meta(@Nullable Object user) {
        if (user == null) {
            return null;
        }
        try {
            return call(call(user, "getCachedData"), "getMetaData");
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static String text(@Nullable Object meta, String method) {
        if (meta == null) {
            return null;
        }
        try {
            Object value = call(meta, method);
            return value == null || value.toString().isBlank() ? null : value.toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void invalidate(Object user) {
        try {
            call(call(user, "getCachedData"), "invalidate");
        } catch (RuntimeException ignored) {
            // не у всех версий есть invalidate — saveUser всё равно пересчитает кэш
        }
    }

    private static void save(Object api, Object user) {
        call(call(api, "getUserManager"), "saveUser", user);
    }

    private static Object requireApi() {
        Object api = api();
        if (api == null) {
            throw new ScriptRuntimeException(Messages.get("lp.missing"));
        }
        return api;
    }

    @Nullable
    private static Object api() {
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            return provider.getMethod("get").invoke(null);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (ReflectiveOperationException e) {
            warn(e);
            return null;
        }
    }

    private static boolean apiClass() {
        try {
            Class.forName("net.luckperms.api.LuckPermsProvider");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean bukkitPlugin() {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object plugins = bukkit.getMethod("getPluginManager").invoke(null);
            Object plugin = null;
            for (Method method : plugins.getClass().getMethods()) {
                if (method.getName().equals("getPlugin") && method.getParameterCount() == 1) {
                    plugin = method.invoke(plugins, "LuckPerms");
                    break;
                }
            }
            if (plugin == null) {
                return false;
            }
            Object enabled = plugin.getClass().getMethod("isEnabled").invoke(plugin);
            return Boolean.TRUE.equals(enabled);
        } catch (ClassNotFoundException e) {
            return false;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static String group(String raw) {
        String name = raw.strip().toLowerCase(Locale.ROOT);
        if (name.isEmpty() || name.equals("<none>")) {
            throw new ScriptRuntimeException(Messages.get("lp.bad_group", raw));
        }
        return name;
    }

    private static void warn(Exception e) {
        if (warned) {
            return;
        }
        warned = true;
        ScriptCraft.LOGGER.warn("[ScriptCraft] LuckPerms: {}", e.toString());
    }

    private static Object call(Object target, String name, Object... args) {
        Method method = find(target.getClass().getMethods(), name, args, false);
        if (method == null) {
            throw new ScriptRuntimeException(Messages.get("lp.fail", name));
        }
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new ScriptRuntimeException(cause.getMessage() == null ? cause.toString() : cause.getMessage());
        } catch (IllegalAccessException e) {
            throw new ScriptRuntimeException(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static Object callStatic(String className, String name, Object... args) {
        try {
            Class<?> type = Class.forName(className);
            Method method = find(type.getMethods(), name, args, true);
            if (method == null) {
                throw new ScriptRuntimeException(Messages.get("lp.fail", name));
            }
            return method.invoke(null, args);
        } catch (ClassNotFoundException e) {
            throw new ScriptRuntimeException(Messages.get("lp.missing"));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new ScriptRuntimeException(cause.getMessage() == null ? cause.toString() : cause.getMessage());
        } catch (IllegalAccessException e) {
            throw new ScriptRuntimeException(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    @Nullable
    private static Method find(Method[] methods, String name, Object[] args, boolean stat) {
        Method loose = null;
        for (Method method : methods) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
                continue;
            }
            if (stat && !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (!fits(method.getParameterTypes(), args)) {
                continue;
            }
            if (loose == null) {
                loose = method;
            }
        }
        return loose;
    }

    private static boolean fits(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            if (args[i] == null || wrap(types[i]).isInstance(args[i])) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        return type;
    }
}
