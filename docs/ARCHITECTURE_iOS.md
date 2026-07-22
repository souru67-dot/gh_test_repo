# ColorHunt iOS ソースコード全貌ガイド（Swift学習用）

このドキュメントは、Swift をこれから学ぶあなたが **自分のアプリのコードを教材に**
できるように書いた「読む順番つき」のツアーです。

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
| 6 | `GridPreviewView.swift` | フィードプレビュー | Drag & Drop (`DropDelegate`) |
| 7 | `HuntMapView.swift` | カラーマップ | `MapKit` / `MKMapSnapshotter` |

## 3. データの流れ（1本道）

```
写真を選ぶ (PhotosPicker)
  → AppState.add(images:)          # ①重複ハッシュチェック(16×16px)
  → DominantColor.extract()        # ②48×48に縮小→ヒストグラム→代表色
  → ColorBridge.classifyKey()      # ③Kotlinの共有分類器で "RED" 等のキーに
  → @Published photos が変わる      # ④SwiftUIが自動で再描画
```

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

## 5. Swift文法ミニ辞典（このコードに出る順）

| 記法 | 意味 |
|---|---|
| `let` / `var` | 定数 / 変数（まず `let`、必要な時だけ `var`） |
| `photo.dominantColor?` | Optional。「無いかもしれない値」。`if let c = ...` で取り出す |
| `{ $0.bucketKey != nil }` | クロージャ（無名関数）。`$0` は第1引数 |
| `some View` | 「何かの View を返す」— SwiftUI の戻り値はほぼこれ |
| `@State` | その View 専用のミニ状態。変わると再描画 |
| `@EnvironmentObject` | 祖先から注入される共有状態（= AppState） |
| `guard let x else { return }` | 早期リターン。ネストを浅く保つ定石 |
| `extension` | 既存型に機能を後付け（`Color(argb:)` など） |

## 6. 学習ロードマップ（このコードで）

1. **1週目**: `TodayColorView` を読む→数値をいじって挙動を見る
   （ホイール太さ・スピン時間など即結果が見えて楽しい）
2. **2週目**: `HuntView` にミニ機能を足す（例: 枚数バッジの色変更→
   お気に入りマーク追加）
3. **3週目**: `AppState` に状態を1つ足して画面に反映
   （例: 最後にハントした日付を保存して表示）
4. 副読本: Apple公式「SwiftUI Tutorials」(無料) と
   Hacking with Swift「100 Days of SwiftUI」(無料) が本コードと相性◎

## 7. 将来の伸びしろ（設計済みの余白）

- **永続化**: 現在は起動中のみ保持。`SwiftData`(iOS17+) か `PHAsset` ID保存で
  再起動復元が次の大型テーマ
- **Liquid Glass**: iOS 26 では `#available` ガードでガラス質UIを追加予定
- **ウィジェット**: 「今日の色」はWidgetKitと相性抜群（サブスク特典候補）
