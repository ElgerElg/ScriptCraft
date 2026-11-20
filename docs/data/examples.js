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
    title: { ru: "Ультимативная Змейка в GUI", en: "Ultimate Snake in GUI" },
    intro: {
      ru: "Полноценная аркадная Змейка внутри кастомного окна: управление WASD и стрелками без закрытия GUI, динамическое добавление сегментов хвоста на лету, кольцевой буфер O(1), режимы скорости и портальных стен. Включить: /sc enable snake, запуск: /snake.",
      en: "A full arcade Snake game inside a custom GUI window: WASD and arrow keys without closing the GUI, dynamic tail growth on the fly, an O(1) ring buffer, speed modes and portal walls. Enable with /sc enable snake, play with /snake."
    },
    blocks: [
      { lang: "sc", title: { ru: "scriptcraft/scripts/snake.sc", en: "scriptcraft/scripts/snake.sc" }, code: `# ==========================================================
#  snake.sc — Ультимативная ЗМЕЙКА в GUI (/snake)
#  Демонстрирует новые возможности GUI-экранов ScriptCraft:
#    - open screen / update screen (добавление сегментов хвоста на лету без пересоздания окна)
#    - move screen / set text of screen / set value of screen bar / show & hide screen
#    - keep open + key <клавиша> на кнопках и on key ... in screen "Змейка": (WASD и стрелки в любой раскладке)
#    - on screen close "Змейка": и условие if loop-player has screen "Змейка" open:
#    - кольцевой буфер сегментов хвоста: за тик двигаются только голова и кончик хвоста!
# ==========================================================

command /snake:
	aliases: zmeyka, arcade
	SnakeStart(player)

function SnakeStart(p: player):
	if {snake_best::%p%} is not set:
		set {snake_best::%p%} to 0
	if {-snake_speed::%p%} is not set:
		set {-snake_speed::%p%} to 2
	if {-snake_walls::%p%} is not set:
		set {-snake_walls::%p%} to "portal"
	set {-snake_score::%p%} to 0
	set {-snake_len::%p%} to 3
	set {-snake_ring::%p%} to 1
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

	open screen "Змейка" to p:
		# Фоновая рамка и игровое поле 15x11 клеток (шаг 12px: X от -84 до 84, Y от -60 до 60)
		bar "board_border" value 100 max 100 at 0, 0 size 186x146 color #1E293B background #E60B0F17 layer 0
		bar "board_inner" value 100 max 100 at 0, 0 size 182x142 color #0F172A background #F20F172A layer 1

		# Верхняя панель статистики
		text "&a&lSNAKE ARCADE" at 0, -102 color #4ADE80 shadow layer 4 id title_lbl
		text "&fСчёт: &a0  &8|  &7Рекорд: &6%{snake_best::%p%}%  &8|  &7Длина: &b4" at 0, -90 color #FFFFFF shadow layer 4 id score_lbl
		bar "bonus_bar" value 0 max 40 at 0, -78 size 182x5 color #FACC15 background #55000000 layer 3

		# Еда, бонусное золотое яблоко (скрыто до появления) и стартовая змейка
		slot apple 1 at 36, 0 size 12x12 no frame layer 2 id apple
		slot enchanted_golden_apple 1 at 0, -36 size 12x12 no frame layer 2 id bonus hidden
		slot emerald_block 1 at 0, 0 size 12x12 no frame layer 4 id head
		slot lime_concrete 1 at -12, 0 size 12x12 no frame layer 3 id seg_1
		slot lime_concrete 1 at -24, 0 size 12x12 no frame layer 3 id seg_2
		slot lime_concrete 1 at -36, 0 size 12x12 no frame layer 3 id seg_3

		# Оверлей паузы / конца игры
		text "&e&lПАУЗА" at 0, 0 color #FACC15 shadow layer 6 id status_banner hidden

		# Левая колонка: управление (кнопки с keep open + горячие клавиши стрелок)
		text "&7Управление" at -132, -52 color #94A3B8 shadow layer 4
		button "▲" at -132, -32 size 24x20 run SnakeDir(player, "up") keep open key up layer 4 id btn_up
		button "◄" at -158, -10 size 24x20 run SnakeDir(player, "left") keep open key left layer 4 id btn_left
		button "►" at -106, -10 size 24x20 run SnakeDir(player, "right") keep open key right layer 4 id btn_right
		button "▼" at -132, 12 size 24x20 run SnakeDir(player, "down") keep open key down layer 4 id btn_down
		button "Пауза [Space]" at -132, 40 size 78x18 run SnakeTogglePause(player) keep open key space layer 4 id btn_pause

		# Правая колонка: режимы скорости и стен без закрытия экрана
		text "&7Режимы" at 136, -52 color #94A3B8 shadow layer 4
		button "Скорость: Норма" at 136, -32 size 88x18 run SnakeCycleSpeed(player) keep open layer 4 id btn_speed
		button "Стены: Портал" at 136, -10 size 88x18 run SnakeToggleWalls(player) keep open layer 4 id btn_walls
		button "Заново [R]" at 136, 18 size 88x18 run SnakeStart(player) keep open key r layer 4 id btn_restart
		button "Выход" at 136, 42 size 88x18 close layer 4

		text "&8WASD / Стрелки — ход   |   Space — пауза   |   R — рестарт" at 0, 84 color #64748B layer 4 id hint_lbl

	SnakeRefreshModeButtons(p)
	play sound block.note_block.pling to p with volume 0.7 pitch 1.4

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
		SnakeStart(p)
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
	if dir is "up" and {-snake_dir::%p%} is not "down":
		set {-snake_next::%p%} to "up"
	else if dir is "down" and {-snake_dir::%p%} is not "up":
		set {-snake_next::%p%} to "down"
	else if dir is "left" and {-snake_dir::%p%} is not "right":
		set {-snake_next::%p%} to "left"
	else if dir is "right" and {-snake_dir::%p%} is not "left":
		set {-snake_next::%p%} to "right"

# WASD работает напрямую в открытом экране независимо от раскладки клавиатуры
on key w in screen "Змейка":
	SnakeDir(player, "up")

on key s in screen "Змейка":
	SnakeDir(player, "down")

on key a in screen "Змейка":
	SnakeDir(player, "left")

on key d in screen "Змейка":
	SnakeDir(player, "right")

# При закрытии окна змейки останавливаем сессию игрока
on screen close "Змейка":
	set {-snake_state::%player%} to "closed"

function SnakeSpawnApple(p: player):
	set {-col} to random integer between -7 and 7
	set {-row} to random integer between -5 and 5
	set {-ax::%p%} to {-col} * 12
	set {-ay::%p%} to {-row} * 12
	move screen "apple" for p to {-ax::%p%}, {-ay::%p%}

function SnakeUpdateBonusBar(p: player):
	update screen "Змейка" for p:
		bar "bonus_bar" value {-snake_bonus_t::%p%} max 40 at 0, -78 size 182x5 color #FACC15 background #55000000 layer 3

function SnakeSpawnBonus(p: player):
	set {-col} to random integer between -7 and 7
	set {-row} to random integer between -5 and 5
	set {-bx::%p%} to {-col} * 12
	set {-by::%p%} to {-row} * 12
	set {-snake_bonus_t::%p%} to 40
	move screen "bonus" for p to {-bx::%p%}, {-by::%p%}
	show screen "bonus" for p
	SnakeUpdateBonusBar(p)
	play sound block.amethyst_block.chime to p with volume 0.9 pitch 1.3

function SnakeUpdateHud(p: player):
	set {-full_len} to {-snake_len::%p%} + 1
	set text of screen "score_lbl" for p to "&fСчёт: &a%{-snake_score::%p%}%  &8|  &7Рекорд: &6%{snake_best::%p%}%  &8|  &7Длина: &b%{-full_len}%"

function SnakeGameOver(p: player, reason: text):
	set {-snake_state::%p%} to "over"
	set text of screen "status_banner" for p to "&c&lИГРА ОКОНЧЕНА! &7(%reason% — нажми R или Space)"
	show screen "status_banner" for p
	play sound entity.villager.no to p with volume 0.8 pitch 0.9

function SnakeAddSeg(p: player, id: integer, x: integer, y: integer):
	set {-sx::%p%::%id%} to x
	set {-sy::%p%::%id%} to y
	if id is 4:
		update screen "Змейка" for p:
			slot lime_concrete 1 at 0, 0 size 12x12 no frame layer 3 id seg_4
	else if id is 5:
		update screen "Змейка" for p:
			slot lime_concrete 1 at 0, 0 size 12x12 no frame layer 3 id seg_5
	else if id is 6:
		update screen "Змейка" for p:
			slot lime_concrete 1 at 0, 0 size 12x12 no frame layer 3 id seg_6
	else if id is 7:
		update screen "Змейка" for p:
			slot lime_concrete 1 at 0, 0 size 12x12 no frame layer 3 id seg_7
	else if id is 8:
		update screen "Змейка" for p:
			slot lime_concrete 1 at 0, 0 size 12x12 no frame layer 3 id seg_8
	else if id is 9:
		update screen "Змейка" for p:
			slot lime_concrete 1 at 0, 0 size 12x12 no frame layer 3 id seg_9
	else if id is 10:
		update screen "Змейка" for p:
			slot lime_concrete 1 at 0, 0 size 12x12 no frame layer 3 id seg_10
	else if id is 11:
		update screen "Змейка" for p:
			slot lime_concrete 1 at 0, 0 size 12x12 no frame layer 3 id seg_11
	else if id is 12:
		update screen "Змейка" for p:
			slot lime_concrete 1 at 0, 0 size 12x12 no frame layer 3 id seg_12
	else if id is 13:
		update screen "Змейка" for p:
			slot lime_concrete 1 at 0, 0 size 12x12 no frame layer 3 id seg_13
	else if id is 14:
		update screen "Змейка" for p:
			slot lime_concrete 1 at 0, 0 size 12x12 no frame layer 3 id seg_14
	else if id is 15:
		update screen "Змейка" for p:
			slot lime_concrete 1 at 0, 0 size 12x12 no frame layer 3 id seg_15
	else if id is 16:
		update screen "Змейка" for p:
			slot lime_concrete 1 at 0, 0 size 12x12 no frame layer 3 id seg_16
	else if id is 17:
		update screen "Змейка" for p:
			slot lime_concrete 1 at 0, 0 size 12x12 no frame layer 3 id seg_17
	else if id is 18:
		update screen "Змейка" for p:
			slot lime_concrete 1 at 0, 0 size 12x12 no frame layer 3 id seg_18
	else if id is 19:
		update screen "Змейка" for p:
			slot lime_concrete 1 at 0, 0 size 12x12 no frame layer 3 id seg_19
	else if id is 20:
		update screen "Змейка" for p:
			slot lime_concrete 1 at 0, 0 size 12x12 no frame layer 3 id seg_20
	else if id is 21:
		update screen "Змейка" for p:
			slot lime_concrete 1 at 0, 0 size 12x12 no frame layer 3 id seg_21
	else if id is 22:
		update screen "Змейка" for p:
			slot lime_concrete 1 at 0, 0 size 12x12 no frame layer 3 id seg_22
	else if id is 23:
		update screen "Змейка" for p:
			slot lime_concrete 1 at 0, 0 size 12x12 no frame layer 3 id seg_23
	else if id is 24:
		update screen "Змейка" for p:
			slot lime_concrete 1 at 0, 0 size 12x12 no frame layer 3 id seg_24
	SnakeMoveSeg(p, id, x, y)

function SnakeMoveSeg(p: player, id: integer, x: integer, y: integer):
	set {-sx::%p%::%id%} to x
	set {-sy::%p%::%id%} to y
	set {-mx} to x
	set {-my} to y
	if id is 1:
		move screen "seg_1" for p to {-mx}, {-my}
	else if id is 2:
		move screen "seg_2" for p to {-mx}, {-my}
	else if id is 3:
		move screen "seg_3" for p to {-mx}, {-my}
	else if id is 4:
		move screen "seg_4" for p to {-mx}, {-my}
	else if id is 5:
		move screen "seg_5" for p to {-mx}, {-my}
	else if id is 6:
		move screen "seg_6" for p to {-mx}, {-my}
	else if id is 7:
		move screen "seg_7" for p to {-mx}, {-my}
	else if id is 8:
		move screen "seg_8" for p to {-mx}, {-my}
	else if id is 9:
		move screen "seg_9" for p to {-mx}, {-my}
	else if id is 10:
		move screen "seg_10" for p to {-mx}, {-my}
	else if id is 11:
		move screen "seg_11" for p to {-mx}, {-my}
	else if id is 12:
		move screen "seg_12" for p to {-mx}, {-my}
	else if id is 13:
		move screen "seg_13" for p to {-mx}, {-my}
	else if id is 14:
		move screen "seg_14" for p to {-mx}, {-my}
	else if id is 15:
		move screen "seg_15" for p to {-mx}, {-my}
	else if id is 16:
		move screen "seg_16" for p to {-mx}, {-my}
	else if id is 17:
		move screen "seg_17" for p to {-mx}, {-my}
	else if id is 18:
		move screen "seg_18" for p to {-mx}, {-my}
	else if id is 19:
		move screen "seg_19" for p to {-mx}, {-my}
	else if id is 20:
		move screen "seg_20" for p to {-mx}, {-my}
	else if id is 21:
		move screen "seg_21" for p to {-mx}, {-my}
	else if id is 22:
		move screen "seg_22" for p to {-mx}, {-my}
	else if id is 23:
		move screen "seg_23" for p to {-mx}, {-my}
	else if id is 24:
		move screen "seg_24" for p to {-mx}, {-my}

function SnakeStep(p: player):
	set {-snake_dir::%p%} to {-snake_next::%p%}
	set {-ox} to {-hx::%p%}
	set {-oy} to {-hy::%p%}
	set {-nx} to {-ox}
	set {-ny} to {-oy}

	if {-snake_dir::%p%} is "up":
		set {-ny} to {-oy} - 12
	else if {-snake_dir::%p%} is "down":
		set {-ny} to {-oy} + 12
	else if {-snake_dir::%p%} is "left":
		set {-nx} to {-ox} - 12
	else:
		set {-nx} to {-ox} + 12

	# Проверка границ поля 15x11 (-84..84, -60..60)
	if {-snake_walls::%p%} is "portal":
		if {-nx} > 84:
			set {-nx} to -84
		else if {-nx} < -84:
			set {-nx} to 84
		if {-ny} > 60:
			set {-ny} to -60
		else if {-ny} < -60:
			set {-ny} to 60
	else:
		if {-nx} > 84 or {-nx} < -84 or {-ny} > 60 or {-ny} < -60:
			SnakeGameOver(p, "стена")
			stop

	# Проверка столкновения с собственным хвостом
	loop {-snake_len::%p%} times:
		if {-nx} is {-sx::%p%::%loop-number%} and {-ny} is {-sy::%p%::%loop-number%}:
			SnakeGameOver(p, "укусил себя")
			stop

	# Таймер бонусного золотого яблока
	if {-snake_bonus_t::%p%} > 0:
		remove 1 from {-snake_bonus_t::%p%}
		SnakeUpdateBonusBar(p)
		if {-snake_bonus_t::%p%} is 0:
			hide screen "bonus" for p

	# Перемещаем голову
	set {-hx::%p%} to {-nx}
	set {-hy::%p%} to {-ny}
	move screen "head" for p to {-nx}, {-ny}

	# Проверяем поедание бонусного яблока (+30 очков и +5 монет!)
	if {-snake_bonus_t::%p%} > 0 and {-nx} is {-bx::%p%} and {-ny} is {-by::%p%}:
		set {-snake_bonus_t::%p%} to 0
		hide screen "bonus" for p
		SnakeUpdateBonusBar(p)
		add 30 to {-snake_score::%p%}
		add 5 to {coins::%p%}
		if {-snake_score::%p%} > {snake_best::%p%}:
			set {snake_best::%p%} to {-snake_score::%p%}
		play sound entity.player.levelup to p with volume 0.7 pitch 1.5

	# Проверяем поедание обычного яблока (+10 очков и рост хвоста на лету)
	if {-nx} is {-ax::%p%} and {-ny} is {-ay::%p%}:
		add 10 to {-snake_score::%p%}
		if {-snake_score::%p%} > {snake_best::%p%}:
			set {snake_best::%p%} to {-snake_score::%p%}
		if {-snake_len::%p%} < 24:
			add 1 to {-snake_len::%p%}
			SnakeAddSeg(p, {-snake_len::%p%}, {-ox}, {-oy})
		else:
			SnakeMoveSeg(p, {-snake_ring::%p%}, {-ox}, {-oy})
			add 1 to {-snake_ring::%p%}
			if {-snake_ring::%p%} > {-snake_len::%p%}:
				set {-snake_ring::%p%} to 1
		SnakeSpawnApple(p)
		SnakeUpdateHud(p)
		play sound entity.experience_orb.pickup to p with volume 0.7 pitch 1.3
		if {-snake_bonus_t::%p%} is 0:
			chance of 35%:
				SnakeSpawnBonus(p)
		stop

	# Обычный шаг без роста: переставляем только один сегмент кольцевого буфера на место старой головы!
	SnakeMoveSeg(p, {-snake_ring::%p%}, {-ox}, {-oy})
	add 1 to {-snake_ring::%p%}
	if {-snake_ring::%p%} > {-snake_len::%p%}:
		set {-snake_ring::%p%} to 1
	SnakeUpdateHud(p)

# Игровой цикл: тикает только у игроков, у которых сейчас открыт экран "Змейка"
every 2 ticks:
	loop all players:
		if loop-player has screen "Змейка" open and {-snake_state::%loop-player%} is "play":
			add 1 to {-snake_tick::%loop-player%}
			if {-snake_tick::%loop-player%} >= {-snake_speed::%loop-player%}:
				set {-snake_tick::%loop-player%} to 0
				SnakeStep(loop-player)` }
    ],
    notes: {
      ru: [
        "Кольцевой буфер хвоста (seg_1..seg_N): на обычном шаге двигаются только 2 элемента (head и один сегмент хвоста), поэтому даже длинная змейка не нагружает сеть.",
        "При поедании яблока новый сегмент добавляется на лету через update screen без пересоздания окна.",
        "Стрелки, Space и R привязаны к кнопкам через key <клавиша> с keep open, а WASD обрабатываются через on key ... in screen \"Змейка\": независимо от раскладки клавиатуры."
      ],
      en: [
        "Ring-buffered tail (seg_1..seg_N): on a normal step only 2 elements move (head and one tail segment), keeping network traffic minimal at any snake length.",
        "Eating an apple dynamically appends a new segment on the fly via update screen without recreating the GUI window.",
        "Arrow keys, Space and R are bound to buttons via key <hotkey> with keep open, while WASD are handled by on key ... in screen \"Змейка\": regardless of OS keyboard layout."
      ]
    },
    related: ["eff-open-screen", "screen-dynamic", "screen-button", "event-key", "event-screen-open", "struct-function"]
  }
];
