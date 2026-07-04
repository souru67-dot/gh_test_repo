# Lumina

Xperia 1 VIII 向けギャラリーアプリ。旧Xperia「アルバム」の快適な操作感を、Material 3 ベースの黒基調ミニマルデザインで再構築する。RAW(DNG)+JPEG 同時撮影と S-Cinetone for Mobile 撮影の映像ワークフローを前提にした、写真・映像クリエイターのためのライブラリ。

## 技術スタック

- Kotlin / Jetpack Compose + Material 3(常時ダークテーマ・黒背景)
- MediaStore API(READ_MEDIA_IMAGES / READ_MEDIA_VIDEO、Android 14+ の一部許可対応)
- Coil 3(MediaStore サムネイルキャッシュ利用の高速フェッチャー)
- Media3(ExoPlayer / Transformer / effect)
- MVVM + StateFlow、編集は非破壊(別名保存)
- minSdk 33 / targetSdk 35

## モジュール構成

```
app/src/main/java/com/souru/lumina/
├── LuminaApplication.kt      # AppContainer(手動DI)+ Coil ImageLoader 構成
├── MainActivity.kt
├── data/
│   ├── model/Models.kt       # MediaItem / GalleryEntry / GridSlot / RawFilterMode
│   ├── MediaRepository.kt    # MediaStore クエリ + ContentObserver Flow
│   ├── SettingsRepository.kt # DataStore(列数・表示モード)
│   ├── coil/                 # MediaStore サムネイル / DNG 埋め込みプレビューの Coil Fetcher
│   ├── pairing/              # RAW+JPEG ペアリング(純Kotlin・テスト付き)
│   ├── edit/                 # AGSL 調整シェーダー / 非破壊 JPEG 書き出し(Exif引き継ぎ)
│   └── luts/                 # .cube パーサー / LUTベイク / LUTライブラリ管理
├── ui/
│   ├── theme/                # 黒基調 M3 テーマ
│   ├── AppNavHost.kt
│   ├── onboarding/           # 権限の説明とリクエスト
│   ├── gallery/              # 日付グルーピングのグリッド、ピンチで列数変更、スクラバー
│   ├── viewer/               # 没入ビューア(ズーム・下スワイプで閉じる・RAW⇔JPEG切替・動画再生)
│   ├── photoedit/            # 写真簡易編集(GPUリアルタイム調整・トリミング・回転)
│   └── videoedit/            # 動画編集(LUT+強度・A/B比較・簡易調整・トリム・書き出し)
├── work/                     # Transformer 書き出しの WorkManager Worker(進捗通知)
└── util/                     # 日付フォーマット・権限・Lightroom 連携ヘルパー
```

## 主な機能

- **グリッド**: 日付ヘッダーでグルーピング、ピンチイン/アウトで 5⇔4⇔3⇔2 列を滑らかに変更(旧Xperiaアルバム風)、右端に日付バブル付き高速スクラバー
- **RAW+JPEG**: ファイル名+撮影日時近接でペアリング。JPEG / RAW / すべて の表示モードを常設、RAW+J / RAW バッジ、DNGは埋め込みプレビューで高速表示
- **ビューア**: 共有要素トランジションで没入表示。ピンチ/ダブルタップズーム、下スワイプで閉じる、同一構図のまま RAW⇔JPEG 切替、UI非表示時はシステムバーも隠す
- **Lightroom連携**: 「Lrで現像」でペアのRAW側を ACTION_EDIT 起動(SENDフォールバック)。複数選択の一括送信、未インストール時はPlayストア誘導
- **写真編集**: AGSL RuntimeShader による11項目のリアルタイムGPU調整+トリミング/回転。保存は常に別名(Pictures/Lumina)で非破壊、Exif引き継ぎ
- **動画編集**: S-Cinetone for Mobile などLog素材向けに .cube 3D LUT を ExoPlayer プレビューへリアルタイム適用。強度スライダー(0〜100%)、A/B比較、簡易調整、トリム。Transformer + WorkManager でバックグラウンド書き出し(解像度/ビットレート選択、進捗通知、Movies/Lumina へ保存)

## 実装フェーズ

- [x] フェーズ1: MediaStore 読み込み、日付グルーピングのグリッド(ピンチで 5⇔4⇔3⇔2 列)、高速スクラバー、没入ビューア(写真ズーム・動画再生)、権限オンボーディング
- [x] フェーズ2: RAW+JPEG ペアリング、JPEG/RAW/すべて表示切替、RAW+J バッジ、DNG 埋め込みプレビュー高速表示、ビューアの RAW⇔JPEG 切替
- [x] フェーズ3: Lightroom Mobile 連携(単体・複数選択)、写真の簡易編集(AGSL)と非破壊保存
- [x] フェーズ4: 動画への 3D LUT(.cube)リアルタイム適用+強度スライダー、簡易調整、Transformer 書き出し、トリム
- [x] フェーズ5: 共有要素トランジション、没入システムバー制御、スクロール/再構成の最適化

## ビルド

```
./gradlew assembleDebug
```

JDK 17 と Android SDK(compileSdk 35)が必要。CI(GitHub Actions)でユニットテストと debug APK のビルドを行う。
