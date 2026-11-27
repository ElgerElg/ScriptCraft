package com.scriptcraft.entity;

import com.scriptcraft.script.ScriptLoader;
import com.scriptcraft.script.runtime.ExecutionContext;
import com.scriptcraft.script.runtime.NpcManager;
import com.scriptcraft.script.runtime.ScriptEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * Фейковый игрок-NPC для РП-серверов, магазинов, квестов и катсцен:
 * поддерживает скины по нику / URL / из папки ресурсов, авто-подбор Alex (slim) / Steve (wide),
 * броню (включая 3D Geo-броню), предметы в руках, аттачи к костям и анимации PlayerAnimator!
 */
public class ScriptFakeNpcEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> DATA_NPC_ID =
            SynchedEntityData.defineId(ScriptFakeNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_SKIN =
            SynchedEntityData.defineId(ScriptFakeNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_MODEL_STYLE =
            SynchedEntityData.defineId(ScriptFakeNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_LOOK_AT_PLAYERS =
            SynchedEntityData.defineId(ScriptFakeNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_ACTIVE_ANIM =
            SynchedEntityData.defineId(ScriptFakeNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_ANIM_SPEED =
            SynchedEntityData.defineId(ScriptFakeNpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_ANIM_SERIAL =
            SynchedEntityData.defineId(ScriptFakeNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_VULNERABLE =
            SynchedEntityData.defineId(ScriptFakeNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SNEAKING =
            SynchedEntityData.defineId(ScriptFakeNpcEntity.class, EntityDataSerializers.BOOLEAN);

    public transient int clientAnimStartTick = 0;
    public transient int clientLastSeenSerial = 0;

    public ScriptFakeNpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_NPC_ID, "");
        this.entityData.define(DATA_SKIN, "");
        this.entityData.define(DATA_MODEL_STYLE, "auto");
        this.entityData.define(DATA_LOOK_AT_PLAYERS, true);
        this.entityData.define(DATA_ACTIVE_ANIM, "");
        this.entityData.define(DATA_ANIM_SPEED, 1.0f);
        this.entityData.define(DATA_ANIM_SERIAL, 0);
        this.entityData.define(DATA_VULNERABLE, false);
        this.entityData.define(DATA_SNEAKING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 10.0F, 1.0F) {
            @Override
            public boolean canUse() {
                return ScriptFakeNpcEntity.this.isLookAtPlayers() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return ScriptFakeNpcEntity.this.isLookAtPlayers() && super.canContinueToUse();
            }
        });
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!this.level().isClientSide && !getNpcId().isEmpty()) {
            NpcManager.register(this);
        }
    }

    @Override
    public void tick() {
        if (this.level().isClientSide) {
            int ser = getAnimSerial();
            if (ser != clientLastSeenSerial) {
                clientLastSeenSerial = ser;
                clientAnimStartTick = this.tickCount;
            }
        }
        boolean sneak = isNpcSneaking();
        this.setShiftKeyDown(sneak);
        this.setPose(sneak ? Pose.CROUCHING : Pose.STANDING);
        super.tick();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide && player instanceof ServerPlayer sp) {
            Map<String, String> ph = buildPlaceholders();
            ScriptLoader.fireEvent(ScriptEvent.NPC_CLICK, sp, ph);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && source.getEntity() instanceof ServerPlayer sp) {
            Map<String, String> ph = buildPlaceholders();
            ExecutionContext ctx = ScriptLoader.fireEvent(ScriptEvent.NPC_ATTACK, sp, ph);
            if (ctx != null && ctx.isCancelled()) {
                return false;
            }
        }
        if (!isVulnerable() && !source.isCreativePlayer() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    private Map<String, String> buildPlaceholders() {
        Map<String, String> ph = new HashMap<>();
        ph.put("npc", getNpcId());
        ph.put("npc_name", getName().getString());
        ph.put("clicked_entity", "scriptcraft:fake_npc");
        ph.put("clicked_entity_name", getName().getString());
        BlockPos pos = blockPosition();
        ph.put("x", Integer.toString(pos.getX()));
        ph.put("y", Integer.toString(pos.getY()));
        ph.put("z", Integer.toString(pos.getZ()));
        return ph;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    public void playNpcAnimation(String animId, float speed) {
        this.entityData.set(DATA_ACTIVE_ANIM, animId == null ? "" : animId);
        this.entityData.set(DATA_ANIM_SPEED, Math.max(0.05f, Math.min(10.0f, speed)));
        this.entityData.set(DATA_ANIM_SERIAL, this.entityData.get(DATA_ANIM_SERIAL) + 1);
    }

    public void stopNpcAnimation() {
        this.entityData.set(DATA_ACTIVE_ANIM, "");
        this.entityData.set(DATA_ANIM_SERIAL, this.entityData.get(DATA_ANIM_SERIAL) + 1);
    }

    public String getNpcId() { return this.entityData.get(DATA_NPC_ID); }
    public void setNpcId(String id) {
        this.entityData.set(DATA_NPC_ID, id == null ? "" : id);
        if (!this.level().isClientSide && id != null && !id.isEmpty()) {
            NpcManager.register(this);
        }
    }

    public String getSkinSource() { return this.entityData.get(DATA_SKIN); }
    public void setSkinSource(String skin) { this.entityData.set(DATA_SKIN, skin == null ? "" : skin); }

    public String getModelStyle() { return this.entityData.get(DATA_MODEL_STYLE); }
    public void setModelStyle(String style) { this.entityData.set(DATA_MODEL_STYLE, style == null ? "auto" : style); }

    public boolean isLookAtPlayers() { return this.entityData.get(DATA_LOOK_AT_PLAYERS); }
    public void setLookAtPlayers(boolean look) { this.entityData.set(DATA_LOOK_AT_PLAYERS, look); }

    public String getActiveAnim() { return this.entityData.get(DATA_ACTIVE_ANIM); }
    public float getAnimSpeed() { return this.entityData.get(DATA_ANIM_SPEED); }
    public int getAnimSerial() { return this.entityData.get(DATA_ANIM_SERIAL); }

    public boolean isVulnerable() { return this.entityData.get(DATA_VULNERABLE); }
    public void setVulnerable(boolean v) { this.entityData.set(DATA_VULNERABLE, v); }

    public boolean isNpcSneaking() { return this.entityData.get(DATA_SNEAKING); }
    public void setNpcSneaking(boolean s) { this.entityData.set(DATA_SNEAKING, s); }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("ScNpcId", getNpcId());
        tag.putString("ScSkin", getSkinSource());
        tag.putString("ScModelStyle", getModelStyle());
        tag.putBoolean("ScLookPlayers", isLookAtPlayers());
        tag.putString("ScActiveAnim", getActiveAnim());
        tag.putFloat("ScAnimSpeed", getAnimSpeed());
        tag.putBoolean("ScVulnerable", isVulnerable());
        tag.putBoolean("ScSneaking", isNpcSneaking());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("ScNpcId")) setNpcId(tag.getString("ScNpcId"));
        if (tag.contains("ScSkin")) setSkinSource(tag.getString("ScSkin"));
        if (tag.contains("ScModelStyle")) setModelStyle(tag.getString("ScModelStyle"));
        if (tag.contains("ScLookPlayers")) setLookAtPlayers(tag.getBoolean("ScLookPlayers"));
        if (tag.contains("ScActiveAnim")) this.entityData.set(DATA_ACTIVE_ANIM, tag.getString("ScActiveAnim"));
        if (tag.contains("ScAnimSpeed")) this.entityData.set(DATA_ANIM_SPEED, tag.getFloat("ScAnimSpeed"));
        if (tag.contains("ScVulnerable")) setVulnerable(tag.getBoolean("ScVulnerable"));
        if (tag.contains("ScSneaking")) setNpcSneaking(tag.getBoolean("ScSneaking"));
    }
}
