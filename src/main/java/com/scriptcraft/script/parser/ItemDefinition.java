package com.scriptcraft.script.parser;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * Предмет из "define item &lt;id&gt;:".
 *
 * @param type        item, food, sword, pickaxe, axe, shovel, hoe, fishing_rod, shears
 * @param nutrition   сколько голода восстанавливает еда (полушашки: 2 = одна ножка)
 * @param saturation  множитель насыщения, как в ванили (хлеб 0.6)
 * @param alwaysEat   еда съедается и на полном голоде
 * @param tier        wood, stone, iron, gold, diamond, netherite
 * @param durability  прочность; 0 — не ломается
 */
public record ItemDefinition(String id, @Nullable String displayName, int stackSize, boolean fireResistant, String rarity,
                             String type, int nutrition, float saturation, boolean alwaysEat, String tier, int durability) {
    public static final Set<String> TYPES = Set.of(
            "item", "food", "sword", "pickaxe", "axe", "shovel", "hoe", "fishing_rod", "shears");

    public String namespace() {
        return id.substring(0, id.indexOf(':'));
    }

    public String path() {
        return id.substring(id.indexOf(':') + 1);
    }

    public int clampedStackSize() {
        return Math.max(1, Math.min(64, stackSize));
    }

    public Rarity rarityValue() {
        return switch (rarity) {
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
    }

    public boolean food() {
        return "food".equals(type);
    }

    public boolean fishingRod() {
        return "fishing_rod".equals(type);
    }

    /** Меч, кирка, топор, лопата, мотыга. */
    public boolean digger() {
        return switch (type) {
            case "sword", "pickaxe", "axe", "shovel", "hoe" -> true;
            default -> false;
        };
    }

    public boolean wearableTool() {
        return digger() || fishingRod() || "shears".equals(type);
    }

    public Tiers tierValue() {
        return switch (tier) {
            case "wood", "wooden" -> Tiers.WOOD;
            case "stone" -> Tiers.STONE;
            case "gold", "golden" -> Tiers.GOLD;
            case "diamond" -> Tiers.DIAMOND;
            case "netherite" -> Tiers.NETHERITE;
            default -> Tiers.IRON;
        };
    }

    /** Урон в руке без базовой единицы игрока: как у ванильного инструмента этого уровня. */
    public double attackDamage() {
        double bonus = tierValue().getAttackDamageBonus();
        return switch (type) {
            case "sword" -> bonus + 3;
            case "axe" -> bonus + 6;
            case "shovel" -> bonus + 1.5;
            case "pickaxe" -> bonus + 1;
            case "hoe" -> bonus - 2;
            default -> 0;
        };
    }

    public double attackSpeed() {
        return switch (type) {
            case "sword" -> -2.4;
            case "axe" -> -3.1;
            case "shovel" -> -3.0;
            case "pickaxe" -> -2.8;
            case "hoe" -> switch (tier) {
                case "wood", "wooden", "gold", "golden" -> -3.0;
                case "stone" -> -2.0;
                case "diamond" -> 0;
                case "netherite" -> 0;
                default -> -1.0;
            };
            default -> 0;
        };
    }

    /** Урон прочности за сломанный блок. */
    public int blockWear() {
        return "sword".equals(type) ? 2 : 1;
    }

    public boolean digs(BlockState state) {
        return switch (type) {
            case "pickaxe" -> state.is(BlockTags.MINEABLE_WITH_PICKAXE);
            case "axe" -> state.is(BlockTags.MINEABLE_WITH_AXE);
            case "shovel" -> state.is(BlockTags.MINEABLE_WITH_SHOVEL);
            case "hoe" -> state.is(BlockTags.MINEABLE_WITH_HOE);
            case "sword" -> state.is(BlockTags.SWORD_EFFICIENT);
            case "shears" -> state.is(BlockTags.WOOL) || state.is(BlockTags.LEAVES)
                    || state.is(BlockTags.WOOL_CARPETS) || state.is(Blocks.COBWEB);
            default -> false;
        };
    }

    public float destroySpeed(BlockState state) {
        if (!digs(state)) {
            return 1f;
        }
        if ("sword".equals(type)) {
            return 15f;
        }
        if ("shears".equals(type)) {
            return state.is(BlockTags.WOOL) ? 5f : 15f;
        }
        return tierValue().getSpeed();
    }

    /** Прочность по умолчанию, если в скрипте её не написали. */
    public static int defaultDurability(String type, String tier) {
        if ("fishing_rod".equals(type)) {
            return 64;
        }
        if ("shears".equals(type)) {
            return 238;
        }
        if (switch (type) {
            case "sword", "pickaxe", "axe", "shovel", "hoe" -> true;
            default -> false;
        }) {
            return switch (tier) {
                case "wood", "wooden" -> 59;
                case "stone" -> 131;
                case "gold", "golden" -> 32;
                case "diamond" -> 1561;
                case "netherite" -> 2031;
                default -> 250;
            };
        }
        return 0;
    }

    /** Синонимы type из скрипта или null, если слово неизвестно. */
    @Nullable
    public static String normalizeType(String raw) {
        String v = raw.replace("\"", "").strip().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (v) {
            case "item", "normal", "basic" -> "item";
            case "food", "eat", "edible" -> "food";
            case "sword" -> "sword";
            case "pickaxe", "pick" -> "pickaxe";
            case "axe" -> "axe";
            case "shovel", "spade" -> "shovel";
            case "hoe" -> "hoe";
            case "fishing_rod", "fishing", "rod" -> "fishing_rod";
            case "shears", "shear", "scissors" -> "shears";
            default -> null;
        };
    }

    @Nullable
    public static String normalizeTier(String raw) {
        String v = raw.replace("\"", "").strip().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "wood", "wooden" -> "wood";
            case "stone" -> "stone";
            case "iron" -> "iron";
            case "gold", "golden" -> "gold";
            case "diamond" -> "diamond";
            case "netherite" -> "netherite";
            default -> null;
        };
    }
}
