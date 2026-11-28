# ==========================================================
#  space.sc — Space Invaders Co-op (1-4 игрока, 10 уровней + Босс)
#  Команды:
#    /space                  — главное меню и кооп-лобби
#    /space coop <игрок>     — пригласить в отряд (до 4 игроков)
#    /space join             — принять приглашение / вернуться в игру
#    /space leave            — выйти из отряда
#  Управление в игре:
#    A / D или Left / Right  — движение корабля
#    Space / W / Up          — стрельба (можно зажать)
#    Q                       — Нова-бомба (при 100% заряда Overdrive)
#    P                       — пауза
#    R                       — рестарт (после поражения или победы)
# ==========================================================

on load:
	clear {-si-active::*}
	clear {-si-host::*}
	clear {-si-team::*}
	clear {-si-online::*}
	clear {-si-inv::*}
	clear {-si-reconn::*}
	clear {-si-kleft::*}
	clear {-si-kright::*}
	clear {-si-kfire::*}

on quit:
	SiOnLeave(player)

on screen close "Space Invaders":
	SiOnLeave(player)

command /space [text] [player]:
	aliases: invaders, si
	if arg-1 is not set:
		SiOpenMenu(player)
		stop
	if arg-1 is "coop" or "invite":
		if arg-2 is not set:
			send "&b[Space Invaders] &eИспользование: &f/space coop <ник> &7(до 4 игроков)" to player
			stop
		if arg-2 is player:
			send "&cНельзя пригласить самого себя!" to player
			stop
		if {-si-team::%player%::*} is empty:
			add %player% to {-si-team::%player%::*}
		if size of {-si-team::%player%::*} >= 4:
			send "&cВ отряде уже максимум (4/4 пилота)!" to player
			stop
		set {-si-inv::%arg-2%} to %player%
		send "&aПриглашение отправлено пилоту &f%arg-2%&a! Жмите &e▶ СТАРТ&a, когда все зайдут." to player
		send "&b[Space Invaders] &e%player% &fприглашает вас в кооп (до 4 игроков)! Напишите &a/space join" to arg-2
		play sound entity.experience_orb.pickup to arg-2 with volume 0.7 pitch 1.4
		SiOpenMenu(player)
		stop
	if arg-1 is "join" or "accept" or "rejoin":
		if {-si-reconn::%player%} is set:
			set {-rh} to {-si-reconn::%player%}
			if {-rh} is online and {-si-active::%{-rh}%} is 1:
				delete {-si-reconn::%player%}
				SiReconnect({-rh}, player)
				stop
			delete {-si-reconn::%player%}
		if {-si-inv::%player%} is not set:
			send "&cУ вас нет активных приглашений в Space Invaders." to player
			stop
		set {-host} to {-si-inv::%player%}
		delete {-si-inv::%player%}
		if {-host} is not online:
			send "&cХост %{-host}% не в сети!" to player
			stop
		if {-si-team::%{-host}%::*} is empty:
			add {-host} to {-si-team::%{-host}%::*}
		if {-si-team::%{-host}%::*} does not contain %player%:
			if size of {-si-team::%{-host}%::*} >= 4:
				send "&cОтряд уже полон (4/4)!" to player
				stop
			add %player% to {-si-team::%{-host}%::*}
		if {-si-active::%{-host}%} is 1:
			SiReconnect({-host}, player)
			stop
		set {-members} to join {-si-team::%{-host}%::*} with ", "
		send "&aВы в отряде: &e%{-members}%&a! Ожидайте старт от &f%{-host}%" to player
		send "&a%player% присоединился к отряду! Состав: &e%{-members}%" to {-host}
		play sound entity.player.levelup to {-host} with volume 0.5 pitch 1.4
		SiOpenMenu({-host})
		stop
	if arg-1 is "leave":
		SiLeaveLobby(player)
		stop
	SiOpenMenu(player)

# ==========================================================
#  Управление клавиатурой внутри экрана "Space Invaders"
# ==========================================================

on key a in screen "Space Invaders":
	set {-si-kleft::%player%} to 1

on key release a in screen "Space Invaders":
	set {-si-kleft::%player%} to 0

on key left in screen "Space Invaders":
	set {-si-kleft::%player%} to 1

on key release left in screen "Space Invaders":
	set {-si-kleft::%player%} to 0

on key d in screen "Space Invaders":
	set {-si-kright::%player%} to 1

on key release d in screen "Space Invaders":
	set {-si-kright::%player%} to 0

on key right in screen "Space Invaders":
	set {-si-kright::%player%} to 1

on key release right in screen "Space Invaders":
	set {-si-kright::%player%} to 0

on key space in screen "Space Invaders":
	set {-si-kfire::%player%} to 1

on key release space in screen "Space Invaders":
	set {-si-kfire::%player%} to 0

on key w in screen "Space Invaders":
	set {-si-kfire::%player%} to 1

on key release w in screen "Space Invaders":
	set {-si-kfire::%player%} to 0

on key up in screen "Space Invaders":
	set {-si-kfire::%player%} to 1

on key release up in screen "Space Invaders":
	set {-si-kfire::%player%} to 0

on key q in screen "Space Invaders":
	SiUseOverdrive(player)

on key p in screen "Space Invaders":
	SiTogglePause(player)

on key r in screen "Space Invaders":
	SiTryRestart(player)

# ==========================================================
#  Игровой цикл (20 тиков/сек)
# ==========================================================

every 1 tick:
	loop {-si-active::*}:
		set {-g} to %loop-index%
		if {-si-active::%{-g}%} is 1:
			SiTick({-g})

# ==========================================================
#  Меню и лобби (1-4 игрока)
# ==========================================================

function SiOpenMenu(p: player):
	if {si::best::%p%} is not set:
		set {si::best::%p%} to 0
	if {-si-team::%p%::*} is empty:
		add %p% to {-si-team::%p%::*}
	set {-cnt} to size of {-si-team::%p%::*}
	set {-members} to join {-si-team::%p%::*} with ", "
	open screen "Space Invaders Menu" to p:
		box at 0 0 size 320x215 color #070B19 gradient #111827 radius 8 anchor center layer 0
		box at 0 -86 size 316x34 color #0F172A gradient #1E293B radius 6 anchor center layer 1
		text "&b&l✦ SPACE INVADERS: CO-OP ✦" at 0 -91 anchor center shadow layer 3
		text "&710 уровней • Кооп до 4 игроков • Босс «Омега-X»" at 0 -78 anchor center layer 3

		box at 0 -30 size 292x54 color #0B1224 radius 5 anchor center layer 1
		text "&eОтряд (%{-cnt}%/4): &f%{-members}%" at 0 -42 anchor center shadow layer 3 id menu_team
		text "&7Пригласить друзей: &b/space coop <ник>   &7|   Зайти: &a/space join" at 0 -24 anchor center layer 3
		text "&7Ваш рекорд: &6%{si::best::%p%}% &7очков" at 0 -10 anchor center layer 3

		box at 0 26 size 292x44 color #0B1224 radius 5 anchor center layer 1
		text "&fУправление: &bA/D &7или &b←/→ &7— ход  |  &eSpace/W &7— огонь  |  &dQ &7— Нова-бомба" at 0 18 anchor center layer 3
		text "&7Цвета пилотов: &b■ P1   &a■ P2   &e■ P3   &d■ P4   &7|   &fP &7— пауза" at 0 34 anchor center layer 3

		button "&a&l▶ СТАРТ КАМПАНИИ" at -74 74 size 138x24 anchor center run SiStartMatch(player) keep open layer 4 tooltip "&aНачать с 1 уровня (соло или всем отрядом)"
		button "&6🏆 ТОП-5 ПИЛОТОВ" at 74 74 size 138x24 anchor center run SiShowTop(player) keep open layer 4 tooltip "&6Лучшие очки сервера"
		button "&eСбросить отряд" at -74 100 size 138x18 anchor center run SiResetLobby(player) keep open layer 4
		button "&cЗакрыть" at 74 100 size 138x18 anchor center close layer 4

function SiResetLobby(p: player):
	clear {-si-team::%p%::*}
	add %p% to {-si-team::%p%::*}
	send "&eОтряд сброшен." to p
	SiOpenMenu(p)

function SiLeaveLobby(p: player):
	if {-si-host::%p%} is set:
		SiOnLeave(p)
	clear {-si-team::%p%::*}
	send "&eВы покинули отряд Space Invaders." to p

function SiShowTop(p: player):
	send "&b&l═══ 🏆 ТОП SPACE INVADERS ═══" to p
	set {-shown} to 0
	loop {si::best::*}:
		set {-shown} to {-shown} + 1
		if {-shown} <= 8:
			send "&e%{-shown}%. &f%loop-index% &7— &6%loop-value% &7очков" to p
	if {-shown} is 0:
		send "&7Пока нет рекордов — стань первым!" to p

# ==========================================================
#  Запуск матча и построение экрана арены
# ==========================================================

function SiStartMatch(host: player):
	set {-g} to "%host%"
	if {-si-team::%{-g}%::*} is empty:
		add %host% to {-si-team::%{-g}%::*}

	# Очистка старых состояний комнаты
	clear {-si-online::%{-g}%::*}
	delete {-si-slot-p::%{-g}%::1}
	delete {-si-slot-p::%{-g}%::2}
	delete {-si-slot-p::%{-g}%::3}
	delete {-si-slot-p::%{-g}%::4}

	# Собираем онлайн-игроков отряда (до 4)
	set {-s} to 0
	loop {-si-team::%{-g}%::*}:
		set {-m} to %loop-value%
		if {-m} is online and {-s} < 4:
			set {-s} to {-s} + 1
			add {-m} to {-si-online::%{-g}%::*}
			set {-si-host::%{-m}%} to {-g}
			set {-si-slot::%{-m}%} to {-s}
			set {-si-slot-p::%{-g}%::%{-s}%} to {-m}
			set {-si-kleft::%{-m}%} to 0
			set {-si-kright::%{-m}%} to 0
			set {-si-kfire::%{-m}%} to 0
			delete {-si-reconn::%{-m}%}

	if {-s} is 0:
		stop

	set {-si-pcount::%{-g}%} to {-s}
	set {-si-active::%{-g}%} to 1
	set {-si-paused::%{-g}%} to 0
	set {-si-over::%{-g}%} to 0
	set {-si-level::%{-g}%} to 1
	set {-si-score::%{-g}%} to 0
	set {-si-lives::%{-g}%} to 3 + 2 * ({-s} - 1)
	set {-si-overdrive::%{-g}%} to 25
	set {-si-tick::%{-g}%} to 0
	set {-si-banner-t::%{-g}%} to 45
	set {-si-ufo-on::%{-g}%} to 0
	set {-si-ufo-cd::%{-g}%} to 240
	set {-si-boss-on::%{-g}%} to 0

	# Начальные позиции кораблей P1..P4
	loop 4 times:
		set {-i} to %loop-number%
		if {-i} <= {-s}:
			set {-si-palive::%{-g}%::%{-i}%} to 1
			set {-si-pinvul::%{-g}%::%{-i}%} to 30
			set {-si-presp::%{-g}%::%{-i}%} to 0
			set {-si-pcd::%{-g}%::%{-i}%} to 0
			set {-si-ppow::%{-g}%::%{-i}%} to 0
			set {-si-ppowt::%{-g}%::%{-i}%} to 0
			if {-s} is 1:
				set {-si-px::%{-g}%::%{-i}%} to 0
			else if {-s} is 2:
				if {-i} is 1:
					set {-si-px::%{-g}%::%{-i}%} to -45
				else:
					set {-si-px::%{-g}%::%{-i}%} to 45
			else if {-s} is 3:
				if {-i} is 1:
					set {-si-px::%{-g}%::%{-i}%} to -75
				else if {-i} is 2:
					set {-si-px::%{-g}%::%{-i}%} to 0
				else:
					set {-si-px::%{-g}%::%{-i}%} to 75
			else:
				if {-i} is 1:
					set {-si-px::%{-g}%::%{-i}%} to -96
				else if {-i} is 2:
					set {-si-px::%{-g}%::%{-i}%} to -32
				else if {-i} is 3:
					set {-si-px::%{-g}%::%{-i}%} to 32
				else:
					set {-si-px::%{-g}%::%{-i}%} to 96
		else:
			set {-si-palive::%{-g}%::%{-i}%} to 0
			set {-si-px::%{-g}%::%{-i}%} to 0

	# Очищаем пулы пуль и бонусов
	loop 12 times:
		set {-si-pb-on::%{-g}%::%loop-number%} to 0
	loop 10 times:
		set {-si-eb-on::%{-g}%::%loop-number%} to 0
	loop 2 times:
		set {-si-pu-on::%{-g}%::%loop-number%} to 0

	# Открываем экран всем участникам
	loop {-si-online::%{-g}%::*}:
		SiOpenGameScreen(%loop-value%, {-g})

	# Загружаем 1-й уровень
	SiSetupLevel({-g}, 1)
	play sound entity.ender_dragon.flap to {-si-online::%{-g}%::*} with volume 0.5 pitch 1.6

function SiOpenGameScreen(p: player, g: text):
	open screen "Space Invaders" to p:
		# Корпус аркадного экрана и звёздный фон
		box at 0 0 size 334x218 color #1E3A8A radius 8 anchor center layer 0
		box at 0 0 size 328x212 color #040712 gradient #0B1124 radius 6 anchor center layer 1
		box at 0 -94 size 324x20 color #0F172A radius 4 anchor center layer 2
		box at 0 96 size 324x16 color #0F172A radius 4 anchor center layer 2

		# Звёзды фона
		text "&8·" at -130 -60 anchor center layer 2
		text "&8✦" at -85 -35 anchor center layer 2
		text "&8·" at -40 -70 anchor center layer 2
		text "&8·" at 22 -50 anchor center layer 2
		text "&8✦" at 95 -65 anchor center layer 2
		text "&8·" at 135 -25 anchor center layer 2
		text "&8·" at -120 15 anchor center layer 2
		text "&8·" at 75 20 anchor center layer 2
		text "&8✦" at -25 32 anchor center layer 2
		text "&8·" at 125 45 anchor center layer 2

		# Верхний HUD
		text "&b&lLVL 1/10" at -116 -94 anchor center shadow layer 10 id hud_lvl
		text "&e0" at -38 -94 anchor center shadow layer 10 id hud_score
		text "&c♥♥♥" at 38 -94 anchor center shadow layer 10 id hud_lives
		text "&dQ:" at 86 -94 anchor center shadow layer 10 id hud_od_lbl
		bar "od_bar" value 25 max 100 at 124 -94 size 56x6 color #C084FC background #33000000 anchor center layer 10

		# Полоса здоровья босса (уровень 10)
		bar "boss_bar" value 100 max 100 at 0 -78 size 260x7 color #EF4444 background #55000000 anchor center layer 11 hidden
		text "&c&l☠ ДРЕДНОУТ ОМЕГА-X ☠" at 0 -78 anchor center shadow layer 12 id boss_hp_txt hidden

		# UFO (секретный корабль)
		text "&d&l🛸" at -160 -72 anchor center shadow layer 6 id ufo hidden

		# 4 разрушаемых бункера (щиты)
		box at -105 54 size 34x8 color #22C55E radius 2 anchor center layer 4 id sh_1
		box at -35 54 size 34x8 color #22C55E radius 2 anchor center layer 4 id sh_2
		box at 35 54 size 34x8 color #22C55E radius 2 anchor center layer 4 id sh_3
		box at 105 54 size 34x8 color #22C55E radius 2 anchor center layer 4 id sh_4
		text "&a6" at -105 54 anchor center layer 5 id sh_t_1
		text "&a6" at -35 54 anchor center layer 5 id sh_t_2
		text "&a6" at 35 54 anchor center layer 5 id sh_t_3
		text "&a6" at 105 54 anchor center layer 5 id sh_t_4

		# 4 корабля игроков (P1 Cyan, P2 Green, P3 Yellow, P4 Pink)
		box at 0 74 size 18x9 color #06B6D4 gradient #22D3EE radius 3 anchor center layer 7 id p1 hidden
		text "&b▲" at 0 67 anchor center shadow layer 8 id p1_t hidden
		box at 0 74 size 18x9 color #16A34A gradient #4ADE80 radius 3 anchor center layer 7 id p2 hidden
		text "&a▲" at 0 67 anchor center shadow layer 8 id p2_t hidden
		box at 0 74 size 18x9 color #D97706 gradient #FBBF24 radius 3 anchor center layer 7 id p3 hidden
		text "&e▲" at 0 67 anchor center shadow layer 8 id p3_t hidden
		box at 0 74 size 18x9 color #DB2777 gradient #F472B6 radius 3 anchor center layer 7 id p4 hidden
		text "&d▲" at 0 67 anchor center shadow layer 8 id p4_t hidden

		# 24 врага (пул en_1..en_24)
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_1 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_2 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_3 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_4 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_5 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_6 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_7 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_8 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_9 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_10 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_11 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_12 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_13 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_14 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_15 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_16 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_17 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_18 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_19 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_20 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_21 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_22 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_23 hidden
		text "&a👾" at 0 -50 anchor center shadow layer 6 id en_24 hidden

		# Босс 10-го уровня (Дредноут Омега-X)
		box at 0 -46 size 82x30 color #7F1D1D gradient #DC2626 radius 6 anchor center layer 5 id boss_body hidden
		box at 0 -46 size 26x14 color #A855F7 gradient #F43F5E radius 4 anchor center layer 6 id boss_core hidden
		text "&f&l☠ OMEGA-X ☠" at 0 -46 anchor center shadow layer 7 id boss_lbl hidden

		# 12 пуль игроков (пул pb_1..pb_12)
		box at 0 0 size 3x9 color #38BDF8 radius 1 anchor center layer 6 id pb_1 hidden
		box at 0 0 size 3x9 color #38BDF8 radius 1 anchor center layer 6 id pb_2 hidden
		box at 0 0 size 3x9 color #38BDF8 radius 1 anchor center layer 6 id pb_3 hidden
		box at 0 0 size 3x9 color #38BDF8 radius 1 anchor center layer 6 id pb_4 hidden
		box at 0 0 size 3x9 color #38BDF8 radius 1 anchor center layer 6 id pb_5 hidden
		box at 0 0 size 3x9 color #38BDF8 radius 1 anchor center layer 6 id pb_6 hidden
		box at 0 0 size 3x9 color #FDE047 radius 1 anchor center layer 6 id pb_7 hidden
		box at 0 0 size 3x9 color #FDE047 radius 1 anchor center layer 6 id pb_8 hidden
		box at 0 0 size 3x9 color #FDE047 radius 1 anchor center layer 6 id pb_9 hidden
		box at 0 0 size 3x9 color #4ADE80 radius 1 anchor center layer 6 id pb_10 hidden
		box at 0 0 size 3x9 color #4ADE80 radius 1 anchor center layer 6 id pb_11 hidden
		box at 0 0 size 3x9 color #F472B6 radius 1 anchor center layer 6 id pb_12 hidden

		# 10 вражеских снарядов (пул eb_1..eb_10)
		box at 0 0 size 4x9 color #F43F5E radius 1 anchor center layer 6 id eb_1 hidden
		box at 0 0 size 4x9 color #F43F5E radius 1 anchor center layer 6 id eb_2 hidden
		box at 0 0 size 4x9 color #F43F5E radius 1 anchor center layer 6 id eb_3 hidden
		box at 0 0 size 4x9 color #F43F5E radius 1 anchor center layer 6 id eb_4 hidden
		box at 0 0 size 4x9 color #F43F5E radius 1 anchor center layer 6 id eb_5 hidden
		box at 0 0 size 4x9 color #E879F9 radius 1 anchor center layer 6 id eb_6 hidden
		box at 0 0 size 4x9 color #E879F9 radius 1 anchor center layer 6 id eb_7 hidden
		box at 0 0 size 4x9 color #FB923C radius 1 anchor center layer 6 id eb_8 hidden
		box at 0 0 size 4x9 color #FB923C radius 1 anchor center layer 6 id eb_9 hidden
		box at 0 0 size 4x9 color #EF4444 radius 1 anchor center layer 6 id eb_10 hidden

		# 2 бонуса (пул pu_1..pu_2)
		text "&e&l⚡" at 0 0 anchor center shadow layer 7 id pu_1 hidden
		text "&b&l⇶" at 0 0 anchor center shadow layer 7 id pu_2 hidden

		# Нижний статус-бар
		text "&7A/D — ход | Space — огонь | Q — Нова | P — пауза" at 0 96 anchor center layer 10 id hud_bottom

		# Баннеры уровня / паузы / победы / поражения
		text "&b&lУРОВЕНЬ 1" at 0 -10 anchor center shadow layer 20 id banner_main hidden
		text "&7Приготовьтесь!" at 0 6 anchor center shadow layer 20 id banner_sub hidden

function SiReconnect(g: text, p: player):
	if {-si-active::%g%} is not 1:
		stop
	if {-si-online::%g%::*} does not contain %p%:
		add %p% to {-si-online::%g%::*}
	set {-si-host::%p%} to g
	set {-si-kleft::%p%} to 0
	set {-si-kright::%p%} to 0
	set {-si-kfire::%p%} to 0
	SiOpenGameScreen(p, g)
	SiSyncFullVisuals(g)
	send "&aВы вернулись в бой (Уровень %{-si-level::%g%}%/10)!" to p

function SiOnLeave(p: player):
	set {-si-kleft::%p%} to 0
	set {-si-kright::%p%} to 0
	set {-si-kfire::%p%} to 0
	if {-si-host::%p%} is not set:
		stop
	set {-g} to {-si-host::%p%}
	delete {-si-host::%p%}
	set {-s} to {-si-slot::%p%}
	remove %p% from {-si-online::%{-g}%::*}
	if {-si-active::%{-g}%} is 1:
		if {-s} is set:
			set {-si-palive::%{-g}%::%{-s}%} to 0
			hide screen "p%{-s}%" for {-si-online::%{-g}%::*}
			hide screen "p%{-s}%_t" for {-si-online::%{-g}%::*}
		if {-si-online::%{-g}%::*} is empty:
			set {-si-active::%{-g}%} to 0
			delete {-si-active::%{-g}%}
		else:
			set {-si-reconn::%p%} to {-g}
			send "&e[Space Invaders] &f%p% &7покинул бой. Вернуться: &a/space join" to {-si-online::%{-g}%::*}

# ==========================================================
#  Генерация 10 уровней + Босс на 10-м уровне
# ==========================================================

function SiSetupLevel(g: text, lvl: number):
	set {-si-level::%g%} to lvl
	set {-si-en-dir::%g%} to 1
	set {-si-en-step::%g%} to 0
	set {-si-en-fcd::%g%} to 25
	set {-si-boss-on::%g%} to 0

	# Сброс пуль и бонусов
	loop 12 times:
		set {-si-pb-on::%g%::%loop-number%} to 0
		hide screen "pb_%loop-number%" for {-si-online::%g%::*}
	loop 10 times:
		set {-si-eb-on::%g%::%loop-number%} to 0
		hide screen "eb_%loop-number%" for {-si-online::%g%::*}
	loop 2 times:
		set {-si-pu-on::%g%::%loop-number%} to 0
		hide screen "pu_%loop-number%" for {-si-online::%g%::*}

	# Скрыть всех врагов и босса
	loop 24 times:
		set {-si-en-on::%g%::%loop-number%} to 0
		hide screen "en_%loop-number%" for {-si-online::%g%::*}
	hide screen "boss_body" for {-si-online::%g%::*}
	hide screen "boss_core" for {-si-online::%g%::*}
	hide screen "boss_lbl" for {-si-online::%g%::*}
	hide screen "boss_bar" for {-si-online::%g%::*}
	hide screen "boss_hp_txt" for {-si-online::%g%::*}

	# Восстановление 4 щитов (бункеров)
	set {-shhp} to 6
	if lvl >= 6:
		set {-shhp} to 8
	set {-si-sh-x::%g%::1} to -105
	set {-si-sh-x::%g%::2} to -35
	set {-si-sh-x::%g%::3} to 35
	set {-si-sh-x::%g%::4} to 105
	loop 4 times:
		set {-k} to %loop-number%
		set {-si-sh-hp::%g%::%{-k}%} to {-shhp}
		show screen "sh_%{-k}%" for {-si-online::%g%::*}
		show screen "sh_t_%{-k}%" for {-si-online::%g%::*}
		set text of screen "sh_t_%{-k}%" for {-si-online::%g%::*} to "&a%{-shhp}%"

	# Бонус ХП в коопе (чтобы 2-4 игрокам было интересно)
	set {-pc} to {-si-pcount::%g%}
	set {-hpbonus} to 0
	if {-pc} >= 3:
		set {-hpbonus} to 1

	set {-lname} to "Разведка"
	if lvl is 1:
		set {-lname} to "Разведка"
		SiSpawnGrid(g, 2, 6, 1, 1, 1, 1, {-hpbonus})
	else if lvl is 2:
		set {-lname} to "Первая атака"
		SiSpawnGrid(g, 3, 6, 2, 1, 1, 1, {-hpbonus})
	else if lvl is 3:
		set {-lname} to "Стальная стена"
		SiSpawnGrid(g, 3, 6, 2, 1, 3, 1, {-hpbonus})
	else if lvl is 4:
		set {-lname} to "Снайперский сектор"
		SiSpawnGrid(g, 3, 6, 4, 2, 1, 1, {-hpbonus})
	else if lvl is 5:
		set {-lname} to "Блиц-вторжение"
		SiSpawnGrid(g, 4, 6, 2, 2, 1, 1, {-hpbonus})
	else if lvl is 6:
		set {-lname} to "Флот вторжения"
		SiSpawnGrid(g, 4, 6, 4, 2, 2, 3, {-hpbonus})
	else if lvl is 7:
		set {-lname} to "Элитный авангард"
		SiSpawnGrid(g, 4, 6, 5, 4, 2, 3, {-hpbonus})
	else if lvl is 8:
		set {-lname} to "Огненный шторм"
		SiSpawnGrid(g, 4, 6, 4, 4, 2, 3, {-hpbonus})
	else if lvl is 9:
		set {-lname} to "Стража Дредноута"
		SiSpawnGrid(g, 4, 6, 5, 4, 3, 3, {-hpbonus})
	else:
		set {-lname} to "БОСС: ОМЕГА-X"
		SiSpawnBoss(g)

	# Баннер начала уровня
	set {-si-banner-t::%g%} to 42
	set text of screen "banner_main" for {-si-online::%g%::*} to "&b&lУРОВЕНЬ %lvl% / 10"
	set text of screen "banner_sub" for {-si-online::%g%::*} to "&e%{-lname}%"
	show screen "banner_main" for {-si-online::%g%::*}
	show screen "banner_sub" for {-si-online::%g%::*}
	SiSyncFullVisuals(g)
	play sound block.note_block.chime to {-si-online::%g%::*} with volume 0.7 pitch 1.3

function SiSpawnGrid(g: text, rows: number, cols: number, r1: number, r2: number, r3: number, r4: number, hpbonus: number):
	set {-idx} to 0
	set {-alive} to 0
	loop rows times:
		set {-r} to %loop-number%
		set {-tp} to r1
		if {-r} is 2:
			set {-tp} to r2
		else if {-r} is 3:
			set {-tp} to r3
		else if {-r} is 4:
			set {-tp} to r4
		loop cols times:
			set {-c} to %loop-number%
			set {-idx} to {-idx} + 1
			if {-idx} <= 24:
				set {-ex} to -100 + ({-c} - 1) * 40
				set {-ey} to -58 + ({-r} - 1) * 20
				set {-si-en-on::%g%::%{-idx}%} to 1
				set {-si-en-x::%g%::%{-idx}%} to {-ex}
				set {-si-en-y::%g%::%{-idx}%} to {-ey}
				set {-si-en-t::%g%::%{-idx}%} to {-tp}
				set {-ehp} to 1
				if {-tp} is 2:
					set {-ehp} to 2
				else if {-tp} is 3:
					set {-ehp} to 4
				else if {-tp} is 4:
					set {-ehp} to 2
				else if {-tp} is 5:
					set {-ehp} to 5
				set {-si-en-hp::%g%::%{-idx}%} to {-ehp} + hpbonus
				set {-alive} to {-alive} + 1
				SiRefreshEnemyVisual(g, {-idx})
	set {-si-en-alive::%g%} to {-alive}

function SiRefreshEnemyVisual(g: text, idx: number):
	set {-tp} to {-si-en-t::%g%::%idx%}
	set {-hp} to {-si-en-hp::%g%::%idx%}
	set {-ex} to {-si-en-x::%g%::%idx%}
	set {-ey} to {-si-en-y::%g%::%idx%}
	set {-ico} to "&a👾"
	if {-tp} is 2:
		set {-ico} to "&e🛸"
	else if {-tp} is 3:
		set {-ico} to "&c🛡&7%{-hp}%"
	else if {-tp} is 4:
		set {-ico} to "&d⚡"
	else if {-tp} is 5:
		set {-ico} to "&6☄&f%{-hp}%"
	set text of screen "en_%idx%" for {-si-online::%g%::*} to {-ico}
	set position of screen "en_%idx%" for {-si-online::%g%::*} to {-ex} {-ey}
	show screen "en_%idx%" for {-si-online::%g%::*}

function SiSpawnBoss(g: text):
	set {-pc} to {-si-pcount::%g%}
	set {-maxhp} to 80 + 45 * {-pc}
	set {-si-boss-on::%g%} to 1
	set {-si-boss-x::%g%} to 0
	set {-si-boss-y::%g%} to -44
	set {-si-boss-dir::%g%} to 1
	set {-si-boss-hp::%g%} to {-maxhp}
	set {-si-boss-max::%g%} to {-maxhp}
	set {-si-boss-phase::%g%} to 1
	set {-si-boss-fcd::%g%} to 25
	set {-si-boss-scd::%g%} to 120
	set {-si-en-alive::%g%} to 0

	set max of screen bar "boss_bar" for {-si-online::%g%::*} to {-maxhp}
	set value of screen bar "boss_bar" for {-si-online::%g%::*} to {-maxhp}
	set text of screen "boss_hp_txt" for {-si-online::%g%::*} to "&c&l☠ OMEGA-X: %{-maxhp}% / %{-maxhp}% HP (ФАЗА 1)"
	show screen "boss_bar" for {-si-online::%g%::*}
	show screen "boss_hp_txt" for {-si-online::%g%::*}
	show screen "boss_body" for {-si-online::%g%::*}
	show screen "boss_core" for {-si-online::%g%::*}
	show screen "boss_lbl" for {-si-online::%g%::*}
	set position of screen "boss_body" for {-si-online::%g%::*} to 0 -44
	set position of screen "boss_core" for {-si-online::%g%::*} to 0 -44
	set position of screen "boss_lbl" for {-si-online::%g%::*} to 0 -44
	play sound entity.wither.spawn to {-si-online::%g%::*} with volume 0.7 pitch 0.9

function SiSyncFullVisuals(g: text):
	set {-pc} to {-si-pcount::%g%}
	loop 4 times:
		set {-s} to %loop-number%
		if {-s} <= {-pc} and {-si-palive::%g%::%{-s}%} is 1:
			set {-px} to {-si-px::%g%::%{-s}%}
			set position of screen "p%{-s}%" for {-si-online::%g%::*} to {-px} 74
			set position of screen "p%{-s}%_t" for {-si-online::%g%::*} to {-px} 67
			show screen "p%{-s}%" for {-si-online::%g%::*}
			show screen "p%{-s}%_t" for {-si-online::%g%::*}
		else:
			hide screen "p%{-s}%" for {-si-online::%g%::*}
			hide screen "p%{-s}%_t" for {-si-online::%g%::*}
	SiUpdateHud(g)

function SiUpdateHud(g: text):
	set {-lvl} to {-si-level::%g%}
	set {-sc} to {-si-score::%g%}
	set {-lv} to {-si-lives::%g%}
	set {-od} to {-si-overdrive::%g%}
	set text of screen "hud_lvl" for {-si-online::%g%::*} to "&b&lLVL %{-lvl}%/10"
	set text of screen "hud_score" for {-si-online::%g%::*} to "&e%{-sc}%"
	set text of screen "hud_lives" for {-si-online::%g%::*} to "&c♥ x%{-lv}%"
	set value of screen bar "od_bar" for {-si-online::%g%::*} to {-od}
	if {-od} >= 100:
		set text of screen "hud_od_lbl" for {-si-online::%g%::*} to "&d&lQ!"
	else:
		set text of screen "hud_od_lbl" for {-si-online::%g%::*} to "&7Q:"

# ==========================================================
#  Главный тик игры (20 FPS)
# ==========================================================

function SiTick(g: text):
	if {-si-online::%g%::*} is empty:
		set {-si-active::%g%} to 0
		delete {-si-active::%g%}
		stop
	if {-si-paused::%g%} is 1 or {-si-over::%g%} > 0:
		stop

	set {-si-tick::%g%} to {-si-tick::%g%} + 1

	# Скрытие баннера уровня
	if {-si-banner-t::%g%} > 0:
		set {-si-banner-t::%g%} to {-si-banner-t::%g%} - 1
		if {-si-banner-t::%g%} is 0:
			hide screen "banner_main" for {-si-online::%g%::*}
			hide screen "banner_sub" for {-si-online::%g%::*}

	# 1. Обновление всех 1..4 кораблей игроков
	SiTickPlayers(g)

	# 2. Пули игроков + попадания по врагам/боссу/UFO
	SiTickPlayerBullets(g)

	# 3. Бонусы (падение и подбор)
	SiTickPowerups(g)

	# 4. UFO
	SiTickUfo(g)

	# 5. Враги или Босс
	if {-si-boss-on::%g%} is 1:
		SiTickBoss(g)
	if {-si-en-alive::%g%} > 0:
		SiTickEnemies(g)

	# 6. Вражеские снаряды + попадания по щитам и игрокам
	SiTickEnemyBullets(g)

	# 7. Проверка прохождения уровня
	if {-si-over::%g%} is 0:
		if {-si-boss-on::%g%} is 0 and {-si-en-alive::%g%} <= 0:
			if {-si-level::%g%} < 10:
				set {-next} to {-si-level::%g%} + 1
				set {-si-score::%g%} to {-si-score::%g%} + 250 * {-si-level::%g%}
				SiSetupLevel(g, {-next})
			else:
				SiVictory(g)

function SiTickPlayers(g: text):
	set {-pc} to {-si-pcount::%g%}
	loop {-pc} times:
		set {-s} to %loop-number%
		set {-p} to {-si-slot-p::%g%::%{-s}%}
		if {-p} is set and {-si-online::%g%::*} contains {-p}:
			# Возрождение в коопе / после потери жизни
			if {-si-palive::%g%::%{-s}%} is 0:
				if {-si-presp::%g%::%{-s}%} > 0:
					set {-si-presp::%g%::%{-s}%} to {-si-presp::%g%::%{-s}%} - 1
					if {-si-presp::%g%::%{-s}%} is 0 and {-si-lives::%g%} > 0:
						set {-si-palive::%g%::%{-s}%} to 1
						set {-si-pinvul::%g%::%{-s}%} to 40
						set {-px} to {-si-px::%g%::%{-s}%}
						set position of screen "p%{-s}%" for {-si-online::%g%::*} to {-px} 74
						set position of screen "p%{-s}%_t" for {-si-online::%g%::*} to {-px} 67
						show screen "p%{-s}%" for {-si-online::%g%::*}
						show screen "p%{-s}%_t" for {-si-online::%g%::*}
						play sound entity.experience_orb.pickup to {-si-online::%g%::*} with volume 0.5 pitch 1.6
			else:
				if {-si-pinvul::%g%::%{-s}%} > 0:
					set {-si-pinvul::%g%::%{-s}%} to {-si-pinvul::%g%::%{-s}%} - 1

				if {-si-ppowt::%g%::%{-s}%} > 0:
					set {-si-ppowt::%g%::%{-s}%} to {-si-ppowt::%g%::%{-s}%} - 1
					if {-si-ppowt::%g%::%{-s}%} is 0:
						set {-si-ppow::%g%::%{-s}%} to 0

				if {-si-pcd::%g%::%{-s}%} > 0:
					set {-si-pcd::%g%::%{-s}%} to {-si-pcd::%g%::%{-s}%} - 1

				# Движение корабля
				set {-dx} to 0
				if {-si-kleft::%{-p}%} is 1:
					set {-dx} to {-dx} - 5
				if {-si-kright::%{-p}%} is 1:
					set {-dx} to {-dx} + 5
				if {-dx} is not 0:
					set {-nx} to {-si-px::%g%::%{-s}%} + {-dx}
					if {-nx} < -144:
						set {-nx} to -144
					if {-nx} > 144:
						set {-nx} to 144
					set {-si-px::%g%::%{-s}%} to {-nx}
					set position of screen "p%{-s}%" for {-si-online::%g%::*} to {-nx} 74
					set position of screen "p%{-s}%_t" for {-si-online::%g%::*} to {-nx} 67

				# Стрельба
				if {-si-kfire::%{-p}%} is 1 and {-si-pcd::%g%::%{-s}%} <= 0:
					set {-px} to {-si-px::%g%::%{-s}%}
					set {-pw} to {-si-ppow::%g%::%{-s}%}
					if {-pw} is 1:
						set {-si-pcd::%g%::%{-s}%} to 3
					else:
						set {-si-pcd::%g%::%{-s}%} to 6
					SiSpawnPlayerBullet(g, {-px}, 64, 0)
					if {-pw} is 2:
						SiSpawnPlayerBullet(g, {-px} - 5, 65, -2)
						SiSpawnPlayerBullet(g, {-px} + 5, 65, 2)
					play sound block.note_block.hat to {-p} with volume 0.35 pitch 1.8

function SiSpawnPlayerBullet(g: text, bx: number, by: number, bvx: number):
	loop 12 times:
		set {-i} to %loop-number%
		if {-si-pb-on::%g%::%{-i}%} is 0:
			set {-si-pb-on::%g%::%{-i}%} to 1
			set {-si-pb-x::%g%::%{-i}%} to bx
			set {-si-pb-y::%g%::%{-i}%} to by
			set {-si-pb-vx::%g%::%{-i}%} to bvx
			set position of screen "pb_%{-i}%" for {-si-online::%g%::*} to bx by
			show screen "pb_%{-i}%" for {-si-online::%g%::*}
			stop

function SiTickPlayerBullets(g: text):
	loop 12 times:
		set {-i} to %loop-number%
		if {-si-pb-on::%g%::%{-i}%} is 1:
			set {-bx} to {-si-pb-x::%g%::%{-i}%} + {-si-pb-vx::%g%::%{-i}%}
			set {-by} to {-si-pb-y::%g%::%{-i}%} - 9
			set {-si-pb-x::%g%::%{-i}%} to {-bx}
			set {-si-pb-y::%g%::%{-i}%} to {-by}

			if {-by} < -84 or {-bx} < -156 or {-bx} > 156:
				set {-si-pb-on::%g%::%{-i}%} to 0
				hide screen "pb_%{-i}%" for {-si-online::%g%::*}
			else:
				set {-hit} to 0

				# Попадание по UFO
				if {-si-ufo-on::%g%} is 1:
					set {-udx} to abs({-bx} - {-si-ufo-x::%g%})
					set {-udy} to abs({-by} + 72)
					if {-udx} <= 14 and {-udy} <= 10:
						set {-hit} to 1
						set {-si-ufo-on::%g%} to 0
						hide screen "ufo" for {-si-online::%g%::*}
						set {-si-score::%g%} to {-si-score::%g%} + 300
						SiAddOverdrive(g, 20)
						SiDropPowerup(g, {-si-ufo-x::%g%}, -70)
						gui particles "gold" count 10 at {-bx} {-by} for {-si-online::%g%::*}
						gui float "&6+300 UFO!" at {-bx} {-by} for {-si-online::%g%::*}
						play sound entity.firework_rocket.blast to {-si-online::%g%::*} with volume 0.5 pitch 1.4
						SiUpdateHud(g)

				# Попадание по Боссу (уровень 10)
				if {-hit} is 0 and {-si-boss-on::%g%} is 1:
					set {-bdx} to abs({-bx} - {-si-boss-x::%g%})
					set {-bdy} to abs({-by} - {-si-boss-y::%g%})
					if {-bdx} <= 42 and {-bdy} <= 18:
						set {-hit} to 1
						SiDamageBoss(g, 1, {-bx}, {-by})

				# Попадание по обычным врагам
				if {-hit} is 0 and {-si-en-alive::%g%} > 0:
					loop 24 times:
						if {-hit} is 0:
							set {-e} to %loop-number%
							if {-si-en-on::%g%::%{-e}%} is 1:
								set {-edx} to abs({-bx} - {-si-en-x::%g%::%{-e}%})
								set {-edy} to abs({-by} - {-si-en-y::%g%::%{-e}%})
								if {-edx} <= 11 and {-edy} <= 10:
									set {-hit} to 1
									SiDamageEnemy(g, {-e}, 1)

				if {-hit} is 1:
					set {-si-pb-on::%g%::%{-i}%} to 0
					hide screen "pb_%{-i}%" for {-si-online::%g%::*}
				else:
					set position of screen "pb_%{-i}%" for {-si-online::%g%::*} to {-bx} {-by}

function SiDamageEnemy(g: text, idx: number, dmg: number):
	set {-hp} to {-si-en-hp::%g%::%idx%} - dmg
	set {-si-en-hp::%g%::%idx%} to {-hp}
	set {-ex} to {-si-en-x::%g%::%idx%}
	set {-ey} to {-si-en-y::%g%::%idx%}
	if {-hp} <= 0:
		set {-si-en-on::%g%::%idx%} to 0
		set {-si-en-alive::%g%} to {-si-en-alive::%g%} - 1
		hide screen "en_%idx%" for {-si-online::%g%::*}
		set {-pts} to 50 * {-si-en-t::%g%::%idx%}
		set {-si-score::%g%} to {-si-score::%g%} + {-pts}
		SiAddOverdrive(g, 6)
		gui particles "explosion" count 6 at {-ex} {-ey} for {-si-online::%g%::*}
		play sound entity.generic.explode to {-si-online::%g%::*} with volume 0.25 pitch 1.7
		# 18% шанс выпадения бонуса
		set {-rnd} to random integer between 1 and 100
		if {-rnd} <= 18:
			SiDropPowerup(g, {-ex}, {-ey})
		SiUpdateHud(g)
	else:
		SiRefreshEnemyVisual(g, idx)
		gui particles "spark" count 3 at {-ex} {-ey} for {-si-online::%g%::*}
		play sound block.anvil.land to {-si-online::%g%::*} with volume 0.15 pitch 1.9

function SiDamageBoss(g: text, dmg: number, hx: number, hy: number):
	set {-hp} to {-si-boss-hp::%g%} - dmg
	set {-si-boss-hp::%g%} to {-hp}
	set {-max} to {-si-boss-max::%g%}
	set {-si-score::%g%} to {-si-score::%g%} + 25 * dmg
	SiAddOverdrive(g, 2)
	gui particles "spark" count 4 at hx hy for {-si-online::%g%::*}
	play sound entity.iron_golem.hurt to {-si-online::%g%::*} with volume 0.25 pitch 1.5

	if {-hp} <= 0:
		set {-si-boss-on::%g%} to 0
		set {-bx} to {-si-boss-x::%g%}
		set {-by} to {-si-boss-y::%g%}
		hide screen "boss_body" for {-si-online::%g%::*}
		hide screen "boss_core" for {-si-online::%g%::*}
		hide screen "boss_lbl" for {-si-online::%g%::*}
		hide screen "boss_bar" for {-si-online::%g%::*}
		hide screen "boss_hp_txt" for {-si-online::%g%::*}
		# Убираем всех оставшихся дронов босса
		loop 24 times:
			set {-k} to %loop-number%
			if {-si-en-on::%g%::%{-k}%} is 1:
				set {-si-en-on::%g%::%{-k}%} to 0
				hide screen "en_%{-k}%" for {-si-online::%g%::*}
		set {-si-en-alive::%g%} to 0
		gui particles "explosion" count 28 at {-bx} {-by} for {-si-online::%g%::*}
		play sound entity.ender_dragon.death to {-si-online::%g%::*} with volume 0.6 pitch 1.1
		set {-si-score::%g%} to {-si-score::%g%} + 2500
		SiVictory(g)
		stop

	# Смена фаз босса (66% и 33%)
	set {-pct} to ({-hp} * 100) / {-max}
	set {-oldp} to {-si-boss-phase::%g%}
	if {-pct} <= 33 and {-oldp} < 3:
		set {-si-boss-phase::%g%} to 3
		set text of screen "banner_main" for {-si-online::%g%::*} to "&c&l⚠ ФАЗА 3: ПЕРЕГРУЗКА! ⚠"
		set text of screen "banner_sub" for {-si-online::%g%::*} to "&eОмега-X переходит в ярость!"
		show screen "banner_main" for {-si-online::%g%::*}
		show screen "banner_sub" for {-si-online::%g%::*}
		set {-si-banner-t::%g%} to 36
		SiDropPowerup(g, {-si-boss-x::%g%}, -20)
		play sound entity.wither.ambient to {-si-online::%g%::*} with volume 0.7 pitch 0.8
	else if {-pct} <= 66 and {-oldp} < 2:
		set {-si-boss-phase::%g%} to 2
		set text of screen "banner_main" for {-si-online::%g%::*} to "&6&l⚠ ФАЗА 2: ШКВАЛ ОГНЯ! ⚠"
		set text of screen "banner_sub" for {-si-online::%g%::*} to "&eУклоняйтесь от веерных залпов!"
		show screen "banner_main" for {-si-online::%g%::*}
		show screen "banner_sub" for {-si-online::%g%::*}
		set {-si-banner-t::%g%} to 36
		SiDropPowerup(g, {-si-boss-x::%g%}, -20)
		play sound entity.wither.ambient to {-si-online::%g%::*} with volume 0.6 pitch 1.1

	set {-ph} to {-si-boss-phase::%g%}
	set value of screen bar "boss_bar" for {-si-online::%g%::*} to {-hp}
	set text of screen "boss_hp_txt" for {-si-online::%g%::*} to "&c&l☠ OMEGA-X: %{-hp}% / %{-max}% HP (ФАЗА %{-ph}%)"
	SiUpdateHud(g)

# ==========================================================
#  Движение и стрельба врагов
# ==========================================================

function SiTickEnemies(g: text):
	set {-lvl} to {-si-level::%g%}
	set {-alive} to {-si-en-alive::%g%}
	set {-si-en-step::%g%} to {-si-en-step::%g%} + 1

	# Чем меньше врагов и выше уровень — тем быстрее марш
	set {-interval} to 10 - floor({-lvl} / 2)
	if {-alive} <= 6:
		set {-interval} to {-interval} - 2
	if {-alive} <= 2:
		set {-interval} to 2
	if {-interval} < 2:
		set {-interval} to 2

	if {-si-en-step::%g%} >= {-interval}:
		set {-si-en-step::%g%} to 0
		set {-dir} to {-si-en-dir::%g%}
		set {-spd} to 5
		if {-lvl} >= 5:
			set {-spd} to 6
		if {-lvl} >= 8:
			set {-spd} to 7

		# Проверяем достижение границ экрана
		set {-edge} to 0
		loop 24 times:
			set {-i} to %loop-number%
			if {-si-en-on::%g%::%{-i}%} is 1:
				set {-nx} to {-si-en-x::%g%::%{-i}%} + {-dir} * {-spd}
				if {-nx} <= -142 or {-nx} >= 142:
					set {-edge} to 1

		if {-edge} is 1:
			set {-si-en-dir::%g%} to 0 - {-dir}
			loop 24 times:
				set {-i} to %loop-number%
				if {-si-en-on::%g%::%{-i}%} is 1:
					set {-ny} to {-si-en-y::%g%::%{-i}%} + 7
					set {-si-en-y::%g%::%{-i}%} to {-ny}
					set {-ex} to {-si-en-x::%g%::%{-i}%}
					set position of screen "en_%{-i}%" for {-si-online::%g%::*} to {-ex} {-ny}
					if {-ny} >= 64:
						SiGameOver(g, "Пришельцы прорвали оборону!")
						stop
		else:
			loop 24 times:
				set {-i} to %loop-number%
				if {-si-en-on::%g%::%{-i}%} is 1:
					set {-nx} to {-si-en-x::%g%::%{-i}%} + {-dir} * {-spd}
					set {-si-en-x::%g%::%{-i}%} to {-nx}
					set {-ey} to {-si-en-y::%g%::%{-i}%}
					set position of screen "en_%{-i}%" for {-si-online::%g%::*} to {-nx} {-ey}

	# Стрельба врагов
	set {-si-en-fcd::%g%} to {-si-en-fcd::%g%} - 1
	if {-si-en-fcd::%g%} <= 0:
		set {-cd} to 24 - {-lvl} * 2
		if {-cd} < 7:
			set {-cd} to 7
		set {-si-en-fcd::%g%} to {-cd}
		# Выбираем случайного живого врага
		set {-pick} to random integer between 1 and 24
		set {-fired} to 0
		loop 24 times:
			if {-fired} is 0:
				set {-k} to {-pick} + %loop-number% - 1
				if {-k} > 24:
					set {-k} to {-k} - 24
				if {-si-en-on::%g%::%{-k}%} is 1:
					set {-fired} to 1
					set {-sx} to {-si-en-x::%g%::%{-k}%}
					set {-sy} to {-si-en-y::%g%::%{-k}%} + 8
					set {-tp} to {-si-en-t::%g%::%{-k}%}
					if {-tp} is 4:
						# Снайпер целится в первого живого игрока
						SiFindTargetX(g)
						set {-vx} to clamp(round(({-si-tx} - {-sx}) / 28), -3, 3)
						SiSpawnEnemyBullet(g, {-sx}, {-sy}, {-vx}, 5)
					else if {-tp} is 5:
						# Авианосец стреляет двойным залпом
						SiSpawnEnemyBullet(g, {-sx} - 6, {-sy}, -1, 4)
						SiSpawnEnemyBullet(g, {-sx} + 6, {-sy}, 1, 4)
					else:
						SiSpawnEnemyBullet(g, {-sx}, {-sy}, 0, 4)

# ==========================================================
#  ИИ Босса 10-го уровня: Дредноут «Омега-X»
# ==========================================================

function SiTickBoss(g: text):
	set {-ph} to {-si-boss-phase::%g%}
	set {-spd} to 2
	if {-ph} is 2:
		set {-spd} to 3
	else if {-ph} is 3:
		set {-spd} to 5

	set {-bx} to {-si-boss-x::%g%} + {-si-boss-dir::%g%} * {-spd}
	if {-bx} >= 102:
		set {-bx} to 102
		set {-si-boss-dir::%g%} to -1
	else if {-bx} <= -102:
		set {-bx} to -102
		set {-si-boss-dir::%g%} to 1
	set {-si-boss-x::%g%} to {-bx}
	set {-by} to {-si-boss-y::%g%}

	set position of screen "boss_body" for {-si-online::%g%::*} to {-bx} {-by}
	set position of screen "boss_core" for {-si-online::%g%::*} to {-bx} {-by}
	set position of screen "boss_lbl" for {-si-online::%g%::*} to {-bx} {-by}

	# Атаки босса
	set {-si-boss-fcd::%g%} to {-si-boss-fcd::%g%} - 1
	if {-si-boss-fcd::%g%} <= 0:
		if {-ph} is 1:
			set {-si-boss-fcd::%g%} to 22
			SiSpawnEnemyBullet(g, {-bx} - 22, {-by} + 16, -1, 4)
			SiSpawnEnemyBullet(g, {-bx}, {-by} + 18, 0, 5)
			SiSpawnEnemyBullet(g, {-bx} + 22, {-by} + 16, 1, 4)
		else if {-ph} is 2:
			set {-si-boss-fcd::%g%} to 18
			SiSpawnEnemyBullet(g, {-bx} - 28, {-by} + 16, -2, 4)
			SiSpawnEnemyBullet(g, {-bx} - 14, {-by} + 17, -1, 5)
			SiSpawnEnemyBullet(g, {-bx}, {-by} + 18, 0, 5)
			SiSpawnEnemyBullet(g, {-bx} + 14, {-by} + 17, 1, 5)
			SiSpawnEnemyBullet(g, {-bx} + 28, {-by} + 16, 2, 4)
		else:
			set {-si-boss-fcd::%g%} to 13
			SiFindTargetX(g)
			set {-vx} to clamp(round(({-si-tx} - {-bx}) / 24), -3, 3)
			SiSpawnEnemyBullet(g, {-bx}, {-by} + 18, {-vx}, 6)
			SiSpawnEnemyBullet(g, {-bx} - 26, {-by} + 16, -2, 5)
			SiSpawnEnemyBullet(g, {-bx} + 26, {-by} + 16, 2, 5)
		play sound entity.blaze.shoot to {-si-online::%g%::*} with volume 0.35 pitch 1.2

	# Призыв дронов-эскортов босса
	set {-si-boss-scd::%g%} to {-si-boss-scd::%g%} - 1
	if {-si-boss-scd::%g%} <= 0:
		set {-si-boss-scd::%g%} to 150 - {-ph} * 20
		if {-si-en-alive::%g%} < 4:
			SiSpawnBossDrone(g, {-bx} - 30, {-by} + 22)
			SiSpawnBossDrone(g, {-bx} + 30, {-by} + 22)

function SiSpawnBossDrone(g: text, dx: number, dy: number):
	loop 24 times:
		set {-i} to %loop-number%
		if {-si-en-on::%g%::%{-i}%} is 0:
			set {-si-en-on::%g%::%{-i}%} to 1
			set {-si-en-x::%g%::%{-i}%} to clamp(dx, -130, 130)
			set {-si-en-y::%g%::%{-i}%} to dy
			set {-si-en-t::%g%::%{-i}%} to 2
			set {-si-en-hp::%g%::%{-i}%} to 2
			set {-si-en-alive::%g%} to {-si-en-alive::%g%} + 1
			SiRefreshEnemyVisual(g, {-i})
			stop

# ==========================================================
#  Вражеские снаряды, щиты и урон игрокам
# ==========================================================

function SiSpawnEnemyBullet(g: text, bx: number, by: number, bvx: number, bvy: number):
	loop 10 times:
		set {-i} to %loop-number%
		if {-si-eb-on::%g%::%{-i}%} is 0:
			set {-si-eb-on::%g%::%{-i}%} to 1
			set {-si-eb-x::%g%::%{-i}%} to bx
			set {-si-eb-y::%g%::%{-i}%} to by
			set {-si-eb-vx::%g%::%{-i}%} to bvx
			set {-si-eb-vy::%g%::%{-i}%} to bvy
			set position of screen "eb_%{-i}%" for {-si-online::%g%::*} to bx by
			show screen "eb_%{-i}%" for {-si-online::%g%::*}
			stop

function SiTickEnemyBullets(g: text):
	loop 10 times:
		set {-i} to %loop-number%
		if {-si-eb-on::%g%::%{-i}%} is 1:
			set {-bx} to {-si-eb-x::%g%::%{-i}%} + {-si-eb-vx::%g%::%{-i}%}
			set {-by} to {-si-eb-y::%g%::%{-i}%} + {-si-eb-vy::%g%::%{-i}%}
			set {-si-eb-x::%g%::%{-i}%} to {-bx}
			set {-si-eb-y::%g%::%{-i}%} to {-by}

			if {-by} > 84 or {-bx} < -156 or {-bx} > 156:
				set {-si-eb-on::%g%::%{-i}%} to 0
				hide screen "eb_%{-i}%" for {-si-online::%g%::*}
			else:
				set {-hit} to 0

				# Попадание по бункерам (щитам)
				if {-by} >= 48 and {-by} <= 60:
					loop 4 times:
						if {-hit} is 0:
							set {-k} to %loop-number%
							if {-si-sh-hp::%g%::%{-k}%} > 0:
								set {-sdx} to abs({-bx} - {-si-sh-x::%g%::%{-k}%})
								if {-sdx} <= 18:
									set {-hit} to 1
									set {-nhp} to {-si-sh-hp::%g%::%{-k}%} - 1
									set {-si-sh-hp::%g%::%{-k}%} to {-nhp}
									if {-nhp} <= 0:
										hide screen "sh_%{-k}%" for {-si-online::%g%::*}
										hide screen "sh_t_%{-k}%" for {-si-online::%g%::*}
										gui particles "smoke" count 6 at {-bx} 54 for {-si-online::%g%::*}
									else if {-nhp} <= 2:
										set text of screen "sh_t_%{-k}%" for {-si-online::%g%::*} to "&c%{-nhp}%"
									else if {-nhp} <= 4:
										set text of screen "sh_t_%{-k}%" for {-si-online::%g%::*} to "&e%{-nhp}%"
									else:
										set text of screen "sh_t_%{-k}%" for {-si-online::%g%::*} to "&a%{-nhp}%"

				# Попадание по кораблям игроков
				if {-hit} is 0 and {-by} >= 66 and {-by} <= 82:
					set {-pc} to {-si-pcount::%g%}
					loop {-pc} times:
						if {-hit} is 0:
							set {-s} to %loop-number%
							if {-si-palive::%g%::%{-s}%} is 1 and {-si-pinvul::%g%::%{-s}%} <= 0:
								set {-pdx} to abs({-bx} - {-si-px::%g%::%{-s}%})
								if {-pdx} <= 11:
									set {-hit} to 1
									SiHitPlayer(g, {-s})

				if {-hit} is 1:
					set {-si-eb-on::%g%::%{-i}%} to 0
					hide screen "eb_%{-i}%" for {-si-online::%g%::*}
				else:
					set position of screen "eb_%{-i}%" for {-si-online::%g%::*} to {-bx} {-by}

function SiHitPlayer(g: text, s: number):
	set {-px} to {-si-px::%g%::%s%}
	set {-si-lives::%g%} to {-si-lives::%g%} - 1
	set {-si-ppow::%g%::%s%} to 0
	set {-si-ppowt::%g%::%s%} to 0
	gui particles "explosion" count 12 at {-px} 74 for {-si-online::%g%::*}
	play sound entity.player.hurt to {-si-online::%g%::*} with volume 0.7 pitch 0.9
	SiUpdateHud(g)

	if {-si-lives::%g%} <= 0:
		set {-si-palive::%g%::%s%} to 0
		hide screen "p%s%" for {-si-online::%g%::*}
		hide screen "p%s%_t" for {-si-online::%g%::*}
		# Проверяем, остался ли кто-то живой в отряде
		set {-any} to 0
		set {-pc} to {-si-pcount::%g%}
		loop {-pc} times:
			if {-si-palive::%g%::%loop-number%} is 1:
				set {-any} to 1
		if {-any} is 0:
			SiGameOver(g, "Все корабли отряда уничтожены!")
	else:
		# Временный респавн 2.5 сек (или неуязвимость в соло)
		if {-si-pcount::%g%} is 1:
			set {-si-pinvul::%g%::%s%} to 40
			gui float "&c-1 ♥" at {-px} 64 for {-si-online::%g%::*}
		else:
			set {-si-palive::%g%::%s%} to 0
			set {-si-presp::%g%::%s%} to 45
			hide screen "p%s%" for {-si-online::%g%::*}
			hide screen "p%s%_t" for {-si-online::%g%::*}
			gui float "&cПОДБИТ! Респавн..." at {-px} 64 for {-si-online::%g%::*}

# ==========================================================
#  Бонусы, UFO и Нова-бомба (Overdrive)
# ==========================================================

function SiDropPowerup(g: text, px: number, py: number):
	loop 2 times:
		set {-i} to %loop-number%
		if {-si-pu-on::%g%::%{-i}%} is 0:
			set {-t} to random integer between 1 and 4
			set {-si-pu-on::%g%::%{-i}%} to 1
			set {-si-pu-x::%g%::%{-i}%} to px
			set {-si-pu-y::%g%::%{-i}%} to py
			set {-si-pu-t::%g%::%{-i}%} to {-t}
			set {-lbl} to "&e&l⚡"
			if {-t} is 2:
				set {-lbl} to "&b&l⇶"
			else if {-t} is 3:
				set {-lbl} to "&a&l♥"
			else if {-t} is 4:
				set {-lbl} to "&d&l★"
			set text of screen "pu_%{-i}%" for {-si-online::%g%::*} to {-lbl}
			set position of screen "pu_%{-i}%" for {-si-online::%g%::*} to px py
			show screen "pu_%{-i}%" for {-si-online::%g%::*}
			stop

function SiTickPowerups(g: text):
	loop 2 times:
		set {-i} to %loop-number%
		if {-si-pu-on::%g%::%{-i}%} is 1:
			set {-px} to {-si-pu-x::%g%::%{-i}%}
			set {-py} to {-si-pu-y::%g%::%{-i}%} + 3
			set {-si-pu-y::%g%::%{-i}%} to {-py}
			if {-py} > 84:
				set {-si-pu-on::%g%::%{-i}%} to 0
				hide screen "pu_%{-i}%" for {-si-online::%g%::*}
			else:
				set {-got} to 0
				if {-py} >= 64 and {-py} <= 82:
					set {-pc} to {-si-pcount::%g%}
					loop {-pc} times:
						if {-got} is 0:
							set {-s} to %loop-number%
							if {-si-palive::%g%::%{-s}%} is 1:
								if abs({-px} - {-si-px::%g%::%{-s}%}) <= 14:
									set {-got} to 1
									set {-t} to {-si-pu-t::%g%::%{-i}%}
									if {-t} is 1:
										set {-si-ppow::%g%::%{-s}%} to 1
										set {-si-ppowt::%g%::%{-s}%} to 160
										gui float "&e⚡ RAPID FIRE!" at {-px} 62 for {-si-online::%g%::*}
									else if {-t} is 2:
										set {-si-ppow::%g%::%{-s}%} to 2
										set {-si-ppowt::%g%::%{-s}%} to 160
										gui float "&b⇶ TRIPLE LASER!" at {-px} 62 for {-si-online::%g%::*}
									else if {-t} is 3:
										set {-si-lives::%g%} to {-si-lives::%g%} + 1
										gui float "&a♥ +1 ЖИЗНЬ!" at {-px} 62 for {-si-online::%g%::*}
									else:
										SiAddOverdrive(g, 50)
										gui float "&d★ +50% НОВА!" at {-px} 62 for {-si-online::%g%::*}
									play sound entity.experience_orb.pickup to {-si-online::%g%::*} with volume 0.7 pitch 1.6
									SiUpdateHud(g)

				if {-got} is 1:
					set {-si-pu-on::%g%::%{-i}%} to 0
					hide screen "pu_%{-i}%" for {-si-online::%g%::*}
				else:
					set position of screen "pu_%{-i}%" for {-si-online::%g%::*} to {-px} {-py}

function SiTickUfo(g: text):
	if {-si-boss-on::%g%} is 1:
		stop
	if {-si-ufo-on::%g%} is 1:
		set {-ux} to {-si-ufo-x::%g%} + {-si-ufo-dir::%g%} * 3
		set {-si-ufo-x::%g%} to {-ux}
		if {-ux} < -160 or {-ux} > 160:
			set {-si-ufo-on::%g%} to 0
			hide screen "ufo" for {-si-online::%g%::*}
		else:
			set position of screen "ufo" for {-si-online::%g%::*} to {-ux} -72
	else:
		set {-si-ufo-cd::%g%} to {-si-ufo-cd::%g%} - 1
		if {-si-ufo-cd::%g%} <= 0:
			set {-si-ufo-cd::%g%} to random integer between 260 and 420
			set {-si-ufo-on::%g%} to 1
			set {-si-ufo-x::%g%} to -152
			set {-si-ufo-dir::%g%} to 1
			set position of screen "ufo" for {-si-online::%g%::*} to -152 -72
			show screen "ufo" for {-si-online::%g%::*}

function SiAddOverdrive(g: text, amt: number):
	set {-od} to {-si-overdrive::%g%} + amt
	if {-od} > 100:
		set {-od} to 100
	set {-si-overdrive::%g%} to {-od}

function SiUseOverdrive(p: player):
	if {-si-host::%p%} is not set:
		stop
	set {-g} to {-si-host::%p%}
	if {-si-active::%{-g}%} is not 1 or {-si-paused::%{-g}%} is 1 or {-si-over::%{-g}%} > 0:
		stop
	if {-si-overdrive::%{-g}%} < 100:
		stop
	set {-si-overdrive::%{-g}%} to 0
	# Уничтожаем все вражеские пули
	loop 10 times:
		set {-si-eb-on::%{-g}%::%loop-number%} to 0
		hide screen "eb_%loop-number%" for {-si-online::%{-g}%::*}
	# Наносим 2 урона всем врагам и 12 урона боссу
	loop 24 times:
		set {-i} to %loop-number%
		if {-si-en-on::%{-g}%::%{-i}%} is 1:
			SiDamageEnemy({-g}, {-i}, 2)
	if {-si-boss-on::%{-g}%} is 1:
		SiDamageBoss({-g}, 12, {-si-boss-x::%{-g}%}, {-si-boss-y::%{-g}%})
	gui particles "magic" count 24 at 0 0 for {-si-online::%{-g}%::*}
	gui float "&d&l✦ НОВА-БОМБА! ✦" at 0 0 for {-si-online::%{-g}%::*}
	play sound entity.generic.explode to {-si-online::%{-g}%::*} with volume 0.7 pitch 0.7
	SiUpdateHud({-g})

# ==========================================================
#  Вспомогательные функции, пауза, победа и поражение
# ==========================================================

function SiFindTargetX(g: text):
	set {-si-tx} to 0
	set {-found} to 0
	set {-pc} to {-si-pcount::%g%}
	loop {-pc} times:
		if {-found} is 0 and {-si-palive::%g%::%loop-number%} is 1:
			set {-si-tx} to {-si-px::%g%::%loop-number%}
			set {-found} to 1

function SiTogglePause(p: player):
	if {-si-host::%p%} is not set:
		stop
	set {-g} to {-si-host::%p%}
	if {-si-active::%{-g}%} is not 1 or {-si-over::%{-g}%} > 0:
		stop
	if {-si-paused::%{-g}%} is 1:
		set {-si-paused::%{-g}%} to 0
		hide screen "banner_main" for {-si-online::%{-g}%::*}
		hide screen "banner_sub" for {-si-online::%{-g}%::*}
	else:
		set {-si-paused::%{-g}%} to 1
		set text of screen "banner_main" for {-si-online::%{-g}%::*} to "&e&lПАУЗА"
		set text of screen "banner_sub" for {-si-online::%{-g}%::*} to "&7Нажмите &fP &7для продолжения"
		show screen "banner_main" for {-si-online::%{-g}%::*}
		show screen "banner_sub" for {-si-online::%{-g}%::*}

function SiTryRestart(p: player):
	if {-si-host::%p%} is not set:
		stop
	set {-g} to {-si-host::%p%}
	if {-si-over::%{-g}%} > 0:
		if "%p%" is {-g}:
			SiStartMatch(p)
		else:
			send "&eТолько хост &f%{-g}% &eможет перезапустить матч (клавиша R)." to p

function SiSaveRecords(g: text):
	set {-sc} to {-si-score::%g%}
	loop {-si-online::%g%::*}:
		set {-m} to %loop-value%
		if {si::best::%{-m}%} is not set:
			set {si::best::%{-m}%} to 0
		if {-sc} > {si::best::%{-m}%}:
			set {si::best::%{-m}%} to {-sc}

function SiGameOver(g: text, reason: text):
	set {-si-over::%g%} to 1
	SiSaveRecords(g)
	set text of screen "banner_main" for {-si-online::%g%::*} to "&c&lGAME OVER"
	set text of screen "banner_sub" for {-si-online::%g%::*} to "&7%reason%  &8|  &eСчёт: %{-si-score::%g%}%  &7(R — рестарт)"
	show screen "banner_main" for {-si-online::%g%::*}
	show screen "banner_sub" for {-si-online::%g%::*}
	play sound entity.wither.death to {-si-online::%g%::*} with volume 0.5 pitch 0.8

function SiVictory(g: text):
	set {-si-over::%g%} to 2
	SiSaveRecords(g)
	set text of screen "banner_main" for {-si-online::%g%::*} to "&a&l★ ПОБЕДА! ГАЛАКТИКА СПАСЕНА! ★"
	set text of screen "banner_sub" for {-si-online::%g%::*} to "&6Финальный счёт: %{-si-score::%g%}%  &7(R — новая игра)"
	show screen "banner_main" for {-si-online::%g%::*}
	show screen "banner_sub" for {-si-online::%g%::*}
	play sound ui.toast.challenge_complete to {-si-online::%g%::*} with volume 0.8 pitch 1.1
