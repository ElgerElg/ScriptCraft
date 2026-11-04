package com.scriptcraft.registry;

import com.scriptcraft.lang.Messages;
import com.scriptcraft.script.parser.BlockDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Блок-слот пула (scriptcraft:dyn_block_N). Сам по себе ничего не умеет: всё берёт из define, привязанного
 * к слоту прямо сейчас ({@link Bindings}). Поэтому /sc reload меняет прочность, звук, имя "на горячую".
 *
 * Свет — свойство состояния LIGHT (0..15): движок освещения кэширует свечение по состоянию блока,
 * поэтому значение записывается в состояние при установке. Уже стоящие блоки сохраняют старый свет.
 * Привязки нет (define удалён/выключен) — блок остаётся в мире и называется "свободный слот".
 * TODO: события блока из скриптов (on click / on break), форма (VoxelShape), BlockEntity.
 */
public class PoolBlock extends Block {
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);

    private final int slot;

    public PoolBlock(int slot) {
        // Базовые свойства — как у камня; реальные значения подставляют переопределения ниже.
        super(Properties.of().strength(1.5f, 6f).lightLevel(state -> state.getValue(LIGHT)));
        this.slot = slot;
        registerDefaultState(stateDefinition.any().setValue(LIGHT, 0));
    }

    public int slot() {
        return slot;
    }

    /** Текущее описание из скрипта или null, если слот свободен. */
    @Nullable
    public BlockDefinition definition() {
        return Bindings.current().block(slot);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIGHT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockDefinition def = definition();
        return defaultBlockState().setValue(LIGHT, def == null ? 0 : def.light());
    }

    /**
     * Блок поставлен НЕ игроком (/setblock, /fill, структура, скрипт) — у него состояние по умолчанию (light=0).
     * Подставляем свет из define; повторный вызов onPlace уже ничего не меняет (значение совпадает).
     */
    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        BlockDefinition def = definition();
        if (!level.isClientSide && def != null && state.getValue(LIGHT) != def.light()) {
            level.setBlock(pos, state.setValue(LIGHT, def.light()), Block.UPDATE_ALL);
        }
    }

    /** Ключ перевода берётся из id скрипта: block.example.ruby_block (lang-файлы ресурсов работают как раньше). */
    @Override
    public String getDescriptionId() {
        BlockDefinition def = definition();
        return def == null ? super.getDescriptionId() : "block." + def.namespace() + "." + def.path();
    }

    @Override
    public MutableComponent getName() {
        BlockDefinition def = definition();
        if (def == null) {
            return Component.literal(Messages.get("pool.free_block", slot));
        }
        // name: "..." из скрипта важнее перевода.
        return def.displayName() != null ? Component.literal(def.displayName()) : Component.translatable(getDescriptionId());
    }

    /** Скорость ломания — формула ванили/Forge, но с прочностью и "нужным инструментом" из скрипта. */
    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        BlockDefinition def = definition();
        if (def == null) {
            return super.getDestroyProgress(state, player, level, pos);
        }
        float hardness = def.hardness();
        if (hardness < 0) {
            return 0f; // как у бедрока: не ломается
        }
        if (hardness == 0) {
            return 1f;
        }
        int divider = canHarvestBlock(state, level, pos, player) ? 30 : 100;
        return player.getDigSpeed(state, pos) / hardness / divider;
    }

    /** Выпадет ли дроп: requires_tool -> нужен подходящий инструмент (теги mineable/... и уровня инструмента). */
    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        BlockDefinition def = definition();
        if (def == null || !def.requiresTool()) {
            return true;
        }
        return player.getMainHandItem().isCorrectToolForDrops(state);
    }

    @Override
    public float getExplosionResistance() {
        BlockDefinition def = definition();
        return def == null ? super.getExplosionResistance() : def.resistance();
    }

    @Override
    @SuppressWarnings("deprecation")
    public SoundType getSoundType(BlockState state) {
        BlockDefinition def = definition();
        return def == null ? super.getSoundType(state) : def.soundType();
    }

    /**
     * Дроп: лут-таблица <namespace>:blocks/<path> из scriptcraft/resources/data (как у обычного блока),
     * а если её нет — сам блок.
     */
    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        BlockDefinition def = definition();
        if (def != null) {
            ServerLevel level = builder.getLevel();
            MinecraftServer server = level.getServer();
            ResourceLocation tableId = ResourceLocation.tryBuild(def.namespace(), "blocks/" + def.path());
            if (tableId != null) {
                LootTable table = server.getLootData().getLootTable(tableId);
                if (table != LootTable.EMPTY) {
                    LootParams params = builder.withParameter(LootContextParams.BLOCK_STATE, state).create(LootContextParamSets.BLOCK);
                    return table.getRandomItems(params);
                }
            }
        }
        return List.of(new ItemStack(asItem()));
    }
}
