# ==============================================================================
#  gecko.sc — Полная витрина 3D-движка GeckoLib / Blockbench и РП-механик
#  Включить: /sc enable gecko   |   Перезагрузить ресурсы: /sc reload resources
#  Команда-стенд в игре: /gecko (открывает интерактивный 3D-терминал со всеми демо)
#
#  Что демонстрирует этот единый скрипт:
#   1. Кастомные 3D-мобы, боссы и турели без лимита слотов (define entity)
#   2. Анимированное 3D-оружие и предметы (define item + play item animation)
#   3. 3D-броня и РП-одежда с привязкой к костям и PlayerAnimator (define armor)
#   4. Анимированные 3D-блоки и мебель (define block + play block animation)
#   5. Светящиеся текстуры (_glowmask.png) и партиклы из костей/локаторов
#   6. Фейковые игроки-NPC со скинами и анимациями PlayerAnimator (spawn npc)
#   7. 3D-аттачи (Props) к костям игрока и NPC (attach prop — дрон на плечо)
#   8. Авто-регистрация кастомных звуков .ogg БЕЗ ручного sounds.json
#   9. Плавные кинематографичные пролёты камеры по сплайну (play cutscene)
# ==============================================================================

namespace gecko

# ------------------------------------------------------------------------------
#  1. КАСТОМНЫЕ 3D-МОБЫ, БОССЫ И ТУРЕЛИ (БЕЗ ЛИМИТА СЛОТОВ!) + ПАРТИКЛЫ ИЗ КОСТЕЙ
# ------------------------------------------------------------------------------

define entity cyber_guardian:
	name: "&b&lКибер-Страж"
	ai: hostile
	model: "gecko:cyber_guardian"
	texture: "gecko:textures/entity/cyber_guardian.png"
	animation: "gecko:cyber_guardian"
	idle_animation: "idle"
	walk_animation: "walk"
	attack_animation: "attack"
	spawn_animation: "roar"
	health: 120
	speed: 0.27
	damage: 8
	attack_range: 3.0
	follow_range: 28
	hitbox: 1.3x2.6
	scale: 1.15
	glow: true
	particle_bone: "core_locator=end_rod"

define entity plasma_turret:
	name: "&eАвто-Турель «Страж-4»"
	ai: turret
	model: "gecko:plasma_turret"
	texture: "gecko:textures/entity/plasma_turret.png"
	animation: "gecko:plasma_turret"
	idle_animation: "idle"
	attack_animation: "attack"
	health: 60
	damage: 6
	attack_range: 14
	hitbox: 1.0x1.5
	scale: 1.0
	glow: true
	particle_bone: "muzzle_locator=electric_spark"

# ------------------------------------------------------------------------------
#  2. АНИМИРОВАННОЕ 3D-ОРУЖИЕ (DEFINE ITEM + .GEO.JSON + _GLOWMASK.PNG)
# ------------------------------------------------------------------------------

define item plasma_rifle:
	name: "&bПлазменная винтовка МК-II"
	lore: "&7ПКМ — выстрел с 3D-анимацией отдачи\n&7Шейдерное свечение ячейки (_glowmask.png)"
	stack: 1
	damage: 7
	model: "gecko:plasma_rifle"
	texture: "gecko:textures/item/plasma_rifle.png"
	animation: "gecko:plasma_rifle"
	idle_animation: "idle"
	scale: 1.0

# ------------------------------------------------------------------------------
#  3. 3D-БРОНЯ И КОСМЕТИКА (СИНХРОНИЗАЦИЯ С КОСТЯМИ ИГРОКА И PLAYERANIMATOR)
# ------------------------------------------------------------------------------

define armor exo_helmet:
	name: "&bЭкзо-шлем «Авангард»"
	slot: helmet
	defense: 4
	toughness: 2.0
	lore: "&7Светящийся визор (_glowmask.png)\n&7Повторяет повороты головы в РП-эмоциях"
	model: "gecko:exo_armor"
	texture: "gecko:textures/armor/exo_armor.png"
	animation: "gecko:exo_armor"
	idle_animation: "idle"

define armor exo_chestplate:
	name: "&bЭкзо-кираса с реактором"
	slot: chestplate
	defense: 8
	toughness: 3.0
	lore: "&7Пульсирующий спинной реактор\n&7Синхронизация рук с PlayerAnimator"
	model: "gecko:exo_armor"
	texture: "gecko:textures/armor/exo_armor.png"
	animation: "gecko:exo_armor"
	idle_animation: "idle"

# ------------------------------------------------------------------------------
#  4. АНИМИРОВАННЫЙ 3D-БЛОК / МЕБЕЛЬ (DEFINE BLOCK + .GEO.JSON)
# ------------------------------------------------------------------------------

define block holo_terminal:
	name: "&3Голографический терминал"
	hardness: 3.0
	resistance: 8.0
	light: 12
	full: false
	facing: horizontal
	tool: pickaxe
	model: "gecko:holo_terminal"
	texture: "gecko:textures/block/holo_terminal.png"
	animation: "gecko:holo_terminal"
	idle_animation: "idle"
	scale: 1.0

# ------------------------------------------------------------------------------
#  ИНТЕРАКТИВ: СТРЕЛЬБА ИЗ 3D-ВИНТОВКИ И ОТКРЫТИЕ 3D-ТЕРМИНАЛА
# ------------------------------------------------------------------------------

on right click:
	# 1) Если кликнули по 3D-блоку holo_terminal — крутим его куб и открываем меню
	if "%block%" is "gecko:holo_terminal":
		cancel event
		play block animation "activate" at targeted block once speed 1.2
		# Кастомный .ogg звук из assets/gecko/sounds/terminal_boot.ogg БЕЗ sounds.json!
		play sound gecko:terminal_boot at targeted block with volume 0.9 pitch 1.0
		spawn particle end_rod at player count 14 speed 0.04
		GeckoOpenHub(player)
		stop

	# 2) Если в руке 3D-винтовка plasma_rifle — отдача, кастомный .ogg выстрел и импульс!
	if player is holding gecko:plasma_rifle:
		if {-gecko_cd::%player%} is set:
			stop
		set {-gecko_cd::%player%} to 1
		play item animation "shoot" for player once speed 1.25
		# Кастомный .ogg звук из assets/gecko/sounds/plasma_shot.ogg БЕЗ sounds.json!
		play sound gecko:plasma_shot at player with volume 0.9 pitch 1.05
		shake camera of player with intensity 0.8 for 4 ticks
		spawn particle electric_spark at player count 18 speed 0.15
		if "%targeted entity%" is not "":
			push targeted entity away from player with force 0.7
			spawn particle end_rod at player count 12 speed 0.08
		wait 8 ticks
		delete {-gecko_cd::%player%}

# ------------------------------------------------------------------------------
#  6. ДИАЛОГИ И ЭМОЦИИ ФЕЙКОВОГО ИГРОКА-NPC (2.1)
# ------------------------------------------------------------------------------

on npc click "commander":
	make npc "commander" look at player
	play npc animation "gecko:npc_salute" on npc "commander" speed 1.0
	play sound gecko:terminal_boot to player with volume 0.7 pitch 1.2
	send "&b[Командор Векс] &fЗдравия желаю, &e%player%&f! Держи 3D-снаряжение или жми &b/gecko&f!" to player

on npc attack "commander":
	play npc animation "gecko:npc_wave" on npc "commander" speed 1.2
	send action bar "&eКомандор Векс: &fЭй, полегче с плазмой, боец!" to player

# ------------------------------------------------------------------------------
#  ГЛАВНАЯ КОМАНДА СТЕНДА: /gecko [menu|kit|boss|turret|npc|drone|cutscene|clear]
# ------------------------------------------------------------------------------

command /gecko [text]:
	permission: 2
	aliases: gecko3d, g3d
	if arg-1 is not set or arg-1 is "menu" or arg-1 is "hub":
		GeckoOpenHub(player)
		stop
	if arg-1 is "kit" or arg-1 is "items":
		GeckoGiveKit(player)
		stop
	if arg-1 is "boss":
		GeckoSpawnBoss(player)
		stop
	if arg-1 is "turret":
		GeckoSpawnTurret(player)
		stop
	if arg-1 is "npc":
		GeckoSpawnCommander(player)
		stop
	if arg-1 is "drone" or arg-1 is "prop":
		GeckoToggleDrone(player)
		stop
	if arg-1 is "cutscene" or arg-1 is "cam":
		GeckoPlayCutscene(player)
		stop
	if arg-1 is "clear":
		GeckoClearAll(player)
		stop
	send "&bИспользование: &f/gecko [menu | kit | boss | turret | npc | drone | cutscene | clear]" to player

# ------------------------------------------------------------------------------
#  ФУНКЦИИ ВИТРИНЫ И ИНТЕРАКТИВНОЕ 3D-МЕНЮ
# ------------------------------------------------------------------------------

function GeckoOpenHub(p: player):
	open screen "Терминал GeckoLib 3D" to p:
		box at 0 0 size 336x204 anchor center color #E80B1220 border #FF38BDF8 radius 8 layer 0
		box at 0 -84 size 336x26 anchor center color #660EA5E9 radius 8 layer 1
		text "&b&l◈ SCRIPTCRAFT × GECKOLIB 3D СТЕНД" at 0 -88 anchor center shadow layer 2

		# Живой 3D-рендер кастомного .geo.json босса прямо внутри GUI!
		box at -98 8 size 118x136 anchor center color #88090D16 border #FF1E293B radius 6 layer 1
		text "&73D-превью босса" at -98 -52 anchor center layer 2
		entity "gecko:cyber_guardian" at -98 56 scale 34 anchor center spin right speed 28 layer 3

		# Кнопки демонстрации каждого из 9 пунктов
		button "&b⚔ Выдать 3D-набор (оружие + броня + блок)" at 64 -46 size 192x20 anchor center run GeckoGiveKit(player) tooltip "&7Выдаёт:%nl%&f• 3D Плазменную винтовку%nl%&f• 3D Экзо-шлем и Экзо-кирасу%nl%&f• 3D Голографический терминал"
		button "&c☠ Призвать 3D-Босса «Кибер-Страж»" at 64 -22 size 192x20 anchor center run GeckoSpawnBoss(player) tooltip "&7Кастомный моб без лимита слотов!%nl%&7Светящееся ядро + партиклы из локатора"
		button "&e⚙ Поставить 3D-Турель (AI: turret)" at 64 2 size 192x20 anchor center run GeckoSpawnTurret(player) tooltip "&7Автоматически наводится на монстров%nl%&7и проигрывает анимацию отдачи стволов"
		button "&a👤 Создать РП-NPC «Командор Векс»" at 64 26 size 192x20 anchor center run GeckoSpawnCommander(player) tooltip "&7Фейковый игрок со скином, 3D-бронёй%nl%&7и эмоциями PlayerAnimator по ПКМ!"
		button "&d🛸 Дрон на плечо (3D-Prop)" at 14 50 size 92x20 anchor center run GeckoToggleDrone(player) keep open tooltip "&7Привязывает 3D-модель дрона к кости body"
		button "&3🎬 Катсцена (пролёт)" at 114 50 size 92x20 anchor center run GeckoPlayCutscene(player) tooltip "&7Плавный пролёт камеры по сплайну вокруг вас"
		button "&7✖ Очистить мобов / NPC / пропы" at 64 76 size 192x18 anchor center run GeckoClearAll(player) keep open

function GeckoGiveKit(p: player):
	give p gecko:plasma_rifle 1
	give p gecko:holo_terminal 2
	set slot helmet of p to 1 gecko:exo_helmet
	set slot chestplate of p to 1 gecko:exo_chestplate
	play sound gecko:terminal_boot to p with volume 0.9 pitch 1.15
	send "&b[Gecko 3D] &fВыданы &eПлазменная винтовка&f, надета &e3D Экзо-броня &fи выдан &eГолографический терминал&f!" to p

function GeckoSpawnBoss(p: player):
	spawn geo entity "gecko:cyber_guardian" at p
	play geo animation "roar" on last spawned entity once speed 1.0
	play sound entity.warden.roar at p with volume 0.9 pitch 1.25
	shake camera of p with intensity 2.2 for 16 ticks
	send "&c&l[БОСС] &bКибер-Страж &7призван! Обратите внимание на светящееся ядро и частицы end_rod." to p

function GeckoSpawnTurret(p: player):
	spawn geo turret "gecko:plasma_turret" at p
	play sound gecko:terminal_boot at p with volume 0.9 pitch 1.3
	send "&e[Турель] &fАвто-турель установлена! Она сама атакует враждебных мобов в радиусе 14 блоков." to p

function GeckoSpawnCommander(p: player):
	spawn npc "commander" at p named "&b★ Командор Векс" skin "gecko:textures/skins/commander.png" wide look true
	equip npc "commander" with chestplate gecko:exo_chestplate
	equip npc "commander" with mainhand gecko:plasma_rifle
	# Можно даже прицепить 3D-проп к кости самого фейкового NPC!
	attach prop "npc_drone" model "gecko:shoulder_drone" animation "hover" to body of npc "commander" offset 6 5 2 scale 0.75
	play npc animation "gecko:npc_salute" on npc "commander" speed 1.0
	play sound gecko:terminal_boot at p with volume 0.8 pitch 1.1
	send "&a[NPC] &fКомандор Векс на посту! Нажмите по нему &eПКМ &fдля диалога и РП-воинского приветствия." to p

function GeckoToggleDrone(p: player):
	if {-gecko_drone::%p%} is set:
		detach prop "drone" from p
		delete {-gecko_drone::%p%}
		send action bar "&7🛸 Плечевой 3D-дрон отключён" to p
	else:
		set {-gecko_drone::%p%} to 1
		# 7. Аттач 3D-модели с постоянной анимацией винтов прямо к кости body игрока!
		attach prop "drone" model "gecko:shoulder_drone" texture "gecko:textures/entity/shoulder_drone.png" animation "hover" to body of p offset -6 5 2 scale 0.85
		play sound gecko:terminal_boot to p with volume 0.8 pitch 1.4
		send action bar "&d🛸 3D-дрон прикреплён к плечу! Нажмите F5, чтобы увидеть его в движении" to p

function GeckoPlayCutscene(p: player):
	close screen of p
	fade screen of p to #000000 in 5 stay 4 out 10 ticks
	play sound gecko:terminal_boot to p with volume 0.9 pitch 0.9
	send title "&3&lКАТСЦЕНА" with subtitle "&7Плавный пролёт по сплайну Catmull-Rom" to p
	# 9. Плавный облёт вокруг игрока на 360° с авто-фокусом взгляда на игрока!
	play cutscene for p points "~0 ~2.2 ~5.5; ~5.2 ~3.6 ~1.5; ~1.5 ~4.0 ~-5.2; ~-5.0 ~2.8 ~-1.0; ~0 ~1.9 ~4.2" for 6 seconds looking at p

function GeckoClearAll(p: player):
	clear all geo entities
	clear all npcs
	clear props of p
	delete {-gecko_drone::%p%}
	stop cutscene for p
	send "&7[Gecko 3D] Все 3D-сущности, фейковые NPC и аттачи очищены." to p
