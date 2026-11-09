# ==========================================================
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
	show vanilla all for player
