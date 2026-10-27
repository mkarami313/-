# اتصال واقعی به Firebase (قدم‌به‌قدم)

برنامه طوری ساخته شده که با وجود `google-services.json`، **به‌صورت خودکار** حالت واقعی
(`BACKEND=FIREBASE`) فعال می‌شود؛ بدون فایل، همان حالت DEMO اجرا می‌شود.

پروژهٔ شما: `outism-efb94` — package اپ: `com.hamraheteyf.app`.

## ۱) در کنسول Firebase
1. **Authentication → Sign-in method → Email/Password → Enable.**
2. **Firestore Database → Create database** (حالت Production). منطقه را نزدیک کاربران انتخاب کنید.
3. **قوانین امنیتی را منتشر کنید:**
   - محتوای `backend/firestore.rules` را در **Firestore → Rules** جای‌گذاری و **Publish** کنید.
   - اگر بعداً بارگذاری مدرک را فعال کردید، `backend/storage.rules` را هم در **Storage → Rules** منتشر کنید.
4. (اختیاری) **Cloud Messaging** برای اعلان‌های Push — برای نسخهٔ فعلی لازم نیست.

## ۲) دادن کانفیگ به CI (امن، بدون قراردادن در کد)
`google-services.json` یک API key دارد؛ آن را داخل مخزن commit نمی‌کنیم. در عوض:

- در GitHub: **Settings → Secrets and variables → Actions → New repository secret**
- نام سکرت: `GOOGLE_SERVICES_JSON`
- مقدار: **کل محتوای فایل `google-services.json`** را کپی و جای‌گذاری کنید.

از این به بعد، هر build در CI فایل را می‌سازد و APK را در حالت **FIREBASE** می‌سازد. (بدون این سکرت، APK در حالت DEMO ساخته می‌شود.)

> اجرای محلی در Android Studio: کافی است همان فایل `google-services.json` را در پوشهٔ `app/`
> قرار دهید. (این فایل در `.gitignore` است و commit نمی‌شود.)

## ۳) ساخت نقش مدیر (برای پنل مدیریت)
قابلیت‌های مدیر به یک **custom claim** به‌نام `admin=true` وابسته‌اند که فقط از سمت سرور ست می‌شود.
یک‌بار برای حساب مدیر خودتان این را اجرا کنید (با Firebase Admin SDK؛ مثلاً یک اسکریپت Node محلی):

```js
// setAdmin.js  — با سرویس‌اکانت پروژه اجرا کنید (فایل کلید را در مخزن نگذارید)
const admin = require("firebase-admin");
admin.initializeApp({ credential: admin.credential.cert(require("./serviceAccountKey.json")) });
admin.auth().getUserByEmail("ایمیل-مدیر@example.com")
  .then(u => admin.auth().setCustomUserClaims(u.uid, { admin: true }))
  .then(() => console.log("admin claim set"))
  .catch(console.error);
```
سپس آن کاربر یک‌بار از برنامه خارج و دوباره وارد شود تا claim اعمال شود.

## ۴) تست
1. سکرت را اضافه کنید و یک build تازه بزنید (push یا اجرای دستی workflow).
2. APK را از Artifacts بگیرید و نصب کنید.
3. **ثبت‌نام** کنید؛ باید یک کاربر در **Authentication** و یک سند در مجموعهٔ `users` ساخته شود.
4. پرسش/گروه بسازید و در کنسول Firestore ببینید که اسناد ایجاد می‌شوند.

## نکته‌های مهندسی (برای تولید)
- در این نسخه، شمارنده‌ها (تعداد پاسخ/عضو/مفید) و اعلان‌های بین‌کاربری **سمت کلاینت** نوشته می‌شوند و
  قوانین امنیتی این موارد را به‌صورت محدود اجازه می‌دهند. برای استحکام بیشتر، این‌ها را به **Cloud Functions**
  منتقل کنید.
- فهرست‌ها فعلاً سمت کلاینت فیلتر/مرتب می‌شوند تا نیازی به ایندکس ترکیبی نباشد؛ برای داده‌های زیاد،
  کوئری‌های سمت سرور + صفحه‌بندی + ایندکس اضافه کنید.
- حذف کامل داده‌های وابسته هنگام حذف حساب بهتر است با یک Cloud Function روی رویداد حذف کاربر انجام شود؛
  نسخهٔ فعلی حذف را best-effort سمت کلاینت انجام می‌دهد.
