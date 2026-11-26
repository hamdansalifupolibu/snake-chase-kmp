"""Synthesizes Snake Chase's own UI sounds (star chimes, unlock arpeggio, button pop) as WAV.

They are made from code (no samples), so they are original to the game. Run:
python tools/generate_sounds.py
"""
import math
import random
import struct
import wave
from pathlib import Path

SR = 44100
OUT = Path(__file__).resolve().parent.parent / "composeApp/src/commonMain/composeResources/files/audio"


def bell(freq, dur, amp=0.5, decay=4.0, partials=((1, 1.0), (2.0, 0.42), (3.01, 0.18), (4.2, 0.08)), attack=0.004):
    """A struck-bell tone: a few harmonic partials, quick attack, exponential decay."""
    n = int(SR * dur)
    out = []
    for i in range(n):
        t = i / SR
        env = min(1.0, t / attack) * math.exp(-decay * t)
        s = sum(a * math.sin(2 * math.pi * freq * m * t) * math.exp(-decay * (m - 1) * 0.6 * t) for m, a in partials)
        out.append(amp * env * s)
    return out


def mix(*tracks):
    n = max(off + len(tr) for off, tr in tracks)
    out = [0.0] * n
    for off, tr in tracks:
        for i, v in enumerate(tr):
            out[off + i] += v
    return out


def at(seconds):
    return int(SR * seconds)


def sparkle(start, count=7, base=2093.0, step=0.045, amp=0.11):
    """A little run of high, quick glints (the 'shine' on a perfect result)."""
    rnd = random.Random(7)
    notes = [base * 2 ** (k / 12) for k in (0, 4, 7, 12, 16, 19, 24)]
    return [(at(start + k * step), bell(notes[k % len(notes)] * (1 + rnd.uniform(-0.004, 0.004)), 0.35, amp, decay=9.0))
            for k in range(count)]


def write(name, samples, gain=0.9):
    peak = max(1e-9, max(abs(s) for s in samples))
    scale = gain / peak
    with wave.open(str(OUT / name), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s * scale)) * 32767)) for s in samples))
    print("wrote", name, round(len(samples) / SR, 2), "s")


C5, E5, G5, C6, E6, G6 = 523.25, 659.25, 783.99, 1046.5, 1318.5, 1568.0

# Victory stars: each star stamps with the next note of a rising C-major chord.
write("star_1.wav", mix((0, bell(C6, 0.9, decay=3.6)), (0, bell(C5, 0.9, 0.18, decay=3.0))))
write("star_2.wav", mix((0, bell(E6, 0.9, decay=3.6)), (0, bell(E5, 0.9, 0.18, decay=3.0))))
# The third star closes the chord and adds a sparkle: the sound of a perfect run.
write("star_3.wav", mix((0, bell(G6, 1.4, decay=2.6)), (0, bell(G5, 1.4, 0.22, decay=2.2)), (0, bell(C6, 1.4, 0.16, decay=2.4)),
                        *sparkle(0.12)))

# Skin / orb unlocked: a soft, warm rising arpeggio with a gentle shimmer on top.
write("unlock.wav", mix(
    (at(0.00), bell(C5, 1.6, 0.30, decay=2.2)),
    (at(0.09), bell(E5, 1.5, 0.28, decay=2.2)),
    (at(0.18), bell(G5, 1.4, 0.28, decay=2.2)),
    (at(0.30), bell(C6, 1.3, 0.30, decay=2.0)),
    *sparkle(0.42, count=5, amp=0.07),
), gain=0.7)

# Button press: a short rising "bubble pop".
pop = []
for i in range(at(0.06)):
    t = i / SR
    f = 520 + 1400 * (t / 0.06)
    pop.append(math.sin(2 * math.pi * f * t) * min(1, t / 0.002) * math.exp(-55 * t))
write("ui_pop.wav", pop, gain=0.6)
