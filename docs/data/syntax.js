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
  version: "0.9.16",
  categories: [
    { id: "events", ru: "События", en: "Events" },
    { id: "structures", ru: "Структуры", en: "Structures" },
    { id: "effects", ru: "Эффекты", en: "Effects" },
    { id: "conditions", ru: "Условия", en: "Conditions" },
    { id: "expressions", ru: "Выражения и плейсхолдеры", en: "Expressions & placeholders" },
    { id: "targets", ru: "Цели (селекторы)", en: "Targets (selectors)" },
    { id: "screen", ru: "Экраны (GUI)", en: "Screens (GUI)" },
    { id: "hud", ru: "UI / HUD", en: "UI / HUD" },
    { id: "commands", ru: "Команды мода", en: "Mod commands" },
    { id: "config", ru: "Конфиг", en: "Config" }
  ],
  entries: [
  {
    id: "event-join", category: "events",
    name: { ru: "Вход игрока", en: "Player join" },
    syntax: ["on join:"],
    desc: {
      ru: "Срабатывает каждый раз, когда игрок заходит на сервер. Внутри `player` — зашедший игрок, `%player%` — ник, `%uuid%` — UUID, `%online%` — текущий онлайн. Можно объединять с загрузкой скрипта: `on join or load:` (тогда при `/sc reload` код выполнится для всех, кто уже онлайн).",
      en: "Fires every time a player joins the server. Inside, `player` is the joining player, `%player%` is their name, `%uuid%` is their UUID, and `%online%` is the online count. Can be combined with script load: `on join or load:` (runs on `/sc reload` for everyone already online)."
    },
    example: `# Приветствие и счётчик входов
on join:
	add 1 to {joins::%uuid%}
	send "&aПривет, %player%! &7Это твой вход #%{joins::%uuid%}%" to player
	broadcast "&7[+] &f%player% &7зашёл (онлайн: %online%)"

# Выдача HUD при входе и при /sc reload
on join or load:
	create hud "stats" for player:
		text "&eМонет: %{coins::%player%}%" at 6 6 anchor top_left shadow`,
    since: "0.1.0", related: ["event-quit","event-load","ph-player","eff-send","eff-broadcast"]
  },
  {
    id: "event-quit", category: "events",
    name: { ru: "Выход игрока", en: "Player quit" },
    syntax: ["on quit:"],
    desc: {
      ru: "Срабатывает, когда игрок выходит с сервера. Внутри `player` — вышедший игрок. Удобно очищать временные переменные `{-...}` и игровые сессии. Можно объединять с выгрузкой скрипта: `on quit or unload:`.",
      en: "Fires when a player leaves the server. Inside, `player` is the leaving player. Great for cleaning up temporary `{-...}` variables and game sessions. Can be combined with unload: `on quit or unload:`."
    },
    example: `# Оповещение и очистка временных данных
on quit:
	delete {-arena::%player%}
	delete {-pm-target::%player%}
	broadcast "&7[-] &f%player% &7вышел с сервера"

# Очистка и при выходе, и при /sc reload
on quit or unload:
	delete {-session::%player%}`,
    since: "0.1.0", related: ["event-join","event-unload"]
  },
  {
    id: "event-load", category: "events",
    name: { ru: "Загрузка скрипта", en: "Script load" },
    syntax: [
      "on load:",
      "on script load:"
    ],
    desc: {
      ru: "Один раз, когда этот скрипт загружен: старт сервера, `/sc reload` и `/sc enable`. Игрока нет — `all players` или `broadcast`. Несколько событий в одной строке пишутся через `or`: `on join or load:` выполнится и при входе, и при загрузке для каждого, кто уже онлайн.",
      en: "Once, when this script is loaded: server start, `/sc reload` and `/sc enable`. There is no player — use `all players` or `broadcast`. Several events on one line are joined with `or`: `on join or load:` runs on join and, on load, for everyone already online."
    },
    example: `# Инициализация настроек при загрузке скрипта
on load:
	if {server_motd} is not set:
		set {server_motd} to "&6Добро пожаловать на сервер!"
	broadcast "&7[ScriptCraft] Скрипт %script% загружен"

# Показ HUD всем, кто уже онлайн, и каждому новому игроку
on join or load:
	create hud "coins" for player:
		text "&eМонеты: %{coins::%player%}%" at 6, 6 anchor top_left shadow`,
    since: "0.1.5", related: ["event-unload","event-join","hud-create","cmd-disable"]
  },
  {
    id: "event-unload", category: "events",
    name: { ru: "Выгрузка скрипта", en: "Script unload" },
    syntax: [
      "on unload:",
      "on script unload:"
    ],
    desc: {
      ru: "Перед выгрузкой: `/sc disable`, перед `/sc reload` и при остановке сервера. Игрока нет. HUD этого скрипта при `/sc disable` убирается сам. Через `or` можно добавить выход: `on quit or unload:`.",
      en: "Before unload: `/sc disable`, before `/sc reload` and on server stop. There is no player. This script's HUD is removed on `/sc disable`. `or` can add quit: `on quit or unload:`."
    },
    example: `# Сохранение или очистка при выключении скрипта
on unload:
	delete {-temp_event::*}
	broadcast "&7Скрипт %script% выключен"

# Уборка временных состояний и при выходе игрока, и при перезагрузке
on quit or unload:
	delete {-duel::%player%}`,
    since: "0.1.5", related: ["event-load","event-quit","cmd-disable"]
  },
  {
    id: "event-death", category: "events",
    name: { ru: "Смерть", en: "Death" },
    syntax: [
      "on death:",
      "on player death:"
    ],
    desc: {
      ru: "Срабатывает при смерти игрока. Плейсхолдеры: `%attacker%` — кто убил (ник игрока, id моба или `<none>`), `%cause%` — причина смерти (`player`, `mob`, `arrow`, `fall`, `lava`, `inFire`, `onFire`, `drown`, `starve`, `magic`, `wither`, `explosion`, `lightningBolt`, `outOfWorld`, `cramming`, `freeze`). `cancel event` отменяет смерть (можно сделать тотем или спасение).",
      en: "Fires when a player dies. Placeholders: `%attacker%` — killer (player name, mob id or `<none>`), `%cause%` — death cause (`player`, `mob`, `arrow`, `fall`, `lava`, `inFire`, `onFire`, `drown`, `starve`, `magic`, `wither`, `explosion`, `lightningBolt`, `outOfWorld`, `cramming`, `freeze`). `cancel event` prevents death."
    },
    example: `# Статистика смертей и награда убийце
on death:
	add 1 to {deaths::%player%}
	broadcast "&c☠ %player% погиб (%cause%)"
	if %attacker% is not "<none>":
		send "&7Тебя убил: &c%attacker%" to player

# Защита VIP-игроков от смерти от падения
on death:
	if %cause% is "fall":
		if player has permission "vip.nofall":
			cancel event
			set health of player to 4
			send "&aСпасение от падения сработало!" to player`,
    since: "0.2.0", related: ["event-respawn","eff-cancel","event-damage","ph-event"]
  },
  {
    id: "event-respawn", category: "events",
    name: { ru: "Возрождение", en: "Respawn" },
    syntax: [
      "on respawn:",
      "on player respawn:"
    ],
    desc: {
      ru: "Срабатывает, когда игрок возродился после смерти. `cancel event` это событие не отменяет. Удобно выдавать стартовые эффекты, восстанавливать скорость или телепортировать на спавн арены.",
      en: "Fires when a player respawns after dying. `cancel event` does not cancel this event. Useful for re-applying effects, restoring speed, or teleporting to an arena spawn."
    },
    example: `# Полное восстановление и защита на 5 секунд
on respawn:
	heal player
	feed player
	give player effect resistance for 5 seconds level 2
	send "&aВы возродились с временной защитой!" to player

# Возврат в лобби после смерти
on respawn:
	if {lobby} is set:
		teleport player to {lobby}`,
    since: "0.2.0", related: ["event-death","eff-heal","eff-feed"]
  },
  {
    id: "event-chat", category: "events",
    name: { ru: "Чат", en: "Chat" },
    syntax: [
      "on chat:",
      "on player chat:"
    ],
    desc: {
      ru: "Срабатывает, когда игрок отправляет сообщение в чат. Текст сообщения доступен как `%message%` и выражение `message` (работает с `contains`, `starts with`, `ends with`). `cancel event` отменяет стандартную отправку сообщения.",
      en: "Fires when a player sends a chat message. The text is available as `%message%` and the `message` expression (works with `contains`, `starts with`, `ends with`). `cancel event` blocks vanilla chat delivery."
    },
    example: `# Глобальный чат через "!" и локальный по умолчанию
on chat:
	if message starts with "!":
		cancel event
		broadcast "&6[Глобал] &f%player%: &e%message%"
	else if message contains "спам" or "реклама":
		cancel event
		send "&cЗапрещённое слово в чате!" to player

# Быстрый ответ на слово
on chat:
	if message is "ping":
		cancel event
		send "&aПонг!" to player`,
    since: "0.2.0", related: ["eff-cancel","eff-send","ph-event"]
  },
  {
    id: "event-break", category: "events",
    name: { ru: "Сломать блок", en: "Block break" },
    syntax: [
      "on break:",
      "on block break:",
      "on mine:",
      "on break block:"
    ],
    desc: {
      ru: "Срабатывает, когда игрок ломает блок. Плейсхолдеры: `%block%` — id блока (`minecraft:diamond_ore`, `example:ruby_block`), `%x%`, `%y%`, `%z%` — целочисленные координаты блока. `cancel event` запрещает ломание.",
      en: "Fires when a player breaks a block. Placeholders: `%block%` — block id (`minecraft:diamond_ore`, `example:ruby_block`), `%x%`, `%y%`, `%z%` — integer block coordinates. `cancel event` prevents breaking."
    },
    example: `# Награда за добычу руды
on break:
	if %block% is "minecraft:diamond_ore" or "minecraft:deepslate_diamond_ore":
		add 10 to {coins::%player%}
		send "&b+10 монет за алмазную руду (%x% %y% %z%)" to player

# Запрет ломать бедрок и спавнеры не-операторам
on break:
	if %block% is "minecraft:spawner":
		if player is not op:
			cancel event
			send "&cСпавнеры ломать нельзя!" to player`,
    since: "0.2.0", related: ["event-place","eff-cancel","ph-event"]
  },
  {
    id: "event-place", category: "events",
    name: { ru: "Поставить блок", en: "Block place" },
    syntax: [
      "on place:",
      "on block place:",
      "on place block:"
    ],
    desc: {
      ru: "Срабатывает, когда игрок ставит блок. Плейсхолдеры: `%block%` — id поставленного блока, `%x%`, `%y%`, `%z%` — координаты. `cancel event` запрещает установку блока.",
      en: "Fires when a player places a block. Placeholders: `%block%` — placed block id, `%x%`, `%y%`, `%z%` — coordinates. `cancel event` prevents placement."
    },
    example: `# Эффект при установке кастомного блока
on place:
	if %block% is "example:ruby_block":
		play sound block.amethyst_block.place at %x% %y% %z%
		spawn particle happy_villager at %x% %y% %z% count 12

# Запрет ставить TNT обычным игрокам
on place:
	if %block% is "minecraft:tnt":
		if player is not op:
			cancel event
			send "&cTNT запрещён на сервере!" to player`,
    since: "0.2.0", related: ["event-break","eff-cancel","ph-event","eff-sound"]
  },
  {
    id: "event-right-click", category: "events",
    name: { ru: "ПКМ по блоку", en: "Right click block" },
    syntax: [
      "on right click:",
      "on rightclick:",
      "on right click block:"
    ],
    desc: {
      ru: "ПКМ основной рукой по блоку или сущности. Плейсхолдеры: `%block%`, `%x%`, `%y%`, `%z%`, а также выражения `clicked block`, `clicked entity`, `clicked player` (`%clicked_entity%`, `%clicked_player%`). `cancel event` отменяет взаимодействие. Клик по воздуху на выделенном сервере не приходит.",
      en: "Main-hand right click on a block or entity. Placeholders: `%block%`, `%x%`, `%y%`, `%z%`, plus expressions `clicked block`, `clicked entity`, `clicked player` (`%clicked_entity%`, `%clicked_player%`). `cancel event` cancels the interaction. Air clicks are not delivered on dedicated servers."
    },
    example: `# Открытие меню по ПКМ по блоку
on right click:
	if %block% is "minecraft:chest":
		cancel event
		send "&eСундук закрыт на ключ!" to player

# Взаимодействие с игроком или NPC по ПКМ
on right click:
	if clicked player is set:
		send "&bВы кликнули по игроку %clicked_player%" to player
	else if clicked entity is "minecraft:villager":
		cancel event
		send "&6Открыт магазин торговца!" to player`,
    since: "0.2.0", related: ["event-left-click","eff-cancel","ph-event"]
  },
  {
    id: "event-left-click", category: "events",
    name: { ru: "ЛКМ по блоку", en: "Left click block" },
    syntax: [
      "on left click:",
      "on leftclick:",
      "on left click block:"
    ],
    desc: {
      ru: "ЛКМ по блоку основной рукой. `%block%` — id блока, `%x%` `%y%` `%z%` — координаты. `cancel event` отменяет клик. Клик по воздуху на выделенном сервере не приходит.",
      en: "Left click on a block with the main hand. `%block%` is the block id, `%x%` `%y%` `%z%` are the coordinates. `cancel event` stops the click. Clicking air is not delivered on a dedicated server."
    },
    example: `# Информация о блоке по ЛКМ с палкой
on left click:
	if player is holding stick:
		cancel event
		send "&7Блок: &f%block% &7на &e%x% %y% %z%" to player

# Защита зоны от ударов по блокам
on left click:
	if %block% is "minecraft:beacon":
		cancel event
		play sound block.anvil.land to player with volume 0.4`,
    since: "0.2.0", related: ["event-right-click","event-break","eff-cancel","ph-event"]
  },
  {
    id: "event-drop", category: "events",
    name: { ru: "Выбросить предмет", en: "Drop item" },
    syntax: [
      "on drop:",
      "on drop item:",
      "on item drop:"
    ],
    desc: {
      ru: "Игрок выбросил предмет (Q). `%item%` — id предмета. `cancel event` отменяет выброс, но предмет уже снят с инвентаря: отмена не возвращает его в руку.",
      en: "The player tossed an item (Q). `%item%` is the item id. `cancel event` cancels the toss, but the item is already out of the inventory: cancelling does not put it back."
    },
    example: `# Запрет выбрасывать ценные предметы
on drop:
	if %item% is "minecraft:diamond" or "minecraft:netherite_ingot":
		cancel event
		send "&cЦенные предметы нельзя выбрасывать!" to player

# Логирование выброшенных предметов
on drop:
	log "%player% выбросил %item%"`,
    since: "0.2.0", related: ["event-pickup","eff-cancel","eff-drop","ph-event"]
  },
  {
    id: "event-pickup", category: "events",
    name: { ru: "Подобрать предмет", en: "Pick up item" },
    syntax: [
      "on pickup:",
      "on pick up:",
      "on item pickup:"
    ],
    desc: {
      ru: "Игрок поднял предмет с земли, ещё до инвентаря. `%item%` — id предмета. `cancel event` не даёт ему попасть в инвентарь. `on receive` при этом срабатывает отдельно, уже когда число в инвентаре выросло.",
      en: "The player picked an item up off the ground, before it enters the inventory. `%item%` is the item id. `cancel event` keeps it out of the inventory. `on receive` still fires on its own, once the inventory count has grown."
    },
    example: `# Запрет подбирать предметы в режиме наблюдателя / гостям
on pickup:
	if player is in group "guest":
		cancel event
		send action bar "&cГости не могут подбирать предметы" to player

# Уведомление о подборе редкого предмета
on pickup:
	if %item% is "minecraft:nether_star":
		play sound entity.player.levelup to player
		broadcast "&6%player% подобрал Звезду Незера!"`,
    since: "0.2.0", related: ["event-drop","event-receive","eff-cancel","ph-event"]
  },
  {
    id: "event-receive", category: "events",
    name: { ru: "Получение предмета", en: "Item received" },
    syntax: ["on receive:"],
    desc: {
      ru: "В инвентаре стало больше этого предмета: подбор с земли, `give`, сундук, крафт, торговля, креатив. `%item%` — id, `%amount%` — на сколько выросло число. Перекладывание из слота в слот сумму не меняет и событие не поднимает. Первый снимок при входе молчит, вещи с диска не считаются добычей. `cancel event` забирает эту добавку обратно. Предмет уже лежит в инвентаре, когда событие бежит. Курсор и эндер-сундук не считаются. `on pickup` при подборе с земли срабатывает отдельно, ещё до инвентаря. Предметы, которые выдал сам обработчик `on receive`, вторым событием не приходят.",
      en: "The inventory total of an item grew: a ground pickup, `give`, a chest, crafting, trading, creative. `%item%` is the id, `%amount%` is how many were added. Moving a stack between slots does not change the total, so it does not fire. The first snapshot on join is silent, so items loaded from disk do not count. `cancel event` removes that added amount. The item is already in the inventory when the event runs. The cursor and the ender chest are not counted. `on pickup` still fires on its own for a ground item, before it enters the inventory. Items the `on receive` handler itself gives do not fire a second time."
    },
    example: `# Уведомление и запрет запрещённого предмета
on receive:
	if %item% is "minecraft:bedrock":
		cancel event
		send "&cБедрок запрещён в инвентаре!" to player

# Квест: собрать 16 алмазов
on receive:
	if %item% is "minecraft:diamond":
		add %amount% to {quest_diamonds::%player%}
		send action bar "&bАлмазов собрано: %{quest_diamonds::%player%}%/16" to player`,
    since: "0.3.7", related: ["event-pickup","eff-cancel","eff-give-item","ph-event"]
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
    example: `# Управление в мини-игре внутри экрана
on key w in screen "Змейка":
	MoveSnake(player, "up")

on key hold d in screen "Змейка":
	StepRight(player)

# Запрет закрывать экран по Escape
on key escape in screen "Выбор класса":
	cancel event
	send action bar "&cСначала выберите класс!" to player

# Быстрое меню по клавише G в обычной игре
on key g:
	if player has screen open:
		stop
	make player execute command "menu"`,
    since: "0.3.7", related: ["event-open","event-screen-open","screen-button","ph-event"]
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
    example: `# Лог открытия и очистка при закрытии игры
on screen open "Змейка":
	play sound ui.button.click to player

on screen close "Змейка":
	delete {-snake::%player%::*}
	send "&7Игра «%screen%» завершена!" to player`,
    since: "0.6.2", related: ["eff-open-screen","event-key"]
  },
  {
    id: "event-open", category: "events",
    name: { ru: "Открытие меню", en: "Menu open" },
    syntax: [
      "on open <меню>:",
      "on open menu <меню>:",
      "on open any:"
    ],
    desc: {
      ru: "Открывается ванильный экран. `%menu%` — его имя. Имена: `inventory` (и выживание, и креатив, то есть E), `chest` (в том числе эндер-сундук и большой сундук), `shulker`, `barrel`, `crafting`, `furnace` (печь, плавильня, коптильня), `anvil`, `enchanting`, `brewing`, `villager`, `hopper`, `horse`, `beacon`, `smithing`, `grindstone`, `loom`, `cartography`, `stonecutter`, `any`. `cancel event` где угодно в теле, даже внутри `if`, блокирует экран всегда: клиент закрывает его сразу и не ждёт сервер. Без `cancel event` меню открывается, а скрипт только узнаёт об этом. Экран самого скрипта (`open screen`) под `any` не попадает.",
      en: "A vanilla screen is opening. `%menu%` is its name. Names: `inventory` (survival and creative, so E), `chest` (including the ender chest and a large chest), `shulker`, `barrel`, `crafting`, `furnace` (furnace, blast furnace, smoker), `anvil`, `enchanting`, `brewing`, `villager`, `hopper`, `horse`, `beacon`, `smithing`, `grindstone`, `loom`, `cartography`, `stonecutter`, `any`. A `cancel event` anywhere in the body, even inside `if`, always blocks the screen: the client closes it immediately and does not wait for the server. Without `cancel event` the menu still opens and the script only observes it. The script's own screen (`open screen`) is not matched by `any`."
    },
    example: `# Подмена ванильного инвентаря своим экраном
on open inventory:
	cancel event
	open screen "Сумка" to player:
		text "&eВаш кастомный инвентарь" at 0, -20
		button "Закрыть" at 0, 20 size 100x20 close

# Логирование открытия сундуков и шалкеров
on open chest:
	log "%player% открыл %menu%"`,
    since: "0.3.7", related: ["event-key","eff-cancel","eff-open-screen","screen-list"]
  },
  {
    id: "event-damage", category: "events",
    name: { ru: "Урон", en: "Damage" },
    syntax: [
      "on damage:",
      "on hurt:"
    ],
    desc: {
      ru: "Срабатывает, когда игрок получает урон. Плейсхолдеры: `%damage%` — величина урона, `%attacker%` — источник урона (игрок, моб или `<none>`), `%cause%` — тип урона (`player`, `mob`, `arrow`, `fall`, `lava`, `inFire`, `onFire`, `drown`, `magic`, `wither`, `explosion`, `lightningBolt`). `cancel event` полностью отменяет урон. Срабатывает часто (огонь, яд, удары) — не ставьте сюда тяжёлые циклы.",
      en: "Fires when a player takes damage. Placeholders: `%damage%` — amount, `%attacker%` — source (player, mob or `<none>`), `%cause%` — damage type (`player`, `mob`, `arrow`, `fall`, `lava`, `inFire`, `onFire`, `drown`, `magic`, `wither`, `explosion`, `lightningBolt`). `cancel event` negates the hit. Fires frequently — keep the body lightweight."
    },
    example: `# Отмена урона от падения в лобби
on damage:
	if %cause% is "fall":
		if player is in world overworld:
			cancel event

# Встряска камеры и предупреждение при сильном ударе
on damage:
	if %damage% >= 6:
		shake camera of player with intensity 1.5 for 8 ticks
		send action bar "&cСильный удар: -%damage% HP" to player`,
    since: "0.2.0", related: ["eff-cancel","eff-stop","cond-gamemode"]
  },
  {
    id: "event-first-join", category: "events",
    name: { ru: "Первый вход", en: "First join" },
    syntax: [
      "on first join:",
      "on firstjoin:"
    ],
    desc: {
      ru: "Игрок ещё ни разу не выходил. Срабатывает вместе с `on join:`, не вместо него.",
      en: "The player has never logged out before. It fires together with `on join:`, not instead of it."
    },
    example: `# Стартовый набор и баланс для новичка
on first join:
	set {coins::%player%} to 100
	give player stone_sword 1
	give player bread 16
	send title "&6Добро пожаловать!" with subtitle "&eВам выдано 100 монет" to player
	broadcast "&aПоприветствуйте нового игрока %player%!"`,
    since: "0.2.0", related: ["event-join","eff-give-item"]
  },
  {
    id: "event-world-change", category: "events",
    name: { ru: "Смена мира", en: "World change" },
    syntax: [
      "on world change:",
      "on change world:",
      "on dimension change:",
      "on worldchange:"
    ],
    desc: {
      ru: "Игрок сменил измерение. `%from%` — старый мир, `%world%` — новый. `cancel event` это событие не отменяет.",
      en: "The player changed dimension. `%from%` is the old world, `%world%` is the new one. `cancel event` does not cancel this event."
    },
    example: `# Приветствие при переходе в другой мир
on world change:
	send "&7Переход: &f%from% &7→ &e%world%" to player
	if %world% is "minecraft:the_nether":
		send title "&cНезер" with subtitle "&7Осторожно, здесь жарко!" to player
		give player effect fire_resistance for 10 seconds`,
    since: "0.2.0", related: ["ph-event","eff-teleport"]
  },
  {
    id: "event-gamemode", category: "events",
    name: { ru: "Смена режима", en: "Game mode change" },
    syntax: [
      "on gamemode change:",
      "on game mode change:",
      "on gamemode:"
    ],
    desc: {
      ru: "Игрок меняет режим. `%from%` — старый, `%gamemode%` — новый (`survival`, `creative`, `adventure`, `spectator`). `cancel event` оставляет старый режим.",
      en: "The player is changing game mode. `%from%` is the old one, `%gamemode%` is the new one (`survival`, `creative`, `adventure`, `spectator`). `cancel event` keeps the old mode."
    },
    example: `# Запрет креатива без прав
on gamemode change:
	if %gamemode% is "creative":
		if player does not have permission "server.creative":
			cancel event
			send "&cНет прав на креатив!" to player
			stop
	send "&eРежим изменён: &7%from% → &a%gamemode%" to player`,
    since: "0.2.0", related: ["eff-cancel","eff-gamemode","cond-gamemode","ph-event"]
  },
  {
    id: "struct-command", category: "structures",
    name: { ru: "Своя команда", en: "Custom command" },
    syntax: [
      "command /<имя>:",
      "command /<имя> <слово> <тип>:",
      "command /<имя> <тип> [тип]:",
      "permission: <0-4>",
      "permission: <узел>",
      "aliases: <имя>, <имя>",
      "arguments: <тип>, [тип]",
      "executable by: players",
      "executable by: console",
      "executable by: players and console"
    ],
    desc: {
      ru: "Объявляет команду сервера. Слово без скобок — подкоманда (`command /bag take <text>:` → `/bag take <текст>`, `%arg-1%` — текст). `<тип>` — аргумент (можно не передавать, тогда `if arg-1 is not set:` сработает внутри команды!), `[тип]` — необязательный. Все типы аргументов и синонимы: `text` (`string`, `strings`), `player` (`online player`), `offline player` (`offlineplayer`), `integer` (`int`), `number` (`double`, `float`), `boolean` (`bool`), `date` (`time` — `today`, `tomorrow`, `yesterday`, `ГГГГ-ММ-ДД`), `world` (`dimension`), `item`. Без списка аргументов хвост строки — `%args%`. Заголовки в теле: `permission: <0-4>` (уровень оператора) или `permission: <узел>` (LuckPerms), `aliases: a, b`, `executable by: players | console | players and console`. `/sc reload` пересобирает команды без перезапуска сервера.",
      en: "Declares a server command. A bare word is a subcommand (`command /bag take <text>:` → `/bag take <text>`, `%arg-1%` is the text). `<type>` is a typed argument (if omitted by the player, `if arg-1 is not set:` runs cleanly inside the command), `[type]` is optional. All argument types & aliases: `text` (`string`, `strings`), `player` (`online player`), `offline player` (`offlineplayer`), `integer` (`int`), `number` (`double`, `float`), `boolean` (`bool`), `date` (`time` — `today`, `tomorrow`, `yesterday`, `YYYY-MM-DD`), `world` (`dimension`), `item`. Without typed args, the rest of the line is `%args%`. Header options: `permission: <0-4>` or `permission: <node>` (LuckPerms), `aliases: a, b`, `executable by: players | console | players and console`. `/sc reload` rebuilds commands without restarting."
    },
    example: `# Простая команда со свободным хвостом %args%
command /hello:
	send "&bПривет из %script%, %player%! Ты написал: %args%" to player

# Команда с проверкой, передан ли аргумент
command /abctoggle <text>:
	permission: server.toggle
	if arg-1 is not set:
		send "&eИспользование: /abctoggle <on|off>" to player
		stop
	if arg-1 is not "on" or "off":
		send "&cУкажите on или off!" to player
		stop
	send "&aРежим переключён на: %arg-1%" to player

# Команда с типами, алиасами и ограничением исполнителя
command /heal <player> [integer]:
	permission: 2
	aliases: h, healme
	executable by: players and console
	if arg-2 is set:
		heal arg-1 by arg-2
	else:
		heal arg-1`,
    since: "0.1.0", related: ["ph-args","cmd-reload","struct-options","struct-function"]
  },
  {
    id: "struct-function", category: "structures",
    name: { ru: "Функция", en: "Function" },
    syntax: [
      "function <имя>(<п1>: <тип>, <п2>: <тип>, ...):",
      "<имя>(<аргумент1>, <аргумент2>, <аргумент3>, ...)"
    ],
    desc: {
      ru: "Объявляет переиспользуемую функцию внутри скрипта. Поддерживается **любое количество параметров** (0, 1, 2, 3 и более — через запятую). Двоеточие в заголовке необязательно, тело пишется с отступом. Все типы параметров и синонимы: `player`, `offline player` (`offline`), `entity`, `block`, `location`, `text` (`string`), `integer` (`int`), `number`, `boolean` (`bool`), `world`, `item`. Вызывается отдельной строкой или из `run MyFunc(...)` в GUI. Аргументами могут быть любые выражения (`player`, `targeted player`, `location of targeted block`, `{переменная}`). Параметр `player` работает как цель (`send \"hi\" to who`, `health of who`, `%who%`). Имя функции нечувствительно к регистру. `stop` завершает только эту функцию.",
      en: "Declares a reusable function inside the script. Supports **any number of parameters** (0, 1, 2, 3 or more, comma-separated). Colon is optional; body is indented. All parameter types & aliases: `player`, `offline player` (`offline`), `entity`, `block`, `location`, `text` (`string`), `integer` (`int`), `number`, `boolean` (`bool`), `world`, `item`. Called on its own line or from GUI `run MyFunc(...)`. Arguments can be any expression. A `player` parameter acts as a target (`send \"hi\" to who`, `health of who`, `%who%`). Case-insensitive name. `stop` exits only this function."
    },
    example: `# Функция с игроком и числом
function GiveReward(who: player, coins: integer, label: text):
	add coins to {coins::%who%}
	send "&a+%coins% монет (%label%)!" to who
	play sound entity.experience_orb.pickup to who

# Функция с локацией и блоком
function Mark(spot: location, id: block):
	set block at spot to id

command /bonus:
	GiveReward(player, 50, "ежедневный бонус")
	Mark(location of targeted block, gold_block)`,
    since: "0.4.4", related: ["eff-send","eff-set-block","expr-targeted-player","expr-targeted-entity","expr-location"]
  },
  {
    id: "struct-options", category: "structures",
    name: { ru: "Заготовки options", en: "Options" },
    syntax: [
      "options:",
      "<имя>: <значение>",
      "{@<имя>}"
    ],
    desc: {
      ru: "Заготовки файла, как в Skript. Имя — буквы, цифры, _ и -. Значение подставляется вместо `{@имя}` до разбора строки, в том числе внутри кавычек и в других заготовках. Неизвестное `{@имя}` — ошибка в журнале.",
      en: "Per-file templates, as in Skript. A name is letters, digits, _ and -. The value replaces `{@name}` before the line is parsed, including inside quotes and inside other options. An unknown `{@name}` is a journal error."
    },
    example: `options:
	prefix: &7[&6Сервер&7]
	currency: монет
	price: 25
	shop_msg: {@prefix} &aКуплено за {@price} {@currency}!

on join:
	send "{@prefix} &fДобро пожаловать, %player%!" to player

command /buy_diamond:
	if {coins::%player%} >= {@price}:
		remove {@price} from {coins::%player%}
		give player diamond 1
		send "{@shop_msg}" to player`,
    since: "0.4.1", related: ["eff-send","struct-command"]
  },
  {
    id: "struct-if", category: "structures",
    name: { ru: "Условие if / else", en: "if / else" },
    syntax: [
      "if <условие>:",
      "else if <условие>:",
      "else:"
    ],
    desc: {
      ru: "Выполняет вложенные действия, если условие верно. `else:` и `else if` пишутся на том же отступе сразу после блока. Можно вкладывать. Несколько проверок в одной строке — через `and` и `or` (`and` связывается теснее).",
      en: "Runs the nested actions when the condition is true. `else:` and `else if` go at the same indent right after the block. Can be nested. Several checks in one line use `and` and `or` (`and` binds tighter)."
    },
    example: `# Цепочка if / else if / else
command /rank:
	if {coins::%player%} >= 1000:
		send "&6Ранг: Легенда" to player
	else if {coins::%player%} >= 200:
		send "&bРанг: Мастер" to player
	else:
		send "&7Ранг: Новичок" to player

# Составное условие с and / or и списком значений
if player is alive and {role::%player%} is "mage" or "archer":
	send "&aКлассовый навык готов!" to player`,
    since: "0.1.0", related: ["struct-every","cond-compare","cond-and","cond-player"]
  },
  {
    id: "struct-every", category: "structures",
    name: { ru: "Таймер every", en: "every timer" },
    syntax: [
      "every <число> seconds:",
      "every <число> minutes:",
      "every <число> ticks:",
      "every second:",
      "every minute:",
      "every tick:"
    ],
    desc: {
      ru: "Повторяет блок по таймеру сервера (`20 ticks = 1 second`). Допустимые единицы: `tick`/`ticks`, `second`/`seconds`, `minute`/`minutes` (от 1 тика до 1 часа). Период задаётся числом в заголовке. Внутри нет `player` — используйте `all players`, `loop all players:` или `broadcast`. `every tick:` выполняется 20 раз в секунду — держите тело максимально лёгким.",
      en: "Repeats a block on the server timer (`20 ticks = 1 second`). Units: `tick`/`ticks`, `second`/`seconds`, `minute`/`minutes` (1 tick to 1 hour). Period is a literal number in the header. No `player` in context — use `all players`, `loop all players:` or `broadcast`. Keep `every tick:` very lightweight."
    },
    example: `# Обновление скорборда каждую секунду
every 1 second:
	loop all players:
		set scoreboard line 1 to "&eМонеты: &f%{coins::%loop-player%}%" for loop-player

# Автообъявление каждые 5 минут
every 5 minutes:
	broadcast "&6[Совет] &fИспользуйте /menu для открытия меню сервера!"

# Быстрый тик мини-игры (каждые 4 тика = 5 раз в секунду)
every 4 ticks:
	loop all players:
		if {-game::%loop-player%} is true:
			TickGame(loop-player)`,
    since: "0.2.0", related: ["struct-wait","struct-loop","expr-property"]
  },
  {
    id: "struct-wait", category: "structures",
    name: { ru: "Пауза wait", en: "wait" },
    syntax: [
      "wait <число> seconds",
      "wait <число> ticks",
      "wait <число> minutes"
    ],
    desc: {
      ru: "Приостанавливает выполнение строк **ниже на том же отступе** на указанное время (без двоеточия). Единицы времени: `tick`/`ticks`, `second`/`seconds`, `minute`/`minutes` (максимум 5 минут). Число может быть `{переменной}` или выражением. Код вне текущего блока не ждёт. Если игрок вышел с сервера во время паузы — остаток не выполнится. `loop-player` после паузы не сохраняется.",
      en: "Pauses lines **below at the same indent** for the given duration (no colon). Units: `tick`/`ticks`, `second`/`seconds`, `minute`/`minutes` (max 5 minutes). Number may be a `{variable}` or expression. Code outside the block does not wait. If the player disconnects during the wait, remaining lines are skipped. `loop-player` is not preserved across `wait`."
    },
    example: `# Обратный отсчёт перед телепортом
command /spawn:
	send "&eТелепорт через 3 секунды..." to player
	wait 1 second
	send action bar "&e2..." to player
	wait 1 second
	send action bar "&e1..." to player
	wait 1 second
	teleport player to world spawn
	send "&aВы на спавне!" to player

# Пауза с переменной в тиках
command /blink:
	fade screen of player to #000000 in 5 stay 10 out 5 ticks
	wait 10 ticks
	teleport player to location of targeted block`,
    since: "0.2.0", related: ["struct-every","eff-send"]
  },
  {
    id: "struct-chance", category: "structures",
    name: { ru: "Шанс", en: "Chance" },
    syntax: ["chance of <число>%:"],
    desc: {
      ru: "Вложенные действия выполняются с шансом 0–100. Знак `%` можно не писать. Число — литерал, `{переменная}` или выражение.",
      en: "The nested actions run with a 0–100 chance. The `%` sign is optional. The number is a literal, a `{variable}` or an expression."
    },
    example: `# Редкий дроп при добыче блока
on break:
	if %block% is "minecraft:stone":
		chance of 15%:
			give player iron_nugget 1
			send action bar "&7Найден самородок железа!" to player

# Шанс из переменной (например, уровень удачи)
on kill:
	chance of {luck_chance::%player%}%:
		give player emerald 1
		send "&aКритический лут: +1 изумруд!" to player`,
    since: "0.2.0", related: ["event-break","expr-random"]
  },
  {
    id: "struct-loop", category: "structures",
    name: { ru: "Цикл", en: "Loop" },
    syntax: [
      "loop <цель>:",
      "loop {<имя>::*}:"
    ],
    desc: {
      ru: "`loop <цель>:` — для каждого игрока. Внутри `loop-player` и `%loop-player%`. `loop {имя::*}:` — по прямым индексам списка: `%loop-index%` — ключ, `%loop-value%` — значение. `player` остаётся тем, кто вызвал скрипт. Каждый проход тратит шаги `maxScriptSteps`.",
      en: "`loop <target>:` runs for every player. Inside: `loop-player` and `%loop-player%`. `loop {name::*}:` walks the direct indexes of a list: `%loop-index%` is the key, `%loop-value%` is the value. `player` stays whoever ran the script. Every pass costs `maxScriptSteps` steps."
    },
    example: `# Цикл по всем игрокам онлайн
command /gather:
	permission: 2
	loop all players:
		teleport loop-player to player
		send "&e%loop-player%, вас призвал %player%!" to loop-player

# Цикл по игрокам в радиусе
command /buff:
	loop players in radius 15 of player:
		give loop-player effect strength for 20 seconds

# Цикл по списку/словарю
command /mybag:
	loop {bag::%player%::*}:
		send "&f%loop-index% &7x%loop-value%" to player`,
    since: "0.1.2", related: ["target-loop-player","ph-loop-player","ph-loop-index","expr-variable","target-all"]
  },
  {
    id: "struct-define-block", category: "structures",
    name: { ru: "Свой блок", en: "Custom block" },
    syntax: [
      "define block <id>:",
      "define block <namespace>:<id>:"
    ],
    desc: {
      ru: "Регистрирует кастомный блок (и его предмет-блок) во вкладке ScriptCraft. Все свойства (необязательные):\n• `name:` — отображаемое имя (в кавычках).\n• `hardness:` — твёрдость/время ломания (по умолчанию `1.5`, камень `1.5`, обсидиан `50`).\n• `resistance:` — взрывоустойчивость (по умолчанию `6`).\n• `sound:` — звук шагов/ломания: `stone` (по умолчанию), `wood`, `metal`, `glass`, `grass`, `sand`, `wool`, `gravel`, `honey`, `slime`, `snow`.\n• `light:` — уровень свечения `0`–`15` (по умолчанию `0`).\n• `requires_tool:` — `true` / `false` (нужен ли подходящий инструмент для дропа).\n• `facing:` — поворот при установке: `none` (`false`, `no` — без поворота), `horizontal` (`true`, `yes` — 4 стороны света, как печь), `all` (все 6 сторон, как раздатчик). Модель для поворота должна смотреть на север.\n• `render:` — режим отрисовки: `solid` (`opaque`, `cube` — обычный куб), `cutout` (`transparent`, `cutout_mipped` — прозрачные вырезы, как листва), `translucent` (`glass` — полупрозрачное стекло).\n• `full:` — `true` / `false` (`false` не прячет грани соседних блоков под 3D-моделью).\n• Физика: `friction:` скольжение `0`–`1` (камень `0.6`, лёд `0.98`), `speed:` множитель скорости ходьбы `0`–`8` (песок душ `0.4`), `jump:` множитель прыжка `0`–`2` (мёд `0.5`), `gravity:` `true`/`false` (падает как песок), `collision:` `true`/`false` (`false` — можно проходить насквозь), `flammable:` `true`/`false` или `burn:` и `spread:` (горючесть и распространение огня; доски: `burn: 5`, `spread: 20`; шерсть: `burn: 30`, `spread: 60`).\nДопустимые символы id: `a-z 0-9 _ . -`. Блок, объявленный до старта сервера, получает настоящий id (`example:ruby_block`); новый после `/sc reload` живёт во временном слоте `scriptcraft:dyn_block_N` до перезапуска.",
      en: "Registers a custom block (and its block item) in the ScriptCraft creative tab. All properties (optional):\n• `name:` — display name.\n• `hardness:` — break hardness (default `1.5`, obsidian `50`).\n• `resistance:` — blast resistance (default `6`).\n• `sound:` — sound group: `stone` (default), `wood`, `metal`, `glass`, `grass`, `sand`, `wool`, `gravel`, `honey`, `slime`, `snow`.\n• `light:` — light emission `0`–`15` (default `0`).\n• `requires_tool:` — `true` / `false`.\n• `facing:` — placement rotation: `none` (`false`, `no`), `horizontal` (`true`, `yes` — 4 directions), `all` (6 directions). Rotating models must face north.\n• `render:` — render layer: `solid` (`opaque`, `cube`), `cutout` (`transparent`, `cutout_mipped`), `translucent` (`glass`).\n• `full:` — `true` / `false` (`false` avoids culling adjacent faces under 3D models).\n• Physics: `friction:` `0`–`1` (stone `0.6`, ice `0.98`), `speed:` `0`–`8` (soul sand `0.4`), `jump:` `0`–`2` (honey `0.5`), `gravity:` `true`/`false`, `collision:` `true`/`false` (`false` = walk-through), `flammable:` `true`/`false` or `burn:` & `spread:` (planks `5`/`20`, wool `30`/`60`).\nValid id chars: `a-z 0-9 _ . -`. Startup blocks get real ids; blocks added on `/sc reload` use temporary slots `scriptcraft:dyn_block_N` until restart."
    },
    example: `# Твёрдый светящийся блок с поворотом по 4 сторонам
define block ruby_block:
	name: "Рубиновый блок"
	hardness: 5
	resistance: 6
	sound: metal
	light: 7
	requires_tool: true
	facing: horizontal
	render: solid
	full: true

# Прозрачный 3D-фонарь с поворотом во все 6 сторон
define block ruby_lantern:
	name: "Рубиновый фонарь"
	sound: glass
	light: 15
	facing: all
	render: cutout
	full: false

# Скользкий лёд и замедляющая грязь
define block super_ice:
	name: "Суперлёд"
	sound: glass
	friction: 0.98
	speed: 1.4

# Сыпучий песок с гравитацией
define block quicksand:
	name: "Зыбучий песок"
	sound: sand
	gravity: true
	collision: false
	speed: 0.35

# Горючий блок (как шерсть)
define block magic_wool:
	name: "Магическая шерсть"
	sound: wool
	burn: 30
	spread: 60`,
    since: "0.1.0", related: ["struct-define-item","struct-namespace"]
  },
  {
    id: "struct-define-item", category: "structures",
    name: { ru: "Свой предмет", en: "Custom item" },
    syntax: [
      "define item <id>:",
      "define item <namespace>:<id>:"
    ],
    desc: {
      ru: "Регистрирует кастомный предмет во вкладке ScriptCraft. Все свойства:\n• `name:` — отображаемое имя.\n• `type:` — тип поведения предмета (поддерживаются синонимы):\n  — `item` (`normal`, `basic`) — обычный предмет (по умолчанию).\n  — `food` (`eat`, `edible`) — еда (съедобный предмет).\n  — `sword` — меч (повышенный урон, режет паутину).\n  — `pickaxe` (`pick`) — кирка (копает камень и руды).\n  — `axe` — топор (рубит дерево, высокий урон).\n  — `shovel` (`spade`) — лопата (копает землю/песок/снег).\n  — `hoe` — мотыга (копает листву/сено/губки).\n  — `fishing_rod` (`fishing`, `rod`) — удочка (закидывает поплавок).\n  — `shears` (`shear`, `scissors`) — ножницы (стрижёт шерсть, листву, ковры, паутину).\n• `rarity:` — редкость и цвет названия: `common` (белый, по умолчанию), `uncommon` (жёлтый), `rare` (голубой), `epic` (фиолетовый).\n• `stack_size:` — максимальный стак `1`–`64` (по умолчанию `64`, для инструментов `1`).\n• `fire_resistant:` — `true` / `false` (не горит в лаве и огне, как незерит).\n• Для еды (`type: food`): `nutrition:` голод `0`–`20` (2 = 1 ножка, хлеб = 5, по умолчанию 4), `saturation:` насыщение `0`–`2` (хлеб = 0.6), `always_eat:` `true`/`false` (можно есть даже сытым).\n• Для инструментов (`sword`, `pickaxe`, `axe`, `shovel`, `hoe`, `fishing_rod`, `shears`): `tier:` материал `wood` (`wooden`), `stone`, `iron` (по умолчанию), `gold` (`golden`), `diamond`, `netherite`; `durability:` прочность (`0` — не ломается; если не указывать — ванильная: wood `59`, stone `131`, iron `250`, gold `32`, diamond `1561`, netherite `2031`, fishing_rod `64`, shears `238`).",
      en: "Registers a custom item in the ScriptCraft creative tab. All properties:\n• `name:` — display name.\n• `type:` — item behaviour (with aliases):\n  — `item` (`normal`, `basic`) — plain item (default).\n  — `food` (`eat`, `edible`) — edible food.\n  — `sword` — sword.\n  — `pickaxe` (`pick`) — pickaxe.\n  — `axe` — axe.\n  — `shovel` (`spade`) — shovel.\n  — `hoe` — hoe.\n  — `fishing_rod` (`fishing`, `rod`) — fishing rod.\n  — `shears` (`shear`, `scissors`) — shears.\n• `rarity:` — `common` (white, default), `uncommon` (yellow), `rare` (aqua), `epic` (light purple).\n• `stack_size:` — `1`–`64` (default `64`, `1` for tools).\n• `fire_resistant:` — `true` / `false` (survives lava/fire).\n• Food options (`type: food`): `nutrition:` `0`–`20` (default `4`, bread `5`), `saturation:` `0`–`2` (default `0.6`), `always_eat:` `true`/`false`.\n• Tool options (`sword`, `pickaxe`, `axe`, `shovel`, `hoe`, `fishing_rod`, `shears`): `tier:` `wood` (`wooden`), `stone`, `iron` (default), `gold` (`golden`), `diamond`, `netherite`; `durability:` (`0` = unbreakable; defaults: wood `59`, stone `131`, iron `250`, gold `32`, diamond `1561`, netherite `2031`, fishing_rod `64`, shears `238`)."
    },
    example: `# Обычный предмет (валюта/артефакт)
define item ruby:
	name: "Рубин"
	type: item
	stack_size: 64
	rarity: rare
	fire_resistant: true

# Еда, которую можно есть даже при полном голоде
define item energy_bar:
	name: "Энергетический батончик"
	type: food
	nutrition: 8
	saturation: 0.8
	always_eat: true
	rarity: uncommon

# Инструмент (меч / кирка / топор / лопата / мотыга)
define item ruby_pickaxe:
	name: "Рубиновая кирка"
	type: pickaxe
	tier: netherite
	durability: 2500
	rarity: epic
	fire_resistant: true

# Удочка и ножницы
define item pro_rod:
	name: "Золотая удочка"
	type: fishing_rod
	durability: 200

define item crystal_shears:
	name: "Кристальные ножницы"
	type: shears
	durability: 500`,
    since: "0.1.0", related: ["struct-define-block","struct-namespace","eff-give-item"]
  },
  {
    id: "struct-namespace", category: "structures",
    name: { ru: "Свой namespace", en: "Custom namespace" },
    syntax: [
      "namespace <ns>",
      "namespace <ns>:",
      "modid <ns>:"
    ],
    desc: {
      ru: "Строка без отступа: все следующие `define` в этом файле получат id `<ns>:...` (в следующем файле снова `scriptcraft`). Запрещены `minecraft`, `forge`, `neoforge`, `c`; только латиница `a-z 0-9 _ . -`. Ресурсы таких блоков кладутся в `scriptcraft/resources/assets/<ns>/`.",
      en: "A line without indent: every following `define` in this file gets the id `<ns>:...` (the next file starts with `scriptcraft` again). `minecraft`, `forge`, `neoforge`, `c` are not allowed; only `a-z 0-9 _ . -`. Resources for those blocks go to `scriptcraft/resources/assets/<ns>/`."
    },
    example: `namespace rpg

# Автоматически получат префикс rpg:
define item mana_crystal:     # -> rpg:mana_crystal
	name: "Кристалл маны"
	rarity: rare

define block altar_stone:     # -> rpg:altar_stone
	name: "Алтарь"
	hardness: 10

# Явный id в обход namespace
define item custom:key:       # -> custom:key
	name: "Ключ"`,
    since: "0.1.2", related: ["struct-define-block","struct-define-item"]
  },
  {
    id: "struct-comment", category: "structures",
    name: { ru: "Комментарий и отступы", en: "Comments and indentation" },
    syntax: ["# комментарий"],
    desc: {
      ru: "Строка, начинающаяся с `#`, — комментарий. Блоки верхнего уровня (`on`, `command`, `define`, `every`) пишутся без отступа и заканчиваются двоеточием, действия — с отступом. В примерах отступ — Tab; один Tab равен четырём пробелам, пробелы тоже понимаются. Ошибка в строке не ломает файл: строка пропускается, ошибка видна в логе и при `/sc reload`. Файлы — UTF-8, `*.sc`; файл с именем на `-` выключен. Старые `*.sk` при запуске мода переименовываются в `*.sc`.",
      en: "A line starting with `#` is a comment. Top-level blocks (`on`, `command`, `define`, `every`) have no indent and end with a colon; actions are indented. Examples use Tab; one Tab equals four spaces, and spaces still work. An error in one line does not break the file: the line is skipped and reported in the log and on `/sc reload`. Files are UTF-8 `*.sc`; a file whose name starts with `-` is disabled. Old `*.sk` files are renamed to `*.sc` when the mod starts."
    },
    example: `# Комментарий на всю строку
# Блоки верхнего уровня пишутся без отступа и с двоеточием:
command /demo:
	# Действия внутри блока пишутся с отступом (Tab или 4 пробела)
	if player is op:
		send "&aТы оператор!" to player
	else:
		send "&7Ты обычный игрок" to player`,
    since: "0.1.0", related: ["cmd-disable"]
  },
  {
    id: "eff-set-var", category: "effects",
    name: { ru: "Записать переменную", en: "Set variable" },
    syntax: ["set {<имя>} to <значение>"],
    desc: {
      ru: "Записывает значение (число, \"текст\", true/false или другую `{переменную}`). Текст с пробелами — в кавычках. `set {a} to {не_заданная}` удаляет `{a}`.",
      en: "Stores a value (number, \"text\", true/false or another `{variable}`). Text with spaces must be quoted. `set {a} to {unset}` deletes `{a}`."
    },
    example: `# Запись чисел, строк, булевых значений и выражений
set {coins::%player%} to 100
set {motd} to "Добро пожаловать на сервер!"
set {vip::%player%} to true
set {home::%player%} to player's location

# Копирование переменной (если справа пусто — переменная удалится)
set {backup::%player%} to {coins::%player%}`,
    since: "0.1.0", related: ["expr-variable","eff-add-var","eff-remove-var","eff-delete-var"]
  },
  {
    id: "eff-add-var", category: "effects",
    name: { ru: "Прибавить к переменной", en: "Add to variable" },
    syntax: [
      "add <значение> to {<имя>}",
      "add <значение> {<имя>}",
      "add <значение> to {<имя>::*}"
    ],
    desc: {
      ru: "Число к числу: незаданная переменная считается 0. Текст дописывается в конец. `{список::*}` — список, не число: элемент кладётся под следующим номером (1, 2, 3…). Слово `to` можно не писать, если сразу после значения стоит переменная. В значение годится текст, `player` (имя), `loop-player`, `targeted block`, `player's tool` и локация.",
      en: "A number added to a number treats an unset variable as 0. Text is appended. `{list::*}` is a list, not a number: the element is stored at the next index (1, 2, 3…). `to` can be left out when the variable comes right after the value. The value may be text, `player` (the name), `loop-player`, `targeted block`, `player's tool` or a location."
    },
    example: `# Прибавление к числу (незаданная переменная считается 0)
add 25 to {coins::%player%}

# Склейка текста
add " [VIP]" to {title::%player%}

# Добавление элемента в конец списка {list::*}
add player's tool to {tools::%player%::*}
add %player% to {queue::*}`,
    since: "0.1.0", related: ["eff-remove-var","eff-set-var","expr-variable"]
  },
  {
    id: "eff-remove-var", category: "effects",
    name: { ru: "Отнять от переменной", en: "Remove from variable" },
    syntax: [
      "remove <значение> from {<имя>}",
      "remove <значение> {<имя>}",
      "remove <значение> from {<имя>::*}"
    ],
    desc: {
      ru: "Из числа вычитает. Из текста вырезает первое вхождение. Из списка `{список::*}` убирает одно равное значение. Слово `from` можно не писать, если сразу после значения стоит переменная.",
      en: "Subtracts from a number. Cuts the first occurrence out of text. Removes one equal value from a list `{list::*}`. `from` can be left out when the variable comes right after the value."
    },
    example: `# Вычитание числа
remove 10 from {coins::%player%}

# Удаление подстроки из текста
remove "[VIP]" from {title::%player%}

# Удаление одного совпавшего значения из списка
remove %player% from {queue::*}`,
    since: "0.1.0", related: ["eff-add-var","eff-set-var","expr-variable"]
  },
  {
    id: "eff-delete-var", category: "effects",
    name: { ru: "Удалить переменную", en: "Delete variable" },
    syntax: [
      "delete {<имя>}",
      "clear {<имя>}",
      "delete {<имя>::*}"
    ],
    desc: {
      ru: "Удаляет переменную (после этого `is not set`). `delete {list::*}` удаляет все прямые индексы, сам список отдельно не хранится.",
      en: "Deletes a variable (it becomes `is not set`). `delete {list::*}` removes every direct index; the list is not stored on its own."
    },
    example: `# Удалить одну переменную (после этого is not set)
delete {coins::%player%}
clear {-session::%player%}

# Очистить весь список / все подключи
delete {bag::%player%::*}`,
    since: "0.1.0", related: ["eff-set-var","cond-var-set"]
  },
  {
    id: "eff-nbt", category: "effects",
    name: { ru: "Записать NBT", en: "Set NBT" },
    syntax: [
      "set nbt \"<путь>\" of targeted entity to <значение>",
      "set nbt \"<путь>\" of targeted block to <значение>",
      "set nbt \"<путь>\" of targeted player to <значение>",
      "set nbt \"<путь>\" of player to <значение>"
    ],
    desc: {
      ru: "Пишет NBT-тег сущности, игрока или блока в прицеле. Путь как в `/data`: `CustomName`, `Items[0].id`. Число пишется числом, `true`/`false` — байтом, текст — строкой, `{...}` и `[...]` — как SNBT. У блока без данных будет ошибка. Весь тег целиком: `%nbt of targeted entity%`, один путь: `%nbt CustomName of targeted entity%`. NBT предметов в инвентаре — отдельное выражение.",
      en: "Writes an NBT tag on the entity, player or block in the crosshair. The path is the same as `/data`: `CustomName`, `Items[0].id`. A number is stored as a number, `true`/`false` as a byte, text as a string, `{...}` and `[...]` as SNBT. A block with no data is an error. The whole tag: `%nbt of targeted entity%`. One path: `%nbt CustomName of targeted entity%`."
    },
    example: `# Переименовать моба в прицеле и сделать его бессмертным
set nbt "CustomName" of targeted entity to ""&6Страж""
set nbt "Invulnerable" of targeted entity to true
set nbt "NoAI" of targeted entity to true

# Прочитать весь NBT или отдельный тег
send "%nbt of targeted entity%" to player
send "%nbt CustomName of targeted entity%" to player`,
    since: "0.3.9", related: ["eff-nbt-remove","ph-targeted","target-targeted","expr-item-nbt"]
  },
  {
    id: "eff-nbt-remove", category: "effects",
    name: { ru: "Стереть NBT", en: "Remove NBT" },
    syntax: [
      "remove nbt \"<путь>\" of targeted entity",
      "remove nbt \"<путь>\" of targeted block",
      "remove nbt \"<путь>\" of targeted player",
      "remove nbt \"<путь>\" of player"
    ],
    desc: {
      ru: "Стирает один NBT-путь у сущности, игрока или блока в прицеле. Путь как в `/data`: `CustomName`, `Items[0].id`. У блока без данных будет ошибка.",
      en: "Removes one NBT path from the entity, player or block in the crosshair. The path is the same as `/data`: `CustomName`, `Items[0].id`. A block with no data is an error."
    },
    example: `# Снять кастомное имя или тег с сущности / блока в прицеле
remove nbt "CustomName" of targeted entity
remove nbt "Lock" of targeted block`,
    since: "0.3.9", related: ["eff-nbt","ph-targeted","target-targeted"]
  },
  {
    id: "eff-send", category: "effects",
    name: { ru: "Сообщение игрокам", en: "Send message" },
    syntax: [
      "send \"<текст>\"",
      "send \"<текст>\" to <цель>",
      "message \"<текст>\" to <цель>"
    ],
    desc: {
      ru: "Личное сообщение игрокам цели (без `to` — игроку из контекста). Поддерживает плейсхолдеры, `{переменные}`, `{@заготовку}` и цвета `&`. Кавычка внутри текста пишется дважды: `send \"оружие \"\"karabin\"\" старое\" to player`. Плейсхолдеры считаются от вызвавшего, а не от получателя.",
      en: "Private message to the target players (without `to` — the context player). Supports placeholders, `{variables}`, `{@option}` and `&` colours. A quote inside the text is written twice: `send \"weapon \"\"karabin\"\" is old\" to player`. Placeholders are evaluated for the caller, not the recipient."
    },
    example: `# Личное сообщение с цветами, переменными и плейсхолдерами
send "&aПривет, %player%! &7У тебя &e%{coins::%player%}% &7монет" to player

# Кавычки внутри строки (пишутся дважды)
send "&7Оружие ""АК-47"" экипировано" to player

# Отправка группе игроков
send "&eСбор на арене через 10 секунд!" to all players
send "&cРядом враг!" to other players in radius 20 of player`,
    since: "0.1.0", related: ["eff-broadcast","eff-reply","expr-colors"]
  },
  {
    id: "eff-broadcast", category: "effects",
    name: { ru: "Сообщение всем", en: "Broadcast" },
    syntax: ["broadcast \"<текст>\""],
    desc: {
      ru: "Сообщение в чат всем игрокам сервера.",
      en: "A chat message to every player on the server."
    },
    example: `# Объявление всем игрокам на сервере
broadcast "&6[Сервер] &fПерезапуск через 5 минут!"
broadcast "&a%player% &7выиграл турнир и получил &e500 монет&7!"`,
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
    example: `# Работает и для игрока, и для консоли сервера
command /status:
	reply "&aСервер работает! Онлайн: %online%"

command /addcoins <text> <integer>:
	permission: 2
	executable by: players and console
	add arg-2 to {coins::%arg-1%}
	reply "Игроку %arg-1% начислено %arg-2% монет"`,
    since: "0.1.0", related: ["eff-send"]
  },
  {
    id: "eff-title", category: "effects",
    name: { ru: "Титры", en: "Title" },
    syntax: [
      "send title \"<текст>\" to <цель>",
      "send title \"<текст>\" with subtitle \"<текст>\" to <цель>"
    ],
    desc: {
      ru: "Большой текст по центру экрана (0.5 с появление, 3.5 с показ, 1 с исчезновение).",
      en: "Big text in the middle of the screen (0.5 s fade in, 3.5 s stay, 1 s fade out)."
    },
    example: `# Заголовок и подзаголовок по центру экрана
send title "&6&lПОБЕДА!" with subtitle "&eВы прошли испытание" to player

# Только заголовок всем игрокам
send title "&cБОСС ПОЯВИЛСЯ" to all players`,
    since: "0.1.2", related: ["eff-actionbar","eff-send"]
  },
  {
    id: "eff-actionbar", category: "effects",
    name: { ru: "Строка над хотбаром", en: "Action bar" },
    syntax: [
      "send action bar \"<текст>\" to <цель>",
      "send actionbar \"<текст>\" to <цель>"
    ],
    desc: {
      ru: "Короткий текст над панелью быстрого доступа.",
      en: "Short text above the hotbar."
    },
    example: `# Короткий статус над хотбаром
send action bar "&a✔ Точка дома сохранена!" to player
send actionbar "&c❤ %health%/%max_health%  &7|  &e⚡ %food%/20" to all players`,
    since: "0.1.2", related: ["eff-title"]
  },
  {
    id: "eff-sound", category: "effects",
    name: { ru: "Звук", en: "Play sound" },
    syntax: [
      "play sound <id звука> to <цель>",
      "play sound <id> to <цель> with volume <громкость>",
      "play sound <id> to <цель> with volume <громкость> pitch <высота>"
    ],
    desc: {
      ru: "Проигрывает звук лично указанным игрокам (другие не слышат). Поддерживаются все ванильные звуки (`entity.player.levelup`, `entity.experience_orb.pickup`, `ui.button.click`, `block.anvil.land`, `entity.ender_dragon.growl`, `block.note_block.pling`), звуки модов (`mod:sound`) и кастомные `.ogg` из `scriptcraft/resources/assets/<ns>/sounds.json`. Параметры: `volume` — громкость `0`–`10` (по умолчанию `1`), `pitch` — высота `0.5`–`2` (по умолчанию `1`).",
      en: "Plays a sound privately to the target players. Supports all vanilla sounds (`entity.player.levelup`, `entity.experience_orb.pickup`, `ui.button.click`, `block.anvil.land`, `entity.ender_dragon.growl`, `block.note_block.pling`), modded sounds (`mod:sound`), and custom `.ogg` sounds from `scriptcraft/resources/assets/<ns>/sounds.json`. `volume`: `0`–`10` (default `1`), `pitch`: `0.5`–`2` (default `1`)."
    },
    example: `# Ванильные звуки с громкостью и высотой
play sound entity.player.levelup to player
play sound entity.experience_orb.pickup to player with volume 0.5 pitch 1.3
play sound block.note_block.pling to all players with volume 1 pitch 1.5

# Кастомный звук из ресурсов скрипта
play sound mypack:victory_fanfare to player with volume 0.9 pitch 1.0`,
    since: "0.1.2", related: ["eff-title"]
  },
  {
    id: "eff-give-item", category: "effects",
    name: { ru: "Выдать предмет", en: "Give item" },
    syntax: [
      "give <цель> <предмет>",
      "give <цель> <предмет> <количество>",
      "give <цель> <предмет>{<nbt>}",
      "give <цель> player's tool",
      "give <цель> {<переменная>}"
    ],
    desc: {
      ru: "Выдаёт предмет (если инвентарь полон — бросает рядом). Количество — число или `{переменная}` (до 2304). В id можно плейсхолдер: `give player %arg-1%`. Сразу за id можно написать SNBT: `give player tacz:modern_kinetic_gun{GunId:\"tacz:glock_17\"} 1`. Если двоеточие перед кавычкой пропущено (`GunId\"tacz:glock_17\"`), оно подставляется. То же nbt принимает `drop`, слот и проверки `has` / `holding`. Предмет может быть выражением: `player's tool`, `{слот}`. Короткий id ищется как `minecraft:`, потом `scriptcraft:`, потом namespace скриптов.",
      en: "Gives an item (drops it nearby if the inventory is full). Count is a number or `{variable}` (up to 2304). The id may contain a placeholder: `give player %arg-1%`. SNBT may follow the id: `give player tacz:modern_kinetic_gun{GunId:\"tacz:glock_17\"} 1`. A missing colon before the quote (`GunId\"tacz:glock_17\"`) is inserted. `drop`, slots and `has` / `holding` accept the same nbt. The item may be an expression: `player's tool`, `{slot}`. A short id is looked up as `minecraft:`, then `scriptcraft:`, then script namespaces."
    },
    example: `# Обычные и кастомные предметы
give player diamond 5
give all players example:ruby 1

# Предмет с NBT (например, неломаемый или из мода)
give player diamond_sword{Unbreakable:1b} 1
give player tacz:modern_kinetic_gun{GunId:"tacz:glock_17"} 1

# Выдать предмет из переменной, аргумента или руки игрока
give player %arg-1% {amount}
give player player's tool`,
    since: "0.1.0", related: ["struct-define-item","eff-give-effect","eff-set-slot"]
  },
  {
    id: "eff-set-slot", category: "effects",
    name: { ru: "Слот инвентаря", en: "Inventory slot" },
    syntax: [
      "set slot <слот> of <цель> to <предмет>",
      "set slot <слот> of <цель> to <количество> <предмет>",
      "set slot hotbar <1-9> of <цель> to <предмет>",
      "clear slot <слот> of <цель>"
    ],
    desc: {
      ru: "Кладёт один стак в слот. Номера 0–8 — хотбар, 9–35 — инвентарь, 36 ботинки, 37 штаны, 38 нагрудник, 39 шлем, 40 левая рука. `slot hotbar 1` — это слот 0. Имена: `helmet`, `chestplate`, `leggings`, `boots`, `offhand`, `mainhand`. Количество больше максимума стака обрезается. `air` и количество 0 очищают слот. Предмет — выражение: `diamond`, `diamond{Unbreakable:1b}`, `player's tool`, `{переменная}`, параметр функции. `named \"текст\"` ставит имя (цвета `&`, без курсива). `with nbt {тег}` дописывает NBT после тега в id. Имя пишется последним и перекрывает `display.Name` из тега. Можно `of player's inventory` и `of who`, если `who` — параметр-игрок.",
      en: "Puts one stack into a slot. Indices 0–8 are the hotbar, 9–35 storage, 36 boots, 37 leggings, 38 chestplate, 39 helmet, 40 offhand. `slot hotbar 1` is slot 0. Names: `helmet`, `chestplate`, `leggings`, `boots`, `offhand`, `mainhand`. A count above the stack limit is clamped. `air` and a count of 0 clear the slot. The item is an expression: `diamond`, `diamond{Unbreakable:1b}`, `player's tool`, `{variable}`, a function parameter. `named \"text\"` sets the display name (`&` colours, not italic). `with nbt {tag}` merges NBT after the tag in the id. The name is applied last and overrides `display.Name` from the tag. `of player's inventory` and `of who` work when `who` is a player parameter."
    },
    example: `# Заполнение хотбара, брони и левой руки
set slot hotbar 1 of player to 1 diamond_sword{Unbreakable:1b} named "&bМеч героя"
set slot 1 of player to 16 cooked_beef
set slot helmet of player to 1 netherite_helmet named "&5Корона"
set slot offhand of player to 1 shield

# Очистить конкретный слот
clear slot 40 of player
clear slot hotbar 9 of player`,
    since: "0.4.5", related: ["eff-give-item","expr-item-nbt","eff-nbt"]
  },
  {
    id: "eff-teleport", category: "effects",
    name: { ru: "Телепорт", en: "Teleport" },
    syntax: [
      "teleport <цель> to <x> <y> <z>",
      "teleport <цель> to <цель>",
      "teleport <цель> to <локация>",
      "teleport <цель> to spawn",
      "teleport <цель> to world spawn",
      "tp <цель> to ..."
    ],
    desc: {
      ru: "Телепорт по координатам (в текущем мире игрока; запятые между числами можно писать), к первому игроку второй цели (в его мир, с его поворотом) или в локацию: `player's location`, `location of targeted block`, `{дом}`. `to spawn` — точка возрождения, `to world spawn` — общий спавн обычного мира. Пустая вторая цель — ничего.",
      en: "Teleports to coordinates (in the player's current world; commas between numbers are allowed), to the first player of the second target (into their world, with their rotation), or to a location: `player's location`, `location of targeted block`, `{home}`. `to spawn` is the respawn point, `to world spawn` is the shared overworld spawn. An empty second target does nothing."
    },
    example: `# По координатам (в текущем мире)
teleport player to 0, 100, 0

# По локации с миром и поворотом камеры (yaw, pitch)
teleport player to location(8.5, 64, -12.5, world "lobby", 180, 0)

# К другому игроку, на спавн или в сохранённую переменную
teleport other players in radius 50 of player to player
teleport player to {home::%player%}
tp player to world spawn`,
    since: "0.1.0", related: ["target-radius"]
  },
  {
    id: "eff-open-screen", category: "effects",
    name: { ru: "Открыть экран", en: "Open screen" },
    syntax: [
      "open screen \"<заголовок>\":",
      "open screen \"<заголовок>\" to <цель>:"
    ],
    desc: {
      ru: "Открывает игрокам кастомный экран (GUI) с перечисленными ниже элементами: `text`, `button`, `slot`, `image`, `bar`, `hotbar`, `list`, `input`, `slider`, `toggle`. У каждого элемента поддерживаются `at X Y`, `anchor`, `size WxH`, `layer N`, `id <имя>`, `hidden` и `tooltip \"...\"`. См. категории «Экраны (GUI)» и «UI / HUD».",
      en: "Opens a custom GUI screen with indented elements: `text`, `button`, `slot`, `image`, `bar`, `hotbar`, `list`, `input`, `slider`, `toggle`. All elements support `at X Y`, `anchor`, `size WxH`, `layer N`, `id <name>`, `hidden`, and `tooltip \"...\"`. See “Screens (GUI)” and “UI / HUD”."
    },
    example: `command /menu:
	open screen "Главное меню" to player:
		text "&6Привет, %player%!" at 0, -55 color #FFD700 shadow
		slot diamond 1 at 0, -28 tooltip "&bВаш баланс: %{coins::%player%}%"
		button "&aСпавн" at 0, 4 size 120x20 run "/spawn"
		button "&cЗакрыть" at 0, 30 size 120x20 close`,
    since: "0.1.0", related: ["screen-text","screen-button","screen-slot","screen-image","hud-bar","hud-layer"]
  },
  {
    id: "eff-heal", category: "effects",
    name: { ru: "Вылечить", en: "Heal" },
    syntax: [
      "heal <цель>",
      "heal <цель> by <число>"
    ],
    desc: {
      ru: "Полностью восстанавливает здоровье или добавляет указанное количество (4 = два сердца).",
      en: "Fully restores health or adds the given amount (4 = two hearts)."
    },
    example: `# Полное лечение
heal player

# Лечение на заданное число единиц (4 = 2 сердца)
heal player by 6
heal players in radius 10 of player by {heal_power}`,
    since: "0.1.0", related: ["eff-set-health","eff-damage","eff-feed"]
  },
  {
    id: "eff-damage", category: "effects",
    name: { ru: "Урон", en: "Damage" },
    syntax: ["damage <цель> by <число>"],
    desc: {
      ru: "Обычный урон: учитывает броню и режим игры (в креативе не действует).",
      en: "Generic damage: armour and game mode apply (no effect in creative)."
    },
    example: `# Нанести 4 единицы урона (2 сердца, с учётом брони)
damage player by 4
damage other players in radius 5 of player by 6`,
    since: "0.1.2", related: ["eff-heal","eff-kill"]
  },
  {
    id: "eff-set-health", category: "effects",
    name: { ru: "Задать здоровье", en: "Set health" },
    syntax: [
      "set health of <цель> to <число>",
      "set player's health to <число>"
    ],
    desc: {
      ru: "Ставит здоровье (от 0 до максимума игрока).",
      en: "Sets health (0 to the player's maximum)."
    },
    example: `# Установить точное здоровье (20 = 10 полных сердец)
set health of player to 20
set player's health to 6`,
    since: "0.1.2", related: ["eff-heal"]
  },
  {
    id: "eff-feed", category: "effects",
    name: { ru: "Накормить", en: "Feed" },
    syntax: [
      "feed <цель>",
      "feed <цель> by <число>"
    ],
    desc: {
      ru: "Без числа — голод и насыщение на максимум (20); с числом — прибавляет голод.",
      en: "Without a number — food and saturation to max (20); with a number — adds food."
    },
    example: `# Полностью восстановить голод и насыщение
feed player

# Прибавить 6 единиц голода (3 ножки)
feed all players by 6`,
    since: "0.1.2", related: ["eff-set-food","eff-set-saturation"]
  },
  {
    id: "eff-set-food", category: "effects",
    name: { ru: "Голод", en: "Food level" },
    syntax: [
      "set food level of <цель> to <число>",
      "set hunger of <цель> to <число>",
      "set player's food level to <число>",
      "set player's hunger to <число>"
    ],
    desc: {
      ru: "Ставит голод, 0–20. `hunger` — то же, что `food level`.",
      en: "Sets food level, 0–20. `hunger` is the same as `food level`."
    },
    example: `# Задать уровень голода от 0 до 20
set food level of player to 20
set player's hunger to 6`,
    since: "0.1.2", related: ["eff-set-saturation","eff-feed"]
  },
  {
    id: "eff-set-saturation", category: "effects",
    name: { ru: "Насыщение", en: "Saturation" },
    syntax: [
      "set saturation of <цель> to <число>",
      "set player's saturation to <число>"
    ],
    desc: {
      ru: "Ставит насыщение. Оно не бывает больше текущего голода — так устроена ванилла.",
      en: "Sets saturation. It cannot exceed the current food level: that is how vanilla works."
    },
    example: `# Задать насыщение (не может превышать текущий голод)
set food level of player to 20
set saturation of player to 10`,
    since: "0.1.2", related: ["eff-set-food","eff-feed"]
  },
  {
    id: "eff-kill", category: "effects",
    name: { ru: "Убить", en: "Kill" },
    syntax: ["kill <цель>"],
    desc: {
      ru: "Убивает игроков цели.",
      en: "Kills the target players."
    },
    example: `# Мгновенно убить цель
kill targeted player
kill players in radius 5 of player`,
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
    example: `# Поджечь игрока на 5 секунд (допустимо 1–3600)
ignite player for 5 seconds
ignite targeted player for 10 seconds`,
    since: "0.1.2", related: ["eff-extinguish","eff-damage"]
  },
  {
    id: "eff-extinguish", category: "effects",
    name: { ru: "Потушить", en: "Extinguish" },
    syntax: ["extinguish <цель>"],
    desc: {
      ru: "Тушит игроков цели.",
      en: "Puts out the fire on the target players."
    },
    example: `# Потушить огонь на игроке
extinguish player
extinguish all players`,
    since: "0.1.2", related: ["eff-ignite","eff-damage"]
  },
  {
    id: "eff-clear-inventory", category: "effects",
    name: { ru: "Очистить инвентарь", en: "Clear inventory" },
    syntax: [
      "clear inventory of <цель>",
      "clear player's inventory"
    ],
    desc: {
      ru: "Удаляет все предметы из инвентаря.",
      en: "Removes every item from the inventory."
    },
    example: `# Полностью очистить инвентарь перед мини-игрой
clear inventory of player
clear player's inventory`,
    since: "0.1.2", related: ["eff-give-item"]
  },
  {
    id: "eff-gamemode", category: "effects",
    name: { ru: "Режим игры", en: "Game mode" },
    syntax: [
      "set gamemode of <цель> to <режим>",
      "set game mode of <цель> to <режим>",
      "set player's gamemode to <режим>"
    ],
    desc: {
      ru: "Меняет игровой режим цели. Все допустимые варианты и сокращения:\n• `survival` / `s` / `0` — выживание\n• `creative` / `c` / `1` — творческий\n• `adventure` / `a` / `2` — приключение\n• `spectator` / `sp` / `3` — наблюдатель",
      en: "Changes the target's game mode. All valid values & shortcuts:\n• `survival` / `s` / `0`\n• `creative` / `c` / `1`\n• `adventure` / `a` / `2`\n• `spectator` / `sp` / `3`"
    },
    example: `# Смена режима полным именем или сокращением
set player's gamemode to creative
set gamemode of all players to survival
set game mode of arg-1 to spectator`,
    since: "0.1.2", related: ["eff-flying"]
  },
  {
    id: "eff-flying", category: "effects",
    name: { ru: "Полёт", en: "Flying" },
    syntax: [
      "set flying of <цель> to true",
      "set flying of <цель> to false",
      "set flight of <цель> to true",
      "set player's flying to false"
    ],
    desc: {
      ru: "Включает или выключает полёт в выживании/приключении. Допустимые значения: `true` (`yes`, `on`, `1`) — выдаёт право полёта и поднимает в воздух; `false` (`no`, `off`, `0`) — забирает полёт. В креативе и наблюдателе право полёта не снимается.",
      en: "Enables or disables flight in survival/adventure. Values: `true` (`yes`, `on`, `1`) grants flight and takes off; `false` (`no`, `off`, `0`) revokes flight. Creative and spectator keep their ability."
    },
    example: `# Переключатель /fly
command /fly:
	permission: server.fly
	if player is flying:
		set flying of player to false
		send "&7Полёт выключен" to player
	else:
		set flying of player to true
		send "&aПолёт включён!" to player`,
    since: "0.1.2", related: ["eff-gamemode"]
  },
  {
    id: "eff-give-effect", category: "effects",
    name: { ru: "Эффект зелья", en: "Potion effect" },
    syntax: [
      "give <цель> effect <id> for <число> seconds",
      "give <цель> effect <id> for <число> seconds level <число>"
    ],
    desc: {
      ru: "Накладывает эффект зелья. `level 1` — I (по умолчанию), `level 2` — II и т.д. Единицы: `seconds`, `second`, `secs`, `sec`. Популярные ванильные id: `speed`, `slowness`, `haste`, `mining_fatigue`, `strength`, `instant_health`, `instant_damage`, `jump_boost`, `nausea`, `regeneration`, `resistance`, `fire_resistance`, `water_breathing`, `invisibility`, `blindness`, `night_vision`, `hunger`, `weakness`, `poison`, `wither`, `health_boost`, `absorption`, `saturation`, `glowing`, `levitation`, `luck`, `slow_falling`, `conduit_power`, `dolphins_grace`, `darkness`, а также модовые `othermod:frost`.",
      en: "Applies a potion effect. `level 1` is I (default), `level 2` is II, etc. Time aliases: `seconds`, `second`, `secs`, `sec`. Common vanilla ids: `speed`, `slowness`, `haste`, `mining_fatigue`, `strength`, `instant_health`, `instant_damage`, `jump_boost`, `nausea`, `regeneration`, `resistance`, `fire_resistance`, `water_breathing`, `invisibility`, `blindness`, `night_vision`, `hunger`, `weakness`, `poison`, `wither`, `health_boost`, `absorption`, `saturation`, `glowing`, `levitation`, `luck`, `slow_falling`, `conduit_power`, `dolphins_grace`, `darkness`, plus modded `othermod:frost`."
    },
    example: `# Баффы одному игроку или группе
give player effect speed for 60 seconds level 2
give player effect night_vision for 300 seconds
give @a[distance=..10] effect regeneration for 10 seconds level 2`,
    since: "0.1.2", related: ["eff-remove-effect","eff-clear-effects"]
  },
  {
    id: "eff-remove-effect", category: "effects",
    name: { ru: "Снять эффект", en: "Remove effect" },
    syntax: ["remove effect <id> from <цель>"],
    desc: {
      ru: "Снимает один эффект зелья.",
      en: "Removes one potion effect."
    },
    example: `# Снять конкретный эффект зелья
remove effect poison from player
remove effect invisibility from all players`,
    since: "0.1.2", related: ["eff-clear-effects","eff-give-effect"]
  },
  {
    id: "eff-clear-effects", category: "effects",
    name: { ru: "Снять все эффекты", en: "Clear effects" },
    syntax: [
      "clear effects of <цель>",
      "clear all effects of <цель>"
    ],
    desc: {
      ru: "Снимает все эффекты зелий. `clear all effects` — то же самое.",
      en: "Removes every potion effect. `clear all effects` is the same."
    },
    example: `# Очистить все активные эффекты зелий
clear effects of player
clear all effects of all players`,
    since: "0.1.2", related: ["eff-remove-effect","eff-give-effect"]
  },
  {
    id: "eff-xp", category: "effects",
    name: { ru: "Дать опыт", en: "Give experience" },
    syntax: [
      "give <число> xp to <цель>",
      "give <число> experience to <цель>"
    ],
    desc: {
      ru: "Добавляет очки опыта, не уровни. Число может быть отрицательным.",
      en: "Adds experience points, not levels. The number may be negative."
    },
    example: `# Добавить или отнять очки опыта (не уровни)
give 100 xp to player
give -25 experience to player`,
    since: "0.1.2", related: ["eff-give-levels","eff-set-xp"]
  },
  {
    id: "eff-give-levels", category: "effects",
    name: { ru: "Дать уровни", en: "Give levels" },
    syntax: [
      "give <число> levels to <цель>",
      "give <число> level to <цель>"
    ],
    desc: {
      ru: "Прибавляет уровни опыта. Число может быть отрицательным.",
      en: "Adds experience levels. The number may be negative."
    },
    example: `# Добавить или снять уровни опыта
give 3 levels to player
give -1 level to player`,
    since: "0.1.2", related: ["eff-xp","eff-set-xp"]
  },
  {
    id: "eff-set-xp", category: "effects",
    name: { ru: "Задать уровень", en: "Set experience level" },
    syntax: [
      "set xp level of <цель> to <число>",
      "set level of <цель> to <число>",
      "set player's xp level to <число>",
      "set player's level to <число>"
    ],
    desc: {
      ru: "Ставит уровень опыта. `level` — то же, что `xp level`.",
      en: "Sets the experience level. `level` is the same as `xp level`."
    },
    example: `# Установить точный уровень опыта
set xp level of player to 30
set player's level to 0`,
    since: "0.1.2", related: ["eff-xp","eff-give-levels"]
  },
  {
    id: "eff-console-command", category: "effects",
    name: { ru: "Команда от консоли", en: "Console command" },
    syntax: ["execute console command \"<команда>\""],
    desc: {
      ru: "Выполняет команду с правами **консоли** (`/` в начале не обязателен). Выключается `allowCommandEffects = false`. Не подставляйте сюда `%args%` в командах для обычных игроков. Из эффект-команд в чате — только операторам.",
      en: "Runs a command with **console** permissions (leading `/` optional). Disabled by `allowCommandEffects = false`. Never put `%args%` here in commands available to regular players. From chat effect commands — operators only."
    },
    example: `# Выполнение команды от имени консоли сервера
command /day:
	permission: 2
	execute console command "time set day"
	execute console command "weather clear"`,
    since: "0.1.2", related: ["eff-player-command","cfg-allowCommandEffects"]
  },
  {
    id: "eff-player-command", category: "effects",
    name: { ru: "Команда от игрока", en: "Make player run command" },
    syntax: ["make <цель> execute command \"<команда>\""],
    desc: {
      ru: "Каждый игрок цели выполняет команду со своими правами. Выключается `allowCommandEffects = false`.",
      en: "Each target player runs the command with their own permissions. Disabled by `allowCommandEffects = false`."
    },
    example: `# Выполнить команду от имени игрока (с его правами)
make player execute command "spawn"
make all players execute command "me готов к игре!"`,
    since: "0.1.2", related: ["eff-console-command"]
  },
  {
    id: "eff-kick", category: "effects",
    name: { ru: "Кикнуть", en: "Kick" },
    syntax: [
      "kick <цель>",
      "kick <цель> due to \"<причина>\"",
      "kick <цель> because \"<причина>\""
    ],
    desc: {
      ru: "Отключает игроков цели. Без причины сообщение — `Kicked`.",
      en: "Disconnects the target players. Without a reason the message is `Kicked`."
    },
    example: `# Кикнуть одного игрока или всех с причиной
command /kickall:
	permission: 2
	kick all players due to "Перезапуск сервера"

kick targeted player because "Нарушение правил"`,
    since: "0.2.0", related: ["eff-ban","eff-op","eff-deop","target-all"]
  },
  {
    id: "eff-ban", category: "effects",
    name: { ru: "Забанить", en: "Ban" },
    syntax: [
      "ban <цель>",
      "ban <цель> due to \"<причина>\"",
      "ban <цель> because \"<причина>\"",
      "ban <цель> for \"<причина>\""
    ],
    desc: {
      ru: "Пишет игрока в бан-лист сервера и отключает, если он онлайн. Без причины сообщение — `Banned`. Имя оффлайн банится, только если этот профиль уже заходил на сервер. `kick` по-прежнему только отключает и не пишет в бан-лист.",
      en: "Adds the player to the server ban list and disconnects them if they are online. Without a reason the message is `Banned`. An offline name is banned only when that profile has already joined this server. `kick` still only disconnects and does not write the ban list."
    },
    example: `# Бан онлайн-игрока и оффлайн-ника
command /banplayer <player> [text]:
	permission: 2
	if arg-2 is set:
		ban arg-1 due to arg-2
	else:
		ban arg-1 due to "Нарушение правил"

command /banoffline <text>:
	permission: 2
	ban arg-1 for "Реклама"`,
    since: "0.4.4", related: ["eff-kick","cond-banned","target-named"]
  },
  {
    id: "eff-lp-permission", category: "effects",
    name: { ru: "Право LuckPerms", en: "LuckPerms permission" },
    syntax: [
      "set permission <узел> of <цель> to true|false [for <число> seconds|minutes|hours|days]",
      "add permission <узел> to <цель> [for <число> seconds|minutes|hours|days]",
      "give permission <узел> to <цель>",
      "remove permission <узел> from <цель>",
      "clear permissions of <цель>",
      "add permission <узел> to group <имя> [for <срок>]",
      "remove permission <узел> from group <имя>",
      "set permission <узел> of group <имя> to true|false [for <срок>]"
    ],
    desc: {
      ru: "Управляет правами LuckPerms для игрока (онлайн или оффлайн) или для группы (`group \"vip\"`). В конце можно указать срок действия: `for <число> seconds|minutes|hours|days`. `set ... to false` записывает явный запрет. `clear permissions of <цель>` стирает обычные и regex-права, группы не трогает.",
      en: "Manages LuckPerms permissions for a player (online or offline) or a group (`group \"vip\"`). Optional expiry: `for <number> seconds|minutes|hours|days`. `set ... to false` stores an explicit deny. `clear permissions` clears regular/regex nodes without touching groups."
    },
    example: `# Права для игрока (постоянные и временные)
add permission "scriptcraft.shop" to player
set permission "essentials.fly" of player to false
add permission "temp.fly" to player for 2 hours
remove permission "scriptcraft.shop" from "Steve"

# Права для группы LuckPerms
add permission "my.feature" to group "default"
remove permission "my.feature" from group "vip"
set permission "server.beta" of group "tester" to true for 7 days`,
    since: "0.4.5", related: ["eff-lp-group","cond-permission","cond-group"]
  },
  {
    id: "eff-lp-group", category: "effects",
    name: { ru: "Группа LuckPerms", en: "LuckPerms group" },
    syntax: [
      "add group <имя> to <цель>",
      "give group <имя> to <цель>",
      "remove group <имя> from <цель>",
      "set group of <цель> to <имя>",
      "set primary group of <цель> to <имя>",
      "clear groups of <цель>"
    ],
    desc: {
      ru: "Добавляет или снимает наследование группы. `set group` и `set primary group` делают группу основной и добавляют её в наследование. `clear groups` снимает наследование и ставит основную группу `default`. Имя приводится к нижнему регистру. Срок — `for <число> days` (и seconds, minutes, hours). Оффлайн-имя пишется, если профиль уже есть на сервере.",
      en: "Adds or removes a group inheritance. `set group` and `set primary group` make that group primary and add the inheritance. `clear groups` removes inheritance and sets the primary group to `default`. The name is lowercased. A duration is `for <number> days` (also seconds, minutes, hours). An offline name is written when the server already has that profile."
    },
    example: `# Выдать временную группу, сменить основную или очистить
add group "vip" to player for 7 days
set primary group of player to "member"
remove group "vip" from "Steve"
clear groups of player`,
    since: "0.4.5", related: ["eff-lp-permission","cond-group","expr-lp"]
  },
  {
    id: "eff-lp-prefix", category: "effects",
    name: { ru: "Префикс LuckPerms", en: "LuckPerms prefix" },
    syntax: [
      "set prefix of <цель> to <текст>",
      "add prefix <текст> to <цель>",
      "remove prefix of <цель>",
      "clear prefixes of <цель>"
    ],
    desc: {
      ru: "Префикс в чате LuckPerms. Цвета `&` работают. Приоритет по умолчанию 100: `with priority 200`. `set` без приоритета заменяет все префиксы, с приоритетом — только этот вес. `add` не стирает остальные. `remove` без приоритета стирает все, с приоритетом — один вес. Срок и приоритет можно писать в любом порядке: `for 1 hour with priority 10`.",
      en: "A LuckPerms chat prefix. `&` colours work. The default priority is 100: `with priority 200`. `set` without a priority replaces every prefix; with a priority it replaces only that weight. `add` leaves the others. `remove` without a priority clears all, with a priority only that weight. Duration and priority can be written in either order: `for 1 hour with priority 10`."
    },
    example: `# Префиксы с цветами, приоритетом и сроком
set prefix of player to "&6[VIP] "
add prefix "&c[Админ] " to player with priority 200
add prefix "&b[Ивент] " to player for 1 hour with priority 150
remove prefix of player with priority 200
clear prefixes of player`,
    since: "0.4.5", related: ["eff-lp-suffix","expr-lp","expr-colors"]
  },
  {
    id: "eff-lp-suffix", category: "effects",
    name: { ru: "Суффикс LuckPerms", en: "LuckPerms suffix" },
    syntax: [
      "set suffix of <цель> to <текст>",
      "add suffix <текст> to <цель>",
      "remove suffix of <цель>",
      "clear suffixes of <цель>"
    ],
    desc: {
      ru: "Суффикс LuckPerms. Те же правила, что у префикса: цвета `&`, `with priority`, `for <срок>`, `set` заменяет, `add` добавляет, `clear suffixes` стирает все.",
      en: "A LuckPerms suffix. Same rules as a prefix: `&` colours, `with priority`, `for <duration>`, `set` replaces, `add` adds, `clear suffixes` removes all."
    },
    example: `# Суффиксы с приоритетом и сроком
set suffix of player to " &7новичок"
add suffix " &e★" to player with priority 50 for 1 day
clear suffixes of player`,
    since: "0.4.5", related: ["eff-lp-prefix","expr-lp"]
  },
  {
    id: "eff-lp-meta", category: "effects",
    name: { ru: "Meta LuckPerms", en: "LuckPerms meta" },
    syntax: [
      "set meta <ключ> of <цель> to <значение>",
      "remove meta <ключ> of <цель>",
      "clear meta of <цель>"
    ],
    desc: {
      ru: "Произвольная meta-запись LuckPerms. Ключ и значение — выражения. `clear meta` стирает все meta-ключи игрока и не трогает префикс, суффикс и группы.",
      en: "An arbitrary LuckPerms meta entry. The key and the value are expressions. `clear meta` removes every meta key of the player and leaves the prefix, suffix and groups."
    },
    example: `# Запись, чтение и удаление meta-ключей LuckPerms
set meta "chat_color" of player to "&b"
set {-col} to meta "chat_color" of player
send "%{-col}%Цветной текст!" to player
remove meta "chat_color" of player`,
    since: "0.4.5", related: ["eff-lp-prefix","expr-lp"]
  },
  {
    id: "eff-op", category: "effects",
    name: { ru: "Сделать оператором", en: "Op" },
    syntax: ["op <цель>"],
    desc: {
      ru: "Добавляет игрока в список операторов сервера. Права применяются сразу.",
      en: "Adds the player to the server operator list. Permissions apply immediately."
    },
    example: `# Выдать права оператора
command /grantop <player>:
	permission: 4
	op arg-1
	send "&aИгроку %arg-1% выданы права оператора" to player`,
    since: "0.2.0", related: ["eff-deop","cond-player","eff-kick"]
  },
  {
    id: "eff-deop", category: "effects",
    name: { ru: "Снять оператора", en: "Deop" },
    syntax: ["deop <цель>"],
    desc: {
      ru: "Убирает игрока из списка операторов сервера. Права применяются сразу.",
      en: "Removes the player from the server operator list. Permissions apply immediately."
    },
    example: `# Снять права оператора
command /revokeop <player>:
	permission: 4
	deop arg-1
	send "&eС игрока %arg-1% сняты права оператора" to player`,
    since: "0.2.0", related: ["eff-op","cond-player","eff-kick"]
  },
  {
    id: "eff-say", category: "effects",
    name: { ru: "Сказать в чат", en: "Say" },
    syntax: [
      "make <цель> say \"<текст>\"",
      "make <цель> chat \"<текст>\""
    ],
    desc: {
      ru: "Показывает строку в чате как сообщение этого игрока. `chat` — то же, что `say`.",
      en: "Shows the line in chat as that player's message. `chat` is the same as `say`."
    },
    example: `# Отправить сообщение в чат от лица игрока
make player say "Всем привет!"
make targeted player chat "Я сдаюсь!"`,
    since: "0.2.0", related: ["eff-log","eff-broadcast","eff-send"]
  },
  {
    id: "eff-log", category: "effects",
    name: { ru: "Записать в лог", en: "Log" },
    syntax: ["log \"<текст>\""],
    desc: {
      ru: "Пишет строку в лог сервера (`[Script]`). Игроки её не видят.",
      en: "Writes the line to the server log (`[Script]`). Players do not see it."
    },
    example: `# Запись событий в консоль/лог сервера (игроки не видят)
log "[Shop] %player% купил алмаз за 50 монет"
log "[Admin] %player% выполнил /reset"`,
    since: "0.2.0", related: ["eff-say","eff-broadcast"]
  },
  {
    id: "eff-stop", category: "effects",
    name: { ru: "stop", en: "stop" },
    syntax: ["stop"],
    desc: {
      ru: "Прерывает остальные действия этого запуска (в том числе код после `if` и следующие проходы `loop`).",
      en: "Stops the rest of this run (including code after the `if` and further `loop` passes)."
    },
    example: `# Прервать выполнение команды/функции при ошибке
command /vip:
	if {rank::%player%} is not "vip":
		send "&cТолько для VIP!" to player
		stop
	give player diamond 1
	send "&aНаграда выдана!" to player`,
    since: "0.2.0", related: ["eff-cancel","struct-if"]
  },
  {
    id: "eff-cancel", category: "effects",
    name: { ru: "Отменить событие", en: "Cancel event" },
    syntax: [
      "cancel event",
      "cancel the event"
    ],
    desc: {
      ru: "Отменяет текущее событие, если оно это позволяет: чат, ломание и установка блока, клик, выбрасывание, подбор, урон, смерть, смена режима. В команде и в `every` флаг ни на что не влияет.",
      en: "Cancels the current event when the event allows it: chat, break, place, click, drop, pickup, damage, death, game mode change. Inside a command or `every` the flag does nothing."
    },
    example: `# Отмена чата, урона или ломания блока
on chat:
	if message contains "http://":
		cancel event
		send "&cСсылки запрещены!" to player

on break:
	if %block% is "minecraft:spawner":
		cancel event`,
    since: "0.2.0", related: ["event-chat","event-break","event-right-click","event-drop","event-death","event-gamemode","eff-stop"]
  },
  {
    id: "eff-time", category: "effects",
    name: { ru: "Время", en: "Time" },
    syntax: [
      "set time to day",
      "set time to noon",
      "set time to night",
      "set time to midnight",
      "set time to <тики>"
    ],
    desc: {
      ru: "Устанавливает время суток во всех мирах. Именованные значения: `day` (`1000` тиков — утро), `noon` (`6000` — полдень), `night` (`13000` — ночь), `midnight` (`18000` — полночь). Также можно указать любое число тиков от `0` до `24000` (например `12000` — закат).",
      en: "Sets time of day in all worlds. Named presets: `day` (`1000` ticks), `noon` (`6000`), `night` (`13000`), `midnight` (`18000`). Or any tick number `0`–`24000` (e.g. `12000` for sunset)."
    },
    example: `# Именованные пресеты и точные тики
set time to day
set time to midnight
set time to 12000   # закат`,
    since: "0.2.0", related: ["eff-weather"]
  },
  {
    id: "eff-weather", category: "effects",
    name: { ru: "Погода", en: "Weather" },
    syntax: [
      "set weather to clear",
      "set weather to sun",
      "set weather to rain",
      "set weather to thunder",
      "set weather to storm",
      "set weather to rain for <секунды> seconds",
      "set weather to thunder for <секунды> seconds"
    ],
    desc: {
      ru: "Меняет погоду во всех мирах. Все допустимые варианты: `clear` / `sun` (ясно), `rain` (дождь), `thunder` / `storm` (гроза). С хвостом `for <N> seconds` задаётся длительность погоды.",
      en: "Changes weather in all worlds. Options: `clear` / `sun`, `rain`, `thunder` / `storm`. Optional `for <N> seconds` sets duration."
    },
    example: `# Ясная погода или гроза на заданное время
set weather to clear
set weather to rain for 120 seconds
set weather to storm for 60 seconds`,
    since: "0.2.0", related: ["eff-time"]
  },
  {
    id: "eff-lightning", category: "effects",
    name: { ru: "Молния", en: "Lightning" },
    syntax: [
      "strike lightning at <цель>",
      "strike lightning at <x> <y> <z>",
      "strike lightning at <локация>"
    ],
    desc: {
      ru: "Бьёт молнией по игрокам цели, по координатам или по локации (`location of targeted block`, `{дом}`). Координаты — в мире игрока из контекста, иначе в обычном мире.",
      en: "Strikes the target players, coordinates, or a location (`location of targeted block`, `{home}`). Coordinates use the context player's world, otherwise the overworld."
    },
    example: `# Настоящая молния (наносит урон и поджигает)
strike lightning at targeted player
strike lightning at location of targeted block
strike lightning at 0 64 0`,
    since: "0.2.0", related: ["eff-explode","eff-set-block","target-targeted","expr-location"]
  },
  {
    id: "eff-set-block", category: "effects",
    name: { ru: "Поставить блок", en: "Set block" },
    syntax: [
      "set block at <x> <y> <z> to <блок>",
      "set block at <локация> to <блок>",
      "set block at targeted block to <блок>"
    ],
    desc: {
      ru: "Ставит блок в состоянии по умолчанию. Координаты без локации — мир игрока из контекста, иначе обычный мир. `<блок>` — id (`stone`, `minecraft:glass`, `{блок}`) или параметр функции типа block. `air` убирает блок. `targeted block` — блок, на который смотрит игрок.",
      en: "Places the block's default state. Coordinates without a location use the context player's world, otherwise the overworld. `<block>` is an id (`stone`, `minecraft:glass`, `{block}`) or a function parameter of type block. `air` removes the block. `targeted block` is the block the player is looking at."
    },
    example: `# Установка и удаление блоков (air)
set block at 10 64 10 to stone
set block at targeted block to gold_block
set block at ~~vector(0, -1, 0) to glass
set block at location of targeted block to air`,
    since: "0.4.4", related: ["expr-location","expr-targeted-block","struct-function"]
  },
  {
    id: "eff-explode", category: "effects",
    name: { ru: "Взрыв", en: "Explosion" },
    syntax: [
      "explode at <цель>",
      "explode at <x> <y> <z>",
      "explode at <локация>",
      "explode at <цель> with force <0-8>",
      "explode at <локация> with force <0-8>"
    ],
    desc: {
      ru: "Взрыв у игроков цели, по координатам или по локации. Сила 0–8, по умолчанию 3. Блоки ломаются. Координаты — в мире игрока из контекста, иначе в обычном мире. `power` — то же, что `force`.",
      en: "An explosion at the target players, at coordinates, or at a location. Force is 0–8, default 3. Blocks break. Coordinates use the context player's world, otherwise the overworld. `power` is the same as `force`."
    },
    example: `# Взрыв с разрушением блоков (сила 0–8, по умолчанию 3)
explode at player with force 2
explode at location of targeted block with power 4`,
    since: "0.2.0", related: ["eff-lightning","expr-location"]
  },
  {
    id: "eff-velocity", category: "effects",
    name: { ru: "Скорость (вектор движения)", en: "Velocity" },
    syntax: [
      "set velocity of <цель> to <x> <y> <z>",
      "set velocity of <цель> to vector(<x>, <y>, <z>)",
      "set player's velocity to vector(<x>, <y>, <z>)"
    ],
    desc: {
      ru: "Задаёт скорость игрока по трём осям — тремя числами или через `vector(x, y, z)`.",
      en: "Sets the player's velocity on the three axes — either as three numbers or via `vector(x, y, z)`."
    },
    example: `# Подбросить игрока вертикально вверх или задать вектор
set velocity of player to 0 1.2 0
set player's velocity to vector(0.5, 0.9, 0.0)`,
    since: "0.2.0", related: ["eff-launch","eff-teleport","expr-location"]
  },
  {
    id: "eff-launch", category: "effects",
    name: { ru: "Толчок", en: "Launch" },
    syntax: [
      "launch <цель> upwards",
      "launch <цель> forwards",
      "launch <цель> upwards by <скорость>",
      "launch <цель> forwards by <скорость>"
    ],
    desc: {
      ru: "Подбрасывает цель вверх (`upwards`) или толкает вперёд по направлению взгляда (`forwards`). По умолчанию вверх ~`1.2`, вперёд ~`1.5`, максимум `−8…8`. Вместо `by` можно писать `with speed`.",
      en: "Launches targets `upwards` or `forwards` along their look direction. Defaults: up ~`1.2`, forward ~`1.5`, clamped to `−8…8`. `with speed` is an alias for `by`."
    },
    example: `# Прыжок вверх и рывок вперёд
command /jump:
	launch player upwards by 1.3

command /dash:
	launch player forwards with speed 2.0`,
    since: "0.2.0", related: ["eff-velocity","eff-teleport"]
  },
  {
    id: "eff-spawn", category: "effects",
    name: { ru: "Точка возрождения", en: "Spawn point" },
    syntax: [
      "set spawn of <цель>",
      "set spawn of <цель> to <x> <y> <z>",
      "set spawn of <цель> to <локация>"
    ],
    desc: {
      ru: "Без координат ставит точку возрождения там, где стоит игрок (в его мире). С координатами — в его текущем мире. С локацией — в мире и точке этой локации. Телепорт туда — `teleport ... to spawn`.",
      en: "Without coordinates, sets the respawn point where the player stands (in their world). With coordinates — in their current world. With a location — in that location's world and point. Teleporting there is `teleport ... to spawn`."
    },
    example: `# Сохранить точку возрождения и вернуться на неё
command /sethome:
	set spawn of player
	send "&aТочка дома сохранена!" to player

command /home:
	teleport player to spawn`,
    since: "0.2.0", related: ["eff-teleport","event-death","event-respawn"]
  },
  {
    id: "eff-drop", category: "effects",
    name: { ru: "Бросить предмет", en: "Drop item" },
    syntax: [
      "drop <предмет> at <цель>",
      "drop <количество> <предмет> at <цель>",
      "drop <предмет> at <локация>"
    ],
    desc: {
      ru: "Роняет предмет у ног игроков цели или в точке локации (не в инвентарь). Предмет может быть `player's tool`, `{слот}` или id с nbt, как у `give`.",
      en: "Drops the item at the target players' feet or at a location (not into the inventory). The item may be `player's tool`, `{slot}` or an id with nbt, the same as `give`."
    },
    example: `# Бросить предмет на землю (у игрока или на локации)
drop 4 diamond at player
drop 1 diamond_sword{Unbreakable:1b} at location of targeted block
drop player's tool at player`,
    since: "0.2.0", related: ["eff-give-item","event-drop","event-pickup"]
  },
  {
    id: "eff-walk-speed", category: "effects",
    name: { ru: "Скорость ходьбы", en: "Walk speed" },
    syntax: [
      "set walk speed of <цель> to <число>",
      "set player's walk speed to <число>"
    ],
    desc: {
      ru: "Скорость ходьбы. У ванили это `0.1`, диапазон 0–1.",
      en: "Walk speed. Vanilla uses `0.1`. The range is 0–1."
    },
    example: `# Ускорить игрока (ваниль = 0.1, диапазон 0..1)
set walk speed of player to 0.25
# Вернуть стандартную скорость
set player's walk speed to 0.1`,
    since: "0.2.0", related: ["eff-fly-speed","expr-property"]
  },
  {
    id: "eff-fly-speed", category: "effects",
    name: { ru: "Скорость полёта", en: "Fly speed" },
    syntax: [
      "set fly speed of <цель> to <число>",
      "set flying speed of <цель> to <число>",
      "set player's fly speed to <число>"
    ],
    desc: {
      ru: "Скорость полёта. У ванили это `0.05`, диапазон 0–1. `flying speed` — то же, что `fly speed`.",
      en: "Fly speed. Vanilla uses `0.05`. The range is 0–1. `flying speed` is the same as `fly speed`."
    },
    example: `# Ускорить полёт (ваниль = 0.05, диапазон 0..1)
set fly speed of player to 0.15
# Вернуть стандартную скорость полёта
set player's fly speed to 0.05`,
    since: "0.2.0", related: ["eff-walk-speed","eff-flying","expr-property"]
  },
  {
    id: "eff-air", category: "effects",
    name: { ru: "Воздух", en: "Air" },
    syntax: [
      "set air of <цель> to <число>",
      "set oxygen of <цель> to <число>",
      "set player's air to <число>"
    ],
    desc: {
      ru: "Запас воздуха. Обрезается по максимуму игрока. `oxygen` — то же, что `air`.",
      en: "Air supply. Clamped to the player's maximum. `oxygen` is the same as `air`."
    },
    example: `# Восстановить запас воздуха под водой (ванильный максимум = 300)
set air of player to 300
set oxygen of all players to 300`,
    since: "0.2.0", related: ["expr-property"]
  },
  {
    id: "eff-absorption", category: "effects",
    name: { ru: "Абсорбция", en: "Absorption" },
    syntax: [
      "set absorption of <цель> to <число>",
      "set player's absorption to <число>"
    ],
    desc: {
      ru: "Ставит жёлтые сердца абсорбции.",
      en: "Sets the yellow absorption hearts."
    },
    example: `# Выдать 4 жёлтых сердца (8 единиц абсорбции)
set absorption of player to 8
# Снять абсорбцию
set player's absorption to 0`,
    since: "0.2.0", related: ["eff-set-health","expr-property"]
  },
  {
    id: "eff-close-inventory", category: "effects",
    name: { ru: "Закрыть инвентарь", en: "Close inventory" },
    syntax: ["close inventory of <цель>"],
    desc: {
      ru: "Закрывает открытый сундук или верстак.",
      en: "Closes an open chest or crafting table."
    },
    example: `# Принудительно закрыть сундук/верстак у игрока
close inventory of player
close inventory of all players`,
    since: "0.2.0", related: ["eff-clear-inventory","event-open"]
  },
  {
    id: "eff-wake", category: "effects",
    name: { ru: "Разбудить", en: "Wake up" },
    syntax: [
      "wake up <цель>",
      "wake <цель> up"
    ],
    desc: {
      ru: "Будит игрока, если он спит.",
      en: "Wakes the player if they are sleeping."
    },
    example: `# Разбудить всех спящих игроков утром
wake up all players`,
    since: "0.2.0", related: ["cond-player"]
  },
  {
    id: "cond-compare", category: "conditions",
    name: { ru: "Равно / не равно (и списки значений через or / and)", en: "Equals / not equals (& multi-value or / and lists)" },
    syntax: [
      "<значение> is <значение> [or <значение2> ...]",
      "<значение> is not <значение> [or|and <значение2> ...]",
      "<значение> = <значение>",
      "<значение> == <значение>",
      "<значение> != <значение>"
    ],
    desc: {
      ru: "Числа сравниваются как числа, текст — без учёта регистра. Справа можно перечислить несколько значений через `or` / `and`: например `if arg-1 is \"on\" or \"enable\":` или `if arg-1 is not \"on\" or \"off\":` (при отрицании `is not A or B` означает «ни A, ни B»).",
      en: "Numbers compare as numbers, text case-insensitively. You can list multiple right-hand values with `or` / `and`: e.g. `if arg-1 is \"on\" or \"enable\":` or `if arg-1 is not \"on\" or \"off\":` (for negated checks, `is not A or B` means neither A nor B)."
    },
    example: `# Сравнение с одним или несколькими значениями через or / and
if {rank::%player%} is "vip" or "premium" or "admin":
	send "&6VIP-доступ открыт!" to player

# Отрицание списка значений: «ни on, ни off»
if arg-1 is not "on" or "off":
	send "&cУкажите on или off!" to player

# Операторы =, ==, != для чисел и строк
if {level::%player%} == 10:
	send "&aДостигнут 10 уровень!" to player
if {team::%player%} != "red":
	send "&7Вы не в красной команде" to player`,
    since: "0.1.0", related: ["struct-if","cond-number","cond-text-match"]
  },
  {
    id: "cond-number", category: "conditions",
    name: { ru: "Сравнение чисел", en: "Number comparison" },
    syntax: [
      "<значение> > <значение>",
      "<значение> < <значение>",
      "<значение> >= <значение>",
      "<значение> <= <значение>"
    ],
    desc: {
      ru: "Если хоть одна сторона не число — условие ложно.",
      en: "If either side is not a number, the condition is false."
    },
    example: `# Проверка баланса перед покупкой
if {coins::%player%} >= 50:
	remove 50 from {coins::%player%}
	give player diamond 1
else:
	send "&cНедостаточно монет!" to player

# Проверка здоровья и высоты игрока
if health of player <= 6 and y of player < 0:
	send "&cОпасно! Низкое здоровье в глубине!" to player`,
    since: "0.1.0", related: ["cond-compare"]
  },
  {
    id: "cond-var-set", category: "conditions",
    name: { ru: "Переменная задана", en: "Variable is set" },
    syntax: [
      "{<имя>} is set",
      "{<имя>} is not set"
    ],
    desc: {
      ru: "Есть ли значение у переменной.",
      en: "Whether the variable has a value."
    },
    example: `# Значение по умолчанию при первом использовании
if {coins::%player%} is not set:
	set {coins::%player%} to 100

# Проверка, сохранён ли дом
if {home::%player%} is set:
	teleport player to {home::%player%}
else:
	send "&cДом ещё не установлен! Используйте /sethome" to player`,
    since: "0.1.0", related: ["eff-delete-var"]
  },
  {
    id: "cond-target-set", category: "conditions",
    name: { ru: "Есть ли игроки в цели", en: "Target is set" },
    syntax: [
      "<цель> is set",
      "<цель> is not set"
    ],
    desc: {
      ru: "Есть ли хоть один игрок в цели (для `targeted player`, `players in radius ...`, `all players`, `@a[...]`, `player`). Для имени игрока не работает — `\"Steve\" is set` сравнивается как текст.",
      en: "Whether the target has at least one player (for `targeted player`, `players in radius ...`, `all players`, `@a[...]`, `player`). Does not work for a player name — `\"Steve\" is set` is compared as text."
    },
    example: `# Проверка, смотрит ли игрок на другого игрока
if targeted player is set:
	send "&aВы смотрите на %targeted%" to player
else:
	send "&7Вы ни на кого не смотрите" to player

# Проверка, есть ли игроки рядом
if other players in radius 15 of player is set:
	send "&eРядом есть другие игроки!" to player`,
    since: "0.1.2", related: ["target-targeted","target-radius","ph-targeted"]
  },
  {
    id: "cond-and", category: "conditions",
    name: { ru: "and / or", en: "and / or" },
    syntax: [
      "<условие> and <условие>",
      "<условие> or <условие>"
    ],
    desc: {
      ru: "Связывает условия в одной строке. `and` проверяется раньше `or`. Работает и со сравнениями, и с проверками игрока.",
      en: "Joins conditions in one line. `and` is checked before `or`. Works with comparisons and with player checks."
    },
    example: `# Комбинация and (выше приоритет) и or
if player is sneaking and health of player < 10:
	send "&eТихий и раненый" to player

if player is op or player has permission "server.vip":
	send "&aДоступ разрешён!" to player

# Сложное условие в одной строке
if player is in world overworld and player is holding diamond_sword and {coins::%player%} >= 10:
	send "&aГотов к бою!" to player`,
    since: "0.2.0", related: ["struct-if","cond-player","cond-compare"]
  },
  {
    id: "cond-player", category: "conditions",
    name: { ru: "Состояние игрока", en: "Player state" },
    syntax: [
      "<цель> is sneaking",
      "<цель> is not sneaking",
      "<цель> is sprinting",
      "<цель> is flying",
      "<цель> is sleeping",
      "<цель> is burning",
      "<цель> is on fire",
      "<цель> is op",
      "<цель> is alive",
      "<цель> is blocking",
      "<цель> is gliding",
      "<цель> is swimming",
      "<цель> is online",
      "<цель> is not online",
      "<цель> is holding <предмет>",
      "<цель> has <предмет>",
      "<цель> does not have <предмет>",
      "<цель> has permission <0-4>",
      "<цель> has permission \"<узел>\""
    ],
    desc: {
      ru: "Проверяет состояние каждого игрока цели (если цель пуста — ложь и для `is`, и для `is not`). Все доступные проверки:\n• Движение/действие: `is [not] sneaking` (присел), `is [not] sprinting` (бежит), `is [not] flying` (летит), `is [not] sleeping` (спит), `is [not] blocking` (блокирует щитом), `is [not] gliding` (летит на элитрах), `is [not] swimming` (плывёт).\n• Статус: `is [not] burning` / `is [not] on fire` (горит), `is [not] alive` (жив), `is [not] op` (оператор), `is [not] online` (в сети).\n• Инвентарь: `is [not] holding <предмет>` (держит в основной руке), `has <предмет>` / `does not have <предмет>` (есть где угодно в инвентаре; поддерживается NBT, например `diamond{CustomModelData:1}`).\n• Права: `has permission <0-4>` (уровень OP) или `has permission \"<узел>\"` (LuckPerms / Forge).",
      en: "Checks state for every player in the target (empty target = false for both `is` and `is not`). All checks:\n• Movement/action: `is [not] sneaking`, `is [not] sprinting`, `is [not] flying`, `is [not] sleeping`, `is [not] blocking`, `is [not] gliding`, `is [not] swimming`.\n• Status: `is [not] burning` / `is [not] on fire`, `is [not] alive`, `is [not] op`, `is [not] online`.\n• Inventory: `is [not] holding <item>` (main hand), `has <item>` / `does not have <item>` (anywhere in inventory; supports NBT).\n• Permissions: `has permission <0-4>` or `has permission \"<node>\"`."
    },
    example: `# Проверка движения и элитр
if player is gliding:
	spawn particle cloud at player count 5

# Проверка предмета в руке и в инвентаре
if player is holding diamond_sword and player has golden_apple:
	send "&aЭкипировка готова!" to player
else if player does not have bread:
	give player bread 8

# Проверка горения
if player is on fire:
	extinguish player
	send "&bВы потушены!" to player`,
    since: "0.2.0", related: ["cond-banned","cond-gamemode","cond-world","cond-and","eff-give-item","cond-permission"]
  },
  {
    id: "cond-banned", category: "conditions",
    name: { ru: "Игрок забанен", en: "Player is banned" },
    syntax: [
      "<цель> is banned",
      "<цель> is not banned"
    ],
    desc: {
      ru: "Смотрит бан-лист сервера. Для имени, которого нет в сети (`\"Steve\" is banned`), проверяется профиль, если сервер его уже видел. Никого в цели нет и имени нет — условие ложно.",
      en: "Reads the server ban list. A name that is not online (`\"Steve\" is banned`) is checked when the server has already seen that profile. Nobody in the target and no name — the condition is false."
    },
    example: `# Проверка онлайн-игрока и оффлайн-ника
command /checkban <text>:
	if arg-1 is banned:
		send "&cИгрок %arg-1% находится в бане" to player
	else:
		send "&aИгрок %arg-1% не забанен" to player`,
    since: "0.4.4", related: ["eff-ban","eff-kick","cond-player"]
  },
  {
    id: "cond-group", category: "conditions",
    name: { ru: "Группа LuckPerms", en: "LuckPerms group" },
    syntax: [
      "<цель> is in group <имя>",
      "<цель> is not in group <имя>"
    ],
    desc: {
      ru: "Игрок в этой группе LuckPerms: основная группа или наследование. Имя — выражение. Никого в цели нет и имени нет — ложь и для `is`, и для `is not`. Имя оффлайн проверяется, если LuckPerms уже загрузил этот профиль. Без LuckPerms условие ложно.",
      en: "The player is in that LuckPerms group: the primary group or an inheritance. The name is an expression. Nobody in the target and no name — false for both `is` and `is not`. An offline name is checked when LuckPerms has already loaded that profile. Without LuckPerms the condition is false."
    },
    example: `# Проверка группы LuckPerms (основной или унаследованной)
if player is in group "vip":
	send "&6Вы в группе VIP!" to player
else if player is in group "admin":
	send "&cВы администратор!" to player

# Проверка оффлайн-игрока
if "Steve" is not in group "vip":
	add group "vip" to "Steve" for 7 days`,
    since: "0.4.5", related: ["eff-lp-group","cond-permission","cond-player"]
  },
  {
    id: "cond-permission", category: "conditions",
    name: { ru: "Есть право (у игрока или группы)", en: "Has permission (player or group)" },
    syntax: [
      "<цель> has permission <0-4>",
      "<цель> has permission \"<узел>\"",
      "<цель> does not have permission <0-4>",
      "<цель> does not have permission \"<узел>\"",
      "group \"<имя>\" has permission \"<узел>\"",
      "group \"<имя>\" does not have permission \"<узел>\""
    ],
    desc: {
      ru: "Число 0–4 — ванильный уровень (2 — оператор). Строка — узел прав, например `scriptcraft.shop`. Его проверяет LuckPerms (мод или плагин) как для игроков, так и для групп (`group \"vip\" has permission \"...\"`). Выдавать и забирать права тоже можно как игроку (`add permission \"...\" to player`), так и группе (`add permission \"...\" to group \"vip\"`, `remove permission \"...\" from group \"vip\"`).",
      en: "A number 0–4 is the vanilla level (2 is operator). A string is a permission node, e.g. `scriptcraft.shop`. Checked via LuckPerms (mod or plugin) for both players and groups (`group \"vip\" has permission \"...\"`). Granting/removing permissions also works on both players (`add permission \"...\" to player`) and groups (`add permission \"...\" to group \"vip\"`, `remove permission \"...\" from group \"vip\"`)."
    },
    example: `# Проверка права игрока (LuckPerms или уровень OP 0-4)
if player has permission "scriptcraft.shop":
	send "&aДоступ к магазину открыт" to player
if player does not have permission 2:
	send "&cТребуется уровень оператора 2!" to player

# Проверка и переключение права у группы LuckPerms
if group "default" has permission "my.feature":
	remove permission "my.feature" from group "default"
else:
	add permission "my.feature" to group "default"`,
    since: "0.4.1", related: ["struct-command","cond-player","cond-group"]
  },
  {
    id: "cond-gamemode", category: "conditions",
    name: { ru: "Режим игры", en: "Game mode" },
    syntax: [
      "<цель> is in creative",
      "<цель> is in survival",
      "<цель> is in adventure",
      "<цель> is in spectator",
      "<цель> is not in creative",
      "<цель> is in gamemode <режим>"
    ],
    desc: {
      ru: "Проверяет игровой режим цели. Доступные режимы: `survival`, `creative`, `adventure`, `spectator`. Можно писать со словом `mode` (`is in creative mode`) или через `is in gamemode <режим>`. Если цель пуста — условие ложно.",
      en: "Checks the target's game mode. Modes: `survival`, `creative`, `adventure`, `spectator`. Optional `mode` word (`is in creative mode`) or `is in gamemode <mode>`. Empty target = false."
    },
    example: `# Разное поведение по режиму игры
if player is in creative:
	send "&bВы в творческом режиме" to player
else if player is not in spectator:
	heal player`,
    since: "0.2.0", related: ["cond-world","eff-gamemode","event-gamemode","cond-player"]
  },
  {
    id: "cond-world", category: "conditions",
    name: { ru: "Мир игрока", en: "Player's world" },
    syntax: [
      "<цель> is in world <мир>",
      "<цель> is not in world <мир>"
    ],
    desc: {
      ru: "Проверяет измерение (мир), в котором находится игрок. Короткие имена: `overworld`, `nether` / `the_nether`, `end` / `the_end`, либо полный id любого ванильного или модового измерения (`minecraft:overworld`, `twilightforest:twilight_forest`).",
      en: "Checks which dimension the player is in. Short names: `overworld`, `nether` / `the_nether`, `end` / `the_end`, or any full vanilla/modded dimension id (`minecraft:overworld`, `twilightforest:twilight_forest`)."
    },
    example: `# Запрет команды в Незере и Энде
if player is in world the_nether or player is in world the_end:
	send "&cЭту команду нельзя использовать в этом измерении!" to player
	stop`,
    since: "0.2.0", related: ["cond-gamemode","event-world-change","cond-player"]
  },
  {
    id: "ph-player", category: "expressions",
    name: { ru: "%player% — имя игрока", en: "%player% — player name" },
    syntax: ["%player%"],
    desc: {
      ru: "Имя игрока из контекста (вызвал команду / зашёл / вышел). Работает в тексте и в именах переменных: `{coins::%player%}`.",
      en: "Name of the context player (ran the command / joined / quit). Works in text and in variable names: `{coins::%player%}`."
    },
    example: `send "&aПривет, %player%!" to player
set {last_seen::%player%} to "online"`,
    since: "0.1.0", related: ["ph-uuid","target-player"]
  },
  {
    id: "ph-uuid", category: "expressions",
    name: { ru: "%uuid% — UUID игрока", en: "%uuid% — player UUID" },
    syntax: ["%uuid%"],
    desc: {
      ru: "UUID игрока из контекста — удобно для переменных, которые не должны теряться при смене ника.",
      en: "UUID of the context player — handy for variables that must survive a name change."
    },
    example: `# Привязка статистики к UUID (не сбросится при смене ника)
add 1 to {joins::%uuid%}
send "&7Твой UUID: %uuid%" to player`,
    since: "0.1.0", related: ["ph-player"]
  },
  {
    id: "ph-online", category: "expressions",
    name: { ru: "%online% — игроков онлайн", en: "%online% — players online" },
    syntax: ["%online%"],
    desc: {
      ru: "Сколько игроков сейчас на сервере.",
      en: "How many players are online."
    },
    example: `broadcast "&7%player% зашёл &8(онлайн: &a%online%&8)"
set scoreboard line 1 to "&7Онлайн: &f%online%" for player`,
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
    example: `command /announce:
	permission: 2
	broadcast "&6[Объявление] &f%args%"

command /healp:
	permission: 2
	heal "%args%"
	reply "Вылечен: %args%"`,
    since: "0.1.0", related: ["struct-command","target-named"]
  },
  {
    id: "ph-targeted", category: "expressions",
    name: { ru: "%targeted% — куда смотрит игрок", en: "%targeted% — what the player looks at" },
    syntax: [
      "%targeted%",
      "%targeted player%",
      "%targeted entity%",
      "%targeted block%"
    ],
    desc: {
      ru: "`%targeted%` и `%targeted player%` — имя игрока в прицеле. `%targeted entity%` — моб или другая сущность (id, или имя). `%targeted block%` — id блока и координаты. Никого и ничего — `<none>`. Луч до `targetedPlayerRange` блоков, сквозь блоки не идёт. Ближе побеждает: сущность или блок.",
      en: "`%targeted%` and `%targeted player%` are the name of the player in the crosshair. `%targeted entity%` is a mob or any other entity (its id, or its name). `%targeted block%` is the block id and coordinates. Nothing — `<none>`. The ray is `targetedPlayerRange` blocks and stops at blocks. The closer one wins: the entity or the block."
    },
    example: `command /inspect:
	send "&eИгрок: &f%targeted player%" to player
	send "&eСущность: &f%targeted entity%" to player
	send "&eБлок: &f%targeted block%" to player`,
    since: "0.1.2", related: ["target-targeted","cfg-targetedPlayerRange"]
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
	send "&eПривет, %loop-player%! &7Тебя позвал %player%" to loop-player`,
    since: "0.1.2", related: ["struct-loop","target-loop-player","ph-loop-index"]
  },
  {
    id: "ph-loop-index", category: "expressions",
    name: { ru: "%loop-index% и %loop-value%", en: "%loop-index% and %loop-value%" },
    syntax: [
      "%loop-index%",
      "%loop-value%"
    ],
    desc: {
      ru: "Внутри `loop {имя::*}:` и строки `list from`. `%loop-index%` — ключ (`minecraft:diamond`), `%loop-value%` — значение. Вне цикла — пусто.",
      en: "Inside `loop {name::*}:` and a `list from` row. `%loop-index%` is the key (`minecraft:diamond`), `%loop-value%` is the value. Empty outside the loop."
    },
    example: `# Вывод содержимого словаря ключ -> значение
loop {bag::%player%::*}:
	send "&f%loop-index% &8x&e%loop-value%" to player`,
    since: "0.3.8", related: ["struct-loop","expr-variable","screen-list"]
  },
  {
    id: "expr-variable", category: "expressions",
    name: { ru: "Переменные {имя}", en: "Variables {name}" },
    syntax: [
      "{<имя>}",
      "{<имя>::%player%}",
      "{<имя>::*}",
      "\"%{<имя>}%\""
    ],
    desc: {
      ru: "Постоянные переменные в `scriptcraft/variables/variables.json`. В условиях и эффектах пишется `{coins::%player%}`. В тексте в кавычках фигурные скобки остаются как есть: значение подставляется только из `%{coins::%player%}%`. `{list::*}` — сколько прямых индексов. `set {list::*}` нельзя. `add ... to {list::*}` добавляет элемент, `remove ... from {list::*}` убирает одно равное значение.",
      en: "Persistent variables in `scriptcraft/variables/variables.json`. Conditions and effects use `{coins::%player%}`. Inside quoted text the braces stay as written: the value is inserted only from `%{coins::%player%}%.` `{list::*}` is how many direct indexes there are. `set {list::*}` is not allowed. `add ... to {list::*}` appends an element, `remove ... from {list::*}` deletes one equal value."
    },
    example: `# Обычные переменные и списки (сохраняются на диск)
set {coins::%player%} to 50
add 10 to {coins::%player%}
send "&eБаланс: %{coins::%player%}%" to player

# Списки и подсчёт элементов
add "меч" to {kit::*}
set {-total} to {kit::*}
send "&7Предметов в наборе: %{-total}%" to player`,
    since: "0.1.0", related: ["expr-temp-variable","eff-set-var","cfg-variablesSaveIntervalSeconds"]
  },
  {
    id: "expr-temp-variable", category: "expressions",
    name: { ru: "Временные переменные {-имя}", en: "Temporary variables {-name}" },
    syntax: ["{-<имя>}"],
    desc: {
      ru: "Имя начинается с `-`: живут только в памяти и стираются при старте/остановке сервера (если `enableTemporaryVariables = true`).",
      en: "Name starts with `-`: kept in memory only, wiped on server start/stop (when `enableTemporaryVariables = true`)."
    },
    example: `# Временные переменные (очищаются при перезапуске сервера)
set {-cooldown::%player%} to true
set {-arena_hp::%player%} to 20
send "&7Временное HP: %{-arena_hp::%player%}%" to player`,
    since: "0.1.0", related: ["expr-variable","cfg-enableTemporaryVariables"]
  },
  {
    id: "expr-values", category: "expressions",
    name: { ru: "Значения: числа, текст, true/false", en: "Values: numbers, text, true/false" },
    syntax: [
      "123",
      "-4.5",
      "\"текст\"",
      "true",
      "false"
    ],
    desc: {
      ru: "Цифры без кавычек — число, `true`/`false` — логическое значение, слово без кавычек — текст, текст с пробелами — в кавычках (`\\\"` внутри — кавычка). Запятые вне кавычек считаются пробелами.",
      en: "Unquoted digits are numbers, `true`/`false` are booleans, an unquoted word is text, text with spaces must be quoted (`\\\"` inside is a quote). Commas outside quotes count as spaces."
    },
    example: `set {level} to 5
set {multiplier} to 1.25
set {motd} to "Добро пожаловать!"
set {vip::%player%} to true`,
    since: "0.1.0", related: ["eff-set-var"]
  },
  {
    id: "expr-colors", category: "expressions",
    name: { ru: "Цвета &a, &l ...", en: "Colours &a, &l ..." },
    syntax: [
      "&0-&9",
      "&a-&f",
      "&k-&o",
      "&r"
    ],
    desc: {
      ru: "Цветовые коды и форматирование в стиле Bukkit работают в чате, титрах, скорборде, именах предметов, HUD и GUI:\n• Цвета: `&0` чёрный, `&1` тёмно-синий, `&2` тёмно-зелёный, `&3` бирюзовый, `&4` тёмно-красный, `&5` фиолетовый, `&6` золотой, `&7` серый, `&8` тёмно-серый, `&9` синий, `&a` зелёный, `&b` голубой, `&c` красный, `&d` розовый, `&e` жёлтый, `&f` белый.\n• Форматирование: `&l` жирный, `&o` курсив, `&n` подчёркнутый, `&m` зачёркнутый, `&k` бегущие символы, `&r` сброс стиля.",
      en: "Bukkit-style colour and formatting codes work in chat, titles, scoreboards, item names, HUD and GUI:\n• Colours: `&0` black, `&1` dark blue, `&2` dark green, `&3` dark aqua, `&4` dark red, `&5` dark purple, `&6` gold, `&7` gray, `&8` dark gray, `&9` blue, `&a` green, `&b` aqua, `&c` red, `&d` light purple, `&e` yellow, `&f` white.\n• Formatting: `&l` bold, `&o` italic, `&n` underline, `&m` strikethrough, `&k` obfuscated, `&r` reset."
    },
    example: `send "&6&lМАГАЗИН &r&7— &aоткрыт! &e&nНажми /shop" to player
send "&c&mСтарая цена: 100 &r&aНовая: 50" to player`,
    since: "0.1.0", related: ["eff-send"]
  },
  {
    id: "expr-property", category: "expressions",
    name: { ru: "Свойства игрока", en: "Player properties" },
    syntax: [
      "health of <цель>",
      "max health of <цель>",
      "food level of <цель>",
      "hunger of <цель>",
      "saturation of <цель>",
      "level of <цель>",
      "xp level of <цель>",
      "experience of <цель>",
      "air of <цель>",
      "oxygen of <цель>",
      "max air of <цель>",
      "absorption of <цель>",
      "walk speed of <цель>",
      "fly speed of <цель>",
      "flying speed of <цель>",
      "ping of <цель>",
      "x of <цель>",
      "y of <цель>",
      "z of <цель>",
      "yaw of <цель>",
      "pitch of <цель>",
      "gamemode of <цель>",
      "game mode of <цель>",
      "world of <цель>",
      "name of <цель>",
      "uuid of <цель>",
      "tool of <цель>",
      "held item of <цель>",
      "number of all players"
    ],
    desc: {
      ru: "Значение свойства первого игрока цели (нет игрока — `<none>` / условие ложно). Пишется в `if`, в `set {переменная} to ...` и в `add ... to {переменная}`, а также в конце эффектов вроде `heal player by health of player`. `gamemode` — текст `survival`/`creative`/`adventure`/`spectator`. `held item` — id предмета в руке или `minecraft:air`. `number of all players` — сколько игроков онлайн (то же, что `number of online players`).",
      en: "The property of the first player of the target (no player — `<none>` / the condition is false). Use it in `if`, in `set {variable} to ...` and `add ... to {variable}`, and at the end of effects such as `heal player by health of player`. `gamemode` is the text `survival`/`creative`/`adventure`/`spectator`. `held item` is the id in the hand or `minecraft:air`. `number of all players` is the online count (same as `number of online players`)."
    },
    example: `# Чтение свойств игрока в переменные и условия
set {-hp} to health of player
set {-world} to world of player
set {-gm} to gamemode of player
send "&7Мир: &f%{-world}% &7| Режим: &e%{-gm}% &7| HP: &c%{-hp}%" to player

if health of player < max health of player:
	heal player by 4

if number of all players >= 5:
	broadcast "&aНа сервере уже достаточно игроков для старта!"`,
    since: "0.2.0", related: ["eff-set-health","cond-compare","ph-online","expr-random","expr-location"]
  },
  {
    id: "expr-lp", category: "expressions",
    name: { ru: "Данные LuckPerms", en: "LuckPerms data" },
    syntax: [
      "prefix of <цель>",
      "suffix of <цель>",
      "group of <цель>",
      "groups of <цель>",
      "meta <ключ> of <цель>",
      "%prefix%",
      "%suffix%",
      "%group%",
      "%groups%",
      "%prefix of player%"
    ],
    desc: {
      ru: "Читает LuckPerms: префикс, суффикс, основную группу, список групп через запятую, meta по ключу. Работает в `set`, `if`, аргументе функции и в тексте. `%prefix%`, `%suffix%`, `%group%`, `%groups%` — игрок из контекста. `%prefix of player%` тоже он. Внутри функции `prefix of who` — параметр, а `%prefix%` остаётся вызвавшим, если параметра с именем `prefix` нет. Параметр или переменная `prefix` перекрывает `%prefix%`. Нет LuckPerms или значения — `<none>`.",
      en: "Reads LuckPerms: prefix, suffix, primary group, a comma-separated group list, meta by key. Works in `set`, `if`, a function argument and in text. `%prefix%`, `%suffix%`, `%group%`, `%groups%` are the context player. `%prefix of player%` is that player too. Inside a function, `prefix of who` is the parameter, while `%prefix%` stays the caller unless a parameter named `prefix` exists. A parameter or variable named `prefix` shadows `%prefix%`. No LuckPerms or no value — `<none>`."
    },
    example: `# Вывод префикса, группы и meta LuckPerms
send "%prefix%%player%%suffix%" to player
set {-rank} to group of player
set {-all} to groups of player
send "&7Основная: &e%{-rank}% &7| Все: &f%{-all}%" to player

if meta "color" of player is "gold":
	send "&6У вас золотая тема!" to player`,
    since: "0.4.5", related: ["eff-lp-prefix","eff-lp-group","cond-group","ph-player"]
  },
  {
    id: "expr-item-nbt", category: "expressions",
    name: { ru: "NBT предметов", en: "Item NBT" },
    syntax: [
      "nbt from items in player's inventory",
      "nbt of player's inventory",
      "%nbt from items in player's inventory%",
      "%nbt of slot <слот> of player%",
      "%nbt <путь> of slot <слот> of player%"
    ],
    desc: {
      ru: "Весь инвентарь — SNBT-список непустых слотов с полем `Slot`. Пустой инвентарь — `[]`. Один слот — SNBT предмета, пустой слот — `<none>`. Путь читает тег предмета, как `%nbt CustomName of targeted entity%`. Слот — число, `hotbar 1` или выражение. В тексте: `%nbt from items in player's inventory%`, `%nbt of items in player's inventory%`, `%nbt of player's inventory%`, `%nbt of slot 0 of player%`, `%nbt Damage of slot 0 of player's inventory%`. В функции `nbt of slot 0 of who` читает параметр.",
      en: "The whole inventory is an SNBT list of non-empty stacks with a `Slot` field. An empty inventory is `[]`. One slot is the item SNBT, an empty slot is `<none>`. A path reads the item tag, like `%nbt CustomName of targeted entity%`. The slot is a number, `hotbar 1` or an expression. In text: `%nbt from items in player's inventory%`, `%nbt of items in player's inventory%`, `%nbt of player's inventory%`, `%nbt of slot 0 of player%`, `%nbt Damage of slot 0 of player's inventory%`. Inside a function, `nbt of slot 0 of who` reads the parameter."
    },
    example: `# Чтение NBT всего инвентаря и конкретного слота
send "%nbt from items in player's inventory%" to player
set {-tag} to nbt of slot 0 of player
send "&7Прочность слота 0: %nbt Damage of slot 0 of player%" to player`,
    since: "0.4.5", related: ["eff-nbt","eff-set-slot","eff-give-item"]
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
    example: `# Точка в другом мире с поворотом камеры (yaw, pitch)
teleport player to location(8.5, 64, 51.5, world "lobby", -180, 0)

# Смещение относительно позиции игрока или блока в прицеле
teleport player to player's location ~~vector(0.0, 5.0, 0.0)
spawn particle flame at location of targeted block ~~vector(0.0, 1.0, 0.0) count 25 speed 0.02
set block at ~~vector(0, -1, 0) to gold_block`,
    since: "0.3.10", related: ["eff-teleport","eff-spawn","eff-set-block","eff-velocity","expr-targeted-block","expr-tool"]
  },
  {
    id: "expr-tool", category: "expressions",
    name: { ru: "Предмет в руке", en: "Held item" },
    syntax: [
      "player's tool",
      "tool of <цель>",
      "held item of <цель>",
      "%tool%"
    ],
    desc: {
      ru: "Id предмета в руке (`minecraft:air`, если пусто). `player` и `loop-player` в значении — имя игрока, не предмет. В тексте `%tool%` берётся у игрока из контекста.",
      en: "The id of the held item (`minecraft:air` if empty). As a value, `player` and `loop-player` are the player's name, not an item. In text, `%tool%` uses the context player."
    },
    example: `# Проверка и сохранение предмета в руке
send "&7У тебя в руке: &e%tool%" to player
add player's tool to {tools::%player%::*}
give player player's tool`,
    since: "0.3.10", related: ["expr-location","eff-give-item","eff-add-var"]
  },
  {
    id: "expr-targeted-block", category: "expressions",
    name: { ru: "Блок в прицеле", en: "Targeted block" },
    syntax: [
      "targeted block",
      "location of targeted block",
      "targeted location"
    ],
    desc: {
      ru: "`targeted block` — id блока в прицеле. `location of targeted block` и `targeted location` — точка, куда смотрит игрок.",
      en: "`targeted block` is the block id in the crosshair. `location of targeted block` and `targeted location` are the point the player is looking at."
    },
    example: `# Телепорт на блок в прицеле и запись его id
add targeted block to {seen::%player%::*}
teleport player to location of targeted block ~~vector(0, 1, 0)`,
    since: "0.3.10", related: ["expr-targeted-entity","expr-targeted-player","expr-location","target-targeted"]
  },
  {
    id: "expr-targeted-entity", category: "expressions",
    name: { ru: "Сущность в прицеле", en: "Targeted entity" },
    syntax: [
      "targeted entity",
      "location of targeted entity"
    ],
    desc: {
      ru: "`targeted entity` — id типа моба в прицеле. `location of targeted entity` — его точка.",
      en: "`targeted entity` is the type id of the mob in the crosshair. `location of targeted entity` is its point."
    },
    example: `# Проверка типа сущности в прицеле и спавн частиц на ней
set {-mob} to targeted entity
send "&7Вы смотрите на: &e%{-mob}%" to player
spawn particle heart at location of targeted entity count 10`,
    since: "0.3.10", related: ["expr-targeted-block","expr-targeted-player","ph-targeted"]
  },
  {
    id: "expr-targeted-player", category: "expressions",
    name: { ru: "Игрок в прицеле", en: "Targeted player" },
    syntax: [
      "targeted player",
      "location of targeted player"
    ],
    desc: {
      ru: "`targeted player` — имя игрока в прицеле. `location of targeted player` — его точка.",
      en: "`targeted player` is the name of the player in the crosshair. `location of targeted player` is their point."
    },
    example: `# Информация об игроке в прицеле и телепорт к нему
set {-who} to targeted player
send "&aВы смотрите на: &f%{-who}%" to player
teleport player to location of targeted player`,
    since: "0.3.10", related: ["expr-targeted-block","expr-targeted-entity","ph-targeted","target-targeted"]
  },
  {
    id: "expr-random", category: "expressions",
    name: { ru: "Случайное число", en: "Random number" },
    syntax: [
      "random integer between <A> and <B>",
      "random int between <A> and <B>",
      "random number between <A> and <B>"
    ],
    desc: {
      ru: "`integer` — целое от меньшего к большему, включительно. `number` — дробное в том же промежутке. Границы могут быть выражениями (`health of player`).",
      en: "`integer` is a whole number from the lower bound to the upper, inclusive. `number` is a fraction in the same range. The bounds may be expressions (`health of player`)."
    },
    example: `# Бросок кубика (целое число от 1 до 6 включительно)
set {-roll} to random integer between 1 and 6
if {-roll} is 6:
	give player diamond 1
	send "&6Выпала шестёрка! +1 алмаз" to player

# Случайное дробное число (например, для громкости или множителя)
set {-pitch} to random number between 0.8 and 1.4
play sound entity.experience_orb.pickup to player with volume 1 pitch {-pitch}`,
    since: "0.2.0", related: ["expr-property","struct-chance","eff-xp","eff-give-levels"]
  },
  {
    id: "expr-script", category: "expressions",
    name: { ru: "Имя скрипта", en: "Script name" },
    syntax: [
      "script",
      "%script%"
    ],
    desc: {
      ru: "Имя файла, чей код сейчас выполняется, без пути и без .sc. Пустая строка у эффект-команды в чате. Работает в тексте, в set и в условии.",
      en: "The file whose code is running, without the path and without .sc. Empty for a chat effect command. Works in text, in set and in a condition."
    },
    example: `send "&7Выполнено из скрипта: &e%script%" to player
set {-source} to script`,
    since: "0.3.4", related: ["struct-command","expr-argument"]
  },
  {
    id: "expr-argument", category: "expressions",
    name: { ru: "Аргумент команды", en: "Command argument" },
    syntax: [
      "arg-1",
      "argument 1",
      "%arg-1%",
      "player-argument",
      "text-argument",
      "integer-argument",
      "number-argument",
      "boolean-argument",
      "date-argument",
      "world-argument",
      "item-argument",
      "offline player-argument"
    ],
    desc: {
      ru: "Значение аргумента текущей команды. arg-1 — первый, argument 2 — второй. player-argument и остальные — первый аргумент этого типа. Число остаётся числом, true/false — логическим значением, игрок — его именем (heal arg-1 лечит этого игрока). date понимает today, tomorrow, yesterday и ГГГГ-ММ-ДД. Нет аргумента — <none>.",
      en: "A value of the current command. arg-1 is the first, argument 2 is the second. player-argument and the other type forms are the first argument of that type. A number stays a number, true/false stays a boolean, a player is their name (heal arg-1 heals that player). date accepts today, tomorrow, yesterday and YYYY-MM-DD. A missing argument is <none>."
    },
    example: `command /pay <player> <integer> [text]:
	permission: 0
	aliases: payme
	if arg-1 is not set or arg-2 is not set:
		send "&eИспользование: /pay <игрок> <сумма> [комментарий]" to player
		stop
	remove arg-2 from {coins::%player%}
	add arg-2 to {coins::%arg-1%}
	send "&aПереведено %arg-2% монет игроку %arg-1%" to player
	if arg-3 is set:
		send "&e%player% перевёл вам %arg-2% монет: &f%arg-3%" to arg-1`,
    since: "0.3.4", related: ["struct-command","expr-script","ph-args"]
  },
  {
    id: "ph-event", category: "expressions",
    name: { ru: "Плейсхолдеры событий", en: "Event placeholders" },
    syntax: [
      "%message%",
      "%block%",
      "%item%",
      "%x%",
      "%y%",
      "%z%",
      "%attacker%",
      "%cause%",
      "%damage%",
      "%world%",
      "%from%",
      "%gamemode%"
    ],
    desc: {
      ru: "Контекстные плейсхолдеры событий:\n• `on chat`: `%message%` (и выражение `message`).\n• `on break`, `on place`, `on right click`, `on left click`: `%block%`, `%x%`, `%y%`, `%z%` (плюс `%clicked_entity%`, `%clicked_player%` в `on right click`).\n• `on drop`, `on pickup`, `on consume`, `on fish`, `on item break`: `%item%`.\n• `on receive`, `on craft`: `%item%`, `%amount%`.\n• `on death`, `on damage`, `on kill`, `on attack`: `%attacker%`, `%victim%`, `%cause%`, `%damage%`.\n• `on shoot`, `on projectile hit`: `%projectile%`, `%victim%`, `%block%`, `%x%`, `%y%`, `%z%`.\n• `on world change`: `%from%`, `%world%`.\n• `on gamemode change`: `%from%`, `%gamemode%`.\n• `on key`: `%key%`, `%screen%`. `on open`: `%menu%`. `on gui change`: `%element%`, `%value%`, `%screen%`.\n• `on sneak` / `on sprint`: `%sneaking%` / `%sprinting%`. `on command`: `%command%`, `%args%`.",
      en: "Context-specific event placeholders:\n• `on chat`: `%message%` (and `message` expression).\n• `on break`, `on place`, `on right click`, `on left click`: `%block%`, `%x%`, `%y%`, `%z%` (plus `%clicked_entity%`, `%clicked_player%` in `on right click`).\n• `on drop`, `on pickup`, `on consume`, `on fish`, `on item break`: `%item%`.\n• `on receive`, `on craft`: `%item%`, `%amount%`.\n• `on death`, `on damage`, `on kill`, `on attack`: `%attacker%`, `%victim%`, `%cause%`, `%damage%`.\n• `on shoot`, `on projectile hit`: `%projectile%`, `%victim%`, `%block%`, `%x%`, `%y%`, `%z%`.\n• `on world change`: `%from%`, `%world%`.\n• `on gamemode change`: `%from%`, `%gamemode%`.\n• `on key`: `%key%`, `%screen%`. `on open`: `%menu%`. `on gui change`: `%element%`, `%value%`, `%screen%`.\n• `on sneak` / `on sprint`: `%sneaking%` / `%sprinting%`. `on command`: `%command%`, `%args%`."
    },
    example: `on chat:
	log "[CHAT] %player%: %message%"

on break:
	send "&7Сломан %block% на %x% %y% %z%" to player

on kill:
	send "&aУбит %victim% (причина: %cause%)" to player`,
    since: "0.2.0", related: ["event-chat","event-break","event-place","event-right-click","event-left-click","event-drop","event-pickup","event-damage","event-death","event-world-change","event-gamemode"]
  },
  {
    id: "target-player", category: "targets",
    name: { ru: "player — игрок из контекста", en: "player — context player" },
    syntax: [
      "player",
      "%player%"
    ],
    desc: {
      ru: "Тот, кто вызвал команду / зашёл / написал эффект-команду. Из консоли — ошибка «действие требует игрока».",
      en: "Whoever ran the command / joined / typed the effect command. From the console it is an error (“requires a player”)."
    },
    example: `# Игрок, вызвавший событие или команду
command /me_heal:
	heal player
	feed player
	send "&aВы полностью восстановлены, %player%!" to player`,
    since: "0.1.0", related: ["ph-player"]
  },
  {
    id: "target-named", category: "targets",
    name: { ru: "Игрок по имени", en: "Player by name" },
    syntax: [
      "\"<имя>\"",
      "{<переменная>}",
      "\"%args%\"",
      "<имя>"
    ],
    desc: {
      ru: "Игрок онлайн с таким именем. Если его нет в сети — пустая цель (действие ничего не делает). Селекторы внутри текста не разбираются: `\"%args%\"` = `@a` не выберет всех.",
      en: "The online player with that name. If they are offline the target is empty (the action does nothing). Selectors inside text are not parsed: `\"%args%\"` = `@a` does not select everyone."
    },
    example: `# Обращение к игроку по имени или переменной
command /poke <text>:
	send action bar "&eВас позвал %player%!" to arg-1
	play sound entity.experience_orb.pickup to arg-1

# Игрок из переменной
set {-friend} to "Steve"
send "&aПривет!" to {-friend}`,
    since: "0.1.2", related: ["ph-args"]
  },
  {
    id: "target-all", category: "targets",
    name: { ru: "Все игроки", en: "All players" },
    syntax: [
      "all players",
      "@a"
    ],
    desc: {
      ru: "Все игроки онлайн.",
      en: "Every online player."
    },
    example: `# Действие для всех игроков на сервере
command /event_start:
	permission: 2
	send title "&6Событие началось!" with subtitle "&eУдачи всем" to all players
	play sound entity.ender_dragon.growl to all players with volume 0.5
	give all players bread 8`,
    since: "0.1.2", related: ["target-selector","struct-loop"]
  },
  {
    id: "target-targeted", category: "targets",
    name: { ru: "Игрок, на которого смотрят", en: "Targeted player" },
    syntax: [
      "targeted player",
      "targeted player of <цель>",
      "target of <цель>"
    ],
    desc: {
      ru: "Игрок, на которого смотрит `player` (или игроки другой цели): луч из глаз до `targetedPlayerRange` блоков, сквозь блоки не видит, наблюдатели не считаются. Никого — пустая цель.",
      en: "The player `player` (or each player of another target) is looking at: a ray from the eyes up to `targetedPlayerRange` blocks, blocked by blocks, spectators ignored. Nobody — empty target."
    },
    example: `# Воздействие на игрока в прицеле
command /freeze:
	permission: 2
	if targeted player is not set:
		send "&cНаведите прицел на игрока!" to player
		stop
	give targeted player effect slowness for 10 seconds level 10
	send title "&bВы заморожены!" to targeted player
	send "&aЗаморожен игрок: %targeted%" to player`,
    since: "0.1.2", related: ["ph-targeted","cond-target-set","cfg-targetedPlayerRange"]
  },
  {
    id: "target-radius", category: "targets",
    name: { ru: "Игроки в радиусе", en: "Players in radius" },
    syntax: [
      "players in radius <N> of <цель>",
      "players within <N> [blocks] of <цель>",
      "players in radius <N> around <цель>",
      "other players in radius <N> of <цель>"
    ],
    desc: {
      ru: "Игроки в том же мире не дальше N блоков от каждого игрока цели-центра. N — число или `{переменная}`. Центр входит в набор; с `other` — без самих центров.",
      en: "Players in the same world within N blocks of each centre player. N is a number or `{variable}`. The centre is included; with `other` the centres are excluded."
    },
    example: `# Лечение союзников рядом (включая себя)
command /heal_area:
	heal players in radius 12 of player by 6
	spawn particle heart at player count 20

# Отбрасывание других игроков вокруг (кроме себя)
command /shockwave:
	push other players in radius 8 of player upwards with force 1.1
	send "&c%player% создал ударную волну!" to other players in radius 8 of player`,
    since: "0.1.2", related: ["target-selector","struct-loop"]
  },
  {
    id: "target-selector", category: "targets",
    name: { ru: "Ванильные селекторы", en: "Vanilla selectors" },
    syntax: [
      "@p",
      "@r",
      "@s",
      "@a[<аргументы>]",
      "@p[...]",
      "@r[...]",
      "@s[...]"
    ],
    desc: {
      ru: "Разбираются самим Minecraft, от лица игрока из контекста (из консоли — от сервера) с уровнем прав 2. `@p` от игрока — он сам (как в ванилле), `@r` — случайный, `@s` — сам игрок. Аргументы как в командах: `distance=..10`, `limit=2`, `gamemode=survival`, `name=...`. `@e` не поддерживается — только игроки. Пробелов внутри `[...]` лучше не ставить.",
      en: "Parsed by Minecraft itself, as the context player (the server from the console) with permission level 2. `@p` from a player is that player (vanilla behaviour), `@r` is random, `@s` is the player. Arguments as in commands: `distance=..10`, `limit=2`, `gamemode=survival`, `name=...`. `@e` is not supported — players only. Avoid spaces inside `[...]`."
    },
    example: `# Ванильные селекторы с фильтрами
give @a[distance=..15,gamemode=survival] effect regeneration for 10 seconds
give 50 xp to @r
teleport @a[distance=..5] to 0 100 0`,
    since: "0.1.2", related: ["target-all","target-radius"]
  },
  {
    id: "target-loop-player", category: "targets",
    name: { ru: "loop-player — игрок цикла", en: "loop-player" },
    syntax: ["loop-player"],
    desc: {
      ru: "Текущий игрок внутри `loop <цель>:`. Вне цикла — пустая цель.",
      en: "The current player inside `loop <target>:`. Empty outside a loop."
    },
    example: `# Индивидуальные действия для каждого игрока в цикле
command /payday:
	permission: 2
	loop all players:
		add 50 to {coins::%loop-player%}
		send "&aЗарплата: +50 монет! Баланс: %{coins::%loop-player%}%" to loop-player`,
    since: "0.1.2", related: ["struct-loop","ph-loop-player"]
  },
  {
    id: "screen-text", category: "screen",
    name: { ru: "Текст на экране", en: "Screen text" },
    syntax: [
      "text \"<текст>\" [at <X> <Y>] [anchor <точка>] [color #RRGGBB] [shadow]",
      "text \"<текст>\" run \"/<команда>\"",
      "text \"<текст>\" run <Функция>(<аргументы>)",
      "text \"<текст>\" close"
    ],
    desc: {
      ru: "Надпись. `color` — `#RRGGBB` или `#AARRGGBB`, `shadow` — с тенью. Плейсхолдеры и `{переменные}` подставляются при открытии. `run` (команда `\"/cmd\"` или функция `MyFunc(player)`), `function MyFunc(player)` или `close` делают хитбокс: ЛКМ по тексту (или по `size`, если задан) выполняет действие и закрывает экран. На HUD клика нет.",
      en: "A label. `color` is `#RRGGBB` or `#AARRGGBB`, `shadow` adds a shadow. Placeholders and `{variables}` are filled in when opening. `run` (a `\"/cmd\"` command or a `MyFunc(player)` function call), `function MyFunc(player)` or `close` adds a hitbox: left click on the text (or on `size`, if set) runs the action and closes the screen. HUD elements are not clickable."
    },
    example: `# Обычная надпись с тенью и цветом
text "&6Добро пожаловать, %player%!" at 0, -60 color #FFD700 shadow

# Кликабельная надпись с тултипом и keep open
text "&a[Нажать для бонуса]" at 0, -35 run GiveBonus(player) keep open tooltip "&7Выдаст 10 монет"

# Текст с id для обновления через set text of screen
text "&fСчёт: 0" id "score" at 0, -10 anchor center`,
    since: "0.1.0", related: ["eff-open-screen","screen-layout","struct-function"]
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
    example: `# Кнопка с горячей клавишей и без закрытия окна
button "Вверх (W)" at 0, -30 size 100x20 key w run Move(player, "up") keep open tooltip "&7Или нажмите W"

# Кнопка с функцией и передачей аргументов
button "&aКупить меч" at 0, 0 size 120x20 run BuyItem(player, "diamond_sword", 50) keep open

# Кнопка, которая закрывает экран
button "&cЗакрыть" at 0, 30 size 120x20 close`,
    since: "0.1.0", related: ["eff-open-screen","screen-layout","struct-function","event-key"]
  },
  {
    id: "screen-slot", category: "screen",
    name: { ru: "Слот с предметом", en: "Item slot" },
    syntax: [
      "slot <предмет> [<количество>] [at <X> <Y>] [anchor <точка>]",
      "slot <предмет> ... run <Функция>(<аргументы>)",
      "slot hotbar <1-9>",
      "slot image \"<картинка>\"",
      "slot text \"<текст>\""
    ],
    desc: {
      ru: "Показывает предмет с подсказкой при наведении. Сам по себе слот ничего не берёт. В id можно дописать nbt, как в `give`. `run \"/команда\"`, `run MyFunc(player)` или `close` — хитбокс ЛКМ по прямоугольнику слота на `open screen` (на HUD клика нет). С 0.1.33 слот может показывать предмет из хотбара игрока, картинку или текст (см. «Слоты HUD»).",
      en: "Shows an item with a tooltip on hover. By itself the slot takes nothing. The id may include nbt, as in `give`. `run \"/command\"`, `run MyFunc(player)` or `close` is a left-click hitbox on the slot rectangle in `open screen` (HUD elements are not clickable). Since 0.1.33 a slot can also show an item from the player's hotbar, an image or a text (see “HUD slots”)."
    },
    example: `# Кликабельный слот товара в магазине
slot diamond 5 at -40, -15 run BuyItem(player, "diamond", 5) keep open tooltip "&bАлмазы x5\\n&eЦена: 250 монет"

# Слот с предметом из 1-го слота хотбара игрока
slot hotbar 1 at 0, -15

# Слот с картинкой и слот с текстом
slot image "example:textures/gui/coin.png" at 40, -15
slot text "Q" at 70, -15`,
    since: "0.1.0", related: ["eff-open-screen","hud-slot","struct-function"]
  },
  {
    id: "screen-image", category: "screen",
    name: { ru: "Картинка", en: "Image" },
    syntax: ["image \"<ns>:<путь>.png\" [at <X> <Y>] [size <W>x<H>] [texture <W>x<H>] [anchor <точка>] [color #AARRGGBB] [run \"/<команда>\" | run <Функция>(<аргументы>) | close]"],
    desc: {
      ru: "Рисует текстуру. `texture` — реальный размер файла, `color` — оттенок. `run` (команда или функция) или `close` — хитбокс ЛКМ по `size` (без размера это 16x16) на `open screen`. Картинки из `scriptcraft/resources/assets/<ns>/textures/gui/` сервер отправляет игрокам сам, они показываются сразу.",
      en: "Draws a texture. `texture` is the real file size, `color` tints it. `run` (command or function) or `close` is a left-click hitbox on `size` (16x16 when size is omitted) in `open screen`. Images from `scriptcraft/resources/assets/<ns>/textures/gui/` are sent to players by the server and show up immediately."
    },
    example: `# Баннер с указанием размера файла (texture) и размера на экране (size)
image "example:textures/gui/menu_banner.png" at 0, -85 size 128x32 texture 128x32

# Кликабельная иконка с оттенком (color #AARRGGBB) и тултипом
image "minecraft:textures/item/diamond.png" at -40, 0 size 24x24 texture 16x16 color #FF88FFFF run GiveReward(player) keep open tooltip "&bНажми!"`,
    since: "0.1.0", related: ["eff-open-screen","cmd-reload","struct-function"]
  },
  {
    id: "screen-layout", category: "screen",
    name: { ru: "Позиция и привязка", en: "Position and anchor" },
    syntax: [
      "at <X> <Y>",
      "anchor center",
      "anchor top_left",
      "anchor top",
      "anchor top_right",
      "anchor left",
      "anchor right",
      "anchor bottom_left",
      "anchor bottom",
      "anchor bottom_right"
    ],
    desc: {
      ru: "Элемент выравнивается по точке привязки экрана и сдвигается на `X Y` пикселей (отрицательные — влево и вверх). Все 9 точек привязки и их синонимы:\n• `center` (`middle` — по умолчанию)\n• `top_left`, `top` (`top_center`, `center_top`), `top_right`\n• `left`, `right`\n• `bottom_left`, `bottom` (`bottom_center`, `center_bottom`), `bottom_right`",
      en: "Aligns the element to an anchor point and shifts by `X Y` pixels (negative = left/up). All 9 anchors & aliases:\n• `center` (`middle` — default)\n• `top_left`, `top` (`top_center`, `center_top`), `top_right`\n• `left`, `right`\n• `bottom_left`, `bottom` (`bottom_center`, `center_bottom`), `bottom_right`"
    },
    example: `# Привязка к разным углам и краям экрана
text "&6ScriptCraft" at 6, 6 anchor top_left shadow
text "&eЦентр" at 0, -40 anchor center
text "&7v0.7.6" at -6, -6 anchor bottom_right color #808080`,
    since: "0.1.0", related: ["screen-text","screen-button","hud-layer"]
  },
  {
    id: "screen-click", category: "screen",
    name: { ru: "Хитбокс клика", en: "Click hitbox" },
    syntax: [
      "text \"<текст>\" run \"/<команда>\"",
      "text \"<текст>\" run <Функция>(<аргументы>)",
      "image \"<путь>\" size <W>x<H> run <Функция>(<аргументы>)",
      "text \"<текст>\" close",
      "<элемент> ... hover_sound <звук> [volume <V>] [pitch <P>] [on_hover <Функция>(...) | \"/<команда>\"]"
    ],
    desc: {
      ru: "ЛКМ и наведение курсора на элементы в `open screen`:\n• **Клик (`run` / `close`)**: работает у `button`, `text`, `image`, `box`, `slot`, `entity`/`player` и строк `row` в `list`. `run \"/команда\"` выполняет команду от игрока, `run MyFunc(player)` вызывает функцию на сервере, `close` закрывает экран (или добавьте `keep open`, чтобы не закрывать).\n• **Звук при наведении (`hover_sound`)**: мгновенно проигрывает клиентский UI-звук (0 мс задержки) в момент входа курсора на элемент или на конкретную строку `row` в `list` (например: `hover_sound block.note_block.hat volume 0.4 pitch 1.35`).\n• **Действие при наведении (`on_hover` / `hover_run`)**: вызывает функцию скрипта или команду один раз при наведении мыши на элемент или строку `row` (например, для живого переключения 3D-превью роли без клика).",
      en: "Left-click and mouse-hover actions in `open screen`:\n• **Click (`run` / `close`)**: supported on `button`, `text`, `image`, `box`, `slot`, `entity`/`player`, and `list` rows (`keep open` prevents closing).\n• **Hover sound (`hover_sound`)**: plays an instant zero-latency client UI sound when the cursor enters an element or an individual `row` in `list` (`hover_sound block.note_block.hat volume 0.4 pitch 1.35`).\n• **Hover action (`on_hover` / `hover_run`)**: runs a script function or command once on hover entry (ideal for live 3D previews without clicking)."
    },
    example: `# ЛКМ по картинке, тексту и слоту (с keep open или закрытием)
image "minecraft:textures/item/diamond.png" at -40, -10 size 32x32 texture 16x16 run GiveReward(player, "diamond") keep open
text "&aВзять алмаз" at -40, 26 run GiveReward(player, "diamond") keep open
slot emerald 1 at 40, -10 run "/shop"
text "&cВыход" at 0, 60 close`,
    since: "0.3.7", related: ["screen-text","screen-image","screen-slot","screen-list","eff-open-screen","struct-function"]
  },
  {
    id: "screen-list", category: "screen",
    name: { ru: "Продвинутый список (размер, разделители, иконки, тултипы, выравнивание)", en: "Advanced List (sizing, separators, inline elements, tooltips, alignment)" },
    syntax: [
      "list [from {<имя>::*}] [vertical|horizontal] [at <X> <Y>] [size <W>x<H>] [row_height <N>] [spacing <N>] [scale <S>] [align left|center|right] [header \"<текст>\"] [empty_text \"<текст>\"] [columns \"W1,W2,...\"] [select] [disabled|active] [background #hex|none] [border #hex|none] [tooltip \"<текст>\"] [no scrollbar|no slider]:",
      "row \"<текст>\" [width <W>] [height <H>] [align left|center|right] [active|disabled] [item <id> [<N>] | icon \"<путь>\"] [head \"<ник|URL|ресурс>\"] [badge \"<тег>\" [badge_color #hex]] [right \"<текст>\"] [bar <0..100|V/M> [bar_color #hex]] [button \"<текст>\" [button_run ...]] [color #hex] [tooltip \"<текст>\"] [hover_sound <id> [volume <V>] [pitch <P>]] [on_hover <Функция>(...)] [run ... | close] [keep open]",
      "separator [\"<заголовок раздела>\"] [color #hex] [height <H>] [align left|center|right]",
      "empty [<высота>] [height <H>] [width <W>]"
    ],
    desc: {
      ru: "Полноценный прокручиваемый список для магазинов, лидербордов, инвентарей, вкладок и журналов квестов:\n• **Размер и масштаб**: `size <W>x<H>` задаёт область списка, `row_height <N>` (по умолчанию `14`) увеличивает высоту строк, `spacing <N>` (`gap`) добавляет отступ между позициями, а `scale <S>` масштабирует текст внутри строк.\n• **Индивидуальная ширина и высота позиций**: у любой строки можно указать `width <W>` (например, разная ширина вкладок в `horizontal`-списке или суженная карточка в `vertical`) и `height <H>`, а также задать табличные колонки `columns \"30, 120, 60\"` с разделением ячеек через `|` или `||`.\n• **Разделители и пустые списки**: строка `separator [\"Раздел\"]` рисует линию-разделитель с подписью, `empty [N]` (`spacer`) — пустой отступ. Сам блок `list:` может быть пустым (без строк), а параметр `empty_text \"&7Список пуст\"` автоматически выводит надпись, когда список или `{переменная::*}` пусты.\n• **Активные и неактивные списки / строки**: флаг `disabled` (`inactive`) на `list` делает весь список неактивным (приглушается, клики блокируются; переключается на лету через `enable screen \"id\"` / `disable screen \"id\"`). На отдельных `row` доступны `active` (`selected` — акцентная подсветка выбранного пункта) и `disabled` (строка недоступна для нажатия).\n• **Тултипы**: `tooltip \"...\"` работает и на всём `list`, и индивидуально на каждой строке `row` (а при наведении на `item` без кастомного тултипа показывается ванильное описание предмета).\n• **Элементы внутри строки**: `item <предмет> [<кол-во>]` или `icon \"ns:textures/...\"`, 2D-голова скина `head \"<ник|URL|ресурс>\"`, цветной тег `badge \"VIP\"`, правая подпись/цена `right \"&e500 ⛁\"`, мини-полоса `bar 7/10` и встроенная кнопка `button \"Купить\"`.\n• **Выравнивание**: `align left | center | right` задаётся как для всего списка (включая `header`), так и отдельно для конкретной `row` или `separator`.",
      en: "Full-featured scrollable list for shops, leaderboards, tabs, quest logs, and menus:\n• **Sizing & Scale**: `size <W>x<H>`, `row_height <N>` (default `14`), `spacing <N>` (`gap`), and `scale <S>`.\n• **Per-item Width & Columns**: set `width <W>` and `height <H>` on any specific `row` (ideal for variable-width `horizontal` tabs or custom vertical cards), or define multi-column widths via `columns \"30, 120, 60\"` using `|` / `||` in row text.\n• **Separators & Empty Lists**: `separator [\"Section\"]` draws a labeled divider; `empty [N]` (`spacer`) adds blank space; empty `list:` blocks are valid and `empty_text \"&7Empty\"` displays centered text when 0 rows exist.\n• **Active / Inactive Lists & Rows**: `disabled` (`inactive`) dims the entire list and blocks clicks (toggle live via `enable/disable screen \"id\"`); per-row `active` (`selected`) highlights the active entry and `disabled` locks an individual row.\n• **Tooltips**: `tooltip \"...\"` is supported both on the `list` and on each individual `row` (plus vanilla item tooltips on inline `item` icons).\n• **Inline Row Elements**: `item <id> [<count>]` / `icon \"...\"`, 2D player face `head \"<nick|URL|resource>\"`, `badge \"VIP\"`, right-aligned `right \"&e500\"`, inline `bar 7/10`, and mini action `button \"Buy\"`.\n• **Alignment**: `align left | center | right` on the `list` and per `row` / `separator`."
    },
    example: `# Продвинутый магазин с иконками, разделителями, бейджами, ценами и тултипами:
list vertical at -75 0 size 195x155 row_height 20 spacing 2 header "&6&lЛавка снаряжения" align left empty_text "&7Товаров пока нет" select id shop_list:
	row "&fАлмазный меч" item diamond_sword badge "ХИТ" right "&e500 ⛁" tooltip "&bУрон +7%nl%&aНажмите для покупки" run Buy(player, "diamond_sword") keep open
	separator "&7Зелья и еда" align center
	row "&fЗолотое яблоко" item golden_apple 4 right "&e120 ⛁" active run Buy(player, "golden_apple") keep open
	empty 6
	row "&7Элитры" item elytra right "&c2500 ⛁" disabled tooltip "&cТребуется 25 уровень!"

# Горизонтальные вкладки с разной шириной конкретных позиций:
list horizontal at 0 -90 size 240x22 row_height 18 spacing 4 align center no scrollbar:
	row "&eПрофиль" width 68 active keep open
	row "&fДостижения и квесты" width 118 run OpenQuests(player) keep open
	row "&7⚙" width 28 tooltip "&7Настройки" run OpenSettings(player) keep open`,
    since: "0.3.7", related: ["screen-entity-player","screen-click","eff-open-screen","screen-dynamic","expr-variable","ph-loop-index","struct-function"]
  },
  {
    id: "screen-dynamic", category: "screen",
    name: { ru: "Динамическое обновление экрана", en: "Dynamic screen updates" },
    syntax: [
      "update screen [\"<заголовок>\"] [for <цель>]:",
      "set text of screen \"<id>\" [for <цель>] to \"<текст>\"",
      "set image of screen \"<id>\" [for <цель>] to \"<текстура>\"",
      "set skin of screen \"<id>\" [for <цель>] to \"<ник|URL|ресурс>\"",
      "set entity of screen \"<id>\" [for <цель>] to \"<entity_id>\"",
      "set spin of screen \"<id>\" [for <цель>] to [left|right] <скорость>",
      "set look of screen \"<id>\" [for <цель>] to cursor | none | <X> <Y>",
      "set position of screen \"<id>\" [for <цель>] to <X> <Y>",
      "set size of screen \"<id>\" [for <цель>] to <W> <H>",
      "move screen \"<id>\" [for <цель>] to <X> <Y>",
      "set item of screen \"<id>\" [for <цель>] to <предмет> [<кол-во>]",
      "set value of screen bar \"<id>\" [for <цель>] to <значение>",
      "enable screen \"<id>\" [for <цель>]",
      "disable screen \"<id>\" [for <цель>]",
      "show screen \"<id>\" [for <цель>]",
      "hide screen \"<id>\" [for <цель>]",
      "remove screen \"<id>\" [from <цель>]",
      "clear screen [of <цель>]",
      "close screen [of <цель>]"
    ],
    desc: {
      ru: "Обновляет уже открытый кастомный экран игрока на лету **без повторного `open screen` и без пересоздания окна** (фокус, прокрутка и зажатые клавиши сохраняются). Блок `update screen for player:` добавляет новые элементы или заменяет существующие элементы с тем же `id`. Точечные эффекты изменяют текст, картинку, скин/моба/вращение/взгляд 3D-рендера, координаты (`at X Y`), размер (`set size`), предмет слота, значение полосы, активность (`enable`/`disable`) и видимость (`show`/`hide`) элемента по его `id`, либо удаляют элемент (`remove screen \"id\"`). Если новое состояние элемента или экрана не отличается от текущего, пакет по сети не отправляется. Для проверки открытого экрана используйте условия `if player has screen open:`, `if player has screen \"Меню\" open:`, `if player is in screen \"Меню\":` или выражение `screen of player` / `%screen%`.",
      en: "Updates a player's currently open custom screen on the fly **without re-running `open screen` and without recreating the GUI** (focus, scroll and held keys are preserved). The `update screen for player:` block adds new elements or replaces existing elements with the same `id`. Targeted effects change text, texture, 3D skin/entity/spin/look, coordinates, size (`set size`), slot item, bar value, active state (`enable`/`disable`), or visibility (`show`/`hide`) by `id`, or remove an element (`remove screen \"id\"`). Check the active screen with `if player has screen open:`, `if player has screen \"Menu\" open:`, `if player is in screen \"Menu\":` or `screen of player` / `%screen%`."
    },
    example: `# Точечное обновление текста, скин-модели, активности списка и видимости
set text of screen "score" for player to "&eОчки: &f%{score::%player%}%"
set skin of screen "hero_preview" for player to "ElgerElg"
set spin of screen "hero_preview" for player to right 45
disable screen "shop_list" for player
enable screen "shop_list" for player

# Пакетное обновление или добавление элементов без переоткрытия экрана
update screen for player:
	image "example:textures/gui/coin.png" id "apple" at {-ax} {-ay} size 12x12`,
    since: "0.6.2", related: ["eff-open-screen","screen-entity-player","screen-list","screen-button","event-screen-open","event-key"]
  },
  {
    id: "hud-create", category: "hud",
    name: { ru: "Создать HUD", en: "Create HUD" },
    syntax: [
      "create hud \"<группа>\":",
      "create hud \"<группа>\" for <цель>:"
    ],
    desc: {
      ru: "Рисует поверх экрана игры группу элементов, перечисленных с отступом ниже: `text`, `image`, `slot`, `bar`, `hotbar` (кнопок нет — HUD не принимает клики). Имя группы — латиница. Повторный `create hud` с тем же именем заменяет группу целиком (если ничего не изменилось — пакет не отправляется). Если в тексте, `value` или `max` есть `{переменная}`, HUD обновляет её сам в момент записи: пакет уходит только этому игроку и только если строка изменилась. HUD хранится на сервере: после перезахода игрок получает его снова, при остановке сервера он очищается. `/sc reload` и `/sc reload <скрипт>` заново применяют HUD из `on load` и `on join` этого скрипта игрокам, которые уже онлайн: `create hud for player` без игрока в контексте идёт всем онлайн. Группа запоминает создавший её скрипт: `/sc disable` этого скрипта убирает её у всех. До 128 элементов на игрока.",
      en: "Draws a group of elements over the game screen, listed indented below: `text`, `image`, `slot`, `bar`, `hotbar` (no buttons — the HUD takes no clicks). The group name is Latin. Running `create hud` again with the same name replaces the whole group (nothing is sent if nothing changed). If a text, `value` or `max` contains a `{variable}`, the HUD updates it when that variable is written: a packet goes only to that player, and only if the text changed. The HUD is kept on the server: players get it back after re-joining, and it is cleared when the server stops. `/sc reload` and `/sc reload <script>` reapply that script's HUD from `on load` and `on join` for players already online: `create hud for player` with no player in context goes to everyone online. A group remembers the script that created it: `/sc disable` of that script removes it for everyone. Up to 128 elements per player."
    },
    example: `# HUD, автоматически обновляемый при изменении переменной
on join or load:
	create hud "stats" for player:
		hotbar color #FFD700
		bar "hp" value health at -51, -28 size 80x8 anchor bottom color #E53935
		text "&c%health%/%max_health%" at -51, -38 anchor bottom shadow layer 3
		text "&eМонеты: %{coins::%player%}%" at 6, 6 anchor top_left shadow`,
    since: "0.1.33", related: ["hud-bar","hud-hotbar","hud-slot","hud-layer","hud-show-hide","eff-open-screen"]
  },
  {
    id: "hud-bar", category: "hud",
    name: { ru: "Полоса прогресса", en: "Progress bar" },
    syntax: [
      "bar [\"<имя>\"] [value <значение>] [max <значение>] [direction left_to_right|right_to_left|bottom_to_top|top_to_bottom]",
      "    [size <W>x<H>] [color #заполнение] [background #цвет|\"картинка\"|none] [fill \"картинка\"] [texture <W>x<H>] [text \"<надпись>\"] [shadow]"
    ],
    desc: {
      ru: "Горизонтальная или вертикальная полоса прогресса (по умолчанию `80x8`, `left_to_right`).\n• Все направления (`direction`) и синонимы: `left_to_right` (`right`, `horizontal`), `right_to_left` (`left`), `bottom_to_top` (`up`, `vertical`), `top_to_bottom` (`down`).\n• `value` / `max`: число, `{переменная}` или клиентский источник (`health`, `max_health`, `absorption`, `food`, `saturation`, `armor`, `air`, `max_air`, `xp`, `level`). Без `max` подбирается автоматически (`health` → `max_health`, `food` → `20`, `air` → `max_air`, иначе `100`).\n• Оформление: `color #RRGGBB`, `background #AARRGGBB | \"картинка\" | none`, `fill \"картинка\"` (обрезается по прогрессу), `texture WxH`, `text \"...\"` (поддерживает `%value%`, `%max%`, `%percent%`), `shadow`. `progress` — синоним `bar`. Работает и в HUD, и в `open screen`.",
      en: "Horizontal or vertical progress bar (default `80x8`, `left_to_right`).\n• All `direction` values & aliases: `left_to_right` (`right`, `horizontal`), `right_to_left` (`left`), `bottom_to_top` (`up`, `vertical`), `top_to_bottom` (`down`).\n• `value` / `max`: number, `{variable}` or live client source (`health`, `max_health`, `absorption`, `food`, `saturation`, `armor`, `air`, `max_air`, `xp`, `level`). Auto `max`: `health` → `max_health`, `food` → `20`, `air` → `max_air`, else `100`.\n• Styling: `color #RRGGBB`, `background #AARRGGBB | \"image\" | none`, `fill \"image\"` (cropped to progress), `texture WxH`, `text \"...\"` (`%value%`, `%max%`, `%percent%`), `shadow`. `progress` is an alias of `bar`. Works in both HUD and `open screen`."
    },
    example: `# Полоса здоровья с текстурой заполнения и текстом
bar "hp" value health size 80x8 anchor bottom at -51, -28 fill "example:textures/gui/bar_fill_hp.png" background #A0000000 text "%value%/%max%"

# Вертикальная полоса воздуха справа
bar "air" value air direction bottom_to_top size 6x40 anchor right at -8, 0 color #55AAFF

# Полоса прогресса из переменной
bar "mana" value {mana::%player%} max 100 direction left_to_right size 90x10 anchor top_left at 6, 20 color #3B82F6 text "%percent%%"`,
    since: "0.1.33", related: ["hud-bar-sources","hud-set-bar","hud-layer","hud-create"]
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
    example: `# Живые источники клиента (обновляются каждый кадр без сетевых пакетов)
bar "food" value food direction right_to_left size 80x8 anchor bottom at 51, -28 color #F59E0B text "%percent%%"
bar "xp_bar" value xp max 1 size 160x5 anchor bottom at 0, -20 color #22C55E
text "&c❤ %health%/%max_health%  &7🛡 %armor%  &aLVL %level%" anchor top_left at 6, 6 shadow`,
    since: "0.1.33", related: ["hud-bar","hud-set-text"]
  },
  {
    id: "hud-hotbar", category: "hud",
    name: { ru: "Свой хотбар", en: "Custom hotbar" },
    syntax: ["hotbar [at <X> <Y>] [anchor <точка>] [size <W>x<H>] [spacing <N>] [color #выделение] [no frame] [id <имя>] [layer <N>]"],
    desc: {
      ru: "Ряд из 9 слотов с настоящими предметами хотбара игрока; выбранный слот обводится цветом `color`. По умолчанию — внизу по центру, как ванильный. `spacing` — шаг между слотами (по умолчанию размер слота + 2), `size` — размер слота (по умолчанию 18). С `id hb` слоты получают имена `hb1`…`hb9`. Ванильный хотбар прячется отдельно: `hide vanilla hotbar`.",
      en: "A row of 9 slots showing the player's real hotbar items; the selected slot is outlined in `color`. By default it sits at the bottom centre like the vanilla one. `spacing` is the step between slots (slot size + 2 by default), `size` is the slot size (18 by default). With `id hb` the slots are named `hb1`…`hb9`. Hide the vanilla hotbar separately: `hide vanilla hotbar`."
    },
    example: `# Заменить ванильный хотбар вертикальным/сдвинутым кастомным
on join or load:
	hide vanilla hotbar for player
	create hud "custom_hb" for player:
		hotbar anchor bottom at 0, -4 spacing 22 size 20x20 color #55FF55 id hb`,
    since: "0.1.33", related: ["hud-slot","hud-vanilla","hud-create"]
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
    example: `# Все 4 режима слота: предмет, хотбар, картинка, текст
slot diamond 5 at 6, 6 anchor top_left
slot hotbar 1 at 28, 6 anchor top_left size 22x22
slot image "example:textures/gui/coin.png" at 54, 6 anchor top_left no frame
slot text "Q" at 76, 6 anchor top_left color #FFD700`,
    since: "0.1.33", related: ["hud-hotbar","screen-slot","hud-create"]
  },
  {
    id: "hud-layer", category: "hud",
    name: { ru: "Слои (layer) и имена (id)", en: "Layers (layer) and names (id)" },
    syntax: [
      "layer <N>",
      "id <имя>"
    ],
    desc: {
      ru: "У любого элемента HUD и экрана: `layer` — порядок отрисовки (больше = выше, по умолчанию 0; при равных слоях — по порядку в скрипте). Элементы с отрицательным слоем в HUD рисуются под ванильным HUD. Предметы в слотах тоже подчиняются слоям: картинка со слоем выше перекроет предмет. `id` — имя элемента для `set value/text`, `show/hide/remove hud` (у `bar \"имя\"` имя задаётся сразу). Элементы без имени управляются через имя группы.",
      en: "On any HUD or screen element: `layer` is the drawing order (higher = on top, 0 by default; equal layers keep script order). HUD elements with a negative layer are drawn below the vanilla HUD. Items in slots follow layers too: an image on a higher layer covers the item. `id` names the element for `set value/text`, `show/hide/remove hud` (`bar \"name\"` sets it directly). Unnamed elements are controlled through the group name."
    },
    example: `# Многослойный индикатор: фон (1) -> полоса (2) -> рамка (3) -> текст (4)
bar "hp" value health size 80x8 anchor bottom at 0, -28 layer 2
image "example:textures/gui/bar_frame.png" size 84x12 anchor bottom at 0, -26 layer 3
text "HP" anchor bottom at 0, -38 layer 4 id hp_label`,
    since: "0.1.33", related: ["hud-create","screen-layout","eff-open-screen"]
  },
  {
    id: "hud-set-bar", category: "hud",
    name: { ru: "Значение и максимум полосы", en: "Bar value and max" },
    syntax: [
      "set value of bar \"<имя>\" [for <цель>] to <значение>",
      "set progress of bar \"<имя>\" [for <цель>] to <значение>",
      "set max of bar \"<имя>\" [for <цель>] to <значение>"
    ],
    desc: {
      ru: "Меняет значение или максимум полосы: число, `{переменная}` или источник (`health`, `food`…). Без `for` — игроку из контекста. Клиенту уходит только изменение, и только если значение действительно поменялось.",
      en: "Changes a bar's value or max: a number, a `{variable}` or a source (`health`, `food`…). Without `for` it targets the context player. Only the change is sent, and only if the value actually changed."
    },
    example: `# Динамическое обновление значения и максимума полосы
set progress of bar "boss_hp" for all players to 75
set max of bar "coins_goal" for player to 200
set value of bar "coins_goal" for player to {coins::%player%}`,
    since: "0.1.33", related: ["hud-bar","hud-bar-sources","hud-set-text"]
  },
  {
    id: "hud-set-text", category: "hud",
    name: { ru: "Текст элемента HUD", en: "HUD element text" },
    syntax: ["set text of hud \"<имя>\" [for <цель>] to \"<текст>\""],
    desc: {
      ru: "Меняет текст у `text`, `slot text` или надпись на полосе `bar`. `%player%` подставляется для игрока из контекста. Если в тексте есть `{переменная}`, элемент и дальше следит за ней. Текст без переменных эту связь снимает.",
      en: "Changes the text of a `text`, a `slot text` or the label on a `bar`. `%player%` is filled in for the context player. If the text contains a `{variable}`, the element keeps following it. Text without a variable drops that link."
    },
    example: `# Обновить текст элемента HUD по его id
set text of hud "coins_text" for player to "&eМонет: &f%{coins::%player%}%"
set text of hud "status" for all players to "&aИдёт раунд 2"`,
    since: "0.1.33", related: ["hud-set-bar","hud-layer"]
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
    example: `# Временно скрыть и вернуть HUD (не удаляя его)
hide hud "stats" from player
show hud "stats" to player

# Удалить группу или полностью очистить кастомный HUD
remove hud "boss" from all players
clear hud for player`,
    since: "0.1.33", related: ["hud-create","hud-vanilla"]
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
      ru: "Прячет или возвращает стандартные элементы интерфейса Minecraft. Все доступные части и синонимы:\n• `hotbar` — панель быстрого доступа\n• `health` (`hearts`, `health_bar`) — сердца здоровья\n• `food` (`hunger`, `food_level`) — голод\n• `armor` — иконки брони\n• `air` — пузырьки воздуха\n• `experience` (`xp`, `experience_bar`, `exp`) — шкала опыта\n• `mount_health` — здоровье транспорта/лошади\n• `jump_bar` — полоса прыжка лошади\n• `crosshair` — прицел по центру\n• `effects` (`potion_effects`, `potions`) — иконки зелий\n• `item_name` — всплывающее название предмета\n• `scoreboard` — правый скорборд\n• `bossbar` (`boss_bar`, `bossbars`) — полосы боссов\n• `hud` (`all`, `everything`) — весь ванильный HUD сразу.\nСостояние восстанавливается при перезаходе и автоматически сбрасывается при `/sc disable` скрипта.",
      en: "Hides or restores vanilla Minecraft HUD overlays. All parts & aliases:\n• `hotbar`\n• `health` (`hearts`, `health_bar`)\n• `food` (`hunger`, `food_level`)\n• `armor`\n• `air`\n• `experience` (`xp`, `experience_bar`, `exp`)\n• `mount_health`\n• `jump_bar`\n• `crosshair`\n• `effects` (`potion_effects`, `potions`)\n• `item_name`\n• `scoreboard`\n• `bossbar` (`boss_bar`, `bossbars`)\n• `hud` (`all`, `everything`) — all vanilla HUD.\nPersists across reconnects and resets automatically on `/sc disable`."
    },
    example: `# Скрыть отдельные части ванильного HUD
hide vanilla hotbar for player
hide vanilla health for player
hide vanilla food for player

# Скрыть всё сразу (например, для катсцены) и вернуть обратно
hide vanilla all for player
wait 5 seconds
show vanilla all for player`,
    since: "0.1.33", related: ["hud-hotbar","hud-show-hide"]
  },
  {
    id: "cmd-help", category: "commands",
    name: { ru: "/sc help", en: "/sc help" },
    syntax: [
      "/sc",
      "/sc help",
      "/scriptcraft ...",
      "/script ..."
    ],
    desc: {
      ru: "Справка. `/scriptcraft`, `/sc` и `/script` — полные синонимы. Все команды мода требуют уровень оператора 2.",
      en: "Help. `/scriptcraft`, `/sc` and `/script` are full aliases. All mod commands need operator level 2."
    },
    example: `/sc
/sc help
/scriptcraft help`,
    since: "0.1.0", related: ["cmd-reload","cmd-journal"]
  },
  {
    id: "cmd-journal", category: "commands",
    name: { ru: "Студия", en: "Studio" },
    syntax: ["F3 + `"],
    desc: {
      ru: "Окно мода. Его открывают и правят оператор или игрок с правом scriptcraft.studio. В конфиге studio.requireOp = false студия открыта всем. Клавиша не видна в «Управлении»: по умолчанию F3 и ` (на русской раскладке это Ё). Пока игрок не вышел с сервера, вкладки и размер окна помнятся; выход сбрасывает их. Главная по центру показывает крупный заголовок ScriptCraft, сменяющиеся фразы и модель скина ElgerElg. Журнал делится на активные ошибки и историю (перезагрузки, старые ошибки, уведомления). Скрипт удаляется из меню «Файл» или с панели, после подтверждения. Закрытие вкладки с несохранённым текстом тоже спрашивает; закрытие всего окна ничего не спрашивает и оставляет вкладки на клиенте. Слева от имени вкладки карандаш, если текст отличается от последней сохранённой версии. Tab вставляет таб и рисуется тонкой палкой. Ctrl+Backspace стирает слово назад, Ctrl+Delete — вперёд, до пробела или отступа. Ctrl и стрелки переходят к соседнему слову. Колесо листает текст и не держит строку курсора. Alt и левая кнопка выделяют прямоугольник: набор и удаление идут в каждую строку. Уведомления растут по высоте текста и показывают серую полоску оставшегося времени. Клиент и сервер должны быть одной версии: протокол сети 12. Совместная правка studio.liveEdit по умолчанию выключена. Если её включить, в одном скрипте видны чужой курсор, выделение и набор, у курсора маленькая голова скина, а ник — при наведении на строку. Сохранение принимается только от последней копии: побеждает последнее сохранение или последняя принятая правка.",
      en: "Mod window for an operator or a player with the scriptcraft.studio permission. studio.requireOp = false opens it for everyone. The key is not listed in Controls: default is F3 and ` (Ё on a Russian layout). Tabs and the window size stay until the player leaves the server; logout clears them. Home centers a large ScriptCraft title, rotating phrases, and ElgerElg's skin model. The journal splits into active errors and a history of reloads, old errors, and notices. A script is deleted from the File menu or the toolbar after a confirmation. Closing a tab with unsaved text also asks; closing the whole window does not ask and keeps the tabs on the client. A pencil sits left of a tab name when the text differs from the last saved copy. Tab inserts a tab drawn as a thin bar. Ctrl+Backspace deletes the word backward, Ctrl+Delete forward, up to a space or indent. Ctrl and the arrow keys jump to the next word. The wheel scrolls the text and does not pin the cursor line. Alt and the left button select a rectangle: typing and deleting apply to every line. Notices grow with the text and show a gray bar of the time left. Client and server must be the same version: the network protocol is 12. Shared editing, studio.liveEdit, is off by default. When it is on, the same script shows the other person's caret, selection and typing, a small skin head at the caret, and the name when the line is hovered. A save is accepted only from the latest copy: the last save or the last accepted edit wins."
    },
    example: `F3 + \``,
    since: "0.2.2", related: ["cmd-reload","cfg-studioLiveEdit","cfg-studioRequireOp"]
  },
  {
    id: "cmd-reload", category: "commands",
    name: { ru: "/sc reload", en: "/sc reload" },
    syntax: [
      "/sc reload",
      "/sc reload config",
      "/sc reload resources",
      "/sc reload <скрипт>"
    ],
    desc: {
      ru: "`/sc reload` перечитывает `config.toml`, `scriptcraft/resources` и все скрипты. `config` и `resources` — не имена скриптов: первое перечитывает только конфиг, второе — только ресурсы. `/sc reload <скрипт>` перезагружает один файл. Аргументы своих команд (`<player>`, `[text]` и остальные) собираются заново. Ошибки — в журнале (F3 + `).",
      en: "`/sc reload` rereads `config.toml`, `scriptcraft/resources` and every script. `config` and `resources` are not script names: the first rereads only the config, the second only resources. `/sc reload <script>` reloads one file. Arguments of script commands (`<player>`, `[text]` and the rest) are built again. Errors are in the journal (F3 + `)."
    },
    example: `# Перезагрузить всё (конфиг, ресурсы, все скрипты)
/sc reload

# Перезагрузить только конфиг или только ресурсы
/sc reload config
/sc reload resources

# Перезагрузить один конкретный скрипт
/sc reload welcome`,
    since: "0.1.0", related: ["cmd-list","cmd-disable"]
  },
  {
    id: "cmd-disable", category: "commands",
    name: { ru: "/sc enable | disable", en: "/sc enable | disable" },
    syntax: [
      "/sc disable <скрипт>",
      "/sc enable <скрипт>"
    ],
    desc: {
      ru: "`disable` выгружает скрипт и переименовывает файл в `-имя.sc`; `enable` возвращает имя и загружает (срабатывает `on load:`). При `disable` срабатывает `on unload:`, а HUD-группы и спрятанные ванильные части этого скрипта убираются у всех игроков. Команды выключенного скрипта остаются в списке до перезапуска и отвечают «выключена».",
      en: "`disable` unloads the script and renames the file to `-name.sc`; `enable` renames it back and loads it (fires `on load:`). `disable` fires `on unload:` and removes this script's HUD groups and hidden vanilla parts for all players. Commands of a disabled script stay registered until restart and reply “disabled”."
    },
    example: `# Выключить скрипт (переименует welcome.sc -> -welcome.sc и вызовет on unload)
/sc disable welcome

# Включить обратно (вызовет on load)
/sc enable welcome`,
    since: "0.1.0", related: ["cmd-reload","event-load","event-unload"]
  },
  {
    id: "cmd-list", category: "commands",
    name: { ru: "/sc list", en: "/sc list" },
    syntax: ["/sc list"],
    desc: {
      ru: "Загруженные и выключенные скрипты, команды, число переменных и ошибок.",
      en: "Loaded and disabled scripts, commands, variable and error counts."
    },
    example: `# Показать список всех скриптов, команд, переменных и ошибок
/sc list`,
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
    example: `# Показать реальные и динамические id всех define block / define item
/sc ids`,
    since: "0.1.7", related: ["struct-define-block","struct-define-item","cmd-give"]
  },
  {
    id: "cmd-give", category: "commands",
    name: { ru: "/sc give", en: "/sc give" },
    syntax: ["/sc give <игроки> <id> [количество]"],
    desc: {
      ru: "Выдать блок или предмет по id скрипта (`example:ruby_block`, короткий `ruby`) или любой обычный предмет. В отличие от ванильной `/give` (она с 0.1.8 тоже принимает `example:ruby_block`), понимает короткий id и подсказывает id скриптов при наборе. Полный инвентарь — предмет падает рядом.",
      en: "Give a block or item by its script id (`example:ruby_block`, short `ruby`) or any regular item. Unlike vanilla `/give` (which also accepts `example:ruby_block` since 0.1.8), it understands short ids and suggests script ids while typing. Full inventory — the item drops nearby."
    },
    example: `# Выдать свой блок или предмет по полному или короткому id
/sc give @s example:ruby_block 4
/sc give @a ruby_pickaxe 1`,
    since: "0.1.7", related: ["cmd-ids","struct-define-block"]
  },
  {
    id: "cmd-effect-commands", category: "commands",
    name: { ru: "Эффект-команды в чате ?...", en: "Chat effect commands ?..." },
    syntax: ["?<строка скрипта>"],
    desc: {
      ru: "Сообщение в чате, начинающееся с токена (по умолчанию `?`), выполняется как одна строка скрипта от имени написавшего. На новом сервере включено. Если в конфиге уже стоит `enableEffectCommands = false`, `?` молчит, пока это не сменить и не сделать `/sc reload config`. На гибриде чат читается и через Bukkit. Право `scriptcraft.effectcommands` проверяет LuckPerms (мод или плагин) и Forge. Блоки (`if`, `loop`, `open screen`) не поддерживаются; пустая цель — сообщение «Нет целей».",
      en: "A chat message starting with the token (`?` by default) runs as one script line on behalf of the sender. On a new server it is on. If the config already has `enableEffectCommands = false`, `?` stays quiet until that is changed and `/sc reload config` is run. On a hybrid, chat is also read through Bukkit. The node `scriptcraft.effectcommands` is checked in LuckPerms (mod or plugin) and Forge. Blocks (`if`, `loop`, `open screen`) are not supported; an empty target replies “No targets”."
    },
    example: `# Быстрое выполнение одной строки скрипта прямо из чата
?heal player
?set time to day
?send title "&aТест" to all players
?give @a[distance=..20] diamond 1`,
    since: "0.1.2", related: ["cfg-enableEffectCommands","cfg-effectCommandToken","cfg-effectCommandsRequireOp"]
  },
  {
    id: "cfg-language", category: "config",
    name: { ru: "general.language", en: "general.language" },
    syntax: [
      "language = \"en\"",
      "language = \"ru\""
    ],
    desc: {
      ru: "Язык сообщений мода, журнала и уведомлений. По умолчанию `\"en\"`. Меняется после `/sc reload config`.",
      en: "Language of mod messages, the journal and notices. Default `\"en\"`. Applies after `/sc reload config`."
    },
    example: `[general]
	language = "ru"   # "ru" или "en"`,
    since: "0.1.0", related: []
  },
  {
    id: "cfg-enableTemporaryVariables", category: "config",
    name: { ru: "general.enableTemporaryVariables", en: "general.enableTemporaryVariables" },
    syntax: ["enableTemporaryVariables = true"],
    desc: {
      ru: "Переменные `{-имя}` живут только в памяти. При `false` сохраняются, как обычные. По умолчанию `true`.",
      en: "`{-name}` variables live in memory only. With `false` they are saved like normal ones. Default `true`."
    },
    example: `[general]
	enableTemporaryVariables = true`,
    since: "0.1.0", related: ["expr-temp-variable"]
  },
  {
    id: "cfg-variablesSaveIntervalSeconds", category: "config",
    name: { ru: "general.variablesSaveIntervalSeconds", en: "general.variablesSaveIntervalSeconds" },
    syntax: ["variablesSaveIntervalSeconds = 300"],
    desc: {
      ru: "Как часто автосохранять переменные, 10–86400 секунд (и при остановке сервера). По умолчанию 300.",
      en: "How often variables are autosaved, 10–86400 s (and on server stop). Default 300."
    },
    example: `[general]
	variablesSaveIntervalSeconds = 300   # автосохранение каждые 5 минут`,
    since: "0.1.0", related: ["expr-variable"]
  },
  {
    id: "cfg-variableBackups", category: "config",
    name: { ru: "general.variableBackups", en: "general.variableBackups" },
    syntax: ["variableBackups = 5"],
    desc: {
      ru: "Сколько резервных копий `variables-ГГГГММДД-ЧЧММСС.json` хранить (делаются при старте сервера), 0–100, 0 — не делать.",
      en: "How many `variables-YYYYMMDD-HHMMSS.json` backups to keep (made at server start), 0–100, 0 = none."
    },
    example: `[general]
	variableBackups = 5   # 0 — не создавать резервные копии`,
    since: "0.1.2", related: ["expr-variable"]
  },
  {
    id: "cfg-maxScriptSteps", category: "config",
    name: { ru: "general.maxScriptSteps", en: "general.maxScriptSteps" },
    syntax: ["maxScriptSteps = 10000"],
    desc: {
      ru: "Максимум действий за один запуск скрипта (100–10 000 000). Защита от зависаний.",
      en: "Max actions per script run (100–10,000,000). Protects against hangs."
    },
    example: `[general]
	maxScriptSteps = 10000   # от 100 до 10000000`,
    since: "0.1.0", related: ["struct-loop"]
  },
  {
    id: "cfg-maxScriptMillis", category: "config",
    name: { ru: "general.maxScriptMillis", en: "general.maxScriptMillis" },
    syntax: ["maxScriptMillis = 50"],
    desc: {
      ru: "Максимум миллисекунд на один запуск скрипта (1–10000).",
      en: "Max milliseconds per script run (1–10000)."
    },
    example: `[general]
	maxScriptMillis = 50   # максимум мс на один запуск (1..10000)`,
    since: "0.1.0", related: ["cfg-maxScriptSteps"]
  },
  {
    id: "cfg-logScriptLoadTimes", category: "config",
    name: { ru: "general.logScriptLoadTimes", en: "general.logScriptLoadTimes" },
    syntax: ["logScriptLoadTimes = false"],
    desc: {
      ru: "Писать в лог время загрузки скриптов.",
      en: "Log how long script loading takes."
    },
    example: `[general]
	logScriptLoadTimes = true`,
    since: "0.1.0", related: []
  },
  {
    id: "cfg-allowCommandEffects", category: "config",
    name: { ru: "general.allowCommandEffects", en: "general.allowCommandEffects" },
    syntax: ["allowCommandEffects = true"],
    desc: {
      ru: "Разрешить `execute console command` и `make ... execute command`. По умолчанию `true`.",
      en: "Allow `execute console command` and `make ... execute command`. Default `true`."
    },
    example: `[general]
	allowCommandEffects = true   # false запретит execute console command`,
    since: "0.1.2", related: ["eff-console-command","eff-player-command"]
  },
  {
    id: "cfg-targetedPlayerRange", category: "config",
    name: { ru: "general.targetedPlayerRange", en: "general.targetedPlayerRange" },
    syntax: ["targetedPlayerRange = 50"],
    desc: {
      ru: "Дальность `targeted player` / `%targeted%`, 1–256 блоков. По умолчанию 50.",
      en: "Range of `targeted player` / `%targeted%`, 1–256 blocks. Default 50."
    },
    example: `[general]
	targetedPlayerRange = 50   # дальность луча в блоках (1..256)`,
    since: "0.1.2", related: ["target-targeted","ph-targeted"]
  },
  {
    id: "cfg-syncResourcesToClients", category: "config",
    name: { ru: "resources.syncResourcesToClients", en: "resources.syncResourcesToClients" },
    syntax: ["syncResourcesToClients = true"],
    desc: {
      ru: "Отправлять `scriptcraft/resources/assets` игрокам через сетевой канал мода (без ресурспака).",
      en: "Send `scriptcraft/resources/assets` to players through the mod's network channel (no resource pack prompt)."
    },
    example: `[resources]
	syncResourcesToClients = true`,
    since: "0.1.2", related: ["cmd-reload"]
  },
  {
    id: "cfg-maxResourceSizeMb", category: "config",
    name: { ru: "resources.maxResourceSizeMb", en: "resources.maxResourceSizeMb" },
    syntax: ["maxResourceSizeMb = 32"],
    desc: {
      ru: "Максимальный общий размер ресурсов, 1–512 МБ. Проверяют и сервер, и клиент.",
      en: "Max total resource size, 1–512 MB. Checked by both server and client."
    },
    example: `[resources]
	maxResourceSizeMb = 32   # лимит в МБ (1..512)`,
    since: "0.1.2", related: ["cfg-syncResourcesToClients"]
  },
  {
    id: "cfg-resourceChunkSizeKb", category: "config",
    name: { ru: "resources.resourceChunkSizeKb", en: "resources.resourceChunkSizeKb" },
    syntax: ["resourceChunkSizeKb = 256"],
    desc: {
      ru: "Размер одного сетевого куска, 16–900 КБ.",
      en: "Size of one network chunk, 16–900 KB."
    },
    example: `[resources]
	resourceChunkSizeKb = 256   # размер чанка в КБ (16..900)`,
    since: "0.1.2", related: ["cfg-syncResourcesToClients"]
  },
  {
    id: "cfg-enableEffectCommands", category: "config",
    name: { ru: "effectCommands.enableEffectCommands", en: "effectCommands.enableEffectCommands" },
    syntax: ["enableEffectCommands = false"],
    desc: {
      ru: "Включить эффект-команды в чате (`?heal player`). На новом сервере включено. Уже записанный конфиг со значением `false` сам не меняется.",
      en: "Enable chat effect commands (`?heal player`). On for a new server. An existing config that says `false` is not changed by itself."
    },
    example: `[effectCommands]
	enableEffectCommands = true`,
    since: "0.1.2", related: ["cmd-effect-commands"]
  },
  {
    id: "cfg-effectCommandToken", category: "config",
    name: { ru: "effectCommands.effectCommandToken", en: "effectCommands.effectCommandToken" },
    syntax: ["effectCommandToken = \"?\""],
    desc: {
      ru: "С чего начинается эффект-команда: до 8 символов, не `/`.",
      en: "Prefix of effect commands: up to 8 characters, not `/`."
    },
    example: `[effectCommands]
	effectCommandToken = "?"   # любой префикс до 8 символов, кроме "/"`,
    since: "0.1.2", related: ["cmd-effect-commands"]
  },
  {
    id: "cfg-effectCommandsRequireOp", category: "config",
    name: { ru: "effectCommands.effectCommandsRequireOp", en: "effectCommands.effectCommandsRequireOp" },
    syntax: ["effectCommandsRequireOp = true"],
    desc: {
      ru: "Без мода прав эффект-команды только у операторов (уровень 2). `false` — у всех игроков (опасно).",
      en: "Without a permissions mod, effect commands are for operators (level 2) only. `false` = every player (dangerous)."
    },
    example: `[effectCommands]
	effectCommandsRequireOp = true`,
    since: "0.1.2", related: ["cmd-effect-commands"]
  },
  {
    id: "cfg-studioLiveEdit", category: "config",
    name: { ru: "studio.liveEdit", en: "studio.liveEdit" },
    syntax: ["liveEdit = false"],
    desc: {
      ru: "Совместная правка студии. По умолчанию `false`: чужие курсоры не видны. `true` — в одном скрипте видны курсор, выделение и набор других людей с доступом к студии, у курсора маленькая голова скина, ник при наведении на строку. Сохранение принимается только от последней копии: побеждает последнее сохранение или последняя принятая правка. Уже записанный конфиг сам не включает этот ключ: поставьте `true` и выполните `/sc reload config`. Клиент и сервер оба должны быть 0.4.7.",
      en: "Shared studio editing. Default `false`: other carets stay hidden. `true` shows the caret, selection and typing of other people who can open the studio, a small skin head at the caret, and the name when the line is hovered. A save is accepted only from the latest copy: the last save or the last accepted edit wins. An existing config does not turn this on by itself: set `true` and run `/sc reload config`. Client and server both need 0.4.7."
    },
    example: `[studio]
	liveEdit = true   # совместное редактирование в реальном времени`,
    since: "0.4.7", related: ["cmd-journal","cfg-studioRequireOp"]
  },
  {
    id: "cfg-studioRequireOp", category: "config",
    name: { ru: "studio.requireOp", en: "studio.requireOp" },
    syntax: ["requireOp = true"],
    desc: {
      ru: "Кто открывает и правит студию. Узел права — `scriptcraft.studio` (его видно в LuckPerms после `/sc reload`, мод или плагин). `true` (по умолчанию): операторы и те, кому выдано это право. `false`: любой игрок. Уже записанный конфиг получает ключ при следующей загрузке, пока его нет — студия остаётся у операторов.",
      en: "Who may open and edit the studio. The node is `scriptcraft.studio` (LuckPerms lists it after `/sc reload`, mod or plugin). `true` (default): operators and anyone granted the node. `false`: every player. An existing config gains the key on the next load; until then the studio stays with operators."
    },
    example: `[studio]
	requireOp = true   # true: OP или право scriptcraft.studio; false: все`,
    since: "0.4.7", related: ["cmd-journal","cfg-studioLiveEdit"]
  },
  {
    id: "event-kill", category: "events",
    name: { ru: "Убийство сущности / игрока", en: "Entity / player kill" },
    syntax: [
      "on kill:",
      "on entity death:"
    ],
    desc: {
      ru: "Срабатывает, когда игрок убивает моба или другого игрока. Плейсхолдеры: `%victim%` — ник убитого игрока или id моба (`minecraft:zombie`, `minecraft:creeper`), `%attacker%` — убийца, `%cause%` — причина смерти.",
      en: "Fires when a player kills a mob or another player. Placeholders: `%victim%` — killed player name or mob id (`minecraft:zombie`, `minecraft:creeper`), `%attacker%` — killer, `%cause%` — cause."
    },
    example: `# Награда за убийство мобов
on kill:
	if %victim% is "minecraft:zombie" or "minecraft:skeleton":
		add 5 to {coins::%player%}
		send action bar "&a+5 монет за %victim%" to player
	else if %victim% is "minecraft:ender_dragon":
		broadcast "&5&l%player% победил Дракона Края!"`,
    since: "0.5.3", related: ["event-attack","event-death"]
  },
  {
    id: "event-attack", category: "events",
    name: { ru: "Атака сущности", en: "Attack entity" },
    syntax: [
      "on attack:",
      "on hit:"
    ],
    desc: {
      ru: "Срабатывает при ударе игрока по мобу или другому игроку. Можно отменить (`cancel event`). Плейсхолдеры: `%victim%` (id моба или ник игрока), `%attacker%`.",
      en: "Fires when a player hits a mob or another player. Cancelable (`cancel event`). Placeholders: `%victim%` (mob id or player name), `%attacker%`."
    },
    example: `# Защита мирных жителей от ударов
on attack:
	if %victim% is "minecraft:villager" or "minecraft:iron_golem":
		cancel event
		send "&cЖителей бить нельзя!" to player

# Огненный меч поджигает и добавляет частицы
on attack:
	if player is holding diamond_sword:
		spawn particle crit at player count 10 speed 0.1`,
    since: "0.5.3", related: ["event-kill","event-damage"]
  },
  {
    id: "event-shoot", category: "events",
    name: { ru: "Выстрел из лука", en: "Bow shoot" },
    syntax: [
      "on shoot:",
      "on bow shoot:"
    ],
    desc: {
      ru: "Срабатывает при выстреле из лука. Можно отменить. Плейсхолдер: `%projectile%`.",
      en: "Fires when shooting a bow. Cancelable. Placeholder: `%projectile%`."
    },
    example: `# Огненные частицы при выстреле из лука
on shoot:
	spawn particle flame at player count 12 speed 0.05
	play sound entity.blaze.shoot to player with volume 0.5 pitch 1.4

# Запрет стрельбы в мирной зоне
on shoot:
	if {-peaceful::%player%} is true:
		cancel event
		send "&cСтрельба здесь запрещена!" to player`,
    since: "0.5.3", related: ["event-projectile-hit"]
  },
  {
    id: "event-projectile-hit", category: "events",
    name: { ru: "Попадание снаряда", en: "Projectile hit" },
    syntax: ["on projectile hit:"],
    desc: {
      ru: "Срабатывает при попадании снаряда игрока в блок или сущность. Плейсхолдеры: `%projectile%`, `%victim%`, `%block%`, `%x%`, `%y%`, `%z%`.",
      en: "Fires when a player's projectile hits a block or entity. Placeholders: `%projectile%`, `%victim%`, `%block%`, `%x%`, `%y%`, `%z%`."
    },
    example: `# Взрывные стрелы
on projectile hit:
	if %projectile% is "minecraft:arrow":
		create safe explosion at %x% %y% %z% with power 2
		spawn particle flame at %x% %y% %z% count 20 speed 0.1

# Телепорт по попаданию снежка
on projectile hit:
	if %projectile% is "minecraft:snowball":
		teleport player to %x% %y% %z%`,
    since: "0.5.3", related: ["event-shoot","eff-explosion"]
  },
  {
    id: "event-consume", category: "events",
    name: { ru: "Употребление еды / зелья", en: "Consume item" },
    syntax: [
      "on consume:",
      "on eat:",
      "on drink:"
    ],
    desc: {
      ru: "Срабатывает, когда игрок доедает еду или допивает зелье. Плейсхолдер: `%item%`.",
      en: "Fires when a player finishes eating or drinking. Placeholder: `%item%`."
    },
    example: `# Бонус после еды
on consume:
	if %item% is "minecraft:golden_apple":
		send "&6Золотое яблоко дало ускорение!" to player
		give player effect speed for 15 seconds level 2
	else if %item% is "example:energy_drink":
		give player effect haste for 30 seconds level 1`,
    since: "0.5.3", related: ["event-craft"]
  },
  {
    id: "event-craft", category: "events",
    name: { ru: "Крафт предмета", en: "Craft item" },
    syntax: ["on craft:"],
    desc: {
      ru: "Срабатывает при крафте предмета. Плейсхолдеры: `%item%`, `%amount%`.",
      en: "Fires when a player crafts an item. Placeholders: `%item%`, `%amount%`."
    },
    example: `# Опыт и уведомление за крафт
on craft:
	give 5 xp to player
	if %item% is "minecraft:diamond_sword":
		broadcast "&b%player% скрафтил алмазный меч!"`,
    since: "0.5.3", related: ["event-consume"]
  },
  {
    id: "event-fish", category: "events",
    name: { ru: "Рыбалка", en: "Fishing" },
    syntax: ["on fish:"],
    desc: {
      ru: "Срабатывает при вылавливании предмета удочкой. Можно отменить. Плейсхолдер: `%item%`.",
      en: "Fires when catching an item with a fishing rod. Cancelable. Placeholder: `%item%`."
    },
    example: `# Бонус при удачной рыбалке
on fish:
	add 3 to {coins::%player%}
	send action bar "&bУлов: %item% &a(+3 монеты)" to player
	chance of 10%:
		give player emerald 1
		send "&aБонусный изумруд за удачную рыбалку!" to player`,
    since: "0.5.3", related: []
  },
  {
    id: "event-sneak", category: "events",
    name: { ru: "Приседание (Shift)", en: "Toggle sneak" },
    syntax: [
      "on sneak:",
      "on toggle sneak:"
    ],
    desc: {
      ru: "Срабатывает при нажатии/отпускании Shift. Можно отменить (`cancel event`). Плейсхолдер: `%sneaking%` (`true` / `false`).",
      en: "Fires when sneaking toggles. Cancelable (`cancel event`). Placeholder: `%sneaking%` (`true` / `false`)."
    },
    example: `# Невидимость при приседании с кольцом
on sneak:
	if %sneaking% is "true":
		if player is holding gold_nugget:
			give player effect invisibility for 5 seconds
	else:
		remove effect invisibility from player`,
    since: "0.5.3", related: ["event-sprint","event-jump"]
  },
  {
    id: "event-sprint", category: "events",
    name: { ru: "Бег (Sprint)", en: "Toggle sprint" },
    syntax: [
      "on sprint:",
      "on toggle sprint:"
    ],
    desc: {
      ru: "Срабатывает при начале или окончании бега. Можно отменить (`cancel event`). Плейсхолдер: `%sprinting%`.",
      en: "Fires when sprinting toggles. Cancelable (`cancel event`). Placeholder: `%sprinting%`."
    },
    example: `# Частицы под ногами при беге
on sprint:
	if %sprinting% is "true":
		spawn particle cloud at player count 6 speed 0.02

# Запрет бега, если игрок ранен
on sprint:
	if %sprinting% is "true" and health of player <= 4:
		cancel event
		send action bar "&cСлишком мало здоровья для бега!" to player`,
    since: "0.5.3", related: ["event-sneak","event-jump"]
  },
  {
    id: "event-jump", category: "events",
    name: { ru: "Прыжок", en: "Player jump" },
    syntax: ["on jump:"],
    desc: {
      ru: "Срабатывает при прыжке игрока. Можно отменить (`cancel event`).",
      en: "Fires when a player jumps. Cancelable (`cancel event`)."
    },
    example: `# Супер-прыжок на арене
on jump:
	if {-superjump::%player%} is true:
		push player upwards with force 1.1
		spawn particle cloud at player count 8

# Запрет прыжков при заморозке
on jump:
	if {-frozen::%player%} is true:
		cancel event`,
    since: "0.5.3", related: ["eff-push", "event-move"]
  },
  {
    id: "event-move", category: "events",
    name: { ru: "Движение игрока (on move)", en: "Player move (on move)" },
    syntax: ["on move:"],
    desc: {
      ru: "Срабатывает в каждом тике, когда изменились координаты игрока (`x`, `y`, `z`). Можно отменить (`cancel event`) — игрок вернётся на прежнюю позицию с обнулением скорости. Плейсхолдеры: `%x%`, `%y%`, `%z%`, `%from_x%`, `%from_y%`, `%from_z%`, `%yaw%`, `%pitch%`, `%distance%`, `%moved%`, `%turned%`.",
      en: "Fires each tick when the player's position (`x`, `y`, `z`) changes. Cancelable (`cancel event` reverts position and clears velocity). Placeholders: `%x%`, `%y%`, `%z%`, `%from_x%`, `%from_y%`, `%from_z%`, `%yaw%`, `%pitch%`, `%distance%`, `%moved%`, `%turned%`."
    },
    example: `# Заморозка игрока на месте (можно осматриваться, но нельзя ходить)
on move:
	if {-frozen::%player%} is true:
		cancel event
		send action bar "&bВы заморожены!" to player`,
    since: "0.8.8", related: ["event-turning-around", "event-move-or-turning-around", "event-jump"]
  },
  {
    id: "event-turning-around", category: "events",
    name: { ru: "Поворот камеры (on turning around)", en: "Turning around (on turning around)" },
    syntax: ["on turning around:", "on turn around:", "on turn:", "on look:"],
    desc: {
      ru: "Срабатывает, когда игрок повернул голову/камеру (`yaw` или `pitch`). Можно отменить (`cancel event`) — углы обзора вернутся к прежним. Плейсхолдеры: `%yaw%`, `%pitch%`, `%from_yaw%`, `%from_pitch%`, `%x%`, `%y%`, `%z%`, `%moved%`, `%turned%`.",
      en: "Fires when the player rotates their camera (`yaw` or `pitch`). Cancelable (`cancel event` restores previous rotation). Placeholders: `%yaw%`, `%pitch%`, `%from_yaw%`, `%from_pitch%`, `%x%`, `%y%`, `%z%`, `%moved%`, `%turned%`."
    },
    example: `# Блокировка поворота камеры во время кат-сцены
on turning around:
	if {-cutscene::%player%} is true:
		cancel event`,
    since: "0.8.8", related: ["event-move", "event-move-or-turning-around"]
  },
  {
    id: "event-move-or-turning-around", category: "events",
    name: { ru: "Движение или поворот (on move or turning around)", en: "Move or turning around" },
    syntax: ["on move or turning around:"],
    desc: {
      ru: "Срабатывает **один раз за тик**, если игрок сдвинулся с места и/или повернул камеру. Можно отменить (`cancel event` откатывает то, что изменилось — позицию, поворот или оба). Удобно для полной заморозки, отмены телепортации при движении или снятия AFK. Плейсхолдеры: `%moved%`, `%turned%`, `%distance%`, `%x%`, `%y%`, `%z%`, `%yaw%`, `%pitch%`.",
      en: "Fires **once per tick** when the player moves and/or rotates the camera. Cancelable (`cancel event` reverts whichever changed). Great for full freeze, cancelling teleport warmup, or clearing AFK. Placeholders: `%moved%`, `%turned%`, `%distance%`, `%x%`, `%y%`, `%z%`, `%yaw%`, `%pitch%`."
    },
    example: `# Отмена телепортации на спавн, если игрок шевельнулся или повернулся
on move or turning around:
	if {-tp-wait::%player%} is true:
		delete {-tp-wait::%player%}
		send "&cТелепортация отменена — вы пошевелились!" to player

# Полный стан (нельзя ни ходить, ни крутить камерой)
on move or turning around:
	if {-stunned::%player%} is true:
		cancel event`,
    since: "0.8.8", related: ["event-move", "event-turning-around"]
  },
  {
    id: "event-command", category: "events",
    name: { ru: "Перехват команды", en: "Command event" },
    syntax: ["on command:"],
    desc: {
      ru: "Срабатывает перед выполнением любой команды игроком. Можно отменить (`cancel event`). Плейсхолдеры: `%command%`, `%args%`.",
      en: "Fires before any player command runs. Cancelable. Placeholders: `%command%`, `%args%`."
    },
    example: `# Скрытие служебных команд от обычных игроков
on command:
	if %command% is "plugins" or "pl" or "ver":
		if player is not op:
			cancel event
			send "&cНеизвестная команда." to player

# Логирование всех команд администраторов
on command:
	if player is op:
		log "[CMD] %player%: /%command% %args%"`,
    since: "0.5.3", related: []
  },
  {
    id: "event-item-break", category: "events",
    name: { ru: "Поломка инструмента", en: "Item break" },
    syntax: ["on item break:"],
    desc: {
      ru: "Срабатывает, когда ломается инструмент или броня игрока. Плейсхолдер: `%item%`.",
      en: "Fires when a player's tool or armor breaks. Placeholder: `%item%`."
    },
    example: `# Предупреждение и звук при поломке инструмента
on item break:
	play sound entity.item.break to player with volume 1 pitch 0.8
	send "&cВаш предмет &f%item% &cсломался!" to player`,
    since: "0.5.3", related: []
  },
  {
    id: "expr-clicked", category: "expressions",
    name: { ru: "Кликнутая сущность / игрок / блок", en: "Clicked entity / player / block" },
    syntax: [
      "clicked entity",
      "clicked player",
      "clicked block"
    ],
    desc: {
      ru: "Внутри `on right click:` возвращает id сущности, ник игрока или id блока, по которому кликнули. Если клик был не по сущности/игроку — значение не задано (`is not set`).",
      en: "Inside `on right click:`, returns the clicked entity id, player name, or block id. Unset if not applicable."
    },
    example: `on right click:
	if clicked player is set:
		send "&eВы кликнули по игроку: %clicked_player%" to player
	else if clicked entity is set:
		send "&7Клик по сущности: %clicked_entity%" to player
	else if clicked block is set:
		send "&7Клик по блоку: %block%" to player`,
    since: "0.5.3", related: []
  },
  {
    id: "expr-math", category: "expressions",
    name: { ru: "Математика и случайный элемент", en: "Math & random element" },
    syntax: [
      "<a> + <b>",
      "<a> - <b>",
      "<a> * <b>",
      "<a> / <b>",
      "<a> % <b>",
      "<a> ^ <b>",
      "round(<x>)",
      "floor(<x>)",
      "ceil(<x>)",
      "abs(<x>)",
      "sqrt(<x>)",
      "min(<a>, <b>)",
      "max(<a>, <b>)",
      "clamp(<v>, <lo>, <hi>)",
      "random element out of {list::*}"
    ],
    desc: {
      ru: "Арифметика с приоритетом операций (`+`, `-`, `*`, `/`, `%` остаток, `^` степень) и скобками `( ... )`. Встроенные функции: `round(x)` (округлить), `floor(x)` (вниз), `ceil(x)` (вверх), `abs(x)` (модуль), `sqrt(x)` (корень), `min(a, b)`, `max(a, b)`, `clamp(v, lo, hi)` (ограничить в диапазоне). Также выбор случайного значения из списка: `random element out of {list::*}`.",
      en: "Arithmetic with operator precedence (`+`, `-`, `*`, `/`, `%`, `^`) and parentheses `( ... )`. Built-in functions: `round(x)`, `floor(x)`, `ceil(x)`, `abs(x)`, `sqrt(x)`, `min(a, b)`, `max(a, b)`, `clamp(v, lo, hi)`. Plus `random element out of {list::*}`."
    },
    example: `# Формулы с приоритетом и функциями
set {-dmg} to round((health of player + 4) * 1.5)
set {-clamped} to clamp({-dmg}, 1, 20)
set {-dist} to sqrt(3 ^ 2 + 4 ^ 2)

# Случайная награда из списка
set {-prize} to random element out of {rewards::*}
give player {-prize} 1`,
    since: "0.5.3", related: []
  },
  {
    id: "struct-loop-while", category: "structures",
    name: { ru: "Циклы loop N times и while", en: "loop N times & while loops" },
    syntax: [
      "loop <число> times:",
      "while <условие>:"
    ],
    desc: {
      ru: "`loop N times:` повторяет блок N раз (текущий шаг в `%loop-number%`). `while <условие>:` выполняется, пока условие истинно (ограничено бюджетом шагов).",
      en: "`loop N times:` repeats N times (`%loop-number%`). `while <condition>:` repeats while true (bounded by step budget)."
    },
    example: `# Повторить действие N раз (индекс в %loop-number%)
command /countdown:
	loop 5 times:
		send "&eШаг #%loop-number%" to player

# Цикл while с условием (ограничен maxScriptSteps)
command /fill_list:
	set {-i} to 1
	while {-i} <= 5:
		add "Пункт %{-i}%" to {-items::*}
		add 1 to {-i}`,
    since: "0.5.3", related: []
  },
  {
    id: "eff-particle", category: "effects",
    name: { ru: "Частицы", en: "Spawn particles" },
    syntax: ["spawn particle <id> at <цель|локация|x y z> [count <N>] [speed <S>]"],
    desc: {
      ru: "Создаёт облако частиц в точке, у игрока или на локации. По умолчанию `count 10`, `speed 0.05`. Популярные id частиц: `flame`, `heart`, `crit`, `enchanted_hit`, `smoke`, `large_smoke`, `portal`, `enchant`, `soul_fire_flame`, `explosion`, `cloud`, `happy_villager`, `angry_villager`, `end_rod`, `snowflake`, `cherry_leaves`, `totem_of_undying`, `note`, `dust`, `sonic_boom`. Для цветной пыли, `offset` и колец см. «Цветные партиклы, разброс и кольца».",
      en: "Spawns particles at a target, location or coordinates. Default `count 10`, `speed 0.05`. Common particle ids: `flame`, `heart`, `crit`, `enchanted_hit`, `smoke`, `large_smoke`, `portal`, `enchant`, `soul_fire_flame`, `explosion`, `cloud`, `happy_villager`, `angry_villager`, `end_rod`, `snowflake`, `cherry_leaves`, `totem_of_undying`, `note`, `dust`, `sonic_boom`. For hex-colored dust, offsets and rings see “Colored particles, offsets & rings”."
    },
    example: `# Разные эффекты частиц у игрока и на блоке
spawn particle flame at player count 25 speed 0.08
spawn particle heart at location of targeted block count 10
spawn particle totem_of_undying at player ~~vector(0, 1, 0) count 40 speed 0.25`,
    since: "0.5.3", related: ["eff-playsound-at"]
  },
  {
    id: "eff-scoreboard", category: "effects",
    name: { ru: "Скорборд (боковая панель)", en: "Scoreboard sidebar" },
    syntax: [
      "set scoreboard title \"...\" [for <цель>]",
      "set scoreboard line <N> to \"...\" [for <цель>]",
      "clear scoreboard [of <цель>]"
    ],
    desc: {
      ru: "Персональный сайдбар-скорборд для каждого игрока с поддержкой цветов `&` и плейсхолдеров.",
      en: "Per-player sidebar scoreboard with `&` colors and placeholders."
    },
    example: `# Персональная боковая панель при входе
on join or load:
	set scoreboard title "&6&lMyServer" for player
	set scoreboard line 4 to "&7" for player
	set scoreboard line 3 to "&7Игрок: &f%player%" for player
	set scoreboard line 2 to "&7Монеты: &e%{coins::%player%}%" for player
	set scoreboard line 1 to "&7Онлайн: &a%online%" for player

# Убрать скорборд
command /hidesb:
	clear scoreboard of player`,
    since: "0.5.3", related: []
  },
  {
    id: "eff-push", category: "effects",
    name: { ru: "Толчок сущности/игрока (push)", en: "Push entity / player" },
    syntax: [
      "push <цель-сущность> away from|from|towards|in direction of <источник> [with force <N>]",
      "push <цель-сущность> upwards|downwards|forwards|backwards|north|south|east|west [with force <N>]"
    ],
    desc: {
      ru: "Добавляет импульс игроку или любой сущности (`player`, `targeted player`, `targeted entity`, `entities in radius N of player`, `@e[...]`). Поддерживает два режима:\n• **Относительно источника** (игрока, другой сущности или локации): `away from` / `from` (оттолкнуть от источника), `towards` (притянуть к источнику), `in direction of` (толкнуть по направлению взгляда источника).\n• **По направлению**: `upwards` (`up`), `downwards` (`down`), `forwards` (`forward`), `backwards` (`backward`), `north`, `south`, `east`, `west`.\nСила задаётся через `with force <N>` (или `with speed <N>`, `with <N>`).",
      en: "Adds a velocity impulse to a player or any entity (`player`, `targeted player`, `targeted entity`, `entities in radius N of player`, `@e[...]`). Supports two modes:\n• **Relative to a source** (player, entity, or location): `away from` / `from` (knock back from source), `towards` (pull toward source), `in direction of` (push along source's look vector).\n• **Directional**: `upwards` (`up`), `downwards` (`down`), `forwards` (`forward`), `backwards` (`backward`), `north`, `south`, `east`, `west`.\nStrength via `with force <N>` (or `with speed <N>`, `with <N>`)."
    },
    example: `# Оттолкнуть цель в прицеле (моба или игрока) от игрока
push targeted entity away from player with force 1.5
push targeted player from player with force 2

# Оттолкнуть игрока от сущности или всех мобов вокруг себя
push player away from targeted entity with force 1.2
push entities in radius 5 of player away from player with force 1.5

# Притянуть к себе или толкнуть по взгляду игрока
push targeted entity towards player with force 1.2
push targeted entity in direction of player with force 2

# Классические направления
push player upwards with force 1.2
push targeted entity upwards with force 1.4`,
    since: "0.8.8", related: ["event-jump", "event-move"]
  },
  {
    id: "eff-spawn-entity", category: "effects",
    name: { ru: "Спавн сущности (с NBT и экипировкой)", en: "Spawn entity (NBT & gear)" },
    syntax: ["spawn entity \"<id>\" at <цель|локация|x y z> [named \"...\"] [with health <N>] [with nbt \"{...}\"] [with weapon|helmet|chestplate|leggings|boots <предмет>]"],
    desc: {
      ru: "Спавнит любую ванильную или модовую сущность (`minecraft:zombie`, `minecraft:skeleton`, `minecraft:villager`, `minecraft:iron_golem`, `minecraft:wolf`, `minecraft:armor_stand` и т.д.). Опции: `named \"...\"` (имя с цветами `&`), `with health <N>` (максимальное и текущее здоровье), `with nbt \"{...}\"` (любые SNBT-теги), экипировка: `with weapon <предмет>`, `with helmet <предмет>`, `with chestplate <предмет>`, `with leggings <предмет>`, `with boots <предмет>`.",
      en: "Spawns any vanilla or modded entity (`minecraft:zombie`, `minecraft:skeleton`, `minecraft:villager`, `minecraft:iron_golem`, `minecraft:wolf`, `minecraft:armor_stand`, etc.). Options: `named \"...\"`, `with health <N>`, `with nbt \"{...}\"`, gear: `with weapon`, `with helmet`, `with chestplate`, `with leggings`, `with boots`."
    },
    example: `# Мини-босс в полном снаряжении и со свечением
spawn entity "minecraft:zombie" at player named "&c&lКороль зомби" with health 120 with weapon diamond_sword with helmet golden_helmet with chestplate iron_chestplate with nbt "{Glowing:1b}"

# Неподвижный NPC-торговец в точке прицела
spawn entity "minecraft:villager" at location of targeted block named "&6Торговец" with nbt "{NoAI:1b,Invulnerable:1b}"`,
    since: "0.5.3", related: []
  },
  {
    id: "eff-lightning-fake", category: "effects",
    name: { ru: "Декоративная молния (без урона)", en: "Fake / visual lightning" },
    syntax: ["strike fake|effect|visual lightning at <цель|локация|x y z>"],
    desc: {
      ru: "Бьёт декоративной молнией без урона и поджога.",
      en: "Strikes visual-only lightning without damage or fire."
    },
    example: `# Визуальная молния без урона и огня (для эффектов и приветствий)
strike fake lightning at player
strike visual lightning at location of targeted block`,
    since: "0.5.3", related: []
  },
  {
    id: "eff-explosion", category: "effects",
    name: { ru: "Взрыв (обычный и безопасный)", en: "Explosion (normal & safe)" },
    syntax: ["create [safe] explosion at <цель|локация|x y z> [with power <N>]"],
    desc: {
      ru: "Создаёт взрыв. Слово `safe` отключает разрушение блоков.",
      en: "Creates an explosion. `safe` prevents block destruction."
    },
    example: `# Безопасный взрыв (не ломает блоки) и обычный
create safe explosion at location of targeted block with power 2.5
create explosion at player with power 3`,
    since: "0.5.3", related: []
  },
  {
    id: "eff-playsound-at", category: "effects",
    name: { ru: "Звук в точке / от игрока", en: "Play sound at location / player" },
    syntax: ["play sound <id> at <x y z|цель|локация> [for <слушатели>] [with volume <V> [pitch <P>]]"],
    desc: {
      ru: "Проигрывает звук в координатах, от игрока или от `targeted block`/`targeted entity`. Без `for` слышно всем рядом; с `for <цель>` — только выбранным игрокам. Поддерживаются как ванильные звуки, так и кастомные `.ogg` из `scriptcraft/resources/assets/<ns>/sounds.json`.",
      en: "Plays a sound at coordinates, a player, or a location expression. Without `for`, everyone nearby hears it; with `for <target>`, only chosen players hear it. Supports both vanilla and custom `.ogg` sounds from `scriptcraft/resources/assets/<ns>/sounds.json`."
    },
    example: `# Звук в точке слышен всем вокруг
play sound entity.generic.explode at location of targeted block with volume 1.2 pitch 0.8

# Звук от игрока только выбранным слушателям
play sound entity.experience_orb.pickup at player for all players with volume 1 pitch 1.2

# Кастомный .ogg звук из ресурсов
play sound mypack:boss_roar at player with volume 1.5 pitch 0.9`,
    since: "0.5.3", related: ["eff-stop-sound"]
  },
  {
    id: "screen-input", category: "screen",
    name: { ru: "Поле ввода текста (input)", en: "Text input field (input)" },
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
    since: "0.6.8", related: ["screen-slider","screen-toggle","screen-tooltip","event-gui-change"]
  },
  {
    id: "screen-slider", category: "screen",
    name: { ru: "Слайдер / ползунок (slider)", en: "Slider control (slider)" },
    syntax: ["slider [\"<подпись>\"] id <id> at <x> <y> [size <WxH>] [anchor <A>] from <min> to <max> [value <V>] [step <S>] [color #hex] [tooltip \"...\"] [run ...]"],
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
    since: "0.6.8", related: ["screen-input","screen-toggle","screen-tooltip","event-gui-change"]
  },
  {
    id: "screen-toggle", category: "screen",
    name: { ru: "Тумблер / чекбокс (toggle)", en: "Toggle / checkbox (toggle)" },
    syntax: ["toggle|checkbox [\"<подпись>\"] id <id> at <x> <y> [size <WxH>] [anchor <A>] [value true|false] [checked] [color #hex] [tooltip \"...\"] [run ...]"],
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
    since: "0.6.8", related: ["screen-input","screen-slider","screen-tooltip","event-gui-change"]
  },
  {
    id: "screen-tooltip", category: "screen",
    name: { ru: "Тултипы элементов (tooltip)", en: "Element tooltips (tooltip)" },
    syntax: [
      "... tooltip \"Подсказка\\nВторая строка\"",
      "... hover \"Подсказка\""
    ],
    desc: {
      ru: "Параметр `tooltip \"...\"` (или `hover \"...\"`) работает у любых элементов экрана (`button`, `text`, `image`, `slot`, `bar`, `list`, `input`, `slider`, `toggle`). Поддерживаются цветовые коды `&` и переносы строк `\\n`.",
      en: "The `tooltip \"...\"` (or `hover \"...\"`) option works on all screen elements (`button`, `text`, `image`, `slot`, `bar`, `list`, `input`, `slider`, `toggle`). Supports `&` color codes and `\\n` line breaks."
    },
    example: `command /shop:
	open screen "Магазин":
		button "&6Купить меч" at 0 -15 size 130x20 run "/buy sword" keep open tooltip "&eАлмазный меч\\n&7Цена: &a500 монет\\n&8Нажмите ЛКМ"
		image "minecraft:textures/item/golden_apple.png" at 0 20 size 24x24 texture 16x16 hover "&6Золотое яблоко\\n&7Восстанавливает здоровье"`,
    since: "0.6.8", related: ["screen-input","screen-slider","screen-toggle"]
  },
  {
    id: "event-gui-change", category: "events",
    name: { ru: "Изменение контрола GUI", en: "GUI control change" },
    syntax: [
      "on gui change [\"<id>\"]:",
      "on input [\"<id>\"]:",
      "on slider [\"<id>\"]:",
      "on toggle [\"<id>\"]:"
    ],
    desc: {
      ru: "Срабатывает, когда игрок вводит текст в `input`, двигает `slider` или переключает `toggle` на кастомном экране. Плейсхолдеры: `%element%` (id контрола), `%value%` (новое значение), `%screen%` (заголовок экрана).",
      en: "Fires when a player types into an `input`, moves a `slider`, or clicks a `toggle` on a custom screen. Placeholders: `%element%`, `%value%`, `%screen%`."
    },
    example: `# Общий обработчик всех контролов GUI
on gui change:
	send "&7[%screen%] &e%element% &7= &f%value%" to player

# Отдельные обработчики по id элемента
on input "search":
	set {-query::%player%} to %value%

on slider "volume":
	set {vol::%player%} to %value%

on toggle "music":
	if %value% is "true":
		play sound mypack:theme to player
	else:
		stop all sounds for player`,
    since: "0.6.8", related: ["screen-input","screen-slider","screen-toggle"]
  },
  {
    id: "list-operations", category: "effects",
    name: { ru: "Списки и массивы (sort, reverse, size, join)", en: "Lists & arrays (sort, reverse, size, join)" },
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
    example: `# Сортировка, переворот, размер, поиск и склейка списка
command /listdemo:
	clear {nums::*}
	add 42 to {nums::*}
	add 7 to {nums::*}
	add 19 to {nums::*}
	sort {nums::*} descending
	set {-cnt} to size of {nums::*}
	set {-first} to first element of {nums::*}
	set {-last} to last element of {nums::*}
	set {-joined} to join {nums::*} with ", "
	send "&aВсего %{-cnt}%: &f%{-joined}% &7(макс: %{-first}%, мин: %{-last}%)" to player

# Удаление элемента по ключу и поиск индекса
set {-idx} to index of "Steve" in {queue::*}
remove index {-idx} from {queue::*}`,
    since: "0.6.8", related: ["list-conditions","loop-sorted"]
  },
  {
    id: "list-conditions", category: "conditions",
    name: { ru: "Проверки списков (contains, has key, is empty)", en: "List conditions (contains, has key, is empty)" },
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
    example: `# Проверка значения, ключа и пустоты списка
if {vip::*} contains %player%:
	send "&6Вы в списке VIP!" to player

if {scores::*} does not have key %player%:
	set {scores::%player%} to 0

if {queue::*} is empty:
	send "&7Очередь на арену пуста" to player
else:
	send "&aВ очереди есть игроки!" to player`,
    since: "0.6.8", related: ["list-operations","loop-sorted","cond-text-match"]
  },
  {
    id: "cond-text-match", category: "conditions",
    name: { ru: "Проверки текста (contains, starts with, ends with)", en: "Text conditions (contains, starts with, ends with)" },
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
    example: `# Префикс чата и фильтр слов
on chat:
	if message starts with "!":
		cancel event
		broadcast "&6[Глобал] &f%player%: &e%message%"
	else if message contains "спам" or "чит":
		cancel event
		send "&cЗапрещённое слово!" to player

# Проверка суффикса, пустоты и длины строки
command /nick <text>:
	if arg-1 is empty:
		send "&cНик пустой!" to player
		stop
	set {-len} to length of arg-1
	if {-len} > 16:
		send "&cСлишком длинный ник (%{-len}% > 16)" to player
	else if arg-1 ends with "_bot":
		send "&cНедопустимый суффикс!" to player`,
    since: "0.7.0", related: ["list-conditions"]
  },
  {
    id: "loop-sorted", category: "structures",
    name: { ru: "Цикл по ключам и сортированный цикл", en: "Key/value loop & sorted loop" },
    syntax: [
      "loop {map::*}:",
      "loop {map::*} sorted [ascending|descending]:"
    ],
    desc: {
      ru: "Перебирает все прямые пары ключ/значение `{map::*}`. Внутри доступны `loop-index` (`%loop-index%`) и `loop-value` (`%loop-value%`). С модификатором `sorted descending` (или `ascending`) перебирает элементы по возрастанию/убыванию значений без изменения исходной таблицы — идеально для Топ-10.",
      en: "Iterates all direct key/value entries of `{map::*}` with `loop-index` (`%loop-index%`) and `loop-value` (`%loop-value%`). Adding `sorted descending` (or `ascending`) iterates in sorted value order without mutating the map — ideal for Top-10 leaderboards."
    },
    example: `# Топ-10 богатейших игроков по убыванию
command /top:
	set {-rank} to 0
	send "&6=== ТОП-10 ИГРОКОВ ===" to player
	loop {coins::*} sorted descending:
		add 1 to {-rank}
		send "&e#%{-rank}% &f%loop-index% &7— &a%loop-value% монет" to player
		if {-rank} >= 10:
			stop

# Вывод списка по возрастанию
command /cheapest:
	loop {prices::*} sorted ascending:
		send "&f%loop-index%: &e%loop-value%" to player`,
    since: "0.6.8", related: ["list-operations","list-conditions"]
  },
  {
    id: "eff-stop-sound", category: "effects",
    name: { ru: "Остановка звука и кастомные звуки", en: "Stop sound & custom sounds" },
    syntax: [
      "play sound <ns:sound_id> to <цель> [with volume <V> [pitch <P>]]",
      "stop sound [<id>] for|to <цель>",
      "stop all sounds for|to <цель>"
    ],
    desc: {
      ru: "`play sound` поддерживает кастомные `.ogg` звуки из `scriptcraft/resources/assets/<ns>/sounds/` + `sounds.json` (автоматически синхронизируются клиентам). `stop sound` останавливает конкретный звук или все звуки у игрока.",
      en: "`play sound` supports custom `.ogg` sounds from `scriptcraft/resources/assets/<ns>/sounds/` + `sounds.json` (auto-synced to clients). `stop sound` stops a specific sound or all sounds for the target."
    },
    example: `# Включить кастомный саундтрек и остановить его по команде
command /music:
	play sound mypack:boss_theme to player with volume 0.8 pitch 1.0

command /stopmusic:
	stop sound mypack:boss_theme for player

command /muteall:
	stop all sounds for player`,
    since: "0.6.8", related: ["eff-playsound-at"]
  },
  {
    id: "eff-particle-advanced", category: "effects",
    name: { ru: "Цветные партиклы, разброс и кольца", en: "Colored particles, offsets & rings" },
    syntax: [
      "spawn particle dust at <цель|локация> [count <N>] color #RRGGBB [for <слушатели>]",
      "spawn particle <id> at <цель> count <N> offset <dx> <dy> <dz> speed <S> [for <слушатели>]",
      "spawn particle ring|circle <id> at <цель|локация> radius <R> [points <N>] [color #RRGGBB]"
    ],
    desc: {
      ru: "Расширенные эффекты частиц: цветная пыль `dust` с HEX-цветом `#RRGGBB`, настраиваемый разброс `offset dx dy dz`, показ только выбранным игрокам `for <цель>`, а также готовое кольцо частиц `spawn particle ring`.",
      en: "Advanced particles: hex-colored `dust` (`#RRGGBB`), custom `offset dx dy dz`, per-player visibility (`for <target>`), and instant particle circles (`spawn particle ring`)."
    },
    example: `# Цветное кольцо из пыли вокруг игрока
spawn particle ring dust at player radius 2.5 points 36 color #FF44AA

# Разброс по осям X/Y/Z и показ только самому игроку
spawn particle flame at player count 20 offset 0.5 1.0 0.5 speed 0.04 for player

# Цветная пыль в точке прицела
spawn particle dust at location of targeted block count 30 color #00FFCC`,
    since: "0.6.8", related: ["eff-particle","eff-camera"]
  },
  {
    id: "eff-camera", category: "effects",
    name: { ru: "Эффекты камеры (shake, fade, fov, roll)", en: "Camera effects (shake, fade, fov, roll)" },
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
    example: `# Комбо-эффект удара босса: тряска + красная вспышка + зум + наклон
command /impact:
	shake camera of player with intensity 2.5 for 1.5 seconds
	fade screen of player to #FF2200 in 3 stay 4 out 15 ticks
	set camera fov of player to -15 for 20 ticks
	roll camera of player by 8 for 15 ticks

# Плавное затемнение экрана перед телепортом
command /cinematic_tp:
	fade screen of player to #000000 for 2 seconds
	wait 1 second
	teleport player to world spawn

# Мгновенный сброс всех эффектов камеры
command /camreset:
	reset camera of player`,
    since: "0.6.8", related: ["eff-particle-advanced","eff-playsound-at"]
  },
  {
    id: "cfg-studio-history-profiler", category: "config",
    name: { ru: "История версий и профайлер студии", en: "Script version history & Studio profiler" },
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
  },
  {
    id: "expr-string-ops", category: "expressions",
    name: { ru: "Строковые функции (replace, split, lowercase, substring)", en: "String functions (replace, split, lowercase, substring)" },
    syntax: [
      "replace <что> with <на_что> in {<переменная>}",
      "replace <что> with <на_что> in <выражение>",
      "split <текст> by <разделитель> into {<список>::*}",
      "lowercase [of] <выражение>",
      "uppercase [of] <выражение>",
      "trim [of] <выражение>",
      "substring [of] <выражение> from <начало> [to <конец>]"
    ],
    desc: {
      ru: "Полный набор работы со строками:\n• `replace \"a\" with \"b\" in {-s}` — заменяет все вхождения прямо в переменной (или как выражение в `set {-out} to replace ...`).\n• `split {-s} by \",\" into {-arr::*}` — разбивает текст по разделителю и сохраняет части в список `{-arr::1}`, `{-arr::2}`…\n• `lowercase` / `uppercase` / `trim` — перевод в нижний/верхний регистр и обрезка пробелов.\n• `substring of {-s} from 1 to 5` — вырезает подстроку (нумерация с `1`; без `to` — до конца строки).",
      en: "Complete string manipulation:\n• `replace \"a\" with \"b\" in {-s}` — replaces all occurrences in-place (or as an expression in `set`).\n• `split {-s} by \",\" into {-arr::*}` — splits into `{-arr::1}`, `{-arr::2}`…\n• `lowercase` / `uppercase` / `trim` — case conversion and whitespace trimming.\n• `substring of {-s} from 1 to 5` — 1-based slice (omit `to` for end of string)."
    },
    example: `# Замена, регистр, обрезка и подстрока
set {-msg} to "  Привет, Мир!  "
set {-clean} to trim of {-msg}
set {-low} to lowercase {-clean}
replace "мир" with "ScriptCraft" in {-low}
set {-part} to substring of {-clean} from 1 to 6

# Разбиение строки в список
split "яблоко,груша,вишня" by "," into {-fruits::*}
loop {-fruits::*}:
\tsend "&e%loop-index%: &f%loop-value%" to player`,
    since: "0.7.6", related: ["cond-text-match", "list-operations"]
  },
  {
    id: "screen-box", category: "screen",
    name: { ru: "Панель / прямоугольник с градиентом (box / rect)", en: "Panel / gradient box (box / rect)" },
    syntax: [
      "box|rect [at <X> <Y>] [size <W>x<H>] [anchor <A>] [color #AARRGGBB] [gradient|to #AARRGGBB] [horizontal|vertical] [border #AARRGGBB] [border_width <N>] [radius <N>] [shadow] [blur] [scale <N>] [rotate <deg>] [hover_scale <N>] [id <id>] [layer <N>] [hidden] [tooltip \"...\"] [run ... | close]"
    ],
    desc: {
      ru: "Рисует современную панель, карточку или разделитель прямо в `open screen` и `create hud` **без PNG-текстур**:\n• `color #AARRGGBB` — основной цвет с прозрачностью (по умолчанию `#CC111827`).\n• `gradient #AARRGGBB` (`to #AARRGGBB`) + `vertical`/`horizontal` — плавный градиент.\n• `border #AARRGGBB` и `border_width <N>` — цвет и толщина рамки.\n• `radius <N>` (0–32) — скруглённые углы у заливки, градиента и рамки.\n• `shadow` — мягкая многослойная тень под панелью.\n• `blur` — эффект матового стекла с тонким верхним бликом.\n• Поддерживает `scale`, `rotate`, `hover_scale`, `id`, `layer`, `hidden`, `tooltip`, `run`, `close` и `animate screen`.",
      en: "Draws a modern rectangle/card in `open screen` or `create hud` **without PNG textures**:\n• `color #AARRGGBB` — fill color with alpha (default `#CC111827`).\n• `gradient #AARRGGBB` (`to #AARRGGBB`) + `vertical`/`horizontal` — smooth gradient.\n• `border #AARRGGBB` and `border_width <N>` — crisp border.\n• `radius <N>` (0–32) — rounded corners for fill, gradient, and border.\n• `shadow` — soft multi-layer drop shadow.\n• `blur` — frosted glass backdrop with subtle top highlight.\n• Supports `scale`, `rotate`, `hover_scale`, `id`, `layer`, `hidden`, `tooltip`, `run`, `close`, and `animate screen`."
    },
    example: `command /card:
\topen screen "Профиль" to player:
\t\t# Скруглённая стеклянная карточка с тенью, blur, градиентом и рамкой
\t\tbox at 0 0 size 240x130 color #CC0F172A gradient #CC1E293B border #FF38BDF8 border_width 1 radius 8 shadow blur layer 0
\t\t# Горизонтальная градиентная полоса-заголовок
\t\trect at 0 -54 size 232x20 color #FF2563EB to #FF7C3AED horizontal radius 5 layer 1
\t\ttext "&f&lПрофиль %player%" at 0 -54 anchor center shadow layer 2`,
    since: "0.7.6", related: ["eff-open-screen", "screen-animate", "screen-transform-sprites", "hud-create"]
  },
  {
    id: "screen-animate", category: "screen",
    name: { ru: "Плавная анимация элемента (animate / tween)", en: "Smooth client animation (animate / tween)" },
    syntax: [
      "animate|tween screen \"<id>\" [for <цель>] to <X> <Y> [in <T> ticks|seconds]",
      "animate|tween screen element \"<id>\" [for <цель>] to <X> <Y> [in <T> ticks|seconds]"
    ],
    desc: {
      ru: "Плавно перемещает любой элемент открытого экрана (`image`, `box`, `bar`, `text`, `button`, `slot`, `input`, `slider`, `toggle`) в новые координаты `<X> <Y>` прямо на клиенте со скоростью **60+ FPS** в одном компактном дельта-пакете. Длительность задаётся в `ticks` (по умолчанию `6`) или `seconds`. С версии `0.8.3` работает и для полосок `bar` (например, HP-баров над мобами).",
      en: "Smoothly animates any open screen element (`image`, `box`, `bar`, `text`, `button`, `slot`, `input`, `slider`, `toggle`) to `(X, Y)` on the client at **60+ FPS** via a single delta packet. Duration in `ticks` (default `6`) or `seconds`. Since `0.8.3` also supports `bar` elements."
    },
    example: `# Плавный выезд панели и полёт снаряда за 1 пакет
animate screen "menu_panel" for player to 0 0 in 10 ticks
animate screen "projectile_1" for player to 120 -30 in 6 ticks
tween screen "popup" to 0 -60 in 0.5 seconds`,
    since: "0.7.6", related: ["screen-dynamic", "screen-box", "eff-open-screen"]
  },
  {
    id: "eff-hologram", category: "effects",
    name: { ru: "3D-голограммы в мире (TextDisplay)", en: "3D World Holograms (TextDisplay)" },
    syntax: [
      "create hologram \"<id>\" text \"<текст>\" at <x y z|локация|цель> [scale <N>] [billboard center|vertical|horizontal|fixed]",
      "set text of hologram \"<id>\" to \"<текст>\"",
      "move hologram \"<id>\" to <x y z|локация|цель>",
      "remove|delete hologram \"<id>\"",
      "clear holograms"
    ],
    desc: {
      ru: "Создаёт нативные 3D-голограммы Minecraft 1.20.1 (`TextDisplay`) с поддержкой цветов `&`, многострочного текста `\\n`, масштаба `scale` (по умолчанию `1.0`) и режима поворота `billboard`: `center` (всегда к камере, по умолчанию), `vertical`, `horizontal`, `fixed`. Голограммы управляются по строковому `id`.",
      en: "Spawns native Minecraft 1.20.1 `TextDisplay` 3D holograms with `&` colors, `\\n` multiline text, `scale` (default `1.0`), and `billboard` mode (`center`, `vertical`, `horizontal`, `fixed`). Managed by string `id`."
    },
    example: `# Создать 3D-голограмму над игроком
command /holo:
\tcreate hologram "shop" text "&6&lМАГАЗИН\\n&7Нажмите ПКМ по торговцу" at player scale 1.4 billboard center

# Обновить текст, переместить или удалить
set text of hologram "shop" to "&a&lСКИДКА 50%!"
move hologram "shop" to 10 66 -5
remove hologram "shop"`,
    since: "0.7.6", related: ["expr-location", "eff-bossbar"]
  },
  {
    id: "eff-bossbar", category: "effects",
    name: { ru: "Ванильные боссбары сверху экрана (bossbar)", en: "Vanilla BossBars (bossbar)" },
    syntax: [
      "create bossbar \"<id>\" title \"<текст>\" [color pink|blue|red|green|yellow|purple|white] [style progress|notched_6|notched_10|notched_12|notched_20] [progress <0..1|0..100>] [for <цель>]",
      "set title of bossbar \"<id>\" to \"<текст>\"",
      "set progress of bossbar \"<id>\" to <число>",
      "set color of bossbar \"<id>\" to <цвет>",
      "set style of bossbar \"<id>\" to <стиль>",
      "show bossbar \"<id>\" to <цель>",
      "hide bossbar \"<id>\" from <цель>",
      "remove|delete bossbar \"<id>\"",
      "clear bossbars"
    ],
    desc: {
      ru: "Управляет ванильными боссбарами вверху экрана (`ServerBossEvent`).\n• Цвета (`color`): `red`, `blue`, `green`, `yellow`, `pink`, `purple`, `white`.\n• Стили (`style`): `progress` (сплошной), `notched_6`, `notched_10`, `notched_12`, `notched_20` (деления).\n• Прогресс (`progress`): от `0.0` до `1.0` (числа больше `1` автоматически делятся на `100`, например `75` = `0.75`).",
      en: "Controls vanilla top-of-screen BossBars (`ServerBossEvent`).\n• `color`: `red`, `blue`, `green`, `yellow`, `pink`, `purple`, `white`.\n• `style`: `progress`, `notched_6`, `notched_10`, `notched_12`, `notched_20`.\n• `progress`: `0.0`–`1.0` (values > `1` auto-divide by `100`, e.g. `75` → `0.75`)."
    },
    example: `# Показать боссбар события всем игрокам
create bossbar "raid" title "&c&lРЕЙД БОССА" color red style notched_10 progress 1.0 for all players

# Обновить прогресс и заголовок
set progress of bossbar "raid" to 0.45
set title of bossbar "raid" to "&6&lФАЗА 2 — 45%"
set color of bossbar "raid" to yellow

# Скрыть или удалить
hide bossbar "raid" from player
remove bossbar "raid"`,
    since: "0.7.6", related: ["hud-bar", "eff-hologram"]
  },
  {
    id: "eff-item-lore-enchant", category: "effects",
    name: { ru: "Лор, зачарования и макс. здоровье (lore / enchant / max health)", en: "Item Lore, Enchants & Max Health" },
    syntax: [
      "set lore of player's tool to \"<строка1\\nстрока2>\"",
      "set lore of slot [hotbar] <слот> of <цель> to \"<текст>\"",
      "add lore \"<строка>\" to player's tool | slot [hotbar] <слот> of <цель>",
      "clear lore of player's tool | slot [hotbar] <слот> of <цель>",
      "enchant player's tool | slot [hotbar] <слот> of <цель> with <id> [level <N>]",
      "set max health of <цель> to <число>",
      "set player's max health to <число>"
    ],
    desc: {
      ru: "Удобное редактирование предметов и характеристик игрока без сложного SNBT:\n• `set lore` / `add lore` / `clear lore` — задаёт описание (лор) предмета в руке (`player's tool`) или любом слоте (`slot 0`, `slot hotbar 1`, `slot helmet`). Поддерживает цвета `&` (без ванильного курсива) и `\\n` для нескольких строк.\n• `enchant ... with <id> [level <N>]` — накладывает любое ванильное или модовое зачарование (`sharpness`, `unbreaking`, `mending`, `efficiency`, `protection`, `fire_aspect`, `looting`, `fortune`, `silk_touch`, `power`, `infinity`, `frost_walker` и др.) с уровнем `1`–`255`.\n• `set max health of <цель> to <N>` — меняет максимальное здоровье игрока (`Attributes.MAX_HEALTH`, например `40` = 20 сердец).",
      en: "Clean item & attribute editing without raw SNBT:\n• `set lore` / `add lore` / `clear lore` — sets non-italic `&`-colored lore lines (`\\n` separates lines) on `player's tool` or any inventory/hotbar slot.\n• `enchant ... with <id> [level <N>]` — applies any vanilla or modded enchantment (`sharpness`, `unbreaking`, `mending`, `efficiency`, `protection`, etc., level `1`–`255`).\n• `set max health of <target> to <N>` — changes `Attributes.MAX_HEALTH` (`40` = 20 hearts)."
    },
    example: `# Легендарный меч с лором и зачарованиями
command /legend:
\tgive player diamond_sword 1
\tset lore of player's tool to "&6Легендарный клинок\\n&7Урон: &c+15\\n&8Привязан к %player%"
\tadd lore "&a★ Уровень 1" to player's tool
\tenchant player's tool with sharpness level 5
\tenchant player's tool with unbreaking level 3

# Увеличить максимальное здоровье игрока до 20 сердец (40 HP)
set max health of player to 40
heal player`,
    since: "0.7.6", related: ["eff-set-slot", "eff-give-item", "eff-set-health"]
  },
  {
    id: "screen-gui-fx", category: "screen",
    name: { ru: "Всплывающие цифры и GUI-частицы (gui float / gui particle)", en: "Client Floating Numbers & GUI Particles" },
    syntax: [
      "gui float <текст> at <X> <Y> [color #RRGGBB] [for <T> ticks] [to|for <цель>]",
      "gui particle|particles <пресет> [count <N>] at <X> <Y> [color #RRGGBB] [spread <R>] [to|for <цель>]"
    ],
    desc: {
      ru: "Создаёт эффекты прямо поверх открытого экрана (`open screen`), полностью анимируемые на клиенте в **60+ FPS** (сервер шлёт всего 1 короткий пакет):\n• `gui float` — всплывающий текст/урон с плавным подъёмом, лёгким pop-эффектом и затуханием.\n• `gui particle` — вспышка 2D-частиц с физикой разлёта, гравитацией и затуханием.\n• Пресеты частиц: `spark` (искры), `explosion` / `boom` (взрыв), `fire` / `flame` (огонь вверх), `ice` / `frost` (снежинки), `poison` (яд), `gold` / `coin` (звёзды золота), `magic` (магия), `heart` / `heal` (сердечки), `smoke` (дым).",
      en: "Spawns 60+ FPS client-side effects over `open screen` using a single tiny packet:\n• `gui float` — rising, pop-scaled, fading floating damage/text.\n• `gui particle` — physics-driven 2D GUI particle burst with gravity and fade.\n• Presets: `spark`, `explosion`/`boom`, `fire`/`flame`, `ice`/`frost`, `poison`, `gold`/`coin`, `magic`, `heart`/`heal`, `smoke`."
    },
    example: `# Всплывающий крит и взрыв искр при попадании в мини-игре
gui float "&c&l-120 CRIT!" at 40 -20 color #FF4444 for 16 ticks to {-team::*}
gui particle "explosion" count 14 at 40 -20 spread 32 to {-team::*}
gui particle "ice" count 10 at -50 10 to player`,
    since: "0.8.3", related: ["screen-animate", "screen-box", "target-varlist"]
  },
  {
    id: "screen-transform-sprites", category: "screen",
    name: { ru: "Поворот, масштаб, hover-scale и спрайтшиты", en: "Rotation, Scale, Hover-Scale & Spritesheets" },
    syntax: [
      "image \"...\" ... [scale <N>] [hover_scale <N>] [rotate <градусы>] [frames <N>] [fps <N>] [follow cursor] [hover_circle <R>] [hover_color #RRGGBB]",
      "text \"...\" ... [scale <N>] [hover_scale <N>] [rotate <градусы>] [follow cursor] [hover_circle <R>] [hover_color #RRGGBB]",
      "set scale of screen [element] \"<id>\" [for <цель>] to <число>",
      "set rotation of screen [element] \"<id>\" [for <цель>] to <градусы>",
      "set hover_circle of screen [element] \"<id>\" [for <цель>] to <радиус> [color \"#RRGGBB\"]"
    ],
    desc: {
      ru: "Клиентские трансформации, привязка к курсору, круг при наведении и покадровая анимация спрайтов без нагрузки на сервер:\n• `scale <N>` (0.1–10.0) — масштаб элемента (`image`, `text`, `box`).\n• `hover_scale <N>` (`hscale`) — автоматическое увеличение при наведении курсора на клиенте.\n• `rotate <deg>` (`rotation`) — поворот элемента на заданный угол в градусах.\n• `follow cursor` — элемент автоматически следует за курсором мыши на экране (управляется через `show` / `hide`).\n• `hover_circle <R>` и `hover_color #RRGGBB` — при наведении на элемент клиент рисует полупрозрачный тактический круг радиуса `R` вокруг него (можно менять на лету через `set hover_circle ... to <R> [color \"#...\"]`, `0` — выключить).\n• `frames <N>` и `fps <N>` у `image` — клиентская анимация спрайтшита.\n• `set scale` и `set rotation` — динамическое изменение масштаба и угла на лету.",
      en: "Client-side transforms, cursor follower, hover circle, and spritesheet animation with zero server overhead:\n• `scale <N>` (0.1–10.0) — scales `image`, `text`, or `box`.\n• `hover_scale <N>` (`hscale`) — automatic hover zoom on the client.\n• `rotate <deg>` (`rotation`) — rotates around element center.\n• `follow cursor` — element follows the mouse cursor on screen (toggle via `show` / `hide`).\n• `hover_circle <R>` & `hover_color #RRGGBB` — draws a translucent tactical radius circle on hover (dynamic `set hover_circle ... to <R> [color \"#...\"]`, `0` disables).\n• `frames <N>` and `fps <N>` on `image` — client-side spritesheet playback.\n• `set scale` / `set rotation` — dynamically update scale or angle."
    },
    example: `# Иконка с увеличением при наведении и анимированный спрайтшит огня (4 кадра, 10 FPS)
open screen "Арена" to player:
\timage "game:textures/gui/btn_play.png" at 0 40 size 48x48 texture 48x48 hover_scale 1.15 run Start() keep open
\timage "game:textures/gui/fire_sheet.png" at 0 -20 size 32x32 texture 128x32 frames 4 fps 10 id fire
\timage "game:textures/gui/arrow.png" at 0 0 size 16x16 texture 16x16 rotate 45 id arrow

# Повернуть стрелу на лету
set rotation of screen "arrow" for player to 135
set scale of screen "arrow" for player to 1.3`,
    since: "0.8.3", related: ["screen-animate", "screen-box", "screen-gui-fx"]
  },
  {
    id: "target-varlist", category: "targets",
    name: { ru: "Список игроков в цели ({list::*})", en: "Player list target ({list::*})" },
    syntax: [
      "<действие> ... to|for {список::*}",
      "<действие> ... to|for {-локальный-список::*}"
    ],
    desc: {
      ru: "Любую переменную-список с никами игроков (или объектами `player`) можно передавать **напрямую как цель** любого эффекта — без `loop`! Онлайн-игроки из списка выбираются автоматически с дедупликацией. Ускоряет групповые рассылки и кооп-скрипты.",
      en: "Any list variable containing player names/objects can be passed **directly as a target** to any effect without a `loop`. Online players are resolved and deduplicated automatically."
    },
    example: `# Отправка сообщения, звука и обновление экрана всей команде в одну строку
send "&aВолна началась!" to {-team::*}
play sound entity.player.levelup to {-team::*} with volume 0.5 pitch 1.3
set position of screen element "boss" for {-team::*} to 100 -20`,
    since: "0.8.3", related: ["target-all", "target-named", "list-operations"]
  },
  {
    id: "expr-newline", category: "expressions",
    name: { ru: "Перенос строки (%nl% / %newline%)", en: "Newline placeholder (%nl% / %newline%)" },
    syntax: [
      "%nl%",
      "%newline%",
      "\\n"
    ],
    desc: {
      ru: "Вставляет перенос на новую строку в **любом тексте** по всему ScriptCraft: в `tooltip`, `text` на экранах и HUD, `send` / `broadcast`, `set lore`, `create hologram`, а также при склейке строк в переменных. Работают и `%nl%` / `%newline%`, и классический `\\n`.",
      en: "Inserts a line break in **any text** across ScriptCraft: `tooltip`, screen/HUD `text`, `send` / `broadcast`, `set lore`, `create hologram`, and string variables. Both `%nl%` / `%newline%` and `\\n` are supported."
    },
    example: `# Многострочный тултип, текст на экране, лор и сообщение в чат
button "&aСтарт" at 0 0 tooltip "&aЗапустить волну &7(Space)%nl%&bВо время волны: x1 / x2"
text "&6Первая строка%nl%&7Вторая строка" at 0 -20 anchor center shadow
send "&eСтрока 1%newline%&aСтрока 2" to player
set lore of player's tool to "&6Редкий меч%nl%&7Урон: &c+15"`,
    since: "0.8.3", related: ["expr-colors", "eff-open-screen", "eff-item-lore-enchant", "eff-hologram"]
  },
  {
    id: "eff-player-animation", category: "effects",
    name: { ru: "Анимации игрока (PlayerAnimator + Bendy-lib)", en: "Player Animations (PlayerAnimator + Bendy-lib)" },
    syntax: [
      "play [player] animation \"<id>\" [on|of <цель>] [for|to <зрители>] [first person|no first person|first person <bool>] [speed <N>] [fade <тики>]",
      "stop [player] animation [on|of <цель>] [for|to <зрители>] [fade <тики>]"
    ],
    desc: {
      ru: "Проигрывает или останавливает 3D-анимацию модели игрока через опциональные клиентские библиотеки **PlayerAnimator** и **Bendy-lib** (сгибание конечностей).\n• **Загрузка из ресурсов**: положите файл анимации `.json` (формат Emotecraft / GeckoLib / Blockbench player animation) в `scriptcraft/resources/assets/<namespace>/player_animation/<name>.json` (или `player_animations/<name>.json`) и выполните `/sc reload resources` — файл синхронизируется клиентам и подхватывается сразу без перезагрузки ресурспаков! Если namespace не указан (`\"wave\"`), используется `scriptcraft:wave`.\n• `on|of <цель>` — на чьей модели проиграть анимацию (`player`, `targeted player`, `all players`, `{-team::*}`; по умолчанию `player`).\n• `for|to <зрители>` — кто увидит анимацию (`for player` — только один игрок, `for all players` — все; по умолчанию все игроки в том же мире).\n• `first person` (по умолчанию) / `no first person` (`without first person`) / `first person <bool>` — показывать ли анимацию рук и тела самому игроку от первого лица.\n• `speed <N>` (`0.05`–`10.0`, по умолчанию `1.0`) — множитель скорости.\n• `fade <тики>` (`0`–`40`, по умолчанию `4`) — плавный вход/выход между позами.",
      en: "Plays or stops a 3D player model animation via optional client libraries **PlayerAnimator** and **Bendy-lib** (smooth limb bending).\n• **Loading from resources**: place `.json` animation files in `scriptcraft/resources/assets/<namespace>/player_animation/<name>.json` (or `player_animations/<name>.json`) and run `/sc reload resources` — synced to clients and hot-loaded without a full resourcepack reload. Omitting namespace (`\"wave\"`) defaults to `scriptcraft:wave`.\n• `on|of <target>` — player(s) whose model animates (default `player`).\n• `for|to <viewers>` — who sees the animation (`for player`, `for all players`; defaults to all players in the same world).\n• `first person` (default) / `no first person` (`without first person`) / `first person <bool>` — toggle first-person rendering.\n• `speed <N>` (`0.05`–`10.0`, default `1.0`) and `fade <ticks>` (`0`–`40`, default `4`)."
    },
    example: `# Помахать рукой (видят все вокруг и сам игрок от первого лица)
command /wave:
\tplay animation "myanim:wave" on player

# Удар мечом быстрее обычного, без показа от первого лица
play animation "combat:slash" on player no first person speed 1.4 fade 3 for all players

# Проиграть анимацию на цели в прицеле только для самого игрока
play animation "emotes:dance" on targeted player for player first person false

# Плавно остановить анимацию за 6 тиков
command /stopanim:
\tstop animation of player fade 6`,
    since: "0.8.8", related: ["studio-anim-editor", "studio-libs-and-colors", "cmd-reload", "target-varlist"]
  },
  {
    id: "studio-anim-editor", category: "commands",
    name: { ru: "3D-редактор анимаций игрока в Студии", en: "3D Player Animation Editor in Studio" },
    syntax: [
      "Студия (F3 + ~) → вкладка «Анимация игрока» (слева от «Профайлер»)"
    ],
    desc: {
      ru: "Полноценный встроенный 3D-редактор анимаций модели игрока уровня Blockbench прямо внутри Студии ScriptCraft:\n• **Живой 3D-вьюпорт с Bendy-lib и видом от 1-го лица (`1P`)**: рендерит вашего персонажа со скином, вторым слоем, бронёй и предметами в руках; при установленных `PlayerAnimator` + `Bendy-lib` показывает плавный изгиб конечностей в реальном времени, а кнопка **`1P`** включает настоящую перспективную камеру от первого лица из глаз игрока.\n• **Ортогональный 3D-Гизмо + 2 стрелки изгиба прямо на кости**: кликайте ЛКМ по частям тела на 3D-модели — в суставе отображаются строгие ортогональные оси **`X` (вправо), `Y` (строго вверх), `Z` (вперёд)** для **Поворота (`R`)** и **Сдвига (`G`)**, а посередине каждой сгибаемой кости одновременно доступны **2 отдельные стрелки изгиба Bendy-lib** (`B` — угол изгиба, `D` — направление изгиба) без переключения режима!\n• **Отсоединяемые и настраиваемые панели**: левая панель («Скелет»), правая («Инспектор») и нижняя («Таймлайн») по умолчанию закреплены у краёв экрана, но каждую можно **свернуть/развернуть (`▾`/`▸`)**, **растянуть за края** или **отсоединить (`⧉`)** в плавающее окно любого размера и привязать обратно (`▣`). Все пропорции и позиции автоматически сохраняются у клиента.\n• **Многодорожечный таймлайн и 31 кривая Easing**: перемотка ползунка, выделение нескольких ключей рамкой мыши (**box-select**), сдвиг групп ключей по времени, переключение вида «Все кости / 8 каналов выбранной кости», живой график кривой (`INOUTSINE`, `OUTBACK`, `OUTELASTIC`, `OUTBOUNCE` и др.), цикл (`Loop` + `returnTick`).\n• **Тест в мире и мгновенная синхронизация**: кнопки **`▶ В игре`** и **`■ Стоп`** проверяют и останавливают анимацию на вашем персонаже вне меню; `Ctrl+S` сохраняет `.json` в `scriptcraft/resources/assets/<ns>/player_animation/<name>.json` и сразу рассылает ресурс всем игрокам.",
      en: "Full-featured Blockbench-class 3D player animation editor built right into ScriptCraft Studio:\n• **Live 3D Viewport with Bendy-lib & True 1st-Person (`1P`)**: renders your character with skin layers, armor, and held items with real-time curved limb deformation, plus a true perspective first-person eye camera (`1P`).\n• **Orthogonal 3D Gizmo + 2 Direct Bend Arrows per Bone**: selecting any bone shows orthogonal **`X` (right), `Y` (up), `Z` (forward)** handles at the joint for **Rotate (`R`)** and **Move (`G`)**, plus **2 separate Bendy-lib arrows** (`B` bend angle, `D` bend direction) right at the mid-limb with no mode switching.\n• **Detachable, Resizable, Collapsible Docks**: Left (Skeleton), Right (Inspector), and Bottom (Timeline) panels are docked by default and can be resized, collapsed/expanded (`▾`/`▸`), or detached (`⧉`) into freely movable/resizable floating windows and re-docked (`▣`), with all proportions persisted client-side.\n• **Multi-Track Timeline & 31 Easing Curves**: playhead scrubbing, **marquee box-selection**, multi-keyframe retiming, Bones/Channels view toggle, live 2D easing curve graph, and loop (`returnTick`).\n• **In-World Test & Instant Sync**: **`▶ In-game`** and **`■ Stop`** test and stop the animation on your player outside the menu; `Ctrl+S` saves and hot-syncs `.json` to all online clients immediately."
    },
    example: `# После сохранения анимации (Ctrl+S) во вкладке «Анимация игрока»
# нажмите «→ В скрипт» — команда готова к запуску сразу в игре:
command /slash:
\tplay animation "scriptcraft:slash" on player for all players first person`,
    since: "0.8.8", related: ["eff-player-animation", "studio-libs-and-colors", "screen-gui-builder"]
  },
  {
    id: "screen-entity-player", category: "screen",
    name: { ru: "3D-рендер сущностей, игроков и скинов (entity / player / skin)", en: "3D Entity, Player & Skin Render (entity / player / skin)" },
    syntax: [
      "player [\"<ник|URL|ресурс>\"] [at <X> <Y>] [size <W>x<H>] [scale <S>] [model auto|slim|wide] [overlay|no overlay] [spin [left|right] [speed <deg>]] [look cursor | look at <X> <Y> | yaw <Y> [pitch <P>]] [walk] [sneaking] [baby] [weapon <id>] [helmet <id>] [chestplate <id>] [leggings <id>] [boots <id>] [clip] [id <id>] [tooltip \"...\"] [run ...]",
      "skin \"<ник | https://... | ns:textures/skins/file.png>\" [at <X> <Y>] [size <W>x<H>] [scale <S>] [model auto|slim|wide] [no overlay] [spin left|right speed <N>] [look cursor | look at <X> <Y>]",
      "entity \"<ns:entity_id>\" [at <X> <Y>] [size <W>x<H>] [scale <S>] [spin [left|right] [speed <deg>]] [look cursor | look at <X> <Y> | yaw <Y> [pitch <P>]] [nbt \"{...}\"] [baby] [walk] [weapon <id>] [helmet <id>] [clip] [id <id>]"
    ],
    desc: {
      ru: "Рендерит живые 3D-модели игроков, кастомных скинов и любых ванильных/модовых сущностей прямо в `open screen` и `create hud`:\n• **Игроки и 3 источника скинов**: укажите **никнейм** (`\"%player%\"`, `\"ElgerElg\"` — мгновенно берётся из таб-листа или асинхронно через Mojang API), **прямую ссылку** (`\"https://.../skin.png\"`) или **файл из ресурсов** (`\"mypack:textures/skins/knight.png\"` в `scriptcraft/resources/assets/...` — подхватывается без перезагрузки ресурспаков!). Старые скины `64×32` автоматически конвертируются в `64×64`.\n• **Автоподбор модели Алекс / Стив и второй слой (overlay)**: по умолчанию `model auto` сам определяет тонкие руки **Alex (`slim`, 3px)** или классические **Steve (`wide`, 4px)** по профилю игрока или прозрачности пикселей PNG! Можно задать вручную (`slim`/`alex` или `wide`/`steve`). Второй объёмный слой скина (шляпа, куртка, рукава, штаны) включён по умолчанию и отключается флагом `no overlay`.\n• **Размер и обрезка**: `size <W>x<H>` задаёт область и хитбокс (при отсутствии `scale` модель автоматически вписывается в `WxH`, учитывая габариты моба), `scale <S>` задаёт точный масштаб в пикселях на блок, `clip` обрезает выход за границы `WxH`.\n• **Вращение и слежение взглядом**:\n  – `spin right speed 40` / `spin left speed 60` (`spin -45`) — плавное вращение в выбранную сторону с заданной скоростью (градусов/сек);\n  – `look cursor` (`look mouse`) — персонаж или моб плавно поворачивает голову и корпус вслед за курсором мыши;\n  – `look at <X> <Y>` — сущность смотрит в конкретную точку экрана `<X> <Y>` относительно центра;\n  – `yaw <deg>`, `pitch <deg>`, `roll <deg>` — фиксированный ракурс (можно комбинировать со `spin`).\n• **Экипировка, позы и NBT**: поддерживаются `weapon`, `offhand`, `helmet`, `chestplate`, `leggings`, `boots`, `walk` (анимация шага), `sneaking`, `baby` и `nbt \"{...}\"` (например, заряженный крипер `{powered:1b}` или профессия жителя).",
      en: "Renders live 3D players, custom skins, and any vanilla or modded entity inside `open screen` and `create hud`:\n• **3 Skin Sources**: by **nickname** (`\"%player%\"`, `\"ElgerElg\"` via tablist or async Mojang API), **HTTP/HTTPS URL** (`\"https://.../skin.png\"`), or **mod resources** (`\"mypack:textures/skins/knight.png\"`, hot-loaded without resourcepack reload). Legacy `64×32` skins auto-upgrade to `64×64`.\n• **Auto Alex / Steve Model & Skin Overlay**: `model auto` (default) automatically selects **Alex (`slim`, 3px)** or **Steve (`wide`, 4px)** from profile metadata or PNG pixel alpha! Override with `slim`/`alex` or `wide`/`steve`. 2nd skin layer (`hat`, `jacket`, `sleeves`, `pants`) is shown by default (`no overlay` hides it).\n• **Size & Clipping**: `size <W>x<H>` sets bounds & auto-fits large/small mobs unless exact `scale <S>` (px/block) is set; `clip` scissors to `WxH`.\n• **Spin & Look Modes**:\n  – `spin right speed 40` / `spin left speed 60` — smooth continuous rotation in either direction;\n  – `look cursor` — smoothly tracks the live mouse cursor with head and torso;\n  – `look at <X> <Y>` — looks toward a specific screen point `<X> <Y>`;\n  – `yaw`, `pitch`, `roll` — static angles.\n• **Gear, Poses & NBT**: `weapon`, `offhand`, `helmet`, `chestplate`, `leggings`, `boots`, `walk`, `sneaking`, `baby`, and `nbt \"{...}\"`."
    },
    example: `command /profile:
\topen screen "Профиль героя" to player:
\t\tbox at 0 0 size 260x165 color #E60F172A border #FF38BDF8 radius 8 shadow layer 0
\t\t# Свой персонаж со снаряжением следит взглядом за курсором мыши
\t\tplayer "%player%" at -78 6 size 76x115 scale 36 look cursor weapon diamond_sword helmet diamond_helmet id hero layer 2
\t\t# Кастомный NPC-скин из ресурсов или по ссылке, плавно крутящийся вправо
\t\tskin "mypack:textures/skins/guard.png" at 0 6 size 70x110 model auto spin right speed 35 walk layer 2
\t\t# Заряженный крипер, смотрящий в точку кнопки (0, 62)
\t\tentity "minecraft:creeper" at 78 10 size 68x100 nbt "{powered:1b}" look at 0 62 id mob_preview layer 2
\t\tbutton "&aСменить скин" at 0 62 size 110x20 run SetGuardSkin(player) keep open layer 3`,
    since: "0.8.8", related: ["eff-open-screen", "screen-dynamic", "screen-list", "screen-gui-builder", "hud-create"]
  },
  {
    id: "screen-gui-builder", category: "screen",
    name: { ru: "Визуальный редактор экранов (GUI Builder) в Студии", en: "Visual GUI Screen Builder in Studio" },
    syntax: [
      "Кнопка «GUI» в верхней панели Студии (для любого .sc с open screen)"
    ],
    desc: {
      ru: "Встроенный визуальный WYSIWYG-редактор экранов прямо внутри Студии (`F3 + ~`):\n• Автоматически находит все блоки `open screen` и `create hud` в открытом `.sc` файле и разделяет их на отдельные экраны.\n• **Холст 1-в-1 с игрой (включая 3D-сущности и списки)**: перетаскивание мышью, ресайз за уголки, сетка/привязка, палитра элементов (`+ Box`, `+ Text`, `+ Button`, `+ Image`, `+ Player`, `+ Entity`, `+ List`, `+ Slot`, `+ Bar`, `+ Input`, `+ Slider`, `+ Toggle`), слои в стиле Photoshop и инспектор свойств.\n• **Инспектор 3D-рендеров и списков**: настройка скина (ник / URL / ресурс), автоподбора Alex/Steve, второго слоя, скорости/направления вращения, взгляда за курсором или в точку (с прицелом на холсте), а также визуальный конструктор строк списка (`+Row`, `+Sep`, `+Empty`, ширина/высота позиции, иконки, головы, бейджи, выравнивание, активность и тултипы).\n• **Раскрываемые текстовые поля (`⤢`)**, **`Ctrl+Z` / `Ctrl+Y`** и **совместное редактирование GUI (`liveEdit`)**.",
      en: "Built-in WYSIWYG screen builder inside Studio (`F3 + ~`):\n• Automatically parses all `open screen` and `create hud` blocks in any `.sc` script into selectable screens.\n• **1:1 live canvas (including 3D Entities/Players & Rich Lists)**: drag-and-drop, corner resize, snap grid, element palette (`+ Box`, `+ Text`, `+ Button`, `+ Image`, `+ Player`, `+ Entity`, `+ List`, `+ Slot`, `+ Bar`, `+ Input`, `+ Slider`, `+ Toggle`), Photoshop-style layers, and property inspector.\n• **3D Render & List Row Inspector**: configure skin source (nick/URL/resource), Alex/Steve model, overlay, spin direction/speed, cursor/point look target (with canvas crosshair), plus an interactive list row builder (`+Row`, `+Sep`, `+Empty`, custom width/height, icons, heads, badges, alignment, active/disabled states, and tooltips).\n• **Expandable string fields (`⤢`)**, **`Ctrl+Z` / `Ctrl+Y`**, and **collaborative GUI editing (`liveEdit`)**."
    },
    example: `# Любой блок open screen в .sc сразу открывается в визуальном GUI-редакторе Студии:
command /menu:
\topen screen "Главное меню" to player:
\t\tbox at 0 0 size 240x150 color #CC0F172A gradient #CC1E293B radius 8 shadow layer 0 id bg
\t\ttext "&6&lГЛАВНОЕ МЕНЮ" at 0 -56 anchor center shadow layer 2 id title
\t\tbutton "&aИграть" at 0 0 size 120x22 anchor center run StartGame(player) layer 3 id btn_play`,
    since: "0.8.8", related: ["eff-open-screen", "screen-box", "cfg-studioLiveEdit", "studio-libs-and-colors"]
  },
  {
    id: "studio-libs-and-colors", category: "commands",
    name: { ru: "Статус библиотек и цвет редактора в Студии", en: "Studio Library Status & Editor Color Palette" },
    syntax: [
      "Студия (F3 + ~) → Настройки (⚙)"
    ],
    desc: {
      ru: "В настройках Студии (`⚙`) доступны диагностика подключённых интеграций и персонализация совместной работы:\n• **Библиотеки и интеграции**: показывает в реальном времени, загружены ли на клиенте `PlayerAnimator` и `Bendy-lib` (с версией мода), а также какой режим поддержки `LuckPerms` активен на сервере (`мод`, `плагин`, `мод и плагин`, `api` или `нет`).\n• **Палитра цвета редактора**: выбор из 12 цветов (или авто-цвет `A`), которым ваш курсор, выделение текста и рамка выбранного элемента в GUI-редакторе отображаются у всех остальных участников сессии.",
      en: "Studio Settings (`⚙`) includes live integration diagnostics and co-editing customization:\n• **Libraries & Integrations**: displays live status and version for client-side `PlayerAnimator` and `Bendy-lib`, plus the server's active `LuckPerms` mode (`mod`, `plugin`, `mod and plugin`, `api`, or `no`).\n• **Editor Color Palette**: 12 swatches (including Auto `A`) synced live so other editors see your chosen color on carets, text selections, and GUI Builder element frames."
    },
    example: `# Проверка прав LuckPerms и запуск анимации PlayerAnimator в одном скрипте:
command /vipwave:
\tif player has permission "server.vip":
\t\tplay animation "vip:royal_wave" on player for all players first person
\telse:
\t\tsend "&cНужна привилегия VIP!" to player`,
    since: "0.8.8", related: ["eff-player-animation", "screen-gui-builder", "cfg-studioLiveEdit", "cmd-journal"]
  },
  {
    id: "eff-leash", category: "effects",
    name: { ru: "Настраиваемый поводок (сущность/игрок ↔ сущность/игрок или блок)", en: "Configurable Leash (Entity/Player ↔ Entity/Player or Block)" },
    syntax: [
      "leash <сущность/игрок> to <сущность/игрок> [length <N>] [min_length <N>] [strength <N>] [break <N> | unbreakable] [color <#HEX>] [width <N>] [sag <N>] [two_way] [drop_lead]",
      "leash <сущность/игрок> to targeted block [length <N>] [color <#HEX>] [knot true|false] ...",
      "leash <сущность/игрок> to block at <X> <Y> <Z> [length <N>] ...",
      "leash <сущность/игрок> to location <локация> [length <N>] ...",
      "set leash <length|min_length|strength|break|color|width|sag|two_way|knot> of <сущность/игрок> to <значение>",
      "unleash <сущность/игрок>",
      "clear all leashes"
    ],
    desc: {
      ru: "Универсальная механика 3D-поводка, работающая с **любыми сущностями и игроками** (игрок ↔ игрок, моб ↔ игрок, игрок ↔ блок/забор, лодка/стойка ↔ сущность):\n• **Настоящий 3D-рендер верёвки и узла** в мире (от 1-го и от 3-го лица) с учётом освещения и провисания + плавная клиент-серверная физика натяжения для игроков без рывков.\n• **Гибкие настройки поводка**:\n  – `length <N>` — макс. длина до натяжения (по умолчанию `6.0`); `min_length <N>` — мин. дистанция (режим жёсткой штанги).\n  – `strength <N>` (`pull` / `stiffness`) — упругость натяжения (`0` — только визуальная верёвка без тяги, `1.0` — обычная, `2.5` — жёсткая цепь).\n  – `break <N>` / `breakable` / `unbreakable` — дистанция обрыва со звуком и событием `on leash break:` (по умолчанию не рвётся).\n  – `two_way` (`bidirectional`) — обоюдная тяга 50/50 (например, два игрока скованы одной цепью).\n  – `color <#RRGGBB>` (или `chain`, `gold`, `blue`, `red`), `width <N>` (толщина `0.2..6`), `sag <N>` (провисание `0..4`), `knot` (3D-узел на блоке), `drop_lead` (выпадение предмета поводка при обрыве).\n• Все свойства можно менять на лету через `set leash <свойство> of <цель> to ...`.",
      en: "Universal 3D leash tether working on **any entities and players** (player ↔ player, mob ↔ player, player ↔ block/fence, boat/stand ↔ entity):\n• **Authentic 3D sagging rope & block-knot rendering** (1st-person and 3rd-person with world lighting) paired with smooth client-predicted spring physics for players.\n• **Configurable parameters**:\n  – `length <N>` — max slack length (default `6.0`); `min_length <N>` — min strut distance.\n  – `strength <N>` (`pull` / `stiffness`) — pull tension (`0` = visual-only rope, `1.0` = normal, `2.5` = heavy chain).\n  – `break <N>` / `breakable` / `unbreakable` — snap distance triggering `on leash break:` (unbreakable by default).\n  – `two_way` (`bidirectional`) — mutual 50/50 tension between two entities/players.\n  – `color <#RRGGBB>` (or `chain`, `gold`, `blue`, `red`), `width <N>` (`0.2..6`), `sag <N>` (`0..4`), `knot`, `drop_lead`.\n• Live-adjustable via `set leash <prop> of <target> to ...`."
    },
    example: `# Конвой / наручники между игроками + привязка к столбу по команде:
command /cuff <player>:
\tif arg-1 is leashed:
\t\tunleash arg-1
\t\tsend "&aВы сняли конвой с %arg-1%" to player
\telse:
\t\tleash arg-1 to player length 4.5 strength 1.6 color #94A3B8 width 1.3 sag 0.4
\t\tsend "&eВы взяли %arg-1% на стальной трос!" to player

command /tiepost:
\tleash targeted entity to targeted block length 5 break 14 drop_lead`,
    since: "0.8.8", related: ["event-leash-break", "cond-leashed", "expr-leash"]
  },
  {
    id: "event-leash-break", category: "events",
    name: { ru: "Обрыв поводка (on leash break)", en: "Leash Break Event (on leash break)" },
    syntax: [
      "on leash break:"
    ],
    desc: {
      ru: "Срабатывает, когда настраиваемый поводок рвётся из-за превышения дистанции `break <N>` или разрушения опорного блока. Плейсхолдеры: `%victim%` (кто отвязался), `%holder%` (ник держателя, тип сущности или `block`), `%player%` (участник-игрок).",
      en: "Fires when a custom leash snaps due to exceeding `break <N>` distance or breaking its anchor block. Placeholders: `%victim%` (unleashed entity/player), `%holder%` (holder name, entity id, or `block`), `%player%`."
    },
    example: `on leash break:
\tsend "&cПоводок с %victim% оборвался! (держатель: %holder%)" to player`,
    since: "0.8.8", related: ["eff-leash", "cond-leashed", "expr-leash"]
  },
  {
    id: "cond-leashed", category: "conditions",
    name: { ru: "Проверка поводка (is leashed)", en: "Leashed Check (is leashed)" },
    syntax: [
      "<сущность/игрок> is leashed",
      "<сущность/игрок> is not leashed"
    ],
    desc: {
      ru: "Проверяет, привязана ли сущность или игрок поводком (поддерживает как кастомные поводки `leash`, так и ванильные поводки мобов).",
      en: "Checks whether a player or entity is currently tethered by a custom or vanilla leash."
    },
    example: `if player is leashed:
\tsend "&eДержатель поводка: &f%leash holder of player%" to player`,
    since: "0.8.8", related: ["eff-leash", "expr-leash", "event-leash-break"]
  },
  {
    id: "expr-leash", category: "expressions",
    name: { ru: "Свойства поводка и созданной сущности", en: "Leash Properties & Last Spawned Entity" },
    syntax: [
      "leash length of <сущность/игрок>",
      "leash distance of <сущность/игрок>",
      "leash holder of <сущность/игрок>",
      "leash anchor of <сущность/игрок>",
      "last spawned entity",
      "uuid of last spawned entity",
      "uuid of targeted entity"
    ],
    desc: {
      ru: "Выражения для работы с поводками и сущностями:\n• `leash length of ...` — максимальная длина поводка (число).\n• `leash distance of ...` — текущая дистанция натяжения в блоках.\n• `leash holder of ...` — имя игрока-держателя, ID типа сущности или `\"block\"`.\n• `leash anchor of ...` — локация точки крепления поводка.\n• `last spawned entity` и `uuid of last spawned entity` — ссылка на только что созданную через `spawn entity` сущность (UUID можно сохранить в `{переменную}` и передавать в `leash {pet} to player`!).",
      en: "Expressions for inspecting leashes and spawned entities:\n• `leash length of ...` — configured max rope length.\n• `leash distance of ...` — live distance to anchor in blocks.\n• `leash holder of ...` — holder player name, entity type ID, or `\"block\"`.\n• `leash anchor of ...` — anchor `location`.\n• `last spawned entity` & `uuid of last spawned entity` — references the entity created by `spawn entity` (storing its UUID in `{var}` lets you pass `{var}` directly to `leash`, `unleash`, or `push`!)."
    },
    example: `command /petwolf:
\tspawn entity "minecraft:wolf" at player named "&bВерный Пёс" with health 40
\tset {pet::%player%} to uuid of last spawned entity
\tleash last spawned entity to player length 5 color #38BDF8`,
    since: "0.8.8", related: ["eff-leash", "cond-leashed", "event-leash-break"]
  }
,
  {
    id: "define-entity", category: "structures",
    name: { ru: "Кастомные 3D-мобы, боссы, турели и декор (define entity)", en: "Custom 3D Mobs, Bosses, Turrets & Decor (define entity)" },
    syntax: [
      "define entity <id>:\n\tname: \"<имя>\"\n\tai: hostile|passive|turret|none\n\tmodel: \"<ns:модель>\"\n\ttexture: \"<ns:textures/entity/текстура.png>\"\n\tanimation: \"<ns:анимации>\"\n\tidle_animation: \"idle\"\n\twalk_animation: \"walk\"\n\tattack_animation: \"attack\"\n\tdeath_animation: \"death\"\n\tspawn_animation: \"roar\"\n\thealth: <N>\n\tspeed: <N>\n\tdamage: <N>\n\tattack_range: <N>\n\tfollow_range: <N>\n\thitbox: <Ш>x<В>\n\tscale: <N>\n\tglow: true|false\n\tparticle_bone: \"<локатор=частица>\""
    ],
    desc: {
      ru: "Объявляет полноценную 3D-сущность в формате Blockbench / GeckoLib (`.geo.json` + `.animation.json`) **вообще без лимита слотов** и без перезапуска игры при добавлении новых видов!\n• **Режимы ИИ (`ai:`)**: `hostile` (агрессивный моб/босс), `passive` (мирный), `turret` (неподвижная 3D-турель с авто-наведением и стрельбой по враждебным мобам), `none` (статичный 3D-декор/статуя).\n• **Светящиеся текстуры (`_glowmask.png`)**: если рядом с `cyber_guardian.png` положить `cyber_guardian_glowmask.png`, светящиеся части рендерятся в полной яркости в темноте и с шейдерами.\n• **Партиклы из костей/локаторов**: `particle_bone: \"core_locator=end_rod\"` (а также `particle_effects` и `sound_effects` прямо внутри `.animation.json`!) спавнят частицы в точных мировых 3D-координатах кости с учётом её поворотов.",
      en: "Declares a custom Blockbench / GeckoLib 3D entity (`.geo.json` + `.animation.json`) with **zero slot limits** and live `/sc reload` support!\n• **AI Modes (`ai:`)**: `hostile` (melee boss/monster), `passive` (wandering mob), `turret` (stationary sentry auto-targeting monsters), `none` (3D prop/statue).\n• **Emissive Glowmasks (`_glowmask.png`)**: placing `<name>_glowmask.png` next to the texture automatically renders full-bright emissive layers.\n• **Bone & Locator Particles**: `particle_bone: \"core_locator=end_rod\"` (plus `.animation.json` `particle_effects` & `sound_effects` keyframes) emits particles from exact animated 3D bone/locator world positions."
    },
    example: `namespace gecko

define entity cyber_guardian:
	name: "&b&lКибер-Страж"
	ai: hostile
	model: "gecko:cyber_guardian"
	texture: "gecko:textures/entity/cyber_guardian.png"
	animation: "gecko:cyber_guardian"
	idle_animation: "idle"
	walk_animation: "walk"
	attack_animation: "attack"
	health: 120
	damage: 8
	hitbox: 1.3x2.6
	scale: 1.15
	particle_bone: "core_locator=end_rod"`,
    since: "0.9.10", related: ["eff-geo-entity", "define-armor", "eff-geo-item-block", "res-auto-ogg-studio"]
  },
  {
    id: "define-armor", category: "structures",
    name: { ru: "3D-броня, 3D-оружие и 3D-блоки (define armor / item / block)", en: "3D Armor, 3D Weapons & 3D Animated Blocks (define armor / item / block)" },
    syntax: [
      "define armor <id>:\n\tslot: helmet|chestplate|leggings|boots\n\tdefense: <N>\n\ttoughness: <N>\n\tmodel: \"<ns:модель>\"\n\ttexture: \"<ns:textures/armor/текстура.png>\"\n\tanimation: \"<ns:анимации>\"\n\tidle_animation: \"idle\"",
      "define item <id>:\n\tmodel: \"<ns:модель>\"\n\ttexture: \"<ns:textures/item/текстура.png>\"\n\tanimation: \"<ns:анимации>\"\n\tidle_animation: \"idle\"\n\tscale: <N>",
      "define block <id>:\n\tfull: false\n\tfacing: horizontal\n\tmodel: \"<ns:модель>\"\n\ttexture: \"<ns:textures/block/текстура.png>\"\n\tanimation: \"<ns:анимации>\"\n\tidle_animation: \"idle\""
    ],
    desc: {
      ru: "Полная поддержка 3D-моделей Blockbench (`.geo.json` + `.animation.json` + `*_glowmask.png`) для брони, предметов и блоков:\n• **`define armor`**: привязывает кости `armorHead`, `armorBody`, `armorRightArm`, `armorLeftArm`, `armorPelvis`, `armorRightLeg`, `armorLeftLeg`, `armorRightBoot`, `armorLeftBoot` напрямую к скелету игрока и фейковых NPC — **100% синхронизация с анимациями `PlayerAnimator`!**\n• **3D `define item`**: рендерит 3D-модель с анимациями от 1-го лица, от 3-го лица, в хотбаре/инвентаре, в рамке и на земле (автоматический `builtin/entity`, ручной JSON в `models/item` не нужен!).\n• **3D `define block`**: создаёт анимированный `BlockEntity` с поддержкой `facing: horizontal`, `light:` и переключением клипов через `play block animation`.",
      en: "Full Blockbench 3D `.geo.json` + `.animation.json` + `*_glowmask.png` support for armor, items, and blocks:\n• **`define armor`**: binds `armorHead`, `armorBody`, `armorRightArm`, `armorLeftArm`, `armorPelvis`, `armorRightLeg`, `armorLeftLeg`, `armorRightBoot`, `armorLeftBoot` directly to player/NPC humanoid bones — **100% synced with `PlayerAnimator` emotes!**\n• **3D `define item`**: renders animated 3D weapons/items in 1st-person, 3rd-person, GUI slots, item frames, and ground drops automatically.\n• **3D `define block`**: creates an animated 3D `BlockEntity` supporting `facing: horizontal`, `light:`, and `play block animation`."
    },
    example: `namespace gecko

define armor exo_helmet:
	name: "&bЭкзо-шлем"
	slot: helmet
	defense: 4
	toughness: 2.0
	model: "gecko:exo_armor"
	texture: "gecko:textures/armor/exo_armor.png"

define item plasma_rifle:
	name: "&bПлазменная винтовка"
	stack: 1
	model: "gecko:plasma_rifle"
	texture: "gecko:textures/item/plasma_rifle.png"
	animation: "gecko:plasma_rifle"
	idle_animation: "idle"`,
    since: "0.9.10", related: ["define-entity", "eff-geo-item-block", "eff-prop-attach"]
  },
  {
    id: "eff-geo-entity", category: "effects",
    name: { ru: "Призыв и анимации 3D-мобов (spawn geo entity / play geo animation)", en: "Spawn & Animate 3D Geo Entities" },
    syntax: [
      "spawn geo entity|boss|turret|prop \"<ns:id>\" at <локация/игрок/X Y Z> [named \"<имя>\"] [health <N>] [damage <N>] [scale <N>] [hitbox <Ш>x<В>] [ai <режим>]",
      "play geo animation \"<клип>\" on <сущность> [once|loop|hold] [speed <N>]",
      "stop geo animation on <сущность>",
      "set geo texture of <сущность> to \"<ns:путь.png>\"",
      "set geo scale of <сущность> to <N>",
      "clear all geo entities",
      "/sc summon <ns:id> [X Y Z]   (команда администратора в чате)"
    ],
    desc: {
      ru: "Призывает 3D-мобов из `define entity` (или напрямую по модели `model \"...\" texture \"...\"`) и управляет их анимациями, текстурой фаз босса и масштабом в реальном времени. Сразу после спавна сущность доступна через `last spawned entity` (а также её можно выводить в 3D прямо внутри GUI через `entity \"gecko:cyber_guardian\"`!).",
      en: "Spawns 3D entities defined via `define entity` (or inline `model`/`texture` overrides) and controls their animations, boss-phase textures, and scale on the fly. Accessible immediately via `last spawned entity` and renderable live inside GUI screens via `entity \"gecko:cyber_guardian\"`."
    },
    example: `command /boss:
	spawn geo entity "gecko:cyber_guardian" at player
	play geo animation "roar" on last spawned entity once speed 1.0`,
    since: "0.9.10", related: ["define-entity", "expr-leash", "eff-geo-item-block"]
  },
  {
    id: "eff-geo-item-block", category: "effects",
    name: { ru: "Анимации 3D-предметов и 3D-блоков (play item / block animation)", en: "Trigger 3D Item & 3D Block Animations" },
    syntax: [
      "play item animation \"<клип>\" [for <игрок>] [mainhand|offhand] [once|loop] [speed <N>]",
      "stop item animation [for <игрок>] [mainhand|offhand]",
      "play block animation \"<клип>\" at <targeted block | X Y Z | локация> [once|loop|hold] [speed <N>]",
      "stop block animation at <targeted block | X Y Z | локация>"
    ],
    desc: {
      ru: "Запускает 3D-анимацию (`.animation.json` с поддержкой математических выражений Molang `math.sin(query.anim_time * ...)`) на кастомном 3D-предмете в руке игрока (отдача выстрела, перезарядка, осмотр оружия) или на установленном в мире 3D-блоке (открытие сейфа, активация голограммы).",
      en: "Triggers a `.animation.json` clip (including live Molang math expressions) on a held 3D item (recoil, reload, inspect) or a placed 3D block in the world."
    },
    example: `on right click:
	if player is holding gecko:plasma_rifle:
		play item animation "shoot" for player once speed 1.25
	if "%block%" is "gecko:holo_terminal":
		play block animation "activate" at targeted block once`,
    since: "0.9.10", related: ["define-armor", "eff-geo-entity", "res-auto-ogg-studio"]
  },
  {
    id: "eff-npc", category: "effects",
    name: { ru: "Фейковые игроки-NPC со скинами и эмоциями (spawn npc)", en: "Fake Player NPCs with Skins & Emotes (spawn npc)" },
    syntax: [
      "spawn npc \"<id>\" at <игрок/локация/X Y Z> [named \"<имя>\"] [skin \"<ник | URL | ns:путь.png>\"] [wide|slim] [look true|false] [vulnerable] [sneaking]",
      "equip npc \"<id>\" with <mainhand|offhand|helmet|chestplate|leggings|boots> <предмет>",
      "play npc animation \"<ns:эмоция>\" on npc \"<id>\" [speed <N>]",
      "stop npc animation on npc \"<id>\"",
      "make npc \"<id>\" look at <игрок/локация/X Y Z>",
      "make npc \"<id>\" walk to <игрок/локация/X Y Z> [speed <N>]",
      "set skin|name of npc \"<id>\" to <значение>",
      "remove npc \"<id>\"",
      "clear all npcs"
    ],
    desc: {
      ru: "Создаёт полноценного NPC-человека без засорения таб-листа:\n• **Любые скины**: локальный PNG из `resources/` (`\"gecko:textures/skins/commander.png\"`), прямая ссылка `https://...` или ник лицензионного игрока + модели рук Steve (`wide`) и Alex (`slim`).\n• **Эмоции `PlayerAnimator`**: проигрывает любые анимации из внутриигровой Студии (`play npc animation \"gecko:npc_salute\" on npc \"commander\"`)!\n• **3D-экипировка и аттачи**: NPC носит кастомную 3D-броню (`define armor`), держит 3D-оружие (`define item`) и поддерживает 3D-аттачи к костям (`attach prop ... to body of npc \"id\"`).",
      en: "Spawns a full humanoid player NPC without polluting the server tab list:\n• **Flexible Skins**: local resource PNG (`\"gecko:textures/skins/commander.png\"`), HTTP PNG URL, or player nickname, in `wide` (Steve) or `slim` (Alex) model styles.\n• **`PlayerAnimator` Emotes**: plays any Studio/Emotecraft animation via `play npc animation`.\n• **3D Gear & Props**: wears bone-synced 3D armor, holds 3D weapons, and accepts bone prop attachments."
    },
    example: `command /commander:
	spawn npc "commander" at player named "&b★ Командор Векс" skin "gecko:textures/skins/commander.png" wide look true
	equip npc "commander" with chestplate gecko:exo_chestplate
	equip npc "commander" with mainhand gecko:plasma_rifle
	play npc animation "gecko:npc_salute" on npc "commander"`,
    since: "0.9.10", related: ["event-npc", "eff-prop-attach", "define-armor"]
  },
  {
    id: "event-npc", category: "events",
    name: { ru: "Взаимодействие с фейковым NPC (on npc click / attack)", en: "Fake NPC Interaction Events (on npc click / attack)" },
    syntax: [
      "on npc click [\"<id>\"]:",
      "on npc attack [\"<id>\"]:"
    ],
    desc: {
      ru: "Срабатывает при нажатии ПКМ (`on npc click`) или ударе ЛКМ (`on npc attack`) по фейковому игроку-NPC. Если указать строковый ID (например `on npc click \"commander\":`), обработчик сработает только для этого NPC. Внутри доступны `player` и `%npc%` (ID нажатого NPC).",
      en: "Fires when a player right-clicks (`on npc click`) or hits (`on npc attack`) a fake NPC. Optional string ID filters a specific NPC; `%npc%` holds the NPC's ID."
    },
    example: `on npc click "commander":
	make npc "commander" look at player
	play npc animation "gecko:npc_salute" on npc "commander"
	send "&b[Командор] &fЧесть имею, %player%!" to player`,
    since: "0.9.10", related: ["eff-npc", "eff-open-screen"]
  },
  {
    id: "eff-prop-attach", category: "effects",
    name: { ru: "3D-аттачи (Props) к костям игрока и NPC (attach prop)", en: "3D Bone Attachments / Props (attach prop)" },
    syntax: [
      "attach prop \"<слот>\" model \"<ns:модель>\" [texture \"<ns:путь.png>\"] [animation \"<клип>\"] to <head|body|right_arm|left_arm|right_leg|left_leg> of <игрок | npc \"id\"> [offset <X> <Y> <Z>] [rotation <RX> <RY> <RZ>] [scale <N>]",
      "attach prop \"<слот>\" item \"<id_предмета>\" to <кость> of <игрок | npc \"id\"> [offset <X> <Y> <Z>] [rotation <RX> <RY> <RZ>] [scale <N>]",
      "detach prop \"<слот>\" from <игрок | npc \"id\">",
      "clear props of <игрок | npc \"id\">"
    ],
    desc: {
      ru: "Прикрепляет 3D-модель `.geo.json` (с собственной цикличной анимацией и `_glowmask.png`!) или любой предмет прямо к выбранной кости тела игрока или фейкового NPC (`head`, `body`, `right_arm`, `left_arm`, `right_leg`, `left_leg`). Идеально для рюкзаков, крыльев, дронов на плечо, кобуры, наручников или меча за спиной — аттачи плавно двигаются вместе с ходьбой, приседанием и эмоциями `PlayerAnimator`!",
      en: "Attaches an animated `.geo.json` 3D model (with `_glowmask.png` support) or any Minecraft/custom item directly to a bone (`head`, `body`, `right_arm`, `left_arm`, `right_leg`, `left_leg`) of a player or fake NPC, moving seamlessly with walking, sneaking, and `PlayerAnimator` animations."
    },
    example: `command /drone:
	attach prop "drone" model "gecko:shoulder_drone" animation "hover" to body of player offset -6 5 2 scale 0.85`,
    since: "0.9.10", related: ["eff-npc", "define-armor", "eff-geo-entity"]
  },
  {
    id: "eff-camera-cutscene", category: "effects",
    name: { ru: "Катсцены и плавный пролёт камеры по точкам (play cutscene)", en: "Smooth Spline Camera Cutscenes (play cutscene)" },
    syntax: [
      "play cutscene for <игрок> points \"<X1 Y1 Z1; X2 Y2 Z2; X3 Y3 Z3...>\" for <N> seconds|ticks [looking at <цель | npc \"id\" | X Y Z>] [lock|unlock] [lock_look|free_look] [hide_hand|show_hand]",
      "move camera of <игрок> from \"<X1 Y1 Z1 [yaw pitch]>\" to \"<X2 Y2 Z2 [yaw pitch]>\" in <N> seconds|ticks [looking at <цель>] [lock|unlock] [lock_look|free_look] [hide_hand|show_hand]",
      "stop cutscene [for <игрок>]"
    ],
    desc: {
      ru: "Кинематографичный пролёт камеры с по-кадровой интерполяцией по гладкому сплайну **Catmull-Rom** (240+ FPS без ступенек тиков!):\n• Поддерживаются как абсолютные координаты, так и относительные от игрока через `~` (`\"~0 ~2.2 ~5.5; ~5 ~3.5 ~1; ~0 ~2 ~-5\"`), а также явные углы `X Y Z yaw pitch` в каждой точке.\n• Опция `looking at <игрок | npc \"id\" | X Y Z>` плавно удерживает фокус камеры на персонаже, боссе или точке интереса. Во время пролёта модель самого игрока остаётся видимой со стороны (с 3D-бронёй и эмоциями)!\n• **Полный контроль над персонажем и рукой (всё опционально!)**:\n  – По умолчанию во время катсцены **заблокирована ходьба** (`lock`), **заблокирован поворот головы/тела мышкой** (`lock_look`) и **скрыта правая рука от 1-го лица** (`hide_hand`).\n  – Любое ограничение можно гибко переключить прямо в команде: `unlock` (или `free_move`) — разрешить ходьбу; `free_look` (или `lock_look false`) — разрешить вращать головой мышкой; `show_hand` (или `hand true` / `hide_hand false`) — оставить руку справа видимой; `free` — разблокировать и движение, и мышку.",
      en: "Cinematic per-frame **Catmull-Rom spline** camera flight (butter-smooth at 240+ FPS):\n• Supports absolute coordinates, player-relative `~` offsets (`\"~0 ~2.2 ~5.5; ~5 ~3.5 ~1\"`), or explicit `X Y Z yaw pitch` per waypoint.\n• `looking at <entity | npc \"id\" | X Y Z>` smoothly tracks a target throughout the flight while keeping the local player's 3D body, armor, and emotes visible.\n• **Optional Movement, Head-Turn & First-Person Hand Controls**:\n  – By default, cutscenes lock WASD movement (`lock`), lock mouse head/body rotation (`lock_look`), and hide the right-hand first-person arm (`hide_hand`).\n  – Every flag is optional: use `unlock` (`free_move`) to allow walking, `free_look` (`lock_look false`) to allow mouse head turning, `show_hand` (`hand true` / `hide_hand false`) to keep the first-person arm visible, or `free` to unlock both movement and look."
    },
    example: `command /intro:
	fade screen of player to #000000 in 5 stay 4 out 10 ticks
	play cutscene for player points "~0 ~2.2 ~5.5; ~5.2 ~3.6 ~1.5; ~1.5 ~4.0 ~-5.2; ~-5.0 ~2.8 ~-1.0" for 6 seconds looking at player`,
    since: "0.9.10", related: ["eff-camera", "eff-npc", "eff-geo-entity"]
  },
  {
    id: "res-auto-ogg-studio", category: "config",
    name: { ru: "Авто-регистрация звуков .ogg + 3D-вьюер и аудиоплеер в Студии", en: "Auto .ogg Sound Registration + Studio 3D Geo Viewer & Audio Player" },
    syntax: [
      "scriptcraft/resources/assets/<ns>/sounds/<имя>.ogg  →  play sound <ns>:<имя> to player",
      "scriptcraft/resources/assets/<ns>/geo/<модель>.geo.json  →  кнопка [◈ 3D Geo] в Студии"
    ],
    desc: {
      ru: "Удобная работа со звуками и 3D-моделями прямо внутри игры:\n• **Никакого окна «Установить серверный ресурспак: Да / Нет?» (`disableVanillaResourcePackPrompt = true`)**: ресурсы из `scriptcraft/resources/assets` передаются через собственный кэш мода (`scriptcraft/cache/assets`), 3D-модели `.geo.json`/`.animation.json` и текстуры мобов/брони подхватываются на лету вообще без перезагрузки ресурспаков, а лишний ванильный запрос из `server.properties` автоматически подавляется.\n• **Авто-звуки `.ogg` без ручного `sounds.json`**: достаточно положить любой `.ogg` файл в `scriptcraft/resources/assets/<ns>/sounds/` (например `assets/gecko/sounds/plasma_shot.ogg`) и выполнить `/sc reload resources` — ScriptCraft автоматически сгенерирует и объединит `sounds.json`, синхронизирует звук клиентам и позволит сразу вызывать `play sound gecko:plasma_shot`! Подпапки переводятся в точки или слэши (`sounds/ui/click.ogg` → `ns:ui.click` или `ns:ui/click`).\n• **Встроенный OGG-аудиоплеер в Студии**: при открытии `.ogg` в Проводнике Студии отображается реальная звуковая волна (Waveform), кнопки Play/Pause/Stop/Loop, перемотка, ползунки Volume/Pitch и кнопка `→ В скрипт`, вставляющая готовую команду `play sound ...`!\n• **Интерактивный 3D-превьюер `.geo.json` в Студии**: открывает любую модель Blockbench прямо в редакторе с вращением ЛКМ, панорамированием ПКМ, зумом колёсиком, проверкой всех клипов `.animation.json`, подсветкой `_glowmask.png`, списком костей/локаторов и генератором заготовок `+ Entity / + Item / + Armor / + Block`!",
      en: "Zero-boilerplate `.ogg` audio and interactive 3D model tooling inside Studio:\n• **Automatic `.ogg` Sound Registration**: drop any `.ogg` file into `scriptcraft/resources/assets/<ns>/sounds/` and run `/sc reload resources` — ScriptCraft auto-generates/merges `sounds.json` and syncs it to clients with no manual JSON needed.\n• **Studio OGG Audio Player**: inspect real PCM waveforms, scrub, loop, test volume/pitch, and click `→ Insert` to paste a ready `play sound` line.\n• **Studio 3D `.geo.json` Viewer**: orbit, pan, and zoom Blockbench models live inside Studio, preview `.animation.json` clips & `_glowmask.png` layers, inspect bones/locators, and generate `define` snippets in one click."
    },
    example: `# Положите файл scriptcraft/resources/assets/gecko/sounds/plasma_shot.ogg
# Никакой sounds.json создавать НЕ нужно! Сразу пишите в скрипте:
on right click:
	if player is holding gecko:plasma_rifle:
		play sound gecko:plasma_shot at player with volume 0.9 pitch 1.05`,
    since: "0.9.10", related: ["define-entity", "define-armor", "eff-geo-item-block"]
  }
,
  {
    id: "cfg-dynamic-real-ids", category: "config",
    name: {
      ru: "Настоящие modid:item / modid:block через Mixin + Виджет загрузки сверху слева",
      en: "Mixin Dynamic Real IDs (modid:item / modid:block) + Top-Left Download Progress Toast"
    },
    syntax: [
      "useLegacySlotPool = false   # в scriptcraft/config.toml (по умолчанию false — сразу настоящие modid:item!)"
    ],
    desc: {
      ru: "Полный отказ от ручного кэша слотов и перезапусков игры за счёт встроенного **Mixin-движка динамической регистрации** и фоновой синхронизации кэша:\n• **Вход с 1-й попытки с настоящими `modid:item` / `modid:block`**: при подключении сервер передаёт описания `define block / item / armor` и пачки ресурсов в `scriptcraft/cache/assets/`, а клиент через Mixin на лету регистрирует настоящие объекты (`gecko:plasma_rifle`, `gecko:holo_terminal` и т.д.) перед сверкой реестров Forge. Все ID сразу настоящие в F3, `/give`, датапаках, рецептах и JEI!\n• **Горячая регистрация новых ID по `/sc reload` (Вариант Б)**: новые `define block` / `define item` на работающем сервере сразу регистрируются у всех онлайн-игроков (`HotRegistryPacket`) без кика и без рестарта!\n• **Компактная плашка прогресса слева сверху (в стиле уведомлений скриптов)**: ванильные и модовые меню входа и загрузки мира остаются нетронутыми — поверх них в левом верхнем углу выводится аккуратный прогресс-бар с **процентом (`0%..100%`)**, **именем текущего скачиваемого файла** и **счётчиком оставшихся файлов (`Осталось файлов: X из Y`)**, который не исчезает, пока абсолютно все файлы не скачаются и не применятся.",
      en: "Eliminates slot-cache restarts via **Mixin dynamic registry injection** and seamless asset sync:\n• **First-try server join with real `modid:item` / `modid:block` IDs**: during the login handshake, the server streams `define` metadata and asset batches into `scriptcraft/cache/assets/`, while the client dynamically injects real `Block`/`Item` entries prior to Forge's `injectSnapshot`.\n• **Hot `/sc reload` Registration (Variant B)**: adding brand-new `define block` or `define item` IDs on a live server immediately registers them on the server and all connected clients (`HotRegistryPacket`).\n• **Compact Top-Left Download Progress Widget**: leaves vanilla/modded connection and world-loading screens completely untouched, displaying a sleek script-toast-style widget in the top-left corner with **percentage (`0%..100%`)**, **currently downloading file**, and **remaining file count** that stays pinned until every file finishes downloading."
    },
    example: `[general]
# false (по умолчанию): настоящие modid:item / modid:block без рестарта клиента и авто-миграция старых слотов!
useLegacySlotPool = false

[resources]
syncResourcesToClients = true
disableVanillaResourcePackPrompt = true`,
    since: "0.9.15", related: ["define-entity", "define-armor", "res-auto-ogg-studio"]
  }
  ]
};
