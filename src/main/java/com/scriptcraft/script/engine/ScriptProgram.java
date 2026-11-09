package com.scriptcraft.script.engine;

import com.scriptcraft.script.parser.BlockDefinition;
import com.scriptcraft.script.parser.ItemDefinition;
import com.scriptcraft.script.runtime.ExecutionContext;
import com.scriptcraft.script.runtime.ScriptEvent;

import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Результат компиляции всех скриптов: обработчики событий, команды и объявления блоков/предметов. */
public interface ScriptProgram {
    /** Блоки, объявленные через "define block". Используются ТОЛЬКО при запуске (реестры потом заморожены). */
    List<BlockDefinition> blocks();

    /** Предметы, объявленные через "define item". Используются ТОЛЬКО при запуске. */
    List<ItemDefinition> items();

    /** Имена команд из скриптов (без косой черты). */
    Set<String> commandNames();

    /** Уровень прав для команды (0 — все игроки, 2 — операторы) или -1, если такой команды нет. */
    int commandPermission(String name);

    /** Выполняет все обработчики события игрока (join/quit) с этим контекстом. */
    void fireEvent(ScriptEvent event, ExecutionContext ctx);

    /**
     * События самого скрипта (load/unload) — только у файлов, прошедших {@code files}.
     * Обычный обработчик выполняется один раз без игрока, обработчик из "on join or load:" — для каждого
     * игрока онлайн. Ошибка одного обработчика пишется в лог и не мешает остальным.
     */
    void fireScriptEvent(ScriptEvent event, Predicate<String> files, MinecraftServer server);

    /** Выполняет команду. Возвращает false, если команды больше нет в скриптах. */
    boolean runCommand(String name, ExecutionContext ctx);

    /** Периодические блоки "every N seconds:" — раз в период, без игрока в контексте. */
    void tickEvery(MinecraftServer server);
}
