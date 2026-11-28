import os
import wave
import math
import json
import numpy as np

SR = 44100
BPM = 150.0
BEAT = 60.0 / BPM          # 0.400s
STEP = BEAT / 4.0          # 0.100s (16th note)
BAR = BEAT * 4.0           # 1.600s

SCRATCH_DIR = r"C:\Users\User\.gemini\antigravity\brain\43f29777-f22c-4b49-bcd1-f784c68983e8\scratch"
SOURCE_WAV = os.path.join(SCRATCH_DIR, "source.wav")
SAMPLES_DIR = os.path.join(SCRATCH_DIR, "samples")
OUT_DIR = r"D:\My\projects\ScriptCraft\phonk"
os.makedirs(SAMPLES_DIR, exist_ok=True)
os.makedirs(OUT_DIR, exist_ok=True)


# ============================================================================
# FAST FFT & STUDIO DSP UTILITIES
# ============================================================================

def load_wav(path):
    with wave.open(path, "rb") as w:
        ch = w.getnchannels()
        raw = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768.0
        raw = raw.reshape(-1, ch)
        if ch == 1:
            raw = np.repeat(raw, 2, axis=1)
    return raw


def save_wav(path, audio, sr=SR):
    audio = np.clip(audio, -1.0, 1.0)
    pcm = (audio * 32767.0).astype(np.int16)
    with wave.open(path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(pcm.tobytes())


def ensure_stereo(x):
    if x.ndim == 1:
        return np.column_stack([x, x])
    return x


def fast_white_noise(n, seed=1337):
    idx = np.arange(n, dtype=np.uint64) + np.uint64(seed * 2654435761)
    idx ^= idx >> np.uint64(16)
    idx *= np.uint64(0x7feb352d)
    idx ^= idx >> np.uint64(15)
    idx *= np.uint64(0x846ca68b)
    idx ^= idx >> np.uint64(16)
    u1 = ((idx & np.uint64(0xFFFFFF)).astype(np.float32) + 1.0) / 16777217.0
    idx2 = idx * np.uint64(1664525) + np.uint64(1013904223)
    u2 = ((idx2 & np.uint64(0xFFFFFF)).astype(np.float32) + 1.0) / 16777217.0
    return (np.sqrt(-2.0 * np.log(u1)) * np.cos(2.0 * np.pi * u2)).astype(np.float32)


def fft_filter(audio, hp_hz=None, lp_hz=None, order=4, eq_peaks=None):
    is_1d = (audio.ndim == 1)
    x = ensure_stereo(audio)
    n = len(x)
    if n == 0:
        return audio
    n_fft = 1 << (n + 2048 - 1).bit_length()
    freqs = np.fft.rfftfreq(n_fft, 1.0 / SR)
    freqs_safe = np.maximum(freqs, 1e-3)
    H = np.ones_like(freqs, dtype=np.float64)

    if hp_hz is not None and hp_hz > 0:
        H *= 1.0 / np.sqrt(1.0 + (hp_hz / freqs_safe) ** (2 * order))
        H[0] = 0.0
    if lp_hz is not None and lp_hz > 0:
        H *= 1.0 / np.sqrt(1.0 + (freqs_safe / lp_hz) ** (2 * order))
    if eq_peaks:
        for f0, gain_db, q in eq_peaks:
            bw = f0 / max(q, 0.1)
            bell = np.exp(-0.5 * ((freqs - f0) / (bw * 0.6)) ** 2)
            H *= 10.0 ** ((gain_db * bell) / 20.0)

    X = np.fft.rfft(x, n=n_fft, axis=0)
    y = np.fft.irfft(X * H[:, None], n=n_fft, axis=0)[:n].astype(np.float32)
    return y[:, 0] if is_1d else y


def filter_sweep_lp(audio, f_start, f_end, hp_hz=35.0):
    x = ensure_stereo(audio)
    n = len(x)
    win_len = 2048
    hop = 512
    win = np.hanning(win_len).astype(np.float32)[:, None]
    pad = win_len
    xp = np.pad(x, ((pad, pad + win_len), (0, 0)))
    yp = np.zeros_like(xp)
    wsum = np.zeros((len(xp), 1), dtype=np.float32)
    freqs = np.fft.rfftfreq(win_len, 1.0 / SR)
    freqs_safe = np.maximum(freqs, 1.0)

    num_frames = (len(xp) - win_len) // hop
    for i in range(num_frames):
        pos = i * hop
        prog = np.clip((pos - pad) / max(1, n), 0.0, 1.0)
        fc = f_start * ((f_end / f_start) ** prog)
        H = 1.0 / np.sqrt(1.0 + (freqs_safe / fc) ** 6)
        if hp_hz > 0:
            H *= 1.0 / np.sqrt(1.0 + (hp_hz / freqs_safe) ** 4)
            H[0] = 0.0
        seg = xp[pos:pos + win_len] * win
        Y = np.fft.rfft(seg, axis=0) * H[:, None]
        rec = np.fft.irfft(Y, n=win_len, axis=0) * win
        yp[pos:pos + win_len] += rec
        wsum[pos:pos + win_len] += win ** 2

    out = yp[pad:pad + n] / np.maximum(wsum[pad:pad + n], 1e-6)
    return out.astype(np.float32)


def resample_audio(audio, ratio):
    x = ensure_stereo(audio)
    n_in = len(x)
    n_out = max(1, int(round(n_in / ratio)))
    idx = np.linspace(0, n_in - 1, n_out, dtype=np.float32)
    i0 = np.floor(idx).astype(np.int32)
    i1 = np.minimum(i0 + 1, n_in - 1)
    frac = (idx - i0)[:, None]
    return (x[i0] * (1.0 - frac) + x[i1] * frac).astype(np.float32)


def wsola_stretch(audio, target_dur_sec):
    x = ensure_stereo(audio)
    n_in = len(x)
    n_out = max(256, int(round(target_dur_sec * SR)))
    grain = int(0.032 * SR)
    hop_s = grain // 2
    search = int(0.009 * SR)
    win = np.hanning(grain).astype(np.float32)[:, None]

    if n_in <= grain + search * 2:
        return resample_audio(x, n_in / n_out)

    out = np.zeros((n_out + grain, 2), dtype=np.float32)
    wsum = np.zeros((n_out + grain, 1), dtype=np.float32)
    mono = x.mean(axis=1)

    num_hops = max(1, (n_out - grain) // hop_s + 1)
    prev_in = 0
    for k in range(num_hops):
        out_pos = k * hop_s
        ideal_in = int(round((out_pos / max(1, n_out - grain)) * max(1, n_in - grain - 1)))
        if k == 0:
            best_in = 0
        else:
            expected = min(n_in - grain - 1, prev_in + hop_s)
            ref = mono[expected:expected + hop_s]
            s0 = max(0, ideal_in - search)
            s1 = min(n_in - grain - 1, ideal_in + search)
            if s1 > s0 and len(ref) == hop_s:
                cands = np.arange(s0, s1 + 1, 4)
                best_in = ideal_in
                best_corr = -1e9
                for c in cands:
                    seg = mono[c:c + hop_s]
                    score = float(np.dot(ref, seg))
                    if score > best_corr:
                        best_corr = score
                        best_in = int(c)
            else:
                best_in = ideal_in
        prev_in = best_in
        out[out_pos:out_pos + grain] += x[best_in:best_in + grain] * win
        wsum[out_pos:out_pos + grain] += win

    y = out[:n_out] / np.maximum(wsum[:n_out], 1e-4)
    fade = min(88, n_out // 16)
    y[:fade] *= np.linspace(0, 1, fade)[:, None]
    y[-fade * 4:] *= np.linspace(1, 0, fade * 4)[:, None]
    return y.astype(np.float32)


def clean_vocal_compressor(audio, thresh_db=-18.0, ratio=4.0, makeup_db=6.5):
    """
    Fast transparent vocal syllable leveler: brings initial consonants & unstressed first syllables
    ('О-гр-' in Огромный, 'Б-оль-' in Большой, 'Х-' in Хуй) right up to full vowel loudness!
    """
    x = ensure_stereo(audio)
    mono_sq = np.mean(x ** 2, axis=1)
    win = int(0.008 * SR)
    rms = np.sqrt(np.convolve(mono_sq, np.ones(win, dtype=np.float32) / win, mode="same") + 1e-8)
    db = 20.0 * np.log10(rms)
    over = np.maximum(0.0, db - thresh_db)
    gr_db = -over * (1.0 - 1.0 / ratio) + makeup_db
    s_win = int(0.004 * SR)
    gr_smooth = np.convolve(gr_db, np.ones(s_win, dtype=np.float32) / s_win, mode="same")
    gain = (10.0 ** (gr_smooth / 20.0)).astype(np.float32)
    y = x * gain[:, None]
    # Gentle soft-knee peak catch at 0.95
    y = np.tanh(y * 1.12) / np.tanh(1.12) * 0.95
    return y.astype(np.float32)


def lookahead_brickwall_limiter(audio, ceiling=0.96, lookahead_ms=4.5, release_ms=55.0):
    x = ensure_stereo(audio)
    n = len(x)
    la = max(1, int(lookahead_ms * 0.001 * SR))
    abs_peak = np.max(np.abs(x), axis=1)

    padded = np.pad(abs_peak, (0, la), mode="edge")
    sl_max = padded.copy()
    step = 1
    while step < la:
        shift = min(step, la - step)
        sl_max[:-shift] = np.maximum(sl_max[:-shift], sl_max[shift:])
        step += shift
    env = sl_max[:n]

    blk = 64
    nb = (n + blk - 1) // blk
    env_pad = np.pad(env, (0, nb * blk - n), mode="edge").reshape(nb, blk)
    blk_peaks = np.max(env_pad, axis=1)
    rel_blocks = max(2, int(release_ms * 0.001 * SR / blk))
    alpha = math.exp(-1.0 / rel_blocks)
    smoothed_blk = blk_peaks.copy()
    for i in range(1, nb):
        if smoothed_blk[i] < smoothed_blk[i - 1] * alpha:
            smoothed_blk[i] = smoothed_blk[i - 1] * alpha
    env_smooth = np.interp(
        np.arange(n, dtype=np.float32),
        np.arange(nb, dtype=np.float32) * blk,
        smoothed_blk,
    )
    env_final = np.maximum(env, env_smooth)
    gain = np.minimum(1.0, ceiling / np.maximum(env_final, 1e-6)).astype(np.float32)
    return x * gain[:, None]


def stereo_widener_haas(audio, delay_ms=6.5, mix=0.22):
    x = ensure_stereo(audio).copy()
    d = int(delay_ms * 0.001 * SR)
    mid = 0.5 * (x[:, 0] + x[:, 1])
    delayed = np.zeros_like(mid)
    if d < len(mid):
        delayed[d:] = mid[:-d]
    out_l = x[:, 0] + mix * (mid - delayed)
    out_r = x[:, 1] + mix * delayed
    return np.column_stack([out_l, out_r]).astype(np.float32)


def ping_pong_delay(audio, delay_sec, feedback=0.22, wet=0.10, hp_hz=450, lp_hz=3500):
    x = ensure_stereo(audio)
    n = len(x)
    d = int(round(delay_sec * SR))
    wet_buf = np.zeros_like(x)
    tap = fft_filter(x.mean(axis=1), hp_hz=hp_hz, lp_hz=lp_hz)
    gain = 1.0
    for bounce in range(1, 5):
        offset = bounce * d
        if offset >= n:
            break
        gain *= feedback if bounce > 1 else 1.0
        ch = bounce % 2
        wet_buf[offset:, ch] += tap[:n - offset] * gain
    return x + wet_buf * wet


def lush_reverb(audio, decay=1.3, wet=0.18, hp_hz=300, lp_hz=5500):
    x = ensure_stereo(audio)
    ir_len = int(decay * SR)
    t = np.arange(ir_len, dtype=np.float32) / SR
    env = np.exp(-t * (5.0 / decay)) * (1.0 - np.exp(-t * 90.0))
    ir_l = fast_white_noise(ir_len, 1337) * env
    ir_r = fast_white_noise(ir_len, 7331) * env
    for ms, g in [(14, 0.45), (26, 0.35), (41, 0.25)]:
        idx = int(ms * 0.001 * SR)
        if idx < ir_len:
            ir_l[idx] += g
            ir_r[min(ir_len - 1, idx + 95)] += g * 0.95
    ir = np.column_stack([ir_l, ir_r])
    ir = fft_filter(ir, hp_hz=hp_hz, lp_hz=lp_hz)
    ir /= np.sqrt(np.sum(ir ** 2) / 2.0 + 1e-8)

    n = len(x)
    n_fft = 1 << (n + ir_len - 1).bit_length()
    X = np.fft.rfft(x, n=n_fft, axis=0)
    H = np.fft.rfft(ir, n=n_fft, axis=0)
    y = np.fft.irfft(X * H, n=n_fft, axis=0)[:n].astype(np.float32)
    return x * (1.0 - wet * 0.25) + y * wet


def place(buf, clip, t_sec, gain=1.0, pan=0.0):
    clip = ensure_stereo(clip)
    i0 = int(round(t_sec * SR))
    if i0 >= len(buf) or i0 + len(clip) <= 0:
        return
    c0 = 0
    if i0 < 0:
        c0 = -i0
        i0 = 0
    length = min(len(clip) - c0, len(buf) - i0)
    if length <= 0:
        return
    gl = gain * math.cos((pan + 1.0) * math.pi / 4.0) * 1.4142
    gr = gain * math.sin((pan + 1.0) * math.pi / 4.0) * 1.4142
    buf[i0:i0 + length, 0] += clip[c0:c0 + length, 0] * gl
    buf[i0:i0 + length, 1] += clip[c0:c0 + length, 1] * gr


# ============================================================================
# AUTHENTIC HEAVY DRIFT PHONK SYNTHESIS (UPGRADED COWBELL, KICK, SNARE, 808)
# ============================================================================

def midi_to_hz(midi_note):
    return 440.0 * (2.0 ** ((midi_note - 69.0) / 12.0))


def build_master_808_cowbell():
    """
    Authentic Kordhell / DVRST / Roland TR-808 Drift Phonk Cowbell!
    Root tuned to F4 (MIDI 65 = 349.23 Hz, with 808 metallic pair at f0 & f0*1.4815 + octave harmonic bite).
    Sitting in the chesty F4..Db5 register gives that signature dark metallic Drift Phonk knock
    instead of a high whistly tone, AND leaves 1.5-4 kHz wide open for crystal-clear vocals!
    """
    dur = 0.30
    n = int(dur * SR)
    t = np.arange(n, dtype=np.float64) / SR
    f0 = midi_to_hz(65)        # F4 = 349.23 Hz (meaty Phonk register!)
    f1 = f0 * 1.48148          # Classic TR-808 inharmonic metallic ratio (517.4 Hz)
    f_oct = f0 * 2.0           # F5 harmonic snap (698.46 Hz)

    pitch_env = 1.0 + 0.022 * np.exp(-t / 0.0035)
    p0 = np.cumsum(f0 * pitch_env / SR)
    p1 = np.cumsum(f1 * pitch_env / SR)
    p2 = np.cumsum(f_oct * pitch_env / SR)

    # Crisp asymmetric square waves (true 808 diode-clipped character)
    sq0 = np.tanh(9.0 * (np.sin(2.0 * np.pi * p0) + 0.12))
    sq1 = np.tanh(9.0 * (np.sin(2.0 * np.pi * p1) - 0.08))
    sq2 = np.tanh(6.0 * np.sin(2.0 * np.pi * p2))
    fundamental = np.sin(2.0 * np.pi * p0) * 0.70

    # Tight percussive 808 cowbell envelope: hard punch + controlled metallic ring
    env_hit = np.exp(-t / 0.016)
    env_ring = np.exp(-t / 0.115)
    raw = (
        (sq0 * 0.65 + sq1 * 0.46 + sq2 * 0.34) * (0.68 * env_hit + 0.32 * env_ring)
        + fundamental * (0.55 * env_hit + 0.45 * np.exp(-t / 0.105))
    )

    click = fft_filter(fast_white_noise(n, 808), hp_hz=1200, lp_hz=5500) * np.exp(-t / 0.0022) * 0.28
    sig = (raw + click).astype(np.float32)

    # Classic TR-808 bandpass formant + OTT punch + pocket dip at 2.6 kHz for vocal consonants
    sig = fft_filter(
        sig, hp_hz=230.0, lp_hz=5600.0, order=3,
        eq_peaks=[(520.0, 3.8, 1.8), (1050.0, 3.2, 1.6), (2600.0, -3.0, 1.5)]
    )
    sig = np.tanh(sig * 2.2) / np.tanh(2.2)

    fade = int(0.020 * SR)
    sig[-fade:] *= np.linspace(1.0, 0.0, fade)
    sig /= np.max(np.abs(sig)) + 1e-6

    return stereo_widener_haas(np.column_stack([sig, sig]), delay_ms=5.2, mix=0.22)


MASTER_COWBELL = build_master_808_cowbell()


def note_cowbell(midi_note, length_sec, vel=1.0, slide_to_midi=None):
    # Master cowbell root is MIDI 65 (F4)
    ratio = 2.0 ** ((midi_note - 65.0) / 12.0)
    if slide_to_midi is None:
        cb = resample_audio(MASTER_COWBELL, ratio)
    else:
        ratio_end = 2.0 ** ((slide_to_midi - 65.0) / 12.0)
        n_out = max(64, int(len(MASTER_COWBELL) / ((ratio + ratio_end) * 0.5)))
        t_norm = np.linspace(0, 1, n_out)
        inst_ratio = ratio + (ratio_end - ratio) * (t_norm ** 1.6)
        idx = np.cumsum(inst_ratio)
        idx = np.clip(idx, 0, len(MASTER_COWBELL) - 1)
        i0 = np.floor(idx).astype(np.int32)
        i1 = np.minimum(i0 + 1, len(MASTER_COWBELL) - 1)
        fr = (idx - i0)[:, None]
        cb = (MASTER_COWBELL[i0] * (1.0 - fr) + MASTER_COWBELL[i1] * fr).astype(np.float32)

    max_n = int(round(length_sec * SR))
    if len(cb) > max_n and max_n > 64:
        cb = cb[:max_n].copy()
        rel = min(int(0.014 * SR), max_n // 3)
        cb[-rel:] *= np.linspace(1.0, 0.0, rel)[:, None]
    return cb * vel


def synth_phonk_kick():
    """Hard-hitting, punchy Drift Phonk kick."""
    dur = 0.165
    n = int(dur * SR)
    t = np.arange(n, dtype=np.float64) / SR
    f = 50.0 + 175.0 * np.exp(-t / 0.019) + 290.0 * np.exp(-t / 0.003)
    phase = 2.0 * np.pi * np.cumsum(f / SR)
    env = np.exp(-t / 0.085) * (1.0 - np.exp(-t / 0.0006))
    body = np.sin(phase) * env

    click = fft_filter(fast_white_noise(n, 42), hp_hz=1600, lp_hz=6200) * np.exp(-t / 0.0025) * 0.36
    kick = np.tanh((body * 1.95 + click) * 1.45).astype(np.float32)
    kick = fft_filter(kick, hp_hz=30.0, eq_peaks=[(75.0, 2.0, 1.4), (250.0, -4.0, 1.5)])
    fade = int(0.016 * SR)
    kick[-fade:] *= np.linspace(1.0, 0.0, fade)
    kick /= np.max(np.abs(kick)) + 1e-6
    return np.column_stack([kick, kick])


def synth_phonk_snare():
    """Snappy, cracking Memphis Phonk snare + clap."""
    dur = 0.19
    n = int(dur * SR)
    t = np.arange(n, dtype=np.float64) / SR
    f = 195.0 + 140.0 * np.exp(-t / 0.010)
    tone = np.sin(2.0 * np.pi * np.cumsum(f / SR)) * np.exp(-t / 0.038) * 0.68

    noise = fft_filter(fast_white_noise(n, 99), hp_hz=1150, lp_hz=13000,
                       eq_peaks=[(2400, 3.8, 1.4), (6200, 3.0, 1.2)])
    clap_env = (0.55 * np.exp(-np.maximum(0, t) / 0.006) +
                1.00 * np.exp(-np.maximum(0, t - 0.009) / 0.058) * (t >= 0.008))
    sn = np.tanh((tone + noise * clap_env * 0.85) * 2.05).astype(np.float32)
    sn /= np.max(np.abs(sn)) + 1e-6
    return stereo_widener_haas(np.column_stack([sn, sn]), delay_ms=3.0, mix=0.22)


def synth_hihat(open_hat=False):
    dur = 0.20 if open_hat else 0.044
    n = int(dur * SR)
    t = np.arange(n, dtype=np.float64) / SR
    sq = sum(np.sign(np.sin(2 * np.pi * f * t)) for f in [3520, 4710, 6280, 8120]) * 0.09
    nz = fast_white_noise(n, 777 if open_hat else 555) * 0.78
    raw = fft_filter((sq + nz).astype(np.float32), hp_hz=7200, lp_hz=16500)
    tau = 0.088 if open_hat else 0.013
    env = np.exp(-t / tau) * (1.0 - np.exp(-t / 0.0005))
    hh = np.tanh(raw * env * 2.1).astype(np.float32)
    hh /= np.max(np.abs(hh)) + 1e-6
    return np.column_stack([hh * 0.96, hh * 1.04])


def synth_808_note(midi_note, dur_sec, glide_from_midi=None, drive=3.4, reese=False):
    """
    Fat, aggressive Drift Phonk 808:
    - Massive sub fundamental (43.65 Hz F1)
    - Gritty low-mid growl (90-460 Hz) with steep 480 Hz lowpass so vocals above 500 Hz are 100% untouched!
    """
    n = max(128, int(round(dur_sec * SR)))
    t = np.arange(n, dtype=np.float64) / SR
    f_target = midi_to_hz(midi_note)
    if glide_from_midi is not None:
        f_start = midi_to_hz(glide_from_midi)
        f_curve = f_target + (f_start - f_target) * np.exp(-t / 0.045)
    else:
        f_curve = f_target * (1.0 + 0.42 * np.exp(-t / 0.015))

    phase = 2.0 * np.pi * np.cumsum(f_curve / SR)

    if reese:
        p_a = 2.0 * np.pi * np.cumsum((f_curve * 0.994) / SR)
        p_b = 2.0 * np.pi * np.cumsum((f_curve * 1.006) / SR)
        saw_a = 2.0 * ((p_a / (2 * np.pi)) % 1.0) - 1.0
        saw_b = 2.0 * ((p_b / (2 * np.pi)) % 1.0) - 1.0
        sub = np.sin(phase) * 0.82
        mid_l = fft_filter(np.tanh(saw_a * 2.1).astype(np.float32), hp_hz=85, lp_hz=560, order=4)
        mid_r = fft_filter(np.tanh(saw_b * 2.1).astype(np.float32), hp_hz=85, lp_hz=560, order=4)
        env = (1.0 - np.exp(-t / 0.015))
        rel = min(int(0.04 * SR), n // 4)
        env[-rel:] *= np.linspace(1, 0, rel)
        return np.column_stack([(sub + mid_l * 0.44) * env, (sub + mid_r * 0.44) * env]).astype(np.float32)

    sub_clean = np.sin(phase)
    harm_src = np.sin(phase) + 0.38 * np.sin(2.0 * phase)
    growl = np.tanh(harm_src * drive + 0.20 * (harm_src ** 2))
    growl = fft_filter(growl.astype(np.float32), hp_hz=88.0, lp_hz=470.0, order=4,
                       eq_peaks=[(145.0, 4.2, 1.3), (260.0, 2.5, 1.4)])

    decay_tau = max(0.45, dur_sec * 1.08)
    env = np.exp(-t / decay_tau) * (1.0 - np.exp(-t / 0.003))
    rel = min(int(0.022 * SR), n // 4)
    env[-rel:] *= np.linspace(1.0, 0.0, rel)

    mono_808 = (sub_clean * 0.82 + growl * 0.52) * env
    mono_808 = np.tanh(mono_808 * 1.22).astype(np.float32)
    return np.column_stack([mono_808, mono_808])


def synth_riser(dur_sec):
    n = int(round(dur_sec * SR))
    t = np.arange(n, dtype=np.float64) / SR
    prog = (t / dur_sec) ** 2.0
    f = 220.0 * (9.0 ** prog)
    sweep = np.sin(2.0 * np.pi * np.cumsum(f / SR)) * 0.26
    nz = filter_sweep_lp(fast_white_noise(n, 2026), 400.0, 10000.0, hp_hz=350.0)[:, 0]
    env = (t / dur_sec) ** 1.8
    rel = min(int(0.015 * SR), n // 8)
    env[-rel:] *= np.linspace(1, 0, rel)
    sig = (sweep + nz * 0.36) * env
    return stereo_widener_haas(np.column_stack([sig, sig]).astype(np.float32), delay_ms=10.0, mix=0.40)


def tape_stop(audio, stop_dur_sec=0.25):
    x = ensure_stereo(audio).copy()
    n_stop = min(len(x), int(round(stop_dur_sec * SR)))
    seg = x[-n_stop:]
    t = np.linspace(0.0, 1.0, n_stop)
    speed = np.maximum(0.04, (1.0 - t) ** 1.8)
    idx = np.cumsum(speed)
    idx = idx / idx[-1] * (n_stop - 1) * 0.52
    i0 = np.floor(idx).astype(np.int32)
    i1 = np.minimum(i0 + 1, n_stop - 1)
    fr = (idx - i0)[:, None]
    braked = (seg[i0] * (1.0 - fr) + seg[i1] * fr) * ((1.0 - t ** 3)[:, None])
    x[-n_stop:] = braked
    return x


# ============================================================================
# SURGICAL VOCAL SLICING DIRECTLY FROM SOURCE.WAV (100% FIRST-LETTER CLARITY!)
# ============================================================================

def slice_and_prepare_samples():
    src = load_wav(SOURCE_WAV)

    def cut(t0, t1, fi_ms=1.5, fo_ms=18.0, start_boost_ms=0, start_boost_gain=1.0):
        s = src[int(round(t0 * SR)):int(round(t1 * SR))].copy()
        if start_boost_ms > 0 and start_boost_gain > 1.0:
            nb = min(len(s), int(start_boost_ms * 0.001 * SR))
            # Smooth plateau boost across the entire initial consonant/syllable, then taper to 1.0!
            half = nb // 2
            ramp = np.ones(nb, dtype=np.float32) * start_boost_gain
            ramp[half:] = np.linspace(start_boost_gain, 1.0, nb - half)
            s[:nb] *= ramp[:, None]
        fi = min(int(fi_ms * 0.001 * SR), len(s) // 10)
        fo = min(int(fo_ms * 0.001 * SR), len(s) // 6)
        if fi > 0:
            s[:fi] *= np.linspace(0.0, 1.0, fi)[:, None]
        if fo > 0:
            s[-fo:] *= np.linspace(1.0, 0.0, fo)[:, None]
        pk = float(np.max(np.abs(s)))
        if pk > 1e-5:
            s = s / pk * 0.94
        return s.astype(np.float32)

    raw = {
        # Isolated Beatbox One-Shots (0..4s)
        "bb_hat":    cut(0.320, 0.430, 2.0, 15.0),
        "bb_kick1":  cut(0.795, 0.930, 1.5, 18.0),
        "bb_snare1": cut(1.290, 1.590, 2.0, 22.0),
        "bb_kick2":  cut(1.810, 1.930, 1.5, 18.0),
        "bb_snare2": cut(3.310, 3.600, 2.0, 22.0),
        "bb_loop":   cut(4.325, 8.345, 4.0, 15.0),

        # Verified by ear! Exact complete words with boosted first letters ('Х-', 'О-гр-', 'Б-оль-'):
        "hui1":      cut(8.850, 9.215, 1.5, 18.0, start_boost_ms=115, start_boost_gain=1.35),
        "hui2":      cut(7.840, 8.200, 1.5, 18.0, start_boost_ms=115, start_boost_gain=1.35),
        "ogromny1":  cut(10.195, 10.855, 1.5, 20.0, start_boost_ms=230, start_boost_gain=1.55),
        "ogromny2":  cut(11.205, 11.865, 1.5, 20.0, start_boost_ms=230, start_boost_gain=1.55),
        "bolshoi1":  cut(12.178, 12.845, 1.5, 20.0, start_boost_ms=270, start_boost_gain=1.60),
        "bolshoi2":  cut(13.185, 13.855, 1.5, 20.0, start_boost_ms=270, start_boost_gain=1.65),
        "full_loop": cut(9.860, 13.880, 3.0, 15.0),
    }

    s = {}
    for k in ["bb_hat", "bb_kick1", "bb_kick2", "bb_snare1", "bb_snare2"]:
        hp = 200 if ("hat" in k or "snare" in k) else 60
        eq = [(3200, 3.0, 1.4)] if ("snare" in k or "hat" in k) else [(150, 2.5, 1.5), (2500, 2.5, 1.5)]
        proc = fft_filter(raw[k], hp_hz=hp, lp_hz=12500, eq_peaks=eq)
        s[k] = (np.tanh(proc * 1.5) / np.tanh(1.5)).astype(np.float32)

    s["bb_loop_150"] = fft_filter(
        np.tanh(wsola_stretch(raw["bb_loop"], 2 * BAR) * 1.45) / np.tanh(1.45),
        hp_hz=160, lp_hz=11500, eq_peaks=[(2800, 2.5, 1.4)]
    )

    fl_150 = wsola_stretch(raw["full_loop"], 2 * BAR)
    fl_150 = fft_filter(fl_150, hp_hz=95, lp_hz=12500, eq_peaks=[(310, -2.0, 1.5), (2600, 3.5, 1.3), (5200, 2.8, 1.3)])
    s["full_loop_150"] = clean_vocal_compressor(fl_150, thresh_db=-17.0, ratio=3.5, makeup_db=5.5)

    for base in ["hui", "ogromny", "bolshoi"]:
        for idx in ["1", "2"]:
            key = f"{base}{idx}"
            mono_clean = raw[key].mean(axis=1)
            eq_v = fft_filter(
                mono_clean,
                hp_hz=90.0,     # Full male chest body on voiced 'Б-' (550 Hz) and 'О-'!
                lp_hz=14000.0,
                order=3,
                eq_peaks=[
                    (170.0, 1.5, 1.5),    # Warmth on 'Б-' and 'О-'
                    (340.0, -2.2, 1.6),   # Mud cleanup
                    (1850.0, 2.5, 1.3),   # Vowel presence
                    (3400.0, 4.0, 1.3),   # Crisp consonant snap ('Б', 'Г', 'Р')
                    (6000.0, 3.0, 1.2),   # Air on 'Х' & 'Ш'
                ],
            )
            lead = clean_vocal_compressor(np.column_stack([eq_v, eq_v]), thresh_db=-18.5, ratio=4.0, makeup_db=6.5)
            s[key] = lead

            evil_raw = resample_audio(lead, 2.0 ** (-5.0 / 12.0))
            evil_fit = wsola_stretch(evil_raw, len(lead) / SR)
            evil_eq = fft_filter(evil_fit, hp_hz=220, lp_hz=3000, order=3, eq_peaks=[(950, 2.8, 1.5)])
            s[f"{key}_evil"] = stereo_widener_haas(evil_eq, delay_ms=11.0, mix=0.55)

            st_len = min(len(lead), int(0.175 * SR))
            st = lead[:st_len].copy()
            fo = int(0.018 * SR)
            st[-fo:] *= np.linspace(1.0, 0.0, fo)[:, None]
            s[f"{key}_stut"] = st

    return s


# ============================================================================
# FULL TRACK ARRANGEMENT & MIXING
# ============================================================================

def render_arrangement():
    s = slice_and_prepare_samples()
    kick_sample = synth_phonk_kick()
    snare_sample = synth_phonk_snare()
    hh_closed = synth_hihat(open_hat=False)
    hh_open = synth_hihat(open_hat=True)

    main_bars = 24
    main_len = int(round((main_bars * BAR + 0.5) * SR))

    bus_kick = np.zeros((main_len, 2), dtype=np.float32)
    bus_snare = np.zeros((main_len, 2), dtype=np.float32)
    bus_hats = np.zeros((main_len, 2), dtype=np.float32)
    bus_808 = np.zeros((main_len, 2), dtype=np.float32)
    bus_cowbell = np.zeros((main_len, 2), dtype=np.float32)
    bus_beatbox = np.zeros((main_len, 2), dtype=np.float32)
    bus_vocals = np.zeros((main_len, 2), dtype=np.float32)
    bus_voc_bg = np.zeros((main_len, 2), dtype=np.float32)
    bus_fx = np.zeros((main_len, 2), dtype=np.float32)

    kick_times = []
    lyric_events = []
    # Track exact initial-consonant windows [(t0, t1)] so drums/kicks duck slightly instead of masking 'Б-' / 'О-'!
    consonant_windows = []

    def add_lyric(t, text, style="main"):
        lyric_events.append({"t": round(float(t), 4), "text": text, "style": style})

    # Place words in KICK-FREE pockets so 'Х-', 'О-', 'Б-' start cleanly without colliding with a Kick!
    def place_word_start(key, start_sec, gain=1.0, evil_shadow=0.0, lyric_text=None, style="drop"):
        t_start = max(0.0, start_sec)
        place(bus_vocals, s[key], t_start, gain=gain)
        if evil_shadow > 0.0:
            place(bus_voc_bg, s[f"{key}_evil"], t_start, gain=evil_shadow)
        # Protect first 240ms of 'Огромный' / 'Большой' and first 120ms of 'Хуй' from drum masking!
        prot_dur = 0.24 if ("ogromny" in key or "bolshoi" in key) else 0.12
        consonant_windows.append((t_start - 0.015, t_start + prot_dur))
        if lyric_text:
            add_lyric(t_start + 0.02, lyric_text, style)

    # ------------------------------------------------------------------------
    # 1. INTRO (Bars 0..3: 0.0s .. 6.4s)
    # ------------------------------------------------------------------------
    intro_bb = np.zeros((int(4 * BAR * SR), 2), dtype=np.float32)
    place(intro_bb, s["bb_loop_150"], 0.0, gain=0.88)
    place(intro_bb, s["bb_loop_150"], 2 * BAR, gain=0.92)
    intro_bb = filter_sweep_lp(intro_bb, 750.0, 10500.0, hp_hz=170.0)
    place(bus_beatbox, intro_bb, 0.0, gain=0.86)
    add_lyric(0.15, "...", "intro")
    add_lyric(1.60, "BEATBOX", "intro")

    reese_prog = [(0.0, 29, BAR), (BAR, 37, BAR), (2 * BAR, 32, 2.5 * BEAT)]
    for t_r, m_note, dur_r in reese_prog:
        place(bus_808, synth_808_note(m_note, dur_r, reese=True), t_r, gain=0.52)

    intro_cb_buf = np.zeros((int(3.0 * BAR * SR), 2), dtype=np.float32)
    teaser_riff = [
        (0, 65, 2), (2, 65, 1), (3, 68, 2), (6, 72, 2), (8, 73, 2), (10, 72, 2), (12, 68, 2), (14, 66, 2),
        (16, 65, 2), (18, 65, 1), (19, 68, 2), (22, 72, 2), (24, 77, 2), (26, 75, 2), (28, 72, 2), (30, 68, 2),
    ]
    for st_i, m_n, dur_st in teaser_riff:
        place(intro_cb_buf, note_cowbell(m_n, dur_st * STEP, vel=0.78), st_i * STEP, gain=0.78)
    intro_cb_buf = filter_sweep_lp(intro_cb_buf, 500.0, 3800.0, hp_hz=240.0)
    intro_cb_buf = lush_reverb(intro_cb_buf, decay=1.3, wet=0.30)
    place(bus_cowbell, intro_cb_buf, 0.0, gain=0.72)

    # Pre-Drop Callout in Bar 2.5..Bar 3: Solo, loud, 100% intelligible!
    place_word_start("hui1",     2 * BAR + 7.0 * STEP, gain=1.02, evil_shadow=0.20, lyric_text="ХУЙ", style="main")
    place_word_start("ogromny1", 3 * BAR + 0.0 * STEP, gain=1.08, evil_shadow=0.20, lyric_text="ОГРОМНЫЙ", style="main")
    place_word_start("bolshoi1", 3 * BAR + 8.5 * STEP, gain=1.14, evil_shadow=0.20, lyric_text="БОЛЬШОЙ!", style="drop_call")

    place(bus_fx, synth_riser(BAR * 0.82), 3 * BAR, gain=0.38)
    for i in range(5):
        vel = 0.28 + 0.45 * (i / 4.0)
        place(bus_snare, snare_sample, 2 * BAR + (11 + i) * STEP, gain=vel * 0.55)

    # Mute background beatbox during Bar 3 pre-drop words so "ОГРОМНЫЙ... БОЛЬШОЙ!" hits in dead-clean air!
    mute_s = int((3 * BAR - 0.05) * SR)
    mute_e = int(4 * BAR * SR)
    bus_beatbox[mute_s:mute_e] *= 0.18
    bus_beatbox[int((4 * BAR - 0.15) * SR):mute_e] = 0.0

    # ------------------------------------------------------------------------
    # AUTHENTIC DRIFT PHONK COWBELL MELODIES (F4 = 65 root register!)
    # ------------------------------------------------------------------------
    cb_pattern_A = [
        # Bar 1: F4 - F4 - Ab4 - C5 - Db5->C5 - Ab4 - Gb4 - F4
        (0,  65, 1.7, 1.00, None),
        (2,  65, 0.9, 0.82, None),
        (3,  68, 1.7, 0.96, None),
        (5,  65, 0.9, 0.78, None),
        (6,  72, 1.7, 1.05, None),
        (8,  73, 1.7, 1.02, 72),
        (10, 72, 1.7, 0.94, None),
        (12, 68, 1.7, 0.92, None),
        (14, 66, 0.9, 0.86, None),
        (15, 65, 0.9, 0.88, None),
        # Bar 2: F4 - Ab4 - Bb4 - C5 - Ab4 - F4 - Eb4 - Gb4 - Ab4
        (16, 65, 1.7, 1.00, None),
        (18, 65, 0.9, 0.80, None),
        (19, 68, 1.7, 0.95, None),
        (21, 70, 1.7, 0.98, None),
        (23, 72, 1.7, 1.05, None),
        (25, 68, 1.4, 0.90, None),
        (27, 65, 1.7, 0.95, None),
        (29, 63, 1.3, 0.88, None),
        (30, 66, 0.9, 0.86, None),
        (31, 68, 0.9, 0.92, None),
    ]

    cb_pattern_VIP = [
        (0,  65, 1.4, 1.00, None),
        (1,  77, 0.9, 0.88, None),   # F5 octave flick!
        (2,  65, 0.9, 0.84, None),
        (3,  68, 1.7, 0.98, None),
        (5,  72, 1.4, 0.96, None),
        (6,  77, 1.7, 1.06, 76),
        (8,  73, 1.7, 1.02, None),
        (10, 72, 1.4, 0.95, None),
        (11, 68, 0.9, 0.86, None),
        (12, 72, 1.7, 0.98, None),
        (14, 66, 0.9, 0.90, None),
        (15, 65, 0.9, 0.92, None),
        (16, 65, 1.4, 1.02, None),
        (17, 77, 0.9, 0.86, None),
        (18, 68, 1.4, 0.92, None),
        (19, 72, 1.7, 1.00, None),
        (21, 75, 1.7, 1.04, None),
        (23, 77, 1.7, 1.06, None),
        (25, 73, 1.4, 0.96, None),
        (27, 72, 1.7, 0.98, None),
        (29, 68, 1.4, 0.92, None),
        (30, 66, 0.9, 0.90, None),
        (31, 63, 0.9, 0.88, None),
    ]

    # ------------------------------------------------------------------------
    # 2. SCHEDULE DROP 1 & VIP DROP 2 VOCALS FIRST (IN OPEN RHYTHMIC POCKETS!)
    # Notice Kick steps are [0, 3, 6, 10] and Snare steps are [4, 12].
    # Starting 'Огромный' / 'Большой' at Step 6.8 (0.68s -> right AFTER the Kick at Step 6 finishes!)
    # means 'О-' and 'Б-' hit in the WIDE-OPEN pocket [6.8..9.5] with ZERO Kick or Snare collision,
    # and the stressed syllable ('-ГРОМ-' / '-ШОЙ') lands right on Step 9..10.5!
    # ------------------------------------------------------------------------
    for cycle, base_bar in enumerate([4, 8]):
        ev_lvl = 0.0 if cycle == 0 else 0.24
        bt = base_bar * BAR
        # Bar +0: "ХУЙ" (step 0.8) ... "ОГРОМНЫЙ" (starts step 6.8 in clean gap after step-6 kick!)
        place_word_start("hui1",     bt + 0.8 * STEP, gain=1.06, evil_shadow=ev_lvl, lyric_text="ХУЙ", style="drop")
        place_word_start("ogromny1", bt + 6.8 * STEP, gain=1.12, evil_shadow=ev_lvl, lyric_text="ОГРОМНЫЙ", style="drop")

        # Bar +1: "ХУЙ" (step 0.8) ... "БОЛЬШОЙ" (starts step 6.8 in clean gap after step-6 kick!)
        place_word_start("hui2",     bt + BAR + 0.8 * STEP, gain=1.06, evil_shadow=ev_lvl, lyric_text="ХУЙ", style="drop")
        place_word_start("bolshoi1", bt + BAR + 6.8 * STEP, gain=1.14, evil_shadow=ev_lvl, lyric_text="БОЛЬШОЙ", style="drop")

        # Bar +2: Cowbell riff shines on beats 1-2 -> "ОГРОМНЫЙ" (starts step 6.8 cleanly!)
        place_word_start("ogromny2", bt + 2 * BAR + 6.8 * STEP, gain=1.12, evil_shadow=ev_lvl, lyric_text="ОГРОМНЫЙ", style="drop")

        # Bar +3: "ХУЙ" (step 0.8) -> "БОЛЬШОЙ!" (starts step 6.8 cleanly!)
        place_word_start("hui1",     bt + 3 * BAR + 0.8 * STEP, gain=1.06, evil_shadow=ev_lvl, lyric_text="ХУЙ", style="drop")
        place_word_start("bolshoi2", bt + 3 * BAR + 6.8 * STEP, gain=1.16, evil_shadow=ev_lvl, lyric_text="БОЛЬШОЙ!", style="drop_call")

    for cycle, base_bar in enumerate([16, 20]):
        bt = base_bar * BAR
        # VIP Drop 2: Crisp 3-word anthem flow in clean pockets!
        place_word_start("hui1",     bt + 0.8 * STEP,       gain=1.08, evil_shadow=0.25, lyric_text="ХУЙ", style="vip")
        place_word_start("ogromny1", bt + 6.8 * STEP,       gain=1.14, evil_shadow=0.25, lyric_text="ОГРОМНЫЙ", style="vip")
        place_word_start("bolshoi1", bt + BAR + 0.8 * STEP, gain=1.16, evil_shadow=0.25, lyric_text="БОЛЬШОЙ", style="vip")
        place_word_start("hui2",     bt + BAR + 10.5 * STEP, gain=1.08, evil_shadow=0.25, lyric_text="ХУЙ!", style="vip")

        place_word_start("ogromny2", bt + 2 * BAR + 0.8 * STEP, gain=1.14, evil_shadow=0.25, lyric_text="ОГРОМНЫЙ", style="vip")
        place_word_start("bolshoi2", bt + 2 * BAR + 8.5 * STEP, gain=1.16, evil_shadow=0.25, lyric_text="БОЛЬШОЙ!", style="vip")
        place_word_start("hui1",     bt + 3 * BAR + 0.8 * STEP, gain=1.08, evil_shadow=0.25, lyric_text="ХУЙ", style="vip")
        place_word_start("ogromny1", bt + 3 * BAR + 6.8 * STEP, gain=1.14, evil_shadow=0.25, lyric_text="ОГРОМНЫЙ!", style="drop_call")

    def in_consonant_guard(t_sec):
        for w0, w1 in consonant_windows:
            if w0 <= t_sec <= w1:
                return True
        return False

    def program_drop_section(start_bar, num_bars, vip=False):
        for b in range(num_bars):
            bar_t = (start_bar + b) * BAR
            if b % 2 == 0:
                place(bus_beatbox, s["bb_loop_150"], bar_t, gain=0.36 if not vip else 0.40)

            k_steps = [0, 3, 6, 10] if (b % 2 == 0) else [0, 3, 6, 10, 14]
            if b == num_bars - 1:
                k_steps = [0, 3, 6, 11, 14]
            for ks in k_steps:
                kt = bar_t + ks * STEP
                # Soften kick if it ever lands inside an initial-consonant guard window!
                k_gain = 0.52 if in_consonant_guard(kt) else 1.00
                if k_gain > 0.7:
                    kick_times.append(kt)
                place(bus_kick, kick_sample, kt, gain=k_gain)
                bbk = s["bb_kick1"] if (ks % 2 == 0) else s["bb_kick2"]
                place(bus_beatbox, bbk, kt, gain=0.38 * k_gain)

            for ss in [4, 12]:
                st = bar_t + ss * STEP
                s_gain = 0.55 if in_consonant_guard(st) else 0.94
                place(bus_snare, snare_sample, st, gain=s_gain)
                bbs = s["bb_snare1"] if ss == 4 else s["bb_snare2"]
                place(bus_beatbox, bbs, st, gain=0.42 * s_gain)

            if b % 4 == 3:
                for r_step in [14, 15]:
                    place(bus_snare, snare_sample, bar_t + r_step * STEP, gain=0.62 + 0.12 * (r_step - 14))

            for hs in range(16):
                ht = bar_t + hs * STEP
                if hs in [2, 10] or (vip and hs in [2, 6, 10, 14]):
                    place(bus_hats, hh_open, ht, gain=0.32, pan=0.18)
                    place(bus_beatbox, s["bb_hat"], ht, gain=0.26, pan=-0.18)
                else:
                    vel = 0.38 if (hs % 2 == 0) else 0.25
                    place(bus_hats, hh_closed, ht, gain=vel, pan=-0.14 if (hs % 2 == 0) else 0.14)
                if hs in [3, 11]:
                    for sub in [0.33, 0.66]:
                        place(bus_hats, hh_closed, ht + sub * STEP, gain=0.22, pan=0.22)

            prog_idx = b % 4
            if prog_idx == 0:
                bass_notes = [(0, 29, 3, None), (3, 29, 3, None), (6, 29, 4, None), (10, 29, 6, 41 if vip else None)]
            elif prog_idx == 1:
                bass_notes = [(0, 37, 3, 29), (3, 37, 3, None), (6, 37, 4, None), (10, 36, 6, 37)]
            elif prog_idx == 2:
                bass_notes = [(0, 32, 3, None), (3, 32, 3, None), (6, 32, 4, None), (10, 34, 6, 32)]
            else:
                bass_notes = [(0, 30, 3, None), (3, 30, 3, None), (6, 36, 4, 30), (10, 30, 6, 36)]
            for bs_st, m_b, dur_st, gl_m in bass_notes:
                n808 = synth_808_note(m_b, dur_st * STEP, glide_from_midi=gl_m, drive=3.6 if vip else 3.2)
                place(bus_808, n808, bar_t + bs_st * STEP, gain=0.92)

            if b % 2 == 0:
                pat = cb_pattern_VIP if vip else cb_pattern_A
                for st_i, m_n, dur_st, vel, sl in pat:
                    cb_clip = note_cowbell(m_n, dur_st * STEP, vel=vel, slide_to_midi=sl)
                    pan_val = 0.11 if (st_i % 2 == 0) else -0.11
                    place(bus_cowbell, cb_clip, bar_t + st_i * STEP, gain=0.88, pan=pan_val)
                    if vip:
                        cb_hi = note_cowbell(m_n + 12, dur_st * STEP, vel=vel * 0.34, slide_to_midi=(sl + 12 if sl else None))
                        place(bus_cowbell, cb_hi, bar_t + st_i * STEP, gain=0.36, pan=-pan_val)

    program_drop_section(start_bar=4, num_bars=8, vip=False)
    program_drop_section(start_bar=16, num_bars=8, vip=True)

    # ------------------------------------------------------------------------
    # 3. MEMPHIS BRIDGE / BREAKDOWN (Bars 12..15: 19.2s .. 25.6s)
    # ------------------------------------------------------------------------
    br_t = 12 * BAR
    place(bus_vocals, s["full_loop_150"], br_t, gain=1.04)
    bridge_words = ["ХУЙ — ОГРОМНЫЙ", "ХУЙ — ОГРОМНЫЙ", "ХУЙ — БОЛЬШОЙ", "ХУЙ — БОЛЬШОЙ"]
    for idx_w, w_txt in enumerate(bridge_words):
        add_lyric(br_t + idx_w * (2 * BEAT), w_txt, "evil")

    for b_off, m_n in [(0, 29), (1, 30), (2, 32)]:
        place(bus_808, synth_808_note(m_n, BAR, reese=True), br_t + b_off * BAR, gain=0.56)
        kt = br_t + b_off * BAR
        kick_times.append(kt)
        place(bus_kick, kick_sample, kt, gain=0.82)
        place(bus_snare, snare_sample, kt + 8 * STEP, gain=0.78)

    br_cb = np.zeros((int(3 * BAR * SR), 2), dtype=np.float32)
    for st_i, m_n in [(0, 65), (6, 68), (12, 66), (16, 65), (22, 63), (28, 60), (32, 61), (38, 60), (44, 56)]:
        place(br_cb, note_cowbell(m_n, 4 * STEP, vel=0.72), st_i * STEP, gain=0.78)
    br_cb = lush_reverb(fft_filter(br_cb, hp_hz=220, lp_hz=3400), decay=1.8, wet=0.40)
    place(bus_cowbell, br_cb, br_t, gain=0.72)

    # Bar 14..15: Clean full-word Memphis Callout + Snare Build into VIP Drop 2!
    place_word_start("ogromny1", br_t + 2 * BAR + 1.0 * STEP, gain=1.10, evil_shadow=0.26, lyric_text="ОГРОМНЫЙ", style="evil")
    place_word_start("bolshoi2", br_t + 2 * BAR + 9.0 * STEP, gain=1.14, evil_shadow=0.26, lyric_text="БОЛЬШОЙ", style="evil")

    b15 = 15 * BAR
    place(bus_fx, synth_riser(BAR * 0.85), b15, gain=0.45)
    for i in range(6):
        vel = 0.30 + 0.55 * (i / 5.0)
        place(bus_snare, snare_sample, b15 + i * STEP, gain=vel * 0.58)

    place_word_start("hui1",     b15 + 0.5 * STEP, gain=1.08, evil_shadow=0.22, lyric_text="ХУЙ", style="main")
    place_word_start("ogromny2", b15 + 4.5 * STEP, gain=1.14, evil_shadow=0.22, lyric_text="ОГРОМНЫЙ", style="main")
    place_word_start("bolshoi1", b15 + 9.5 * STEP, gain=1.18, evil_shadow=0.22, lyric_text="БОЛЬШОЙ!", style="drop_call")

    # ========================================================================
    # CLEAN BUS PROCESSING, KICK SIDECHAIN & VOCAL-PRIORITY UNMASKING
    # ========================================================================
    bus_cowbell = ping_pong_delay(bus_cowbell, delay_sec=1.5 * STEP, feedback=0.20, wet=0.10)
    bus_cowbell = lush_reverb(bus_cowbell, decay=0.80, wet=0.13, hp_hz=320, lp_hz=6200)

    voc_amb = ping_pong_delay(bus_vocals, delay_sec=2.0 * STEP, feedback=0.16, wet=0.08, hp_hz=500, lp_hz=3400)
    bus_vocals_final = bus_vocals * 0.96 + voc_amb * 0.12 + bus_voc_bg * 0.78

    bus_snare = lush_reverb(bus_snare, decay=0.45, wet=0.09, hp_hz=600, lp_hz=9000)

    # 1) Kick Sidechain (pumps 808, Cowbell, Beatbox — NEVER ducks Vocals!)
    sc_808 = np.ones(main_len, dtype=np.float32)
    sc_mel = np.ones(main_len, dtype=np.float32)

    duck_len = int(0.145 * SR)
    t_duck = np.arange(duck_len, dtype=np.float32) / SR
    curve_808 = 1.0 - 0.88 * np.exp(-t_duck / 0.048) * (1.0 - np.exp(-t_duck / 0.0015))
    curve_mel = 1.0 - 0.54 * np.exp(-t_duck / 0.052) * (1.0 - np.exp(-t_duck / 0.002))

    for kt in kick_times:
        i0 = int(round(kt * SR))
        if 0 <= i0 < main_len:
            L = min(duck_len, main_len - i0)
            sc_808[i0:i0 + L] = np.minimum(sc_808[i0:i0 + L], curve_808[:L])
            sc_mel[i0:i0 + L] = np.minimum(sc_mel[i0:i0 + L], curve_mel[:L])

    # 2) VOCAL-PRIORITY UNMASKING (20ms lookahead ducks Cowbell, Beatbox & Hats BEFORE 'Б-'/'О-'/'Х-' begins!)
    voc_abs = np.max(np.abs(bus_vocals), axis=1)
    la_v = int(0.020 * SR)
    voc_shift = np.pad(voc_abs[la_v:], (0, la_v))
    win_v = int(0.032 * SR)
    voc_env = np.convolve(np.maximum(voc_abs, voc_shift), np.ones(win_v, dtype=np.float32) / win_v, mode="same")
    voc_activity = np.clip(voc_env * 3.5, 0.0, 1.0)

    duck_for_voc_cb = 1.0 - 0.52 * voc_activity
    duck_for_voc_bb = 1.0 - 0.60 * voc_activity
    duck_for_voc_hh = 1.0 - 0.38 * voc_activity

    mix_main = (
        bus_kick * 1.02 +
        bus_snare * 0.90 +
        bus_hats * duck_for_voc_hh[:, None] * 0.74 +
        bus_808 * sc_808[:, None] * 0.96 +
        bus_cowbell * (sc_mel * duck_for_voc_cb)[:, None] * 0.84 +
        bus_beatbox * (sc_mel * duck_for_voc_bb)[:, None] * 0.60 +
        bus_vocals_final * 1.25 +
        bus_fx * 0.50
    )

    n_24 = int(round(24 * BAR * SR))
    mix_24 = mix_main[:n_24].copy()
    mix_24 = tape_stop(mix_24, stop_dur_sec=0.26)

    # ------------------------------------------------------------------------
    # 5. SLOWED + REVERB OUTRO (38.4s .. ~46.5s)
    # ------------------------------------------------------------------------
    vip_4bars = mix_main[int(round(20 * BAR * SR)):n_24].copy()
    slow_ratio = 2.0 ** (-4.0 / 12.0)
    slowed = resample_audio(vip_4bars, slow_ratio)
    slowed = fft_filter(slowed, hp_hz=32.0, lp_hz=9200.0, eq_peaks=[(58.0, 1.5, 1.2), (2800.0, 2.0, 1.3)])
    slowed = lush_reverb(slowed, decay=2.0, wet=0.28, hp_hz=250.0, lp_hz=5800.0)

    t_outro_start = 24 * BAR
    add_lyric(t_outro_start + 0.05, "SLOWED + REVERB", "slowed")
    for rel_st, txt in [(0.8, "ХУЙ"), (6.8, "ОГРОМНЫЙ"), (16.8, "БОЛЬШОЙ"), (26.5, "ХУЙ!")]:
        t_sl = t_outro_start + (rel_st * STEP) / slow_ratio
        if t_sl < t_outro_start + len(slowed) / SR - 0.6:
            add_lyric(t_sl, txt, "slowed")

    fi_s = int(0.04 * SR)
    fo_s = int(2.2 * SR)
    slowed[:fi_s] *= np.linspace(0.0, 1.0, fi_s)[:, None]
    slowed[-fo_s:] *= (np.linspace(1.0, 0.0, fo_s) ** 1.6)[:, None]

    full_mix = np.vstack([mix_24, slowed * 0.92])

    crackle_n = int(3.2 * SR)
    wn_c = fast_white_noise(crackle_n, 1999)
    dust = (np.abs(wn_c) > 3.35).astype(np.float32) * wn_c * 0.010
    hiss = fft_filter(fast_white_noise(crackle_n, 2001) * 0.002, hp_hz=2000, lp_hz=8000)
    full_mix[:crackle_n] += np.column_stack([dust + hiss, dust * 0.8 + hiss])

    # ========================================================================
    # STUDIO MASTERING: LINEAR EQ + TRUE LOOKAHEAD BRICKWALL LIMITER
    # ========================================================================
    full_mix = fft_filter(
        full_mix,
        hp_hz=28.0,
        lp_hz=18500.0,
        order=3,
        eq_peaks=[
            (52.0, 1.6, 1.3),
            (250.0, -2.0, 1.5),
            (3100.0, 1.8, 1.3),
            (10500.0, 1.5, 1.0),
        ],
    )

    rms = float(np.sqrt(np.mean(full_mix[int(6.4 * SR):int(38.0 * SR)] ** 2)))
    full_mix *= (0.325 / max(rms, 1e-4))
    full_mix = lookahead_brickwall_limiter(full_mix, ceiling=0.96, lookahead_ms=4.5, release_ms=55.0)

    wav_out = os.path.join(OUT_DIR, "phonk_remix.wav")
    save_wav(wav_out, full_mix, SR)

    kick_all = sorted(kick_times + [t_outro_start + (k * STEP) / slow_ratio for k in [0, 3, 6, 10, 16, 19, 22, 26]])
    meta = {
        "duration": round(len(full_mix) / SR, 4),
        "bpm": BPM,
        "drop1_start": 4 * BAR,
        "bridge_start": 12 * BAR,
        "drop2_start": 16 * BAR,
        "slowed_start": 24 * BAR,
        "kicks": [round(float(k), 4) for k in kick_all],
        "lyrics": sorted(lyric_events, key=lambda e: e["t"]),
    }
    with open(os.path.join(SCRATCH_DIR, "remix_meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)

    peak = float(np.max(np.abs(full_mix)))
    drop_rms = float(np.sqrt(np.mean(full_mix[int(6.4 * SR):int(19.2 * SR)] ** 2)))
    est_lufs = 20.0 * math.log10(max(drop_rms, 1e-6)) - 0.691
    print(f"RENDERED: {wav_out}")
    print(f"Duration: {len(full_mix)/SR:.2f}s | Peak: {peak:.3f} | Drop RMS: {drop_rms:.3f} (~{est_lufs:.1f} dBFS/LUFS)")


if __name__ == "__main__":
    render_arrangement()
