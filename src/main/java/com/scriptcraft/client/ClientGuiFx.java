package com.scriptcraft.client;

import com.scriptcraft.network.GuiFxPacket;
import com.scriptcraft.screen.Anchor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Клиентский рендерер всплывающих цифр/текста и GUI-частиц (поверх ScriptScreen и HUD).
 * Работает полностью на клиенте в 60+ FPS — сервер отправляет только 1 короткий пакет при спавне.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientGuiFx {
    private static final int MAX_FLOATS = 64;
    private static final int MAX_PARTICLES = 256;

    private static final class FloatText {
        final String text;
        final Anchor anchor;
        final int relX, relY;
        final int rgb;
        final long startMs;
        final int durationMs;
        final float driftX;

        FloatText(String text, Anchor anchor, int relX, int relY, int rgb, long startMs, int durationMs, float driftX) {
            this.text = text;
            this.anchor = anchor;
            this.relX = relX;
            this.relY = relY;
            this.rgb = rgb;
            this.startMs = startMs;
            this.durationMs = durationMs;
            this.driftX = driftX;
        }
    }

    private static final class GuiParticle {
        final Anchor anchor;
        final int originX, originY;
        final float vx, vy;
        final float gravity;
        final float size;
        final int rgb;
        final byte shape; // 0 = square/spark, 1 = diamond/star, 2 = snowflake/cross, 3 = ring/heart
        final long startMs;
        final int durationMs;

        GuiParticle(Anchor anchor, int originX, int originY, float vx, float vy, float gravity,
                    float size, int rgb, byte shape, long startMs, int durationMs) {
            this.anchor = anchor;
            this.originX = originX;
            this.originY = originY;
            this.vx = vx;
            this.vy = vy;
            this.gravity = gravity;
            this.size = size;
            this.rgb = rgb;
            this.shape = shape;
            this.startMs = startMs;
            this.durationMs = durationMs;
        }
    }

    private static final List<FloatText> FLOATS = new ArrayList<>();
    private static final List<GuiParticle> PARTICLES = new ArrayList<>();

    private ClientGuiFx() {
    }

    public static void clear() {
        FLOATS.clear();
        PARTICLES.clear();
    }

    public static void accept(GuiFxPacket p) {
        long now = System.currentTimeMillis();
        Anchor anchor = parseAnchor(p.anchor());
        if (p.kind() == GuiFxPacket.FLOAT_TEXT) {
            if (FLOATS.size() >= MAX_FLOATS) {
                FLOATS.remove(0);
            }
            int dur = Math.max(200, Math.min(5000, p.durationMs()));
            float drift = (ThreadLocalRandom.current().nextFloat() - 0.5f) * 14f;
            int rgb = (p.color() & 0x00FFFFFF);
            if (rgb == 0) rgb = 0xFFFFFF;
            FLOATS.add(new FloatText(p.text(), anchor, p.x(), p.y(), rgb, now, dur, drift));
        } else if (p.kind() == GuiFxPacket.PARTICLES) {
            spawnParticles(anchor, p.x(), p.y(), p.text(), p.color(), p.count(), p.durationMs(), p.spread(), now);
        }
    }

    private static void spawnParticles(Anchor anchor, int x, int y, String presetRaw, int customColor,
                                       int countRaw, int durRaw, float spreadRaw, long now) {
        String preset = presetRaw == null ? "spark" : presetRaw.toLowerCase(Locale.ROOT).strip();
        int count = Math.max(1, Math.min(48, countRaw));
        int baseDur = Math.max(200, Math.min(3000, durRaw));
        float spread = spreadRaw > 0 ? Math.min(160f, spreadRaw) : 28f;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        int defaultRgb = switch (preset) {
            case "fire", "flame", "napalm" -> 0xFF6B1A;
            case "ice", "frost", "snow" -> 0x7DD3FC;
            case "poison", "toxic", "slime" -> 0x4ADE80;
            case "gold", "coin" -> 0xFACC15;
            case "magic", "arcane", "portal" -> 0xC084FC;
            case "heart", "heal", "love" -> 0xFB7185;
            case "explosion", "boom", "crit" -> 0xF97316;
            case "smoke", "cloud" -> 0x94A3B8;
            default -> 0xFDE047; // spark
        };
        int baseRgb = (customColor & 0x00FFFFFF) != 0 ? (customColor & 0x00FFFFFF) : defaultRgb;
        byte shape = switch (preset) {
            case "ice", "frost", "snow" -> (byte) 2;
            case "magic", "gold", "coin", "crit" -> (byte) 1;
            case "heart", "heal", "love" -> (byte) 3;
            default -> (byte) 0;
        };
        float gravity = switch (preset) {
            case "fire", "flame", "smoke", "cloud", "heart", "heal" -> -22f;
            case "magic", "ice", "frost" -> 8f;
            case "explosion", "boom" -> 55f;
            default -> 40f;
        };

        for (int i = 0; i < count; i++) {
            if (PARTICLES.size() >= MAX_PARTICLES) {
                PARTICLES.remove(0);
            }
            double angle = rng.nextDouble() * Math.PI * 2.0;
            float speed = (0.35f + rng.nextFloat() * 0.85f) * spread * 1.6f;
            float vx = (float) (Math.cos(angle) * speed);
            float vy = (float) (Math.sin(angle) * speed) - (gravity > 0 ? 18f : 12f);
            float size = switch (preset) {
                case "explosion", "boom" -> 2.8f + rng.nextFloat() * 2.5f;
                case "smoke", "cloud" -> 3.0f + rng.nextFloat() * 2.2f;
                default -> 1.8f + rng.nextFloat() * 1.8f;
            };
            int dur = (int) (baseDur * (0.7f + rng.nextFloat() * 0.55f));
            int rgb = jitterColor(baseRgb, rng);
            PARTICLES.add(new GuiParticle(anchor, x, y, vx, vy, gravity, size, rgb, shape, now, dur));
        }
    }

    private static int jitterColor(int rgb, ThreadLocalRandom rng) {
        int r = Math.max(0, Math.min(255, ((rgb >> 16) & 0xFF) + rng.nextInt(-22, 23)));
        int g = Math.max(0, Math.min(255, ((rgb >> 8) & 0xFF) + rng.nextInt(-22, 23)));
        int b = Math.max(0, Math.min(255, (rgb & 0xFF) + rng.nextInt(-22, 23)));
        return (r << 16) | (g << 8) | b;
    }

    private static Anchor parseAnchor(String raw) {
        if (raw == null || raw.isEmpty()) return Anchor.CENTER;
        try {
            return Anchor.valueOf(raw.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return Anchor.CENTER;
        }
    }

    public static void render(GuiGraphics g, Font font, int screenW, int screenH) {
        if (FLOATS.isEmpty() && PARTICLES.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();

        // 1. GUI-частицы
        Iterator<GuiParticle> pit = PARTICLES.iterator();
        while (pit.hasNext()) {
            GuiParticle p = pit.next();
            long age = now - p.startMs;
            if (age >= p.durationMs) {
                pit.remove();
                continue;
            }
            float t = age / (float) p.durationMs;
            float sec = age / 1000f;
            int ox = p.anchor.resolveX(screenW, 0, p.originX);
            int oy = p.anchor.resolveY(screenH, 0, p.originY);
            int px = Math.round(ox + p.vx * sec);
            int py = Math.round(oy + p.vy * sec + 0.5f * p.gravity * sec * sec);
            float alphaF = t < 0.7f ? 1f : Math.max(0f, 1f - (t - 0.7f) / 0.3f);
            int alpha = Math.max(4, Math.min(255, Math.round(alphaF * 255f)));
            int argb = (alpha << 24) | p.rgb;
            int sz = Math.max(1, Math.round(p.size * (1f - t * 0.45f)));
            if (p.shape == 2) {
                // Снежинка / крестик
                g.fill(px - sz, py, px + sz + 1, py + 1, argb);
                g.fill(px, py - sz, px + 1, py + sz + 1, argb);
            } else if (p.shape == 1) {
                // Звёздочка / ромб
                g.fill(px - sz, py, px + sz + 1, py + 1, argb);
                g.fill(px, py - sz, px + 1, py + sz + 1, argb);
                if (sz > 1) {
                    g.fill(px - sz + 1, py - sz + 1, px + sz, py + sz, argb);
                }
            } else if (p.shape == 3) {
                // Мини-сердечко
                g.fill(px - 2, py - 1, px, py + 1, argb);
                g.fill(px + 1, py - 1, px + 3, py + 1, argb);
                g.fill(px - 1, py, px + 2, py + 2, argb);
                g.fill(px, py + 2, px + 1, py + 3, argb);
            } else {
                // Искра / квадрат
                int half = sz / 2;
                g.fill(px - half, py - half, px - half + sz, py - half + sz, argb);
            }
        }

        // 2. Всплывающие цифры / текст
        Iterator<FloatText> fit = FLOATS.iterator();
        while (fit.hasNext()) {
            FloatText f = fit.next();
            long age = now - f.startMs;
            if (age >= f.durationMs) {
                fit.remove();
                continue;
            }
            float t = age / (float) f.durationMs;
            float easeOut = 1f - (1f - t) * (1f - t);
            float rise = easeOut * 24f;
            float alphaF = t < 0.6f ? 1f : Math.max(0f, 1f - (t - 0.6f) / 0.4f);
            int alpha = Math.max(8, Math.min(255, Math.round(alphaF * 255f)));
            int argb = (alpha << 24) | f.rgb;
            int w = font.width(f.text);
            int cx = f.anchor.resolveX(screenW, w, f.relX) + Math.round(f.driftX * t);
            int cy = f.anchor.resolveY(screenH, font.lineHeight, f.relY) - Math.round(rise);
            float pop = t < 0.14f ? (0.8f + (t / 0.14f) * 0.35f) : (t < 0.28f ? (1.15f - ((t - 0.14f) / 0.14f) * 0.15f) : 1f);
            g.pose().pushPose();
            float tx = cx + w / 2f;
            float ty = cy + font.lineHeight / 2f;
            g.pose().translate(tx, ty, 0f);
            g.pose().scale(pop, pop, 1f);
            g.pose().translate(-tx, -ty, 0f);
            g.drawString(font, f.text, cx, cy, argb, true);
            g.pose().popPose();
        }
    }
}
