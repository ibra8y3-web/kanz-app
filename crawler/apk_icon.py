"""Pull a launcher icon out of an APK without downloading all of it (HTTP range reads of the zip),
falling back to aapt2 on a full download only for small APKs whose resource names are obfuscated."""
import io, os, re, subprocess, shutil, tempfile, zipfile, urllib.request, urllib.error
try:
    from PIL import Image
except Exception:
    Image = None

UA = "KanzCatalogBot/1 (+https://github.com/ibra8y3-web/kanz-catalog)"
DENS = {"xxxhdpi": 0, "xxhdpi": 1, "xhdpi": 2, "hdpi": 3, "mdpi": 4, "ldpi": 6, "nodpi": 5, "anydpi": 9}

class RangeFile(io.RawIOBase):
    BLOCK = 128 * 1024
    def __init__(self, url, max_bytes=40 * 1024 * 1024):
        self.url = url; self.pos = 0; self.cache = {}; self.fetched = 0; self.max_bytes = max_bytes
        r = self._get("bytes=0-0")
        cr = r[1].get("Content-Range", "")
        m = re.search(r"/(\d+)$", cr)
        if not m: raise IOError("no range support")
        self.size = int(m.group(1))
    def _get(self, rng):
        req = urllib.request.Request(self.url, headers={"User-Agent": UA, "Range": rng})
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, dict(r.headers), r.read()
    def seekable(self): return True
    def readable(self): return True
    def tell(self): return self.pos
    def seek(self, off, whence=0):
        self.pos = off if whence == 0 else self.pos + off if whence == 1 else self.size + off
        self.pos = max(0, min(self.pos, self.size)); return self.pos
    def _block(self, i):
        if i not in self.cache:
            if self.fetched > self.max_bytes: raise IOError("range budget exceeded")
            a = i * self.BLOCK; b = min(self.size, a + self.BLOCK) - 1
            st, hd, body = self._get("bytes=%d-%d" % (a, b)); self.fetched += len(body); self.cache[i] = body
        return self.cache[i]
    def readinto(self, buf):
        n = len(buf); out = bytearray()
        while len(out) < n and self.pos < self.size:
            i, o = divmod(self.pos, self.BLOCK); blk = self._block(i)[o:o + (n - len(out))]
            if not blk: break
            out += blk; self.pos += len(blk)
        buf[:len(out)] = out; return len(out)

def _dens(p):
    for k, v in DENS.items():
        if "-" + k in p: return v
    return 7

def candidates(names):
    """zip entry names -> ordered list of ('full'|'fg'|'bg', path)"""
    res = [n for n in names if n.lower().endswith((".png", ".webp")) and n.startswith("res/")]
    full, fg, bg = [], [], []
    for n in res:
        b = n.rsplit("/", 1)[-1].lower(); stem = b.rsplit(".", 1)[0]
        if re.search(r"(monochrome|notification|notif|splash|banner|adaptive_icon_round)", stem): continue
        if "foreground" in stem and re.search(r"(launcher|icon|logo|app)", stem + n.lower()): fg.append(n); continue
        if "background" in stem and re.search(r"(launcher|icon|logo|app)", stem): bg.append(n); continue
        m = re.match(r"(ic_launcher|ic_launcher_round|icon|app_icon|ic_app|ic_logo|logo|launcher|ic_icon|appicon|ic_launcher_\w+)$", stem)
        if m and not stem.endswith(("_foreground", "_background")):
            full.append((_dens(n) + (20 if "round" in stem else 0) + (10 if stem not in ("ic_launcher", "icon", "app_icon") else 0), n))
    full.sort()
    return [p for _, p in full], sorted(fg, key=_dens), sorted(bg, key=_dens)

def _norm(im):
    im = im.convert("RGBA")
    if min(im.size) < 32: return None
    return im

def _tile(im):
    """128px square webp-ready tile; a mostly-white glyph goes on a dark tile"""
    im = im.copy(); im.thumbnail((128, 128), Image.LANCZOS)
    sq = Image.new("RGBA", (128, 128), (0, 0, 0, 0)); sq.paste(im, ((128 - im.size[0]) // 2, (128 - im.size[1]) // 2))
    if sq.getchannel("A").getextrema()[1] < 40: return None
    px = [q for q in sq.getdata() if q[3] > 40]
    if px and sum(1 for q in px if min(q[:3]) > 235) / len(px) > 0.8:
        t = Image.new("RGBA", (128, 128), (30, 27, 75, 255)); t.alpha_composite(sq); sq = t
    return sq

def _adaptive(fgim, bgim):
    S = 432; fg = fgim.resize((S, S), Image.LANCZOS)
    if bgim is not None: base = bgim.resize((S, S), Image.LANCZOS)
    else:
        px = [q for q in fg.resize((48, 48)).getdata() if q[3] > 40]
        lum = sum(0.3 * q[0] + 0.59 * q[1] + 0.11 * q[2] for q in px) / len(px) if px else 0
        base = Image.new("RGBA", (S, S), (30, 27, 75, 255) if lum > 170 else (240, 240, 240, 255))
    base.alpha_composite(fg)
    inner = base.crop((S // 6, S // 6, S - S // 6, S - S // 6))   # safe zone of the 108dp canvas
    mask = Image.new("L", inner.size, 0)
    from PIL import ImageDraw
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, inner.size[0] - 1, inner.size[1] - 1), radius=inner.size[0] // 5, fill=255)
    out = Image.new("RGBA", inner.size, (0, 0, 0, 0)); out.paste(inner, (0, 0), mask); return out

def _from_zip(zf):
    names = zf.namelist(); full, fg, bg = candidates(names)
    for p in full[:3]:
        try:
            im = _norm(Image.open(io.BytesIO(zf.read(p))))
            if im is not None and im.getchannel("A").getextrema()[1] > 40: return _tile(im)
        except Exception: continue
    for p in fg[:2]:
        try:
            f = _norm(Image.open(io.BytesIO(zf.read(p)))); b = None
            if f is None: continue
            if bg:
                try: b = Image.open(io.BytesIO(zf.read(bg[0]))).convert("RGBA")
                except Exception: b = None
            r = _adaptive(f, b)
            if r is not None: return _tile(r)
        except Exception: continue
    return None


# ---------- vector / adaptive icons through aapt2 (needs the whole APK on disk) ----------
def _run(a, *args):
    return subprocess.run([a] + list(args), capture_output=True, text=True, timeout=180).stdout

def _xmltree(a, apk, path):
    root = []; stack = []
    for ln in _run(a, "dump", "xmltree", "--file", path, apk).splitlines():
        m = re.match(r"^(\s*)E: ([\w\-:.]+)", ln)
        if m:
            d = len(m.group(1)); node = {"tag": m.group(2).split(":")[-1], "at": {}, "ch": []}
            while stack and stack[-1][0] >= d: stack.pop()
            (stack[-1][1]["ch"] if stack else root).append(node); stack.append((d, node)); continue
        m = re.match(r"^\s*A: (?:\S+:)?([\w\-]+)(?:\(0x[0-9a-f]+\))?=(.*)$", ln)
        if m and stack:
            v = m.group(2); 
            if v.startswith('"'):
                j = v.find('" (Raw:'); v = v[1:j] if j > 0 else v.strip('"')
            else: v = re.sub(r" \(Raw:.*$", "", v)
            stack[-1][1]["at"][m.group(1)] = v.strip()
    return root

def _resources(a, apk):
    res = {}; cur = None
    for ln in _run(a, "dump", "resources", apk).splitlines():
        m = re.match(r"^\s+resource (0x[0-9a-f]+) (\S+)", ln)
        if m: cur = res.setdefault(m.group(1), {"name": m.group(2), "vals": []}); continue
        m = re.match(r"^\s+\(([^)]*)\) (.*)$", ln)
        if m and cur is not None: cur["vals"].append((m.group(1), m.group(2).strip()))
    return res

def _res_value(res, ref):
    r = res.get(ref)
    if not r or not r["vals"]: return None
    best = sorted(r["vals"], key=lambda v: _dens("-" + v[0]) if v[0] else 8)[0][1]
    m = re.match(r"\(file\) (\S+)", best)
    if m: return ("file", m.group(1))
    if best.startswith("#"): return ("color", best)
    return None

def _col(res, v):
    if v is None: return None, 1.0
    if v.startswith("@0x"):
        r = _res_value(res, v[1:]); v = r[1] if r and r[0] == "color" else None
    if not v or not v.startswith("#"): return None, 1.0
    h = v[1:]
    if len(h) == 3: h = "".join(c * 2 for c in h)
    if len(h) == 4: h = "".join(c * 2 for c in h)
    if len(h) == 6: return "#" + h, 1.0
    if len(h) == 8: return "#" + h[2:], int(h[:2], 16) / 255.0
    return None, 1.0

def _f(v, d=0.0):
    try: return float(re.sub(r"[a-z]+$", "", v))
    except Exception: return d

def _svg(a, apk, res, root):
    v = next((n for n in root if n["tag"] == "vector"), None)
    if v is None: return None
    vw = _f(v["at"].get("viewportWidth", "24"), 24) or 24; vh = _f(v["at"].get("viewportHeight", "24"), 24) or 24
    out = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %g %g" width="432" height="432">' % (vw, vh)]
    def walk(n):
        for c in n["ch"]:
            at = c["at"]
            if c["tag"] == "group":
                tr = "translate(%g %g) translate(%g %g) rotate(%g) scale(%g %g) translate(%g %g)" % (_f(at.get("translateX", "0")), _f(at.get("translateY", "0")), _f(at.get("pivotX", "0")), _f(at.get("pivotY", "0")), _f(at.get("rotation", "0")), _f(at.get("scaleX", "1"), 1), _f(at.get("scaleY", "1"), 1), -_f(at.get("pivotX", "0")), -_f(at.get("pivotY", "0")))
                out.append('<g transform="%s">' % tr); walk(c); out.append("</g>")
            elif c["tag"] == "path" and at.get("pathData"):
                fc, fa = _col(res, at.get("fillColor")); sc, sa = _col(res, at.get("strokeColor"))
                fa *= _f(at.get("fillAlpha", "1"), 1); sa *= _f(at.get("strokeAlpha", "1"), 1)
                if fc is None and sc is None: continue
                out.append('<path d="%s" fill="%s" fill-opacity="%g" fill-rule="%s" stroke="%s" stroke-opacity="%g" stroke-width="%g" stroke-linecap="%s" stroke-linejoin="%s"/>' % (
                    at["pathData"].replace('"', ""), fc or "none", fa, "evenodd" if at.get("fillType", "").lower() in ("evenodd", "1") else "nonzero", sc or "none", sa, _f(at.get("strokeWidth", "0")), at.get("strokeLineCap", "butt").replace("0", "butt").replace("1", "round").replace("2", "square"), at.get("strokeLineJoin", "miter").replace("0", "miter").replace("1", "round").replace("2", "bevel")))
    walk(v); out.append("</svg>"); return "".join(out)

def _render_ref(a, apk, zf, res, ref, depth=0):
    """a drawable reference (@0x...) or a file path -> RGBA 432px image, or a solid color tuple"""
    if isinstance(ref, str) and ref.startswith("@0x"):
        r = _res_value(res, ref[1:])
        if r is None: return None
        if r[0] == "color":
            c, al = _col(res, r[1]); 
            if c is None: return None
            return Image.new("RGBA", (432, 432), tuple(int(c[i:i + 2], 16) for i in (1, 3, 5)) + (int(al * 255),))
        path = r[1]
    else: path = ref
    pl = path.lower()
    if pl.endswith((".png", ".webp")):
        im = Image.open(io.BytesIO(zf.read(path))).convert("RGBA"); return im.resize((432, 432), Image.LANCZOS)
    if pl.endswith(".xml") and depth < 3:
        tree = _xmltree(a, apk, path)
        if any(n["tag"] == "vector" for n in tree):
            import cairosvg
            svg = _svg(a, apk, res, tree)
            if not svg: return None
            return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg.encode("utf8"), output_width=432, output_height=432))).convert("RGBA")
        ad = next((n for n in tree if n["tag"] == "adaptive-icon"), None)
        if ad is not None:
            fg = next((c for c in ad["ch"] if c["tag"] == "foreground"), None); bg = next((c for c in ad["ch"] if c["tag"] == "background"), None)
            f = _render_ref(a, apk, zf, res, fg["at"].get("drawable"), depth + 1) if fg and fg["at"].get("drawable") else None
            b = _render_ref(a, apk, zf, res, bg["at"].get("drawable"), depth + 1) if bg and bg["at"].get("drawable") else None
            if f is None: return None
            return _adaptive(f, b)
        li = next((n for n in tree if n["tag"] in ("layer-list", "inset", "bitmap")), None)
        if li is not None and depth < 3:
            it = next((c for c in li["ch"] if c["at"].get("drawable")), None)
            if it: return _render_ref(a, apk, zf, res, it["at"]["drawable"], depth + 1)
            if li["at"].get("drawable"): return _render_ref(a, apk, zf, res, li["at"]["drawable"], depth + 1)
    return None

def _aapt2_path():
    if shutil.which("aapt2"): return shutil.which("aapt2")
    root = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""
    for d in sorted(os.listdir(os.path.join(root, "build-tools")), reverse=True) if root and os.path.isdir(os.path.join(root, "build-tools")) else []:
        p = os.path.join(root, "build-tools", d, "aapt2")
        if os.path.exists(p): return p
    return None

def _from_full(url, max_mb):
    a = _aapt2_path()
    if not a: return None, 0
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=120) as r:
        n = int(r.headers.get("Content-Length") or 0)
        if n > max_mb * 1024 * 1024: return None, 0
        data = r.read()
    with tempfile.NamedTemporaryFile(suffix=".apk") as tf:
        tf.write(data); tf.flush()
        out = _run(a, "dump", "badging", tf.name)
        paths = re.findall(r"application-icon-(\d+):'([^']+)'", out)
        m = re.search(r"icon='([^']+)'", out)
        if m: paths.append(("0", m.group(1)))
        zf = zipfile.ZipFile(tf.name); res = None
        for dpi, p in sorted(set(paths), key=lambda x: -int(x[0])):
            try:
                if p.lower().endswith((".png", ".webp")):
                    im = _norm(Image.open(io.BytesIO(zf.read(p))))
                else:
                    res = res or _resources(a, tf.name); im = _render_ref(a, tf.name, zf, res, p)
                if im is not None:
                    t = _tile(im)
                    if t is not None: return t, len(data)
            except Exception: continue
        return _from_zip(zf), len(data)

def extract(url, full_fallback_mb=30):
    """returns (PIL image 128px | None, bytes_downloaded, how)"""
    if Image is None: return None, 0, "no-pillow"
    fetched = 0
    try:
        rf = RangeFile(url); zf = zipfile.ZipFile(rf); im = _from_zip(zf); fetched = rf.fetched
        if im is not None: return im, fetched, "range"
        if rf.size <= full_fallback_mb * 1024 * 1024:
            im, n = _from_full(url, full_fallback_mb)
            return im, fetched + n, "aapt2" if im else "none"
        return None, fetched, "none-large"
    except Exception as e:
        return None, fetched, "error:%s" % str(e)[:60]
