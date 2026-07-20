# Compose / AndroidX / Play Billing / Coil ship consumer rules of their own.

# osmdroid ships no consumer rules; keep its public surface so the MapView,
# tile providers and overlays survive R8 (reflection-adjacent configuration).
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
