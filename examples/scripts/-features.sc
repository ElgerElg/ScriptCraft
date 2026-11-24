# ==========================================================
#  features.sc — первый вход, перехват чата, кубик с задержкой и таймер
#  Копируется выключенным (-features.sc). Включить: /sc enable features
# ==========================================================

on first join:
	give player bread 16
	send "&aДобро пожаловать, %player%!" to player

on chat:
	if %message% is "hp":
		cancel event
		set {hp} to health of player
		set {max} to max health of player
		send "&cЗдоровье: %{hp}% / %{max}%" to player

command /roll:
	set {roll} to random integer between 1 and 6
	if {roll} is 6:
		send "&aШестёрка!" to player
		give 2 levels to player
	else if {roll} >= 4:
		send "&eВыпало %{roll}%" to player
	else:
		send "&7Выпало %{roll}%" to player
	wait 1 second
	send "&7Бросок учтён" to player

every 10 seconds:
	loop all players:
		if health of loop-player < 6 and loop-player is not in creative:
			heal loop-player by 2
