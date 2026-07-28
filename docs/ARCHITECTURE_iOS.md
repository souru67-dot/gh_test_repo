# ColorHunt iOS 構成リファレンス

**どのファイルに何があるか**を引くためのドキュメントです。
「なぜこの設計なのか」「Swift の文法」は学習シリーズにあります。

> 📖 **Swift を学びたい場合は [learn/](learn/) から始めてください。**
> このアプリのコードを教材に、文法 → SwiftUI → 非同期 → 設計 →
> コードの追い方、の順で読める5本セットです。

---

## 1. 10秒でわかる全体像

```
┌──────────── iPhone ────────────┐
│  SwiftUI (iosApp/ColorHunt/*.swift)      │  ← 画面・操作・アニメ
│      │  呼び出し                          │
│  SharedColor.framework (KMP)             │  ← Kotlin製の共有ロジック
│   ├ ColorBridge   … 色分類               │     （Androidと同じ計算）
│   └ CollageBridge … コラージュ座標計算    │
└──────────────────────────────┘
```

- **UIは全部 SwiftUI**。1画面=1ファイル。
- **「色の判定」と「コラージュの矩形計算」だけ Kotlin**（`shared/`）。
  だから Android 版とコラージュがピクセル一致します。
- データベースなし。起動中のデータはすべて `AppState` が握るシンプル構成。

## 2. ファイルマップ（読む順）

| # | ファイル | 役割 | 学べるSwiftの概念 |
|---|---|---|---|
| 1 | `ColorHuntApp.swift` | エントリポイント。タブ・ブランド色・オンボーディング・共通部品 | `@main` / `App` / `TabView` / `ButtonStyle` |
| 2 | `AppState.swift` | **心臓部**。写真の配列・選択・色解析・重複排除・地図ピン・Pro課金 | `ObservableObject` / `@Published` / `async` / actor隔離 |
| 3 | `HuntView.swift` | 写真取込→色分け表示。フィルタ・コレクションカード | `ForEach` / `LazyVGrid` / `PhotosPicker` |
| 4 | `CollageView.swift` | 最大ファイル。プレビュー・編集・書き出し・課金UI | ジェスチャ合成 / `ImageRenderer` / `sheet` |
| 5 | `TodayColorView.swift` | ルーレット・通知 | 毎フレーム描画 / `UserNotifications` |
| 6 | `HuntCameraView.swift` | ハントカメラ。レンズ切替（望遠自動ラベル）・タイマー・露出・**フレームモード**（テンプレのキャンバスが画面をほぼ占有し、**カメラの全景がアクティブなコマの中に縮小表示**される。プレビューレイヤーは1枚だけで、フルスクリーン⇄コマ間をアニメーション移動＝撮るたび次のコマへホップ。保存はコマのアスペクトでの最大中央クロップ＝コマに見えていた構図そのもの。`guideGeometry`が表示・プレビュー位置・切り抜きの単一ソース） | `AVFoundation` / `UIViewRepresentable` / `Task` |
| 7 | `GridPreviewView.swift` | フィードプレビュー | Drag & Drop (`DropDelegate`) |
| 8 | `HuntMapView.swift` | カラーマップ | `MapKit` / `MKMapSnapshotter` |

> カメラ→コラージュの受け渡しは `AppState.startCollage(with:templateID:)` が
> 1本道: 撮影4枚を追加→その4枚だけを選択→`pendingCollageTemplateID` に
> テンプレを積んで コラージュタブへ。CollageView 側が `onAppear/onChange` で
> 一度だけ適用して消します（テンプレ11種は `CollageTemplateVM.all`）。

## 3. データの流れ（1本道）

```
写真を選ぶ (PhotosPicker)
  → AppState.add(images:)          # ①重複ハッシュチェック(16×16px, 安定FNV-1a)
  → DominantColor.extract()        # ②48×48に縮小→ヒストグラム→代表色
  → ColorBridge.classifyKey()      # ③Kotlinの共有分類器で "RED" 等のキーに
  → @Published photos が変わる      # ④SwiftUIが自動で再描画
  → didSet → scheduleSave()        # ⑤0.9秒デバウンスで Documents/HuntStore に
                                   #   JPEG(≤2400px) + manifest.json を自動保存
起動時: loadStore() が manifest を読み、画像をバックグラウンドで復元
（選択・並び順・今日の色・グリッドも同じ manifest で往復）
```

**Pro の境界**（`CollageTemplateVM.isPro`）: 無料=拡散の主役（4カット・フレーム
モード・基本6テンプレ・1080px+透かし）。Pro=デイログ/パステル/Y2K/チェキ/
マガジン＋透かし削除＋2160px。Proテンプレは適用（試用）自由で、保存/共有の
瞬間に `proTemplateLock` がペイウォールを出す try-then-buy 設計。さらに各Pro
テンプレは `signatureDeco`（日付ステッカー/ステッカー散らし/クロームフレーム/
チェキ白マット/マストヘッド）をレンダラー内だけに持つ——スライダーや色スウォッチ
の組合せでは絶対に再現できない層で、無料の手動設定との差を構造的に保証する。

**ここが SwiftUI の核心**: `@Published` の値が変わると、それを見ている View が
勝手に再描画されます。「画面を更新するコード」は 1 行も書いていません。

## 4. 各ファイルの見どころ（コード付き）

### AppState.swift — 状態管理のお手本
```swift
@MainActor                            // このクラスは常にメインスレッドで動く宣言
final class AppState: ObservableObject {
    @Published var photos: [HuntPhoto] = []   // 変更=UI更新のトリガー
```
- `Task.detached { ... }` で重い処理（色解析）を裏スレッドへ逃がし、
  終わったら `await self?.finishAnalysis(...)` でメインに戻る——iOSの
  並行処理の基本形です。
- `quickHash`: 画像を16×16に縮めてバイト列をハッシュ→重複判定。
  「同一性のない値(UIImage)にIDを与える」定石。

### CollageView.swift — ジェスチャと描画の教材
- **タップ=トリミング / 長押しドラッグ=並べ替え** の共存:
  ```swift
  .onTapGesture { ... }                       // 子(セル)に付ける
  .simultaneousGesture(                        // 親(キャンバス)に付ける
      LongPressGesture(minimumDuration: 0.3)
          .sequenced(before: DragGesture(...)) // 長押し"成立後"だけドラッグが動く
  )
  ```
- **WYSIWYG**: プレビューも書き出しも同じ `canvas(width:)` を使い、
  340pt(画面) と 1080px(書き出し) を同一コードで描く設計。
- 落とし穴として覚える価値大: `clipShape` は **見た目だけ** 切り抜き、
  タッチ判定は切らない → はみ出す画像には `.allowsHitTesting(false)`。

### 共有Kotlinとの橋渡し（KMPブリッジ）
```swift
let flat = CollageBridge.shared.computeFlat(...)  // Kotlin関数を直接呼ぶ
let layout = decodeLayout(flat)                    // FloatArray → [CGRect]
```
- enum やネスト型は Swift へ渡すと名前が壊れやすいので、
  **数値配列と文字列だけ**やり取りする方針（`bucketKeys() -> [String]` 等）。
  異言語間インターフェイスの実践的な設計指針です。

### TodayColorView.swift — アニメーションの2方式
- `withAnimation { }`: 状態A→Bを OS が補間（普通はこれ）
- ルーレットは**毎フレーム自分で値を更新**（`Task` + 16ms sleep）。
  「色相が虹を巡る」ような補間できない動きはこちら。使い分けを体感できます。

## 5. Swift / SwiftUI を学ぶには

このドキュメントには文法の解説はありません。**[learn/](learn/) にあります。**

| | 内容 |
|---|---|
| [learn/01-swift-basics.md](learn/01-swift-basics.md) | 文法。`let`/`var`・オプショナル・struct/class・クロージャ |
| [learn/02-swiftui.md](learn/02-swiftui.md) | View・状態管理の4記号・モディファイア・レイアウト |
| [learn/03-concurrency.md](learn/03-concurrency.md) | `async/await`・`Task`・`@MainActor`・デバウンス |
| [learn/04-architecture.md](learn/04-architecture.md) | **なぜこの設計なのか**と、現状の弱点 |
| [learn/05-code-tour.md](learn/05-code-tour.md) | 実際の処理を行番号で追う練習 |

## 6. 既知の設計上の負債

v1.0 時点で認識している弱点です。詳細と理由は
[learn/04-architecture.md](learn/04-architecture.md) §4-7 に書いています。

| 項目 | 内容 | 対応時期 |
|---|---|---|
| **写真の常駐メモリ** | `HuntPhoto` が `UIImage` を保持。200枚で900MB規模 | v1.1 でサムネイル分離。v1.0 は実測でゲート（`IOS_LAUNCH_GUIDE.md` §3.4） |
| `CollageView.swift` 1625行 | プレビュー・編集・書き出し・課金UI・テンプレ定義が同居 | テンプレ定義の分離から |
| Swift 側の自動テストが無い | 色分類は Kotlin 側に21件あるが、`AppState` は未テスト | 重複排除と保存/読込から |
| `.xcodeproj` が未管理 | 署名設定・権限文言・アイコン割り当てが Mac 上のみ | v1.0 提出後にコミット |
| **パレット配置「下帯」が iOS 専用** | 共有 `CollageGeometry` に FOOTER が無く、`CollageView.canvas(width:)` が高さを縮めて帯を足している。計算は帯＋減算のみで両OS同一に再現可能だが、共有されていない | Android 提出前に `CollageGeometry` へ FOOTER を追加し、レンダラーも新デザインへ移植 |
| **パレットの意匠が iOS のみ新設計** | iOS は「採集票」（分類名＋HEXを左揃え・下端そろえ、区切りは幅40%の目盛り）。さらに ハーフ／インスタント は**レールを使わず紙の余白に印字**（`formatPalette`）。Android の `CollageRenderer.drawPalette` は旧「等分ブロック＋HEX中央寄せ」のままで、**フォーマット固定テンプレでレールを敷くと装飾とずれる不具合も残っている** | Android 提出前に移植 |

## 7. 将来の伸びしろ（設計済みの余白）

- **Liquid Glass**: iOS 26 では `#available` ガードでガラス質UIを追加予定
  （コードは iOS 16 フォールバック前提で設計済み）
- **ウィジェット**: 「今日の色」はWidgetKitと相性抜群（サブスク特典候補）
- **サムネイル分離**: 上記の負債解消と同時に、一覧のスクロールも軽くなる
