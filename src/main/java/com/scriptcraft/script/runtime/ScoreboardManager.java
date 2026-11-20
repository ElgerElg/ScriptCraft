package com.scriptcraft.script.runtime;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Персональный сайдбар-скорборд для каждого игрока через ванильные пакеты.
 * Не засоряет общий скорборд мира и корректно очищается при выходе игрока.
 */
public final class ScoreboardManager {
    private static final String OBJ_NAME = "sc_board";
    private static final Map<UUID, Board> BOARDS = new HashMap<>();

    private ScoreboardManager() {
    }

    public static void setTitle(ServerPlayer player, String title) {
        Board board = BOARDS.computeIfAbsent(player.getUUID(), id -> new Board());
        board.title = title == null ? "" : title;
        sendObjective(player, board);
    }

    public static void setLine(ServerPlayer player, int line, String text) {
        int score = Math.max(0, Math.min(99, line));
        Board board = BOARDS.computeIfAbsent(player.getUUID(), id -> new Board());
        if (!board.created) {
            sendObjective(player, board);
        }
        String prev = board.lines.get(score);
        String next = uniqueEntry(score, text == null ? "" : text);
        if (prev != null && !prev.equals(next)) {
            player.connection.send(new ClientboundSetScorePacket(ServerScoreboard.Method.REMOVE, OBJ_NAME, prev, 0));
        }
        board.lines.put(score, next);
        player.connection.send(new ClientboundSetScorePacket(ServerScoreboard.Method.CHANGE, OBJ_NAME, next, score));
    }

    public static void clear(ServerPlayer player) {
        Board board = BOARDS.remove(player.getUUID());
        if (board == null || !board.created) {
            return;
        }
        Objective dummy = dummyObjective(board.title);
        player.connection.send(new ClientboundSetObjectivePacket(dummy, ClientboundSetObjectivePacket.METHOD_REMOVE));
    }

    public static void onQuit(ServerPlayer player) {
        if (player != null) {
            BOARDS.remove(player.getUUID());
        }
    }

    public static void onServerStopped() {
        BOARDS.clear();
    }

    private static void sendObjective(ServerPlayer player, Board board) {
        Objective dummy = dummyObjective(board.title);
        if (!board.created) {
            board.created = true;
            player.connection.send(new ClientboundSetObjectivePacket(dummy, ClientboundSetObjectivePacket.METHOD_ADD));
            player.connection.send(new ClientboundSetDisplayObjectivePacket(Scoreboard.DISPLAY_SLOT_SIDEBAR, dummy));
        } else {
            player.connection.send(new ClientboundSetObjectivePacket(dummy, ClientboundSetObjectivePacket.METHOD_CHANGE));
        }
    }

    private static Objective dummyObjective(String title) {
        Scoreboard sb = new Scoreboard();
        return new Objective(sb, OBJ_NAME, ObjectiveCriteria.DUMMY,
                Component.literal(title == null ? "" : title), ObjectiveCriteria.RenderType.INTEGER);
    }

    /** Уникальный суффикс цветового кода, чтобы одинаковые строки на разных линиях не склеивались. */
    private static String uniqueEntry(int score, String text) {
        ChatFormatting[] vals = ChatFormatting.values();
        String suffix = "\u00a7" + vals[score % 16].getChar() + "\u00a7r";
        String combined = text + suffix;
        if (combined.length() > 40) {
            combined = text.substring(0, Math.max(0, 40 - suffix.length())) + suffix;
        }
        return combined;
    }

    private static final class Board {
        boolean created;
        String title = "ScriptCraft";
        final Map<Integer, String> lines = new LinkedHashMap<>();
    }
}
