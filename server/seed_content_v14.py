#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""محتوای پایهٔ نسخهٔ ۱.۴: ۲۳ مقالهٔ آموزشی (seed_articles_v14.json)، یک موضوعِ سنجاق‌شدهٔ شروع در هر تالار،
و چند مرکز/خطِ تماسِ رسمیِ تأییدشده. Idempotent (شناسه‌های ثابت). اجرا روی سرور:

    cd /home/rasta/hamrah && python3 seed_content_v14.py
"""
import importlib.util
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.argv = [sys.argv[0]]
spec = importlib.util.spec_from_file_location("hamrah_api", os.path.join(HERE, "hamrah-api.py"))
H = importlib.util.module_from_spec(spec)
spec.loader.exec_module(H)

STARTERS = {
    "clinic": ("سؤالتان را از متخصص بپرسید — راهنمای پرسیدن",
               "در این تالار متخصصانِ تأییدشده (با نشانِ تأیید) پاسخ می‌دهند.\n\nبرای پاسخِ بهتر بنویسید:\n- سنِ کودک و این‌که تشخیص دارد یا نه\n- مشکل از کی شروع شده و چقدر تکرار می‌شود\n- چه کارهایی تا حالا امتحان کرده‌اید\n\nپاسخ‌های این‌جا جای معاینه و ویزیتِ حضوری را نمی‌گیرد؛ در موقعیتِ اورژانسی با ۱۱۵ تماس بگیرید."),
    "diag": ("اولین نشانه‌هایی که دیدید چه بود؟",
             "خیلی از ما اولش نمی‌دانستیم این نشانه‌ها معنایی دارند یا نه. شما اولین بار به چه چیزی شک کردید و چند سالگی بود؟ "
             "تجربه‌تان به خانواده‌ای که الان در همان نقطه است کمک می‌کند.\n\nمقالهٔ «تأخیرِ گفتارِ ساده یا اوتیسم؟» در بخشِ آموزش هم شاید کمک کند."),
    "therapy": ("گفتاردرمانی، کاردرمانی، رفتاردرمانی: تجربهٔ شما کدام بوده؟",
                "از تجربه‌تان بگویید: از چه سنی شروع کردید، هفته‌ای چند جلسه، و چه تغییری دیدید؟ لطفاً نامِ درمانگر را برای "
                "تبلیغ یا انتقاد ننویسید؛ تجربه را بگویید."),
    "daily": ("یک ترفندِ کوچک که زندگیِ روزمره را برایتان راحت‌تر کرد",
              "خواب، غذا، لباس پوشیدن، بیرون رفتن… هر کدام یک ترفندِ کوچک دارد. شما چه چیزی را امتحان کردید که جواب داد؟"),
    "school": ("مهد و مدرسه: از ثبت‌نام تا همکاری با معلم",
               "بچه‌هایتان کجا درس می‌خوانند و تجربه‌تان از ثبت‌نام و همکاری با مدرسه چه بوده؟ سؤال‌هایتان دربارهٔ مدرسهٔ عادی، "
               "تلفیقی یا استثنایی را هم همین‌جا بپرسید."),
    "family": ("خودتان چطورید؟",
               "مراقبت از کودکِ طیف خسته‌کننده است و حالِ خودتان هم مهم است. این‌جا بدونِ قضاوت از خستگی، نگرانی یا چیزهایی که "
               "حالتان را بهتر می‌کند بنویسید. اگر بخواهید می‌توانید ناشناس بنویسید."),
    "rights": ("مراکزی که می‌شناسید را معرفی کنید",
               "کدام مرکزِ تشخیص یا توان‌بخشی در شهرتان را تجربه کرده‌اید؟ شهر، نوعِ خدمت و تجربه‌تان را بنویسید تا فهرستِ مراکز "
               "کامل‌تر شود.\n\nبرای سؤال دربارهٔ خدماتِ بهزیستی می‌توانید با خطِ مشاورهٔ «صدای مشاور» بهزیستی (۱۴۸۰) تماس بگیرید."),
    "heart": ("یک موفقیتِ کوچکِ این هفته",
              "اولین کلمه، یک شبِ خوابِ راحت، یک بار دستشویی رفتنِ موفق… هیچ موفقیتی کوچک نیست. خوشحالی‌تان را با ما قسمت کنید 💚"),
}

PLACES = [
    ("r_seed_ctad", {"name": "مرکز تهران اتیسم (CTAD)", "type": "تشخیص و توان‌بخشی", "city": "تهران",
                     "address": "امیرآباد شمالی، بالاتر از چهارراه جلال آل احمد، خیابان هفتم، نبشِ بن‌بستِ چهارم",
                     "phone": "۰۲۱-۸۸۶۳۳۷۱۵",
                     "note": "ارزیابی و تشخیص، مداخلهٔ زودهنگام و ABA. منبع: سایتِ رسمیِ ctad.ir — پیش از مراجعه تلفنی هماهنگ کنید."}),
    ("r_seed_1480", {"name": "صدای مشاورِ بهزیستی", "type": "مشاورهٔ تلفنی", "city": "سراسرِ کشور", "address": "",
                     "phone": "۱۴۸۰", "note": "مشاوره و راهنماییِ خدماتِ بهزیستی."}),
    ("r_seed_123", {"name": "اورژانسِ اجتماعی", "type": "خطِ اضطراری", "city": "سراسرِ کشور", "address": "",
                    "phone": "۱۲۳", "note": "برای بحران‌های خانوادگی و اجتماعی."}),
]


def main():
    H.community_init()
    store = H.Store()
    admin = next((u for u in store.all("users") if u.get("isAdmin")), None)
    if not admin:
        sys.exit("no admin account")
    now = H.now_ms()
    arts = json.load(open(os.path.join(HERE, "seed_articles_v14.json"), encoding="utf-8"))
    for i, a in enumerate(arts):
        H._x("INSERT INTO l_items(id,kind,category_id,title,summary,body,author_id,status,featured,created_at,updated_at,published_at) "
             "VALUES(%s,'article',%s,%s,%s,%s,%s,'PUBLISHED',false,%s,%s,%s) ON CONFLICT(id) DO NOTHING",
             ("l_v14_%02d" % i, a["cat"], a["title"], a["summary"], a["body"], admin["id"], now - i * 60000, now, now - i * 60000))
    for fid, (title, body) in STARTERS.items():
        H._x("INSERT INTO f_topics(id,forum_id,author_id,title,body,pinned,created_at,last_post_at,last_post_by) "
             "VALUES(%s,%s,%s,%s,%s,true,%s,%s,%s) ON CONFLICT(id) DO NOTHING",
             ("t_v14_" + fid, fid, admin["id"], title, body, now, now, admin["id"]))
    for rid, d in PLACES:
        if not store.get("r_place", rid):
            store.put("r_place", dict(d, id=rid, ownerId=admin["id"], ownerName=admin.get("displayName", ""),
                                      status="ACTIVE", createdAt=now, updatedAt=now))
    print("seeded v14: %d articles, %d starter topics, %d places" % (len(arts), len(STARTERS), len(PLACES)))


if __name__ == "__main__":
    main()
