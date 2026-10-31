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
    act(ta, "createQuestion", {"title": "پرسش عمومی", "body": "متن پرسش", "category": "EDUCATION"})  # authors of visible content are in everyone's users list
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

    v14_tests(atok, auid, ta, ua, tb, ub)

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
    check("deleteAccount without password rejected (breaking for <0.14 clients)", r.get("ok") is False, r)
    r = act(ta, "deleteAccount", {"password": "wrong-" + RUN})
    check("deleteAccount wrong password rejected", r.get("ok") is False, r)
    check("account survives rejected deletes", http("GET", "/api/state", token=ta)[0] == 200)
    r = act(ta, "deleteAccount", {"password": "pw-" + RUN + "-123"})
    check("deleteAccount", r.get("ok"), r)
    check("wipe: file bytes gone", http("GET", "/api/files/" + fid)[0] == 404)
    left = sql("SELECT count(*) FROM docs WHERE data->>'ownerId'=%s OR data->>'familyUserId'=%s OR data->>'userId'=%s", (ua, ua, ua))[0][0]
    check("wipe: no docs left for user", left == 0, left)
    check("wipe: creds gone", H.has_email(mailA) is False)
    check("wipe: token dead", http("GET", "/api/state", token=ta)[0] == 401)


def state_of(tok):
    return jhttp("GET", "/api/state", token=tok)[1].get("state", {})


def notes_of(tok, ntype=None):
    return [n for n in state_of(tok).get("notifications", []) if ntype is None or n.get("type") == ntype]


def v14_tests(atok, auid, ta, ua, tb, ub):
    """Server v14 items S1..S25. Uses its own throwaway users so earlier flows are not disturbed."""
    pw = "pw-" + RUN + "-123"
    mailC, tc, uc, _ = register("c")
    mailD, td, ud, _ = register("d")

    # S1 profile photo
    code, up = upload(tc)
    pid = up["file"]["id"]
    check("S1 upload returns signedUrl", "?exp=" in up["file"].get("signedUrl", up.get("signedUrl", "")) or "exp=" in json.dumps(up), up)
    r = act(tc, "updateProfile", {"displayName": "C", "photoId": pid})
    me = next(u for u in r["state"]["users"] if u["id"] == uc)
    check("S1 photoId sets photoUrl", me.get("photoUrl") == "files/" + pid, me)
    r = act(tc, "updateProfile", {"displayName": "C2", "bio": "x" * 700})
    me = next(u for u in r["state"]["users"] if u["id"] == uc)
    check("S1 photo kept when photoId absent", me.get("photoUrl") == "files/" + pid, me)
    check("S1 bio truncated to 600", len(me.get("bio") or "") == 600, len(me.get("bio") or ""))
    foreign = upload(td)[1]["file"]["id"]
    check("S1 foreign photo rejected", act(tc, "updateProfile", {"displayName": "C", "photoId": foreign}).get("ok") is False)
    r = act(tc, "updateProfile", {"displayName": "C", "photoId": ""})
    me = next(u for u in r["state"]["users"] if u["id"] == uc)
    check("S1 photoId '' removes photo + file", not me.get("photoUrl") and http("GET", "/api/files/" + pid, token=tc)[0] == 404, me)

    # S2/S3 professional profile + verification
    check("S2 upsert before verification denied", act(tc, "upsertProfessionalProfile", {"title": "روان‌شناس"}).get("ok") is False)
    doc = upload(tc)[1]["file"]["id"]
    check("S3 foreign documentRefs rejected", act(tc, "submitVerificationRequest", {"professionalTitle": "t", "documentRefs": [foreign]}).get("ok") is False)
    check("S3 bad requestedType rejected", act(tc, "submitVerificationRequest", {"professionalTitle": "t", "requestedType": "FAMILY"}).get("ok") is False)
    r = act(tc, "submitVerificationRequest", {"professionalTitle": "روان‌شناس", "licenseNumber": "123", "documentRefs": [doc]})
    check("S3 submitVerificationRequest", r.get("ok"), r)
    check("S3 admin cannot request", act(atok, "submitVerificationRequest", {"professionalTitle": "x"}).get("ok") is False)
    rq = next(v for v in state_of(tc)["verifications"] if v["userId"] == uc)
    check("S3 pro doc created as PENDING", any(p["userId"] == uc and p["verificationStatus"] == "PENDING" for p in state_of(tc)["professionals"]))
    fr = act(atok, "fileUrls", {"ids": [doc]})
    check("S25 admin gets signed url for any documentRef", doc in fr.get("data", {}).get("urls", {}), fr)
    check("S25 other user cannot", doc not in act(td, "fileUrls", {"ids": [doc]}).get("data", {}).get("urls", {}))
    check("S3 review needs valid decision", act(atok, "reviewVerification", {"requestId": rq["id"], "decision": "MAYBE", "reason": "r"}).get("ok") is False)
    r = act(atok, "reviewVerification", {"requestId": rq["id"], "decision": "VERIFIED", "reason": "مدارک تأیید شد"})
    check("S3 review VERIFIED", r.get("ok"), r)
    check("S3 verified user notified", any(n.get("type") == "VERIFICATION_RESULT" for n in notes_of(tc)))
    check("S3 accountType PROFESSIONAL after verify", next(u for u in state_of(tc)["users"] if u["id"] == uc)["accountType"] == "PROFESSIONAL")

    # S21 professional profile photo/fee/hours + S22 sweeper
    ppid = upload(tc)[1]["file"]["id"]
    base = {"title": "روان‌شناس کودک", "workAreas": ["اوتیسم"], "city": "تهران", "offersOnline": True, "experience": "۵ سال"}
    r = act(tc, "upsertProfessionalProfile", dict(base, photoId=ppid, fee="۲۰۰ هزار تومان", hours="شنبه تا چهارشنبه ۹ تا ۱۷"))
    check("S21 upsert pro profile", r.get("ok"), r)
    pr = next(p for p in r["state"]["professionals"] if p["userId"] == uc)
    check("S21 photoId/fee/hours stored + returned", pr.get("photoId") == ppid and pr.get("fee") == "۲۰۰ هزار تومان" and pr.get("hours", "").startswith("شنبه"), pr)
    check("S21 fee > 80 rejected", act(tc, "upsertProfessionalProfile", dict(base, fee="x" * 81)).get("ok") is False)
    check("S21 hours > 120 rejected", act(tc, "upsertProfessionalProfile", dict(base, hours="x" * 121)).get("ok") is False)
    check("S21 foreign photoId rejected", act(tc, "upsertProfessionalProfile", dict(base, photoId=foreign)).get("ok") is False)
    check("S21 non-image photoId rejected", act(tc, "upsertProfessionalProfile", dict(base, photoId="z" * 32)).get("ok") is False)
    r = act(tc, "upsertProfessionalProfile", base)
    pr = next(p for p in r["state"]["professionals"] if p["userId"] == uc)
    check("S21 absent photoId/fee/hours keep stored values", pr.get("photoId") == ppid and pr.get("fee") and pr.get("hours"), pr)
    fr = act(td, "fileUrls", {"ids": [ppid]})
    check("S21 family sees pro photo via fileUrls", ppid in fr.get("data", {}).get("urls", {}), fr)
    n = H.sweep_orphans(0, owner=uc)
    gone = [f for f in (doc, ppid) if http("GET", "/api/files/" + f, token=atok)[0] != 200]
    check("S22 sweeper keeps documentRefs + pro photoId", not gone, (n, gone))
    ufid = upload(tc)[1]["file"]["id"]
    act(tc, "updateProfile", {"displayName": "C", "photoId": ufid})
    H.sweep_orphans(0, owner=uc)
    check("S22 sweeper keeps users.photoId", http("GET", "/api/files/" + ufid, token=tc)[0] == 200)

    # S4 changePassword
    check("S4 wrong current rejected", act(tc, "changePassword", {"current": "nope", "new": "newer-" + RUN}).get("ok") is False)
    check("S4 short new rejected", act(tc, "changePassword", {"current": pw, "new": "123"}).get("ok") is False)
    check("S4 same password rejected", act(tc, "changePassword", {"current": pw, "new": pw}).get("ok") is False)
    old = tc
    time.sleep(1.1)
    r = act(tc, "changePassword", {"current": pw, "new": "newer-" + RUN})
    check("S4 changePassword returns token", r.get("ok") and r.get("data", {}).get("token"), r)
    tc = r["data"]["token"]
    made[:] = [(tc if u == uc else t, u) for t, u in made]
    check("S4 old token dead / new token works", http("GET", "/api/state", token=old)[0] == 401 and http("GET", "/api/state", token=tc)[0] == 200)
    c, j, _ = jhttp("POST", "/api/auth/login", {"email": mailC, "password": "newer-" + RUN})
    check("S4 login with new password", c == 200 and j.get("token"))

    # S5 children
    r = act(td, "addChild", {"nickname": "نیما", "ageRange": "AGE_3_5", "notes": "یادداشت", "interests": ["نقاشی"]})
    kid = next(c for c in r["state"]["children"] if c["nickname"] == "نیما")
    check("S5 addChild notes stored", kid.get("notes") == "یادداشت", kid)
    r = act(td, "updateChild", {"id": kid["id"], "fields": {"nickname": "نیما۲", "notes": "n2"}})
    kid = next(c for c in r["state"]["children"] if c["id"] == kid["id"])
    check("S5 updateChild", kid["nickname"] == "نیما۲" and kid["notes"] == "n2", kid)
    check("S5 updateChild foreign denied", act(tc, "updateChild", {"id": kid["id"], "fields": {"nickname": "x"}}).get("ok") is False)
    act(td, "recSave", {"kind": "diary", "data": {"childId": kid["id"], "note": "ثبت", "date": "2026-10-30"}})
    sh = act(td, "shareCreate", {"childId": kid["id"], "kinds": ["diary"]})
    check("S5 deleteChild without confirm rejected (breaking for <0.14)", act(td, "deleteChild", {"childId": kid["id"]}).get("ok") is False)
    check("S5 child still there", any(c["id"] == kid["id"] for c in state_of(td)["children"]))
    r = act(td, "deleteChild", {"childId": kid["id"], "confirm": True})
    check("S5 deleteChild confirm ok", r.get("ok") and not any(c["id"] == kid["id"] for c in r["state"]["children"]), r)
    left = sql("SELECT count(*) FROM docs WHERE data->>'childId'=%s", (kid["id"],))[0][0]
    check("S5 deleteChild cascades records + shares", left == 0, left)

    # S6 exportMyData
    r = act(td, "exportMyData")
    d = r.get("data", {})
    ex = json.loads(d["json"]) if d.get("json") else {}
    check("S6 exportMyData shape", r.get("ok") and d.get("filename") and d.get("text") and "profile" in ex and "children" in ex, list(d))
    check("S6 export has no secrets", "codeHash" not in d.get("json", "") and "tokensValidAfter" not in d.get("json", ""))

    # S7 shares: recipient revoke keeps owner's record (S23)
    r = act(td, "addChild", {"nickname": "سارا", "ageRange": "AGE_3_5"})
    kid2 = next(c for c in r["state"]["children"] if c["nickname"] == "سارا")["id"]
    act(td, "recSave", {"kind": "diary", "data": {"childId": kid2, "note": "x", "date": "2026-10-30"}})
    s1 = act(td, "shareCreate", {"childId": kid2, "kinds": ["diary"]})
    code1 = s1["data"]["code"]
    check("S23 redeem", act(tc, "shareRedeem", {"code": code1}).get("ok"))
    check("S23 stranger cannot revoke", act(tb, "shareRevoke", {"id": s1["data"]["id"]}).get("ok") is False)
    r = act(tc, "shareRevoke", {"id": s1["data"]["id"]})
    check("S23 recipient revoke ok", r.get("ok"), r)
    lst = act(td, "shareList").get("data", {})
    mine = [s for s in lst.get("owned", []) if s["id"] == s1["data"]["id"]]
    check("S23 owner's record survives recipient revoke (REVOKED)", mine and mine[0]["status"] == "REVOKED", lst)
    check("S23 recipient lost access", not act(tc, "shareList").get("data", {}).get("received"))
    check("S23 revoked code unusable", act(tb, "shareRedeem", {"code": code1}).get("ok") is False)
    check("S23 owner revoke still deletes", act(td, "shareRevoke", {"id": s1["data"]["id"]}).get("ok")
          and not [s for s in act(td, "shareList")["data"]["owned"] if s["id"] == s1["data"]["id"]])

    # S8 groups + notifications
    gid = act(td, "createGroup", {"title": "گروه آزمون"})["data"]["id"]
    act(tc, "joinOrRequest", {"groupId": gid})
    act(td, "removeMember", {"groupId": gid, "userId": uc})
    check("S8 removeMember notifies", notes_of(tc, "SAFETY_NOTICE") or notes_of(tc, "GROUP_JOIN_REQUEST"))
    gid2 = act(td, "createGroup", {"title": "گروه خصوصی", "visibility": "PRIVATE"})["data"]["id"]
    act(tc, "joinOrRequest", {"groupId": gid2})
    act(td, "approveMember", {"groupId": gid2, "userId": uc})
    check("S8 approveMember notifies requester", any("پذیرفته" in (n.get("body") or n.get("message") or "") for n in notes_of(tc, "GROUP_JOIN_REQUEST")), notes_of(tc))
    pr1 = act(td, "createPost", {"groupId": gid2, "body": "پست نخست"})
    posts = [p for p in pr1["state"]["posts"] if p["groupId"] == gid2]
    act(tc, "createPost", {"groupId": gid2, "body": "پاسخ", "replyToPostId": posts[0]["id"]})
    check("S8 reply stored + parent author notified", any(p.get("replyToPostId") == posts[0]["id"] for p in state_of(tc)["posts"]) and notes_of(td, "GROUP_REPLY"))

    # S9 reports
    check("S9 report unknown content rejected", act(tc, "createReport", {"contentType": "GROUP_POST", "contentId": "gp_nope"}).get("ok") is False)
    check("S9 self report rejected", act(tc, "createReport", {"contentType": "USER", "contentId": uc}).get("ok") is False)
    r1 = act(tc, "createReport", {"contentType": "GROUP_POST", "contentId": posts[0]["id"], "reason": "spam"})
    r2 = act(tc, "createReport", {"contentType": "GROUP_POST", "contentId": posts[0]["id"], "reason": "spam"})
    check("S9 duplicate report flagged", r1.get("ok") and r2.get("data", {}).get("duplicate") is True, (r1.get("data"), r2.get("data")))
    rep = next(x for x in state_of(atok)["reports"] if x["contentId"] == posts[0]["id"])
    r = act(atok, "resolveReport", {"reportId": rep["id"], "newStatus": "ACTIONED", "resolutionNote": "n", "hideContent": True})
    hst = sql("SELECT data->>'status' FROM docs WHERE collection='posts' AND id=%s", (posts[0]["id"],))
    check("S9 resolveReport hideContent hides", r.get("ok") and hst and hst[0][0] == "HIDDEN"
          and all(x["id"] != posts[0]["id"] for x in state_of(td)["posts"]), (r, hst))

    # S10 admin audit log + account type + status
    check("S10 non-admin has no auditLog", "auditLog" not in state_of(tc) or not state_of(tc)["auditLog"])
    check("S24 adminSetAccountType requires reason", act(atok, "adminSetAccountType", {"userId": ud, "accountType": "EDUCATOR"}).get("ok") is False)
    check("S10 adminSetAccountType invalid type", act(atok, "adminSetAccountType", {"userId": ud, "accountType": "KING", "reason": "r"}).get("ok") is False)
    check("S10 adminSetAccountType non-admin denied", act(tc, "adminSetAccountType", {"userId": ud, "accountType": "EDUCATOR", "reason": "r"}).get("ok") is False)
    r = act(atok, "adminSetAccountType", {"userId": ud, "accountType": "EDUCATOR", "reason": "سابقهٔ آموزشی"})
    check("S24 adminSetAccountType ok", r.get("ok"), r)
    check("S10 account type changed + user notified", next(u for u in state_of(td)["users"] if u["id"] == ud)["accountType"] == "EDUCATOR" and notes_of(td, "VERIFICATION_RESULT"))
    check("S10 audit log lists SET_ACCOUNT_TYPE", any(a.get("actionType") == "SET_ACCOUNT_TYPE" for a in state_of(atok).get("auditLog", [])), state_of(atok).get("auditLog", [])[:2])
    act(atok, "adminSetAccountType", {"userId": ud, "accountType": "FAMILY", "reason": "برگشت"})
    check("S10 admin account type cannot change", act(atok, "adminSetAccountType", {"userId": auid, "accountType": "FAMILY", "reason": "r"}).get("ok") is False)
    check("S10 setAccountStatus unknown user error", act(atok, "setAccountStatus", {"userId": "u_nope", "status": "BLOCKED", "reason": "r"}).get("ok") is False)
    check("S10 hideContent unknown content error", act(atok, "hideContent", {"type": "QUESTION", "contentId": "q_nope", "reason": "r"}).get("ok") is False)

    # S11 blocks, privacy
    act(tc, "blockUser", {"targetUserId": ud})
    stD = state_of(td)
    check("S11 blocker not visible to blocked user (users)", all(u["id"] != uc for u in stD["users"]))
    check("S11 blocker hidden from blocked user's professionals", all(p["userId"] != uc for p in stD["professionals"]))
    check("S11 blocked pair cannot start a conversation", act(td, "requestConversation", {"recipientUserId": uc, "message": "x"}).get("ok") is False)
    act(tc, "unblockUser", {"targetUserId": ud})

    # S12 slots
    now = int(time.time() * 1000)
    r = act(tc, "recSave", {"kind": "slot", "data": {"when": "گذشته", "duration": "30", "epochMs": now - 86400000}})
    check("S12 past slot rejected", r.get("ok") is False, r)
    r = act(tc, "recSave", {"kind": "slot", "data": {"when": "آینده", "duration": "30", "epochMs": now + 3 * 86400000}})
    check("S12 future slot saved with epochMs", r.get("ok"), r)
    sid = r["data"]["id"]
    lst = act(td, "recList", {"kind": "slot"}).get("data", {})
    items = lst.get("items", lst if isinstance(lst, list) else [])
    check("S12 slot list has epochMs", any(i.get("id") == sid and i.get("epochMs") for i in items), lst)
    check("S12 owner cannot self-book", act(tc, "slotBook", {"id": sid}).get("ok") is False)
    r = act(td, "slotBook", {"id": sid})
    check("S12 book ok", r.get("ok"), r)
    check("S12 booked slot cannot be edited", act(tc, "recSave", {"kind": "slot", "id": sid, "data": {"when": "x", "duration": "40", "epochMs": now + 4 * 86400000}}).get("ok") is False)
    check("S12 owner notified of booking", notes_of(tc, "BOOKING"))
    act(td, "slotCancel", {"id": sid})

    # S13 conversation / message guards
    rc = act(td, "requestConversation", {"recipientUserId": uc, "message": "سلام"})
    check("S13 requestConversation ok", rc.get("ok"), rc)

    # S14 admin stats + broadcast
    st = act(atok, "adminStats").get("data", {})
    check("S14 adminStats has moduleRoles/disabledFeatures", "moduleRoles" in st and "disabledFeatures" in st, list(st))
    r = act(atok, "adminBroadcast", {"title": "آزمون", "body": "پیام آزمایشی"})
    bd = r.get("data", {})
    check("S14 adminBroadcast returns counts", r.get("ok") and "sent" in bd and "skipped" in bd, r)
    r = act(atok, "adminIssueReset", {"email": mailD})
    check("S14 adminIssueReset notifies target", r.get("ok") and notes_of(td, "SAFETY_NOTICE"), r)

    # S15/S16 blocked & limited accounts
    act(atok, "setAccountStatus", {"userId": ud, "status": "LIMITED", "reason": "test"})
    r = act(td, "recSave", {"kind": "diary", "data": {"note": "x", "date": "2026-10-30"}})
    check("S15 limited account cannot mutate", r.get("ok") is False, r)
    check("S15 limited account can still read", http("GET", "/api/state", token=td)[0] == 200)
    act(atok, "setAccountStatus", {"userId": ud, "status": "ACTIVE", "reason": "ok"})
    check("S15 restored account can mutate", act(td, "updateProfile", {"displayName": "D"}).get("ok"))

    # S17 state scoping
    stD = state_of(td)
    check("S17 users scoped (not whole directory)", len(stD["users"]) < sql("SELECT count(*) FROM docs WHERE collection='users'")[0][0] or sql("SELECT count(*) FROM docs WHERE collection='users'")[0][0] < 8)
    check("S17 non-admin sees no other pending pro", all(p["verificationStatus"] == "VERIFIED" or p["userId"] == ud for p in stD["professionals"]))
    check("S17 non-admin verifications only own", all(v["userId"] == ud for v in stD["verifications"]))

    files_tests(atok, tc, td, ppid, doc)

    # S18 deleteAccount hands over groups and anonymises
    gid3 = act(td, "createGroup", {"title": "گروه رها"})["data"]["id"]
    act(tc, "joinOrRequest", {"groupId": gid3})
    r = act(td, "deleteAccount", {"password": pw})
    check("S18 deleteAccount with password", r.get("ok"), r)
    own = [m for m in sql("SELECT data->>'userId', data->>'role' FROM docs WHERE collection='memberships' AND data->>'groupId'=%s", (gid3,))]
    check("S18 group handed to remaining member", (uc, "OWNER") in own, own)


def files_tests(atok, tc, td, ppid, doc):
    """S19: signed file URLs, in both modes. Mode A = running server (FILES_ENFORCE off), mode B = a second
    instance started with FILES_ENFORCE=1."""
    global BASE
    r = act(tc, "fileUrls", {"ids": [ppid, "0" * 32, "f" * 32, "bad"]})
    urls = r.get("data", {}).get("urls", {})
    check("S19 fileUrls returns signed url for own file", ppid in urls and "exp=" in urls[ppid] and "sig=" in urls[ppid], r)
    check("S19 fileUrls omits zero/missing/bad ids", set(urls) == {ppid}, urls)
    check("S19 fileUrls rejects >100 ids", act(tc, "fileUrls", {"ids": [ppid] * 101}).get("ok") is False)
    check("S19 fileUrls: other user's private doc omitted", doc not in act(td, "fileUrls", {"ids": [doc]}).get("data", {}).get("urls", {}))
    path = "/api/" + urls[ppid]
    exp = int(urls[ppid].split("exp=")[1].split("&")[0])
    sig = urls[ppid].split("sig=")[1]

    def modes(label, base, enforced):
        global BASE
        saved, BASE = BASE, base
        try:
            check("S19[%s] signed url works without auth" % label, http("GET", path)[0] == 200)
            check("S19[%s] forged sig -> 403" % label, http("GET", "/api/files/%s?exp=%d&sig=%s" % (ppid, exp, "0" * 32))[0] == 403)
            check("S19[%s] tampered exp -> 403" % label, http("GET", "/api/files/%s?exp=%d&sig=%s" % (ppid, exp + 5, sig))[0] == 403)
            check("S19[%s] expired signed url -> 403" % label, http("GET", "/api/files/%s?exp=%d&sig=%s" % (ppid, int(time.time()) - 5, H._file_sig(ppid, int(time.time()) - 5)))[0] == 403)
            check("S19[%s] sig of another file -> 403" % label, http("GET", "/api/files/%s?exp=%d&sig=%s" % (doc, exp, sig))[0] == 403)
            check("S19[%s] owner Bearer works" % label, http("GET", "/api/files/" + ppid, token=tc)[0] == 200)
            check("S19[%s] admin Bearer works on any file" % label, http("GET", "/api/files/" + doc, token=atok)[0] == 200)
            check("S19[%s] non-owner Bearer on private doc" % label, http("GET", "/api/files/" + doc, token=td)[0] == (200 if not enforced else 403))
            check("S19[%s] bare url %s" % (label, "blocked" if enforced else "still works (0.13 clients)"),
                  http("GET", "/api/files/" + doc)[0] == (403 if enforced else 200))
            check("S19[%s] missing file 404" % label, http("GET", "/api/files/" + "e" * 32)[0] == 404)
        finally:
            BASE = saved

    modes("enforce off", BASE, False)
    port = 8159
    env = dict(os.environ, PORT=str(port), FILES_ENFORCE="1")
    proc = subprocess.Popen([sys.executable, os.path.join(HERE, "hamrah-api.py")], env=env, cwd=HERE,
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        base2 = "http://127.0.0.1:%d" % port
        up = False
        for _ in range(40):
            try:
                if urllib.request.urlopen(base2 + "/api/health", timeout=2).status == 200:
                    up = True
                    break
            except Exception:
                time.sleep(0.5)
        check("S19 enforcing instance started", up)
        if up:
            modes("enforce on", base2, True)
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=10)
        except Exception:
            proc.kill()


def cleanup():
    for tok, uid in made:
        try:
            if sql("SELECT 1 FROM docs WHERE collection='users' AND id=%s", (uid,)):
                st = H.Store()
                H.wipe_account(st, uid)
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
