/*
 * ScriptCraft — данные документации (синтаксис языка).
 * Подключается обычным <script>, поэтому сайт работает и с GitHub Pages, и открытым как файл (file://).
 *
 * Как добавить запись: скопируйте любую запись ниже и поменяйте поля.
 *   id       — уникальный, латиница/цифры/дефис (используется в ссылке #id)
 *   category — один из ключей categories ниже
 *   name     — { ru, en } короткое название
 *   syntax   — массив шаблонов: <цель>, <число>, "текст", [необязательное], a|b — варианты
 *   desc     — { ru, en } описание (можно `код` в обратных кавычках)
 *   example  — пример кода на языке ScriptCraft (несколько строк — через \n или в `...`)
 *   since    — версия мода, с которой работает
 *   related  — id связанных записей
 * Проверка: node docs/check.js
 */
window.SC_DOCS = window.SC_DOCS || {};
window.SC_DOCS.syntax = {
  version: "0.1.2",
  categories: [
    { id: "events",     ru: "События",            en: "Events" },
    { id: "structures", ru: "Структуры",          en: "Structures" },
    { id: "effects",    ru: "Эффекты",            en: "Effects" },
    { id: "conditions", ru: "Условия",            en: "Conditions" },
    { id: "expressions",ru: "Выражения и плейсхолдеры", en: "Expressions & placeholders" },
    { id: "targets",    ru: "Цели (селекторы)",   en: "Targets (selectors)" },
    { id: "screen",     ru: "Экраны (GUI)",       en: "Screens (GUI)" },
    { id: "commands",   ru: "Команды мода",       en: "Mod commands" },
    { id: "config",     ru: "Конфиг",             en: "Config" }
  ],
  entries: [
  // ================================================================== СОБЫТИЯ
  {
    id: "event-join", category: "events",
    name: { ru: "Вход игрока", en: "Player join" },
    syntax: ["on join:"],
    desc: {
      ru: "Срабатывает, когда игрок заходит на сервер. Внутри `player` — зашедший игрок.",
      en: "Fires when a player joins the server. Inside, `player` is the joining player."
    },
    example: `on join:
    send "&aПривет, %player%!" to player
    broadcast "&7%player% зашёл (онлайн: %online%)"`,
    since: "0.1.0", related: ["event-quit", "ph-player", "eff-send", "eff-broadcast"]
  },
  {
    id: "event-quit", category: "events",
    name: { ru: "Выход игрока", en: "Player quit" },
    syntax: ["on quit:"],
    desc: {
      ru: "Срабатывает, когда игрок выходит с сервера. Внутри `player` — вышедший игрок.",
      en: "Fires when a player leaves the server. Inside, `player` is the leaving player."
    },
    example: `on quit:
    broadcast "&7%player% вышел с сервера"`,
    since: "0.1.0", related: ["event-join"]
  },

  // ================================================================== СТРУКТУРЫ
  {
    id: "struct-command", category: "structures",
    name: { ru: "Своя команда", en: "Custom command" },
    syntax: ["command /<имя>:", "command /<имя> permission <0-4>:"],
    desc: {
      ru: "Объявляет команду. Имя: `a-z 0-9 _` до 32 символов. `permission N` — нужный уровень оператора (0–4, по умолчанию 0 — все). Всё, что написано после команды, доступно как `%args%`. Имена `scriptcraft`, `sc`, `script` заняты модом; если команда уже есть в ванилле или другом моде, она пропускается.",
      en: "Declares a command. Name: `a-z 0-9 _`, up to 32 chars. `permission N` is the required operator level (0–4, default 0 = everyone). Everything after the command is available as `%args%`. Names `scriptcraft`, `sc`, `script` are reserved; a command that already exists in vanilla or another mod is skipped."
    },
    example: `command /hello:
    send "&bПривет, %player%! Ты написал: %args%" to player

command /kit permission 2:
    give player diamond 3
    heal player`,
    since: "0.1.0", related: ["ph-args", "cmd-reload"]
  },
  {
    id: "struct-if", category: "structures",
    name: { ru: "Условие if / else", en: "if / else" },
    syntax: ["if <условие>:", "else:"],
    desc: {
      ru: "Выполняет вложенные действия, если условие верно. `else:` пишется на том же отступе сразу после блока `if`. Можно вкладывать. `else if`, `and`/`or` пока нет — используйте вложенные `if`.",
      en: "Runs the nested actions when the condition is true. `else:` goes at the same indent right after the `if` block. Can be nested. No `else if`, `and`/`or` yet — use nested `if`s."
    },
    example: `command /rich:
    if {coins::%player%} >= 10:
        send "&aТы богат!" to player
    else:
        send "&cНужно ещё монет" to player`,
    since: "0.1.0", related: ["cond-compare", "cond-number", "cond-var-set", "cond-target-set"]
  },
  {
    id: "struct-loop", category: "structures",
    name: { ru: "Цикл по игрокам", en: "Loop over players" },
    syntax: ["loop <цель>:"],
    desc: {
      ru: "Выполняет вложенные действия для каждого игрока цели. Внутри `loop-player` — текущий игрок, `%loop-player%` — его имя. `player` внутри цикла — по-прежнему тот, кто вызвал скрипт. Циклы можно вкладывать; каждое действие каждого прохода тратит шаг `maxScriptSteps`. В эффект-командах в чате недоступен.",
      en: "Runs the nested actions for every player of the target. Inside, `loop-player` is the current player and `%loop-player%` its name. `player` still means whoever ran the script. Loops can be nested; every action of every pass costs one `maxScriptSteps` step. Not available in chat effect commands."
    },
    example: `command /gather permission 2:
    loop all players:
        send "&e%loop-player%, сбор у %player%!" to loop-player`,
    since: "0.1.2", related: ["target-loop-player", "ph-loop-player", "target-all"]
  },
  {
    id: "struct-define-block", category: "structures",
    name: { ru: "Свой блок", en: "Custom block" },
    syntax: ["define block <id>:", "define block <namespace>:<id>:"],
    desc: {
      ru: "Регистрирует блок (вместе с предметом-блоком) во вкладке творческого режима ScriptCraft. Свойства (все необязательные): `name:` название, `hardness:` прочность (1.5), `resistance:` взрывоустойчивость (6), `sound:` stone/wood/metal/glass/grass/sand/wool/gravel, `light:` свечение 0–15, `requires_tool:` true — дроп только подходящим инструментом. id — только `a-z 0-9 _ . -`. Работает только после **перезапуска** игры/сервера; файл должен быть одинаковым у сервера и клиентов.",
      en: "Registers a block (plus its block item) in the ScriptCraft creative tab. Properties (all optional): `name:` display name, `hardness:` (1.5), `resistance:` (6), `sound:` stone/wood/metal/glass/grass/sand/wool/gravel, `light:` 0–15, `requires_tool:` true — drops only with the right tool. id: only `a-z 0-9 _ . -`. Takes effect only after a **restart**; the file must be identical on the server and all clients."
    },
    example: `define block ruby_block:
    name: "Рубиновый блок"
    hardness: 5
    resistance: 6
    sound: metal
    light: 7
    requires_tool: true`,
    since: "0.1.0", related: ["struct-define-item", "struct-namespace"]
  },
  {
    id: "struct-define-item", category: "structures",
    name: { ru: "Свой предмет", en: "Custom item" },
    syntax: ["define item <id>:", "define item <namespace>:<id>:"],
    desc: {
      ru: "Регистрирует предмет. Свойства: `name:` название, `stack_size:` 1–64 (64), `rarity:` common/uncommon/rare/epic (цвет названия), `fire_resistant:` true — не горит в лаве. Как и блоки — только после перезапуска.",
      en: "Registers an item. Properties: `name:`, `stack_size:` 1–64 (64), `rarity:` common/uncommon/rare/epic (name colour), `fire_resistant:` true — survives lava. Like blocks — only after a restart."
    },
    example: `define item ruby:
    name: "Рубин"
    stack_size: 64
    rarity: rare
    fire_resistant: false`,
    since: "0.1.0", related: ["struct-define-block", "struct-namespace", "eff-give-item"]
  },
  {
    id: "struct-namespace", category: "structures",
    name: { ru: "Свой namespace", en: "Custom namespace" },
    syntax: ["namespace <ns>", "namespace <ns>:", "modid <ns>:"],
    desc: {
      ru: "Строка без отступа: все следующие `define` в этом файле получат id `<ns>:...` (в следующем файле снова `scriptcraft`). Запрещены `minecraft`, `forge`, `neoforge`, `c`; только латиница `a-z 0-9 _ . -`. Ресурсы таких блоков кладутся в `scriptcraft/resources/assets/<ns>/`.",
      en: "A line without indent: every following `define` in this file gets the id `<ns>:...` (the next file starts with `scriptcraft` again). `minecraft`, `forge`, `neoforge`, `c` are not allowed; only `a-z 0-9 _ . -`. Resources for those blocks go to `scriptcraft/resources/assets/<ns>/`."
    },
    example: `namespace example

define block ruby_block:      # -> example:ruby_block
    hardness: 5

define item othermod:gem:     # полный id в обход namespace
    name: "Самоцвет"`,
    since: "0.1.2", related: ["struct-define-block", "struct-define-item"]
  },
  {
    id: "struct-comment", category: "structures",
    name: { ru: "Комментарий и отступы", en: "Comments and indentation" },
    syntax: ["# комментарий"],
    desc: {
      ru: "Строка, начинающаяся с `#`, — комментарий. Блоки верхнего уровня (`on`, `command`, `define`) пишутся без отступа и заканчиваются двоеточием, действия — с отступом (пробелы или Tab = 4 пробела). Ошибка в строке не ломает файл: строка пропускается, ошибка видна в логе и при `/sc reload`. Файлы — UTF-8, `*.sk`; файл с именем на `-` выключен.",
      en: "A line starting with `#` is a comment. Top-level blocks (`on`, `command`, `define`) have no indent and end with a colon; actions are indented (spaces or Tab = 4 spaces). An error in one line does not break the file: the line is skipped and reported in the log and on `/sc reload`. Files are UTF-8 `*.sk`; a file whose name starts with `-` is disabled."
    },
    example: `# Это комментарий
command /hi:
    send "Hi" to player`,
    since: "0.1.0", related: ["cmd-disable"]
  },

  // ================================================================== ЭФФЕКТЫ: переменные
  {
    id: "eff-set-var", category: "effects",
    name: { ru: "Записать переменную", en: "Set variable" },
    syntax: ["set {<имя>} to <значение>"],
    desc: {
      ru: "Записывает значение (число, \"текст\", true/false или другую `{переменную}`). Текст с пробелами — в кавычках. `set {a} to {не_заданная}` удаляет `{a}`.",
      en: "Stores a value (number, \"text\", true/false or another `{variable}`). Text with spaces must be quoted. `set {a} to {unset}` deletes `{a}`."
    },
    example: `set {coins::%player%} to 0
set {motd} to "Добро пожаловать"`,
    since: "0.1.0", related: ["expr-variable", "eff-add-var", "eff-delete-var"]
  },
  {
    id: "eff-add-var", category: "effects",
    name: { ru: "Прибавить / отнять", en: "Add / remove" },
    syntax: ["add <число> to {<имя>}", "remove <число> from {<имя>}"],
    desc: {
      ru: "Меняет числовую переменную. Незаданная считается 0; если в переменной текст — ошибка выполнения.",
      en: "Changes a numeric variable. An unset one counts as 0; text in the variable is a runtime error."
    },
    example: `add 5 to {coins::%player%}
remove 1 from {coins::%player%}`,
    since: "0.1.0", related: ["eff-set-var", "expr-variable"]
  },
  {
    id: "eff-delete-var", category: "effects",
    name: { ru: "Удалить переменную", en: "Delete variable" },
    syntax: ["delete {<имя>}", "clear {<имя>}"],
    desc: {
      ru: "Удаляет переменную (после этого `is not set`).",
      en: "Deletes a variable (it becomes `is not set`)."
    },
    example: `delete {coins::%player%}`,
    since: "0.1.0", related: ["eff-set-var", "cond-var-set"]
  },

  // ================================================================== ЭФФЕКТЫ: чат
  {
    id: "eff-send", category: "effects",
    name: { ru: "Сообщение игрокам", en: "Send message" },
    syntax: ["send \"<текст>\"", "send \"<текст>\" to <цель>", "message \"<текст>\" to <цель>"],
    desc: {
      ru: "Личное сообщение игрокам цели (без `to` — игроку из контекста). Поддерживает плейсхолдеры, `{переменные}` и цвета `&`. Плейсхолдеры считаются от вызвавшего, а не от получателя.",
      en: "Private message to the target players (without `to` — the context player). Supports placeholders, `{variables}` and `&` colours. Placeholders are evaluated for the caller, not the recipient."
    },
    example: `send "&aПривет, %player%!" to player
send "&eВнимание!" to all players`,
    since: "0.1.0", related: ["eff-broadcast", "eff-reply", "expr-colors"]
  },
  {
    id: "eff-broadcast", category: "effects",
    name: { ru: "Сообщение всем", en: "Broadcast" },
    syntax: ["broadcast \"<текст>\""],
    desc: {
      ru: "Сообщение в чат всем игрокам сервера.",
      en: "A chat message to every player on the server."
    },
    example: `broadcast "&6Сервер перезапустится через 5 минут"`,
    since: "0.1.0", related: ["eff-send"]
  },
  {
    id: "eff-reply", category: "effects",
    name: { ru: "Ответ вызвавшему", en: "Reply" },
    syntax: ["reply \"<текст>\""],
    desc: {
      ru: "Ответ тому, кто запустил команду — работает и из консоли (где `player` нет).",
      en: "Replies to whoever ran the command — works from the console too (where there is no `player`)."
    },
    example: `command /ping:
    reply "Pong! Онлайн: %online%"`,
    since: "0.1.0", related: ["eff-send"]
  },
  {
    id: "eff-title", category: "effects",
    name: { ru: "Титры", en: "Title" },
    syntax: ["send title \"<текст>\" to <цель>", "send title \"<текст>\" with subtitle \"<текст>\" to <цель>"],
    desc: {
      ru: "Большой текст по центру экрана (0.5 с появление, 3.5 с показ, 1 с исчезновение).",
      en: "Big text in the middle of the screen (0.5 s fade in, 3.5 s stay, 1 s fade out)."
    },
    example: `send title "&6Привет" with subtitle "&eтак держать" to player`,
    since: "0.1.2", related: ["eff-actionbar", "eff-send"]
  },
  {
    id: "eff-actionbar", category: "effects",
    name: { ru: "Строка над хотбаром", en: "Action bar" },
    syntax: ["send action bar \"<текст>\" to <цель>", "send actionbar \"<текст>\" to <цель>"],
    desc: {
      ru: "Короткий текст над панелью быстрого доступа.",
      en: "Short text above the hotbar."
    },
    example: `send action bar "&aСохранено" to player`,
    since: "0.1.2", related: ["eff-title"]
  },
  {
    id: "eff-sound", category: "effects",
    name: { ru: "Звук", en: "Play sound" },
    syntax: ["play sound <id звука> to <цель>"],
    desc: {
      ru: "Проигрывает звук, который слышит только сам игрок. id — как в ванильных командах (`entity.player.levelup`), можно из модов (`mod:sound`).",
      en: "Plays a sound only that player hears. id as in vanilla commands (`entity.player.levelup`), mod sounds work too (`mod:sound`)."
    },
    example: `play sound entity.player.levelup to player`,
    since: "0.1.2", related: ["eff-title"]
  },

  // ================================================================== ЭФФЕКТЫ: предметы и перемещение
  {
    id: "eff-give-item", category: "effects",
    name: { ru: "Выдать предмет", en: "Give item" },
    syntax: ["give <цель> <предмет>", "give <цель> <предмет> <количество>"],
    desc: {
      ru: "Выдаёт предмет (если инвентарь полон — бросает рядом). Количество — число или `{переменная}` (до 2304). Короткий id ищется так: `minecraft:`, потом `scriptcraft:`, потом namespace из скриптов; надёжнее полный id `example:ruby`.",
      en: "Gives an item (drops it nearby if the inventory is full). Count is a number or `{variable}` (up to 2304). A short id is looked up as `minecraft:`, then `scriptcraft:`, then script namespaces; a full id like `example:ruby` is safer."
    },
    example: `give player diamond 3
give all players example:ruby`,
    since: "0.1.0", related: ["struct-define-item", "eff-give-effect"]
  },
  {
    id: "eff-teleport", category: "effects",
    name: { ru: "Телепорт", en: "Teleport" },
    syntax: ["teleport <цель> to <x> <y> <z>", "teleport <цель> to <цель>", "tp <цель> to ..."],
    desc: {
      ru: "Телепорт по координатам (в текущем мире игрока; запятые между числами можно писать) или к первому игроку второй цели (в его мир, с его поворотом). Пустая вторая цель — ничего.",
      en: "Teleports to coordinates (in the player's current world; commas between numbers are allowed) or to the first player of the second target (into their world, with their rotation). An empty second target does nothing."
    },
    example: `teleport player to 0, 100, 0
teleport other players in radius 100 of player to player`,
    since: "0.1.0", related: ["target-radius"]
  },
  {
    id: "eff-open-screen", category: "effects",
    name: { ru: "Открыть экран", en: "Open screen" },
    syntax: ["open screen \"<заголовок>\":", "open screen \"<заголовок>\" to <цель>:"],
    desc: {
      ru: "Открывает игрокам экран (GUI) с элементами, перечисленными с отступом ниже: `text`, `button`, `slot`, `image`. См. категорию «Экраны».",
      en: "Opens a screen (GUI) for the players, with elements listed indented below: `text`, `button`, `slot`, `image`. See the “Screens” category."
    },
    example: `command /menu:
    open screen "Главное меню" to player:
        text "Привет, %player%!" at 0, -40 color #FFD700 shadow
        button "Закрыть" at 0, 20 size 120x20 close`,
    since: "0.1.0", related: ["screen-text", "screen-button", "screen-slot", "screen-image"]
  },

  // ================================================================== ЭФФЕКТЫ: здоровье
  {
    id: "eff-heal", category: "effects",
    name: { ru: "Вылечить", en: "Heal" },
    syntax: ["heal <цель>", "heal <цель> by <число>"],
    desc: {
      ru: "Полностью восстанавливает здоровье или добавляет указанное количество (4 = два сердца).",
      en: "Fully restores health or adds the given amount (4 = two hearts)."
    },
    example: `heal player
heal players in radius 10 of player by {bonus}`,
    since: "0.1.0", related: ["eff-set-health", "eff-damage", "eff-feed"]
  },
  {
    id: "eff-damage", category: "effects",
    name: { ru: "Урон", en: "Damage" },
    syntax: ["damage <цель> by <число>"],
    desc: {
      ru: "Обычный урон: учитывает броню и режим игры (в креативе не действует).",
      en: "Generic damage: armour and game mode apply (no effect in creative)."
    },
    example: `damage player by 4`,
    since: "0.1.2", related: ["eff-heal", "eff-kill"]
  },
  {
    id: "eff-set-health", category: "effects",
    name: { ru: "Задать здоровье", en: "Set health" },
    syntax: ["set health of <цель> to <число>", "set player's health to <число>"],
    desc: {
      ru: "Ставит здоровье (от 0 до максимума игрока).",
      en: "Sets health (0 to the player's maximum)."
    },
    example: `set health of player to 10`,
    since: "0.1.2", related: ["eff-heal"]
  },
  {
    id: "eff-feed", category: "effects",
    name: { ru: "Накормить", en: "Feed" },
    syntax: ["feed <цель>", "feed <цель> by <число>"],
    desc: {
      ru: "Без числа — голод и насыщение на максимум (20); с числом — прибавляет голод.",
      en: "Without a number — food and saturation to max (20); with a number — adds food."
    },
    example: `feed player
feed player by 6`,
    since: "0.1.2", related: ["eff-set-food"]
  },
  {
    id: "eff-set-food", category: "effects",
    name: { ru: "Голод и насыщение", en: "Food level & saturation" },
    syntax: ["set food level of <цель> to <число>", "set hunger of <цель> to <число>", "set saturation of <цель> to <число>", "set player's food level|hunger|saturation to <число>"],
    desc: {
      ru: "Голод 0–20; насыщение не больше текущего голода (так работает ванилла).",
      en: "Food 0–20; saturation cannot exceed the current food level (vanilla rule)."
    },
    example: `set food level of player to 10
set saturation of player to 5`,
    since: "0.1.2", related: ["eff-feed"]
  },
  {
    id: "eff-kill", category: "effects",
    name: { ru: "Убить", en: "Kill" },
    syntax: ["kill <цель>"],
    desc: { ru: "Убивает игроков цели.", en: "Kills the target players." },
    example: `kill target of player`,
    since: "0.1.2", related: ["eff-damage"]
  },
  {
    id: "eff-fire", category: "effects",
    name: { ru: "Поджечь / потушить", en: "Ignite / extinguish" },
    syntax: ["ignite <цель> for <число> seconds|second|secs|sec", "extinguish <цель>"],
    desc: {
      ru: "Поджигает на N секунд (1–3600) или тушит.",
      en: "Sets on fire for N seconds (1–3600) or puts out the fire."
    },
    example: `ignite player for 5 seconds
extinguish player`,
    since: "0.1.2", related: ["eff-damage"]
  },
  {
    id: "eff-clear-inventory", category: "effects",
    name: { ru: "Очистить инвентарь", en: "Clear inventory" },
    syntax: ["clear inventory of <цель>", "clear player's inventory"],
    desc: { ru: "Удаляет все предметы из инвентаря.", en: "Removes every item from the inventory." },
    example: `clear inventory of player`,
    since: "0.1.2", related: ["eff-give-item"]
  },
  {
    id: "eff-gamemode", category: "effects",
    name: { ru: "Режим игры", en: "Game mode" },
    syntax: ["set gamemode of <цель> to <режим>", "set game mode of <цель> to <режим>", "set player's gamemode to <режим>"],
    desc: {
      ru: "Режим: `survival`/`s`/`0`, `creative`/`c`/`1`, `adventure`/`a`/`2`, `spectator`/`sp`/`3`.",
      en: "Mode: `survival`/`s`/`0`, `creative`/`c`/`1`, `adventure`/`a`/`2`, `spectator`/`sp`/`3`."
    },
    example: `set player's gamemode to creative
set gamemode of all players to survival`,
    since: "0.1.2", related: ["eff-flying"]
  },
  {
    id: "eff-flying", category: "effects",
    name: { ru: "Полёт", en: "Flying" },
    syntax: ["set flying of <цель> to true|false", "set flight of <цель> to true|false", "set player's flying to true|false"],
    desc: {
      ru: "`true` даёт право летать и сразу взлетает, `false` забирает право и останавливает полёт (в креативе/наблюдателе право не трогается). Также понимает yes/on/1.",
      en: "`true` grants flight and starts flying, `false` revokes it and stops flying (creative/spectator keep their ability). Also accepts yes/on/1."
    },
    example: `set flying of player to true`,
    since: "0.1.2", related: ["eff-gamemode"]
  },

  // ================================================================== ЭФФЕКТЫ: зелья и опыт
  {
    id: "eff-give-effect", category: "effects",
    name: { ru: "Эффект зелья", en: "Potion effect" },
    syntax: ["give <цель> effect <id> for <число> seconds", "give <цель> effect <id> for <число> seconds level|lvl <число>"],
    desc: {
      ru: "Накладывает эффект. `level 1` — без усиления (по умолчанию), `level 2` — II и т.д. Вместо `seconds` можно `second`, `secs`, `sec`. id: `speed`, `minecraft:night_vision`, `othermod:frost`.",
      en: "Applies an effect. `level 1` is the base level (default), `level 2` is II, etc. `second`, `secs`, `sec` also work. id: `speed`, `minecraft:night_vision`, `othermod:frost`."
    },
    example: `give player effect speed for 60 seconds level 2
give @a[distance=..10] effect regeneration for 10 seconds`,
    since: "0.1.2", related: ["eff-remove-effect"]
  },
  {
    id: "eff-remove-effect", category: "effects",
    name: { ru: "Снять эффекты", en: "Remove effects" },
    syntax: ["remove effect <id> from <цель>", "clear effects of <цель>", "clear all effects of <цель>"],
    desc: { ru: "Снимает один эффект или все.", en: "Removes one effect or all of them." },
    example: `remove effect speed from player
clear effects of player`,
    since: "0.1.2", related: ["eff-give-effect"]
  },
  {
    id: "eff-xp", category: "effects",
    name: { ru: "Опыт", en: "Experience" },
    syntax: ["give <число> xp|experience to <цель>", "set xp level of <цель> to <число>", "set level of <цель> to <число>", "set player's xp level to <число>"],
    desc: {
      ru: "`give N xp` добавляет очки опыта (не уровни; может быть отрицательным), `set xp level` ставит уровень.",
      en: "`give N xp` adds experience points (not levels; may be negative), `set xp level` sets the level."
    },
    example: `give 100 xp to player
set xp level of player to 30`,
    since: "0.1.2", related: []
  },

  // ================================================================== ЭФФЕКТЫ: команды
  {
    id: "eff-console-command", category: "effects",
    name: { ru: "Команда от консоли", en: "Console command" },
    syntax: ["execute console command \"<команда>\""],
    desc: {
      ru: "Выполняет команду с правами **консоли** (`/` в начале не обязателен). Выключается `allowCommandEffects = false`. Не подставляйте сюда `%args%` в командах для обычных игроков. Из эффект-команд в чате — только операторам.",
      en: "Runs a command with **console** permissions (leading `/` optional). Disabled by `allowCommandEffects = false`. Never put `%args%` here in commands available to regular players. From chat effect commands — operators only."
    },
    example: `execute console command "time set day"`,
    since: "0.1.2", related: ["eff-player-command", "cfg-allowCommandEffects"]
  },
  {
    id: "eff-player-command", category: "effects",
    name: { ru: "Команда от игрока", en: "Make player run command" },
    syntax: ["make <цель> execute command \"<команда>\""],
    desc: {
      ru: "Каждый игрок цели выполняет команду со своими правами. Выключается `allowCommandEffects = false`.",
      en: "Each target player runs the command with their own permissions. Disabled by `allowCommandEffects = false`."
    },
    example: `make player execute command "me поставил день"`,
    since: "0.1.2", related: ["eff-console-command"]
  },

  // ================================================================== УСЛОВИЯ
  {
    id: "cond-compare", category: "conditions",
    name: { ru: "Равно / не равно", en: "Equals / not equals" },
    syntax: ["<значение> is <значение>", "<значение> is not <значение>", "<значение> = <значение>", "<значение> == <значение>", "<значение> != <значение>"],
    desc: {
      ru: "Числа сравниваются как числа, текст — без учёта регистра. Справа — одно значение (текст с пробелами — в кавычках).",
      en: "Numbers compare as numbers, text case-insensitively. One value on the right (quote text with spaces)."
    },
    example: `if {rank::%player%} is "vip":
    send "&6VIP!" to player
if %player% is not "Notch":
    send "Ты не Notch" to player`,
    since: "0.1.0", related: ["struct-if", "cond-number"]
  },
  {
    id: "cond-number", category: "conditions",
    name: { ru: "Сравнение чисел", en: "Number comparison" },
    syntax: ["<значение> > <значение>", "<значение> < <значение>", "<значение> >= <значение>", "<значение> <= <значение>"],
    desc: {
      ru: "Если хоть одна сторона не число — условие ложно.",
      en: "If either side is not a number, the condition is false."
    },
    example: `if {coins::%player%} >= 10:
    remove 10 from {coins::%player%}`,
    since: "0.1.0", related: ["cond-compare"]
  },
  {
    id: "cond-var-set", category: "conditions",
    name: { ru: "Переменная задана", en: "Variable is set" },
    syntax: ["{<имя>} is set", "{<имя>} is not set"],
    desc: { ru: "Есть ли значение у переменной.", en: "Whether the variable has a value." },
    example: `if {coins::%player%} is not set:
    set {coins::%player%} to 0`,
    since: "0.1.0", related: ["eff-delete-var"]
  },
  {
    id: "cond-target-set", category: "conditions",
    name: { ru: "Есть ли игроки в цели", en: "Target is set" },
    syntax: ["<цель> is set", "<цель> is not set"],
    desc: {
      ru: "Есть ли хоть один игрок в цели (для `targeted player`, `players in radius ...`, `all players`, `@a[...]`, `player`). Для имени игрока не работает — `\"Steve\" is set` сравнивается как текст.",
      en: "Whether the target has at least one player (for `targeted player`, `players in radius ...`, `all players`, `@a[...]`, `player`). Does not work for a player name — `\"Steve\" is set` is compared as text."
    },
    example: `if targeted player is set:
    send "Ты смотришь на %targeted%" to player
else:
    send "Ты ни на кого не смотришь" to player`,
    since: "0.1.2", related: ["target-targeted", "target-radius", "ph-targeted"]
  },

  // ================================================================== ВЫРАЖЕНИЯ
  {
    id: "ph-player", category: "expressions",
    name: { ru: "%player% — имя игрока", en: "%player% — player name" },
    syntax: ["%player%"],
    desc: {
      ru: "Имя игрока из контекста (вызвал команду / зашёл / вышел). Работает в тексте и в именах переменных: `{coins::%player%}`.",
      en: "Name of the context player (ran the command / joined / quit). Works in text and in variable names: `{coins::%player%}`."
    },
    example: `send "Привет, %player%!" to player`,
    since: "0.1.0", related: ["ph-uuid", "target-player"]
  },
  {
    id: "ph-uuid", category: "expressions",
    name: { ru: "%uuid% — UUID игрока", en: "%uuid% — player UUID" },
    syntax: ["%uuid%"],
    desc: {
      ru: "UUID игрока из контекста — удобно для переменных, которые не должны теряться при смене ника.",
      en: "UUID of the context player — handy for variables that must survive a name change."
    },
    example: `add 1 to {joins::%uuid%}`,
    since: "0.1.0", related: ["ph-player"]
  },
  {
    id: "ph-online", category: "expressions",
    name: { ru: "%online% — игроков онлайн", en: "%online% — players online" },
    syntax: ["%online%"],
    desc: { ru: "Сколько игроков сейчас на сервере.", en: "How many players are online." },
    example: `broadcast "&7%player% зашёл (онлайн: %online%)"`,
    since: "0.1.0", related: []
  },
  {
    id: "ph-args", category: "expressions",
    name: { ru: "%args% — аргументы команды", en: "%args% — command arguments" },
    syntax: ["%args%"],
    desc: {
      ru: "Всё, что игрок написал после команды (`/hello мир` → `мир`), иначе пусто. Только в `command`. Внимание: игрок может написать что угодно.",
      en: "Everything typed after the command (`/hello world` → `world`), empty otherwise. Only inside `command`. Careful: players can type anything."
    },
    example: `command /healp permission 2:
    heal "%args%"
    reply "Вылечен: %args%"`,
    since: "0.1.0", related: ["struct-command", "target-named"]
  },
  {
    id: "ph-targeted", category: "expressions",
    name: { ru: "%targeted% — на кого смотрит игрок", en: "%targeted% — looked-at player" },
    syntax: ["%targeted%"],
    desc: {
      ru: "Имя игрока, на которого смотрит `player` (луч до `targetedPlayerRange` блоков), или `<none>`.",
      en: "Name of the player `player` is looking at (ray up to `targetedPlayerRange` blocks), or `<none>`."
    },
    example: `send "Ты смотришь на %targeted%" to player`,
    since: "0.1.2", related: ["target-targeted", "cfg-targetedPlayerRange"]
  },
  {
    id: "ph-loop-player", category: "expressions",
    name: { ru: "%loop-player% — игрок цикла", en: "%loop-player% — loop player" },
    syntax: ["%loop-player%"],
    desc: {
      ru: "Имя текущего игрока внутри `loop <цель>:` (вне цикла — `<none>`).",
      en: "Name of the current player inside `loop <target>:` (`<none>` outside a loop)."
    },
    example: `loop all players:
    send "Привет, %loop-player%!" to loop-player`,
    since: "0.1.2", related: ["struct-loop", "target-loop-player"]
  },
  {
    id: "expr-variable", category: "expressions",
    name: { ru: "Переменные {имя}", en: "Variables {name}" },
    syntax: ["{<имя>}", "{<имя>::%player%}"],
    desc: {
      ru: "Постоянные переменные, сохраняются в `scriptcraft/variables/variables.json` (общий файл). Плейсхолдеры внутри имени подставляются — `{coins::%player%}` своя у каждого. В тексте `{имя}` заменяется значением (незаданная — `<none>`).",
      en: "Persistent variables saved to `scriptcraft/variables/variables.json` (one shared file). Placeholders inside the name are expanded — `{coins::%player%}` is per player. In text `{name}` is replaced by its value (`<none>` if unset)."
    },
    example: `add 1 to {coins::%player%}
send "&eМонет: {coins::%player%}" to player`,
    since: "0.1.0", related: ["expr-temp-variable", "eff-set-var", "cfg-variablesSaveIntervalSeconds"]
  },
  {
    id: "expr-temp-variable", category: "expressions",
    name: { ru: "Временные переменные {-имя}", en: "Temporary variables {-name}" },
    syntax: ["{-<имя>}"],
    desc: {
      ru: "Имя начинается с `-`: живут только в памяти и стираются при старте/остановке сервера (если `enableTemporaryVariables = true`).",
      en: "Name starts with `-`: kept in memory only, wiped on server start/stop (when `enableTemporaryVariables = true`)."
    },
    example: `set {-online_since::%player%} to "сегодня"`,
    since: "0.1.0", related: ["expr-variable", "cfg-enableTemporaryVariables"]
  },
  {
    id: "expr-values", category: "expressions",
    name: { ru: "Значения: числа, текст, true/false", en: "Values: numbers, text, true/false" },
    syntax: ["123", "-4.5", "\"текст\"", "true", "false"],
    desc: {
      ru: "Цифры без кавычек — число, `true`/`false` — логическое значение, слово без кавычек — текст, текст с пробелами — в кавычках (`\\\"` внутри — кавычка). Запятые вне кавычек считаются пробелами.",
      en: "Unquoted digits are numbers, `true`/`false` are booleans, an unquoted word is text, text with spaces must be quoted (`\\\"` inside is a quote). Commas outside quotes count as spaces."
    },
    example: `set {level} to 5
set {motd} to "Привет всем"
set {vip::%player%} to true`,
    since: "0.1.0", related: ["eff-set-var"]
  },
  {
    id: "expr-colors", category: "expressions",
    name: { ru: "Цвета &a, &l ...", en: "Colours &a, &l ..." },
    syntax: ["&0-&9", "&a-&f", "&k-&o", "&r"],
    desc: {
      ru: "Цветовые коды в стиле Bukkit в любом тексте: `&a` зелёный, `&e` жёлтый, `&c` красный, `&l` жирный, `&r` сброс.",
      en: "Bukkit-style colour codes in any text: `&a` green, `&e` yellow, `&c` red, `&l` bold, `&r` reset."
    },
    example: `send "&a&lУспех! &r&7(серый текст)" to player`,
    since: "0.1.0", related: ["eff-send"]
  },

  // ================================================================== ЦЕЛИ
  {
    id: "target-player", category: "targets",
    name: { ru: "player — игрок из контекста", en: "player — context player" },
    syntax: ["player", "%player%"],
    desc: {
      ru: "Тот, кто вызвал команду / зашёл / написал эффект-команду. Из консоли — ошибка «действие требует игрока».",
      en: "Whoever ran the command / joined / typed the effect command. From the console it is an error (“requires a player”)."
    },
    example: `heal player`,
    since: "0.1.0", related: ["ph-player"]
  },
  {
    id: "target-named", category: "targets",
    name: { ru: "Игрок по имени", en: "Player by name" },
    syntax: ["\"<имя>\"", "{<переменная>}", "\"%args%\"", "<имя>"],
    desc: {
      ru: "Игрок онлайн с таким именем. Если его нет в сети — пустая цель (действие ничего не делает). Селекторы внутри текста не разбираются: `\"%args%\"` = `@a` не выберет всех.",
      en: "The online player with that name. If they are offline the target is empty (the action does nothing). Selectors inside text are not parsed: `\"%args%\"` = `@a` does not select everyone."
    },
    example: `send action bar "&aТебя вылечили!" to "%args%"`,
    since: "0.1.2", related: ["ph-args"]
  },
  {
    id: "target-all", category: "targets",
    name: { ru: "Все игроки", en: "All players" },
    syntax: ["all players", "@a"],
    desc: { ru: "Все игроки онлайн.", en: "Every online player." },
    example: `send title "&cРестарт через минуту" to all players`,
    since: "0.1.2", related: ["target-selector", "struct-loop"]
  },
  {
    id: "target-targeted", category: "targets",
    name: { ru: "Игрок, на которого смотрят", en: "Targeted player" },
    syntax: ["targeted player", "targeted player of <цель>", "target of <цель>"],
    desc: {
      ru: "Игрок, на которого смотрит `player` (или игроки другой цели): луч из глаз до `targetedPlayerRange` блоков, сквозь блоки не видит, наблюдатели не считаются. Никого — пустая цель.",
      en: "The player `player` (or each player of another target) is looking at: a ray from the eyes up to `targetedPlayerRange` blocks, blocked by blocks, spectators ignored. Nobody — empty target."
    },
    example: `send title "&cНа тебя смотрят" to targeted player
kill target of "Steve"`,
    since: "0.1.2", related: ["ph-targeted", "cond-target-set", "cfg-targetedPlayerRange"]
  },
  {
    id: "target-radius", category: "targets",
    name: { ru: "Игроки в радиусе", en: "Players in radius" },
    syntax: ["players in radius <N> of <цель>", "players within <N> [blocks] of <цель>", "players in radius <N> around <цель>", "other players in radius <N> of <цель>"],
    desc: {
      ru: "Игроки в том же мире не дальше N блоков от каждого игрока цели-центра. N — число или `{переменная}`. Центр входит в набор; с `other` — без самих центров.",
      en: "Players in the same world within N blocks of each centre player. N is a number or `{variable}`. The centre is included; with `other` the centres are excluded."
    },
    example: `heal players in radius 20 of player
send "&a%player% вылечил всех рядом" to other players in radius 20 of player`,
    since: "0.1.2", related: ["target-selector", "struct-loop"]
  },
  {
    id: "target-selector", category: "targets",
    name: { ru: "Ванильные селекторы", en: "Vanilla selectors" },
    syntax: ["@p", "@r", "@s", "@a[<аргументы>]", "@p[...]", "@r[...]", "@s[...]"],
    desc: {
      ru: "Разбираются самим Minecraft, от лица игрока из контекста (из консоли — от сервера) с уровнем прав 2. `@p` от игрока — он сам (как в ванилле), `@r` — случайный, `@s` — сам игрок. Аргументы как в командах: `distance=..10`, `limit=2`, `gamemode=survival`, `name=...`. `@e` не поддерживается — только игроки. Пробелов внутри `[...]` лучше не ставить.",
      en: "Parsed by Minecraft itself, as the context player (the server from the console) with permission level 2. `@p` from a player is that player (vanilla behaviour), `@r` is random, `@s` is the player. Arguments as in commands: `distance=..10`, `limit=2`, `gamemode=survival`, `name=...`. `@e` is not supported — players only. Avoid spaces inside `[...]`."
    },
    example: `give @a[distance=..10] effect regeneration for 10 seconds
give 50 xp to @r`,
    since: "0.1.2", related: ["target-all", "target-radius"]
  },
  {
    id: "target-loop-player", category: "targets",
    name: { ru: "loop-player — игрок цикла", en: "loop-player" },
    syntax: ["loop-player"],
    desc: {
      ru: "Текущий игрок внутри `loop <цель>:`. Вне цикла — пустая цель.",
      en: "The current player inside `loop <target>:`. Empty outside a loop."
    },
    example: `loop players in radius 10 of player:
    give loop-player bread 4`,
    since: "0.1.2", related: ["struct-loop", "ph-loop-player"]
  },

  // ================================================================== ЭКРАНЫ
  {
    id: "screen-text", category: "screen",
    name: { ru: "Текст на экране", en: "Screen text" },
    syntax: ["text \"<текст>\" [at <X> <Y>] [anchor <точка>] [color #RRGGBB] [shadow]"],
    desc: {
      ru: "Надпись. `color` — `#RRGGBB` или `#AARRGGBB`, `shadow` — с тенью. Плейсхолдеры и `{переменные}` подставляются при открытии.",
      en: "A label. `color` is `#RRGGBB` or `#AARRGGBB`, `shadow` adds a shadow. Placeholders and `{variables}` are filled in when opening."
    },
    example: `text "Добро пожаловать, %player%!" at 0, -60 color #FFD700 shadow`,
    since: "0.1.0", related: ["eff-open-screen", "screen-layout"]
  },
  {
    id: "screen-button", category: "screen",
    name: { ru: "Кнопка", en: "Button" },
    syntax: ["button \"<надпись>\" [at <X> <Y>] [size <W>x<H>] [anchor <точка>] run \"/<команда>\"", "button \"<надпись>\" ... close"],
    desc: {
      ru: "Кнопка (по умолчанию 100x20). `run \"/cmd\"` — игрок выполняет команду (со своими правами) и экран закрывается, `close` — просто закрыть экран (по умолчанию).",
      en: "A button (100x20 by default). `run \"/cmd\"` makes the player run the command (with their own permissions) and closes the screen, `close` just closes it (default)."
    },
    example: `button "Сказать привет" at 0, 15 size 120x20 run "/hello из меню"
button "Закрыть" at 0, 40 size 120x20 close`,
    since: "0.1.0", related: ["eff-open-screen", "screen-layout"]
  },
  {
    id: "screen-slot", category: "screen",
    name: { ru: "Слот с предметом", en: "Item slot" },
    syntax: ["slot <предмет> [<количество>] [at <X> <Y>] [anchor <точка>]"],
    desc: {
      ru: "Показывает предмет с подсказкой при наведении. Только показ — брать нельзя.",
      en: "Shows an item with a tooltip on hover. Display only — it cannot be taken."
    },
    example: `slot diamond 5 at -30, -15
slot example:ruby 4 at 60, -15`,
    since: "0.1.0", related: ["eff-open-screen"]
  },
  {
    id: "screen-image", category: "screen",
    name: { ru: "Картинка", en: "Image" },
    syntax: ["image \"<ns>:<путь>.png\" [at <X> <Y>] [size <W>x<H>] [texture <W>x<H>] [anchor <точка>] [color #AARRGGBB]"],
    desc: {
      ru: "Рисует текстуру. `texture` — реальный размер файла, `color` — оттенок. Картинки из `scriptcraft/resources/assets/<ns>/textures/gui/` сервер отправляет игрокам сам, они показываются сразу.",
      en: "Draws a texture. `texture` is the real file size, `color` tints it. Images from `scriptcraft/resources/assets/<ns>/textures/gui/` are sent to players by the server and show up immediately."
    },
    example: `image "example:textures/gui/menu_banner.png" at 0, -85 size 128x32 texture 128x32`,
    since: "0.1.0", related: ["eff-open-screen", "cmd-resources-reload"]
  },
  {
    id: "screen-layout", category: "screen",
    name: { ru: "Позиция и привязка", en: "Position and anchor" },
    syntax: ["at <X> <Y>", "anchor top_left|top|top_right|left|center|right|bottom_left|bottom|bottom_right"],
    desc: {
      ru: "Элемент выравнивается по точке привязки экрана (по умолчанию `center`) и сдвигается на X Y пикселей (минус — влево/вверх).",
      en: "The element is aligned to an anchor point of the screen (`center` by default) and shifted by X Y pixels (negative = left/up)."
    },
    example: `text "ScriptCraft" at -4, -4 anchor bottom_right color #808080`,
    since: "0.1.0", related: ["screen-text", "screen-button"]
  },

  // ================================================================== КОМАНДЫ МОДА
  {
    id: "cmd-help", category: "commands",
    name: { ru: "/sc help", en: "/sc help" },
    syntax: ["/sc", "/sc help", "/scriptcraft ...", "/script ..."],
    desc: {
      ru: "Справка. `/scriptcraft`, `/sc` и `/script` — полные синонимы. Все команды мода требуют уровень оператора 2.",
      en: "Help. `/scriptcraft`, `/sc` and `/script` are full aliases. All mod commands need operator level 2."
    },
    example: `/sc help`,
    since: "0.1.0", related: ["cmd-reload"]
  },
  {
    id: "cmd-reload", category: "commands",
    name: { ru: "/sc reload", en: "/sc reload" },
    syntax: ["/sc reload", "/sc reload <скрипт>"],
    desc: {
      ru: "Перезагрузить все скрипты или один (`welcome`, `sub/name`, `.sk` не обязателен). Ошибки выводятся как `файл:строка: текст`. Новые `define` — только после перезапуска.",
      en: "Reload all scripts or one (`welcome`, `sub/name`, `.sk` optional). Errors are shown as `file:line: text`. New `define`s need a restart."
    },
    example: `/sc reload
/sc reload welcome`,
    since: "0.1.0", related: ["cmd-list", "cmd-disable"]
  },
  {
    id: "cmd-disable", category: "commands",
    name: { ru: "/sc enable | disable", en: "/sc enable | disable" },
    syntax: ["/sc disable <скрипт>", "/sc enable <скрипт>"],
    desc: {
      ru: "`disable` выгружает скрипт и переименовывает файл в `-имя.sk`; `enable` возвращает имя и загружает. Команды выключенного скрипта остаются в списке до перезапуска и отвечают «выключена».",
      en: "`disable` unloads the script and renames the file to `-name.sk`; `enable` renames it back and loads it. Commands of a disabled script stay registered until restart and reply “disabled”."
    },
    example: `/sc disable welcome
/sc enable welcome`,
    since: "0.1.0", related: ["cmd-reload"]
  },
  {
    id: "cmd-list", category: "commands",
    name: { ru: "/sc list", en: "/sc list" },
    syntax: ["/sc list"],
    desc: {
      ru: "Загруженные и выключенные скрипты, команды, число переменных и ошибок.",
      en: "Loaded and disabled scripts, commands, variable and error counts."
    },
    example: `/sc list`,
    since: "0.1.0", related: ["cmd-reload"]
  },
  {
    id: "cmd-resources-reload", category: "commands",
    name: { ru: "/sc resources reload", en: "/sc resources reload" },
    syntax: ["/sc resources reload"],
    desc: {
      ru: "Перечитать `scriptcraft/resources/assets` и разослать изменения игрокам онлайн (качаются только изменённые файлы). `data/` перечитывает ванильная `/reload`.",
      en: "Rescan `scriptcraft/resources/assets` and send changes to online players (only changed files are downloaded). `data/` is reloaded by vanilla `/reload`."
    },
    example: `/sc resources reload`,
    since: "0.1.2", related: ["cfg-syncResourcesToClients", "screen-image"]
  },
  {
    id: "cmd-effect-commands", category: "commands",
    name: { ru: "Эффект-команды в чате ?...", en: "Chat effect commands ?..." },
    syntax: ["?<строка скрипта>"],
    desc: {
      ru: "Сообщение в чате, начинающееся с токена (по умолчанию `?`), выполняется как одна строка скрипта от имени написавшего. Выключено по умолчанию (`enableEffectCommands`). Право `scriptcraft.effectcommands` (PermissionAPI, по умолчанию — операторы). Блоки (`if`, `loop`, `open screen`) не поддерживаются; пустая цель — сообщение «Нет целей».",
      en: "A chat message starting with the token (`?` by default) runs as one script line on behalf of the sender. Off by default (`enableEffectCommands`). Permission `scriptcraft.effectcommands` (PermissionAPI, operators by default). Blocks (`if`, `loop`, `open screen`) are not supported; an empty target replies “No targets”."
    },
    example: `?heal player
?send "привет" to targeted player
?give @a[distance=..20] diamond 1`,
    since: "0.1.2", related: ["cfg-enableEffectCommands", "cfg-effectCommandToken", "cfg-effectCommandsRequireOp"]
  },

  // ================================================================== КОНФИГ
  { id: "cfg-language", category: "config", name: { ru: "general.language", en: "general.language" },
    syntax: ["language = \"en\" | \"ru\""],
    desc: { ru: "Язык сообщений мода. По умолчанию `\"en\"`.", en: "Language of mod messages. Default `\"en\"`." },
    example: `language = "ru"`, since: "0.1.0", related: [] },
  { id: "cfg-enableTemporaryVariables", category: "config", name: { ru: "general.enableTemporaryVariables", en: "general.enableTemporaryVariables" },
    syntax: ["enableTemporaryVariables = true"],
    desc: { ru: "Переменные `{-имя}` живут только в памяти. При `false` сохраняются, как обычные. По умолчанию `true`.", en: "`{-name}` variables live in memory only. With `false` they are saved like normal ones. Default `true`." },
    example: `enableTemporaryVariables = true`, since: "0.1.0", related: ["expr-temp-variable"] },
  { id: "cfg-variablesSaveIntervalSeconds", category: "config", name: { ru: "general.variablesSaveIntervalSeconds", en: "general.variablesSaveIntervalSeconds" },
    syntax: ["variablesSaveIntervalSeconds = 300"],
    desc: { ru: "Как часто автосохранять переменные, 10–86400 секунд (и при остановке сервера). По умолчанию 300.", en: "How often variables are autosaved, 10–86400 s (and on server stop). Default 300." },
    example: `variablesSaveIntervalSeconds = 300`, since: "0.1.0", related: ["expr-variable"] },
  { id: "cfg-variableBackups", category: "config", name: { ru: "general.variableBackups", en: "general.variableBackups" },
    syntax: ["variableBackups = 5"],
    desc: { ru: "Сколько резервных копий `variables-ГГГГММДД-ЧЧММСС.json` хранить (делаются при старте сервера), 0–100, 0 — не делать.", en: "How many `variables-YYYYMMDD-HHMMSS.json` backups to keep (made at server start), 0–100, 0 = none." },
    example: `variableBackups = 5`, since: "0.1.2", related: ["expr-variable"] },
  { id: "cfg-maxScriptSteps", category: "config", name: { ru: "general.maxScriptSteps", en: "general.maxScriptSteps" },
    syntax: ["maxScriptSteps = 10000"],
    desc: { ru: "Максимум действий за один запуск скрипта (100–10 000 000). Защита от зависаний.", en: "Max actions per script run (100–10,000,000). Protects against hangs." },
    example: `maxScriptSteps = 10000`, since: "0.1.0", related: ["struct-loop"] },
  { id: "cfg-maxScriptMillis", category: "config", name: { ru: "general.maxScriptMillis", en: "general.maxScriptMillis" },
    syntax: ["maxScriptMillis = 50"],
    desc: { ru: "Максимум миллисекунд на один запуск скрипта (1–10000).", en: "Max milliseconds per script run (1–10000)." },
    example: `maxScriptMillis = 50`, since: "0.1.0", related: ["cfg-maxScriptSteps"] },
  { id: "cfg-logScriptLoadTimes", category: "config", name: { ru: "general.logScriptLoadTimes", en: "general.logScriptLoadTimes" },
    syntax: ["logScriptLoadTimes = false"],
    desc: { ru: "Писать в лог время загрузки скриптов.", en: "Log how long script loading takes." },
    example: `logScriptLoadTimes = true`, since: "0.1.0", related: [] },
  { id: "cfg-allowCommandEffects", category: "config", name: { ru: "general.allowCommandEffects", en: "general.allowCommandEffects" },
    syntax: ["allowCommandEffects = true"],
    desc: { ru: "Разрешить `execute console command` и `make ... execute command`. По умолчанию `true`.", en: "Allow `execute console command` and `make ... execute command`. Default `true`." },
    example: `allowCommandEffects = false`, since: "0.1.2", related: ["eff-console-command", "eff-player-command"] },
  { id: "cfg-targetedPlayerRange", category: "config", name: { ru: "general.targetedPlayerRange", en: "general.targetedPlayerRange" },
    syntax: ["targetedPlayerRange = 50"],
    desc: { ru: "Дальность `targeted player` / `%targeted%`, 1–256 блоков. По умолчанию 50.", en: "Range of `targeted player` / `%targeted%`, 1–256 blocks. Default 50." },
    example: `targetedPlayerRange = 50`, since: "0.1.2", related: ["target-targeted", "ph-targeted"] },
  { id: "cfg-syncResourcesToClients", category: "config", name: { ru: "resources.syncResourcesToClients", en: "resources.syncResourcesToClients" },
    syntax: ["syncResourcesToClients = true"],
    desc: { ru: "Отправлять `scriptcraft/resources/assets` игрокам через сетевой канал мода (без ресурспака).", en: "Send `scriptcraft/resources/assets` to players through the mod's network channel (no resource pack prompt)." },
    example: `syncResourcesToClients = true`, since: "0.1.2", related: ["cmd-resources-reload"] },
  { id: "cfg-maxResourceSizeMb", category: "config", name: { ru: "resources.maxResourceSizeMb", en: "resources.maxResourceSizeMb" },
    syntax: ["maxResourceSizeMb = 32"],
    desc: { ru: "Максимальный общий размер ресурсов, 1–512 МБ. Проверяют и сервер, и клиент.", en: "Max total resource size, 1–512 MB. Checked by both server and client." },
    example: `maxResourceSizeMb = 32`, since: "0.1.2", related: ["cfg-syncResourcesToClients"] },
  { id: "cfg-resourceChunkSizeKb", category: "config", name: { ru: "resources.resourceChunkSizeKb", en: "resources.resourceChunkSizeKb" },
    syntax: ["resourceChunkSizeKb = 256"],
    desc: { ru: "Размер одного сетевого куска, 16–900 КБ.", en: "Size of one network chunk, 16–900 KB." },
    example: `resourceChunkSizeKb = 256`, since: "0.1.2", related: ["cfg-syncResourcesToClients"] },
  { id: "cfg-enableEffectCommands", category: "config", name: { ru: "effectCommands.enableEffectCommands", en: "effectCommands.enableEffectCommands" },
    syntax: ["enableEffectCommands = false"],
    desc: { ru: "Включить эффект-команды в чате (`?heal player`). По умолчанию выключено.", en: "Enable chat effect commands (`?heal player`). Off by default." },
    example: `enableEffectCommands = true`, since: "0.1.2", related: ["cmd-effect-commands"] },
  { id: "cfg-effectCommandToken", category: "config", name: { ru: "effectCommands.effectCommandToken", en: "effectCommands.effectCommandToken" },
    syntax: ["effectCommandToken = \"?\""],
    desc: { ru: "С чего начинается эффект-команда: до 8 символов, не `/`.", en: "Prefix of effect commands: up to 8 characters, not `/`." },
    example: `effectCommandToken = "!"`, since: "0.1.2", related: ["cmd-effect-commands"] },
  { id: "cfg-effectCommandsRequireOp", category: "config", name: { ru: "effectCommands.effectCommandsRequireOp", en: "effectCommands.effectCommandsRequireOp" },
    syntax: ["effectCommandsRequireOp = true"],
    desc: { ru: "Без мода прав эффект-команды только у операторов (уровень 2). `false` — у всех игроков (опасно).", en: "Without a permissions mod, effect commands are for operators (level 2) only. `false` = every player (dangerous)." },
    example: `effectCommandsRequireOp = true`, since: "0.1.2", related: ["cmd-effect-commands"] }
  ]
};
