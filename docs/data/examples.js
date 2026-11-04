/*
 * ScriptCraft — большие примеры для вкладки «Примеры».
 * Каждый пример: id, title {ru,en}, intro {ru,en}, blocks[] (lang: sk | json | toml | text), notes {ru[], en[]}, related[] (id из syntax.js).
 * Скрипты взяты из examples/scripts репозитория (welcome.sk, menu.sk, ruby.sk, hud.sk) — держите их в согласии.
 */
window.SC_DOCS = window.SC_DOCS || {};
window.SC_DOCS.examples = [
  {
    id: "menu",
    title: { ru: "Меню: экран с кнопками, текстом, картинкой и слотами", en: "Menu: screen with buttons, text, image and slots" },
    intro: {
      ru: "Команда `/menu` открывает игроку экран. Баннер лежит в `scriptcraft/resources/assets/example/textures/gui/` — сервер сам отправит его игрокам, и он покажется без перезагрузки ресурсов. Кнопка с `run` выполняет команду от имени игрока и закрывает экран. Вторая команда открывает экран сразу всем игрокам рядом.",
      en: "`/menu` opens a screen for the player. The banner lives in `scriptcraft/resources/assets/example/textures/gui/` — the server sends it to players itself and it shows without a resource reload. A button with `run` runs the command as the player and closes the screen. The second command opens a screen for every nearby player at once."
    },
    blocks: [
      { lang: "sk", title: { ru: "scriptcraft/scripts/menu.sk", en: "scriptcraft/scripts/menu.sk" }, code: `command /menu:
    open screen "Главное меню" to player:
        # Картинка из scriptcraft/resources/assets/example/textures/gui/
        image "example:textures/gui/menu_banner.png" at 0, -85 size 128x32 texture 128x32
        text "Добро пожаловать, %player%!" at 0, -60 color #FFD700 shadow
        image "minecraft:textures/item/diamond.png" at 0, -40 size 16x16 texture 16x16
        slot diamond 5 at -30, -15
        slot scriptcraft:ruby 8 at 0, -15
        slot bread 16 at 30, -15
        slot example:ruby 4 at 60, -15
        button "Сказать привет" at 0, 15 size 120x20 run "/hello из меню"
        button "Закрыть" at 0, 40 size 120x20 close
        text "ScriptCraft" at -4, -4 anchor bottom_right color #808080

command /hello:
    send "&bHello, %player%! Ты написал: %args%" to player

# Экран сразу всем в радиусе 10 блоков
command /announce permission 2:
    open screen "Объявление" to players in radius 10 of player:
        text "%player% собирает команду!" at 0, -20 color #FFD700 shadow
        button "Понятно" at 0, 10 size 120x20 close` }
    ],
    notes: {
      ru: ["Координаты `at X Y` считаются от точки `anchor` (по умолчанию центр экрана), минус — влево/вверх.",
           "Слоты только показывают предмет с подсказкой, забрать его нельзя.",
           "`%player%` и `{переменные}` подставляются на сервере в момент открытия."],
      en: ["`at X Y` is relative to the `anchor` point (screen centre by default), negative = left/up.",
           "Slots only display the item with a tooltip; it cannot be taken.",
           "`%player%` and `{variables}` are filled in on the server when the screen opens."]
    },
    related: ["eff-open-screen", "screen-text", "screen-button", "screen-slot", "screen-image", "screen-layout"]
  },
  {
    id: "hud",
    title: { ru: "HUD: свой хотбар, полосы HP и голода, картинки и слои", en: "HUD: custom hotbar, HP and hunger bars, images and layers" },
    intro: {
      ru: "При входе (и сразу всем онлайн — при `/sc enable hud`, `/sc reload` и старте сервера, благодаря `on join or load:`) игрок получает свой HUD: хотбар из 9 настоящих слотов, полосы здоровья и сытости с картинками заполнения и рамкой (рамка — отдельная картинка слоем выше), счётчик монет со слотом-картинкой. Ванильные хотбар, сердечки и голод спрятаны. Полосы со значением `health`/`food` клиент обновляет сам каждый кадр — без пакетов. Картинки лежат в `scriptcraft/resources/assets/example/textures/gui/` и отправляются игрокам автоматически.",
      en: "On join (and instantly for everyone online on `/sc enable hud`, `/sc reload` and server start, thanks to `on join or load:`) the player gets a custom HUD: a hotbar of 9 real slots, health and hunger bars with fill images and a frame (the frame is a separate image on a higher layer), and a coin counter with an image slot. The vanilla hotbar, hearts and hunger are hidden. Bars with `health`/`food` values are updated by the client every frame — no packets. Images live in `scriptcraft/resources/assets/example/textures/gui/` and are sent to players automatically."
    },
    blocks: [
      { lang: "sk", title: { ru: "scriptcraft/scripts/hud.sk", en: "scriptcraft/scripts/hud.sk" }, code: `on join or load:
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
    show vanilla all for player` }
    ],
    notes: {
      ru: ["`layer` — порядок отрисовки: больше = выше. Отрицательный слой рисуется под ванильным HUD.",
           "HUD хранится на сервере и возвращается игроку после перезахода; `/sc reload` его не сбрасывает — повторный `create hud` с тем же именем заменяет группу.",
           "`/sc disable hud` убирает все группы, созданные этим скриптом, и возвращает ванильные части, которые он прятал.",
           "Клиенту уходят только изменения: `set value of bar` с тем же значением ничего не отправляет.",
           "`bar`, `hotbar`, `slot image/text` и `layer` работают и в `open screen`."],
      en: ["`layer` is the drawing order: higher = on top. A negative layer is drawn below the vanilla HUD.",
           "The HUD is kept on the server and comes back after re-joining; `/sc reload` does not reset it — running `create hud` with the same name replaces the group.",
           "`/sc disable hud` removes every group created by this script and restores the vanilla parts it hid.",
           "Only changes are sent: `set value of bar` with the same value sends nothing.",
           "`bar`, `hotbar`, `slot image/text` and `layer` also work in `open screen`."]
    },
    related: ["hud-create", "hud-bar", "hud-bar-sources", "hud-hotbar", "hud-slot", "hud-layer", "hud-set-bar", "hud-set-text", "hud-show-hide", "hud-vanilla"]
  },
  {
    id: "economy",
    title: { ru: "Экономика на монетах (переменные и условия)", en: "Coin economy (variables and conditions)" },
    intro: {
      ru: "У каждого игрока свой баланс `{coins::%player%}` — переменная сохраняется в `scriptcraft/variables/variables.json` и переживает перезапуск. `/work` можно использовать раз до перезапуска сервера: это отмечено временной переменной `{-worked::%player%}`.",
      en: "Each player has their own balance `{coins::%player%}` — saved to `scriptcraft/variables/variables.json`, survives restarts. `/work` can be used once per server run: tracked by the temporary variable `{-worked::%player%}`."
    },
    blocks: [
      { lang: "sk", title: { ru: "scriptcraft/scripts/economy.sk", en: "scriptcraft/scripts/economy.sk" }, code: `# /coins — показать баланс (незаданная переменная = <none>, поэтому сначала проверяем)
command /coins:
    if {coins::%player%} is not set:
        set {coins::%player%} to 0
    send "&eМонет: &6{coins::%player%}" to player

# /work — заработать 5 монет, не чаще раза до перезапуска (временная переменная)
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
    reply "Баланс сброшен"` }
    ],
    notes: {
      ru: ["`add`/`remove` считают незаданную переменную равной 0.",
           "Переменные сохраняются раз в `variablesSaveIntervalSeconds` и при остановке сервера; при старте делается резервная копия.",
           "Нет `else if` и `and`/`or` — используйте вложенные `if`."],
      en: ["`add`/`remove` treat an unset variable as 0.",
           "Variables are saved every `variablesSaveIntervalSeconds` and on server stop; a backup is made at start.",
           "No `else if` or `and`/`or` — use nested `if`s."]
    },
    related: ["expr-variable", "expr-temp-variable", "eff-set-var", "eff-add-var", "struct-if", "cond-number"]
  },
  {
    id: "custom-block",
    title: { ru: "Свой блок и предмет в своём namespace + ресурсы", en: "Custom block and item in your own namespace + resources" },
    intro: {
      ru: "Скрипт объявляет `example:ruby_block` и `example:ruby`. Модели, текстуры и названия лежат в `scriptcraft/resources/assets/example/`, дроп и «копается киркой» — в `scriptcraft/resources/data/`. Сервер отправит `assets` игрокам сам, но **файл скрипта с `define` должен быть одинаковым у сервера и у клиентов**, а новые блоки появляются только после перезапуска.",
      en: "The script declares `example:ruby_block` and `example:ruby`. Models, textures and names live in `scriptcraft/resources/assets/example/`, loot and the pickaxe tag in `scriptcraft/resources/data/`. The server sends `assets` to players itself, but **the script file with `define` must be identical on the server and clients**, and new blocks appear only after a restart."
    },
    blocks: [
      { lang: "sk", title: { ru: "scriptcraft/scripts/ruby.sk", en: "scriptcraft/scripts/ruby.sk" }, code: `namespace example

define block ruby_block:
    hardness: 5
    resistance: 6
    sound: metal
    requires_tool: true

define item ruby:
    stack_size: 16
    rarity: epic

command /ruby permission 2:
    give player example:ruby_block 1
    give player example:ruby 4
    send "&cДержи рубины!" to player` },
      { lang: "text", title: { ru: "Раскладка папки resources", en: "resources folder layout" }, code: `scriptcraft/resources/
  assets/example/
    blockstates/ruby_block.json
    models/block/ruby_block.json
    models/item/ruby_block.json
    models/item/ruby.json
    textures/block/ruby_block.png
    textures/item/ruby.png
    textures/gui/menu_banner.png        (картинка для экранов)
    lang/en_us.json
    lang/ru_ru.json
  data/
    example/loot_tables/blocks/ruby_block.json
    minecraft/tags/blocks/mineable/pickaxe.json` },
      { lang: "json", title: { ru: "assets/example/blockstates/ruby_block.json", en: "assets/example/blockstates/ruby_block.json" }, code: `{
  "variants": {
    "": { "model": "example:block/ruby_block" }
  }
}` },
      { lang: "json", title: { ru: "assets/example/lang/ru_ru.json", en: "assets/example/lang/ru_ru.json" }, code: `{
  "block.example.ruby_block": "Рубиновый блок (namespace example)",
  "item.example.ruby": "Рубин (namespace example)"
}` },
      { lang: "json", title: { ru: "data/example/loot_tables/blocks/ruby_block.json", en: "data/example/loot_tables/blocks/ruby_block.json" }, code: `{
  "type": "minecraft:block",
  "pools": [
    {
      "rolls": 1,
      "entries": [ { "type": "minecraft:item", "name": "example:ruby_block" } ],
      "conditions": [ { "condition": "minecraft:survives_explosion" } ]
    }
  ]
}` }
    ],
    notes: {
      ru: ["id и имена файлов — только `a-z 0-9 _ . -`: русское название задайте через `name:` или `lang/ru_ru.json`.",
           "Без модели блок будет фиолетово-чёрным, без loot table из него ничего не выпадет.",
           "Текстуры блоков/предметов у клиента применяются одной перезагрузкой ресурсов при первом входе (и только если что-то изменилось).",
           "После правки `assets` на сервере: `/sc resources reload`; после правки `data`: ванильная `/reload`."],
      en: ["ids and file names: only `a-z 0-9 _ . -`; set a localized name with `name:` or `lang/ru_ru.json`.",
           "Without a model the block is purple-black; without a loot table it drops nothing.",
           "Block/item textures are applied on the client with one resource reload on first join (only when something changed).",
           "After editing `assets` on the server: `/sc resources reload`; after editing `data`: vanilla `/reload`."]
    },
    related: ["struct-namespace", "struct-define-block", "struct-define-item", "cmd-resources-reload", "eff-give-item"]
  },
  {
    id: "heal-radius",
    title: { ru: "Лечение в радиусе: цели и циклы", en: "Heal in radius: targets and loops" },
    intro: {
      ru: "Цель — это набор игроков: действие выполняется для каждого, пустой набор — ничего не происходит. `loop` нужен, когда каждому игроку надо что-то своё (например, его имя в тексте).",
      en: "A target is a set of players: the action runs for each, an empty set does nothing. Use `loop` when each player needs something personal (e.g. their name in a message)."
    },
    blocks: [
      { lang: "sk", title: { ru: "scriptcraft/scripts/targets.sk", en: "scriptcraft/scripts/targets.sk" }, code: `# /healall — вылечить и накормить всех рядом (включая себя)
command /healall permission 2:
    heal players in radius 20 of player
    feed players in radius 20 of player
    send "&a%player% вылечил всех в радиусе 20 блоков" to other players in radius 20 of player
    reply "Готово"

# /look — на кого я смотрю (луч до targetedPlayerRange блоков)
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
    send title "&aРегенерация" to @a[distance=..10]` }
    ],
    notes: {
      ru: ["`players in radius N of player` включает самого игрока, `other players ...` — нет.",
           "Плейсхолдеры в тексте считаются от вызвавшего: для имени получателя используйте `loop` и `%loop-player%`.",
           "Селекторы (`@a[...]`) работают, только если написаны прямо в скрипте; текст из `%args%` всегда считается именем."],
      en: ["`players in radius N of player` includes the player, `other players ...` does not.",
           "Placeholders are evaluated for the caller: use `loop` and `%loop-player%` for the recipient's name.",
           "Selectors (`@a[...]`) only work when written directly in the script; text from `%args%` is always a name."]
    },
    related: ["target-radius", "target-targeted", "target-selector", "struct-loop", "cond-target-set", "ph-targeted"]
  },
  {
    id: "effect-commands",
    title: { ru: "Эффект-команды в чате", en: "Chat effect commands" },
    intro: {
      ru: "Как в Skript: сообщение в чате с токеном `?` выполняется как одна строка скрипта от имени написавшего — удобно для тестов и админов. По умолчанию выключено.",
      en: "Like in Skript: a chat message starting with `?` runs as one script line on behalf of the sender — handy for testing and admins. Off by default."
    },
    blocks: [
      { lang: "toml", title: { ru: "scriptcraft/config.toml", en: "scriptcraft/config.toml" }, code: `[effectCommands]
    enableEffectCommands = true
    effectCommandToken = "?"
    effectCommandsRequireOp = true` },
      { lang: "sk", title: { ru: "В чате игры", en: "In the game chat" }, code: `?heal player
?set player's gamemode to creative
?give player effect night_vision for 600 seconds
?send title "Тест" to "Steve"
?heal players in radius 10 of player
?send "привет" to targeted player
?give @a[distance=..20] diamond 1
?set {coins::Steve} to 100` }
    ],
    notes: {
      ru: ["Право — `scriptcraft.effectcommands` (Forge PermissionAPI, выдаётся через LuckPerms и т.п.). Без мода прав — только операторы, если `effectCommandsRequireOp = true`.",
           "`execute console command` / `make ... execute command` из чата доступны только операторам уровня 2.",
           "Блоки (`if`, `loop`, `open screen`) не поддерживаются; если цель пуста, придёт «Нет целей».",
           "Сообщение с токеном никогда не уходит в общий чат, каждое использование пишется в лог."],
      en: ["Permission `scriptcraft.effectcommands` (Forge PermissionAPI, grant via LuckPerms etc.). Without a permissions mod — operators only when `effectCommandsRequireOp = true`.",
           "`execute console command` / `make ... execute command` from chat are operator-only (level 2).",
           "Blocks (`if`, `loop`, `open screen`) are not supported; an empty target replies “No targets”.",
           "A message with the token never reaches public chat; every use is logged."]
    },
    related: ["cmd-effect-commands", "cfg-enableEffectCommands", "cfg-effectCommandToken", "cfg-effectCommandsRequireOp"]
  },
  {
    id: "join-messages",
    title: { ru: "Приветствие при входе и временные переменные", en: "Join messages and temporary variables" },
    intro: {
      ru: "Считаем входы игрока в постоянной переменной и приветствуем новичков отдельно. Временная переменная `{-online_since::%player%}` живёт только до перезапуска сервера.",
      en: "Count a player's joins in a persistent variable and greet newcomers differently. The temporary variable `{-online_since::%player%}` only lives until the server restarts."
    },
    blocks: [
      { lang: "sk", title: { ru: "scriptcraft/scripts/welcome.sk", en: "scriptcraft/scripts/welcome.sk" }, code: `on join:
    # Постоянная переменная (сохраняется в scriptcraft/variables/variables.json)
    add 1 to {joins::%player%}
    if {joins::%player%} is 1:
        send "&aПривет, &e%player%&a! Ты здесь впервые, добро пожаловать!" to player
        give player bread 16
    else:
        send "&aС возвращением, &e%player%&a! Это твой {joins::%player%}-й вход." to player
    # Временная переменная (имя с "-"): живёт только до перезапуска сервера
    set {-online_since::%player%} to "сегодня"
    broadcast "&7%player% зашёл на сервер (онлайн: %online%)"

on quit:
    broadcast "&7%player% вышел с сервера"` }
    ],
    notes: {
      ru: ["Временные переменные стираются при старте и остановке сервера (`enableTemporaryVariables = true`).",
           "`%online%` — число игроков онлайн, `{joins::%player%}` в тексте заменяется значением."],
      en: ["Temporary variables are wiped on server start and stop (`enableTemporaryVariables = true`).",
           "`%online%` is the online player count; `{joins::%player%}` in text is replaced by its value."]
    },
    related: ["event-join", "event-quit", "expr-temp-variable", "expr-variable", "eff-broadcast"]
  }
];
