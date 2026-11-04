package com.scriptcraft.event;

import com.scriptcraft.command.ScriptCommands;
import com.scriptcraft.script.ScriptLoader;
import com.scriptcraft.script.runtime.ScriptEvent;
import com.scriptcraft.script.variables.VariableStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Мост между событиями Forge и событиями скриптов.
 * Регистрируется в конструкторе мода: MinecraftForge.EVENT_BUS.register(ServerEvents.class) — поэтому методы static.
 */
public final class ServerEvents {
    private ServerEvents() {
    }

    /** Регистрация команд. Forge вызывает это при старте сервера и при ванильном /reload. */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ScriptCommands.registerAll(event.getDispatcher());
    }

    /** "on join:" */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ScriptLoader.fireEvent(ScriptEvent.JOIN, player);
        }
    }

    /** "on quit:" */
    @SubscribeEvent
    public static void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ScriptLoader.fireEvent(ScriptEvent.QUIT, player);
        }
    }

    // ------------------------------------------------------------------ переменные

    /** Старт сервера (мира): загружаем <мир>/scriptcraft/variables.json, временные переменные очищаются. */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        VariableStore.load(event.getServer());
    }

    /** Остановка сервера: сохраняем переменные, пока мир ещё доступен. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        VariableStore.save();
    }

    /** Сервер остановлен: финальное сохранение и очистка памяти (важно для одиночной игры — следующий мир чистый). */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        VariableStore.shutdown();
    }

    /** Периодическое автосохранение (интервал — variablesSaveIntervalSeconds в конфиге). */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            VariableStore.tick();
        }
    }
}
