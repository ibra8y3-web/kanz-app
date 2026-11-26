"""Static site generator: one page per app (live catalog + baked F-Droid-style catalog), browse index, names.json for shared lists,
shared-list page, sitemap.xml. Slug = lowercase id with non-alphanumerics as '-', plus first 6 hex of sha1(id). The Android app computes the same slug."""
import os, re, json, hashlib, html, urllib.parse, datetime, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "vendor"))
try:
    import segno
except Exception:
    segno = None

_QC = {}
def qr_matrix(text):
    if segno is None: return None
    if text in _QC: return _QC[text]
    try: m = [[1 if c else 0 for c in r] for r in segno.make(text, error="l", micro=False).matrix]
    except Exception: m = None
    if len(_QC) > 64: _QC.clear()
    _QC[text] = m; return m

def qr_svg(text):
    """Offline QR as a compact inline SVG (pure-python segno, no network). Empty string if unavailable."""
    m = qr_matrix(text)
    if not m: return ""
    b = 2; n = len(m); d = []
    for y, row in enumerate(m):
        x = 0
        while x < n:
            if row[x]:
                x0 = x
                while x < n and row[x]: x += 1
                d.append("M%d %dh%dv1h-%dz" % (x0 + b, y + b, x - x0, x - x0))
            else: x += 1
    w = n + 2 * b
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" width="180" height="180" shape-rendering="crispEdges" role="img" aria-label="QR"><rect width="%d" height="%d" fill="#fff"/><path d="%s"/></svg>' % (w, w, w, w, "".join(d))

def qr_png(text):
    """Tiny 1-bit PNG, one pixel per module with a 2-module quiet zone (the app scales it up without smoothing)."""
    import zlib, struct
    m = qr_matrix(text)
    if not m: return None
    b = 2; n = len(m); w = n + 2 * b; raw = bytearray()
    for y in range(w):
        bits = [1] * w if (y < b or y >= n + b) else [1] * b + [0 if v else 1 for v in m[y - b]] + [1] * b
        row = bytearray((w + 7) // 8)
        for i, v in enumerate(bits):
            if v: row[i >> 3] |= 0x80 >> (i & 7)
        raw += b"\x00" + row
    def ch(t, d): c = struct.pack(">I", len(d)) + t + d; return c + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", w, w, 1, 0, 0, 0, 0)) + ch(b"IDAT", zlib.compress(bytes(raw), 9)) + ch(b"IEND", b"")

_HOSTS = {"io.github", "com.github", "io.gitlab", "com.gitlab", "page.codeberg", "org.codeberg", "me.github", "dev.github"}
_NOGROUP = {"com.android", "com.google", "android", "org.fdroid", "com.example"}
def dev_key(aid):
    """Owner of the repo for GitHub-style ids, otherwise the package namespace. Must match devKey() in the Android app."""
    if "/" in aid: return aid.split("/")[0].lower()
    p = aid.lower().split(".")
    if len(p) >= 4 and ".".join(p[:2]) in _HOSTS: return ".".join(p[:3])
    if len(p) >= 3 and ".".join(p[:2]) not in _NOGROUP and ".".join(p[:2]) not in _HOSTS: return ".".join(p[:2])
    return aid.lower()

def _write_bytes(path, data):
    try:
        if open(path, "rb").read() == data: return 0
    except Exception: pass
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(data); return 1

SITE = "https://ibra8y3-web.github.io/kanz-catalog/"
SELF_ID = "ibra8y3-web/kanz-catalog"
HERE = os.path.dirname(os.path.abspath(__file__))
BASES = ["https://f-droid.org/repo/", "https://apt.izzysoft.de/fdroid/repo/", "https://f-droid.org/archive/", "https://guardianproject.info/fdroid/repo/",
         "https://calyxos.gitlab.io/calyx-fdroid-repo/fdroid/repo/", "https://repo.microg.org/fdroid/repo/", "https://molly.im/fdroid/foss/fdroid/repo/",
         "https://mobileapp.bitwarden.com/fdroid/repo/", "https://cdn.kde.org/android/stable-releases/fdroid/repo/", "https://cdn.kde.org/android/nightly/fdroid/repo/"]

def slug(aid):
    s = re.sub(r"[^a-z0-9]+", "-", aid.lower()).strip("-")
    x = hashlib.sha1(aid.encode("utf-8")).hexdigest()[:6]
    return (s + "-" + x) if s else x + "0000"

def _mb(n):
    try: n = int(n)
    except Exception: return ""
    if n <= 0: return ""
    return "%.1f MB" % (n / 1048576.0) if n >= 1048576 else "%d KB" % max(1, n // 1024)

def _best(a):
    vs = a.get("v") or []
    for v in vs:
        if not v[7] and not v[8]: return v
    return vs[0] if vs else None

def live_items(apps, icon_file, out):
    for a in apps:
        if a.get("arch") == 1 or a.get("dup"): continue
        v = _best(a)
        if not v: continue
        ic = ("../../icons/" + icon_file(a["id"])) if a.get("ic") == 1 else "../../icon.png"
        yield {"id": a["id"], "name": a.get("n") or a["id"].split("/")[-1], "desc": (a.get("d") or "").strip(), "ver": v[0], "size": v[3], "sha": v[4],
               "url": v[5], "lic": a.get("l") or "", "icon": ic, "src": a.get("u") or SITE, "date": (v[1] or "")[:10], "self": a["id"] == SELF_ID,
               "cat": a.get("c") or "", "live": True, "vers": a.get("v") or [], "stars": a.get("s", 0)}

def baked_items(out):
    f = os.path.join(HERE, "baked.tsv")
    if not os.path.exists(f): return
    try: cats = [l.strip() for l in open(os.path.join(HERE, "cats.txt"), encoding="utf-8")]
    except Exception: cats = []
    for ln in open(f, encoding="utf-8"):
        p = ln.rstrip("\n").split("\t")
        if len(p) < 13: continue
        pk, name, sm, cat, lic, apk, sha, size, vn, upd, icon, repo, desc = p[:13]
        try: ri = int(repo)
        except Exception: ri = 0
        base = BASES[ri] if 0 <= ri < len(BASES) else BASES[0]
        local = os.path.join(out, "icons", "fd_" + re.sub(r"[^A-Za-z0-9._-]", "_", pk) + ".webp")
        ic = (base + icon.lstrip("/")) if icon else ("../../icons/" + os.path.basename(local) if os.path.exists(local) else "../../icon.png")
        try: d = datetime.datetime.utcfromtimestamp(int(upd)).strftime("%Y-%m-%d")
        except Exception: d = ""
        try: ci = int(cat); cn = cats[ci] if 0 <= ci < len(cats) else ""
        except Exception: cn = ""
        yield {"id": pk, "name": name, "desc": (desc or sm).strip(), "ver": vn, "size": size, "sha": sha, "url": base + apk, "lic": lic, "icon": ic,
               "src": "https://f-droid.org/packages/" + pk + "/" if ri == 0 else SITE, "date": d, "self": False, "cat": cn, "live": False, "vers": []}

CSS = ("body{font-family:system-ui,-apple-system,'Segoe UI',Tahoma,sans-serif;margin:0;background:#f6f6fb;color:#1c1b29}"
       ".w{max-width:640px;margin:0 auto;padding:16px}.top{background:linear-gradient(135deg,#2b1f8f,#7b3fe4);color:#fff;padding:14px 16px}"
       ".top a{color:#fff;text-decoration:none;font-weight:bold}.card{background:#fff;border-radius:16px;padding:16px;margin:14px 0;box-shadow:0 1px 4px #0001}"
       ".h{display:flex;gap:14px;align-items:center}.h img{width:84px;height:84px;border-radius:18px;background:#eee}h1{font-size:22px;margin:0 0 4px}"
       ".s{color:#666;font-size:14px}.g{display:flex;gap:8px;text-align:center}.g div{flex:1;background:#f1effc;border-radius:12px;padding:10px 4px}.g b{display:block}"
       ".b{display:block;text-align:center;padding:14px;border-radius:14px;font-weight:bold;text-decoration:none;margin-top:10px}"
       ".p{background:#5b3fd4;color:#fff}.o{background:#fff;color:#5b3fd4;border:2px solid #5b3fd4}.sh{display:flex;gap:8px;overflow-x:auto}.sh img{height:240px;border-radius:8px;border:1px solid #e6e6f0}.qr{display:flex;justify-content:center;padding:8px}.qr svg{border:1px solid #e6e6f0;border-radius:8px;max-width:100%}.n{font-size:12px;color:#777;margin-top:10px}"
       ".v bdi{white-space:nowrap}.v{display:inline-block;background:#dcfce7;color:#166534;border-radius:20px;padding:5px 12px;font-size:13px;margin-top:12px}"
       "code{font-size:11px;word-break:break-all}ul{padding:0 18px}a.l{color:#5b3fd4}.b small{font-weight:normal;opacity:.85}"
       ".bk a{display:inline-block;margin:4px;padding:8px 14px;background:#f1effc;border-radius:12px;text-decoration:none;color:#5b3fd4}")

def render(n):
    sl = slug(n["id"]); name = n["name"]; desc = n["desc"]; url = SITE + "a/" + sl + "/"
    short = re.sub(r"\s+", " ", desc)[:155]; e = html.escape
    ld = {"@context": "https://schema.org", "@type": "SoftwareApplication", "name": name, "operatingSystem": "Android", "applicationCategory": "MobileApplication",
          "softwareVersion": n["ver"], "description": short, "url": url, "downloadUrl": n["url"], "offers": {"@type": "Offer", "price": "0", "priceCurrency": "USD"}}
    lic = n["lic"]; known = lic and lic not in ("UNSPECIFIED", "FOSS")
    badge = ""
    if known: badge = '<div class="v">✓ ترخيص مفتوح (<bdi dir="ltr">%s</bdi>)%s%s</div>' % (e(lic), " · بصمة SHA-256" if n["live"] else "", ((" · %s: <bdi dir=\"ltr\">%s</bdi>" % ("آخر إصدار" if n["live"] else "آخر تحديث", e(n["date"])))) if n["date"] else "")
    elif n["self"]: badge = ""
    else: badge = '<div class="v" style="background:#fef3c7;color:#92400e">ℹ الترخيص غير معلن</div>'
    note = "" if n["self"] or not n["live"] else '<p class="n">متفحّص آليًا (ترخيص مفتوح + بصمة SHA-256) لكن مش مراجَع يدويًا.<br><span dir="ltr">Checked automatically (open license + SHA-256), not manually reviewed.</span></p>'
    sha = ('<p class="n"><bdi dir="ltr">SHA-256: <code>%s</code></bdi></p>' % e(n["sha"])) if n["sha"] else ""
    shots = "".join('<img src="../../shots/%s/%s" alt="" loading="lazy">' % (e(slug(n["id"])), e(f)) for f in n.get("shots") or [])
    shotbox = ('<div class="card"><div class="sh">%s</div></div>' % shots) if shots else ""
    dv = n.get("dev"); devbox = ('<div class="card"><p class="s"><a class="l" href="../../d/%s/">تطبيقات تانية لنفس المطوّر (%d)</a><br><span dir="ltr">More apps from this developer: <bdi>%s</bdi></span></p></div>' % (e(dv[1]), dv[2], e(dv[0]))) if dv else ""
    cat = ('<div class="s">%s</div>' % e(n["cat"])) if n["cat"] else ""
    return ('<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
            '<title>%s - تحميل APK | كنز</title><meta name="description" content="%s"><link rel="canonical" href="%s">'
            '<meta property="og:title" content="%s | كنز"><meta property="og:description" content="%s"><meta property="og:type" content="website">'
            '<meta property="og:url" content="%s"><meta property="og:image" content="%sicon.png">'
            '<script type="application/ld+json">%s</script><link rel="stylesheet" href="../style.css"></head><body>'
            '<div class="top"><a href="../../">كنز | Kanz</a></div><div class="w"><div class="card"><div class="h"><img src="%s" alt="">'
            '<div><h1>%s</h1>%s</div></div><p class="s">%s</p>%s'
            '<div class="g" style="margin-top:14px"><div><b><bdi dir="ltr">%s</bdi></b>الإصدار</div><div><b><bdi dir="ltr">%s</bdi></b>الحجم</div><div><b><bdi dir="ltr">%s</bdi></b>الرخصة</div></div>'
            '<a class="b o" href="kanz://app?id=%s">افتح في كنز<br><small dir="ltr">Open in Kanz</small></a>'
            '<a class="b p" href="%s" rel="nofollow">تحميل APK (<bdi dir="ltr">%s</bdi>)<br><small dir="ltr">Download APK</small></a>%s%s</div>'
            '%s%s<div class="card" style="text-align:center"><div class="qr">%s</div><p class="n">امسح الكود لفتح صفحة التطبيق<br><span dir="ltr">Scan to open this app page</span></p></div><div class="card"><p class="s">لو كنز مش متثبت عندك: <a class="l" href="../../kanz.apk">نزّل متجر كنز</a>.<br><span dir="ltr">Not installed? <a class="l" href="../../kanz.apk">Get the Kanz store</a>.</span></p>'
            '<p class="s"><a class="l" href="%s">المصدر | Source</a> · <a class="l" href="../">كل التطبيقات</a></p></div></div></body></html>') % (
        e(name), e(short), e(url), e(name), e(short), e(url), SITE, json.dumps(ld, ensure_ascii=False).replace("</", "<\\/"),
        e(n["icon"]), e(name), cat, e(desc[:300]), badge, e(n["ver"]), e(_mb(n["size"])), e(lic), urllib.parse.quote(n["id"], safe=""),
        e(n["url"]), e(_mb(n["size"])), sha, note, shotbox, devbox, qr_svg(url), e(n["src"]))

def _write(path, text):
    try:
        if open(path, encoding="utf-8").read() == text: return 0
    except Exception: pass
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w", encoding="utf-8").write(text); return 1

def _bucket(name):
    c = (name or "?")[:1].lower()
    return c if re.match(r"[a-z]", c) else ("0" if c.isdigit() else "other")

LIST_HTML = ('<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
             '<title>قائمة تطبيقات | كنز</title><link rel="stylesheet" href="../a/style.css"></head><body><div class="top"><a href="../">كنز | Kanz</a></div>'
             '<div class="w"><div class="card"><h1 id="t">قائمة تطبيقات</h1><ul id="u"></ul><p id="m" class="s">جاري التحميل…</p>'
             '<a class="b o" id="k" href="#">افتح القائمة في كنز<br><small dir="ltr">Open the list in Kanz</small></a>'
             '<a class="b p" href="../kanz.apk">نزّل متجر كنز<br><small dir="ltr">Get the Kanz store</small></a></div></div>'
             '<script>(function(){var h=location.hash.replace(/^#/,""),q={};h.split("&").forEach(function(x){var i=x.indexOf("=");if(i>0)q[x.slice(0,i)]=decodeURIComponent(x.slice(i+1))});'
             'var ids=(q.ids||"").split(",").filter(function(s){return /^[a-z0-9-]+$/.test(s)}).slice(0,100);var n=q.n||"قائمة تطبيقات";'
             'document.getElementById("t").textContent=n;document.title=n+" | كنز";'
             'document.getElementById("k").href="kanz://list?ids="+encodeURIComponent(ids.join(","))+"&n="+encodeURIComponent(n);'
             'var m=document.getElementById("m");if(!ids.length){m.textContent="القائمة فاضية.";return}'
             'fetch("../names.json").then(function(r){return r.json()}).then(function(N){var u=document.getElementById("u");m.textContent="";'
             'ids.forEach(function(s){var li=document.createElement("li"),a=document.createElement("a");a.className="l";a.href="../a/"+s+"/";a.textContent=N[s]||s;li.appendChild(a);u.appendChild(li)})})'
             '.catch(function(){m.textContent="تعذر تحميل الأسماء."})})();</script></body></html>')

API_VERSION = 1
def _api_app(n, sl, gen):
    """Public JSON record for one app. Full record (all known versions) lives in api/v1/apps/<slug>.json."""
    vers = [{"version": v[0], "date": (v[1] or "")[:10], "file": v[2], "size": v[3], "sha256": v[4], "url": v[5],
             "prerelease": bool(v[7]), "removed_upstream": bool(v[8])} for v in n.get("vers") or [] if len(v) >= 9]
    if not vers and n.get("url"):
        vers = [{"version": n["ver"], "date": n["date"], "file": n["url"].rsplit("/", 1)[-1], "size": int(n["size"] or 0), "sha256": n["sha"], "url": n["url"], "prerelease": False, "removed_upstream": False}]
    dv = n.get("dev")
    return {"id": n["id"], "slug": sl, "name": n["name"], "description": n["desc"][:600], "category": n["cat"], "license": n["lic"] or None,
            "latest_version": n["ver"], "latest_size": int(n["size"] or 0), "latest_sha256": n["sha"] or None, "updated": n["date"] or None,
            "kind": "live" if n["live"] else "baked", "page": SITE + "a/" + sl + "/", "qr_png": SITE + "a/" + sl + "/qr.png",
            "apk": n["url"], "source_url": n["src"] if n["src"] != SITE else None, "developer_group": dv[0] if dv else None, "screenshots": [SITE + "shots/" + sl + "/" + f for f in n.get("shots") or []], "versions": vers, "generated": gen}

SCHEMA = {"$schema": "http://json-schema.org/draft-07/schema#", "title": "Kanz app record", "type": "object",
          "required": ["id", "slug", "name", "latest_version", "apk", "page"],
          "properties": {"id": {"type": "string", "description": "owner/repo for live apps, Android package name for baked ones"}, "slug": {"type": "string"},
                         "name": {"type": "string"}, "description": {"type": "string"}, "category": {"type": "string"}, "license": {"type": ["string", "null"], "description": "SPDX id, null if not declared"},
                         "latest_version": {"type": "string"}, "latest_size": {"type": "integer", "description": "bytes"}, "latest_sha256": {"type": ["string", "null"]},
                         "updated": {"type": ["string", "null"], "description": "YYYY-MM-DD of latest release"}, "kind": {"enum": ["live", "baked"], "description": "live = checked by the crawler every run, baked = from a static snapshot"},
                         "page": {"type": "string"}, "qr_png": {"type": "string"}, "apk": {"type": "string"}, "source_url": {"type": ["string", "null"]}, "developer_group": {"type": ["string", "null"]}, "screenshots": {"type": "array", "items": {"type": "string"}, "description": "small webp copies, F-Droid apps only, may be empty"},
                         "versions": {"type": "array", "items": {"type": "object", "properties": {"version": {"type": "string"}, "date": {"type": "string"}, "file": {"type": "string"}, "size": {"type": "integer"},
                                      "sha256": {"type": "string"}, "url": {"type": "string"}, "prerelease": {"type": "boolean"}, "removed_upstream": {"type": "boolean"}}}},
                         "generated": {"type": "string"}}}

def _api_files(out, api, gen):
    w = 0
    slim = [{k: a[k] for k in ("id", "slug", "name", "category", "license", "latest_version", "latest_size", "latest_sha256", "updated", "kind", "page", "apk")} for a in api]
    slim.sort(key=lambda a: a["name"].lower())
    idx = {"api_version": API_VERSION, "generated": gen, "count": len(slim), "app_url_template": SITE + "api/v1/apps/{slug}.json", "apps": slim}
    w += _write(os.path.join(out, "api", "v1", "index.json"), json.dumps(idx, ensure_ascii=False, separators=(",", ":")))
    w += _write(os.path.join(out, "api", "v1", "schema.json"), json.dumps(SCHEMA, ensure_ascii=False, indent=1))
    w += _write(os.path.join(out, "api", "index.html"), '<!doctype html><meta charset="utf-8"><meta http-equiv="refresh" content="0;url=../developers/"><a href="../developers/">developers</a>')
    w += _write(os.path.join(out, "developers", "index.html"), DEV_HTML.replace("@SITE@", SITE).replace("@COUNT@", str(len(slim))))
    return w

DEV_HTML = """<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>واجهة المطورين | Kanz Developer API</title><link rel="stylesheet" href="../a/style.css">
<style>pre{direction:ltr;text-align:left;background:#1c1b29;color:#e8e6ff;padding:12px;border-radius:10px;overflow:auto;font-size:12px}code{direction:ltr}.en{direction:ltr;text-align:left}</style></head><body>
<div class="top"><a href="../">كنز | Kanz</a></div><div class="w">
<div class="card"><h1>واجهة المطورين (API مفتوحة)</h1>
<p class="s">بيانات المتجر كلها متاحة كملفات JSON ثابتة، قراءة بس، <b>من غير مفتاح ولا تسجيل</b>. أي برنامج يقدر يسحبها ويدمجها. الملفات بتتحدّث مع كل تشغيل للمحرك (حوالي كل 30 دقيقة). مفيش حد رسمي للاستخدام، بس اطلب بحساب (cache) وما تعملش طلبات كتير ورا بعض: الاستضافة على GitHub Pages.</p>
<ul>
<li><a class="l" href="../api/v1/index.json">api/v1/index.json</a>: فهرس كل التطبيقات (@COUNT@) بحقول مختصرة.</li>
<li><code>api/v1/apps/&lt;slug&gt;.json</code>: سجل كامل لتطبيق، بكل النسخ المعروفة والبصمات.</li>
<li><a class="l" href="../api/v1/schema.json">api/v1/schema.json</a>: وصف الحقول (JSON Schema).</li>
<li><a class="l" href="../sitemap.xml">sitemap.xml</a> · <a class="l" href="../app.json">app.json</a> (نسخة تطبيق كنز).</li>
</ul>
<p class="s">الروابط ثابتة (v1). لو الشكل اتغيّر بشكل يكسر التوافق هيبقى v2 ومعاه v1 شغّال لفترة. دعم CORS: GitHub Pages بيرجّع Access-Control-Allow-Origin: * للملفات الثابتة، فالمتصفح يقدر يقرأها مباشرة.</p>
<pre>curl -s @SITE@api/v1/index.json | jq '.apps[0]'
curl -s @SITE@api/v1/apps/&lt;slug&gt;.json | jq '.versions[0]'</pre>
<p class="s">الـslug = اسم التطبيق بحروف صغيرة وشرطات + أول 6 حروف من SHA-1 للمعرّف (موجود في كل سجل في الفهرس).</p>
<p class="s">الترخيص والبصمة: كل تطبيق عنده حقل license (معرّف SPDX أو null لو غير معلن) وsha256 للنسخ. التطبيقات من نوع live بتتفحص آليًا في كل تشغيل؛ baked من لقطة ثابتة وبياناتها قد تكون أقدم.</p>
<h2>إضافة بيانات ودمجها</h2>
<ul><li>لإضافة تطبيق: افتح Issue بنموذج «إضافة تطبيق إلى كنز» في المستودع، والمحرك بيفحصه (ترخيص مفتوح + APK + SHA-256) ويضيفه تلقائيًا. مستودعات GitHub بس.</li>
<li>للدمج في نظامك: اسحب <code>index.json</code> دوريًا وقارن <code>latest_version</code> وupdated. ملفات data/ و docs/ في المستودع بتتولد من المحرك وأي تعديل يدوي عليها بيتكتب فوقه، فالمساهمة بتتم عن طريق نموذج الإضافة أو بفتح Issue/PR على كود المحرك (crawler/).</li></ul></div>
<div class="card en"><h1>Developer API (open, read-only)</h1>
<p class="s">The whole catalog is published as static JSON. <b>No API key or sign-up is needed</b> for read access: the store is open source, so there is nothing to protect. Files refresh on every crawler run (about every 30 minutes). There is no official rate limit, but cache responses and be gentle: it is hosted on GitHub Pages.</p>
<ul><li><code>GET api/v1/index.json</code>: slim index of all @COUNT@ apps.</li><li><code>GET api/v1/apps/{slug}.json</code>: full record with every known version and SHA-256.</li><li><code>GET api/v1/schema.json</code>: JSON Schema.</li></ul>
<p class="s">URLs are stable under /v1/. Breaking changes will ship as /v2/. GitHub Pages serves static files with permissive CORS headers, so browsers can read them directly. Each record has <code>kind</code>: <code>live</code> (checked by the crawler each run) or <code>baked</code> (static snapshot, may be older). <code>license</code> is an SPDX id or null if undeclared.</p>
<p class="s">To add an app, open an issue with the "Submit an app to Kanz" form (GitHub repos only). To merge the data into your own system, poll index.json and compare <code>latest_version</code>/<code>updated</code>. Files in data/ and docs/ are generated and overwritten by the crawler, so contribute through the submit form or issues/PRs against crawler/.</p></div>
</div></body></html>"""

def generate(apps, out, icon_file, limit=None):
    """apps: public live app dicts. Returns (files_written, total_pages)."""
    items = list(live_items(apps, icon_file, out)); live_ids = {i["id"] for i in items}
    items += [b for b in baked_items(out) if b["id"] not in live_ids]
    if limit: items = items[:limit]
    wrote = 0; rows = []; names = {}
    groups = {}
    for n in items: groups.setdefault(dev_key(n["id"]), []).append(n)
    groups = {k: v for k, v in groups.items() if len(v) >= 2}
    for k, v in groups.items():
        for n in v: n["dev"] = (k, slug(k), len(v))
    gen = datetime.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"); api = []
    for n in items:
        sl = slug(n["id"])
        sdir = os.path.join(out, "shots", sl)
        n["shots"] = sorted(f for f in os.listdir(sdir) if f.endswith(".webp")) if os.path.isdir(sdir) else []
        api.append(_api_app(n, sl, gen))
        wrote += _write(os.path.join(out, "api", "v1", "apps", sl + ".json"), json.dumps(api[-1], ensure_ascii=False, separators=(",", ":"), sort_keys=True))
        png = qr_png(SITE + "a/" + sl + "/")
        if png: wrote += _write_bytes(os.path.join(out, "a", sl, "qr.png"), png)
        wrote += _write(os.path.join(out, "a", sl, "index.html"), render(n))
        rows.append((n["name"], sl, n["date"])); names[sl] = n["name"]
    rows.sort(key=lambda r: r[0].lower())
    buckets = {}
    for r in rows: buckets.setdefault(_bucket(r[0]), []).append(r)
    li = "".join('<a href="b/%s.html">%s (%d)</a>' % (k, html.escape(k.upper()) if k != "other" else "أخرى", len(v)) for k, v in sorted(buckets.items()))
    wrote += _write(os.path.join(out, "a", "style.css"), CSS)
    wrote += _write(os.path.join(out, "a", "index.html"),
        '<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>كل التطبيقات | كنز</title>'
        '<link rel="canonical" href="%sa/"><link rel="stylesheet" href="style.css"></head><body><div class="top"><a href="../">كنز | Kanz</a></div>'
        '<div class="w"><div class="card bk"><h1>كل التطبيقات (%d)</h1>%s</div></div></body></html>' % (SITE, len(rows), li))
    for k, v in buckets.items():
        ul = "".join('<li><a class="l" href="../%s/">%s</a></li>' % (sl, html.escape(nm)) for nm, sl, _ in v)
        wrote += _write(os.path.join(out, "a", "b", k + ".html"),
            '<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>تطبيقات %s | كنز</title>'
            '<link rel="canonical" href="%sa/b/%s.html"><link rel="stylesheet" href="../style.css"></head><body><div class="top"><a href="../../">كنز | Kanz</a></div>'
            '<div class="w"><div class="card"><h1>%s (%d)</h1><ul>%s</ul><p class="s"><a class="l" href="../">كل الحروف</a></p></div></div></body></html>' % (html.escape(k), SITE, k, html.escape(k.upper()), len(v), ul))
    for k, v in groups.items():
        v = sorted(v, key=lambda x: x["name"].lower())[:300]
        ul = "".join('<li><a class="l" href="../../a/%s/">%s</a></li>' % (slug(x["id"]), html.escape(x["name"])) for x in v)
        wrote += _write(os.path.join(out, "d", slug(k), "index.html"),
            '<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>%s | كنز</title>'
            '<link rel="canonical" href="%sd/%s/"><link rel="stylesheet" href="../../a/style.css"></head><body><div class="top"><a href="../../">كنز | Kanz</a></div>'
            '<div class="w"><div class="card"><h1><bdi dir="ltr">%s</bdi></h1><p class="n">تطبيقات مجمّعة حسب صاحب المستودع أو اسم الحزمة (مش دايمًا نفس المطوّر بالظبط).</p><ul>%s</ul></div></div></body></html>' % (html.escape(k), SITE, slug(k), html.escape(k), ul))
    wrote += _api_files(out, api, gen)
    wrote += _write(os.path.join(out, "names.json"), json.dumps(names, ensure_ascii=False, separators=(",", ":"), sort_keys=True))
    wrote += _write(os.path.join(out, "l", "index.html"), LIST_HTML)
    sm = ['<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">', "<url><loc>%s</loc></url>" % SITE, "<url><loc>%sa/</loc></url>" % SITE]
    for k in sorted(buckets): sm.append("<url><loc>%sa/b/%s.html</loc></url>" % (SITE, k))
    sm.append("<url><loc>%sdevelopers/</loc></url>" % SITE)
    for k in sorted(groups): sm.append("<url><loc>%sd/%s/</loc></url>" % (SITE, slug(k)))
    for nm, sl, d in rows:
        sm.append("<url><loc>%sa/%s/</loc>%s</url>" % (SITE, sl, "<lastmod>%s</lastmod>" % d if re.match(r"\d{4}-\d\d-\d\d$", d or "") else ""))
    sm.append("</urlset>")
    wrote += _write(os.path.join(out, "sitemap.xml"), "\n".join(sm))
    wrote += _write(os.path.join(out, "robots.txt"), "User-agent: *\nAllow: /\nSitemap: %ssitemap.xml\n" % SITE)
    return wrote, len(rows)
