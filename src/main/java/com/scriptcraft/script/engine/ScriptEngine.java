package com.scriptcraft.script.engine;

import com.scriptcraft.script.ScriptSource;

import java.util.List;

/**
 * Движок скриптов. Сейчас есть одна реализация — {@link SimpleScriptEngine}
 * (собственный построчный Skript-подобный язык).
 *
 * Интерфейс оставлен специально: позже можно подключить, например, Rhino (JavaScript) или LuaJ,
 * не меняя остальной мод — загрузчик работает только с {@link ScriptProgram}.
 */
public interface ScriptEngine {
    /** Короткое имя движка для логов. */
    String id();

    /**
     * Компилирует (разбирает) все исходники в готовую программу.
     * Ошибки разбора не бросаются, а добавляются в {@code errors} в формате "файл:строка: текст".
     * Строки с ошибками пропускаются, остальное продолжает работать.
     */
    ScriptProgram compile(List<ScriptSource> sources, List<String> errors);
}
