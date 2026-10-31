# Release minification is OFF (isMinifyEnabled=false) and has never been verified on a device, so these
# rules are only a starting point. Before enabling it, test login, sync, biometrics, TTS, reminders and
# the updater on a real phone; kotlinx.serialization models in data/model need keep rules at that point.

# --- Keep-rules audit (0.14.0; minify still OFF by decision — do not enable without device testing) ---
# Things that would break under R8 and need rules when/if it is turned on:
#  - kotlinx.serialization: data/model/*, data/repository/fake/Db, net/* DTOs are decoded via generated
#    serializers -> -keepattributes *Annotation*, InnerClasses; -keep,includedescriptorclasses class **$$serializer { *; }
#    and keep companion serializer() methods of every @Serializable class.
#  - Manifest components are kept automatically (ReminderReceiver, BootReceiver, FCM service, MainActivity).
#  - org.json (platform), HttpURLConnection, MediaPlayer, ToneGenerator, AlarmManager: platform classes, no rules.
#  - Firebase Messaging / Play services ship consumer rules; AndroidX Biometric/Navigation/Compose too.
#  - Reflection/JNI: none in app code (FileUrls, Reminders, RemoteImage are plain Kotlin).
#  - src/demo (SeedData, FakeAuthRepository, DemoAppContainer) is excluded from release by source set, not by R8.
