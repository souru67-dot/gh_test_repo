# 1. Swift の文法 — ColorHunt のコードで学ぶ

すべての例は実際の ColorHunt のコードです。Xcode で **⌘⇧O** →ファイル名で開いて、
同じ行を見ながら読んでください。

---

## 1-1. 変数と定数 — `let` と `var`

Swift では、**まず `let` を使う**のが鉄則です。

```swift
let id: UUID              // 後から変えられない（定数）
var dominantColor: Int32? // 後から変えられる（変数）
```
> `AppState.swift:16-19`（`HuntPhoto` の中身）

`HuntPhoto` を見ると、`id` は `let`、`dominantColor` は `var` です。理由は明確で、

- **写真のIDは、作られた後に変わることがない** → `let`
- **色は「解析前は不明、解析後に決まる」** → `var`

### なぜ `let` を優先するのか

`let` にしておくと「この値は途中で変わらない」とコンパイラが保証してくれます。
バグの多くは「いつの間にか値が変わっていた」ことが原因なので、
変える必要が出てから `var` にする、という順番が安全です。

> **試してみる**: `AppState.swift:16` の `let id` を `var id` に変えても
> ビルドは通ります。逆に `var dominantColor` を `let` にすると、
> `finishAnalysis` の中でエラーになります。「なぜエラーになるか」を
> 読んでみてください。

---

## 1-2. 型 — 書かなくても推論される

```swift
@Published var importing = false        // Bool と推論される
@Published var photos: [HuntPhoto] = [] // 型を明示（空配列だと推論できないため）
```
> `AppState.swift:67, 48`

`= false` と書けば `Bool` だと分かるので `: Bool` は不要です。
一方 `= []` は「何の配列か」が分からないので、`[HuntPhoto]` と書く必要があります。

### よく出る型

| 書き方 | 意味 | ColorHunt での例 |
|---|---|---|
| `Int` / `Int32` / `Int64` | 整数 | 色は `Int32`（0xFFRRGGBB を詰めている） |
| `Double` | 小数 | 色の計算 `Double((argb >> 16) & 0xFF) / 255.0` |
| `Bool` | true / false | `importing`, `isPro` |
| `String` | 文字列 | `bucketKey`（"RED" など） |
| `[要素の型]` | 配列 | `[HuntPhoto]`, `[UUID]` |
| `Set<要素の型>` | 重複なしの集合 | `selection: Set<UUID>` |
| `[キー: 値]` | 辞書 | `hashByID: [UUID: Int]` |
| `UUID` | 一意なID | 写真1枚ごとのID |

### `Set` を使う理由

選択中の写真を `selection: Set<UUID>` にしているのには理由があります。

```swift
@Published var selection: Set<UUID> = [] { didSet { scheduleSave() } }
```
> `AppState.swift:49`

- **重複しない** — 同じ写真を2回選択できない
- **「含まれているか」の判定が速い** — 配列だと全部見る必要がありますが、
  `Set` は一発で分かります。200枚あっても速度が落ちません

```swift
if selection.contains(id) { … }  // 速い
```
> `AppState.swift:230`

---

## 1-3. オプショナル — Swift 最大の特徴

**`?` が付いた型は「値が無いかもしれない」という意味**です。

```swift
var dominantColor: Int32?  // 解析前は「まだ無い」
var bucketKey: String?     // 分類前は「まだ無い」
```
> `AppState.swift:19-21`

多くの言語ではこれを `null` で表しますが、うっかり `null` のまま使ってクラッシュ
するのが定番のバグでした。Swift は**「無いかもしれない値」を型で区別**して、
中身を取り出さないと使えなくします。

### 取り出し方1: `if let`

```swift
if let color {
    photos[idx].bucketKey = ColorBridge.shared.classifyKey(colorInt: color)
}
```
> `AppState.swift:223-226`

「`color` に値があれば、それを `color` という名前で使う」という意味です。
値が無ければ `{ }` の中は実行されません。

> 昔は `if let color = color {` と書きましたが、Swift 5.7 以降は
> 同じ名前なら `if let color {` と省略できます。

### 取り出し方2: `guard let`（早期リターン）

```swift
private func finishAnalysis(id: UUID, color: Int32?) {
    guard let idx = photos.firstIndex(where: { $0.id == id }) else { return }
    photos[idx].dominantColor = color
    …
}
```
> `AppState.swift:220-222`

「見つからなければ、この関数はここで終わり」という書き方です。

**`if let` との違いは、取り出した値がその後もずっと使えること**です。
`guard` を抜けた後の行で `idx` が使えているのが分かると思います。
`if let` だと `{ }` の中でしか使えません。

処理の前提条件を先にはじく用途では `guard` を使います。
コードが右にずれていかないので読みやすくなります。

### 取り出し方3: `?.`（オプショナルチェーン）

```swift
await self?.appendUnique(result, unreadable: lost)
```
> `AppState.swift:126`

「`self` があれば `appendUnique` を呼ぶ、無ければ何もしない」。
`self` が既に消えている可能性があるので、`?` を付けています
（→ 1-8 の `weak self` で詳しく）。

### 取り出し方4: `??`（デフォルト値）

```swift
photos.first?.image ?? UIImage()
```

「左が nil なら右を使う」。ColorHunt では設定値の取得などで使われます。

---

## 1-4. `struct` と `class` — 一番大事な違い

ColorHunt には両方が出てきます。**この違いが分かると設計の意図が読めます。**

```swift
struct HuntPhoto: Identifiable, Equatable { … }   // 値
final class AppState: ObservableObject { … }      // 参照
```
> `AppState.swift:15, 43`

### 違いは「コピーされるか」

```swift
// struct（値型）: コピーされる
var a = HuntPhoto(image: img, dominantColor: nil, bucketKey: nil)
var b = a          // 中身がまるごとコピーされる
b.bucketKey = "RED"
// → a.bucketKey は nil のまま。b だけが変わる

// class（参照型）: 同じものを指す
let x = AppState()
let y = x          // 同じ AppState を指しているだけ
y.selectedTab = .collage
// → x.selectedTab も .collage になる（同じ実体だから）
```

### なぜ使い分けるのか

| | `struct` | `class` |
|---|---|---|
| ColorHunt での例 | `HuntPhoto`, `Manifest`, すべての View | `AppState` |
| 性質 | コピーされる | 共有される |
| 使いどころ | **データ** | **共有したい状態** |

`AppState` が `class` なのは、**すべての画面が「同じ1個」を見る必要がある**からです。
ハント画面で写真を選んだら、コラージュ画面にも反映されなければ困ります。
コピーされてしまう `struct` では実現できません。

逆に `HuntPhoto` が `struct` なのは、写真1枚1枚は独立したデータで、
共有する必要が無いからです。コピーされたほうが安全です。

> **`final` とは**: 「このクラスは継承させない」という指定です。
> 継承の予定が無いなら付けておくと、実行速度がわずかに上がります。

---

## 1-5. プロトコル — 「これができます」という約束

```swift
struct HuntPhoto: Identifiable, Equatable {
```
> `AppState.swift:15`

`:` の後ろに並んでいるのが**プロトコル**です。
「この型は、こういう機能を持っています」という宣言だと思ってください。

| プロトコル | 意味 | なぜ必要か |
|---|---|---|
| `Identifiable` | `id` を持つ | SwiftUI の `ForEach` が「どれがどれか」を見分けるため |
| `Equatable` | `==` で比較できる | 変化を検知するため |
| `Codable` | JSON に変換できる | 保存・読み込みのため |
| `Hashable` | `Set` や辞書のキーにできる | `AppTab` がタブの識別に使う |

### `Equatable` を自分で書いている理由

```swift
static func == (lhs: HuntPhoto, rhs: HuntPhoto) -> Bool { lhs.id == rhs.id }
```
> `AppState.swift:30`

普通は Swift が自動で作ってくれますが、ここでは**手書き**しています。
なぜなら、自動生成だと `image`（画像そのもの）まで比較してしまい、
200枚 × 全ピクセルの比較が走って激烈に遅くなるからです。

**「IDが同じなら同じ写真」**と決めてしまえば、比較は一瞬で終わります。

### `Codable` — 保存のための変換

```swift
private struct StoredPhoto: Codable {
    let id: UUID
    let hash: Int?
    let color: Int32?
    let bucket: String?
}
```
> `AppState.swift:270-275`

`Codable` と書くだけで、この構造体は JSON に変換できるようになります。
画像そのものは重いので JSON に入れず、別途 JPEG として保存し、
**「それ以外の情報」だけ**をこの型で保存しています。

---

## 1-6. 関数とクロージャ

### 関数

```swift
func rebucket(_ id: UUID, to key: String) {
    guard let idx = photos.firstIndex(where: { $0.id == id }) else { return }
    photos[idx].bucketKey = key
}
```
> `AppState.swift:240-243`

呼ぶときはこうなります。

```swift
state.rebucket(photo.id, to: "RED")
```

**Swift の関数は、引数に名前が付きます。** `to:` の部分です。
`_` は「呼ぶときに名前を書かない」という指定です。

読むと英文のようになるのが Swift らしさで、
`rebucket(photo.id, to: "RED")` は「photo.id を RED に付け替える」と読めます。

### クロージャ — 名前のない関数

```swift
photos.filter { selection.contains($0.id) }
```
> `AppState.swift:88`

`{ }` の中が**クロージャ**です。「選択に含まれる写真だけ残す」という条件を
`filter` に渡しています。

**`$0` は「1個目の引数」**という意味の省略記法です。丁寧に書くとこうなります。

```swift
photos.filter { photo in selection.contains(photo.id) }
```

慣れるまでは `$0` を頭の中で「今見ている1個」に置き換えて読んでください。

### よく使う3つ

```swift
// filter — 条件に合うものだけ残す
photos.filter { $0.bucketKey == nil }
// AppState.swift:103 — 未解析の写真だけ

// map — 全部を別のものに変換する
photos.map { $0.id }
// AppState.swift:246 — 写真の配列 → IDの配列

// compactMap — 変換して、nil は捨てる
collageOrder.compactMap { byId[$0] }
// AppState.swift:93 — IDの配列 → 写真の配列（見つからないIDは無視）
```

この3つが読めれば、ColorHunt のコードの大半は読めます。

### 末尾クロージャ

最後の引数がクロージャのとき、カッコの外に出せます。

```swift
// 正式
Task(priority: .userInitiated, operation: { … })
// 末尾クロージャ（実際のコード）
Task.detached(priority: .userInitiated) { … }
```

SwiftUI はこの記法を多用します。`VStack { … }` も実は関数呼び出しです。

---

## 1-7. enum — 選択肢を型にする

```swift
enum AppTab: Hashable {
    case hunt, collage, camera, today, grid, map
}
```
> `AppState.swift:33-37`

タブを `String` で `"hunt"` のように扱うと、打ち間違えてもコンパイラが気づけません。
`enum` にしておけば、**存在しない選択肢を書いた時点でエラー**になります。

使うときは型が分かっていれば `.` から書けます。

```swift
state.selectedTab = .collage
```
> `AppState.swift:210`

---

## 1-8. メモリ管理 — `weak self` はなぜ要るのか

```swift
Task.detached(priority: .userInitiated) { [weak self] in
    let color = DominantColor.extract(from: photo.image)
    await self?.finishAnalysis(id: photo.id, color: color)
}
```
> `AppState.swift:146-149`

`[weak self]` と `self?` が謎に見えると思います。順に説明します。

### 何が起きているか

Swift は「誰からも参照されなくなったオブジェクト」を自動で解放します。
逆に言うと、**参照が残っている限り解放されません**。

上のコードは「重い色解析を裏で走らせて、終わったら `AppState` に報告する」処理です。
このとき普通に書くと、クロージャが `AppState` を掴み続けます。

- 画面が閉じて `AppState` が不要になっても
- クロージャが掴んでいるので解放されない
- → **メモリリーク**

`[weak self]` は「掴むけれど、解放を邪魔しない」という指定です。
その代わり、**使うときには既に消えているかもしれない**ので、
`self?` とオプショナルになります。

### 覚え方

> **裏で長く走る処理の中で `self` を使うときは `[weak self]`。**

いまは「そういうお作法」で構いません。ColorHunt では色解析・保存・
写真の読み込みなど、時間のかかる処理すべてに付いています。

---

## 1-9. extension — 既存の型に機能を足す

```swift
extension Color {
    /// Packed 0xAARRGGBB / 0xFFRRGGBB → SwiftUI Color.
    init(argb: Int64) {
        let r = Double((argb >> 16) & 0xFF) / 255.0
        let g = Double((argb >> 8) & 0xFF) / 255.0
        let b = Double(argb & 0xFF) / 255.0
        self.init(red: r, green: g, blue: b)
    }
}
```
> `AppState.swift:732-739`

SwiftUI の `Color` は Apple が作った型ですが、**後から機能を追加できます**。
これが `extension` です。

ここでは「`0xFF7C4DFF` のような数値から `Color` を作る」機能を足しています。
おかげでアプリ中どこでもこう書けます。

```swift
Color(argb: 0xFF7C4DFF)   // ブランドの紫
```
> `HuntView.swift:180` ほか多数

### `>>` と `&` は何をしているか

色は `0xFF7C4DFF` という1つの数値に、4つの情報が詰まっています。

```
0xFF 7C 4D FF
  │   │  │  └─ 青  (0xFF = 255)
  │   │  └──── 緑  (0x4D = 77)
  │   └─────── 赤  (0x7C = 124)
  └─────────── 不透明度
```

- `argb >> 16` — 16ビット右にずらす（赤が一番右に来る）
- `& 0xFF` — 一番右の8ビットだけ取り出す
- `/ 255.0` — 0〜255 を 0.0〜1.0 に変換（SwiftUI はこの形式を使うため）

Android 版とまったく同じ数値の持ち方をしているので、
**両OSで色が1ビットも狂いません。**

---

## 1-10. アクセス制御 — `private`

```swift
private var photoHashes = Set<Int>()
private func appendUnique(…)
```
> `AppState.swift:109, 130`

`private` が付いたものは、**そのファイルの外から触れません**。

`AppState` は796行あり、外から使ってほしい機能（`add`, `toggleSelection`, `clearAll`）と、
内部の都合（`photoHashes`, `saveManifest`）が混在しています。
`private` を付けることで「外から使うのはこれだけ」が明確になります。

**初心者のうちは、迷ったら `private` を付けてください。**
外から必要になったときに外せばいいだけです。

---

## この章のまとめ

| 概念 | 一言で | ColorHunt での代表例 |
|---|---|---|
| `let` / `var` | 変えないなら `let` | `let id` / `var bucketKey` |
| オプショナル `?` | 値が無いかもしれない | `dominantColor: Int32?` |
| `if let` / `guard let` | 中身を安全に取り出す | `guard let idx = …` |
| `struct` | コピーされるデータ | `HuntPhoto` |
| `class` | 共有される状態 | `AppState` |
| プロトコル | 「これができます」の約束 | `Identifiable`, `Codable` |
| クロージャ `{ }` | 名前のない関数 | `filter { $0.bucketKey == nil }` |
| `enum` | 選択肢を型にする | `AppTab` |
| `[weak self]` | 裏で走る処理のお作法 | 色解析の `Task` |
| `extension` | 既存の型に機能を足す | `Color(argb:)` |
| `private` | 外から触らせない | `photoHashes` |

---

### 次に読む

→ **[02-swiftui.md](02-swiftui.md)** — この文法で、画面がどう組み立てられているか
