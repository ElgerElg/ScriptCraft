# ==========================================================
#  td.sc — Tower Defense Pro (3 карты, 3 сложности: 10/20/30 волн,
#          6 видов башен, свободная постройка, летающие прожектайлы!)
#  Команда: /td
#  Управление:
#    1..6  — выбрать башню (Лучник / Пушка / Лёд / Маг / Тесла / Снайпер)
#    ЛКМ по любой свободной клетке — построить башню
#    ЛКМ по построенной башне — сразу улучшить (до 4★!)
#    X     — режим продажи (возврат 70%)
#    Space — старт волны / скорость x1 <-> x2
#    R     — рестарт текущей карты | M — меню выбора карты
#  Текстуры: распакуйте td-resources.zip в scriptcraft/resources/
# ==========================================================

on load:
	clear {-td-active::*}

on quit:
	TdCleanup(player)

on screen close "Tower Defense":
	TdCleanup(player)

command /td:
	TdOpenMenu(player)

# --- Горячие клавиши в игре ---

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

on key x in screen "Tower Defense":
	TdToggleSell(player)

on key space in screen "Tower Defense":
	TdWaveOrSpeed(player)

on key r in screen "Tower Defense":
	TdStartGame(player)

on key m in screen "Tower Defense":
	TdOpenMenu(player)

on toggle "autowave":
	if %value% is "true":
		set {-td-auto::%player%} to 1
		set text of screen element "status_txt" for player to "&aАвто-волна включена!"
	else:
		set {-td-auto::%player%} to 0
		set text of screen element "status_txt" for player to "&7Авто-волна выключена"

# --- Игровой цикл (10 тиков/сек, при x2 — 2 шага за тик) ---

every 2 ticks:
	loop all players:
		if {-td-active::%loop-player%} is 1:
			if loop-player has screen "Tower Defense" open:
				TdTick(loop-player)
				if {-td-speed::%loop-player%} is 2 and {-td-in-wave::%loop-player%} is 1 and {-td-over::%loop-player%} is 0:
					TdTick(loop-player)
			else:
				TdCleanup(loop-player)

function TdCleanup(p: player):
	delete {-td-active::%p%}
	delete {-td-over::%p%}

# ==========================================================
#  МЕНЮ ВЫБОРА КАРТЫ И СЛОЖНОСТИ
# ==========================================================

function TdOpenMenu(p: player):
	TdCleanup(p)
	if {-td-map::%p%} is not set:
		set {-td-map::%p%} to 1
	if {-td-diff::%p%} is not set:
		set {-td-diff::%p%} to 2
	open screen "Tower Defense" to p:
		text "&6&lTOWER DEFENSE" at 0 -106 anchor center shadow
		text "&7Выберите карту и сложность (башни можно ставить куда угодно!)" at 0 -92 anchor center shadow

		# 3 превью карт
		image "td:textures/gui/map_valley.png" at -122 -36 size 112x56 texture 384x192 anchor center run TdPickMap(player, 1) keep open tooltip "&aИзумрудная долина\n&7Классический S-маршрут"
		text "&a1. Долина" at -122 0 anchor center shadow id m_lbl1

		image "td:textures/gui/map_volcano.png" at 0 -36 size 112x56 texture 384x192 anchor center run TdPickMap(player, 2) keep open tooltip "&6Лавовый каньон\n&7Двойной зигзаг — много поворотов!"
		text "&72. Каньон" at 0 0 anchor center shadow id m_lbl2

		image "td:textures/gui/map_ice.png" at 122 -36 size 112x56 texture 384x192 anchor center run TdPickMap(player, 3) keep open tooltip "&bЛедяная цитадель\n&7Короткий и быстрый серпантин!"
		text "&73. Цитадель" at 122 0 anchor center shadow id m_lbl3

		# 3 уровня сложности
		text "&eСложность:" at 0 24 anchor center shadow
		button "&aЛёгкий (10 волн)" at -110 44 size 100x20 anchor center run TdPickDiff(player, 1) keep open tooltip "&aЛёгкий уровень\n&710 волн | 2 босса | 15 HP | 140з"
		button "&eСредний (20 волн)" at 0 44 size 100x20 anchor center run TdPickDiff(player, 2) keep open tooltip "&eСредний уровень\n&720 волн | 4 босса | 10 HP | 120з"
		button "&cТяжёлый (30 волн)" at 110 44 size 100x20 anchor center run TdPickDiff(player, 3) keep open tooltip "&cТяжёлый уровень\n&730 волн | 6 боссов | 8 HP | 110з"

		text "&7Выбрано: &aДолина &7| &eСредний (20 волн)" at 0 72 anchor center shadow id menu_info
		button "&a&l▶ ИГРАТЬ" at -48 96 size 110x22 anchor center run TdStartGame(player) keep open
		button "&6Топ-5" at 66 96 size 80x22 anchor center run TdShowTop(player) keep open
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
	set text of screen element "m_lbl1" for p to "&71. Долина"
	set text of screen element "m_lbl2" for p to "&72. Каньон"
	set text of screen element "m_lbl3" for p to "&73. Цитадель"
	set {-mn} to "&aДолина"
	if {-m} is 1:
		set text of screen element "m_lbl1" for p to "&a&l▶ 1. Долина"
	else if {-m} is 2:
		set {-mn} to "&6Каньон"
		set text of screen element "m_lbl2" for p to "&6&l▶ 2. Каньон"
	else:
		set {-mn} to "&bЦитадель"
		set text of screen element "m_lbl3" for p to "&b&l▶ 3. Цитадель"
	set {-dn} to "&eСредний (20 волн)"
	if {-d} is 1:
		set {-dn} to "&aЛёгкий (10 волн)"
	else if {-d} is 3:
		set {-dn} to "&cТяжёлый (30 волн)"
	set text of screen element "menu_info" for p to "&7Выбрано: %{-mn}% &7| %{-dn}%"

# ==========================================================
#  ЗАПУСК ИГРЫ И КАРТЫ
# ==========================================================

function TdStartGame(p: player):
	if {-td-map::%p%} is not set:
		set {-td-map::%p%} to 1
	if {-td-diff::%p%} is not set:
		set {-td-diff::%p%} to 2
	set {-m} to {-td-map::%p%}
	set {-d} to {-td-diff::%p%}

	set {-td-active::%p%} to 1
	set {-td-over::%p%} to 0
	set {-td-wave::%p%} to 0
	set {-td-score::%p%} to 0
	set {-td-tool::%p%} to 1
	set {-td-sell::%p%} to 0
	set {-td-speed::%p%} to 1
	set {-td-auto::%p%} to 0
	set {-td-auto-cd::%p%} to 0
	set {-td-in-wave::%p%} to 0
	set {-td-to-spawn::%p%} to 0
	set {-td-spawn-boss::%p%} to 0
	set {-td-spawn-cd::%p%} to 0
	set {-td-fx-cd::%p%} to 0

	if {-d} is 1:
		set {-td-maxw::%p%} to 10
		set {-td-gold::%p%} to 140
		set {-td-hp::%p%} to 15
	else if {-d} is 2:
		set {-td-maxw::%p%} to 20
		set {-td-gold::%p%} to 120
		set {-td-hp::%p%} to 10
	else:
		set {-td-maxw::%p%} to 30
		set {-td-gold::%p%} to 110
		set {-td-hp::%p%} to 8

	clear {-td-grid::%p%::*}
	loop 24 times:
		set {-i} to %loop-number%
		set {-td-ta::%p%::%{-i}%} to 0
		set {-td-tt::%p%::%{-i}%} to 0
		set {-td-tl::%p%::%{-i}%} to 0
		set {-td-tcd::%p%::%{-i}%} to 0
		set {-td-tcost::%p%::%{-i}%} to 0

	loop 10 times:
		set {-i} to %loop-number%
		set {-td-ea::%p%::%{-i}%} to 0
		set {-td-eboss::%p%::%{-i}%} to 0
		set {-td-eslow::%p%::%{-i}%} to 0

	loop 6 times:
		set {-i} to %loop-number%
		set {-td-pra::%p%::%{-i}%} to 0

	TdLoadWaypoints(p, {-m})
	if {-m} is 1:
		TdOpenMap1(p)
	else if {-m} is 2:
		TdOpenMap2(p)
	else:
		TdOpenMap3(p)
	TdUpdateHud(p)

function TdLoadWaypoints(p: player, mapId: number):
	if mapId is 1:
		set {-td-wpcnt::%p%} to 6
		set {-td-wpx::%p%::1} to -180
		set {-td-wpy::%p%::1} to -70
		set {-td-wpx::%p%::2} to 132
		set {-td-wpy::%p%::2} to -70
		set {-td-wpx::%p%::3} to 132
		set {-td-wpy::%p%::3} to -22
		set {-td-wpx::%p%::4} to -132
		set {-td-wpy::%p%::4} to -22
		set {-td-wpx::%p%::5} to -132
		set {-td-wpy::%p%::5} to 50
		set {-td-wpx::%p%::6} to 180
		set {-td-wpy::%p%::6} to 50
	else if mapId is 2:
		set {-td-wpcnt::%p%} to 8
		set {-td-wpx::%p%::1} to -180
		set {-td-wpy::%p%::1} to -94
		set {-td-wpx::%p%::2} to -84
		set {-td-wpy::%p%::2} to -94
		set {-td-wpx::%p%::3} to -84
		set {-td-wpy::%p%::3} to 50
		set {-td-wpx::%p%::4} to 36
		set {-td-wpy::%p%::4} to 50
		set {-td-wpx::%p%::5} to 36
		set {-td-wpy::%p%::5} to -70
		set {-td-wpx::%p%::6} to 132
		set {-td-wpy::%p%::6} to -70
		set {-td-wpx::%p%::7} to 132
		set {-td-wpy::%p%::7} to 26
		set {-td-wpx::%p%::8} to 180
		set {-td-wpy::%p%::8} to 26
	else if mapId is 3:
		set {-td-wpcnt::%p%} to 8
		set {-td-wpx::%p%::1} to -180
		set {-td-wpy::%p%::1} to 2
		set {-td-wpx::%p%::2} to -108
		set {-td-wpy::%p%::2} to 2
		set {-td-wpx::%p%::3} to -108
		set {-td-wpy::%p%::3} to -70
		set {-td-wpx::%p%::4} to -12
		set {-td-wpy::%p%::4} to -70
		set {-td-wpx::%p%::5} to -12
		set {-td-wpy::%p%::5} to 50
		set {-td-wpx::%p%::6} to 84
		set {-td-wpy::%p%::6} to 50
		set {-td-wpx::%p%::7} to 84
		set {-td-wpy::%p%::7} to -46
		set {-td-wpx::%p%::8} to 180
		set {-td-wpy::%p%::8} to -46

function TdOpenMap1(p: player):
	open screen "Tower Defense" to p:
		image "td:textures/gui/map_valley.png" at 0 -10 size 384x192 texture 384x192 anchor center layer 0 id map
		text "&eВолна: &f0/20   &6Золото: &f120   &cБаза: &f10 HP   &aСчёт: &f0   &7[x1]" at 0 -116 anchor center shadow layer 12 id hud_top
		bar "boss_bar" value 100 max 100 at 0 -98 size 200x8 anchor center color #E74C3C background #AA1A0A0A text "&c&lБОСС: %value%/%max%" shadow layer 14 hidden

		# Свободная сетка постройки (любая клетка вне дороги)
		image "td:textures/gui/tile_build.png" at -180 -94 size 24x24 texture 24x24 anchor center layer 3 id c0_0 run TdCellClick(player, 0, 0) keep open
		image "td:textures/gui/tile_build.png" at -156 -94 size 24x24 texture 24x24 anchor center layer 3 id c1_0 run TdCellClick(player, 1, 0) keep open
		image "td:textures/gui/tile_build.png" at -132 -94 size 24x24 texture 24x24 anchor center layer 3 id c2_0 run TdCellClick(player, 2, 0) keep open
		image "td:textures/gui/tile_build.png" at -108 -94 size 24x24 texture 24x24 anchor center layer 3 id c3_0 run TdCellClick(player, 3, 0) keep open
		image "td:textures/gui/tile_build.png" at -84 -94 size 24x24 texture 24x24 anchor center layer 3 id c4_0 run TdCellClick(player, 4, 0) keep open
		image "td:textures/gui/tile_build.png" at -60 -94 size 24x24 texture 24x24 anchor center layer 3 id c5_0 run TdCellClick(player, 5, 0) keep open
		image "td:textures/gui/tile_build.png" at -36 -94 size 24x24 texture 24x24 anchor center layer 3 id c6_0 run TdCellClick(player, 6, 0) keep open
		image "td:textures/gui/tile_build.png" at -12 -94 size 24x24 texture 24x24 anchor center layer 3 id c7_0 run TdCellClick(player, 7, 0) keep open
		image "td:textures/gui/tile_build.png" at 12 -94 size 24x24 texture 24x24 anchor center layer 3 id c8_0 run TdCellClick(player, 8, 0) keep open
		image "td:textures/gui/tile_build.png" at 36 -94 size 24x24 texture 24x24 anchor center layer 3 id c9_0 run TdCellClick(player, 9, 0) keep open
		image "td:textures/gui/tile_build.png" at 60 -94 size 24x24 texture 24x24 anchor center layer 3 id c10_0 run TdCellClick(player, 10, 0) keep open
		image "td:textures/gui/tile_build.png" at 84 -94 size 24x24 texture 24x24 anchor center layer 3 id c11_0 run TdCellClick(player, 11, 0) keep open
		image "td:textures/gui/tile_build.png" at 108 -94 size 24x24 texture 24x24 anchor center layer 3 id c12_0 run TdCellClick(player, 12, 0) keep open
		image "td:textures/gui/tile_build.png" at 132 -94 size 24x24 texture 24x24 anchor center layer 3 id c13_0 run TdCellClick(player, 13, 0) keep open
		image "td:textures/gui/tile_build.png" at 156 -94 size 24x24 texture 24x24 anchor center layer 3 id c14_0 run TdCellClick(player, 14, 0) keep open
		image "td:textures/gui/tile_build.png" at 180 -94 size 24x24 texture 24x24 anchor center layer 3 id c15_0 run TdCellClick(player, 15, 0) keep open
		image "td:textures/gui/tile_build.png" at 156 -70 size 24x24 texture 24x24 anchor center layer 3 id c14_1 run TdCellClick(player, 14, 1) keep open
		image "td:textures/gui/tile_build.png" at 180 -70 size 24x24 texture 24x24 anchor center layer 3 id c15_1 run TdCellClick(player, 15, 1) keep open
		image "td:textures/gui/tile_build.png" at -180 -46 size 24x24 texture 24x24 anchor center layer 3 id c0_2 run TdCellClick(player, 0, 2) keep open
		image "td:textures/gui/tile_build.png" at -156 -46 size 24x24 texture 24x24 anchor center layer 3 id c1_2 run TdCellClick(player, 1, 2) keep open
		image "td:textures/gui/tile_build.png" at -132 -46 size 24x24 texture 24x24 anchor center layer 3 id c2_2 run TdCellClick(player, 2, 2) keep open
		image "td:textures/gui/tile_build.png" at -108 -46 size 24x24 texture 24x24 anchor center layer 3 id c3_2 run TdCellClick(player, 3, 2) keep open
		image "td:textures/gui/tile_build.png" at -84 -46 size 24x24 texture 24x24 anchor center layer 3 id c4_2 run TdCellClick(player, 4, 2) keep open
		image "td:textures/gui/tile_build.png" at -60 -46 size 24x24 texture 24x24 anchor center layer 3 id c5_2 run TdCellClick(player, 5, 2) keep open
		image "td:textures/gui/tile_build.png" at -36 -46 size 24x24 texture 24x24 anchor center layer 3 id c6_2 run TdCellClick(player, 6, 2) keep open
		image "td:textures/gui/tile_build.png" at -12 -46 size 24x24 texture 24x24 anchor center layer 3 id c7_2 run TdCellClick(player, 7, 2) keep open
		image "td:textures/gui/tile_build.png" at 12 -46 size 24x24 texture 24x24 anchor center layer 3 id c8_2 run TdCellClick(player, 8, 2) keep open
		image "td:textures/gui/tile_build.png" at 36 -46 size 24x24 texture 24x24 anchor center layer 3 id c9_2 run TdCellClick(player, 9, 2) keep open
		image "td:textures/gui/tile_build.png" at 60 -46 size 24x24 texture 24x24 anchor center layer 3 id c10_2 run TdCellClick(player, 10, 2) keep open
		image "td:textures/gui/tile_build.png" at 84 -46 size 24x24 texture 24x24 anchor center layer 3 id c11_2 run TdCellClick(player, 11, 2) keep open
		image "td:textures/gui/tile_build.png" at 108 -46 size 24x24 texture 24x24 anchor center layer 3 id c12_2 run TdCellClick(player, 12, 2) keep open
		image "td:textures/gui/tile_build.png" at 156 -46 size 24x24 texture 24x24 anchor center layer 3 id c14_2 run TdCellClick(player, 14, 2) keep open
		image "td:textures/gui/tile_build.png" at 180 -46 size 24x24 texture 24x24 anchor center layer 3 id c15_2 run TdCellClick(player, 15, 2) keep open
		image "td:textures/gui/tile_build.png" at -180 -22 size 24x24 texture 24x24 anchor center layer 3 id c0_3 run TdCellClick(player, 0, 3) keep open
		image "td:textures/gui/tile_build.png" at -156 -22 size 24x24 texture 24x24 anchor center layer 3 id c1_3 run TdCellClick(player, 1, 3) keep open
		image "td:textures/gui/tile_build.png" at 156 -22 size 24x24 texture 24x24 anchor center layer 3 id c14_3 run TdCellClick(player, 14, 3) keep open
		image "td:textures/gui/tile_build.png" at 180 -22 size 24x24 texture 24x24 anchor center layer 3 id c15_3 run TdCellClick(player, 15, 3) keep open
		image "td:textures/gui/tile_build.png" at -180 2 size 24x24 texture 24x24 anchor center layer 3 id c0_4 run TdCellClick(player, 0, 4) keep open
		image "td:textures/gui/tile_build.png" at -156 2 size 24x24 texture 24x24 anchor center layer 3 id c1_4 run TdCellClick(player, 1, 4) keep open
		image "td:textures/gui/tile_build.png" at -108 2 size 24x24 texture 24x24 anchor center layer 3 id c3_4 run TdCellClick(player, 3, 4) keep open
		image "td:textures/gui/tile_build.png" at -84 2 size 24x24 texture 24x24 anchor center layer 3 id c4_4 run TdCellClick(player, 4, 4) keep open
		image "td:textures/gui/tile_build.png" at -60 2 size 24x24 texture 24x24 anchor center layer 3 id c5_4 run TdCellClick(player, 5, 4) keep open
		image "td:textures/gui/tile_build.png" at -36 2 size 24x24 texture 24x24 anchor center layer 3 id c6_4 run TdCellClick(player, 6, 4) keep open
		image "td:textures/gui/tile_build.png" at -12 2 size 24x24 texture 24x24 anchor center layer 3 id c7_4 run TdCellClick(player, 7, 4) keep open
		image "td:textures/gui/tile_build.png" at 12 2 size 24x24 texture 24x24 anchor center layer 3 id c8_4 run TdCellClick(player, 8, 4) keep open
		image "td:textures/gui/tile_build.png" at 36 2 size 24x24 texture 24x24 anchor center layer 3 id c9_4 run TdCellClick(player, 9, 4) keep open
		image "td:textures/gui/tile_build.png" at 60 2 size 24x24 texture 24x24 anchor center layer 3 id c10_4 run TdCellClick(player, 10, 4) keep open
		image "td:textures/gui/tile_build.png" at 84 2 size 24x24 texture 24x24 anchor center layer 3 id c11_4 run TdCellClick(player, 11, 4) keep open
		image "td:textures/gui/tile_build.png" at 108 2 size 24x24 texture 24x24 anchor center layer 3 id c12_4 run TdCellClick(player, 12, 4) keep open
		image "td:textures/gui/tile_build.png" at 132 2 size 24x24 texture 24x24 anchor center layer 3 id c13_4 run TdCellClick(player, 13, 4) keep open
		image "td:textures/gui/tile_build.png" at 156 2 size 24x24 texture 24x24 anchor center layer 3 id c14_4 run TdCellClick(player, 14, 4) keep open
		image "td:textures/gui/tile_build.png" at 180 2 size 24x24 texture 24x24 anchor center layer 3 id c15_4 run TdCellClick(player, 15, 4) keep open
		image "td:textures/gui/tile_build.png" at -180 26 size 24x24 texture 24x24 anchor center layer 3 id c0_5 run TdCellClick(player, 0, 5) keep open
		image "td:textures/gui/tile_build.png" at -156 26 size 24x24 texture 24x24 anchor center layer 3 id c1_5 run TdCellClick(player, 1, 5) keep open
		image "td:textures/gui/tile_build.png" at -108 26 size 24x24 texture 24x24 anchor center layer 3 id c3_5 run TdCellClick(player, 3, 5) keep open
		image "td:textures/gui/tile_build.png" at -84 26 size 24x24 texture 24x24 anchor center layer 3 id c4_5 run TdCellClick(player, 4, 5) keep open
		image "td:textures/gui/tile_build.png" at -60 26 size 24x24 texture 24x24 anchor center layer 3 id c5_5 run TdCellClick(player, 5, 5) keep open
		image "td:textures/gui/tile_build.png" at -36 26 size 24x24 texture 24x24 anchor center layer 3 id c6_5 run TdCellClick(player, 6, 5) keep open
		image "td:textures/gui/tile_build.png" at -12 26 size 24x24 texture 24x24 anchor center layer 3 id c7_5 run TdCellClick(player, 7, 5) keep open
		image "td:textures/gui/tile_build.png" at 12 26 size 24x24 texture 24x24 anchor center layer 3 id c8_5 run TdCellClick(player, 8, 5) keep open
		image "td:textures/gui/tile_build.png" at 36 26 size 24x24 texture 24x24 anchor center layer 3 id c9_5 run TdCellClick(player, 9, 5) keep open
		image "td:textures/gui/tile_build.png" at 60 26 size 24x24 texture 24x24 anchor center layer 3 id c10_5 run TdCellClick(player, 10, 5) keep open
		image "td:textures/gui/tile_build.png" at 84 26 size 24x24 texture 24x24 anchor center layer 3 id c11_5 run TdCellClick(player, 11, 5) keep open
		image "td:textures/gui/tile_build.png" at 108 26 size 24x24 texture 24x24 anchor center layer 3 id c12_5 run TdCellClick(player, 12, 5) keep open
		image "td:textures/gui/tile_build.png" at 132 26 size 24x24 texture 24x24 anchor center layer 3 id c13_5 run TdCellClick(player, 13, 5) keep open
		image "td:textures/gui/tile_build.png" at 156 26 size 24x24 texture 24x24 anchor center layer 3 id c14_5 run TdCellClick(player, 14, 5) keep open
		image "td:textures/gui/tile_build.png" at 180 26 size 24x24 texture 24x24 anchor center layer 3 id c15_5 run TdCellClick(player, 15, 5) keep open
		image "td:textures/gui/tile_build.png" at -180 50 size 24x24 texture 24x24 anchor center layer 3 id c0_6 run TdCellClick(player, 0, 6) keep open
		image "td:textures/gui/tile_build.png" at -156 50 size 24x24 texture 24x24 anchor center layer 3 id c1_6 run TdCellClick(player, 1, 6) keep open
		image "td:textures/gui/tile_build.png" at -180 74 size 24x24 texture 24x24 anchor center layer 3 id c0_7 run TdCellClick(player, 0, 7) keep open
		image "td:textures/gui/tile_build.png" at -156 74 size 24x24 texture 24x24 anchor center layer 3 id c1_7 run TdCellClick(player, 1, 7) keep open
		image "td:textures/gui/tile_build.png" at -132 74 size 24x24 texture 24x24 anchor center layer 3 id c2_7 run TdCellClick(player, 2, 7) keep open
		image "td:textures/gui/tile_build.png" at -108 74 size 24x24 texture 24x24 anchor center layer 3 id c3_7 run TdCellClick(player, 3, 7) keep open
		image "td:textures/gui/tile_build.png" at -84 74 size 24x24 texture 24x24 anchor center layer 3 id c4_7 run TdCellClick(player, 4, 7) keep open
		image "td:textures/gui/tile_build.png" at -60 74 size 24x24 texture 24x24 anchor center layer 3 id c5_7 run TdCellClick(player, 5, 7) keep open
		image "td:textures/gui/tile_build.png" at -36 74 size 24x24 texture 24x24 anchor center layer 3 id c6_7 run TdCellClick(player, 6, 7) keep open
		image "td:textures/gui/tile_build.png" at -12 74 size 24x24 texture 24x24 anchor center layer 3 id c7_7 run TdCellClick(player, 7, 7) keep open
		image "td:textures/gui/tile_build.png" at 12 74 size 24x24 texture 24x24 anchor center layer 3 id c8_7 run TdCellClick(player, 8, 7) keep open
		image "td:textures/gui/tile_build.png" at 36 74 size 24x24 texture 24x24 anchor center layer 3 id c9_7 run TdCellClick(player, 9, 7) keep open
		image "td:textures/gui/tile_build.png" at 60 74 size 24x24 texture 24x24 anchor center layer 3 id c10_7 run TdCellClick(player, 10, 7) keep open
		image "td:textures/gui/tile_build.png" at 84 74 size 24x24 texture 24x24 anchor center layer 3 id c11_7 run TdCellClick(player, 11, 7) keep open
		image "td:textures/gui/tile_build.png" at 108 74 size 24x24 texture 24x24 anchor center layer 3 id c12_7 run TdCellClick(player, 12, 7) keep open
		image "td:textures/gui/tile_build.png" at 132 74 size 24x24 texture 24x24 anchor center layer 3 id c13_7 run TdCellClick(player, 13, 7) keep open
		image "td:textures/gui/tile_build.png" at 156 74 size 24x24 texture 24x24 anchor center layer 3 id c14_7 run TdCellClick(player, 14, 7) keep open
		image "td:textures/gui/tile_build.png" at 180 74 size 24x24 texture 24x24 anchor center layer 3 id c15_7 run TdCellClick(player, 15, 7) keep open

		# Пул звёзд уровней для башен (до 24 башен)
		text "" at 0 0 anchor center shadow layer 6 id st1 hidden
		text "" at 0 0 anchor center shadow layer 6 id st2 hidden
		text "" at 0 0 anchor center shadow layer 6 id st3 hidden
		text "" at 0 0 anchor center shadow layer 6 id st4 hidden
		text "" at 0 0 anchor center shadow layer 6 id st5 hidden
		text "" at 0 0 anchor center shadow layer 6 id st6 hidden
		text "" at 0 0 anchor center shadow layer 6 id st7 hidden
		text "" at 0 0 anchor center shadow layer 6 id st8 hidden
		text "" at 0 0 anchor center shadow layer 6 id st9 hidden
		text "" at 0 0 anchor center shadow layer 6 id st10 hidden
		text "" at 0 0 anchor center shadow layer 6 id st11 hidden
		text "" at 0 0 anchor center shadow layer 6 id st12 hidden
		text "" at 0 0 anchor center shadow layer 6 id st13 hidden
		text "" at 0 0 anchor center shadow layer 6 id st14 hidden
		text "" at 0 0 anchor center shadow layer 6 id st15 hidden
		text "" at 0 0 anchor center shadow layer 6 id st16 hidden
		text "" at 0 0 anchor center shadow layer 6 id st17 hidden
		text "" at 0 0 anchor center shadow layer 6 id st18 hidden
		text "" at 0 0 anchor center shadow layer 6 id st19 hidden
		text "" at 0 0 anchor center shadow layer 6 id st20 hidden
		text "" at 0 0 anchor center shadow layer 6 id st21 hidden
		text "" at 0 0 anchor center shadow layer 6 id st22 hidden
		text "" at 0 0 anchor center shadow layer 6 id st23 hidden
		text "" at 0 0 anchor center shadow layer 6 id st24 hidden

		# 9 слотов врагов + 1 слот Босса
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e1 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e2 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e3 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e4 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e5 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e6 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e7 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e8 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e9 hidden
		image "td:textures/gui/mob_boss.png" at -180 -70 size 28x28 texture 28x28 anchor center layer 8 id e10 hidden

		# 6 летающих прожектайлов + вспышка взрыва
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 10x10 anchor center layer 9 id pr1 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 10x10 anchor center layer 9 id pr2 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 10x10 anchor center layer 9 id pr3 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 10x10 anchor center layer 9 id pr4 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 10x10 anchor center layer 9 id pr5 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 10x10 anchor center layer 9 id pr6 hidden
		image "td:textures/gui/fx_boom.png" at 0 0 size 16x16 texture 16x16 anchor center layer 10 id fx hidden

		# Статус и оверлей победы/поражения
		text "&eВыбран: &fЛучник (50з) &7— клик по любой траве строит, по башне улучшает!" at 0 92 anchor center shadow layer 12 id status_txt
		text "&c&lБАЗА ПАЛА!" at 0 -20 anchor center shadow layer 20 id end_title hidden
		text "&fНажми &eR &fили кнопку Меню" at 0 -4 anchor center shadow layer 20 id end_sub hidden

		# Компактная панель: 6 иконок башен + 3 кнопки + тумблер Авто-волны
		image "td:textures/gui/tower_archer.png" at -176 109 size 22x22 texture 24x24 anchor center layer 12 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e[1] Лучник &7(50з)\n&7Урон: 14 | Очень быстрый\n&7Дальность: 88"
		text "&e[1] 50" at -176 124 anchor center shadow layer 12 id lbl1
		image "td:textures/gui/tower_cannon.png" at -144 109 size 22x22 texture 24x24 anchor center layer 12 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6[2] Пушка &7(95з)\n&7Урон: 34 + &cСплеш по области\n&7Дальность: 82"
		text "&7[2] 95" at -144 124 anchor center shadow layer 12 id lbl2
		image "td:textures/gui/tower_frost.png" at -112 109 size 22x22 texture 24x24 anchor center layer 12 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b[3] Лёд &7(85з)\n&7Урон: 10 + &bЗамедление 45%\n&7Дальность: 86"
		text "&7[3] 85" at -112 124 anchor center shadow layer 12 id lbl3
		image "td:textures/gui/tower_mage.png" at -80 109 size 22x22 texture 24x24 anchor center layer 12 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d[4] Маг &7(130з)\n&7Урон: 30 | &dДальность 118\n&7Отлично бьёт боссов"
		text "&7[4] 130" at -80 124 anchor center shadow layer 12 id lbl4
		image "td:textures/gui/tower_tesla.png" at -48 109 size 22x22 texture 24x24 anchor center layer 12 id ico5 run TdSelectTool(player, 5) keep open tooltip "&b[5] Тесла &7(115з)\n&7Урон: 18 | &bЦепная молния (3 цели)\n&7Дальность: 96"
		text "&7[5] 115" at -48 124 anchor center shadow layer 12 id lbl5
		image "td:textures/gui/tower_sniper.png" at -16 109 size 22x22 texture 24x24 anchor center layer 12 id ico6 run TdSelectTool(player, 6) keep open tooltip "&c[6] Снайпер &7(150з)\n&7Урон: 52 | &cКрит 25% (x2)\n&7Огромная дальность: 190"
		text "&7[6] 150" at -16 124 anchor center shadow layer 12 id lbl6
		button "&aСтарт/x2" at 42 110 size 62x18 anchor center run TdWaveOrSpeed(player) keep open tooltip "&aЗапустить волну &7(Space)\n&bВо время волны: скорость x1 / x2"
		toggle "Авто" id autowave at 102 110 size 50x18 anchor center value false color #22C55E tooltip "&aАвто-волна\n&7Автоматически запускает следующую волну"
		button "&cПродать" at 148 110 size 36x18 anchor center run TdToggleSell(player) keep open tooltip "&cПродать башню &7(X)\n&7Возврат 70% стоимости"
		button "Меню" at 182 110 size 26x18 anchor center run TdOpenMenu(player) keep open tooltip "&eВыбор карты и сложности &7(M)"

function TdOpenMap2(p: player):
	open screen "Tower Defense" to p:
		image "td:textures/gui/map_volcano.png" at 0 -10 size 384x192 texture 384x192 anchor center layer 0 id map
		text "&eВолна: &f0/20   &6Золото: &f120   &cБаза: &f10 HP   &aСчёт: &f0   &7[x1]" at 0 -116 anchor center shadow layer 12 id hud_top
		bar "boss_bar" value 100 max 100 at 0 -98 size 200x8 anchor center color #E74C3C background #AA1A0A0A text "&c&lБОСС: %value%/%max%" shadow layer 14 hidden

		# Свободная сетка постройки (любая клетка вне дороги)
		image "td:textures/gui/tile_build.png" at -60 -94 size 24x24 texture 24x24 anchor center layer 3 id c5_0 run TdCellClick(player, 5, 0) keep open
		image "td:textures/gui/tile_build.png" at -36 -94 size 24x24 texture 24x24 anchor center layer 3 id c6_0 run TdCellClick(player, 6, 0) keep open
		image "td:textures/gui/tile_build.png" at -12 -94 size 24x24 texture 24x24 anchor center layer 3 id c7_0 run TdCellClick(player, 7, 0) keep open
		image "td:textures/gui/tile_build.png" at 12 -94 size 24x24 texture 24x24 anchor center layer 3 id c8_0 run TdCellClick(player, 8, 0) keep open
		image "td:textures/gui/tile_build.png" at 36 -94 size 24x24 texture 24x24 anchor center layer 3 id c9_0 run TdCellClick(player, 9, 0) keep open
		image "td:textures/gui/tile_build.png" at 60 -94 size 24x24 texture 24x24 anchor center layer 3 id c10_0 run TdCellClick(player, 10, 0) keep open
		image "td:textures/gui/tile_build.png" at 84 -94 size 24x24 texture 24x24 anchor center layer 3 id c11_0 run TdCellClick(player, 11, 0) keep open
		image "td:textures/gui/tile_build.png" at 108 -94 size 24x24 texture 24x24 anchor center layer 3 id c12_0 run TdCellClick(player, 12, 0) keep open
		image "td:textures/gui/tile_build.png" at 132 -94 size 24x24 texture 24x24 anchor center layer 3 id c13_0 run TdCellClick(player, 13, 0) keep open
		image "td:textures/gui/tile_build.png" at 156 -94 size 24x24 texture 24x24 anchor center layer 3 id c14_0 run TdCellClick(player, 14, 0) keep open
		image "td:textures/gui/tile_build.png" at 180 -94 size 24x24 texture 24x24 anchor center layer 3 id c15_0 run TdCellClick(player, 15, 0) keep open
		image "td:textures/gui/tile_build.png" at -180 -70 size 24x24 texture 24x24 anchor center layer 3 id c0_1 run TdCellClick(player, 0, 1) keep open
		image "td:textures/gui/tile_build.png" at -156 -70 size 24x24 texture 24x24 anchor center layer 3 id c1_1 run TdCellClick(player, 1, 1) keep open
		image "td:textures/gui/tile_build.png" at -132 -70 size 24x24 texture 24x24 anchor center layer 3 id c2_1 run TdCellClick(player, 2, 1) keep open
		image "td:textures/gui/tile_build.png" at -108 -70 size 24x24 texture 24x24 anchor center layer 3 id c3_1 run TdCellClick(player, 3, 1) keep open
		image "td:textures/gui/tile_build.png" at -60 -70 size 24x24 texture 24x24 anchor center layer 3 id c5_1 run TdCellClick(player, 5, 1) keep open
		image "td:textures/gui/tile_build.png" at -36 -70 size 24x24 texture 24x24 anchor center layer 3 id c6_1 run TdCellClick(player, 6, 1) keep open
		image "td:textures/gui/tile_build.png" at -12 -70 size 24x24 texture 24x24 anchor center layer 3 id c7_1 run TdCellClick(player, 7, 1) keep open
		image "td:textures/gui/tile_build.png" at 12 -70 size 24x24 texture 24x24 anchor center layer 3 id c8_1 run TdCellClick(player, 8, 1) keep open
		image "td:textures/gui/tile_build.png" at 156 -70 size 24x24 texture 24x24 anchor center layer 3 id c14_1 run TdCellClick(player, 14, 1) keep open
		image "td:textures/gui/tile_build.png" at 180 -70 size 24x24 texture 24x24 anchor center layer 3 id c15_1 run TdCellClick(player, 15, 1) keep open
		image "td:textures/gui/tile_build.png" at -180 -46 size 24x24 texture 24x24 anchor center layer 3 id c0_2 run TdCellClick(player, 0, 2) keep open
		image "td:textures/gui/tile_build.png" at -156 -46 size 24x24 texture 24x24 anchor center layer 3 id c1_2 run TdCellClick(player, 1, 2) keep open
		image "td:textures/gui/tile_build.png" at -132 -46 size 24x24 texture 24x24 anchor center layer 3 id c2_2 run TdCellClick(player, 2, 2) keep open
		image "td:textures/gui/tile_build.png" at -108 -46 size 24x24 texture 24x24 anchor center layer 3 id c3_2 run TdCellClick(player, 3, 2) keep open
		image "td:textures/gui/tile_build.png" at -60 -46 size 24x24 texture 24x24 anchor center layer 3 id c5_2 run TdCellClick(player, 5, 2) keep open
		image "td:textures/gui/tile_build.png" at -36 -46 size 24x24 texture 24x24 anchor center layer 3 id c6_2 run TdCellClick(player, 6, 2) keep open
		image "td:textures/gui/tile_build.png" at -12 -46 size 24x24 texture 24x24 anchor center layer 3 id c7_2 run TdCellClick(player, 7, 2) keep open
		image "td:textures/gui/tile_build.png" at 12 -46 size 24x24 texture 24x24 anchor center layer 3 id c8_2 run TdCellClick(player, 8, 2) keep open
		image "td:textures/gui/tile_build.png" at 60 -46 size 24x24 texture 24x24 anchor center layer 3 id c10_2 run TdCellClick(player, 10, 2) keep open
		image "td:textures/gui/tile_build.png" at 84 -46 size 24x24 texture 24x24 anchor center layer 3 id c11_2 run TdCellClick(player, 11, 2) keep open
		image "td:textures/gui/tile_build.png" at 108 -46 size 24x24 texture 24x24 anchor center layer 3 id c12_2 run TdCellClick(player, 12, 2) keep open
		image "td:textures/gui/tile_build.png" at 156 -46 size 24x24 texture 24x24 anchor center layer 3 id c14_2 run TdCellClick(player, 14, 2) keep open
		image "td:textures/gui/tile_build.png" at 180 -46 size 24x24 texture 24x24 anchor center layer 3 id c15_2 run TdCellClick(player, 15, 2) keep open
		image "td:textures/gui/tile_build.png" at -180 -22 size 24x24 texture 24x24 anchor center layer 3 id c0_3 run TdCellClick(player, 0, 3) keep open
		image "td:textures/gui/tile_build.png" at -156 -22 size 24x24 texture 24x24 anchor center layer 3 id c1_3 run TdCellClick(player, 1, 3) keep open
		image "td:textures/gui/tile_build.png" at -132 -22 size 24x24 texture 24x24 anchor center layer 3 id c2_3 run TdCellClick(player, 2, 3) keep open
		image "td:textures/gui/tile_build.png" at -108 -22 size 24x24 texture 24x24 anchor center layer 3 id c3_3 run TdCellClick(player, 3, 3) keep open
		image "td:textures/gui/tile_build.png" at -60 -22 size 24x24 texture 24x24 anchor center layer 3 id c5_3 run TdCellClick(player, 5, 3) keep open
		image "td:textures/gui/tile_build.png" at -36 -22 size 24x24 texture 24x24 anchor center layer 3 id c6_3 run TdCellClick(player, 6, 3) keep open
		image "td:textures/gui/tile_build.png" at -12 -22 size 24x24 texture 24x24 anchor center layer 3 id c7_3 run TdCellClick(player, 7, 3) keep open
		image "td:textures/gui/tile_build.png" at 12 -22 size 24x24 texture 24x24 anchor center layer 3 id c8_3 run TdCellClick(player, 8, 3) keep open
		image "td:textures/gui/tile_build.png" at 60 -22 size 24x24 texture 24x24 anchor center layer 3 id c10_3 run TdCellClick(player, 10, 3) keep open
		image "td:textures/gui/tile_build.png" at 84 -22 size 24x24 texture 24x24 anchor center layer 3 id c11_3 run TdCellClick(player, 11, 3) keep open
		image "td:textures/gui/tile_build.png" at 108 -22 size 24x24 texture 24x24 anchor center layer 3 id c12_3 run TdCellClick(player, 12, 3) keep open
		image "td:textures/gui/tile_build.png" at 156 -22 size 24x24 texture 24x24 anchor center layer 3 id c14_3 run TdCellClick(player, 14, 3) keep open
		image "td:textures/gui/tile_build.png" at 180 -22 size 24x24 texture 24x24 anchor center layer 3 id c15_3 run TdCellClick(player, 15, 3) keep open
		image "td:textures/gui/tile_build.png" at -180 2 size 24x24 texture 24x24 anchor center layer 3 id c0_4 run TdCellClick(player, 0, 4) keep open
		image "td:textures/gui/tile_build.png" at -156 2 size 24x24 texture 24x24 anchor center layer 3 id c1_4 run TdCellClick(player, 1, 4) keep open
		image "td:textures/gui/tile_build.png" at -132 2 size 24x24 texture 24x24 anchor center layer 3 id c2_4 run TdCellClick(player, 2, 4) keep open
		image "td:textures/gui/tile_build.png" at -108 2 size 24x24 texture 24x24 anchor center layer 3 id c3_4 run TdCellClick(player, 3, 4) keep open
		image "td:textures/gui/tile_build.png" at -60 2 size 24x24 texture 24x24 anchor center layer 3 id c5_4 run TdCellClick(player, 5, 4) keep open
		image "td:textures/gui/tile_build.png" at -36 2 size 24x24 texture 24x24 anchor center layer 3 id c6_4 run TdCellClick(player, 6, 4) keep open
		image "td:textures/gui/tile_build.png" at -12 2 size 24x24 texture 24x24 anchor center layer 3 id c7_4 run TdCellClick(player, 7, 4) keep open
		image "td:textures/gui/tile_build.png" at 12 2 size 24x24 texture 24x24 anchor center layer 3 id c8_4 run TdCellClick(player, 8, 4) keep open
		image "td:textures/gui/tile_build.png" at 60 2 size 24x24 texture 24x24 anchor center layer 3 id c10_4 run TdCellClick(player, 10, 4) keep open
		image "td:textures/gui/tile_build.png" at 84 2 size 24x24 texture 24x24 anchor center layer 3 id c11_4 run TdCellClick(player, 11, 4) keep open
		image "td:textures/gui/tile_build.png" at 108 2 size 24x24 texture 24x24 anchor center layer 3 id c12_4 run TdCellClick(player, 12, 4) keep open
		image "td:textures/gui/tile_build.png" at 156 2 size 24x24 texture 24x24 anchor center layer 3 id c14_4 run TdCellClick(player, 14, 4) keep open
		image "td:textures/gui/tile_build.png" at 180 2 size 24x24 texture 24x24 anchor center layer 3 id c15_4 run TdCellClick(player, 15, 4) keep open
		image "td:textures/gui/tile_build.png" at -180 26 size 24x24 texture 24x24 anchor center layer 3 id c0_5 run TdCellClick(player, 0, 5) keep open
		image "td:textures/gui/tile_build.png" at -156 26 size 24x24 texture 24x24 anchor center layer 3 id c1_5 run TdCellClick(player, 1, 5) keep open
		image "td:textures/gui/tile_build.png" at -132 26 size 24x24 texture 24x24 anchor center layer 3 id c2_5 run TdCellClick(player, 2, 5) keep open
		image "td:textures/gui/tile_build.png" at -108 26 size 24x24 texture 24x24 anchor center layer 3 id c3_5 run TdCellClick(player, 3, 5) keep open
		image "td:textures/gui/tile_build.png" at -60 26 size 24x24 texture 24x24 anchor center layer 3 id c5_5 run TdCellClick(player, 5, 5) keep open
		image "td:textures/gui/tile_build.png" at -36 26 size 24x24 texture 24x24 anchor center layer 3 id c6_5 run TdCellClick(player, 6, 5) keep open
		image "td:textures/gui/tile_build.png" at -12 26 size 24x24 texture 24x24 anchor center layer 3 id c7_5 run TdCellClick(player, 7, 5) keep open
		image "td:textures/gui/tile_build.png" at 12 26 size 24x24 texture 24x24 anchor center layer 3 id c8_5 run TdCellClick(player, 8, 5) keep open
		image "td:textures/gui/tile_build.png" at 60 26 size 24x24 texture 24x24 anchor center layer 3 id c10_5 run TdCellClick(player, 10, 5) keep open
		image "td:textures/gui/tile_build.png" at 84 26 size 24x24 texture 24x24 anchor center layer 3 id c11_5 run TdCellClick(player, 11, 5) keep open
		image "td:textures/gui/tile_build.png" at 108 26 size 24x24 texture 24x24 anchor center layer 3 id c12_5 run TdCellClick(player, 12, 5) keep open
		image "td:textures/gui/tile_build.png" at -180 50 size 24x24 texture 24x24 anchor center layer 3 id c0_6 run TdCellClick(player, 0, 6) keep open
		image "td:textures/gui/tile_build.png" at -156 50 size 24x24 texture 24x24 anchor center layer 3 id c1_6 run TdCellClick(player, 1, 6) keep open
		image "td:textures/gui/tile_build.png" at -132 50 size 24x24 texture 24x24 anchor center layer 3 id c2_6 run TdCellClick(player, 2, 6) keep open
		image "td:textures/gui/tile_build.png" at -108 50 size 24x24 texture 24x24 anchor center layer 3 id c3_6 run TdCellClick(player, 3, 6) keep open
		image "td:textures/gui/tile_build.png" at 60 50 size 24x24 texture 24x24 anchor center layer 3 id c10_6 run TdCellClick(player, 10, 6) keep open
		image "td:textures/gui/tile_build.png" at 84 50 size 24x24 texture 24x24 anchor center layer 3 id c11_6 run TdCellClick(player, 11, 6) keep open
		image "td:textures/gui/tile_build.png" at 108 50 size 24x24 texture 24x24 anchor center layer 3 id c12_6 run TdCellClick(player, 12, 6) keep open
		image "td:textures/gui/tile_build.png" at 132 50 size 24x24 texture 24x24 anchor center layer 3 id c13_6 run TdCellClick(player, 13, 6) keep open
		image "td:textures/gui/tile_build.png" at 156 50 size 24x24 texture 24x24 anchor center layer 3 id c14_6 run TdCellClick(player, 14, 6) keep open
		image "td:textures/gui/tile_build.png" at 180 50 size 24x24 texture 24x24 anchor center layer 3 id c15_6 run TdCellClick(player, 15, 6) keep open
		image "td:textures/gui/tile_build.png" at -180 74 size 24x24 texture 24x24 anchor center layer 3 id c0_7 run TdCellClick(player, 0, 7) keep open
		image "td:textures/gui/tile_build.png" at -156 74 size 24x24 texture 24x24 anchor center layer 3 id c1_7 run TdCellClick(player, 1, 7) keep open
		image "td:textures/gui/tile_build.png" at -132 74 size 24x24 texture 24x24 anchor center layer 3 id c2_7 run TdCellClick(player, 2, 7) keep open
		image "td:textures/gui/tile_build.png" at -108 74 size 24x24 texture 24x24 anchor center layer 3 id c3_7 run TdCellClick(player, 3, 7) keep open
		image "td:textures/gui/tile_build.png" at -84 74 size 24x24 texture 24x24 anchor center layer 3 id c4_7 run TdCellClick(player, 4, 7) keep open
		image "td:textures/gui/tile_build.png" at -60 74 size 24x24 texture 24x24 anchor center layer 3 id c5_7 run TdCellClick(player, 5, 7) keep open
		image "td:textures/gui/tile_build.png" at -36 74 size 24x24 texture 24x24 anchor center layer 3 id c6_7 run TdCellClick(player, 6, 7) keep open
		image "td:textures/gui/tile_build.png" at -12 74 size 24x24 texture 24x24 anchor center layer 3 id c7_7 run TdCellClick(player, 7, 7) keep open
		image "td:textures/gui/tile_build.png" at 12 74 size 24x24 texture 24x24 anchor center layer 3 id c8_7 run TdCellClick(player, 8, 7) keep open
		image "td:textures/gui/tile_build.png" at 36 74 size 24x24 texture 24x24 anchor center layer 3 id c9_7 run TdCellClick(player, 9, 7) keep open
		image "td:textures/gui/tile_build.png" at 60 74 size 24x24 texture 24x24 anchor center layer 3 id c10_7 run TdCellClick(player, 10, 7) keep open
		image "td:textures/gui/tile_build.png" at 84 74 size 24x24 texture 24x24 anchor center layer 3 id c11_7 run TdCellClick(player, 11, 7) keep open
		image "td:textures/gui/tile_build.png" at 108 74 size 24x24 texture 24x24 anchor center layer 3 id c12_7 run TdCellClick(player, 12, 7) keep open
		image "td:textures/gui/tile_build.png" at 132 74 size 24x24 texture 24x24 anchor center layer 3 id c13_7 run TdCellClick(player, 13, 7) keep open
		image "td:textures/gui/tile_build.png" at 156 74 size 24x24 texture 24x24 anchor center layer 3 id c14_7 run TdCellClick(player, 14, 7) keep open
		image "td:textures/gui/tile_build.png" at 180 74 size 24x24 texture 24x24 anchor center layer 3 id c15_7 run TdCellClick(player, 15, 7) keep open

		# Пул звёзд уровней для башен (до 24 башен)
		text "" at 0 0 anchor center shadow layer 6 id st1 hidden
		text "" at 0 0 anchor center shadow layer 6 id st2 hidden
		text "" at 0 0 anchor center shadow layer 6 id st3 hidden
		text "" at 0 0 anchor center shadow layer 6 id st4 hidden
		text "" at 0 0 anchor center shadow layer 6 id st5 hidden
		text "" at 0 0 anchor center shadow layer 6 id st6 hidden
		text "" at 0 0 anchor center shadow layer 6 id st7 hidden
		text "" at 0 0 anchor center shadow layer 6 id st8 hidden
		text "" at 0 0 anchor center shadow layer 6 id st9 hidden
		text "" at 0 0 anchor center shadow layer 6 id st10 hidden
		text "" at 0 0 anchor center shadow layer 6 id st11 hidden
		text "" at 0 0 anchor center shadow layer 6 id st12 hidden
		text "" at 0 0 anchor center shadow layer 6 id st13 hidden
		text "" at 0 0 anchor center shadow layer 6 id st14 hidden
		text "" at 0 0 anchor center shadow layer 6 id st15 hidden
		text "" at 0 0 anchor center shadow layer 6 id st16 hidden
		text "" at 0 0 anchor center shadow layer 6 id st17 hidden
		text "" at 0 0 anchor center shadow layer 6 id st18 hidden
		text "" at 0 0 anchor center shadow layer 6 id st19 hidden
		text "" at 0 0 anchor center shadow layer 6 id st20 hidden
		text "" at 0 0 anchor center shadow layer 6 id st21 hidden
		text "" at 0 0 anchor center shadow layer 6 id st22 hidden
		text "" at 0 0 anchor center shadow layer 6 id st23 hidden
		text "" at 0 0 anchor center shadow layer 6 id st24 hidden

		# 9 слотов врагов + 1 слот Босса
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e1 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e2 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e3 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e4 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e5 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e6 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e7 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e8 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e9 hidden
		image "td:textures/gui/mob_boss.png" at -180 -70 size 28x28 texture 28x28 anchor center layer 8 id e10 hidden

		# 6 летающих прожектайлов + вспышка взрыва
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 10x10 anchor center layer 9 id pr1 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 10x10 anchor center layer 9 id pr2 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 10x10 anchor center layer 9 id pr3 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 10x10 anchor center layer 9 id pr4 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 10x10 anchor center layer 9 id pr5 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 10x10 anchor center layer 9 id pr6 hidden
		image "td:textures/gui/fx_boom.png" at 0 0 size 16x16 texture 16x16 anchor center layer 10 id fx hidden

		# Статус и оверлей победы/поражения
		text "&eВыбран: &fЛучник (50з) &7— клик по любой траве строит, по башне улучшает!" at 0 92 anchor center shadow layer 12 id status_txt
		text "&c&lБАЗА ПАЛА!" at 0 -20 anchor center shadow layer 20 id end_title hidden
		text "&fНажми &eR &fили кнопку Меню" at 0 -4 anchor center shadow layer 20 id end_sub hidden

		# Компактная панель: 6 иконок башен + 3 кнопки + тумблер Авто-волны
		image "td:textures/gui/tower_archer.png" at -176 109 size 22x22 texture 24x24 anchor center layer 12 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e[1] Лучник &7(50з)\n&7Урон: 14 | Очень быстрый\n&7Дальность: 88"
		text "&e[1] 50" at -176 124 anchor center shadow layer 12 id lbl1
		image "td:textures/gui/tower_cannon.png" at -144 109 size 22x22 texture 24x24 anchor center layer 12 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6[2] Пушка &7(95з)\n&7Урон: 34 + &cСплеш по области\n&7Дальность: 82"
		text "&7[2] 95" at -144 124 anchor center shadow layer 12 id lbl2
		image "td:textures/gui/tower_frost.png" at -112 109 size 22x22 texture 24x24 anchor center layer 12 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b[3] Лёд &7(85з)\n&7Урон: 10 + &bЗамедление 45%\n&7Дальность: 86"
		text "&7[3] 85" at -112 124 anchor center shadow layer 12 id lbl3
		image "td:textures/gui/tower_mage.png" at -80 109 size 22x22 texture 24x24 anchor center layer 12 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d[4] Маг &7(130з)\n&7Урон: 30 | &dДальность 118\n&7Отлично бьёт боссов"
		text "&7[4] 130" at -80 124 anchor center shadow layer 12 id lbl4
		image "td:textures/gui/tower_tesla.png" at -48 109 size 22x22 texture 24x24 anchor center layer 12 id ico5 run TdSelectTool(player, 5) keep open tooltip "&b[5] Тесла &7(115з)\n&7Урон: 18 | &bЦепная молния (3 цели)\n&7Дальность: 96"
		text "&7[5] 115" at -48 124 anchor center shadow layer 12 id lbl5
		image "td:textures/gui/tower_sniper.png" at -16 109 size 22x22 texture 24x24 anchor center layer 12 id ico6 run TdSelectTool(player, 6) keep open tooltip "&c[6] Снайпер &7(150з)\n&7Урон: 52 | &cКрит 25% (x2)\n&7Огромная дальность: 190"
		text "&7[6] 150" at -16 124 anchor center shadow layer 12 id lbl6
		button "&aСтарт/x2" at 42 110 size 62x18 anchor center run TdWaveOrSpeed(player) keep open tooltip "&aЗапустить волну &7(Space)\n&bВо время волны: скорость x1 / x2"
		toggle "Авто" id autowave at 102 110 size 50x18 anchor center value false color #22C55E tooltip "&aАвто-волна\n&7Автоматически запускает следующую волну"
		button "&cПродать" at 148 110 size 36x18 anchor center run TdToggleSell(player) keep open tooltip "&cПродать башню &7(X)\n&7Возврат 70% стоимости"
		button "Меню" at 182 110 size 26x18 anchor center run TdOpenMenu(player) keep open tooltip "&eВыбор карты и сложности &7(M)"

function TdOpenMap3(p: player):
	open screen "Tower Defense" to p:
		image "td:textures/gui/map_ice.png" at 0 -10 size 384x192 texture 384x192 anchor center layer 0 id map
		text "&eВолна: &f0/20   &6Золото: &f120   &cБаза: &f10 HP   &aСчёт: &f0   &7[x1]" at 0 -116 anchor center shadow layer 12 id hud_top
		bar "boss_bar" value 100 max 100 at 0 -98 size 200x8 anchor center color #E74C3C background #AA1A0A0A text "&c&lБОСС: %value%/%max%" shadow layer 14 hidden

		# Свободная сетка постройки (любая клетка вне дороги)
		image "td:textures/gui/tile_build.png" at -180 -94 size 24x24 texture 24x24 anchor center layer 3 id c0_0 run TdCellClick(player, 0, 0) keep open
		image "td:textures/gui/tile_build.png" at -156 -94 size 24x24 texture 24x24 anchor center layer 3 id c1_0 run TdCellClick(player, 1, 0) keep open
		image "td:textures/gui/tile_build.png" at -132 -94 size 24x24 texture 24x24 anchor center layer 3 id c2_0 run TdCellClick(player, 2, 0) keep open
		image "td:textures/gui/tile_build.png" at -108 -94 size 24x24 texture 24x24 anchor center layer 3 id c3_0 run TdCellClick(player, 3, 0) keep open
		image "td:textures/gui/tile_build.png" at -84 -94 size 24x24 texture 24x24 anchor center layer 3 id c4_0 run TdCellClick(player, 4, 0) keep open
		image "td:textures/gui/tile_build.png" at -60 -94 size 24x24 texture 24x24 anchor center layer 3 id c5_0 run TdCellClick(player, 5, 0) keep open
		image "td:textures/gui/tile_build.png" at -36 -94 size 24x24 texture 24x24 anchor center layer 3 id c6_0 run TdCellClick(player, 6, 0) keep open
		image "td:textures/gui/tile_build.png" at -12 -94 size 24x24 texture 24x24 anchor center layer 3 id c7_0 run TdCellClick(player, 7, 0) keep open
		image "td:textures/gui/tile_build.png" at 12 -94 size 24x24 texture 24x24 anchor center layer 3 id c8_0 run TdCellClick(player, 8, 0) keep open
		image "td:textures/gui/tile_build.png" at 36 -94 size 24x24 texture 24x24 anchor center layer 3 id c9_0 run TdCellClick(player, 9, 0) keep open
		image "td:textures/gui/tile_build.png" at 60 -94 size 24x24 texture 24x24 anchor center layer 3 id c10_0 run TdCellClick(player, 10, 0) keep open
		image "td:textures/gui/tile_build.png" at 84 -94 size 24x24 texture 24x24 anchor center layer 3 id c11_0 run TdCellClick(player, 11, 0) keep open
		image "td:textures/gui/tile_build.png" at 108 -94 size 24x24 texture 24x24 anchor center layer 3 id c12_0 run TdCellClick(player, 12, 0) keep open
		image "td:textures/gui/tile_build.png" at 132 -94 size 24x24 texture 24x24 anchor center layer 3 id c13_0 run TdCellClick(player, 13, 0) keep open
		image "td:textures/gui/tile_build.png" at 156 -94 size 24x24 texture 24x24 anchor center layer 3 id c14_0 run TdCellClick(player, 14, 0) keep open
		image "td:textures/gui/tile_build.png" at 180 -94 size 24x24 texture 24x24 anchor center layer 3 id c15_0 run TdCellClick(player, 15, 0) keep open
		image "td:textures/gui/tile_build.png" at -180 -70 size 24x24 texture 24x24 anchor center layer 3 id c0_1 run TdCellClick(player, 0, 1) keep open
		image "td:textures/gui/tile_build.png" at -156 -70 size 24x24 texture 24x24 anchor center layer 3 id c1_1 run TdCellClick(player, 1, 1) keep open
		image "td:textures/gui/tile_build.png" at -132 -70 size 24x24 texture 24x24 anchor center layer 3 id c2_1 run TdCellClick(player, 2, 1) keep open
		image "td:textures/gui/tile_build.png" at 12 -70 size 24x24 texture 24x24 anchor center layer 3 id c8_1 run TdCellClick(player, 8, 1) keep open
		image "td:textures/gui/tile_build.png" at 36 -70 size 24x24 texture 24x24 anchor center layer 3 id c9_1 run TdCellClick(player, 9, 1) keep open
		image "td:textures/gui/tile_build.png" at 60 -70 size 24x24 texture 24x24 anchor center layer 3 id c10_1 run TdCellClick(player, 10, 1) keep open
		image "td:textures/gui/tile_build.png" at 84 -70 size 24x24 texture 24x24 anchor center layer 3 id c11_1 run TdCellClick(player, 11, 1) keep open
		image "td:textures/gui/tile_build.png" at 108 -70 size 24x24 texture 24x24 anchor center layer 3 id c12_1 run TdCellClick(player, 12, 1) keep open
		image "td:textures/gui/tile_build.png" at 132 -70 size 24x24 texture 24x24 anchor center layer 3 id c13_1 run TdCellClick(player, 13, 1) keep open
		image "td:textures/gui/tile_build.png" at 156 -70 size 24x24 texture 24x24 anchor center layer 3 id c14_1 run TdCellClick(player, 14, 1) keep open
		image "td:textures/gui/tile_build.png" at 180 -70 size 24x24 texture 24x24 anchor center layer 3 id c15_1 run TdCellClick(player, 15, 1) keep open
		image "td:textures/gui/tile_build.png" at -180 -46 size 24x24 texture 24x24 anchor center layer 3 id c0_2 run TdCellClick(player, 0, 2) keep open
		image "td:textures/gui/tile_build.png" at -156 -46 size 24x24 texture 24x24 anchor center layer 3 id c1_2 run TdCellClick(player, 1, 2) keep open
		image "td:textures/gui/tile_build.png" at -132 -46 size 24x24 texture 24x24 anchor center layer 3 id c2_2 run TdCellClick(player, 2, 2) keep open
		image "td:textures/gui/tile_build.png" at -84 -46 size 24x24 texture 24x24 anchor center layer 3 id c4_2 run TdCellClick(player, 4, 2) keep open
		image "td:textures/gui/tile_build.png" at -60 -46 size 24x24 texture 24x24 anchor center layer 3 id c5_2 run TdCellClick(player, 5, 2) keep open
		image "td:textures/gui/tile_build.png" at -36 -46 size 24x24 texture 24x24 anchor center layer 3 id c6_2 run TdCellClick(player, 6, 2) keep open
		image "td:textures/gui/tile_build.png" at 12 -46 size 24x24 texture 24x24 anchor center layer 3 id c8_2 run TdCellClick(player, 8, 2) keep open
		image "td:textures/gui/tile_build.png" at 36 -46 size 24x24 texture 24x24 anchor center layer 3 id c9_2 run TdCellClick(player, 9, 2) keep open
		image "td:textures/gui/tile_build.png" at 60 -46 size 24x24 texture 24x24 anchor center layer 3 id c10_2 run TdCellClick(player, 10, 2) keep open
		image "td:textures/gui/tile_build.png" at -180 -22 size 24x24 texture 24x24 anchor center layer 3 id c0_3 run TdCellClick(player, 0, 3) keep open
		image "td:textures/gui/tile_build.png" at -156 -22 size 24x24 texture 24x24 anchor center layer 3 id c1_3 run TdCellClick(player, 1, 3) keep open
		image "td:textures/gui/tile_build.png" at -132 -22 size 24x24 texture 24x24 anchor center layer 3 id c2_3 run TdCellClick(player, 2, 3) keep open
		image "td:textures/gui/tile_build.png" at -84 -22 size 24x24 texture 24x24 anchor center layer 3 id c4_3 run TdCellClick(player, 4, 3) keep open
		image "td:textures/gui/tile_build.png" at -60 -22 size 24x24 texture 24x24 anchor center layer 3 id c5_3 run TdCellClick(player, 5, 3) keep open
		image "td:textures/gui/tile_build.png" at -36 -22 size 24x24 texture 24x24 anchor center layer 3 id c6_3 run TdCellClick(player, 6, 3) keep open
		image "td:textures/gui/tile_build.png" at 12 -22 size 24x24 texture 24x24 anchor center layer 3 id c8_3 run TdCellClick(player, 8, 3) keep open
		image "td:textures/gui/tile_build.png" at 36 -22 size 24x24 texture 24x24 anchor center layer 3 id c9_3 run TdCellClick(player, 9, 3) keep open
		image "td:textures/gui/tile_build.png" at 60 -22 size 24x24 texture 24x24 anchor center layer 3 id c10_3 run TdCellClick(player, 10, 3) keep open
		image "td:textures/gui/tile_build.png" at 108 -22 size 24x24 texture 24x24 anchor center layer 3 id c12_3 run TdCellClick(player, 12, 3) keep open
		image "td:textures/gui/tile_build.png" at 132 -22 size 24x24 texture 24x24 anchor center layer 3 id c13_3 run TdCellClick(player, 13, 3) keep open
		image "td:textures/gui/tile_build.png" at 156 -22 size 24x24 texture 24x24 anchor center layer 3 id c14_3 run TdCellClick(player, 14, 3) keep open
		image "td:textures/gui/tile_build.png" at 180 -22 size 24x24 texture 24x24 anchor center layer 3 id c15_3 run TdCellClick(player, 15, 3) keep open
		image "td:textures/gui/tile_build.png" at -84 2 size 24x24 texture 24x24 anchor center layer 3 id c4_4 run TdCellClick(player, 4, 4) keep open
		image "td:textures/gui/tile_build.png" at -60 2 size 24x24 texture 24x24 anchor center layer 3 id c5_4 run TdCellClick(player, 5, 4) keep open
		image "td:textures/gui/tile_build.png" at -36 2 size 24x24 texture 24x24 anchor center layer 3 id c6_4 run TdCellClick(player, 6, 4) keep open
		image "td:textures/gui/tile_build.png" at 12 2 size 24x24 texture 24x24 anchor center layer 3 id c8_4 run TdCellClick(player, 8, 4) keep open
		image "td:textures/gui/tile_build.png" at 36 2 size 24x24 texture 24x24 anchor center layer 3 id c9_4 run TdCellClick(player, 9, 4) keep open
		image "td:textures/gui/tile_build.png" at 60 2 size 24x24 texture 24x24 anchor center layer 3 id c10_4 run TdCellClick(player, 10, 4) keep open
		image "td:textures/gui/tile_build.png" at 108 2 size 24x24 texture 24x24 anchor center layer 3 id c12_4 run TdCellClick(player, 12, 4) keep open
		image "td:textures/gui/tile_build.png" at 132 2 size 24x24 texture 24x24 anchor center layer 3 id c13_4 run TdCellClick(player, 13, 4) keep open
		image "td:textures/gui/tile_build.png" at 156 2 size 24x24 texture 24x24 anchor center layer 3 id c14_4 run TdCellClick(player, 14, 4) keep open
		image "td:textures/gui/tile_build.png" at 180 2 size 24x24 texture 24x24 anchor center layer 3 id c15_4 run TdCellClick(player, 15, 4) keep open
		image "td:textures/gui/tile_build.png" at -180 26 size 24x24 texture 24x24 anchor center layer 3 id c0_5 run TdCellClick(player, 0, 5) keep open
		image "td:textures/gui/tile_build.png" at -156 26 size 24x24 texture 24x24 anchor center layer 3 id c1_5 run TdCellClick(player, 1, 5) keep open
		image "td:textures/gui/tile_build.png" at -132 26 size 24x24 texture 24x24 anchor center layer 3 id c2_5 run TdCellClick(player, 2, 5) keep open
		image "td:textures/gui/tile_build.png" at -108 26 size 24x24 texture 24x24 anchor center layer 3 id c3_5 run TdCellClick(player, 3, 5) keep open
		image "td:textures/gui/tile_build.png" at -84 26 size 24x24 texture 24x24 anchor center layer 3 id c4_5 run TdCellClick(player, 4, 5) keep open
		image "td:textures/gui/tile_build.png" at -60 26 size 24x24 texture 24x24 anchor center layer 3 id c5_5 run TdCellClick(player, 5, 5) keep open
		image "td:textures/gui/tile_build.png" at -36 26 size 24x24 texture 24x24 anchor center layer 3 id c6_5 run TdCellClick(player, 6, 5) keep open
		image "td:textures/gui/tile_build.png" at 12 26 size 24x24 texture 24x24 anchor center layer 3 id c8_5 run TdCellClick(player, 8, 5) keep open
		image "td:textures/gui/tile_build.png" at 36 26 size 24x24 texture 24x24 anchor center layer 3 id c9_5 run TdCellClick(player, 9, 5) keep open
		image "td:textures/gui/tile_build.png" at 60 26 size 24x24 texture 24x24 anchor center layer 3 id c10_5 run TdCellClick(player, 10, 5) keep open
		image "td:textures/gui/tile_build.png" at 108 26 size 24x24 texture 24x24 anchor center layer 3 id c12_5 run TdCellClick(player, 12, 5) keep open
		image "td:textures/gui/tile_build.png" at 132 26 size 24x24 texture 24x24 anchor center layer 3 id c13_5 run TdCellClick(player, 13, 5) keep open
		image "td:textures/gui/tile_build.png" at 156 26 size 24x24 texture 24x24 anchor center layer 3 id c14_5 run TdCellClick(player, 14, 5) keep open
		image "td:textures/gui/tile_build.png" at 180 26 size 24x24 texture 24x24 anchor center layer 3 id c15_5 run TdCellClick(player, 15, 5) keep open
		image "td:textures/gui/tile_build.png" at -180 50 size 24x24 texture 24x24 anchor center layer 3 id c0_6 run TdCellClick(player, 0, 6) keep open
		image "td:textures/gui/tile_build.png" at -156 50 size 24x24 texture 24x24 anchor center layer 3 id c1_6 run TdCellClick(player, 1, 6) keep open
		image "td:textures/gui/tile_build.png" at -132 50 size 24x24 texture 24x24 anchor center layer 3 id c2_6 run TdCellClick(player, 2, 6) keep open
		image "td:textures/gui/tile_build.png" at -108 50 size 24x24 texture 24x24 anchor center layer 3 id c3_6 run TdCellClick(player, 3, 6) keep open
		image "td:textures/gui/tile_build.png" at -84 50 size 24x24 texture 24x24 anchor center layer 3 id c4_6 run TdCellClick(player, 4, 6) keep open
		image "td:textures/gui/tile_build.png" at -60 50 size 24x24 texture 24x24 anchor center layer 3 id c5_6 run TdCellClick(player, 5, 6) keep open
		image "td:textures/gui/tile_build.png" at -36 50 size 24x24 texture 24x24 anchor center layer 3 id c6_6 run TdCellClick(player, 6, 6) keep open
		image "td:textures/gui/tile_build.png" at 108 50 size 24x24 texture 24x24 anchor center layer 3 id c12_6 run TdCellClick(player, 12, 6) keep open
		image "td:textures/gui/tile_build.png" at 132 50 size 24x24 texture 24x24 anchor center layer 3 id c13_6 run TdCellClick(player, 13, 6) keep open
		image "td:textures/gui/tile_build.png" at 156 50 size 24x24 texture 24x24 anchor center layer 3 id c14_6 run TdCellClick(player, 14, 6) keep open
		image "td:textures/gui/tile_build.png" at 180 50 size 24x24 texture 24x24 anchor center layer 3 id c15_6 run TdCellClick(player, 15, 6) keep open
		image "td:textures/gui/tile_build.png" at -180 74 size 24x24 texture 24x24 anchor center layer 3 id c0_7 run TdCellClick(player, 0, 7) keep open
		image "td:textures/gui/tile_build.png" at -156 74 size 24x24 texture 24x24 anchor center layer 3 id c1_7 run TdCellClick(player, 1, 7) keep open
		image "td:textures/gui/tile_build.png" at -132 74 size 24x24 texture 24x24 anchor center layer 3 id c2_7 run TdCellClick(player, 2, 7) keep open
		image "td:textures/gui/tile_build.png" at -108 74 size 24x24 texture 24x24 anchor center layer 3 id c3_7 run TdCellClick(player, 3, 7) keep open
		image "td:textures/gui/tile_build.png" at -84 74 size 24x24 texture 24x24 anchor center layer 3 id c4_7 run TdCellClick(player, 4, 7) keep open
		image "td:textures/gui/tile_build.png" at -60 74 size 24x24 texture 24x24 anchor center layer 3 id c5_7 run TdCellClick(player, 5, 7) keep open
		image "td:textures/gui/tile_build.png" at -36 74 size 24x24 texture 24x24 anchor center layer 3 id c6_7 run TdCellClick(player, 6, 7) keep open
		image "td:textures/gui/tile_build.png" at -12 74 size 24x24 texture 24x24 anchor center layer 3 id c7_7 run TdCellClick(player, 7, 7) keep open
		image "td:textures/gui/tile_build.png" at 12 74 size 24x24 texture 24x24 anchor center layer 3 id c8_7 run TdCellClick(player, 8, 7) keep open
		image "td:textures/gui/tile_build.png" at 36 74 size 24x24 texture 24x24 anchor center layer 3 id c9_7 run TdCellClick(player, 9, 7) keep open
		image "td:textures/gui/tile_build.png" at 60 74 size 24x24 texture 24x24 anchor center layer 3 id c10_7 run TdCellClick(player, 10, 7) keep open
		image "td:textures/gui/tile_build.png" at 84 74 size 24x24 texture 24x24 anchor center layer 3 id c11_7 run TdCellClick(player, 11, 7) keep open
		image "td:textures/gui/tile_build.png" at 108 74 size 24x24 texture 24x24 anchor center layer 3 id c12_7 run TdCellClick(player, 12, 7) keep open
		image "td:textures/gui/tile_build.png" at 132 74 size 24x24 texture 24x24 anchor center layer 3 id c13_7 run TdCellClick(player, 13, 7) keep open
		image "td:textures/gui/tile_build.png" at 156 74 size 24x24 texture 24x24 anchor center layer 3 id c14_7 run TdCellClick(player, 14, 7) keep open
		image "td:textures/gui/tile_build.png" at 180 74 size 24x24 texture 24x24 anchor center layer 3 id c15_7 run TdCellClick(player, 15, 7) keep open

		# Пул звёзд уровней для башен (до 24 башен)
		text "" at 0 0 anchor center shadow layer 6 id st1 hidden
		text "" at 0 0 anchor center shadow layer 6 id st2 hidden
		text "" at 0 0 anchor center shadow layer 6 id st3 hidden
		text "" at 0 0 anchor center shadow layer 6 id st4 hidden
		text "" at 0 0 anchor center shadow layer 6 id st5 hidden
		text "" at 0 0 anchor center shadow layer 6 id st6 hidden
		text "" at 0 0 anchor center shadow layer 6 id st7 hidden
		text "" at 0 0 anchor center shadow layer 6 id st8 hidden
		text "" at 0 0 anchor center shadow layer 6 id st9 hidden
		text "" at 0 0 anchor center shadow layer 6 id st10 hidden
		text "" at 0 0 anchor center shadow layer 6 id st11 hidden
		text "" at 0 0 anchor center shadow layer 6 id st12 hidden
		text "" at 0 0 anchor center shadow layer 6 id st13 hidden
		text "" at 0 0 anchor center shadow layer 6 id st14 hidden
		text "" at 0 0 anchor center shadow layer 6 id st15 hidden
		text "" at 0 0 anchor center shadow layer 6 id st16 hidden
		text "" at 0 0 anchor center shadow layer 6 id st17 hidden
		text "" at 0 0 anchor center shadow layer 6 id st18 hidden
		text "" at 0 0 anchor center shadow layer 6 id st19 hidden
		text "" at 0 0 anchor center shadow layer 6 id st20 hidden
		text "" at 0 0 anchor center shadow layer 6 id st21 hidden
		text "" at 0 0 anchor center shadow layer 6 id st22 hidden
		text "" at 0 0 anchor center shadow layer 6 id st23 hidden
		text "" at 0 0 anchor center shadow layer 6 id st24 hidden

		# 9 слотов врагов + 1 слот Босса
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e1 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e2 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e3 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e4 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e5 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e6 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e7 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e8 hidden
		image "td:textures/gui/mob_slime.png" at -180 -70 size 20x20 texture 20x20 anchor center layer 7 id e9 hidden
		image "td:textures/gui/mob_boss.png" at -180 -70 size 28x28 texture 28x28 anchor center layer 8 id e10 hidden

		# 6 летающих прожектайлов + вспышка взрыва
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 10x10 anchor center layer 9 id pr1 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 10x10 anchor center layer 9 id pr2 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 10x10 anchor center layer 9 id pr3 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 10x10 anchor center layer 9 id pr4 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 10x10 anchor center layer 9 id pr5 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 10x10 anchor center layer 9 id pr6 hidden
		image "td:textures/gui/fx_boom.png" at 0 0 size 16x16 texture 16x16 anchor center layer 10 id fx hidden

		# Статус и оверлей победы/поражения
		text "&eВыбран: &fЛучник (50з) &7— клик по любой траве строит, по башне улучшает!" at 0 92 anchor center shadow layer 12 id status_txt
		text "&c&lБАЗА ПАЛА!" at 0 -20 anchor center shadow layer 20 id end_title hidden
		text "&fНажми &eR &fили кнопку Меню" at 0 -4 anchor center shadow layer 20 id end_sub hidden

		# Компактная панель: 6 иконок башен + 3 кнопки + тумблер Авто-волны
		image "td:textures/gui/tower_archer.png" at -176 109 size 22x22 texture 24x24 anchor center layer 12 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e[1] Лучник &7(50з)\n&7Урон: 14 | Очень быстрый\n&7Дальность: 88"
		text "&e[1] 50" at -176 124 anchor center shadow layer 12 id lbl1
		image "td:textures/gui/tower_cannon.png" at -144 109 size 22x22 texture 24x24 anchor center layer 12 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6[2] Пушка &7(95з)\n&7Урон: 34 + &cСплеш по области\n&7Дальность: 82"
		text "&7[2] 95" at -144 124 anchor center shadow layer 12 id lbl2
		image "td:textures/gui/tower_frost.png" at -112 109 size 22x22 texture 24x24 anchor center layer 12 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b[3] Лёд &7(85з)\n&7Урон: 10 + &bЗамедление 45%\n&7Дальность: 86"
		text "&7[3] 85" at -112 124 anchor center shadow layer 12 id lbl3
		image "td:textures/gui/tower_mage.png" at -80 109 size 22x22 texture 24x24 anchor center layer 12 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d[4] Маг &7(130з)\n&7Урон: 30 | &dДальность 118\n&7Отлично бьёт боссов"
		text "&7[4] 130" at -80 124 anchor center shadow layer 12 id lbl4
		image "td:textures/gui/tower_tesla.png" at -48 109 size 22x22 texture 24x24 anchor center layer 12 id ico5 run TdSelectTool(player, 5) keep open tooltip "&b[5] Тесла &7(115з)\n&7Урон: 18 | &bЦепная молния (3 цели)\n&7Дальность: 96"
		text "&7[5] 115" at -48 124 anchor center shadow layer 12 id lbl5
		image "td:textures/gui/tower_sniper.png" at -16 109 size 22x22 texture 24x24 anchor center layer 12 id ico6 run TdSelectTool(player, 6) keep open tooltip "&c[6] Снайпер &7(150з)\n&7Урон: 52 | &cКрит 25% (x2)\n&7Огромная дальность: 190"
		text "&7[6] 150" at -16 124 anchor center shadow layer 12 id lbl6
		button "&aСтарт/x2" at 42 110 size 62x18 anchor center run TdWaveOrSpeed(player) keep open tooltip "&aЗапустить волну &7(Space)\n&bВо время волны: скорость x1 / x2"
		toggle "Авто" id autowave at 102 110 size 50x18 anchor center value false color #22C55E tooltip "&aАвто-волна\n&7Автоматически запускает следующую волну"
		button "&cПродать" at 148 110 size 36x18 anchor center run TdToggleSell(player) keep open tooltip "&cПродать башню &7(X)\n&7Возврат 70% стоимости"
		button "Меню" at 182 110 size 26x18 anchor center run TdOpenMenu(player) keep open tooltip "&eВыбор карты и сложности &7(M)"

# ==========================================================
#  УПРАВЛЕНИЕ, СТРОИТЕЛЬСТВО И ПРОДАЖА
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

function TdSelectTool(p: player, tool: number):
	if {-td-active::%p%} is not 1:
		stop
	set {-td-tool::%p%} to tool
	set {-td-sell::%p%} to 0
	set text of screen element "lbl1" for p to "&7[1] 50"
	set text of screen element "lbl2" for p to "&7[2] 95"
	set text of screen element "lbl3" for p to "&7[3] 85"
	set text of screen element "lbl4" for p to "&7[4] 130"
	set text of screen element "lbl5" for p to "&7[5] 115"
	set text of screen element "lbl6" for p to "&7[6] 150"
	if tool is 1:
		set text of screen element "lbl1" for p to "&e[1] 50"
		set text of screen element "status_txt" for p to "&eВыбран: &fЛучник (50з) &7— быстрый урон по 1 цели"
	else if tool is 2:
		set text of screen element "lbl2" for p to "&6[2] 95"
		set text of screen element "status_txt" for p to "&6Выбран: &fПушка (95з) &7— сплеш по области"
	else if tool is 3:
		set text of screen element "lbl3" for p to "&b[3] 85"
		set text of screen element "status_txt" for p to "&bВыбран: &fЛёд (85з) &7— замедляет врагов на 45%%"
	else if tool is 4:
		set text of screen element "lbl4" for p to "&d[4] 130"
		set text of screen element "status_txt" for p to "&dВыбран: &fМаг (130з) &7— дальнобойный шар против боссов"
	else if tool is 5:
		set text of screen element "lbl5" for p to "&b[5] 115"
		set text of screen element "status_txt" for p to "&bВыбран: &fТесла (115з) &7— цепная молния по 3 целям!"
	else if tool is 6:
		set text of screen element "lbl6" for p to "&c[6] 150"
		set text of screen element "status_txt" for p to "&cВыбран: &fСнайпер (150з) &7— бьёт почти на всю карту + крит x2!"
	play sound ui.button.click to p with volume 0.4 pitch 1.3

function TdToggleSell(p: player):
	if {-td-active::%p%} is not 1:
		stop
	if {-td-sell::%p%} is 1:
		set {-td-sell::%p%} to 0
		TdSelectTool(p, {-td-tool::%p%})
	else:
		set {-td-sell::%p%} to 1
		set text of screen element "status_txt" for p to "&c&lПРОДАЖА &7— клик по башне вернёт 70%% (X — отмена)"
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

function TdCellClick(p: player, col: number, row: number):
	if {-td-active::%p%} is not 1 or {-td-over::%p%} is 1:
		stop
	set {-tid} to {-td-grid::%p%::%col%_%row%}
	if {-tid} is not set:
		set {-tid} to 0

	# Режим продажи
	if {-td-sell::%p%} is 1:
		if {-tid} is 0:
			set text of screen element "status_txt" for p to "&7Здесь нет башни"
			stop
		set {-ref} to round({-td-tcost::%p%::%{-tid}%} * 0.7)
		add {-ref} to {-td-gold::%p%}
		set {-td-ta::%p%::%{-tid}%} to 0
		set {-td-tt::%p%::%{-tid}%} to 0
		set {-td-tl::%p%::%{-tid}%} to 0
		set {-td-tcost::%p%::%{-tid}%} to 0
		set {-td-grid::%p%::%col%_%row%} to 0
		set {-td-sell::%p%} to 0
		set image of screen element "c%col%_%row%" for p to "td:textures/gui/tile_build.png"
		hide screen element "st%{-tid}%" from p
		set text of screen element "status_txt" for p to "&eБашня продана за +%{-ref}% золота"
		play sound entity.experience_orb.pickup to p with volume 0.6 pitch 0.9
		TdUpdateHud(p)
		stop

	# Если клетка занята — сразу УЛУЧШАЕМ башню (до 4 ур.)
	if {-tid} > 0:
		set {-lvl} to {-td-tl::%p%::%{-tid}%}
		if {-lvl} >= 4:
			set text of screen element "status_txt" for p to "&dБашня уже макс. уровня (★★★★)!"
			stop
		set {-up} to 65
		if {-lvl} is 2:
			set {-up} to 140
		else if {-lvl} is 3:
			set {-up} to 240
		if {-td-gold::%p%} < {-up}:
			set {-nl} to {-lvl} + 1
			set text of screen element "status_txt" for p to "&cДля ур.%{-nl}% нужно %{-up}%з!"
			play sound entity.villager.no to p with volume 0.5 pitch 1.0
			stop
		remove {-up} from {-td-gold::%p%}
		add {-up} to {-td-tcost::%p%::%{-tid}%}
		add 1 to {-td-tl::%p%::%{-tid}%}
		set {-nl} to {-td-tl::%p%::%{-tid}%}
		if {-nl} is 2:
			set text of screen element "st%{-tid}%" for p to "&6★★"
		else if {-nl} is 3:
			set text of screen element "st%{-tid}%" for p to "&d★★★"
		else:
			set text of screen element "st%{-tid}%" for p to "&b★★★★"
		set text of screen element "status_txt" for p to "&aБашня улучшена до ур.%{-nl}%! (-%{-up}%з)"
		play sound entity.player.levelup to p with volume 0.5 pitch 1.4
		TdUpdateHud(p)
		stop

	# Ищем свободный слот башни (макс. 24 на карте)
	set {-slot} to 0
	loop 24 times:
		set {-k} to %loop-number%
		if {-td-ta::%p%::%{-k}%} is 0 and {-slot} is 0:
			set {-slot} to {-k}
	if {-slot} is 0:
		set text of screen element "status_txt" for p to "&cМаксимум 24 башни на карте! Улучшайте существующие"
		stop

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
	else if {-tool} is 5:
		set {-cost} to 115
		set {-tex} to "td:textures/gui/tower_tesla.png"
	else if {-tool} is 6:
		set {-cost} to 150
		set {-tex} to "td:textures/gui/tower_sniper.png"

	if {-td-gold::%p%} < {-cost}:
		set text of screen element "status_txt" for p to "&cНе хватает золота! Нужно %{-cost}%з"
		play sound entity.villager.no to p with volume 0.5 pitch 1.0
		stop

	remove {-cost} from {-td-gold::%p%}
	set {-cx} to -180 + col * 24
	set {-cy} to -94 + row * 24
	set {-td-ta::%p%::%{-slot}%} to 1
	set {-td-tt::%p%::%{-slot}%} to {-tool}
	set {-td-tl::%p%::%{-slot}%} to 1
	set {-td-tcd::%p%::%{-slot}%} to 0
	set {-td-tcost::%p%::%{-slot}%} to {-cost}
	set {-td-tx::%p%::%{-slot}%} to {-cx}
	set {-td-ty::%p%::%{-slot}%} to {-cy}
	set {-td-grid::%p%::%col%_%row%} to {-slot}

	set image of screen element "c%col%_%row%" for p to {-tex}
	set {-sy} to {-cy} + 10
	set position of screen element "st%{-slot}%" for p to {-cx} {-sy}
	set text of screen element "st%{-slot}%" for p to "&e★"
	show screen element "st%{-slot}%" to p
	set text of screen element "status_txt" for p to "&aБашня построена! Клик по ней улучшит за 65з"
	play sound block.anvil.use to p with volume 0.35 pitch 1.5
	TdUpdateHud(p)

# ==========================================================
#  ВОЛНЫ И СПАВН (10 / 20 / 30 ВОЛН + 6 ВИДОВ МОБОВ + 2 БОССА)
# ==========================================================

function TdNextWave(p: player):
	if {-td-wave::%p%} >= {-td-maxw::%p%}:
		stop
	add 1 to {-td-wave::%p%}
	set {-w} to {-td-wave::%p%}
	set {-mw} to {-td-maxw::%p%}
	set {-td-in-wave::%p%} to 1
	set {-td-auto-cd::%p%} to 0
	set {-td-spawn-cd::%p%} to 1
	set {-td-spawn-boss::%p%} to 0

	set {-cnt} to 5 + round({-w} * 0.65)
	set {-td-to-spawn::%p%} to {-cnt}

	# Босс каждые 5 волн (5, 10, 15, 20, 25, 30)
	set {-rem5} to {-w} % 5
	if {-rem5} is 0:
		set {-bkind} to round({-w} / 5)
		set {-td-spawn-boss::%p%} to {-bkind}
		if {-w} is {-mw}:
			set text of screen element "status_txt" for p to "&4&lФИНАЛЬНАЯ ВОЛНА %{-w}%/%{-mw}% — МЕГА-БОСС!"
			play sound entity.wither.spawn to p with volume 0.55 pitch 0.85
		else:
			set text of screen element "status_txt" for p to "&c&lВОЛНА %{-w}%/%{-mw}% — БОСС ВЫХОДИТ НА ПОЛЕ!"
			play sound entity.ender_dragon.growl to p with volume 0.5 pitch 1.1
	else:
		set text of screen element "status_txt" for p to "&eВолна %{-w}%/%{-mw}% началась! &7(Space — скорость x1/x2)"
		play sound entity.experience_orb.pickup to p with volume 0.5 pitch 1.25
	TdUpdateHud(p)

function TdUpdateHud(p: player):
	set {-sp} to "x1"
	if {-td-speed::%p%} is 2:
		set {-sp} to "&b&lx2"
	set text of screen element "hud_top" for p to "&eВолна: &f%{-td-wave::%p%}%/%{-td-maxw::%p%}%   &6Золото: &f%{-td-gold::%p%}%   &cБаза: &f%{-td-hp::%p%}% HP   &aСчёт: &f%{-td-score::%p%}%   &7[%{-sp}%&7]"

# ==========================================================
#  ОСНОВНОЙ ТИК ИГРЫ
# ==========================================================

function TdTick(p: player):
	if {-td-over::%p%} is 1:
		stop

	if {-td-fx-cd::%p%} > 0:
		remove 1 from {-td-fx-cd::%p%}
		if {-td-fx-cd::%p%} <= 0:
			hide screen element "fx" from p

	# Авто-старт следующей волны, если включён тумблер Авто
	if {-td-in-wave::%p%} is 0:
		if {-td-auto::%p%} is 1 and {-td-wave::%p%} > 0 and {-td-wave::%p%} < {-td-maxw::%p%}:
			add 1 to {-td-auto-cd::%p%}
			if {-td-auto-cd::%p%} >= 15:
				TdNextWave(p)
		stop

	# 1. Спавн врагов
	if {-td-to-spawn::%p%} > 0 or {-td-spawn-boss::%p%} > 0:
		remove 1 from {-td-spawn-cd::%p%}
		if {-td-spawn-cd::%p%} <= 0:
			TdSpawnNext(p)

	# 2. Движение врагов по точкам маршрута
	set {-boss-alive} to 0
	loop 10 times:
		set {-ei} to %loop-number%
		if {-td-ea::%p%::%{-ei}%} is 1:
			TdMoveEnemy(p, {-ei})
			if {-td-ea::%p%::%{-ei}%} is 1 and {-td-eboss::%p%::%{-ei}%} is 1:
				set {-boss-alive} to 1
				set value of screen bar "boss_bar" for p to {-td-ehp::%p%::%{-ei}%}
	if {-boss-alive} is 0:
		hide screen element "boss_bar" from p

	# 3. Полёт прожектайлов и попадания
	TdMoveProjectiles(p)

	# 4. Стрельба всех активных башен
	loop 24 times:
		set {-ti} to %loop-number%
		if {-td-ta::%p%::%{-ti}%} is 1:
			TdTowerTick(p, {-ti})

	# 5. Завершение волны
	if {-td-to-spawn::%p%} <= 0 and {-td-spawn-boss::%p%} <= 0:
		set {-rem} to 0
		loop 10 times:
			set {-k} to %loop-number%
			if {-td-ea::%p%::%{-k}%} is 1:
				add 1 to {-rem}
		if {-rem} is 0:
			set {-td-in-wave::%p%} to 0
			set {-td-auto-cd::%p%} to 0
			TdClearProjectiles(p)
			set {-bonus} to 18 + {-td-wave::%p%} * 6
			add {-bonus} to {-td-gold::%p%}
			add 100 + {-td-wave::%p%} * 15 to {-td-score::%p%}
			TdUpdateHud(p)
			if {-td-wave::%p%} >= {-td-maxw::%p%}:
				TdWin(p)
			else:
				set text of screen element "status_txt" for p to "&aВолна %{-td-wave::%p%}%/%{-td-maxw::%p%}% пройдена! +%{-bonus}%з. Жмите &eSpace &aдля следующей!"
				play sound entity.player.levelup to p with volume 0.5 pitch 1.25

function TdSpawnNext(p: player):
	set {-w} to {-td-wave::%p%}
	set {-d} to {-td-diff::%p%}
	set {-dmul} to 1.0
	if {-d} is 1:
		set {-dmul} to 0.85
	else if {-d} is 3:
		set {-dmul} to 1.25

	set {-sx} to {-td-wpx::%p%::1}
	set {-sy} to {-td-wpy::%p%::1}

	# Обычные мобы (слоты 1..9)
	if {-td-to-spawn::%p%} > 0:
		set {-slot} to 0
		loop 9 times:
			set {-k} to %loop-number%
			if {-td-ea::%p%::%{-k}%} is 0 and {-slot} is 0:
				set {-slot} to {-k}
		if {-slot} is 0:
			set {-td-spawn-cd::%p%} to 2
			stop
		remove 1 from {-td-to-spawn::%p%}

		set {-hp} to round((32 + {-w} * 24 + {-w} * {-w} * 2.5) * {-dmul})
		set {-spd} to 3.6 + {-w} * 0.08
		set {-rew} to 10 + round({-w} * 0.65)
		set {-cd} to 8 - floor({-w} / 5)
		if {-cd} < 3:
			set {-cd} to 3
		set {-tex} to "td:textures/gui/mob_slime.png"

		# 6 типов мобов по волнам
		set {-m6} to {-w} % 6
		if {-m6} is 2:
			# Гоблин-бегун (очень быстрый!)
			set {-tex} to "td:textures/gui/mob_goblin.png"
			set {-hp} to round({-hp} * 0.72)
			set {-spd} to {-spd} + 1.45
			set {-cd} to 4
		else if {-m6} is 3:
			# Орк-воин
			set {-tex} to "td:textures/gui/mob_orc.png"
			set {-hp} to round({-hp} * 1.15)
		else if {-m6} is 4:
			# Призрак (быстрый и хитрый)
			set {-tex} to "td:textures/gui/mob_ghost.png"
			set {-hp} to round({-hp} * 0.88)
			set {-spd} to {-spd} + 1.0
		else if {-m6} is 5:
			# Тёмный рыцарь (броня)
			set {-tex} to "td:textures/gui/mob_knight.png"
			set {-hp} to round({-hp} * 1.32)
			set {-spd} to {-spd} - 0.3
		else if {-m6} is 0:
			# Лавовый голем (жирный танк!)
			set {-tex} to "td:textures/gui/mob_golem.png"
			set {-hp} to round({-hp} * 1.65)
			set {-spd} to {-spd} - 0.7
			add 5 to {-rew}

		set {-td-spawn-cd::%p%} to {-cd}
		set {-td-ea::%p%::%{-slot}%} to 1
		set {-td-eboss::%p%::%{-slot}%} to 0
		set {-td-ex::%p%::%{-slot}%} to {-sx}
		set {-td-ey::%p%::%{-slot}%} to {-sy}
		set {-td-eseg::%p%::%{-slot}%} to 2
		set {-td-ehp::%p%::%{-slot}%} to {-hp}
		set {-td-espd::%p%::%{-slot}%} to {-spd}
		set {-td-erew::%p%::%{-slot}%} to {-rew}
		set {-td-eslow::%p%::%{-slot}%} to 0
		set image of screen element "e%{-slot}%" for p to {-tex}
		set position of screen element "e%{-slot}%" for p to {-sx} {-sy}
		show screen element "e%{-slot}%" to p
		stop

	# Спавн Босса (слот 10)
	if {-td-spawn-boss::%p%} > 0 and {-td-ea::%p%::10} is 0:
		set {-bk} to {-td-spawn-boss::%p%}
		set {-td-spawn-boss::%p%} to 0
		set {-bhp} to round((480 + {-bk} * {-bk} * 380) * {-dmul})
		set {-bspd} to 2.25 + {-bk} * 0.08
		set {-brew} to 90 + {-bk} * 75
		set {-btex} to "td:textures/gui/mob_boss.png"
		if {-w} is {-td-maxw::%p%}:
			set {-btex} to "td:textures/gui/mob_megaboss.png"
			set {-bhp} to round({-bhp} * 1.35)
		set {-td-ea::%p%::10} to 1
		set {-td-eboss::%p%::10} to 1
		set {-td-ex::%p%::10} to {-sx}
		set {-td-ey::%p%::10} to {-sy}
		set {-td-eseg::%p%::10} to 2
		set {-td-ehp::%p%::10} to {-bhp}
		set {-td-espd::%p%::10} to {-bspd}
		set {-td-erew::%p%::10} to {-brew}
		set {-td-eslow::%p%::10} to 0
		set image of screen element "e10" for p to {-btex}
		set position of screen element "e10" for p to {-sx} {-sy}
		show screen element "e10" to p
		set max of screen bar "boss_bar" for p to {-bhp}
		set value of screen bar "boss_bar" for p to {-bhp}
		show screen element "boss_bar" to p

# Универсальное движение по точкам маршрута любой карты
function TdMoveEnemy(p: player, i: number):
	set {-spd} to {-td-espd::%p%::%i%}
	if {-td-eslow::%p%::%i%} > 0:
		remove 1 from {-td-eslow::%p%::%i%}
		set {-spd} to {-spd} * 0.55
	set {-seg} to {-td-eseg::%p%::%i%}
	set {-x} to {-td-ex::%p%::%i%}
	set {-y} to {-td-ey::%p%::%i%}
	set {-tx} to {-td-wpx::%p%::%{-seg}%}
	set {-ty} to {-td-wpy::%p%::%{-seg}%}
	set {-dx} to {-tx} - {-x}
	set {-dy} to {-ty} - {-y}
	set {-dist} to sqrt({-dx} * {-dx} + {-dy} * {-dy})

	if {-dist} <= {-spd}:
		set {-x} to {-tx}
		set {-y} to {-ty}
		add 1 to {-seg}
		if {-seg} > {-td-wpcnt::%p%}:
			# Враг прорвался в замок!
			set {-td-ea::%p%::%i%} to 0
			hide screen element "e%i%" from p
			set {-dmg} to 1
			if {-td-eboss::%p%::%i%} is 1:
				set {-dmg} to 5
			remove {-dmg} from {-td-hp::%p%}
			play sound entity.iron_golem.damage to p with volume 0.7 pitch 0.8
			shake camera of p with intensity 1.6 for 8 ticks
			fade screen of p to #FF1100 in 2 stay 2 out 8 ticks
			TdUpdateHud(p)
			if {-td-hp::%p%} <= 0:
				TdLose(p)
			stop
	else:
		set {-x} to {-x} + ({-dx} / {-dist}) * {-spd}
		set {-y} to {-y} + ({-dy} / {-dist}) * {-spd}

	set {-td-ex::%p%::%i%} to {-x}
	set {-td-ey::%p%::%i%} to {-y}
	set {-td-eseg::%p%::%i%} to {-seg}
	set position of screen element "e%i%" for p to {-x} {-y}

# ==========================================================
#  БАШНИ И ЛЕТАЮЩИЕ ПРОЖЕКТАЙЛЫ
# ==========================================================

function TdTowerTick(p: player, tid: number):
	if {-td-tcd::%p%::%tid%} > 0:
		remove 1 from {-td-tcd::%p%::%tid%}
		stop

	set {-type} to {-td-tt::%p%::%tid%}
	set {-lvl} to {-td-tl::%p%::%tid%}
	set {-tx} to {-td-tx::%p%::%tid%}
	set {-ty} to {-td-ty::%p%::%tid%}

	set {-range} to 88
	set {-dmg} to 14
	set {-cd} to 4
	set {-pspd} to 26
	set {-ptex} to "td:textures/gui/proj_arrow.png"
	if {-type} is 2:
		set {-range} to 82
		set {-dmg} to 34
		set {-cd} to 11
		set {-pspd} to 18
		set {-ptex} to "td:textures/gui/proj_cannon.png"
	else if {-type} is 3:
		set {-range} to 86
		set {-dmg} to 10
		set {-cd} to 7
		set {-pspd} to 22
		set {-ptex} to "td:textures/gui/proj_frost.png"
	else if {-type} is 4:
		set {-range} to 118
		set {-dmg} to 30
		set {-cd} to 6
		set {-pspd} to 20
		set {-ptex} to "td:textures/gui/proj_mage.png"
	else if {-type} is 5:
		set {-range} to 96
		set {-dmg} to 18
		set {-cd} to 7
		set {-pspd} to 28
		set {-ptex} to "td:textures/gui/proj_tesla.png"
	else if {-type} is 6:
		set {-range} to 190
		set {-dmg} to 52
		set {-cd} to 13
		set {-pspd} to 34
		set {-ptex} to "td:textures/gui/proj_sniper.png"

	set {-range} to {-range} + ({-lvl} - 1) * 9
	set {-dmg} to round({-dmg} * (1 + ({-lvl} - 1) * 0.55))

	# Выбираем самого продвинутого врага в радиусе
	set {-best} to 0
	set {-best-prog} to -1
	loop 10 times:
		set {-ei} to %loop-number%
		if {-td-ea::%p%::%{-ei}%} is 1:
			set {-dx} to {-td-ex::%p%::%{-ei}%} - {-tx}
			set {-dy} to {-td-ey::%p%::%{-ei}%} - {-ty}
			if sqrt({-dx} * {-dx} + {-dy} * {-dy}) <= {-range}:
				set {-prog} to {-td-eseg::%p%::%{-ei}%} * 1000
				if {-prog} > {-best-prog}:
					set {-best-prog} to {-prog}
					set {-best} to {-ei}

	if {-best} is 0:
		stop

	set {-td-tcd::%p%::%tid%} to {-cd}
	TdSpawnProjectile(p, {-tx}, {-ty}, {-best}, {-type}, {-dmg}, {-pspd}, {-ptex})

function TdSpawnProjectile(p: player, sx: number, sy: number, tgt: number, ptype: number, dmg: number, spd: number, tex: text):
	# Ищем свободный слот прожектайла (1..6); если все заняты — мгновенное попадание
	set {-ps} to 0
	loop 6 times:
		set {-k} to %loop-number%
		if {-td-pra::%p%::%{-k}%} is 0 and {-ps} is 0:
			set {-ps} to {-k}
	if {-ps} is 0:
		TdImpact(p, tgt, ptype, dmg, {-td-ex::%p%::%tgt%}, {-td-ey::%p%::%tgt%})
		stop
	set {-td-pra::%p%::%{-ps}%} to 1
	set {-td-prx::%p%::%{-ps}%} to sx
	set {-td-pry::%p%::%{-ps}%} to sy
	set {-td-prtgt::%p%::%{-ps}%} to tgt
	set {-td-prtype::%p%::%{-ps}%} to ptype
	set {-td-prdmg::%p%::%{-ps}%} to dmg
	set {-td-prspd::%p%::%{-ps}%} to spd
	set image of screen element "pr%{-ps}%" for p to tex
	set position of screen element "pr%{-ps}%" for p to sx sy
	show screen element "pr%{-ps}%" to p

function TdMoveProjectiles(p: player):
	loop 6 times:
		set {-k} to %loop-number%
		if {-td-pra::%p%::%{-k}%} is 1:
			set {-tgt} to {-td-prtgt::%p%::%{-k}%}
			if {-td-ea::%p%::%{-tgt}%} is not 1:
				set {-td-pra::%p%::%{-k}%} to 0
				hide screen element "pr%{-k}%" from p
			else:
				set {-px} to {-td-prx::%p%::%{-k}%}
				set {-py} to {-td-pry::%p%::%{-k}%}
				set {-tx} to {-td-ex::%p%::%{-tgt}%}
				set {-ty} to {-td-ey::%p%::%{-tgt}%}
				set {-spd} to {-td-prspd::%p%::%{-k}%}
				set {-dx} to {-tx} - {-px}
				set {-dy} to {-ty} - {-py}
				set {-dist} to sqrt({-dx} * {-dx} + {-dy} * {-dy})
				if {-dist} <= {-spd}:
					set {-td-pra::%p%::%{-k}%} to 0
					hide screen element "pr%{-k}%" from p
					TdImpact(p, {-tgt}, {-td-prtype::%p%::%{-k}%}, {-td-prdmg::%p%::%{-k}%}, {-tx}, {-ty})
				else:
					set {-nx} to {-px} + ({-dx} / {-dist}) * {-spd}
					set {-ny} to {-py} + ({-dy} / {-dist}) * {-spd}
					set {-td-prx::%p%::%{-k}%} to {-nx}
					set {-td-pry::%p%::%{-k}%} to {-ny}
					set position of screen element "pr%{-k}%" for p to {-nx} {-ny}

function TdClearProjectiles(p: player):
	loop 6 times:
		set {-k} to %loop-number%
		set {-td-pra::%p%::%{-k}%} to 0
		hide screen element "pr%{-k}%" from p

function TdImpact(p: player, tgt: number, ptype: number, dmg: number, hx: number, hy: number):
	if ptype is 2:
		# Пушка: сплеш-взрыв
		set position of screen element "fx" for p to hx hy
		show screen element "fx" to p
		set {-td-fx-cd::%p%} to 2
		play sound entity.generic.explode to p with volume 0.16 pitch 1.65
		loop 10 times:
			set {-si} to %loop-number%
			if {-td-ea::%p%::%{-si}%} is 1:
				set {-sdx} to {-td-ex::%p%::%{-si}%} - hx
				set {-sdy} to {-td-ey::%p%::%{-si}%} - hy
				if sqrt({-sdx} * {-sdx} + {-sdy} * {-sdy}) <= 44:
					TdDamageEnemy(p, {-si}, dmg, 0)
	else if ptype is 3:
		# Лёд: замедление 45%
		play sound block.glass.break to p with volume 0.18 pitch 1.75
		TdDamageEnemy(p, tgt, dmg, 18)
	else if ptype is 5:
		# Тесла: цепная молния до 3 врагов!
		play sound entity.lightning_bolt.impact to p with volume 0.14 pitch 1.9
		TdDamageEnemy(p, tgt, dmg, 0)
		set {-hits} to 1
		loop 10 times:
			set {-si} to %loop-number%
			if {-si} is not tgt and {-hits} < 3 and {-td-ea::%p%::%{-si}%} is 1:
				set {-sdx} to {-td-ex::%p%::%{-si}%} - hx
				set {-sdy} to {-td-ey::%p%::%{-si}%} - hy
				if sqrt({-sdx} * {-sdx} + {-sdy} * {-sdy}) <= 68:
					TdDamageEnemy(p, {-si}, round(dmg * 0.75), 0)
					add 1 to {-hits}
	else if ptype is 6:
		# Снайпер: шанс крита 25% (x2 урон + вспышка)
		set {-fdmg} to dmg
		chance of 25%:
			set {-fdmg} to dmg * 2
			set position of screen element "fx" for p to hx hy
			show screen element "fx" to p
			set {-td-fx-cd::%p%} to 2
		play sound entity.arrow.hit_player to p with volume 0.25 pitch 1.6
		TdDamageEnemy(p, tgt, {-fdmg}, 0)
	else:
		play sound entity.arrow.shoot to p with volume 0.18 pitch 1.45
		TdDamageEnemy(p, tgt, dmg, 0)

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

# ==========================================================
#  ПОБЕДА, ПОРАЖЕНИЕ И РЕКОРДЫ
# ==========================================================

function TdLose(p: player):
	set {-td-over::%p%} to 1
	set {-td-in-wave::%p%} to 0
	TdClearProjectiles(p)
	set text of screen element "end_title" for p to "&c&lБАЗА ПАЛА! (Волна %{-td-wave::%p%}%/%{-td-maxw::%p%}%)"
	set text of screen element "end_sub" for p to "&fСчёт: &e%{-td-score::%p%}% &7— нажми &eR &7(рестарт) или &eM &7(меню)"
	show screen element "end_title" to p
	show screen element "end_sub" to p
	TdSaveScore(p)

function TdWin(p: player):
	set {-td-over::%p%} to 1
	add {-td-hp::%p%} * 100 * {-td-diff::%p%} to {-td-score::%p%}
	TdUpdateHud(p)
	set text of screen element "end_title" for p to "&6&lПОБЕДА! ВСЕ %{-td-maxw::%p%}% ВОЛН ПРОЙДЕНЫ!"
	set text of screen element "end_sub" for p to "&aСчёт: &e%{-td-score::%p%}% &7— нажми &eR &7(ещё раз) или &eM &7(меню)"
	show screen element "end_title" to p
	show screen element "end_sub" to p
	fade screen of p to #FFD700 in 3 stay 4 out 14 ticks
	play sound ui.toast.challenge_complete to p with volume 0.9 pitch 1.0
	TdSaveScore(p)

function TdSaveScore(p: player):
	set {-sc} to {-td-score::%p%}
	if {td::lb::%p%} is not set:
		set {td::lb::%p%} to {-sc}
	else if {-sc} > {td::lb::%p%}:
		set {td::lb::%p%} to {-sc}
