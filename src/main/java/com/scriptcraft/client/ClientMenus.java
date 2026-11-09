package com.scriptcraft.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/**
 * Ванильный экран -> имя для on open. Свой ScriptScreen сюда не попадает:
 * его зовущий код отсекает раньше, иначе отмена inventory закрыла бы и меню скрипта.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientMenus {
    private ClientMenus() {
    }

    @Nullable
    public static String name(Screen screen) {
        return switch (screen.getClass().getSimpleName()) {
            case "InventoryScreen", "CreativeModeInventoryScreen" -> "inventory";
            case "ContainerScreen" -> "chest";
            case "ShulkerBoxScreen" -> "shulker";
            case "BarrelScreen" -> "barrel";
            case "CraftingScreen" -> "crafting";
            case "FurnaceScreen", "BlastFurnaceScreen", "SmokerScreen" -> "furnace";
            case "AnvilScreen" -> "anvil";
            case "EnchantmentScreen" -> "enchanting";
            case "BrewingStandScreen" -> "brewing";
            case "MerchantScreen" -> "villager";
            case "HopperScreen" -> "hopper";
            case "HorseInventoryScreen" -> "horse";
            case "BeaconScreen" -> "beacon";
            case "SmithingScreen" -> "smithing";
            case "GrindstoneScreen" -> "grindstone";
            case "LoomScreen" -> "loom";
            case "CartographyTableScreen" -> "cartography";
            case "StonecutterScreen" -> "stonecutter";
            default -> null;
        };
    }
}
