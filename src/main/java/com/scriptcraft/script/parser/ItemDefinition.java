package com.scriptcraft.script.parser;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import org.jetbrains.annotations.Nullable;

/**
 * Предмет из "define item <id>:".
 *
 * @param id            полный id "namespace:path"
 * @param stackSize     размер стака 1..64
 * @param fireResistant не сгорает в лаве (как незерит)
 * @param rarity        common, uncommon, rare, epic (цвет названия)
 */
public record ItemDefinition(String id, @Nullable String displayName, int stackSize, boolean fireResistant, String rarity) {

    public String namespace() {
        return id.substring(0, id.indexOf(':'));
    }

    public String path() {
        return id.substring(id.indexOf(':') + 1);
    }

    public Item.Properties toProperties() {
        Item.Properties props = new Item.Properties().stacksTo(Math.max(1, Math.min(64, stackSize)));
        if (fireResistant) {
            props = props.fireResistant();
        }
        Rarity r = switch (rarity) {
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
        return props.rarity(r);
    }
}
