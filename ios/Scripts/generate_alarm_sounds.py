#!/usr/bin/env python3
"""Generates the 5 alarm melody WAV files used as iOS notification sounds.

Reimplements the exact sine+envelope synthesis algorithm from the Android
`AlarmTonePlayer`/`AlarmSounds` (44.1kHz mono 16-bit PCM, 5ms fade in/out,
0.85 amplitude), then loops each melody pattern to fill ~28s (iOS notification
sounds are truncated to the system default if longer than 30s).

Run once: python3 generate_alarm_sounds.py
Output: ../CalarSaat/Resources/Sounds/melody_*.wav
"""
import math
import struct
import wave
import os

SAMPLE_RATE = 44100
AMPLITUDE = 0.85 * 32767
FADE_SECONDS = 0.005  # 5ms, matches Android's anti-click envelope
TARGET_DURATION_SECONDS = 28

# (frequencyHz, durationMs) per tone; frequencyHz <= 0 means silence.
MELODIES = {
    "melody_classic": {
        "tones": [(1000, 150), (0, 80), (1000, 150), (0, 80), (750, 220)],
        "tail_ms": 350,
    },
    "melody_digital": {
        "tones": [(1300, 90), (0, 90), (1300, 90), (0, 90), (1300, 90), (0, 90), (1300, 90)],
        "tail_ms": 400,
    },
    "melody_triplet": {
        "tones": [(800, 130), (0, 60), (900, 130), (0, 60), (1050, 130)],
        "tail_ms": 450,
    },
    "melody_rising": {
        "tones": [(600, 90), (750, 90), (900, 90), (1050, 90), (1200, 110)],
        "tail_ms": 400,
    },
    "melody_siren": {
        "tones": [(1100, 300), (750, 300), (1100, 300), (750, 300)],
        "tail_ms": 250,
    },
}


def synthesize_tone(freq_hz, duration_ms):
    frames = int(duration_ms * SAMPLE_RATE / 1000)
    fade_frames = max(1, int(FADE_SECONDS * SAMPLE_RATE))
    samples = []
    if freq_hz <= 0:
        return [0] * frames
    angular = 2 * math.pi * freq_hz / SAMPLE_RATE
    for i in range(frames):
        if i < fade_frames:
            fade = i / fade_frames
        elif i >= frames - fade_frames:
            fade = (frames - i) / fade_frames
        else:
            fade = 1.0
        value = math.sin(angular * i) * AMPLITUDE * fade
        samples.append(int(max(-32768, min(32767, value))))
    return samples


def synthesize_pass(tones, tail_ms):
    samples = []
    for freq_hz, duration_ms in tones:
        samples.extend(synthesize_tone(freq_hz, duration_ms))
    samples.extend([0] * int(tail_ms * SAMPLE_RATE / 1000))
    return samples


def write_wav(path, samples):
    with wave.open(path, "w") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(SAMPLE_RATE)
        f.writeframes(struct.pack("<%dh" % len(samples), *samples))


def main():
    out_dir = os.path.join(os.path.dirname(__file__), "..", "CalarSaat", "Resources", "Sounds")
    os.makedirs(out_dir, exist_ok=True)
    for name, melody in MELODIES.items():
        pass_samples = synthesize_pass(melody["tones"], melody["tail_ms"])
        pass_duration_s = len(pass_samples) / SAMPLE_RATE
        reps = max(1, int(TARGET_DURATION_SECONDS / pass_duration_s))
        full = pass_samples * reps
        out_path = os.path.join(out_dir, f"{name}.wav")
        write_wav(out_path, full)
        print(f"{name}: {len(full) / SAMPLE_RATE:.2f}s ({reps} reps) -> {out_path}")


if __name__ == "__main__":
    main()
