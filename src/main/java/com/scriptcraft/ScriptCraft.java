package com.scriptcraft;

import com.mojang.logging.LogUtils;
import com.scriptcraft.command.EffectCommands;
import com.scriptcraft.config.ScriptCraftConfig;
import com.scriptcraft.event.ServerEvents;
import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.registry.BindingStore;
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
 * 2) читаем и разбираем все скрипты (первая загрузка логики);
 * 3) регистрируем фиксированный пул блоков/предметов scriptcraft:dyn_block_N / dyn_item_N — одинаковый
 *    на клиенте и сервере, независимо от скриптов на диске (см. ScriptRegistries);
 * 4) подключаем DeferredRegister к шине мода — Forge сам зарегистрирует их в RegisterEvent.
 *
 * Реестры блоков и предметов "замораживаются" после загрузки, поэтому "define block / define item" не
 * регистрируют ничего нового: сервер во время работы привязывает их к свободным слотам пула и рассылает
 * таблицу игрокам. Новые блоки появляются после /sc reload без перезапуска сервера и клиентов.
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

        // 2. Первая загрузка скриптов: события/команды готовы сразу. Define отсюда закрепляют "подсказки" слотов
        //    (scriptcraft/bindings.json) — нужны data-паку и переназначению блоков старых миров до загрузки мира.
        ScriptLoader.initialLoad();
        BindingStore.prepare(ScriptLoader.getProgram());

        // 3. Пул блоков/предметов (одинаковый у всех) и вкладка творческого режима.
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
