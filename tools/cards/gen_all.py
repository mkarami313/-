#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generates every card illustration with the image model (Metis, OpenAI-compatible, gpt-image-1.5, medium) into
$CARDS_RAW/<cat>/<id>.png. Skips files that exist, 5 in parallel, retries. Key: $METIS_KEY or $METIS_KEY_FILE."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cards import CATEGORIES
from prompts import prompt_for
import json, os, sys, base64, urllib.request, time
K = (os.environ.get("METIS_KEY") or open(os.environ.get("METIS_KEY_FILE", "/root/build/cards-ai/.key")).read()).strip()
STYLE = ("Warm children's picture-book illustration for an educational flash card, soft hand-painted digital style "
         "with gentle texture, clean outlines, friendly and simple, plain light cream background (#FFFBF2), "
         "single clear subject centered, no text, no letters, no watermark, no border. ")
def gen(prompt, out, model="gpt-image-1.5", quality="medium"):
    body = json.dumps({"model": model, "prompt": STYLE + prompt, "size": "1024x1024", "quality": quality, "n": 1}).encode()
    req = urllib.request.Request("https://api.metisai.ir/openai/v1/images/generations", data=body,
                                 headers={"Authorization": "Bearer " + K, "Content-Type": "application/json"})
    t = time.time()
    r = json.loads(urllib.request.urlopen(req, timeout=300).read())
    d = r["data"][0]
    img = base64.b64decode(d["b64_json"]) if d.get("b64_json") else urllib.request.urlopen(d["url"], timeout=120).read()
    open(out, "wb").write(img)
    return time.time() - t, r.get("usage")

import time, threading, concurrent.futures as cf
OUT = os.environ.get("CARDS_RAW", "/root/build/cards-ai/raw")
jobs = []
for cat, _, _, cards in CATEGORIES:
    if cat == "numbers":
        continue
    os.makedirs(os.path.join(OUT, cat), exist_ok=True)
    for i, (cid, _) in enumerate(cards):
        f = os.path.join(OUT, cat, cid + ".png")
        if not os.path.exists(f):
            jobs.append((cat, cid, prompt_for(cat, cid, i), f))
print("to generate:", len(jobs), flush=True)
lock = threading.Lock(); done = [0]; failed = []
def work(j):
    cat, cid, p, f = j
    for attempt in range(4):
        try:
            gen(p, f + ".tmp", "gpt-image-1.5", "medium"); os.replace(f + ".tmp", f)
            with lock:
                done[0] += 1
                print("ok %d/%d %s/%s" % (done[0], len(jobs), cat, cid), flush=True)
            return
        except Exception as e:
            err = str(e)[:120]; time.sleep(5 * (attempt + 1))
    with lock:
        failed.append((cat, cid, err)); print("FAIL", cat, cid, err, flush=True)
with cf.ThreadPoolExecutor(5) as ex:
    list(ex.map(work, jobs))
print("done", done[0], "failed", failed, flush=True)
