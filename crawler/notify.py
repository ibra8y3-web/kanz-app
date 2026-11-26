"""Posts the outcome of each queued submission on its issue (once) and closes the issue."""
import os, sys, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ghapi
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RF = os.path.join(ROOT, "data", "submission_results.json")
def main():
    if not os.path.exists(RF): return
    me = os.environ.get("GITHUB_REPOSITORY", ""); R = json.load(open(RF, encoding="utf-8")); ch = False
    for repo, r in R.items():
        if r.get("notified") or r.get("status") not in ("accepted", "rejected"): continue
        if r["status"] == "accepted":
            txt = "✅ اتضاف %s للمتجر. هيظهر في كنز بعد المزامنة القادمة. | %s was added to Kanz and will appear after the next catalog sync." % (repo, repo)
        else:
            txt = "❌ مقدرناش نضيف %s. السبب: %s. لو صلحته افتح طلب جديد. | %s was not added. Reason: %s. Fix it and open a new request." % (repo, r.get("why", ""), repo, r.get("why", ""))
        st, _ = ghapi.comment(me, int(r["issue"]), txt)
        if st in (200, 201):
            ghapi.close(me, int(r["issue"])); r["notified"] = True; ch = True
    if ch: json.dump(R, open(RF, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
main()
