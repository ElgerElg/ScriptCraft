# =====================================================================
#  rp_roles.sc — Полноэкранное меню выбора ролей «МЕГАПОЛИС RP»
#  Макет:
#   • СЛЕВА (anchor left): Заголовок, статус игрока, подробная информация
#     о роли, лимит слотов, живой 3D-предпросмотр персонажа со скином +
#     3D-спутник фракции, стартовый набор и кнопки действий
#   • СПРАВА (anchor right, на весь экран): Единый большой непрерывный
#     список всех 10 ролей во всю высоту экрана со скроллбаром, иконками,
#     бейджами, полосами лимита, звуками при наведении (hover_sound) и
#     мгновенным предпросмотром при наведении курсора (on_hover)
#   • Кнопка «⛶ Экран» адаптирует ширину и высоту единого списка под любой
#     GUI Scale (480x270 / 640x360 / 960x540) во весь экран без зазоров!
#
#  Команды:
#   /roles (или /rp, /role, /job, /jobs) — открыть полноэкранное меню ролей
#   /roles list                          — список занятых ролей в чате
#   /roles reset                         — (OP) сбросить все лимиты ролей
#   /mayor <указ>                        — указ Мэра города
#   /news <эфир>                         — прямой эфир Репортёра СМИ
#   /ems <игрок>                         — реанимация и лечение от Парамедика
# =====================================================================

on load:
	RpInitMeta()

on join:
	if {rp::role::%player%} is not set:
		set {rp::role::%player%} to 10
	if {rp::cash::%player%} is not set:
		set {rp::cash::%player%} to 1000

# --- Периодическая зарплата каждые 5 минут по текущей РП-роли ---
every 5 minutes:
	loop all players:
		set {-r} to {rp::role::%loop-player%}
		if {-r} is not set:
			set {-r} to 10
		RpLoadRoleMeta({-r})
		add {-m-sal} to {rp::cash::%loop-player%}
		send action bar "&a💳 Зарплата (%{-m-clean}%): &e+$%{-m-sal}% &8| &7Баланс: &a$%{rp::cash::%loop-player%}%" to loop-player
		play sound entity.experience_orb.pickup to loop-player with volume 0.45 pitch 1.35

# =====================================================================
#  КОМАНДЫ И ГОРЯЧИЕ КЛАВИШИ ЭКРАНА
# =====================================================================

command /roles [text]:
	aliases: role, rp, job, jobs
	if arg-1 is "reset":
		if player does not have permission 2:
			send "&cНедостаточно прав для сброса ролей!" to player
			stop
		loop 10 times:
			clear {rp::holders::%loop-number%::*}
		loop all players:
			set {rp::role::%loop-player%} to 10
		send "&a[RP] Все лимиты и роли игроков сброшены до Мирного Жителя." to player
		play sound block.anvil.use to player with volume 0.5 pitch 1.2
		stop
	if arg-1 is "list":
		send "&8&m----------------------------------------" to player
		send "&b&lМЕГАПОЛИС RP &8— &fЗанятость городских ролей:" to player
		loop 9 times:
			set {-ri} to %loop-number%
			RpLoadRoleMeta({-ri})
			set {-cnt} to size of {rp::holders::%{-ri}%::*}
			if {-cnt} > 0:
				set {-who} to join {rp::holders::%{-ri}%::*} with ", "
				send " &8▪ %{-m-title}% &7(%{-cnt}%/%{-m-lim}%): &f%{-who}%" to player
			else:
				send " &8▪ %{-m-title}% &7(0/%{-m-lim}%): &8свободно" to player
		send "&8&m----------------------------------------" to player
		stop
	RpOpenMenu(player)

# Быстрый выбор ролей клавишами 1..9, 0, стрелками Up/Down и подтверждение Enter
on key 1 in screen "Биржа Ролей":
	RpSelectRole(player, 1)

on key 2 in screen "Биржа Ролей":
	RpSelectRole(player, 2)

on key 3 in screen "Биржа Ролей":
	RpSelectRole(player, 3)

on key 4 in screen "Биржа Ролей":
	RpSelectRole(player, 4)

on key 5 in screen "Биржа Ролей":
	RpSelectRole(player, 5)

on key 6 in screen "Биржа Ролей":
	RpSelectRole(player, 6)

on key 7 in screen "Биржа Ролей":
	RpSelectRole(player, 7)

on key 8 in screen "Биржа Ролей":
	RpSelectRole(player, 8)

on key 9 in screen "Биржа Ролей":
	RpSelectRole(player, 9)

on key 0 in screen "Биржа Ролей":
	RpSelectRole(player, 10)

on key up in screen "Биржа Ролей":
	set {-cur} to {-rp-sel::%player%}
	if {-cur} is not set:
		set {-cur} to 1
	remove 1 from {-cur}
	if {-cur} < 1:
		set {-cur} to 10
	RpSelectRole(player, {-cur})

on key down in screen "Биржа Ролей":
	set {-cur} to {-rp-sel::%player%}
	if {-cur} is not set:
		set {-cur} to 1
	add 1 to {-cur}
	if {-cur} > 10:
		set {-cur} to 1
	RpSelectRole(player, {-cur})

on key enter in screen "Биржа Ролей":
	RpJoinCurrent(player)

# --- Фракционные РП-команды ---
command /mayor <text>:
	if {rp::role::%player%} is not 1:
		send "&cЭта команда доступна только &6Мэру Города&c! Откройте &e/roles" to player
		stop
	broadcast "&6&l[🏛 УКАЗ МЭРА %player%] &e%args%"
	send title "&6&l🏛 УКАЗ МЭРА" with subtitle "&f%args%" to all players
	play sound block.bell.use to all players with volume 0.8 pitch 1.1

command /news <text>:
	if {rp::role::%player%} is not 9:
		send "&cПрямой эфир доступен только &dРепортёру СМИ&c! Откройте &e/roles" to player
		stop
	broadcast "&d&l[🎥 ПРЯМОЙ ЭФИР | %player%] &f%args%"
	play sound block.note_block.chime to all players with volume 0.7 pitch 1.4

command /ems <player>:
	if {rp::role::%player%} is not 4:
		send "&cРеанимация доступна только &cПарамедику (EMS)&c! Откройте &e/roles" to player
		stop
	if arg-1 is not set:
		send "&eИспользование: &f/ems <игрок>" to player
		stop
	heal arg-1
	feed arg-1
	give arg-1 effect regeneration for 8 seconds level 2
	spawn particle heart at arg-1 count 16
	play sound item.totem.use at arg-1 for players in radius 16 of arg-1 with volume 0.45 pitch 1.5
	send "&a❤ Вы оказали экстренную помощь игроку &f%arg-1%&a!" to player
	send "&a❤ Парамедик &f%player% &aоказал вам медицинскую помощь!" to arg-1

# =====================================================================
#  БАЗА ДАННЫХ 10 ГОРОДСКИХ РОЛЕЙ (ЕДИНЫЙ СПИСОК)
# =====================================================================

function RpInitMeta():
	set {rp::limit::1} to 1
	set {rp::limit::2} to 4
	set {rp::limit::3} to 2
	set {rp::limit::4} to 3
	set {rp::limit::5} to 2
	set {rp::limit::6} to 3
	set {rp::limit::7} to 3
	set {rp::limit::8} to 3
	set {rp::limit::9} to 2
	set {rp::limit::10} to 0

function RpLoadRoleMeta(r: number):
	if r is 1:
		set {-m-clean} to "Мэр Города"
		set {-m-title} to "&6&l👑 МЭР ГОРОДА"
		set {-m-faction} to "&6&l★ МЭРИЯ МЕГАПОЛИСА"
		set {-m-lim} to 1
		set {-m-sal} to 3500
		set {-m-skin} to "Notch"
		set {-m-pet} to "minecraft:iron_golem"
		set {-m-pet-nbt} to ""
		set {-m-helm} to "golden_helmet"
		set {-m-chest} to "chainmail_chestplate"
		set {-m-legs} to "chainmail_leggings"
		set {-m-boots} to "golden_boots"
		set {-m-hand} to "clock"
		set {-m-off} to "totem_of_undying"
		set {-m-k1} to "golden_helmet"
		set {-m-k2} to "clock"
		set {-m-k3} to "totem_of_undying"
		set {-m-k4} to "writable_book"
		set {-m-part} to "gold"
		set {-m-desc} to "&fГлава города и казны.%nl%&e• Указы: &f/mayor <текст>%nl%&e• Охрана и престиж%nl%&7• Сложность: &6★★★"
	else if r is 2:
		set {-m-clean} to "Офицер Полиции"
		set {-m-title} to "&9&l🛡 ОФИЦЕР ПОЛИЦИИ"
		set {-m-faction} to "&9&l★ ПОЛИЦИЯ (PD)"
		set {-m-lim} to 4
		set {-m-sal} to 2200
		set {-m-skin} to "jeb_"
		set {-m-pet} to "minecraft:wolf"
		set {-m-pet-nbt} to "{CollarColor:11b}"
		set {-m-helm} to "turtle_helmet"
		set {-m-chest} to "diamond_chestplate"
		set {-m-legs} to "iron_leggings"
		set {-m-boots} to "iron_boots"
		set {-m-hand} to "crossbow"
		set {-m-off} to "shield"
		set {-m-k1} to "crossbow"
		set {-m-k2} to "shield"
		set {-m-k3} to "diamond_chestplate"
		set {-m-k4} to "lead"
		set {-m-part} to "ice"
		set {-m-desc} to "&fПатруль улиц и закон.%nl%&b• Табельный арбалет%nl%&b• Служебный пёс PD%nl%&7• Сложность: &b★★☆"
	else if r is 3:
		set {-m-clean} to "Спецназ SWAT"
		set {-m-title} to "&b&l⚔ СПЕЦНАЗ SWAT"
		set {-m-faction} to "&b&l★ ШТУРМОВОЙ ОТРЯД"
		set {-m-lim} to 2
		set {-m-sal} to 2800
		set {-m-skin} to "Searge"
		set {-m-pet} to "minecraft:iron_golem"
		set {-m-pet-nbt} to ""
		set {-m-helm} to "netherite_helmet"
		set {-m-chest} to "netherite_chestplate"
		set {-m-legs} to "diamond_leggings"
		set {-m-boots} to "netherite_boots"
		set {-m-hand} to "diamond_sword"
		set {-m-off} to "shield"
		set {-m-k1} to "diamond_sword"
		set {-m-k2} to "shield"
		set {-m-k3} to "netherite_helmet"
		set {-m-k4} to "golden_apple"
		set {-m-part} to "ice"
		set {-m-desc} to "&fАнтитеррор и рейды.%nl%&b• Тяжёлая броня SWAT%nl%&b• Штурм особо опасных%nl%&7• Сложность: &c★★★"
	else if r is 4:
		set {-m-clean} to "Парамедик EMS"
		set {-m-title} to "&c&l✚ ПАРАМЕДИК (EMS)"
		set {-m-faction} to "&c&l★ ГОСПИТАЛЬ 24/7"
		set {-m-lim} to 3
		set {-m-sal} to 2000
		set {-m-skin} to "Alex"
		set {-m-pet} to "minecraft:allay"
		set {-m-pet-nbt} to ""
		set {-m-helm} to "iron_helmet"
		set {-m-chest} to "golden_chestplate"
		set {-m-legs} to "iron_leggings"
		set {-m-boots} to "golden_boots"
		set {-m-hand} to "golden_apple"
		set {-m-off} to "potion"
		set {-m-k1} to "golden_apple"
		set {-m-k2} to "glistering_melon_slice"
		set {-m-k3} to "honey_bottle"
		set {-m-k4} to "paper"
		set {-m-part} to "heart"
		set {-m-desc} to "&fСпасение жизней 24/7.%nl%&c• Лечение: &f/ems <ник>%nl%&c• Быстрый бег на вызов%nl%&7• Сложность: &a★☆☆"
	else if r is 5:
		set {-m-clean} to "Судья и Адвокат"
		set {-m-title} to "&e&l⚖ СУДЬЯ / АДВОКАТ"
		set {-m-faction} to "&e&l★ ВЕРХОВНЫЙ СУД"
		set {-m-lim} to 2
		set {-m-sal} to 2400
		set {-m-skin} to "Agnes"
		set {-m-pet} to "minecraft:villager"
		set {-m-pet-nbt} to "{VillagerData:{profession:\"minecraft:librarian\",level:5,type:\"minecraft:plains\"}}"
		set {-m-helm} to "golden_helmet"
		set {-m-chest} to "leather_chestplate"
		set {-m-legs} to "leather_leggings"
		set {-m-boots} to "golden_boots"
		set {-m-hand} to "book"
		set {-m-off} to "emerald"
		set {-m-k1} to "writable_book"
		set {-m-k2} to "book"
		set {-m-k3} to "bell"
		set {-m-k4} to "emerald"
		set {-m-part} to "gold"
		set {-m-desc} to "&fСудебные процессы РП.%nl%&e• Защита и приговоры%nl%&e• Залог и контракты%nl%&7• Сложность: &e★★☆"
	else if r is 6:
		set {-m-clean} to "Синдикат Мафии"
		set {-m-title} to "&4&l🕶 СИНДИКАТ МАФИИ"
		set {-m-faction} to "&4&l★ ТЕНЕВОЙ КАРТЕЛЬ"
		set {-m-lim} to 3
		set {-m-sal} to 2600
		set {-m-skin} to "Dinnerbone"
		set {-m-pet} to "minecraft:vindicator"
		set {-m-pet-nbt} to ""
		set {-m-helm} to "netherite_helmet"
		set {-m-chest} to "netherite_chestplate"
		set {-m-legs} to "netherite_leggings"
		set {-m-boots} to "netherite_boots"
		set {-m-hand} to "iron_sword"
		set {-m-off} to "emerald"
		set {-m-k1} to "iron_sword"
		set {-m-k2} to "netherite_chestplate"
		set {-m-k3} to "ender_pearl"
		set {-m-k4} to "emerald"
		set {-m-part} to "smoke"
		set {-m-desc} to "&fКонтроль районов.%nl%&4• Контрабанда и тень%nl%&4• Бонус силы и броня%nl%&7• Риск: &c★★★"
	else if r is 7:
		set {-m-clean} to "Уличный Гонщик"
		set {-m-title} to "&6&l🏍 УЛИЧНЫЙ ГОНЩИК"
		set {-m-faction} to "&6&l★ ДРИФТ-КЛУБ"
		set {-m-lim} to 3
		set {-m-sal} to 1950
		set {-m-skin} to "iskall85"
		set {-m-pet} to "minecraft:fox"
		set {-m-pet-nbt} to ""
		set {-m-helm} to "iron_helmet"
		set {-m-chest} to "leather_chestplate"
		set {-m-legs} to "chainmail_leggings"
		set {-m-boots} to "diamond_boots"
		set {-m-hand} to "carrot_on_a_stick"
		set {-m-off} to "firework_rocket"
		set {-m-k1} to "saddle"
		set {-m-k2} to "firework_rocket"
		set {-m-k3} to "diamond_boots"
		set {-m-k4} to "compass"
		set {-m-part} to "fire"
		set {-m-desc} to "&fНелегальные заезды.%nl%&6• Скорость II и драйв%nl%&6• Срочные доставки%nl%&7• Сложность: &e★★☆"
	else if r is 8:
		set {-m-clean} to "Автомеханик"
		set {-m-title} to "&6&l🔧 АВТОМЕХАНИК"
		set {-m-faction} to "&6&l★ ТЕХЦЕНТР ГОРОДА"
		set {-m-lim} to 3
		set {-m-sal} to 1800
		set {-m-skin} to "MHF_Villager"
		set {-m-pet} to "minecraft:villager"
		set {-m-pet-nbt} to "{VillagerData:{profession:\"minecraft:toolsmith\",level:5,type:\"minecraft:plains\"}}"
		set {-m-helm} to "leather_helmet"
		set {-m-chest} to "iron_chestplate"
		set {-m-legs} to "leather_leggings"
		set {-m-boots} to "iron_boots"
		set {-m-hand} to "iron_pickaxe"
		set {-m-off} to "redstone_torch"
		set {-m-k1} to "iron_pickaxe"
		set {-m-k2} to "anvil"
		set {-m-k3} to "redstone"
		set {-m-k4} to "iron_ingot"
		set {-m-part} to "spark"
		set {-m-desc} to "&fРемонт авто и сетей.%nl%&6• Тюнинг и механизмы%nl%&6• Эффект Спешки II%nl%&7• Сложность: &e★★☆"
	else if r is 9:
		set {-m-clean} to "Репортёр СМИ"
		set {-m-title} to "&d&l🎥 РЕПОРТЁР СМИ"
		set {-m-faction} to "&d&l★ КАНАЛ «CITY LIVE»"
		set {-m-lim} to 2
		set {-m-sal} to 1900
		set {-m-skin} to "Toast"
		set {-m-pet} to "minecraft:parrot"
		set {-m-pet-nbt} to "{Variant:1}"
		set {-m-helm} to "golden_helmet"
		set {-m-chest} to "leather_chestplate"
		set {-m-legs} to "leather_leggings"
		set {-m-boots} to "leather_boots"
		set {-m-hand} to "spyglass"
		set {-m-off} to "book"
		set {-m-k1} to "spyglass"
		set {-m-k2} to "writable_book"
		set {-m-k3} to "amethyst_shard"
		set {-m-k4} to "cookie"
		set {-m-part} to "magic"
		set {-m-desc} to "&fНовости и репортажи.%nl%&d• Эфир: &f/news <текст>%nl%&d• Камера и интервью%nl%&7• Сложность: &a★☆☆"
	else:
		set {-m-clean} to "Мирный Житель"
		set {-m-title} to "&a&l🏙 МИРНЫЙ ЖИТЕЛЬ"
		set {-m-faction} to "&a&l★ ГРАЖДАНИН ГОРОДА"
		set {-m-lim} to 0
		set {-m-sal} to 900
		set {-m-skin} to "Steve"
		set {-m-pet} to "minecraft:cat"
		set {-m-pet-nbt} to ""
		set {-m-helm} to "air"
		set {-m-chest} to "leather_chestplate"
		set {-m-legs} to "leather_leggings"
		set {-m-boots} to "leather_boots"
		set {-m-hand} to "bread"
		set {-m-off} to "emerald"
		set {-m-k1} to "bread"
		set {-m-k2} to "emerald"
		set {-m-k3} to "compass"
		set {-m-k4} to "apple"
		set {-m-part} to "spark"
		set {-m-desc} to "&fСвободная карьера РП.%nl%&a• Бизнес и торговля%nl%&a• Свободный вход (∞)%nl%&7• Сложность: &a★☆☆"

# =====================================================================
#  ОТКРЫТИЕ ПОЛНОЭКРАННОГО МЕНЮ: СЛЕВА ИНФО + 3D, СПРАВА ЕДИНЫЙ СПИСОК
# =====================================================================

function RpOpenMenu(p: player):
	RpInitMeta()
	if {rp::role::%p%} is not set:
		set {rp::role::%p%} to 10
	if {-rp-sel::%p%} is not set:
		set {-rp-sel::%p%} to {rp::role::%p%}
	if {-rp-skinmode::%p%} is not set:
		set {-rp-skinmode::%p%} to 0
	if {-rp-look::%p%} is not set:
		set {-rp-look::%p%} to 0
	if {rp::uisize::%p%} is not set:
		set {rp::uisize::%p%} to 2

	set {-curR} to {rp::role::%p%}
	RpLoadRoleMeta({-curR})
	set {-myRoleName} to {-m-title}

	open screen "Биржа Ролей" to p:
		# 0. ПОЛНОЭКРАННЫЙ ФОН (закрывает 100% экрана при любом разрешении)
		box at 0 0 size 2400x1400 anchor center color #F4060A14 gradient #FC0B1324 layer 0
		box at 0 0 size 320x480 anchor left color #1C38BDF8 gradient #00000000 horizontal blur layer 0
		box at 0 0 size 420x480 anchor right color #00000000 gradient #1CA855F7 horizontal blur layer 0

		# =================================================================
		# 1. СЛЕВА (anchor left): ИНФОРМАЦИЯ О РОЛИ И ЖИВОЙ 3D-ПРЕДПРОСМОТР
		# =================================================================
		box at 6 0 size 220x258 anchor left color #E60A1120 gradient #F2101C34 border #6638BDF8 border_width 1 radius 7 shadow blur layer 1 id rp_left_box

		# Шапка левой панели + кнопка подбора размера списка на весь экран
		box at 11 -113 size 210x22 anchor left color #FF1D4ED8 to #FF7C3AED horizontal radius 5 shadow layer 2
		text "&b&l✦ &f&lМЕГАПОЛИС RP" at 16 -118 anchor left scale 0.88 shadow layer 3
		button "⛶ Средн." at 167 -120 size 50x14 anchor left layer 5 id btn_uisize run RpCycleUiSize(player) keep open hover_sound ui.button.click hover_volume 0.35 hover_pitch 1.4 tooltip "&bМасштаб списка на весь экран:%nl%&7• Компакт (GUI Scale 4 / 480x270)%nl%&7• Средний (GUI Scale 3 / 640x360)%nl%&7• Полный HD (GUI Scale 2 / 960x540)"
		text "&7Вы: %{-myRoleName}% &8• &a$%{rp::cash::%p%}%" at 16 -107 anchor left scale 0.7 shadow layer 3 id rp_top_cur

		# Цветной градиентный баннер выбранной роли и полоса лимита слотов
		box at 11 -89 size 210x18 anchor left color #FFD97706 to #FF9333EA horizontal radius 4 shadow layer 2 id rp_banner
		text "&6&l👑 МЭР ГОРОДА" at 18 -89 anchor left scale 0.92 shadow layer 3 id rp_title
		text "&7Слоты: &e0 / 1 &8• &7Зарплата: &a$3 500" at 14 -74 anchor left scale 0.76 shadow layer 3 id rp_limit_txt
		bar "rp_limit_bar" value 0 max 1 at 12 -64 size 208x5 anchor left color #F59E0B background #AA070B14 text "" layer 3 tooltip "&7Заполненность лимита выбранной роли"

		# 3D-подиум персонажа со скином + 3D-спутник фракции + управление ракурсом
		box at 11 12 size 92x136 anchor left color #B0070C17 border #3A60A5FA radius 5 layer 2 id rp_stage_box
		box at 20 45 size 74x4 anchor left color #CC38BDF8 to #CCA855F7 horizontal radius 2 layer 2 id rp_podium

		player "Notch" at 16 -6 size 68x102 anchor left scale 39 look cursor walk helmet golden_helmet chestplate chainmail_chestplate leggings chainmail_leggings boots golden_boots weapon clock offhand totem_of_undying hover_scale 1.04 hover_sound item.armor.equip_gold hover_volume 0.35 hover_pitch 1.2 layer 4 id rp_hero tooltip "&e3D-предпросмотр персонажа%nl%&7Следит взглядом за курсором мыши!"
		entity "minecraft:iron_golem" at 71 22 size 28x36 anchor left spin right speed 36 hover_scale 1.12 hover_sound entity.experience_orb.pickup hover_volume 0.25 hover_pitch 1.4 layer 5 id rp_pet tooltip "&bСпутник фракции"

		button "🎭 Скин: Роль" at 15 56 size 84x12 anchor left layer 5 id btn_skinmode run RpToggleSkinMode(player) keep open hover_sound ui.button.click hover_volume 0.35 hover_pitch 1.35 tooltip "&bПереключить между фирменным скином роли%nl%&bи вашим собственным скином (%p%)!"
		button "👁" at 15 70 size 26x12 anchor left layer 5 run RpSetView(player, 0) keep open hover_sound block.note_block.hat hover_volume 0.3 hover_pitch 1.5 tooltip "&7Следить взглядом за курсором"
		button "↺" at 44 70 size 26x12 anchor left layer 5 run RpSetView(player, -1) keep open hover_sound block.note_block.hat hover_volume 0.3 hover_pitch 1.25 tooltip "&7Вращать модель влево"
		button "↻" at 73 70 size 26x12 anchor left layer 5 run RpSetView(player, 1) keep open hover_sound block.note_block.hat hover_volume 0.3 hover_pitch 1.25 tooltip "&7Вращать модель вправо"

		# Блок информации о роли, игроков на посту и стартового набора
		box at 107 12 size 114x136 anchor left color #B0070C17 border #2A38BDF8 radius 5 layer 2
		text "&6&l★ МЭРИЯ" at 113 -49 anchor left scale 0.72 shadow layer 3 id rp_faction_tag
		text "&8На посту: &aСвободно" at 113 -38 anchor left scale 0.68 shadow layer 3 id rp_holders_txt
		box at 112 -31 size 104x1 anchor left color #3338BDF8 layer 2
		text "&fОписание роли..." at 113 4 anchor left scale 0.72 shadow layer 3 id rp_desc

		text "&7Стартовый набор:" at 113 43 anchor left scale 0.72 shadow layer 3
		slot golden_helmet 1 at 114 62 anchor left layer 3 id rp_kit1 hover_sound item.armor.equip_chain hover_volume 0.35 hover_pitch 1.25
		slot clock 1 at 138 62 anchor left layer 3 id rp_kit2 hover_sound item.armor.equip_chain hover_volume 0.35 hover_pitch 1.35
		slot totem_of_undying 1 at 162 62 anchor left layer 3 id rp_kit3 hover_sound item.armor.equip_chain hover_volume 0.35 hover_pitch 1.45
		slot writable_book 1 at 186 62 anchor left layer 3 id rp_kit4 hover_sound item.armor.equip_chain hover_volume 0.35 hover_pitch 1.55

		# Кнопки вступления, увольнения и закрытия
		button "&a&l▶ ВСТУПИТЬ В РОЛЬ" at 11 94 size 210x18 anchor left layer 5 id btn_join run RpJoinCurrent(player) keep open hover_sound block.amethyst_block.chime hover_volume 0.55 hover_pitch 1.2 tooltip "&aПодтвердить выбор роли и получить форму%nl%&8(Горячая клавиша: Enter)"
		button "&eУволиться" at 11 115 size 102x13 anchor left layer 5 id btn_leave run RpLeaveRole(player) keep open hover_sound ui.button.click hover_volume 0.35 hover_pitch 0.95 tooltip "&eОсвободить слот и стать Мирным Жителем"
		button "&cЗакрыть (ESC)" at 119 115 size 102x13 anchor left layer 5 close hover_sound ui.button.click hover_volume 0.35 hover_pitch 0.85 tooltip "&7Закрыть меню ролей"

		# =================================================================
		# 2. СПРАВА (anchor right): ЕДИНЫЙ БОЛЬШОЙ СПИСОК РОЛЕЙ ВО ВЕСЬ ЭКРАН
		# =================================================================
		box at -6 0 size 402x346 anchor right color #E60A1120 gradient #F2101C34 border #6638BDF8 border_width 1 radius 7 shadow blur layer 1 id rp_right_box

		list vertical at -10 0 size 394x338 anchor right row_height 28 spacing 3 header "&b&l📋 ЕДИНЫЙ РЕЕСТР РОЛЕЙ МЕГАПОЛИСА  &7(Наведите курсор для превью)" align left background #66070B14 border #3338BDF8 select scrollbar layer 3 id rp_list:
			row "&6Мэр Города" item golden_helmet badge "VIP" badge_color #F59E0B right "&a0/1" hover_sound block.note_block.hat volume 0.4 pitch 1.15 on_hover RpHoverRole(player, 1) run RpSelectRole(player, 1) keep open
			row "&9Офицер Полиции" item shield badge "PD" badge_color #3B82F6 right "&b0/4" hover_sound block.note_block.hat volume 0.4 pitch 1.22 on_hover RpHoverRole(player, 2) run RpSelectRole(player, 2) keep open
			row "&bСпецназ SWAT" item netherite_chestplate badge "SWAT" badge_color #0EA5E9 right "&b0/2" hover_sound block.note_block.hat volume 0.4 pitch 1.28 on_hover RpHoverRole(player, 3) run RpSelectRole(player, 3) keep open
			row "&cПарамедик EMS" item golden_apple badge "EMS" badge_color #EF4444 right "&e0/3" hover_sound block.note_block.hat volume 0.4 pitch 1.35 on_hover RpHoverRole(player, 4) run RpSelectRole(player, 4) keep open
			row "&eСудья / Адвокат" item writable_book badge "СУД" badge_color #EAB308 right "&e0/2" hover_sound block.note_block.hat volume 0.4 pitch 1.18 on_hover RpHoverRole(player, 5) run RpSelectRole(player, 5) keep open
			row "&4Синдикат Мафии" item iron_sword badge "ТЕНЬ" badge_color #DC2626 right "&e0/3" hover_sound block.note_block.hat volume 0.4 pitch 0.9 on_hover RpHoverRole(player, 6) run RpSelectRole(player, 6) keep open
			row "&6Уличный Гонщик" item saddle badge "ДРАЙВ" badge_color #F97316 right "&e0/3" hover_sound block.note_block.hat volume 0.4 pitch 1.1 on_hover RpHoverRole(player, 7) run RpSelectRole(player, 7) keep open
			row "&6Автомеханик" item iron_pickaxe badge "ТЕХ" badge_color #EA580C right "&e0/3" hover_sound block.note_block.hat volume 0.4 pitch 1.05 on_hover RpHoverRole(player, 8) run RpSelectRole(player, 8) keep open
			row "&dРепортёр СМИ" item spyglass badge "LIVE" badge_color #A855F7 right "&d0/2" hover_sound block.note_block.hat volume 0.4 pitch 1.45 on_hover RpHoverRole(player, 9) run RpSelectRole(player, 9) keep open
			row "&aМирный Житель" item emerald badge "∞" badge_color #22C55E right "&a∞" hover_sound block.note_block.hat volume 0.4 pitch 1.25 on_hover RpHoverRole(player, 10) run RpSelectRole(player, 10) keep open

	play sound block.beacon.activate to p with volume 0.35 pitch 1.6
	RpRefreshAll(p, 1)

# =====================================================================
#  ПЕРЕКЛЮЧЕНИЕ МАСШТАБА СПИСКА НА ВЕСЬ ЭКРАН (480p / 640p / 960p)
# =====================================================================

function RpCycleUiSize(p: player):
	if {rp::uisize::%p%} is 1:
		set {rp::uisize::%p%} to 2
		gui float "&b⛶ Экран: Средний (640x360)" at 0 -80 color #38BDF8 for 18 ticks to p
	else if {rp::uisize::%p%} is 2:
		set {rp::uisize::%p%} to 3
		gui float "&d⛶ Экран: Полный HD (960x540)" at 0 -80 color #C084FC for 18 ticks to p
	else:
		set {rp::uisize::%p%} to 1
		gui float "&a⛶ Экран: Компакт (480x270)" at 0 -80 color #4ADE80 for 18 ticks to p
	play sound ui.button.click to p with volume 0.45 pitch 1.35
	RpApplyUiSize(p)

function RpApplyUiSize(p: player):
	set {-sz} to {rp::uisize::%p%}
	if {-sz} is 1:
		set text of screen "btn_uisize" for p to "⛶ Авто"
		set size of screen "rp_left_box" for p to 220 258
		set size of screen "rp_right_box" for p to 242 258
		set size of screen "rp_list" for p to 234 250
	else if {-sz} is 3:
		set text of screen "btn_uisize" for p to "⛶ FullHD"
		set size of screen "rp_left_box" for p to 220 524
		set size of screen "rp_right_box" for p to 722 524
		set size of screen "rp_list" for p to 714 516
	else:
		set text of screen "btn_uisize" for p to "⛶ Средн."
		set size of screen "rp_left_box" for p to 220 346
		set size of screen "rp_right_box" for p to 402 346
		set size of screen "rp_list" for p to 394 338

# =====================================================================
#  ЖИВОЕ НАВЕДЕНИЕ, ПЕРЕКЛЮЧЕНИЕ СКИНА, КАМЕРА И АНИМАЦИИ
# =====================================================================

function RpHoverRole(p: player, r: number):
	if p does not have screen "Биржа Ролей" open:
		stop
	if {-rp-sel::%p%} is r:
		stop
	set {-rp-sel::%p%} to r
	RpRefreshAll(p, 1)

function RpSelectRole(p: player, r: number):
	if p does not have screen "Биржа Ролей" open:
		stop
	set {-rp-sel::%p%} to r
	play sound ui.button.click to p with volume 0.45 pitch 1.3
	RpRefreshAll(p, 1)

function RpToggleSkinMode(p: player):
	if {-rp-skinmode::%p%} is 1:
		set {-rp-skinmode::%p%} to 0
		gui float "&e🎭 Скин: Образ роли" at 0 -75 color #FACC15 for 16 ticks to p
	else:
		set {-rp-skinmode::%p%} to 1
		gui float "&b👤 Ваш скин (%p%)" at 0 -75 color #38BDF8 for 16 ticks to p
	play sound item.armor.equip_leather to p with volume 0.6 pitch 1.3
	RpRefreshAll(p, 0)

function RpSetView(p: player, mode: number):
	set {-rp-look::%p%} to mode
	if mode is 0:
		set spin of screen "rp_hero" for p to 0
		set look of screen "rp_hero" for p to cursor
		gui float "&b👁 Слежение за курсором" at 0 -75 color #38BDF8 for 14 ticks to p
	else if mode < 0:
		set look of screen "rp_hero" for p to none
		set spin of screen "rp_hero" for p to left 48
		gui float "&d↺ Вращение влево" at 0 -75 color #C084FC for 14 ticks to p
	else:
		set look of screen "rp_hero" for p to none
		set spin of screen "rp_hero" for p to right 48
		gui float "&d↻ Вращение вправо" at 0 -75 color #C084FC for 14 ticks to p
	play sound ui.button.click to p with volume 0.4 pitch 1.4

# --- Обновление левой панели превью/инфо и правого единого списка ---
function RpRefreshAll(p: player, withAnim: number):
	if p does not have screen "Биржа Ролей" open:
		stop
	set {-sel} to {-rp-sel::%p%}
	if {-sel} is not set:
		set {-sel} to 1

	# Считаем занятость слотов всех лимитных ролей (1..9) + зарплату для правого текста строки
	loop 9 times:
		set {-ri} to %loop-number%
		RpLoadRoleMeta({-ri})
		set {-cnt::%{-ri}%} to size of {rp::holders::%{-ri}%::*}
		if {-cnt::%{-ri}%} >= {-m-lim}:
			set {-rtxt::%{-ri}%} to "&7$%{-m-sal}%  &c%{-cnt::%{-ri}%}%/%{-m-lim}%"
		else:
			set {-rtxt::%{-ri}%} to "&7$%{-m-sal}%  &a%{-cnt::%{-ri}%}%/%{-m-lim}%"

	set {-c1} to {-cnt::1}
	set {-c2} to {-cnt::2}
	set {-c3} to {-cnt::3}
	set {-c4} to {-cnt::4}
	set {-c5} to {-cnt::5}
	set {-c6} to {-cnt::6}
	set {-c7} to {-cnt::7}
	set {-c8} to {-cnt::8}
	set {-c9} to {-cnt::9}

	# Стрелка выбранной роли в едином списке справа
	loop 10 times:
		set {-ri} to %loop-number%
		if {-ri} is {-sel}:
			set {-pfx::%{-ri}%} to "&f&l▸ "
		else:
			set {-pfx::%{-ri}%} to ""

	# Загружаем метаданные выбранной роли для левой панели
	RpLoadRoleMeta({-sel})
	set {-curCnt} to size of {rp::holders::%{-sel}%::*}
	set {-myCurRole} to {rp::role::%p%}

	set {-previewSkin} to {-m-skin}
	set {-skinBtnLbl} to "🎭 Скин: Роль"
	if {-rp-skinmode::%p%} is 1:
		set {-previewSkin} to "%p%"
		set {-skinBtnLbl} to "👤 Вы: %p%"

	if {-m-lim} > 0:
		set {-limStr} to "&7Слоты: &e%{-curCnt}% / %{-m-lim}% &8• &7Зарплата: &a$%{-m-sal}%"
		set value of screen bar "rp_limit_bar" for p to {-curCnt}
		set max of screen bar "rp_limit_bar" for p to {-m-lim}
		if {-curCnt} > 0:
			set {-names} to join {rp::holders::%{-sel}%::*} with ", "
			set {-holdStr} to "&8Занято: &f%{-names}%"
		else:
			set {-holdStr} to "&8На посту: &aСвободно!"
	else:
		set {-limStr} to "&7Слоты: &aБез лимита (∞) &8• &7Пособие: &a$%{-m-sal}%"
		set value of screen bar "rp_limit_bar" for p to 1
		set max of screen bar "rp_limit_bar" for p to 4
		set {-holdStr} to "&8Открыто всем жителям"

	set {-joinLbl} to "&a&l▶ ВСТУПИТЬ В РОЛЬ"
	set {-joinDis} to 0
	if {-myCurRole} is {-sel}:
		set {-joinLbl} to "&b&l✓ ВЫ УЖЕ НА ЭТОЙ РОЛИ"
		set {-joinDis} to 1
	else if {-m-lim} > 0 and {-curCnt} >= {-m-lim}:
		set {-joinLbl} to "&c&l✖ ЛИМИТ ЗАНЯТ (%{-curCnt}%/%{-m-lim}%)"
		set {-joinDis} to 1

	# Обновляем элементы левой панели
	set text of screen "rp_faction_tag" for p to {-m-faction}
	set text of screen "rp_title" for p to {-m-title}
	set text of screen "rp_limit_txt" for p to {-limStr}
	set text of screen "rp_holders_txt" for p to {-holdStr}
	set text of screen "rp_desc" for p to {-m-desc}
	set text of screen "btn_skinmode" for p to {-skinBtnLbl}
	set text of screen "btn_join" for p to {-joinLbl}
	set item of screen "rp_kit1" for p to {-m-k1} 1
	set item of screen "rp_kit2" for p to {-m-k2} 1
	set item of screen "rp_kit3" for p to {-m-k3} 1
	set item of screen "rp_kit4" for p to {-m-k4} 1

	if {-joinDis} is 1:
		disable screen "btn_join" for p
	else:
		enable screen "btn_join" for p

	# Цветной градиентный баннер выбранной роли слева
	if {-sel} is 1:
		update screen "Биржа Ролей" for p:
			box at 11 -89 size 210x18 anchor left color #FFD97706 to #FF9333EA horizontal radius 4 shadow layer 2 id rp_banner
	else if {-sel} is 2:
		update screen "Биржа Ролей" for p:
			box at 11 -89 size 210x18 anchor left color #FF1D4ED8 to #FF0284C7 horizontal radius 4 shadow layer 2 id rp_banner
	else if {-sel} is 3:
		update screen "Биржа Ролей" for p:
			box at 11 -89 size 210x18 anchor left color #FF0369A1 to #FF1E3A8A horizontal radius 4 shadow layer 2 id rp_banner
	else if {-sel} is 4:
		update screen "Биржа Ролей" for p:
			box at 11 -89 size 210x18 anchor left color #FFDC2626 to #FFEC4899 horizontal radius 4 shadow layer 2 id rp_banner
	else if {-sel} is 5:
		update screen "Биржа Ролей" for p:
			box at 11 -89 size 210x18 anchor left color #FFCA8A04 to #FFB45309 horizontal radius 4 shadow layer 2 id rp_banner
	else if {-sel} is 6:
		update screen "Биржа Ролей" for p:
			box at 11 -89 size 210x18 anchor left color #FF7F1D1D to #FF312E81 horizontal radius 4 shadow layer 2 id rp_banner
	else if {-sel} is 7:
		update screen "Биржа Ролей" for p:
			box at 11 -89 size 210x18 anchor left color #FFEA580C to #FFDC2626 horizontal radius 4 shadow layer 2 id rp_banner
	else if {-sel} is 8:
		update screen "Биржа Ролей" for p:
			box at 11 -89 size 210x18 anchor left color #FFC2410C to #FFCA8A04 horizontal radius 4 shadow layer 2 id rp_banner
	else if {-sel} is 9:
		update screen "Биржа Ролей" for p:
			box at 11 -89 size 210x18 anchor left color #FF7E22CE to #FF2563EB horizontal radius 4 shadow layer 2 id rp_banner
	else:
		update screen "Биржа Ролей" for p:
			box at 11 -89 size 210x18 anchor left color #FF15803D to #FF0F766E horizontal radius 4 shadow layer 2 id rp_banner

	# Обновляем 3D-персонажа и спутника слева + единый большой список ролей справа
	set {-lkMode} to {-rp-look::%p%}
	update screen "Биржа Ролей" for p:
		player "%{-previewSkin}%" at 16 -6 size 68x102 anchor left scale 39 look cursor walk helmet %{-m-helm}% chestplate %{-m-chest}% leggings %{-m-legs}% boots %{-m-boots}% weapon %{-m-hand}% offhand %{-m-off}% hover_scale 1.04 hover_sound item.armor.equip_gold hover_volume 0.35 hover_pitch 1.2 layer 4 id rp_hero tooltip "&e%{-m-clean}%%nl%&7Двигайте мышкой — персонаж следит взглядом!"
		entity "%{-m-pet}%" at 71 22 size 28x36 anchor left spin right speed 36 nbt "%{-m-pet-nbt}%" hover_scale 1.12 hover_sound entity.experience_orb.pickup hover_volume 0.25 hover_pitch 1.4 layer 5 id rp_pet tooltip "&bСпутник фракции: &f%{-m-clean}%"
		list vertical at -10 0 size 394x338 anchor right row_height 28 spacing 3 header "&b&l📋 ЕДИНЫЙ РЕЕСТР РОЛЕЙ МЕГАПОЛИСА  &7(Наведите курсор для превью)" align left background #66070B14 border #3338BDF8 select scrollbar layer 3 id rp_list:
			row "%{-pfx::1}%&6Мэр Города" item golden_helmet badge "VIP" badge_color #F59E0B right "%{-rtxt::1}%" bar %{-c1}%/1 bar_color #F59E0B tooltip "&6&lМэр Города &7[1 слот | $3 500]%nl%&7Указы /mayor и управление городом" hover_sound block.note_block.hat volume 0.4 pitch 1.15 on_hover RpHoverRole(player, 1) run RpSelectRole(player, 1) keep open
			row "%{-pfx::2}%&9Офицер Полиции" item shield badge "PD" badge_color #3B82F6 right "%{-rtxt::2}%" bar %{-c2}%/4 bar_color #3B82F6 tooltip "&9&lОфицер Полиции &7[4 слота | $2 200]%nl%&7Патруль, щит и охрана порядка" hover_sound block.note_block.hat volume 0.4 pitch 1.22 on_hover RpHoverRole(player, 2) run RpSelectRole(player, 2) keep open
			row "%{-pfx::3}%&bСпецназ SWAT" item netherite_chestplate badge "SWAT" badge_color #0EA5E9 right "%{-rtxt::3}%" bar %{-c3}%/2 bar_color #0EA5E9 tooltip "&b&lСпецназ SWAT &7[2 слота | $2 800]%nl%&7Штурмовой отряд и тяжёлая броня" hover_sound block.note_block.hat volume 0.4 pitch 1.28 on_hover RpHoverRole(player, 3) run RpSelectRole(player, 3) keep open
			row "%{-pfx::4}%&cПарамедик EMS" item golden_apple badge "EMS" badge_color #EF4444 right "%{-rtxt::4}%" bar %{-c4}%/3 bar_color #EF4444 tooltip "&c&lПарамедик EMS &7[3 слота | $2 000]%nl%&7Экстренная реанимация /ems <ник>" hover_sound block.note_block.hat volume 0.4 pitch 1.35 on_hover RpHoverRole(player, 4) run RpSelectRole(player, 4) keep open
			row "%{-pfx::5}%&eСудья / Адвокат" item writable_book badge "СУД" badge_color #EAB308 right "%{-rtxt::5}%" bar %{-c5}%/2 bar_color #EAB308 tooltip "&e&lСудья / Адвокат &7[2 слота | $2 400]%nl%&7Судебные заседания, защита и залог" hover_sound block.note_block.hat volume 0.4 pitch 1.18 on_hover RpHoverRole(player, 5) run RpSelectRole(player, 5) keep open
			row "%{-pfx::6}%&4Синдикат Мафии" item iron_sword badge "ТЕНЬ" badge_color #DC2626 right "%{-rtxt::6}%" bar %{-c6}%/3 bar_color #DC2626 tooltip "&4&lСиндикат Мафии &7[3 слота | $2 600]%nl%&7Теневой картель, броня и сила" hover_sound block.note_block.hat volume 0.4 pitch 0.9 on_hover RpHoverRole(player, 6) run RpSelectRole(player, 6) keep open
			row "%{-pfx::7}%&6Уличный Гонщик" item saddle badge "ДРАЙВ" badge_color #F97316 right "%{-rtxt::7}%" bar %{-c7}%/3 bar_color #F97316 tooltip "&6&lУличный Гонщик &7[3 слота | $1 950]%nl%&7Ночной дрифт-клуб и скорость" hover_sound block.note_block.hat volume 0.4 pitch 1.1 on_hover RpHoverRole(player, 7) run RpSelectRole(player, 7) keep open
			row "%{-pfx::8}%&6Автомеханик" item iron_pickaxe badge "ТЕХ" badge_color #EA580C right "%{-rtxt::8}%" bar %{-c8}%/3 bar_color #EA580C tooltip "&6&lАвтомеханик &7[3 слота | $1 800]%nl%&7Тюнинг, починка и инженерные сети" hover_sound block.note_block.hat volume 0.4 pitch 1.05 on_hover RpHoverRole(player, 8) run RpSelectRole(player, 8) keep open
			row "%{-pfx::9}%&dРепортёр СМИ" item spyglass badge "LIVE" badge_color #A855F7 right "%{-rtxt::9}%" bar %{-c9}%/2 bar_color #A855F7 tooltip "&d&lРепортёр СМИ &7[2 слота | $1 900]%nl%&7Прямые эфиры на весь город /news" hover_sound block.note_block.hat volume 0.4 pitch 1.45 on_hover RpHoverRole(player, 9) run RpSelectRole(player, 9) keep open
			row "%{-pfx::10}%&aМирный Житель" item emerald badge "∞" badge_color #22C55E right "&7$900  &a∞" tooltip "&a&lМирный Житель &7[Без лимита | $900]%nl%&7Свободная жизнь, бизнес и старт карьеры" hover_sound block.note_block.hat volume 0.4 pitch 1.25 on_hover RpHoverRole(player, 10) run RpSelectRole(player, 10) keep open

	# Применяем сохранённый полноэкранный масштаб списка игрока
	RpApplyUiSize(p)

	if {-lkMode} < 0:
		set look of screen "rp_hero" for p to none
		set spin of screen "rp_hero" for p to left 48
	else if {-lkMode} > 0:
		set look of screen "rp_hero" for p to none
		set spin of screen "rp_hero" for p to right 48

	# Обновляем строку статуса игрока в левой шапке
	RpLoadRoleMeta({-myCurRole})
	set text of screen "rp_top_cur" for p to "&7Вы: %{-m-title}% &8• &a$%{rp::cash::%p%}%"
	RpLoadRoleMeta({-sel})

	# Плавные 60 FPS анимации 3D-превью слева при наведении!
	if withAnim is 1:
		set position of screen "rp_hero" for p to 16 1
		animate screen "rp_hero" for p to 16 -6 in 5 ticks
		set position of screen "rp_podium" for p to 12 45
		animate screen "rp_podium" for p to 20 45 in 4 ticks
		gui particle "%{-m-part}%" count 12 at -160 20 spread 24 to p

# =====================================================================
#  ВСТУПЛЕНИЕ В РОЛЬ И УВОЛЬНЕНИЕ
# =====================================================================

function RpJoinCurrent(p: player):
	set {-r} to {-rp-sel::%p%}
	if {-r} is not set:
		set {-r} to 10
	RpLoadRoleMeta({-r})

	if {rp::role::%p%} is {-r}:
		play sound entity.villager.no to p with volume 0.5 pitch 1.1
		gui float "&eВы уже на этой роли!" at 0 -65 color #FACC15 for 18 ticks to p
		stop

	set {-cnt} to size of {rp::holders::%{-r}%::*}
	if {-m-lim} > 0 and {-cnt} >= {-m-lim}:
		play sound entity.villager.no to p with volume 0.65 pitch 0.85
		gui particle "smoke" count 14 at -140 70 spread 22 to p
		gui float "&c✖ Слоты заняты (%{-cnt}%/%{-m-lim}%)!" at 0 -65 color #EF4444 for 22 ticks to p
		send action bar "&cНа роли %{-m-clean}% нет свободных мест (%{-cnt}%/%{-m-lim}%)!" to p
		stop

	# Освобождаем предыдущий лимитный слот игрока
	set {-old} to {rp::role::%p%}
	if {-old} is set and {-old} >= 1 and {-old} <= 9:
		if {rp::holders::%{-old}%::*} contains "%p%":
			remove "%p%" from {rp::holders::%{-old}%::*}

	# Записываем игрока на новую роль
	set {rp::role::%p%} to {-r}
	if {-r} >= 1 and {-r} <= 9:
		if {rp::holders::%{-r}%::*} does not contain "%p%":
			add "%p%" to {rp::holders::%{-r}%::*}

	# Выдаём форму, стартовые предметы и бонус роли
	clear effects of p
	if {-m-helm} is not "air":
		set slot helmet of p to {-m-helm}
	set slot chestplate of p to {-m-chest}
	set slot leggings of p to {-m-legs}
	set slot boots of p to {-m-boots}
	give p {-m-k1} 1
	give p {-m-k2} 1

	if {-r} is 1:
		give p effect hero_of_the_village for 600 seconds level 1
	else if {-r} is 2:
		give p effect resistance for 600 seconds level 1
	else if {-r} is 3:
		give p effect resistance for 600 seconds level 1
		give p effect strength for 600 seconds level 1
	else if {-r} is 4:
		give p effect speed for 600 seconds level 1
	else if {-r} is 6:
		give p effect strength for 600 seconds level 1
	else if {-r} is 7:
		give p effect speed for 600 seconds level 2
	else if {-r} is 8:
		give p effect haste for 600 seconds level 2

	# Звуки, вспышки частиц и анимация персонажа
	play sound ui.toast.challenge_complete to p with volume 0.55 pitch 1.25
	play sound entity.player.levelup to p with volume 0.7 pitch 1.3
	gui particle "gold" count 26 at -160 0 spread 40 to p
	gui particle "%{-m-part}%" count 18 at 0 -60 spread 34 to p
	gui float "&a&l✓ %{-m-clean}%!" at 0 -70 color #4ADE80 for 26 ticks to p

	play animation "scriptcraft:wave" on p for all players first person fade 4
	send title "%{-m-title}%" with subtitle "&aВы заступили на новую роль!" to p
	broadcast "&8[&bМЕГАПОЛИС RP&8] &f%p% &7вступил в роль: %{-m-title}%"

	loop all players:
		if loop-player has screen "Биржа Ролей" open:
			RpRefreshAll(loop-player, 0)

function RpLeaveRole(p: player):
	set {-old} to {rp::role::%p%}
	if {-old} is not set or {-old} is 10:
		play sound entity.villager.no to p with volume 0.5 pitch 1.1
		gui float "&7Вы уже Мирный Житель" at 0 -65 color #94A3B8 for 16 ticks to p
		stop
	if {rp::holders::%{-old}%::*} contains "%p%":
		remove "%p%" from {rp::holders::%{-old}%::*}
	set {rp::role::%p%} to 10
	clear effects of p
	play sound block.beacon.deactivate to p with volume 0.5 pitch 1.2
	gui particle "smoke" count 14 at -160 16 spread 26 to p
	gui float "&eВы уволились со службы" at 0 -65 color #FACC15 for 20 ticks to p
	send "&e[RP] Вы покинули должность и снова стали &aМирным Жителем&e." to p
	loop all players:
		if loop-player has screen "Биржа Ролей" open:
			RpRefreshAll(loop-player, 0)
