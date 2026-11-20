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
        return "ru".equals(Messages.language()) ? ru : en;
    }

    public static String localize(String message) {
        return translate(message, "ru".equals(Messages.language()));
    }

    /** Переводит текст ошибки (с сервера или парсера) на текущий язык клиента. */
    public static String forClient(String message) {
        return translate(message, "ru".equals(Messages.language()));
    }

    private static String translate(String message, boolean toRu) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        if (!toRu) {
            String exact = exactRuToEn(message);
            if (exact != null) {
                return exact;
            }
            String prefix = prefix(message, false);
            return prefix == null ? message : prefix;
        } else {
            String exact = exactEnToRu(message);
            if (exact != null) {
                return exact;
            }
            String prefix = prefix(message, true);
            return prefix == null ? message : prefix;
        }
    }

    private static String exactRuToEn(String message) {
        if ("Превышен лимит времени выполнения".equals(message)) {
            return "Execution time limit exceeded";
        }
        if ("Это действие требует игрока (команда вызвана не игроком?)".equals(message)) {
            return "This action requires a player (was the command run from console?)";
        }
        return exact(message);
    }

    private static String exactEnToRu(String message) {
        return switch (message) {
            case "Execution time limit exceeded" -> "Превышен лимит времени выполнения";
            case "This action requires a player (was the command run from console?)" -> "Это действие требует игрока (команда вызвана не игроком?)";
            case "empty effect command" -> "пустая эффект-команда";
            case "blocks (if/open screen) are not supported in effect commands" -> "блоки (if/open screen) в эффект-командах не поддерживаются";
            case "unexpected indent" -> "неожиданный отступ";
            case "expected a block header with a colon, for example 'on join:'" -> "ожидается заголовок блока с двоеточием, например 'on join:'";
            case "'every' has no nested actions" -> "у 'every' нет вложенных действий";
            case "expected 'key: value'" -> "ожидается 'ключ: значение'";
            case "permission: a number from 0 to 4" -> "permission: число от 0 до 4";
            case "executable by: players, console or players and console" -> "executable by: players, console или players and console";
            case "aliases: a short name, several separated by commas" -> "aliases: короткое имя, несколько через запятую";
            case "arguments: types separated by commas, optional ones in [brackets]" -> "arguments: типы через запятую, необязательные в [скобках]";
            case "a required argument cannot follow an optional one" -> "обязательный аргумент не может стоять после необязательного";
            case "bad indent" -> "неверный отступ";
            case "need: chance of 50%:" -> "нужно: chance of 50%:";
            case "extra text in 'chance of'" -> "лишний текст в 'chance of'";
            case "'loop' has no nested actions" -> "у 'loop' нет вложенных действий";
            case "'else if' without an 'if' directly above" -> "'else if' без 'if' прямо над ним";
            case "'else if' after 'else'" -> "'else if' после 'else'";
            case "'else' without an 'if' directly above" -> "'else' без 'if' прямо над ним";
            case "this if already has an else" -> "у этого if уже есть else";
            case "'wait' has no nested lines — write the next actions at the same indent" -> "у 'wait' не бывает вложенных строк — следующие действия пишите на том же отступе";
            case "'wait' has no colon: the lines below at the same indent run after the pause" -> "'wait' пишется без двоеточия: строки ниже на том же отступе выполнятся после паузы";
            case "empty action" -> "пустое действие";
            case "a colon is only allowed on 'open screen', 'create hud', 'loop', 'if' and 'else'" -> "двоеточие допустимо только у 'open screen', 'create hud', 'loop', 'if' и 'else'";
            case "this action cannot have nested lines" -> "у этого действия не может быть вложенных строк";
            case "need: set {name} to <value> (quote text that contains spaces)" -> "нужно: set {имя} to <значение> (текст с пробелами — в кавычках)";
            case "need: every 5 seconds" -> "нужно: every 5 seconds";
            case "need: every <number> seconds|minutes|ticks" -> "нужно: every <число> seconds|minutes|ticks";
            case "the 'every' period is a number, for example every 5 seconds" -> "период 'every' задаётся числом, например every 5 seconds";
            case "time unit: ticks, seconds or minutes" -> "единица времени: ticks, seconds или minutes";
            case "the period must be at least 1 tick" -> "период должен быть хотя бы 1 тик";
            case "an 'every' period cannot be longer than an hour" -> "период 'every' не длиннее часа";
            case "need: wait <number> seconds|ticks|minutes" -> "нужно: wait <число> seconds|ticks|minutes";
            case "expected a variable in braces, for example {coins::%player%}" -> "ожидается переменная в фигурных скобках, например {coins::%player%}";
            case "incomplete condition, for example: if {coins::%player%} >= 10:" -> "неполное условие, пример: if {coins::%player%} >= 10:";
            case "need: player has permission <0-4>" -> "нужно: player has permission <0-4>";
            case "the permission level is a number from 0 to 4" -> "уровень прав — число от 0 до 4";
            case "need: player has <item>" -> "нужно: player has <предмет>";
            case "need: player is holding <item>" -> "нужно: player is holding <предмет>";
            case "need: player is in world overworld" -> "нужно: player is in world overworld";
            case "expected creative, survival, adventure, spectator or world <world>" -> "ожидается creative, survival, adventure, spectator или world <мир>";
            case "need: every <number> seconds|minutes|ticks or every second|minute|tick" -> "нужно: every <число> seconds|minutes|ticks  или  every second|minute|tick";
            case "'namespace' has no nested lines: write define below it without extra indent" -> "у 'namespace' не бывает вложенных строк: пишите define без отступа ниже";
            default -> null;
        };
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

    private static String prefix(String message, boolean toRu) {
        String[][] rules = {
                {"неизвестный тип аргумента '", "unknown argument type '"},
                {"после имени команды ожидаются аргументы <тип> или [тип], а не '", "after the command name write <type> or [type], not '"},
                {"имя /", "the name /"},
                {"команда /", "command /"},
                {"псевдоним '", "alias '"},
                {"псевдоним /", "alias /"},
                {"неизвестное событие: ", "unknown event: "},
                {"неизвестное действие: ", "unknown action: "},
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
                {"неверное число в свойствах: ", "bad number in properties: "},
                {"Превышен лимит действий (", "Action limit exceeded ("}
        };
        int srcIdx = toRu ? 1 : 0;
        int dstIdx = toRu ? 0 : 1;
        for (String[] rule : rules) {
            if (message.startsWith(rule[srcIdx])) {
                String rest = message.substring(rule[srcIdx].length());
                if (!toRu) {
                    rest = rest.replace("уже объявлена в другом месте", "is already declared elsewhere")
                            .replace("зарезервировано модом (scriptcraft, sc, script)", "is reserved by the mod (scriptcraft, sc, script)")
                            .replace("уже занят", "is already used")
                            .replace(" — только a-z, 0-9 и _", " — only a-z, 0-9 and _")
                            .replace("указано дважды", "is listed twice");
                } else {
                    rest = rest.replace("is already declared elsewhere", "уже объявлена в другом месте")
                            .replace("is reserved by the mod (scriptcraft, sc, script)", "зарезервировано модом (scriptcraft, sc, script)")
                            .replace("is already used", "уже занят")
                            .replace(" — only a-z, 0-9 and _", " — только a-z, 0-9 и _")
                            .replace("is listed twice", "указано дважды");
                }
                return rule[dstIdx] + rest;
            }
        }
        return null;
    }
}
