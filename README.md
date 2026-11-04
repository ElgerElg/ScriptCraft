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
- `runClient` и `runServer` работают в папке `run\`, поэтому скрипты при разработке лежат в `run\scripts\`.
- Перед первым `runServer` открой `run\eula.txt` и поставь `eula=true`.
- Для IntelliJ IDEA: *File → Open →* `build.gradle`, *Open as Project*. После импорта выполни `gradlew.bat genIntellijRuns`.

## 3. Где лежат скрипты

- Папка **`scripts`** находится в папке игры или сервера (рядом с `mods`, на сервере рядом с `server.properties`).
- Файлы называются `*.sk` и читаются в кодировке **UTF-8**. Подпапки тоже читаются.
- При первом запуске мод сам создаёт `scripts/` и копирует туда примеры из [`examples/scripts`](examples/scripts).

### Команды мода

Корневая команда **`/scriptcraft`**, у неё есть полноценные синонимы **`/sk`** и **`/script`**: все три работают одинаково.
Нужен уровень оператора 2. Имена скриптов подсказываются по Tab, расширение `.sk` писать не обязательно.

| Команда | Что делает |
|---|---|
| `/sk` или `/sk help` | справка |
| `/sk reload` | перезагрузить **все** скрипты (логику: события, команды, сообщения, экраны) |
| `/sk reload welcome` | перезагрузить только `welcome.sk` (подпапки так: `/sk reload sub/name`) |
| `/sk disable welcome` | выключить скрипт: выгружает его события и команды и переименовывает файл в `-welcome.sk` |
| `/sk enable welcome` | включить: переименовывает `-welcome.sk` обратно в `welcome.sk` и загружает его |
| `/sk list` | загруженные и выключенные скрипты, команды, число переменных и ошибок |

**Выключенные скрипты** (как в Skript): любой файл, имя которого начинается с `-`, не загружается ни при старте,
ни при `reload`, ни при предварительном чтении блоков. Выключить скрипт можно и вручную: просто переименуй файл.
Ограничения Brigadier:
- команды выключенного скрипта остаются в списке команд до перезапуска сервера и при вызове отвечают «выключена»;
- `define block/item` выключенного или включённого скрипта меняются только после перезапуска.

### Конфиг

Файл `config/scriptcraft-common.toml` создаётся при первом запуске:

| Параметр | По умолчанию | Описание |
|---|---|---|
| `language` | `"en"` | язык сообщений мода: `"en"` или `"ru"` |
| `enableTemporaryVariables` | `true` | переменные `{-имя}` живут только в памяти и очищаются при старте и остановке сервера. При `false` они сохраняются, как обычные |
| `variablesSaveIntervalSeconds` | `300` | как часто автосохранять переменные (10–86400 с) |
| `maxScriptSteps` | `10000` | максимум действий за один запуск скрипта |
| `maxScriptMillis` | `50` | максимум миллисекунд на один запуск скрипта |
| `logScriptLoadTimes` | `false` | писать в лог время компиляции скриптов |

Конфиг читается после загрузки мода, поэтому во время самого первого чтения скриптов при запуске
действуют значения по умолчанию. На работу в игре это не влияет: команды и скрипты читают уже загруженный конфиг.

### ⚠ Блоки и предметы требуют перезапуска

Реестры Minecraft «замораживаются» после загрузки. Поэтому мод **заранее** читает скрипты при старте (в конструкторе мода)
и регистрирует найденные `define block` и `define item`.

- Новый или изменённый `define block/item` начнёт работать только **после перезапуска** игры или сервера. При `reload` мод об этом предупредит.
- Файлы с `define` должны быть **одинаковыми на сервере и у всех клиентов**: положи их в сборку модпака.
  Если наборы блоков различаются, клиент не сможет зайти (ошибка несовпадения реестров).

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
| сообщение игроку | `send "Привет, %player%!" to player` |
| сообщение всем | `broadcast "Сервер перезапустится"` |
| ответ вызвавшему (работает и из консоли) | `reply "Готово"` |
| выдать предмет | `give player diamond 3` / `give player scriptcraft:ruby 1` / `give player ruby 1` |
| вылечить | `heal player` |
| телепорт | `teleport player to 0, 100, 0` |
| открыть экран | `open screen "Заголовок" to player:` + элементы (см. ниже) |

| записать переменную | `set {coins::%player%} to 0` / `set {motd} to "Добро пожаловать"` |
| прибавить / отнять | `add 5 to {coins::%player%}` / `remove 1 from {coins::%player%}` |
| удалить переменную | `delete {coins::%player%}` (или `clear {...}`) |
| условие | `if {coins::%player%} >= 10:` + действия с отступом, затем `else:` (необязательно) |

Плейсхолдеры: `%player%`, `%uuid%`, `%online%`, `%args%`. Цвета: `&a`, `&e`, `&l` и т.д. (как в Bukkit).
Если у предмета не указан мод (`diamond`), мод сначала ищет `minecraft:diamond`, потом `scriptcraft:diamond`.

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
- Обычные переменные сохраняются в файл **`<папка мира>/scriptcraft/variables.json`** (свой файл у каждого мира).
  Сохранение идёт раз в `variablesSaveIntervalSeconds` и при остановке сервера.
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

**Текстуры, дроп и инструмент** задаются обычными ресурсами. Пример для `ruby_block` и `ruby` уже есть в моде:
- `src/main/resources/assets/scriptcraft/blockstates/<id>.json`
- `.../models/block/<id>.json`, `.../models/item/<id>.json`
- `.../textures/block/<id>.png`, `.../textures/item/<id>.png`
- дроп: `src/main/resources/data/scriptcraft/loot_tables/blocks/<id>.json`
- кирка: `data/minecraft/tags/blocks/mineable/pickaxe.json`

Ресурсы можно положить и в обычный ресурспак или датапак, пересобирать мод для этого не нужно.
Без модели блок будет фиолетово-чёрным, без loot table из него ничего не выпадет.

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

## 6. Архитектура

```
src/main/java/com/scriptcraft/
  ScriptCraft.java             @Mod: порядок запуска (скрипты -> реестры -> сеть -> события)
  registry/                    DeferredRegister: блоки/предметы из скриптов, вкладка творческого режима
  script/
    ScriptPaths, ScriptSource  чтение папки scripts/, выключенные "-имя.sk"
    ScriptLoader               текущая программа, reload всего/одного, enable/disable, запуск событий/команд
    variables/VariableStore    переменные: <мир>/scriptcraft/variables.json + временные {-имя}
    engine/                    ScriptEngine (интерфейс) -> SimpleScriptEngine -> ScriptProgram
    parser/                    Tokenizer, ScriptParser, определения блоков/предметов/команд
    runtime/                   Action, Actions (белый список), Expression, Condition, ExecutionContext, ExecutionBudget
  config/ScriptCraftConfig     ForgeConfigSpec -> config/scriptcraft-common.toml
  lang/Messages                сообщения мода на EN/RU (язык выбирается в конфиге)
  command/ScriptCommands       /scriptcraft|/sk|/script help|reload|enable|disable|list + команды из скриптов
  event/ServerEvents           Forge-события -> события скриптов, жизненный цикл переменных
  network/, screen/, client/   Screen API (см. выше)
```

**Песочница.** Скрипт может вызывать только действия из `Actions.java`. У него нет рефлексии, доступа к файлам и Java-классам.
На каждый запуск действует лимит: по умолчанию 10 000 шагов и 50 мс (`maxScriptSteps`, `maxScriptMillis` в конфиге).
Пока в языке нет циклов, но лимит уже стоит на будущее.

**Горячая перезагрузка.** `reload` компилирует скрипты заново и подменяет программу целиком, одной операцией.
Новые команды добавляются в дерево Brigadier, после чего дерево команд заново рассылается игрокам.
Удалённые команды остаются в дереве до перезапуска. При вызове они пишут «выключена», потому что Brigadier не умеет удалять узлы.

**Почему три дерева, а не redirect.** У Brigadier `redirect` не переносит `executes()` самого корня,
поэтому голый `/sk` не показал бы справку. Поэтому одна функция строит одинаковое дерево для `/scriptcraft`, `/sk` и `/script`.
Если `/script` уже занят другим модом, этот синоним пропускается (с предупреждением в логе).

**Новое действие** добавляется так: создать `record` в `Actions.java` и добавить `case` в `ScriptParser.parseAction`.

## 7. TODO

- [ ] **Аддон PlayerAnimator и GeckoLib**: действие `play animation "staff_cast" on player`, анимация от третьего лица плюс анимация предмета от первого лица по одному ключу.
- [ ] **Полноценная GUI-система**: настоящие слоты через `AbstractContainerMenu`, поля ввода, прокрутка, вкладки, серверные колбэки кнопок (пакет клиент -> сервер вместо `run "/cmd"`), обновление открытого экрана.
- [ ] **Сайт в стиле ScriptHub**: каталог скриптов, загрузка и скачивание `.sk`, версии, рейтинг, документация по синтаксису.
- [ ] **Движок Rhino (JavaScript) или LuaJ** как вторая реализация `ScriptEngine`, с белым списком API через `ClassShutter`.
- [ ] **Больше событий**: death, respawn, chat, block break/place, right click, item use, tick/таймеры.
- [x] Переменные (`set/add/remove/delete`, `{x::%player%}`, временные `{-x}`) и условия `if/else`.
- [ ] Язык: `else if`, `and`/`or`, списки `{x::*}` и `loop {x::*}`, циклы, `wait 5 seconds` (планировщик по тикам), арифметика в выражениях, аргументы команд (`arg 1`).
- [ ] События блоков и предметов из скриптов (`on use`, `on break`), форма блока, BlockEntity.
- [ ] Генерация моделей и blockstate «на лету» через встроенный ресурспак, чтобы не писать JSON руками.
- [ ] Синхронизация `define` с клиентом и понятная ошибка при несовпадении.
- [ ] Публичный API для аддонов (отдельный артефакт `scriptcraft-api` + Javadoc).

## 8. GitHub

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
