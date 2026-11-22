# ==========================================================
#  td.sc — Tower Defense Ultra HD v3 (оптимизирован под ScriptCraft 0.8.3!)
#          3 HD-карты (448x224) | Кооп до 4 игроков + реконнект
#          60 FPS клиентский Tween (мобы, HP-бары, снаряды) + GUI-частицы
#          Скруглённые стеклянные панели (box radius/shadow/blur) + {list::*} цели
#  Команды:
#    /td               — меню карты, сложности и команды
#    /td coop <игрок>  — пригласить друга в кооп (до 4 игроков!)
#    /td join          — войти в команду или переподключиться в кооп
#  Управление:
#    1..8  — выбрать башню | Навести на башню — живой тултип
#    ЛКМ по траве — построить | ЛКМ по башне — радиус и апгрейд
#    X     — продать (70%) | Space — волна / скорость x1 <-> x2
#    R     — рестарт | M — меню
#  Текстуры: распакуйте td-resources.zip в scriptcraft/resources/
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
			send "&eИспользование: &f/td coop <ник_игрока> &7(можно до 4 игроков!)" to player
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

on key 7 in screen "Tower Defense":
	TdSelectTool(player, 7)

on key 8 in screen "Tower Defense":
	TdSelectTool(player, 8)

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

# --- Игровой цикл (x1 = 5 тиков/сек + 4-tick 60 FPS tween; x2 = 10 тиков/сек + 2-tick 60 FPS tween) ---

every 2 ticks:
	loop all players:
		if {-td-active::%loop-player%} is 1:
			# На скорости x1 тикаем через раз (каждые 4 тика), на x2 — каждые 2 тика!
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
	set {-pcnt} to size of {-td-team::%{-g}%::*}
	if {-pcnt} <= 1:
		delete {-td-active::%{-g}%}
		delete {-td-over::%{-g}%}
		delete {-td-host::%p%}
		clear {-td-team::%{-g}%::*}
		clear {-td-online::%{-g}%::*}
		stop
	if {-td-active::%{-g}%} is 1 and {-td-over::%{-g}%} is 0:
		set {-td-reconn::%p%} to {-g}
		if p is online:
			send "&e[TD Кооп] &7Вы закрыли игру. Вернуться обратно с сохранением золота: &a/td join" to p
		TdSyncStatus({-g}, "&e%p% временно вышел &7(может вернуться через /td join)")

function TdReconnect(g: player, p: player):
	set {-m} to {-td-map::%g%}
	if {-td-team::%g%::*} does not contain %p%:
		add %p% to {-td-team::%g%::*}
	if {-td-online::%g%::*} does not contain %p%:
		add %p% to {-td-online::%g%::*}
	set {-td-host::%p%} to %g%
	if {-td-gold::%p%} is not set:
		set {-td-gold::%p%} to 120
	set {-td-tool::%p%} to 1
	set {-td-sell::%p%} to 0
	if {-m} is 1:
		TdOpenMap1(p)
	else if {-m} is 2:
		TdOpenMap2(p)
	else:
		TdOpenMap3(p)
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
			set {-hy} to {-ey} - 13
			if {-td-eboss::%g%::%{-ei}%} is 1:
				set {-hy} to {-ey} - 19
				set max of screen bar "boss_bar" for p to {-td-emhp::%g%::%{-ei}%}
				set value of screen bar "boss_bar" for p to {-td-ehp::%g%::%{-ei}%}
				show screen element "boss_bar" to p
			set max of screen bar "ehp%{-ei}%" for p to {-td-emhp::%g%::%{-ei}%}
			set value of screen bar "ehp%{-ei}%" for p to {-td-ehp::%g%::%{-ei}%}
			set position of screen element "ehp%{-ei}%" for p to {-ex} {-hy}
			show screen element "ehp%{-ei}%" to p
	TdSyncHud(g)
	TdSyncStatus(g, "&a%p% вернулся в кооп-игру!")
	play sound entity.player.levelup to p with volume 0.5 pitch 1.3

# ==========================================================
#  ХЕЛПЕРЫ СИНХРОНИЗАЦИИ (ПРЯМОЙ ТАРГЕТ {-td-online::%g%::*} БЕЗ LOOP!)
# ==========================================================

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

function TdSyncCell(g: player, col: integer, row: integer, cx: number, cy: number, tex: text, tip: text):
	update screen "Tower Defense" for {-td-online::%g%::*}:
		image "td:textures/gui/tile_build.png" at 0 0 size 28x28 texture 28x28 anchor center layer 3 id "c%col%_%row%" run TdCellClick(player, %col%, %row%) keep open tooltip "%tip%"
	set image of screen element "c%col%_%row%" for {-td-online::%g%::*} to tex
	set position of screen element "c%col%_%row%" for {-td-online::%g%::*} to cx cy

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
	set {-pcnt} to size of {-td-team::%g%::*}
	set {-names} to join {-td-team::%g%::*} with ", "
	set {-tstr} to "&7Соло &7[%{-sp}%&7]"
	if {-pcnt} > 1:
		set {-tstr} to "&dКооп(%{-pcnt}%): &f%{-names}% &7[%{-sp}%&7]"
	set text of screen element "hud_wave" for {-td-online::%g%::*} to "&f%{-td-wave::%g%}%/%{-td-maxw::%g%}%"
	set text of screen element "hud_hp" for {-td-online::%g%::*} to "&c%{-td-hp::%g%}%"
	set text of screen element "hud_score" for {-td-online::%g%::*} to "&a%{-td-score::%g%}%"
	set text of screen element "hud_team" for {-td-online::%g%::*} to {-tstr}
	loop {-td-online::%g%::*}:
		set {-tp} to %loop-value%
		set text of screen element "hud_gold" for {-tp} to "&6%{-td-gold::%{-tp}%}%"

function TdShowRange(p: player, tx: number, ty: number, rng: number):
	hide screen element "rng_s" from p
	hide screen element "rng_m" from p
	hide screen element "rng_l" from p
	if rng <= 0:
		stop
	if rng >= 170:
		set position of screen element "rng_l" for p to tx ty
		show screen element "rng_l" to p
	else if rng >= 115:
		set position of screen element "rng_m" for p to tx ty
		show screen element "rng_m" to p
	else:
		set position of screen element "rng_s" for p to tx ty
		show screen element "rng_s" to p

# --- Обновление визуала и живого тултипа построенной башни ---

function TdSyncTowerVisual(g: player, tid: number):
	set {-tc} to {-td-tcol::%g%::%tid%}
	set {-tr} to {-td-trow::%g%::%tid%}
	set {-tt} to {-td-tt::%g%::%tid%}
	set {-tl} to {-td-tl::%g%::%tid%}
	set {-tx} to {-td-tx::%g%::%tid%}
	set {-ty} to {-td-ty::%g%::%tid%}
	set {-own} to {-td-towner::%g%::%tid%}
	set {-ref} to round({-td-tcost::%g%::%tid%} * 0.7)

	set {-tex} to "td:textures/gui/tower_archer.png"
	set {-tname} to "&eЛучник"
	set {-bdmg} to 15
	set {-evod} to "Шквал (2 цели сразу!)"
	if {-tt} is 2:
		set {-tex} to "td:textures/gui/tower_cannon.png"
		set {-tname} to "&6Пушка"
		set {-bdmg} to 36
		set {-evod} to "Напалм (мега-сплеш + поджог)"
	else if {-tt} is 3:
		set {-tex} to "td:textures/gui/tower_frost.png"
		set {-tname} to "&bЛёд"
		set {-bdmg} to 8
		set {-evod} to "Стужа (50%% замедл. по радиусу + 12%% заморозка)"
	else if {-tt} is 4:
		set {-tex} to "td:textures/gui/tower_mage.png"
		set {-tname} to "&dМаг"
		set {-bdmg} to 32
		set {-evod} to "Убийца Боссов (x2 + 4%% макс.HP)"
	else if {-tt} is 5:
		set {-tex} to "td:textures/gui/tower_tesla.png"
		set {-tname} to "&bТесла"
		set {-bdmg} to 20
		set {-evod} to "Шторм (5 целей + стан)"
	else if {-tt} is 6:
		set {-tex} to "td:textures/gui/tower_sniper.png"
		set {-tname} to "&cСнайпер"
		set {-bdmg} to 56
		set {-evod} to "Казнь (<20%% HP + крит 45%%)"
	else if {-tt} is 7:
		set {-tex} to "td:textures/gui/tower_poison.png"
		set {-tname} to "&aАлхимик"
		set {-bdmg} to 12
		set {-evod} to "Чума (заразный яд по области)"
	else if {-tt} is 8:
		set {-tex} to "td:textures/gui/tower_farm.png"
		set {-tname} to "&6Банк"
		set {-bdmg} to 0
		set {-evod} to "Золотой Дворец (+180з и +1 HP/волну)"

	set {-st} to "&e★"
	if {-tl} is 2:
		set {-st} to "&6★★"
	else if {-tl} is 3:
		set {-st} to "&d★★★"
	else if {-tl} is 4:
		set {-st} to "&b★★★★"
	else if {-tl} >= 5:
		set {-st} to "&6&l✦5★"

	TdTowerRange({-tt}, {-tl})
	set {-rng} to {-td-tmp-rng}

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
		set {-stat} to "&7Доход: &6+%{-inc}%з/волну"
	else:
		set {-cdmg} to round({-bdmg} * (1 + ({-tl} - 1) * 0.55))
		if {-tl} >= 5:
			set {-cdmg} to round({-cdmg} * 1.4)
		if {-tt} is 3:
			set {-stat} to "&7Аура урон: &b%{-cdmg}% &7| Радиус: &b%{-rng}%"
		else:
			set {-stat} to "&7Урон: &c%{-cdmg}% &7| Дальн: &b%{-rng}%"

	if {-tl} >= 5:
		set {-act} to "&6✦ %{-evod}%\n&7Продажа (X): &e+%{-ref}%з"
	else if {-tl} is 4:
		set {-act} to "&6✦ ЛКМ: Эволюция 5★ за 570з!\n&7Продажа (X): &e+%{-ref}%з"
	else:
		set {-up} to 95
		if {-tl} is 2:
			set {-up} to 210
		else if {-tl} is 3:
			set {-up} to 360
		set {-nl} to {-tl} + 1
		set {-act} to "&aЛКМ: ур.%{-nl}% за &6%{-up}%з &7| Продажа (X): &e+%{-ref}%з"

	set {-tip} to "%{-tname}% %{-st}% &7(ур.%{-tl}%)\n&7Владелец: &f%{-own}%\n%{-stat}%\n%{-act}%"
	TdSyncCell(g, {-tc}, {-tr}, {-tx}, {-ty}, {-tex}, {-tip})
	set {-sy} to {-ty} + 11
	TdSyncPos(g, "st%tid%", {-tx}, {-sy})
	TdSyncTxt(g, "st%tid%", {-st})
	TdSyncShow(g, "st%tid%")

# ==========================================================
#  СОВРЕМЕННОЕ МЕНЮ ВЫБОРА КАРТЫ, СЛОЖНОСТИ И КОМАНДЫ (1..4)
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
		box at 0 0 size 440x260 anchor center color #EE090E1A gradient #EE111C33 border #FF38BDF8 border_width 1 radius 10 shadow blur layer 0
		box at 0 -114 size 430x24 anchor center color #FF1D4ED8 to #FF7C3AED horizontal radius 6 layer 1
		text "&b&l✦ &f&lTOWER DEFENSE &b&lULTRA HD &b&l✦" at 0 -114 anchor center shadow layer 2
		text "&7Кооп до 4 игроков: &e/td coop <ник> &7(друг: &a/td join&7) | 8 башен + ✦5★ Эволюция!" at 0 -92 anchor center shadow layer 2

		# Карточки под 3 HD превью карт + плавный индикатор выбора
		box at -132 -40 size 130x68 anchor center color #CC162032 border #3338BDF8 radius 6 shadow layer 1
		box at 0 -40 size 130x68 anchor center color #CC162032 border #3338BDF8 radius 6 shadow layer 1
		box at 132 -40 size 130x68 anchor center color #CC162032 border #3338BDF8 radius 6 shadow layer 1
		box at -132 -6 size 122x2 anchor center color #FF22C55E radius 1 id map_sel layer 3

		image "td:textures/gui/map_valley.png" at -132 -40 size 124x62 texture 896x448 anchor center hover_scale 1.05 layer 2 run TdPickMap(player, 1) keep open tooltip "&aИзумрудная долина\n&7Классический S-маршрут"
		text "&a1. Долина" at -132 4 anchor center shadow layer 2 id m_lbl1

		image "td:textures/gui/map_volcano.png" at 0 -40 size 124x62 texture 896x448 anchor center hover_scale 1.05 layer 2 run TdPickMap(player, 2) keep open tooltip "&6Лавовый каньон\n&7Двойной зигзаг — много поворотов!"
		text "&72. Каньон" at 0 4 anchor center shadow layer 2 id m_lbl2

		image "td:textures/gui/map_ice.png" at 132 -40 size 124x62 texture 896x448 anchor center hover_scale 1.05 layer 2 run TdPickMap(player, 3) keep open tooltip "&bЛедяная цитадель\n&7Быстрый арктический серпантин!"
		text "&73. Цитадель" at 132 4 anchor center shadow layer 2 id m_lbl3

		# Блок сложности и команды
		box at 0 54 size 414x70 anchor center color #AA111827 border #2238BDF8 radius 6 layer 1
		text "&b&lСЛОЖНОСТЬ" at 0 26 anchor center shadow layer 2
		button "&aЛёгкий (10 волн)" at -122 44 size 112x20 anchor center layer 2 run TdPickDiff(player, 1) keep open tooltip "&aЛёгкий уровень\n&710 волн | 2 босса | 15 HP | 140з"
		button "&eСредний (20 волн)" at 0 44 size 112x20 anchor center layer 2 run TdPickDiff(player, 2) keep open tooltip "&eСредний уровень\n&720 волн | 4 босса | 10 HP | 120з"
		button "&cТяжёлый (30 волн)" at 122 44 size 112x20 anchor center layer 2 run TdPickDiff(player, 3) keep open tooltip "&cТяжёлый уровень\n&730 волн | 6 боссов | 8 HP | 110з"

		text "&7Выбрано: &aДолина &7| &eСредний" at 0 68 anchor center shadow layer 2 id menu_info
		text "&dКоманда (1/4): &f%p%" at 0 81 anchor center shadow layer 2 id menu_team
		button "&a&l▶ ИГРАТЬ" at -60 107 size 126x22 anchor center layer 2 run TdStartGame(player) keep open
		button "&6Топ-5" at 72 107 size 92x22 anchor center layer 2 run TdShowTop(player) keep open
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
		animate screen "map_sel" for p to -132 -6 in 4 ticks
	else if {-m} is 2:
		set {-mn} to "&6Каньон"
		set text of screen element "m_lbl2" for p to "&6&l▶ 2. Каньон"
		animate screen "map_sel" for p to 0 -6 in 4 ticks
	else:
		set {-mn} to "&bЦитадель"
		set text of screen element "m_lbl3" for p to "&b&l▶ 3. Цитадель"
		animate screen "map_sel" for p to 132 -6 in 4 ticks
	set {-dn} to "&eСредний (20 волн)"
	if {-d} is 1:
		set {-dn} to "&aЛёгкий (10 волн)"
	else if {-d} is 3:
		set {-dn} to "&cТяжёлый (30 волн)"
	set text of screen element "menu_info" for p to "&7Выбрано: %{-mn}% &7| %{-dn}%"
	set {-pcnt} to size of {-td-team::%p%::*}
	set {-names} to join {-td-team::%p%::*} with ", "
	set text of screen element "menu_team" for p to "&dКоманда (%{-pcnt}%/4): &f%{-names}%"

# ==========================================================
#  ЗАПУСК ИГРЫ (1..4 ИГРОКА)
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

	clear {-td-grid::%host%::*}
	loop 28 times:
		set {-i} to %loop-number%
		set {-td-ta::%host%::%{-i}%} to 0
		set {-td-tt::%host%::%{-i}%} to 0
		set {-td-tl::%host%::%{-i}%} to 0
		set {-td-tcd::%host%::%{-i}%} to 0
		set {-td-tcost::%host%::%{-i}%} to 0
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

	loop 6 times:
		set {-i} to %loop-number%
		set {-td-pra::%host%::%{-i}%} to 0

	TdLoadWaypoints(host, {-m})

	loop {-td-team::%host%::*}:
		set {-tp} to %loop-value%
		set {-td-host::%{-tp}%} to %host%
		set {-td-gold::%{-tp}%} to {-sgold}
		set {-td-tool::%{-tp}%} to 1
		set {-td-sell::%{-tp}%} to 0
		if {-m} is 1:
			TdOpenMap1({-tp})
		else if {-m} is 2:
			TdOpenMap2({-tp})
		else:
			TdOpenMap3({-tp})

	TdSyncHud(host)
	set {-pcnt} to size of {-td-team::%host%::*}
	if {-pcnt} > 1:
		set {-names} to join {-td-team::%host%::*} with ", "
		TdSyncStatus(host, "&d&lКООП (%{-pcnt}% игр.): &f%{-names}% &7— враги усилены! Удачи!")

function TdLoadWaypoints(g: player, mapId: number):
	if mapId is 1:
		set {-td-wpcnt::%g%} to 6
		set {-td-wpx::%g%::1} to -210
		set {-td-wpy::%g%::1} to -72
		set {-td-wpx::%g%::2} to 154
		set {-td-wpy::%g%::2} to -72
		set {-td-wpx::%g%::3} to 154
		set {-td-wpy::%g%::3} to -16
		set {-td-wpx::%g%::4} to -154
		set {-td-wpy::%g%::4} to -16
		set {-td-wpx::%g%::5} to -154
		set {-td-wpy::%g%::5} to 68
		set {-td-wpx::%g%::6} to 210
		set {-td-wpy::%g%::6} to 68
	else if mapId is 2:
		set {-td-wpcnt::%g%} to 8
		set {-td-wpx::%g%::1} to -210
		set {-td-wpy::%g%::1} to -100
		set {-td-wpx::%g%::2} to -98
		set {-td-wpy::%g%::2} to -100
		set {-td-wpx::%g%::3} to -98
		set {-td-wpy::%g%::3} to 68
		set {-td-wpx::%g%::4} to 42
		set {-td-wpy::%g%::4} to 68
		set {-td-wpx::%g%::5} to 42
		set {-td-wpy::%g%::5} to -72
		set {-td-wpx::%g%::6} to 154
		set {-td-wpy::%g%::6} to -72
		set {-td-wpx::%g%::7} to 154
		set {-td-wpy::%g%::7} to 40
		set {-td-wpx::%g%::8} to 210
		set {-td-wpy::%g%::8} to 40
	else if mapId is 3:
		set {-td-wpcnt::%g%} to 8
		set {-td-wpx::%g%::1} to -210
		set {-td-wpy::%g%::1} to 12
		set {-td-wpx::%g%::2} to -126
		set {-td-wpy::%g%::2} to 12
		set {-td-wpx::%g%::3} to -126
		set {-td-wpy::%g%::3} to -72
		set {-td-wpx::%g%::4} to -14
		set {-td-wpy::%g%::4} to -72
		set {-td-wpx::%g%::5} to -14
		set {-td-wpy::%g%::5} to 68
		set {-td-wpx::%g%::6} to 98
		set {-td-wpy::%g%::6} to 68
		set {-td-wpx::%g%::7} to 98
		set {-td-wpy::%g%::7} to -44
		set {-td-wpx::%g%::8} to 210
		set {-td-wpy::%g%::8} to -44

function TdOpenMap1(p: player):
	open screen "Tower Defense" to p:
		# Современный верхний HUD (скруглённые стеклянные карточки + blur + shadow)
		box at 0 -132 size 458x24 anchor center color #E6090E1A gradient #E6111C33 border #FF38BDF8 border_width 1 radius 6 shadow blur layer 11
		box at -198 -132 size 56x16 anchor center color #CC162032 border #4438BDF8 radius 4 layer 11
		box at -126 -132 size 64x16 anchor center color #CC162032 border #44FACC15 radius 4 layer 11
		box at -58 -132 size 50x16 anchor center color #CC162032 border #44EF4444 radius 4 layer 11
		box at 10 -132 size 68x16 anchor center color #CC162032 border #4422C55E radius 4 layer 11
		box at 140 -132 size 166x16 anchor center color #CC162032 border #44C084FC radius 4 layer 11
		image "td:textures/gui/icon_wave.png" at -216 -132 size 11x11 texture 32x32 anchor center layer 12 tooltip "&eТекущая волна"
		text "&f0/20" at -190 -132 anchor center shadow layer 12 id hud_wave
		image "td:textures/gui/icon_coin.png" at -148 -132 size 11x11 texture 32x32 anchor center layer 12 tooltip "&6Ваше личное золото"
		text "&6120" at -118 -132 anchor center shadow layer 12 id hud_gold
		image "td:textures/gui/icon_heart.png" at -72 -132 size 11x11 texture 32x32 anchor center layer 12 tooltip "&cЗдоровье базы"
		text "&c10" at -50 -132 anchor center shadow layer 12 id hud_hp
		image "td:textures/gui/icon_trophy.png" at -14 -132 size 11x11 texture 32x32 anchor center layer 12 tooltip "&aОбщий счёт"
		text "&a0" at 18 -132 anchor center shadow layer 12 id hud_score
		text "&7Соло &7[x1]" at 140 -132 anchor center shadow layer 12 id hud_team

		# Тактическая рамка с тенью + большая HD карта 448x224 (текстура 896x448)
		box at 0 -2 size 454x230 anchor center color #AA090E1A border #AA38BDF8 border_width 2 radius 5 shadow layer 0
		image "td:textures/gui/map_valley.png" at 0 -2 size 448x224 texture 896x448 anchor center layer 1 id map
		bar "boss_bar" value 100 max 100 at 0 -106 size 230x9 anchor center color #FFEF4444 background #CC1A0A0A text "&c&l☠ БОСС: %value%/%max%" shadow layer 14 hidden

		# Круги радиуса башни (малый / средний / снайпер)
		image "td:textures/gui/range_circle.png" at 0 0 size 196x196 texture 192x192 anchor center layer 2 id rng_s hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 260x260 texture 192x192 anchor center layer 2 id rng_m hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 390x390 texture 192x192 anchor center layer 2 id rng_l hidden

		# Свободная сетка постройки 16x8 (клетка 28x28, живые тултипы!)
		image "td:textures/gui/tile_build.png" at -210 -100 size 28x28 texture 28x28 anchor center layer 3 id c0_0 run TdCellClick(player, 0, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -182 -100 size 28x28 texture 28x28 anchor center layer 3 id c1_0 run TdCellClick(player, 1, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -154 -100 size 28x28 texture 28x28 anchor center layer 3 id c2_0 run TdCellClick(player, 2, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -126 -100 size 28x28 texture 28x28 anchor center layer 3 id c3_0 run TdCellClick(player, 3, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -98 -100 size 28x28 texture 28x28 anchor center layer 3 id c4_0 run TdCellClick(player, 4, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -70 -100 size 28x28 texture 28x28 anchor center layer 3 id c5_0 run TdCellClick(player, 5, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -42 -100 size 28x28 texture 28x28 anchor center layer 3 id c6_0 run TdCellClick(player, 6, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -14 -100 size 28x28 texture 28x28 anchor center layer 3 id c7_0 run TdCellClick(player, 7, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 14 -100 size 28x28 texture 28x28 anchor center layer 3 id c8_0 run TdCellClick(player, 8, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 42 -100 size 28x28 texture 28x28 anchor center layer 3 id c9_0 run TdCellClick(player, 9, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 70 -100 size 28x28 texture 28x28 anchor center layer 3 id c10_0 run TdCellClick(player, 10, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 98 -100 size 28x28 texture 28x28 anchor center layer 3 id c11_0 run TdCellClick(player, 11, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 126 -100 size 28x28 texture 28x28 anchor center layer 3 id c12_0 run TdCellClick(player, 12, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 154 -100 size 28x28 texture 28x28 anchor center layer 3 id c13_0 run TdCellClick(player, 13, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 182 -100 size 28x28 texture 28x28 anchor center layer 3 id c14_0 run TdCellClick(player, 14, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 210 -100 size 28x28 texture 28x28 anchor center layer 3 id c15_0 run TdCellClick(player, 15, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 182 -72 size 28x28 texture 28x28 anchor center layer 3 id c14_1 run TdCellClick(player, 14, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 210 -72 size 28x28 texture 28x28 anchor center layer 3 id c15_1 run TdCellClick(player, 15, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -210 -44 size 28x28 texture 28x28 anchor center layer 3 id c0_2 run TdCellClick(player, 0, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -182 -44 size 28x28 texture 28x28 anchor center layer 3 id c1_2 run TdCellClick(player, 1, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -154 -44 size 28x28 texture 28x28 anchor center layer 3 id c2_2 run TdCellClick(player, 2, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -126 -44 size 28x28 texture 28x28 anchor center layer 3 id c3_2 run TdCellClick(player, 3, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -98 -44 size 28x28 texture 28x28 anchor center layer 3 id c4_2 run TdCellClick(player, 4, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -70 -44 size 28x28 texture 28x28 anchor center layer 3 id c5_2 run TdCellClick(player, 5, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -42 -44 size 28x28 texture 28x28 anchor center layer 3 id c6_2 run TdCellClick(player, 6, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -14 -44 size 28x28 texture 28x28 anchor center layer 3 id c7_2 run TdCellClick(player, 7, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 14 -44 size 28x28 texture 28x28 anchor center layer 3 id c8_2 run TdCellClick(player, 8, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 42 -44 size 28x28 texture 28x28 anchor center layer 3 id c9_2 run TdCellClick(player, 9, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 70 -44 size 28x28 texture 28x28 anchor center layer 3 id c10_2 run TdCellClick(player, 10, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 98 -44 size 28x28 texture 28x28 anchor center layer 3 id c11_2 run TdCellClick(player, 11, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 126 -44 size 28x28 texture 28x28 anchor center layer 3 id c12_2 run TdCellClick(player, 12, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 182 -44 size 28x28 texture 28x28 anchor center layer 3 id c14_2 run TdCellClick(player, 14, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 210 -44 size 28x28 texture 28x28 anchor center layer 3 id c15_2 run TdCellClick(player, 15, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -210 -16 size 28x28 texture 28x28 anchor center layer 3 id c0_3 run TdCellClick(player, 0, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -182 -16 size 28x28 texture 28x28 anchor center layer 3 id c1_3 run TdCellClick(player, 1, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 182 -16 size 28x28 texture 28x28 anchor center layer 3 id c14_3 run TdCellClick(player, 14, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 210 -16 size 28x28 texture 28x28 anchor center layer 3 id c15_3 run TdCellClick(player, 15, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -210 12 size 28x28 texture 28x28 anchor center layer 3 id c0_4 run TdCellClick(player, 0, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -182 12 size 28x28 texture 28x28 anchor center layer 3 id c1_4 run TdCellClick(player, 1, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -126 12 size 28x28 texture 28x28 anchor center layer 3 id c3_4 run TdCellClick(player, 3, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -98 12 size 28x28 texture 28x28 anchor center layer 3 id c4_4 run TdCellClick(player, 4, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -70 12 size 28x28 texture 28x28 anchor center layer 3 id c5_4 run TdCellClick(player, 5, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -42 12 size 28x28 texture 28x28 anchor center layer 3 id c6_4 run TdCellClick(player, 6, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -14 12 size 28x28 texture 28x28 anchor center layer 3 id c7_4 run TdCellClick(player, 7, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 14 12 size 28x28 texture 28x28 anchor center layer 3 id c8_4 run TdCellClick(player, 8, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 42 12 size 28x28 texture 28x28 anchor center layer 3 id c9_4 run TdCellClick(player, 9, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 70 12 size 28x28 texture 28x28 anchor center layer 3 id c10_4 run TdCellClick(player, 10, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 98 12 size 28x28 texture 28x28 anchor center layer 3 id c11_4 run TdCellClick(player, 11, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 126 12 size 28x28 texture 28x28 anchor center layer 3 id c12_4 run TdCellClick(player, 12, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 154 12 size 28x28 texture 28x28 anchor center layer 3 id c13_4 run TdCellClick(player, 13, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 182 12 size 28x28 texture 28x28 anchor center layer 3 id c14_4 run TdCellClick(player, 14, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 210 12 size 28x28 texture 28x28 anchor center layer 3 id c15_4 run TdCellClick(player, 15, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -210 40 size 28x28 texture 28x28 anchor center layer 3 id c0_5 run TdCellClick(player, 0, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -182 40 size 28x28 texture 28x28 anchor center layer 3 id c1_5 run TdCellClick(player, 1, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -126 40 size 28x28 texture 28x28 anchor center layer 3 id c3_5 run TdCellClick(player, 3, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -98 40 size 28x28 texture 28x28 anchor center layer 3 id c4_5 run TdCellClick(player, 4, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -70 40 size 28x28 texture 28x28 anchor center layer 3 id c5_5 run TdCellClick(player, 5, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -42 40 size 28x28 texture 28x28 anchor center layer 3 id c6_5 run TdCellClick(player, 6, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -14 40 size 28x28 texture 28x28 anchor center layer 3 id c7_5 run TdCellClick(player, 7, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 14 40 size 28x28 texture 28x28 anchor center layer 3 id c8_5 run TdCellClick(player, 8, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 42 40 size 28x28 texture 28x28 anchor center layer 3 id c9_5 run TdCellClick(player, 9, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 70 40 size 28x28 texture 28x28 anchor center layer 3 id c10_5 run TdCellClick(player, 10, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 98 40 size 28x28 texture 28x28 anchor center layer 3 id c11_5 run TdCellClick(player, 11, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 126 40 size 28x28 texture 28x28 anchor center layer 3 id c12_5 run TdCellClick(player, 12, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 154 40 size 28x28 texture 28x28 anchor center layer 3 id c13_5 run TdCellClick(player, 13, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 182 40 size 28x28 texture 28x28 anchor center layer 3 id c14_5 run TdCellClick(player, 14, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 210 40 size 28x28 texture 28x28 anchor center layer 3 id c15_5 run TdCellClick(player, 15, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -210 68 size 28x28 texture 28x28 anchor center layer 3 id c0_6 run TdCellClick(player, 0, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -182 68 size 28x28 texture 28x28 anchor center layer 3 id c1_6 run TdCellClick(player, 1, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -210 96 size 28x28 texture 28x28 anchor center layer 3 id c0_7 run TdCellClick(player, 0, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -182 96 size 28x28 texture 28x28 anchor center layer 3 id c1_7 run TdCellClick(player, 1, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -154 96 size 28x28 texture 28x28 anchor center layer 3 id c2_7 run TdCellClick(player, 2, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -126 96 size 28x28 texture 28x28 anchor center layer 3 id c3_7 run TdCellClick(player, 3, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -98 96 size 28x28 texture 28x28 anchor center layer 3 id c4_7 run TdCellClick(player, 4, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -70 96 size 28x28 texture 28x28 anchor center layer 3 id c5_7 run TdCellClick(player, 5, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -42 96 size 28x28 texture 28x28 anchor center layer 3 id c6_7 run TdCellClick(player, 6, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -14 96 size 28x28 texture 28x28 anchor center layer 3 id c7_7 run TdCellClick(player, 7, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 14 96 size 28x28 texture 28x28 anchor center layer 3 id c8_7 run TdCellClick(player, 8, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 42 96 size 28x28 texture 28x28 anchor center layer 3 id c9_7 run TdCellClick(player, 9, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 70 96 size 28x28 texture 28x28 anchor center layer 3 id c10_7 run TdCellClick(player, 10, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 98 96 size 28x28 texture 28x28 anchor center layer 3 id c11_7 run TdCellClick(player, 11, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 126 96 size 28x28 texture 28x28 anchor center layer 3 id c12_7 run TdCellClick(player, 12, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 154 96 size 28x28 texture 28x28 anchor center layer 3 id c13_7 run TdCellClick(player, 13, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 182 96 size 28x28 texture 28x28 anchor center layer 3 id c14_7 run TdCellClick(player, 14, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 210 96 size 28x28 texture 28x28 anchor center layer 3 id c15_7 run TdCellClick(player, 15, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"

		# Пул звёзд уровней и эволюций (до 28 башен)
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
		text "" at 0 0 anchor center shadow layer 6 id st25 hidden
		text "" at 0 0 anchor center shadow layer 6 id st26 hidden
		text "" at 0 0 anchor center shadow layer 6 id st27 hidden
		text "" at 0 0 anchor center shadow layer 6 id st28 hidden

		# 11 слотов обычных врагов + 1 слот Босса + тонкие HP-бары (60 FPS tween!)
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e1 hidden
		bar "ehp1" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e2 hidden
		bar "ehp2" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e3 hidden
		bar "ehp3" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e4 hidden
		bar "ehp4" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e5 hidden
		bar "ehp5" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e6 hidden
		bar "ehp6" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e7 hidden
		bar "ehp7" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e8 hidden
		bar "ehp8" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e9 hidden
		bar "ehp9" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e10 hidden
		bar "ehp10" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e11 hidden
		bar "ehp11" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_boss.png" at -210 -72 size 32x32 texture 56x56 anchor center layer 8 id e12 hidden
		bar "ehp12" value 100 max 100 at -210 -91 size 30x4 anchor center color #FFEF4444 background #CC1A0A0A layer 9 hidden

		# 6 летающих HD прожектайлов (60 FPS tween!)
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr1 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr2 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr3 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr4 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr5 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr6 hidden

		# Современная инфо-строка статуса
		box at 0 119 size 458x15 anchor center color #E60B1220 border #3338BDF8 radius 4 layer 11
		box at -226 119 size 3x11 anchor center color #FF38BDF8 radius 1 layer 12
		text "&eВыбран: &fЛучник (50з) &7— наведи на башню для инфо, ЛКМ для постройки/апгрейда!" at 0 119 anchor center shadow layer 12 id status_txt
		text "&c&lБАЗА ПАЛА!" at 0 -16 anchor center shadow scale 1.4 layer 20 id end_title hidden
		text "&fНажми &eR &f(рестарт) или &eM &f(меню)" at 0 4 anchor center shadow layer 20 id end_sub hidden

		# Нижняя панель инструментов (скруглённые слоты + hover_scale + индикатор выбора)
		box at 0 148 size 458x42 anchor center color #EE090E1A gradient #EE111827 border #FF1E293B radius 6 shadow blur layer 11
		box at -208 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at -172 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at -136 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at -100 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at -64 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at -28 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at 8 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at 44 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at -208 131 size 30x2 anchor center color #FFFACC15 radius 1 id sel_bar layer 13
		image "td:textures/gui/tower_archer.png" at -208 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e[1] Лучник &7(50з)\n&7Урон: 15 | Очень быстрый | Дальн: 98\n&6✦5★ Шквал: &fстреляет сразу по 2 целям!"
		text "&e[1]50" at -208 159 anchor center shadow layer 12 id lbl1
		image "td:textures/gui/tower_cannon.png" at -172 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6[2] Пушка &7(95з)\n&7Урон: 36 + &cСплеш | Дальн: 92\n&6✦5★ Напалм: &fмега-сплеш + поджог врагов!"
		text "&7[2]95" at -172 159 anchor center shadow layer 12 id lbl2
		image "td:textures/gui/tower_frost.png" at -136 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b[3] Лёд &7(85з)\n&7Урон: 8 | &bАура замедления 40% по радиусу | Дальн: 96\n&6✦5★ Стужа: &f50% замедл. + 12% заморозка (0.5с, не боссов)"
		text "&7[3]85" at -136 159 anchor center shadow layer 12 id lbl3
		image "td:textures/gui/tower_mage.png" at -100 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d[4] Маг &7(130з)\n&7Урон: 32 | &dДальность 132\n&6✦5★ Убийца Боссов: &fx2 Боссам + 4% макс.HP!"
		text "&7[4]130" at -100 159 anchor center shadow layer 12 id lbl4
		image "td:textures/gui/tower_tesla.png" at -64 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico5 run TdSelectTool(player, 5) keep open tooltip "&b[5] Тесла &7(115з)\n&7Урон: 20 | &bЦепная молния (3 цели) | Дальн: 108\n&6✦5★ Шторм: &f5 целей + 90% урон + стан!"
		text "&7[5]115" at -64 159 anchor center shadow layer 12 id lbl5
		image "td:textures/gui/tower_sniper.png" at -28 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico6 run TdSelectTool(player, 6) keep open tooltip "&c[6] Снайпер &7(150з)\n&7Урон: 56 | &cКрит 25% | Дальн: 215\n&6✦5★ Казнь: &fкрит 45% x2.5 + казнь <20% HP!"
		text "&7[6]150" at -28 159 anchor center shadow layer 12 id lbl6
		image "td:textures/gui/tower_poison.png" at 8 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico7 run TdSelectTool(player, 7) keep open tooltip "&a[7] Алхимик &7(105з)\n&7Урон: 12 + &aЯд (урон в сек.) | Дальн: 100\n&6✦5★ Чума: &fяд заражает всех врагов вокруг!"
		text "&7[7]105" at 8 159 anchor center shadow layer 12 id lbl7
		image "td:textures/gui/tower_farm.png" at 44 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico8 run TdSelectTool(player, 8) keep open tooltip "&6[8] Банк &7(140з)\n&7Доход: &6+28/+50/+80/+120з &7каждую волну!\n&6✦5★ Дворец: &f+180з за волну и +1 HP базе!"
		text "&7[8]140" at 44 159 anchor center shadow layer 12 id lbl8
		button "&aСтарт/x2" at 118 139 size 68x16 anchor center run TdWaveOrSpeed(player) keep open tooltip "&aЗапустить волну &7(Space)\n&bВо время волны: скорость x1 / x2"
		toggle "Авто" id autowave at 192 139 size 58x16 anchor center value false color #22C55E tooltip "&aАвто-волна\n&7Авто-старт следующей волны через 1.5с"
		button "&cПродать" at 118 158 size 68x16 anchor center run TdToggleSell(player) keep open tooltip "&cПродать свою башню &7(X)\n&7Возврат 70% стоимости"
		button "Меню" at 192 158 size 58x16 anchor center run TdOpenMenu(player) keep open tooltip "&eВыбор карты, кооп и сложность &7(M)"

function TdOpenMap2(p: player):
	open screen "Tower Defense" to p:
		# Современный верхний HUD (скруглённые стеклянные карточки + blur + shadow)
		box at 0 -132 size 458x24 anchor center color #E6090E1A gradient #E6111C33 border #FF38BDF8 border_width 1 radius 6 shadow blur layer 11
		box at -198 -132 size 56x16 anchor center color #CC162032 border #4438BDF8 radius 4 layer 11
		box at -126 -132 size 64x16 anchor center color #CC162032 border #44FACC15 radius 4 layer 11
		box at -58 -132 size 50x16 anchor center color #CC162032 border #44EF4444 radius 4 layer 11
		box at 10 -132 size 68x16 anchor center color #CC162032 border #4422C55E radius 4 layer 11
		box at 140 -132 size 166x16 anchor center color #CC162032 border #44C084FC radius 4 layer 11
		image "td:textures/gui/icon_wave.png" at -216 -132 size 11x11 texture 32x32 anchor center layer 12 tooltip "&eТекущая волна"
		text "&f0/20" at -190 -132 anchor center shadow layer 12 id hud_wave
		image "td:textures/gui/icon_coin.png" at -148 -132 size 11x11 texture 32x32 anchor center layer 12 tooltip "&6Ваше личное золото"
		text "&6120" at -118 -132 anchor center shadow layer 12 id hud_gold
		image "td:textures/gui/icon_heart.png" at -72 -132 size 11x11 texture 32x32 anchor center layer 12 tooltip "&cЗдоровье базы"
		text "&c10" at -50 -132 anchor center shadow layer 12 id hud_hp
		image "td:textures/gui/icon_trophy.png" at -14 -132 size 11x11 texture 32x32 anchor center layer 12 tooltip "&aОбщий счёт"
		text "&a0" at 18 -132 anchor center shadow layer 12 id hud_score
		text "&7Соло &7[x1]" at 140 -132 anchor center shadow layer 12 id hud_team

		# Тактическая рамка с тенью + большая HD карта 448x224 (текстура 896x448)
		box at 0 -2 size 454x230 anchor center color #AA090E1A border #AA38BDF8 border_width 2 radius 5 shadow layer 0
		image "td:textures/gui/map_volcano.png" at 0 -2 size 448x224 texture 896x448 anchor center layer 1 id map
		bar "boss_bar" value 100 max 100 at 0 -106 size 230x9 anchor center color #FFEF4444 background #CC1A0A0A text "&c&l☠ БОСС: %value%/%max%" shadow layer 14 hidden

		# Круги радиуса башни (малый / средний / снайпер)
		image "td:textures/gui/range_circle.png" at 0 0 size 196x196 texture 192x192 anchor center layer 2 id rng_s hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 260x260 texture 192x192 anchor center layer 2 id rng_m hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 390x390 texture 192x192 anchor center layer 2 id rng_l hidden

		# Свободная сетка постройки 16x8 (клетка 28x28, живые тултипы!)
		image "td:textures/gui/tile_build.png" at -70 -100 size 28x28 texture 28x28 anchor center layer 3 id c5_0 run TdCellClick(player, 5, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -42 -100 size 28x28 texture 28x28 anchor center layer 3 id c6_0 run TdCellClick(player, 6, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -14 -100 size 28x28 texture 28x28 anchor center layer 3 id c7_0 run TdCellClick(player, 7, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 14 -100 size 28x28 texture 28x28 anchor center layer 3 id c8_0 run TdCellClick(player, 8, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 42 -100 size 28x28 texture 28x28 anchor center layer 3 id c9_0 run TdCellClick(player, 9, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 70 -100 size 28x28 texture 28x28 anchor center layer 3 id c10_0 run TdCellClick(player, 10, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 98 -100 size 28x28 texture 28x28 anchor center layer 3 id c11_0 run TdCellClick(player, 11, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 126 -100 size 28x28 texture 28x28 anchor center layer 3 id c12_0 run TdCellClick(player, 12, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 154 -100 size 28x28 texture 28x28 anchor center layer 3 id c13_0 run TdCellClick(player, 13, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 182 -100 size 28x28 texture 28x28 anchor center layer 3 id c14_0 run TdCellClick(player, 14, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 210 -100 size 28x28 texture 28x28 anchor center layer 3 id c15_0 run TdCellClick(player, 15, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -210 -72 size 28x28 texture 28x28 anchor center layer 3 id c0_1 run TdCellClick(player, 0, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -182 -72 size 28x28 texture 28x28 anchor center layer 3 id c1_1 run TdCellClick(player, 1, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -154 -72 size 28x28 texture 28x28 anchor center layer 3 id c2_1 run TdCellClick(player, 2, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -126 -72 size 28x28 texture 28x28 anchor center layer 3 id c3_1 run TdCellClick(player, 3, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -70 -72 size 28x28 texture 28x28 anchor center layer 3 id c5_1 run TdCellClick(player, 5, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -42 -72 size 28x28 texture 28x28 anchor center layer 3 id c6_1 run TdCellClick(player, 6, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -14 -72 size 28x28 texture 28x28 anchor center layer 3 id c7_1 run TdCellClick(player, 7, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 14 -72 size 28x28 texture 28x28 anchor center layer 3 id c8_1 run TdCellClick(player, 8, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 182 -72 size 28x28 texture 28x28 anchor center layer 3 id c14_1 run TdCellClick(player, 14, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 210 -72 size 28x28 texture 28x28 anchor center layer 3 id c15_1 run TdCellClick(player, 15, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -210 -44 size 28x28 texture 28x28 anchor center layer 3 id c0_2 run TdCellClick(player, 0, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -182 -44 size 28x28 texture 28x28 anchor center layer 3 id c1_2 run TdCellClick(player, 1, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -154 -44 size 28x28 texture 28x28 anchor center layer 3 id c2_2 run TdCellClick(player, 2, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -126 -44 size 28x28 texture 28x28 anchor center layer 3 id c3_2 run TdCellClick(player, 3, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -70 -44 size 28x28 texture 28x28 anchor center layer 3 id c5_2 run TdCellClick(player, 5, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -42 -44 size 28x28 texture 28x28 anchor center layer 3 id c6_2 run TdCellClick(player, 6, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -14 -44 size 28x28 texture 28x28 anchor center layer 3 id c7_2 run TdCellClick(player, 7, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 14 -44 size 28x28 texture 28x28 anchor center layer 3 id c8_2 run TdCellClick(player, 8, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 70 -44 size 28x28 texture 28x28 anchor center layer 3 id c10_2 run TdCellClick(player, 10, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 98 -44 size 28x28 texture 28x28 anchor center layer 3 id c11_2 run TdCellClick(player, 11, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 126 -44 size 28x28 texture 28x28 anchor center layer 3 id c12_2 run TdCellClick(player, 12, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 182 -44 size 28x28 texture 28x28 anchor center layer 3 id c14_2 run TdCellClick(player, 14, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 210 -44 size 28x28 texture 28x28 anchor center layer 3 id c15_2 run TdCellClick(player, 15, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -210 -16 size 28x28 texture 28x28 anchor center layer 3 id c0_3 run TdCellClick(player, 0, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -182 -16 size 28x28 texture 28x28 anchor center layer 3 id c1_3 run TdCellClick(player, 1, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -154 -16 size 28x28 texture 28x28 anchor center layer 3 id c2_3 run TdCellClick(player, 2, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -126 -16 size 28x28 texture 28x28 anchor center layer 3 id c3_3 run TdCellClick(player, 3, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -70 -16 size 28x28 texture 28x28 anchor center layer 3 id c5_3 run TdCellClick(player, 5, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -42 -16 size 28x28 texture 28x28 anchor center layer 3 id c6_3 run TdCellClick(player, 6, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -14 -16 size 28x28 texture 28x28 anchor center layer 3 id c7_3 run TdCellClick(player, 7, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 14 -16 size 28x28 texture 28x28 anchor center layer 3 id c8_3 run TdCellClick(player, 8, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 70 -16 size 28x28 texture 28x28 anchor center layer 3 id c10_3 run TdCellClick(player, 10, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 98 -16 size 28x28 texture 28x28 anchor center layer 3 id c11_3 run TdCellClick(player, 11, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 126 -16 size 28x28 texture 28x28 anchor center layer 3 id c12_3 run TdCellClick(player, 12, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 182 -16 size 28x28 texture 28x28 anchor center layer 3 id c14_3 run TdCellClick(player, 14, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 210 -16 size 28x28 texture 28x28 anchor center layer 3 id c15_3 run TdCellClick(player, 15, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -210 12 size 28x28 texture 28x28 anchor center layer 3 id c0_4 run TdCellClick(player, 0, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -182 12 size 28x28 texture 28x28 anchor center layer 3 id c1_4 run TdCellClick(player, 1, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -154 12 size 28x28 texture 28x28 anchor center layer 3 id c2_4 run TdCellClick(player, 2, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -126 12 size 28x28 texture 28x28 anchor center layer 3 id c3_4 run TdCellClick(player, 3, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -70 12 size 28x28 texture 28x28 anchor center layer 3 id c5_4 run TdCellClick(player, 5, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -42 12 size 28x28 texture 28x28 anchor center layer 3 id c6_4 run TdCellClick(player, 6, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -14 12 size 28x28 texture 28x28 anchor center layer 3 id c7_4 run TdCellClick(player, 7, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 14 12 size 28x28 texture 28x28 anchor center layer 3 id c8_4 run TdCellClick(player, 8, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 70 12 size 28x28 texture 28x28 anchor center layer 3 id c10_4 run TdCellClick(player, 10, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 98 12 size 28x28 texture 28x28 anchor center layer 3 id c11_4 run TdCellClick(player, 11, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 126 12 size 28x28 texture 28x28 anchor center layer 3 id c12_4 run TdCellClick(player, 12, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 182 12 size 28x28 texture 28x28 anchor center layer 3 id c14_4 run TdCellClick(player, 14, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 210 12 size 28x28 texture 28x28 anchor center layer 3 id c15_4 run TdCellClick(player, 15, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -210 40 size 28x28 texture 28x28 anchor center layer 3 id c0_5 run TdCellClick(player, 0, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -182 40 size 28x28 texture 28x28 anchor center layer 3 id c1_5 run TdCellClick(player, 1, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -154 40 size 28x28 texture 28x28 anchor center layer 3 id c2_5 run TdCellClick(player, 2, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -126 40 size 28x28 texture 28x28 anchor center layer 3 id c3_5 run TdCellClick(player, 3, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -70 40 size 28x28 texture 28x28 anchor center layer 3 id c5_5 run TdCellClick(player, 5, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -42 40 size 28x28 texture 28x28 anchor center layer 3 id c6_5 run TdCellClick(player, 6, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -14 40 size 28x28 texture 28x28 anchor center layer 3 id c7_5 run TdCellClick(player, 7, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 14 40 size 28x28 texture 28x28 anchor center layer 3 id c8_5 run TdCellClick(player, 8, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 70 40 size 28x28 texture 28x28 anchor center layer 3 id c10_5 run TdCellClick(player, 10, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 98 40 size 28x28 texture 28x28 anchor center layer 3 id c11_5 run TdCellClick(player, 11, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 126 40 size 28x28 texture 28x28 anchor center layer 3 id c12_5 run TdCellClick(player, 12, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -210 68 size 28x28 texture 28x28 anchor center layer 3 id c0_6 run TdCellClick(player, 0, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -182 68 size 28x28 texture 28x28 anchor center layer 3 id c1_6 run TdCellClick(player, 1, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -154 68 size 28x28 texture 28x28 anchor center layer 3 id c2_6 run TdCellClick(player, 2, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -126 68 size 28x28 texture 28x28 anchor center layer 3 id c3_6 run TdCellClick(player, 3, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 70 68 size 28x28 texture 28x28 anchor center layer 3 id c10_6 run TdCellClick(player, 10, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 98 68 size 28x28 texture 28x28 anchor center layer 3 id c11_6 run TdCellClick(player, 11, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 126 68 size 28x28 texture 28x28 anchor center layer 3 id c12_6 run TdCellClick(player, 12, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 154 68 size 28x28 texture 28x28 anchor center layer 3 id c13_6 run TdCellClick(player, 13, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 182 68 size 28x28 texture 28x28 anchor center layer 3 id c14_6 run TdCellClick(player, 14, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 210 68 size 28x28 texture 28x28 anchor center layer 3 id c15_6 run TdCellClick(player, 15, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -210 96 size 28x28 texture 28x28 anchor center layer 3 id c0_7 run TdCellClick(player, 0, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -182 96 size 28x28 texture 28x28 anchor center layer 3 id c1_7 run TdCellClick(player, 1, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -154 96 size 28x28 texture 28x28 anchor center layer 3 id c2_7 run TdCellClick(player, 2, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -126 96 size 28x28 texture 28x28 anchor center layer 3 id c3_7 run TdCellClick(player, 3, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -98 96 size 28x28 texture 28x28 anchor center layer 3 id c4_7 run TdCellClick(player, 4, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -70 96 size 28x28 texture 28x28 anchor center layer 3 id c5_7 run TdCellClick(player, 5, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -42 96 size 28x28 texture 28x28 anchor center layer 3 id c6_7 run TdCellClick(player, 6, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -14 96 size 28x28 texture 28x28 anchor center layer 3 id c7_7 run TdCellClick(player, 7, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 14 96 size 28x28 texture 28x28 anchor center layer 3 id c8_7 run TdCellClick(player, 8, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 42 96 size 28x28 texture 28x28 anchor center layer 3 id c9_7 run TdCellClick(player, 9, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 70 96 size 28x28 texture 28x28 anchor center layer 3 id c10_7 run TdCellClick(player, 10, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 98 96 size 28x28 texture 28x28 anchor center layer 3 id c11_7 run TdCellClick(player, 11, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 126 96 size 28x28 texture 28x28 anchor center layer 3 id c12_7 run TdCellClick(player, 12, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 154 96 size 28x28 texture 28x28 anchor center layer 3 id c13_7 run TdCellClick(player, 13, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 182 96 size 28x28 texture 28x28 anchor center layer 3 id c14_7 run TdCellClick(player, 14, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 210 96 size 28x28 texture 28x28 anchor center layer 3 id c15_7 run TdCellClick(player, 15, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"

		# Пул звёзд уровней и эволюций (до 28 башен)
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
		text "" at 0 0 anchor center shadow layer 6 id st25 hidden
		text "" at 0 0 anchor center shadow layer 6 id st26 hidden
		text "" at 0 0 anchor center shadow layer 6 id st27 hidden
		text "" at 0 0 anchor center shadow layer 6 id st28 hidden

		# 11 слотов обычных врагов + 1 слот Босса + тонкие HP-бары (60 FPS tween!)
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e1 hidden
		bar "ehp1" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e2 hidden
		bar "ehp2" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e3 hidden
		bar "ehp3" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e4 hidden
		bar "ehp4" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e5 hidden
		bar "ehp5" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e6 hidden
		bar "ehp6" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e7 hidden
		bar "ehp7" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e8 hidden
		bar "ehp8" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e9 hidden
		bar "ehp9" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e10 hidden
		bar "ehp10" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e11 hidden
		bar "ehp11" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_boss.png" at -210 -72 size 32x32 texture 56x56 anchor center layer 8 id e12 hidden
		bar "ehp12" value 100 max 100 at -210 -91 size 30x4 anchor center color #FFEF4444 background #CC1A0A0A layer 9 hidden

		# 6 летающих HD прожектайлов (60 FPS tween!)
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr1 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr2 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr3 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr4 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr5 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr6 hidden

		# Современная инфо-строка статуса
		box at 0 119 size 458x15 anchor center color #E60B1220 border #3338BDF8 radius 4 layer 11
		box at -226 119 size 3x11 anchor center color #FF38BDF8 radius 1 layer 12
		text "&eВыбран: &fЛучник (50з) &7— наведи на башню для инфо, ЛКМ для постройки/апгрейда!" at 0 119 anchor center shadow layer 12 id status_txt
		text "&c&lБАЗА ПАЛА!" at 0 -16 anchor center shadow scale 1.4 layer 20 id end_title hidden
		text "&fНажми &eR &f(рестарт) или &eM &f(меню)" at 0 4 anchor center shadow layer 20 id end_sub hidden

		# Нижняя панель инструментов (скруглённые слоты + hover_scale + индикатор выбора)
		box at 0 148 size 458x42 anchor center color #EE090E1A gradient #EE111827 border #FF1E293B radius 6 shadow blur layer 11
		box at -208 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at -172 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at -136 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at -100 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at -64 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at -28 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at 8 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at 44 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at -208 131 size 30x2 anchor center color #FFFACC15 radius 1 id sel_bar layer 13
		image "td:textures/gui/tower_archer.png" at -208 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e[1] Лучник &7(50з)\n&7Урон: 15 | Очень быстрый | Дальн: 98\n&6✦5★ Шквал: &fстреляет сразу по 2 целям!"
		text "&e[1]50" at -208 159 anchor center shadow layer 12 id lbl1
		image "td:textures/gui/tower_cannon.png" at -172 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6[2] Пушка &7(95з)\n&7Урон: 36 + &cСплеш | Дальн: 92\n&6✦5★ Напалм: &fмега-сплеш + поджог врагов!"
		text "&7[2]95" at -172 159 anchor center shadow layer 12 id lbl2
		image "td:textures/gui/tower_frost.png" at -136 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b[3] Лёд &7(85з)\n&7Урон: 8 | &bАура замедления 40% по радиусу | Дальн: 96\n&6✦5★ Стужа: &f50% замедл. + 12% заморозка (0.5с, не боссов)"
		text "&7[3]85" at -136 159 anchor center shadow layer 12 id lbl3
		image "td:textures/gui/tower_mage.png" at -100 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d[4] Маг &7(130з)\n&7Урон: 32 | &dДальность 132\n&6✦5★ Убийца Боссов: &fx2 Боссам + 4% макс.HP!"
		text "&7[4]130" at -100 159 anchor center shadow layer 12 id lbl4
		image "td:textures/gui/tower_tesla.png" at -64 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico5 run TdSelectTool(player, 5) keep open tooltip "&b[5] Тесла &7(115з)\n&7Урон: 20 | &bЦепная молния (3 цели) | Дальн: 108\n&6✦5★ Шторм: &f5 целей + 90% урон + стан!"
		text "&7[5]115" at -64 159 anchor center shadow layer 12 id lbl5
		image "td:textures/gui/tower_sniper.png" at -28 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico6 run TdSelectTool(player, 6) keep open tooltip "&c[6] Снайпер &7(150з)\n&7Урон: 56 | &cКрит 25% | Дальн: 215\n&6✦5★ Казнь: &fкрит 45% x2.5 + казнь <20% HP!"
		text "&7[6]150" at -28 159 anchor center shadow layer 12 id lbl6
		image "td:textures/gui/tower_poison.png" at 8 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico7 run TdSelectTool(player, 7) keep open tooltip "&a[7] Алхимик &7(105з)\n&7Урон: 12 + &aЯд (урон в сек.) | Дальн: 100\n&6✦5★ Чума: &fяд заражает всех врагов вокруг!"
		text "&7[7]105" at 8 159 anchor center shadow layer 12 id lbl7
		image "td:textures/gui/tower_farm.png" at 44 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico8 run TdSelectTool(player, 8) keep open tooltip "&6[8] Банк &7(140з)\n&7Доход: &6+28/+50/+80/+120з &7каждую волну!\n&6✦5★ Дворец: &f+180з за волну и +1 HP базе!"
		text "&7[8]140" at 44 159 anchor center shadow layer 12 id lbl8
		button "&aСтарт/x2" at 118 139 size 68x16 anchor center run TdWaveOrSpeed(player) keep open tooltip "&aЗапустить волну &7(Space)\n&bВо время волны: скорость x1 / x2"
		toggle "Авто" id autowave at 192 139 size 58x16 anchor center value false color #22C55E tooltip "&aАвто-волна\n&7Авто-старт следующей волны через 1.5с"
		button "&cПродать" at 118 158 size 68x16 anchor center run TdToggleSell(player) keep open tooltip "&cПродать свою башню &7(X)\n&7Возврат 70% стоимости"
		button "Меню" at 192 158 size 58x16 anchor center run TdOpenMenu(player) keep open tooltip "&eВыбор карты, кооп и сложность &7(M)"

function TdOpenMap3(p: player):
	open screen "Tower Defense" to p:
		# Современный верхний HUD (скруглённые стеклянные карточки + blur + shadow)
		box at 0 -132 size 458x24 anchor center color #E6090E1A gradient #E6111C33 border #FF38BDF8 border_width 1 radius 6 shadow blur layer 11
		box at -198 -132 size 56x16 anchor center color #CC162032 border #4438BDF8 radius 4 layer 11
		box at -126 -132 size 64x16 anchor center color #CC162032 border #44FACC15 radius 4 layer 11
		box at -58 -132 size 50x16 anchor center color #CC162032 border #44EF4444 radius 4 layer 11
		box at 10 -132 size 68x16 anchor center color #CC162032 border #4422C55E radius 4 layer 11
		box at 140 -132 size 166x16 anchor center color #CC162032 border #44C084FC radius 4 layer 11
		image "td:textures/gui/icon_wave.png" at -216 -132 size 11x11 texture 32x32 anchor center layer 12 tooltip "&eТекущая волна"
		text "&f0/20" at -190 -132 anchor center shadow layer 12 id hud_wave
		image "td:textures/gui/icon_coin.png" at -148 -132 size 11x11 texture 32x32 anchor center layer 12 tooltip "&6Ваше личное золото"
		text "&6120" at -118 -132 anchor center shadow layer 12 id hud_gold
		image "td:textures/gui/icon_heart.png" at -72 -132 size 11x11 texture 32x32 anchor center layer 12 tooltip "&cЗдоровье базы"
		text "&c10" at -50 -132 anchor center shadow layer 12 id hud_hp
		image "td:textures/gui/icon_trophy.png" at -14 -132 size 11x11 texture 32x32 anchor center layer 12 tooltip "&aОбщий счёт"
		text "&a0" at 18 -132 anchor center shadow layer 12 id hud_score
		text "&7Соло &7[x1]" at 140 -132 anchor center shadow layer 12 id hud_team

		# Тактическая рамка с тенью + большая HD карта 448x224 (текстура 896x448)
		box at 0 -2 size 454x230 anchor center color #AA090E1A border #AA38BDF8 border_width 2 radius 5 shadow layer 0
		image "td:textures/gui/map_ice.png" at 0 -2 size 448x224 texture 896x448 anchor center layer 1 id map
		bar "boss_bar" value 100 max 100 at 0 -106 size 230x9 anchor center color #FFEF4444 background #CC1A0A0A text "&c&l☠ БОСС: %value%/%max%" shadow layer 14 hidden

		# Круги радиуса башни (малый / средний / снайпер)
		image "td:textures/gui/range_circle.png" at 0 0 size 196x196 texture 192x192 anchor center layer 2 id rng_s hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 260x260 texture 192x192 anchor center layer 2 id rng_m hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 390x390 texture 192x192 anchor center layer 2 id rng_l hidden

		# Свободная сетка постройки 16x8 (клетка 28x28, живые тултипы!)
		image "td:textures/gui/tile_build.png" at -210 -100 size 28x28 texture 28x28 anchor center layer 3 id c0_0 run TdCellClick(player, 0, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -182 -100 size 28x28 texture 28x28 anchor center layer 3 id c1_0 run TdCellClick(player, 1, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -154 -100 size 28x28 texture 28x28 anchor center layer 3 id c2_0 run TdCellClick(player, 2, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -126 -100 size 28x28 texture 28x28 anchor center layer 3 id c3_0 run TdCellClick(player, 3, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -98 -100 size 28x28 texture 28x28 anchor center layer 3 id c4_0 run TdCellClick(player, 4, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -70 -100 size 28x28 texture 28x28 anchor center layer 3 id c5_0 run TdCellClick(player, 5, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -42 -100 size 28x28 texture 28x28 anchor center layer 3 id c6_0 run TdCellClick(player, 6, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -14 -100 size 28x28 texture 28x28 anchor center layer 3 id c7_0 run TdCellClick(player, 7, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 14 -100 size 28x28 texture 28x28 anchor center layer 3 id c8_0 run TdCellClick(player, 8, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 42 -100 size 28x28 texture 28x28 anchor center layer 3 id c9_0 run TdCellClick(player, 9, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 70 -100 size 28x28 texture 28x28 anchor center layer 3 id c10_0 run TdCellClick(player, 10, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 98 -100 size 28x28 texture 28x28 anchor center layer 3 id c11_0 run TdCellClick(player, 11, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 126 -100 size 28x28 texture 28x28 anchor center layer 3 id c12_0 run TdCellClick(player, 12, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 154 -100 size 28x28 texture 28x28 anchor center layer 3 id c13_0 run TdCellClick(player, 13, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 182 -100 size 28x28 texture 28x28 anchor center layer 3 id c14_0 run TdCellClick(player, 14, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 210 -100 size 28x28 texture 28x28 anchor center layer 3 id c15_0 run TdCellClick(player, 15, 0) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -210 -72 size 28x28 texture 28x28 anchor center layer 3 id c0_1 run TdCellClick(player, 0, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -182 -72 size 28x28 texture 28x28 anchor center layer 3 id c1_1 run TdCellClick(player, 1, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -154 -72 size 28x28 texture 28x28 anchor center layer 3 id c2_1 run TdCellClick(player, 2, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 14 -72 size 28x28 texture 28x28 anchor center layer 3 id c8_1 run TdCellClick(player, 8, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 42 -72 size 28x28 texture 28x28 anchor center layer 3 id c9_1 run TdCellClick(player, 9, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 70 -72 size 28x28 texture 28x28 anchor center layer 3 id c10_1 run TdCellClick(player, 10, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 98 -72 size 28x28 texture 28x28 anchor center layer 3 id c11_1 run TdCellClick(player, 11, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 126 -72 size 28x28 texture 28x28 anchor center layer 3 id c12_1 run TdCellClick(player, 12, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 154 -72 size 28x28 texture 28x28 anchor center layer 3 id c13_1 run TdCellClick(player, 13, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 182 -72 size 28x28 texture 28x28 anchor center layer 3 id c14_1 run TdCellClick(player, 14, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 210 -72 size 28x28 texture 28x28 anchor center layer 3 id c15_1 run TdCellClick(player, 15, 1) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -210 -44 size 28x28 texture 28x28 anchor center layer 3 id c0_2 run TdCellClick(player, 0, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -182 -44 size 28x28 texture 28x28 anchor center layer 3 id c1_2 run TdCellClick(player, 1, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -154 -44 size 28x28 texture 28x28 anchor center layer 3 id c2_2 run TdCellClick(player, 2, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -98 -44 size 28x28 texture 28x28 anchor center layer 3 id c4_2 run TdCellClick(player, 4, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -70 -44 size 28x28 texture 28x28 anchor center layer 3 id c5_2 run TdCellClick(player, 5, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -42 -44 size 28x28 texture 28x28 anchor center layer 3 id c6_2 run TdCellClick(player, 6, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 14 -44 size 28x28 texture 28x28 anchor center layer 3 id c8_2 run TdCellClick(player, 8, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 42 -44 size 28x28 texture 28x28 anchor center layer 3 id c9_2 run TdCellClick(player, 9, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 70 -44 size 28x28 texture 28x28 anchor center layer 3 id c10_2 run TdCellClick(player, 10, 2) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -210 -16 size 28x28 texture 28x28 anchor center layer 3 id c0_3 run TdCellClick(player, 0, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -182 -16 size 28x28 texture 28x28 anchor center layer 3 id c1_3 run TdCellClick(player, 1, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -154 -16 size 28x28 texture 28x28 anchor center layer 3 id c2_3 run TdCellClick(player, 2, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -98 -16 size 28x28 texture 28x28 anchor center layer 3 id c4_3 run TdCellClick(player, 4, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -70 -16 size 28x28 texture 28x28 anchor center layer 3 id c5_3 run TdCellClick(player, 5, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -42 -16 size 28x28 texture 28x28 anchor center layer 3 id c6_3 run TdCellClick(player, 6, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 14 -16 size 28x28 texture 28x28 anchor center layer 3 id c8_3 run TdCellClick(player, 8, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 42 -16 size 28x28 texture 28x28 anchor center layer 3 id c9_3 run TdCellClick(player, 9, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 70 -16 size 28x28 texture 28x28 anchor center layer 3 id c10_3 run TdCellClick(player, 10, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 126 -16 size 28x28 texture 28x28 anchor center layer 3 id c12_3 run TdCellClick(player, 12, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 154 -16 size 28x28 texture 28x28 anchor center layer 3 id c13_3 run TdCellClick(player, 13, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 182 -16 size 28x28 texture 28x28 anchor center layer 3 id c14_3 run TdCellClick(player, 14, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 210 -16 size 28x28 texture 28x28 anchor center layer 3 id c15_3 run TdCellClick(player, 15, 3) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -98 12 size 28x28 texture 28x28 anchor center layer 3 id c4_4 run TdCellClick(player, 4, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -70 12 size 28x28 texture 28x28 anchor center layer 3 id c5_4 run TdCellClick(player, 5, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -42 12 size 28x28 texture 28x28 anchor center layer 3 id c6_4 run TdCellClick(player, 6, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 14 12 size 28x28 texture 28x28 anchor center layer 3 id c8_4 run TdCellClick(player, 8, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 42 12 size 28x28 texture 28x28 anchor center layer 3 id c9_4 run TdCellClick(player, 9, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 70 12 size 28x28 texture 28x28 anchor center layer 3 id c10_4 run TdCellClick(player, 10, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 126 12 size 28x28 texture 28x28 anchor center layer 3 id c12_4 run TdCellClick(player, 12, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 154 12 size 28x28 texture 28x28 anchor center layer 3 id c13_4 run TdCellClick(player, 13, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 182 12 size 28x28 texture 28x28 anchor center layer 3 id c14_4 run TdCellClick(player, 14, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 210 12 size 28x28 texture 28x28 anchor center layer 3 id c15_4 run TdCellClick(player, 15, 4) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -210 40 size 28x28 texture 28x28 anchor center layer 3 id c0_5 run TdCellClick(player, 0, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -182 40 size 28x28 texture 28x28 anchor center layer 3 id c1_5 run TdCellClick(player, 1, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -154 40 size 28x28 texture 28x28 anchor center layer 3 id c2_5 run TdCellClick(player, 2, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -126 40 size 28x28 texture 28x28 anchor center layer 3 id c3_5 run TdCellClick(player, 3, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -98 40 size 28x28 texture 28x28 anchor center layer 3 id c4_5 run TdCellClick(player, 4, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -70 40 size 28x28 texture 28x28 anchor center layer 3 id c5_5 run TdCellClick(player, 5, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -42 40 size 28x28 texture 28x28 anchor center layer 3 id c6_5 run TdCellClick(player, 6, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 14 40 size 28x28 texture 28x28 anchor center layer 3 id c8_5 run TdCellClick(player, 8, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 42 40 size 28x28 texture 28x28 anchor center layer 3 id c9_5 run TdCellClick(player, 9, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 70 40 size 28x28 texture 28x28 anchor center layer 3 id c10_5 run TdCellClick(player, 10, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 126 40 size 28x28 texture 28x28 anchor center layer 3 id c12_5 run TdCellClick(player, 12, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 154 40 size 28x28 texture 28x28 anchor center layer 3 id c13_5 run TdCellClick(player, 13, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 182 40 size 28x28 texture 28x28 anchor center layer 3 id c14_5 run TdCellClick(player, 14, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 210 40 size 28x28 texture 28x28 anchor center layer 3 id c15_5 run TdCellClick(player, 15, 5) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -210 68 size 28x28 texture 28x28 anchor center layer 3 id c0_6 run TdCellClick(player, 0, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -182 68 size 28x28 texture 28x28 anchor center layer 3 id c1_6 run TdCellClick(player, 1, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -154 68 size 28x28 texture 28x28 anchor center layer 3 id c2_6 run TdCellClick(player, 2, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -126 68 size 28x28 texture 28x28 anchor center layer 3 id c3_6 run TdCellClick(player, 3, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -98 68 size 28x28 texture 28x28 anchor center layer 3 id c4_6 run TdCellClick(player, 4, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -70 68 size 28x28 texture 28x28 anchor center layer 3 id c5_6 run TdCellClick(player, 5, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -42 68 size 28x28 texture 28x28 anchor center layer 3 id c6_6 run TdCellClick(player, 6, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 126 68 size 28x28 texture 28x28 anchor center layer 3 id c12_6 run TdCellClick(player, 12, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 154 68 size 28x28 texture 28x28 anchor center layer 3 id c13_6 run TdCellClick(player, 13, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 182 68 size 28x28 texture 28x28 anchor center layer 3 id c14_6 run TdCellClick(player, 14, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 210 68 size 28x28 texture 28x28 anchor center layer 3 id c15_6 run TdCellClick(player, 15, 6) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -210 96 size 28x28 texture 28x28 anchor center layer 3 id c0_7 run TdCellClick(player, 0, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -182 96 size 28x28 texture 28x28 anchor center layer 3 id c1_7 run TdCellClick(player, 1, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -154 96 size 28x28 texture 28x28 anchor center layer 3 id c2_7 run TdCellClick(player, 2, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -126 96 size 28x28 texture 28x28 anchor center layer 3 id c3_7 run TdCellClick(player, 3, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -98 96 size 28x28 texture 28x28 anchor center layer 3 id c4_7 run TdCellClick(player, 4, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -70 96 size 28x28 texture 28x28 anchor center layer 3 id c5_7 run TdCellClick(player, 5, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -42 96 size 28x28 texture 28x28 anchor center layer 3 id c6_7 run TdCellClick(player, 6, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at -14 96 size 28x28 texture 28x28 anchor center layer 3 id c7_7 run TdCellClick(player, 7, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 14 96 size 28x28 texture 28x28 anchor center layer 3 id c8_7 run TdCellClick(player, 8, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 42 96 size 28x28 texture 28x28 anchor center layer 3 id c9_7 run TdCellClick(player, 9, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 70 96 size 28x28 texture 28x28 anchor center layer 3 id c10_7 run TdCellClick(player, 10, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 98 96 size 28x28 texture 28x28 anchor center layer 3 id c11_7 run TdCellClick(player, 11, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 126 96 size 28x28 texture 28x28 anchor center layer 3 id c12_7 run TdCellClick(player, 12, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 154 96 size 28x28 texture 28x28 anchor center layer 3 id c13_7 run TdCellClick(player, 13, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 182 96 size 28x28 texture 28x28 anchor center layer 3 id c14_7 run TdCellClick(player, 14, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"
		image "td:textures/gui/tile_build.png" at 210 96 size 28x28 texture 28x28 anchor center layer 3 id c15_7 run TdCellClick(player, 15, 7) keep open tooltip "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню"

		# Пул звёзд уровней и эволюций (до 28 башен)
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
		text "" at 0 0 anchor center shadow layer 6 id st25 hidden
		text "" at 0 0 anchor center shadow layer 6 id st26 hidden
		text "" at 0 0 anchor center shadow layer 6 id st27 hidden
		text "" at 0 0 anchor center shadow layer 6 id st28 hidden

		# 11 слотов обычных врагов + 1 слот Босса + тонкие HP-бары (60 FPS tween!)
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e1 hidden
		bar "ehp1" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e2 hidden
		bar "ehp2" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e3 hidden
		bar "ehp3" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e4 hidden
		bar "ehp4" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e5 hidden
		bar "ehp5" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e6 hidden
		bar "ehp6" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e7 hidden
		bar "ehp7" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e8 hidden
		bar "ehp8" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e9 hidden
		bar "ehp9" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e10 hidden
		bar "ehp10" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e11 hidden
		bar "ehp11" value 100 max 100 at -210 -85 size 20x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_boss.png" at -210 -72 size 32x32 texture 56x56 anchor center layer 8 id e12 hidden
		bar "ehp12" value 100 max 100 at -210 -91 size 30x4 anchor center color #FFEF4444 background #CC1A0A0A layer 9 hidden

		# 6 летающих HD прожектайлов (60 FPS tween!)
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr1 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr2 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr3 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr4 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr5 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr6 hidden

		# Современная инфо-строка статуса
		box at 0 119 size 458x15 anchor center color #E60B1220 border #3338BDF8 radius 4 layer 11
		box at -226 119 size 3x11 anchor center color #FF38BDF8 radius 1 layer 12
		text "&eВыбран: &fЛучник (50з) &7— наведи на башню для инфо, ЛКМ для постройки/апгрейда!" at 0 119 anchor center shadow layer 12 id status_txt
		text "&c&lБАЗА ПАЛА!" at 0 -16 anchor center shadow scale 1.4 layer 20 id end_title hidden
		text "&fНажми &eR &f(рестарт) или &eM &f(меню)" at 0 4 anchor center shadow layer 20 id end_sub hidden

		# Нижняя панель инструментов (скруглённые слоты + hover_scale + индикатор выбора)
		box at 0 148 size 458x42 anchor center color #EE090E1A gradient #EE111827 border #FF1E293B radius 6 shadow blur layer 11
		box at -208 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at -172 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at -136 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at -100 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at -64 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at -28 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at 8 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at 44 148 size 32x34 anchor center color #CC162032 border #3338BDF8 radius 5 layer 11
		box at -208 131 size 30x2 anchor center color #FFFACC15 radius 1 id sel_bar layer 13
		image "td:textures/gui/tower_archer.png" at -208 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e[1] Лучник &7(50з)\n&7Урон: 15 | Очень быстрый | Дальн: 98\n&6✦5★ Шквал: &fстреляет сразу по 2 целям!"
		text "&e[1]50" at -208 159 anchor center shadow layer 12 id lbl1
		image "td:textures/gui/tower_cannon.png" at -172 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6[2] Пушка &7(95з)\n&7Урон: 36 + &cСплеш | Дальн: 92\n&6✦5★ Напалм: &fмега-сплеш + поджог врагов!"
		text "&7[2]95" at -172 159 anchor center shadow layer 12 id lbl2
		image "td:textures/gui/tower_frost.png" at -136 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b[3] Лёд &7(85з)\n&7Урон: 8 | &bАура замедления 40% по радиусу | Дальн: 96\n&6✦5★ Стужа: &f50% замедл. + 12% заморозка (0.5с, не боссов)"
		text "&7[3]85" at -136 159 anchor center shadow layer 12 id lbl3
		image "td:textures/gui/tower_mage.png" at -100 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d[4] Маг &7(130з)\n&7Урон: 32 | &dДальность 132\n&6✦5★ Убийца Боссов: &fx2 Боссам + 4% макс.HP!"
		text "&7[4]130" at -100 159 anchor center shadow layer 12 id lbl4
		image "td:textures/gui/tower_tesla.png" at -64 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico5 run TdSelectTool(player, 5) keep open tooltip "&b[5] Тесла &7(115з)\n&7Урон: 20 | &bЦепная молния (3 цели) | Дальн: 108\n&6✦5★ Шторм: &f5 целей + 90% урон + стан!"
		text "&7[5]115" at -64 159 anchor center shadow layer 12 id lbl5
		image "td:textures/gui/tower_sniper.png" at -28 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico6 run TdSelectTool(player, 6) keep open tooltip "&c[6] Снайпер &7(150з)\n&7Урон: 56 | &cКрит 25% | Дальн: 215\n&6✦5★ Казнь: &fкрит 45% x2.5 + казнь <20% HP!"
		text "&7[6]150" at -28 159 anchor center shadow layer 12 id lbl6
		image "td:textures/gui/tower_poison.png" at 8 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico7 run TdSelectTool(player, 7) keep open tooltip "&a[7] Алхимик &7(105з)\n&7Урон: 12 + &aЯд (урон в сек.) | Дальн: 100\n&6✦5★ Чума: &fяд заражает всех врагов вокруг!"
		text "&7[7]105" at 8 159 anchor center shadow layer 12 id lbl7
		image "td:textures/gui/tower_farm.png" at 44 142 size 22x22 texture 48x48 anchor center hover_scale 1.14 layer 12 id ico8 run TdSelectTool(player, 8) keep open tooltip "&6[8] Банк &7(140з)\n&7Доход: &6+28/+50/+80/+120з &7каждую волну!\n&6✦5★ Дворец: &f+180з за волну и +1 HP базе!"
		text "&7[8]140" at 44 159 anchor center shadow layer 12 id lbl8
		button "&aСтарт/x2" at 118 139 size 68x16 anchor center run TdWaveOrSpeed(player) keep open tooltip "&aЗапустить волну &7(Space)\n&bВо время волны: скорость x1 / x2"
		toggle "Авто" id autowave at 192 139 size 58x16 anchor center value false color #22C55E tooltip "&aАвто-волна\n&7Авто-старт следующей волны через 1.5с"
		button "&cПродать" at 118 158 size 68x16 anchor center run TdToggleSell(player) keep open tooltip "&cПродать свою башню &7(X)\n&7Возврат 70% стоимости"
		button "Меню" at 192 158 size 58x16 anchor center run TdOpenMenu(player) keep open tooltip "&eВыбор карты, кооп и сложность &7(M)"

# ==========================================================
#  УПРАВЛЕНИЕ, СТРОИТЕЛЬСТВО, РАДИУС И ✦5★ ЭВОЛЮЦИЯ
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
	set text of screen element "lbl1" for p to "&7[1]50"
	set text of screen element "lbl2" for p to "&7[2]95"
	set text of screen element "lbl3" for p to "&7[3]85"
	set text of screen element "lbl4" for p to "&7[4]130"
	set text of screen element "lbl5" for p to "&7[5]115"
	set text of screen element "lbl6" for p to "&7[6]150"
	set text of screen element "lbl7" for p to "&7[7]105"
	set text of screen element "lbl8" for p to "&7[8]140"
	set {-sx} to -208 + (tool - 1) * 36
	animate screen "sel_bar" for p to {-sx} 131 in 3 ticks
	if tool is 1:
		set text of screen element "lbl1" for p to "&e[1]50"
		set text of screen element "status_txt" for p to "&eЛучник (50з) &7| &6✦5★: &fШквал по 2 целям!"
	else if tool is 2:
		set text of screen element "lbl2" for p to "&6[2]95"
		set text of screen element "status_txt" for p to "&6Пушка (95з) &7| &6✦5★: &fНапалм (поджог + мега-сплеш)!"
	else if tool is 3:
		set text of screen element "lbl3" for p to "&b[3]85"
		set text of screen element "status_txt" for p to "&bЛёд (85з) &7| Аура замедления по радиусу | &6✦5★: &fСтужа (50%% + 12%% фриз)"
	else if tool is 4:
		set text of screen element "lbl4" for p to "&d[4]130"
		set text of screen element "status_txt" for p to "&dМаг (130з) &7| &6✦5★: &fУбийца Боссов (x2 + 4%% HP)!"
	else if tool is 5:
		set text of screen element "lbl5" for p to "&b[5]115"
		set text of screen element "status_txt" for p to "&bТесла (115з) &7| &6✦5★: &fШторм (5 целей + стан)!"
	else if tool is 6:
		set text of screen element "lbl6" for p to "&c[6]150"
		set text of screen element "status_txt" for p to "&cСнайпер (150з) &7| &6✦5★: &fКазнь (<20%% HP + крит 45%%)!"
	else if tool is 7:
		set text of screen element "lbl7" for p to "&a[7]105"
		set text of screen element "status_txt" for p to "&aАлхимик (105з) &7| &6✦5★: &fЧума (яд заражает толпу)!"
	else if tool is 8:
		set text of screen element "lbl8" for p to "&6[8]140"
		set text of screen element "status_txt" for p to "&6Банк (140з) &7| &6✦5★: &fЗолотой Дворец (+180з и +1 HP базе)!"
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
		TdShowRange(p, 0, 0, 0)
		set text of screen element "status_txt" for p to "&c&lПРОДАЖА &7— клик по своей башне вернёт 70%% (X — отмена)"
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

function TdTowerRange(tt: number, lvl: number):
	set {-r} to 98
	if tt is 2:
		set {-r} to 92
	else if tt is 3:
		set {-r} to 96
	else if tt is 4:
		set {-r} to 132
	else if tt is 5:
		set {-r} to 108
	else if tt is 6:
		set {-r} to 215
	else if tt is 7:
		set {-r} to 100
	else if tt is 8:
		set {-r} to 0
	if {-r} > 0:
		set {-r} to {-r} + (lvl - 1) * 9
		if lvl >= 5:
			add 14 to {-r}
	set {-td-tmp-rng} to {-r}

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
		set {-own} to {-td-towner::%{-g}%::%{-tid}%}
		if {-own} is not %p% and %p% is not {-g}:
			set text of screen element "status_txt" for p to "&cЭто башня игрока &f%{-own}%&c! Продавать может владелец"
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
		TdShowRange(p, 0, 0, 0)
		TdSyncCell({-g}, col, row, {-tx}, {-ty}, "td:textures/gui/tile_build.png", "&7Свободная клетка\n&aЛКМ: &fпостроить выбранную башню")
		TdSyncHide({-g}, "st%{-tid}%")
		gui float "&6+%{-ref}%з" at {-tx} {-ty} color #FACC15 for 16 ticks to {-td-online::%{-g}%::*}
		gui particle "gold" count 8 at {-tx} {-ty} spread 22 to {-td-online::%{-g}%::*}
		TdSyncStatus({-g}, "&e%p% продал башню (+%{-ref}%з)")
		TdSyncSound({-g}, "entity.experience_orb.pickup", 0.6, 0.9)
		TdSyncGoldScore({-g})
		stop

	# Клик по построенной башне -> радиус + апгрейд/эволюция!
	if {-tid} > 0:
		set {-lvl} to {-td-tl::%{-g}%::%{-tid}%}
		set {-tt} to {-td-tt::%{-g}%::%{-tid}%}
		set {-own} to {-td-towner::%{-g}%::%{-tid}%}
		set {-tx} to {-td-tx::%{-g}%::%{-tid}%}
		set {-ty} to {-td-ty::%{-g}%::%{-tid}%}
		TdTowerRange({-tt}, {-lvl})
		TdShowRange(p, {-tx}, {-ty}, {-td-tmp-rng})

		if {-lvl} >= 5:
			set text of screen element "status_txt" for p to "&6&l✦5★ MAX &7| Владелец: &f%{-own}% &7| Дальность: &b%{-td-tmp-rng}%"
			stop
		set {-up} to 95
		if {-lvl} is 2:
			set {-up} to 210
		else if {-lvl} is 3:
			set {-up} to 360
		else if {-lvl} is 4:
			set {-up} to 570
		if {-td-gold::%p%} < {-up}:
			if {-lvl} is 4:
				set text of screen element "status_txt" for p to "&7Владелец: &f%{-own}% &7(ур.4) | Для &6✦5★ ЭВОЛЮЦИИ &7нужно &6%{-up}%з!"
			else:
				set {-nl} to {-lvl} + 1
				set text of screen element "status_txt" for p to "&7Владелец: &f%{-own}% &7(ур.%{-lvl}%) | До ур.%{-nl}% нужно &c%{-up}%з"
			play sound entity.villager.no to p with volume 0.5 pitch 1.0
			stop
		remove {-up} from {-td-gold::%p%}
		add {-up} to {-td-tcost::%{-g}%::%{-tid}%}
		add 1 to {-td-tl::%{-g}%::%{-tid}%}
		set {-nl} to {-td-tl::%{-g}%::%{-tid}%}
		TdTowerRange({-tt}, {-nl})
		TdShowRange(p, {-tx}, {-ty}, {-td-tmp-rng})
		TdSyncTowerVisual({-g}, {-tid})
		if {-nl} is 2:
			gui particle "spark" count 8 at {-tx} {-ty} color #FACC15 spread 20 to {-td-online::%{-g}%::*}
			TdSyncStatus({-g}, "&a%p% улучшил башню (%{-own}%) → ур.2! (-%{-up}%з)")
			TdSyncSound({-g}, "entity.player.levelup", 0.5, 1.35)
		else if {-nl} is 3:
			gui particle "magic" count 10 at {-tx} {-ty} spread 24 to {-td-online::%{-g}%::*}
			TdSyncStatus({-g}, "&a%p% улучшил башню (%{-own}%) → ур.3! (-%{-up}%з)")
			TdSyncSound({-g}, "entity.player.levelup", 0.5, 1.45)
		else if {-nl} is 4:
			gui particle "magic" count 12 at {-tx} {-ty} color #38BDF8 spread 28 to {-td-online::%{-g}%::*}
			TdSyncStatus({-g}, "&b%p% поднял ур.4! След. клик (570з) → &6&l✦5★ ЭВОЛЮЦИЯ!")
			TdSyncSound({-g}, "entity.player.levelup", 0.55, 1.6)
		else:
			set {-evo} to "Шквал (2 цели!)"
			if {-tt} is 2:
				set {-evo} to "Напалм (поджог + мега-сплеш!)"
			else if {-tt} is 3:
				set {-evo} to "Стужа (50%% замедл. по радиусу + 12%% фриз!)"
			else if {-tt} is 4:
				set {-evo} to "Убийца Боссов (x2 + 4%% HP!)"
			else if {-tt} is 5:
				set {-evo} to "Грозовой шторм (5 целей + стан!)"
			else if {-tt} is 6:
				set {-evo} to "Казнь (<20%% HP + крит 45%%!)"
			else if {-tt} is 7:
				set {-evo} to "Чума (заразный яд по области!)"
			else if {-tt} is 8:
				set {-evo} to "Золотой Дворец (+180з и +1 HP!)"
			gui float "&6&l✦ 5★ ЭВОЛЮЦИЯ!" at {-tx} {-ty} color #FACC15 for 24 ticks to {-td-online::%{-g}%::*}
			gui particle "gold" count 18 at {-tx} {-ty} spread 38 to {-td-online::%{-g}%::*}
			TdSyncStatus({-g}, "&6&l✦ ЭВОЛЮЦИЯ 5★! &f%p% открыл: &e%{-evo}%")
			TdSyncSound({-g}, "ui.toast.challenge_complete", 0.75, 1.2)
		TdSyncGoldScore({-g})
		stop

	# Постройка новой башни (до 28 на карте)
	set {-slot} to 0
	loop 28 times:
		set {-k} to %loop-number%
		if {-td-ta::%{-g}%::%{-k}%} is 0 and {-slot} is 0:
			set {-slot} to {-k}
	if {-slot} is 0:
		set text of screen element "status_txt" for p to "&cМаксимум 28 башен! Улучшайте и эволюционируйте до ✦5★"
		stop

	set {-tool} to {-td-tool::%p%}
	set {-cost} to 50
	if {-tool} is 2:
		set {-cost} to 95
	else if {-tool} is 3:
		set {-cost} to 85
	else if {-tool} is 4:
		set {-cost} to 130
	else if {-tool} is 5:
		set {-cost} to 115
	else if {-tool} is 6:
		set {-cost} to 150
	else if {-tool} is 7:
		set {-cost} to 105
	else if {-tool} is 8:
		set {-cost} to 140

	if {-td-gold::%p%} < {-cost}:
		TdShowRange(p, 0, 0, 0)
		set text of screen element "status_txt" for p to "&cНе хватает золота! Нужно %{-cost}%з"
		play sound entity.villager.no to p with volume 0.5 pitch 1.0
		stop

	remove {-cost} from {-td-gold::%p%}
	set {-cx} to -210 + col * 28
	set {-cy} to -100 + row * 28
	set {-td-ta::%{-g}%::%{-slot}%} to 1
	set {-td-tt::%{-g}%::%{-slot}%} to {-tool}
	set {-td-tl::%{-g}%::%{-slot}%} to 1
	set {-td-tcd::%{-g}%::%{-slot}%} to 0
	set {-td-tcost::%{-g}%::%{-slot}%} to {-cost}
	set {-td-towner::%{-g}%::%{-slot}%} to %p%
	set {-td-tcol::%{-g}%::%{-slot}%} to col
	set {-td-trow::%{-g}%::%{-slot}%} to row
	set {-td-tx::%{-g}%::%{-slot}%} to {-cx}
	set {-td-ty::%{-g}%::%{-slot}%} to {-cy}
	set {-td-grid::%{-g}%::%col%_%row%} to {-slot}
	if {-slot} > {-td-tmax::%{-g}%}:
		set {-td-tmax::%{-g}%} to {-slot}

	TdTowerRange({-tool}, 1)
	TdShowRange(p, {-cx}, {-cy}, {-td-tmp-rng})
	TdSyncTowerVisual({-g}, {-slot})
	gui particle "spark" count 8 at {-cx} {-cy} color #38BDF8 spread 22 to {-td-online::%{-g}%::*}
	TdSyncStatus({-g}, "&a%p% построил башню! Наведи курсор для статов, ЛКМ — апгрейд")
	TdSyncSound({-g}, "block.anvil.use", 0.35, 1.5)
	TdSyncGoldScore({-g})

# ==========================================================
#  ВОЛНЫ И СПАВН (13 ВИДОВ МОБОВ + СКЕЙЛИНГ НА 1..4 ИГРОКОВ)
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
			TdSyncStatus(g, "&4&lФИНАЛЬНАЯ ВОЛНА %{-w}%/%{-mw}% — ПОВЕЛИТЕЛЬ КРАЯ!")
			TdSyncSound(g, "entity.wither.spawn", 0.55, 0.85)
		else if {-bkind} >= 3:
			TdSyncStatus(g, "&b&lВОЛНА %{-w}%/%{-mw}% — ЛЕДЯНОЙ ТИТАН!")
			TdSyncSound(g, "entity.ender_dragon.growl", 0.55, 0.9)
		else:
			TdSyncStatus(g, "&c&lВОЛНА %{-w}%/%{-mw}% — ДЕМОН-БОСС!")
			TdSyncSound(g, "entity.ender_dragon.growl", 0.5, 1.1)
	else:
		TdSyncStatus(g, "&eВолна %{-w}%/%{-mw}% началась! &7(Space — x1/x2)")
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

	# 2. Враги: регенерация, горение, яд, плавное движение (tween!)
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
				TdDamageEnemy(g, {-ei}, {-td-eburn-d::%g%::%{-ei}%}, 0, 0)
			if {-td-ea::%g%::%{-ei}%} is 1 and {-td-epoison::%g%::%{-ei}%} > 0:
				remove 1 from {-td-epoison::%g%::%{-ei}%}
				TdDamageEnemy(g, {-ei}, {-td-epoison-d::%g%::%{-ei}%}, 0, 0)
			if {-td-ea::%g%::%{-ei}%} is 1:
				TdMoveEnemy(g, {-ei}, {-anim})
			if {-td-ea::%g%::%{-ei}%} is 1 and {-td-eboss::%g%::%{-ei}%} is 1:
				set {-boss-alive} to 1
				TdSyncBossBar(g, {-td-ehp::%g%::%{-ei}%}, 0)
	if {-boss-alive} is 0 and {-td-boss-vis::%g%} is 1:
		set {-td-boss-vis::%g%} to 0
		TdSyncHide(g, "boss_bar")

	# 3. Полёт прожектайлов (только если есть активные снаряды)
	if {-td-prcnt::%g%} > 0:
		TdMoveProjectiles(g, {-anim})

	# 4. Стрельба всех башен (инлайн кд + цикл только до tmax!)
	set {-tmax} to {-td-tmax::%g%}
	if {-tmax} > 0:
		loop {-tmax} times:
			set {-ti} to %loop-number%
			if {-td-ta::%g%::%{-ti}%} is 1 and {-td-tt::%g%::%{-ti}%} is not 8:
				if {-td-tcd::%g%::%{-ti}%} > 0:
					remove 1 from {-td-tcd::%g%::%{-ti}%}
				else:
					TdTowerTick(g, {-ti})

	# 5. Завершение волны + доход от всех Банков!
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
					if {-td-ta::%g%::%{-bi}%} is 1 and {-td-tt::%g%::%{-bi}%} is 8:
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
			add 100 + {-td-wave::%g%} * 15 to {-td-score::%g%}
			TdSyncHud(g)
			if {-td-wave::%g%} >= {-td-maxw::%g%}:
				TdWin(g)
			else:
				if {-bank-tot} > 0:
					gui float "&6+%{-bank-tot}%з от Банков!" at 0 -30 color #FACC15 for 24 ticks to {-td-online::%g%::*}
					TdSyncStatus(g, "&aВолна %{-td-wave::%g%}%/%{-td-maxw::%g%}% пройдена! +%{-bonus}%з &6(+%{-bank-tot}%з от Банков!)")
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

		# Скорости адаптированы под 4-tick цикл (в 2 раза реже тики -> в 2 раза длиннее шаг + 60 FPS tween!)
		set {-hp} to round((35 + {-w} * 25 + {-w} * {-w} * 2.6) * {-dmul})
		set {-spd} to 8.2 + {-w} * 0.18
		set {-rew} to 10 + round({-w} * 0.65)
		set {-cd} to 4 - floor({-w} / 10)
		if {-cd} < 2:
			set {-cd} to 2
		set {-tex} to "td:textures/gui/mob_slime.png"
		set {-reg} to 0
		set {-imm} to 0
		set {-bdmg} to 1

		set {-m10} to {-w} % 10
		if {-m10} is 2:
			set {-tex} to "td:textures/gui/mob_goblin.png"
			set {-hp} to round({-hp} * 0.7)
			set {-spd} to {-spd} + 3.3
			set {-cd} to 2
		else if {-m10} is 3:
			set {-tex} to "td:textures/gui/mob_orc.png"
			set {-hp} to round({-hp} * 1.15)
		else if {-m10} is 4:
			set {-tex} to "td:textures/gui/mob_spider.png"
			set {-hp} to round({-hp} * 0.8)
			set {-spd} to {-spd} + 2.7
			set {-cd} to 2
		else if {-m10} is 5:
			set {-tex} to "td:textures/gui/mob_knight.png"
			set {-hp} to round({-hp} * 1.35)
			set {-spd} to {-spd} - 0.7
		else if {-m10} is 6:
			set {-tex} to "td:textures/gui/mob_ghost.png"
			set {-hp} to round({-hp} * 0.88)
			set {-spd} to {-spd} + 2.4
		else if {-m10} is 7:
			set {-tex} to "td:textures/gui/mob_necromancer.png"
			set {-hp} to round({-hp} * 1.1)
			set {-reg} to round({-hp} * 0.03)
			add 4 to {-rew}
		else if {-m10} is 8:
			set {-tex} to "td:textures/gui/mob_blaze.png"
			set {-hp} to round({-hp} * 1.05)
			set {-spd} to {-spd} + 1.3
			set {-imm} to 1
			add 4 to {-rew}
		else if {-m10} is 9:
			set {-tex} to "td:textures/gui/mob_golem.png"
			set {-hp} to round({-hp} * 1.7)
			set {-spd} to {-spd} - 1.6
			add 6 to {-rew}
		else if {-m10} is 0:
			set {-tex} to "td:textures/gui/mob_ravager.png"
			set {-hp} to round({-hp} * 1.85)
			set {-spd} to {-spd} - 1.0
			set {-bdmg} to 2
			add 8 to {-rew}

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
		set {-td-eslow::%g%::%{-slot}%} to 0
		set {-td-eslow-f::%g%::%{-slot}%} to 0.6
		set {-td-efreeze::%g%::%{-slot}%} to 0
		set {-td-eburn::%g%::%{-slot}%} to 0
		set {-td-epoison::%g%::%{-slot}%} to 0
		set {-td-etex::%g%::%{-slot}%} to {-tex}
		TdSyncImg(g, "e%{-slot}%", {-tex})
		TdSyncPos(g, "e%{-slot}%", {-sx}, {-sy})
		TdSyncShow(g, "e%{-slot}%")
		set {-hy} to {-sy} - 13
		TdSyncBar(g, "ehp%{-slot}%", {-hp}, {-hp})
		TdSyncPos(g, "ehp%{-slot}%", {-sx}, {-hy})
		TdSyncShow(g, "ehp%{-slot}%")
		stop

	# Спавн Босса (слот 12 — 3 вида боссов!)
	if {-td-spawn-boss::%g%} > 0 and {-td-ea::%g%::12} is 0:
		set {-bk} to {-td-spawn-boss::%g%}
		set {-td-spawn-boss::%g%} to 0
		set {-bhp} to round((520 + {-bk} * {-bk} * 420) * {-dmul})
		set {-bspd} to 5.2 + {-bk} * 0.18
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
		set {-bhy} to {-sy} - 19
		TdSyncBar(g, "ehp12", {-bhp}, {-bhp})
		TdSyncPos(g, "ehp12", {-sx}, {-bhy})
		TdSyncShow(g, "ehp12")
		TdSyncBossBar(g, {-bhp}, {-bhp})
		TdSyncShow(g, "boss_bar")
		gui particle "explosion" count 16 at {-sx} {-sy} color #EF4444 spread 36 to {-td-online::%g%::*}

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
			gui float "&c-%{-dmg}% HP!" at {-tx} {-ty} color #EF4444 for 18 ticks to {-td-online::%g%::*}
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
	set {-hy} to {-y} - 13
	if {-td-eboss::%g%::%i%} is 1:
		set {-hy} to {-y} - 19
	TdSyncAnim(g, "ehp%i%", {-x}, {-hy}, anim)

# ==========================================================
#  БАШНИ, ЭВОЛЮЦИИ ✦5★, ЛЕТАЮЩИЕ ПРОЖЕКТАЙЛЫ И GUI-ЧАСТИЦЫ
# ==========================================================

function TdTowerTick(g: player, tid: number):
	set {-type} to {-td-tt::%g%::%tid%}
	set {-lvl} to {-td-tl::%g%::%tid%}
	set {-tx} to {-td-tx::%g%::%tid%}
	set {-ty} to {-td-ty::%g%::%tid%}
	set {-evo} to 0
	if {-lvl} >= 5:
		set {-evo} to 1

	TdTowerRange({-type}, {-lvl})
	set {-range} to {-td-tmp-rng}
	set {-r2} to {-range} * {-range}

	# Кулдауны и скорости снарядов под 4-tick цикл (ровно тот же DPS при вдвое меньшей нагрузке!)
	set {-dmg} to 15
	set {-cd} to 2
	set {-pspd} to 56
	set {-ptex} to "td:textures/gui/proj_arrow.png"
	if {-type} is 2:
		set {-dmg} to 36
		set {-cd} to 5
		set {-pspd} to 40
		set {-ptex} to "td:textures/gui/proj_cannon.png"
	else if {-type} is 3:
		set {-dmg} to 8
		set {-cd} to 4
		set {-pspd} to 48
		set {-ptex} to "td:textures/gui/proj_frost.png"
	else if {-type} is 4:
		set {-dmg} to 32
		set {-cd} to 3
		set {-pspd} to 44
		set {-ptex} to "td:textures/gui/proj_mage.png"
	else if {-type} is 5:
		set {-dmg} to 20
		set {-cd} to 3
		set {-pspd} to 60
		set {-ptex} to "td:textures/gui/proj_tesla.png"
	else if {-type} is 6:
		set {-dmg} to 56
		set {-cd} to 6
		set {-pspd} to 76
		set {-ptex} to "td:textures/gui/proj_sniper.png"
	else if {-type} is 7:
		set {-dmg} to 12
		set {-cd} to 3
		set {-pspd} to 48
		set {-ptex} to "td:textures/gui/proj_poison.png"

	set {-dmg} to round({-dmg} * (1 + ({-lvl} - 1) * 0.55))
	if {-evo} is 1:
		set {-dmg} to round({-dmg} * 1.4)

	# Ледяная башня (#3): аура замедления по всему радиусу + клиентские снежинки!
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
					TdDamageEnemy(g, {-ei}, {-dmg}, 9, {-frz})
		if {-hit-any} is 1:
			set {-td-tcd::%g%::%tid%} to {-cd}
			gui particle "ice" count 10 at {-tx} {-ty} spread 42 to {-td-online::%g%::*}
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
		gui particle "explosion" count 12 at hx hy spread 32 to {-td-online::%g%::*}
		TdSyncSound(g, "entity.generic.explode", 0.15, 1.65)
		set {-rad2} to 2304
		if evo is 1:
			set {-rad2} to 4624
			gui particle "fire" count 10 at hx hy spread 36 to {-td-online::%g%::*}
		loop 12 times:
			set {-si} to %loop-number%
			if {-td-ea::%g%::%{-si}%} is 1:
				set {-sdx} to {-td-ex::%g%::%{-si}%} - hx
				set {-sdy} to {-td-ey::%g%::%{-si}%} - hy
				if {-sdx} * {-sdx} + {-sdy} * {-sdy} <= {-rad2}:
					TdDamageEnemy(g, {-si}, dmg, 0, 0)
					if evo is 1 and {-td-ea::%g%::%{-si}%} is 1:
						set {-td-eburn::%g%::%{-si}%} to 6
						set {-td-eburn-d::%g%::%{-si}%} to round(dmg * 0.28)
	else if ptype is 4:
		set {-fdmg} to dmg
		if evo is 1:
			if {-td-eboss::%g%::%tgt%} is 1:
				set {-fdmg} to round({-fdmg} * 2.0)
			add round({-td-emhp::%g%::%tgt%} * 0.04) to {-fdmg}
		gui particle "magic" count 7 at hx hy spread 20 to {-td-online::%g%::*}
		TdSyncSound(g, "entity.illusioner.cast_spell", 0.17, 1.5)
		TdDamageEnemy(g, tgt, {-fdmg}, 0, 0)
	else if ptype is 5:
		gui particle "spark" count 10 at hx hy color #38BDF8 spread 28 to {-td-online::%g%::*}
		TdSyncSound(g, "entity.lightning_bolt.impact", 0.13, 1.9)
		set {-maxh} to 3
		set {-crad2} to 5476
		set {-cmul} to 0.75
		set {-stun} to 0
		if evo is 1:
			set {-maxh} to 5
			set {-crad2} to 9216
			set {-cmul} to 0.9
			set {-stun} to 2
		TdDamageEnemy(g, tgt, dmg, 0, {-stun})
		set {-hits} to 1
		loop 12 times:
			set {-si} to %loop-number%
			if {-si} is not tgt and {-hits} < {-maxh} and {-td-ea::%g%::%{-si}%} is 1:
				set {-sdx} to {-td-ex::%g%::%{-si}%} - hx
				set {-sdy} to {-td-ey::%g%::%{-si}%} - hy
				if {-sdx} * {-sdx} + {-sdy} * {-sdy} <= {-crad2}:
					TdDamageEnemy(g, {-si}, round(dmg * {-cmul}), 0, {-stun})
					add 1 to {-hits}
	else if ptype is 6:
		set {-fdmg} to dmg
		if evo is 1:
			chance of 45%:
				set {-fdmg} to round(dmg * 2.5)
				gui float "&c&l-%{-fdmg}% CRIT!" at hx hy color #FF4444 for 14 ticks to {-td-online::%g%::*}
				gui particle "spark" count 10 at hx hy color #EF4444 spread 26 to {-td-online::%g%::*}
			if {-td-eboss::%g%::%tgt%} is 0 and {-td-ehp::%g%::%tgt%} <= {-td-emhp::%g%::%tgt%} * 0.2:
				set {-fdmg} to 999999
				gui float "&4&l☠ КАЗНЬ!" at hx hy color #EF4444 for 16 ticks to {-td-online::%g%::*}
		else:
			chance of 25%:
				set {-fdmg} to dmg * 2
				gui float "&c-%{-fdmg}%!" at hx hy color #FF6B6B for 12 ticks to {-td-online::%g%::*}
				gui particle "spark" count 8 at hx hy color #EF4444 spread 20 to {-td-online::%g%::*}
		TdSyncSound(g, "entity.arrow.hit_player", 0.22, 1.6)
		TdDamageEnemy(g, tgt, {-fdmg}, 0, 0)
	else if ptype is 7:
		gui particle "poison" count 8 at hx hy spread 24 to {-td-online::%g%::*}
		TdSyncSound(g, "entity.splash_potion.break", 0.18, 1.4)
		TdDamageEnemy(g, tgt, dmg, 0, 0)
		if {-td-ea::%g%::%tgt%} is 1:
			set {-td-epoison::%g%::%tgt%} to 10
			set {-td-epoison-d::%g%::%tgt%} to round(dmg * 0.7)
		if evo is 1:
			loop 12 times:
				set {-si} to %loop-number%
				if {-si} is not tgt and {-td-ea::%g%::%{-si}%} is 1:
					set {-sdx} to {-td-ex::%g%::%{-si}%} - hx
					set {-sdy} to {-td-ey::%g%::%{-si}%} - hy
					if {-sdx} * {-sdx} + {-sdy} * {-sdy} <= 3364:
						set {-td-epoison::%g%::%{-si}%} to 10
						set {-td-epoison-d::%g%::%{-si}%} to round(dmg * 0.6)
	else:
		TdSyncSound(g, "entity.arrow.shoot", 0.17, 1.45)
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
		add {-rew} to {-td-score::%g%}
		gui float "&6+%{-share}%з" at {-kx} {-ky} color #FACC15 for 14 ticks to {-td-online::%g%::*}
		gui particle "gold" count 6 at {-kx} {-ky} spread 18 to {-td-online::%g%::*}
		if {-td-eboss::%g%::%i%} is 1:
			set {-td-boss-vis::%g%} to 0
			TdSyncHide(g, "boss_bar")
			gui particle "gold" count 24 at {-kx} {-ky} spread 44 to {-td-online::%g%::*}
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
	fade screen of {-td-online::%g%::*} to #FFD700 in 3 stay 4 out 14 ticks
	gui particle "gold" count 32 at 0 -10 spread 80 to {-td-online::%g%::*}
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
