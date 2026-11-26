#!/usr/bin/env python3
"""Kanz catalog crawler. Python 3.8+, standard library only.
Finds open-source Android apps with real APK releases on GitHub / GitLab / Codeberg,
verifies them, keeps every version forever, and writes a small compressed catalog."""
import os, sys, json, gzip, hashlib, time, re, urllib.request, urllib.error, urllib.parse, datetime

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CFG = json.load(open(os.environ.get("KANZ_CONFIG") or os.path.join(ROOT, "config.json"), encoding="utf-8"))
STATE_F = os.environ.get("KANZ_STATE") or os.path.join(ROOT, "data", "state.json")
OUT = os.environ.get("KANZ_OUT") or os.path.join(ROOT, "docs")
TOKEN = os.environ.get("GITHUB_TOKEN", "")
START = time.time()
UA = "kanz-catalog-crawler/1.0 (+https://github.com)"
DENY = re.compile(CFG["deny_regex"], re.I)
LIC_OK = set(CFG["licenses_ok"])
hash_budget = [CFG["max_hash_download_mb_per_run"] * 1024 * 1024]
stats = {"requests": 0, "new_apps": 0, "new_versions": 0, "rejected": 0, "checked": 0, "duplicates": 0}

class Stop(Exception): pass

def now(): return datetime.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")
def out_of_time(): return time.time() - START > CFG["max_runtime_minutes"] * 60
def log(*a): print(*a, file=sys.stderr, flush=True)

def http(url, etag=None, host="github", accept=None):
    """returns (status, headers, bytes|None). 304 -> (304, h, None)."""
    h = {"User-Agent": UA}
    if host == "github":
        h["Accept"] = accept or "application/vnd.github+json"
        if TOKEN: h["Authorization"] = "Bearer " + TOKEN
    if etag: h["If-None-Match"] = etag
    for attempt in range(3):
        stats["requests"] += 1
        try:
            r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=60)
            body = r.read()
            if host in ("gitlab", "codeberg"): time.sleep(CFG.get("host_delay_seconds", 2.5))
            return r.status, dict(r.headers), body
        except urllib.error.HTTPError as e:
            hd = dict(e.headers)
            if e.code == 304: return 304, hd, None
            if e.code in (403, 429):
                rem = hd.get("X-RateLimit-Remaining")
                ra = hd.get("Retry-After")
                if rem == "0":
                    reset = int(hd.get("X-RateLimit-Reset", "0")) - int(time.time())
                    if 0 < reset < 90 and host == "github": time.sleep(reset + 2); continue
                    raise Stop("github rate limit")
                if ra and int(ra) < 90: time.sleep(int(ra) + 1); continue
                if e.code == 429: raise Stop("rate limited by " + host)
                return e.code, hd, None
            if e.code >= 500: time.sleep(3 * (attempt + 1)); continue
            return e.code, hd, None
        except Exception as ex:
            time.sleep(2 * (attempt + 1))
    return 0, {}, None

def jget(url, etag=None, host="github"):
    st, hd, body = http(url, etag, host)
    if st == 200:
        try: return st, hd, json.loads(body.decode("utf-8"))
        except Exception: return 0, hd, None
    return st, hd, None

def sha256_url(url, host, size):
    """download to hash (streaming, never stored). Returns hex or None."""
    if size and size > hash_budget[0]: return None
    h = hashlib.sha256(); n = 0
    hd = {"User-Agent": UA}
    if host == "github" and TOKEN and "api.github.com" in url:
        hd["Authorization"] = "Bearer " + TOKEN
    try:
        r = urllib.request.urlopen(urllib.request.Request(url, headers=hd), timeout=120)
        while True:
            b = r.read(1 << 20)
            if not b: break
            h.update(b); n += len(b)
            if n > CFG["max_apk_mb"] * 1024 * 1024: return None
    except Exception as e:
        log("hash fail", url, e); return None
    hash_budget[0] -= n
    return h.hexdigest()

# ---------- categories ----------
CATMAP = [
 ("ألعاب", r"game|puzzle|emulator|chess|sudoku|2048|arcade|minecraft"),
 ("تواصل ومراسلة", r"messag|chat|matrix|xmpp|mastodon|fediverse|social|email|mail|sms|telegram|signal|irc|twitter|bluesky"),
 ("أمان وخصوصية", r"password|2fa|otp|authenticator|vpn|privacy|encrypt|security|firewall|tor\b|wireguard|keepass"),
 ("إنترنت ومتصفحات", r"browser|rss|feed reader|news|download manager|torrent|webview"),
 ("صوت وفيديو وموسيقى", r"music|audio|video|player|podcast|radio|stream|media|recorder|equalizer"),
 ("صور وتصميم", r"camera|photo|gallery|image|draw|wallpaper|icon pack|theme|sketch|ocr"),
 ("قراءة وكتابة", r"ebook|e-book|reader|epub|pdf|notes?\b|markdown|editor|dictionary|translat|text-to-speech|tts"),
 ("دين", r"quran|islam|prayer|salah|bible|adhan|athan|qibla"),
 ("تعليم وعلوم", r"learn|education|quran|math|science|flashcard|anki|school"),
 ("إنتاجية ومهام", r"todo|task|calendar|reminder|habit|timer|alarm|clock|productivity|planner|pomodoro"),
 ("مال وتسوق", r"finance|budget|expense|wallet|bitcoin|crypto|money|shopping"),
 ("صحة ورياضة", r"fitness|workout|health|diet|sleep|meditation|running|step counter|medication"),
 ("خرائط وسفر", r"map|navigation|gps|transit|weather|travel|osm|openstreetmap|location"),
 ("أدوات النظام", r"launcher|keyboard|file manager|battery|flashlight|calculator|system|widget|notification|app manager|root"),
 ("شبكات وملفات", r"sync|ftp|ssh|remote|network|nextcloud|syncthing|webdav|file transfer|wifi|wi-fi|bluetooth"),
 ("برمجة", r"developer|terminal|git\b|ide\b|code|programming|debug|adb|api client"),
 ("ذكاء اصطناعي", r"\bai\b|llm|gpt|chatbot|machine learning|stable diffusion|whisper"),
]
CATC = [(n, re.compile(p, re.I)) for n, p in CATMAP]
def categorize(name, desc, topics):
    t = " ".join([name or "", desc or "", " ".join(topics or [])])
    best = "أخرى"
    for n, rx in CATC:
        if rx.search(t): return n
    return best

def norm_name(n): return re.sub(r"[^a-z0-9\u0600-\u06ff]", "", (n or "").lower())

# ---------- state ----------
def load_state():
    if os.path.exists(STATE_F): return json.load(open(STATE_F, encoding="utf-8"))
    return {"rev": 0, "apps": {}, "rejected": {}, "pending": {}, "disc": {}}

def save_state(s):
    os.makedirs(os.path.dirname(STATE_F) or ".", exist_ok=True)
    tmp = STATE_F + ".tmp"
    json.dump(s, open(tmp, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    os.replace(tmp, STATE_F)

# ---------- verification rules ----------
def license_verdict(spdx, owner):
    if spdx in LIC_OK: return True, spdx
    if not spdx and owner in CFG["trusted_owners_allow_no_license"]: return True, "UNSPECIFIED"
    return False, "license:" + (spdx or "none")

def repo_verdict(meta):
    """meta: id, owner, name, desc, topics, spdx, archived, fork"""
    if meta["archived"]: return False, "archived"
    own = meta["owner"]
    ok, lic = license_verdict(meta["spdx"], own)
    if not ok: return False, lic
    txt = " ".join([meta["name"], meta["desc"] or "", " ".join(meta["topics"])])
    if DENY.search(txt): return False, "mod/crack keywords"
    if meta["fork"] and own not in CFG["trusted_owners_allow_no_license"]: return False, "fork"
    return True, lic

def apk_ok(name, size, trusted=False):
    n = name.lower()
    if not n.endswith(".apk"): return False
    if size and size > CFG["max_apk_mb"] * 1048576: return False
    if CFG["skip_debug_apks"] and not trusted and re.search(r"debug|unsigned|test", n): return False
    if DENY.search(re.sub(r"[_.-]", " ", n)): return False
    return True

# ---------- version merge (never replaces) ----------
def merge_versions(app, found, ts):
    """found: list of dict(tag,date,file,size,sha256,url,hs,rid). Appends only."""
    have = {(v["tag"], v["file"], v["sha"]) for v in app["versions"]}
    added = 0
    live = {(f["tag"], f["file"]) for f in found}
    for f in found:
        k = (f["tag"], f["file"], f["sha"])
        if k in have: continue
        # same tag+file but different hash: keep both, mark old as superseded
        for v in app["versions"]:
            if v["tag"] == f["tag"] and v["file"] == f["file"] and not v.get("sup"):
                v["sup"] = 1
        app["versions"].append(f); added += 1
    for v in app["versions"]:
        v["gone"] = 0 if (v["tag"], v["file"]) in live else 1
    app["versions"].sort(key=lambda v: v["date"], reverse=True)
    return added

# ---------- GitHub ----------
def gh_meta(o):
    lic = (o.get("license") or {}).get("spdx_id") or ""
    if lic in ("NOASSERTION", "NONE"): lic = ""
    return {"id": o["full_name"], "owner": o["owner"]["login"], "name": o["name"], "desc": o.get("description") or "",
            "topics": o.get("topics") or [], "spdx": lic, "archived": o.get("archived", False), "fork": o.get("fork", False),
            "stars": o.get("stargazers_count", 0), "url": o["html_url"], "host": "github",
            "pushed": o.get("pushed_at") or "", "avatar": o["owner"].get("avatar_url", "")}

def gh_releases(app_id, etag):
    st, hd, data = jget("https://api.github.com/repos/%s/releases?per_page=50" % app_id, etag)
    if st in (200,):
        return st, hd.get("ETag"), data
    return st, etag, None

def gh_collect(app_id, rels):
    found = []
    for r in rels:
        if r.get("draft"): continue
        pre = bool(r.get("prerelease"))
        for a in r.get("assets", []):
            if not apk_ok(a["name"], a.get("size"), app_id.split("/")[0] in CFG["trusted_owners_allow_no_license"]): continue
            dg = a.get("digest") or ""
            sha = dg[7:].lower() if dg.startswith("sha256:") else None
            hs = "api"
            if not sha:
                sha = sha256_url(a["browser_download_url"], "github", a.get("size"))
                hs = "computed"
            if not sha: continue   # cannot verify -> do not publish
            found.append({"tag": r.get("tag_name") or r.get("name") or "?", "date": (r.get("published_at") or r.get("created_at") or "")[:10],
                          "file": a["name"], "size": a.get("size", 0), "sha": sha, "url": a["browser_download_url"],
                          "hs": hs, "pre": 1 if pre else 0})
    return found

def new_app(meta, lic):
    return {"id": meta["id"], "host": meta["host"], "name": meta["name"], "desc": meta["desc"][:200], "lic": lic,
            "stars": meta["stars"], "url": meta["url"], "topics": meta["topics"][:8], "cat": categorize(meta["name"], meta["desc"], meta["topics"]),
            "owner": meta["owner"], "avatar": meta.get("avatar", ""), "added": now()[:10], "r": 0, "versions": [], "etag": None,
            "checked": "", "dup": None, "maybe_dup": None}

def process_github_candidate(S, meta):
    aid = meta["id"]
    if aid in S["apps"]: return
    rej = S["rejected"].get(aid)
    if rej and (time.time() - rej["t"]) < CFG["reject_recheck_days"] * 86400 and rej["pushed"] == meta["pushed"]: return
    ok, lic = repo_verdict(meta)
    if not ok:
        S["rejected"][aid] = {"why": lic, "t": int(time.time()), "pushed": meta["pushed"]}; stats["rejected"] += 1; return
    st, et, rels = gh_releases(aid, None)
    if st != 200:
        if st in (404, 451): S["rejected"][aid] = {"why": "http %s" % st, "t": int(time.time()), "pushed": meta["pushed"]}
        return
    found = gh_collect(aid, rels)
    if not found:
        pend = any(not r.get("draft") and any(a["name"].lower().endswith(".apk") for a in r.get("assets", [])) for r in rels)
        if pend and hash_budget[0] <= 0: return  # retry next run
        S["rejected"][aid] = {"why": "no verifiable apk release", "t": int(time.time()), "pushed": meta["pushed"]}; stats["rejected"] += 1; return
    a = new_app(meta, lic); a["etag"] = et; a["checked"] = now()
    S["apps"][aid] = a
    merge_versions(a, found, now()); a["r"] = S["rev"] + 1
    icon_now(S, a)
    stats["new_apps"] += 1; stats["new_versions"] += len(a["versions"])
    log("NEW", aid, len(a["versions"]), "versions")

def vt_check(sha):
    """Optional VirusTotal lookup by hash. Returns positives count, 0 clean, None unavailable."""
    if not CFG.get("vt_enabled") or not os.environ.get("VT_API_KEY"): return None
    st, hd, d = jget("https://www.virustotal.com/api/v3/files/" + sha, host="virustotal")
    if st != 200 or not d: return None
    try:
        stt = d["data"]["attributes"]["last_analysis_stats"]
        return int(stt.get("malicious", 0)) + int(stt.get("suspicious", 0))
    except Exception: return None

def sub_trusted(S, prefixes, exclude=None):
    """a developer/source is trusted once they already have ANOTHER app in the catalog"""
    return any(aid != exclude and any(aid.startswith(p) for p in prefixes) for aid in S["apps"])

def sub_finish(S, it, r, a, doubt):
    """decide: auto-accept (trusted source, no doubts) or hold for the store owner's review (removes the staged app)."""
    if not doubt and sub_trusted(S, r["trust_prefixes"], a["id"]):
        r["status"] = "accepted"; log("SUBMISSION accepted", a["id"]); return
    S["apps"].pop(a["id"], None)
    r["status"] = "review"; r["doubt"] = doubt or ["مطور جديد (أول تطبيق من المصدر ده)"]
    log("SUBMISSION pending review", a["id"], r["doubt"])

def sub_add_gitlab(S, path):
    st, hd, o = jget("https://gitlab.com/api/v4/projects/%s?license=true" % urllib.parse.quote(path, safe=""), host="gitlab")
    if st != 200 or not o: return None, "مشروع GitLab مش موجود أو مش عام", []
    spdx = ((o.get("license") or {}).get("key") or "")
    spdx = {"mit": "MIT", "apache-2.0": "Apache-2.0", "gpl-3.0": "GPL-3.0", "gpl-2.0": "GPL-2.0", "agpl-3.0": "AGPL-3.0", "lgpl-3.0": "LGPL-3.0",
            "lgpl-2.1": "LGPL-2.1", "bsd-3-clause": "BSD-3-Clause", "bsd-2-clause": "BSD-2-Clause", "mpl-2.0": "MPL-2.0", "unlicense": "Unlicense", "isc": "ISC", "cc0-1.0": "CC0-1.0"}.get(spdx, "")
    meta = {"id": "gitlab:" + path, "owner": o["namespace"]["full_path"], "name": o["name"], "desc": o.get("description") or "", "topics": o.get("topics") or [],
            "spdx": spdx, "archived": o.get("archived", False), "fork": bool(o.get("forked_from_project")), "stars": o.get("star_count", 0),
            "url": o["web_url"], "host": "gitlab", "pushed": o.get("last_activity_at", ""), "avatar": o.get("avatar_url") or ""}
    ok, lic = repo_verdict(meta)
    if not ok: return None, lic, []
    st, hd, rels = jget("https://gitlab.com/api/v4/projects/%d/releases?per_page=30" % o["id"], host="gitlab")
    found = gl_collect(rels or [])
    if not found: return None, "مفيش Release فيه ملف APK يتوثّق بالـSHA-256", []
    a = new_app(meta, lic); a["pid"] = o["id"]; a["checked"] = now(); S["apps"][meta["id"]] = a
    merge_versions(a, found, now()); a["r"] = S["rev"] + 1; icon_now(S, a); stats["new_apps"] += 1
    return a, "", []

def sub_add_codeberg(S, full):
    base = "https://codeberg.org/api/v1"
    st, hd, o = jget("%s/repos/%s" % (base, full), host="codeberg")
    if st != 200 or not o: return None, "مستودع Codeberg مش موجود أو مش عام", []
    spdx = cb_license(base, full)
    meta = {"id": "codeberg:" + full, "owner": o["owner"]["login"], "name": o["name"], "desc": o.get("description") or "", "topics": o.get("topics") or [],
            "spdx": spdx, "archived": o.get("archived", False), "fork": o.get("fork", False), "stars": o.get("stars_count", 0),
            "url": o["html_url"], "host": "codeberg", "pushed": o.get("updated_at", ""), "avatar": o.get("avatar_url") or ""}
    ok, lic = repo_verdict(meta)
    if not ok: return None, lic, []
    st, hd, rels = jget("%s/repos/%s/releases?limit=30" % (base, full), host="codeberg")
    found = cb_collect(rels or [])
    if not found: return None, "مفيش Release فيه ملف APK يتوثّق بالـSHA-256", []
    a = new_app(meta, lic); a["checked"] = now(); S["apps"][meta["id"]] = a
    merge_versions(a, found, now()); a["r"] = S["rev"] + 1; icon_now(S, a); stats["new_apps"] += 1
    return a, "", []

def sub_add_fdroid(S, pkg):
    st, hd, d = jget("https://f-droid.org/api/v1/packages/" + pkg, host="fdroid")
    if st != 200 or not d: return None, "التطبيق مش موجود على F-Droid", []
    lic = (d.get("license") or "").split(",")[0]
    lic = re.sub(r"-(only|or-later)$", "", lic)
    name = d.get("name") or pkg
    summ = re.sub(r"<[^>]+>", "", d.get("summary") or "")
    meta = {"id": "fdroid:" + pkg, "owner": "f-droid", "name": name, "desc": summ, "topics": [],
            "spdx": lic, "archived": False, "fork": False, "stars": 0,
            "url": "https://f-droid.org/packages/%s/" % pkg, "host": "fdroid", "pushed": "", "avatar": ""}
    ok, lic2 = license_verdict(lic, "f-droid")
    if not ok: return None, lic2, []
    svc = d.get("suggestedVersionCode")
    if svc: meta["avatar"] = "https://f-droid.org/repo/icons-640/%s.%s.png" % (pkg, svc)
    found = []
    for vc, pk in (d.get("packages") or {}).items():
        fn = pk.get("apkName")
        if not fn or not apk_ok(fn, pk.get("size", 0)): continue
        url = "https://f-droid.org/repo/" + fn
        sha = pk.get("hash") if pk.get("hashType") == "sha256" else None
        hs = "api" if sha else "computed"
        if not sha:
            sha = sha256_url(url, "fdroid", pk.get("size", 0))
            if not sha: continue
        dt = ""
        try: dt = time.strftime("%Y-%m-%d", time.gmtime((pk.get("added") or 0) / 1000))
        except Exception: pass
        found.append({"tag": pk.get("versionName") or str(vc), "date": dt or now()[:10], "file": fn, "size": pk.get("size", 0), "sha": sha, "url": url, "hs": hs, "pre": 0})
    if not found: return None, "مفيش ملف APK يتوثّق بالـSHA-256 على F-Droid", []
    a = new_app(meta, lic2); a["checked"] = now(); S["apps"][meta["id"]] = a
    merge_versions(a, found, now()); a["r"] = S["rev"] + 1; icon_now(S, a); stats["new_apps"] += 1
    return a, "", []

def process_submissions(S):
    """apps queued by 'submit-app' issues (data/submissions.json); outcome goes to data/submission_results.json.
    Passing apps from already-listed developers enter at once; new developers or flagged apps wait for the owner's
    'approved' label on the issue."""
    qf = os.path.join(os.path.dirname(STATE_F), "submissions.json"); rf = os.path.join(os.path.dirname(STATE_F), "submission_results.json")
    if not os.path.exists(qf): return
    try: Q = json.load(open(qf, encoding="utf-8"))
    except Exception: return
    R = json.load(open(rf, encoding="utf-8")) if os.path.exists(rf) else {}
    me = os.environ.get("GITHUB_REPOSITORY", "")
    for it in Q[:200]:
        typ = it.get("type", "github"); link = it.get("link") or it.get("repo", "")
        if not link: continue
        key = typ + ":" + link
        r = R.setdefault(key, {"issue": it.get("issue"), "status": "pending", "tries": 0, "type": typ})
        if r["status"] in ("accepted", "rejected"): continue
        if out_of_time(): break
        if r["status"] == "review":
            num = r.get("issue")
            if not (me and num): continue
            st, hd, iss = jget("https://api.github.com/repos/%s/issues/%s" % (me, num))
            if st != 200 or not iss: continue
            if iss.get("state") == "closed": r["status"] = "rejected"; r["why"] = "اتقفل الطلب من غير موافقة"; continue
            labels = [l.get("name", "") for l in iss.get("labels", [])]
            if "approved" not in labels: continue
            r["status"] = "pending"; r["approved"] = True  # fall through to add on this run
        if not r.get("approved"):
            r["tries"] += 1
            if r["tries"] > 12: r["status"] = "rejected"; r["why"] = "عدد محاولات كتير من غير نتيجة"; continue
        a, why, doubt = None, "", []
        if typ == "github":
            st, hd, o = jget("https://api.github.com/repos/" + link)
            if st != 200 or not o:
                if st in (404, 451): r["status"] = "rejected"; r["why"] = "المستودع مش موجود أو مش عام"
                continue
            meta = gh_meta(o); S["rejected"].pop(meta["id"], None)
            before = set(S["apps"]); process_github_candidate(S, meta)
            if meta["id"] in S["apps"] and meta["id"] not in before:
                a = S["apps"][meta["id"]]
            elif meta["id"] in S["apps"]:
                r["status"] = "accepted"; continue
            else:
                rj = S["rejected"].get(meta["id"])
                if rj and rj["why"] != "no verifiable apk release": r["status"] = "rejected"; r["why"] = rj["why"]
                elif rj: r["status"] = "rejected"; r["why"] = "مفيش Release فيه ملف APK يتوثّق بالـSHA-256"
                continue
            r["trust_prefixes"] = ["gh:" + meta["owner"] + "/", meta["owner"] + "/"]
        elif typ == "gitlab":
            a, why, doubt = sub_add_gitlab(S, link); r["trust_prefixes"] = ["gitlab:" + link.rsplit("/", 1)[0] + "/"]
        elif typ == "codeberg":
            a, why, doubt = sub_add_codeberg(S, link); r["trust_prefixes"] = ["codeberg:" + link.split("/", 1)[0] + "/"]
        elif typ == "fdroid":
            a, why, doubt = sub_add_fdroid(S, link); r["trust_prefixes"] = ["fdroid:"]
        if not a:
            if why: r["status"] = "rejected"; r["why"] = why
            continue
        if r.get("approved"):
            r["status"] = "accepted"; log("SUBMISSION accepted after review", a["id"]); continue
        if CFG.get("scan_apk_submissions", True) and not doubt:
            try:
                import apk_scan
                top = _top_apk(a)
                if top:
                    scan = apk_scan.scan_apk(top, CFG.get("max_apk_mb", 150), os.path.join(os.path.dirname(os.path.abspath(__file__)), "trk.txt"), set(CFG.get("danger_perms", [])))
                    if scan.get("danger"): doubt.append("صلاحيات حساسة: " + ", ".join(p.rsplit(".", 1)[-1] for p in scan["danger"]))
                    if scan.get("trackers"): doubt.append("متتبعون: " + ", ".join(scan["trackers"]))
                    vt = vt_check(a["versions"][0]["sha"])
                    if vt is not None and vt > CFG.get("vt_max_positives", 0): doubt.append("فحص الفيروسات: %d محرك اشتبه" % vt)
            except Exception as e: log("submission scan skipped:", repr(e))
        sub_finish(S, it, r, a, doubt)
    os.makedirs(os.path.dirname(rf), exist_ok=True); json.dump(R, open(rf, "w", encoding="utf-8"), indent=1, ensure_ascii=False)

def gh_search(S, q, pages, since, sort_stars=False):
    n = 0
    qq = q + (" pushed:>" + since if since else "")
    for p in range(1, pages + 1):
        if out_of_time(): break
        url = "https://api.github.com/search/repositories?q=%s&sort=updated&order=desc&per_page=100&page=%d" % (urllib.parse.quote(qq), p)
        st, hd, d = jget(url)
        time.sleep(2.2)
        if st != 200: return None
        if not d: break
        items = d.get("items", [])
        for o in items:
            if out_of_time(): return n
            try: process_github_candidate(S, gh_meta(o)); n += 1
            except Stop: raise
        if len(items) < 100: break
    return n

def gh_user_repos(S, owner):
    page = 1
    while True:
        st, hd, d = jget("https://api.github.com/users/%s/repos?per_page=100&page=%d" % (owner, page))
        if st != 200 or not d: break
        for o in d: process_github_candidate(S, gh_meta(o))
        if len(d) < 100: break
        page += 1

def gh_recheck(S):
    """watch imported apps for new releases (conditional requests, 304 is free)"""
    apps = sorted([a for a in S["apps"].values() if a["host"] == "github"], key=lambda a: a["checked"])
    for a in apps:
        if out_of_time(): break
        st, et, rels = gh_releases(a["id"], a["etag"])
        stats["checked"] += 1
        a["checked"] = now()
        if st == 304: continue
        if st in (404, 451): a["gone"] = 1; a["r"] = S["rev"] + 1; continue
        if st != 200: continue
        a["etag"] = et
        found = gh_collect(a["id"], rels)
        add = merge_versions(a, found, now()) if found else 0
        if add:
            a["r"] = S["rev"] + 1; stats["new_versions"] += add; log("UPDATE", a["id"], "+%d" % add)
        # refresh repo facts rarely (stars) from releases call isn't possible; done in refresh_meta

def refresh_meta(S, limit=60):
    apps = sorted([a for a in S["apps"].values() if a["host"] == "github"], key=lambda a: a.get("meta_at", ""))[:limit]
    for a in apps:
        if out_of_time(): break
        st, hd, o = jget("https://api.github.com/repos/" + a["id"])
        a["meta_at"] = now()
        if st != 200 or not o: continue
        m = gh_meta(o)
        if m["stars"] != a["stars"] or m["archived"]:
            a["stars"] = m["stars"]; a["r"] = S["rev"] + 1
        if m["archived"]: a["archived"] = 1

# ---------- GitLab ----------
def gl_run(S):
    for t, pg in [(t, pg) for t in CFG["gitlab_topics"] for pg in range(1, CFG.get("gitlab_pages_per_run", 3) + 1)]:
        if out_of_time(): return
        st, hd, lst = jget("https://gitlab.com/api/v4/projects?topic=%s&order_by=last_activity_at&per_page=50&simple=false&page=%d" % (t, pg), host="gitlab")
        if st != 200 or not lst: continue
        for p in lst:
            if out_of_time(): return
            aid = "gitlab:" + p["path_with_namespace"]
            if aid in S["apps"]: continue
            rej = S["rejected"].get(aid)
            if rej and time.time() - rej["t"] < CFG["reject_recheck_days"] * 86400: continue
            st, hd, d = jget("https://gitlab.com/api/v4/projects/%d?license=true" % p["id"], host="gitlab")
            if st != 200 or not d: continue
            spdx = ((d.get("license") or {}).get("key") or "")
            spdx = {"mit": "MIT", "apache-2.0": "Apache-2.0", "gpl-3.0": "GPL-3.0", "gpl-2.0": "GPL-2.0", "agpl-3.0": "AGPL-3.0", "lgpl-3.0": "LGPL-3.0",
                    "lgpl-2.1": "LGPL-2.1", "bsd-3-clause": "BSD-3-Clause", "bsd-2-clause": "BSD-2-Clause", "mpl-2.0": "MPL-2.0", "unlicense": "Unlicense", "isc": "ISC", "cc0-1.0": "CC0-1.0"}.get(spdx, "")
            meta = {"id": aid, "owner": d["namespace"]["full_path"], "name": d["name"], "desc": d.get("description") or "", "topics": d.get("topics") or [],
                    "spdx": spdx, "archived": d.get("archived", False), "fork": bool(d.get("forked_from_project")), "stars": d.get("star_count", 0),
                    "url": d["web_url"], "host": "gitlab", "pushed": d.get("last_activity_at", ""), "avatar": d.get("avatar_url") or ""}
            ok, lic = repo_verdict(meta)
            if not ok:
                S["rejected"][aid] = {"why": lic, "t": int(time.time()), "pushed": ""}; continue
            st, hd, rels = jget("https://gitlab.com/api/v4/projects/%d/releases?per_page=30" % p["id"], host="gitlab")
            found = gl_collect(rels or [])
            if not found:
                S["rejected"][aid] = {"why": "no apk release", "t": int(time.time()), "pushed": ""}; continue
            a = new_app(meta, lic); a["pid"] = p["id"]; S["apps"][aid] = a; a["checked"] = now()
            merge_versions(a, found, now()); a["r"] = S["rev"] + 1; icon_now(S, a); stats["new_apps"] += 1; stats["new_versions"] += len(found)
            log("NEW", aid)
    for a in [x for x in S["apps"].values() if x["host"] == "gitlab"]:
        if out_of_time(): break
        pid = a.get("pid")
        url = ("https://gitlab.com/api/v4/projects/%d/releases?per_page=30" % pid) if pid else               ("https://gitlab.com/api/v4/projects/%s/releases?per_page=30" % urllib.parse.quote(a["id"][7:], safe=""))
        st, hd, rels = jget(url, host="gitlab")
        a["checked"] = now()
        if st == 200 and rels:
            known = {(v["tag"], v["file"]): v for v in a["versions"]}
            f = gl_collect(rels, known=known)
            if f and merge_versions(a, f, now()): a["r"] = S["rev"] + 1; stats["new_versions"] += 1

def gl_collect(rels, known=None):
    found = []
    for r in rels:
        for l in (r.get("assets") or {}).get("links", []):
            u = l.get("direct_asset_url") or l.get("url") or ""
            nm = l.get("name") or u.rsplit("/", 1)[-1]
            if not (u.lower().split("?")[0].endswith(".apk") or nm.lower().endswith(".apk")): continue
            fn = nm if nm.lower().endswith(".apk") else u.split("?")[0].rsplit("/", 1)[-1]
            if not apk_ok(fn, 0): continue
            k = (r.get("tag_name", "?"), fn)
            if known and k in known:
                found.append(known[k]); continue
            sha = sha256_url(u, "gitlab", 0)
            if not sha: continue
            found.append({"tag": r.get("tag_name", "?"), "date": (r.get("released_at") or "")[:10], "file": fn, "size": 0, "sha": sha, "url": u, "hs": "computed", "pre": 0})
    return found

# ---------- Codeberg / Forgejo (untested live: Codeberg rate-limited the build sandbox) ----------
def cb_run(S):
    base = "https://codeberg.org/api/v1"
    for t, pg in [(t, pg) for t in CFG["codeberg_topics"] for pg in range(1, CFG.get("codeberg_pages_per_run", 3) + 1)]:
        if out_of_time(): return
        st, hd, d = jget("%s/repos/search?topic=true&q=%s&limit=50&sort=updated&page=%d" % (base, t, pg), host="codeberg")
        if st != 200 or not d: continue
        for p in d.get("data", []):
            if out_of_time(): return
            aid = "codeberg:" + p["full_name"]
            if aid in S["apps"]: continue
            rej = S["rejected"].get(aid)
            if rej and time.time() - rej["t"] < CFG["reject_recheck_days"] * 86400: continue
            spdx = cb_license(base, p["full_name"])
            meta = {"id": aid, "owner": p["owner"]["login"], "name": p["name"], "desc": p.get("description") or "", "topics": p.get("topics") or [],
                    "spdx": spdx, "archived": p.get("archived", False), "fork": p.get("fork", False), "stars": p.get("stars_count", 0),
                    "url": p["html_url"], "host": "codeberg", "pushed": p.get("updated_at", ""), "avatar": p.get("avatar_url") or ""}
            ok, lic = repo_verdict(meta)
            if not ok: S["rejected"][aid] = {"why": lic, "t": int(time.time()), "pushed": ""}; continue
            st, hd, rels = jget("%s/repos/%s/releases?limit=30" % (base, p["full_name"]), host="codeberg")
            found = cb_collect(rels or [])
            if not found: S["rejected"][aid] = {"why": "no apk release", "t": int(time.time()), "pushed": ""}; continue
            a = new_app(meta, lic); S["apps"][aid] = a; a["checked"] = now()
            merge_versions(a, found, now()); a["r"] = S["rev"] + 1; icon_now(S, a); stats["new_apps"] += 1; stats["new_versions"] += len(found)
    for a in [x for x in S["apps"].values() if x["host"] == "codeberg"]:
        if out_of_time(): break
        st, hd, rels = jget("%s/repos/%s/releases?limit=30" % (base, a["id"][9:]), host="codeberg")
        a["checked"] = now()
        if st == 200 and rels:
            known = {(v["tag"], v["file"]): v for v in a["versions"]}
            f = cb_collect(rels, known=known)
            if f and merge_versions(a, f, now()): a["r"] = S["rev"] + 1; stats["new_versions"] += 1

LIC_SNIFF = [("GNU AFFERO GENERAL PUBLIC LICENSE", "AGPL-3.0"), ("GNU LESSER GENERAL PUBLIC LICENSE", "LGPL-3.0"), ("GNU GENERAL PUBLIC LICENSE\n Version 3", "GPL-3.0"),
             ("GNU GENERAL PUBLIC LICENSE\n                       Version 3", "GPL-3.0"), ("Apache License", "Apache-2.0"), ("MIT License", "MIT"), ("Mozilla Public License", "MPL-2.0")]
def cb_license(base, full):
    for fn in ("LICENSE", "LICENSE.md", "COPYING", "LICENSE.txt"):
        st, hd, body = http("%s/repos/%s/raw/%s" % (base, full, fn), host="codeberg")
        if st == 200 and body:
            t = body[:600].decode("utf-8", "ignore")
            for k, v in LIC_SNIFF:
                if k.lower() in t.lower(): return v
    return ""

def cb_collect(rels, known=None):
    found = []
    for r in rels:
        if r.get("draft"): continue
        for a in r.get("assets", []):
            if not apk_ok(a["name"], a.get("size")): continue
            k = (r.get("tag_name", "?"), a["name"])
            if known and k in known:
                found.append(known[k]); continue
            sha = sha256_url(a["browser_download_url"], "codeberg", a.get("size"))
            if not sha: continue
            found.append({"tag": r.get("tag_name", "?"), "date": (r.get("published_at") or r.get("created_at") or "")[:10], "file": a["name"],
                          "size": a.get("size", 0), "sha": sha, "url": a["browser_download_url"], "hs": "computed", "pre": 1 if r.get("prerelease") else 0})
    return found

# ---------- icons (GitHub repos only; small webp, letter avatar in the app when none) ----------
try:
    from PIL import Image
    import io
except Exception:
    Image = None

def icon_file(aid): return re.sub(r"[^A-Za-z0-9._-]", "_", aid) + ".webp"
DENS = {"xxxhdpi": 2, "xxhdpi": 3, "xhdpi": 4, "hdpi": 5, "mdpi": 6}

def pick_icon(paths):
    best = None
    for p in paths:
        pl = p.lower()
        if not pl.endswith((".png", ".webp")): continue
        if re.search(r"(^|/)(test|androidtest|sample|samples|demo|docs|screenshots?|build)/", pl): continue
        base = pl.rsplit("/", 1)[-1]; score = None
        if re.search(r"(fastlane/)?metadata/(android/)?[^/]+/images/icon\.(png|webp)$", pl): score = 0
        elif "playstore" in base and "launcher" in base: score = 1
        elif re.match(r"ic_launcher(_round)?\.(png|webp)$", base) and "/res/" in pl:
            d = next((v for k, v in DENS.items() if "-" + k in pl), 7)
            score = 10 + d + (20 if "round" in base else 0) + (5 if "/src/main/" not in pl else 0) + (3 if "/debug/" in pl else 0)
        elif base in ("icon.png", "app_icon.png", "logo.png") and pl.count("/") <= 1: score = 60
        if score is not None and (best is None or score < best[0]): best = (score, p)
    return best[1] if best else None

def make_icon(aid):
    """returns True if docs/icons/<id>.webp written"""
    if Image is None: return False
    st, hd, t = jget("https://api.github.com/repos/%s/git/trees/HEAD?recursive=1" % aid)
    if st != 200 or not t: return False
    path = pick_icon([x["path"] for x in t.get("tree", []) if x.get("type") == "blob" and x.get("size", 1) < 400000])
    if not path: return False
    st, hd, body = http("https://raw.githubusercontent.com/%s/HEAD/%s" % (aid, urllib.parse.quote(path)), host="raw")
    if st != 200 or not body: return False
    try:
        im = Image.open(io.BytesIO(body)).convert("RGBA")
        if min(im.size) < 32: return False
        im.thumbnail((96, 96), Image.LANCZOS)
        sq = Image.new("RGBA", (96, 96), (0, 0, 0, 0)); sq.paste(im, ((96 - im.size[0]) // 2, (96 - im.size[1]) // 2))
        # reject fully transparent / blank images
        if sq.getchannel("A").getextrema()[1] < 40: return False
        px = [q for q in sq.getdata() if q[3] > 40]
        if px and sum(1 for q in px if min(q[:3]) > 235) / len(px) > 0.8:   # white glyph only: put it on a dark tile
            bgi = Image.new("RGBA", (96, 96), (30, 27, 75, 255)); bgi.alpha_composite(sq); sq = bgi
        os.makedirs(os.path.join(OUT, "icons"), exist_ok=True)
        sq.save(os.path.join(OUT, "icons", icon_file(aid)), "WEBP", quality=82, method=6)
        return True
    except Exception as e:
        log("icon fail", aid, e); return False

def icons_pass(S, limit=None):
    limit = limit or CFG.get("icons_per_run", 500); n = 0
    for a in sorted(S["apps"].values(), key=lambda a: a["added"], reverse=True):
        if n >= limit or out_of_time(): break
        if a["host"] != "github" or a.get("ic"): continue
        if a.get("ict") and time.time() - a["ict"] < 60 * 86400: continue
        n += 1
        if make_icon(a["id"]): a["ic"] = 1; a["r"] = S["rev"] + 1; log("ICON", a["id"])
        else: a["ict"] = int(time.time())
    for a in S["apps"].values():   # file vanished (fresh checkout without icons) -> redo
        pass

# ---------- icons pulled out of the APK itself (when the repo / index has none) ----------
def _save_icon(im, name):
    os.makedirs(os.path.join(OUT, "icons"), exist_ok=True)
    im.save(os.path.join(OUT, "icons", name), "WEBP", quality=75, method=6)

def _fetch_img(url, max_mb=3):
    """download one image (size-capped) and return a PIL image or None (svg and broken files are skipped)"""
    try:
        r = urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=30)
        raw = r.read(max_mb * 1024 * 1024 + 1)
        if not raw or len(raw) > max_mb * 1024 * 1024: return None
        im = Image.open(io.BytesIO(raw)); im.load(); return im
    except Exception: return None

def _square(im, size=128):
    """centre-crop to a square (so a banner or screenshot still gives a usable icon), then shrink"""
    im = im.convert("RGBA"); w, h = im.size
    if min(w, h) < 24: return None
    m = min(w, h); l = (w - m) // 2; t = 0 if h > w * 1.3 else (h - m) // 2   # tall screenshots: keep the top
    im = im.crop((l, t, l + m, t + m)).resize((size, size), Image.LANCZOS)
    bg = Image.new("RGBA", (size, size), (255, 255, 255, 255)); bg.alpha_composite(im)
    ex = bg.convert("L").getextrema()
    if ex[1] - ex[0] < 12: return None      # blank image
    return bg.convert("RGB")

_BAD_IMG = re.compile(r"shields\.io|badge|travis|circleci|codecov|github\.com/[^/]+/[^/]+/(actions|workflows)|\.svg|sponsor|donate|liberapay|paypal|weblate|hosted\.weblate|fdroid\.gitlab|get-it-on|google-play|play\.google|buymeacoffee|ko-fi|opencollective|badgen|img\.shields", re.I)
def _readme_images(raw_base, text, limit=4):
    out = []
    for m in re.finditer(r'!\[[^\]]*\]\(\s*<?([^)\s>]+)|<img[^>]+src=["\']([^"\']+)', text):
        u = (m.group(1) or m.group(2) or "").strip()
        if not u or _BAD_IMG.search(u) or u.startswith("data:"): continue
        if not u.startswith("http"): u = raw_base + urllib.parse.quote(u.lstrip("./").lstrip("/"), safe="/%:@")
        out.append(u)
        if len(out) >= limit: break
    return out

def related_image(a, S):
    """last real-image fallbacks when the app's own icon cannot be extracted: README/repo images, store graphics,
    social preview, project avatar, owner avatar. Returns a 128px PIL image or None. Shrunk small by the caller."""
    if Image is None: return None
    aid = a["id"]; urls = []
    try:
        if a["host"] == "github":
            for rd in ("README.md", "readme.md", "README.rst", "README"):
                st, hd, body = http("https://raw.githubusercontent.com/%s/HEAD/%s" % (aid, rd), host="raw")
                if st == 200 and body:
                    urls += _readme_images("https://raw.githubusercontent.com/%s/HEAD/" % aid, body.decode("utf-8", "ignore")[:60000]); break
            urls += ["https://opengraph.githubusercontent.com/1/" + aid, "https://github.com/%s.png?size=200" % aid.split("/")[0]]
        elif a["host"] == "gitlab":
            path = aid.split(":", 1)[1]
            st, hd, pj = jget("https://gitlab.com/api/v4/projects/" + urllib.parse.quote(path, safe=""), host="raw")
            if pj and pj.get("avatar_url"): urls.append(pj["avatar_url"])
            br = pj.get("default_branch") if pj else None
            if br:
                st, hd, body = http("https://gitlab.com/%s/-/raw/%s/README.md" % (path, br), host="raw")
                if st == 200 and body: urls += _readme_images("https://gitlab.com/%s/-/raw/%s/" % (path, br), body.decode("utf-8", "ignore")[:60000])
            st, hd, ns = jget("https://gitlab.com/api/v4/namespaces/" + urllib.parse.quote(path.rsplit("/", 1)[0], safe=""), host="raw")
            if ns and ns.get("avatar_url"): urls.append(ns["avatar_url"])
        elif a["host"] == "codeberg":
            path = aid.split(":", 1)[1]
            st, hd, pj = jget("https://codeberg.org/api/v1/repos/" + path, host="raw")
            if pj:
                if pj.get("avatar_url"): urls.append(pj["avatar_url"])
                br = pj.get("default_branch") or "main"
                st, hd, body = http("https://codeberg.org/%s/raw/branch/%s/README.md" % (path, br), host="raw")
                if st == 200 and body: urls += _readme_images("https://codeberg.org/%s/raw/branch/%s/" % (path, br), body.decode("utf-8", "ignore")[:60000])
                if (pj.get("owner") or {}).get("avatar_url"): urls.append(pj["owner"]["avatar_url"])
    except Exception as e: log("related urls failed", aid, repr(e)[:60])
    for u in urls[:8]:
        im = _fetch_img(u)
        if im is None: continue
        sq = _square(im)
        if sq is not None: return sq
    return None

def related_image_fd(pk, S):
    urls = []
    for pth in ((S.get("shots") or {}).get("list") or {}).get(pk, [])[:2]: urls.append(FD_REPO + urllib.parse.quote(pth))
    urls += ["%s/%s/en-US/featureGraphic.png" % (FD_REPO.rstrip("/"), pk), "%s/%s/en-US/icon.png" % (FD_REPO.rstrip("/"), pk)]
    for u in urls:
        im = _fetch_img(u)
        if im is None: continue
        sq = _square(im)
        if sq is not None: return sq
    return None

def letter_tile(name, key):
    """generated placeholder (128px webp tile with the first letter). Only used when no real icon can be extracted."""
    from PIL import Image, ImageDraw, ImageFont
    h = int(hashlib.sha1(key.encode("utf-8")).hexdigest()[:6], 16)
    pal = [(91, 63, 212), (13, 148, 136), (217, 119, 6), (190, 24, 93), (37, 99, 235), (101, 163, 13), (124, 58, 237), (220, 38, 38)]
    col = pal[h % len(pal)]
    ch = next((c for c in (name or "") if c.isascii() and c.isalnum()), None) or "K"
    im = Image.new("RGB", (128, 128), col); d = ImageDraw.Draw(im)
    try: f = ImageFont.load_default(size=72)
    except Exception: f = ImageFont.load_default()
    try:
        bb = d.textbbox((0, 0), ch.upper(), font=f); w, hh = bb[2] - bb[0], bb[3] - bb[1]
        d.text(((128 - w) / 2 - bb[0], (128 - hh) / 2 - bb[1]), ch.upper(), fill=(255, 255, 255), font=f)
    except Exception: d.text((50, 50), ch.upper(), fill=(255, 255, 255))
    return im

def _top_apk(a):
    vs = [v for v in a["versions"] if not v.get("gone") and not v.get("pre")] or a["versions"]
    top = [v for v in vs if v["tag"] == vs[0]["tag"]]
    top.sort(key=lambda v: (0 if re.search(r"arm64|universal|all", v["file"], re.I) else 1 if not re.search(r"x86", v["file"], re.I) else 2, v.get("size") or 0))
    return top[0]["url"] if top else None

_import_budget = [None]
def _apk_try(url, full_mb):
    """APK's own icon, one retry on failure. returns (im, bytes_read, how)"""
    import apk_icon
    tot = 0
    for i in range(2):
        im, got, how = apk_icon.extract(url, full_mb); tot += got
        if im is not None: return im, tot, how
    return None, tot, "fail"

def icon_now(S, a):
    """called right when a new app is imported: icon in the same step. Order: 1) the APK's own icon (one retry),
    2) repo icon (GitHub), 3) related image (README, preview, avatars). Whatever fails is left for the sweep."""
    if Image is None or a.get("ic"): return
    try: import apk_icon
    except Exception: return
    if _import_budget[0] is None: _import_budget[0] = CFG.get("import_icon_mb_per_run", 400) * 1024 * 1024
    if out_of_time() or _import_budget[0] <= 0: return
    try:
        url = _top_apk(a)
        if url:
            im, got, how = _apk_try(url, CFG.get("apk_icon_full_mb", 30)); _import_budget[0] -= got
            if im is not None:
                _save_icon(im, icon_file(a["id"])); a["ic"] = 1; a["r"] = S["rev"] + 1; log("APKICON at import", a["id"], how); return
        if a["host"] == "github" and make_icon(a["id"]): a["ic"] = 1; a["r"] = S["rev"] + 1; log("repo icon at import", a["id"]); return
        ri = related_image(a, S)
        if ri is not None:
            _save_icon(ri, icon_file(a["id"])); a["ic"] = 1; a["ph"] = 2; a["aic"] = int(time.time()); a["r"] = S["rev"] + 1; log("RELATED image at import", a["id"])
    except Exception as e: log("icon at import failed", a["id"], repr(e)[:80])

def apk_icons_pass(S):
    """sweep of every app that still has no icon (or only a generated placeholder), fair round-robin over sources:
    GitHub, GitLab, Codeberg and the F-Droid-style list. After 3 failed attempts a letter tile is stored so nothing stays blank;
    real extraction is retried every 30 days for placeholders."""
    try: import apk_icon
    except Exception as e: log("apk_icon unavailable", e); return
    full_mb = CFG.get("apk_icon_full_mb", 30); limit = CFG.get("apk_icon_sweep_per_run", 400)
    deadline = min(START + CFG.get("apk_icon_hard_stop_minutes", 54) * 60, time.time() + CFG.get("apk_icon_minutes", 12) * 60)
    budget = [CFG.get("apk_icon_mb_per_run", 1500) * 1024 * 1024]; n = ok = ph = rel = 0; now_t = time.time()
    # queues per source
    qs = {"github": [], "gitlab": [], "codeberg": [], "fd": []}
    for a in sorted(S["apps"].values(), key=lambda a: a["added"], reverse=True):
        if not a["versions"]: continue
        real_missing = (not a.get("ic")) or a.get("ph")
        if not real_missing: continue
        last = a.get("aic") or 0
        if a.get("ph"):
            if now_t - last < 30 * 86400: continue
        elif last and now_t - last < 3 * 86400: continue
        qs.setdefault(a["host"], []).append(("app", a))
    fd = S.setdefault("fdic", {}); fdf = S.setdefault("fdfail", {}); f = os.path.join(ROOT, "crawler", "fd_noicon.tsv")
    if os.path.exists(f):
        for ln in open(f, encoding="utf-8"):
            pk, _, url = ln.rstrip("\n").partition("\t")
            if not url: continue
            fn = "fd_" + re.sub(r"[^A-Za-z0-9._-]", "_", pk) + ".webp"
            st = fd.get(pk)
            if st == 1 and os.path.exists(os.path.join(OUT, "icons", fn)): continue
            fl = fdf.get(pk) or [0, 0]
            if st == 2 and now_t - fl[1] < 30 * 86400: continue
            if st != 2 and fl[1] and now_t - fl[1] < 3 * 86400: continue
            qs["fd"].append(("fd", (pk, url, fn)))
    order = [k for k in ("github", "gitlab", "codeberg", "fd") if qs.get(k)]
    idx = {k: 0 for k in order}
    while order and n < limit and time.time() < deadline and budget[0] > 0:
        for k in list(order):
            if idx[k] >= len(qs[k]): order.remove(k); continue
            kind, it = qs[k][idx[k]]; idx[k] += 1; n += 1
            try:
                if kind == "app":
                    a = it; url = _top_apk(a); im, got, how = _apk_try(url, full_mb) if url else (None, 0, "no-url"); budget[0] -= got
                    if im is not None:
                        _save_icon(im, icon_file(a["id"])); a["ic"] = 1; a["ph"] = 0; a["r"] = S["rev"] + 1; ok += 1; log("APKICON", a["id"], how, got // 1024, "KB")
                    elif not a.get("ic") and a["host"] == "github" and make_icon(a["id"]):
                        a["ic"] = 1; a["ph"] = 0; a["r"] = S["rev"] + 1; ok += 1; log("repo icon", a["id"])
                    elif not a.get("ic") and (ri := related_image(a, S)) is not None:
                        _save_icon(ri, icon_file(a["id"])); a["ic"] = 1; a["ph"] = 2; a["aic"] = int(time.time()); a["r"] = S["rev"] + 1; rel += 1; log("RELATED image", a["id"])
                    else:
                        a["aic"] = int(time.time()); a["aicn"] = a.get("aicn", 0) + 1
                        if not a.get("ic") and a["aicn"] >= 3:
                            _save_icon(letter_tile(a["name"], a["id"]), icon_file(a["id"])); a["ic"] = 1; a["ph"] = 1; a["r"] = S["rev"] + 1; ph += 1; log("placeholder", a["id"])
                else:
                    pk, url, fn = it; im, got, how = _apk_try(url, full_mb); budget[0] -= got
                    fl = fdf.setdefault(pk, [0, 0])
                    if im is not None:
                        _save_icon(im, fn); fd[pk] = 1; fl[0] = 0; ok += 1; log("APKICON fd", pk, how, got // 1024, "KB")
                    elif fd.get(pk) != 2 and (ri := related_image_fd(pk, S)) is not None:
                        _save_icon(ri, fn); fd[pk] = 2; fl[1] = int(time.time()); rel += 1; log("RELATED image fd", pk)
                    else:
                        fl[0] += 1; fl[1] = int(time.time())
                        if fl[0] >= 3 and fd.get(pk) != 1: _save_icon(letter_tile(pk.split(".")[-1], pk), fn); fd[pk] = 2; ph += 1; log("placeholder fd", pk)
            except Exception as e: log("icon sweep error", repr(e)[:80])
            if n >= limit or time.time() >= deadline or budget[0] <= 0: break
    log("apk icons sweep: tried", n, "ok", ok, "related-image", rel, "letter tiles", ph, "remaining queues", {k: len(qs[k]) - idx.get(k, len(qs[k])) for k in qs})

# ---------- screenshots (F-Droid main repo; small webp copies, hard size caps) ----------
FD_REPO = "https://f-droid.org/repo"
def _dir_bytes(d):
    t = 0
    for r, _, fs in os.walk(d):
        for f in fs:
            try: t += os.path.getsize(os.path.join(r, f))
            except OSError: pass
    return t

def _fdroid_shot_index(S):
    """one streamed download of the F-Droid index (about 75 MB, at most once a week), keeping only the first 2 phone screenshots per baked app"""
    sh = S.setdefault("shots", {"idx_t": 0, "list": {}, "done": {}})
    if sh["list"] and time.time() - sh["idx_t"] < 7 * 86400: return
    import tempfile
    pks = set()
    f = os.path.join(ROOT, "crawler", "baked.tsv")
    if os.path.exists(f):
        for ln in open(f, encoding="utf-8"):
            p = ln.split("\t")
            if len(p) > 11 and p[11] == "0": pks.add(p[0])
    if not pks: return
    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".json"); tmp.close()
    try:
        r = urllib.request.urlopen(urllib.request.Request(FD_REPO + "/index-v2.json", headers={"User-Agent": UA}), timeout=120)
        n = 0
        with open(tmp.name, "wb") as o:
            while True:
                b = r.read(1 << 20)
                if not b: break
                n += len(b); o.write(b)
                if n > 200 * 1024 * 1024: raise IOError("index too large")
        d = json.load(open(tmp.name, encoding="utf-8"))
        lst = {}
        for pk, v in (d.get("packages") or {}).items():
            if pk not in pks: continue
            ph = ((v.get("metadata") or {}).get("screenshots") or {}).get("phone") or {}
            loc = ph.get("en-US") or (next(iter(ph.values())) if ph else None)
            if loc: lst[pk] = [x["name"] for x in loc[:2] if isinstance(x, dict) and x.get("name", "").startswith("/")]
        del d
        sh["list"] = {k: v for k, v in lst.items() if v}; sh["idx_t"] = int(time.time())
        log("shots index:", len(sh["list"]), "apps with screenshots")
    except Exception as e: log("shots index failed:", repr(e))
    finally:
        try: os.unlink(tmp.name)
        except OSError: pass

def shots_pass(S):
    per_run = CFG.get("shots_apps_per_run", 120)
    if per_run <= 0: return
    try:
        from PIL import Image
        import io
        sys.path.insert(0, os.path.join(ROOT, "crawler")); import pages
    except Exception as e: log("shots unavailable", repr(e)); return
    deadline = min(START + CFG.get("apk_icon_hard_stop_minutes", 54) * 60, time.time() + CFG.get("shots_minutes", 8) * 60)
    _fdroid_shot_index(S)
    sh = S["shots"]; done = sh["done"]; cap = CFG.get("shots_max_mb", 120) * 1024 * 1024
    sd = os.path.join(OUT, "shots"); total = _dir_bytes(sd) if os.path.isdir(sd) else 0; n = ok = 0
    for pk, paths in sh["list"].items():
        if n >= per_run or time.time() > deadline or total >= cap: break
        st = done.get(pk)
        if st and (st > 0 or time.time() + st < 30 * 86400): continue
        n += 1; saved = 0; sl = pages.slug(pk)
        for i, path in enumerate(paths[:2], 1):
            try:
                r = urllib.request.urlopen(urllib.request.Request(FD_REPO + urllib.parse.quote(path), headers={"User-Agent": UA}), timeout=40)
                raw = r.read(4 * 1024 * 1024 + 1)
                if len(raw) > 4 * 1024 * 1024: continue
                im = Image.open(io.BytesIO(raw)).convert("RGB"); im.thumbnail((320, 640), Image.LANCZOS)
                buf = io.BytesIO(); q = 55; im.save(buf, "WEBP", quality=q, method=6)
                while buf.tell() > 45 * 1024 and q > 25: q -= 10; buf = io.BytesIO(); im.save(buf, "WEBP", quality=q, method=6)
                if buf.tell() > 70 * 1024: continue
                os.makedirs(os.path.join(sd, sl), exist_ok=True)
                with open(os.path.join(sd, sl, "%d.webp" % i), "wb") as o: o.write(buf.getvalue())
                total += buf.tell(); saved += 1
            except Exception as e: log("shot failed", pk, repr(e)[:80])
        if saved: done[pk] = saved; ok += 1
        else: done[pk] = -int(time.time())
    log("screenshots this run: apps tried", n, "ok", ok, "total KB", total // 1024)

# ---------- duplicates ----------
def detect_dups(S):
    owner_of = {}
    for a in sorted(S["apps"].values(), key=lambda a: (a["added"], a["id"])):
        a["dup"] = None; a["maybe_dup"] = None
    for a in sorted(S["apps"].values(), key=lambda a: (a["added"], a["id"])):
        for v in a["versions"]:
            o = owner_of.setdefault(v["sha"], a["id"])
            if o != a["id"]:
                a["dup"] = o      # identical APK bytes already published by an older entry
    by_name = {}
    for a in S["apps"].values():
        if a["dup"]: continue
        by_name.setdefault(norm_name(a["name"]), []).append(a)
    for n, lst in by_name.items():
        if len(lst) > 1 and n:
            lst.sort(key=lambda a: -a["stars"])
            for a in lst[1:]: a["maybe_dup"] = lst[0]["id"]
    stats["duplicates"] = sum(1 for a in S["apps"].values() if a["dup"])

# ---------- publish ----------
def public(a):
    return {"id": a["id"], "h": a["host"], "n": a["name"], "d": a["desc"], "l": a["lic"], "s": a["stars"], "u": a["url"], "c": a["cat"],
            "o": a["owner"], "ic": a.get("ic", 0), "add": a["added"], "r": a["r"], "dup": a["dup"], "md": a["maybe_dup"],
            "arch": a.get("archived", 0) or a.get("gone", 0),
            "v": [[v["tag"], v["date"], v["file"], v["size"], v["sha"], v["url"], v["hs"], v.get("pre", 0), v.get("gone", 0), v.get("sup", 0)] for v in a["versions"]]}

PUB = []

def self_entry():
    """the store lists itself: version/size/sha come from docs/app.json (same file the in-app self-update uses)"""
    try:
        j = json.load(open(os.path.join(OUT, "app.json"), encoding="utf-8"))
        if not os.path.exists(os.path.join(OUT, j.get("file", "kanz.apk"))): return None
        ver = re.match(r"[\d.]+", j["version"]).group(0)
        _ap = os.path.join(OUT, j.get("file", "kanz.apk"))   # hash/size come from the real file so the catalog can never disagree with it
        j["size"] = os.path.getsize(_ap); j["sha256"] = hashlib.sha256(open(_ap, "rb").read()).hexdigest()
        iid = "ibra8y3-web/kanz-catalog"; ico = os.path.join(OUT, "icons", icon_file(iid)); src = os.path.join(OUT, "icon.png")
        # the store's own icon follows the APK's launcher icon; refreshed whenever the APK's sha changes (marker file), icon.png is only a fallback
        mark = ico + ".sha"
        prev = open(mark, encoding="utf-8").read().strip() if os.path.exists(mark) else ""
        if prev != j["sha256"]:
            im = None
            try:
                import zipfile, apk_icon
                im = apk_icon._from_zip(zipfile.ZipFile(_ap))
            except Exception as e:
                log("self apk icon failed", repr(e))
            if im is None and os.path.exists(src):
                from PIL import Image
                im = Image.open(src).convert("RGBA"); im.thumbnail((128, 128))
            if im is not None:
                _save_icon(im, icon_file(iid)); open(mark, "w", encoding="utf-8").write(j["sha256"])
        url = "https://raw.githubusercontent.com/ibra8y3-web/kanz-catalog/main/docs/" + j.get("file", "kanz.apk")
        return {"id": iid, "h": "github", "n": "كنز (Kanz)", "d": "متجر تطبيقات مفتوحة المصدر بالعربي. من هنا بتحدّث المتجر نفسه.",
                "l": "FOSS", "s": 0, "u": "https://ibra8y3-web.github.io/kanz-catalog/", "c": "أدوات النظام", "o": "kanz",
                "ic": 1 if os.path.exists(ico) else 0, "add": "2026-11-26", "r": 0, "dup": None, "md": None, "arch": 0,
                "v": [["v" + ver, now()[:10], j.get("file", "kanz.apk"), int(j["size"]), j["sha256"], url, "api", 0, 0, 0]]}
    except Exception as e:
        log("self entry skipped", repr(e)); return None

def publish(S, changed):
    if changed: S["rev"] += 1
    rev = S["rev"]
    for a in S["apps"].values():
        if a["r"] == 0 or a["r"] > rev: a["r"] = rev
    detect_dups(S)
    apps = [public(a) for a in sorted(S["apps"].values(), key=lambda a: a["id"].lower()) if a["versions"]]
    se = self_entry()
    if se:
        sg = json.dumps(se["v"][0][0:5])
        if S.get("self_sig") != sg: S["self_sig"] = sg; S["self_r"] = rev
        se["r"] = S.get("self_r", rev); apps.append(se); apps.sort(key=lambda x: x["id"].lower())
    PUB[:] = apps
    full = {"fmt": 1, "rev": rev, "gen": now(), "count": len(apps), "apps": apps}
    base = max(0, rev - CFG["delta_window_revs"])
    delta = {"fmt": 1, "rev": rev, "base": base, "gen": full["gen"], "apps": [x for x in apps if x["r"] > base]}
    os.makedirs(OUT, exist_ok=True)
    def w(name, obj):
        b = json.dumps(obj, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        with gzip.GzipFile(os.path.join(OUT, name + ".gz"), "wb", compresslevel=9, mtime=0) as f: f.write(b)
        return len(b)
    raw = w("catalog.json", full); w("delta.json", delta)
    meta = {"fmt": 1, "rev": rev, "gen": full["gen"], "count": len(apps),
            "full_bytes": os.path.getsize(os.path.join(OUT, "catalog.json.gz")), "delta_bytes": os.path.getsize(os.path.join(OUT, "delta.json.gz")),
            "delta_base": base, "versions": sum(len(x["v"]) for x in apps)}
    json.dump(meta, open(os.path.join(OUT, "meta.json"), "w"), indent=1)
    return meta

_last_sig = [None]
def sig(S): return json.dumps({k: [v["r"], len(v["versions"])] for k, v in S["apps"].items()}, sort_keys=True)
def checkpoint(S):
    cur = sig(S); publish(S, cur != _last_sig[0]); save_state(S); _last_sig[0] = cur

def sweep_slices():
    y0 = CFG.get("sweep_first_year", 2012); y1 = datetime.datetime.utcnow().year; out = []
    for q in CFG.get("sweep_queries", []):
        for y in range(y1, y0 - 1, -1):
            if y >= 2016:
                for a, b in (("01-01", "03-31"), ("04-01", "06-30"), ("07-01", "09-30"), ("10-01", "12-31")):
                    out.append("%s created:%d-%s..%d-%s" % (q, y, a, y, b))
            else: out.append("%s created:%d-01-01..%d-12-31" % (q, y, y))
    return out

def sweep(S):
    """walk the whole GitHub backlog in date slices (each slice stays under the 1000-result search cap); resumes across runs"""
    done = S.setdefault("sweep_done", {}); deadline = START + CFG.get("sweep_minutes", 26) * 60; n = 0
    for sl in sweep_slices():
        if time.time() > deadline or out_of_time(): break
        if sl in done: continue
        try: r = gh_search(S, sl, CFG.get("sweep_pages_per_slice", 10), None, sort_stars=False)
        except Stop as e: log("sweep stopped:", e); break
        if r is None or out_of_time() or time.time() > deadline + 600: break
        done[sl] = now(); n += 1; checkpoint(S)
    log("sweep slices done this run:", n, "total:", len(done))

def main():
    S = load_state()
    S.setdefault("disc", {}); S.setdefault("pending", {})
    first = not S["apps"]
    _last_sig[0] = sig(S)
    try:
        for o in CFG["seed_owners"]: gh_user_repos(S, o)
        for r in CFG["seed_repos"]:
            st, hd, o = jget("https://api.github.com/repos/" + r)
            if st == 200: process_github_candidate(S, gh_meta(o))
        try: process_submissions(S)
        except Stop: raise
        except Exception as e: log("submissions failed:", repr(e))
        gh_recheck(S)
        pages = CFG["pages_per_query_first_run"] if first else CFG["pages_per_query_next_runs"]
        for q in CFG["github_queries"]:
            if out_of_time(): break
            since = S["disc"].get(q)
            if since:   # incremental: a little overlap
                d = datetime.datetime.strptime(since, "%Y-%m-%dT%H:%M:%SZ") - datetime.timedelta(days=1)
                since_q = d.strftime("%Y-%m-%d")
            else: since_q = None
            gh_search(S, q, pages, since_q)
            S["disc"][q] = now()
            checkpoint(S)
        sweep(S)
        refresh_meta(S)
        icons_pass(S)
        checkpoint(S)
    except Stop as e:
        log("stopped early:", e)
    # APK icon extraction downloads release files (no API quota), so it runs even if the API quota stopped the crawl
    try:
        apk_icons_pass(S)
    except Exception as e:
        log("apk icons pass failed:", repr(e))
    try: shots_pass(S)
    except Exception as e: log("screenshots pass failed:", repr(e))
    try: checkpoint(S)
    except Exception as e: log("checkpoint failed", repr(e))
    for fn in (gl_run, cb_run):
        try:
            if not out_of_time(): fn(S)
        except Stop as e: log("stopped", fn.__name__, e)
    cur = sig(S); meta = publish(S, cur != _last_sig[0]); save_state(S)
    try:
        import pages
        w, tot = pages.generate(PUB, OUT, icon_file, CFG.get("max_pages"))
        log("pages: wrote", w, "of", tot)
    except Exception as e: log("pages failed:", repr(e))
    log("DONE", json.dumps(stats), json.dumps(meta))

if __name__ == "__main__": main()
