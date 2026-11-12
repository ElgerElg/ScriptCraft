package com.scriptcraft.registry;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.scriptcraft.script.parser.ItemDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.TierSortingRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Общая основа предметов из скриптов: слотов пула ({@link PoolItem}) и настоящих ({@link RealItem}).
 * Размер стака, редкость, огнестойкость и имя — из define "на лету" ({@link Bindings}); NBT не используется.
 * TODO: событие "on use" из скрипта, анимации (аддон PlayerAnimator / GeckoLib).
 */
public abstract class ScriptItem extends Item {
    protected ScriptItem() {
        super(new Properties().stacksTo(64));
    }

    @Nullable
    public abstract ItemDefinition definition();

    /** Имя, когда define нет. */
    protected abstract Component unboundName();

    @Override
    public String getDescriptionId() {
        ItemDefinition def = definition();
        return def == null ? super.getDescriptionId() : "item." + def.namespace() + "." + def.path();
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return getDescriptionId();
    }

    @Override
    public Component getName(ItemStack stack) {
        ItemDefinition def = definition();
        if (def == null) {
            return unboundName();
        }
        return def.displayName() != null ? Component.literal(def.displayName()) : Component.translatable(getDescriptionId());
    }

    /** Подробные подсказки (F3+H) у слота: рядом с техническим id показываем id из скрипта. */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ItemDefinition def = definition();
        if (flag.isAdvanced() && def != null && this instanceof PoolItem) {
            tooltip.add(ScriptBlockItem.scriptIdLine(def.id()));
        }
    }

    /** Forge спрашивает размер стака у предмета для каждого стака — так он меняется без перезапуска. */
    @Override
    public int getMaxStackSize(ItemStack stack) {
        ItemDefinition def = definition();
        return def == null ? 64 : def.clampedStackSize();
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        ItemDefinition def = definition();
        return def == null ? Rarity.COMMON : def.rarityValue();
    }

    @Override
    public boolean isFireResistant() {
        ItemDefinition def = definition();
        return def != null && def.fireResistant();
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        ItemDefinition def = definition();
        return def == null ? 0 : Math.max(0, def.durability());
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return getMaxDamage(stack) > 0;
    }

    @Override
    public int getEnchantmentValue() {
        ItemDefinition def = definition();
        if (def == null || !def.wearableTool()) {
            return 0;
        }
        if (def.fishingRod()) {
            return 1;
        }
        if ("shears".equals(def.type())) {
            return 1;
        }
        return def.tierValue().getEnchantmentValue();
    }

    @Override
    public boolean isEdible() {
        ItemDefinition def = definition();
        return def != null && def.food();
    }

    @Override
    public FoodProperties getFoodProperties(ItemStack stack, @Nullable LivingEntity entity) {
        ItemDefinition def = definition();
        if (def == null || !def.food()) {
            return null;
        }
        FoodProperties.Builder food = new FoodProperties.Builder()
                .nutrition(Math.max(0, def.nutrition()))
                .saturationMod(def.saturation());
        if (def.alwaysEat()) {
            food.alwaysEat();
        }
        return food.build();
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return isEdible() ? 32 : super.getUseDuration(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return isEdible() ? UseAnim.EAT : super.getUseAnimation(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemDefinition def = definition();
        if (def != null && def.fishingRod()) {
            return castRod(level, player, hand);
        }
        return super.use(level, player, hand);
    }

    /** Заброс и подсечка, как у ванильной удочки. */
    private InteractionResultHolder<ItemStack> castRod(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.fishing != null) {
            if (!level.isClientSide) {
                int wear = player.fishing.retrieve(stack);
                stack.hurtAndBreak(wear, player, p -> p.broadcastBreakEvent(hand));
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE,
                    SoundSource.NEUTRAL, 1f, 0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f));
            player.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW,
                    SoundSource.NEUTRAL, 0.5f, 0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f));
            if (!level.isClientSide) {
                int luck = EnchantmentHelper.getFishingLuckBonus(stack);
                int speed = EnchantmentHelper.getFishingSpeedBonus(stack);
                level.addFreshEntity(new FishingHook(player, level, luck, speed));
            }
            player.awardStat(Stats.ITEM_USED.get(this));
            player.gameEvent(GameEvent.ITEM_INTERACT_START);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        ItemDefinition def = definition();
        return def == null ? super.getDestroySpeed(stack, state) : def.destroySpeed(state);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        ItemDefinition def = definition();
        if (def == null || !def.digs(state)) {
            return false;
        }
        if (!def.digger() || "sword".equals(def.type())) {
            return true;
        }
        return TierSortingRegistry.isCorrectTierForDrops(def.tierValue(), state);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        ItemDefinition def = definition();
        if (def != null && def.digs(state) && !level.isClientSide && state.getDestroySpeed(level, pos) != 0f) {
            stack.hurtAndBreak(def.blockWear(), entity, living -> living.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
        return true;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        ItemDefinition def = definition();
        if (def == null || slot != EquipmentSlot.MAINHAND || !def.digger()) {
            return super.getDefaultAttributeModifiers(slot);
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> mods = ImmutableMultimap.builder();
        mods.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier",
                def.attackDamage(), AttributeModifier.Operation.ADDITION));
        mods.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier",
                def.attackSpeed(), AttributeModifier.Operation.ADDITION));
        return mods.build();
    }
}
