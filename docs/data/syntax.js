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
  version: "0.6.1",
  categories: [
    { id: "events",     ru: "События",            en: "Events" },
    { id: "structures", ru: "Структуры",          en: "Structures" },
    { id: "effects",    ru: "Эффекты",            en: "Effects" },
    { id: "conditions", ru: "Условия",            en: "Conditions" },
    { id: "expressions",ru: "Выражения и плейсхолдеры", en: "Expressions & placeholders" },
    { id: "targets",    ru: "Цели (селекторы)",   en: "Targets (selectors)" },
    { id: "screen",     ru: "Экраны (GUI)",       en: "Screens (GUI)" },
    { id: "hud",        ru: "UI / HUD",           en: "UI / HUD" },
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
    since: "0.1.0", related: ["event-quit", "event-load", "ph-player", "eff-send", "eff-broadcast"]
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
    since: "0.1.0", related: ["event-join", "event-unload"]
  },
  {
    id: "event-load", category: "events",
    name: { ru: "Загрузка скрипта", en: "Script load" },
    syntax: ["on load:", "on script load:"],
    desc: {
      ru: "Один раз, когда этот скрипт загружен: старт сервера, `/sc reload` и `/sc enable`. Игрока нет — `all players` или `broadcast`. Несколько событий в одной строке пишутся через `or`: `on join or load:` выполнится и при входе, и при загрузке для каждого, кто уже онлайн.",
      en: "Once, when this script is loaded: server start, `/sc reload` and `/sc enable`. There is no player — use `all players` or `broadcast`. Several events on one line are joined with `or`: `on join or load:` runs on join and, on load, for everyone already online."
    },
    example: `on load:
	broadcast "&7Скрипт событий загружен"

on join or load:
	create hud "coins" for player:
		text "%{coins::%player%}%" at 4, 4 anchor top_left`,
    since: "0.1.5", related: ["event-unload", "event-join", "hud-create", "cmd-disable"]
  },
  {
    id: "event-unload", category: "events",
    name: { ru: "Выгрузка скрипта", en: "Script unload" },
    syntax: ["on unload:", "on script unload:"],
    desc: {
      ru: "Перед выгрузкой: `/sc disable`, перед `/sc reload` и при остановке сервера. Игрока нет. HUD этого скрипта при `/sc disable` убирается сам. Через `or` можно добавить выход: `on quit or unload:`.",
      en: "Before unload: `/sc disable`, before `/sc reload` and on server stop. There is no player. This script's HUD is removed on `/sc disable`. `or` can add quit: `on quit or unload:`."
    },
    example: `on unload:
	broadcast "&7Скрипт событий выключен"`,
    since: "0.1.5", related: ["event-load", "event-quit", "cmd-disable"]
  },
  {
    id: "event-death", category: "events",
    name: { ru: "Смерть", en: "Death" },
    syntax: ["on death:", "on player death:"],
    desc: {
      ru: "Игрок умер. `%attacker%` — кто ударил (или `<none>`), `%cause%` — причина (`player`, `fall`, `lava`...). `cancel event` отменяет смерть.",
      en: "The player died. `%attacker%` is who hit them (or `<none>`), `%cause%` is the cause (`player`, `fall`, `lava`...). `cancel event` prevents the death."
    },
    example: `on death:
	broadcast "&c%player% погиб (%cause%)"
	if %attacker% is not "<none>":
		send "&7Тебя убил %attacker%" to player`,
    since: "0.2.0", related: ["event-respawn", "eff-cancel", "event-damage", "ph-event"]
  },
  {
    id: "event-respawn", category: "events",
    name: { ru: "Возрождение", en: "Respawn" },
    syntax: ["on respawn:", "on player respawn:"],
    desc: {
      ru: "Игрок появился снова после смерти. `cancel event` это событие не отменяет.",
      en: "The player is back after dying. `cancel event` does not cancel this event."
    },
    example: `on respawn:
	heal player
	feed player`,
    since: "0.2.0", related: ["event-death", "eff-heal", "eff-feed"]
  },
  {
    id: "event-chat", category: "events",
    name: { ru: "Чат", en: "Chat" },
    syntax: ["on chat:", "on player chat:"],
    desc: {
      ru: "Игрок написал в чат. Текст — `%message%`. `cancel event` не даёт сообщению уйти игрокам.",
      en: "A player sent a chat message. The text is `%message%`. `cancel event` stops it from being sent."
    },
    example: `on chat:
	if %message% is "ping":
		cancel event
		send "&aПонг!" to player`,
    since: "0.2.0", related: ["eff-cancel", "eff-send", "ph-event"]
  },
  {
    id: "event-break", category: "events",
    name: { ru: "Сломать блок", en: "Block break" },
    syntax: ["on break:", "on block break:", "on mine:", "on break block:"],
    desc: {
      ru: "Игрок сломал блок. `%block%` — id (`minecraft:stone`), `%x%` `%y%` `%z%` — координаты. `cancel event` не даёт сломать.",
      en: "The player broke a block. `%block%` is the id (`minecraft:stone`), `%x%` `%y%` `%z%` are the coordinates. `cancel event` stops the break."
    },
    example: `on break:
	if %block% is "minecraft:diamond_ore":
		send "&bТы нашёл алмазы на %x% %y% %z%" to player`,
    since: "0.2.0", related: ["event-place", "eff-cancel", "ph-event"]
  },
  {
    id: "event-place", category: "events",
    name: { ru: "Поставить блок", en: "Block place" },
    syntax: ["on place:", "on block place:", "on place block:"],
    desc: {
      ru: "Игрок поставил блок. `%block%` — id поставленного блока, `%x%` `%y%` `%z%` — координаты. `cancel event` не даёт поставить.",
      en: "The player placed a block. `%block%` is the placed block's id, `%x%` `%y%` `%z%` are the coordinates. `cancel event` stops the placement."
    },
    example: `on place:
	if %block% is "example:ruby_block":
		play sound block.amethyst_block.place to player`,
    since: "0.2.0", related: ["event-break", "eff-cancel", "ph-event", "eff-sound"]
  },
  {
    id: "event-right-click", category: "events",
    name: { ru: "ПКМ по блоку", en: "Right click block" },
    syntax: ["on right click:", "on rightclick:", "on right click block:"],
    desc: {
      ru: "ПКМ по блоку основной рукой. `%block%` — id блока, `%x%` `%y%` `%z%` — координаты. `cancel event` отменяет клик. Клик по воздуху на выделенном сервере не приходит.",
      en: "Right click on a block with the main hand. `%block%` is the block id, `%x%` `%y%` `%z%` are the coordinates. `cancel event` stops the click. Clicking air is not delivered on a dedicated server."
    },
    example: `on right click:
	if %block% is "minecraft:chest":
		cancel event
		send "&eСундук закрыт" to player`,
    since: "0.2.0", related: ["event-left-click", "eff-cancel", "ph-event"]
  },
  {
    id: "event-left-click", category: "events",
    name: { ru: "ЛКМ по блоку", en: "Left click block" },
    syntax: ["on left click:", "on leftclick:", "on left click block:"],
    desc: {
      ru: "ЛКМ по блоку основной рукой. `%block%` — id блока, `%x%` `%y%` `%z%` — координаты. `cancel event` отменяет клик. Клик по воздуху на выделенном сервере не приходит.",
      en: "Left click on a block with the main hand. `%block%` is the block id, `%x%` `%y%` `%z%` are the coordinates. `cancel event` stops the click. Clicking air is not delivered on a dedicated server."
    },
    example: `on left click:
	send "&7ЛКМ по %block% на %x% %y% %z%" to player`,
    since: "0.2.0", related: ["event-right-click", "event-break", "eff-cancel", "ph-event"]
  },
  {
    id: "event-drop", category: "events",
    name: { ru: "Выбросить предмет", en: "Drop item" },
    syntax: ["on drop:", "on drop item:", "on item drop:"],
    desc: {
      ru: "Игрок выбросил предмет (Q). `%item%` — id предмета. `cancel event` отменяет выброс, но предмет уже снят с инвентаря: отмена не возвращает его в руку.",
      en: "The player tossed an item (Q). `%item%` is the item id. `cancel event` cancels the toss, but the item is already out of the inventory: cancelling does not put it back."
    },
    example: `on drop:
	if %item% is "minecraft:diamond":
		cancel event
		send "&cАлмазы выбрасывать нельзя" to player`,
    since: "0.2.0", related: ["event-pickup", "eff-cancel", "eff-drop", "ph-event"]
  },
  {
    id: "event-pickup", category: "events",
    name: { ru: "Подобрать предмет", en: "Pick up item" },
    syntax: ["on pickup:", "on pick up:", "on item pickup:"],
    desc: {
      ru: "Игрок поднял предмет с земли, ещё до инвентаря. `%item%` — id предмета. `cancel event` не даёт ему попасть в инвентарь. `on receive` при этом срабатывает отдельно, уже когда число в инвентаре выросло.",
      en: "The player picked an item up off the ground, before it enters the inventory. `%item%` is the item id. `cancel event` keeps it out of the inventory. `on receive` still fires on its own, once the inventory count has grown."
    },
    example: `on pickup:
	send "&7Поднято: %item%" to player`,
    since: "0.2.0", related: ["event-drop", "event-receive", "eff-cancel", "ph-event"]
  },
  {
    id: "event-receive", category: "events",
    name: { ru: "Получение предмета", en: "Item received" },
    syntax: ["on receive:"],
    desc: {
      ru: "В инвентаре стало больше этого предмета: подбор с земли, `give`, сундук, крафт, торговля, креатив. `%item%` — id, `%amount%` — на сколько выросло число. Перекладывание из слота в слот сумму не меняет и событие не поднимает. Первый снимок при входе молчит, вещи с диска не считаются добычей. `cancel event` забирает эту добавку обратно. Предмет уже лежит в инвентаре, когда событие бежит. Курсор и эндер-сундук не считаются. `on pickup` при подборе с земли срабатывает отдельно, ещё до инвентаря. Предметы, которые выдал сам обработчик `on receive`, вторым событием не приходят.",
      en: "The inventory total of an item grew: a ground pickup, `give`, a chest, crafting, trading, creative. `%item%` is the id, `%amount%` is how many were added. Moving a stack between slots does not change the total, so it does not fire. The first snapshot on join is silent, so items loaded from disk do not count. `cancel event` removes that added amount. The item is already in the inventory when the event runs. The cursor and the ender chest are not counted. `on pickup` still fires on its own for a ground item, before it enters the inventory. Items the `on receive` handler itself gives do not fire a second time."
    },
    example: `on receive:
	send "&7Получено: &f%item% &7x%amount%" to player
	if %item% is "minecraft:diamond":
		cancel event
		send "&cАлмазы в этот инвентарь не кладутся" to player`,
    since: "0.3.7", related: ["event-pickup", "eff-cancel", "eff-give-item", "ph-event"]
  },
  {
    id: "event-key", category: "events",
    name: { ru: "Клавиша (нажатие / отпускание / удержание)", en: "Key press / release / hold" },
    syntax: [
      "on key <клавиша>:",
      "on key <клавиша> in screen [\"<заголовок>\"]:",
      "on key release <клавиша> [in screen [\"<заголовок>\"]]:",
      "on key hold <клавиша> [in screen [\"<заголовок>\"]]:",
      "on any key [in screen [\"<заголовок>\"]]:"
    ],
    desc: {
      ru: "Обработка физических клавиш (независимо от раскладки клавиатуры RU/EN): `a-z`, `0-9`, `f1-f12`, `space`, `escape`, `tab`, `enter`, `backspace`, `delete`, стрелки (`up`, `down`, `left`, `right`), `lshift`, `rshift`, `lctrl`, `rctrl`, `lalt`, `ralt`, `grave`, либо `any` (любая клавиша). Работает как в игре, так и при открытом кастомном экране (`open screen`). Добавка `in screen` (или `in screen \"Змейка\"`) фильтрует событие только когда открыт кастомный экран. `on key release` срабатывает при отпускании, `on key hold` — каждый тик (20 раз/сек), пока клавиша зажата. В открытом GUI нажатия поглощаются и не передаются стандартному управлению Minecraft (а `cancel event` в `on key escape in screen:` запрещает закрытие экрана по Esc). Плейсхолдеры: `%key%` — клавиша, `%screen%` — заголовок открытого экрана (или `none`).",
      en: "Physical key handling (independent of OS keyboard layout): `a-z`, `0-9`, `f1-f12`, `space`, `escape`, `tab`, `enter`, `backspace`, `delete`, arrows (`up`, `down`, `left`, `right`), `lshift`, `rshift`, `lctrl`, `rctrl`, `lalt`, `ralt`, `grave`, or `any`. Works both in normal gameplay and while a custom `open screen` GUI is open. Adding `in screen` (or `in screen \"Snake\"`) filters the event to when that screen is open. `on key release` fires when released, `on key hold` fires every tick (20 TPS) while held down. Inside an open GUI, keys are consumed so vanilla binds don't trigger (and `cancel event` inside `on key escape in screen:` prevents closing the screen with Esc). Placeholders: `%key%` and `%screen%`."
    },
    example: `on key w in screen "Змейка":
	MoveSnake(player, "up")

on key hold d in screen "Змейка":
	StepRight(player)`,
    since: "0.3.7", related: ["event-open", "event-screen-open", "screen-button", "ph-event"]
  },
  {
    id: "event-screen-open", category: "events",
    name: { ru: "Открытие и закрытие экрана скрипта", en: "Custom screen open & close" },
    syntax: [
      "on screen open [\"<заголовок>\"]:",
      "on screen close [\"<заголовок>\"]:"
    ],
    desc: {
      ru: "Срабатывает, когда у игрока открывается (`on screen open:`) или закрывается (`on screen close:`) кастомный GUI-экран (`open screen`). Можно указать конкретный заголовок в кавычках. При закрытии экрана (по Esc, кнопке `close`, эффекту `close screen` или выходу игрока) автоматически очищаются все зажатые клавиши и колбэки этого экрана. Плейсхолдер `%screen%` — заголовок экрана.",
      en: "Fires when a custom `open screen` GUI opens (`on screen open:`) or closes (`on screen close:`) for a player. You may filter by screen title in quotes. When the screen closes (via Esc, a `close` button, `close screen` or disconnect), all held keys and button callbacks for that screen are automatically cleaned up. `%screen%` is the screen title."
    },
    example: `on screen close "Змейка":
	delete {snake::%player%::*}
	send "&7Игра окончена!" to player`,
    since: "0.6.2", related: ["eff-open-screen", "event-key"]
  },
  {
    id: "event-open", category: "events",
    name: { ru: "Открытие меню", en: "Menu open" },
    syntax: ["on open <меню>:", "on open menu <меню>:", "on open any:"],
    desc: {
      ru: "Открывается ванильный экран. `%menu%` — его имя. Имена: `inventory` (и выживание, и креатив, то есть E), `chest` (в том числе эндер-сундук и большой сундук), `shulker`, `barrel`, `crafting`, `furnace` (печь, плавильня, коптильня), `anvil`, `enchanting`, `brewing`, `villager`, `hopper`, `horse`, `beacon`, `smithing`, `grindstone`, `loom`, `cartography`, `stonecutter`, `any`. `cancel event` где угодно в теле, даже внутри `if`, блокирует экран всегда: клиент закрывает его сразу и не ждёт сервер. Без `cancel event` меню открывается, а скрипт только узнаёт об этом. Экран самого скрипта (`open screen`) под `any` не попадает.",
      en: "A vanilla screen is opening. `%menu%` is its name. Names: `inventory` (survival and creative, so E), `chest` (including the ender chest and a large chest), `shulker`, `barrel`, `crafting`, `furnace` (furnace, blast furnace, smoker), `anvil`, `enchanting`, `brewing`, `villager`, `hopper`, `horse`, `beacon`, `smithing`, `grindstone`, `loom`, `cartography`, `stonecutter`, `any`. A `cancel event` anywhere in the body, even inside `if`, always blocks the screen: the client closes it immediately and does not wait for the server. Without `cancel event` the menu still opens and the script only observes it. The script's own screen (`open screen`) is not matched by `any`."
    },
    example: `on open inventory:
	cancel event
	open screen "Сумка" to player:
		text "Вместо инвентаря" at 0, 0`,
    since: "0.3.7", related: ["event-key", "eff-cancel", "eff-open-screen", "screen-list"]
  },
  {
    id: "event-damage", category: "events",
    name: { ru: "Урон", en: "Damage" },
    syntax: ["on damage:", "on hurt:"],
    desc: {
      ru: "Игрок получил урон. `%damage%` — сколько, `%attacker%` — источник, `%cause%` — тип. `cancel event` убирает этот урон. Срабатывает часто (яд, огонь, удары) — не ставьте сюда тяжёлые действия.",
      en: "The player took damage. `%damage%` is the amount, `%attacker%` who dealt it, `%cause%` the type. `cancel event` negates this hit. It fires often (poison, fire, hits) — keep the body light."
    },
    example: `on damage:
	if player is in creative:
		stop
	if %damage% >= 10:
		send "&cСильный удар: %damage%" to player`,
    since: "0.2.0", related: ["eff-cancel", "eff-stop", "cond-gamemode"]
  },
  {
    id: "event-first-join", category: "events",
    name: { ru: "Первый вход", en: "First join" },
    syntax: ["on first join:", "on firstjoin:"],
    desc: {
      ru: "Игрок ещё ни разу не выходил. Срабатывает вместе с `on join:`, не вместо него.",
      en: "The player has never logged out before. It fires together with `on join:`, not instead of it."
    },
    example: `on first join:
	give player bread 16
	send "&aДобро пожаловать, %player%!" to player`,
    since: "0.2.0", related: ["event-join", "eff-give-item"]
  },
  {
    id: "event-world-change", category: "events",
    name: { ru: "Смена мира", en: "World change" },
    syntax: ["on world change:", "on change world:", "on dimension change:", "on worldchange:"],
    desc: {
      ru: "Игрок сменил измерение. `%from%` — старый мир, `%world%` — новый. `cancel event` это событие не отменяет.",
      en: "The player changed dimension. `%from%` is the old world, `%world%` is the new one. `cancel event` does not cancel this event."
    },
    example: `on world change:
	send "&7%from% → %world%" to player`,
    since: "0.2.0", related: ["ph-event", "eff-teleport"]
  },
  {
    id: "event-gamemode", category: "events",
    name: { ru: "Смена режима", en: "Game mode change" },
    syntax: ["on gamemode change:", "on game mode change:", "on gamemode:"],
    desc: {
      ru: "Игрок меняет режим. `%from%` — старый, `%gamemode%` — новый (`survival`, `creative`, `adventure`, `spectator`). `cancel event` оставляет старый режим.",
      en: "The player is changing game mode. `%from%` is the old one, `%gamemode%` is the new one (`survival`, `creative`, `adventure`, `spectator`). `cancel event` keeps the old mode."
    },
    example: `on gamemode change:
	send "&eТеперь ты в %gamemode%" to player`,
    since: "0.2.0", related: ["eff-cancel", "eff-gamemode", "cond-gamemode", "ph-event"]
  },

  // ================================================================== СТРУКТУРЫ
  {
    id: "struct-command", category: "structures",
    name: { ru: "Своя команда", en: "Custom command" },
    syntax: ["command /<имя>:", "command /<имя> <слово> <тип>:", "command /<имя> <тип> [тип]:", "permission: <0-4>", "permission: <узел>", "aliases: <имя>, <имя>", "arguments: <тип>, [тип]", "executable by: players", "executable by: console", "executable by: players and console"],
    desc: {
      ru: "Объявляет команду. Слово без скобок — часть пути и не считается аргументом: `command /bag take <text>:` это `/bag take <текст>`, а `%arg-1%` — текст. <тип> обязательный, [тип] можно не писать. Типы: text, player, offline player, integer, number, boolean, date, world, item. Без списка аргументов хвост строки — %args%. `/sc reload` заново собирает аргументы своих команд, перезапуск сервера для этого не нужен. `permission: 2` — уровень оператора. `permission: scriptcraft.bag` — узел LuckPerms, мод или плагин; та же проверка, что у `has permission`. Узел попадает в редактор LuckPerms. Без LuckPerms узел есть только у оператора. Консоль узлом не ограничивается.",
      en: "Declares a command. A bare word is part of the path and is not an argument: `command /bag take <text>:` is `/bag take <text>`, and `%arg-1%` is the text. <type> is required, [type] may be omitted. Types: text, player, offline player, integer, number, boolean, date, world, item. With no argument list the rest of the line is %args%. `/sc reload` rebuilds the arguments of script commands; a server restart is not required for that. `permission: 2` is the operator level. `permission: scriptcraft.bag` is a LuckPerms node, mod or plugin; the same check as `has permission`. The node is listed in the LuckPerms editor. Without LuckPerms only operators have the node. The console is not limited by a node."
    },
    example: `command /hello:
	send "&bПривет из %script%, %player%! Ты написал: %args%" to player

command /heal <player> [integer]:
	permission: 2
	aliases: h, healme
	heal arg-1 by arg-2`,
    since: "0.1.0", related: ["ph-args", "cmd-reload", "struct-options", "struct-function"]
  },
  {
    id: "struct-function", category: "structures",
    name: { ru: "Функция", en: "Function" },
    syntax: ["function <имя>(<параметр>: <тип>):", "<имя>(<аргумент>, <аргумент>)"],
    desc: {
      ru: "Своя команда внутри скрипта. Заголовок можно писать без двоеточия, тело — с отступом. Типы: player, offline player, entity, block, location, text, integer, number, boolean, world, item. Вызов — отдельная строка, аргументы любые выражения: `player`, `targeted player`, `targeted entity`, `targeted block`, `location of targeted block`, `{переменная}`. Параметр player — это цель (`send \"hi\" to who`, `health of who`, `%who%`). entity и block получают id, location — точку. Имя функции ищется без учёта регистра. `stop` обрывает только эту функцию.",
      en: "A command of your own inside the script. The header may omit the colon; the body is indented. Types: player, offline player, entity, block, location, text, integer, number, boolean, world, item. A call is its own line. Arguments are any expression: `player`, `targeted player`, `targeted entity`, `targeted block`, `location of targeted block`, `{variable}`. A player parameter is a target (`send \"hi\" to who`, `health of who`, `%who%`). entity and block receive an id, location receives a point. The name is case-insensitive. `stop` ends only this function."
    },
    example: `function Test(who: player):
	send "&aПривет, %who%!" to who

function Mark(spot: location, id: block):
	set block at spot to id

on right click:
	Test(targeted player)
	Mark(location of targeted block, glass)`,
    since: "0.4.4", related: ["eff-send", "eff-set-block", "expr-targeted-player", "expr-targeted-entity", "expr-location"]
  },
  {
    id: "struct-options", category: "structures",
    name: { ru: "Заготовки options", en: "Options" },
    syntax: ["options:", "<имя>: <значение>", "{@<имя>}"],
    desc: {
      ru: "Заготовки файла, как в Skript. Имя — буквы, цифры, _ и -. Значение подставляется вместо `{@имя}` до разбора строки, в том числе внутри кавычек и в других заготовках. Неизвестное `{@имя}` — ошибка в журнале.",
      en: "Per-file templates, as in Skript. A name is letters, digits, _ and -. The value replaces `{@name}` before the line is parsed, including inside quotes and inside other options. An unknown `{@name}` is a journal error."
    },
    example: `options:
	prefix: &7[&6Сервер&7]
	price: 10

on join:
	send "{@prefix} &fПривет" to player

command /shop:
	give player diamond {@price}`,
    since: "0.4.1", related: ["eff-send", "struct-command"]
  },
  {
    id: "struct-if", category: "structures",
    name: { ru: "Условие if / else", en: "if / else" },
    syntax: ["if <условие>:", "else if <условие>:", "else:"],
    desc: {
      ru: "Выполняет вложенные действия, если условие верно. `else:` и `else if` пишутся на том же отступе сразу после блока. Можно вкладывать. Несколько проверок в одной строке — через `and` и `or` (`and` связывается теснее).",
      en: "Runs the nested actions when the condition is true. `else:` and `else if` go at the same indent right after the block. Can be nested. Several checks in one line use `and` and `or` (`and` binds tighter)."
    },
    example: `command /rich:
	if {coins::%player%} >= 10:
		send "&aТы богат!" to player
	else:
		send "&cНужно ещё монет" to player`,
    since: "0.1.0", related: ["struct-every", "cond-compare", "cond-and", "cond-player"]
  },
  {
    id: "struct-every", category: "structures",
    name: { ru: "Таймер every", en: "every timer" },
    syntax: ["every <число> seconds:", "every <число> minutes:", "every <число> ticks:", "every second:", "every minute:", "every tick:"],
    desc: {
      ru: "Повторяет тело по таймеру сервера (20 тиков = 1 секунда). Игрока в контексте нет — пишите `all players` или `broadcast`. Период — число в заголовке (не переменная), от 1 тика до часа. `every tick:` срабатывает каждый тик: держите тело коротким.",
      en: "Repeats the body on the server timer (20 ticks = 1 second). There is no player — use `all players` or `broadcast`. The period is a number in the header (not a variable), from 1 tick up to an hour. `every tick:` runs every tick: keep the body short."
    },
    example: `every 30 seconds:
	loop all players:
		set value of bar "hp" for loop-player to health of loop-player`,
    since: "0.2.0", related: ["struct-wait", "struct-loop", "expr-property"]
  },
  {
    id: "struct-wait", category: "structures",
    name: { ru: "Пауза wait", en: "wait" },
    syntax: ["wait <число> seconds", "wait <число> ticks", "wait <число> minutes"],
    desc: {
      ru: "Без двоеточия. Строки **ниже на том же отступе** выполнятся после паузы; код снаружи блока не ждёт. Если игрок вышел — хвост не выполняется. Дольше 5 минут пауза обрезается. `loop-player` после паузы не сохраняется. Число может быть `{переменной}` или выражением.",
      en: "No colon. Lines **below at the same indent** run after the pause; code outside the block does not wait. If the player leaves, the rest is skipped. Waits longer than 5 minutes are cut to 5 minutes. `loop-player` is not kept across the pause. The number may be a `{variable}` or an expression."
    },
    example: `command /later:
	send "&eСейчас" to player
	wait 3 seconds
	send "&aПрошло 3 секунды" to player`,
    since: "0.2.0", related: ["struct-every", "eff-send"]
  },
  {
    id: "struct-chance", category: "structures",
    name: { ru: "Шанс", en: "Chance" },
    syntax: ["chance of <число>%:"],
    desc: {
      ru: "Вложенные действия выполняются с шансом 0–100. Знак `%` можно не писать. Число — литерал, `{переменная}` или выражение.",
      en: "The nested actions run with a 0–100 chance. The `%` sign is optional. The number is a literal, a `{variable}` or an expression."
    },
    example: `on break:
	chance of 25%:
		give player emerald 1
		send "&aРедкая находка!" to player`,
    since: "0.2.0", related: ["event-break", "expr-random"]
  },
  {
    id: "struct-loop", category: "structures",
    name: { ru: "Цикл", en: "Loop" },
    syntax: ["loop <цель>:", "loop {<имя>::*}:"],
    desc: {
      ru: "`loop <цель>:` — для каждого игрока. Внутри `loop-player` и `%loop-player%`. `loop {имя::*}:` — по прямым индексам списка: `%loop-index%` — ключ, `%loop-value%` — значение. `player` остаётся тем, кто вызвал скрипт. Каждый проход тратит шаги `maxScriptSteps`.",
      en: "`loop <target>:` runs for every player. Inside: `loop-player` and `%loop-player%`. `loop {name::*}:` walks the direct indexes of a list: `%loop-index%` is the key, `%loop-value%` is the value. `player` stays whoever ran the script. Every pass costs `maxScriptSteps` steps."
    },
    example: `command /gather:
	permission: 2
	loop all players:
		send "&e%loop-player%, сбор у %player%!" to loop-player

loop {bag::%player%::*}:
	send "%loop-index% x%loop-value%" to player`,
    since: "0.1.2", related: ["target-loop-player", "ph-loop-player", "ph-loop-index", "expr-variable", "target-all"]
  },
  {
    id: "struct-define-block", category: "structures",
    name: { ru: "Свой блок", en: "Custom block" },
    syntax: ["define block <id>:", "define block <namespace>:<id>:"],
    desc: {
      ru: "Регистрирует блок (вместе с предметом-блоком) во вкладке творческого режима ScriptCraft. Свойства (все необязательные): `name:` название, `hardness:` прочность (1.5), `resistance:` взрывоустойчивость (6), `sound:` stone/wood/metal/glass/grass/sand/wool/gravel/honey/slime/snow, `light:` свечение 0–15, `requires_tool:` true — дроп только подходящим инструментом, `facing:` none/horizontal/all. Физика: `friction:` скольжение 0–1 (камень 0.6, лёд 0.98), `speed:` скорость ходьбы (1 обычно, песок душ 0.4), `jump:` высота прыжка (мёд 0.5), `gravity: true` — падает как песок, `collision: false` — сквозь блок можно пройти, `flammable: true` или `burn:` и `spread:` — горит (доски: burn 5, spread 20). id — только `a-z 0-9 _ . -`. С 0.1.9 блок, который есть в скриптах при запуске сервера, регистрируется под **настоящим id** (`example:ruby_block`): работают ванильные `/give`, `/setblock`, F3. Новый `define` после `/sc reload` появляется сразу во временном слоте `scriptcraft:dyn_block_N`. `render:` solid / cutout / translucent. `full: false` — блок не прячет пол под собой. Модель для поворота смотрит на север. Смена `render`/`full` у уже стоящих блоков видна после перезапуска клиента.",
      en: "Registers a block (plus its block item) in the ScriptCraft creative tab. Properties (all optional): `name:`, `hardness:` (1.5), `resistance:` (6), `sound:` stone/wood/metal/glass/grass/sand/wool/gravel/honey/slime/snow, `light:` 0–15, `requires_tool:` true, `facing:` none/horizontal/all. Physics: `friction:` 0–1 (stone 0.6, ice 0.98), `speed:` walk speed (soul sand 0.4), `jump:` (honey 0.5), `gravity: true` falls like sand, `collision: false` can be walked through, `flammable: true` or `burn:` and `spread:` (planks: burn 5, spread 20). id: only `a-z 0-9 _ . -`. Since 0.1.9 a block present at server start is registered under its **real id** (`example:ruby_block`). A new `define` after `/sc reload` appears at once in `scriptcraft:dyn_block_N`. `render:` solid / cutout / translucent. `full: false` keeps the floor under the block visible. A rotating model must face north. Changing `render`/`full` on placed blocks shows up after a client restart.",
    },
    example: `define block ruby_block:
	name: "Рубиновый блок"
	hardness: 5
	resistance: 6
	sound: metal
	light: 7
	requires_tool: true
	facing: horizontal
	render: solid
	full: true

define block ruby_lantern:
	name: "Рубиновый фонарь"
	sound: glass
	light: 12
	facing: all
	render: cutout
	full: false

define block ice_block:
	name: "Лёд"
	sound: glass
	friction: 0.98

define block soft_sand:
	name: "Сыпучий песок"
	sound: sand
	gravity: true
	flammable: false

define block wool_block:
	name: "Шерсть"
	sound: wool
	burn: 30
	spread: 60`,
    since: "0.1.0", related: ["struct-define-item", "struct-namespace"]
  },
  {
    id: "struct-define-item", category: "structures",
    name: { ru: "Свой предмет", en: "Custom item" },
    syntax: ["define item <id>:", "define item <namespace>:<id>:"],
    desc: {
      ru: "Регистрирует предмет. Общие свойства: `name:`, `stack_size:` 1–64, `rarity:` common/uncommon/rare/epic, `fire_resistant:` true. `type:` задаёт поведение. `food` — еда: `nutrition:` голод (хлеб 5), `saturation:` насыщение (хлеб 0.6), `always_eat: true` — есть и сытым. `sword`, `pickaxe`, `axe`, `shovel`, `hoe` — инструмент: `tier:` wood/stone/iron/gold/diamond/netherite, `durability:` прочность (если не писать — как у ванили этого уровня). Инструмент копает свои блоки и бьёт с уроном этого уровня. `fishing_rod` — удочка, `shears` — ножницы (шерсть, листва, паутина). У инструментов стак по умолчанию 1. Известный при запуске предмет получает настоящий id; новый после `/sc reload` живёт во временном слоте до перезапуска.",
      en: "Registers an item. Shared properties: `name:`, `stack_size:` 1–64, `rarity:` common/uncommon/rare/epic, `fire_resistant:` true. `type:` picks the behaviour. `food`: `nutrition:` hunger (bread 5), `saturation:` (bread 0.6), `always_eat: true` can be eaten when full. `sword`, `pickaxe`, `axe`, `shovel`, `hoe`: `tier:` wood/stone/iron/gold/diamond/netherite, `durability:` (omit it for the vanilla durability of that tier). The tool mines its own blocks and hits with that tier's damage. `fishing_rod` casts a line, `shears` cut wool, leaves and cobwebs. Tools stack to 1 unless you set `stack_size`. An item known at startup gets its real id; a new one after `/sc reload` stays in a temporary slot until a restart."
    },
    example: `define item ruby:
	name: "Рубин"
	stack_size: 64
	rarity: rare

define item meat:
	name: "Жаркое"
	type: food
	nutrition: 8
	saturation: 0.8

define item ruby_pickaxe:
	name: "Рубиновая кирка"
	type: pickaxe
	tier: diamond

define item rod:
	name: "Удочка"
	type: fishing_rod`,
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
      ru: "Строка, начинающаяся с `#`, — комментарий. Блоки верхнего уровня (`on`, `command`, `define`, `every`) пишутся без отступа и заканчиваются двоеточием, действия — с отступом. В примерах отступ — Tab; один Tab равен четырём пробелам, пробелы тоже понимаются. Ошибка в строке не ломает файл: строка пропускается, ошибка видна в логе и при `/sc reload`. Файлы — UTF-8, `*.sc`; файл с именем на `-` выключен. Старые `*.sk` при запуске мода переименовываются в `*.sc`.",
      en: "A line starting with `#` is a comment. Top-level blocks (`on`, `command`, `define`, `every`) have no indent and end with a colon; actions are indented. Examples use Tab; one Tab equals four spaces, and spaces still work. An error in one line does not break the file: the line is skipped and reported in the log and on `/sc reload`. Files are UTF-8 `*.sc`; a file whose name starts with `-` is disabled. Old `*.sk` files are renamed to `*.sc` when the mod starts."
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
    since: "0.1.0", related: ["expr-variable", "eff-add-var", "eff-remove-var", "eff-delete-var"]
  },
  {
    id: "eff-add-var", category: "effects",
    name: { ru: "Прибавить к переменной", en: "Add to variable" },
    syntax: ["add <значение> to {<имя>}", "add <значение> {<имя>}", "add <значение> to {<имя>::*}"],
    desc: {
      ru: "Число к числу: незаданная переменная считается 0. Текст дописывается в конец. `{список::*}` — список, не число: элемент кладётся под следующим номером (1, 2, 3…). Слово `to` можно не писать, если сразу после значения стоит переменная. В значение годится текст, `player` (имя), `loop-player`, `targeted block`, `player's tool` и локация.",
      en: "A number added to a number treats an unset variable as 0. Text is appended. `{list::*}` is a list, not a number: the element is stored at the next index (1, 2, 3…). `to` can be left out when the variable comes right after the value. The value may be text, `player` (the name), `loop-player`, `targeted block`, `player's tool` or a location."
    },
    example: `add 5 to {coins::%player%}
add player's tool to {tools::%player%::*}
add targeted block {seen::%player%::*}`,
    since: "0.1.0", related: ["eff-remove-var", "eff-set-var", "expr-variable"]
  },
  {
    id: "eff-remove-var", category: "effects",
    name: { ru: "Отнять от переменной", en: "Remove from variable" },
    syntax: ["remove <значение> from {<имя>}", "remove <значение> {<имя>}", "remove <значение> from {<имя>::*}"],
    desc: {
      ru: "Из числа вычитает. Из текста вырезает первое вхождение. Из списка `{список::*}` убирает одно равное значение. Слово `from` можно не писать, если сразу после значения стоит переменная.",
      en: "Subtracts from a number. Cuts the first occurrence out of text. Removes one equal value from a list `{list::*}`. `from` can be left out when the variable comes right after the value."
    },
    example: `remove 1 from {coins::%player%}`,
    since: "0.1.0", related: ["eff-add-var", "eff-set-var", "expr-variable"]
  },
  {
    id: "eff-delete-var", category: "effects",
    name: { ru: "Удалить переменную", en: "Delete variable" },
    syntax: ["delete {<имя>}", "clear {<имя>}", "delete {<имя>::*}"],
    desc: {
      ru: "Удаляет переменную (после этого `is not set`). `delete {list::*}` удаляет все прямые индексы, сам список отдельно не хранится.",
      en: "Deletes a variable (it becomes `is not set`). `delete {list::*}` removes every direct index; the list is not stored on its own."
    },
    example: `delete {coins::%player%}`,
    since: "0.1.0", related: ["eff-set-var", "cond-var-set"]
  },
  {
    id: "eff-nbt", category: "effects",
    name: { ru: "Записать NBT", en: "Set NBT" },
    syntax: ["set nbt \"<путь>\" of targeted entity to <значение>", "set nbt \"<путь>\" of targeted block to <значение>", "set nbt \"<путь>\" of targeted player to <значение>", "set nbt \"<путь>\" of player to <значение>"],
    desc: {
      ru: "Пишет NBT-тег сущности, игрока или блока в прицеле. Путь как в `/data`: `CustomName`, `Items[0].id`. Число пишется числом, `true`/`false` — байтом, текст — строкой, `{...}` и `[...]` — как SNBT. У блока без данных будет ошибка. Весь тег целиком: `%nbt of targeted entity%`, один путь: `%nbt CustomName of targeted entity%`. NBT предметов в инвентаре — отдельное выражение.",
      en: "Writes an NBT tag on the entity, player or block in the crosshair. The path is the same as `/data`: `CustomName`, `Items[0].id`. A number is stored as a number, `true`/`false` as a byte, text as a string, `{...}` and `[...]` as SNBT. A block with no data is an error. The whole tag: `%nbt of targeted entity%`. One path: `%nbt CustomName of targeted entity%`."
    },
    example: `send "%nbt of targeted entity%" to player
set nbt "CustomName" of targeted entity to "\"Гриб\""`,
    since: "0.3.9", related: ["eff-nbt-remove", "ph-targeted", "target-targeted", "expr-item-nbt"]
  },
  {
    id: "eff-nbt-remove", category: "effects",
    name: { ru: "Стереть NBT", en: "Remove NBT" },
    syntax: ["remove nbt \"<путь>\" of targeted entity", "remove nbt \"<путь>\" of targeted block", "remove nbt \"<путь>\" of targeted player", "remove nbt \"<путь>\" of player"],
    desc: {
      ru: "Стирает один NBT-путь у сущности, игрока или блока в прицеле. Путь как в `/data`: `CustomName`, `Items[0].id`. У блока без данных будет ошибка.",
      en: "Removes one NBT path from the entity, player or block in the crosshair. The path is the same as `/data`: `CustomName`, `Items[0].id`. A block with no data is an error."
    },
    example: `remove nbt "CustomName" of targeted entity`,
    since: "0.3.9", related: ["eff-nbt", "ph-targeted", "target-targeted"]
  },

  // ================================================================== ЭФФЕКТЫ: чат
  {
    id: "eff-send", category: "effects",
    name: { ru: "Сообщение игрокам", en: "Send message" },
    syntax: ["send \"<текст>\"", "send \"<текст>\" to <цель>", "message \"<текст>\" to <цель>"],
    desc: {
      ru: "Личное сообщение игрокам цели (без `to` — игроку из контекста). Поддерживает плейсхолдеры, `{переменные}`, `{@заготовку}` и цвета `&`. Кавычка внутри текста пишется дважды: `send \"оружие \"\"karabin\"\" старое\" to player`. Плейсхолдеры считаются от вызвавшего, а не от получателя.",
      en: "Private message to the target players (without `to` — the context player). Supports placeholders, `{variables}`, `{@option}` and `&` colours. A quote inside the text is written twice: `send \"weapon \"\"karabin\"\" is old\" to player`. Placeholders are evaluated for the caller, not the recipient."
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
    syntax: ["play sound <id звука> to <цель>", "play sound <id> to <цель> with volume <громкость>", "play sound <id> to <цель> with volume <громкость> pitch <высота>"],
    desc: {
      ru: "Проигрывает звук, который слышит только сам игрок. id — как в ванильных командах (`entity.player.levelup`), можно из модов (`mod:sound`). Громкость 0–10 (по умолчанию 1), высота 0.5–2 (по умолчанию 1).",
      en: "Plays a sound only that player hears. id as in vanilla commands (`entity.player.levelup`), mod sounds work too (`mod:sound`). Volume 0–10 (default 1), pitch 0.5–2 (default 1)."
    },
    example: `play sound entity.player.levelup to player
play sound entity.experience_orb.pickup to player with volume 0.4 pitch 1.2`,
    since: "0.1.2", related: ["eff-title"]
  },

  // ================================================================== ЭФФЕКТЫ: предметы и перемещение
  {
    id: "eff-give-item", category: "effects",
    name: { ru: "Выдать предмет", en: "Give item" },
    syntax: ["give <цель> <предмет>", "give <цель> <предмет> <количество>", "give <цель> <предмет>{<nbt>}", "give <цель> player's tool", "give <цель> {<переменная>}"],
    desc: {
      ru: "Выдаёт предмет (если инвентарь полон — бросает рядом). Количество — число или `{переменная}` (до 2304). В id можно плейсхолдер: `give player %arg-1%`. Сразу за id можно написать SNBT: `give player tacz:modern_kinetic_gun{GunId:\"tacz:glock_17\"} 1`. Если двоеточие перед кавычкой пропущено (`GunId\"tacz:glock_17\"`), оно подставляется. То же nbt принимает `drop`, слот и проверки `has` / `holding`. Предмет может быть выражением: `player's tool`, `{слот}`. Короткий id ищется как `minecraft:`, потом `scriptcraft:`, потом namespace скриптов.",
      en: "Gives an item (drops it nearby if the inventory is full). Count is a number or `{variable}` (up to 2304). The id may contain a placeholder: `give player %arg-1%`. SNBT may follow the id: `give player tacz:modern_kinetic_gun{GunId:\"tacz:glock_17\"} 1`. A missing colon before the quote (`GunId\"tacz:glock_17\"`) is inserted. `drop`, slots and `has` / `holding` accept the same nbt. The item may be an expression: `player's tool`, `{slot}`. A short id is looked up as `minecraft:`, then `scriptcraft:`, then script namespaces."
    },
    example: `give player diamond 3
give all players example:ruby
give player tacz:modern_kinetic_gun{GunId:"tacz:glock_17"} 1
give player player's tool`,
    since: "0.1.0", related: ["struct-define-item", "eff-give-effect", "eff-set-slot"]
  },
  {
    id: "eff-set-slot", category: "effects",
    name: { ru: "Слот инвентаря", en: "Inventory slot" },
    syntax: ["set slot <слот> of <цель> to <предмет>", "set slot <слот> of <цель> to <количество> <предмет>", "set slot hotbar <1-9> of <цель> to <предмет>", "clear slot <слот> of <цель>"],
    desc: {
      ru: "Кладёт один стак в слот. Номера 0–8 — хотбар, 9–35 — инвентарь, 36 ботинки, 37 штаны, 38 нагрудник, 39 шлем, 40 левая рука. `slot hotbar 1` — это слот 0. Имена: `helmet`, `chestplate`, `leggings`, `boots`, `offhand`, `mainhand`. Количество больше максимума стака обрезается. `air` и количество 0 очищают слот. Предмет — выражение: `diamond`, `diamond{Unbreakable:1b}`, `player's tool`, `{переменная}`, параметр функции. `named \"текст\"` ставит имя (цвета `&`, без курсива). `with nbt {тег}` дописывает NBT после тега в id. Имя пишется последним и перекрывает `display.Name` из тега. Можно `of player's inventory` и `of who`, если `who` — параметр-игрок.",
      en: "Puts one stack into a slot. Indices 0–8 are the hotbar, 9–35 storage, 36 boots, 37 leggings, 38 chestplate, 39 helmet, 40 offhand. `slot hotbar 1` is slot 0. Names: `helmet`, `chestplate`, `leggings`, `boots`, `offhand`, `mainhand`. A count above the stack limit is clamped. `air` and a count of 0 clear the slot. The item is an expression: `diamond`, `diamond{Unbreakable:1b}`, `player's tool`, `{variable}`, a function parameter. `named \"text\"` sets the display name (`&` colours, not italic). `with nbt {tag}` merges NBT after the tag in the id. The name is applied last and overrides `display.Name` from the tag. `of player's inventory` and `of who` work when `who` is a player parameter."
    },
    example: `set slot 0 of player to 1 diamond named "тест"
set slot hotbar 1 of player to diamond{Unbreakable:1b} named "&bМеч" with nbt {Damage:0}
set slot helmet of who to 1 netherite_helmet
clear slot 40 of player`,
    since: "0.4.5", related: ["eff-give-item", "expr-item-nbt", "eff-nbt"]
  },
  {
    id: "eff-teleport", category: "effects",
    name: { ru: "Телепорт", en: "Teleport" },
    syntax: ["teleport <цель> to <x> <y> <z>", "teleport <цель> to <цель>", "teleport <цель> to <локация>", "teleport <цель> to spawn", "teleport <цель> to world spawn", "tp <цель> to ..."],
    desc: {
      ru: "Телепорт по координатам (в текущем мире игрока; запятые между числами можно писать), к первому игроку второй цели (в его мир, с его поворотом) или в локацию: `player's location`, `location of targeted block`, `{дом}`. `to spawn` — точка возрождения, `to world spawn` — общий спавн обычного мира. Пустая вторая цель — ничего.",
      en: "Teleports to coordinates (in the player's current world; commas between numbers are allowed), to the first player of the second target (into their world, with their rotation), or to a location: `player's location`, `location of targeted block`, `{home}`. `to spawn` is the respawn point, `to world spawn` is the shared overworld spawn. An empty second target does nothing."
    },
    example: `teleport player to 0, 100, 0
teleport other players in radius 100 of player to player
teleport player to {home::%player%}`,
    since: "0.1.0", related: ["target-radius"]
  },
  {
    id: "eff-open-screen", category: "effects",
    name: { ru: "Открыть экран", en: "Open screen" },
    syntax: ["open screen \"<заголовок>\":", "open screen \"<заголовок>\" to <цель>:"],
    desc: {
      ru: "Открывает игрокам экран (GUI) с элементами, перечисленными с отступом ниже: `text`, `button`, `slot`, `image`, `bar`, `hotbar`. У каждого элемента есть `layer` (слой). См. категории «Экраны» и «UI / HUD».",
      en: "Opens a screen (GUI) for the players, with elements listed indented below: `text`, `button`, `slot`, `image`, `bar`, `hotbar`. Every element has a `layer`. See the “Screens” and “UI / HUD” categories."
    },
    example: `command /menu:
	open screen "Главное меню" to player:
		text "Привет, %player%!" at 0, -40 color #FFD700 shadow
		button "Закрыть" at 0, 20 size 120x20 close`,
    since: "0.1.0", related: ["screen-text", "screen-button", "screen-slot", "screen-image", "hud-bar", "hud-layer"]
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
    since: "0.1.2", related: ["eff-set-food", "eff-set-saturation"]
  },
  {
    id: "eff-set-food", category: "effects",
    name: { ru: "Голод", en: "Food level" },
    syntax: ["set food level of <цель> to <число>", "set hunger of <цель> to <число>", "set player's food level to <число>", "set player's hunger to <число>"],
    desc: {
      ru: "Ставит голод, 0–20. `hunger` — то же, что `food level`.",
      en: "Sets food level, 0–20. `hunger` is the same as `food level`."
    },
    example: `set food level of player to 10`,
    since: "0.1.2", related: ["eff-set-saturation", "eff-feed"]
  },
  {
    id: "eff-set-saturation", category: "effects",
    name: { ru: "Насыщение", en: "Saturation" },
    syntax: ["set saturation of <цель> to <число>", "set player's saturation to <число>"],
    desc: {
      ru: "Ставит насыщение. Оно не бывает больше текущего голода — так устроена ванилла.",
      en: "Sets saturation. It cannot exceed the current food level: that is how vanilla works."
    },
    example: `set saturation of player to 5`,
    since: "0.1.2", related: ["eff-set-food", "eff-feed"]
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
    id: "eff-ignite", category: "effects",
    name: { ru: "Поджечь", en: "Ignite" },
    syntax: ["ignite <цель> for <число> seconds"],
    desc: {
      ru: "Поджигает игроков цели на N секунд (1–3600).",
      en: "Sets the target players on fire for N seconds (1–3600)."
    },
    example: `ignite player for 5 seconds`,
    since: "0.1.2", related: ["eff-extinguish", "eff-damage"]
  },
  {
    id: "eff-extinguish", category: "effects",
    name: { ru: "Потушить", en: "Extinguish" },
    syntax: ["extinguish <цель>"],
    desc: {
      ru: "Тушит игроков цели.",
      en: "Puts out the fire on the target players."
    },
    example: `extinguish player`,
    since: "0.1.2", related: ["eff-ignite", "eff-damage"]
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
    syntax: ["set flying of <цель> to true", "set flying of <цель> to false", "set flight of <цель> to true", "set player's flying to false"],
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
    syntax: ["give <цель> effect <id> for <число> seconds", "give <цель> effect <id> for <число> seconds level <число>"],
    desc: {
      ru: "Накладывает эффект. `level 1` — без усиления (по умолчанию), `level 2` — II и т.д. Вместо `seconds` можно `second`, `secs`, `sec`. id: `speed`, `minecraft:night_vision`, `othermod:frost`.",
      en: "Applies an effect. `level 1` is the base level (default), `level 2` is II, etc. `second`, `secs`, `sec` also work. id: `speed`, `minecraft:night_vision`, `othermod:frost`."
    },
    example: `give player effect speed for 60 seconds level 2
give @a[distance=..10] effect regeneration for 10 seconds`,
    since: "0.1.2", related: ["eff-remove-effect", "eff-clear-effects"]
  },
  {
    id: "eff-remove-effect", category: "effects",
    name: { ru: "Снять эффект", en: "Remove effect" },
    syntax: ["remove effect <id> from <цель>"],
    desc: { ru: "Снимает один эффект зелья.", en: "Removes one potion effect." },
    example: `remove effect speed from player`,
    since: "0.1.2", related: ["eff-clear-effects", "eff-give-effect"]
  },
  {
    id: "eff-clear-effects", category: "effects",
    name: { ru: "Снять все эффекты", en: "Clear effects" },
    syntax: ["clear effects of <цель>", "clear all effects of <цель>"],
    desc: { ru: "Снимает все эффекты зелий. `clear all effects` — то же самое.", en: "Removes every potion effect. `clear all effects` is the same." },
    example: `clear effects of player`,
    since: "0.1.2", related: ["eff-remove-effect", "eff-give-effect"]
  },
  {
    id: "eff-xp", category: "effects",
    name: { ru: "Дать опыт", en: "Give experience" },
    syntax: ["give <число> xp to <цель>", "give <число> experience to <цель>"],
    desc: {
      ru: "Добавляет очки опыта, не уровни. Число может быть отрицательным.",
      en: "Adds experience points, not levels. The number may be negative."
    },
    example: `give 100 xp to player`,
    since: "0.1.2", related: ["eff-give-levels", "eff-set-xp"]
  },
  {
    id: "eff-give-levels", category: "effects",
    name: { ru: "Дать уровни", en: "Give levels" },
    syntax: ["give <число> levels to <цель>", "give <число> level to <цель>"],
    desc: {
      ru: "Прибавляет уровни опыта. Число может быть отрицательным.",
      en: "Adds experience levels. The number may be negative."
    },
    example: `give 3 levels to player`,
    since: "0.1.2", related: ["eff-xp", "eff-set-xp"]
  },
  {
    id: "eff-set-xp", category: "effects",
    name: { ru: "Задать уровень", en: "Set experience level" },
    syntax: ["set xp level of <цель> to <число>", "set level of <цель> to <число>", "set player's xp level to <число>", "set player's level to <число>"],
    desc: {
      ru: "Ставит уровень опыта. `level` — то же, что `xp level`.",
      en: "Sets the experience level. `level` is the same as `xp level`."
    },
    example: `set xp level of player to 30`,
    since: "0.1.2", related: ["eff-xp", "eff-give-levels"]
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
  {
    id: "eff-kick", category: "effects",
    name: { ru: "Кикнуть", en: "Kick" },
    syntax: ["kick <цель>", "kick <цель> due to \"<причина>\"", "kick <цель> because \"<причина>\""],
    desc: { ru: "Отключает игроков цели. Без причины сообщение — `Kicked`.", en: "Disconnects the target players. Without a reason the message is `Kicked`." },
    example: `command /kickall permission 2:
	kick all players due to "Перезапуск"`,
    since: "0.2.0", related: ["eff-ban", "eff-op", "eff-deop", "target-all"]
  },
  {
    id: "eff-ban", category: "effects",
    name: { ru: "Забанить", en: "Ban" },
    syntax: ["ban <цель>", "ban <цель> due to \"<причина>\"", "ban <цель> because \"<причина>\"", "ban <цель> for \"<причина>\""],
    desc: {
      ru: "Пишет игрока в бан-лист сервера и отключает, если он онлайн. Без причины сообщение — `Banned`. Имя оффлайн банится, только если этот профиль уже заходил на сервер. `kick` по-прежнему только отключает и не пишет в бан-лист.",
      en: "Adds the player to the server ban list and disconnects them if they are online. Without a reason the message is `Banned`. An offline name is banned only when that profile has already joined this server. `kick` still only disconnects and does not write the ban list."
    },
    example: `command /banplayer <player> permission 2:
	ban arg-1 due to "Читер"

command /banoffline <text> permission 2:
	ban arg-1 for "Реклама"`,
    since: "0.4.4", related: ["eff-kick", "cond-banned", "target-named"]
  },
  {
    id: "eff-lp-permission", category: "effects",
    name: { ru: "Право LuckPerms", en: "LuckPerms permission" },
    syntax: ["set permission <узел> of <цель> to true|false", "add permission <узел> to <цель>", "give permission <узел> to <цель>", "remove permission <узел> from <цель>", "clear permissions of <цель>"],
    desc: {
      ru: "Ставит, добавляет, снимает или сбрасывает узел LuckPerms. Узел и значение — выражения: кавычки, `{переменная}`, параметр функции. В конце можно `for <число> seconds|minutes|hours|days`. `set ... to false` записывает запрет. `clear` убирает обычные и regex-права, группы не трогает. Цель — любой игрок, в том числе параметр функции и имя оффлайн, если сервер уже видел профиль. Без LuckPerms строка — ошибка в журнале. В консоли при старте: `LuckPerms: мод`, `плагин`, `мод и плагин`, `api` или `нет`.",
      en: "Sets, adds, removes or clears a LuckPerms node. The node and the value are expressions: quotes, `{variable}`, a function parameter. A tail `for <number> seconds|minutes|hours|days` is optional. `set ... to false` stores a deny. `clear` removes normal and regex permissions and leaves groups. The target is any player, including a function parameter and an offline name the server has already seen. Without LuckPerms the line is a journal error. The console at startup says `LuckPerms: mod`, `plugin`, `mod and plugin`, `api` or `no`."
    },
    example: `add permission "scriptcraft.shop" to player
set permission "essentials.fly" of player to false
add permission "temp.fly" to player for 2 hours
clear permissions of "Steve"`,
    since: "0.4.5", related: ["eff-lp-group", "cond-permission", "cond-group"]
  },
  {
    id: "eff-lp-group", category: "effects",
    name: { ru: "Группа LuckPerms", en: "LuckPerms group" },
    syntax: ["add group <имя> to <цель>", "give group <имя> to <цель>", "remove group <имя> from <цель>", "set group of <цель> to <имя>", "set primary group of <цель> to <имя>", "clear groups of <цель>"],
    desc: {
      ru: "Добавляет или снимает наследование группы. `set group` и `set primary group` делают группу основной и добавляют её в наследование. `clear groups` снимает наследование и ставит основную группу `default`. Имя приводится к нижнему регистру. Срок — `for <число> days` (и seconds, minutes, hours). Оффлайн-имя пишется, если профиль уже есть на сервере.",
      en: "Adds or removes a group inheritance. `set group` and `set primary group` make that group primary and add the inheritance. `clear groups` removes inheritance and sets the primary group to `default`. The name is lowercased. A duration is `for <number> days` (also seconds, minutes, hours). An offline name is written when the server already has that profile."
    },
    example: `add group "vip" to player for 7 days
set primary group of player to "member"
remove group "vip" from "Steve"
clear groups of player`,
    since: "0.4.5", related: ["eff-lp-permission", "cond-group", "expr-lp"]
  },
  {
    id: "eff-lp-prefix", category: "effects",
    name: { ru: "Префикс LuckPerms", en: "LuckPerms prefix" },
    syntax: ["set prefix of <цель> to <текст>", "add prefix <текст> to <цель>", "remove prefix of <цель>", "clear prefixes of <цель>"],
    desc: {
      ru: "Префикс в чате LuckPerms. Цвета `&` работают. Приоритет по умолчанию 100: `with priority 200`. `set` без приоритета заменяет все префиксы, с приоритетом — только этот вес. `add` не стирает остальные. `remove` без приоритета стирает все, с приоритетом — один вес. Срок и приоритет можно писать в любом порядке: `for 1 hour with priority 10`.",
      en: "A LuckPerms chat prefix. `&` colours work. The default priority is 100: `with priority 200`. `set` without a priority replaces every prefix; with a priority it replaces only that weight. `add` leaves the others. `remove` without a priority clears all, with a priority only that weight. Duration and priority can be written in either order: `for 1 hour with priority 10`."
    },
    example: `set prefix of player to "&6[VIP] "
add prefix "&c[Админ] " to player with priority 200
remove prefix of player with priority 200
clear prefixes of player`,
    since: "0.4.5", related: ["eff-lp-suffix", "expr-lp", "expr-colors"]
  },
  {
    id: "eff-lp-suffix", category: "effects",
    name: { ru: "Суффикс LuckPerms", en: "LuckPerms suffix" },
    syntax: ["set suffix of <цель> to <текст>", "add suffix <текст> to <цель>", "remove suffix of <цель>", "clear suffixes of <цель>"],
    desc: {
      ru: "Суффикс LuckPerms. Те же правила, что у префикса: цвета `&`, `with priority`, `for <срок>`, `set` заменяет, `add` добавляет, `clear suffixes` стирает все.",
      en: "A LuckPerms suffix. Same rules as a prefix: `&` colours, `with priority`, `for <duration>`, `set` replaces, `add` adds, `clear suffixes` removes all."
    },
    example: `set suffix of player to " &7новичок"
add suffix " &b★" to player with priority 50 for 1 day
clear suffixes of player`,
    since: "0.4.5", related: ["eff-lp-prefix", "expr-lp"]
  },
  {
    id: "eff-lp-meta", category: "effects",
    name: { ru: "Meta LuckPerms", en: "LuckPerms meta" },
    syntax: ["set meta <ключ> of <цель> to <значение>", "remove meta <ключ> of <цель>", "clear meta of <цель>"],
    desc: {
      ru: "Произвольная meta-запись LuckPerms. Ключ и значение — выражения. `clear meta` стирает все meta-ключи игрока и не трогает префикс, суффикс и группы.",
      en: "An arbitrary LuckPerms meta entry. The key and the value are expressions. `clear meta` removes every meta key of the player and leaves the prefix, suffix and groups."
    },
    example: `set meta "color" of player to "gold"
set {color} to meta "color" of player
remove meta "color" of player`,
    since: "0.4.5", related: ["eff-lp-prefix", "expr-lp"]
  },
  {
    id: "eff-op", category: "effects",
    name: { ru: "Сделать оператором", en: "Op" },
    syntax: ["op <цель>"],
    desc: { ru: "Добавляет игрока в список операторов сервера. Права применяются сразу.", en: "Adds the player to the server operator list. Permissions apply immediately." },
    example: `command /makemeop permission 4:
	op player`,
    since: "0.2.0", related: ["eff-deop", "cond-player", "eff-kick"]
  },
  {
    id: "eff-deop", category: "effects",
    name: { ru: "Снять оператора", en: "Deop" },
    syntax: ["deop <цель>"],
    desc: { ru: "Убирает игрока из списка операторов сервера. Права применяются сразу.", en: "Removes the player from the server operator list. Permissions apply immediately." },
    example: `deop player`,
    since: "0.2.0", related: ["eff-op", "cond-player", "eff-kick"]
  },
  {
    id: "eff-say", category: "effects",
    name: { ru: "Сказать в чат", en: "Say" },
    syntax: ["make <цель> say \"<текст>\"", "make <цель> chat \"<текст>\""],
    desc: {
      ru: "Показывает строку в чате как сообщение этого игрока. `chat` — то же, что `say`.",
      en: "Shows the line in chat as that player's message. `chat` is the same as `say`."
    },
    example: `make player say "всем привет"`,
    since: "0.2.0", related: ["eff-log", "eff-broadcast", "eff-send"]
  },
  {
    id: "eff-log", category: "effects",
    name: { ru: "Записать в лог", en: "Log" },
    syntax: ["log \"<текст>\""],
    desc: {
      ru: "Пишет строку в лог сервера (`[Script]`). Игроки её не видят.",
      en: "Writes the line to the server log (`[Script]`). Players do not see it."
    },
    example: `log "%player% использовал /secret"`,
    since: "0.2.0", related: ["eff-say", "eff-broadcast"]
  },
  {
    id: "eff-stop", category: "effects",
    name: { ru: "stop", en: "stop" },
    syntax: ["stop"],
    desc: { ru: "Прерывает остальные действия этого запуска (в том числе код после `if` и следующие проходы `loop`).", en: "Stops the rest of this run (including code after the `if` and further `loop` passes)." },
    example: `command /vip:
	if {rank::%player%} is not "vip":
		send "&cТолько для VIP" to player
		stop
	give player diamond 1`,
    since: "0.2.0", related: ["eff-cancel", "struct-if"]
  },
  {
    id: "eff-cancel", category: "effects",
    name: { ru: "Отменить событие", en: "Cancel event" },
    syntax: ["cancel event", "cancel the event"],
    desc: {
      ru: "Отменяет текущее событие, если оно это позволяет: чат, ломание и установка блока, клик, выбрасывание, подбор, урон, смерть, смена режима. В команде и в `every` флаг ни на что не влияет.",
      en: "Cancels the current event when the event allows it: chat, break, place, click, drop, pickup, damage, death, game mode change. Inside a command or `every` the flag does nothing."
    },
    example: `on chat:
	if %message% is "secret":
		cancel event`,
    since: "0.2.0", related: ["event-chat", "event-break", "event-right-click", "event-drop", "event-death", "event-gamemode", "eff-stop"]
  },
  {
    id: "eff-time", category: "effects",
    name: { ru: "Время", en: "Time" },
    syntax: ["set time to day", "set time to noon", "set time to night", "set time to midnight", "set time to <тики>"],
    desc: {
      ru: "Меняет время во всех мирах. Имена: `day`, `noon`, `night`, `midnight`. Число — тики суток; длиннее суток заворачивается.",
      en: "Changes the time in every world. Names: `day`, `noon`, `night`, `midnight`. A number is the time of day in ticks; past a full day it wraps."
    },
    example: `command /day permission 2:
	set time to day`,
    since: "0.2.0", related: ["eff-weather"]
  },
  {
    id: "eff-weather", category: "effects",
    name: { ru: "Погода", en: "Weather" },
    syntax: ["set weather to clear", "set weather to sun", "set weather to rain", "set weather to thunder", "set weather to storm", "set weather to rain for <секунды> seconds", "set weather to thunder for <секунды> seconds"],
    desc: {
      ru: "Меняет погоду во всех мирах. `sun` — то же, что `clear`, `storm` — то же, что `thunder`. `for N seconds` задаёт, сколько погода держится.",
      en: "Changes the weather in every world. `sun` is the same as `clear`, `storm` is the same as `thunder`. `for N seconds` sets how long it lasts."
    },
    example: `set weather to clear
set weather to rain for 60 seconds`,
    since: "0.2.0", related: ["eff-time"]
  },
  {
    id: "eff-lightning", category: "effects",
    name: { ru: "Молния", en: "Lightning" },
    syntax: ["strike lightning at <цель>", "strike lightning at <x> <y> <z>", "strike lightning at <локация>"],
    desc: {
      ru: "Бьёт молнией по игрокам цели, по координатам или по локации (`location of targeted block`, `{дом}`). Координаты — в мире игрока из контекста, иначе в обычном мире.",
      en: "Strikes the target players, coordinates, or a location (`location of targeted block`, `{home}`). Coordinates use the context player's world, otherwise the overworld."
    },
    example: `command /smite permission 2:
	strike lightning at targeted player`,
    since: "0.2.0", related: ["eff-explode", "eff-set-block", "target-targeted", "expr-location"]
  },
  {
    id: "eff-set-block", category: "effects",
    name: { ru: "Поставить блок", en: "Set block" },
    syntax: ["set block at <x> <y> <z> to <блок>", "set block at <локация> to <блок>", "set block at targeted block to <блок>"],
    desc: {
      ru: "Ставит блок в состоянии по умолчанию. Координаты без локации — мир игрока из контекста, иначе обычный мир. `<блок>` — id (`stone`, `minecraft:glass`, `{блок}`) или параметр функции типа block. `air` убирает блок. `targeted block` — блок, на который смотрит игрок.",
      en: "Places the block's default state. Coordinates without a location use the context player's world, otherwise the overworld. `<block>` is an id (`stone`, `minecraft:glass`, `{block}`) or a function parameter of type block. `air` removes the block. `targeted block` is the block the player is looking at."
    },
    example: `set block at 10 64 10 to stone
set block at location of targeted block to air
set block at targeted block to glass`,
    since: "0.4.4", related: ["expr-location", "expr-targeted-block", "struct-function"]
  },
  {
    id: "eff-explode", category: "effects",
    name: { ru: "Взрыв", en: "Explosion" },
    syntax: ["explode at <цель>", "explode at <x> <y> <z>", "explode at <локация>", "explode at <цель> with force <0-8>", "explode at <локация> with force <0-8>"],
    desc: {
      ru: "Взрыв у игроков цели, по координатам или по локации. Сила 0–8, по умолчанию 3. Блоки ломаются. Координаты — в мире игрока из контекста, иначе в обычном мире. `power` — то же, что `force`.",
      en: "An explosion at the target players, at coordinates, or at a location. Force is 0–8, default 3. Blocks break. Coordinates use the context player's world, otherwise the overworld. `power` is the same as `force`."
    },
    example: `explode at player with force 2`,
    since: "0.2.0", related: ["eff-lightning", "expr-location"]
  },
  {
    id: "eff-velocity", category: "effects",
    name: { ru: "Скорость (вектор движения)", en: "Velocity" },
    syntax: ["set velocity of <цель> to <x> <y> <z>", "set velocity of <цель> to vector(<x>, <y>, <z>)", "set player's velocity to vector(<x>, <y>, <z>)"],
    desc: {
      ru: "Задаёт скорость игрока по трём осям — тремя числами или через `vector(x, y, z)`.",
      en: "Sets the player's velocity on the three axes — either as three numbers or via `vector(x, y, z)`."
    },
    example: `set velocity of player to 0 1 0
set velocity of player to vector(0.0, 1.2, 0.0)`,
    since: "0.2.0", related: ["eff-launch", "eff-teleport", "expr-location"]
  },
  {
    id: "eff-launch", category: "effects",
    name: { ru: "Толчок", en: "Launch" },
    syntax: ["launch <цель> upwards", "launch <цель> forwards", "launch <цель> upwards by <скорость>", "launch <цель> forwards by <скорость>"],
    desc: {
      ru: "Толкает вверх или вперёд по взгляду. Без числа вверх — около 1.2, вперёд — около 1.5. Скорость ограничена примерно −8…8. `by` можно писать как `with speed`.",
      en: "Pushes up, or forward along the look direction. With no number, up is about 1.2 and forward is about 1.5. Speed is clamped to about −8…8. `by` can also be written `with speed`."
    },
    example: `command /jump:
	launch player upwards by 1.2

command /dash:
	launch player forwards by 2`,
    since: "0.2.0", related: ["eff-velocity", "eff-teleport"]
  },
  {
    id: "eff-spawn", category: "effects",
    name: { ru: "Точка возрождения", en: "Spawn point" },
    syntax: ["set spawn of <цель>", "set spawn of <цель> to <x> <y> <z>", "set spawn of <цель> to <локация>"],
    desc: {
      ru: "Без координат ставит точку возрождения там, где стоит игрок (в его мире). С координатами — в его текущем мире. С локацией — в мире и точке этой локации. Телепорт туда — `teleport ... to spawn`.",
      en: "Without coordinates, sets the respawn point where the player stands (in their world). With coordinates — in their current world. With a location — in that location's world and point. Teleporting there is `teleport ... to spawn`."
    },
    example: `command /sethome:
	set spawn of player
	send "&aДом сохранён" to player

command /home:
	teleport player to spawn`,
    since: "0.2.0", related: ["eff-teleport", "event-death", "event-respawn"]
  },
  {
    id: "eff-drop", category: "effects",
    name: { ru: "Бросить предмет", en: "Drop item" },
    syntax: ["drop <предмет> at <цель>", "drop <количество> <предмет> at <цель>", "drop <предмет> at <локация>"],
    desc: { ru: "Роняет предмет у ног игроков цели или в точке локации (не в инвентарь). Предмет может быть `player's tool`, `{слот}` или id с nbt, как у `give`.", en: "Drops the item at the target players' feet or at a location (not into the inventory). The item may be `player's tool`, `{slot}` or an id with nbt, the same as `give`." },
    example: `drop 4 diamond at player
drop player's tool at location of targeted block
drop example:ruby at all players`,
    since: "0.2.0", related: ["eff-give-item", "event-drop", "event-pickup"]
  },
  {
    id: "eff-walk-speed", category: "effects",
    name: { ru: "Скорость ходьбы", en: "Walk speed" },
    syntax: ["set walk speed of <цель> to <число>", "set player's walk speed to <число>"],
    desc: {
      ru: "Скорость ходьбы. У ванили это `0.1`, диапазон 0–1.",
      en: "Walk speed. Vanilla uses `0.1`. The range is 0–1."
    },
    example: `set walk speed of player to 0.2`,
    since: "0.2.0", related: ["eff-fly-speed", "expr-property"]
  },
  {
    id: "eff-fly-speed", category: "effects",
    name: { ru: "Скорость полёта", en: "Fly speed" },
    syntax: ["set fly speed of <цель> to <число>", "set flying speed of <цель> to <число>", "set player's fly speed to <число>"],
    desc: {
      ru: "Скорость полёта. У ванили это `0.05`, диапазон 0–1. `flying speed` — то же, что `fly speed`.",
      en: "Fly speed. Vanilla uses `0.05`. The range is 0–1. `flying speed` is the same as `fly speed`."
    },
    example: `set fly speed of player to 0.1`,
    since: "0.2.0", related: ["eff-walk-speed", "eff-flying", "expr-property"]
  },
  {
    id: "eff-air", category: "effects",
    name: { ru: "Воздух", en: "Air" },
    syntax: ["set air of <цель> to <число>", "set oxygen of <цель> to <число>", "set player's air to <число>"],
    desc: {
      ru: "Запас воздуха. Обрезается по максимуму игрока. `oxygen` — то же, что `air`.",
      en: "Air supply. Clamped to the player's maximum. `oxygen` is the same as `air`."
    },
    example: `set air of player to 300`,
    since: "0.2.0", related: ["expr-property"]
  },
  {
    id: "eff-absorption", category: "effects",
    name: { ru: "Абсорбция", en: "Absorption" },
    syntax: ["set absorption of <цель> to <число>", "set player's absorption to <число>"],
    desc: {
      ru: "Ставит жёлтые сердца абсорбции.",
      en: "Sets the yellow absorption hearts."
    },
    example: `set absorption of player to 4`,
    since: "0.2.0", related: ["eff-set-health", "expr-property"]
  },
  {
    id: "eff-close-inventory", category: "effects",
    name: { ru: "Закрыть инвентарь", en: "Close inventory" },
    syntax: ["close inventory of <цель>"],
    desc: {
      ru: "Закрывает открытый сундук или верстак.",
      en: "Closes an open chest or crafting table."
    },
    example: `close inventory of player`,
    since: "0.2.0", related: ["eff-clear-inventory", "event-open"]
  },
  {
    id: "eff-wake", category: "effects",
    name: { ru: "Разбудить", en: "Wake up" },
    syntax: ["wake up <цель>", "wake <цель> up"],
    desc: {
      ru: "Будит игрока, если он спит.",
      en: "Wakes the player if they are sleeping."
    },
    example: `wake up all players`,
    since: "0.2.0", related: ["cond-player"]
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
  {
    id: "cond-and", category: "conditions",
    name: { ru: "and / or", en: "and / or" },
    syntax: ["<условие> and <условие>", "<условие> or <условие>"],
    desc: {
      ru: "Связывает условия в одной строке. `and` проверяется раньше `or`. Работает и со сравнениями, и с проверками игрока.",
      en: "Joins conditions in one line. `and` is checked before `or`. Works with comparisons and with player checks."
    },
    example: `if player is sneaking and health of player < 10:
	send "&eТихий и раненый" to player
if player is op or player has permission 2:
	send "&aУ тебя есть права" to player`,
    since: "0.2.0", related: ["struct-if", "cond-player", "cond-compare"]
  },
  {
    id: "cond-player", category: "conditions",
    name: { ru: "Состояние игрока", en: "Player state" },
    syntax: ["<цель> is sneaking", "<цель> is not sneaking", "<цель> is sprinting", "<цель> is flying", "<цель> is sleeping", "<цель> is burning", "<цель> is on fire", "<цель> is op", "<цель> is alive", "<цель> is blocking", "<цель> is gliding", "<цель> is swimming", "<цель> is online", "<цель> is not online", "<цель> is holding <предмет>", "<цель> has <предмет>", "<цель> does not have <предмет>", "<цель> has permission <0-4>", "<цель> has permission \"<узел>\""],
    desc: {
      ru: "Проверка для каждого игрока цели: никого нет — условие ложно (и для `is`, и для `is not`). `online` смотрит, есть ли игроки в цели. `holding` — предмет в основной руке, `has` — где угодно в инвентаре. Если у предмета указан nbt, каждый его ключ должен быть на стеке. Неизвестный предмет даёт ложь. `op` — запись в ops.txt, `permission N` — уровень прав, `permission \"узел\"` — LuckPerms (мод или плагин; без него — узел Forge или оператор).",
      en: "Checked for every player of the target: nobody there — the condition is false (both `is` and `is not`). `online` is whether the target has any players. `holding` is the main hand, `has` is anywhere in the inventory. If the item has nbt, every key of that tag must be on the stack. An unknown item is false. `op` is an ops.txt entry, `permission N` is the permission level, `permission \"node\"` is LuckPerms (a mod or a plugin; without it — a Forge node or an operator)."
    },
    example: `if player is sneaking:
	send "&7ты крадёшься" to player
if player is not holding diamond_sword:
	give player diamond_sword 1
if player does not have bread:
	give player bread 8`,
    since: "0.2.0", related: ["cond-banned", "cond-gamemode", "cond-world", "cond-and", "eff-give-item", "cond-permission"]
  },
  {
    id: "cond-banned", category: "conditions",
    name: { ru: "Игрок забанен", en: "Player is banned" },
    syntax: ["<цель> is banned", "<цель> is not banned"],
    desc: {
      ru: "Смотрит бан-лист сервера. Для имени, которого нет в сети (`\"Steve\" is banned`), проверяется профиль, если сервер его уже видел. Никого в цели нет и имени нет — условие ложно.",
      en: "Reads the server ban list. A name that is not online (`\"Steve\" is banned`) is checked when the server has already seen that profile. Nobody in the target and no name — the condition is false."
    },
    example: `if player is banned:
	kick player due to "Banned"
if "Steve" is not banned:
	send "&aSteve не в бане" to player`,
    since: "0.4.4", related: ["eff-ban", "eff-kick", "cond-player"]
  },
  {
    id: "cond-group", category: "conditions",
    name: { ru: "Группа LuckPerms", en: "LuckPerms group" },
    syntax: ["<цель> is in group <имя>", "<цель> is not in group <имя>"],
    desc: {
      ru: "Игрок в этой группе LuckPerms: основная группа или наследование. Имя — выражение. Никого в цели нет и имени нет — ложь и для `is`, и для `is not`. Имя оффлайн проверяется, если LuckPerms уже загрузил этот профиль. Без LuckPerms условие ложно.",
      en: "The player is in that LuckPerms group: the primary group or an inheritance. The name is an expression. Nobody in the target and no name — false for both `is` and `is not`. An offline name is checked when LuckPerms has already loaded that profile. Without LuckPerms the condition is false."
    },
    example: `if player is in group "vip":
	send "&6VIP" to player
if "Steve" is not in group "banned":
	add group "member" to "Steve"`,
    since: "0.4.5", related: ["eff-lp-group", "cond-permission", "cond-player"]
  },
  {
    id: "cond-permission", category: "conditions",
    name: { ru: "Есть право", en: "Has permission" },
    syntax: ["<цель> has permission <0-4>", "<цель> has permission \"<узел>\"", "<цель> does not have permission <0-4>", "<цель> does not have permission \"<узел>\""],
    desc: {
      ru: "Число 0–4 — ванильный уровень (2 — оператор). Строка — узел прав, например `scriptcraft.shop`. Его проверяет LuckPerms, если он стоит модом или плагином. Если LuckPerms не ответил, проверяется узел, который мод зарегистрировал в Forge. Иначе такой узел есть только у оператора. Никого в цели нет — условие ложно.",
      en: "A number 0–4 is the vanilla level (2 is operator). A string is a permission node, for example `scriptcraft.shop`. LuckPerms answers it whether LuckPerms is installed as a mod or as a plugin. If LuckPerms does not answer, a node registered with Forge is checked. Otherwise only operators have that node. Nobody in the target — the condition is false."
    },
    example: `if player has permission "scriptcraft.shop":
	send "&aМожно" to player
else:
	send "&cНет права" to player

if player does not have permission 2:
	send "&7Ты не оператор" to player`,
    since: "0.4.1", related: ["struct-command", "cond-player"]
  },
  {
    id: "cond-gamemode", category: "conditions",
    name: { ru: "Режим игры", en: "Game mode" },
    syntax: ["<цель> is in creative", "<цель> is in survival", "<цель> is in adventure", "<цель> is in spectator", "<цель> is not in creative", "<цель> is in gamemode <режим>"],
    desc: {
      ru: "Режим можно писать и со словом `mode`: `is in creative mode`. Никого в цели нет — условие ложно.",
      en: "The mode can also include the word `mode`: `is in creative mode`. Nobody in the target — the condition is false."
    },
    example: `if player is in creative:
	send "&bКреатив" to player`,
    since: "0.2.0", related: ["cond-world", "eff-gamemode", "event-gamemode", "cond-player"]
  },
  {
    id: "cond-world", category: "conditions",
    name: { ru: "Мир игрока", en: "Player's world" },
    syntax: ["<цель> is in world <мир>", "<цель> is not in world <мир>"],
    desc: {
      ru: "Мир: `overworld`, `nether`, `the_end` или полный id `minecraft:the_nether`. Никого в цели нет — условие ложно.",
      en: "World: `overworld`, `nether`, `the_end` or a full id `minecraft:the_nether`. Nobody in the target — the condition is false."
    },
    example: `if player is in world the_nether:
	send "&cТы в аду" to player`,
    since: "0.2.0", related: ["cond-gamemode", "event-world-change", "cond-player"]
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
    name: { ru: "%targeted% — куда смотрит игрок", en: "%targeted% — what the player looks at" },
    syntax: ["%targeted%", "%targeted player%", "%targeted entity%", "%targeted block%"],
    desc: {
      ru: "`%targeted%` и `%targeted player%` — имя игрока в прицеле. `%targeted entity%` — моб или другая сущность (id, или имя). `%targeted block%` — id блока и координаты. Никого и ничего — `<none>`. Луч до `targetedPlayerRange` блоков, сквозь блоки не идёт. Ближе побеждает: сущность или блок.",
      en: "`%targeted%` and `%targeted player%` are the name of the player in the crosshair. `%targeted entity%` is a mob or any other entity (its id, or its name). `%targeted block%` is the block id and coordinates. Nothing — `<none>`. The ray is `targetedPlayerRange` blocks and stops at blocks. The closer one wins: the entity or the block."
    },
    example: `send "Игрок: %targeted player%" to player
send "Сущность: %targeted entity%" to player
send "Блок: %targeted block%" to player`,
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
    since: "0.1.2", related: ["struct-loop", "target-loop-player", "ph-loop-index"]
  },
  {
    id: "ph-loop-index", category: "expressions",
    name: { ru: "%loop-index% и %loop-value%", en: "%loop-index% and %loop-value%" },
    syntax: ["%loop-index%", "%loop-value%"],
    desc: {
      ru: "Внутри `loop {имя::*}:` и строки `list from`. `%loop-index%` — ключ (`minecraft:diamond`), `%loop-value%` — значение. Вне цикла — пусто.",
      en: "Inside `loop {name::*}:` and a `list from` row. `%loop-index%` is the key (`minecraft:diamond`), `%loop-value%` is the value. Empty outside the loop."
    },
    example: `loop {bag::%player%::*}:
	send "&f%loop-index% &8x%loop-value%" to player`,
    since: "0.3.8", related: ["struct-loop", "expr-variable", "screen-list"]
  },
  {
    id: "expr-variable", category: "expressions",
    name: { ru: "Переменные {имя}", en: "Variables {name}" },
    syntax: ["{<имя>}", "{<имя>::%player%}", "{<имя>::*}", "\"%{<имя>}%\""],
    desc: {
      ru: "Постоянные переменные в `scriptcraft/variables/variables.json`. В условиях и эффектах пишется `{coins::%player%}`. В тексте в кавычках фигурные скобки остаются как есть: значение подставляется только из `%{coins::%player%}%`. `{list::*}` — сколько прямых индексов. `set {list::*}` нельзя. `add ... to {list::*}` добавляет элемент, `remove ... from {list::*}` убирает одно равное значение.",
      en: "Persistent variables in `scriptcraft/variables/variables.json`. Conditions and effects use `{coins::%player%}`. Inside quoted text the braces stay as written: the value is inserted only from `%{coins::%player%}%.` `{list::*}` is how many direct indexes there are. `set {list::*}` is not allowed. `add ... to {list::*}` appends an element, `remove ... from {list::*}` deletes one equal value."
    },
    example: `add 1 to {coins::%player%}
send "&eМонет: %{coins::%player%}%" to player`,
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
  {
    id: "expr-property", category: "expressions",
    name: { ru: "Свойства игрока", en: "Player properties" },
    syntax: ["health of <цель>", "max health of <цель>", "food level of <цель>", "hunger of <цель>", "saturation of <цель>", "level of <цель>", "xp level of <цель>", "experience of <цель>", "air of <цель>", "oxygen of <цель>", "max air of <цель>", "absorption of <цель>", "walk speed of <цель>", "fly speed of <цель>", "flying speed of <цель>", "ping of <цель>", "x of <цель>", "y of <цель>", "z of <цель>", "yaw of <цель>", "pitch of <цель>", "gamemode of <цель>", "game mode of <цель>", "world of <цель>", "name of <цель>", "uuid of <цель>", "tool of <цель>", "held item of <цель>", "number of all players"],
    desc: {
      ru: "Значение свойства первого игрока цели (нет игрока — `<none>` / условие ложно). Пишется в `if`, в `set {переменная} to ...` и в `add ... to {переменная}`, а также в конце эффектов вроде `heal player by health of player`. `gamemode` — текст `survival`/`creative`/`adventure`/`spectator`. `held item` — id предмета в руке или `minecraft:air`. `number of all players` — сколько игроков онлайн (то же, что `number of online players`).",
      en: "The property of the first player of the target (no player — `<none>` / the condition is false). Use it in `if`, in `set {variable} to ...` and `add ... to {variable}`, and at the end of effects such as `heal player by health of player`. `gamemode` is the text `survival`/`creative`/`adventure`/`spectator`. `held item` is the id in the hand or `minecraft:air`. `number of all players` is the online count (same as `number of online players`)."
    },
    example: `set {hp::%player%} to health of player
if health of player < max health of player:
	heal player by 2
send "Мир: %{tmp}%" to player
set {tmp} to world of player
send "&7Онлайн: " to player
# number of all players — в условии:
if number of all players >= 5:
	broadcast "&aНа сервере людно"`,
    since: "0.2.0", related: ["eff-set-health", "cond-compare", "ph-online", "expr-random", "expr-location"]
  },
  {
    id: "expr-lp", category: "expressions",
    name: { ru: "Данные LuckPerms", en: "LuckPerms data" },
    syntax: ["prefix of <цель>", "suffix of <цель>", "group of <цель>", "groups of <цель>", "meta <ключ> of <цель>", "%prefix%", "%suffix%", "%group%", "%groups%", "%prefix of player%"],
    desc: {
      ru: "Читает LuckPerms: префикс, суффикс, основную группу, список групп через запятую, meta по ключу. Работает в `set`, `if`, аргументе функции и в тексте. `%prefix%`, `%suffix%`, `%group%`, `%groups%` — игрок из контекста. `%prefix of player%` тоже он. Внутри функции `prefix of who` — параметр, а `%prefix%` остаётся вызвавшим, если параметра с именем `prefix` нет. Параметр или переменная `prefix` перекрывает `%prefix%`. Нет LuckPerms или значения — `<none>`.",
      en: "Reads LuckPerms: prefix, suffix, primary group, a comma-separated group list, meta by key. Works in `set`, `if`, a function argument and in text. `%prefix%`, `%suffix%`, `%group%`, `%groups%` are the context player. `%prefix of player%` is that player too. Inside a function, `prefix of who` is the parameter, while `%prefix%` stays the caller unless a parameter named `prefix` exists. A parameter or variable named `prefix` shadows `%prefix%`. No LuckPerms or no value — `<none>`."
    },
    example: `send "%prefix%%player%" to player
set {rank} to group of player
if meta "color" of player is "gold":
	send "&6золото" to player`,
    since: "0.4.5", related: ["eff-lp-prefix", "eff-lp-group", "cond-group", "ph-player"]
  },
  {
    id: "expr-item-nbt", category: "expressions",
    name: { ru: "NBT предметов", en: "Item NBT" },
    syntax: ["nbt from items in player's inventory", "nbt of player's inventory", "%nbt from items in player's inventory%", "%nbt of slot <слот> of player%", "%nbt <путь> of slot <слот> of player%"],
    desc: {
      ru: "Весь инвентарь — SNBT-список непустых слотов с полем `Slot`. Пустой инвентарь — `[]`. Один слот — SNBT предмета, пустой слот — `<none>`. Путь читает тег предмета, как `%nbt CustomName of targeted entity%`. Слот — число, `hotbar 1` или выражение. В тексте: `%nbt from items in player's inventory%`, `%nbt of items in player's inventory%`, `%nbt of player's inventory%`, `%nbt of slot 0 of player%`, `%nbt Damage of slot 0 of player's inventory%`. В функции `nbt of slot 0 of who` читает параметр.",
      en: "The whole inventory is an SNBT list of non-empty stacks with a `Slot` field. An empty inventory is `[]`. One slot is the item SNBT, an empty slot is `<none>`. A path reads the item tag, like `%nbt CustomName of targeted entity%`. The slot is a number, `hotbar 1` or an expression. In text: `%nbt from items in player's inventory%`, `%nbt of items in player's inventory%`, `%nbt of player's inventory%`, `%nbt of slot 0 of player%`, `%nbt Damage of slot 0 of player's inventory%`. Inside a function, `nbt of slot 0 of who` reads the parameter."
    },
    example: `send "%nbt from items in player's inventory%" to player
set {tag} to nbt of slot 0 of player
send "%nbt Damage of slot 0 of player%" to player`,
    since: "0.4.5", related: ["eff-nbt", "eff-set-slot", "eff-give-item"]
  },
  {
    id: "expr-location", category: "expressions",
    name: { ru: "Локация и вектор смещения", en: "Location & vector offset" },
    syntax: [
      "location(<x>, <y>, <z>)",
      "location(<x>, <y>, <z>, world \"<мир>\")",
      "location(<x>, <y>, <z>, world \"<мир>\", <yaw>, <pitch>)",
      "location(<x>, <y>, <z>, <yaw>, <pitch>)",
      "location at <x> <y> <z> [in world \"<мир>\"]",
      "player's location",
      "location of <цель>",
      "<локация> ~~vector(<dx>, <dy>, <dz>)",
      "<локация> ~vector(<dx>, <dy>, <dz>)",
      "~~vector(<dx>, <dy>, <dz>)",
      "vector(<x>, <y>, <z>)",
      "spawn of <цель>",
      "world spawn",
      "%location%"
    ],
    desc: {
      ru: "Мир, координаты и углы поворота (`yaw`, `pitch`). Функция `location(x, y, z, [world \"мир\"], [yaw, pitch])` создаёт точку в любом мире с нужным поворотом камеры. Ко всем локациям (и целям) можно добавить смещение `~~vector(dx, dy, dz)` (или `~vector(...)`, `+ vector(...)`), а запись `~~vector(dx, dy, dz)` без базы смещает текущую точку игрока. Поддерживается везде: в `teleport`, `set block`, `spawn particle`, `spawn entity`, `play sound`, `set spawn`, `drop`, `strike lightning`, `explode` и в `set velocity`.",
      en: "A world, coordinates and rotation (`yaw`, `pitch`). `location(x, y, z, [world \"name\"], [yaw, pitch])` creates a point in any world with optional camera angles. Any location expression can be offset with `~~vector(dx, dy, dz)` (or `~vector(...)`, `+ vector(...)`), and a standalone `~~vector(dx, dy, dz)` offsets the context player's position. Works everywhere: `teleport`, `set block`, `spawn particle`, `spawn entity`, `play sound`, `set spawn`, `drop`, `strike lightning`, `explode` and `set velocity`."
    },
    example: `teleport player to location(8.651, -58, 51.543, world "lobby", -179.3, 0)
teleport player to player's location ~~vector(0.0, 5.0, 0.0)
spawn particle flame at location of targeted block ~~vector(0.0, 1.0, 0.0) count 25 speed 0.02
set block at ~~vector(0, -1, 0) to gold_block`,
    since: "0.3.10", related: ["eff-teleport", "eff-spawn", "eff-set-block", "eff-velocity", "expr-targeted-block", "expr-tool"]
  },
  {
    id: "expr-tool", category: "expressions",
    name: { ru: "Предмет в руке", en: "Held item" },
    syntax: ["player's tool", "tool of <цель>", "held item of <цель>", "%tool%"],
    desc: {
      ru: "Id предмета в руке (`minecraft:air`, если пусто). `player` и `loop-player` в значении — имя игрока, не предмет. В тексте `%tool%` берётся у игрока из контекста.",
      en: "The id of the held item (`minecraft:air` if empty). As a value, `player` and `loop-player` are the player's name, not an item. In text, `%tool%` uses the context player."
    },
    example: `add player's tool to {tools::%player%::*}
give player player's tool`,
    since: "0.3.10", related: ["expr-location", "eff-give-item", "eff-add-var"]
  },
  {
    id: "expr-targeted-block", category: "expressions",
    name: { ru: "Блок в прицеле", en: "Targeted block" },
    syntax: ["targeted block", "location of targeted block", "targeted location"],
    desc: {
      ru: "`targeted block` — id блока в прицеле. `location of targeted block` и `targeted location` — точка, куда смотрит игрок.",
      en: "`targeted block` is the block id in the crosshair. `location of targeted block` and `targeted location` are the point the player is looking at."
    },
    example: `add targeted block {seen::%player%::*}
teleport player to location of targeted block`,
    since: "0.3.10", related: ["expr-targeted-entity", "expr-targeted-player", "expr-location", "target-targeted"]
  },
  {
    id: "expr-targeted-entity", category: "expressions",
    name: { ru: "Сущность в прицеле", en: "Targeted entity" },
    syntax: ["targeted entity", "location of targeted entity"],
    desc: {
      ru: "`targeted entity` — id типа моба в прицеле. `location of targeted entity` — его точка.",
      en: "`targeted entity` is the type id of the mob in the crosshair. `location of targeted entity` is its point."
    },
    example: `set {mob} to targeted entity`,
    since: "0.3.10", related: ["expr-targeted-block", "expr-targeted-player", "ph-targeted"]
  },
  {
    id: "expr-targeted-player", category: "expressions",
    name: { ru: "Игрок в прицеле", en: "Targeted player" },
    syntax: ["targeted player", "location of targeted player"],
    desc: {
      ru: "`targeted player` — имя игрока в прицеле. `location of targeted player` — его точка.",
      en: "`targeted player` is the name of the player in the crosshair. `location of targeted player` is their point."
    },
    example: `set {who} to targeted player
send "Ты смотришь на %{who}%" to player`,
    since: "0.3.10", related: ["expr-targeted-block", "expr-targeted-entity", "ph-targeted", "target-targeted"]
  },
  {
    id: "expr-random", category: "expressions",
    name: { ru: "Случайное число", en: "Random number" },
    syntax: ["random integer between <A> and <B>", "random int between <A> and <B>", "random number between <A> and <B>"],
    desc: {
      ru: "`integer` — целое от меньшего к большему, включительно. `number` — дробное в том же промежутке. Границы могут быть выражениями (`health of player`).",
      en: "`integer` is a whole number from the lower bound to the upper, inclusive. `number` is a fraction in the same range. The bounds may be expressions (`health of player`)."
    },
    example: `set {roll} to random integer between 1 and 6
if {roll} is 6:
	give player diamond 1
set {bonus} to random integer between 1 and 4
give {bonus} levels to player`,
    since: "0.2.0", related: ["expr-property", "struct-chance", "eff-xp", "eff-give-levels"]
  },
  {
    id: "expr-script", category: "expressions",
    name: { ru: "Имя скрипта", en: "Script name" },
    syntax: ["script", "%script%"],
    desc: {
      ru: "Имя файла, чей код сейчас выполняется, без пути и без .sc. Пустая строка у эффект-команды в чате. Работает в тексте, в set и в условии.",
      en: "The file whose code is running, without the path and without .sc. Empty for a chat effect command. Works in text, in set and in a condition."
    },
    example: `send "&7Это скрипт %script%" to player
set {from} to script`,
    since: "0.3.4", related: ["struct-command", "expr-argument"]
  },
  {
    id: "expr-argument", category: "expressions",
    name: { ru: "Аргумент команды", en: "Command argument" },
    syntax: ["arg-1", "argument 1", "%arg-1%", "player-argument", "text-argument", "integer-argument", "number-argument", "boolean-argument", "date-argument", "world-argument", "item-argument", "offline player-argument"],
    desc: {
      ru: "Значение аргумента текущей команды. arg-1 — первый, argument 2 — второй. player-argument и остальные — первый аргумент этого типа. Число остаётся числом, true/false — логическим значением, игрок — его именем (heal arg-1 лечит этого игрока). date понимает today, tomorrow, yesterday и ГГГГ-ММ-ДД. Нет аргумента — <none>.",
      en: "A value of the current command. arg-1 is the first, argument 2 is the second. player-argument and the other type forms are the first argument of that type. A number stays a number, true/false stays a boolean, a player is their name (heal arg-1 heals that player). date accepts today, tomorrow, yesterday and YYYY-MM-DD. A missing argument is <none>."
    },
    example: `command /pay <player> <integer> [text]:
	permission: 0
	aliases: payme
	remove arg-2 from {coins::%player%}
	add arg-2 to {coins::arg-1}
	send "&a%arg-3%" to arg-1`,
    since: "0.3.4", related: ["struct-command", "expr-script", "ph-args"]
  },
  {
    id: "ph-event", category: "expressions",
    name: { ru: "Плейсхолдеры событий", en: "Event placeholders" },
    syntax: ["%message%", "%block%", "%item%", "%x%", "%y%", "%z%", "%attacker%", "%cause%", "%damage%", "%world%", "%from%", "%gamemode%"],
    desc: {
      ru: "Появляются только в своём событии. `on chat` — `%message%`. `on break`, `on place`, `on right click` и `on left click` — `%block%`, `%x%`, `%y%`, `%z%`. `on drop` и `on pickup` — `%item%`. `on death` и `on damage` — `%attacker%` и `%cause%`, у `on damage` ещё `%damage%`. `on world change` — `%from%` и `%world%`. `on gamemode change` — `%from%` и `%gamemode%`. Вне события плейсхолдер остаётся текстом `%message%`.",
      en: "Present only inside their own event. `on chat` — `%message%`. `on break`, `on place`, `on right click` and `on left click` — `%block%`, `%x%`, `%y%`, `%z%`. `on drop` and `on pickup` — `%item%`. `on death` and `on damage` — `%attacker%` and `%cause%`, and `on damage` also has `%damage%`. `on world change` — `%from%` and `%world%`. `on gamemode change` — `%from%` and `%gamemode%`. Outside the event the placeholder stays as the text `%message%`."
    },
    example: `on chat:
	log "%player%: %message%"

on break:
	send "%block% @ %x% %y% %z%" to player`,
    since: "0.2.0", related: ["event-chat", "event-break", "event-place", "event-right-click", "event-left-click", "event-drop", "event-pickup", "event-damage", "event-death", "event-world-change", "event-gamemode"]
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
    syntax: ["text \"<текст>\" [at <X> <Y>] [anchor <точка>] [color #RRGGBB] [shadow]", "text \"<текст>\" run \"/<команда>\"", "text \"<текст>\" run <Функция>(<аргументы>)", "text \"<текст>\" close"],
    desc: {
      ru: "Надпись. `color` — `#RRGGBB` или `#AARRGGBB`, `shadow` — с тенью. Плейсхолдеры и `{переменные}` подставляются при открытии. `run` (команда `\"/cmd\"` или функция `MyFunc(player)`), `function MyFunc(player)` или `close` делают хитбокс: ЛКМ по тексту (или по `size`, если задан) выполняет действие и закрывает экран. На HUD клика нет.",
      en: "A label. `color` is `#RRGGBB` or `#AARRGGBB`, `shadow` adds a shadow. Placeholders and `{variables}` are filled in when opening. `run` (a `\"/cmd\"` command or a `MyFunc(player)` function call), `function MyFunc(player)` or `close` adds a hitbox: left click on the text (or on `size`, if set) runs the action and closes the screen. HUD elements are not clickable."
    },
    example: `text "Добро пожаловать, %player%!" at 0, -60 color #FFD700 shadow
text "&aПолучить бонус" at 0, -40 run GiveBonus(player)`,
    since: "0.1.0", related: ["eff-open-screen", "screen-layout", "struct-function"]
  },
  {
    id: "screen-button", category: "screen",
    name: { ru: "Кнопка", en: "Button" },
    syntax: [
      "button \"<надпись>\" [at <X> <Y>] [size <W>x<H>] [anchor <точка>] [key <клавиша>] run \"/<команда>\" [keep open]",
      "button \"<надпись>\" [at <X> <Y>] [size <W>x<H>] [anchor <точка>] [key <клавиша>] run <Функция>(<аргументы>) [keep open]",
      "button \"<надпись>\" [at <X> <Y>] [size <W>x<H>] [anchor <точка>] [id <имя>] [hidden] close"
    ],
    desc: {
      ru: "Кнопка (по умолчанию 100x20). `run \"/cmd\"` выполняет команду от игрока, `run MyFunc(player, 10)` (или `function MyFunc(...)`) вызывает функцию скрипта на сервере. По умолчанию клик закрывает экран (сохранено поведение для старых скриптов); если дописать **`keep open`**, команда или функция выполнится **без закрытия экрана**. Параметр **`key <клавиша>`** (или `hotkey <клавиша>`, например `key w`, `key up`, `key space`) назначает кнопке горячую клавишу в открытом окне (независимо от раскладки клавиатуры). `id <имя>` позволяет обновлять кнопку на лету, `hidden` — создать скрытой.",
      en: "A button (100x20 by default). `run \"/cmd\"` runs a command as the player, `run MyFunc(player, 10)` (or `function MyFunc(...)`) calls a script function on the server. By default clicking closes the screen (preserving backward compatibility); adding **`keep open`** runs the command or function **without closing the screen**. **`key <key>`** (or `hotkey <key>`, e.g. `key w`, `key up`, `key space`) binds a layout-independent physical hotkey to the button while the screen is open. `id <name>` allows live updates, `hidden` starts it hidden."
    },
    example: `button "Вверх (W)" at 0, -25 size 90x20 key w run Move(player, "up") keep open
button "Купить меч" at 0, 5 size 120x20 run BuyItem(player, "diamond_sword", 50) keep open
button "Закрыть" at 0, 35 size 120x20 close`,
    since: "0.1.0", related: ["eff-open-screen", "screen-layout", "struct-function", "event-key"]
  },
  {
    id: "screen-slot", category: "screen",
    name: { ru: "Слот с предметом", en: "Item slot" },
    syntax: ["slot <предмет> [<количество>] [at <X> <Y>] [anchor <точка>]", "slot <предмет> ... run <Функция>(<аргументы>)", "slot hotbar <1-9>", "slot image \"<картинка>\"", "slot text \"<текст>\""],
    desc: {
      ru: "Показывает предмет с подсказкой при наведении. Сам по себе слот ничего не берёт. В id можно дописать nbt, как в `give`. `run \"/команда\"`, `run MyFunc(player)` или `close` — хитбокс ЛКМ по прямоугольнику слота на `open screen` (на HUD клика нет). С 0.1.33 слот может показывать предмет из хотбара игрока, картинку или текст (см. «Слоты HUD»).",
      en: "Shows an item with a tooltip on hover. By itself the slot takes nothing. The id may include nbt, as in `give`. `run \"/command\"`, `run MyFunc(player)` or `close` is a left-click hitbox on the slot rectangle in `open screen` (HUD elements are not clickable). Since 0.1.33 a slot can also show an item from the player's hotbar, an image or a text (see “HUD slots”)."
    },
    example: `slot diamond 5 at -30, -15 run BuyItem(player, "diamond", 5)
slot example:ruby 4 at 60, -15`,
    since: "0.1.0", related: ["eff-open-screen", "hud-slot", "struct-function"]
  },
  {
    id: "screen-image", category: "screen",
    name: { ru: "Картинка", en: "Image" },
    syntax: ["image \"<ns>:<путь>.png\" [at <X> <Y>] [size <W>x<H>] [texture <W>x<H>] [anchor <точка>] [color #AARRGGBB] [run \"/<команда>\" | run <Функция>(<аргументы>) | close]"],
    desc: {
      ru: "Рисует текстуру. `texture` — реальный размер файла, `color` — оттенок. `run` (команда или функция) или `close` — хитбокс ЛКМ по `size` (без размера это 16x16) на `open screen`. Картинки из `scriptcraft/resources/assets/<ns>/textures/gui/` сервер отправляет игрокам сам, они показываются сразу.",
      en: "Draws a texture. `texture` is the real file size, `color` tints it. `run` (command or function) or `close` is a left-click hitbox on `size` (16x16 when size is omitted) in `open screen`. Images from `scriptcraft/resources/assets/<ns>/textures/gui/` are sent to players by the server and show up immediately."
    },
    example: `image "example:textures/gui/menu_banner.png" at 0, -85 size 128x32 texture 128x32`,
    since: "0.1.0", related: ["eff-open-screen", "cmd-reload", "struct-function"]
  },
  {
    id: "screen-layout", category: "screen",
    name: { ru: "Позиция и привязка", en: "Position and anchor" },
    syntax: ["at <X> <Y>", "anchor center", "anchor top_left", "anchor top", "anchor top_right", "anchor left", "anchor right", "anchor bottom_left", "anchor bottom", "anchor bottom_right"],
    desc: {
      ru: "Элемент выравнивается по точке привязки экрана (по умолчанию `center`) и сдвигается на X Y пикселей (минус — влево/вверх).",
      en: "The element is aligned to an anchor point of the screen (`center` by default) and shifted by X Y pixels (negative = left/up)."
    },
    example: `text "ScriptCraft" at -4, -4 anchor bottom_right color #808080`,
    since: "0.1.0", related: ["screen-text", "screen-button", "hud-layer"]
  },
  {
    id: "screen-click", category: "screen",
    name: { ru: "Хитбокс клика", en: "Click hitbox" },
    syntax: ["text \"<текст>\" run \"/<команда>\"", "text \"<текст>\" run <Функция>(<аргументы>)", "image \"<путь>\" size <W>x<H> run <Функция>(<аргументы>)", "slot <предмет> close"],
    desc: {
      ru: "ЛКМ по нарисованному элементу на `open screen`. Работает у `text`, `image` и `slot`: `run \"/команда\"` выполняет команду от игрока, `run MyFunc(player)` (или `function MyFunc(player)`) вызывает функцию скрипта на сервере, `close` только закрывает экран. Без `run`, `function` и `close` клика нет. Если у текста задан `size`, хитбокс — этот прямоугольник, иначе ширина надписи и высота строки. У картинки и слота — `size`, иначе 16x16 и 18x18. Попадает верхний `layer`. Кнопка по-прежнему виджет. На HUD клики не приходят.",
      en: "Left click on a drawn element in `open screen`. Works on `text`, `image` and `slot`: `run \"/command\"` runs the command as the player, `run MyFunc(player)` (or `function MyFunc(player)`) calls a script function on the server, `close` only closes the screen. Without `run`, `function` and `close` there is no click. If a text has `size`, that rectangle is the hitbox, otherwise the label width and the line height. An image or a slot uses `size`, or 16x16 and 18x18. The higher `layer` wins. A button is still a widget. HUD elements do not receive clicks."
    },
    example: `image "minecraft:textures/item/diamond.png" at -40, -20 size 32x32 texture 16x16 run GiveReward(player, "diamond")
text "Взять алмаз" at -40, 16 run GiveReward(player, "diamond")`,
    since: "0.3.7", related: ["screen-text", "screen-image", "screen-slot", "screen-list", "eff-open-screen", "struct-function"]
  },
  {
    id: "screen-list", category: "screen",
    name: { ru: "Список", en: "List" },
    syntax: [
      "list vertical [at <X> <Y>] [size <W>x<H>] [header \"<текст>\"] [select]:",
      "list horizontal [at <X> <Y>] [size <W>x<H>] [header \"<текст>\"] [select]:",
      "list from {<имя>::*} vertical [at <X> <Y>] [size <W>x<H>] [header \"<текст>\"] [select]:",
      "row \"<текст>\" run \"/<команда>\"",
      "row \"<текст>\" run <Функция>(<аргументы>)",
      "row \"<текст>\" close",
      "row \"<текст>\""
    ],
    desc: {
      ru: "Панель строк. `vertical` — столбец, `horizontal` — ряд. `header` — строка над списком. `select` подсвечивает строку под курсором. Клик по строке с `run` выполняет команду или вызывает функцию скрипта и закрывает экран. `list from {имя::*}` в момент открытия строит строку на каждый прямой индекс: одна `row` — шаблон с `%loop-index%` и `%loop-value%`. Пустой список рисует только заголовок. Колёсико листает, если строк больше, чем влезает в `size`. На HUD список только рисуется и тоже снимок.",
      en: "A panel of rows. `vertical` is a column, `horizontal` is a row. `header` is a line above the list. `select` highlights the row under the cursor. A click on a row with `run` runs the command or calls a script function and closes the screen. `list from {name::*}` builds one row per direct index when the screen opens: the single `row` is a template with `%loop-index%` and `%loop-value%`. An empty list draws only the header. The wheel scrolls when the rows do not fit in `size`. On a HUD the list is display only, and it is a snapshot too."
    },
    example: `list from {bag::%player%::*} vertical at 0, 0 size 180x160 header "&7Предмет" select:
	row "&f%loop-index% &8x%loop-value%" run TakeFromBag(player, "%loop-index%") keep open`,
    since: "0.3.7", related: ["screen-click", "eff-open-screen", "event-open", "expr-variable", "ph-loop-index", "struct-function"]
  },
  {
    id: "screen-dynamic", category: "screen",
    name: { ru: "Динамическое обновление экрана", en: "Dynamic screen updates" },
    syntax: [
      "update screen [\"<заголовок>\"] [for <цель>]:",
      "set text of screen \"<id>\" [for <цель>] to \"<текст>\"",
      "set image of screen \"<id>\" [for <цель>] to \"<текстура>\"",
      "set position of screen \"<id>\" [for <цель>] to <X> <Y>",
      "move screen \"<id>\" [for <цель>] to <X> <Y>",
      "set item of screen \"<id>\" [for <цель>] to <предмет> [<кол-во>]",
      "set value of screen bar \"<id>\" [for <цель>] to <значение>",
      "show screen \"<id>\" [for <цель>]",
      "hide screen \"<id>\" [for <цель>]",
      "remove screen \"<id>\" [from <цель>]",
      "clear screen [of <цель>]",
      "close screen [of <цель>]"
    ],
    desc: {
      ru: "Обновляет уже открытый кастомный экран игрока на лету **без повторного `open screen` и без пересоздания окна** (фокус, прокрутка и зажатые клавиши сохраняются). Блок `update screen for player:` добавляет новые элементы или заменяет существующие элементы с тем же `id`. Точечные эффекты изменяют текст, картинку, координаты (`at X Y`), предмет слота, значение полосы и видимость (`show`/`hide`) элемента по его `id`, либо удаляют элемент (`remove screen \"id\"`). Если новое состояние элемента или экрана не отличается от текущего, пакет по сети не отправляется. Для проверки открытого экрана используйте условия `if player has screen open:`, `if player has screen \"Меню\" open:`, `if player is in screen \"Меню\":` или выражение `screen of player` / `%screen%`.",
      en: "Updates a player's currently open custom screen on the fly **without re-running `open screen` and without recreating the GUI** (focus, scroll and held keys are preserved). The `update screen for player:` block adds new elements or replaces existing elements with the same `id`. Targeted effects change the text, texture, coordinates (`at X Y`), slot item, bar value or visibility (`show`/`hide`) of an element by its `id`, or remove it (`remove screen \"id\"`). If the new state is identical to the current one, no network packet is sent. Check the active screen with `if player has screen open:`, `if player has screen \"Menu\" open:`, `if player is in screen \"Menu\":` or the `screen of player` / `%screen%` expression."
    },
    example: `set text of screen "score" for player to "&eОчки: &f%{score::%player%}%"
move screen "head" for player to {x::%player%} {y::%player%}
update screen for player:
	image "example:textures/gui/coin.png" id "apple" at {ax} {ay} size 12x12`,
    since: "0.6.2", related: ["eff-open-screen", "screen-button", "event-screen-open", "event-key"]
  },

  // ================================================================== UI / HUD
  {
    id: "hud-create", category: "hud",
    name: { ru: "Создать HUD", en: "Create HUD" },
    syntax: ["create hud \"<группа>\":", "create hud \"<группа>\" for <цель>:"],
    desc: {
      ru: "Рисует поверх экрана игры группу элементов, перечисленных с отступом ниже: `text`, `image`, `slot`, `bar`, `hotbar` (кнопок нет — HUD не принимает клики). Имя группы — латиница. Повторный `create hud` с тем же именем заменяет группу целиком (если ничего не изменилось — пакет не отправляется). Если в тексте, `value` или `max` есть `{переменная}`, HUD обновляет её сам в момент записи: пакет уходит только этому игроку и только если строка изменилась. HUD хранится на сервере: после перезахода игрок получает его снова, при остановке сервера он очищается. `/sc reload` и `/sc reload <скрипт>` заново применяют HUD из `on load` и `on join` этого скрипта игрокам, которые уже онлайн: `create hud for player` без игрока в контексте идёт всем онлайн. Группа запоминает создавший её скрипт: `/sc disable` этого скрипта убирает её у всех. До 128 элементов на игрока.",
      en: "Draws a group of elements over the game screen, listed indented below: `text`, `image`, `slot`, `bar`, `hotbar` (no buttons — the HUD takes no clicks). The group name is Latin. Running `create hud` again with the same name replaces the whole group (nothing is sent if nothing changed). If a text, `value` or `max` contains a `{variable}`, the HUD updates it when that variable is written: a packet goes only to that player, and only if the text changed. The HUD is kept on the server: players get it back after re-joining, and it is cleared when the server stops. `/sc reload` and `/sc reload <script>` reapply that script's HUD from `on load` and `on join` for players already online: `create hud for player` with no player in context goes to everyone online. A group remembers the script that created it: `/sc disable` of that script removes it for everyone. Up to 128 elements per player."
    },
    example: `on join or load:
	create hud "stats" for player:
		hotbar color #FFD700
		bar "hp" value health at -51, -28 size 80x8 anchor bottom color #E53935
		text "%health%/%max_health%" at -51, -38 anchor bottom shadow layer 3`,
    since: "0.1.33", related: ["hud-bar", "hud-hotbar", "hud-slot", "hud-layer", "hud-show-hide", "eff-open-screen"]
  },
  {
    id: "hud-bar", category: "hud",
    name: { ru: "Полоса прогресса", en: "Progress bar" },
    syntax: [
      "bar [\"<имя>\"] [value <значение>] [max <значение>] [direction left_to_right|right_to_left|bottom_to_top|top_to_bottom]",
      "    [size <W>x<H>] [color #заполнение] [background #цвет|\"картинка\"|none] [fill \"картинка\"] [texture <W>x<H>] [text \"<надпись>\"] [shadow]"
    ],
    desc: {
      ru: "Горизонтальная или вертикальная полоса (по умолчанию 80x8, слева направо). `value`/`max` — число, `{переменная}` или источник клиента (`health`, `food`…, см. «Источники полос»). Если `max` не указан, он подбирается: `health` → `max_health`, `food` → 20, `air` → `max_air`, иначе 100. `background` — цвет или картинка-фон/рамка, `fill` — картинка заполнения: она обрезается по прогрессу, а не растягивается. `text` рисуется по центру полосы, в нём работают `%value%`, `%max%`, `%percent%`. Работает и в HUD, и в `open screen`. `progress` — синоним `bar`.",
      en: "A horizontal or vertical bar (80x8, left to right by default). `value`/`max` is a number, a `{variable}` or a client source (`health`, `food`…, see “Bar sources”). Without `max` a sensible one is picked: `health` → `max_health`, `food` → 20, `air` → `max_air`, otherwise 100. `background` is a colour or a background/frame image, `fill` is the fill image: it is cropped to the progress, not stretched. `text` is drawn centred on the bar and supports `%value%`, `%max%`, `%percent%`. Works both in the HUD and in `open screen`. `progress` is an alias of `bar`."
    },
    example: `bar "hp" value health size 80x8 anchor bottom at -51, -28 fill "example:textures/gui/bar_fill_hp.png" background #A0000000 text "%value%/%max%"
bar "air" value air direction bottom_to_top size 6x40 anchor right at -8, 0 color #55AAFF`,
    since: "0.1.33", related: ["hud-bar-sources", "hud-set-bar", "hud-layer", "hud-create"]
  },
  {
    id: "hud-bar-sources", category: "hud",
    name: { ru: "Источники полос и плейсхолдеры HUD", en: "Bar sources and HUD placeholders" },
    syntax: [
      "health | max_health | absorption | food | saturation | armor | air | max_air | xp | level",
      "%health% %food% ... | %value% %max% %percent%"
    ],
    desc: {
      ru: "Если `value`/`max` полосы — имя источника, клиент сам берёт значение у игрока каждый кадр: полоса HP всегда актуальна без пакетов и без событий. `xp` — прогресс уровня от 0 до 1. Те же имена работают как плейсхолдеры `%health%` в любом тексте HUD и экранов. Внутри `text` полосы ещё есть `%value%`, `%max%`, `%percent%`. `{переменная}` в `value`, `max` или тексте обновляется сервером в момент записи: один пакет этому игроку, и только если число или текст изменились. Открытый `open screen` так не обновляется — это снимок на момент открытия.",
      en: "If a bar's `value`/`max` is a source name, the client reads it from the player every frame: the HP bar is always current with no packets or events. `xp` is the level progress from 0 to 1. The same names work as `%health%`-style placeholders in any HUD or screen text. Inside a bar's `text` there are also `%value%`, `%max%`, `%percent%`. A `{variable}` in `value`, `max` or text is refreshed by the server when it is written: one packet to that player, and only if the number or text changed. An open `open screen` is not refreshed — it is a snapshot from when it opened."
    },
    example: `bar "food" value food direction right_to_left text "%percent%%"
text "❤ %health%/%max_health%  🛡 %armor%" anchor top_left at 4, 4`,
    since: "0.1.33", related: ["hud-bar", "hud-set-text"]
  },
  {
    id: "hud-hotbar", category: "hud",
    name: { ru: "Свой хотбар", en: "Custom hotbar" },
    syntax: ["hotbar [at <X> <Y>] [anchor <точка>] [size <W>x<H>] [spacing <N>] [color #выделение] [no frame] [id <имя>] [layer <N>]"],
    desc: {
      ru: "Ряд из 9 слотов с настоящими предметами хотбара игрока; выбранный слот обводится цветом `color`. По умолчанию — внизу по центру, как ванильный. `spacing` — шаг между слотами (по умолчанию размер слота + 2), `size` — размер слота (по умолчанию 18). С `id hb` слоты получают имена `hb1`…`hb9`. Ванильный хотбар прячется отдельно: `hide vanilla hotbar`.",
      en: "A row of 9 slots showing the player's real hotbar items; the selected slot is outlined in `color`. By default it sits at the bottom centre like the vanilla one. `spacing` is the step between slots (slot size + 2 by default), `size` is the slot size (18 by default). With `id hb` the slots are named `hb1`…`hb9`. Hide the vanilla hotbar separately: `hide vanilla hotbar`."
    },
    example: `create hud "bar" for player:
	hotbar anchor left at 4, 0 spacing 20 color #55FF55
hide vanilla hotbar for player`,
    since: "0.1.33", related: ["hud-slot", "hud-vanilla", "hud-create"]
  },
  {
    id: "hud-slot", category: "hud",
    name: { ru: "Слоты HUD: предмет, хотбар, картинка, текст", en: "HUD slots: item, hotbar, image, text" },
    syntax: [
      "slot <предмет> [<количество>]",
      "slot hotbar <1-9>",
      "slot image \"<ns>:<путь>.png\"",
      "slot text \"<текст>\"",
      "... [at <X> <Y>] [anchor <точка>] [size <W>x<H>] [color #выделение] [no frame] [id <имя>] [layer <N>]"
    ],
    desc: {
      ru: "Слот (18x18 с рамкой) может содержать предмет, предмет из слота хотбара игрока, картинку или короткий текст. `no frame` — без рамки, `size` масштабирует содержимое. Работает и в HUD, и в `open screen`.",
      en: "A slot (18x18 with a frame) can hold an item, the item from one of the player's hotbar slots, an image or a short text. `no frame` removes the frame, `size` scales the content. Works both in the HUD and in `open screen`."
    },
    example: `slot image "example:textures/gui/coin.png" at 4, 4 anchor top_left
slot text "Q" at 24, 4 anchor top_left
slot hotbar 1 at 0, -40 anchor bottom size 24x24`,
    since: "0.1.33", related: ["hud-hotbar", "screen-slot", "hud-create"]
  },
  {
    id: "hud-layer", category: "hud",
    name: { ru: "Слои (layer) и имена (id)", en: "Layers (layer) and names (id)" },
    syntax: ["layer <N>", "id <имя>"],
    desc: {
      ru: "У любого элемента HUD и экрана: `layer` — порядок отрисовки (больше = выше, по умолчанию 0; при равных слоях — по порядку в скрипте). Элементы с отрицательным слоем в HUD рисуются под ванильным HUD. Предметы в слотах тоже подчиняются слоям: картинка со слоем выше перекроет предмет. `id` — имя элемента для `set value/text`, `show/hide/remove hud` (у `bar \"имя\"` имя задаётся сразу). Элементы без имени управляются через имя группы.",
      en: "On any HUD or screen element: `layer` is the drawing order (higher = on top, 0 by default; equal layers keep script order). HUD elements with a negative layer are drawn below the vanilla HUD. Items in slots follow layers too: an image on a higher layer covers the item. `id` names the element for `set value/text`, `show/hide/remove hud` (`bar \"name\"` sets it directly). Unnamed elements are controlled through the group name."
    },
    example: `bar "hp" value health size 80x8 anchor bottom at 0, -28 layer 1
image "example:textures/gui/bar_frame.png" size 84x12 anchor bottom at 0, -26 layer 2
text "HP" anchor bottom at 0, -38 layer 3 id hp_label`,
    since: "0.1.33", related: ["hud-create", "screen-layout", "eff-open-screen"]
  },
  {
    id: "hud-set-bar", category: "hud",
    name: { ru: "Значение и максимум полосы", en: "Bar value and max" },
    syntax: ["set value of bar \"<имя>\" [for <цель>] to <значение>", "set progress of bar \"<имя>\" [for <цель>] to <значение>", "set max of bar \"<имя>\" [for <цель>] to <значение>"],
    desc: {
      ru: "Меняет значение или максимум полосы: число, `{переменная}` или источник (`health`, `food`…). Без `for` — игроку из контекста. Клиенту уходит только изменение, и только если значение действительно поменялось.",
      en: "Changes a bar's value or max: a number, a `{variable}` or a source (`health`, `food`…). Without `for` it targets the context player. Only the change is sent, and only if the value actually changed."
    },
    example: `set progress of bar "boss_hp" for all players to 75
set max of bar "coins_goal" to 200
set value of bar "coins_goal" for player to {coins::%player%}`,
    since: "0.1.33", related: ["hud-bar", "hud-bar-sources", "hud-set-text"]
  },
  {
    id: "hud-set-text", category: "hud",
    name: { ru: "Текст элемента HUD", en: "HUD element text" },
    syntax: ["set text of hud \"<имя>\" [for <цель>] to \"<текст>\""],
    desc: {
      ru: "Меняет текст у `text`, `slot text` или надпись на полосе `bar`. `%player%` подставляется для игрока из контекста. Если в тексте есть `{переменная}`, элемент и дальше следит за ней. Текст без переменных эту связь снимает.",
      en: "Changes the text of a `text`, a `slot text` or the label on a `bar`. `%player%` is filled in for the context player. If the text contains a `{variable}`, the element keeps following it. Text without a variable drops that link."
    },
    example: `set text of hud "coins_text" for player to "%{coins::%player%}%"`,
    since: "0.1.33", related: ["hud-set-bar", "hud-layer"]
  },
  {
    id: "hud-show-hide", category: "hud",
    name: { ru: "Показать, скрыть, удалить HUD", en: "Show, hide, remove HUD" },
    syntax: [
      "show hud \"<имя>\" [to <цель>]",
      "hide hud \"<имя>\" [from <цель>]",
      "remove hud \"<имя>\" [from <цель>]",
      "clear hud [for <цель>]"
    ],
    desc: {
      ru: "`<имя>` — имя элемента или целой группы из `create hud`. `hide` прячет, но хранит (вернуть — `show`), `remove` удаляет насовсем, `clear hud` убирает весь свой HUD игрока (ванильные части не трогает).",
      en: "`<name>` is an element name or a whole group from `create hud`. `hide` hides but keeps it (bring it back with `show`), `remove` deletes it, `clear hud` removes all of the player's custom HUD (vanilla parts are not touched)."
    },
    example: `hide hud "stats" from player
show hud "stats" to player
remove hud "boss" from all players
clear hud for player`,
    since: "0.1.33", related: ["hud-create", "hud-vanilla"]
  },
  {
    id: "hud-vanilla", category: "hud",
    name: { ru: "Скрыть ванильный HUD", en: "Hide vanilla HUD" },
    syntax: [
      "hide vanilla <часть> [for <цель>]",
      "show vanilla <часть> [for <цель>]",
      "<часть>: hotbar, health, food, armor, air, experience, mount_health, jump_bar, crosshair, effects, item_name, scoreboard, bossbar, hud (всё)"
    ],
    desc: {
      ru: "Прячет или возвращает части ванильного HUD. Синонимы: `hearts`, `hunger`, `xp`, `potions`, `boss_bar`, `all`. Состояние хранится на сервере вместе с HUD и восстанавливается при входе. Каждый скрипт прячет «свои» части: `/sc disable` скрипта возвращает их (если их не прячет другой скрипт); `show vanilla` возвращает часть независимо от того, кто её спрятал.",
      en: "Hides or restores parts of the vanilla HUD. Aliases: `hearts`, `hunger`, `xp`, `potions`, `boss_bar`, `all`. The state is kept on the server with the HUD and restored on join. Each script hides “its own” parts: `/sc disable` of the script restores them (unless another script hides them too); `show vanilla` restores a part no matter who hid it."
    },
    example: `hide vanilla hotbar for player
hide vanilla health for player
show vanilla all for player`,
    since: "0.1.33", related: ["hud-hotbar", "hud-show-hide"]
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
    since: "0.1.0", related: ["cmd-reload", "cmd-journal"]
  },
  {
    id: "cmd-journal", category: "commands",
    name: { ru: "Студия", en: "Studio" },
    syntax: ["F3 + `"],
	desc: {
	  ru: "Окно мода. Его открывают и правят оператор или игрок с правом scriptcraft.studio. В конфиге studio.requireOp = false студия открыта всем. Клавиша не видна в «Управлении»: по умолчанию F3 и ` (на русской раскладке это Ё). Пока игрок не вышел с сервера, вкладки и размер окна помнятся; выход сбрасывает их. Главная по центру показывает крупный заголовок ScriptCraft, сменяющиеся фразы и модель скина ElgerElg. Журнал делится на активные ошибки и историю (перезагрузки, старые ошибки, уведомления). Скрипт удаляется из меню «Файл» или с панели, после подтверждения. Закрытие вкладки с несохранённым текстом тоже спрашивает; закрытие всего окна ничего не спрашивает и оставляет вкладки на клиенте. Слева от имени вкладки карандаш, если текст отличается от последней сохранённой версии. Tab вставляет таб и рисуется тонкой палкой. Ctrl+Backspace стирает слово назад, Ctrl+Delete — вперёд, до пробела или отступа. Ctrl и стрелки переходят к соседнему слову. Колесо листает текст и не держит строку курсора. Alt и левая кнопка выделяют прямоугольник: набор и удаление идут в каждую строку. Уведомления растут по высоте текста и показывают серую полоску оставшегося времени. Клиент и сервер должны быть одной версии: протокол сети 12. Совместная правка studio.liveEdit по умолчанию выключена. Если её включить, в одном скрипте видны чужой курсор, выделение и набор, у курсора маленькая голова скина, а ник — при наведении на строку. Сохранение принимается только от последней копии: побеждает последнее сохранение или последняя принятая правка.",
      en: "Mod window for an operator or a player with the scriptcraft.studio permission. studio.requireOp = false opens it for everyone. The key is not listed in Controls: default is F3 and ` (Ё on a Russian layout). Tabs and the window size stay until the player leaves the server; logout clears them. Home centers a large ScriptCraft title, rotating phrases, and ElgerElg's skin model. The journal splits into active errors and a history of reloads, old errors, and notices. A script is deleted from the File menu or the toolbar after a confirmation. Closing a tab with unsaved text also asks; closing the whole window does not ask and keeps the tabs on the client. A pencil sits left of a tab name when the text differs from the last saved copy. Tab inserts a tab drawn as a thin bar. Ctrl+Backspace deletes the word backward, Ctrl+Delete forward, up to a space or indent. Ctrl and the arrow keys jump to the next word. The wheel scrolls the text and does not pin the cursor line. Alt and the left button select a rectangle: typing and deleting apply to every line. Notices grow with the text and show a gray bar of the time left. Client and server must be the same version: the network protocol is 12. Shared editing, studio.liveEdit, is off by default. When it is on, the same script shows the other person's caret, selection and typing, a small skin head at the caret, and the name when the line is hovered. A save is accepted only from the latest copy: the last save or the last accepted edit wins."
	},
	example: "F3 + `",
    since: "0.2.2", related: ["cmd-reload", "cfg-studioLiveEdit", "cfg-studioRequireOp"]
  },
  {
    id: "cmd-reload", category: "commands",
    name: { ru: "/sc reload", en: "/sc reload" },
    syntax: ["/sc reload", "/sc reload config", "/sc reload resources", "/sc reload <скрипт>"],
    desc: {
      ru: "`/sc reload` перечитывает `config.toml`, `scriptcraft/resources` и все скрипты. `config` и `resources` — не имена скриптов: первое перечитывает только конфиг, второе — только ресурсы. `/sc reload <скрипт>` перезагружает один файл. Аргументы своих команд (`<player>`, `[text]` и остальные) собираются заново. Ошибки — в журнале (F3 + `).",
      en: "`/sc reload` rereads `config.toml`, `scriptcraft/resources` and every script. `config` and `resources` are not script names: the first rereads only the config, the second only resources. `/sc reload <script>` reloads one file. Arguments of script commands (`<player>`, `[text]` and the rest) are built again. Errors are in the journal (F3 + `)."
    },
    example: `/sc reload
/sc reload config
/sc reload resources
/sc reload welcome`,
    since: "0.1.0", related: ["cmd-list", "cmd-disable"]
  },
  {
    id: "cmd-disable", category: "commands",
    name: { ru: "/sc enable | disable", en: "/sc enable | disable" },
    syntax: ["/sc disable <скрипт>", "/sc enable <скрипт>"],
    desc: {
      ru: "`disable` выгружает скрипт и переименовывает файл в `-имя.sc`; `enable` возвращает имя и загружает (срабатывает `on load:`). При `disable` срабатывает `on unload:`, а HUD-группы и спрятанные ванильные части этого скрипта убираются у всех игроков. Команды выключенного скрипта остаются в списке до перезапуска и отвечают «выключена».",
      en: "`disable` unloads the script and renames the file to `-name.sc`; `enable` renames it back and loads it (fires `on load:`). `disable` fires `on unload:` and removes this script's HUD groups and hidden vanilla parts for all players. Commands of a disabled script stay registered until restart and reply “disabled”."
    },
    example: `/sc disable welcome
/sc enable welcome`,
    since: "0.1.0", related: ["cmd-reload", "event-load", "event-unload"]
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
    id: "cmd-ids", category: "commands",
    name: { ru: "/sc ids", en: "/sc ids" },
    syntax: ["/sc ids"],
    desc: {
      ru: "Показать id каждого `define`: «настоящий id» (зарегистрирован при запуске) или временный слот до перезапуска: `example:ruby_block -> scriptcraft:dyn_block_0`. Id слота виден в F3, нужен в датапаках из папки мира; в командах можно писать id скрипта — сервер подставит слот.",
      en: "Show each `define`'s id: «real id» (registered at startup) or a temporary slot until a restart: `example:ruby_block -> scriptcraft:dyn_block_0`. A slot id shows in F3 and is needed in world datapacks; commands accept the script id — the server substitutes the slot."
    },
    example: `/sc ids`,
    since: "0.1.7", related: ["struct-define-block", "struct-define-item", "cmd-give"]
  },
  {
    id: "cmd-give", category: "commands",
    name: { ru: "/sc give", en: "/sc give" },
    syntax: ["/sc give <игроки> <id> [количество]"],
    desc: {
      ru: "Выдать блок или предмет по id скрипта (`example:ruby_block`, короткий `ruby`) или любой обычный предмет. В отличие от ванильной `/give` (она с 0.1.8 тоже принимает `example:ruby_block`), понимает короткий id и подсказывает id скриптов при наборе. Полный инвентарь — предмет падает рядом.",
      en: "Give a block or item by its script id (`example:ruby_block`, short `ruby`) or any regular item. Unlike vanilla `/give` (which also accepts `example:ruby_block` since 0.1.8), it understands short ids and suggests script ids while typing. Full inventory — the item drops nearby."
    },
    example: `/sc give @s example:ruby_block 4`,
    since: "0.1.7", related: ["cmd-ids", "struct-define-block"]
  },
  {
    id: "cmd-effect-commands", category: "commands",
    name: { ru: "Эффект-команды в чате ?...", en: "Chat effect commands ?..." },
    syntax: ["?<строка скрипта>"],
    desc: {
      ru: "Сообщение в чате, начинающееся с токена (по умолчанию `?`), выполняется как одна строка скрипта от имени написавшего. На новом сервере включено. Если в конфиге уже стоит `enableEffectCommands = false`, `?` молчит, пока это не сменить и не сделать `/sc reload config`. На гибриде чат читается и через Bukkit. Право `scriptcraft.effectcommands` проверяет LuckPerms (мод или плагин) и Forge. Блоки (`if`, `loop`, `open screen`) не поддерживаются; пустая цель — сообщение «Нет целей».",
      en: "A chat message starting with the token (`?` by default) runs as one script line on behalf of the sender. On a new server it is on. If the config already has `enableEffectCommands = false`, `?` stays quiet until that is changed and `/sc reload config` is run. On a hybrid, chat is also read through Bukkit. The node `scriptcraft.effectcommands` is checked in LuckPerms (mod or plugin) and Forge. Blocks (`if`, `loop`, `open screen`) are not supported; an empty target replies “No targets”."
    },
    example: `?heal player
?send "привет" to targeted player
?give @a[distance=..20] diamond 1`,
    since: "0.1.2", related: ["cfg-enableEffectCommands", "cfg-effectCommandToken", "cfg-effectCommandsRequireOp"]
  },

  // ================================================================== КОНФИГ
  { id: "cfg-language", category: "config", name: { ru: "general.language", en: "general.language" },
    syntax: ["language = \"en\"", "language = \"ru\""],
    desc: { ru: "Язык сообщений мода, журнала и уведомлений. По умолчанию `\"en\"`. Меняется после `/sc reload config`.", en: "Language of mod messages, the journal and notices. Default `\"en\"`. Applies after `/sc reload config`." },
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
    example: `syncResourcesToClients = true`, since: "0.1.2", related: ["cmd-reload"] },
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
    desc: { ru: "Включить эффект-команды в чате (`?heal player`). На новом сервере включено. Уже записанный конфиг со значением `false` сам не меняется.", en: "Enable chat effect commands (`?heal player`). On for a new server. An existing config that says `false` is not changed by itself." },
    example: `enableEffectCommands = true`, since: "0.1.2", related: ["cmd-effect-commands"] },
  { id: "cfg-effectCommandToken", category: "config", name: { ru: "effectCommands.effectCommandToken", en: "effectCommands.effectCommandToken" },
    syntax: ["effectCommandToken = \"?\""],
    desc: { ru: "С чего начинается эффект-команда: до 8 символов, не `/`.", en: "Prefix of effect commands: up to 8 characters, not `/`." },
    example: `effectCommandToken = "!"`, since: "0.1.2", related: ["cmd-effect-commands"] },
  { id: "cfg-effectCommandsRequireOp", category: "config", name: { ru: "effectCommands.effectCommandsRequireOp", en: "effectCommands.effectCommandsRequireOp" },
    syntax: ["effectCommandsRequireOp = true"],
    desc: { ru: "Без мода прав эффект-команды только у операторов (уровень 2). `false` — у всех игроков (опасно).", en: "Without a permissions mod, effect commands are for operators (level 2) only. `false` = every player (dangerous)." },
    example: `effectCommandsRequireOp = true`, since: "0.1.2", related: ["cmd-effect-commands"] },
  { id: "cfg-studioLiveEdit", category: "config", name: { ru: "studio.liveEdit", en: "studio.liveEdit" },
    syntax: ["liveEdit = false"],
    desc: { ru: "Совместная правка студии. По умолчанию `false`: чужие курсоры не видны. `true` — в одном скрипте видны курсор, выделение и набор других людей с доступом к студии, у курсора маленькая голова скина, ник при наведении на строку. Сохранение принимается только от последней копии: побеждает последнее сохранение или последняя принятая правка. Уже записанный конфиг сам не включает этот ключ: поставьте `true` и выполните `/sc reload config`. Клиент и сервер оба должны быть 0.4.7.", en: "Shared studio editing. Default `false`: other carets stay hidden. `true` shows the caret, selection and typing of other people who can open the studio, a small skin head at the caret, and the name when the line is hovered. A save is accepted only from the latest copy: the last save or the last accepted edit wins. An existing config does not turn this on by itself: set `true` and run `/sc reload config`. Client and server both need 0.4.7." },
    example: `liveEdit = true`, since: "0.4.7", related: ["cmd-journal", "cfg-studioRequireOp"] },
  { id: "cfg-studioRequireOp", category: "config", name: { ru: "studio.requireOp", en: "studio.requireOp" },
    syntax: ["requireOp = true"],
    desc: { ru: "Кто открывает и правит студию. Узел права — `scriptcraft.studio` (его видно в LuckPerms после `/sc reload`, мод или плагин). `true` (по умолчанию): операторы и те, кому выдано это право. `false`: любой игрок. Уже записанный конфиг получает ключ при следующей загрузке, пока его нет — студия остаётся у операторов.", en: "Who may open and edit the studio. The node is `scriptcraft.studio` (LuckPerms lists it after `/sc reload`, mod or plugin). `true` (default): operators and anyone granted the node. `false`: every player. An existing config gains the key on the next load; until then the studio stays with operators." },
    example: `requireOp = true`, since: "0.4.7", related: ["cmd-journal", "cfg-studioLiveEdit"] },

  // ================================================================== НОВОВВЕДЕНИЯ 0.5.3
  { id: "event-kill", category: "events", name: { ru: "Убийство сущности / игрока", en: "Entity / player kill" },
    syntax: ["on kill:", "on entity death:"],
    desc: { ru: "Срабатывает, когда игрок убивает моба или другого игрока. Плейсхолдеры: `%victim%`, `%attacker%`, `%cause%`.", en: "Fires when a player kills a mob or another player. Placeholders: `%victim%`, `%attacker%`, `%cause%`." },
    example: `on kill:\n\tsend "&aВы убили %victim%!" to player`, since: "0.5.3", related: ["event-attack", "event-death"] },
  { id: "event-attack", category: "events", name: { ru: "Атака сущности", en: "Attack entity" },
    syntax: ["on attack:", "on hit:"],
    desc: { ru: "Срабатывает при ударе по мобу или игроку. Можно отменить (`cancel event`). Плейсхолдеры: `%victim%`, `%attacker%`.", en: "Fires when a player hits an entity. Cancelable. Placeholders: `%victim%`, `%attacker%`." },
    example: `on attack:\n\tif %victim% is "minecraft:villager":\n\t\tcancel event`, since: "0.5.3", related: ["event-kill", "event-damage"] },
  { id: "event-shoot", category: "events", name: { ru: "Выстрел из лука", en: "Bow shoot" },
    syntax: ["on shoot:", "on bow shoot:"],
    desc: { ru: "Срабатывает при выстреле из лука. Можно отменить. Плейсхолдер: `%projectile%`.", en: "Fires when shooting a bow. Cancelable. Placeholder: `%projectile%`." },
    example: `on shoot:\n\tspawn particle flame at player count 8`, since: "0.5.3", related: ["event-projectile-hit"] },
  { id: "event-projectile-hit", category: "events", name: { ru: "Попадание снаряда", en: "Projectile hit" },
    syntax: ["on projectile hit:"],
    desc: { ru: "Срабатывает при попадании снаряда игрока в блок или сущность. Плейсхолдеры: `%projectile%`, `%victim%`, `%block%`, `%x%`, `%y%`, `%z%`.", en: "Fires when a player's projectile hits a block or entity. Placeholders: `%projectile%`, `%victim%`, `%block%`, `%x%`, `%y%`, `%z%`." },
    example: `on projectile hit:\n\tcreate safe explosion at %x% %y% %z% with power 2`, since: "0.5.3", related: ["event-shoot", "eff-explosion"] },
  { id: "event-consume", category: "events", name: { ru: "Употребление еды / зелья", en: "Consume item" },
    syntax: ["on consume:", "on eat:", "on drink:"],
    desc: { ru: "Срабатывает, когда игрок доедает еду или допивает зелье. Плейсхолдер: `%item%`.", en: "Fires when a player finishes eating or drinking. Placeholder: `%item%`." },
    example: `on consume:\n\tsend "&7Вы съели %item%" to player`, since: "0.5.3", related: ["event-craft"] },
  { id: "event-craft", category: "events", name: { ru: "Крафт предмета", en: "Craft item" },
    syntax: ["on craft:"],
    desc: { ru: "Срабатывает при крафте предмета. Плейсхолдеры: `%item%`, `%amount%`.", en: "Fires when a player crafts an item. Placeholders: `%item%`, `%amount%`." },
    example: `on craft:\n\tsend "&aСкрафчено: %amount% x %item%" to player`, since: "0.5.3", related: ["event-consume"] },
  { id: "event-fish", category: "events", name: { ru: "Рыбалка", en: "Fishing" },
    syntax: ["on fish:"],
    desc: { ru: "Срабатывает при вылавливании предмета удочкой. Можно отменить. Плейсхолдер: `%item%`.", en: "Fires when catching an item with a fishing rod. Cancelable. Placeholder: `%item%`." },
    example: `on fish:\n\tsend "&bУлов: %item%" to player`, since: "0.5.3", related: [] },
  { id: "event-sneak", category: "events", name: { ru: "Приседание (Shift)", en: "Toggle sneak" },
    syntax: ["on sneak:", "on toggle sneak:"],
    desc: { ru: "Срабатывает при нажатии/отпускании Shift. Можно отменить (`cancel event`). Плейсхолдер: `%sneaking%` (`true` / `false`).", en: "Fires when sneaking toggles. Cancelable (`cancel event`). Placeholder: `%sneaking%` (`true` / `false`)." },
    example: `on sneak:\n\tif %sneaking% is "true":\n\t\tsend "&7Вы присели" to player`, since: "0.5.3", related: ["event-sprint", "event-jump"] },
  { id: "event-sprint", category: "events", name: { ru: "Бег (Sprint)", en: "Toggle sprint" },
    syntax: ["on sprint:", "on toggle sprint:"],
    desc: { ru: "Срабатывает при начале или окончании бега. Можно отменить (`cancel event`). Плейсхолдер: `%sprinting%`.", en: "Fires when sprinting toggles. Cancelable (`cancel event`). Placeholder: `%sprinting%`." },
    example: `on sprint:\n\tif %sprinting% is "true":\n\t\tcancel event`, since: "0.5.3", related: ["event-sneak", "event-jump"] },
  { id: "event-jump", category: "events", name: { ru: "Прыжок", en: "Player jump" },
    syntax: ["on jump:"],
    desc: { ru: "Срабатывает при прыжке игрока. Можно отменить (`cancel event`).", en: "Fires when a player jumps. Cancelable (`cancel event`)." },
    example: `on jump:\n\tcancel event`, since: "0.5.3", related: ["eff-push"] },
  { id: "event-command", category: "events", name: { ru: "Перехват команды", en: "Command event" },
    syntax: ["on command:"],
    desc: { ru: "Срабатывает перед выполнением любой команды игроком. Можно отменить (`cancel event`). Плейсхолдеры: `%command%`, `%args%`.", en: "Fires before any player command runs. Cancelable. Placeholders: `%command%`, `%args%`." },
    example: `on command:\n\tif %command% is "plugins":\n\t\tcancel event`, since: "0.5.3", related: [] },
  { id: "event-item-break", category: "events", name: { ru: "Поломка инструмента", en: "Item break" },
    syntax: ["on item break:"],
    desc: { ru: "Срабатывает, когда ломается инструмент или броня игрока. Плейсхолдер: `%item%`.", en: "Fires when a player's tool or armor breaks. Placeholder: `%item%`." },
    example: `on item break:\n\tsend "&cСломался предмет: %item%" to player`, since: "0.5.3", related: [] },

  { id: "expr-clicked", category: "expressions", name: { ru: "Кликнутая сущность / игрок / блок", en: "Clicked entity / player / block" },
    syntax: ["clicked entity", "clicked player", "clicked block"],
    desc: { ru: "Внутри `on right click:` возвращает id сущности, ник игрока или id блока, по которому кликнули. Если клик был не по сущности/игроку — значение не задано (`is not set`).", en: "Inside `on right click:`, returns the clicked entity id, player name, or block id. Unset if not applicable." },
    example: `on right click:\n\tif clicked player is set:\n\t\tsend "&eВы кликнули по игроку: %clicked_player%" to player\n\telse if clicked entity is set:\n\t\tsend "&7Сущность: %clicked_entity%" to player`, since: "0.5.3", related: [] },
  { id: "expr-math", category: "expressions", name: { ru: "Математика и случайный элемент", en: "Math & random element" },
    syntax: ["<a> + <b>", "<a> - <b>", "<a> * <b>", "<a> / <b>", "<a> % <b>", "<a> ^ <b>", "round(<x>)", "floor(<x>)", "ceil(<x>)", "abs(<x>)", "sqrt(<x>)", "min(<a>, <b>)", "max(<a>, <b>)", "clamp(<v>, <lo>, <hi>)", "random element out of {list::*}"],
    desc: { ru: "Полная арифметика с приоритетом операций и скобками `( ... )`, математические функции и выбор случайного элемента из списка `{list::*}`.", en: "Full arithmetic with precedence and parentheses `( ... )`, math functions, and picking a random element from a list variable `{list::*}`." },
    example: `set {dmg} to round((health of player + 4) * 1.5)\nset {prize} to random element out of {rewards::*}`, since: "0.5.3", related: [] },
  { id: "struct-loop-while", category: "structures", name: { ru: "Циклы loop N times и while", en: "loop N times & while loops" },
    syntax: ["loop <число> times:", "while <условие>:"],
    desc: { ru: "`loop N times:` повторяет блок N раз (текущий шаг в `%loop-number%`). `while <условие>:` выполняется, пока условие истинно (ограничено бюджетом шагов).", en: "`loop N times:` repeats N times (`%loop-number%`). `while <condition>:` repeats while true (bounded by step budget)." },
    example: `loop 5 times:\n\tsend "Шаг %loop-number%" to player`, since: "0.5.3", related: [] },

  { id: "eff-particle", category: "effects", name: { ru: "Частицы", en: "Spawn particles" },
    syntax: ["spawn particle <id> at <цель|локация|x y z> [count <N>] [speed <S>]"],
    desc: { ru: "Создаёт частицы в точке, у игрока или на локации. По умолчанию `count 10`, `speed 0.05`.", en: "Spawns particles at a target, location, or coordinates. Default `count 10`, `speed 0.05`." },
    example: `spawn particle flame at player count 25 speed 0.08\nspawn particle heart at location of targeted block count 10`, since: "0.5.3", related: ["eff-playsound-at"] },
  { id: "eff-scoreboard", category: "effects", name: { ru: "Скорборд (боковая панель)", en: "Scoreboard sidebar" },
    syntax: ["set scoreboard title \"...\" [for <цель>]", "set scoreboard line <N> to \"...\" [for <цель>]", "clear scoreboard [of <цель>]"],
    desc: { ru: "Персональный сайдбар-скорборд для каждого игрока с поддержкой цветов `&` и плейсхолдеров.", en: "Per-player sidebar scoreboard with `&` colors and placeholders." },
    example: `set scoreboard title "&6&lMyServer" for player\nset scoreboard line 2 to "&7Онлайн: &a%online%" for player\nset scoreboard line 1 to "&7Игрок: &f%player%" for player`, since: "0.5.3", related: [] },
  { id: "eff-push", category: "effects", name: { ru: "Толчок игрока (push)", en: "Push player" },
    syntax: ["push <цель> upwards|downwards|forwards|backwards|north|south|east|west [with force <N>]"],
    desc: { ru: "Добавляет импульс игроку в выбранном направлении — удобно для двойных прыжков, джамппадов и отбрасывания.", en: "Adds velocity impulse in a direction — great for double jumps, jump pads, and knockback." },
    example: `push player upwards with force 1.2\npush player forwards with force 1.8`, since: "0.5.3", related: [] },
  { id: "eff-spawn-entity", category: "effects", name: { ru: "Спавн сущности (с NBT и экипировкой)", en: "Spawn entity (NBT & gear)" },
    syntax: ["spawn entity \"<id>\" at <цель|локация|x y z> [named \"...\"] [with health <N>] [with nbt \"{...}\"] [with weapon|helmet|chestplate|leggings|boots <предмет>]"],
    desc: { ru: "Спавнит любую сущность в указанной точке с опциональным именем, здоровьем, произвольными NBT-тегами и экипировкой.", en: "Spawns any entity with optional custom name, max health, SNBT tags, and equipment." },
    example: `spawn entity "minecraft:zombie" at player named "&cБосс" with health 100 with weapon diamond_sword with nbt "{Glowing:1b}"`, since: "0.5.3", related: [] },
  { id: "eff-lightning-fake", category: "effects", name: { ru: "Декоративная молния (без урона)", en: "Fake / visual lightning" },
    syntax: ["strike fake|effect|visual lightning at <цель|локация|x y z>"],
    desc: { ru: "Бьёт декоративной молнией без урона и поджога.", en: "Strikes visual-only lightning without damage or fire." },
    example: `strike fake lightning at player`, since: "0.5.3", related: [] },
  { id: "eff-explosion", category: "effects", name: { ru: "Взрыв (обычный и безопасный)", en: "Explosion (normal & safe)" },
    syntax: ["create [safe] explosion at <цель|локация|x y z> [with power <N>]"],
    desc: { ru: "Создаёт взрыв. Слово `safe` отключает разрушение блоков.", en: "Creates an explosion. `safe` prevents block destruction." },
    example: `create safe explosion at location of targeted block with power 2`, since: "0.5.3", related: [] },
  { id: "eff-playsound-at", category: "effects", name: { ru: "Звук в точке / от игрока", en: "Play sound at location / player" },
    syntax: ["play sound <id> at <x y z|цель|локация> [for <слушатели>] [with volume <V> [pitch <P>]]"],
    desc: { ru: "Проигрывает звук в координатах, от игрока или от `targeted block`/`targeted entity`. Без `for` слышно всем рядом; с `for <цель>` — только выбранным игрокам. Поддерживаются как ванильные звуки, так и кастомные `.ogg` из `scriptcraft/resources/assets/<ns>/sounds.json`.", en: "Plays a sound at coordinates, a player, or a location expression. Without `for`, everyone nearby hears it; with `for <target>`, only chosen players hear it. Supports both vanilla and custom `.ogg` sounds from `scriptcraft/resources/assets/<ns>/sounds.json`." },
    example: `play sound entity.experience_orb.pickup at player for all players with volume 1 pitch 1.2\nplay sound mypack:boss_roar at player with volume 1.5 pitch 0.9`, since: "0.5.3", related: ["eff-stop-sound"] },

  // ================================================================== НОВЫЕ ФИЧИ: GUI КОНТРОЛЫ, СПИСКИ, КАМЕРА, ПРОФАЙЛЕР, ИСТОРИЯ
  { id: "screen-input", category: "screen", name: { ru: "Поле ввода текста (input)", en: "Text input field (input)" },
    syntax: [
      "input [\"<id>\"] [id <id>] at <x> <y> [size <WxH>] [anchor <A>] [value \"...\"] [placeholder \"...\"] [max <N>] [color #hex] [tooltip \"...\"] [run ...]",
      "value of screen element \"<id>\" [of <цель>]",
      "set value of screen element \"<id>\" [for <цель>] to <выражение>"
    ],
    desc: {
      ru: "Интерактивное текстовое поле в `open screen`. Введённый текст синхронизируется на сервер в реальном времени, вызывает событие `on input \"<id>\":` и доступен через выражение `value of screen element \"<id>\"` (или `input \"<id>\"`). Экран при вводе не закрывается.",
      en: "Interactive text box inside `open screen`. Typed text syncs live to the server, triggers `on input \"<id>\":`, and can be read with `value of screen element \"<id>\"` (or `input \"<id>\"`). Typing never closes the screen."
    },
    example: `command /promo:
	open screen "Промокод":
		text "&eВведите промокод:" at 0 -30 anchor center
		input "code" at 0 -10 size 150x20 placeholder "Например: VIP2026" max 24 tooltip "&7Введите код и нажмите Применить"
		button "&aПрименить" at 0 22 size 110x20 run ApplyPromo(player) keep open

function ApplyPromo(p: player):
	set {-code} to value of screen element "code" of p
	if {-code} is "VIP2026":
		send "&aПромокод активирован!" to p
		close screen of p
	else:
		send "&cНеверный код: %{-code}%" to p`,
    since: "0.6.8", related: ["screen-slider", "screen-toggle", "screen-tooltip", "event-gui-change"]
  },
  { id: "screen-slider", category: "screen", name: { ru: "Слайдер / ползунок (slider)", en: "Slider control (slider)" },
    syntax: [
      "slider [\"<подпись>\"] id <id> at <x> <y> [size <WxH>] [anchor <A>] from <min> to <max> [value <V>] [step <S>] [color #hex] [tooltip \"...\"] [run ...]"
    ],
    desc: {
      ru: "Интерактивный ползунок с шагом `step` и диапазоном `from <min> to <max>`. При перетаскивании отправляет числовое значение на сервер, вызывает `on slider \"<id>\":` (в `%value%` — текущее число) и опциональный `run`.",
      en: "Interactive range slider with `from <min> to <max>` and `step`. Dragging syncs the numeric value to the server, fires `on slider \"<id>\":` (`%value%`), and runs the optional `run` action."
    },
    example: `command /settings:
	open screen "Настройки":
		slider "Громкость" id vol at 0 -16 size 160x20 from 0 to 100 value 75 step 5 color #3B82F6 tooltip "&7Перетащите ползунок"

on slider "vol":
	set {vol::%player%} to %value%
	send action bar "&bГромкость: %value%%%" to player`,
    since: "0.6.8", related: ["screen-input", "screen-toggle", "screen-tooltip", "event-gui-change"]
  },
  { id: "screen-toggle", category: "screen", name: { ru: "Тумблер / чекбокс (toggle)", en: "Toggle / checkbox (toggle)" },
    syntax: [
      "toggle|checkbox [\"<подпись>\"] id <id> at <x> <y> [size <WxH>] [anchor <A>] [value true|false] [checked] [color #hex] [tooltip \"...\"] [run ...]"
    ],
    desc: {
      ru: "Переключатель (тумблер) `true` / `false` на экране. При клике сразу меняет состояние без закрытия экрана, вызывает `on toggle \"<id>\":` (`%value%` = `true` или `false`) и сохраняет состояние в `value of screen element \"<id>\"`.",
      en: "Boolean toggle switch (`true` / `false`) on a custom screen. Clicking toggles immediately without closing, fires `on toggle \"<id>\":` (`%value%`), and updates `value of screen element \"<id>\"`."
    },
    example: `command /prefs:
	open screen "Параметры":
		toggle "Показывать HUD" id show_hud at 0 -10 size 150x20 value true color #22C55E tooltip "&7Включить или скрыть интерфейс"

on toggle "show_hud":
	if %value% is "true":
		show hud "main" to player
	else:
		hide hud "main" from player`,
    since: "0.6.8", related: ["screen-input", "screen-slider", "screen-tooltip", "event-gui-change"]
  },
  { id: "screen-tooltip", category: "screen", name: { ru: "Тултипы элементов (tooltip)", en: "Element tooltips (tooltip)" },
    syntax: ["... tooltip \"Подсказка\\nВторая строка\"", "... hover \"Подсказка\""],
    desc: {
      ru: "Параметр `tooltip \"...\"` (или `hover \"...\"`) работает у любых элементов экрана (`button`, `text`, `image`, `slot`, `bar`, `list`, `input`, `slider`, `toggle`). Поддерживаются цветовые коды `&` и переносы строк `\\n`.",
      en: "The `tooltip \"...\"` (or `hover \"...\"`) option works on all screen elements (`button`, `text`, `image`, `slot`, `bar`, `list`, `input`, `slider`, `toggle`). Supports `&` color codes and `\\n` line breaks."
    },
    example: `command /shop:
	open screen "Магазин":
		button "&6Купить меч" at 0 0 size 120x20 run "/buy sword" keep open tooltip "&eАлмазный меч\\n&7Цена: &a500 монет\\n&8Нажмите ЛКМ"`,
    since: "0.6.8", related: ["screen-input", "screen-slider", "screen-toggle"]
  },
  { id: "event-gui-change", category: "events", name: { ru: "Изменение контрола GUI", en: "GUI control change" },
    syntax: ["on gui change [\"<id>\"]:", "on input [\"<id>\"]:", "on slider [\"<id>\"]:", "on toggle [\"<id>\"]:"],
    desc: {
      ru: "Срабатывает, когда игрок вводит текст в `input`, двигает `slider` или переключает `toggle` на кастомном экране. Плейсхолдеры: `%element%` (id контрола), `%value%` (новое значение), `%screen%` (заголовок экрана).",
      en: "Fires when a player types into an `input`, moves a `slider`, or clicks a `toggle` on a custom screen. Placeholders: `%element%`, `%value%`, `%screen%`."
    },
    example: `on gui change:
	send "&7[%screen%] &e%element% &7= &f%value%" to player

on input "search":
	set {search::%player%} to %value%`,
    since: "0.6.8", related: ["screen-input", "screen-slider", "screen-toggle"]
  },
  { id: "list-operations", category: "effects", name: { ru: "Списки и массивы (sort, reverse, size, join)", en: "Lists & arrays (sort, reverse, size, join)" },
    syntax: [
      "add <значение> to {list::*}",
      "remove <значение> from {list::*}",
      "remove index <ключ> from {list::*}",
      "sort {list::*} [ascending|descending]",
      "reverse {list::*}",
      "clear {list::*}",
      "size|count|amount|length of {list::*}",
      "first|last element of {list::*}",
      "index of <значение> in {list::*}",
      "join {list::*} with \", \""
    ],
    desc: {
      ru: "Полный набор операций со списками и словарями `{list::*}`: добавление, удаление по значению или ключу, сортировка чисел/строк (`sort`), переворот (`reverse`), очистка (`clear`), размер (`size of`), первый/последний элемент, поиск индекса и склейка в строку (`join ... with`).",
      en: "Full list & map operations on `{list::*}`: append, remove by value or key, numeric/string sorting (`sort`), `reverse`, `clear`, `size of`, `first`/`last element of`, `index of`, and `join ... with`."
    },
    example: `command /listdemo:
	clear {nums::*}
	add 42 to {nums::*}
	add 7 to {nums::*}
	add 19 to {nums::*}
	sort {nums::*} descending
	set {-cnt} to size of {nums::*}
	set {-joined} to join {nums::*} with ", "
	send "&aВсего %{-cnt}%: &f%{-joined}% &7(первый: %{nums::1}%)" to player`,
    since: "0.6.8", related: ["list-conditions", "loop-sorted"]
  },
  { id: "list-conditions", category: "conditions", name: { ru: "Проверки списков (contains, has key, is empty)", en: "List conditions (contains, has key, is empty)" },
    syntax: [
      "{list::*} contains <значение>",
      "{list::*} does not contain <значение>",
      "{list::*} has key <ключ>",
      "{list::*} does not have key <ключ>",
      "{list::*} is [not] empty"
    ],
    desc: {
      ru: "Условия для списков и ассоциативных массивов `{list::*}`: проверка наличия значения, наличия ключа/индекса и пустоты списка.",
      en: "Conditions for lists and maps `{list::*}`: check if a value exists, if a key exists, or whether the list is empty."
    },
    example: `if {vip::*} contains %player%:
	send "&6Вы в списке VIP!" to player
if {scores::*} has key %player%:
	send "&eВаш счёт: %{scores::%player%}%" to player
if {queue::*} is empty:
	send "&7Очередь пуста" to player`,
    since: "0.6.8", related: ["list-operations", "loop-sorted", "cond-text-match"]
  },
  { id: "cond-text-match", category: "conditions", name: { ru: "Проверки текста (contains, starts with, ends with)", en: "Text conditions (contains, starts with, ends with)" },
    syntax: [
      "<текст> contains <подстрока>",
      "<текст> does not contain <подстрока>",
      "<текст> starts with <префикс>",
      "<текст> does not start with <префикс>",
      "<текст> ends with <суффикс>",
      "<текст> does not end with <суффикс>",
      "<текст> is [not] empty",
      "length of <текст>"
    ],
    desc: {
      ru: "Проверка любого текста, переменной `{var}` или сообщения чата (`message` / `%message%`): содержит ли подстроку (`contains`), начинается ли с символа/префикса (`starts with`), заканчивается ли на суффикс (`ends with`), пуст ли текст (`is empty`), а также длина строки `length of <текст>`.",
      en: "Checks any text, `{var}`, or chat `message` (`%message%`): substring check (`contains`), prefix check (`starts with`), suffix check (`ends with`), emptiness (`is empty`), plus `length of <text>`."
    },
    example: `on chat:
	if message starts with "!":
		cancel event
		broadcast "&6[Глобал] &f%player%: &e%message%"
	else if message contains "спам":
		cancel event
		send "&cЗапрещённое слово в чате!" to player`,
    since: "0.7.0", related: ["list-conditions"]
  },
  { id: "loop-sorted", category: "structures", name: { ru: "Цикл по ключам и сортированный цикл", en: "Key/value loop & sorted loop" },
    syntax: [
      "loop {map::*}:",
      "loop {map::*} sorted [ascending|descending]:"
    ],
    desc: {
      ru: "Перебирает все прямые пары ключ/значение `{map::*}`. Внутри доступны `loop-index` (`%loop-index%`) и `loop-value` (`%loop-value%`). С модификатором `sorted descending` (или `ascending`) перебирает элементы по возрастанию/убыванию значений без изменения исходной таблицы — идеально для Топ-10.",
      en: "Iterates all direct key/value entries of `{map::*}` with `loop-index` (`%loop-index%`) and `loop-value` (`%loop-value%`). Adding `sorted descending` (or `ascending`) iterates in sorted value order without mutating the map — ideal for Top-10 leaderboards."
    },
    example: `command /top:
	set {-rank} to 0
	loop {coins::*} sorted descending:
		add 1 to {-rank}
		send "&e#%{-rank}% &f%loop-index% &7— &a%loop-value% монет" to player
		if {-rank} >= 10:
			stop`,
    since: "0.6.8", related: ["list-operations", "list-conditions"]
  },
  { id: "eff-stop-sound", category: "effects", name: { ru: "Остановка звука и кастомные звуки", en: "Stop sound & custom sounds" },
    syntax: [
      "play sound <ns:sound_id> to <цель> [with volume <V> [pitch <P>]]",
      "stop sound [<id>] for|to <цель>",
      "stop all sounds for|to <цель>"
    ],
    desc: {
      ru: "`play sound` поддерживает кастомные `.ogg` звуки из `scriptcraft/resources/assets/<ns>/sounds/` + `sounds.json` (автоматически синхронизируются клиентам). `stop sound` останавливает конкретный звук или все звуки у игрока.",
      en: "`play sound` supports custom `.ogg` sounds from `scriptcraft/resources/assets/<ns>/sounds/` + `sounds.json` (auto-synced to clients). `stop sound` stops a specific sound or all sounds for the target."
    },
    example: `command /music:
	play sound mypack:boss_theme to player with volume 0.8 pitch 1.0

command /mute:
	stop all sounds for player`,
    since: "0.6.8", related: ["eff-playsound-at"]
  },
  { id: "eff-particle-advanced", category: "effects", name: { ru: "Цветные партиклы, разброс и кольца", en: "Colored particles, offsets & rings" },
    syntax: [
      "spawn particle dust at <цель|локация> [count <N>] color #RRGGBB [for <слушатели>]",
      "spawn particle <id> at <цель> count <N> offset <dx> <dy> <dz> speed <S> [for <слушатели>]",
      "spawn particle ring|circle <id> at <цель|локация> radius <R> [points <N>] [color #RRGGBB]"
    ],
    desc: {
      ru: "Расширенные эффекты частиц: цветная пыль `dust` с HEX-цветом `#RRGGBB`, настраиваемый разброс `offset dx dy dz`, показ только выбранным игрокам `for <цель>`, а также готовое кольцо частиц `spawn particle ring`.",
      en: "Advanced particles: hex-colored `dust` (`#RRGGBB`), custom `offset dx dy dz`, per-player visibility (`for <target>`), and instant particle circles (`spawn particle ring`)."
    },
    example: `command /aura:
	spawn particle ring dust at player radius 2.5 points 36 color #FF44AA
	spawn particle flame at player count 20 offset 0.5 1.0 0.5 speed 0.04 for player`,
    since: "0.6.8", related: ["eff-particle", "eff-camera"]
  },
  { id: "eff-camera", category: "effects", name: { ru: "Эффекты камеры (shake, fade, fov, roll)", en: "Camera effects (shake, fade, fov, roll)" },
    syntax: [
      "shake camera of <цель> [with intensity <N>] [for <T> ticks|seconds]",
      "fade screen of <цель> [to #RRGGBB] [in <T> stay <T> out <T> [ticks]]",
      "fade screen of <цель> [to #RRGGBB] for <N> seconds",
      "set camera fov of <цель> to <смещение> [for <T> ticks|seconds]",
      "roll|tilt camera of <цель> by <градусы> [for <T> ticks|seconds]",
      "reset camera of <цель>"
    ],
    desc: {
      ru: "Плавные клиентские эффекты камеры: встряска (`shake camera`), затемнение или вспышка экрана (`fade screen`), динамический зум/FOV (`set camera fov`), наклон камеры (`roll camera`) и мгновенный сброс (`reset camera`).",
      en: "Smooth client-side camera effects: camera shake (`shake camera`), screen fade/flash (`fade screen`), dynamic FOV offset (`set camera fov`), camera roll/tilt (`roll camera`), and instant reset (`reset camera`)."
    },
    example: `command /impact:
	shake camera of player with intensity 2.5 for 1.5 seconds
	fade screen of player to #FF2200 in 3 stay 4 out 15 ticks
	set camera fov of player to -15 for 20 ticks
	roll camera of player by 8 for 15 ticks`,
    since: "0.6.8", related: ["eff-particle-advanced", "eff-playsound-at"]
  },
  { id: "cfg-studio-history-profiler", category: "config", name: { ru: "История версий и профайлер студии", en: "Script version history & Studio profiler" },
    syntax: [
      "studio.scriptHistoryEnabled = true",
      "studio.scriptHistoryRetentionDays = 3",
      "studio.profilerEnabled = true"
    ],
    desc: {
      ru: "Перед каждым сохранением скрипта в студии создаётся снимок предыдущей версии в `scriptcraft/history/` (кнопка «История» в редакторе позволяет просматривать и откатывать версии). Старые версии автоматически удаляются через `scriptHistoryRetentionDays` дней (по умолчанию `3`, `0` — хранить бессрочно). Профайлер в студии показывает MSPT сервера, затраты каждого скрипта/события/цикла; его можно включать/выключать на сервере, а также полностью скрыть для клиента в настройках студии (шестерёнка).",
      en: "Before every save in Studio, a snapshot of the previous script version is stored in `scriptcraft/history/` (use the History button in the editor to preview & restore). Old versions auto-delete after `scriptHistoryRetentionDays` days (default `3`, `0` = keep forever). The Studio Profiler shows server MSPT and per-script/event/loop timings; it can be toggled on the server and hidden/disabled per-client in Studio Settings."
    },
    example: `[studio]
	liveEdit = true
	requireOp = true
	scriptHistoryEnabled = true
	scriptHistoryRetentionDays = 3
	profilerEnabled = true`,
    since: "0.6.8", related: []
  }
  ]
};
