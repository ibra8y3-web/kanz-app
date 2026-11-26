"""Offline logic test with a fake GitHub. Run: python tests/test_offline.py"""
import os, sys, json, tempfile, gzip, importlib
d = tempfile.mkdtemp()
cfg = json.load(open(os.path.join(os.path.dirname(__file__), "..", "config.json")))
cfg.update(github_queries=["q1"], gitlab_topics=[], codeberg_topics=[], seed_owners=[], seed_repos=[], pages_per_query_first_run=1, pages_per_query_next_runs=1, apk_icons_per_run=0, sweep_queries=[], max_pages=20, shots_apps_per_run=0, apk_icon_sweep_per_run=0, import_icon_mb_per_run=0)
json.dump(cfg, open(d + "/c.json", "w"))
os.environ.update(KANZ_CONFIG=d + "/c.json", KANZ_STATE=d + "/s.json", KANZ_OUT=d + "/out")
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "crawler"))
import crawl as c
c.time.sleep = lambda x: None

def repo(full, lic="MIT", fork=False, desc="A notes app", topics=("android",), arch=False):
    o, n = full.split("/")
    return {"full_name": full, "name": n, "owner": {"login": o, "avatar_url": ""}, "description": desc, "topics": list(topics), "license": {"spdx_id": lic} if lic else None,
            "archived": arch, "fork": fork, "stargazers_count": 10, "html_url": "https://github.com/" + full, "pushed_at": "2026-01-01T00:00:00Z"}
def rel(tag, files, pre=False):
    return {"tag_name": tag, "published_at": "2026-0%s-01T00:00:00Z" % tag[-1], "prerelease": pre, "draft": False,
            "assets": [{"name": f, "size": 1000, "browser_download_url": "https://x/%s/%s" % (tag, f), "digest": ("sha256:" + h) if h else None} for f, h in files]}
REPOS = [repo("a/notes"), repo("b/proprietary", lic=None), repo("c/fork", fork=True), repo("d/modded-app", desc="premium unlocked mod"),
         repo("e/nodigest"), repo("f/dupe-of-a"), repo("g/norel")]
RELS = {"a/notes": [rel("v1", [("notes.apk", "aa" * 32)])],
        "b/proprietary": [rel("v1", [("p.apk", "bb" * 32)])], "c/fork": [rel("v1", [("f.apk", "cc" * 32)])],
        "d/modded-app": [rel("v1", [("m.apk", "dd" * 32)])], "e/nodigest": [rel("v1", [("n.apk", None), ("n-debug.apk", "ee" * 32)])],
        "f/dupe-of-a": [rel("v1", [("notes.apk", "aa" * 32)])], "g/norel": []}
def fake_jget(url, etag=None, host="github"):
    if "search/repositories" in url: return 200, {}, {"items": REPOS}
    for k, v in RELS.items():
        if "/repos/%s/releases" % k in url:
            if etag == "E" + (v[0]["tag_name"] if v else "0"): return 304, {}, None
            return 200, {"ETag": "E" + (v[0]["tag_name"] if v else "0")}, v
    return 404, {}, None
c.jget = fake_jget
c.sha256_url = lambda u, h, s: "11" * 32
def run():
    c.stats.update({k: 0 for k in c.stats}); c.START = c.time.time(); c.main()
    return json.load(open(d + "/s.json")), json.load(gzip.open(d + "/out/catalog.json.gz"))
S, cat = run()
ids = {a["id"]: a for a in cat["apps"]}
assert set(ids) == {"a/notes", "e/nodigest", "f/dupe-of-a"}, set(ids)
assert S["rejected"]["b/proprietary"]["why"].startswith("license"), S["rejected"]
assert S["rejected"]["c/fork"]["why"] == "fork" and "mod" in S["rejected"]["d/modded-app"]["why"]
assert ids["f/dupe-of-a"]["dup"] == "a/notes"
assert ids["e/nodigest"]["v"][0][6] == "computed" and len(ids["e/nodigest"]["v"]) == 1   # debug apk skipped
assert cat["rev"] == 1
# new release arrives + old one removed upstream: old kept, flagged gone
RELS["a/notes"] = [rel("v2", [("notes.apk", "a2" * 32)])]
S, cat = run()
a = {x["id"]: x for x in cat["apps"]}["a/notes"]
assert len(a["v"]) == 2 and a["v"][0][0] == "v2" and a["v"][1][0] == "v1" and a["v"][1][8] == 1, a["v"]
assert cat["rev"] == 2
dl = json.load(gzip.open(d + "/out/delta.json.gz")); assert any(x["id"] == "a/notes" for x in dl["apps"])
# no change -> rev stays
S, cat = run(); assert cat["rev"] == 2, cat["rev"]
print("ALL OFFLINE TESTS PASSED", json.load(open(d + "/out/meta.json")))
