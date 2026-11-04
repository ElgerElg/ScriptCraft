# ==========================================================
#  ruby.sk — блоки и предметы, объявленные скриптом
#  Новые и изменённые блоки/предметы появляются после /sc reload — без перезапуска.
#  Клиентам этот файл не нужен: сервер сам рассылает таблицу блоков, текстуры и модели.
#  В F3 блок виден как scriptcraft:dyn_block_N (/sc ids); выдать по id скрипта — /sc give.
# ==========================================================

# --- свой namespace: всё, что ниже, получит id example:...
#     В самом моде (namespace scriptcraft) нет ни блоков, ни предметов, ни текстур — весь контент живёт здесь.
#     Ресурсы: scriptcraft/resources/assets/example/... (модели, текстуры, lang),
#     лут и теги: scriptcraft/resources/data/example/... и data/minecraft/tags/...
#     id только латиницей: example:ruby_block (а не "пример:рубиновый_блок").
#     Названия берутся из lang-файлов (assets/example/lang/ru_ru.json), но можно задать и name:.

namespace example

define block ruby_block:
    hardness: 5
    resistance: 6
    sound: metal
    light: 7
    requires_tool: true

define item ruby:
    stack_size: 64
    rarity: rare

# Полный id можно указать и явно, независимо от строки namespace:
# define item example:ruby_shard:
#     name: "Осколок рубина"

command /ruby permission 2:
    give player example:ruby_block 1
    give player example:ruby 8
    send "&cДержи рубины!" to player
