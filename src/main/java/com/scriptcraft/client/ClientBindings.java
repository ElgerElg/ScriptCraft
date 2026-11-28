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
        GuiEntityRenderer.invalidateCache(); // обновляем 3D-превью кастомных сущностей в экранах и Студии
        boolean newlyShaped = registerDynamicItemModels();
        boolean layoutChanged = AssetBindings.update(snapshot);
        boolean mustBake = layoutChanged || forceModelBake || newlyShaped;
        if (mustBake) {
            boolean ru = !"en".equalsIgnoreCase(ClientUiSettings.language());
            ClientLoadScreenRenderer.setReloadReason(
                    hotOnline ? "Горячая регистрация и запекание новых modid:item / modid:block..."
                            : "Запекание моделей кастомных предметов и блоков сервера...",
                    hotOnline ? "Hot-registering & baking new modid:item / modid:block..."
                            : "Baking custom server block & item models..."
            );
        }
        ClientResources.onBindings(mustBake);
        rebuildCreativeTab();
        if (hotOnline && mustBake && Minecraft.getInstance().levelRenderer != null) {
            Minecraft.getInstance().levelRenderer.allChanged();
        }
        rememberIds(snapshot);
    }

    /**
     * Регистрирует все настоящие скриптовые предметы и предметы-блоки в {@link net.minecraft.client.renderer.ItemModelShaper}
     * (ForgeItemModelShaper заполняется только один раз в конструкторе ItemRenderer при старте игры, поэтому без
     * явной регистрации динамически созданные modid:item рендерились бы чёрно-фиолетовыми кубами MissingModel!).
     */
    public static boolean registerDynamicItemModels() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getItemRenderer() == null) {
            return false;
        }
        var shaper = mc.getItemRenderer().getItemModelShaper();
        var missing = shaper.getModelManager().getMissingModel();
        boolean anyMissingBefore = false;

        for (String id : ScriptRegistries.realBlockIds()) {
            net.minecraft.world.item.Item bi = ScriptRegistries.realBlockItem(id);
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
            if (bi != null && rl != null) {
                var cur = shaper.getItemModel(bi);
                if (cur == null || cur == missing) {
                    anyMissingBefore = true;
                }
                shaper.register(bi, new net.minecraft.client.resources.model.ModelResourceLocation(rl, "inventory"));
            }
        }
        for (String id : ScriptRegistries.realItemIds()) {
            net.minecraft.world.item.Item it = ScriptRegistries.realItem(id);
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
            if (it != null && rl != null) {
                var cur = shaper.getItemModel(it);
                if (cur == null || cur == missing) {
                    anyMissingBefore = true;
                }
                shaper.register(it, new net.minecraft.client.resources.model.ModelResourceLocation(rl, "inventory"));
            }
        }
        shaper.rebuildCache();
        return anyMissingBefore;
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
        ClientLoadScreenRenderer.resetJoinState();
    }
}
