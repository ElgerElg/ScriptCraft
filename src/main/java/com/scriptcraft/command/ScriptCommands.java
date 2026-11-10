package com.scriptcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.scriptcraft.ScriptCraft;
import com.scriptcraft.config.ScriptCraftConfig;
import com.scriptcraft.lang.Feedback;
import com.scriptcraft.lang.Messages;
import com.scriptcraft.registry.Bindings;
import com.scriptcraft.resources.ResourceIndex;
import com.scriptcraft.resources.ResourceSync;
import com.scriptcraft.script.ScriptLoader;
import com.scriptcraft.script.ScriptPaths;
import com.scriptcraft.script.parser.ArgSpec;
import com.scriptcraft.script.parser.CommandDefinition;
import com.scriptcraft.script.runtime.ArgType;
import com.scriptcraft.script.runtime.ItemResolver;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Команды мода (/scriptcraft, /sc, /script) и команды, объявленные в скриптах (command /hello:).
 *
 * Почему три одинаковых дерева, а не redirect: у Brigadier redirect не переносит executes() корневого узла,
 * поэтому "/sc" без аргументов не показал бы справку. Одна функция строит дерево трижды — поведение идентично.
 *
 * Ограничения горячей перезагрузки команд (Brigadier не умеет удалять узлы):
 * - НОВЫЕ команды при reload/enable добавляются в дерево, и дерево заново отправляется игрокам;
 * - УДАЛЁННЫЕ и ВЫКЛЮЧЕННЫЕ команды остаются в дереве до перезапуска, но при вызове пишут "выключена";
 * - изменённые действия и permission применяются сразу (обработчик и requires читают актуальную программу).
 */
public final class ScriptCommands {
    /** Корневые команды мода. Первая — основная, остальные — синонимы. */
    public static final List<String> ROOTS = List.of("scriptcraft", "sc", "script");

    /** Имена, которые зарегистрировали мы (чтобы не путать со своими при повторной регистрации). */
    private static final Set<String> REGISTERED = new HashSet<>();
    /** Нужен ItemArgument: без него тип item в команде скрипта не собрать. */
    private static net.minecraft.commands.CommandBuildContext BUILD;

    /** Подсказки: включённые скрипты (для reload и disable). */
    private static final SuggestionProvider<CommandSourceStack> ENABLED_SCRIPTS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(suggestions(name -> !ScriptPaths.isDisabled(name)), builder);
    /**
     * Имена скриптов для /sc reload. config и resources — не скрипты, а отдельные слова команды,
     * поэтому файл с таким именем в подсказках не показывается и этой командой не перечитывается.
     */
    private static final SuggestionProvider<CommandSourceStack> RELOAD_SCRIPTS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(suggestions(name -> !ScriptPaths.isDisabled(name))
                    .filter(name -> !name.equals("config") && !name.equals("resources")), builder);
    /** Подсказки: выключенные скрипты (для enable), показываются без минуса. */
    private static final SuggestionProvider<CommandSourceStack> DISABLED_SCRIPTS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(suggestions(ScriptPaths::isDisabled), builder);

    /** Подсказки: id привязанных define (для /sc give). */
    private static final SuggestionProvider<CommandSourceStack> BOUND_IDS = (ctx, builder) -> {
        return SharedSuggestionProvider.suggest(Bindings.current().allIds(), builder);
    };

    private ScriptCommands() {
    }

    /** Имена скриптов без ".sc" и без ведущего "-" (как их удобно набирать). */
    private static Stream<String> suggestions(Predicate<String> filter) {
        return ScriptPaths.listScripts().stream()
                .filter(filter)
                .map(ScriptPaths::enabledName)
                .map(ScriptCommands::withoutExtension);
    }

    private static String withoutExtension(String name) {
        if (name.endsWith(ScriptPaths.EXTENSION)) {
            return name.substring(0, name.length() - ScriptPaths.EXTENSION.length());
        }
        if (name.endsWith(ScriptPaths.LEGACY_EXTENSION)) {
            return name.substring(0, name.length() - ScriptPaths.LEGACY_EXTENSION.length());
        }
        return name;
    }

    /** Вызывается из RegisterCommandsEvent — каждый раз с НОВЫМ диспетчером. */
    public static void registerAll(CommandDispatcher<CommandSourceStack> dispatcher,
                                   net.minecraft.commands.CommandBuildContext build) {
        BUILD = build;
        REGISTERED.clear();
        for (String root : ROOTS) {
            if (root.equals("script") && dispatcher.getRoot().getChild(root) != null) {
                // На случай, если другой мод уже занял /script — основная команда и /sc всё равно работают.
                ScriptCraft.LOGGER.warn("[ScriptCraft] Команда /script уже занята другим модом — синоним пропущен");
                continue;
            }
            dispatcher.register(buildRoot(root));
        }
        registerScriptCommands(dispatcher);
    }

    /** Дерево одной корневой команды: help, reload [config|resources|скрипт], enable, disable, list. */
    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot(String root) {
        return Commands.literal(root)
                .requires(src -> src.hasPermission(2))
                // "/sc" без аргументов — справка
                .executes(ctx -> help(ctx.getSource(), root))
                .then(Commands.literal("help").executes(ctx -> help(ctx.getSource(), root)))
                .then(Commands.literal("reload")
                        .executes(ctx -> reloadAll(ctx.getSource()))
                        // Слова раньше аргумента: Brigadier отдаёт им точное совпадение, скрипт не ищется.
                        .then(Commands.literal("config")
                                .executes(ctx -> reloadConfig(ctx.getSource())))
                        .then(Commands.literal("resources")
                                .executes(ctx -> reloadResources(ctx.getSource())))
                        // greedyString — чтобы работали подпапки "sub/name"
                        .then(Commands.argument("script", StringArgumentType.greedyString())
                                .suggests(RELOAD_SCRIPTS)
                                .executes(ctx -> ScriptLoader.reloadOne(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "script")))))
                .then(Commands.literal("enable")
                        .then(Commands.argument("script", StringArgumentType.greedyString())
                                .suggests(DISABLED_SCRIPTS)
                                .executes(ctx -> ScriptLoader.enable(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "script")))))
                .then(Commands.literal("disable")
                        .then(Commands.argument("script", StringArgumentType.greedyString())
                                .suggests(ENABLED_SCRIPTS)
                                .executes(ctx -> ScriptLoader.disable(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "script")))))
                .then(Commands.literal("list").executes(ctx -> ScriptLoader.list(ctx.getSource())))
                .then(Commands.literal("ids").executes(ctx -> ids(ctx.getSource())))
                // Ванильная /give не знает id скриптов (в реестре только scriptcraft:dyn_*), поэтому своя.
                .then(Commands.literal("give")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .suggests(BOUND_IDS)
                                        .executes(ctx -> give(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                                ResourceLocationArgument.getId(ctx, "item"), 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64 * 36))
                                                .executes(ctx -> give(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                                        ResourceLocationArgument.getId(ctx, "item"),
                                                        IntegerArgumentType.getInteger(ctx, "count")))))));
    }

    /** /sc ids: какие технические id (F3, ванильные команды, датапаки) получили define из скриптов. */
    private static int ids(CommandSourceStack source) {
        Bindings.Snapshot b = Bindings.current();
        if (b.isEmpty()) {
            Feedback.ok(source, "ids.none");
            return 1;
        }
        Feedback.ok(source, "ids.header");
        List<String> all = b.allIds();
        for (String id : all) {
            String poolId = b.poolId(id);
            if (poolId == null) {
                Feedback.ok(source, "ids.real", id);
            } else {
                Feedback.ok(source, "ids.temporary", id, poolId);
            }
        }
        return all.size();
    }

    /** /sc give <игроки> <id> [количество] — как give в скриптах: example:ruby_block, ruby, diamond. */
    private static int give(CommandSourceStack source, Collection<ServerPlayer> targets, ResourceLocation id, int count) {
        // "ruby" Brigadier читает как minecraft:ruby — тогда ищем ещё и по короткому имени.
        Item item = ItemResolver.resolve(id.toString())
                .or(() -> "minecraft".equals(id.getNamespace()) ? ItemResolver.resolve(id.getPath()) : java.util.Optional.empty())
                .orElse(null);
        if (item == null) {
            Feedback.fail(source, "give.unknown", id.toString());
            return 0;
        }
        int maxStack = new ItemStack(item).getMaxStackSize();
        for (ServerPlayer p : targets) {
            int left = count;
            while (left > 0) {
                int size = Math.min(left, maxStack);
                ItemStack stack = new ItemStack(item, size);
                if (!p.getInventory().add(stack)) {
                    p.drop(stack, false);
                }
                left -= size;
            }
        }
        Component name = new ItemStack(item).getHoverName();
        Feedback.ok(source, "give.done", count, name.getString(), targets.size());
        return targets.size();
    }

    /** /sc reload: конфиг, ресурсы и все скрипты. */
    private static int reloadAll(CommandSourceStack source) {
        reloadConfig(source);
        reloadResources(source);
        return ScriptLoader.reloadAll(source);
    }

    /** Перечитать scriptcraft/config.toml. */
    private static int reloadConfig(CommandSourceStack source) {
        String error = ScriptCraftConfig.reloadFromDisk();
        if (error != null) {
            Feedback.fail(source, "config.reload_failed", error);
            return 0;
        }
        Feedback.ok(source, "config.reloaded", ScriptCraftConfig.language());
        return 1;
    }

    /** /sc reload resources: перечитать scriptcraft/resources/assets и разослать изменения игрокам онлайн. */
    private static int reloadResources(CommandSourceStack source) {
        String error = ResourceSync.reloadAndPush(source.getServer());
        if (error != null) {
            Feedback.fail(source, "resources.reload_failed", error);
            return 0;
        }
        int files = ResourceIndex.entries().size();
        long kb = ResourceIndex.totalBytes() / 1024;
        if (ScriptCraftConfig.syncResourcesToClients()) {
            int players = source.getServer().getPlayerList().getPlayerCount();
            Feedback.ok(source, "resources.reloaded", files, kb, players);
        } else {
            Feedback.ok(source, "resources.reloaded_nosync", files, kb);
        }
        // data/ (лут, теги, рецепты) перечитывает ванильная /reload — подсказываем.
        Feedback.ok(source, "resources.data_hint");
        return 1;
    }

    private static int help(CommandSourceStack source, String root) {
        for (String line : Messages.helpLines(root)) {
            Feedback.showColored(source, line);
        }
        return 1;
    }

    /** Регистрирует команды из текущих скриптов, которых ещё нет в дереве. */
    public static void registerScriptCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String name : ScriptLoader.getProgram().commandNames()) {
            if (REGISTERED.contains(name)) {
                continue; // уже есть: обработчик сам возьмёт свежие действия
            }
            if (dispatcher.getRoot().getChild(name) != null) {
                ScriptCraft.LOGGER.warn("[ScriptCraft] Команда /{} уже существует (ваниль или другой мод) — пропущена", name);
                continue;
            }
            CommandDefinition def = ScriptLoader.getProgram().command(name);
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
                    .requires(src -> ScriptLoader.commandAllowed(name, src));
            if (def == null || def.arguments().isEmpty()) {
                root.executes(ctx -> ScriptLoader.runCommand(name, ctx.getSource(), "", List.of(), List.of()));
                root.then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> ScriptLoader.runCommand(name, ctx.getSource(),
                                StringArgumentType.getString(ctx, "args"), List.of(), List.of())));
            } else {
                attachArguments(root, name, def.arguments(), 0);
            }
            dispatcher.register(root);
            REGISTERED.add(name);
        }
    }

    /** Слово (take) — узел literal. Тип — аргумент. Необязательный тип можно пропустить. */
    private static void attachArguments(ArgumentBuilder<CommandSourceStack, ?> parent, String name,
                                        List<ArgSpec> specs, int index) {
        if (index >= specs.size()) {
            parent.executes(ctx -> invoke(name, ctx, specs));
            return;
        }
        ArgSpec spec = specs.get(index);
        if (spec.isLiteral()) {
            var next = Commands.literal(spec.literal());
            attachArguments(next, name, specs, index + 1);
            parent.then(next);
            return;
        }
        if (spec.optional()) {
            parent.executes(ctx -> invoke(name, ctx, specs));
        }
        boolean lastTyped = true;
        for (int i = index + 1; i < specs.size(); i++) {
            if (!specs.get(i).isLiteral()) {
                lastTyped = false;
                break;
            }
        }
        var next = Commands.argument("a" + index, argumentType(spec.type(), lastTyped));
        attachArguments(next, name, specs, index + 1);
        parent.then(next);
    }

    private static int invoke(String name, CommandContext<CommandSourceStack> ctx, List<ArgSpec> specs) {
        List<ArgType> types = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            if (specs.get(i).isLiteral()) {
                continue;
            }
            types.add(specs.get(i).type());
            values.add(readArgument(ctx, specs.get(i).type(), "a" + i));
        }
        return ScriptLoader.runCommand(name, ctx.getSource(), null, types, values);
    }

    private static com.mojang.brigadier.arguments.ArgumentType<?> argumentType(ArgType type, boolean last) {
        return switch (type) {
            case TEXT -> last ? StringArgumentType.greedyString() : StringArgumentType.string();
            case DATE -> StringArgumentType.string();
            case PLAYER -> EntityArgument.player();
            case OFFLINE -> GameProfileArgument.gameProfile();
            case INTEGER -> IntegerArgumentType.integer();
            case NUMBER -> DoubleArgumentType.doubleArg();
            case BOOLEAN -> BoolArgumentType.bool();
            case WORLD -> DimensionArgument.dimension();
            case ITEM -> ItemArgument.item(BUILD);
        };
    }

    @Nullable
    private static Object readArgument(CommandContext<CommandSourceStack> ctx, ArgType type, String id) {
        try {
            ctx.getArgument(id, Object.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
        try {
            return readPresent(ctx, type, id);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return null;
        }
    }

    private static Object readPresent(CommandContext<CommandSourceStack> ctx, ArgType type, String id)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return switch (type) {
            case TEXT -> StringArgumentType.getString(ctx, id);
            case DATE -> dateText(StringArgumentType.getString(ctx, id));
            case PLAYER -> EntityArgument.getPlayer(ctx, id).getGameProfile().getName();
            case OFFLINE -> {
                Collection<com.mojang.authlib.GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, id);
                yield profiles.isEmpty() ? "" : profiles.iterator().next().getName();
            }
            case INTEGER -> (double) IntegerArgumentType.getInteger(ctx, id);
            case NUMBER -> DoubleArgumentType.getDouble(ctx, id);
            case BOOLEAN -> BoolArgumentType.getBool(ctx, id);
            case WORLD -> DimensionArgument.getDimension(ctx, id).dimension().location().toString();
            case ITEM -> {
                ItemInput input = ItemArgument.getItem(ctx, id);
                var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(input.getItem());
                yield key == null ? "minecraft:air" : key.toString();
            }
        };
    }

    /** today, tomorrow, yesterday и ГГГГ-ММ-ДД. Незнакомое слово остаётся как есть. */
    private static String dateText(String raw) {
        String s = raw.strip().toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now();
        LocalDate date = switch (s) {
            case "today", "now" -> today;
            case "tomorrow" -> today.plusDays(1);
            case "yesterday" -> today.minusDays(1);
            default -> {
                try {
                    yield LocalDate.parse(s);
                } catch (RuntimeException e) {
                    yield null;
                }
            }
        };
        return date == null ? raw : date.toString();
    }
}
