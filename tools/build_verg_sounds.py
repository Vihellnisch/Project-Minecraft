"""Vergilius's sounds and the rebuilt Claw's roar, synthesized (no game audio): numpy + soundfile, written as OGG Vorbis.
Usage: python tools/build_verg_sounds.py"""
import os
import numpy as np
import soundfile as sf

SR = 44100
OUT = 'src/main/resources/assets/projectminecraft/sounds'
rng = np.random.default_rng(945)


def t(d):
    return np.arange(int(SR * d)) / SR


def noise(d):
    return rng.uniform(-1, 1, int(SR * d))


def band(x, lo, hi):
    f = np.fft.rfft(x)
    fr = np.fft.rfftfreq(len(x), 1 / SR)
    f[(fr < lo) | (fr > hi)] = 0
    return np.fft.irfft(f, len(x))


def echo(x, delays, gains):
    out = np.concatenate([x, np.zeros(int(SR * (max(delays) + 0.1)))])
    for d, g in zip(delays, gains):
        k = int(SR * d)
        out[k:k + len(x)] += x * g
    return out


def write(name, x, gain=0.9):
    x = x / (np.max(np.abs(x)) + 1e-9) * gain
    path = os.path.join(OUT, name + '.ogg')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sf.write(path, x.astype(np.float32), SR, format='OGG', subtype='VORBIS')
    print('wrote', path, round(len(x) / SR, 2), 's')


# a gladius cut: a fast swish, a hot metal ring, a sizzle tail
d = 0.7; tt = t(d)
swish = band(noise(d), 1500, 9000) * np.exp(-((tt - 0.06) / 0.05) ** 2) * 1.4
ring = (np.sin(2 * np.pi * 2350 * tt) * 0.4 + np.sin(2 * np.pi * 3480 * tt) * 0.25) * np.exp(-7 * tt) * (tt > 0.05)
sizzle = band(noise(d), 4000, 12000) * np.exp(-4 * tt) * 0.35 * (tt > 0.08)
write('vergilius/slash', echo(swish + ring + sizzle, [0.05], [0.25]))

# the blade's sizzle: a hiss with crackles
d = 0.9; tt = t(d)
hiss = band(noise(d), 3500, 12000) * np.minimum(1, tt * 20) * np.exp(-2.5 * tt)
crack = np.zeros_like(tt)
for c in rng.uniform(0, 0.7, 14):
    k = int(c * SR); crack[k:k + 200] += rng.uniform(-1, 1, len(crack[k:k + 200])) * np.exp(-np.arange(len(crack[k:k + 200])) / 40)
write('vergilius/sizzle', hiss + crack * 0.6, 0.8)

# a blood spike: a low thump, a wet burst, a short rising scrape
d = 0.6; tt = t(d)
thump = np.sin(2 * np.pi * (90 - 50 * tt) * tt) * np.exp(-12 * tt)
wet = band(noise(d), 300, 2500) * np.exp(-18 * tt) * 0.8
scrape = band(noise(d), 2000, 6000) * np.exp(-((tt - 0.05) / 0.04) ** 2) * 0.6
write('vergilius/spike', thump + wet + scrape)

# the lake: a deep swell, bubbling, a dark drone under it
d = 2.4; tt = t(d)
swell = np.sin(2 * np.pi * (45 + 20 * tt) * tt) * np.minimum(1, tt * 1.5) * np.exp(-0.9 * tt)
drone = (np.sin(2 * np.pi * 110 * tt) + 0.5 * np.sin(2 * np.pi * 116.5 * tt)) * 0.25 * np.minimum(1, tt * 2) * np.exp(-0.8 * tt)
bub = np.zeros_like(tt)
for c in rng.uniform(0.1, 2.0, 40):
    k = int(c * SR); n = int(SR * 0.04); f = rng.uniform(180, 600)
    if k + n < len(bub): bub[k:k + n] += np.sin(2 * np.pi * f * (1 + 2 * np.arange(n) / n) * np.arange(n) / SR) * np.exp(-np.arange(n) / (n / 4)) * 0.4
write('vergilius/lake', echo(swell + drone + bub, [0.18, 0.4], [0.35, 0.2]))

# the barrier breaking: glass and a dark tone
d = 1.2; tt = t(d)
glass = sum(np.sin(2 * np.pi * f * tt) * np.exp(-a * tt) * g for f, a, g in [(2637, 4, 0.3), (3520, 6, 0.3), (4699, 8, 0.25), (1976, 3, 0.2)])
burst = noise(d) * np.exp(-30 * tt) * 0.9
dark = np.sin(2 * np.pi * 70 * tt) * np.exp(-3 * tt) * 0.5
write('vergilius/barrier', echo(band(glass + burst, 300, 12000) + dark, [0.09, 0.21], [0.3, 0.15]))

# the bus's horn, far off: a two-tone chord, swelling and fading, a long echo
d = 1.8; tt = t(d)
env = np.minimum(1, tt * 8) * np.where(tt > 1.1, np.exp(-(tt - 1.1) * 5), 1)
def saw(f): return 2 * ((f * tt) % 1) - 1
horn = (saw(311.1) + saw(370.0) + 0.6 * saw(466.2)) * env
horn = band(horn, 150, 2600)
write('vergilius/bus_horn', echo(horn, [0.35, 0.75, 1.2], [0.4, 0.25, 0.12]), 0.85)

# the engine pulling away: a low chug speeding up
d = 2.2; tt = t(d)
rate = 6 + 14 * tt
phase = np.cumsum(rate) / SR
chug = (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * phase))) * band(noise(d), 40, 400) * 1.6
rumble = np.sin(2 * np.pi * (48 + 30 * tt) * tt) * 0.6
write('vergilius/bus_engine', (chug + rumble) * np.minimum(1, tt * 4) * np.where(tt > 1.6, np.exp(-(tt - 1.6) * 4), 1))

# the rebuilt Claw's roar: a distorted growl sliding down, a metal screech over it
d = 1.6; tt = t(d)
growl = np.tanh(3 * (np.sin(2 * np.pi * (140 - 60 * tt) * tt) + 0.6 * band(noise(d), 60, 600) * 3)) * np.minimum(1, tt * 6) * np.exp(-1.2 * tt)
screech = np.sin(2 * np.pi * (2400 - 900 * tt) * tt) * 0.25 * np.exp(-2 * tt) * (1 + 0.5 * np.sin(2 * np.pi * 37 * tt))
write('claw/roar', echo(growl + screech, [0.12, 0.3], [0.35, 0.2]))
