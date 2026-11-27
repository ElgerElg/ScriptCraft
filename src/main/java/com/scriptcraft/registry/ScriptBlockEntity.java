package com.scriptcraft.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Блок-сущность для 3D Geo-блоков ScriptCraft ({@code define block ... model: ...}):
 * хранит активную анимацию, режим повтора, скорость и синхронизирует их с клиентами.
 */
public class ScriptBlockEntity extends BlockEntity {
    private String activeAnim = "";
    private int loopMode = 0; // 0 = once, 1 = loop, 2 = hold
    private float speed = 1.0f;
    private int animSerial = 0;

    public transient long clientStartTick = -1L;
    public transient int clientLastSeenSerial = 0;

    public ScriptBlockEntity(BlockPos pos, BlockState state) {
        super(ScriptRegistries.SCRIPT_BLOCK_ENTITY.get(), pos, state);
    }

    public void triggerAnimation(String animName, int loop, float animSpeed) {
        this.activeAnim = animName == null ? "" : animName;
        this.loopMode = loop;
        this.speed = Math.max(0.05f, animSpeed);
        this.animSerial++;
        setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public void applyClientSync(String animName, int loop, float animSpeed) {
        this.activeAnim = animName == null ? "" : animName;
        this.loopMode = loop;
        this.speed = Math.max(0.05f, animSpeed);
        this.animSerial++;
        this.clientLastSeenSerial = this.animSerial;
        this.clientStartTick = this.level != null ? this.level.getGameTime() : 0L;
    }

    public String activeAnim() { return activeAnim; }
    public int loopMode() { return loopMode; }
    public float speed() { return speed; }
    public int animSerial() { return animSerial; }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!activeAnim.isEmpty()) {
            tag.putString("ScAnim", activeAnim);
            tag.putInt("ScLoop", loopMode);
            tag.putFloat("ScSpeed", speed);
            tag.putInt("ScSerial", animSerial);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.activeAnim = tag.getString("ScAnim");
        this.loopMode = tag.getInt("ScLoop");
        this.speed = tag.contains("ScSpeed") ? tag.getFloat("ScSpeed") : 1.0f;
        this.animSerial = tag.getInt("ScSerial");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putString("ScAnim", activeAnim);
        tag.putInt("ScLoop", loopMode);
        tag.putFloat("ScSpeed", speed);
        tag.putInt("ScSerial", animSerial);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
