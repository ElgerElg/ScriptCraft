/*
 * ScriptCraft — большие примеры для вкладки «Примеры».
 * Каждый пример: id, title {ru,en}, intro {ru,en}, blocks[] (lang: sc | json | toml | text), notes {ru[], en[]}, related[] (id из syntax.js).
 * Скрипты взяты из examples/scripts (welcome.sc, menu.sc, hud.sc, ruby.sc, bag.sc) — держите их в согласии.
 * Отступ в скриптах — Tab.
 */
window.SC_DOCS = window.SC_DOCS || {};
window.SC_DOCS.examples = [
  {
    id: "welcome",
    title: { ru: "Вход, эффекты и команды с аргументами", en: "Join, effects and commands with arguments" },
    intro: { ru: "Вход, переменные, шанс и команда с аргументами. Копируется выключенным: /sc enable welcome.", en: "Join, variables, chance and a command with arguments. Copied disabled: /sc enable welcome." },
    blocks: [
      { lang: "sc", title: { ru: "scriptcraft/scripts/welcome.sc", en: "scriptcraft/scripts/welcome.sc" }, code: `# ==========================================================
#  welcome.sc — вход, эффекты, переменные и команды с аргументами
#  Копируется выключенным. Включить: /sc enable welcome
#  Перезагрузка: /sc reload welcome. Выключить: /sc disable welcome
#  Отступ — Tab. Один Tab равен четырём пробелам, пробелы тоже понимаются.
# ==========================================================

on join:
	add 1 to {joins::%player%}
	if {joins::%player%} is 1:
		send "&aПривет, &e%player%&a! Ты здесь впервые." to player
		set {coins::%player%} to 20
		send "&7Стартовые &e20 монет&7. Магазин: &f/menu" to player
	else:
		send "&aС возвращением, &e%player%&a! Вход номер %{joins::%player%}%." to player
	set {-online_since::%player%} to "сегодня"
	broadcast "&7%player% зашёл (онлайн: %online%)"

on quit:
	broadcast "&7%player% вышел"

# Хвост строки без списка аргументов — это %args%.
command /hello:
	aliases: hi
	send "&bПривет, %player%! Ты написал: &f%args%" to player
	send "&8Скрипт: %script%" to player

# permission, aliases и executable by пишутся ПОД командой, не в заголовке.
command /kit:
	permission: 2
	aliases: starter
	give player diamond 3
	give player bread 16
	heal player
	feed player
	send "&6Набор выдан." to player

command /buff:
	permission: 2
	aliases: boost
	heal player
	feed player
	extinguish player
	give player effect speed for 60 seconds level 2
	give player effect night_vision for 120 seconds
	play sound entity.player.levelup to player
	send title "&6Бафф" with subtitle "&eСкорость и ночное зрение" to player
	send action bar "&aЗдоровье и голод восстановлены" to player

command /fly:
	permission: 2
	set flying of player to true
	send action bar "&bПолёт включён" to player

command /nofly:
	permission: 2
	aliases: walk
	set flying of player to false
	send action bar "&7Полёт выключен" to player

command /creative:
	permission: 2
	aliases: gmc
	set player's gamemode to creative

command /survival:
	permission: 2
	aliases: gms
	set gamemode of player to survival
	clear effects of player

# <player> обязателен, [integer] можно не писать. arg-1 — имя, arg-2 — число или <none>.
command /heal <player> [integer]:
	permission: 2
	aliases: h
	executable by: players and console
	if arg-2 is not set:
		heal arg-1
	else:
		heal arg-1 by arg-2
	send action bar "&aТебя лечит %player%" to arg-1
	reply "&aВылечен %arg-1%"

command /spawn:
	permission: 2
	aliases: hub
	teleport player to 0, 100, 0
	reply "Точка 0 100 0"

# Дом хранится в переменных. x/y/z of player — координаты.
command /sethome:
	aliases: sh
	set {home_x::%player%} to x of player
	set {home_y::%player%} to y of player
	set {home_z::%player%} to z of player
	set {home_world::%player%} to world of player
	send "&aДом сохранён: &f%{home_x::%player%}% %{home_y::%player%}% %{home_z::%player%}%" to player

command /home:
	aliases: base
	if {home_x::%player%} is not set:
		send "&cСначала /sethome" to player
	else:
		teleport player to {home_x::%player%}, {home_y::%player%}, {home_z::%player%}
		send "&aТы дома." to player

command /look:
	if targeted player is set:
		send "&eТы смотришь на &6%targeted%" to player
		send action bar "&c%player% смотрит на тебя" to targeted player
	else:
		send "&7Перед тобой никого нет" to player

command /gather:
	permission: 2
	teleport other players in radius 80 of player to player
	loop all players:
		send "&e%loop-player%, сбор у %player%" to loop-player
	play sound entity.player.levelup to @a

command /nearbuff:
	permission: 2
	give @a[distance=..10] effect regeneration for 10 seconds
	send title "&aРегенерация" to @a[distance=..10]

command /coins:
	aliases: bal, balance
	if {coins::%player%} is not set:
		set {coins::%player%} to 0
	send "&eМонет: &6%{coins::%player%}%" to player

# chance of — случайная ветка. {-worked} живёт до перезапуска сервера.
command /work:
	aliases: job
	if {-worked::%player%} is set:
		send "&cТы уже работал. Отдых до перезапуска сервера." to player
	else:
		add 5 to {coins::%player%}
		set {-worked::%player%} to true
		chance of 25%:
			add 5 to {coins::%player%}
			send "&dПремия! Ещё +5." to player
		send "&a+5 монет. Сейчас: &6%{coins::%player%}%" to player

command /pay <player> <integer>:
	aliases: transfer
	if {coins::%player%} < arg-2:
		send "&cНе хватает монет. Баланс: %{coins::%player%}%" to player
	else:
		if arg-2 < 1:
			send "&cСумма должна быть больше нуля." to player
		else:
			remove arg-2 from {coins::%player%}
			add arg-2 to {coins::%arg-1%}
			send "&aПереведено &6%arg-2% &aмонет игроку &f%arg-1%" to player
			send "&a%player% перевёл тебе &6%arg-2% &aмонет" to arg-1

command /resetcoins:
	permission: 2
	delete {coins::%player%}
	reply "Баланс сброшен"` }
    ],
    notes: { ru: ["Отступ — Tab. Один Tab равен четырём пробелам, пробелы по-прежнему понимаются.","Под командой: permission: 0–4, aliases: короткие имена через запятую, executable by: players / console / players and console.","Аргументы: <тип> обязательный, [тип] можно не писать. Типы: text, player, offline player, integer, number, boolean, date, world, item.","arg-1 — первый аргумент, %arg-1% — он же в тексте. Нет необязательного аргумента — arg-2 is not set.","Без списка аргументов хвост строки остаётся в %args%. %script% — имя файла без .sc.","{-имя} живёт до перезапуска сервера, {имя} пишется в scriptcraft/variables/variables.json."], en: ["Indent with Tab. One Tab equals four spaces; spaces still work.","Under the command: permission: 0–4, aliases: short names separated by commas, executable by: players / console / players and console.","Arguments: <type> is required, [type] may be omitted. Types: text, player, offline player, integer, number, boolean, date, world, item.","arg-1 is the first argument, %arg-1% is the same value in text. A missing optional argument is arg-2 is not set.","Without an argument list the rest of the line stays in %args%. %script% is the file name without .sc.","{-name} lives until the server restarts, {name} is saved in scriptcraft/variables/variables.json."] },
    related: ["struct-command", "struct-comment", "event-join", "expr-argument", "expr-script", "expr-variable", "expr-temp-variable", "struct-chance", "eff-teleport"]
  },
  {
    id: "menu",
    title: { ru: "Лавка: экран, слои, слоты и кнопки", en: "Shop: a screen, layers, slots and buttons" },
    intro: { ru: "Лавка /menu. Кнопки тратят монеты из welcome.sc.", en: "A shop on /menu. Buttons spend coins from welcome.sc." },
    blocks: [
      { lang: "sc", title: { ru: "scriptcraft/scripts/menu.sc", en: "scriptcraft/scripts/menu.sc" }, code: `# ==========================================================
#  menu.sc — экран-лавка. Копируется выключенным: /sc enable menu. Открыть: /menu
#  Элементы экрана, все необязательные хвосты можно комбинировать:
#    text "..."   at X Y   anchor ...   color #RRGGBB   shadow   layer N
#    button "..." at X Y   size WxH     anchor ...      run "/команда"  или  close   layer N
#    slot предмет [кол-во] at X Y  anchor ...  size WxH  layer N
#    slot hotbar N | slot image "ns:textures/....png" | slot text "..."
#    image "ns:textures/....png" at X Y  size WxH  texture WxH  anchor ...  color #RRGGBB  layer N
#    bar "имя" value ... max ... at X Y size WxH anchor ... direction ... fill "..." background ... text "..." layer N
#    hotbar id имя color #RRGGBB layer N
#  anchor: top_left, top, top_right, left, center, right, bottom_left, bottom, bottom_right
#  layer: больше = выше. at X Y считается от anchor, минус — влево или вверх.
#  Картинки из scriptcraft/resources/assets/<ns>/textures/gui/ сервер шлёт сам.
#  Монеты — переменная {coins::%player%} из welcome.sc.
# ==========================================================

command /menu:
	aliases: shop, lavka
	if {coins::%player%} is not set:
		set {coins::%player%} to 0
	open screen "Лавка" to player:
		image "example:textures/gui/menu_banner.png" at 0, -92 size 160x32 texture 128x32 layer 0
		text "&e%player%" at 0, -68 color #FFD700 shadow layer 2
		text "Монеты: %{coins::%player%}%" at 0, -54 color #FFFFFF shadow layer 2 id coins_label
		bar "purse" value {coins::%player%} max 100 at 0, -40 size 120x6 color #FFD700 background #40000000 layer 1
		slot example:ruby 1 at -60, -12 layer 1
		text "8" at -60, 6 color #FFD700 layer 2
		slot diamond 1 at -20, -12 layer 1
		text "15" at -20, 6 color #55FFFF layer 2
		slot bread 8 at 20, -12 layer 1
		text "3" at 20, 6 color #FFAA00 layer 2
		slot example:ruby_block 1 at 60, -12 layer 1
		text "20" at 60, 6 color #FF5555 layer 2
		button "Рубин" at -60, 28 size 52x20 run "/order ruby" layer 3
		button "Алмаз" at -20, 28 size 52x20 run "/order diamond" layer 3
		button "Хлеб" at 20, 28 size 52x20 run "/order bread" layer 3
		button "Блок" at 60, 28 size 52x20 run "/order block" layer 3
		button "Подлечиться (10)" at 0, 54 size 140x20 run "/order heal" layer 3
		button "Закрыть" at 0, 78 size 100x20 close layer 3
		text "%script%" at -4, -4 anchor bottom_right color #808080 layer 4

# arg-1 — одно слово: ruby, diamond, bread, block, heal.
command /order <text>:
	aliases: buy
	if {coins::%player%} is not set:
		set {coins::%player%} to 0
	if arg-1 is "ruby":
		if {coins::%player%} < 8:
			send "&cНужно 8 монет, у тебя %{coins::%player%}%" to player
		else:
			remove 8 from {coins::%player%}
			give player example:ruby 1
			send "&aКуплен рубин. Осталось: %{coins::%player%}%" to player
	else:
		if arg-1 is "diamond":
			if {coins::%player%} < 15:
				send "&cНужно 15 монет." to player
			else:
				remove 15 from {coins::%player%}
				give player diamond 1
				send "&bКуплен алмаз. Осталось: %{coins::%player%}%" to player
		else:
			if arg-1 is "bread":
				if {coins::%player%} < 3:
					send "&cНужно 3 монеты." to player
				else:
					remove 3 from {coins::%player%}
					give player bread 8
					send "&6Хлеб выдан. Осталось: %{coins::%player%}%" to player
			else:
				if arg-1 is "block":
					if {coins::%player%} < 20:
						send "&cНужно 20 монет." to player
					else:
						remove 20 from {coins::%player%}
						give player example:ruby_block 1
						send "&cБлок выдан. Осталось: %{coins::%player%}%" to player
				else:
					if arg-1 is "heal":
						if {coins::%player%} < 10:
							send "&cЛечение стоит 10 монет." to player
						else:
							remove 10 from {coins::%player%}
							heal player
							feed player
							send "&aТы в порядке. Осталось: %{coins::%player%}%" to player
					else:
						send "&7Неизвестный товар: %arg-1%. Лавка: /menu" to player

command /announce:
	permission: 2
	aliases: shout
	open screen "Объявление" to players in radius 16 of player:
		text "%player% зовёт всех рядом" at 0, -16 color #FFD700 shadow
		button "Понятно" at 0, 12 size 120x20 close` }
    ],
    notes: { ru: ["Элементы: text, button, slot, image, bar, hotbar. У всех есть at X Y, anchor, layer N (больше = выше) и id имя.","text: color #RRGGBB и shadow. button: size ШxВ, run \"/команда\" или close. Кнопка с run закрывает экран и выполняет команду от игрока.","slot: предмет и количество, либо slot hotbar N, slot image \"картинка\", slot text \"текст\". no frame убирает рамку.","image: \"ns:textures/....png\", size ШxВ, texture ШxВ (размер файла), color — оттенок.","bar: value число|{переменная}|health|food|armor|air|xp|level, max, direction left_to_right|right_to_left|bottom_to_top|top_to_bottom, fill, background, text.","anchor: top_left, top, top_right, left, center, right, bottom_left, bottom, bottom_right. Минус в at — влево или вверх.","Картинки из scriptcraft/resources/assets/<ns>/textures/gui/ сервер шлёт сам, перезагрузка ресурсов не нужна."], en: ["Elements: text, button, slot, image, bar, hotbar. All of them take at X Y, anchor, layer N (higher draws above) and id name.","text: color #RRGGBB and shadow. button: size WxH, run \"/command\" or close. A button with run closes the screen and runs the command as the player.","slot: an item and a count, or slot hotbar N, slot image \"picture\", slot text \"text\". no frame removes the frame.","image: \"ns:textures/....png\", size WxH, texture WxH (the file size), color tints it.","bar: value number|{variable}|health|food|armor|air|xp|level, max, direction left_to_right|right_to_left|bottom_to_top|top_to_bottom, fill, background, text.","anchor: top_left, top, top_right, left, center, right, bottom_left, bottom, bottom_right. A negative at moves left or up.","Pictures in scriptcraft/resources/assets/<ns>/textures/gui/ are sent by the server; no resource reload is needed."] },
    related: ["eff-open-screen", "screen-text", "screen-button", "screen-slot", "screen-image", "hud-bar", "hud-layer", "struct-command"]
  },
  {
    id: "hud",
    title: { ru: "HUD: полосы, хотбар и ванильные части", en: "HUD: bars, a hotbar and vanilla parts" },
    intro: { ru: "Интерфейс поверх игры. Копируется выключенным: /sc enable hud.", en: "An overlay on the game. Copied disabled: /sc enable hud." },
    blocks: [
      { lang: "sc", title: { ru: "scriptcraft/scripts/hud.sc", en: "scriptcraft/scripts/hud.sc" }, code: `# ==========================================================
#  hud.sc — свой интерфейс поверх игры
#  При первой установке файл копируется ВЫКЛЮЧЕННЫМ (-hud.sc):
#  он прячет ванильный хотбар. Включить: /sc enable hud
#  /sc disable hud убирает этот HUD и возвращает ванильные части.
#
#  create hud "группа" for <цель>:
#    text, image, slot, bar, hotbar — те же параметры, что у open screen
#    id имя     — чтобы потом менять элемент
#    layer N    — больше рисуется выше; layer < 0 рисуется под ванильным HUD
#  Полоса:
#    value health|max_health|food|saturation|armor|air|max_air|xp|level|{переменная}|число
#    max число|{переменная}
#    direction left_to_right | right_to_left | bottom_to_top | top_to_bottom
#    fill "картинка"   background цвет|картинка|none   text "%value% %percent%%"
#  Ваниль, которую можно спрятать:
#    hotbar, health, food, armor, air, experience, mount_health, jump_bar,
#    crosshair, effects, item_name, scoreboard, bossbar, hud, all
#  Дальше по игре:
#    set value|progress of bar "имя" for <цель> to <число>
#    set max of bar "имя" ... to <число>
#    set text of hud "имя" ... to "текст"
#    show hud / hide hud / remove hud "имя" / clear hud
#  {переменная} в тексте, value или max сама обновляет HUD в момент записи.
#  Пакет уходит только если строка изменилась. %health% и остальные источники
#  считает клиент каждый кадр, без пакетов.
# ==========================================================

on join or load:
	hide vanilla hotbar for player
	hide vanilla health for player
	hide vanilla food for player
	hide vanilla armor for player
	hide vanilla experience for player
	create hud "stats" for player:
		hotbar id hb color #FFD700 layer 0
		bar "hp" value health at -51, -28 size 80x8 anchor bottom fill "example:textures/gui/bar_fill_hp.png" background #A0000000 layer 1
		image "example:textures/gui/bar_frame.png" at -51, -26 size 84x12 anchor bottom layer 2
		text "❤ %health%/%max_health%" at -51, -40 anchor bottom color #FF5555 shadow layer 3 id hp_text
		bar "food" value food direction right_to_left at 51, -28 size 80x8 anchor bottom fill "example:textures/gui/bar_fill_food.png" background #A0000000 layer 1
		image "example:textures/gui/bar_frame.png" at 51, -26 size 84x12 anchor bottom layer 2
		text "%food%/20" at 51, -40 anchor bottom color #FFAA00 shadow layer 3
		bar "armor" value armor max 20 at -51, -48 size 80x4 anchor bottom color #AAAAAA layer 1
		bar "xp" value xp at 0, -8 size 182x4 anchor bottom color #55FF55 layer 1
		text "ур. %level%" at 0, -16 anchor bottom color #55FF55 shadow layer 2
		bar "air" value air direction bottom_to_top at -8, -70 size 6x36 anchor right color #55AAFF text "%percent%%" layer 1
	create hud "coins" for player:
		slot image "example:textures/gui/coin.png" at 4, 4 anchor top_left id coin_icon layer 0
		text "%{coins::%player%}%" at 26, 8 anchor top_left color #FFD700 shadow id coins_text layer 1
		bar "coins_goal" value {coins::%player%} max 100 at 4, 24 size 70x4 anchor top_left color #FFD700 layer 0
		text "%script%" at -4, 4 anchor top_right color #666666 layer 1

command /coinhud:
	aliases: coinsui
	set text of hud "coins_text" for player to "%{coins::%player%}%"
	set value of bar "coins_goal" for player to {coins::%player%}

command /hudoff:
	hide hud "stats" from player
	show vanilla hotbar for player
	show vanilla health for player
	show vanilla food for player
	show vanilla armor for player
	show vanilla experience for player

command /hudon:
	show hud "stats" to player
	hide vanilla hotbar for player
	hide vanilla health for player
	hide vanilla food for player
	hide vanilla armor for player
	hide vanilla experience for player

command /boss <integer>:
	permission: 2
	aliases: bossbar
	create hud "boss" for players in radius 30 of player:
		text "Страж" at 0, 6 anchor top color #FF5555 shadow layer 1
		bar "boss_hp" value 100 max 100 at 0, 18 size 182x6 anchor top color #AA0000 layer 0
	set progress of bar "boss_hp" for players in radius 30 of player to arg-1

command /hudreset:
	permission: 2
	clear hud for player
	show vanilla all for player` }
    ],
    notes: { ru: ["create hud \"группа\" for <цель>: — элементы с отступом. id имя нужно, чтобы потом менять элемент. layer < 0 рисуется под ванильным HUD.","Источники полосы: health, max_health, food, saturation, armor, air, max_air, xp, level, а также число, {переменная} и %плейсхолдер%.","direction: left_to_right, right_to_left, bottom_to_top, top_to_bottom. fill \"картинка\" или цвет, background цвет|\"картинка\"|none, text \"%value% %percent%%\".","Прячется ваниль: hotbar, health, food, armor, air, experience, mount_health, jump_bar, crosshair, effects, item_name, scoreboard, bossbar, hud, all.","Дальше по игре: set value|progress of bar \"имя\", set max of bar, set text of hud \"имя\", show hud, hide hud, remove hud, clear hud.","{переменная} в тексте, value или max обновляет HUD сама в момент записи. Пакет уходит только если строка изменилась. Источники health/food считает клиент, без пакетов.","/sc disable hud убирает группы этого скрипта и возвращает ванильные части, которые прятал только он."], en: ["create hud \"group\" for <target>: — elements indented below. id name is what you change later. layer < 0 draws under the vanilla HUD.","Bar sources: health, max_health, food, saturation, armor, air, max_air, xp, level, plus a number, a {variable} and a %placeholder%.","direction: left_to_right, right_to_left, bottom_to_top, top_to_bottom. fill is a picture or a color, background is a color, a picture or none, text can be \"%value% %percent%%\".","Vanilla parts you can hide: hotbar, health, food, armor, air, experience, mount_health, jump_bar, crosshair, effects, item_name, scoreboard, bossbar, hud, all.","Later in the game: set value|progress of bar \"name\", set max of bar, set text of hud \"name\", show hud, hide hud, remove hud, clear hud.","A {variable} in text, value or max updates the HUD when it is written. A packet is sent only if the text changed. health/food are read by the client, with no packets.","/sc disable hud removes this script's groups and restores vanilla parts that only it had hidden."] },
    related: ["hud-create", "hud-bar", "hud-bar-sources", "hud-hotbar", "hud-slot", "hud-layer", "hud-set-bar", "hud-set-text", "hud-show-hide", "hud-vanilla"]
  },
  {
    id: "custom-block",
    title: { ru: "Свой блок и предмет: все свойства", en: "A custom block and item: every property" },
    intro: { ru: "Блок и предмет example:ruby. Ресурсы лежат в scriptcraft/resources.", en: "The block and item example:ruby. Resources live in scriptcraft/resources." },
    blocks: [
      { lang: "sc", title: { ru: "scriptcraft/scripts/ruby.sc", en: "scriptcraft/scripts/ruby.sc" }, code: `# ==========================================================
#  ruby.sc — свои блоки и предметы. Копируется выключенным: /sc enable ruby
#  id только латиницей. Русское название — через name: или lang.
#  Ресурсы: scriptcraft/resources/assets/example/  (модели, текстуры, lang)
#  Дроп и теги: scriptcraft/resources/data/
#  Новый define виден сразу после /sc reload (временный слот).
#  Настоящий id example:ruby_block — после перезапуска сервера и клиента.
#  Технический id в F3 смотри в /sc ids. Выдать: /sc give или /ruby.
#
#  define block <id>:          все строки ниже необязательны
#    name: "текст"
#    hardness: 1.5
#    resistance: 6
#    sound: stone | wood | metal | glass | grass | sand | wool | gravel
#    light: 0..15
#    requires_tool: true | false
#    facing: none | horizontal | all
#        horizontal — 4 стороны, как печь; all — ещё вверх и вниз, как раздатчик
#        модель должна смотреть на север, поворот дописывает сам мод
#    render: solid | cutout | translucent
#        solid — обычный куб; cutout (или transparent) — дырки в png
#        translucent (или glass) — полупрозрачность
#    full: true | false
#        false — модель не на весь куб, пол под блоком остаётся виден
#
#  define item <id>:
#    name: "текст"
#    stack_size: 1..64
#    rarity: common | uncommon | rare | epic
#    fire_resistant: true | false
# ==========================================================

namespace example

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

# Фонарь нарочно закомментирован: для него нет своей png в examples/resources.
# Сними решётки и положи текстуру assets/example/textures/block/ruby_lantern.png,
# модель может быть не на весь куб (full: false, render: cutout).
# define block ruby_lantern:
# 	name: "Рубиновый фонарь"
# 	sound: glass
# 	light: 12
# 	facing: all
# 	render: cutout
# 	full: false

define item ruby:
	name: "Рубин"
	stack_size: 64
	rarity: rare
	fire_resistant: false

# define item ruby_shard:
# 	name: "Осколок рубина"
# 	stack_size: 16
# 	rarity: uncommon

on place:
	if "%block%" is "example:ruby_block":
		send "&cРубиновый блок поставлен. Скрипт: %script%" to player

command /ruby:
	permission: 2
	aliases: gems
	give player example:ruby_block 1
	give player example:ruby 8
	send "&cДержи рубины." to player` },
      { lang: "text", title: { ru: "Раскладка папки resources", en: "resources folder layout" }, code: `scriptcraft/resources/
  assets/example/
    blockstates/ruby_block.json
    models/block/ruby_block.json
    models/item/ruby_block.json
    models/item/ruby.json
    textures/block/ruby_block.png
    textures/item/ruby.png
    textures/gui/menu_banner.png
    lang/en_us.json
    lang/ru_ru.json
  data/
    example/loot_tables/blocks/ruby_block.json
    minecraft/tags/blocks/mineable/pickaxe.json` },
      { lang: "json", title: { ru: "assets/example/blockstates/ruby_block.json", en: "assets/example/blockstates/ruby_block.json" }, code: `{
  "variants": {
    "": { "model": "example:block/ruby_block" }
  }
}` },
      { lang: "json", title: { ru: "assets/example/lang/ru_ru.json", en: "assets/example/lang/ru_ru.json" }, code: `{
  "block.example.ruby_block": "Рубиновый блок",
  "item.example.ruby": "Рубин"
}` },
      { lang: "json", title: { ru: "data/example/loot_tables/blocks/ruby_block.json", en: "data/example/loot_tables/blocks/ruby_block.json" }, code: `{
  "type": "minecraft:block",
  "pools": [
    {
      "rolls": 1,
      "entries": [ { "type": "minecraft:item", "name": "example:ruby_block" } ],
      "conditions": [ { "condition": "minecraft:survives_explosion" } ]
    }
  ]
}` }
    ],
    notes: { ru: ["define block: name, hardness (1.5), resistance (6), sound stone|wood|metal|glass|grass|sand|wool|gravel, light 0–15, requires_tool true|false.","facing: none | horizontal (4 стороны, как печь) | all (ещё вверх и вниз, как раздатчик). Модель смотрит на север, поворот дописывает мод.","render: solid | cutout (дырки в png, синоним transparent) | translucent (стекло). full: false — модель не на весь куб, пол под блоком остаётся виден.","define item: name, stack_size 1–64, rarity common|uncommon|rare|epic, fire_resistant true|false.","id и имена файлов — только a-z 0-9 _ . -. Русское название — name: или lang.","В модели путь текстуры — namespace:block/имя, без textures/ и без .png. После правки assets: /sc reload resources, после data: /reload."], en: ["define block: name, hardness (1.5), resistance (6), sound stone|wood|metal|glass|grass|sand|wool|gravel, light 0–15, requires_tool true|false.","facing: none | horizontal (4 sides, like a furnace) | all (also up and down, like a dispenser). The model faces north; the mod writes the rotation.","render: solid | cutout (holes in the png, alias transparent) | translucent (glass). full: false — the model is not a full cube, so the floor under the block stays visible.","define item: name, stack_size 1–64, rarity common|uncommon|rare|epic, fire_resistant true|false.","ids and file names: only a-z 0-9 _ . -. A translated name goes in name: or lang.","A model texture path is namespace:block/name, with no textures/ and no .png. After editing assets: /sc reload resources; after data: /reload."] },
    related: ["struct-namespace", "struct-define-block", "struct-define-item", "cmd-reload", "eff-give-item", "event-place", "event-break"]
  },
  {
    id: "economy",
    title: { ru: "Экономика: переменные, шанс и перевод", en: "Economy: variables, chance and a transfer" },
    intro: { ru: "Баланс {coins::%player%} и перевод /pay.", en: "A {coins::%player%} balance and /pay." },
    blocks: [
      { lang: "sc", title: { ru: "фрагмент welcome.sc", en: "excerpt of welcome.sc" }, code: `# Эти команды уже есть в welcome.sc. Здесь — только баланс.
command /coins:
	aliases: bal, balance
	if {coins::%player%} is not set:
		set {coins::%player%} to 0
	send "&eМонет: &6%{coins::%player%}%" to player

command /work:
	aliases: job
	if {-worked::%player%} is set:
		send "&cТы уже работал. Отдых до перезапуска сервера." to player
	else:
		add 5 to {coins::%player%}
		set {-worked::%player%} to true
		chance of 25%:
			add 5 to {coins::%player%}
			send "&dПремия! Ещё +5." to player
		send "&a+5 монет. Сейчас: &6%{coins::%player%}%" to player

command /pay <player> <integer>:
	aliases: transfer
	if {coins::%player%} < arg-2:
		send "&cНе хватает монет. Баланс: %{coins::%player%}%" to player
	else:
		if arg-2 < 1:
			send "&cСумма должна быть больше нуля." to player
		else:
			remove arg-2 from {coins::%player%}
			add arg-2 to {coins::%arg-1%}
			send "&aПереведено &6%arg-2% &aмонет игроку &f%arg-1%" to player

command /resetcoins:
	permission: 2
	delete {coins::%player%}
	reply "Баланс сброшен"` }
    ],
    notes: { ru: ["add и remove считают незаданную переменную нулём. Текст в числовой переменной — ошибка выполнения.","else if, and и or есть: and связывается теснее. Вложенный if тоже годится.","chance of 25%: — случайная ветка. В имени переменной %arg-1% подставляется, поэтому {coins::%arg-1%} — баланс получателя."], en: ["add and remove treat an unset variable as 0. Text in a numeric variable is a runtime error.","else if, and and or exist: and binds tighter. A nested if is fine too.","chance of 25%: is a random branch. %arg-1% is expanded inside a variable name, so {coins::%arg-1%} is the recipient's balance."] },
    related: ["expr-variable", "expr-temp-variable", "eff-set-var", "eff-add-var", "struct-if", "struct-chance", "expr-argument", "cond-number"]
  },
  {
    id: "heal-radius",
    title: { ru: "Цели, взгляд и цикл", en: "Targets, looking and a loop" },
    intro: { ru: "Цель — набор игроков. Пустой набор ничего не делает.", en: "A target is a set of players. An empty set does nothing." },
    blocks: [
      { lang: "sc", title: { ru: "фрагмент welcome.sc", en: "excerpt of welcome.sc" }, code: `# Эти команды уже есть в welcome.sc.
command /look:
	if targeted player is set:
		send "&eТы смотришь на &6%targeted%" to player
		send action bar "&c%player% смотрит на тебя" to targeted player
	else:
		send "&7Перед тобой никого нет" to player

command /gather:
	permission: 2
	teleport other players in radius 80 of player to player
	loop all players:
		send "&e%loop-player%, сбор у %player%" to loop-player
	play sound entity.player.levelup to @a

command /nearbuff:
	permission: 2
	give @a[distance=..10] effect regeneration for 10 seconds
	send title "&aРегенерация" to @a[distance=..10]` }
    ],
    notes: { ru: ["players in radius N of player включает самого игрока, other players — нет.","Плейсхолдеры считаются от вызвавшего. Имя получателя — loop и %loop-player%.","Селектор @a[...] работает, только если написан прямо в скрипте. Текст из аргумента всегда имя."], en: ["players in radius N of player includes that player, other players does not.","Placeholders are evaluated for the caller. The recipient's name is loop and %loop-player%.","A selector @a[...] only works when it is written in the script. Text from an argument is always a name."] },
    related: ["target-radius", "target-targeted", "target-selector", "struct-loop", "cond-target-set", "ph-targeted"]
  },
  {
    id: "effect-commands",
    title: { ru: "Эффект-команды в чате", en: "Chat effect commands" },
    intro: { ru: "Строка чата с ? выполняется как скрипт. По умолчанию выключено.", en: "A chat line starting with ? runs as a script. Off by default." },
    blocks: [
      { lang: "toml", title: { ru: "scriptcraft/config.toml", en: "scriptcraft/config.toml" }, code: `[effectCommands]
    enableEffectCommands = true
    effectCommandToken = "?"
    effectCommandsRequireOp = true` },
      { lang: "sc", title: { ru: "В чате игры", en: "In the game chat" }, code: `?heal player
?set player's gamemode to creative
?give player effect night_vision for 600 seconds
?send title "Тест" to "Steve"
?heal players in radius 10 of player
?send "привет" to targeted player
?give @a[distance=..20] diamond 1
?set {coins::Steve} to 100` }
    ],
    notes: { ru: ["Право — scriptcraft.effectcommands (Forge PermissionAPI). Без мода прав и при effectCommandsRequireOp = true — только операторы.","execute console command из чата доступно только операторам уровня 2.","if, loop и open screen в одной строке не работают. Сообщение с токеном в общий чат не уходит."], en: ["The permission is scriptcraft.effectcommands (Forge PermissionAPI). Without a permissions mod, and with effectCommandsRequireOp = true, only operators can use it.","execute console command from chat is operator level 2 only.","if, loop and open screen do not work on one line. A message with the token never reaches public chat."] },
    related: ["cmd-effect-commands", "cfg-enableEffectCommands", "cfg-effectCommandToken", "cfg-effectCommandsRequireOp"]
  },
  {
    id: "features-02",
    title: { ru: "Чат, кубик, пауза и таймер", en: "Chat, a die, a pause and a timer" },
    intro: { ru: "every без игрока: внутри используйте loop.", en: "every has no player: use loop inside." },
    blocks: [
      { lang: "sc", title: { ru: "scriptcraft/scripts/features.sc", en: "scriptcraft/scripts/features.sc" }, code: `on first join:
	give player bread 16
	send "&aДобро пожаловать, %player%!" to player

on chat:
	if %message% is "hp":
		cancel event
		set {hp} to health of player
		set {max} to max health of player
		send "&cЗдоровье: %{hp}% / %{max}%" to player

command /roll:
	set {roll} to random integer between 1 and 6
	if {roll} is 6:
		send "&aШестёрка!" to player
		give 2 levels to player
	else if {roll} >= 4:
		send "&eВыпало %{roll}%" to player
	else:
		send "&7Выпало %{roll}%" to player
	wait 1 second
	send "&7Бросок учтён" to player

every 10 seconds:
	loop all players:
		if health of loop-player < 6 and loop-player is not in creative:
			heal loop-player by 2` }
    ],
    notes: { ru: ["wait откладывает только строки ниже на том же отступе и не дольше 5 минут.","cancel event в чате не даёт сообщению появиться у других.","random integer between 1 and 6 — целое, обе границы входят."], en: ["wait delays only the lines below it at the same indent, and not for longer than 5 minutes.","cancel event in chat keeps the message from appearing for others.","random integer between 1 and 6 is a whole number, both ends included."] },
    related: ["event-first-join", "event-chat", "expr-property", "expr-random", "struct-every", "struct-wait", "eff-cancel", "cond-and"]
  },
  {
    id: "bag",
    title: { ru: "Сумка", en: "Bag" },
    intro: { ru: "Любой полученный предмет уходит в сумку. E открывает её. Включить: /sc enable bag.", en: "Every item you receive goes into the bag. E opens it. Enable: /sc enable bag." },
    blocks: [
      { lang: "sc", title: { ru: "scriptcraft/scripts/-bag.sc", en: "scriptcraft/scripts/-bag.sc" }, code: `# bag.sc копируется выключенным. Включить: /sc enable bag
# E открывает сумку. Хотбар остаётся ванильным.

on receive:
	if {-handing::%player%} is %item%:
		delete {-handing::%player%}
		stop
	cancel event
	add %amount% to {bag::%player%::%item%}

on open inventory:
	cancel event
	open screen "Сумка" to player:
		text "&6Сумка" at 0, -90
		list from {bag::%player%::*} vertical at 0, 0 size 180x160 header "&7Предмет" select:
			row "&f%loop-index% &8x%loop-value%" run "/bag take %loop-index%"
		button "Закрыть" at 0, 100 size 80x20 close

command /bag take <text>:
	permission: 0
	if {bag::%player%::%arg-1%} >= 1:
		set {-handing::%player%} to arg-1
		remove 1 from {bag::%player%::%arg-1%}
		if {bag::%player%::%arg-1%} < 1:
			delete {bag::%player%::%arg-1%}
		give player %arg-1% 1
	else:
		send "&cПусто" to player` }
    ],
    notes: { ru: ["E закрывается всегда, даже если cancel event стоит внутри if. Хотбар остаётся.", "Числа на экране — снимок на момент открытия.", "{-handing} живёт до следующего получения, чтобы give не вернулся в сумку."], en: ["E is always blocked, even if cancel event sits inside if. The hotbar stays.", "The numbers are a snapshot from when the screen opened.", "{-handing} lasts until the next receive, so the give is not put back into the bag."] },
    related: ["event-receive", "event-open", "screen-list", "expr-variable", "ph-loop-index", "eff-give-item", "struct-loop"]
  },
  {
    id: "snake",
    title: { ru: "Ультимативная Змейка в GUI + Топ-10", en: "Ultimate Snake in GUI + Top-10" },
    intro: {
      ru: "Полноценная аркадная Змейка внутри кастомного окна: управление WASD и стрелками без закрытия GUI, динамическое добавление и удаление сегментов хвоста на лету, умный спавн яблок и бонусов, режимы скорости и портальных стен, а также постоянный топ-10 лидерборд. Включить: /sc enable snake, запуск: /snake.",
      en: "A full arcade Snake game inside a custom GUI window: WASD and arrow keys without closing the GUI, dynamic tail segment creation/removal on the fly, collision-safe apple & bonus spawning, speed and portal-wall modes, and a persistent Top-10 leaderboard. Enable with /sc enable snake, play with /snake."
    },
    blocks: [
      { lang: "sc", title: { ru: "scriptcraft/scripts/snake.sc", en: "scriptcraft/scripts/snake.sc" }, code: `# ==========================================================
#  snake.sc — Ультимативная ЗМЕЙКА в GUI с топ-10 лидербордом (/snake)
#  Копируется выключенным. Включить: /sc enable snake
#  Демонстрирует:
#    - open screen / update screen (добавление и удаление сегментов хвоста на лету)
#    - move screen / set text of screen / set value of screen bar / show & hide screen
#    - keep open на кнопках + on key ... in screen "Змейка": (WASD, стрелки, Space, R)
#    - on screen close "Змейка": и условие if loop-player has screen "Змейка" open:
#    - вложенные переменные {sx::%p%::%{-idx}%} и постоянный топ-10 лидерборд
# ==========================================================

command /snake:
	aliases: zmeyka, arcade
	if player has screen "Змейка" open:
		SnakeRestart(player)
	else:
		SnakeStart(player)

# ----------------------------------------------------------
# Запуск и сброс
# ----------------------------------------------------------

function SnakeResetData(p: player):
	if {snake_best::%p%} is not set:
		set {snake_best::%p%} to 0
	if {-snake_speed::%p%} is not set:
		set {-snake_speed::%p%} to 2
	if {-snake_walls::%p%} is not set:
		set {-snake_walls::%p%} to "portal"

	set {-snake_score::%p%} to 0
	set {-snake_len::%p%} to 3
	set {-snake_dir::%p%} to "right"
	set {-snake_next::%p%} to "right"
	set {-snake_state::%p%} to "play"
	set {-snake_tick::%p%} to 0
	set {-snake_bonus_t::%p%} to 0

	set {-hx::%p%} to 0
	set {-hy::%p%} to 0

	set {-sx::%p%::1} to -12
	set {-sy::%p%::1} to 0
	set {-sx::%p%::2} to -24
	set {-sy::%p%::2} to 0
	set {-sx::%p%::3} to -36
	set {-sy::%p%::3} to 0

	set {-ax::%p%} to 36
	set {-ay::%p%} to 0
	set {-bx::%p%} to 0
	set {-by::%p%} to -36

function SnakeStart(p: player):
	SnakeResetData(p)

	open screen "Змейка" to p:
		bar "board_border" value 100 max 100 at 0, 0 size 186x146 color #1E293B background #E60B0F17 layer 0
		bar "board_inner" value 100 max 100 at 0, 0 size 182x142 color #0F172A background #F20F172A layer 1

		text "&a&lSNAKE ARCADE" at 0, -102 color #4ADE80 shadow layer 4 id title_lbl
		text "&fСчёт: &a0  &8|  &7Рекорд: &6%{snake_best::%p%}%  &8|  &7Длина: &b4" at 0, -90 color #FFFFFF shadow layer 4 id score_lbl
		bar "bonus_bar" value 0 max 40 at 0, -78 size 182x5 color #FACC15 background #55000000 layer 3

		slot minecraft:apple 1 at 36, 0 size 12x12 no frame layer 2 id apple
		slot minecraft:enchanted_golden_apple 1 at 0, -36 size 12x12 no frame layer 2 id bonus hidden
		slot minecraft:emerald_block 1 at 0, 0 size 12x12 no frame layer 4 id head
		slot minecraft:lime_concrete 1 at -12, 0 size 12x12 no frame layer 3 id seg_1
		slot minecraft:lime_concrete 1 at -24, 0 size 12x12 no frame layer 3 id seg_2
		slot minecraft:lime_concrete 1 at -36, 0 size 12x12 no frame layer 3 id seg_3

		text "&e&lПАУЗА" at 0, 0 color #FACC15 shadow layer 6 id status_banner hidden

		text "&7Управление" at -132, -52 color #94A3B8 shadow layer 4
		button "▲" at -132, -32 size 24x20 run SnakeDir(player, "up") keep open layer 4 id btn_up
		button "◄" at -158, -10 size 24x20 run SnakeDir(player, "left") keep open layer 4 id btn_left
		button "►" at -106, -10 size 24x20 run SnakeDir(player, "right") keep open layer 4 id btn_right
		button "▼" at -132, 12 size 24x20 run SnakeDir(player, "down") keep open layer 4 id btn_down
		button "Пауза [Space]" at -132, 40 size 78x18 run SnakeTogglePause(player) keep open layer 4 id btn_pause

		text "&7Режимы" at 136, -52 color #94A3B8 shadow layer 4
		button "Скорость: Норма" at 136, -32 size 88x18 run SnakeCycleSpeed(player) keep open layer 4 id btn_speed
		button "Стены: Портал" at 136, -10 size 88x18 run SnakeToggleWalls(player) keep open layer 4 id btn_walls
		button "Заново [R]" at 136, 18 size 88x18 run SnakeRestart(player) keep open layer 4 id btn_restart
		button "🏆 Лидерборд" at 136, 40 size 88x18 run SnakeOpenLeaderboard(player) keep open layer 4 id btn_leaderboard
		button "Выход" at 136, 62 size 88x18 close layer 4

		text "&8WASD / Стрелки — ход   |   Space — пауза   |   R — рестарт" at 0, 84 color #64748B layer 4

	SnakeRefreshModeButtons(p)
	play sound block.note_block.pling to p with volume 0.7 pitch 1.4

function SnakeRestart(p: player):
	if {-snake_len::%p%} is set:
		loop {-snake_len::%p%} times:
			if %loop-number% > 3:
				remove screen "seg_%loop-number%" from p

	SnakeResetData(p)

	set position of screen "head" for p to 0 0
	set position of screen "seg_1" for p to -12 0
	set position of screen "seg_2" for p to -24 0
	set position of screen "seg_3" for p to -36 0
	set position of screen "apple" for p to 36 0
	hide screen "bonus" for p
	set value of screen bar "bonus_bar" for p to 0
	hide screen "status_banner" for p
	SnakeUpdateHud(p)
	SnakeRefreshModeButtons(p)
	play sound block.note_block.pling to p with volume 0.7 pitch 1.4

# ----------------------------------------------------------
# Кнопки и управление
# ----------------------------------------------------------

function SnakeRefreshModeButtons(p: player):
	if {-snake_speed::%p%} is 3:
		set text of screen "btn_speed" for p to "&aСкорость: Лайт"
	else if {-snake_speed::%p%} is 2:
		set text of screen "btn_speed" for p to "&eСкорость: Норма"
	else:
		set text of screen "btn_speed" for p to "&cСкорость: Турбо"

	if {-snake_walls::%p%} is "portal":
		set text of screen "btn_walls" for p to "&bСтены: Портал"
	else:
		set text of screen "btn_walls" for p to "&cСтены: Смерть"

function SnakeCycleSpeed(p: player):
	if {-snake_speed::%p%} is 3:
		set {-snake_speed::%p%} to 2
	else if {-snake_speed::%p%} is 2:
		set {-snake_speed::%p%} to 1
	else:
		set {-snake_speed::%p%} to 3

	SnakeRefreshModeButtons(p)
	play sound ui.button.click to p with volume 0.6 pitch 1.2

function SnakeToggleWalls(p: player):
	if {-snake_walls::%p%} is "portal":
		set {-snake_walls::%p%} to "solid"
	else:
		set {-snake_walls::%p%} to "portal"

	SnakeRefreshModeButtons(p)
	play sound ui.button.click to p with volume 0.6 pitch 1.1

function SnakeTogglePause(p: player):
	if {-snake_state::%p%} is "over":
		SnakeRestart(p)
		stop

	if {-snake_state::%p%} is "play":
		set {-snake_state::%p%} to "pause"
		set text of screen "status_banner" for p to "&e&lПАУЗА &7(Space — продолжить)"
		show screen "status_banner" for p
		play sound block.note_block.bass to p with volume 0.6 pitch 0.9
	else:
		set {-snake_state::%p%} to "play"
		hide screen "status_banner" for p
		play sound block.note_block.pling to p with volume 0.6 pitch 1.3

function SnakeDir(p: player, dir: text):
	if {-snake_state::%p%} is not "play":
		stop

	if dir is "up":
		if {-snake_dir::%p%} is not "down":
			set {-snake_next::%p%} to "up"
	else if dir is "down":
		if {-snake_dir::%p%} is not "up":
			set {-snake_next::%p%} to "down"
	else if dir is "left":
		if {-snake_dir::%p%} is not "right":
			set {-snake_next::%p%} to "left"
	else if dir is "right":
		if {-snake_dir::%p%} is not "left":
			set {-snake_next::%p%} to "right"

on key up in screen "Змейка":
	SnakeDir(player, "up")
on key down in screen "Змейка":
	SnakeDir(player, "down")
on key left in screen "Змейка":
	SnakeDir(player, "left")
on key right in screen "Змейка":
	SnakeDir(player, "right")

on key w in screen "Змейка":
	SnakeDir(player, "up")
on key s in screen "Змейка":
	SnakeDir(player, "down")
on key a in screen "Змейка":
	SnakeDir(player, "left")
on key d in screen "Змейка":
	SnakeDir(player, "right")

on key space in screen "Змейка":
	SnakeTogglePause(player)

on key r in screen "Змейка":
	SnakeRestart(player)

on screen close "Змейка":
	set {-snake_state::%player%} to "closed"

# ----------------------------------------------------------
# Спавн еды и бонуса
# ----------------------------------------------------------

function SnakeSpawnApple(p: player):
	set {-snake-found::%p%} to false

	loop 100 times:
		if {-snake-found::%p%} is false:
			set {-snake-try-x::%p%} to random integer between -7 and 7
			set {-snake-try-y::%p%} to random integer between -5 and 5
			set {-snake-try-x::%p%} to {-snake-try-x::%p%} * 12
			set {-snake-try-y::%p%} to {-snake-try-y::%p%} * 12
			set {-snake-free::%p%} to true

			if {-snake-try-x::%p%} is {-hx::%p%}:
				if {-snake-try-y::%p%} is {-hy::%p%}:
					set {-snake-free::%p%} to false

			loop {-snake_len::%p%} times:
				if {-snake-try-x::%p%} is {-sx::%p%::%loop-number%}:
					if {-snake-try-y::%p%} is {-sy::%p%::%loop-number%}:
						set {-snake-free::%p%} to false

			if {-snake_bonus_t::%p%} > 0:
				if {-snake-try-x::%p%} is {-bx::%p%}:
					if {-snake-try-y::%p%} is {-by::%p%}:
						set {-snake-free::%p%} to false

			if {-snake-free::%p%} is true:
				set {-ax::%p%} to {-snake-try-x::%p%}
				set {-ay::%p%} to {-snake-try-y::%p%}
				set {-snake-found::%p%} to true

	if {-snake-found::%p%} is true:
		move screen "apple" for p to {-ax::%p%} {-ay::%p%}
	else:
		SnakeGameOver(p, "поле заполнено")

function SnakeSpawnBonus(p: player):
	set {-snake-bonus-found::%p%} to false

	loop 100 times:
		if {-snake-bonus-found::%p%} is false:
			set {-snake-try-x::%p%} to random integer between -7 and 7
			set {-snake-try-y::%p%} to random integer between -5 and 5
			set {-snake-try-x::%p%} to {-snake-try-x::%p%} * 12
			set {-snake-try-y::%p%} to {-snake-try-y::%p%} * 12
			set {-snake-free::%p%} to true

			if {-snake-try-x::%p%} is {-hx::%p%}:
				if {-snake-try-y::%p%} is {-hy::%p%}:
					set {-snake-free::%p%} to false

			if {-snake-try-x::%p%} is {-ax::%p%}:
				if {-snake-try-y::%p%} is {-ay::%p%}:
					set {-snake-free::%p%} to false

			loop {-snake_len::%p%} times:
				if {-snake-try-x::%p%} is {-sx::%p%::%loop-number%}:
					if {-snake-try-y::%p%} is {-sy::%p%::%loop-number%}:
						set {-snake-free::%p%} to false

			if {-snake-free::%p%} is true:
				set {-bx::%p%} to {-snake-try-x::%p%}
				set {-by::%p%} to {-snake-try-y::%p%}
				set {-snake-bonus-found::%p%} to true

	if {-snake-bonus-found::%p%} is true:
		set {-snake_bonus_t::%p%} to 40
		move screen "bonus" for p to {-bx::%p%} {-by::%p%}
		show screen "bonus" for p
		set value of screen bar "bonus_bar" for p to 40
		play sound block.amethyst_block.chime to p with volume 0.9 pitch 1.3

# ----------------------------------------------------------
# Интерфейс и лидерборд
# ----------------------------------------------------------

function SnakeUpdateHud(p: player):
	set {-snake-full-len::%p%} to {-snake_len::%p%} + 1
	set text of screen "score_lbl" for p to "&fСчёт: &a%{-snake_score::%p%}%  &8|  &7Рекорд: &6%{snake_best::%p%}%  &8|  &7Длина: &b%{-snake-full-len::%p%}%"

function SnakeGameOver(p: player, reason: text):
	SnakeSaveBest(p)
	set {-snake_state::%p%} to "over"
	set text of screen "status_banner" for p to "&c&lИГРА ОКОНЧЕНА! &7(%reason% — R или Space)"
	show screen "status_banner" for p
	play sound entity.villager.no to p with volume 0.8 pitch 0.9

function SnakeSaveBest(p: player):
	if {snake_best::%p%} is not set:
		set {snake_best::%p%} to 0

	if {-snake_score::%p%} > {snake_best::%p%}:
		set {snake_best::%p%} to {-snake_score::%p%}

	# Обновляем постоянный общий топ-10.
	set {-snake-rank-name::%p%} to name of p
	set {-snake-rank-score::%p%} to {snake_best::%p%}

	# Инициализируем пустые строки и убираем старую запись игрока, чтобы не было дублей.
	loop 10 times:
		if {snake_leader_score::%loop-number%} is not set:
			set {snake_leader_name::%loop-number%} to "—"
			set {snake_leader_score::%loop-number%} to 0
		if {snake_leader_name::%loop-number%} is {-snake-rank-name::%p%}:
			set {snake_leader_name::%loop-number%} to "—"
			set {snake_leader_score::%loop-number%} to 0

	# Вставляем рекорд на правильное место, сдвигая записи ниже.
	loop 10 times:
		if {-snake-rank-score::%p%} >= {snake_leader_score::%loop-number%}:
			set {-snake-swap-score::%p%} to {snake_leader_score::%loop-number%}
			set {-snake-swap-name::%p%} to {snake_leader_name::%loop-number%}
			set {snake_leader_score::%loop-number%} to {-snake-rank-score::%p%}
			set {snake_leader_name::%loop-number%} to {-snake-rank-name::%p%}
			set {-snake-rank-score::%p%} to {-snake-swap-score::%p%}
			set {-snake-rank-name::%p%} to {-snake-swap-name::%p%}

function SnakeOpenLeaderboard(p: player):
	loop 10 times:
		if {snake_leader_name::%loop-number%} is not set:
			set {snake_leader_name::%loop-number%} to "—"
			set {snake_leader_score::%loop-number%} to 0

	open screen "Змейка | Лидерборд" to p:
		bar "leader_bg" value 100 max 100 at 0, 0 size 190x166 color #0F172A background #F20B0F17 layer 0
		text "&6&l🏆 ЛИДЕРБОРД SNAKE" at 0, -72 color #FACC15 shadow layer 3
		text "&e#1 &f%{snake_leader_name::1}% &8— &a%{snake_leader_score::1}%" at 0, -48 color #FFFFFF layer 2
		text "&e#2 &f%{snake_leader_name::2}% &8— &a%{snake_leader_score::2}%" at 0, -34 color #FFFFFF layer 2
		text "&6#3 &f%{snake_leader_name::3}% &8— &a%{snake_leader_score::3}%" at 0, -20 color #FFFFFF layer 2
		text "&7#4 &f%{snake_leader_name::4}% &8— &a%{snake_leader_score::4}%" at 0, -6 color #FFFFFF layer 2
		text "&7#5 &f%{snake_leader_name::5}% &8— &a%{snake_leader_score::5}%" at 0, 8 color #FFFFFF layer 2
		text "&7#6 &f%{snake_leader_name::6}% &8— &a%{snake_leader_score::6}%" at 0, 22 color #FFFFFF layer 2
		text "&7#7 &f%{snake_leader_name::7}% &8— &a%{snake_leader_score::7}%" at 0, 36 color #FFFFFF layer 2
		text "&7#8 &f%{snake_leader_name::8}% &8— &a%{snake_leader_score::8}%" at 0, 50 color #FFFFFF layer 2
		text "&7#9 &f%{snake_leader_name::9}% &8— &a%{snake_leader_score::9}%" at 0, 64 color #FFFFFF layer 2
		text "&7#10 &f%{snake_leader_name::10}% &8— &a%{snake_leader_score::10}%" at 0, 78 color #FFFFFF layer 2
		button "&aНазад в игру" at 0, 104 size 100x20 run SnakeStart(player) layer 4

# ----------------------------------------------------------
# Один игровой шаг
# ----------------------------------------------------------

function SnakeStep(p: player):
	if {-snake_state::%p%} is not "play":
		stop

	set {-snake_dir::%p%} to {-snake_next::%p%}
	set {-snake-old-x::%p%} to {-hx::%p%}
	set {-snake-old-y::%p%} to {-hy::%p%}
	set {-snake-new-x::%p%} to {-snake-old-x::%p%}
	set {-snake-new-y::%p%} to {-snake-old-y::%p%}

	if {-snake_dir::%p%} is "up":
		set {-snake-new-y::%p%} to {-snake-old-y::%p%} - 12
	else if {-snake_dir::%p%} is "down":
		set {-snake-new-y::%p%} to {-snake-old-y::%p%} + 12
	else if {-snake_dir::%p%} is "left":
		set {-snake-new-x::%p%} to {-snake-old-x::%p%} - 12
	else:
		set {-snake-new-x::%p%} to {-snake-old-x::%p%} + 12

	# Стены или переход на противоположную сторону.
	if {-snake_walls::%p%} is "portal":
		if {-snake-new-x::%p%} > 84:
			set {-snake-new-x::%p%} to -84
		else if {-snake-new-x::%p%} < -84:
			set {-snake-new-x::%p%} to 84

		if {-snake-new-y::%p%} > 60:
			set {-snake-new-y::%p%} to -60
		else if {-snake-new-y::%p%} < -60:
			set {-snake-new-y::%p%} to 60
	else:
		if {-snake-new-x::%p%} > 84:
			SnakeGameOver(p, "стена")
			stop
		if {-snake-new-x::%p%} < -84:
			SnakeGameOver(p, "стена")
			stop
		if {-snake-new-y::%p%} > 60:
			SnakeGameOver(p, "стена")
			stop
		if {-snake-new-y::%p%} < -60:
			SnakeGameOver(p, "стена")
			stop

	# Проверяем, попала ли голова на обычное или бонусное яблоко.
	set {-snake-eat::%p%} to false
	set {-snake-eat-bonus::%p%} to false

	if {-snake-new-x::%p%} is {-ax::%p%}:
		if {-snake-new-y::%p%} is {-ay::%p%}:
			set {-snake-eat::%p%} to true

	if {-snake_bonus_t::%p%} > 0:
		if {-snake-new-x::%p%} is {-bx::%p%}:
			if {-snake-new-y::%p%} is {-by::%p%}:
				set {-snake-eat-bonus::%p%} to true

	# Столкновение с телом.
	set {-snake-hit::%p%} to false
	loop {-snake_len::%p%} times:
		if {-snake-new-x::%p%} is {-sx::%p%::%loop-number%}:
			if {-snake-new-y::%p%} is {-sy::%p%::%loop-number%}:
				if {-snake-eat::%p%} is true:
					set {-snake-hit::%p%} to true
				else:
					if {-snake-eat-bonus::%p%} is true:
						set {-snake-hit::%p%} to true
					else:
						if %loop-number% is not {-snake_len::%p%}:
							set {-snake-hit::%p%} to true

	if {-snake-hit::%p%} is true:
		SnakeGameOver(p, "укусил себя")
		stop

	# Сохраняем старую позицию последнего сегмента для роста.
	loop {-snake_len::%p%} times:
		if %loop-number% is {-snake_len::%p%}:
			set {-snake-tail-x::%p%} to {-sx::%p%::%loop-number%}
			set {-snake-tail-y::%p%} to {-sy::%p%::%loop-number%}

	# Если съели яблоко — увеличиваем длину и создаём новый сегмент.
	if {-snake-eat::%p%} is true:
		add 1 to {-snake_len::%p%}
		set {-snake-new-id::%p%} to {-snake_len::%p%}

		update screen "Змейка" for p:
			slot minecraft:lime_concrete 1 at 0, 0 size 12x12 no frame layer 3 id seg_%{-snake-new-id::%p%}%

		move screen "seg_%{-snake-new-id::%p%}%" for p to {-snake-tail-x::%p%} {-snake-tail-y::%p%}

	# Сдвигаем тело от хвоста к голове.
	set {-snake-shift-count::%p%} to {-snake_len::%p%} - 1
	loop {-snake-shift-count::%p%} times:
		set {-snake-index::%p%} to {-snake_len::%p%} - %loop-number% + 1
		set {-snake-prev-index::%p%} to {-snake-index::%p%} - 1
		set {-sx::%p%::%{-snake-index::%p%}%} to {-sx::%p%::%{-snake-prev-index::%p%}%}
		set {-sy::%p%::%{-snake-index::%p%}%} to {-sy::%p%::%{-snake-prev-index::%p%}%}

	set {-sx::%p%::1} to {-snake-old-x::%p%}
	set {-sy::%p%::1} to {-snake-old-y::%p%}

	# Перемещаем голову и сегменты на экране.
	set {-hx::%p%} to {-snake-new-x::%p%}
	set {-hy::%p%} to {-snake-new-y::%p%}
	move screen "head" for p to {-hx::%p%} {-hy::%p%}

	loop {-snake_len::%p%} times:
		move screen "seg_%loop-number%" for p to {-sx::%p%::%loop-number%} {-sy::%p%::%loop-number%}

	# Обычное яблоко: +10 очков и новый сегмент.
	if {-snake-eat::%p%} is true:
		add 10 to {-snake_score::%p%}
		SnakeSaveBest(p)
		SnakeSpawnApple(p)
		play sound entity.experience_orb.pickup to p with volume 0.7 pitch 1.3

		if {-snake_bonus_t::%p%} is 0:
			chance of 35%:
				SnakeSpawnBonus(p)

	# Бонусное золотое яблоко: +30 очков.
	if {-snake-eat-bonus::%p%} is true:
		set {-snake_bonus_t::%p%} to 0
		hide screen "bonus" for p
		set value of screen bar "bonus_bar" for p to 0
		add 30 to {-snake_score::%p%}
		SnakeSaveBest(p)
		play sound entity.player.levelup to p with volume 0.7 pitch 1.5

	# Таймер бонуса уменьшается каждый игровой ход.
	if {-snake_bonus_t::%p%} > 0:
		remove 1 from {-snake_bonus_t::%p%}
		set value of screen bar "bonus_bar" for p to {-snake_bonus_t::%p%}
		if {-snake_bonus_t::%p%} is 0:
			hide screen "bonus" for p

	SnakeUpdateHud(p)

# ----------------------------------------------------------
# Игровой цикл и очистка
# ----------------------------------------------------------

every 2 ticks:
	loop all players:
		if loop-player has screen "Змейка" open:
			if {-snake_state::%loop-player%} is "play":
				add 1 to {-snake_tick::%loop-player%}
				if {-snake_tick::%loop-player%} >= {-snake_speed::%loop-player%}:
					set {-snake_tick::%loop-player%} to 0
					SnakeStep(loop-player)

on quit:
	delete {-snake_state::%player%}
	delete {-snake_score::%player%}
	delete {-snake_len::%player%}
	delete {-snake_tick::%player%}` }
    ],
    notes: {
      ru: [
        "Для управления не нужны отдельные команды — все кнопки вызывают функции напрямую через run SnakeDir(player, ...) с keep open, а клавиатура (WASD, стрелки, Space, R) обрабатывается через on key ... in screen \"Змейка\": независимо от раскладки.",
        "При росте змейки новые сегменты хвоста добавляются на лету через update screen, а при рестарте удаляются через remove screen без пересоздания окна.",
        "Топ-10 рекордов хранится в постоянных переменных {snake_leader_name::1..10} и {snake_leader_score::1..10} и открывается отдельной кнопкой прямо из игры."
      ],
      en: [
        "No extra commands are needed — buttons call functions directly via run SnakeDir(player, ...) with keep open, while WASD, arrows, Space and R are handled via on key ... in screen \"Змейка\": regardless of OS keyboard layout.",
        "New tail segments are appended dynamically via update screen and cleaned up on restart via remove screen without recreating the GUI window.",
        "The Top-10 leaderboard is stored in persistent variables {snake_leader_name::1..10} and {snake_leader_score::1..10} and opened straight from the game UI."
      ]
    },
    related: ["eff-open-screen", "screen-dynamic", "screen-button", "event-key", "event-screen-open", "struct-function"]
  },
  {
    id: "dino",
    title: { ru: "Динозаврик (Chrome Dino) в GUI", en: "Chrome Dino Runner in GUI" },
    intro: {
      ru: "Классическая аркада с динозавриком прямо в окне Minecraft: прыжки, приседание под птеродактилями, смена дня и ночи каждые 500 очков, параллакс-облака, кастомные пиксельные текстуры и Топ-5 сервера. Запуск: /dino.",
      en: "Classic Chrome Dino Runner inside a Minecraft GUI: jumping, ducking under pterodactyls, day/night cycle every 500 points, parallax clouds, custom pixel-art textures, and Top-5 server leaderboard. Run: /dino."
    },
    downloads: [
      {
        href: "assets/dino-resources.zip",
        label: { ru: "Скачать текстуры (dino-resources.zip)", en: "Download textures (dino-resources.zip)" },
        desc: { ru: "15 PNG-спрайтов (assets/dino/textures/gui/) — распакуйте в scriptcraft/resources/", en: "15 PNG sprites (assets/dino/textures/gui/) — extract into scriptcraft/resources/" }
      }
    ],
    blocks: [
      { lang: "sc", title: { ru: "scriptcraft/scripts/dino.sc", en: "scriptcraft/scripts/dino.sc" }, code: `# ==========================================================
#  dino.sc — Chrome Dino Runner в GUI ScriptCraft
#  Команда: /dino
#  Управление:
#    Space / W / Up  — прыжок (или рестарт после Game Over)
#    S / Down        — присесть (уклонение от птеродактиля) / быстрое падение
#    P               — пауза
#    R               — перезапуск
#  Текстуры: скопируйте папку assets/dino/textures/gui/ в scriptcraft/resources/
# ==========================================================

on load:
	clear {-dino-active::*}

on quit:
	DinoCleanup(player)

on screen close "Dino":
	DinoCleanup(player)

command /dino:
	DinoStart(player)

# --- Управление с клавиатуры внутри экрана "Dino" ---

on key space in screen "Dino":
	DinoJumpOrRestart(player)

on key w in screen "Dino":
	DinoJumpOrRestart(player)

on key up in screen "Dino":
	DinoJumpOrRestart(player)

on key s in screen "Dino":
	DinoSetDuck(player, true)

on key down in screen "Dino":
	DinoSetDuck(player, true)

on key release s in screen "Dino":
	DinoSetDuck(player, false)

on key release down in screen "Dino":
	DinoSetDuck(player, false)

on key p in screen "Dino":
	DinoTogglePause(player)

on key r in screen "Dino":
	DinoStart(player)

# --- Игровой цикл (20 FPS) ---

every 1 tick:
	loop all players:
		if {-dino-active::%loop-player%} is 1:
			if loop-player has screen "Dino" open:
				DinoTick(loop-player)
			else:
				DinoCleanup(loop-player)

# --- Основные функции ---

function DinoCleanup(p: player):
	delete {-dino-active::%p%}
	delete {-dino-dead::%p%}
	delete {-dino-paused::%p%}
	delete {-dino-duck::%p%}

function DinoStart(p: player):
	set {-dino-active::%p%} to 1
	set {-dino-dead::%p%} to 0
	set {-dino-paused::%p%} to 0
	set {-dino-duck::%p%} to 0
	set {-dino-score::%p%} to 0
	set {-dino-tick::%p%} to 0
	set {-dino-speed::%p%} to 4.2
	set {-dino-night::%p%} to 0
	set {-dino-y::%p%} to 34
	set {-dino-vy::%p%} to 0
	set {-dino-cloud1::%p%} to 110
	set {-dino-cloud2::%p%} to -40
	set {-dino-spawn-cd::%p%} to 18
	set {-dino-obs-s-on::%p%} to 0
	set {-dino-obs-s-x::%p%} to 180
	set {-dino-obs-t-on::%p%} to 0
	set {-dino-obs-t-x::%p%} to 180
	set {-dino-obs-d-on::%p%} to 0
	set {-dino-obs-d-x::%p%} to 180
	set {-dino-obs-p-on::%p%} to 0
	set {-dino-obs-p-x::%p%} to 180
	set {-dino-obs-p-y::%p%} to 18
	if {dino::best::%p%} is not set:
		set {dino::best::%p%} to 0
	open screen "Dino" to p:
		image "dino:textures/gui/bg_day.png" at 0 -10 size 320x160 texture 320x160 anchor center layer 0 id bg
		image "dino:textures/gui/cloud.png" at 110 -58 size 36x14 texture 36x14 anchor center layer 1 id cloud1
		image "dino:textures/gui/cloud.png" at -40 -38 size 36x14 texture 36x14 anchor center layer 1 id cloud2
		image "dino:textures/gui/ground.png" at 0 58 size 316x22 texture 320x24 anchor center layer 2 id ground
		image "dino:textures/gui/dino_run1.png" at -112 34 size 24x26 texture 24x26 anchor center layer 5 id dino
		image "dino:textures/gui/dino_duck1.png" at -110 39 size 32x16 texture 32x16 anchor center layer 5 id dino_duck hidden
		image "dino:textures/gui/cactus_small.png" at 180 35 size 14x24 texture 14x24 anchor center layer 4 id obs_s hidden
		image "dino:textures/gui/cactus_tall.png" at 180 31 size 16x32 texture 16x32 anchor center layer 4 id obs_t hidden
		image "dino:textures/gui/cactus_double.png" at 180 34 size 28x26 texture 28x26 anchor center layer 4 id obs_d hidden
		image "dino:textures/gui/ptero1.png" at 180 18 size 24x18 texture 24x18 anchor center layer 4 id obs_p hidden
		text "&8HI %{dino::best::%p%}%   &00" at 82 -78 anchor center layer 10 id score_txt
		text "&7Space/W — прыжок  |  S — присесть" at -55 -78 anchor center layer 10 id hint_txt
		text "&c&lGAME OVER" at 0 -22 anchor center shadow layer 20 id over_title hidden
		text "&fНажми &eSpace &fили &eR &fдля рестарта" at 0 -6 anchor center shadow layer 20 id over_sub hidden
		text "&e&lПАУЗА" at 0 -16 anchor center shadow layer 20 id pause_txt hidden
		button "Прыжок" at -120 86 size 70x20 anchor center run DinoJumpOrRestart(player) keep open tooltip "&eПрыжок &7(Space / W / Up)"
		button "Рестарт" at -40 86 size 70x20 anchor center run DinoStart(player) keep open tooltip "&aНачать заново &7(R)"
		button "Пауза" at 40 86 size 70x20 anchor center run DinoTogglePause(player) keep open tooltip "&eПауза / продолжить &7(P)"
		button "Топ-5" at 120 86 size 70x20 anchor center run DinoShowTop(player) keep open tooltip "&6Таблица рекордов сервера"

function DinoJumpOrRestart(p: player):
	if {-dino-active::%p%} is not 1:
		stop
	if {-dino-dead::%p%} is 1:
		DinoStart(p)
		stop
	if {-dino-paused::%p%} is 1:
		DinoTogglePause(p)
		stop
	if {-dino-y::%p%} >= 33.5:
		set {-dino-vy::%p%} to -9.6
		if {-dino-duck::%p%} is 1:
			set {-dino-duck::%p%} to 0
			hide screen element "dino_duck" from p
			show screen element "dino" to p
		set image of screen element "dino" for p to "dino:textures/gui/dino_jump.png"
		play sound ui.button.click to p with volume 0.5 pitch 1.5

function DinoSetDuck(p: player, down: boolean):
	if {-dino-active::%p%} is not 1:
		stop
	if {-dino-dead::%p%} is 1:
		stop
	if {-dino-paused::%p%} is 1:
		stop
	if down is true:
		set {-dino-duck::%p%} to 1
		if {-dino-y::%p%} < 33.5:
			if {-dino-vy::%p%} < 7.5:
				set {-dino-vy::%p%} to 7.5
		else:
			hide screen element "dino" from p
			show screen element "dino_duck" to p
	else:
		set {-dino-duck::%p%} to 0
		hide screen element "dino_duck" from p
		show screen element "dino" to p

function DinoTogglePause(p: player):
	if {-dino-active::%p%} is not 1:
		stop
	if {-dino-dead::%p%} is 1:
		stop
	if {-dino-paused::%p%} is 1:
		set {-dino-paused::%p%} to 0
		hide screen element "pause_txt" from p
	else:
		set {-dino-paused::%p%} to 1
		show screen element "pause_txt" to p

function DinoShowTop(p: player):
	send "&6&l=== DINO ТОП-5 ===" to p
	if {dino::lb::*} is empty:
		send "&7Пока нет рекордов. Сыграй первую игру!" to p
		stop
	set {-rank} to 0
	loop {dino::lb::*} sorted descending:
		add 1 to {-rank}
		send "&e#%{-rank}% &f%loop-index% &7— &a%loop-value%" to p
		if {-rank} >= 5:
			stop

function DinoTick(p: player):
	if {-dino-dead::%p%} is 1:
		stop
	if {-dino-paused::%p%} is 1:
		stop

	add 1 to {-dino-tick::%p%}
	set {-t} to {-dino-tick::%p%}

	# Счёт и ускорение
	set {-prev-score} to floor({-dino-score::%p%})
	add 0.35 to {-dino-score::%p%}
	set {-sc} to floor({-dino-score::%p%})
	set {-dino-speed::%p%} to min 9.8 (4.2 + {-sc} * 0.0045)
	set {-spd} to {-dino-speed::%p%}

	if {-sc} > {-prev-score}:
		set {-rem100} to {-sc} % 100
		if {-rem100} is 0:
			play sound entity.experience_orb.pickup to p with volume 0.7 pitch 1.3
		set {-cycle} to floor({-sc} / 500) % 2
		if {-cycle} is not {-dino-night::%p%}:
			set {-dino-night::%p%} to {-cycle}
			if {-cycle} is 1:
				set image of screen element "bg" for p to "dino:textures/gui/bg_night.png"
				set text of screen element "score_txt" for p to "&7HI %{dino::best::%p%}%   &f%{-sc}%"
			else:
				set image of screen element "bg" for p to "dino:textures/gui/bg_day.png"
				set text of screen element "score_txt" for p to "&8HI %{dino::best::%p%}%   &0%{-sc}%"
		else:
			if {-dino-night::%p%} is 1:
				set text of screen element "score_txt" for p to "&7HI %{dino::best::%p%}%   &f%{-sc}%"
			else:
				set text of screen element "score_txt" for p to "&8HI %{dino::best::%p%}%   &0%{-sc}%"

	# Облака (параллакс)
	set {-c1} to {-dino-cloud1::%p%} - 0.6
	if {-c1} < -142:
		set {-c1} to 142
	set {-dino-cloud1::%p%} to {-c1}
	set position of screen element "cloud1" for p to {-c1} -58

	set {-c2} to {-dino-cloud2::%p%} - 0.4
	if {-c2} < -142:
		set {-c2} to 142
	set {-dino-cloud2::%p%} to {-c2}
	set position of screen element "cloud2" for p to {-c2} -38

	# Физика прыжка динозаврика
	set {-dy} to {-dino-y::%p%}
	set {-vy} to {-dino-vy::%p%}
	if {-dy} < 34 or {-vy} < 0:
		add {-vy} to {-dy}
		add 0.95 to {-vy}
		if {-dy} >= 34:
			set {-dy} to 34
			set {-vy} to 0
			if {-dino-duck::%p%} is 1:
				hide screen element "dino" from p
				show screen element "dino_duck" to p
		set {-dino-y::%p%} to {-dy}
		set {-dino-vy::%p%} to {-vy}
		set position of screen element "dino" for p to -112 {-dy}
	else:
		# Анимация бега / приседания каждые 3 тика
		set {-anim} to floor({-t} / 3) % 2
		if {-dino-duck::%p%} is 1:
			if {-anim} is 0:
				set image of screen element "dino_duck" for p to "dino:textures/gui/dino_duck1.png"
			else:
				set image of screen element "dino_duck" for p to "dino:textures/gui/dino_duck2.png"
		else:
			if {-anim} is 0:
				set image of screen element "dino" for p to "dino:textures/gui/dino_run1.png"
			else:
				set image of screen element "dino" for p to "dino:textures/gui/dino_run2.png"

	# Движение препятствий
	if {-dino-obs-s-on::%p%} is 1:
		set {-ox} to {-dino-obs-s-x::%p%} - {-spd}
		if {-ox} < -152:
			set {-dino-obs-s-on::%p%} to 0
			hide screen element "obs_s" from p
		else:
			set {-dino-obs-s-x::%p%} to {-ox}
			set position of screen element "obs_s" for p to {-ox} 35

	if {-dino-obs-t-on::%p%} is 1:
		set {-ox} to {-dino-obs-t-x::%p%} - {-spd}
		if {-ox} < -152:
			set {-dino-obs-t-on::%p%} to 0
			hide screen element "obs_t" from p
		else:
			set {-dino-obs-t-x::%p%} to {-ox}
			set position of screen element "obs_t" for p to {-ox} 31

	if {-dino-obs-d-on::%p%} is 1:
		set {-ox} to {-dino-obs-d-x::%p%} - {-spd}
		if {-ox} < -148:
			set {-dino-obs-d-on::%p%} to 0
			hide screen element "obs_d" from p
		else:
			set {-dino-obs-d-x::%p%} to {-ox}
			set position of screen element "obs_d" for p to {-ox} 34

	if {-dino-obs-p-on::%p%} is 1:
		set {-ox} to {-dino-obs-p-x::%p%} - ({-spd} + 0.8)
		if {-ox} < -150:
			set {-dino-obs-p-on::%p%} to 0
			hide screen element "obs_p" from p
		else:
			set {-dino-obs-p-x::%p%} to {-ox}
			set {-py} to {-dino-obs-p-y::%p%}
			set position of screen element "obs_p" for p to {-ox} {-py}
			set {-wing} to floor({-t} / 4) % 2
			if {-wing} is 0:
				set image of screen element "obs_p" for p to "dino:textures/gui/ptero1.png"
			else:
				set image of screen element "obs_p" for p to "dino:textures/gui/ptero2.png"

	# Спавн новых препятствий
	remove 1 from {-dino-spawn-cd::%p%}
	if {-dino-spawn-cd::%p%} <= 0:
		DinoTrySpawn(p, {-sc})

	# Проверка столкновений (AABB с мягким допуском)
	DinoCheckHit(p)

function DinoTrySpawn(p: player, sc: number):
	set {-max-kind} to 3
	if sc >= 120:
		set {-max-kind} to 4
	set {-pick} to random integer between 1 and {-max-kind}
	set {-spawned} to 0
	if {-pick} is 1 and {-dino-obs-s-on::%p%} is 0:
		set {-dino-obs-s-on::%p%} to 1
		set {-dino-obs-s-x::%p%} to 152
		set position of screen element "obs_s" for p to 152 35
		show screen element "obs_s" to p
		set {-spawned} to 1
	else if {-pick} is 2 and {-dino-obs-t-on::%p%} is 0:
		set {-dino-obs-t-on::%p%} to 1
		set {-dino-obs-t-x::%p%} to 152
		set position of screen element "obs_t" for p to 152 31
		show screen element "obs_t" to p
		set {-spawned} to 1
	else if {-pick} is 3 and {-dino-obs-d-on::%p%} is 0:
		set {-dino-obs-d-on::%p%} to 1
		set {-dino-obs-d-x::%p%} to 148
		set position of screen element "obs_d" for p to 148 34
		show screen element "obs_d" to p
		set {-spawned} to 1
	else if {-pick} is 4 and {-dino-obs-p-on::%p%} is 0:
		set {-dino-obs-p-on::%p%} to 1
		set {-dino-obs-p-x::%p%} to 150
		set {-alt} to random integer between 1 and 2
		if {-alt} is 1:
			set {-dino-obs-p-y::%p%} to 18
		else:
			set {-dino-obs-p-y::%p%} to 34
		set {-py} to {-dino-obs-p-y::%p%}
		set position of screen element "obs_p" for p to 150 {-py}
		show screen element "obs_p" to p
		set {-spawned} to 1

	if {-spawned} is 1:
		set {-min-cd} to max 17 (round (34 - sc * 0.015))
		set {-max-cd} to max 28 (round (54 - sc * 0.02))
		set {-dino-spawn-cd::%p%} to random integer between {-min-cd} and {-max-cd}
	else:
		set {-dino-spawn-cd::%p%} to 6

function DinoCheckHit(p: player):
	# Хитбокс динозаврика (смягчённый на 3px)
	set {-dl} to -121
	set {-dr} to -103
	if {-dino-duck::%p%} is 1 and {-dino-y::%p%} >= 33.5:
		set {-dt} to 33
		set {-db} to 46
		set {-dr} to -97
	else:
		set {-dt} to {-dino-y::%p%} - 10
		set {-db} to {-dino-y::%p%} + 11

	if {-dino-obs-s-on::%p%} is 1:
		set {-ox} to {-dino-obs-s-x::%p%}
		if {-dr} > {-ox} - 5 and {-dl} < {-ox} + 5 and {-db} > 25 and {-dt} < 45:
			DinoGameOver(p)
			stop

	if {-dino-obs-t-on::%p%} is 1:
		set {-ox} to {-dino-obs-t-x::%p%}
		if {-dr} > {-ox} - 6 and {-dl} < {-ox} + 6 and {-db} > 17 and {-dt} < 45:
			DinoGameOver(p)
			stop

	if {-dino-obs-d-on::%p%} is 1:
		set {-ox} to {-dino-obs-d-x::%p%}
		if {-dr} > {-ox} - 11 and {-dl} < {-ox} + 11 and {-db} > 23 and {-dt} < 45:
			DinoGameOver(p)
			stop

	if {-dino-obs-p-on::%p%} is 1:
		set {-ox} to {-dino-obs-p-x::%p%}
		set {-oy} to {-dino-obs-p-y::%p%}
		if {-dr} > {-ox} - 9 and {-dl} < {-ox} + 9 and {-db} > {-oy} - 6 and {-dt} < {-oy} + 6:
			DinoGameOver(p)
			stop

function DinoGameOver(p: player):
	set {-dino-dead::%p%} to 1
	set {-sc} to floor({-dino-score::%p%})
	hide screen element "dino_duck" from p
	show screen element "dino" to p
	set image of screen element "dino" for p to "dino:textures/gui/dino_dead.png"
	show screen element "over_title" to p
	show screen element "over_sub" to p
	play sound entity.villager.no to p with volume 0.8 pitch 0.9
	if {-sc} > {dino::best::%p%}:
		set {dino::best::%p%} to {-sc}
		set {dino::lb::%p%} to {-sc}
		set text of screen element "over_sub" for p to "&6Новый рекорд: &e%{-sc}%&f! (Space / R)"
		play sound ui.toast.challenge_complete to p with volume 0.6 pitch 1.1
` }
    ],
    notes: {
      ru: [
        "Распакуйте архив dino-resources.zip прямо в папку scriptcraft/resources/ на сервере (получится scriptcraft/resources/assets/dino/textures/gui/*.png) и нажмите «Перезагрузка → Ресурсы» в студии (или /sc reload resources).",
        "Управление: Space / W / Стрелка вверх — прыжок (или мгновенный рестарт), S / Стрелка вниз — приседание (уклонение от верхних птеродактилей) и ускоренное падение, P — пауза, R — рестарт.",
        "Топ-5 рекордов работает на новом синтаксисе loop {dino::lb::*} sorted descending: — без ручной сортировки."
      ],
      en: [
        "Extract dino-resources.zip directly into scriptcraft/resources/ on your server (creating scriptcraft/resources/assets/dino/textures/gui/*.png) and run /sc reload resources (or Reload -> Resources in Studio).",
        "Controls: Space / W / Up — jump (or instant restart), S / Down — duck under high pterodactyls and fast-fall, P — pause, R — restart.",
        "The Top-5 leaderboard uses the new loop {dino::lb::*} sorted descending: syntax with zero manual sorting."
      ]
    },
    related: ["eff-open-screen", "screen-dynamic", "screen-image", "screen-tooltip", "event-key", "loop-sorted"]
  },
  {
    id: "tower-defense",
    title: { ru: "Tower Defense (20 волн + 4 Босса + x2)", en: "Tower Defense (20 waves + 4 Bosses + x2)" },
    intro: {
      ru: "Динамичная стратегия Tower Defense в окне GUI: S-образная карта с 8 площадками, 4 кликабельные иконки башен (Лучник, Пушка со сплешем, Лёд с замедлением, Маг), умный клик (пустая площадка строит, занятая сразу улучшает до 4★), всего 3 кнопки управления (Старт / скорость x2, Продать, Рестарт), 20 волн, 4 босса с полосой HP и таблица рекордов Топ-5.",
      en: "Fast-paced Tower Defense in a custom GUI: S-shaped map with 8 build pads, 4 clickable tower icons (Archer, Splash Cannon, Frost Slow, Long-range Mage), smart pad click (empty builds, occupied auto-upgrades up to 4★), only 3 compact buttons (Start / 2x speed, Sell, Restart), 20 waves, 4 bosses with a live HP bar, and Top-5 leaderboard."
    },
    downloads: [
      {
        href: "assets/td-resources.zip",
        label: { ru: "Скачать текстуры (td-resources.zip)", en: "Download textures (td-resources.zip)" },
        desc: { ru: "14 PNG-спрайтов (assets/td/textures/gui/) — распакуйте в scriptcraft/resources/", en: "14 PNG sprites (assets/td/textures/gui/) — extract into scriptcraft/resources/" }
      }
    ],
    blocks: [
      { lang: "sc", title: { ru: "scriptcraft/scripts/td.sc", en: "scriptcraft/scripts/td.sc" }, code: `# ==========================================================
#  td.sc — Tower Defense (20 волн + 4 Босса + ускорение x2)
#  Команда: /td
#  Управление:
#    1 / 2 / 3 / 4 — выбрать башню (Лучник / Пушка / Лёд / Маг)
#    ЛКМ по пустой площадке — построить выбранную башню
#    ЛКМ по построенной башне — сразу улучшить (до ур. 4!)
#    X — режим Продажи (70% возврат)
#    Space — старт волны / переключить скорость x1 <-> x2
#    R — перезапуск
#  Текстуры: распакуйте td-resources.zip в scriptcraft/resources/
# ==========================================================

on load:
	clear {-td-active::*}

on quit:
	TdCleanup(player)

on screen close "Tower Defense":
	TdCleanup(player)

command /td:
	TdStart(player)

# --- Горячие клавиши ---

on key 1 in screen "Tower Defense":
	TdSelectTool(player, 1)

on key 2 in screen "Tower Defense":
	TdSelectTool(player, 2)

on key 3 in screen "Tower Defense":
	TdSelectTool(player, 3)

on key 4 in screen "Tower Defense":
	TdSelectTool(player, 4)

on key x in screen "Tower Defense":
	TdToggleSell(player)

on key space in screen "Tower Defense":
	TdWaveOrSpeed(player)

on key r in screen "Tower Defense":
	TdStart(player)

# --- Игровой цикл (10 тиков/сек, в режиме x2 — два шага за тик) ---

every 2 ticks:
	loop all players:
		if {-td-active::%loop-player%} is 1:
			if loop-player has screen "Tower Defense" open:
				TdTick(loop-player)
				if {-td-speed::%loop-player%} is 2 and {-td-in-wave::%loop-player%} is 1 and {-td-over::%loop-player%} is 0:
					TdTick(loop-player)
			else:
				TdCleanup(loop-player)

# --- Инициализация и очистка ---

function TdCleanup(p: player):
	delete {-td-active::%p%}
	delete {-td-over::%p%}

function TdStart(p: player):
	set {-td-active::%p%} to 1
	set {-td-over::%p%} to 0
	set {-td-gold::%p%} to 110
	set {-td-hp::%p%} to 10
	set {-td-wave::%p%} to 0
	set {-td-score::%p%} to 0
	set {-td-tool::%p%} to 1
	set {-td-sell::%p%} to 0
	set {-td-speed::%p%} to 1
	set {-td-in-wave::%p%} to 0
	set {-td-to-spawn::%p%} to 0
	set {-td-spawn-boss::%p%} to 0
	set {-td-spawn-cd::%p%} to 0
	set {-td-fx-cd::%p%} to 0

	# Координаты 8 площадок строительства
	set {-td-px::%p%::1} to -90
	set {-td-py::%p%::1} to -38
	set {-td-px::%p%::2} to -20
	set {-td-py::%p%::2} to -38
	set {-td-px::%p%::3} to 50
	set {-td-py::%p%::3} to -38
	set {-td-px::%p%::4} to 134
	set {-td-py::%p%::4} to -38
	set {-td-px::%p%::5} to -134
	set {-td-py::%p%::5} to 18
	set {-td-px::%p%::6} to -50
	set {-td-py::%p%::6} to 18
	set {-td-px::%p%::7} to 24
	set {-td-py::%p%::7} to 18
	set {-td-px::%p%::8} to 94
	set {-td-py::%p%::8} to 18

	loop 8 times:
		set {-i} to %loop-number%
		set {-td-pt::%p%::%{-i}%} to 0
		set {-td-pl::%p%::%{-i}%} to 0
		set {-td-pcd::%p%::%{-i}%} to 0
		set {-td-pcost::%p%::%{-i}%} to 0

	# 8 слотов врагов (1..7 обычные, 8 — босс)
	loop 8 times:
		set {-i} to %loop-number%
		set {-td-ea::%p%::%{-i}%} to 0
		set {-td-eboss::%p%::%{-i}%} to 0
		set {-td-eslow::%p%::%{-i}%} to 0

	open screen "Tower Defense" to p:
		image "td:textures/gui/td_map.png" at 0 -14 size 320x180 texture 320x180 anchor center layer 0 id map
		text "&eВолна: &f0/20   &6Золото: &f110   &cБаза: &f10 HP   &aСчёт: &f0   &b[x1]" at 0 -112 anchor center shadow layer 10 id hud_top
		bar "boss_bar" value 100 max 100 at 0 -98 size 180x8 anchor center color #E74C3C background #AA1A0A0A text "&c&lБОСС: %value%/%max%" shadow layer 12 hidden

		# 8 кликабельных площадок (пустая -> постройка, занятая -> улучшение!)
		image "td:textures/gui/pad_empty.png" at -90 -38 size 22x22 texture 22x22 anchor center layer 3 id p1 run TdPadClick(player, 1) keep open tooltip "&7Площадка #1\\n&aКлик: построить / улучшить"
		image "td:textures/gui/pad_empty.png" at -20 -38 size 22x22 texture 22x22 anchor center layer 3 id p2 run TdPadClick(player, 2) keep open tooltip "&7Площадка #2\\n&aКлик: построить / улучшить"
		image "td:textures/gui/pad_empty.png" at 50 -38 size 22x22 texture 22x22 anchor center layer 3 id p3 run TdPadClick(player, 3) keep open tooltip "&7Площадка #3\\n&aКлик: построить / улучшить"
		image "td:textures/gui/pad_empty.png" at 134 -38 size 22x22 texture 22x22 anchor center layer 3 id p4 run TdPadClick(player, 4) keep open tooltip "&7Площадка #4\\n&aКлик: построить / улучшить"
		image "td:textures/gui/pad_empty.png" at -134 18 size 22x22 texture 22x22 anchor center layer 3 id p5 run TdPadClick(player, 5) keep open tooltip "&7Площадка #5\\n&aКлик: построить / улучшить"
		image "td:textures/gui/pad_empty.png" at -50 18 size 22x22 texture 22x22 anchor center layer 3 id p6 run TdPadClick(player, 6) keep open tooltip "&7Площадка #6\\n&aКлик: построить / улучшить"
		image "td:textures/gui/pad_empty.png" at 24 18 size 22x22 texture 22x22 anchor center layer 3 id p7 run TdPadClick(player, 7) keep open tooltip "&7Площадка #7\\n&aКлик: построить / улучшить"
		image "td:textures/gui/pad_empty.png" at 94 18 size 22x22 texture 22x22 anchor center layer 3 id p8 run TdPadClick(player, 8) keep open tooltip "&7Площадка #8\\n&aКлик: построить / улучшить"

		# Уровни башен
		text "" at -90 -24 anchor center shadow layer 6 id pl1 hidden
		text "" at -20 -24 anchor center shadow layer 6 id pl2 hidden
		text "" at 50 -24 anchor center shadow layer 6 id pl3 hidden
		text "" at 134 -24 anchor center shadow layer 6 id pl4 hidden
		text "" at -134 32 anchor center shadow layer 6 id pl5 hidden
		text "" at -50 32 anchor center shadow layer 6 id pl6 hidden
		text "" at 24 32 anchor center shadow layer 6 id pl7 hidden
		text "" at 94 32 anchor center shadow layer 6 id pl8 hidden

		# 7 слотов обычных врагов + 1 босс
		image "td:textures/gui/mob_slime.png" at -148 -66 size 16x16 texture 16x16 anchor center layer 7 id e1 hidden
		image "td:textures/gui/mob_slime.png" at -148 -66 size 16x16 texture 16x16 anchor center layer 7 id e2 hidden
		image "td:textures/gui/mob_slime.png" at -148 -66 size 16x16 texture 16x16 anchor center layer 7 id e3 hidden
		image "td:textures/gui/mob_slime.png" at -148 -66 size 16x16 texture 16x16 anchor center layer 7 id e4 hidden
		image "td:textures/gui/mob_slime.png" at -148 -66 size 16x16 texture 16x16 anchor center layer 7 id e5 hidden
		image "td:textures/gui/mob_slime.png" at -148 -66 size 16x16 texture 16x16 anchor center layer 7 id e6 hidden
		image "td:textures/gui/mob_slime.png" at -148 -66 size 16x16 texture 16x16 anchor center layer 7 id e7 hidden
		image "td:textures/gui/mob_boss.png" at -148 -66 size 22x22 texture 22x22 anchor center layer 8 id e8 hidden

		# Эффект попадания снаряда
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 10x10 anchor center layer 9 id fx hidden

		# Статус и оверлей победы/поражения
		text "&eВыбран: &fЛучник (50з) &7— клик по пустой строит, по башне улучшает!" at 0 82 anchor center shadow layer 10 id status_txt
		text "&c&lБАЗА ПАЛА!" at 0 -22 anchor center shadow layer 20 id end_title hidden
		text "&fНажми &eR &fили Рестарт" at 0 -6 anchor center shadow layer 20 id end_sub hidden

		# Компактная нижняя панель: 4 кликабельные иконки башен + всего 3 кнопки!
		image "td:textures/gui/tower_archer.png" at -138 102 size 22x22 texture 22x22 anchor center layer 10 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e[1] Лучник &7(50з)\\n&7Урон: 13 | Очень быстрый\\n&7Дальность: 86"
		text "&e[1] 50" at -138 117 anchor center shadow layer 10 id lbl1

		image "td:textures/gui/tower_cannon.png" at -104 102 size 22x22 texture 22x22 anchor center layer 10 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6[2] Пушка &7(95з)\\n&7Урон: 32 + &cСплеш по области\\n&7Дальность: 78"
		text "&7[2] 95" at -104 117 anchor center shadow layer 10 id lbl2

		image "td:textures/gui/tower_frost.png" at -70 102 size 22x22 texture 22x22 anchor center layer 10 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b[3] Лёд &7(85з)\\n&7Урон: 9 + &bЗамедление 45%\\n&7Дальность: 84"
		text "&7[3] 85" at -70 117 anchor center shadow layer 10 id lbl3

		image "td:textures/gui/tower_mage.png" at -36 102 size 22x22 texture 22x22 anchor center layer 10 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d[4] Маг &7(130з)\\n&7Урон: 28 | &dДальность 116\\n&7Пробивает боссов"
		text "&7[4] 130" at -36 117 anchor center shadow layer 10 id lbl4

		button "&aСтарт / x2" at 30 104 size 78x20 anchor center run TdWaveOrSpeed(player) keep open tooltip "&aЗапустить волну &7(Space)\\n&bВо время волны: скорость x1 / x2"
		button "&cПродать" at 100 104 size 54x20 anchor center run TdToggleSell(player) keep open tooltip "&cРежим продажи &7(X)\\n&7Возврат 70% стоимости"
		button "Рестарт" at 146 104 size 34x20 anchor center run TdRestartOrTop(player) keep open tooltip "&eРестарт &7(R) + Топ-5 в чат"

function TdRestartOrTop(p: player):
	TdShowTop(p)
	TdStart(p)

function TdShowTop(p: player):
	send "&6&l=== TOWER DEFENSE ТОП-5 ===" to p
	if {td::lb::*} is empty:
		send "&7Пока нет рекордов!" to p
		stop
	set {-r} to 0
	loop {td::lb::*} sorted descending:
		add 1 to {-r}
		send "&e#%{-r}% &f%loop-index% &7— &a%loop-value% очков" to p
		if {-r} >= 5:
			stop

function TdSelectTool(p: player, tool: number):
	if {-td-active::%p%} is not 1:
		stop
	set {-td-tool::%p%} to tool
	set {-td-sell::%p%} to 0
	set text of screen element "lbl1" for p to "&7[1] 50"
	set text of screen element "lbl2" for p to "&7[2] 95"
	set text of screen element "lbl3" for p to "&7[3] 85"
	set text of screen element "lbl4" for p to "&7[4] 130"
	if tool is 1:
		set text of screen element "lbl1" for p to "&e[1] 50"
		set text of screen element "status_txt" for p to "&eВыбран: &fЛучник (50з) &7— клик по пустой строит, по башне улучшает"
	else if tool is 2:
		set text of screen element "lbl2" for p to "&6[2] 95"
		set text of screen element "status_txt" for p to "&6Выбран: &fПушка (95з, сплеш) &7— клик по пустой строит, по башне улучшает"
	else if tool is 3:
		set text of screen element "lbl3" for p to "&b[3] 85"
		set text of screen element "status_txt" for p to "&bВыбран: &fЛёд (85з, замедление) &7— клик по пустой строит, по башне улучшает"
	else if tool is 4:
		set text of screen element "lbl4" for p to "&d[4] 130"
		set text of screen element "status_txt" for p to "&dВыбран: &fМаг (130з, дальний) &7— клик по пустой строит, по башне улучшает"
	play sound ui.button.click to p with volume 0.4 pitch 1.3

function TdToggleSell(p: player):
	if {-td-active::%p%} is not 1:
		stop
	if {-td-sell::%p%} is 1:
		set {-td-sell::%p%} to 0
		TdSelectTool(p, {-td-tool::%p%})
	else:
		set {-td-sell::%p%} to 1
		set text of screen element "status_txt" for p to "&c&lРЕЖИМ ПРОДАЖИ &7— кликните по башне для возврата 70%% (X — отмена)"
		play sound ui.button.click to p with volume 0.4 pitch 0.9

function TdWaveOrSpeed(p: player):
	if {-td-active::%p%} is not 1 or {-td-over::%p%} is 1:
		stop
	if {-td-in-wave::%p%} is 1:
		if {-td-speed::%p%} is 1:
			set {-td-speed::%p%} to 2
			set text of screen element "status_txt" for p to "&bСкорость: &l2x &7(Space — вернуть 1x)"
		else:
			set {-td-speed::%p%} to 1
			set text of screen element "status_txt" for p to "&7Скорость: &f1x &7(Space — ускорить 2x)"
		play sound ui.button.click to p with volume 0.4 pitch 1.5
		TdUpdateHud(p)
		stop
	TdNextWave(p)

function TdPadClick(p: player, pad: number):
	if {-td-active::%p%} is not 1 or {-td-over::%p%} is 1:
		stop
	set {-cur} to {-td-pt::%p%::%pad%}
	set {-lvl} to {-td-pl::%p%::%pad%}

	# Режим продажи (X)
	if {-td-sell::%p%} is 1:
		if {-cur} is 0:
			set text of screen element "status_txt" for p to "&7Площадка #%pad% пуста"
			stop
		set {-refund} to round({-td-pcost::%p%::%pad%} * 0.7)
		add {-refund} to {-td-gold::%p%}
		set {-td-pt::%p%::%pad%} to 0
		set {-td-pl::%p%::%pad%} to 0
		set {-td-pcost::%p%::%pad%} to 0
		set {-td-sell::%p%} to 0
		set image of screen element "p%pad%" for p to "td:textures/gui/pad_empty.png"
		hide screen element "pl%pad%" from p
		set text of screen element "status_txt" for p to "&eБашня продана за +%{-refund}% золота"
		play sound entity.experience_orb.pickup to p with volume 0.6 pitch 0.9
		TdUpdateHud(p)
		stop

	# Если площадка занята — сразу УЛУЧШАЕМ башню!
	if {-cur} is not 0:
		if {-lvl} >= 4:
			set text of screen element "status_txt" for p to "&dБашня #%pad% уже макс. уровня (★★★★)!"
			stop
		set {-up-cost} to 65
		if {-lvl} is 2:
			set {-up-cost} to 140
		else if {-lvl} is 3:
			set {-up-cost} to 240
		if {-td-gold::%p%} < {-up-cost}:
			set {-next-lvl} to {-lvl} + 1
			set text of screen element "status_txt" for p to "&cДля улучшения ур.%{-lvl}%→%{-next-lvl}% нужно %{-up-cost}%з!"
			play sound entity.villager.no to p with volume 0.5 pitch 1.0
			stop
		remove {-up-cost} from {-td-gold::%p%}
		add {-up-cost} to {-td-pcost::%p%::%pad%}
		add 1 to {-td-pl::%p%::%pad%}
		set {-nlvl} to {-td-pl::%p%::%pad%}
		if {-nlvl} is 2:
			set text of screen element "pl%pad%" for p to "&6★★"
		else if {-nlvl} is 3:
			set text of screen element "pl%pad%" for p to "&d★★★"
		else:
			set text of screen element "pl%pad%" for p to "&b★★★★"
		set text of screen element "status_txt" for p to "&aБашня #%pad% улучшена до ур. %{-nlvl}%! (-%{-up-cost}%з)"
		play sound entity.player.levelup to p with volume 0.5 pitch 1.4
		TdUpdateHud(p)
		stop

	# Постройка новой башни (tool 1..4)
	set {-tool} to {-td-tool::%p%}
	set {-cost} to 50
	set {-tex} to "td:textures/gui/tower_archer.png"
	if {-tool} is 2:
		set {-cost} to 95
		set {-tex} to "td:textures/gui/tower_cannon.png"
	else if {-tool} is 3:
		set {-cost} to 85
		set {-tex} to "td:textures/gui/tower_frost.png"
	else if {-tool} is 4:
		set {-cost} to 130
		set {-tex} to "td:textures/gui/tower_mage.png"

	if {-td-gold::%p%} < {-cost}:
		set text of screen element "status_txt" for p to "&cНе хватает золота! Нужно %{-cost}%з"
		play sound entity.villager.no to p with volume 0.5 pitch 1.0
		stop

	remove {-cost} from {-td-gold::%p%}
	set {-td-pt::%p%::%pad%} to {-tool}
	set {-td-pl::%p%::%pad%} to 1
	set {-td-pcd::%p%::%pad%} to 0
	set {-td-pcost::%p%::%pad%} to {-cost}
	set image of screen element "p%pad%" for p to {-tex}
	set text of screen element "pl%pad%" for p to "&e★"
	show screen element "pl%pad%" to p
	set text of screen element "status_txt" for p to "&aБашня построена! Клик по ней улучшит за 65з"
	play sound block.anvil.use to p with volume 0.35 pitch 1.5
	TdUpdateHud(p)

function TdNextWave(p: player):
	if {-td-wave::%p%} >= 20:
		stop

	add 1 to {-td-wave::%p%}
	set {-w} to {-td-wave::%p%}
	set {-td-in-wave::%p%} to 1
	set {-td-spawn-cd::%p%} to 1
	set {-td-spawn-boss::%p%} to 0

	# Количество мобов и боссы (волны 5, 10, 15, 20)
	set {-cnt} to 5 + round({-w} * 0.7)
	set {-td-to-spawn::%p%} to {-cnt}

	if {-w} is 5:
		set {-td-to-spawn::%p%} to 6
		set {-td-spawn-boss::%p%} to 1
		set text of screen element "status_txt" for p to "&c&lВОЛНА 5 — МИНИ-БОСС ВЫХОДИТ НА ПОЛЕ!"
		play sound entity.ender_dragon.growl to p with volume 0.5 pitch 1.15
	else if {-w} is 10:
		set {-td-to-spawn::%p%} to 8
		set {-td-spawn-boss::%p%} to 2
		set text of screen element "status_txt" for p to "&c&lВОЛНА 10 — ОСАДНЫЙ БОСС!"
		play sound entity.ender_dragon.growl to p with volume 0.55 pitch 0.95
	else if {-w} is 15:
		set {-td-to-spawn::%p%} to 10
		set {-td-spawn-boss::%p%} to 3
		set text of screen element "status_txt" for p to "&5&lВОЛНА 15 — ЛЕДЯНОЙ ТИТАН!"
		play sound entity.wither.spawn to p with volume 0.5 pitch 1.1
	else if {-w} is 20:
		set {-td-to-spawn::%p%} to 12
		set {-td-spawn-boss::%p%} to 4
		set text of screen element "status_txt" for p to "&4&lФИНАЛЬНАЯ ВОЛНА 20 — МЕГА-БОСС!"
		play sound entity.wither.spawn to p with volume 0.6 pitch 0.85
	else:
		set text of screen element "status_txt" for p to "&eВолна %{-w}%/20 началась! &7(Space — скорость x1/x2)"
		play sound entity.experience_orb.pickup to p with volume 0.5 pitch 1.25
	TdUpdateHud(p)

function TdUpdateHud(p: player):
	set {-sp} to "x1"
	if {-td-speed::%p%} is 2:
		set {-sp} to "&b&lx2"
	set text of screen element "hud_top" for p to "&eВолна: &f%{-td-wave::%p%}%/20   &6Золото: &f%{-td-gold::%p%}%   &cБаза: &f%{-td-hp::%p%}% HP   &aСчёт: &f%{-td-score::%p%}%   &7[%{-sp}%&7]"

# --- Основной тик игры ---

function TdTick(p: player):
	if {-td-over::%p%} is 1:
		stop

	# Скрытие вспышки снаряда
	if {-td-fx-cd::%p%} > 0:
		remove 1 from {-td-fx-cd::%p%}
		if {-td-fx-cd::%p%} <= 0:
			hide screen element "fx" from p

	if {-td-in-wave::%p%} is 0:
		stop

	# 1. Спавн врагов текущей волны
	if {-td-to-spawn::%p%} > 0 or {-td-spawn-boss::%p%} > 0:
		remove 1 from {-td-spawn-cd::%p%}
		if {-td-spawn-cd::%p%} <= 0:
			TdSpawnNext(p)

	# 2. Движение врагов по S-образной дороге
	set {-boss-alive} to 0
	loop 8 times:
		set {-ei} to %loop-number%
		if {-td-ea::%p%::%{-ei}%} is 1:
			TdMoveEnemy(p, {-ei})
			if {-td-ea::%p%::%{-ei}%} is 1 and {-td-eboss::%p%::%{-ei}%} is 1:
				set {-boss-alive} to 1
				set value of screen bar "boss_bar" for p to {-td-ehp::%p%::%{-ei}%}

	if {-boss-alive} is 0:
		hide screen element "boss_bar" from p

	# 3. Стрельба башен
	loop 8 times:
		set {-pi} to %loop-number%
		if {-td-pt::%p%::%{-pi}%} > 0:
			TdTowerTick(p, {-pi})

	# 4. Проверка завершения волны
	if {-td-to-spawn::%p%} <= 0 and {-td-spawn-boss::%p%} <= 0:
		set {-rem} to 0
		loop 8 times:
			set {-k} to %loop-number%
			if {-td-ea::%p%::%{-k}%} is 1:
				add 1 to {-rem}
		if {-rem} is 0:
			set {-td-in-wave::%p%} to 0
			set {-bonus} to 16 + {-td-wave::%p%} * 6
			add {-bonus} to {-td-gold::%p%}
			add 100 + {-td-wave::%p%} * 10 to {-td-score::%p%}
			TdUpdateHud(p)
			if {-td-wave::%p%} >= 20:
				TdWin(p)
			else:
				set text of screen element "status_txt" for p to "&aВолна %{-td-wave::%p%}%/20 пройдена! +%{-bonus}%з. Жмите &eSpace &aдля следующей!"
				play sound entity.player.levelup to p with volume 0.5 pitch 1.25

function TdSpawnNext(p: player):
	set {-w} to {-td-wave::%p%}
	# Сначала обычные мобы (слоты 1..7), затем Босс (слот 8)
	if {-td-to-spawn::%p%} > 0:
		set {-slot} to 0
		loop 7 times:
			set {-k} to %loop-number%
			if {-td-ea::%p%::%{-k}%} is 0 and {-slot} is 0:
				set {-slot} to {-k}
		if {-slot} is 0:
			set {-td-spawn-cd::%p%} to 2
			stop
		remove 1 from {-td-to-spawn::%p%}

		# Формулы сложности на 20 волн: быстрые раши + танки-рыцари
		set {-hp} to round(30 + {-w} * 22 + {-w} * {-w} * 2.2)
		set {-spd} to 3.4 + {-w} * 0.08
		set {-rew} to 10 + round({-w} * 0.7)
		set {-cd} to max(4, 8 - floor({-w} / 4))
		set {-tex} to "td:textures/gui/mob_slime.png"

		# Тип врага по волне
		if {-w} is 3 or 4 or 5 or 11 or 12:
			set {-tex} to "td:textures/gui/mob_orc.png"
			set {-hp} to round({-hp} * 1.15)
		else if {-w} is 7 or 13 or 17:
			# Быстрая волна-раш слаймов!
			set {-tex} to "td:textures/gui/mob_slime.png"
			set {-hp} to round({-hp} * 0.72)
			set {-spd} to {-spd} + 1.35
			set {-cd} to 4
		else if {-w} >= 6:
			# Бронированные рыцари
			set {-tex} to "td:textures/gui/mob_knight.png"
			set {-hp} to round({-hp} * 1.25)
			set {-spd} to {-spd} - 0.25

		set {-td-spawn-cd::%p%} to {-cd}
		set {-td-ea::%p%::%{-slot}%} to 1
		set {-td-eboss::%p%::%{-slot}%} to 0
		set {-td-ex::%p%::%{-slot}%} to -148
		set {-td-ey::%p%::%{-slot}%} to -66
		set {-td-eseg::%p%::%{-slot}%} to 1
		set {-td-ehp::%p%::%{-slot}%} to {-hp}
		set {-td-espd::%p%::%{-slot}%} to {-spd}
		set {-td-erew::%p%::%{-slot}%} to {-rew}
		set {-td-eslow::%p%::%{-slot}%} to 0
		set image of screen element "e%{-slot}%" for p to {-tex}
		set position of screen element "e%{-slot}%" for p to -148 -66
		show screen element "e%{-slot}%" to p
		stop

	# Спавн Босса (слот 8)
	if {-td-spawn-boss::%p%} > 0 and {-td-ea::%p%::8} is 0:
		set {-bkind} to {-td-spawn-boss::%p%}
		set {-td-spawn-boss::%p%} to 0
		set {-bhp} to 520
		set {-bspd} to 2.35
		set {-brew} to 110
		if {-bkind} is 2:
			set {-bhp} to 1450
			set {-bspd} to 2.2
			set {-brew} to 200
		else if {-bkind} is 3:
			set {-bhp} to 3200
			set {-bspd} to 2.25
			set {-brew} to 300
		else if {-bkind} is 4:
			set {-bhp} to 6500
			set {-bspd} to 2.1
			set {-brew} to 500
		set {-td-ea::%p%::8} to 1
		set {-td-eboss::%p%::8} to 1
		set {-td-ex::%p%::8} to -148
		set {-td-ey::%p%::8} to -66
		set {-td-eseg::%p%::8} to 1
		set {-td-ehp::%p%::8} to {-bhp}
		set {-td-espd::%p%::8} to {-bspd}
		set {-td-erew::%p%::8} to {-brew}
		set {-td-eslow::%p%::8} to 0
		set position of screen element "e8" for p to -148 -66
		show screen element "e8" to p
		set max of screen bar "boss_bar" for p to {-bhp}
		set value of screen bar "boss_bar" for p to {-bhp}
		show screen element "boss_bar" to p

function TdMoveEnemy(p: player, i: number):
	set {-spd} to {-td-espd::%p%::%i%}
	if {-td-eslow::%p%::%i%} > 0:
		remove 1 from {-td-eslow::%p%::%i%}
		set {-spd} to {-spd} * 0.55
	set {-seg} to {-td-eseg::%p%::%i%}
	set {-x} to {-td-ex::%p%::%i%}
	set {-y} to {-td-ey::%p%::%i%}

	if {-seg} is 1:
		add {-spd} to {-x}
		if {-x} >= 100:
			set {-x} to 100
			set {-seg} to 2
	else if {-seg} is 2:
		add {-spd} to {-y}
		if {-y} >= -8:
			set {-y} to -8
			set {-seg} to 3
	else if {-seg} is 3:
		remove {-spd} from {-x}
		if {-x} <= -100:
			set {-x} to -100
			set {-seg} to 4
	else if {-seg} is 4:
		add {-spd} to {-y}
		if {-y} >= 44:
			set {-y} to 44
			set {-seg} to 5
	else if {-seg} is 5:
		add {-spd} to {-x}
		if {-x} >= 136:
			# Враг дошёл до замка!
			set {-td-ea::%p%::%i%} to 0
			hide screen element "e%i%" from p
			set {-dmg} to 1
			if {-td-eboss::%p%::%i%} is 1:
				set {-dmg} to 5
			remove {-dmg} from {-td-hp::%p%}
			play sound entity.iron_golem.damage to p with volume 0.7 pitch 0.8
			TdUpdateHud(p)
			if {-td-hp::%p%} <= 0:
				TdLose(p)
			stop

	set {-td-ex::%p%::%i%} to {-x}
	set {-td-ey::%p%::%i%} to {-y}
	set {-td-eseg::%p%::%i%} to {-seg}
	set position of screen element "e%i%" for p to {-x} {-y}

function TdTowerTick(p: player, pad: number):
	if {-td-pcd::%p%::%pad%} > 0:
		remove 1 from {-td-pcd::%p%::%pad%}
		stop

	set {-type} to {-td-pt::%p%::%pad%}
	set {-lvl} to {-td-pl::%p%::%pad%}
	set {-tx} to {-td-px::%p%::%pad%}
	set {-ty} to {-td-py::%p%::%pad%}

	# Параметры башни с учётом уровня (до 4 ур.)
	set {-range} to 86
	set {-dmg} to 13
	set {-cd} to 4
	set {-ptex} to "td:textures/gui/proj_arrow.png"
	if {-type} is 2:
		set {-range} to 78
		set {-dmg} to 32
		set {-cd} to 11
		set {-ptex} to "td:textures/gui/proj_cannon.png"
	else if {-type} is 3:
		set {-range} to 84
		set {-dmg} to 9
		set {-cd} to 7
		set {-ptex} to "td:textures/gui/proj_frost.png"
	else if {-type} is 4:
		set {-range} to 116
		set {-dmg} to 28
		set {-cd} to 6
		set {-ptex} to "td:textures/gui/proj_mage.png"

	set {-range} to {-range} + ({-lvl} - 1) * 8
	set {-dmg} to round({-dmg} * (1 + ({-lvl} - 1) * 0.55))

	# Ищем врага, продвинувшегося дальше всех в радиусе
	set {-best} to 0
	set {-best-prog} to -1
	loop 8 times:
		set {-ei} to %loop-number%
		if {-td-ea::%p%::%{-ei}%} is 1:
			set {-dx} to {-td-ex::%p%::%{-ei}%} - {-tx}
			set {-dy} to {-td-ey::%p%::%{-ei}%} - {-ty}
			set {-dist} to sqrt({-dx} * {-dx} + {-dy} * {-dy})
			if {-dist} <= {-range}:
				set {-prog} to {-td-eseg::%p%::%{-ei}%} * 1000
				if {-prog} > {-best-prog}:
					set {-best-prog} to {-prog}
					set {-best} to {-ei}

	if {-best} is 0:
		stop

	set {-td-pcd::%p%::%pad%} to {-cd}
	set {-hx} to {-td-ex::%p%::%{-best}%}
	set {-hy} to {-td-ey::%p%::%{-best}%}

	# Показываем вспышку снаряда
	set image of screen element "fx" for p to {-ptex}
	set position of screen element "fx" for p to {-hx} {-hy}
	show screen element "fx" to p
	set {-td-fx-cd::%p%} to 2

	# Наносим урон (Пушка бьёт по области, Лёд замедляет)
	if {-type} is 2:
		play sound entity.generic.explode to p with volume 0.18 pitch 1.6
		loop 8 times:
			set {-si} to %loop-number%
			if {-td-ea::%p%::%{-si}%} is 1:
				set {-sdx} to {-td-ex::%p%::%{-si}%} - {-hx}
				set {-sdy} to {-td-ey::%p%::%{-si}%} - {-hy}
				if sqrt({-sdx} * {-sdx} + {-sdy} * {-sdy}) <= 38:
					TdDamageEnemy(p, {-si}, {-dmg}, 0)
	else if {-type} is 3:
		play sound block.glass.break to p with volume 0.2 pitch 1.7
		TdDamageEnemy(p, {-best}, {-dmg}, 18)
	else:
		play sound entity.arrow.shoot to p with volume 0.2 pitch 1.4
		TdDamageEnemy(p, {-best}, {-dmg}, 0)

function TdDamageEnemy(p: player, i: number, dmg: number, slow: number):
	if {-td-ea::%p%::%i%} is not 1:
		stop
	remove dmg from {-td-ehp::%p%::%i%}
	if slow > 0:
		set {-td-eslow::%p%::%i%} to slow
	if {-td-ehp::%p%::%i%} <= 0:
		set {-td-ea::%p%::%i%} to 0
		hide screen element "e%i%" from p
		set {-rew} to {-td-erew::%p%::%i%}
		add {-rew} to {-td-gold::%p%}
		add {-rew} to {-td-score::%p%}
		if {-td-eboss::%p%::%i%} is 1:
			hide screen element "boss_bar" from p
			play sound ui.toast.challenge_complete to p with volume 0.7 pitch 1.1
		TdUpdateHud(p)

function TdLose(p: player):
	set {-td-over::%p%} to 1
	set {-td-in-wave::%p%} to 0
	set text of screen element "end_title" for p to "&c&lБАЗА ПАЛА! (Волна %{-td-wave::%p%}%/20)"
	set text of screen element "end_sub" for p to "&fСчёт: &e%{-td-score::%p%}% &7— нажми &eR &7для рестарта"
	show screen element "end_title" to p
	show screen element "end_sub" to p
	TdSaveScore(p)

function TdWin(p: player):
	set {-td-over::%p%} to 1
	add {-td-hp::%p%} * 100 to {-td-score::%p%}
	TdUpdateHud(p)
	set text of screen element "end_title" for p to "&6&lПОБЕДА! ВСЕ 20 ВОЛН ПРОЙДЕНЫ!"
	set text of screen element "end_sub" for p to "&aИтоговый счёт: &e%{-td-score::%p%}% &7— нажми &eR &7сыграть ещё"
	show screen element "end_title" to p
	show screen element "end_sub" to p
	play sound ui.toast.challenge_complete to p with volume 0.9 pitch 1.0
	TdSaveScore(p)

function TdSaveScore(p: player):
	set {-sc} to {-td-score::%p%}
	if {td::lb::%p%} is not set:
		set {td::lb::%p%} to {-sc}
	else if {-sc} > {td::lb::%p%}:
		set {td::lb::%p%} to {-sc}
` }
    ],
    notes: {
      ru: [
        "Распакуйте td-resources.zip прямо в scriptcraft/resources/ на сервере (получится scriptcraft/resources/assets/td/textures/gui/*.png) и нажмите «Перезагрузка → Ресурсы» в студии (или /sc reload resources).",
        "Управление: 1–4 — выбор башни (Лучник 50з, Пушка 90з со сплешем, Лёд 80з с замедлением, Маг 120з с огромной дальностью), 5 — апгрейд до ★3, 6 — продажа (70%), ЛКМ по площадке на карте — применить, Space — старт волны, R — рестарт.",
        "На 5-й волне выходит Мини-Босс (440 HP), а на 10-й — Финальный Мега-Босс (1200 HP) с динамической полосой здоровья вверху карты."
      ],
      en: [
        "Extract td-resources.zip directly into scriptcraft/resources/ on your server (creating scriptcraft/resources/assets/td/textures/gui/*.png) and run /sc reload resources (or Reload -> Resources in Studio).",
        "Controls: 1–4 select tower (Archer 50g, Splash Cannon 90g, Slowing Frost 80g, Long-range Mage 120g), 5 upgrade up to ★3, 6 sell (70% refund), click any build pad on the map, Space starts the next wave, R restarts.",
        "Wave 5 spawns a Mini-Boss (440 HP) and Wave 10 spawns the Final Mega-Boss (1200 HP) with a live Boss HP bar at the top of the map."
      ]
    },
    related: ["eff-open-screen", "screen-dynamic", "screen-image", "screen-tooltip", "event-key", "loop-sorted"]
  },
  {
    id: "seabattle",
    title: { ru: "Морской Бой на двоих (в тетради)", en: "2-Player Notebook Sea Battle" },
    intro: {
      ru: "Дуэльный Морской Бой для двух игроков в стиле школьной тетради в клетку: ручная или автоматическая расстановка кораблей без касаний, вызов игрока через поле ввода прямо в GUI, покадровая анимация взрывов и всплесков чернил, авто-обводка потопленных кораблей, тряска камеры, вспышки экрана и Топ-5 адмиралов. Запуск: /seabattle или /sb.",
      en: "2-player Notebook Sea Battle (Battleship) on squared school paper: manual or instant auto-fleet placement with strict spacing rules, in-GUI player challenge input field, frame-by-frame explosion & ink-splash animations, auto-halo around sunken ships, camera shake, screen flash, and Top-5 Admirals leaderboard. Run: /seabattle or /sb."
    },
    downloads: [
      {
        href: "assets/seabattle-resources.zip",
        label: { ru: "Скачать текстуры (seabattle-resources.zip)", en: "Download textures (seabattle-resources.zip)" },
        desc: { ru: "15 PNG-спрайтов (assets/seabattle/textures/gui/) — распакуйте в scriptcraft/resources/", en: "15 PNG sprites (assets/seabattle/textures/gui/) — extract into scriptcraft/resources/" }
      }
    ],
    blocks: [
      { lang: "sc", title: { ru: "scriptcraft/scripts/seabattle.sc", en: "scriptcraft/scripts/seabattle.sc" }, code: `# ==========================================================
#  seabattle.sc — Морской Бой на двоих в тетради в клетку!
#  Команды:
#    /seabattle           — открыть тетрадь Морского Боя
#    /seabattle <игрок>   — вызвать игрока на дуэль
#  Фичи:
#    - Тетрадь в клетку с двумя полями 8x8 (Мой флот и Радар врага)
#    - Поле ввода ника соперника (input), тумблер эффектов (toggle), полосы флота (bar)
#    - Фаза расстановки: вручную кликами (R — поворот) или кнопкой «Авто-флот»
#    - Фаза боя на двоих: покадровая анимация взрывов и всплесков воды,
#      авто-обводка потопленных кораблей, тряска камеры, вспышка экрана и Топ-5!
# ==========================================================

on load:
	clear {-sb-state::*}

on quit:
	SbLeave(player)

on screen close "Морской Бой":
	SbOnScreenClose(player)

command /seabattle [<player>]:
	aliases: sb
	if arg-1 is set:
		SbChallenge(player, arg-1)
	else:
		SbOpenNotebook(player)

# --- Интерактивные контролы и клавиши ---

on key r in screen "Морской Бой":
	SbRotate(player)

on toggle "sfx_tog":
	if %value% is "true":
		set {-sb-sfx::%player%} to 1
		send action bar "&aЭффекты камеры и звуки включены" to player
	else:
		set {-sb-sfx::%player%} to 0
		send action bar "&7Эффекты камеры выключены" to player

# --- Фоновая покадровая анимация компаса и горящих палуб ---

every 6 ticks:
	loop all players:
		if loop-player has screen "Морской Бой" open:
			SbAnimateIdle(loop-player)

function SbAnimateIdle(p: player):
	if {-sb-anim::%p%} is 1:
		set {-sb-anim::%p%} to 2
		set image of screen element "compass" for p to "seabattle:textures/gui/compass2.png"
	else:
		set {-sb-anim::%p%} to 1
		set image of screen element "compass" for p to "seabattle:textures/gui/compass1.png"

# --- Открытие тетради и инициализация ---

function SbOpenNotebook(p: player):
	if {-sb-sfx::%p%} is not set:
		set {-sb-sfx::%p%} to 1
	if {-sb-state::%p%} is not set:
		SbResetBoard(p)
		set {-sb-state::%p%} to "place"

	open screen "Морской Бой" to p:
		image "seabattle:textures/gui/notebook_bg.png" at 0 -10 size 340x196 texture 340x196 anchor center layer 0 id bg
		image "seabattle:textures/gui/compass1.png" at 0 -64 size 22x22 texture 22x22 anchor center layer 6 id compass tooltip "&9Морской Компас\\n&7Пиратская тетрадь в клетку"

		text "&1&lМОЙ ФЛОТ" at -91 -96 anchor center layer 8 id lbl_my
		text "&4&lРАДАР ВРАГА" at 91 -96 anchor center layer 8 id lbl_foe
		bar "my_bar" value 10 max 10 at -91 -82 size 112x7 anchor center color #2563EB background #551E293B text "&fПалубы: %value%/10" shadow layer 8
		bar "foe_bar" value 10 max 10 at 91 -82 size 112x7 anchor center color #DC2626 background #551E293B text "&fВраг: %value%/10" shadow layer 8

		text "&9А" at -140 -60 anchor center layer 4
		text "&9А" at 42 -60 anchor center layer 4
		text "&9Б" at -126 -60 anchor center layer 4
		text "&9Б" at 56 -60 anchor center layer 4
		text "&9В" at -112 -60 anchor center layer 4
		text "&9В" at 70 -60 anchor center layer 4
		text "&9Г" at -98 -60 anchor center layer 4
		text "&9Г" at 84 -60 anchor center layer 4
		text "&9Д" at -84 -60 anchor center layer 4
		text "&9Д" at 98 -60 anchor center layer 4
		text "&9Е" at -70 -60 anchor center layer 4
		text "&9Е" at 112 -60 anchor center layer 4
		text "&9Ж" at -56 -60 anchor center layer 4
		text "&9Ж" at 126 -60 anchor center layer 4
		text "&9З" at -42 -60 anchor center layer 4
		text "&9З" at 140 -60 anchor center layer 4
		text "&91" at -152 -48 anchor center layer 4
		text "&91" at 30 -48 anchor center layer 4
		text "&92" at -152 -34 anchor center layer 4
		text "&92" at 30 -34 anchor center layer 4
		text "&93" at -152 -20 anchor center layer 4
		text "&93" at 30 -20 anchor center layer 4
		text "&94" at -152 -6 anchor center layer 4
		text "&94" at 30 -6 anchor center layer 4
		text "&95" at -152 8 anchor center layer 4
		text "&95" at 30 8 anchor center layer 4
		text "&96" at -152 22 anchor center layer 4
		text "&96" at 30 22 anchor center layer 4
		text "&97" at -152 36 anchor center layer 4
		text "&97" at 30 36 anchor center layer 4
		text "&98" at -152 50 anchor center layer 4
		text "&98" at 30 50 anchor center layer 4
		image "seabattle:textures/gui/cell_empty.png" at -140 -48 size 14x14 texture 14x14 anchor center layer 5 id m_1_1 run SbClickMy(player, 1, 1) keep open tooltip "&9Мой флот: &fА1"
		image "seabattle:textures/gui/cell_empty.png" at -126 -48 size 14x14 texture 14x14 anchor center layer 5 id m_2_1 run SbClickMy(player, 2, 1) keep open tooltip "&9Мой флот: &fБ1"
		image "seabattle:textures/gui/cell_empty.png" at -112 -48 size 14x14 texture 14x14 anchor center layer 5 id m_3_1 run SbClickMy(player, 3, 1) keep open tooltip "&9Мой флот: &fВ1"
		image "seabattle:textures/gui/cell_empty.png" at -98 -48 size 14x14 texture 14x14 anchor center layer 5 id m_4_1 run SbClickMy(player, 4, 1) keep open tooltip "&9Мой флот: &fГ1"
		image "seabattle:textures/gui/cell_empty.png" at -84 -48 size 14x14 texture 14x14 anchor center layer 5 id m_5_1 run SbClickMy(player, 5, 1) keep open tooltip "&9Мой флот: &fД1"
		image "seabattle:textures/gui/cell_empty.png" at -70 -48 size 14x14 texture 14x14 anchor center layer 5 id m_6_1 run SbClickMy(player, 6, 1) keep open tooltip "&9Мой флот: &fЕ1"
		image "seabattle:textures/gui/cell_empty.png" at -56 -48 size 14x14 texture 14x14 anchor center layer 5 id m_7_1 run SbClickMy(player, 7, 1) keep open tooltip "&9Мой флот: &fЖ1"
		image "seabattle:textures/gui/cell_empty.png" at -42 -48 size 14x14 texture 14x14 anchor center layer 5 id m_8_1 run SbClickMy(player, 8, 1) keep open tooltip "&9Мой флот: &fЗ1"
		image "seabattle:textures/gui/cell_empty.png" at -140 -34 size 14x14 texture 14x14 anchor center layer 5 id m_1_2 run SbClickMy(player, 1, 2) keep open tooltip "&9Мой флот: &fА2"
		image "seabattle:textures/gui/cell_empty.png" at -126 -34 size 14x14 texture 14x14 anchor center layer 5 id m_2_2 run SbClickMy(player, 2, 2) keep open tooltip "&9Мой флот: &fБ2"
		image "seabattle:textures/gui/cell_empty.png" at -112 -34 size 14x14 texture 14x14 anchor center layer 5 id m_3_2 run SbClickMy(player, 3, 2) keep open tooltip "&9Мой флот: &fВ2"
		image "seabattle:textures/gui/cell_empty.png" at -98 -34 size 14x14 texture 14x14 anchor center layer 5 id m_4_2 run SbClickMy(player, 4, 2) keep open tooltip "&9Мой флот: &fГ2"
		image "seabattle:textures/gui/cell_empty.png" at -84 -34 size 14x14 texture 14x14 anchor center layer 5 id m_5_2 run SbClickMy(player, 5, 2) keep open tooltip "&9Мой флот: &fД2"
		image "seabattle:textures/gui/cell_empty.png" at -70 -34 size 14x14 texture 14x14 anchor center layer 5 id m_6_2 run SbClickMy(player, 6, 2) keep open tooltip "&9Мой флот: &fЕ2"
		image "seabattle:textures/gui/cell_empty.png" at -56 -34 size 14x14 texture 14x14 anchor center layer 5 id m_7_2 run SbClickMy(player, 7, 2) keep open tooltip "&9Мой флот: &fЖ2"
		image "seabattle:textures/gui/cell_empty.png" at -42 -34 size 14x14 texture 14x14 anchor center layer 5 id m_8_2 run SbClickMy(player, 8, 2) keep open tooltip "&9Мой флот: &fЗ2"
		image "seabattle:textures/gui/cell_empty.png" at -140 -20 size 14x14 texture 14x14 anchor center layer 5 id m_1_3 run SbClickMy(player, 1, 3) keep open tooltip "&9Мой флот: &fА3"
		image "seabattle:textures/gui/cell_empty.png" at -126 -20 size 14x14 texture 14x14 anchor center layer 5 id m_2_3 run SbClickMy(player, 2, 3) keep open tooltip "&9Мой флот: &fБ3"
		image "seabattle:textures/gui/cell_empty.png" at -112 -20 size 14x14 texture 14x14 anchor center layer 5 id m_3_3 run SbClickMy(player, 3, 3) keep open tooltip "&9Мой флот: &fВ3"
		image "seabattle:textures/gui/cell_empty.png" at -98 -20 size 14x14 texture 14x14 anchor center layer 5 id m_4_3 run SbClickMy(player, 4, 3) keep open tooltip "&9Мой флот: &fГ3"
		image "seabattle:textures/gui/cell_empty.png" at -84 -20 size 14x14 texture 14x14 anchor center layer 5 id m_5_3 run SbClickMy(player, 5, 3) keep open tooltip "&9Мой флот: &fД3"
		image "seabattle:textures/gui/cell_empty.png" at -70 -20 size 14x14 texture 14x14 anchor center layer 5 id m_6_3 run SbClickMy(player, 6, 3) keep open tooltip "&9Мой флот: &fЕ3"
		image "seabattle:textures/gui/cell_empty.png" at -56 -20 size 14x14 texture 14x14 anchor center layer 5 id m_7_3 run SbClickMy(player, 7, 3) keep open tooltip "&9Мой флот: &fЖ3"
		image "seabattle:textures/gui/cell_empty.png" at -42 -20 size 14x14 texture 14x14 anchor center layer 5 id m_8_3 run SbClickMy(player, 8, 3) keep open tooltip "&9Мой флот: &fЗ3"
		image "seabattle:textures/gui/cell_empty.png" at -140 -6 size 14x14 texture 14x14 anchor center layer 5 id m_1_4 run SbClickMy(player, 1, 4) keep open tooltip "&9Мой флот: &fА4"
		image "seabattle:textures/gui/cell_empty.png" at -126 -6 size 14x14 texture 14x14 anchor center layer 5 id m_2_4 run SbClickMy(player, 2, 4) keep open tooltip "&9Мой флот: &fБ4"
		image "seabattle:textures/gui/cell_empty.png" at -112 -6 size 14x14 texture 14x14 anchor center layer 5 id m_3_4 run SbClickMy(player, 3, 4) keep open tooltip "&9Мой флот: &fВ4"
		image "seabattle:textures/gui/cell_empty.png" at -98 -6 size 14x14 texture 14x14 anchor center layer 5 id m_4_4 run SbClickMy(player, 4, 4) keep open tooltip "&9Мой флот: &fГ4"
		image "seabattle:textures/gui/cell_empty.png" at -84 -6 size 14x14 texture 14x14 anchor center layer 5 id m_5_4 run SbClickMy(player, 5, 4) keep open tooltip "&9Мой флот: &fД4"
		image "seabattle:textures/gui/cell_empty.png" at -70 -6 size 14x14 texture 14x14 anchor center layer 5 id m_6_4 run SbClickMy(player, 6, 4) keep open tooltip "&9Мой флот: &fЕ4"
		image "seabattle:textures/gui/cell_empty.png" at -56 -6 size 14x14 texture 14x14 anchor center layer 5 id m_7_4 run SbClickMy(player, 7, 4) keep open tooltip "&9Мой флот: &fЖ4"
		image "seabattle:textures/gui/cell_empty.png" at -42 -6 size 14x14 texture 14x14 anchor center layer 5 id m_8_4 run SbClickMy(player, 8, 4) keep open tooltip "&9Мой флот: &fЗ4"
		image "seabattle:textures/gui/cell_empty.png" at -140 8 size 14x14 texture 14x14 anchor center layer 5 id m_1_5 run SbClickMy(player, 1, 5) keep open tooltip "&9Мой флот: &fА5"
		image "seabattle:textures/gui/cell_empty.png" at -126 8 size 14x14 texture 14x14 anchor center layer 5 id m_2_5 run SbClickMy(player, 2, 5) keep open tooltip "&9Мой флот: &fБ5"
		image "seabattle:textures/gui/cell_empty.png" at -112 8 size 14x14 texture 14x14 anchor center layer 5 id m_3_5 run SbClickMy(player, 3, 5) keep open tooltip "&9Мой флот: &fВ5"
		image "seabattle:textures/gui/cell_empty.png" at -98 8 size 14x14 texture 14x14 anchor center layer 5 id m_4_5 run SbClickMy(player, 4, 5) keep open tooltip "&9Мой флот: &fГ5"
		image "seabattle:textures/gui/cell_empty.png" at -84 8 size 14x14 texture 14x14 anchor center layer 5 id m_5_5 run SbClickMy(player, 5, 5) keep open tooltip "&9Мой флот: &fД5"
		image "seabattle:textures/gui/cell_empty.png" at -70 8 size 14x14 texture 14x14 anchor center layer 5 id m_6_5 run SbClickMy(player, 6, 5) keep open tooltip "&9Мой флот: &fЕ5"
		image "seabattle:textures/gui/cell_empty.png" at -56 8 size 14x14 texture 14x14 anchor center layer 5 id m_7_5 run SbClickMy(player, 7, 5) keep open tooltip "&9Мой флот: &fЖ5"
		image "seabattle:textures/gui/cell_empty.png" at -42 8 size 14x14 texture 14x14 anchor center layer 5 id m_8_5 run SbClickMy(player, 8, 5) keep open tooltip "&9Мой флот: &fЗ5"
		image "seabattle:textures/gui/cell_empty.png" at -140 22 size 14x14 texture 14x14 anchor center layer 5 id m_1_6 run SbClickMy(player, 1, 6) keep open tooltip "&9Мой флот: &fА6"
		image "seabattle:textures/gui/cell_empty.png" at -126 22 size 14x14 texture 14x14 anchor center layer 5 id m_2_6 run SbClickMy(player, 2, 6) keep open tooltip "&9Мой флот: &fБ6"
		image "seabattle:textures/gui/cell_empty.png" at -112 22 size 14x14 texture 14x14 anchor center layer 5 id m_3_6 run SbClickMy(player, 3, 6) keep open tooltip "&9Мой флот: &fВ6"
		image "seabattle:textures/gui/cell_empty.png" at -98 22 size 14x14 texture 14x14 anchor center layer 5 id m_4_6 run SbClickMy(player, 4, 6) keep open tooltip "&9Мой флот: &fГ6"
		image "seabattle:textures/gui/cell_empty.png" at -84 22 size 14x14 texture 14x14 anchor center layer 5 id m_5_6 run SbClickMy(player, 5, 6) keep open tooltip "&9Мой флот: &fД6"
		image "seabattle:textures/gui/cell_empty.png" at -70 22 size 14x14 texture 14x14 anchor center layer 5 id m_6_6 run SbClickMy(player, 6, 6) keep open tooltip "&9Мой флот: &fЕ6"
		image "seabattle:textures/gui/cell_empty.png" at -56 22 size 14x14 texture 14x14 anchor center layer 5 id m_7_6 run SbClickMy(player, 7, 6) keep open tooltip "&9Мой флот: &fЖ6"
		image "seabattle:textures/gui/cell_empty.png" at -42 22 size 14x14 texture 14x14 anchor center layer 5 id m_8_6 run SbClickMy(player, 8, 6) keep open tooltip "&9Мой флот: &fЗ6"
		image "seabattle:textures/gui/cell_empty.png" at -140 36 size 14x14 texture 14x14 anchor center layer 5 id m_1_7 run SbClickMy(player, 1, 7) keep open tooltip "&9Мой флот: &fА7"
		image "seabattle:textures/gui/cell_empty.png" at -126 36 size 14x14 texture 14x14 anchor center layer 5 id m_2_7 run SbClickMy(player, 2, 7) keep open tooltip "&9Мой флот: &fБ7"
		image "seabattle:textures/gui/cell_empty.png" at -112 36 size 14x14 texture 14x14 anchor center layer 5 id m_3_7 run SbClickMy(player, 3, 7) keep open tooltip "&9Мой флот: &fВ7"
		image "seabattle:textures/gui/cell_empty.png" at -98 36 size 14x14 texture 14x14 anchor center layer 5 id m_4_7 run SbClickMy(player, 4, 7) keep open tooltip "&9Мой флот: &fГ7"
		image "seabattle:textures/gui/cell_empty.png" at -84 36 size 14x14 texture 14x14 anchor center layer 5 id m_5_7 run SbClickMy(player, 5, 7) keep open tooltip "&9Мой флот: &fД7"
		image "seabattle:textures/gui/cell_empty.png" at -70 36 size 14x14 texture 14x14 anchor center layer 5 id m_6_7 run SbClickMy(player, 6, 7) keep open tooltip "&9Мой флот: &fЕ7"
		image "seabattle:textures/gui/cell_empty.png" at -56 36 size 14x14 texture 14x14 anchor center layer 5 id m_7_7 run SbClickMy(player, 7, 7) keep open tooltip "&9Мой флот: &fЖ7"
		image "seabattle:textures/gui/cell_empty.png" at -42 36 size 14x14 texture 14x14 anchor center layer 5 id m_8_7 run SbClickMy(player, 8, 7) keep open tooltip "&9Мой флот: &fЗ7"
		image "seabattle:textures/gui/cell_empty.png" at -140 50 size 14x14 texture 14x14 anchor center layer 5 id m_1_8 run SbClickMy(player, 1, 8) keep open tooltip "&9Мой флот: &fА8"
		image "seabattle:textures/gui/cell_empty.png" at -126 50 size 14x14 texture 14x14 anchor center layer 5 id m_2_8 run SbClickMy(player, 2, 8) keep open tooltip "&9Мой флот: &fБ8"
		image "seabattle:textures/gui/cell_empty.png" at -112 50 size 14x14 texture 14x14 anchor center layer 5 id m_3_8 run SbClickMy(player, 3, 8) keep open tooltip "&9Мой флот: &fВ8"
		image "seabattle:textures/gui/cell_empty.png" at -98 50 size 14x14 texture 14x14 anchor center layer 5 id m_4_8 run SbClickMy(player, 4, 8) keep open tooltip "&9Мой флот: &fГ8"
		image "seabattle:textures/gui/cell_empty.png" at -84 50 size 14x14 texture 14x14 anchor center layer 5 id m_5_8 run SbClickMy(player, 5, 8) keep open tooltip "&9Мой флот: &fД8"
		image "seabattle:textures/gui/cell_empty.png" at -70 50 size 14x14 texture 14x14 anchor center layer 5 id m_6_8 run SbClickMy(player, 6, 8) keep open tooltip "&9Мой флот: &fЕ8"
		image "seabattle:textures/gui/cell_empty.png" at -56 50 size 14x14 texture 14x14 anchor center layer 5 id m_7_8 run SbClickMy(player, 7, 8) keep open tooltip "&9Мой флот: &fЖ8"
		image "seabattle:textures/gui/cell_empty.png" at -42 50 size 14x14 texture 14x14 anchor center layer 5 id m_8_8 run SbClickMy(player, 8, 8) keep open tooltip "&9Мой флот: &fЗ8"
		image "seabattle:textures/gui/cell_empty.png" at 42 -48 size 14x14 texture 14x14 anchor center layer 5 id e_1_1 run SbFire(player, 1, 1) keep open tooltip "&cВыстрел: &fА1"
		image "seabattle:textures/gui/cell_empty.png" at 56 -48 size 14x14 texture 14x14 anchor center layer 5 id e_2_1 run SbFire(player, 2, 1) keep open tooltip "&cВыстрел: &fБ1"
		image "seabattle:textures/gui/cell_empty.png" at 70 -48 size 14x14 texture 14x14 anchor center layer 5 id e_3_1 run SbFire(player, 3, 1) keep open tooltip "&cВыстрел: &fВ1"
		image "seabattle:textures/gui/cell_empty.png" at 84 -48 size 14x14 texture 14x14 anchor center layer 5 id e_4_1 run SbFire(player, 4, 1) keep open tooltip "&cВыстрел: &fГ1"
		image "seabattle:textures/gui/cell_empty.png" at 98 -48 size 14x14 texture 14x14 anchor center layer 5 id e_5_1 run SbFire(player, 5, 1) keep open tooltip "&cВыстрел: &fД1"
		image "seabattle:textures/gui/cell_empty.png" at 112 -48 size 14x14 texture 14x14 anchor center layer 5 id e_6_1 run SbFire(player, 6, 1) keep open tooltip "&cВыстрел: &fЕ1"
		image "seabattle:textures/gui/cell_empty.png" at 126 -48 size 14x14 texture 14x14 anchor center layer 5 id e_7_1 run SbFire(player, 7, 1) keep open tooltip "&cВыстрел: &fЖ1"
		image "seabattle:textures/gui/cell_empty.png" at 140 -48 size 14x14 texture 14x14 anchor center layer 5 id e_8_1 run SbFire(player, 8, 1) keep open tooltip "&cВыстрел: &fЗ1"
		image "seabattle:textures/gui/cell_empty.png" at 42 -34 size 14x14 texture 14x14 anchor center layer 5 id e_1_2 run SbFire(player, 1, 2) keep open tooltip "&cВыстрел: &fА2"
		image "seabattle:textures/gui/cell_empty.png" at 56 -34 size 14x14 texture 14x14 anchor center layer 5 id e_2_2 run SbFire(player, 2, 2) keep open tooltip "&cВыстрел: &fБ2"
		image "seabattle:textures/gui/cell_empty.png" at 70 -34 size 14x14 texture 14x14 anchor center layer 5 id e_3_2 run SbFire(player, 3, 2) keep open tooltip "&cВыстрел: &fВ2"
		image "seabattle:textures/gui/cell_empty.png" at 84 -34 size 14x14 texture 14x14 anchor center layer 5 id e_4_2 run SbFire(player, 4, 2) keep open tooltip "&cВыстрел: &fГ2"
		image "seabattle:textures/gui/cell_empty.png" at 98 -34 size 14x14 texture 14x14 anchor center layer 5 id e_5_2 run SbFire(player, 5, 2) keep open tooltip "&cВыстрел: &fД2"
		image "seabattle:textures/gui/cell_empty.png" at 112 -34 size 14x14 texture 14x14 anchor center layer 5 id e_6_2 run SbFire(player, 6, 2) keep open tooltip "&cВыстрел: &fЕ2"
		image "seabattle:textures/gui/cell_empty.png" at 126 -34 size 14x14 texture 14x14 anchor center layer 5 id e_7_2 run SbFire(player, 7, 2) keep open tooltip "&cВыстрел: &fЖ2"
		image "seabattle:textures/gui/cell_empty.png" at 140 -34 size 14x14 texture 14x14 anchor center layer 5 id e_8_2 run SbFire(player, 8, 2) keep open tooltip "&cВыстрел: &fЗ2"
		image "seabattle:textures/gui/cell_empty.png" at 42 -20 size 14x14 texture 14x14 anchor center layer 5 id e_1_3 run SbFire(player, 1, 3) keep open tooltip "&cВыстрел: &fА3"
		image "seabattle:textures/gui/cell_empty.png" at 56 -20 size 14x14 texture 14x14 anchor center layer 5 id e_2_3 run SbFire(player, 2, 3) keep open tooltip "&cВыстрел: &fБ3"
		image "seabattle:textures/gui/cell_empty.png" at 70 -20 size 14x14 texture 14x14 anchor center layer 5 id e_3_3 run SbFire(player, 3, 3) keep open tooltip "&cВыстрел: &fВ3"
		image "seabattle:textures/gui/cell_empty.png" at 84 -20 size 14x14 texture 14x14 anchor center layer 5 id e_4_3 run SbFire(player, 4, 3) keep open tooltip "&cВыстрел: &fГ3"
		image "seabattle:textures/gui/cell_empty.png" at 98 -20 size 14x14 texture 14x14 anchor center layer 5 id e_5_3 run SbFire(player, 5, 3) keep open tooltip "&cВыстрел: &fД3"
		image "seabattle:textures/gui/cell_empty.png" at 112 -20 size 14x14 texture 14x14 anchor center layer 5 id e_6_3 run SbFire(player, 6, 3) keep open tooltip "&cВыстрел: &fЕ3"
		image "seabattle:textures/gui/cell_empty.png" at 126 -20 size 14x14 texture 14x14 anchor center layer 5 id e_7_3 run SbFire(player, 7, 3) keep open tooltip "&cВыстрел: &fЖ3"
		image "seabattle:textures/gui/cell_empty.png" at 140 -20 size 14x14 texture 14x14 anchor center layer 5 id e_8_3 run SbFire(player, 8, 3) keep open tooltip "&cВыстрел: &fЗ3"
		image "seabattle:textures/gui/cell_empty.png" at 42 -6 size 14x14 texture 14x14 anchor center layer 5 id e_1_4 run SbFire(player, 1, 4) keep open tooltip "&cВыстрел: &fА4"
		image "seabattle:textures/gui/cell_empty.png" at 56 -6 size 14x14 texture 14x14 anchor center layer 5 id e_2_4 run SbFire(player, 2, 4) keep open tooltip "&cВыстрел: &fБ4"
		image "seabattle:textures/gui/cell_empty.png" at 70 -6 size 14x14 texture 14x14 anchor center layer 5 id e_3_4 run SbFire(player, 3, 4) keep open tooltip "&cВыстрел: &fВ4"
		image "seabattle:textures/gui/cell_empty.png" at 84 -6 size 14x14 texture 14x14 anchor center layer 5 id e_4_4 run SbFire(player, 4, 4) keep open tooltip "&cВыстрел: &fГ4"
		image "seabattle:textures/gui/cell_empty.png" at 98 -6 size 14x14 texture 14x14 anchor center layer 5 id e_5_4 run SbFire(player, 5, 4) keep open tooltip "&cВыстрел: &fД4"
		image "seabattle:textures/gui/cell_empty.png" at 112 -6 size 14x14 texture 14x14 anchor center layer 5 id e_6_4 run SbFire(player, 6, 4) keep open tooltip "&cВыстрел: &fЕ4"
		image "seabattle:textures/gui/cell_empty.png" at 126 -6 size 14x14 texture 14x14 anchor center layer 5 id e_7_4 run SbFire(player, 7, 4) keep open tooltip "&cВыстрел: &fЖ4"
		image "seabattle:textures/gui/cell_empty.png" at 140 -6 size 14x14 texture 14x14 anchor center layer 5 id e_8_4 run SbFire(player, 8, 4) keep open tooltip "&cВыстрел: &fЗ4"
		image "seabattle:textures/gui/cell_empty.png" at 42 8 size 14x14 texture 14x14 anchor center layer 5 id e_1_5 run SbFire(player, 1, 5) keep open tooltip "&cВыстрел: &fА5"
		image "seabattle:textures/gui/cell_empty.png" at 56 8 size 14x14 texture 14x14 anchor center layer 5 id e_2_5 run SbFire(player, 2, 5) keep open tooltip "&cВыстрел: &fБ5"
		image "seabattle:textures/gui/cell_empty.png" at 70 8 size 14x14 texture 14x14 anchor center layer 5 id e_3_5 run SbFire(player, 3, 5) keep open tooltip "&cВыстрел: &fВ5"
		image "seabattle:textures/gui/cell_empty.png" at 84 8 size 14x14 texture 14x14 anchor center layer 5 id e_4_5 run SbFire(player, 4, 5) keep open tooltip "&cВыстрел: &fГ5"
		image "seabattle:textures/gui/cell_empty.png" at 98 8 size 14x14 texture 14x14 anchor center layer 5 id e_5_5 run SbFire(player, 5, 5) keep open tooltip "&cВыстрел: &fД5"
		image "seabattle:textures/gui/cell_empty.png" at 112 8 size 14x14 texture 14x14 anchor center layer 5 id e_6_5 run SbFire(player, 6, 5) keep open tooltip "&cВыстрел: &fЕ5"
		image "seabattle:textures/gui/cell_empty.png" at 126 8 size 14x14 texture 14x14 anchor center layer 5 id e_7_5 run SbFire(player, 7, 5) keep open tooltip "&cВыстрел: &fЖ5"
		image "seabattle:textures/gui/cell_empty.png" at 140 8 size 14x14 texture 14x14 anchor center layer 5 id e_8_5 run SbFire(player, 8, 5) keep open tooltip "&cВыстрел: &fЗ5"
		image "seabattle:textures/gui/cell_empty.png" at 42 22 size 14x14 texture 14x14 anchor center layer 5 id e_1_6 run SbFire(player, 1, 6) keep open tooltip "&cВыстрел: &fА6"
		image "seabattle:textures/gui/cell_empty.png" at 56 22 size 14x14 texture 14x14 anchor center layer 5 id e_2_6 run SbFire(player, 2, 6) keep open tooltip "&cВыстрел: &fБ6"
		image "seabattle:textures/gui/cell_empty.png" at 70 22 size 14x14 texture 14x14 anchor center layer 5 id e_3_6 run SbFire(player, 3, 6) keep open tooltip "&cВыстрел: &fВ6"
		image "seabattle:textures/gui/cell_empty.png" at 84 22 size 14x14 texture 14x14 anchor center layer 5 id e_4_6 run SbFire(player, 4, 6) keep open tooltip "&cВыстрел: &fГ6"
		image "seabattle:textures/gui/cell_empty.png" at 98 22 size 14x14 texture 14x14 anchor center layer 5 id e_5_6 run SbFire(player, 5, 6) keep open tooltip "&cВыстрел: &fД6"
		image "seabattle:textures/gui/cell_empty.png" at 112 22 size 14x14 texture 14x14 anchor center layer 5 id e_6_6 run SbFire(player, 6, 6) keep open tooltip "&cВыстрел: &fЕ6"
		image "seabattle:textures/gui/cell_empty.png" at 126 22 size 14x14 texture 14x14 anchor center layer 5 id e_7_6 run SbFire(player, 7, 6) keep open tooltip "&cВыстрел: &fЖ6"
		image "seabattle:textures/gui/cell_empty.png" at 140 22 size 14x14 texture 14x14 anchor center layer 5 id e_8_6 run SbFire(player, 8, 6) keep open tooltip "&cВыстрел: &fЗ6"
		image "seabattle:textures/gui/cell_empty.png" at 42 36 size 14x14 texture 14x14 anchor center layer 5 id e_1_7 run SbFire(player, 1, 7) keep open tooltip "&cВыстрел: &fА7"
		image "seabattle:textures/gui/cell_empty.png" at 56 36 size 14x14 texture 14x14 anchor center layer 5 id e_2_7 run SbFire(player, 2, 7) keep open tooltip "&cВыстрел: &fБ7"
		image "seabattle:textures/gui/cell_empty.png" at 70 36 size 14x14 texture 14x14 anchor center layer 5 id e_3_7 run SbFire(player, 3, 7) keep open tooltip "&cВыстрел: &fВ7"
		image "seabattle:textures/gui/cell_empty.png" at 84 36 size 14x14 texture 14x14 anchor center layer 5 id e_4_7 run SbFire(player, 4, 7) keep open tooltip "&cВыстрел: &fГ7"
		image "seabattle:textures/gui/cell_empty.png" at 98 36 size 14x14 texture 14x14 anchor center layer 5 id e_5_7 run SbFire(player, 5, 7) keep open tooltip "&cВыстрел: &fД7"
		image "seabattle:textures/gui/cell_empty.png" at 112 36 size 14x14 texture 14x14 anchor center layer 5 id e_6_7 run SbFire(player, 6, 7) keep open tooltip "&cВыстрел: &fЕ7"
		image "seabattle:textures/gui/cell_empty.png" at 126 36 size 14x14 texture 14x14 anchor center layer 5 id e_7_7 run SbFire(player, 7, 7) keep open tooltip "&cВыстрел: &fЖ7"
		image "seabattle:textures/gui/cell_empty.png" at 140 36 size 14x14 texture 14x14 anchor center layer 5 id e_8_7 run SbFire(player, 8, 7) keep open tooltip "&cВыстрел: &fЗ7"
		image "seabattle:textures/gui/cell_empty.png" at 42 50 size 14x14 texture 14x14 anchor center layer 5 id e_1_8 run SbFire(player, 1, 8) keep open tooltip "&cВыстрел: &fА8"
		image "seabattle:textures/gui/cell_empty.png" at 56 50 size 14x14 texture 14x14 anchor center layer 5 id e_2_8 run SbFire(player, 2, 8) keep open tooltip "&cВыстрел: &fБ8"
		image "seabattle:textures/gui/cell_empty.png" at 70 50 size 14x14 texture 14x14 anchor center layer 5 id e_3_8 run SbFire(player, 3, 8) keep open tooltip "&cВыстрел: &fВ8"
		image "seabattle:textures/gui/cell_empty.png" at 84 50 size 14x14 texture 14x14 anchor center layer 5 id e_4_8 run SbFire(player, 4, 8) keep open tooltip "&cВыстрел: &fГ8"
		image "seabattle:textures/gui/cell_empty.png" at 98 50 size 14x14 texture 14x14 anchor center layer 5 id e_5_8 run SbFire(player, 5, 8) keep open tooltip "&cВыстрел: &fД8"
		image "seabattle:textures/gui/cell_empty.png" at 112 50 size 14x14 texture 14x14 anchor center layer 5 id e_6_8 run SbFire(player, 6, 8) keep open tooltip "&cВыстрел: &fЕ8"
		image "seabattle:textures/gui/cell_empty.png" at 126 50 size 14x14 texture 14x14 anchor center layer 5 id e_7_8 run SbFire(player, 7, 8) keep open tooltip "&cВыстрел: &fЖ8"
		image "seabattle:textures/gui/cell_empty.png" at 140 50 size 14x14 texture 14x14 anchor center layer 5 id e_8_8 run SbFire(player, 8, 8) keep open tooltip "&cВыстрел: &fЗ8"

		# Спрайт покадровой анимации взрыва / всплеска (24x24 поверх клеток)
		image "seabattle:textures/gui/anim_boom1.png" at 0 0 size 24x24 texture 24x24 anchor center layer 15 id fx hidden

		# Статус-строка внизу тетрадного листа
		text "&1Расставьте флот (или нажмите Авто-флот) и вызовите соперника!" at 0 70 anchor center layer 10 id status_txt

		# Нижняя панель управления: вызов игрока, расстановка, эффекты
		input "foe_input" at -118 96 size 92x18 anchor center placeholder "Ник соперника..." max 16 tooltip "&eВведите ник игрока\\n&7и нажмите Вызвать"
		button "&6⚔ Вызвать" at -40 96 size 58x18 anchor center run SbInviteFromInput(player) keep open tooltip "&6Отправить вызов игроку из поля ввода\\n&7(или принять его вызов)"
		button "Поворот [R]" at 22 96 size 62x18 anchor center run SbRotate(player) keep open id btn_rot tooltip "&bСменить ориентацию корабля\\n&7Горизонтально / Вертикально (клавиша R)"
		button "🎲 Авто-флот" at 84 96 size 60x18 anchor center run SbAutoFleet(player) keep open id btn_auto tooltip "&aАвтоматически расставить все 6 кораблей\\n&7по правилам без касаний"
		button "Очистить" at 142 96 size 52x18 anchor center run SbClearFleet(player) keep open id btn_clear tooltip "&cСтереть корабли и расставить заново"

		button "&a✔ Готов к бою!" at -110 116 size 106x18 anchor center run SbMarkReady(player) keep open id btn_ready tooltip "&aПодтвердить расстановку кораблей"
		toggle "Эффекты" id sfx_tog at 0 116 size 96x18 anchor center value true color #2563EB tooltip "&bТряска камеры и вспышки при попаданиях"
		button "Топ-5 / Выход" at 112 116 size 102x18 anchor center run SbTopAndLeave(player) keep open tooltip "&6Показать Топ-5 адмиралов / Сдаться"

	SbSyncFullBoard(p)

function SbResetBoard(p: player):
	set {-sb-dir::%p%} to 0
	set {-sb-placed::%p%} to 0
	set {-sb-decks-left::%p%} to 10
	set {-sb-ready::%p%} to 0
	# Корабли: 1x3 (Крейсер), 2x2 (Эсминцы), 3x1 (Катера) = 6 кораблей, 10 палуб
	set {-sb-slen::%p%::1} to 3
	set {-sb-slen::%p%::2} to 2
	set {-sb-slen::%p%::3} to 2
	set {-sb-slen::%p%::4} to 1
	set {-sb-slen::%p%::5} to 1
	set {-sb-slen::%p%::6} to 1
	loop 6 times:
		set {-s} to %loop-number%
		set {-sb-shits::%p%::%{-s}%} to 0
	loop 8 times:
		set {-r} to %loop-number%
		loop 8 times:
			set {-c} to %loop-number%
			set {-sb-my::%p%::%{-c}%_%{-r}%} to 0
			set {-sb-id::%p%::%{-c}%_%{-r}%} to 0
			set {-sb-shot::%p%::%{-c}%_%{-r}%} to 0

function SbSyncFullBoard(p: player):
	if p does not have screen "Морской Бой" open:
		stop
	loop 8 times:
		set {-r} to %loop-number%
		loop 8 times:
			set {-c} to %loop-number%
			set {-m} to {-sb-my::%p%::%{-c}%_%{-r}%}
			if {-m} is 0:
				set image of screen element "m_%{-c}%_%{-r}%" for p to "seabattle:textures/gui/cell_empty.png"
			else if {-m} is 1:
				set image of screen element "m_%{-c}%_%{-r}%" for p to "seabattle:textures/gui/cell_ship.png"
			else if {-m} is 2:
				set image of screen element "m_%{-c}%_%{-r}%" for p to "seabattle:textures/gui/cell_miss.png"
			else if {-m} is 3:
				set image of screen element "m_%{-c}%_%{-r}%" for p to "seabattle:textures/gui/cell_hit1.png"
			else if {-m} is 4:
				set image of screen element "m_%{-c}%_%{-r}%" for p to "seabattle:textures/gui/cell_sunk.png"

			set {-e} to {-sb-shot::%p%::%{-c}%_%{-r}%}
			if {-e} is 0:
				set image of screen element "e_%{-c}%_%{-r}%" for p to "seabattle:textures/gui/cell_empty.png"
			else if {-e} is 2:
				set image of screen element "e_%{-c}%_%{-r}%" for p to "seabattle:textures/gui/cell_miss.png"
			else if {-e} is 3:
				set image of screen element "e_%{-c}%_%{-r}%" for p to "seabattle:textures/gui/cell_hit1.png"
			else if {-e} is 4:
				set image of screen element "e_%{-c}%_%{-r}%" for p to "seabattle:textures/gui/cell_sunk.png"

	set value of screen bar "my_bar" for p to {-sb-decks-left::%p%}
	if {-sb-foe::%p%} is set:
		set {-foe} to {-sb-foe::%p%}
		set value of screen bar "foe_bar" for p to {-sb-decks-left::%{-foe}%}
	else:
		set value of screen bar "foe_bar" for p to 10
	SbUpdatePlacementStatus(p)

# --- Фаза 1: Расстановка кораблей ---

function SbUpdatePlacementStatus(p: player):
	if {-sb-state::%p%} is not "place":
		stop
	set {-idx} to {-sb-placed::%p%} + 1
	if {-idx} > 6:
		if {-sb-ready::%p%} is 1:
			set text of screen element "status_txt" for p to "&2✔ Флот готов! Ждём соперника (введите ник внизу и нажмите Вызвать)"
		else:
			set text of screen element "status_txt" for p to "&2Все 6 кораблей расставлены! Нажмите &a✔ Готов к бою!"
		stop
	set {-len} to {-sb-slen::%p%::%{-idx}%}
	set {-dtxt} to "Горизонтально ➡"
	if {-sb-dir::%p%} is 1:
		set {-dtxt} to "Вертикально ⬇"
	set text of screen element "status_txt" for p to "&1Поставьте корабль #%{-idx}%/6 (&f%{-len}%-палубный&1, %{-dtxt}%) или жмите &2Авто-флот"

function SbRotate(p: player):
	if {-sb-state::%p%} is not "place":
		stop
	if {-sb-dir::%p%} is 0:
		set {-sb-dir::%p%} to 1
	else:
		set {-sb-dir::%p%} to 0
	play sound ui.button.click to p with volume 0.4 pitch 1.4
	SbUpdatePlacementStatus(p)

function SbClearFleet(p: player):
	if {-sb-state::%p%} is not "place":
		set text of screen element "status_txt" for p to "&cВо время боя менять расстановку нельзя!"
		stop
	SbResetBoard(p)
	SbSyncFullBoard(p)
	play sound item.book.page_turn to p with volume 0.7 pitch 1.1

function SbClickMy(p: player, col: number, row: number):
	if {-sb-state::%p%} is not "place":
		stop
	set {-idx} to {-sb-placed::%p%} + 1
	if {-idx} > 6:
		set text of screen element "status_txt" for p to "&2Все корабли уже стоят! Нажмите &a✔ Готов к бою! &2или &cОчистить"
		stop
	set {-len} to {-sb-slen::%p%::%{-idx}%}
	set {-dir} to {-sb-dir::%p%}
	SbTryPlaceShip(p, {-idx}, col, row, {-len}, {-dir}, true)

function SbTryPlaceShip(p: player, shipId: number, col: number, row: number, len: number, dir: number, notify: boolean):
	set {-ok} to 1
	loop len times:
		set {-step} to %loop-number% - 1
		set {-cx} to col
		set {-cy} to row
		if dir is 0:
			add {-step} to {-cx}
		else:
			add {-step} to {-cy}
		if {-cx} > 8 or {-cy} > 8:
			set {-ok} to 0
		else:
			# Проверяем саму клетку и 8 соседей вокруг (корабли не могут касаться друг друга!)
			loop 3 times:
				set {-ny} to {-cy} + %loop-number% - 2
				loop 3 times:
					set {-nx} to {-cx} + %loop-number% - 2
					if {-nx} >= 1 and {-nx} <= 8 and {-ny} >= 1 and {-ny} <= 8:
						if {-sb-my::%p%::%{-nx}%_%{-ny}%} is 1:
							set {-ok} to 0

	if {-ok} is 0:
		if notify is true:
			set text of screen element "status_txt" for p to "&cСюда нельзя: корабли не должны выходить за край или касаться друг друга!"
			play sound entity.villager.no to p with volume 0.5 pitch 1.1
		set {-sb-place-res::%p%} to 0
		stop

	loop len times:
		set {-step} to %loop-number% - 1
		set {-cx} to col
		set {-cy} to row
		if dir is 0:
			add {-step} to {-cx}
		else:
			add {-step} to {-cy}
		set {-sb-my::%p%::%{-cx}%_%{-cy}%} to 1
		set {-sb-id::%p%::%{-cx}%_%{-cy}%} to shipId
		if notify is true:
			set image of screen element "m_%{-cx}%_%{-cy}%" for p to "seabattle:textures/gui/cell_ship.png"

	add 1 to {-sb-placed::%p%}
	set {-sb-place-res::%p%} to 1
	if notify is true:
		play sound entity.villager.work_cartographer to p with volume 0.6 pitch 1.3
		SbUpdatePlacementStatus(p)

function SbAutoFleet(p: player):
	if {-sb-state::%p%} is not "place":
		set text of screen element "status_txt" for p to "&cВо время боя менять расстановку нельзя!"
		stop
	SbResetBoard(p)
	loop 6 times:
		set {-sid} to %loop-number%
		set {-slen} to {-sb-slen::%p%::%{-sid}%}
		set {-done} to 0
		set {-tries} to 0
		while {-done} is 0 and {-tries} < 80:
			add 1 to {-tries}
			set {-rc} to random integer between 1 and 8
			set {-rr} to random integer between 1 and 8
			set {-rd} to random integer between 0 and 1
			SbTryPlaceShip(p, {-sid}, {-rc}, {-rr}, {-slen}, {-rd}, false)
			if {-sb-place-res::%p%} is 1:
				set {-done} to 1
	SbSyncFullBoard(p)
	play sound item.book.page_turn to p with volume 0.8 pitch 1.25

function SbMarkReady(p: player):
	if {-sb-state::%p%} is not "place":
		stop
	if {-sb-placed::%p%} < 6:
		SbAutoFleet(p)
	set {-sb-ready::%p%} to 1
	play sound entity.experience_orb.pickup to p with volume 0.7 pitch 1.3
	SbUpdatePlacementStatus(p)
	if {-sb-foe::%p%} is set:
		set {-foe} to {-sb-foe::%p%}
		if {-sb-ready::%{-foe}%} is 1:
			SbBeginBattle(p, {-foe})
		else:
			set text of screen element "status_txt" for p to "&2✔ Вы готовы! Ожидаем, пока &e%{-foe}% &2расставит корабли..."

# --- Вызов соперника и старт дуэли ---

function SbInviteFromInput(p: player):
	set {-raw} to value of screen element "foe_input" of p
	if {-raw} is not set or {-raw} is "":
		set text of screen element "status_txt" for p to "&cВведите ник игрока в поле слева от кнопки Вызвать!"
		stop
	set {-found} to 0
	loop all players:
		if "%loop-player%" is "%{-raw}%":
			set {-found} to 1
			SbChallenge(p, loop-player)
	if {-found} is 0:
		set text of screen element "status_txt" for p to "&cИгрок '%{-raw}%' не найден на сервере!"
		play sound entity.villager.no to p with volume 0.6 pitch 1.0

function SbChallenge(p: player, target: player):
	if "%p%" is "%target%":
		send "&cНельзя вызвать на Морской Бой самого себя!" to p
		stop
	if {-sb-state::%p%} is "battle":
		send "&cВы уже ведёте морской бой!" to p
		stop
	if {-sb-state::%target%} is "battle":
		send "&cИгрок %target% сейчас уже в бою!" to p
		stop

	if p does not have screen "Морской Бой" open:
		SbOpenNotebook(p)

	# Если target уже вызывал p — сразу связываем пару!
	if {-sb-invite::%target%} is "%p%":
		delete {-sb-invite::%target%}
		delete {-sb-invite::%p%}
		set {-sb-foe::%p%} to "%target%"
		set {-sb-foe::%target%} to "%p%"
		if target does not have screen "Морской Бой" open:
			SbOpenNotebook(target)
		send "&aДуэль Морского Боя с &e%target% &aпринята! Расставьте корабли и жмите Готов!" to p
		send "&aДуэль Морского Боя с &e%p% &aначалась! Расставьте корабли и жмите Готов!" to target
		set text of screen element "status_txt" for p to "&2Соперник: &e%target%&2! Расставьте флот и нажмите &a✔ Готов к бою!"
		set text of screen element "status_txt" for target to "&2Соперник: &e%p%&2! Расставьте флот и нажмите &a✔ Готов к бою!"
		if {-sb-ready::%p%} is 1 and {-sb-ready::%target%} is 1:
			SbBeginBattle(p, "%target%")
		stop

	set {-sb-invite::%p%} to "%target%"
	send "&eВы вызвали &f%target% &eна Морской Бой! Ожидаем ответа..." to p
	send "&6⚓ &e%p% &fвызывает вас в &bМорской Бой&f! Напишите &a/seabattle %p% &f(или введите его ник в \`/sb\`)" to target
	play sound entity.experience_orb.pickup to target with volume 0.8 pitch 1.2
	set text of screen element "status_txt" for p to "&eВызов отправлен игроку &f%target%&e! Пока расставьте флот."

function SbBeginBattle(p1: player, p2Name: text):
	loop all players:
		if "%loop-player%" is p2Name:
			SbStartMatch(p1, loop-player)

function SbStartMatch(p1: player, p2: player):
	set {-sb-state::%p1%} to "battle"
	set {-sb-state::%p2%} to "battle"
	set {-sb-turn::%p1%} to 1
	set {-sb-turn::%p2%} to 0
	set {-sb-lock::%p1%} to 0
	set {-sb-lock::%p2%} to 0
	set text of screen element "lbl_foe" for p1 to "&4&lРАДАР: %p2%"
	set text of screen element "lbl_foe" for p2 to "&4&lРАДАР: %p1%"
	set text of screen element "status_txt" for p1 to "&2&lВАШ ХОД! &1Выберите клетку на правом поле (Радар врага)"
	set text of screen element "status_txt" for p2 to "&6Ход соперника (&e%p1%&6)... Приготовьтесь!"
	play sound block.note_block.pling to p1 with volume 0.8 pitch 1.4
	play sound block.note_block.pling to p2 with volume 0.8 pitch 1.0

# --- Фаза 2: Выстрелы и покадровая анимация ---

function SbFire(p: player, col: number, row: number):
	if {-sb-state::%p%} is not "battle":
		if {-sb-state::%p%} is "place":
			set text of screen element "status_txt" for p to "&cСначала расставьте флот слева, нажмите Готов и вызовите соперника!"
		stop
	if {-sb-turn::%p%} is not 1 or {-sb-lock::%p%} is 1:
		set text of screen element "status_txt" for p to "&eСейчас ход соперника!"
		stop
	if {-sb-shot::%p%::%col%_%row%} is not 0:
		set text of screen element "status_txt" for p to "&cВы уже стреляли в эту клетку!"
		stop

	set {-foeName} to {-sb-foe::%p%}
	loop all players:
		if "%loop-player%" is "%{-foeName}%":
			SbExecuteShot(p, loop-player, col, row)

function SbExecuteShot(attacker: player, defender: player, col: number, row: number):
	set {-sb-lock::%attacker%} to 1
	set {-ex} to 42 + (col - 1) * 14
	set {-mx} to -140 + (col - 1) * 14
	set {-sy} to -48 + (row - 1) * 14

	set {-cell} to {-sb-my::%defender%::%col%_%row%}
	if {-cell} is 1:
		# --- ПОПАДАНИЕ! Покадровая анимация взрыва (3 кадра) ---
		set {-sb-my::%defender%::%col%_%row%} to 3
		set {-sb-shot::%attacker%::%col%_%row%} to 3
		remove 1 from {-sb-decks-left::%defender%}
		set {-sid} to {-sb-id::%defender%::%col%_%row%}
		add 1 to {-sb-shits::%defender%::%{-sid}%}

		set position of screen element "fx" for attacker to {-ex} {-sy}
		set position of screen element "fx" for defender to {-mx} {-sy}
		set image of screen element "fx" for attacker to "seabattle:textures/gui/anim_boom1.png"
		set image of screen element "fx" for defender to "seabattle:textures/gui/anim_boom1.png"
		show screen element "fx" to attacker
		show screen element "fx" to defender
		play sound entity.generic.explode to attacker with volume 0.5 pitch 1.3
		play sound entity.generic.explode to defender with volume 0.7 pitch 0.9

		if {-sb-sfx::%defender%} is 1:
			shake camera of defender with intensity 2.2 for 12 ticks
			fade screen of defender to #FF2200 in 2 stay 2 out 8 ticks

		wait 2 ticks
		set image of screen element "fx" for attacker to "seabattle:textures/gui/anim_boom2.png"
		set image of screen element "fx" for defender to "seabattle:textures/gui/anim_boom2.png"
		wait 2 ticks
		set image of screen element "fx" for attacker to "seabattle:textures/gui/anim_boom3.png"
		set image of screen element "fx" for defender to "seabattle:textures/gui/anim_boom3.png"
		wait 2 ticks
		hide screen element "fx" from attacker
		hide screen element "fx" from defender

		set image of screen element "e_%col%_%row%" for attacker to "seabattle:textures/gui/cell_hit1.png"
		set image of screen element "m_%col%_%row%" for defender to "seabattle:textures/gui/cell_hit1.png"
		set value of screen bar "foe_bar" for attacker to {-sb-decks-left::%defender%}
		set value of screen bar "my_bar" for defender to {-sb-decks-left::%defender%}

		# Проверяем, потоплен ли корабль целиком
		set {-slen} to {-sb-slen::%defender%::%{-sid}%}
		if {-sb-shits::%defender%::%{-sid}%} >= {-slen}:
			SbMarkShipSunk(attacker, defender, {-sid})
			set text of screen element "status_txt" for attacker to "&4&lПОТОПЛЕН! &2Вы уничтожили корабль врага! Стреляйте ещё раз!"
			set text of screen element "status_txt" for defender to "&4&lНаш %{-slen}%-палубный корабль потоплен! &6Враг стреляет снова..."
		else:
			set text of screen element "status_txt" for attacker to "&c&lПОПАДАНИЕ! &2Ваш дополнительный ход — стреляйте ещё!"
			set text of screen element "status_txt" for defender to "&cВ наш корабль попали! &6Соперник делает ещё выстрел..."

		set {-sb-lock::%attacker%} to 0
		if {-sb-decks-left::%defender%} <= 0:
			SbVictory(attacker, defender)
	else:
		# --- ПРОМАХ! Покадровая анимация всплеска чернил (3 кадра) ---
		set {-sb-my::%defender%::%col%_%row%} to 2
		set {-sb-shot::%attacker%::%col%_%row%} to 2

		set position of screen element "fx" for attacker to {-ex} {-sy}
		set position of screen element "fx" for defender to {-mx} {-sy}
		set image of screen element "fx" for attacker to "seabattle:textures/gui/anim_splash1.png"
		set image of screen element "fx" for defender to "seabattle:textures/gui/anim_splash1.png"
		show screen element "fx" to attacker
		show screen element "fx" to defender
		play sound entity.fishing_bobber.splash to attacker with volume 0.5 pitch 1.3
		play sound entity.fishing_bobber.splash to defender with volume 0.5 pitch 1.3

		wait 2 ticks
		set image of screen element "fx" for attacker to "seabattle:textures/gui/anim_splash2.png"
		set image of screen element "fx" for defender to "seabattle:textures/gui/anim_splash2.png"
		wait 2 ticks
		set image of screen element "fx" for attacker to "seabattle:textures/gui/anim_splash3.png"
		set image of screen element "fx" for defender to "seabattle:textures/gui/anim_splash3.png"
		wait 2 ticks
		hide screen element "fx" from attacker
		hide screen element "fx" from defender

		set image of screen element "e_%col%_%row%" for attacker to "seabattle:textures/gui/cell_miss.png"
		set image of screen element "m_%col%_%row%" for defender to "seabattle:textures/gui/cell_miss.png"

		# Переход хода
		set {-sb-turn::%attacker%} to 0
		set {-sb-turn::%defender%} to 1
		set {-sb-lock::%attacker%} to 0
		set {-sb-lock::%defender%} to 0
		set text of screen element "status_txt" for attacker to "&9Мимо! &6Теперь ход соперника (&e%defender%&6)..."
		set text of screen element "status_txt" for defender to "&2&lВАШ ХОД! &1Соперник промахнулся — огонь по радару справа!"
		play sound block.note_block.pling to defender with volume 0.7 pitch 1.4

function SbMarkShipSunk(attacker: player, defender: player, shipId: number):
	loop 8 times:
		set {-r} to %loop-number%
		loop 8 times:
			set {-c} to %loop-number%
			if {-sb-id::%defender%::%{-c}%_%{-r}%} is shipId:
				set {-sb-my::%defender%::%{-c}%_%{-r}%} to 4
				set {-sb-shot::%attacker%::%{-c}%_%{-r}%} to 4
				set image of screen element "e_%{-c}%_%{-r}%" for attacker to "seabattle:textures/gui/cell_sunk.png"
				set image of screen element "m_%{-c}%_%{-r}%" for defender to "seabattle:textures/gui/cell_sunk.png"
				# Авто-обводка ореола вокруг потопленного корабля точками промаха
				loop 3 times:
					set {-ny} to {-r} + %loop-number% - 2
					loop 3 times:
						set {-nx} to {-c} + %loop-number% - 2
						if {-nx} >= 1 and {-nx} <= 8 and {-ny} >= 1 and {-ny} <= 8:
							if {-sb-shot::%attacker%::%{-nx}%_%{-ny}%} is 0:
								set {-sb-shot::%attacker%::%{-nx}%_%{-ny}%} to 2
								set image of screen element "e_%{-nx}%_%{-ny}%" for attacker to "seabattle:textures/gui/cell_miss.png"
							if {-sb-my::%defender%::%{-nx}%_%{-ny}%} is 0:
								set {-sb-my::%defender%::%{-nx}%_%{-ny}%} to 2
								set image of screen element "m_%{-nx}%_%{-ny}%" for defender to "seabattle:textures/gui/cell_miss.png"

function SbVictory(winner: player, loser: player):
	set {-sb-state::%winner%} to "over"
	set {-sb-state::%loser%} to "over"
	set {-sb-turn::%winner%} to 0
	set {-sb-turn::%loser%} to 0
	if {sb::wins::%winner%} is not set:
		set {sb::wins::%winner%} to 1
	else:
		add 1 to {sb::wins::%winner%}
	set text of screen element "status_txt" for winner to "&2&l⚓ ПОБЕДА! Весь флот %loser% потоплен! (Побед: %{sb::wins::%winner%}%)"
	set text of screen element "status_txt" for loser to "&4&l⚓ ПОРАЖЕНИЕ! Ваш флот потоплен адмиралом %winner%!"
	play sound ui.toast.challenge_complete to winner with volume 0.9 pitch 1.1
	play sound entity.iron_golem.death to loser with volume 0.7 pitch 0.8
	spawn particle ring dust at winner radius 2.2 points 32 color #FFD700
	broadcast "&9⚓ &e%winner% &fразгромил флот &e%loser% &fв тетрадном &bМорском Бою&f!"

function SbTopAndLeave(p: player):
	send "&9&l=== ⚓ ТОП-5 АДМИРАЛОВ МОРСКОГО БОЯ ===" to p
	if {sb::wins::*} is empty:
		send "&7Пока нет побед!" to p
	else:
		set {-r} to 0
		loop {sb::wins::*} sorted descending:
			add 1 to {-r}
			send "&e#%{-r}% &f%loop-index% &7— &a%loop-value% побед" to p
			if {-r} >= 5:
				stop
	SbLeave(p)
	close screen of p

function SbOnScreenClose(p: player):
	if {-sb-state::%p%} is "battle" or {-sb-state::%p%} is "over":
		SbLeave(p)

function SbLeave(p: player):
	if {-sb-foe::%p%} is set:
		set {-foeName} to {-sb-foe::%p%}
		delete {-sb-foe::%p%}
		loop all players:
			if "%loop-player%" is "%{-foeName}%":
				delete {-sb-foe::%loop-player%}
				if {-sb-state::%loop-player%} is "battle":
					set {-sb-state::%loop-player%} to "over"
					send "&eСоперник %p% покинул Морской Бой — техническая победа!" to loop-player
					set text of screen element "status_txt" for loop-player to "&2Соперник %p% сдался! Вы победили!"
	delete {-sb-state::%p%}
	delete {-sb-invite::%p%}
	delete {-sb-ready::%p%}
	delete {-sb-turn::%p%}
	delete {-sb-lock::%p%}
` }
    ],
    notes: {
      ru: [
        "Распакуйте seabattle-resources.zip в scriptcraft/resources/ на сервере (получится scriptcraft/resources/assets/seabattle/textures/gui/*.png) и выполните /sc reload resources.",
        "Фаза расстановки: кликайте по левому полю «МОЙ ФЛОТ» (клавиша R поворачивает корабль) либо нажмите «🎲 Авто-флот», затем нажмите «✔ Готов к бою!» и введите ник соперника в поле input (или используйте /seabattle <ник>).",
        "Фаза боя: стреляйте по правому полю «РАДАР ВРАГА». При попадании проигрывается 3-кадровая анимация взрыва (anim_boom1..3), у защитника трясётся камера и вспыхивает экран, а стрелок получает дополнительный ход. При потоплении корабля клетки вокруг него автоматически помечаются точками."
      ],
      en: [
        "Extract seabattle-resources.zip into scriptcraft/resources/ on your server (creating scriptcraft/resources/assets/seabattle/textures/gui/*.png) and run /sc reload resources.",
        "Placement phase: click cells on the left 'MY FLEET' grid (press R to rotate) or click 'Auto-Fleet', then click 'Ready!' and invite an opponent using the input box (or /seabattle <player>).",
        "Battle phase: fire on the right 'ENEMY RADAR' grid. Hits play a 3-frame explosion animation (anim_boom1..3), shake and flash the defender's camera, and grant an extra turn. Sinking a ship automatically marks its surrounding halo cells."
      ]
    },
    related: ["eff-open-screen", "screen-dynamic", "screen-input", "screen-toggle", "screen-tooltip", "eff-camera", "loop-sorted"]
  }
];
