# ==========================================================
#  td.sc — Tower Defense Pro CO-OP
#          3 карты | 3 сложности (10/20/30 волн) | Кооп на 2 игроков!
#          6 башен + Эволюция ✦5★ с уникальной пассивкой каждой башни!
#          Свободная постройка + летающие прожектайлы!
#  Команды:
#    /td               — выбор карты и сложности (Соло)
#    /td coop <игрок>  — пригласить друга в кооп на 2 игроков
#    /td join          — принять приглашение и войти в игру с другом
#  Управление:
#    1..6  — выбрать башню | ЛКМ по траве — построить
#    ЛКМ по башне — улучшить (1★→2★→3★→4★ → ✦5★ Эволюция!)
#    X     — продажа (70%) | Space — волна / скорость x1 <-> x2
#    R     — рестарт | M — меню выбора карты
#  Текстуры: распакуйте td-resources.zip в scriptcraft/resources/
# ==========================================================

on load:
	clear {-td-active::*}
	clear {-td-host::*}
	clear {-td-coop::*}
	clear {-td-inv::*}

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
			send "&eИспользование: &f/td coop <ник_игрока>" to player
			stop
		if arg-2 is player:
			send "&cНельзя пригласить самого себя!" to player
			stop
		set {-td-inv::%arg-2%} to %player%
		set {-td-partner-pend::%player%} to %arg-2%
		send "&aПриглашение в кооп отправлено игроку &f%arg-2%&a! Сначала выберите карту в &e/td&a." to player
		send "&6[Tower Defense] &e%player% &fприглашает вас в кооп! Напишите &a/td join &fчтобы принять." to arg-2
		play sound entity.experience_orb.pickup to arg-2 with volume 0.7 pitch 1.3
		TdOpenMenu(player)
		stop
	if arg-1 is "join" or "accept":
		if {-td-inv::%player%} is not set:
			send "&cУ вас нет активных приглашений в кооп." to player
			stop
		set {-host} to {-td-inv::%player%}
		delete {-td-inv::%player%}
		if {-host} is not online:
			send "&cИгрок %{-host}% не в сети!" to player
			stop
		set {-td-coop-ready::%{-host}%} to %player%
		send "&aВы приняли приглашение от &e%{-host}%&a! Запускаем кооп..." to player
		send "&aИгрок &e%player% &aприсоединился к коопу! Запускаем игру..." to {-host}
		TdStartCoop({-host}, player)
		stop
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
	TdRestartCurrent(player)

on key m in screen "Tower Defense":
	TdOpenMenu(player)

on toggle "autowave":
	set {-g} to {-td-host::%player%}
	if {-g} is not set:
		stop
	if %value% is "true":
		set {-td-auto::%{-g}%} to 1
		TdSyncStatus({-g}, "&aАвто-волна включена (%player%)!")
	else:
		set {-td-auto::%{-g}%} to 0
		TdSyncStatus({-g}, "&7Авто-волна выключена")

# --- Игровой цикл (тикает только хост комнаты -> синхронизируется обоим игрокам) ---

every 2 ticks:
	loop all players:
		if {-td-active::%loop-player%} is 1:
			if loop-player has screen "Tower Defense" open:
				TdTick(loop-player)
				if {-td-speed::%loop-player%} is 2 and {-td-in-wave::%loop-player%} is 1 and {-td-over::%loop-player%} is 0:
					TdTick(loop-player)
			else:
				TdOnLeave(loop-player)

function TdOnLeave(p: player):
	set {-g} to {-td-host::%p%}
	if {-g} is not set:
		stop
	set {-p2} to {-td-coop::%{-g}%}
	delete {-td-active::%{-g}%}
	delete {-td-over::%{-g}%}
	delete {-td-host::%{-g}%}
	if {-p2} is set and {-p2} is not "":
		delete {-td-host::%{-p2}%}
		if %p% is not {-p2}:
			send "&e[TD Кооп] &7%p% вышел из игры." to {-p2}
		else:
			send "&e[TD Кооп] &7%p% вышел из игры." to {-g}
	delete {-td-coop::%{-g}%}

# ==========================================================
#  ХЕЛПЕРЫ СИНХРОНИЗАЦИИ ЭКРАНА ДЛЯ 1 ИЛИ 2 ИГРОКОВ
# ==========================================================

function TdSyncPos(g: player, id: text, x: number, y: number):
	set position of screen element id for g to x y
	set {-p2} to {-td-coop::%g%}
	if {-p2} is set and {-p2} is not "":
		set position of screen element id for {-p2} to x y

function TdSyncImg(g: player, id: text, tex: text):
	set image of screen element id for g to tex
	set {-p2} to {-td-coop::%g%}
	if {-p2} is set and {-p2} is not "":
		set image of screen element id for {-p2} to tex

function TdSyncTxt(g: player, id: text, txt: text):
	set text of screen element id for g to txt
	set {-p2} to {-td-coop::%g%}
	if {-p2} is set and {-p2} is not "":
		set text of screen element id for {-p2} to txt

function TdSyncShow(g: player, id: text):
	show screen element id to g
	set {-p2} to {-td-coop::%g%}
	if {-p2} is set and {-p2} is not "":
		show screen element id to {-p2}

function TdSyncHide(g: player, id: text):
	hide screen element id from g
	set {-p2} to {-td-coop::%g%}
	if {-p2} is set and {-p2} is not "":
		hide screen element id from {-p2}

function TdSyncStatus(g: player, txt: text):
	TdSyncTxt(g, "status_txt", txt)

function TdSyncSound(g: player, snd: text, vol: number, pit: number):
	play sound %snd% to g with volume vol pitch pit
	set {-p2} to {-td-coop::%g%}
	if {-p2} is set and {-p2} is not "":
		play sound %snd% to {-p2} with volume vol pitch pit

function TdSyncBossBar(g: player, val: number, mx: number):
	if mx > 0:
		set max of screen bar "boss_bar" for g to mx
	set value of screen bar "boss_bar" for g to val
	set {-p2} to {-td-coop::%g%}
	if {-p2} is set and {-p2} is not "":
		if mx > 0:
			set max of screen bar "boss_bar" for {-p2} to mx
		set value of screen bar "boss_bar" for {-p2} to val

function TdSyncHud(g: player):
	set {-sp} to "x1"
	if {-td-speed::%g%} is 2:
		set {-sp} to "&b&lx2"
	set {-p2} to {-td-coop::%g%}
	if {-p2} is set and {-p2} is not "":
		set text of screen element "hud_top" for g to "&d[КООП] &eВ:%{-td-wave::%g%}%/%{-td-maxw::%g%}%  &6Зол:%{-td-gold::%g%}%  &cHP:%{-td-hp::%g%}%  &aСч:%{-td-score::%g%}%  &7[%{-sp}%&7]"
		set text of screen element "hud_top" for {-p2} to "&d[КООП] &eВ:%{-td-wave::%g%}%/%{-td-maxw::%g%}%  &6Зол:%{-td-gold::%{-p2}%}%  &cHP:%{-td-hp::%g%}%  &aСч:%{-td-score::%g%}%  &7[%{-sp}%&7]"
	else:
		set text of screen element "hud_top" for g to "&eВолна: &f%{-td-wave::%g%}%/%{-td-maxw::%g%}%   &6Золото: &f%{-td-gold::%g%}%   &cБаза: &f%{-td-hp::%g%}% HP   &aСчёт: &f%{-td-score::%g%}%   &7[%{-sp}%&7]"

# ==========================================================
#  МЕНЮ ВЫБОРА КАРТЫ И СЛОЖНОСТИ
# ==========================================================

function TdOpenMenu(p: player):
	TdOnLeave(p)
	if {-td-map::%p%} is not set:
		set {-td-map::%p%} to 1
	if {-td-diff::%p%} is not set:
		set {-td-diff::%p%} to 2
	open screen "Tower Defense" to p:
		text "&6&lTOWER DEFENSE PRO" at 0 -108 anchor center shadow
		text "&7Свободная постройка + Эволюция ✦5★ | Кооп на двоих: &e/td coop <ник>" at 0 -94 anchor center shadow

		# 3 превью карт
		image "td:textures/gui/map_valley.png" at -122 -38 size 112x56 texture 384x192 anchor center run TdPickMap(player, 1) keep open tooltip "&aИзумрудная долина\n&7Классический S-маршрут"
		text "&a1. Долина" at -122 -2 anchor center shadow id m_lbl1

		image "td:textures/gui/map_volcano.png" at 0 -38 size 112x56 texture 384x192 anchor center run TdPickMap(player, 2) keep open tooltip "&6Лавовый каньон\n&7Двойной зигзаг — много поворотов!"
		text "&72. Каньон" at 0 -2 anchor center shadow id m_lbl2

		image "td:textures/gui/map_ice.png" at 122 -38 size 112x56 texture 384x192 anchor center run TdPickMap(player, 3) keep open tooltip "&bЛедяная цитадель\n&7Короткий и быстрый серпантин!"
		text "&73. Цитадель" at 122 -2 anchor center shadow id m_lbl3

		# 3 уровня сложности
		text "&eСложность:" at 0 22 anchor center shadow
		button "&aЛёгкий (10 волн)" at -110 42 size 100x20 anchor center run TdPickDiff(player, 1) keep open tooltip "&aЛёгкий уровень\n&710 волн | 2 босса | 15 HP | 140з"
		button "&eСредний (20 волн)" at 0 42 size 100x20 anchor center run TdPickDiff(player, 2) keep open tooltip "&eСредний уровень\n&720 волн | 4 босса | 10 HP | 120з"
		button "&cТяжёлый (30 волн)" at 110 42 size 100x20 anchor center run TdPickDiff(player, 3) keep open tooltip "&cТяжёлый уровень\n&730 волн | 6 боссов | 8 HP | 110з"

		text "&7Выбрано: &aДолина &7| &eСредний (20 волн)" at 0 70 anchor center shadow id menu_info
		button "&a&l▶ ИГРАТЬ (СОЛО)" at -54 96 size 118x22 anchor center run TdStartSolo(player) keep open
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
#  ЗАПУСК ИГРЫ (СОЛО И КООП НА 2 ИГРОКОВ)
# ==========================================================

function TdStartSolo(p: player):
	TdStartSession(p, "")

function TdStartCoop(host: player, partner: player):
	TdStartSession(host, %partner%)

function TdRestartCurrent(p: player):
	set {-g} to {-td-host::%p%}
	if {-g} is not set:
		TdStartSolo(p)
		stop
	set {-p2} to {-td-coop::%{-g}%}
	if {-p2} is set and {-p2} is not "" and {-p2} is online:
		TdStartSession({-g}, {-p2})
	else:
		TdStartSession(p, "")

function TdStartSession(host: player, partnerName: text):
	set {-g} to %host%
	if {-td-map::%{-g}%} is not set:
		set {-td-map::%{-g}%} to 1
	if {-td-diff::%{-g}%} is not set:
		set {-td-diff::%{-g}%} to 2
	set {-m} to {-td-map::%{-g}%}
	set {-d} to {-td-diff::%{-g}%}

	set {-td-active::%{-g}%} to 1
	set {-td-over::%{-g}%} to 0
	set {-td-host::%{-g}%} to {-g}
	set {-td-coop::%{-g}%} to partnerName
	set {-td-wave::%{-g}%} to 0
	set {-td-score::%{-g}%} to 0
	set {-td-speed::%{-g}%} to 1
	set {-td-auto::%{-g}%} to 0
	set {-td-auto-cd::%{-g}%} to 0
	set {-td-in-wave::%{-g}%} to 0
	set {-td-to-spawn::%{-g}%} to 0
	set {-td-spawn-boss::%{-g}%} to 0
	set {-td-spawn-cd::%{-g}%} to 0
	set {-td-fx-cd::%{-g}%} to 0
	set {-td-tool::%{-g}%} to 1
	set {-td-sell::%{-g}%} to 0

	if {-d} is 1:
		set {-td-maxw::%{-g}%} to 10
		set {-sgold} to 140
		set {-td-hp::%{-g}%} to 15
	else if {-d} is 2:
		set {-td-maxw::%{-g}%} to 20
		set {-sgold} to 120
		set {-td-hp::%{-g}%} to 10
	else:
		set {-td-maxw::%{-g}%} to 30
		set {-sgold} to 110
		set {-td-hp::%{-g}%} to 8

	set {-td-gold::%{-g}%} to {-sgold}
	if partnerName is not "":
		set {-td-host::%partnerName%} to {-g}
		set {-td-gold::%partnerName%} to {-sgold}
		set {-td-tool::%partnerName%} to 1
		set {-td-sell::%partnerName%} to 0

	clear {-td-grid::%{-g}%::*}
	loop 24 times:
		set {-i} to %loop-number%
		set {-td-ta::%{-g}%::%{-i}%} to 0
		set {-td-tt::%{-g}%::%{-i}%} to 0
		set {-td-tl::%{-g}%::%{-i}%} to 0
		set {-td-tcd::%{-g}%::%{-i}%} to 0
		set {-td-tcost::%{-g}%::%{-i}%} to 0

	loop 10 times:
		set {-i} to %loop-number%
		set {-td-ea::%{-g}%::%{-i}%} to 0
		set {-td-eboss::%{-g}%::%{-i}%} to 0
		set {-td-eslow::%{-g}%::%{-i}%} to 0
		set {-td-efreeze::%{-g}%::%{-i}%} to 0
		set {-td-eburn::%{-g}%::%{-i}%} to 0
		set {-td-eburn-d::%{-g}%::%{-i}%} to 0

	loop 6 times:
		set {-i} to %loop-number%
		set {-td-pra::%{-g}%::%{-i}%} to 0

	TdLoadWaypoints({-g}, {-m})
	if {-m} is 1:
		TdOpenMap1(host)
		if partnerName is not "":
			TdOpenMap1(partnerName)
	else if {-m} is 2:
		TdOpenMap2(host)
		if partnerName is not "":
			TdOpenMap2(partnerName)
	else:
		TdOpenMap3(host)
		if partnerName is not "":
			TdOpenMap3(partnerName)
	TdSyncHud({-g})
	if partnerName is not "":
		TdSyncStatus({-g}, "&d&lКООП НА ДВОИХ! &f%{-g}% &7+ &f%partnerName% &7— стройте башни вместе!")

function TdLoadWaypoints(g: player, mapId: number):
	if mapId is 1:
		set {-td-wpcnt::%g%} to 6
		set {-td-wpx::%g%::1} to -180
		set {-td-wpy::%g%::1} to -70
		set {-td-wpx::%g%::2} to 132
		set {-td-wpy::%g%::2} to -70
		set {-td-wpx::%g%::3} to 132
		set {-td-wpy::%g%::3} to -22
		set {-td-wpx::%g%::4} to -132
		set {-td-wpy::%g%::4} to -22
		set {-td-wpx::%g%::5} to -132
		set {-td-wpy::%g%::5} to 50
		set {-td-wpx::%g%::6} to 180
		set {-td-wpy::%g%::6} to 50
	else if mapId is 2:
		set {-td-wpcnt::%g%} to 8
		set {-td-wpx::%g%::1} to -180
		set {-td-wpy::%g%::1} to -94
		set {-td-wpx::%g%::2} to -84
		set {-td-wpy::%g%::2} to -94
		set {-td-wpx::%g%::3} to -84
		set {-td-wpy::%g%::3} to 50
		set {-td-wpx::%g%::4} to 36
		set {-td-wpy::%g%::4} to 50
		set {-td-wpx::%g%::5} to 36
		set {-td-wpy::%g%::5} to -70
		set {-td-wpx::%g%::6} to 132
		set {-td-wpy::%g%::6} to -70
		set {-td-wpx::%g%::7} to 132
		set {-td-wpy::%g%::7} to 26
		set {-td-wpx::%g%::8} to 180
		set {-td-wpy::%g%::8} to 26
	else if mapId is 3:
		set {-td-wpcnt::%g%} to 8
		set {-td-wpx::%g%::1} to -180
		set {-td-wpy::%g%::1} to 2
		set {-td-wpx::%g%::2} to -108
		set {-td-wpy::%g%::2} to 2
		set {-td-wpx::%g%::3} to -108
		set {-td-wpy::%g%::3} to -70
		set {-td-wpx::%g%::4} to -12
		set {-td-wpy::%g%::4} to -70
		set {-td-wpx::%g%::5} to -12
		set {-td-wpy::%g%::5} to 50
		set {-td-wpx::%g%::6} to 84
		set {-td-wpy::%g%::6} to 50
		set {-td-wpx::%g%::7} to 84
		set {-td-wpy::%g%::7} to -46
		set {-td-wpx::%g%::8} to 180
		set {-td-wpy::%g%::8} to -46

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

		# Пул звёзд уровней и эволюций (до 24 башен)
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
		text "&eВыбран: &fЛучник (50з) &7— клик по траве строит, по башне улучшает/эволюционирует (✦5★)!" at 0 92 anchor center shadow layer 12 id status_txt
		text "&c&lБАЗА ПАЛА!" at 0 -20 anchor center shadow layer 20 id end_title hidden
		text "&fНажми &eR &fили кнопку Меню" at 0 -4 anchor center shadow layer 20 id end_sub hidden

		# Компактная панель: 6 иконок башен (с описанием ✦5★ эволюции) + 3 кнопки + Авто-волна
		image "td:textures/gui/tower_archer.png" at -176 109 size 22x22 texture 24x24 anchor center layer 12 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e[1] Лучник &7(50з)\n&7Урон: 14 | Быстрый выстрел\n&6✦5★ Эволюция: &fШквал (бьёт по 2 целям сразу!)"
		text "&e[1] 50" at -176 124 anchor center shadow layer 12 id lbl1
		image "td:textures/gui/tower_cannon.png" at -144 109 size 22x22 texture 24x24 anchor center layer 12 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6[2] Пушка &7(95з)\n&7Урон: 34 + &cСплеш\n&6✦5★ Эволюция: &fНапалм (мега-сплеш + поджог врагов!)"
		text "&7[2] 95" at -144 124 anchor center shadow layer 12 id lbl2
		image "td:textures/gui/tower_frost.png" at -112 109 size 22x22 texture 24x24 anchor center layer 12 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b[3] Лёд &7(85з)\n&7Урон: 10 + &bЗамедление 45%\n&6✦5★ Эволюция: &fМерзлота (65% замедл. + 25% заморозка!)"
		text "&7[3] 85" at -112 124 anchor center shadow layer 12 id lbl3
		image "td:textures/gui/tower_mage.png" at -80 109 size 22x22 texture 24x24 anchor center layer 12 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d[4] Маг &7(130з)\n&7Урон: 30 | &dДальность 118\n&6✦5★ Эволюция: &fУбийца Боссов (x2 урон Боссам + 4% макс.HP!)"
		text "&7[4] 130" at -80 124 anchor center shadow layer 12 id lbl4
		image "td:textures/gui/tower_tesla.png" at -48 109 size 22x22 texture 24x24 anchor center layer 12 id ico5 run TdSelectTool(player, 5) keep open tooltip "&b[5] Тесла &7(115з)\n&7Урон: 18 | &bЦепная молния (3 цели)\n&6✦5★ Эволюция: &fШторм (5 целей + 90% урон + оглушение!)"
		text "&7[5] 115" at -48 124 anchor center shadow layer 12 id lbl5
		image "td:textures/gui/tower_sniper.png" at -16 109 size 22x22 texture 24x24 anchor center layer 12 id ico6 run TdSelectTool(player, 6) keep open tooltip "&c[6] Снайпер &7(150з)\n&7Урон: 52 | &cКрит 25% | Дальн. 190\n&6✦5★ Эволюция: &fКазнь (крит 45% x2.5 + казнь <20% HP!)"
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

		# Пул звёзд уровней и эволюций (до 24 башен)
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
		text "&eВыбран: &fЛучник (50з) &7— клик по траве строит, по башне улучшает/эволюционирует (✦5★)!" at 0 92 anchor center shadow layer 12 id status_txt
		text "&c&lБАЗА ПАЛА!" at 0 -20 anchor center shadow layer 20 id end_title hidden
		text "&fНажми &eR &fили кнопку Меню" at 0 -4 anchor center shadow layer 20 id end_sub hidden

		# Компактная панель: 6 иконок башен (с описанием ✦5★ эволюции) + 3 кнопки + Авто-волна
		image "td:textures/gui/tower_archer.png" at -176 109 size 22x22 texture 24x24 anchor center layer 12 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e[1] Лучник &7(50з)\n&7Урон: 14 | Быстрый выстрел\n&6✦5★ Эволюция: &fШквал (бьёт по 2 целям сразу!)"
		text "&e[1] 50" at -176 124 anchor center shadow layer 12 id lbl1
		image "td:textures/gui/tower_cannon.png" at -144 109 size 22x22 texture 24x24 anchor center layer 12 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6[2] Пушка &7(95з)\n&7Урон: 34 + &cСплеш\n&6✦5★ Эволюция: &fНапалм (мега-сплеш + поджог врагов!)"
		text "&7[2] 95" at -144 124 anchor center shadow layer 12 id lbl2
		image "td:textures/gui/tower_frost.png" at -112 109 size 22x22 texture 24x24 anchor center layer 12 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b[3] Лёд &7(85з)\n&7Урон: 10 + &bЗамедление 45%\n&6✦5★ Эволюция: &fМерзлота (65% замедл. + 25% заморозка!)"
		text "&7[3] 85" at -112 124 anchor center shadow layer 12 id lbl3
		image "td:textures/gui/tower_mage.png" at -80 109 size 22x22 texture 24x24 anchor center layer 12 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d[4] Маг &7(130з)\n&7Урон: 30 | &dДальность 118\n&6✦5★ Эволюция: &fУбийца Боссов (x2 урон Боссам + 4% макс.HP!)"
		text "&7[4] 130" at -80 124 anchor center shadow layer 12 id lbl4
		image "td:textures/gui/tower_tesla.png" at -48 109 size 22x22 texture 24x24 anchor center layer 12 id ico5 run TdSelectTool(player, 5) keep open tooltip "&b[5] Тесла &7(115з)\n&7Урон: 18 | &bЦепная молния (3 цели)\n&6✦5★ Эволюция: &fШторм (5 целей + 90% урон + оглушение!)"
		text "&7[5] 115" at -48 124 anchor center shadow layer 12 id lbl5
		image "td:textures/gui/tower_sniper.png" at -16 109 size 22x22 texture 24x24 anchor center layer 12 id ico6 run TdSelectTool(player, 6) keep open tooltip "&c[6] Снайпер &7(150з)\n&7Урон: 52 | &cКрит 25% | Дальн. 190\n&6✦5★ Эволюция: &fКазнь (крит 45% x2.5 + казнь <20% HP!)"
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

		# Пул звёзд уровней и эволюций (до 24 башен)
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
		text "&eВыбран: &fЛучник (50з) &7— клик по траве строит, по башне улучшает/эволюционирует (✦5★)!" at 0 92 anchor center shadow layer 12 id status_txt
		text "&c&lБАЗА ПАЛА!" at 0 -20 anchor center shadow layer 20 id end_title hidden
		text "&fНажми &eR &fили кнопку Меню" at 0 -4 anchor center shadow layer 20 id end_sub hidden

		# Компактная панель: 6 иконок башен (с описанием ✦5★ эволюции) + 3 кнопки + Авто-волна
		image "td:textures/gui/tower_archer.png" at -176 109 size 22x22 texture 24x24 anchor center layer 12 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e[1] Лучник &7(50з)\n&7Урон: 14 | Быстрый выстрел\n&6✦5★ Эволюция: &fШквал (бьёт по 2 целям сразу!)"
		text "&e[1] 50" at -176 124 anchor center shadow layer 12 id lbl1
		image "td:textures/gui/tower_cannon.png" at -144 109 size 22x22 texture 24x24 anchor center layer 12 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6[2] Пушка &7(95з)\n&7Урон: 34 + &cСплеш\n&6✦5★ Эволюция: &fНапалм (мега-сплеш + поджог врагов!)"
		text "&7[2] 95" at -144 124 anchor center shadow layer 12 id lbl2
		image "td:textures/gui/tower_frost.png" at -112 109 size 22x22 texture 24x24 anchor center layer 12 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b[3] Лёд &7(85з)\n&7Урон: 10 + &bЗамедление 45%\n&6✦5★ Эволюция: &fМерзлота (65% замедл. + 25% заморозка!)"
		text "&7[3] 85" at -112 124 anchor center shadow layer 12 id lbl3
		image "td:textures/gui/tower_mage.png" at -80 109 size 22x22 texture 24x24 anchor center layer 12 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d[4] Маг &7(130з)\n&7Урон: 30 | &dДальность 118\n&6✦5★ Эволюция: &fУбийца Боссов (x2 урон Боссам + 4% макс.HP!)"
		text "&7[4] 130" at -80 124 anchor center shadow layer 12 id lbl4
		image "td:textures/gui/tower_tesla.png" at -48 109 size 22x22 texture 24x24 anchor center layer 12 id ico5 run TdSelectTool(player, 5) keep open tooltip "&b[5] Тесла &7(115з)\n&7Урон: 18 | &bЦепная молния (3 цели)\n&6✦5★ Эволюция: &fШторм (5 целей + 90% урон + оглушение!)"
		text "&7[5] 115" at -48 124 anchor center shadow layer 12 id lbl5
		image "td:textures/gui/tower_sniper.png" at -16 109 size 22x22 texture 24x24 anchor center layer 12 id ico6 run TdSelectTool(player, 6) keep open tooltip "&c[6] Снайпер &7(150з)\n&7Урон: 52 | &cКрит 25% | Дальн. 190\n&6✦5★ Эволюция: &fКазнь (крит 45% x2.5 + казнь <20% HP!)"
		text "&7[6] 150" at -16 124 anchor center shadow layer 12 id lbl6
		button "&aСтарт/x2" at 42 110 size 62x18 anchor center run TdWaveOrSpeed(player) keep open tooltip "&aЗапустить волну &7(Space)\n&bВо время волны: скорость x1 / x2"
		toggle "Авто" id autowave at 102 110 size 50x18 anchor center value false color #22C55E tooltip "&aАвто-волна\n&7Автоматически запускает следующую волну"
		button "&cПродать" at 148 110 size 36x18 anchor center run TdToggleSell(player) keep open tooltip "&cПродать башню &7(X)\n&7Возврат 70% стоимости"
		button "Меню" at 182 110 size 26x18 anchor center run TdOpenMenu(player) keep open tooltip "&eВыбор карты и сложности &7(M)"

# ==========================================================
#  УПРАВЛЕНИЕ, СТРОИТЕЛЬСТВО, АПГРЕЙД И ✦5★ ЭВОЛЮЦИЯ
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
	set {-g} to {-td-host::%p%}
	if {-g} is not set or {-td-active::%{-g}%} is not 1:
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
		set text of screen element "status_txt" for p to "&eЛучник (50з) &7| &6✦5★: &fШквал по 2 целям!"
	else if tool is 2:
		set text of screen element "lbl2" for p to "&6[2] 95"
		set text of screen element "status_txt" for p to "&6Пушка (95з) &7| &6✦5★: &fНапалм (поджог + мега-сплеш)!"
	else if tool is 3:
		set text of screen element "lbl3" for p to "&b[3] 85"
		set text of screen element "status_txt" for p to "&bЛёд (85з) &7| &6✦5★: &fМерзлота (заморозка на месте)!"
	else if tool is 4:
		set text of screen element "lbl4" for p to "&d[4] 130"
		set text of screen element "status_txt" for p to "&dМаг (130з) &7| &6✦5★: &fУбийца Боссов (x2 + 4%% HP)!"
	else if tool is 5:
		set text of screen element "lbl5" for p to "&b[5] 115"
		set text of screen element "status_txt" for p to "&bТесла (115з) &7| &6✦5★: &fШторм (5 целей + оглушение)!"
	else if tool is 6:
		set text of screen element "lbl6" for p to "&c[6] 150"
		set text of screen element "status_txt" for p to "&cСнайпер (150з) &7| &6✦5★: &fКазнь (<20%% HP + крит 45%%)!"
	play sound ui.button.click to p with volume 0.4 pitch 1.3

function TdToggleSell(p: player):
	set {-g} to {-td-host::%p%}
	if {-g} is not set or {-td-active::%{-g}%} is not 1:
		stop
	if {-td-sell::%p%} is 1:
		set {-td-sell::%p%} to 0
		TdSelectTool(p, {-td-tool::%p%})
	else:
		set {-td-sell::%p%} to 1
		set text of screen element "status_txt" for p to "&c&lПРОДАЖА &7— клик по башне вернёт 70%% (X — отмена)"
		play sound ui.button.click to p with volume 0.4 pitch 0.9

function TdWaveOrSpeed(p: player):
	set {-g} to {-td-host::%p%}
	if {-g} is not set or {-td-active::%{-g}%} is not 1 or {-td-over::%{-g}%} is 1:
		stop
	if {-td-in-wave::%{-g}%} is 1:
		if {-td-speed::%{-g}%} is 1:
			set {-td-speed::%{-g}%} to 2
			TdSyncStatus({-g}, "&bСкорость: &l2x &7(Space — 1x)")
		else:
			set {-td-speed::%{-g}%} to 1
			TdSyncStatus({-g}, "&7Скорость: &f1x &7(Space — 2x)")
		TdSyncSound({-g}, "ui.button.click", 0.4, 1.5)
		TdSyncHud({-g})
		stop
	TdNextWave({-g})

function TdCellClick(p: player, col: integer, row: integer):
	set {-g} to {-td-host::%p%}
	if {-g} is not set or {-td-active::%{-g}%} is not 1 or {-td-over::%{-g}%} is 1:
		stop
	set {-tid} to {-td-grid::%{-g}%::%col%_%row%}
	if {-tid} is not set:
		set {-tid} to 0

	# Продажа
	if {-td-sell::%p%} is 1:
		if {-tid} is 0:
			set text of screen element "status_txt" for p to "&7Здесь нет башни"
			stop
		set {-ref} to round({-td-tcost::%{-g}%::%{-tid}%} * 0.7)
		add {-ref} to {-td-gold::%p%}
		set {-td-ta::%{-g}%::%{-tid}%} to 0
		set {-td-tt::%{-g}%::%{-tid}%} to 0
		set {-td-tl::%{-g}%::%{-tid}%} to 0
		set {-td-tcost::%{-g}%::%{-tid}%} to 0
		set {-td-grid::%{-g}%::%col%_%row%} to 0
		set {-td-sell::%p%} to 0
		TdSyncImg({-g}, "c%col%_%row%", "td:textures/gui/tile_build.png")
		TdSyncHide({-g}, "st%{-tid}%")
		TdSyncStatus({-g}, "&e%p% продал башню (+%{-ref}%з)")
		TdSyncSound({-g}, "entity.experience_orb.pickup", 0.6, 0.9)
		TdSyncHud({-g})
		stop

	# Апгрейд (1→2→3→4) и ЭВОЛЮЦИЯ (4→5★ с уникальной пассивкой!)
	if {-tid} > 0:
		set {-lvl} to {-td-tl::%{-g}%::%{-tid}%}
		if {-lvl} >= 5:
			set text of screen element "status_txt" for p to "&6&lБашня уже эволюционирована (✦5★ MAX)!"
			stop
		set {-up} to 65
		if {-lvl} is 2:
			set {-up} to 140
		else if {-lvl} is 3:
			set {-up} to 240
		else if {-lvl} is 4:
			set {-up} to 380
		if {-td-gold::%p%} < {-up}:
			if {-lvl} is 4:
				set text of screen element "status_txt" for p to "&6Для ✦5★ ЭВОЛЮЦИИ нужно %{-up}%з!"
			else:
				set {-nl} to {-lvl} + 1
				set text of screen element "status_txt" for p to "&cДля ур.%{-nl}% нужно %{-up}%з!"
			play sound entity.villager.no to p with volume 0.5 pitch 1.0
			stop
		remove {-up} from {-td-gold::%p%}
		add {-up} to {-td-tcost::%{-g}%::%{-tid}%}
		add 1 to {-td-tl::%{-g}%::%{-tid}%}
		set {-nl} to {-td-tl::%{-g}%::%{-tid}%}
		if {-nl} is 2:
			TdSyncTxt({-g}, "st%{-tid}%", "&6★★")
			TdSyncStatus({-g}, "&a%p% улучшил башню до ур.2! (-%{-up}%з)")
			TdSyncSound({-g}, "entity.player.levelup", 0.5, 1.35)
		else if {-nl} is 3:
			TdSyncTxt({-g}, "st%{-tid}%", "&d★★★")
			TdSyncStatus({-g}, "&a%p% улучшил башню до ур.3! (-%{-up}%з)")
			TdSyncSound({-g}, "entity.player.levelup", 0.5, 1.45)
		else if {-nl} is 4:
			TdSyncTxt({-g}, "st%{-tid}%", "&b★★★★")
			TdSyncStatus({-g}, "&b%p% поднял ур.4! Следующий клик (380з) → &6&l✦5★ ЭВОЛЮЦИЯ!")
			TdSyncSound({-g}, "entity.player.levelup", 0.55, 1.6)
		else:
			# ЭВОЛЮЦИЯ ✦5★!
			TdSyncTxt({-g}, "st%{-tid}%", "&6&l✦5★")
			set {-tt} to {-td-tt::%{-g}%::%{-tid}%}
			set {-evo} to "Шквал (2 цели!)"
			if {-tt} is 2:
				set {-evo} to "Напалм (поджог + мега-сплеш!)"
			else if {-tt} is 3:
				set {-evo} to "Вечная мерзлота (заморозка!)"
			else if {-tt} is 4:
				set {-evo} to "Убийца Боссов (x2 + 4%% HP!)"
			else if {-tt} is 5:
				set {-evo} to "Грозовой шторм (5 целей + стан!)"
			else if {-tt} is 6:
				set {-evo} to "Казнь (<20%% HP + крит 45%%!)"
			TdSyncStatus({-g}, "&6&l✦ ЭВОЛЮЦИЯ 5★! &f%p% открыл: &e%{-evo}%")
			TdSyncSound({-g}, "ui.toast.challenge_complete", 0.75, 1.2)
		TdSyncHud({-g})
		stop

	# Постройка новой башни (до 24 на карте)
	set {-slot} to 0
	loop 24 times:
		set {-k} to %loop-number%
		if {-td-ta::%{-g}%::%{-k}%} is 0 and {-slot} is 0:
			set {-slot} to {-k}
	if {-slot} is 0:
		set text of screen element "status_txt" for p to "&cМаксимум 24 башни! Улучшайте и эволюционируйте до ✦5★"
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
	set {-td-ta::%{-g}%::%{-slot}%} to 1
	set {-td-tt::%{-g}%::%{-slot}%} to {-tool}
	set {-td-tl::%{-g}%::%{-slot}%} to 1
	set {-td-tcd::%{-g}%::%{-slot}%} to 0
	set {-td-tcost::%{-g}%::%{-slot}%} to {-cost}
	set {-td-tx::%{-g}%::%{-slot}%} to {-cx}
	set {-td-ty::%{-g}%::%{-slot}%} to {-cy}
	set {-td-grid::%{-g}%::%col%_%row%} to {-slot}

	TdSyncImg({-g}, "c%col%_%row%", {-tex})
	set {-sy} to {-cy} + 10
	TdSyncPos({-g}, "st%{-slot}%", {-cx}, {-sy})
	TdSyncTxt({-g}, "st%{-slot}%", "&e★")
	TdSyncShow({-g}, "st%{-slot}%")
	TdSyncStatus({-g}, "&a%p% построил башню! Клик по ней улучшит (до ✦5★ Эволюции)")
	TdSyncSound({-g}, "block.anvil.use", 0.35, 1.5)
	TdSyncHud({-g})

# ==========================================================
#  ВОЛНЫ И СПАВН
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

	set {-cnt} to 5 + round({-w} * 0.65)
	set {-p2} to {-td-coop::%g%}
	if {-p2} is set and {-p2} is not "":
		add 2 to {-cnt}
	set {-td-to-spawn::%g%} to {-cnt}

	set {-rem5} to {-w} % 5
	if {-rem5} is 0:
		set {-bkind} to round({-w} / 5)
		set {-td-spawn-boss::%g%} to {-bkind}
		if {-w} is {-mw}:
			TdSyncStatus(g, "&4&lФИНАЛЬНАЯ ВОЛНА %{-w}%/%{-mw}% — МЕГА-БОСС!")
			TdSyncSound(g, "entity.wither.spawn", 0.55, 0.85)
		else:
			TdSyncStatus(g, "&c&lВОЛНА %{-w}%/%{-mw}% — БОСС ВЫХОДИТ НА ПОЛЕ!")
			TdSyncSound(g, "entity.ender_dragon.growl", 0.5, 1.1)
	else:
		TdSyncStatus(g, "&eВолна %{-w}%/%{-mw}% началась! &7(Space — x1/x2)")
		TdSyncSound(g, "entity.experience_orb.pickup", 0.5, 1.25)
	TdSyncHud(g)

# ==========================================================
#  ОСНОВНОЙ ТИК ИГРЫ
# ==========================================================

function TdTick(g: player):
	if {-td-over::%g%} is 1:
		stop

	if {-td-fx-cd::%g%} > 0:
		remove 1 from {-td-fx-cd::%g%}
		if {-td-fx-cd::%g%} <= 0:
			TdSyncHide(g, "fx")

	if {-td-in-wave::%g%} is 0:
		if {-td-auto::%g%} is 1 and {-td-wave::%g%} > 0 and {-td-wave::%g%} < {-td-maxw::%g%}:
			add 1 to {-td-auto-cd::%g%}
			if {-td-auto-cd::%g%} >= 15:
				TdNextWave(g)
		stop

	# 1. Спавн врагов
	if {-td-to-spawn::%g%} > 0 or {-td-spawn-boss::%g%} > 0:
		remove 1 from {-td-spawn-cd::%g%}
		if {-td-spawn-cd::%g%} <= 0:
			TdSpawnNext(g)

	# 2. Движение врагов + тик горения Напалма
	set {-boss-alive} to 0
	loop 10 times:
		set {-ei} to %loop-number%
		if {-td-ea::%g%::%{-ei}%} is 1:
			if {-td-eburn::%g%::%{-ei}%} > 0:
				remove 1 from {-td-eburn::%g%::%{-ei}%}
				TdDamageEnemy(g, {-ei}, {-td-eburn-d::%g%::%{-ei}%}, 0, 0)
			if {-td-ea::%g%::%{-ei}%} is 1:
				TdMoveEnemy(g, {-ei})
			if {-td-ea::%g%::%{-ei}%} is 1 and {-td-eboss::%g%::%{-ei}%} is 1:
				set {-boss-alive} to 1
				TdSyncBossBar(g, {-td-ehp::%g%::%{-ei}%}, 0)
	if {-boss-alive} is 0:
		TdSyncHide(g, "boss_bar")

	# 3. Полёт прожектайлов
	TdMoveProjectiles(g)

	# 4. Стрельба всех башен
	loop 24 times:
		set {-ti} to %loop-number%
		if {-td-ta::%g%::%{-ti}%} is 1:
			TdTowerTick(g, {-ti})

	# 5. Завершение волны
	if {-td-to-spawn::%g%} <= 0 and {-td-spawn-boss::%g%} <= 0:
		set {-rem} to 0
		loop 10 times:
			set {-k} to %loop-number%
			if {-td-ea::%g%::%{-k}%} is 1:
				add 1 to {-rem}
		if {-rem} is 0:
			set {-td-in-wave::%g%} to 0
			set {-td-auto-cd::%g%} to 0
			TdClearProjectiles(g)
			set {-bonus} to 18 + {-td-wave::%g%} * 6
			add {-bonus} to {-td-gold::%g%}
			set {-p2} to {-td-coop::%g%}
			if {-p2} is set and {-p2} is not "":
				add {-bonus} to {-td-gold::%{-p2}%}
			add 100 + {-td-wave::%g%} * 15 to {-td-score::%g%}
			TdSyncHud(g)
			if {-td-wave::%g%} >= {-td-maxw::%g%}:
				TdWin(g)
			else:
				TdSyncStatus(g, "&aВолна %{-td-wave::%g%}%/%{-td-maxw::%g%}% пройдена! +%{-bonus}%з. Жмите &eSpace!")
				TdSyncSound(g, "entity.player.levelup", 0.5, 1.25)

function TdSpawnNext(g: player):
	set {-w} to {-td-wave::%g%}
	set {-d} to {-td-diff::%g%}
	set {-dmul} to 1.0
	if {-d} is 1:
		set {-dmul} to 0.85
	else if {-d} is 3:
		set {-dmul} to 1.25
	set {-p2} to {-td-coop::%g%}
	if {-p2} is set and {-p2} is not "":
		set {-dmul} to {-dmul} * 1.45

	set {-sx} to {-td-wpx::%g%::1}
	set {-sy} to {-td-wpy::%g%::1}

	if {-td-to-spawn::%g%} > 0:
		set {-slot} to 0
		loop 9 times:
			set {-k} to %loop-number%
			if {-td-ea::%g%::%{-k}%} is 0 and {-slot} is 0:
				set {-slot} to {-k}
		if {-slot} is 0:
			set {-td-spawn-cd::%g%} to 2
			stop
		remove 1 from {-td-to-spawn::%g%}

		set {-hp} to round((32 + {-w} * 24 + {-w} * {-w} * 2.5) * {-dmul})
		set {-spd} to 3.6 + {-w} * 0.08
		set {-rew} to 10 + round({-w} * 0.65)
		set {-cd} to 8 - floor({-w} / 5)
		if {-cd} < 3:
			set {-cd} to 3
		set {-tex} to "td:textures/gui/mob_slime.png"

		set {-m6} to {-w} % 6
		if {-m6} is 2:
			set {-tex} to "td:textures/gui/mob_goblin.png"
			set {-hp} to round({-hp} * 0.72)
			set {-spd} to {-spd} + 1.45
			set {-cd} to 4
		else if {-m6} is 3:
			set {-tex} to "td:textures/gui/mob_orc.png"
			set {-hp} to round({-hp} * 1.15)
		else if {-m6} is 4:
			set {-tex} to "td:textures/gui/mob_ghost.png"
			set {-hp} to round({-hp} * 0.88)
			set {-spd} to {-spd} + 1.0
		else if {-m6} is 5:
			set {-tex} to "td:textures/gui/mob_knight.png"
			set {-hp} to round({-hp} * 1.32)
			set {-spd} to {-spd} - 0.3
		else if {-m6} is 0:
			set {-tex} to "td:textures/gui/mob_golem.png"
			set {-hp} to round({-hp} * 1.65)
			set {-spd} to {-spd} - 0.7
			add 5 to {-rew}

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
		set {-td-eslow::%g%::%{-slot}%} to 0
		set {-td-eslow-f::%g%::%{-slot}%} to 0.55
		set {-td-efreeze::%g%::%{-slot}%} to 0
		set {-td-eburn::%g%::%{-slot}%} to 0
		TdSyncImg(g, "e%{-slot}%", {-tex})
		TdSyncPos(g, "e%{-slot}%", {-sx}, {-sy})
		TdSyncShow(g, "e%{-slot}%")
		stop

	# Спавн Босса (слот 10)
	if {-td-spawn-boss::%g%} > 0 and {-td-ea::%g%::10} is 0:
		set {-bk} to {-td-spawn-boss::%g%}
		set {-td-spawn-boss::%g%} to 0
		set {-bhp} to round((480 + {-bk} * {-bk} * 380) * {-dmul})
		set {-bspd} to 2.25 + {-bk} * 0.08
		set {-brew} to 90 + {-bk} * 75
		set {-btex} to "td:textures/gui/mob_boss.png"
		if {-w} is {-td-maxw::%g%}:
			set {-btex} to "td:textures/gui/mob_megaboss.png"
			set {-bhp} to round({-bhp} * 1.35)
		set {-td-ea::%g%::10} to 1
		set {-td-eboss::%g%::10} to 1
		set {-td-ex::%g%::10} to {-sx}
		set {-td-ey::%g%::10} to {-sy}
		set {-td-eseg::%g%::10} to 2
		set {-td-ehp::%g%::10} to {-bhp}
		set {-td-emhp::%g%::10} to {-bhp}
		set {-td-espd::%g%::10} to {-bspd}
		set {-td-erew::%g%::10} to {-brew}
		set {-td-eslow::%g%::10} to 0
		set {-td-eslow-f::%g%::10} to 0.55
		set {-td-efreeze::%g%::10} to 0
		set {-td-eburn::%g%::10} to 0
		TdSyncImg(g, "e10", {-btex})
		TdSyncPos(g, "e10", {-sx}, {-sy})
		TdSyncShow(g, "e10")
		TdSyncBossBar(g, {-bhp}, {-bhp})
		TdSyncShow(g, "boss_bar")

function TdMoveEnemy(g: player, i: number):
	if {-td-efreeze::%g%::%i%} > 0:
		remove 1 from {-td-efreeze::%g%::%i%}
		stop
	set {-spd} to {-td-espd::%g%::%i%}
	if {-td-eslow::%g%::%i%} > 0:
		remove 1 from {-td-eslow::%g%::%i%}
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
			set {-dmg} to 1
			if {-td-eboss::%g%::%i%} is 1:
				set {-dmg} to 5
			remove {-dmg} from {-td-hp::%g%}
			TdSyncSound(g, "entity.iron_golem.damage", 0.7, 0.8)
			shake camera of g with intensity 1.6 for 8 ticks
			fade screen of g to #FF1100 in 2 stay 2 out 8 ticks
			set {-p2} to {-td-coop::%g%}
			if {-p2} is set and {-p2} is not "":
				shake camera of {-p2} with intensity 1.6 for 8 ticks
				fade screen of {-p2} to #FF1100 in 2 stay 2 out 8 ticks
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
	TdSyncPos(g, "e%i%", {-x}, {-y})

# ==========================================================
#  БАШНИ, ЭВОЛЮЦИИ ✦5★ И ЛЕТАЮЩИЕ ПРОЖЕКТАЙЛЫ
# ==========================================================

function TdTowerTick(g: player, tid: number):
	if {-td-tcd::%g%::%tid%} > 0:
		remove 1 from {-td-tcd::%g%::%tid%}
		stop

	set {-type} to {-td-tt::%g%::%tid%}
	set {-lvl} to {-td-tl::%g%::%tid%}
	set {-tx} to {-td-tx::%g%::%tid%}
	set {-ty} to {-td-ty::%g%::%tid%}
	set {-evo} to 0
	if {-lvl} >= 5:
		set {-evo} to 1

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
	if {-evo} is 1:
		add 14 to {-range}
		set {-dmg} to round({-dmg} * 1.4)

	# Ищем лучшую цель (и вторую цель для ✦5★ Лучника «Шквал»)
	set {-best} to 0
	set {-best-prog} to -1
	set {-second} to 0
	loop 10 times:
		set {-ei} to %loop-number%
		if {-td-ea::%g%::%{-ei}%} is 1:
			set {-dx} to {-td-ex::%g%::%{-ei}%} - {-tx}
			set {-dy} to {-td-ey::%g%::%{-ei}%} - {-ty}
			if sqrt({-dx} * {-dx} + {-dy} * {-dy}) <= {-range}:
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

	# Пассивка ✦5★ Лучника: Шквал — выстрел сразу по 2-й цели!
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

function TdMoveProjectiles(g: player):
	loop 6 times:
		set {-k} to %loop-number%
		if {-td-pra::%g%::%{-k}%} is 1:
			set {-tgt} to {-td-prtgt::%g%::%{-k}%}
			if {-td-ea::%g%::%{-tgt}%} is not 1:
				set {-td-pra::%g%::%{-k}%} to 0
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
					TdSyncHide(g, "pr%{-k}%")
					TdImpact(g, {-tgt}, {-td-prtype::%g%::%{-k}%}, {-td-prevo::%g%::%{-k}%}, {-td-prdmg::%g%::%{-k}%}, {-tx}, {-ty})
				else:
					set {-nx} to {-px} + ({-dx} / {-dist}) * {-spd}
					set {-ny} to {-py} + ({-dy} / {-dist}) * {-spd}
					set {-td-prx::%g%::%{-k}%} to {-nx}
					set {-td-pry::%g%::%{-k}%} to {-ny}
					TdSyncPos(g, "pr%{-k}%", {-nx}, {-ny})

function TdClearProjectiles(g: player):
	loop 6 times:
		set {-k} to %loop-number%
		set {-td-pra::%g%::%{-k}%} to 0
		TdSyncHide(g, "pr%{-k}%")

function TdImpact(g: player, tgt: number, ptype: number, evo: number, dmg: number, hx: number, hy: number):
	if ptype is 2:
		# Пушка: сплеш (+ ✦5★ Напалм: радиус 62 и поджог!)
		TdSyncPos(g, "fx", hx, hy)
		TdSyncShow(g, "fx")
		set {-td-fx-cd::%g%} to 2
		TdSyncSound(g, "entity.generic.explode", 0.16, 1.65)
		set {-rad} to 44
		if evo is 1:
			set {-rad} to 62
		loop 10 times:
			set {-si} to %loop-number%
			if {-td-ea::%g%::%{-si}%} is 1:
				set {-sdx} to {-td-ex::%g%::%{-si}%} - hx
				set {-sdy} to {-td-ey::%g%::%{-si}%} - hy
				if sqrt({-sdx} * {-sdx} + {-sdy} * {-sdy}) <= {-rad}:
					TdDamageEnemy(g, {-si}, dmg, 0, 0)
					if evo is 1 and {-td-ea::%g%::%{-si}%} is 1:
						set {-td-eburn::%g%::%{-si}%} to 12
						set {-td-eburn-d::%g%::%{-si}%} to round(dmg * 0.12)
	else if ptype is 3:
		# Лёд: замедление 45% (+ ✦5★ Вечная мерзлота: 65% + 25% заморозка на месте!)
		TdSyncSound(g, "block.glass.break", 0.18, 1.75)
		set {-frz} to 0
		if evo is 1:
			set {-td-eslow-f::%g%::%tgt%} to 0.35
			chance of 25%:
				set {-frz} to 12
		else:
			set {-td-eslow-f::%g%::%tgt%} to 0.55
		TdDamageEnemy(g, tgt, dmg, 20, {-frz})
	else if ptype is 4:
		# Маг (+ ✦5★ Убийца Боссов: x2 урон Боссам + 4% макс. HP!)
		set {-fdmg} to dmg
		if evo is 1:
			if {-td-eboss::%g%::%tgt%} is 1:
				set {-fdmg} to round({-fdmg} * 2.0)
			add round({-td-emhp::%g%::%tgt%} * 0.04) to {-fdmg}
		TdSyncSound(g, "entity.illusioner.cast_spell", 0.18, 1.5)
		TdDamageEnemy(g, tgt, {-fdmg}, 0, 0)
	else if ptype is 5:
		# Тесла: цепная молния 3 цели (+ ✦5★ Шторм: 5 целей, 90% урон, стан 4 тика!)
		TdSyncSound(g, "entity.lightning_bolt.impact", 0.14, 1.9)
		set {-maxh} to 3
		set {-crad} to 68
		set {-cmul} to 0.75
		set {-stun} to 0
		if evo is 1:
			set {-maxh} to 5
			set {-crad} to 88
			set {-cmul} to 0.9
			set {-stun} to 4
		TdDamageEnemy(g, tgt, dmg, 0, {-stun})
		set {-hits} to 1
		loop 10 times:
			set {-si} to %loop-number%
			if {-si} is not tgt and {-hits} < {-maxh} and {-td-ea::%g%::%{-si}%} is 1:
				set {-sdx} to {-td-ex::%g%::%{-si}%} - hx
				set {-sdy} to {-td-ey::%g%::%{-si}%} - hy
				if sqrt({-sdx} * {-sdx} + {-sdy} * {-sdy}) <= {-crad}:
					TdDamageEnemy(g, {-si}, round(dmg * {-cmul}), 0, {-stun})
					add 1 to {-hits}
	else if ptype is 6:
		# Снайпер: крит 25% x2 (+ ✦5★ Казнь: крит 45% x2.5 + казнь обычных мобов <20% HP!)
		set {-fdmg} to dmg
		if evo is 1:
			chance of 45%:
				set {-fdmg} to round(dmg * 2.5)
				TdSyncPos(g, "fx", hx, hy)
				TdSyncShow(g, "fx")
				set {-td-fx-cd::%g%} to 2
			if {-td-eboss::%g%::%tgt%} is 0 and {-td-ehp::%g%::%tgt%} <= {-td-emhp::%g%::%tgt%} * 0.2:
				set {-fdmg} to 999999
		else:
			chance of 25%:
				set {-fdmg} to dmg * 2
				TdSyncPos(g, "fx", hx, hy)
				TdSyncShow(g, "fx")
				set {-td-fx-cd::%g%} to 2
		TdSyncSound(g, "entity.arrow.hit_player", 0.24, 1.6)
		TdDamageEnemy(g, tgt, {-fdmg}, 0, 0)
	else:
		TdSyncSound(g, "entity.arrow.shoot", 0.18, 1.45)
		TdDamageEnemy(g, tgt, dmg, 0, 0)

function TdDamageEnemy(g: player, i: number, dmg: number, slow: number, freeze: number):
	if {-td-ea::%g%::%i%} is not 1:
		stop
	remove dmg from {-td-ehp::%g%::%i%}
	if slow > 0:
		set {-td-eslow::%g%::%i%} to slow
	if freeze > 0:
		set {-td-efreeze::%g%::%i%} to freeze
	if {-td-ehp::%g%::%i%} <= 0:
		set {-td-ea::%g%::%i%} to 0
		TdSyncHide(g, "e%i%")
		set {-rew} to {-td-erew::%g%::%i%}
		set {-p2} to {-td-coop::%g%}
		if {-p2} is set and {-p2} is not "":
			set {-share} to round({-rew} * 0.65)
			add {-share} to {-td-gold::%g%}
			add {-share} to {-td-gold::%{-p2}%}
		else:
			add {-rew} to {-td-gold::%g%}
		add {-rew} to {-td-score::%g%}
		if {-td-eboss::%g%::%i%} is 1:
			TdSyncHide(g, "boss_bar")
			TdSyncSound(g, "ui.toast.challenge_complete", 0.7, 1.1)
		TdSyncHud(g)

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
	TdSaveScore(g)

function TdWin(g: player):
	set {-td-over::%g%} to 1
	set {-wbon} to {-td-hp::%g%} * 100 * {-td-diff::%g%}
	add {-wbon} to {-td-score::%g%}
	TdSyncHud(g)
	TdSyncTxt(g, "end_title", "&6&lПОБЕДА! ВСЕ %{-td-maxw::%g%}% ВОЛН ПРОЙДЕНЫ!")
	TdSyncTxt(g, "end_sub", "&aСчёт: &e%{-td-score::%g%}% &7— нажми &eR &7(ещё раз) или &eM &7(меню)")
	TdSyncShow(g, "end_title")
	TdSyncShow(g, "end_sub")
	fade screen of g to #FFD700 in 3 stay 4 out 14 ticks
	set {-p2} to {-td-coop::%g%}
	if {-p2} is set and {-p2} is not "":
		fade screen of {-p2} to #FFD700 in 3 stay 4 out 14 ticks
	TdSyncSound(g, "ui.toast.challenge_complete", 0.9, 1.0)
	TdSaveScore(g)

function TdSaveScore(g: player):
	set {-sc} to {-td-score::%g%}
	if {td::lb::%g%} is not set:
		set {td::lb::%g%} to {-sc}
	else if {-sc} > {td::lb::%g%}:
		set {td::lb::%g%} to {-sc}
	set {-p2} to {-td-coop::%g%}
	if {-p2} is set and {-p2} is not "":
		if {td::lb::%{-p2}%} is not set:
			set {td::lb::%{-p2}%} to {-sc}
		else if {-sc} > {td::lb::%{-p2}%}:
			set {td::lb::%{-p2}%} to {-sc}
