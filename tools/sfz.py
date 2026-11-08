"""
Minimaler SFZ-Leser: lädt eine .sfz-Datei samt aller #include-Dateien (lokal oder von
raw.githubusercontent.com), ersetzt #define-Variablen und liefert die Regionen mit allen
geerbten Opcodes (control → global → master → group → region).

Nur für den Instrumentenbau (tools/build_instruments.py) – läuft nicht in der App.
"""
import os
import re
import urllib.parse
import urllib.request

RAW = "https://raw.githubusercontent.com"


class Source:
    """Datei-Quelle: entweder ein lokales Verzeichnis oder ein GitHub-Repository (raw)."""

    def __init__(self, local_root=None, repo=None, branch="master", cache_dir=None):
        self.local_root = local_root
        self.repo = repo
        self.branch = branch
        self.cache_dir = cache_dir

    def read_text(self, path):
        return self.fetch(path).decode("utf-8", errors="replace")

    def fetch(self, path):
        path = os.path.normpath(path).replace("\\", "/")
        if self.local_root:
            with open(os.path.join(self.local_root, path), "rb") as f:
                return f.read()
        cached = os.path.join(self.cache_dir, path) if self.cache_dir else None
        if cached and os.path.exists(cached):
            with open(cached, "rb") as f:
                return f.read()
        url = f"{RAW}/{self.repo}/{self.branch}/" + urllib.parse.quote(path)
        with urllib.request.urlopen(url, timeout=120) as r:
            data = r.read()
        if cached:
            os.makedirs(os.path.dirname(cached), exist_ok=True)
            with open(cached, "wb") as f:
                f.write(data)
        return data


_include_re = re.compile(r'#include\s+"([^"]+)"')
_define_re = re.compile(r"#define\s+(\$\w+)\s+(\S+)")
_header_re = re.compile(r"<(\w+)>")
_opcode_re = re.compile(r"(\w+)=")


def _strip_comments(text):
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def _subst(text, defines):
    for k in sorted(defines, key=len, reverse=True):
        if k in text:
            text = text.replace(k, defines[k])
    return text


def _expand(src, path, base_dir, defines, depth=0):
    """Liest die Datei, ersetzt Defines und fügt Includes (relativ zu base_dir) ein –
    auch mehrere Includes mitten in einer Zeile, in Textreihenfolge."""
    if depth > 20:
        raise RuntimeError("Include-Tiefe zu groß: " + path)
    text = _strip_comments(src.read_text(path))
    out = []
    for line in text.split("\n"):
        pieces = []
        for part in re.split(r'(#include\s+"[^"]+")', line):
            m = _include_re.fullmatch(part.strip()) if part.strip().startswith("#include") else None
            if m:
                inc = _subst(m.group(1), defines)
                pieces.append(_expand(src, os.path.join(base_dir, inc), base_dir, defines, depth + 1))
                continue
            while True:
                d = _define_re.search(part)
                if not d:
                    break
                defines[d.group(1)] = d.group(2)
                part = part[:d.start()] + part[d.end():]
            pieces.append(_subst(part, defines))
        out.append(" ".join(pieces))
    return "\n".join(out)


def _tokens(text):
    """Zerlegt in Header und (opcode, wert). Werte dürfen Leerzeichen enthalten (sample=...)."""
    items = []
    for part in re.split(r"(<\w+>)", text):
        h = _header_re.fullmatch(part.strip()) if part.strip().startswith("<") else None
        if h:
            items.append(("header", h.group(1)))
            continue
        matches = list(_opcode_re.finditer(part))
        for i, m in enumerate(matches):
            end = matches[i + 1].start() if i + 1 < len(matches) else len(part)
            value = part[m.end():end].strip()
            items.append(("op", m.group(1), value))
    return items


def load(src, sfz_path):
    """Liefert eine Liste von Regionen (dicts mit allen geerbten Opcodes).
    Der Sample-Pfad steht unter 'sample_path' relativ zur Quelle."""
    base_dir = os.path.dirname(sfz_path)
    text = _expand(src, sfz_path, base_dir, {})
    control, glob, master, group, region = {}, {}, {}, {}, None
    regions = []
    level = None

    def close_region():
        nonlocal region
        if region is not None:
            r = {}
            r.update(glob)
            r.update(master)
            r.update(group)
            r.update(region)
            if "sample" in r:
                default_path = control.get("default_path", "")
                p = os.path.normpath(os.path.join(base_dir, default_path, r["sample"].replace("\\", "/")))
                r["sample_path"] = p.replace("\\", "/")
                regions.append(r)
            region = None

    for t in _tokens(text):
        if t[0] == "header":
            close_region()
            h = t[1]
            level = h
            if h == "control":
                control = {}
            elif h == "global":
                glob, master, group = {}, {}, {}
            elif h == "master":
                master, group = {}, {}
            elif h == "group":
                group = {}
            elif h == "region":
                region = {}
        else:
            _, k, v = t
            target = {"control": control, "global": glob, "master": master, "group": group,
                      "region": region}.get(level)
            if target is not None:
                target[k] = v
    close_region()
    return regions


NOTE_NAMES = {"c": 0, "d": 2, "e": 4, "f": 5, "g": 7, "a": 9, "b": 11}


def note_num(v):
    """'60', 'c4', 'f#3', 'eb2' → MIDI-Nummer (c4 = 60)."""
    v = str(v).strip().lower()
    if re.fullmatch(r"-?\d+", v):
        return int(v)
    m = re.fullmatch(r"([a-g])([#b]?)(-?\d+)", v)
    if not m:
        raise ValueError("Unbekannte Note: " + v)
    n = NOTE_NAMES[m.group(1)] + (1 if m.group(2) == "#" else -1 if m.group(2) == "b" else 0)
    return n + 12 * (int(m.group(3)) + 1)


def key_range(r):
    if "key" in r:
        k = note_num(r["key"])
        return k, k, note_num(r.get("pitch_keycenter", k))
    lo = note_num(r.get("lokey", 0))
    hi = note_num(r.get("hikey", 127))
    center = note_num(r.get("pitch_keycenter", lo))
    return lo, hi, center
