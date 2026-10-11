# Generates chess-like sound effects for the NVGT chess game.
# All sounds are 44100 Hz, 16-bit, mono WAVs written to sounds/ui/.
import numpy as np
import wave
import os

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sounds", "ui")

def write(name, data, gain=0.85):
    data = np.asarray(data, dtype=np.float64)
    peak = np.max(np.abs(data))
    if peak > 0:
        data = data / peak * gain
    # 5ms fade-out to avoid clicks
    n = min(int(SR * 0.005), len(data))
    if n > 1:
        data[-n:] *= np.linspace(1, 0, n)
    pcm = (data * 32767).astype(np.int16)
    path = os.path.join(OUT, name)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    print("%-14s %6d bytes" % (name, os.path.getsize(path)))

def t(dur):
    return np.arange(int(SR * dur)) / SR

def wood_clack(dur=0.14, f0=1400.0, body=320.0, decay=0.045, noise_amt=0.9, body_amt=0.55):
    """A wooden chess piece set down on a wooden board."""
    tt = t(dur)
    # resonant modes of the piece/board, each exponentially decaying
    sig = noise_amt * np.random.RandomState(7).randn(len(tt)) * np.exp(-tt / (decay * 0.25))
    modes = [(f0, 1.0), (f0 * 1.63, 0.6), (f0 * 2.4, 0.35), (f0 * 3.3, 0.2)]
    for f, a in modes:
        sig += a * np.sin(2 * np.pi * f * tt) * np.exp(-tt / decay)
    # low body thump of the board
    sig += body_amt * np.sin(2 * np.pi * body * tt) * np.exp(-tt / (decay * 1.6))
    return sig

def tone(freq, dur, harm=(1.0, 0.4, 0.2, 0.1), decay=None, attack=0.005):
    tt = t(dur)
    sig = np.zeros(len(tt))
    for i, a in enumerate(harm):
        sig += a * np.sin(2 * np.pi * freq * (i + 1) * tt)
    env = np.minimum(1.0, tt / attack)
    d = decay if decay else dur * 0.45
    env *= np.exp(-tt / d)
    return sig * env

# --- menu click: crisp short tick ---
click = np.sin(2 * np.pi * 2000 * t(0.05)) * np.exp(-t(0.05) / 0.012)
click += 0.4 * np.sin(2 * np.pi * 950 * t(0.05)) * np.exp(-t(0.05) / 0.02)
write("click.wav", click)

# --- select: light pickup tick (piece lifted off the board) ---
sel_t = t(0.06)
sel = np.random.RandomState(3).randn(len(sel_t)) * np.exp(-sel_t / 0.006)
sel += 0.7 * np.sin(2 * np.pi * 2600 * sel_t) * np.exp(-sel_t / 0.015)
write("select.wav", sel)

# --- move: a piece placed on the board ---
write("move.wav", wood_clack(dur=0.16, f0=1350, body=300, decay=0.05))

# --- capture: harder, two-part clack ---
cap = np.concatenate([
    wood_clack(dur=0.12, f0=1700, body=250, decay=0.035, noise_amt=1.1),
    np.concatenate([np.zeros(int(SR * 0.055)), wood_clack(dur=0.2, f0=950, body=180, decay=0.07, noise_amt=0.8, body_amt=0.8)])[:int(SR * 0.2)],
])
write("capture.wav", cap)

# --- castle: two quick clacks (king then rook) ---
c1 = wood_clack(dur=0.1, f0=1300, body=300, decay=0.04)
c2 = wood_clack(dur=0.14, f0=1100, body=260, decay=0.05)
gap = np.zeros(int(SR * 0.07))
castle = np.concatenate([c1, gap, c2])
write("castle.wav", castle)

# --- promote: rising magical chime ---
prom = tone(523.25, 0.7, harm=(1.0, 0.3, 0.15)) * 0.7
for i, f in enumerate([659.25, 783.99, 1046.5]):
    delay = int(SR * 0.13 * (i + 1))
    note = tone(f, 0.7 - 0.13 * (i + 1), harm=(1.0, 0.5, 0.25), decay=0.35)
    prom[delay:delay + len(note)] += note[:len(prom) - delay]
write("promote.wav", prom)

# --- check: urgent double alert ---
chk = np.concatenate([
    tone(932.33, 0.11, harm=(1.0, 0.6, 0.3), decay=0.05),
    np.zeros(int(SR * 0.03)),
    tone(1108.73, 0.14, harm=(1.0, 0.6, 0.3), decay=0.06),
])
write("check.wav", chk)

# --- checkmate / game start handled below ---
start = tone(523.25, 0.5, harm=(1.0, 0.2, 0.1)) * 0.6
for i, f in enumerate([659.25, 783.99]):
    delay = int(SR * 0.11 * (i + 1))
    note = tone(f, 0.5 - 0.11 * (i + 1), harm=(1.0, 0.35, 0.15), decay=0.28)
    start[delay:delay + len(note)] += note[:len(start) - delay]
write("start.wav", start)

# --- win: bright ascending fanfare ---
win = np.zeros(int(SR * 1.2))
for i, f in enumerate([523.25, 659.25, 783.99, 1046.5]):
    delay = int(SR * 0.14 * i)
    dur = 0.5 if i < 3 else 0.7
    note = tone(f, dur, harm=(1.0, 0.5, 0.3, 0.15), decay=0.3 if i < 3 else 0.5)
    win[delay:delay + len(note)] += note[:len(win) - delay] * (0.7 + 0.1 * i)
write("win.wav", win)

# --- lose: descending minor, soft ---
lose = np.zeros(int(SR * 1.1))
for i, f in enumerate([392.0, 311.13, 261.63]):
    delay = int(SR * 0.22 * i)
    note = tone(f, 0.8 - 0.2 * i, harm=(1.0, 0.25, 0.1), decay=0.45)
    lose[delay:delay + len(note)] += note[:len(lose) - delay]
write("lose.wav", lose)

# --- draw: neutral two-note ---
dr = np.concatenate([
    tone(440.0, 0.28, harm=(1.0, 0.3, 0.15), decay=0.18),
    np.zeros(int(SR * 0.04)),
    tone(440.0, 0.35, harm=(1.0, 0.3, 0.15), decay=0.25),
])
write("draw.wav", dr)

# --- error: soft low thunk (not harsh) ---
err_t = t(0.22)
err = np.sin(2 * np.pi * 160 * err_t) * np.exp(-err_t / 0.06)
err += 0.35 * np.sin(2 * np.pi * 110 * err_t) * np.exp(-err_t / 0.09)
err += 0.15 * np.random.RandomState(11).randn(len(err_t)) * np.exp(-err_t / 0.02)
write("error.wav", err)

# --- flag: clock tick warning (low time) ---
flag_t = t(0.08)
flag = np.sin(2 * np.pi * 1000 * flag_t) * np.exp(-flag_t / 0.02)
write("flag.wav", flag)

print("done ->", OUT)
