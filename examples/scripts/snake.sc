# ==========================================================
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
	delete {-snake_tick::%player%}
