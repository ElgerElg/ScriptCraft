# ==========================================================
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

function SnakeSpawnBonus(p: player):
	set {-col} to random integer between -7 and 7
	set {-row} to random integer between -5 and 5
	set {-bx::%p%} to {-col} * 12
	set {-by::%p%} to {-row} * 12
	set {-snake_bonus_t::%p%} to 40
	move screen "bonus" for p to {-bx::%p%}, {-by::%p%}
	show screen "bonus" for p
	set value of screen bar "bonus_bar" for p to 40
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
		set value of screen bar "bonus_bar" for p to {-snake_bonus_t::%p%}
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
		set value of screen bar "bonus_bar" for p to 0
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
				SnakeStep(loop-player)