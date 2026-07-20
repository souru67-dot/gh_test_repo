# ColorHunt — iOS (SwiftUI)

iOS 版本体です。Android と **同じコア**（Kotlin Multiplatform `:shared` =
フレームワーク名 `SharedColor`）を使用：色分類（`ColorClassifier`）に加え、
**コラージュのレイアウト計算（`CollageGeometry`/`CollageBridge`）も共有**
しているため、両OSのコラージュはピクセル互換です。UI は SwiftUI、地図は
**Apple MapKit**、対象は iOS 17+。

> iOS のビルド/実行には **macOS + Xcode 15+** が必要で、この CI 環境（Linux）
> では Swift のコンパイル検証ができません。共有 Kotlin コードは Android CI が
> 毎プッシュ検証しています。**Swift 側は macOS で最初のビルドを通す際に、
> KMP ブリッジ名（`CollageGeometryRect` 等の生成名）を Xcode の補完で確認し、
> 差異があれば合わせてください。**

## 構成（実装済み画面）

```
iosApp/ColorHunt/
├── ColorHuntApp.swift      # @main / 5タブ（ハント・コラージュ・今日の色・グリッド・マップ）
├── AppState.swift          # 写真ストア＋ドミナントカラー抽出（Android同等の重み付け）
├── HuntView.swift          # 写真選択→自動仕分け・選択・長押しで色変更
├── CollageView.swift       # 共有ジオメトリでレイアウト／パレット5配置／保存・共有＋キャプションコピー
├── TodayColorView.swift    # リング型ホイール＋ルーレットスピン
├── GridPreviewView.swift   # 4:5フィードプレビュー（独自選択）
└── HuntMapView.swift       # MapKit（GPS連携は次パス）
shared/                     # KMP: 色分類 + コラージュ幾何 + ブリッジ
```

## セットアップ（初回のみ）

1. **前提**: macOS / Xcode 15+ / JDK 17。リポジトリのルートで
   `./gradlew :shared:compileKotlinIosSimulatorArm64` が通ること（K/N を初回DL）。

2. Xcode で新規 **iOS App** プロジェクト（Interface: SwiftUI、名前: `ColorHunt`、
   iOS 17.0+）を作成し、`iosApp/ColorHunt/*.swift` を参照追加。

3. 共有フレームワークを埋め込む **Run Script** ビルドフェーズを
   「Compile Sources」より前に追加:
   ```bash
   cd "$SRCROOT/.."
   ./gradlew :shared:embedAndSignAppleFrameworkForXcode
   ```

4. Build Settings の **Framework Search Paths** に追加:
   ```
   $(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)
   ```

5. Info.plist: `NSPhotoLibraryAddUsageDescription`（コラージュ保存）を追加。

## 共有 API の Swift からの使い方

- `ColorClassifier.shared.classify(colorInt: Int32)` → `ColorBucket`（`.name` / `.swatch`）
- `ColorBridge.shared`: `buckets()` / `classifyKey(colorInt:)` / `swatchOf(key:)`
  — enum ブリッジの罠を避ける安定キーAPI
- `CollageBridge.shared.compute(...)` — レイアウト/パレット配置は **ordinal 指定**
  （0=GRID…、0=NONE…4=OVERLAY）で、Android と同一の `Layout`（`cells` + `palette` 矩形）を返す

## 残タスク（次パス）

- [ ] macOS で初回ビルド（ブリッジ生成名の確認・修正）
- [ ] トリミングエディタ（パン＋ピンチ、共有 `FocalPoint` 使用）
- [ ] テンプレート5種（共有 `CollageTemplates` を UI に接続）
- [ ] マップ: PHPhotoLibrary 連携で Exif GPS 復元＋色ピン
- [ ] グリッド: ドラッグ並べ替え
- [ ] StoreKit 2（Pro）、透かし描画、Localizable.strings（en/ja/ko/zh）
