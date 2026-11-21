# ==========================================================
#  td.sc — Tower Defense Ultra HD (Кооп до 4 игроков!)
#          3 HD-карты (448x224) | 3 сложности (10 / 20 / 30 волн)
#          8 башен + Эволюция ✦5★ | 13 видов мобов и боссов
#          Свободная постройка + круг радиуса + летающие прожектайлы!
#  Команды:
#    /td               — меню карты, сложности и команды
#    /td coop <игрок>  — пригласить друга в кооп (до 4 игроков!)
#    /td join          — принять приглашение в кооп-команду
#  Управление:
#    1..8  — выбрать башню | ЛКМ по траве — построить
#    ЛКМ по башне — показать радиус, владельца и улучшить (до ✦5★!)
#    X     — продать (70%) | Space — волна / скорость x1 <-> x2
#    R     — рестарт | M — меню
#  Текстуры: распакуйте td-resources.zip в scriptcraft/resources/
# ==========================================================

on load:
	clear {-td-active::*}
	clear {-td-host::*}
	clear {-td-team::*}
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
			send "&eИспользование: &f/td coop <ник_игрока> &7(можно до 4 игроков!)" to player
			stop
		if arg-2 is player:
			send "&cНельзя пригласить самого себя!" to player
			stop
		# Убедимся, что хост в своей команде
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
	if arg-1 is "join" or "accept":
		if {-td-inv::%player%} is not set:
			send "&cУ вас нет активных приглашений в кооп." to player
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

# --- Игровой цикл (тикает только хост комнаты -> синк всем 1..4 игрокам) ---

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
	delete {-td-host::%p%}
	remove %p% from {-td-team::%{-g}%::*}
	if %p% is {-g} or {-td-team::%{-g}%::*} is empty:
		# Хост вышел — завершаем сессию для всех
		delete {-td-active::%{-g}%}
		delete {-td-over::%{-g}%}
		loop {-td-team::%{-g}%::*}:
			set {-tp} to %loop-value%
			delete {-td-host::%{-tp}%}
			send "&e[TD Кооп] &7Хост %p% завершил игру." to {-tp}
		clear {-td-team::%{-g}%::*}
	else:
		TdSyncStatus({-g}, "&e%p% покинул команду.")
		TdSyncHud({-g})

# ==========================================================
#  ХЕЛПЕРЫ СИНХРОНИЗАЦИИ ДЛЯ 1..4 ИГРОКОВ
# ==========================================================

function TdSyncPos(g: player, id: text, x: number, y: number):
	loop {-td-team::%g%::*}:
		set {-tp} to %loop-value%
		set position of screen element "%id%" for {-tp} to x y

function TdSyncImg(g: player, id: text, tex: text):
	loop {-td-team::%g%::*}:
		set {-tp} to %loop-value%
		set image of screen element "%id%" for {-tp} to tex

function TdSyncTxt(g: player, id: text, txt: text):
	loop {-td-team::%g%::*}:
		set {-tp} to %loop-value%
		set text of screen element "%id%" for {-tp} to txt

function TdSyncShow(g: player, id: text):
	loop {-td-team::%g%::*}:
		set {-tp} to %loop-value%
		show screen element "%id%" to {-tp}

function TdSyncHide(g: player, id: text):
	loop {-td-team::%g%::*}:
		set {-tp} to %loop-value%
		hide screen element "%id%" from {-tp}

function TdSyncStatus(g: player, txt: text):
	TdSyncTxt(g, "status_txt", txt)

function TdSyncSound(g: player, snd: text, vol: number, pit: number):
	loop {-td-team::%g%::*}:
		set {-tp} to %loop-value%
		play sound %snd% to {-tp} with volume vol pitch pit

function TdSyncBossBar(g: player, val: number, mx: number):
	loop {-td-team::%g%::*}:
		set {-tp} to %loop-value%
		if mx > 0:
			set max of screen bar "boss_bar" for {-tp} to mx
		set value of screen bar "boss_bar" for {-tp} to val

function TdSyncHud(g: player):
	set {-sp} to "x1"
	if {-td-speed::%g%} is 2:
		set {-sp} to "&b&lx2"
	set {-pcnt} to size of {-td-team::%g%::*}
	set {-names} to join {-td-team::%g%::*} with ", "
	set {-tstr} to "&7Соло &7[%{-sp}%&7]"
	if {-pcnt} > 1:
		set {-tstr} to "&dКооп(%{-pcnt}%): &f%{-names}% &7[%{-sp}%&7]"
	loop {-td-team::%g%::*}:
		set {-tp} to %loop-value%
		set text of screen element "hud_wave" for {-tp} to "&f%{-td-wave::%g%}%/%{-td-maxw::%g%}%"
		set text of screen element "hud_gold" for {-tp} to "&6%{-td-gold::%{-tp}%}%"
		set text of screen element "hud_hp" for {-tp} to "&c%{-td-hp::%g%}%"
		set text of screen element "hud_score" for {-tp} to "&a%{-td-score::%g%}%"
		set text of screen element "hud_team" for {-tp} to {-tstr}

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

# ==========================================================
#  МЕНЮ ВЫБОРА КАРТЫ, СЛОЖНОСТИ И КОМАНДЫ (ДО 4 ИГРОКОВ)
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
		bar "bg_menu" value 0 max 1 at 0 0 size 430x252 anchor center background #EE0B1220 layer 0
		bar "bg_hdr" value 1 max 1 at 0 -114 size 426x22 anchor center color #FF2563EB background #FF2563EB layer 1
		text "&f&lTOWER DEFENSE ULTRA HD" at 0 -114 anchor center shadow layer 2
		text "&7Кооп до 4 игроков: &e/td coop <ник> &7(друг: &a/td join&7) | 8 башен + ✦5★ Эволюция!" at 0 -92 anchor center shadow layer 2

		# 3 HD превью карт
		image "td:textures/gui/map_valley.png" at -132 -40 size 124x62 texture 896x448 anchor center layer 2 run TdPickMap(player, 1) keep open tooltip "&aИзумрудная долина\n&7Классический S-маршрут"
		text "&a1. Долина" at -132 -1 anchor center shadow layer 2 id m_lbl1

		image "td:textures/gui/map_volcano.png" at 0 -40 size 124x62 texture 896x448 anchor center layer 2 run TdPickMap(player, 2) keep open tooltip "&6Лавовый каньон\n&7Двойной зигзаг — много поворотов!"
		text "&72. Каньон" at 0 -1 anchor center shadow layer 2 id m_lbl2

		image "td:textures/gui/map_ice.png" at 132 -40 size 124x62 texture 896x448 anchor center layer 2 run TdPickMap(player, 3) keep open tooltip "&bЛедяная цитадель\n&7Быстрый арктический серпантин!"
		text "&73. Цитадель" at 132 -1 anchor center shadow layer 2 id m_lbl3

		# 3 уровня сложности
		text "&eСложность:" at 0 22 anchor center shadow layer 2
		button "&aЛёгкий (10 волн)" at -122 42 size 112x20 anchor center layer 2 run TdPickDiff(player, 1) keep open tooltip "&aЛёгкий уровень\n&710 волн | 2 босса | 15 HP | 140з"
		button "&eСредний (20 волн)" at 0 42 size 112x20 anchor center layer 2 run TdPickDiff(player, 2) keep open tooltip "&eСредний уровень\n&720 волн | 4 босса | 10 HP | 120з"
		button "&cТяжёлый (30 волн)" at 122 42 size 112x20 anchor center layer 2 run TdPickDiff(player, 3) keep open tooltip "&cТяжёлый уровень\n&730 волн | 6 боссов | 8 HP | 110з"

		text "&7Выбрано: &aДолина &7| &eСредний" at 0 68 anchor center shadow layer 2 id menu_info
		text "&dКоманда (1/4): &f%p%" at 0 82 anchor center shadow layer 2 id menu_team
		button "&a&l▶ ИГРАТЬ" at -60 106 size 124x22 anchor center layer 2 run TdStartGame(player) keep open
		button "&6Топ-5" at 72 106 size 92x22 anchor center layer 2 run TdShowTop(player) keep open
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

	# Фильтруем команду (оставляем только онлайн-игроков, хост всегда первый)
	clear {-tmp-team::*}
	add %host% to {-tmp-team::*}
	loop {-td-team::%host%::*}:
		set {-cand} to %loop-value%
		if {-cand} is not %host% and {-cand} is online:
			if size of {-tmp-team::*} < 4:
				add {-cand} to {-tmp-team::*}
	clear {-td-team::%host%::*}
	loop {-tmp-team::*}:
		add %loop-value% to {-td-team::%host%::*}

	set {-td-active::%host%} to 1
	set {-td-over::%host%} to 0
	set {-td-wave::%host%} to 0
	set {-td-score::%host%} to 0
	set {-td-speed::%host%} to 1
	set {-td-auto::%host%} to 0
	set {-td-auto-cd::%host%} to 0
	set {-td-in-wave::%host%} to 0
	set {-td-to-spawn::%host%} to 0
	set {-td-spawn-boss::%host%} to 0
	set {-td-spawn-cd::%host%} to 0
	set {-td-fx-cd::%host%} to 0

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
		# Верхняя HUD-панель (без PNG — не бывает битых текстур!)
		bar "bg_hud" value 0 max 1 at 0 -131 size 456x22 anchor center background #DD0F172A layer 11
		image "td:textures/gui/icon_wave.png" at -214 -131 size 12x12 texture 32x32 anchor center layer 12 tooltip "&eТекущая волна"
		text "&f0/20" at -188 -131 anchor center shadow layer 12 id hud_wave
		image "td:textures/gui/icon_coin.png" at -142 -131 size 12x12 texture 32x32 anchor center layer 12 tooltip "&6Ваше личное золото"
		text "&6120" at -116 -131 anchor center shadow layer 12 id hud_gold
		image "td:textures/gui/icon_heart.png" at -68 -131 size 12x12 texture 32x32 anchor center layer 12 tooltip "&cЗдоровье базы"
		text "&c10" at -48 -131 anchor center shadow layer 12 id hud_hp
		image "td:textures/gui/icon_trophy.png" at -12 -131 size 12x12 texture 32x32 anchor center layer 12 tooltip "&aОбщий счёт"
		text "&a0" at 16 -131 anchor center shadow layer 12 id hud_score
		text "&7Соло &7[x1]" at 142 -131 anchor center shadow layer 12 id hud_team

		# Большая HD карта 448x224 (текстура 896x448)
		image "td:textures/gui/map_valley.png" at 0 -2 size 448x224 texture 896x448 anchor center layer 0 id map
		bar "boss_bar" value 100 max 100 at 0 -106 size 220x8 anchor center color #E74C3C background #AA1A0A0A text "&c&lБОСС: %value%/%max%" shadow layer 14 hidden

		# Круги радиуса башни (малый / средний / снайпер)
		image "td:textures/gui/range_circle.png" at 0 0 size 196x196 texture 192x192 anchor center layer 2 id rng_s hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 260x260 texture 192x192 anchor center layer 2 id rng_m hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 390x390 texture 192x192 anchor center layer 2 id rng_l hidden

		# Свободная сетка постройки 16x8 (клетка 28x28)
		image "td:textures/gui/tile_build.png" at -210 -100 size 28x28 texture 28x28 anchor center layer 3 id c0_0 run TdCellClick(player, 0, 0) keep open
		image "td:textures/gui/tile_build.png" at -182 -100 size 28x28 texture 28x28 anchor center layer 3 id c1_0 run TdCellClick(player, 1, 0) keep open
		image "td:textures/gui/tile_build.png" at -154 -100 size 28x28 texture 28x28 anchor center layer 3 id c2_0 run TdCellClick(player, 2, 0) keep open
		image "td:textures/gui/tile_build.png" at -126 -100 size 28x28 texture 28x28 anchor center layer 3 id c3_0 run TdCellClick(player, 3, 0) keep open
		image "td:textures/gui/tile_build.png" at -98 -100 size 28x28 texture 28x28 anchor center layer 3 id c4_0 run TdCellClick(player, 4, 0) keep open
		image "td:textures/gui/tile_build.png" at -70 -100 size 28x28 texture 28x28 anchor center layer 3 id c5_0 run TdCellClick(player, 5, 0) keep open
		image "td:textures/gui/tile_build.png" at -42 -100 size 28x28 texture 28x28 anchor center layer 3 id c6_0 run TdCellClick(player, 6, 0) keep open
		image "td:textures/gui/tile_build.png" at -14 -100 size 28x28 texture 28x28 anchor center layer 3 id c7_0 run TdCellClick(player, 7, 0) keep open
		image "td:textures/gui/tile_build.png" at 14 -100 size 28x28 texture 28x28 anchor center layer 3 id c8_0 run TdCellClick(player, 8, 0) keep open
		image "td:textures/gui/tile_build.png" at 42 -100 size 28x28 texture 28x28 anchor center layer 3 id c9_0 run TdCellClick(player, 9, 0) keep open
		image "td:textures/gui/tile_build.png" at 70 -100 size 28x28 texture 28x28 anchor center layer 3 id c10_0 run TdCellClick(player, 10, 0) keep open
		image "td:textures/gui/tile_build.png" at 98 -100 size 28x28 texture 28x28 anchor center layer 3 id c11_0 run TdCellClick(player, 11, 0) keep open
		image "td:textures/gui/tile_build.png" at 126 -100 size 28x28 texture 28x28 anchor center layer 3 id c12_0 run TdCellClick(player, 12, 0) keep open
		image "td:textures/gui/tile_build.png" at 154 -100 size 28x28 texture 28x28 anchor center layer 3 id c13_0 run TdCellClick(player, 13, 0) keep open
		image "td:textures/gui/tile_build.png" at 182 -100 size 28x28 texture 28x28 anchor center layer 3 id c14_0 run TdCellClick(player, 14, 0) keep open
		image "td:textures/gui/tile_build.png" at 210 -100 size 28x28 texture 28x28 anchor center layer 3 id c15_0 run TdCellClick(player, 15, 0) keep open
		image "td:textures/gui/tile_build.png" at 182 -72 size 28x28 texture 28x28 anchor center layer 3 id c14_1 run TdCellClick(player, 14, 1) keep open
		image "td:textures/gui/tile_build.png" at 210 -72 size 28x28 texture 28x28 anchor center layer 3 id c15_1 run TdCellClick(player, 15, 1) keep open
		image "td:textures/gui/tile_build.png" at -210 -44 size 28x28 texture 28x28 anchor center layer 3 id c0_2 run TdCellClick(player, 0, 2) keep open
		image "td:textures/gui/tile_build.png" at -182 -44 size 28x28 texture 28x28 anchor center layer 3 id c1_2 run TdCellClick(player, 1, 2) keep open
		image "td:textures/gui/tile_build.png" at -154 -44 size 28x28 texture 28x28 anchor center layer 3 id c2_2 run TdCellClick(player, 2, 2) keep open
		image "td:textures/gui/tile_build.png" at -126 -44 size 28x28 texture 28x28 anchor center layer 3 id c3_2 run TdCellClick(player, 3, 2) keep open
		image "td:textures/gui/tile_build.png" at -98 -44 size 28x28 texture 28x28 anchor center layer 3 id c4_2 run TdCellClick(player, 4, 2) keep open
		image "td:textures/gui/tile_build.png" at -70 -44 size 28x28 texture 28x28 anchor center layer 3 id c5_2 run TdCellClick(player, 5, 2) keep open
		image "td:textures/gui/tile_build.png" at -42 -44 size 28x28 texture 28x28 anchor center layer 3 id c6_2 run TdCellClick(player, 6, 2) keep open
		image "td:textures/gui/tile_build.png" at -14 -44 size 28x28 texture 28x28 anchor center layer 3 id c7_2 run TdCellClick(player, 7, 2) keep open
		image "td:textures/gui/tile_build.png" at 14 -44 size 28x28 texture 28x28 anchor center layer 3 id c8_2 run TdCellClick(player, 8, 2) keep open
		image "td:textures/gui/tile_build.png" at 42 -44 size 28x28 texture 28x28 anchor center layer 3 id c9_2 run TdCellClick(player, 9, 2) keep open
		image "td:textures/gui/tile_build.png" at 70 -44 size 28x28 texture 28x28 anchor center layer 3 id c10_2 run TdCellClick(player, 10, 2) keep open
		image "td:textures/gui/tile_build.png" at 98 -44 size 28x28 texture 28x28 anchor center layer 3 id c11_2 run TdCellClick(player, 11, 2) keep open
		image "td:textures/gui/tile_build.png" at 126 -44 size 28x28 texture 28x28 anchor center layer 3 id c12_2 run TdCellClick(player, 12, 2) keep open
		image "td:textures/gui/tile_build.png" at 182 -44 size 28x28 texture 28x28 anchor center layer 3 id c14_2 run TdCellClick(player, 14, 2) keep open
		image "td:textures/gui/tile_build.png" at 210 -44 size 28x28 texture 28x28 anchor center layer 3 id c15_2 run TdCellClick(player, 15, 2) keep open
		image "td:textures/gui/tile_build.png" at -210 -16 size 28x28 texture 28x28 anchor center layer 3 id c0_3 run TdCellClick(player, 0, 3) keep open
		image "td:textures/gui/tile_build.png" at -182 -16 size 28x28 texture 28x28 anchor center layer 3 id c1_3 run TdCellClick(player, 1, 3) keep open
		image "td:textures/gui/tile_build.png" at 182 -16 size 28x28 texture 28x28 anchor center layer 3 id c14_3 run TdCellClick(player, 14, 3) keep open
		image "td:textures/gui/tile_build.png" at 210 -16 size 28x28 texture 28x28 anchor center layer 3 id c15_3 run TdCellClick(player, 15, 3) keep open
		image "td:textures/gui/tile_build.png" at -210 12 size 28x28 texture 28x28 anchor center layer 3 id c0_4 run TdCellClick(player, 0, 4) keep open
		image "td:textures/gui/tile_build.png" at -182 12 size 28x28 texture 28x28 anchor center layer 3 id c1_4 run TdCellClick(player, 1, 4) keep open
		image "td:textures/gui/tile_build.png" at -126 12 size 28x28 texture 28x28 anchor center layer 3 id c3_4 run TdCellClick(player, 3, 4) keep open
		image "td:textures/gui/tile_build.png" at -98 12 size 28x28 texture 28x28 anchor center layer 3 id c4_4 run TdCellClick(player, 4, 4) keep open
		image "td:textures/gui/tile_build.png" at -70 12 size 28x28 texture 28x28 anchor center layer 3 id c5_4 run TdCellClick(player, 5, 4) keep open
		image "td:textures/gui/tile_build.png" at -42 12 size 28x28 texture 28x28 anchor center layer 3 id c6_4 run TdCellClick(player, 6, 4) keep open
		image "td:textures/gui/tile_build.png" at -14 12 size 28x28 texture 28x28 anchor center layer 3 id c7_4 run TdCellClick(player, 7, 4) keep open
		image "td:textures/gui/tile_build.png" at 14 12 size 28x28 texture 28x28 anchor center layer 3 id c8_4 run TdCellClick(player, 8, 4) keep open
		image "td:textures/gui/tile_build.png" at 42 12 size 28x28 texture 28x28 anchor center layer 3 id c9_4 run TdCellClick(player, 9, 4) keep open
		image "td:textures/gui/tile_build.png" at 70 12 size 28x28 texture 28x28 anchor center layer 3 id c10_4 run TdCellClick(player, 10, 4) keep open
		image "td:textures/gui/tile_build.png" at 98 12 size 28x28 texture 28x28 anchor center layer 3 id c11_4 run TdCellClick(player, 11, 4) keep open
		image "td:textures/gui/tile_build.png" at 126 12 size 28x28 texture 28x28 anchor center layer 3 id c12_4 run TdCellClick(player, 12, 4) keep open
		image "td:textures/gui/tile_build.png" at 154 12 size 28x28 texture 28x28 anchor center layer 3 id c13_4 run TdCellClick(player, 13, 4) keep open
		image "td:textures/gui/tile_build.png" at 182 12 size 28x28 texture 28x28 anchor center layer 3 id c14_4 run TdCellClick(player, 14, 4) keep open
		image "td:textures/gui/tile_build.png" at 210 12 size 28x28 texture 28x28 anchor center layer 3 id c15_4 run TdCellClick(player, 15, 4) keep open
		image "td:textures/gui/tile_build.png" at -210 40 size 28x28 texture 28x28 anchor center layer 3 id c0_5 run TdCellClick(player, 0, 5) keep open
		image "td:textures/gui/tile_build.png" at -182 40 size 28x28 texture 28x28 anchor center layer 3 id c1_5 run TdCellClick(player, 1, 5) keep open
		image "td:textures/gui/tile_build.png" at -126 40 size 28x28 texture 28x28 anchor center layer 3 id c3_5 run TdCellClick(player, 3, 5) keep open
		image "td:textures/gui/tile_build.png" at -98 40 size 28x28 texture 28x28 anchor center layer 3 id c4_5 run TdCellClick(player, 4, 5) keep open
		image "td:textures/gui/tile_build.png" at -70 40 size 28x28 texture 28x28 anchor center layer 3 id c5_5 run TdCellClick(player, 5, 5) keep open
		image "td:textures/gui/tile_build.png" at -42 40 size 28x28 texture 28x28 anchor center layer 3 id c6_5 run TdCellClick(player, 6, 5) keep open
		image "td:textures/gui/tile_build.png" at -14 40 size 28x28 texture 28x28 anchor center layer 3 id c7_5 run TdCellClick(player, 7, 5) keep open
		image "td:textures/gui/tile_build.png" at 14 40 size 28x28 texture 28x28 anchor center layer 3 id c8_5 run TdCellClick(player, 8, 5) keep open
		image "td:textures/gui/tile_build.png" at 42 40 size 28x28 texture 28x28 anchor center layer 3 id c9_5 run TdCellClick(player, 9, 5) keep open
		image "td:textures/gui/tile_build.png" at 70 40 size 28x28 texture 28x28 anchor center layer 3 id c10_5 run TdCellClick(player, 10, 5) keep open
		image "td:textures/gui/tile_build.png" at 98 40 size 28x28 texture 28x28 anchor center layer 3 id c11_5 run TdCellClick(player, 11, 5) keep open
		image "td:textures/gui/tile_build.png" at 126 40 size 28x28 texture 28x28 anchor center layer 3 id c12_5 run TdCellClick(player, 12, 5) keep open
		image "td:textures/gui/tile_build.png" at 154 40 size 28x28 texture 28x28 anchor center layer 3 id c13_5 run TdCellClick(player, 13, 5) keep open
		image "td:textures/gui/tile_build.png" at 182 40 size 28x28 texture 28x28 anchor center layer 3 id c14_5 run TdCellClick(player, 14, 5) keep open
		image "td:textures/gui/tile_build.png" at 210 40 size 28x28 texture 28x28 anchor center layer 3 id c15_5 run TdCellClick(player, 15, 5) keep open
		image "td:textures/gui/tile_build.png" at -210 68 size 28x28 texture 28x28 anchor center layer 3 id c0_6 run TdCellClick(player, 0, 6) keep open
		image "td:textures/gui/tile_build.png" at -182 68 size 28x28 texture 28x28 anchor center layer 3 id c1_6 run TdCellClick(player, 1, 6) keep open
		image "td:textures/gui/tile_build.png" at -210 96 size 28x28 texture 28x28 anchor center layer 3 id c0_7 run TdCellClick(player, 0, 7) keep open
		image "td:textures/gui/tile_build.png" at -182 96 size 28x28 texture 28x28 anchor center layer 3 id c1_7 run TdCellClick(player, 1, 7) keep open
		image "td:textures/gui/tile_build.png" at -154 96 size 28x28 texture 28x28 anchor center layer 3 id c2_7 run TdCellClick(player, 2, 7) keep open
		image "td:textures/gui/tile_build.png" at -126 96 size 28x28 texture 28x28 anchor center layer 3 id c3_7 run TdCellClick(player, 3, 7) keep open
		image "td:textures/gui/tile_build.png" at -98 96 size 28x28 texture 28x28 anchor center layer 3 id c4_7 run TdCellClick(player, 4, 7) keep open
		image "td:textures/gui/tile_build.png" at -70 96 size 28x28 texture 28x28 anchor center layer 3 id c5_7 run TdCellClick(player, 5, 7) keep open
		image "td:textures/gui/tile_build.png" at -42 96 size 28x28 texture 28x28 anchor center layer 3 id c6_7 run TdCellClick(player, 6, 7) keep open
		image "td:textures/gui/tile_build.png" at -14 96 size 28x28 texture 28x28 anchor center layer 3 id c7_7 run TdCellClick(player, 7, 7) keep open
		image "td:textures/gui/tile_build.png" at 14 96 size 28x28 texture 28x28 anchor center layer 3 id c8_7 run TdCellClick(player, 8, 7) keep open
		image "td:textures/gui/tile_build.png" at 42 96 size 28x28 texture 28x28 anchor center layer 3 id c9_7 run TdCellClick(player, 9, 7) keep open
		image "td:textures/gui/tile_build.png" at 70 96 size 28x28 texture 28x28 anchor center layer 3 id c10_7 run TdCellClick(player, 10, 7) keep open
		image "td:textures/gui/tile_build.png" at 98 96 size 28x28 texture 28x28 anchor center layer 3 id c11_7 run TdCellClick(player, 11, 7) keep open
		image "td:textures/gui/tile_build.png" at 126 96 size 28x28 texture 28x28 anchor center layer 3 id c12_7 run TdCellClick(player, 12, 7) keep open
		image "td:textures/gui/tile_build.png" at 154 96 size 28x28 texture 28x28 anchor center layer 3 id c13_7 run TdCellClick(player, 13, 7) keep open
		image "td:textures/gui/tile_build.png" at 182 96 size 28x28 texture 28x28 anchor center layer 3 id c14_7 run TdCellClick(player, 14, 7) keep open
		image "td:textures/gui/tile_build.png" at 210 96 size 28x28 texture 28x28 anchor center layer 3 id c15_7 run TdCellClick(player, 15, 7) keep open

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

		# 11 слотов обычных врагов + 1 слот Босса (HD 40x40 / 56x56)
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e1 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e2 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e3 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e4 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e5 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e6 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e7 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e9 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e10 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e11 hidden
		image "td:textures/gui/mob_boss.png" at -210 -72 size 32x32 texture 56x56 anchor center layer 8 id e12 hidden

		# 6 летающих HD прожектайлов + вспышка взрыва
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr1 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr2 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr3 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr4 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr5 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr6 hidden
		image "td:textures/gui/fx_boom.png" at 0 0 size 20x20 texture 32x32 anchor center layer 10 id fx hidden

		# Информационная строка (между картой и нижней панелью — ничего не перекрывает)
		bar "bg_stat" value 0 max 1 at 0 119 size 456x14 anchor center background #DD0F172A layer 11
		text "&eВыбран: &fЛучник (50з) &7— клик по траве строит, по башне улучшает/показывает радиус!" at 0 119 anchor center shadow layer 12 id status_txt
		text "&c&lБАЗА ПАЛА!" at 0 -16 anchor center shadow layer 20 id end_title hidden
		text "&fНажми &eR &f(рестарт) или &eM &f(меню)" at 0 2 anchor center shadow layer 20 id end_sub hidden

		# Нижняя панель инструментов (8 HD башен + 3 кнопки + Авто-волна)
		bar "bg_bar" value 0 max 1 at 0 148 size 456x40 anchor center background #EE0B1220 layer 11
		image "td:textures/gui/tower_archer.png" at -208 140 size 22x22 texture 48x48 anchor center layer 12 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e[1] Лучник &7(50з)\n&7Урон: 15 | Очень быстрый | Дальн: 98\n&6✦5★ Шквал: &fстреляет сразу по 2 целям!"
		text "&e[1]50" at -208 158 anchor center shadow layer 12 id lbl1
		image "td:textures/gui/tower_cannon.png" at -172 140 size 22x22 texture 48x48 anchor center layer 12 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6[2] Пушка &7(95з)\n&7Урон: 36 + &cСплеш | Дальн: 92\n&6✦5★ Напалм: &fмега-сплеш + поджог врагов!"
		text "&7[2]95" at -172 158 anchor center shadow layer 12 id lbl2
		image "td:textures/gui/tower_frost.png" at -136 140 size 22x22 texture 48x48 anchor center layer 12 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b[3] Лёд &7(85з)\n&7Урон: 11 + &bЗамедление 45% | Дальн: 96\n&6✦5★ Мерзлота: &f65% замедл. + 25% заморозка!"
		text "&7[3]85" at -136 158 anchor center shadow layer 12 id lbl3
		image "td:textures/gui/tower_mage.png" at -100 140 size 22x22 texture 48x48 anchor center layer 12 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d[4] Маг &7(130з)\n&7Урон: 32 | &dДальность 132\n&6✦5★ Убийца Боссов: &fx2 Боссам + 4% макс.HP!"
		text "&7[4]130" at -100 158 anchor center shadow layer 12 id lbl4
		image "td:textures/gui/tower_tesla.png" at -64 140 size 22x22 texture 48x48 anchor center layer 12 id ico5 run TdSelectTool(player, 5) keep open tooltip "&b[5] Тесла &7(115з)\n&7Урон: 20 | &bЦепная молния (3 цели) | Дальн: 108\n&6✦5★ Шторм: &f5 целей + 90% урон + стан!"
		text "&7[5]115" at -64 158 anchor center shadow layer 12 id lbl5
		image "td:textures/gui/tower_sniper.png" at -28 140 size 22x22 texture 48x48 anchor center layer 12 id ico6 run TdSelectTool(player, 6) keep open tooltip "&c[6] Снайпер &7(150з)\n&7Урон: 56 | &cКрит 25% | Дальн: 215\n&6✦5★ Казнь: &fкрит 45% x2.5 + казнь <20% HP!"
		text "&7[6]150" at -28 158 anchor center shadow layer 12 id lbl6
		image "td:textures/gui/tower_poison.png" at 8 140 size 22x22 texture 48x48 anchor center layer 12 id ico7 run TdSelectTool(player, 7) keep open tooltip "&a[7] Алхимик &7(105з)\n&7Урон: 12 + &aЯд (урон в сек.) | Дальн: 100\n&6✦5★ Чума: &fяд заражает всех врагов вокруг!"
		text "&7[7]105" at 8 158 anchor center shadow layer 12 id lbl7
		image "td:textures/gui/tower_farm.png" at 44 140 size 22x22 texture 48x48 anchor center layer 12 id ico8 run TdSelectTool(player, 8) keep open tooltip "&6[8] Банк &7(140з)\n&7Доход: &6+28/+50/+80/+120з &7каждую волну!\n&6✦5★ Дворец: &f+180з за волну и +1 HP базе!"
		text "&7[8]140" at 44 158 anchor center shadow layer 12 id lbl8
		button "&aСтарт/x2" at 118 139 size 68x16 anchor center run TdWaveOrSpeed(player) keep open tooltip "&aЗапустить волну &7(Space)\n&bВо время волны: скорость x1 / x2"
		toggle "Авто" id autowave at 192 139 size 58x16 anchor center value false color #22C55E tooltip "&aАвто-волна\n&7Авто-старт следующей волны через 1.5с"
		button "&cПродать" at 118 158 size 68x16 anchor center run TdToggleSell(player) keep open tooltip "&cПродать свою башню &7(X)\n&7Возврат 70% стоимости"
		button "Меню" at 192 158 size 58x16 anchor center run TdOpenMenu(player) keep open tooltip "&eВыбор карты, кооп и сложность &7(M)"

function TdOpenMap2(p: player):
	open screen "Tower Defense" to p:
		# Верхняя HUD-панель (без PNG — не бывает битых текстур!)
		bar "bg_hud" value 0 max 1 at 0 -131 size 456x22 anchor center background #DD0F172A layer 11
		image "td:textures/gui/icon_wave.png" at -214 -131 size 12x12 texture 32x32 anchor center layer 12 tooltip "&eТекущая волна"
		text "&f0/20" at -188 -131 anchor center shadow layer 12 id hud_wave
		image "td:textures/gui/icon_coin.png" at -142 -131 size 12x12 texture 32x32 anchor center layer 12 tooltip "&6Ваше личное золото"
		text "&6120" at -116 -131 anchor center shadow layer 12 id hud_gold
		image "td:textures/gui/icon_heart.png" at -68 -131 size 12x12 texture 32x32 anchor center layer 12 tooltip "&cЗдоровье базы"
		text "&c10" at -48 -131 anchor center shadow layer 12 id hud_hp
		image "td:textures/gui/icon_trophy.png" at -12 -131 size 12x12 texture 32x32 anchor center layer 12 tooltip "&aОбщий счёт"
		text "&a0" at 16 -131 anchor center shadow layer 12 id hud_score
		text "&7Соло &7[x1]" at 142 -131 anchor center shadow layer 12 id hud_team

		# Большая HD карта 448x224 (текстура 896x448)
		image "td:textures/gui/map_volcano.png" at 0 -2 size 448x224 texture 896x448 anchor center layer 0 id map
		bar "boss_bar" value 100 max 100 at 0 -106 size 220x8 anchor center color #E74C3C background #AA1A0A0A text "&c&lБОСС: %value%/%max%" shadow layer 14 hidden

		# Круги радиуса башни (малый / средний / снайпер)
		image "td:textures/gui/range_circle.png" at 0 0 size 196x196 texture 192x192 anchor center layer 2 id rng_s hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 260x260 texture 192x192 anchor center layer 2 id rng_m hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 390x390 texture 192x192 anchor center layer 2 id rng_l hidden

		# Свободная сетка постройки 16x8 (клетка 28x28)
		image "td:textures/gui/tile_build.png" at -70 -100 size 28x28 texture 28x28 anchor center layer 3 id c5_0 run TdCellClick(player, 5, 0) keep open
		image "td:textures/gui/tile_build.png" at -42 -100 size 28x28 texture 28x28 anchor center layer 3 id c6_0 run TdCellClick(player, 6, 0) keep open
		image "td:textures/gui/tile_build.png" at -14 -100 size 28x28 texture 28x28 anchor center layer 3 id c7_0 run TdCellClick(player, 7, 0) keep open
		image "td:textures/gui/tile_build.png" at 14 -100 size 28x28 texture 28x28 anchor center layer 3 id c8_0 run TdCellClick(player, 8, 0) keep open
		image "td:textures/gui/tile_build.png" at 42 -100 size 28x28 texture 28x28 anchor center layer 3 id c9_0 run TdCellClick(player, 9, 0) keep open
		image "td:textures/gui/tile_build.png" at 70 -100 size 28x28 texture 28x28 anchor center layer 3 id c10_0 run TdCellClick(player, 10, 0) keep open
		image "td:textures/gui/tile_build.png" at 98 -100 size 28x28 texture 28x28 anchor center layer 3 id c11_0 run TdCellClick(player, 11, 0) keep open
		image "td:textures/gui/tile_build.png" at 126 -100 size 28x28 texture 28x28 anchor center layer 3 id c12_0 run TdCellClick(player, 12, 0) keep open
		image "td:textures/gui/tile_build.png" at 154 -100 size 28x28 texture 28x28 anchor center layer 3 id c13_0 run TdCellClick(player, 13, 0) keep open
		image "td:textures/gui/tile_build.png" at 182 -100 size 28x28 texture 28x28 anchor center layer 3 id c14_0 run TdCellClick(player, 14, 0) keep open
		image "td:textures/gui/tile_build.png" at 210 -100 size 28x28 texture 28x28 anchor center layer 3 id c15_0 run TdCellClick(player, 15, 0) keep open
		image "td:textures/gui/tile_build.png" at -210 -72 size 28x28 texture 28x28 anchor center layer 3 id c0_1 run TdCellClick(player, 0, 1) keep open
		image "td:textures/gui/tile_build.png" at -182 -72 size 28x28 texture 28x28 anchor center layer 3 id c1_1 run TdCellClick(player, 1, 1) keep open
		image "td:textures/gui/tile_build.png" at -154 -72 size 28x28 texture 28x28 anchor center layer 3 id c2_1 run TdCellClick(player, 2, 1) keep open
		image "td:textures/gui/tile_build.png" at -126 -72 size 28x28 texture 28x28 anchor center layer 3 id c3_1 run TdCellClick(player, 3, 1) keep open
		image "td:textures/gui/tile_build.png" at -70 -72 size 28x28 texture 28x28 anchor center layer 3 id c5_1 run TdCellClick(player, 5, 1) keep open
		image "td:textures/gui/tile_build.png" at -42 -72 size 28x28 texture 28x28 anchor center layer 3 id c6_1 run TdCellClick(player, 6, 1) keep open
		image "td:textures/gui/tile_build.png" at -14 -72 size 28x28 texture 28x28 anchor center layer 3 id c7_1 run TdCellClick(player, 7, 1) keep open
		image "td:textures/gui/tile_build.png" at 14 -72 size 28x28 texture 28x28 anchor center layer 3 id c8_1 run TdCellClick(player, 8, 1) keep open
		image "td:textures/gui/tile_build.png" at 182 -72 size 28x28 texture 28x28 anchor center layer 3 id c14_1 run TdCellClick(player, 14, 1) keep open
		image "td:textures/gui/tile_build.png" at 210 -72 size 28x28 texture 28x28 anchor center layer 3 id c15_1 run TdCellClick(player, 15, 1) keep open
		image "td:textures/gui/tile_build.png" at -210 -44 size 28x28 texture 28x28 anchor center layer 3 id c0_2 run TdCellClick(player, 0, 2) keep open
		image "td:textures/gui/tile_build.png" at -182 -44 size 28x28 texture 28x28 anchor center layer 3 id c1_2 run TdCellClick(player, 1, 2) keep open
		image "td:textures/gui/tile_build.png" at -154 -44 size 28x28 texture 28x28 anchor center layer 3 id c2_2 run TdCellClick(player, 2, 2) keep open
		image "td:textures/gui/tile_build.png" at -126 -44 size 28x28 texture 28x28 anchor center layer 3 id c3_2 run TdCellClick(player, 3, 2) keep open
		image "td:textures/gui/tile_build.png" at -70 -44 size 28x28 texture 28x28 anchor center layer 3 id c5_2 run TdCellClick(player, 5, 2) keep open
		image "td:textures/gui/tile_build.png" at -42 -44 size 28x28 texture 28x28 anchor center layer 3 id c6_2 run TdCellClick(player, 6, 2) keep open
		image "td:textures/gui/tile_build.png" at -14 -44 size 28x28 texture 28x28 anchor center layer 3 id c7_2 run TdCellClick(player, 7, 2) keep open
		image "td:textures/gui/tile_build.png" at 14 -44 size 28x28 texture 28x28 anchor center layer 3 id c8_2 run TdCellClick(player, 8, 2) keep open
		image "td:textures/gui/tile_build.png" at 70 -44 size 28x28 texture 28x28 anchor center layer 3 id c10_2 run TdCellClick(player, 10, 2) keep open
		image "td:textures/gui/tile_build.png" at 98 -44 size 28x28 texture 28x28 anchor center layer 3 id c11_2 run TdCellClick(player, 11, 2) keep open
		image "td:textures/gui/tile_build.png" at 126 -44 size 28x28 texture 28x28 anchor center layer 3 id c12_2 run TdCellClick(player, 12, 2) keep open
		image "td:textures/gui/tile_build.png" at 182 -44 size 28x28 texture 28x28 anchor center layer 3 id c14_2 run TdCellClick(player, 14, 2) keep open
		image "td:textures/gui/tile_build.png" at 210 -44 size 28x28 texture 28x28 anchor center layer 3 id c15_2 run TdCellClick(player, 15, 2) keep open
		image "td:textures/gui/tile_build.png" at -210 -16 size 28x28 texture 28x28 anchor center layer 3 id c0_3 run TdCellClick(player, 0, 3) keep open
		image "td:textures/gui/tile_build.png" at -182 -16 size 28x28 texture 28x28 anchor center layer 3 id c1_3 run TdCellClick(player, 1, 3) keep open
		image "td:textures/gui/tile_build.png" at -154 -16 size 28x28 texture 28x28 anchor center layer 3 id c2_3 run TdCellClick(player, 2, 3) keep open
		image "td:textures/gui/tile_build.png" at -126 -16 size 28x28 texture 28x28 anchor center layer 3 id c3_3 run TdCellClick(player, 3, 3) keep open
		image "td:textures/gui/tile_build.png" at -70 -16 size 28x28 texture 28x28 anchor center layer 3 id c5_3 run TdCellClick(player, 5, 3) keep open
		image "td:textures/gui/tile_build.png" at -42 -16 size 28x28 texture 28x28 anchor center layer 3 id c6_3 run TdCellClick(player, 6, 3) keep open
		image "td:textures/gui/tile_build.png" at -14 -16 size 28x28 texture 28x28 anchor center layer 3 id c7_3 run TdCellClick(player, 7, 3) keep open
		image "td:textures/gui/tile_build.png" at 14 -16 size 28x28 texture 28x28 anchor center layer 3 id c8_3 run TdCellClick(player, 8, 3) keep open
		image "td:textures/gui/tile_build.png" at 70 -16 size 28x28 texture 28x28 anchor center layer 3 id c10_3 run TdCellClick(player, 10, 3) keep open
		image "td:textures/gui/tile_build.png" at 98 -16 size 28x28 texture 28x28 anchor center layer 3 id c11_3 run TdCellClick(player, 11, 3) keep open
		image "td:textures/gui/tile_build.png" at 126 -16 size 28x28 texture 28x28 anchor center layer 3 id c12_3 run TdCellClick(player, 12, 3) keep open
		image "td:textures/gui/tile_build.png" at 182 -16 size 28x28 texture 28x28 anchor center layer 3 id c14_3 run TdCellClick(player, 14, 3) keep open
		image "td:textures/gui/tile_build.png" at 210 -16 size 28x28 texture 28x28 anchor center layer 3 id c15_3 run TdCellClick(player, 15, 3) keep open
		image "td:textures/gui/tile_build.png" at -210 12 size 28x28 texture 28x28 anchor center layer 3 id c0_4 run TdCellClick(player, 0, 4) keep open
		image "td:textures/gui/tile_build.png" at -182 12 size 28x28 texture 28x28 anchor center layer 3 id c1_4 run TdCellClick(player, 1, 4) keep open
		image "td:textures/gui/tile_build.png" at -154 12 size 28x28 texture 28x28 anchor center layer 3 id c2_4 run TdCellClick(player, 2, 4) keep open
		image "td:textures/gui/tile_build.png" at -126 12 size 28x28 texture 28x28 anchor center layer 3 id c3_4 run TdCellClick(player, 3, 4) keep open
		image "td:textures/gui/tile_build.png" at -70 12 size 28x28 texture 28x28 anchor center layer 3 id c5_4 run TdCellClick(player, 5, 4) keep open
		image "td:textures/gui/tile_build.png" at -42 12 size 28x28 texture 28x28 anchor center layer 3 id c6_4 run TdCellClick(player, 6, 4) keep open
		image "td:textures/gui/tile_build.png" at -14 12 size 28x28 texture 28x28 anchor center layer 3 id c7_4 run TdCellClick(player, 7, 4) keep open
		image "td:textures/gui/tile_build.png" at 14 12 size 28x28 texture 28x28 anchor center layer 3 id c8_4 run TdCellClick(player, 8, 4) keep open
		image "td:textures/gui/tile_build.png" at 70 12 size 28x28 texture 28x28 anchor center layer 3 id c10_4 run TdCellClick(player, 10, 4) keep open
		image "td:textures/gui/tile_build.png" at 98 12 size 28x28 texture 28x28 anchor center layer 3 id c11_4 run TdCellClick(player, 11, 4) keep open
		image "td:textures/gui/tile_build.png" at 126 12 size 28x28 texture 28x28 anchor center layer 3 id c12_4 run TdCellClick(player, 12, 4) keep open
		image "td:textures/gui/tile_build.png" at 182 12 size 28x28 texture 28x28 anchor center layer 3 id c14_4 run TdCellClick(player, 14, 4) keep open
		image "td:textures/gui/tile_build.png" at 210 12 size 28x28 texture 28x28 anchor center layer 3 id c15_4 run TdCellClick(player, 15, 4) keep open
		image "td:textures/gui/tile_build.png" at -210 40 size 28x28 texture 28x28 anchor center layer 3 id c0_5 run TdCellClick(player, 0, 5) keep open
		image "td:textures/gui/tile_build.png" at -182 40 size 28x28 texture 28x28 anchor center layer 3 id c1_5 run TdCellClick(player, 1, 5) keep open
		image "td:textures/gui/tile_build.png" at -154 40 size 28x28 texture 28x28 anchor center layer 3 id c2_5 run TdCellClick(player, 2, 5) keep open
		image "td:textures/gui/tile_build.png" at -126 40 size 28x28 texture 28x28 anchor center layer 3 id c3_5 run TdCellClick(player, 3, 5) keep open
		image "td:textures/gui/tile_build.png" at -70 40 size 28x28 texture 28x28 anchor center layer 3 id c5_5 run TdCellClick(player, 5, 5) keep open
		image "td:textures/gui/tile_build.png" at -42 40 size 28x28 texture 28x28 anchor center layer 3 id c6_5 run TdCellClick(player, 6, 5) keep open
		image "td:textures/gui/tile_build.png" at -14 40 size 28x28 texture 28x28 anchor center layer 3 id c7_5 run TdCellClick(player, 7, 5) keep open
		image "td:textures/gui/tile_build.png" at 14 40 size 28x28 texture 28x28 anchor center layer 3 id c8_5 run TdCellClick(player, 8, 5) keep open
		image "td:textures/gui/tile_build.png" at 70 40 size 28x28 texture 28x28 anchor center layer 3 id c10_5 run TdCellClick(player, 10, 5) keep open
		image "td:textures/gui/tile_build.png" at 98 40 size 28x28 texture 28x28 anchor center layer 3 id c11_5 run TdCellClick(player, 11, 5) keep open
		image "td:textures/gui/tile_build.png" at 126 40 size 28x28 texture 28x28 anchor center layer 3 id c12_5 run TdCellClick(player, 12, 5) keep open
		image "td:textures/gui/tile_build.png" at -210 68 size 28x28 texture 28x28 anchor center layer 3 id c0_6 run TdCellClick(player, 0, 6) keep open
		image "td:textures/gui/tile_build.png" at -182 68 size 28x28 texture 28x28 anchor center layer 3 id c1_6 run TdCellClick(player, 1, 6) keep open
		image "td:textures/gui/tile_build.png" at -154 68 size 28x28 texture 28x28 anchor center layer 3 id c2_6 run TdCellClick(player, 2, 6) keep open
		image "td:textures/gui/tile_build.png" at -126 68 size 28x28 texture 28x28 anchor center layer 3 id c3_6 run TdCellClick(player, 3, 6) keep open
		image "td:textures/gui/tile_build.png" at 70 68 size 28x28 texture 28x28 anchor center layer 3 id c10_6 run TdCellClick(player, 10, 6) keep open
		image "td:textures/gui/tile_build.png" at 98 68 size 28x28 texture 28x28 anchor center layer 3 id c11_6 run TdCellClick(player, 11, 6) keep open
		image "td:textures/gui/tile_build.png" at 126 68 size 28x28 texture 28x28 anchor center layer 3 id c12_6 run TdCellClick(player, 12, 6) keep open
		image "td:textures/gui/tile_build.png" at 154 68 size 28x28 texture 28x28 anchor center layer 3 id c13_6 run TdCellClick(player, 13, 6) keep open
		image "td:textures/gui/tile_build.png" at 182 68 size 28x28 texture 28x28 anchor center layer 3 id c14_6 run TdCellClick(player, 14, 6) keep open
		image "td:textures/gui/tile_build.png" at 210 68 size 28x28 texture 28x28 anchor center layer 3 id c15_6 run TdCellClick(player, 15, 6) keep open
		image "td:textures/gui/tile_build.png" at -210 96 size 28x28 texture 28x28 anchor center layer 3 id c0_7 run TdCellClick(player, 0, 7) keep open
		image "td:textures/gui/tile_build.png" at -182 96 size 28x28 texture 28x28 anchor center layer 3 id c1_7 run TdCellClick(player, 1, 7) keep open
		image "td:textures/gui/tile_build.png" at -154 96 size 28x28 texture 28x28 anchor center layer 3 id c2_7 run TdCellClick(player, 2, 7) keep open
		image "td:textures/gui/tile_build.png" at -126 96 size 28x28 texture 28x28 anchor center layer 3 id c3_7 run TdCellClick(player, 3, 7) keep open
		image "td:textures/gui/tile_build.png" at -98 96 size 28x28 texture 28x28 anchor center layer 3 id c4_7 run TdCellClick(player, 4, 7) keep open
		image "td:textures/gui/tile_build.png" at -70 96 size 28x28 texture 28x28 anchor center layer 3 id c5_7 run TdCellClick(player, 5, 7) keep open
		image "td:textures/gui/tile_build.png" at -42 96 size 28x28 texture 28x28 anchor center layer 3 id c6_7 run TdCellClick(player, 6, 7) keep open
		image "td:textures/gui/tile_build.png" at -14 96 size 28x28 texture 28x28 anchor center layer 3 id c7_7 run TdCellClick(player, 7, 7) keep open
		image "td:textures/gui/tile_build.png" at 14 96 size 28x28 texture 28x28 anchor center layer 3 id c8_7 run TdCellClick(player, 8, 7) keep open
		image "td:textures/gui/tile_build.png" at 42 96 size 28x28 texture 28x28 anchor center layer 3 id c9_7 run TdCellClick(player, 9, 7) keep open
		image "td:textures/gui/tile_build.png" at 70 96 size 28x28 texture 28x28 anchor center layer 3 id c10_7 run TdCellClick(player, 10, 7) keep open
		image "td:textures/gui/tile_build.png" at 98 96 size 28x28 texture 28x28 anchor center layer 3 id c11_7 run TdCellClick(player, 11, 7) keep open
		image "td:textures/gui/tile_build.png" at 126 96 size 28x28 texture 28x28 anchor center layer 3 id c12_7 run TdCellClick(player, 12, 7) keep open
		image "td:textures/gui/tile_build.png" at 154 96 size 28x28 texture 28x28 anchor center layer 3 id c13_7 run TdCellClick(player, 13, 7) keep open
		image "td:textures/gui/tile_build.png" at 182 96 size 28x28 texture 28x28 anchor center layer 3 id c14_7 run TdCellClick(player, 14, 7) keep open
		image "td:textures/gui/tile_build.png" at 210 96 size 28x28 texture 28x28 anchor center layer 3 id c15_7 run TdCellClick(player, 15, 7) keep open

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

		# 11 слотов обычных врагов + 1 слот Босса (HD 40x40 / 56x56)
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e1 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e2 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e3 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e4 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e5 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e6 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e7 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e9 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e10 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e11 hidden
		image "td:textures/gui/mob_boss.png" at -210 -72 size 32x32 texture 56x56 anchor center layer 8 id e12 hidden

		# 6 летающих HD прожектайлов + вспышка взрыва
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr1 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr2 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr3 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr4 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr5 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr6 hidden
		image "td:textures/gui/fx_boom.png" at 0 0 size 20x20 texture 32x32 anchor center layer 10 id fx hidden

		# Информационная строка (между картой и нижней панелью — ничего не перекрывает)
		bar "bg_stat" value 0 max 1 at 0 119 size 456x14 anchor center background #DD0F172A layer 11
		text "&eВыбран: &fЛучник (50з) &7— клик по траве строит, по башне улучшает/показывает радиус!" at 0 119 anchor center shadow layer 12 id status_txt
		text "&c&lБАЗА ПАЛА!" at 0 -16 anchor center shadow layer 20 id end_title hidden
		text "&fНажми &eR &f(рестарт) или &eM &f(меню)" at 0 2 anchor center shadow layer 20 id end_sub hidden

		# Нижняя панель инструментов (8 HD башен + 3 кнопки + Авто-волна)
		bar "bg_bar" value 0 max 1 at 0 148 size 456x40 anchor center background #EE0B1220 layer 11
		image "td:textures/gui/tower_archer.png" at -208 140 size 22x22 texture 48x48 anchor center layer 12 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e[1] Лучник &7(50з)\n&7Урон: 15 | Очень быстрый | Дальн: 98\n&6✦5★ Шквал: &fстреляет сразу по 2 целям!"
		text "&e[1]50" at -208 158 anchor center shadow layer 12 id lbl1
		image "td:textures/gui/tower_cannon.png" at -172 140 size 22x22 texture 48x48 anchor center layer 12 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6[2] Пушка &7(95з)\n&7Урон: 36 + &cСплеш | Дальн: 92\n&6✦5★ Напалм: &fмега-сплеш + поджог врагов!"
		text "&7[2]95" at -172 158 anchor center shadow layer 12 id lbl2
		image "td:textures/gui/tower_frost.png" at -136 140 size 22x22 texture 48x48 anchor center layer 12 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b[3] Лёд &7(85з)\n&7Урон: 11 + &bЗамедление 45% | Дальн: 96\n&6✦5★ Мерзлота: &f65% замедл. + 25% заморозка!"
		text "&7[3]85" at -136 158 anchor center shadow layer 12 id lbl3
		image "td:textures/gui/tower_mage.png" at -100 140 size 22x22 texture 48x48 anchor center layer 12 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d[4] Маг &7(130з)\n&7Урон: 32 | &dДальность 132\n&6✦5★ Убийца Боссов: &fx2 Боссам + 4% макс.HP!"
		text "&7[4]130" at -100 158 anchor center shadow layer 12 id lbl4
		image "td:textures/gui/tower_tesla.png" at -64 140 size 22x22 texture 48x48 anchor center layer 12 id ico5 run TdSelectTool(player, 5) keep open tooltip "&b[5] Тесла &7(115з)\n&7Урон: 20 | &bЦепная молния (3 цели) | Дальн: 108\n&6✦5★ Шторм: &f5 целей + 90% урон + стан!"
		text "&7[5]115" at -64 158 anchor center shadow layer 12 id lbl5
		image "td:textures/gui/tower_sniper.png" at -28 140 size 22x22 texture 48x48 anchor center layer 12 id ico6 run TdSelectTool(player, 6) keep open tooltip "&c[6] Снайпер &7(150з)\n&7Урон: 56 | &cКрит 25% | Дальн: 215\n&6✦5★ Казнь: &fкрит 45% x2.5 + казнь <20% HP!"
		text "&7[6]150" at -28 158 anchor center shadow layer 12 id lbl6
		image "td:textures/gui/tower_poison.png" at 8 140 size 22x22 texture 48x48 anchor center layer 12 id ico7 run TdSelectTool(player, 7) keep open tooltip "&a[7] Алхимик &7(105з)\n&7Урон: 12 + &aЯд (урон в сек.) | Дальн: 100\n&6✦5★ Чума: &fяд заражает всех врагов вокруг!"
		text "&7[7]105" at 8 158 anchor center shadow layer 12 id lbl7
		image "td:textures/gui/tower_farm.png" at 44 140 size 22x22 texture 48x48 anchor center layer 12 id ico8 run TdSelectTool(player, 8) keep open tooltip "&6[8] Банк &7(140з)\n&7Доход: &6+28/+50/+80/+120з &7каждую волну!\n&6✦5★ Дворец: &f+180з за волну и +1 HP базе!"
		text "&7[8]140" at 44 158 anchor center shadow layer 12 id lbl8
		button "&aСтарт/x2" at 118 139 size 68x16 anchor center run TdWaveOrSpeed(player) keep open tooltip "&aЗапустить волну &7(Space)\n&bВо время волны: скорость x1 / x2"
		toggle "Авто" id autowave at 192 139 size 58x16 anchor center value false color #22C55E tooltip "&aАвто-волна\n&7Авто-старт следующей волны через 1.5с"
		button "&cПродать" at 118 158 size 68x16 anchor center run TdToggleSell(player) keep open tooltip "&cПродать свою башню &7(X)\n&7Возврат 70% стоимости"
		button "Меню" at 192 158 size 58x16 anchor center run TdOpenMenu(player) keep open tooltip "&eВыбор карты, кооп и сложность &7(M)"

function TdOpenMap3(p: player):
	open screen "Tower Defense" to p:
		# Верхняя HUD-панель (без PNG — не бывает битых текстур!)
		bar "bg_hud" value 0 max 1 at 0 -131 size 456x22 anchor center background #DD0F172A layer 11
		image "td:textures/gui/icon_wave.png" at -214 -131 size 12x12 texture 32x32 anchor center layer 12 tooltip "&eТекущая волна"
		text "&f0/20" at -188 -131 anchor center shadow layer 12 id hud_wave
		image "td:textures/gui/icon_coin.png" at -142 -131 size 12x12 texture 32x32 anchor center layer 12 tooltip "&6Ваше личное золото"
		text "&6120" at -116 -131 anchor center shadow layer 12 id hud_gold
		image "td:textures/gui/icon_heart.png" at -68 -131 size 12x12 texture 32x32 anchor center layer 12 tooltip "&cЗдоровье базы"
		text "&c10" at -48 -131 anchor center shadow layer 12 id hud_hp
		image "td:textures/gui/icon_trophy.png" at -12 -131 size 12x12 texture 32x32 anchor center layer 12 tooltip "&aОбщий счёт"
		text "&a0" at 16 -131 anchor center shadow layer 12 id hud_score
		text "&7Соло &7[x1]" at 142 -131 anchor center shadow layer 12 id hud_team

		# Большая HD карта 448x224 (текстура 896x448)
		image "td:textures/gui/map_ice.png" at 0 -2 size 448x224 texture 896x448 anchor center layer 0 id map
		bar "boss_bar" value 100 max 100 at 0 -106 size 220x8 anchor center color #E74C3C background #AA1A0A0A text "&c&lБОСС: %value%/%max%" shadow layer 14 hidden

		# Круги радиуса башни (малый / средний / снайпер)
		image "td:textures/gui/range_circle.png" at 0 0 size 196x196 texture 192x192 anchor center layer 2 id rng_s hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 260x260 texture 192x192 anchor center layer 2 id rng_m hidden
		image "td:textures/gui/range_circle.png" at 0 0 size 390x390 texture 192x192 anchor center layer 2 id rng_l hidden

		# Свободная сетка постройки 16x8 (клетка 28x28)
		image "td:textures/gui/tile_build.png" at -210 -100 size 28x28 texture 28x28 anchor center layer 3 id c0_0 run TdCellClick(player, 0, 0) keep open
		image "td:textures/gui/tile_build.png" at -182 -100 size 28x28 texture 28x28 anchor center layer 3 id c1_0 run TdCellClick(player, 1, 0) keep open
		image "td:textures/gui/tile_build.png" at -154 -100 size 28x28 texture 28x28 anchor center layer 3 id c2_0 run TdCellClick(player, 2, 0) keep open
		image "td:textures/gui/tile_build.png" at -126 -100 size 28x28 texture 28x28 anchor center layer 3 id c3_0 run TdCellClick(player, 3, 0) keep open
		image "td:textures/gui/tile_build.png" at -98 -100 size 28x28 texture 28x28 anchor center layer 3 id c4_0 run TdCellClick(player, 4, 0) keep open
		image "td:textures/gui/tile_build.png" at -70 -100 size 28x28 texture 28x28 anchor center layer 3 id c5_0 run TdCellClick(player, 5, 0) keep open
		image "td:textures/gui/tile_build.png" at -42 -100 size 28x28 texture 28x28 anchor center layer 3 id c6_0 run TdCellClick(player, 6, 0) keep open
		image "td:textures/gui/tile_build.png" at -14 -100 size 28x28 texture 28x28 anchor center layer 3 id c7_0 run TdCellClick(player, 7, 0) keep open
		image "td:textures/gui/tile_build.png" at 14 -100 size 28x28 texture 28x28 anchor center layer 3 id c8_0 run TdCellClick(player, 8, 0) keep open
		image "td:textures/gui/tile_build.png" at 42 -100 size 28x28 texture 28x28 anchor center layer 3 id c9_0 run TdCellClick(player, 9, 0) keep open
		image "td:textures/gui/tile_build.png" at 70 -100 size 28x28 texture 28x28 anchor center layer 3 id c10_0 run TdCellClick(player, 10, 0) keep open
		image "td:textures/gui/tile_build.png" at 98 -100 size 28x28 texture 28x28 anchor center layer 3 id c11_0 run TdCellClick(player, 11, 0) keep open
		image "td:textures/gui/tile_build.png" at 126 -100 size 28x28 texture 28x28 anchor center layer 3 id c12_0 run TdCellClick(player, 12, 0) keep open
		image "td:textures/gui/tile_build.png" at 154 -100 size 28x28 texture 28x28 anchor center layer 3 id c13_0 run TdCellClick(player, 13, 0) keep open
		image "td:textures/gui/tile_build.png" at 182 -100 size 28x28 texture 28x28 anchor center layer 3 id c14_0 run TdCellClick(player, 14, 0) keep open
		image "td:textures/gui/tile_build.png" at 210 -100 size 28x28 texture 28x28 anchor center layer 3 id c15_0 run TdCellClick(player, 15, 0) keep open
		image "td:textures/gui/tile_build.png" at -210 -72 size 28x28 texture 28x28 anchor center layer 3 id c0_1 run TdCellClick(player, 0, 1) keep open
		image "td:textures/gui/tile_build.png" at -182 -72 size 28x28 texture 28x28 anchor center layer 3 id c1_1 run TdCellClick(player, 1, 1) keep open
		image "td:textures/gui/tile_build.png" at -154 -72 size 28x28 texture 28x28 anchor center layer 3 id c2_1 run TdCellClick(player, 2, 1) keep open
		image "td:textures/gui/tile_build.png" at 14 -72 size 28x28 texture 28x28 anchor center layer 3 id c8_1 run TdCellClick(player, 8, 1) keep open
		image "td:textures/gui/tile_build.png" at 42 -72 size 28x28 texture 28x28 anchor center layer 3 id c9_1 run TdCellClick(player, 9, 1) keep open
		image "td:textures/gui/tile_build.png" at 70 -72 size 28x28 texture 28x28 anchor center layer 3 id c10_1 run TdCellClick(player, 10, 1) keep open
		image "td:textures/gui/tile_build.png" at 98 -72 size 28x28 texture 28x28 anchor center layer 3 id c11_1 run TdCellClick(player, 11, 1) keep open
		image "td:textures/gui/tile_build.png" at 126 -72 size 28x28 texture 28x28 anchor center layer 3 id c12_1 run TdCellClick(player, 12, 1) keep open
		image "td:textures/gui/tile_build.png" at 154 -72 size 28x28 texture 28x28 anchor center layer 3 id c13_1 run TdCellClick(player, 13, 1) keep open
		image "td:textures/gui/tile_build.png" at 182 -72 size 28x28 texture 28x28 anchor center layer 3 id c14_1 run TdCellClick(player, 14, 1) keep open
		image "td:textures/gui/tile_build.png" at 210 -72 size 28x28 texture 28x28 anchor center layer 3 id c15_1 run TdCellClick(player, 15, 1) keep open
		image "td:textures/gui/tile_build.png" at -210 -44 size 28x28 texture 28x28 anchor center layer 3 id c0_2 run TdCellClick(player, 0, 2) keep open
		image "td:textures/gui/tile_build.png" at -182 -44 size 28x28 texture 28x28 anchor center layer 3 id c1_2 run TdCellClick(player, 1, 2) keep open
		image "td:textures/gui/tile_build.png" at -154 -44 size 28x28 texture 28x28 anchor center layer 3 id c2_2 run TdCellClick(player, 2, 2) keep open
		image "td:textures/gui/tile_build.png" at -98 -44 size 28x28 texture 28x28 anchor center layer 3 id c4_2 run TdCellClick(player, 4, 2) keep open
		image "td:textures/gui/tile_build.png" at -70 -44 size 28x28 texture 28x28 anchor center layer 3 id c5_2 run TdCellClick(player, 5, 2) keep open
		image "td:textures/gui/tile_build.png" at -42 -44 size 28x28 texture 28x28 anchor center layer 3 id c6_2 run TdCellClick(player, 6, 2) keep open
		image "td:textures/gui/tile_build.png" at 14 -44 size 28x28 texture 28x28 anchor center layer 3 id c8_2 run TdCellClick(player, 8, 2) keep open
		image "td:textures/gui/tile_build.png" at 42 -44 size 28x28 texture 28x28 anchor center layer 3 id c9_2 run TdCellClick(player, 9, 2) keep open
		image "td:textures/gui/tile_build.png" at 70 -44 size 28x28 texture 28x28 anchor center layer 3 id c10_2 run TdCellClick(player, 10, 2) keep open
		image "td:textures/gui/tile_build.png" at -210 -16 size 28x28 texture 28x28 anchor center layer 3 id c0_3 run TdCellClick(player, 0, 3) keep open
		image "td:textures/gui/tile_build.png" at -182 -16 size 28x28 texture 28x28 anchor center layer 3 id c1_3 run TdCellClick(player, 1, 3) keep open
		image "td:textures/gui/tile_build.png" at -154 -16 size 28x28 texture 28x28 anchor center layer 3 id c2_3 run TdCellClick(player, 2, 3) keep open
		image "td:textures/gui/tile_build.png" at -98 -16 size 28x28 texture 28x28 anchor center layer 3 id c4_3 run TdCellClick(player, 4, 3) keep open
		image "td:textures/gui/tile_build.png" at -70 -16 size 28x28 texture 28x28 anchor center layer 3 id c5_3 run TdCellClick(player, 5, 3) keep open
		image "td:textures/gui/tile_build.png" at -42 -16 size 28x28 texture 28x28 anchor center layer 3 id c6_3 run TdCellClick(player, 6, 3) keep open
		image "td:textures/gui/tile_build.png" at 14 -16 size 28x28 texture 28x28 anchor center layer 3 id c8_3 run TdCellClick(player, 8, 3) keep open
		image "td:textures/gui/tile_build.png" at 42 -16 size 28x28 texture 28x28 anchor center layer 3 id c9_3 run TdCellClick(player, 9, 3) keep open
		image "td:textures/gui/tile_build.png" at 70 -16 size 28x28 texture 28x28 anchor center layer 3 id c10_3 run TdCellClick(player, 10, 3) keep open
		image "td:textures/gui/tile_build.png" at 126 -16 size 28x28 texture 28x28 anchor center layer 3 id c12_3 run TdCellClick(player, 12, 3) keep open
		image "td:textures/gui/tile_build.png" at 154 -16 size 28x28 texture 28x28 anchor center layer 3 id c13_3 run TdCellClick(player, 13, 3) keep open
		image "td:textures/gui/tile_build.png" at 182 -16 size 28x28 texture 28x28 anchor center layer 3 id c14_3 run TdCellClick(player, 14, 3) keep open
		image "td:textures/gui/tile_build.png" at 210 -16 size 28x28 texture 28x28 anchor center layer 3 id c15_3 run TdCellClick(player, 15, 3) keep open
		image "td:textures/gui/tile_build.png" at -98 12 size 28x28 texture 28x28 anchor center layer 3 id c4_4 run TdCellClick(player, 4, 4) keep open
		image "td:textures/gui/tile_build.png" at -70 12 size 28x28 texture 28x28 anchor center layer 3 id c5_4 run TdCellClick(player, 5, 4) keep open
		image "td:textures/gui/tile_build.png" at -42 12 size 28x28 texture 28x28 anchor center layer 3 id c6_4 run TdCellClick(player, 6, 4) keep open
		image "td:textures/gui/tile_build.png" at 14 12 size 28x28 texture 28x28 anchor center layer 3 id c8_4 run TdCellClick(player, 8, 4) keep open
		image "td:textures/gui/tile_build.png" at 42 12 size 28x28 texture 28x28 anchor center layer 3 id c9_4 run TdCellClick(player, 9, 4) keep open
		image "td:textures/gui/tile_build.png" at 70 12 size 28x28 texture 28x28 anchor center layer 3 id c10_4 run TdCellClick(player, 10, 4) keep open
		image "td:textures/gui/tile_build.png" at 126 12 size 28x28 texture 28x28 anchor center layer 3 id c12_4 run TdCellClick(player, 12, 4) keep open
		image "td:textures/gui/tile_build.png" at 154 12 size 28x28 texture 28x28 anchor center layer 3 id c13_4 run TdCellClick(player, 13, 4) keep open
		image "td:textures/gui/tile_build.png" at 182 12 size 28x28 texture 28x28 anchor center layer 3 id c14_4 run TdCellClick(player, 14, 4) keep open
		image "td:textures/gui/tile_build.png" at 210 12 size 28x28 texture 28x28 anchor center layer 3 id c15_4 run TdCellClick(player, 15, 4) keep open
		image "td:textures/gui/tile_build.png" at -210 40 size 28x28 texture 28x28 anchor center layer 3 id c0_5 run TdCellClick(player, 0, 5) keep open
		image "td:textures/gui/tile_build.png" at -182 40 size 28x28 texture 28x28 anchor center layer 3 id c1_5 run TdCellClick(player, 1, 5) keep open
		image "td:textures/gui/tile_build.png" at -154 40 size 28x28 texture 28x28 anchor center layer 3 id c2_5 run TdCellClick(player, 2, 5) keep open
		image "td:textures/gui/tile_build.png" at -126 40 size 28x28 texture 28x28 anchor center layer 3 id c3_5 run TdCellClick(player, 3, 5) keep open
		image "td:textures/gui/tile_build.png" at -98 40 size 28x28 texture 28x28 anchor center layer 3 id c4_5 run TdCellClick(player, 4, 5) keep open
		image "td:textures/gui/tile_build.png" at -70 40 size 28x28 texture 28x28 anchor center layer 3 id c5_5 run TdCellClick(player, 5, 5) keep open
		image "td:textures/gui/tile_build.png" at -42 40 size 28x28 texture 28x28 anchor center layer 3 id c6_5 run TdCellClick(player, 6, 5) keep open
		image "td:textures/gui/tile_build.png" at 14 40 size 28x28 texture 28x28 anchor center layer 3 id c8_5 run TdCellClick(player, 8, 5) keep open
		image "td:textures/gui/tile_build.png" at 42 40 size 28x28 texture 28x28 anchor center layer 3 id c9_5 run TdCellClick(player, 9, 5) keep open
		image "td:textures/gui/tile_build.png" at 70 40 size 28x28 texture 28x28 anchor center layer 3 id c10_5 run TdCellClick(player, 10, 5) keep open
		image "td:textures/gui/tile_build.png" at 126 40 size 28x28 texture 28x28 anchor center layer 3 id c12_5 run TdCellClick(player, 12, 5) keep open
		image "td:textures/gui/tile_build.png" at 154 40 size 28x28 texture 28x28 anchor center layer 3 id c13_5 run TdCellClick(player, 13, 5) keep open
		image "td:textures/gui/tile_build.png" at 182 40 size 28x28 texture 28x28 anchor center layer 3 id c14_5 run TdCellClick(player, 14, 5) keep open
		image "td:textures/gui/tile_build.png" at 210 40 size 28x28 texture 28x28 anchor center layer 3 id c15_5 run TdCellClick(player, 15, 5) keep open
		image "td:textures/gui/tile_build.png" at -210 68 size 28x28 texture 28x28 anchor center layer 3 id c0_6 run TdCellClick(player, 0, 6) keep open
		image "td:textures/gui/tile_build.png" at -182 68 size 28x28 texture 28x28 anchor center layer 3 id c1_6 run TdCellClick(player, 1, 6) keep open
		image "td:textures/gui/tile_build.png" at -154 68 size 28x28 texture 28x28 anchor center layer 3 id c2_6 run TdCellClick(player, 2, 6) keep open
		image "td:textures/gui/tile_build.png" at -126 68 size 28x28 texture 28x28 anchor center layer 3 id c3_6 run TdCellClick(player, 3, 6) keep open
		image "td:textures/gui/tile_build.png" at -98 68 size 28x28 texture 28x28 anchor center layer 3 id c4_6 run TdCellClick(player, 4, 6) keep open
		image "td:textures/gui/tile_build.png" at -70 68 size 28x28 texture 28x28 anchor center layer 3 id c5_6 run TdCellClick(player, 5, 6) keep open
		image "td:textures/gui/tile_build.png" at -42 68 size 28x28 texture 28x28 anchor center layer 3 id c6_6 run TdCellClick(player, 6, 6) keep open
		image "td:textures/gui/tile_build.png" at 126 68 size 28x28 texture 28x28 anchor center layer 3 id c12_6 run TdCellClick(player, 12, 6) keep open
		image "td:textures/gui/tile_build.png" at 154 68 size 28x28 texture 28x28 anchor center layer 3 id c13_6 run TdCellClick(player, 13, 6) keep open
		image "td:textures/gui/tile_build.png" at 182 68 size 28x28 texture 28x28 anchor center layer 3 id c14_6 run TdCellClick(player, 14, 6) keep open
		image "td:textures/gui/tile_build.png" at 210 68 size 28x28 texture 28x28 anchor center layer 3 id c15_6 run TdCellClick(player, 15, 6) keep open
		image "td:textures/gui/tile_build.png" at -210 96 size 28x28 texture 28x28 anchor center layer 3 id c0_7 run TdCellClick(player, 0, 7) keep open
		image "td:textures/gui/tile_build.png" at -182 96 size 28x28 texture 28x28 anchor center layer 3 id c1_7 run TdCellClick(player, 1, 7) keep open
		image "td:textures/gui/tile_build.png" at -154 96 size 28x28 texture 28x28 anchor center layer 3 id c2_7 run TdCellClick(player, 2, 7) keep open
		image "td:textures/gui/tile_build.png" at -126 96 size 28x28 texture 28x28 anchor center layer 3 id c3_7 run TdCellClick(player, 3, 7) keep open
		image "td:textures/gui/tile_build.png" at -98 96 size 28x28 texture 28x28 anchor center layer 3 id c4_7 run TdCellClick(player, 4, 7) keep open
		image "td:textures/gui/tile_build.png" at -70 96 size 28x28 texture 28x28 anchor center layer 3 id c5_7 run TdCellClick(player, 5, 7) keep open
		image "td:textures/gui/tile_build.png" at -42 96 size 28x28 texture 28x28 anchor center layer 3 id c6_7 run TdCellClick(player, 6, 7) keep open
		image "td:textures/gui/tile_build.png" at -14 96 size 28x28 texture 28x28 anchor center layer 3 id c7_7 run TdCellClick(player, 7, 7) keep open
		image "td:textures/gui/tile_build.png" at 14 96 size 28x28 texture 28x28 anchor center layer 3 id c8_7 run TdCellClick(player, 8, 7) keep open
		image "td:textures/gui/tile_build.png" at 42 96 size 28x28 texture 28x28 anchor center layer 3 id c9_7 run TdCellClick(player, 9, 7) keep open
		image "td:textures/gui/tile_build.png" at 70 96 size 28x28 texture 28x28 anchor center layer 3 id c10_7 run TdCellClick(player, 10, 7) keep open
		image "td:textures/gui/tile_build.png" at 98 96 size 28x28 texture 28x28 anchor center layer 3 id c11_7 run TdCellClick(player, 11, 7) keep open
		image "td:textures/gui/tile_build.png" at 126 96 size 28x28 texture 28x28 anchor center layer 3 id c12_7 run TdCellClick(player, 12, 7) keep open
		image "td:textures/gui/tile_build.png" at 154 96 size 28x28 texture 28x28 anchor center layer 3 id c13_7 run TdCellClick(player, 13, 7) keep open
		image "td:textures/gui/tile_build.png" at 182 96 size 28x28 texture 28x28 anchor center layer 3 id c14_7 run TdCellClick(player, 14, 7) keep open
		image "td:textures/gui/tile_build.png" at 210 96 size 28x28 texture 28x28 anchor center layer 3 id c15_7 run TdCellClick(player, 15, 7) keep open

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

		# 11 слотов обычных врагов + 1 слот Босса (HD 40x40 / 56x56)
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e1 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e2 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e3 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e4 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e5 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e6 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e7 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e8 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e9 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e10 hidden
		image "td:textures/gui/mob_slime.png" at -210 -72 size 22x22 texture 40x40 anchor center layer 7 id e11 hidden
		image "td:textures/gui/mob_boss.png" at -210 -72 size 32x32 texture 56x56 anchor center layer 8 id e12 hidden

		# 6 летающих HD прожектайлов + вспышка взрыва
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr1 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr2 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr3 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr4 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr5 hidden
		image "td:textures/gui/proj_arrow.png" at 0 0 size 11x11 texture 20x20 anchor center layer 9 id pr6 hidden
		image "td:textures/gui/fx_boom.png" at 0 0 size 20x20 texture 32x32 anchor center layer 10 id fx hidden

		# Информационная строка (между картой и нижней панелью — ничего не перекрывает)
		bar "bg_stat" value 0 max 1 at 0 119 size 456x14 anchor center background #DD0F172A layer 11
		text "&eВыбран: &fЛучник (50з) &7— клик по траве строит, по башне улучшает/показывает радиус!" at 0 119 anchor center shadow layer 12 id status_txt
		text "&c&lБАЗА ПАЛА!" at 0 -16 anchor center shadow layer 20 id end_title hidden
		text "&fНажми &eR &f(рестарт) или &eM &f(меню)" at 0 2 anchor center shadow layer 20 id end_sub hidden

		# Нижняя панель инструментов (8 HD башен + 3 кнопки + Авто-волна)
		bar "bg_bar" value 0 max 1 at 0 148 size 456x40 anchor center background #EE0B1220 layer 11
		image "td:textures/gui/tower_archer.png" at -208 140 size 22x22 texture 48x48 anchor center layer 12 id ico1 run TdSelectTool(player, 1) keep open tooltip "&e[1] Лучник &7(50з)\n&7Урон: 15 | Очень быстрый | Дальн: 98\n&6✦5★ Шквал: &fстреляет сразу по 2 целям!"
		text "&e[1]50" at -208 158 anchor center shadow layer 12 id lbl1
		image "td:textures/gui/tower_cannon.png" at -172 140 size 22x22 texture 48x48 anchor center layer 12 id ico2 run TdSelectTool(player, 2) keep open tooltip "&6[2] Пушка &7(95з)\n&7Урон: 36 + &cСплеш | Дальн: 92\n&6✦5★ Напалм: &fмега-сплеш + поджог врагов!"
		text "&7[2]95" at -172 158 anchor center shadow layer 12 id lbl2
		image "td:textures/gui/tower_frost.png" at -136 140 size 22x22 texture 48x48 anchor center layer 12 id ico3 run TdSelectTool(player, 3) keep open tooltip "&b[3] Лёд &7(85з)\n&7Урон: 11 + &bЗамедление 45% | Дальн: 96\n&6✦5★ Мерзлота: &f65% замедл. + 25% заморозка!"
		text "&7[3]85" at -136 158 anchor center shadow layer 12 id lbl3
		image "td:textures/gui/tower_mage.png" at -100 140 size 22x22 texture 48x48 anchor center layer 12 id ico4 run TdSelectTool(player, 4) keep open tooltip "&d[4] Маг &7(130з)\n&7Урон: 32 | &dДальность 132\n&6✦5★ Убийца Боссов: &fx2 Боссам + 4% макс.HP!"
		text "&7[4]130" at -100 158 anchor center shadow layer 12 id lbl4
		image "td:textures/gui/tower_tesla.png" at -64 140 size 22x22 texture 48x48 anchor center layer 12 id ico5 run TdSelectTool(player, 5) keep open tooltip "&b[5] Тесла &7(115з)\n&7Урон: 20 | &bЦепная молния (3 цели) | Дальн: 108\n&6✦5★ Шторм: &f5 целей + 90% урон + стан!"
		text "&7[5]115" at -64 158 anchor center shadow layer 12 id lbl5
		image "td:textures/gui/tower_sniper.png" at -28 140 size 22x22 texture 48x48 anchor center layer 12 id ico6 run TdSelectTool(player, 6) keep open tooltip "&c[6] Снайпер &7(150з)\n&7Урон: 56 | &cКрит 25% | Дальн: 215\n&6✦5★ Казнь: &fкрит 45% x2.5 + казнь <20% HP!"
		text "&7[6]150" at -28 158 anchor center shadow layer 12 id lbl6
		image "td:textures/gui/tower_poison.png" at 8 140 size 22x22 texture 48x48 anchor center layer 12 id ico7 run TdSelectTool(player, 7) keep open tooltip "&a[7] Алхимик &7(105з)\n&7Урон: 12 + &aЯд (урон в сек.) | Дальн: 100\n&6✦5★ Чума: &fяд заражает всех врагов вокруг!"
		text "&7[7]105" at 8 158 anchor center shadow layer 12 id lbl7
		image "td:textures/gui/tower_farm.png" at 44 140 size 22x22 texture 48x48 anchor center layer 12 id ico8 run TdSelectTool(player, 8) keep open tooltip "&6[8] Банк &7(140з)\n&7Доход: &6+28/+50/+80/+120з &7каждую волну!\n&6✦5★ Дворец: &f+180з за волну и +1 HP базе!"
		text "&7[8]140" at 44 158 anchor center shadow layer 12 id lbl8
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
	if tool is 1:
		set text of screen element "lbl1" for p to "&e[1]50"
		set text of screen element "status_txt" for p to "&eЛучник (50з) &7| &6✦5★: &fШквал по 2 целям!"
	else if tool is 2:
		set text of screen element "lbl2" for p to "&6[2]95"
		set text of screen element "status_txt" for p to "&6Пушка (95з) &7| &6✦5★: &fНапалм (поджог + мега-сплеш)!"
	else if tool is 3:
		set text of screen element "lbl3" for p to "&b[3]85"
		set text of screen element "status_txt" for p to "&bЛёд (85з) &7| &6✦5★: &fМерзлота (заморозка на месте)!"
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
	# Вычисляем радиус в {-td-tmp-rng}
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
		TdSyncImg({-g}, "c%col%_%row%", "td:textures/gui/tile_build.png")
		TdSyncHide({-g}, "st%{-tid}%")
		TdSyncStatus({-g}, "&e%p% продал башню (+%{-ref}%з)")
		TdSyncSound({-g}, "entity.experience_orb.pickup", 0.6, 0.9)
		TdSyncHud({-g})
		stop

	# Клик по построенной башне -> показываем её круг радиуса + владельца + улучшаем/эволюционируем!
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
		if {-nl} is 2:
			TdSyncTxt({-g}, "st%{-tid}%", "&6★★")
			TdSyncStatus({-g}, "&a%p% улучшил башню (%{-own}%) → ур.2! (-%{-up}%з)")
			TdSyncSound({-g}, "entity.player.levelup", 0.5, 1.35)
		else if {-nl} is 3:
			TdSyncTxt({-g}, "st%{-tid}%", "&d★★★")
			TdSyncStatus({-g}, "&a%p% улучшил башню (%{-own}%) → ур.3! (-%{-up}%з)")
			TdSyncSound({-g}, "entity.player.levelup", 0.5, 1.45)
		else if {-nl} is 4:
			TdSyncTxt({-g}, "st%{-tid}%", "&b★★★★")
			TdSyncStatus({-g}, "&b%p% поднял ур.4! След. клик (570з) → &6&l✦5★ ЭВОЛЮЦИЯ!")
			TdSyncSound({-g}, "entity.player.levelup", 0.55, 1.6)
		else:
			TdSyncTxt({-g}, "st%{-tid}%", "&6&l✦5★")
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
			else if {-tt} is 7:
				set {-evo} to "Чума (заразный яд по области!)"
			else if {-tt} is 8:
				set {-evo} to "Золотой Дворец (+180з и +1 HP!)"
			TdSyncStatus({-g}, "&6&l✦ ЭВОЛЮЦИЯ 5★! &f%p% открыл: &e%{-evo}%")
			TdSyncSound({-g}, "ui.toast.challenge_complete", 0.75, 1.2)
		TdSyncHud({-g})
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
	else if {-tool} is 7:
		set {-cost} to 105
		set {-tex} to "td:textures/gui/tower_poison.png"
	else if {-tool} is 8:
		set {-cost} to 140
		set {-tex} to "td:textures/gui/tower_farm.png"

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
	set {-td-tx::%{-g}%::%{-slot}%} to {-cx}
	set {-td-ty::%{-g}%::%{-slot}%} to {-cy}
	set {-td-grid::%{-g}%::%col%_%row%} to {-slot}

	TdTowerRange({-tool}, 1)
	TdShowRange(p, {-cx}, {-cy}, {-td-tmp-rng})
	TdSyncImg({-g}, "c%col%_%row%", {-tex})
	set {-sy} to {-cy} + 11
	TdSyncPos({-g}, "st%{-slot}%", {-cx}, {-sy})
	TdSyncTxt({-g}, "st%{-slot}%", "&e★")
	TdSyncShow({-g}, "st%{-slot}%")
	TdSyncStatus({-g}, "&a%p% построил башню! Клик по ней покажет радиус и улучшит (до ✦5★)")
	TdSyncSound({-g}, "block.anvil.use", 0.35, 1.5)
	TdSyncHud({-g})

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

	# 2. Враги: регенерация Некроманта, горение Напалма, яд Алхимика, движение
	set {-boss-alive} to 0
	loop 12 times:
		set {-ei} to %loop-number%
		if {-td-ea::%g%::%{-ei}%} is 1:
			if {-td-eregen::%g%::%{-ei}%} > 0:
				if {-td-ehp::%g%::%{-ei}%} < {-td-emhp::%g%::%{-ei}%}:
					add {-td-eregen::%g%::%{-ei}%} to {-td-ehp::%g%::%{-ei}%}
			if {-td-eburn::%g%::%{-ei}%} > 0:
				remove 1 from {-td-eburn::%g%::%{-ei}%}
				TdDamageEnemy(g, {-ei}, {-td-eburn-d::%g%::%{-ei}%}, 0, 0)
			if {-td-ea::%g%::%{-ei}%} is 1 and {-td-epoison::%g%::%{-ei}%} > 0:
				remove 1 from {-td-epoison::%g%::%{-ei}%}
				TdDamageEnemy(g, {-ei}, {-td-epoison-d::%g%::%{-ei}%}, 0, 0)
			if {-td-ea::%g%::%{-ei}%} is 1:
				TdMoveEnemy(g, {-ei})
			if {-td-ea::%g%::%{-ei}%} is 1 and {-td-eboss::%g%::%{-ei}%} is 1:
				set {-boss-alive} to 1
				TdSyncBossBar(g, {-td-ehp::%g%::%{-ei}%}, 0)
	if {-boss-alive} is 0:
		TdSyncHide(g, "boss_bar")

	# 3. Полёт прожектайлов
	TdMoveProjectiles(g)

	# 4. Стрельба всех башен (кроме Банка)
	loop 28 times:
		set {-ti} to %loop-number%
		if {-td-ta::%g%::%{-ti}%} is 1 and {-td-tt::%g%::%{-ti}%} is not 8:
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
			# Доход от Банков (башня #8) их владельцам + лечение базы от ✦5★ Дворца!
			set {-bank-tot} to 0
			loop 28 times:
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
	# Масштабирование сложности для 1..4 игроков!
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
			set {-td-spawn-cd::%g%} to 2
			stop
		remove 1 from {-td-to-spawn::%g%}

		set {-hp} to round((35 + {-w} * 25 + {-w} * {-w} * 2.6) * {-dmul})
		set {-spd} to 4.1 + {-w} * 0.09
		set {-rew} to 10 + round({-w} * 0.65)
		set {-cd} to 7 - floor({-w} / 6)
		if {-cd} < 3:
			set {-cd} to 3
		set {-tex} to "td:textures/gui/mob_slime.png"
		set {-reg} to 0
		set {-imm} to 0
		set {-bdmg} to 1

		# 10 видов обычных мобов с уникальными свойствами!
		set {-m10} to {-w} % 10
		if {-m10} is 2:
			# Гоблин-бегун (сверхбыстрый!)
			set {-tex} to "td:textures/gui/mob_goblin.png"
			set {-hp} to round({-hp} * 0.7)
			set {-spd} to {-spd} + 1.65
			set {-cd} to 3
		else if {-m10} is 3:
			# Орк-воин
			set {-tex} to "td:textures/gui/mob_orc.png"
			set {-hp} to round({-hp} * 1.15)
		else if {-m10} is 4:
			# Пещерный паук (быстрая плотная стая)
			set {-tex} to "td:textures/gui/mob_spider.png"
			set {-hp} to round({-hp} * 0.8)
			set {-spd} to {-spd} + 1.35
			set {-cd} to 3
		else if {-m10} is 5:
			# Тёмный рыцарь (броня)
			set {-tex} to "td:textures/gui/mob_knight.png"
			set {-hp} to round({-hp} * 1.35)
			set {-spd} to {-spd} - 0.35
		else if {-m10} is 6:
			# Призрак (быстрый разведчик)
			set {-tex} to "td:textures/gui/mob_ghost.png"
			set {-hp} to round({-hp} * 0.88)
			set {-spd} to {-spd} + 1.2
		else if {-m10} is 7:
			# Некромант (регенерирует HP на ходу!)
			set {-tex} to "td:textures/gui/mob_necromancer.png"
			set {-hp} to round({-hp} * 1.1)
			set {-reg} to round({-hp} * 0.015)
			add 4 to {-rew}
		else if {-m10} is 8:
			# Ифрит (ИММУНИТЕТ к замедлению льда!)
			set {-tex} to "td:textures/gui/mob_blaze.png"
			set {-hp} to round({-hp} * 1.05)
			set {-spd} to {-spd} + 0.65
			set {-imm} to 1
			add 4 to {-rew}
		else if {-m10} is 9:
			# Лавовый голем (жирный танк)
			set {-tex} to "td:textures/gui/mob_golem.png"
			set {-hp} to round({-hp} * 1.7)
			set {-spd} to {-spd} - 0.8
			add 6 to {-rew}
		else if {-m10} is 0:
			# Разоритель (элитный танк, бьёт базу на 2 HP!)
			set {-tex} to "td:textures/gui/mob_ravager.png"
			set {-hp} to round({-hp} * 1.85)
			set {-spd} to {-spd} - 0.5
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
		set {-td-eslow-f::%g%::%{-slot}%} to 0.55
		set {-td-efreeze::%g%::%{-slot}%} to 0
		set {-td-eburn::%g%::%{-slot}%} to 0
		set {-td-epoison::%g%::%{-slot}%} to 0
		TdSyncImg(g, "e%{-slot}%", {-tex})
		TdSyncPos(g, "e%{-slot}%", {-sx}, {-sy})
		TdSyncShow(g, "e%{-slot}%")
		stop

	# Спавн Босса (слот 12 — 3 вида боссов!)
	if {-td-spawn-boss::%g%} > 0 and {-td-ea::%g%::12} is 0:
		set {-bk} to {-td-spawn-boss::%g%}
		set {-td-spawn-boss::%g%} to 0
		set {-bhp} to round((520 + {-bk} * {-bk} * 420) * {-dmul})
		set {-bspd} to 2.6 + {-bk} * 0.09
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
		set {-td-eslow-f::%g%::12} to 0.55
		set {-td-efreeze::%g%::12} to 0
		set {-td-eburn::%g%::12} to 0
		set {-td-epoison::%g%::12} to 0
		TdSyncImg(g, "e12", {-btex})
		TdSyncPos(g, "e12", {-sx}, {-sy})
		TdSyncShow(g, "e12")
		TdSyncBossBar(g, {-bhp}, {-bhp})
		TdSyncShow(g, "boss_bar")

function TdMoveEnemy(g: player, i: number):
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
			set {-dmg} to {-td-ebdmg::%g%::%i%}
			remove {-dmg} from {-td-hp::%g%}
			TdSyncSound(g, "entity.iron_golem.damage", 0.7, 0.8)
			loop {-td-team::%g%::*}:
				set {-tp} to %loop-value%
				shake camera of {-tp} with intensity 1.6 for 8 ticks
				fade screen of {-tp} to #FF1100 in 2 stay 2 out 8 ticks
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

	TdTowerRange({-type}, {-lvl})
	set {-range} to {-td-tmp-rng}

	set {-dmg} to 15
	set {-cd} to 4
	set {-pspd} to 28
	set {-ptex} to "td:textures/gui/proj_arrow.png"
	if {-type} is 2:
		set {-dmg} to 36
		set {-cd} to 11
		set {-pspd} to 20
		set {-ptex} to "td:textures/gui/proj_cannon.png"
	else if {-type} is 3:
		set {-dmg} to 11
		set {-cd} to 7
		set {-pspd} to 24
		set {-ptex} to "td:textures/gui/proj_frost.png"
	else if {-type} is 4:
		set {-dmg} to 32
		set {-cd} to 6
		set {-pspd} to 22
		set {-ptex} to "td:textures/gui/proj_mage.png"
	else if {-type} is 5:
		set {-dmg} to 20
		set {-cd} to 7
		set {-pspd} to 30
		set {-ptex} to "td:textures/gui/proj_tesla.png"
	else if {-type} is 6:
		set {-dmg} to 56
		set {-cd} to 13
		set {-pspd} to 38
		set {-ptex} to "td:textures/gui/proj_sniper.png"
	else if {-type} is 7:
		set {-dmg} to 12
		set {-cd} to 7
		set {-pspd} to 24
		set {-ptex} to "td:textures/gui/proj_poison.png"

	set {-dmg} to round({-dmg} * (1 + ({-lvl} - 1) * 0.55))
	if {-evo} is 1:
		set {-dmg} to round({-dmg} * 1.4)

	set {-best} to 0
	set {-best-prog} to -1
	set {-second} to 0
	loop 12 times:
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
		TdSyncPos(g, "fx", hx, hy)
		TdSyncShow(g, "fx")
		set {-td-fx-cd::%g%} to 2
		TdSyncSound(g, "entity.generic.explode", 0.15, 1.65)
		set {-rad} to 48
		if evo is 1:
			set {-rad} to 68
		loop 12 times:
			set {-si} to %loop-number%
			if {-td-ea::%g%::%{-si}%} is 1:
				set {-sdx} to {-td-ex::%g%::%{-si}%} - hx
				set {-sdy} to {-td-ey::%g%::%{-si}%} - hy
				if sqrt({-sdx} * {-sdx} + {-sdy} * {-sdy}) <= {-rad}:
					TdDamageEnemy(g, {-si}, dmg, 0, 0)
					if evo is 1 and {-td-ea::%g%::%{-si}%} is 1:
						set {-td-eburn::%g%::%{-si}%} to 12
						set {-td-eburn-d::%g%::%{-si}%} to round(dmg * 0.14)
	else if ptype is 3:
		TdSyncSound(g, "block.glass.break", 0.17, 1.75)
		set {-frz} to 0
		if evo is 1:
			set {-td-eslow-f::%g%::%tgt%} to 0.35
			chance of 25%:
				set {-frz} to 12
		else:
			set {-td-eslow-f::%g%::%tgt%} to 0.55
		TdDamageEnemy(g, tgt, dmg, 20, {-frz})
	else if ptype is 4:
		set {-fdmg} to dmg
		if evo is 1:
			if {-td-eboss::%g%::%tgt%} is 1:
				set {-fdmg} to round({-fdmg} * 2.0)
			add round({-td-emhp::%g%::%tgt%} * 0.04) to {-fdmg}
		TdSyncSound(g, "entity.illusioner.cast_spell", 0.17, 1.5)
		TdDamageEnemy(g, tgt, {-fdmg}, 0, 0)
	else if ptype is 5:
		TdSyncSound(g, "entity.lightning_bolt.impact", 0.13, 1.9)
		set {-maxh} to 3
		set {-crad} to 74
		set {-cmul} to 0.75
		set {-stun} to 0
		if evo is 1:
			set {-maxh} to 5
			set {-crad} to 96
			set {-cmul} to 0.9
			set {-stun} to 4
		TdDamageEnemy(g, tgt, dmg, 0, {-stun})
		set {-hits} to 1
		loop 12 times:
			set {-si} to %loop-number%
			if {-si} is not tgt and {-hits} < {-maxh} and {-td-ea::%g%::%{-si}%} is 1:
				set {-sdx} to {-td-ex::%g%::%{-si}%} - hx
				set {-sdy} to {-td-ey::%g%::%{-si}%} - hy
				if sqrt({-sdx} * {-sdx} + {-sdy} * {-sdy}) <= {-crad}:
					TdDamageEnemy(g, {-si}, round(dmg * {-cmul}), 0, {-stun})
					add 1 to {-hits}
	else if ptype is 6:
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
		TdSyncSound(g, "entity.arrow.hit_player", 0.22, 1.6)
		TdDamageEnemy(g, tgt, {-fdmg}, 0, 0)
	else if ptype is 7:
		# Алхимик: сильный яд (+ ✦5★ Чума: заражает всех врагов в радиусе 58!)
		TdSyncSound(g, "entity.splash_potion.break", 0.18, 1.4)
		TdDamageEnemy(g, tgt, dmg, 0, 0)
		if {-td-ea::%g%::%tgt%} is 1:
			set {-td-epoison::%g%::%tgt%} to 20
			set {-td-epoison-d::%g%::%tgt%} to round(dmg * 0.35)
		if evo is 1:
			loop 12 times:
				set {-si} to %loop-number%
				if {-si} is not tgt and {-td-ea::%g%::%{-si}%} is 1:
					set {-sdx} to {-td-ex::%g%::%{-si}%} - hx
					set {-sdy} to {-td-ey::%g%::%{-si}%} - hy
					if sqrt({-sdx} * {-sdx} + {-sdy} * {-sdy}) <= 58:
						set {-td-epoison::%g%::%{-si}%} to 20
						set {-td-epoison-d::%g%::%{-si}%} to round(dmg * 0.3)
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
		TdSyncHide(g, "e%i%")
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
	loop {-td-team::%g%::*}:
		set {-tp} to %loop-value%
		fade screen of {-tp} to #FFD700 in 3 stay 4 out 14 ticks
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
