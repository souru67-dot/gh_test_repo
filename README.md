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
│   └── coil/                 # MediaStore サムネイル用 Coil Fetcher
├── ui/
│   ├── theme/                # 黒基調 M3 テーマ
│   ├── AppNavHost.kt
│   ├── onboarding/           # 権限の説明とリクエスト
│   ├── gallery/              # 日付グルーピングのグリッド、ピンチで列数変更、スクラバー
│   └── viewer/               # 没入ビューア(ズーム・下スワイプで閉じる・動画再生)
└── util/                     # 日付フォーマット・権限ヘルパー
```

## 実装フェーズ

- [x] フェーズ1: MediaStore 読み込み、日付グルーピングのグリッド(ピンチで 5⇔4⇔3⇔2 列)、高速スクラバー、没入ビューア(写真ズーム・動画再生)、権限オンボーディング
- [ ] フェーズ2: RAW+JPEG ペアリング、JPEG/RAW/すべて表示切替、RAW+J バッジ、DNG 埋め込みプレビュー高速表示、ビューアの RAW⇔JPEG 切替
- [ ] フェーズ3: Lightroom Mobile 連携(単体・複数選択)、写真の簡易編集(AGSL)と非破壊保存
- [ ] フェーズ4: 動画への 3D LUT(.cube)リアルタイム適用+強度スライダー、簡易調整、Transformer 書き出し、トリム
- [ ] フェーズ5: トランジション磨き込み、パフォーマンス最適化

## ビルド

```
./gradlew assembleDebug
```

JDK 17 と Android SDK(compileSdk 35)が必要。CI(GitHub Actions)でユニットテストと debug APK のビルドを行う。
