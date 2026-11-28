package com.scriptcraft.client;

import com.scriptcraft.registry.Bindings;
import com.scriptcraft.registry.ScriptRegistries;
import com.scriptcraft.resources.AssetBindings;
import com.scriptcraft.lang.Messages;
import com.scriptcraft.resources.DefineCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Клиент: таблица привязок с сервера (BindingsPacket).
 * Свойства и имена блоков/предметов применяются сразу; если поменялась раскладка моделей по слотам —
 * одна перезагрузка ресурсов (после докачки ресурсов сервера, чтобы не перезагружать дважды).
 * Блоки во временных слотах — сообщение "перезапустите клиент": после перезапуска клиента (и сервера) они
 * получат настоящие id.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientBindings {
    private ClientBindings() {
    }

    /** id, о которых уже сказали "перезапустите клиент" в этой сессии игры. */
    private static final Set<String> TOLD = new HashSet<>();

    public static void apply(Bindings.Snapshot snapshot) {
        boolean injectedOnHandshake = com.scriptcraft.registry.DynamicRegistryManager.consumeClientInjectedFlag();
        applyInternal(snapshot, injectedOnHandshake, false);
    }

    public static void applyHot(Bindings.Snapshot snapshot, boolean anyNewRegistered) {
        applyInternal(snapshot, anyNewRegistered, true);
    }

    private static void applyInternal(Bindings.Snapshot snapshot, boolean forceModelBake, boolean hotOnline) {
        Bindings.set(snapshot);
        ElementRenderer.clearItemCache(); // слоты экранов/HUD ("item example:ruby") — заново через привязки
        boolean layoutChanged = AssetBindings.update(snapshot);
        if (forceModelBake) {
            ClientLoadScreenRenderer.setReloadReason(
                    hotOnline ? "Горячая регистрация и запекание новых modid:item / modid:block..."
                            : "Запекание моделей кастомных предметов и блоков сервера...",
                    hotOnline ? "Hot-registering & baking new modid:item / modid:block..."
                            : "Baking custom server block & item models..."
            );
        }
        ClientResources.onBindings(layoutChanged || forceModelBake);
        rebuildCreativeTab();
        if (hotOnline && forceModelBake && Minecraft.getInstance().levelRenderer != null) {
            Minecraft.getInstance().levelRenderer.allChanged();
        }
        rememberIds(snapshot);
    }

    /**
     * Сохраняет известные id в локальный список кэша. Никакого перезапуска игры больше не требуется:
     * все настоящие modid:id прописываются на лету через Mixin при входе и по /sc reload!
     */
    private static void rememberIds(Bindings.Snapshot snapshot) {
        List<String> blocks = new ArrayList<>(snapshot.realBlocks().keySet());
        blocks.addAll(snapshot.blockSlots().keySet());
        List<String> items = new ArrayList<>(snapshot.realItems().keySet());
        items.addAll(snapshot.itemSlots().keySet());
        if (Minecraft.getInstance().getSingleplayerServer() == null) {
            DefineCache.replace(blocks, items);
        } else {
            DefineCache.add(blocks, items);
        }
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
