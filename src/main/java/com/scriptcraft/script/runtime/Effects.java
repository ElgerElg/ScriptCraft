package com.scriptcraft.script.runtime;

import com.scriptcraft.config.ScriptCraftConfig;
import com.scriptcraft.lang.Messages;
import com.scriptcraft.ScriptCraft;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;

/**
 * Эффекты над игроком (белый список, синтаксис как в Skript). Разбираются таблицей шаблонов в
 * {@link com.scriptcraft.script.parser.EffectPatterns}. Новый эффект = record здесь + одна строка в таблице.
 */
public final class Effects {
    private Effects() {
    }

    // ------------------------------------------------------------------ помощники

    static double number(Expression e, ExecutionContext ctx) {
        Double d = Expression.toNumber(e.evaluate(ctx));
        if (d == null || d.isNaN() || d.isInfinite()) {
            throw new ScriptRuntimeException(Messages.get("effect.not_number", Expression.toText(e.evaluate(ctx))));
        }
        return d;
    }

    static boolean bool(Expression e, ExecutionContext ctx) {
        Object v = e.evaluate(ctx);
        if (v instanceof Boolean b) {
            return b;
        }
        String s = Expression.toText(v).strip().toLowerCase();
        return s.equals("true") || s.equals("yes") || s.equals("on") || s.equals("1");
    }

    /** "speed" -> minecraft:speed, "mymod:x" -> как есть. */
    static ResourceLocation id(String raw) {
        ResourceLocation rl = ResourceLocation.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
        if (rl == null) {
            throw new ScriptRuntimeException(Messages.get("effect.bad_id", raw));
        }
        return rl;
    }

    static MobEffect mobEffect(String raw) {
        ResourceLocation rl = id(raw);
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(rl);
        if (effect == null || !ForgeRegistries.MOB_EFFECTS.containsKey(rl)) {
            throw new ScriptRuntimeException(Messages.get("effect.unknown_effect", rl));
        }
        return effect;
    }

    private static int ticks(double seconds) {
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE / 2.0, seconds * 20));
    }

    // ------------------------------------------------------------------ режим игры, здоровье, голод

    /** set gamemode of player to creative */
    public record SetGameMode(Target target, GameType mode) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            for (ServerPlayer p : target.resolve(ctx)) {
                p.setGameMode(mode);
            }
        }
    }

    /** heal player (полностью) / heal player by 4 */
    public record Heal(Target target, @Nullable Expression amount) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            Float by = amount == null ? null : (float) number(amount, ctx);
            for (ServerPlayer p : target.resolve(ctx)) {
                if (by == null) {
                    p.setHealth(p.getMaxHealth());
                } else {
                    p.heal(by);
                }
            }
        }
    }

    /** set health of player to 10 */
    public record SetHealth(Target target, Expression value) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            double v = number(value, ctx);
            for (ServerPlayer p : target.resolve(ctx)) {
                p.setHealth((float) Math.max(0, Math.min(p.getMaxHealth(), v)));
            }
        }
    }

    /** damage player by 4 (обычный урон: учитывает броню и режим игры) */
    public record Damage(Target target, Expression amount) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            float v = (float) Math.max(0, number(amount, ctx));
            for (ServerPlayer p : target.resolve(ctx)) {
                p.hurt(p.damageSources().generic(), v);
            }
        }
    }

    /** feed player (полностью: голод 20, насыщение 20) / feed player by 6 */
    public record Feed(Target target, @Nullable Expression amount) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            Double by = amount == null ? null : number(amount, ctx);
            for (ServerPlayer p : target.resolve(ctx)) {
                FoodData food = p.getFoodData();
                if (by == null) {
                    food.setFoodLevel(20);
                    food.setSaturation(20f);
                } else {
                    food.setFoodLevel((int) Math.max(0, Math.min(20, food.getFoodLevel() + by)));
                }
            }
        }
    }

    /** set food level of player to 10 */
    public record SetFood(Target target, Expression value) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            int v = (int) Math.max(0, Math.min(20, number(value, ctx)));
            for (ServerPlayer p : target.resolve(ctx)) {
                p.getFoodData().setFoodLevel(v);
            }
        }
    }

    /** set saturation of player to 5 (не больше уровня голода — так работает ванилла) */
    public record SetSaturation(Target target, Expression value) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            double v = number(value, ctx);
            for (ServerPlayer p : target.resolve(ctx)) {
                FoodData food = p.getFoodData();
                food.setSaturation((float) Math.max(0, Math.min(food.getFoodLevel(), v)));
            }
        }
    }

    /** kill player */
    public record Kill(Target target) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            for (ServerPlayer p : target.resolve(ctx)) {
                p.kill();
            }
        }
    }

    /** extinguish player */
    public record Extinguish(Target target) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            for (ServerPlayer p : target.resolve(ctx)) {
                p.clearFire();
            }
        }
    }

    /** ignite player for 5 seconds */
    public record Ignite(Target target, Expression seconds) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            int v = (int) Math.max(1, Math.min(3600, number(seconds, ctx)));
            for (ServerPlayer p : target.resolve(ctx)) {
                p.setSecondsOnFire(v);
            }
        }
    }

    /** clear inventory of player */
    public record ClearInventory(Target target) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            for (ServerPlayer p : target.resolve(ctx)) {
                p.getInventory().clearContent();
            }
        }
    }

    // ------------------------------------------------------------------ эффекты зелий

    /** give player effect speed for 30 seconds [level 2] (level 1 = без усиления) */
    public record GiveEffect(Target target, String effectId, Expression seconds, @Nullable Expression level) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            MobEffect effect = mobEffect(effectId);
            int amplifier = level == null ? 0 : (int) Math.max(0, Math.min(255, number(level, ctx) - 1));
            int duration = ticks(number(seconds, ctx));
            for (ServerPlayer p : target.resolve(ctx)) {
                // Свой экземпляр на каждого игрока: MobEffectInstance изменяемый (тикает длительность).
                p.addEffect(new MobEffectInstance(effect, duration, amplifier));
            }
        }
    }

    /** remove effect speed from player */
    public record RemoveEffect(Target target, String effectId) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            MobEffect effect = mobEffect(effectId);
            for (ServerPlayer p : target.resolve(ctx)) {
                p.removeEffect(effect);
            }
        }
    }

    /** clear effects of player */
    public record ClearEffects(Target target) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            for (ServerPlayer p : target.resolve(ctx)) {
                p.removeAllEffects();
            }
        }
    }

    // ------------------------------------------------------------------ звук, титры, опыт, полёт

    /** play sound entity.player.levelup to player [with volume N] [pitch N] (слышит только этот игрок) */
    public record PlaySound(Target target, String soundId, @Nullable Expression volume, @Nullable Expression pitch) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            SoundEvent sound = soundEvent(ctx.format(soundId));
            float vol = volume == null ? 1f : (float) Math.max(0, Math.min(10, number(volume, ctx)));
            float pit = pitch == null ? 1f : (float) Math.max(0.5, Math.min(2, number(pitch, ctx)));
            boolean registered = ForgeRegistries.SOUND_EVENTS.containsKey(sound.getLocation());
            Holder<SoundEvent> directHolder = registered ? null : Holder.direct(sound);
            for (ServerPlayer p : target.resolve(ctx)) {
                if (registered) {
                    p.playNotifySound(sound, SoundSource.MASTER, vol, pit);
                } else {
                    long seed = p.serverLevel().getRandom().nextLong();
                    p.connection.send(new ClientboundSoundPacket(directHolder, SoundSource.MASTER,
                            p.getX(), p.getY(), p.getZ(), vol, pit, seed));
                }
            }
        }
    }

    /**
     * play sound <id> at <x y z | target | expr> [for <listeners>] [with volume N] [pitch N].
     * Если listeners == null, звук слышен всем рядом в мире; иначе только указанным игрокам от этой точки.
     */
    public record PlaySoundAt(String soundId, @Nullable Target atTarget, @Nullable Expression atPlace,
                              @Nullable Expression x, @Nullable Expression y, @Nullable Expression z,
                              @Nullable Target listeners, @Nullable Expression volume, @Nullable Expression pitch) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            SoundEvent sound = soundEvent(ctx.format(soundId));
            float vol = volume == null ? 1f : (float) Math.max(0, Math.min(10, number(volume, ctx)));
            float pit = pitch == null ? 1f : (float) Math.max(0.5, Math.min(2, number(pitch, ctx)));
            if (x != null && y != null && z != null) {
                emitSound(ctx, levelOf(ctx), number(x, ctx), number(y, ctx), number(z, ctx), sound, vol, pit, listeners);
                return;
            }
            if (atPlace != null) {
                ScriptLocation loc = Places.require(atPlace.evaluate(ctx));
                emitSound(ctx, Places.level(ctx.server(), loc), loc.x(), loc.y(), loc.z(), sound, vol, pit, listeners);
                return;
            }
            if (atTarget != null) {
                ScriptLocation loc = Places.fromNamed(atTarget, ctx);
                if (loc != null) {
                    emitSound(ctx, Places.level(ctx.server(), loc), loc.x(), loc.y(), loc.z(), sound, vol, pit, listeners);
                    return;
                }
                for (ServerPlayer src : atTarget.resolve(ctx)) {
                    emitSound(ctx, src.serverLevel(), src.getX(), src.getY(), src.getZ(), sound, vol, pit, listeners);
                }
            }
        }

        private static void emitSound(ExecutionContext ctx, ServerLevel level, double sx, double sy, double sz,
                                      SoundEvent sound, float vol, float pit, @Nullable Target listeners) {
            if (listeners == null) {
                level.playSound(null, sx, sy, sz, sound, SoundSource.MASTER, vol, pit);
                return;
            }
            Holder<SoundEvent> holder = Holder.direct(sound);
            long seed = level.getRandom().nextLong();
            ClientboundSoundPacket packet = new ClientboundSoundPacket(holder, SoundSource.MASTER, sx, sy, sz, vol, pit, seed);
            for (ServerPlayer p : listeners.resolve(ctx)) {
                p.connection.send(packet);
            }
        }
    }

    private static SoundEvent soundEvent(String raw) {
        ResourceLocation rl = id(raw);
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(rl);
        if (sound != null && ForgeRegistries.SOUND_EVENTS.containsKey(rl)) {
            return sound;
        }
        // Кастомные звуки из scriptcraft/resources/assets/<ns>/sounds.json + .ogg (любой не-minecraft namespace или любой кастомный путь)
        if (!"minecraft".equals(rl.getNamespace()) || hasCustomSoundResource(rl)) {
            return SoundEvent.createVariableRangeEvent(rl);
        }
        throw new ScriptRuntimeException(Messages.get("effect.unknown_sound", rl));
    }

    private static boolean hasCustomSoundResource(ResourceLocation rl) {
        String ns = rl.getNamespace();
        String soundsJson = "assets/" + ns + "/sounds.json";
        if (com.scriptcraft.resources.ResourceIndex.entries().containsKey(soundsJson)) {
            return true;
        }
        String oggDirect = "assets/" + ns + "/sounds/" + rl.getPath() + ".ogg";
        String oggSlash = "assets/" + ns + "/sounds/" + rl.getPath().replace('.', '/') + ".ogg";
        return com.scriptcraft.resources.ResourceIndex.entries().containsKey(oggDirect)
                || com.scriptcraft.resources.ResourceIndex.entries().containsKey(oggSlash);
    }

    /** stop sound [<id>] for|to <target> */
    public record StopSound(Target target, @Nullable String soundId) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ResourceLocation rl = soundId == null || soundId.isBlank() ? null : id(ctx.format(soundId));
            net.minecraft.network.protocol.game.ClientboundStopSoundPacket packet =
                    new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(rl, null);
            for (ServerPlayer p : target.resolve(ctx)) {
                p.connection.send(packet);
            }
        }
    }

    /** send title "..." [with subtitle "..."] to player */
    public record Title(Target target, String title, @Nullable String subtitle) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            Component titleText = Component.literal(ctx.format(title));
            Component subtitleText = subtitle == null ? null : Component.literal(ctx.format(subtitle));
            for (ServerPlayer p : target.resolve(ctx)) {
                // Порядок как у ванильной /title: тайминги, подзаголовок, потом заголовок (он запускает показ).
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
                if (subtitleText != null) {
                    p.connection.send(new ClientboundSetSubtitleTextPacket(subtitleText));
                }
                p.connection.send(new ClientboundSetTitleTextPacket(titleText));
            }
        }
    }

    /** send action bar "..." to player */
    public record ActionBar(Target target, String text) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            Component message = Component.literal(ctx.format(text));
            for (ServerPlayer p : target.resolve(ctx)) {
                p.displayClientMessage(message, true);
            }
        }
    }

    /** set xp level of player to 30 */
    public record SetXpLevel(Target target, Expression value) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            int v = (int) Math.max(0, Math.min(1_000_000, number(value, ctx)));
            for (ServerPlayer p : target.resolve(ctx)) {
                p.setExperienceLevels(v);
            }
        }
    }

    /** give 3 levels to player (уровни, можно отрицательное — снять) */
    public record GiveLevels(Target target, Expression amount) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            int v = (int) Math.max(-1000, Math.min(1000, number(amount, ctx)));
            for (ServerPlayer p : target.resolve(ctx)) {
                p.giveExperienceLevels(v);
            }
        }
    }

    /** give 100 xp to player (очки опыта, не уровни) */
    public record GiveXp(Target target, Expression amount) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            int v = (int) Math.max(-1_000_000, Math.min(1_000_000, number(amount, ctx)));
            for (ServerPlayer p : target.resolve(ctx)) {
                p.giveExperiencePoints(v);
            }
        }
    }

    /** set flying of player to true (true даёт и право летать, false — забирает и останавливает полёт) */
    public record SetFlying(Target target, Expression value) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            boolean fly = bool(value, ctx);
            for (ServerPlayer p : target.resolve(ctx)) {
                if (!p.isCreative() && !p.isSpectator()) {
                    p.getAbilities().mayfly = fly;
                }
                p.getAbilities().flying = fly && p.getAbilities().mayfly;
                p.onUpdateAbilities();
            }
        }
    }

    // ------------------------------------------------------------------ чат, предметы, телепорт

    /** send "текст" [to <цель>] — сообщение игрокам. */
    public record SendMessage(Target target, String text) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            Component message = Component.literal(ctx.format(text));
            for (ServerPlayer p : target.resolve(ctx)) {
                p.sendSystemMessage(message);
            }
        }
    }

    /** %arg-1% и {переменная} в id предмета раскрываются здесь, не при разборе строки. Регистр nbt сохраняется. */
    private static String expandItem(String raw, ExecutionContext ctx) {
        String expanded = ctx.expandPlaceholders(raw).strip();
        if (expanded.length() > 2 && expanded.startsWith("{") && expanded.endsWith("}")) {
            expanded = Expression.toText(ctx.getVariable(expanded.substring(1, expanded.length() - 1)));
        }
        return expanded;
    }

    private static ItemStack stackOf(String raw, ExecutionContext ctx) {
        return ItemStacks.create(expandItem(raw, ctx), 1);
    }

    private static String itemText(Expression item, ExecutionContext ctx) {
        String text = Expression.toText(item.evaluate(ctx));
        if (text.equals("<none>")) {
            throw new ScriptRuntimeException(Messages.get("effect.unknown_item", text));
        }
        return text.strip();
    }

    private static void giveStacks(ServerPlayer player, ItemStack proto, int total) {
        int maxStack = Math.max(1, proto.getMaxStackSize());
        int left = total;
        while (left > 0) {
            int size = Math.min(left, maxStack);
            ItemStack stack = proto.copy();
            stack.setCount(size);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            left -= size;
        }
    }

    private static void giveDrop(ServerPlayer player, ItemStack proto, int total) {
        int maxStack = Math.max(1, proto.getMaxStackSize());
        int left = total;
        while (left > 0) {
            int size = Math.min(left, maxStack);
            ItemStack stack = proto.copy();
            stack.setCount(size);
            player.drop(stack, false);
            left -= size;
        }
    }

    private static void dropStacks(ExecutionContext ctx, ScriptLocation loc, ItemStack proto, int total) {
        int maxStack = Math.max(1, proto.getMaxStackSize());
        int left = total;
        while (left > 0) {
            int size = Math.min(left, maxStack);
            ItemStack stack = proto.copy();
            stack.setCount(size);
            Places.drop(ctx.server(), loc, stack);
            left -= size;
        }
    }

    private static int itemCount(@Nullable Expression count, ExecutionContext ctx) {
        return count == null ? 1 : (int) Math.max(1, Math.min(64 * 36, number(count, ctx)));
    }

    /** give <цель> <предмет> [количество] — выдать предмет; если инвентарь полон, предмет падает рядом. */
    public record GiveItem(Target target, String itemId, @Nullable Expression count) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ItemStack proto = stackOf(itemId, ctx);
            int total = itemCount(count, ctx);
            for (ServerPlayer p : target.resolve(ctx)) {
                giveStacks(p, proto, total);
            }
        }
    }

    /**
     * set slot 0 of player to 1 diamond named "тест" with nbt {Unbreakable:1b}.
     * item == null — очистить слот. 0–8 хотбар, 9–35 инвентарь, 36–39 броня, 40 левая рука.
     */
    public record SetSlot(Expression slot, boolean hotbar, Target target, @Nullable Expression count,
                          @Nullable Expression item, @Nullable Expression name, @Nullable Expression nbt) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ItemStack stack = item == null ? ItemStack.EMPTY : decorate(ctx);
            for (ServerPlayer player : target.resolve(ctx)) {
                int index = slotIndex(player, ctx);
                player.getInventory().setItem(index, stack.copy());
                player.inventoryMenu.broadcastChanges();
            }
        }

        private ItemStack decorate(ExecutionContext ctx) {
            String id = itemText(item, ctx);
            if (id.equalsIgnoreCase("air") || id.equalsIgnoreCase("minecraft:air")) {
                return ItemStack.EMPTY;
            }
            int total = count == null ? 1 : (int) Math.round(number(count, ctx));
            if (total <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = ItemStacks.create(id, 1);
            stack.setCount(Math.min(total, Math.max(1, stack.getMaxStackSize())));
            if (nbt != null) {
                String extra = Expression.toText(nbt.evaluate(ctx)).strip();
                if (!extra.isEmpty() && !extra.equals("<none>")) {
                    stack.getOrCreateTag().merge(ItemStacks.parseCompound(extra));
                }
            }
            if (name != null) {
                String label = ctx.format(Expression.toText(name.evaluate(ctx)));
                if (!label.isEmpty() && !label.equals("<none>")) {
                    stack.setHoverName(Component.literal(label).withStyle(style -> style.withItalic(false)));
                }
            }
            return stack;
        }

        private int slotIndex(ServerPlayer player, ExecutionContext ctx) {
            return inventorySlot(player, slot, hotbar, ctx);
        }
    }

    /** 0–40, hotbar 1–9 или имя: helmet, chestplate, leggings, boots, offhand, mainhand. */
    public static int inventorySlot(ServerPlayer player, Expression slot, boolean hotbar, ExecutionContext ctx) {
        if (hotbar) {
            int n = (int) Math.round(number(slot, ctx));
            if (n < 1 || n > 9) {
                throw new ScriptRuntimeException(Messages.get("effect.bad_slot", n));
            }
            return n - 1;
        }
        Object raw = slot.evaluate(ctx);
        if (raw instanceof String text && !text.isBlank() && Expression.toNumber(text) == null) {
            Integer named = namedSlot(player, text.strip().toLowerCase(java.util.Locale.ROOT));
            if (named == null) {
                throw new ScriptRuntimeException(Messages.get("effect.bad_slot", text));
            }
            return named;
        }
        int index = (int) Math.round(number(slot, ctx));
        if (index < 0 || index >= player.getInventory().getContainerSize()) {
            throw new ScriptRuntimeException(Messages.get("effect.bad_slot", index));
        }
        return index;
    }

    private static Integer namedSlot(ServerPlayer player, String name) {
        return switch (name) {
            case "mainhand", "hand", "tool" -> player.getInventory().selected;
            case "offhand" -> 40;
            case "boots", "feet" -> 36;
            case "leggings", "legs" -> 37;
            case "chestplate", "chest" -> 38;
            case "helmet", "head" -> 39;
            default -> null;
        };
    }

    /** give player player's tool / give player {слот} — предмет из выражения. */
    public record GiveExpr(Target target, Expression item, @Nullable Expression count) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ItemStack proto = ItemStacks.create(itemText(item, ctx), 1);
            int total = itemCount(count, ctx);
            for (ServerPlayer p : target.resolve(ctx)) {
                giveStacks(p, proto, total);
            }
        }
    }

    /** teleport <цель> to x, y, z — в текущем мире каждого игрока. */
    public record TeleportToPos(Target target, Expression x, Expression y, Expression z) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            double tx = number(x, ctx), ty = number(y, ctx), tz = number(z, ctx);
            for (ServerPlayer p : target.resolve(ctx)) {
                p.teleportTo(tx, ty, tz);
            }
        }
    }

    /**
     * teleport <цель> to <цель> — к первому игроку второго набора (в его мир, с его поворотом головы).
     * Пустой второй набор = ничего не делать.
     */
    public record TeleportToPlayer(Target target, Target destination) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            if (destination instanceof Target.Named named) {
                ScriptLocation loc = ScriptLocation.decode(named.name().evaluate(ctx));
                if (loc != null) {
                    for (ServerPlayer p : target.resolve(ctx)) {
                        Places.teleport(p, loc, ctx.server());
                    }
                    return;
                }
            }
            List<ServerPlayer> dest = destination.resolve(ctx);
            if (dest.isEmpty()) {
                return;
            }
            ServerPlayer to = dest.get(0);
            for (ServerPlayer p : target.resolve(ctx)) {
                if (p != to) {
                    p.teleportTo(to.serverLevel(), to.getX(), to.getY(), to.getZ(), to.getYRot(), to.getXRot());
                }
            }
        }
    }

    /** teleport <цель> to player's location / location of targeted block / {переменная}. */
    public record TeleportToLocation(Target target, Expression place) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ScriptLocation loc = Places.require(place.evaluate(ctx));
            for (ServerPlayer p : target.resolve(ctx)) {
                Places.teleport(p, loc, ctx.server());
            }
        }
    }

    // ------------------------------------------------------------------ команды (allowCommandEffects)

    private static String command(String raw, ExecutionContext ctx) {
        if (!ScriptCraftConfig.allowCommandEffects()) {
            throw new ScriptRuntimeException(Messages.get("effect.commands_disabled"));
        }
        // Из эффект-команды в чате команды доступны только операторам, даже если effectCommandsRequireOp=false:
        // иначе любой игрок получил бы права консоли.
        if (ctx.isEffectCommand() && (ctx.player() == null || !ctx.player().hasPermissions(2))) {
            throw new ScriptRuntimeException(Messages.get("effect.commands_need_op"));
        }
        String cmd = ctx.format(raw).strip();
        return cmd.startsWith("/") ? cmd.substring(1) : cmd;
    }

    /** execute console command "say hi" — от имени сервера (полные права!). */
    public record ConsoleCommand(String raw) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            MinecraftServer server = ctx.server();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command(raw, ctx));
        }
    }

    /** make player execute command "spawn" — с правами самого игрока. */
    public record PlayerCommand(Target target, String raw) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String cmd = command(raw, ctx);
            for (ServerPlayer p : target.resolve(ctx)) {
                ctx.server().getCommands().performPrefixedCommand(p.createCommandSourceStack(), cmd);
            }
        }
    }

    // ------------------------------------------------------------------ кик, права, чат, мир

    /**
     * set block at x y z to stone / set block at {home} to air / set block at targeted block to glass.
     * Координаты без локации — мир игрока из контекста, иначе обычный мир. Ставится состояние блока по умолчанию.
     */
    public record SetBlock(Mode mode, @Nullable Expression x, @Nullable Expression y, @Nullable Expression z,
                           @Nullable Expression place, Expression block) implements Action {
        public enum Mode { LOOK, XYZ, PLACE }

        @Override
        public void execute(ExecutionContext ctx) {
            BlockState state = resolveBlock(block.evaluate(ctx)).defaultBlockState();
            ServerLevel level;
            BlockPos pos;
            switch (mode) {
                case LOOK -> {
                    ServerPlayer viewer = ctx.requirePlayer();
                    String encoded = Places.blockLocation(viewer);
                    if (encoded == null) {
                        throw new ScriptRuntimeException(Messages.get("effect.no_block"));
                    }
                    ScriptLocation loc = Places.require(encoded);
                    level = Places.level(ctx.server(), loc);
                    pos = BlockPos.containing(loc.x(), loc.y(), loc.z());
                }
                case PLACE -> {
                    ScriptLocation loc = Places.require(place.evaluate(ctx));
                    level = Places.level(ctx.server(), loc);
                    pos = BlockPos.containing(loc.x(), loc.y(), loc.z());
                }
                default -> {
                    level = ctx.player() != null ? ctx.player().serverLevel() : ctx.server().overworld();
                    pos = BlockPos.containing(number(x, ctx), number(y, ctx), number(z, ctx));
                }
            }
            level.setBlock(pos, state, 3);
        }
    }

    private static Block resolveBlock(@Nullable Object raw) {
        String text = Expression.toText(raw).strip();
        String id = text.toLowerCase(java.util.Locale.ROOT);
        Block bound = com.scriptcraft.registry.Bindings.current().resolveBlock(id);
        if (bound != null) {
            return bound;
        }
        throw new ScriptRuntimeException(Messages.get("effect.unknown_block", text));
    }

    /** kick player [due to "причина"] */
    public record Kick(Target target, @Nullable String reason) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            Component message = Component.literal(reason == null ? "Kicked" : ctx.format(reason));
            for (ServerPlayer p : target.resolve(ctx)) {
                p.connection.disconnect(message);
            }
        }
    }

    /** ban player [due to|because|for "причина"]. Оффлайн-имя банится, если сервер уже видел этот профиль. */
    public record Ban(Target target, @Nullable String reason) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String why = reason == null ? "Banned" : ctx.format(reason);
            if (target instanceof Target.Local local) {
                Object value = ctx.local(local.name());
                if (value instanceof ServerPlayer player) {
                    banAndKick(ctx, player.getGameProfile(), why);
                    return;
                }
            }
            List<ServerPlayer> players = target.resolve(ctx);
            if (!players.isEmpty()) {
                for (ServerPlayer player : players) {
                    banAndKick(ctx, player.getGameProfile(), why);
                }
                return;
            }
            String name = storedName(target, ctx);
            if (name == null) {
                return;
            }
            GameProfile profile = profileByName(ctx, name);
            if (profile == null || profile.getId() == null) {
                throw new ScriptRuntimeException(Messages.get("effect.ban_unknown", name));
            }
            banAndKick(ctx, profile, why);
        }
    }

    private static void banAndKick(ExecutionContext ctx, GameProfile profile, String why) {
        if (profile.getId() == null) {
            throw new ScriptRuntimeException(Messages.get("effect.ban_unknown", profile.getName()));
        }
        UserBanList bans = ctx.server().getPlayerList().getBans();
        bans.add(new UserBanListEntry(profile, new Date(), "ScriptCraft", null, why));
        ServerPlayer live = ctx.server().getPlayerList().getPlayer(profile.getId());
        if (live != null) {
            live.connection.disconnect(Component.literal(why));
        }
    }

    @Nullable
    static String storedName(Target target, ExecutionContext ctx) {
        if (target instanceof Target.Named named) {
            return plainName(Expression.toText(named.name().evaluate(ctx)));
        }
        if (target instanceof Target.Local local) {
            return plainName(Expression.toText(ctx.local(local.name())));
        }
        return null;
    }

    @Nullable
    private static String plainName(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.strip();
        if (name.isEmpty() || name.equals("<none>")) {
            return null;
        }
        return name;
    }

    @Nullable
    static GameProfile profileByName(ExecutionContext ctx, String name) {
        var cache = ctx.server().getProfileCache();
        if (cache == null) {
            return null;
        }
        return cache.get(name).orElse(null);
    }

    static boolean isBanned(ExecutionContext ctx, @Nullable GameProfile profile) {
        return profile != null && profile.getId() != null && ctx.server().getPlayerList().getBans().isBanned(profile);
    }

    static boolean isBannedName(ExecutionContext ctx, String name) {
        return isBanned(ctx, profileByName(ctx, name));
    }

    /** op player / deop player */
    public record ChangeOp(Target target, boolean op) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            var list = ctx.server().getPlayerList();
            for (ServerPlayer p : target.resolve(ctx)) {
                if (op) {
                    list.op(p.getGameProfile());
                } else {
                    list.deop(p.getGameProfile());
                }
            }
        }
    }

    /** make player say "текст" — сообщение в чат от имени игрока. */
    public record Say(Target target, String text) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String body = ctx.format(text);
            for (ServerPlayer p : target.resolve(ctx)) {
                Component line = Component.translatable("chat.type.text", p.getDisplayName(), Component.literal(body));
                ctx.server().getPlayerList().broadcastSystemMessage(line, false);
            }
        }
    }

    /** log "текст" — строка в лог сервера. */
    public record Log(String text) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ScriptCraft.LOGGER.info("[Script] {}", ctx.format(text));
        }
    }

    /** close inventory of player */
    public record CloseInventory(Target target) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            for (ServerPlayer p : target.resolve(ctx)) {
                p.closeContainer();
            }
        }
    }

    /** wake up player */
    public record Wake(Target target) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            for (ServerPlayer p : target.resolve(ctx)) {
                if (p.isSleeping()) {
                    p.stopSleeping();
                }
            }
        }
    }

    /** set walk speed / fly speed of player to N (ванильные значения: ходьба 0.1, полёт 0.05). */
    public record SetSpeed(Target target, boolean flying, Expression value) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            float v = (float) Math.max(0, Math.min(1, number(value, ctx)));
            for (ServerPlayer p : target.resolve(ctx)) {
                if (flying) {
                    p.getAbilities().setFlyingSpeed(v);
                } else {
                    p.getAbilities().setWalkingSpeed(v);
                }
                p.onUpdateAbilities();
            }
        }
    }

    /** set air of player to N */
    public record SetAir(Target target, Expression value) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            int v = (int) number(value, ctx);
            for (ServerPlayer p : target.resolve(ctx)) {
                p.setAirSupply(Math.max(0, Math.min(p.getMaxAirSupply(), v)));
            }
        }
    }

    /** set absorption of player to N */
    public record SetAbsorption(Target target, Expression value) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            float v = (float) Math.max(0, Math.min(2048, number(value, ctx)));
            for (ServerPlayer p : target.resolve(ctx)) {
                p.setAbsorptionAmount(v);
            }
        }
    }

    /** strike [fake|effect] lightning at player / at x y z (в мире игрока из контекста, иначе в обычном мире). */
    public record Lightning(Target target, @Nullable Expression x, @Nullable Expression y, @Nullable Expression z,
                            boolean visualOnly) implements Action {
        public Lightning(Target target, @Nullable Expression x, @Nullable Expression y, @Nullable Expression z) {
            this(target, x, y, z, false);
        }

        @Override
        public void execute(ExecutionContext ctx) {
            if (x != null && y != null && z != null) {
                bolt(levelOf(ctx), number(x, ctx), number(y, ctx), number(z, ctx), visualOnly);
                return;
            }
            ScriptLocation loc = Places.fromNamed(target, ctx);
            if (loc != null) {
                bolt(Places.level(ctx.server(), loc), loc.x(), loc.y(), loc.z(), visualOnly);
                return;
            }
            for (ServerPlayer p : target.resolve(ctx)) {
                bolt(p.serverLevel(), p.getX(), p.getY(), p.getZ(), visualOnly);
            }
        }
    }

    /** strike [fake|effect] lightning at location of player / at {переменная}. */
    public record LightningAt(Expression place, boolean visualOnly) implements Action {
        public LightningAt(Expression place) {
            this(place, false);
        }

        @Override
        public void execute(ExecutionContext ctx) {
            ScriptLocation loc = Places.require(place.evaluate(ctx));
            bolt(Places.level(ctx.server(), loc), loc.x(), loc.y(), loc.z(), visualOnly);
        }
    }

    /** set time to day|night|noon|midnight|<число> — во всех мирах. */
    public record SetTime(Expression value) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            long time = timeOf(value.evaluate(ctx));
            for (ServerLevel level : ctx.server().getAllLevels()) {
                level.setDayTime(time);
            }
        }

        private static long timeOf(Object v) {
            if (v instanceof String s) {
                long named = switch (s.strip().toLowerCase(java.util.Locale.ROOT)) {
                    case "day", "daytime" -> 1000;
                    case "noon" -> 6000;
                    case "sunset", "evening" -> 12000;
                    case "night" -> 13000;
                    case "midnight" -> 18000;
                    case "sunrise" -> 23000;
                    default -> Long.MIN_VALUE;
                };
                if (named != Long.MIN_VALUE) {
                    return named;
                }
            }
            Double n = Expression.toNumber(v);
            if (n == null) {
                throw new ScriptRuntimeException(com.scriptcraft.lang.Phrase.pick(
                        "unknown time: " + Expression.toText(v) + " (day, noon, night, midnight or a tick number)",
                        "неизвестное время: " + Expression.toText(v) + " (day, noon, night, midnight или число тиков)"));
            }
            return Math.floorMod(n.longValue(), 24000L);
        }
    }

    /** set weather to clear|rain|thunder [for N seconds] — во всех мирах. */
    public record SetWeather(String kind, @Nullable Expression seconds) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            int duration = seconds == null ? 6000 : ticks(number(seconds, ctx));
            boolean rain = kind.equals("rain") || kind.equals("thunder");
            boolean thunder = kind.equals("thunder");
            for (ServerLevel level : ctx.server().getAllLevels()) {
                if (rain) {
                    level.setWeatherParameters(0, duration, true, thunder);
                } else {
                    level.setWeatherParameters(duration, 0, false, false);
                }
            }
        }
    }

    /** drop [N] <предмет> at <цель> */
    public record DropItem(Target target, String itemId, @Nullable Expression count) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ItemStack proto = stackOf(itemId, ctx);
            int total = itemCount(count, ctx);
            ScriptLocation loc = Places.fromNamed(target, ctx);
            if (loc != null) {
                dropStacks(ctx, loc, proto, total);
                return;
            }
            for (ServerPlayer p : target.resolve(ctx)) {
                giveDrop(p, proto, total);
            }
        }
    }

    /** drop player's tool at player / drop diamond at location of targeted block. */
    public record DropExpr(Expression item, @Nullable Expression count, @Nullable Target atPlayer,
                           @Nullable Expression atPlace) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ItemStack proto = ItemStacks.create(itemText(item, ctx), 1);
            int total = itemCount(count, ctx);
            if (atPlace != null) {
                dropStacks(ctx, Places.require(atPlace.evaluate(ctx)), proto, total);
                return;
            }
            ScriptLocation loc = Places.fromNamed(atPlayer, ctx);
            if (loc != null) {
                dropStacks(ctx, loc, proto, total);
                return;
            }
            for (ServerPlayer p : atPlayer.resolve(ctx)) {
                giveDrop(p, proto, total);
            }
        }
    }

    /**
     * teleport player to spawn — точка возрождения (кровать), иначе спавн обычного мира.
     * teleport player to world spawn — общий спавн обычного мира.
     */
    public record TeleportSpawn(Target target, boolean worldSpawn) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            for (ServerPlayer p : target.resolve(ctx)) {
                ServerLevel level;
                BlockPos pos;
                float yaw = p.getYRot();
                if (worldSpawn) {
                    level = ctx.server().overworld();
                    pos = level.getSharedSpawnPos();
                    yaw = level.getSharedSpawnAngle();
                } else {
                    ResourceKey<Level> dim = p.getRespawnDimension();
                    BlockPos bed = p.getRespawnPosition();
                    level = ctx.server().getLevel(dim);
                    if (bed == null || level == null) {
                        level = ctx.server().overworld();
                        pos = level.getSharedSpawnPos();
                        yaw = level.getSharedSpawnAngle();
                    } else {
                        pos = bed;
                    }
                }
                p.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, p.getXRot());
            }
        }
    }

    /** set spawn of player — здесь; set spawn of player to x y z — в его текущем мире. */
    public record SetSpawn(Target target, @Nullable Expression x, @Nullable Expression y, @Nullable Expression z) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            Double tx = x == null ? null : number(x, ctx);
            Double ty = y == null ? null : number(y, ctx);
            Double tz = z == null ? null : number(z, ctx);
            for (ServerPlayer p : target.resolve(ctx)) {
                BlockPos pos = tx == null
                        ? p.blockPosition()
                        : BlockPos.containing(tx, ty, tz);
                p.setRespawnPosition(p.serverLevel().dimension(), pos, p.getYRot(), true, false);
            }
        }
    }

    /** set spawn of player to {дом} / to location of targeted block. */
    public record SetSpawnAt(Target target, Expression place) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ScriptLocation loc = Places.require(place.evaluate(ctx));
            ServerLevel level = Places.level(ctx.server(), loc);
            BlockPos pos = BlockPos.containing(loc.x(), loc.y(), loc.z());
            for (ServerPlayer p : target.resolve(ctx)) {
                p.setRespawnPosition(level.dimension(), pos, loc.yaw(), true, false);
            }
        }
    }

    /** set velocity of player to x y z */
    public record SetVelocity(Target target, Expression x, Expression y, Expression z) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            double vx = number(x, ctx), vy = number(y, ctx), vz = number(z, ctx);
            for (ServerPlayer p : target.resolve(ctx)) {
                motion(p, vx, vy, vz);
            }
        }
    }

    /** set velocity of player to vector(x, y, z) */
    public record SetVelocityVec(Target target, Expression vector) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            Object raw = vector.evaluate(ctx);
            double[] vec = ScriptLocation.decodeVec(raw);
            if (vec == null) {
                throw new ScriptRuntimeException("ожидается vector(x, y, z), а не '" + Expression.toText(raw) + "'");
            }
            for (ServerPlayer p : target.resolve(ctx)) {
                motion(p, vec[0], vec[1], vec[2]);
            }
        }
    }

    /** launch player upwards|forwards [by N] */
    public record Launch(Target target, boolean forwards, @Nullable Expression speed) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            double v = speed == null ? (forwards ? 1.5 : 1.2) : Math.max(-8, Math.min(8, number(speed, ctx)));
            for (ServerPlayer p : target.resolve(ctx)) {
                if (forwards) {
                    Vec3 look = p.getLookAngle();
                    motion(p, look.x * v, look.y * v, look.z * v);
                } else {
                    motion(p, 0, v, 0);
                }
            }
        }
    }

    /** Режим относительного толчка: от источника, к источнику или по направлению взгляда источника. */
    public enum PushMode {
        AWAY,
        TOWARDS,
        LOOK
    }

    /** push <цель-сущность> upwards|downwards|forwards|backwards|north|south|east|west [with force N] */
    public record Push(EntityTarget target, String direction, @Nullable Expression force) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            double v = force == null ? 1.0 : Math.max(-8, Math.min(8, number(force, ctx)));
            String dir = direction.toLowerCase(java.util.Locale.ROOT);
            for (Entity e : target.resolve(ctx)) {
                Vec3 cur = e.getDeltaMovement();
                Vec3 look = e.getLookAngle();
                double dx = 0, dy = 0, dz = 0;
                switch (dir) {
                    case "up", "upwards" -> dy = v;
                    case "down", "downwards" -> dy = -v;
                    case "forward", "forwards" -> { dx = look.x * v; dy = look.y * v; dz = look.z * v; }
                    case "backward", "backwards" -> { dx = -look.x * v; dy = -look.y * v; dz = -look.z * v; }
                    case "north" -> dz = -v;
                    case "south" -> dz = v;
                    case "east" -> dx = v;
                    case "west" -> dx = -v;
                    default -> dy = v;
                }
                motion(e, cur.x + dx, cur.y + dy, cur.z + dz);
            }
        }
    }

    /**
     * push <цель-сущность> away from|from|towards|in direction of <источник> [with force N].
     * Позволяет толкнуть targeted entity / targeted player / player от игрока, другой сущности или точки.
     */
    public record PushFrom(EntityTarget target, @Nullable EntityTarget sourceEntity,
                           @Nullable Expression sourceLocation, PushMode mode,
                           @Nullable Expression force) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            double v = force == null ? 1.0 : Math.max(-8, Math.min(8, number(force, ctx)));
            Entity srcEntity = null;
            Vec3 srcPos = null;
            Vec3 srcLook = null;
            if (sourceEntity != null) {
                srcEntity = sourceEntity.first(ctx);
                if (srcEntity != null) {
                    srcPos = srcEntity.position();
                    srcLook = srcEntity.getLookAngle();
                } else if (sourceEntity instanceof EntityTarget.Players players && players.target() instanceof Target.Named named) {
                    ScriptLocation loc = Places.fromNamed(named, ctx);
                    if (loc != null) {
                        srcPos = new Vec3(loc.x(), loc.y(), loc.z());
                    }
                }
            } else if (sourceLocation != null) {
                ScriptLocation loc = Places.require(sourceLocation.evaluate(ctx));
                srcPos = new Vec3(loc.x(), loc.y(), loc.z());
            }
            if (srcPos == null && (mode != PushMode.LOOK || srcLook == null)) {
                return;
            }
            for (Entity e : target.resolve(ctx)) {
                if (srcEntity != null && e == srcEntity && mode != PushMode.LOOK) {
                    continue;
                }
                Vec3 dir;
                if (mode == PushMode.LOOK) {
                    dir = srcLook != null ? srcLook : e.getLookAngle();
                } else if (mode == PushMode.AWAY) {
                    Vec3 diff = e.position().subtract(srcPos);
                    if (diff.lengthSqr() < 1.0e-6) {
                        dir = srcLook != null ? srcLook : e.getLookAngle();
                    } else {
                        dir = diff.normalize();
                    }
                } else {
                    Vec3 diff = srcPos.subtract(e.position());
                    if (diff.lengthSqr() < 1.0e-6) {
                        continue;
                    }
                    dir = diff.normalize();
                }
                Vec3 cur = e.getDeltaMovement();
                double dx = dir.x * v;
                double dy = dir.y * v;
                double dz = dir.z * v;
                if (mode == PushMode.AWAY && v > 0 && e.onGround() && dy < 0.25) {
                    dy = Math.max(dy, Math.min(0.35, 0.2 * v));
                }
                motion(e, cur.x + dx, cur.y + dy, cur.z + dz);
            }
        }
    }

    /** explode / create [safe] explosion at ... [with force|power N]. */
    public record Explode(@Nullable Target target, @Nullable Expression x, @Nullable Expression y, @Nullable Expression z,
                          @Nullable Expression force, boolean safe) implements Action {
        public Explode(@Nullable Target target, @Nullable Expression x, @Nullable Expression y, @Nullable Expression z,
                       @Nullable Expression force) {
            this(target, x, y, z, force, false);
        }

        @Override
        public void execute(ExecutionContext ctx) {
            float power = force == null ? 3f : (float) Math.max(0, Math.min(8, number(force, ctx)));
            Level.ExplosionInteraction mode = safe ? Level.ExplosionInteraction.NONE : Level.ExplosionInteraction.BLOCK;
            if (x != null && y != null && z != null) {
                levelOf(ctx).explode(null, number(x, ctx), number(y, ctx), number(z, ctx), power, mode);
                return;
            }
            ScriptLocation loc = Places.fromNamed(target, ctx);
            if (loc != null) {
                Places.level(ctx.server(), loc).explode(null, loc.x(), loc.y(), loc.z(), power, mode);
                return;
            }
            for (ServerPlayer p : target.resolve(ctx)) {
                p.serverLevel().explode(null, p.getX(), p.getY(), p.getZ(), power, mode);
            }
        }
    }

    /** explode / create [safe] explosion at location of player [with force|power N]. */
    public record ExplodeAt(Expression place, @Nullable Expression force, boolean safe) implements Action {
        public ExplodeAt(Expression place, @Nullable Expression force) {
            this(place, force, false);
        }

        @Override
        public void execute(ExecutionContext ctx) {
            float power = force == null ? 3f : (float) Math.max(0, Math.min(8, number(force, ctx)));
            Level.ExplosionInteraction mode = safe ? Level.ExplosionInteraction.NONE : Level.ExplosionInteraction.BLOCK;
            ScriptLocation loc = Places.require(place.evaluate(ctx));
            Places.level(ctx.server(), loc).explode(null, loc.x(), loc.y(), loc.z(), power, mode);
        }
    }

    /** spawn particle <id> at <x y z | target | expr> [count N] [speed S] [offset dx dy dz] [color #RRGGBB] [for <listeners>] */
    public record SpawnParticle(String particleId, @Nullable Target target, @Nullable Expression place,
                                @Nullable Expression x, @Nullable Expression y, @Nullable Expression z,
                                @Nullable Expression count, @Nullable Expression speed,
                                @Nullable Expression dx, @Nullable Expression dy, @Nullable Expression dz,
                                @Nullable String hexColor, @Nullable Target listeners) implements Action {
        public SpawnParticle(String particleId, @Nullable Target target, @Nullable Expression place,
                             @Nullable Expression x, @Nullable Expression y, @Nullable Expression z,
                             @Nullable Expression count, @Nullable Expression speed) {
            this(particleId, target, place, x, y, z, count, speed, null, null, null, null, null);
        }

        @Override
        public void execute(ExecutionContext ctx) {
            ParticleOptions opts = resolveParticle(ctx.format(particleId), hexColor == null ? null : ctx.format(hexColor));
            int cnt = count == null ? 10 : (int) Math.max(1, Math.min(500, number(count, ctx)));
            double spd = speed == null ? 0.05 : Math.max(0, Math.min(5, number(speed, ctx)));
            double ox = dx == null ? 0.25 : Math.max(0, Math.min(32, number(dx, ctx)));
            double oy = dy == null ? 0.25 : Math.max(0, Math.min(32, number(dy, ctx)));
            double oz = dz == null ? 0.25 : Math.max(0, Math.min(32, number(dz, ctx)));
            if (x != null && y != null && z != null) {
                emit(ctx, levelOf(ctx), opts, number(x, ctx), number(y, ctx), number(z, ctx), cnt, ox, oy, oz, spd, listeners);
                return;
            }
            if (place != null) {
                ScriptLocation loc = Places.require(place.evaluate(ctx));
                emit(ctx, Places.level(ctx.server(), loc), opts, loc.x(), loc.y(), loc.z(), cnt, ox, oy, oz, spd, listeners);
                return;
            }
            if (target != null) {
                ScriptLocation loc = Places.fromNamed(target, ctx);
                if (loc != null) {
                    emit(ctx, Places.level(ctx.server(), loc), opts, loc.x(), loc.y(), loc.z(), cnt, ox, oy, oz, spd, listeners);
                    return;
                }
                for (ServerPlayer p : target.resolve(ctx)) {
                    emit(ctx, p.serverLevel(), opts, p.getX(), p.getY() + 1.0, p.getZ(), cnt, ox, oy, oz, spd, listeners);
                }
            }
        }

        static ParticleOptions resolveParticle(String rawId, @Nullable String hexColor) {
            String low = rawId.strip().toLowerCase(java.util.Locale.ROOT);
            if (low.equals("dust") || low.equals("minecraft:dust") || low.equals("redstone") || low.equals("minecraft:redstone")) {
                int rgb = parseHexRgb(hexColor, 0xFF3333);
                float r = ((rgb >> 16) & 0xFF) / 255f;
                float g = ((rgb >> 8) & 0xFF) / 255f;
                float b = (rgb & 0xFF) / 255f;
                return new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(r, g, b), 1.0f);
            }
            ResourceLocation rl = id(rawId);
            ParticleType<?> type = ForgeRegistries.PARTICLE_TYPES.getValue(rl);
            if (!(type instanceof SimpleParticleType simple)) {
                throw new ScriptRuntimeException("неизвестная или неподдерживаемая частица: " + rl);
            }
            return simple;
        }

        private static int parseHexRgb(@Nullable String raw, int fallback) {
            if (raw == null || raw.isBlank()) return fallback;
            String s = raw.strip();
            if (s.startsWith("#")) s = s.substring(1);
            if (s.length() == 8) s = s.substring(2);
            try {
                return Integer.parseInt(s, 16) & 0xFFFFFF;
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        private static void emit(ExecutionContext ctx, ServerLevel level, ParticleOptions opts,
                                 double px, double py, double pz, int cnt, double ox, double oy, double oz, double spd,
                                 @Nullable Target listeners) {
            if (listeners == null) {
                level.sendParticles(opts, px, py, pz, cnt, ox, oy, oz, spd);
                return;
            }
            for (ServerPlayer p : listeners.resolve(ctx)) {
                if (p.serverLevel() == level) {
                    level.sendParticles(p, opts, true, px, py, pz, cnt, ox, oy, oz, spd);
                }
            }
        }
    }

    /** spawn particle ring <id> at <target|loc> radius <r> [points <n>] [color #RRGGBB] */
    public record SpawnParticleRing(String particleId, @Nullable Target target, @Nullable Expression place,
                                    Expression radius, @Nullable Expression points, @Nullable String hexColor) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ParticleOptions opts = SpawnParticle.resolveParticle(ctx.format(particleId), hexColor == null ? null : ctx.format(hexColor));
            double rad = Math.max(0.1, Math.min(32.0, number(radius, ctx)));
            int pts = points == null ? 24 : (int) Math.max(4, Math.min(180, Math.round(number(points, ctx))));
            if (place != null) {
                ScriptLocation loc = Places.require(place.evaluate(ctx));
                drawRing(Places.level(ctx.server(), loc), opts, loc.x(), loc.y(), loc.z(), rad, pts);
                return;
            }
            if (target != null) {
                ScriptLocation loc = Places.fromNamed(target, ctx);
                if (loc != null) {
                    drawRing(Places.level(ctx.server(), loc), opts, loc.x(), loc.y(), loc.z(), rad, pts);
                    return;
                }
                for (ServerPlayer p : target.resolve(ctx)) {
                    drawRing(p.serverLevel(), opts, p.getX(), p.getY() + 0.15, p.getZ(), rad, pts);
                }
            }
        }

        private static void drawRing(ServerLevel level, ParticleOptions opts, double cx, double cy, double cz, double r, int pts) {
            for (int i = 0; i < pts; i++) {
                double ang = (Math.PI * 2.0 * i) / pts;
                level.sendParticles(opts, cx + Math.cos(ang) * r, cy, cz + Math.sin(ang) * r, 1, 0, 0, 0, 0);
            }
        }
    }

    /** shake camera of <target> [with intensity N] [for T ticks|seconds] */
    public record CameraShake(Target target, @Nullable Expression intensity, @Nullable Expression duration, int ticksPerUnit) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            float amp = intensity == null ? 1.5f : (float) Math.max(0.05, Math.min(15.0, number(intensity, ctx)));
            int ticks = duration == null ? 20 : (int) Math.max(1, Math.min(20 * 60, Math.round(number(duration, ctx) * ticksPerUnit)));
            com.scriptcraft.network.CameraEffectPacket pkt = com.scriptcraft.network.CameraEffectPacket.shake(amp, ticks);
            for (ServerPlayer p : target.resolve(ctx)) {
                com.scriptcraft.network.ModNetwork.sendTo(p, pkt);
            }
        }
    }

    /** fade screen of <target> [to #RRGGBB] [in N] [stay N] [out N] */
    public record CameraFade(Target target, @Nullable String hexColor, Expression fadeIn, Expression stay, Expression fadeOut, int ticksPerUnit) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            int argb = 0xFF000000;
            if (hexColor != null && !hexColor.isBlank()) {
                String s = ctx.format(hexColor).strip();
                if (s.startsWith("#")) s = s.substring(1);
                try {
                    long v = Long.parseLong(s, 16);
                    if (s.length() <= 6) v |= 0xFF000000L;
                    argb = (int) v;
                } catch (NumberFormatException ignored) {
                }
            }
            int inT = (int) Math.max(0, Math.min(20 * 60, Math.round(number(fadeIn, ctx) * ticksPerUnit)));
            int stayT = (int) Math.max(0, Math.min(20 * 60, Math.round(number(stay, ctx) * ticksPerUnit)));
            int outT = (int) Math.max(0, Math.min(20 * 60, Math.round(number(fadeOut, ctx) * ticksPerUnit)));
            com.scriptcraft.network.CameraEffectPacket pkt = com.scriptcraft.network.CameraEffectPacket.fade(argb, inT, stayT, outT);
            for (ServerPlayer p : target.resolve(ctx)) {
                com.scriptcraft.network.ModNetwork.sendTo(p, pkt);
            }
        }
    }

    /** set camera fov of <target> to <offset> [for T ticks|seconds] */
    public record CameraFov(Target target, Expression offset, @Nullable Expression duration, int ticksPerUnit) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            float off = (float) Math.max(-50.0, Math.min(60.0, number(offset, ctx)));
            int ticks = duration == null ? 0 : (int) Math.max(1, Math.min(20 * 300, Math.round(number(duration, ctx) * ticksPerUnit)));
            com.scriptcraft.network.CameraEffectPacket pkt = com.scriptcraft.network.CameraEffectPacket.fov(off, ticks);
            for (ServerPlayer p : target.resolve(ctx)) {
                com.scriptcraft.network.ModNetwork.sendTo(p, pkt);
            }
        }
    }

    /** roll/tilt camera of <target> by <degrees> [for T ticks|seconds] */
    public record CameraRoll(Target target, Expression degrees, @Nullable Expression duration, int ticksPerUnit) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            float deg = (float) Math.max(-180.0, Math.min(180.0, number(degrees, ctx)));
            int ticks = duration == null ? 0 : (int) Math.max(1, Math.min(20 * 300, Math.round(number(duration, ctx) * ticksPerUnit)));
            com.scriptcraft.network.CameraEffectPacket pkt = com.scriptcraft.network.CameraEffectPacket.roll(deg, ticks);
            for (ServerPlayer p : target.resolve(ctx)) {
                com.scriptcraft.network.ModNetwork.sendTo(p, pkt);
            }
        }
    }

    /** reset camera of <target> */
    public record CameraReset(Target target) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            com.scriptcraft.network.CameraEffectPacket pkt = com.scriptcraft.network.CameraEffectPacket.reset();
            for (ServerPlayer p : target.resolve(ctx)) {
                com.scriptcraft.network.ModNetwork.sendTo(p, pkt);
            }
        }
    }

    /** set scoreboard title "..." [for <target>] */
    public record ScoreboardTitle(Target target, String title) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String formatted = ctx.format(title);
            for (ServerPlayer p : target.resolve(ctx)) {
                ScoreboardManager.setTitle(p, formatted);
            }
        }
    }

    /** set scoreboard line N to "..." [for <target>] */
    public record ScoreboardLine(Target target, Expression line, String text) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            int num = (int) Math.round(number(line, ctx));
            String formatted = ctx.format(text);
            for (ServerPlayer p : target.resolve(ctx)) {
                ScoreboardManager.setLine(p, num, formatted);
            }
        }
    }

    /** clear scoreboard [of <target>] */
    public record ScoreboardClear(Target target) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            for (ServerPlayer p : target.resolve(ctx)) {
                ScoreboardManager.clear(p);
            }
        }
    }

    /** spawn entity <id> at <loc|target|xyz> [named "..."] [with health N] [with nbt "..."] [with weapon/helmet/...] */
    public record SpawnEntity(String entityId, @Nullable Target target, @Nullable Expression place,
                              @Nullable Expression x, @Nullable Expression y, @Nullable Expression z,
                              @Nullable String customName, @Nullable Expression health, @Nullable String snbt,
                              @Nullable java.util.Map<EquipmentSlot, String> equipment) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String formattedId = ctx.format(entityId).strip();
            String lookupId = formattedId.contains(":") ? formattedId.toLowerCase(java.util.Locale.ROOT)
                    : (ScriptCraft.MODID + ":" + formattedId.toLowerCase(java.util.Locale.ROOT));
            var customGeoDef = com.scriptcraft.script.ScriptLoader.getProgram().entity(lookupId);
            if (customGeoDef == null && !formattedId.contains(":")) {
                // Проверяем по короткому имени среди всех define entity
                for (var entry : com.scriptcraft.script.ScriptLoader.getProgram().entities().entrySet()) {
                    if (entry.getKey().endsWith(":" + formattedId.toLowerCase(java.util.Locale.ROOT))) {
                        customGeoDef = entry.getValue();
                        break;
                    }
                }
            }
            EntityType<?> type;
            if (customGeoDef != null) {
                type = com.scriptcraft.registry.ScriptRegistries.GEO_ENTITY.get();
            } else {
                ResourceLocation rl = id(formattedId);
                type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
                if (type == null || !ForgeRegistries.ENTITY_TYPES.containsKey(rl)) {
                    throw new ScriptRuntimeException("неизвестная сущность: " + rl);
                }
            }
            final var resolvedGeoDef = customGeoDef;
            if (x != null && y != null && z != null) {
                spawnOne(ctx, levelOf(ctx), type, resolvedGeoDef, number(x, ctx), number(y, ctx), number(z, ctx));
                return;
            }
            if (place != null) {
                ScriptLocation loc = Places.require(place.evaluate(ctx));
                spawnOne(ctx, Places.level(ctx.server(), loc), type, resolvedGeoDef, loc.x(), loc.y(), loc.z());
                return;
            }
            if (target != null) {
                ScriptLocation loc = Places.fromNamed(target, ctx);
                if (loc != null) {
                    spawnOne(ctx, Places.level(ctx.server(), loc), type, resolvedGeoDef, loc.x(), loc.y(), loc.z());
                    return;
                }
                for (ServerPlayer p : target.resolve(ctx)) {
                    spawnOne(ctx, p.serverLevel(), type, resolvedGeoDef, p.getX(), p.getY(), p.getZ());
                }
            }
        }

        private void spawnOne(ExecutionContext ctx, ServerLevel level, EntityType<?> type,
                              @Nullable com.scriptcraft.script.parser.EntityDefinition geoDef,
                              double sx, double sy, double sz) {
            Entity entity = type.create(level);
            if (entity == null) {
                return;
            }
            entity.moveTo(sx, sy, sz, 0f, 0f);
            if (geoDef != null && entity instanceof com.scriptcraft.entity.ScriptGeoEntity geoEntity) {
                geoEntity.applyDefinition(geoDef);
            }
            if (snbt != null && !snbt.isBlank()) {
                try {
                    CompoundTag merged = entity.saveWithoutId(new CompoundTag());
                    CompoundTag extra = TagParser.parseTag(ctx.format(snbt));
                    merged.merge(extra);
                    entity.load(merged);
                    entity.moveTo(sx, sy, sz, entity.getYRot(), entity.getXRot());
                } catch (Exception e) {
                    throw new ScriptRuntimeException("ошибка NBT у spawn entity: " + e.getMessage());
                }
            }
            if (customName != null) {
                entity.setCustomName(Component.literal(ctx.format(customName)));
                entity.setCustomNameVisible(true);
            }
            if (entity instanceof LivingEntity living) {
                if (health != null) {
                    double hp = Math.max(1.0, Math.min(2048.0, number(health, ctx)));
                    AttributeInstance attr = living.getAttribute(Attributes.MAX_HEALTH);
                    if (attr != null) {
                        attr.setBaseValue(hp);
                    }
                    living.setHealth((float) hp);
                }
                if (equipment != null) {
                    for (var entry : equipment.entrySet()) {
                        ItemStack stack = ItemStacks.create(ctx.format(entry.getValue()), 1);
                        living.setItemSlot(entry.getKey(), stack);
                    }
                }
            }
            level.addFreshEntity(entity);
            LAST_SPAWNED_UUID = entity.getUUID();
            ctx.setPlaceholder("last_spawned_uuid", entity.getStringUUID());
        }
    }

    private static volatile java.util.UUID LAST_SPAWNED_UUID;

    @Nullable
    public static Entity resolveLastSpawned(ExecutionContext ctx) {
        String local = ctx.placeholderOrNull("last_spawned_uuid");
        java.util.UUID id = null;
        if (local != null && !local.isBlank()) {
            try {
                id = java.util.UUID.fromString(local);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (id == null) {
            id = LAST_SPAWNED_UUID;
        }
        return id == null ? null : LeashManager.findEntity(ctx.server(), id, null);
    }

    /**
     * leash <entity> to <entity | block at x y z | targeted block | location> [опции поводка...]
     */
    public record AttachLeash(
            EntityTarget subject,
            @Nullable EntityTarget holderEntity,
            boolean toTargetedBlock,
            @Nullable Expression bx,
            @Nullable Expression by,
            @Nullable Expression bz,
            @Nullable Expression locExpr,
            @Nullable Expression maxLen,
            @Nullable Expression minLen,
            @Nullable Expression stiffness,
            @Nullable Expression breakDist,
            @Nullable Boolean breakableFlag,
            @Nullable Expression colorExpr,
            @Nullable Expression widthExpr,
            @Nullable Expression sagExpr,
            @Nullable Expression twoWayExpr,
            @Nullable Expression knotExpr,
            @Nullable Expression dropLeadExpr
    ) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            List<Entity> subjects = subject.resolve(ctx);
            if (subjects.isEmpty()) {
                return;
            }
            float len = maxLen != null ? (float) number(maxLen, ctx) : 6.0f;
            float mLen = minLen != null ? (float) number(minLen, ctx) : 0.0f;
            float stiff = stiffness != null ? (float) number(stiffness, ctx) : 1.0f;
            float brk = 0.0f;
            if (breakDist != null) {
                brk = (float) number(breakDist, ctx);
            } else if (breakableFlag != null) {
                brk = breakableFlag ? len + 4.0f : 0.0f;
            }
            int col = colorExpr != null
                    ? LeashManager.parseHexColor(Expression.toText(colorExpr.evaluate(ctx)), LeashManager.DEFAULT_COLOR)
                    : LeashManager.DEFAULT_COLOR;
            float w = widthExpr != null ? (float) number(widthExpr, ctx) : 1.0f;
            float s = sagExpr != null ? (float) number(sagExpr, ctx) : 1.0f;
            boolean bidir = twoWayExpr != null && Boolean.parseBoolean(Expression.toText(twoWayExpr.evaluate(ctx)).strip());
            boolean knot = knotExpr == null || !"false".equalsIgnoreCase(Expression.toText(knotExpr.evaluate(ctx)).strip());
            boolean drop = dropLeadExpr != null && Boolean.parseBoolean(Expression.toText(dropLeadExpr.evaluate(ctx)).strip());

            LeashManager.Config cfg = new LeashManager.Config(len, mLen, stiff, brk, bidir, col, w, s, knot, drop);

            if (toTargetedBlock) {
                ServerPlayer viewer = ctx.player();
                if (viewer == null) {
                    return;
                }
                net.minecraft.world.phys.BlockHitResult hit = Look.sight(viewer).block();
                if (hit == null) {
                    return;
                }
                for (Entity sub : subjects) {
                    LeashManager.attachToBlock(ctx.server(), sub, viewer.serverLevel(), hit.getBlockPos(), cfg);
                }
                return;
            }

            if (bx != null && by != null && bz != null) {
                ServerLevel lvl = levelOf(ctx);
                BlockPos bp = BlockPos.containing(number(bx, ctx), number(by, ctx), number(bz, ctx));
                for (Entity sub : subjects) {
                    ServerLevel subLvl = sub.level() instanceof ServerLevel sl ? sl : lvl;
                    LeashManager.attachToBlock(ctx.server(), sub, subLvl, bp, cfg);
                }
                return;
            }

            if (locExpr != null) {
                ScriptLocation loc = Places.require(locExpr.evaluate(ctx));
                ServerLevel lvl = Places.level(ctx.server(), loc);
                BlockPos bp = BlockPos.containing(loc.x(), loc.y(), loc.z());
                for (Entity sub : subjects) {
                    LeashManager.attachToBlock(ctx.server(), sub, lvl, bp, cfg);
                }
                return;
            }

            if (holderEntity != null) {
                // Если в holderEntity передали {var} с локацией (например, leash player to {anchor_loc}) — поддержим!
                if (holderEntity instanceof EntityTarget.Players pl && pl.target() instanceof Target.Named named) {
                    ScriptLocation maybeLoc = ScriptLocation.decode(named.name().evaluate(ctx));
                    if (maybeLoc != null) {
                        ServerLevel lvl = Places.level(ctx.server(), maybeLoc);
                        BlockPos bp = BlockPos.containing(maybeLoc.x(), maybeLoc.y(), maybeLoc.z());
                        for (Entity sub : subjects) {
                            LeashManager.attachToBlock(ctx.server(), sub, lvl, bp, cfg);
                        }
                        return;
                    }
                }
                Entity holder = holderEntity.first(ctx);
                if (holder == null) {
                    return;
                }
                for (Entity sub : subjects) {
                    if (sub != holder) {
                        LeashManager.attachToEntity(ctx.server(), sub, holder, cfg);
                    }
                }
            }
        }
    }

    /** set leash <prop> of <entity> to <expr> */
    public record SetLeashProperty(String property, EntityTarget target, Expression value) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            Object v = value.evaluate(ctx);
            for (Entity e : target.resolve(ctx)) {
                LeashManager.updateProperty(ctx.server(), e, property, v);
            }
        }
    }

    /** unleash <entity> */
    public record UnleashEntity(EntityTarget target) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            for (Entity e : target.resolve(ctx)) {
                LeashManager.unleash(ctx.server(), e, false, true);
            }
        }
    }

    /** clear [all] leashes */
    public record ClearLeashes() implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            LeashManager.clearAll(ctx.server());
        }
    }

    /** Преобразует &-цвета в Component. */
    public static Component colored(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        return Component.literal(raw.replace('&', '\u00a7'));
    }

    /** replace <find> with <repl> in {<var>} */
    public record ReplaceInVar(Expression find, Expression repl, String varName) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String key = ctx.format(varName);
            Object raw = com.scriptcraft.script.variables.VariableStore.get(key);
            if (raw == null) {
                return;
            }
            String src = Expression.toText(raw);
            String f = Expression.toText(find.evaluate(ctx));
            String r = Expression.toText(repl.evaluate(ctx));
            if (!f.isEmpty()) {
                com.scriptcraft.script.variables.VariableStore.set(key, src.replace(f, r));
            }
        }
    }

    /** split <text> by <sep> into {<list>::*} */
    public record SplitIntoList(Expression text, Expression sep, String listPrefix) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String prefix = ctx.format(listPrefix);
            com.scriptcraft.script.variables.VariableStore.deleteChildren(prefix);
            String src = Expression.toText(text.evaluate(ctx));
            String delimiter = Expression.toText(sep.evaluate(ctx));
            String[] parts = delimiter.isEmpty()
                    ? src.split("")
                    : src.split(java.util.regex.Pattern.quote(delimiter), -1);
            int idx = 1;
            for (String part : parts) {
                com.scriptcraft.script.variables.VariableStore.set(prefix + idx, part);
                idx++;
            }
        }
    }

    /** set max health of <target> to <N> */
    public record SetMaxHealth(Target target, Expression value) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            double maxHp = Math.max(1.0, Math.min(2048.0, number(value, ctx)));
            for (ServerPlayer p : target.resolve(ctx)) {
                AttributeInstance attr = p.getAttribute(Attributes.MAX_HEALTH);
                if (attr != null) {
                    attr.setBaseValue(maxHp);
                    if (p.getHealth() > maxHp) {
                        p.setHealth((float) maxHp);
                    }
                }
            }
        }
    }

    /** set / add / clear lore of slot / player's tool */
    public record ItemLore(Target target, @Nullable Expression slot, boolean hotbar, boolean heldTool,
                           String mode, List<Expression> lines) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            List<String> resolved = new java.util.ArrayList<>();
            for (Expression lineExpr : lines) {
                String txt = Expression.toText(lineExpr.evaluate(ctx)).replace("\\n", "\n");
                for (String piece : txt.split("\n")) {
                    resolved.add(piece);
                }
            }
            for (ServerPlayer p : target.resolve(ctx)) {
                ItemStack stack = resolveTargetStack(p, slot, hotbar, heldTool, ctx);
                if (stack.isEmpty()) {
                    continue;
                }
                if ("clear".equals(mode)) {
                    CompoundTag display = stack.getTagElement("display");
                    if (display != null) {
                        display.remove("Lore");
                    }
                    continue;
                }
                CompoundTag display = stack.getOrCreateTagElement("display");
                net.minecraft.nbt.ListTag loreTag = "add".equals(mode) && display.contains("Lore", 9)
                        ? display.getList("Lore", 8)
                        : new net.minecraft.nbt.ListTag();
                for (String line : resolved) {
                    Component comp = colored(line).copy().withStyle(s -> s.withItalic(false));
                    loreTag.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(comp)));
                }
                display.put("Lore", loreTag);
            }
        }
    }

    /** enchant slot / player's tool with <id> [level <N>] */
    public record EnchantItem(Target target, @Nullable Expression slot, boolean hotbar, boolean heldTool,
                              Expression enchantId, @Nullable Expression level) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String rawId = Expression.toText(enchantId.evaluate(ctx)).strip().toLowerCase(java.util.Locale.ROOT);
            if (!rawId.contains(":")) {
                rawId = "minecraft:" + rawId;
            }
            ResourceLocation rl = ResourceLocation.tryParse(rawId);
            if (rl == null) {
                throw new ScriptRuntimeException("неизвестное зачарование: " + rawId);
            }
            net.minecraft.world.item.enchantment.Enchantment ench =
                    net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getValue(rl);
            if (ench == null) {
                throw new ScriptRuntimeException("зачарование не найдено: " + rawId);
            }
            int lvl = level == null ? 1 : Math.max(1, Math.min(255, (int) Math.round(number(level, ctx))));
            for (ServerPlayer p : target.resolve(ctx)) {
                ItemStack stack = resolveTargetStack(p, slot, hotbar, heldTool, ctx);
                if (!stack.isEmpty()) {
                    stack.enchant(ench, lvl);
                }
            }
        }
    }

    private static ItemStack resolveTargetStack(ServerPlayer p, @Nullable Expression slot, boolean hotbar,
                                                boolean heldTool, ExecutionContext ctx) {
        if (heldTool || slot == null) {
            return p.getMainHandItem();
        }
        int idx = resolveSlotIdx(slot.evaluate(ctx), hotbar, p);
        if (idx < 0) {
            return ItemStack.EMPTY;
        }
        return p.getInventory().getItem(idx);
    }

    private static int resolveSlotIdx(@Nullable Object raw, boolean hotbar, ServerPlayer p) {
        if (hotbar) {
            Double n = Expression.toNumber(raw);
            if (n == null) return -1;
            int idx = (int) Math.round(n) - 1;
            return (idx >= 0 && idx <= 8) ? idx : -1;
        }
        String text = Expression.toText(raw).strip().toLowerCase(java.util.Locale.ROOT);
        return switch (text) {
            case "boots" -> 36;
            case "leggings" -> 37;
            case "chestplate" -> 38;
            case "helmet" -> 39;
            case "offhand" -> 40;
            case "mainhand" -> p.getInventory().selected;
            default -> {
                Double n = Expression.toNumber(raw);
                if (n == null) yield -1;
                int idx = (int) Math.round(n);
                yield (idx >= 0 && idx <= 40) ? idx : -1;
            }
        };
    }

    /** animate screen "<id>" [for <target>] to <X> <Y> [in <T> ticks|seconds] */
    public record AnimateScreenElement(Expression id, @Nullable Target target, Expression x, Expression y,
                                       @Nullable Expression durationTicks) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String elemId = Expression.toText(id.evaluate(ctx));
            int tx = (int) Math.round(number(x, ctx));
            int ty = (int) Math.round(number(y, ctx));
            int ticks = durationTicks == null ? 6 : Math.max(1, Math.min(400, (int) Math.round(number(durationTicks, ctx))));
            int durMs = ticks * 50;
            List<ServerPlayer> players = target == null
                    ? (ctx.player() == null ? List.of() : List.of(ctx.player()))
                    : target.resolve(ctx);
            for (ServerPlayer p : players) {
                com.scriptcraft.screen.ScreenManager.animatePosition(p, elemId, tx, ty, durMs);
            }
        }
    }

    /** 3D-голограммы TextDisplay (1.20.1). */
    public record HologramAction(String op, @Nullable Expression id, @Nullable Expression text,
                                 @Nullable Expression location, @Nullable Target target,
                                 @Nullable Expression x, @Nullable Expression y, @Nullable Expression z,
                                 @Nullable Expression scale, @Nullable String billboard) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            if ("clear".equals(op)) {
                HologramManager.clearAll(ctx.server());
                return;
            }
            String holoId = id == null ? "" : Expression.toText(id.evaluate(ctx));
            if ("remove".equals(op)) {
                HologramManager.remove(ctx.server(), holoId);
                return;
            }
            if ("text".equals(op)) {
                HologramManager.setText(ctx.server(), holoId, text == null ? "" : Expression.toText(text.evaluate(ctx)));
                return;
            }
            ServerLevel level = levelOf(ctx);
            double px, py, pz;
            if (location != null) {
                ScriptLocation loc = Places.require(location.evaluate(ctx));
                ServerLevel resolved = Places.level(ctx.server(), loc);
                if (resolved != null) {
                    level = resolved;
                }
                px = loc.x();
                py = loc.y();
                pz = loc.z();
            } else if (target != null) {
                ServerPlayer p = target.first(ctx);
                if (p == null) {
                    return;
                }
                level = p.serverLevel();
                px = p.getX();
                py = p.getY() + 2.2;
                pz = p.getZ();
            } else {
                px = number(x, ctx);
                py = number(y, ctx);
                pz = number(z, ctx);
            }
            if ("move".equals(op)) {
                HologramManager.move(ctx.server(), level, holoId, px, py, pz);
            } else if ("create".equals(op)) {
                float sc = scale == null ? 1.0f : (float) number(scale, ctx);
                String txt = text == null ? "" : Expression.toText(text.evaluate(ctx));
                HologramManager.create(ctx.server(), level, holoId, txt, px, py, pz, sc, billboard == null ? "center" : billboard);
            }
        }
    }

    /** Ванильные боссбары (ServerBossEvent). */
    public record BossBarAction(String op, @Nullable Expression id, @Nullable Expression title,
                                @Nullable String color, @Nullable String style,
                                @Nullable Expression progress, @Nullable Target target) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            if ("clear".equals(op)) {
                BossBarManager.clearAll();
                return;
            }
            String barId = id == null ? "" : Expression.toText(id.evaluate(ctx));
            if ("remove".equals(op)) {
                BossBarManager.remove(barId);
                return;
            }
            List<ServerPlayer> players = target == null
                    ? (ctx.player() == null ? List.of() : List.of(ctx.player()))
                    : target.resolve(ctx);
            switch (op) {
                case "create" -> {
                    String t = title == null ? barId : Expression.toText(title.evaluate(ctx));
                    double prog = progress == null ? 1.0 : number(progress, ctx);
                    BossBarManager.create(barId, t, color == null ? "red" : color, style == null ? "progress" : style, prog, players);
                }
                case "title" -> BossBarManager.setTitle(barId, title == null ? "" : Expression.toText(title.evaluate(ctx)));
                case "progress" -> BossBarManager.setProgress(barId, progress == null ? 1.0 : number(progress, ctx));
                case "color" -> BossBarManager.setColor(barId, color == null ? "red" : color);
                case "style" -> BossBarManager.setStyle(barId, style == null ? "progress" : style);
                case "show" -> BossBarManager.show(barId, players);
                case "hide" -> BossBarManager.hide(barId, players);
                default -> {
                }
            }
        }
    }

    /** play [player] animation <id> [on <цель>] [for <зрители>] [first person|no first person] [speed N] [fade N] */
    public record PlayAnimation(Expression animation, @Nullable Target target, @Nullable Target viewers,
                                @Nullable Expression firstPerson, boolean firstPersonDefault,
                                @Nullable Expression speed, @Nullable Expression fadeTicks) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String animId = Expression.toText(animation.evaluate(ctx)).strip();
            if (animId.isEmpty()) {
                return;
            }
            boolean fp = firstPersonDefault;
            if (firstPerson != null) {
                fp = bool(firstPerson, ctx);
            }
            float spd = speed == null ? 1.0f : (float) Math.max(0.05, Math.min(10.0, number(speed, ctx)));
            int fade = fadeTicks == null ? 4 : Math.max(0, Math.min(40, (int) Math.round(number(fadeTicks, ctx))));

            List<ServerPlayer> animated = target == null
                    ? List.of(ctx.requirePlayer())
                    : target.resolve(ctx);
            if (animated.isEmpty()) {
                return;
            }
            List<ServerPlayer> audience = viewers == null ? null : viewers.resolve(ctx);
            for (ServerPlayer subject : animated) {
                com.scriptcraft.network.PlayerAnimPacket pkt = com.scriptcraft.network.PlayerAnimPacket.play(
                        subject.getUUID(), animId, fp, spd, fade);
                if (audience != null) {
                    for (ServerPlayer viewer : audience) {
                        com.scriptcraft.network.ModNetwork.sendTo(viewer, pkt);
                    }
                } else {
                    for (ServerPlayer viewer : subject.serverLevel().players()) {
                        com.scriptcraft.network.ModNetwork.sendTo(viewer, pkt);
                    }
                }
            }
        }
    }

    /** stop [player] animation [on|of <цель>] [for <зрители>] [fade N] */
    public record StopAnimation(@Nullable Target target, @Nullable Target viewers,
                                @Nullable Expression fadeTicks) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            int fade = fadeTicks == null ? 4 : Math.max(0, Math.min(40, (int) Math.round(number(fadeTicks, ctx))));
            List<ServerPlayer> animated = target == null
                    ? List.of(ctx.requirePlayer())
                    : target.resolve(ctx);
            if (animated.isEmpty()) {
                return;
            }
            List<ServerPlayer> audience = viewers == null ? null : viewers.resolve(ctx);
            for (ServerPlayer subject : animated) {
                com.scriptcraft.network.PlayerAnimPacket pkt = com.scriptcraft.network.PlayerAnimPacket.stop(
                        subject.getUUID(), fade);
                if (audience != null) {
                    for (ServerPlayer viewer : audience) {
                        com.scriptcraft.network.ModNetwork.sendTo(viewer, pkt);
                    }
                } else {
                    for (ServerPlayer viewer : subject.serverLevel().players()) {
                        com.scriptcraft.network.ModNetwork.sendTo(viewer, pkt);
                    }
                }
            }
        }
    }

    private static void motion(Entity e, double x, double y, double z) {
        e.setDeltaMovement(x, y, z);
        e.hurtMarked = true;
        e.hasImpulse = true;
        if (e instanceof ServerPlayer sp) {
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
        }
    }

    private static ServerLevel levelOf(ExecutionContext ctx) {
        return ctx.player() != null ? ctx.player().serverLevel() : ctx.server().overworld();
    }

    private static void bolt(ServerLevel level, double x, double y, double z, boolean visualOnly) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }
        bolt.moveTo(x, y, z);
        bolt.setVisualOnly(visualOnly);
        level.addFreshEntity(bolt);
    }

    // =========================================================================
    // 3D GECKOLIB / BLOCKBENCH СУЩНОСТИ, ПРЕДМЕТЫ, БЛОКИ, NPC, АТТАЧИ И КАТСЦЕНЫ
    // =========================================================================

    private static ServerLevel resolvePosAndLevel(ExecutionContext ctx, @Nullable Target target, @Nullable Expression place,
                                                  @Nullable Expression x, @Nullable Expression y, @Nullable Expression z,
                                                  double[] outXyz) {
        if (x != null && y != null && z != null) {
            outXyz[0] = number(x, ctx);
            outXyz[1] = number(y, ctx);
            outXyz[2] = number(z, ctx);
            return levelOf(ctx);
        }
        if (place != null) {
            ScriptLocation loc = Places.require(place.evaluate(ctx));
            outXyz[0] = loc.x();
            outXyz[1] = loc.y();
            outXyz[2] = loc.z();
            return Places.level(ctx.server(), loc);
        }
        if (target != null) {
            ScriptLocation loc = Places.fromNamed(target, ctx);
            if (loc != null) {
                outXyz[0] = loc.x();
                outXyz[1] = loc.y();
                outXyz[2] = loc.z();
                return Places.level(ctx.server(), loc);
            }
            ServerPlayer p = target.first(ctx);
            if (p != null) {
                outXyz[0] = p.getX();
                outXyz[1] = p.getY();
                outXyz[2] = p.getZ();
                return p.serverLevel();
            }
        }
        ServerPlayer self = ctx.player();
        if (self != null) {
            outXyz[0] = self.getX();
            outXyz[1] = self.getY();
            outXyz[2] = self.getZ();
            return self.serverLevel();
        }
        outXyz[0] = 0;
        outXyz[1] = 64;
        outXyz[2] = 0;
        return ctx.server().overworld();
    }

    /** spawn geo [entity|mob|boss|turret|prop] <idExpr> at <pos> [...] */
    public record SpawnGeoEntity(
            Expression idOrModel,
            @Nullable Target atTarget, @Nullable Expression atPlace,
            @Nullable Expression x, @Nullable Expression y, @Nullable Expression z,
            @Nullable Expression modelOverride,
            @Nullable Expression texOverride,
            @Nullable Expression animOverride,
            @Nullable String customName,
            @Nullable Expression health,
            @Nullable Expression damage,
            @Nullable Expression scale,
            float hitboxW, float hitboxH,
            @Nullable String aiOverride
    ) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            double[] xyz = new double[3];
            ServerLevel level = resolvePosAndLevel(ctx, atTarget, atPlace, x, y, z, xyz);
            var geo = com.scriptcraft.registry.ScriptRegistries.GEO_ENTITY.get().create(level);
            if (geo == null) return;
            geo.moveTo(xyz[0], xyz[1], xyz[2], ctx.player() != null ? ctx.player().getYRot() + 180f : 0f, 0f);

            String rawId = Expression.toText(idOrModel.evaluate(ctx)).strip();
            String fullId = rawId.contains(":") ? rawId.toLowerCase(java.util.Locale.ROOT)
                    : (ScriptCraft.MODID + ":" + rawId.toLowerCase(java.util.Locale.ROOT));
            var def = com.scriptcraft.script.ScriptLoader.getProgram().entity(fullId);
            if (def == null && !rawId.contains(":")) {
                for (var e : com.scriptcraft.script.ScriptLoader.getProgram().entities().entrySet()) {
                    if (e.getKey().endsWith(":" + rawId.toLowerCase(java.util.Locale.ROOT))) {
                        def = e.getValue();
                        break;
                    }
                }
            }
            if (def != null) {
                geo.applyDefinition(def);
            } else {
                geo.setDefId(fullId);
                geo.setModelPath(rawId);
                geo.setTexturePath(rawId);
                geo.setAnimFilePath(rawId);
                geo.setAiMode(aiOverride != null ? aiOverride : "hostile");
            }

            if (modelOverride != null) geo.setModelPath(Expression.toText(modelOverride.evaluate(ctx)).strip());
            if (texOverride != null) geo.setTexturePath(Expression.toText(texOverride.evaluate(ctx)).strip());
            if (animOverride != null) geo.setAnimFilePath(Expression.toText(animOverride.evaluate(ctx)).strip());
            if (customName != null) {
                geo.setCustomName(colored(ctx.format(customName)));
                geo.setCustomNameVisible(true);
            }
            if (health != null) {
                double hp = Math.max(1.0, number(health, ctx));
                AttributeInstance attr = geo.getAttribute(Attributes.MAX_HEALTH);
                if (attr != null) attr.setBaseValue(hp);
                geo.setHealth((float) hp);
            }
            if (damage != null) {
                AttributeInstance attr = geo.getAttribute(Attributes.ATTACK_DAMAGE);
                if (attr != null) attr.setBaseValue(Math.max(0.0, number(damage, ctx)));
            }
            if (scale != null) {
                geo.setGeoScale((float) number(scale, ctx));
            }
            if (hitboxW > 0f && hitboxH > 0f) {
                geo.setHitbox(hitboxW, hitboxH);
            }
            if (aiOverride != null) {
                geo.setAiMode(aiOverride);
            }

            level.addFreshEntity(geo);
            LAST_SPAWNED_UUID = geo.getUUID();
            ctx.setPlaceholder("last_spawned_uuid", geo.getStringUUID());
        }
    }

    /** play/stop geo animation / set geo texture / set geo scale / clear geo entities */
    public record GeoEntityControl(
            String op,
            @Nullable EntityTarget target,
            @Nullable Expression value,
            int loopMode,
            @Nullable Expression speed
    ) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            if ("clear_all".equals(op)) {
                for (ServerLevel lvl : ctx.server().getAllLevels()) {
                    List<com.scriptcraft.entity.ScriptGeoEntity> list = new java.util.ArrayList<>();
                    for (Entity e : lvl.getAllEntities()) {
                        if (e instanceof com.scriptcraft.entity.ScriptGeoEntity g) list.add(g);
                    }
                    list.forEach(Entity::discard);
                }
                return;
            }
            if (target == null) return;
            float spd = speed == null ? 1.0f : (float) number(speed, ctx);
            String txt = value == null ? "" : Expression.toText(value.evaluate(ctx)).strip();
            for (Entity e : target.resolve(ctx)) {
                if (e instanceof com.scriptcraft.entity.ScriptGeoEntity geo) {
                    switch (op) {
                        case "play" -> geo.playAnimation(txt, loopMode, spd);
                        case "stop" -> geo.stopAnimation();
                        case "texture" -> geo.setTexturePath(txt);
                        case "scale" -> geo.setGeoScale((float) number(value, ctx));
                        default -> {
                        }
                    }
                }
            }
        }
    }

    /** play / stop item animation <anim> [for <target>] [mainhand|offhand] [loop|once] [speed N] */
    public record GeoItemAnim(
            @Nullable Expression animName,
            @Nullable Target target,
            boolean offhand,
            int loopMode,
            @Nullable Expression speed
    ) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            String anim = animName == null ? "" : Expression.toText(animName.evaluate(ctx)).strip();
            float spd = speed == null ? 1.0f : (float) number(speed, ctx);
            List<ServerPlayer> players = target == null
                    ? (ctx.player() == null ? List.of() : List.of(ctx.player()))
                    : target.resolve(ctx);
            for (ServerPlayer p : players) {
                var pkt = com.scriptcraft.network.GeoSyncPacket.itemAnim(p.getUUID(), offhand, anim, loopMode, spd);
                for (ServerPlayer viewer : p.serverLevel().players()) {
                    com.scriptcraft.network.ModNetwork.sendTo(viewer, pkt);
                }
            }
        }
    }

    /** play / stop block animation <anim> at <targeted block | x y z | expr> [loop|once|hold] [speed N] */
    public record GeoBlockAnim(
            @Nullable Expression animName,
            boolean targetedBlock,
            @Nullable Expression place,
            @Nullable Expression x, @Nullable Expression y, @Nullable Expression z,
            int loopMode,
            @Nullable Expression speed
    ) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ServerLevel level;
            BlockPos pos;
            if (targetedBlock) {
                ServerPlayer viewer = ctx.requirePlayer();
                String enc = Places.blockLocation(viewer);
                if (enc == null) return;
                ScriptLocation loc = Places.require(enc);
                level = Places.level(ctx.server(), loc);
                pos = BlockPos.containing(loc.x(), loc.y(), loc.z());
            } else if (x != null && y != null && z != null) {
                level = levelOf(ctx);
                pos = BlockPos.containing(number(x, ctx), number(y, ctx), number(z, ctx));
            } else if (place != null) {
                ScriptLocation loc = Places.require(place.evaluate(ctx));
                level = Places.level(ctx.server(), loc);
                pos = BlockPos.containing(loc.x(), loc.y(), loc.z());
            } else {
                return;
            }
            String anim = animName == null ? "" : Expression.toText(animName.evaluate(ctx)).strip();
            float spd = speed == null ? 1.0f : (float) number(speed, ctx);
            if (level.getBlockEntity(pos) instanceof com.scriptcraft.registry.ScriptBlockEntity sbe) {
                sbe.triggerAnimation(anim, loopMode, spd);
            }
            var pkt = com.scriptcraft.network.GeoSyncPacket.blockAnim(pos, anim, loopMode, spd);
            for (ServerPlayer viewer : level.players()) {
                if (viewer.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 96.0 * 96.0) {
                    com.scriptcraft.network.ModNetwork.sendTo(viewer, pkt);
                }
            }
        }
    }

    /** Управление фейковыми игроками-NPC (spawn, remove, clear, skin, name, equip, anim, stop_anim, look, walk). */
    public record NpcAction(
            String op,
            @Nullable Expression npcId,
            @Nullable Target atTarget, @Nullable Expression atPlace,
            @Nullable Expression x, @Nullable Expression y, @Nullable Expression z,
            @Nullable Expression strArg,
            @Nullable String modelStyle,
            @Nullable Boolean lookPlayers,
            @Nullable Boolean vulnerable,
            @Nullable Boolean sneaking,
            @Nullable EquipmentSlot slot,
            @Nullable Expression numArg
    ) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            if ("clear".equals(op)) {
                NpcManager.clearAll(ctx.server());
                return;
            }
            String id = npcId == null ? "" : Expression.toText(npcId.evaluate(ctx)).strip();
            if (id.isEmpty()) return;

            if ("remove".equals(op)) {
                NpcManager.remove(ctx.server(), id);
                return;
            }
            if ("spawn".equals(op)) {
                NpcManager.remove(ctx.server(), id);
                double[] xyz = new double[3];
                ServerLevel level = resolvePosAndLevel(ctx, atTarget, atPlace, x, y, z, xyz);
                var npc = com.scriptcraft.registry.ScriptRegistries.FAKE_NPC.get().create(level);
                if (npc == null) return;
                float yaw = ctx.player() != null ? ctx.player().getYRot() + 180f : 0f;
                npc.moveTo(xyz[0], xyz[1], xyz[2], yaw, 0f);
                npc.setYHeadRot(yaw);
                npc.setYBodyRot(yaw);
                npc.setNpcId(id);
                if (strArg != null) {
                    npc.setSkinSource(Expression.toText(strArg.evaluate(ctx)).strip());
                } else {
                    npc.setSkinSource(id);
                }
                if (modelStyle != null) npc.setModelStyle(modelStyle);
                if (lookPlayers != null) npc.setLookAtPlayers(lookPlayers);
                if (vulnerable != null) npc.setVulnerable(vulnerable);
                if (sneaking != null) npc.setNpcSneaking(sneaking);
                if (numArg != null) {
                    String display = ctx.format(Expression.toText(numArg.evaluate(ctx)));
                    npc.setCustomName(colored(display));
                    npc.setCustomNameVisible(true);
                }
                level.addFreshEntity(npc);
                NpcManager.register(npc);
                LAST_SPAWNED_UUID = npc.getUUID();
                ctx.setPlaceholder("last_spawned_uuid", npc.getStringUUID());
                return;
            }

            var npc = NpcManager.find(ctx.server(), id);
            if (npc == null) return;

            switch (op) {
                case "skin" -> npc.setSkinSource(strArg == null ? "" : Expression.toText(strArg.evaluate(ctx)).strip());
                case "name" -> {
                    String nm = strArg == null ? "" : ctx.format(Expression.toText(strArg.evaluate(ctx)));
                    npc.setCustomName(nm.isEmpty() ? null : colored(nm));
                    npc.setCustomNameVisible(!nm.isEmpty());
                }
                case "equip" -> {
                    if (slot != null && strArg != null) {
                        String itemSpec = Expression.toText(strArg.evaluate(ctx)).strip();
                        ItemStack st = (itemSpec.isEmpty() || itemSpec.equalsIgnoreCase("air") || itemSpec.equalsIgnoreCase("none"))
                                ? ItemStack.EMPTY : ItemStacks.create(itemSpec, 1);
                        npc.setItemSlot(slot, st);
                    }
                }
                case "anim" -> {
                    String anim = strArg == null ? "" : Expression.toText(strArg.evaluate(ctx)).strip();
                    float spd = numArg == null ? 1.0f : (float) number(numArg, ctx);
                    npc.playNpcAnimation(anim, spd);
                }
                case "stop_anim" -> npc.stopNpcAnimation();
                case "look" -> {
                    double[] xyz = new double[3];
                    resolvePosAndLevel(ctx, atTarget, atPlace, x, y, z, xyz);
                    npc.setLookAtPlayers(false);
                    npc.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                            new Vec3(xyz[0], xyz[1] + (atTarget != null ? 1.6 : 0.0), xyz[2]));
                }
                case "walk" -> {
                    double[] xyz = new double[3];
                    resolvePosAndLevel(ctx, atTarget, atPlace, x, y, z, xyz);
                    double spd = numArg == null ? 1.0 : Math.max(0.2, Math.min(3.0, number(numArg, ctx)));
                    npc.getNavigation().moveTo(xyz[0], xyz[1], xyz[2], spd);
                }
                default -> {
                }
            }
        }
    }

    /** Привязка / отвязка 3D-моделей (.geo.json) и предметов к костям игроков и NPC (Props). */
    public record PropAction(
            String op,
            @Nullable Expression slotId,
            @Nullable String bone,
            boolean itemMode,
            @Nullable Expression modelOrItem,
            @Nullable Expression texture,
            @Nullable Expression animation,
            @Nullable EntityTarget entityTarget,
            @Nullable Expression npcIdTarget,
            @Nullable Expression ox, @Nullable Expression oy, @Nullable Expression oz,
            @Nullable Expression rx, @Nullable Expression ry, @Nullable Expression rz,
            @Nullable Expression scale
    ) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            List<java.util.UUID> uuids = new java.util.ArrayList<>();
            if (npcIdTarget != null) {
                var npc = NpcManager.find(ctx.server(), Expression.toText(npcIdTarget.evaluate(ctx)).strip());
                if (npc != null) uuids.add(npc.getUUID());
            } else if (entityTarget != null) {
                for (Entity e : entityTarget.resolve(ctx)) {
                    uuids.add(e.getUUID());
                }
            } else if (ctx.player() != null) {
                uuids.add(ctx.player().getUUID());
            }
            if (uuids.isEmpty()) return;

            if ("clear".equals(op)) {
                for (java.util.UUID u : uuids) {
                    PropManager.clearEntity(ctx.server(), u);
                }
                return;
            }
            String slot = slotId == null ? "default" : Expression.toText(slotId.evaluate(ctx)).strip();
            if ("detach".equals(op)) {
                for (java.util.UUID u : uuids) {
                    PropManager.detach(ctx.server(), u, slot);
                }
                return;
            }
            String mainVal = modelOrItem == null ? "" : Expression.toText(modelOrItem.evaluate(ctx)).strip();
            String texVal = texture == null ? "" : Expression.toText(texture.evaluate(ctx)).strip();
            String animVal = animation == null ? "" : Expression.toText(animation.evaluate(ctx)).strip();
            float fox = ox == null ? 0f : (float) number(ox, ctx);
            float foy = oy == null ? 0f : (float) number(oy, ctx);
            float foz = oz == null ? 0f : (float) number(oz, ctx);
            float frx = rx == null ? 0f : (float) number(rx, ctx);
            float fry = ry == null ? 0f : (float) number(ry, ctx);
            float frz = rz == null ? 0f : (float) number(rz, ctx);
            float fsc = scale == null ? 1.0f : (float) number(scale, ctx);

            PropManager.PropSpec spec = new PropManager.PropSpec(
                    slot, bone == null ? "body" : bone, itemMode, mainVal, texVal, animVal,
                    fox, foy, foz, frx, fry, frz, fsc);
            for (java.util.UUID u : uuids) {
                PropManager.attach(ctx.server(), u, spec);
            }
        }
    }

    /** Плавный пролёт камеры / катсцена по точкам Catmull-Rom (абсолютные и ~относительные координаты!). */
    public record CameraCutscene(
            boolean stop,
            @Nullable Target target,
            @Nullable Expression pointsSpec,
            @Nullable Expression fromLoc,
            @Nullable Expression toLoc,
            @Nullable Expression duration,
            int ticksPerUnit,
            @Nullable EntityTarget lookEntity,
            @Nullable Expression lookNpcId,
            @Nullable Expression lookPlace,
            @Nullable Expression lookX, @Nullable Expression lookY, @Nullable Expression lookZ,
            boolean lockInput
    ) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            List<ServerPlayer> viewers = target == null
                    ? (ctx.player() == null ? List.of() : List.of(ctx.player()))
                    : target.resolve(ctx);
            if (viewers.isEmpty()) return;

            if (stop) {
                var stopPkt = com.scriptcraft.network.CameraPathPacket.stopCutscene();
                for (ServerPlayer p : viewers) {
                    com.scriptcraft.network.ModNetwork.sendTo(p, stopPkt);
                }
                return;
            }

            int durTicks = duration == null ? 100
                    : Math.max(2, Math.min(20 * 600, (int) Math.round(number(duration, ctx) * ticksPerUnit)));

            for (ServerPlayer viewer : viewers) {
                List<com.scriptcraft.network.CameraPathPacket.Waypoint> waypoints = new java.util.ArrayList<>();
                if (pointsSpec != null) {
                    String raw = ctx.format(Expression.toText(pointsSpec.evaluate(ctx)));
                    for (String seg : raw.split(";")) {
                        String trimmed = seg.strip();
                        if (trimmed.isEmpty()) continue;
                        String[] parts = trimmed.split("[,\\s]+");
                        if (parts.length < 3) continue;
                        double wx = parseCoord(parts[0], viewer.getX());
                        double wy = parseCoord(parts[1], viewer.getY());
                        double wz = parseCoord(parts[2], viewer.getZ());
                        boolean hasAng = parts.length >= 5;
                        float wyaw = hasAng ? (float) parseCoord(parts[3], viewer.getYRot()) : 0f;
                        float wpitch = hasAng ? (float) parseCoord(parts[4], viewer.getXRot()) : 0f;
                        waypoints.add(new com.scriptcraft.network.CameraPathPacket.Waypoint(wx, wy, wz, wyaw, wpitch, hasAng));
                    }
                } else if (fromLoc != null && toLoc != null) {
                    ScriptLocation a = Places.require(fromLoc.evaluate(ctx));
                    ScriptLocation b = Places.require(toLoc.evaluate(ctx));
                    waypoints.add(new com.scriptcraft.network.CameraPathPacket.Waypoint(a.x(), a.y(), a.z(), a.yaw(), a.pitch(), false));
                    waypoints.add(new com.scriptcraft.network.CameraPathPacket.Waypoint(b.x(), b.y(), b.z(), b.yaw(), b.pitch(), false));
                }
                if (waypoints.isEmpty()) continue;

                boolean hasLook = false;
                double lx = 0, ly = 0, lz = 0;
                int entNetId = -1;
                if (lookNpcId != null) {
                    var npc = NpcManager.find(ctx.server(), Expression.toText(lookNpcId.evaluate(ctx)).strip());
                    if (npc != null) {
                        hasLook = true;
                        entNetId = npc.getId();
                        lx = npc.getX(); ly = npc.getEyeY(); lz = npc.getZ();
                    }
                } else if (lookEntity != null) {
                    Entity le = lookEntity.first(ctx);
                    if (le != null) {
                        hasLook = true;
                        entNetId = le.getId();
                        lx = le.getX(); ly = le.getEyeY(); lz = le.getZ();
                    }
                } else if (lookX != null && lookY != null && lookZ != null) {
                    hasLook = true;
                    lx = parseExprOrRel(lookX, viewer.getX(), ctx);
                    ly = parseExprOrRel(lookY, viewer.getY(), ctx);
                    lz = parseExprOrRel(lookZ, viewer.getZ(), ctx);
                } else if (lookPlace != null) {
                    ScriptLocation l = Places.require(lookPlace.evaluate(ctx));
                    hasLook = true;
                    lx = l.x(); ly = l.y(); lz = l.z();
                }

                var pkt = new com.scriptcraft.network.CameraPathPacket(
                        false, waypoints, durTicks, hasLook, lx, ly, lz, entNetId, lockInput);
                com.scriptcraft.network.ModNetwork.sendTo(viewer, pkt);
            }
        }

        private static double parseCoord(String token, double origin) {
            String t = token.strip();
            if (t.startsWith("~")) {
                if (t.length() == 1) return origin;
                return origin + Double.parseDouble(t.substring(1));
            }
            return Double.parseDouble(t);
        }

        private static double parseExprOrRel(Expression expr, double origin, ExecutionContext ctx) {
            Object val = expr.evaluate(ctx);
            String txt = Expression.toText(val).strip();
            if (txt.startsWith("~")) {
                return parseCoord(txt, origin);
            }
            return number(expr, ctx);
        }
    }
}
