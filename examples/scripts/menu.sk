# ==========================================================
#  menu.sk — экран (GUI), который сервер открывает игроку
#  Элементы: text, button, slot, image
#  Позиция: at X Y относительно anchor (по умолчанию center)
# ==========================================================

command /menu:
    open screen "Главное меню" to player:
        # Картинка из scriptcraft/resources/assets/example/textures/gui/ — сервер сам отправит её игрокам,
        # она применяется сразу, без перезагрузки ресурсов (DynamicTexture).
        image "example:textures/gui/menu_banner.png" at 0, -85 size 128x32 texture 128x32
        text "Добро пожаловать, %player%!" at 0, -60 color #FFD700 shadow
        image "minecraft:textures/item/diamond.png" at 0, -40 size 16x16 texture 16x16
        slot diamond 5 at -30, -15
        slot example:ruby 8 at 0, -15
        slot bread 16 at 30, -15
        slot example:ruby_block 4 at 60, -15
        button "Сказать привет" at 0, 15 size 120x20 run "/hello из меню"
        button "Закрыть" at 0, 40 size 120x20 close
        text "ScriptCraft" at -4, -4 anchor bottom_right color #808080

# /announce — открыть экран всем игрокам в радиусе 10 блоков (цель — набор игроков)
command /announce permission 2:
    open screen "Объявление" to players in radius 10 of player:
        text "%player% собирает команду!" at 0, -20 color #FFD700 shadow
        button "Понятно" at 0, 10 size 120x20 close
