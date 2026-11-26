"""Runs when someone opens a 'submit-app' issue. Treats the issue text as data only: classifies the submitted link
(GitHub / GitLab / Codeberg / F-Droid / direct APK) with strict patterns and queues it in data/submissions.json.
The crawler verifies license/APK/SHA/permissions and reports back on the issue."""
import os, sys, re, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ghapi
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
QF = os.path.join(ROOT, "data", "submissions.json")
PAT_GH = re.compile(r"(?:https?://github\.com/)?([A-Za-z0-9](?:[A-Za-z0-9_-]{0,38}))/([A-Za-z0-9_.-]{1,100})/?$")
PAT_GL = re.compile(r"https?://gitlab\.com/([A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)+)/?$")
PAT_CB = re.compile(r"https?://codeberg\.org/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)/?$")
PAT_FD = re.compile(r"https?://(?:www\.)?f-droid\.org/(?:[a-z]{2}(?:-[A-Za-z]+)?/)?packages/([A-Za-z0-9_.]+)/?")
PAT_APK = re.compile(r"https://([A-Za-z0-9.-]+)/\S*\.apk(?:\?\S*)?$", re.I)

def classify(line):
    line = line.strip().rstrip("/").removesuffix(".git")
    m = PAT_GH.match(line)
    if m: return "github", m.group(1) + "/" + m.group(2)
    m = PAT_GL.match(line)
    if m: return "gitlab", m.group(1)
    m = PAT_CB.match(line)
    if m: return "codeberg", m.group(1)
    m = PAT_FD.match(line)
    if m: return "fdroid", m.group(1)
    m = PAT_APK.match(line)
    if m: return "apk", line
    return None, None

def main():
    ev = json.load(open(os.environ["GITHUB_EVENT_PATH"], encoding="utf-8")); iss = ev["issue"]; num = int(iss["number"]); me = os.environ.get("GITHUB_REPOSITORY", "")
    sender = (iss.get("user") or {}).get("login", "")
    body = iss.get("body") or ""
    lines = [l.strip() for l in re.split(r"###[^\n]*\n+", body) if l.strip() and not l.strip().startswith(("_", "متجر", "The store", "حط ", "Paste ", "الفحص"))]
    link_line = lines[0].split("\n")[0].strip() if lines else ""
    name = lines[1].split("\n")[0].strip() if len(lines) > 1 else ""
    if name.lower().startswith("_no response"): name = ""
    typ, link = classify(link_line)
    if not typ:
        ghapi.comment(me, num, "ما قدرتش أقرأ الرابط. المصادر المقبولة: GitHub (owner/repo)، GitLab، Codeberg، F-Droid، أو رابط APK مباشر HTTPS. | Could not read the link. Accepted sources: GitHub (owner/repo), GitLab, Codeberg, F-Droid, or a direct HTTPS APK link."); ghapi.close(me, num, "not_planned"); return
    if typ == "apk":
        ghapi.comment(me, num, "المتجر مفتوح المصدر بس: ممنوع التقديم برابط APK من غير كود مصدر عام. قدّم رابط المستودع العام (GitHub / GitLab / Codeberg) أو صفحة التطبيق على F-Droid بدلها. | Kanz is open source only: an APK link without public source code is not accepted. Submit the public repo (GitHub / GitLab / Codeberg) or the app's F-Droid page instead."); ghapi.close(me, num, "not_planned"); return
    if typ == "github":
        st, o = ghapi.call("GET", "/repos/" + link)
        if st != 200 or not o or o.get("private"):
            ghapi.comment(me, num, "المستودع %s مش موجود أو مش عام. | Repository %s was not found or is not public." % (link, link)); ghapi.close(me, num, "not_planned"); return
        link = o["full_name"]
    q = json.load(open(QF, encoding="utf-8")) if os.path.exists(QF) else []
    if any((x.get("link") or x.get("repo", "")).lower() == link.lower() for x in q):
        ghapi.comment(me, num, "الطلب ده متقدّم قبل كده. | This app was already submitted."); ghapi.close(me, num, "not_planned"); return
    if sum(1 for x in q if x.get("sender") == sender) >= 5:
        ghapi.comment(me, num, "وصلت للحد الأقصى من الطلبات المفتوحة (5). استنى نتيجة طلباتك الأول. | You reached the maximum of 5 open submissions. Please wait for your earlier ones."); ghapi.close(me, num, "not_planned"); return
    if len(q) > 5000: return
    q.append({"type": typ, "link": link, "name": name, "issue": num, "sender": sender}); os.makedirs(os.path.dirname(QF), exist_ok=True)
    json.dump(q, open(QF, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    ghapi.comment(me, num, "تمام، %s اتحط في الطابور. هيتفحص (ترخيص + APK + SHA-256 + صلاحيات) في التشغيل الجاي للمحرك (خلال حوالي 30 دقيقة) وهرد هنا بالنتيجة. تطبيقات المطورين الجدد بتستنى موافقة صاحب المتجر. | %s is queued and will be checked on the next crawler run (about 30 minutes). Apps from new developers also wait for the store owner's approval." % (link, link))
main()
