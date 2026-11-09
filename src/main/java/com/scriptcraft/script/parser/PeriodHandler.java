package com.scriptcraft.script.parser;

import com.scriptcraft.script.runtime.Action;

import java.util.List;

/**
 * Периодический блок "every 5 seconds:" / "every minute:" / "every tick:".
 *
 * @param file         файл скрипта
 * @param periodTicks  период в тиках сервера (20 тиков = 1 секунда), минимум 1
 * @param actions      тело без игрока в контексте (используйте all players / broadcast)
 */
public record PeriodHandler(String file, int periodTicks, List<Action> actions) {
}
