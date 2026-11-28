# ==========================================================
#  pm.sc — Простой и удобный GUI-мессенджер личных сообщений
#          с прокручиваемым списком игроков и списком чата со скроллбаром
#  Команды:
#    /pm [<игрок>]  (или /msg, /chatgui) — открыть окно ЛС
#    /r             — быстро открыть диалог с последним написавшим
# ==========================================================

command /pm [<player>]:
	aliases: msg, chatgui
	if arg-1 is set:
		set {-pm-target::%player%} to "%arg-1%"
	PmOpen(player)

command /r:
	aliases: pmreply
	if {-pm-last::%player%} is set:
		set {-pm-target::%player%} to {-pm-last::%player%}
	PmOpen(player)

function PmOpen(p: player):
	if {-pm-target::%p%} is not set:
		set {-pm-target::%p%} to ""

	# Собираем список онлайн-игроков для выбора слева
	clear {-pm-online::*}
	loop all players:
		if "%loop-player%" is not "%p%":
			set {-pm-online::%loop-player%} to "онлайн"

	set {-to} to {-pm-target::%p%}
	PmPrepareView(p)

	open screen "Личные сообщения" to p:
		# Список игроков слева (прокручивается колёсиком и ползунком справа)
		list from {-pm-online::*} select header "&eИгроки онлайн" at -114 -4 size 96x152 anchor center id online_list:
			row "&f%loop-index%" run PmSelect(player, "%loop-index%") keep open

		# Шапка: кому пишем + кнопка очистки
		text "&6Кому:" at -46 -72 anchor center shadow
		input "to" at 22 -76 size 102x16 anchor center value "%{-to}%" placeholder "Ник игрока..." max 16 tooltip "&7Выберите игрока слева или введите ник"
		button "Очистить" at 106 -76 size 54x16 anchor center run PmClearHistory(player) keep open tooltip "&cОчистить историю переписки"

		# Прокручиваемый список сообщений со слайдером-скроллбаром
		list from {-pm-view::%p%::*} header "&6Диалог: &f%{-to}%" at 38 -8 size 188x112 anchor center id chat_list:
			row "%loop-value%"

		# Поле ввода сообщения и кнопка отправки
		input "msg" at 7 64 size 124x18 anchor center placeholder "Введите сообщение..." max 120 tooltip "&eВведите текст сообщения"
		button "&aОтправить" at 102 64 size 60x18 anchor center run PmSend(player) keep open tooltip "&aОтправить личное сообщение"

function PmPrepareView(p: player):
	clear {-pm-view::%p%::*}
	set {-peer} to {-pm-target::%p%}
	if {-peer} is not set or {-peer} is empty:
		add "&7⬅ Выберите игрока из списка слева" to {-pm-view::%p%::*}
		stop
	if {-pm-hist::%p%::%{-peer}%::*} is empty:
		add "&8История с %{-peer}% пока пуста. Напишите первым!" to {-pm-view::%p%::*}
		stop
	loop {-pm-hist::%p%::%{-peer}%::*}:
		add "%loop-value%" to {-pm-view::%p%::*}

function PmRefreshList(p: player):
	if p does not have screen "Личные сообщения" open:
		stop
	set {-to} to {-pm-target::%p%}
	PmPrepareView(p)
	update screen "Личные сообщения" for p:
		list from {-pm-view::%p%::*} header "&6Диалог: &f%{-to}%" at 38 -8 size 188x112 anchor center id chat_list:
			row "%loop-value%"

function PmSelect(p: player, targetName: text):
	set {-pm-target::%p%} to targetName
	set value of screen element "to" for p to targetName
	play sound ui.button.click to p with volume 0.4 pitch 1.3
	PmRefreshList(p)

function PmSend(p: player):
	set {-to} to value of screen element "to" of p
	if {-to} is not set or {-to} is empty:
		send action bar "&cСначала выберите игрока слева или введите ник!" to p
		play sound entity.villager.no to p with volume 0.5 pitch 1.0
		stop

	set {-text} to value of screen element "msg" of p
	if {-text} is not set or {-text} is empty:
		stop

	set {-found} to 0
	loop all players:
		if "%loop-player%" is "%{-to}%":
			set {-found} to 1
			PmDeliver(p, loop-player, {-text})

	if {-found} is 0:
		send action bar "&cИгрок %{-to}% сейчас не в сети!" to p
		play sound entity.villager.no to p with volume 0.5 pitch 1.0

function PmDeliver(sender: player, receiver: player, msgText: text):
	set {-pm-target::%sender%} to "%receiver%"
	set {-pm-last::%receiver%} to "%sender%"

	# Добавляем сообщение в списки истории обоих игроков
	add "&aВы: &f%msgText%" to {-pm-hist::%sender%::%receiver%::*}
	add "&e%sender%: &f%msgText%" to {-pm-hist::%receiver%::%sender%::*}

	# Очищаем поле ввода и обновляем список чата на лету
	set value of screen element "msg" for sender to ""
	PmRefreshList(sender)
	play sound entity.experience_orb.pickup to sender with volume 0.4 pitch 1.5

	if receiver has screen "Личные сообщения" open:
		if {-pm-target::%receiver%} is not set or {-pm-target::%receiver%} is empty:
			set {-pm-target::%receiver%} to "%sender%"
			set value of screen element "to" for receiver to "%sender%"
		PmRefreshList(receiver)
	send "&6[ЛС от &e%sender%&6] &f%msgText% &8(/r — открыть GUI)" to receiver
	play sound block.note_block.bell to receiver with volume 0.7 pitch 1.3

function PmClearHistory(p: player):
	set {-peer} to value of screen element "to" of p
	if {-peer} is not set or {-peer} is empty:
		stop
	clear {-pm-hist::%p%::%{-peer}%::*}
	PmRefreshList(p)
	play sound ui.button.click to p with volume 0.4 pitch 0.9
