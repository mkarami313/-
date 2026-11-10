#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""کاورِ مقاله‌های آموزشیِ بی‌کاور: یک تصویرِ هم‌سبک با gpt-image ِ متیس برای هر مقاله، ذخیره به‌نامِ ادمین.
Idempotent (فقط مقاله‌های بدونِ cover_id). اجرا روی سرور: python3 gen_covers.py"""
import base64, concurrent.futures as cf, importlib.util, json, os, sys, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.argv = [sys.argv[0]]
spec = importlib.util.spec_from_file_location("hamrah_api", os.path.join(HERE, "hamrah-api.py"))
H = importlib.util.module_from_spec(spec)
spec.loader.exec_module(H)
KEY = os.environ["METIS_KEY"]
STYLE = ("Soft, warm flat vector illustration for a Persian parenting app about autism. Calm pastel palette "
         "(teal #0E6E62 accents, cream, soft orange). Gentle, hopeful, respectful; simple shapes, lots of empty space. "
         "Middle-Eastern family look, modest clothing. Absolutely no text, letters, numbers or logos. Scene: ")


def image(title, summary):
    body = {"model": "gpt-image-2", "prompt": STYLE + title + " — " + summary, "size": "1536x1024", "quality": "low",
            "output_format": "jpeg", "output_compression": 85, "n": 1}
    req = urllib.request.Request("https://api.metisai.ir/openai/v1/images/generations", data=json.dumps(body).encode(),
                                 headers={"Authorization": "Bearer " + KEY, "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=240) as r:
        return base64.b64decode(json.load(r)["data"][0]["b64_json"])


def main():
    store = H.Store()
    admin = next(u for u in store.all("users") if u.get("isAdmin"))
    rows = H._q("SELECT id, title, summary FROM l_items WHERE status='PUBLISHED' AND kind='article' AND cover_id IS NULL")
    def one(r):
        data = image(r["title"], r["summary"] or "")
        f = H.save_upload(store, admin["id"], "image/jpeg", data, "cover-%s.jpg" % r["id"])
        H._x("UPDATE l_items SET cover_id=%s WHERE id=%s AND cover_id IS NULL", (f["id"], r["id"]))
        return r["title"]
    ok = 0
    with cf.ThreadPoolExecutor(4) as ex:
        for fut in cf.as_completed([ex.submit(one, r) for r in rows]):
            try:
                print("ok", fut.result(), flush=True); ok += 1
            except Exception as e:
                print("FAIL", repr(e)[:200], flush=True)
    print("covers: %d/%d" % (ok, len(rows)))


if __name__ == "__main__":
    main()
