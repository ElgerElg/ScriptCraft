package com.scriptcraft.script.parser;

import com.scriptcraft.script.runtime.Action;

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
 */
public record EventHandler(String file, List<Action> actions, boolean eachPlayer) {
}
