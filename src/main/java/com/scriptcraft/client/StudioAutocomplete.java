package com.scriptcraft.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Умное контекстное автодополнение для .sc скриптов в редакторе Студии.
 * Поддерживает:
 * - все события (включая поиск по смыслу и алиасам, например "on player" -> все события игрока),
 * - все эффекты, условия и выражения (включая поводки leash, анимации, камеру, GUI, NBT, LuckPerms),
 * - контекст внутри open screen / update screen / create hud / define item / define block,
 * - живые переменные {...} и пользовательские функции Name(...) из текущего открытого скрипта,
 * - умную постановку курсора ($0) внутрь кавычек/скобок/аргументов и ненавязчивый режим (не мешает Enter/комментариям/тексту).
 */
@OnlyIn(Dist.CLIENT)
public final class StudioAutocomplete {
    public static final int COL_EVENT = 0xFFF59E0B;
    public static final int COL_EFFECT = 0xFF38BDF8;
    public static final int COL_EXPR = 0xFFC084FC;
    public static final int COL_COND = 0xFF4ADE80;
    public static final int COL_FLOW = 0xFF34D399;
    public static final int COL_GUI = 0xFFF472B6;
    public static final int COL_DEF = 0xFFFB923C;
    public static final int COL_VAR = 0xFFFACC15;
    public static final int COL_FUNC = 0xFF60A5FA;

    private static final Pattern VAR_SCAN = Pattern.compile("\\{([^{}\\s]{1,64})}");
    private static final Pattern FUNC_SCAN = Pattern.compile("^\\s*function\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^)]*)\\)\\s*:");

    public record Item(String label, String insertText, int caretOffset, String tag, int tagColor, String hint) {
        @Override
        public String toString() {
            return label;
        }
    }

    public record Match(int replaceFrom, List<Item> items) {
    }

    private record Spec(String template, String labelOverride, String tag, int tagColor, String hint, String searchTokens) {
        Item toItem() {
            int marker = template.indexOf("$0");
            String clean = marker >= 0 ? template.replace("$0", "") : template;
            int caret = marker >= 0 ? marker : clean.length();
            String lbl = labelOverride != null ? labelOverride : template.replace("$0", "");
            return new Item(lbl, clean, caret, tag, tagColor, hint);
        }
    }

    private enum BlockKind {
        NONE, CODE, SCREEN, LIST, HUD, DEFINE_ITEM, DEFINE_BLOCK, DEFINE_ENTITY
    }

    private static final List<Spec> EVENTS = new ArrayList<>();
    private static final List<Spec> TOP_STRUCTURES = new ArrayList<>();
    private static final List<Spec> FLOW = new ArrayList<>();
    private static final List<Spec> EFFECTS = new ArrayList<>();
    private static final List<Spec> CONDITIONS = new ArrayList<>();
    private static final List<Spec> EXPRESSIONS = new ArrayList<>();
    private static final List<Spec> PLACEHOLDERS = new ArrayList<>();
    private static final List<Spec> GUI_ELEMENTS = new ArrayList<>();
    private static final List<Spec> GUI_LIST_ROWS = new ArrayList<>();
    private static final List<Spec> GUI_MODIFIERS = new ArrayList<>();
    private static final List<Spec> DEFINE_PROPS = new ArrayList<>();

    static {
        // ==================== СОБЫТИЯ (on ...:) ====================
        ev("on join:", "%player%", "on player join login вход");
        ev("on first join:", "%player%", "on player first join firstjoin первый вход");
        ev("on quit:", "%player%", "on player quit leave disconnect выход");
        ev("on death:", "cancel, %attacker%, %cause%", "on player death die смерть");
        ev("on respawn:", "%player%", "on player respawn возрождение");
        ev("on chat:", "cancel, %message%", "on player chat message чат");
        ev("on command:", "cancel, %command%, %args%", "on player command cmd команда");
        ev("on damage:", "cancel, %damage%, %attacker%, %cause%", "on player damage hurt урон");
        ev("on attack:", "cancel, %victim%, %attacker%", "on player attack hit удар атака");
        ev("on kill:", "%victim%, %attacker%, %cause%", "on player kill entity death убийство");
        ev("on move:", "cancel, %x%, %y%, %z%, %distance%", "on player move walk движение");
        ev("on turning around:", "cancel, %yaw%, %pitch%", "on player turn around look поворот камеры");
        ev("on move or turning around:", "cancel, %moved%, %turned%", "on player move turn");
        ev("on jump:", "cancel, %player%", "on player jump прыжок");
        ev("on sneak:", "%sneaking%", "on player sneak crouch shift присед");
        ev("on sprint:", "%sprinting%", "on player sprint run бег");
        ev("on break:", "cancel, %block%, %x%, %y%, %z%", "on player block break mine сломал блок");
        ev("on place:", "cancel, %block%, %x%, %y%, %z%", "on player block place поставил блок");
        ev("on right click:", "cancel, %block%, clicked entity", "on player right click block entity пкм");
        ev("on left click:", "cancel, %block%", "on player left click block лкм");
        ev("on drop:", "cancel, %item%", "on player item drop выбросил");
        ev("on pickup:", "cancel, %item%", "on player item pickup pick up подобрал");
        ev("on receive:", "cancel, %item%, %amount%", "on player item receive инвентарь получил");
        ev("on consume:", "cancel, %item%", "on player item consume eat drink еда зелье");
        ev("on craft:", "%item%, %amount%", "on player item craft крафт");
        ev("on fish:", "cancel, %item%", "on player fish fishing рыбалка");
        ev("on item break:", "%item%", "on player item tool break поломка");
        ev("on shoot:", "cancel, %projectile%", "on player bow shoot выстрел");
        ev("on projectile hit:", "%projectile%, %victim%, %block%", "on projectile hit impact попадание снаряда");
        ev("on leash break:", "%victim%, %holder%", "on player entity leash break snap unleash обрыв поводка");
        ev("on npc click:", "%npc%, %npc_name%", "on npc click interact right click рп нпс клик");
        ev("on npc click \"$0\":", "on npc click \"<id>\":", "%npc%, %npc_name%", "on npc click id нпс");
        ev("on npc attack \"$0\":", "on npc attack \"<id>\":", "cancel, %npc%", "on npc attack hit удар нпс");
        ev("on world change:", "%world%, %from%", "on player world dimension change смена мира");
        ev("on gamemode change:", "cancel, %gamemode%, %from%", "on player gamemode game mode change режим");
        ev("on key $0:", "on key <клавиша>:", "%key%, %screen%", "on player key press клавиша");
        ev("on key $0 in screen \"\":", "on key <клавиша> in screen \"...\":", "горячая клавиша GUI", "on player key in screen gui");
        ev("on key release $0:", "on key release <клавиша>:", "%key%", "on player key release отпустил");
        ev("on key hold $0:", "on key hold <клавиша>:", "%key%", "on player key hold удержание");
        ev("on open $0:", "on open <inventory|chest|...>:", "cancel, %menu%", "on player open menu inventory");
        ev("on screen open:", "%screen%", "on player screen gui open открытие экрана");
        ev("on screen close:", "%screen%", "on player screen gui close закрытие экрана");
        ev("on screen close \"$0\":", "on screen close \"...\":", "%screen%", "on player screen gui close");
        ev("on gui change:", "%element%, %value%, %screen%", "on player gui screen input slider toggle change");
        ev("on input \"$0\":", "on input \"<id>\":", "%value%", "on gui screen input текстовое поле");
        ev("on slider \"$0\":", "on slider \"<id>\":", "%value%", "on gui screen slider ползунок");
        ev("on toggle \"$0\":", "on toggle \"<id>\":", "%value%", "on gui screen toggle переключатель");
        ev("on load:", "старт скрипта", "on script load загрузка");
        ev("on unload:", "выгрузка скрипта", "on script unload выгрузка");

        // ==================== СТРУКТУРЫ ВЕРХНЕГО УРОВНЯ ====================
        TOP_STRUCTURES.add(new Spec("command /$0 [text]:", "command /<имя> [аргументы]:", "CMD", COL_FLOW, "команда чата", "command cmd команда"));
        TOP_STRUCTURES.add(new Spec("every $0 seconds:", "every <N> seconds:", "TMR", COL_FLOW, "таймер (сек)", "every seconds timer периодически"));
        TOP_STRUCTURES.add(new Spec("every $0 ticks:", "every <N> ticks:", "TMR", COL_FLOW, "таймер (тики)", "every ticks timer тики"));
        TOP_STRUCTURES.add(new Spec("function $0(p: player):", "function Name(p: player):", "FUN", COL_FUNC, "объявление функции", "function def функция"));
        TOP_STRUCTURES.add(new Spec("define item $0:", "define item <ns:id>:", "DEF", COL_DEF, "кастомный предмет / 3D оружие", "define item custom geo предмет оружие"));
        TOP_STRUCTURES.add(new Spec("define armor $0:", "define armor <ns:id>:", "DEF", COL_DEF, "3D Geo-броня / одежда", "define armor helmet chestplate leggings boots броня одежда"));
        TOP_STRUCTURES.add(new Spec("define block $0:", "define block <ns:id>:", "DEF", COL_DEF, "кастомный / 3D блок", "define block custom geo блок мебель"));
        TOP_STRUCTURES.add(new Spec("define entity $0:", "define entity <ns:id>:", "DEF", COL_DEF, "3D моб / босс / турель / декор", "define entity mob boss turret geo моб босс турель"));

        // ==================== УПРАВЛЕНИЕ ПОТОКОМ ====================
        flow("if $0:", "if <условие>:", "ветвление", "if условие");
        flow("else if $0:", "else if <условие>:", "иначе если", "else if elseif");
        flow("else:", "else:", "иначе", "else иначе");
        flow("while $0:", "while <условие>:", "цикл по условию", "while цикл");
        flow("loop all players:", "loop all players:", "%loop-player%", "loop all players цикл игроки");
        flow("loop $0 times:", "loop <N> times:", "%loop-number%", "loop times цикл раз");
        flow("loop {$0::*}:", "loop {список::*}:", "%loop-index%, %loop-value%", "loop list список");
        flow("loop {$0::*} sorted descending:", "loop {список::*} sorted descending:", "топ по убыванию", "loop list sorted descending топ");
        flow("wait $0 seconds", "wait <N> seconds", "пауза (сек)", "wait delay sleep seconds");
        flow("wait $0 ticks", "wait <N> ticks", "пауза (тики)", "wait delay ticks");
        flow("chance of $0%:", "chance of <N>%:", "случайный шанс", "chance random шанс");
        flow("return $0", "return <значение>", "вернуть из функции", "return выход результат");
        flow("stop", "stop", "прервать выполнение", "stop break выход");
        flow("cancel event", "cancel event", "отменить событие", "cancel the event отмена");

        // ==================== ЭФФЕКТЫ (ВСЕ КАТЕГОРИИ МОДА + ПОВОДОК LEASH) ====================
        // Переменные и списки
        eff("set {$0} to ", "set {имя} to <значение>", "задать переменную", "set var variable переменная");
        eff("add $0 to {}", "add <знач> to {имя}", "прибавить / в список", "add variable list добавить");
        eff("remove $0 from {}", "remove <знач> from {имя}", "вычесть / из списка", "remove subtract variable list");
        eff("remove index $0 from {::*}", "remove index <ключ> from {список::*}", "удалить ключ списка", "remove index key from list");
        eff("delete {$0}", "delete {имя}", "удалить переменную", "delete clear variable");
        eff("clear {$0::*}", "clear {список::*}", "очистить список", "clear delete list");
        eff("sort {$0::*} descending", "sort {список::*} descending", "сортировать список", "sort list ascending descending");
        eff("reverse {$0::*}", "reverse {список::*}", "развернуть список", "reverse list");
        eff("replace \"$0\" with \"\" in {}", "replace \"...\" with \"...\" in {var}", "замена в переменной", "replace string text variable");
        eff("split $0 by \",\" into {::*}", "split <текст> by \",\" into {список::*}", "разбить строку в список", "split string into list");

        // Поводки (Leash — НОВАЯ МЕХАНИКА!)
        eff("leash $0 to player length 6", "leash <сущность/игрок> to player length 6", "привязать поводком к игроку", "leash tether tie rope player entity поводок привязать");
        eff("leash targeted entity to player length $0", "leash targeted entity to player length <N>", "моба в прицеле на поводок", "leash targeted entity mob player поводок");
        eff("leash targeted player to player length 5 color #94A3B8", "leash targeted player to player ...", "привязать игрока к игроку", "leash targeted player handcuffs наручники поводок");
        eff("leash player to targeted block length $0", "leash player to targeted block length <N>", "привязать к блоку в прицеле", "leash player entity to targeted block fence блок");
        eff("leash $0 to block at 0 64 0 length 6", "leash <цель> to block at <x> <y> <z>", "привязать к координатам блока", "leash to block at coords блок");
        eff("leash last spawned entity to player length $0", "leash last spawned entity to player ...", "привязать созданную сущность", "leash last spawned entity поводок");
        eff("leash targeted player to player length 6 two_way color #38BDF8", "leash ... two_way color #HEX", "двусторонний поводок (цепь)", "leash two_way bidirectional color width sag break");
        eff("set leash length of $0 to 8", "set leash length of <цель> to <N>", "изменить длину поводка", "set leash length max_length min_length поводок длина");
        eff("set leash strength of $0 to 1.5", "set leash strength of <цель> to <N>", "упругость/сила натяжения (0..5)", "set leash strength pull stiffness force натяжение");
        eff("set leash break of $0 to 14", "set leash break of <цель> to <N>", "дистанция разрыва (0 = не рвётся)", "set leash break break_at unbreakable разрыв");
        eff("set leash color of $0 to #38BDF8", "set leash color of <цель> to <#HEX>", "цвет верёвки поводка", "set leash color colour цвет");
        eff("set leash width of $0 to 1.5", "set leash width of <цель> to <N>", "толщина верёвки (0.2..6)", "set leash width thickness толщина");
        eff("set leash sag of $0 to 0.5", "set leash sag of <цель> to <N>", "провисание верёвки (0..4)", "set leash sag droop slack провисание");
        eff("set leash two_way of $0 to true", "set leash two_way of <цель> to true", "обоюдное натяжение", "set leash two_way bidirectional");
        eff("unleash $0", "unleash <сущность/игрок>", "отвязать поводок", "unleash untie release remove leash отвязать поводок");
        eff("clear all leashes", "clear all leashes", "снять все поводки на сервере", "clear all leashes поводок");

        // Сообщения, Тайтлы, Уведомления
        eff("send \"$0\" to player", "send \"...\" to player", "сообщение игроку", "send message chat player");
        eff("send \"$0\" to all players", "send \"...\" to all players", "сообщение всем", "send message all players");
        eff("broadcast \"$0\"", "broadcast \"...\"", "объявление в чат", "broadcast chat all");
        eff("reply \"$0\"", "reply \"...\"", "ответ вызвавшему", "reply command message");
        eff("send actionbar \"$0\" to player", "send actionbar \"...\" to player", "текст над хотбаром", "send actionbar action bar player");
        eff("send title \"$0\" with subtitle \"\" to player for 3 seconds", "send title \"...\" with subtitle \"...\"", "экранный заголовок", "send title subtitle fadein stay fadeout");
        eff("notify \"$0\" to player", "notify \"...\" to player", "тост-уведомление", "notify toast alert");
        eff("log \"$0\"", "log \"...\"", "запись в консоль/журнал", "log console debug");

        // Игрок: характеристики, эффекты, предметы
        eff("heal player", "heal player", "полное исцеление", "heal player health лечение");
        eff("heal player by $0", "heal player by <N>", "исцелить на N HP", "heal player by health");
        eff("damage player by $0", "damage player by <N>", "нанести урон", "damage hurt player by");
        eff("feed player", "feed player", "восстановить сытость", "feed hunger food player");
        eff("kill player", "kill player", "убить цель", "kill player смерть");
        eff("ignite player for $0 seconds", "ignite <цель> for <N> seconds", "поджечь цель", "ignite burn fire player");
        eff("extinguish player", "extinguish <цель>", "потушить огонь", "extinguish fire player");
        eff("give player $0 1", "give <цель> <предмет> [кол-во]", "выдать предмет", "give item player выдать");
        eff("take 1 $0 from player", "take <кол-во> <предмет> from <цель>", "забрать предмет", "take remove item inventory");
        eff("clear inventory of player", "clear inventory of <цель>", "очистить инвентарь", "clear inventory player");
        eff("set slot $0 of player to ", "set slot <0..40> of <цель> to <предмет>", "предмет в слот", "set slot inventory armor offhand");
        eff("set slot hotbar $0 of player to ", "set slot hotbar <1..9> of <цель> to <предмет>", "предмет в хотбар", "set slot hotbar player");
        eff("set lore of player's tool to \"$0\"", "set lore of player's tool to \"...\"", "описание предмета", "set add clear lore item tool");
        eff("enchant player's tool with $0 level 1", "enchant player's tool with <чары> level <N>", "зачаровать предмет", "enchant item tool level");
        eff("set nbt \"$0\" of player's tool to ", "set nbt \"<путь>\" of player's tool to <знач>", "записать NBT тег", "set remove delete nbt item entity block");
        eff("set gamemode of player to $0", "set gamemode of <цель> to survival|creative|...", "сменить режим игры", "set gamemode game mode creative survival adventure spectator");
        eff("set health of player to $0", "set health of <цель> to <N>", "здоровье игрока", "set health player");
        eff("set max health of player to $0", "set max health of <цель> to <N>", "макс. здоровье", "set max health player");
        eff("set food level of player to $0", "set food level of <цель> to <0..20>", "уровень голода", "set food hunger saturation");
        eff("set xp level of player to $0", "set xp level of <цель> to <N>", "уровень опыта", "set xp level experience");
        eff("set flight of player to $0", "set flight of <цель> to true|false", "разрешить полёт", "set flight flying player");
        eff("set walk speed of player to $0", "set walk speed of <цель> to <0.2>", "скорость ходьбы", "set walk fly speed player");
        eff("give player effect $0 for 10 seconds level 1", "give <цель> effect <id> for <N> seconds [level M]", "эффект зелья", "give potion effect speed strength regeneration");
        eff("remove effect $0 from player", "remove effect <id> from <цель>", "снять эффект зелья", "remove potion effect");
        eff("clear effects of player", "clear effects of <цель>", "снять все эффекты", "clear all effects potion");
        eff("kick player due to \"$0\"", "kick <цель> due to \"...\"", "кикнуть игрока", "kick ban pardon player");

        // Перемещение, Импульсы, Мир и Сущности
        eff("teleport player to $0", "teleport <цель> to <x y z | цель | локация>", "телепортация", "teleport tp player location");
        eff("push player upwards with force $0", "push <сущность> upwards|forwards with force <N>", "толкнуть по направлению", "push launch upwards forwards force");
        eff("push targeted entity away from player with force $0", "push <сущность> away from|towards <цель> with force <N>", "оттолкнуть / притянуть", "push entity away from towards");
        eff("set velocity of player to $0 1 0", "set velocity of <цель> to <vx> <vy> <vz>", "задать вектор скорости", "set velocity vector motion");
        eff("spawn entity \"minecraft:$0\" at player", "spawn entity \"<id>\" at <локация>", "призвать сущность", "spawn entity mob summon named health equipment");
        eff("set block at $0 to stone", "set block at <x y z | локация> to <блок>", "поставить блок", "set block fill world");
        eff("fill from $0 to  with stone", "fill from <x1 y1 z1> to <x2 y2 z2> with <блок>", "заполнить область", "fill blocks region");
        eff("drop 1 $0 at player", "drop [кол-во] <предмет> at <локация>", "выбросить предмет в мир", "drop item location");
        eff("strike lightning at $0", "strike [fake] lightning at <цель|локация>", "удар молнии", "strike fake lightning");
        eff("create safe explosion at $0 with power 2", "create [safe] explosion at <локация> with power <N>", "взрыв (safe — без поломки блоков)", "create safe explosion explode");
        eff("set time to $0", "set time to day|night|noon|<тики>", "время мира", "set time day night weather clear rain thunder");

        // Звуки, Частицы, Анимации и Камера
        eff("play sound $0 to player with volume 1.0 pitch 1.0", "play sound <id> to <цель> [with volume V pitch P]", "проиграть звук", "play sound audio volume pitch");
        eff("play animation \"scriptcraft:$0\" on player for all players", "play animation \"<id>\" on <цель> [first person]", "3D-анимация игрока", "play player animation first person");
        eff("stop animation of player", "stop animation of <цель>", "остановить анимацию", "stop player animation");
        eff("spawn particle $0 at player count 20 speed 0.05", "spawn particle <id> at <цель> count <N> speed <S>", "частицы в мире", "spawn particle ring color offset");
        eff("shake camera of player with intensity $0 for 10 ticks", "shake camera of <цель> with intensity <I> for <N> ticks", "тряска камеры", "shake camera screen");
        eff("fade screen of player to #000000 in 4 stay 8 out 8 ticks", "fade screen of <цель> to <#HEX> in <A> stay <B> out <C> ticks", "затемнение экрана", "fade screen camera color");
        eff("set camera fov of player to $0 for 20 ticks", "set camera fov of <цель> to <FOV> for <N> ticks", "зум / FOV камеры", "set camera fov roll tilt reset");
        eff("reset camera of player", "reset camera of <цель>", "сброс эффектов камеры", "reset camera");
        eff("play cutscene for player points \"$0\" for 6 seconds", "play cutscene for <цель> points \"x y z; ...\" for <N> seconds", "катсцена: плавный пролёт по точкам", "play cutscene camera path spline points катсцена камера пролет");
        eff("stop cutscene of player", "stop cutscene of <цель>", "остановить катсцену", "stop cutscene camera");

        // 3D GeckoLib / Blockbench мобы, оружие, блоки, NPC и Props (аттачи на костях)
        eff("spawn geo entity \"$0\" at player", "spawn geo entity \"<ns:id>\" at <лок>", "призвать 3D Geo-моба/босса/турель", "spawn geo entity mob boss turret prop 3d geckolib");
        eff("play geo animation \"$0\" on last spawned entity", "play geo animation \"<клип>\" on <сущность> [loop]", "запустить 3D-анимацию моба", "play geo animation entity boss geckolib");
        eff("stop geo animation on last spawned entity", "stop geo animation on <сущность>", "остановить 3D-анимацию моба", "stop geo animation entity");
        eff("clear all geo entities", "clear all geo entities", "удалить всех 3D Geo-мобов", "clear remove all geo entities");
        eff("play item animation \"$0\" for player", "play item animation \"<клип>\" for <игрок> [offhand]", "3D-анимация оружия/предмета в руке", "play item weapon animation geckolib");
        eff("play block animation \"$0\" at targeted block", "play block animation \"<клип>\" at <блок>", "3D-анимация блока/мебели", "play block animation geckolib");
        eff("spawn npc \"$0\" at player named \"&eNPC\" skin \"Notch\"", "spawn npc \"<id>\" at <лок> named \"...\" skin \"...\"", "создать фейкового игрока-NPC со скином", "spawn npc fake player skin рп нпс");
        eff("equip npc \"$0\" with mainhand diamond_sword", "equip npc \"<id>\" with <слот> <предмет>", "экипировать NPC бронёй/оружием", "equip npc weapon armor helmet chestplate");
        eff("play npc animation \"$0\" on npc \"commander\"", "play npc animation \"<id>\" on npc \"<id>\"", "проиграть РП-анимацию на NPC", "play npc animation playeranimator");
        eff("make npc \"$0\" walk to player speed 1.0", "make npc \"<id>\" walk to <лок> [speed N]", "отправить NPC идти к точке", "make npc walk move look");
        eff("remove npc \"$0\"", "remove npc \"<id>\"", "удалить NPC по id", "remove delete clear all npcs");
        eff("attach prop \"$0\" model \"gecko:shoulder_drone\" animation \"hover\" to body of player offset 6 4 3", "attach prop \"<слот>\" model \"<geo>\" to <кость> of player", "привязать 3D-модель к кости игрока/NPC", "attach prop model item bone head body right_arm аттач проп");
        eff("detach prop \"$0\" from player", "detach prop \"<слот>\" from <цель>", "отвязать 3D-аттач со слота", "detach clear props player");

        // Голограммы, Боссбары, Скорборд, Команды, LuckPerms
        eff("create hologram \"$0\" text \"&eТекст\" at player scale 1.2", "create hologram \"<id>\" text \"...\" at <лок>", "3D-голограмма", "create move remove clear hologram");
        eff("create bossbar \"$0\" title \"&cБосс\" color red progress 100 for all players", "create bossbar \"<id>\" title \"...\"", "полоса босса сверху", "create set show hide remove bossbar");
        eff("set scoreboard title \"$0\" for player", "set scoreboard title \"...\" for <цель>", "заголовок скорборда", "set clear scoreboard title line");
        eff("set scoreboard line $0 to \"\" for player", "set scoreboard line <1..15> to \"...\" for <цель>", "строка скорборда", "set scoreboard line");
        eff("execute console command \"$0\"", "execute console command \"...\"", "команда от консоли", "execute console make player command");
        eff("make player execute command \"$0\"", "make <цель> execute command \"...\"", "команда от имени игрока", "make player execute command");
        eff("add group \"$0\" to player", "add group \"<группа>\" to <цель>", "LuckPerms группа", "add remove set group grant revoke permission prefix suffix luckperms");

        // Кастомные GUI-экраны и HUD
        eff("open screen \"$0\" to player:", "open screen \"<Заголовок>\" to <цель>:", "открыть GUI-экран", "open screen gui menu");
        eff("update screen \"$0\" for player:", "update screen [\"...\"] for <цель>:", "обновить элементы GUI", "update screen gui");
        eff("close screen of player", "close screen of <цель>", "закрыть GUI-экран", "close screen gui");
        eff("set text of screen \"$0\" for player to \"\"", "set text of screen \"<id>\" for <цель> to <знач>", "обновить текст в GUI", "set text of screen element");
        eff("set position of screen \"$0\" for player to 0 0", "set position of screen \"<id>\" for <цель> to <X> <Y>", "переместить элемент GUI", "set position move of screen element");
        eff("set size of screen \"$0\" for player to 100 20", "set size of screen \"<id>\" for <цель> to <W> <H>", "размер элемента GUI", "set size of screen element");
        eff("set image of screen \"$0\" for player to \"\"", "set image of screen \"<id>\" for <цель> to \"...\"", "сменить текстуру в GUI", "set image texture of screen");
        eff("set skin of screen \"$0\" for player to \"%player%\"", "set skin of screen \"<id>\" for <цель> to \"...\"", "сменить скин 3D-модели в GUI", "set skin entity spin look of screen");
        eff("set value of screen bar \"$0\" for player to 50", "set value of screen bar \"<id>\" for <цель> to <N>", "значение полосы/контрола GUI", "set value max of screen bar");
        eff("animate screen \"$0\" for player to 0 0 in 5 ticks", "animate screen \"<id>\" for <цель> to <X> <Y> in <N> ticks", "плавная анимация (tween)", "animate tween screen element");
        eff("show screen element \"$0\" to player", "show screen element \"<id>\" to <цель>", "показать скрытый элемент", "show hide enable disable remove screen element");
        eff("hide screen element \"$0\" from player", "hide screen element \"<id>\" from <цель>", "скрыть элемент GUI", "hide screen element");
        eff("gui float \"$0\" at 0 0 color #FACC15 for 20 ticks to player", "gui float \"...\" at <X> <Y> color <#HEX>", "всплывающий текст в GUI", "gui float particle fx");
        eff("gui particle \"spark\" count 10 at 0 0 color #38BDF8 spread 20 to player", "gui particle \"<тип>\" count <N> at <X> <Y>", "частицы поверх GUI", "gui particle spark magic gold fire ice");
        eff("create hud \"$0\" for player:", "create hud \"<группа>\" for <цель>:", "постоянный HUD на экране", "create show hide remove clear hud hide vanilla");

        // ==================== УСЛОВИЯ (if ...:) ====================
        cond("{$0} is set", "{переменная} is set", "переменная задана", "is set not variable");
        cond("{$0} is not set", "{переменная} is not set", "переменная пуста", "is not set null");
        cond("{$0::*} is empty", "{список::*} is empty", "список пуст", "list is empty not");
        cond("{$0::*} contains ", "{список::*} contains <знач>", "значение есть в списке", "list contains does not contain");
        cond("{$0::*} has key ", "{список::*} has key <ключ>", "ключ есть в списке", "list has key index");
        cond("player is leashed", "<сущность/игрок> is leashed", "привязан ли поводком", "is not leashed tethered поводок");
        cond("targeted entity is leashed", "targeted entity is leashed", "моб в прицеле на поводке", "targeted entity player is leashed");
        cond("player is sneaking", "player is sneaking", "игрок крадётся (Shift)", "player is sneaking crouching");
        cond("player is sprinting", "player is sprinting", "игрок бежит", "player is sprinting");
        cond("player is flying", "player is flying", "игрок летит", "player is flying gliding swimming sleeping alive burning op");
        cond("player is op", "player is op", "оператор сервера", "player is op permission");
        cond("player is online", "<цель> is online", "игрок в сети", "player is online offline banned");
        cond("player is holding $0", "player is holding <предмет>", "держит предмет в руке", "player is holding tool hand");
        cond("player has $0", "player has <предмет>", "предмет есть в инвентаре", "player has item inventory");
        cond("player has permission \"$0\"", "player has permission <0..4 | \"узел\">", "проверка прав / LuckPerms", "player group has permission");
        cond("player has screen \"$0\" open", "player has screen [\"...\"] open", "открыт ли GUI-экран", "player has screen open in gui");
        cond("player is in world $0", "player is in world overworld|the_nether|...", "проверка измерения", "player is in world creative survival group");
        cond("targeted entity is set", "targeted entity is set", "есть ли сущность в прицеле", "targeted clicked entity player block is set");

        // ==================== ВЫРАЖЕНИЯ (Expressions) ====================
        expr("health of player", "текущее HP (число)", "health hp of player");
        expr("max health of player", "максимум HP", "max health of player");
        expr("food level of player", "сытость (0..20)", "food level hunger saturation of player");
        expr("xp level of player", "уровень опыта", "xp level experience of player");
        expr("walk speed of player", "скорость ходьбы", "walk fly speed of player");
        expr("gamemode of player", "режим игры (текст)", "gamemode game mode of player");
        expr("world of player", "id мира игрока", "world dimension of player");
        expr("name of player", "ник игрока", "name uuid ping of player");
        expr("uuid of player", "UUID игрока", "uuid of player");
        expr("uuid of targeted entity", "UUID сущности в прицеле", "uuid of targeted last spawned entity");
        expr("uuid of last spawned entity", "UUID последней созданной сущности", "uuid of last spawned entity");
        expr("player's tool", "предмет в основной руке", "player's tool held item");
        expr("location of player", "локация игрока", "location x y z yaw pitch of player");
        expr("location($0, 64, 0)", "location(x, y, z [, world])", "создать локацию", "location at coords world");
        expr("vector($0, 0, 0)", "vector(x, y, z)", "3D-вектор смещения/скорости", "vector ~~vector offset");
        expr("targeted block", "id блока в прицеле", "targeted block location of");
        expr("targeted entity", "тип сущности в прицеле", "targeted entity player location");
        expr("targeted player", "игрок в прицеле", "targeted player");
        expr("last spawned entity", "последняя созданная сущность", "last spawned entity");
        expr("clicked entity", "кликнутая сущность", "clicked entity player block");
        expr("leash length of player", "макс. длина поводка (число)", "leash length max_length of player entity поводок");
        expr("leash distance of player", "текущее натяжение поводка (блоки)", "leash distance stretch of player entity поводок");
        expr("leash holder of player", "кто держит поводок (ник/тип/block)", "leash holder owner of player entity поводок");
        expr("leash anchor of player", "локация крепления поводка", "leash anchor location of player entity поводок");
        expr("screen of player", "заголовок открытого экрана", "active open screen of player");
        expr("value of screen \"$0\" of player", "value of screen \"<id>\" of player", "значение input/slider/toggle", "value of screen element input");
        expr("number of all players", "онлайн сервера (число)", "number of all online players");
        expr("size of {$0::*}", "size of {список::*}", "размер списка", "size count length of list");
        expr("first element of {$0::*}", "first element of {список::*}", "первый элемент списка", "first last element index of list");
        expr("join {$0::*} with \", \"", "join {список::*} with \", \"", "склеить список в строку", "join list with string");
        expr("random integer between $0 and 10", "random integer between <A> and <B>", "случайное целое число", "random integer int number element between");
        expr("random number between $0 and 1", "random number between <A> and <B>", "случайное дробное число", "random number float between");
        expr("random element out of {$0::*}", "random element out of {список::*}", "случайный элемент списка", "random element out of list");
        expr("round($0)", "round(x)", "округление до целого", "round math");
        expr("floor($0)", "floor(x)", "округление вниз", "floor ceil math");
        expr("ceil($0)", "ceil(x)", "округление вверх", "ceil math");
        expr("abs($0)", "abs(x)", "модуль числа", "abs math");
        expr("sqrt($0)", "sqrt(x)", "квадратный корень", "sqrt sin cos tan pow math");
        expr("min($0, )", "min(a, b)", "меньшее из двух", "min max math");
        expr("max($0, )", "max(a, b)", "большее из двух", "max min math");
        expr("clamp($0, 0, 100)", "clamp(v, min, max)", "ограничить диапазон", "clamp math");
        expr("length of $0", "length of <текст>", "длина строки", "length lowercase uppercase trim of string");
        expr("substring of $0 from 1 to 5", "substring of <текст> from <A> to <B>", "срез строки", "substring replace string");
        expr("nbt of player's tool", "SNBT предмета в руке", "nbt of slot player's tool inventory");
        expr("group of player", "основная группа LuckPerms", "group groups prefix suffix meta of player");

        // ==================== ПЛЕЙСХОЛДЕРЫ (%...%) ====================
        ph("%player%", "имя игрока", "join quit chat death move");
        ph("%online%", "онлайн сервера", "");
        ph("%arg-1%", "1-й аргумент команды", "command");
        ph("%arg-2%", "2-й аргумент команды", "command");
        ph("%args%", "все аргументы команды", "command");
        ph("%message%", "текст сообщения чата", "chat");
        ph("%victim%", "жертва / отвязанный", "kill attack projectile hit leash break");
        ph("%attacker%", "атакующий", "death damage kill attack");
        ph("%holder%", "держатель поводка / block", "leash break");
        ph("%damage%", "величина урона", "damage");
        ph("%cause%", "причина урона/смерти", "death damage kill");
        ph("%block%", "id блока события", "break place right click left click");
        ph("%item%", "id предмета события", "drop pickup receive consume craft fish item break");
        ph("%amount%", "количество предметов", "receive craft");
        ph("%x%", "координата X", "move break place projectile hit");
        ph("%y%", "координата Y", "move break place projectile hit");
        ph("%z%", "координата Z", "move break place projectile hit");
        ph("%distance%", "пройденная дистанция", "move");
        ph("%yaw%", "поворот головы по горизонтали", "turning around");
        ph("%pitch%", "наклон головы по вертикали", "turning around");
        ph("%world%", "мир игрока", "world change");
        ph("%from%", "предыдущий мир/режим", "world change gamemode change");
        ph("%gamemode%", "режим игры", "gamemode change");
        ph("%command%", "выполненная команда", "command");
        ph("%key%", "нажатая клавиша", "key key release key hold");
        ph("%screen%", "открытый GUI-экран", "screen open screen close key gui change");
        ph("%element%", "id изменённого контрола", "gui change");
        ph("%value%", "новое значение контрола", "gui change input slider toggle");
        ph("%loop-number%", "номер итерации 1..N", "loop");
        ph("%loop-player%", "игрок текущей итерации", "loop");
        ph("%loop-index%", "ключ элемента списка", "loop");
        ph("%loop-value%", "значение элемента списка", "loop");
        ph("%sneaking%", "крадётся ли (true/false)", "sneak");
        ph("%sprinting%", "бежит ли (true/false)", "sprint");
        ph("%projectile%", "тип снаряда", "shoot projectile hit");
        ph("%targeted entity%", "сущность в прицеле", "");
        ph("%targeted block%", "блок в прицеле", "");
        ph("%targeted player%", "игрок в прицеле", "");
        ph("%nl%", "перенос строки в тултипе/тексте", "screen gui");

        // ==================== ЭЛЕМЕНТЫ И МОДИФИКАТОРЫ GUI ====================
        gui("box at 0 0 size 200x120 anchor center color #EE0F172A border #FF38BDF8 radius 6 layer 0", "box at X Y size WxH ...", "прямоугольник / панель", "box rect panel background");
        gui("text \"$0\" at 0 0 anchor center shadow layer 2", "text \"...\" at X Y ...", "текстовая надпись", "text label shadow scale");
        gui("button \"$0\" at 0 0 size 100x20 anchor center run  keep open", "button \"...\" at X Y size WxH ...", "интерактивная кнопка", "button btn click run");
        gui("image \"$0\" at 0 0 size 32x32 texture 64x64 anchor center", "image \"...\" at X Y size WxH ...", "PNG-изображение / спрайт", "image sprite texture png");
        gui("slot diamond 1 at 0 0 anchor center", "slot <предмет> [кол-во] at X Y", "иконка предмета / хотбар", "slot item hotbar");
        gui("bar \"$0\" value 100 max 100 at 0 0 size 120x8 anchor center color #FF22C55E", "bar \"<id>\" value V max M ...", "полоса прогресса / HP", "bar progress health");
        gui("input \"$0\" at 0 0 size 120x18 anchor center placeholder \"Текст...\"", "input \"<id>\" at X Y size WxH", "поле ввода текста", "input field text");
        gui("slider \"$0\" min 0 max 100 value 50 at 0 0 size 120x14 anchor center", "slider \"<id>\" min A max B ...", "ползунок числа", "slider range");
        gui("toggle \"$0\" at 0 0 size 64x16 anchor center value true", "toggle \"<текст>\" id ... value true", "переключатель вкл/выкл", "toggle checkbox switch");
        gui("player \"%player%\" at 0 0 size 64x96 anchor center scale 32 look cursor", "player \"<ник/скин>\" at X Y ...", "3D-игрок со скином и бронёй", "player skin 3d model avatar");
        gui("entity \"minecraft:$0\" at 0 0 size 64x96 anchor center spin right speed 35", "entity \"<id>\" at X Y ...", "3D-модель любой сущности", "entity mob 3d model");
        gui("list vertical at 0 0 size 180x140 anchor center row_height 22 scrollbar id $0:", "list vertical at X Y size WxH ...:", "прокручиваемый список", "list from vertical horizontal");

        GUI_LIST_ROWS.add(new Spec("row \"$0\" right \"\" hover_sound ui.button.click keep open", "row \"<текст>\" [icon/head/badge/right/run]", "GUI", COL_GUI, "строка списка", "row item icon head badge bar button right on_hover hover_sound"));
        GUI_LIST_ROWS.add(new Spec("separator \"$0\"", "separator [\"заголовок\"]", "GUI", COL_GUI, "разделитель секций", "separator divider"));
        GUI_LIST_ROWS.add(new Spec("empty 6", "empty [высота]", "GUI", COL_GUI, "пустой отступ", "empty spacer"));

        gmod("anchor center", "привязка (center/left/right/top/bottom)");
        gmod("size $0x20", "размер элемента WxH");
        gmod("color #FF$0", "основной цвет #AARRGGBB");
        gmod("gradient #FF$0", "цвет градиента");
        gmod("border #FF$0", "цвет рамки");
        gmod("border_width 1", "толщина рамки");
        gmod("radius 6", "скругление углов");
        gmod("layer $0", "слой отрисовки (Z-order)");
        gmod("id $0", "идентификатор для динамики");
        gmod("run $0", "функция или /команда по ЛКМ");
        gmod("keep open", "не закрывать экран при клике");
        gmod("tooltip \"$0\"", "всплывающая подсказка");
        gmod("hover_scale 1.08", "увеличение при наведении");
        gmod("hover_sound ui.button.click volume 0.4 pitch 1.2", "звук наведения курсора");
        gmod("on_hover $0", "функция при наведении курсора");
        gmod("look cursor", "3D-модель следит за курсором");
        gmod("spin right speed 35", "вращение 3D-модели");
        gmod("follow cursor", "иконка следует за курсором");
        gmod("hidden", "скрыть элемент при открытии");

        // ==================== СВОЙСТВА DEFINE ITEM / BLOCK / ENTITY / ARMOR ====================
        defp("name: \"$0\"", "отображаемое имя");
        defp("texture: \"$0\"", "путь к PNG-текстуре");
        defp("model: \"$0\"", "путь к 3D-модели .geo.json (GeckoLib)");
        defp("animation: \"$0\"", "путь к .animation.json");
        defp("idle_animation: \"idle\"", "фоновая анимация покоя");
        defp("walk_animation: \"walk\"", "анимация ходьбы 3D-моба");
        defp("attack_animation: \"attack\"", "анимация атаки 3D-моба/турели");
        defp("death_animation: \"death\"", "анимация смерти 3D-моба");
        defp("ai: hostile", "ИИ моба (hostile / passive / turret / none)");
        defp("health: 100", "здоровье 3D-моба или босса");
        defp("speed: 0.28", "скорость передвижения 3D-моба");
        defp("attack_range: 12.0", "радиус атаки моба или турели");
        defp("hitbox: 1.2x2.4", "хитбокс ШxВ в блоках");
        defp("scale: 1.0", "масштаб 3D-модели");
        defp("particle_bone: \"core=end_rod\"", "партиклы из кости/локатора");
        defp("slot: helmet", "слот 3D-брони (helmet/chestplate/leggings/boots)");
        defp("defense: 6", "очки защиты 3D-брони");
        defp("toughness: 2.0", "твёрдость 3D-брони");
        defp("lore: \"$0\"", "описание предмета");
        defp("glow: true", "перелив зачарования / _glowmask.png");
        defp("stack: 64", "макс. размер стака");
        defp("type: $0", "тип (sword/pickaxe/axe/shovel/food/helmet/chestplate)");
        defp("damage: $0", "урон оружия или моба");
        defp("food: $0", "сытость еды");
        defp("hardness: 2.0", "прочность блока");
        defp("resistance: 6.0", "взрывоустойчивость блока");
        defp("light: 15", "свечение блока (0..15)");
        defp("facing: horizontal", "поворот блока к игроку");
        defp("tool: pickaxe", "нужный инструмент");
        defp("render: cutout", "прозрачность (solid/cutout/translucent)");
    }

    private static void ev(String tpl, String hint, String search) {
        EVENTS.add(new Spec(tpl, null, "EVT", COL_EVENT, hint, (tpl + " " + search).toLowerCase(Locale.ROOT)));
    }

    private static void ev(String tpl, String lbl, String hint, String search) {
        EVENTS.add(new Spec(tpl, lbl, "EVT", COL_EVENT, hint, (tpl + " " + search).toLowerCase(Locale.ROOT)));
    }

    private static void flow(String tpl, String lbl, String hint, String search) {
        FLOW.add(new Spec(tpl, lbl, "FLW", COL_FLOW, hint, (tpl + " " + search).toLowerCase(Locale.ROOT)));
    }

    private static void eff(String tpl, String lbl, String hint, String search) {
        EFFECTS.add(new Spec(tpl, lbl, "EFF", COL_EFFECT, hint, (tpl + " " + search).toLowerCase(Locale.ROOT)));
    }

    private static void cond(String tpl, String lbl, String hint, String search) {
        CONDITIONS.add(new Spec(tpl, lbl, "CND", COL_COND, hint, (tpl + " " + search).toLowerCase(Locale.ROOT)));
    }

    private static void expr(String tpl, String hint, String search) {
        EXPRESSIONS.add(new Spec(tpl, null, "EXP", COL_EXPR, hint, (tpl + " " + search).toLowerCase(Locale.ROOT)));
    }

    private static void expr(String tpl, String lbl, String hint, String search) {
        EXPRESSIONS.add(new Spec(tpl, lbl, "EXP", COL_EXPR, hint, (tpl + " " + search).toLowerCase(Locale.ROOT)));
    }

    private static void ph(String tpl, String hint, String eventHint) {
        PLACEHOLDERS.add(new Spec(tpl, null, "PH", COL_EXPR, hint, (tpl + " " + eventHint).toLowerCase(Locale.ROOT)));
    }

    private static void gui(String tpl, String lbl, String hint, String search) {
        GUI_ELEMENTS.add(new Spec(tpl, lbl, "GUI", COL_GUI, hint, (tpl + " " + search).toLowerCase(Locale.ROOT)));
    }

    private static void gmod(String tpl, String hint) {
        GUI_MODIFIERS.add(new Spec(tpl, tpl.replace("$0", "..."), "OPT", COL_GUI, hint, tpl.replace("$0", "").toLowerCase(Locale.ROOT)));
    }

    private static void defp(String tpl, String hint) {
        DEFINE_PROPS.add(new Spec(tpl, tpl.replace("$0", "..."), "DEF", COL_DEF, hint, tpl.replace("$0", "").toLowerCase(Locale.ROOT)));
    }

    private StudioAutocomplete() {
    }

    /** Совместимость с одиночным вызовом одной строки. */
    public static Match suggest(String line, int col, boolean manual) {
        return suggest(line == null ? List.of() : List.of(line), 0, col, manual);
    }

    /**
     * Главная точка входа контекстного автодополнения с анализом окружающего блока и переменных документа.
     */
    public static Match suggest(List<String> lines, int lineIdx, int col, boolean manual) {
        if (lines == null || lines.isEmpty() || lineIdx < 0 || lineIdx >= lines.size()) {
            return null;
        }
        String line = lines.get(lineIdx);
        if (line == null || col < 0 || col > line.length()) {
            return null;
        }

        String before = line.substring(0, col);
        String trimmed = before.stripLeading();
        int indentCols = before.length() - trimmed.length();
        boolean indented = indentCols > 0;

        // 1. Никогда не мешаем внутри комментариев (# ...)
        if (trimmed.startsWith("#")) {
            return null;
        }
        // Если курсор стоит прямо посреди слова (справа буква/цифра) и это не ручной вызов Ctrl+Space — не мешаем
        if (!manual && col < line.length() && Character.isLetterOrDigit(line.charAt(col))) {
            return null;
        }

        // 2. Проверяем: внутри ли кавычек "..." сейчас находится курсор?
        boolean inQuotes = isInsideQuotes(before);
        int lastPct = before.lastIndexOf('%');
        int lastBrace = before.lastIndexOf('{');

        // Внутри текстовой строки "..." показываем ТОЛЬКО %плейсхолдеры% или %{переменные}%, чтобы не мешать писать обычный текст!
        if (inQuotes) {
            if (lastBrace > lastPct && before.indexOf('}', lastBrace) < 0) {
                return suggestVariables(lines, before, lastBrace, manual);
            }
            if (lastPct >= 0 && countPercentsSinceQuote(before) % 2 == 1) {
                String phPrefix = before.substring(lastPct).toLowerCase(Locale.ROOT);
                if (phPrefix.indexOf(' ') < 0 || phPrefix.startsWith("%targeted") || phPrefix.startsWith("%nbt")) {
                    return suggestPlaceholders(lines, lineIdx, lastPct, phPrefix);
                }
            }
            return null;
        }

        // Если строка уже закончена двоеточием ':' (например, написали "on join:" или "else:") — не мешаем Enter'у!
        if (!manual && trimmed.endsWith(":")) {
            return null;
        }

        // 3. Ввод переменной {...} в любом месте строки
        if (lastBrace >= 0 && before.indexOf('}', lastBrace) < 0) {
            String varPart = before.substring(lastBrace);
            if (varPart.indexOf(' ') < 0 && varPart.indexOf('"') < 0) {
                Match vm = suggestVariables(lines, before, lastBrace, true);
                if (vm != null) {
                    return vm;
                }
            }
        }

        // 4. Ввод плейсхолдера %...% вне кавычек
        int wordStart = findTokenStart(before);
        String word = before.substring(wordStart).toLowerCase(Locale.ROOT);
        if (word.startsWith("%")) {
            return suggestPlaceholders(lines, lineIdx, wordStart, word);
        }

        // Определяем родительский контекст по отступам выше (код / open screen / list / define)
        int curIndent = measureIndent(line);
        BlockKind blockKind = detectBlockKind(lines, lineIdx, curIndent);

        // 5. ВЕРХНИЙ УРОВЕНЬ (без отступа): события "on ...:" (включая "on player"!), command, every, function, define
        if (!indented) {
            if (trimmed.isEmpty() && !manual) {
                return null;
            }
            List<Scored> scored = new ArrayList<>();
            String q = trimmed.toLowerCase(Locale.ROOT);
            if (q.startsWith("on ") || q.equals("on") || "on".startsWith(q)) {
                scorePool(EVENTS, q, true, scored);
            }
            if (!q.startsWith("on ")) {
                scorePool(TOP_STRUCTURES, q, true, scored);
                if (scored.isEmpty()) {
                    scorePool(EVENTS, q, true, scored);
                }
            }
            return packResult(indentCols, trimmed, scored, manual);
        }

        // 6. Контекст внутри DEFINE ITEM / DEFINE BLOCK / DEFINE ENTITY / DEFINE ARMOR
        if (blockKind == BlockKind.DEFINE_ITEM || blockKind == BlockKind.DEFINE_BLOCK || blockKind == BlockKind.DEFINE_ENTITY) {
            if (!manual && trimmed.length() < 2) {
                return null;
            }
            List<Scored> scored = new ArrayList<>();
            scorePool(DEFINE_PROPS, trimmed.toLowerCase(Locale.ROOT), true, scored);
            return packResult(indentCols, trimmed, scored, manual);
        }

        // 7. Контекст внутри OPEN SCREEN / UPDATE SCREEN / CREATE HUD / LIST
        if (blockKind == BlockKind.SCREEN || blockKind == BlockKind.HUD || blockKind == BlockKind.LIST) {
            int firstSpace = trimmed.indexOf(' ');
            if (firstSpace < 0) {
                // Пользователь печатает тип элемента (box, text, button, player, entity, row...)
                if (!manual && trimmed.length() < 2) {
                    return null;
                }
                List<Scored> scored = new ArrayList<>();
                String q = trimmed.toLowerCase(Locale.ROOT);
                if (blockKind == BlockKind.LIST) {
                    scorePool(GUI_LIST_ROWS, q, true, scored);
                } else {
                    scorePool(GUI_ELEMENTS, q, true, scored);
                }
                return packResult(indentCols, trimmed, scored, manual);
            } else {
                // Пользователь дописывает модификатор в конце строки GUI-элемента (anchor, hover_sound, on_hover, tooltip...)
                if (!manual && word.length() < 2) {
                    return null;
                }
                List<Scored> scored = new ArrayList<>();
                scorePool(GUI_MODIFIERS, word, false, scored);
                return packResult(wordStart, word, scored, manual);
            }
        }

        // 8. ОБЫЧНЫЙ БЛОК КОДА: разбираем подконтекст (условие / выражение после оператора / начало эффекта)
        String lowerTrimmed = trimmed.toLowerCase(Locale.ROOT);

        // 8a. Контекст условия: после "if ", "else if ", "while ", а также после " and " / " or " внутри if/while
        int condExprStart = conditionClauseOffset(before, indentCols);
        if (condExprStart >= 0) {
            String clause = before.substring(condExprStart).stripLeading();
            int replaceAt = before.length() - clause.length();
            if (!manual && clause.length() < 2) {
                return null;
            }
            String cq = clause.toLowerCase(Locale.ROOT);
            List<Scored> scored = new ArrayList<>();
            // Если уже написана цель (например "player is l" или "targeted entity "), подсказываем и хвост условия, и целые шаблоны
            scorePool(CONDITIONS, cq, false, scored);
            scorePool(EXPRESSIONS, cq, false, scored);
            if (!word.isEmpty() && word.length() >= 2 && scored.isEmpty()) {
                scorePool(CONDITIONS, word, false, scored);
                scorePool(EXPRESSIONS, word, false, scored);
                return packResult(wordStart, word, scored, manual);
            }
            return packResult(replaceAt, clause, scored, manual);
        }

        // 8b. Контекст значения/выражения: после "to ", "by ", "with ", "from ", "return ", "(", ",", "+", "-", "*", "/"
        int valStart = valueClauseOffset(before, indentCols);
        if (valStart >= 0) {
            String valClause = before.substring(valStart).stripLeading();
            int replaceAt = before.length() - valClause.length();
            if (!manual && valClause.length() < 2) {
                return null;
            }
            String vq = valClause.toLowerCase(Locale.ROOT);
            List<Scored> scored = new ArrayList<>();
            scorePool(EXPRESSIONS, vq, false, scored);
            scoreUserFunctions(lines, vq, scored);
            if (scored.isEmpty() && word.length() >= 2 && wordStart > replaceAt) {
                scorePool(EXPRESSIONS, word, false, scored);
                return packResult(wordStart, word, scored, manual);
            }
            return packResult(replaceAt, valClause, scored, manual);
        }

        // 8c. Начало строки действия: эффекты + конструкции потока + пользовательские функции скрипта
        if (!manual && trimmed.length() < 2) {
            return null;
        }
        // Если пользователь поставил пробел после длинной законченной команды и ничего не совпадёт префиксно — не спамим
        List<Scored> scored = new ArrayList<>();
        scorePool(FLOW, lowerTrimmed, true, scored);
        scorePool(EFFECTS, lowerTrimmed, true, scored);
        scoreUserFunctions(lines, lowerTrimmed, scored);

        // Если человек в конце длинной команды начал печатать выражение в последнем слове (например "send round")
        if (scored.isEmpty() && word.length() >= 3 && wordStart > indentCols) {
            scorePool(EXPRESSIONS, word, false, scored);
            return packResult(wordStart, word, scored, manual);
        }
        return packResult(indentCols, trimmed, scored, manual);
    }

    private record Scored(Spec spec, int score) {
    }

    /**
     * Умное ранжирование по префиксу строки, префиксам отдельных слов по порядку и поисковым тегам/алиасам
     * (благодаря чему "on player" находит все события игрока, а "set leash" или "leash block" — нужные формы поводка!).
     */
    private static void scorePool(List<Spec> pool, String query, boolean statementMode, List<Scored> out) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            for (Spec sp : pool) {
                out.add(new Scored(sp, 50));
            }
            return;
        }
        String[] qTokens = q.split("\\s+");
        boolean endsWithSpace = !query.isEmpty() && Character.isWhitespace(query.charAt(query.length() - 1));

        for (Spec sp : pool) {
            String cleanTpl = sp.template.replace("$0", "").toLowerCase(Locale.ROOT);
            // Не предлагаем шаблон, если строка уже один-в-один совпадает с ним
            if (cleanTpl.equals(q)) {
                continue;
            }
            int sc = computeScore(cleanTpl, sp.searchTokens, q, qTokens, endsWithSpace, statementMode);
            if (sc > 0) {
                out.add(new Scored(sp, sc));
            }
        }
    }

    private static int computeScore(String cleanTpl, String searchHaystack, String q, String[] qTokens,
                                    boolean endsWithSpace, boolean statementMode) {
        // 1. Точное совпадение с начала шаблона — наивысший приоритет
        if (cleanTpl.startsWith(q)) {
            return 1000 - Math.min(200, cleanTpl.length());
        }
        // Если пользователь только что нажал пробел в длинной строке (3+ слов), не вываливаем далёкие fuzzy-совпадения
        if (endsWithSpace && qTokens.length >= 3 && !searchHaystack.contains(q)) {
            return 0;
        }

        String[] tplWords = splitWords(cleanTpl);
        String[] searchWords = splitWords(searchHaystack);

        // 2. Все слова запроса по порядку являются префиксами слов шаблона (напр. "set pos screen" -> "set position of screen...")
        int ti = 0;
        int matchedInTpl = 0;
        for (String qt : qTokens) {
            while (ti < tplWords.length) {
                if (tplWords[ti].startsWith(qt)) {
                    matchedInTpl++;
                    ti++;
                    break;
                }
                ti++;
            }
        }
        if (matchedInTpl == qTokens.length) {
            boolean firstWordMatches = tplWords.length > 0 && tplWords[0].startsWith(qTokens[0]);
            return (firstWordMatches ? 780 : 620) - Math.min(120, cleanTpl.length());
        }

        // 3. Все слова запроса совпадают как префиксы слов в поисковых ключевых словах / алиасах
        //    (например: "on player" -> searchWords содержит "on" и "player" у всех событий игрока!)
        for (String qt : qTokens) {
            if (qt.length() < 2 && !qt.equals("on") && !qt.equals("if")) {
                return 0;
            }
            boolean tokenFound = false;
            for (String sw : searchWords) {
                if (sw.startsWith(qt)) {
                    tokenFound = true;
                    break;
                }
            }
            if (!tokenFound) {
                return 0;
            }
        }
        // Для однословного короткого запроса в режиме начала строки требуем совпадение первого слова или сильного тега
        if (statementMode && qTokens.length == 1 && q.length() < 3) {
            if (tplWords.length == 0 || !tplWords[0].startsWith(q)) {
                return 0;
            }
        }
        boolean sameHead = tplWords.length > 0 && qTokens.length > 0 && tplWords[0].equals(qTokens[0]);
        return (sameHead ? 520 : 360) - Math.min(100, cleanTpl.length());
    }

    private static String[] splitWords(String s) {
        return s.replaceAll("[^a-zа-яё0-9_%#-]+", " ").trim().split("\\s+");
    }

    private static Match packResult(int replaceFrom, String currentTyped, List<Scored> scored, boolean manual) {
        if (scored.isEmpty()) {
            return null;
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed());
        List<Item> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Scored sc : scored) {
            Item item = sc.spec.toItem();
            if (item.insertText().equalsIgnoreCase(currentTyped)) {
                continue;
            }
            if (seen.add(item.label())) {
                out.add(item);
                if (out.size() >= 24) {
                    break;
                }
            }
        }
        return out.isEmpty() ? null : new Match(replaceFrom, out);
    }

    /** Собирает все {переменные} и {списки::*} из открытого документа и подсказывает их! */
    private static Match suggestVariables(List<String> lines, String before, int bracePos, boolean manual) {
        String typed = before.substring(bracePos).toLowerCase(Locale.ROOT); // например "{-td"
        Set<String> vars = new LinkedHashSet<>();
        for (String ln : lines) {
            if (ln == null || ln.stripLeading().startsWith("#")) {
                continue;
            }
            Matcher m = VAR_SCAN.matcher(ln);
            while (m.find()) {
                String full = "{" + m.group(1) + "}";
                if (full.length() > 2 && !full.equalsIgnoreCase(typed)) {
                    vars.add(full);
                }
            }
        }
        List<Item> items = new ArrayList<>();
        for (String v : vars) {
            if (v.toLowerCase(Locale.ROOT).startsWith(typed)) {
                boolean isLocal = v.startsWith("{-");
                boolean isList = v.endsWith("::*}");
                String hint = isList ? "список скрипта" : (isLocal ? "локальная переменная" : "глобальная переменная");
                items.add(new Item(v, v, v.length(), "VAR", COL_VAR, hint));
                if (items.size() >= 18) {
                    break;
                }
            }
        }
        if (items.isEmpty() && typed.equals("{") && manual) {
            items.add(new Item("{-local}", "{-$0}", 2, "VAR", COL_VAR, "временная переменная"));
            items.add(new Item("{global::%player%}", "{$0::%player%}", 1, "VAR", COL_VAR, "переменная игрока"));
        }
        return items.isEmpty() ? null : new Match(bracePos, items);
    }

    /** Подсказывает %плейсхолдеры%, поднимая наверх те, которые относятся к текущему событию "on ...:". */
    private static Match suggestPlaceholders(List<String> lines, int lineIdx, int startPos, String prefix) {
        String curEvent = findEnclosingEvent(lines, lineIdx);
        List<Scored> scored = new ArrayList<>();
        for (Spec sp : PLACEHOLDERS) {
            String low = sp.template.toLowerCase(Locale.ROOT);
            if (low.startsWith(prefix) && !low.equals(prefix)) {
                int bonus = (!curEvent.isEmpty() && sp.searchTokens.contains(curEvent)) ? 300 : 0;
                scored.add(new Scored(sp, 800 + bonus - low.length()));
            }
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed());
        List<Item> out = new ArrayList<>();
        for (Scored sc : scored) {
            out.add(sc.spec.toItem());
            if (out.size() >= 16) {
                break;
            }
        }
        return out.isEmpty() ? null : new Match(startPos, out);
    }

    /** Сканирует объявленные в этом файле функции "function Name(a: type, ...):" и предлагает их вызовы! */
    private static void scoreUserFunctions(List<String> lines, String query, List<Scored> out) {
        if (query.isEmpty()) {
            return;
        }
        for (String ln : lines) {
            if (ln == null) continue;
            Matcher m = FUNC_SCAN.matcher(ln);
            if (m.find()) {
                String fnName = m.group(1);
                String params = m.group(2).strip();
                String tpl = params.isEmpty() ? fnName + "()" : fnName + "($0)";
                String lbl = fnName + "(" + params + ")";
                Spec sp = new Spec(tpl, lbl, "FUN", COL_FUNC, "функция скрипта", fnName.toLowerCase(Locale.ROOT));
                if (fnName.toLowerCase(Locale.ROOT).startsWith(query)) {
                    out.add(new Scored(sp, 920 - fnName.length()));
                }
            }
        }
    }

    private static BlockKind detectBlockKind(List<String> lines, int lineIdx, int curIndent) {
        if (curIndent <= 0) {
            return BlockKind.NONE;
        }
        boolean sawListParent = false;
        for (int i = lineIdx - 1; i >= 0; i--) {
            String s = lines.get(i);
            if (s == null) continue;
            String tr = s.strip();
            if (tr.isEmpty() || tr.startsWith("#")) continue;
            int ind = measureIndent(s);
            if (ind < curIndent) {
                String low = tr.toLowerCase(Locale.ROOT);
                if (low.startsWith("list ") && low.endsWith(":")) {
                    sawListParent = true;
                    curIndent = ind;
                    continue;
                }
                if ((low.startsWith("open screen ") || low.startsWith("update screen")) && low.endsWith(":")) {
                    return sawListParent ? BlockKind.LIST : BlockKind.SCREEN;
                }
                if (low.startsWith("create hud ") && low.endsWith(":")) {
                    return BlockKind.HUD;
                }
                if (low.startsWith("define item ") || low.startsWith("define armor ")) {
                    return BlockKind.DEFINE_ITEM;
                }
                if (low.startsWith("define block ") && low.endsWith(":")) {
                    return BlockKind.DEFINE_BLOCK;
                }
                if ((low.startsWith("define entity ") || low.startsWith("define mob ") || low.startsWith("define boss ")) && low.endsWith(":")) {
                    return BlockKind.DEFINE_ENTITY;
                }
                if (ind == 0) {
                    return BlockKind.CODE;
                }
                curIndent = ind;
            }
        }
        return BlockKind.CODE;
    }

    private static String findEnclosingEvent(List<String> lines, int lineIdx) {
        for (int i = lineIdx; i >= 0; i--) {
            String s = lines.get(i);
            if (s == null) continue;
            String tr = s.strip();
            if (measureIndent(s) == 0 && tr.toLowerCase(Locale.ROOT).startsWith("on ") && tr.endsWith(":")) {
                return tr.substring(3, tr.length() - 1).strip().toLowerCase(Locale.ROOT);
            }
        }
        return "";
    }

    private static int conditionClauseOffset(String before, int indentCols) {
        String tr = before.substring(indentCols);
        String low = tr.toLowerCase(Locale.ROOT);
        int base = -1;
        if (low.startsWith("if ")) {
            base = indentCols + 3;
        } else if (low.startsWith("else if ")) {
            base = indentCols + 8;
        } else if (low.startsWith("elseif ")) {
            base = indentCols + 7;
        } else if (low.startsWith("while ")) {
            base = indentCols + 6;
        }
        if (base < 0) {
            return -1;
        }
        String afterIf = before.substring(base);
        String lowAfter = afterIf.toLowerCase(Locale.ROOT);
        int lastAnd = lowAfter.lastIndexOf(" and ");
        int lastOr = lowAfter.lastIndexOf(" or ");
        if (lastAnd >= 0 && lastAnd > lastOr) {
            return base + lastAnd + 5;
        }
        if (lastOr >= 0) {
            return base + lastOr + 4;
        }
        return base;
    }

    private static int valueClauseOffset(String before, int indentCols) {
        String tr = before.substring(indentCols);
        String low = tr.toLowerCase(Locale.ROOT);
        // Не перехватываем "leash ... to ..." и "teleport ... to ...", чтобы продолжали работать подсказки эффектов поводка/телепорта!
        if (low.startsWith("leash ") || low.startsWith("tether ") || low.startsWith("tie ")
                || low.startsWith("send ") || low.startsWith("play ") || low.startsWith("show ")
                || low.startsWith("hide ") || low.startsWith("open ") || low.startsWith("animate ")) {
            return -1;
        }
        if (low.startsWith("return ")) {
            return indentCols + 7;
        }
        // После арифметики или открывающей скобки/запятой
        for (int i = before.length() - 1; i >= indentCols; i--) {
            char c = before.charAt(i);
            if (c == '(' || c == ',' || c == '+' || c == '*' || c == '/') {
                return i + 1;
            }
        }
        int toIdx = low.lastIndexOf(" to ");
        if (toIdx > 0 && (low.startsWith("set ") || low.startsWith("add "))) {
            return indentCols + toIdx + 4;
        }
        int byIdx = low.lastIndexOf(" by ");
        if (byIdx > 0 && (low.startsWith("heal ") || low.startsWith("damage ") || low.startsWith("feed "))) {
            return indentCols + byIdx + 4;
        }
        return -1;
    }

    private static int measureIndent(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\t') w += 4;
            else if (c == ' ') w += 1;
            else break;
        }
        return w;
    }

    private static int findTokenStart(String before) {
        int i = before.length();
        while (i > 0) {
            char c = before.charAt(i - 1);
            if (Character.isWhitespace(c) || c == '(' || c == ')' || c == ',' || c == ':' || c == '"') {
                break;
            }
            i--;
        }
        return i;
    }

    private static boolean isInsideQuotes(String before) {
        int q = 0;
        for (int i = 0; i < before.length(); i++) {
            if (before.charAt(i) == '"') {
                q++;
            }
        }
        return (q % 2) == 1;
    }

    private static int countPercentsSinceQuote(String before) {
        int lastQ = before.lastIndexOf('"');
        int cnt = 0;
        for (int i = Math.max(0, lastQ + 1); i < before.length(); i++) {
            if (before.charAt(i) == '%') {
                cnt++;
            }
        }
        return cnt;
    }
}
