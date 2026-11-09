# ==========================================================
#  hud.sk — свой HUD поверх экрана игры
#  Хотбар, полосы здоровья и сытости, картинки и слои (layer)
#  Картинки лежат в scriptcraft/resources/assets/example/textures/gui/ —
#  сервер сам отправляет их игрокам (как и для open screen).
#  HUD хранится на сервере: после перезахода игрок получит его снова.
#  В новой папке scriptcraft/ пример лежит выключенным (-hud.sk): включить — /sc enable hud
#  "on join or load:" — HUD строится при входе И сразу для всех онлайн при /sc enable hud,
#  /sc reload и старте сервера. /sc disable hud уберёт этот HUD и вернёт ванильные части.
# ==========================================================

on join or load:
    # Прячем ванильные хотбар, сердечки и голод — рисуем свои
    hide vanilla hotbar for player
    hide vanilla health for player
    hide vanilla food for player
    create hud "stats" for player:
        # Хотбар: 9 настоящих слотов игрока, выбранный слот подсвечен цветом color
        hotbar id hb color #FFD700 layer 0
        # Полосы: значение берётся на клиенте каждый кадр (health, food...), пакеты не нужны.
        # Заполнение — картинка (обрезается, а не растягивается), рамка — отдельная картинка слоем выше.
        bar "hp" value health at -51, -28 size 80x8 anchor bottom fill "example:textures/gui/bar_fill_hp.png" background #A0000000 layer 1
        image "example:textures/gui/bar_frame.png" at -51, -26 size 84x12 anchor bottom layer 2
        text "❤ %health%/%max_health%" at -51, -38 anchor bottom color #FF5555 shadow layer 3 id hp_text
        bar "food" value food direction right_to_left at 51, -28 size 80x8 anchor bottom fill "example:textures/gui/bar_fill_food.png" background #A0000000 layer 1
        image "example:textures/gui/bar_frame.png" at 51, -26 size 84x12 anchor bottom layer 2
        text "%food%/20" at 51, -38 anchor bottom color #FFAA00 shadow layer 3
        # Текст прямо на полосе (плейсхолдеры %value%, %max%, %percent%); в обычном тексте — %health%, %food%...
        bar "air" value air direction bottom_to_top at -8, -60 size 6x40 anchor right color #55AAFF text "%percent%%" layer 1

    # Отдельная группа: монеты игрока. Слот может содержать картинку или текст.
    create hud "coins" for player:
        slot image "example:textures/gui/coin.png" at 4, 4 anchor top_left id coin_icon layer 0
        text "{coins::%player%}" at 26, 9 anchor top_left color #FFD700 shadow id coins_text layer 1
        bar "coins_goal" value {coins::%player%} max 100 at 4, 26 size 60x4 anchor top_left color #FFD700 layer 0

# /coinhud — обновить счётчик монет (только изменения уходят клиенту)
command /coinhud:
    set text of hud "coins_text" for player to "{coins::%player%}"
    set value of bar "coins_goal" for player to {coins::%player%}

# /hudoff и /hudon — скрыть/показать группу целиком
command /hudoff:
    hide hud "stats" from player
    show vanilla hotbar for player
    show vanilla health for player
    show vanilla food for player

command /hudon:
    show hud "stats" to player
    hide vanilla hotbar for player
    hide vanilla health for player
    hide vanilla food for player

# /boss — полоса для всех игроков рядом, значение задаёт скрипт
command /boss permission 2:
    create hud "boss" for players in radius 30 of player:
        text "Страж подземелья" at 0, 6 anchor top color #FF5555 shadow layer 1
        bar "boss_hp" value 100 max 100 at 0, 18 size 182x6 anchor top color #AA0000 layer 0
    set progress of bar "boss_hp" for players in radius 30 of player to 75

# /hudreset — убрать весь свой HUD и вернуть ванильный
command /hudreset:
    clear hud for player
    show vanilla all for player
