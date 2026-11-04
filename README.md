# ScriptCraft

Мод для **Minecraft Forge 1.20.1** (Forge 47.4.9). С ним можно писать простые Skript-подобные скрипты прямо в моде:
события, команды, сообщения, выдача предметов, свои **блоки и предметы** и **экраны (GUI)**, без отдельного плагина и гибридного ядра.

> Статус: **v0.1 (в разработке)**. Архитектура уже рабочая, но проект ещё ни разу не собирался.
> Первая сборка (`gradlew.bat build`) может показать мелкие ошибки компиляции, их нужно будет поправить.

---

## 1. Установка JDK 17 (Windows)

Forge 1.20.1 требует **Java 17**: не 8, не 21.

1. Скачай **Eclipse Temurin 17 (LTS)**, вариант JDK `.msi` для Windows x64: https://adoptium.net/temurin/releases/?version=17
2. В установщике включи пункты **Set JAVA_HOME variable** и **Add to PATH**.
3. Если JAVA_HOME не выставился, задай его вручную: *Пуск → «Изменение системных переменных среды» → Переменные среды*.
   - Создай переменную `JAVA_HOME` = `C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot` (вставь свою версию).
   - Добавь в `Path` строку `%JAVA_HOME%\bin`.
4. Открой **новое** окно `cmd` и проверь:
   ```bat
   java -version
   echo %JAVA_HOME%
   ```
   В выводе должно быть `openjdk version "17..."`.

Можно сделать и через winget: `winget install EclipseAdoptium.Temurin.17.JDK`.

## 2. Сборка и запуск

Открой `cmd` в папке проекта (`D:\My\projects\ScriptCraft`):

```bat
gradlew.bat build        :: собрать мод
gradlew.bat runClient    :: запустить клиент Minecraft с модом
gradlew.bat runServer    :: запустить выделенный сервер с модом
```

- Первый запуск идёт долго (10–30 минут): Gradle скачивает Minecraft и Forge и декомпилирует их.
- Готовый jar появится здесь: **`build\libs\scriptcraft-0.1.0.jar`**. Его кладут в папку `mods` сервера и клиента.
- `runClient` и `runServer` работают в папке `run\`, поэтому при разработке всё лежит в `run\scriptcraft\`.
- Перед первым `runServer` открой `run\eula.txt` и поставь `eula=true`.
- Для IntelliJ IDEA: *File → Open →* `build.gradle`, *Open as Project*. После импорта выполни `gradlew.bat genIntellijRuns`.

## 3. Папка scriptcraft/

Всё, что относится к моду, лежит в **одной папке `scriptcraft/`** в папке игры или сервера
(рядом с `mods`, на сервере — рядом с `server.properties`):

```
scriptcraft/
  config.toml              настройки мода (см. «Конфиг»)
  scripts/                 скрипты *.sk (UTF-8, подпапки читаются; выключенные — "-имя.sk")
  variables/
    variables.json         все постоянные переменные (один общий файл)
    variables-20261104-153000.json   резервные копии (последние variableBackups штук)
  resources/
    assets/<namespace>/... текстуры, модели, blockstates, lang — сервер сам отправит их игрокам
    data/<namespace>/...   лут-таблицы, теги, рецепты — работают только на сервере
  cache/assets/...         (клиент) ресурсы, полученные с серверов
```

- При первом запуске мод создаёт эти папки и копирует примеры из [`examples/scripts`](examples/scripts)
  и [`examples/resources`](examples/resources) (если папок `scripts/` или `resources/` ещё нет).
- **Переезд со старой версии:** если рядом лежит старая папка `scripts/` (от предыдущих версий мода), а `scriptcraft/scripts/` ещё нет,
  мод при первом запуске **переносит** её в `scriptcraft/scripts/` (если перенести нельзя, например файл занят, — копирует,
  старую папку тогда можно удалить вручную). Старый файл переменных `<мир>/scriptcraft/variables.json`
  копируется в `scriptcraft/variables/variables.json`, если общего файла ещё нет.

### Команды мода

Корневая команда **`/scriptcraft`**, у неё есть полноценные синонимы **`/sc`** и **`/script`**: все три работают одинаково.
Нужен уровень оператора 2. Имена скриптов подсказываются по Tab, расширение `.sk` писать не обязательно.

| Команда | Что делает |
|---|---|
| `/sc` или `/sc help` | справка |
| `/sc reload` | перезагрузить **все** скрипты (логику: события, команды, сообщения, экраны) |
| `/sc reload welcome` | перезагрузить только `welcome.sk` (подпапки так: `/sc reload sub/name`) |
| `/sc disable welcome` | выключить скрипт: выгружает его события и команды и переименовывает файл в `-welcome.sk` |
| `/sc enable welcome` | включить: переименовывает `-welcome.sk` обратно в `welcome.sk` и загружает его |
| `/sc list` | загруженные и выключенные скрипты, команды, число переменных и ошибок |
| `/sc resources reload` | перечитать `scriptcraft/resources/assets` и разослать изменения всем игрокам онлайн (они скачают только изменённые файлы). `data/` перечитывает ванильная `/reload` |

**Выключенные скрипты** (как в Skript): любой файл, имя которого начинается с `-`, не загружается ни при старте,
ни при `reload`, ни при предварительном чтении блоков. Выключить скрипт можно и вручную: просто переименуй файл.
Ограничения Brigadier:
- команды выключенного скрипта остаются в списке команд до перезапуска сервера и при вызове отвечают «выключена»;
- `define block/item` выключенного или включённого скрипта меняются только после перезапуска.

### Конфиг

Файл **`scriptcraft/config.toml`** создаётся при первом запуске.

> Как это сделано: Forge 1.20.1 разрешает имя файла конфига относительно папки `config/`, поэтому мод регистрирует
> его как `../scriptcraft/config.toml` (получается `config/../scriptcraft/config.toml`). Исходников загрузчика конфигов
> (fmlcore) под рукой не было, поэтому это **не проверено на живой сборке** — проверьте при первом запуске. Если Forge откажется
> (ошибка в логе при запуске про config), верните в `ScriptCraft.java` вызов `registerConfig(COMMON, SPEC)` без имени —
> тогда конфиг будет в стандартном месте `config/scriptcraft-common.toml` с теми же параметрами.

| Раздел / параметр | По умолчанию | Описание |
|---|---|---|
| `general.language` | `"en"` | язык сообщений мода: `"en"` или `"ru"` |
| `general.enableTemporaryVariables` | `true` | переменные `{-имя}` живут только в памяти и очищаются при старте и остановке сервера. При `false` они сохраняются, как обычные |
| `general.variablesSaveIntervalSeconds` | `300` | как часто автосохранять переменные (10–86400 с) |
| `general.variableBackups` | `5` | сколько резервных копий `variables-ГГГГММДД-ЧЧММСС.json` хранить (0 — не делать). Копия делается при каждом старте сервера |
| `general.maxScriptSteps` | `10000` | максимум действий за один запуск скрипта |
| `general.maxScriptMillis` | `50` | максимум миллисекунд на один запуск скрипта |
| `general.logScriptLoadTimes` | `false` | писать в лог время компиляции скриптов |
| `general.allowCommandEffects` | `true` | разрешить эффекты `execute console command` и `make player execute command` |
| `general.targetedPlayerRange` | `50` | как далеко ищется `targeted player` / `%targeted%` (1–256 блоков, сквозь блоки луч не проходит) |
| `resources.syncResourcesToClients` | `true` | отправлять `scriptcraft/resources/assets` игрокам через сетевой канал мода |
| `resources.maxResourceSizeMb` | `32` | максимальный общий размер ресурсов (1–512 МБ). Проверяет и сервер (не раздаёт больше), и клиент (не качает больше) |
| `resources.resourceChunkSizeKb` | `256` | размер одного сетевого куска (16–900 КБ; лимит пакета Minecraft — 1 МБ) |
| `effectCommands.enableEffectCommands` | `false` | включить эффект-команды в чате (`?heal player`) |
| `effectCommands.effectCommandToken` | `"?"` | с чего начинается эффект-команда (не `/`, до 8 символов) |
| `effectCommands.effectCommandsRequireOp` | `true` | по умолчанию эффект-команды только у операторов (уровень 2) |

Конфиг читается после загрузки мода, поэтому во время самого первого чтения скриптов при запуске
действуют значения по умолчанию. На работу в игре это не влияет: команды и скрипты читают уже загруженный конфиг.

### ⚠ Блоки и предметы требуют перезапуска

Реестры Minecraft «замораживаются» после загрузки. Поэтому мод **заранее** читает скрипты при старте (в конструкторе мода)
и регистрирует найденные `define block` и `define item`.

- Новый или изменённый `define block/item` начнёт работать только **после перезапуска** игры или сервера. При `reload` мод об этом предупредит.
- Файлы с `define` должны быть **одинаковыми на сервере и у всех клиентов**: положи `scriptcraft/scripts/` в сборку модпака.
  Если наборы блоков различаются, клиент не сможет зайти (ошибка несовпадения реестров).
  Текстуры и модели клиентам ставить вручную **не нужно** (их отправляет сервер, см. раздел 6), а вот реестры
  отправить нельзя: Forge регистрирует блоки при запуске игры, до подключения к серверу.

## 4. Синтаксис скриптов

Блоки верхнего уровня заканчиваются двоеточием, действия внутри пишутся с отступом (пробелы или Tab).
Строка, которая начинается с `#`, считается комментарием. Ошибка в строке не ломает весь файл:
строка пропускается, а ошибка показывается в логе и при `reload` в формате `файл:строка: текст`.

### События

| Скрипт      | Когда срабатывает |
|-------------|-------------------|
| `on join:`  | игрок зашёл       |
| `on quit:`  | игрок вышел       |

### Команды

```
command /hello:                  # для всех
command /kit permission 2:       # только операторы (0–4)
```
Всё, что игрок написал после команды, доступно как `%args%`.

### Действия (белый список)

| Действие | Пример |
|---|---|
| сообщение игрокам | `send "Привет, %player%!" to player` / `send "Внимание" to all players` / `message "..." to @a` |
| сообщение всем | `broadcast "Сервер перезапустится"` |
| ответ вызвавшему (работает и из консоли) | `reply "Готово"` |
| выдать предмет | `give player diamond 3` / `give all players scriptcraft:ruby` / `give @a ruby {n}` |
| вылечить | `heal player` / `heal players in radius 10 of player` (и другие эффекты — см. ниже) |
| телепорт | `teleport player to 0, 100, 0` / `teleport @a to player` / `tp targeted player to "Steve"` |
| открыть экран | `open screen "Заголовок" to player:` / `open screen "Меню" to players in radius 5 of player:` + элементы |
| цикл по игрокам | `loop all players:` + действия с отступом, внутри `loop-player` (см. «Цели») |
| записать переменную | `set {coins::%player%} to 0` / `set {motd} to "Добро пожаловать"` |
| прибавить / отнять | `add 5 to {coins::%player%}` / `remove 1 from {coins::%player%}` |
| удалить переменную | `delete {coins::%player%}` (или `clear {...}`) |
| условие | `if {coins::%player%} >= 10:` + действия с отступом, затем `else:` (необязательно) |

Плейсхолдеры: `%player%`, `%uuid%`, `%online%`, `%args%`, `%targeted%` (имя игрока, на которого смотрит `player`,
или `<none>`), `%loop-player%` (текущий игрок цикла). Цвета: `&a`, `&e`, `&l` и т.д. (как в Bukkit).
Если у предмета не указан мод (`diamond`), мод сначала ищет `minecraft:diamond`, потом `scriptcraft:diamond`,
потом тот же путь в других namespace из скриптов (`example:diamond`). Надёжнее писать полный id: `give player example:ruby 1`.

### Цели: кому применять действие

Везде, где действие относится к игроку (`send … to`, `give`, `heal`, `teleport`, `open screen … to`, все эффекты ниже,
`make … execute command`), вместо `player` можно написать **цель**. Цель — это **набор игроков**: действие выполняется
для каждого по очереди. Пустой набор — действие просто ничего не делает (без ошибки).

| Цель | Кто это |
|---|---|
| `player` (или `%player%`) | тот, кто вызвал команду / зашёл / написал эффект-команду |
| `"Steve"` / `{target::%player%}` / `"%args%"` / `Steve` | игрок по имени. Если его нет в сети — пустой набор (раньше была ошибка) |
| `targeted player` / `target of player` | игрок, на которого смотрит `player` (луч из глаз до `targetedPlayerRange` блоков, сквозь блоки не видит; зрители не считаются). Можно и от другого: `target of "Steve"`, `targeted player of {x}` |
| `all players` / `@a` | все игроки онлайн |
| `players in radius N of <цель>` | игроки в том же мире не дальше N блоков от каждого игрока цели (сам центр входит). N — число или `{переменная}`. Синоним: `players within N blocks of <цель>`, вместо `of` можно `around` |
| `other players in radius N of <цель>` | то же, но без самих центров |
| `@p` / `@r` / `@s` | ванильные селекторы: ближайший (от лица игрока это всегда он сам — как в ванилле), случайный, сам игрок |
| `@a[distance=..10]`, `@r[limit=2,gamemode=survival]` … | ванильные селекторы с аргументами (разбираются самим Minecraft). Пробелов внутри `[...]` лучше не ставить |
| `loop-player` | текущий игрок внутри `loop <цель>:` |

```
command /buffall:
    give players in radius 20 of player effect regeneration for 10 seconds
    send "&a%player% вылечил всех рядом" to other players in radius 20 of player

command /who:
    if targeted player is set:
        send "Ты смотришь на %targeted%" to player
        send title "&cНа тебя смотрят" to targeted player
    else:
        send "Ты ни на кого не смотришь" to player

command /gather permission 2:
    teleport other players in radius 100 of player to player
    loop all players:
        send "&e%loop-player%, сбор у %player%!" to loop-player
```

- Селекторы выполняются от лица игрока из контекста (его позиция и мир; из консоли — от сервера, где `@s` пуст)
  с уровнем прав 2: селектор написал автор скрипта, поэтому прав самого игрока на селекторы не нужно.
  `@e` не поддерживается — только игроки.
- Селекторы распознаются **только написанными прямо в скрипте**. Текст из переменной или `%args%` всегда считается
  именем игрока: `/heal @a` у команды `heal "%args%"` не вылечит всех.
- `teleport <цель> to <цель>` — к первому игроку второго набора (в его мир, с его поворотом). Если второй набор пуст — ничего.
- Условие для целей: `if <цель> is set:` / `if <цель> is not set:` — есть ли хоть один такой игрок
  (`if targeted player is set:`, `if other players in radius 10 of player is not set:`).
- `loop <цель>:` выполняет вложенные действия для каждого игрока; внутри `loop-player` — цель и `%loop-player%` — имя.
  `player` внутри цикла — по-прежнему тот, кто вызвал скрипт (как в Skript). Циклы можно вкладывать.
  Каждое действие каждого прохода тратит шаг `maxScriptSteps`.
- Плейсхолдеры в тексте (`%player%` и т.п.) считаются от игрока из контекста, а не от получателя:
  `send "Привет, %player%" to all players` всем напишет имя вызвавшего. Для имени получателя используйте `loop` и `%loop-player%`.

### Эффекты над игроком (как в Skript)

`<цель>` ниже — любая цель из таблицы выше. Числа можно брать из переменных: `heal player by {bonus}`.

| Эффект | Пример |
|---|---|
| режим игры | `set gamemode of player to creative` / `set player's gamemode to survival` (`survival`/`s`/`0`, `creative`/`c`/`1`, `adventure`/`a`/`2`, `spectator`/`sp`/`3`) |
| лечение | `heal player` (полностью) / `heal player by 4` (4 = два сердца) |
| здоровье | `set health of player to 10` / `set player's health to 20` |
| урон | `damage player by 4` (обычный урон, учитывает броню и режим игры) |
| еда | `feed player` (голод и насыщение на максимум) / `feed player by 6` |
| голод / насыщение | `set food level of player to 10` / `set saturation of player to 5` |
| убить | `kill player` |
| потушить / поджечь | `extinguish player` / `ignite player for 5 seconds` |
| очистить инвентарь | `clear inventory of player` / `clear player's inventory` |
| эффект зелья | `give player effect speed for 30 seconds` / `give player effect speed for 30 seconds level 2` |
| снять эффект | `remove effect speed from player` / `clear effects of player` |
| звук (слышит только игрок) | `play sound entity.player.levelup to player` |
| титры | `send title "&6Привет" to player` / `send title "Уровень 5" with subtitle "&eтак держать" to player` |
| строка над хотбаром | `send action bar "&aСохранено" to player` |
| опыт | `set xp level of player to 30` / `give 100 xp to player` (очки, не уровни) |
| полёт | `set flying of player to true` (даёт право летать и взлетает) / `false` |
| команда от консоли | `execute console command "time set day"` |
| команда от игрока | `make player execute command "spawn"` (с правами самого игрока) |

- id эффектов и звуков — как в ванильных командах: `speed`, `minecraft:night_vision`, `entity.experience_orb.pickup`;
  можно и из модов: `othermod:frost`.
- Текст в `send title`, `send action bar` и команды поддерживают плейсхолдеры, `{переменные}` и цвета `&`.
- Команды отключаются целиком параметром `allowCommandEffects = false`.
- ⚠ **Безопасность команд:** `execute console command` выполняется **с правами консоли**. Не подставляйте туда
  `%args%` в командах, доступных обычным игрокам: игрок может дописать в аргументы что угодно
  (например, `execute console command "give %args% diamond 1"` при `/mycmd @a` выдаст алмазы всем
  игрокам сразу). Для действий над игроком используйте эффекты выше.

**Как добавить свой эффект.** Все шаблоны — одна таблица в `script/parser/EffectPatterns.java`:
```java
rule("ignite %target% for %num% seconds|second|sec", c -> new Effects.Ignite(c.target(0), c.num(1)));
```
Слова шаблона должны совпасть (варианты через `|`), `%target%`, `%num%`, `%text%`, `%id%` — значения.
`%target%` занимает столько слов, сколько нужно цели (разбирает `script/parser/TargetParser.java`), остальные — одно слово.
Действие — `record` в `script/runtime/Effects.java`. Сеттеры (`set X of <цель> to …` и `set player's X to …`)
регистрируются одной строкой `setter("health", …)`.

### Эффект-команды в чате (`?heal player`)

Как в Skript: сообщение в чате, которое начинается с токена (по умолчанию `?`), не уходит в чат,
а выполняется как **одна строка скрипта** от имени написавшего (`player` = он сам):

```
?heal player
?set player's gamemode to creative
?give player effect night_vision for 600 seconds
?send title "Тест" to "Steve"
?heal players in radius 10 of player
?send "привет" to targeted player
?give @a[distance=..20] diamond 1
?set {coins::Steve} to 100
```

- Выключено по умолчанию: `effectCommands.enableEffectCommands = true` в `scriptcraft/config.toml`.
- Право: **`scriptcraft.effectcommands`** (Forge PermissionAPI — его можно выдать через LuckPerms и подобные моды).
  Без мода прав действует правило по умолчанию: только операторы уровня 2, если `effectCommandsRequireOp = true`;
  при `false` — **все игроки** (⚠ опасно: кто угодно сможет `?kill @a`, `?kill "Steve"` или `?set {coins::...}`).
- `execute console command` / `make player execute command` из эффект-команд доступны **только операторам** (уровень 2),
  даже если право выдано через PermissionAPI.
- Блоки (`if …:`, `loop …:`, `open screen …:`) в одной строке не поддерживаются.
- Если цель оказалась пустой (никого в радиусе, ни на кого не смотришь, игрок не в сети), в чат придёт «Нет целей».
- Ошибки разбора и выполнения приходят в чат красным, каждое использование пишется в лог сервера.
- Сообщение с токеном отменяется всегда (даже без прав), чтобы случайно не уйти в общий чат.

### Переменные

```
command /coins:
    add 1 to {coins::%player%}
    send "&eМонет: {coins::%player%}" to player
```

- Имя переменной пишется в фигурных скобках. `%player%` и другие плейсхолдеры внутри имени подставляются,
  поэтому `{coins::%player%}` у каждого игрока своя (`coins::Steve`, `coins::Alex`).
- Внутри текста `{имя}` заменяется значением: `send "Монет: {coins::%player%}" to player`.
  Если переменная не задана, вместо значения выводится `<none>`.
- Значения бывают числами, текстом и `true`/`false`. Текст с пробелами пиши в кавычках: `set {motd} to "Привет всем"`.
  Цифры без кавычек становятся числом, слова без кавычек становятся текстом.
- `add`/`remove` считают незаданную переменную равной 0. Если в переменной лежит текст, будет ошибка выполнения.
- Обычные переменные сохраняются в **один общий файл `scriptcraft/variables/variables.json`**.
  Сохранение идёт раз в `variablesSaveIntervalSeconds` и при остановке сервера.
  - Файл общий для всех миров: на сервере мир один, так что ничего не меняется. В одиночной игре переменные
    теперь общие для всех миров (как в Skript, где `variables.csv` один на сервер).
  - При каждом старте сервера делается резервная копия `variables-ГГГГММДД-ЧЧММСС.json`; хранятся последние
    `variableBackups` (по умолчанию 5), более старые удаляются. Чтобы откатиться — остановите сервер и скопируйте
    нужную копию поверх `variables.json`.
- **Временные переменные**: имя начинается с `-`, например `{-cooldown::%player%}`. Они хранятся только в памяти
  и стираются при старте и остановке сервера (если `enableTemporaryVariables = true`).
- Переменные работают только на сервере.

### Условия

```
command /rich:
    if {coins::%player%} >= 10:
        send "&aТы богат!" to player
    else:
        send "&cНужно ещё монет" to player
```

| Условие | Пример |
|---|---|
| равно / не равно (текст сравнивается без учёта регистра) | `if {rank::%player%} is "vip":` / `if {x} is not 0:` |
| сравнение чисел | `if {x} > 5:` `<` `>=` `<=` |
| задана / не задана | `if {x} is set:` / `if {x} is not set:` |
| есть ли игроки в цели | `if targeted player is set:` / `if players in radius 5 of player is not set:` |

Условия могут быть вложенными. `else:` пишется на том же отступе, что и `if`, сразу после его блока.
Пока нет `else if`, `and`/`or`: используй вложенные `if`.

### Блок через скрипт (пример)

```
define block ruby_block:
    name: "Рубиновый блок"
    hardness: 5            # прочность (камень 1.5)
    resistance: 6          # взрывоустойчивость
    sound: metal           # stone, wood, metal, glass, grass, sand, wool, gravel
    light: 7               # свечение 0..15
    requires_tool: true    # дроп только подходящим инструментом

define item ruby:
    name: "Рубин"
    stack_size: 64
    rarity: rare           # common, uncommon, rare, epic
    fire_resistant: false
```

Получится `scriptcraft:ruby_block` (блок вместе с предметом) и `scriptcraft:ruby`. Оба попадут во вкладку творческого режима **ScriptCraft**.
Свойство `name:` задаёт отображаемое название (можно по-русски) и не требует lang-файлов.

### Свой namespace (`mymod:ruby_block`)

По умолчанию всё регистрируется в namespace `scriptcraft`. Свой namespace задаётся строкой в начале файла
(действует на все `define` ниже в этом файле; в следующем файле снова `scriptcraft`) или прямо в id:

```
namespace mymod                 # или "namespace mymod:" / "modid mymod:"

define block ruby_block:        # -> mymod:ruby_block
    name: "Рубиновый блок"

define item othermod:gem:       # полный id в обход строки namespace
    name: "Самоцвет"
```

- Для каждого namespace мод создаёт свой `DeferredRegister`. Отдельный мод с таким modid не нужен:
  Forge 47.x разрешает регистрировать объекты в чужом namespace (пишет только debug-строку в лог).
- Запрещены `minecraft`, `forge`, `neoforge`, `c` — чтобы не ломать ваниль и общие теги.
- **id только латиницей:** Minecraft разрешает в id лишь `a-z 0-9 _ . -` (и `/` в пути). `своймод:рубиновый_блок`
  невозможен — пишите транслитом: `svoymod:rubinovyj_blok`, а русское название задайте через `name: "Рубиновый блок"`
  или в `lang/ru_ru.json`. Заглавные буквы приводятся к строчным.
- Ресурсы такого блока ищутся в `assets/mymod/...` и `data/mymod/...` — кладите их в `scriptcraft/resources/` (см. ниже).
- Пример: [`examples/scripts/ruby.sk`](examples/scripts/ruby.sk) объявляет и `scriptcraft:ruby_block`/`scriptcraft:ruby`
  (ресурсы в jar), и `example:ruby_block`/`example:ruby` (ресурсы в `scriptcraft/resources/assets/example`,
  названия из `lang`).

### Текстуры, модели, дроп

Кладите обычные файлы ресурспака/датапака в **`scriptcraft/resources/`** — пересобирать мод не нужно:
- `assets/<ns>/blockstates/<id>.json`
- `assets/<ns>/models/block/<id>.json`, `assets/<ns>/models/item/<id>.json`
- `assets/<ns>/textures/block/<id>.png`, `assets/<ns>/textures/item/<id>.png`
- `assets/<ns>/lang/ru_ru.json`, `en_us.json` (если не используете `name:`)
- `assets/<ns>/textures/gui/*.png` — картинки для экранов (`image "<ns>:textures/gui/x.png"`)
- дроп: `data/<ns>/loot_tables/blocks/<id>.json`
- кирка: `data/minecraft/tags/blocks/mineable/pickaxe.json`

Имена файлов — тоже только `a-z 0-9 _ . -` (файлы с заглавными буквами или кириллицей пропускаются с предупреждением).
Без модели блок будет фиолетово-чёрным, без loot table из него ничего не выпадет.
`data/` подключается встроенным датапаком мода (только на сервере, перечитывается ванильной `/reload`),
`assets/` сервер отправляет игрокам сам — см. раздел 6.

## 5. Экраны (Screen API)

```
command /menu:
    open screen "Главное меню" to player:
        text "Привет, %player%!" at 0, -60 color #FFD700 shadow
        image "minecraft:textures/item/diamond.png" at 0, -40 size 16x16 texture 16x16
        slot diamond 5 at -30, -15
        button "Сказать привет" at 0, 15 size 120x20 run "/hello"
        button "Закрыть" at 0, 40 size 120x20 close
        text "v0.1" at -4, -4 anchor bottom_right color #808080
```

| Элемент | Параметры |
|---|---|
| `text "..."` | `at X Y`, `anchor`, `color #RRGGBB` / `#AARRGGBB`, `shadow` |
| `button "..."` | `at`, `size WxH` (по умолчанию 100x20), `anchor`, `run "/команда"` или `close` |
| `slot <предмет> [кол-во]` | `at`, `anchor` (только показ и подсказка, брать нельзя) |
| `image "ns:путь.png"` | `at`, `size WxH`, `texture WxH` (реальный размер файла), `anchor`, `color` (оттенок) |

`anchor`: `top_left`, `top`, `top_right`, `left`, `center` (по умолчанию), `right`, `bottom_left`, `bottom`, `bottom_right`.
Позиция считается так: элемент выравнивается по точке привязки и сдвигается на `X Y` (минус означает влево или вверх).

Картинки из `scriptcraft/resources/assets/<ns>/textures/gui/` сервер отправляет игрокам сам, и они показываются
**сразу, без перезагрузки ресурсов** (см. раздел 6). Пример — баннер в [`menu.sk`](examples/scripts/menu.sk).

Устройство в коде:
```
com.scriptcraft.screen        (общий код: сервер + клиент)
  ScreenDescription           заголовок + список элементов, сериализация в FriendlyByteBuf
  ScreenElement (sealed)      TextElement, ButtonElement, SlotElement, ImageElement
  Layout                      x, y, width, height, anchor, color
  Anchor                      9 точек привязки
com.scriptcraft.network
  ModNetwork                  SimpleChannel "scriptcraft:main"
  OpenScreenPacket            сервер -> клиент: открыть экран
com.scriptcraft.client        (ТОЛЬКО клиент, @OnlyIn(Dist.CLIENT))
  ClientPacketHandler         Minecraft.setScreen(...)
  ScriptScreen                Screen + GuiGraphics: рисует элементы, кнопки — настоящие виджеты
```

## 6. Ресурсы: как текстуры попадают к игрокам

Игрокам **не нужно ставить ресурспак** и не будет никакого окна «Сервер предлагает ресурспак»:
всё идёт через сетевой канал самого мода.

1. Сервер при старте (и по `/sc resources reload`) индексирует каждый файл `scriptcraft/resources/assets/**`:
   путь + SHA-1.
2. При входе игрока сервер отправляет ему этот список (манифест).
3. Клиент сравнивает его со своим кэшем `scriptcraft/cache/assets/...` и просит **только недостающие или изменённые** файлы.
4. Сервер отправляет их кусками (`resourceChunkSizeKb`, не больше ~512 КБ на игрока за тик), клиент собирает файл,
   проверяет SHA-1 и пишет в кэш.
5. Кэш подключён к игре **встроенным скрытым ресурспаком мода**: он регистрируется один раз при запуске игры
   (`AddPackFindersEvent`), всегда включён, закреплён поверх остальных и не виден в меню «Пакеты ресурсов».

**Когда нужна перезагрузка ресурсов (честно):**
- **Картинки для экранов** (`assets/<ns>/textures/gui/*.png`) применяются сразу: мод регистрирует их через
  `DynamicTexture` в `TextureManager`. Перезагрузки нет.
- **Текстуры блоков/предметов, модели, blockstates, lang** перезагрузки требуют: Minecraft склеивает все текстуры
  блоков и предметов в один атлас и «запекает» модели только во время перезагрузки ресурсов, подменить их на лету
  штатного способа нет. Поэтому, если пришли такие файлы, клиент делает **одну** `Minecraft.reloadResourcePacks()`
  (как F3+T, несколько секунд с экраном загрузки) — и **только если что-то действительно изменилось**.
- Так как кэш подключён ещё при запуске игры, при следующих входах (если на сервере ничего не менялось)
  **ничего не скачивается и ничего не перезагружается**. Перезагрузка бывает только при первом входе
  и после изменения ресурсов на сервере.
- Старые файлы из кэша не удаляются (чтобы при переходе между серверами не качать и не перезагружать заново).
  Очистить кэш: закрыть игру и удалить `scriptcraft/cache/`.
- В одиночной игре всё работает так же (встроенный сервер «отправляет» файлы своему же клиенту),
  поэтому первый вход в мир с новыми ресурсами тоже даёт одну перезагрузку.

**Ограничения и защита:** общий размер — `maxResourceSizeMb` (по умолчанию 32 МБ; клиент со своим меньшим лимитом
откажется качать), до 8000 файлов, пути проверяются (только `assets/<ns>/...`, без `..`), SHA-1 проверяется после сборки.
`data/` никогда не отправляется клиентам.

**Важно:** блоки и предметы (`define`) так передать нельзя — реестры создаются при запуске игры, до входа на сервер.
Скрипты с `define` должны быть у клиентов одинаковыми (папка `scriptcraft/scripts/` в сборке модпака).

TODO (оптимизация):
- [ ] хранить индекс кэша (путь → SHA-1), чтобы не пересчитывать SHA-1 файлов при каждом входе;
- [ ] сжатие кусков (PNG уже сжаты, но JSON — нет);
- [ ] перезагружать только атлас и модели, а не все ресурсы (нужен хак в обход ванильной перезагрузки);
- [ ] отдельный кэш на каждый сервер и очистка устаревших файлов;
- [ ] передача `define` в модпак-копию скриптов (утилита, которая собирает папку для клиентов).

## 7. Архитектура

```
src/main/java/com/scriptcraft/
  ScriptCraft.java             @Mod: порядок запуска (папки -> конфиг -> скрипты -> реестры -> сеть -> паки -> события)
  ModPaths.java                папка scriptcraft/: scripts, variables, resources, cache; перенос старой scripts/; примеры
  registry/                    DeferredRegister на каждый namespace: блоки/предметы из скриптов, вкладка творческого режима
  script/
    ScriptPaths, ScriptSource  чтение scriptcraft/scripts/, выключенные "-имя.sk"
    ScriptLoader               текущая программа, reload всего/одного, enable/disable, запуск событий/команд
    variables/VariableStore    переменные: scriptcraft/variables/variables.json + резервные копии + временные {-имя}
    engine/                    ScriptEngine (интерфейс) -> SimpleScriptEngine -> ScriptProgram
    parser/                    Tokenizer, ScriptParser (namespace, define), EffectPatterns (таблица эффектов),
                               TargetParser (цели: all players, players in radius ..., @a[...])
    runtime/                   Action, Actions, Effects (эффекты над игроком), Target, Expression, Condition,
                               ExecutionContext, ExecutionBudget
  config/ScriptCraftConfig     ForgeConfigSpec -> scriptcraft/config.toml
  lang/Messages                сообщения мода на EN/RU (язык выбирается в конфиге)
  command/ScriptCommands       /scriptcraft|/sc|/script help|reload|enable|disable|list|resources reload + команды скриптов
  command/EffectCommands       эффект-команды в чате (?heal player), право scriptcraft.effectcommands
  resources/                   ResourceIndex (индекс + SHA-1), ResourceSync (манифест, очередь кусков),
                               ResourcePacks (скрытый клиентский пак кэша, серверный датапак)
  event/ServerEvents           Forge-события -> события скриптов, переменные, раздача ресурсов
  network/                     ModNetwork (протокол 2), OpenScreenPacket, ResourceManifest/Request/ChunkPacket
  screen/, client/             Screen API; client/ClientResources — кэш, DynamicTexture, перезагрузка ресурсов
```

**Песочница.** Скрипт может вызывать только действия из `Actions.java`. У него нет рефлексии, доступа к файлам и Java-классам.
На каждый запуск действует лимит: по умолчанию 10 000 шагов и 50 мс (`maxScriptSteps`, `maxScriptMillis` в конфиге).
Пока в языке нет циклов, но лимит уже стоит на будущее.

**Горячая перезагрузка.** `reload` компилирует скрипты заново и подменяет программу целиком, одной операцией.
Новые команды добавляются в дерево Brigadier, после чего дерево команд заново рассылается игрокам.
Удалённые команды остаются в дереве до перезапуска. При вызове они пишут «выключена», потому что Brigadier не умеет удалять узлы.

**Почему три дерева, а не redirect.** У Brigadier `redirect` не переносит `executes()` самого корня,
поэтому голый `/sc` не показал бы справку. Поэтому одна функция строит одинаковое дерево для `/scriptcraft`, `/sc` и `/script`.
Если `/script` уже занят другим модом, этот синоним пропускается (с предупреждением в логе).

**Новое действие** добавляется так: эффект над игроком — `record` в `Effects.java` и строка в `EffectPatterns`;
остальное — `record` в `Actions.java` и `case` в `ScriptParser.parseAction`.

## 8. TODO

- [ ] **Аддон PlayerAnimator и GeckoLib**: действие `play animation "staff_cast" on player`, анимация от третьего лица плюс анимация предмета от первого лица по одному ключу.
- [ ] **Полноценная GUI-система**: настоящие слоты через `AbstractContainerMenu`, поля ввода, прокрутка, вкладки, серверные колбэки кнопок (пакет клиент -> сервер вместо `run "/cmd"`), обновление открытого экрана.
- [ ] **Сайт в стиле ScriptHub**: каталог скриптов, загрузка и скачивание `.sk`, версии, рейтинг, документация по синтаксису.
- [ ] **Движок Rhino (JavaScript) или LuaJ** как вторая реализация `ScriptEngine`, с белым списком API через `ClassShutter`.
- [ ] **Больше событий**: death, respawn, chat, block break/place, right click, item use, tick/таймеры.
- [x] Переменные (`set/add/remove/delete`, `{x::%player%}`, временные `{-x}`) и условия `if/else`.
- [ ] Язык: `else if`, `and`/`or`, списки `{x::*}` и `loop {x::*}`, циклы по числам (`loop 5 times`), `wait 5 seconds` (планировщик по тикам), арифметика в выражениях, аргументы команд (`arg 1`).
- [ ] События блоков и предметов из скриптов (`on use`, `on break`), форма блока, BlockEntity.
- [ ] Генерация моделей и blockstate «на лету» через встроенный ресурспак, чтобы не писать JSON руками.
- [ ] Синхронизация `define` с клиентом (модпак-копия скриптов) и понятная ошибка при несовпадении.
- [x] Эффекты над игроком (как в Skript), эффект-команды `?…`, свой namespace, синхронизация ресурсов через мод.
- [x] Цели-наборы игроков: `all players`, `@a`/`@p`/`@r`/`@s` и селекторы с аргументами, `targeted player`, `players in radius N of …`, `loop <цель>:`.
- [ ] Эффекты над мобами и блоками, цели `targeted entity`, `entities in radius`, `loop-entity`.
- [ ] Публичный API для аддонов (отдельный артефакт `scriptcraft-api` + Javadoc).

## 9. Сайт документации (GitHub Pages)

В папке [`docs/`](docs) лежит сайт документации в духе SkriptHub: поиск по всему синтаксису, фильтр по категориям
(события, эффекты, условия, выражения, цели, экраны, команды мода, конфиг), пример у каждой строки, вкладки
«Примеры» (большие готовые скрипты) и «Начало» (установка, папка `scriptcraft/`, команды, конфиг). Русский интерфейс
с переключателем на английский, тёмная и светлая тема. Это обычные HTML/CSS/JS без сборки и без внешних CDN:
сайт открывается и прямо с диска (`docs/index.html` двойным щелчком).

Адрес после включения: **https://elgerelg.github.io/ScriptCraft/**

**Как включить GitHub Pages** (один раз, после `git push`):
1. На GitHub откройте репозиторий → **Settings → Pages**.
2. В **Build and deployment → Source** выберите **Deploy from a branch**.
3. **Branch:** `main`, папка **`/docs`** → **Save**.
4. Через 1–2 минуты сайт появится по адресу выше. Дальше он обновляется сам после каждого push в `main`.

Файл `docs/.nojekyll` отключает обработку Jekyll: файлы отдаются как есть.

**Устройство:**

```
docs/
  index.html        синтаксис: поиск, фильтры, карточки, ссылки вида index.html#eff-heal
  examples.html     большие примеры (данные — data/examples.js)
  start.html        «Начало»
  data/syntax.js    ВСЕ записи синтаксиса (данные сайта)
  data/examples.js  большие примеры
  assets/           style.css, app.js (строки RU/EN, тема, подсветка, копирование), docs.js, examples.js
  check.js          проверка данных: node docs/check.js
```

Данные подключаются тегом `<script>` (а не `fetch` JSON), поэтому сайт работает и с `file://`.
Подборки можно давать ссылкой: `index.html?q=radius` или `index.html?cat=effects,targets`.

**Как добавить запись в документацию** — допишите объект в массив `entries` в `docs/data/syntax.js`:

```js
{
  id: "eff-heal",                       // уникальный, латиница/цифры/-/_ ; это и якорь #eff-heal
  category: "effects",                  // events | structures | effects | conditions | expressions | targets | screen | commands | config
  name: { ru: "Вылечить", en: "Heal" },
  syntax: ["heal <цель>"],              // один или несколько шаблонов
  desc: { ru: "Описание, `код` и **жирный** можно.", en: "Description." },
  example: `command /healme:
    heal player`,                       // пример (для config — строка из config.toml)
  since: "0.1.2",                       // версия мода, где появилось
  related: ["eff-feed", "target-radius"] // id других записей (могут быть пустыми: [])
}
```

Пишите только то, что мод действительно поддерживает. Новую категорию добавляют в `categories` (id, ru, en).
Большой пример — объект в `docs/data/examples.js` (id, title, intro, blocks с кодом, notes, related).
Строки интерфейса (кнопки, подсказки) — таблица `SC.strings` в `docs/assets/app.js`.
После правки запустите проверку (Node.js 16+):

```bat
node docs/check.js
```

Она проверяет уникальность id, обязательные поля, категории и ссылки `related` и печатает число записей по категориям.

## 10. GitHub

Репозиторий: https://github.com/ElgerElg/ScriptCraft. Remote `origin` уже добавлен в локальный git.

Проверить remote:
```bat
git remote -v
```

**Если репозиторий на GitHub пустой** (без README и лицензии):
```bat
git push -u origin main
```

**Если на GitHub уже есть коммиты** (создан с README, .gitignore или лицензией):
```bat
git pull origin main --allow-unrelated-histories
:: при конфликте (например, в README.md) поправь файл, затем:
::   git add README.md
::   git commit
git push -u origin main
```

Если remote ещё не добавлен (например, проект скопирован без папки `.git`):
```bat
git init -b main
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/ElgerElg/ScriptCraft.git
git push -u origin main
```

Через GitHub CLI новый приватный репозиторий создаётся одной командой (нужен `gh auth login`):
```bat
gh repo create ScriptCraft --private --source . --push
```

При первом `push` Git for Windows откроет окно входа в GitHub. Пароль от аккаунта при push не подходит, нужен вход через браузер или Personal Access Token.
"# ScriptCraft" 
