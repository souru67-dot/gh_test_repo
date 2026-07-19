# ColorHunt

SNSで流行中の「カラーハンティング」（テーマ色を決めて街で撮り、まとめて投稿する遊び）に
特化した Android ネイティブアプリ。写真をドミナントカラーで自動仕分けし、コラージュにして
SNS 用に書き出し／共有します。

> 後の英語圏・アジア圏展開と iOS 版を見据え、文字列は英語をデフォルト（`values/`）＋日本語
> （`values-ja/`）で用意し、色分類などのコアロジックは Android 非依存の純 Kotlin に切り出して
> あります（将来 KMP / iOS へ移植しやすくするため）。

## 技術スタック

- Kotlin / Jetpack Compose / Material 3（Dynamic Color 対応）
- MVVM + 単方向データフロー（ViewModel + StateFlow）
- minSdk 26 / targetSdk 35 / Gradle Kotlin DSL
- 写真取り込み: **Photo Picker**（`PickMultipleVisualMedia`）— ストレージ権限不要
- ドミナントカラー抽出: `androidx.palette:palette-ktx`（バックグラウンド＋LRUキャッシュ）
- Exif（回転・GPS）: `androidx.exifinterface`
- 画像読み込み: Coil
- 保存: `MediaStore` / 共有: `FileProvider` + `ACTION_SEND`

## フェーズ進捗

| Phase | 内容 | 状態 |
|------|------|------|
| 1 | 取り込み・色仕分け・コラージュ・書き出し | 実装済み |
| 2 | Instagram グリッドプレビュー | 実装済み |
| 3 | カラーマップ（osmdroid） | 実装済み |
| 4 | お題ルーレット / Play Billing | seam のみ（`domain/pro`, `domain/roulette`, `FeatureFlags.isPro`） |

## モジュール構成（Phase 1）

```
app/src/main/java/com/souru/colorhunt/
├── ColorHuntApplication.kt / AppContainer.kt   # 手動DIコンテナ
├── MainActivity.kt
├── domain/                                      # Android非依存のコア
│   ├── color/  Hsv / ColorBucket / ColorClassifier   # HSV→バケツ分類（定数化）
│   ├── config/ SnsSize / CollagePresets / FeatureFlags
│   └── model/  HuntPhoto
├── data/
│   ├── PaletteExtractor.kt   # Palette抽出（コルーチン＋キャッシュ）
│   ├── PhotoRepository.kt    # 選択写真の共有ストア（並列解析・選択保持）
│   ├── BitmapLoader.kt       # Exif回転込みのダウンサンプリング読み込み
│   └── export/  CollageRenderer / ImageExporter / ShareHelper
└── ui/
    ├── theme/               # M3ダークテーマ＋ブランドグラデーション
    ├── AppNavHost.kt        # 下部ナビ（Sort / Collage / Grid / Map）
    ├── imports/  SortScreen / SortViewModel      # 取り込み・色仕分け・自動仕分け
    ├── collage/  CollageScreen / CollageViewModel # コラージュ
    ├── grid/     GridPreviewScreen / ...VM        # Phase2 グリッドプレビュー
    └── map/      MapScreen / MapViewModel         # Phase3 カラーマップ
```

Phase 4 の seam は `domain/pro`（ProEntitlement / PlayBillingスタブ）と
`domain/roulette`（DailyColorRoulette / 通知スタブ）に配置。

## 色分類の仕様

各写真のドミナントカラーを HSV に変換し、以下のバケツへ自動仕分けします。
先に彩度・明度で無彩色（白／黒／グレー）を分離し、残りを色相で分類します。

`赤 / 橙 / 黄 / 黄緑 / 緑 / 水色 / 青 / 紫 / ピンク` ＋ `白 / 黒 / グレー`

バケツ名・境界値はすべて `domain/color/ColorClassifier.kt`（`ColorClassifierConfig`）と
`ColorBucket.kt` に定数化してあり、後から調整できます。

## Phase 1 でできること

- **仕分け画面**: Photo Picker で複数選択 → 各写真のドミナントカラーを抽出 → 色バケツごとに
  グリッド表示。上部の色チップでテーマ色フィルタ、各サムネイルに抽出色ドット。
  取得できない写真は「未分類」に分け、クラッシュしない。
- **コラージュ画面**: 枚数プリセット（3/4/6/9/カスタム）、SNSサイズ（1:1 / 4:5 / 9:16 / 16:9）、
  セル間余白・角丸・枠線・背景色（テーマ色連動可）、色相グラデーション自動整列。
  「保存」（MediaStore）と「共有」（FileProvider + 共有シート）。書き出し解像度はサイズプリセット準拠。
- **Phase 4 seam**: `FeatureFlags.isPro` で透かし・SNSサイズ・枚数上限のゲート分岐を用意。

## ビルド

```bash
./gradlew assembleDebug        # デバッグAPK
./gradlew test                 # 単体テスト（色分類ロジック）
```

Android SDK（platform 35 / build-tools）と Google Maven リポジトリへのアクセスが必要です。

CI（GitHub Actions / `.github/workflows/android.yml`）で毎プッシュ `testDebugUnitTest` と
`assembleDebug` を実行し、デバッグ APK（アーティファクト名 `colorhunt-debug-apk`）を生成します。

## iOS / 多言語展開に向けて

- 色分類などのコアロジックは Android 非依存の純 Kotlin（`domain/color`）に切り出し済みで、
  将来 Kotlin Multiplatform の共有モジュールへ移しやすい構成。
- 文字列は英語デフォルト＋日本語。アジア圏展開時はロケール追加のみで対応。
