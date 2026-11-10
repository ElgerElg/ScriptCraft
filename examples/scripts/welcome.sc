# ==========================================================
#  welcome.sc — вход, эффекты, переменные и команды с аргументами
#  Копируется выключенным. Включить: /sc enable welcome
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
		send "&aС возвращением, &e%player%&a! Вход номер {joins::%player%}." to player
	set {-online_since::%player%} to "сегодня"
	broadcast "&7%player% зашёл (онлайн: %online%)"

on quit:
	broadcast "&7%player% вышел"

# Хвост строки без списка аргументов — это %args%.
command /hello:
	aliases: hi
	send "&bПривет, %player%! Ты написал: &f%args%" to player
	send "&8Скрипт: %script%" to player

# permission, aliases и executable by пишутся ПОД командой, не в заголовке.
command /kit:
	permission: 2
	aliases: starter
	give player diamond 3
	give player bread 16
	heal player
	feed player
	send "&6Набор выдан." to player

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

command /fly:
	permission: 2
	set flying of player to true
	send action bar "&bПолёт включён" to player

command /nofly:
	permission: 2
	aliases: walk
	set flying of player to false
	send action bar "&7Полёт выключен" to player

command /creative:
	permission: 2
	aliases: gmc
	set player's gamemode to creative

command /survival:
	permission: 2
	aliases: gms
	set gamemode of player to survival
	clear effects of player

# <player> обязателен, [integer] можно не писать. arg-1 — имя, arg-2 — число или <none>.
command /heal <player> [integer]:
	permission: 2
	aliases: h
	executable by: players and console
	if arg-2 is not set:
		heal arg-1
	else:
		heal arg-1 by arg-2
	send action bar "&aТебя лечит %player%" to arg-1
	reply "&aВылечен %arg-1%"

command /spawn:
	permission: 2
	aliases: hub
	teleport player to 0, 100, 0
	reply "Точка 0 100 0"

# Дом хранится в переменных. x/y/z of player — координаты.
command /sethome:
	aliases: sh
	set {home_x::%player%} to x of player
	set {home_y::%player%} to y of player
	set {home_z::%player%} to z of player
	set {home_world::%player%} to world of player
	send "&aДом сохранён: &f{home_x::%player%} {home_y::%player%} {home_z::%player%}" to player

command /home:
	aliases: base
	if {home_x::%player%} is not set:
		send "&cСначала /sethome" to player
	else:
		teleport player to {home_x::%player%}, {home_y::%player%}, {home_z::%player%}
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
	send "&eМонет: &6{coins::%player%}" to player

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
		send "&a+5 монет. Сейчас: &6{coins::%player%}" to player

command /pay <player> <integer>:
	aliases: transfer
	if {coins::%player%} < arg-2:
		send "&cНе хватает монет. Баланс: {coins::%player%}" to player
	else:
		if arg-2 < 1:
			send "&cСумма должна быть больше нуля." to player
		else:
			remove arg-2 from {coins::%player%}
			add arg-2 to {coins::%arg-1%}
			send "&aПереведено &6%arg-2% &aмонет игроку &f%arg-1%" to player
			send "&a%player% перевёл тебе &6%arg-2% &aмонет" to arg-1

command /resetcoins:
	permission: 2
	delete {coins::%player%}
	reply "Баланс сброшен"
