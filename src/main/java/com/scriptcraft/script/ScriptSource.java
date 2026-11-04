package com.scriptcraft.script;

import java.util.List;

/**
 * Исходный текст одного файла скрипта.
 *
 * @param fileName имя файла (для сообщений об ошибках)
 * @param lines    строки файла
 */
public record ScriptSource(String fileName, List<String> lines) {
}
