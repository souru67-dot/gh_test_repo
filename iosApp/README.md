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
├── AppState.swift          # 写真ストア（Documents/HuntStoreにJPEG+manifestで永続化）＋色抽出
├── HuntView.swift          # 写真選択→自動仕分け・カラーコレクション・グリッドへの導線
├── CollageView.swift       # テンプレ11種（無料6/Pro5・試用→保存でペイウォール）／日付スタンプ
├── TodayColorView.swift    # リング型ホイール＋ルーレットスピン
├── HuntCameraView.swift    # ハントカメラ（レンズ/セルフィー/フラッシュ/タイマー/露出/比率＋フレームモード）
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

   > **リポジトリや `iosApp` フォルダを丸ごとドラッグしないでください。**
   > 追加するのは **`iosApp/ColorHunt/*.swift` と `iosApp/ColorHunt/*.lproj`**
   > だけです。
   >
   > iOS のバンドルはリソースを **`Resources/` 直下にフラットにコピー**する
   > ため、フォルダで名前を分けている Android 側のファイルが軒並み衝突し、
   > `Multiple commands produce ...` でビルドが落ちます（`README.md` ×2、
   > `values-*/strings.xml` ×5、`build.gradle.kts` ×3、`themes.xml` ×2 など）。
   >
   > **既に入れてしまった場合の復旧**: TARGETS → Build Phases →
   > **Copy Bundle Resources** で全項目を選択して「−」で削除し（実ファイルは
   > 消えません）、「＋」で以下の **5つだけ**を追加し直してから
   > Clean Build Folder（⇧⌘K）:
   > `Assets.xcassets` と `Localizable.strings`（en/ja/ko/zh-Hans）。
   >
   > ナビゲータからも消す場合は右クリック → Delete → **Remove Reference**
   > を選ぶこと（**Move to Trash はリポジトリの実ファイルを削除します**）。

3. 共有フレームワークを埋め込む **Run Script** ビルドフェーズを
   「Compile Sources」より前に追加:
   ```bash
   cd "$SRCROOT/.."
   export JAVA_HOME=$(/usr/libexec/java_home -v 17)
   ./gradlew :shared:embedAndSignAppleFrameworkForXcode
   ```

   > **`JAVA_HOME` の明示は必須です。** Xcode の Run Script は `~/.zshrc` を
   > 読まないため、シェルで JDK 17 を設定していても Xcode からは
   > システム既定の JDK が使われます。新しい JDK（例: 26）を拾うと
   > Gradle 8.14.3 が対応しておらず、
   > `FAILURE: Build failed with an exception. * What went wrong: 26.0.2`
   > のように**バージョン番号だけのエラー**で落ちます（本プロジェクトは
   > Java 17 前提）。`/usr/libexec/java_home -V` で 17 の有無を確認し、
   > 無ければ `brew install --cask temurin@17`。

4. Build Settings の **Framework Search Paths** に追加:
   ```
   $(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)
   ```

   > **3・4 のパスは `.xcodeproj` の置き場所に依存します。** `$(SRCROOT)` は
   > `.xcodeproj` があるフォルダです。上記は `<リポジトリ>/iosApp/` に置いた
   > 場合の値。たとえば `~/Desktop/git/ColorHunt/ColorHunt.xcodeproj` を作り、
   > リポジトリを `~/Desktop/git/ColorHunt/gh_test_repo/` に clone した配置なら
   > `cd "$SRCROOT/gh_test_repo"` と
   > `$(SRCROOT)/gh_test_repo/shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`
   > になります。パスが違うと `Unable to resolve module dependency: 'SharedColor'`
   > で落ちます。

5. **実行先は必ず iPhone 実機か iOS Simulator**。`shared` は iOS ターゲット
   （`iosX64`/`iosArm64`/`iosSimulatorArm64`）のみを生成するため、**「My Mac」で
   ビルドすると `SharedColor` が存在せず**同じエラーになります。
   ログの `-target arm64-apple-macos...` / バンドルパスの `Contents/Resources/`
   は macOS 向けビルドになっているサインです。

6. **Xcode 26 で新規作成した場合**: Build Settings の
   **Default Actor Isolation** を `nonisolated` に設定してください。
   既定の `MainActor`（Approachable Concurrency）だと、専用キューで
   AVFoundation のコールバックを受ける `CameraController` が並行性エラーに
   なります（本コードは Swift 5 言語モード・従来の隔離規則が前提）。

   > なお `AppState.swift` / `HuntCameraView.swift` の `import Combine` は
   > **削除しないでください**。Xcode 26 既定の **MemberImportVisibility**
   > では `ObservableObject` / `@Published` の定義元モジュールを直接
   > import する必要があり、外すと
   > `Initializer 'init(wrappedValue:)' is not available due to missing
   > import of defining module 'Combine'` が多発します。

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
  - **placement 5（下帯）は iOS 側だけで組み立てます。** 共有 enum には無い値で、
    `CollageView.canvas(width:)` が「高さを帯のぶん縮めて placement 0 で計算し、
    下辺に帯の矩形を足す」処理をしています。計算自体は帯と減算だけなので
    両OSで同一に再現できますが、Android を出す際は `CollageGeometry` に
    FOOTER を足して共有側へ寄せてください（`ARCHITECTURE_iOS.md` §6）

> 唯一の KMP 標準型依存は `KotlinFloatArray`（`decodeLayout` 内）。生成名が
> フレームワーク接頭辞付き等で異なる場合は、その一箇所だけ合わせてください。

## 残タスク（次パス）

- [x] macOS で初回ビルド（ブリッジ生成名の確認・修正）
- [x] トリミングエディタ（パン＋ピンチ、`CellFocal`＝共有 `FocalPoint` と同じ計算）
- [x] テンプレート5種（`CollageTemplateVM`＝共有 `CollageTemplates` と同値）
- [x] ハント: 自動で仕分け（PHPhotoLibrary で直近200枚）＋色フィルタ＋色相ホイール
      取り込み結果はトーストで要約（追加／重複スキップ／読み込み失敗）。
      「200枚ぴったりにならない」のは重複スキップとiCloud未取得が理由で、
      その内訳をそのまま表示している。
- [x] グリッド: iOS Instagram 風プロフィール＋ドラッグ並べ替え
- [x] コラージュ: セルのドラッグ並べ替え／帯調整／HEXチップ／色相整列／枠線・背景
- [x] 今日の色: 毎日リマインド通知
- [x] マップ: PHPhotoLibrary 連携で GPS 復元＋色ピン
- [x] StoreKit 2（Pro）＋透かし描画＋ペイウォール
- [x] Localizable.strings（en/ja/ko/zh-Hans）※要 Xcode でのファイル追加＋言語登録
