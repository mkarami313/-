#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
همراهِ طیف — real backend (self-hosted on tizbin).

A single-file, stdlib-only HTTP API that replaces the app's Firebase backend. It stores
every collection as a row in a Postgres `docs(collection, id, data jsonb)` table and ports
the DEMO repository logic (the single source of truth for access rules — see the Kotlin
`fake` package and backend/firestore.rules) verbatim to the server side, where it is now
actually enforced rather than merely mirrored on the client.

Protocol (all JSON):
  POST /api/auth/register  {displayName,email,password,accountType,acceptedTerms} -> {token,user}
  POST /api/auth/login     {email,password}                                        -> {token,user}
  POST /api/auth/reset     {email}                                                 -> {ok:true}
  GET  /api/state          (Bearer)   -> a snapshot scoped to what the caller may read
  POST /api/action         (Bearer) {name, args:{...}}  -> {ok:true, data?} | {ok:false, error}

The client holds the /state snapshot in a local mirror and re-reads it after each action,
so the existing reactive UI keeps working unchanged.

CLI:
  python3 hamrah-api.py                      # run the server
  python3 hamrah-api.py init-db              # create tables + seed guides (idempotent)
  python3 hamrah-api.py create-admin EMAIL PASSWORD "نام"   # make/replace an admin account
"""

import os
import sys
import json
import time
import hmac
import base64
import hashlib
import secrets
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import psycopg2
import psycopg2.extras

# --------------------------------------------------------------------------------------
# Config
# --------------------------------------------------------------------------------------

HERE = os.path.dirname(os.path.abspath(__file__))


def _load_env():
    env = {}
    path = os.path.join(HERE, ".env")
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                env[k.strip()] = v.strip()
    return env


ENV = _load_env()
DB_DSN = ENV.get("DB_DSN", "dbname=rasta_hamrah user=rasta_office host=127.0.0.1 port=5432")
JWT_SECRET = ENV.get("JWT_SECRET", "change-me").encode("utf-8")
PORT = int(ENV.get("PORT", "8150"))
TOKEN_TTL = int(ENV.get("TOKEN_TTL_SECONDS", str(30 * 24 * 3600)))  # 30 days

NOT_SIGNED_IN = "ابتدا وارد حساب خود شوید."
DELETED_USER_ID = "u_deleted"

# Account types that may hold a professional profile / request verification.
PRO_TYPES = {"PROFESSIONAL", "EDUCATOR", "SOCIAL_WORKER"}

COLLECTIONS = [
    "users", "professionals", "verifications", "children", "questions", "answers",
    "groups", "memberships", "posts", "conversations", "messages", "guides",
    "reports", "notifications", "adminActions",
]


def now_ms():
    return int(time.time() * 1000)


def new_id(prefix):
    return "%s_%s" % (prefix, secrets.token_hex(8))


# --------------------------------------------------------------------------------------
# DB helpers
# --------------------------------------------------------------------------------------

_local = threading.local()


def conn():
    c = getattr(_local, "conn", None)
    if c is None or c.closed:
        c = psycopg2.connect(DB_DSN)
        c.autocommit = True
        _local.conn = c
    return c


class Store:
    """A thin per-request view over the docs table. Loads collections lazily and caches
    them so a single action reads each collection once; writes go straight to Postgres."""

    def __init__(self):
        self._cache = {}

    def all(self, coll):
        if coll not in self._cache:
            cur = conn().cursor()
            cur.execute("SELECT data FROM docs WHERE collection=%s", (coll,))
            self._cache[coll] = [row[0] for row in cur.fetchall()]
            cur.close()
        return self._cache[coll]

    def get(self, coll, _id):
        for d in self.all(coll):
            if d.get("id") == _id:
                return d
        return None

    def put(self, coll, doc):
        cur = conn().cursor()
        cur.execute(
            "INSERT INTO docs(collection,id,data) VALUES(%s,%s,%s) "
            "ON CONFLICT(collection,id) DO UPDATE SET data=EXCLUDED.data",
            (coll, doc["id"], json.dumps(doc)),
        )
        cur.close()
        lst = self._cache.get(coll)
        if lst is not None:
            for i, d in enumerate(lst):
                if d.get("id") == doc["id"]:
                    lst[i] = doc
                    break
            else:
                lst.append(doc)

    def delete(self, coll, _id):
        cur = conn().cursor()
        cur.execute("DELETE FROM docs WHERE collection=%s AND id=%s", (coll, _id))
        cur.close()
        lst = self._cache.get(coll)
        if lst is not None:
            self._cache[coll] = [d for d in lst if d.get("id") != _id]

    def delete_where(self, coll, pred):
        for d in list(self.all(coll)):
            if pred(d):
                self.delete(coll, d["id"])


def set_cred(email, password, uid):
    salt = secrets.token_hex(16)
    h = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt.encode("utf-8"), 120000).hex()
    cur = conn().cursor()
    cur.execute(
        "INSERT INTO creds(email,salt,pwhash,uid) VALUES(%s,%s,%s,%s) "
        "ON CONFLICT(email) DO UPDATE SET salt=EXCLUDED.salt,pwhash=EXCLUDED.pwhash,uid=EXCLUDED.uid",
        (email.strip().lower(), salt, h, uid),
    )
    cur.close()


def check_cred(email, password):
    cur = conn().cursor()
    cur.execute("SELECT salt,pwhash,uid FROM creds WHERE email=%s", (email.strip().lower(),))
    row = cur.fetchone()
    cur.close()
    if not row:
        return None
    salt, pwhash, uid = row
    h = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt.encode("utf-8"), 120000).hex()
    return uid if hmac.compare_digest(h, pwhash) else None


def has_email(email):
    cur = conn().cursor()
    cur.execute("SELECT 1 FROM creds WHERE email=%s", (email.strip().lower(),))
    row = cur.fetchone()
    cur.close()
    return row is not None


def remove_cred(email):
    cur = conn().cursor()
    cur.execute("DELETE FROM creds WHERE email=%s", (email.strip().lower(),))
    cur.close()


# --------------------------------------------------------------------------------------
# JWT (HS256, manual)
# --------------------------------------------------------------------------------------

def _b64(b):
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode("ascii")


def _b64d(s):
    return base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))


def make_token(uid):
    header = _b64(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
    payload = _b64(json.dumps({"uid": uid, "exp": int(time.time()) + TOKEN_TTL}).encode())
    signing = ("%s.%s" % (header, payload)).encode()
    sig = _b64(hmac.new(JWT_SECRET, signing, hashlib.sha256).digest())
    return "%s.%s.%s" % (header, payload, sig)


def verify_token(token):
    try:
        header, payload, sig = token.split(".")
        signing = ("%s.%s" % (header, payload)).encode()
        expected = _b64(hmac.new(JWT_SECRET, signing, hashlib.sha256).digest())
        if not hmac.compare_digest(sig, expected):
            return None
        data = json.loads(_b64d(payload))
        if data.get("exp", 0) < int(time.time()):
            return None
        return data.get("uid")
    except Exception:
        return None


# --------------------------------------------------------------------------------------
# Domain helpers (ported from the Kotlin fake layer)
# --------------------------------------------------------------------------------------

class ApiError(Exception):
    pass


def is_admin(store, uid):
    u = store.get("users", uid) if uid else None
    return bool(u and u.get("isAdmin"))


NOTIF_TOGGLE = {
    "QUESTION_ANSWERED": "questionAnswered",
    "NEW_MESSAGE": "newMessage",
    "GROUP_JOIN_REQUEST": "groupJoinRequest",
    "GROUP_REPLY": "groupReply",
    "VERIFICATION_RESULT": "verificationResult",
    "SAFETY_NOTICE": "safetyNotice",
}


def add_notification(store, user_id, ntype, title, body, related_id=None):
    recipient = store.get("users", user_id)
    if not recipient:
        return
    settings = recipient.get("notificationSettings") or {}
    key = NOTIF_TOGGLE.get(ntype)
    if key is not None and settings.get(key, True) is False:
        return
    store.put("notifications", {
        "id": new_id("n"), "userId": user_id, "type": ntype, "title": title,
        "body": body, "relatedId": related_id, "read": False, "createdAt": now_ms(),
    })


def is_active_member(store, group_id, uid):
    return uid is not None and any(
        m.get("groupId") == group_id and m.get("userId") == uid and m.get("status") == "ACTIVE"
        for m in store.all("memberships")
    )


def can_manage(store, group_id, uid):
    return uid is not None and any(
        m.get("groupId") == group_id and m.get("userId") == uid and m.get("status") == "ACTIVE"
        and m.get("role") in ("OWNER", "MODERATOR")
        for m in store.all("memberships")
    )


def _s(v):
    return (v or "").strip()


# --------------------------------------------------------------------------------------
# Actions (name -> handler(store, uid, args) -> optional data). Raise ApiError(msg) to fail.
# --------------------------------------------------------------------------------------

def _require(uid):
    if not uid:
        raise ApiError(NOT_SIGNED_IN)
    return uid


# ---- User / professional / children / verification ----

def a_updateProfile(store, uid, args):
    _require(uid)
    if not _s(args.get("displayName")):
        raise ApiError("نام نمایشی را وارد کنید.")
    u = store.get("users", uid)
    if not u:
        raise ApiError("حساب یافت نشد.")
    u["displayName"] = _s(args.get("displayName"))
    u["city"] = _s(args.get("city")) or None
    u["bio"] = _s(args.get("bio")) or None
    u["photoUrl"] = args.get("photoUrl")
    u["updatedAt"] = now_ms()
    store.put("users", u)


def a_updatePrivacy(store, uid, args):
    _require(uid)
    u = store.get("users", uid)
    u["privacy"] = args.get("settings") or {}
    u["updatedAt"] = now_ms()
    store.put("users", u)


def a_updateNotificationSettings(store, uid, args):
    _require(uid)
    u = store.get("users", uid)
    u["notificationSettings"] = args.get("settings") or {}
    u["updatedAt"] = now_ms()
    store.put("users", u)


def a_blockUser(store, uid, args):
    _require(uid)
    target = args.get("targetUserId")
    if uid == target:
        raise ApiError("نمی‌توانید خودتان را مسدود کنید.")
    u = store.get("users", uid)
    blocked = set(u.get("blockedUserIds") or [])
    blocked.add(target)
    u["blockedUserIds"] = sorted(blocked)
    store.put("users", u)


def a_unblockUser(store, uid, args):
    _require(uid)
    u = store.get("users", uid)
    blocked = set(u.get("blockedUserIds") or [])
    blocked.discard(args.get("targetUserId"))
    u["blockedUserIds"] = sorted(blocked)
    store.put("users", u)


def a_upsertProfessionalProfile(store, uid, args):
    _require(uid)
    u = store.get("users", uid)
    if not u:
        raise ApiError("حساب یافت نشد.")
    if u.get("accountType") not in PRO_TYPES:
        raise ApiError("این نوع حساب پروفایل تخصصی ندارد.")
    if not _s(args.get("title")):
        raise ApiError("عنوان تخصص را وارد کنید.")
    existing = next((p for p in store.all("professionals") if p.get("userId") == uid), None)
    if existing:
        existing.update({
            "title": _s(args.get("title")),
            "workAreas": args.get("workAreas") or [],
            "city": _s(args.get("city")) or None,
            "offersOnline": bool(args.get("offersOnline")),
            "experience": _s(args.get("experience")) or None,
            "updatedAt": now_ms(),
        })
        store.put("professionals", existing)
    else:
        store.put("professionals", {
            "id": new_id("p"), "userId": uid, "title": _s(args.get("title")),
            "workAreas": args.get("workAreas") or [], "city": _s(args.get("city")) or None,
            "offersOnline": bool(args.get("offersOnline")),
            "experience": _s(args.get("experience")) or None,
            "verificationStatus": "NONE", "createdAt": now_ms(), "updatedAt": now_ms(),
        })


def a_submitVerificationRequest(store, uid, args):
    _require(uid)
    u = store.get("users", uid)
    if not u:
        raise ApiError("حساب یافت نشد.")
    if u.get("accountType") not in PRO_TYPES:
        raise ApiError("این نوع حساب امکان درخواست تأیید تخصص ندارد.")
    title = _s(args.get("professionalTitle"))
    if not title:
        raise ApiError("عنوان تخصص را وارد کنید.")
    # Replace any existing PENDING request from this user.
    store.delete_where("verifications",
                       lambda v: v.get("userId") == uid and v.get("status") == "PENDING")
    store.put("verifications", {
        "id": new_id("v"), "userId": uid, "professionalTitle": title,
        "licenseNumber": _s(args.get("licenseNumber")) or None,
        "credentialNote": _s(args.get("credentialNote")) or None,
        "documentRefs": [], "status": "PENDING", "createdAt": now_ms(), "updatedAt": now_ms(),
    })
    existing = next((p for p in store.all("professionals") if p.get("userId") == uid), None)
    if existing:
        existing["verificationStatus"] = "PENDING"
        existing["updatedAt"] = now_ms()
        store.put("professionals", existing)
    else:
        store.put("professionals", {
            "id": new_id("p"), "userId": uid, "title": title, "workAreas": [], "city": None,
            "offersOnline": False, "experience": None, "verificationStatus": "PENDING",
            "createdAt": now_ms(), "updatedAt": now_ms(),
        })


def a_addChild(store, uid, args):
    _require(uid)
    store.put("children", {
        "id": new_id("c"), "familyUserId": uid, "nickname": _s(args.get("nickname")) or None,
        "ageRange": args.get("ageRange") or "UNSPECIFIED", "interests": args.get("interests") or [],
        "isPublic": False, "createdAt": now_ms(), "updatedAt": now_ms(),
    })


def a_deleteChild(store, uid, args):
    _require(uid)
    store.delete_where("children",
                       lambda c: c.get("id") == args.get("childId") and c.get("familyUserId") == uid)


def a_exportMyData(store, uid, args):
    _require(uid)
    u = store.get("users", uid)
    if not u:
        raise ApiError("حساب یافت نشد.")
    lines = [
        "# نسخهٔ اطلاعات حساب شما",
        "نام نمایشی: %s" % u.get("displayName", ""),
        "نوع حساب: %s" % u.get("accountType", ""),
        "ایمیل: %s" % (u.get("email") or "-"),
        "شهر: %s" % (u.get("city") or "-"),
        "تعداد پرسش‌ها: %d" % sum(1 for q in store.all("questions") if q.get("authorId") == uid),
        "تعداد پاسخ‌ها: %d" % sum(1 for a in store.all("answers") if a.get("authorId") == uid),
        "تعداد پروفایل‌های کودک: %d" % sum(1 for c in store.all("children") if c.get("familyUserId") == uid),
        "عضویت در گروه‌ها: %d" % sum(1 for m in store.all("memberships")
                                      if m.get("userId") == uid and m.get("status") == "ACTIVE"),
    ]
    return {"text": "\n".join(lines)}


def a_deleteAccount(store, uid, args):
    _require(uid)
    u = store.get("users", uid)
    if not u:
        raise ApiError("حساب یافت نشد.")
    if u.get("email"):
        remove_cred(u["email"])
    store.delete("users", uid)
    store.delete_where("professionals", lambda p: p.get("userId") == uid)
    store.delete_where("verifications", lambda v: v.get("userId") == uid)
    store.delete_where("children", lambda c: c.get("familyUserId") == uid)
    store.delete_where("notifications", lambda n: n.get("userId") == uid)
    store.delete_where("memberships", lambda m: m.get("userId") == uid)
    my_convs = [c["id"] for c in store.all("conversations") if uid in (c.get("participantIds") or [])]
    store.delete_where("messages", lambda msg: msg.get("conversationId") in my_convs)
    store.delete_where("conversations", lambda c: uid in (c.get("participantIds") or []))
    # Anonymize public contributions so threads stay readable.
    for q in store.all("questions"):
        if q.get("authorId") == uid:
            q["authorId"] = DELETED_USER_ID
            q["isAnonymous"] = True
            store.put("questions", q)
    for a in store.all("answers"):
        if a.get("authorId") == uid:
            a["authorId"] = DELETED_USER_ID
            store.put("answers", a)
    for p in store.all("posts"):
        if p.get("authorId") == uid:
            p["authorId"] = DELETED_USER_ID
            store.put("posts", p)


# ---- Questions & answers ----

def a_createQuestion(store, uid, args):
    _require(uid)
    if len(_s(args.get("title"))) < 3:
        raise ApiError("عنوان پرسش را کامل‌تر بنویسید.")
    if len(_s(args.get("body"))) < 5:
        raise ApiError("متن پرسش را کامل‌تر بنویسید.")
    qid = new_id("q")
    store.put("questions", {
        "id": qid, "authorId": uid, "isAnonymous": bool(args.get("anonymous")),
        "category": args.get("category"), "title": _s(args.get("title")), "body": _s(args.get("body")),
        "status": "PUBLISHED", "commentsClosed": False, "answerCount": 0, "helpfulCount": 0,
        "createdAt": now_ms(), "updatedAt": now_ms(),
    })
    return {"id": qid}


def a_editQuestion(store, uid, args):
    _require(uid)
    q = store.get("questions", args.get("questionId"))
    if not q:
        raise ApiError("پرسش یافت نشد.")
    if q.get("authorId") != uid:
        raise ApiError("فقط نویسنده می‌تواند پرسش را ویرایش کند.")
    q["title"] = _s(args.get("title"))
    q["body"] = _s(args.get("body"))
    q["updatedAt"] = now_ms()
    store.put("questions", q)


def a_deleteQuestion(store, uid, args):
    _require(uid)
    q = store.get("questions", args.get("questionId"))
    if not q:
        raise ApiError("پرسش یافت نشد.")
    if q.get("authorId") != uid:
        raise ApiError("فقط نویسنده می‌تواند پرسش را حذف کند.")
    store.delete("questions", q["id"])
    store.delete_where("answers", lambda a: a.get("questionId") == q["id"])


def a_setCommentsClosed(store, uid, args):
    _require(uid)
    q = store.get("questions", args.get("questionId"))
    if not q:
        raise ApiError("پرسش یافت نشد.")
    if q.get("authorId") != uid and not is_admin(store, uid):
        raise ApiError("اجازهٔ این کار را ندارید.")
    q["commentsClosed"] = bool(args.get("closed"))
    q["updatedAt"] = now_ms()
    store.put("questions", q)


def a_createAnswer(store, uid, args):
    _require(uid)
    q = store.get("questions", args.get("questionId"))
    if not q:
        raise ApiError("پرسش یافت نشد.")
    if q.get("commentsClosed"):
        raise ApiError("امکان پاسخ به این پرسش بسته شده است.")
    if len(_s(args.get("body"))) < 2:
        raise ApiError("متن پاسخ را بنویسید.")
    store.put("answers", {
        "id": new_id("a"), "questionId": q["id"], "authorId": uid, "body": _s(args.get("body")),
        "status": "PUBLISHED", "helpfulByUserIds": [], "createdAt": now_ms(), "updatedAt": now_ms(),
    })
    q["answerCount"] = int(q.get("answerCount", 0)) + 1
    q["updatedAt"] = now_ms()
    store.put("questions", q)
    if q.get("authorId") not in (uid, DELETED_USER_ID):
        add_notification(store, q["authorId"], "QUESTION_ANSWERED", "پاسخ تازه",
                         "به یکی از پرسش‌های شما پاسخ داده شد.", related_id=q["id"])


def a_toggleHelpful(store, uid, args):
    _require(uid)
    ans = store.get("answers", args.get("answerId"))
    if not ans:
        raise ApiError("پاسخ یافت نشد.")
    helpful = set(ans.get("helpfulByUserIds") or [])
    if uid in helpful:
        helpful.discard(uid)
    else:
        helpful.add(uid)
    ans["helpfulByUserIds"] = sorted(helpful)
    store.put("answers", ans)
    total = sum(len(a.get("helpfulByUserIds") or [])
                for a in store.all("answers") if a.get("questionId") == ans.get("questionId"))
    q = store.get("questions", ans.get("questionId"))
    if q:
        q["helpfulCount"] = total
        store.put("questions", q)


# ---- Groups ----

def a_createGroup(store, uid, args):
    _require(uid)
    if len(_s(args.get("title"))) < 3:
        raise ApiError("عنوان گروه را کامل‌تر بنویسید.")
    gid = new_id("g")
    store.put("groups", {
        "id": gid, "ownerId": uid, "title": _s(args.get("title")),
        "description": _s(args.get("description")), "category": _s(args.get("category")) or None,
        "city": _s(args.get("city")) or None, "visibility": args.get("visibility") or "PUBLIC",
        "rules": _s(args.get("rules")) or None, "memberCount": 1,
        "createdAt": now_ms(), "updatedAt": now_ms(),
    })
    store.put("memberships", {
        "id": new_id("m"), "groupId": gid, "userId": uid, "role": "OWNER", "status": "ACTIVE",
        "createdAt": now_ms(), "updatedAt": now_ms(),
    })
    return {"id": gid}


def a_joinOrRequest(store, uid, args):
    _require(uid)
    gid = args.get("groupId")
    g = store.get("groups", gid)
    if not g:
        raise ApiError("گروه یافت نشد.")
    existing = next((m for m in store.all("memberships")
                     if m.get("groupId") == gid and m.get("userId") == uid), None)
    if existing and existing.get("status") != "REMOVED":
        raise ApiError("شما قبلاً در این گروه عضو یا در انتظار تأیید هستید.")
    new_status = "ACTIVE" if g.get("visibility") == "PUBLIC" else "REQUESTED"
    store.delete_where("memberships", lambda m: m.get("groupId") == gid and m.get("userId") == uid)
    store.put("memberships", {
        "id": new_id("m"), "groupId": gid, "userId": uid, "role": "MEMBER", "status": new_status,
        "createdAt": now_ms(), "updatedAt": now_ms(),
    })
    if new_status == "ACTIVE":
        g["memberCount"] = int(g.get("memberCount", 0)) + 1
        g["updatedAt"] = now_ms()
        store.put("groups", g)
    else:
        add_notification(store, g["ownerId"], "GROUP_JOIN_REQUEST", "درخواست عضویت",
                         "یک درخواست عضویت تازه در گروه شما ثبت شد.", related_id=gid)


def a_leave(store, uid, args):
    _require(uid)
    gid = args.get("groupId")
    membership = next((m for m in store.all("memberships")
                       if m.get("groupId") == gid and m.get("userId") == uid
                       and m.get("status") != "REMOVED"), None)
    if not membership:
        raise ApiError("شما عضو این گروه نیستید.")
    if membership.get("role") == "OWNER":
        raise ApiError("مدیر گروه نمی‌تواند گروه را ترک کند؛ ابتدا مدیریت را واگذار کنید.")
    was_active = membership.get("status") == "ACTIVE"
    store.delete("memberships", membership["id"])
    if was_active:
        g = store.get("groups", gid)
        if g:
            g["memberCount"] = max(0, int(g.get("memberCount", 0)) - 1)
            g["updatedAt"] = now_ms()
            store.put("groups", g)


def a_approveMember(store, uid, args):
    _require(uid)
    gid = args.get("groupId")
    if not can_manage(store, gid, uid):
        raise ApiError("اجازهٔ مدیریت این گروه را ندارید.")
    changed = False
    for m in store.all("memberships"):
        if (m.get("groupId") == gid and m.get("userId") == args.get("userId")
                and m.get("status") == "REQUESTED"):
            m["status"] = "ACTIVE"
            m["updatedAt"] = now_ms()
            store.put("memberships", m)
            changed = True
    if changed:
        g = store.get("groups", gid)
        if g:
            g["memberCount"] = int(g.get("memberCount", 0)) + 1
            g["updatedAt"] = now_ms()
            store.put("groups", g)


def a_removeMember(store, uid, args):
    _require(uid)
    gid = args.get("groupId")
    if not can_manage(store, gid, uid):
        raise ApiError("اجازهٔ مدیریت این گروه را ندارید.")
    target = next((m for m in store.all("memberships")
                   if m.get("groupId") == gid and m.get("userId") == args.get("userId")), None)
    if target and target.get("role") == "OWNER":
        raise ApiError("نمی‌توان مدیر گروه را حذف کرد.")
    was_active = target and target.get("status") == "ACTIVE"
    store.delete_where("memberships",
                       lambda m: m.get("groupId") == gid and m.get("userId") == args.get("userId"))
    if was_active:
        g = store.get("groups", gid)
        if g:
            g["memberCount"] = max(0, int(g.get("memberCount", 0)) - 1)
            g["updatedAt"] = now_ms()
            store.put("groups", g)


def a_createPost(store, uid, args):
    _require(uid)
    gid = args.get("groupId")
    if not is_active_member(store, gid, uid):
        raise ApiError("برای ارسال پست باید عضو فعال گروه باشید.")
    if len(_s(args.get("body"))) < 2:
        raise ApiError("متن پست را بنویسید.")
    store.put("posts", {
        "id": new_id("gp"), "groupId": gid, "authorId": uid, "body": _s(args.get("body")),
        "status": "PUBLISHED", "createdAt": now_ms(), "updatedAt": now_ms(),
    })
    g = store.get("groups", gid)
    if g:
        g["updatedAt"] = now_ms()
        store.put("groups", g)


def a_deletePost(store, uid, args):
    _require(uid)
    post = store.get("posts", args.get("postId"))
    if not post:
        raise ApiError("پست یافت نشد.")
    if post.get("authorId") != uid and not can_manage(store, post.get("groupId"), uid):
        raise ApiError("اجازهٔ حذف این پست را ندارید.")
    store.delete("posts", post["id"])


# ---- Conversations ----

def a_requestConversation(store, uid, args):
    _require(uid)
    recipient_id = args.get("recipientUserId")
    if uid == recipient_id:
        raise ApiError("نمی‌توانید با خودتان گفتگو شروع کنید.")
    recipient = store.get("users", recipient_id)
    if not recipient:
        raise ApiError("کاربر یافت نشد.")
    if (recipient.get("privacy") or {}).get("allowMessageRequests", True) is False:
        raise ApiError("این کاربر درخواست گفتگو نمی‌پذیرد.")
    me = store.get("users", uid)
    if recipient_id in set((me or {}).get("blockedUserIds") or []):
        raise ApiError("این کاربر در فهرست مسدودشده‌های شماست.")
    if uid in set(recipient.get("blockedUserIds") or []):
        raise ApiError("امکان ارسال درخواست به این کاربر وجود ندارد.")
    existing = next((c for c in store.all("conversations")
                     if set(c.get("participantIds") or []) == {uid, recipient_id}
                     and c.get("status") != "DECLINED"), None)
    if existing:
        return {"id": existing["id"]}
    cid = new_id("conv")
    store.put("conversations", {
        "id": cid, "participantIds": [uid, recipient_id], "initiatorId": uid,
        "status": "REQUESTED", "mutedByUserIds": [], "endedByUserId": None,
        "lastMessagePreview": None, "createdAt": now_ms(), "updatedAt": now_ms(),
    })
    add_notification(store, recipient_id, "NEW_MESSAGE", "درخواست گفتگو",
                     "یک درخواست گفتگوی تازه دارید.", related_id=cid)
    return {"id": cid}


def _conv_guard(store, uid, cid):
    conv = store.get("conversations", cid)
    if not conv:
        raise ApiError("گفتگو یافت نشد.")
    if uid not in (conv.get("participantIds") or []):
        raise ApiError("اجازهٔ دسترسی ندارید.")
    return conv


def a_acceptConversation(store, uid, args):
    _require(uid)
    conv = _conv_guard(store, uid, args.get("conversationId"))
    if conv.get("initiatorId") == uid:
        raise ApiError("پذیرش گفتگو با طرف مقابل است.")
    conv["status"] = "ACCEPTED"
    conv["updatedAt"] = now_ms()
    store.put("conversations", conv)


def a_declineConversation(store, uid, args):
    _require(uid)
    conv = _conv_guard(store, uid, args.get("conversationId"))
    conv["status"] = "DECLINED"
    conv["updatedAt"] = now_ms()
    store.put("conversations", conv)


def a_endConversation(store, uid, args):
    _require(uid)
    conv = _conv_guard(store, uid, args.get("conversationId"))
    conv["status"] = "ENDED"
    conv["endedByUserId"] = uid
    conv["updatedAt"] = now_ms()
    store.put("conversations", conv)


def a_toggleMute(store, uid, args):
    _require(uid)
    conv = _conv_guard(store, uid, args.get("conversationId"))
    muted = set(conv.get("mutedByUserIds") or [])
    if uid in muted:
        muted.discard(uid)
    else:
        muted.add(uid)
    conv["mutedByUserIds"] = sorted(muted)
    store.put("conversations", conv)


def a_sendMessage(store, uid, args):
    _require(uid)
    conv = _conv_guard(store, uid, args.get("conversationId"))
    if conv.get("status") != "ACCEPTED":
        raise ApiError("گفتگو فعال نیست.")
    body = _s(args.get("body"))
    if not body:
        raise ApiError("پیام خالی است.")
    other = next(p for p in conv["participantIds"] if p != uid)
    store.put("messages", {
        "id": new_id("msg"), "conversationId": conv["id"], "senderId": uid, "body": body,
        "createdAt": now_ms(),
    })
    conv["lastMessagePreview"] = body[:40]
    conv["updatedAt"] = now_ms()
    store.put("conversations", conv)
    add_notification(store, other, "NEW_MESSAGE", "پیام جدید", "پیام جدید دارید.",
                     related_id=conv["id"])


# ---- Guides ----

def a_toggleSaved(store, uid, args):
    _require(uid)
    guide = store.get("guides", args.get("guideId"))
    if not guide:
        raise ApiError("راهنما یافت نشد.")
    saved = set(guide.get("savedByUserIds") or [])
    if uid in saved:
        saved.discard(uid)
    else:
        saved.add(uid)
    guide["savedByUserIds"] = sorted(saved)
    store.put("guides", guide)


def a_upsertGuide(store, uid, args):
    _require(uid)
    if not is_admin(store, uid):
        raise ApiError("فقط مدیر می‌تواند راهنما را ویرایش کند.")
    g = dict(args.get("guide") or {})
    gid = g.get("id") or new_id("guide")
    g["id"] = gid
    g.setdefault("status", "PUBLISHED")
    g.setdefault("savedByUserIds", [])
    g["updatedAt"] = now_ms()
    if not store.get("guides", gid):
        g.setdefault("createdAt", now_ms())
    store.put("guides", g)


# ---- Reports ----

def a_createReport(store, uid, args):
    _require(uid)
    store.put("reports", {
        "id": new_id("r"), "reporterId": uid, "contentType": args.get("contentType"),
        "contentId": args.get("contentId"), "reason": args.get("reason"),
        "note": _s(args.get("note")) or None, "status": "OPEN",
        "createdAt": now_ms(), "updatedAt": now_ms(),
    })


def a_resolveReport(store, uid, args):
    _require(uid)
    if not is_admin(store, uid):
        raise ApiError("فقط مدیر می‌تواند گزارش را رسیدگی کند.")
    rep = store.get("reports", args.get("reportId"))
    if not rep:
        raise ApiError("گزارش یافت نشد.")
    note = _s(args.get("resolutionNote"))
    rep["status"] = args.get("newStatus")
    rep["resolutionNote"] = note
    rep["reviewedByAdminId"] = uid
    rep["updatedAt"] = now_ms()
    store.put("reports", rep)
    _log_admin(store, uid, "RESOLVE_REPORT", "USER", rep["id"], note)


# ---- Notifications ----

def a_markRead(store, uid, args):
    _require(uid)
    n = store.get("notifications", args.get("notificationId"))
    if n and n.get("userId") == uid:
        n["read"] = True
        store.put("notifications", n)


def a_markAllRead(store, uid, args):
    _require(uid)
    for n in store.all("notifications"):
        if n.get("userId") == uid and not n.get("read"):
            n["read"] = True
            store.put("notifications", n)


# ---- Admin (audited) ----

def _log_admin(store, admin_id, action_type, target_type, target_id, reason):
    store.put("adminActions", {
        "id": new_id("aa"), "adminId": admin_id, "actionType": action_type,
        "targetType": target_type, "targetId": target_id, "reason": reason, "createdAt": now_ms(),
    })


def a_viewCredential(store, uid, args):
    _require(uid)
    if not is_admin(store, uid):
        raise ApiError("دسترسی مدیر لازم است.")
    req = store.get("verifications", args.get("requestId"))
    if not req:
        raise ApiError("درخواست یافت نشد.")
    _log_admin(store, uid, "VIEW_CREDENTIAL", "USER", req["userId"], "مشاهدهٔ مدرک برای بررسی")
    return {"request": req}


def a_reviewVerification(store, uid, args):
    _require(uid)
    if not is_admin(store, uid):
        raise ApiError("دسترسی مدیر لازم است.")
    reason = _s(args.get("reason"))
    if not reason:
        raise ApiError("ثبت دلیل تصمیم الزامی است.")
    req = store.get("verifications", args.get("requestId"))
    if not req:
        raise ApiError("درخواست یافت نشد.")
    decision = args.get("decision")
    action_type = {"VERIFIED": "VERIFY_PROFESSIONAL", "REJECTED": "REJECT_PROFESSIONAL"}.get(
        decision, "REQUEST_MORE_INFO")
    req["status"] = decision
    req["reviewerNote"] = reason
    req["reviewedByAdminId"] = uid
    req["updatedAt"] = now_ms()
    store.put("verifications", req)
    for p in store.all("professionals"):
        if p.get("userId") == req["userId"]:
            p["verificationStatus"] = decision
            p["updatedAt"] = now_ms()
            store.put("professionals", p)
    _log_admin(store, uid, action_type, "USER", req["userId"], reason)
    label = {"VERIFIED": "تأییدشده", "REJECTED": "ردشده", "PENDING": "در انتظار بررسی",
             "MORE_INFO_NEEDED": "نیازمند اطلاعات بیشتر", "NONE": "بدون درخواست"}.get(decision, decision)
    add_notification(store, req["userId"], "VERIFICATION_RESULT", "نتیجهٔ بررسی تخصص",
                     "وضعیت بررسی تخصص شما به «%s» تغییر کرد." % label)


def _set_content_status(store, uid, ctype, cid, status, action, reason):
    if not is_admin(store, uid):
        raise ApiError("دسترسی مدیر لازم است.")
    if not _s(reason):
        raise ApiError("ثبت دلیل تصمیم الزامی است.")
    coll = {"QUESTION": "questions", "ANSWER": "answers", "GROUP_POST": "posts",
            "GUIDE": "guides"}.get(ctype)
    if coll:
        doc = store.get(coll, cid)
        if doc:
            doc["status"] = status
            store.put(coll, doc)
    _log_admin(store, uid, action, ctype, cid, _s(reason))


def a_hideContent(store, uid, args):
    _require(uid)
    _set_content_status(store, uid, args.get("type"), args.get("contentId"),
                        "HIDDEN", "HIDE_CONTENT", args.get("reason"))


def a_deleteContent(store, uid, args):
    _require(uid)
    _set_content_status(store, uid, args.get("type"), args.get("contentId"),
                        "REMOVED", "DELETE_CONTENT", args.get("reason"))


def a_setAccountStatus(store, uid, args):
    _require(uid)
    if not is_admin(store, uid):
        raise ApiError("دسترسی مدیر لازم است.")
    reason = _s(args.get("reason"))
    if not reason:
        raise ApiError("ثبت دلیل تصمیم الزامی است.")
    status = args.get("status")
    target = store.get("users", args.get("userId"))
    if target:
        target["status"] = status
        target["updatedAt"] = now_ms()
        store.put("users", target)
    action = "BLOCK_ACCOUNT" if status == "BLOCKED" else "LIMIT_ACCOUNT"
    _log_admin(store, uid, action, "USER", args.get("userId"), reason)


ACTIONS = {name[2:]: fn for name, fn in list(globals().items())
           if name.startswith("a_") and callable(fn)}


# --------------------------------------------------------------------------------------
# State snapshot (scoped to what the caller may read — mirrors firestore.rules read rules)
# --------------------------------------------------------------------------------------

def build_state(store, uid):
    admin = is_admin(store, uid)

    def public_user(u):
        if u.get("id") == uid:
            return u
        # Strip fields that are private to the owner (and admins, via the admin panel).
        pu = dict(u)
        pu.pop("email", None)
        pu.pop("blockedUserIds", None)
        pu.pop("notificationSettings", None)
        return pu

    users = [public_user(u) for u in store.all("users")]
    professionals = list(store.all("professionals"))

    # Questions/answers/posts/guides: PUBLISHED for everyone; author sees own; admin sees all.
    def visible_content(doc, author_field="authorId"):
        return (doc.get("status") == "PUBLISHED" or doc.get(author_field) == uid or admin)

    questions = [q for q in store.all("questions")
                 if q.get("status") != "REMOVED" and visible_content(q)]
    q_ids = {q["id"] for q in questions}
    answers = [a for a in store.all("answers")
               if a.get("status") != "REMOVED" and visible_content(a)]
    guides = [g for g in store.all("guides")
              if g.get("status") == "PUBLISHED" or admin]

    # Groups: public + ones I'm a member of (any status besides REMOVED).
    my_group_ids = {m["groupId"] for m in store.all("memberships")
                    if m.get("userId") == uid and m.get("status") != "REMOVED"}
    groups = [g for g in store.all("groups")
              if g.get("visibility") == "PUBLIC" or g["id"] in my_group_ids or admin]
    visible_group_ids = {g["id"] for g in groups}

    # Memberships: of groups I can see (so member lists / my requests resolve).
    memberships = [m for m in store.all("memberships")
                   if m.get("groupId") in visible_group_ids and m.get("status") != "REMOVED"]

    # Posts: of visible groups where I'm allowed (public group, or active member).
    def post_visible(p):
        g = store.get("groups", p.get("groupId"))
        if not g:
            return False
        if g.get("visibility") == "PRIVATE" and not is_active_member(store, g["id"], uid) and not admin:
            return False
        return p.get("status") == "PUBLISHED"
    posts = [p for p in store.all("posts") if post_visible(p)]

    # Conversations & messages: mine only.
    conversations = [c for c in store.all("conversations")
                     if uid in (c.get("participantIds") or []) and c.get("status") != "DECLINED"]
    conv_ids = {c["id"] for c in conversations}
    messages = [m for m in store.all("messages") if m.get("conversationId") in conv_ids]

    notifications = [n for n in store.all("notifications") if n.get("userId") == uid]

    # Verifications: own + all if admin. Children: own only.
    verifications = [v for v in store.all("verifications")
                     if v.get("userId") == uid or admin]
    children = [c for c in store.all("children") if c.get("familyUserId") == uid]

    # Reports & admin actions: admin only.
    reports = list(store.all("reports")) if admin else []
    admin_actions = list(store.all("adminActions")) if admin else []

    return {
        "users": users, "professionals": professionals, "verifications": verifications,
        "children": children, "questions": questions, "answers": answers, "groups": groups,
        "memberships": memberships, "posts": posts, "conversations": conversations,
        "messages": messages, "guides": guides, "reports": reports,
        "notifications": notifications, "adminActions": admin_actions,
    }


# --------------------------------------------------------------------------------------
# Auth endpoints
# --------------------------------------------------------------------------------------

def do_register(args):
    name = _s(args.get("displayName"))
    mail = _s(args.get("email")).lower()
    password = args.get("password") or ""
    if not name:
        raise ApiError("نام نمایشی را وارد کنید.")
    if "@" not in mail or len(mail) < 5:
        raise ApiError("ایمیل معتبر وارد کنید.")
    if len(password) < 6:
        raise ApiError("رمز عبور باید حداقل ۶ نویسه باشد.")
    if not args.get("acceptedTerms"):
        raise ApiError("برای ادامه، قوانین و حریم خصوصی را بپذیرید.")
    if has_email(mail):
        raise ApiError("این ایمیل قبلاً ثبت شده است.")
    store = Store()
    uid = new_id("u")
    user = {
        "id": uid, "displayName": name, "accountType": args.get("accountType") or "FAMILY",
        "email": mail, "photoUrl": None, "city": None, "bio": None, "status": "ACTIVE",
        "isAdmin": False, "privacy": {"showCity": False, "showBio": True, "allowMessageRequests": True},
        "notificationSettings": {}, "blockedUserIds": [], "joinedAt": now_ms(), "updatedAt": now_ms(),
    }
    store.put("users", user)
    set_cred(mail, password, uid)
    return {"token": make_token(uid), "user": user}


def do_login(args):
    mail = _s(args.get("email")).lower()
    uid = check_cred(mail, args.get("password") or "")
    if not uid:
        raise ApiError("ایمیل یا رمز عبور نادرست است.")
    store = Store()
    user = store.get("users", uid)
    if not user:
        raise ApiError("حساب یافت نشد.")
    if user.get("status") == "BLOCKED":
        raise ApiError("این حساب مسدود شده است.")
    return {"token": make_token(uid), "user": user}


# --------------------------------------------------------------------------------------
# HTTP handler
# --------------------------------------------------------------------------------------

class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *a):
        pass

    def _send(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _uid(self):
        auth = self.headers.get("Authorization", "")
        if auth.startswith("Bearer "):
            return verify_token(auth[7:])
        return None

    def _body(self):
        n = int(self.headers.get("Content-Length", "0") or "0")
        if n <= 0:
            return {}
        raw = self.rfile.read(n)
        try:
            return json.loads(raw.decode("utf-8"))
        except Exception:
            return {}

    def do_GET(self):
        if self.path.rstrip("/") == "/api/health":
            return self._send(200, {"ok": True})
        if self.path.rstrip("/") == "/api/state":
            uid = self._uid()
            if not uid:
                return self._send(401, {"ok": False, "error": NOT_SIGNED_IN})
            try:
                state = build_state(Store(), uid)
                return self._send(200, {"ok": True, "state": state})
            except Exception as e:
                return self._send(500, {"ok": False, "error": "خطای سرور: %s" % e})
        return self._send(404, {"ok": False, "error": "یافت نشد."})

    def do_POST(self):
        path = self.path.rstrip("/")
        args = self._body()
        try:
            if path == "/api/auth/register":
                return self._send(200, do_register(args))
            if path == "/api/auth/login":
                return self._send(200, do_login(args))
            if path == "/api/auth/reset":
                return self._send(200, {"ok": True})  # never reveal whether the email exists
            if path == "/api/action":
                uid = self._uid()
                if not uid:
                    return self._send(401, {"ok": False, "error": NOT_SIGNED_IN})
                name = args.get("name")
                fn = ACTIONS.get(name)
                if not fn:
                    return self._send(404, {"ok": False, "error": "عملیات ناشناخته."})
                store = Store()
                data = fn(store, uid, args.get("args") or {})
                resp = {"ok": True}
                if data:
                    resp["data"] = data
                resp["state"] = build_state(store, uid)  # fresh snapshot saves a round-trip
                return self._send(200, resp)
            return self._send(404, {"ok": False, "error": "یافت نشد."})
        except ApiError as e:
            return self._send(200, {"ok": False, "error": str(e)})
        except Exception as e:
            return self._send(500, {"ok": False, "error": "خطای سرور: %s" % e})


# --------------------------------------------------------------------------------------
# Schema + seed + CLI
# --------------------------------------------------------------------------------------

SCHEMA = """
CREATE TABLE IF NOT EXISTS docs (
    collection text NOT NULL,
    id text NOT NULL,
    data jsonb NOT NULL,
    PRIMARY KEY (collection, id)
);
CREATE INDEX IF NOT EXISTS docs_collection_idx ON docs(collection);
CREATE TABLE IF NOT EXISTS creds (
    email text PRIMARY KEY,
    salt text NOT NULL,
    pwhash text NOT NULL,
    uid text NOT NULL
);
"""

def cmd_init_db():
    # Schema only — no sample/mock content. Real content is created by admins in-app.
    cur = conn().cursor()
    cur.execute(SCHEMA)
    cur.close()
    print("init-db: schema ready.")


def cmd_create_admin(email, password, name):
    cmd_init_db()
    store = Store()
    mail = email.strip().lower()
    existing_uid = None
    for u in store.all("users"):
        if (u.get("email") or "").lower() == mail:
            existing_uid = u["id"]
            break
    uid = existing_uid or new_id("u")
    user = store.get("users", uid) or {
        "id": uid, "accountType": "COMMUNITY", "city": None, "bio": "حساب مدیریت سامانه.",
        "photoUrl": None, "privacy": {"showCity": False, "showBio": True, "allowMessageRequests": True},
        "notificationSettings": {}, "blockedUserIds": [], "joinedAt": now_ms(),
    }
    user.update({"displayName": name, "email": mail, "isAdmin": True,
                 "status": "ACTIVE", "updatedAt": now_ms()})
    store.put("users", user)
    set_cred(mail, password, uid)
    print("admin ready: %s (uid=%s)" % (mail, uid))


def main():
    if len(sys.argv) > 1:
        cmd = sys.argv[1]
        if cmd == "init-db":
            return cmd_init_db()
        if cmd == "create-admin":
            return cmd_create_admin(sys.argv[2], sys.argv[3], sys.argv[4])
        print("unknown command: %s" % cmd)
        return
    cmd_init_db()
    srv = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print("hamrah-api listening on 127.0.0.1:%d" % PORT)
    srv.serve_forever()


if __name__ == "__main__":
    main()
