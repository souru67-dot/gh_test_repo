# 2. SwiftUI — 画面の作り方

ColorHunt の画面はすべて SwiftUI で書かれています。この章で、
「画面がどう組み立てられ、どうやって更新されるか」が分かります。

---

## 2-1. View とは「今の状態から見た目を計算する関数」

これが SwiftUI で一番大事な考え方です。

```swift
struct HuntView: View {
    @EnvironmentObject private var state: AppState

    var body: some View {
        NavigationStack {
            ScrollView {
                …
            }
        }
    }
}
```
> `HuntView.swift:7-22`

### 従来のやり方との違い

昔の iOS 開発（UIKit）はこうでした。

```
写真が増えた → テーブルに「1行足して」と命令する
写真が消えた → テーブルに「1行消して」と命令する
```

命令し忘れると画面がズレます。しかも状態が増えるほど命令の組み合わせが爆発します。

SwiftUI はこうです。

```
写真が増えた → body をもう一度計算する → 新しい見た目になる
```

**「どう変えるか」ではなく「今どう見えるべきか」だけを書きます。**
差分の計算は SwiftUI がやってくれます。

### `some View` とは

```swift
var body: some View {
```

「View のうちの何か（具体的な型は書かないけど、1種類に決まっている）」という意味です。

SwiftUI の View の型は、実際には
`NavigationStack<ScrollView<LazyVStack<TupleView<…>>>>` のように
入れ子で恐ろしく長くなります。書いていられないので `some View` で省略します。

**初心者のうちは「おまじない」で構いません。** `body` には必ずこう書く、と覚えてください。

---

## 2-2. View は struct — 毎回作り直される

```swift
struct HuntView: View {   // class ではなく struct
```

第1章でやったとおり、`struct` はコピーされる軽い値です。
**SwiftUI の View は、状態が変わるたびに何度も作り直されます。**
1秒に何十回も作られることもあります。

だからこそ軽い `struct` である必要があるのです。

### ここから導かれる大事な帰結

**View の中に、消えては困るデータを持たせてはいけません。**
作り直されたら消えるからです。

だから ColorHunt は、写真も選択状態も `AppState`（class）に持たせています。
View が持っているのは「その画面だけの一時的な状態」だけです。

```swift
@State private var pickerItems: [PhotosPickerItem] = []  // 一時的でよい
@State private var showGrid = false                      // 一時的でよい
```
> `HuntView.swift:9-10`

---

## 2-3. 状態管理の4つの記号 — 使い分けが全て

SwiftUI の学習でここが最大の山場です。**この表だけは覚えてください。**

| 記号 | 意味 | 誰が持ち主か | ColorHunt での例 |
|---|---|---|---|
| `@State` | この画面だけの一時的な状態 | この View | `HuntView.swift:10` `showGrid` |
| `@Binding` | 他人の `@State` を借りる | 別の View | 子View に渡すとき |
| `@StateObject` | class を**作って**持つ | この View | `ColorHuntApp.swift:35` `state` |
| `@EnvironmentObject` | 上から配られた class を使う | 祖先の View | 各画面の `state` |

### `@State` — 自分だけの状態

```swift
@State private var showGrid = false
```
> `HuntView.swift:10`

「グリッド画面を出しているか」は、ハント画面の中だけの話です。
他の画面には関係ありません。こういうものが `@State` です。

`@State` を付けた変数が変わると、**その View の `body` が再計算されます**。

```swift
.fullScreenCover(isPresented: $showGrid) {
    GridPreviewView()
}
```
> `HuntView.swift:75-77`

`$showGrid` の **`$` は「変数そのものへの参照」**という意味です。
`showGrid` だと値（true/false）ですが、`$showGrid` だと
「読み書きできる窓口」を渡せます。`fullScreenCover` は閉じるときに
`false` を書き込む必要があるので、`$` 付きで渡しています。

### `@StateObject` — class を作って持つ

```swift
@main
struct ColorHuntApp: App {
    @StateObject private var state = AppState()
```
> `ColorHuntApp.swift:33-35`

**アプリ全体で `AppState` はここで1個だけ作られます。**

`@StateObject` は「作った1個を、View が作り直されても保持し続ける」という指定です。
もし `@State` で書いてしまうと、View が再計算されるたびに `AppState()` が
新しく作られ、写真が全部消えます。

> **`@StateObject` は作る側、`@ObservedObject` は受け取る側**です。
> ColorHunt では受け取り側に `@EnvironmentObject` を使っているので、
> `@ObservedObject` は出てきません。

### `@EnvironmentObject` — 上から配る

`AppState` は全画面で必要です。かといって、画面から画面へバケツリレーすると
コードが汚れます。そこで SwiftUI には「上から配る」仕組みがあります。

**配る側**（アプリの一番上）:

```swift
RootTabView()
    .environmentObject(state)
```
> `ColorHuntApp.swift:64-65`

**受け取る側**（どの画面でも）:

```swift
@EnvironmentObject private var state: AppState
```
> `HuntView.swift:8`

これだけで、`HuntView` も `CollageView` も `TodayColorView` も
**同じ1個の `AppState`** を見られます。

> ⚠️ **落とし穴**: `.environmentObject()` で配り忘れた画面で
> `@EnvironmentObject` を使うと、**実行時にクラッシュ**します
> （コンパイルは通ります）。ColorHunt で
> `HuntCameraView().environmentObject(state)` と明示的に渡しているのは、
> `fullScreenCover` が環境を引き継がない場合があるためです。

### `@Published` — 変化を知らせる

```swift
final class AppState: ObservableObject {
    @Published var photos: [HuntPhoto] = []
```
> `AppState.swift:43-48`

`ObservableObject` は「変化を通知できるクラス」、
`@Published` は「この値が変わったら通知する」という指定です。

**流れはこうです。**

```
AppState.photos に写真が追加される
        ↓
@Published が「変わったよ」と通知
        ↓
@EnvironmentObject で見ている全 View の body が再計算される
        ↓
画面が新しい写真を表示する
```

**あなたは「画面を更新しろ」と一度も書いていません。** これが SwiftUI です。

---

## 2-4. モディファイア — 順番に意味がある

```swift
Text(LocalizedStringKey(label))
    .font(.subheadline.weight(.medium))
    .padding(.horizontal, 14)
    .padding(.vertical, 8)
    .foregroundStyle(.white)
    .background(active ? Color(argb: 0xFF7C4DFF).opacity(0.35) : .white.opacity(0.06), in: Capsule())
```
> `HuntView.swift:217-221`（一部整形）

`.font(…)` のような `.` から始まるものを**モディファイア**と呼びます。

### 重要: モディファイアは「変更」ではなく「包む」

```swift
Text("あ").padding().background(.red)
```

これは「Text に padding を設定して、背景を赤にする」ではありません。
正確には、

```
Text("あ") を padding で包んだものを、background で包んだもの
```

という**新しい View を作っています**。だから**順番で結果が変わります**。

```swift
Text("あ").padding().background(.red)
// → 余白も含めて赤くなる

Text("あ").background(.red).padding()
// → 文字の部分だけ赤く、その外に余白
```

**SwiftUI で「思ったとおりに表示されない」ときの原因の半分がこれです。**

> **試してみる**: `HuntView.swift:219` の `.padding` と `.background` の
> 行を入れ替えて、フィルターチップの見た目がどう変わるか見てください。

### 三項演算子 `? :`

```swift
active ? Color(argb: 0xFF7C4DFF).opacity(0.35) : .white.opacity(0.06)
```

「`active` が true なら紫、false なら白」という意味です。
`if` を1行で書く記法で、SwiftUI では多用されます。

---

## 2-5. レイアウト — 3つのスタック

| 名前 | 並べ方 | イメージ |
|---|---|---|
| `VStack` | 縦 | Vertical |
| `HStack` | 横 | Horizontal |
| `ZStack` | 重ねる | Z軸＝奥行き |

```swift
ZStack {
    Circle()
        .fill(Brand.gradient)
        .frame(width: 52, height: 52)
    Image(systemName: "camera.viewfinder")
        .font(.system(size: 23, weight: .bold))
        .foregroundStyle(.white)
}
```
> `ColorHuntApp.swift:156-165`

タブバー中央のカメラボタンです。**円の上にアイコンを重ねている**ので `ZStack`。
後に書いたものが手前に来ます。

### `spacing` と `alignment`

```swift
VStack(alignment: .leading, spacing: 12) { … }
```
> `HuntView.swift:237`

- `spacing` — 要素どうしの間隔
- `alignment` — 揃える位置（`.leading` = 左揃え）

**要素の間隔は `spacing` で指定するのが基本**です。
個々に `.padding` を付けると、間隔が二重になったり消えたりして崩れます。

### `frame` — 大きさの指定

```swift
.frame(maxWidth: .infinity, alignment: .leading)
```
> `HuntView.swift:177`

`maxWidth: .infinity` は「横いっぱいに広がる」。
`alignment: .leading` で、その中身は左に寄せます。

これはよく使う組み合わせです。指定しないと、View は中身のサイズになります。

---

## 2-6. ForEach — 配列から View を作る

```swift
LazyVGrid(columns: columns, spacing: 8) {
    ForEach(group.photos) { photo in thumb(photo) }
}
```
> `HuntView.swift:49-51`

写真の配列から、サムネイルを並べています。

### なぜ `Identifiable` が必要だったのか

第1章で `HuntPhoto: Identifiable` と書いたのを覚えているでしょうか。
`ForEach` はここで `id` を使います。

写真が1枚増えたとき、SwiftUI は
「**どれが新しくて、どれが元からあるか**」を `id` で見分けます。
見分けられれば、増えた1枚だけを描き足せます。全部描き直す必要がありません。

`id` が無いと、200枚全部を毎回描き直すことになり、動作が重くなります。

### `id: \.self` という書き方

```swift
ForEach(availableFilters, id: \.self) { key in
    filterChip(key, bucketLabel(key), active: state.huntFilter == key)
}
```
> `HuntView.swift:200-202`

`availableFilters` は `[String]` で、`String` は `Identifiable` ではありません。
そこで「文字列そのものをIDとして使う」と指定しています。
`\.self` の `\` はキーパスという記法です。

> ⚠️ 重複する値があると表示が壊れます。ここでは色キーが重複しないので安全です。

### `Lazy` が付く意味

`LazyVGrid` / `LazyVStack` の `Lazy` は「**画面に映る分だけ作る**」という意味です。

200枚の写真があっても、実際に描かれるのは画面に見えている十数枚だけ。
スクロールに応じて必要な分が作られます。`Lazy` の付かない `VStack` で
200枚並べると、一気に重くなります。

---

## 2-7. 見た目を1か所にまとめる — `Brand`

```swift
enum Brand {
    static let purple = Color(red: 124 / 255, green: 77 / 255, blue: 255 / 255)
    static let pink   = Color(red: 236 / 255, green: 64 / 255, blue: 122 / 255)
    static let amber  = Color(red: 255 / 255, green: 167 / 255, blue: 38 / 255)
    static let base   = Color(red: 0x10 / 255.0, green: 0x10 / 255.0, blue: 0x14 / 255.0)

    static let gradient = LinearGradient(
        colors: [purple, pink, amber],
        startPoint: .topLeading, endPoint: .bottomTrailing
    )
}
```
> `ColorHuntApp.swift:4-16`

色をあちこちに直接書くと、後から変えたいときに全ファイルを探すことになります。
`Brand` にまとめておけば、**1か所直せば全画面に反映**されます。

> `enum` なのは「インスタンスを作る必要がない、ただの入れ物」だからです。
> `struct` でもできますが、`enum` だと誤って `Brand()` と書けないので、
> 意図が明確になります。

### 再利用できる部品を作る

```swift
struct PopButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.6), value: configuration.isPressed)
    }
}
```
> `ColorHuntApp.swift:177-183`

「押すと少し縮む」という動きを部品にしています。使うときは1行です。

```swift
.buttonStyle(PopButtonStyle())
```

アプリ全体でボタンの手触りが統一されます。

---

## 2-8. アニメーション

```swift
.onTapGesture {
    withAnimation(.easeInOut(duration: 0.15)) {
        state.huntFilter = (state.huntFilter == key ? nil : key)
    }
}
```
> `HuntView.swift:223-227`

**`withAnimation { }` の中で状態を変えると、その変化がアニメーションになります。**

書いているのは「フィルターを切り替える」ことだけです。
「チップの色を0.15秒かけて変える」とは一言も書いていません。
状態が変われば見た目が変わる、その差分を SwiftUI がなめらかに繋ぎます。

---

## 2-9. UIKit との橋渡し（発展）

SwiftUI だけでは作れないものもあります。ColorHunt ではカメラがそれです。

```swift
var videoPreviewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
```
> `HuntCameraView.swift:366`

カメラのプレビューは古い仕組み（UIKit / AVFoundation）でしか作れないため、
`UIViewRepresentable` という仕組みで SwiftUI に埋め込んでいます。

**今は「そういう逃げ道がある」とだけ知っておけば十分です。**
`HuntCameraView.swift` は1546行あり、最後に読むべきファイルです。

---

## この章のまとめ

| 概念 | 一言で |
|---|---|
| View | 状態から見た目を計算する関数 |
| `some View` | body に書くおまじない |
| View は struct | 何度も作り直される。データを持たせない |
| `@State` | この画面だけの一時的な状態 |
| `@StateObject` | class を作って持つ（アプリで1個） |
| `@EnvironmentObject` | 上から配られた class を使う |
| `@Published` | 変わったら画面を更新する |
| `$変数` | 読み書きできる窓口を渡す |
| モディファイア | 包む。**順番で結果が変わる** |
| `VStack`/`HStack`/`ZStack` | 縦・横・重ね |
| `ForEach` | 配列から View を作る（`id` が必要） |
| `Lazy` | 画面に映る分だけ作る |
| `withAnimation` | 中の変化がアニメーションになる |

---

### 次に読む

→ **[03-concurrency.md](03-concurrency.md)** — 200枚の解析中も画面が固まらない仕組み
