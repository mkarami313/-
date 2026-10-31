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
  POST /api/auth/forgot    {email}                  -> {ok:true} always; emails an 8-digit code if SMTP is set
  POST /api/auth/reset     {email,code,newPassword} -> {ok:true} | {ok:false,error}
  POST /api/tts            {text} (Bearer)          -> audio bytes   (GET ?text= kept for old clients)
  GET  /api/report?childId=&from=&to=&share=        (Bearer) -> printable RTL HTML report
  GET  /api/state          (Bearer)   -> a snapshot scoped to what the caller may read
  POST /api/action         (Bearer) {name, args:{...}}  -> {ok:true, data?} | {ok:false, error}

The client holds the /state snapshot in a local mirror and re-reads it after each action,
so the existing reactive UI keeps working unchanged.

CLI:
  python3 hamrah-api.py                      # run the server
  python3 hamrah-api.py init-db              # create tables + seed guides (idempotent)
  python3 hamrah-api.py feature ID on|off                 # toggle a module for all users
  python3 hamrah-api.py perm ID ROLE,ROLE|all             # restrict a module to account types
  python3 hamrah-api.py create-admin EMAIL PASSWORD "نام"   # make/replace an admin account
  python3 hamrah-api.py mailtest TO                       # send a test email through SMTP_* settings
  python3 hamrah-api.py sweep                             # delete orphan uploads (unreferenced, >24h old)
"""

import os
import re
import sys
import json
import time
import hmac
import base64
import hashlib
import secrets
import threading
import traceback
import urllib.request
import urllib.parse
import urllib.error
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


def _cfg(key, default=""):
    return os.environ.get(key) or ENV.get(key, default)


def log(*a):
    print(*a, file=sys.stderr, flush=True)


DB_DSN = _cfg("DB_DSN", "dbname=rasta_hamrah user=rasta_office host=127.0.0.1 port=5432")
_jwt = _cfg("JWT_SECRET")
if len(_jwt) < 16 or _jwt == "change-me":
    sys.exit("hamrah-api: JWT_SECRET is missing or too short (set it in .env) - refusing to start.")
JWT_SECRET = _jwt.encode("utf-8")
PORT = int(_cfg("PORT", "8150"))
TOKEN_TTL = int(_cfg("TOKEN_TTL_SECONDS", str(30 * 24 * 3600)))  # 30 days

# Outgoing mail (password-reset codes). All optional: without SMTP_HOST nothing is sent and the
# admin path (adminIssueReset) stays the fallback.
SMTP_HOST = _cfg("SMTP_HOST")
SMTP_PORT = int(_cfg("SMTP_PORT", "587") or "587")
SMTP_USER = _cfg("SMTP_USER")
SMTP_PASS = _cfg("SMTP_PASS")
SMTP_FROM = _cfg("SMTP_FROM") or SMTP_USER

# FCM (push). All Google traffic egresses via the box82 German-IP proxy (see FCM_PROXY) so
# the Firebase project is never hit from an Iranian IP. Both are optional: without the SA
# file, push is simply skipped and only the app's own poll channel delivers.
FCM_SA_FILE = ENV.get("FCM_SA_FILE", os.path.join(HERE, "fcm_sa.json"))
FCM_PROXY = ENV.get("FCM_PROXY", "http://127.0.0.1:12080")

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


PBKDF2_ITERS = 600000
_hash_sem = threading.BoundedSemaphore(4)  # cap concurrent KDF work (CPU-DoS guard)
MAX_PASSWORD = 128


def _pbkdf2(password, salt, iters):
    with _hash_sem:
        return hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt.encode("utf-8"), iters).hex()


def set_cred(email, password, uid):
    salt = secrets.token_hex(16)
    h = _pbkdf2(password, salt, PBKDF2_ITERS)
    cur = conn().cursor()
    cur.execute(
        "INSERT INTO creds(email,salt,pwhash,uid,iters) VALUES(%s,%s,%s,%s,%s) "
        "ON CONFLICT(email) DO UPDATE SET salt=EXCLUDED.salt,pwhash=EXCLUDED.pwhash,"
        "uid=EXCLUDED.uid,iters=EXCLUDED.iters",
        (email.strip().lower(), salt, h, uid, PBKDF2_ITERS),
    )
    cur.close()


def check_cred(email, password):
    mail = email.strip().lower()
    cur = conn().cursor()
    cur.execute("SELECT salt,pwhash,uid,iters FROM creds WHERE email=%s", (mail,))
    row = cur.fetchone()
    cur.close()
    if not row:
        _pbkdf2(password, "0" * 32, PBKDF2_ITERS)  # equalise timing: don't reveal unknown emails
        return None
    salt, pwhash, uid, iters = row
    h = _pbkdf2(password, salt, iters or 120000)
    if not hmac.compare_digest(h, pwhash):
        return None
    if (iters or 120000) < PBKDF2_ITERS:  # transparent upgrade of old hashes
        try:
            set_cred(mail, password, uid)
        except Exception:
            log("cred upgrade failed:", traceback.format_exc(limit=2))
    return uid


def cred_uid(email):
    cur = conn().cursor()
    cur.execute("SELECT uid FROM creds WHERE email=%s", (email.strip().lower(),))
    row = cur.fetchone()
    cur.close()
    return row[0] if row else None


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


def make_token(uid, iat=None):
    header = _b64(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
    now = int(iat if iat is not None else time.time())
    payload = _b64(json.dumps({"uid": uid, "iat": now, "exp": now + TOKEN_TTL}).encode())
    signing = ("%s.%s" % (header, payload)).encode()
    sig = _b64(hmac.new(JWT_SECRET, signing, hashlib.sha256).digest())
    return "%s.%s.%s" % (header, payload, sig)


def verify_token(token):
    r = verify_token_full(token)
    return r[0] if r else None


def verify_token_full(token):
    """-> (uid, iat) for a validly signed, unexpired token, else None."""
    try:
        header, payload, sig = token.split(".")
        signing = ("%s.%s" % (header, payload)).encode()
        expected = _b64(hmac.new(JWT_SECRET, signing, hashlib.sha256).digest())
        if not hmac.compare_digest(sig, expected):
            return None
        data = json.loads(_b64d(payload))
        if data.get("exp", 0) < int(time.time()):
            return None
        uid = data.get("uid")
        return (uid, int(data.get("iat", 0))) if uid else None
    except Exception:
        return None


def token_user(token):
    """Full auth check: signature + expiry + user still exists, is not BLOCKED/DELETED and the
    token was issued after the user's tokensValidAfter (password reset / forced logout)."""
    r = verify_token_full(token)
    if not r:
        return None
    uid, iat = r
    cur = conn().cursor()
    cur.execute("SELECT data FROM docs WHERE collection='users' AND id=%s", (uid,))
    row = cur.fetchone()
    cur.close()
    if not row:
        return None
    u = row[0]
    if u.get("status") in ("BLOCKED", "DELETED"):
        return None
    if iat < int(u.get("tokensValidAfter", 0) or 0):
        return None
    return uid


# --------------------------------------------------------------------------------------
# Rate limiting (in-memory, per process) + serialisation of writes
# --------------------------------------------------------------------------------------

class RateLimiter:
    def __init__(self):
        self._h = {}
        self._lock = threading.Lock()

    def _prune(self, key, window, now):
        q = [t for t in self._h.get(key, ()) if now - t < window]
        if q:
            self._h[key] = q
        else:
            self._h.pop(key, None)
        return q

    def blocked(self, key, limit, window):
        with self._lock:
            return len(self._prune(key, window, time.time())) >= limit

    def hit(self, key, window):
        with self._lock:
            now = time.time()
            q = self._prune(key, window, now)
            q.append(now)
            self._h[key] = q
            if len(self._h) > 20000:  # bound memory under key-spraying
                for k in list(self._h)[:5000]:
                    self._prune(k, window, now)

    def allow(self, key, limit, window):
        """Count one event; False when the limit for the window is already used up."""
        with self._lock:
            now = time.time()
            q = self._prune(key, window, now)
            if len(q) >= limit:
                return False
            q.append(now)
            self._h[key] = q
            return True

    def clear(self, key):
        with self._lock:
            self._h.pop(key, None)


RL = RateLimiter()
TOO_MANY = "تلاش‌های بیش از حد. چند دقیقه بعد دوباره امتحان کنید."

# One process-wide lock serialises every mutating action, so read-modify-write sequences on the
# docs table (slot booking, donation requests, helpful toggles, per-user caps) cannot interleave.
MUT_LOCK = threading.RLock()


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


# ---- FCM push (HTTP v1, via the German-IP proxy) ----

_fcm_lock = threading.Lock()
_fcm_token = {"value": None, "exp": 0}


def _fcm_opener():
    """urllib opener that routes HTTPS through the box82 proxy (German egress)."""
    if FCM_PROXY:
        return urllib.request.build_opener(
            urllib.request.ProxyHandler({"http": FCM_PROXY, "https": FCM_PROXY}))
    return urllib.request.build_opener()


def _fcm_access_token():
    """Service-account OAuth2 access token for the FCM scope. Cached ~55 min."""
    import jwt  # PyJWT
    with _fcm_lock:
        if _fcm_token["value"] and _fcm_token["exp"] > time.time() + 60:
            return _fcm_token["value"]
        sa = json.load(open(FCM_SA_FILE, encoding="utf-8"))
        now = int(time.time())
        assertion = jwt.encode(
            {"iss": sa["client_email"], "scope": "https://www.googleapis.com/auth/firebase.messaging",
             "aud": "https://oauth2.googleapis.com/token", "iat": now, "exp": now + 3600},
            sa["private_key"], algorithm="RS256")
        data = urllib.parse.urlencode(
            {"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion": assertion}).encode()
        req = urllib.request.Request("https://oauth2.googleapis.com/token", data=data, method="POST")
        resp = json.load(_fcm_opener().open(req, timeout=15))
        _fcm_token["value"] = resp["access_token"]
        _fcm_token["exp"] = time.time() + int(resp.get("expires_in", 3600))
        return _fcm_token["value"]


def _fcm_project():
    return json.load(open(FCM_SA_FILE, encoding="utf-8"))["project_id"]


def _fcm_send_one(device_token, title, body, data):
    """Returns (ok, should_remove_token). Data-only + high priority so the app shows it
    itself (and dedupes on notifId), which keeps a single push even with two channels.
    A token is pruned only when FCM says it is UNREGISTERED / NOT_FOUND."""
    try:
        access = _fcm_access_token()
        payload = json.dumps({"message": {
            "token": device_token,
            "android": {"priority": "high"},
            "data": {k: str(v) for k, v in data.items()} | {"title": title, "body": body},
        }}).encode()
        req = urllib.request.Request(
            "https://fcm.googleapis.com/v1/projects/%s/messages:send" % _fcm_project(),
            data=payload, method="POST",
            headers={"Authorization": "Bearer %s" % access, "Content-Type": "application/json"})
        _fcm_opener().open(req, timeout=15)
        return True, False
    except urllib.error.HTTPError as e:
        status, codes = "", []
        try:
            err = json.loads(e.read().decode()).get("error", {})
            status = err.get("status", "")
            codes = [d.get("errorCode", "") for d in err.get("details", []) if isinstance(d, dict)]
        except Exception:
            pass
        remove = status == "NOT_FOUND" or "UNREGISTERED" in codes or status == "UNREGISTERED"
        if not remove:
            log("fcm send failed: http=%s status=%s codes=%s" % (e.code, status, codes))
        return False, remove
    except Exception as e:
        log("fcm send error: %s" % type(e).__name__)
        return False, False


def _fcm_fanout(pairs, title, body, ntype, related_id):
    """pairs = [(device_token, notifId)] -> send on a daemon thread, prune dead tokens."""
    if not pairs or not os.path.exists(FCM_SA_FILE):
        return

    def worker():
        dead = []
        for tok, nid in pairs:
            data = {"notifId": nid, "type": ntype or "", "relatedId": related_id or ""}
            ok, remove = _fcm_send_one(tok, title, body, data)
            if remove:
                dead.append(tok)
        if dead:
            try:
                cur = conn().cursor()
                for tok in dead:
                    cur.execute("DELETE FROM docs WHERE collection='pushTokens' AND id=%s", (tok,))
                cur.close()
            except Exception:
                log("fcm prune failed:", traceback.format_exc(limit=2))

    threading.Thread(target=worker, daemon=True).start()


def push_to_user(store, user_id, nid, ntype, title, body, related_id):
    """Fan out a push to the user's registered devices, off the request thread."""
    tokens = [t["id"] for t in store.all("pushTokens") if t.get("userId") == user_id]
    _fcm_fanout([(t, nid) for t in tokens], title, body, ntype, related_id)


def add_notification(store, user_id, ntype, title, body, related_id=None, force=False):
    recipient = store.get("users", user_id)
    if not recipient:
        return
    settings = recipient.get("notificationSettings") or {}
    key = NOTIF_TOGGLE.get(ntype)
    if not force and key is not None and settings.get(key, True) is False:
        return
    nid = new_id("n")
    store.put("notifications", {
        "id": nid, "userId": user_id, "type": ntype, "title": title,
        "body": body, "relatedId": related_id, "read": False, "createdAt": now_ms(),
    })
    # Fan out a push to the user's registered devices. The device dedupes on `nid`, so a
    # user who happens to receive via more than one channel still sees it exactly once.
    push_to_user(store, user_id, nid, ntype, title, body, related_id)


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


LIMITED_MSG = "حساب شما محدود شده است و فقط امکان مشاهده دارید."
# Actions a LIMITED account may still run: reads, and everything that only removes/protects its own data.
LIMITED_OK = {
    "state", "recList", "donationList", "donationGet", "search", "shareList", "shareView", "exportMyData",
    "fileUrls", "deleteAccount", "changePassword", "logoutAllDevices", "blockUser", "unblockUser",
    "updatePrivacy", "updateNotificationSettings", "markRead", "markAllRead", "registerPush",
    "unregisterPush", "recDelete", "deleteChild", "deleteQuestion", "deletePost", "leave", "shareRevoke",
    "endConversation", "declineConversation", "toggleMute", "slotCancel", "donationRemove",
}


def _require_active(store, uid, name=""):
    """Writes need an ACTIVE account. LIMITED may still read and delete/protect its own data."""
    _require(uid)
    u = store.get("users", uid)
    if u and u.get("status") == "LIMITED" and name not in LIMITED_OK and not u.get("isAdmin"):
        raise ApiError(LIMITED_MSG)
    return u


def _blocked_between(store, a, b):
    """True when either user has blocked the other."""
    ua, ub = store.get("users", a) or {}, store.get("users", b) or {}
    return b in (ua.get("blockedUserIds") or []) or a in (ub.get("blockedUserIds") or [])


def _blocked_ids(store, uid):
    """Ids hidden from `uid`: everyone they blocked plus everyone who blocked them."""
    me = store.get("users", uid) or {}
    out = set(me.get("blockedUserIds") or [])
    for u in store.all("users"):
        if uid in (u.get("blockedUserIds") or []):
            out.add(u["id"])
    return out


# ---- User / professional / children / verification ----

def a_updateProfile(store, uid, args):
    _require(uid)
    if not _s(args.get("displayName")):
        raise ApiError("نام نمایشی را وارد کنید.")
    u = store.get("users", uid)
    if not u:
        raise ApiError("حساب یافت نشد.")
    u["displayName"] = _s(args.get("displayName"))[:60]
    u["city"] = _s(args.get("city"))[:60] or None
    u["bio"] = _s(args.get("bio"))[:600] or None
    old_pid = u.get("photoId")
    pid = args.get("photoId")
    if isinstance(pid, str) and pid == "":  # explicit removal
        u["photoId"], u["photoUrl"] = None, None
    elif isinstance(pid, str):
        if not HEX32.fullmatch(pid):
            raise ApiError("شناسهٔ فایل نامعتبر است.")
        _check_file_ownership(store, uid, [pid], image_only=True)
        u["photoId"], u["photoUrl"] = pid, "files/%s" % pid
    else:  # absent/null never wipes the photo
        photo = args.get("photoUrl")
        if isinstance(photo, str) and photo.strip():
            u["photoUrl"] = photo.strip()[:500]
    if old_pid and old_pid != u.get("photoId"):
        _drop_file(store, uid, old_pid)
    u["updatedAt"] = now_ms()
    store.put("users", u)


PRIVACY_KEYS = ("showCity", "showBio", "allowMessageRequests")


def _bool_settings(raw, keys, base):
    out = dict(base or {})
    if isinstance(raw, dict):
        for k in keys:
            if isinstance(raw.get(k), bool):
                out[k] = raw[k]
    return {k: v for k, v in out.items() if k in keys}


def a_updatePrivacy(store, uid, args):
    _require(uid)
    u = store.get("users", uid)
    u["privacy"] = _bool_settings(args.get("settings"), PRIVACY_KEYS, u.get("privacy"))
    u["updatedAt"] = now_ms()
    store.put("users", u)


def a_updateNotificationSettings(store, uid, args):
    _require(uid)
    u = store.get("users", uid)
    u["notificationSettings"] = _bool_settings(
        args.get("settings"), tuple(NOTIF_TOGGLE.values()), u.get("notificationSettings"))
    u["updatedAt"] = now_ms()
    store.put("users", u)


def a_blockUser(store, uid, args):
    _require(uid)
    target = args.get("targetUserId")
    if uid == target:
        raise ApiError("نمی‌توانید خودتان را مسدود کنید.")
    if not isinstance(target, str) or not store.get("users", target):
        raise ApiError("کاربر یافت نشد.")
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


def _pro_fields(args):
    title = _s(args.get("title"))
    if not title:
        raise ApiError("عنوان تخصص را وارد کنید.")
    if len(title) > 80:
        raise ApiError("عنوان تخصص حداکثر ۸۰ نویسه باشد.")
    raw = args.get("workAreas")
    if raw is None:
        raw = []
    bad_areas = ApiError("حوزه‌های کاری حداکثر ۲۰ مورد و هرکدام تا ۴۰ نویسه باشد.")
    if not isinstance(raw, list):
        raise bad_areas
    areas = []
    for x in raw:
        if not isinstance(x, str):
            raise bad_areas
        x = x.strip()
        if len(x) > 40:
            raise bad_areas
        if x and x not in areas:
            areas.append(x)
    if len(areas) > 20:
        raise bad_areas
    city, exp = _s(args.get("city")), _s(args.get("experience"))
    if len(city) > 60:
        raise ApiError("شهر حداکثر ۶۰ نویسه باشد.")
    if len(exp) > 3000:
        raise ApiError("سابقه حداکثر ۳۰۰۰ نویسه باشد.")
    out = {"title": title, "workAreas": areas, "city": city or None,
           "offersOnline": bool(args.get("offersOnline")), "experience": exp or None}
    for key, limit, label in (("fee", 80, "هزینه ویزیت حداکثر ۸۰ نویسه باشد."),
                              ("hours", 120, "ساعات کاری حداکثر ۱۲۰ نویسه باشد.")):
        if args.get(key) is None:  # absent/null leaves the stored value alone
            continue
        if not isinstance(args[key], str):
            raise ApiError(label)
        v = args[key].strip()
        if len(v) > limit:
            raise ApiError(label)
        out[key] = v or None
    return out


def a_upsertProfessionalProfile(store, uid, args):
    _require(uid)
    u = store.get("users", uid)
    if not u:
        raise ApiError("حساب یافت نشد.")
    if u.get("accountType") not in PRO_TYPES and not any(
            v.get("userId") == uid for v in store.all("verifications")):
        raise ApiError("ابتدا درخواست تأیید تخصص ثبت کنید.")
    fields = _pro_fields(args)
    existing = next((p for p in store.all("professionals") if p.get("userId") == uid), None)
    pid, old_pid = args.get("photoId"), (existing or {}).get("photoId")
    if isinstance(pid, str) and pid == "":
        fields["photoId"] = None
    elif isinstance(pid, str):
        if not HEX32.fullmatch(pid):
            raise ApiError("شناسهٔ فایل نامعتبر است.")
        _check_file_ownership(store, uid, [pid], image_only=True)
        fields["photoId"] = pid
    if old_pid and "photoId" in fields and fields["photoId"] != old_pid:
        _drop_file(store, uid, old_pid)
    if existing:
        existing.update(fields, updatedAt=now_ms())
        store.put("professionals", existing)
    else:
        status = "VERIFIED" if u.get("accountType") in PRO_TYPES else "NONE"
        store.put("professionals", dict(fields, id=new_id("p"), userId=uid, verificationStatus=status,
                                        createdAt=now_ms(), updatedAt=now_ms()))


def _latest_verification(store, uid):
    mine = [v for v in store.all("verifications") if v.get("userId") == uid]
    return max(mine, key=lambda v: (v.get("createdAt", 0), v.get("updatedAt", 0)), default=None)


def a_submitVerificationRequest(store, uid, args):
    _require(uid)
    u = store.get("users", uid)
    if not u:
        raise ApiError("حساب یافت نشد.")
    if u.get("isAdmin"):
        raise ApiError("حساب مدیریت امکان درخواست تأیید تخصص ندارد.")
    title = _s(args.get("professionalTitle"))
    if not title:
        raise ApiError("عنوان تخصص را وارد کنید.")
    if len(title) > 80:
        raise ApiError("عنوان تخصص حداکثر ۸۰ نویسه باشد.")
    rtype = args.get("requestedType") or args.get("accountType") or "PROFESSIONAL"
    if rtype not in PRO_TYPES:
        raise ApiError("نوع تخصص نامعتبر است.")
    if u.get("accountType") in PRO_TYPES:
        raise ApiError("تخصص شما قبلاً تأیید شده است.")
    refs = args.get("documentRefs") or []
    if not isinstance(refs, list) or len(refs) > 5 or any(not isinstance(x, str) or not HEX32.fullmatch(x) for x in refs):
        raise ApiError("مدارک پیوست‌شده نامعتبر است.")
    refs = list(dict.fromkeys(refs))
    _check_file_ownership(store, uid, refs)
    if not RL.allow("verif:" + uid, 5, 86400):
        raise ApiError("تعداد درخواست‌ها زیاد است؛ کمی بعد دوباره تلاش کنید.")
    lic, note = _s(args.get("licenseNumber")), _s(args.get("credentialNote"))
    if len(lic) > 60 or len(note) > 2000:
        raise ApiError("متن وارد‌شده بیش از حد طولانی است.")
    # Replace any existing PENDING request from this user (its documents are not orphaned: the new one reuses or drops them).
    store.delete_where("verifications",
                       lambda v: v.get("userId") == uid and v.get("status") == "PENDING")
    store.put("verifications", {
        "id": new_id("v"), "userId": uid, "professionalTitle": title,
        "licenseNumber": lic or None, "credentialNote": note or None,
        "requestedType": rtype, "documentRefs": refs,
        "status": "PENDING", "createdAt": now_ms(), "updatedAt": now_ms(),
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


def _child_fields(src, creating):
    out = {}
    if creating or "nickname" in src:
        nick = _s(src.get("nickname"))[:60]
        if "nickname" in src and not nick and not creating:
            raise ApiError("نام کودک را وارد کنید.")
        out["nickname"] = nick or None
    if creating or "ageRange" in src:
        age = src.get("ageRange")
        out["ageRange"] = age[:30] if isinstance(age, str) and age.strip() else "UNSPECIFIED"
    if creating or "interests" in src:
        raw = src.get("interests") or []
        if not isinstance(raw, list):
            raise ApiError("علاقه‌مندی‌ها نامعتبر است.")
        out["interests"] = [_s(i)[:40] for i in raw[:20] if isinstance(i, str) and _s(i)]
    if "notes" in src or creating:
        notes = src.get("notes")
        notes = notes.strip() if isinstance(notes, str) else ""
        if len(notes) > 1000:
            raise ApiError("یادداشت حداکثر ۱۰۰۰ نویسه باشد.")
        if notes or "notes" in src:
            out["notes"] = notes
    return out


def a_addChild(store, uid, args):
    _require(uid)
    if sum(1 for c in store.all("children") if c.get("familyUserId") == uid) >= 20:
        raise ApiError("به سقف تعداد پروفایل کودک رسیده‌اید.")
    store.put("children", dict(_child_fields(args, True), id=new_id("c"), familyUserId=uid,
                               isPublic=False, createdAt=now_ms(), updatedAt=now_ms()))


def a_updateChild(store, uid, args):
    _require(uid)
    cid = _s(args.get("id") or args.get("childId"))
    mine = next((c for c in store.all("children") if c.get("id") == cid and c.get("familyUserId") == uid), None)
    if not mine:
        raise ApiError("پروفایل کودک پیدا نشد.")
    fields = args.get("fields")
    src = fields if isinstance(fields, dict) else args
    mine.update(_child_fields(src, False))
    mine["updatedAt"] = now_ms()
    store.put("children", mine)


def a_deleteChild(store, uid, args):
    _require(uid)
    if args.get("confirm") is not True:
        raise ApiError("برای حذف پروفایل کودک تأیید لازم است (در صورت نیاز برنامه را به‌روز کنید).")
    cid = args.get("childId") or args.get("id")
    mine = next((c for c in store.all("children") if c.get("id") == cid and c.get("familyUserId") == uid), None)
    if not mine:
        return
    store.delete("children", cid)
    for kind, spec in REC_KINDS.items():  # everything recorded for this child goes with it
        if "childId" in spec["fields"]:
            for d in list(store.all("r_" + kind)):
                if d.get("ownerId") == uid and d.get("childId") == cid:
                    _delete_rec(store, kind, d)
    store.delete_where("shares", lambda sh: sh.get("ownerId") == uid and sh.get("childId") == cid)


def a_exportMyData(store, uid, args):
    _require(uid)
    u = store.get("users", uid)
    if not u:
        raise ApiError("حساب یافت نشد.")
    mine = {k: [d for d in store.all("r_" + k) if d.get("ownerId") == uid] for k in REC_KINDS}
    convs = [c for c in store.all("conversations") if uid in (c.get("participantIds") or [])]
    cids = {c["id"] for c in convs}
    prof = next((p for p in store.all("professionals") if p.get("userId") == uid), None)
    export = {
        "exportedAt": now_ms(), "app": "hamrah-teyf",
        "profile": {k: v for k, v in u.items() if k != "tokensValidAfter"},
        "professionalProfile": prof,
        "verificationRequests": [v for v in store.all("verifications") if v.get("userId") == uid],
        "children": [c for c in store.all("children") if c.get("familyUserId") == uid],
        "records": {k: v for k, v in mine.items() if v},
        "bookedSlots": [d for d in store.all("r_slot") if d.get("bookedBy") == uid],
        "questions": [q for q in store.all("questions") if q.get("authorId") == uid],
        "answers": [a for a in store.all("answers") if a.get("authorId") == uid],
        "posts": [p for p in store.all("posts") if p.get("authorId") == uid],
        "memberships": [m for m in store.all("memberships") if m.get("userId") == uid],
        "conversations": convs,
        "messages": [m for m in store.all("messages") if m.get("conversationId") in cids],
        "shares": [{k: v for k, v in sh.items() if k != "codeHash"} for sh in store.all("shares")
                   if sh.get("ownerId") == uid],
        "donations": [d for d in store.all("donations") if d.get("ownerId") == uid],
        "notifications": [n for n in store.all("notifications") if n.get("userId") == uid],
        "files": [{k: f.get(k) for k in ("id", "name", "mime", "size", "createdAt")}
                  for f in store.all("files") if f.get("ownerId") == uid],
    }
    lines = [
        "# نسخهٔ اطلاعات حساب شما",
        "نام نمایشی: %s" % u.get("displayName", ""),
        "نوع حساب: %s" % u.get("accountType", ""),
        "ایمیل: %s" % (u.get("email") or "-"),
        "شهر: %s" % (u.get("city") or "-"),
        "تعداد پرسش‌ها: %d" % len(export["questions"]),
        "تعداد پاسخ‌ها: %d" % len(export["answers"]),
        "تعداد پروفایل‌های کودک: %d" % len(export["children"]),
        "عضویت در گروه‌ها: %d" % sum(1 for m in export["memberships"] if m.get("status") == "ACTIVE"),
    ]
    return {"json": json.dumps(export, ensure_ascii=False, indent=2),
            "text": "\n".join(lines),
            "filename": "hamrah-export-%s.json" % time.strftime("%Y-%m-%d")}


def _unlink_file(doc):
    ext = UPLOAD_TYPES.get(doc.get("mime"))
    if ext:
        try:
            os.remove(os.path.join(UPLOAD_DIR, "%s.%s" % (doc["id"], ext)))
        except OSError:
            pass


def a_changePassword(store, uid, args):
    _require(uid)
    u = store.get("users", uid)
    if not u or not u.get("email"):
        raise ApiError("حساب یافت نشد.")
    cur_pw, new_pw = args.get("current"), args.get("new")
    key = "chpw:" + uid
    if RL.blocked(key, 5, 900):
        raise ApiError(TOO_MANY)
    if not isinstance(cur_pw, str) or not cur_pw or len(cur_pw) > MAX_PASSWORD \
            or check_cred(u["email"], cur_pw) != uid:
        RL.hit(key, 900)
        raise ApiError("رمز فعلی نادرست است.")
    if not isinstance(new_pw, str) or len(new_pw) < 6:
        raise ApiError("رمز عبور باید حداقل ۶ نویسه باشد.")
    if len(new_pw) > MAX_PASSWORD:
        raise ApiError("رمز عبور بیش از حد طولانی است.")
    if new_pw == cur_pw:
        raise ApiError("رمز جدید باید با رمز فعلی متفاوت باشد.")
    set_cred(u["email"], new_pw, uid)
    iat = int(time.time()) + 1  # every older token (other devices) dies; the returned one lives
    u["tokensValidAfter"] = iat
    u["updatedAt"] = now_ms()
    store.put("users", u)
    RL.clear(key)
    return {"token": make_token(uid, iat)}


def a_deleteAccount(store, uid, args):
    _require(uid)
    u = store.get("users", uid)
    if not u:
        raise ApiError("حساب یافت نشد.")
    pw = args.get("password")
    if not isinstance(pw, str) or not pw:
        raise ApiError("برای حذف حساب، رمز عبور را وارد کنید (در صورت نیاز برنامه را به‌روز کنید).")
    key = "delpw:" + uid
    if RL.blocked(key, 5, 900):
        raise ApiError(TOO_MANY)
    if len(pw) > MAX_PASSWORD or not u.get("email") or check_cred(u["email"], pw) != uid:
        RL.hit(key, 900)
        raise ApiError("رمز عبور نادرست است.")
    wipe_account(store, uid)


def _hand_over_groups(store, uid):
    """Groups the leaving user owned: hand to the oldest active moderator/member, else archive."""
    for g in list(store.all("groups")):
        if g.get("ownerId") != uid:
            continue
        cands = sorted((m for m in store.all("memberships")
                        if m.get("groupId") == g["id"] and m.get("userId") != uid and m.get("status") == "ACTIVE"),
                       key=lambda m: (0 if m.get("role") == "MODERATOR" else 1, m.get("createdAt", 0)))
        if cands:
            heir = cands[0]
            heir.update(role="OWNER", updatedAt=now_ms())
            store.put("memberships", heir)
            g["ownerId"] = heir["userId"]
            store.put("groups", g)
            add_notification(store, heir["userId"], "SAFETY_NOTICE", "مدیریت گروه به شما رسید",
                             "مدیر قبلی گروه «%s» حساب خود را حذف کرد و مدیریت به شما واگذار شد." % g.get("title", ""),
                             g["id"], force=True)
        else:
            g["status"] = "ARCHIVED"
            g["memberCount"] = 0
            g["updatedAt"] = now_ms()
            store.put("groups", g)


def wipe_account(store, uid):
    u = store.get("users", uid)
    if not u:
        raise ApiError("حساب یافت نشد.")
    # People who booked this person's slots are told before the slots disappear.
    for d in store.all("r_slot"):
        if d.get("ownerId") == uid and d.get("bookedBy") and d["bookedBy"] != uid:
            add_notification(store, d["bookedBy"], "BOOKING", "نوبت لغو شد",
                             "متخصص حساب خود را حذف کرد و نوبت «%s» لغو شد." % d.get("when", ""), d["id"])
    _hand_over_groups(store, uid)
    my_groups = {m["groupId"] for m in store.all("memberships") if m.get("userId") == uid}
    if u.get("email"):
        remove_cred(u["email"])
        store.delete("resets", _reset_key(u["email"]))
    store.delete("users", uid)
    store.delete_where("professionals", lambda p: p.get("userId") == uid)
    store.delete_where("verifications", lambda v: v.get("userId") == uid)
    store.delete_where("children", lambda c: c.get("familyUserId") == uid)
    store.delete_where("notifications", lambda n: n.get("userId") == uid)
    store.delete_where("memberships", lambda m: m.get("userId") == uid)
    for g in store.all("groups"):  # memberCount follows reality
        if g["id"] in my_groups and g.get("status") != "ARCHIVED":
            n = sum(1 for m in store.all("memberships") if m.get("groupId") == g["id"] and m.get("status") == "ACTIVE")
            if g.get("memberCount") != n:
                g["memberCount"] = n
                store.put("groups", g)
    my_convs = [c["id"] for c in store.all("conversations") if uid in (c.get("participantIds") or [])]
    store.delete_where("messages", lambda msg: msg.get("conversationId") in my_convs)
    store.delete_where("conversations", lambda c: uid in (c.get("participantIds") or []))
    store.delete_where("pushTokens", lambda t: t.get("userId") == uid)
    store.delete_where("reports", lambda r: r.get("reporterId") == uid)
    store.delete_where("shares", lambda sh: sh.get("ownerId") == uid or sh.get("redeemedBy") == uid)
    # Records kept in the generic r_* collections (child file, tools, booking...). A slot the user
    # had booked goes back on the market instead of being deleted.
    for kind in REC_KINDS:
        for d in list(store.all("r_" + kind)):
            if d.get("ownerId") == uid:
                store.delete("r_" + kind, d["id"])
            elif kind == "slot" and d.get("bookedBy") == uid:
                d.update(status="OPEN", bookedBy="", bookedByName="", updatedAt=now_ms())
                store.put("r_slot", d)
    # Donations: own listings go; requests/reservations made by the user are withdrawn.
    store.delete_where("donations", lambda d: d.get("ownerId") == uid)
    for d in store.all("donations"):
        if any(r.get("userId") == uid for r in d.get("requests") or []) or d.get("recipientId") == uid:
            d["requests"] = [r for r in d.get("requests") or [] if r.get("userId") != uid]
            if d.get("recipientId") == uid:
                d["recipientId"] = None
                if d.get("status") == "RESERVED":
                    d["status"] = "AVAILABLE"
            store.put("donations", d)
    # Uploaded files: bytes and docs.
    for f in list(store.all("files")):
        if f.get("ownerId") == uid:
            _unlink_file(f)
            store.delete("files", f["id"])
    # Anonymize public contributions so threads stay readable.
    for q in store.all("questions"):
        if q.get("authorId") == uid:
            q["authorId"] = DELETED_USER_ID
            q["isAnonymous"] = True
            store.put("questions", q)
    for a in store.all("answers"):
        changed = False
        if a.get("authorId") == uid:
            a["authorId"] = DELETED_USER_ID
            changed = True
        if uid in (a.get("helpfulByUserIds") or []):
            a["helpfulByUserIds"] = [x for x in a["helpfulByUserIds"] if x != uid]
            changed = True
        if changed:
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
        add_notification(store, args.get("userId"), "GROUP_JOIN_REQUEST", "عضویت پذیرفته شد",
                         "درخواست عضویت شما در گروه «%s» پذیرفته شد." % ((g or {}).get("title") or ""), gid)


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
    g = store.get("groups", gid)
    if was_active and g:
        g["memberCount"] = max(0, int(g.get("memberCount", 0)) - 1)
        g["updatedAt"] = now_ms()
        store.put("groups", g)
    if target and args.get("userId") != uid:
        gt = (g or {}).get("title") or ""
        if was_active:
            add_notification(store, args.get("userId"), "SAFETY_NOTICE", "حذف از گروه",
                             "شما از گروه «%s» حذف شدید." % gt, gid, force=True)
        else:
            add_notification(store, args.get("userId"), "GROUP_JOIN_REQUEST", "درخواست عضویت رد شد",
                             "درخواست عضویت شما در گروه «%s» پذیرفته نشد." % gt, gid)


def a_createPost(store, uid, args):
    _require(uid)
    gid = args.get("groupId")
    if not is_active_member(store, gid, uid):
        raise ApiError("برای ارسال پست باید عضو فعال گروه باشید.")
    if len(_s(args.get("body"))) < 2:
        raise ApiError("متن پست را بنویسید.")
    reply_to = args.get("replyToPostId")
    parent = store.get("posts", reply_to) if isinstance(reply_to, str) and reply_to else None
    if parent and parent.get("groupId") != gid:
        parent = None
    doc = {
        "id": new_id("gp"), "groupId": gid, "authorId": uid, "body": _s(args.get("body")),
        "status": "PUBLISHED", "createdAt": now_ms(), "updatedAt": now_ms(),
    }
    if parent:
        doc["replyToPostId"] = parent["id"]
    store.put("posts", doc)
    g = store.get("groups", gid)
    if g:
        g["updatedAt"] = now_ms()
        store.put("groups", g)
    if parent and parent.get("authorId") not in (uid, DELETED_USER_ID) \
            and parent.get("authorId") not in _blocked_ids(store, uid):
        add_notification(store, parent["authorId"], "GROUP_REPLY", "پاسخ به پست شما",
                         "به پست شما در گروه «%s» پاسخ داده شد." % ((g or {}).get("title") or ""), gid)


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
    if not RL.allow("convreq:" + uid, 20, 3600):
        raise ApiError(TOO_MANY)
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
    if _blocked_between(store, uid, other):
        raise ApiError("امکان ارسال پیام به این کاربر وجود ندارد.")
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

REPORT_TYPES = {"QUESTION", "ANSWER", "GROUP_POST", "MESSAGE", "USER", "GUIDE", "DONATION"}
REPORT_STATUSES = {"OPEN", "UNDER_REVIEW", "ACTIONED", "DISMISSED"}


def a_createReport(store, uid, args):
    _require(uid)
    ctype, cid = args.get("contentType"), args.get("contentId")
    if ctype not in REPORT_TYPES or not isinstance(cid, str) or not cid or len(cid) > 80:
        raise ApiError("گزارش نامعتبر است.")
    if not RL.allow("report:" + uid, 30, 3600):
        raise ApiError(TOO_MANY)
    coll = {"QUESTION": "questions", "ANSWER": "answers", "GROUP_POST": "posts", "MESSAGE": "messages",
            "USER": "users", "GUIDE": "guides", "DONATION": "donations"}[ctype]
    if not store.get(coll, cid):
        raise ApiError("محتوای گزارش‌شده پیدا نشد.")
    if ctype == "USER" and cid == uid:
        raise ApiError("نمی‌توانید خودتان را گزارش کنید.")
    if any(r.get("reporterId") == uid and r.get("contentType") == ctype and r.get("contentId") == cid
           and r.get("status") in ("OPEN", "UNDER_REVIEW") for r in store.all("reports")):
        return {"duplicate": True}
    store.put("reports", {
        "id": new_id("r"), "reporterId": uid, "contentType": ctype,
        "contentId": cid, "reason": _s(args.get("reason"))[:40],
        "note": _s(args.get("note"))[:1000] or None, "status": "OPEN",
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
    if args.get("newStatus") not in REPORT_STATUSES:
        raise ApiError("وضعیت گزارش نامعتبر است.")
    rep["status"] = args.get("newStatus")
    rep["resolutionNote"] = note
    rep["reviewedByAdminId"] = uid
    rep["updatedAt"] = now_ms()
    store.put("reports", rep)
    _log_admin(store, uid, "RESOLVE_REPORT", "USER", rep["id"], note)
    if args.get("hideContent") is True and rep["status"] == "ACTIONED" \
            and rep.get("contentType") in ("QUESTION", "ANSWER", "GROUP_POST", "GUIDE", "DONATION", "USER"):
        try:
            _set_content_status(store, uid, rep["contentType"], rep["contentId"], "HIDDEN", "HIDE_CONTENT",
                                note or "پنهان‌سازی به‌دنبال رسیدگی به گزارش")
        except ApiError:
            pass  # the content is already gone or protected (admin account); the report stays resolved


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


# ---- Push device registration ----

def a_registerPush(store, uid, args):
    _require(uid)
    token = _s(args.get("token"))
    if not token:
        raise ApiError("توکن دستگاه خالی است.")
    if len(token) > 4096 or not re.fullmatch(r"[A-Za-z0-9:_\-.]+", token):
        raise ApiError("توکن دستگاه نامعتبر است.")
    if sum(1 for t in store.all("pushTokens") if t.get("userId") == uid) >= 10 \
            and not store.get("pushTokens", token):
        raise ApiError("تعداد دستگاه‌های ثبت‌شده به سقف رسیده است.")
    # doc id = token, so re-registering (incl. moving to another account) just overwrites.
    store.put("pushTokens", {
        "id": token, "userId": uid, "platform": _s(args.get("platform"))[:20] or "android",
        "updatedAt": now_ms(),
    })


def a_unregisterPush(store, uid, args):
    _require(uid)
    token = _s(args.get("token"))
    existing = store.get("pushTokens", token)
    if existing and existing.get("userId") == uid:
        store.delete("pushTokens", token)


# ---- Admin (audited) ----

CLIENT_ACTION_TYPES = {"VERIFY_PROFESSIONAL", "REJECT_PROFESSIONAL", "REQUEST_MORE_INFO", "VIEW_CREDENTIAL",
                       "HIDE_CONTENT", "DELETE_CONTENT", "LIMIT_ACCOUNT", "BLOCK_ACCOUNT", "RESOLVE_REPORT",
                       "PUBLISH_GUIDE"}


CLIENT_CONTENT_TYPES = {"QUESTION", "ANSWER", "GROUP_POST", "MESSAGE", "USER", "GUIDE"}


def _log_admin(store, admin_id, action_type, target_type, target_id, reason):
    # Types the app's enum doesn't know would break decoding of the admin log, so they go to a
    # server-side audit collection that is never sent to clients.
    coll = "adminActions" if action_type in CLIENT_ACTION_TYPES and target_type in CLIENT_CONTENT_TYPES else "adminAudit"
    store.put(coll, {
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
    if decision not in ("VERIFIED", "REJECTED", "MORE_INFO_NEEDED"):
        raise ApiError("تصمیم نامعتبر است.")
    latest = _latest_verification(store, req["userId"])
    if latest and latest["id"] != req["id"]:
        raise ApiError("این درخواست قدیمی است؛ درخواست جدیدتری از همین کاربر وجود دارد.")
    was_verified = req.get("status") == "VERIFIED"
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
    tu = store.get("users", req["userId"])
    if tu and not tu.get("isAdmin"):
        if decision == "VERIFIED":  # accounts register as FAMILY; approval grants the professional type
            if tu.get("accountType") not in PRO_TYPES:
                tu["accountType"] = req.get("requestedType") if req.get("requestedType") in PRO_TYPES else "PROFESSIONAL"
                tu["updatedAt"] = now_ms()
                store.put("users", tu)
        elif was_verified and tu.get("accountType") in PRO_TYPES:  # a verdict reversed: the type goes back
            tu["accountType"] = "FAMILY"
            tu["updatedAt"] = now_ms()
            store.put("users", tu)
    _log_admin(store, uid, action_type, "USER", req["userId"], reason)
    label = {"VERIFIED": "تأییدشده", "REJECTED": "ردشده", "MORE_INFO_NEEDED": "نیازمند اطلاعات بیشتر"}[decision]
    add_notification(store, req["userId"], "VERIFICATION_RESULT", "نتیجهٔ بررسی تخصص",
                     "وضعیت بررسی تخصص شما به «%s» تغییر کرد. توضیح بررسی‌کننده: %s" % (label, reason[:300]),
                     req["id"], force=True)


def a_adminSetAccountType(store, uid, args):
    _require(uid)
    if not is_admin(store, uid):
        raise ApiError("دسترسی مدیر لازم است.")
    reason = _s(args.get("reason"))
    if not reason:
        raise ApiError("ثبت دلیل تصمیم الزامی است.")
    atype = args.get("accountType")
    if atype not in ROLES:
        raise ApiError("نوع حساب نامعتبر است.")
    target = store.get("users", args.get("userId"))
    if not target:
        raise ApiError("کاربر یافت نشد.")
    if target.get("isAdmin"):
        raise ApiError("نوع حساب مدیر تغییر نمی‌کند.")
    target["accountType"] = atype
    target["updatedAt"] = now_ms()
    store.put("users", target)
    for pr in store.all("professionals"):
        if pr.get("userId") == target["id"]:
            pr["verificationStatus"] = "VERIFIED" if atype in PRO_TYPES else "NONE"
            pr["updatedAt"] = now_ms()
            store.put("professionals", pr)
    _log_admin(store, uid, "SET_ACCOUNT_TYPE", "USER", target["id"], reason + " [→ %s]" % atype)
    add_notification(store, target["id"], "VERIFICATION_RESULT", "تغییر نوع حساب",
                     "نوع حساب شما توسط مدیریت تغییر کرد. توضیح: %s" % reason[:300], target["id"], force=True)


def _apply_account_status(store, target, status):
    """Single place that changes a user's status so every path revokes sessions the same way."""
    prev = target.get("status")
    target["status"] = status
    target["updatedAt"] = now_ms()
    if status == "BLOCKED" or (prev == "BLOCKED" and status != "BLOCKED"):
        # Old tokens must not revive when the account is later restored.
        target["tokensValidAfter"] = int(time.time()) + 1
    store.put("users", target)
    if status == "BLOCKED":
        store.delete_where("pushTokens", lambda t: t.get("userId") == target["id"])
    elif prev != status:
        msg = {"LIMITED": "حساب شما توسط مدیریت محدود شد و فقط امکان مشاهده دارید.",
               "ACTIVE": "محدودیت حساب شما برداشته شد."}.get(status)
        if msg:
            add_notification(store, target["id"], "SAFETY_NOTICE", "وضعیت حساب", msg, force=True)


def _set_content_status(store, uid, ctype, cid, status, action, reason):
    if not is_admin(store, uid):
        raise ApiError("دسترسی مدیر لازم است.")
    if not _s(reason):
        raise ApiError("ثبت دلیل تصمیم الزامی است.")
    coll = {"QUESTION": "questions", "ANSWER": "answers", "GROUP_POST": "posts",
            "GUIDE": "guides", "DONATION": "donations"}.get(ctype)
    if coll:
        doc = store.get(coll, cid)
        if not doc:
            raise ApiError("محتوا یافت نشد.")
        doc["status"] = status
        store.put(coll, doc)
    elif ctype == "USER":  # hide -> limit the account, delete -> block it (never touches admins)
        tu = store.get("users", cid)
        if not tu:
            raise ApiError("کاربر یافت نشد.")
        if tu.get("isAdmin"):
            raise ApiError("وضعیت حساب مدیر تغییر نمی‌کند.")
        _apply_account_status(store, tu, "LIMITED" if status == "HIDDEN" else "BLOCKED")
    else:
        raise ApiError("نوع محتوا نامعتبر است.")
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
    if status not in ("ACTIVE", "LIMITED", "BLOCKED", "PENDING_REVIEW"):
        raise ApiError("وضعیت حساب نامعتبر است.")
    target = store.get("users", args.get("userId"))
    if not target:
        raise ApiError("کاربر یافت نشد.")
    if target.get("isAdmin") and status != "ACTIVE":
        raise ApiError("وضعیت حساب مدیر تغییر نمی‌کند.")
    _apply_account_status(store, target, status)
    action = "BLOCK_ACCOUNT" if status == "BLOCKED" else ("RESTORE_ACCOUNT" if status == "ACTIVE" else "LIMIT_ACCOUNT")
    _log_admin(store, uid, action, "USER", args.get("userId"), reason)


# ---- «دست‌به‌دست»: free item donations between families (no payments) ----

DONATION_CATEGORIES = ["لوازم کمک‌آموزشی", "اسباب‌بازی و بازی حسی", "لباس و کفش", "کتاب و کارت آموزشی",
                       "وسایل درمانی و کمکی", "سایر"]
DONATION_CONDITIONS = {"NEW": "نو", "GOOD": "سالم و تمیز", "USED": "کارکرده"}
_FILE_ID_RE = None


def _file_ids(urls):
    out = []
    for u in (urls or [])[:4]:
        m = re.search(r"files/([0-9a-f]{32})$", str(u))
        if m and m.group(1) not in out:
            out.append(m.group(1))
    return out


def _donation_view(store, d, uid):
    owner = d.get("ownerId") == uid
    v = {k: d.get(k) for k in ("id", "title", "description", "category", "condition", "city",
                               "imageIds", "status", "ownerId", "ownerName", "createdAt")}
    v["mine"] = owner
    reqs = d.get("requests") or []
    v["requestCount"] = len(reqs)
    v["iRequested"] = any(r["userId"] == uid for r in reqs)
    v["iAmRecipient"] = d.get("recipientId") == uid
    if owner:
        v["requests"] = reqs
        v["recipientId"] = d.get("recipientId")
    return v


def _donation(store, did, uid=None):
    d = store.get("donations", did)
    if not d or d.get("status") == "REMOVED":
        raise ApiError("این آگهی در دسترس نیست.")
    if d.get("status") == "HIDDEN" and not (uid and (d.get("ownerId") == uid or is_admin(store, uid))):
        raise ApiError("این آگهی در دسترس نیست.")
    return d


def a_donationList(store, uid, args):
    _require(uid)
    cat, city, mine = _s(args.get("category")), _s(args.get("city")), bool(args.get("mine"))
    items = []
    for d in store.all("donations"):
        if d.get("status") == "REMOVED":
            continue
        if d.get("status") == "HIDDEN" and d.get("ownerId") != uid and not is_admin(store, uid):
            continue
        if mine != (d.get("ownerId") == uid):
            if mine or d.get("status") == "GIVEN":
                continue
        if cat and d.get("category") != cat:
            continue
        if city and city not in _s(d.get("city")):
            continue
        items.append(d)
    items.sort(key=lambda d: d.get("createdAt", 0), reverse=True)
    return {"items": [_donation_view(store, d, uid) for d in items[:100]],
            "categories": DONATION_CATEGORIES}


def a_donationGet(store, uid, args):
    _require(uid)
    return {"item": _donation_view(store, _donation(store, args.get("id"), uid), uid)}


def a_donationCreate(store, uid, args):
    _require(uid)
    me = store.get("users", uid) or {}
    if me.get("status") in ("BLOCKED", "LIMITED"):
        raise ApiError("حساب شما اجازهٔ ثبت آگهی ندارد.")
    title, desc = _s(args.get("title")), _s(args.get("description"))
    if len(title) < 3 or len(title) > 100:
        raise ApiError("عنوان باید بین ۳ تا ۱۰۰ نویسه باشد.")
    if len(desc) > 1000:
        raise ApiError("توضیحات حداکثر ۱۰۰۰ نویسه.")
    cat = args.get("category")
    cond = args.get("condition")
    if cat not in DONATION_CATEGORIES or cond not in DONATION_CONDITIONS:
        raise ApiError("دسته‌بندی یا وضعیت کالا نامعتبر است.")
    mine_open = [d for d in store.all("donations") if d.get("ownerId") == uid and d.get("status") in ("AVAILABLE", "RESERVED")]
    if len(mine_open) >= 20:
        raise ApiError("حداکثر ۲۰ آگهی فعال می‌توانید داشته باشید.")
    imgs = _file_ids(args.get("imageUrls"))
    _check_file_ownership(store, uid, imgs, image_only=True)
    did = new_id("don")
    store.put("donations", {
        "id": did, "ownerId": uid, "ownerName": me.get("displayName", ""), "title": title,
        "description": desc, "category": cat, "condition": cond, "city": _s(args.get("city"))[:60],
        "imageIds": imgs, "status": "AVAILABLE", "requests": [],
        "recipientId": None, "createdAt": now_ms(), "updatedAt": now_ms(),
    })
    return {"id": did}


def a_donationRequest(store, uid, args):
    _require(uid)
    d = _donation(store, args.get("id"), uid)
    if d["ownerId"] == uid:
        raise ApiError("این آگهی مال خودتان است.")
    if d["status"] != "AVAILABLE":
        raise ApiError("این مورد دیگر در دسترس نیست.")
    if any(r["userId"] == uid for r in d["requests"]):
        raise ApiError("قبلاً درخواست داده‌اید.")
    me = store.get("users", uid) or {}
    d["requests"].append({"userId": uid, "name": me.get("displayName", ""),
                          "note": _s(args.get("note"))[:300], "createdAt": now_ms()})
    d["updatedAt"] = now_ms()
    store.put("donations", d)
    add_notification(store, d["ownerId"], "DONATION", "درخواست برای «%s»" % d["title"],
                     "%s این مورد را درخواست کرد." % (me.get("displayName") or "یک کاربر"), related_id=d["id"])


def _donation_owner(store, uid, did):
    d = _donation(store, did, uid)
    if d["ownerId"] != uid and not is_admin(store, uid):
        raise ApiError("فقط اهداکننده می‌تواند این کار را انجام دهد.")
    if d.get("status") == "HIDDEN" and not is_admin(store, uid):
        raise ApiError("این آگهی توسط مدیر مخفی شده است.")
    return d


def a_donationApprove(store, uid, args):
    _require(uid)
    d = _donation_owner(store, uid, args.get("id"))
    if d["status"] != "AVAILABLE":
        raise ApiError("این مورد دیگر در دسترس نیست.")
    rid = args.get("userId")
    if not any(r["userId"] == rid for r in d["requests"]):
        raise ApiError("این کاربر درخواستی ندارد.")
    d["status"], d["recipientId"], d["updatedAt"] = "RESERVED", rid, now_ms()
    store.put("donations", d)
    add_notification(store, rid, "DONATION", "درخواست شما پذیرفته شد",
                     "«%s» برای شما کنار گذاشته شد. با اهداکننده پیام بدهید." % d["title"], related_id=d["id"])


def a_donationMarkGiven(store, uid, args):
    _require(uid)
    d = _donation_owner(store, uid, args.get("id"))
    d["status"], d["updatedAt"] = "GIVEN", now_ms()
    store.put("donations", d)


def a_donationReopen(store, uid, args):
    _require(uid)
    d = _donation_owner(store, uid, args.get("id"))
    d["status"], d["recipientId"], d["updatedAt"] = "AVAILABLE", None, now_ms()
    store.put("donations", d)


def a_donationRemove(store, uid, args):
    _require(uid)
    d = _donation_owner(store, uid, args.get("id"))
    d["status"], d["updatedAt"] = "REMOVED", now_ms()
    store.put("donations", d)
    for fid in d.get("imageIds") or []:  # files belong to the donation's OWNER, even if an admin removes it
        _drop_file(store, d["ownerId"], fid)
    d["imageIds"] = []
    store.put("donations", d)




CORE_FEATURES = {"home", "profile"}
KNOWN_FEATURES = {"home", "qa", "groups", "messages", "guides", "directory", "donations", "childfile", "booking", "tools", "courses", "places", "profile"}


def disabled_features(store):
    doc = store.get("config", "features") or {}
    return [f for f in doc.get("disabled", []) if f not in CORE_FEATURES]


def set_feature(store, fid, enabled):
    if fid in CORE_FEATURES:
        raise ApiError("این بخش پایه است و خاموش نمی‌شود.")
    doc = store.get("config", "features") or {"id": "features", "disabled": []}
    off = set(doc.get("disabled", []))
    (off.discard if enabled else off.add)(fid)
    doc["disabled"] = sorted(off)
    store.put("config", doc)


def a_setFeatureEnabled(store, uid, args):
    _require(uid)
    if not is_admin(store, uid):
        raise ApiError("دسترسی مدیر لازم است.")
    fid = _s(args.get("id"))
    set_feature(store, fid, bool(args.get("enabled")))
    _log_admin(store, uid, "SET_FEATURE", "FEATURE", fid, "enabled=%s" % bool(args.get("enabled")))


# ---- Generic records: child file, tools, booking, courses, places/events ----

REC_KINDS = {
    "diary":    {"mod": "childfile", "scope": "own",   "req": "note",  "fields": ["childId", "childName", "date", "mood", "note"], "child": "optional"},
    "med":      {"mod": "childfile", "scope": "own",   "req": "name",  "fields": ["childId", "childName", "name", "dose", "times", "notes"], "child": "optional"},
    "schedule": {"mod": "tools",     "scope": "own",   "req": "title", "fields": ["title", "steps", "cover", "stepImages"], "files": ["cover"], "lists": ["stepImages"]},
    "story":    {"mod": "tools",     "scope": "own",   "req": "title", "fields": ["title", "pages", "cover", "pageImages"], "files": ["cover"], "lists": ["pageImages"]},
    "aac":      {"mod": "tools",     "scope": "own",   "req": "text",  "fields": ["text", "emoji", "image", "audio", "builtin", "childId"], "files": ["image", "audio"]},
    "log":      {"mod": "childfile", "scope": "own",   "req": "type",  "fields": ["childId", "date", "time", "type", "intensity", "durationMin", "trigger", "notes"],
                 "child": "required", "cap": 3000, "limit": 600,
                 "enums": {"type": ("meltdown", "sleep", "seizure", "meal", "trigger", "other")},
                 "ints": {"intensity": (1, 5), "durationMin": (0, 1440)}},
    "reward":   {"mod": "childfile", "scope": "own",   "req": "title", "fields": ["childId", "title", "emoji", "goal", "stars"],
                 "child": "required", "ints": {"goal": (1, 50), "stars": (0, 50)}},
    "firstthen": {"mod": "childfile", "scope": "own",  "req": "first", "fields": ["childId", "first", "then", "firstEmoji", "thenEmoji", "firstImage", "thenImage"],
                  "child": "required", "files": ["firstImage", "thenImage"]},
    "emergency": {"mod": "childfile", "scope": "own",  "req": "name",
                  "fields": ["childId", "name", "diagnosis", "sensory", "triggers", "calming", "medical",
                             "contact1Name", "contact1Phone", "contact2Name", "contact2Phone", "photo"],
                  "child": "required", "files": ["photo"]},
    "slot":     {"mod": "booking",   "scope": "pro",   "req": "when",  "fields": ["when", "duration", "place", "note"]},
    "course":   {"mod": "courses",   "scope": "admin", "req": "title", "fields": ["title", "description", "level", "lessons"]},
    "place":    {"mod": "places",    "scope": "admin", "req": "name",  "fields": ["name", "type", "city", "address", "phone", "lat", "lng", "note"]},
    "event":    {"mod": "places",    "scope": "admin", "req": "title", "fields": ["title", "date", "city", "address", "description", "link"]},
}
REC_LONG = {"note", "notes", "steps", "pages", "lessons", "description", "diagnosis", "sensory",
            "triggers", "calming", "medical"}
REC_PER_USER = 300
ROLES = ["FAMILY", "PROFESSIONAL", "EDUCATOR", "SOCIAL_WORKER", "COMMUNITY"]
HEX32 = re.compile(r"[0-9a-f]{32}")
ZERO_ID = "0" * 32  # list-field placeholder: "no image for this step/page"
MAX_LIST_LINES = 60


def module_roles(store):
    return (store.get("config", "perms") or {}).get("roles", {})


def blocked_modules(store, uid):
    """Modules this caller must not use: switched off for everyone, or restricted to other roles."""
    out = set(disabled_features(store))
    if is_admin(store, uid):
        return out
    role = (store.get("users", uid) or {}).get("accountType") or "FAMILY"
    for mod, roles in module_roles(store).items():
        if roles and role not in roles:
            out.add(mod)
    return out


def module_of(name, args):
    if name.startswith("donation"):
        return "donations"
    if name in ("recList", "recSave", "recDelete"):
        k = REC_KINDS.get(_s(args.get("kind")))
        return k["mod"] if k else None
    if name in ("slotBook", "slotCancel"):
        return "booking"
    if name == "shareCreate":
        return "childfile"
    return None


def _rec_kind(args):
    kind = _s(args.get("kind"))
    if kind not in REC_KINDS:
        raise ApiError("نوع داده نامعتبر است.")
    return kind, REC_KINDS[kind]


def _slot_epoch(d):
    try:
        return int(d.get("epochMs") or 0)
    except (TypeError, ValueError):
        return 0


def _parse_epoch(v):
    if v is None or v == "":
        return 0
    if isinstance(v, bool):
        raise ApiError("زمان نوبت نامعتبر است.")
    if isinstance(v, str):
        v = v.strip()
        if not v.isdigit():
            raise ApiError("زمان نوبت نامعتبر است.")
    try:
        n = int(float(v))
    except (TypeError, ValueError):
        raise ApiError("زمان نوبت نامعتبر است.")
    if n < 0 or n > 4102444800000:  # up to year 2100
        raise ApiError("زمان نوبت نامعتبر است.")
    return n


def _rec_view(store, d, uid, kind):
    out = {k: d.get(k, "") for k in REC_KINDS[kind]["fields"]}
    out.update(id=d["id"], ownerId=d.get("ownerId"), ownerName=d.get("ownerName", ""),
               createdAt=d.get("createdAt", 0), mine=d.get("ownerId") == uid)
    if kind == "slot":
        out["epochMs"] = _slot_epoch(d)
        out["status"] = d.get("status", "OPEN")
        out["iBooked"] = d.get("bookedBy") == uid
        if d.get("ownerId") == uid:
            out["bookedByName"] = d.get("bookedByName", "")
            out["bookedBy"] = d.get("bookedBy", "")
    return out


def a_recList(store, uid, args):
    _require(uid)
    kind, spec = _rec_kind(args)
    mine, booked = bool(args.get("mine")), bool(args.get("booked"))
    child = _s(args.get("childId")) if isinstance(args.get("childId"), str) else ""
    items = []
    hidden_owners = _blocked_ids(store, uid) if kind == "slot" else set()
    now = now_ms()
    for d in store.all("r_" + kind):
        if d.get("status") == "REMOVED":
            continue
        own = d.get("ownerId") == uid
        if spec["scope"] == "own" and not own:
            continue
        if child and "childId" in spec["fields"] and d.get("childId") != child:
            continue
        if kind == "slot":
            if booked and d.get("bookedBy") != uid:
                continue
            if not booked and mine != own:
                continue
            if not booked and not mine and d.get("status") == "BOOKED":
                continue
            if not booked and not own:  # open lists: no past slots, no blocked owners
                ep = _slot_epoch(d)
                if (ep and ep < now) or d.get("ownerId") in hidden_owners:
                    continue
        elif mine and not own:
            continue
        items.append(d)
    if kind == "slot":
        items.sort(key=lambda d: (_slot_epoch(d) or float("inf"), -d.get("createdAt", 0)))
    else:
        items.sort(key=lambda d: d.get("createdAt", 0), reverse=True)
    return {"items": [_rec_view(store, d, uid, kind) for d in items[:spec.get("limit", 200)]]}


def _file_doc(store, fid):
    return store.get("files", fid) if isinstance(fid, str) and HEX32.fullmatch(fid) else None


def _drop_file(store, uid, fid):
    """Delete an uploaded file (doc + bytes) if `uid` owns it."""
    doc = _file_doc(store, fid)
    if not doc or doc.get("ownerId") != uid:
        return
    _unlink_file(doc)
    store.delete("files", fid)


def _check_file_ownership(store, uid, ids, image_only=False, admin=False):
    """Every referenced file must exist and belong to the caller (admins are exempt)."""
    for fid in ids:
        doc = _file_doc(store, fid)
        if not doc:
            raise ApiError("فایل پیوست‌شده پیدا نشد.")
        if not admin and doc.get("ownerId") != uid:
            raise ApiError("فایل پیوست‌شده متعلق به شما نیست.")
        if image_only and not str(doc.get("mime", "")).startswith("image/"):
            raise ApiError("فقط تصویر می‌توان پیوست کرد.")


def _split_ids(v):
    return [x.strip() for x in str(v or "").split("\n")]


def _rec_file_ids(spec, d):
    ids = [d.get(f) for f in spec.get("files", []) if d.get(f)]
    for f in spec.get("lists", []):
        ids += [x for x in _split_ids(d.get(f)) if x and x != ZERO_ID]
    return ids


def _str_field(v, f, is_list):
    if v is None:
        return ""
    if isinstance(v, bool):
        raise ApiError("مقدار یکی از فیلدها نامعتبر است.")
    if isinstance(v, (int, float)):
        return str(int(v)) if float(v).is_integer() else str(v)
    if isinstance(v, list) and is_list:
        return "\n".join(str(x).strip() for x in v)
    if isinstance(v, str):
        return v.strip() if not is_list else "\n".join(x.strip() for x in v.split("\n")).rstrip("\n")
    raise ApiError("مقدار یکی از فیلدها نامعتبر است.")


def _clean_rec(store, owner_uid, spec, data):
    clean = {}
    lists = spec.get("lists", [])
    for f in spec["fields"]:
        v = _str_field(data.get(f), f, f in lists)
        if len(v) > (5000 if f in REC_LONG else (4000 if f in lists else 200)):
            raise ApiError("متن یکی از فیلدها بیش از حد طولانی است.")
        if f in spec.get("files", []) and v and not HEX32.fullmatch(v):
            raise ApiError("شناسهٔ فایل نامعتبر است.")
        if f in lists and v:
            lines = v.split("\n")
            if len(lines) > MAX_LIST_LINES or any(x and not HEX32.fullmatch(x) for x in lines):
                raise ApiError("فهرست شناسهٔ فایل‌ها نامعتبر است.")
        if v and f in spec.get("enums", {}) and v not in spec["enums"][f]:
            raise ApiError("مقدار «%s» نامعتبر است." % f)
        if v and f in spec.get("ints", {}):
            lo, hi = spec["ints"][f]
            if not v.isdigit() or not lo <= int(v) <= hi:
                raise ApiError("مقدار «%s» باید عددی بین %d و %d باشد." % (f, lo, hi))
            v = str(int(v))
        clean[f] = v
    mode = spec.get("child")
    if mode and (clean.get("childId") or mode == "required"):
        cid = clean.get("childId")
        if not any(c.get("id") == cid and c.get("familyUserId") == owner_uid for c in store.all("children")):
            raise ApiError("پروفایل کودک پیدا نشد.")
    if not clean[spec["req"]]:
        raise ApiError("فیلد اصلی را پر کنید.")
    return clean


def _delete_rec(store, kind, d):
    for fid in _rec_file_ids(REC_KINDS[kind], d):
        _drop_file(store, d.get("ownerId"), fid)
    store.delete("r_" + kind, d["id"])


def a_recSave(store, uid, args):
    _require(uid)
    kind, spec = _rec_kind(args)
    me = store.get("users", uid) or {}
    if me.get("status") in ("BLOCKED", "LIMITED"):
        raise ApiError("حساب شما اجازهٔ این کار را ندارد.")
    admin = is_admin(store, uid)
    if spec["scope"] == "admin" and not admin:
        raise ApiError("فقط مدیر می‌تواند این مورد را ثبت کند.")
    if spec["scope"] == "pro" and not (admin or me.get("accountType") in PRO_TYPES):
        raise ApiError("فقط متخصصان می‌توانند نوبت ثبت کنند.")
    data = args.get("data") or {}
    if not isinstance(data, dict):
        raise ApiError("داده نامعتبر است.")
    rid = _s(args.get("id"))
    d = store.get("r_" + kind, rid) if rid else None
    if d and d.get("ownerId") != uid and not admin:
        raise ApiError("اجازهٔ ویرایش ندارید.")
    owner = d["ownerId"] if d else uid
    if kind == "slot" and d and d.get("status") == "BOOKED":
        raise ApiError("نوبت رزروشده قابل ویرایش نیست؛ ابتدا آن را لغو کنید.")
    clean = _clean_rec(store, owner, spec, data)
    if kind == "slot":
        epoch = _parse_epoch(data.get("epochMs")) if "epochMs" in data else (_slot_epoch(d) if d else 0)
        if epoch and epoch < now_ms() and (not d or epoch != _slot_epoch(d)):
            raise ApiError("زمان نوبت باید در آینده باشد.")
    new_files = _rec_file_ids(spec, clean)
    if spec["scope"] == "own" or new_files:
        _check_file_ownership(store, owner, new_files, admin=admin and owner != uid)
    if d:
        gone = set(_rec_file_ids(spec, d)) - set(new_files)
        d.update(clean)
        if kind == "slot":
            d["epochMs"] = epoch
        d["updatedAt"] = now_ms()
        for fid in gone:  # files always belong to the record's OWNER, also when an admin edits
            _drop_file(store, owner, fid)
    else:
        if sum(1 for x in store.all("r_" + kind) if x.get("ownerId") == uid) >= spec.get("cap", REC_PER_USER):
            raise ApiError("به سقف تعداد رسیدید.")
        d = dict(clean, id=new_id("r"), ownerId=uid, ownerName=me.get("displayName", ""),
                 status="OPEN" if kind == "slot" else "ACTIVE", createdAt=now_ms(), updatedAt=now_ms())
        if kind == "slot":
            d["epochMs"] = epoch
    store.put("r_" + kind, d)
    return {"id": d["id"]}


def a_recDelete(store, uid, args):
    _require(uid)
    kind, spec = _rec_kind(args)
    d = store.get("r_" + kind, _s(args.get("id")))
    if not d:
        raise ApiError("یافت نشد.")
    if d.get("ownerId") != uid and not is_admin(store, uid):
        raise ApiError("اجازهٔ حذف ندارید.")
    if kind == "slot" and d.get("bookedBy"):
        add_notification(store, d["bookedBy"], "BOOKING", "نوبت لغو شد",
                         "متخصص نوبت «%s» را حذف کرد." % d.get("when", ""), d["id"])
    _delete_rec(store, kind, d)


def _slot(store, args):
    d = store.get("r_slot", _s(args.get("id")))
    if not d or d.get("status") == "REMOVED":
        raise ApiError("این نوبت دیگر وجود ندارد.")
    return d


def a_slotBook(store, uid, args):
    _require(uid)
    d = _slot(store, args)
    me = store.get("users", uid) or {}
    if d.get("ownerId") == uid:
        raise ApiError("نمی‌توانید نوبت خودتان را رزرو کنید.")
    if me.get("status") in ("BLOCKED", "LIMITED"):
        raise ApiError("حساب شما اجازهٔ رزرو ندارد.")
    if d.get("status") != "OPEN":
        raise ApiError("این نوبت قبلاً رزرو شده است.")
    owner = store.get("users", d["ownerId"]) or {}
    if owner.get("accountType") not in PRO_TYPES or owner.get("status") in ("BLOCKED", "LIMITED", "DELETED"):
        raise ApiError("این متخصص در حال حاضر نوبت نمی‌پذیرد.")
    if _blocked_between(store, uid, d["ownerId"]):
        raise ApiError("امکان رزرو با این متخصص وجود ندارد.")
    ep, now = _slot_epoch(d), now_ms()
    if ep and ep < now:
        raise ApiError("زمان این نوبت گذشته است.")
    active = sum(1 for x in store.all("r_slot") if x.get("bookedBy") == uid and x.get("ownerId") == d["ownerId"]
                 and x.get("status") == "BOOKED" and (not _slot_epoch(x) or _slot_epoch(x) >= now))
    if active >= 2:
        raise ApiError("حداکثر ۲ نوبت فعال نزد هر متخصص می‌توانید داشته باشید.")
    d.update(status="BOOKED", bookedBy=uid, bookedByName=me.get("displayName", ""), updatedAt=now_ms())
    store.put("r_slot", d)
    add_notification(store, d["ownerId"], "BOOKING", "نوبت جدید رزرو شد",
                     "%s نوبت «%s» را رزرو کرد." % (me.get("displayName", "کاربر"), d.get("when", "")), d["id"])


def a_slotCancel(store, uid, args):
    _require(uid)
    d = _slot(store, args)
    if uid not in (d.get("bookedBy"), d.get("ownerId")):
        raise ApiError("اجازهٔ لغو ندارید.")
    if d.get("status") != "BOOKED":
        return
    other = d["ownerId"] if uid == d.get("bookedBy") else d.get("bookedBy")
    d.update(status="OPEN", bookedBy="", bookedByName="", updatedAt=now_ms())
    store.put("r_slot", d)
    add_notification(store, other, "BOOKING", "رزرو لغو شد",
                     "رزرو نوبت «%s» لغو شد." % d.get("when", ""), d["id"])


def a_search(store, uid, args):
    _require(uid)
    q = _s(args.get("q")).lower()
    if len(q) < 2:
        raise ApiError("حداقل دو نویسه وارد کنید.")
    off = blocked_modules(store, uid)
    hidden = _blocked_ids(store, uid)
    out = []

    def hit(*parts):
        return any(q in _s(x).lower() for x in parts)

    def add(t, d, title, sub=""):
        if len(out) < 60:
            out.append({"type": t, "id": d["id"], "title": title, "sub": sub})

    if "qa" not in off:
        for d in store.all("questions"):
            if d.get("status") == "PUBLISHED" and hit(d.get("title"), d.get("body")):
                add("question", d, d.get("title", ""), "پرسش")
    if "groups" not in off:
        for d in store.all("groups"):
            if d.get("visibility") == "PUBLIC" and d.get("status") != "REMOVED" and hit(d.get("title"), d.get("description")):
                add("group", d, d.get("title", ""), "گروه")
    if "guides" not in off:
        for d in store.all("guides"):
            if d.get("status") == "PUBLISHED" and hit(d.get("title")):
                add("guide", d, d.get("title", ""), "راهنما")
    if "directory" not in off:
        for d in store.all("users"):
            if d.get("accountType") in PRO_TYPES and d.get("status") not in ("BLOCKED",) and d["id"] not in hidden and hit(d.get("displayName"), d.get("city"), d.get("bio")):
                add("user", d, d.get("displayName", ""), "متخصص" + (" · " + d["city"] if d.get("city") else ""))
    if "donations" not in off:
        for d in store.all("donations"):
            if d.get("status") in ("AVAILABLE", "RESERVED") and hit(d.get("title"), d.get("description"), d.get("city")):
                add("donation", d, d.get("title", ""), "دست‌به‌دست")
    for kind, label, fields in (("course", "دوره آموزشی", ("title", "description")),
                                ("place", "مرکز", ("name", "city", "type")),
                                ("event", "رویداد", ("title", "city", "description"))):
        if REC_KINDS[kind]["mod"] in off:
            continue
        for d in store.all("r_" + kind):
            if hit(*[d.get(f) for f in fields]):
                add(kind, d, d.get(REC_KINDS[kind]["req"], ""), label)
    return {"items": out}


def a_setModuleRoles(store, uid, args):
    _require(uid)
    if not is_admin(store, uid):
        raise ApiError("دسترسی مدیر لازم است.")
    set_module_roles(store, _s(args.get("id")), args.get("roles") or [])
    _log_admin(store, uid, "SET_MODULE_ROLES", "FEATURE", _s(args.get("id")), ",".join(args.get("roles") or []))


def set_module_roles(store, fid, roles):
    if fid in CORE_FEATURES or fid not in KNOWN_FEATURES:
        raise ApiError("ماژول نامعتبر یا پایه است.")
    roles = [r for r in roles if r in ROLES]
    doc = store.get("config", "perms") or {"id": "perms", "roles": {}}
    if roles:
        doc["roles"][fid] = roles
    else:
        doc["roles"].pop(fid, None)
    store.put("config", doc)


# ---- Password reset by emailed code -----------------------------------------------------

RESET_TTL_MS = 30 * 60 * 1000
RESET_MAX_TRIES = 5
EMAIL_RE = re.compile(r"^[^@\s<>\"',;:()\[\]\\]+@[^@\s<>\"',;:()\[\]\\]+\.[^@\s<>\"',;:()\[\]\\]+$")
_DIGITS = str.maketrans("۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩", "01234567890123456789")
RLM = "‏"


def _reset_key(email):
    return hashlib.sha256(email.strip().lower().encode("utf-8")).hexdigest()[:32]


def _code_hash(email, code):
    return hmac.new(JWT_SECRET, ("reset|%s|%s" % (email.strip().lower(), code)).encode("utf-8"),
                    hashlib.sha256).hexdigest()


def _issue_reset(store, email, uid):
    mail = email.strip().lower()
    code = "%08d" % secrets.randbelow(10 ** 8)
    store.put("resets", {"id": _reset_key(mail), "email": mail, "uid": uid,
                         "codeHash": _code_hash(mail, code), "expiresAt": now_ms() + RESET_TTL_MS,
                         "tries": 0, "createdAt": now_ms()})
    return code


def smtp_configured():
    return bool(SMTP_HOST and SMTP_FROM)


def send_mail(to, subject, body):
    """Send a UTF-8 plain-text mail through SMTP_* (SSL on 465, STARTTLS otherwise). Raises on failure."""
    import smtplib
    import ssl
    from email.message import EmailMessage
    from email.utils import formatdate, make_msgid, parseaddr
    if not smtp_configured():
        raise RuntimeError("SMTP not configured")
    if not EMAIL_RE.match(to):
        raise ValueError("bad recipient")
    msg = EmailMessage()
    msg["Subject"] = subject
    msg["From"] = SMTP_FROM
    msg["To"] = to
    msg["Date"] = formatdate(localtime=True)
    dom = parseaddr(SMTP_FROM)[1].rpartition("@")[2] or "localhost"
    msg["Message-ID"] = make_msgid(domain=dom)
    msg.set_content(body, charset="utf-8")
    ctx = ssl.create_default_context()
    if SMTP_PORT == 465:
        s = smtplib.SMTP_SSL(SMTP_HOST, SMTP_PORT, timeout=20, context=ctx)
    else:
        s = smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=20)
    try:
        s.ehlo()
        if SMTP_PORT != 465 and (SMTP_PORT != 25 or s.has_extn("starttls")):
            s.starttls(context=ctx)
            s.ehlo()
        if SMTP_USER:
            s.login(SMTP_USER, SMTP_PASS)
        s.send_message(msg, from_addr=parseaddr(SMTP_FROM)[1] or SMTP_FROM, to_addrs=[to])
    finally:
        try:
            s.quit()
        except Exception:
            pass


def _reset_mail_text(code):
    lines = [
        "سلام،",
        "برای بازیابی رمز عبور حساب «همراهِ طیف» این کد را در برنامه وارد کنید:",
        "",
        "کد بازیابی: %s" % code,
        "",
        "این کد %d دقیقه اعتبار دارد و فقط یک بار قابل استفاده است." % (RESET_TTL_MS // 60000),
        "اگر شما درخواست بازیابی نکرده‌اید، این ایمیل را نادیده بگیرید؛ رمز شما تغییری نمی‌کند.",
        "",
        "همراهِ طیف",
    ]
    return "\n".join((RLM + ln) if ln else "" for ln in lines)


def _mail_reset_worker(mail, code):
    try:
        send_mail(mail, "کد بازیابی رمز عبور — همراهِ طیف", _reset_mail_text(code))
    except Exception as e:
        log("reset mail failed: %s: %s" % (type(e).__name__, e))


def do_forgot(args, ip):
    mail = _s(args.get("email")).lower()
    if not EMAIL_RE.match(mail) or len(mail) > 120:
        return {"ok": True}
    if not RL.allow("forgot-ip:" + ip, 10, 3600):
        raise ApiError(TOO_MANY)
    if not RL.allow("forgot-mail:" + mail, 3, 3600):
        return {"ok": True}  # silently capped: never reveal anything about the account
    uid = cred_uid(mail)
    if uid:
        with MUT_LOCK:
            store = Store()
            u = store.get("users", uid)
            if u and u.get("status") not in ("BLOCKED", "DELETED"):
                if smtp_configured():
                    code = _issue_reset(store, mail, uid)
                    threading.Thread(target=_mail_reset_worker, args=(mail, code), daemon=True).start()
                else:
                    log("SMTP not configured - password-reset code NOT sent (admin can use adminIssueReset)")
    return {"ok": True}


def do_reset(args, ip):
    mail = _s(args.get("email")).lower()
    code = _s(args.get("code")).translate(_DIGITS)
    pw = args.get("newPassword")
    if not RL.allow("reset-ip:" + ip, 20, 3600):
        raise ApiError(TOO_MANY)
    if not isinstance(pw, str) or len(pw) < 6:
        raise ApiError("رمز عبور باید حداقل ۶ نویسه باشد.")
    if len(pw) > MAX_PASSWORD:
        raise ApiError("رمز عبور بیش از حد طولانی است.")
    bad = ApiError("کد نامعتبر است یا منقضی شده.")
    if not EMAIL_RE.match(mail) or not code.isdigit() or len(code) != 8:
        raise bad
    with MUT_LOCK:
        store = Store()
        doc = store.get("resets", _reset_key(mail))
        if not doc:
            raise bad
        if doc.get("expiresAt", 0) < now_ms():
            store.delete("resets", doc["id"])
            raise bad
        if not hmac.compare_digest(doc.get("codeHash", ""), _code_hash(mail, code)):
            doc["tries"] = doc.get("tries", 0) + 1
            if doc["tries"] >= RESET_MAX_TRIES:
                store.delete("resets", doc["id"])
            else:
                store.put("resets", doc)
            raise bad
        uid = cred_uid(mail)
        u = store.get("users", uid) if uid else None
        if not u or uid != doc.get("uid") or u.get("status") in ("BLOCKED", "DELETED"):
            store.delete("resets", doc["id"])
            raise bad
        set_cred(mail, pw, uid)
        u["tokensValidAfter"] = int(time.time())
        u["updatedAt"] = now_ms()
        store.put("users", u)
        store.delete("resets", doc["id"])
    return {"ok": True}


def _need_admin(store, uid):
    _require(uid)
    if not is_admin(store, uid):
        raise ApiError("دسترسی مدیر لازم است.")


def a_adminIssueReset(store, uid, args):
    """Fallback when email is unavailable: the admin gets a one-time code to relay to the user."""
    _need_admin(store, uid)
    target = store.get("users", _s(args.get("userId"))) if args.get("userId") else None
    if not target:
        mail = _s(args.get("email")).lower()
        target = next((x for x in store.all("users") if (x.get("email") or "").lower() == mail), None) if mail else None
    if not target or not target.get("email"):
        raise ApiError("کاربر پیدا نشد.")
    if target.get("isAdmin") and target["id"] != uid:
        raise ApiError("بازنشانی رمز مدیر دیگر از این مسیر ممکن نیست.")
    code = _issue_reset(store, target["email"], target["id"])
    _log_admin(store, uid, "ISSUE_RESET", "USER", target["id"], "صدور کد بازیابی رمز")
    add_notification(store, target["id"], "SAFETY_NOTICE", "کد بازیابی رمز صادر شد",
                     "مدیریت برای حساب شما کد بازیابی رمز صادر کرد. اگر این درخواست از شما نبوده، به پشتیبانی اطلاع دهید.",
                     force=True)
    return {"code": code, "email": target["email"], "expiresInMinutes": RESET_TTL_MS // 60000}


def a_logoutAllDevices(store, uid, args):
    _require(uid)
    u = store.get("users", uid)
    if not u:
        raise ApiError("حساب یافت نشد.")
    u["tokensValidAfter"] = int(time.time()) + 1  # the caller's own next login gets a later iat
    store.put("users", u)


# ---- Admin console ------------------------------------------------------------------------

def a_adminStats(store, uid, args):
    _need_admin(store, uid)
    now = now_ms()
    day = 86400000
    users = store.all("users")

    def count_by(items, key):
        out = {}
        for it in items:
            k = it.get(key) or "-"
            out[k] = out.get(k, 0) + 1
        return out

    files = store.all("files")
    return {
        "users": {
            "total": len(users),
            "byType": count_by(users, "accountType"),
            "byStatus": count_by(users, "status"),
            "new7d": sum(1 for u in users if u.get("joinedAt", 0) >= now - 7 * day),
            "new30d": sum(1 for u in users if u.get("joinedAt", 0) >= now - 30 * day),
            "active7d": sum(1 for u in users if u.get("lastSeenAt", 0) >= now - 7 * day),
            "active30d": sum(1 for u in users if u.get("lastSeenAt", 0) >= now - 30 * day),
        },
        "content": {
            "questions": sum(1 for q in store.all("questions") if q.get("status") != "REMOVED"),
            "answers": sum(1 for a in store.all("answers") if a.get("status") != "REMOVED"),
            "groups": len(store.all("groups")),
            "posts": sum(1 for p in store.all("posts") if p.get("status") == "PUBLISHED"),
            "guides": sum(1 for g in store.all("guides") if g.get("status") == "PUBLISHED"),
            "children": len(store.all("children")),
            "conversations": len(store.all("conversations")),
            "donations": count_by(store.all("donations"), "status"),
        },
        "moderation": {
            "openReports": sum(1 for r in store.all("reports") if r.get("status") in ("OPEN", "UNDER_REVIEW")),
            "pendingVerifications": sum(1 for v in store.all("verifications") if v.get("status") == "PENDING"),
        },
        "records": {k: sum(1 for d in store.all("r_" + k) if d.get("status") != "REMOVED") for k in REC_KINDS},
        "files": {"count": len(files), "bytes": sum(int(f.get("size", 0) or 0) for f in files)},
        "moduleRoles": module_roles(store),
        "disabledFeatures": disabled_features(store),
        "smtpConfigured": smtp_configured(),
        "generatedAt": now,
    }


def a_adminListUsers(store, uid, args):
    _need_admin(store, uid)
    q = _s(args.get("q")).lower()[:80]
    status, atype = _s(args.get("status")), _s(args.get("accountType"))
    try:
        offset = max(0, int(args.get("offset") or 0))
        limit = min(100, max(1, int(args.get("limit") or 50)))
    except (TypeError, ValueError):
        raise ApiError("پارامتر نامعتبر است.")
    rows = []
    for u in store.all("users"):
        if status and u.get("status") != status:
            continue
        if atype and u.get("accountType") != atype:
            continue
        if q and q not in (u.get("displayName") or "").lower() and q not in (u.get("email") or "").lower():
            continue
        rows.append(u)
    rows.sort(key=lambda u: u.get("joinedAt", 0), reverse=True)
    keep = ("id", "displayName", "email", "accountType", "status", "isAdmin", "city", "joinedAt", "lastSeenAt")
    return {"total": len(rows), "items": [{k: u.get(k) for k in keep} for u in rows[offset:offset + limit]]}


def a_adminBroadcast(store, uid, args):
    _need_admin(store, uid)
    title, body = _s(args.get("title")), _s(args.get("body"))
    audience = _s(args.get("audience")) or "ALL"
    if not title or not body:
        raise ApiError("عنوان و متن اعلان را وارد کنید.")
    if len(title) > 100 or len(body) > 500:
        raise ApiError("عنوان حداکثر ۱۰۰ و متن حداکثر ۵۰۰ نویسه باشد.")
    if audience != "ALL" and audience not in ROLES:
        raise ApiError("مخاطب نامعتبر است.")
    if not RL.allow("broadcast:" + uid, 5, 3600):
        raise ApiError(TOO_MANY)
    tokens = {}
    for t in store.all("pushTokens"):
        tokens.setdefault(t.get("userId"), []).append(t["id"])
    sent, skipped, pairs = 0, 0, []
    for u in list(store.all("users")):  # safety notices ignore the user's opt-out and are never capped
        if audience != "ALL" and u.get("accountType") != audience:
            continue
        if u.get("status") in ("BLOCKED", "DELETED"):
            skipped += 1
            continue
        nid = new_id("n")
        store.put("notifications", {"id": nid, "userId": u["id"], "type": "SAFETY_NOTICE", "title": title,
                                    "body": body, "relatedId": None, "read": False, "createdAt": now_ms()})
        pairs += [(tk, nid) for tk in tokens.get(u["id"], [])]
        sent += 1
    _fcm_fanout(pairs, title, body, "SAFETY_NOTICE", "")
    _log_admin(store, uid, "BROADCAST", "USER", audience, title)
    return {"sent": sent, "devices": len(pairs), "skipped": skipped}


# ---- Sharing a child's file with another person (teacher, therapist, spouse) ----------------

SHARE_KINDS = [k for k, s in REC_KINDS.items() if s.get("child")]
SHARE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
MAX_SHARES = 20


def _norm_code(c):
    return re.sub(r"[^A-Z0-9]", "", str(c or "").upper())


def _share_hash(code):
    return hmac.new(JWT_SECRET, b"share|" + code.encode("ascii", "ignore"), hashlib.sha256).hexdigest()


def _share_live(sh):
    return bool(sh) and not sh.get("revoked") and sh.get("expiresAt", 0) > now_ms()


def _child_name(store, cid):
    c = next((c for c in store.all("children") if c.get("id") == cid), None)
    return (c or {}).get("nickname") or "کودک"


def a_shareCreate(store, uid, args):
    _require(uid)
    cid = _s(args.get("childId"))
    if not any(c.get("id") == cid and c.get("familyUserId") == uid for c in store.all("children")):
        raise ApiError("پروفایل کودک پیدا نشد.")
    kinds = args.get("kinds")
    if not isinstance(kinds, list) or not kinds or any(k not in SHARE_KINDS for k in kinds):
        raise ApiError("بخش‌های قابل اشتراک نامعتبر است.")
    try:
        days = int(args.get("days") or 7)
    except (TypeError, ValueError):
        raise ApiError("مدت اعتبار نامعتبر است.")
    if not 1 <= days <= 90:
        raise ApiError("مدت اعتبار باید بین ۱ تا ۹۰ روز باشد.")
    mine = [s for s in store.all("shares") if s.get("ownerId") == uid]
    if sum(1 for s in mine if _share_live(s)) >= MAX_SHARES:
        raise ApiError("به سقف تعداد اشتراک‌های فعال رسیده‌اید.")
    code = "".join(secrets.choice(SHARE_ALPHABET) for _ in range(8))
    sh = {"id": new_id("sh"), "ownerId": uid, "childId": cid, "kinds": sorted(set(kinds)),
          "label": _s(args.get("label"))[:60], "codeHash": _share_hash(code),
          "expiresAt": now_ms() + days * 86400000, "createdAt": now_ms(),
          "redeemedBy": "", "redeemedByName": "", "redeemedAt": 0, "revoked": False}
    store.put("shares", sh)
    return {"id": sh["id"], "code": "%s-%s" % (code[:4], code[4:]), "expiresAt": sh["expiresAt"]}


def a_shareList(store, uid, args):
    _require(uid)
    owned, received = [], []
    for s in sorted(store.all("shares"), key=lambda s: s.get("createdAt", 0), reverse=True):
        if s.get("ownerId") == uid:
            st = "REVOKED" if s.get("revoked") else "EXPIRED" if s.get("expiresAt", 0) <= now_ms() \
                else "REDEEMED" if s.get("redeemedBy") else "WAITING"
            owned.append({"id": s["id"], "childId": s["childId"], "childName": _child_name(store, s["childId"]),
                          "kinds": s["kinds"], "label": s.get("label", ""), "expiresAt": s["expiresAt"],
                          "createdAt": s["createdAt"], "status": st,
                          "redeemedByName": s.get("redeemedByName", "")})
        elif s.get("redeemedBy") == uid and _share_live(s):
            owner = store.get("users", s["ownerId"]) or {}
            received.append({"id": s["id"], "childName": _child_name(store, s["childId"]),
                             "ownerName": owner.get("displayName", ""), "kinds": s["kinds"],
                             "label": s.get("label", ""), "expiresAt": s["expiresAt"]})
    return {"owned": owned, "received": received}


def a_shareRevoke(store, uid, args):
    _require(uid)
    sh = store.get("shares", _s(args.get("id")))
    if not sh or uid not in (sh.get("ownerId"), sh.get("redeemedBy")):
        raise ApiError("اشتراک پیدا نشد.")
    if uid == sh.get("ownerId"):
        store.delete("shares", sh["id"])  # the code and any access derived from it die immediately
    else:  # a recipient only gives up their own access; the owner keeps the record (status REVOKED)
        sh.update(revoked=True, revokedBy=uid)
        store.put("shares", sh)


def a_shareRedeem(store, uid, args):
    _require(uid)
    code = _norm_code(args.get("code"))
    if RL.blocked("share-redeem:" + uid, 10, 900):
        raise ApiError(TOO_MANY)
    h = _share_hash(code)
    sh = next((s for s in store.all("shares") if len(code) == 8 and hmac.compare_digest(s.get("codeHash", ""), h)), None)
    if not sh or not _share_live(sh) or sh.get("ownerId") == uid or \
            (sh.get("redeemedBy") and sh["redeemedBy"] != uid):
        RL.hit("share-redeem:" + uid, 900)
        raise ApiError("کد نامعتبر است یا منقضی شده.")
    me = store.get("users", uid) or {}
    sh.update(redeemedBy=uid, redeemedByName=me.get("displayName", ""), redeemedAt=sh.get("redeemedAt") or now_ms())
    store.put("shares", sh)
    owner = store.get("users", sh["ownerId"]) or {}
    return {"id": sh["id"], "childName": _child_name(store, sh["childId"]),
            "ownerName": owner.get("displayName", ""), "kinds": sh["kinds"], "expiresAt": sh["expiresAt"]}


def _iso_day(d):
    v = str(d.get("date") or "")
    if re.fullmatch(r"\d{4}-\d{2}-\d{2}", v):
        return v
    return time.strftime("%Y-%m-%d", time.gmtime(d.get("createdAt", 0) / 1000))


def _child_records(store, owner, cid, kinds, dfrom="", dto=""):
    out = {}
    for k in kinds:
        items = [d for d in store.all("r_" + k) if d.get("ownerId") == owner and d.get("childId") == cid
                 and d.get("status") != "REMOVED"]
        if dfrom:
            items = [d for d in items if _iso_day(d) >= dfrom]
        if dto:
            items = [d for d in items if _iso_day(d) <= dto]
        items.sort(key=lambda d: (_iso_day(d), d.get("createdAt", 0)), reverse=True)
        out[k] = items[:500]
    return out


def _access_share(store, uid, sid):
    sh = store.get("shares", sid)
    if not sh or not _share_live(sh) or uid not in (sh.get("ownerId"), sh.get("redeemedBy")):
        raise ApiError("اشتراک پیدا نشد یا منقضی شده است.")
    return sh


def a_shareView(store, uid, args):
    _require(uid)
    sh = _access_share(store, uid, _s(args.get("id")))
    child = next((c for c in store.all("children") if c.get("id") == sh["childId"]), None)
    if not child:
        raise ApiError("پروفایل کودک پیدا نشد.")
    owner = store.get("users", sh["ownerId"]) or {}
    recs = _child_records(store, sh["ownerId"], sh["childId"], sh["kinds"])
    return {"child": {"id": child["id"], "nickname": child.get("nickname"), "ageRange": child.get("ageRange")},
            "ownerName": owner.get("displayName", ""), "kinds": sh["kinds"], "expiresAt": sh["expiresAt"],
            "records": {k: [_rec_view(store, d, uid, k) for d in v] for k, v in recs.items()}}


# ---- Printable report -----------------------------------------------------------------------

LOG_TYPES = {"meltdown": "بحران رفتاری", "sleep": "خواب", "seizure": "تشنج", "meal": "تغذیه",
             "trigger": "محرک", "other": "سایر"}


def build_report_html(store, uid, q):
    import html as _html
    esc = lambda v: _html.escape(str(v if v is not None else ""), quote=True)
    sid = _s(q.get("share"))
    if sid:
        sh = _access_share(store, uid, sid)
        owner, cid, kinds = sh["ownerId"], sh["childId"], sh["kinds"]
    else:
        cid, owner, kinds = _s(q.get("childId")), uid, SHARE_KINDS
    child = next((c for c in store.all("children") if c.get("id") == cid and c.get("familyUserId") == owner), None)
    if not child:
        raise ApiError("پروفایل کودک پیدا نشد.")
    dfrom, dto = _s(q.get("from")), _s(q.get("to"))
    iso = re.compile(r"\d{4}-\d{2}-\d{2}")
    dfrom = dfrom if iso.fullmatch(dfrom) else ""
    dto = dto if iso.fullmatch(dto) else ""
    recs = _child_records(store, owner, cid, kinds, dfrom, dto)
    parts = []

    def table(head, rows):
        if not rows:
            return "<p class=muted>موردی ثبت نشده است.</p>"
        return "<table><tr>%s</tr>%s</table>" % (
            "".join("<th>%s</th>" % esc(h) for h in head),
            "".join("<tr>%s</tr>" % "".join("<td>%s</td>" % esc(c) for c in r) for r in rows))

    if "emergency" in kinds and recs.get("emergency"):
        e = recs["emergency"][0]
        rows = [(lbl, e.get(f)) for lbl, f in (("نام", "name"), ("تشخیص", "diagnosis"), ("حساسیت‌های حسی", "sensory"),
                                                ("محرک‌ها", "triggers"), ("روش‌های آرام‌سازی", "calming"),
                                                ("نکات پزشکی", "medical"), ("تماس ۱", "contact1Name"),
                                                ("تلفن ۱", "contact1Phone"), ("تماس ۲", "contact2Name"),
                                                ("تلفن ۲", "contact2Phone")) if e.get(f)]
        parts.append("<h2>کارت اضطراری</h2>" + table(("مورد", "مقدار"), rows))
    if "log" in kinds:
        logs = recs.get("log", [])
        counts = {}
        for d in logs:
            counts[d.get("type")] = counts.get(d.get("type"), 0) + 1
        ints = [int(d["intensity"]) for d in logs if str(d.get("intensity") or "").isdigit()]
        summ = "، ".join("%s: %d" % (LOG_TYPES.get(k, k), n) for k, n in counts.items())
        parts.append("<h2>ثبت رویدادها</h2><p class=muted>%s%s</p>" % (
            esc("جمع: %d" % len(logs) + ("  —  " + summ if summ else "")),
            esc("  —  میانگین شدت: %.1f" % (sum(ints) / len(ints)) if ints else "")))
        parts.append(table(("تاریخ", "ساعت", "نوع", "شدت", "مدت (دقیقه)", "محرک", "یادداشت"),
                           [(_iso_day(d), d.get("time"), LOG_TYPES.get(d.get("type"), d.get("type")), d.get("intensity"),
                             d.get("durationMin"), d.get("trigger"), d.get("notes")) for d in logs]))
    if "diary" in kinds:
        parts.append("<h2>دفتر خاطرات</h2>" + table(("تاریخ", "حال", "یادداشت"),
                     [(_iso_day(d), d.get("mood"), d.get("note")) for d in recs.get("diary", [])]))
    if "med" in kinds:
        parts.append("<h2>دارو و مکمل</h2>" + table(("نام", "دوز", "زمان‌ها", "توضیح"),
                     [(d.get("name"), d.get("dose"), d.get("times"), d.get("notes")) for d in recs.get("med", [])]))
    if "reward" in kinds:
        parts.append("<h2>پاداش‌ها</h2>" + table(("عنوان", "ستاره", "هدف"),
                     [(d.get("title"), d.get("stars"), d.get("goal")) for d in recs.get("reward", [])]))
    owner_doc = store.get("users", owner) or {}
    period = ("از %s " % dfrom if dfrom else "") + ("تا %s" % dto if dto else "") or "همهٔ زمان‌ها"
    return (
        "<!doctype html><html lang=fa dir=rtl><head><meta charset=utf-8>"
        "<meta name=viewport content='width=device-width,initial-scale=1'>"
        "<title>گزارش %s</title><style>"
        "body{font-family:Vazirmatn,Tahoma,sans-serif;margin:24px;color:#222;line-height:1.9}"
        "h1{font-size:22px;margin:0}h2{font-size:17px;border-bottom:2px solid #6a5acd;padding-bottom:4px;margin-top:28px}"
        "table{border-collapse:collapse;width:100%%;font-size:13px}th,td{border:1px solid #ccc;padding:5px 8px;text-align:right;vertical-align:top}"
        "th{background:#f0eefc}.muted{color:#666;font-size:13px}.foot{margin-top:32px;color:#888;font-size:12px}"
        "@media print{body{margin:8mm}h2{break-after:avoid}tr{break-inside:avoid}}</style></head><body>"
        "<h1>گزارش «%s»</h1><p class=muted>بازهٔ گزارش: %s — تنظیم‌شده توسط %s — تاریخ تهیه: %s</p>%s"
        "<p class=foot>این گزارش از ثبت‌های خانواده در «همراهِ طیف» ساخته شده و جایگزین نظر پزشک یا متخصص نیست.</p>"
        "</body></html>"
    ) % (esc(child.get("nickname") or "کودک"), esc(child.get("nickname") or "کودک"), esc(period),
         esc(owner_doc.get("displayName", "")), time.strftime("%Y-%m-%d"), "".join(parts))


# ---- State etag (cheap polling) --------------------------------------------------------------

def state_etag(state):
    return hashlib.sha1(json.dumps(state, sort_keys=True, ensure_ascii=False).encode("utf-8")).hexdigest()[:20]


def a_state(store, uid, args):
    """Poll-friendly snapshot: {etag, notModified:true} when args.etag still matches, else {etag, state}."""
    _require(uid)
    state = build_state(store, uid)
    tag = state_etag(state)
    if _s(args.get("etag")) == tag:
        return {"etag": tag, "notModified": True}
    return {"etag": tag, "state": state}


# ---- Orphan upload sweeper ----------------------------------------------------------------------

def sweep_orphans(min_age_hours=24, owner=None, dry=False):
    """Remove uploaded files that no document references. -> number removed."""
    cur = conn().cursor()
    cur.execute("SELECT data::text FROM docs WHERE collection<>'files'")
    referenced = set()
    for (txt,) in cur.fetchall():
        referenced.update(re.findall(r"[0-9a-f]{32}", txt))
    cur.close()
    cutoff = now_ms() - int(min_age_hours * 3600 * 1000)
    removed = 0
    with MUT_LOCK:
        store = Store()
        known = set()
        for f in list(store.all("files")):
            known.add(f["id"])
            if f["id"] in referenced or f.get("createdAt", 0) > cutoff or (owner and f.get("ownerId") != owner):
                continue
            removed += 1
            if not dry:
                _unlink_file(f)
                store.delete("files", f["id"])
        if not owner and os.path.isdir(UPLOAD_DIR):  # bytes with no doc at all
            for fn in os.listdir(UPLOAD_DIR):
                fid = fn.split(".")[0]
                p = os.path.join(UPLOAD_DIR, fn)
                if HEX32.fullmatch(fid) and fid not in known and fid not in referenced \
                        and os.path.getmtime(p) < cutoff / 1000:
                    removed += 1
                    if not dry:
                        os.remove(p)
    return removed


def _sweeper_loop():
    time.sleep(30)
    while True:
        try:
            n = sweep_orphans()
            log("sweeper: removed %d orphan upload(s)" % n)
        except Exception:
            log("sweeper failed:", traceback.format_exc(limit=3))
        time.sleep(24 * 3600)


# ---- Signed file URLs -------------------------------------------------------------------------
# GET /api/files/<id> is accepted with a valid ?exp&sig, or a Bearer of a user allowed to read the
# file, or (only while FILES_ENFORCE is off, for 0.13 clients) as a bare capability URL.

FILES_ENFORCE = os.environ.get("FILES_ENFORCE", "").strip().lower() in ("1", "true", "yes", "on")
FILE_URL_TTL = 24 * 3600
FILE_LINK_BAD = "پیوند فایل منقضی یا نامعتبر است."


def _file_sig(fid, exp):
    return hmac.new(JWT_SECRET, ("file|%s|%d" % (fid, exp)).encode("ascii"), hashlib.sha256).hexdigest()[:32]


def sign_file(fid, exp=None):
    """-> (relative signed url, exp unix seconds)."""
    exp = int(exp if exp is not None else time.time() + FILE_URL_TTL)
    return "files/%s?exp=%d&sig=%s" % (fid, exp, _file_sig(fid, exp)), exp


def file_sig_ok(fid, exp, sig):
    try:
        exp = int(exp)
    except (TypeError, ValueError):
        return False
    return exp > time.time() and isinstance(sig, str) and hmac.compare_digest(sig, _file_sig(fid, exp))


def _readable_file_ids(store, uid):
    """Files the caller may read besides their own: avatars, donation photos, curated content,
    and records of children shared with them through a live share."""
    out = set()
    for u in store.all("users"):
        if u.get("photoId"):
            out.add(u["photoId"])
        m = re.search(r"files/([0-9a-f]{32})$", str(u.get("photoUrl") or ""))
        if m:
            out.add(m.group(1))
    for d in store.all("donations"):
        if d.get("status") not in ("REMOVED", "HIDDEN"):
            out.update(x for x in d.get("imageIds") or [] if isinstance(x, str))
    for pr in store.all("professionals"):
        if pr.get("photoId") and (pr.get("userId") == uid or pr.get("verificationStatus") == "VERIFIED"):
            out.add(pr["photoId"])
    for kind, spec in REC_KINDS.items():
        for d in store.all("r_" + kind):
            if spec["scope"] != "own" or d.get("ownerId") == uid:
                out.update(_rec_file_ids(spec, d))
    for sh in store.all("shares"):
        if sh.get("redeemedBy") == uid and _share_live(sh):
            for k, items in _child_records(store, sh["ownerId"], sh["childId"], sh["kinds"]).items():
                for d in items:
                    out.update(_rec_file_ids(REC_KINDS[k], d))
    for v in store.all("verifications"):
        if v.get("userId") == uid:
            out.update(v.get("documentRefs") or [])
    return out


def a_fileUrls(store, uid, args):
    _require(uid)
    ids = args.get("ids")
    if not isinstance(ids, list) or len(ids) > 100:
        raise ApiError("فهرست فایل‌ها نامعتبر است.")
    admin = is_admin(store, uid)
    readable = None
    exp = int(time.time() + FILE_URL_TTL)
    urls = {}
    for fid in dict.fromkeys(i for i in ids if isinstance(i, str) and HEX32.fullmatch(i) and i != ZERO_ID):
        doc = store.get("files", fid)
        if not doc:
            continue
        if not (admin or doc.get("ownerId") == uid):
            if readable is None:
                readable = _readable_file_ids(store, uid)
            if fid not in readable:
                continue
        urls[fid] = sign_file(fid, exp)[0]
    return {"urls": urls, "expiresAt": exp * 1000}


LIGHT_PREFIXES = ("a_donation", "a_rec", "a_slot", "a_search", "a_share", "a_admin", "a_state")
LIGHT_ACTIONS = {n[2:] for n in list(globals()) if n.startswith(LIGHT_PREFIXES)} | {"exportMyData", "fileUrls", "changePassword"}


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
        for k in ("email", "blockedUserIds", "notificationSettings", "tokensValidAfter", "lastSeenAt"):
            pu.pop(k, None)
        if not admin:  # honour the owner's privacy switches (city hidden by default, bio shown by default)
            priv = u.get("privacy") or {}
            if not priv.get("showCity", False):
                pu["city"] = None
            if not priv.get("showBio", True):
                pu["bio"] = None
        return pu

    all_users = list(store.all("users"))
    blocked_me = {u["id"] for u in all_users if uid in (u.get("blockedUserIds") or [])}
    users_by_id = {u["id"]: u for u in all_users}
    professionals = []
    for pr in store.all("professionals"):
        if admin or pr.get("userId") == uid:
            professionals.append(pr)
        elif pr.get("verificationStatus") == "VERIFIED" and pr.get("userId") not in blocked_me:
            owner_priv = (users_by_id.get(pr.get("userId")) or {}).get("privacy") or {}
            professionals.append(pr if owner_priv.get("showCity", False) else dict(pr, city=None))

    # Questions/answers/posts/guides: PUBLISHED for everyone; author sees own; admin sees all.
    def visible_content(doc, author_field="authorId"):
        return (doc.get("status") == "PUBLISHED" or doc.get(author_field) == uid or admin)

    def not_blocked(doc):
        return admin or doc.get("authorId") not in blocked_me

    questions = [dict(q, authorId="") if q.get("isAnonymous") and q.get("authorId") != uid and not admin else q
                 for q in store.all("questions")
                 if q.get("status") != "REMOVED" and visible_content(q) and not_blocked(q)]
    q_ids = {q["id"] for q in questions}
    answers = [a for a in store.all("answers")
               if a.get("status") != "REMOVED" and visible_content(a) and not_blocked(a)]
    guides = [g for g in store.all("guides")
              if g.get("status") == "PUBLISHED" or admin]

    # Groups: public + ones I'm a member of (any status besides REMOVED).
    my_group_ids = {m["groupId"] for m in store.all("memberships")
                    if m.get("userId") == uid and m.get("status") != "REMOVED"}
    groups = [g for g in store.all("groups")
              if admin or g["id"] in my_group_ids
              or (g.get("visibility") == "PUBLIC" and g.get("status") != "ARCHIVED")]
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
    posts = [p for p in store.all("posts") if post_visible(p) and not_blocked(p)]

    # Conversations & messages: mine only.
    conversations = [c for c in store.all("conversations")
                     if uid in (c.get("participantIds") or []) and c.get("status") != "DECLINED"]
    conv_ids = {c["id"] for c in conversations}
    messages = [m for m in store.all("messages") if m.get("conversationId") in conv_ids]

    notifications = [n for n in store.all("notifications") if n.get("userId") == uid]

    # Verifications: own + all if admin. Children: own only.
    if admin:  # the review queue holds only each user's latest request
        latest = {}
        for v in store.all("verifications"):
            cur = latest.get(v.get("userId"))
            if not cur or (v.get("createdAt", 0), v.get("updatedAt", 0)) >= (cur.get("createdAt", 0), cur.get("updatedAt", 0)):
                latest[v.get("userId")] = v
        verifications = list(latest.values())
    else:
        verifications = [v for v in store.all("verifications") if v.get("userId") == uid]
    children = [c for c in store.all("children") if c.get("familyUserId") == uid]

    # Reports & admin actions: admin only.
    reports = list(store.all("reports")) if admin else []
    admin_actions = list(store.all("adminActions")) if admin else []
    audit_log = sorted(store.all("adminAudit"), key=lambda a: a.get("createdAt", 0), reverse=True)[:200] if admin else []

    # Users: only people the caller can meet (self, admins, verified pros, content authors, group
    # members, conversation partners, people they blocked); admins see everyone.
    wanted = {uid} | {x for x in (users_by_id.get(uid) or {}).get("blockedUserIds") or []}
    wanted |= {u["id"] for u in all_users if u.get("isAdmin")}
    wanted |= {pr.get("userId") for pr in professionals}
    wanted |= {q.get("authorId") for q in questions} | {a.get("authorId") for a in answers}
    wanted |= {p.get("authorId") for p in posts} | {m.get("userId") for m in memberships}
    wanted |= {g.get("ownerId") for g in groups}
    wanted |= {pid for c in conversations for pid in c.get("participantIds") or []}
    wanted -= blocked_me
    users = [public_user(u) for u in all_users if admin or u["id"] in wanted]

    return {
        "users": users, "professionals": professionals, "verifications": verifications,
        "children": children, "questions": questions, "answers": answers, "groups": groups,
        "memberships": memberships, "posts": posts, "conversations": conversations,
        "messages": messages, "guides": guides, "reports": reports,
        "notifications": notifications, "adminActions": admin_actions, "auditLog": audit_log,
    }


# --------------------------------------------------------------------------------------
# Auth endpoints
# --------------------------------------------------------------------------------------

def do_register(args, ip):
    if not RL.allow("register-ip:" + ip, 10, 3600):
        raise ApiError(TOO_MANY)
    name = _s(args.get("displayName"))
    mail = _s(args.get("email")).lower()
    password = args.get("password")
    if not name or len(name) > 60:
        raise ApiError("نام نمایشی را وارد کنید (حداکثر ۶۰ نویسه).")
    if not EMAIL_RE.match(mail) or len(mail) > 120:
        raise ApiError("ایمیل معتبر وارد کنید.")
    if not isinstance(password, str) or len(password) < 6:
        raise ApiError("رمز عبور باید حداقل ۶ نویسه باشد.")
    if len(password) > MAX_PASSWORD:
        raise ApiError("رمز عبور بیش از حد طولانی است.")
    if not args.get("acceptedTerms"):
        raise ApiError("برای ادامه، قوانین و حریم خصوصی را بپذیرید.")
    with MUT_LOCK:
        if has_email(mail):
            raise ApiError("این ایمیل قبلاً ثبت شده است.")
        store = Store()
        uid = new_id("u")
        # Everyone starts as FAMILY: a professional account type is granted only by admin verification.
        user = {
            "id": uid, "displayName": name, "accountType": "FAMILY",
            "email": mail, "photoUrl": None, "city": None, "bio": None, "status": "ACTIVE",
            "isAdmin": False, "privacy": {"showCity": False, "showBio": True, "allowMessageRequests": True},
            "notificationSettings": {}, "blockedUserIds": [], "joinedAt": now_ms(), "updatedAt": now_ms(),
        }
        store.put("users", user)
        set_cred(mail, password, uid)
    return {"token": make_token(uid), "user": user}


def do_login(args, ip):
    mail = _s(args.get("email")).lower()
    password = args.get("password")
    fkey, ikey = "login-fail:" + mail, "login-ip:" + ip
    if RL.blocked(fkey, 8, 900) or RL.blocked(ikey, 40, 900):
        raise ApiError(TOO_MANY)
    uid = check_cred(mail, password[:MAX_PASSWORD] if isinstance(password, str) else "") \
        if len(mail) <= 120 and isinstance(password, str) and len(password) <= MAX_PASSWORD else None
    if not uid:
        RL.hit(fkey, 900)
        RL.hit(ikey, 900)
        raise ApiError("ایمیل یا رمز عبور نادرست است.")
    store = Store()
    user = store.get("users", uid)
    if not user:
        raise ApiError("حساب یافت نشد.")
    if user.get("status") in ("BLOCKED", "DELETED"):
        raise ApiError("این حساب مسدود شده است.")
    RL.clear(fkey)
    return {"token": make_token(uid), "user": user}


# --------------------------------------------------------------------------------------
# HTTP handler
# --------------------------------------------------------------------------------------

# --------------------------------------------------------------------------------------
# File uploads (images / audio / video / PDF). Files get an unguessable id; anyone holding the
# id-URL can read it (capability URL) — the id is only ever shared inside signed-in content.
# --------------------------------------------------------------------------------------

UPLOAD_DIR = os.environ.get("UPLOAD_DIR", os.path.join(os.path.dirname(os.path.abspath(__file__)), "uploads"))
MAX_UPLOAD = 8 * 1024 * 1024
UPLOAD_TYPES = {
    "image/jpeg": "jpg", "image/png": "png", "image/webp": "webp", "image/gif": "gif",
    "audio/mp4": "m4a", "audio/mpeg": "mp3", "audio/ogg": "ogg", "audio/aac": "aac",
    "video/mp4": "mp4", "application/pdf": "pdf",
}
MIME_BY_EXT = {v: k for k, v in UPLOAD_TYPES.items()}


MAX_FILES_PER_USER = 300
MAX_BYTES_PER_USER = 200 * 1024 * 1024


def _sniff_ok(mime, d):
    """Magic-byte check so a declared mime can't be used to smuggle other content."""
    h = d[:16]
    if mime == "image/jpeg":
        return h[:3] == b"\xff\xd8\xff"
    if mime == "image/png":
        return h[:8] == b"\x89PNG\r\n\x1a\n"
    if mime == "image/gif":
        return h[:4] == b"GIF8"
    if mime == "image/webp":
        return h[:4] == b"RIFF" and h[8:12] == b"WEBP"
    if mime == "application/pdf":
        return d[:1024].find(b"%PDF-") >= 0
    if mime in ("audio/mp4", "video/mp4"):
        return h[4:8] == b"ftyp"
    if mime == "audio/mpeg":
        return h[:3] == b"ID3" or (h[0:1] == b"\xff" and (h[1] & 0xE0) == 0xE0)
    if mime == "audio/ogg":
        return h[:4] == b"OggS"
    if mime == "audio/aac":
        return h[0:1] == b"\xff" and (h[1] & 0xF0) == 0xF0
    return False


def save_upload(store, uid, mime, data, name):
    ext = UPLOAD_TYPES.get(mime)
    if not ext:
        raise ApiError("نوع فایل پشتیبانی نمی‌شود.")
    if not data or len(data) > MAX_UPLOAD:
        raise ApiError("حجم فایل باید کمتر از ۸ مگابایت باشد.")
    if not _sniff_ok(mime, data):
        raise ApiError("محتوای فایل با نوع اعلام‌شده هم‌خوانی ندارد.")
    with MUT_LOCK:  # quota check + insert must not interleave
        mine = [f for f in store.all("files") if f.get("ownerId") == uid]
        if len(mine) >= MAX_FILES_PER_USER or sum(int(f.get("size", 0) or 0) for f in mine) + len(data) > MAX_BYTES_PER_USER:
            raise ApiError("سقف فضای فایل‌های شما پر شده است؛ چند فایل قدیمی را حذف کنید.")
        fid = secrets.token_hex(16)
        os.makedirs(UPLOAD_DIR, exist_ok=True)
        path = os.path.join(UPLOAD_DIR, "%s.%s" % (fid, ext))
        with open(path + ".part", "wb") as f:
            f.write(data)
        os.replace(path + ".part", path)
        store.put("files", {"id": fid, "ownerId": uid, "mime": mime, "size": len(data),
                            "name": _s(name)[:120], "createdAt": now_ms()})
    return {"id": fid, "url": "files/%s" % fid, "signedUrl": sign_file(fid)[0], "mime": mime, "size": len(data)}


TTS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "tts")
TTS_MAX = 300
TTS_CACHE_FILES = 3000
TTS_CACHE_BYTES = 300 * 1024 * 1024
_tts_lock = threading.Lock()


def _tts_cached(cache, h):
    for ext, mime in (("mp3", "audio/mpeg"), ("wav", "audio/wav")):
        p = os.path.join(cache, h + "." + ext)
        if os.path.exists(p):
            try:
                os.utime(p)  # LRU: a hit makes the entry the freshest
                with open(p, "rb") as f:
                    return f.read(), mime
            except OSError:
                pass
    return None


def tts_audio(text, uid=None):
    """Persian speech -> (bytes, mime). Microsoft neural voice (edge-tts, fa-IR-Dilara) first,
    Piper (fa_IR-amir, local) as the fallback. Results are cached on disk by text hash (LRU)."""
    import subprocess
    cache = os.path.join(TTS_DIR, "cache")
    os.makedirs(cache, exist_ok=True)
    h = hashlib.sha1(text.encode("utf-8")).hexdigest()
    hit = _tts_cached(cache, h)
    if hit:
        return hit
    if uid and not RL.allow("tts:" + uid, 120, 3600):  # only uncached synthesis is metered
        raise ApiError(TOO_MANY)
    py = os.path.join(TTS_DIR, "venv/bin/python")
    with _tts_lock:
        hit = _tts_cached(cache, h)
        if hit:
            return hit
        entries = []
        for fn in os.listdir(cache):
            p = os.path.join(cache, fn)
            try:
                st = os.stat(p)
                entries.append((st.st_mtime, st.st_size, p))
            except OSError:
                pass
        entries.sort()
        total = sum(e[1] for e in entries)
        while entries and (len(entries) > TTS_CACHE_FILES or total > TTS_CACHE_BYTES):
            _, size, p = entries.pop(0)
            total -= size
            try:
                os.remove(p)
            except OSError:
                pass
        attempts = (
            ("mp3", "audio/mpeg", [py, "-m", "edge_tts", "--voice", "fa-IR-DilaraNeural", "--file", "-", "--write-media"]),
            ("wav", "audio/wav", [py, "-m", "piper", "-m", os.path.join(TTS_DIR, "fa_IR-amir-medium.onnx"), "-f"]),
        )
        err = ""
        for ext, mime, cmd in attempts:
            path = os.path.join(cache, h + "." + ext)
            tmp = path + ".part." + ext
            try:
                r = subprocess.run(cmd + [tmp], input=text.encode("utf-8"), capture_output=True, timeout=45)
            except subprocess.TimeoutExpired:
                err = "timeout"
                continue
            if r.returncode == 0 and os.path.exists(tmp) and os.path.getsize(tmp) > 500:
                os.replace(tmp, path)
                with open(path, "rb") as f:
                    return f.read(), mime
            err = r.stderr.decode("utf-8", "ignore")[-120:]
            if os.path.exists(tmp):
                os.remove(tmp)
        raise RuntimeError(err or "tts failed")


def clean_tts_text(text):
    text = re.sub(r"[\x00-\x1f\x7f]+", " ", text if isinstance(text, str) else "").strip()
    if not text or len(text) > TTS_MAX:
        raise ApiError("متن نامعتبر است.")
    return text


GENERIC_ERROR = "خطای داخلی سرور. کمی بعد دوباره تلاش کنید."
MAX_BODY = 1024 * 1024
READ_ONLY_ACTIONS = {"recList", "donationList", "donationGet", "search", "shareList", "shareView",
                     "adminStats", "adminListUsers", "state", "exportMyData", "fileUrls"}


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    timeout = 60  # drop stalled/slow-loris connections

    def handle(self):
        try:
            super().handle()
        finally:
            c = getattr(_local, "conn", None)
            if c is not None:
                try:
                    c.close()
                except Exception:
                    pass
                _local.conn = None

    def log_message(self, fmt, *a):
        log("http: " + (fmt % a))

    def log_request(self, code="-", size="-"):
        # One line per request in journald. Never the query string (it can carry text or ids).
        path = self.path.split("?")[0]
        if path.startswith("/api/files/"):
            path = "/api/files/" + path.rsplit("/", 1)[-1][:6] + "..."
        ms = int((time.time() - getattr(self, "_t0", time.time())) * 1000)
        log("%s %s %s %s uid=%s %dms" % (self.client_address[0], self.command, path, code,
                                          getattr(self, "_log_uid", "-"), ms))

    def _ip(self):
        peer = self.client_address[0]
        if peer in ("127.0.0.1", "::1"):  # behind our own nginx / CDN: trust its forwarding headers
            fwd = (self.headers.get("X-Forwarded-For") or self.headers.get("X-Real-IP") or "").split(",")[0].strip()
            if re.fullmatch(r"[0-9a-fA-F:.]{3,45}", fwd):
                return fwd
        return peer

    def _send(self, code, obj, extra=None):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def _fail(self, where):
        log("error in %s:\n%s" % (where, traceback.format_exc()))
        try:
            self._send(500, {"ok": False, "error": GENERIC_ERROR})
        except Exception:
            pass

    def _uid(self):
        auth = self.headers.get("Authorization", "")
        if not auth.startswith("Bearer "):
            return None
        uid = token_user(auth[7:])
        if uid:
            self._log_uid = uid
            touch_user(uid)
        return uid

    def _body(self):
        n = int(self.headers.get("Content-Length", "0") or "0")
        if n <= 0:
            return {}
        if n > MAX_BODY:
            raise ApiError("درخواست بیش از حد بزرگ است.")
        raw = self.rfile.read(n)
        try:
            obj = json.loads(raw.decode("utf-8"))
        except Exception:
            return {}
        return obj if isinstance(obj, dict) else {}

    def _send_bytes(self, mime, body, cache="private, max-age=31536000, immutable", extra=None):
        self.send_response(200)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", cache)
        self.send_header("X-Content-Type-Options", "nosniff")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        self._t0 = time.time()
        try:
            self._get()
        except ApiError as e:
            self._send(400, {"ok": False, "error": str(e)})
        except Exception:
            self._fail("GET " + self.path.split("?")[0])

    def do_POST(self):
        self._t0 = time.time()
        try:
            self._post()
        except ApiError as e:
            self._send(200, {"ok": False, "error": str(e)})
        except Exception:
            self._fail("POST " + self.path.split("?")[0])

    def _file_allowed(self, store, fid, doc, query):
        if not doc:
            return True  # 404 follows; nothing to protect
        if file_sig_ok(fid, query.get("exp"), query.get("sig")):
            return True
        uid = self._uid()
        if uid:
            u = store.get("users", uid) or {}
            if u.get("isAdmin") or doc.get("ownerId") == uid or fid in _readable_file_ids(store, uid):
                return True
        if "sig" in query or "exp" in query:
            return False  # a signed URL that fails is rejected even while legacy URLs are tolerated
        return not FILES_ENFORCE

    def _get(self):
        path = self.path.split("?")[0].rstrip("/")
        query = {k: v[0] for k, v in urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query).items()}
        if path.startswith("/api/files/"):
            fid = path.rsplit("/", 1)[-1]
            if not HEX32.fullmatch(fid):
                return self._send(404, {"ok": False, "error": "یافت نشد."})
            store = Store()
            doc = store.get("files", fid)
            if not self._file_allowed(store, fid, doc, query):
                return self._send(403, {"ok": False, "error": FILE_LINK_BAD})
            ext = UPLOAD_TYPES.get(doc["mime"]) if doc else None
            fpath = os.path.join(UPLOAD_DIR, "%s.%s" % (fid, ext)) if ext else None
            if not fpath or not os.path.exists(fpath):
                return self._send(404, {"ok": False, "error": "یافت نشد."})
            with open(fpath, "rb") as f:
                body = f.read()
            return self._send_bytes(doc["mime"], body, extra={"Content-Security-Policy": "default-src 'none'; sandbox"})
        if path == "/api/tts":  # GET kept for 0.12 clients; POST is preferred (text stays out of URLs/logs)
            uid = self._uid()
            if not uid:
                return self._send(401, {"ok": False, "error": NOT_SIGNED_IN})
            return self._serve_tts(uid, query.get("text", ""))
        if path == "/api/health":
            return self._send(200, {"ok": True})
        if path == "/api/features":
            store = Store()
            uid = self._uid()
            off = blocked_modules(store, uid) if uid else set(disabled_features(store))
            return self._send(200, {"ok": True, "disabled": sorted(off)})
        if path == "/api/notifications":
            # Light endpoint for the background push poller (just the caller's notifications).
            uid = self._uid()
            if not uid:
                return self._send(401, {"ok": False, "error": NOT_SIGNED_IN})
            store = Store()
            items = sorted(
                (n for n in store.all("notifications") if n.get("userId") == uid),
                key=lambda n: n.get("createdAt", 0), reverse=True,
            )[:50]
            return self._send(200, {"ok": True, "notifications": items})
        if path == "/api/state":
            uid = self._uid()
            if not uid:
                return self._send(401, {"ok": False, "error": NOT_SIGNED_IN})
            state = build_state(Store(), uid)
            tag = state_etag(state)
            if query.get("etag") == tag or (self.headers.get("If-None-Match") or "").strip('"') == tag:
                return self._send(200, {"ok": True, "notModified": True, "etag": tag}, {"ETag": '"%s"' % tag})
            return self._send(200, {"ok": True, "state": state, "etag": tag}, {"ETag": '"%s"' % tag})
        if path == "/api/report":
            uid = self._uid()
            if not uid:
                return self._send(401, {"ok": False, "error": NOT_SIGNED_IN})
            if not RL.allow("report:" + uid, 30, 600):
                raise ApiError(TOO_MANY)
            page = build_report_html(Store(), uid, query).encode("utf-8")
            return self._send_bytes("text/html; charset=utf-8", page, cache="no-store", extra={
                "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'; img-src data:"})
        return self._send(404, {"ok": False, "error": "یافت نشد."})

    def _serve_tts(self, uid, text):
        try:
            text = clean_tts_text(text)
        except ApiError as e:
            return self._send(400, {"ok": False, "error": str(e)})
        try:
            body, mime = tts_audio(text, uid)
        except ApiError as e:
            return self._send(429, {"ok": False, "error": str(e)})
        except Exception as e:
            log("tts failed: %s" % e)
            return self._send(503, {"ok": False, "error": "تبدیل متن به صدا ممکن نشد."})
        return self._send_bytes(mime, body)

    def _post(self):
        path = self.path.split("?")[0].rstrip("/")
        if path == "/api/upload":
            uid = self._uid()
            n = int(self.headers.get("Content-Length", "0") or "0")
            if not uid:
                return self._send(401, {"ok": False, "error": NOT_SIGNED_IN})
            if n > MAX_UPLOAD:
                return self._send(413, {"ok": False, "error": "حجم فایل باید کمتر از ۸ مگابایت باشد."})
            if n <= 0:
                return self._send(400, {"ok": False, "error": "فایل خالی است."})
            if not RL.allow("upload:" + uid, 60, 600):
                return self._send(429, {"ok": False, "error": TOO_MANY})
            try:
                data = self.rfile.read(n)
                mime = (self.headers.get("Content-Type", "") or "").split(";")[0].strip().lower()
                name = urllib.parse.unquote(self.headers.get("X-Filename", ""))
                return self._send(200, {"ok": True, "file": save_upload(Store(), uid, mime, data, name)})
            except ApiError as e:
                return self._send(400, {"ok": False, "error": str(e)})
        if path == "/api/tts":
            uid = self._uid()
            if not uid:
                return self._send(401, {"ok": False, "error": NOT_SIGNED_IN})
            return self._serve_tts(uid, self._body().get("text", ""))
        args = self._body()
        ip = self._ip()
        if path == "/api/auth/register":
            return self._send(200, do_register(args, ip))
        if path == "/api/auth/login":
            return self._send(200, do_login(args, ip))
        if path == "/api/auth/forgot":
            return self._send(200, do_forgot(args, ip))
        if path == "/api/auth/reset":
            return self._send(200, do_reset(args, ip))
        if path == "/api/action":
            uid = self._uid()
            if not uid:
                return self._send(401, {"ok": False, "error": NOT_SIGNED_IN})
            name = args.get("name")
            fn = ACTIONS.get(name) if isinstance(name, str) else None
            if not fn:
                return self._send(404, {"ok": False, "error": "عملیات ناشناخته."})
            a = args.get("args")
            a = a if isinstance(a, dict) else {}
            if not RL.allow("act:" + uid, 600, 60):
                return self._send(429, {"ok": False, "error": TOO_MANY})
            gate = module_of(name, a)
            if gate and gate in blocked_modules(Store(), uid):
                return self._send(200, {"ok": False, "error": "این بخش برای شما فعال نیست."})
            if name in READ_ONLY_ACTIONS:
                store = Store()
                _require_active(store, uid, name)
                data = fn(store, uid, a)
            else:
                with MUT_LOCK:  # one writer at a time; a fresh Store inside the lock avoids stale reads
                    store = Store()
                    _require_active(store, uid, name)
                    data = fn(store, uid, a)
            resp = {"ok": True}
            if data:
                resp["data"] = data
            if name not in LIGHT_ACTIONS:
                resp["state"] = build_state(store, uid)  # fresh snapshot saves a round-trip
            return self._send(200, resp)
        return self._send(404, {"ok": False, "error": "یافت نشد."})


_LAST_TOUCH = {}


def touch_user(uid):
    """Record lastSeenAt (at most hourly per user) with an atomic jsonb update."""
    now = time.time()
    if now - _LAST_TOUCH.get(uid, 0) < 3600:
        return
    _LAST_TOUCH[uid] = now
    try:
        cur = conn().cursor()
        cur.execute("UPDATE docs SET data = jsonb_set(data, '{lastSeenAt}', to_jsonb(%s::bigint)) "
                    "WHERE collection='users' AND id=%s", (int(now * 1000), uid))
        cur.close()
    except Exception:
        log("touch_user failed:", traceback.format_exc(limit=2))


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
ALTER TABLE creds ADD COLUMN IF NOT EXISTS iters integer;
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
        if cmd == "perm":  # perm ID ROLE,ROLE|all
            set_module_roles(Store(), sys.argv[2], [] if sys.argv[3] == "all" else sys.argv[3].split(","))
            return print("roles:", module_roles(Store()))
        if cmd == "feature":  # feature ID on|off
            set_feature(Store(), sys.argv[2], sys.argv[3] == "on")
            return print("disabled:", disabled_features(Store()))
        if cmd == "create-admin":
            return cmd_create_admin(sys.argv[2], sys.argv[3], sys.argv[4])
        if cmd == "mailtest":  # mailtest TO
            if len(sys.argv) < 3:
                return print("usage: hamrah-api.py mailtest TO")
            if not smtp_configured():
                return print("SMTP not configured: set SMTP_HOST (and SMTP_PORT/USER/PASS/FROM) in .env")
            try:
                send_mail(sys.argv[2], "آزمایش ایمیل — همراهِ طیف",
                          "\n".join(RLM + ln for ln in ("سلام،", "این یک ایمیل آزمایشی از سرور «همراهِ طیف» است.",
                                                          "اگر آن را می‌بینید، ارسال کد بازیابی رمز کار می‌کند.")))
            except Exception as e:
                return print("mail FAILED: %s: %s" % (type(e).__name__, e))
            return print("mail sent to %s via %s:%d" % (sys.argv[2], SMTP_HOST, SMTP_PORT))
        if cmd == "sweep":  # sweep [HOURS]  -> delete unreferenced uploads older than HOURS (default 24)
            hours = float(sys.argv[2]) if len(sys.argv) > 2 else 24
            return print("removed %d orphan file(s)" % sweep_orphans(hours))
        print("unknown command: %s" % cmd)
        return
    cmd_init_db()
    if not smtp_configured():
        log("SMTP not configured: password-reset emails are disabled (adminIssueReset still works)")
    threading.Thread(target=_sweeper_loop, daemon=True).start()
    srv = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    srv.daemon_threads = True
    print("hamrah-api listening on 127.0.0.1:%d" % PORT)
    srv.serve_forever()


if __name__ == "__main__":
    main()
