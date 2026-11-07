#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Regression tests for the second audit pass (snapshot membership privacy, notification flooding,
Persian search normalisation, blocks in donations/groups, double submits, transactions, the rate
limiter, Tehran day boundaries, notification pruning, legacy-action input limits). Run ON the server
against the TEST instance only, on a freshly restarted server (rate limits are in memory):

    cd /home/rasta/hamrah-v2test && BASE=http://127.0.0.1:8151 python3 test_audit2.py
"""
import os
import sys
import time
import http.client
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import test_community as T  # noqa: E402  (refuses to run unless DB_DSN is the v2test database)

H, act, data, jhttp, upload, notes = T.H, T.act, T.data, T.jhttp, T.upload, T.notes
PASS = FAIL = 0


def check(name, cond, extra=""):
    global PASS, FAIL
    if cond:
        PASS += 1
    else:
        FAIL += 1
        print("FAIL: %s %s" % (name, str(extra)[:400]))


def rejected(r):
    err = r.get("error") or ""
    return r.get("ok") is False and r.get("_code") is None and err != H.GENERIC_ERROR \
        and any("؀" <= ch <= "ۿ" for ch in err)


def state(token):
    return jhttp("GET", "/api/state", token=token)[1]["state"]


def main():
    import subprocess
    admin_mail = "cm-%s-admin@example.invalid" % T.RUN
    subprocess.run([sys.executable, os.path.join(T.HERE, "hamrah-api.py"), "create-admin", admin_mail, "adminpw-" + T.RUN, "cm admin"],
                   check=True, capture_output=True, cwd=T.HERE)
    _, j, _ = jhttp("POST", "/api/auth/login", {"email": admin_mail, "password": "adminpw-" + T.RUN})
    at, au = j["token"], j["user"]["id"]
    T.made.append((at, au))
    ta, ua = T.register("a")
    tb, ub = T.register("b")
    tc, uc = T.register("c")
    td, ud = T.register("d")
    tf, uf = T.register("f")

    # ---- snapshot: a pending request doesn't reveal a private group's member list -------------
    g = data(ta, "createGroup", {"title": "گروه خصوصی آزمون", "visibility": "PRIVATE"})["id"]
    act(tb, "joinOrRequest", {"groupId": g})
    data(ta, "approveMember", {"groupId": g, "userId": ub})
    act(tc, "joinOrRequest", {"groupId": g})
    ms_c = [m for m in state(tc)["memberships"] if m["groupId"] == g]
    check("requester sees only own membership row", [m["userId"] for m in ms_c] == [uc], ms_c)
    check("requester's users list has no private members", ub not in {u["id"] for u in state(tc)["users"]})
    ms_b = {m["userId"]: m["status"] for m in state(tb)["memberships"] if m["groupId"] == g}
    check("member sees members, not others' pending requests", ms_b == {ua: "ACTIVE", ub: "ACTIVE"}, ms_b)
    ms_a = {m["userId"]: m["status"] for m in state(ta)["memberships"] if m["groupId"] == g}
    check("owner sees the pending request", ms_a.get(uc) == "REQUESTED", ms_a)

    # ---- join-request spam: request -> cancel -> request pings the owner once ------------------
    act(tc, "groupCancelRequest", {"groupId": g})
    act(tc, "joinOrRequest", {"groupId": g})
    act(tc, "groupCancelRequest", {"groupId": g})
    check("re-request still works", act(tc, "joinOrRequest", {"groupId": g}).get("ok"))
    n_join = [n for n in notes(ta) if n["relatedId"] == g and n["title"] == "درخواست عضویت"]
    check("owner pinged once per requester", len(n_join) == 2, len(n_join))  # b's request + c's first one

    # ---- blocked by the owner: no join request into their private group ---------------------
    data(ta, "blockUser", {"targetUserId": ud})
    check("blocked user can't request a private group", rejected(act(td, "joinOrRequest", {"groupId": g})))

    # ---- donations respect blocks ------------------------------------------------------------
    did = data(ta, "donationCreate", {"title": "کتاب کار آزمون", "description": "سالم", "category": H.DONATION_CATEGORIES[3],
                                      "condition": "GOOD", "city": "تهران"})["id"]
    check("donation listed for others", any(i["id"] == did for i in data(tb, "donationList")["items"]))
    check("blocked user doesn't see donation", all(i["id"] != did for i in data(td, "donationList")["items"]))
    check("blocked user can't open donation", rejected(act(td, "donationGet", {"id": did})))
    check("blocked user can't request donation", rejected(act(td, "donationRequest", {"id": did})))
    check("search hides blocked owner's donation", all(i["id"] != did for i in data(td, "search", {"q": "کتاب کار"})["items"]))
    check("owner still lists own donation", any(i["id"] == did for i in data(ta, "donationList", {"mine": True})["items"]))
    data(ta, "unblockUser", {"targetUserId": ud})

    # ---- Persian normalisation in search -------------------------------------------------------
    tn = data(ta, "forumCreateTopic", {"forumId": "daily_sleep", "title": "خواب كودك و بازي",  # Arabic kaf/yeh
                                       "body": "شب‌ها نمی‌خوابد و می‌خواهم راهکار بدانم"})["id"]
    hits = lambda q: [t["id"] for t in data(tb, "forumTopics", {"q": q})["items"]]  # noqa: E731
    check("Persian kaf/yeh finds Arabic-typed title", tn in hits("کودک و بازی"), hits("کودک و بازی"))
    check("query without ZWNJ finds text with ZWNJ", tn in hits("میخواهم راهکار"))
    check("query with ZWNJ finds it too", tn in hits("می‌خواهم راهکار"))
    check("Arabic-typed query finds Persian text", tn in hits("ميخواهم"))
    check("global search normalises too", any(i["id"] == tn for i in data(tb, "search", {"q": "كودك"})["items"]))
    check("LIKE wildcards still literal", tn not in hits("%%"))
    gn = data(ta, "createGroup", {"title": "والدين كرج", "visibility": "PUBLIC"})["id"]
    check("group directory normalises", any(x["id"] == gn for x in data(tb, "groupDirectory", {"q": "والدین کرج"})["items"]))

    # ---- double submit: identical topic / reply within the window is stored once ------------
    args = {"forumId": "daily_eat", "title": "غذا نخوردن کودک", "body": "هیچ غذایی جز ماکارونی نمی‌خورد"}
    t1 = data(tb, "forumCreateTopic", args)["id"]
    check("same topic twice -> one topic", data(tb, "forumCreateTopic", args)["id"] == t1)
    r1 = data(tc, "forumReply", {"topicId": t1, "body": "ما هم همین مشکل را داشتیم"})
    r2 = data(tc, "forumReply", {"topicId": t1, "body": "ما هم همین مشکل را داشتیم"})
    check("same reply twice -> one reply", r1["id"] == r2["id"], (r1, r2))
    det = data(tb, "forumTopic", {"topicId": t1, "page": 0})
    check("reply_count counted once", det["topic"]["replyCount"] == 1 and det["total"] == 1, (det["topic"]["replyCount"], det["total"]))

    # ---- follower / author notifications are throttled per thread -----------------------------
    data(tf, "forumFollow", {"topicId": t1})
    for body in ("پاسخ دوم", "پاسخ سوم", "پاسخ چهارم"):
        data(tc, "forumReply", {"topicId": t1, "body": body})
    nf = [n for n in notes(tf) if n["relatedId"] == t1]
    check("follower gets one notification per burst", len(nf) == 1, len(nf))
    na = [n for n in notes(tb) if n["relatedId"] == t1]
    check("author gets one notification per burst", len(na) == 1, len(na))
    data(ta, "forumReply", {"topicId": t1, "body": "نقل‌قول", "quoteId": r1["id"]})
    check("a quote still always notifies", any(n["title"] == "پاسخ به نوشتهٔ شما" for n in notes(tc) if n["relatedId"] == t1))

    # ---- conversations: message bursts and end/re-request loops don't flood --------------------
    cid = data(tb, "requestConversation", {"recipientUserId": ud})["id"]
    data(td, "acceptConversation", {"conversationId": cid})
    for i in range(4):
        data(tb, "sendMessage", {"conversationId": cid, "body": "پیام %d" % i})
    nm = [n for n in notes(td) if n["relatedId"] == cid and n["title"] == "پیام جدید"]
    check("message burst -> one notification", len(nm) == 1, len(nm))
    st_d = state(td)
    check("snapshot still carries the messages", sum(1 for m in st_d["messages"] if m["conversationId"] == cid) == 4)
    loops = []
    for _ in range(7):
        act(tb, "endConversation", {"conversationId": cid})
        loops.append(act(tb, "requestConversation", {"recipientUserId": ud}).get("ok"))
    check("end/re-request loop is capped", loops.count(True) == 4 and not loops[-1], loops)  # 1 + 4 = 5 per pair/day
    nreq = [n for n in notes(td) if n["relatedId"] == cid and n["title"] == "درخواست گفتگو"]
    check("recipient pinged at most 5 times", len(nreq) <= 5, len(nreq))

    # ---- markRead / markAllRead still work with the indexed lookup ---------------------------
    n0 = notes(td)[0]
    data(td, "markRead", {"notificationId": n0["id"]})
    check("markRead", next(n for n in notes(td) if n["id"] == n0["id"])["read"] is True)
    data(td, "markAllRead")
    check("markAllRead", all(n["read"] for n in notes(td)))
    check("markRead of someone else's id is a no-op", act(tb, "markRead", {"notificationId": n0["id"]}).get("ok"))

    # ---- groupUpdate: a moderator sending the cover back as a signed URL keeps it -----------
    cov = upload(ta)
    data(ta, "groupUpdate", {"groupId": g, "coverId": cov})
    data(ta, "groupSetRole", {"groupId": g, "userId": ub, "role": "MODERATOR"})
    signed = data(tb, "fileUrls", {"ids": [cov]})["urls"].get(cov, "files/%s" % cov)
    check("moderator re-sends cover as URL", act(tb, "groupUpdate", {"groupId": g, "title": "گروه خصوصی آزمون ۲", "coverId": signed}).get("ok"))
    check("cover unchanged", data(ta, "groupInfo", {"groupId": g})["group"]["coverId"] == cov)
    check("garbage cover id refused", rejected(act(ta, "groupUpdate", {"groupId": g, "coverId": "not-a-file"})))

    # ---- legacy v1 actions: bounded input, hidden targets -------------------------------------
    qid = data(ta, "createQuestion", {"title": "پرسش قدیمی", "body": "متن پرسش قدیمی"})["id"]
    check("editQuestion empty title refused", rejected(act(ta, "editQuestion", {"questionId": qid, "title": "", "body": "متن"})))
    check("editQuestion huge body refused", rejected(act(ta, "editQuestion", {"questionId": qid, "title": "عنوان", "body": "x" * 30000})))
    check("createAnswer huge body refused", rejected(act(tb, "createAnswer", {"questionId": qid, "body": "x" * 20000})))
    data(at, "hideContent", {"type": "QUESTION", "contentId": qid, "reason": "آزمون"})
    check("createAnswer on hidden question refused", rejected(act(tb, "createAnswer", {"questionId": qid, "body": "پاسخ"})))
    check("createPost huge body refused", rejected(act(ta, "createPost", {"groupId": g, "body": "x" * 20000})))

    # ---- printable report has its own rate bucket (it used to share createReport's) ----------
    kid = next(c["id"] for c in act(ta, "addChild", {"nickname": "کودکِ گزارش"})["state"]["children"] if c["nickname"] == "کودکِ گزارش")
    for _ in range(31):
        jhttp("GET", "/api/report?childId=" + kid, token=ta)
    check("31st report page is rate-limited", jhttp("GET", "/api/report?childId=" + kid, token=ta)[0] == 400)
    check("content report still possible", act(ta, "createReport", {"contentType": "TOPIC", "contentId": t1, "reason": "SPAM"}).get("ok"))

    # ---- malformed Content-Length: a clean error, never a 500 -------------------------------
    u = urllib.parse.urlparse(T.BASE)
    for path in ("/api/action", "/api/upload"):
        c = http.client.HTTPConnection(u.hostname, u.port, timeout=10)
        c.putrequest("POST", path)
        c.putheader("Authorization", "Bearer " + ta)
        c.putheader("Content-Length", "abc")
        c.endheaders()
        code = c.getresponse().status
        c.close()
        check("bad Content-Length on %s is not a 500" % path, code != 500, code)

    # ---- in-process units against the test DB -----------------------------------------------
    # transaction(): an action failing half-way leaves nothing behind
    probe = "probe_" + T.RUN
    try:
        with H.transaction():
            H._x("INSERT INTO docs(collection,id,data) VALUES('config',%s,'{}')", (probe,))
            raise H.ApiError("x")
    except H.ApiError:
        pass
    check("transaction rolls back on error", not H._q("SELECT 1 FROM docs WHERE collection='config' AND id=%s", (probe,), one=True))
    check("connection back in autocommit", H.conn().autocommit is True)
    with H.transaction():
        H._x("INSERT INTO docs(collection,id,data) VALUES('config',%s,'{}')", (probe,))
    check("transaction commits", bool(H._q("SELECT 1 FROM docs WHERE collection='config' AND id=%s", (probe,), one=True)))
    H._x("DELETE FROM docs WHERE collection='config' AND id=%s", (probe,))

    # rate limiter: a memory sweep must not trim a long window with a short one
    rl = H.RateLimiter()
    rl.MAX_KEYS = rl._sweep_at = 10
    check("rl first", rl.allow("long", 1, 86400))
    for i in range(30):
        rl.allow("short%d" % i, 1, 0.01)
    time.sleep(0.05)
    for i in range(100):
        rl.allow("more%d" % i, 1, 0.01)
    check("long-window limit survives the sweep", rl.allow("long", 1, 86400) is False)
    check("expired keys are swept", not any(k.startswith("short") for k in rl._h), len(rl._h))

    # Tehran calendar day, not UTC
    check("tehran_day after 20:30 UTC is the next day", H.tehran_day(1767301200000) == "2026-01-02")  # 2026-01-01T21:00Z
    check("_iso_day uses Tehran", H._iso_day({"createdAt": 1767301200000}) == "2026-01-02")

    # notification pruning: old read ones go, at most NOTIF_MAX_PER_USER kept per user
    ghost = "u_ghost_" + T.RUN
    old = H.now_ms() - H.NOTIF_READ_TTL_MS - 1000
    with H.MUT_LOCK:
        st = H.Store()
        st.put("notifications", {"id": "n_old_" + T.RUN, "userId": ghost, "read": True, "createdAt": old})
        st.put("notifications", {"id": "n_oldunread_" + T.RUN, "userId": ghost, "read": False, "createdAt": old})
        for i in range(H.NOTIF_MAX_PER_USER + 5):
            st.put("notifications", {"id": "n_%s_%d" % (T.RUN, i), "userId": ghost, "read": False, "createdAt": H.now_ms() + i})
    H.prune_notifications()
    left = H._q("SELECT id FROM docs WHERE collection='notifications' AND data->>'userId'=%s", (ghost,))
    ids = {r["id"] for r in left}
    check("per-user cap", len(ids) == H.NOTIF_MAX_PER_USER, len(ids))
    check("old read notification pruned", "n_old_" + T.RUN not in ids)
    check("newest kept", "n_%s_%d" % (T.RUN, H.NOTIF_MAX_PER_USER + 4) in ids)
    H._x("DELETE FROM docs WHERE collection='notifications' AND data->>'userId'=%s", (ghost,))

    # Persian normaliser unit
    check("fa_norm", H.fa_norm("مي‌خواهم كتاب ۱۲ ـ") == "میخواهم کتاب 12 ", H.fa_norm("مي‌خواهم كتاب ۱۲ ـ"))
    sql_norm = H._q("SELECT " + H.FA_SQL("%s") + " AS v", ("مي‌خواهم كتاب ۱۲",), one=True)["v"]
    check("SQL and Python normalise alike", sql_norm == H.fa_norm("مي‌خواهم كتاب ۱۲"), sql_norm)

    log = open(os.path.join(T.HERE, "run.log"), encoding="utf-8", errors="replace").read() if os.path.exists(os.path.join(T.HERE, "run.log")) else ""
    check("no 'error in' tracebacks in run.log", "error in " not in log, log[log.find("error in "):][:600])


if __name__ == "__main__":
    try:
        main()
    finally:
        T.cleanup()
    print("audit2: %d passed, %d failed" % (PASS, FAIL))
    sys.exit(1 if FAIL else 0)
