# ساخت APK آزمایشی

## APK دیباگ (ساده‌ترین، برای آزمایش)
این APK با کلید دیباگِ خودکار امضا می‌شود و برای نصب روی دستگاه آزمایشی کافی است.

```
./gradlew assembleDebug
```
خروجی: `app/build/outputs/apk/debug/app-debug.apk`

نصب روی دستگاه متصل:
```
./gradlew installDebug
# یا
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> APK دیباگ در **حالت DEMO** اجرا می‌شود و به هیچ سرویسی وصل نیست.

## APK ریلیز (امضاشده)
۱. یک keystore بسازید (یک‌بار):
```
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias hamrah
```
> فایل‌های `*.jks`/`*.keystore` در `.gitignore` هستند و نباید commit شوند.

۲. اطلاعات امضا را **خارج از کد** نگه دارید. یک فایل `keystore.properties` (خارج از VCS) بسازید:
```
storeFile=/path/to/release.jks
storePassword=...
keyAlias=hamrah
keyPassword=...
```
۳. در `app/build.gradle.kts` یک `signingConfig` اضافه کنید که این مقادیر را از `keystore.properties`
می‌خواند (نه از کد). سپس:
```
./gradlew assembleRelease
```
خروجی: `app/build/outputs/apk/release/app-release.apk`

## نکته
- برای انتشار در Google Play از **App Bundle** استفاده کنید: `./gradlew bundleRelease`.
- پیش از ریلیز واقعی، `BACKEND` را روی `FIREBASE` بگذارید و `google-services.json` واقعی را اضافه کنید
  (راهنما: `docs/BACKEND_SETUP.md`).
