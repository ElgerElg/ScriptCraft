package com.scriptcraft.client;

import com.scriptcraft.registry.Bindings;
import com.scriptcraft.registry.ScriptRegistries;
import com.scriptcraft.resources.AssetBindings;
import com.scriptcraft.lang.Messages;
import com.scriptcraft.resources.DefineCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
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
        Bindings.set(snapshot);
        ElementRenderer.clearItemCache(); // слоты экранов/HUD ("item example:ruby") — заново через привязки
        ClientResources.onBindings(AssetBindings.update(snapshot));
        rebuildCreativeTab();
        rememberIds(snapshot);
    }

    /**
     * Все id сервера — в кэш define (при следующем запуске игры они станут настоящими записями клиента).
     * Блоки во временных слотах, которых у этого клиента нет настоящими, — сообщение в чат (один раз за сессию).
     */
    private static void rememberIds(Bindings.Snapshot snapshot) {
        List<String> blocks = new ArrayList<>(snapshot.realBlocks().keySet());
        blocks.addAll(snapshot.blockSlots().keySet());
        List<String> items = new ArrayList<>(snapshot.realItems().keySet());
        items.addAll(snapshot.itemSlots().keySet());
        DefineCache.add(blocks, items);

        List<String> temporary = new ArrayList<>();
        for (String id : snapshot.blockSlots().keySet()) {
            if (TOLD.add(id)) {
                temporary.add(id);
            }
        }
        for (String id : snapshot.itemSlots().keySet()) {
            if (TOLD.add(id)) {
                temporary.add(id);
            }
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (!temporary.isEmpty() && player != null) {
            player.sendSystemMessage(Component.literal(Messages.get("bindings.restart_client", String.join(", ", temporary)))
                    .withStyle(ChatFormatting.YELLOW));
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
