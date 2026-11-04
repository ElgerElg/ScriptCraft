package com.scriptcraft.resources;

import com.scriptcraft.ModPaths;
import com.scriptcraft.ScriptCraft;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.file.Path;

/**
 * Встроенные паки мода (регистрируются один раз при запуске, шина мода):
 * - CLIENT_RESOURCES: scriptcraft/cache/ (там лежит assets/, полученный с сервера). Пак обязательный,
 *   закреплён сверху и СКРЫТ из меню ресурспаков (Forge Pack.Info hidden=true) — игрок его не видит и не
 *   может выключить; никакого ванильного запроса "скачать ресурспак сервера" нет. Плюс сгенерированные
 *   модели слотов пула блоков/предметов ({@link GeneratedAssetsPack}).
 * - SERVER_DATA: scriptcraft/resources/ (там data/) — лут-таблицы, теги, рецепты. Только на сервере.
 *   id из скриптов в JSON заменяются на id слотов пула ({@link RewritingDataPack}).
 * Содержимое папок перечитывается при каждой перезагрузке ресурсов (F3+T, /reload).
 */
public final class ResourcePacks {
    private ResourcePacks() {
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            add(event, "scriptcraft_cache", ModPaths.cache(), PackType.CLIENT_RESOURCES, "ScriptCraft: server resources");
        } else if (event.getPackType() == PackType.SERVER_DATA) {
            add(event, "scriptcraft_data", ModPaths.resources(), PackType.SERVER_DATA, "ScriptCraft: scriptcraft/resources/data");
        }
    }

    private static void add(AddPackFindersEvent event, String id, Path root, PackType type, String title) {
        Component name = Component.literal(title);
        Pack.Info info = new Pack.Info(name,
                SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA),
                SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES),
                FeatureFlagSet.of(),
                type == PackType.CLIENT_RESOURCES); // hidden: клиентский пак не показываем в меню
        Pack pack = Pack.create(id, name, true, packId -> type == PackType.CLIENT_RESOURCES
                        ? new GeneratedAssetsPack(packId, root) : new RewritingDataPack(packId, root),
                info, type, Pack.Position.TOP, true, PackSource.BUILT_IN);
        event.addRepositorySource(consumer -> consumer.accept(pack));
        ScriptCraft.LOGGER.debug("[ScriptCraft] Встроенный пак {} -> {}", id, root);
    }
}
