# こよみ — R8 / ProGuard rules.
#
# Compose, AndroidX, Glance and WorkManager ship their own consumer rules, so
# the surface we must protect ourselves is small: only classes instantiated
# reflectively by name that R8 cannot see being constructed.

# WorkManager instantiates workers by their fully-qualified class name.
-keep class com.souru.koyomi.data.CalendarSyncWorker { <init>(...); }
-keep class com.souru.koyomi.notifications.TaskReminderWorker { <init>(...); }
-keep class com.souru.koyomi.widget.WidgetUpdateWorker { <init>(...); }

# Glance app-widget receivers and the widget configuration activity are named
# in AndroidManifest.xml (so kept), but keep the GlanceAppWidget subclasses
# they reference explicitly for safety across R8 versions.
-keep class com.souru.koyomi.widget.**Widget { *; }
-keep class com.souru.koyomi.widget.**WidgetReceiver { *; }

# Line-number info for readable Play Console crash reports; hide the source
# file name itself.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
