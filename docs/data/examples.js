/*
 * ScriptCraft — большие примеры для вкладки «Примеры».
 * Каждый пример: id, title {ru,en}, intro {ru,en}, blocks[] (lang: sc | json | toml | text), notes {ru[], en[]}, related[] (id из syntax.js).
 * Скрипты взяты из examples/scripts (welcome.sc, menu.sc, hud.sc, ruby.sc, bag.sc) — держите их в согласии.
 * pouch — только эта страница: мод его в папку scripts не копирует.
 * Отступ в скриптах — Tab.
 */
window.SC_DOCS = window.SC_DOCS || {};
window.SC_DOCS.examples = [
  {
    id: "welcome",
    title: { ru: "Вход, эффекты и команды с аргументами", en: "Join, effects and commands with arguments" },
    intro: { ru: "welcome.sc включается сам и собран так, чтобы с него можно было копировать: события, переменные, шанс, дом по координатам и современный вид команды (permission, aliases и аргументы под заголовком, а не в одной строке).", en: "welcome.sc is enabled by default and is meant to be copied from: events, variables, chance, a home stored as coordinates, and the current command shape (permission, aliases and arguments under the header, not on the same line)." },
    blocks: [
      { lang: "sc", title: { ru: "scriptcraft/scripts/welcome.sc", en: "scriptcraft/scripts/welcome.sc" }, code: `# ==========================================================
#  welcome.sc — вход, эффекты, переменные и команды с аргументами
#  Этот файл включается сам. Перезагрузка: /sc reload welcome
#  Выключить: /sc disable welcome  (файл станет -welcome.sc)
#  Отступ — Tab. Один Tab равен четырём пробелам, пробелы тоже понимаются.
# ==========================================================

on join:
	add 1 to {joins::%player%}
	if {joins::%player%} is 1:
		send "&aПривет, &e%player%&a! Ты здесь впервые." to player
		set {coins::%player%} to 20
		send "&7Стартовые &e20 монет&7. Магазин: &f/menu" to player
	else:
		send "&aС возвращением, &e%player%&a! Вход номер {joins::%player%}." to player
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
	send "&aДом сохранён: &f{home_x::%player%} {home_y::%player%} {home_z::%player%}" to player

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
	send "&eМонет: &6{coins::%player%}" to player

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
		send "&a+5 монет. Сейчас: &6{coins::%player%}" to player

command /pay <player> <integer>:
	aliases: transfer
	if {coins::%player%} < arg-2:
		send "&cНе хватает монет. Баланс: {coins::%player%}" to player
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
    intro: { ru: "menu.sc открывает лавку /menu (ещё /shop и /lavka). Кнопки вызывают /order <text> и тратят {coins::%player%} из welcome.sc. Ниже — полный список того, что можно поставить на экран.", en: "menu.sc opens a shop with /menu (also /shop and /lavka). Buttons run /order <text> and spend {coins::%player%} from welcome.sc. Below is the full list of what a screen can hold." },
    blocks: [
      { lang: "sc", title: { ru: "scriptcraft/scripts/menu.sc", en: "scriptcraft/scripts/menu.sc" }, code: `# ==========================================================
#  menu.sc — экран-лавка. Открыть: /menu  (псевдоним /shop)
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
		text "Монеты: {coins::%player%}" at 0, -54 color #FFFFFF shadow layer 2 id coins_label
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
			send "&cНужно 8 монет, у тебя {coins::%player%}" to player
		else:
			remove 8 from {coins::%player%}
			give player example:ruby 1
			send "&aКуплен рубин. Осталось: {coins::%player%}" to player
	else:
		if arg-1 is "diamond":
			if {coins::%player%} < 15:
				send "&cНужно 15 монет." to player
			else:
				remove 15 from {coins::%player%}
				give player diamond 1
				send "&bКуплен алмаз. Осталось: {coins::%player%}" to player
		else:
			if arg-1 is "bread":
				if {coins::%player%} < 3:
					send "&cНужно 3 монеты." to player
				else:
					remove 3 from {coins::%player%}
					give player bread 8
					send "&6Хлеб выдан. Осталось: {coins::%player%}" to player
			else:
				if arg-1 is "block":
					if {coins::%player%} < 20:
						send "&cНужно 20 монет." to player
					else:
						remove 20 from {coins::%player%}
						give player example:ruby_block 1
						send "&cБлок выдан. Осталось: {coins::%player%}" to player
				else:
					if arg-1 is "heal":
						if {coins::%player%} < 10:
							send "&cЛечение стоит 10 монет." to player
						else:
							remove 10 from {coins::%player%}
							heal player
							feed player
							send "&aТы в порядке. Осталось: {coins::%player%}" to player
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
    intro: { ru: "hud.sc при первой установке копируется выключенным (-hud.sc), потому что прячет ванильный хотбар, здоровье и голод. Включить: /sc enable hud. Элементы те же, что у экрана, кроме кнопок: HUD не принимает клики.", en: "On a first install hud.sc is copied disabled (-hud.sc), because it hides the vanilla hotbar, health and food. Enable it with /sc enable hud. The elements are the same as a screen, except buttons: a HUD does not take clicks." },
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
		text "{coins::%player%}" at 26, 8 anchor top_left color #FFD700 shadow id coins_text layer 1
		bar "coins_goal" value {coins::%player%} max 100 at 4, 24 size 70x4 anchor top_left color #FFD700 layer 0
		text "%script%" at -4, 4 anchor top_right color #666666 layer 1

command /coinhud:
	aliases: coinsui
	set text of hud "coins_text" for player to "{coins::%player%}"
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
    intro: { ru: "ruby.sc объявляет example:ruby_block и example:ruby. Модели и текстуры лежат в scriptcraft/resources. Блок, который есть в скриптах при запуске сервера, получает настоящий id; новый define после /sc reload появляется сразу во временном слоте scriptcraft:dyn_block_N. Технический id смотрите в /sc ids.", en: "ruby.sc declares example:ruby_block and example:ruby. Models and textures live in scriptcraft/resources. A block that is in the scripts when the server starts gets its real id; a new define after /sc reload appears at once in a temporary slot scriptcraft:dyn_block_N. The technical id is in /sc ids." },
    blocks: [
      { lang: "sc", title: { ru: "scriptcraft/scripts/ruby.sc", en: "scriptcraft/scripts/ruby.sc" }, code: `# ==========================================================
#  ruby.sc — свои блоки и предметы
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
    notes: { ru: ["define block: name, hardness (1.5), resistance (6), sound stone|wood|metal|glass|grass|sand|wool|gravel, light 0–15, requires_tool true|false.","facing: none | horizontal (4 стороны, как печь) | all (ещё вверх и вниз, как раздатчик). Модель смотрит на север, поворот дописывает мод.","render: solid | cutout (дырки в png, синоним transparent) | translucent (стекло). full: false — модель не на весь куб, пол под блоком остаётся виден.","define item: name, stack_size 1–64, rarity common|uncommon|rare|epic, fire_resistant true|false.","id и имена файлов — только a-z 0-9 _ . -. Русское название — name: или lang.","В модели путь текстуры — namespace:block/имя, без textures/ и без .png. После правки assets: /sc resources reload, после data: /reload."], en: ["define block: name, hardness (1.5), resistance (6), sound stone|wood|metal|glass|grass|sand|wool|gravel, light 0–15, requires_tool true|false.","facing: none | horizontal (4 sides, like a furnace) | all (also up and down, like a dispenser). The model faces north; the mod writes the rotation.","render: solid | cutout (holes in the png, alias transparent) | translucent (glass). full: false — the model is not a full cube, so the floor under the block stays visible.","define item: name, stack_size 1–64, rarity common|uncommon|rare|epic, fire_resistant true|false.","ids and file names: only a-z 0-9 _ . -. A translated name goes in name: or lang.","A model texture path is namespace:block/name, with no textures/ and no .png. After editing assets: /sc resources reload; after data: /reload."] },
    related: ["struct-namespace", "struct-define-block", "struct-define-item", "cmd-resources-reload", "eff-give-item", "event-blocks"]
  },
  {
    id: "economy",
    title: { ru: "Экономика: переменные, шанс и перевод", en: "Economy: variables, chance and a transfer" },
    intro: { ru: "Тот же баланс, что в welcome.sc. {coins::%player%} сохраняется, {-worked::%player%} живёт до перезапуска. /pay <player> <integer> переводит монеты другому игроку.", en: "The same balance as in welcome.sc. {coins::%player%} is saved, {-worked::%player%} lives until the server restarts. /pay <player> <integer> transfers coins to another player." },
    blocks: [
      { lang: "sc", title: { ru: "фрагмент welcome.sc", en: "excerpt of welcome.sc" }, code: `# Эти команды уже есть в welcome.sc. Здесь — только баланс.
command /coins:
	aliases: bal, balance
	if {coins::%player%} is not set:
		set {coins::%player%} to 0
	send "&eМонет: &6{coins::%player%}" to player

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
		send "&a+5 монет. Сейчас: &6{coins::%player%}" to player

command /pay <player> <integer>:
	aliases: transfer
	if {coins::%player%} < arg-2:
		send "&cНе хватает монет. Баланс: {coins::%player%}" to player
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
    intro: { ru: "Цель — набор игроков. Пустой набор ничего не делает. loop нужен, когда каждому игроку надо написать его собственное имя. Эти команды есть в welcome.sc.", en: "A target is a set of players. An empty set does nothing. Use loop when each player needs their own name in a message. These commands are in welcome.sc." },
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
    intro: { ru: "Сообщение в чате с токеном ? выполняется как одна строка скрипта от имени написавшего. По умолчанию выключено.", en: "A chat message starting with ? runs as one script line on behalf of the sender. Off by default." },
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
    intro: { ru: "Короткий учебный файл, его нет среди включаемых примеров. Положите в scriptcraft/scripts/features.sc, если нужен. every без игрока в player: внутри используйте loop.", en: "A short teaching file; it is not one of the scripts the mod copies in. Put it in scriptcraft/scripts/features.sc if you want it. every has no player: use loop inside." },
    blocks: [
      { lang: "sc", title: { ru: "scriptcraft/scripts/features.sc", en: "scriptcraft/scripts/features.sc" }, code: `on first join:
	give player bread 16
	send "&aДобро пожаловать, %player%!" to player

on chat:
	if %message% is "hp":
		cancel event
		set {hp} to health of player
		set {max} to max health of player
		send "&cЗдоровье: {hp} / {max}" to player

command /roll:
	set {roll} to random integer between 1 and 6
	if {roll} is 6:
		send "&aШестёрка!" to player
		give 2 levels to player
	else if {roll} >= 4:
		send "&eВыпало {roll}" to player
	else:
		send "&7Выпало {roll}" to player
	wait 1 second
	send "&7Бросок учтён" to player

every 10 seconds:
	loop all players:
		if health of loop-player < 6 and loop-player is not in creative:
			heal loop-player by 2` }
    ],
    notes: { ru: ["wait откладывает только строки ниже на том же отступе и не дольше 5 минут.","cancel event в чате не даёт сообщению появиться у других.","random integer between 1 and 6 — целое, обе границы входят."], en: ["wait delays only the lines below it at the same indent, and not for longer than 5 minutes.","cancel event in chat keeps the message from appearing for others.","random integer between 1 and 6 is a whole number, both ends included."] },
    related: ["event-world", "event-chat", "expr-property", "expr-random", "struct-every", "struct-wait", "eff-cancel", "cond-and"]
  },
  {
    id: "bag",
    title: { ru: "Сумка вместо инвентаря", en: "A bag instead of the inventory" },
    intro: { ru: "bag.sc копируется выключенным (-bag.sc): он отменяет ванильный инвентарь на E. Включить: /sc enable bag. Это не сундук и не хотбар — список и картинка запускают команду, а команда делает give.", en: "bag.sc is copied disabled (-bag.sc): it cancels the vanilla inventory on E. Enable it with /sc enable bag. It is not a chest and not the hotbar — a list row and an image run a command, and the command gives the item." },
    blocks: [
      { lang: "sc", title: { ru: "scriptcraft/scripts/-bag.sc", en: "scriptcraft/scripts/-bag.sc" }, code: `# bag.sc копируется выключенным. Включить: /sc enable bag

on receive:
	send "&7Получено: &f%item% &7x%amount%" to player

on key g:
	send "&eНажата G" to player

on open inventory:
	cancel event
	open screen "Сумка" to player:
		list vertical at -8, 0 size 140x160 anchor right header "&6Сумка" select:
			row "&bАлмаз" run "/hand diamond"
			row "&cРубин" run "/hand ruby"
			row "&7Закрыть" close
		image "minecraft:textures/item/diamond.png" at -170, -24 size 32x32 texture 16x16 anchor right run "/hand diamond"
		text "Взять алмаз" at -170, 16 anchor right color #FFFFFF run "/hand diamond"
		button "Закрыть" at 0, 80 size 80x20 close

command /hand <text>:
	permission: 0
	if arg-1 is "diamond":
		give player diamond 1
	else if arg-1 is "ruby":
		give player example:ruby 1
	else:
		send "&cНет такого предмета: &f%arg-1%" to player` }
    ],
    notes: { ru: ["on receive видит рост суммы предмета: give, сундук, крафт, подбор, креатив. Перекладывание слотов не считается. cancel event забирает добавку.","on key только наблюдает. E отменяется через on open inventory, и cancel event внутри if всё равно закрывает меню всегда.","Клик по строке, тексту и картинке есть только у open screen. Хотбар остаётся ванильным."], en: ["on receive sees the inventory total grow: give, a chest, crafting, a pickup, creative. Moving stacks does not count. cancel event removes the added amount.","on key only observes. E is stopped with on open inventory, and a cancel event inside if still always blocks the menu.","A click on a row, a text or an image exists only on open screen. The hotbar stays vanilla."] },
    related: ["event-receive", "event-key", "event-open", "screen-list", "screen-click", "eff-open-screen", "eff-give-item"]
  },
  {
    id: "pouch",
    title: { ru: "Сумка: свои предметы вместо инвентаря", en: "A pouch instead of the inventory" },
    intro: { ru: "Учебный инвентарь только для этой страницы: мод не кладёт его в scripts. E закрывает ванильное меню. Алмаз, железо, золото и хлеб при получении уходят в переменные и снимаются с ванильного инвентаря. ЛКМ по слоту или строке кладёт одну штуку в руку. Это не сундук: строки заданы в скрипте, хотбар остаётся ванильным.", en: "A teaching inventory that lives only on this page: the mod does not copy it into scripts. E closes the vanilla menu. Diamonds, iron, gold and bread are stored in variables and removed from the vanilla inventory when received. Left click on a slot or a row puts one into the hand. It is not a chest: the rows are written in the script, and the hotbar stays vanilla." },
    blocks: [
      { lang: "sc", title: { ru: "scriptcraft/scripts/pouch.sc", en: "scriptcraft/scripts/pouch.sc" }, code: `# Сумка вместо инвентаря. Скопируйте в scriptcraft/scripts/pouch.sc и выполните /sc reload.
# E больше не откроет ванильный инвентарь. Хотбар остаётся.
# Слот и строка только запускают команду. Новые виды предметов сами в список не добавляются.

on join:
	if {pouch::%player%::diamond} is not set:
		set {pouch::%player%::diamond} to 0
	if {pouch::%player%::iron} is not set:
		set {pouch::%player%::iron} to 0
	if {pouch::%player%::gold} is not set:
		set {pouch::%player%::gold} to 0
	if {pouch::%player%::bread} is not set:
		set {pouch::%player%::bread} to 0

# give из /pouch take тоже попадает в on receive. Флаг говорит: эту штуку оставить в руке.
on receive:
	if {-handing::%player%} is %item%:
		delete {-handing::%player%}
		stop
	if %item% is "minecraft:diamond":
		cancel event
		add %amount% to {pouch::%player%::diamond}
		stop
	if %item% is "minecraft:iron_ingot":
		cancel event
		add %amount% to {pouch::%player%::iron}
		stop
	if %item% is "minecraft:gold_ingot":
		cancel event
		add %amount% to {pouch::%player%::gold}
		stop
	if %item% is "minecraft:bread":
		cancel event
		add %amount% to {pouch::%player%::bread}

on open inventory:
	cancel event
	open screen "Сумка" to player:
		text "&6Сумка" at 0, -78
		text "&7Клик — взять одну штуку в руку" at 0, -62 color #AAAAAA
		slot diamond at -72, -24 run "/pouch take diamond"
		text "&b{pouch::%player%::diamond}" at -72, 2
		slot iron_ingot at -24, -24 run "/pouch take iron"
		text "&f{pouch::%player%::iron}" at -24, 2
		slot gold_ingot at 24, -24 run "/pouch take gold"
		text "&e{pouch::%player%::gold}" at 24, 2
		slot bread at 72, -24 run "/pouch take bread"
		text "&6{pouch::%player%::bread}" at 72, 2
		list vertical at -8, 8 size 160x100 anchor right header "&6Взять" select:
			row "&bАлмаз &8x{pouch::%player%::diamond}" run "/pouch take diamond"
			row "&fЖелезо &8x{pouch::%player%::iron}" run "/pouch take iron"
			row "&eЗолото &8x{pouch::%player%::gold}" run "/pouch take gold"
			row "&6Хлеб &8x{pouch::%player%::bread}" run "/pouch take bread"
		button "Закрыть" at 0, 78 size 80x20 close

command /pouch take <text>:
	permission: 0
	if arg-1 is "diamond":
		if {pouch::%player%::diamond} >= 1:
			set {-handing::%player%} to "minecraft:diamond"
			remove 1 from {pouch::%player%::diamond}
			give player diamond 1
		else:
			send "&cАлмазов нет" to player
		stop
	if arg-1 is "iron":
		if {pouch::%player%::iron} >= 1:
			set {-handing::%player%} to "minecraft:iron_ingot"
			remove 1 from {pouch::%player%::iron}
			give player iron_ingot 1
		else:
			send "&cЖелеза нет" to player
		stop
	if arg-1 is "gold":
		if {pouch::%player%::gold} >= 1:
			set {-handing::%player%} to "minecraft:gold_ingot"
			remove 1 from {pouch::%player%::gold}
			give player gold_ingot 1
		else:
			send "&cЗолота нет" to player
		stop
	if arg-1 is "bread":
		if {pouch::%player%::bread} >= 1:
			set {-handing::%player%} to "minecraft:bread"
			remove 1 from {pouch::%player%::bread}
			give player bread 1
		else:
			send "&cХлеба нет" to player
		stop
	send "&cНет такого кармана" to player` }
    ],
    notes: {
      ru: [
        "Файл в игру сам не копируется. Вставьте текст в scriptcraft/scripts/pouch.sc.",
        "cancel event в on open inventory закрывает E всегда, даже если поставить его внутрь if. Выживание и креатив — оба.",
        "В сумку попадают только четыре id. Остальное остаётся в ванильном инвентаре, который этот экран прячет.",
        "{-handing::%player%} нужен, чтобы give из команды не забрала строка on receive. Флаг живёт до перезапуска сервера.",
        "Числа на экране — снимок на момент открытия. Хотбар ванильный."
      ],
      en: [
        "The mod does not copy this file. Paste it into scriptcraft/scripts/pouch.sc.",
        "cancel event in on open inventory always blocks E, even inside if. Survival and creative both use this menu.",
        "Only these four ids go into the pouch. Everything else stays in the vanilla inventory, which this screen hides.",
        "{-handing::%player%} stops on receive from taking back the item the command just gave. The flag lasts until the server restarts.",
        "The numbers are a snapshot from when the screen opened. The hotbar stays vanilla."
      ]
    },
    related: ["event-receive", "event-open", "screen-list", "screen-click", "screen-slot", "eff-give-item", "eff-add-var", "eff-stop", "expr-temp-variable"]
  }
];
