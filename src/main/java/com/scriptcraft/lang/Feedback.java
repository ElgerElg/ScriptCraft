package com.scriptcraft.lang;

import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.network.NotifyPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ответ мода игроку: всплывающее уведомление, а не строка чата.
 * Консоль по-прежнему получает обычное сообщение.
 */
public final class Feedback {
    private Feedback() {
    }

    public static void ok(CommandSourceStack source, String key, Object... args) {
        show(source, Messages.toast(key, args));
    }

    public static void fail(CommandSourceStack source, String key, Object... args) {
        show(source, Messages.toast(key, args));
    }

    /** Уже раскрашенная строка (справка, список) — к ней добавляется заголовок мода. */
    public static void showColored(CommandSourceStack source, String colored) {
        show(source, Messages.branded(colored));
    }

    public static void failText(CommandSourceStack source, String plain) {
        show(source, Messages.branded("\u00a7c" + plain));
    }

    public static void toPlayer(ServerPlayer player, String key, Object... args) {
        ModNetwork.sendTo(player, new NotifyPacket(Messages.toast(key, args)));
    }

    private static void show(CommandSourceStack source, String colored) {
        if (source.getEntity() instanceof ServerPlayer player) {
            ModNetwork.sendTo(player, new NotifyPacket(colored));
            return;
        }
        source.sendSystemMessage(Component.literal(colored.replaceAll("\u00a7.", "")));
    }
}
