# ==========================================================
#  td.sc — Tower Defense (10 волн + Боссы + 4 вида башен)
#  Команда: /td
#  Управление:
#    1 / 2 / 3 / 4 — выбрать башню (Лучник / Пушка / Лёд / Маг)
#    5 / 6         — режим Улучшить / Продать
#    ЛКМ по площадке на карте — построить / улучшить / продать
#    Space         — запустить следующую волну
#    R             — перезапуск
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

on key 5 in screen "Tower Defense":
	TdSelectTool(player, 5)

on key 6 in screen "Tower Defense":
	TdSelectTool(player, 6)

on key space in screen "Tower Defense":
	TdNextWave(player)

on key r in screen "Tower Defense":
	TdStart(player)

# --- Игровой цикл (10 тиков/сек — плавно и экономично по пакетам) ---

every 2 ticks:
	loop all players:
		if {-td-active::%loop-player%} is 1:
			if loop-player has screen "Tower Defense" open:
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
	set {-td-gold::%p%} to 130
	set {-td-hp::%p%} to 10
	set {-td-wave::%p%} to 0
	set {-td-score::%p%} to 0
	set {-td-tool::%p%} to 1
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

	loop 6 times:
		set {-i} to %loop-number%
		set {-td-ea::%p%::%{-i}%} to 0
		set {-td-eboss::%p%::%{-i}%} to 0
		set {-td-eslow::%p%::%{-i}%} to 0

	open screen "Tower Defense" to p:
		image "td:textures/gui/td_map.png" at 0 -14 size 320x180 texture 320x180 anchor center layer 0 id map
		text "&eВолна: &f0/10   &6Золото: &f130   &cБаза: &f10 HP   &aСчёт: &f0" at 0 -112 anchor center shadow layer 10 id hud_top
		bar "boss_bar" value 100 max 100 at 0 -98 size 180x8 anchor center color #E74C3C background #AA1A0A0A text "&c&lБОСС: %value%/%max%" shadow layer 12 hidden

		# 8 кликабельных площадок для башен
		image "td:textures/gui/pad_empty.png" at -90 -38 size 22x22 texture 22x22 anchor center layer 3 id p1 run TdPadClick(player, 1) keep open tooltip "&7Площадка #1 — клик для постройки"
		image "td:textures/gui/pad_empty.png" at -20 -38 size 22x22 texture 22x22 anchor center layer 3 id p2 run TdPadClick(player, 2) keep open tooltip "&7Площадка #2 — клик для постройки"
		image "td:textures/gui/pad_empty.png" at 50 -38 size 22x22 texture 22x22 anchor center layer 3 id p3 run TdPadClick(player, 3) keep open tooltip "&7Площадка #3 — клик для постройки"
		image "td:textures/gui/pad_empty.png" at 134 -38 size 22x22 texture 22x22 anchor center layer 3 id p4 run TdPadClick(player, 4) keep open tooltip "&7Площадка #4 — клик для постройки"
		image "td:textures/gui/pad_empty.png" at -134 18 size 22x22 texture 22x22 anchor center layer 3 id p5 run TdPadClick(player, 5) keep open tooltip "&7Площадка #5 — клик для постройки"
		image "td:textures/gui/pad_empty.png" at -50 18 size 22x22 texture 22x22 anchor center layer 3 id p6 run TdPadClick(player, 6) keep open tooltip "&7Площадка #6 — клик для постройки"
		image "td:textures/gui/pad_empty.png" at 24 18 size 22x22 texture 22x22 anchor center layer 3 id p7 run TdPadClick(player, 7) keep open tooltip "&7Площадка #7 — клик для постройки"
		image "td:textures/gui/pad_empty.png" at 94 18 size 22x22 texture 22x22 anchor center layer 3 id p8 run TdPadClick(player, 8) keep open tooltip "&7Площадка #8 — клик для постройки"

		# Уровни башен
		text "" at -90 -24 anchor center shadow layer 6 id pl1 hidden
		text "" at -20 -24 anchor center shadow layer 6 id pl2 hidden
		text "" at 50 -24 anchor center shadow layer 6 id pl3 hidden
		text "" at 134 -24 anchor center shadow layer 6 id pl4 hidden
		text "" at -134 32 anchor center shadow layer 6 id pl5 hidden
		text "" at -50 32 anchor center shadow layer 6 id pl6 hidden
		text "" at 24 32 anchor center shadow layer 6 id pl7 hidden
		text "" at 94 32 anchor center shadow layer 6 id pl8 hidden

		# 6 слотов врагов + босс
		image "td:textures/gui/mob_slime.png" at -148 -66 size 16x16 texture 16x16 anchor center layer 7 id e1 hidden
		image "td:textures/gui/mob_slime.png" at -148 -66 size 16x16 texture 16x16 anchor center layer 7 id e2 hidden
		image "td:textures/gui/mob_slime.png" at -148 -66 size 16x16 texture 16x16 anchor center layer 7 id e3 hidden
		image "td:textures/gui/mob_slime.png" at -148 -66 size 16x16 texture 16x16 anchor center layer 7 id e4 hidden
		image "td:textures/gui/mob_slime.png" at -148 -66 size 16x16 texture 16x16 anchor center layer 7 id e5 hidden
		image "td:textures/gui/mob_boss.png" at -148 -66 size 22x22 texture 22x22 anchor center layer 8 id e6 hidden

		# Эффект попадания снаряда
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 10x10 anchor center layer 9 id fx hidden

		# Статус и оверлей победы/поражения
		text "&aВыбрано: &fЛучник (50з) &7— кликните по площадке и нажмите Старт волны!" at 0 82 anchor center shadow layer 10 id status_txt
		text "&c&lБАЗА ПАЛА!" at 0 -22 anchor center shadow layer 20 id end_title hidden
		text "&fНажми &eR &fили кнопку Рестарт" at 0 -6 anchor center shadow layer 20 id end_sub hidden

		# Панель инструментов (2 ряда внизу)
		button "[1] Лучник 50" at -122 98 size 76x18 anchor center run TdSelectTool(player, 1) keep open tooltip "&eЛучник &7(50 золота)\n&7Урон: 14 | Быстрый выстрел\n&7Дальность: 86"
		button "[2] Пушка 90" at -42 98 size 76x18 anchor center run TdSelectTool(player, 2) keep open tooltip "&6Пушка &7(90 золота)\n&7Урон: 34 + &cСплеш по области\n&7Дальность: 76"
		button "[3] Лёд 80" at 38 98 size 76x18 anchor center run TdSelectTool(player, 3) keep open tooltip "&bЛедяная башня &7(80 золота)\n&7Урон: 10 + &bЗамедление 45%\n&7Дальность: 82"
		button "[4] Маг 120" at 118 98 size 76x18 anchor center run TdSelectTool(player, 4) keep open tooltip "&dБашня Мага &7(120 золота)\n&7Урон: 26 | &dОгромная дальность 112\n&7Идеальна против Боссов"

		button "[5] Апгрейд" at -122 119 size 76x18 anchor center run TdSelectTool(player, 5) keep open tooltip "&aУлучшить башню &7(до ур. 3)\n&7Цена: 60 / 120 золота\n&7+55% урона и +10 дальности"
		button "[6] Продать" at -42 119 size 76x18 anchor center run TdSelectTool(player, 6) keep open tooltip "&cПродать башню\n&7Возврат 70% стоимости"
		button "&aСтарт волны" at 38 119 size 76x18 anchor center run TdNextWave(player) keep open tooltip "&aЗапустить следующую волну &7(Space)"
		button "Рестарт / Топ" at 118 119 size 76x18 anchor center run TdRestartOrTop(player) keep open tooltip "&eНачать заново &7(R) и показать Топ-5"

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
	if tool is 1:
		set text of screen element "status_txt" for p to "&eВыбрано: &fЛучник (50з) &7— клик по площадке"
	else if tool is 2:
		set text of screen element "status_txt" for p to "&6Выбрано: &fПушка (90з, сплеш) &7— клик по площадке"
	else if tool is 3:
		set text of screen element "status_txt" for p to "&bВыбрано: &fЛёд (80з, замедление) &7— клик по площадке"
	else if tool is 4:
		set text of screen element "status_txt" for p to "&dВыбрано: &fМаг (120з, дальний) &7— клик по площадке"
	else if tool is 5:
		set text of screen element "status_txt" for p to "&aРежим: &fАпгрейд &7— клик по построенной башне"
	else if tool is 6:
		set text of screen element "status_txt" for p to "&cРежим: &fПродать (70%%) &7— клик по башне"
	play sound ui.button.click to p with volume 0.4 pitch 1.3

function TdPadClick(p: player, pad: number):
	if {-td-active::%p%} is not 1 or {-td-over::%p%} is 1:
		stop
	set {-tool} to {-td-tool::%p%}
	set {-cur} to {-td-pt::%p%::%pad%}
	set {-lvl} to {-td-pl::%p%::%pad%}

	# Продажа
	if {-tool} is 6:
		if {-cur} is 0:
			set text of screen element "status_txt" for p to "&7Площадка #%pad% пуста"
			stop
		set {-refund} to round({-td-pcost::%p%::%pad%} * 0.7)
		add {-refund} to {-td-gold::%p%}
		set {-td-pt::%p%::%pad%} to 0
		set {-td-pl::%p%::%pad%} to 0
		set {-td-pcost::%p%::%pad%} to 0
		set image of screen element "p%pad%" for p to "td:textures/gui/pad_empty.png"
		hide screen element "pl%pad%" from p
		set text of screen element "status_txt" for p to "&eБашня продана за +%{-refund}% золота"
		play sound entity.experience_orb.pickup to p with volume 0.6 pitch 0.9
		TdUpdateHud(p)
		stop

	# Апгрейд
	if {-tool} is 5:
		if {-cur} is 0:
			set text of screen element "status_txt" for p to "&cЗдесь нет башни для улучшения"
			stop
		if {-lvl} >= 3:
			set text of screen element "status_txt" for p to "&6Башня #%pad% уже макс. уровня (★3)!"
			stop
		set {-up-cost} to {-lvl} * 60
		if {-td-gold::%p%} < {-up-cost}:
			set text of screen element "status_txt" for p to "&cНе хватает золота! Нужно %{-up-cost}%з"
			play sound entity.villager.no to p with volume 0.5 pitch 1.0
			stop
		remove {-up-cost} from {-td-gold::%p%}
		add {-up-cost} to {-td-pcost::%p%::%pad%}
		add 1 to {-td-pl::%p%::%pad%}
		set {-nlvl} to {-td-pl::%p%::%pad%}
		if {-nlvl} is 2:
			set text of screen element "pl%pad%" for p to "&6★★"
		else:
			set text of screen element "pl%pad%" for p to "&d★★★"
		set text of screen element "status_txt" for p to "&aБашня #%pad% улучшена до ур. %{-nlvl}%!"
		play sound entity.player.levelup to p with volume 0.5 pitch 1.4
		TdUpdateHud(p)
		stop

	# Постройка новой башни (tool 1..4)
	if {-cur} is not 0:
		set text of screen element "status_txt" for p to "&eПлощадка #%pad% занята! Выберите [5] Апгрейд или [6] Продать"
		stop

	set {-cost} to 50
	set {-tex} to "td:textures/gui/tower_archer.png"
	if {-tool} is 2:
		set {-cost} to 90
		set {-tex} to "td:textures/gui/tower_cannon.png"
	else if {-tool} is 3:
		set {-cost} to 80
		set {-tex} to "td:textures/gui/tower_frost.png"
	else if {-tool} is 4:
		set {-cost} to 120
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
	set text of screen element "status_txt" for p to "&aБашня построена на площадке #%pad%!"
	play sound block.anvil.use to p with volume 0.35 pitch 1.5
	TdUpdateHud(p)

function TdNextWave(p: player):
	if {-td-active::%p%} is not 1 or {-td-over::%p%} is 1:
		stop
	if {-td-in-wave::%p%} is 1:
		set text of screen element "status_txt" for p to "&eВолна уже идёт!"
		stop
	if {-td-wave::%p%} >= 10:
		stop

	add 1 to {-td-wave::%p%}
	set {-w} to {-td-wave::%p%}
	set {-td-in-wave::%p%} to 1
	set {-td-spawn-cd::%p%} to 1
	set {-td-spawn-boss::%p%} to 0

	if {-w} is 1:
		set {-td-to-spawn::%p%} to 5
	else if {-w} is 2:
		set {-td-to-spawn::%p%} to 7
	else if {-w} is 3:
		set {-td-to-spawn::%p%} to 6
	else if {-w} is 4:
		set {-td-to-spawn::%p%} to 8
	else if {-w} is 5:
		set {-td-to-spawn::%p%} to 4
		set {-td-spawn-boss::%p%} to 1
		set text of screen element "status_txt" for p to "&c&lВОЛНА 5 — МИНИ-БОСС ВЫХОДИТ НА ПОЛЕ!"
		play sound entity.ender_dragon.growl to p with volume 0.5 pitch 1.1
	else if {-w} is 6:
		set {-td-to-spawn::%p%} to 7
	else if {-w} is 7:
		set {-td-to-spawn::%p%} to 9
	else if {-w} is 8:
		set {-td-to-spawn::%p%} to 8
	else if {-w} is 9:
		set {-td-to-spawn::%p%} to 10
	else if {-w} is 10:
		set {-td-to-spawn::%p%} to 5
		set {-td-spawn-boss::%p%} to 2
		set text of screen element "status_txt" for p to "&4&lФИНАЛЬНАЯ ВОЛНА 10 — МЕГА-БОСС!"
		play sound entity.wither.spawn to p with volume 0.5 pitch 1.0

	if {-w} is not 5 and {-w} is not 10:
		set text of screen element "status_txt" for p to "&eВолна %{-w}%/10 началась!"
		play sound entity.experience_orb.pickup to p with volume 0.5 pitch 1.2
	TdUpdateHud(p)

function TdUpdateHud(p: player):
	set text of screen element "hud_top" for p to "&eВолна: &f%{-td-wave::%p%}%/10   &6Золото: &f%{-td-gold::%p%}%   &cБаза: &f%{-td-hp::%p%}% HP   &aСчёт: &f%{-td-score::%p%}%"

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
	set {-alive} to 0
	set {-boss-alive} to 0
	loop 6 times:
		set {-ei} to %loop-number%
		if {-td-ea::%p%::%{-ei}%} is 1:
			add 1 to {-alive}
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
		loop 6 times:
			set {-k} to %loop-number%
			if {-td-ea::%p%::%{-k}%} is 1:
				add 1 to {-rem}
		if {-rem} is 0:
			set {-td-in-wave::%p%} to 0
			set {-bonus} to 25 + {-td-wave::%p%} * 10
			add {-bonus} to {-td-gold::%p%}
			add 100 to {-td-score::%p%}
			TdUpdateHud(p)
			if {-td-wave::%p%} >= 10:
				TdWin(p)
			else:
				set text of screen element "status_txt" for p to "&aВолна %{-td-wave::%p%}% пройдена! +%{-bonus}%з. Жмите &eSpace &aдля следующей!"
				play sound entity.player.levelup to p with volume 0.5 pitch 1.2

function TdSpawnNext(p: player):
	set {-w} to {-td-wave::%p%}
	# Сначала обычные мобы (слоты 1..5), затем Босс (слот 6)
	if {-td-to-spawn::%p%} > 0:
		set {-slot} to 0
		loop 5 times:
			set {-k} to %loop-number%
			if {-td-ea::%p%::%{-k}%} is 0 and {-slot} is 0:
				set {-slot} to {-k}
		if {-slot} is 0:
			set {-td-spawn-cd::%p%} to 3
			stop
		remove 1 from {-td-to-spawn::%p%}
		set {-td-spawn-cd::%p%} to 11
		set {-hp} to 30
		set {-spd} to 2.7
		set {-rew} to 16
		set {-tex} to "td:textures/gui/mob_slime.png"
		if {-w} is 2:
			set {-hp} to 44
			set {-spd} to 3.0
		else if {-w} is 3:
			set {-hp} to 75
			set {-spd} to 2.4
			set {-rew} to 22
			set {-tex} to "td:textures/gui/mob_orc.png"
		else if {-w} is 4:
			set {-hp} to 95
			set {-spd} to 2.6
			set {-rew} to 22
			set {-tex} to "td:textures/gui/mob_orc.png"
		else if {-w} is 5:
			set {-hp} to 115
			set {-spd} to 2.6
			set {-rew} to 24
			set {-tex} to "td:textures/gui/mob_orc.png"
		else if {-w} is 6:
			set {-hp} to 155
			set {-spd} to 2.2
			set {-rew} to 28
			set {-tex} to "td:textures/gui/mob_knight.png"
		else if {-w} is 7:
			set {-hp} to 115
			set {-spd} to 3.7
			set {-rew} to 22
			set {-tex} to "td:textures/gui/mob_slime.png"
			set {-td-spawn-cd::%p%} to 7
		else if {-w} is 8:
			set {-hp} to 215
			set {-spd} to 2.3
			set {-rew} to 32
			set {-tex} to "td:textures/gui/mob_knight.png"
		else if {-w} is 9:
			set {-hp} to 265
			set {-spd} to 2.5
			set {-rew} to 34
			set {-tex} to "td:textures/gui/mob_knight.png"
		else if {-w} is 10:
			set {-hp} to 310
			set {-spd} to 2.5
			set {-rew} to 38
			set {-tex} to "td:textures/gui/mob_knight.png"

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

	# Спавн Босса (слот 6)
	if {-td-spawn-boss::%p%} > 0 and {-td-ea::%p%::6} is 0:
		set {-bkind} to {-td-spawn-boss::%p%}
		set {-td-spawn-boss::%p%} to 0
		set {-bhp} to 440
		set {-bspd} to 1.8
		set {-brew} to 130
		if {-bkind} is 2:
			set {-bhp} to 1200
			set {-bspd} to 1.6
			set {-brew} to 300
		set {-td-ea::%p%::6} to 1
		set {-td-eboss::%p%::6} to 1
		set {-td-ex::%p%::6} to -148
		set {-td-ey::%p%::6} to -66
		set {-td-eseg::%p%::6} to 1
		set {-td-ehp::%p%::6} to {-bhp}
		set {-td-espd::%p%::6} to {-bspd}
		set {-td-erew::%p%::6} to {-brew}
		set {-td-eslow::%p%::6} to 0
		set position of screen element "e6" for p to -148 -66
		show screen element "e6" to p
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

	# Параметры башни с учётом уровня
	set {-range} to 86
	set {-dmg} to 14
	set {-cd} to 5
	set {-ptex} to "td:textures/gui/proj_arrow.png"
	if {-type} is 2:
		set {-range} to 76
		set {-dmg} to 34
		set {-cd} to 12
		set {-ptex} to "td:textures/gui/proj_cannon.png"
	else if {-type} is 3:
		set {-range} to 82
		set {-dmg} to 10
		set {-cd} to 8
		set {-ptex} to "td:textures/gui/proj_frost.png"
	else if {-type} is 4:
		set {-range} to 112
		set {-dmg} to 26
		set {-cd} to 7
		set {-ptex} to "td:textures/gui/proj_mage.png"

	set {-range} to {-range} + ({-lvl} - 1) * 10
	set {-dmg} to round({-dmg} * (1 + ({-lvl} - 1) * 0.55))

	# Ищем ближайшего живого врага в радиусе (приоритет — самый продвинутый по сегменту)
	set {-best} to 0
	set {-best-prog} to -1
	loop 6 times:
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
		play sound entity.generic.explode to p with volume 0.22 pitch 1.6
		loop 6 times:
			set {-si} to %loop-number%
			if {-td-ea::%p%::%{-si}%} is 1:
				set {-sdx} to {-td-ex::%p%::%{-si}%} - {-hx}
				set {-sdy} to {-td-ey::%p%::%{-si}%} - {-hy}
				if sqrt({-sdx} * {-sdx} + {-sdy} * {-sdy}) <= 34:
					TdDamageEnemy(p, {-si}, {-dmg}, 0)
	else if {-type} is 3:
		play sound block.glass.break to p with volume 0.25 pitch 1.7
		TdDamageEnemy(p, {-best}, {-dmg}, 18)
	else:
		play sound entity.arrow.shoot to p with volume 0.25 pitch 1.4
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
	set text of screen element "end_title" for p to "&c&lБАЗА ПАЛА! (Волна %{-td-wave::%p%}%/10)"
	set text of screen element "end_sub" for p to "&fСчёт: &e%{-td-score::%p%}% &7— нажми &eR &7для рестарта"
	show screen element "end_title" to p
	show screen element "end_sub" to p
	TdSaveScore(p)

function TdWin(p: player):
	set {-td-over::%p%} to 1
	add {-td-hp::%p%} * 50 to {-td-score::%p%}
	TdUpdateHud(p)
	set text of screen element "end_title" for p to "&6&lПОБЕДА! ВСЕ 10 ВОЛН ПРОЙДЕНЫ!"
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
