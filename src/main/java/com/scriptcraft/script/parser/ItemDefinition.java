package com.scriptcraft.script.parser;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * Предмет или 3D-броня из "define item <id>:" / "define armor <id>:".
 */
public record ItemDefinition(String id, @Nullable String displayName, int stackSize, boolean fireResistant, String rarity,
                             String type, int nutrition, float saturation, boolean alwaysEat, String tier, int durability,
                             int defense, float toughness,
                             String geoModel, String geoTexture, String geoAnimation,
                             String idleAnimation, float geoScale) {
    public static final Set<String> TYPES = Set.of(
            "item", "food", "sword", "pickaxe", "axe", "shovel", "hoe", "fishing_rod", "shears",
            "helmet", "chestplate", "leggings", "boots");

    public ItemDefinition(String id, @Nullable String displayName, int stackSize, boolean fireResistant, String rarity,
                          String type, int nutrition, float saturation, boolean alwaysEat, String tier, int durability) {
        this(id, displayName, stackSize, fireResistant, rarity, type, nutrition, saturation, alwaysEat, tier, durability,
                0, 0f, "", "", "", "idle", 1.0f);
    }

    public boolean isGeo() {
        return geoModel != null && !geoModel.isBlank();
    }

    public boolean isArmor() {
        return armorSlot() != null;
    }

    @Nullable
    public EquipmentSlot armorSlot() {
        return switch (type) {
            case "helmet" -> EquipmentSlot.HEAD;
            case "chestplate" -> EquipmentSlot.CHEST;
            case "leggings" -> EquipmentSlot.LEGS;
            case "boots" -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    public String namespace() {
        return id.substring(0, id.indexOf(':'));
    }

    public String path() {
        return id.substring(id.indexOf(':') + 1);
    }

    public int clampedStackSize() {
        if (isArmor()) {
            return 1;
        }
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

    public boolean digger() {
        return switch (type) {
            case "sword", "pickaxe", "axe", "shovel", "hoe" -> true;
            default -> false;
        };
    }

    public boolean wearableTool() {
        return digger() || fishingRod() || "shears".equals(type) || isArmor();
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

    public static int defaultDefense(String type, String tier) {
        return switch (type) {
            case "helmet" -> switch (tier) {
                case "wood" -> 1;
                case "gold", "stone" -> 2;
                case "diamond", "netherite" -> 3;
                default -> 2;
            };
            case "chestplate" -> switch (tier) {
                case "wood" -> 3;
                case "gold", "stone" -> 5;
                case "diamond", "netherite" -> 8;
                default -> 6;
            };
            case "leggings" -> switch (tier) {
                case "wood" -> 2;
                case "gold" -> 3;
                case "stone" -> 4;
                case "diamond", "netherite" -> 6;
                default -> 5;
            };
            case "boots" -> switch (tier) {
                case "wood", "gold", "stone" -> 1;
                case "diamond", "netherite" -> 3;
                default -> 2;
            };
            default -> 0;
        };
    }

    public static int defaultDurability(String type, String tier) {
        if ("fishing_rod".equals(type)) {
            return 64;
        }
        if ("shears".equals(type)) {
            return 238;
        }
        if (switch (type) {
            case "helmet", "chestplate", "leggings", "boots" -> true;
            default -> false;
        }) {
            int base = switch (type) {
                case "helmet" -> 11;
                case "chestplate" -> 16;
                case "leggings" -> 15;
                default -> 13;
            };
            int mul = switch (tier) {
                case "wood", "wooden" -> 5;
                case "gold", "golden" -> 7;
                case "stone" -> 15;
                case "diamond" -> 33;
                case "netherite" -> 37;
                default -> 15;
            };
            return base * mul;
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

    @Nullable
    public static String normalizeType(String raw) {
        String v = raw.replace("\"", "").strip().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (v) {
            case "item", "normal", "basic", "weapon", "gun", "prop" -> "item";
            case "food", "eat", "edible" -> "food";
            case "sword" -> "sword";
            case "pickaxe", "pick" -> "pickaxe";
            case "axe" -> "axe";
            case "shovel", "spade" -> "shovel";
            case "hoe" -> "hoe";
            case "fishing_rod", "fishing", "rod" -> "fishing_rod";
            case "shears", "shear", "scissors" -> "shears";
            case "helmet", "head", "hat", "mask", "visor" -> "helmet";
            case "chestplate", "chest", "vest", "jacket", "body", "torso", "wings", "backpack" -> "chestplate";
            case "leggings", "legs", "pants" -> "leggings";
            case "boots", "feet", "shoes" -> "boots";
            default -> null;
        };
    }

    @Nullable
    public static String normalizeTier(String raw) {
        String v = raw.replace("\"", "").strip().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "wood", "wooden", "leather" -> "wood";
            case "stone", "chain", "chainmail" -> "stone";
            case "iron" -> "iron";
            case "gold", "golden" -> "gold";
            case "diamond" -> "diamond";
            case "netherite" -> "netherite";
            default -> null;
        };
    }
}
