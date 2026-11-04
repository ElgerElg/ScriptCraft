package com.scriptcraft.script.runtime;

import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.network.OpenScreenPacket;
import com.scriptcraft.screen.ScreenDescription;
import com.scriptcraft.screen.ScreenElement;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * БЕЛЫЙ СПИСОК действий, доступных скриптам. Новое действие = новый record здесь + правило в ScriptParser.
 * Всё, чего здесь нет, скрипт сделать не может.
 */
public final class Actions {
    private Actions() {
    }

    /** send "текст" [to player] — сообщение игроку. */
    public record SendMessage(String text) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ctx.requirePlayer().sendSystemMessage(Component.literal(ctx.format(text)));
        }
    }

    /** broadcast "текст" — сообщение всем игрокам. */
    public record Broadcast(String text) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ctx.server().getPlayerList().broadcastSystemMessage(Component.literal(ctx.format(text)), false);
        }
    }

    /** reply "текст" — ответ тому, кто вызвал (работает и из консоли). */
    public record Reply(String text) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ctx.reply(ctx.format(text));
        }
    }

    /** give player <предмет> [количество] — выдать предмет; если инвентарь полон, предмет падает рядом. */
    public record GiveItem(String itemId, int count) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ServerPlayer player = ctx.requirePlayer();
            Item item = ItemResolver.resolve(itemId)
                    .orElseThrow(() -> new ScriptRuntimeException("Неизвестный предмет: " + itemId));
            int left = Math.max(1, Math.min(count, 64 * 36));
            while (left > 0) {
                int size = Math.min(left, new ItemStack(item).getMaxStackSize());
                ItemStack stack = new ItemStack(item, size);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                left -= size;
            }
        }
    }

    /** heal player — восстановить здоровье. */
    public record HealPlayer() implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ServerPlayer player = ctx.requirePlayer();
            player.setHealth(player.getMaxHealth());
        }
    }

    /** teleport player to x, y, z — телепорт в текущем мире игрока. */
    public record Teleport(double x, double y, double z) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ctx.requirePlayer().teleportTo(x, y, z);
        }
    }

    /**
     * open screen "Заголовок" [to player]: + вложенные элементы.
     * Сервер подставляет плейсхолдеры и отправляет описание экрана клиенту пакетом.
     */
    public record OpenScreen(String title, List<ScreenElement> elements) implements Action {
        @Override
        public void execute(ExecutionContext ctx) {
            ServerPlayer player = ctx.requirePlayer();
            List<ScreenElement> formatted = new ArrayList<>(elements.size());
            for (ScreenElement element : elements) {
                formatted.add(element.format(ctx::format));
            }
            ModNetwork.sendTo(player, new OpenScreenPacket(new ScreenDescription(ctx.format(title), formatted)));
        }
    }
}
