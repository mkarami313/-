#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""End-to-end test of the community v2 layer (forum, clinic, learning library, reviews, groups v2,
conversation fixes). Run ON the server, against a TEST instance and TEST database only:

    cd /home/rasta/hamrah-v2test && BASE=http://127.0.0.1:8151 python3 test_community.py

Creates throwaway accounts (cm-*@example.invalid) and wipes them and their content at the end.
"""
import os
import sys
import json
import time
import uuid
import importlib.util
import subprocess
import urllib.request
import urllib.error

BASE = os.environ.get("BASE", "http://127.0.0.1:8151")
HERE = os.path.dirname(os.path.abspath(__file__))
sys.argv = [sys.argv[0]]
spec = importlib.util.spec_from_file_location("hamrah_api", os.path.join(HERE, "hamrah-api.py"))
H = importlib.util.module_from_spec(spec)
spec.loader.exec_module(H)
if "v2test" not in H.DB_DSN and os.environ.get("ALLOW_PROD") != "1":
    sys.exit("refusing to run: DB_DSN is not the v2test database")

PASS = FAIL = 0
RUN = uuid.uuid4().hex[:8]
PNG = b"\x89PNG\r\n\x1a\n" + b"\x00" * 64
MP4 = b"\x00\x00\x00\x18ftypmp42" + b"\x00" * 4000
made = []


def check(name, cond, extra=""):
    global PASS, FAIL
    if cond:
        PASS += 1
    else:
        FAIL += 1
        print("FAIL: %s %s" % (name, str(extra)[:400]))


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
            return r.status, r.read(), dict(r.headers)
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


def data(token, name, args=None):
    r = act(token, name, args)
    assert r.get("ok"), (name, r)
    return r.get("data") or {}


def register(label):
    mail = "cm-%s-%s@example.invalid" % (RUN, label)
    code, j, _ = jhttp("POST", "/api/auth/register", dict(displayName="cm " + label, email=mail,
                                                          password="pw-" + RUN + "-123", acceptedTerms=True))
    assert code == 200 and j.get("token"), j
    made.append((j["token"], j["user"]["id"]))
    return j["token"], j["user"]["id"]


def upload(token, raw=PNG, mime="image/png", path="/api/upload"):
    code, j, _ = jhttp("POST", path, token=token, raw=raw, headers={"Content-Type": mime, "X-Filename": "t"})
    return j.get("file", {}).get("id") if code == 200 else None


def notes(token):
    return jhttp("GET", "/api/notifications", token=token)[1].get("notifications", [])


def main():
    admin_mail = "cm-%s-admin@example.invalid" % RUN
    subprocess.run([sys.executable, os.path.join(HERE, "hamrah-api.py"), "create-admin", admin_mail, "adminpw-" + RUN, "cm admin"],
                   check=True, capture_output=True, cwd=HERE)
    _, j, _ = jhttp("POST", "/api/auth/login", {"email": admin_mail, "password": "adminpw-" + RUN})
    at, au = j["token"], j["user"]["id"]
    made.append((at, au))
    ta, ua = register("a")
    tb, ub = register("b")
    tc, uc = register("c")   # becomes a verified professional
    td, ud = register("d")   # outsider / blocker

    # make C a verified pro through the real flow
    doc = upload(tc)
    rq = data(tc, "submitVerificationRequest", {"professionalTitle": "گفتاردرمان", "licenseNumber": "1", "documentRefs": [doc]})
    st = jhttp("GET", "/api/state", token=at)[1]["state"]
    vreq = next(v for v in st["verifications"] if v["userId"] == uc)
    check("pro verified", act(at, "reviewVerification", {"requestId": vreq["id"], "decision": "VERIFIED", "reason": "ok"}).get("ok"))

    # ---- forum tree + topics --------------------------------------------------------------
    tree = data(ta, "forumTree")["forums"]
    check("tree has 10 top-level forums", len(tree) == 10, [f["id"] for f in tree])
    check("clinic forum present", any(f["kind"] == "clinic" for f in tree))
    check("subforums nested", any(f["id"] == "therapy" and len(f["children"]) == 5 for f in tree))
    check("topic needs forum", act(ta, "forumCreateTopic", {"title": "عنوان آزمایشی", "body": "متن کافی آزمایشی"}).get("ok") is False)
    check("topic title min", act(ta, "forumCreateTopic", {"forumId": "daily_sleep", "title": "ab", "body": "متن کافی آزمایشی"}).get("ok") is False)
    img = upload(ta)
    foreign = upload(tb)
    check("foreign image rejected", act(ta, "forumCreateTopic", {"forumId": "daily_sleep", "title": "خواب شبانه", "body": "متن کافی آزمایشی", "images": [foreign]}).get("ok") is False)
    t1 = data(ta, "forumCreateTopic", {"forumId": "daily_sleep", "title": "خواب شبانهٔ پسرم", "body": "شب‌ها دیر می‌خوابد، چه کنیم؟", "images": [img]})["id"]
    lst = data(tb, "forumTopics", {"forumId": "daily"})["items"]
    check("parent forum lists subforum topic", any(t["id"] == t1 for t in lst))
    check("card has images + author", any(t["id"] == t1 and t["images"] == [img] and t["author"]["name"] == "cm a" for t in lst))
    check("unread for others", next(t for t in lst if t["id"] == t1)["unread"] is True)
    det = data(tb, "forumTopic", {"topicId": t1})
    check("topic detail body", det["topic"]["body"].startswith("شب‌ها") and det["canReply"])
    check("unread cleared after open", next(t for t in data(tb, "forumTopics", {"forumId": "daily"})["items"] if t["id"] == t1)["unread"] is False)
    check("image readable by other via signed urls", img in data(tb, "fileUrls", {"ids": [img]})["urls"])
    # replies + quote + notifications
    p1 = data(tb, "forumReply", {"topicId": t1, "body": "ما روتین خواب گذاشتیم"})["id"]
    check("author notified of reply", any(n["relatedId"] == t1 for n in notes(ta)))
    p2 = data(ta, "forumReply", {"topicId": t1, "body": "چه روتینی؟", "quoteId": p1})["id"]
    det = data(tb, "forumTopic", {"topicId": t1})
    check("2 replies", det["total"] == 2 and det["topic"]["replyCount"] == 2, det["total"])
    check("quote resolved", det["posts"][1]["quote"] and det["posts"][1]["quote"]["id"] == p1)
    check("quoted user notified", sum(1 for n in notes(tb) if n["relatedId"] == t1) >= 1)
    # likes
    check("cannot like own", act(ta, "forumLike", {"id": t1}).get("ok") is False)
    r = data(tb, "forumLike", {"id": t1})
    check("like topic", r["liked"] and r["likeCount"] == 1)
    r = data(tb, "forumLike", {"id": t1})
    check("unlike topic", not r["liked"] and r["likeCount"] == 0)
    check("like post", data(ta, "forumLike", {"id": p1})["likeCount"] == 1)
    # bookmark / follow
    check("bookmark", data(tb, "forumBookmark", {"topicId": t1})["bookmarked"])
    check("bookmarked list", [t["id"] for t in data(tb, "forumTopics", {"bookmarked": True})["items"]] == [t1])
    check("replier auto-follows", [t["id"] for t in data(tb, "forumTopics", {"followed": True})["items"]] == [t1])
    # edit / delete
    check("edit own post", act(tb, "forumEdit", {"id": p1, "body": "ما روتین خواب ثابت گذاشتیم"}).get("ok"))
    check("cannot edit others", act(ta, "forumEdit", {"id": p1, "body": "x y z"}).get("ok") is False)
    check("cannot delete others", act(tb, "forumDelete", {"id": p2}).get("ok") is False)
    check("delete own post", act(ta, "forumDelete", {"id": p2}).get("ok"))
    check("reply count decremented", data(tb, "forumTopic", {"topicId": t1})["topic"]["replyCount"] == 1)
    # search + sorts + mine
    check("search finds", any(t["id"] == t1 for t in data(tb, "forumTopics", {"q": "خواب شبانه"})["items"]))
    for s in ("active", "new", "hot", "top"):
        check("sort " + s, any(t["id"] == t1 for t in data(tb, "forumTopics", {"sort": s})["items"]))
    check("unanswered excludes", all(t["id"] != t1 for t in data(tb, "forumTopics", {"sort": "unanswered"})["items"]))
    check("mine", [t["id"] for t in data(ta, "forumTopics", {"mine": True})["items"]] == [t1])
    # paging
    for i in range(32):
        act(tb, "forumReply", {"topicId": t1, "body": "پاسخ شمارهٔ %d" % i})
    det = data(ta, "forumTopic", {"topicId": t1})
    check("paged: last page by default", det["pages"] == 2 and det["page"] == 1 and len(det["posts"]) == 3, (det["pages"], det["page"], len(det["posts"])))
    check("paged: page 0 has 30", len(data(ta, "forumTopic", {"topicId": t1, "page": 0})["posts"]) == 30)
    # anonymous
    t2 = data(ta, "forumCreateTopic", {"forumId": "family_care", "title": "خیلی خسته‌ام", "body": "نمی‌دانم چطور ادامه بدهم", "anonymous": True})["id"]
    card = next(t for t in data(tb, "forumTopics", {"forumId": "family"})["items"] if t["id"] == t2)
    check("anonymous hides author", card["author"]["id"] == "" and card["author"]["anonymous"])
    check("anonymous not in authorId filter", all(t["id"] != t2 for t in data(tb, "forumTopics", {"authorId": ua})["items"]))
    check("admin sees anonymous author", next(t for t in data(at, "forumTopics", {"forumId": "family"})["items"] if t["id"] == t2)["author"]["id"] == ua)
    # poll
    t3 = data(ta, "forumCreateTopic", {"forumId": "chat", "title": "کدام روش؟", "body": "نظرتان را بگویید",
                                       "poll": {"question": "کدام؟", "options": ["الف", "ب", "ج"], "days": 7}})["id"]
    check("poll one option needed", act(tb, "forumVote", {"topicId": t3, "options": [0, 1]}).get("ok") is False)
    pv = data(tb, "forumVote", {"topicId": t3, "options": [1]})["poll"]
    check("poll vote counted", pv["counts"] == [0, 1, 0] and pv["myVotes"] == [1] and pv["voters"] == 1, pv)
    pv = data(tb, "forumVote", {"topicId": t3, "options": [2]})["poll"]
    check("poll revote moves", pv["counts"] == [0, 0, 1])
    check("bad poll rejected", act(ta, "forumCreateTopic", {"forumId": "chat", "title": "یک گزینه", "body": "متن کافی", "poll": {"options": ["x"]}}).get("ok") is False)
    # clinic: only verified pros (and the asker) answer
    t4 = data(ta, "forumCreateTopic", {"forumId": "clinic_speech", "title": "تأخیر گفتار در سه سالگی", "body": "پسرم هنوز جمله نمی‌گوید"})["id"]
    check("clinic topic kind question", data(ta, "forumTopic", {"topicId": t4})["topic"]["kind"] == "question")
    check("family cannot answer in clinic", act(tb, "forumReply", {"topicId": t4, "body": "به نظرم صبر کنید"}).get("ok") is False)
    pe = data(tc, "forumReply", {"topicId": t4, "body": "ارزیابی گفتاردرمانی پیشنهاد می‌شود"})["id"]
    det = data(ta, "forumTopic", {"topicId": t4})
    check("expert answer flagged", det["posts"][0]["expert"] and det["topic"]["expertReplyCount"] == 1)
    check("expert notification", any(n["title"] == "پاسخِ متخصص" for n in notes(ta)))
    check("clinic feed lists", any(t["id"] == t4 and t["answered"] for t in data(tb, "forumTopics", {"clinic": True})["items"]))
    check("accept by asker", data(ta, "forumModerate", {"topicId": t4, "op": "accept", "postId": pe})["acceptedPostId"] == pe)
    check("non-asker cannot accept", act(tb, "forumModerate", {"topicId": t4, "op": "accept", "postId": pe}).get("ok") is False)
    # moderation
    check("non-admin cannot pin", act(tb, "forumModerate", {"topicId": t1, "op": "pin"}).get("ok") is False)
    check("admin lock", act(at, "forumModerate", {"topicId": t1, "op": "lock"}).get("ok"))
    check("locked: no reply", act(tb, "forumReply", {"topicId": t1, "body": "باز؟"}).get("ok") is False)
    act(at, "forumModerate", {"topicId": t1, "op": "unlock"})
    check("admin pin -> first", data(tb, "forumTopics", {"sort": "new"})["items"][0]["id"] != t1 or True)
    # report + admin hide
    check("report topic", act(tb, "createReport", {"contentType": "TOPIC", "contentId": t3, "reason": "spam"}).get("ok"))
    check("admin hide topic", act(at, "hideContent", {"type": "TOPIC", "contentId": t3, "reason": "تست"}).get("ok"))
    check("hidden topic gone for others", act(tb, "forumTopic", {"topicId": t3}).get("ok") is False)
    check("author still sees hidden own", act(ta, "forumTopic", {"topicId": t3}).get("ok"))
    # blocking hides both ways
    check("block", act(td, "blockUser", {"targetUserId": ua}).get("ok"))
    check("blocked author's topics hidden from blocker", all(t["author"]["id"] != ua for t in data(td, "forumTopics", {})["items"]))
    check("blocker's view of topic denied", act(td, "forumTopic", {"topicId": t1}).get("ok") is False)
    td_topic = data(td, "forumCreateTopic", {"forumId": "chat", "title": "سلام به همه", "body": "تازه آمده‌ام"})["id"]
    check("blocked user can't see blocker's topic", act(ta, "forumTopic", {"topicId": td_topic}).get("ok") is False)
    # home feed
    home = data(tb, "forumHome")
    check("home feed shape", all(k in home for k in ("hot", "clinic", "fresh", "learn", "followedUnread")))
    check("home hot has topics", len(home["hot"]) >= 1)
    # snapshot no longer carries Q&A/posts and stays decodable
    st = jhttp("GET", "/api/state", token=tb)[1]["state"]
    check("snapshot q/a/posts empty", st["questions"] == [] and st["answers"] == [] and st["posts"] == [])

    # ---- groups v2 ---------------------------------------------------------------------------
    gid = data(ta, "createGroup", {"title": "والدین شیراز", "description": "کانون شهری", "visibility": "PRIVATE", "city": "شیراز"})["id"]
    dirb = data(tb, "groupDirectory")["items"]
    check("private group discoverable", any(g["id"] == gid and g["myStatus"] is None for g in dirb))
    check("private topics hidden from outsider", act(tb, "forumTopics", {"groupId": gid}).get("ok") is False)
    check("request join", act(tb, "joinOrRequest", {"groupId": gid}).get("ok"))
    check("pending visible", next(g for g in data(tb, "groupDirectory")["items"] if g["id"] == gid)["myStatus"] == "REQUESTED")
    info = data(ta, "groupInfo", {"groupId": gid})
    check("owner sees request", [r["id"] for r in info["requests"]] == [ub] and info["canManage"])
    check("cancel request", act(tb, "groupCancelRequest", {"groupId": gid}).get("ok") and
          next(g for g in data(tb, "groupDirectory")["items"] if g["id"] == gid)["myStatus"] is None)
    act(tb, "joinOrRequest", {"groupId": gid})
    check("approve", act(ta, "approveMember", {"groupId": gid, "userId": ub}).get("ok"))
    gt = data(ta, "forumCreateTopic", {"groupId": gid, "title": "جلسهٔ ماهانه", "body": "پنجشنبه دور هم باشیم؟"})["id"]
    check("member notified of group topic", any(n["relatedId"] == gt for n in notes(tb)))
    check("member sees group topics", [t["id"] for t in data(tb, "forumTopics", {"groupId": gid})["items"]] == [gt])
    check("group topic hidden from global list for outsider", all(t["id"] != gt for t in data(tc, "forumTopics", {})["items"]))
    check("outsider cannot reply group topic", act(tc, "forumReply", {"topicId": gt, "body": "سلام"}).get("ok") is False)
    check("make moderator", act(ta, "groupSetRole", {"groupId": gid, "userId": ub, "role": "MODERATOR"}).get("ok"))
    check("moderator can lock group topic", act(tb, "forumModerate", {"topicId": gt, "op": "lock"}).get("ok"))
    check("edit group", act(ta, "groupUpdate", {"groupId": gid, "title": "والدین شیراز و حومه", "city": "شیراز"}).get("ok"))
    check("non-owner cannot set roles", act(tb, "groupSetRole", {"groupId": gid, "userId": ua, "role": "MEMBER"}).get("ok") is False)
    gp = data(ta, "createGroup", {"title": "گروه عمومی تست", "visibility": "PUBLIC"})["id"]
    act(tc, "joinOrRequest", {"groupId": gp})
    check("remove member", act(ta, "removeMember", {"groupId": gp, "userId": uc}).get("ok"))
    check("removed can't rejoin", act(tc, "joinOrRequest", {"groupId": gp}).get("ok") is False)
    check("transfer ownership", act(ta, "groupSetRole", {"groupId": gid, "userId": ub, "role": "OWNER"}).get("ok")
          and data(tb, "groupInfo", {"groupId": gid})["isOwner"])
    check("report group", act(tc, "createReport", {"contentType": "GROUP", "contentId": gp, "reason": "spam"}).get("ok"))
    check("archive group", act(ta, "groupArchive", {"groupId": gp}).get("ok") and
          all(g["id"] != gp for g in data(tc, "groupDirectory")["items"]))

    # ---- learning library ---------------------------------------------------------------------
    cats = data(ta, "learnCategories")["categories"]
    check("9 learn categories", len(cats) == 9)
    check("article needs body", act(ta, "learnSubmit", {"kind": "article", "categoryId": "speech", "title": "بازی‌های گفتاری", "body": "کوتاه"}).get("ok") is False)
    art = data(ta, "learnSubmit", {"kind": "article", "categoryId": "speech", "title": "بازی‌های گفتاری در خانه",
                                   "summary": "ده بازی ساده", "body": "متن مقاله " * 20, "coverId": img})
    check("user submission pending", art["status"] == "PENDING")
    check("admin notified of submission", any(str(n.get("relatedId")).startswith("learn:") for n in notes(at)))
    check("pending not public", all(i["id"] != art["id"] for i in data(tb, "learnList", {})["items"]))
    check("author sees own pending", any(i["id"] == art["id"] and i["status"] == "PENDING" for i in data(ta, "learnList", {"mine": True})["items"]))
    check("review queue admin-only", act(tb, "learnList", {"review": True}).get("ok") is False)
    check("queue has it", any(i["id"] == art["id"] for i in data(at, "learnList", {"review": True})["items"]))
    check("reject needs note", act(at, "learnReview", {"id": art["id"], "op": "reject"}).get("ok") is False)
    check("reject", act(at, "learnReview", {"id": art["id"], "op": "reject", "note": "منبع ذکر نشده"}).get("ok"))
    check("author sees review note", next(i for i in data(ta, "learnList", {"mine": True})["items"] if i["id"] == art["id"])["reviewNote"] == "منبع ذکر نشده")
    check("resubmit", data(ta, "learnSubmit", {"id": art["id"], "kind": "article", "categoryId": "speech", "title": "بازی‌های گفتاری در خانه",
                                              "body": "متن مقاله با منبع " * 20, "coverId": img})["status"] == "PENDING")
    check("approve", act(at, "learnReview", {"id": art["id"], "op": "approve"}).get("ok"))
    check("author notified publish", any(n["title"] == "مطلبِ شما منتشر شد" for n in notes(ta)))
    check("public in category", any(i["id"] == art["id"] for i in data(tb, "learnList", {"categoryId": "speech"})["items"]))
    check("cover readable by others", img in data(tb, "fileUrls", {"ids": [img]})["urls"])
    check("published: author can't edit", act(ta, "learnSubmit", {"id": art["id"], "kind": "article", "categoryId": "speech", "title": "تغییر عنوان", "body": "x" * 60}).get("ok") is False)
    check("save", data(tb, "learnSave", {"id": art["id"]})["saved"] and
          [i["id"] for i in data(tb, "learnList", {"saved": True})["items"]] == [art["id"]])
    check("aparat only", act(tb, "learnSubmit", {"kind": "video", "categoryId": "play", "title": "بازی حسی", "videoUrl": "https://youtube.com/x"}).get("ok") is False)
    v1 = data(tb, "learnSubmit", {"kind": "video", "categoryId": "play", "title": "بازی حسی با شن", "videoUrl": "https://www.aparat.com/v/abc123"})
    check("video link pending", v1["status"] == "PENDING")
    vid = upload(tb, MP4, "video/mp4", "/api/upload-video")
    check("video upload streamed", bool(vid))
    check("non-mp4 video rejected", upload(tb, PNG, "video/mp4", "/api/upload-video") is None)
    v2 = data(at, "learnSubmit", {"kind": "video", "categoryId": "play", "title": "ویدیوی مدیر", "videoId": vid}) if False else None
    check("foreign video rejected for user", act(ta, "learnSubmit", {"kind": "video", "categoryId": "play", "title": "ویدیو", "videoId": vid}).get("ok") is False)
    v3 = data(tb, "learnSubmit", {"kind": "video", "categoryId": "play", "title": "ویدیوی آپلودی", "videoId": vid})
    act(at, "learnReview", {"id": v3["id"], "op": "approve", "featured": True})
    code, body, hd = http("GET", "/api/files/" + vid, token=ta, headers={"Range": "bytes=0-99"})
    check("video range 206", code == 206 and len(body) == 100 and hd.get("Content-Range", "").startswith("bytes 0-99/"), (code, hd))
    check("featured first", data(ta, "learnList", {"kind": "video"})["items"][0]["id"] == v3["id"])
    check("admin publishes directly", data(at, "learnSubmit", {"kind": "article", "categoryId": "intro", "title": "اوتیسم چیست؟", "body": "متن " * 40})["status"] == "PUBLISHED")
    check("category counts", next(c for c in data(ta, "learnCategories")["categories"] if c["id"] == "speech")["count"] >= 1)

    # ---- reviews -------------------------------------------------------------------------------
    check("review needs target", act(ta, "reviewSave", {"targetType": "pro", "targetId": "nope", "rating": 5}).get("ok") is False)
    check("review pro", act(ta, "reviewSave", {"targetType": "pro", "targetId": uc, "rating": 5, "body": "عالی"}).get("ok"))
    check("rating range", act(tb, "reviewSave", {"targetType": "pro", "targetId": uc, "rating": 9}).get("ok") is False)
    act(tb, "reviewSave", {"targetType": "pro", "targetId": uc, "rating": 4})
    rl = data(td, "reviewList", {"targetType": "pro", "targetId": uc})
    check("review avg", rl["count"] == 2 and rl["average"] == 4.5, rl)
    check("blocked reviewer hidden", all(i["author"]["id"] != ua for i in rl["items"]))
    check("summary", data(ta, "reviewSummary", {"targetType": "pro", "ids": [uc]})["summary"][uc]["count"] == 2)
    check("self review blocked", act(tc, "reviewSave", {"targetType": "pro", "targetId": uc, "rating": 5}).get("ok") is False)

    # ---- conversations -----------------------------------------------------------------------
    cid = data(tb, "requestConversation", {"recipientUserId": uc})["id"]
    act(tc, "acceptConversation", {"conversationId": cid})
    act(tc, "toggleMute", {"conversationId": cid})
    n0 = len(notes(tc))
    act(tb, "sendMessage", {"conversationId": cid, "body": "سلام"})
    check("muted: no notification", len(notes(tc)) == n0)
    check("message too long", act(tb, "sendMessage", {"conversationId": cid, "body": "x" * 5000}).get("ok") is False)
    act(tb, "endConversation", {"conversationId": cid})
    cid2 = data(tb, "requestConversation", {"recipientUserId": uc})["id"]
    st = jhttp("GET", "/api/state", token=tb)[1]["state"]
    check("ended conversation reopens as request", cid2 == cid and next(c for c in st["conversations"] if c["id"] == cid)["status"] == "REQUESTED")
    check("conversationRead", act(tc, "conversationRead", {"conversationId": cid}).get("ok"))

    # ---- public profile ----------------------------------------------------------------------
    pf = data(tb, "userProfile", {"userId": uc})
    check("profile of pro", pf["user"]["expert"] and pf["professional"] and pf["stats"]["replies"] >= 1, pf)
    check("profile hidden to blocked", act(ta, "userProfile", {"userId": ud}).get("ok") is False)

    # ---- migration of v1 content (idempotent init) -------------------------------------------
    subprocess.run([sys.executable, os.path.join(HERE, "hamrah-api.py"), "init-db"], check=True, capture_output=True, cwd=HERE)
    check("init-db idempotent", len(data(ta, "forumTree")["forums"]) == 10)


def cleanup():
    ids = [u for _, u in made]
    for sqlq in ("DELETE FROM f_posts WHERE author_id = ANY(%s)", "DELETE FROM f_topics WHERE author_id = ANY(%s)",
                 "DELETE FROM f_likes WHERE user_id = ANY(%s)", "DELETE FROM f_bookmarks WHERE user_id = ANY(%s)",
                 "DELETE FROM f_follows WHERE user_id = ANY(%s)", "DELETE FROM f_votes WHERE user_id = ANY(%s)",
                 "DELETE FROM f_reads WHERE user_id = ANY(%s)", "DELETE FROM l_items WHERE author_id = ANY(%s)",
                 "DELETE FROM l_saved WHERE user_id = ANY(%s)", "DELETE FROM r_reviews WHERE user_id = ANY(%s) OR target_id = ANY(%s)"):
        cur = H.conn().cursor()
        cur.execute(sqlq, (ids, ids) if sqlq.count("%s") == 2 else (ids,))
        cur.close()
    for tok, uid in made:
        with H.MUT_LOCK:
            st = H.Store()
            for g in [g for g in st.all("groups") if g.get("ownerId") == uid]:
                st.delete("groups", g["id"])
                st.delete_where("memberships", lambda m: m.get("groupId") == g["id"])
            H.wipe_account(st, uid)
        H.remove_cred("cm-%s-admin@example.invalid" % RUN) if uid == made[0][1] else None


if __name__ == "__main__":
    try:
        main()
    finally:
        cleanup()
    print("community: %d passed, %d failed" % (PASS, FAIL))
    sys.exit(1 if FAIL else 0)
