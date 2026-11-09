package com.scriptcraft.script.parser;

import com.scriptcraft.script.runtime.Action;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Обработчик события из скрипта.
 *
 * @param file       файл скрипта, из которого он взят (для on load/unload только своего скрипта и для
 *                   уборки HUD при /sc disable)
 * @param actions    тело обработчика
 * @param eachPlayer для load/unload: выполнить отдельно для КАЖДОГО игрока онлайн (с ним в роли player).
 *                   Так работает "on join or load:" — то, что игрок получает при входе, сразу получают
 *                   и те, кто уже на сервере, когда скрипт включили или перезагрузили.
 * @param filter     для on key и on open — имя клавиши или меню; у остальных событий null
 */
public record EventHandler(String file, List<Action> actions, boolean eachPlayer, @Nullable String filter) {
}
