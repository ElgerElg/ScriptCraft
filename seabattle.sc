# ==========================================================
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
		image "seabattle:textures/gui/compass1.png" at 0 -64 size 22x22 texture 22x22 anchor center layer 6 id compass tooltip "&9Морской Компас\n&7Пиратская тетрадь в клетку"

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
		input "foe_input" at -118 96 size 92x18 anchor center placeholder "Ник соперника..." max 16 tooltip "&eВведите ник игрока\n&7и нажмите Вызвать"
		button "&6⚔ Вызвать" at -40 96 size 58x18 anchor center run SbInviteFromInput(player) keep open tooltip "&6Отправить вызов игроку из поля ввода\n&7(или принять его вызов)"
		button "Поворот [R]" at 22 96 size 62x18 anchor center run SbRotate(player) keep open id btn_rot tooltip "&bСменить ориентацию корабля\n&7Горизонтально / Вертикально (клавиша R)"
		button "🎲 Авто-флот" at 84 96 size 60x18 anchor center run SbAutoFleet(player) keep open id btn_auto tooltip "&aАвтоматически расставить все 6 кораблей\n&7по правилам без касаний"
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
	send "&6⚓ &e%p% &fвызывает вас в &bМорской Бой&f! Напишите &a/seabattle %p% &f(или введите его ник в `/sb`)" to target
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
