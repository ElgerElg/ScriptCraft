# ==========================================================
#  td.sc — Tower Defense Ultra HD v4 (ScriptCraft 0.8.3)
#          5 уникальных HD-карт (включая Болото и Разлом с запретными зонами!)
#          9 башен 64x64 HD (включая Боевой Маяк-Баффер #9!) | Лимит 32 башни
#          16 видов противников 64x64 HD (Ассасин с уворотом, Храмовник со щитом, Вестник Бездны!)
#          Живой list-журнал событий справа | Иконка башни у курсора (вкл/выкл)
#          Авто-показ круга радиуса при наведении | 60 FPS Tween + GUI-частицы
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
			send "&eИспользование: &f/td coop <ник_игрока> &7(до 4 игроков!)" to player
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

# --- Горячие клавиши (1..9 башни, C — иконка у курсора, X — продать, Space — волна/скорость) ---

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

on key 9 in screen "Tower Defense":
	TdSelectTool(player, 9)

on key c in screen "Tower Defense":
	TdToggleCursorKey(player)

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
		TdLog({-g}, "&a⚙ Авто-волна: ВКЛ")
	else:
		set {-td-auto::%{-g}%} to 0
		TdLog({-g}, "&7⚙ Авто-волна: ВЫКЛ")

on toggle "cursortog":
	if %value% is "true":
		set {-td-cur-on::%player%} to 1
		if {-td-tool::%player%} > 0 or {-td-sell::%player%} is 1:
			show screen element "cursor_icon" to player
		set text of screen element "status_txt" for player to "&bИконка башни у курсора включена &8(C — переключить)"
	else:
		set {-td-cur-on::%player%} to 0
		hide screen element "cursor_icon" from player
		set text of screen element "status_txt" for player to "&7Иконка башни у курсора скрыта &8(C — вернуть)"

function TdToggleCursorKey(p: player):
	if {-td-cur-on::%p%} is 0:
		set {-td-cur-on::%p%} to 1
		set value of screen element "cursortog" for p to true
		if {-td-tool::%p%} > 0 or {-td-sell::%p%} is 1:
			show screen element "cursor_icon" to p
		set text of screen element "status_txt" for p to "&bИконка башни у курсора включена"
	else:
		set {-td-cur-on::%p%} to 0
		set value of screen element "cursortog" for p to false
		hide screen element "cursor_icon" from p
		set text of screen element "status_txt" for p to "&7Иконка башни у курсора выключена"
	play sound ui.button.click to p with volume 0.35 pitch 1.4

# --- Игровой цикл (x1 = 5 тиков/сек + 4-tick 60 FPS tween; x2 = 10 тиков/сек + 2-tick 60 FPS tween) ---

every 2 ticks:
	loop all players:
		if {-td-active::%loop-player%} is 1:
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
	# Сразу убираем игрока из {-td-online}, чтобы update screen и gui-эффекты больше не приходили ему!
	remove %p% from {-td-online::%{-g}%::*}
	set {-pcnt} to size of {-td-team::%{-g}%::*}
	if {-pcnt} <= 1:
		delete {-td-active::%{-g}%}
		delete {-td-over::%{-g}%}
		delete {-td-host::%p%}
		clear {-td-team::%{-g}%::*}
		clear {-td-online::%{-g}%::*}
		stop
	# В коопе сохраняем право вернуться по /td join, но не спамим повторно и не открываем экран заново
	if {-td-active::%{-g}%} is 1 and {-td-over::%{-g}%} is 0:
		if {-td-reconn::%p%} is not set:
			set {-td-reconn::%p%} to {-g}
			if p is online:
				send "&e[TD Кооп] &7Вы вышли из окна игры. Вернуться обратно: &a/td join" to p
			if size of {-td-online::%{-g}%::*} > 0:
				TdLog({-g}, "&e↩ %p% вышел")

function TdReconnect(g: player, p: player):
	set {-m} to {-td-map::%g%}
	if {-td-team::%g%::*} does not contain %p%:
		add %p% to {-td-team::%g%::*}
	if {-td-online::%g%::*} does not contain %p%:
		add %p% to {-td-online::%g%::*}
	set {-td-host::%p%} to %g%
	if {-td-gold::%p%} is not set:
		set {-td-gold::%p%} to 120
	set {-td-tool::%p%} to 0
	set {-td-sell::%p%} to 0
	set {-td-cur-on::%p%} to 1
	if {-m} is 1:
		TdOpenMap1(p)
	else if {-m} is 2:
		TdOpenMap2(p)
	else if {-m} is 3:
		TdOpenMap3(p)
	else if {-m} is 4:
		TdOpenMap4(p)
	else:
		TdOpenMap5(p)
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
			set {-hy} to {-ey} - 11
			if {-td-eboss::%g%::%{-ei}%} is 1:
				set {-hy} to {-ey} - 16
				set max of screen bar "boss_bar" for p to {-td-emhp::%g%::%{-ei}%}
				set value of screen bar "boss_bar" for p to {-td-ehp::%g%::%{-ei}%}
				show screen element "boss_bar" to p
			set max of screen bar "ehp%{-ei}%" for p to {-td-emhp::%g%::%{-ei}%}
			set value of screen bar "ehp%{-ei}%" for p to {-td-ehp::%g%::%{-ei}%}
			set position of screen element "ehp%{-ei}%" for p to {-ex} {-hy}
			show screen element "ehp%{-ei}%" to p
	TdSyncHud(g)
	TdLog(g, "&a↪ %p% в игре!")
	play sound entity.player.levelup to p with volume 0.5 pitch 1.3

# ==========================================================
#  ХЕЛПЕРЫ СИНХРОНИЗАЦИИ И ЖУРНАЛА СОБЫТИЙ (LIST)
# ==========================================================

function TdLog(g: player, msg: text):
	# Сдвигаем последние 9 строк вниз, новая запись всегда сверху (#1)!
	set {-i} to 9
	loop 8 times:
		set {-prev} to {-i} - 1
		if {-td-log::%g%::%{-prev}%} is set:
			set {-td-log::%g%::%{-i}%} to {-td-log::%g%::%{-prev}%}
		remove 1 from {-i}
	set {-td-log::%g%::1} to msg
	# Обновляем list ТОЛЬКО игрокам с открытым экраном, чтобы update screen не переоткрывал закрытый экран!
	loop {-td-online::%g%::*}:
		set {-tp} to %loop-value%
		if {-tp} is online and {-tp} has screen "Tower Defense" open:
			update screen "Tower Defense" for {-tp}:
				list from {-td-log::%g%::*} vertical no scrollbar header "&b&l⚡ СОБЫТИЯ" at 179 -8 size 104x174 anchor center layer 12 id ev_log:
					row "%loop-value%"

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

function TdSyncCell(g: player, col: integer, row: integer, cx: number, cy: number, tex: text, tip: text, hrad: number, hcol: text):
	loop {-td-online::%g%::*}:
		set {-tp} to %loop-value%
		if {-tp} is online and {-tp} has screen "Tower Defense" open:
			update screen "Tower Defense" for {-tp}:
				image "td:textures/gui/tile_build.png" at 0 0 size 22x22 texture 64x64 anchor center layer 3 id "c%col%_%row%" run TdCellClick(player, %col%, %row%) keep open tooltip "%tip%"
			set image of screen element "c%col%_%row%" for {-tp} to tex
			set position of screen element "c%col%_%row%" for {-tp} to cx cy
			if hrad > 0:
				set hover_circle of screen element "c%col%_%row%" for {-tp} to hrad color "%hcol%"
			else:
				set hover_circle of screen element "c%col%_%row%" for {-tp} to 0

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
	set {-m} to {-td-map::%g%}
	set {-mtag} to "&aДолина"
	if {-m} is 2:
		set {-mtag} to "&6Каньон"
	else if {-m} is 3:
		set {-mtag} to "&bЦитадель"
	else if {-m} is 4:
		set {-mtag} to "&2Болото"
	else if {-m} is 5:
		set {-mtag} to "&dРазлом"
	set {-pcnt} to size of {-td-team::%g%::*}
	set {-tstr} to "%{-mtag}% &8| &7Соло &7[%{-sp}%&7]"
	if {-pcnt} > 1:
		set {-names} to join {-td-team::%g%::*} with ","
		set {-tstr} to "%{-mtag}% &8| &d%{-pcnt}%игр &7[%{-sp}%&7]"
	set text of screen element "hud_wave" for {-td-online::%g%::*} to "&f%{-td-wave::%g%}%/%{-td-maxw::%g%}%"
	set text of screen element "hud_hp" for {-td-online::%g%::*} to "&c%{-td-hp::%g%}%"
	set text of screen element "hud_score" for {-td-online::%g%::*} to "&a%{-td-score::%g%}%"
	set text of screen element "hud_team" for {-td-online::%g%::*} to {-tstr}
	loop {-td-online::%g%::*}:
		set {-tp} to %loop-value%
		set text of screen element "hud_gold" for {-tp} to "&6%{-td-gold::%{-tp}%}%"

function TdShowRange(p: player, tx: number, ty: number, rng: number, isBuff: number):
	hide screen element "rng_s" from p
	hide screen element "rng_m" from p
	hide screen element "rng_l" from p
	hide screen element "rng_b" from p
	if rng <= 0:
		stop
	if isBuff is 1:
		set position of screen element "rng_b" for p to tx ty
		show screen element "rng_b" to p
		stop
	if rng >= 140:
		set position of screen element "rng_l" for p to tx ty
		show screen element "rng_l" to p
	else if rng >= 95:
		set position of screen element "rng_m" for p to tx ty
		show screen element "rng_m" to p
	else:
		set position of screen element "rng_s" for p to tx ty
		show screen element "rng_s" to p

# --- Пересчёт баффов от Боевых Маяков (#9) и обновление стильных тултипов ---

function TdRecalcBuffs(g: player):
	set {-tmax} to {-td-tmax::%g%}
	if {-tmax} <= 0:
		stop
	loop {-tmax} times:
		set {-i} to %loop-number%
		if {-td-ta::%g%::%{-i}%} is 1:
			set {-td-tbuff::%g%::%{-i}%} to 1.0
			set {-td-tfast::%g%::%{-i}%} to 0
	loop {-tmax} times:
		set {-bi} to %loop-number%
		if {-td-ta::%g%::%{-bi}%} is 1 and {-td-tt::%g%::%{-bi}%} is 9:
			set {-bl} to {-td-tl::%g%::%{-bi}%}
			set {-bx} to {-td-tx::%g%::%{-bi}%}
			set {-by} to {-td-ty::%g%::%{-bi}%}
			TdTowerRange(g, 9, {-bl})
			set {-br2} to {-td-tmp-rng} * {-td-tmp-rng}
			set {-bmul} to 1.18 + ({-bl} - 1) * 0.08
			if {-bl} >= 5:
				set {-bmul} to 1.55
			loop {-tmax} times:
				set {-ti} to %loop-number%
				if {-td-ta::%g%::%{-ti}%} is 1 and {-td-tt::%g%::%{-ti}%} is not 9 and {-td-tt::%g%::%{-ti}%} is not 8:
					set {-dx} to {-td-tx::%g%::%{-ti}%} - {-bx}
					set {-dy} to {-td-ty::%g%::%{-ti}%} - {-by}
					if {-dx} * {-dx} + {-dy} * {-dy} <= {-br2}:
						if {-bmul} > {-td-tbuff::%g%::%{-ti}%}:
							set {-td-tbuff::%g%::%{-ti}%} to {-bmul}
						if {-bl} >= 5:
							set {-td-tfast::%g%::%{-ti}%} to 1
	loop {-tmax} times:
		set {-ti} to %loop-number%
		if {-td-ta::%g%::%{-ti}%} is 1:
			TdSyncTowerVisual(g, {-ti})

function TdSyncTowerVisual(g: player, tid: number):
	set {-tc} to {-td-tcol::%g%::%tid%}
	set {-tr} to {-td-trow::%g%::%tid%}
	set {-tt} to {-td-tt::%g%::%tid%}
	set {-tl} to {-td-tl::%g%::%tid%}
	set {-tx} to {-td-tx::%g%::%tid%}
	set {-ty} to {-td-ty::%g%::%tid%}
	set {-own} to {-td-towner::%g%::%tid%}
	set {-ref} to round({-td-tcost::%g%::%tid%} * 0.7)
	set {-bmul} to {-td-tbuff::%g%::%tid%}
	if {-bmul} is not set:
		set {-bmul} to 1.0

	set {-tex} to "td:textures/gui/tower_archer.png"
	set {-tname} to "&eБаллиста"
	set {-bdmg} to 15
	set {-evod} to "Шквал (2 цели)"
	if {-tt} is 2:
		set {-tex} to "td:textures/gui/tower_cannon.png"
		set {-tname} to "&6Мортира"
		set {-bdmg} to 36
		set {-evod} to "Напалм (сплеш + ожог)"
	else if {-tt} is 3:
		set {-tex} to "td:textures/gui/tower_frost.png"
		set {-tname} to "&bКрио-Обелиск"
		set {-bdmg} to 8
		set {-evod} to "Стужа (50%% замедл. + фриз)"
	else if {-tt} is 4:
		set {-tex} to "td:textures/gui/tower_mage.png"
		set {-tname} to "&dАркан-Шпиль"
		set {-bdmg} to 32
		set {-evod} to "Убийца Боссов (x2 + 4%% HP)"
	else if {-tt} is 5:
		set {-tex} to "td:textures/gui/tower_tesla.png"
		set {-tname} to "&bПлазмо-Тесла"
		set {-bdmg} to 20
		set {-evod} to "Шторм (5 целей + стан)"
	else if {-tt} is 6:
		set {-tex} to "td:textures/gui/tower_sniper.png"
		set {-tname} to "&cРейлган"
		set {-bdmg} to 56
		set {-evod} to "Казнь (<20%% HP + крит 45%%)"
	else if {-tt} is 7:
		set {-tex} to "td:textures/gui/tower_poison.png"
		set {-tname} to "&aЧумной Реактор"
		set {-bdmg} to 12
		set {-evod} to "Чума (заразная кислота)"
	else if {-tt} is 8:
		set {-tex} to "td:textures/gui/tower_farm.png"
		set {-tname} to "&6Монетный Двор"
		set {-bdmg} to 0
		set {-evod} to "Дворец (+180з и +1 HP)"
	else if {-tt} is 9:
		set {-tex} to "td:textures/gui/tower_buffer.png"
		set {-tname} to "&dБоевой Маяк"
		set {-bdmg} to 0
		set {-evod} to "Знамя (+55%% урон, +20%% скор., +25з)"

	set {-st} to "&e★"
	if {-tl} is 2:
		set {-st} to "&6★★"
	else if {-tl} is 3:
		set {-st} to "&d★★★"
	else if {-tl} is 4:
		set {-st} to "&b★★★★"
	else if {-tl} >= 5:
		set {-st} to "&6&l✦5★"

	TdTowerRange(g, {-tt}, {-tl})
	set {-rng} to {-td-tmp-rng}

	set {-mapMul} to 1.0
	if {-td-map::%g%} is 2:
		set {-mapMul} to 1.1
	else if {-td-map::%g%} is 5:
		set {-mapMul} to 1.2

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
		set {-stat} to "&8▪ &7Доход: &6+%{-inc}%з/волну"
	else if {-tt} is 9:
		set {-bpct} to 18 + ({-tl} - 1) * 8
		if {-tl} >= 5:
			set {-bpct} to 55
		set {-stat} to "&8▪ &7Бафф-аура: &d+%{-bpct}%% урона &8| &7Радиус: &b%{-rng}%"
	else:
		set {-cdmg} to round({-bdmg} * (1 + ({-tl} - 1) * 0.55) * {-bmul} * {-mapMul})
		if {-tl} >= 5:
			set {-cdmg} to round({-cdmg} * 1.4)
		set {-bstr} to ""
		if {-bmul} > 1.01:
			set {-bp} to round(({-bmul} - 1.0) * 100)
			set {-bstr} to " &d(+%{-bp}%% Маяк)"
		if {-tt} is 3:
			set {-stat} to "&8▪ &7Аура: &b%{-cdmg}% &8| &7Радиус: &b%{-rng}%%{-bstr}%"
		else:
			set {-stat} to "&8▪ &7Урон: &f%{-cdmg}% &8| &7Дальн: &b%{-rng}%%{-bstr}%"

	if {-tl} >= 5:
		set {-act} to "&6✦ %{-evod}% &8| &cX: +%{-ref}%з"
	else if {-tl} is 4:
		set {-act} to "&6▶ ЛКМ: ✦5★ Эволюция (570з) &8| &cX: +%{-ref}%з"
	else:
		set {-up} to 95
		if {-tl} is 2:
			set {-up} to 210
		else if {-tl} is 3:
			set {-up} to 360
		set {-nl} to {-tl} + 1
		set {-act} to "&a▶ ЛКМ: Ур.%{-nl}% &6(%{-up}%з) &8| &cX: +%{-ref}%з"

	set {-tip} to "&b&l▍ %{-tname}% %{-st}% &8[Ур.%{-tl}%]%nl%%{-stat}%%nl%&8▪ &7Владелец: &f%{-own}%%nl%%{-act}%"
	set {-hc} to "#38BDF8"
	if {-tt} is 9:
		set {-hc} to "#FACC15"
	TdSyncCell(g, {-tc}, {-tr}, {-tx}, {-ty}, {-tex}, {-tip}, {-rng}, {-hc})
	set {-sy} to {-ty} + 8
	TdSyncPos(g, "st%tid%", {-tx}, {-sy})
	TdSyncTxt(g, "st%tid%", {-st})
	TdSyncShow(g, "st%tid%")

# ==========================================================
#  СОВРЕМЕННОЕ МЕНЮ: 5 HD КАРТ, СЛОЖНОСТЬ И КООП (1..4)
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
		box at 0 0 size 460x262 anchor center color #EE090E1A gradient #EE111C33 border #FF38BDF8 border_width 1 radius 10 shadow blur layer 0
		box at 0 -115 size 450x24 anchor center color #FF1D4ED8 to #FF7C3AED horizontal radius 6 layer 1
		text "&b&l✦ &f&lTOWER DEFENSE &b&lULTRA HD v4 &b&l✦" at 0 -115 anchor center shadow layer 2
		text "&75 уникальных карт | 9 башен (Маяк-Баффер!) | 16 видов врагов | Кооп: &e/td coop <ник>" at 0 -94 anchor center shadow layer 2

		# 5 карточек под HD превью карт + плавный индикатор выбора
		box at -172 -44 size 82x54 anchor center color #CC162032 border #3338BDF8 radius 5 shadow layer 1
		box at -86 -44 size 82x54 anchor center color #CC162032 border #3338BDF8 radius 5 shadow layer 1
		box at 0 -44 size 82x54 anchor center color #CC162032 border #3338BDF8 radius 5 shadow layer 1
		box at 86 -44 size 82x54 anchor center color #CC162032 border #3338BDF8 radius 5 shadow layer 1
		box at 172 -44 size 82x54 anchor center color #CC162032 border #3338BDF8 radius 5 shadow layer 1
		box at -172 -14 size 76x2 anchor center color #FF22C55E radius 1 id map_sel layer 3

		image "td:textures/gui/map_valley.png" at -172 -45 size 76x38 texture 896x448 anchor center hover_scale 1.07 layer 2 run TdPickMap(player, 1) keep open tooltip "&a&l1. Изумрудная долина%nl%&8▪ &7Маршрут: Классический S-путь%nl%&8▪ &7Все клетки свободны%nl%&a✦ Бонус: &f+20 стартового золота"
		text "&a1.Долина" at -172 -5 anchor center shadow scale 0.9 layer 2 id m_lbl1

		image "td:textures/gui/map_volcano.png" at -86 -45 size 76x38 texture 896x448 anchor center hover_scale 1.07 layer 2 run TdPickMap(player, 2) keep open tooltip "&6&l2. Лавовый каньон%nl%&8▪ &7Маршрут: Двойной зигзаг%nl%&6✦ Особенность: &f+10% урон башен%nl%&c⚠ Угроза: &fвраги на 8% быстрее"
		text "&72.Каньон" at -86 -5 anchor center shadow scale 0.9 layer 2 id m_lbl2

		image "td:textures/gui/map_ice.png" at 0 -45 size 76x38 texture 896x448 anchor center hover_scale 1.07 layer 2 run TdPickMap(player, 3) keep open tooltip "&b&l3. Ледяная цитадель%nl%&8▪ &7Маршрут: Арктический серпантин%nl%&b✦ Особенность: &f+12% дальность всех башен"
		text "&73.Цитадель" at 0 -5 anchor center shadow scale 0.9 layer 2 id m_lbl3

		image "td:textures/gui/map_swamp.png" at 86 -45 size 76x38 texture 896x448 anchor center hover_scale 1.07 layer 2 run TdPickMap(player, 4) keep open tooltip "&2&l4. Проклятое болото &e[НОВАЯ!]%nl%&c⚠ 18 клеток трясины (нельзя строить!)%nl%&6✦ Бонус: &f+20% золота за убийство мобов%nl%&c⚠ Угроза: &f+15% HP у всех врагов"
		text "&74.Болото" at 86 -5 anchor center shadow scale 0.9 layer 2 id m_lbl4

		image "td:textures/gui/map_void.png" at 172 -45 size 76x38 texture 896x448 anchor center hover_scale 1.07 layer 2 run TdPickMap(player, 5) keep open tooltip "&d&l5. Небесный разлом &e[ЭКСТРИМ!]%nl%&c⚠ 24 клетки обрыва бездны (островки!)%nl%&d✦ Бонус: &f+20% урон башен и x1.5 очков!"
		text "&75.Разлом" at 172 -5 anchor center shadow scale 0.9 layer 2 id m_lbl5

		# Блок сложности и команды
		box at 0 52 size 432x72 anchor center color #AA111827 border #2238BDF8 radius 6 layer 1
		text "&b&lСЛОЖНОСТЬ КАМПАНИИ" at 0 24 anchor center shadow layer 2
		button "&aЛёгкий (10 волн)" at -126 43 size 116x20 anchor center layer 2 run TdPickDiff(player, 1) keep open tooltip "&aЛёгкий%nl%&8▪ &710 волн | 2 босса | 15 HP | 140з"
		button "&eСредний (20 волн)" at 0 43 size 116x20 anchor center layer 2 run TdPickDiff(player, 2) keep open tooltip "&eСредний%nl%&8▪ &720 волн | 4 босса | 10 HP | 120з"
		button "&cТяжёлый (30 волн)" at 126 43 size 116x20 anchor center layer 2 run TdPickDiff(player, 3) keep open tooltip "&cТяжёлый%nl%&8▪ &730 волн | 6 боссов | 8 HP | 110з"

		text "&7Выбрано: &aДолина &7| &eСредний" at 0 68 anchor center shadow layer 2 id menu_info
		text "&dКоманда (1/4): &f%p%" at 0 80 anchor center shadow layer 2 id menu_team
		button "&a&l▶ ИГРАТЬ" at -60 107 size 128x22 anchor center layer 2 run TdStartGame(player) keep open
		button "&6Топ-5" at 74 107 size 94x22 anchor center layer 2 run TdShowTop(player) keep open
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
	set text of screen element "m_lbl1" for p to "&71.Долина"
	set text of screen element "m_lbl2" for p to "&72.Каньон"
	set text of screen element "m_lbl3" for p to "&73.Цитадель"
	set text of screen element "m_lbl4" for p to "&74.Болото"
	set text of screen element "m_lbl5" for p to "&75.Разлом"
	set {-mn} to "&aДолина (+20з)"
	if {-m} is 1:
		set text of screen element "m_lbl1" for p to "&a&l▶ 1.Долина"
		animate screen "map_sel" for p to -172 -14 in 4 ticks
	else if {-m} is 2:
		set {-mn} to "&6Каньон (+10% урон, +8% скор.)"
		set text of screen element "m_lbl2" for p to "&6&l▶ 2.Каньон"
		animate screen "map_sel" for p to -86 -14 in 4 ticks
	else if {-m} is 3:
		set {-mn} to "&bЦитадель (+12% дальность)"
		set text of screen element "m_lbl3" for p to "&b&l▶ 3.Цитадель"
		animate screen "map_sel" for p to 0 -14 in 4 ticks
	else if {-m} is 4:
		set {-mn} to "&2Болото (топи, +20% золота, +15% HP)"
		set text of screen element "m_lbl4" for p to "&2&l▶ 4.Болото"
		animate screen "map_sel" for p to 86 -14 in 4 ticks
	else:
		set {-mn} to "&dРазлом (островки, +20% урон, x1.5 очк.)"
		set text of screen element "m_lbl5" for p to "&d&l▶ 5.Разлом"
		animate screen "map_sel" for p to 172 -14 in 4 ticks
	set {-dn} to "&eСредний (20 волн)"
	if {-d} is 1:
		set {-dn} to "&aЛёгкий (10 волн)"
	else if {-d} is 3:
		set {-dn} to "&cТяжёлый (30 волн)"
	set text of screen element "menu_info" for p to "&7Карта: %{-mn}% &8| %{-dn}%"
	set {-pcnt} to size of {-td-team::%p%::*}
	set {-names} to join {-td-team::%p%::*} with ", "
	set text of screen element "menu_team" for p to "&dКоманда (%{-pcnt}%/4): &f%{-names}%"

# ==========================================================
#  ЗАПУСК ИГРЫ (1..4 ИГРОКА, 5 КАРТ, ДО 32 БАШЕН)
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

	# Бонус карты 1 (Долина): +20 стартового золота
	if {-m} is 1:
		add 20 to {-sgold}

	clear {-td-log::%host%::*}
	set {-td-log::%host%::1} to "&7Ждём 1-ю волну..."
	set {-td-log::%host%::2} to "&8Наведи на башню"
	set {-td-log::%host%::3} to "&8для круга радиуса!"

	clear {-td-grid::%host%::*}
	loop 32 times:
		set {-i} to %loop-number%
		set {-td-ta::%host%::%{-i}%} to 0
		set {-td-tt::%host%::%{-i}%} to 0
		set {-td-tl::%host%::%{-i}%} to 0
		set {-td-tcd::%host%::%{-i}%} to 0
		set {-td-tcost::%host%::%{-i}%} to 0
		set {-td-tbuff::%host%::%{-i}%} to 1.0
		set {-td-tfast::%host%::%{-i}%} to 0
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
		set {-td-edodge::%host%::%{-i}%} to 0
		set {-td-eshield::%host%::%{-i}%} to 0

	loop 6 times:
		set {-i} to %loop-number%
		set {-td-pra::%host%::%{-i}%} to 0

	TdLoadWaypoints(host, {-m})
	TdInitBlocked(host, {-m})

	loop {-td-team::%host%::*}:
		set {-tp} to %loop-value%
		set {-td-host::%{-tp}%} to %host%
		set {-td-gold::%{-tp}%} to {-sgold}
		set {-td-tool::%{-tp}%} to 0
		set {-td-sell::%{-tp}%} to 0
		set {-td-cur-on::%{-tp}%} to 1
		if {-m} is 1:
			TdOpenMap1({-tp})
		else if {-m} is 2:
			TdOpenMap2({-tp})
		else if {-m} is 3:
			TdOpenMap3({-tp})
		else if {-m} is 4:
			TdOpenMap4({-tp})
		else:
			TdOpenMap5({-tp})

	TdSyncHud(host)
	TdLog(host, "&a▶ Матч начался!")

function TdLoadWaypoints(g: player, mapId: number):
	if mapId is 1:
		set {-td-wpcnt::%g%} to 6
		set {-td-wpx::%g%::1} to -221
		set {-td-wpy::%g%::1} to -63
		set {-td-wpx::%g%::2} to 65
		set {-td-wpy::%g%::2} to -63
		set {-td-wpx::%g%::3} to 65
		set {-td-wpy::%g%::3} to -19
		set {-td-wpx::%g%::4} to -177
		set {-td-wpy::%g%::4} to -19
		set {-td-wpx::%g%::5} to -177
		set {-td-wpy::%g%::5} to 47
		set {-td-wpx::%g%::6} to 109
		set {-td-wpy::%g%::6} to 47
	else if mapId is 2:
		set {-td-wpcnt::%g%} to 8
		set {-td-wpx::%g%::1} to -221
		set {-td-wpy::%g%::1} to -85
		set {-td-wpx::%g%::2} to -133
		set {-td-wpy::%g%::2} to -85
		set {-td-wpx::%g%::3} to -133
		set {-td-wpy::%g%::3} to 47
		set {-td-wpx::%g%::4} to -23
		set {-td-wpy::%g%::4} to 47
		set {-td-wpx::%g%::5} to -23
		set {-td-wpy::%g%::5} to -63
		set {-td-wpx::%g%::6} to 65
		set {-td-wpy::%g%::6} to -63
		set {-td-wpx::%g%::7} to 65
		set {-td-wpy::%g%::7} to 25
		set {-td-wpx::%g%::8} to 109
		set {-td-wpy::%g%::8} to 25
	else if mapId is 3:
		set {-td-wpcnt::%g%} to 8
		set {-td-wpx::%g%::1} to -221
		set {-td-wpy::%g%::1} to 3
		set {-td-wpx::%g%::2} to -155
		set {-td-wpy::%g%::2} to 3
		set {-td-wpx::%g%::3} to -155
		set {-td-wpy::%g%::3} to -63
		set {-td-wpx::%g%::4} to -67
		set {-td-wpy::%g%::4} to -63
		set {-td-wpx::%g%::5} to -67
		set {-td-wpy::%g%::5} to 47
		set {-td-wpx::%g%::6} to 21
		set {-td-wpy::%g%::6} to 47
		set {-td-wpx::%g%::7} to 21
		set {-td-wpy::%g%::7} to -41
		set {-td-wpx::%g%::8} to 109
		set {-td-wpy::%g%::8} to -41
	else if mapId is 4:
		set {-td-wpcnt::%g%} to 8
		set {-td-wpx::%g%::1} to -221
		set {-td-wpy::%g%::1} to -41
		set {-td-wpx::%g%::2} to -111
		set {-td-wpy::%g%::2} to -41
		set {-td-wpx::%g%::3} to -111
		set {-td-wpy::%g%::3} to -85
		set {-td-wpx::%g%::4} to -1
		set {-td-wpy::%g%::4} to -85
		set {-td-wpx::%g%::5} to -1
		set {-td-wpy::%g%::5} to 69
		set {-td-wpx::%g%::6} to 65
		set {-td-wpy::%g%::6} to 69
		set {-td-wpx::%g%::7} to 65
		set {-td-wpy::%g%::7} to -19
		set {-td-wpx::%g%::8} to 109
		set {-td-wpy::%g%::8} to -19
	else if mapId is 5:
		set {-td-wpcnt::%g%} to 8
		set {-td-wpx::%g%::1} to -221
		set {-td-wpy::%g%::1} to 47
		set {-td-wpx::%g%::2} to -155
		set {-td-wpy::%g%::2} to 47
		set {-td-wpx::%g%::3} to -155
		set {-td-wpy::%g%::3} to -63
		set {-td-wpx::%g%::4} to -45
		set {-td-wpy::%g%::4} to -63
		set {-td-wpx::%g%::5} to -45
		set {-td-wpy::%g%::5} to 25
		set {-td-wpx::%g%::6} to 43
		set {-td-wpy::%g%::6} to 25
		set {-td-wpx::%g%::7} to 43
		set {-td-wpy::%g%::7} to -41
		set {-td-wpx::%g%::8} to 109
		set {-td-wpy::%g%::8} to -41

function TdInitBlocked(g: player, mapId: number):
	clear {-td-blk::%g%::*}
	if mapId is 4:
		set {-td-blk::%g%::1_0} to 1
		set {-td-blk::%g%::2_0} to 1
		set {-td-blk::%g%::2_4} to 1
		set {-td-blk::%g%::3_4} to 1
		set {-td-blk::%g%::3_5} to 1
		set {-td-blk::%g%::4_6} to 1
		set {-td-blk::%g%::5_6} to 1
		set {-td-blk::%g%::6_2} to 1
		set {-td-blk::%g%::6_4} to 1
		set {-td-blk::%g%::7_4} to 1
		set {-td-blk::%g%::7_5} to 1
		set {-td-blk::%g%::8_2} to 1
		set {-td-blk::%g%::8_3} to 1
		set {-td-blk::%g%::11_2} to 1
		set {-td-blk::%g%::11_3} to 1
		set {-td-blk::%g%::12_1} to 1
		set {-td-blk::%g%::14_5} to 1
		set {-td-blk::%g%::14_6} to 1
	else if mapId is 5:
		set {-td-blk::%g%::0_0} to 1
		set {-td-blk::%g%::0_1} to 1
		set {-td-blk::%g%::1_0} to 1
		set {-td-blk::%g%::1_2} to 1
		set {-td-blk::%g%::1_3} to 1
		set {-td-blk::%g%::5_3} to 1
		set {-td-blk::%g%::5_4} to 1
		set {-td-blk::%g%::5_6} to 1
		set {-td-blk::%g%::5_7} to 1
		set {-td-blk::%g%::6_3} to 1
		set {-td-blk::%g%::6_4} to 1
		set {-td-blk::%g%::6_6} to 1
		set {-td-blk::%g%::10_0} to 1
		set {-td-blk::%g%::10_2} to 1
		set {-td-blk::%g%::10_3} to 1
		set {-td-blk::%g%::10_7} to 1
		set {-td-blk::%g%::11_0} to 1
		set {-td-blk::%g%::11_7} to 1
		set {-td-blk::%g%::14_0} to 1
		set {-td-blk::%g%::14_6} to 1
		set {-td-blk::%g%::14_7} to 1
		set {-td-blk::%g%::15_0} to 1
		set {-td-blk::%g%::15_6} to 1
		set {-td-blk::%g%::15_7} to 1

function TdOpenMap1(p: player):
	set {-g} to {-td-host::%p%}
	if {-g} is not set:
		set {-g} to %p%
	open screen "Tower Defense" to p:
		# Невидимый фон за экраном — клик в пустое место или за экран отвязывает выбранную башню от курсора
		box at 0 0 size 2000x2000 anchor center color #00000000 layer -1 run TdClearTool(player) keep open
		# Верхний HUD (скруглённая стеклянная панель + пилюли показателей)
		box at 0 -114 size 468x22 anchor center color #E6090E1A gradient #E6111C33 border #FF38BDF8 border_width 1 radius 6 shadow blur layer 11
		box at -202 -114 size 54x15 anchor center color #CC162032 border #4438BDF8 radius 4 layer 11
		box at -134 -114 size 62x15 anchor center color #CC162032 border #44FACC15 radius 4 layer 11
		box at -68 -114 size 48x15 anchor center color #CC162032 border #44EF4444 radius 4 layer 11
		box at -2 -114 size 66x15 anchor center color #CC162032 border #4422C55E radius 4 layer 11
		box at 132 -114 size 190x15 anchor center color #CC162032 border #44C084FC radius 4 layer 11
		image "td:textures/gui/icon_wave.png" at -220 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&eВолна"
		text "&f0/20" at -195 -114 anchor center shadow layer 12 id hud_wave
		image "td:textures/gui/icon_coin.png" at -154 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&6Личное золото"
		text "&6120" at -126 -114 anchor center shadow layer 12 id hud_gold
		image "td:textures/gui/icon_heart.png" at -82 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&cЗдоровье базы"
		text "&c10" at -60 -114 anchor center shadow layer 12 id hud_hp
		image "td:textures/gui/icon_trophy.png" at -24 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&aСчёт"
		text "&a0" at 6 -114 anchor center shadow layer 12 id hud_score
		text "&7Соло &7[x1]" at 132 -114 anchor center shadow layer 12 id hud_team

		# Тактическая рамка + HD карта 352x176 (текстура 896x448)
		box at -56 -8 size 358x182 anchor center color #AA090E1A border #AA38BDF8 border_width 2 radius 5 shadow layer 0
		image "td:textures/gui/map_valley.png" at -56 -8 size 352x176 texture 896x448 anchor center layer 1 id map run TdClearTool(player) keep open
		bar "boss_bar" value 100 max 100 at -56 -88 size 210x8 anchor center color #FFEF4444 background #CC1A0A0A text "&c&l☠ БОСС: %value%/%max%" shadow layer 14 hidden

		# Правая панель — Журнал событий (list без скроллбара)
		box at 179 -8 size 110x182 anchor center color #E6090E1A gradient #E6111827 border #6638BDF8 border_width 1 radius 5 shadow blur layer 11
		list from {-td-log::%{-g}%::*} vertical no scrollbar header "&b&l⚡ СОБЫТИЯ" at 179 -8 size 104x174 anchor center layer 12 id ev_log:
			row "%loop-value%"

		# Тактические радар-круги радиуса башен (авто-показ при наведении и клике!)
		image "td:textures/gui/range_circle.png" at 0 0 size 154x154 texture 256x256 anchor center layer 2 id rng_s hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 208x208 texture 256x256 anchor center layer 2 id rng_m hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 310x310 texture 256x256 anchor center layer 2 id rng_l hidden
		image "td:textures/gui/range_buff.png" at 0 0 size 150x150 texture 256x256 anchor center layer 2 id rng_b hidden

		# Сетка постройки 16x8 (клетка 22x22; пустые клетки без лишних тултипов)
		image "td:textures/gui/tile_build.png" at -221 -85 size 22x22 texture 28x28 anchor center layer 3 id c0_0 run TdCellClick(player, 0, 0) keep open
		image "td:textures/gui/tile_build.png" at -199 -85 size 22x22 texture 28x28 anchor center layer 3 id c1_0 run TdCellClick(player, 1, 0) keep open
		image "td:textures/gui/tile_build.png" at -177 -85 size 22x22 texture 28x28 anchor center layer 3 id c2_0 run TdCellClick(player, 2, 0) keep open
		image "td:textures/gui/tile_build.png" at -155 -85 size 22x22 texture 28x28 anchor center layer 3 id c3_0 run TdCellClick(player, 3, 0) keep open
		image "td:textures/gui/tile_build.png" at -133 -85 size 22x22 texture 28x28 anchor center layer 3 id c4_0 run TdCellClick(player, 4, 0) keep open
		image "td:textures/gui/tile_build.png" at -111 -85 size 22x22 texture 28x28 anchor center layer 3 id c5_0 run TdCellClick(player, 5, 0) keep open
		image "td:textures/gui/tile_build.png" at -89 -85 size 22x22 texture 28x28 anchor center layer 3 id c6_0 run TdCellClick(player, 6, 0) keep open
		image "td:textures/gui/tile_build.png" at -67 -85 size 22x22 texture 28x28 anchor center layer 3 id c7_0 run TdCellClick(player, 7, 0) keep open
		image "td:textures/gui/tile_build.png" at -45 -85 size 22x22 texture 28x28 anchor center layer 3 id c8_0 run TdCellClick(player, 8, 0) keep open
		image "td:textures/gui/tile_build.png" at -23 -85 size 22x22 texture 28x28 anchor center layer 3 id c9_0 run TdCellClick(player, 9, 0) keep open
		image "td:textures/gui/tile_build.png" at -1 -85 size 22x22 texture 28x28 anchor center layer 3 id c10_0 run TdCellClick(player, 10, 0) keep open
		image "td:textures/gui/tile_build.png" at 21 -85 size 22x22 texture 28x28 anchor center layer 3 id c11_0 run TdCellClick(player, 11, 0) keep open
		image "td:textures/gui/tile_build.png" at 43 -85 size 22x22 texture 28x28 anchor center layer 3 id c12_0 run TdCellClick(player, 12, 0) keep open
		image "td:textures/gui/tile_build.png" at 65 -85 size 22x22 texture 28x28 anchor center layer 3 id c13_0 run TdCellClick(player, 13, 0) keep open
		image "td:textures/gui/tile_build.png" at 87 -85 size 22x22 texture 28x28 anchor center layer 3 id c14_0 run TdCellClick(player, 14, 0) keep open
		image "td:textures/gui/tile_build.png" at 109 -85 size 22x22 texture 28x28 anchor center layer 3 id c15_0 run TdCellClick(player, 15, 0) keep open
		image "td:textures/gui/tile_build.png" at 87 -63 size 22x22 texture 28x28 anchor center layer 3 id c14_1 run TdCellClick(player, 14, 1) keep open
		image "td:textures/gui/tile_build.png" at 109 -63 size 22x22 texture 28x28 anchor center layer 3 id c15_1 run TdCellClick(player, 15, 1) keep open
		image "td:textures/gui/tile_build.png" at -221 -41 size 22x22 texture 28x28 anchor center layer 3 id c0_2 run TdCellClick(player, 0, 2) keep open
		image "td:textures/gui/tile_build.png" at -199 -41 size 22x22 texture 28x28 anchor center layer 3 id c1_2 run TdCellClick(player, 1, 2) keep open
		image "td:textures/gui/tile_build.png" at -177 -41 size 22x22 texture 28x28 anchor center layer 3 id c2_2 run TdCellClick(player, 2, 2) keep open
		image "td:textures/gui/tile_build.png" at -155 -41 size 22x22 texture 28x28 anchor center layer 3 id c3_2 run TdCellClick(player, 3, 2) keep open
		image "td:textures/gui/tile_build.png" at -133 -41 size 22x22 texture 28x28 anchor center layer 3 id c4_2 run TdCellClick(player, 4, 2) keep open
		image "td:textures/gui/tile_build.png" at -111 -41 size 22x22 texture 28x28 anchor center layer 3 id c5_2 run TdCellClick(player, 5, 2) keep open
		image "td:textures/gui/tile_build.png" at -89 -41 size 22x22 texture 28x28 anchor center layer 3 id c6_2 run TdCellClick(player, 6, 2) keep open
		image "td:textures/gui/tile_build.png" at -67 -41 size 22x22 texture 28x28 anchor center layer 3 id c7_2 run TdCellClick(player, 7, 2) keep open
		image "td:textures/gui/tile_build.png" at -45 -41 size 22x22 texture 28x28 anchor center layer 3 id c8_2 run TdCellClick(player, 8, 2) keep open
		image "td:textures/gui/tile_build.png" at -23 -41 size 22x22 texture 28x28 anchor center layer 3 id c9_2 run TdCellClick(player, 9, 2) keep open
		image "td:textures/gui/tile_build.png" at -1 -41 size 22x22 texture 28x28 anchor center layer 3 id c10_2 run TdCellClick(player, 10, 2) keep open
		image "td:textures/gui/tile_build.png" at 21 -41 size 22x22 texture 28x28 anchor center layer 3 id c11_2 run TdCellClick(player, 11, 2) keep open
		image "td:textures/gui/tile_build.png" at 43 -41 size 22x22 texture 28x28 anchor center layer 3 id c12_2 run TdCellClick(player, 12, 2) keep open
		image "td:textures/gui/tile_build.png" at 87 -41 size 22x22 texture 28x28 anchor center layer 3 id c14_2 run TdCellClick(player, 14, 2) keep open
		image "td:textures/gui/tile_build.png" at 109 -41 size 22x22 texture 28x28 anchor center layer 3 id c15_2 run TdCellClick(player, 15, 2) keep open
		image "td:textures/gui/tile_build.png" at -221 -19 size 22x22 texture 28x28 anchor center layer 3 id c0_3 run TdCellClick(player, 0, 3) keep open
		image "td:textures/gui/tile_build.png" at -199 -19 size 22x22 texture 28x28 anchor center layer 3 id c1_3 run TdCellClick(player, 1, 3) keep open
		image "td:textures/gui/tile_build.png" at 87 -19 size 22x22 texture 28x28 anchor center layer 3 id c14_3 run TdCellClick(player, 14, 3) keep open
		image "td:textures/gui/tile_build.png" at 109 -19 size 22x22 texture 28x28 anchor center layer 3 id c15_3 run TdCellClick(player, 15, 3) keep open
		image "td:textures/gui/tile_build.png" at -221 3 size 22x22 texture 28x28 anchor center layer 3 id c0_4 run TdCellClick(player, 0, 4) keep open
		image "td:textures/gui/tile_build.png" at -199 3 size 22x22 texture 28x28 anchor center layer 3 id c1_4 run TdCellClick(player, 1, 4) keep open
		image "td:textures/gui/tile_build.png" at -155 3 size 22x22 texture 28x28 anchor center layer 3 id c3_4 run TdCellClick(player, 3, 4) keep open
		image "td:textures/gui/tile_build.png" at -133 3 size 22x22 texture 28x28 anchor center layer 3 id c4_4 run TdCellClick(player, 4, 4) keep open
		image "td:textures/gui/tile_build.png" at -111 3 size 22x22 texture 28x28 anchor center layer 3 id c5_4 run TdCellClick(player, 5, 4) keep open
		image "td:textures/gui/tile_build.png" at -89 3 size 22x22 texture 28x28 anchor center layer 3 id c6_4 run TdCellClick(player, 6, 4) keep open
		image "td:textures/gui/tile_build.png" at -67 3 size 22x22 texture 28x28 anchor center layer 3 id c7_4 run TdCellClick(player, 7, 4) keep open
		image "td:textures/gui/tile_build.png" at -45 3 size 22x22 texture 28x28 anchor center layer 3 id c8_4 run TdCellClick(player, 8, 4) keep open
		image "td:textures/gui/tile_build.png" at -23 3 size 22x22 texture 28x28 anchor center layer 3 id c9_4 run TdCellClick(player, 9, 4) keep open
		image "td:textures/gui/tile_build.png" at -1 3 size 22x22 texture 28x28 anchor center layer 3 id c10_4 run TdCellClick(player, 10, 4) keep open
		image "td:textures/gui/tile_build.png" at 21 3 size 22x22 texture 28x28 anchor center layer 3 id c11_4 run TdCellClick(player, 11, 4) keep open
		image "td:textures/gui/tile_build.png" at 43 3 size 22x22 texture 28x28 anchor center layer 3 id c12_4 run TdCellClick(player, 12, 4) keep open
		image "td:textures/gui/tile_build.png" at 65 3 size 22x22 texture 28x28 anchor center layer 3 id c13_4 run TdCellClick(player, 13, 4) keep open
		image "td:textures/gui/tile_build.png" at 87 3 size 22x22 texture 28x28 anchor center layer 3 id c14_4 run TdCellClick(player, 14, 4) keep open
		image "td:textures/gui/tile_build.png" at 109 3 size 22x22 texture 28x28 anchor center layer 3 id c15_4 run TdCellClick(player, 15, 4) keep open
		image "td:textures/gui/tile_build.png" at -221 25 size 22x22 texture 28x28 anchor center layer 3 id c0_5 run TdCellClick(player, 0, 5) keep open
		image "td:textures/gui/tile_build.png" at -199 25 size 22x22 texture 28x28 anchor center layer 3 id c1_5 run TdCellClick(player, 1, 5) keep open
		image "td:textures/gui/tile_build.png" at -155 25 size 22x22 texture 28x28 anchor center layer 3 id c3_5 run TdCellClick(player, 3, 5) keep open
		image "td:textures/gui/tile_build.png" at -133 25 size 22x22 texture 28x28 anchor center layer 3 id c4_5 run TdCellClick(player, 4, 5) keep open
		image "td:textures/gui/tile_build.png" at -111 25 size 22x22 texture 28x28 anchor center layer 3 id c5_5 run TdCellClick(player, 5, 5) keep open
		image "td:textures/gui/tile_build.png" at -89 25 size 22x22 texture 28x28 anchor center layer 3 id c6_5 run TdCellClick(player, 6, 5) keep open
		image "td:textures/gui/tile_build.png" at -67 25 size 22x22 texture 28x28 anchor center layer 3 id c7_5 run TdCellClick(player, 7, 5) keep open
		image "td:textures/gui/tile_build.png" at -45 25 size 22x22 texture 28x28 anchor center layer 3 id c8_5 run TdCellClick(player, 8, 5) keep open
		image "td:textures/gui/tile_build.png" at -23 25 size 22x22 texture 28x28 anchor center layer 3 id c9_5 run TdCellClick(player, 9, 5) keep open
		image "td:textures/gui/tile_build.png" at -1 25 size 22x22 texture 28x28 anchor center layer 3 id c10_5 run TdCellClick(player, 10, 5) keep open
		image "td:textures/gui/tile_build.png" at 21 25 size 22x22 texture 28x28 anchor center layer 3 id c11_5 run TdCellClick(player, 11, 5) keep open
		image "td:textures/gui/tile_build.png" at 43 25 size 22x22 texture 28x28 anchor center layer 3 id c12_5 run TdCellClick(player, 12, 5) keep open
		image "td:textures/gui/tile_build.png" at 65 25 size 22x22 texture 28x28 anchor center layer 3 id c13_5 run TdCellClick(player, 13, 5) keep open
		image "td:textures/gui/tile_build.png" at 87 25 size 22x22 texture 28x28 anchor center layer 3 id c14_5 run TdCellClick(player, 14, 5) keep open
		image "td:textures/gui/tile_build.png" at 109 25 size 22x22 texture 28x28 anchor center layer 3 id c15_5 run TdCellClick(player, 15, 5) keep open
		image "td:textures/gui/tile_build.png" at -221 47 size 22x22 texture 28x28 anchor center layer 3 id c0_6 run TdCellClick(player, 0, 6) keep open
		image "td:textures/gui/tile_build.png" at -199 47 size 22x22 texture 28x28 anchor center layer 3 id c1_6 run TdCellClick(player, 1, 6) keep open
		image "td:textures/gui/tile_build.png" at -221 69 size 22x22 texture 28x28 anchor center layer 3 id c0_7 run TdCellClick(player, 0, 7) keep open
		image "td:textures/gui/tile_build.png" at -199 69 size 22x22 texture 28x28 anchor center layer 3 id c1_7 run TdCellClick(player, 1, 7) keep open
		image "td:textures/gui/tile_build.png" at -177 69 size 22x22 texture 28x28 anchor center layer 3 id c2_7 run TdCellClick(player, 2, 7) keep open
		image "td:textures/gui/tile_build.png" at -155 69 size 22x22 texture 28x28 anchor center layer 3 id c3_7 run TdCellClick(player, 3, 7) keep open
		image "td:textures/gui/tile_build.png" at -133 69 size 22x22 texture 28x28 anchor center layer 3 id c4_7 run TdCellClick(player, 4, 7) keep open
		image "td:textures/gui/tile_build.png" at -111 69 size 22x22 texture 28x28 anchor center layer 3 id c5_7 run TdCellClick(player, 5, 7) keep open
		image "td:textures/gui/tile_build.png" at -89 69 size 22x22 texture 28x28 anchor center layer 3 id c6_7 run TdCellClick(player, 6, 7) keep open
		image "td:textures/gui/tile_build.png" at -67 69 size 22x22 texture 28x28 anchor center layer 3 id c7_7 run TdCellClick(player, 7, 7) keep open
		image "td:textures/gui/tile_build.png" at -45 69 size 22x22 texture 28x28 anchor center layer 3 id c8_7 run TdCellClick(player, 8, 7) keep open
		image "td:textures/gui/tile_build.png" at -23 69 size 22x22 texture 28x28 anchor center layer 3 id c9_7 run TdCellClick(player, 9, 7) keep open
		image "td:textures/gui/tile_build.png" at -1 69 size 22x22 texture 28x28 anchor center layer 3 id c10_7 run TdCellClick(player, 10, 7) keep open
		image "td:textures/gui/tile_build.png" at 21 69 size 22x22 texture 28x28 anchor center layer 3 id c11_7 run TdCellClick(player, 11, 7) keep open
		image "td:textures/gui/tile_build.png" at 43 69 size 22x22 texture 28x28 anchor center layer 3 id c12_7 run TdCellClick(player, 12, 7) keep open
		image "td:textures/gui/tile_build.png" at 65 69 size 22x22 texture 28x28 anchor center layer 3 id c13_7 run TdCellClick(player, 13, 7) keep open
		image "td:textures/gui/tile_build.png" at 87 69 size 22x22 texture 28x28 anchor center layer 3 id c14_7 run TdCellClick(player, 14, 7) keep open
		image "td:textures/gui/tile_build.png" at 109 69 size 22x22 texture 28x28 anchor center layer 3 id c15_7 run TdCellClick(player, 15, 7) keep open

		# Пул звёзд уровней и эволюций (до 32 башен)
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st1 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st2 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st3 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st4 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st5 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st6 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st7 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st8 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st9 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st10 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st11 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st12 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st13 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st14 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st15 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st16 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st17 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st18 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st19 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st20 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st21 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st22 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st23 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st24 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st25 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st26 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st27 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st28 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st29 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st30 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st31 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st32 hidden

		# 11 слотов обычных врагов + 1 слот Босса + тонкие HP-бары (64x64 HD + 60 FPS tween)
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e1 hidden
		bar "ehp1" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e2 hidden
		bar "ehp2" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e3 hidden
		bar "ehp3" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e4 hidden
		bar "ehp4" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e5 hidden
		bar "ehp5" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e6 hidden
		bar "ehp6" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e7 hidden
		bar "ehp7" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e8 hidden
		bar "ehp8" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e9 hidden
		bar "ehp9" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e10 hidden
		bar "ehp10" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e11 hidden
		bar "ehp11" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_boss.png" at -221 -63 size 28x28 texture 64x64 anchor center layer 8 id e12 hidden
		bar "ehp12" value 100 max 100 at -221 -79 size 26x4 anchor center color #FFEF4444 background #CC1A0A0A layer 9 hidden

		# 6 летающих HD прожектайлов (60 FPS tween)
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr1 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr2 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr3 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr4 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr5 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr6 hidden

		# Компактная строка статуса
		box at 0 92 size 468x13 anchor center color #E60B1220 border #3338BDF8 radius 3 layer 11
		text "&e[1] Баллиста &7— наведите на башню для радиуса и статов, ЛКМ по клетке — строить" at 0 92 anchor center shadow layer 12 id status_txt
		text "&c&lБАЗА ПАЛА!" at -56 -18 anchor center shadow scale 1.4 layer 20 id end_title hidden
		text "&fНажми &eR &f(рестарт) или &eM &f(меню)" at -56 2 anchor center shadow layer 20 id end_sub hidden

		# Нижняя панель: 9 HD башен (включая Маяк-Баффер #9) + управление и тумблер Курсора
		box at 0 121 size 468x40 anchor center color #EE090E1A gradient #EE111827 border #FF1E293B radius 6 shadow blur layer 11
		box at -218 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -186 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -154 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -122 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -90 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -58 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -26 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at 6 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at 38 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -218 106 size 26x2 anchor center color #FFFACC15 radius 1 id sel_bar hidden layer 13
		image "td:textures/gui/tower_archer.png" at -218 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e&l[1] Эльфийская Баллиста &650з%nl%&8▪ &7Урон: &f15 &8| &7КД: &a0.4с &8| &7Дальн: &b78%nl%&6✦ 5★ Шквал: &fстреляет сразу по 2 целям"
		text "&e[1]50" at -218 131 anchor center shadow scale 0.85 layer 12 id lbl1
		image "td:textures/gui/tower_cannon.png" at -186 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6&l[2] Мортира «Вулкан» &695з%nl%&8▪ &7Урон: &f36 &c(Сплеш) &8| &7Дальн: &b74%nl%&6✦ 5★ Напалм: &fмега-сплеш + поджог врагов"
		text "&7[2]95" at -186 131 anchor center shadow scale 0.85 layer 12 id lbl2
		image "td:textures/gui/tower_frost.png" at -154 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b&l[3] Крио-Обелиск &685з%nl%&8▪ &7Аура: &f8 ур. &b(40% замедл. всех в радиусе 76)%nl%&6✦ 5★ Стужа: &f50% замедл. + 12% заморозка"
		text "&7[3]85" at -154 131 anchor center shadow scale 0.85 layer 12 id lbl3
		image "td:textures/gui/tower_mage.png" at -122 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d&l[4] Аркан-Шпиль &6130з%nl%&8▪ &7Урон: &f32 &8| &7Дальн: &b106%nl%&6✦ 5★ Убийца Боссов: &fx2 Боссам + 4% макс.HP"
		text "&7[4]130" at -122 131 anchor center shadow scale 0.85 layer 12 id lbl4
		image "td:textures/gui/tower_tesla.png" at -90 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico5 run TdSelectTool(player, 5) keep open tooltip "&b&l[5] Плазмо-Тесла &6115з%nl%&8▪ &7Урон: &f20 &b(Цепь на 3 цели) &8| &7Дальн: &b86%nl%&6✦ 5★ Шторм: &f5 целей + 90% урон + микро-стан"
		text "&7[5]115" at -90 131 anchor center shadow scale 0.85 layer 12 id lbl5
		image "td:textures/gui/tower_sniper.png" at -58 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico6 run TdSelectTool(player, 6) keep open tooltip "&c&l[6] Рейлган «Сокол» &6150з%nl%&8▪ &7Урон: &f56 &c(Крит 25%) &8| &7Дальн: &b172%nl%&6✦ 5★ Казнь: &fкрит 45% x2.5 + добивание <20% HP"
		text "&7[6]150" at -58 131 anchor center shadow scale 0.85 layer 12 id lbl6
		image "td:textures/gui/tower_poison.png" at -26 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico7 run TdSelectTool(player, 7) keep open tooltip "&a&l[7] Чумной Реактор &6105з%nl%&8▪ &7Урон: &f12 + &aКислота (DOT) &8| &7Дальн: &b80%nl%&6✦ 5★ Чума: &fзаражает всех врагов рядом"
		text "&7[7]105" at -26 131 anchor center shadow scale 0.85 layer 12 id lbl7
		image "td:textures/gui/tower_farm.png" at 6 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico8 run TdSelectTool(player, 8) keep open tooltip "&6&l[8] Монетный Двор &6140з%nl%&8▪ &7Доход: &6+28/+50/+80/+120з &7за волну%nl%&6✦ 5★ Золотой Дворец: &f+180з и +1 HP базе"
		text "&7[8]140" at 6 131 anchor center shadow scale 0.85 layer 12 id lbl8
		image "td:textures/gui/tower_buffer.png" at 38 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico9 run TdSelectTool(player, 9) keep open tooltip "&d&l[9] Боевой Маяк (Баффер) &6125з%nl%&8▪ &7Бафф-аура: &d+18..42% урона &7башням в радиусе 68%nl%&6✦ 5★ Знамя Империи: &f+55% урон, +20% скоростр. и +25з"
		text "&7[9]125" at 38 131 anchor center shadow scale 0.85 layer 12 id lbl9
		button "&aСтарт/x2" at 94 112 size 56x15 anchor center run TdWaveOrSpeed(player) keep open tooltip "&aСтарт волны / Скорость x1↔x2 &8[Space]"
		toggle "Авто" id autowave at 152 112 size 52x15 anchor center value false color #22C55E tooltip "&aАвто-старт волн"
		toggle "Курсор" id cursortog at 208 112 size 52x15 anchor center value true color #38BDF8 tooltip "&bИконка выбранной башни у курсора &8[C]"
		button "&cПродать" at 94 129 size 56x15 anchor center run TdToggleSell(player) keep open tooltip "&cПродать башню за 70% &8[X]"
		button "Заново" at 152 129 size 52x15 anchor center run TdRestartCurrent(player) keep open tooltip "&eРестарт карты &8[R]"
		button "Меню" at 208 129 size 52x15 anchor center run TdOpenMenu(player) keep open tooltip "&eВыбор карты и кооп &8[M]"

		# Привязанная к курсору иконка текущей башни (универсальное свойство follow cursor)
		image "td:textures/gui/tower_archer.png" at 0 0 size 15x15 texture 64x64 anchor center follow cursor layer 24 id cursor_icon hidden

function TdOpenMap2(p: player):
	set {-g} to {-td-host::%p%}
	if {-g} is not set:
		set {-g} to %p%
	open screen "Tower Defense" to p:
		# Невидимый фон за экраном — клик в пустое место или за экран отвязывает выбранную башню от курсора
		box at 0 0 size 2000x2000 anchor center color #00000000 layer -1 run TdClearTool(player) keep open
		# Верхний HUD (скруглённая стеклянная панель + пилюли показателей)
		box at 0 -114 size 468x22 anchor center color #E6090E1A gradient #E6111C33 border #FF38BDF8 border_width 1 radius 6 shadow blur layer 11
		box at -202 -114 size 54x15 anchor center color #CC162032 border #4438BDF8 radius 4 layer 11
		box at -134 -114 size 62x15 anchor center color #CC162032 border #44FACC15 radius 4 layer 11
		box at -68 -114 size 48x15 anchor center color #CC162032 border #44EF4444 radius 4 layer 11
		box at -2 -114 size 66x15 anchor center color #CC162032 border #4422C55E radius 4 layer 11
		box at 132 -114 size 190x15 anchor center color #CC162032 border #44C084FC radius 4 layer 11
		image "td:textures/gui/icon_wave.png" at -220 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&eВолна"
		text "&f0/20" at -195 -114 anchor center shadow layer 12 id hud_wave
		image "td:textures/gui/icon_coin.png" at -154 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&6Личное золото"
		text "&6120" at -126 -114 anchor center shadow layer 12 id hud_gold
		image "td:textures/gui/icon_heart.png" at -82 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&cЗдоровье базы"
		text "&c10" at -60 -114 anchor center shadow layer 12 id hud_hp
		image "td:textures/gui/icon_trophy.png" at -24 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&aСчёт"
		text "&a0" at 6 -114 anchor center shadow layer 12 id hud_score
		text "&7Соло &7[x1]" at 132 -114 anchor center shadow layer 12 id hud_team

		# Тактическая рамка + HD карта 352x176 (текстура 896x448)
		box at -56 -8 size 358x182 anchor center color #AA090E1A border #AA38BDF8 border_width 2 radius 5 shadow layer 0
		image "td:textures/gui/map_volcano.png" at -56 -8 size 352x176 texture 896x448 anchor center layer 1 id map run TdClearTool(player) keep open
		bar "boss_bar" value 100 max 100 at -56 -88 size 210x8 anchor center color #FFEF4444 background #CC1A0A0A text "&c&l☠ БОСС: %value%/%max%" shadow layer 14 hidden

		# Правая панель — Журнал событий (list без скроллбара)
		box at 179 -8 size 110x182 anchor center color #E6090E1A gradient #E6111827 border #6638BDF8 border_width 1 radius 5 shadow blur layer 11
		list from {-td-log::%{-g}%::*} vertical no scrollbar header "&b&l⚡ СОБЫТИЯ" at 179 -8 size 104x174 anchor center layer 12 id ev_log:
			row "%loop-value%"

		# Тактические радар-круги радиуса башен (авто-показ при наведении и клике!)
		image "td:textures/gui/range_circle.png" at 0 0 size 154x154 texture 256x256 anchor center layer 2 id rng_s hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 208x208 texture 256x256 anchor center layer 2 id rng_m hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 310x310 texture 256x256 anchor center layer 2 id rng_l hidden
		image "td:textures/gui/range_buff.png" at 0 0 size 150x150 texture 256x256 anchor center layer 2 id rng_b hidden

		# Сетка постройки 16x8 (клетка 22x22; пустые клетки без лишних тултипов)
		image "td:textures/gui/tile_build.png" at -111 -85 size 22x22 texture 28x28 anchor center layer 3 id c5_0 run TdCellClick(player, 5, 0) keep open
		image "td:textures/gui/tile_build.png" at -89 -85 size 22x22 texture 28x28 anchor center layer 3 id c6_0 run TdCellClick(player, 6, 0) keep open
		image "td:textures/gui/tile_build.png" at -67 -85 size 22x22 texture 28x28 anchor center layer 3 id c7_0 run TdCellClick(player, 7, 0) keep open
		image "td:textures/gui/tile_build.png" at -45 -85 size 22x22 texture 28x28 anchor center layer 3 id c8_0 run TdCellClick(player, 8, 0) keep open
		image "td:textures/gui/tile_build.png" at -23 -85 size 22x22 texture 28x28 anchor center layer 3 id c9_0 run TdCellClick(player, 9, 0) keep open
		image "td:textures/gui/tile_build.png" at -1 -85 size 22x22 texture 28x28 anchor center layer 3 id c10_0 run TdCellClick(player, 10, 0) keep open
		image "td:textures/gui/tile_build.png" at 21 -85 size 22x22 texture 28x28 anchor center layer 3 id c11_0 run TdCellClick(player, 11, 0) keep open
		image "td:textures/gui/tile_build.png" at 43 -85 size 22x22 texture 28x28 anchor center layer 3 id c12_0 run TdCellClick(player, 12, 0) keep open
		image "td:textures/gui/tile_build.png" at 65 -85 size 22x22 texture 28x28 anchor center layer 3 id c13_0 run TdCellClick(player, 13, 0) keep open
		image "td:textures/gui/tile_build.png" at 87 -85 size 22x22 texture 28x28 anchor center layer 3 id c14_0 run TdCellClick(player, 14, 0) keep open
		image "td:textures/gui/tile_build.png" at 109 -85 size 22x22 texture 28x28 anchor center layer 3 id c15_0 run TdCellClick(player, 15, 0) keep open
		image "td:textures/gui/tile_build.png" at -221 -63 size 22x22 texture 28x28 anchor center layer 3 id c0_1 run TdCellClick(player, 0, 1) keep open
		image "td:textures/gui/tile_build.png" at -199 -63 size 22x22 texture 28x28 anchor center layer 3 id c1_1 run TdCellClick(player, 1, 1) keep open
		image "td:textures/gui/tile_build.png" at -177 -63 size 22x22 texture 28x28 anchor center layer 3 id c2_1 run TdCellClick(player, 2, 1) keep open
		image "td:textures/gui/tile_build.png" at -155 -63 size 22x22 texture 28x28 anchor center layer 3 id c3_1 run TdCellClick(player, 3, 1) keep open
		image "td:textures/gui/tile_build.png" at -111 -63 size 22x22 texture 28x28 anchor center layer 3 id c5_1 run TdCellClick(player, 5, 1) keep open
		image "td:textures/gui/tile_build.png" at -89 -63 size 22x22 texture 28x28 anchor center layer 3 id c6_1 run TdCellClick(player, 6, 1) keep open
		image "td:textures/gui/tile_build.png" at -67 -63 size 22x22 texture 28x28 anchor center layer 3 id c7_1 run TdCellClick(player, 7, 1) keep open
		image "td:textures/gui/tile_build.png" at -45 -63 size 22x22 texture 28x28 anchor center layer 3 id c8_1 run TdCellClick(player, 8, 1) keep open
		image "td:textures/gui/tile_build.png" at 87 -63 size 22x22 texture 28x28 anchor center layer 3 id c14_1 run TdCellClick(player, 14, 1) keep open
		image "td:textures/gui/tile_build.png" at 109 -63 size 22x22 texture 28x28 anchor center layer 3 id c15_1 run TdCellClick(player, 15, 1) keep open
		image "td:textures/gui/tile_build.png" at -221 -41 size 22x22 texture 28x28 anchor center layer 3 id c0_2 run TdCellClick(player, 0, 2) keep open
		image "td:textures/gui/tile_build.png" at -199 -41 size 22x22 texture 28x28 anchor center layer 3 id c1_2 run TdCellClick(player, 1, 2) keep open
		image "td:textures/gui/tile_build.png" at -177 -41 size 22x22 texture 28x28 anchor center layer 3 id c2_2 run TdCellClick(player, 2, 2) keep open
		image "td:textures/gui/tile_build.png" at -155 -41 size 22x22 texture 28x28 anchor center layer 3 id c3_2 run TdCellClick(player, 3, 2) keep open
		image "td:textures/gui/tile_build.png" at -111 -41 size 22x22 texture 28x28 anchor center layer 3 id c5_2 run TdCellClick(player, 5, 2) keep open
		image "td:textures/gui/tile_build.png" at -89 -41 size 22x22 texture 28x28 anchor center layer 3 id c6_2 run TdCellClick(player, 6, 2) keep open
		image "td:textures/gui/tile_build.png" at -67 -41 size 22x22 texture 28x28 anchor center layer 3 id c7_2 run TdCellClick(player, 7, 2) keep open
		image "td:textures/gui/tile_build.png" at -45 -41 size 22x22 texture 28x28 anchor center layer 3 id c8_2 run TdCellClick(player, 8, 2) keep open
		image "td:textures/gui/tile_build.png" at -1 -41 size 22x22 texture 28x28 anchor center layer 3 id c10_2 run TdCellClick(player, 10, 2) keep open
		image "td:textures/gui/tile_build.png" at 21 -41 size 22x22 texture 28x28 anchor center layer 3 id c11_2 run TdCellClick(player, 11, 2) keep open
		image "td:textures/gui/tile_build.png" at 43 -41 size 22x22 texture 28x28 anchor center layer 3 id c12_2 run TdCellClick(player, 12, 2) keep open
		image "td:textures/gui/tile_build.png" at 87 -41 size 22x22 texture 28x28 anchor center layer 3 id c14_2 run TdCellClick(player, 14, 2) keep open
		image "td:textures/gui/tile_build.png" at 109 -41 size 22x22 texture 28x28 anchor center layer 3 id c15_2 run TdCellClick(player, 15, 2) keep open
		image "td:textures/gui/tile_build.png" at -221 -19 size 22x22 texture 28x28 anchor center layer 3 id c0_3 run TdCellClick(player, 0, 3) keep open
		image "td:textures/gui/tile_build.png" at -199 -19 size 22x22 texture 28x28 anchor center layer 3 id c1_3 run TdCellClick(player, 1, 3) keep open
		image "td:textures/gui/tile_build.png" at -177 -19 size 22x22 texture 28x28 anchor center layer 3 id c2_3 run TdCellClick(player, 2, 3) keep open
		image "td:textures/gui/tile_build.png" at -155 -19 size 22x22 texture 28x28 anchor center layer 3 id c3_3 run TdCellClick(player, 3, 3) keep open
		image "td:textures/gui/tile_build.png" at -111 -19 size 22x22 texture 28x28 anchor center layer 3 id c5_3 run TdCellClick(player, 5, 3) keep open
		image "td:textures/gui/tile_build.png" at -89 -19 size 22x22 texture 28x28 anchor center layer 3 id c6_3 run TdCellClick(player, 6, 3) keep open
		image "td:textures/gui/tile_build.png" at -67 -19 size 22x22 texture 28x28 anchor center layer 3 id c7_3 run TdCellClick(player, 7, 3) keep open
		image "td:textures/gui/tile_build.png" at -45 -19 size 22x22 texture 28x28 anchor center layer 3 id c8_3 run TdCellClick(player, 8, 3) keep open
		image "td:textures/gui/tile_build.png" at -1 -19 size 22x22 texture 28x28 anchor center layer 3 id c10_3 run TdCellClick(player, 10, 3) keep open
		image "td:textures/gui/tile_build.png" at 21 -19 size 22x22 texture 28x28 anchor center layer 3 id c11_3 run TdCellClick(player, 11, 3) keep open
		image "td:textures/gui/tile_build.png" at 43 -19 size 22x22 texture 28x28 anchor center layer 3 id c12_3 run TdCellClick(player, 12, 3) keep open
		image "td:textures/gui/tile_build.png" at 87 -19 size 22x22 texture 28x28 anchor center layer 3 id c14_3 run TdCellClick(player, 14, 3) keep open
		image "td:textures/gui/tile_build.png" at 109 -19 size 22x22 texture 28x28 anchor center layer 3 id c15_3 run TdCellClick(player, 15, 3) keep open
		image "td:textures/gui/tile_build.png" at -221 3 size 22x22 texture 28x28 anchor center layer 3 id c0_4 run TdCellClick(player, 0, 4) keep open
		image "td:textures/gui/tile_build.png" at -199 3 size 22x22 texture 28x28 anchor center layer 3 id c1_4 run TdCellClick(player, 1, 4) keep open
		image "td:textures/gui/tile_build.png" at -177 3 size 22x22 texture 28x28 anchor center layer 3 id c2_4 run TdCellClick(player, 2, 4) keep open
		image "td:textures/gui/tile_build.png" at -155 3 size 22x22 texture 28x28 anchor center layer 3 id c3_4 run TdCellClick(player, 3, 4) keep open
		image "td:textures/gui/tile_build.png" at -111 3 size 22x22 texture 28x28 anchor center layer 3 id c5_4 run TdCellClick(player, 5, 4) keep open
		image "td:textures/gui/tile_build.png" at -89 3 size 22x22 texture 28x28 anchor center layer 3 id c6_4 run TdCellClick(player, 6, 4) keep open
		image "td:textures/gui/tile_build.png" at -67 3 size 22x22 texture 28x28 anchor center layer 3 id c7_4 run TdCellClick(player, 7, 4) keep open
		image "td:textures/gui/tile_build.png" at -45 3 size 22x22 texture 28x28 anchor center layer 3 id c8_4 run TdCellClick(player, 8, 4) keep open
		image "td:textures/gui/tile_build.png" at -1 3 size 22x22 texture 28x28 anchor center layer 3 id c10_4 run TdCellClick(player, 10, 4) keep open
		image "td:textures/gui/tile_build.png" at 21 3 size 22x22 texture 28x28 anchor center layer 3 id c11_4 run TdCellClick(player, 11, 4) keep open
		image "td:textures/gui/tile_build.png" at 43 3 size 22x22 texture 28x28 anchor center layer 3 id c12_4 run TdCellClick(player, 12, 4) keep open
		image "td:textures/gui/tile_build.png" at 87 3 size 22x22 texture 28x28 anchor center layer 3 id c14_4 run TdCellClick(player, 14, 4) keep open
		image "td:textures/gui/tile_build.png" at 109 3 size 22x22 texture 28x28 anchor center layer 3 id c15_4 run TdCellClick(player, 15, 4) keep open
		image "td:textures/gui/tile_build.png" at -221 25 size 22x22 texture 28x28 anchor center layer 3 id c0_5 run TdCellClick(player, 0, 5) keep open
		image "td:textures/gui/tile_build.png" at -199 25 size 22x22 texture 28x28 anchor center layer 3 id c1_5 run TdCellClick(player, 1, 5) keep open
		image "td:textures/gui/tile_build.png" at -177 25 size 22x22 texture 28x28 anchor center layer 3 id c2_5 run TdCellClick(player, 2, 5) keep open
		image "td:textures/gui/tile_build.png" at -155 25 size 22x22 texture 28x28 anchor center layer 3 id c3_5 run TdCellClick(player, 3, 5) keep open
		image "td:textures/gui/tile_build.png" at -111 25 size 22x22 texture 28x28 anchor center layer 3 id c5_5 run TdCellClick(player, 5, 5) keep open
		image "td:textures/gui/tile_build.png" at -89 25 size 22x22 texture 28x28 anchor center layer 3 id c6_5 run TdCellClick(player, 6, 5) keep open
		image "td:textures/gui/tile_build.png" at -67 25 size 22x22 texture 28x28 anchor center layer 3 id c7_5 run TdCellClick(player, 7, 5) keep open
		image "td:textures/gui/tile_build.png" at -45 25 size 22x22 texture 28x28 anchor center layer 3 id c8_5 run TdCellClick(player, 8, 5) keep open
		image "td:textures/gui/tile_build.png" at -1 25 size 22x22 texture 28x28 anchor center layer 3 id c10_5 run TdCellClick(player, 10, 5) keep open
		image "td:textures/gui/tile_build.png" at 21 25 size 22x22 texture 28x28 anchor center layer 3 id c11_5 run TdCellClick(player, 11, 5) keep open
		image "td:textures/gui/tile_build.png" at 43 25 size 22x22 texture 28x28 anchor center layer 3 id c12_5 run TdCellClick(player, 12, 5) keep open
		image "td:textures/gui/tile_build.png" at -221 47 size 22x22 texture 28x28 anchor center layer 3 id c0_6 run TdCellClick(player, 0, 6) keep open
		image "td:textures/gui/tile_build.png" at -199 47 size 22x22 texture 28x28 anchor center layer 3 id c1_6 run TdCellClick(player, 1, 6) keep open
		image "td:textures/gui/tile_build.png" at -177 47 size 22x22 texture 28x28 anchor center layer 3 id c2_6 run TdCellClick(player, 2, 6) keep open
		image "td:textures/gui/tile_build.png" at -155 47 size 22x22 texture 28x28 anchor center layer 3 id c3_6 run TdCellClick(player, 3, 6) keep open
		image "td:textures/gui/tile_build.png" at -1 47 size 22x22 texture 28x28 anchor center layer 3 id c10_6 run TdCellClick(player, 10, 6) keep open
		image "td:textures/gui/tile_build.png" at 21 47 size 22x22 texture 28x28 anchor center layer 3 id c11_6 run TdCellClick(player, 11, 6) keep open
		image "td:textures/gui/tile_build.png" at 43 47 size 22x22 texture 28x28 anchor center layer 3 id c12_6 run TdCellClick(player, 12, 6) keep open
		image "td:textures/gui/tile_build.png" at 65 47 size 22x22 texture 28x28 anchor center layer 3 id c13_6 run TdCellClick(player, 13, 6) keep open
		image "td:textures/gui/tile_build.png" at 87 47 size 22x22 texture 28x28 anchor center layer 3 id c14_6 run TdCellClick(player, 14, 6) keep open
		image "td:textures/gui/tile_build.png" at 109 47 size 22x22 texture 28x28 anchor center layer 3 id c15_6 run TdCellClick(player, 15, 6) keep open
		image "td:textures/gui/tile_build.png" at -221 69 size 22x22 texture 28x28 anchor center layer 3 id c0_7 run TdCellClick(player, 0, 7) keep open
		image "td:textures/gui/tile_build.png" at -199 69 size 22x22 texture 28x28 anchor center layer 3 id c1_7 run TdCellClick(player, 1, 7) keep open
		image "td:textures/gui/tile_build.png" at -177 69 size 22x22 texture 28x28 anchor center layer 3 id c2_7 run TdCellClick(player, 2, 7) keep open
		image "td:textures/gui/tile_build.png" at -155 69 size 22x22 texture 28x28 anchor center layer 3 id c3_7 run TdCellClick(player, 3, 7) keep open
		image "td:textures/gui/tile_build.png" at -133 69 size 22x22 texture 28x28 anchor center layer 3 id c4_7 run TdCellClick(player, 4, 7) keep open
		image "td:textures/gui/tile_build.png" at -111 69 size 22x22 texture 28x28 anchor center layer 3 id c5_7 run TdCellClick(player, 5, 7) keep open
		image "td:textures/gui/tile_build.png" at -89 69 size 22x22 texture 28x28 anchor center layer 3 id c6_7 run TdCellClick(player, 6, 7) keep open
		image "td:textures/gui/tile_build.png" at -67 69 size 22x22 texture 28x28 anchor center layer 3 id c7_7 run TdCellClick(player, 7, 7) keep open
		image "td:textures/gui/tile_build.png" at -45 69 size 22x22 texture 28x28 anchor center layer 3 id c8_7 run TdCellClick(player, 8, 7) keep open
		image "td:textures/gui/tile_build.png" at -23 69 size 22x22 texture 28x28 anchor center layer 3 id c9_7 run TdCellClick(player, 9, 7) keep open
		image "td:textures/gui/tile_build.png" at -1 69 size 22x22 texture 28x28 anchor center layer 3 id c10_7 run TdCellClick(player, 10, 7) keep open
		image "td:textures/gui/tile_build.png" at 21 69 size 22x22 texture 28x28 anchor center layer 3 id c11_7 run TdCellClick(player, 11, 7) keep open
		image "td:textures/gui/tile_build.png" at 43 69 size 22x22 texture 28x28 anchor center layer 3 id c12_7 run TdCellClick(player, 12, 7) keep open
		image "td:textures/gui/tile_build.png" at 65 69 size 22x22 texture 28x28 anchor center layer 3 id c13_7 run TdCellClick(player, 13, 7) keep open
		image "td:textures/gui/tile_build.png" at 87 69 size 22x22 texture 28x28 anchor center layer 3 id c14_7 run TdCellClick(player, 14, 7) keep open
		image "td:textures/gui/tile_build.png" at 109 69 size 22x22 texture 28x28 anchor center layer 3 id c15_7 run TdCellClick(player, 15, 7) keep open

		# Пул звёзд уровней и эволюций (до 32 башен)
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st1 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st2 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st3 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st4 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st5 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st6 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st7 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st8 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st9 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st10 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st11 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st12 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st13 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st14 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st15 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st16 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st17 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st18 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st19 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st20 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st21 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st22 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st23 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st24 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st25 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st26 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st27 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st28 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st29 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st30 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st31 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st32 hidden

		# 11 слотов обычных врагов + 1 слот Босса + тонкие HP-бары (64x64 HD + 60 FPS tween)
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e1 hidden
		bar "ehp1" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e2 hidden
		bar "ehp2" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e3 hidden
		bar "ehp3" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e4 hidden
		bar "ehp4" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e5 hidden
		bar "ehp5" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e6 hidden
		bar "ehp6" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e7 hidden
		bar "ehp7" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e8 hidden
		bar "ehp8" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e9 hidden
		bar "ehp9" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e10 hidden
		bar "ehp10" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e11 hidden
		bar "ehp11" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_boss.png" at -221 -63 size 28x28 texture 64x64 anchor center layer 8 id e12 hidden
		bar "ehp12" value 100 max 100 at -221 -79 size 26x4 anchor center color #FFEF4444 background #CC1A0A0A layer 9 hidden

		# 6 летающих HD прожектайлов (60 FPS tween)
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr1 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr2 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr3 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr4 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr5 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr6 hidden

		# Компактная строка статуса
		box at 0 92 size 468x13 anchor center color #E60B1220 border #3338BDF8 radius 3 layer 11
		text "&e[1] Баллиста &7— наведите на башню для радиуса и статов, ЛКМ по клетке — строить" at 0 92 anchor center shadow layer 12 id status_txt
		text "&c&lБАЗА ПАЛА!" at -56 -18 anchor center shadow scale 1.4 layer 20 id end_title hidden
		text "&fНажми &eR &f(рестарт) или &eM &f(меню)" at -56 2 anchor center shadow layer 20 id end_sub hidden

		# Нижняя панель: 9 HD башен (включая Маяк-Баффер #9) + управление и тумблер Курсора
		box at 0 121 size 468x40 anchor center color #EE090E1A gradient #EE111827 border #FF1E293B radius 6 shadow blur layer 11
		box at -218 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -186 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -154 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -122 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -90 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -58 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -26 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at 6 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at 38 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -218 106 size 26x2 anchor center color #FFFACC15 radius 1 id sel_bar hidden layer 13
		image "td:textures/gui/tower_archer.png" at -218 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e&l[1] Эльфийская Баллиста &650з%nl%&8▪ &7Урон: &f15 &8| &7КД: &a0.4с &8| &7Дальн: &b78%nl%&6✦ 5★ Шквал: &fстреляет сразу по 2 целям"
		text "&e[1]50" at -218 131 anchor center shadow scale 0.85 layer 12 id lbl1
		image "td:textures/gui/tower_cannon.png" at -186 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6&l[2] Мортира «Вулкан» &695з%nl%&8▪ &7Урон: &f36 &c(Сплеш) &8| &7Дальн: &b74%nl%&6✦ 5★ Напалм: &fмега-сплеш + поджог врагов"
		text "&7[2]95" at -186 131 anchor center shadow scale 0.85 layer 12 id lbl2
		image "td:textures/gui/tower_frost.png" at -154 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b&l[3] Крио-Обелиск &685з%nl%&8▪ &7Аура: &f8 ур. &b(40% замедл. всех в радиусе 76)%nl%&6✦ 5★ Стужа: &f50% замедл. + 12% заморозка"
		text "&7[3]85" at -154 131 anchor center shadow scale 0.85 layer 12 id lbl3
		image "td:textures/gui/tower_mage.png" at -122 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d&l[4] Аркан-Шпиль &6130з%nl%&8▪ &7Урон: &f32 &8| &7Дальн: &b106%nl%&6✦ 5★ Убийца Боссов: &fx2 Боссам + 4% макс.HP"
		text "&7[4]130" at -122 131 anchor center shadow scale 0.85 layer 12 id lbl4
		image "td:textures/gui/tower_tesla.png" at -90 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico5 run TdSelectTool(player, 5) keep open tooltip "&b&l[5] Плазмо-Тесла &6115з%nl%&8▪ &7Урон: &f20 &b(Цепь на 3 цели) &8| &7Дальн: &b86%nl%&6✦ 5★ Шторм: &f5 целей + 90% урон + микро-стан"
		text "&7[5]115" at -90 131 anchor center shadow scale 0.85 layer 12 id lbl5
		image "td:textures/gui/tower_sniper.png" at -58 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico6 run TdSelectTool(player, 6) keep open tooltip "&c&l[6] Рейлган «Сокол» &6150з%nl%&8▪ &7Урон: &f56 &c(Крит 25%) &8| &7Дальн: &b172%nl%&6✦ 5★ Казнь: &fкрит 45% x2.5 + добивание <20% HP"
		text "&7[6]150" at -58 131 anchor center shadow scale 0.85 layer 12 id lbl6
		image "td:textures/gui/tower_poison.png" at -26 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico7 run TdSelectTool(player, 7) keep open tooltip "&a&l[7] Чумной Реактор &6105з%nl%&8▪ &7Урон: &f12 + &aКислота (DOT) &8| &7Дальн: &b80%nl%&6✦ 5★ Чума: &fзаражает всех врагов рядом"
		text "&7[7]105" at -26 131 anchor center shadow scale 0.85 layer 12 id lbl7
		image "td:textures/gui/tower_farm.png" at 6 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico8 run TdSelectTool(player, 8) keep open tooltip "&6&l[8] Монетный Двор &6140з%nl%&8▪ &7Доход: &6+28/+50/+80/+120з &7за волну%nl%&6✦ 5★ Золотой Дворец: &f+180з и +1 HP базе"
		text "&7[8]140" at 6 131 anchor center shadow scale 0.85 layer 12 id lbl8
		image "td:textures/gui/tower_buffer.png" at 38 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico9 run TdSelectTool(player, 9) keep open tooltip "&d&l[9] Боевой Маяк (Баффер) &6125з%nl%&8▪ &7Бафф-аура: &d+18..42% урона &7башням в радиусе 68%nl%&6✦ 5★ Знамя Империи: &f+55% урон, +20% скоростр. и +25з"
		text "&7[9]125" at 38 131 anchor center shadow scale 0.85 layer 12 id lbl9
		button "&aСтарт/x2" at 94 112 size 56x15 anchor center run TdWaveOrSpeed(player) keep open tooltip "&aСтарт волны / Скорость x1↔x2 &8[Space]"
		toggle "Авто" id autowave at 152 112 size 52x15 anchor center value false color #22C55E tooltip "&aАвто-старт волн"
		toggle "Курсор" id cursortog at 208 112 size 52x15 anchor center value true color #38BDF8 tooltip "&bИконка выбранной башни у курсора &8[C]"
		button "&cПродать" at 94 129 size 56x15 anchor center run TdToggleSell(player) keep open tooltip "&cПродать башню за 70% &8[X]"
		button "Заново" at 152 129 size 52x15 anchor center run TdRestartCurrent(player) keep open tooltip "&eРестарт карты &8[R]"
		button "Меню" at 208 129 size 52x15 anchor center run TdOpenMenu(player) keep open tooltip "&eВыбор карты и кооп &8[M]"

		# Привязанная к курсору иконка текущей башни (универсальное свойство follow cursor)
		image "td:textures/gui/tower_archer.png" at 0 0 size 15x15 texture 64x64 anchor center follow cursor layer 24 id cursor_icon hidden

function TdOpenMap3(p: player):
	set {-g} to {-td-host::%p%}
	if {-g} is not set:
		set {-g} to %p%
	open screen "Tower Defense" to p:
		# Невидимый фон за экраном — клик в пустое место или за экран отвязывает выбранную башню от курсора
		box at 0 0 size 2000x2000 anchor center color #00000000 layer -1 run TdClearTool(player) keep open
		# Верхний HUD (скруглённая стеклянная панель + пилюли показателей)
		box at 0 -114 size 468x22 anchor center color #E6090E1A gradient #E6111C33 border #FF38BDF8 border_width 1 radius 6 shadow blur layer 11
		box at -202 -114 size 54x15 anchor center color #CC162032 border #4438BDF8 radius 4 layer 11
		box at -134 -114 size 62x15 anchor center color #CC162032 border #44FACC15 radius 4 layer 11
		box at -68 -114 size 48x15 anchor center color #CC162032 border #44EF4444 radius 4 layer 11
		box at -2 -114 size 66x15 anchor center color #CC162032 border #4422C55E radius 4 layer 11
		box at 132 -114 size 190x15 anchor center color #CC162032 border #44C084FC radius 4 layer 11
		image "td:textures/gui/icon_wave.png" at -220 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&eВолна"
		text "&f0/20" at -195 -114 anchor center shadow layer 12 id hud_wave
		image "td:textures/gui/icon_coin.png" at -154 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&6Личное золото"
		text "&6120" at -126 -114 anchor center shadow layer 12 id hud_gold
		image "td:textures/gui/icon_heart.png" at -82 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&cЗдоровье базы"
		text "&c10" at -60 -114 anchor center shadow layer 12 id hud_hp
		image "td:textures/gui/icon_trophy.png" at -24 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&aСчёт"
		text "&a0" at 6 -114 anchor center shadow layer 12 id hud_score
		text "&7Соло &7[x1]" at 132 -114 anchor center shadow layer 12 id hud_team

		# Тактическая рамка + HD карта 352x176 (текстура 896x448)
		box at -56 -8 size 358x182 anchor center color #AA090E1A border #AA38BDF8 border_width 2 radius 5 shadow layer 0
		image "td:textures/gui/map_ice.png" at -56 -8 size 352x176 texture 896x448 anchor center layer 1 id map run TdClearTool(player) keep open
		bar "boss_bar" value 100 max 100 at -56 -88 size 210x8 anchor center color #FFEF4444 background #CC1A0A0A text "&c&l☠ БОСС: %value%/%max%" shadow layer 14 hidden

		# Правая панель — Журнал событий (list без скроллбара)
		box at 179 -8 size 110x182 anchor center color #E6090E1A gradient #E6111827 border #6638BDF8 border_width 1 radius 5 shadow blur layer 11
		list from {-td-log::%{-g}%::*} vertical no scrollbar header "&b&l⚡ СОБЫТИЯ" at 179 -8 size 104x174 anchor center layer 12 id ev_log:
			row "%loop-value%"

		# Тактические радар-круги радиуса башен (авто-показ при наведении и клике!)
		image "td:textures/gui/range_circle.png" at 0 0 size 154x154 texture 256x256 anchor center layer 2 id rng_s hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 208x208 texture 256x256 anchor center layer 2 id rng_m hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 310x310 texture 256x256 anchor center layer 2 id rng_l hidden
		image "td:textures/gui/range_buff.png" at 0 0 size 150x150 texture 256x256 anchor center layer 2 id rng_b hidden

		# Сетка постройки 16x8 (клетка 22x22; пустые клетки без лишних тултипов)
		image "td:textures/gui/tile_build.png" at -221 -85 size 22x22 texture 28x28 anchor center layer 3 id c0_0 run TdCellClick(player, 0, 0) keep open
		image "td:textures/gui/tile_build.png" at -199 -85 size 22x22 texture 28x28 anchor center layer 3 id c1_0 run TdCellClick(player, 1, 0) keep open
		image "td:textures/gui/tile_build.png" at -177 -85 size 22x22 texture 28x28 anchor center layer 3 id c2_0 run TdCellClick(player, 2, 0) keep open
		image "td:textures/gui/tile_build.png" at -155 -85 size 22x22 texture 28x28 anchor center layer 3 id c3_0 run TdCellClick(player, 3, 0) keep open
		image "td:textures/gui/tile_build.png" at -133 -85 size 22x22 texture 28x28 anchor center layer 3 id c4_0 run TdCellClick(player, 4, 0) keep open
		image "td:textures/gui/tile_build.png" at -111 -85 size 22x22 texture 28x28 anchor center layer 3 id c5_0 run TdCellClick(player, 5, 0) keep open
		image "td:textures/gui/tile_build.png" at -89 -85 size 22x22 texture 28x28 anchor center layer 3 id c6_0 run TdCellClick(player, 6, 0) keep open
		image "td:textures/gui/tile_build.png" at -67 -85 size 22x22 texture 28x28 anchor center layer 3 id c7_0 run TdCellClick(player, 7, 0) keep open
		image "td:textures/gui/tile_build.png" at -45 -85 size 22x22 texture 28x28 anchor center layer 3 id c8_0 run TdCellClick(player, 8, 0) keep open
		image "td:textures/gui/tile_build.png" at -23 -85 size 22x22 texture 28x28 anchor center layer 3 id c9_0 run TdCellClick(player, 9, 0) keep open
		image "td:textures/gui/tile_build.png" at -1 -85 size 22x22 texture 28x28 anchor center layer 3 id c10_0 run TdCellClick(player, 10, 0) keep open
		image "td:textures/gui/tile_build.png" at 21 -85 size 22x22 texture 28x28 anchor center layer 3 id c11_0 run TdCellClick(player, 11, 0) keep open
		image "td:textures/gui/tile_build.png" at 43 -85 size 22x22 texture 28x28 anchor center layer 3 id c12_0 run TdCellClick(player, 12, 0) keep open
		image "td:textures/gui/tile_build.png" at 65 -85 size 22x22 texture 28x28 anchor center layer 3 id c13_0 run TdCellClick(player, 13, 0) keep open
		image "td:textures/gui/tile_build.png" at 87 -85 size 22x22 texture 28x28 anchor center layer 3 id c14_0 run TdCellClick(player, 14, 0) keep open
		image "td:textures/gui/tile_build.png" at 109 -85 size 22x22 texture 28x28 anchor center layer 3 id c15_0 run TdCellClick(player, 15, 0) keep open
		image "td:textures/gui/tile_build.png" at -221 -63 size 22x22 texture 28x28 anchor center layer 3 id c0_1 run TdCellClick(player, 0, 1) keep open
		image "td:textures/gui/tile_build.png" at -199 -63 size 22x22 texture 28x28 anchor center layer 3 id c1_1 run TdCellClick(player, 1, 1) keep open
		image "td:textures/gui/tile_build.png" at -177 -63 size 22x22 texture 28x28 anchor center layer 3 id c2_1 run TdCellClick(player, 2, 1) keep open
		image "td:textures/gui/tile_build.png" at -45 -63 size 22x22 texture 28x28 anchor center layer 3 id c8_1 run TdCellClick(player, 8, 1) keep open
		image "td:textures/gui/tile_build.png" at -23 -63 size 22x22 texture 28x28 anchor center layer 3 id c9_1 run TdCellClick(player, 9, 1) keep open
		image "td:textures/gui/tile_build.png" at -1 -63 size 22x22 texture 28x28 anchor center layer 3 id c10_1 run TdCellClick(player, 10, 1) keep open
		image "td:textures/gui/tile_build.png" at 21 -63 size 22x22 texture 28x28 anchor center layer 3 id c11_1 run TdCellClick(player, 11, 1) keep open
		image "td:textures/gui/tile_build.png" at 43 -63 size 22x22 texture 28x28 anchor center layer 3 id c12_1 run TdCellClick(player, 12, 1) keep open
		image "td:textures/gui/tile_build.png" at 65 -63 size 22x22 texture 28x28 anchor center layer 3 id c13_1 run TdCellClick(player, 13, 1) keep open
		image "td:textures/gui/tile_build.png" at 87 -63 size 22x22 texture 28x28 anchor center layer 3 id c14_1 run TdCellClick(player, 14, 1) keep open
		image "td:textures/gui/tile_build.png" at 109 -63 size 22x22 texture 28x28 anchor center layer 3 id c15_1 run TdCellClick(player, 15, 1) keep open
		image "td:textures/gui/tile_build.png" at -221 -41 size 22x22 texture 28x28 anchor center layer 3 id c0_2 run TdCellClick(player, 0, 2) keep open
		image "td:textures/gui/tile_build.png" at -199 -41 size 22x22 texture 28x28 anchor center layer 3 id c1_2 run TdCellClick(player, 1, 2) keep open
		image "td:textures/gui/tile_build.png" at -177 -41 size 22x22 texture 28x28 anchor center layer 3 id c2_2 run TdCellClick(player, 2, 2) keep open
		image "td:textures/gui/tile_build.png" at -133 -41 size 22x22 texture 28x28 anchor center layer 3 id c4_2 run TdCellClick(player, 4, 2) keep open
		image "td:textures/gui/tile_build.png" at -111 -41 size 22x22 texture 28x28 anchor center layer 3 id c5_2 run TdCellClick(player, 5, 2) keep open
		image "td:textures/gui/tile_build.png" at -89 -41 size 22x22 texture 28x28 anchor center layer 3 id c6_2 run TdCellClick(player, 6, 2) keep open
		image "td:textures/gui/tile_build.png" at -45 -41 size 22x22 texture 28x28 anchor center layer 3 id c8_2 run TdCellClick(player, 8, 2) keep open
		image "td:textures/gui/tile_build.png" at -23 -41 size 22x22 texture 28x28 anchor center layer 3 id c9_2 run TdCellClick(player, 9, 2) keep open
		image "td:textures/gui/tile_build.png" at -1 -41 size 22x22 texture 28x28 anchor center layer 3 id c10_2 run TdCellClick(player, 10, 2) keep open
		image "td:textures/gui/tile_build.png" at -221 -19 size 22x22 texture 28x28 anchor center layer 3 id c0_3 run TdCellClick(player, 0, 3) keep open
		image "td:textures/gui/tile_build.png" at -199 -19 size 22x22 texture 28x28 anchor center layer 3 id c1_3 run TdCellClick(player, 1, 3) keep open
		image "td:textures/gui/tile_build.png" at -177 -19 size 22x22 texture 28x28 anchor center layer 3 id c2_3 run TdCellClick(player, 2, 3) keep open
		image "td:textures/gui/tile_build.png" at -133 -19 size 22x22 texture 28x28 anchor center layer 3 id c4_3 run TdCellClick(player, 4, 3) keep open
		image "td:textures/gui/tile_build.png" at -111 -19 size 22x22 texture 28x28 anchor center layer 3 id c5_3 run TdCellClick(player, 5, 3) keep open
		image "td:textures/gui/tile_build.png" at -89 -19 size 22x22 texture 28x28 anchor center layer 3 id c6_3 run TdCellClick(player, 6, 3) keep open
		image "td:textures/gui/tile_build.png" at -45 -19 size 22x22 texture 28x28 anchor center layer 3 id c8_3 run TdCellClick(player, 8, 3) keep open
		image "td:textures/gui/tile_build.png" at -23 -19 size 22x22 texture 28x28 anchor center layer 3 id c9_3 run TdCellClick(player, 9, 3) keep open
		image "td:textures/gui/tile_build.png" at -1 -19 size 22x22 texture 28x28 anchor center layer 3 id c10_3 run TdCellClick(player, 10, 3) keep open
		image "td:textures/gui/tile_build.png" at 43 -19 size 22x22 texture 28x28 anchor center layer 3 id c12_3 run TdCellClick(player, 12, 3) keep open
		image "td:textures/gui/tile_build.png" at 65 -19 size 22x22 texture 28x28 anchor center layer 3 id c13_3 run TdCellClick(player, 13, 3) keep open
		image "td:textures/gui/tile_build.png" at 87 -19 size 22x22 texture 28x28 anchor center layer 3 id c14_3 run TdCellClick(player, 14, 3) keep open
		image "td:textures/gui/tile_build.png" at 109 -19 size 22x22 texture 28x28 anchor center layer 3 id c15_3 run TdCellClick(player, 15, 3) keep open
		image "td:textures/gui/tile_build.png" at -133 3 size 22x22 texture 28x28 anchor center layer 3 id c4_4 run TdCellClick(player, 4, 4) keep open
		image "td:textures/gui/tile_build.png" at -111 3 size 22x22 texture 28x28 anchor center layer 3 id c5_4 run TdCellClick(player, 5, 4) keep open
		image "td:textures/gui/tile_build.png" at -89 3 size 22x22 texture 28x28 anchor center layer 3 id c6_4 run TdCellClick(player, 6, 4) keep open
		image "td:textures/gui/tile_build.png" at -45 3 size 22x22 texture 28x28 anchor center layer 3 id c8_4 run TdCellClick(player, 8, 4) keep open
		image "td:textures/gui/tile_build.png" at -23 3 size 22x22 texture 28x28 anchor center layer 3 id c9_4 run TdCellClick(player, 9, 4) keep open
		image "td:textures/gui/tile_build.png" at -1 3 size 22x22 texture 28x28 anchor center layer 3 id c10_4 run TdCellClick(player, 10, 4) keep open
		image "td:textures/gui/tile_build.png" at 43 3 size 22x22 texture 28x28 anchor center layer 3 id c12_4 run TdCellClick(player, 12, 4) keep open
		image "td:textures/gui/tile_build.png" at 65 3 size 22x22 texture 28x28 anchor center layer 3 id c13_4 run TdCellClick(player, 13, 4) keep open
		image "td:textures/gui/tile_build.png" at 87 3 size 22x22 texture 28x28 anchor center layer 3 id c14_4 run TdCellClick(player, 14, 4) keep open
		image "td:textures/gui/tile_build.png" at 109 3 size 22x22 texture 28x28 anchor center layer 3 id c15_4 run TdCellClick(player, 15, 4) keep open
		image "td:textures/gui/tile_build.png" at -221 25 size 22x22 texture 28x28 anchor center layer 3 id c0_5 run TdCellClick(player, 0, 5) keep open
		image "td:textures/gui/tile_build.png" at -199 25 size 22x22 texture 28x28 anchor center layer 3 id c1_5 run TdCellClick(player, 1, 5) keep open
		image "td:textures/gui/tile_build.png" at -177 25 size 22x22 texture 28x28 anchor center layer 3 id c2_5 run TdCellClick(player, 2, 5) keep open
		image "td:textures/gui/tile_build.png" at -155 25 size 22x22 texture 28x28 anchor center layer 3 id c3_5 run TdCellClick(player, 3, 5) keep open
		image "td:textures/gui/tile_build.png" at -133 25 size 22x22 texture 28x28 anchor center layer 3 id c4_5 run TdCellClick(player, 4, 5) keep open
		image "td:textures/gui/tile_build.png" at -111 25 size 22x22 texture 28x28 anchor center layer 3 id c5_5 run TdCellClick(player, 5, 5) keep open
		image "td:textures/gui/tile_build.png" at -89 25 size 22x22 texture 28x28 anchor center layer 3 id c6_5 run TdCellClick(player, 6, 5) keep open
		image "td:textures/gui/tile_build.png" at -45 25 size 22x22 texture 28x28 anchor center layer 3 id c8_5 run TdCellClick(player, 8, 5) keep open
		image "td:textures/gui/tile_build.png" at -23 25 size 22x22 texture 28x28 anchor center layer 3 id c9_5 run TdCellClick(player, 9, 5) keep open
		image "td:textures/gui/tile_build.png" at -1 25 size 22x22 texture 28x28 anchor center layer 3 id c10_5 run TdCellClick(player, 10, 5) keep open
		image "td:textures/gui/tile_build.png" at 43 25 size 22x22 texture 28x28 anchor center layer 3 id c12_5 run TdCellClick(player, 12, 5) keep open
		image "td:textures/gui/tile_build.png" at 65 25 size 22x22 texture 28x28 anchor center layer 3 id c13_5 run TdCellClick(player, 13, 5) keep open
		image "td:textures/gui/tile_build.png" at 87 25 size 22x22 texture 28x28 anchor center layer 3 id c14_5 run TdCellClick(player, 14, 5) keep open
		image "td:textures/gui/tile_build.png" at 109 25 size 22x22 texture 28x28 anchor center layer 3 id c15_5 run TdCellClick(player, 15, 5) keep open
		image "td:textures/gui/tile_build.png" at -221 47 size 22x22 texture 28x28 anchor center layer 3 id c0_6 run TdCellClick(player, 0, 6) keep open
		image "td:textures/gui/tile_build.png" at -199 47 size 22x22 texture 28x28 anchor center layer 3 id c1_6 run TdCellClick(player, 1, 6) keep open
		image "td:textures/gui/tile_build.png" at -177 47 size 22x22 texture 28x28 anchor center layer 3 id c2_6 run TdCellClick(player, 2, 6) keep open
		image "td:textures/gui/tile_build.png" at -155 47 size 22x22 texture 28x28 anchor center layer 3 id c3_6 run TdCellClick(player, 3, 6) keep open
		image "td:textures/gui/tile_build.png" at -133 47 size 22x22 texture 28x28 anchor center layer 3 id c4_6 run TdCellClick(player, 4, 6) keep open
		image "td:textures/gui/tile_build.png" at -111 47 size 22x22 texture 28x28 anchor center layer 3 id c5_6 run TdCellClick(player, 5, 6) keep open
		image "td:textures/gui/tile_build.png" at -89 47 size 22x22 texture 28x28 anchor center layer 3 id c6_6 run TdCellClick(player, 6, 6) keep open
		image "td:textures/gui/tile_build.png" at 43 47 size 22x22 texture 28x28 anchor center layer 3 id c12_6 run TdCellClick(player, 12, 6) keep open
		image "td:textures/gui/tile_build.png" at 65 47 size 22x22 texture 28x28 anchor center layer 3 id c13_6 run TdCellClick(player, 13, 6) keep open
		image "td:textures/gui/tile_build.png" at 87 47 size 22x22 texture 28x28 anchor center layer 3 id c14_6 run TdCellClick(player, 14, 6) keep open
		image "td:textures/gui/tile_build.png" at 109 47 size 22x22 texture 28x28 anchor center layer 3 id c15_6 run TdCellClick(player, 15, 6) keep open
		image "td:textures/gui/tile_build.png" at -221 69 size 22x22 texture 28x28 anchor center layer 3 id c0_7 run TdCellClick(player, 0, 7) keep open
		image "td:textures/gui/tile_build.png" at -199 69 size 22x22 texture 28x28 anchor center layer 3 id c1_7 run TdCellClick(player, 1, 7) keep open
		image "td:textures/gui/tile_build.png" at -177 69 size 22x22 texture 28x28 anchor center layer 3 id c2_7 run TdCellClick(player, 2, 7) keep open
		image "td:textures/gui/tile_build.png" at -155 69 size 22x22 texture 28x28 anchor center layer 3 id c3_7 run TdCellClick(player, 3, 7) keep open
		image "td:textures/gui/tile_build.png" at -133 69 size 22x22 texture 28x28 anchor center layer 3 id c4_7 run TdCellClick(player, 4, 7) keep open
		image "td:textures/gui/tile_build.png" at -111 69 size 22x22 texture 28x28 anchor center layer 3 id c5_7 run TdCellClick(player, 5, 7) keep open
		image "td:textures/gui/tile_build.png" at -89 69 size 22x22 texture 28x28 anchor center layer 3 id c6_7 run TdCellClick(player, 6, 7) keep open
		image "td:textures/gui/tile_build.png" at -67 69 size 22x22 texture 28x28 anchor center layer 3 id c7_7 run TdCellClick(player, 7, 7) keep open
		image "td:textures/gui/tile_build.png" at -45 69 size 22x22 texture 28x28 anchor center layer 3 id c8_7 run TdCellClick(player, 8, 7) keep open
		image "td:textures/gui/tile_build.png" at -23 69 size 22x22 texture 28x28 anchor center layer 3 id c9_7 run TdCellClick(player, 9, 7) keep open
		image "td:textures/gui/tile_build.png" at -1 69 size 22x22 texture 28x28 anchor center layer 3 id c10_7 run TdCellClick(player, 10, 7) keep open
		image "td:textures/gui/tile_build.png" at 21 69 size 22x22 texture 28x28 anchor center layer 3 id c11_7 run TdCellClick(player, 11, 7) keep open
		image "td:textures/gui/tile_build.png" at 43 69 size 22x22 texture 28x28 anchor center layer 3 id c12_7 run TdCellClick(player, 12, 7) keep open
		image "td:textures/gui/tile_build.png" at 65 69 size 22x22 texture 28x28 anchor center layer 3 id c13_7 run TdCellClick(player, 13, 7) keep open
		image "td:textures/gui/tile_build.png" at 87 69 size 22x22 texture 28x28 anchor center layer 3 id c14_7 run TdCellClick(player, 14, 7) keep open
		image "td:textures/gui/tile_build.png" at 109 69 size 22x22 texture 28x28 anchor center layer 3 id c15_7 run TdCellClick(player, 15, 7) keep open

		# Пул звёзд уровней и эволюций (до 32 башен)
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st1 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st2 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st3 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st4 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st5 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st6 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st7 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st8 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st9 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st10 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st11 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st12 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st13 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st14 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st15 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st16 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st17 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st18 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st19 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st20 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st21 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st22 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st23 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st24 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st25 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st26 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st27 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st28 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st29 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st30 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st31 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st32 hidden

		# 11 слотов обычных врагов + 1 слот Босса + тонкие HP-бары (64x64 HD + 60 FPS tween)
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e1 hidden
		bar "ehp1" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e2 hidden
		bar "ehp2" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e3 hidden
		bar "ehp3" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e4 hidden
		bar "ehp4" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e5 hidden
		bar "ehp5" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e6 hidden
		bar "ehp6" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e7 hidden
		bar "ehp7" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e8 hidden
		bar "ehp8" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e9 hidden
		bar "ehp9" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e10 hidden
		bar "ehp10" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e11 hidden
		bar "ehp11" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_boss.png" at -221 -63 size 28x28 texture 64x64 anchor center layer 8 id e12 hidden
		bar "ehp12" value 100 max 100 at -221 -79 size 26x4 anchor center color #FFEF4444 background #CC1A0A0A layer 9 hidden

		# 6 летающих HD прожектайлов (60 FPS tween)
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr1 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr2 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr3 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr4 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr5 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr6 hidden

		# Компактная строка статуса
		box at 0 92 size 468x13 anchor center color #E60B1220 border #3338BDF8 radius 3 layer 11
		text "&e[1] Баллиста &7— наведите на башню для радиуса и статов, ЛКМ по клетке — строить" at 0 92 anchor center shadow layer 12 id status_txt
		text "&c&lБАЗА ПАЛА!" at -56 -18 anchor center shadow scale 1.4 layer 20 id end_title hidden
		text "&fНажми &eR &f(рестарт) или &eM &f(меню)" at -56 2 anchor center shadow layer 20 id end_sub hidden

		# Нижняя панель: 9 HD башен (включая Маяк-Баффер #9) + управление и тумблер Курсора
		box at 0 121 size 468x40 anchor center color #EE090E1A gradient #EE111827 border #FF1E293B radius 6 shadow blur layer 11
		box at -218 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -186 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -154 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -122 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -90 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -58 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -26 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at 6 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at 38 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -218 106 size 26x2 anchor center color #FFFACC15 radius 1 id sel_bar hidden layer 13
		image "td:textures/gui/tower_archer.png" at -218 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e&l[1] Эльфийская Баллиста &650з%nl%&8▪ &7Урон: &f15 &8| &7КД: &a0.4с &8| &7Дальн: &b78%nl%&6✦ 5★ Шквал: &fстреляет сразу по 2 целям"
		text "&e[1]50" at -218 131 anchor center shadow scale 0.85 layer 12 id lbl1
		image "td:textures/gui/tower_cannon.png" at -186 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6&l[2] Мортира «Вулкан» &695з%nl%&8▪ &7Урон: &f36 &c(Сплеш) &8| &7Дальн: &b74%nl%&6✦ 5★ Напалм: &fмега-сплеш + поджог врагов"
		text "&7[2]95" at -186 131 anchor center shadow scale 0.85 layer 12 id lbl2
		image "td:textures/gui/tower_frost.png" at -154 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b&l[3] Крио-Обелиск &685з%nl%&8▪ &7Аура: &f8 ур. &b(40% замедл. всех в радиусе 76)%nl%&6✦ 5★ Стужа: &f50% замедл. + 12% заморозка"
		text "&7[3]85" at -154 131 anchor center shadow scale 0.85 layer 12 id lbl3
		image "td:textures/gui/tower_mage.png" at -122 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d&l[4] Аркан-Шпиль &6130з%nl%&8▪ &7Урон: &f32 &8| &7Дальн: &b106%nl%&6✦ 5★ Убийца Боссов: &fx2 Боссам + 4% макс.HP"
		text "&7[4]130" at -122 131 anchor center shadow scale 0.85 layer 12 id lbl4
		image "td:textures/gui/tower_tesla.png" at -90 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico5 run TdSelectTool(player, 5) keep open tooltip "&b&l[5] Плазмо-Тесла &6115з%nl%&8▪ &7Урон: &f20 &b(Цепь на 3 цели) &8| &7Дальн: &b86%nl%&6✦ 5★ Шторм: &f5 целей + 90% урон + микро-стан"
		text "&7[5]115" at -90 131 anchor center shadow scale 0.85 layer 12 id lbl5
		image "td:textures/gui/tower_sniper.png" at -58 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico6 run TdSelectTool(player, 6) keep open tooltip "&c&l[6] Рейлган «Сокол» &6150з%nl%&8▪ &7Урон: &f56 &c(Крит 25%) &8| &7Дальн: &b172%nl%&6✦ 5★ Казнь: &fкрит 45% x2.5 + добивание <20% HP"
		text "&7[6]150" at -58 131 anchor center shadow scale 0.85 layer 12 id lbl6
		image "td:textures/gui/tower_poison.png" at -26 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico7 run TdSelectTool(player, 7) keep open tooltip "&a&l[7] Чумной Реактор &6105з%nl%&8▪ &7Урон: &f12 + &aКислота (DOT) &8| &7Дальн: &b80%nl%&6✦ 5★ Чума: &fзаражает всех врагов рядом"
		text "&7[7]105" at -26 131 anchor center shadow scale 0.85 layer 12 id lbl7
		image "td:textures/gui/tower_farm.png" at 6 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico8 run TdSelectTool(player, 8) keep open tooltip "&6&l[8] Монетный Двор &6140з%nl%&8▪ &7Доход: &6+28/+50/+80/+120з &7за волну%nl%&6✦ 5★ Золотой Дворец: &f+180з и +1 HP базе"
		text "&7[8]140" at 6 131 anchor center shadow scale 0.85 layer 12 id lbl8
		image "td:textures/gui/tower_buffer.png" at 38 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico9 run TdSelectTool(player, 9) keep open tooltip "&d&l[9] Боевой Маяк (Баффер) &6125з%nl%&8▪ &7Бафф-аура: &d+18..42% урона &7башням в радиусе 68%nl%&6✦ 5★ Знамя Империи: &f+55% урон, +20% скоростр. и +25з"
		text "&7[9]125" at 38 131 anchor center shadow scale 0.85 layer 12 id lbl9
		button "&aСтарт/x2" at 94 112 size 56x15 anchor center run TdWaveOrSpeed(player) keep open tooltip "&aСтарт волны / Скорость x1↔x2 &8[Space]"
		toggle "Авто" id autowave at 152 112 size 52x15 anchor center value false color #22C55E tooltip "&aАвто-старт волн"
		toggle "Курсор" id cursortog at 208 112 size 52x15 anchor center value true color #38BDF8 tooltip "&bИконка выбранной башни у курсора &8[C]"
		button "&cПродать" at 94 129 size 56x15 anchor center run TdToggleSell(player) keep open tooltip "&cПродать башню за 70% &8[X]"
		button "Заново" at 152 129 size 52x15 anchor center run TdRestartCurrent(player) keep open tooltip "&eРестарт карты &8[R]"
		button "Меню" at 208 129 size 52x15 anchor center run TdOpenMenu(player) keep open tooltip "&eВыбор карты и кооп &8[M]"

		# Привязанная к курсору иконка текущей башни (универсальное свойство follow cursor)
		image "td:textures/gui/tower_archer.png" at 0 0 size 15x15 texture 64x64 anchor center follow cursor layer 24 id cursor_icon hidden

function TdOpenMap4(p: player):
	set {-g} to {-td-host::%p%}
	if {-g} is not set:
		set {-g} to %p%
	open screen "Tower Defense" to p:
		# Невидимый фон за экраном — клик в пустое место или за экран отвязывает выбранную башню от курсора
		box at 0 0 size 2000x2000 anchor center color #00000000 layer -1 run TdClearTool(player) keep open
		# Верхний HUD (скруглённая стеклянная панель + пилюли показателей)
		box at 0 -114 size 468x22 anchor center color #E6090E1A gradient #E6111C33 border #FF38BDF8 border_width 1 radius 6 shadow blur layer 11
		box at -202 -114 size 54x15 anchor center color #CC162032 border #4438BDF8 radius 4 layer 11
		box at -134 -114 size 62x15 anchor center color #CC162032 border #44FACC15 radius 4 layer 11
		box at -68 -114 size 48x15 anchor center color #CC162032 border #44EF4444 radius 4 layer 11
		box at -2 -114 size 66x15 anchor center color #CC162032 border #4422C55E radius 4 layer 11
		box at 132 -114 size 190x15 anchor center color #CC162032 border #44C084FC radius 4 layer 11
		image "td:textures/gui/icon_wave.png" at -220 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&eВолна"
		text "&f0/20" at -195 -114 anchor center shadow layer 12 id hud_wave
		image "td:textures/gui/icon_coin.png" at -154 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&6Личное золото"
		text "&6120" at -126 -114 anchor center shadow layer 12 id hud_gold
		image "td:textures/gui/icon_heart.png" at -82 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&cЗдоровье базы"
		text "&c10" at -60 -114 anchor center shadow layer 12 id hud_hp
		image "td:textures/gui/icon_trophy.png" at -24 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&aСчёт"
		text "&a0" at 6 -114 anchor center shadow layer 12 id hud_score
		text "&7Соло &7[x1]" at 132 -114 anchor center shadow layer 12 id hud_team

		# Тактическая рамка + HD карта 352x176 (текстура 896x448)
		box at -56 -8 size 358x182 anchor center color #AA090E1A border #AA38BDF8 border_width 2 radius 5 shadow layer 0
		image "td:textures/gui/map_swamp.png" at -56 -8 size 352x176 texture 896x448 anchor center layer 1 id map run TdClearTool(player) keep open
		bar "boss_bar" value 100 max 100 at -56 -88 size 210x8 anchor center color #FFEF4444 background #CC1A0A0A text "&c&l☠ БОСС: %value%/%max%" shadow layer 14 hidden

		# Правая панель — Журнал событий (list без скроллбара)
		box at 179 -8 size 110x182 anchor center color #E6090E1A gradient #E6111827 border #6638BDF8 border_width 1 radius 5 shadow blur layer 11
		list from {-td-log::%{-g}%::*} vertical no scrollbar header "&b&l⚡ СОБЫТИЯ" at 179 -8 size 104x174 anchor center layer 12 id ev_log:
			row "%loop-value%"

		# Тактические радар-круги радиуса башен (авто-показ при наведении и клике!)
		image "td:textures/gui/range_circle.png" at 0 0 size 154x154 texture 256x256 anchor center layer 2 id rng_s hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 208x208 texture 256x256 anchor center layer 2 id rng_m hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 310x310 texture 256x256 anchor center layer 2 id rng_l hidden
		image "td:textures/gui/range_buff.png" at 0 0 size 150x150 texture 256x256 anchor center layer 2 id rng_b hidden

		# Сетка постройки 16x8 (клетка 22x22; пустые клетки без лишних тултипов)
		image "td:textures/gui/tile_build.png" at -221 -85 size 22x22 texture 28x28 anchor center layer 3 id c0_0 run TdCellClick(player, 0, 0) keep open
		image "td:textures/gui/tile_build.png" at -155 -85 size 22x22 texture 28x28 anchor center layer 3 id c3_0 run TdCellClick(player, 3, 0) keep open
		image "td:textures/gui/tile_build.png" at -133 -85 size 22x22 texture 28x28 anchor center layer 3 id c4_0 run TdCellClick(player, 4, 0) keep open
		image "td:textures/gui/tile_build.png" at 21 -85 size 22x22 texture 28x28 anchor center layer 3 id c11_0 run TdCellClick(player, 11, 0) keep open
		image "td:textures/gui/tile_build.png" at 43 -85 size 22x22 texture 28x28 anchor center layer 3 id c12_0 run TdCellClick(player, 12, 0) keep open
		image "td:textures/gui/tile_build.png" at 65 -85 size 22x22 texture 28x28 anchor center layer 3 id c13_0 run TdCellClick(player, 13, 0) keep open
		image "td:textures/gui/tile_build.png" at 87 -85 size 22x22 texture 28x28 anchor center layer 3 id c14_0 run TdCellClick(player, 14, 0) keep open
		image "td:textures/gui/tile_build.png" at 109 -85 size 22x22 texture 28x28 anchor center layer 3 id c15_0 run TdCellClick(player, 15, 0) keep open
		image "td:textures/gui/tile_build.png" at -221 -63 size 22x22 texture 28x28 anchor center layer 3 id c0_1 run TdCellClick(player, 0, 1) keep open
		image "td:textures/gui/tile_build.png" at -199 -63 size 22x22 texture 28x28 anchor center layer 3 id c1_1 run TdCellClick(player, 1, 1) keep open
		image "td:textures/gui/tile_build.png" at -177 -63 size 22x22 texture 28x28 anchor center layer 3 id c2_1 run TdCellClick(player, 2, 1) keep open
		image "td:textures/gui/tile_build.png" at -155 -63 size 22x22 texture 28x28 anchor center layer 3 id c3_1 run TdCellClick(player, 3, 1) keep open
		image "td:textures/gui/tile_build.png" at -133 -63 size 22x22 texture 28x28 anchor center layer 3 id c4_1 run TdCellClick(player, 4, 1) keep open
		image "td:textures/gui/tile_build.png" at -89 -63 size 22x22 texture 28x28 anchor center layer 3 id c6_1 run TdCellClick(player, 6, 1) keep open
		image "td:textures/gui/tile_build.png" at -67 -63 size 22x22 texture 28x28 anchor center layer 3 id c7_1 run TdCellClick(player, 7, 1) keep open
		image "td:textures/gui/tile_build.png" at -45 -63 size 22x22 texture 28x28 anchor center layer 3 id c8_1 run TdCellClick(player, 8, 1) keep open
		image "td:textures/gui/tile_build.png" at -23 -63 size 22x22 texture 28x28 anchor center layer 3 id c9_1 run TdCellClick(player, 9, 1) keep open
		image "td:textures/gui/tile_build.png" at 21 -63 size 22x22 texture 28x28 anchor center layer 3 id c11_1 run TdCellClick(player, 11, 1) keep open
		image "td:textures/gui/tile_build.png" at 65 -63 size 22x22 texture 28x28 anchor center layer 3 id c13_1 run TdCellClick(player, 13, 1) keep open
		image "td:textures/gui/tile_build.png" at 87 -63 size 22x22 texture 28x28 anchor center layer 3 id c14_1 run TdCellClick(player, 14, 1) keep open
		image "td:textures/gui/tile_build.png" at 109 -63 size 22x22 texture 28x28 anchor center layer 3 id c15_1 run TdCellClick(player, 15, 1) keep open
		image "td:textures/gui/tile_build.png" at -67 -41 size 22x22 texture 28x28 anchor center layer 3 id c7_2 run TdCellClick(player, 7, 2) keep open
		image "td:textures/gui/tile_build.png" at -23 -41 size 22x22 texture 28x28 anchor center layer 3 id c9_2 run TdCellClick(player, 9, 2) keep open
		image "td:textures/gui/tile_build.png" at 43 -41 size 22x22 texture 28x28 anchor center layer 3 id c12_2 run TdCellClick(player, 12, 2) keep open
		image "td:textures/gui/tile_build.png" at 65 -41 size 22x22 texture 28x28 anchor center layer 3 id c13_2 run TdCellClick(player, 13, 2) keep open
		image "td:textures/gui/tile_build.png" at 87 -41 size 22x22 texture 28x28 anchor center layer 3 id c14_2 run TdCellClick(player, 14, 2) keep open
		image "td:textures/gui/tile_build.png" at 109 -41 size 22x22 texture 28x28 anchor center layer 3 id c15_2 run TdCellClick(player, 15, 2) keep open
		image "td:textures/gui/tile_build.png" at -221 -19 size 22x22 texture 28x28 anchor center layer 3 id c0_3 run TdCellClick(player, 0, 3) keep open
		image "td:textures/gui/tile_build.png" at -199 -19 size 22x22 texture 28x28 anchor center layer 3 id c1_3 run TdCellClick(player, 1, 3) keep open
		image "td:textures/gui/tile_build.png" at -177 -19 size 22x22 texture 28x28 anchor center layer 3 id c2_3 run TdCellClick(player, 2, 3) keep open
		image "td:textures/gui/tile_build.png" at -155 -19 size 22x22 texture 28x28 anchor center layer 3 id c3_3 run TdCellClick(player, 3, 3) keep open
		image "td:textures/gui/tile_build.png" at -133 -19 size 22x22 texture 28x28 anchor center layer 3 id c4_3 run TdCellClick(player, 4, 3) keep open
		image "td:textures/gui/tile_build.png" at -111 -19 size 22x22 texture 28x28 anchor center layer 3 id c5_3 run TdCellClick(player, 5, 3) keep open
		image "td:textures/gui/tile_build.png" at -89 -19 size 22x22 texture 28x28 anchor center layer 3 id c6_3 run TdCellClick(player, 6, 3) keep open
		image "td:textures/gui/tile_build.png" at -67 -19 size 22x22 texture 28x28 anchor center layer 3 id c7_3 run TdCellClick(player, 7, 3) keep open
		image "td:textures/gui/tile_build.png" at -23 -19 size 22x22 texture 28x28 anchor center layer 3 id c9_3 run TdCellClick(player, 9, 3) keep open
		image "td:textures/gui/tile_build.png" at 43 -19 size 22x22 texture 28x28 anchor center layer 3 id c12_3 run TdCellClick(player, 12, 3) keep open
		image "td:textures/gui/tile_build.png" at -221 3 size 22x22 texture 28x28 anchor center layer 3 id c0_4 run TdCellClick(player, 0, 4) keep open
		image "td:textures/gui/tile_build.png" at -199 3 size 22x22 texture 28x28 anchor center layer 3 id c1_4 run TdCellClick(player, 1, 4) keep open
		image "td:textures/gui/tile_build.png" at -133 3 size 22x22 texture 28x28 anchor center layer 3 id c4_4 run TdCellClick(player, 4, 4) keep open
		image "td:textures/gui/tile_build.png" at -111 3 size 22x22 texture 28x28 anchor center layer 3 id c5_4 run TdCellClick(player, 5, 4) keep open
		image "td:textures/gui/tile_build.png" at -45 3 size 22x22 texture 28x28 anchor center layer 3 id c8_4 run TdCellClick(player, 8, 4) keep open
		image "td:textures/gui/tile_build.png" at -23 3 size 22x22 texture 28x28 anchor center layer 3 id c9_4 run TdCellClick(player, 9, 4) keep open
		image "td:textures/gui/tile_build.png" at 21 3 size 22x22 texture 28x28 anchor center layer 3 id c11_4 run TdCellClick(player, 11, 4) keep open
		image "td:textures/gui/tile_build.png" at 43 3 size 22x22 texture 28x28 anchor center layer 3 id c12_4 run TdCellClick(player, 12, 4) keep open
		image "td:textures/gui/tile_build.png" at 87 3 size 22x22 texture 28x28 anchor center layer 3 id c14_4 run TdCellClick(player, 14, 4) keep open
		image "td:textures/gui/tile_build.png" at 109 3 size 22x22 texture 28x28 anchor center layer 3 id c15_4 run TdCellClick(player, 15, 4) keep open
		image "td:textures/gui/tile_build.png" at -221 25 size 22x22 texture 28x28 anchor center layer 3 id c0_5 run TdCellClick(player, 0, 5) keep open
		image "td:textures/gui/tile_build.png" at -199 25 size 22x22 texture 28x28 anchor center layer 3 id c1_5 run TdCellClick(player, 1, 5) keep open
		image "td:textures/gui/tile_build.png" at -177 25 size 22x22 texture 28x28 anchor center layer 3 id c2_5 run TdCellClick(player, 2, 5) keep open
		image "td:textures/gui/tile_build.png" at -133 25 size 22x22 texture 28x28 anchor center layer 3 id c4_5 run TdCellClick(player, 4, 5) keep open
		image "td:textures/gui/tile_build.png" at -111 25 size 22x22 texture 28x28 anchor center layer 3 id c5_5 run TdCellClick(player, 5, 5) keep open
		image "td:textures/gui/tile_build.png" at -89 25 size 22x22 texture 28x28 anchor center layer 3 id c6_5 run TdCellClick(player, 6, 5) keep open
		image "td:textures/gui/tile_build.png" at -45 25 size 22x22 texture 28x28 anchor center layer 3 id c8_5 run TdCellClick(player, 8, 5) keep open
		image "td:textures/gui/tile_build.png" at -23 25 size 22x22 texture 28x28 anchor center layer 3 id c9_5 run TdCellClick(player, 9, 5) keep open
		image "td:textures/gui/tile_build.png" at 21 25 size 22x22 texture 28x28 anchor center layer 3 id c11_5 run TdCellClick(player, 11, 5) keep open
		image "td:textures/gui/tile_build.png" at 43 25 size 22x22 texture 28x28 anchor center layer 3 id c12_5 run TdCellClick(player, 12, 5) keep open
		image "td:textures/gui/tile_build.png" at 109 25 size 22x22 texture 28x28 anchor center layer 3 id c15_5 run TdCellClick(player, 15, 5) keep open
		image "td:textures/gui/tile_build.png" at -221 47 size 22x22 texture 28x28 anchor center layer 3 id c0_6 run TdCellClick(player, 0, 6) keep open
		image "td:textures/gui/tile_build.png" at -199 47 size 22x22 texture 28x28 anchor center layer 3 id c1_6 run TdCellClick(player, 1, 6) keep open
		image "td:textures/gui/tile_build.png" at -177 47 size 22x22 texture 28x28 anchor center layer 3 id c2_6 run TdCellClick(player, 2, 6) keep open
		image "td:textures/gui/tile_build.png" at -155 47 size 22x22 texture 28x28 anchor center layer 3 id c3_6 run TdCellClick(player, 3, 6) keep open
		image "td:textures/gui/tile_build.png" at -89 47 size 22x22 texture 28x28 anchor center layer 3 id c6_6 run TdCellClick(player, 6, 6) keep open
		image "td:textures/gui/tile_build.png" at -67 47 size 22x22 texture 28x28 anchor center layer 3 id c7_6 run TdCellClick(player, 7, 6) keep open
		image "td:textures/gui/tile_build.png" at -45 47 size 22x22 texture 28x28 anchor center layer 3 id c8_6 run TdCellClick(player, 8, 6) keep open
		image "td:textures/gui/tile_build.png" at -23 47 size 22x22 texture 28x28 anchor center layer 3 id c9_6 run TdCellClick(player, 9, 6) keep open
		image "td:textures/gui/tile_build.png" at 21 47 size 22x22 texture 28x28 anchor center layer 3 id c11_6 run TdCellClick(player, 11, 6) keep open
		image "td:textures/gui/tile_build.png" at 43 47 size 22x22 texture 28x28 anchor center layer 3 id c12_6 run TdCellClick(player, 12, 6) keep open
		image "td:textures/gui/tile_build.png" at 109 47 size 22x22 texture 28x28 anchor center layer 3 id c15_6 run TdCellClick(player, 15, 6) keep open
		image "td:textures/gui/tile_build.png" at -221 69 size 22x22 texture 28x28 anchor center layer 3 id c0_7 run TdCellClick(player, 0, 7) keep open
		image "td:textures/gui/tile_build.png" at -199 69 size 22x22 texture 28x28 anchor center layer 3 id c1_7 run TdCellClick(player, 1, 7) keep open
		image "td:textures/gui/tile_build.png" at -177 69 size 22x22 texture 28x28 anchor center layer 3 id c2_7 run TdCellClick(player, 2, 7) keep open
		image "td:textures/gui/tile_build.png" at -155 69 size 22x22 texture 28x28 anchor center layer 3 id c3_7 run TdCellClick(player, 3, 7) keep open
		image "td:textures/gui/tile_build.png" at -133 69 size 22x22 texture 28x28 anchor center layer 3 id c4_7 run TdCellClick(player, 4, 7) keep open
		image "td:textures/gui/tile_build.png" at -111 69 size 22x22 texture 28x28 anchor center layer 3 id c5_7 run TdCellClick(player, 5, 7) keep open
		image "td:textures/gui/tile_build.png" at -89 69 size 22x22 texture 28x28 anchor center layer 3 id c6_7 run TdCellClick(player, 6, 7) keep open
		image "td:textures/gui/tile_build.png" at -67 69 size 22x22 texture 28x28 anchor center layer 3 id c7_7 run TdCellClick(player, 7, 7) keep open
		image "td:textures/gui/tile_build.png" at -45 69 size 22x22 texture 28x28 anchor center layer 3 id c8_7 run TdCellClick(player, 8, 7) keep open
		image "td:textures/gui/tile_build.png" at -23 69 size 22x22 texture 28x28 anchor center layer 3 id c9_7 run TdCellClick(player, 9, 7) keep open
		image "td:textures/gui/tile_build.png" at 87 69 size 22x22 texture 28x28 anchor center layer 3 id c14_7 run TdCellClick(player, 14, 7) keep open
		image "td:textures/gui/tile_build.png" at 109 69 size 22x22 texture 28x28 anchor center layer 3 id c15_7 run TdCellClick(player, 15, 7) keep open

		# Пул звёзд уровней и эволюций (до 32 башен)
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st1 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st2 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st3 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st4 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st5 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st6 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st7 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st8 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st9 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st10 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st11 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st12 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st13 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st14 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st15 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st16 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st17 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st18 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st19 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st20 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st21 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st22 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st23 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st24 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st25 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st26 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st27 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st28 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st29 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st30 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st31 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st32 hidden

		# 11 слотов обычных врагов + 1 слот Босса + тонкие HP-бары (64x64 HD + 60 FPS tween)
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e1 hidden
		bar "ehp1" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e2 hidden
		bar "ehp2" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e3 hidden
		bar "ehp3" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e4 hidden
		bar "ehp4" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e5 hidden
		bar "ehp5" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e6 hidden
		bar "ehp6" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e7 hidden
		bar "ehp7" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e8 hidden
		bar "ehp8" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e9 hidden
		bar "ehp9" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e10 hidden
		bar "ehp10" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e11 hidden
		bar "ehp11" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_boss.png" at -221 -63 size 28x28 texture 64x64 anchor center layer 8 id e12 hidden
		bar "ehp12" value 100 max 100 at -221 -79 size 26x4 anchor center color #FFEF4444 background #CC1A0A0A layer 9 hidden

		# 6 летающих HD прожектайлов (60 FPS tween)
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr1 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr2 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr3 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr4 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr5 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr6 hidden

		# Компактная строка статуса
		box at 0 92 size 468x13 anchor center color #E60B1220 border #3338BDF8 radius 3 layer 11
		text "&e[1] Баллиста &7— наведите на башню для радиуса и статов, ЛКМ по клетке — строить" at 0 92 anchor center shadow layer 12 id status_txt
		text "&c&lБАЗА ПАЛА!" at -56 -18 anchor center shadow scale 1.4 layer 20 id end_title hidden
		text "&fНажми &eR &f(рестарт) или &eM &f(меню)" at -56 2 anchor center shadow layer 20 id end_sub hidden

		# Нижняя панель: 9 HD башен (включая Маяк-Баффер #9) + управление и тумблер Курсора
		box at 0 121 size 468x40 anchor center color #EE090E1A gradient #EE111827 border #FF1E293B radius 6 shadow blur layer 11
		box at -218 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -186 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -154 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -122 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -90 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -58 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -26 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at 6 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at 38 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -218 106 size 26x2 anchor center color #FFFACC15 radius 1 id sel_bar hidden layer 13
		image "td:textures/gui/tower_archer.png" at -218 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e&l[1] Эльфийская Баллиста &650з%nl%&8▪ &7Урон: &f15 &8| &7КД: &a0.4с &8| &7Дальн: &b78%nl%&6✦ 5★ Шквал: &fстреляет сразу по 2 целям"
		text "&e[1]50" at -218 131 anchor center shadow scale 0.85 layer 12 id lbl1
		image "td:textures/gui/tower_cannon.png" at -186 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6&l[2] Мортира «Вулкан» &695з%nl%&8▪ &7Урон: &f36 &c(Сплеш) &8| &7Дальн: &b74%nl%&6✦ 5★ Напалм: &fмега-сплеш + поджог врагов"
		text "&7[2]95" at -186 131 anchor center shadow scale 0.85 layer 12 id lbl2
		image "td:textures/gui/tower_frost.png" at -154 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b&l[3] Крио-Обелиск &685з%nl%&8▪ &7Аура: &f8 ур. &b(40% замедл. всех в радиусе 76)%nl%&6✦ 5★ Стужа: &f50% замедл. + 12% заморозка"
		text "&7[3]85" at -154 131 anchor center shadow scale 0.85 layer 12 id lbl3
		image "td:textures/gui/tower_mage.png" at -122 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d&l[4] Аркан-Шпиль &6130з%nl%&8▪ &7Урон: &f32 &8| &7Дальн: &b106%nl%&6✦ 5★ Убийца Боссов: &fx2 Боссам + 4% макс.HP"
		text "&7[4]130" at -122 131 anchor center shadow scale 0.85 layer 12 id lbl4
		image "td:textures/gui/tower_tesla.png" at -90 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico5 run TdSelectTool(player, 5) keep open tooltip "&b&l[5] Плазмо-Тесла &6115з%nl%&8▪ &7Урон: &f20 &b(Цепь на 3 цели) &8| &7Дальн: &b86%nl%&6✦ 5★ Шторм: &f5 целей + 90% урон + микро-стан"
		text "&7[5]115" at -90 131 anchor center shadow scale 0.85 layer 12 id lbl5
		image "td:textures/gui/tower_sniper.png" at -58 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico6 run TdSelectTool(player, 6) keep open tooltip "&c&l[6] Рейлган «Сокол» &6150з%nl%&8▪ &7Урон: &f56 &c(Крит 25%) &8| &7Дальн: &b172%nl%&6✦ 5★ Казнь: &fкрит 45% x2.5 + добивание <20% HP"
		text "&7[6]150" at -58 131 anchor center shadow scale 0.85 layer 12 id lbl6
		image "td:textures/gui/tower_poison.png" at -26 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico7 run TdSelectTool(player, 7) keep open tooltip "&a&l[7] Чумной Реактор &6105з%nl%&8▪ &7Урон: &f12 + &aКислота (DOT) &8| &7Дальн: &b80%nl%&6✦ 5★ Чума: &fзаражает всех врагов рядом"
		text "&7[7]105" at -26 131 anchor center shadow scale 0.85 layer 12 id lbl7
		image "td:textures/gui/tower_farm.png" at 6 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico8 run TdSelectTool(player, 8) keep open tooltip "&6&l[8] Монетный Двор &6140з%nl%&8▪ &7Доход: &6+28/+50/+80/+120з &7за волну%nl%&6✦ 5★ Золотой Дворец: &f+180з и +1 HP базе"
		text "&7[8]140" at 6 131 anchor center shadow scale 0.85 layer 12 id lbl8
		image "td:textures/gui/tower_buffer.png" at 38 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico9 run TdSelectTool(player, 9) keep open tooltip "&d&l[9] Боевой Маяк (Баффер) &6125з%nl%&8▪ &7Бафф-аура: &d+18..42% урона &7башням в радиусе 68%nl%&6✦ 5★ Знамя Империи: &f+55% урон, +20% скоростр. и +25з"
		text "&7[9]125" at 38 131 anchor center shadow scale 0.85 layer 12 id lbl9
		button "&aСтарт/x2" at 94 112 size 56x15 anchor center run TdWaveOrSpeed(player) keep open tooltip "&aСтарт волны / Скорость x1↔x2 &8[Space]"
		toggle "Авто" id autowave at 152 112 size 52x15 anchor center value false color #22C55E tooltip "&aАвто-старт волн"
		toggle "Курсор" id cursortog at 208 112 size 52x15 anchor center value true color #38BDF8 tooltip "&bИконка выбранной башни у курсора &8[C]"
		button "&cПродать" at 94 129 size 56x15 anchor center run TdToggleSell(player) keep open tooltip "&cПродать башню за 70% &8[X]"
		button "Заново" at 152 129 size 52x15 anchor center run TdRestartCurrent(player) keep open tooltip "&eРестарт карты &8[R]"
		button "Меню" at 208 129 size 52x15 anchor center run TdOpenMenu(player) keep open tooltip "&eВыбор карты и кооп &8[M]"

		# Привязанная к курсору иконка текущей башни (универсальное свойство follow cursor)
		image "td:textures/gui/tower_archer.png" at 0 0 size 15x15 texture 64x64 anchor center follow cursor layer 24 id cursor_icon hidden

function TdOpenMap5(p: player):
	set {-g} to {-td-host::%p%}
	if {-g} is not set:
		set {-g} to %p%
	open screen "Tower Defense" to p:
		# Невидимый фон за экраном — клик в пустое место или за экран отвязывает выбранную башню от курсора
		box at 0 0 size 2000x2000 anchor center color #00000000 layer -1 run TdClearTool(player) keep open
		# Верхний HUD (скруглённая стеклянная панель + пилюли показателей)
		box at 0 -114 size 468x22 anchor center color #E6090E1A gradient #E6111C33 border #FF38BDF8 border_width 1 radius 6 shadow blur layer 11
		box at -202 -114 size 54x15 anchor center color #CC162032 border #4438BDF8 radius 4 layer 11
		box at -134 -114 size 62x15 anchor center color #CC162032 border #44FACC15 radius 4 layer 11
		box at -68 -114 size 48x15 anchor center color #CC162032 border #44EF4444 radius 4 layer 11
		box at -2 -114 size 66x15 anchor center color #CC162032 border #4422C55E radius 4 layer 11
		box at 132 -114 size 190x15 anchor center color #CC162032 border #44C084FC radius 4 layer 11
		image "td:textures/gui/icon_wave.png" at -220 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&eВолна"
		text "&f0/20" at -195 -114 anchor center shadow layer 12 id hud_wave
		image "td:textures/gui/icon_coin.png" at -154 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&6Личное золото"
		text "&6120" at -126 -114 anchor center shadow layer 12 id hud_gold
		image "td:textures/gui/icon_heart.png" at -82 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&cЗдоровье базы"
		text "&c10" at -60 -114 anchor center shadow layer 12 id hud_hp
		image "td:textures/gui/icon_trophy.png" at -24 -114 size 11x11 texture 32x32 anchor center layer 12 tooltip "&aСчёт"
		text "&a0" at 6 -114 anchor center shadow layer 12 id hud_score
		text "&7Соло &7[x1]" at 132 -114 anchor center shadow layer 12 id hud_team

		# Тактическая рамка + HD карта 352x176 (текстура 896x448)
		box at -56 -8 size 358x182 anchor center color #AA090E1A border #AA38BDF8 border_width 2 radius 5 shadow layer 0
		image "td:textures/gui/map_void.png" at -56 -8 size 352x176 texture 896x448 anchor center layer 1 id map run TdClearTool(player) keep open
		bar "boss_bar" value 100 max 100 at -56 -88 size 210x8 anchor center color #FFEF4444 background #CC1A0A0A text "&c&l☠ БОСС: %value%/%max%" shadow layer 14 hidden

		# Правая панель — Журнал событий (list без скроллбара)
		box at 179 -8 size 110x182 anchor center color #E6090E1A gradient #E6111827 border #6638BDF8 border_width 1 radius 5 shadow blur layer 11
		list from {-td-log::%{-g}%::*} vertical no scrollbar header "&b&l⚡ СОБЫТИЯ" at 179 -8 size 104x174 anchor center layer 12 id ev_log:
			row "%loop-value%"

		# Тактические радар-круги радиуса башен (авто-показ при наведении и клике!)
		image "td:textures/gui/range_circle.png" at 0 0 size 154x154 texture 256x256 anchor center layer 2 id rng_s hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 208x208 texture 256x256 anchor center layer 2 id rng_m hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 310x310 texture 256x256 anchor center layer 2 id rng_l hidden
		image "td:textures/gui/range_buff.png" at 0 0 size 150x150 texture 256x256 anchor center layer 2 id rng_b hidden

		# Сетка постройки 16x8 (клетка 22x22; пустые клетки без лишних тултипов)
		image "td:textures/gui/tile_build.png" at -177 -85 size 22x22 texture 28x28 anchor center layer 3 id c2_0 run TdCellClick(player, 2, 0) keep open
		image "td:textures/gui/tile_build.png" at -155 -85 size 22x22 texture 28x28 anchor center layer 3 id c3_0 run TdCellClick(player, 3, 0) keep open
		image "td:textures/gui/tile_build.png" at -133 -85 size 22x22 texture 28x28 anchor center layer 3 id c4_0 run TdCellClick(player, 4, 0) keep open
		image "td:textures/gui/tile_build.png" at -111 -85 size 22x22 texture 28x28 anchor center layer 3 id c5_0 run TdCellClick(player, 5, 0) keep open
		image "td:textures/gui/tile_build.png" at -89 -85 size 22x22 texture 28x28 anchor center layer 3 id c6_0 run TdCellClick(player, 6, 0) keep open
		image "td:textures/gui/tile_build.png" at -67 -85 size 22x22 texture 28x28 anchor center layer 3 id c7_0 run TdCellClick(player, 7, 0) keep open
		image "td:textures/gui/tile_build.png" at -45 -85 size 22x22 texture 28x28 anchor center layer 3 id c8_0 run TdCellClick(player, 8, 0) keep open
		image "td:textures/gui/tile_build.png" at -23 -85 size 22x22 texture 28x28 anchor center layer 3 id c9_0 run TdCellClick(player, 9, 0) keep open
		image "td:textures/gui/tile_build.png" at 43 -85 size 22x22 texture 28x28 anchor center layer 3 id c12_0 run TdCellClick(player, 12, 0) keep open
		image "td:textures/gui/tile_build.png" at 65 -85 size 22x22 texture 28x28 anchor center layer 3 id c13_0 run TdCellClick(player, 13, 0) keep open
		image "td:textures/gui/tile_build.png" at -199 -63 size 22x22 texture 28x28 anchor center layer 3 id c1_1 run TdCellClick(player, 1, 1) keep open
		image "td:textures/gui/tile_build.png" at -177 -63 size 22x22 texture 28x28 anchor center layer 3 id c2_1 run TdCellClick(player, 2, 1) keep open
		image "td:textures/gui/tile_build.png" at -23 -63 size 22x22 texture 28x28 anchor center layer 3 id c9_1 run TdCellClick(player, 9, 1) keep open
		image "td:textures/gui/tile_build.png" at -1 -63 size 22x22 texture 28x28 anchor center layer 3 id c10_1 run TdCellClick(player, 10, 1) keep open
		image "td:textures/gui/tile_build.png" at 21 -63 size 22x22 texture 28x28 anchor center layer 3 id c11_1 run TdCellClick(player, 11, 1) keep open
		image "td:textures/gui/tile_build.png" at 43 -63 size 22x22 texture 28x28 anchor center layer 3 id c12_1 run TdCellClick(player, 12, 1) keep open
		image "td:textures/gui/tile_build.png" at 65 -63 size 22x22 texture 28x28 anchor center layer 3 id c13_1 run TdCellClick(player, 13, 1) keep open
		image "td:textures/gui/tile_build.png" at 87 -63 size 22x22 texture 28x28 anchor center layer 3 id c14_1 run TdCellClick(player, 14, 1) keep open
		image "td:textures/gui/tile_build.png" at 109 -63 size 22x22 texture 28x28 anchor center layer 3 id c15_1 run TdCellClick(player, 15, 1) keep open
		image "td:textures/gui/tile_build.png" at -221 -41 size 22x22 texture 28x28 anchor center layer 3 id c0_2 run TdCellClick(player, 0, 2) keep open
		image "td:textures/gui/tile_build.png" at -177 -41 size 22x22 texture 28x28 anchor center layer 3 id c2_2 run TdCellClick(player, 2, 2) keep open
		image "td:textures/gui/tile_build.png" at -133 -41 size 22x22 texture 28x28 anchor center layer 3 id c4_2 run TdCellClick(player, 4, 2) keep open
		image "td:textures/gui/tile_build.png" at -111 -41 size 22x22 texture 28x28 anchor center layer 3 id c5_2 run TdCellClick(player, 5, 2) keep open
		image "td:textures/gui/tile_build.png" at -89 -41 size 22x22 texture 28x28 anchor center layer 3 id c6_2 run TdCellClick(player, 6, 2) keep open
		image "td:textures/gui/tile_build.png" at -67 -41 size 22x22 texture 28x28 anchor center layer 3 id c7_2 run TdCellClick(player, 7, 2) keep open
		image "td:textures/gui/tile_build.png" at -23 -41 size 22x22 texture 28x28 anchor center layer 3 id c9_2 run TdCellClick(player, 9, 2) keep open
		image "td:textures/gui/tile_build.png" at 21 -41 size 22x22 texture 28x28 anchor center layer 3 id c11_2 run TdCellClick(player, 11, 2) keep open
		image "td:textures/gui/tile_build.png" at -221 -19 size 22x22 texture 28x28 anchor center layer 3 id c0_3 run TdCellClick(player, 0, 3) keep open
		image "td:textures/gui/tile_build.png" at -177 -19 size 22x22 texture 28x28 anchor center layer 3 id c2_3 run TdCellClick(player, 2, 3) keep open
		image "td:textures/gui/tile_build.png" at -133 -19 size 22x22 texture 28x28 anchor center layer 3 id c4_3 run TdCellClick(player, 4, 3) keep open
		image "td:textures/gui/tile_build.png" at -67 -19 size 22x22 texture 28x28 anchor center layer 3 id c7_3 run TdCellClick(player, 7, 3) keep open
		image "td:textures/gui/tile_build.png" at -23 -19 size 22x22 texture 28x28 anchor center layer 3 id c9_3 run TdCellClick(player, 9, 3) keep open
		image "td:textures/gui/tile_build.png" at 21 -19 size 22x22 texture 28x28 anchor center layer 3 id c11_3 run TdCellClick(player, 11, 3) keep open
		image "td:textures/gui/tile_build.png" at 65 -19 size 22x22 texture 28x28 anchor center layer 3 id c13_3 run TdCellClick(player, 13, 3) keep open
		image "td:textures/gui/tile_build.png" at 87 -19 size 22x22 texture 28x28 anchor center layer 3 id c14_3 run TdCellClick(player, 14, 3) keep open
		image "td:textures/gui/tile_build.png" at 109 -19 size 22x22 texture 28x28 anchor center layer 3 id c15_3 run TdCellClick(player, 15, 3) keep open
		image "td:textures/gui/tile_build.png" at -221 3 size 22x22 texture 28x28 anchor center layer 3 id c0_4 run TdCellClick(player, 0, 4) keep open
		image "td:textures/gui/tile_build.png" at -199 3 size 22x22 texture 28x28 anchor center layer 3 id c1_4 run TdCellClick(player, 1, 4) keep open
		image "td:textures/gui/tile_build.png" at -177 3 size 22x22 texture 28x28 anchor center layer 3 id c2_4 run TdCellClick(player, 2, 4) keep open
		image "td:textures/gui/tile_build.png" at -133 3 size 22x22 texture 28x28 anchor center layer 3 id c4_4 run TdCellClick(player, 4, 4) keep open
		image "td:textures/gui/tile_build.png" at -67 3 size 22x22 texture 28x28 anchor center layer 3 id c7_4 run TdCellClick(player, 7, 4) keep open
		image "td:textures/gui/tile_build.png" at -23 3 size 22x22 texture 28x28 anchor center layer 3 id c9_4 run TdCellClick(player, 9, 4) keep open
		image "td:textures/gui/tile_build.png" at -1 3 size 22x22 texture 28x28 anchor center layer 3 id c10_4 run TdCellClick(player, 10, 4) keep open
		image "td:textures/gui/tile_build.png" at 21 3 size 22x22 texture 28x28 anchor center layer 3 id c11_4 run TdCellClick(player, 11, 4) keep open
		image "td:textures/gui/tile_build.png" at 65 3 size 22x22 texture 28x28 anchor center layer 3 id c13_4 run TdCellClick(player, 13, 4) keep open
		image "td:textures/gui/tile_build.png" at 87 3 size 22x22 texture 28x28 anchor center layer 3 id c14_4 run TdCellClick(player, 14, 4) keep open
		image "td:textures/gui/tile_build.png" at 109 3 size 22x22 texture 28x28 anchor center layer 3 id c15_4 run TdCellClick(player, 15, 4) keep open
		image "td:textures/gui/tile_build.png" at -221 25 size 22x22 texture 28x28 anchor center layer 3 id c0_5 run TdCellClick(player, 0, 5) keep open
		image "td:textures/gui/tile_build.png" at -199 25 size 22x22 texture 28x28 anchor center layer 3 id c1_5 run TdCellClick(player, 1, 5) keep open
		image "td:textures/gui/tile_build.png" at -177 25 size 22x22 texture 28x28 anchor center layer 3 id c2_5 run TdCellClick(player, 2, 5) keep open
		image "td:textures/gui/tile_build.png" at -133 25 size 22x22 texture 28x28 anchor center layer 3 id c4_5 run TdCellClick(player, 4, 5) keep open
		image "td:textures/gui/tile_build.png" at -111 25 size 22x22 texture 28x28 anchor center layer 3 id c5_5 run TdCellClick(player, 5, 5) keep open
		image "td:textures/gui/tile_build.png" at -89 25 size 22x22 texture 28x28 anchor center layer 3 id c6_5 run TdCellClick(player, 6, 5) keep open
		image "td:textures/gui/tile_build.png" at -67 25 size 22x22 texture 28x28 anchor center layer 3 id c7_5 run TdCellClick(player, 7, 5) keep open
		image "td:textures/gui/tile_build.png" at 65 25 size 22x22 texture 28x28 anchor center layer 3 id c13_5 run TdCellClick(player, 13, 5) keep open
		image "td:textures/gui/tile_build.png" at 87 25 size 22x22 texture 28x28 anchor center layer 3 id c14_5 run TdCellClick(player, 14, 5) keep open
		image "td:textures/gui/tile_build.png" at 109 25 size 22x22 texture 28x28 anchor center layer 3 id c15_5 run TdCellClick(player, 15, 5) keep open
		image "td:textures/gui/tile_build.png" at -133 47 size 22x22 texture 28x28 anchor center layer 3 id c4_6 run TdCellClick(player, 4, 6) keep open
		image "td:textures/gui/tile_build.png" at -67 47 size 22x22 texture 28x28 anchor center layer 3 id c7_6 run TdCellClick(player, 7, 6) keep open
		image "td:textures/gui/tile_build.png" at -45 47 size 22x22 texture 28x28 anchor center layer 3 id c8_6 run TdCellClick(player, 8, 6) keep open
		image "td:textures/gui/tile_build.png" at -23 47 size 22x22 texture 28x28 anchor center layer 3 id c9_6 run TdCellClick(player, 9, 6) keep open
		image "td:textures/gui/tile_build.png" at -1 47 size 22x22 texture 28x28 anchor center layer 3 id c10_6 run TdCellClick(player, 10, 6) keep open
		image "td:textures/gui/tile_build.png" at 21 47 size 22x22 texture 28x28 anchor center layer 3 id c11_6 run TdCellClick(player, 11, 6) keep open
		image "td:textures/gui/tile_build.png" at 43 47 size 22x22 texture 28x28 anchor center layer 3 id c12_6 run TdCellClick(player, 12, 6) keep open
		image "td:textures/gui/tile_build.png" at 65 47 size 22x22 texture 28x28 anchor center layer 3 id c13_6 run TdCellClick(player, 13, 6) keep open
		image "td:textures/gui/tile_build.png" at -221 69 size 22x22 texture 28x28 anchor center layer 3 id c0_7 run TdCellClick(player, 0, 7) keep open
		image "td:textures/gui/tile_build.png" at -199 69 size 22x22 texture 28x28 anchor center layer 3 id c1_7 run TdCellClick(player, 1, 7) keep open
		image "td:textures/gui/tile_build.png" at -177 69 size 22x22 texture 28x28 anchor center layer 3 id c2_7 run TdCellClick(player, 2, 7) keep open
		image "td:textures/gui/tile_build.png" at -155 69 size 22x22 texture 28x28 anchor center layer 3 id c3_7 run TdCellClick(player, 3, 7) keep open
		image "td:textures/gui/tile_build.png" at -133 69 size 22x22 texture 28x28 anchor center layer 3 id c4_7 run TdCellClick(player, 4, 7) keep open
		image "td:textures/gui/tile_build.png" at -89 69 size 22x22 texture 28x28 anchor center layer 3 id c6_7 run TdCellClick(player, 6, 7) keep open
		image "td:textures/gui/tile_build.png" at -67 69 size 22x22 texture 28x28 anchor center layer 3 id c7_7 run TdCellClick(player, 7, 7) keep open
		image "td:textures/gui/tile_build.png" at -45 69 size 22x22 texture 28x28 anchor center layer 3 id c8_7 run TdCellClick(player, 8, 7) keep open
		image "td:textures/gui/tile_build.png" at -23 69 size 22x22 texture 28x28 anchor center layer 3 id c9_7 run TdCellClick(player, 9, 7) keep open
		image "td:textures/gui/tile_build.png" at 43 69 size 22x22 texture 28x28 anchor center layer 3 id c12_7 run TdCellClick(player, 12, 7) keep open
		image "td:textures/gui/tile_build.png" at 65 69 size 22x22 texture 28x28 anchor center layer 3 id c13_7 run TdCellClick(player, 13, 7) keep open

		# Пул звёзд уровней и эволюций (до 32 башен)
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st1 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st2 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st3 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st4 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st5 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st6 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st7 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st8 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st9 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st10 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st11 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st12 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st13 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st14 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st15 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st16 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st17 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st18 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st19 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st20 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st21 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st22 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st23 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st24 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st25 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st26 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st27 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st28 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st29 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st30 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st31 hidden
		text "" at 0 0 anchor center shadow scale 0.85 layer 6 id st32 hidden

		# 11 слотов обычных врагов + 1 слот Босса + тонкие HP-бары (64x64 HD + 60 FPS tween)
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e1 hidden
		bar "ehp1" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e2 hidden
		bar "ehp2" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e3 hidden
		bar "ehp3" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e4 hidden
		bar "ehp4" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e5 hidden
		bar "ehp5" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e6 hidden
		bar "ehp6" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e7 hidden
		bar "ehp7" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e8 hidden
		bar "ehp8" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e9 hidden
		bar "ehp9" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e10 hidden
		bar "ehp10" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_slime.png" at -221 -63 size 20x20 texture 64x64 anchor center layer 7 id e11 hidden
		bar "ehp11" value 100 max 100 at -221 -74 size 18x3 anchor center color #FF22C55E background #CC0F172A layer 8 hidden
		image "td:textures/gui/mob_boss.png" at -221 -63 size 28x28 texture 64x64 anchor center layer 8 id e12 hidden
		bar "ehp12" value 100 max 100 at -221 -79 size 26x4 anchor center color #FFEF4444 background #CC1A0A0A layer 9 hidden

		# 6 летающих HD прожектайлов (60 FPS tween)
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr1 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr2 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr3 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr4 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr5 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 10x10 texture 20x20 anchor center layer 9 id pr6 hidden

		# Компактная строка статуса
		box at 0 92 size 468x13 anchor center color #E60B1220 border #3338BDF8 radius 3 layer 11
		text "&e[1] Баллиста &7— наведите на башню для радиуса и статов, ЛКМ по клетке — строить" at 0 92 anchor center shadow layer 12 id status_txt
		text "&c&lБАЗА ПАЛА!" at -56 -18 anchor center shadow scale 1.4 layer 20 id end_title hidden
		text "&fНажми &eR &f(рестарт) или &eM &f(меню)" at -56 2 anchor center shadow layer 20 id end_sub hidden

		# Нижняя панель: 9 HD башен (включая Маяк-Баффер #9) + управление и тумблер Курсора
		box at 0 121 size 468x40 anchor center color #EE090E1A gradient #EE111827 border #FF1E293B radius 6 shadow blur layer 11
		box at -218 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -186 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -154 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -122 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -90 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -58 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -26 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at 6 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at 38 121 size 28x32 anchor center color #CC162032 border #3338BDF8 radius 4 layer 11
		box at -218 106 size 26x2 anchor center color #FFFACC15 radius 1 id sel_bar hidden layer 13
		image "td:textures/gui/tower_archer.png" at -218 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e&l[1] Эльфийская Баллиста &650з%nl%&8▪ &7Урон: &f15 &8| &7КД: &a0.4с &8| &7Дальн: &b78%nl%&6✦ 5★ Шквал: &fстреляет сразу по 2 целям"
		text "&e[1]50" at -218 131 anchor center shadow scale 0.85 layer 12 id lbl1
		image "td:textures/gui/tower_cannon.png" at -186 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6&l[2] Мортира «Вулкан» &695з%nl%&8▪ &7Урон: &f36 &c(Сплеш) &8| &7Дальн: &b74%nl%&6✦ 5★ Напалм: &fмега-сплеш + поджог врагов"
		text "&7[2]95" at -186 131 anchor center shadow scale 0.85 layer 12 id lbl2
		image "td:textures/gui/tower_frost.png" at -154 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b&l[3] Крио-Обелиск &685з%nl%&8▪ &7Аура: &f8 ур. &b(40% замедл. всех в радиусе 76)%nl%&6✦ 5★ Стужа: &f50% замедл. + 12% заморозка"
		text "&7[3]85" at -154 131 anchor center shadow scale 0.85 layer 12 id lbl3
		image "td:textures/gui/tower_mage.png" at -122 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d&l[4] Аркан-Шпиль &6130з%nl%&8▪ &7Урон: &f32 &8| &7Дальн: &b106%nl%&6✦ 5★ Убийца Боссов: &fx2 Боссам + 4% макс.HP"
		text "&7[4]130" at -122 131 anchor center shadow scale 0.85 layer 12 id lbl4
		image "td:textures/gui/tower_tesla.png" at -90 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico5 run TdSelectTool(player, 5) keep open tooltip "&b&l[5] Плазмо-Тесла &6115з%nl%&8▪ &7Урон: &f20 &b(Цепь на 3 цели) &8| &7Дальн: &b86%nl%&6✦ 5★ Шторм: &f5 целей + 90% урон + микро-стан"
		text "&7[5]115" at -90 131 anchor center shadow scale 0.85 layer 12 id lbl5
		image "td:textures/gui/tower_sniper.png" at -58 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico6 run TdSelectTool(player, 6) keep open tooltip "&c&l[6] Рейлган «Сокол» &6150з%nl%&8▪ &7Урон: &f56 &c(Крит 25%) &8| &7Дальн: &b172%nl%&6✦ 5★ Казнь: &fкрит 45% x2.5 + добивание <20% HP"
		text "&7[6]150" at -58 131 anchor center shadow scale 0.85 layer 12 id lbl6
		image "td:textures/gui/tower_poison.png" at -26 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico7 run TdSelectTool(player, 7) keep open tooltip "&a&l[7] Чумной Реактор &6105з%nl%&8▪ &7Урон: &f12 + &aКислота (DOT) &8| &7Дальн: &b80%nl%&6✦ 5★ Чума: &fзаражает всех врагов рядом"
		text "&7[7]105" at -26 131 anchor center shadow scale 0.85 layer 12 id lbl7
		image "td:textures/gui/tower_farm.png" at 6 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico8 run TdSelectTool(player, 8) keep open tooltip "&6&l[8] Монетный Двор &6140з%nl%&8▪ &7Доход: &6+28/+50/+80/+120з &7за волну%nl%&6✦ 5★ Золотой Дворец: &f+180з и +1 HP базе"
		text "&7[8]140" at 6 131 anchor center shadow scale 0.85 layer 12 id lbl8
		image "td:textures/gui/tower_buffer.png" at 38 115 size 19x19 texture 64x64 anchor center hover_scale 1.15 layer 12 id ico9 run TdSelectTool(player, 9) keep open tooltip "&d&l[9] Боевой Маяк (Баффер) &6125з%nl%&8▪ &7Бафф-аура: &d+18..42% урона &7башням в радиусе 68%nl%&6✦ 5★ Знамя Империи: &f+55% урон, +20% скоростр. и +25з"
		text "&7[9]125" at 38 131 anchor center shadow scale 0.85 layer 12 id lbl9
		button "&aСтарт/x2" at 94 112 size 56x15 anchor center run TdWaveOrSpeed(player) keep open tooltip "&aСтарт волны / Скорость x1↔x2 &8[Space]"
		toggle "Авто" id autowave at 152 112 size 52x15 anchor center value false color #22C55E tooltip "&aАвто-старт волн"
		toggle "Курсор" id cursortog at 208 112 size 52x15 anchor center value true color #38BDF8 tooltip "&bИконка выбранной башни у курсора &8[C]"
		button "&cПродать" at 94 129 size 56x15 anchor center run TdToggleSell(player) keep open tooltip "&cПродать башню за 70% &8[X]"
		button "Заново" at 152 129 size 52x15 anchor center run TdRestartCurrent(player) keep open tooltip "&eРестарт карты &8[R]"
		button "Меню" at 208 129 size 52x15 anchor center run TdOpenMenu(player) keep open tooltip "&eВыбор карты и кооп &8[M]"

		# Привязанная к курсору иконка текущей башни (универсальное свойство follow cursor)
		image "td:textures/gui/tower_archer.png" at 0 0 size 15x15 texture 64x64 anchor center follow cursor layer 24 id cursor_icon hidden

# ==========================================================
#  УПРАВЛЕНИЕ, СТРОИТЕЛЬСТВО (ДО 32 БАШЕН), МАЯК-БАФФЕР И ✦5★
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

function TdClearTool(p: player):
	set {-g} to {-td-host::%p%}
	if {-g} is not set or {-td-active::%{-g}%} is not 1:
		stop
	set {-td-tool::%p%} to 0
	set {-td-sell::%p%} to 0
	hide screen element "cursor_icon" from p
	hide screen element "sel_bar" from p
	TdShowRange(p, 0, 0, 0, 0)
	set text of screen element "lbl1" for p to "&7[1]50"
	set text of screen element "lbl2" for p to "&7[2]95"
	set text of screen element "lbl3" for p to "&7[3]85"
	set text of screen element "lbl4" for p to "&7[4]130"
	set text of screen element "lbl5" for p to "&7[5]115"
	set text of screen element "lbl6" for p to "&7[6]150"
	set text of screen element "lbl7" for p to "&7[7]105"
	set text of screen element "lbl8" for p to "&7[8]140"
	set text of screen element "lbl9" for p to "&7[9]125"

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
	set text of screen element "lbl9" for p to "&7[9]125"
	set {-sx} to -218 + (tool - 1) * 32
	show screen element "sel_bar" to p
	animate screen "sel_bar" for p to {-sx} 106 in 3 ticks
	set {-ctex} to "td:textures/gui/tower_archer.png"
	if tool is 1:
		set text of screen element "lbl1" for p to "&e[1]50"
		set text of screen element "status_txt" for p to "&e[1] Эльфийская Баллиста (50з) &8| &6✦5★: &fШквал по 2 целям"
	else if tool is 2:
		set {-ctex} to "td:textures/gui/tower_cannon.png"
		set text of screen element "lbl2" for p to "&6[2]95"
		set text of screen element "status_txt" for p to "&6[2] Мортира «Вулкан» (95з) &8| &6✦5★: &fНапалм (сплеш + ожог)"
	else if tool is 3:
		set {-ctex} to "td:textures/gui/tower_frost.png"
		set text of screen element "lbl3" for p to "&b[3]85"
		set text of screen element "status_txt" for p to "&b[3] Крио-Обелиск (85з) &8| Аура холода | &6✦5★: &fСтужа (50%% + фриз)"
	else if tool is 4:
		set {-ctex} to "td:textures/gui/tower_mage.png"
		set text of screen element "lbl4" for p to "&d[4]130"
		set text of screen element "status_txt" for p to "&d[4] Аркан-Шпиль (130з) &8| &6✦5★: &fУбийца Боссов (x2 + 4%% HP)"
	else if tool is 5:
		set {-ctex} to "td:textures/gui/tower_tesla.png"
		set text of screen element "lbl5" for p to "&b[5]115"
		set text of screen element "status_txt" for p to "&b[5] Плазмо-Тесла (115з) &8| &6✦5★: &fШторм (5 целей + стан)"
	else if tool is 6:
		set {-ctex} to "td:textures/gui/tower_sniper.png"
		set text of screen element "lbl6" for p to "&c[6]150"
		set text of screen element "status_txt" for p to "&c[6] Рейлган «Сокол» (150з) &8| &6✦5★: &fКазнь (<20%% HP + крит 45%%)"
	else if tool is 7:
		set {-ctex} to "td:textures/gui/tower_poison.png"
		set text of screen element "lbl7" for p to "&a[7]105"
		set text of screen element "status_txt" for p to "&a[7] Чумной Реактор (105з) &8| &6✦5★: &fЧума (заразная кислота)"
	else if tool is 8:
		set {-ctex} to "td:textures/gui/tower_farm.png"
		set text of screen element "lbl8" for p to "&6[8]140"
		set text of screen element "status_txt" for p to "&6[8] Монетный Двор (140з) &8| &6✦5★: &fДворец (+180з и +1 HP базе)"
	else if tool is 9:
		set {-ctex} to "td:textures/gui/tower_buffer.png"
		set text of screen element "lbl9" for p to "&d[9]125"
		set text of screen element "status_txt" for p to "&d[9] Боевой Маяк (125з) &8| Бафф +18..55%% урона соседним башням!"
	set image of screen element "cursor_icon" for p to {-ctex}
	if {-td-cur-on::%p%} is not 0:
		show screen element "cursor_icon" to p
	play sound ui.button.click to p with volume 0.4 pitch 1.3

function TdToggleSell(p: player):
	set {-g} to {-td-host::%p%}
	if {-g} is not set or {-td-active::%{-g}%} is not 1:
		stop
	if {-td-sell::%p%} is 1:
		TdClearTool(p)
	else:
		set {-td-sell::%p%} to 1
		set {-td-tool::%p%} to 0
		hide screen element "sel_bar" from p
		TdShowRange(p, 0, 0, 0, 0)
		set image of screen element "cursor_icon" for p to "td:textures/gui/icon_sell.png"
		if {-td-cur-on::%p%} is not 0:
			show screen element "cursor_icon" to p
		set text of screen element "status_txt" for p to "&c&lРЕЖИМ ПРОДАЖИ &7— клик по своей башне вернёт 70%% золота (X — отмена)"
		play sound ui.button.click to p with volume 0.4 pitch 0.9

function TdWaveOrSpeed(p: player):
	set {-g} to {-td-host::%p%}
	if {-g} is not set or {-td-active::%{-g}%} is not 1 or {-td-over::%{-g}%} is 1:
		stop
	if {-td-in-wave::%{-g}%} is 1:
		if {-td-speed::%{-g}%} is 1:
			set {-td-speed::%{-g}%} to 2
			TdLog({-g}, "&b⏩ Скорость: x2")
		else:
			set {-td-speed::%{-g}%} to 1
			TdLog({-g}, "&7▶ Скорость: x1")
		TdSyncSound({-g}, "ui.button.click", 0.4, 1.5)
		TdSyncHud({-g})
		stop
	TdNextWave({-g})

function TdTowerRange(g: player, tt: number, lvl: number):
	# Радиус в масштабе карты 352x176 (клетка 22x22) + бонус +12% на карте 3 (Цитадель)
	set {-r} to 78
	if tt is 2:
		set {-r} to 74
	else if tt is 3:
		set {-r} to 76
	else if tt is 4:
		set {-r} to 106
	else if tt is 5:
		set {-r} to 86
	else if tt is 6:
		set {-r} to 172
	else if tt is 7:
		set {-r} to 80
	else if tt is 8:
		set {-r} to 0
	else if tt is 9:
		set {-r} to 68
	if {-r} > 0:
		set {-r} to {-r} + (lvl - 1) * 7
		if lvl >= 5:
			add 11 to {-r}
		if {-td-map::%g%} is 3:
			set {-r} to round({-r} * 1.12)
	set {-td-tmp-rng} to {-r}

function TdCellClick(p: player, col: integer, row: integer):
	set {-g} to {-td-host::%p%}
	if {-g} is not set or {-td-active::%{-g}%} is not 1 or {-td-over::%{-g}%} is 1:
		stop
	if {-td-blk::%{-g}%::%col%_%row%} is 1:
		set text of screen element "status_txt" for p to "&cЗдесь опасная зона (топь / разлом) — строить нельзя!"
		play sound entity.villager.no to p with volume 0.4 pitch 0.9
		stop
	set {-tid} to {-td-grid::%{-g}%::%col%_%row%}
	if {-tid} is not set:
		set {-tid} to 0

	# Продажа
	if {-td-sell::%p%} is 1:
		if {-tid} is 0:
			set text of screen element "status_txt" for p to "&7Клетка пуста"
			stop
		set {-own} to {-td-towner::%{-g}%::%{-tid}%}
		if {-own} is not %p% and %p% is not {-g}:
			set text of screen element "status_txt" for p to "&cЭто башня игрока &f%{-own}%&c!"
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
		TdClearTool(p)
		TdSyncCell({-g}, col, row, {-tx}, {-ty}, "td:textures/gui/tile_build.png", "", 0, "#38BDF8")
		TdSyncHide({-g}, "st%{-tid}%")
		TdRecalcBuffs({-g})
		gui float "&6+%{-ref}%з" at {-tx} {-ty} color "#FACC15" for 16 ticks to {-td-online::%{-g}%::*}
		gui particle "gold" count 8 at {-tx} {-ty} spread 22 to {-td-online::%{-g}%::*}
		TdLog({-g}, "&c✖ %p% &7(+%{-ref}%з)")
		TdSyncSound({-g}, "entity.experience_orb.pickup", 0.6, 0.9)
		TdSyncGoldScore({-g})
		stop

	# Клик по построенной башне -> апгрейд / эволюция! (радиус показывается сам при наведении — по клику скрываем старый)
	if {-tid} > 0:
		TdShowRange(p, 0, 0, 0, 0)
		set {-lvl} to {-td-tl::%{-g}%::%{-tid}%}
		set {-tt} to {-td-tt::%{-g}%::%{-tid}%}
		set {-own} to {-td-towner::%{-g}%::%{-tid}%}
		set {-tx} to {-td-tx::%{-g}%::%{-tid}%}
		set {-ty} to {-td-ty::%{-g}%::%{-tid}%}
		TdTowerRange({-g}, {-tt}, {-lvl})

		if {-lvl} >= 5:
			set text of screen element "status_txt" for p to "&6&l✦5★ MAX &8| &7Владелец: &f%{-own}% &8| &7Дальность: &b%{-td-tmp-rng}%"
			stop
		set {-up} to 95
		if {-lvl} is 2:
			set {-up} to 210
		else if {-lvl} is 3:
			set {-up} to 360
		else if {-lvl} is 4:
			set {-up} to 570
		if {-td-gold::%p%} < {-up}:
			set {-nl} to {-lvl} + 1
			set text of screen element "status_txt" for p to "&cНужно &6%{-up}%з &cдля улучшения до ур.%{-nl}%!"
			play sound entity.villager.no to p with volume 0.5 pitch 1.0
			stop
		remove {-up} from {-td-gold::%p%}
		add {-up} to {-td-tcost::%{-g}%::%{-tid}%}
		add 1 to {-td-tl::%{-g}%::%{-tid}%}
		set {-nl} to {-td-tl::%{-g}%::%{-tid}%}
		TdRecalcBuffs({-g})
		if {-nl} < 5:
			gui particle "magic" count 10 at {-tx} {-ty} color "#38BDF8" spread 24 to {-td-online::%{-g}%::*}
			TdLog({-g}, "&b⬆ %p% &f→ Ур.%{-nl}%")
			TdSyncSound({-g}, "entity.player.levelup", 0.5, 1.45)
		else:
			set {-evo} to "Шквал 5★"
			if {-tt} is 2:
				set {-evo} to "Напалм 5★"
			else if {-tt} is 3:
				set {-evo} to "Стужа 5★"
			else if {-tt} is 4:
				set {-evo} to "Аркан 5★"
			else if {-tt} is 5:
				set {-evo} to "Шторм 5★"
			else if {-tt} is 6:
				set {-evo} to "Казнь 5★"
			else if {-tt} is 7:
				set {-evo} to "Чума 5★"
			else if {-tt} is 8:
				set {-evo} to "Дворец 5★"
			else if {-tt} is 9:
				set {-evo} to "Знамя 5★"
			gui float "&6&l✦ %{-evo}%!" at {-tx} {-ty} color "#FACC15" for 24 ticks to {-td-online::%{-g}%::*}
			gui particle "gold" count 18 at {-tx} {-ty} spread 38 to {-td-online::%{-g}%::*}
			TdLog({-g}, "&6✦ %p%: &e%{-evo}%")
			TdSyncSound({-g}, "ui.toast.challenge_complete", 0.75, 1.2)
		TdSyncGoldScore({-g})
		stop

	# Постройка новой башни (до 32 на карте; после постройки выбор сбрасывается!)
	set {-tool} to {-td-tool::%p%}
	if {-tool} is not set or {-tool} <= 0:
		TdClearTool(p)
		set text of screen element "status_txt" for p to "&7Сначала выберите башню &e1..9 &7на панели внизу!"
		stop
	set {-slot} to 0
	loop 32 times:
		set {-k} to %loop-number%
		if {-td-ta::%{-g}%::%{-k}%} is 0 and {-slot} is 0:
			set {-slot} to {-k}
	if {-slot} is 0:
		set text of screen element "status_txt" for p to "&cЛимит 32 башни! Прокачивайте построенные до ✦5★"
		stop

	set {-cost} to 50
	set {-sn} to "Баллиста"
	if {-tool} is 2:
		set {-cost} to 95
		set {-sn} to "Мортира"
	else if {-tool} is 3:
		set {-cost} to 85
		set {-sn} to "Обелиск"
	else if {-tool} is 4:
		set {-cost} to 130
		set {-sn} to "Аркан"
	else if {-tool} is 5:
		set {-cost} to 115
		set {-sn} to "Тесла"
	else if {-tool} is 6:
		set {-cost} to 150
		set {-sn} to "Рейлган"
	else if {-tool} is 7:
		set {-cost} to 105
		set {-sn} to "Реактор"
	else if {-tool} is 8:
		set {-cost} to 140
		set {-sn} to "Банк"
	else if {-tool} is 9:
		set {-cost} to 125
		set {-sn} to "Маяк"

	if {-td-gold::%p%} < {-cost}:
		TdShowRange(p, 0, 0, 0, 0)
		set text of screen element "status_txt" for p to "&cНе хватает золота! Нужно &6%{-cost}%з"
		play sound entity.villager.no to p with volume 0.5 pitch 1.0
		stop

	remove {-cost} from {-td-gold::%p%}
	set {-cx} to -221 + col * 22
	set {-cy} to -85 + row * 22
	set {-td-ta::%{-g}%::%{-slot}%} to 1
	set {-td-tt::%{-g}%::%{-slot}%} to {-tool}
	set {-td-tl::%{-g}%::%{-slot}%} to 1
	set {-td-tcd::%{-g}%::%{-slot}%} to 0
	set {-td-tcost::%{-g}%::%{-slot}%} to {-cost}
	set {-td-tbuff::%{-g}%::%{-slot}%} to 1.0
	set {-td-tfast::%{-g}%::%{-slot}%} to 0
	set {-td-towner::%{-g}%::%{-slot}%} to %p%
	set {-td-tcol::%{-g}%::%{-slot}%} to col
	set {-td-trow::%{-g}%::%{-slot}%} to row
	set {-td-tx::%{-g}%::%{-slot}%} to {-cx}
	set {-td-ty::%{-g}%::%{-slot}%} to {-cy}
	set {-td-grid::%{-g}%::%col%_%row%} to {-slot}
	if {-slot} > {-td-tmax::%{-g}%}:
		set {-td-tmax::%{-g}%} to {-slot}

	TdRecalcBuffs({-g})
	# Сразу отвязываем иконку от курсора и сбрасываем выбор после 1 постройки!
	TdClearTool(p)
	gui particle "spark" count 8 at {-cx} {-cy} color "#38BDF8" spread 20 to {-td-online::%{-g}%::*}
	TdLog({-g}, "&a+ %p%: &f%{-sn}%")
	TdSyncSound({-g}, "block.anvil.use", 0.35, 1.5)
	TdSyncGoldScore({-g})

# ==========================================================
#  ВОЛНЫ И СПАВН (16 ВИДОВ МОБОВ + ОСОБЕННОСТИ 5 КАРТ)
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
			TdLog(g, "&4☠ БОСС: Владыка Края!")
			TdSyncSound(g, "entity.wither.spawn", 0.55, 0.85)
		else if {-bkind} >= 3:
			TdLog(g, "&b☠ БОСС: Император Стужи!")
			TdSyncSound(g, "entity.ender_dragon.growl", 0.55, 0.9)
		else:
			TdLog(g, "&c☠ БОСС: Архидемон!")
			TdSyncSound(g, "entity.ender_dragon.growl", 0.5, 1.1)
	else:
		TdLog(g, "&e⚔ Волна %{-w}%/%{-mw}%")
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

	# 2. Враги: регенерация, горение, яд, плавное движение (tween)
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
				TdDamageEnemy(g, {-ei}, {-td-eburn-d::%g%::%{-ei}%}, 0, 0, 1)
			if {-td-ea::%g%::%{-ei}%} is 1 and {-td-epoison::%g%::%{-ei}%} > 0:
				remove 1 from {-td-epoison::%g%::%{-ei}%}
				TdDamageEnemy(g, {-ei}, {-td-epoison-d::%g%::%{-ei}%}, 0, 0, 1)
			if {-td-ea::%g%::%{-ei}%} is 1:
				TdMoveEnemy(g, {-ei}, {-anim})
			if {-td-ea::%g%::%{-ei}%} is 1 and {-td-eboss::%g%::%{-ei}%} is 1:
				set {-boss-alive} to 1
				TdSyncBossBar(g, {-td-ehp::%g%::%{-ei}%}, 0)
	if {-boss-alive} is 0 and {-td-boss-vis::%g%} is 1:
		set {-td-boss-vis::%g%} to 0
		TdSyncHide(g, "boss_bar")

	# 3. Полёт прожектайлов
	if {-td-prcnt::%g%} > 0:
		TdMoveProjectiles(g, {-anim})

	# 4. Стрельба всех башен (кроме Банка #8 и Маяка #9)
	set {-tmax} to {-td-tmax::%g%}
	if {-tmax} > 0:
		loop {-tmax} times:
			set {-ti} to %loop-number%
			if {-td-ta::%g%::%{-ti}%} is 1 and {-td-tt::%g%::%{-ti}%} < 8:
				if {-td-tcd::%g%::%{-ti}%} > 0:
					remove 1 from {-td-tcd::%g%::%{-ti}%}
				else:
					TdTowerTick(g, {-ti})

	# 5. Завершение волны + доход от Банков (#8) и ✦5★ Маяков (#9)!
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
					if {-td-ta::%g%::%{-bi}%} is 1:
						if {-td-tt::%g%::%{-bi}%} is 8:
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
						else if {-td-tt::%g%::%{-bi}%} is 9 and {-td-tl::%g%::%{-bi}%} >= 5:
							set {-bo} to {-td-towner::%g%::%{-bi}%}
							add 25 to {-td-gold::%{-bo}%}
							add 25 to {-bank-tot}
			set {-sc-add} to 100 + {-td-wave::%g%} * 15
			if {-td-map::%g%} is 5:
				set {-sc-add} to round({-sc-add} * 1.5)
			add {-sc-add} to {-td-score::%g%}
			TdSyncHud(g)
			if {-td-wave::%g%} >= {-td-maxw::%g%}:
				TdWin(g)
			else:
				if {-bank-tot} > 0:
					gui float "&6+%{-bank-tot}%з Казна!" at -56 -30 color "#FACC15" for 24 ticks to {-td-online::%g%::*}
					TdLog(g, "&6💰 Казна: +%{-bank-tot}%з")
				TdLog(g, "&a✔ Волна %{-td-wave::%g%}% (+%{-bonus}%з)")
				TdSyncSound(g, "entity.player.levelup", 0.5, 1.25)

function TdSpawnNext(g: player):
	set {-w} to {-td-wave::%g%}
	set {-d} to {-td-diff::%g%}
	set {-m} to {-td-map::%g%}
	set {-dmul} to 1.0
	if {-d} is 1:
		set {-dmul} to 0.85
	else if {-d} is 3:
		set {-dmul} to 1.25
	if {-m} is 4:
		set {-dmul} to {-dmul} * 1.15
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

		# Шаг адаптирован под клетку 22x22 и 4-tick цикл (6.5 px за шаг + 60 FPS tween!)
		set {-hp} to round((35 + {-w} * 25 + {-w} * {-w} * 2.6) * {-dmul})
		set {-spd} to 6.4 + {-w} * 0.14
		if {-m} is 2:
			set {-spd} to {-spd} * 1.08
		set {-rew} to 10 + round({-w} * 0.65)
		if {-m} is 4:
			set {-rew} to round({-rew} * 1.2)
		set {-cd} to 4 - floor({-w} / 10)
		if {-cd} < 2:
			set {-cd} to 2
		set {-tex} to "td:textures/gui/mob_slime.png"
		set {-reg} to 0
		set {-imm} to 0
		set {-bdmg} to 1
		set {-dodge} to 0
		set {-shield} to 0

		# 13 видов обычных и элитных врагов (включая Ассасина, Храмовника и Вестника Бездны!)
		set {-m13} to {-w} % 13
		if {-m13} is 2:
			set {-tex} to "td:textures/gui/mob_goblin.png"
			set {-hp} to round({-hp} * 0.72)
			set {-spd} to {-spd} + 2.6
			set {-cd} to 2
		else if {-m13} is 3:
			set {-tex} to "td:textures/gui/mob_orc.png"
			set {-hp} to round({-hp} * 1.18)
		else if {-m13} is 4:
			set {-tex} to "td:textures/gui/mob_spider.png"
			set {-hp} to round({-hp} * 0.82)
			set {-spd} to {-spd} + 2.2
			set {-cd} to 2
		else if {-m13} is 5:
			set {-tex} to "td:textures/gui/mob_knight.png"
			set {-hp} to round({-hp} * 1.38)
			set {-spd} to {-spd} - 0.6
		else if {-m13} is 6:
			set {-tex} to "td:textures/gui/mob_ghost.png"
			set {-hp} to round({-hp} * 0.88)
			set {-spd} to {-spd} + 1.9
		else if {-m13} is 7:
			set {-tex} to "td:textures/gui/mob_necromancer.png"
			set {-hp} to round({-hp} * 1.12)
			set {-reg} to round({-hp} * 0.035)
			add 4 to {-rew}
		else if {-m13} is 8:
			set {-tex} to "td:textures/gui/mob_blaze.png"
			set {-hp} to round({-hp} * 1.08)
			set {-spd} to {-spd} + 1.1
			set {-imm} to 1
			add 4 to {-rew}
		else if {-m13} is 9:
			# НОВЫЙ: Теневой Ассасин (25% шанс уворота от атак!)
			set {-tex} to "td:textures/gui/mob_assassin.png"
			set {-hp} to round({-hp} * 0.85)
			set {-spd} to {-spd} + 2.8
			set {-dodge} to 25
			add 5 to {-rew}
		else if {-m13} is 10:
			set {-tex} to "td:textures/gui/mob_golem.png"
			set {-hp} to round({-hp} * 1.72)
			set {-spd} to {-spd} - 1.3
			add 6 to {-rew}
		else if {-m13} is 11:
			# НОВЫЙ: Храмовник Эгиды (щит поглощает 35% прямого урона!)
			set {-tex} to "td:textures/gui/mob_paladin.png"
			set {-hp} to round({-hp} * 1.35)
			set {-shield} to 1
			add 6 to {-rew}
		else if {-m13} is 12:
			set {-tex} to "td:textures/gui/mob_ravager.png"
			set {-hp} to round({-hp} * 1.85)
			set {-spd} to {-spd} - 0.8
			set {-bdmg} to 2
			add 8 to {-rew}
		else if {-m13} is 0:
			# НОВЫЙ: Вестник Бездны (реген + уклонение 15% + 2 урона базе!)
			set {-tex} to "td:textures/gui/mob_voidwalker.png"
			set {-hp} to round({-hp} * 1.55)
			set {-spd} to {-spd} + 1.0
			set {-reg} to round({-hp} * 0.025)
			set {-dodge} to 15
			set {-bdmg} to 2
			add 9 to {-rew}

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
		set {-td-edodge::%g%::%{-slot}%} to {-dodge}
		set {-td-eshield::%g%::%{-slot}%} to {-shield}
		set {-td-eslow::%g%::%{-slot}%} to 0
		set {-td-eslow-f::%g%::%{-slot}%} to 0.6
		set {-td-efreeze::%g%::%{-slot}%} to 0
		set {-td-eburn::%g%::%{-slot}%} to 0
		set {-td-epoison::%g%::%{-slot}%} to 0
		set {-td-etex::%g%::%{-slot}%} to {-tex}
		TdSyncImg(g, "e%{-slot}%", {-tex})
		TdSyncPos(g, "e%{-slot}%", {-sx}, {-sy})
		TdSyncShow(g, "e%{-slot}%")
		set {-hy} to {-sy} - 11
		TdSyncBar(g, "ehp%{-slot}%", {-hp}, {-hp})
		TdSyncPos(g, "ehp%{-slot}%", {-sx}, {-hy})
		TdSyncShow(g, "ehp%{-slot}%")
		stop

	# Спавн Босса (слот 12)
	if {-td-spawn-boss::%g%} > 0 and {-td-ea::%g%::12} is 0:
		set {-bk} to {-td-spawn-boss::%g%}
		set {-td-spawn-boss::%g%} to 0
		set {-bhp} to round((520 + {-bk} * {-bk} * 420) * {-dmul})
		set {-bspd} to 4.2 + {-bk} * 0.15
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
		set {-td-edodge::%g%::12} to 0
		set {-td-eshield::%g%::12} to 0
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
		set {-bhy} to {-sy} - 16
		TdSyncBar(g, "ehp12", {-bhp}, {-bhp})
		TdSyncPos(g, "ehp12", {-sx}, {-bhy})
		TdSyncShow(g, "ehp12")
		TdSyncBossBar(g, {-bhp}, {-bhp})
		TdSyncShow(g, "boss_bar")
		gui particle "explosion" count 16 at {-sx} {-sy} color "#EF4444" spread 36 to {-td-online::%g%::*}

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
			gui float "&c-%{-dmg}% HP!" at {-tx} {-ty} color "#EF4444" for 18 ticks to {-td-online::%g%::*}
			TdLog(g, "&c💔 Прорыв: -%{-dmg}% HP!")
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
	set {-hy} to {-y} - 11
	if {-td-eboss::%g%::%i%} is 1:
		set {-hy} to {-y} - 16
	TdSyncAnim(g, "ehp%i%", {-x}, {-hy}, anim)

# ==========================================================
#  БАШНИ, АУРА МАЯКА, ЭВОЛЮЦИИ ✦5★ И ПРОЖЕКТАЙЛЫ
# ==========================================================

function TdTowerTick(g: player, tid: number):
	set {-type} to {-td-tt::%g%::%tid%}
	set {-lvl} to {-td-tl::%g%::%tid%}
	set {-tx} to {-td-tx::%g%::%tid%}
	set {-ty} to {-td-ty::%g%::%tid%}
	set {-evo} to 0
	if {-lvl} >= 5:
		set {-evo} to 1

	TdTowerRange(g, {-type}, {-lvl})
	set {-range} to {-td-tmp-rng}
	set {-r2} to {-range} * {-range}

	set {-dmg} to 15
	set {-cd} to 2
	set {-pspd} to 46
	set {-ptex} to "td:textures/gui/proj_arrow.png"
	if {-type} is 2:
		set {-dmg} to 36
		set {-cd} to 5
		set {-pspd} to 34
		set {-ptex} to "td:textures/gui/proj_cannon.png"
	else if {-type} is 3:
		set {-dmg} to 8
		set {-cd} to 4
		set {-pspd} to 40
		set {-ptex} to "td:textures/gui/proj_frost.png"
	else if {-type} is 4:
		set {-dmg} to 32
		set {-cd} to 3
		set {-pspd} to 36
		set {-ptex} to "td:textures/gui/proj_mage.png"
	else if {-type} is 5:
		set {-dmg} to 20
		set {-cd} to 3
		set {-pspd} to 50
		set {-ptex} to "td:textures/gui/proj_tesla.png"
	else if {-type} is 6:
		set {-dmg} to 56
		set {-cd} to 6
		set {-pspd} to 64
		set {-ptex} to "td:textures/gui/proj_sniper.png"
	else if {-type} is 7:
		set {-dmg} to 12
		set {-cd} to 3
		set {-pspd} to 40
		set {-ptex} to "td:textures/gui/proj_poison.png"

	# Учитываем бафф Боевого Маяка (#9) и бонус карты (Каньон +10%, Разлом +20%)
	set {-bmul} to {-td-tbuff::%g%::%tid%}
	if {-bmul} is not set:
		set {-bmul} to 1.0
	set {-mapMul} to 1.0
	if {-td-map::%g%} is 2:
		set {-mapMul} to 1.1
	else if {-td-map::%g%} is 5:
		set {-mapMul} to 1.2
	set {-dmg} to round({-dmg} * (1 + ({-lvl} - 1) * 0.55) * {-bmul} * {-mapMul})
	if {-evo} is 1:
		set {-dmg} to round({-dmg} * 1.4)
	if {-td-tfast::%g%::%tid%} is 1 and {-cd} > 1:
		remove 1 from {-cd}

	# Крио-Обелиск (#3): аура холода по всему радиусу
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
					TdDamageEnemy(g, {-ei}, {-dmg}, 9, {-frz}, 1)
		if {-hit-any} is 1:
			set {-td-tcd::%g%::%tid%} to {-cd}
			gui particle "ice" count 10 at {-tx} {-ty} spread 36 to {-td-online::%g%::*}
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
		gui particle "explosion" count 12 at hx hy spread 28 to {-td-online::%g%::*}
		TdSyncSound(g, "entity.generic.explode", 0.15, 1.65)
		set {-rad2} to 1600
		if evo is 1:
			set {-rad2} to 3136
			gui particle "fire" count 10 at hx hy spread 32 to {-td-online::%g%::*}
		loop 12 times:
			set {-si} to %loop-number%
			if {-td-ea::%g%::%{-si}%} is 1:
				set {-sdx} to {-td-ex::%g%::%{-si}%} - hx
				set {-sdy} to {-td-ey::%g%::%{-si}%} - hy
				if {-sdx} * {-sdx} + {-sdy} * {-sdy} <= {-rad2}:
					TdDamageEnemy(g, {-si}, dmg, 0, 0, 1)
					if evo is 1 and {-td-ea::%g%::%{-si}%} is 1:
						set {-td-eburn::%g%::%{-si}%} to 6
						set {-td-eburn-d::%g%::%{-si}%} to round(dmg * 0.28)
	else if ptype is 4:
		set {-fdmg} to dmg
		if evo is 1:
			if {-td-eboss::%g%::%tgt%} is 1:
				set {-fdmg} to round({-fdmg} * 2.0)
			add round({-td-emhp::%g%::%tgt%} * 0.04) to {-fdmg}
		gui particle "magic" count 7 at hx hy spread 18 to {-td-online::%g%::*}
		TdSyncSound(g, "entity.illusioner.cast_spell", 0.17, 1.5)
		TdDamageEnemy(g, tgt, {-fdmg}, 0, 0, 1)
	else if ptype is 5:
		gui particle "spark" count 10 at hx hy color "#38BDF8" spread 24 to {-td-online::%g%::*}
		TdSyncSound(g, "entity.lightning_bolt.impact", 0.13, 1.9)
		set {-maxh} to 3
		set {-crad2} to 3844
		set {-cmul} to 0.75
		set {-stun} to 0
		if evo is 1:
			set {-maxh} to 5
			set {-crad2} to 6400
			set {-cmul} to 0.9
			set {-stun} to 2
		TdDamageEnemy(g, tgt, dmg, 0, {-stun}, 1)
		set {-hits} to 1
		loop 12 times:
			set {-si} to %loop-number%
			if {-si} is not tgt and {-hits} < {-maxh} and {-td-ea::%g%::%{-si}%} is 1:
				set {-sdx} to {-td-ex::%g%::%{-si}%} - hx
				set {-sdy} to {-td-ey::%g%::%{-si}%} - hy
				if {-sdx} * {-sdx} + {-sdy} * {-sdy} <= {-crad2}:
					TdDamageEnemy(g, {-si}, round(dmg * {-cmul}), 0, {-stun}, 1)
					add 1 to {-hits}
	else if ptype is 6:
		set {-fdmg} to dmg
		if evo is 1:
			chance of 45%:
				set {-fdmg} to round(dmg * 2.5)
				gui float "&c&l-%{-fdmg}% CRIT!" at hx hy color "#FF4444" for 14 ticks to {-td-online::%g%::*}
				gui particle "spark" count 10 at hx hy color "#EF4444" spread 24 to {-td-online::%g%::*}
			if {-td-eboss::%g%::%tgt%} is 0 and {-td-ehp::%g%::%tgt%} <= {-td-emhp::%g%::%tgt%} * 0.2:
				set {-fdmg} to 999999
				gui float "&4&l☠ КАЗНЬ!" at hx hy color "#EF4444" for 16 ticks to {-td-online::%g%::*}
		else:
			chance of 25%:
				set {-fdmg} to dmg * 2
				gui float "&c-%{-fdmg}%!" at hx hy color "#FF6B6B" for 12 ticks to {-td-online::%g%::*}
				gui particle "spark" count 8 at hx hy color "#EF4444" spread 18 to {-td-online::%g%::*}
		TdSyncSound(g, "entity.arrow.hit_player", 0.22, 1.6)
		TdDamageEnemy(g, tgt, {-fdmg}, 0, 0, 1)
	else if ptype is 7:
		gui particle "poison" count 8 at hx hy spread 22 to {-td-online::%g%::*}
		TdSyncSound(g, "entity.splash_potion.break", 0.18, 1.4)
		TdDamageEnemy(g, tgt, dmg, 0, 0, 1)
		if {-td-ea::%g%::%tgt%} is 1:
			set {-td-epoison::%g%::%tgt%} to 10
			set {-td-epoison-d::%g%::%tgt%} to round(dmg * 0.7)
		if evo is 1:
			loop 12 times:
				set {-si} to %loop-number%
				if {-si} is not tgt and {-td-ea::%g%::%{-si}%} is 1:
					set {-sdx} to {-td-ex::%g%::%{-si}%} - hx
					set {-sdy} to {-td-ey::%g%::%{-si}%} - hy
					if {-sdx} * {-sdx} + {-sdy} * {-sdy} <= 2304:
						set {-td-epoison::%g%::%{-si}%} to 10
						set {-td-epoison-d::%g%::%{-si}%} to round(dmg * 0.6)
	else:
		TdSyncSound(g, "entity.arrow.shoot", 0.17, 1.45)
		TdDamageEnemy(g, tgt, dmg, 0, 0, 0)

function TdDamageEnemy(g: player, i: number, dmg: number, slow: number, freeze: number, bypassDodge: number):
	if {-td-ea::%g%::%i%} is not 1:
		stop
	# Уклонение Ассасина и Вестника Бездны от обычных стрел
	if bypassDodge is 0 and {-td-edodge::%g%::%i%} > 0:
		chance of {-td-edodge::%g%::%i%}%:
			gui float "&dУКЛОН!" at {-td-ex::%g%::%i%} {-td-ey::%g%::%i%} color "#C084FC" for 10 ticks to {-td-online::%g%::*}
			stop
	set {-realDmg} to dmg
	# Энергетический щит Храмовника Эгиды (-35% прямого урона)
	if {-td-eshield::%g%::%i%} is 1 and {-realDmg} < 50000:
		set {-realDmg} to round({-realDmg} * 0.65)
	remove {-realDmg} from {-td-ehp::%g%::%i%}
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
		set {-sc-kill} to {-rew}
		if {-td-map::%g%} is 5:
			set {-sc-kill} to round({-rew} * 1.5)
		add {-sc-kill} to {-td-score::%g%}
		gui float "&6+%{-share}%з" at {-kx} {-ky} color "#FACC15" for 14 ticks to {-td-online::%g%::*}
		gui particle "gold" count 6 at {-kx} {-ky} spread 16 to {-td-online::%g%::*}
		if {-td-eboss::%g%::%i%} is 1:
			set {-td-boss-vis::%g%} to 0
			TdSyncHide(g, "boss_bar")
			gui particle "gold" count 24 at {-kx} {-ky} spread 44 to {-td-online::%g%::*}
			TdLog(g, "&6🏆 Босс повержен!")
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
	TdLog(g, "&c✖ Поражение на в.%{-td-wave::%g%}%")
	TdSaveScore(g)

function TdWin(g: player):
	set {-td-over::%g%} to 1
	set {-wbon} to {-td-hp::%g%} * 100 * {-td-diff::%g%}
	if {-td-map::%g%} is 5:
		set {-wbon} to round({-wbon} * 1.5)
	add {-wbon} to {-td-score::%g%}
	TdSyncHud(g)
	TdSyncTxt(g, "end_title", "&6&lПОБЕДА! ВСЕ %{-td-maxw::%g%}% ВОЛН ПРОЙДЕНЫ!")
	TdSyncTxt(g, "end_sub", "&aСчёт: &e%{-td-score::%g%}% &7— нажми &eR &7(ещё раз) или &eM &7(меню)")
	TdSyncShow(g, "end_title")
	TdSyncShow(g, "end_sub")
	fade screen of {-td-online::%g%::*} to #FFD700 in 3 stay 4 out 14 ticks
	gui particle "gold" count 32 at -56 -10 spread 80 to {-td-online::%g%::*}
	TdLog(g, "&6🏆 ПОБЕДА! %{-td-score::%g%}% очк.")
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
