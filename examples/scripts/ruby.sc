# ==========================================================
#  ruby.sc — свои блоки и предметы
#  id только латиницей. Русское название — через name: или lang.
#  Ресурсы: scriptcraft/resources/assets/example/  (модели, текстуры, lang)
#  Дроп и теги: scriptcraft/resources/data/
#  Новый define виден сразу после /sc reload (временный слот).
#  Настоящий id example:ruby_block — после перезапуска сервера и клиента.
#  Технический id в F3 смотри в /sc ids. Выдать: /sc give или /ruby.
#
#  define block <id>:          все строки ниже необязательны
#    name: "текст"
#    hardness: 1.5
#    resistance: 6
#    sound: stone | wood | metal | glass | grass | sand | wool | gravel
#    light: 0..15
#    requires_tool: true | false
#    facing: none | horizontal | all
#        horizontal — 4 стороны, как печь; all — ещё вверх и вниз, как раздатчик
#        модель должна смотреть на север, поворот дописывает сам мод
#    render: solid | cutout | translucent
#        solid — обычный куб; cutout (или transparent) — дырки в png
#        translucent (или glass) — полупрозрачность
#    full: true | false
#        false — модель не на весь куб, пол под блоком остаётся виден
#
#  define item <id>:
#    name: "текст"
#    stack_size: 1..64
#    rarity: common | uncommon | rare | epic
#    fire_resistant: true | false
# ==========================================================

namespace example

define block ruby_block:
	name: "Рубиновый блок"
	hardness: 5
	resistance: 6
	sound: metal
	light: 7
	requires_tool: true
	facing: horizontal
	render: solid
	full: true

# Фонарь нарочно закомментирован: для него нет своей png в examples/resources.
# Сними решётки и положи текстуру assets/example/textures/block/ruby_lantern.png,
# модель может быть не на весь куб (full: false, render: cutout).
# define block ruby_lantern:
# 	name: "Рубиновый фонарь"
# 	sound: glass
# 	light: 12
# 	facing: all
# 	render: cutout
# 	full: false

define item ruby:
	name: "Рубин"
	stack_size: 64
	rarity: rare
	fire_resistant: false

# define item ruby_shard:
# 	name: "Осколок рубина"
# 	stack_size: 16
# 	rarity: uncommon

on place:
	if "%block%" is "example:ruby_block":
		send "&cРубиновый блок поставлен. Скрипт: %script%" to player

command /ruby:
	permission: 2
	aliases: gems
	give player example:ruby_block 1
	give player example:ruby 8
	send "&cДержи рубины." to player
