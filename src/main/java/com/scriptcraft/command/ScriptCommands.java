package com.scriptcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.scriptcraft.ScriptCraft;
import com.scriptcraft.lang.Messages;
import com.scriptcraft.script.ScriptLoader;
import com.scriptcraft.script.ScriptPaths;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.List;
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

    /** Подсказки: включённые скрипты (для reload и disable). */
    private static final SuggestionProvider<CommandSourceStack> ENABLED_SCRIPTS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(suggestions(name -> !ScriptPaths.isDisabled(name)), builder);
    /** Подсказки: выключенные скрипты (для enable), показываются без минуса. */
    private static final SuggestionProvider<CommandSourceStack> DISABLED_SCRIPTS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(suggestions(ScriptPaths::isDisabled), builder);

    private ScriptCommands() {
    }

    /** Имена скриптов без ".sk" и без ведущего "-" (как их удобно набирать). */
    private static Stream<String> suggestions(Predicate<String> filter) {
        return ScriptPaths.listScripts().stream()
                .filter(filter)
                .map(ScriptPaths::enabledName)
                .map(n -> n.endsWith(".sk") ? n.substring(0, n.length() - 3) : n);
    }

    /** Вызывается из RegisterCommandsEvent — каждый раз с НОВЫМ диспетчером. */
    public static void registerAll(CommandDispatcher<CommandSourceStack> dispatcher) {
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

    /** Дерево одной корневой команды: help, reload [скрипт], enable, disable, list. */
    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot(String root) {
        return Commands.literal(root)
                .requires(src -> src.hasPermission(2))
                // "/sc" без аргументов — справка
                .executes(ctx -> help(ctx.getSource(), root))
                .then(Commands.literal("help").executes(ctx -> help(ctx.getSource(), root)))
                .then(Commands.literal("reload")
                        .executes(ctx -> ScriptLoader.reloadAll(ctx.getSource()))
                        // greedyString — чтобы работали подпапки "sub/name"
                        .then(Commands.argument("script", StringArgumentType.greedyString())
                                .suggests(ENABLED_SCRIPTS)
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
                .then(Commands.literal("list").executes(ctx -> ScriptLoader.list(ctx.getSource())));
    }

    private static int help(CommandSourceStack source, String root) {
        for (String line : Messages.helpLines(root)) {
            source.sendSuccess(() -> Component.literal(line), false);
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
            dispatcher.register(Commands.literal(name)
                    // Права читаются при каждой проверке, поэтому смена "permission N" работает после reload.
                    .requires(src -> {
                        int level = ScriptLoader.getProgram().commandPermission(name);
                        return level <= 0 || src.hasPermission(level);
                    })
                    .executes(ctx -> ScriptLoader.runCommand(name, ctx.getSource(), ""))
                    // Всё после имени команды доступно в скрипте как %args%.
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                            .executes(ctx -> ScriptLoader.runCommand(name, ctx.getSource(),
                                    StringArgumentType.getString(ctx, "args")))));
            REGISTERED.add(name);
        }
    }
}
