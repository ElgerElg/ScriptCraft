import os
import json
import wave
import math
import subprocess
import numpy as np
import cv2
from PIL import Image, ImageDraw, ImageFont

FFMPEG = r"C:\Program Files\Virtual Desktop Streamer\ffmpeg.exe"
SCRATCH_DIR = r"C:\Users\User\.gemini\antigravity\brain\43f29777-f22c-4b49-bcd1-f784c68983e8\scratch"
OUT_DIR = r"D:\My\projects\ScriptCraft\phonk"

W, H = 720, 720
FPS = 60


def load_font(size):
    for candidate in [
        r"C:\Windows\Fonts\impact.ttf",
        r"C:\Windows\Fonts\arialbd.ttf",
        r"C:\Windows\Fonts\segoeuib.ttf",
        r"C:\Windows\Fonts\tahomabd.ttf",
    ]:
        if os.path.exists(candidate):
            return ImageFont.truetype(candidate, size)
    return ImageFont.load_default()


def prerender_text_mask(text, font_size):
    font = load_font(font_size)
    img = Image.new("L", (W, 240), 0)
    draw = ImageDraw.Draw(img)
    bbox = draw.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    if tw > W - 60:
        font = load_font(int(font_size * (W - 60) / max(tw, 1)))
        bbox = draw.textbbox((0, 0), text, font=font)
        tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    x = (W - tw) // 2 - bbox[0]
    y = (240 - th) // 2 - bbox[1]
    draw.text((x, y), text, fill=255, font=font)
    return np.array(img, dtype=np.float32) / 255.0


def prerender_badge(text, font_size=20):
    font = load_font(font_size)
    img = Image.new("L", (W, 44), 0)
    draw = ImageDraw.Draw(img)
    bbox = draw.textbbox((0, 0), text, font=font)
    tw = bbox[2] - bbox[0]
    draw.text(((W - tw) // 2 - bbox[0], 8 - bbox[1]), text, fill=255, font=font)
    return np.array(img, dtype=np.float32) / 255.0


def build_video():
    wav_path = os.path.join(OUT_DIR, "phonk_remix.wav")
    mp4_path = os.path.join(OUT_DIR, "phonk_remix.mp4")
    mp3_path = os.path.join(OUT_DIR, "phonk_remix.mp3")

    with open(os.path.join(SCRATCH_DIR, "remix_meta.json"), "r", encoding="utf-8") as f:
        meta = json.load(f)

    with wave.open(wav_path, "rb") as w:
        sr = w.getframerate()
        n_samples = w.getnframes()
        raw = np.frombuffer(w.readframes(n_samples), dtype=np.int16).astype(np.float32).reshape(-1, 2) / 32768.0
    mono = raw.mean(axis=1)
    dur = meta["duration"]
    total_frames = int(dur * FPS)

    # Precompute radial vignette & VHS scanlines
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    cy, cx = H / 2.0, W / 2.0
    dist_norm = np.sqrt(((xx - cx) / (W * 0.5)) ** 2 + ((yy - cy) / (H * 0.5)) ** 2)
    glow_kernel = np.exp(-2.2 * (dist_norm ** 2))
    scanline = (0.88 + 0.12 * ((yy.astype(np.int32) % 4 < 2).astype(np.float32)))[:, :, None]

    # Cache all unique lyric masks & section badges
    lyrics = meta["lyrics"]
    text_cache = {}
    for ev in lyrics:
        txt = ev["text"]
        if txt not in text_cache:
            sz = 104 if len(txt) <= 8 else (82 if len(txt) <= 12 else 62)
            text_cache[txt] = prerender_text_mask(txt, sz)

    badges = {
        "intro": prerender_badge("150 BPM  //  MEMPHIS TAPE INTRO", 21),
        "drop1": prerender_badge("150 BPM  //  DRIFT PHONK DROP", 21),
        "bridge": prerender_badge("150 BPM  //  UNDERGROUND BRIDGE", 21),
        "drop2": prerender_badge("150 BPM  //  VIP COWBELL OVERDRIVE", 21),
        "slowed": prerender_badge("119 BPM  //  SLOWED + REVERB", 21),
    }

    kicks = np.array(meta["kicks"], dtype=np.float32)

    # Export 320kbps MP3 first
    subprocess.run(
        [FFMPEG, "-y", "-i", wav_path, "-codec:a", "libmp3lame", "-b:a", "320k", mp3_path],
        check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
    )

    # Launch FFmpeg pipe for crisp 720x720 60fps H.264 (OpenH264) + 320k AAC MP4
    cmd = [
        FFMPEG, "-y",
        "-f", "rawvideo",
        "-vcodec", "rawvideo",
        "-s", f"{W}x{H}",
        "-pix_fmt", "bgr24",
        "-r", str(FPS),
        "-i", "-",
        "-i", wav_path,
        "-c:v", "libopenh264",
        "-b:v", "4500k",
        "-pix_fmt", "yuv420p",
        "-c:a", "aac",
        "-b:a", "320k",
        "-movflags", "+faststart",
        "-shortest",
        mp4_path,
    ]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)

    n_bars = 40
    bar_w = 10
    gap = 4
    total_bar_w = n_bars * (bar_w + gap)
    bar_x0 = (W - total_bar_w) // 2

    for f_idx in range(total_frames):
        t = f_idx / FPS
        s_center = int(t * sr)
        s0 = max(0, s_center - 1024)
        s1 = min(len(mono), s_center + 1024)
        win_seg = mono[s0:s1]
        if len(win_seg) < 2048:
            win_seg = np.pad(win_seg, (0, 2048 - len(win_seg)))

        # FFT spectrum for reactive bars + sub energy
        spec = np.abs(np.fft.rfft(win_seg * np.hanning(2048)))
        sub_energy = float(np.clip(np.mean(spec[1:6]) * 0.045, 0.0, 1.4))

        # Kick impact decay for camera shake + RGB chromatic aberration!
        dt_kicks = t - kicks
        past = dt_kicks[dt_kicks >= 0]
        kick_hit = float(np.exp(-np.min(past) / 0.085)) if len(past) > 0 else 0.0

        # Section color theme (BGR!)
        if t < meta["drop1_start"]:
            sec = "intro"
            col_bg = np.array([28, 28, 32], dtype=np.float32)
            col_acc = np.array([210, 210, 225], dtype=np.float32)
        elif t < meta["bridge_start"]:
            sec = "drop1"
            col_bg = np.array([22, 12, 95], dtype=np.float32)    # Deep Crimson
            col_acc = np.array([55, 35, 255], dtype=np.float32)  # Neon Phonk Red
        elif t < meta["drop2_start"]:
            sec = "bridge"
            col_bg = np.array([35, 55, 12], dtype=np.float32)    # Memphis Toxic Green
            col_acc = np.array([110, 240, 45], dtype=np.float32)
        elif t < meta["slowed_start"]:
            sec = "drop2"
            col_bg = np.array([75, 14, 125], dtype=np.float32)   # Electric Magenta/Crimson
            col_acc = np.array([140, 45, 255], dtype=np.float32)
        else:
            sec = "slowed"
            col_bg = np.array([95, 22, 58], dtype=np.float32)    # Slowed+Reverb Deep Violet
            col_acc = np.array([255, 95, 185], dtype=np.float32)

        pulse = 0.25 + 0.85 * kick_hit + 0.45 * sub_energy
        frame = (glow_kernel[:, :, None] * (col_bg * pulse))[..., :3].copy()

        # Subtle white strobe flash on Drop 1 & Drop 2 downbeat!
        for d_start in [meta["drop1_start"], meta["drop2_start"]]:
            if 0 <= t - d_start < 0.16:
                fl = (1.0 - (t - d_start) / 0.16) ** 2
                frame += fl * 220.0

        # Draw symmetric mirrored Phonk spectrum equalizer bars at bottom (y: 520..640)
        band_indices = np.geomspace(2, 320, n_bars + 1).astype(np.int32)
        half_bars = n_bars // 2
        vals = []
        for b in range(half_bars):
            i_a = band_indices[b]
            i_b = max(i_a + 1, band_indices[b + 1])
            v = float(np.mean(spec[i_a:i_b])) * (0.022 + 0.0025 * b)
            vals.append(min(1.0, v ** 0.72))
        sym_vals = vals[::-1] + vals
        base_y = 605
        for idx_b, v in enumerate(sym_vals):
            bh = max(3, int(v * 95))
            bx = bar_x0 + idx_b * (bar_w + gap)
            c_bar = (int(min(255, col_acc[0] + 60 * v)),
                     int(min(255, col_acc[1] + 60 * v)),
                     int(min(255, col_acc[2] + 60 * v)))
            frame[base_y - bh:base_y + bh // 3, bx:bx + bar_w] = c_bar

        # Top Section Badge
        b_mask = badges[sec]
        frame[38:82, :] = frame[38:82, :] * (1.0 - b_mask[:, :, None] * 0.7) + (col_acc * 0.9 + 50) * (b_mask[:, :, None] * 0.85)

        # Active Kinetic Lyric in Center (homage to original spotify_lyrics.mp4, upgraded to Drift Phonk!)
        active_ev = None
        for ev in lyrics:
            if ev["t"] <= t:
                active_ev = ev
            else:
                break

        if active_ev is not None:
            age = t - active_ev["t"]
            max_age = 1.35 if sec != "slowed" else 2.2
            if 0.0 <= age <= max_age:
                mask = text_cache[active_ev["text"]]
                fade = min(1.0, max(0.0, (max_age - age) / 0.25))
                # Punchy zoom-in scale on word onset!
                zoom = 1.0 + 0.24 * math.exp(-age / 0.075) + 0.06 * kick_hit
                mh, mw = mask.shape
                nw, nh = int(mw * zoom), int(mh * zoom)
                scaled = cv2.resize(mask, (nw, nh), interpolation=cv2.INTER_LINEAR)
                # Crop/pad back to (mh, mw)
                y_off = (nh - mh) // 2
                x_off = (nw - mw) // 2
                crop = scaled[y_off:y_off + mh, x_off:x_off + mw]

                # Chromatic aberration RGB split on bass/kick hits!
                shift = int(round(3 + 11 * kick_hit))
                y_top = (H - mh) // 2 - 20
                roi = frame[y_top:y_top + mh, :]

                # Red ghost right, Blue/Cyan ghost left, crisp white core!
                if shift > 0 and shift < W:
                    roi[:, shift:, 2] = np.maximum(roi[:, shift:, 2], crop[:, :-shift] * 245.0 * fade)
                    roi[:, :-shift, 0] = np.maximum(roi[:, :-shift, 0], crop[:, shift:] * 235.0 * fade)
                core = (crop * 255.0 * fade)[:, :, None]
                roi[:] = np.maximum(roi, core)

        # Progress bar at very bottom
        prog_w = int((t / dur) * (W - 120))
        frame[672:676, 60:W - 60] = 45
        if prog_w > 0:
            frame[671:677, 60:60 + prog_w] = col_acc

        # Subtle camera shake on big drop kicks
        if sec in ("drop1", "drop2") and kick_hit > 0.45:
            dx = int(math.sin(f_idx * 2.7) * 6 * kick_hit)
            dy = int(math.cos(f_idx * 3.9) * 5 * kick_hit)
            M = np.float32([[1, 0, dx], [0, 1, dy]])
            frame = cv2.warpAffine(frame, M, (W, H), borderMode=cv2.BORDER_REFLECT)

        # VHS scanlines & clamp
        frame *= scanline
        out_bgr = np.clip(frame, 0, 255).astype(np.uint8)
        proc.stdin.write(out_bgr.tobytes())

    proc.stdin.close()
    stderr_out = proc.stderr.read().decode("utf-8", errors="ignore")
    ret = proc.wait()
    if ret != 0:
        raise RuntimeError(f"FFmpeg failed ({ret}): {stderr_out[-600:]}")
    print(f"RENDERED MP3: {mp3_path}")
    print(f"RENDERED MP4: {mp4_path} ({total_frames} frames @ {FPS}fps)")


if __name__ == "__main__":
    build_video()
