package com.scriptcraft.registry;

import com.scriptcraft.script.parser.BlockDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Общая основа блоков из скриптов: и слотов пула (scriptcraft:dyn_block_N, {@link PoolBlock}), и "настоящих"
 * блоков с id из скрипта (example:ruby_block, {@link RealBlock}). Свойства берутся из define "на лету"
 * ({@link Bindings}), поэтому /sc reload меняет прочность, звук, имя без перезапуска.
 *
 * Набор свойств состояния у ВСЕХ блоков скриптов одинаковый и не зависит от define: LIGHT (0..15) и FACING
 * (6 сторон). Причина: Forge при входе на сервер синхронизирует только номера блоков, а номера СОСТОЯНИЙ каждая
 * сторона считает сама по своему набору свойств. Если бы у example:ruby_block на сервере был facing, а у клиента
 * (из устаревшего кэша) — нет, все номера состояний после него разъехались бы и мир отображался бы мусором.
 * Одинаковый набор свойств делает регистрацию по одному лишь id безопасной. Поворачивается ли блок — решает
 * define (facing: none | horizontal | all); у неповоротных facing всегда north.
 *
 * Свет — свойство состояния: движок освещения кэширует свечение по состоянию, поэтому значение
 * записывается при установке (и в onPlace — для /setblock, /fill, структур).
 */
public abstract class ScriptBlock extends Block {
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    protected ScriptBlock() {
        // Базовые свойства — как у камня; реальные значения подставляют переопределения ниже.
        super(Properties.of().strength(1.5f, 6f).lightLevel(state -> state.getValue(LIGHT)));
        registerDefaultState(stateDefinition.any().setValue(LIGHT, 0).setValue(FACING, Direction.NORTH));
    }

    /** Текущее описание из скрипта или null (define удалён/выключен, слот свободен). */
    @Nullable
    public abstract BlockDefinition definition();

    /** Имя, когда define нет. */
    protected abstract MutableComponent unboundName();

    /** Лут-таблица, когда define нет (null — дроп самим блоком). */
    @Nullable
    protected abstract ResourceLocation fallbackLootTable();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIGHT, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockDefinition def = definition();
        BlockState state = defaultBlockState().setValue(LIGHT, def == null ? 0 : def.light());
        if (def == null || !def.rotatable()) {
            return state;
        }
        // Лицевая сторона смотрит на игрока — как у печи (horizontal) и раздатчика (all).
        Direction facing = BlockDefinition.FACING_ALL.equals(def.facing())
                ? context.getNearestLookingDirection().getOpposite()
                : context.getHorizontalDirection().getOpposite();
        return state.setValue(FACING, facing);
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

    /** Поворот структурой / командой: только у поворотных блоков (horizontal не уходит вверх/вниз сам по себе). */
    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        BlockDefinition def = definition();
        return def != null && def.rotatable() ? state.setValue(FACING, rotation.rotate(state.getValue(FACING))) : state;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        BlockDefinition def = definition();
        return def != null && def.rotatable() ? state.rotate(mirror.getRotation(state.getValue(FACING))) : state;
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
            return unboundName();
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
     * а если её нет — сам блок (обычный предмет без NBT).
     */
    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        BlockDefinition def = definition();
        ResourceLocation tableId = def != null ? ResourceLocation.tryBuild(def.namespace(), "blocks/" + def.path())
                : fallbackLootTable();
        if (tableId != null) {
            ServerLevel level = builder.getLevel();
            MinecraftServer server = level.getServer();
            LootTable table = server.getLootData().getLootTable(tableId);
            if (table != LootTable.EMPTY) {
                LootParams params = builder.withParameter(LootContextParams.BLOCK_STATE, state).create(LootContextParamSets.BLOCK);
                return table.getRandomItems(params);
            }
        }
        return List.of(new ItemStack(asItem()));
    }
}
