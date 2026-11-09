# ==========================================================
#  bag.sc — получение предмета, клавиша, отмена меню, список и хитбокс
#  Файл копируется ВЫКЛЮЧЕННЫМ (-bag.sc): он отменяет ванильный инвентарь на E.
#  Включить: /sc enable bag     Выключить: /sc disable bag
#  Отступ — Tab.
# ==========================================================

# Любой рост числа предмета в инвентаре: give, сундук, крафт, подбор, креатив.
# %item% — id, %amount% — на сколько стало больше. Перекладывание слотов событие не поднимает.
# cancel event забирает эту добавку обратно. on pickup при этом тоже может сработать (ещё до инвентаря).
on receive:
	send "&7Получено: &f%item% &7x%amount%" to player

# Клавиша только наблюдается. Она не проглатывается: E так не отменить, для этого on open.
# Имена — латиница, физическая клавиша (на русской раскладке G всё ещё g).
on key g:
	send "&eНажата G" to player

# survival и creative — оба меню называются inventory.
# cancel event в этом теле блокирует экран всегда, даже если он стоит внутри if:
# клиент не ждёт ответ сервера и закрывает меню сразу.
on open inventory:
	cancel event
	open screen "Сумка" to player:
		list vertical at -8, 0 size 140x160 anchor right header "&6Сумка" select:
			row "&bАлмаз" run "/hand diamond"
			row "&cРубин" run "/hand ruby"
			row "&7Закрыть" close
		image "minecraft:textures/item/diamond.png" at -170, -24 size 32x32 texture 16x16 anchor right run "/hand diamond"
		text "Взять алмаз" at -170, 16 anchor right color #FFFFFF run "/hand diamond"
		button "Закрыть" at 0, 80 size 80x20 close

# give player arg-1 не подставит текст аргумента: give забирает токен как id.
command /hand <text>:
	permission: 0
	if arg-1 is "diamond":
		give player diamond 1
	else if arg-1 is "ruby":
		give player example:ruby 1
	else:
		send "&cНет такого предмета: &f%arg-1%" to player
