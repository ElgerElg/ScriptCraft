package com.scriptcraft.script.runtime;

import java.util.Locale;

/**
 * События, на которые могут подписываться скрипты ("on join:", "on death:", "on chat:").
 * Несколько событий у одного обработчика: "on join or death:" (см. ScriptParser).
 * Периодика "every 5 seconds:" — это не событие, а отдельный блок (PeriodHandler).
 */
public enum ScriptEvent {
    /** Игрок зашёл на сервер (в контексте — этот игрок). */
    JOIN("join", true),
    /** Игрок вышел (в контексте — этот игрок). */
    QUIT("quit", true),
    /** Первый заход этого игрока (ещё ни разу не выходил). Вместе с ним всё равно срабатывает join. */
    FIRST_JOIN("first join", true, "firstjoin"),
    /** Игрок умер. Можно отменить (cancel event). %attacker%, %cause%. */
    DEATH("death", true, "player death"),
    /** Игрок возродился. */
    RESPAWN("respawn", true, "player respawn"),
    /** Сообщение в чат. Можно отменить. %message%. */
    CHAT("chat", true, "player chat"),
    /** Сломал блок. Можно отменить. %block%, %x%, %y%, %z%. */
    BREAK("break", true, "block break", "mine", "break block"),
    /** Поставил блок. Можно отменить. %block%, %x%, %y%, %z%. */
    PLACE("place", true, "block place", "place block"),
    /** ПКМ по блоку (основная рука). Можно отменить. %block%, %x%, %y%, %z%. */
    RIGHT_CLICK("right click", true, "rightclick", "right click block"),
    /** ЛКМ по блоку (основная рука). Можно отменить. %block%, %x%, %y%, %z%. */
    LEFT_CLICK("left click", true, "leftclick", "left click block"),
    /** Выбросил предмет. Можно отменить. %item%. */
    DROP("drop", true, "drop item", "item drop"),
    /** Подобрал предмет с земли. Можно отменить до того, как он попадёт в инвентарь. %item%. */
    PICKUP("pickup", true, "pick up", "item pickup"),
    /**
     * В инвентаре стало больше этого предмета: подбор, give, сундук, крафт, торговля, креатив.
     * %item% — id, %amount% — на сколько выросло число. cancel event забирает эту добавку обратно.
     */
    RECEIVE("receive", true, "item receive", "receive item"),
    /** Нажата клавиша (on key e:). %key% — имя, %screen% — открытый экран или none. */
    KEY("key", true, "key press", "press key"),
    /** Отпущена клавиша (on key release e:). %key% — имя, %screen% — открытый экран или none. */
    KEY_RELEASE("key release", true, "release key"),
    /** Удерживается клавиша (on key hold e:). %key% — имя, %screen% — открытый экран или none. */
    KEY_HOLD("key hold", true, "hold key"),
    /** Открыт кастомный экран скрипта (on screen open:). %screen% — заголовок экрана. */
    SCREEN_OPEN("screen open", true, "open screen", "gui open", "open gui"),
    /** Закрыт кастомный экран скрипта (on screen close:). %screen% — заголовок экрана. */
    SCREEN_CLOSE("screen close", true, "close screen", "gui close", "close gui"),
    /** Изменилось значение input / slider / toggle на кастомном экране. %element%, %value%, %screen%. */
    GUI_CHANGE("gui change", true, "screen change", "gui input", "screen input", "input", "slider", "toggle"),
    /** Открывается ванильный экран (on open inventory:). %menu%. cancel event в теле блокирует экран на клиенте. */
    OPEN("open", true, "open menu", "menu open"),
    /** Получил урон. Можно отменить. %damage%, %attacker%, %cause%. */
    DAMAGE("damage", true, "player damage", "hurt"),
    /** Сменил мир. %world% — новый, %from% — старый. */
    WORLD_CHANGE("world change", true, "change world", "dimension change", "worldchange"),
    /** Сменил режим игры. Можно отменить. %gamemode% — новый, %from% — старый. */
    GAMEMODE_CHANGE("gamemode change", true, "game mode change", "gamemode"),
    /** Убил сущность или игрока. %victim%, %attacker%, %cause%. */
    KILL("kill", true, "entity death", "player kill"),
    /** Ударил сущность или игрока. Можно отменить. %victim%, %attacker%. */
    ATTACK("attack", true, "player attack", "hit"),
    /** Выстрелил из лука. Можно отменить. %projectile%. */
    SHOOT("shoot", true, "bow shoot"),
    /** Снаряд попал в блок или сущность. %projectile%, %victim%, %block%, %x%, %y%, %z%. */
    PROJECTILE_HIT("projectile hit", true, "projectile impact"),
    /** Съел еду или выпил зелье. %item%. */
    CONSUME("consume", true, "eat", "drink"),
    /** Скрафтил предмет. %item%, %amount%. */
    CRAFT("craft", true, "item craft"),
    /** Поймал рыбу/предмет удочкой. Можно отменить. %item%. */
    FISH("fish", true, "fishing"),
    /** Присел / встал (Shift). %sneaking%. */
    SNEAK("sneak", true, "toggle sneak"),
    /** Начал / перестал бежать. %sprinting%. */
    SPRINT("sprint", true, "toggle sprint"),
    /** Прыгнул. */
    JUMP("jump", true, "player jump"),
    /** Игрок сдвинулся с места. Можно отменить. %x%, %y%, %z%, %from_x%, %from_y%, %from_z%, %distance%. */
    MOVE("move", true, "player move"),
    /** Игрок повернул камеру (yaw/pitch). Можно отменить. %yaw%, %pitch%, %from_yaw%, %from_pitch%. */
    TURN_AROUND("turning around", true, "turn around", "turn", "look", "player turn"),
    /** Игрок сдвинулся или повернул камеру (один вызов за тик). Можно отменить. %moved%, %turned%. */
    MOVE_OR_TURN("move or turning around", true, "turning around or move", "move or turn around", "turn around or move", "move or turn", "turn or move"),
    /** Выполнил команду. Можно отменить. %command%, %args%. */
    COMMAND("command", true, "player command"),
    /** Сломался инструмент/броня. %item%. */
    ITEM_BREAK("item break", true, "tool break"),
    /** Порвался поводок (по дистанции break_at или при поломке опорного блока). %victim%, %holder%. */
    LEASH_BREAK("leash break", true, "leash snap", "unleash"),
    /** ПКМ по фейковому NPC (on npc click ["id"]:). %npc%, %npc_name%, %x%, %y%, %z%. */
    NPC_CLICK("npc click", true, "click npc", "right click npc", "npc interact"),
    /** ЛКМ / удар по фейковому NPC (on npc attack ["id"]:). %npc%, %npc_name%, %x%, %y%, %z%. */
    NPC_ATTACK("npc attack", true, "attack npc", "hit npc", "left click npc"),
    /**
     * Скрипт загружен: старт сервера, /sc reload, /sc reload &lt;скрипт&gt;, /sc enable &lt;скрипт&gt;.
     * Срабатывает только у ЭТОГО скрипта (перезагрузка другого файла его не вызывает). Игрока в контексте нет.
     */
    LOAD("load", false),
    /** Скрипт выгружается: /sc disable, перед /sc reload, остановка сервера. Игрока в контексте нет. */
    UNLOAD("unload", false);

    private final String scriptName;
    private final boolean playerEvent;
    private final String[] aliases;

    ScriptEvent(String scriptName, boolean playerEvent, String... aliases) {
        this.scriptName = scriptName;
        this.playerEvent = playerEvent;
        this.aliases = aliases;
    }

    public String scriptName() {
        return scriptName;
    }

    /** true — событие про конкретного игрока, false — про сам скрипт (load/unload). */
    public boolean playerEvent() {
        return playerEvent;
    }

    /** Ищет событие по имени из скрипта, например "join", "right click" или "script load". */
    public static ScriptEvent byName(String name) {
        String n = name.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        // Как в Skript: "on script load" / "on script unload" — то же, что "on load" / "on unload".
        if (n.startsWith("script ")) {
            n = n.substring(7);
        }
        for (ScriptEvent e : values()) {
            if (e.scriptName.equals(n)) {
                return e;
            }
            for (String alias : e.aliases) {
                if (alias.equals(n)) {
                    return e;
                }
            }
        }
        return null;
    }
}
