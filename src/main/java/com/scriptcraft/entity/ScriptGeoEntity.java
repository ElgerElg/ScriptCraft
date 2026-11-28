package com.scriptcraft.entity;

import com.scriptcraft.script.parser.EntityDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Универсальная 3D-сущность ScriptCraft (мобы, боссы, турели, анимированный декор).
 * Один зарегистрированный EntityType обслуживает неограниченное число {@code define entity} без лимита слотов!
 */
public class ScriptGeoEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> DATA_DEF_ID =
            SynchedEntityData.defineId(ScriptGeoEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_MODEL =
            SynchedEntityData.defineId(ScriptGeoEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_TEXTURE =
            SynchedEntityData.defineId(ScriptGeoEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_ANIM_FILE =
            SynchedEntityData.defineId(ScriptGeoEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_IDLE_ANIM =
            SynchedEntityData.defineId(ScriptGeoEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_WALK_ANIM =
            SynchedEntityData.defineId(ScriptGeoEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_ATTACK_ANIM =
            SynchedEntityData.defineId(ScriptGeoEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_DEATH_ANIM =
            SynchedEntityData.defineId(ScriptGeoEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_ACTIVE_ANIM =
            SynchedEntityData.defineId(ScriptGeoEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_ACTIVE_LOOP =
            SynchedEntityData.defineId(ScriptGeoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_ACTIVE_SPEED =
            SynchedEntityData.defineId(ScriptGeoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_ACTIVE_SERIAL =
            SynchedEntityData.defineId(ScriptGeoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_WIDTH =
            SynchedEntityData.defineId(ScriptGeoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT =
            SynchedEntityData.defineId(ScriptGeoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(ScriptGeoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> DATA_AI_MODE =
            SynchedEntityData.defineId(ScriptGeoEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_GLOW =
            SynchedEntityData.defineId(ScriptGeoEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_PARTICLE_BONES =
            SynchedEntityData.defineId(ScriptGeoEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_PUSHABLE =
            SynchedEntityData.defineId(ScriptGeoEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_INVULNERABLE =
            SynchedEntityData.defineId(ScriptGeoEntity.class, EntityDataSerializers.BOOLEAN);

    private double configuredAttackRange = 2.5;
    private boolean goalsInitialized = false;

    // Клиентские поля для плавного отсчёта времени анимаций
    public transient int clientAnimStartTick = 0;
    public transient int clientLastSeenSerial = 0;
    public transient int clientSpawnTick = -1;

    public ScriptGeoEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_DEF_ID, "");
        this.entityData.define(DATA_MODEL, "");
        this.entityData.define(DATA_TEXTURE, "");
        this.entityData.define(DATA_ANIM_FILE, "");
        this.entityData.define(DATA_IDLE_ANIM, "idle");
        this.entityData.define(DATA_WALK_ANIM, "walk");
        this.entityData.define(DATA_ATTACK_ANIM, "attack");
        this.entityData.define(DATA_DEATH_ANIM, "death");
        this.entityData.define(DATA_ACTIVE_ANIM, "");
        this.entityData.define(DATA_ACTIVE_LOOP, 0);
        this.entityData.define(DATA_ACTIVE_SPEED, 1.0f);
        this.entityData.define(DATA_ACTIVE_SERIAL, 0);
        this.entityData.define(DATA_WIDTH, 0.8f);
        this.entityData.define(DATA_HEIGHT, 1.9f);
        this.entityData.define(DATA_SCALE, 1.0f);
        this.entityData.define(DATA_AI_MODE, EntityDefinition.AI_HOSTILE);
        this.entityData.define(DATA_GLOW, true);
        this.entityData.define(DATA_PARTICLE_BONES, "");
        this.entityData.define(DATA_PUSHABLE, true);
        this.entityData.define(DATA_INVULNERABLE, false);
    }

    @Override
    protected void registerGoals() {
        // Настраиваются динамически в rebuildAiGoals() после чтения NBT или applyDefinition()
    }

    public void applyDefinition(EntityDefinition def) {
        if (def == null) return;
        setDefId(def.id());
        setModelPath(def.effectiveModel());
        setTexturePath(def.effectiveTexture());
        setAnimFilePath(def.effectiveAnimationFile());
        this.entityData.set(DATA_IDLE_ANIM, def.idleAnimation() == null ? "idle" : def.idleAnimation());
        this.entityData.set(DATA_WALK_ANIM, def.walkAnimation() == null ? "walk" : def.walkAnimation());
        this.entityData.set(DATA_ATTACK_ANIM, def.attackAnimation() == null ? "attack" : def.attackAnimation());
        this.entityData.set(DATA_DEATH_ANIM, def.deathAnimation() == null ? "death" : def.deathAnimation());
        setHitbox(def.width(), def.height());
        setGeoScale(def.scale());
        this.entityData.set(DATA_GLOW, def.glow());
        this.entityData.set(DATA_PUSHABLE, def.pushable());
        this.entityData.set(DATA_INVULNERABLE, def.invulnerable());
        this.setNoGravity(!def.gravity());
        this.configuredAttackRange = Math.max(1.0, def.attackRange());
        this.entityData.set(DATA_PARTICLE_BONES, encodeParticleBones(def.particleBones()));

        if (def.displayName() != null && !def.displayName().isBlank()) {
            this.setCustomName(com.scriptcraft.util.TextFormat.format(def.displayName()));
            this.setCustomNameVisible(def.nametag());
        }

        setAttr(Attributes.MAX_HEALTH, Math.max(1.0, def.health()));
        this.setHealth(this.getMaxHealth());
        setAttr(Attributes.MOVEMENT_SPEED, Math.max(0.0, def.speed()));
        setAttr(Attributes.ATTACK_DAMAGE, Math.max(0.0, def.damage()));
        setAttr(Attributes.FOLLOW_RANGE, Math.max(4.0, def.followRange()));
        setAiMode(def.ai());

        if (def.spawnAnimation() != null && !def.spawnAnimation().isBlank()) {
            playAnimation(def.spawnAnimation(), 0, 1.0f);
        }
    }

    @Override
    public void setCustomName(@org.jetbrains.annotations.Nullable Component name) {
        if (name != null && com.scriptcraft.util.TextFormat.containsAmpersandCodes(name.getString())) {
            name = com.scriptcraft.util.TextFormat.format(name.getString());
        }
        super.setCustomName(name);
    }

    private void setAttr(net.minecraft.world.entity.ai.attributes.Attribute attr, double val) {
        AttributeInstance inst = this.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(val);
        }
    }

    public void rebuildAiGoals() {
        if (this.level().isClientSide) return;
        this.goalSelector.removeAllGoals(g -> true);
        this.targetSelector.removeAllGoals(g -> true);

        String mode = getAiMode().toLowerCase(Locale.ROOT);
        this.goalsInitialized = true;

        switch (mode) {
            case EntityDefinition.AI_NONE -> {
                setAttr(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
                this.getNavigation().stop();
            }
            case EntityDefinition.AI_PASSIVE -> {
                setAttr(Attributes.KNOCKBACK_RESISTANCE, 0.0D);
                this.goalSelector.addGoal(0, new FloatGoal(this));
                this.goalSelector.addGoal(1, new PanicGoal(this, 1.25D));
                this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0D));
                this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
                this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
            }
            case EntityDefinition.AI_TURRET -> {
                setAttr(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
                this.goalSelector.addGoal(1, new TurretAttackGoal(this));
                this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, (float) Math.max(8.0, configuredAttackRange)));
                this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
                this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                        living -> living instanceof Enemy && living != this));
            }
            default -> { // hostile
                setAttr(Attributes.KNOCKBACK_RESISTANCE, 0.15D);
                this.goalSelector.addGoal(0, new FloatGoal(this));
                this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1D, true) {
                    @Override
                    protected void checkAndPerformAttack(LivingEntity enemy, double distToEnemySqr) {
                        double reach = getAttackReachSqr(enemy);
                        if (distToEnemySqr <= reach && this.getTicksUntilNextAttack() <= 0) {
                            this.resetAttackCooldown();
                            ScriptGeoEntity.this.swing(InteractionHand.MAIN_HAND);
                            String atk = ScriptGeoEntity.this.getAttackAnim();
                            if (!atk.isEmpty()) {
                                ScriptGeoEntity.this.playAnimation(atk, 0, 1.0f);
                            }
                            ScriptGeoEntity.this.doHurtTarget(enemy);
                        }
                    }

                    @Override
                    protected double getAttackReachSqr(LivingEntity target) {
                        double r = Math.max(1.5, configuredAttackRange);
                        return r * r + target.getBbWidth();
                    }
                });
                this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.9D));
                this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 10.0F));
                this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
                this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
                this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
            }
        }
    }

    private static final class TurretAttackGoal extends Goal {
        private final ScriptGeoEntity turret;
        private int cooldown = 0;

        TurretAttackGoal(ScriptGeoEntity turret) {
            this.turret = turret;
            this.setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = turret.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void tick() {
            turret.getNavigation().stop();
            LivingEntity target = turret.getTarget();
            if (target == null || !target.isAlive()) return;
            turret.getLookControl().setLookAt(target, 35.0F, 35.0F);
            double maxDist = Math.max(4.0, turret.configuredAttackRange);
            if (cooldown > 0) cooldown--;
            if (cooldown <= 0 && turret.distanceToSqr(target) <= maxDist * maxDist && turret.hasLineOfSight(target)) {
                cooldown = 25;
                String atk = turret.getAttackAnim();
                if (!atk.isEmpty()) {
                    turret.playAnimation(atk, 0, 1.0f);
                }
                float dmg = (float) turret.getAttributeValue(Attributes.ATTACK_DAMAGE);
                if (dmg > 0f) {
                    target.hurt(turret.damageSources().mobAttack(turret), dmg);
                }
            }
        }
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && !goalsInitialized) {
            rebuildAiGoals();
        }
        if (this.level().isClientSide) {
            if (clientSpawnTick < 0) {
                clientSpawnTick = this.tickCount;
            }
            int ser = getActiveSerial();
            if (ser != clientLastSeenSerial) {
                clientLastSeenSerial = ser;
                clientAnimStartTick = this.tickCount;
            }
        }
        super.tick();
        if (EntityDefinition.AI_TURRET.equals(getAiMode()) || EntityDefinition.AI_NONE.equals(getAiMode())) {
            this.setDeltaMovement(0.0, this.isNoGravity() ? 0.0 : this.getDeltaMovement().y, 0.0);
        }
    }

    public void playAnimation(String animName, int loopMode, float speed) {
        this.entityData.set(DATA_ACTIVE_ANIM, animName == null ? "" : animName);
        this.entityData.set(DATA_ACTIVE_LOOP, loopMode);
        this.entityData.set(DATA_ACTIVE_SPEED, Math.max(0.05f, speed));
        this.entityData.set(DATA_ACTIVE_SERIAL, this.entityData.get(DATA_ACTIVE_SERIAL) + 1);
    }

    public void stopAnimation() {
        this.entityData.set(DATA_ACTIVE_ANIM, "");
        this.entityData.set(DATA_ACTIVE_SERIAL, this.entityData.get(DATA_ACTIVE_SERIAL) + 1);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float w = Math.max(0.1f, this.entityData.get(DATA_WIDTH));
        float h = Math.max(0.1f, this.entityData.get(DATA_HEIGHT));
        return EntityDimensions.scalable(w, h);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_WIDTH.equals(key) || DATA_HEIGHT.equals(key)) {
            this.refreshDimensions();
        }
    }

    @Override
    public boolean isPushable() {
        return this.entityData.get(DATA_PUSHABLE) && !EntityDefinition.AI_NONE.equals(getAiMode()) && !EntityDefinition.AI_TURRET.equals(getAiMode());
    }

    @Override
    protected void doPush(Entity entity) {
        if (isPushable()) {
            super.doPush(entity);
        }
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (this.entityData.get(DATA_INVULNERABLE) && !source.isCreativePlayer() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // --- Геттеры и сеттеры ---

    public String getDefId() { return this.entityData.get(DATA_DEF_ID); }
    public void setDefId(String id) { this.entityData.set(DATA_DEF_ID, id == null ? "" : id); }

    public String getModelPath() { return this.entityData.get(DATA_MODEL); }
    public void setModelPath(String model) { this.entityData.set(DATA_MODEL, model == null ? "" : model); }

    public String getTexturePath() { return this.entityData.get(DATA_TEXTURE); }
    public void setTexturePath(String tex) { this.entityData.set(DATA_TEXTURE, tex == null ? "" : tex); }

    public String getAnimFilePath() { return this.entityData.get(DATA_ANIM_FILE); }
    public void setAnimFilePath(String anim) { this.entityData.set(DATA_ANIM_FILE, anim == null ? "" : anim); }

    public String getIdleAnim() { return this.entityData.get(DATA_IDLE_ANIM); }
    public String getWalkAnim() { return this.entityData.get(DATA_WALK_ANIM); }
    public String getAttackAnim() { return this.entityData.get(DATA_ATTACK_ANIM); }
    public String getDeathAnim() { return this.entityData.get(DATA_DEATH_ANIM); }
    public String getActiveAnim() { return this.entityData.get(DATA_ACTIVE_ANIM); }
    public int getActiveLoop() { return this.entityData.get(DATA_ACTIVE_LOOP); }
    public float getActiveSpeed() { return this.entityData.get(DATA_ACTIVE_SPEED); }
    public int getActiveSerial() { return this.entityData.get(DATA_ACTIVE_SERIAL); }

    public float getGeoScale() { return this.entityData.get(DATA_SCALE); }
    public void setGeoScale(float scale) { this.entityData.set(DATA_SCALE, Math.max(0.05f, Math.min(16.0f, scale))); }

    public void setHitbox(float w, float h) {
        this.entityData.set(DATA_WIDTH, Math.max(0.1f, Math.min(16.0f, w)));
        this.entityData.set(DATA_HEIGHT, Math.max(0.1f, Math.min(16.0f, h)));
        this.refreshDimensions();
    }

    public String getAiMode() { return this.entityData.get(DATA_AI_MODE); }
    public void setAiMode(String ai) {
        String clean = EntityDefinition.parseAi(ai);
        this.entityData.set(DATA_AI_MODE, clean == null ? EntityDefinition.AI_HOSTILE : clean);
        rebuildAiGoals();
    }

    public boolean isGlowEnabled() { return this.entityData.get(DATA_GLOW); }
    public String getParticleBonesRaw() { return this.entityData.get(DATA_PARTICLE_BONES); }

    public static String encodeParticleBones(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        map.forEach((bone, part) -> {
            if (!sb.isEmpty()) sb.append(';');
            sb.append(bone).append('=').append(part);
        });
        return sb.toString();
    }

    public static Map<String, String> decodeParticleBones(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        for (String pair : raw.split(";")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                out.put(pair.substring(0, eq).strip(), pair.substring(eq + 1).strip());
            }
        }
        return out;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("ScDefId", getDefId());
        tag.putString("ScModel", getModelPath());
        tag.putString("ScTexture", getTexturePath());
        tag.putString("ScAnimFile", getAnimFilePath());
        tag.putString("ScIdleAnim", getIdleAnim());
        tag.putString("ScWalkAnim", getWalkAnim());
        tag.putString("ScAttackAnim", getAttackAnim());
        tag.putString("ScDeathAnim", getDeathAnim());
        tag.putString("ScActiveAnim", getActiveAnim());
        tag.putInt("ScActiveLoop", getActiveLoop());
        tag.putFloat("ScActiveSpeed", getActiveSpeed());
        tag.putFloat("ScWidth", this.entityData.get(DATA_WIDTH));
        tag.putFloat("ScHeight", this.entityData.get(DATA_HEIGHT));
        tag.putFloat("ScScale", getGeoScale());
        tag.putString("ScAi", getAiMode());
        tag.putBoolean("ScGlow", isGlowEnabled());
        tag.putString("ScParticleBones", getParticleBonesRaw());
        tag.putBoolean("ScPushable", this.entityData.get(DATA_PUSHABLE));
        tag.putBoolean("ScInvulnerable", this.entityData.get(DATA_INVULNERABLE));
        tag.putDouble("ScAttackRange", configuredAttackRange);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("ScDefId")) setDefId(tag.getString("ScDefId"));
        if (tag.contains("ScModel")) setModelPath(tag.getString("ScModel"));
        if (tag.contains("ScTexture")) setTexturePath(tag.getString("ScTexture"));
        if (tag.contains("ScAnimFile")) setAnimFilePath(tag.getString("ScAnimFile"));
        if (tag.contains("ScIdleAnim")) this.entityData.set(DATA_IDLE_ANIM, tag.getString("ScIdleAnim"));
        if (tag.contains("ScWalkAnim")) this.entityData.set(DATA_WALK_ANIM, tag.getString("ScWalkAnim"));
        if (tag.contains("ScAttackAnim")) this.entityData.set(DATA_ATTACK_ANIM, tag.getString("ScAttackAnim"));
        if (tag.contains("ScDeathAnim")) this.entityData.set(DATA_DEATH_ANIM, tag.getString("ScDeathAnim"));
        if (tag.contains("ScActiveAnim")) this.entityData.set(DATA_ACTIVE_ANIM, tag.getString("ScActiveAnim"));
        if (tag.contains("ScActiveLoop")) this.entityData.set(DATA_ACTIVE_LOOP, tag.getInt("ScActiveLoop"));
        if (tag.contains("ScActiveSpeed")) this.entityData.set(DATA_ACTIVE_SPEED, tag.getFloat("ScActiveSpeed"));
        if (tag.contains("ScWidth") && tag.contains("ScHeight")) {
            setHitbox(tag.getFloat("ScWidth"), tag.getFloat("ScHeight"));
        }
        if (tag.contains("ScScale")) setGeoScale(tag.getFloat("ScScale"));
        if (tag.contains("ScGlow")) this.entityData.set(DATA_GLOW, tag.getBoolean("ScGlow"));
        if (tag.contains("ScParticleBones")) this.entityData.set(DATA_PARTICLE_BONES, tag.getString("ScParticleBones"));
        if (tag.contains("ScPushable")) this.entityData.set(DATA_PUSHABLE, tag.getBoolean("ScPushable"));
        if (tag.contains("ScInvulnerable")) this.entityData.set(DATA_INVULNERABLE, tag.getBoolean("ScInvulnerable"));
        if (tag.contains("ScAttackRange")) configuredAttackRange = tag.getDouble("ScAttackRange");
        if (tag.contains("ScAi")) setAiMode(tag.getString("ScAi"));
    }
}
