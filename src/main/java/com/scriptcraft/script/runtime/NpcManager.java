package com.scriptcraft.script.runtime;

import com.scriptcraft.entity.ScriptFakeNpcEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверный реестр фейковых игроков-NPC по строковому идентификатору ({@code spawn npc "commander" ...}).
 */
public final class NpcManager {
    private static final Map<String, UUID> BY_ID = new ConcurrentHashMap<>();

    private NpcManager() {
    }

    public static String normalizeId(String id) {
        return id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
    }

    public static void register(ScriptFakeNpcEntity npc) {
        String key = normalizeId(npc.getNpcId());
        if (!key.isEmpty()) {
            BY_ID.put(key, npc.getUUID());
        }
    }

    @Nullable
    public static ScriptFakeNpcEntity find(MinecraftServer server, String id) {
        if (server == null || id == null) return null;
        String key = normalizeId(id);
        if (key.isEmpty()) return null;

        UUID known = BY_ID.get(key);
        if (known != null) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity ent = level.getEntity(known);
                if (ent instanceof ScriptFakeNpcEntity npc && !npc.isRemoved()) {
                    return npc;
                }
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity ent : level.getAllEntities()) {
                if (ent instanceof ScriptFakeNpcEntity npc && !npc.isRemoved()
                        && key.equals(normalizeId(npc.getNpcId()))) {
                    BY_ID.put(key, npc.getUUID());
                    return npc;
                }
            }
        }
        return null;
    }

    public static boolean remove(MinecraftServer server, String id) {
        ScriptFakeNpcEntity existing = find(server, id);
        BY_ID.remove(normalizeId(id));
        if (existing != null) {
            PropManager.clearEntity(server, existing.getUUID());
            existing.discard();
            return true;
        }
        return false;
    }

    public static int clearAll(MinecraftServer server) {
        if (server == null) return 0;
        List<ScriptFakeNpcEntity> list = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity ent : level.getAllEntities()) {
                if (ent instanceof ScriptFakeNpcEntity npc && !npc.isRemoved()) {
                    list.add(npc);
                }
            }
        }
        BY_ID.clear();
        for (ScriptFakeNpcEntity npc : list) {
            PropManager.clearEntity(server, npc.getUUID());
            npc.discard();
        }
        return list.size();
    }

    public static void onServerStopped() {
        BY_ID.clear();
    }
}
