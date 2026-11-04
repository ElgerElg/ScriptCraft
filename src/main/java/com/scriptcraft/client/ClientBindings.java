package com.scriptcraft.client;

import com.scriptcraft.registry.Bindings;
import com.scriptcraft.registry.ScriptRegistries;
import com.scriptcraft.resources.AssetBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Клиент: таблица привязок с сервера (BindingsPacket).
 * Свойства и имена блоков/предметов применяются сразу; если поменялась раскладка моделей по слотам —
 * одна перезагрузка ресурсов (после докачки ресурсов сервера, чтобы не перезагружать дважды).
 */
@OnlyIn(Dist.CLIENT)
public final class ClientBindings {
    private ClientBindings() {
    }

    public static void apply(Bindings.Snapshot snapshot) {
        Bindings.set(snapshot);
        ElementRenderer.clearItemCache(); // слоты экранов/HUD ("item example:ruby") — заново через привязки
        ClientResources.onBindings(AssetBindings.update(snapshot));
        rebuildCreativeTab();
    }

    /** Вкладка "ScriptCraft" в творческом режиме: только привязанное сейчас. */
    private static void rebuildCreativeTab() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        boolean op = mc.options.operatorItemsTab().get() && player.canUseGameMasterBlocks();
        CreativeModeTab tab = ScriptRegistries.TAB.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(player.connection.enabledFeatures(), op,
                player.level().registryAccess()));
    }

    /** Выход с удалённого сервера: привязки этого сервера больше не действуют (в одиночной игре их сбросит сервер). */
    public static void onLoggingOut() {
        if (Minecraft.getInstance().getSingleplayerServer() == null) {
            Bindings.set(Bindings.Snapshot.EMPTY);
        }
        ElementRenderer.clearItemCache();
    }
}
