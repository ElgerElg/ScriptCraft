package com.scriptcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.scriptcraft.ScriptCraft;
import com.scriptcraft.script.ScriptLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Команды мода (/scriptcraft ...) и команды, объявленные в скриптах (command /hello:).
 *
 * Ограничения горячей перезагрузки команд (Brigadier не умеет удалять узлы):
 * - НОВЫЕ команды при /scriptcraft reload добавляются в дерево, и дерево заново отправляется игрокам;
 * - УДАЛЁННЫЕ команды остаются в дереве до перезапуска (или ванильного /reload), но при вызове пишут "удалена";
 * - изменённые действия и permission применяются сразу (обработчик и requires читают актуальную программу).
 */
public final class ScriptCommands {
    /** Имена, которые зарегистрировали мы (чтобы не путать со своими при повторной регистрации). */
    private static final Set<String> REGISTERED = new HashSet<>();

    private ScriptCommands() {
    }

    /** Вызывается из RegisterCommandsEvent — каждый раз с НОВЫМ диспетчером. */
    public static void registerAll(CommandDispatcher<CommandSourceStack> dispatcher) {
        REGISTERED.clear();
        dispatcher.register(Commands.literal("scriptcraft")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("reload").executes(ctx -> ScriptLoader.reload(ctx.getSource())))
                .then(Commands.literal("list").executes(ctx -> {
                    var program = ScriptLoader.getProgram();
                    ctx.getSource().sendSuccess(() -> Component.literal("[ScriptCraft] Команды: /"
                            + String.join(", /", program.commandNames())
                            + " | блоков: " + program.blocks().size()
                            + " | предметов: " + program.items().size()
                            + " | ошибок: " + ScriptLoader.lastErrors().size()), false);
                    return 1;
                })));
        registerScriptCommands(dispatcher);
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
