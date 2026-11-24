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
      { lang: "sc", title: { ru: "scriptcraft/scripts/-welcome.sc", en: "scriptcraft/scripts/-welcome.sc" }, code: `# ==========================================================
#  welcome.sc — вход, эффекты, переменные и команды с аргументами
#  Копируется выключенным (-welcome.sc). Включить: /sc enable welcome
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

# Хвост строки без списка аргументов попадает в %args%.
command /bc:
	permission: 2
	aliases: broadcast
	if "%args%" is "":
		send "&cИспользование: /bc <текст объявления>" to player
		stop
	broadcast "&6[Объявление] &f%args%"

# permission, aliases и executable by пишутся ПОД командой, не в заголовке.
command /kit:
	permission: 2
	aliases: starter
	give player diamond 3
	give player bread 16
	heal player
	feed player
	send "&6Стартовый набор выдан." to player

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

# Переключатель полёта для себя или указанного игрока.
command /fly [player]:
	permission: 2
	if arg-1 is not set:
		if player is flying:
			set flying of player to false
			send action bar "&7Полёт выключен" to player
		else:
			set flying of player to true
			send action bar "&bПолёт включён" to player
	else:
		if arg-1 is flying:
			set flying of arg-1 to false
			send action bar "&7Полёт выключен" to arg-1
			reply "&7Полёт выключен для &f%arg-1%"
		else:
			set flying of arg-1 to true
			send action bar "&bПолёт включён" to arg-1
			reply "&bПолёт включён для &f%arg-1%"

# Смена режима игры: /gm <0|1|2|3> [игрок] (или s/c/a/sp).
function SetPlayerGm(target: player, mode: text):
	if mode is "0" or "s" or "survival":
		set gamemode of target to survival
		send action bar "&aРежим выживания" to target
	else if mode is "1" or "c" or "creative":
		set gamemode of target to creative
		send action bar "&bТворческий режим" to target
	else if mode is "2" or "a" or "adventure":
		set gamemode of target to adventure
		send action bar "&eРежим приключения" to target
	else if mode is "3" or "sp" or "spectator":
		set gamemode of target to spectator
		send action bar "&7Режим наблюдателя" to target

command /gm <text> [player]:
	permission: 2
	aliases: gamemode
	if arg-1 is not "0" and arg-1 is not "1" and arg-1 is not "2" and arg-1 is not "3" and arg-1 is not "s" and arg-1 is not "c" and arg-1 is not "a" and arg-1 is not "sp" and arg-1 is not "survival" and arg-1 is not "creative" and arg-1 is not "adventure" and arg-1 is not "spectator":
		send "&cИспользование: /gm <0|1|2|3> [игрок]" to player
		stop
	if arg-2 is not set:
		SetPlayerGm(player, arg-1)
	else:
		SetPlayerGm(arg-2, arg-1)
		reply "&aРежим игры для &f%arg-2% &aизменён."

# Оба аргумента необязательны: /heal, /heal Steve, /heal Steve 10.
command /heal [player] [integer]:
	permission: 2
	aliases: h
	executable by: players and console
	if arg-1 is not set:
		heal player
		feed player
		send action bar "&aЗдоровье восстановлено" to player
	else if arg-2 is not set:
		heal arg-1
		send action bar "&aТебя вылечил %player%" to arg-1
		reply "&aВылечен %arg-1%"
	else:
		heal arg-1 by arg-2
		send action bar "&aТебя лечит %player% (+%arg-2%)" to arg-1
		reply "&aВылечен %arg-1% на %arg-2%"

# Точка спавна и личный дом хранятся как полноценная локация (мир + координаты + поворот).
command /setspawn:
	permission: 2
	set {spawn} to location of player
	send "&aТочка спавна сохранена." to player

command /spawn:
	aliases: hub
	if {spawn} is not set:
		send "&cТочка спавна ещё не задана (/setspawn)." to player
	else:
		teleport player to {spawn}
		send action bar "&aТелепортация на спавн" to player

command /sethome:
	aliases: sh
	set {home::%player%} to location of player
	send "&aДом сохранён!" to player

command /home:
	aliases: base
	if {home::%player%} is not set:
		send "&cСначала установите дом: /sethome" to player
	else:
		teleport player to {home::%player%}
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
	else if arg-2 < 1:
		send "&cСумма должна быть больше нуля." to player
	else:
		remove arg-2 from {coins::%player%}
		add arg-2 to {coins::%arg-1%}
		send "&aПереведено &6%arg-2% &aмонет игроку &f%arg-1%" to player
		send "&a%player% перевёл тебе &6%arg-2% &aмонет" to arg-1` }
    ],
    notes: { ru: ["Отступ — Tab. Один Tab равен четырём пробелам, пробелы по-прежнему понимаются.","Под командой: permission: 0–4, aliases: короткие имена через запятую, executable by: players / console / players and console.","Аргументы: <тип> обязательный, [тип] можно не писать. Типы: text, player, offline player, integer, number, boolean, date, world, item.","arg-1 — первый аргумент, %arg-1% — он же в тексте. Нет необязательного аргумента — arg-2 is not set.","Без списка аргументов хвост строки остаётся в %args%. %script% — имя файла без .sc.","{-имя} живёт до перезапуска сервера, {имя} пишется в scriptcraft/variables/variables.json."], en: ["Indent with Tab. One Tab equals four spaces; spaces still work.","Under the command: permission: 0–4, aliases: short names separated by commas, executable by: players / console / players and console.","Arguments: <type> is required, [type] may be omitted. Types: text, player, offline player, integer, number, boolean, date, world, item.","arg-1 is the first argument, %arg-1% is the same value in text. A missing optional argument is arg-2 is not set.","Without an argument list the rest of the line stays in %args%. %script% is the file name without .sc.","{-name} lives until the server restarts, {name} is saved in scriptcraft/variables/variables.json."] },
    related: ["struct-command", "struct-comment", "event-join", "expr-argument", "expr-script", "expr-variable", "expr-temp-variable", "struct-chance", "eff-teleport"]
  },
  {
    id: "menu",
    title: { ru: "Лавка: экран, слои, слоты и кнопки", en: "Shop: a screen, layers, slots and buttons" },
    intro: { ru: "Лавка /menu. Кнопки тратят монеты из welcome.sc.", en: "A shop on /menu. Buttons spend coins from welcome.sc." },
    blocks: [
      { lang: "sc", title: { ru: "scriptcraft/scripts/-menu.sc", en: "scriptcraft/scripts/-menu.sc" }, code: `# ==========================================================
#  menu.sc — экран-лавка. Копируется выключенным (-menu.sc): /sc enable menu
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
	else if arg-1 is "diamond":
		if {coins::%player%} < 15:
			send "&cНужно 15 монет." to player
		else:
			remove 15 from {coins::%player%}
			give player diamond 1
			send "&bКуплен алмаз. Осталось: %{coins::%player%}%" to player
	else if arg-1 is "bread":
		if {coins::%player%} < 3:
			send "&cНужно 3 монеты." to player
		else:
			remove 3 from {coins::%player%}
			give player bread 8
			send "&6Хлеб выдан. Осталось: %{coins::%player%}%" to player
	else if arg-1 is "block":
		if {coins::%player%} < 20:
			send "&cНужно 20 монет." to player
		else:
			remove 20 from {coins::%player%}
			give player example:ruby_block 1
			send "&cБлок выдан. Осталось: %{coins::%player%}%" to player
	else if arg-1 is "heal":
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
	set {-msg} to "%args%"
	if {-msg} is "":
		set {-msg} to "%player% зовёт всех рядом!"
	open screen "Объявление" to players in radius 16 of player:
		box at 0, 0 size 220x74 color #CC0F172A border #FFD700 border_width 1 radius 6 shadow layer 0
		text "&6&lОбъявление от %player%" at 0, -22 color #FFD700 shadow layer 2
		text "&f%{-msg}%" at 0, -4 color #FFFFFF shadow layer 2
		button "Понятно" at 0, 18 size 110x18 close layer 3` }
    ],
    notes: { ru: ["Элементы: text, button, slot, image, bar, hotbar. У всех есть at X Y, anchor, layer N (больше = выше) и id имя.","text: color #RRGGBB и shadow. button: size ШxВ, run \"/команда\" или close. Кнопка с run закрывает экран и выполняет команду от игрока.","slot: предмет и количество, либо slot hotbar N, slot image \"картинка\", slot text \"текст\". no frame убирает рамку.","image: \"ns:textures/....png\", size ШxВ, texture ШxВ (размер файла), color — оттенок.","bar: value число|{переменная}|health|food|armor|air|xp|level, max, direction left_to_right|right_to_left|bottom_to_top|top_to_bottom, fill, background, text.","anchor: top_left, top, top_right, left, center, right, bottom_left, bottom, bottom_right. Минус в at — влево или вверх.","Картинки из scriptcraft/resources/assets/<ns>/textures/gui/ сервер шлёт сам, перезагрузка ресурсов не нужна."], en: ["Elements: text, button, slot, image, bar, hotbar. All of them take at X Y, anchor, layer N (higher draws above) and id name.","text: color #RRGGBB and shadow. button: size WxH, run \"/command\" or close. A button with run closes the screen and runs the command as the player.","slot: an item and a count, or slot hotbar N, slot image \"picture\", slot text \"text\". no frame removes the frame.","image: \"ns:textures/....png\", size WxH, texture WxH (the file size), color tints it.","bar: value number|{variable}|health|food|armor|air|xp|level, max, direction left_to_right|right_to_left|bottom_to_top|top_to_bottom, fill, background, text.","anchor: top_left, top, top_right, left, center, right, bottom_left, bottom, bottom_right. A negative at moves left or up.","Pictures in scriptcraft/resources/assets/<ns>/textures/gui/ are sent by the server; no resource reload is needed."] },
    related: ["eff-open-screen", "screen-text", "screen-button", "screen-slot", "screen-image", "hud-bar", "hud-layer", "struct-command"]
  },
  {
    id: "hud",
    title: { ru: "HUD: полосы, хотбар и ванильные части", en: "HUD: bars, a hotbar and vanilla parts" },
    intro: { ru: "Интерфейс поверх игры. Копируется выключенным: /sc enable hud.", en: "An overlay on the game. Copied disabled: /sc enable hud." },
    blocks: [
      { lang: "sc", title: { ru: "scriptcraft/scripts/-hud.sc", en: "scriptcraft/scripts/-hud.sc" }, code: `# ==========================================================
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
#  {переменная} в тексте, value или max сама обновляет HUD в момент записи.
# ==========================================================

on join or load:
	set {-hud_on::%player%} to true
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

function HudSetState(p: player, enabled: boolean):
	set {-hud_on::%p%} to enabled
	if enabled is true:
		show hud "stats" to p
		show hud "coins" to p
		hide vanilla hotbar for p
		hide vanilla health for p
		hide vanilla food for p
		hide vanilla armor for p
		hide vanilla experience for p
		send action bar "&aКастомный HUD включён" to p
	else:
		hide hud "stats" from p
		hide hud "coins" from p
		show vanilla hotbar for p
		show vanilla health for p
		show vanilla food for p
		show vanilla armor for p
		show vanilla experience for p
		send action bar "&7Кастомный HUD скрыт" to p

# Единый переключатель кастомного HUD: /hud [on|off]
command /hud [text]:
	if arg-1 is "off":
		HudSetState(player, false)
	else if arg-1 is "on":
		HudSetState(player, true)
	else if {-hud_on::%player%} is true:
		HudSetState(player, false)
	else:
		HudSetState(player, true)

command /boss <integer>:
	permission: 2
	aliases: bossbar
	create hud "boss" for players in radius 30 of player:
		text "Страж" at 0, 6 anchor top color #FF5555 shadow layer 1
		bar "boss_hp" value 100 max 100 at 0, 18 size 182x6 anchor top color #AA0000 layer 0
	set progress of bar "boss_hp" for players in radius 30 of player to arg-1` }
    ],
    notes: { ru: ["create hud \"группа\" for <цель>: — элементы с отступом. id имя нужно, чтобы потом менять элемент. layer < 0 рисуется под ванильным HUD.","Источники полосы: health, max_health, food, saturation, armor, air, max_air, xp, level, а также число, {переменная} и %плейсхолдер%.","direction: left_to_right, right_to_left, bottom_to_top, top_to_bottom. fill \"картинка\" или цвет, background цвет|\"картинка\"|none, text \"%value% %percent%%\".","Прячется ваниль: hotbar, health, food, armor, air, experience, mount_health, jump_bar, crosshair, effects, item_name, scoreboard, bossbar, hud, all.","Дальше по игре: set value|progress of bar \"имя\", set max of bar, set text of hud \"имя\", show hud, hide hud, remove hud, clear hud.","{переменная} в тексте, value или max обновляет HUD сама в момент записи. Пакет уходит только если строка изменилась. Источники health/food считает клиент, без пакетов.","/sc disable hud убирает группы этого скрипта и возвращает ванильные части, которые прятал только он."], en: ["create hud \"group\" for <target>: — elements indented below. id name is what you change later. layer < 0 draws under the vanilla HUD.","Bar sources: health, max_health, food, saturation, armor, air, max_air, xp, level, plus a number, a {variable} and a %placeholder%.","direction: left_to_right, right_to_left, bottom_to_top, top_to_bottom. fill is a picture or a color, background is a color, a picture or none, text can be \"%value% %percent%%\".","Vanilla parts you can hide: hotbar, health, food, armor, air, experience, mount_health, jump_bar, crosshair, effects, item_name, scoreboard, bossbar, hud, all.","Later in the game: set value|progress of bar \"name\", set max of bar, set text of hud \"name\", show hud, hide hud, remove hud, clear hud.","A {variable} in text, value or max updates the HUD when it is written. A packet is sent only if the text changed. health/food are read by the client, with no packets.","/sc disable hud removes this script's groups and restores vanilla parts that only it had hidden."] },
    related: ["hud-create", "hud-bar", "hud-bar-sources", "hud-hotbar", "hud-slot", "hud-layer", "hud-set-bar", "hud-set-text", "hud-show-hide", "hud-vanilla"]
  },
  {
    id: "custom-block",
    title: { ru: "Свой блок и предмет: все свойства", en: "A custom block and item: every property" },
    intro: { ru: "Блок и предмет example:ruby. Ресурсы лежат в scriptcraft/resources.", en: "The block and item example:ruby. Resources live in scriptcraft/resources." },
    blocks: [
      { lang: "sc", title: { ru: "scriptcraft/scripts/-ruby.sc", en: "scriptcraft/scripts/-ruby.sc" }, code: `# ==========================================================
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
      { lang: "sc", title: { ru: "фрагмент -welcome.sc", en: "excerpt of -welcome.sc" }, code: `# Эти команды уже есть в welcome.sc. Здесь — только баланс и перевод.
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
	else if arg-2 < 1:
		send "&cСумма должна быть больше нуля." to player
	else:
		remove arg-2 from {coins::%player%}
		add arg-2 to {coins::%arg-1%}
		send "&aПереведено &6%arg-2% &aмонет игроку &f%arg-1%" to player
		send "&a%player% перевёл тебе &6%arg-2% &aмонет" to arg-1` }
    ],
    notes: { ru: ["add и remove считают незаданную переменную нулём. Текст в числовой переменной — ошибка выполнения.","else if, and и or есть: and связывается теснее. Вложенный if тоже годится.","chance of 25%: — случайная ветка. В имени переменной %arg-1% подставляется, поэтому {coins::%arg-1%} — баланс получателя."], en: ["add and remove treat an unset variable as 0. Text in a numeric variable is a runtime error.","else if, and and or exist: and binds tighter. A nested if is fine too.","chance of 25%: is a random branch. %arg-1% is expanded inside a variable name, so {coins::%arg-1%} is the recipient's balance."] },
    related: ["expr-variable", "expr-temp-variable", "eff-set-var", "eff-add-var", "struct-if", "struct-chance", "expr-argument", "cond-number"]
  },
  {
    id: "heal-radius",
    title: { ru: "Цели, взгляд и цикл", en: "Targets, looking and a loop" },
    intro: { ru: "Цель — набор игроков. Пустой набор ничего не делает.", en: "A target is a set of players. An empty set does nothing." },
    blocks: [
      { lang: "sc", title: { ru: "фрагмент -welcome.sc", en: "excerpt of -welcome.sc" }, code: `# Эти команды уже есть в welcome.sc.
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
      { lang: "sc", title: { ru: "scriptcraft/scripts/-features.sc", en: "scriptcraft/scripts/-features.sc" }, code: `# ==========================================================
#  features.sc — первый вход, перехват чата, кубик с задержкой и таймер
#  Копируется выключенным (-features.sc). Включить: /sc enable features
# ==========================================================

on first join:
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
      { lang: "sc", title: { ru: "scriptcraft/scripts/-snake.sc", en: "scriptcraft/scripts/-snake.sc" }, code: `# ==========================================================
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
      { lang: "sc", title: { ru: "scriptcraft/scripts/-dino.sc", en: "scriptcraft/scripts/-dino.sc" }, code: `# ==========================================================
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
    title: { ru: "Tower Defense Pro (3 карты, 10/20/30 волн, 6 башен)", en: "Tower Defense Pro (3 maps, 10/20/30 waves, 6 towers)" },
    intro: {
      ru: "Большая стратегия Tower Defense Pro (3 maps, 10/20/30 waves, 6 towers) с меню выбора из 3 карт (Изумрудная долина, Лавовый каньон, Ледяная цитадель) и 3 уровней сложности (Лёгкий — 10 волн, Средний — 20 волн, Тяжёлый — 30 волн). Свободная постройка башен на любой клетке карты, 6 видов башен (Лучник, Пушка, Лёд, Маг, Тесла с цепной молнией, Снайпер с критами), 6 видов мобов + 2 вида боссов, настоящие летающие прожектайлы, умный клик (апгрейд до 4★), тумблер Авто-волны, скорость x2 и эффекты камеры (shake/fade).",
      en: "Full-featured Tower Defense Pro (3 maps, 10/20/30 waves, 6 towers) with a 3-map & 3-difficulty selector (Emerald Valley, Lava Canyon, Ice Citadel; Easy 10w / Medium 20w / Hard 30w). Free tower placement on any non-road tile, 6 towers (Archer, Splash Cannon, Frost, Mage, Chain-Lightning Tesla, Crit Sniper), 6 enemy types + 2 bosses, real homing flying projectiles, smart click auto-upgrade up to 4★, Auto-Wave toggle, 2x speed, and camera shake/fade effects."
    },
    downloads: [
      {
        href: "assets/td-resources.zip",
        label: { ru: "Скачать текстуры (td-resources.zip)", en: "Download textures (td-resources.zip)" },
        desc: { ru: "14 PNG-спрайтов (assets/td/textures/gui/) — распакуйте в scriptcraft/resources/", en: "14 PNG sprites (assets/td/textures/gui/) — extract into scriptcraft/resources/" }
      }
    ],
    blocks: [
      { lang: "sc", title: { ru: "scriptcraft/scripts/-td.sc", en: "scriptcraft/scripts/-td.sc" }, code: `# ==========================================================
#  td.sc — Tower Defense Ultra HD v4 (ScriptCraft 0.8.3)
#          5 уникальных HD-карт (включая Болото и Разлом с запретными зонами!)
#          9 башен 64x64 HD (включая Боевой Маяк-Баффер #9!) | Лимит 32 башни
#          16 видов противников 64x64 HD (Ассасин с уворотом, Храмовник со щитом, Вестник Бездны!)
#          Живой list-журнал событий справа | Иконка башни у курсора (вкл/выкл)
#          Авто-показ круга радиуса при наведении | 60 FPS Tween + GUI-частицы
# ==========================================================

on load:
	clear {-td-active::*}
	clear {-td-host::*}
	clear {-td-team::*}
	clear {-td-online::*}
	clear {-td-inv::*}
	clear {-td-reconn::*}

on quit:
	TdOnLeave(player)

on screen close "Tower Defense":
	TdOnLeave(player)

command /td [text] [player]:
	if arg-1 is not set:
		TdOpenMenu(player)
		stop
	if arg-1 is "coop" or "invite":
		if arg-2 is not set:
			send "&eИспользование: &f/td coop <ник_игрока> &7(до 4 игроков!)" to player
			stop
		if arg-2 is player:
			send "&cНельзя пригласить самого себя!" to player
			stop
		if {-td-team::%player%::*} is empty:
			add %player% to {-td-team::%player%::*}
		set {-cnt} to size of {-td-team::%player%::*}
		if {-cnt} >= 4:
			send "&cВ команде уже максимум (4 игрока)!" to player
			stop
		set {-td-inv::%arg-2%} to %player%
		send "&aПриглашение отправлено игроку &f%arg-2%&a! Когда все войдут — жмите &e▶ ИГРАТЬ&a." to player
		send "&6[Tower Defense] &e%player% &fприглашает вас в кооп (до 4 игроков)! Напишите &a/td join" to arg-2
		play sound entity.experience_orb.pickup to arg-2 with volume 0.7 pitch 1.3
		TdOpenMenu(player)
		stop
	if arg-1 is "join" or "accept" or "reconnect" or "rejoin":
		if {-td-reconn::%player%} is set:
			set {-rh} to {-td-reconn::%player%}
			if {-rh} is online and {-td-active::%{-rh}%} is 1:
				delete {-td-reconn::%player%}
				TdReconnect({-rh}, player)
				stop
			delete {-td-reconn::%player%}
		if {-td-inv::%player%} is not set:
			send "&cУ вас нет активных приглашений или кооп-игры для переподключения." to player
			stop
		set {-host} to {-td-inv::%player%}
		delete {-td-inv::%player%}
		if {-host} is not online:
			send "&cХост %{-host}% не в сети!" to player
			stop
		if {-td-team::%{-host}%::*} is empty:
			add {-host} to {-td-team::%{-host}%::*}
		if {-td-team::%{-host}%::*} does not contain %player%:
			if size of {-td-team::%{-host}%::*} >= 4:
				send "&cКоманда уже полная (4/4)!" to player
				stop
			add %player% to {-td-team::%{-host}%::*}
		if {-td-active::%{-host}%} is 1:
			TdReconnect({-host}, player)
			stop
		set {-members} to join {-td-team::%{-host}%::*} with ", "
		send "&aВы в команде с &e%{-members}%&a! Ждите старта от хоста &f%{-host}%" to player
		send "&a%player% присоединился! Команда: &e%{-members}% &7— жмите ▶ ИГРАТЬ в /td!" to {-host}
		play sound entity.player.levelup to {-host} with volume 0.5 pitch 1.4
		TdRefreshMenuInfo({-host})
		stop
	TdOpenMenu(player)

# --- Горячие клавиши (1..9 башни, C — иконка у курсора, X — продать, Space — волна/скорость) ---

on key 1 in screen "Tower Defense":
	TdSelectTool(player, 1)

on key 2 in screen "Tower Defense":
	TdSelectTool(player, 2)

on key 3 in screen "Tower Defense":
	TdSelectTool(player, 3)

on key 4 in screen "Tower Defense":
	TdSelectTool(player, 4)

on key 5 in screen "Tower Defense":
	TdSelectTool(player, 5)

on key 6 in screen "Tower Defense":
	TdSelectTool(player, 6)

on key 7 in screen "Tower Defense":
	TdSelectTool(player, 7)

on key 8 in screen "Tower Defense":
	TdSelectTool(player, 8)

on key 9 in screen "Tower Defense":
	TdSelectTool(player, 9)

on key c in screen "Tower Defense":
	TdToggleCursorKey(player)

on key x in screen "Tower Defense":
	TdToggleSell(player)

on key space in screen "Tower Defense":
	TdWaveOrSpeed(player)

on key r in screen "Tower Defense":
	TdRestartCurrent(player)

on key m in screen "Tower Defense":
	TdOpenMenu(player)

on toggle "autowave":
	set {-g} to {-td-host::%player%}
	if {-g} is not set:
		stop
	if %value% is "true":
		set {-td-auto::%{-g}%} to 1
		TdLog({-g}, "&a⚙ Авто-волна: ВКЛ")
	else:
		set {-td-auto::%{-g}%} to 0
		TdLog({-g}, "&7⚙ Авто-волна: ВЫКЛ")

on toggle "cursortog":
	if %value% is "true":
		set {-td-cur-on::%player%} to 1
		if {-td-tool::%player%} > 0 or {-td-sell::%player%} is 1:
			show screen element "cursor_icon" to player
		set text of screen element "status_txt" for player to "&bИконка башни у курсора включена &8(C — переключить)"
	else:
		set {-td-cur-on::%player%} to 0
		hide screen element "cursor_icon" from player
		set text of screen element "status_txt" for player to "&7Иконка башни у курсора скрыта &8(C — вернуть)"

function TdToggleCursorKey(p: player):
	if {-td-cur-on::%p%} is 0:
		set {-td-cur-on::%p%} to 1
		set value of screen element "cursortog" for p to true
		if {-td-tool::%p%} > 0 or {-td-sell::%p%} is 1:
			show screen element "cursor_icon" to p
		set text of screen element "status_txt" for p to "&bИконка башни у курсора включена"
	else:
		set {-td-cur-on::%p%} to 0
		set value of screen element "cursortog" for p to false
		hide screen element "cursor_icon" from p
		set text of screen element "status_txt" for p to "&7Иконка башни у курсора выключена"
	play sound ui.button.click to p with volume 0.35 pitch 1.4

# --- Игровой цикл (x1 = 5 тиков/сек + 4-tick 60 FPS tween; x2 = 10 тиков/сек + 2-tick 60 FPS tween) ---

every 2 ticks:
	loop all players:
		if {-td-active::%loop-player%} is 1:
			set {-run} to 1
			if {-td-speed::%loop-player%} is 1 or {-td-in-wave::%loop-player%} is 0:
				add 1 to {-td-sub::%loop-player%}
				if {-td-sub::%loop-player%} < 2:
					set {-run} to 0
				else:
					set {-td-sub::%loop-player%} to 0
			if {-run} is 1:
				clear {-td-online::%loop-player%::*}
				loop {-td-team::%loop-player%::*}:
					set {-tp} to %loop-value%
					if {-tp} is online and {-tp} has screen "Tower Defense" open:
						add {-tp} to {-td-online::%loop-player%::*}
				if size of {-td-online::%loop-player%::*} > 0:
					TdTick(loop-player)
				else:
					delete {-td-active::%loop-player%}
					delete {-td-over::%loop-player%}

function TdOnLeave(p: player):
	set {-g} to {-td-host::%p%}
	if {-g} is not set:
		stop
	# Сразу убираем игрока из {-td-online}, чтобы update screen и gui-эффекты больше не приходили ему!
	remove %p% from {-td-online::%{-g}%::*}
	set {-pcnt} to size of {-td-team::%{-g}%::*}
	if {-pcnt} <= 1:
		delete {-td-active::%{-g}%}
		delete {-td-over::%{-g}%}
		delete {-td-host::%p%}
		clear {-td-team::%{-g}%::*}
		clear {-td-online::%{-g}%::*}
		stop
	# В коопе сохраняем право вернуться по /td join, но не спамим повторно и не открываем экран заново
	if {-td-active::%{-g}%} is 1 and {-td-over::%{-g}%} is 0:
		if {-td-reconn::%p%} is not set:
			set {-td-reconn::%p%} to {-g}
			if p is online:
				send "&e[TD Кооп] &7Вы вышли из окна игры. Вернуться обратно: &a/td join" to p
			if size of {-td-online::%{-g}%::*} > 0:
				TdLog({-g}, "&e↩ %p% вышел")

function TdReconnect(g: player, p: player):
	set {-m} to {-td-map::%g%}
	if {-td-team::%g%::*} does not contain %p%:
		add %p% to {-td-team::%g%::*}
	if {-td-online::%g%::*} does not contain %p%:
		add %p% to {-td-online::%g%::*}
	set {-td-host::%p%} to %g%
	if {-td-gold::%p%} is not set:
		set {-td-gold::%p%} to 120
	set {-td-tool::%p%} to 0
	set {-td-sell::%p%} to 0
	set {-td-cur-on::%p%} to 1
	if {-m} is 1:
		TdOpenMap1(p)
	else if {-m} is 2:
		TdOpenMap2(p)
	else if {-m} is 3:
		TdOpenMap3(p)
	else if {-m} is 4:
		TdOpenMap4(p)
	else:
		TdOpenMap5(p)
	set {-tmax} to {-td-tmax::%g%}
	if {-tmax} > 0:
		loop {-tmax} times:
			set {-ti} to %loop-number%
			if {-td-ta::%g%::%{-ti}%} is 1:
				TdSyncTowerVisual(g, {-ti})
	loop 12 times:
		set {-ei} to %loop-number%
		if {-td-ea::%g%::%{-ei}%} is 1:
			set {-ex} to {-td-ex::%g%::%{-ei}%}
			set {-ey} to {-td-ey::%g%::%{-ei}%}
			set image of screen element "e%{-ei}%" for p to {-td-etex::%g%::%{-ei}%}
			set position of screen element "e%{-ei}%" for p to {-ex} {-ey}
			show screen element "e%{-ei}%" to p
			set {-hy} to {-ey} - 11
			if {-td-eboss::%g%::%{-ei}%} is 1:
				set {-hy} to {-ey} - 16
				set max of screen bar "boss_bar" for p to {-td-emhp::%g%::%{-ei}%}
				set value of screen bar "boss_bar" for p to {-td-ehp::%g%::%{-ei}%}
				show screen element "boss_bar" to p
			set max of screen bar "ehp%{-ei}%" for p to {-td-emhp::%g%::%{-ei}%}
			set value of screen bar "ehp%{-ei}%" for p to {-td-ehp::%g%::%{-ei}%}
			set position of screen element "ehp%{-ei}%" for p to {-ex} {-hy}
			show screen element "ehp%{-ei}%" to p
	TdSyncHud(g)
	TdLog(g, "&a↪ %p% в игре!")
	play sound entity.player.levelup to p with volume 0.5 pitch 1.3

# ==========================================================
#  ХЕЛПЕРЫ СИНХРОНИЗАЦИИ И ЖУРНАЛА СОБЫТИЙ (LIST)
# ==========================================================

function TdLog(g: player, msg: text):
	# Сдвигаем последние 9 строк вниз, новая запись всегда сверху (#1)!
	set {-i} to 9
	loop 8 times:
		set {-prev} to {-i} - 1
		if {-td-log::%g%::%{-prev}%} is set:
			set {-td-log::%g%::%{-i}%} to {-td-log::%g%::%{-prev}%}
		remove 1 from {-i}
	set {-td-log::%g%::1} to msg
	# Обновляем list ТОЛЬКО игрокам с открытым экраном, чтобы update screen не переоткрывал закрытый экран!
	loop {-td-online::%g%::*}:
		set {-tp} to %loop-value%
		if {-tp} is online and {-tp} has screen "Tower Defense" open:
			update screen "Tower Defense" for {-tp}:
				list from {-td-log::%g%::*} vertical no scrollbar header "&b&l⚡ СОБЫТИЯ" at 179 -8 size 104x174 anchor center layer 12 id ev_log:
					row "%loop-value%"

function TdSyncPos(g: player, id: text, x: number, y: number):
	set position of screen element "%id%" for {-td-online::%g%::*} to x y

function TdSyncAnim(g: player, id: text, x: number, y: number, dur: number):
	animate screen "%id%" for {-td-online::%g%::*} to x y in dur ticks

function TdSyncImg(g: player, id: text, tex: text):
	set image of screen element "%id%" for {-td-online::%g%::*} to tex

function TdSyncTxt(g: player, id: text, txt: text):
	set text of screen element "%id%" for {-td-online::%g%::*} to txt

function TdSyncShow(g: player, id: text):
	show screen element "%id%" to {-td-online::%g%::*}

function TdSyncHide(g: player, id: text):
	hide screen element "%id%" from {-td-online::%g%::*}

function TdSyncCell(g: player, col: integer, row: integer, cx: number, cy: number, tex: text, tip: text, hrad: number, hcol: text):
	loop {-td-online::%g%::*}:
		set {-tp} to %loop-value%
		if {-tp} is online and {-tp} has screen "Tower Defense" open:
			update screen "Tower Defense" for {-tp}:
				image "td:textures/gui/tile_build.png" at 0 0 size 22x22 texture 64x64 anchor center layer 3 id "c%col%_%row%" run TdCellClick(player, %col%, %row%) keep open tooltip "%tip%"
			set image of screen element "c%col%_%row%" for {-tp} to tex
			set position of screen element "c%col%_%row%" for {-tp} to cx cy
			if hrad > 0:
				set hover_circle of screen element "c%col%_%row%" for {-tp} to hrad color "%hcol%"
			else:
				set hover_circle of screen element "c%col%_%row%" for {-tp} to 0

function TdSyncStatus(g: player, txt: text):
	set text of screen element "status_txt" for {-td-online::%g%::*} to txt

function TdSyncSound(g: player, snd: text, vol: number, pit: number):
	play sound %snd% to {-td-online::%g%::*} with volume vol pitch pit

function TdSyncBar(g: player, id: text, val: number, mx: number):
	if mx > 0:
		set max of screen bar "%id%" for {-td-online::%g%::*} to mx
	set value of screen bar "%id%" for {-td-online::%g%::*} to val

function TdSyncBossBar(g: player, val: number, mx: number):
	TdSyncBar(g, "boss_bar", val, mx)

function TdSyncGoldScore(g: player):
	set text of screen element "hud_score" for {-td-online::%g%::*} to "&a%{-td-score::%g%}%"
	loop {-td-online::%g%::*}:
		set {-tp} to %loop-value%
		set text of screen element "hud_gold" for {-tp} to "&6%{-td-gold::%{-tp}%}%"

function TdSyncHud(g: player):
	set {-sp} to "x1"
	if {-td-speed::%g%} is 2:
		set {-sp} to "&b&lx2"
	set {-m} to {-td-map::%g%}
	set {-mtag} to "&aДолина"
	if {-m} is 2:
		set {-mtag} to "&6Каньон"
	else if {-m} is 3:
		set {-mtag} to "&bЦитадель"
	else if {-m} is 4:
		set {-mtag} to "&2Болото"
	else if {-m} is 5:
		set {-mtag} to "&dРазлом"
	set {-pcnt} to size of {-td-team::%g%::*}
	set {-tstr} to "%{-mtag}% &8| &7Соло &7[%{-sp}%&7]"
	if {-pcnt} > 1:
		set {-names} to join {-td-team::%g%::*} with ","
		set {-tstr} to "%{-mtag}% &8| &d%{-pcnt}%игр &7[%{-sp}%&7]"
	set text of screen element "hud_wave" for {-td-online::%g%::*} to "&f%{-td-wave::%g%}%/%{-td-maxw::%g%}%"
	set text of screen element "hud_hp" for {-td-online::%g%::*} to "&c%{-td-hp::%g%}%"
	set text of screen element "hud_score" for {-td-online::%g%::*} to "&a%{-td-score::%g%}%"
	set text of screen element "hud_team" for {-td-online::%g%::*} to {-tstr}
	loop {-td-online::%g%::*}:
		set {-tp} to %loop-value%
		set text of screen element "hud_gold" for {-tp} to "&6%{-td-gold::%{-tp}%}%"

function TdShowRange(p: player, tx: number, ty: number, rng: number, isBuff: number):
	hide screen element "rng_s" from p
	hide screen element "rng_m" from p
	hide screen element "rng_l" from p
	hide screen element "rng_b" from p
	if rng <= 0:
		stop
	if isBuff is 1:
		set position of screen element "rng_b" for p to tx ty
		show screen element "rng_b" to p
		stop
	if rng >= 140:
		set position of screen element "rng_l" for p to tx ty
		show screen element "rng_l" to p
	else if rng >= 95:
		set position of screen element "rng_m" for p to tx ty
		show screen element "rng_m" to p
	else:
		set position of screen element "rng_s" for p to tx ty
		show screen element "rng_s" to p

# --- Пересчёт баффов от Боевых Маяков (#9) и обновление стильных тултипов ---

function TdRecalcBuffs(g: player):
	set {-tmax} to {-td-tmax::%g%}
	if {-tmax} <= 0:
		stop
	loop {-tmax} times:
		set {-i} to %loop-number%
		if {-td-ta::%g%::%{-i}%} is 1:
			set {-td-tbuff::%g%::%{-i}%} to 1.0
			set {-td-tfast::%g%::%{-i}%} to 0
	loop {-tmax} times:
		set {-bi} to %loop-number%
		if {-td-ta::%g%::%{-bi}%} is 1 and {-td-tt::%g%::%{-bi}%} is 9:
			set {-bl} to {-td-tl::%g%::%{-bi}%}
			set {-bx} to {-td-tx::%g%::%{-bi}%}
			set {-by} to {-td-ty::%g%::%{-bi}%}
			TdTowerRange(g, 9, {-bl})
			set {-br2} to {-td-tmp-rng} * {-td-tmp-rng}
			set {-bmul} to 1.18 + ({-bl} - 1) * 0.08
			if {-bl} >= 5:
				set {-bmul} to 1.55
			loop {-tmax} times:
				set {-ti} to %loop-number%
				if {-td-ta::%g%::%{-ti}%} is 1 and {-td-tt::%g%::%{-ti}%} is not 9 and {-td-tt::%g%::%{-ti}%} is not 8:
					set {-dx} to {-td-tx::%g%::%{-ti}%} - {-bx}
					set {-dy} to {-td-ty::%g%::%{-ti}%} - {-by}
					if {-dx} * {-dx} + {-dy} * {-dy} <= {-br2}:
						if {-bmul} > {-td-tbuff::%g%::%{-ti}%}:
							set {-td-tbuff::%g%::%{-ti}%} to {-bmul}
						if {-bl} >= 5:
							set {-td-tfast::%g%::%{-ti}%} to 1
	loop {-tmax} times:
		set {-ti} to %loop-number%
		if {-td-ta::%g%::%{-ti}%} is 1:
			TdSyncTowerVisual(g, {-ti})

function TdSyncTowerVisual(g: player, tid: number):
	set {-tc} to {-td-tcol::%g%::%tid%}
	set {-tr} to {-td-trow::%g%::%tid%}
	set {-tt} to {-td-tt::%g%::%tid%}
	set {-tl} to {-td-tl::%g%::%tid%}
	set {-tx} to {-td-tx::%g%::%tid%}
	set {-ty} to {-td-ty::%g%::%tid%}
	set {-own} to {-td-towner::%g%::%tid%}
	set {-ref} to round({-td-tcost::%g%::%tid%} * 0.7)
	set {-bmul} to {-td-tbuff::%g%::%tid%}
	if {-bmul} is not set:
		set {-bmul} to 1.0

	set {-tex} to "td:textures/gui/tower_archer.png"
	set {-tname} to "&eБаллиста"
	set {-bdmg} to 15
	set {-evod} to "Шквал (2 цели)"
	if {-tt} is 2:
		set {-tex} to "td:textures/gui/tower_cannon.png"
		set {-tname} to "&6Мортира"
		set {-bdmg} to 36
		set {-evod} to "Напалм (сплеш + ожог)"
	else if {-tt} is 3:
		set {-tex} to "td:textures/gui/tower_frost.png"
		set {-tname} to "&bКрио-Обелиск"
		set {-bdmg} to 8
		set {-evod} to "Стужа (50%% замедл. + фриз)"
	else if {-tt} is 4:
		set {-tex} to "td:textures/gui/tower_mage.png"
		set {-tname} to "&dАркан-Шпиль"
		set {-bdmg} to 32
		set {-evod} to "Убийца Боссов (x2 + 4%% HP)"
	else if {-tt} is 5:
		set {-tex} to "td:textures/gui/tower_tesla.png"
		set {-tname} to "&bПлазмо-Тесла"
		set {-bdmg} to 20
		set {-evod} to "Шторм (5 целей + стан)"
	else if {-tt} is 6:
		set {-tex} to "td:textures/gui/tower_sniper.png"
		set {-tname} to "&cРейлган"
		set {-bdmg} to 56
		set {-evod} to "Казнь (<20%% HP + крит 45%%)"
	else if {-tt} is 7:
		set {-tex} to "td:textures/gui/tower_poison.png"
		set {-tname} to "&aЧумной Реактор"
		set {-bdmg} to 12
		set {-evod} to "Чума (заразная кислота)"
	else if {-tt} is 8:
		set {-tex} to "td:textures/gui/tower_farm.png"
		set {-tname} to "&6Монетный Двор"
		set {-bdmg} to 0
		set {-evod} to "Дворец (+180з и +1 HP)"
	else if {-tt} is 9:
		set {-tex} to "td:textures/gui/tower_buffer.png"
		set {-tname} to "&dБоевой Маяк"
		set {-bdmg} to 0
		set {-evod} to "Знамя (+55%% урон, +20%% скор., +25з)"

	set {-st} to "&e★"
	if {-tl} is 2:
		set {-st} to "&6★★"
	else if {-tl} is 3:
		set {-st} to "&d★★★"
	else if {-tl} is 4:
		set {-st} to "&b★★★★"
	else if {-tl} >= 5:
		set {-st} to "&6&l✦5★"

	TdTowerRange(g, {-tt}, {-tl})
	set {-rng} to {-td-tmp-rng}

	set {-mapMul} to 1.0
	if {-td-map::%g%} is 2:
		set {-mapMul} to 1.1

	if {-tt} is 8:
		set {-inc} to 28
		if {-tl} is 2:
			set {-inc} to 50
		else if {-tl} is 3:
			set {-inc} to 80
		else if {-tl} is 4:
			set {-inc} to 120
		else if {-tl} >= 5:
			set {-inc} to 180
		set {-stat} to "&8▪ &7Доход: &6+%{-inc}%з/волну"
	else if {-tt} is 9:
		set {-bpct} to 18 + ({-tl} - 1) * 8
		if {-tl} >= 5:
			set {-bpct} to 55
		set {-stat} to "&8▪ &7Бафф-аура: &d+%{-bpct}%% урона &8| &7Радиус: &b%{-rng}%"
	else:
		set {-cdmg} to round({-bdmg} * (1 + ({-tl} - 1) * 0.55) * {-bmul} * {-mapMul})
		if {-tl} >= 5:
			set {-cdmg} to round({-cdmg} * 1.4)
		set {-bstr} to ""
		if {-bmul} > 1.01:
			set {-bp} to round(({-bmul} - 1.0) * 100)
			set {-bstr} to " &d(+%{-bp}%% Маяк)"
		if {-tt} is 3:
			set {-stat} to "&8▪ &7Аура: &b%{-cdmg}% &8| &7Радиус: &b%{-rng}%%{-bstr}%"
		else:
			set {-stat} to "&8▪ &7Урон: &f%{-cdmg}% &8| &7Дальн: &b%{-rng}%%{-bstr}%"

	if {-tl} >= 5:
		set {-act} to "&6✦ %{-evod}% &8| &cX: +%{-ref}%з"
	else if {-tl} is 4:
		set {-act} to "&6▶ ЛКМ: ✦5★ Эволюция (570з) &8| &cX: +%{-ref}%з"
	else:
		set {-up} to 95
		if {-tl} is 2:
			set {-up} to 210
		else if {-tl} is 3:
			set {-up} to 360
		set {-nl} to {-tl} + 1
		set {-act} to "&a▶ ЛКМ: Ур.%{-nl}% &6(%{-up}%з) &8| &cX: +%{-ref}%з"

	set {-tip} to "&b&l▍ %{-tname}% %{-st}% &8[Ур.%{-tl}%]%nl%%{-stat}%%nl%&8▪ &7Владелец: &f%{-own}%%nl%%{-act}%"
	set {-hc} to "#38BDF8"
	if {-tt} is 9:
		set {-hc} to "#FACC15"
	TdSyncCell(g, {-tc}, {-tr}, {-tx}, {-ty}, {-tex}, {-tip}, {-rng}, {-hc})
	set {-sy} to {-ty} + 8
	TdSyncPos(g, "st%tid%", {-tx}, {-sy})
	TdSyncTxt(g, "st%tid%", {-st})
	TdSyncShow(g, "st%tid%")

# ==========================================================
#  СОВРЕМЕННОЕ МЕНЮ: 5 HD КАРТ, СЛОЖНОСТЬ И КООП (1..4)
# ==========================================================

function TdOpenMenu(p: player):
	TdOnLeave(p)
	if {-td-map::%p%} is not set:
		set {-td-map::%p%} to 1
	if {-td-diff::%p%} is not set:
		set {-td-diff::%p%} to 2
	if {-td-team::%p%::*} is empty:
		add %p% to {-td-team::%p%::*}
	open screen "Tower Defense" to p:
		box at 0 0 size 460x262 anchor center color #EE090E1A gradient #EE111C33 border #FF38BDF8 border_width 1 radius 10 shadow blur layer 0
		box at 0 -115 size 450x24 anchor center color #FF1D4ED8 to #FF7C3AED horizontal radius 6 layer 1
		text "&b&l✦ &f&lTOWER DEFENSE &b&lULTRA HD v4 &b&l✦" at 0 -115 anchor center shadow layer 2
		text "&75 уникальных карт | 9 башен (Маяк-Баффер!) | 16 видов врагов | Кооп: &e/td coop <ник>" at 0 -94 anchor center shadow layer 2

		# 5 карточек под HD превью карт + плавный индикатор выбора
		box at -172 -44 size 82x54 anchor center color #CC162032 border #3338BDF8 radius 5 shadow layer 1
		box at -86 -44 size 82x54 anchor center color #CC162032 border #3338BDF8 radius 5 shadow layer 1
		box at 0 -44 size 82x54 anchor center color #CC162032 border #3338BDF8 radius 5 shadow layer 1
		box at 86 -44 size 82x54 anchor center color #CC162032 border #3338BDF8 radius 5 shadow layer 1
		box at 172 -44 size 82x54 anchor center color #CC162032 border #3338BDF8 radius 5 shadow layer 1
		box at -172 -14 size 76x2 anchor center color #FF22C55E radius 1 id map_sel layer 3

		image "td:textures/gui/map_valley.png" at -172 -45 size 76x38 texture 896x448 anchor center hover_scale 1.07 layer 2 run TdPickMap(player, 1) keep open tooltip "&a&l1. Изумрудная долина%nl%&8▪ &7Маршрут: Классический S-путь%nl%&8▪ &7Все клетки свободны%nl%&a✦ Бонус: &f+20 стартового золота"
		text "&a1.Долина" at -172 -5 anchor center shadow scale 0.9 layer 2 id m_lbl1

		image "td:textures/gui/map_volcano.png" at -86 -45 size 76x38 texture 896x448 anchor center hover_scale 1.07 layer 2 run TdPickMap(player, 2) keep open tooltip "&6&l2. Лавовый каньон%nl%&8▪ &7Маршрут: Двойной зигзаг%nl%&6✦ Особенность: &f+10% урон башен%nl%&c⚠ Угроза: &fвраги на 8% быстрее"
		text "&72.Каньон" at -86 -5 anchor center shadow scale 0.9 layer 2 id m_lbl2

		image "td:textures/gui/map_ice.png" at 0 -45 size 76x38 texture 896x448 anchor center hover_scale 1.07 layer 2 run TdPickMap(player, 3) keep open tooltip "&b&l3. Ледяная цитадель%nl%&8▪ &7Маршрут: Арктический серпантин%nl%&b✦ Особенность: &f+12% дальность всех башен"
		text "&73.Цитадель" at 0 -5 anchor center shadow scale 0.9 layer 2 id m_lbl3

		image "td:textures/gui/map_swamp.png" at 86 -45 size 76x38 texture 896x448 anchor center hover_scale 1.07 layer 2 run TdPickMap(player, 4) keep open tooltip "&2&l4. Проклятое болото &e[НОВАЯ!]%nl%&c⚠ 18 клеток трясины (нельзя строить!)%nl%&6✦ Бонус: &f+20% золота за убийство мобов%nl%&c⚠ Угроза: &f+15% HP у всех врагов"
		text "&74.Болото" at 86 -5 anchor center shadow scale 0.9 layer 2 id m_lbl4

		image "td:textures/gui/map_void.png" at 172 -45 size 76x38 texture 896x448 anchor center hover_scale 1.07 layer 2 run TdPickMap(player, 5) keep open tooltip "&d&l5. Небесный разлом &c[ХАРДКОР!]%nl%&c⚠ Короткий штурмовой путь + 30 обрывов!%nl%&c⚠ Враги: +20% HP и +15% скорость (без бонусов!)%nl%&d✦ Награда: &fx1.5 очков рекорда"
		text "&75.Разлом" at 172 -5 anchor center shadow scale 0.9 layer 2 id m_lbl5

		# Блок сложности и команды
		box at 0 52 size 432x72 anchor center color #AA111827 border #2238BDF8 radius 6 layer 1
		text "&b&lСЛОЖНОСТЬ КАМПАНИИ" at 0 24 anchor center shadow layer 2
		button "&aЛёгкий (10 волн)" at -126 43 size 116x20 anchor center layer 2 run TdPickDiff(player, 1) keep open tooltip "&aЛёгкий%nl%&8▪ &710 волн | 2 босса | 15 HP | 140з"
		button "&eСредний (20 волн)" at 0 43 size 116x20 anchor center layer 2 run TdPickDiff(player, 2) keep open tooltip "&eСредний%nl%&8▪ &720 волн | 4 босса | 10 HP | 120з"
		button "&cТяжёлый (30 волн)" at 126 43 size 116x20 anchor center layer 2 run TdPickDiff(player, 3) keep open tooltip "&cТяжёлый%nl%&8▪ &730 волн | 6 боссов | 8 HP | 110з"

		text "&7Выбрано: &aДолина &7| &eСредний" at 0 68 anchor center shadow layer 2 id menu_info
		text "&dКоманда (1/4): &f%p%" at 0 80 anchor center shadow layer 2 id menu_team
		button "&a&l▶ ИГРАТЬ" at -60 107 size 128x22 anchor center layer 2 run TdStartGame(player) keep open
		button "&6Топ-5" at 74 107 size 94x22 anchor center layer 2 run TdShowTop(player) keep open
	TdRefreshMenuInfo(p)

function TdPickMap(p: player, m: number):
	set {-td-map::%p%} to m
	play sound ui.button.click to p with volume 0.45 pitch 1.3
	TdRefreshMenuInfo(p)

function TdPickDiff(p: player, d: number):
	set {-td-diff::%p%} to d
	play sound ui.button.click to p with volume 0.45 pitch 1.2
	TdRefreshMenuInfo(p)

function TdRefreshMenuInfo(p: player):
	set {-m} to {-td-map::%p%}
	set {-d} to {-td-diff::%p%}
	set text of screen element "m_lbl1" for p to "&71.Долина"
	set text of screen element "m_lbl2" for p to "&72.Каньон"
	set text of screen element "m_lbl3" for p to "&73.Цитадель"
	set text of screen element "m_lbl4" for p to "&74.Болото"
	set text of screen element "m_lbl5" for p to "&75.Разлом"
	set {-mn} to "&aДолина (+20з)"
	if {-m} is 1:
		set text of screen element "m_lbl1" for p to "&a&l▶ 1.Долина"
		animate screen "map_sel" for p to -172 -14 in 4 ticks
	else if {-m} is 2:
		set {-mn} to "&6Каньон (+10% урон, +8% скор.)"
		set text of screen element "m_lbl2" for p to "&6&l▶ 2.Каньон"
		animate screen "map_sel" for p to -86 -14 in 4 ticks
	else if {-m} is 3:
		set {-mn} to "&bЦитадель (+12% дальность)"
		set text of screen element "m_lbl3" for p to "&b&l▶ 3.Цитадель"
		animate screen "map_sel" for p to 0 -14 in 4 ticks
	else if {-m} is 4:
		set {-mn} to "&2Болото (топи, +20% золота, +15% HP)"
		set text of screen element "m_lbl4" for p to "&2&l▶ 4.Болото"
		animate screen "map_sel" for p to 86 -14 in 4 ticks
	else:
		set {-mn} to "&dРазлом (ХАРДКОР: короткий путь, +20% HP, +15% скор.)"
		set text of screen element "m_lbl5" for p to "&d&l▶ 5.Разлом"
		animate screen "map_sel" for p to 172 -14 in 4 ticks
	set {-dn} to "&eСредний (20 волн)"
	if {-d} is 1:
		set {-dn} to "&aЛёгкий (10 волн)"
	else if {-d} is 3:
		set {-dn} to "&cТяжёлый (30 волн)"
	set text of screen element "menu_info" for p to "&7Карта: %{-mn}% &8| %{-dn}%"
	set {-pcnt} to size of {-td-team::%p%::*}
	set {-names} to join {-td-team::%p%::*} with ", "
	set text of screen element "menu_team" for p to "&dКоманда (%{-pcnt}%/4): &f%{-names}%"

# ==========================================================
#  ЗАПУСК ИГРЫ (1..4 ИГРОКА, 5 КАРТ, ДО 32 БАШЕН)
# ==========================================================

function TdRestartCurrent(p: player):
	set {-g} to {-td-host::%p%}
	if {-g} is not set:
		TdStartGame(p)
		stop
	TdStartGame({-g})

function TdStartGame(host: player):
	if {-td-map::%host%} is not set:
		set {-td-map::%host%} to 1
	if {-td-diff::%host%} is not set:
		set {-td-diff::%host%} to 2
	set {-m} to {-td-map::%host%}
	set {-d} to {-td-diff::%host%}

	clear {-tmp-team::*}
	add %host% to {-tmp-team::*}
	loop {-td-team::%host%::*}:
		set {-cand} to %loop-value%
		if {-cand} is not %host% and {-cand} is online:
			if size of {-tmp-team::*} < 4:
				add {-cand} to {-tmp-team::*}
	clear {-td-team::%host%::*}
	clear {-td-online::%host%::*}
	loop {-tmp-team::*}:
		add %loop-value% to {-td-team::%host%::*}
		add %loop-value% to {-td-online::%host%::*}

	set {-td-active::%host%} to 1
	set {-td-over::%host%} to 0
	set {-td-wave::%host%} to 0
	set {-td-score::%host%} to 0
	set {-td-speed::%host%} to 1
	set {-td-sub::%host%} to 0
	set {-td-auto::%host%} to 0
	set {-td-auto-cd::%host%} to 0
	set {-td-in-wave::%host%} to 0
	set {-td-to-spawn::%host%} to 0
	set {-td-spawn-boss::%host%} to 0
	set {-td-spawn-cd::%host%} to 0
	set {-td-boss-vis::%host%} to 0
	set {-td-tmax::%host%} to 0
	set {-td-prcnt::%host%} to 0

	if {-d} is 1:
		set {-td-maxw::%host%} to 10
		set {-sgold} to 140
		set {-td-hp::%host%} to 15
	else if {-d} is 2:
		set {-td-maxw::%host%} to 20
		set {-sgold} to 120
		set {-td-hp::%host%} to 10
	else:
		set {-td-maxw::%host%} to 30
		set {-sgold} to 110
		set {-td-hp::%host%} to 8

	# Бонус карты 1 (Долина): +20 стартового золота
	if {-m} is 1:
		add 20 to {-sgold}

	clear {-td-log::%host%::*}
	set {-td-log::%host%::1} to "&7Ждём 1-ю волну..."
	set {-td-log::%host%::2} to "&8Наведи на башню"
	set {-td-log::%host%::3} to "&8для круга радиуса!"

	clear {-td-grid::%host%::*}
	loop 32 times:
		set {-i} to %loop-number%
		set {-td-ta::%host%::%{-i}%} to 0
		set {-td-tt::%host%::%{-i}%} to 0
		set {-td-tl::%host%::%{-i}%} to 0
		set {-td-tcd::%host%::%{-i}%} to 0
		set {-td-tcost::%host%::%{-i}%} to 0
		set {-td-tbuff::%host%::%{-i}%} to 1.0
		set {-td-tfast::%host%::%{-i}%} to 0
		set {-td-towner::%host%::%{-i}%} to ""

	loop 12 times:
		set {-i} to %loop-number%
		set {-td-ea::%host%::%{-i}%} to 0
		set {-td-eboss::%host%::%{-i}%} to 0
		set {-td-eslow::%host%::%{-i}%} to 0
		set {-td-efreeze::%host%::%{-i}%} to 0
		set {-td-eburn::%host%::%{-i}%} to 0
		set {-td-epoison::%host%::%{-i}%} to 0
		set {-td-eregen::%host%::%{-i}%} to 0
		set {-td-eimm::%host%::%{-i}%} to 0
		set {-td-edodge::%host%::%{-i}%} to 0
		set {-td-eshield::%host%::%{-i}%} to 0

	loop 6 times:
		set {-i} to %loop-number%
		set {-td-pra::%host%::%{-i}%} to 0

	TdLoadWaypoints(host, {-m})
	TdInitBlocked(host, {-m})

	loop {-td-team::%host%::*}:
		set {-tp} to %loop-value%
		set {-td-host::%{-tp}%} to %host%
		set {-td-gold::%{-tp}%} to {-sgold}
		set {-td-tool::%{-tp}%} to 0
		set {-td-sell::%{-tp}%} to 0
		set {-td-cur-on::%{-tp}%} to 1
		if {-m} is 1:
			TdOpenMap1({-tp})
		else if {-m} is 2:
			TdOpenMap2({-tp})
		else if {-m} is 3:
			TdOpenMap3({-tp})
		else if {-m} is 4:
			TdOpenMap4({-tp})
		else:
			TdOpenMap5({-tp})

	TdSyncHud(host)
	TdLog(host, "&a▶ Матч начался!")

function TdLoadWaypoints(g: player, mapId: number):
	if mapId is 1:
		set {-td-wpcnt::%g%} to 6
		set {-td-wpx::%g%::1} to -221
		set {-td-wpy::%g%::1} to -63
		set {-td-wpx::%g%::2} to 65
		set {-td-wpy::%g%::2} to -63
		set {-td-wpx::%g%::3} to 65
		set {-td-wpy::%g%::3} to -19
		set {-td-wpx::%g%::4} to -177
		set {-td-wpy::%g%::4} to -19
		set {-td-wpx::%g%::5} to -177
		set {-td-wpy::%g%::5} to 47
		set {-td-wpx::%g%::6} to 109
		set {-td-wpy::%g%::6} to 47
	else if mapId is 2:
		set {-td-wpcnt::%g%} to 8
		set {-td-wpx::%g%::1} to -221
		set {-td-wpy::%g%::1} to -85
		set {-td-wpx::%g%::2} to -133
		set {-td-wpy::%g%::2} to -85
		set {-td-wpx::%g%::3} to -133
		set {-td-wpy::%g%::3} to 47
		set {-td-wpx::%g%::4} to -23
		set {-td-wpy::%g%::4} to 47
		set {-td-wpx::%g%::5} to -23
		set {-td-wpy::%g%::5} to -63
		set {-td-wpx::%g%::6} to 65
		set {-td-wpy::%g%::6} to -63
		set {-td-wpx::%g%::7} to 65
		set {-td-wpy::%g%::7} to 25
		set {-td-wpx::%g%::8} to 109
		set {-td-wpy::%g%::8} to 25
	else if mapId is 3:
		set {-td-wpcnt::%g%} to 8
		set {-td-wpx::%g%::1} to -221
		set {-td-wpy::%g%::1} to 3
		set {-td-wpx::%g%::2} to -155
		set {-td-wpy::%g%::2} to 3
		set {-td-wpx::%g%::3} to -155
		set {-td-wpy::%g%::3} to -63
		set {-td-wpx::%g%::4} to -67
		set {-td-wpy::%g%::4} to -63
		set {-td-wpx::%g%::5} to -67
		set {-td-wpy::%g%::5} to 47
		set {-td-wpx::%g%::6} to 21
		set {-td-wpy::%g%::6} to 47
		set {-td-wpx::%g%::7} to 21
		set {-td-wpy::%g%::7} to -41
		set {-td-wpx::%g%::8} to 109
		set {-td-wpy::%g%::8} to -41
	else if mapId is 4:
		set {-td-wpcnt::%g%} to 8
		set {-td-wpx::%g%::1} to -221
		set {-td-wpy::%g%::1} to -41
		set {-td-wpx::%g%::2} to -111
		set {-td-wpy::%g%::2} to -41
		set {-td-wpx::%g%::3} to -111
		set {-td-wpy::%g%::3} to -85
		set {-td-wpx::%g%::4} to -1
		set {-td-wpy::%g%::4} to -85
		set {-td-wpx::%g%::5} to -1
		set {-td-wpy::%g%::5} to 69
		set {-td-wpx::%g%::6} to 65
		set {-td-wpy::%g%::6} to 69
		set {-td-wpx::%g%::7} to 65
		set {-td-wpy::%g%::7} to -19
		set {-td-wpx::%g%::8} to 109
		set {-td-wpy::%g%::8} to -19
	else if mapId is 5:
		set {-td-wpcnt::%g%} to 8
		set {-td-wpx::%g%::1} to -221
		set {-td-wpy::%g%::1} to -19
		set {-td-wpx::%g%::2} to -133
		set {-td-wpy::%g%::2} to -19
		set {-td-wpx::%g%::3} to -133
		set {-td-wpy::%g%::3} to 25
		set {-td-wpx::%g%::4} to -45
		set {-td-wpy::%g%::4} to 25
		set {-td-wpx::%g%::5} to -45
		set {-td-wpy::%g%::5} to -41
		set {-td-wpx::%g%::6} to 21
		set {-td-wpy::%g%::6} to -41
		set {-td-wpx::%g%::7} to 21
		set {-td-wpy::%g%::7} to 3
		set {-td-wpx::%g%::8} to 109
		set {-td-wpy::%g%::8} to 3

function TdInitBlocked(g: player, mapId: number):
	clear {-td-blk::%g%::*}
	if mapId is 4:
		set {-td-blk::%g%::1_0} to 1
		set {-td-blk::%g%::2_0} to 1
		set {-td-blk::%g%::2_4} to 1
		set {-td-blk::%g%::3_4} to 1
		set {-td-blk::%g%::3_5} to 1
		set {-td-blk::%g%::4_6} to 1
		set {-td-blk::%g%::5_6} to 1
		set {-td-blk::%g%::6_2} to 1
		set {-td-blk::%g%::6_4} to 1
		set {-td-blk::%g%::7_4} to 1
		set {-td-blk::%g%::7_5} to 1
		set {-td-blk::%g%::8_2} to 1
		set {-td-blk::%g%::8_3} to 1
		set {-td-blk::%g%::11_2} to 1
		set {-td-blk::%g%::11_3} to 1
		set {-td-blk::%g%::12_1} to 1
		set {-td-blk::%g%::14_5} to 1
		set {-td-blk::%g%::14_6} to 1
	else if mapId is 5:
		set {-td-blk::%g%::0_0} to 1
		set {-td-blk::%g%::0_1} to 1
		set {-td-blk::%g%::0_2} to 1
		set {-td-blk::%g%::0_5} to 1
		set {-td-blk::%g%::0_6} to 1
		set {-td-blk::%g%::0_7} to 1
		set {-td-blk::%g%::1_0} to 1
		set {-td-blk::%g%::1_1} to 1
		set {-td-blk::%g%::1_5} to 1
		set {-td-blk::%g%::1_6} to 1
		set {-td-blk::%g%::1_7} to 1
		set {-td-blk::%g%::2_0} to 1
		set {-td-blk::%g%::2_6} to 1
		set {-td-blk::%g%::2_7} to 1
		set {-td-blk::%g%::5_0} to 1
		set {-td-blk::%g%::5_1} to 1
		set {-td-blk::%g%::5_7} to 1
		set {-td-blk::%g%::6_0} to 1
		set {-td-blk::%g%::6_1} to 1
		set {-td-blk::%g%::6_7} to 1
		set {-td-blk::%g%::7_0} to 1
		set {-td-blk::%g%::7_7} to 1
		set {-td-blk::%g%::9_6} to 1
		set {-td-blk::%g%::9_7} to 1
		set {-td-blk::%g%::10_6} to 1
		set {-td-blk::%g%::13_0} to 1
		set {-td-blk::%g%::14_0} to 1
		set {-td-blk::%g%::14_6} to 1
		set {-td-blk::%g%::15_0} to 1
		set {-td-blk::%g%::15_6} to 1

function TdOpenMap1(p: player):
	set {-g} to {-td-host::%p%}
	if {-g} is not set:
		set {-g} to %p%
	open screen "Tower Defense" to p:
		# Невидимый фон за экраном — клик в пустое место или за экран отвязывает выбранную башню от курсора
		box at 0 0 size 2000x2000 anchor center color #00000000 layer -1 run TdClearTool(player) keep open
		# Верхний HUD (скруглённая стеклянная панель + пилюли показателей)
		box at 0 -114 size 468x22 anchor center color #E6090E1A gradient #E6111C33 border #FF38BDF8 border_width 1 radius 6 shadow blur layer 11
		box at -202 -114 size 54x15 anchor center color #CC162032 border #4438BDF8 radius 4 layer 11
		box at -134 -114 size 62x15 anchor center color #CC162032 border #44FACC15 radius 4 layer 11
		box at -68 -114 size 48x15 anchor center color #CC162032 border #44EF4444 radius 4 layer 11
		box at -2 -114 size 66x15 anchor center color #CC162032 border #4422C55E radius 4 layer 11
		box at 132 -114 size 190x15 anchor center color #CC162032 border #44C084FC radius 4 layer 11
		image "td:textures/gui/icon_wave.png" at -220 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&eВолна"
		text "&f0/20" at -195 -114 anchor center shadow layer 12 id hud_wave
		image "td:textures/gui/icon_coin.png" at -154 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&6Личное золото"
		text "&6120" at -126 -114 anchor center shadow layer 12 id hud_gold
		image "td:textures/gui/icon_heart.png" at -82 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&cЗдоровье базы"
		text "&c10" at -60 -114 anchor center shadow layer 12 id hud_hp
		image "td:textures/gui/icon_trophy.png" at -24 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&aСчёт"
		text "&a0" at 6 -114 anchor center shadow layer 12 id hud_score
		text "&7Соло &7[x1]" at 132 -114 anchor center shadow layer 12 id hud_team

		# Тактическая рамка + HD карта 352x176 (текстура 896x448)
		box at -56 -8 size 358x182 anchor center color #AA090E1A border #AA38BDF8 border_width 2 radius 5 shadow layer 0
		image "td:textures/gui/map_valley.png" at -56 -8 size 352x176 texture 896x448 anchor center layer 1 id map run TdClearTool(player) keep open
		bar "boss_bar" value 100 max 100 at -56 -88 size 210x8 anchor center color #FFEF4444 background #CC1A0A0A text "&c&l☠ БОСС: %value%/%max%" shadow layer 14 hidden

		# Правая панель — Журнал событий (list без скроллбара)
		box at 179 -8 size 110x182 anchor center color #E6090E1A gradient #E6111827 border #6638BDF8 border_width 1 radius 5 shadow blur layer 11
		list from {-td-log::%{-g}%::*} vertical no scrollbar header "&b&l⚡ СОБЫТИЯ" at 179 -8 size 104x174 anchor center layer 12 id ev_log:
			row "%loop-value%"

		# Тактические радар-круги радиуса башен (авто-показ при наведении и клике!)
		image "td:textures/gui/range_circle.png" at 0 0 size 154x154 texture 256x256 anchor center layer 2 id rng_s hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 208x208 texture 256x256 anchor center layer 2 id rng_m hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 310x310 texture 256x256 anchor center layer 2 id rng_l hidden
		image "td:textures/gui/range_buff.png" at 0 0 size 150x150 texture 256x256 anchor center layer 2 id rng_b hidden

		# Сетка постройки 16x8 (клетка 22x22; пустые клетки без лишних тултипов)
		image "td:textures/gui/tile_build.png" at -221 -85 size 22x22 texture 28x28 anchor center layer 3 id c0_0 run TdCellClick(player, 0, 0) keep open
		image "td:textures/gui/tile_build.png" at -199 -85 size 22x22 texture 28x28 anchor center layer 3 id c1_0 run TdCellClick(player, 1, 0) keep open
		image "td:textures/gui/tile_build.png" at -177 -85 size 22x22 texture 28x28 anchor center layer 3 id c2_0 run TdCellClick(player, 2, 0) keep open
		image "td:textures/gui/tile_build.png" at -155 -85 size 22x22 texture 28x28 anchor center layer 3 id c3_0 run TdCellClick(player, 3, 0) keep open
		image "td:textures/gui/tile_build.png" at -133 -85 size 22x22 texture 28x28 anchor center layer 3 id c4_0 run TdCellClick(player, 4, 0) keep open
		image "td:textures/gui/tile_build.png" at -111 -85 size 22x22 texture 28x28 anchor center layer 3 id c5_0 run TdCellClick(player, 5, 0) keep open
		image "td:textures/gui/tile_build.png" at -89 -85 size 22x22 texture 28x28 anchor center layer 3 id c6_0 run TdCellClick(player, 6, 0) keep open
		image "td:textures/gui/tile_build.png" at -67 -85 size 22x22 texture 28x28 anchor center layer 3 id c7_0 run TdCellClick(player, 7, 0) keep open
		image "td:textures/gui/tile_build.png" at -45 -85 size 22x22 texture 28x28 anchor center layer 3 id c8_0 run TdCellClick(player, 8, 0) keep open
		image "td:textures/gui/tile_build.png" at -23 -85 size 22x22 texture 28x28 anchor center layer 3 id c9_0 run TdCellClick(player, 9, 0) keep open
		image "td:textures/gui/tile_build.png" at -1 -85 size 22x22 texture 28x28 anchor center layer 3 id c10_0 run TdCellClick(player, 10, 0) keep open
		image "td:textures/gui/tile_build.png" at 21 -85 size 22x22 texture 28x28 anchor center layer 3 id c11_0 run TdCellClick(player, 11, 0) keep open
		image "td:textures/gui/tile_build.png" at 43 -85 size 22x22 texture 28x28 anchor center layer 3 id c12_0 run TdCellClick(player, 12, 0) keep open
		image "td:textures/gui/tile_build.png" at 65 -85 size 22x22 texture 28x28 anchor center layer 3 id c13_0 run TdCellClick(player, 13, 0) keep open
		image "td:textures/gui/tile_build.png" at 87 -85 size 22x22 texture 28x28 anchor center layer 3 id c14_0 run TdCellClick(player, 14, 0) keep open
		image "td:textures/gui/tile_build.png" at 109 -85 size 22x22 texture 28x28 anchor center layer 3 id c15_0 run TdCellClick(player, 15, 0) keep open
		image "td:textures/gui/tile_build.png" at 87 -63 size 22x22 texture 28x28 anchor center layer 3 id c14_1 run TdCellClick(player, 14, 1) keep open
		image "td:textures/gui/tile_build.png" at 109 -63 size 22x22 texture 28x28 anchor center layer 3 id c15_1 run TdCellClick(player, 15, 1) keep open
		image "td:textures/gui/tile_build.png" at -221 -41 size 22x22 texture 28x28 anchor center layer 3 id c0_2 run TdCellClick(player, 0, 2) keep open
		image "td:textures/gui/tile_build.png" at -199 -41 size 22x22 texture 28x28 anchor center layer 3 id c1_2 run TdCellClick(player, 1, 2) keep open
		image "td:textures/gui/tile_build.png" at -177 -41 size 22x22 texture 28x28 anchor center layer 3 id c2_2 run TdCellClick(player, 2, 2) keep open
		image "td:textures/gui/tile_build.png" at -155 -41 size 22x22 texture 28x28 anchor center layer 3 id c3_2 run TdCellClick(player, 3, 2) keep open
		image "td:textures/gui/tile_build.png" at -133 -41 size 22x22 texture 28x28 anchor center layer 3 id c4_2 run TdCellClick(player, 4, 2) keep open
		image "td:textures/gui/tile_build.png" at -111 -41 size 22x22 texture 28x28 anchor center layer 3 id c5_2 run TdCellClick(player, 5, 2) keep open
		image "td:textures/gui/tile_build.png" at -89 -41 size 22x22 texture 28x28 anchor center layer 3 id c6_2 run TdCellClick(player, 6, 2) keep open
		image "td:textures/gui/tile_build.png" at -67 -41 size 22x22 texture 28x28 anchor center layer 3 id c7_2 run TdCellClick(player, 7, 2) keep open
		image "td:textures/gui/tile_build.png" at -45 -41 size 22x22 texture 28x28 anchor center layer 3 id c8_2 run TdCellClick(player, 8, 2) keep open
		image "td:textures/gui/tile_build.png" at -23 -41 size 22x22 texture 28x28 anchor center layer 3 id c9_2 run TdCellClick(player, 9, 2) keep open
		image "td:textures/gui/tile_build.png" at -1 -41 size 22x22 texture 28x28 anchor center layer 3 id c10_2 run TdCellClick(player, 10, 2) keep open
		image "td:textures/gui/tile_build.png" at 21 -41 size 22x22 texture 28x28 anchor center layer 3 id c11_2 run TdCellClick(player, 11, 2) keep open
		image "td:textures/gui/tile_build.png" at 43 -41 size 22x22 texture 28x28 anchor center layer 3 id c12_2 run TdCellClick(player, 12, 2) keep open
		image "td:textures/gui/tile_build.png" at 87 -41 size 22x22 texture 28x28 anchor center layer 3 id c14_2 run TdCellClick(player, 14, 2) keep open
		image "td:textures/gui/tile_build.png" at 109 -41 size 22x22 texture 28x28 anchor center layer 3 id c15_2 run TdCellClick(player, 15, 2) keep open
		image "td:textures/gui/tile_build.png" at -221 -19 size 22x22 texture 28x28 anchor center layer 3 id c0_3 run TdCellClick(player, 0, 3) keep open
		image "td:textures/gui/tile_build.png" at -199 -19 size 22x22 texture 28x28 anchor center layer 3 id c1_3 run TdCellClick(player, 1, 3) keep open
		image "td:textures/gui/tile_build.png" at 87 -19 size 22x22 texture 28x28 anchor center layer 3 id c14_3 run TdCellClick(player, 14, 3) keep open
		image "td:textures/gui/tile_build.png" at 109 -19 size 22x22 texture 28x28 anchor center layer 3 id c15_3 run TdCellClick(player, 15, 3) keep open
		image "td:textures/gui/tile_build.png" at -221 3 size 22x22 texture 28x28 anchor center layer 3 id c0_4 run TdCellClick(player, 0, 4) keep open
		image "td:textures/gui/tile_build.png" at -199 3 size 22x22 texture 28x28 anchor center layer 3 id c1_4 run TdCellClick(player, 1, 4) keep open
		image "td:textures/gui/tile_build.png" at -155 3 size 22x22 texture 28x28 anchor center layer 3 id c3_4 run TdCellClick(player, 3, 4) keep open
		image "td:textures/gui/tile_build.png" at -133 3 size 22x22 texture 28x28 anchor center layer 3 id c4_4 run TdCellClick(player, 4, 4) keep open
		image "td:textures/gui/tile_build.png" at -111 3 size 22x22 texture 28x28 anchor center layer 3 id c5_4 run TdCellClick(player, 5, 4) keep open
		image "td:textures/gui/tile_build.png" at -89 3 size 22x22 texture 28x28 anchor center layer 3 id c6_4 run TdCellClick(player, 6, 4) keep open
		image "td:textures/gui/tile_build.png" at -67 3 size 22x22 texture 28x28 anchor center layer 3 id c7_4 run TdCellClick(player, 7, 4) keep open
		image "td:textures/gui/tile_build.png" at -45 3 size 22x22 texture 28x28 anchor center layer 3 id c8_4 run TdCellClick(player, 8, 4) keep open
		image "td:textures/gui/tile_build.png" at -23 3 size 22x22 texture 28x28 anchor center layer 3 id c9_4 run TdCellClick(player, 9, 4) keep open
		image "td:textures/gui/tile_build.png" at -1 3 size 22x22 texture 28x28 anchor center layer 3 id c10_4 run TdCellClick(player, 10, 4) keep open
		image "td:textures/gui/tile_build.png" at 21 3 size 22x22 texture 28x28 anchor center layer 3 id c11_4 run TdCellClick(player, 11, 4) keep open
		image "td:textures/gui/tile_build.png" at 43 3 size 22x22 texture 28x28 anchor center layer 3 id c12_4 run TdCellClick(player, 12, 4) keep open
		image "td:textures/gui/tile_build.png" at 65 3 size 22x22 texture 28x28 anchor center layer 3 id c13_4 run TdCellClick(player, 13, 4) keep open
		image "td:textures/gui/tile_build.png" at 87 3 size 22x22 texture 28x28 anchor center layer 3 id c14_4 run TdCellClick(player, 14, 4) keep open
		image "td:textures/gui/tile_build.png" at 109 3 size 22x22 texture 28x28 anchor center layer 3 id c15_4 run TdCellClick(player, 15, 4) keep open
		image "td:textures/gui/tile_build.png" at -221 25 size 22x22 texture 28x28 anchor center layer 3 id c0_5 run TdCellClick(player, 0, 5) keep open
		image "td:textures/gui/tile_build.png" at -199 25 size 22x22 texture 28x28 anchor center layer 3 id c1_5 run TdCellClick(player, 1, 5) keep open
		image "td:textures/gui/tile_build.png" at -155 25 size 22x22 texture 28x28 anchor center layer 3 id c3_5 run TdCellClick(player, 3, 5) keep open
		image "td:textures/gui/tile_build.png" at -133 25 size 22x22 texture 28x28 anchor center layer 3 id c4_5 run TdCellClick(player, 4, 5) keep open
		image "td:textures/gui/tile_build.png" at -111 25 size 22x22 texture 28x28 anchor center layer 3 id c5_5 run TdCellClick(player, 5, 5) keep open
		image "td:textures/gui/tile_build.png" at -89 25 size 22x22 texture 28x28 anchor center layer 3 id c6_5 run TdCellClick(player, 6, 5) keep open
		image "td:textures/gui/tile_build.png" at -67 25 size 22x22 texture 28x28 anchor center layer 3 id c7_5 run TdCellClick(player, 7, 5) keep open
		image "td:textures/gui/tile_build.png" at -45 25 size 22x22 texture 28x28 anchor center layer 3 id c8_5 run TdCellClick(player, 8, 5) keep open
		image "td:textures/gui/tile_build.png" at -23 25 size 22x22 texture 28x28 anchor center layer 3 id c9_5 run TdCellClick(player, 9, 5) keep open
		image "td:textures/gui/tile_build.png" at -1 25 size 22x22 texture 28x28 anchor center layer 3 id c10_5 run TdCellClick(player, 10, 5) keep open
		image "td:textures/gui/tile_build.png" at 21 25 size 22x22 texture 28x28 anchor center layer 3 id c11_5 run TdCellClick(player, 11, 5) keep open
		image "td:textures/gui/tile_build.png" at 43 25 size 22x22 texture 28x28 anchor center layer 3 id c12_5 run TdCellClick(player, 12, 5) keep open
		image "td:textures/gui/tile_build.png" at 65 25 size 22x22 texture 28x28 anchor center layer 3 id c13_5 run TdCellClick(player, 13, 5) keep open
		image "td:textures/gui/tile_build.png" at 87 25 size 22x22 texture 28x28 anchor center layer 3 id c14_5 run TdCellClick(player, 14, 5) keep open
		image "td:textures/gui/tile_build.png" at 109 25 size 22x22 texture 28x28 anchor center layer 3 id c15_5 run TdCellClick(player, 15, 5) keep open
		image "td:textures/gui/tile_build.png" at -221 47 size 22x22 texture 28x28 anchor center layer 3 id c0_6 run TdCellClick(player, 0, 6) keep open
		image "td:textures/gui/tile_build.png" at -199 47 size 22x22 texture 28x28 anchor center layer 3 id c1_6 run TdCellClick(player, 1, 6) keep open
		image "td:textures/gui/tile_build.png" at -221 69 size 22x22 texture 28x28 anchor center layer 3 id c0_7 run TdCellClick(player, 0, 7) keep open
		image "td:textures/gui/tile_build.png" at -199 69 size 22x22 texture 28x28 anchor center layer 3 id c1_7 run TdCellClick(player, 1, 7) keep open
		image "td:textures/gui/tile_build.png" at -177 69 size 22x22 texture 28x28 anchor center layer 3 id c2_7 run TdCellClick(player, 2, 7) keep open
		image "td:textures/gui/tile_build.png" at -155 69 size 22x22 texture 28x28 anchor center layer 3 id c3_7 run TdCellClick(player, 3, 7) keep open
		image "td:textures/gui/tile_build.png" at -133 69 size 22x22 texture 28x28 anchor center layer 3 id c4_7 run TdCellClick(player, 4, 7) keep open
		image "td:textures/gui/tile_build.png" at -111 69 size 22x22 texture 28x28 anchor center layer 3 id c5_7 run TdCellClick(player, 5, 7) keep open
		image "td:textures/gui/tile_build.png" at -89 69 size 22x22 texture 28x28 anchor center layer 3 id c6_7 run TdCellClick(player, 6, 7) keep open
		image "td:textures/gui/tile_build.png" at -67 69 size 22x22 texture 28x28 anchor center layer 3 id c7_7 run TdCellClick(player, 7, 7) keep open
		image "td:textures/gui/tile_build.png" at -45 69 size 22x22 texture 28x28 anchor center layer 3 id c8_7 run TdCellClick(player, 8, 7) keep open
		image "td:textures/gui/tile_build.png" at -23 69 size 22x22 texture 28x28 anchor center layer 3 id c9_7 run TdCellClick(player, 9, 7) keep open
		image "td:textures/gui/tile_build.png" at -1 69 size 22x22 texture 28x28 anchor center layer 3 id c10_7 run TdCellClick(player, 10, 7) keep open
		image "td:textures/gui/tile_build.png" at 21 69 size 22x22 texture 28x28 anchor center layer 3 id c11_7 run TdCellClick(player, 11, 7) keep open
		image "td:textures/gui/tile_build.png" at 43 69 size 22x22 texture 28x28 anchor center layer 3 id c12_7 run TdCellClick(player, 12, 7) keep open
		image "td:textures/gui/tile_build.png" at 65 69 size 22x22 texture 28x28 anchor center layer 3 id c13_7 run TdCellClick(player, 13, 7) keep open
		image "td:textures/gui/tile_build.png" at 87 69 size 22x22 texture 28x28 anchor center layer 3 id c14_7 run TdCellClick(player, 14, 7) keep open
		image "td:textures/gui/tile_build.png" at 109 69 size 22x22 texture 28x28 anchor center layer 3 id c15_7 run TdCellClick(player, 15, 7) keep open

		# Пул звёзд уровней и эволюций (до 32 башен)
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st1 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st2 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st3 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st4 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st5 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st6 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st7 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st8 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st9 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st10 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st11 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st12 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st13 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st14 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st15 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st16 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st17 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st18 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st19 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st20 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st21 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st22 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st23 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st24 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st25 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st26 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st27 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st28 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st29 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st30 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st31 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st32 hidden

		# 11 слотов обычных врагов + 1 слот Босса + тонкие HP-бары (64x64 HD + 60 FPS tween)
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e1 hidden
		bar "ehp1" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e2 hidden
		bar "ehp2" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e3 hidden
		bar "ehp3" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e4 hidden
		bar "ehp4" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e5 hidden
		bar "ehp5" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e6 hidden
		bar "ehp6" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e7 hidden
		bar "ehp7" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e8 hidden
		bar "ehp8" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e9 hidden
		bar "ehp9" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e10 hidden
		bar "ehp10" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e11 hidden
		bar "ehp11" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_boss.png" at -221 -63 size 28x28 texture 64x64 anchor center layer 8 id e12 hidden
		bar "ehp12" value 100 max 100 at -221 -79 size 26x4 anchor center color #FFEF4444 background #CC1A0A0A layer 9 hidden

		# 6 летающих HD прожектайлов (60 FPS tween)
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr1 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr2 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr3 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr4 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr5 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr6 hidden

		# Компактная строка статуса
		box at 0 92 size 468x13 anchor center color #E60B1220 border #3338BDF8 radius 3 layer 11
		text "&e[1] Баллиста &7— наведите на башню для радиуса и статов, ЛКМ по клетке — строить" at 0 92 anchor center shadow layer 12 id status_txt
		text "&c&lБАЗА ПАЛА!" at -56 -18 anchor center shadow scale 1.4 layer 20 id end_title hidden
		text "&fНажми &eR &f(рестарт) или &eM &f(меню)" at -56 2 anchor center shadow layer 20 id end_sub hidden

		# Нижняя панель: 9 HD башен (включая Маяк-Баффер #9) + управление и тумблер Курсора
		box at 0 121 size 468x40 anchor center color #EE090E1A gradient #EE111827 border #FF1E293B radius 6 shadow blur layer 11
		box at -218 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -186 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -154 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -122 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -90 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -58 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -26 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at 6 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at 38 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -218 106 size 26x2 anchor center color #FFFACC15 radius 1 id sel_bar hidden layer 13
		image "td:textures/gui/tower_archer.png" at -218 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e&l[1] Эльфийская Баллиста &650з%nl%&8▪ &7Урон: &f15 &8| &7КД: &a0.4с &8| &7Дальн: &b78%nl%&6✦ 5★ Шквал: &fстреляет сразу по 2 целям"
		text "&e[1]50" at -218 131 anchor center shadow scale 0.85 layer 12 id lbl1
		image "td:textures/gui/tower_cannon.png" at -186 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6&l[2] Мортира «Вулкан» &695з%nl%&8▪ &7Урон: &f36 &c(Сплеш) &8| &7Дальн: &b74%nl%&6✦ 5★ Напалм: &fмега-сплеш + поджог врагов"
		text "&7[2]95" at -186 131 anchor center shadow scale 0.85 layer 12 id lbl2
		image "td:textures/gui/tower_frost.png" at -154 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b&l[3] Крио-Обелиск &685з%nl%&8▪ &7Аура: &f8 ур. &b(40% замедл. всех в радиусе 76)%nl%&6✦ 5★ Стужа: &f50% замедл. + 12% заморозка"
		text "&7[3]85" at -154 131 anchor center shadow scale 0.85 layer 12 id lbl3
		image "td:textures/gui/tower_mage.png" at -122 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d&l[4] Аркан-Шпиль &6130з%nl%&8▪ &7Урон: &f32 &8| &7Дальн: &b106%nl%&6✦ 5★ Убийца Боссов: &fx2 Боссам + 4% макс.HP"
		text "&7[4]130" at -122 131 anchor center shadow scale 0.85 layer 12 id lbl4
		image "td:textures/gui/tower_tesla.png" at -90 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico5 run TdSelectTool(player, 5) keep open tooltip "&b&l[5] Плазмо-Тесла &6115з%nl%&8▪ &7Урон: &f20 &b(Цепь на 3 цели) &8| &7Дальн: &b86%nl%&6✦ 5★ Шторм: &f5 целей + 90% урон + микро-стан"
		text "&7[5]115" at -90 131 anchor center shadow scale 0.85 layer 12 id lbl5
		image "td:textures/gui/tower_sniper.png" at -58 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico6 run TdSelectTool(player, 6) keep open tooltip "&c&l[6] Рейлган «Сокол» &6150з%nl%&8▪ &7Урон: &f56 &c(Крит 25%) &8| &7Дальн: &b172%nl%&6✦ 5★ Казнь: &fкрит 45% x2.5 + добивание <20% HP"
		text "&7[6]150" at -58 131 anchor center shadow scale 0.85 layer 12 id lbl6
		image "td:textures/gui/tower_poison.png" at -26 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico7 run TdSelectTool(player, 7) keep open tooltip "&a&l[7] Чумной Реактор &6105з%nl%&8▪ &7Урон: &f12 + &aКислота (DOT) &8| &7Дальн: &b80%nl%&6✦ 5★ Чума: &fзаражает всех врагов рядом"
		text "&7[7]105" at -26 131 anchor center shadow scale 0.85 layer 12 id lbl7
		image "td:textures/gui/tower_farm.png" at 6 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico8 run TdSelectTool(player, 8) keep open tooltip "&6&l[8] Монетный Двор &6140з%nl%&8▪ &7Доход: &6+28/+50/+80/+120з &7за волну%nl%&6✦ 5★ Золотой Дворец: &f+180з и +1 HP базе"
		text "&7[8]140" at 6 131 anchor center shadow scale 0.85 layer 12 id lbl8
		image "td:textures/gui/tower_buffer.png" at 38 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico9 run TdSelectTool(player, 9) keep open tooltip "&d&l[9] Боевой Маяк (Баффер) &6125з%nl%&8▪ &7Бафф-аура: &d+18..42% урона &7башням в радиусе 68%nl%&6✦ 5★ Знамя Империи: &f+55% урон, +20% скоростр. и +25з"
		text "&7[9]125" at 38 131 anchor center shadow scale 0.85 layer 12 id lbl9
		button "&aСтарт/x2" at 94 112 size 56x15 anchor center run TdWaveOrSpeed(player) keep open tooltip "&aСтарт волны / Скорость x1↔x2 &8[Space]"
		toggle "Авто" id autowave at 152 112 size 52x15 anchor center value false color #22C55E tooltip "&aАвто-старт волн"
		toggle "Курсор" id cursortog at 208 112 size 52x15 anchor center value true color #38BDF8 tooltip "&bИконка выбранной башни у курсора &8[C]"
		button "&cПродать" at 94 129 size 56x15 anchor center run TdToggleSell(player) keep open tooltip "&cПродать башню за 70% &8[X]"
		button "Заново" at 152 129 size 52x15 anchor center run TdRestartCurrent(player) keep open tooltip "&eРестарт карты &8[R]"
		button "Меню" at 208 129 size 52x15 anchor center run TdOpenMenu(player) keep open tooltip "&eВыбор карты и кооп &8[M]"

		# Привязанная к курсору иконка текущей башни (универсальное свойство follow cursor)
		image "td:textures/gui/tower_archer.png" at 0 0 size 15x15 texture 64x64 anchor center follow cursor layer 24 id cursor_icon hidden

function TdOpenMap2(p: player):
	set {-g} to {-td-host::%p%}
	if {-g} is not set:
		set {-g} to %p%
	open screen "Tower Defense" to p:
		# Невидимый фон за экраном — клик в пустое место или за экран отвязывает выбранную башню от курсора
		box at 0 0 size 2000x2000 anchor center color #00000000 layer -1 run TdClearTool(player) keep open
		# Верхний HUD (скруглённая стеклянная панель + пилюли показателей)
		box at 0 -114 size 468x22 anchor center color #E6090E1A gradient #E6111C33 border #FF38BDF8 border_width 1 radius 6 shadow blur layer 11
		box at -202 -114 size 54x15 anchor center color #CC162032 border #4438BDF8 radius 4 layer 11
		box at -134 -114 size 62x15 anchor center color #CC162032 border #44FACC15 radius 4 layer 11
		box at -68 -114 size 48x15 anchor center color #CC162032 border #44EF4444 radius 4 layer 11
		box at -2 -114 size 66x15 anchor center color #CC162032 border #4422C55E radius 4 layer 11
		box at 132 -114 size 190x15 anchor center color #CC162032 border #44C084FC radius 4 layer 11
		image "td:textures/gui/icon_wave.png" at -220 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&eВолна"
		text "&f0/20" at -195 -114 anchor center shadow layer 12 id hud_wave
		image "td:textures/gui/icon_coin.png" at -154 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&6Личное золото"
		text "&6120" at -126 -114 anchor center shadow layer 12 id hud_gold
		image "td:textures/gui/icon_heart.png" at -82 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&cЗдоровье базы"
		text "&c10" at -60 -114 anchor center shadow layer 12 id hud_hp
		image "td:textures/gui/icon_trophy.png" at -24 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&aСчёт"
		text "&a0" at 6 -114 anchor center shadow layer 12 id hud_score
		text "&7Соло &7[x1]" at 132 -114 anchor center shadow layer 12 id hud_team

		# Тактическая рамка + HD карта 352x176 (текстура 896x448)
		box at -56 -8 size 358x182 anchor center color #AA090E1A border #AA38BDF8 border_width 2 radius 5 shadow layer 0
		image "td:textures/gui/map_volcano.png" at -56 -8 size 352x176 texture 896x448 anchor center layer 1 id map run TdClearTool(player) keep open
		bar "boss_bar" value 100 max 100 at -56 -88 size 210x8 anchor center color #FFEF4444 background #CC1A0A0A text "&c&l☠ БОСС: %value%/%max%" shadow layer 14 hidden

		# Правая панель — Журнал событий (list без скроллбара)
		box at 179 -8 size 110x182 anchor center color #E6090E1A gradient #E6111827 border #6638BDF8 border_width 1 radius 5 shadow blur layer 11
		list from {-td-log::%{-g}%::*} vertical no scrollbar header "&b&l⚡ СОБЫТИЯ" at 179 -8 size 104x174 anchor center layer 12 id ev_log:
			row "%loop-value%"

		# Тактические радар-круги радиуса башен (авто-показ при наведении и клике!)
		image "td:textures/gui/range_circle.png" at 0 0 size 154x154 texture 256x256 anchor center layer 2 id rng_s hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 208x208 texture 256x256 anchor center layer 2 id rng_m hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 310x310 texture 256x256 anchor center layer 2 id rng_l hidden
		image "td:textures/gui/range_buff.png" at 0 0 size 150x150 texture 256x256 anchor center layer 2 id rng_b hidden

		# Сетка постройки 16x8 (клетка 22x22; пустые клетки без лишних тултипов)
		image "td:textures/gui/tile_build.png" at -111 -85 size 22x22 texture 28x28 anchor center layer 3 id c5_0 run TdCellClick(player, 5, 0) keep open
		image "td:textures/gui/tile_build.png" at -89 -85 size 22x22 texture 28x28 anchor center layer 3 id c6_0 run TdCellClick(player, 6, 0) keep open
		image "td:textures/gui/tile_build.png" at -67 -85 size 22x22 texture 28x28 anchor center layer 3 id c7_0 run TdCellClick(player, 7, 0) keep open
		image "td:textures/gui/tile_build.png" at -45 -85 size 22x22 texture 28x28 anchor center layer 3 id c8_0 run TdCellClick(player, 8, 0) keep open
		image "td:textures/gui/tile_build.png" at -23 -85 size 22x22 texture 28x28 anchor center layer 3 id c9_0 run TdCellClick(player, 9, 0) keep open
		image "td:textures/gui/tile_build.png" at -1 -85 size 22x22 texture 28x28 anchor center layer 3 id c10_0 run TdCellClick(player, 10, 0) keep open
		image "td:textures/gui/tile_build.png" at 21 -85 size 22x22 texture 28x28 anchor center layer 3 id c11_0 run TdCellClick(player, 11, 0) keep open
		image "td:textures/gui/tile_build.png" at 43 -85 size 22x22 texture 28x28 anchor center layer 3 id c12_0 run TdCellClick(player, 12, 0) keep open
		image "td:textures/gui/tile_build.png" at 65 -85 size 22x22 texture 28x28 anchor center layer 3 id c13_0 run TdCellClick(player, 13, 0) keep open
		image "td:textures/gui/tile_build.png" at 87 -85 size 22x22 texture 28x28 anchor center layer 3 id c14_0 run TdCellClick(player, 14, 0) keep open
		image "td:textures/gui/tile_build.png" at 109 -85 size 22x22 texture 28x28 anchor center layer 3 id c15_0 run TdCellClick(player, 15, 0) keep open
		image "td:textures/gui/tile_build.png" at -221 -63 size 22x22 texture 28x28 anchor center layer 3 id c0_1 run TdCellClick(player, 0, 1) keep open
		image "td:textures/gui/tile_build.png" at -199 -63 size 22x22 texture 28x28 anchor center layer 3 id c1_1 run TdCellClick(player, 1, 1) keep open
		image "td:textures/gui/tile_build.png" at -177 -63 size 22x22 texture 28x28 anchor center layer 3 id c2_1 run TdCellClick(player, 2, 1) keep open
		image "td:textures/gui/tile_build.png" at -155 -63 size 22x22 texture 28x28 anchor center layer 3 id c3_1 run TdCellClick(player, 3, 1) keep open
		image "td:textures/gui/tile_build.png" at -111 -63 size 22x22 texture 28x28 anchor center layer 3 id c5_1 run TdCellClick(player, 5, 1) keep open
		image "td:textures/gui/tile_build.png" at -89 -63 size 22x22 texture 28x28 anchor center layer 3 id c6_1 run TdCellClick(player, 6, 1) keep open
		image "td:textures/gui/tile_build.png" at -67 -63 size 22x22 texture 28x28 anchor center layer 3 id c7_1 run TdCellClick(player, 7, 1) keep open
		image "td:textures/gui/tile_build.png" at -45 -63 size 22x22 texture 28x28 anchor center layer 3 id c8_1 run TdCellClick(player, 8, 1) keep open
		image "td:textures/gui/tile_build.png" at 87 -63 size 22x22 texture 28x28 anchor center layer 3 id c14_1 run TdCellClick(player, 14, 1) keep open
		image "td:textures/gui/tile_build.png" at 109 -63 size 22x22 texture 28x28 anchor center layer 3 id c15_1 run TdCellClick(player, 15, 1) keep open
		image "td:textures/gui/tile_build.png" at -221 -41 size 22x22 texture 28x28 anchor center layer 3 id c0_2 run TdCellClick(player, 0, 2) keep open
		image "td:textures/gui/tile_build.png" at -199 -41 size 22x22 texture 28x28 anchor center layer 3 id c1_2 run TdCellClick(player, 1, 2) keep open
		image "td:textures/gui/tile_build.png" at -177 -41 size 22x22 texture 28x28 anchor center layer 3 id c2_2 run TdCellClick(player, 2, 2) keep open
		image "td:textures/gui/tile_build.png" at -155 -41 size 22x22 texture 28x28 anchor center layer 3 id c3_2 run TdCellClick(player, 3, 2) keep open
		image "td:textures/gui/tile_build.png" at -111 -41 size 22x22 texture 28x28 anchor center layer 3 id c5_2 run TdCellClick(player, 5, 2) keep open
		image "td:textures/gui/tile_build.png" at -89 -41 size 22x22 texture 28x28 anchor center layer 3 id c6_2 run TdCellClick(player, 6, 2) keep open
		image "td:textures/gui/tile_build.png" at -67 -41 size 22x22 texture 28x28 anchor center layer 3 id c7_2 run TdCellClick(player, 7, 2) keep open
		image "td:textures/gui/tile_build.png" at -45 -41 size 22x22 texture 28x28 anchor center layer 3 id c8_2 run TdCellClick(player, 8, 2) keep open
		image "td:textures/gui/tile_build.png" at -1 -41 size 22x22 texture 28x28 anchor center layer 3 id c10_2 run TdCellClick(player, 10, 2) keep open
		image "td:textures/gui/tile_build.png" at 21 -41 size 22x22 texture 28x28 anchor center layer 3 id c11_2 run TdCellClick(player, 11, 2) keep open
		image "td:textures/gui/tile_build.png" at 43 -41 size 22x22 texture 28x28 anchor center layer 3 id c12_2 run TdCellClick(player, 12, 2) keep open
		image "td:textures/gui/tile_build.png" at 87 -41 size 22x22 texture 28x28 anchor center layer 3 id c14_2 run TdCellClick(player, 14, 2) keep open
		image "td:textures/gui/tile_build.png" at 109 -41 size 22x22 texture 28x28 anchor center layer 3 id c15_2 run TdCellClick(player, 15, 2) keep open
		image "td:textures/gui/tile_build.png" at -221 -19 size 22x22 texture 28x28 anchor center layer 3 id c0_3 run TdCellClick(player, 0, 3) keep open
		image "td:textures/gui/tile_build.png" at -199 -19 size 22x22 texture 28x28 anchor center layer 3 id c1_3 run TdCellClick(player, 1, 3) keep open
		image "td:textures/gui/tile_build.png" at -177 -19 size 22x22 texture 28x28 anchor center layer 3 id c2_3 run TdCellClick(player, 2, 3) keep open
		image "td:textures/gui/tile_build.png" at -155 -19 size 22x22 texture 28x28 anchor center layer 3 id c3_3 run TdCellClick(player, 3, 3) keep open
		image "td:textures/gui/tile_build.png" at -111 -19 size 22x22 texture 28x28 anchor center layer 3 id c5_3 run TdCellClick(player, 5, 3) keep open
		image "td:textures/gui/tile_build.png" at -89 -19 size 22x22 texture 28x28 anchor center layer 3 id c6_3 run TdCellClick(player, 6, 3) keep open
		image "td:textures/gui/tile_build.png" at -67 -19 size 22x22 texture 28x28 anchor center layer 3 id c7_3 run TdCellClick(player, 7, 3) keep open
		image "td:textures/gui/tile_build.png" at -45 -19 size 22x22 texture 28x28 anchor center layer 3 id c8_3 run TdCellClick(player, 8, 3) keep open
		image "td:textures/gui/tile_build.png" at -1 -19 size 22x22 texture 28x28 anchor center layer 3 id c10_3 run TdCellClick(player, 10, 3) keep open
		image "td:textures/gui/tile_build.png" at 21 -19 size 22x22 texture 28x28 anchor center layer 3 id c11_3 run TdCellClick(player, 11, 3) keep open
		image "td:textures/gui/tile_build.png" at 43 -19 size 22x22 texture 28x28 anchor center layer 3 id c12_3 run TdCellClick(player, 12, 3) keep open
		image "td:textures/gui/tile_build.png" at 87 -19 size 22x22 texture 28x28 anchor center layer 3 id c14_3 run TdCellClick(player, 14, 3) keep open
		image "td:textures/gui/tile_build.png" at 109 -19 size 22x22 texture 28x28 anchor center layer 3 id c15_3 run TdCellClick(player, 15, 3) keep open
		image "td:textures/gui/tile_build.png" at -221 3 size 22x22 texture 28x28 anchor center layer 3 id c0_4 run TdCellClick(player, 0, 4) keep open
		image "td:textures/gui/tile_build.png" at -199 3 size 22x22 texture 28x28 anchor center layer 3 id c1_4 run TdCellClick(player, 1, 4) keep open
		image "td:textures/gui/tile_build.png" at -177 3 size 22x22 texture 28x28 anchor center layer 3 id c2_4 run TdCellClick(player, 2, 4) keep open
		image "td:textures/gui/tile_build.png" at -155 3 size 22x22 texture 28x28 anchor center layer 3 id c3_4 run TdCellClick(player, 3, 4) keep open
		image "td:textures/gui/tile_build.png" at -111 3 size 22x22 texture 28x28 anchor center layer 3 id c5_4 run TdCellClick(player, 5, 4) keep open
		image "td:textures/gui/tile_build.png" at -89 3 size 22x22 texture 28x28 anchor center layer 3 id c6_4 run TdCellClick(player, 6, 4) keep open
		image "td:textures/gui/tile_build.png" at -67 3 size 22x22 texture 28x28 anchor center layer 3 id c7_4 run TdCellClick(player, 7, 4) keep open
		image "td:textures/gui/tile_build.png" at -45 3 size 22x22 texture 28x28 anchor center layer 3 id c8_4 run TdCellClick(player, 8, 4) keep open
		image "td:textures/gui/tile_build.png" at -1 3 size 22x22 texture 28x28 anchor center layer 3 id c10_4 run TdCellClick(player, 10, 4) keep open
		image "td:textures/gui/tile_build.png" at 21 3 size 22x22 texture 28x28 anchor center layer 3 id c11_4 run TdCellClick(player, 11, 4) keep open
		image "td:textures/gui/tile_build.png" at 43 3 size 22x22 texture 28x28 anchor center layer 3 id c12_4 run TdCellClick(player, 12, 4) keep open
		image "td:textures/gui/tile_build.png" at 87 3 size 22x22 texture 28x28 anchor center layer 3 id c14_4 run TdCellClick(player, 14, 4) keep open
		image "td:textures/gui/tile_build.png" at 109 3 size 22x22 texture 28x28 anchor center layer 3 id c15_4 run TdCellClick(player, 15, 4) keep open
		image "td:textures/gui/tile_build.png" at -221 25 size 22x22 texture 28x28 anchor center layer 3 id c0_5 run TdCellClick(player, 0, 5) keep open
		image "td:textures/gui/tile_build.png" at -199 25 size 22x22 texture 28x28 anchor center layer 3 id c1_5 run TdCellClick(player, 1, 5) keep open
		image "td:textures/gui/tile_build.png" at -177 25 size 22x22 texture 28x28 anchor center layer 3 id c2_5 run TdCellClick(player, 2, 5) keep open
		image "td:textures/gui/tile_build.png" at -155 25 size 22x22 texture 28x28 anchor center layer 3 id c3_5 run TdCellClick(player, 3, 5) keep open
		image "td:textures/gui/tile_build.png" at -111 25 size 22x22 texture 28x28 anchor center layer 3 id c5_5 run TdCellClick(player, 5, 5) keep open
		image "td:textures/gui/tile_build.png" at -89 25 size 22x22 texture 28x28 anchor center layer 3 id c6_5 run TdCellClick(player, 6, 5) keep open
		image "td:textures/gui/tile_build.png" at -67 25 size 22x22 texture 28x28 anchor center layer 3 id c7_5 run TdCellClick(player, 7, 5) keep open
		image "td:textures/gui/tile_build.png" at -45 25 size 22x22 texture 28x28 anchor center layer 3 id c8_5 run TdCellClick(player, 8, 5) keep open
		image "td:textures/gui/tile_build.png" at -1 25 size 22x22 texture 28x28 anchor center layer 3 id c10_5 run TdCellClick(player, 10, 5) keep open
		image "td:textures/gui/tile_build.png" at 21 25 size 22x22 texture 28x28 anchor center layer 3 id c11_5 run TdCellClick(player, 11, 5) keep open
		image "td:textures/gui/tile_build.png" at 43 25 size 22x22 texture 28x28 anchor center layer 3 id c12_5 run TdCellClick(player, 12, 5) keep open
		image "td:textures/gui/tile_build.png" at -221 47 size 22x22 texture 28x28 anchor center layer 3 id c0_6 run TdCellClick(player, 0, 6) keep open
		image "td:textures/gui/tile_build.png" at -199 47 size 22x22 texture 28x28 anchor center layer 3 id c1_6 run TdCellClick(player, 1, 6) keep open
		image "td:textures/gui/tile_build.png" at -177 47 size 22x22 texture 28x28 anchor center layer 3 id c2_6 run TdCellClick(player, 2, 6) keep open
		image "td:textures/gui/tile_build.png" at -155 47 size 22x22 texture 28x28 anchor center layer 3 id c3_6 run TdCellClick(player, 3, 6) keep open
		image "td:textures/gui/tile_build.png" at -1 47 size 22x22 texture 28x28 anchor center layer 3 id c10_6 run TdCellClick(player, 10, 6) keep open
		image "td:textures/gui/tile_build.png" at 21 47 size 22x22 texture 28x28 anchor center layer 3 id c11_6 run TdCellClick(player, 11, 6) keep open
		image "td:textures/gui/tile_build.png" at 43 47 size 22x22 texture 28x28 anchor center layer 3 id c12_6 run TdCellClick(player, 12, 6) keep open
		image "td:textures/gui/tile_build.png" at 65 47 size 22x22 texture 28x28 anchor center layer 3 id c13_6 run TdCellClick(player, 13, 6) keep open
		image "td:textures/gui/tile_build.png" at 87 47 size 22x22 texture 28x28 anchor center layer 3 id c14_6 run TdCellClick(player, 14, 6) keep open
		image "td:textures/gui/tile_build.png" at 109 47 size 22x22 texture 28x28 anchor center layer 3 id c15_6 run TdCellClick(player, 15, 6) keep open
		image "td:textures/gui/tile_build.png" at -221 69 size 22x22 texture 28x28 anchor center layer 3 id c0_7 run TdCellClick(player, 0, 7) keep open
		image "td:textures/gui/tile_build.png" at -199 69 size 22x22 texture 28x28 anchor center layer 3 id c1_7 run TdCellClick(player, 1, 7) keep open
		image "td:textures/gui/tile_build.png" at -177 69 size 22x22 texture 28x28 anchor center layer 3 id c2_7 run TdCellClick(player, 2, 7) keep open
		image "td:textures/gui/tile_build.png" at -155 69 size 22x22 texture 28x28 anchor center layer 3 id c3_7 run TdCellClick(player, 3, 7) keep open
		image "td:textures/gui/tile_build.png" at -133 69 size 22x22 texture 28x28 anchor center layer 3 id c4_7 run TdCellClick(player, 4, 7) keep open
		image "td:textures/gui/tile_build.png" at -111 69 size 22x22 texture 28x28 anchor center layer 3 id c5_7 run TdCellClick(player, 5, 7) keep open
		image "td:textures/gui/tile_build.png" at -89 69 size 22x22 texture 28x28 anchor center layer 3 id c6_7 run TdCellClick(player, 6, 7) keep open
		image "td:textures/gui/tile_build.png" at -67 69 size 22x22 texture 28x28 anchor center layer 3 id c7_7 run TdCellClick(player, 7, 7) keep open
		image "td:textures/gui/tile_build.png" at -45 69 size 22x22 texture 28x28 anchor center layer 3 id c8_7 run TdCellClick(player, 8, 7) keep open
		image "td:textures/gui/tile_build.png" at -23 69 size 22x22 texture 28x28 anchor center layer 3 id c9_7 run TdCellClick(player, 9, 7) keep open
		image "td:textures/gui/tile_build.png" at -1 69 size 22x22 texture 28x28 anchor center layer 3 id c10_7 run TdCellClick(player, 10, 7) keep open
		image "td:textures/gui/tile_build.png" at 21 69 size 22x22 texture 28x28 anchor center layer 3 id c11_7 run TdCellClick(player, 11, 7) keep open
		image "td:textures/gui/tile_build.png" at 43 69 size 22x22 texture 28x28 anchor center layer 3 id c12_7 run TdCellClick(player, 12, 7) keep open
		image "td:textures/gui/tile_build.png" at 65 69 size 22x22 texture 28x28 anchor center layer 3 id c13_7 run TdCellClick(player, 13, 7) keep open
		image "td:textures/gui/tile_build.png" at 87 69 size 22x22 texture 28x28 anchor center layer 3 id c14_7 run TdCellClick(player, 14, 7) keep open
		image "td:textures/gui/tile_build.png" at 109 69 size 22x22 texture 28x28 anchor center layer 3 id c15_7 run TdCellClick(player, 15, 7) keep open

		# Пул звёзд уровней и эволюций (до 32 башен)
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st1 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st2 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st3 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st4 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st5 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st6 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st7 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st8 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st9 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st10 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st11 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st12 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st13 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st14 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st15 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st16 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st17 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st18 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st19 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st20 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st21 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st22 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st23 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st24 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st25 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st26 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st27 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st28 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st29 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st30 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st31 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st32 hidden

		# 11 слотов обычных врагов + 1 слот Босса + тонкие HP-бары (64x64 HD + 60 FPS tween)
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e1 hidden
		bar "ehp1" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e2 hidden
		bar "ehp2" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e3 hidden
		bar "ehp3" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e4 hidden
		bar "ehp4" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e5 hidden
		bar "ehp5" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e6 hidden
		bar "ehp6" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e7 hidden
		bar "ehp7" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e8 hidden
		bar "ehp8" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e9 hidden
		bar "ehp9" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e10 hidden
		bar "ehp10" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e11 hidden
		bar "ehp11" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_boss.png" at -221 -63 size 28x28 texture 64x64 anchor center layer 8 id e12 hidden
		bar "ehp12" value 100 max 100 at -221 -79 size 26x4 anchor center color #FFEF4444 background #CC1A0A0A layer 9 hidden

		# 6 летающих HD прожектайлов (60 FPS tween)
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr1 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr2 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr3 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr4 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr5 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr6 hidden

		# Компактная строка статуса
		box at 0 92 size 468x13 anchor center color #E60B1220 border #3338BDF8 radius 3 layer 11
		text "&e[1] Баллиста &7— наведите на башню для радиуса и статов, ЛКМ по клетке — строить" at 0 92 anchor center shadow layer 12 id status_txt
		text "&c&lБАЗА ПАЛА!" at -56 -18 anchor center shadow scale 1.4 layer 20 id end_title hidden
		text "&fНажми &eR &f(рестарт) или &eM &f(меню)" at -56 2 anchor center shadow layer 20 id end_sub hidden

		# Нижняя панель: 9 HD башен (включая Маяк-Баффер #9) + управление и тумблер Курсора
		box at 0 121 size 468x40 anchor center color #EE090E1A gradient #EE111827 border #FF1E293B radius 6 shadow blur layer 11
		box at -218 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -186 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -154 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -122 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -90 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -58 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -26 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at 6 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at 38 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -218 106 size 26x2 anchor center color #FFFACC15 radius 1 id sel_bar hidden layer 13
		image "td:textures/gui/tower_archer.png" at -218 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e&l[1] Эльфийская Баллиста &650з%nl%&8▪ &7Урон: &f15 &8| &7КД: &a0.4с &8| &7Дальн: &b78%nl%&6✦ 5★ Шквал: &fстреляет сразу по 2 целям"
		text "&e[1]50" at -218 131 anchor center shadow scale 0.85 layer 12 id lbl1
		image "td:textures/gui/tower_cannon.png" at -186 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6&l[2] Мортира «Вулкан» &695з%nl%&8▪ &7Урон: &f36 &c(Сплеш) &8| &7Дальн: &b74%nl%&6✦ 5★ Напалм: &fмега-сплеш + поджог врагов"
		text "&7[2]95" at -186 131 anchor center shadow scale 0.85 layer 12 id lbl2
		image "td:textures/gui/tower_frost.png" at -154 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b&l[3] Крио-Обелиск &685з%nl%&8▪ &7Аура: &f8 ур. &b(40% замедл. всех в радиусе 76)%nl%&6✦ 5★ Стужа: &f50% замедл. + 12% заморозка"
		text "&7[3]85" at -154 131 anchor center shadow scale 0.85 layer 12 id lbl3
		image "td:textures/gui/tower_mage.png" at -122 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d&l[4] Аркан-Шпиль &6130з%nl%&8▪ &7Урон: &f32 &8| &7Дальн: &b106%nl%&6✦ 5★ Убийца Боссов: &fx2 Боссам + 4% макс.HP"
		text "&7[4]130" at -122 131 anchor center shadow scale 0.85 layer 12 id lbl4
		image "td:textures/gui/tower_tesla.png" at -90 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico5 run TdSelectTool(player, 5) keep open tooltip "&b&l[5] Плазмо-Тесла &6115з%nl%&8▪ &7Урон: &f20 &b(Цепь на 3 цели) &8| &7Дальн: &b86%nl%&6✦ 5★ Шторм: &f5 целей + 90% урон + микро-стан"
		text "&7[5]115" at -90 131 anchor center shadow scale 0.85 layer 12 id lbl5
		image "td:textures/gui/tower_sniper.png" at -58 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico6 run TdSelectTool(player, 6) keep open tooltip "&c&l[6] Рейлган «Сокол» &6150з%nl%&8▪ &7Урон: &f56 &c(Крит 25%) &8| &7Дальн: &b172%nl%&6✦ 5★ Казнь: &fкрит 45% x2.5 + добивание <20% HP"
		text "&7[6]150" at -58 131 anchor center shadow scale 0.85 layer 12 id lbl6
		image "td:textures/gui/tower_poison.png" at -26 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico7 run TdSelectTool(player, 7) keep open tooltip "&a&l[7] Чумной Реактор &6105з%nl%&8▪ &7Урон: &f12 + &aКислота (DOT) &8| &7Дальн: &b80%nl%&6✦ 5★ Чума: &fзаражает всех врагов рядом"
		text "&7[7]105" at -26 131 anchor center shadow scale 0.85 layer 12 id lbl7
		image "td:textures/gui/tower_farm.png" at 6 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico8 run TdSelectTool(player, 8) keep open tooltip "&6&l[8] Монетный Двор &6140з%nl%&8▪ &7Доход: &6+28/+50/+80/+120з &7за волну%nl%&6✦ 5★ Золотой Дворец: &f+180з и +1 HP базе"
		text "&7[8]140" at 6 131 anchor center shadow scale 0.85 layer 12 id lbl8
		image "td:textures/gui/tower_buffer.png" at 38 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico9 run TdSelectTool(player, 9) keep open tooltip "&d&l[9] Боевой Маяк (Баффер) &6125з%nl%&8▪ &7Бафф-аура: &d+18..42% урона &7башням в радиусе 68%nl%&6✦ 5★ Знамя Империи: &f+55% урон, +20% скоростр. и +25з"
		text "&7[9]125" at 38 131 anchor center shadow scale 0.85 layer 12 id lbl9
		button "&aСтарт/x2" at 94 112 size 56x15 anchor center run TdWaveOrSpeed(player) keep open tooltip "&aСтарт волны / Скорость x1↔x2 &8[Space]"
		toggle "Авто" id autowave at 152 112 size 52x15 anchor center value false color #22C55E tooltip "&aАвто-старт волн"
		toggle "Курсор" id cursortog at 208 112 size 52x15 anchor center value true color #38BDF8 tooltip "&bИконка выбранной башни у курсора &8[C]"
		button "&cПродать" at 94 129 size 56x15 anchor center run TdToggleSell(player) keep open tooltip "&cПродать башню за 70% &8[X]"
		button "Заново" at 152 129 size 52x15 anchor center run TdRestartCurrent(player) keep open tooltip "&eРестарт карты &8[R]"
		button "Меню" at 208 129 size 52x15 anchor center run TdOpenMenu(player) keep open tooltip "&eВыбор карты и кооп &8[M]"

		# Привязанная к курсору иконка текущей башни (универсальное свойство follow cursor)
		image "td:textures/gui/tower_archer.png" at 0 0 size 15x15 texture 64x64 anchor center follow cursor layer 24 id cursor_icon hidden

function TdOpenMap3(p: player):
	set {-g} to {-td-host::%p%}
	if {-g} is not set:
		set {-g} to %p%
	open screen "Tower Defense" to p:
		# Невидимый фон за экраном — клик в пустое место или за экран отвязывает выбранную башню от курсора
		box at 0 0 size 2000x2000 anchor center color #00000000 layer -1 run TdClearTool(player) keep open
		# Верхний HUD (скруглённая стеклянная панель + пилюли показателей)
		box at 0 -114 size 468x22 anchor center color #E6090E1A gradient #E6111C33 border #FF38BDF8 border_width 1 radius 6 shadow blur layer 11
		box at -202 -114 size 54x15 anchor center color #CC162032 border #4438BDF8 radius 4 layer 11
		box at -134 -114 size 62x15 anchor center color #CC162032 border #44FACC15 radius 4 layer 11
		box at -68 -114 size 48x15 anchor center color #CC162032 border #44EF4444 radius 4 layer 11
		box at -2 -114 size 66x15 anchor center color #CC162032 border #4422C55E radius 4 layer 11
		box at 132 -114 size 190x15 anchor center color #CC162032 border #44C084FC radius 4 layer 11
		image "td:textures/gui/icon_wave.png" at -220 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&eВолна"
		text "&f0/20" at -195 -114 anchor center shadow layer 12 id hud_wave
		image "td:textures/gui/icon_coin.png" at -154 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&6Личное золото"
		text "&6120" at -126 -114 anchor center shadow layer 12 id hud_gold
		image "td:textures/gui/icon_heart.png" at -82 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&cЗдоровье базы"
		text "&c10" at -60 -114 anchor center shadow layer 12 id hud_hp
		image "td:textures/gui/icon_trophy.png" at -24 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&aСчёт"
		text "&a0" at 6 -114 anchor center shadow layer 12 id hud_score
		text "&7Соло &7[x1]" at 132 -114 anchor center shadow layer 12 id hud_team

		# Тактическая рамка + HD карта 352x176 (текстура 896x448)
		box at -56 -8 size 358x182 anchor center color #AA090E1A border #AA38BDF8 border_width 2 radius 5 shadow layer 0
		image "td:textures/gui/map_ice.png" at -56 -8 size 352x176 texture 896x448 anchor center layer 1 id map run TdClearTool(player) keep open
		bar "boss_bar" value 100 max 100 at -56 -88 size 210x8 anchor center color #FFEF4444 background #CC1A0A0A text "&c&l☠ БОСС: %value%/%max%" shadow layer 14 hidden

		# Правая панель — Журнал событий (list без скроллбара)
		box at 179 -8 size 110x182 anchor center color #E6090E1A gradient #E6111827 border #6638BDF8 border_width 1 radius 5 shadow blur layer 11
		list from {-td-log::%{-g}%::*} vertical no scrollbar header "&b&l⚡ СОБЫТИЯ" at 179 -8 size 104x174 anchor center layer 12 id ev_log:
			row "%loop-value%"

		# Тактические радар-круги радиуса башен (авто-показ при наведении и клике!)
		image "td:textures/gui/range_circle.png" at 0 0 size 154x154 texture 256x256 anchor center layer 2 id rng_s hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 208x208 texture 256x256 anchor center layer 2 id rng_m hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 310x310 texture 256x256 anchor center layer 2 id rng_l hidden
		image "td:textures/gui/range_buff.png" at 0 0 size 150x150 texture 256x256 anchor center layer 2 id rng_b hidden

		# Сетка постройки 16x8 (клетка 22x22; пустые клетки без лишних тултипов)
		image "td:textures/gui/tile_build.png" at -221 -85 size 22x22 texture 28x28 anchor center layer 3 id c0_0 run TdCellClick(player, 0, 0) keep open
		image "td:textures/gui/tile_build.png" at -199 -85 size 22x22 texture 28x28 anchor center layer 3 id c1_0 run TdCellClick(player, 1, 0) keep open
		image "td:textures/gui/tile_build.png" at -177 -85 size 22x22 texture 28x28 anchor center layer 3 id c2_0 run TdCellClick(player, 2, 0) keep open
		image "td:textures/gui/tile_build.png" at -155 -85 size 22x22 texture 28x28 anchor center layer 3 id c3_0 run TdCellClick(player, 3, 0) keep open
		image "td:textures/gui/tile_build.png" at -133 -85 size 22x22 texture 28x28 anchor center layer 3 id c4_0 run TdCellClick(player, 4, 0) keep open
		image "td:textures/gui/tile_build.png" at -111 -85 size 22x22 texture 28x28 anchor center layer 3 id c5_0 run TdCellClick(player, 5, 0) keep open
		image "td:textures/gui/tile_build.png" at -89 -85 size 22x22 texture 28x28 anchor center layer 3 id c6_0 run TdCellClick(player, 6, 0) keep open
		image "td:textures/gui/tile_build.png" at -67 -85 size 22x22 texture 28x28 anchor center layer 3 id c7_0 run TdCellClick(player, 7, 0) keep open
		image "td:textures/gui/tile_build.png" at -45 -85 size 22x22 texture 28x28 anchor center layer 3 id c8_0 run TdCellClick(player, 8, 0) keep open
		image "td:textures/gui/tile_build.png" at -23 -85 size 22x22 texture 28x28 anchor center layer 3 id c9_0 run TdCellClick(player, 9, 0) keep open
		image "td:textures/gui/tile_build.png" at -1 -85 size 22x22 texture 28x28 anchor center layer 3 id c10_0 run TdCellClick(player, 10, 0) keep open
		image "td:textures/gui/tile_build.png" at 21 -85 size 22x22 texture 28x28 anchor center layer 3 id c11_0 run TdCellClick(player, 11, 0) keep open
		image "td:textures/gui/tile_build.png" at 43 -85 size 22x22 texture 28x28 anchor center layer 3 id c12_0 run TdCellClick(player, 12, 0) keep open
		image "td:textures/gui/tile_build.png" at 65 -85 size 22x22 texture 28x28 anchor center layer 3 id c13_0 run TdCellClick(player, 13, 0) keep open
		image "td:textures/gui/tile_build.png" at 87 -85 size 22x22 texture 28x28 anchor center layer 3 id c14_0 run TdCellClick(player, 14, 0) keep open
		image "td:textures/gui/tile_build.png" at 109 -85 size 22x22 texture 28x28 anchor center layer 3 id c15_0 run TdCellClick(player, 15, 0) keep open
		image "td:textures/gui/tile_build.png" at -221 -63 size 22x22 texture 28x28 anchor center layer 3 id c0_1 run TdCellClick(player, 0, 1) keep open
		image "td:textures/gui/tile_build.png" at -199 -63 size 22x22 texture 28x28 anchor center layer 3 id c1_1 run TdCellClick(player, 1, 1) keep open
		image "td:textures/gui/tile_build.png" at -177 -63 size 22x22 texture 28x28 anchor center layer 3 id c2_1 run TdCellClick(player, 2, 1) keep open
		image "td:textures/gui/tile_build.png" at -45 -63 size 22x22 texture 28x28 anchor center layer 3 id c8_1 run TdCellClick(player, 8, 1) keep open
		image "td:textures/gui/tile_build.png" at -23 -63 size 22x22 texture 28x28 anchor center layer 3 id c9_1 run TdCellClick(player, 9, 1) keep open
		image "td:textures/gui/tile_build.png" at -1 -63 size 22x22 texture 28x28 anchor center layer 3 id c10_1 run TdCellClick(player, 10, 1) keep open
		image "td:textures/gui/tile_build.png" at 21 -63 size 22x22 texture 28x28 anchor center layer 3 id c11_1 run TdCellClick(player, 11, 1) keep open
		image "td:textures/gui/tile_build.png" at 43 -63 size 22x22 texture 28x28 anchor center layer 3 id c12_1 run TdCellClick(player, 12, 1) keep open
		image "td:textures/gui/tile_build.png" at 65 -63 size 22x22 texture 28x28 anchor center layer 3 id c13_1 run TdCellClick(player, 13, 1) keep open
		image "td:textures/gui/tile_build.png" at 87 -63 size 22x22 texture 28x28 anchor center layer 3 id c14_1 run TdCellClick(player, 14, 1) keep open
		image "td:textures/gui/tile_build.png" at 109 -63 size 22x22 texture 28x28 anchor center layer 3 id c15_1 run TdCellClick(player, 15, 1) keep open
		image "td:textures/gui/tile_build.png" at -221 -41 size 22x22 texture 28x28 anchor center layer 3 id c0_2 run TdCellClick(player, 0, 2) keep open
		image "td:textures/gui/tile_build.png" at -199 -41 size 22x22 texture 28x28 anchor center layer 3 id c1_2 run TdCellClick(player, 1, 2) keep open
		image "td:textures/gui/tile_build.png" at -177 -41 size 22x22 texture 28x28 anchor center layer 3 id c2_2 run TdCellClick(player, 2, 2) keep open
		image "td:textures/gui/tile_build.png" at -133 -41 size 22x22 texture 28x28 anchor center layer 3 id c4_2 run TdCellClick(player, 4, 2) keep open
		image "td:textures/gui/tile_build.png" at -111 -41 size 22x22 texture 28x28 anchor center layer 3 id c5_2 run TdCellClick(player, 5, 2) keep open
		image "td:textures/gui/tile_build.png" at -89 -41 size 22x22 texture 28x28 anchor center layer 3 id c6_2 run TdCellClick(player, 6, 2) keep open
		image "td:textures/gui/tile_build.png" at -45 -41 size 22x22 texture 28x28 anchor center layer 3 id c8_2 run TdCellClick(player, 8, 2) keep open
		image "td:textures/gui/tile_build.png" at -23 -41 size 22x22 texture 28x28 anchor center layer 3 id c9_2 run TdCellClick(player, 9, 2) keep open
		image "td:textures/gui/tile_build.png" at -1 -41 size 22x22 texture 28x28 anchor center layer 3 id c10_2 run TdCellClick(player, 10, 2) keep open
		image "td:textures/gui/tile_build.png" at -221 -19 size 22x22 texture 28x28 anchor center layer 3 id c0_3 run TdCellClick(player, 0, 3) keep open
		image "td:textures/gui/tile_build.png" at -199 -19 size 22x22 texture 28x28 anchor center layer 3 id c1_3 run TdCellClick(player, 1, 3) keep open
		image "td:textures/gui/tile_build.png" at -177 -19 size 22x22 texture 28x28 anchor center layer 3 id c2_3 run TdCellClick(player, 2, 3) keep open
		image "td:textures/gui/tile_build.png" at -133 -19 size 22x22 texture 28x28 anchor center layer 3 id c4_3 run TdCellClick(player, 4, 3) keep open
		image "td:textures/gui/tile_build.png" at -111 -19 size 22x22 texture 28x28 anchor center layer 3 id c5_3 run TdCellClick(player, 5, 3) keep open
		image "td:textures/gui/tile_build.png" at -89 -19 size 22x22 texture 28x28 anchor center layer 3 id c6_3 run TdCellClick(player, 6, 3) keep open
		image "td:textures/gui/tile_build.png" at -45 -19 size 22x22 texture 28x28 anchor center layer 3 id c8_3 run TdCellClick(player, 8, 3) keep open
		image "td:textures/gui/tile_build.png" at -23 -19 size 22x22 texture 28x28 anchor center layer 3 id c9_3 run TdCellClick(player, 9, 3) keep open
		image "td:textures/gui/tile_build.png" at -1 -19 size 22x22 texture 28x28 anchor center layer 3 id c10_3 run TdCellClick(player, 10, 3) keep open
		image "td:textures/gui/tile_build.png" at 43 -19 size 22x22 texture 28x28 anchor center layer 3 id c12_3 run TdCellClick(player, 12, 3) keep open
		image "td:textures/gui/tile_build.png" at 65 -19 size 22x22 texture 28x28 anchor center layer 3 id c13_3 run TdCellClick(player, 13, 3) keep open
		image "td:textures/gui/tile_build.png" at 87 -19 size 22x22 texture 28x28 anchor center layer 3 id c14_3 run TdCellClick(player, 14, 3) keep open
		image "td:textures/gui/tile_build.png" at 109 -19 size 22x22 texture 28x28 anchor center layer 3 id c15_3 run TdCellClick(player, 15, 3) keep open
		image "td:textures/gui/tile_build.png" at -133 3 size 22x22 texture 28x28 anchor center layer 3 id c4_4 run TdCellClick(player, 4, 4) keep open
		image "td:textures/gui/tile_build.png" at -111 3 size 22x22 texture 28x28 anchor center layer 3 id c5_4 run TdCellClick(player, 5, 4) keep open
		image "td:textures/gui/tile_build.png" at -89 3 size 22x22 texture 28x28 anchor center layer 3 id c6_4 run TdCellClick(player, 6, 4) keep open
		image "td:textures/gui/tile_build.png" at -45 3 size 22x22 texture 28x28 anchor center layer 3 id c8_4 run TdCellClick(player, 8, 4) keep open
		image "td:textures/gui/tile_build.png" at -23 3 size 22x22 texture 28x28 anchor center layer 3 id c9_4 run TdCellClick(player, 9, 4) keep open
		image "td:textures/gui/tile_build.png" at -1 3 size 22x22 texture 28x28 anchor center layer 3 id c10_4 run TdCellClick(player, 10, 4) keep open
		image "td:textures/gui/tile_build.png" at 43 3 size 22x22 texture 28x28 anchor center layer 3 id c12_4 run TdCellClick(player, 12, 4) keep open
		image "td:textures/gui/tile_build.png" at 65 3 size 22x22 texture 28x28 anchor center layer 3 id c13_4 run TdCellClick(player, 13, 4) keep open
		image "td:textures/gui/tile_build.png" at 87 3 size 22x22 texture 28x28 anchor center layer 3 id c14_4 run TdCellClick(player, 14, 4) keep open
		image "td:textures/gui/tile_build.png" at 109 3 size 22x22 texture 28x28 anchor center layer 3 id c15_4 run TdCellClick(player, 15, 4) keep open
		image "td:textures/gui/tile_build.png" at -221 25 size 22x22 texture 28x28 anchor center layer 3 id c0_5 run TdCellClick(player, 0, 5) keep open
		image "td:textures/gui/tile_build.png" at -199 25 size 22x22 texture 28x28 anchor center layer 3 id c1_5 run TdCellClick(player, 1, 5) keep open
		image "td:textures/gui/tile_build.png" at -177 25 size 22x22 texture 28x28 anchor center layer 3 id c2_5 run TdCellClick(player, 2, 5) keep open
		image "td:textures/gui/tile_build.png" at -155 25 size 22x22 texture 28x28 anchor center layer 3 id c3_5 run TdCellClick(player, 3, 5) keep open
		image "td:textures/gui/tile_build.png" at -133 25 size 22x22 texture 28x28 anchor center layer 3 id c4_5 run TdCellClick(player, 4, 5) keep open
		image "td:textures/gui/tile_build.png" at -111 25 size 22x22 texture 28x28 anchor center layer 3 id c5_5 run TdCellClick(player, 5, 5) keep open
		image "td:textures/gui/tile_build.png" at -89 25 size 22x22 texture 28x28 anchor center layer 3 id c6_5 run TdCellClick(player, 6, 5) keep open
		image "td:textures/gui/tile_build.png" at -45 25 size 22x22 texture 28x28 anchor center layer 3 id c8_5 run TdCellClick(player, 8, 5) keep open
		image "td:textures/gui/tile_build.png" at -23 25 size 22x22 texture 28x28 anchor center layer 3 id c9_5 run TdCellClick(player, 9, 5) keep open
		image "td:textures/gui/tile_build.png" at -1 25 size 22x22 texture 28x28 anchor center layer 3 id c10_5 run TdCellClick(player, 10, 5) keep open
		image "td:textures/gui/tile_build.png" at 43 25 size 22x22 texture 28x28 anchor center layer 3 id c12_5 run TdCellClick(player, 12, 5) keep open
		image "td:textures/gui/tile_build.png" at 65 25 size 22x22 texture 28x28 anchor center layer 3 id c13_5 run TdCellClick(player, 13, 5) keep open
		image "td:textures/gui/tile_build.png" at 87 25 size 22x22 texture 28x28 anchor center layer 3 id c14_5 run TdCellClick(player, 14, 5) keep open
		image "td:textures/gui/tile_build.png" at 109 25 size 22x22 texture 28x28 anchor center layer 3 id c15_5 run TdCellClick(player, 15, 5) keep open
		image "td:textures/gui/tile_build.png" at -221 47 size 22x22 texture 28x28 anchor center layer 3 id c0_6 run TdCellClick(player, 0, 6) keep open
		image "td:textures/gui/tile_build.png" at -199 47 size 22x22 texture 28x28 anchor center layer 3 id c1_6 run TdCellClick(player, 1, 6) keep open
		image "td:textures/gui/tile_build.png" at -177 47 size 22x22 texture 28x28 anchor center layer 3 id c2_6 run TdCellClick(player, 2, 6) keep open
		image "td:textures/gui/tile_build.png" at -155 47 size 22x22 texture 28x28 anchor center layer 3 id c3_6 run TdCellClick(player, 3, 6) keep open
		image "td:textures/gui/tile_build.png" at -133 47 size 22x22 texture 28x28 anchor center layer 3 id c4_6 run TdCellClick(player, 4, 6) keep open
		image "td:textures/gui/tile_build.png" at -111 47 size 22x22 texture 28x28 anchor center layer 3 id c5_6 run TdCellClick(player, 5, 6) keep open
		image "td:textures/gui/tile_build.png" at -89 47 size 22x22 texture 28x28 anchor center layer 3 id c6_6 run TdCellClick(player, 6, 6) keep open
		image "td:textures/gui/tile_build.png" at 43 47 size 22x22 texture 28x28 anchor center layer 3 id c12_6 run TdCellClick(player, 12, 6) keep open
		image "td:textures/gui/tile_build.png" at 65 47 size 22x22 texture 28x28 anchor center layer 3 id c13_6 run TdCellClick(player, 13, 6) keep open
		image "td:textures/gui/tile_build.png" at 87 47 size 22x22 texture 28x28 anchor center layer 3 id c14_6 run TdCellClick(player, 14, 6) keep open
		image "td:textures/gui/tile_build.png" at 109 47 size 22x22 texture 28x28 anchor center layer 3 id c15_6 run TdCellClick(player, 15, 6) keep open
		image "td:textures/gui/tile_build.png" at -221 69 size 22x22 texture 28x28 anchor center layer 3 id c0_7 run TdCellClick(player, 0, 7) keep open
		image "td:textures/gui/tile_build.png" at -199 69 size 22x22 texture 28x28 anchor center layer 3 id c1_7 run TdCellClick(player, 1, 7) keep open
		image "td:textures/gui/tile_build.png" at -177 69 size 22x22 texture 28x28 anchor center layer 3 id c2_7 run TdCellClick(player, 2, 7) keep open
		image "td:textures/gui/tile_build.png" at -155 69 size 22x22 texture 28x28 anchor center layer 3 id c3_7 run TdCellClick(player, 3, 7) keep open
		image "td:textures/gui/tile_build.png" at -133 69 size 22x22 texture 28x28 anchor center layer 3 id c4_7 run TdCellClick(player, 4, 7) keep open
		image "td:textures/gui/tile_build.png" at -111 69 size 22x22 texture 28x28 anchor center layer 3 id c5_7 run TdCellClick(player, 5, 7) keep open
		image "td:textures/gui/tile_build.png" at -89 69 size 22x22 texture 28x28 anchor center layer 3 id c6_7 run TdCellClick(player, 6, 7) keep open
		image "td:textures/gui/tile_build.png" at -67 69 size 22x22 texture 28x28 anchor center layer 3 id c7_7 run TdCellClick(player, 7, 7) keep open
		image "td:textures/gui/tile_build.png" at -45 69 size 22x22 texture 28x28 anchor center layer 3 id c8_7 run TdCellClick(player, 8, 7) keep open
		image "td:textures/gui/tile_build.png" at -23 69 size 22x22 texture 28x28 anchor center layer 3 id c9_7 run TdCellClick(player, 9, 7) keep open
		image "td:textures/gui/tile_build.png" at -1 69 size 22x22 texture 28x28 anchor center layer 3 id c10_7 run TdCellClick(player, 10, 7) keep open
		image "td:textures/gui/tile_build.png" at 21 69 size 22x22 texture 28x28 anchor center layer 3 id c11_7 run TdCellClick(player, 11, 7) keep open
		image "td:textures/gui/tile_build.png" at 43 69 size 22x22 texture 28x28 anchor center layer 3 id c12_7 run TdCellClick(player, 12, 7) keep open
		image "td:textures/gui/tile_build.png" at 65 69 size 22x22 texture 28x28 anchor center layer 3 id c13_7 run TdCellClick(player, 13, 7) keep open
		image "td:textures/gui/tile_build.png" at 87 69 size 22x22 texture 28x28 anchor center layer 3 id c14_7 run TdCellClick(player, 14, 7) keep open
		image "td:textures/gui/tile_build.png" at 109 69 size 22x22 texture 28x28 anchor center layer 3 id c15_7 run TdCellClick(player, 15, 7) keep open

		# Пул звёзд уровней и эволюций (до 32 башен)
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st1 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st2 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st3 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st4 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st5 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st6 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st7 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st8 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st9 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st10 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st11 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st12 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st13 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st14 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st15 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st16 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st17 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st18 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st19 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st20 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st21 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st22 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st23 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st24 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st25 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st26 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st27 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st28 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st29 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st30 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st31 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st32 hidden

		# 11 слотов обычных врагов + 1 слот Босса + тонкие HP-бары (64x64 HD + 60 FPS tween)
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e1 hidden
		bar "ehp1" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e2 hidden
		bar "ehp2" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e3 hidden
		bar "ehp3" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e4 hidden
		bar "ehp4" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e5 hidden
		bar "ehp5" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e6 hidden
		bar "ehp6" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e7 hidden
		bar "ehp7" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e8 hidden
		bar "ehp8" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e9 hidden
		bar "ehp9" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e10 hidden
		bar "ehp10" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e11 hidden
		bar "ehp11" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_boss.png" at -221 -63 size 28x28 texture 64x64 anchor center layer 8 id e12 hidden
		bar "ehp12" value 100 max 100 at -221 -79 size 26x4 anchor center color #FFEF4444 background #CC1A0A0A layer 9 hidden

		# 6 летающих HD прожектайлов (60 FPS tween)
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr1 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr2 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr3 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr4 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr5 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr6 hidden

		# Компактная строка статуса
		box at 0 92 size 468x13 anchor center color #E60B1220 border #3338BDF8 radius 3 layer 11
		text "&e[1] Баллиста &7— наведите на башню для радиуса и статов, ЛКМ по клетке — строить" at 0 92 anchor center shadow layer 12 id status_txt
		text "&c&lБАЗА ПАЛА!" at -56 -18 anchor center shadow scale 1.4 layer 20 id end_title hidden
		text "&fНажми &eR &f(рестарт) или &eM &f(меню)" at -56 2 anchor center shadow layer 20 id end_sub hidden

		# Нижняя панель: 9 HD башен (включая Маяк-Баффер #9) + управление и тумблер Курсора
		box at 0 121 size 468x40 anchor center color #EE090E1A gradient #EE111827 border #FF1E293B radius 6 shadow blur layer 11
		box at -218 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -186 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -154 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -122 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -90 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -58 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -26 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at 6 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at 38 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -218 106 size 26x2 anchor center color #FFFACC15 radius 1 id sel_bar hidden layer 13
		image "td:textures/gui/tower_archer.png" at -218 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e&l[1] Эльфийская Баллиста &650з%nl%&8▪ &7Урон: &f15 &8| &7КД: &a0.4с &8| &7Дальн: &b78%nl%&6✦ 5★ Шквал: &fстреляет сразу по 2 целям"
		text "&e[1]50" at -218 131 anchor center shadow scale 0.85 layer 12 id lbl1
		image "td:textures/gui/tower_cannon.png" at -186 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6&l[2] Мортира «Вулкан» &695з%nl%&8▪ &7Урон: &f36 &c(Сплеш) &8| &7Дальн: &b74%nl%&6✦ 5★ Напалм: &fмега-сплеш + поджог врагов"
		text "&7[2]95" at -186 131 anchor center shadow scale 0.85 layer 12 id lbl2
		image "td:textures/gui/tower_frost.png" at -154 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b&l[3] Крио-Обелиск &685з%nl%&8▪ &7Аура: &f8 ур. &b(40% замедл. всех в радиусе 76)%nl%&6✦ 5★ Стужа: &f50% замедл. + 12% заморозка"
		text "&7[3]85" at -154 131 anchor center shadow scale 0.85 layer 12 id lbl3
		image "td:textures/gui/tower_mage.png" at -122 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d&l[4] Аркан-Шпиль &6130з%nl%&8▪ &7Урон: &f32 &8| &7Дальн: &b106%nl%&6✦ 5★ Убийца Боссов: &fx2 Боссам + 4% макс.HP"
		text "&7[4]130" at -122 131 anchor center shadow scale 0.85 layer 12 id lbl4
		image "td:textures/gui/tower_tesla.png" at -90 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico5 run TdSelectTool(player, 5) keep open tooltip "&b&l[5] Плазмо-Тесла &6115з%nl%&8▪ &7Урон: &f20 &b(Цепь на 3 цели) &8| &7Дальн: &b86%nl%&6✦ 5★ Шторм: &f5 целей + 90% урон + микро-стан"
		text "&7[5]115" at -90 131 anchor center shadow scale 0.85 layer 12 id lbl5
		image "td:textures/gui/tower_sniper.png" at -58 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico6 run TdSelectTool(player, 6) keep open tooltip "&c&l[6] Рейлган «Сокол» &6150з%nl%&8▪ &7Урон: &f56 &c(Крит 25%) &8| &7Дальн: &b172%nl%&6✦ 5★ Казнь: &fкрит 45% x2.5 + добивание <20% HP"
		text "&7[6]150" at -58 131 anchor center shadow scale 0.85 layer 12 id lbl6
		image "td:textures/gui/tower_poison.png" at -26 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico7 run TdSelectTool(player, 7) keep open tooltip "&a&l[7] Чумной Реактор &6105з%nl%&8▪ &7Урон: &f12 + &aКислота (DOT) &8| &7Дальн: &b80%nl%&6✦ 5★ Чума: &fзаражает всех врагов рядом"
		text "&7[7]105" at -26 131 anchor center shadow scale 0.85 layer 12 id lbl7
		image "td:textures/gui/tower_farm.png" at 6 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico8 run TdSelectTool(player, 8) keep open tooltip "&6&l[8] Монетный Двор &6140з%nl%&8▪ &7Доход: &6+28/+50/+80/+120з &7за волну%nl%&6✦ 5★ Золотой Дворец: &f+180з и +1 HP базе"
		text "&7[8]140" at 6 131 anchor center shadow scale 0.85 layer 12 id lbl8
		image "td:textures/gui/tower_buffer.png" at 38 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico9 run TdSelectTool(player, 9) keep open tooltip "&d&l[9] Боевой Маяк (Баффер) &6125з%nl%&8▪ &7Бафф-аура: &d+18..42% урона &7башням в радиусе 68%nl%&6✦ 5★ Знамя Империи: &f+55% урон, +20% скоростр. и +25з"
		text "&7[9]125" at 38 131 anchor center shadow scale 0.85 layer 12 id lbl9
		button "&aСтарт/x2" at 94 112 size 56x15 anchor center run TdWaveOrSpeed(player) keep open tooltip "&aСтарт волны / Скорость x1↔x2 &8[Space]"
		toggle "Авто" id autowave at 152 112 size 52x15 anchor center value false color #22C55E tooltip "&aАвто-старт волн"
		toggle "Курсор" id cursortog at 208 112 size 52x15 anchor center value true color #38BDF8 tooltip "&bИконка выбранной башни у курсора &8[C]"
		button "&cПродать" at 94 129 size 56x15 anchor center run TdToggleSell(player) keep open tooltip "&cПродать башню за 70% &8[X]"
		button "Заново" at 152 129 size 52x15 anchor center run TdRestartCurrent(player) keep open tooltip "&eРестарт карты &8[R]"
		button "Меню" at 208 129 size 52x15 anchor center run TdOpenMenu(player) keep open tooltip "&eВыбор карты и кооп &8[M]"

		# Привязанная к курсору иконка текущей башни (универсальное свойство follow cursor)
		image "td:textures/gui/tower_archer.png" at 0 0 size 15x15 texture 64x64 anchor center follow cursor layer 24 id cursor_icon hidden

function TdOpenMap4(p: player):
	set {-g} to {-td-host::%p%}
	if {-g} is not set:
		set {-g} to %p%
	open screen "Tower Defense" to p:
		# Невидимый фон за экраном — клик в пустое место или за экран отвязывает выбранную башню от курсора
		box at 0 0 size 2000x2000 anchor center color #00000000 layer -1 run TdClearTool(player) keep open
		# Верхний HUD (скруглённая стеклянная панель + пилюли показателей)
		box at 0 -114 size 468x22 anchor center color #E6090E1A gradient #E6111C33 border #FF38BDF8 border_width 1 radius 6 shadow blur layer 11
		box at -202 -114 size 54x15 anchor center color #CC162032 border #4438BDF8 radius 4 layer 11
		box at -134 -114 size 62x15 anchor center color #CC162032 border #44FACC15 radius 4 layer 11
		box at -68 -114 size 48x15 anchor center color #CC162032 border #44EF4444 radius 4 layer 11
		box at -2 -114 size 66x15 anchor center color #CC162032 border #4422C55E radius 4 layer 11
		box at 132 -114 size 190x15 anchor center color #CC162032 border #44C084FC radius 4 layer 11
		image "td:textures/gui/icon_wave.png" at -220 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&eВолна"
		text "&f0/20" at -195 -114 anchor center shadow layer 12 id hud_wave
		image "td:textures/gui/icon_coin.png" at -154 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&6Личное золото"
		text "&6120" at -126 -114 anchor center shadow layer 12 id hud_gold
		image "td:textures/gui/icon_heart.png" at -82 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&cЗдоровье базы"
		text "&c10" at -60 -114 anchor center shadow layer 12 id hud_hp
		image "td:textures/gui/icon_trophy.png" at -24 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&aСчёт"
		text "&a0" at 6 -114 anchor center shadow layer 12 id hud_score
		text "&7Соло &7[x1]" at 132 -114 anchor center shadow layer 12 id hud_team

		# Тактическая рамка + HD карта 352x176 (текстура 896x448)
		box at -56 -8 size 358x182 anchor center color #AA090E1A border #AA38BDF8 border_width 2 radius 5 shadow layer 0
		image "td:textures/gui/map_swamp.png" at -56 -8 size 352x176 texture 896x448 anchor center layer 1 id map run TdClearTool(player) keep open
		bar "boss_bar" value 100 max 100 at -56 -88 size 210x8 anchor center color #FFEF4444 background #CC1A0A0A text "&c&l☠ БОСС: %value%/%max%" shadow layer 14 hidden

		# Правая панель — Журнал событий (list без скроллбара)
		box at 179 -8 size 110x182 anchor center color #E6090E1A gradient #E6111827 border #6638BDF8 border_width 1 radius 5 shadow blur layer 11
		list from {-td-log::%{-g}%::*} vertical no scrollbar header "&b&l⚡ СОБЫТИЯ" at 179 -8 size 104x174 anchor center layer 12 id ev_log:
			row "%loop-value%"

		# Тактические радар-круги радиуса башен (авто-показ при наведении и клике!)
		image "td:textures/gui/range_circle.png" at 0 0 size 154x154 texture 256x256 anchor center layer 2 id rng_s hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 208x208 texture 256x256 anchor center layer 2 id rng_m hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 310x310 texture 256x256 anchor center layer 2 id rng_l hidden
		image "td:textures/gui/range_buff.png" at 0 0 size 150x150 texture 256x256 anchor center layer 2 id rng_b hidden

		# Сетка постройки 16x8 (клетка 22x22; пустые клетки без лишних тултипов)
		image "td:textures/gui/tile_build.png" at -221 -85 size 22x22 texture 28x28 anchor center layer 3 id c0_0 run TdCellClick(player, 0, 0) keep open
		image "td:textures/gui/tile_build.png" at -155 -85 size 22x22 texture 28x28 anchor center layer 3 id c3_0 run TdCellClick(player, 3, 0) keep open
		image "td:textures/gui/tile_build.png" at -133 -85 size 22x22 texture 28x28 anchor center layer 3 id c4_0 run TdCellClick(player, 4, 0) keep open
		image "td:textures/gui/tile_build.png" at 21 -85 size 22x22 texture 28x28 anchor center layer 3 id c11_0 run TdCellClick(player, 11, 0) keep open
		image "td:textures/gui/tile_build.png" at 43 -85 size 22x22 texture 28x28 anchor center layer 3 id c12_0 run TdCellClick(player, 12, 0) keep open
		image "td:textures/gui/tile_build.png" at 65 -85 size 22x22 texture 28x28 anchor center layer 3 id c13_0 run TdCellClick(player, 13, 0) keep open
		image "td:textures/gui/tile_build.png" at 87 -85 size 22x22 texture 28x28 anchor center layer 3 id c14_0 run TdCellClick(player, 14, 0) keep open
		image "td:textures/gui/tile_build.png" at 109 -85 size 22x22 texture 28x28 anchor center layer 3 id c15_0 run TdCellClick(player, 15, 0) keep open
		image "td:textures/gui/tile_build.png" at -221 -63 size 22x22 texture 28x28 anchor center layer 3 id c0_1 run TdCellClick(player, 0, 1) keep open
		image "td:textures/gui/tile_build.png" at -199 -63 size 22x22 texture 28x28 anchor center layer 3 id c1_1 run TdCellClick(player, 1, 1) keep open
		image "td:textures/gui/tile_build.png" at -177 -63 size 22x22 texture 28x28 anchor center layer 3 id c2_1 run TdCellClick(player, 2, 1) keep open
		image "td:textures/gui/tile_build.png" at -155 -63 size 22x22 texture 28x28 anchor center layer 3 id c3_1 run TdCellClick(player, 3, 1) keep open
		image "td:textures/gui/tile_build.png" at -133 -63 size 22x22 texture 28x28 anchor center layer 3 id c4_1 run TdCellClick(player, 4, 1) keep open
		image "td:textures/gui/tile_build.png" at -89 -63 size 22x22 texture 28x28 anchor center layer 3 id c6_1 run TdCellClick(player, 6, 1) keep open
		image "td:textures/gui/tile_build.png" at -67 -63 size 22x22 texture 28x28 anchor center layer 3 id c7_1 run TdCellClick(player, 7, 1) keep open
		image "td:textures/gui/tile_build.png" at -45 -63 size 22x22 texture 28x28 anchor center layer 3 id c8_1 run TdCellClick(player, 8, 1) keep open
		image "td:textures/gui/tile_build.png" at -23 -63 size 22x22 texture 28x28 anchor center layer 3 id c9_1 run TdCellClick(player, 9, 1) keep open
		image "td:textures/gui/tile_build.png" at 21 -63 size 22x22 texture 28x28 anchor center layer 3 id c11_1 run TdCellClick(player, 11, 1) keep open
		image "td:textures/gui/tile_build.png" at 65 -63 size 22x22 texture 28x28 anchor center layer 3 id c13_1 run TdCellClick(player, 13, 1) keep open
		image "td:textures/gui/tile_build.png" at 87 -63 size 22x22 texture 28x28 anchor center layer 3 id c14_1 run TdCellClick(player, 14, 1) keep open
		image "td:textures/gui/tile_build.png" at 109 -63 size 22x22 texture 28x28 anchor center layer 3 id c15_1 run TdCellClick(player, 15, 1) keep open
		image "td:textures/gui/tile_build.png" at -67 -41 size 22x22 texture 28x28 anchor center layer 3 id c7_2 run TdCellClick(player, 7, 2) keep open
		image "td:textures/gui/tile_build.png" at -23 -41 size 22x22 texture 28x28 anchor center layer 3 id c9_2 run TdCellClick(player, 9, 2) keep open
		image "td:textures/gui/tile_build.png" at 43 -41 size 22x22 texture 28x28 anchor center layer 3 id c12_2 run TdCellClick(player, 12, 2) keep open
		image "td:textures/gui/tile_build.png" at 65 -41 size 22x22 texture 28x28 anchor center layer 3 id c13_2 run TdCellClick(player, 13, 2) keep open
		image "td:textures/gui/tile_build.png" at 87 -41 size 22x22 texture 28x28 anchor center layer 3 id c14_2 run TdCellClick(player, 14, 2) keep open
		image "td:textures/gui/tile_build.png" at 109 -41 size 22x22 texture 28x28 anchor center layer 3 id c15_2 run TdCellClick(player, 15, 2) keep open
		image "td:textures/gui/tile_build.png" at -221 -19 size 22x22 texture 28x28 anchor center layer 3 id c0_3 run TdCellClick(player, 0, 3) keep open
		image "td:textures/gui/tile_build.png" at -199 -19 size 22x22 texture 28x28 anchor center layer 3 id c1_3 run TdCellClick(player, 1, 3) keep open
		image "td:textures/gui/tile_build.png" at -177 -19 size 22x22 texture 28x28 anchor center layer 3 id c2_3 run TdCellClick(player, 2, 3) keep open
		image "td:textures/gui/tile_build.png" at -155 -19 size 22x22 texture 28x28 anchor center layer 3 id c3_3 run TdCellClick(player, 3, 3) keep open
		image "td:textures/gui/tile_build.png" at -133 -19 size 22x22 texture 28x28 anchor center layer 3 id c4_3 run TdCellClick(player, 4, 3) keep open
		image "td:textures/gui/tile_build.png" at -111 -19 size 22x22 texture 28x28 anchor center layer 3 id c5_3 run TdCellClick(player, 5, 3) keep open
		image "td:textures/gui/tile_build.png" at -89 -19 size 22x22 texture 28x28 anchor center layer 3 id c6_3 run TdCellClick(player, 6, 3) keep open
		image "td:textures/gui/tile_build.png" at -67 -19 size 22x22 texture 28x28 anchor center layer 3 id c7_3 run TdCellClick(player, 7, 3) keep open
		image "td:textures/gui/tile_build.png" at -23 -19 size 22x22 texture 28x28 anchor center layer 3 id c9_3 run TdCellClick(player, 9, 3) keep open
		image "td:textures/gui/tile_build.png" at 43 -19 size 22x22 texture 28x28 anchor center layer 3 id c12_3 run TdCellClick(player, 12, 3) keep open
		image "td:textures/gui/tile_build.png" at -221 3 size 22x22 texture 28x28 anchor center layer 3 id c0_4 run TdCellClick(player, 0, 4) keep open
		image "td:textures/gui/tile_build.png" at -199 3 size 22x22 texture 28x28 anchor center layer 3 id c1_4 run TdCellClick(player, 1, 4) keep open
		image "td:textures/gui/tile_build.png" at -133 3 size 22x22 texture 28x28 anchor center layer 3 id c4_4 run TdCellClick(player, 4, 4) keep open
		image "td:textures/gui/tile_build.png" at -111 3 size 22x22 texture 28x28 anchor center layer 3 id c5_4 run TdCellClick(player, 5, 4) keep open
		image "td:textures/gui/tile_build.png" at -45 3 size 22x22 texture 28x28 anchor center layer 3 id c8_4 run TdCellClick(player, 8, 4) keep open
		image "td:textures/gui/tile_build.png" at -23 3 size 22x22 texture 28x28 anchor center layer 3 id c9_4 run TdCellClick(player, 9, 4) keep open
		image "td:textures/gui/tile_build.png" at 21 3 size 22x22 texture 28x28 anchor center layer 3 id c11_4 run TdCellClick(player, 11, 4) keep open
		image "td:textures/gui/tile_build.png" at 43 3 size 22x22 texture 28x28 anchor center layer 3 id c12_4 run TdCellClick(player, 12, 4) keep open
		image "td:textures/gui/tile_build.png" at 87 3 size 22x22 texture 28x28 anchor center layer 3 id c14_4 run TdCellClick(player, 14, 4) keep open
		image "td:textures/gui/tile_build.png" at 109 3 size 22x22 texture 28x28 anchor center layer 3 id c15_4 run TdCellClick(player, 15, 4) keep open
		image "td:textures/gui/tile_build.png" at -221 25 size 22x22 texture 28x28 anchor center layer 3 id c0_5 run TdCellClick(player, 0, 5) keep open
		image "td:textures/gui/tile_build.png" at -199 25 size 22x22 texture 28x28 anchor center layer 3 id c1_5 run TdCellClick(player, 1, 5) keep open
		image "td:textures/gui/tile_build.png" at -177 25 size 22x22 texture 28x28 anchor center layer 3 id c2_5 run TdCellClick(player, 2, 5) keep open
		image "td:textures/gui/tile_build.png" at -133 25 size 22x22 texture 28x28 anchor center layer 3 id c4_5 run TdCellClick(player, 4, 5) keep open
		image "td:textures/gui/tile_build.png" at -111 25 size 22x22 texture 28x28 anchor center layer 3 id c5_5 run TdCellClick(player, 5, 5) keep open
		image "td:textures/gui/tile_build.png" at -89 25 size 22x22 texture 28x28 anchor center layer 3 id c6_5 run TdCellClick(player, 6, 5) keep open
		image "td:textures/gui/tile_build.png" at -45 25 size 22x22 texture 28x28 anchor center layer 3 id c8_5 run TdCellClick(player, 8, 5) keep open
		image "td:textures/gui/tile_build.png" at -23 25 size 22x22 texture 28x28 anchor center layer 3 id c9_5 run TdCellClick(player, 9, 5) keep open
		image "td:textures/gui/tile_build.png" at 21 25 size 22x22 texture 28x28 anchor center layer 3 id c11_5 run TdCellClick(player, 11, 5) keep open
		image "td:textures/gui/tile_build.png" at 43 25 size 22x22 texture 28x28 anchor center layer 3 id c12_5 run TdCellClick(player, 12, 5) keep open
		image "td:textures/gui/tile_build.png" at 109 25 size 22x22 texture 28x28 anchor center layer 3 id c15_5 run TdCellClick(player, 15, 5) keep open
		image "td:textures/gui/tile_build.png" at -221 47 size 22x22 texture 28x28 anchor center layer 3 id c0_6 run TdCellClick(player, 0, 6) keep open
		image "td:textures/gui/tile_build.png" at -199 47 size 22x22 texture 28x28 anchor center layer 3 id c1_6 run TdCellClick(player, 1, 6) keep open
		image "td:textures/gui/tile_build.png" at -177 47 size 22x22 texture 28x28 anchor center layer 3 id c2_6 run TdCellClick(player, 2, 6) keep open
		image "td:textures/gui/tile_build.png" at -155 47 size 22x22 texture 28x28 anchor center layer 3 id c3_6 run TdCellClick(player, 3, 6) keep open
		image "td:textures/gui/tile_build.png" at -89 47 size 22x22 texture 28x28 anchor center layer 3 id c6_6 run TdCellClick(player, 6, 6) keep open
		image "td:textures/gui/tile_build.png" at -67 47 size 22x22 texture 28x28 anchor center layer 3 id c7_6 run TdCellClick(player, 7, 6) keep open
		image "td:textures/gui/tile_build.png" at -45 47 size 22x22 texture 28x28 anchor center layer 3 id c8_6 run TdCellClick(player, 8, 6) keep open
		image "td:textures/gui/tile_build.png" at -23 47 size 22x22 texture 28x28 anchor center layer 3 id c9_6 run TdCellClick(player, 9, 6) keep open
		image "td:textures/gui/tile_build.png" at 21 47 size 22x22 texture 28x28 anchor center layer 3 id c11_6 run TdCellClick(player, 11, 6) keep open
		image "td:textures/gui/tile_build.png" at 43 47 size 22x22 texture 28x28 anchor center layer 3 id c12_6 run TdCellClick(player, 12, 6) keep open
		image "td:textures/gui/tile_build.png" at 109 47 size 22x22 texture 28x28 anchor center layer 3 id c15_6 run TdCellClick(player, 15, 6) keep open
		image "td:textures/gui/tile_build.png" at -221 69 size 22x22 texture 28x28 anchor center layer 3 id c0_7 run TdCellClick(player, 0, 7) keep open
		image "td:textures/gui/tile_build.png" at -199 69 size 22x22 texture 28x28 anchor center layer 3 id c1_7 run TdCellClick(player, 1, 7) keep open
		image "td:textures/gui/tile_build.png" at -177 69 size 22x22 texture 28x28 anchor center layer 3 id c2_7 run TdCellClick(player, 2, 7) keep open
		image "td:textures/gui/tile_build.png" at -155 69 size 22x22 texture 28x28 anchor center layer 3 id c3_7 run TdCellClick(player, 3, 7) keep open
		image "td:textures/gui/tile_build.png" at -133 69 size 22x22 texture 28x28 anchor center layer 3 id c4_7 run TdCellClick(player, 4, 7) keep open
		image "td:textures/gui/tile_build.png" at -111 69 size 22x22 texture 28x28 anchor center layer 3 id c5_7 run TdCellClick(player, 5, 7) keep open
		image "td:textures/gui/tile_build.png" at -89 69 size 22x22 texture 28x28 anchor center layer 3 id c6_7 run TdCellClick(player, 6, 7) keep open
		image "td:textures/gui/tile_build.png" at -67 69 size 22x22 texture 28x28 anchor center layer 3 id c7_7 run TdCellClick(player, 7, 7) keep open
		image "td:textures/gui/tile_build.png" at -45 69 size 22x22 texture 28x28 anchor center layer 3 id c8_7 run TdCellClick(player, 8, 7) keep open
		image "td:textures/gui/tile_build.png" at -23 69 size 22x22 texture 28x28 anchor center layer 3 id c9_7 run TdCellClick(player, 9, 7) keep open
		image "td:textures/gui/tile_build.png" at 87 69 size 22x22 texture 28x28 anchor center layer 3 id c14_7 run TdCellClick(player, 14, 7) keep open
		image "td:textures/gui/tile_build.png" at 109 69 size 22x22 texture 28x28 anchor center layer 3 id c15_7 run TdCellClick(player, 15, 7) keep open

		# Пул звёзд уровней и эволюций (до 32 башен)
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st1 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st2 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st3 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st4 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st5 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st6 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st7 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st8 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st9 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st10 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st11 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st12 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st13 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st14 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st15 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st16 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st17 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st18 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st19 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st20 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st21 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st22 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st23 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st24 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st25 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st26 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st27 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st28 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st29 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st30 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st31 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st32 hidden

		# 11 слотов обычных врагов + 1 слот Босса + тонкие HP-бары (64x64 HD + 60 FPS tween)
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e1 hidden
		bar "ehp1" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e2 hidden
		bar "ehp2" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e3 hidden
		bar "ehp3" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e4 hidden
		bar "ehp4" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e5 hidden
		bar "ehp5" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e6 hidden
		bar "ehp6" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e7 hidden
		bar "ehp7" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e8 hidden
		bar "ehp8" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e9 hidden
		bar "ehp9" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e10 hidden
		bar "ehp10" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e11 hidden
		bar "ehp11" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_boss.png" at -221 -63 size 28x28 texture 64x64 anchor center layer 8 id e12 hidden
		bar "ehp12" value 100 max 100 at -221 -79 size 26x4 anchor center color #FFEF4444 background #CC1A0A0A layer 9 hidden

		# 6 летающих HD прожектайлов (60 FPS tween)
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr1 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr2 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr3 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr4 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr5 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr6 hidden

		# Компактная строка статуса
		box at 0 92 size 468x13 anchor center color #E60B1220 border #3338BDF8 radius 3 layer 11
		text "&e[1] Баллиста &7— наведите на башню для радиуса и статов, ЛКМ по клетке — строить" at 0 92 anchor center shadow layer 12 id status_txt
		text "&c&lБАЗА ПАЛА!" at -56 -18 anchor center shadow scale 1.4 layer 20 id end_title hidden
		text "&fНажми &eR &f(рестарт) или &eM &f(меню)" at -56 2 anchor center shadow layer 20 id end_sub hidden

		# Нижняя панель: 9 HD башен (включая Маяк-Баффер #9) + управление и тумблер Курсора
		box at 0 121 size 468x40 anchor center color #EE090E1A gradient #EE111827 border #FF1E293B radius 6 shadow blur layer 11
		box at -218 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -186 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -154 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -122 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -90 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -58 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -26 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at 6 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at 38 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -218 106 size 26x2 anchor center color #FFFACC15 radius 1 id sel_bar hidden layer 13
		image "td:textures/gui/tower_archer.png" at -218 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e&l[1] Эльфийская Баллиста &650з%nl%&8▪ &7Урон: &f15 &8| &7КД: &a0.4с &8| &7Дальн: &b78%nl%&6✦ 5★ Шквал: &fстреляет сразу по 2 целям"
		text "&e[1]50" at -218 131 anchor center shadow scale 0.85 layer 12 id lbl1
		image "td:textures/gui/tower_cannon.png" at -186 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6&l[2] Мортира «Вулкан» &695з%nl%&8▪ &7Урон: &f36 &c(Сплеш) &8| &7Дальн: &b74%nl%&6✦ 5★ Напалм: &fмега-сплеш + поджог врагов"
		text "&7[2]95" at -186 131 anchor center shadow scale 0.85 layer 12 id lbl2
		image "td:textures/gui/tower_frost.png" at -154 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b&l[3] Крио-Обелиск &685з%nl%&8▪ &7Аура: &f8 ур. &b(40% замедл. всех в радиусе 76)%nl%&6✦ 5★ Стужа: &f50% замедл. + 12% заморозка"
		text "&7[3]85" at -154 131 anchor center shadow scale 0.85 layer 12 id lbl3
		image "td:textures/gui/tower_mage.png" at -122 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d&l[4] Аркан-Шпиль &6130з%nl%&8▪ &7Урон: &f32 &8| &7Дальн: &b106%nl%&6✦ 5★ Убийца Боссов: &fx2 Боссам + 4% макс.HP"
		text "&7[4]130" at -122 131 anchor center shadow scale 0.85 layer 12 id lbl4
		image "td:textures/gui/tower_tesla.png" at -90 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico5 run TdSelectTool(player, 5) keep open tooltip "&b&l[5] Плазмо-Тесла &6115з%nl%&8▪ &7Урон: &f20 &b(Цепь на 3 цели) &8| &7Дальн: &b86%nl%&6✦ 5★ Шторм: &f5 целей + 90% урон + микро-стан"
		text "&7[5]115" at -90 131 anchor center shadow scale 0.85 layer 12 id lbl5
		image "td:textures/gui/tower_sniper.png" at -58 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico6 run TdSelectTool(player, 6) keep open tooltip "&c&l[6] Рейлган «Сокол» &6150з%nl%&8▪ &7Урон: &f56 &c(Крит 25%) &8| &7Дальн: &b172%nl%&6✦ 5★ Казнь: &fкрит 45% x2.5 + добивание <20% HP"
		text "&7[6]150" at -58 131 anchor center shadow scale 0.85 layer 12 id lbl6
		image "td:textures/gui/tower_poison.png" at -26 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico7 run TdSelectTool(player, 7) keep open tooltip "&a&l[7] Чумной Реактор &6105з%nl%&8▪ &7Урон: &f12 + &aКислота (DOT) &8| &7Дальн: &b80%nl%&6✦ 5★ Чума: &fзаражает всех врагов рядом"
		text "&7[7]105" at -26 131 anchor center shadow scale 0.85 layer 12 id lbl7
		image "td:textures/gui/tower_farm.png" at 6 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico8 run TdSelectTool(player, 8) keep open tooltip "&6&l[8] Монетный Двор &6140з%nl%&8▪ &7Доход: &6+28/+50/+80/+120з &7за волну%nl%&6✦ 5★ Золотой Дворец: &f+180з и +1 HP базе"
		text "&7[8]140" at 6 131 anchor center shadow scale 0.85 layer 12 id lbl8
		image "td:textures/gui/tower_buffer.png" at 38 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico9 run TdSelectTool(player, 9) keep open tooltip "&d&l[9] Боевой Маяк (Баффер) &6125з%nl%&8▪ &7Бафф-аура: &d+18..42% урона &7башням в радиусе 68%nl%&6✦ 5★ Знамя Империи: &f+55% урон, +20% скоростр. и +25з"
		text "&7[9]125" at 38 131 anchor center shadow scale 0.85 layer 12 id lbl9
		button "&aСтарт/x2" at 94 112 size 56x15 anchor center run TdWaveOrSpeed(player) keep open tooltip "&aСтарт волны / Скорость x1↔x2 &8[Space]"
		toggle "Авто" id autowave at 152 112 size 52x15 anchor center value false color #22C55E tooltip "&aАвто-старт волн"
		toggle "Курсор" id cursortog at 208 112 size 52x15 anchor center value true color #38BDF8 tooltip "&bИконка выбранной башни у курсора &8[C]"
		button "&cПродать" at 94 129 size 56x15 anchor center run TdToggleSell(player) keep open tooltip "&cПродать башню за 70% &8[X]"
		button "Заново" at 152 129 size 52x15 anchor center run TdRestartCurrent(player) keep open tooltip "&eРестарт карты &8[R]"
		button "Меню" at 208 129 size 52x15 anchor center run TdOpenMenu(player) keep open tooltip "&eВыбор карты и кооп &8[M]"

		# Привязанная к курсору иконка текущей башни (универсальное свойство follow cursor)
		image "td:textures/gui/tower_archer.png" at 0 0 size 15x15 texture 64x64 anchor center follow cursor layer 24 id cursor_icon hidden

function TdOpenMap5(p: player):
	set {-g} to {-td-host::%p%}
	if {-g} is not set:
		set {-g} to %p%
	open screen "Tower Defense" to p:
		# Невидимый фон за экраном — клик в пустое место или за экран отвязывает выбранную башню от курсора
		box at 0 0 size 2000x2000 anchor center color #00000000 layer -1 run TdClearTool(player) keep open
		# Верхний HUD (скруглённая стеклянная панель + пилюли показателей)
		box at 0 -114 size 468x22 anchor center color #E6090E1A gradient #E6111C33 border #FF38BDF8 border_width 1 radius 6 shadow blur layer 11
		box at -202 -114 size 54x15 anchor center color #CC162032 border #4438BDF8 radius 4 layer 11
		box at -134 -114 size 62x15 anchor center color #CC162032 border #44FACC15 radius 4 layer 11
		box at -68 -114 size 48x15 anchor center color #CC162032 border #44EF4444 radius 4 layer 11
		box at -2 -114 size 66x15 anchor center color #CC162032 border #4422C55E radius 4 layer 11
		box at 132 -114 size 190x15 anchor center color #CC162032 border #44C084FC radius 4 layer 11
		image "td:textures/gui/icon_wave.png" at -220 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&eВолна"
		text "&f0/20" at -195 -114 anchor center shadow layer 12 id hud_wave
		image "td:textures/gui/icon_coin.png" at -154 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&6Личное золото"
		text "&6120" at -126 -114 anchor center shadow layer 12 id hud_gold
		image "td:textures/gui/icon_heart.png" at -82 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&cЗдоровье базы"
		text "&c10" at -60 -114 anchor center shadow layer 12 id hud_hp
		image "td:textures/gui/icon_trophy.png" at -24 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&aСчёт"
		text "&a0" at 6 -114 anchor center shadow layer 12 id hud_score
		text "&7Соло &7[x1]" at 132 -114 anchor center shadow layer 12 id hud_team

		# Тактическая рамка + HD карта 352x176 (текстура 896x448)
		box at -56 -8 size 358x182 anchor center color #AA090E1A border #AA38BDF8 border_width 2 radius 5 shadow layer 0
		image "td:textures/gui/map_void.png" at -56 -8 size 352x176 texture 896x448 anchor center layer 1 id map run TdClearTool(player) keep open
		bar "boss_bar" value 100 max 100 at -56 -88 size 210x8 anchor center color #FFEF4444 background #CC1A0A0A text "&c&l☠ БОСС: %value%/%max%" shadow layer 14 hidden

		# Правая панель — Журнал событий (list без скроллбара)
		box at 179 -8 size 110x182 anchor center color #E6090E1A gradient #E6111827 border #6638BDF8 border_width 1 radius 5 shadow blur layer 11
		list from {-td-log::%{-g}%::*} vertical no scrollbar header "&b&l⚡ СОБЫТИЯ" at 179 -8 size 104x174 anchor center layer 12 id ev_log:
			row "%loop-value%"

		# Тактические радар-круги радиуса башен (авто-показ при наведении и клике!)
		image "td:textures/gui/range_circle.png" at 0 0 size 154x154 texture 256x256 anchor center layer 2 id rng_s hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 208x208 texture 256x256 anchor center layer 2 id rng_m hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 310x310 texture 256x256 anchor center layer 2 id rng_l hidden
		image "td:textures/gui/range_buff.png" at 0 0 size 150x150 texture 256x256 anchor center layer 2 id rng_b hidden

		# Сетка постройки 16x8 (клетка 22x22; пустые клетки без лишних тултипов)
		image "td:textures/gui/tile_build.png" at -155 -85 size 22x22 texture 28x28 anchor center layer 3 id c3_0 run TdCellClick(player, 3, 0) keep open
		image "td:textures/gui/tile_build.png" at -133 -85 size 22x22 texture 28x28 anchor center layer 3 id c4_0 run TdCellClick(player, 4, 0) keep open
		image "td:textures/gui/tile_build.png" at -45 -85 size 22x22 texture 28x28 anchor center layer 3 id c8_0 run TdCellClick(player, 8, 0) keep open
		image "td:textures/gui/tile_build.png" at -23 -85 size 22x22 texture 28x28 anchor center layer 3 id c9_0 run TdCellClick(player, 9, 0) keep open
		image "td:textures/gui/tile_build.png" at -1 -85 size 22x22 texture 28x28 anchor center layer 3 id c10_0 run TdCellClick(player, 10, 0) keep open
		image "td:textures/gui/tile_build.png" at 21 -85 size 22x22 texture 28x28 anchor center layer 3 id c11_0 run TdCellClick(player, 11, 0) keep open
		image "td:textures/gui/tile_build.png" at 43 -85 size 22x22 texture 28x28 anchor center layer 3 id c12_0 run TdCellClick(player, 12, 0) keep open
		image "td:textures/gui/tile_build.png" at -177 -63 size 22x22 texture 28x28 anchor center layer 3 id c2_1 run TdCellClick(player, 2, 1) keep open
		image "td:textures/gui/tile_build.png" at -155 -63 size 22x22 texture 28x28 anchor center layer 3 id c3_1 run TdCellClick(player, 3, 1) keep open
		image "td:textures/gui/tile_build.png" at -133 -63 size 22x22 texture 28x28 anchor center layer 3 id c4_1 run TdCellClick(player, 4, 1) keep open
		image "td:textures/gui/tile_build.png" at -67 -63 size 22x22 texture 28x28 anchor center layer 3 id c7_1 run TdCellClick(player, 7, 1) keep open
		image "td:textures/gui/tile_build.png" at -45 -63 size 22x22 texture 28x28 anchor center layer 3 id c8_1 run TdCellClick(player, 8, 1) keep open
		image "td:textures/gui/tile_build.png" at -23 -63 size 22x22 texture 28x28 anchor center layer 3 id c9_1 run TdCellClick(player, 9, 1) keep open
		image "td:textures/gui/tile_build.png" at -1 -63 size 22x22 texture 28x28 anchor center layer 3 id c10_1 run TdCellClick(player, 10, 1) keep open
		image "td:textures/gui/tile_build.png" at 21 -63 size 22x22 texture 28x28 anchor center layer 3 id c11_1 run TdCellClick(player, 11, 1) keep open
		image "td:textures/gui/tile_build.png" at 43 -63 size 22x22 texture 28x28 anchor center layer 3 id c12_1 run TdCellClick(player, 12, 1) keep open
		image "td:textures/gui/tile_build.png" at 65 -63 size 22x22 texture 28x28 anchor center layer 3 id c13_1 run TdCellClick(player, 13, 1) keep open
		image "td:textures/gui/tile_build.png" at 87 -63 size 22x22 texture 28x28 anchor center layer 3 id c14_1 run TdCellClick(player, 14, 1) keep open
		image "td:textures/gui/tile_build.png" at 109 -63 size 22x22 texture 28x28 anchor center layer 3 id c15_1 run TdCellClick(player, 15, 1) keep open
		image "td:textures/gui/tile_build.png" at -199 -41 size 22x22 texture 28x28 anchor center layer 3 id c1_2 run TdCellClick(player, 1, 2) keep open
		image "td:textures/gui/tile_build.png" at -177 -41 size 22x22 texture 28x28 anchor center layer 3 id c2_2 run TdCellClick(player, 2, 2) keep open
		image "td:textures/gui/tile_build.png" at -155 -41 size 22x22 texture 28x28 anchor center layer 3 id c3_2 run TdCellClick(player, 3, 2) keep open
		image "td:textures/gui/tile_build.png" at -133 -41 size 22x22 texture 28x28 anchor center layer 3 id c4_2 run TdCellClick(player, 4, 2) keep open
		image "td:textures/gui/tile_build.png" at -111 -41 size 22x22 texture 28x28 anchor center layer 3 id c5_2 run TdCellClick(player, 5, 2) keep open
		image "td:textures/gui/tile_build.png" at -89 -41 size 22x22 texture 28x28 anchor center layer 3 id c6_2 run TdCellClick(player, 6, 2) keep open
		image "td:textures/gui/tile_build.png" at -67 -41 size 22x22 texture 28x28 anchor center layer 3 id c7_2 run TdCellClick(player, 7, 2) keep open
		image "td:textures/gui/tile_build.png" at 43 -41 size 22x22 texture 28x28 anchor center layer 3 id c12_2 run TdCellClick(player, 12, 2) keep open
		image "td:textures/gui/tile_build.png" at 65 -41 size 22x22 texture 28x28 anchor center layer 3 id c13_2 run TdCellClick(player, 13, 2) keep open
		image "td:textures/gui/tile_build.png" at 87 -41 size 22x22 texture 28x28 anchor center layer 3 id c14_2 run TdCellClick(player, 14, 2) keep open
		image "td:textures/gui/tile_build.png" at 109 -41 size 22x22 texture 28x28 anchor center layer 3 id c15_2 run TdCellClick(player, 15, 2) keep open
		image "td:textures/gui/tile_build.png" at -111 -19 size 22x22 texture 28x28 anchor center layer 3 id c5_3 run TdCellClick(player, 5, 3) keep open
		image "td:textures/gui/tile_build.png" at -89 -19 size 22x22 texture 28x28 anchor center layer 3 id c6_3 run TdCellClick(player, 6, 3) keep open
		image "td:textures/gui/tile_build.png" at -67 -19 size 22x22 texture 28x28 anchor center layer 3 id c7_3 run TdCellClick(player, 7, 3) keep open
		image "td:textures/gui/tile_build.png" at -23 -19 size 22x22 texture 28x28 anchor center layer 3 id c9_3 run TdCellClick(player, 9, 3) keep open
		image "td:textures/gui/tile_build.png" at -1 -19 size 22x22 texture 28x28 anchor center layer 3 id c10_3 run TdCellClick(player, 10, 3) keep open
		image "td:textures/gui/tile_build.png" at 43 -19 size 22x22 texture 28x28 anchor center layer 3 id c12_3 run TdCellClick(player, 12, 3) keep open
		image "td:textures/gui/tile_build.png" at 65 -19 size 22x22 texture 28x28 anchor center layer 3 id c13_3 run TdCellClick(player, 13, 3) keep open
		image "td:textures/gui/tile_build.png" at 87 -19 size 22x22 texture 28x28 anchor center layer 3 id c14_3 run TdCellClick(player, 14, 3) keep open
		image "td:textures/gui/tile_build.png" at 109 -19 size 22x22 texture 28x28 anchor center layer 3 id c15_3 run TdCellClick(player, 15, 3) keep open
		image "td:textures/gui/tile_build.png" at -221 3 size 22x22 texture 28x28 anchor center layer 3 id c0_4 run TdCellClick(player, 0, 4) keep open
		image "td:textures/gui/tile_build.png" at -199 3 size 22x22 texture 28x28 anchor center layer 3 id c1_4 run TdCellClick(player, 1, 4) keep open
		image "td:textures/gui/tile_build.png" at -177 3 size 22x22 texture 28x28 anchor center layer 3 id c2_4 run TdCellClick(player, 2, 4) keep open
		image "td:textures/gui/tile_build.png" at -155 3 size 22x22 texture 28x28 anchor center layer 3 id c3_4 run TdCellClick(player, 3, 4) keep open
		image "td:textures/gui/tile_build.png" at -111 3 size 22x22 texture 28x28 anchor center layer 3 id c5_4 run TdCellClick(player, 5, 4) keep open
		image "td:textures/gui/tile_build.png" at -89 3 size 22x22 texture 28x28 anchor center layer 3 id c6_4 run TdCellClick(player, 6, 4) keep open
		image "td:textures/gui/tile_build.png" at -67 3 size 22x22 texture 28x28 anchor center layer 3 id c7_4 run TdCellClick(player, 7, 4) keep open
		image "td:textures/gui/tile_build.png" at -23 3 size 22x22 texture 28x28 anchor center layer 3 id c9_4 run TdCellClick(player, 9, 4) keep open
		image "td:textures/gui/tile_build.png" at -1 3 size 22x22 texture 28x28 anchor center layer 3 id c10_4 run TdCellClick(player, 10, 4) keep open
		image "td:textures/gui/tile_build.png" at -177 25 size 22x22 texture 28x28 anchor center layer 3 id c2_5 run TdCellClick(player, 2, 5) keep open
		image "td:textures/gui/tile_build.png" at -155 25 size 22x22 texture 28x28 anchor center layer 3 id c3_5 run TdCellClick(player, 3, 5) keep open
		image "td:textures/gui/tile_build.png" at -23 25 size 22x22 texture 28x28 anchor center layer 3 id c9_5 run TdCellClick(player, 9, 5) keep open
		image "td:textures/gui/tile_build.png" at -1 25 size 22x22 texture 28x28 anchor center layer 3 id c10_5 run TdCellClick(player, 10, 5) keep open
		image "td:textures/gui/tile_build.png" at 21 25 size 22x22 texture 28x28 anchor center layer 3 id c11_5 run TdCellClick(player, 11, 5) keep open
		image "td:textures/gui/tile_build.png" at 43 25 size 22x22 texture 28x28 anchor center layer 3 id c12_5 run TdCellClick(player, 12, 5) keep open
		image "td:textures/gui/tile_build.png" at 65 25 size 22x22 texture 28x28 anchor center layer 3 id c13_5 run TdCellClick(player, 13, 5) keep open
		image "td:textures/gui/tile_build.png" at 87 25 size 22x22 texture 28x28 anchor center layer 3 id c14_5 run TdCellClick(player, 14, 5) keep open
		image "td:textures/gui/tile_build.png" at 109 25 size 22x22 texture 28x28 anchor center layer 3 id c15_5 run TdCellClick(player, 15, 5) keep open
		image "td:textures/gui/tile_build.png" at -155 47 size 22x22 texture 28x28 anchor center layer 3 id c3_6 run TdCellClick(player, 3, 6) keep open
		image "td:textures/gui/tile_build.png" at -133 47 size 22x22 texture 28x28 anchor center layer 3 id c4_6 run TdCellClick(player, 4, 6) keep open
		image "td:textures/gui/tile_build.png" at -111 47 size 22x22 texture 28x28 anchor center layer 3 id c5_6 run TdCellClick(player, 5, 6) keep open
		image "td:textures/gui/tile_build.png" at -89 47 size 22x22 texture 28x28 anchor center layer 3 id c6_6 run TdCellClick(player, 6, 6) keep open
		image "td:textures/gui/tile_build.png" at -67 47 size 22x22 texture 28x28 anchor center layer 3 id c7_6 run TdCellClick(player, 7, 6) keep open
		image "td:textures/gui/tile_build.png" at -45 47 size 22x22 texture 28x28 anchor center layer 3 id c8_6 run TdCellClick(player, 8, 6) keep open
		image "td:textures/gui/tile_build.png" at 21 47 size 22x22 texture 28x28 anchor center layer 3 id c11_6 run TdCellClick(player, 11, 6) keep open
		image "td:textures/gui/tile_build.png" at 43 47 size 22x22 texture 28x28 anchor center layer 3 id c12_6 run TdCellClick(player, 12, 6) keep open
		image "td:textures/gui/tile_build.png" at 65 47 size 22x22 texture 28x28 anchor center layer 3 id c13_6 run TdCellClick(player, 13, 6) keep open
		image "td:textures/gui/tile_build.png" at -155 69 size 22x22 texture 28x28 anchor center layer 3 id c3_7 run TdCellClick(player, 3, 7) keep open
		image "td:textures/gui/tile_build.png" at -133 69 size 22x22 texture 28x28 anchor center layer 3 id c4_7 run TdCellClick(player, 4, 7) keep open
		image "td:textures/gui/tile_build.png" at -45 69 size 22x22 texture 28x28 anchor center layer 3 id c8_7 run TdCellClick(player, 8, 7) keep open
		image "td:textures/gui/tile_build.png" at -1 69 size 22x22 texture 28x28 anchor center layer 3 id c10_7 run TdCellClick(player, 10, 7) keep open
		image "td:textures/gui/tile_build.png" at 21 69 size 22x22 texture 28x28 anchor center layer 3 id c11_7 run TdCellClick(player, 11, 7) keep open
		image "td:textures/gui/tile_build.png" at 43 69 size 22x22 texture 28x28 anchor center layer 3 id c12_7 run TdCellClick(player, 12, 7) keep open
		image "td:textures/gui/tile_build.png" at 65 69 size 22x22 texture 28x28 anchor center layer 3 id c13_7 run TdCellClick(player, 13, 7) keep open
		image "td:textures/gui/tile_build.png" at 87 69 size 22x22 texture 28x28 anchor center layer 3 id c14_7 run TdCellClick(player, 14, 7) keep open
		image "td:textures/gui/tile_build.png" at 109 69 size 22x22 texture 28x28 anchor center layer 3 id c15_7 run TdCellClick(player, 15, 7) keep open

		# Пул звёзд уровней и эволюций (до 32 башен)
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st1 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st2 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st3 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st4 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st5 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st6 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st7 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st8 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st9 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st10 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st11 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st12 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st13 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st14 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st15 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st16 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st17 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st18 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st19 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st20 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st21 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st22 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st23 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st24 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st25 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st26 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st27 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st28 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st29 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st30 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st31 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st32 hidden

		# 11 слотов обычных врагов + 1 слот Босса + тонкие HP-бары (64x64 HD + 60 FPS tween)
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e1 hidden
		bar "ehp1" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e2 hidden
		bar "ehp2" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e3 hidden
		bar "ehp3" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e4 hidden
		bar "ehp4" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e5 hidden
		bar "ehp5" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e6 hidden
		bar "ehp6" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e7 hidden
		bar "ehp7" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e8 hidden
		bar "ehp8" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e9 hidden
		bar "ehp9" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e10 hidden
		bar "ehp10" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e11 hidden
		bar "ehp11" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_boss.png" at -221 -63 size 28x28 texture 64x64 anchor center layer 8 id e12 hidden
		bar "ehp12" value 100 max 100 at -221 -79 size 26x4 anchor center color #FFEF4444 background #CC1A0A0A layer 9 hidden

		# 6 летающих HD прожектайлов (60 FPS tween)
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr1 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr2 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr3 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr4 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr5 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr6 hidden

		# Компактная строка статуса
		box at 0 92 size 468x13 anchor center color #E60B1220 border #3338BDF8 radius 3 layer 11
		text "&e[1] Баллиста &7— наведите на башню для радиуса и статов, ЛКМ по клетке — строить" at 0 92 anchor center shadow layer 12 id status_txt
		text "&c&lБАЗА ПАЛА!" at -56 -18 anchor center shadow scale 1.4 layer 20 id end_title hidden
		text "&fНажми &eR &f(рестарт) или &eM &f(меню)" at -56 2 anchor center shadow layer 20 id end_sub hidden

		# Нижняя панель: 9 HD башен (включая Маяк-Баффер #9) + управление и тумблер Курсора
		box at 0 121 size 468x40 anchor center color #EE090E1A gradient #EE111827 border #FF1E293B radius 6 shadow blur layer 11
		box at -218 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -186 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -154 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -122 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -90 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -58 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -26 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at 6 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at 38 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -218 106 size 26x2 anchor center color #FFFACC15 radius 1 id sel_bar hidden layer 13
		image "td:textures/gui/tower_archer.png" at -218 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e&l[1] Эльфийская Баллиста &650з%nl%&8▪ &7Урон: &f15 &8| &7КД: &a0.4с &8| &7Дальн: &b78%nl%&6✦ 5★ Шквал: &fстреляет сразу по 2 целям"
		text "&e[1]50" at -218 131 anchor center shadow scale 0.85 layer 12 id lbl1
		image "td:textures/gui/tower_cannon.png" at -186 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6&l[2] Мортира «Вулкан» &695з%nl%&8▪ &7Урон: &f36 &c(Сплеш) &8| &7Дальн: &b74%nl%&6✦ 5★ Напалм: &fмега-сплеш + поджог врагов"
		text "&7[2]95" at -186 131 anchor center shadow scale 0.85 layer 12 id lbl2
		image "td:textures/gui/tower_frost.png" at -154 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b&l[3] Крио-Обелиск &685з%nl%&8▪ &7Аура: &f8 ур. &b(40% замедл. всех в радиусе 76)%nl%&6✦ 5★ Стужа: &f50% замедл. + 12% заморозка"
		text "&7[3]85" at -154 131 anchor center shadow scale 0.85 layer 12 id lbl3
		image "td:textures/gui/tower_mage.png" at -122 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d&l[4] Аркан-Шпиль &6130з%nl%&8▪ &7Урон: &f32 &8| &7Дальн: &b106%nl%&6✦ 5★ Убийца Боссов: &fx2 Боссам + 4% макс.HP"
		text "&7[4]130" at -122 131 anchor center shadow scale 0.85 layer 12 id lbl4
		image "td:textures/gui/tower_tesla.png" at -90 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico5 run TdSelectTool(player, 5) keep open tooltip "&b&l[5] Плазмо-Тесла &6115з%nl%&8▪ &7Урон: &f20 &b(Цепь на 3 цели) &8| &7Дальн: &b86%nl%&6✦ 5★ Шторм: &f5 целей + 90% урон + микро-стан"
		text "&7[5]115" at -90 131 anchor center shadow scale 0.85 layer 12 id lbl5
		image "td:textures/gui/tower_sniper.png" at -58 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico6 run TdSelectTool(player, 6) keep open tooltip "&c&l[6] Рейлган «Сокол» &6150з%nl%&8▪ &7Урон: &f56 &c(Крит 25%) &8| &7Дальн: &b172%nl%&6✦ 5★ Казнь: &fкрит 45% x2.5 + добивание <20% HP"
		text "&7[6]150" at -58 131 anchor center shadow scale 0.85 layer 12 id lbl6
		image "td:textures/gui/tower_poison.png" at -26 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico7 run TdSelectTool(player, 7) keep open tooltip "&a&l[7] Чумной Реактор &6105з%nl%&8▪ &7Урон: &f12 + &aКислота (DOT) &8| &7Дальн: &b80%nl%&6✦ 5★ Чума: &fзаражает всех врагов рядом"
		text "&7[7]105" at -26 131 anchor center shadow scale 0.85 layer 12 id lbl7
		image "td:textures/gui/tower_farm.png" at 6 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico8 run TdSelectTool(player, 8) keep open tooltip "&6&l[8] Монетный Двор &6140з%nl%&8▪ &7Доход: &6+28/+50/+80/+120з &7за волну%nl%&6✦ 5★ Золотой Дворец: &f+180з и +1 HP базе"
		text "&7[8]140" at 6 131 anchor center shadow scale 0.85 layer 12 id lbl8
		image "td:textures/gui/tower_buffer.png" at 38 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico9 run TdSelectTool(player, 9) keep open tooltip "&d&l[9] Боевой Маяк (Баффер) &6125з%nl%&8▪ &7Бафф-аура: &d+18..42% урона &7башням в радиусе 68%nl%&6✦ 5★ Знамя Империи: &f+55% урон, +20% скоростр. и +25з"
		text "&7[9]125" at 38 131 anchor center shadow scale 0.85 layer 12 id lbl9
		button "&aСтарт/x2" at 94 112 size 56x15 anchor center run TdWaveOrSpeed(player) keep open tooltip "&aСтарт волны / Скорость x1↔x2 &8[Space]"
		toggle "Авто" id autowave at 152 112 size 52x15 anchor center value false color #22C55E tooltip "&aАвто-старт волн"
		toggle "Курсор" id cursortog at 208 112 size 52x15 anchor center value true color #38BDF8 tooltip "&bИконка выбранной башни у курсора &8[C]"
		button "&cПродать" at 94 129 size 56x15 anchor center run TdToggleSell(player) keep open tooltip "&cПродать башню за 70% &8[X]"
		button "Заново" at 152 129 size 52x15 anchor center run TdRestartCurrent(player) keep open tooltip "&eРестарт карты &8[R]"
		button "Меню" at 208 129 size 52x15 anchor center run TdOpenMenu(player) keep open tooltip "&eВыбор карты и кооп &8[M]"

		# Привязанная к курсору иконка текущей башни (универсальное свойство follow cursor)
		image "td:textures/gui/tower_archer.png" at 0 0 size 15x15 texture 64x64 anchor center follow cursor layer 24 id cursor_icon hidden

# ==========================================================
#  УПРАВЛЕНИЕ, СТРОИТЕЛЬСТВО (ДО 32 БАШЕН), МАЯК-БАФФЕР И ✦5★
# ==========================================================

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

function TdClearTool(p: player):
	set {-g} to {-td-host::%p%}
	if {-g} is not set or {-td-active::%{-g}%} is not 1:
		stop
	set {-td-tool::%p%} to 0
	set {-td-sell::%p%} to 0
	hide screen element "cursor_icon" from p
	hide screen element "sel_bar" from p
	TdShowRange(p, 0, 0, 0, 0)
	set text of screen element "lbl1" for p to "&7[1]50"
	set text of screen element "lbl2" for p to "&7[2]95"
	set text of screen element "lbl3" for p to "&7[3]85"
	set text of screen element "lbl4" for p to "&7[4]130"
	set text of screen element "lbl5" for p to "&7[5]115"
	set text of screen element "lbl6" for p to "&7[6]150"
	set text of screen element "lbl7" for p to "&7[7]105"
	set text of screen element "lbl8" for p to "&7[8]140"
	set text of screen element "lbl9" for p to "&7[9]125"

function TdSelectTool(p: player, tool: number):
	set {-g} to {-td-host::%p%}
	if {-g} is not set or {-td-active::%{-g}%} is not 1:
		stop
	set {-td-tool::%p%} to tool
	set {-td-sell::%p%} to 0
	set text of screen element "lbl1" for p to "&7[1]50"
	set text of screen element "lbl2" for p to "&7[2]95"
	set text of screen element "lbl3" for p to "&7[3]85"
	set text of screen element "lbl4" for p to "&7[4]130"
	set text of screen element "lbl5" for p to "&7[5]115"
	set text of screen element "lbl6" for p to "&7[6]150"
	set text of screen element "lbl7" for p to "&7[7]105"
	set text of screen element "lbl8" for p to "&7[8]140"
	set text of screen element "lbl9" for p to "&7[9]125"
	set {-sx} to -218 + (tool - 1) * 32
	show screen element "sel_bar" to p
	animate screen "sel_bar" for p to {-sx} 106 in 3 ticks
	set {-ctex} to "td:textures/gui/tower_archer.png"
	if tool is 1:
		set text of screen element "lbl1" for p to "&e[1]50"
		set text of screen element "status_txt" for p to "&e[1] Эльфийская Баллиста (50з) &8| &6✦5★: &fШквал по 2 целям"
	else if tool is 2:
		set {-ctex} to "td:textures/gui/tower_cannon.png"
		set text of screen element "lbl2" for p to "&6[2]95"
		set text of screen element "status_txt" for p to "&6[2] Мортира «Вулкан» (95з) &8| &6✦5★: &fНапалм (сплеш + ожог)"
	else if tool is 3:
		set {-ctex} to "td:textures/gui/tower_frost.png"
		set text of screen element "lbl3" for p to "&b[3]85"
		set text of screen element "status_txt" for p to "&b[3] Крио-Обелиск (85з) &8| Аура холода | &6✦5★: &fСтужа (50%% + фриз)"
	else if tool is 4:
		set {-ctex} to "td:textures/gui/tower_mage.png"
		set text of screen element "lbl4" for p to "&d[4]130"
		set text of screen element "status_txt" for p to "&d[4] Аркан-Шпиль (130з) &8| &6✦5★: &fУбийца Боссов (x2 + 4%% HP)"
	else if tool is 5:
		set {-ctex} to "td:textures/gui/tower_tesla.png"
		set text of screen element "lbl5" for p to "&b[5]115"
		set text of screen element "status_txt" for p to "&b[5] Плазмо-Тесла (115з) &8| &6✦5★: &fШторм (5 целей + стан)"
	else if tool is 6:
		set {-ctex} to "td:textures/gui/tower_sniper.png"
		set text of screen element "lbl6" for p to "&c[6]150"
		set text of screen element "status_txt" for p to "&c[6] Рейлган «Сокол» (150з) &8| &6✦5★: &fКазнь (<20%% HP + крит 45%%)"
	else if tool is 7:
		set {-ctex} to "td:textures/gui/tower_poison.png"
		set text of screen element "lbl7" for p to "&a[7]105"
		set text of screen element "status_txt" for p to "&a[7] Чумной Реактор (105з) &8| &6✦5★: &fЧума (заразная кислота)"
	else if tool is 8:
		set {-ctex} to "td:textures/gui/tower_farm.png"
		set text of screen element "lbl8" for p to "&6[8]140"
		set text of screen element "status_txt" for p to "&6[8] Монетный Двор (140з) &8| &6✦5★: &fДворец (+180з и +1 HP базе)"
	else if tool is 9:
		set {-ctex} to "td:textures/gui/tower_buffer.png"
		set text of screen element "lbl9" for p to "&d[9]125"
		set text of screen element "status_txt" for p to "&d[9] Боевой Маяк (125з) &8| Бафф +18..55%% урона соседним башням!"
	set image of screen element "cursor_icon" for p to {-ctex}
	if {-td-cur-on::%p%} is not 0:
		show screen element "cursor_icon" to p
	play sound ui.button.click to p with volume 0.4 pitch 1.3

function TdToggleSell(p: player):
	set {-g} to {-td-host::%p%}
	if {-g} is not set or {-td-active::%{-g}%} is not 1:
		stop
	if {-td-sell::%p%} is 1:
		TdClearTool(p)
	else:
		set {-td-sell::%p%} to 1
		set {-td-tool::%p%} to 0
		hide screen element "sel_bar" from p
		TdShowRange(p, 0, 0, 0, 0)
		set image of screen element "cursor_icon" for p to "td:textures/gui/icon_sell.png"
		if {-td-cur-on::%p%} is not 0:
			show screen element "cursor_icon" to p
		set text of screen element "status_txt" for p to "&c&lРЕЖИМ ПРОДАЖИ &7— клик по своей башне вернёт 70%% золота (X — отмена)"
		play sound ui.button.click to p with volume 0.4 pitch 0.9

function TdWaveOrSpeed(p: player):
	set {-g} to {-td-host::%p%}
	if {-g} is not set or {-td-active::%{-g}%} is not 1 or {-td-over::%{-g}%} is 1:
		stop
	if {-td-in-wave::%{-g}%} is 1:
		if {-td-speed::%{-g}%} is 1:
			set {-td-speed::%{-g}%} to 2
			TdLog({-g}, "&b⏩ Скорость: x2")
		else:
			set {-td-speed::%{-g}%} to 1
			TdLog({-g}, "&7▶ Скорость: x1")
		TdSyncSound({-g}, "ui.button.click", 0.4, 1.5)
		TdSyncHud({-g})
		stop
	TdNextWave({-g})

function TdTowerRange(g: player, tt: number, lvl: number):
	# Радиус в масштабе карты 352x176 (клетка 22x22) + бонус +12% на карте 3 (Цитадель)
	set {-r} to 78
	if tt is 2:
		set {-r} to 74
	else if tt is 3:
		set {-r} to 76
	else if tt is 4:
		set {-r} to 106
	else if tt is 5:
		set {-r} to 86
	else if tt is 6:
		set {-r} to 172
	else if tt is 7:
		set {-r} to 80
	else if tt is 8:
		set {-r} to 0
	else if tt is 9:
		set {-r} to 68
	if {-r} > 0:
		set {-r} to {-r} + (lvl - 1) * 7
		if lvl >= 5:
			add 11 to {-r}
		if {-td-map::%g%} is 3:
			set {-r} to round({-r} * 1.12)
	set {-td-tmp-rng} to {-r}

function TdCellClick(p: player, col: integer, row: integer):
	set {-g} to {-td-host::%p%}
	if {-g} is not set or {-td-active::%{-g}%} is not 1 or {-td-over::%{-g}%} is 1:
		stop
	if {-td-blk::%{-g}%::%col%_%row%} is 1:
		set text of screen element "status_txt" for p to "&cЗдесь опасная зона (топь / разлом) — строить нельзя!"
		play sound entity.villager.no to p with volume 0.4 pitch 0.9
		stop
	set {-tid} to {-td-grid::%{-g}%::%col%_%row%}
	if {-tid} is not set:
		set {-tid} to 0

	# Продажа
	if {-td-sell::%p%} is 1:
		if {-tid} is 0:
			set text of screen element "status_txt" for p to "&7Клетка пуста"
			stop
		set {-own} to {-td-towner::%{-g}%::%{-tid}%}
		if {-own} is not %p% and %p% is not {-g}:
			set text of screen element "status_txt" for p to "&cЭто башня игрока &f%{-own}%&c!"
			stop
		set {-tx} to {-td-tx::%{-g}%::%{-tid}%}
		set {-ty} to {-td-ty::%{-g}%::%{-tid}%}
		set {-ref} to round({-td-tcost::%{-g}%::%{-tid}%} * 0.7)
		add {-ref} to {-td-gold::%p%}
		set {-td-ta::%{-g}%::%{-tid}%} to 0
		set {-td-tt::%{-g}%::%{-tid}%} to 0
		set {-td-tl::%{-g}%::%{-tid}%} to 0
		set {-td-tcost::%{-g}%::%{-tid}%} to 0
		set {-td-towner::%{-g}%::%{-tid}%} to ""
		set {-td-grid::%{-g}%::%col%_%row%} to 0
		set {-td-sell::%p%} to 0
		TdClearTool(p)
		TdSyncCell({-g}, col, row, {-tx}, {-ty}, "td:textures/gui/tile_build.png", "", 0, "#38BDF8")
		TdSyncHide({-g}, "st%{-tid}%")
		TdRecalcBuffs({-g})
		gui float "&6+%{-ref}%з" at {-tx} {-ty} color "#FACC15" for 16 ticks to {-td-online::%{-g}%::*}
		gui particle "gold" count 8 at {-tx} {-ty} spread 22 to {-td-online::%{-g}%::*}
		TdLog({-g}, "&c✖ %p% &7(+%{-ref}%з)")
		TdSyncSound({-g}, "entity.experience_orb.pickup", 0.6, 0.9)
		TdSyncGoldScore({-g})
		stop

	# Клик по построенной башне -> апгрейд / эволюция! (радиус показывается сам при наведении — по клику скрываем старый)
	if {-tid} > 0:
		TdShowRange(p, 0, 0, 0, 0)
		set {-lvl} to {-td-tl::%{-g}%::%{-tid}%}
		set {-tt} to {-td-tt::%{-g}%::%{-tid}%}
		set {-own} to {-td-towner::%{-g}%::%{-tid}%}
		set {-tx} to {-td-tx::%{-g}%::%{-tid}%}
		set {-ty} to {-td-ty::%{-g}%::%{-tid}%}
		TdTowerRange({-g}, {-tt}, {-lvl})

		if {-lvl} >= 5:
			set text of screen element "status_txt" for p to "&6&l✦5★ MAX &8| &7Владелец: &f%{-own}% &8| &7Дальность: &b%{-td-tmp-rng}%"
			stop
		set {-up} to 95
		if {-lvl} is 2:
			set {-up} to 210
		else if {-lvl} is 3:
			set {-up} to 360
		else if {-lvl} is 4:
			set {-up} to 570
		if {-td-gold::%p%} < {-up}:
			set {-nl} to {-lvl} + 1
			set text of screen element "status_txt" for p to "&cНужно &6%{-up}%з &cдля улучшения до ур.%{-nl}%!"
			play sound entity.villager.no to p with volume 0.5 pitch 1.0
			stop
		remove {-up} from {-td-gold::%p%}
		add {-up} to {-td-tcost::%{-g}%::%{-tid}%}
		add 1 to {-td-tl::%{-g}%::%{-tid}%}
		set {-nl} to {-td-tl::%{-g}%::%{-tid}%}
		TdRecalcBuffs({-g})
		if {-nl} < 5:
			gui particle "magic" count 10 at {-tx} {-ty} color "#38BDF8" spread 24 to {-td-online::%{-g}%::*}
			TdLog({-g}, "&b⬆ %p% &f→ Ур.%{-nl}%")
			TdSyncSound({-g}, "entity.player.levelup", 0.5, 1.45)
		else:
			set {-evo} to "Шквал 5★"
			if {-tt} is 2:
				set {-evo} to "Напалм 5★"
			else if {-tt} is 3:
				set {-evo} to "Стужа 5★"
			else if {-tt} is 4:
				set {-evo} to "Аркан 5★"
			else if {-tt} is 5:
				set {-evo} to "Шторм 5★"
			else if {-tt} is 6:
				set {-evo} to "Казнь 5★"
			else if {-tt} is 7:
				set {-evo} to "Чума 5★"
			else if {-tt} is 8:
				set {-evo} to "Дворец 5★"
			else if {-tt} is 9:
				set {-evo} to "Знамя 5★"
			gui float "&6&l✦ %{-evo}%!" at {-tx} {-ty} color "#FACC15" for 24 ticks to {-td-online::%{-g}%::*}
			gui particle "gold" count 18 at {-tx} {-ty} spread 38 to {-td-online::%{-g}%::*}
			TdLog({-g}, "&6✦ %p%: &e%{-evo}%")
			TdSyncSound({-g}, "ui.toast.challenge_complete", 0.75, 1.2)
		TdSyncGoldScore({-g})
		stop

	# Постройка новой башни (до 32 на карте; после постройки выбор сбрасывается!)
	set {-tool} to {-td-tool::%p%}
	if {-tool} is not set or {-tool} <= 0:
		TdClearTool(p)
		set text of screen element "status_txt" for p to "&7Сначала выберите башню &e1..9 &7на панели внизу!"
		stop
	set {-slot} to 0
	loop 32 times:
		set {-k} to %loop-number%
		if {-td-ta::%{-g}%::%{-k}%} is 0 and {-slot} is 0:
			set {-slot} to {-k}
	if {-slot} is 0:
		set text of screen element "status_txt" for p to "&cЛимит 32 башни! Прокачивайте построенные до ✦5★"
		stop

	set {-cost} to 50
	set {-sn} to "Баллиста"
	if {-tool} is 2:
		set {-cost} to 95
		set {-sn} to "Мортира"
	else if {-tool} is 3:
		set {-cost} to 85
		set {-sn} to "Обелиск"
	else if {-tool} is 4:
		set {-cost} to 130
		set {-sn} to "Аркан"
	else if {-tool} is 5:
		set {-cost} to 115
		set {-sn} to "Тесла"
	else if {-tool} is 6:
		set {-cost} to 150
		set {-sn} to "Рейлган"
	else if {-tool} is 7:
		set {-cost} to 105
		set {-sn} to "Реактор"
	else if {-tool} is 8:
		set {-cost} to 140
		set {-sn} to "Банк"
	else if {-tool} is 9:
		set {-cost} to 125
		set {-sn} to "Маяк"

	if {-td-gold::%p%} < {-cost}:
		TdShowRange(p, 0, 0, 0, 0)
		set text of screen element "status_txt" for p to "&cНе хватает золота! Нужно &6%{-cost}%з"
		play sound entity.villager.no to p with volume 0.5 pitch 1.0
		stop

	remove {-cost} from {-td-gold::%p%}
	set {-cx} to -221 + col * 22
	set {-cy} to -85 + row * 22
	set {-td-ta::%{-g}%::%{-slot}%} to 1
	set {-td-tt::%{-g}%::%{-slot}%} to {-tool}
	set {-td-tl::%{-g}%::%{-slot}%} to 1
	set {-td-tcd::%{-g}%::%{-slot}%} to 0
	set {-td-tcost::%{-g}%::%{-slot}%} to {-cost}
	set {-td-tbuff::%{-g}%::%{-slot}%} to 1.0
	set {-td-tfast::%{-g}%::%{-slot}%} to 0
	set {-td-towner::%{-g}%::%{-slot}%} to %p%
	set {-td-tcol::%{-g}%::%{-slot}%} to col
	set {-td-trow::%{-g}%::%{-slot}%} to row
	set {-td-tx::%{-g}%::%{-slot}%} to {-cx}
	set {-td-ty::%{-g}%::%{-slot}%} to {-cy}
	set {-td-grid::%{-g}%::%col%_%row%} to {-slot}
	if {-slot} > {-td-tmax::%{-g}%}:
		set {-td-tmax::%{-g}%} to {-slot}

	TdRecalcBuffs({-g})
	# Сразу отвязываем иконку от курсора и сбрасываем выбор после 1 постройки!
	TdClearTool(p)
	gui particle "spark" count 8 at {-cx} {-cy} color "#38BDF8" spread 20 to {-td-online::%{-g}%::*}
	TdLog({-g}, "&a+ %p%: &f%{-sn}%")
	TdSyncSound({-g}, "block.anvil.use", 0.35, 1.5)
	TdSyncGoldScore({-g})

# ==========================================================
#  ВОЛНЫ И СПАВН (16 ВИДОВ МОБОВ + ОСОБЕННОСТИ 5 КАРТ)
# ==========================================================

function TdNextWave(g: player):
	if {-td-wave::%g%} >= {-td-maxw::%g%}:
		stop
	add 1 to {-td-wave::%g%}
	set {-w} to {-td-wave::%g%}
	set {-mw} to {-td-maxw::%g%}
	set {-td-in-wave::%g%} to 1
	set {-td-auto-cd::%g%} to 0
	set {-td-spawn-cd::%g%} to 1
	set {-td-spawn-boss::%g%} to 0

	set {-pcnt} to size of {-td-team::%g%::*}
	set {-cnt} to 5 + round({-w} * 0.65) + ({-pcnt} - 1) * 2
	set {-td-to-spawn::%g%} to {-cnt}

	set {-rem5} to {-w} % 5
	if {-rem5} is 0:
		set {-bkind} to round({-w} / 5)
		set {-td-spawn-boss::%g%} to {-bkind}
		if {-w} is {-mw}:
			TdLog(g, "&4☠ БОСС: Владыка Края!")
			TdSyncSound(g, "entity.wither.spawn", 0.55, 0.85)
		else if {-bkind} >= 3:
			TdLog(g, "&b☠ БОСС: Император Стужи!")
			TdSyncSound(g, "entity.ender_dragon.growl", 0.55, 0.9)
		else:
			TdLog(g, "&c☠ БОСС: Архидемон!")
			TdSyncSound(g, "entity.ender_dragon.growl", 0.5, 1.1)
	else:
		TdLog(g, "&e⚔ Волна %{-w}%/%{-mw}%")
		TdSyncSound(g, "entity.experience_orb.pickup", 0.5, 1.25)
	TdSyncHud(g)

# ==========================================================
#  ОСНОВНОЙ ТИК ИГРЫ (5 ТИКОВ/СЕК x1, 10 ТИКОВ/СЕК x2 + 60 FPS TWEEN)
# ==========================================================

function TdTick(g: player):
	if {-td-over::%g%} is 1:
		stop

	if {-td-in-wave::%g%} is 0:
		if {-td-auto::%g%} is 1 and {-td-wave::%g%} > 0 and {-td-wave::%g%} < {-td-maxw::%g%}:
			add 1 to {-td-auto-cd::%g%}
			if {-td-auto-cd::%g%} >= 8:
				TdNextWave(g)
		stop

	set {-anim} to 4
	if {-td-speed::%g%} is 2:
		set {-anim} to 2

	# 1. Спавн врагов
	if {-td-to-spawn::%g%} > 0 or {-td-spawn-boss::%g%} > 0:
		remove 1 from {-td-spawn-cd::%g%}
		if {-td-spawn-cd::%g%} <= 0:
			TdSpawnNext(g)

	# 2. Враги: регенерация, горение, яд, плавное движение (tween)
	set {-boss-alive} to 0
	loop 12 times:
		set {-ei} to %loop-number%
		if {-td-ea::%g%::%{-ei}%} is 1:
			if {-td-eregen::%g%::%{-ei}%} > 0:
				if {-td-ehp::%g%::%{-ei}%} < {-td-emhp::%g%::%{-ei}%}:
					add {-td-eregen::%g%::%{-ei}%} to {-td-ehp::%g%::%{-ei}%}
					TdSyncBar(g, "ehp%{-ei}%", {-td-ehp::%g%::%{-ei}%}, 0)
			if {-td-eburn::%g%::%{-ei}%} > 0:
				remove 1 from {-td-eburn::%g%::%{-ei}%}
				TdDamageEnemy(g, {-ei}, {-td-eburn-d::%g%::%{-ei}%}, 0, 0, 1)
			if {-td-ea::%g%::%{-ei}%} is 1 and {-td-epoison::%g%::%{-ei}%} > 0:
				remove 1 from {-td-epoison::%g%::%{-ei}%}
				TdDamageEnemy(g, {-ei}, {-td-epoison-d::%g%::%{-ei}%}, 0, 0, 1)
			if {-td-ea::%g%::%{-ei}%} is 1:
				TdMoveEnemy(g, {-ei}, {-anim})
			if {-td-ea::%g%::%{-ei}%} is 1 and {-td-eboss::%g%::%{-ei}%} is 1:
				set {-boss-alive} to 1
				TdSyncBossBar(g, {-td-ehp::%g%::%{-ei}%}, 0)
	if {-boss-alive} is 0 and {-td-boss-vis::%g%} is 1:
		set {-td-boss-vis::%g%} to 0
		TdSyncHide(g, "boss_bar")

	# 3. Полёт прожектайлов
	if {-td-prcnt::%g%} > 0:
		TdMoveProjectiles(g, {-anim})

	# 4. Стрельба всех башен (кроме Банка #8 и Маяка #9)
	set {-tmax} to {-td-tmax::%g%}
	if {-tmax} > 0:
		loop {-tmax} times:
			set {-ti} to %loop-number%
			if {-td-ta::%g%::%{-ti}%} is 1 and {-td-tt::%g%::%{-ti}%} < 8:
				if {-td-tcd::%g%::%{-ti}%} > 0:
					remove 1 from {-td-tcd::%g%::%{-ti}%}
				else:
					TdTowerTick(g, {-ti})

	# 5. Завершение волны + доход от Банков (#8) и ✦5★ Маяков (#9)!
	if {-td-to-spawn::%g%} <= 0 and {-td-spawn-boss::%g%} <= 0:
		set {-rem} to 0
		loop 12 times:
			set {-k} to %loop-number%
			if {-td-ea::%g%::%{-k}%} is 1:
				add 1 to {-rem}
		if {-rem} is 0:
			set {-td-in-wave::%g%} to 0
			set {-td-auto-cd::%g%} to 0
			TdClearProjectiles(g)
			set {-bonus} to 18 + {-td-wave::%g%} * 6
			loop {-td-team::%g%::*}:
				set {-tp} to %loop-value%
				add {-bonus} to {-td-gold::%{-tp}%}
			set {-bank-tot} to 0
			if {-tmax} > 0:
				loop {-tmax} times:
					set {-bi} to %loop-number%
					if {-td-ta::%g%::%{-bi}%} is 1:
						if {-td-tt::%g%::%{-bi}%} is 8:
							set {-bl} to {-td-tl::%g%::%{-bi}%}
							set {-inc} to 28
							if {-bl} is 2:
								set {-inc} to 50
							else if {-bl} is 3:
								set {-inc} to 80
							else if {-bl} is 4:
								set {-inc} to 120
							else if {-bl} >= 5:
								set {-inc} to 180
								add 1 to {-td-hp::%g%}
							set {-bo} to {-td-towner::%g%::%{-bi}%}
							add {-inc} to {-td-gold::%{-bo}%}
							add {-inc} to {-bank-tot}
						else if {-td-tt::%g%::%{-bi}%} is 9 and {-td-tl::%g%::%{-bi}%} >= 5:
							set {-bo} to {-td-towner::%g%::%{-bi}%}
							add 25 to {-td-gold::%{-bo}%}
							add 25 to {-bank-tot}
			set {-sc-add} to 100 + {-td-wave::%g%} * 15
			if {-td-map::%g%} is 5:
				set {-sc-add} to round({-sc-add} * 1.5)
			add {-sc-add} to {-td-score::%g%}
			TdSyncHud(g)
			if {-td-wave::%g%} >= {-td-maxw::%g%}:
				TdWin(g)
			else:
				if {-bank-tot} > 0:
					gui float "&6+%{-bank-tot}%з Казна!" at -56 -30 color "#FACC15" for 24 ticks to {-td-online::%g%::*}
					TdLog(g, "&6💰 Казна: +%{-bank-tot}%з")
				TdLog(g, "&a✔ Волна %{-td-wave::%g%}% (+%{-bonus}%з)")
				TdSyncSound(g, "entity.player.levelup", 0.5, 1.25)

function TdSpawnNext(g: player):
	set {-w} to {-td-wave::%g%}
	set {-d} to {-td-diff::%g%}
	set {-m} to {-td-map::%g%}
	set {-dmul} to 1.0
	if {-d} is 1:
		set {-dmul} to 0.85
	else if {-d} is 3:
		set {-dmul} to 1.25
	if {-m} is 4:
		set {-dmul} to {-dmul} * 1.15
	else if {-m} is 5:
		set {-dmul} to {-dmul} * 1.2
	set {-pcnt} to size of {-td-team::%g%::*}
	if {-pcnt} is 2:
		set {-dmul} to {-dmul} * 1.45
	else if {-pcnt} is 3:
		set {-dmul} to {-dmul} * 1.95
	else if {-pcnt} >= 4:
		set {-dmul} to {-dmul} * 2.5

	set {-sx} to {-td-wpx::%g%::1}
	set {-sy} to {-td-wpy::%g%::1}

	if {-td-to-spawn::%g%} > 0:
		set {-slot} to 0
		loop 11 times:
			set {-k} to %loop-number%
			if {-td-ea::%g%::%{-k}%} is 0 and {-slot} is 0:
				set {-slot} to {-k}
		if {-slot} is 0:
			set {-td-spawn-cd::%g%} to 1
			stop
		remove 1 from {-td-to-spawn::%g%}

		# Шаг адаптирован под клетку 22x22 и 4-tick цикл (6.5 px за шаг + 60 FPS tween!)
		set {-hp} to round((35 + {-w} * 25 + {-w} * {-w} * 2.6) * {-dmul})
		set {-spd} to 6.4 + {-w} * 0.14
		if {-m} is 2:
			set {-spd} to {-spd} * 1.08
		else if {-m} is 5:
			set {-spd} to {-spd} * 1.15
		set {-rew} to 10 + round({-w} * 0.65)
		if {-m} is 4:
			set {-rew} to round({-rew} * 1.2)
		set {-cd} to 4 - floor({-w} / 10)
		if {-cd} < 2:
			set {-cd} to 2
		set {-tex} to "td:textures/gui/mob_slime.png"
		set {-reg} to 0
		set {-imm} to 0
		set {-bdmg} to 1
		set {-dodge} to 0
		set {-shield} to 0

		# 13 видов обычных и элитных врагов (включая Ассасина, Храмовника и Вестника Бездны!)
		set {-m13} to {-w} % 13
		if {-m13} is 2:
			set {-tex} to "td:textures/gui/mob_goblin.png"
			set {-hp} to round({-hp} * 0.72)
			set {-spd} to {-spd} + 2.6
			set {-cd} to 2
		else if {-m13} is 3:
			set {-tex} to "td:textures/gui/mob_orc.png"
			set {-hp} to round({-hp} * 1.18)
		else if {-m13} is 4:
			set {-tex} to "td:textures/gui/mob_spider.png"
			set {-hp} to round({-hp} * 0.82)
			set {-spd} to {-spd} + 2.2
			set {-cd} to 2
		else if {-m13} is 5:
			set {-tex} to "td:textures/gui/mob_knight.png"
			set {-hp} to round({-hp} * 1.38)
			set {-spd} to {-spd} - 0.6
		else if {-m13} is 6:
			set {-tex} to "td:textures/gui/mob_ghost.png"
			set {-hp} to round({-hp} * 0.88)
			set {-spd} to {-spd} + 1.9
		else if {-m13} is 7:
			set {-tex} to "td:textures/gui/mob_necromancer.png"
			set {-hp} to round({-hp} * 1.12)
			set {-reg} to round({-hp} * 0.035)
			add 4 to {-rew}
		else if {-m13} is 8:
			set {-tex} to "td:textures/gui/mob_blaze.png"
			set {-hp} to round({-hp} * 1.08)
			set {-spd} to {-spd} + 1.1
			set {-imm} to 1
			add 4 to {-rew}
		else if {-m13} is 9:
			# НОВЫЙ: Теневой Ассасин (25% шанс уворота от атак!)
			set {-tex} to "td:textures/gui/mob_assassin.png"
			set {-hp} to round({-hp} * 0.85)
			set {-spd} to {-spd} + 2.8
			set {-dodge} to 25
			add 5 to {-rew}
		else if {-m13} is 10:
			set {-tex} to "td:textures/gui/mob_golem.png"
			set {-hp} to round({-hp} * 1.72)
			set {-spd} to {-spd} - 1.3
			add 6 to {-rew}
		else if {-m13} is 11:
			# НОВЫЙ: Храмовник Эгиды (щит поглощает 35% прямого урона!)
			set {-tex} to "td:textures/gui/mob_paladin.png"
			set {-hp} to round({-hp} * 1.35)
			set {-shield} to 1
			add 6 to {-rew}
		else if {-m13} is 12:
			set {-tex} to "td:textures/gui/mob_ravager.png"
			set {-hp} to round({-hp} * 1.85)
			set {-spd} to {-spd} - 0.8
			set {-bdmg} to 2
			add 8 to {-rew}
		else if {-m13} is 0:
			# НОВЫЙ: Вестник Бездны (реген + уклонение 15% + 2 урона базе!)
			set {-tex} to "td:textures/gui/mob_voidwalker.png"
			set {-hp} to round({-hp} * 1.55)
			set {-spd} to {-spd} + 1.0
			set {-reg} to round({-hp} * 0.025)
			set {-dodge} to 15
			set {-bdmg} to 2
			add 9 to {-rew}

		set {-td-spawn-cd::%g%} to {-cd}
		set {-td-ea::%g%::%{-slot}%} to 1
		set {-td-eboss::%g%::%{-slot}%} to 0
		set {-td-ex::%g%::%{-slot}%} to {-sx}
		set {-td-ey::%g%::%{-slot}%} to {-sy}
		set {-td-eseg::%g%::%{-slot}%} to 2
		set {-td-ehp::%g%::%{-slot}%} to {-hp}
		set {-td-emhp::%g%::%{-slot}%} to {-hp}
		set {-td-espd::%g%::%{-slot}%} to {-spd}
		set {-td-erew::%g%::%{-slot}%} to {-rew}
		set {-td-ebdmg::%g%::%{-slot}%} to {-bdmg}
		set {-td-eregen::%g%::%{-slot}%} to {-reg}
		set {-td-eimm::%g%::%{-slot}%} to {-imm}
		set {-td-edodge::%g%::%{-slot}%} to {-dodge}
		set {-td-eshield::%g%::%{-slot}%} to {-shield}
		set {-td-eslow::%g%::%{-slot}%} to 0
		set {-td-eslow-f::%g%::%{-slot}%} to 0.6
		set {-td-efreeze::%g%::%{-slot}%} to 0
		set {-td-eburn::%g%::%{-slot}%} to 0
		set {-td-epoison::%g%::%{-slot}%} to 0
		set {-td-etex::%g%::%{-slot}%} to {-tex}
		TdSyncImg(g, "e%{-slot}%", {-tex})
		TdSyncPos(g, "e%{-slot}%", {-sx}, {-sy})
		TdSyncShow(g, "e%{-slot}%")
		set {-hy} to {-sy} - 11
		TdSyncBar(g, "ehp%{-slot}%", {-hp}, {-hp})
		TdSyncPos(g, "ehp%{-slot}%", {-sx}, {-hy})
		TdSyncShow(g, "ehp%{-slot}%")
		stop

	# Спавн Босса (слот 12)
	if {-td-spawn-boss::%g%} > 0 and {-td-ea::%g%::12} is 0:
		set {-bk} to {-td-spawn-boss::%g%}
		set {-td-spawn-boss::%g%} to 0
		set {-bhp} to round((520 + {-bk} * {-bk} * 420) * {-dmul})
		set {-bspd} to 4.2 + {-bk} * 0.15
		set {-brew} to 90 + {-bk} * 75
		set {-btex} to "td:textures/gui/mob_boss.png"
		if {-w} is {-td-maxw::%g%}:
			set {-btex} to "td:textures/gui/mob_megaboss.png"
			set {-bhp} to round({-bhp} * 1.4)
		else if {-bk} >= 3:
			set {-btex} to "td:textures/gui/mob_iceboss.png"
			set {-bhp} to round({-bhp} * 1.15)
		set {-td-ea::%g%::12} to 1
		set {-td-eboss::%g%::12} to 1
		set {-td-ex::%g%::12} to {-sx}
		set {-td-ey::%g%::12} to {-sy}
		set {-td-eseg::%g%::12} to 2
		set {-td-ehp::%g%::12} to {-bhp}
		set {-td-emhp::%g%::12} to {-bhp}
		set {-td-espd::%g%::12} to {-bspd}
		set {-td-erew::%g%::12} to {-brew}
		set {-td-ebdmg::%g%::12} to 5
		set {-td-eregen::%g%::12} to 0
		set {-td-eimm::%g%::12} to 0
		set {-td-edodge::%g%::12} to 0
		set {-td-eshield::%g%::12} to 0
		set {-td-eslow::%g%::12} to 0
		set {-td-eslow-f::%g%::12} to 0.6
		set {-td-efreeze::%g%::12} to 0
		set {-td-eburn::%g%::12} to 0
		set {-td-epoison::%g%::12} to 0
		set {-td-etex::%g%::12} to {-btex}
		set {-td-boss-vis::%g%} to 1
		TdSyncImg(g, "e12", {-btex})
		TdSyncPos(g, "e12", {-sx}, {-sy})
		TdSyncShow(g, "e12")
		set {-bhy} to {-sy} - 16
		TdSyncBar(g, "ehp12", {-bhp}, {-bhp})
		TdSyncPos(g, "ehp12", {-sx}, {-bhy})
		TdSyncShow(g, "ehp12")
		TdSyncBossBar(g, {-bhp}, {-bhp})
		TdSyncShow(g, "boss_bar")
		gui particle "explosion" count 16 at {-sx} {-sy} color "#EF4444" spread 36 to {-td-online::%g%::*}

function TdMoveEnemy(g: player, i: number, anim: number):
	if {-td-efreeze::%g%::%i%} > 0:
		remove 1 from {-td-efreeze::%g%::%i%}
		stop
	set {-spd} to {-td-espd::%g%::%i%}
	if {-td-eslow::%g%::%i%} > 0:
		remove 1 from {-td-eslow::%g%::%i%}
		if {-td-eimm::%g%::%i%} is 0:
			set {-spd} to {-spd} * {-td-eslow-f::%g%::%i%}
	set {-seg} to {-td-eseg::%g%::%i%}
	set {-x} to {-td-ex::%g%::%i%}
	set {-y} to {-td-ey::%g%::%i%}
	set {-tx} to {-td-wpx::%g%::%{-seg}%}
	set {-ty} to {-td-wpy::%g%::%{-seg}%}
	set {-dx} to {-tx} - {-x}
	set {-dy} to {-ty} - {-y}
	set {-dist} to sqrt({-dx} * {-dx} + {-dy} * {-dy})

	if {-dist} <= {-spd}:
		set {-x} to {-tx}
		set {-y} to {-ty}
		add 1 to {-seg}
		if {-seg} > {-td-wpcnt::%g%}:
			set {-td-ea::%g%::%i%} to 0
			TdSyncHide(g, "e%i%")
			TdSyncHide(g, "ehp%i%")
			set {-dmg} to {-td-ebdmg::%g%::%i%}
			remove {-dmg} from {-td-hp::%g%}
			TdSyncSound(g, "entity.iron_golem.damage", 0.7, 0.8)
			shake camera of {-td-online::%g%::*} with intensity 1.6 for 8 ticks
			fade screen of {-td-online::%g%::*} to #FF1100 in 2 stay 2 out 8 ticks
			gui float "&c-%{-dmg}% HP!" at {-tx} {-ty} color "#EF4444" for 18 ticks to {-td-online::%g%::*}
			TdLog(g, "&c💔 Прорыв: -%{-dmg}% HP!")
			TdSyncHud(g)
			if {-td-hp::%g%} <= 0:
				TdLose(g)
			stop
	else:
		set {-x} to {-x} + ({-dx} / {-dist}) * {-spd}
		set {-y} to {-y} + ({-dy} / {-dist}) * {-spd}

	set {-td-ex::%g%::%i%} to {-x}
	set {-td-ey::%g%::%i%} to {-y}
	set {-td-eseg::%g%::%i%} to {-seg}
	TdSyncAnim(g, "e%i%", {-x}, {-y}, anim)
	set {-hy} to {-y} - 11
	if {-td-eboss::%g%::%i%} is 1:
		set {-hy} to {-y} - 16
	TdSyncAnim(g, "ehp%i%", {-x}, {-hy}, anim)

# ==========================================================
#  БАШНИ, АУРА МАЯКА, ЭВОЛЮЦИИ ✦5★ И ПРОЖЕКТАЙЛЫ
# ==========================================================

function TdTowerTick(g: player, tid: number):
	set {-type} to {-td-tt::%g%::%tid%}
	set {-lvl} to {-td-tl::%g%::%tid%}
	set {-tx} to {-td-tx::%g%::%tid%}
	set {-ty} to {-td-ty::%g%::%tid%}
	set {-evo} to 0
	if {-lvl} >= 5:
		set {-evo} to 1

	TdTowerRange(g, {-type}, {-lvl})
	set {-range} to {-td-tmp-rng}
	set {-r2} to {-range} * {-range}

	set {-dmg} to 15
	set {-cd} to 2
	set {-pspd} to 46
	set {-ptex} to "td:textures/gui/proj_arrow.png"
	if {-type} is 2:
		set {-dmg} to 36
		set {-cd} to 5
		set {-pspd} to 34
		set {-ptex} to "td:textures/gui/proj_cannon.png"
	else if {-type} is 3:
		set {-dmg} to 8
		set {-cd} to 4
		set {-pspd} to 40
		set {-ptex} to "td:textures/gui/proj_frost.png"
	else if {-type} is 4:
		set {-dmg} to 32
		set {-cd} to 3
		set {-pspd} to 36
		set {-ptex} to "td:textures/gui/proj_mage.png"
	else if {-type} is 5:
		set {-dmg} to 20
		set {-cd} to 3
		set {-pspd} to 50
		set {-ptex} to "td:textures/gui/proj_tesla.png"
	else if {-type} is 6:
		set {-dmg} to 56
		set {-cd} to 6
		set {-pspd} to 64
		set {-ptex} to "td:textures/gui/proj_sniper.png"
	else if {-type} is 7:
		set {-dmg} to 12
		set {-cd} to 3
		set {-pspd} to 40
		set {-ptex} to "td:textures/gui/proj_poison.png"

	# Учитываем бафф Боевого Маяка (#9) и бонус карты (Каньон +10%, Разлом +20%)
	set {-bmul} to {-td-tbuff::%g%::%tid%}
	if {-bmul} is not set:
		set {-bmul} to 1.0
	set {-mapMul} to 1.0
	if {-td-map::%g%} is 2:
		set {-mapMul} to 1.1
	set {-dmg} to round({-dmg} * (1 + ({-lvl} - 1) * 0.55) * {-bmul} * {-mapMul})
	if {-evo} is 1:
		set {-dmg} to round({-dmg} * 1.4)
	if {-td-tfast::%g%::%tid%} is 1 and {-cd} > 1:
		remove 1 from {-cd}

	# Крио-Обелиск (#3): аура холода по всему радиусу
	if {-type} is 3:
		set {-hit-any} to 0
		loop 12 times:
			set {-ei} to %loop-number%
			if {-td-ea::%g%::%{-ei}%} is 1:
				set {-dx} to {-td-ex::%g%::%{-ei}%} - {-tx}
				set {-dy} to {-td-ey::%g%::%{-ei}%} - {-ty}
				if {-dx} * {-dx} + {-dy} * {-dy} <= {-r2}:
					set {-hit-any} to 1
					set {-frz} to 0
					if {-evo} is 1:
						set {-td-eslow-f::%g%::%{-ei}%} to 0.5
						if {-td-eboss::%g%::%{-ei}%} is 0 and {-td-eimm::%g%::%{-ei}%} is 0:
							chance of 12%:
								set {-frz} to 3
					else:
						set {-td-eslow-f::%g%::%{-ei}%} to 0.6
					TdDamageEnemy(g, {-ei}, {-dmg}, 9, {-frz}, 1)
		if {-hit-any} is 1:
			set {-td-tcd::%g%::%tid%} to {-cd}
			gui particle "ice" count 10 at {-tx} {-ty} spread 36 to {-td-online::%g%::*}
			TdSyncSound(g, "block.glass.break", 0.14, 1.85)
		stop

	set {-best} to 0
	set {-best-prog} to -1
	set {-second} to 0
	loop 12 times:
		set {-ei} to %loop-number%
		if {-td-ea::%g%::%{-ei}%} is 1:
			set {-dx} to {-td-ex::%g%::%{-ei}%} - {-tx}
			set {-dy} to {-td-ey::%g%::%{-ei}%} - {-ty}
			if {-dx} * {-dx} + {-dy} * {-dy} <= {-r2}:
				set {-prog} to {-td-eseg::%g%::%{-ei}%} * 1000
				if {-prog} > {-best-prog}:
					set {-second} to {-best}
					set {-best-prog} to {-prog}
					set {-best} to {-ei}
				else if {-second} is 0:
					set {-second} to {-ei}

	if {-best} is 0:
		stop

	set {-td-tcd::%g%::%tid%} to {-cd}
	TdSpawnProjectile(g, {-tx}, {-ty}, {-best}, {-type}, {-evo}, {-dmg}, {-pspd}, {-ptex})

	if {-type} is 1 and {-evo} is 1 and {-second} > 0:
		TdSpawnProjectile(g, {-tx}, {-ty}, {-second}, 1, 1, {-dmg}, {-pspd}, {-ptex})

function TdSpawnProjectile(g: player, sx: number, sy: number, tgt: number, ptype: number, evo: number, dmg: number, spd: number, tex: text):
	set {-ps} to 0
	loop 6 times:
		set {-k} to %loop-number%
		if {-td-pra::%g%::%{-k}%} is 0 and {-ps} is 0:
			set {-ps} to {-k}
	if {-ps} is 0:
		TdImpact(g, tgt, ptype, evo, dmg, {-td-ex::%g%::%tgt%}, {-td-ey::%g%::%tgt%})
		stop
	set {-td-pra::%g%::%{-ps}%} to 1
	add 1 to {-td-prcnt::%g%}
	set {-td-prx::%g%::%{-ps}%} to sx
	set {-td-pry::%g%::%{-ps}%} to sy
	set {-td-prtgt::%g%::%{-ps}%} to tgt
	set {-td-prtype::%g%::%{-ps}%} to ptype
	set {-td-prevo::%g%::%{-ps}%} to evo
	set {-td-prdmg::%g%::%{-ps}%} to dmg
	set {-td-prspd::%g%::%{-ps}%} to spd
	TdSyncImg(g, "pr%{-ps}%", tex)
	TdSyncPos(g, "pr%{-ps}%", sx, sy)
	TdSyncShow(g, "pr%{-ps}%")

function TdMoveProjectiles(g: player, anim: number):
	loop 6 times:
		set {-k} to %loop-number%
		if {-td-pra::%g%::%{-k}%} is 1:
			set {-tgt} to {-td-prtgt::%g%::%{-k}%}
			if {-td-ea::%g%::%{-tgt}%} is not 1:
				set {-td-pra::%g%::%{-k}%} to 0
				remove 1 from {-td-prcnt::%g%}
				TdSyncHide(g, "pr%{-k}%")
			else:
				set {-px} to {-td-prx::%g%::%{-k}%}
				set {-py} to {-td-pry::%g%::%{-k}%}
				set {-tx} to {-td-ex::%g%::%{-tgt}%}
				set {-ty} to {-td-ey::%g%::%{-tgt}%}
				set {-spd} to {-td-prspd::%g%::%{-k}%}
				set {-dx} to {-tx} - {-px}
				set {-dy} to {-ty} - {-py}
				set {-dist} to sqrt({-dx} * {-dx} + {-dy} * {-dy})
				if {-dist} <= {-spd}:
					set {-td-pra::%g%::%{-k}%} to 0
					remove 1 from {-td-prcnt::%g%}
					TdSyncHide(g, "pr%{-k}%")
					TdImpact(g, {-tgt}, {-td-prtype::%g%::%{-k}%}, {-td-prevo::%g%::%{-k}%}, {-td-prdmg::%g%::%{-k}%}, {-tx}, {-ty})
				else:
					set {-nx} to {-px} + ({-dx} / {-dist}) * {-spd}
					set {-ny} to {-py} + ({-dy} / {-dist}) * {-spd}
					set {-td-prx::%g%::%{-k}%} to {-nx}
					set {-td-pry::%g%::%{-k}%} to {-ny}
					TdSyncAnim(g, "pr%{-k}%", {-nx}, {-ny}, anim)

function TdClearProjectiles(g: player):
	set {-td-prcnt::%g%} to 0
	loop 6 times:
		set {-k} to %loop-number%
		set {-td-pra::%g%::%{-k}%} to 0
		TdSyncHide(g, "pr%{-k}%")

function TdImpact(g: player, tgt: number, ptype: number, evo: number, dmg: number, hx: number, hy: number):
	if ptype is 2:
		gui particle "explosion" count 12 at hx hy spread 28 to {-td-online::%g%::*}
		TdSyncSound(g, "entity.generic.explode", 0.15, 1.65)
		set {-rad2} to 1600
		if evo is 1:
			set {-rad2} to 3136
			gui particle "fire" count 10 at hx hy spread 32 to {-td-online::%g%::*}
		loop 12 times:
			set {-si} to %loop-number%
			if {-td-ea::%g%::%{-si}%} is 1:
				set {-sdx} to {-td-ex::%g%::%{-si}%} - hx
				set {-sdy} to {-td-ey::%g%::%{-si}%} - hy
				if {-sdx} * {-sdx} + {-sdy} * {-sdy} <= {-rad2}:
					TdDamageEnemy(g, {-si}, dmg, 0, 0, 1)
					if evo is 1 and {-td-ea::%g%::%{-si}%} is 1:
						set {-td-eburn::%g%::%{-si}%} to 6
						set {-td-eburn-d::%g%::%{-si}%} to round(dmg * 0.28)
	else if ptype is 4:
		set {-fdmg} to dmg
		if evo is 1:
			if {-td-eboss::%g%::%tgt%} is 1:
				set {-fdmg} to round({-fdmg} * 2.0)
			add round({-td-emhp::%g%::%tgt%} * 0.04) to {-fdmg}
		gui particle "magic" count 7 at hx hy spread 18 to {-td-online::%g%::*}
		TdSyncSound(g, "entity.illusioner.cast_spell", 0.17, 1.5)
		TdDamageEnemy(g, tgt, {-fdmg}, 0, 0, 1)
	else if ptype is 5:
		gui particle "spark" count 10 at hx hy color "#38BDF8" spread 24 to {-td-online::%g%::*}
		TdSyncSound(g, "entity.lightning_bolt.impact", 0.13, 1.9)
		set {-maxh} to 3
		set {-crad2} to 3844
		set {-cmul} to 0.75
		set {-stun} to 0
		if evo is 1:
			set {-maxh} to 5
			set {-crad2} to 6400
			set {-cmul} to 0.9
			set {-stun} to 2
		TdDamageEnemy(g, tgt, dmg, 0, {-stun}, 1)
		set {-hits} to 1
		loop 12 times:
			set {-si} to %loop-number%
			if {-si} is not tgt and {-hits} < {-maxh} and {-td-ea::%g%::%{-si}%} is 1:
				set {-sdx} to {-td-ex::%g%::%{-si}%} - hx
				set {-sdy} to {-td-ey::%g%::%{-si}%} - hy
				if {-sdx} * {-sdx} + {-sdy} * {-sdy} <= {-crad2}:
					TdDamageEnemy(g, {-si}, round(dmg * {-cmul}), 0, {-stun}, 1)
					add 1 to {-hits}
	else if ptype is 6:
		set {-fdmg} to dmg
		if evo is 1:
			chance of 45%:
				set {-fdmg} to round(dmg * 2.5)
				gui float "&c&l-%{-fdmg}% CRIT!" at hx hy color "#FF4444" for 14 ticks to {-td-online::%g%::*}
				gui particle "spark" count 10 at hx hy color "#EF4444" spread 24 to {-td-online::%g%::*}
			if {-td-eboss::%g%::%tgt%} is 0 and {-td-ehp::%g%::%tgt%} <= {-td-emhp::%g%::%tgt%} * 0.2:
				set {-fdmg} to 999999
				gui float "&4&l☠ КАЗНЬ!" at hx hy color "#EF4444" for 16 ticks to {-td-online::%g%::*}
		else:
			chance of 25%:
				set {-fdmg} to dmg * 2
				gui float "&c-%{-fdmg}%!" at hx hy color "#FF6B6B" for 12 ticks to {-td-online::%g%::*}
				gui particle "spark" count 8 at hx hy color "#EF4444" spread 18 to {-td-online::%g%::*}
		TdSyncSound(g, "entity.arrow.hit_player", 0.22, 1.6)
		TdDamageEnemy(g, tgt, {-fdmg}, 0, 0, 1)
	else if ptype is 7:
		gui particle "poison" count 8 at hx hy spread 22 to {-td-online::%g%::*}
		TdSyncSound(g, "entity.splash_potion.break", 0.18, 1.4)
		TdDamageEnemy(g, tgt, dmg, 0, 0, 1)
		if {-td-ea::%g%::%tgt%} is 1:
			set {-td-epoison::%g%::%tgt%} to 10
			set {-td-epoison-d::%g%::%tgt%} to round(dmg * 0.7)
		if evo is 1:
			loop 12 times:
				set {-si} to %loop-number%
				if {-si} is not tgt and {-td-ea::%g%::%{-si}%} is 1:
					set {-sdx} to {-td-ex::%g%::%{-si}%} - hx
					set {-sdy} to {-td-ey::%g%::%{-si}%} - hy
					if {-sdx} * {-sdx} + {-sdy} * {-sdy} <= 2304:
						set {-td-epoison::%g%::%{-si}%} to 10
						set {-td-epoison-d::%g%::%{-si}%} to round(dmg * 0.6)
	else:
		TdSyncSound(g, "entity.arrow.shoot", 0.17, 1.45)
		TdDamageEnemy(g, tgt, dmg, 0, 0, 0)

function TdDamageEnemy(g: player, i: number, dmg: number, slow: number, freeze: number, bypassDodge: number):
	if {-td-ea::%g%::%i%} is not 1:
		stop
	# Уклонение Ассасина и Вестника Бездны от обычных стрел
	if bypassDodge is 0 and {-td-edodge::%g%::%i%} > 0:
		chance of {-td-edodge::%g%::%i%}%:
			gui float "&dУКЛОН!" at {-td-ex::%g%::%i%} {-td-ey::%g%::%i%} color "#C084FC" for 10 ticks to {-td-online::%g%::*}
			stop
	set {-realDmg} to dmg
	# Энергетический щит Храмовника Эгиды (-35% прямого урона)
	if {-td-eshield::%g%::%i%} is 1 and {-realDmg} < 50000:
		set {-realDmg} to round({-realDmg} * 0.65)
	remove {-realDmg} from {-td-ehp::%g%::%i%}
	if slow > 0:
		set {-td-eslow::%g%::%i%} to slow
	if freeze > 0:
		set {-td-efreeze::%g%::%i%} to freeze
	if {-td-ehp::%g%::%i%} <= 0:
		set {-td-ea::%g%::%i%} to 0
		set {-kx} to {-td-ex::%g%::%i%}
		set {-ky} to {-td-ey::%g%::%i%}
		TdSyncHide(g, "e%i%")
		TdSyncHide(g, "ehp%i%")
		set {-rew} to {-td-erew::%g%::%i%}
		set {-pcnt} to size of {-td-team::%g%::*}
		set {-share} to {-rew}
		if {-pcnt} is 2:
			set {-share} to round({-rew} * 0.65)
		else if {-pcnt} is 3:
			set {-share} to round({-rew} * 0.5)
		else if {-pcnt} >= 4:
			set {-share} to round({-rew} * 0.42)
		loop {-td-team::%g%::*}:
			set {-tp} to %loop-value%
			add {-share} to {-td-gold::%{-tp}%}
		set {-sc-kill} to {-rew}
		if {-td-map::%g%} is 5:
			set {-sc-kill} to round({-rew} * 1.5)
		add {-sc-kill} to {-td-score::%g%}
		gui float "&6+%{-share}%з" at {-kx} {-ky} color "#FACC15" for 14 ticks to {-td-online::%g%::*}
		gui particle "gold" count 6 at {-kx} {-ky} spread 16 to {-td-online::%g%::*}
		if {-td-eboss::%g%::%i%} is 1:
			set {-td-boss-vis::%g%} to 0
			TdSyncHide(g, "boss_bar")
			gui particle "gold" count 24 at {-kx} {-ky} spread 44 to {-td-online::%g%::*}
			TdLog(g, "&6🏆 Босс повержен!")
			TdSyncSound(g, "ui.toast.challenge_complete", 0.7, 1.1)
		TdSyncGoldScore(g)
	else:
		TdSyncBar(g, "ehp%i%", {-td-ehp::%g%::%i%}, 0)

# ==========================================================
#  ПОБЕДА, ПОРАЖЕНИЕ И РЕКОРДЫ
# ==========================================================

function TdLose(g: player):
	set {-td-over::%g%} to 1
	set {-td-in-wave::%g%} to 0
	TdClearProjectiles(g)
	TdSyncTxt(g, "end_title", "&c&lБАЗА ПАЛА! (Волна %{-td-wave::%g%}%/%{-td-maxw::%g%}%)")
	TdSyncTxt(g, "end_sub", "&fСчёт: &e%{-td-score::%g%}% &7— нажми &eR &7(рестарт) или &eM &7(меню)")
	TdSyncShow(g, "end_title")
	TdSyncShow(g, "end_sub")
	TdLog(g, "&c✖ Поражение на в.%{-td-wave::%g%}%")
	TdSaveScore(g)

function TdWin(g: player):
	set {-td-over::%g%} to 1
	set {-wbon} to {-td-hp::%g%} * 100 * {-td-diff::%g%}
	if {-td-map::%g%} is 5:
		set {-wbon} to round({-wbon} * 1.5)
	add {-wbon} to {-td-score::%g%}
	TdSyncHud(g)
	TdSyncTxt(g, "end_title", "&6&lПОБЕДА! ВСЕ %{-td-maxw::%g%}% ВОЛН ПРОЙДЕНЫ!")
	TdSyncTxt(g, "end_sub", "&aСчёт: &e%{-td-score::%g%}% &7— нажми &eR &7(ещё раз) или &eM &7(меню)")
	TdSyncShow(g, "end_title")
	TdSyncShow(g, "end_sub")
	fade screen of {-td-online::%g%::*} to #FFD700 in 3 stay 4 out 14 ticks
	gui particle "gold" count 32 at -56 -10 spread 80 to {-td-online::%g%::*}
	TdLog(g, "&6🏆 ПОБЕДА! %{-td-score::%g%}% очк.")
	TdSyncSound(g, "ui.toast.challenge_complete", 0.9, 1.0)
	TdSaveScore(g)

function TdSaveScore(g: player):
	set {-sc} to {-td-score::%g%}
	loop {-td-team::%g%::*}:
		set {-tp} to %loop-value%
		if {td::lb::%{-tp}%} is not set:
			set {td::lb::%{-tp}%} to {-sc}
		else if {-sc} > {td::lb::%{-tp}%}:
			set {td::lb::%{-tp}%} to {-sc}
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
      { lang: "sc", title: { ru: "scriptcraft/scripts/-seabattle.sc", en: "scriptcraft/scripts/-seabattle.sc" }, code: `# ==========================================================
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
