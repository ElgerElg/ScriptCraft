# ==========================================================
#  welcome.sc — вход, эффекты, переменные и команды с аргументами
#  Копируется выключенным (-welcome.sc). Включить: /sc enable welcome
#  Перезагрузка: /sc reload welcome. Выключить: /sc disable welcome
#  Отступ — Tab. Один Tab равен четырём пробелам, пробелы тоже понимаются.
# ==========================================================

on join:
	add 1 to {joins::%player%}
	if {joins::%player%} is 1:
		send "&aПривет, &e%player%&a! Ты здесь впервые." to player
		set {coins::%player%} to 20
		send "&7Стартовые &e20 монет&7. Магазин: &f/menu" to player
	else:
		send "&aС возвращением, &e%player%&a! Вход номер %{joins::%player%}%." to player
	set {-online_since::%player%} to "сегодня"
	broadcast "&7%player% зашёл (онлайн: %online%)"

on quit:
	broadcast "&7%player% вышел"

# Хвост строки без списка аргументов попадает в %args%.
command /bc:
	permission: 2
	aliases: broadcast
	if "%args%" is "":
		send "&cИспользование: /bc <текст объявления>" to player
		stop
	broadcast "&6[Объявление] &f%args%"

# permission, aliases и executable by пишутся ПОД командой, не в заголовке.
command /kit:
	permission: 2
	aliases: starter
	give player diamond 3
	give player bread 16
	heal player
	feed player
	send "&6Стартовый набор выдан." to player

command /buff:
	permission: 2
	aliases: boost
	heal player
	feed player
	extinguish player
	give player effect speed for 60 seconds level 2
	give player effect night_vision for 120 seconds
	play sound entity.player.levelup to player
	send title "&6Бафф" with subtitle "&eСкорость и ночное зрение" to player
	send action bar "&aЗдоровье и голод восстановлены" to player

# Переключатель полёта для себя или указанного игрока.
command /fly [player]:
	permission: 2
	if arg-1 is not set:
		if player is flying:
			set flying of player to false
			send action bar "&7Полёт выключен" to player
		else:
			set flying of player to true
			send action bar "&bПолёт включён" to player
	else:
		if arg-1 is flying:
			set flying of arg-1 to false
			send action bar "&7Полёт выключен" to arg-1
			reply "&7Полёт выключен для &f%arg-1%"
		else:
			set flying of arg-1 to true
			send action bar "&bПолёт включён" to arg-1
			reply "&bПолёт включён для &f%arg-1%"

# Смена режима игры: /gm <0|1|2|3> [игрок] (или s/c/a/sp).
function SetPlayerGm(target: player, mode: text):
	if mode is "0" or "s" or "survival":
		set gamemode of target to survival
		send action bar "&aРежим выживания" to target
	else if mode is "1" or "c" or "creative":
		set gamemode of target to creative
		send action bar "&bТворческий режим" to target
	else if mode is "2" or "a" or "adventure":
		set gamemode of target to adventure
		send action bar "&eРежим приключения" to target
	else if mode is "3" or "sp" or "spectator":
		set gamemode of target to spectator
		send action bar "&7Режим наблюдателя" to target

command /gm <text> [player]:
	permission: 2
	aliases: gamemode
	if arg-1 is not "0" and arg-1 is not "1" and arg-1 is not "2" and arg-1 is not "3" and arg-1 is not "s" and arg-1 is not "c" and arg-1 is not "a" and arg-1 is not "sp" and arg-1 is not "survival" and arg-1 is not "creative" and arg-1 is not "adventure" and arg-1 is not "spectator":
		send "&cИспользование: /gm <0|1|2|3> [игрок]" to player
		stop
	if arg-2 is not set:
		SetPlayerGm(player, arg-1)
	else:
		SetPlayerGm(arg-2, arg-1)
		reply "&aРежим игры для &f%arg-2% &aизменён."

# Оба аргумента необязательны: /heal, /heal Steve, /heal Steve 10.
command /heal [player] [integer]:
	permission: 2
	aliases: h
	executable by: players and console
	if arg-1 is not set:
		heal player
		feed player
		send action bar "&aЗдоровье восстановлено" to player
	else if arg-2 is not set:
		heal arg-1
		send action bar "&aТебя вылечил %player%" to arg-1
		reply "&aВылечен %arg-1%"
	else:
		heal arg-1 by arg-2
		send action bar "&aТебя лечит %player% (+%arg-2%)" to arg-1
		reply "&aВылечен %arg-1% на %arg-2%"

# Точка спавна и личный дом хранятся как полноценная локация (мир + координаты + поворот).
command /setspawn:
	permission: 2
	set {spawn} to location of player
	send "&aТочка спавна сохранена." to player

command /spawn:
	aliases: hub
	if {spawn} is not set:
		send "&cТочка спавна ещё не задана (/setspawn)." to player
	else:
		teleport player to {spawn}
		send action bar "&aТелепортация на спавн" to player

command /sethome:
	aliases: sh
	set {home::%player%} to location of player
	send "&aДом сохранён!" to player

command /home:
	aliases: base
	if {home::%player%} is not set:
		send "&cСначала установите дом: /sethome" to player
	else:
		teleport player to {home::%player%}
		send "&aТы дома." to player

command /look:
	if targeted player is set:
		send "&eТы смотришь на &6%targeted%" to player
		send action bar "&c%player% смотрит на тебя" to targeted player
	else:
		send "&7Перед тобой никого нет" to player

command /gather:
	permission: 2
	teleport other players in radius 80 of player to player
	loop all players:
		send "&e%loop-player%, сбор у %player%" to loop-player
	play sound entity.player.levelup to @a

command /nearbuff:
	permission: 2
	give @a[distance=..10] effect regeneration for 10 seconds
	send title "&aРегенерация" to @a[distance=..10]

command /coins:
	aliases: bal, balance
	if {coins::%player%} is not set:
		set {coins::%player%} to 0
	send "&eМонет: &6%{coins::%player%}%" to player

# chance of — случайная ветка. {-worked} живёт до перезапуска сервера.
command /work:
	aliases: job
	if {-worked::%player%} is set:
		send "&cТы уже работал. Отдых до перезапуска сервера." to player
	else:
		add 5 to {coins::%player%}
		set {-worked::%player%} to true
		chance of 25%:
			add 5 to {coins::%player%}
			send "&dПремия! Ещё +5." to player
		send "&a+5 монет. Сейчас: &6%{coins::%player%}%" to player

command /pay <player> <integer>:
	aliases: transfer
	if {coins::%player%} < arg-2:
		send "&cНе хватает монет. Баланс: %{coins::%player%}%" to player
	else if arg-2 < 1:
		send "&cСумма должна быть больше нуля." to player
	else:
		remove arg-2 from {coins::%player%}
		add arg-2 to {coins::%arg-1%}
		send "&aПереведено &6%arg-2% &aмонет игроку &f%arg-1%" to player
		send "&a%player% перевёл тебе &6%arg-2% &aмонет" to arg-1
