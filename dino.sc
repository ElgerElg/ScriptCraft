# ==========================================================
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
