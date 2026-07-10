# R8/ProGuard ルール。Compose・AndroidX・Media3・Coil・Play Billing は
# それぞれ consumer rules を同梱しているため基本は自動で保たれるが、
# リフレクションで参照される箇所を明示的に keep して安全側に倒す。

# --- Play Billing -------------------------------------------------------
# ライブラリの consumer rules で必要分は保たれるが、モデル/コールバックを明示keep
-keep class com.android.billingclient.api.** { *; }

# --- WorkManager Workers ------------------------------------------------
# Worker はクラス名からリフレクションで生成されるため難読化・削除しない
-keep class com.souru.lumina.work.** { *; }

# --- Media3 (ExoPlayer / Transformer / Effect) --------------------------
# エフェクト/トランスフォーマは一部GL/効果クラスをリフレクション参照する。
# 動画LUT・書き出しの中核なので安全側で保持する
-keep class androidx.media3.effect.** { *; }
-keep class androidx.media3.transformer.** { *; }

# --- スタックトレース ---------------------------------------------------
# crash.txt を実機で読むため、行番号を残して意味のあるトレースにする
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
