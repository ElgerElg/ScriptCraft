package com.scriptcraft.script;

import com.scriptcraft.config.ScriptCraftConfig;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Профайлер выполнения скриптов.
 * При выключении проверка одного volatile boolean практически бесплатна.
 * Собирает общую статистику (вызовы, среднее, макс) и скользящее окно за последнюю секунду (20 тиков).
 */
public final class ScriptProfiler {
    public record Snapshot(
            boolean enabled,
            float serverMspt,
            float scriptTickMs,
            List<Row> rows
    ) {
    }

    public record Row(
            String script,
            String section,
            long calls,
            float totalMs,
            float avgMs,
            float maxMs,
            float lastMs,
            long recentCalls,
            float recentMsPerSec
    ) {
    }

    private static final class Bucket {
        final String script;
        final String section;
        long calls;
        long totalNanos;
        long maxNanos;
        long lastNanos;
        final long[] ringNanos = new long[20];
        final long[] ringCalls = new long[20];
        int lastTick = -1;

        Bucket(String script, String section) {
            this.script = script;
            this.section = section;
        }

        synchronized void record(int tick, long nanos) {
            calls++;
            totalNanos += nanos;
            if (nanos > maxNanos) {
                maxNanos = nanos;
            }
            lastNanos = nanos;
            int slot = Math.floorMod(tick, 20);
            if (lastTick != tick) {
                if (lastTick >= 0) {
                    int gap = Math.min(20, Math.max(0, tick - lastTick));
                    for (int i = 1; i <= gap; i++) {
                        int s = Math.floorMod(lastTick + i, 20);
                        ringNanos[s] = 0L;
                        ringCalls[s] = 0L;
                    }
                } else {
                    ringNanos[slot] = 0L;
                    ringCalls[slot] = 0L;
                }
                lastTick = tick;
            }
            ringNanos[slot] += nanos;
            ringCalls[slot] += 1L;
        }

        synchronized Row toRow(int currentTick) {
            long rNanos = 0L;
            long rCalls = 0L;
            if (lastTick >= 0 && currentTick - lastTick < 20) {
                int stale = Math.max(0, currentTick - lastTick);
                for (int i = 0; i < 20; i++) {
                    int age = Math.floorMod(currentTick - i, 20);
                    // Слот валиден только если он не новее lastTick (т.е. age >= stale)
                    if (age >= stale) {
                        rNanos += ringNanos[i];
                        rCalls += ringCalls[i];
                    }
                }
            }
            float totalMs = totalNanos / 1_000_000f;
            float avgMs = calls > 0 ? totalMs / calls : 0f;
            float maxMs = maxNanos / 1_000_000f;
            float lastMs = lastNanos / 1_000_000f;
            float recentMs = rNanos / 1_000_000f;
            return new Row(script, section, calls, totalMs, avgMs, maxMs, lastMs, rCalls, recentMs);
        }
    }

    private static volatile boolean enabled = true;
    private static volatile boolean userOverridden = false;
    private static final Map<String, Bucket> BUCKETS = new ConcurrentHashMap<>();
    private static final long[] TICK_SCRIPT_NANOS = new long[20];
    private static volatile int currentTick = 0;
    private static final AtomicLong CURRENT_TICK_ACC = new AtomicLong();

    private ScriptProfiler() {
    }

    public static void onServerStarting() {
        if (!userOverridden) {
            enabled = ScriptCraftConfig.profilerEnabled();
        }
        reset();
    }

    public static void onServerStopped() {
        reset();
        userOverridden = false;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        userOverridden = true;
    }

    public static boolean toggle() {
        setEnabled(!enabled);
        return enabled;
    }

    public static void reset() {
        BUCKETS.clear();
        for (int i = 0; i < TICK_SCRIPT_NANOS.length; i++) {
            TICK_SCRIPT_NANOS[i] = 0L;
        }
        CURRENT_TICK_ACC.set(0L);
    }

    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        int tick = server.getTickCount();
        long acc = CURRENT_TICK_ACC.getAndSet(0L);
        TICK_SCRIPT_NANOS[Math.floorMod(tick, 20)] = acc;
        currentTick = tick;
    }

    public static long start() {
        return enabled ? System.nanoTime() : 0L;
    }

    public static void end(long startNanos, String script, String section) {
        if (startNanos == 0L || !enabled) {
            return;
        }
        long elapsed = Math.max(0L, System.nanoTime() - startNanos);
        CURRENT_TICK_ACC.addAndGet(elapsed);
        String sFile = (script == null || script.isEmpty()) ? "<inline>" : script;
        String sec = (section == null || section.isEmpty()) ? "run" : section;
        String key = sFile + "\0" + sec;
        Bucket bucket = BUCKETS.computeIfAbsent(key, k -> new Bucket(sFile, sec));
        bucket.record(currentTick, elapsed);
    }

    public static Snapshot snapshot(MinecraftServer server) {
        int tick = server != null ? server.getTickCount() : currentTick;
        float mspt = server != null ? server.getAverageTickTime() : 0f;
        long sumTickNanos = 0L;
        for (long n : TICK_SCRIPT_NANOS) {
            sumTickNanos += n;
        }
        float scriptTickMs = (sumTickNanos / 20.0f) / 1_000_000f;
        List<Row> rows = new ArrayList<>(BUCKETS.size());
        for (Bucket b : BUCKETS.values()) {
            rows.add(b.toRow(tick));
        }
        rows.sort(Comparator.comparingDouble(Row::recentMsPerSec).reversed()
                .thenComparing(Comparator.comparingDouble(Row::totalMs).reversed()));
        if (rows.size() > 120) {
            rows = new ArrayList<>(rows.subList(0, 120));
        }
        return new Snapshot(enabled, mspt, scriptTickMs, List.copyOf(rows));
    }
}
