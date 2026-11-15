#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Tests of the 1.4.1 "urgent package": device-account deletion (B1), sliding session renewal (B2),
signed-file enforcement (B5), reserved display names (B6), no external avatar URL (B7) and the
feature switches used to hide empty sections (I1).

Run ON the server against the TEST instance only (never production):

    cd /home/rasta/hamrah-v2test && BASE=http://127.0.0.1:8151 python3 test_urgent.py

Reuses the helpers of test_community.py (which refuses to run unless DB_DSN is the v2test database).
Accounts are throwaway (cm-*@example.invalid, device ids zzurg…) and are wiped at the end.
"""
import os
import sys
import time
import subprocess
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import test_community as T  # noqa: E402

H, act, data, jhttp, http, upload = T.H, T.act, T.data, T.jhttp, T.http, T.upload
PASS = FAIL = 0
RENEW = "X-Hamrah-Token"


def check(name, cond, extra=""):
    global PASS, FAIL
    if cond:
        PASS += 1
    else:
        FAIL += 1
        print("FAIL: %s %s" % (name, str(extra)[:400]))


def hdr(headers, name):
    return next((v for k, v in headers.items() if k.lower() == name.lower()), None)


def device(tag, name="", ip=None):
    """-> (http code, json). Device ids are unique per run."""
    h = {"X-Forwarded-For": ip} if ip else None
    code, j, _ = jhttp("POST", "/api/auth/device", {"device": "zzurg%s%s" % (T.RUN, tag), "displayName": name,
                                                    "acceptedTerms": True}, headers=h)
    if j.get("token") and j["user"]["id"] not in [u for _, u in T.made]:
        T.made.append((j["token"], j["user"]["id"]))
    return code, j


def state(token):
    return jhttp("GET", "/api/state", token=token)[1].get("state") or {}


def set_user(uid, **fields):
    """Writes straight to the store: simulates data saved by an OLDER server version."""
    with H.MUT_LOCK:
        st = H.Store()
        u = st.get("users", uid)
        u.update(fields)
        st.put("users", u)


def b1_delete_device_account(ta):
    _, j = device("del", "مامانِ آزمایشی")
    tok, uid = j["token"], j["user"]["id"]
    check("B1 device account has no email", j["user"].get("email") is None)
    kid = act(tok, "addChild", {"nickname": "ک", "ageRange": "3-5"})
    r = act(tok, "deleteAccount", {})
    check("B1 delete without confirm is refused (old app behaviour kept)", r.get("ok") is False and "تأیید" in r.get("error", ""), r)
    check("B1 a made-up password does not delete a device account", act(tok, "deleteAccount", {"password": "x" * 8}).get("ok") is False)
    check("B1 confirm must be a real boolean", act(tok, "deleteAccount", {"confirm": "true"}).get("ok") is False)
    r = act(tok, "changePassword", {"current": "a", "new": "abcdef1"})
    check("B1 changePassword on a device account: clear message", r.get("ok") is False and "بدون رمز" in r.get("error", ""), r)
    check("B1 account still there after refusals", state(tok).get("users"))
    r = act(tok, "deleteAccount", {"confirm": True})
    check("B1 confirm:true deletes the device account", r.get("ok") is True, r)
    check("B1 token is dead after deletion", jhttp("GET", "/api/state", token=tok)[0] == 401)
    T.made[:] = [m for m in T.made if m[1] != uid]
    st = H.Store()
    check("B1 user doc gone", st.get("users", uid) is None)
    check("B1 children of the account gone", not [c for c in st.all("children") if c.get("familyUserId") == uid], kid)
    _, j2 = device("del")
    check("B1 same phone starts a NEW empty account afterwards", j2.get("created") is True and j2["user"]["id"] != uid, j2)
    # an email account can NOT be deleted by confirm alone
    r = act(ta, "deleteAccount", {"confirm": True})
    check("B1 email account still needs its password", r.get("ok") is False and "رمز" in r.get("error", ""), r)
    check("B1 email account wrong password refused", act(ta, "deleteAccount", {"confirm": True, "password": "nope-nope"}).get("ok") is False)
    check("B1 email account still alive", jhttp("GET", "/api/state", token=ta)[0] == 200)


def b2_renewal(ta, ua):
    now = int(time.time())
    code, j, hd = jhttp("GET", "/api/state", token=ta)
    check("B2 fresh token: no renewal header", code == 200 and hdr(hd, RENEW) is None, hdr(hd, RENEW))
    old = H.make_token(ua, now - 2 * 86400)
    code, j, hd = jhttp("GET", "/api/state", token=old)
    new = hdr(hd, RENEW)
    check("B2 2-day-old token still works and body is unchanged", code == 200 and j.get("ok") and "state" in j)
    check("B2 2-day-old token gets a renewal header", bool(new) and new != old, hd)
    full = H.verify_token_full(new or "")
    check("B2 renewed token is for the same user and is new", bool(full) and full[0] == ua and full[1] >= now, full)
    check("B2 renewed token works", jhttp("GET", "/api/state", token=new)[0] == 200)
    code, j, hd = jhttp("POST", "/api/action", {"name": "state", "args": {}}, old)
    check("B2 renewal also rides on action replies", code == 200 and bool(hdr(hd, RENEW)))
    # a token about to expire (29.9 days old) is renewed instead of throwing the user out
    almost = H.make_token(ua, now - H.TOKEN_TTL + 600)
    code, _, hd = jhttp("GET", "/api/state", token=almost)
    check("B2 token 10 minutes before expiry is renewed", code == 200 and bool(hdr(hd, RENEW)))
    dead = H.make_token(ua, now - H.TOKEN_TTL - 60)
    code, _, hd = jhttp("GET", "/api/state", token=dead)
    check("B2 expired token -> 401, no renewal", code == 401 and hdr(hd, RENEW) is None)
    code, _, hd = jhttp("GET", "/api/state", token=old[:-2] + "xx")
    check("B2 forged token -> 401, no renewal", code == 401 and hdr(hd, RENEW) is None)
    # unauthenticated endpoints never emit a token
    code, _, hd = jhttp("GET", "/api/health")
    check("B2 health: no renewal header", code == 200 and hdr(hd, RENEW) is None)
    # sign-out-everywhere must not hand back a working token in its own reply
    _, j = device("b2", "نشستِ آزمایشی")
    tok, uid = j["token"], j["user"]["id"]
    old = H.make_token(uid, now - 2 * 86400)
    code, r, hd = jhttp("POST", "/api/action", {"name": "logoutAllDevices", "args": {}}, old)
    leaked = hdr(hd, RENEW)
    time.sleep(1.2)
    check("B2 logoutAllDevices ok", code == 200 and r.get("ok"), r)
    check("B2 logoutAllDevices: no usable token leaks through the renewal header",
          leaked is None or jhttp("GET", "/api/state", token=leaked)[0] == 401, leaked)
    check("B2 old tokens are dead after logoutAllDevices", jhttp("GET", "/api/state", token=old)[0] == 401
          and jhttp("GET", "/api/state", token=tok)[0] == 401)
    code, j2 = device("b2")
    check("B2 device account signs back in silently with the same id", j2.get("created") is False and j2["user"]["id"] == uid, j2)
    check("B2 the new device token works", jhttp("GET", "/api/state", token=j2["token"])[0] == 200)
    # the app's silent re-open sends NO acceptedTerms: it may only ever return an existing account
    dev_id = "zzurg%sb2" % T.RUN
    code, j4, _ = jhttp("POST", "/api/auth/device", {"device": dev_id})
    check("B2 silent re-open (no acceptedTerms) returns the existing account", j4.get("created") is False and j4["user"]["id"] == uid, j4)
    n_before = len(H.Store().all("users"))
    code, j5, _ = jhttp("POST", "/api/auth/device", {"device": "zzurg%snever" % T.RUN})
    check("B2 silent re-open never creates an account", not j5.get("token") and len(H.Store().all("users")) == n_before, j5)
    # blocked account: neither renewal nor device re-login
    set_user(uid, status="BLOCKED")
    old = H.make_token(uid, int(time.time()) - 2 * 86400)
    code, _, hd = jhttp("GET", "/api/state", token=old)
    check("B2 blocked account: 401 and no renewal", code == 401 and hdr(hd, RENEW) is None)
    _, j3 = device("b2")
    check("B2 blocked account cannot come back through device login", not j3.get("token"), j3)
    set_user(uid, status="ACTIVE")


def b6_names(at, ta, ua, tb):
    def rename(tok, name):
        return act(tok, "updateProfile", {"displayName": name})

    bad = ["مدیر", "مدیر سایت", "مدیرِ همراهِ طیف", "پشتیبانی", "پشتیبانی همراه طیف", "پشتیبان", "ادمین", "Admin",
           "ADMIN 1", "support", "همراه طیف", "همراهِ طیف", "هـمـراه‌طـیف", "متخصص تأییدشده", "متخصصِ تأیید شده",
           "مـديـر", "مدير", "مد‌یر", "م‌دیر كل", "ناظر", "Moderator", "مدیریت", "پشتیباني", "«مدیر»", "مدیر.", "hamrah_support"]
    for n in bad:
        r = rename(ta, n)
        check("B6 reserved name refused: %r" % n, r.get("ok") is False and "نگه داشته" in r.get("error", ""), r)
    good = ["مدیری", "علی مدیرزاده", "ناظری", "مامانِ سارا", "پدرِ آرش", "Sara M", "همراه 4821", "دکتر رضایی", "ادیب", "کاربر۱۲"]
    for n in good:
        r = rename(ta, n)
        check("B6 ordinary name accepted: %r" % n, r.get("ok") is True, r)
    me = next(u for u in state(ta)["users"] if u["id"] == ua)
    check("B6 last accepted name is stored", me["displayName"] == good[-1], me["displayName"])
    # sign-up paths
    code, j, _ = jhttp("POST", "/api/auth/register", dict(displayName="پشتیبانی", email="cm-%s-res@example.invalid" % T.RUN,
                                                          password="pw-%s-123" % T.RUN, acceptedTerms=True))
    check("B6 register with a reserved name refused", not j.get("token") and "نگه داشته" in j.get("error", ""), j)
    check("B6 refused register created no account", not H.has_email("cm-%s-res@example.invalid" % T.RUN))
    _, j = device("res", "مدیر")
    check("B6 device start with a reserved name refused", not j.get("token") and "نگه داشته" in j.get("error", ""), j)
    _, j = device("res", "")
    check("B6 device start with no name still works", bool(j.get("token")) and j["user"]["displayName"].startswith("همراه "), j)
    # exact name of staff
    old_admin_name = next(u for u in state(at)["users"] if u["id"] == T.made[0][1])["displayName"]
    admin_name = "Sara Karimi %s" % T.RUN
    check("B6 admin takes an ordinary name", rename(at, admin_name).get("ok") is True)
    r = rename(tb, admin_name)
    check("B6 exact name of an admin refused", r.get("ok") is False and "یکی است" in r.get("error", ""), r)
    r = rename(tb, " ".join(admin_name.upper()))
    check("B6 spaced/upper-cased copy of an admin's name refused", r.get("ok") is False, r)
    check("B6 an admin may be called «مدیر»", rename(at, "مدیر آزمایشی").get("ok") is True)
    check("B6 admin name restored", rename(at, old_admin_name).get("ok") is True)
    # a name saved by an older server keeps working for unrelated edits
    set_user(ua, displayName="پشتیبانی قدیمی")
    r = act(ta, "updateProfile", {"displayName": "پشتیبانی قدیمی", "bio": "معرفیِ تازه", "city": "تهران"})
    check("B6 unchanged legacy name does not block editing bio/city", r.get("ok") is True, r)
    check("B6 ...but it cannot be changed to another reserved name", rename(ta, "پشتیبانی جدید").get("ok") is False)
    check("B6 back to a normal name", rename(ta, "cm a").get("ok") is True)


def b7_photo(ta, ua, tb, ub):
    def me(tok, uid):
        """The user as `tok` sees them: own record from the state, anyone else through userProfile."""
        own = next((u for u in state(tok)["users"] if u["id"] == uid), None)
        return own if own is not None else data(tok, "userProfile", {"userId": uid})["user"]

    ext = "https://tracker.example/p.png?u=1"
    r = act(ta, "updateProfile", {"displayName": "cm a", "photoUrl": ext})
    check("B7 updateProfile with an outside photoUrl does not fail (old apps)", r.get("ok") is True, r)
    check("B7 outside photoUrl is NOT stored", me(ta, ua).get("photoUrl") in (None, ""), me(ta, ua).get("photoUrl"))
    for bad in ("http://127.0.0.1/x", "//evil.example/files/" + "a" * 32 + "/x", "javascript:alert(1)", "files/../../etc/passwd", "x" * 600):
        act(ta, "updateProfile", {"displayName": "cm a", "photoUrl": bad})
        check("B7 junk photoUrl ignored: %s" % bad[:30], me(ta, ua).get("photoUrl") in (None, ""), me(ta, ua).get("photoUrl"))
    mine, theirs = upload(ta), upload(tb)
    act(ta, "updateProfile", {"displayName": "cm a", "photoUrl": "files/" + theirs})
    check("B7 someone else's file as photoUrl ignored", me(ta, ua).get("photoUrl") in (None, ""))
    act(ta, "updateProfile", {"displayName": "cm a", "photoUrl": "https://proj.tizbin.org/hamrah/api/files/%s?exp=1&sig=x" % mine})
    check("B7 own file given as a URL is normalised to files/<id>", me(ta, ua).get("photoUrl") == "files/" + mine, me(ta, ua).get("photoUrl"))
    check("B7 photoId path unchanged", act(ta, "updateProfile", {"displayName": "cm a", "photoId": mine}).get("ok") is True
          and me(tb, ua).get("photoUrl") == "files/" + mine)
    check("B7 photoId of another user's file still refused", act(ta, "updateProfile", {"displayName": "cm a", "photoId": theirs}).get("ok") is False)
    # an outside URL saved BEFORE this rule is not handed to other users
    set_user(ua, photoUrl=ext, photoId=None)
    tid = data(ta, "forumCreateTopic", {"forumId": "heart", "title": "گفتگوی آزمایشیِ عکس", "body": "متنِ کافیِ آزمایشی برای عکس"})["id"]
    card = data(tb, "forumTopic", {"topicId": tid})
    check("B7 legacy outside URL hidden in forum author card", "tracker.example" not in str(card), str(card)[:300])
    check("B7 legacy outside URL hidden in other users' state", "tracker.example" not in str(state(tb)))
    lst = data(tb, "forumTopics", {"forumId": "heart"})
    check("B7 legacy outside URL hidden in topic list", "tracker.example" not in str(lst))
    prof = act(tb, "userProfile", {"userId": ua})
    check("B7 legacy outside URL hidden in userProfile", "tracker.example" not in str(prof), str(prof)[:300])
    set_user(ua, photoUrl="files/" + mine, photoId=mine)
    check("B7 a real avatar still reaches other users", data(tb, "forumTopic", {"topicId": tid})["topic"]["author"]["photoUrl"] == "files/" + mine)
    check("B7 explicit removal still works", act(ta, "updateProfile", {"displayName": "cm a", "photoId": ""}).get("ok") is True
          and me(tb, ua).get("photoUrl") is None)
    return tid


def b5_files(at, ta, ua, tb, ub):
    """Everything the apps (1.3.1 and 1.4.x) do with files, against a second instance started with
    FILES_ENFORCE=1 — and the same on the normal instance, to show the switch is what changes it."""
    avatar = upload(ta)
    act(ta, "updateProfile", {"displayName": "cm a", "photoId": avatar})
    kid = data(ta, "addChild", {"nickname": "ک", "ageRange": "3-5"}).get("id") or \
        next(c["id"] for c in state(ta)["children"])
    secret = upload(ta)
    r = act(ta, "recSave", {"kind": "emergency", "data": {"childId": kid, "name": "ک", "photo": secret}})
    check("B5 emergency card with a private photo saved", r.get("ok") is True, r)
    pic = upload(ta)
    tid = data(ta, "forumCreateTopic", {"forumId": "heart", "title": "گفتگوی آزمایشیِ فایل", "body": "متنِ کافیِ آزمایشی برای فایل", "images": [pic]})["id"]
    video = upload(ta, raw=T.MP4, mime="video/mp4", path="/api/upload-video")
    code, j, _ = jhttp("POST", "/api/upload", token=tb, raw=T.PNG, headers={"Content-Type": "image/png", "X-Filename": "t"})
    check("B5 upload reply carries a signed URL", "sig=" in (j.get("file") or {}).get("signedUrl", ""), j)

    def signed(tok, fid):
        return (act(tok, "fileUrls", {"ids": [fid]}).get("data") or {}).get("urls", {}).get(fid)

    def run(label, base, enforced):
        saved, T.BASE = T.BASE, base
        try:
            open_code = 403 if enforced else 200
            for name, fid in (("private emergency photo", secret), ("avatar", avatar), ("forum image", pic)):
                check("B5[%s] %s: bare URL, no login" % (label, name), http("GET", "/api/files/" + fid)[0] == open_code)
            check("B5[%s] private photo: bare URL + ANOTHER user's login" % label, http("GET", "/api/files/" + secret, token=tb)[0] == open_code)
            check("B5[%s] private photo: owner's login" % label, http("GET", "/api/files/" + secret, token=ta)[0] == 200)
            check("B5[%s] private photo: admin's login" % label, http("GET", "/api/files/" + secret, token=at)[0] == 200)
            check("B5[%s] private photo: server signs it for the owner" % label, bool(signed(ta, secret)))
            check("B5[%s] private photo: server does NOT sign it for another user" % label, signed(tb, secret) is None)
            # what the app does: ask for signed URLs, then fetch them with no Authorization header
            for name, fid, tok in (("own private photo", secret, ta), ("someone's avatar", avatar, tb), ("forum image", pic, tb)):
                u = signed(tok, fid)
                check("B5[%s] app flow (fileUrls -> GET) for %s" % (label, name), bool(u) and http("GET", "/api/" + u)[0] == 200, u)
            u = signed(tb, avatar)
            check("B5[%s] a signature does not open a different file" % label,
                  http("GET", "/api/files/%s?%s" % (secret, u.split("?", 1)[1]))[0] == 403)
            check("B5[%s] broken signature -> 403 (the app then re-signs)" % label, http("GET", "/api/" + u[:-4] + "0000")[0] == 403)
            # avatar / forum image by login only (old screens that send a Bearer)
            check("B5[%s] avatar with another user's login" % label, http("GET", "/api/files/" + avatar, token=tb)[0] == 200)
            check("B5[%s] forum image with another user's login" % label, http("GET", "/api/files/" + pic, token=tb)[0] == 200)
            if video:
                u = signed(ta, video)
                code, body, hd = http("GET", "/api/" + u, headers={"Range": "bytes=0-99"})
                check("B5[%s] video: signed URL with Range (player seek)" % label, code == 206 and len(body) == 100, code)
                check("B5[%s] video: bare URL, no login" % label, http("GET", "/api/files/" + video, headers={"Range": "bytes=0-9"})[0] in ((403,) if enforced else (200, 206)))
            check("B5[%s] missing file -> 404" % label, http("GET", "/api/files/" + "e" * 32)[0] == 404)
            # published learning item cover: readable by any signed-in user through fileUrls
            row = H._q("SELECT cover_id FROM l_items WHERE status='PUBLISHED' AND cover_id IS NOT NULL LIMIT 1", one=True)
            if row:
                u = signed(tb, row["cover_id"])
                check("B5[%s] article cover via fileUrls" % label, bool(u) and http("GET", "/api/" + u)[0] in (200, 404), u)
        finally:
            T.BASE = saved

    run("enforce off", T.BASE, False)
    port = 8159
    env = dict(os.environ, PORT=str(port), FILES_ENFORCE="1")
    proc = subprocess.Popen([sys.executable, os.path.join(T.HERE, "hamrah-api.py")], env=env, cwd=T.HERE,
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
        check("B5 enforcing instance started", up)
        if up:
            run("enforce ON", base2, True)
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=10)
        except Exception:
            proc.kill()
    # the switch is also read from .env (it used to be process-environment only)
    out = subprocess.run([sys.executable, "-c",
                          "import importlib.util,sys,os\n"
                          "sys.argv=['x']\n"
                          "s=importlib.util.spec_from_file_location('h','hamrah-api.py');m=importlib.util.module_from_spec(s)\n"
                          "s.loader.exec_module(m);m.ENV['FILES_ENFORCE']='1'\n"
                          "print(m._cfg('FILES_ENFORCE'))"],
                         cwd=T.HERE, capture_output=True, text=True, env={k: v for k, v in os.environ.items() if k != "FILES_ENFORCE"})
    check("B5 FILES_ENFORCE is read through _cfg (.env works)", out.stdout.strip() == "1" and
          '_cfg("FILES_ENFORCE")' in open(os.path.join(T.HERE, "hamrah-api.py"), encoding="utf-8").read(), out.stderr[-300:])
    return tid


def i1_features(at, ta):
    """Hiding the empty sections is the existing feature switch; it must be reversible and must not
    take the forum, learning, tools, child file, cards or places with it."""
    off = ["directory", "booking", "groups", "donations", "courses"]
    before = set(jhttp("GET", "/api/features", token=ta)[1].get("disabled") or [])
    try:
        for f in off:
            check("I1 admin switches %s off" % f, act(at, "setFeatureEnabled", {"id": f, "enabled": False}).get("ok") is True)
        dis = set(jhttp("GET", "/api/features", token=ta)[1].get("disabled") or [])
        check("I1 /api/features lists the five sections", set(off) <= dis, dis)
        check("I1 /api/features without login lists them too", set(off) <= set(jhttp("GET", "/api/features")[1].get("disabled") or []))
        check("I1 a normal user cannot switch features", act(ta, "setFeatureEnabled", {"id": "groups", "enabled": True}).get("ok") is False)
        check("I1 core sections cannot be switched off", act(at, "setFeatureEnabled", {"id": "home", "enabled": False}).get("ok") is False)
        # still working
        check("I1 forum tree still served", len(data(ta, "forumTree")["forums"]) >= 8)
        check("I1 forum home still served", act(ta, "forumHome").get("ok") is True)
        t = act(ta, "forumCreateTopic", {"forumId": "heart", "title": "گفتگوی آزمایشیِ بخش‌ها", "body": "متنِ کافیِ آزمایشی برای بخش‌ها"})
        check("I1 posting in the forum still works", t.get("ok") is True, t)
        check("I1 learning library still served", act(ta, "learnCategories").get("ok") is True)
        for kind in ("diary", "med", "schedule", "aac", "emergency", "place"):
            check("I1 recList %s still works" % kind, act(ta, "recList", {"kind": kind}).get("ok") is True)
        check("I1 state still served", bool(state(ta).get("users")))
        # switched off
        for name, args in (("recList", {"kind": "slot"}), ("recList", {"kind": "course"}), ("donationList", {}),
                           ("groupDirectory", {})):
            r = act(ta, name, args)
            check("I1 %s %s refused while off" % (name, args.get("kind", "")), r.get("ok") is False and "فعال نیست" in r.get("error", ""), r)
    finally:
        for f in off:
            if f not in before:
                act(at, "setFeatureEnabled", {"id": f, "enabled": True})
    after = set(jhttp("GET", "/api/features", token=ta)[1].get("disabled") or [])
    check("I1 switching back on restores the previous set", after == before, (before, after))
    check("I1 sections work again after switching back", act(ta, "recList", {"kind": "course"}).get("ok") is True)


def main():
    admin_mail = "cm-%s-admin@example.invalid" % T.RUN
    subprocess.run([sys.executable, os.path.join(T.HERE, "hamrah-api.py"), "create-admin", admin_mail, "adminpw-" + T.RUN, "cm admin"],
                   check=True, capture_output=True, cwd=T.HERE)
    _, j, _ = jhttp("POST", "/api/auth/login", {"email": admin_mail, "password": "adminpw-" + T.RUN})
    at, au = j["token"], j["user"]["id"]
    T.made.append((at, au))
    ta, ua = T.register("a")
    tb, ub = T.register("b")
    b1_delete_device_account(ta)
    b2_renewal(ta, ua)
    b6_names(at, ta, ua, tb)
    b7_photo(ta, ua, tb, ub)
    b5_files(at, ta, ua, tb, ub)
    i1_features(at, ta)
    log_path = os.path.join(T.HERE, "run.log")
    log = open(log_path, encoding="utf-8", errors="replace").read() if os.path.exists(log_path) else ""
    check("no server-side tracebacks in run.log", "error in " not in log, log[-600:] if "error in " in log else "")


if __name__ == "__main__":
    try:
        main()
    finally:
        T.cleanup()
    print("urgent: %d passed, %d failed" % (PASS, FAIL))
    sys.exit(1 if FAIL else 0)
