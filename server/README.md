# سرور همراهِ طیف (backend خودمیزبان)

جایگزینِ backendِ Firebase. یک فایلِ پایتونِ **بدونِ وابستگیِ غیراستاندارد جز `psycopg2`** که
همهٔ دادهٔ برنامه را در PostgreSQL نگه می‌دارد و همان قواعدِ دسترسیِ لایهٔ `fake` (منبعِ حقیقتِ
دسترسی‌ها) را این‌بار **سمتِ سرور** اجرا می‌کند.

## معماری

- ذخیره‌سازی: جدولِ `docs(collection, id, data jsonb)` — هر «کالکشن» (users, questions, …) مثلِ
  Firestore، و جدولِ `creds` برای ایمیل/رمز (هشِ PBKDF2-SHA256 با salt).
- احراز هویت: JWT دستیِ HS256 (رازش در `.env`)، توکنِ Bearer.
- کلاینت یک **آینهٔ محلی** از snapshotِ سرور نگه می‌دارد تا منطقِ reactiveِ موجودِ UI بدونِ تغییر
  کار کند؛ هر تغییر (action) به سرور POST می‌شود و snapshotِ تازه برمی‌گردد.

## پروتکل (همه JSON)

| متد و مسیر | کار |
|---|---|
| `POST /api/auth/register` | ثبت‌نام → `{token, user}` |
| `POST /api/auth/login` | ورود → `{token, user}` |
| `POST /api/auth/reset` | فراموشی رمز (همیشه `{ok:true}`) |
| `GET  /api/state` | snapshotِ محدودشده به چیزی که کاربر مجاز به دیدنش است |
| `POST /api/action` | `{name, args}` → اجرای یک عملیات و برگرداندنِ snapshotِ تازه |
| `GET  /api/health` | سلامت |

نامِ `action`ها دقیقاً مثلِ متدهای repository است: `createQuestion`, `joinOrRequest`,
`reviewVerification`, … (فهرست کامل در `ACTIONS` داخلِ `hamrah-api.py`).

## استقرار (روی `tizbin`)

```bash
# ۱) دیتابیس
sudo -u postgres psql -c "CREATE DATABASE rasta_hamrah OWNER rasta_office"

# ۲) پیکربندی  /home/rasta/hamrah/.env
DB_DSN=dbname=rasta_hamrah user=rasta_office password=… host=127.0.0.1 port=5432
JWT_SECRET=<۳۲ بایت تصادفی>        # الزامی؛ بدونِ آن (یا کوتاه‌تر از ۳۲ نویسه) سرویس بالا نمی‌آید
PORT=8150
# ایمیلِ بازیابی رمز (اختیاری؛ خالی = ارسال غیرفعال و فقط adminIssueReset کار می‌کند)
SMTP_HOST=
SMTP_PORT=587                       # 465 = SSL مستقیم، غیر از آن STARTTLS
SMTP_USER=
SMTP_PASS=
SMTP_FROM=

# ۳) جدول‌ها + دادهٔ اولیهٔ راهنماها
python3 hamrah-api.py init-db

# ۴) ساختِ حسابِ مدیر (custom-claim معادل: فیلدِ isAdmin روی سندِ کاربر)
python3 hamrah-api.py create-admin <ایمیل> <رمز> "نامِ نمایشی"

# ۵) سرویس
systemctl enable --now hamrah        # واحد: /etc/systemd/system/hamrah.service
```

nginx روی `proj.tizbin.org` یک location دارد: `location /hamrah/ → http://127.0.0.1:8150/`.

## نقشِ مدیر

کاربری مدیر است که سندِ `users/{uid}` او `isAdmin: true` داشته باشد. فقط `create-admin`
(یا ویرایشِ مستقیمِ دیتابیس) این را ست می‌کند؛ ثبت‌نامِ عادی همیشه `isAdmin=false` است و کاربر
نمی‌تواند خودش را ارتقا دهد.

## نسخهٔ ۰٫۱۴ (سرور)

قراردادِ کاملِ اکشن‌ها (نام، آرگومان، شکلِ پاسخ، خطاهای فارسی) در `SERVER_CONTRACT.md` است. خلاصه:

* **شکستنیِ کلاینت‌های قدیمی:** `deleteChild` به `confirm:true` و `deleteAccount` به `password` نیاز دارد.
* **فایل‌ها:** `fileUrls{ids}` لینکِ امضاشدهٔ ۲۴ساعته (`files/<id>?exp=&sig=`) می‌دهد؛ `/api/upload`
  هم `signedUrl` برمی‌گرداند. متغیرِ محیطیِ `FILES_ENFORCE=1` لینکِ بدونِ امضا/توکن را رد می‌کند
  (پیش‌فرض خاموش تا کلاینت‌های ۰٫۱۳ کار کنند). ادمین هر فایلی (از جمله مدارکِ احراز) را می‌بیند.
* **جاروکنندهٔ یتیم‌ها** هر شناسهٔ ۳۲هگزی را که در هر سندی آمده باشد (documentRefs، photoId کاربر و متخصص، …) نگه می‌دارد.
* حساب‌های `LIMITED` فقط خواندن/حذفِ داده‌های خودشان؛ `BLOCKED` توکن‌ها را فوراً باطل می‌کند.
* `auditLog` (فقط ادمین) آخرین ۲۰۰ ثبتِ ممیزیِ سمتِ سرور را نشان می‌دهد.
* تست: `python3 test_smoke.py` روی خودِ سرور (به psycopg2 و دیتابیس نیاز دارد). بخشِ فایل‌ها هر دو حالتِ
  `FILES_ENFORCE` را آزمایش می‌کند؛ برای حالتِ روشن یک نمونهٔ موقت روی پورت ۸۱۵۹ بالا می‌آورد.

## به‌روزرسانی

ویرایشِ محلی → `rsync hamrah-api.py tizbin:/home/rasta/hamrah/` → `ssh tizbin systemctl restart hamrah`.

## بازیابی رمز

* `POST /api/auth/forgot {email}` همیشه `{ok:true}` می‌دهد (وجودِ حساب لو نمی‌رود). اگر حساب هست
  و SMTP تنظیم است، کدِ ۸ رقمیِ یک‌بارمصرف (۳۰ دقیقه، هش‌شده، حداکثر ۵ تلاش) ایمیل می‌شود.
* `POST /api/auth/reset {email,code,newPassword}` رمز را عوض و همهٔ نشست‌های قبلی را باطل می‌کند.
* بدونِ SMTP: مدیر با اکشنِ `adminIssueReset` کد را می‌گیرد و به کاربر می‌رساند.
* آزمایشِ ارسال: `python3 hamrah-api.py mailtest <گیرنده>`.
* سرور tizbin MTA محلی ندارد؛ پورت‌های خروجیِ 25/465/587 باز است، پس یک رله با نام‌کاربری/رمز لازم است.

## عملیات

* لاگ دسترسی در journald (بدونِ query؛ شناسهٔ فایل‌ها ماسک)، خطاها با traceback در لاگ و پیامِ عمومی به کلاینت.
* فایل‌های بی‌صاحب (آپلودی که هیچ سندی به آن اشاره نکند، قدیمی‌تر از ۲۴ ساعت) هنگامِ شروع و روزانه جارو
  می‌شوند؛ دستی: `python3 hamrah-api.py sweep [ساعت]`.
* پشتیبان شبانه (`/home/rasta/backup-all-to-server.sh`): دامپِ `rasta_hamrah` + `uploads/` + `.env` +
  `fcm_sa.json` به `185.8.173.151:/tizbin/` (بدونِ `--delete`).
* تست دودی: `cd /home/rasta/hamrah && python3 test_smoke.py` (کاربرانِ موقت می‌سازد و پاک می‌کند). ثبت‌نامِ
  هر IP ساعتی ۱۰ بار محدود است؛ بعد از چند اجرا سرویس را ری‌استارت کن.
