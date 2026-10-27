# Default ProGuard rules. The demo build does not minify, so these are a
# starting point for a real release build only.
# Keep data model classes used for (future) Firestore (de)serialization.
-keep class com.hamraheteyf.app.data.model.** { *; }
