# اجرای پروژه در Android Studio

## پیش‌نیازها
- **Android Studio** (نسخهٔ جدید؛ هم‌خوان با Android Gradle Plugin 8.7).
- **JDK 17** (در Android Studio: Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK = 17).
- **Android SDK** با Platform **API 35** و Build-Tools متناظر.
- دسترسی به اینترنت در اولین build برای دانلود وابستگی‌ها (از `google` و `mavenCentral`).

## گام‌به‌گام
۱. مخزن را باز کنید: `File → Open` و پوشهٔ ریشهٔ پروژه را انتخاب کنید.
۲. اجازه دهید Gradle Sync کامل شود. (اگر خطای نسخهٔ SDK دیدید، از طریق SDK Manager، API 35 را نصب کنید.)
۳. یک دستگاه مجازی (AVD) با API ≥ 24 بسازید یا یک گوشی واقعی را وصل کنید.
۴. پیکربندی اجرا را روی ماژول `app` بگذارید و **Run ▶** را بزنید.
۵. برنامه در **حالت نمایشی (DEMO)** بالا می‌آید؛ نیازی به هیچ کلید یا سرویس بیرونی نیست.

## حساب‌های نمونه (حالت DEMO)
رمز عبور همهٔ حساب‌ها: `demo1234`

| نقش | ایمیل |
|---|---|
| خانواده | `zahra@example.com` |
| خانوادهٔ دیگر | `parisa@example.com` |
| متخصص تأییدشده | `dr.verified@example.com` |
| متخصص در انتظار بررسی | `newpro@example.com` |
| مربی تأییدشده | `teacher@example.com` |
| عضو جامعه | `volunteer@example.com` |
| مدیر (پنل مدیریت) | `admin@example.com` |

می‌توانید با «ثبت‌نام» هم یک حساب تازه بسازید؛ داده‌ها تا زمانی که برنامه در حافظه است باقی می‌مانند (حالت DEMO داده را روی دستگاه ذخیره نمی‌کند).

## اجرای آزمون‌ها
- در Android Studio: روی `RepositoriesTest` راست‌کلیک → Run.
- از خط فرمان: `./gradlew testDebugUnitTest`

## نکته دربارهٔ فونت فارسی
برنامه از فونت پیش‌فرض سیستم استفاده می‌کند که فارسی را درست نمایش می‌دهد. برای فونت اختصاصی
(مثلاً Vazirmatn)، فایل‌های `.ttf` را در `app/src/main/res/font/` قرار دهید و `AppFontFamily`
را در `ui/theme/Type.kt` به آن تغییر دهید.
