package com.scriptcraft.client.geo;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;

/**
 * Мягкий мост определения установленного мода GeckoLib 4.
 * Наше ядро GeoRenderer рендерит все модели .geo.json и анимации .animation.json горячей перезагрузкой напрямую из
 * scriptcraft/cache/assets/, а при установленном geckolib дополнительно синхронизирует кэш и показывает статус в Студии.
 */
@OnlyIn(Dist.CLIENT)
public final class GeckoLibBridge {
    private GeckoLibBridge() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("geckolib");
    }

    public static String versionText() {
        return ModList.get().getModContainerById("geckolib")
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("builtin");
    }
}
