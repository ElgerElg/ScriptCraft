# ==========================================================
#  welcome.sk — события входа/выхода и простые команды
#  Логика перезагружается командой /sc reload (или /sc reload welcome)
#  Выключить файл: /sc disable welcome (станет -welcome.sk)
# ==========================================================

on join:
    # Постоянная переменная (сохраняется в scriptcraft/variables/variables.json)
    add 1 to {joins::%player%}
    if {joins::%player%} is 1:
        send "&aПривет, &e%player%&a! Ты здесь впервые, добро пожаловать!" to player
    else:
        send "&aС возвращением, &e%player%&a! Это твой {joins::%player%}-й вход." to player
    # Временная переменная (имя с "-"): живёт только до перезапуска сервера
    set {-online_since::%player%} to "сегодня"
    broadcast "&7%player% зашёл на сервер (онлайн: %online%)"

on quit:
    broadcast "&7%player% вышел с сервера"

# Команда для всех игроков
command /hello:
    send "&bHello, %player%! Ты написал: %args%" to player

# Команда только для операторов (permission 2)
command /kit permission 2:
    give player diamond 3
    give player bread 16
    heal player
    send "&6Набор выдан!" to player

# ----------------------------------------------------------
#  Эффекты над игроком (как в Skript). Цель: player, имя игрока ("Steve", {var}, "%args%")
#  или набор игроков (all players, @a, targeted player, players in radius 10 of player) — см. ниже
# ----------------------------------------------------------

command /buff permission 2:
    heal player
    feed player
    extinguish player
    give player effect speed for 60 seconds level 2
    give player effect night_vision for 120 seconds
    play sound entity.player.levelup to player
    send title "&6Бафф!" with subtitle "&eСкорость и ночное зрение" to player
    send action bar "&aЗдоровье и голод восстановлены" to player

command /fly permission 2:
    set flying of player to true
    send action bar "&bПолёт включён" to player

command /nofly permission 2:
    set flying of player to false

command /creative permission 2:
    set player's gamemode to creative

command /survival permission 2:
    set gamemode of player to survival
    clear effects of player

# /healp Steve — вылечить другого игрока (имя из %args%; если его нет в сети — ничего не произойдёт)
command /healp permission 2:
    heal "%args%"
    send action bar "&aТебя вылечили!" to "%args%"
    reply "Вылечен: %args%"

command /xp permission 2:
    give 100 xp to player
    set xp level of player to 30

# Команды: выполняются только если allowCommandEffects = true (по умолчанию).
command /daytime permission 2:
    execute console command "time set day"
    make player execute command "me поставил день"

command /spawnpoint0:
    teleport player to 0, 100, 0
    reply "Телепортирован на 0 100 0"

# ----------------------------------------------------------
#  Цели-наборы: действие выполняется для каждого игрока набора, пустой набор = ничего
# ----------------------------------------------------------

# /healall — вылечить и накормить всех рядом (включая себя)
command /healall permission 2:
    heal players in radius 20 of player
    feed players in radius 20 of player
    send "&a%player% вылечил всех в радиусе 20 блоков" to other players in radius 20 of player
    reply "Готово"

# /look — на кого я смотрю (луч до targetedPlayerRange блоков из конфига)
command /look:
    if targeted player is set:
        send "&eТы смотришь на &6%targeted%" to player
        send action bar "&c%player% смотрит на тебя" to targeted player
    else:
        send "&7Ты ни на кого не смотришь" to player

# /gather — собрать всех к себе и поздороваться с каждым по имени
command /gather permission 2:
    teleport other players in radius 200 of player to player
    loop all players:
        send "&e%loop-player%, сбор у %player%!" to loop-player
    play sound entity.player.levelup to @a

# /nearbuff — ванильный селектор с аргументами
command /nearbuff permission 2:
    give @a[distance=..10] effect regeneration for 10 seconds
    send title "&aРегенерация" to @a[distance=..10]

# ----------------------------------------------------------
#  Переменные и условия: простая "экономика"
# ----------------------------------------------------------

# /coins — показать баланс (незаданная переменная = <none>, поэтому сначала проверяем)
command /coins:
    if {coins::%player%} is not set:
        set {coins::%player%} to 0
    send "&eМонет: &6{coins::%player%}" to player

# /work — заработать 5 монет, не чаще одного раза до перезапуска (временная переменная)
command /work:
    if {-worked::%player%} is set:
        send "&cТы уже работал. Отдохни до перезапуска сервера." to player
    else:
        add 5 to {coins::%player%}
        set {-worked::%player%} to true
        send "&a+5 монет! Теперь: {coins::%player%}" to player

# /buy — купить алмаз за 10 монет
command /buy:
    if {coins::%player%} >= 10:
        remove 10 from {coins::%player%}
        give player diamond 1
        send "&aКуплено! Осталось монет: {coins::%player%}" to player
    else:
        send "&cНужно 10 монет, у тебя: {coins::%player%}" to player

# /resetcoins — только операторы
command /resetcoins permission 2:
    delete {coins::%player%}
    reply "Баланс сброшен"
