#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Packs the generated card illustrations into the app (offline):

    python3 tools/cards/gen_all.py     # (once) generate raw PNGs with the image model → $CARDS_RAW/<cat>/<id>.png
    python3 tools/cards/build.py       # → app/src/main/assets/cards/{<cat>/<id>.webp, cards.json}

Number cards are composed here (N apples + the Persian numeral), because image models miscount.
"""
import json
import os
import shutil
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
RAW = os.environ.get("CARDS_RAW", "/root/build/cards-ai/raw")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "cards")
FONT = os.path.join(ROOT, "app", "src", "main", "res", "font", "vazirmatn_bold.ttf")
SIZE = 448
BG = (255, 251, 242)
FA = "۰۱۲۳۴۵۶۷۸۹"

sys.path.insert(0, HERE)
from cards import CATEGORIES  # noqa: E402


def number_card(n, apple):
    img = Image.new("RGB", (SIZE, SIZE), BG)
    d = ImageDraw.Draw(img)
    font = ImageFont.truetype(FONT, 150)
    text = "".join(FA[int(c)] for c in str(n))
    bb = d.textbbox((0, 0), text, font=font)
    d.text(((SIZE - (bb[2] - bb[0])) / 2 - bb[0], 18 - bb[1]), text, font=font, fill=(11, 114, 133))
    if n:
        # lift the apple off its cream background and tile it under the numeral
        a = apple.convert("RGBA")
        px = a.load()
        for y in range(a.height):
            for x in range(a.width):
                r, g, b, _ = px[x, y]
                if r > 235 and g > 228 and b > 205:
                    px[x, y] = (r, g, b, 0)
        a = a.crop(a.getbbox())
        per_row = 5 if n > 4 else n
        rows = (n + per_row - 1) // per_row
        cell = min((SIZE - 40) // per_row, (SIZE - 200) // rows)
        a.thumbnail((cell - 8, cell - 8), Image.LANCZOS)
        for i in range(n):
            r, c = divmod(i, per_row)
            in_row = min(per_row, n - r * per_row)
            x0 = (SIZE - in_row * cell) // 2
            img.paste(a, (x0 + c * cell + (cell - a.width) // 2, 190 + r * cell + (cell - a.height) // 2), a)
    return img


def main():
    shutil.rmtree(OUT, ignore_errors=True)
    manifest = {"version": 2, "categories": []}
    apple = Image.open(os.path.join(RAW, "food", "apple.png"))
    missing = []
    for cid, title, color, cards in CATEGORIES:
        os.makedirs(os.path.join(OUT, cid), exist_ok=True)
        items = []
        for card_id, word in cards:
            if cid == "numbers":
                img = number_card(int(card_id[1:]), apple)
            else:
                src = os.path.join(RAW, cid, card_id + ".png")
                if not os.path.exists(src):
                    missing.append("%s/%s" % (cid, card_id))
                    continue
                img = Image.open(src).convert("RGB").resize((SIZE, SIZE), Image.LANCZOS)
            img.save(os.path.join(OUT, cid, card_id + ".webp"), "WEBP", quality=80, method=6)
            items.append({"id": card_id, "word": word})
        manifest["categories"].append({"id": cid, "title": title, "color": color, "cards": items})
        print("%-10s %3d" % (cid, len(items)))
    json.dump(manifest, open(os.path.join(OUT, "cards.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("missing:", missing)


if __name__ == "__main__":
    main()
