# اتصال به backend واقعی (Firebase)

برنامه به‌صورت پیش‌فرض در **حالت DEMO** (دادهٔ درون‌حافظه‌ای) اجرا می‌شود. برای اتصال به یک
backend واقعی، معماری طوری طراحی شده که فقط لایهٔ repository جایگزین شود و UI/ViewModel دست‌نخورده بماند.

> هیچ کلید یا رمز واقعی نباید داخل کد یا مخزن قرار گیرد. فایل `app/google-services.json`
> در `.gitignore` است و نباید commit شود.

## ۱. ساخت پروژهٔ Firebase
1. در [Firebase Console](https://console.firebase.google.com) یک پروژه بسازید.
2. یک اپ Android با package name دقیقِ `com.hamraheteyf.app` اضافه کنید.
3. فایل `google-services.json` را دانلود و در `app/google-services.json` قرار دهید
   (می‌توانید از `app/google-services.json.sample` به‌عنوان الگو استفاده کنید).

## ۲. فعال‌کردن سرویس‌ها در کنسول
- **Authentication:** روش ورود با ایمیل/رمز (و در صورت نیاز شماره تلفن) را فعال کنید.
- **Firestore Database:** یک دیتابیس بسازید و قوانین `backend/firestore.rules` را منتشر کنید.
- **Storage:** فقط اگر بارگذاری مدرک تخصصی را فعال می‌کنید؛ قوانین `backend/storage.rules` را منتشر کنید.
- **Cloud Messaging (FCM):** برای اعلان‌ها.

## ۳. فعال‌کردن وابستگی‌ها در Gradle
در `gradle/libs.versions.toml` بخش Firebase از قبل تعریف شده است. کافی است:
- در `build.gradle.kts` (ریشه) و `app/build.gradle.kts`، خطوط مربوط به پلاگین
  `com.google.gms.google-services` و وابستگی‌های Firebase (که به‌صورت کامنت گذاشته شده‌اند) را فعال کنید.
- در `app/build.gradle.kts` مقدار `buildConfigField("String", "BACKEND", "\"DEMO\"")`
  را به `"\"FIREBASE\""` تغییر دهید.

## ۴. پیاده‌سازی repositoryهای Firebase
یک کلاس `FirebaseAppContainer : AppContainer` بسازید که همان واسط‌های موجود در
`data/repository/Repositories.kt` را با Firebase پیاده می‌کند (Auth، Firestore، Storage، FCM).
سپس در `di/AppContainer.kt` تابع `createAppContainer()` را برای شاخهٔ `"FIREBASE"` به آن وصل کنید.

نگاشت پیشنهادی مجموعه‌ها: `users`, `professionals`, `verifications`, `children`, `questions`,
`answers`, `groups`, `memberships`, `posts`, `conversations`, `messages`, `guides`, `reports`,
`notifications`, `adminActions` — دقیقاً مطابق قواعد دسترسی در `backend/firestore.rules`.

## ۵. نقش مدیر
«مدیر» با یک **custom claim** به‌نام `admin=true` مشخص می‌شود که فقط از سمت سرور
(Cloud Function یا Admin SDK) تنظیم می‌شود؛ هرگز از سمت کلاینت. قوانین امنیتی به همین claim تکیه دارند.

## ۶. آزمودن قوانین
پیش از استفادهٔ واقعی، قوانین را با **Firebase Emulator Suite** آزمایش کنید:
```
firebase emulators:start --only firestore,storage,auth
```
