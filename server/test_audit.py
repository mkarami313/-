#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Regression tests for the community-v2 audit fixes (anonymity, counters, authorization, input
validation, file access, privacy). Run ON the server against the TEST instance only:

    cd /home/rasta/hamrah-v2test && BASE=http://127.0.0.1:8151 python3 test_audit.py

Reuses the helpers of test_community.py (same throwaway cm-* accounts, same cleanup).
"""
import os
import sys

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
    """A clean refusal: ok:false, HTTP 200, a Persian message — never a 500."""
    err = r.get("error") or ""
    return r.get("ok") is False and r.get("_code") is None and err != H.GENERIC_ERROR \
        and any("؀" <= ch <= "ۿ" for ch in err)


def topic(token, tid, **kw):
    return data(token, "forumTopic", dict(topicId=tid, **kw))


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
    tc, uc = T.register("c")   # verified professional
    td, ud = T.register("d")
    te, ue = T.register("e")   # deletes their account mid-test

    doc = upload(tc)
    data(tc, "submitVerificationRequest", {"professionalTitle": "گفتاردرمان", "licenseNumber": "1", "documentRefs": [doc]})
    st = jhttp("GET", "/api/state", token=at)[1]["state"]
    vreq = next(v for v in st["verifications"] if v["userId"] == uc)
    data(at, "reviewVerification", {"requestId": vreq["id"], "decision": "VERIFIED", "reason": "ok"})

    # ---- anonymity: the opener of an anonymous topic is not unmasked by their own replies -----
    t_anon = data(ta, "forumCreateTopic", {"forumId": "family_care", "title": "یک درد دل ناشناس", "body": "متنِ ناشناس کافی", "anonymous": True})["id"]
    pb = data(tb, "forumReply", {"topicId": t_anon, "body": "همدلی می‌کنم"})["id"]
    pa = data(ta, "forumReply", {"topicId": t_anon, "body": "ممنون از شما", "quoteId": pb})["id"]
    data(tb, "forumReply", {"topicId": t_anon, "body": "خواهش می‌کنم", "quoteId": pa})
    det = topic(tb, t_anon, page=0)
    mine = next(p for p in det["posts"] if p["id"] == pa)
    check("anon opener's reply stays anonymous", mine["author"]["id"] == "" and mine["author"]["anonymous"], mine["author"])
    q = next(p for p in det["posts"] if p["quote"] and p["quote"]["id"] == pa)
    check("quote of anon opener stays anonymous", q["quote"]["authorName"] == "کاربرِ ناشناس", q["quote"])
    check("anon opener's own view still shows self", next(p for p in topic(ta, t_anon, page=0)["posts"] if p["id"] == pa)["author"]["id"] == ua)
    check("admin sees anon opener", next(p for p in topic(at, t_anon, page=0)["posts"] if p["id"] == pa)["author"]["id"] == ua)
    check("notification doesn't name anon opener", all("cm a" not in (n.get("body") or "") for n in notes(tb) if n.get("relatedId") == t_anon),
          [n.get("body") for n in notes(tb) if n.get("relatedId") == t_anon])
    check("profile stats skip replies in own anon topics", data(tb, "userProfile", {"userId": ua})["stats"]["replies"] == 0)

    # ---- counters stay consistent through hide + delete ------------------------------------------
    t_c = data(ta, "forumCreateTopic", {"forumId": "clinic_speech", "title": "پرسش برای شمارنده", "body": "متن کافی برای آزمون"})["id"]
    p1 = data(tc, "forumReply", {"topicId": t_c, "body": "پاسخ متخصص یک"})["id"]
    p2 = data(ta, "forumReply", {"topicId": t_c, "body": "پاسخ پرسش‌کننده"})["id"]
    data(ta, "forumModerate", {"topicId": t_c, "op": "accept", "postId": p1})
    check("hide expert reply", act(at, "hideContent", {"type": "POST", "contentId": p1, "reason": "تست"}).get("ok"))
    tt = topic(ta, t_c)["topic"]
    check("hide: reply/expert counts recomputed", tt["replyCount"] == 1 and tt["expertReplyCount"] == 0, tt)
    check("hide: best answer cleared", tt["acceptedPostId"] is None, tt["acceptedPostId"])
    check("author deletes hidden reply", act(tc, "forumDelete", {"id": p1}).get("ok"))
    check("no double decrement after hide+delete", topic(ta, t_c)["topic"]["replyCount"] == 1)
    check("accept own reply refused", rejected(act(ta, "forumModerate", {"topicId": t_c, "op": "accept", "postId": p2})))

    # ---- likes: hidden content can't be liked; deleted accounts take their likes with them ----------
    t_l = data(ta, "forumCreateTopic", {"forumId": "chat", "title": "موضوعِ پسندها", "body": "متن کافی برای پسند"})["id"]
    pl = data(tb, "forumReply", {"topicId": t_l, "body": "پاسخی برای پنهان شدن"})["id"]
    act(at, "hideContent", {"type": "POST", "contentId": pl, "reason": "تست"})
    check("cannot like hidden reply", rejected(act(ta, "forumLike", {"id": pl})))
    data(te, "forumLike", {"id": t_l})
    check("like counted", topic(ta, t_l)["topic"]["likeCount"] == 1)
    check("delete account", act(te, "deleteAccount", {"password": "pw-" + T.RUN + "-123"}).get("ok"))
    T.made.remove((te, ue))
    check("deleted account's like removed from counter", topic(ta, t_l)["topic"]["likeCount"] == 0)

    # ---- polls: wrong types / out-of-range / locked are clean refusals --------------------------------
    t_p = data(ta, "forumCreateTopic", {"forumId": "chat", "title": "نظرسنجی آزمون", "body": "نظر بدهید لطفاً",
                                        "poll": {"question": "کدام؟", "options": ["الف", "ب"], "days": "x"}})["id"]
    check("poll options as string refused", rejected(act(tb, "forumVote", {"topicId": t_p, "options": "1"})))
    check("poll option out of range refused", rejected(act(tb, "forumVote", {"topicId": t_p, "options": [5]})))
    check("poll option dict refused", rejected(act(tb, "forumVote", {"topicId": t_p, "options": [{"a": 1}]})))
    check("poll options as string on create refused", rejected(act(ta, "forumCreateTopic", {"forumId": "chat", "title": "نظرسنجی بد",
                                                                                            "body": "متن کافی", "poll": {"options": "ab"}})))
    act(at, "forumModerate", {"topicId": t_p, "op": "lock"})
    check("locked poll refuses votes", rejected(act(tb, "forumVote", {"topicId": t_p, "options": [0]})))

    # ---- wrong-typed args never 500 ------------------------------------------------------------------
    junk = {"x": 1}
    cases = [
        ("forumTopics", {"page": "abc", "size": [], "forumId": junk}), ("forumTopics", {"groupId": 5, "authorId": junk}),
        ("forumTopic", {"topicId": t_l, "page": "zz"}), ("forumTopic", {"topicId": junk}),
        ("forumCreateTopic", {"forumId": junk, "title": "عنوان سالم", "body": "متن سالم کافی"}),
        ("forumCreateTopic", {"forumId": "chat", "title": 12345, "body": ["x"]}),
        ("forumReply", {"topicId": t_l, "body": {"a": 1}}), ("forumEdit", {"id": junk, "body": 3}),
        ("forumLike", {"id": [1]}), ("forumModerate", {"topicId": t_l, "op": "move", "forumId": junk}),
        ("learnList", {"page": "x", "categoryId": junk}), ("learnGet", {"id": 7}),
        ("learnSubmit", {"kind": "article", "categoryId": junk, "title": "عنوان مطلب", "body": "x" * 60}),
        ("learnReview", {"id": junk, "op": "approve"}), ("learnDelete", {"id": junk}), ("learnSave", {"id": [1]}),
        ("learnCategorySave", {"title": "دستهٔ تست", "sort": "abc", "id": junk}),
        ("reviewList", {"targetType": "pro", "targetId": junk}), ("reviewSummary", {"targetType": "pro", "ids": 5}),
        ("reviewDelete", {"targetType": "pro", "targetId": junk}),
        ("groupInfo", {"groupId": junk}), ("groupUpdate", {"groupId": junk}), ("joinOrRequest", {"groupId": junk}),
        ("userProfile", {"userId": junk}), ("fileUrls", {"ids": "abc"}), ("search", {"q": junk}),
        ("hideContent", {"type": "TOPIC", "contentId": junk, "reason": "x"}),
    ]
    for name, args in cases:
        r = act(at if name in ("learnReview", "learnCategorySave", "hideContent") else tb, name, args)
        check("no 500: %s" % name, r.get("_code") is None and (r.get("ok") or rejected(r)), r)

    # ---- private groups: their pictures stay inside --------------------------------------------------
    gid = data(ta, "createGroup", {"title": "گروه خصوصی تصویر", "visibility": "PRIVATE"})["id"]
    act(tb, "joinOrRequest", {"groupId": gid})
    act(ta, "approveMember", {"groupId": gid, "userId": ub})
    gimg = upload(ta)
    data(ta, "forumCreateTopic", {"groupId": gid, "title": "عکسِ جلسه", "body": "عکسِ جلسهٔ گروه", "images": [gimg]})
    check("member gets private group image url", gimg in data(tb, "fileUrls", {"ids": [gimg]})["urls"])
    check("outsider doesn't get private group image url", gimg not in data(td, "fileUrls", {"ids": [gimg]})["urls"])

    # ---- groups: roles, visibility and archive authority ---------------------------------------------
    check("bad visibility refused", rejected(act(ta, "createGroup", {"title": "گروه نامعتبر", "visibility": "SECRET"})))
    check("overlong group title refused", rejected(act(ta, "createGroup", {"title": "ع" * 200})))
    act(tc, "joinOrRequest", {"groupId": gid})
    act(ta, "approveMember", {"groupId": gid, "userId": uc})
    data(ta, "groupSetRole", {"groupId": gid, "userId": ub, "role": "MODERATOR"})
    data(ta, "groupSetRole", {"groupId": gid, "userId": uc, "role": "MODERATOR"})
    check("moderator can't remove another moderator", rejected(act(tb, "removeMember", {"groupId": gid, "userId": uc})))
    check("moderator can't flip visibility", rejected(act(tb, "groupUpdate", {"groupId": gid, "title": "گروه خصوصی تصویر", "visibility": "PUBLIC"})))
    check("moderator may edit with same visibility", act(tb, "groupUpdate", {"groupId": gid, "title": "گروه خصوصی تصویر ۲", "visibility": "PRIVATE"}).get("ok"))
    check("owner can't demote self", rejected(act(ta, "groupSetRole", {"groupId": gid, "userId": ua, "role": "MEMBER"})))
    check("owner still owner", data(ta, "groupInfo", {"groupId": gid})["myRole"] == "OWNER")
    check("owner removes moderator", act(ta, "removeMember", {"groupId": gid, "userId": uc}).get("ok"))
    gq = data(ta, "createGroup", {"title": "گروه برای بستن", "visibility": "PUBLIC"})["id"]
    act(at, "hideContent", {"type": "GROUP", "contentId": gq, "reason": "تست"})
    check("owner can't reopen an admin-closed group", rejected(act(ta, "groupArchive", {"groupId": gq, "restore": True})))
    check("admin can reopen", act(at, "groupArchive", {"groupId": gq, "restore": True}).get("ok"))
    act(td, "joinOrRequest", {"groupId": gq})
    act(td, "blockUser", {"targetUserId": ub})
    act(tb, "joinOrRequest", {"groupId": gq})
    check("member list hides blocked users", all(m["id"] != ub for m in data(td, "groupInfo", {"groupId": gq})["members"]))
    act(td, "unblockUser", {"targetUserId": ub})

    # ---- edits / learning library ----------------------------------------------------------------------
    t_r = data(tb, "forumCreateTopic", {"forumId": "chat", "title": "موضوعی برای حذف", "body": "متن کافی برای حذف"})["id"]
    act(tb, "forumDelete", {"id": t_r})
    check("can't edit a removed topic", rejected(act(tb, "forumEdit", {"id": t_r, "body": "ویرایشِ پس از حذف"})))
    cov = upload(tb)
    art = data(tb, "learnSubmit", {"kind": "article", "categoryId": "speech", "title": "مقالهٔ آزمون جلد",
                                   "body": "متن مقاله " * 20, "coverId": cov})["id"]
    check("admin edits a user's item keeping its cover", act(at, "learnSubmit", {"id": art, "kind": "article", "categoryId": "speech",
                                                                                 "title": "مقالهٔ آزمون جلد", "body": "متن مقاله " * 21, "coverId": cov}).get("ok"))
    act(tb, "learnDelete", {"id": art})
    check("removed item is gone even for its author", rejected(act(tb, "learnGet", {"id": art})))

    # ---- privacy of professionals ------------------------------------------------------------------------
    data(tc, "upsertProfessionalProfile", {"title": "گفتاردرمان", "city": "یاسوج%s" % T.RUN[:3], "workAreas": []})
    u = jhttp("GET", "/api/state", token=tc)[1]["state"]["users"]
    me = next(x for x in u if x["id"] == uc)
    act(tc, "updateProfile", {"displayName": me["displayName"], "city": "یاسوج%s" % T.RUN[:3]})
    pf = data(tb, "userProfile", {"userId": uc})
    check("profile hides pro city when showCity is off", pf["professional"] and pf["professional"].get("city") is None, pf["professional"])
    check("search doesn't match a private city", all(h["id"] != uc for h in data(tb, "search", {"q": "یاسوج%s" % T.RUN[:3]})["items"]))

    # ---- misc authorization -------------------------------------------------------------------------------
    act(td, "blockUser", {"targetUserId": uc})
    check("can't review a pro across a block", rejected(act(td, "reviewSave", {"targetType": "pro", "targetId": uc, "rating": 1})))
    cid = data(tb, "requestConversation", {"recipientUserId": uc})["id"]
    act(tc, "declineConversation", {"conversationId": cid})
    check("declined conversation can't be accepted later", rejected(act(tc, "acceptConversation", {"conversationId": cid})))
    t_w = data(ta, "forumCreateTopic", {"forumId": "chat", "title": "عنوان بی‌خط‌زیر", "body": "متن ساده"})["id"]
    check("LIKE wildcards are literal", all(t["id"] != t_w for t in data(tb, "forumTopics", {"q": "_"})["items"]))
    exp = data(ta, "exportMyData")["json"]
    check("export carries forum topics", t_w in exp and "forumReplies" in exp)

    # ---- coordinator findings ------------------------------------------------------------------------------
    t_pg = data(ta, "forumCreateTopic", {"forumId": "chat", "title": "شمارش صفحه‌ها", "body": "متن کافی صفحه"})["id"]
    check("first reply -> 1 page", data(tb, "forumReply", {"topicId": t_pg, "body": "اولین پاسخ"})["pages"] == 1)
    act(tb, "blockUser", {"targetUserId": ud})
    pr = act(tb, "userProfile", {"userId": ud})
    check("profile of someone I blocked opens (to unblock)", pr.get("ok") and pr["data"]["blockedByMe"] is True
          and pr["data"]["canMessage"] is False, pr)
    check("profile of someone who blocked me stays hidden", rejected(act(td, "userProfile", {"userId": ub})))
    act(tb, "unblockUser", {"targetUserId": ud})
    g2 = data(ta, "createGroup", {"title": "گروه دسته‌دار", "visibility": "PRIVATE", "category": "ورزش", "rules": "قانون یک"})["id"]
    act(td, "joinOrRequest", {"groupId": g2})
    act(tc, "joinOrRequest", {"groupId": g2})
    check("site admin approves in any group", act(at, "approveMember", {"groupId": g2, "userId": ud}).get("ok")
          and data(td, "groupInfo", {"groupId": g2})["myStatus"] == "ACTIVE")
    check("site admin removes in any group", act(at, "removeMember", {"groupId": g2, "userId": ud}).get("ok"))
    check("partial groupUpdate", act(ta, "groupUpdate", {"groupId": g2, "title": "گروه دسته‌دار ۲"}).get("ok"))
    g2info = data(ta, "groupInfo", {"groupId": g2})["group"]
    check("absent fields kept by groupUpdate", g2info["category"] == "ورزش" and g2info["rules"] == "قانون یک", g2info)
    n0 = data(ta, "groupInfo", {"groupId": g2})["group"]["memberCount"]
    check("open the group", act(ta, "groupUpdate", {"groupId": g2, "title": "گروه دسته‌دار ۲", "visibility": "PUBLIC"}).get("ok"))
    gi = data(tc, "groupInfo", {"groupId": g2})
    check("opening a group admits pending requests", gi["myStatus"] == "ACTIVE" and gi["group"]["memberCount"] == n0 + 1, gi)

    # nothing above may have produced a server-side 500
    log = open(os.path.join(T.HERE, "run.log"), encoding="utf-8", errors="replace").read() if os.path.exists(os.path.join(T.HERE, "run.log")) else ""
    check("no 'error in' tracebacks in run.log", "error in POST /api/action" not in log)


if __name__ == "__main__":
    try:
        main()
    finally:
        T.cleanup()
    print("audit: %d passed, %d failed" % (PASS, FAIL))
    sys.exit(1 if FAIL else 0)
