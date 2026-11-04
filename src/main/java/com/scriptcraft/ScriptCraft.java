package com.scriptcraft;

import com.mojang.logging.LogUtils;
import com.scriptcraft.command.EffectCommands;
import com.scriptcraft.config.ScriptCraftConfig;
import com.scriptcraft.event.ServerEvents;
import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.registry.ScriptRegistries;
import com.scriptcraft.resources.ResourcePacks;
import com.scriptcraft.script.ScriptLoader;
import com.scriptcraft.script.ScriptPaths;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Главный класс мода ScriptCraft.
 *
 * Порядок запуска важен:
 * 1) создаём папку scriptcraft/ (scripts, variables, resources, cache; перенос старой scripts/; примеры);
 * 2) ПРЕДВАРИТЕЛЬНО читаем и разбираем все скрипты — ещё ДО регистрации реестров;
 * 3) из найденных в скриптах "define block / define item" создаём записи в DeferredRegister;
 * 4) подключаем DeferredRegister к шине мода — Forge сам зарегистрирует их в RegisterEvent.
 *
 * ВАЖНО: реестры блоков и предметов "замораживаются" после загрузки. Поэтому НОВЫЕ блоки и предметы
 * из скриптов появятся только после ПЕРЕЗАПУСКА игры/сервера. А логика (события, команды, сообщения, экраны)
 * перезагружается "на горячую" командой /scriptcraft reload (синонимы /sc и /script).
 *
 * Конфиг (scriptcraft/config.toml) Forge загружает ПОСЛЕ конструктора мода, поэтому
 * во время предварительного разбора действуют значения по умолчанию (язык en, стандартные лимиты).
 */
@Mod(ScriptCraft.MODID)
public class ScriptCraft {
    /** Идентификатор мода. Должен совпадать с mod_id в gradle.properties. */
    public static final String MODID = "scriptcraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ScriptCraft() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 1. Папка scriptcraft/ в папке игры/сервера. Создаётся ДО регистрации конфига, потому что
        //    конфиг лежит в ней (scriptcraft/config.toml). Старая папка scripts/ переносится сюда.
        ModPaths.ensureLayout();

        // 1a. Конфиг: язык сообщений, лимиты выполнения, переменные, ресурсы, эффект-команды.
        //    Forge разрешает имя файла относительно config/, поэтому "../scriptcraft/config.toml".
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ScriptCraftConfig.SPEC, ModPaths.CONFIG_FILE);

        // 2. Предварительный разбор скриптов: нужен, чтобы узнать объявленные блоки/предметы до заморозки реестров.
        //    Заодно это первая загрузка логики, поэтому события/команды готовы сразу.
        ScriptLoader.initialLoad();

        // 3. Регистрируем блоки/предметы из скриптов (свой DeferredRegister на каждый namespace). ОДИН раз за запуск.
        ScriptRegistries.registerFromScripts(ScriptLoader.getProgram());
        ScriptRegistries.register(modBus);

        // 4. Сетевой канал: экраны из скриптов и синхронизация ресурсов.
        ModNetwork.register();

        // 5. Встроенные паки: скрытый клиентский (кэш ресурсов сервера) и серверный (scriptcraft/resources/data).
        modBus.register(ResourcePacks.class);

        // 6. Игровые события (вход игрока, команды, тики) и эффект-команды в чате — на общей шине Forge.
        MinecraftForge.EVENT_BUS.register(ServerEvents.class);
        MinecraftForge.EVENT_BUS.register(EffectCommands.class);

        LOGGER.info("[ScriptCraft] Мод загружен. Папка скриптов: {}", ScriptPaths.scriptsDir());
    }
}
