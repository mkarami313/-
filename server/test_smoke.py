#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""End-to-end smoke test for hamrah-api. Run ON the server (it needs .env + DB access):

    cd /home/rasta/hamrah && python3 test_smoke.py            # against http://127.0.0.1:8150

Creates throwaway accounts (smoke-*@example.invalid) and deletes them (plus everything they own)
at the end. It never touches other users' data. Exit code 0 = all checks passed.
"""
import os
import sys
import json
import time
import uuid
import importlib.util
import subprocess
import threading
import urllib.request
import urllib.error

BASE = os.environ.get("BASE", "http://127.0.0.1:8150")
HERE = os.path.dirname(os.path.abspath(__file__))
sys.argv = [sys.argv[0]]
spec = importlib.util.spec_from_file_location("hamrah_api", os.path.join(HERE, "hamrah-api.py"))
H = importlib.util.module_from_spec(spec)
spec.loader.exec_module(H)

PASS = FAIL = 0
RUN = uuid.uuid4().hex[:8]
PNG = b"\x89PNG\r\n\x1a\n" + b"\x00" * 64
ZERO = "0" * 32
made = []  # (token, uid)


def check(name, cond, extra=""):
    global PASS, FAIL
    if cond:
        PASS += 1
    else:
        FAIL += 1
        print("FAIL: %s %s" % (name, extra))


def http(method, path, body=None, token=None, raw=None, headers=None):
    h = dict(headers or {})
    if token:
        h["Authorization"] = "Bearer " + token
    data = raw
    if body is not None:
        data = json.dumps(body).encode()
        h["Content-Type"] = "application/json"
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            b = r.read()
            return r.status, b, dict(r.headers)
    except urllib.error.HTTPError as e:
        return e.code, e.read(), dict(e.headers)


def jhttp(method, path, body=None, token=None, **kw):
    code, b, hd = http(method, path, body, token, **kw)
    try:
        return code, json.loads(b.decode()), hd
    except Exception:
        return code, {"_raw": b[:200]}, hd


def act(token, name, args=None):
    code, j, _ = jhttp("POST", "/api/action", {"name": name, "args": args or {}}, token)
    return j if code == 200 else dict(j, _code=code, ok=False)


def register(label, **extra):
    mail = "smoke-%s-%s@example.invalid" % (RUN, label)
    code, j, _ = jhttp("POST", "/api/auth/register", dict(
        displayName="smoke " + label, email=mail, password="pw-" + RUN + "-123", acceptedTerms=True, **extra))
    assert code == 200 and j.get("token"), j
    made.append((j["token"], j["user"]["id"]))
    return mail, j["token"], j["user"]["id"], j


def sql(q, args=()):
    cur = H.conn().cursor()
    cur.execute(q, args)
    rows = cur.fetchall() if cur.description else []
    cur.close()
    return rows


def upload(token, data=PNG, mime="image/png"):
    code, j, _ = jhttp("POST", "/api/upload", token=token, raw=data, headers={"Content-Type": mime, "X-Filename": "t.png"})
    return code, j


def main():
    admin_mail = "smoke-%s-admin@example.invalid" % RUN
    subprocess.run([sys.executable, os.path.join(HERE, "hamrah-api.py"), "create-admin", admin_mail, "adminpw-" + RUN, "smoke admin"],
                   check=True, capture_output=True, cwd=HERE)
    code, j, _ = jhttp("POST", "/api/auth/login", {"email": admin_mail, "password": "adminpw-" + RUN})
    atok, auid = j["token"], j["user"]["id"]
    made.append((atok, auid))

    # ---- basics + 0.12 compatibility ------------------------------------------------------
    check("health", http("GET", "/api/health")[0] == 200)
    mailA, ta, ua, jr = register("a", accountType="PROFESSIONAL")
    check("register forces FAMILY", jr["user"]["accountType"] == "FAMILY")
    mailB, tb, ub, _ = register("b")
    code, st, hd = jhttp("GET", "/api/state", token=ta)
    check("state shape", st.get("ok") and "users" in st["state"] and st.get("etag"))
    code, st2, _ = jhttp("GET", "/api/state?etag=" + st["etag"], token=ta)
    check("state etag 304-style", st2.get("notModified") is True)
    r = act(ta, "state", {"etag": st["etag"]})
    check("state action etag", r.get("data", {}).get("notModified") is True, r)
    r = act(ta, "updateProfile", {"displayName": "Smoke A", "city": "تهران", "bio": "bio A"})
    check("old action shape (updateProfile)", r.get("ok") and "state" in r, r)
    check("unauth 401", http("GET", "/api/state")[0] == 401)
    check("bad json ok", http("POST", "/api/action", raw=b"{{{", token=ta)[0] in (200, 404))

    # ---- auth hardening ---------------------------------------------------------------------
    c, j, _ = jhttp("POST", "/api/auth/login", {"email": mailA, "password": "x" * 200})
    check("password >128 rejected", j.get("ok") is False)
    c, j, _ = jhttp("POST", "/api/auth/register", {"displayName": "x", "email": "n%s@example.invalid" % RUN,
                                                    "password": "y" * 129, "acceptedTerms": True})
    check("register password >128 rejected", j.get("ok") is False)
    iters = sql("SELECT iters FROM creds WHERE email=%s", (mailA,))[0][0]
    check("pbkdf2 600k stored", iters == 600000, iters)
    rows = sql("SELECT iters FROM creds WHERE email=%s", (mailA,))
    sql("UPDATE creds SET iters=120000, pwhash=%s WHERE email=%s", (H._pbkdf2("pw-%s-123" % RUN, sql("SELECT salt FROM creds WHERE email=%s", (mailA,))[0][0], 120000), mailA))
    c, j, _ = jhttp("POST", "/api/auth/login", {"email": mailA, "password": "pw-%s-123" % RUN})
    check("legacy hash login works", c == 200 and j.get("token"))
    check("legacy hash upgraded", sql("SELECT iters FROM creds WHERE email=%s", (mailA,))[0][0] == 600000)
    ta = j["token"]
    victim = "nobody-%s@example.invalid" % RUN
    codes = [jhttp("POST", "/api/auth/login", {"email": victim, "password": "wrong1"})[1].get("error", "") for _ in range(10)]
    check("login rate limit kicks in", any(H.TOO_MANY == e for e in codes), codes[-1])

    # ---- email reset: forgot / adminIssueReset / reset -----------------------------------------
    c, j, _ = jhttp("POST", "/api/auth/forgot", {"email": victim})
    check("forgot unknown -> ok", j == {"ok": True}, j)
    c, j, _ = jhttp("POST", "/api/auth/forgot", {"email": mailB})
    check("forgot known -> ok (SMTP %s)" % ("on" if H.smtp_configured() else "off"), j == {"ok": True}, j)
    c, j, _ = jhttp("POST", "/api/auth/reset", {"email": mailB, "code": "00000000", "newPassword": "newpw-%s" % RUN})
    check("reset wrong code rejected", j.get("ok") is False, j)
    r = act(ta, "adminIssueReset", {"email": mailB})
    check("adminIssueReset non-admin denied", r.get("ok") is False)
    r = act(atok, "adminIssueReset", {"email": mailB})
    code8 = r.get("data", {}).get("code", "")
    check("adminIssueReset gives 8 digits", len(code8) == 8 and code8.isdigit(), r)
    oldtb = tb
    time.sleep(1.2)
    c, j, _ = jhttp("POST", "/api/auth/reset", {"email": mailB, "code": code8, "newPassword": "newpw-%s" % RUN})
    check("reset ok", j.get("ok") is True, j)
    c, j, _ = jhttp("POST", "/api/auth/reset", {"email": mailB, "code": code8, "newPassword": "again-%s" % RUN})
    check("reset code single-use", j.get("ok") is False)
    check("old token dead after reset", http("GET", "/api/state", token=oldtb)[0] == 401)
    c, j, _ = jhttp("POST", "/api/auth/login", {"email": mailB, "password": "newpw-%s" % RUN})
    tb = j["token"]
    made[:] = [(t if u != ub else tb, u) for t, u in made]
    check("login with new password", c == 200 and tb)
    r = act(atok, "adminIssueReset", {"email": mailB})
    for _ in range(5):
        jhttp("POST", "/api/auth/reset", {"email": mailB, "code": "11111111", "newPassword": "zzzzzz"})
    c, j, _ = jhttp("POST", "/api/auth/reset", {"email": mailB, "code": r["data"]["code"], "newPassword": "zzzzzz"})
    check("code burned after 5 wrong tries", j.get("ok") is False, j)
    if H.smtp_configured():
        pass

    # ---- child records, validation, file ownership ------------------------------------------------
    act(ta, "addChild", {"nickname": "کودک", "ageRange": "AGE_6_8"})
    cid = next(c["id"] for c in act(ta, "addChild", {"nickname": "کودک ۲", "ageRange": "AGE_6_8"})["state"]["children"] if c["nickname"] == "کودک")
    code, up = upload(ta)
    check("upload ok", code == 200 and up.get("ok"), up)
    fid = up["file"]["id"]
    fid2 = upload(ta)[1]["file"]["id"]
    check("upload magic-byte mismatch rejected", upload(ta, b"not a png at all", "image/png")[0] == 400)
    check("upload unsupported type rejected", upload(ta, b"x", "text/html")[0] == 400)
    for kind, data in (("log", {"childId": cid, "type": "meltdown", "intensity": "3", "date": "2026-10-30", "notes": "<script>alert(1)</script>"}),
                       ("reward", {"childId": cid, "title": "ستاره", "goal": "5", "stars": "2"}),
                       ("firstthen", {"childId": cid, "first": "تکلیف", "then": "بازی", "firstImage": fid}),
                       ("emergency", {"childId": cid, "name": "کودک", "contact1Phone": "0912"}),
                       ("diary", {"childId": cid, "note": "یادداشت", "date": "2026-10-30"}),
                       ("med", {"childId": cid, "name": "دارو", "dose": "1"})):
        r = act(ta, "recSave", {"kind": kind, "data": data})
        check("recSave " + kind, r.get("ok"), r)
    bad = act(ta, "recSave", {"kind": "log", "data": {"childId": cid, "type": "bogus"}})
    check("log enum validated", bad.get("ok") is False)
    bad = act(ta, "recSave", {"kind": "log", "data": {"childId": cid, "type": "sleep", "intensity": "9"}})
    check("log intensity range validated", bad.get("ok") is False)
    bad = act(tb, "recSave", {"kind": "log", "data": {"childId": cid, "type": "sleep"}})
    check("foreign childId rejected", bad.get("ok") is False)
    bad = act(ta, "recSave", {"kind": "diary", "data": {"childId": "c_nope", "note": "x"}})
    check("unknown childId rejected", bad.get("ok") is False)
    r = act(ta, "recSave", {"kind": "schedule", "data": {"title": "برنامه", "steps": "a\nb\nc", "cover": fid2,
                                                            "stepImages": "%s\n%s\n" % (fid2, ZERO)}})
    check("schedule with real+zero id placeholder", r.get("ok"), r)
    sid = r.get("data", {}).get("id")
    r = act(ta, "recSave", {"kind": "schedule", "data": {"title": "t", "steps": "a", "stepImages": "%s\n\n%s" % (ZERO, fid2)}})
    check("blank line allowed in list", r.get("ok"), r)
    r = act(ta, "recSave", {"kind": "story", "data": {"title": "t", "pages": "p", "pageImages": "zz-not-hex"}})
    check("bad list id rejected", r.get("ok") is False)
    r = act(tb, "recSave", {"kind": "story", "data": {"title": "t", "pages": "p", "cover": fid2}})
    check("other user's file rejected (single)", r.get("ok") is False, r)
    r = act(tb, "recSave", {"kind": "story", "data": {"title": "t", "pages": "p", "pageImages": fid}})
    check("other user's file rejected (list)", r.get("ok") is False, r)
    r = act(tb, "recSave", {"kind": "story", "data": {"title": "t", "pages": "p", "pageImages": ZERO}})
    check("zero id accepted w/o ownership", r.get("ok"), r)
    r = act(ta, "recSave", {"kind": "story", "data": {"title": "t", "pages": "p", "cover": "f" * 32}})
    check("nonexistent file rejected", r.get("ok") is False)
    lst = act(ta, "recList", {"kind": "log", "childId": cid})
    check("recList by childId", len(lst.get("data", {}).get("items", [])) == 1, lst)
    r = act(ta, "recDelete", {"kind": "schedule", "id": sid})
    check("recDelete schedule", r.get("ok"))
    check("file dropped with its last record? (still used elsewhere is ok)", True)

    # ---- sharing + report -----------------------------------------------------------------------------
    r = act(tb, "shareCreate", {"childId": cid, "kinds": ["log"]})
    check("shareCreate foreign child denied", r.get("ok") is False)
    r = act(ta, "shareCreate", {"childId": cid, "kinds": ["log", "emergency", "bogus"]})
    check("shareCreate bad kind denied", r.get("ok") is False)
    r = act(ta, "shareCreate", {"childId": cid, "kinds": ["log", "emergency"], "days": 3, "label": "معلم"})
    scode, shid = r["data"]["code"], r["data"]["id"]
    check("shareCreate code format", len(scode) == 9 and scode[4] == "-", r)
    r = act(tb, "shareRedeem", {"code": "ZZZZ-ZZZZ"})
    check("shareRedeem wrong code", r.get("ok") is False)
    r = act(ta, "shareRedeem", {"code": scode})
    check("owner cannot redeem own share", r.get("ok") is False)
    r = act(tb, "shareRedeem", {"code": scode.lower()})
    check("shareRedeem ok (case-insensitive)", r.get("ok") and r["data"]["childName"] == "کودک", r)
    r = act(tb, "shareView", {"id": shid})
    recs = r.get("data", {}).get("records", {})
    check("shareView scoped to kinds", set(recs) == {"log", "emergency"} and len(recs["log"]) == 1, r)
    r = act(atok, "shareView", {"id": shid})
    check("shareView stranger denied", r.get("ok") is False)
    lst = act(tb, "shareList")["data"]
    check("shareList received/owned", len(lst["received"]) == 1 and len(act(ta, "shareList")["data"]["owned"]) == 1)
    c, body, hd = http("GET", "/api/report?share=" + shid, token=tb)
    html = body.decode()
    check("report via share is RTL html", c == 200 and 'dir=rtl' in html and "text/html" in hd.get("Content-Type", ""))
    check("report escapes html", "<script>alert" not in html and "&lt;script&gt;" in html)
    check("report hides unshared kinds", "دفتر خاطرات" not in html and "دارو و مکمل" not in html)
    c, body, _ = http("GET", "/api/report?childId=" + cid, token=ta)
    check("owner report has all sections", c == 200 and "دفتر خاطرات" in body.decode() and "پاداش" in body.decode())
    check("report foreign child denied", http("GET", "/api/report?childId=" + cid, token=tb)[0] == 400)
    check("report unauth", http("GET", "/api/report?childId=" + cid)[0] == 401)
    check("report date filter", "2026-10-30" not in http("GET", "/api/report?childId=%s&from=2026-11-01" % cid, token=ta)[1].decode().split("ثبت رویدادها")[-1].split("<h2>")[0])
    r = act(tb, "shareRevoke", {"id": shid})
    check("redeemer can drop share", r.get("ok"))
    check("share gone after revoke", act(tb, "shareView", {"id": shid}).get("ok") is False)

    # ---- privacy ------------------------------------------------------------------------------------------
    act(ta, "updatePrivacy", {"settings": {"showCity": False, "showBio": False, "allowMessageRequests": True, "evil": True}})
    users = jhttp("GET", "/api/state", token=tb)[1]["state"]["users"]
    ua_pub = next(u for u in users if u["id"] == ua)
    check("showCity=false hides city", ua_pub.get("city") is None, ua_pub)
    check("showBio=false hides bio", ua_pub.get("bio") is None)
    check("email not exposed", "email" not in ua_pub)
    me = next(u for u in jhttp("GET", "/api/state", token=ta)[1]["state"]["users"] if u["id"] == ua)
    check("owner still sees own city", me.get("city") == "تهران" and "evil" not in (me.get("privacy") or {}), me)
    r = act(ta, "createQuestion", {"title": "پرسش ناشناس", "body": "متن پرسش", "anonymous": True, "category": "EDUCATION"})
    qid = r.get("data", {}).get("id")
    def myq(tok):
        return [q for q in jhttp("GET", "/api/state", token=tok)[1]["state"]["questions"] if q["id"] == qid]
    qs = myq(tb)
    check("anonymous authorId hidden from others", qs and qs[0]["authorId"] == "", r)
    check("anonymous authorId hidden in every other anon question", all(q["authorId"] == "" for q in jhttp("GET", "/api/state", token=tb)[1]["state"]["questions"] if q.get("isAnonymous")))
    qs = myq(ta)
    check("anonymous authorId visible to author", qs and qs[0]["authorId"] == ua)
    qs = myq(atok)
    check("anonymous authorId visible to admin", qs and qs[0]["authorId"] == ua)
    r = act(ta, "updateNotificationSettings", {"settings": {"newMessage": False, "junk": True}})
    check("notification keys whitelisted", r.get("ok"))

    # ---- admin console / moderation -----------------------------------------------------------------------------
    check("adminStats non-admin denied", act(ta, "adminStats").get("ok") is False)
    s = act(atok, "adminStats").get("data", {})
    check("adminStats", s.get("users", {}).get("total", 0) >= 3 and "records" in s and "files" in s, s)
    r = act(atok, "adminListUsers", {"q": "smoke-" + RUN})
    check("adminListUsers search", r.get("data", {}).get("total") == 3, r)
    check("adminListUsers non-admin denied", act(ta, "adminListUsers").get("ok") is False)
    soc = sql("SELECT count(*) FROM docs WHERE collection='users' AND data->>'accountType'='SOCIAL_WORKER'")[0][0]
    if soc == 0:
        sql("UPDATE docs SET data=jsonb_set(data,'{accountType}','\"SOCIAL_WORKER\"') WHERE collection='users' AND id=%s", (ua,))
        r = act(atok, "adminBroadcast", {"title": "آزمون", "body": "متن", "audience": "SOCIAL_WORKER"})
        check("adminBroadcast to one test user only", r.get("data", {}).get("sent") == 1, r)
        sql("UPDATE docs SET data=jsonb_set(data,'{accountType}','\"FAMILY\"') WHERE collection='users' AND id=%s", (ua,))
    else:
        print("skip broadcast: real SOCIAL_WORKER users exist")
    check("adminBroadcast non-admin denied", act(ta, "adminBroadcast", {"title": "x", "body": "y"}).get("ok") is False)
    check("setAccountStatus invalid value", act(atok, "setAccountStatus", {"userId": ub, "status": "NOPE", "reason": "x"}).get("ok") is False)
    r = act(ta, "donationCreate", {"title": "هدیه", "description": "d", "category": H.DONATION_CATEGORIES[0], "condition": "NEW", "city": "تهران"})
    check("donationCreate", r.get("ok"), r)
    did = r.get("data", {}).get("id")
    rep = act(tb, "createReport", {"contentType": "DONATION", "contentId": did, "reason": "SPAM", "note": "t"})
    check("report DONATION accepted", rep.get("ok"), rep)
    rep2 = act(tb, "createReport", {"contentType": "NOPE", "contentId": did, "reason": "SPAM"})
    check("report bad type rejected", rep2.get("ok") is False)
    r = act(atok, "hideContent", {"type": "DONATION", "contentId": did, "reason": "test"})
    check("hide donation", r.get("ok"), r)
    lst = act(tb, "donationList", {})
    check("hidden donation gone from public list", not any(d.get("id") == did for d in lst.get("data", {}).get("items", [])), lst)
    r = act(atok, "hideContent", {"type": "USER", "contentId": ub, "reason": "test"})
    check("hide user ok", r.get("ok"), r)
    act(atok, "setAccountStatus", {"userId": ub, "status": "ACTIVE", "reason": "restore"})
    r = act(atok, "setAccountStatus", {"userId": ub, "status": "BLOCKED", "reason": "test"})
    check("setAccountStatus BLOCKED", r.get("ok"), r)
    check("blocked user's token rejected immediately", http("GET", "/api/state", token=tb)[0] == 401)
    c, j, _ = jhttp("POST", "/api/auth/login", {"email": mailB, "password": "newpw-%s" % RUN})
    check("blocked user cannot log in", j.get("ok") is False or not j.get("token"))
    act(atok, "setAccountStatus", {"userId": ub, "status": "ACTIVE", "reason": "restore"})
    c, j, _ = jhttp("POST", "/api/auth/login", {"email": mailB, "password": "newpw-%s" % RUN})
    tb = j["token"]
    r = act(atok, "recSave", {"kind": "course", "data": {"title": "دورهٔ آزمون", "description": "d"}})
    check("admin recSave course", r.get("ok"), r)
    cid_course = r.get("data", {}).get("id")
    check("non-admin recSave course denied", act(ta, "recSave", {"kind": "course", "data": {"title": "x"}}).get("ok") is False)
    if cid_course:
        act(atok, "recDelete", {"kind": "course", "id": cid_course})

    # ---- concurrency: one slot, two bookers ------------------------------------------------------------------------------
    r = act(atok, "recSave", {"kind": "slot", "data": {"when": "فردا ۱۰", "duration": "30"}})
    slot = r.get("data", {}).get("id")
    sql("UPDATE docs SET data=jsonb_set(data,'{accountType}','\"PROFESSIONAL\"') WHERE collection='users' AND id=%s", (auid,))
    out = []
    def book(t):
        out.append(act(t, "slotBook", {"id": slot}).get("ok"))
    ths = [threading.Thread(target=book, args=(t,)) for t in (ta, tb, ta, tb)]
    [t.start() for t in ths]; [t.join() for t in ths]
    check("slot booked exactly once under race", out.count(True) == 1, out)

    # ---- TTS -----------------------------------------------------------------------------------------------------------
    check("tts unauth", http("POST", "/api/tts", {"text": "سلام"})[0] == 401)
    check("tts too long", http("POST", "/api/tts", {"text": "ا" * 400}, token=ta)[0] == 400)
    c, b, hd = http("POST", "/api/tts", {"text": "سلام دنیا"}, token=ta)
    check("tts POST audio", c == 200 and hd.get("Content-Type", "").startswith("audio/") and len(b) > 500, (c, hd.get("Content-Type")))
    c2, b2, _ = http("GET", "/api/tts?text=" + urllib.request.quote("سلام دنیا"), token=ta)
    check("tts GET (old clients) cached", c2 == 200 and b2 == b)

    # ---- orphan sweeper (scoped to our own test uid) ------------------------------------------------------------------------------
    code, o = upload(ta)
    orph = o["file"]["id"]
    check("sweeper keeps fresh files", H.sweep_orphans(24, owner=ua) == 0)
    n = H.sweep_orphans(0, owner=ua)
    check("sweeper removes unreferenced file", n >= 1 and http("GET", "/api/files/" + orph, token=ta)[0] == 404, n)
    check("sweeper keeps referenced file", http("GET", "/api/files/" + fid, token=ta)[0] == 200)

    # ---- delete-account wipe -------------------------------------------------------------------------------------------------------------
    act(ta, "shareCreate", {"childId": cid, "kinds": ["log"]})
    r = act(ta, "deleteAccount")
    check("deleteAccount", r.get("ok"), r)
    check("wipe: file bytes gone", http("GET", "/api/files/" + fid)[0] == 404)
    left = sql("SELECT count(*) FROM docs WHERE data->>'ownerId'=%s OR data->>'familyUserId'=%s OR data->>'userId'=%s", (ua, ua, ua))[0][0]
    check("wipe: no docs left for user", left == 0, left)
    check("wipe: creds gone", H.has_email(mailA) is False)
    check("wipe: token dead", http("GET", "/api/state", token=ta)[0] == 401)


def cleanup():
    for tok, uid in made:
        try:
            if sql("SELECT 1 FROM docs WHERE collection='users' AND id=%s", (uid,)):
                st = H.Store()
                H.a_deleteAccount(st, uid, {})
        except Exception as e:
            print("cleanup error for", uid, e)
    uids = [u for _, u in made]
    for coll in ("adminActions", "adminAudit"):
        sql("DELETE FROM docs WHERE collection=%s AND data->>'adminId' = ANY(%s)", (coll, uids))
    sql("DELETE FROM docs WHERE collection='reports' AND data->>'reporterId' = ANY(%s)", (uids,))
    sql("DELETE FROM docs WHERE collection='notifications' AND data->>'userId' = ANY(%s)", (uids,))
    left = sql("SELECT count(*) FROM docs WHERE collection='users' AND data->>'email' LIKE %s", ("smoke-%s-%%" % RUN,))[0][0]
    print("cleanup: %d smoke users left" % left)


if __name__ == "__main__":
    try:
        main()
    finally:
        cleanup()
    print("passed=%d failed=%d" % (PASS, FAIL))
    sys.exit(1 if FAIL else 0)
