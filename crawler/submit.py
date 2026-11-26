"""Runs when someone opens a 'submit-app' issue. Treats the issue text as data only: extracts one owner/repo with a strict pattern,
checks it exists and is public, and queues it in data/submissions.json. The crawler verifies license/APK/SHA and reports back on the issue."""
import os, sys, re, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ghapi
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
QF = os.path.join(ROOT, "data", "submissions.json")
PAT = re.compile(r"(?:https?://github\.com/)?([A-Za-z0-9](?:[A-Za-z0-9_-]{0,38}))/([A-Za-z0-9_.-]{1,100})")
def main():
    ev = json.load(open(os.environ["GITHUB_EVENT_PATH"], encoding="utf-8")); iss = ev["issue"]; num = int(iss["number"]); me = os.environ.get("GITHUB_REPOSITORY", "")
    body = iss.get("body") or ""
    m = re.search(r"###[^\n]*\n+([^\n]+)", body); line = (m.group(1) if m else body.split("\n")[0]).strip()
    mm = PAT.fullmatch(line.rstrip("/").removesuffix(".git")) if line else None
    if not mm:
        ghapi.comment(me, num, "ما قدرتش أقرأ اسم المستودع. اكتبه بالشكل owner/repo أو رابط GitHub. | Could not read the repository. Use owner/repo or a GitHub URL."); ghapi.close(me, num, "not_planned"); return
    repo = mm.group(1) + "/" + mm.group(2)
    st, o = ghapi.call("GET", "/repos/" + repo)
    if st != 200 or not o or o.get("private"):
        ghapi.comment(me, num, "المستودع %s مش موجود أو مش عام. | Repository %s was not found or is not public." % (repo, repo)); ghapi.close(me, num, "not_planned"); return
    repo = o["full_name"]
    q = json.load(open(QF, encoding="utf-8")) if os.path.exists(QF) else []
    if any(x["repo"].lower() == repo.lower() for x in q):
        ghapi.comment(me, num, "المستودع ده متقدّم قبل كده. | This repository was already submitted."); ghapi.close(me, num, "not_planned"); return
    if len(q) > 5000: return
    q.append({"repo": repo, "issue": num}); os.makedirs(os.path.dirname(QF), exist_ok=True)
    json.dump(q, open(QF, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    ghapi.comment(me, num, "تمام، %s اتحط في الطابور. هيتفحص (ترخيص مفتوح + Release فيه APK + SHA-256) في التشغيل الجاي للمحرك (خلال حوالي 30 دقيقة) وهرد هنا بالنتيجة. | %s is queued and will be checked on the next crawler run (about 30 minutes). The result will be posted here." % (repo, repo))
main()
