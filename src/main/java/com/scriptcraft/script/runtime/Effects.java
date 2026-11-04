package com.scriptcraft.script.runtime;

import com.scriptcraft.config.ScriptCraftConfig;
import com.scriptcraft.lang.Messages;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

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

    /** play sound entity.player.levelup to player (слышит только этот игрок) */
    public record PlaySound(Target target, String soundId) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ResourceLocation rl = id(soundId);
            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(rl);
            if (sound == null || !ForgeRegistries.SOUND_EVENTS.containsKey(rl)) {
                throw new ScriptRuntimeException(Messages.get("effect.unknown_sound", rl));
            }
            for (ServerPlayer p : target.resolve(ctx)) {
                p.playNotifySound(sound, SoundSource.MASTER, 1f, 1f);
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

    /** give <цель> <предмет> [количество] — выдать предмет; если инвентарь полон, предмет падает рядом. */
    public record GiveItem(Target target, String itemId, @Nullable Expression count) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            Item item = ItemResolver.resolve(itemId)
                    .orElseThrow(() -> new ScriptRuntimeException("Неизвестный предмет: " + itemId));
            int total = count == null ? 1 : (int) Math.max(1, Math.min(64 * 36, number(count, ctx)));
            int maxStack = new ItemStack(item).getMaxStackSize();
            for (ServerPlayer p : target.resolve(ctx)) {
                int left = total;
                while (left > 0) {
                    int size = Math.min(left, maxStack);
                    ItemStack stack = new ItemStack(item, size);
                    if (!p.getInventory().add(stack)) {
                        p.drop(stack, false);
                    }
                    left -= size;
                }
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
}
