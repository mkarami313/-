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
JWT_SECRET=<۳۲ بایت تصادفی>
PORT=8150

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

## به‌روزرسانی

ویرایشِ محلی → `rsync hamrah-api.py tizbin:/home/rasta/hamrah/` → `ssh tizbin systemctl restart hamrah`.
