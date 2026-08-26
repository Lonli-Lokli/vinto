#!/usr/bin/env python3
"""The table's four sounds, synthesized.

Placeholder audio, deliberately: recording foley needs a studio and licensing a pack needs a
decision, and neither should block the sound *layer* — the plumbing, the setting, the hook
points — from existing. These are quiet, short, and inoffensive; swap the files under
`composeApp/src/commonMain/composeResources/files/sfx/` for real ones whenever they exist,
and nothing else changes.

Deterministic (seeded), so regenerating produces byte-identical files and the repo never
churns. Mono, 22.05 kHz, 16-bit PCM; every file lands well under 15 KB.

Run from the repo root:  python3 kmp/tools/make-sfx.py
"""
import math
import os
import random
import struct
import wave

RATE = 22050
OUT = os.path.join(
    os.path.dirname(__file__),
    "..", "composeApp", "src", "commonMain", "composeResources", "files", "sfx",
)


def write(name, samples):
    path = os.path.join(OUT, name)
    os.makedirs(OUT, exist_ok=True)
    with wave.open(path, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(RATE)
        frames = b"".join(
            struct.pack("<h", max(-32767, min(32767, int(s * 32767)))) for s in samples
        )
        f.writeframes(frames)
    print(f"{name}: {len(samples) / RATE * 1000:.0f} ms, {os.path.getsize(path)} bytes")


def env(i, n, attack=0.008, curve=5.0):
    """A fast attack and an exponential decay — the shape of every struck object."""
    t = i / RATE
    a = min(1.0, t / attack) if attack > 0 else 1.0
    return a * math.exp(-curve * (i / n) * (n / RATE) / (n / RATE) * (i / n) * curve)


def decay(i, n, curve=6.0):
    return math.exp(-curve * i / n)


def deal():
    """A card flicked off the deck: a short burst of filtered noise, falling in pitch."""
    rng = random.Random(11)
    n = int(0.09 * RATE)
    out, prev = [], 0.0
    for i in range(n):
        # One-pole lowpass over white noise, the cutoff sweeping down: 'fffp'.
        alpha = 0.35 - 0.28 * (i / n)
        prev = prev + alpha * (rng.uniform(-1, 1) - prev)
        out.append(prev * decay(i, n, 5.5) * 0.6)
    return out


def land():
    """A card landing: a soft, low tap — mostly thump, a little texture."""
    rng = random.Random(22)
    n = int(0.07 * RATE)
    out, prev = [], 0.0
    for i in range(n):
        t = i / RATE
        thump = math.sin(2 * math.pi * (170 - 60 * i / n) * t)
        prev = prev + 0.12 * (rng.uniform(-1, 1) - prev)
        out.append((0.8 * thump + 0.2 * prev) * decay(i, n, 7.0) * 0.5)
    return out


def thud():
    """A penalty: lower, longer, unmistakably a cost."""
    n = int(0.16 * RATE)
    out = []
    for i in range(n):
        t = i / RATE
        fundamental = math.sin(2 * math.pi * (95 - 25 * i / n) * t)
        overtone = 0.3 * math.sin(2 * math.pi * 190 * t)
        out.append((fundamental + overtone) * decay(i, n, 5.0) * 0.55)
    return out


def chime():
    """The round ending: two soft notes a fifth apart, the second a beat behind."""
    n = int(0.55 * RATE)
    out = []
    second_start = int(0.16 * RATE)
    for i in range(n):
        t = i / RATE
        s = math.sin(2 * math.pi * 523.25 * t) * decay(i, n, 4.0) * 0.28
        if i >= second_start:
            j = i - second_start
            tj = j / RATE
            s += math.sin(2 * math.pi * 784.0 * tj) * decay(j, n - second_start, 4.0) * 0.24
        out.append(s)
    return out


if __name__ == "__main__":
    write("deal.wav", deal())
    write("land.wav", land())
    write("thud.wav", thud())
    write("chime.wav", chime())
