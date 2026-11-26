"""Posts the outcome of each queued submission on its issue (once per status) and closes finished ones.
'review' submissions get a pending-review label for the store owner; labeling the issue 'approved' lets the next crawler run add the app."""
import os, sys, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ghapi
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RF = os.path.join(ROOT, "data", "submission_results.json")
def main():
    if not os.path.exists(RF): return
    me = os.environ.get("GITHUB_REPOSITORY", ""); R = json.load(open(RF, encoding="utf-8")); ch = False
    for key, r in R.items():
        st = r.get("status")
        if st not in ("accepted", "rejected", "review") or r.get("notified") == st: continue
        if st == "accepted":
            txt = "✅ اتضاف %s للمتجر. هيظهر في كنز بعد المزامنة القادمة. | %s was added to Kanz and will appear after the next catalog sync." % (key, key)
        elif st == "review":
            doubts = "؛ ".join(r.get("doubt") or [])
            txt = "⏳ %s عدى الفحوص الأساسية بس محتاج موافقة صاحب المتجر قبل الظهور. الملاحظات: %s. لو إنت صاحب المتجر: ضيف علامة approved للطلب عشان يدخل في التشغيل الجاي (حوالي 30 دقيقة). | %s passed the basic checks but waits for the store owner's approval. Notes: %s. Owner: add the approved label and it will be added on the next run." % (key, doubts, key, doubts)
        else:
            txt = "❌ مقدرناش نضيف %s. السبب: %s. لو صلحته افتح طلب جديد. | %s was not added. Reason: %s. Fix it and open a new request." % (key, r.get("why", ""), key, r.get("why", ""))
        code, _ = ghapi.comment(me, int(r["issue"]), txt)
        if code in (200, 201):
            if st == "review": ghapi.add_label(me, int(r["issue"]), "pending-review")
            else: ghapi.close(me, int(r["issue"]))
            r["notified"] = st; ch = True
    if ch: json.dump(R, open(RF, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
main()
