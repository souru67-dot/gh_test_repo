# ColorHunt — iOS (SwiftUI)

iOS 版のスキャフォールドです。Android と **同じ色分類ロジック**（Kotlin
Multiplatform の `:shared` モジュール = フレームワーク名 `SharedColor`）を
そのまま利用します。UI は SwiftUI、地図は **Apple MapKit**。

> このディレクトリは出発点です。iOS アプリのビルド/実行には **macOS + Xcode**
> が必要で、この CI 環境（Linux）ではビルド検証できません。Android CI は
> `:shared` の Android ターゲットをビルドするので、共有コード自体の
> コンパイルは毎プッシュ検証されています。

## 構成

```
iosApp/ColorHunt/
├── ColorHuntApp.swift   # @main App
└── ContentView.swift    # 共有ColorClassifierで色分類デモ＋MapKit地図
shared/                  # KMP共有モジュール（commonMainに色分類コア）
```

## セットアップ（初回のみ）

1. **前提**: macOS / Xcode 15+ / JDK 17。リポジトリのルートで
   `./gradlew :shared:compileKotlinIosSimulatorArm64` が通ること（K/N を初回DL）。

2. Xcode で新規 **iOS App** プロジェクト（Interface: SwiftUI、名前: `ColorHunt`）を
   作成し、`iosApp/ColorHunt/*.swift` を取り込む（既存ファイルを参照追加）。

3. 共有フレームワークを埋め込む **Run Script** ビルドフェーズを
   「Compile Sources」より前に追加:
   ```bash
   cd "$SRCROOT/.."
   ./gradlew :shared:embedAndSignAppleFrameworkForXcode
   ```
   （このタスクは Kotlin プラグインが提供。Xcode の `CONFIGURATION` / `SDK_NAME` /
   `ARCHS` などを読み取り、対象に合うフレームワークを生成・署名します）

4. Build Settings の **Framework Search Paths** に追加:
   ```
   $(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)
   ```

5. Swift 側で `import SharedColor`。`ColorClassifier.shared.classify(colorInt:)` などが使えます。

## 共有 API の Swift からの使い方

- `ColorClassifier`（Kotlin `object`）→ Swift では `ColorClassifier.shared`
- `ColorClassifier.shared.classify(colorInt: Int32)` → `ColorBucket`
- `ColorBucket` は `.name`（String）と `.swatch`（Int32 ARGB）を公開
- `Hsv.companion.fromRgb(red:green:blue:)` でHSVも取得可能

## 今後（iOS 各機能の対応方針）

| 機能 | Android | iOS |
|------|---------|-----|
| 色分類 | `:shared`（共有） | `:shared`（共有・実装済み） |
| ドミナントカラー抽出 | AndroidX Palette | `UIImage`＋Core Image / vImage で実装 |
| 写真取り込み | Photo Picker | `PhotosPicker`（PhotoKit） |
| 地図 | osmdroid | MapKit（`Map` + `Annotation`） |
| Exif GPS | ExifInterface | `CLLocation` / ImageIO（`CGImageSource`） |
| 保存・共有 | MediaStore / FileProvider | `UIActivityViewController` / Photos |
| 課金 | Play Billing（Phase4 seam） | StoreKit 2（Phase4） |

`domain/config`（SNSサイズ・枚数プリセット・FeatureFlags）やお題ルーレットの
コアも純Kotlinなので、順次 `:shared` へ移して iOS と共有できます。
