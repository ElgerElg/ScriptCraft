package com.scriptcraft;

import com.mojang.logging.LogUtils;
import com.scriptcraft.event.ServerEvents;
import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.registry.ScriptRegistries;
import com.scriptcraft.script.ScriptLoader;
import com.scriptcraft.script.ScriptPaths;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Главный класс мода ScriptCraft.
 *
 * Порядок запуска важен:
 * 1) создаём папку scripts (и копируем примеры при первом запуске);
 * 2) ПРЕДВАРИТЕЛЬНО читаем и разбираем все скрипты — ещё ДО регистрации реестров;
 * 3) из найденных в скриптах "define block / define item" создаём записи в DeferredRegister;
 * 4) подключаем DeferredRegister к шине мода — Forge сам зарегистрирует их в RegisterEvent.
 *
 * ВАЖНО: реестры блоков и предметов "замораживаются" после загрузки. Поэтому НОВЫЕ блоки и предметы
 * из скриптов появятся только после ПЕРЕЗАПУСКА игры/сервера. А логика (события, команды, сообщения, экраны)
 * перезагружается "на горячую" командой /scriptcraft reload.
 */
@Mod(ScriptCraft.MODID)
public class ScriptCraft {
    /** Идентификатор мода. Должен совпадать с mod_id в gradle.properties. */
    public static final String MODID = "scriptcraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ScriptCraft() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 1. Папка скриптов (<папка игры>/scripts). При первом запуске туда копируются примеры.
        ScriptPaths.ensureScriptsFolder();

        // 2. Предварительный разбор скриптов: нужен, чтобы узнать объявленные блоки/предметы до заморозки реестров.
        //    Заодно это первая загрузка логики, поэтому события/команды готовы сразу.
        ScriptLoader.initialLoad();

        // 3. Регистрируем блоки/предметы из скриптов. Делается ОДИН раз за запуск.
        ScriptRegistries.registerFromScripts(ScriptLoader.getProgram());
        ScriptRegistries.register(modBus);

        // 4. Сетевой канал (сервер -> клиент: открыть экран из скрипта).
        ModNetwork.register();

        // 5. Игровые события (вход игрока, регистрация команд и т.д.) — на общей шине Forge.
        MinecraftForge.EVENT_BUS.register(ServerEvents.class);

        LOGGER.info("[ScriptCraft] Мод загружен. Папка скриптов: {}", ScriptPaths.scriptsDir());
    }
}
