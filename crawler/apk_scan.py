"""Optional deep scan of a submission APK: package, permissions, signing cert, trackers.
Uses androguard when installed on the runner; otherwise reports unavailable (caller treats that as a doubt)."""
import os, tempfile, zipfile, urllib.request

def scan_apk(url, max_mb, trk_path, danger_perms):
    out = {"ok": False, "why": "", "package": "", "version_name": "", "version_code": 0,
           "perms": [], "danger": [], "trackers": [], "cert_sha256": ""}
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "kanz-bot"})
        data = b""
        with urllib.request.urlopen(req, timeout=180) as r:
            while True:
                b = r.read(1 << 20)
                if not b: break
                data += b
                if len(data) > max_mb * 1024 * 1024:
                    out["why"] = "too big"; return out
    except Exception as e:
        out["why"] = "download failed"; return out
    try:
        from androguard.core.apk import APK
    except Exception:
        out["why"] = "scanner unavailable"; return out
    path = None
    try:
        with tempfile.NamedTemporaryFile(suffix=".apk", delete=False) as f:
            f.write(data); path = f.name
        a = APK(path)
        out["package"] = a.get_package() or ""
        out["version_name"] = a.get_androidversion_name() or ""
        try: out["version_code"] = int(a.get_androidversion_code() or 0)
        except Exception: out["version_code"] = 0
        perms = list(a.get_permissions() or [])
        out["perms"] = perms
        out["danger"] = sorted({p for p in perms if p in danger_perms})
        try:
            certs = a.get_certificates()
            if certs:
                import hashlib
                try: out["cert_sha256"] = certs[0].sha256_fingerprint.replace(":", "").lower()
                except Exception:
                    from cryptography.hazmat.primitives import hashes
                    out["cert_sha256"] = certs[0].fingerprint(hashes.SHA256()).hex()
        except Exception: pass
        if not out["cert_sha256"]:
            try: out["cert_sha256"] = (a.get_signature() or "").lower()
            except Exception: pass
        try:
            pats = [l.rstrip("\n").split("\t") for l in open(trk_path, encoding="utf-8") if "\t" in l]
            zf = zipfile.ZipFile(path)
            blob = b"".join(zf.read(n) for n in zf.namelist() if n.endswith(".dex"))
            out["trackers"] = [name for name, pat in pats if ("L" + pat).encode() in blob]
        except Exception: pass
        out["ok"] = True
    except Exception as e:
        out["why"] = "scan failed"
    finally:
        if path:
            try: os.unlink(path)
            except Exception: pass
    return out
