# ==========================================================
#  menu.sc — экран-лавка. Копируется выключенным: /sc enable menu. Открыть: /menu
#  Элементы экрана, все необязательные хвосты можно комбинировать:
#    text "..."   at X Y   anchor ...   color #RRGGBB   shadow   layer N
#    button "..." at X Y   size WxH     anchor ...      run "/команда"  или  close   layer N
#    slot предмет [кол-во] at X Y  anchor ...  size WxH  layer N
#    slot hotbar N | slot image "ns:textures/....png" | slot text "..."
#    image "ns:textures/....png" at X Y  size WxH  texture WxH  anchor ...  color #RRGGBB  layer N
#    bar "имя" value ... max ... at X Y size WxH anchor ... direction ... fill "..." background ... text "..." layer N
#    hotbar id имя color #RRGGBB layer N
#  anchor: top_left, top, top_right, left, center, right, bottom_left, bottom, bottom_right
#  layer: больше = выше. at X Y считается от anchor, минус — влево или вверх.
#  Картинки из scriptcraft/resources/assets/<ns>/textures/gui/ сервер шлёт сам.
#  Монеты — переменная {coins::%player%} из welcome.sc.
# ==========================================================

command /menu:
	aliases: shop, lavka
	if {coins::%player%} is not set:
		set {coins::%player%} to 0
	open screen "Лавка" to player:
		image "example:textures/gui/menu_banner.png" at 0, -92 size 160x32 texture 128x32 layer 0
		text "&e%player%" at 0, -68 color #FFD700 shadow layer 2
		text "Монеты: {coins::%player%}" at 0, -54 color #FFFFFF shadow layer 2 id coins_label
		bar "purse" value {coins::%player%} max 100 at 0, -40 size 120x6 color #FFD700 background #40000000 layer 1
		slot example:ruby 1 at -60, -12 layer 1
		text "8" at -60, 6 color #FFD700 layer 2
		slot diamond 1 at -20, -12 layer 1
		text "15" at -20, 6 color #55FFFF layer 2
		slot bread 8 at 20, -12 layer 1
		text "3" at 20, 6 color #FFAA00 layer 2
		slot example:ruby_block 1 at 60, -12 layer 1
		text "20" at 60, 6 color #FF5555 layer 2
		button "Рубин" at -60, 28 size 52x20 run "/order ruby" layer 3
		button "Алмаз" at -20, 28 size 52x20 run "/order diamond" layer 3
		button "Хлеб" at 20, 28 size 52x20 run "/order bread" layer 3
		button "Блок" at 60, 28 size 52x20 run "/order block" layer 3
		button "Подлечиться (10)" at 0, 54 size 140x20 run "/order heal" layer 3
		button "Закрыть" at 0, 78 size 100x20 close layer 3
		text "%script%" at -4, -4 anchor bottom_right color #808080 layer 4

# arg-1 — одно слово: ruby, diamond, bread, block, heal.
command /order <text>:
	aliases: buy
	if {coins::%player%} is not set:
		set {coins::%player%} to 0
	if arg-1 is "ruby":
		if {coins::%player%} < 8:
			send "&cНужно 8 монет, у тебя {coins::%player%}" to player
		else:
			remove 8 from {coins::%player%}
			give player example:ruby 1
			send "&aКуплен рубин. Осталось: {coins::%player%}" to player
	else:
		if arg-1 is "diamond":
			if {coins::%player%} < 15:
				send "&cНужно 15 монет." to player
			else:
				remove 15 from {coins::%player%}
				give player diamond 1
				send "&bКуплен алмаз. Осталось: {coins::%player%}" to player
		else:
			if arg-1 is "bread":
				if {coins::%player%} < 3:
					send "&cНужно 3 монеты." to player
				else:
					remove 3 from {coins::%player%}
					give player bread 8
					send "&6Хлеб выдан. Осталось: {coins::%player%}" to player
			else:
				if arg-1 is "block":
					if {coins::%player%} < 20:
						send "&cНужно 20 монет." to player
					else:
						remove 20 from {coins::%player%}
						give player example:ruby_block 1
						send "&cБлок выдан. Осталось: {coins::%player%}" to player
				else:
					if arg-1 is "heal":
						if {coins::%player%} < 10:
							send "&cЛечение стоит 10 монет." to player
						else:
							remove 10 from {coins::%player%}
							heal player
							feed player
							send "&aТы в порядке. Осталось: {coins::%player%}" to player
					else:
						send "&7Неизвестный товар: %arg-1%. Лавка: /menu" to player

command /announce:
	permission: 2
	aliases: shout
	open screen "Объявление" to players in radius 16 of player:
		text "%player% зовёт всех рядом" at 0, -16 color #FFD700 shadow
		button "Понятно" at 0, 12 size 120x20 close
