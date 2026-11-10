"""Синтез звуков для «Таймера отдыха»: рычание, шипение, дикая природа."""
import sys
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, sosfilt

SR = 22050
rng = np.random.default_rng(7)
out_dir = sys.argv[1]


def t_axis(sec):
    return np.arange(int(SR * sec)) / SR


def bp(x, lo, hi, order=4):
    return sosfilt(butter(order, [lo, hi], btype="band", fs=SR, output="sos"), x)


def lp(x, f, order=4):
    return sosfilt(butter(order, f, btype="low", fs=SR, output="sos"), x)


def hp(x, f, order=4):
    return sosfilt(butter(order, f, btype="high", fs=SR, output="sos"), x)


def env(n, attack, release, total):
    e = np.ones(n)
    a = int(attack * SR)
    r = int(release * SR)
    e[:a] = np.linspace(0, 1, a)
    e[n - r:] = np.linspace(1, 0, r) ** 1.5
    return e


def norm(x, peak=0.85):
    x = x - np.mean(x)
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def save(name, x):
    fade = int(0.01 * SR)
    x[:fade] *= np.linspace(0, 1, fade)
    x[-fade:] *= np.linspace(1, 0, fade)
    x = np.tanh(2.5 * norm(x, 1.0))  # мягкая компрессия: громче и ровнее
    wavfile.write(f"{out_dir}/{name}.wav", SR, (norm(x) * 32767).astype(np.int16))


# ---------- Рычание: низкий хриплый рокот с «дребезгом» ----------
def growl():
    sec = 1.6
    t = t_axis(sec)
    n = len(t)
    f0 = 85 + 12 * np.sin(2 * np.pi * 1.3 * t) + 6 * lp(rng.standard_normal(n), 8) * 40
    phase = 2 * np.pi * np.cumsum(f0) / SR
    saw = sum(np.sin(k * phase) / k for k in range(1, 18))
    rattle = 0.55 + 0.45 * np.sign(np.sin(2 * np.pi * 26 * t + 0.6 * np.sin(2 * np.pi * 3 * t)))
    rattle = lp(rattle, 120, 2)
    rough = bp(rng.standard_normal(n), 120, 900) * 0.8
    voice = bp(saw * rattle + rough * rattle, 70, 1400)
    e = env(n, 0.18, 0.35, sec) * (0.75 + 0.25 * np.sin(2 * np.pi * 0.9 * t))
    return voice * e


# ---------- Шипение: кошачье «ххх-ш» с резким началом ----------
def hiss():
    sec = 1.3
    t = t_axis(sec)
    n = len(t)
    noise = rng.standard_normal(n)
    body = bp(noise, 2500, 9500, 6)
    air = hp(noise, 6000, 4) * 0.4
    spit = np.zeros(n)
    s = int(0.05 * SR)
    spit[:s] = bp(rng.standard_normal(s), 1200, 6000) * np.linspace(1.8, 0, s)
    flutter = 0.85 + 0.15 * np.sin(2 * np.pi * 11 * t)
    e = env(n, 0.03, 0.45, sec)
    e *= np.where(t < 0.25, 1.0, 1.0 - 0.25 * (t - 0.25))
    return (body + air) * e * flutter + spit


# ---------- Дикая природа: джунгли — птицы, лягушка, далёкий рык ----------
def wild():
    sec = 2.4
    t = t_axis(sec)
    n = len(t)
    mix = np.zeros(n)

    # фон: шелест листвы и стрекот насекомых
    rustle = bp(rng.standard_normal(n), 800, 4000) * 0.06
    cicada = np.sin(2 * np.pi * 4300 * t) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 38 * t)))
    cicada = bp(cicada, 3500, 5200) * 0.05
    mix += rustle + cicada

    # птичьи трели: быстрые свисты вверх-вниз
    def chirp(start, dur, f1, f2, amp):
        i0 = int(start * SR)
        m = int(dur * SR)
        tt = np.arange(m) / SR
        f = np.linspace(f1, f2, m) + 250 * np.sin(2 * np.pi * 28 * tt)
        ph = 2 * np.pi * np.cumsum(f) / SR
        e = np.sin(np.pi * np.arange(m) / m) ** 2
        mix[i0:i0 + m] += amp * np.sin(ph) * e

    for k in range(5):
        chirp(0.10 + k * 0.11, 0.08, 2600, 3900, 0.35)
    chirp(1.25, 0.30, 4200, 2200, 0.3)
    for k in range(3):
        chirp(1.70 + k * 0.13, 0.09, 3000, 4500, 0.3)

    # лягушка: низкое пульсирующее «ква»
    for start in (0.75, 1.05):
        i0 = int(start * SR)
        m = int(0.22 * SR)
        tt = np.arange(m) / SR
        tone = np.sign(np.sin(2 * np.pi * 160 * tt)) * (0.5 + 0.5 * np.sin(2 * np.pi * 45 * tt))
        mix[i0:i0 + m] += lp(tone, 900) * np.sin(np.pi * np.arange(m) / m) * 0.35

    # далёкий рык большого зверя
    i0 = int(0.55 * SR)
    m = int(1.5 * SR)
    tt = np.arange(m) / SR
    f0 = 110 - 30 * tt
    ph = 2 * np.pi * np.cumsum(f0) / SR
    roar = sum(np.sin(k * ph) / k for k in range(1, 12))
    roar = bp(roar + bp(rng.standard_normal(m), 150, 700) * 0.9, 80, 1200)
    roar *= np.sin(np.pi * np.minimum(tt / 1.5, 1)) ** 0.7
    mix[i0:i0 + m] += roar * 0.55
    return mix


save("wrong_growl", growl())
save("wrong_hiss", hiss())
save("wrong_wild", wild())
print("ok")
