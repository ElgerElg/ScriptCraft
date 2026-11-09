package com.scriptcraft.script.runtime;

import com.scriptcraft.ScriptCraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Отложенные хвосты "wait N seconds": остаток блока выполняется на главном потоке сервера
 * через N тиков. При /sc reload задачи этого скрипта снимаются.
 * Дольше 5 минут пауза не ставится — остаток обрезается до 5 минут.
 */
public final class ScriptScheduler {
    private static final int MAX_DELAY = 20 * 300;
    private static final List<Task> TASKS = new ArrayList<>();

    private record Task(int runAt, @Nullable String script, @Nullable UUID player,
                        Map<String, String> placeholders, List<Action> actions) {
    }

    private ScriptScheduler() {
    }

    public static void schedule(ExecutionContext ctx, int ticks, List<Action> actions) {
        if (actions.isEmpty()) {
            return;
        }
        int delay = Math.max(1, Math.min(MAX_DELAY, ticks));
        UUID id = ctx.player() == null ? null : ctx.player().getUUID();
        TASKS.add(new Task(ctx.server().getTickCount() + delay, ctx.script(), id, ctx.snapshotPlaceholders(), actions));
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
            ExecutionContext ctx = new ExecutionContext(server, player, null);
            ctx.setScript(task.script());
            ctx.restorePlaceholders(task.placeholders());
            try {
                Actions.runAll(task.actions(), ctx);
            } catch (RuntimeException e) {
                ScriptCraft.LOGGER.warn("[ScriptCraft] Ошибка после wait ({}): {}", task.script(), e.getMessage());
            }
        }
    }
}
