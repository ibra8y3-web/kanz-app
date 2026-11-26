import os, json, urllib.request, urllib.error
def call(method, path, body=None):
    req = urllib.request.Request("https://api.github.com" + path, method=method, data=json.dumps(body).encode() if body is not None else None)
    req.add_header("Accept", "application/vnd.github+json"); req.add_header("User-Agent", "kanz-bot")
    t = os.environ.get("GITHUB_TOKEN")
    if t: req.add_header("Authorization", "Bearer " + t)
    if body is not None: req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=30) as r: return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        try: return e.code, json.loads(e.read() or b"null")
        except Exception: return e.code, None
    except Exception: return 0, None
def comment(repo, num, text): return call("POST", "/repos/%s/issues/%d/comments" % (repo, num), {"body": text})
def close(repo, num, reason="completed"): return call("PATCH", "/repos/%s/issues/%d" % (repo, num), {"state": "closed", "state_reason": reason})
