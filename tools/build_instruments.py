#!/usr/bin/env python3
"""
Baut die Sample-Instrumente der App aus freien SFZ-Bibliotheken (siehe CREDITS.md).

Für jedes Instrument werden passende Aufnahmen ausgewählt (Tonabstand, Anschlagstärken,
Varianten), Schlagzeug-Mikrofone zu einem fertigen Stereo-Mix zusammengefügt, Längen
sinnvoll gekürzt, für Streicher/Flöte nahtlose Loops erzeugt, die Tonhöhe jedes Samples
nachgemessen und alles als Ogg Vorbis nach app/src/main/assets/instruments/<id>/ geschrieben:

    samples.ogg   alle Zonen hintereinander (mit kurzer Stille dazwischen)
    inst.txt      Kopfzeilen "name wert" und je Zone eine Zeile:
                  zone taste lo hi vel_lo vel_hi variante grundton pos länge loop_start loop_ende

Aufruf:  python3 tools/build_instruments.py [instrument …]
Quellen: lokale Klone unter ~/samplesrc/<Name> oder direkt von raw.githubusercontent.com
(zwischengespeichert in ~/samplesrc/cache).
"""
import collections
import json
import math
import os
import re
import subprocess
import sys

import numpy as np

import sfz

SR = 48000
HOME = os.path.expanduser("~")
LOCAL = os.path.join(HOME, "samplesrc")
CACHE = os.path.join(LOCAL, "cache")
OUT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "instruments"))
GAP = 2048  # Stille zwischen Zonen in samples.ogg
LEAD = 2048  # Vorspann mit Synchronisations-Klick (wird nie abgespielt)
SYNC = 1024  # Position des Klicks – die App gleicht damit Decoder-Versatz aus

LIBS = {
    "salamander": dict(local="SalamanderGrandPiano", repo="sfzinstruments/SalamanderGrandPiano"),
    "vsco": dict(local="VSCO", repo="sgossner/VSCO-2-CE", branch="SFZ"),
    "virtuosity": dict(repo="sfzinstruments/virtuosity_drums", cache="virtuosity_drums"),
    "epianos": dict(repo="sfzinstruments/GregSullivan.E-Pianos", cache="epianos"),
    "sneakybass": dict(repo="sfzinstruments/karoryfer.sneakybass", cache="sneakybass"),
    "bbb": dict(repo="sfzinstruments/karoryfer.black-and-blue-basses", cache="bbb"),
    "bgg": dict(repo="sfzinstruments/karoryfer.black-and-green-guitars", cache="bgg"),
    "body": dict(repo="sfzinstruments/body_percussion", cache="body"),
}


def source(lib):
    d = LIBS[lib]
    if "local" in d and os.path.isdir(os.path.join(LOCAL, d["local"])):
        return sfz.Source(local_root=os.path.join(LOCAL, d["local"]))
    return sfz.Source(repo=d["repo"], branch=d.get("branch", "master"), cache_dir=os.path.join(CACHE, d.get("cache", lib)))


# ---------------------------------------------------------------- Audio-Helfer

def decode(src, path, channels):
    data = src.fetch(path)
    p = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", "pipe:0", "-f", "f32le", "-ac", str(channels), "-ar", str(SR),
         "-af", "aresample=resampler=swr:filter_size=64:cutoff=0.97", "pipe:1"],
        input=data, capture_output=True, check=True)
    return np.frombuffer(p.stdout, dtype=np.float32).reshape(-1, channels).copy()


def db(x):
    return 10 ** (x / 20.0)


def onset(a, rel_db=-45.0, preroll_ms=2.0):
    """Index kurz vor dem ersten Sample über rel_db relativ zur Spitze."""
    env = np.abs(a).max(axis=1)
    peak = env.max()
    if peak <= 0:
        return 0
    idx = np.argmax(env > peak * db(rel_db))
    return max(0, idx - int(preroll_ms * SR / 1000))


def tail_end(a, rel_db=-60.0):
    """Ende des hörbaren Ausklangs."""
    env = np.abs(a).max(axis=1)
    peak = env.max()
    above = np.nonzero(env > peak * db(rel_db))[0]
    return int(above[-1]) + 1 if len(above) else len(a)


def trim(a, max_s, fade_s, start_rel_db=-45.0):
    a = a[onset(a, start_rel_db):]
    end = min(len(a), tail_end(a), int(max_s * SR))
    a = a[:end].copy()
    f = min(int(fade_s * SR), len(a) // 2)
    if f > 0:
        # natürlich wirkendes, exponentielles Ausblenden
        w = np.exp(-np.linspace(0, 6, f)).astype(np.float32)
        w = (w - w[-1]) / (w[0] - w[-1])
        a[-f:] *= w[:, None]
    return a


def make_loop(a, xfade_s=0.25, start_frac=0.35, min_loop_s=1.2):
    """Nahtloser Loop im Sustain-Teil: das Ende wird in den Bereich vor dem Loopstart
    übergeblendet (gleiche Leistung). Liefert (audio, loop_start, loop_end)."""
    n = len(a)
    x = int(xfade_s * SR)
    ls = int(n * start_frac)
    le = n - int(0.05 * SR)
    if le - ls < min_loop_s * SR or ls < x:
        return a, -1, -1
    out = a[:le].copy()
    t = np.linspace(0, 1, x, dtype=np.float32)
    w_in = np.sin(t * np.pi / 2)[:, None]
    w_out = np.cos(t * np.pi / 2)[:, None]
    out[le - x:le] = a[le - x:le] * w_out + a[ls - x:ls] * w_in
    return out, ls, le


def f0_midi(a, expected):
    """Klingender Grundton nahe `expected` (± eine Oktave) über die Obertonreihe:
    Für jede Oktav-Kandidatin wird geprüft, ob ihre ungeraden Harmonischen (1, 3, 5, 7)
    Energie haben – bei einem Oktavfehler fehlen genau diese. Danach Feinstimmung ±60 Cent.
    Liefert (midi, sicher?)."""
    m = a.mean(axis=1)
    s = onset(a) + int(0.06 * SR)
    seg = m[s:s + int(0.6 * SR)]
    if len(seg) < int(0.15 * SR):
        return None
    seg = (seg - seg.mean()) * np.hanning(len(seg))
    N = 1 << 19
    spec = np.abs(np.fft.rfft(seg, N))
    ref = spec.max()
    if ref <= 0:
        return None

    def mag(f):
        if f <= 20 or f >= SR * 0.45:
            return 0.0
        i = f / SR * N
        w = max(3.0, i * 0.006)
        return float(spec[int(i - w):int(i + w) + 1].max())

    def hz(midi):
        return 440.0 * 2 ** ((midi - 69) / 12)

    chosen = None
    for c in (expected - 12, expected, expected + 12):
        f = hz(c)
        odd = sum(mag(f * h) for h in (1, 3, 5, 7))
        even = sum(mag(f * h) for h in (2, 4, 6, 8))
        if odd > 0.12 * even and odd > 0.05 * ref:
            chosen = c
            break
    if chosen is None:
        return None
    # Feinstimmung: maximale gewichtete Obertonsumme im Bereich ±60 Cent
    best, best_c = -1.0, chosen
    for cents in range(-60, 61, 2):
        f = hz(chosen + cents / 100)
        sc = sum(mag(f * h) / h for h in range(1, 7))
        if sc > best:
            best, best_c = sc, chosen + cents / 100
    return best_c


# ---------------------------------------------------------------- Auswahl

def vel_layers(regs):
    return sorted({(int(r.get("lovel", 1)), int(r.get("hivel", 127))) for r in regs})


def spread(items, n):
    """n gleichmäßig verteilte Elemente aus einer sortierten Liste."""
    if n >= len(items):
        return list(items)
    if n == 1:
        return [items[len(items) * 2 // 3]]
    return [items[round(i * (len(items) - 1) / (n - 1))] for i in range(n)]


def remap_ranges(values, lo_bound, hi_bound):
    """Teilt [lo_bound, hi_bound] lückenlos zwischen sortierten Mittelpunkten auf."""
    out = []
    for i, v in enumerate(values):
        lo = lo_bound if i == 0 else (values[i - 1] + v) // 2 + 1
        hi = hi_bound if i == len(values) - 1 else (v + values[i + 1]) // 2
        out.append((lo, hi))
    return out


class Builder:
    def __init__(self, inst_id, channels, release, note=""):
        self.id = inst_id
        self.channels = channels
        self.release = release
        self.note = note
        self.zones = []  # dicts mit 'audio'
        self.norm_groups = collections.defaultdict(list)

    def add(self, audio, key, lo, hi, vlo, vhi, rr, root, loop=(-1, -1), group="all"):
        z = dict(audio=audio, key=key, lo=lo, hi=hi, vlo=vlo, vhi=vhi, rr=rr, root=root, ls=loop[0], le=loop[1], group=group)
        self.zones.append(z)
        self.norm_groups[group].append(z)

    def write(self):
        d = os.path.join(OUT, self.id)
        os.makedirs(d, exist_ok=True)
        # Normalisieren je Gruppe (Instrument bzw. Schlagzeug-Artikulation), Abstände bleiben erhalten
        for g, zs in self.norm_groups.items():
            peak = max(float(np.abs(z["audio"]).max()) for z in zs)
            k = db(-1.0) / peak if peak > 0 else 1.0
            for z in zs:
                z["audio"] = z["audio"] * k
        lead = np.zeros((LEAD, self.channels), np.float32)
        lead[SYNC, :] = 0.9
        parts, meta, pos = [lead], [], LEAD
        for z in self.zones:
            a = z["audio"].astype(np.float32)
            parts.append(a)
            parts.append(np.zeros((GAP, self.channels), np.float32))
            m = dict(k=z["key"], lo=z["lo"], hi=z["hi"], v0=z["vlo"], v1=z["vhi"], rr=z["rr"],
                     root=round(z["root"], 3), pos=pos, len=len(a))
            if z["ls"] >= 0:
                m["ls"], m["le"] = z["ls"], z["le"]
            meta.append(m)
            pos += len(a) + GAP
        allx = np.concatenate(parts)
        raw = allx.astype("<f4").tobytes()
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", str(self.channels),
                        "-i", "pipe:0", "-c:a", "libvorbis", "-q:a", "6", os.path.join(d, "samples.ogg")],
                       input=raw, check=True)
        with open(os.path.join(d, "inst.txt"), "w") as f:
            f.write(f"id {self.id}\nsampleRate {SR}\nchannels {self.channels}\nframes {len(allx)}\n"
                    f"release {self.release}\nsync {SYNC}\nnote {self.note}\n")
            for m in meta:
                f.write("zone {k} {lo} {hi} {v0} {v1} {rr} {root} {pos} {len} ".format(**m)
                        + f"{m.get('ls', -1)} {m.get('le', -1)}\n")
        old = os.path.join(d, "inst.json")
        if os.path.exists(old):
            os.remove(old)
        size = os.path.getsize(os.path.join(d, "samples.ogg"))
        print(f"  → {self.id}: {len(meta)} Zonen, {len(allx) / SR:.0f} s Audio, {size / 1e6:.2f} MB, "
              f"{len(allx) * self.channels * 2 / 1e6:.0f} MB PCM")


PITCH_LOG = []


def check_pitch(name, a, expected):
    m = f0_midi(a, expected)
    if m is None:
        return expected, "?"
    diff = m - expected
    if abs(diff) > 0.4:
        # nur melden – Korrekturen werden bewusst im Rezept gesetzt (extra_offset)
        print(f"    ! {name}: Tonhöhe weicht ab: gemessen {m:.2f}, erwartet {expected:.2f}")
        PITCH_LOG.append((name, m, expected))
        return expected, "off"
    return expected, f"{diff * 100:+.0f}c"


def melodic(inst_id, lib, sfz_path, *, filt=None, keys=None, step=3, layers=3, rr=1, max_len=None,
            fade=0.4, loop=False, channels=2, release=0.3, gain_db=0.0, note="", start_db=-45.0,
            key_bounds=(0, 127), pitch_check=True, extra_offset=0.0):
    print(f"[{inst_id}] {lib}:{sfz_path}")
    src = source(lib)
    regs = [r for r in sfz.load(src, sfz_path) if r.get("trigger", "attack") == "attack"]
    if filt:
        regs = [r for r in regs if filt(r)]
    # alles in klingender Tonhöhe (extra_offset korrigiert notierte Instrumente)
    off = int(round(extra_offset))
    by_center = collections.defaultdict(list)
    for r in regs:
        lo, hi, c = sfz.key_range(r)
        by_center[c + off].append(r)
    centers = sorted(by_center)
    if keys:
        centers = [c for c in centers if keys[0] <= c <= keys[1]]
    # Tonabstand: mindestens `step` Halbtöne
    chosen = []
    for c in centers:
        if not chosen or c - chosen[-1] >= step:
            chosen.append(c)
    key_ranges = remap_ranges(chosen, key_bounds[0], key_bounds[1])
    b = Builder(inst_id, channels, release, note)
    for c, (klo, khi) in zip(chosen, key_ranges):
        rs = by_center[c]
        lays = spread(vel_layers(rs), layers)
        vr = remap_ranges([(lo + hi) // 2 for lo, hi in lays], 1, 127)
        for (lay, (v0, v1)) in zip(lays, vr):
            cands = [r for r in rs if (int(r.get("lovel", 1)), int(r.get("hivel", 127))) == lay]
            seen, picks = set(), []
            for r in sorted(cands, key=lambda r: (int(r.get("seq_position", 1)), r["sample_path"])):
                if r["sample_path"] not in seen:
                    seen.add(r["sample_path"])
                    picks.append(r)
            for i, r in enumerate(picks[:rr]):
                a = decode(src, r["sample_path"], channels)
                a *= db(float(r.get("volume", 0)) + gain_db)
                ml = max_len(c) if callable(max_len) else (max_len or 8.0)
                a = trim(a, ml, fade, start_db)
                expected = c - float(r.get("transpose", 0)) - float(r.get("tune", 0)) / 100.0 + (extra_offset - off)
                root, q = check_pitch(os.path.basename(r["sample_path"]), a, expected) if pitch_check else (expected, "-")
                lp = (-1, -1)
                if loop:
                    a, ls, le = make_loop(a)
                    lp = (ls, le)
                b.add(a, c, klo, khi, v0, v1, i, root, lp)
    b.write()


def mic_weight(r, mics):
    parts = r["sample_path"].split("/")
    mic = parts[1] if len(parts) > 2 else ""
    return mics.get(mic, mics.get("*", 0.0))


def kit(inst_id, lib, sfz_path, arts, mics, *, channels=2, note="", release=0.08):
    """Schlagzeug: je Artikulation Anschlagstärken × Varianten, Mikrofone zu Stereo gemischt.
    arts: Liste (eigene Taste, SFZ-Taste, Namensmuster, Anzahl Layer, Anzahl Varianten, max. Länge)."""
    print(f"[{inst_id}] {lib}:{sfz_path}")
    src = source(lib)
    regs = [r for r in sfz.load(src, sfz_path) if "silence" not in r["sample_path"]
            and r.get("trigger", "attack") == "attack"]
    b = Builder(inst_id, channels, release, note)
    for (my_key, key, pattern, n_layers, n_rr, max_len) in arts:
        rs = [r for r in regs if (key is None or sfz.key_range(r)[0] <= key <= sfz.key_range(r)[1])
              and re.search(pattern, os.path.basename(r["sample_path"])) and mic_weight(r, mics) > 0]
        if not rs:
            print(f"    ! keine Regionen für {pattern}")
            continue
        lays = spread(vel_layers(rs), n_layers)
        vr = remap_ranges([(lo + hi) // 2 for lo, hi in lays], 1, 127)
        for lay, (v0, v1) in zip(lays, vr):
            lr = [r for r in rs if (int(r.get("lovel", 1)), int(r.get("hivel", 127))) == lay]
            by_rr = collections.defaultdict(list)
            for r in lr:
                rrn = int(r.get("seq_position", 0) or 0)
                m = re.search(r"_rr(\d+)", r["sample_path"]) or re.search(r"(\d+)\.(?:wav|flac)$", r["sample_path"])
                if rrn == 0 and m:
                    rrn = int(m.group(1))
                by_rr[rrn].append(r)
            for i, rrn in enumerate(sorted(by_rr)[:n_rr]):
                mix = None
                for r in by_rr[rrn]:
                    w = mic_weight(r, mics)
                    if w <= 0:
                        continue
                    a = decode(src, r["sample_path"], 2) * (w * db(float(r.get("volume", 0))))
                    if mix is None:
                        mix = a
                    else:
                        n = max(len(mix), len(a))
                        mix = np.pad(mix, ((0, n - len(mix)), (0, 0))) + np.pad(a, ((0, n - len(a)), (0, 0)))
                if mix is None:
                    continue
                mix = trim(mix, max_len, min(0.3, max_len / 3), start_rel_db=-50.0)
                b.add(mix, my_key, my_key, my_key, v0, v1, i, my_key, group=str(my_key))
        print(f"    {pattern}: {len(lays)} Layer")
    b.write()


# ---------------------------------------------------------------- Rezepte

def piano_len(c):
    return 10.0 if c < 48 else 8.0 if c < 64 else 6.0 if c < 76 else 4.5 if c < 88 else 3.0


def build_piano():
    melodic("piano", "salamander", "Salamander Grand Piano V3.sfz",
            filt=lambda r: (r.get("sw_last") or "").lower() in ("c0",) and "sample_path" in r and re.search(r"v\d+\.flac$", r["sample_path"]),
            keys=(33, 99), step=3, layers=5, rr=1, max_len=piano_len, fade=1.2, channels=2, release=0.35,
            note="Salamander Grand Piano V3 (Yamaha C5) – Alexander Holm, CC-BY 3.0")


def build_wurli():
    melodic("wurli", "epianos", "Wurlitzer EP200/Wurlitzer EP200.sfz", keys=(33, 96), step=3, layers=4, rr=1,
            max_len=lambda c: 6.0 if c < 60 else 4.0, fade=0.8, channels=1, release=0.25,
            note="Wurlitzer EP200 – Greg Sullivan, CC-BY 3.0")


def build_upright():
    melodic("upright", "sneakybass", "Programs/02-sneakybass_pluck.sfz", keys=(28, 62), step=2, layers=1, rr=2,
            filt=lambda r: "_pluck_" in r["sample_path"],
            max_len=2.8, fade=0.5, channels=1, release=0.18, extra_offset=-12,  # klingt eine Oktave tiefer als notiert
            note="Sneakybass (Kontrabass gezupft) – Karoryfer Samples, CC0")


def build_ebass():
    melodic("ebass", "bbb", "Programs/05-darkblack_pluck.sfz", keys=(28, 64), step=2, layers=3, rr=2,
            max_len=2.5, fade=0.4, channels=1, release=0.12, extra_offset=-12,  # Notation eine Oktave über Klang
            note="Black And Blue Basses (Dark Black, gezupft) – Karoryfer Samples, CC0")


def build_guitar():
    melodic("guitar", "bgg", "Programs/01-green_keyswitch.sfz",
            filt=lambda r: "/green/ord/" in r["sample_path"] and "twang" in r["sample_path"],
            keys=(40, 88), step=2, layers=2, rr=2, max_len=3.0, fade=0.6, channels=1, release=0.1,
            note="Black And Green Guitars (Green, gezupft) – Karoryfer Samples, CC0")
    melodic("guitar_mute", "bgg", "Programs/05-green_staccato.sfz", keys=(40, 64), step=2, layers=1, rr=3,
            max_len=0.6, fade=0.15, channels=1, release=0.05, pitch_check=False,
            note="Black And Green Guitars (Green, Staccato/abgedämpft) – Karoryfer Samples, CC0")


def build_strings():
    common = dict(step=3, layers=1, rr=1, max_len=9.0, fade=0.3, loop=True, channels=2, release=1.2, start_db=-40.0)
    melodic("violins", "vsco", "ViolinEnsSusVib-Quiet.sfz", keys=(55, 96), note="VSCO 2 CE Violinen-Ensemble – Versilian Studios, CC0", **common)
    melodic("violas", "vsco", "ViolaEnsSusVib-Quiet.sfz", keys=(48, 86), note="VSCO 2 CE Bratschen-Ensemble – Versilian Studios, CC0", **common)
    melodic("cellos", "vsco", "CelloEnsSusVib-Quiet.sfz", keys=(36, 77), note="VSCO 2 CE Cello-Ensemble – Versilian Studios, CC0", **common)
    melodic("contrabass", "vsco", "ContrabassSusVB-Quiet.sfz", keys=(28, 60), note="VSCO 2 CE Kontrabass – Versilian Studios, CC0", **common)


def build_mallets():
    melodic("harp", "vsco", "Harp.sfz", step=3, layers=1, rr=1, max_len=lambda c: 7.0 if c < 60 else 5.0, fade=1.0,
            channels=2, release=1.5, note="VSCO 2 CE Harfe – Versilian Studios, CC0")
    melodic("glock", "vsco", "Glockenspiel.sfz", step=3, layers=1, rr=1, max_len=4.0, fade=1.0, channels=2, release=1.0,
            extra_offset=12,  # gemessen: klingt eine Oktave höher als zugeordnet
            note="VSCO 2 CE Glockenspiel – Versilian Studios, CC0")
    melodic("marimba", "vsco", "Marimba.sfz", step=3, layers=1, rr=1, max_len=2.5, fade=0.6, channels=2, release=0.4,
            note="VSCO 2 CE Marimba – Versilian Studios, CC0")
    melodic("flute", "vsco", "FluteSusVib.sfz", step=3, layers=2, rr=1, max_len=6.0, fade=0.3, loop=True,
            channels=2, release=0.35, note="VSCO 2 CE Flöte – Versilian Studios, CC0")


STUDIO = {"kickmic": 0.9, "snaremic": 0.75, "oh": 0.8, "room": 0.45}
LOFI = {"lofi": 1.0}

KIT_ARTS = [
    # eigene Taste, SFZ-Taste, Muster, Layer, Varianten, max. Länge
    (36, 36, r"kick_snon", 4, 3, 0.9),
    (38, 38, r"_snare_center", 5, 1, 1.1),
    (40, 40, r"_snare_rimshot", 3, 1, 1.2),
    (37, 88, r"_snare_crossstick", 3, 1, 0.6),
    (42, 42, r"_hh_closed", 4, 3, 0.4),
    (43, 42, r"_hh_half", 3, 2, 0.8),
    (46, 42, r"_hh_open", 3, 2, 1.6),
    (44, 44, r"_hh_pedal", 2, 2, 0.5),
    (41, 41, r"_ltom_center", 3, 1, 1.5),
    (48, 48, r"_htom_center", 3, 1, 1.4),
    (49, 49, r"_crash_crash", 3, 2, 3.5),
    (51, 51, r"_ride_ride", 3, 2, 2.5),
    (53, 53, r"_ride_bell", 2, 2, 2.5),
]

PERC_ARTS = [
    (54, 54, r"Tamb1_Shake", 1, 4, 0.8),
    (56, 56, r"Cowbell1_Normal_v2", 1, 3, 0.8),
    (60, 60, r"BongoH_Hit1_v2", 1, 4, 0.7),
    (61, 61, r"BongoL_Hit1_v2", 1, 4, 0.8),
    (62, 62, r"Conga_17_HitHM1", 1, 4, 0.6),
    (63, 63, r"Conga_22_HitN_51_100", 1, 4, 0.9),
    (64, 64, r"Tumba_24_HitN_71_100", 1, 4, 1.0),
    (70, 70, r"LShaker_Shake1U", 1, 4, 0.5),
    (82, 82, r"LShaker_Shake1D", 1, 4, 0.5),
    (75, 75, r"Claves1_Hit_v2", 1, 3, 0.5),
    (81, 81, r"Triangle1_Hit_v1", 1, 3, 2.5),
]


def build_drums():
    kit("drums", "virtuosity", "Programs/02-full-kit.sfz", KIT_ARTS, STUDIO,
        note="Virtuosity Drums – Versilian Studios & Karoryfer Samples, CC0 (Nah-, Overhead- und Raummikrofone gemischt)")
    kit("drums_lofi", "virtuosity", "Programs/02-full-kit.sfz",
        [a for a in KIT_ARTS if a[0] in (36, 38, 37, 42, 46, 44, 51)], LOFI,
        note="Virtuosity Drums (Lo-Fi-Mikrofon) – Versilian Studios & Karoryfer Samples, CC0")
    kit("perc", "virtuosity", "Programs/02-full-kit.sfz", PERC_ARTS, {"perc": 1.0},
        note="Virtuosity Drums (Percussion) – Versilian Studios & Karoryfer Samples, CC0")


def build_claps():
    kit("claps", "body", "Programs/body.sfz",
        [(39, None, r"handclap", 2, 6, 0.8), (40, None, r"snap", 1, 4, 0.5)], {"*": 1.0},
        note="Body Percussion (Handclaps, Fingerschnipsen) – sfzinstruments, CC0")


BUILDS = {
    "piano": build_piano, "wurli": build_wurli, "upright": build_upright, "ebass": build_ebass,
    "guitar": build_guitar, "strings": build_strings, "mallets": build_mallets, "drums": build_drums,
    "claps": build_claps,
}

if __name__ == "__main__":
    names = sys.argv[1:] or list(BUILDS)
    for n in names:
        BUILDS[n]()
