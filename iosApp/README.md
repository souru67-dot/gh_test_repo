# ColorHunt — iOS (SwiftUI)

iOS 版本体です。Android と **同じコア**（Kotlin Multiplatform `:shared` =
フレームワーク名 `SharedColor`）を使用：色分類（`ColorClassifier`）に加え、
**コラージュのレイアウト計算（`CollageGeometry`/`CollageBridge`）も共有**
しているため、両OSのコラージュはピクセル互換です。UI は SwiftUI、地図は
**Apple MapKit**、対象は **iOS 16+**（iPhone 8 以降）。

> iOS のビルド/実行には **macOS + Xcode 15+** が必要で、この CI 環境（Linux）
> では Swift のコンパイル検証ができません。共有 Kotlin コードは Android CI が
> 毎プッシュ検証しています。**Swift 側は macOS で最初のビルドを通す際に、
> KMP ブリッジ名（`CollageGeometryRect` 等の生成名）を Xcode の補完で確認し、
> 差異があれば合わせてください。**

## 構成（実装済み画面）

```
iosApp/ColorHunt/
├── ColorHuntApp.swift      # @main / 4タブ＋中央の浮きカメラボタン（左右2:2バランス）
├── AppState.swift          # 写真ストア＋ドミナントカラー抽出（Android同等の重み付け）
├── HuntView.swift          # 写真選択→自動仕分け・カラーコレクション・グリッドへの導線
├── CollageView.swift       # 共有ジオメトリでレイアウト／テンプレ9種／保存・共有＋キャプションコピー
├── TodayColorView.swift    # リング型ホイール＋ルーレットスピン
├── HuntCameraView.swift    # ハントカメラ（レンズ切替/セルフィー/フラッシュ/タイマー/露出/比率）
├── GridPreviewView.swift   # 4:5フィードプレビュー（独自選択）
└── HuntMapView.swift       # MapKit＋写真バブルピン＋マップ書き出し
shared/                     # KMP: 色分類 + コラージュ幾何 + ブリッジ
```

## セットアップ（初回のみ）

1. **前提**: macOS / Xcode 15+ / JDK 17。リポジトリのルートで
   `./gradlew :shared:compileKotlinIosSimulatorArm64` が通ること（K/N を初回DL）。

2. Xcode で新規 **iOS App** プロジェクト（Interface: SwiftUI、名前: `ColorHunt`、
   iOS 16.0+）を作成し、`iosApp/ColorHunt/*.swift` を参照追加。
   ※ プロジェクトの **Minimum Deployments を iOS 16.0** に設定してください。

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

5. Info.plist に以下を追加:
   - `NSPhotoLibraryAddUsageDescription`（コラージュ保存）
   - `NSPhotoLibraryUsageDescription`（ハントの「自動で仕分け」で
     ライブラリの直近200枚を読み込むため）
   - `NSCameraUsageDescription`（ハントカメラ）例:「街の色をリアルタイムで
     ハントするためにカメラを使用します」

> **新規ファイル**: `HuntCameraView.swift` を追加した場合は、Xcode の
> プロジェクトナビゲータにドラッグして **ColorHunt ターゲットにチェック**を
> 入れてください（未追加だと `HuntCameraView` が未定義になります）。
> カメラはシミュレータでは動きません（実機 iPhone で確認）。

6. **多言語化**: `en.lproj` / `ja.lproj` / `ko.lproj` / `zh-Hans.lproj` の
   `Localizable.strings` を Xcode プロジェクトに追加し、Project → Info →
   Localizations に English / Japanese / Korean / Chinese (Simplified) を
   追加。SwiftUI の `Text("日本語")` は日本語リテラルをキーに自動翻訳し、
   キーが無い言語では日本語にフォールバックします。

## 共有 API の Swift からの使い方（プリミティブのみ・ブリッジ安全）

iOS 側は Kotlin の enum / 入れ子データクラスに触れず、**文字列配列・Float配列**
だけを受け取ります（ObjC ブリッジの生成名に依存しないため堅牢）。

- `ColorBridge.shared.bucketKeys()` → `[String]`（例: `["RED", "ORANGE", ...]`）
- `ColorBridge.shared.classifyKey(colorInt: Int32)` → `String`（安定キー）
- `ColorBridge.shared.swatchOf(key: String)` → `Int32`（0xFFRRGGBB）
- `CollageBridge.shared.computeFlat(...)` → `KotlinFloatArray`（フラット配列）
  - `[0]`=セル数, `[1]`=パレット有無, `[2..5]`=パレット矩形(L,T,R,B),
    `[6..]`=各セル L,T,R,B の並び。Swift 側は `decodeLayout(_:)`（AppState.swift）で
    `CGRect` 配列に変換
  - 配置は **ordinal 指定**: layout 0=GRID/1=VERTICAL/2=TWO_COLUMN、
    placement 0=NONE/1=CENTER/2=SIDE/3=LEFT/4=OVERLAY

> 唯一の KMP 標準型依存は `KotlinFloatArray`（`decodeLayout` 内）。生成名が
> フレームワーク接頭辞付き等で異なる場合は、その一箇所だけ合わせてください。

## 残タスク（次パス）

- [x] macOS で初回ビルド（ブリッジ生成名の確認・修正）
- [x] トリミングエディタ（パン＋ピンチ、`CellFocal`＝共有 `FocalPoint` と同じ計算）
- [x] テンプレート5種（`CollageTemplateVM`＝共有 `CollageTemplates` と同値）
- [x] ハント: 自動で仕分け（PHPhotoLibrary で直近200枚）＋色フィルタ＋色相ホイール
- [x] グリッド: iOS Instagram 風プロフィール＋ドラッグ並べ替え
- [x] コラージュ: セルのドラッグ並べ替え／帯調整／HEXチップ／色相整列／枠線・背景
- [x] 今日の色: 毎日リマインド通知
- [x] マップ: PHPhotoLibrary 連携で GPS 復元＋色ピン
- [x] StoreKit 2（Pro）＋透かし描画＋ペイウォール
- [x] Localizable.strings（en/ja/ko/zh-Hans）※要 Xcode でのファイル追加＋言語登録
