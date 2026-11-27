package com.scriptcraft.client;

import com.mojang.blaze3d.audio.OggAudioStream;
import com.scriptcraft.lang.Messages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTOffset;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Встроенный аудиоплеер .ogg файлов в Студии ScriptCraft:
 * декодирует Ogg Vorbis через {@link OggAudioStream}, строит реальную волновую форму (waveform),
 * проигрывает через OpenAL с регулировкой громкости, питча (0.5x..2.0x), повтором, перемоткой
 * и вставкой готового эффекта {@code play sound "ns:id" to player} прямо в скрипт!
 */
@OnlyIn(Dist.CLIENT)
public final class StudioAudioPlayer {
    private static final int BARS = 72;

    private static String loadedDocKey = "";
    private static byte[] loadedBytes = null;
    private static int alBuffer = 0;
    private static int alSource = 0;
    private static float durationSec = 0f;
    private static int sampleRate = 44100;
    private static int channels = 1;
    private static final float[] waveform = new float[BARS];
    private static String loadError = null;

    private static float volume = 0.85f;
    private static float pitch = 1.0f;
    private static boolean loop = false;
    private static int dragSlider = 0; // 1 = seek, 2 = vol, 3 = pitch

    // Координаты интерактивных элементов последнего кадра
    private static int waveX, waveY, waveW, waveH;
    private static int playBtnX, playBtnY, playBtnW;
    private static int stopBtnX, loopBtnX, copyBtnX, copyBtnW, insBtnX, insBtnW, ctrlY;
    private static int volX, volY, volW, pitchX, pitchY, pitchW;
    private static String currentSoundId = "";

    private StudioAudioPlayer() {
    }

    public static void stopAndRelease() {
        try {
            if (alSource != 0) {
                AL10.alSourceStop(alSource);
                AL10.alDeleteSources(alSource);
                alSource = 0;
            }
            if (alBuffer != 0) {
                AL10.alDeleteBuffers(alBuffer);
                alBuffer = 0;
            }
        } catch (Throwable ignored) {
        }
        loadedDocKey = "";
        loadedBytes = null;
        loadError = null;
        dragSlider = 0;
    }

    private static void ensureLoaded(ScriptDocument doc) {
        if (doc == null || doc.audioBytes == null) return;
        if (doc.name.equals(loadedDocKey) && loadedBytes == doc.audioBytes) {
            return;
        }
        stopAndRelease();
        loadedDocKey = doc.name;
        loadedBytes = doc.audioBytes;
        currentSoundId = deriveSoundId(doc.name);

        if (doc.audioBytes.length == 0) {
            loadError = "Empty .ogg file";
            return;
        }
        try (OggAudioStream stream = new OggAudioStream(new ByteArrayInputStream(doc.audioBytes))) {
            AudioFormat fmt = stream.getFormat();
            sampleRate = Math.max(8000, (int) fmt.getSampleRate());
            channels = Math.max(1, fmt.getChannels());
            int bits = Math.max(8, fmt.getSampleSizeInBits());
            ByteBuffer pcm = stream.readAll();
            int totalBytes = pcm.remaining();
            float bytesPerSec = sampleRate * channels * (bits / 8.0f);
            durationSec = bytesPerSec > 0 ? (totalBytes / bytesPerSec) : 0f;
            computeWaveform(pcm, bits);

            int alFmt = channels == 1
                    ? (bits == 16 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_MONO8)
                    : (bits == 16 ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_STEREO8);
            alBuffer = AL10.alGenBuffers();
            AL10.alBufferData(alBuffer, alFmt, pcm, sampleRate);
            alSource = AL10.alGenSources();
            AL10.alSourcei(alSource, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSourcei(alSource, AL10.AL_BUFFER, alBuffer);
            AL10.alSourcef(alSource, AL10.AL_GAIN, volume);
            AL10.alSourcef(alSource, AL10.AL_PITCH, pitch);
            AL10.alSourcei(alSource, AL10.AL_LOOPING, loop ? AL10.AL_TRUE : AL10.AL_FALSE);
        } catch (Throwable t) {
            loadError = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        }
    }

    private static void computeWaveform(ByteBuffer pcm, int bits) {
        for (int i = 0; i < BARS; i++) waveform[i] = 0.08f;
        int pos = pcm.position();
        int rem = pcm.remaining();
        if (rem < 4) return;

        float maxPeak = 0.01f;
        if (bits == 16) {
            int totalSamples = rem / 2;
            int perBar = Math.max(1, totalSamples / BARS);
            for (int b = 0; b < BARS; b++) {
                int start = b * perBar;
                int end = Math.min(totalSamples, start + perBar);
                int step = Math.max(1, (end - start) / 64);
                float sumSq = 0f;
                int cnt = 0;
                for (int s = start; s < end; s += step) {
                    int idx = pos + s * 2;
                    if (idx + 1 >= pos + rem) break;
                    short sample = (short) ((pcm.get(idx) & 0xFF) | (pcm.get(idx + 1) << 8));
                    float norm = Math.abs(sample / 32768f);
                    sumSq += norm * norm;
                    cnt++;
                }
                float rms = cnt > 0 ? (float) Math.sqrt(sumSq / cnt) : 0.05f;
                waveform[b] = rms;
                if (rms > maxPeak) maxPeak = rms;
            }
        }
        for (int b = 0; b < BARS; b++) {
            waveform[b] = Math.max(0.08f, Math.min(1.0f, waveform[b] / maxPeak));
        }
    }

    public static String deriveSoundId(String rawName) {
        String rel = rawName.startsWith("@") ? rawName.substring(1) : rawName;
        rel = rel.replace('\\', '/');
        if (rel.startsWith("resources/assets/")) {
            String rest = rel.substring("resources/assets/".length());
            int slash = rest.indexOf('/');
            if (slash > 0) {
                String ns = rest.substring(0, slash);
                String after = rest.substring(slash + 1);
                if (after.startsWith("sounds/")) {
                    after = after.substring("sounds/".length());
                }
                if (after.toLowerCase(Locale.ROOT).endsWith(".ogg")) {
                    after = after.substring(0, after.length() - 4);
                }
                return ns + ":" + after;
            }
        }
        int sl = rel.lastIndexOf('/');
        String base = sl >= 0 ? rel.substring(sl + 1) : rel;
        if (base.toLowerCase(Locale.ROOT).endsWith(".ogg")) {
            base = base.substring(0, base.length() - 4);
        }
        return "scriptcraft:" + base;
    }

    private static boolean isPlaying() {
        return alSource != 0 && AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
    }

    private static float playbackSec() {
        if (alSource == 0) return 0f;
        int state = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);
        if (state != AL10.AL_PLAYING && state != AL10.AL_PAUSED) {
            return 0f;
        }
        return Math.max(0f, Math.min(durationSec, AL10.alGetSourcef(alSource, EXTOffset.AL_SEC_OFFSET)));
    }

    public static void render(GuiGraphics g, Font font, ScriptDocument doc,
                              int x, int y, int w, int h, int mx, int my) {
        ensureLoaded(doc);
        int cardW = Math.min(480, Math.max(240, w - 32));
        int cardH = Math.min(248, Math.max(180, h - 24));
        int cx = x + (w - cardW) / 2;
        int cy = y + (h - cardH) / 2;

        g.fill(cx + 3, cy + 3, cx + cardW + 3, cy + cardH + 3, 0x66000000);
        g.fill(cx, cy, cx + cardW, cy + cardH, 0xF5131B28);
        g.fillGradient(cx + 1, cy + 1, cx + cardW - 1, cy + 2, 0xFFF0B85B, 0xFF58A6FF);
        border(g, cx, cy, cardW, cardH, 0xFF2E3C54);

        // Заголовок и авто-ID звука
        g.drawString(font, "♪ " + Messages.get("studio.audio.title"), cx + 14, cy + 12, 0xFFF0B85B, false);
        String idBadge = currentSoundId;
        int idW = font.width(idBadge) + 12;
        int idX = cx + cardW - idW - 14;
        g.fill(idX, cy + 9, idX + idW, cy + 23, 0xFF0E1520);
        border(g, idX, cy + 9, idW, 14, 0xFF364866);
        g.drawString(font, idBadge, idX + 6, cy + 12, 0xFF58A6FF, false);

        if (loadError != null) {
            g.drawString(font, "Ogg Error: " + loadError, cx + 14, cy + 40, 0xFFE56B6F, false);
            return;
        }

        String chName = channels == 1 ? "Mono" : (channels + "ch");
        String meta = String.format(Locale.ROOT, "%d Hz · %s · %.2fs · Auto-registered (no sounds.json needed)",
                sampleRate, chName, durationSec);
        g.drawString(font, meta, cx + 14, cy + 28, 0xFF8A96AA, false);

        // Интерактивная волновая форма (waveform)
        waveX = cx + 14;
        waveY = cy + 46;
        waveW = cardW - 28;
        waveH = Math.max(48, cardH - 134);
        g.fill(waveX, waveY, waveX + waveW, waveY + waveH, 0xFF0B1018);
        border(g, waveX, waveY, waveW, waveH, 0xFF253247);

        float curSec = playbackSec();
        float progress = durationSec > 0.001f ? Math.max(0f, Math.min(1f, curSec / durationSec)) : 0f;
        int midY = waveY + waveH / 2;
        float barStep = (float) (waveW - 8) / BARS;
        int barPxW = Math.max(1, Math.round(barStep) - 1);

        for (int i = 0; i < BARS; i++) {
            int bx = waveX + 4 + Math.round(i * barStep);
            int halfH = Math.max(2, Math.round(waveform[i] * (waveH * 0.42f)));
            boolean passed = ((i + 0.5f) / BARS) <= progress && (isPlaying() || curSec > 0.01f);
            int col = passed ? 0xFFF0B85B : 0xFF3B5378;
            g.fill(bx, midY - halfH, bx + barPxW, midY + halfH, col);
        }

        // Курсор воспроизведения
        int playheadX = waveX + 4 + Math.round(progress * (waveW - 8));
        g.fill(playheadX, waveY + 2, playheadX + 2, waveY + waveH - 2, 0xFF4CC38A);

        String timeStr = String.format(Locale.ROOT, "%02d:%04.1f / %02d:%04.1f",
                (int) (curSec / 60), curSec % 60f, (int) (durationSec / 60), durationSec % 60f);
        g.drawString(font, timeStr, waveX + 6, waveY + waveH - 11, 0xFFE8EDF5, false);

        // Кнопки управления
        ctrlY = waveY + waveH + 10;
        boolean playing = isPlaying();
        String playLbl = playing ? "⏸ Pause" : "▶ Play";
        playBtnW = font.width(playLbl) + 16;
        playBtnX = waveX;
        playBtnY = ctrlY;
        drawBtn(g, font, playBtnX, playBtnY, playBtnW, 18, playLbl, playing, mx, my, 0xFF4CC38A);

        stopBtnX = playBtnX + playBtnW + 6;
        drawBtn(g, font, stopBtnX, ctrlY, 46, 18, "■ Stop", false, mx, my, 0xFFE56B6F);

        loopBtnX = stopBtnX + 52;
        drawBtn(g, font, loopBtnX, ctrlY, 54, 18, "↻ Loop", loop, mx, my, 0xFFF0B85B);

        String insLbl = "→ " + Messages.get("studio.audio.insert");
        insBtnW = font.width(insLbl) + 14;
        insBtnX = waveX + waveW - insBtnW;
        drawBtn(g, font, insBtnX, ctrlY, insBtnW, 18, insLbl, false, mx, my, 0xFF58A6FF);

        String cpLbl = "⎘ ID";
        copyBtnW = font.width(cpLbl) + 12;
        copyBtnX = insBtnX - copyBtnW - 6;
        drawBtn(g, font, copyBtnX, ctrlY, copyBtnW, 18, cpLbl, false, mx, my, 0xFFF0B85B);

        // Ползунки Громкость и Питч
        int slY = ctrlY + 28;
        int halfW = (waveW - 16) / 2;
        volX = waveX;
        volY = slY + 11;
        volW = halfW;
        g.drawString(font, String.format(Locale.ROOT, "%s: %d%%", Messages.get("studio.audio.vol"), Math.round(volume * 100f)),
                volX, slY, 0xFF8A96AA, false);
        drawSlider(g, volX, volY, volW, volume, 0xFF4CC38A);

        pitchX = waveX + halfW + 16;
        pitchY = slY + 11;
        pitchW = halfW;
        g.drawString(font, String.format(Locale.ROOT, "Pitch: %.2fx", pitch), pitchX, slY, 0xFF8A96AA, false);
        drawSlider(g, pitchX, pitchY, pitchW, (pitch - 0.5f) / 1.5f, 0xFF58A6FF);
    }

    public static boolean mouseClicked(int mx, int my, int button) {
        if (alSource == 0) return false;
        if (hover(mx, my, waveX, waveY, waveW, waveH)) {
            dragSlider = 1;
            seekToMouse(mx);
            return true;
        }
        if (hover(mx, my, playBtnX, playBtnY, playBtnW, 18)) {
            if (isPlaying()) {
                AL10.alSourcePause(alSource);
            } else {
                AL10.alSourcef(alSource, AL10.AL_GAIN, volume);
                AL10.alSourcef(alSource, AL10.AL_PITCH, pitch);
                AL10.alSourcei(alSource, AL10.AL_LOOPING, loop ? AL10.AL_TRUE : AL10.AL_FALSE);
                AL10.alSourcePlay(alSource);
            }
            return true;
        }
        if (hover(mx, my, stopBtnX, ctrlY, 46, 18)) {
            AL10.alSourceStop(alSource);
            AL10.alSourceRewind(alSource);
            return true;
        }
        if (hover(mx, my, loopBtnX, ctrlY, 54, 18)) {
            loop = !loop;
            AL10.alSourcei(alSource, AL10.AL_LOOPING, loop ? AL10.AL_TRUE : AL10.AL_FALSE);
            return true;
        }
        if (hover(mx, my, copyBtnX, ctrlY, copyBtnW, 18)) {
            Minecraft.getInstance().keyboardHandler.setClipboard(currentSoundId);
            ClientToasts.push(Messages.toast("studio.anim.copied_code", currentSoundId));
            return true;
        }
        if (hover(mx, my, insBtnX, ctrlY, insBtnW, 18)) {
            String snippet = String.format(Locale.ROOT, "play sound \"%s\" to player volume %.2f pitch %.2f",
                    currentSoundId, volume, pitch);
            insertIntoLastScriptOrClipboard(snippet);
            return true;
        }
        if (hover(mx, my, volX, volY - 5, volW, 14)) {
            dragSlider = 2;
            applyVolMouse(mx);
            return true;
        }
        if (hover(mx, my, pitchX, pitchY - 5, pitchW, 14)) {
            dragSlider = 3;
            applyPitchMouse(mx);
            return true;
        }
        return false;
    }

    public static boolean mouseDragged(int mx, int my) {
        if (dragSlider == 1) { seekToMouse(mx); return true; }
        if (dragSlider == 2) { applyVolMouse(mx); return true; }
        if (dragSlider == 3) { applyPitchMouse(mx); return true; }
        return false;
    }

    public static void mouseReleased() {
        dragSlider = 0;
    }

    private static void seekToMouse(int mx) {
        if (alSource == 0 || durationSec <= 0.01f) return;
        float t = Math.max(0f, Math.min(1f, (mx - (waveX + 4)) / (float) Math.max(1, waveW - 8)));
        if (!isPlaying()) {
            AL10.alSourcePlay(alSource);
        }
        AL10.alSourcef(alSource, EXTOffset.AL_SEC_OFFSET, t * durationSec);
    }

    private static void applyVolMouse(int mx) {
        volume = Math.max(0f, Math.min(1f, (mx - volX) / (float) Math.max(1, volW)));
        if (alSource != 0) AL10.alSourcef(alSource, AL10.AL_GAIN, volume);
    }

    private static void applyPitchMouse(int mx) {
        float t = Math.max(0f, Math.min(1f, (mx - pitchX) / (float) Math.max(1, pitchW)));
        pitch = Math.round((0.5f + t * 1.5f) * 20f) / 20f;
        if (alSource != 0) AL10.alSourcef(alSource, AL10.AL_PITCH, pitch);
    }

    static void insertIntoLastScriptOrClipboard(String code) {
        if (StudioSession.lastScriptTab != null) {
            ScriptDocument target = StudioSession.docs.get(StudioSession.lastScriptTab);
            if (target != null && target.loaded && !target.genericFile && !target.isImage && !target.isAudio) {
                target.insert(code);
                StudioSession.active = StudioSession.lastScriptTab;
                ClientToasts.push(Messages.toast("studio.anim.copied_code", StudioSession.lastScriptTab));
                return;
            }
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(code);
        ClientToasts.push(Messages.toast("studio.anim.copied_code", code));
    }

    private static void drawBtn(GuiGraphics g, Font font, int x, int y, int w, int h, String label,
                                boolean active, int mx, int my, int accent) {
        boolean hot = hover(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, active ? 0xFF1F3248 : (hot ? 0xFF27344A : 0xFF1D2636));
        border(g, x, y, w, h, active || hot ? accent : 0xFF2B364A);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 5, active || hot ? accent : 0xFFE8EDF5, false);
    }

    private static void drawSlider(GuiGraphics g, int x, int y, int w, float norm, int color) {
        g.fill(x, y + 2, x + w, y + 6, 0xFF253247);
        int fillW = Math.round(Math.max(0f, Math.min(1f, norm)) * w);
        g.fill(x, y + 2, x + fillW, y + 6, color);
        int kx = x + Math.min(w - 6, Math.max(0, fillW - 3));
        g.fill(kx, y - 1, kx + 6, y + 9, 0xFFE8EDF5);
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private static boolean hover(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
