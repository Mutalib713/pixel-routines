# Shizuku's newProcess is reached by reflection (see ShizukuBridge.exec), so R8 must not
# rename or strip it.
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# osmdroid loads tile sources and config reflectively.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Play services location (geofencing).
-dontwarn com.google.android.gms.**

# Routines are persisted as JSON holding enum *names* ("SILENT", "REVERT", ...) and read
# back with valueOf(). If R8 renamed the constants, saved routines from an older build
# would fail to parse — and Store.load()'s catch would silently drop every routine.
# Keeping all enum members pins the names so stored data always round-trips.
-keepclassmembers enum com.mosman.routines.** { *; }
