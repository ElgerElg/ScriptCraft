package com.scriptcraft.lang;

import com.scriptcraft.config.ScriptCraftConfig;

/**
 * Текст ошибки на языке конфига. Журнал и уведомления получают уже выбранную строку.
 * Точные фразы и начала фраз покрывают сообщения разбора, которые раньше были только по-русски.
 */
public final class Phrase {
    private Phrase() {
    }

    public static String pick(String en, String ru) {
        return "ru".equals(ScriptCraftConfig.language()) ? ru : en;
    }

    public static String localize(String message) {
        if (message == null || message.isEmpty() || "ru".equals(ScriptCraftConfig.language())) {
            return message;
        }
        String exact = exact(message);
        if (exact != null) {
            return exact;
        }
        String prefix = prefix(message);
        return prefix == null ? message : prefix;
    }

    private static String exact(String message) {
        return switch (message) {
            case "пустая эффект-команда" -> "empty effect command";
            case "блоки (if/open screen) в эффект-командах не поддерживаются" -> "blocks (if/open screen) are not supported in effect commands";
            case "неожиданный отступ" -> "unexpected indent";
            case "ожидается заголовок блока с двоеточием, например 'on join:'" -> "expected a block header with a colon, for example 'on join:'";
            case "у 'every' нет вложенных действий" -> "'every' has no nested actions";
            case "ожидается 'ключ: значение'" -> "expected 'key: value'";
            case "permission: число от 0 до 4" -> "permission: a number from 0 to 4";
            case "executable by: players, console или players and console" -> "executable by: players, console or players and console";
            case "aliases: короткое имя, несколько через запятую" -> "aliases: a short name, several separated by commas";
            case "arguments: типы через запятую, необязательные в [скобках]" -> "arguments: types separated by commas, optional ones in [brackets]";
            case "обязательный аргумент не может стоять после необязательного" -> "a required argument cannot follow an optional one";
            case "неверный отступ" -> "bad indent";
            case "нужно: chance of 50%:" -> "need: chance of 50%:";
            case "лишний текст в 'chance of'" -> "extra text in 'chance of'";
            case "у 'loop' нет вложенных действий" -> "'loop' has no nested actions";
            case "'else if' без 'if' прямо над ним" -> "'else if' without an 'if' directly above";
            case "'else if' после 'else'" -> "'else if' after 'else'";
            case "'else' без 'if' прямо над ним" -> "'else' without an 'if' directly above";
            case "у этого if уже есть else" -> "this if already has an else";
            case "у 'wait' не бывает вложенных строк — следующие действия пишите на том же отступе" -> "'wait' has no nested lines — write the next actions at the same indent";
            case "'wait' пишется без двоеточия: строки ниже на том же отступе выполнятся после паузы" -> "'wait' has no colon: the lines below at the same indent run after the pause";
            case "пустое действие" -> "empty action";
            case "двоеточие допустимо только у 'open screen', 'create hud', 'loop', 'if' и 'else'" -> "a colon is only allowed on 'open screen', 'create hud', 'loop', 'if' and 'else'";
            case "у этого действия не может быть вложенных строк" -> "this action cannot have nested lines";
            case "нужно: set {имя} to <значение> (текст с пробелами — в кавычках)" -> "need: set {name} to <value> (quote text that contains spaces)";
            case "нужно: every 5 seconds" -> "need: every 5 seconds";
            case "нужно: every <число> seconds|minutes|ticks" -> "need: every <number> seconds|minutes|ticks";
            case "период 'every' задаётся числом, например every 5 seconds" -> "the 'every' period is a number, for example every 5 seconds";
            case "единица времени: ticks, seconds или minutes" -> "time unit: ticks, seconds or minutes";
            case "период должен быть хотя бы 1 тик" -> "the period must be at least 1 tick";
            case "период 'every' не длиннее часа" -> "an 'every' period cannot be longer than an hour";
            case "нужно: wait <число> seconds|ticks|minutes" -> "need: wait <number> seconds|ticks|minutes";
            case "ожидается переменная в фигурных скобках, например {coins::%player%}" -> "expected a variable in braces, for example {coins::%player%}";
            case "неполное условие, пример: if {coins::%player%} >= 10:" -> "incomplete condition, for example: if {coins::%player%} >= 10:";
            case "нужно: player has permission <0-4>" -> "need: player has permission <0-4>";
            case "уровень прав — число от 0 до 4" -> "the permission level is a number from 0 to 4";
            case "нужно: player has <предмет>" -> "need: player has <item>";
            case "нужно: player is holding <предмет>" -> "need: player is holding <item>";
            case "нужно: player is in world overworld" -> "need: player is in world overworld";
            case "ожидается creative, survival, adventure, spectator или world <мир>" -> "expected creative, survival, adventure, spectator or world <world>";
            case "нужно: every <число> seconds|minutes|ticks  или  every second|minute|tick" -> "need: every <number> seconds|minutes|ticks or every second|minute|tick";
            case "у 'namespace' не бывает вложенных строк: пишите define без отступа ниже" -> "'namespace' has no nested lines: write define below it without extra indent";
            default -> null;
        };
    }

    private static String prefix(String message) {
        String[][] rules = {
                {"неизвестный тип аргумента '", "unknown argument type '"},
                {"после имени команды ожидаются аргументы <тип> или [тип], а не '", "after the command name write <type> or [type], not '"},
                {"имя /", "the name /"},
                {"команда /", "command /"},
                {"псевдоним '", "alias '"},
                {"псевдоним /", "alias /"},
                {"неизвестное событие: ", "unknown event: "},
                {"событие '", "event '"},
                {"id '", "id '"},
                {"неизвестный блок: ", "unknown block: "},
                {"лишний текст в условии: ", "extra text in the condition: "},
                {"неизвестный оператор условия: ", "unknown condition operator: "},
                {"нужно: ", "need: "},
                {"ожидается: ", "expected: "},
                {"ожидается ", "expected "},
                {"не указан", "missing"},
                {"неподдерживаемый селектор '", "unsupported selector '"},
                {"радиус должен быть числом", "the radius must be a number"},
                {"лишние слова после цели: ", "extra words after the target: "},
                {"не указана цель", "target is missing"},
                {"у list нет ", "list has no "},
                {"у 'loop' нет ", "'loop' has no "},
                {"facing: ", "facing: "},
                {"render: ", "render: "},
                {"full: ", "full: "},
                {"неверное число в свойствах: ", "bad number in properties: "}
        };
        for (String[] rule : rules) {
            if (message.startsWith(rule[0])) {
                String rest = message.substring(rule[0].length());
                rest = rest.replace("уже объявлена в другом месте", "is already declared elsewhere")
                        .replace("зарезервировано модом (scriptcraft, sc, script)", "is reserved by the mod (scriptcraft, sc, script)")
                        .replace("уже занят", "is already used")
                        .replace(" — только a-z, 0-9 и _", " — only a-z, 0-9 and _")
                        .replace("указано дважды", "is listed twice");
                return rule[1] + rest;
            }
        }
        return null;
    }
}
