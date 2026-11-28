package com.scriptcraft.script.runtime;

import com.scriptcraft.ScriptCraft;
import com.scriptcraft.script.ScriptLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Отложенные продолжения "wait N seconds|ticks|minutes": остаток блока (включая if, loop, while, function)
 * выполняется на главном потоке сервера через N тиков. При /sc reload задачи этого скрипта снимаются.
 * Дольше 5 минут пауза не ставится — остаток обрезается до 5 минут.
 */
public final class ScriptScheduler {
    private static final int MAX_DELAY = 20 * 300;
    private static final List<Task> TASKS = new ArrayList<>();

    private record Task(int runAt, @Nullable String script, @Nullable UUID player, @Nullable UUID loopPlayer,
                        @Nullable CommandSourceStack source, List<ArgType> argTypes, List<Object> args,
                        Map<String, String> placeholders, List<Map<String, Object>> frames, List<Action> actions) {
    }

    private ScriptScheduler() {
    }

    public static void schedule(ExecutionContext ctx, int ticks, List<Action> actions) {
        if (actions.isEmpty()) {
            return;
        }
        int delay = Math.max(1, Math.min(MAX_DELAY, ticks));
        UUID id = ctx.player() == null ? null : ctx.player().getUUID();
        UUID loopId = ctx.loopPlayer() == null ? null : ctx.loopPlayer().getUUID();
        TASKS.add(new Task(ctx.server().getTickCount() + delay, ctx.script(), id, loopId,
                ctx.source(), ctx.snapshotCommandArgTypes(), ctx.snapshotCommandArgs(),
                ctx.snapshotPlaceholders(), ctx.snapshotFrames(), actions));
    }

    /** script == null — снять все задачи (полная перезагрузка и остановка сервера). */
    public static void clear(@Nullable String script) {
        if (script == null) {
            TASKS.clear();
        } else {
            TASKS.removeIf(t -> script.equals(t.script()));
        }
    }

    public static void tick(MinecraftServer server) {
        if (TASKS.isEmpty()) {
            return;
        }
        int now = server.getTickCount();
        List<Task> due = new ArrayList<>();
        TASKS.removeIf(t -> {
            if (t.runAt() <= now) {
                due.add(t);
                return true;
            }
            return false;
        });
        for (Task task : due) {
            ServerPlayer player = task.player() == null ? null : server.getPlayerList().getPlayer(task.player());
            if (task.player() != null && player == null) {
                continue; // игрок вышел, пока скрипт ждал
            }
            ServerPlayer loopPlayer = task.loopPlayer() == null ? null : server.getPlayerList().getPlayer(task.loopPlayer());
            CommandSourceStack src = player != null ? player.createCommandSourceStack() : task.source();
            ExecutionContext ctx = new ExecutionContext(server, player, src);
            ctx.setScript(task.script());
            ctx.setLoopPlayer(loopPlayer);
            ctx.restoreCommandArguments(task.argTypes(), task.args());
            ctx.restorePlaceholders(task.placeholders());
            ctx.restoreFrames(task.frames(), server);
            long prof = com.scriptcraft.script.ScriptProfiler.start();
            try {
                Actions.runAll(task.actions(), ctx);
            } catch (RuntimeException e) {
                String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                String errFile = ctx.script() != null && !ctx.script().isEmpty() ? ctx.script() : (task.script() == null ? "" : task.script());
                ScriptCraft.LOGGER.warn("[ScriptCraft] Ошибка после wait ({}): {}", errFile, msg);
                ScriptLoader.noteRuntime(errFile, ctx.line(), msg, player, server);
            } finally {
                com.scriptcraft.script.ScriptProfiler.end(prof, task.script(), "wait");
            }
        }
    }
}
