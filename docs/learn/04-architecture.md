# 4. ColorHunt の設計 — なぜこの構造なのか

ここからは文法ではなく**設計**の話です。
「どう書くか」ではなく「**なぜそう決めたか**」を扱います。

設計に唯一の正解はありません。この章では、選んだ理由と、
**選ばなかった選択肢、そして今の設計の弱点**まで正直に書きます。

---

## 4-1. 全体は3層

```
┌─────────────────────────────────────────────┐
│  画面層（SwiftUI View）                       │
│  HuntView / CollageView / HuntCameraView …   │
│                                              │
│  「今の状態から、どう見えるか」だけを書く       │
│  データは持たない                             │
└──────────────┬──────────────────────────────┘
               │ 読む: state.photos
               │ 頼む: state.add(images:)
┌──────────────▼──────────────────────────────┐
│  状態層（AppState）                           │
│                                              │
│  アプリのデータをすべて握る唯一の場所           │
│  写真・選択・順序・今日の色・Pro・地図ピン       │
└──────────────┬──────────────────────────────┘
               │ 色の判定 / 座標の計算を頼む
┌──────────────▼──────────────────────────────┐
│  共通ロジック層（SharedColor / Kotlin製）      │
│                                              │
│  ColorBridge   … 色を12バケツに分類            │
│  CollageBridge … コラージュのコマ座標を計算      │
│                                              │
│  Android 版とまったく同じコードが動く           │
└─────────────────────────────────────────────┘
```

**依存は上から下へ一方通行**です。
`AppState` は View を知りません。Kotlin 層は Swift を知りません。

この一方通行が守られていると、下の層を単体でテストでき、
上の層を作り直しても下は影響を受けません。

---

## 4-2. なぜ `AppState` 1個なのか

ColorHunt のデータは、796行の `AppState` クラスに全部入っています。

```swift
@MainActor
final class AppState: ObservableObject {
    @Published var photos: [HuntPhoto] = []
    @Published var selection: Set<UUID> = []
    @Published var todayColor: Int32?
    @Published var isPro: Bool
    …
}
```

### 選ばなかった選択肢

もっと細かく分ける設計もありえました。

```
PhotoStore      … 写真だけ
SelectionStore  … 選択だけ
PurchaseStore   … 課金だけ
```

一般には、こちらのほうが「正しい」とされます。1つのクラスが小さくなり、
テストしやすくなるからです。

### それでも1個にした理由

**このアプリでは、データどうしが密に絡んでいるからです。**

```swift
func toggleSelection(_ id: UUID) {
    if selection.contains(id) {
        selection.remove(id)
        collageOrder.removeAll { $0 == id }   // ← 選択を外すと順序からも消す
    } else {
        selection.insert(id)
        collageOrder.append(id)               // ← 選ぶと順序の末尾に足す
    }
}
```
> `AppState.swift:229-237`

「写真を1枚選ぶ」だけで、`selection` と `collageOrder` の2つが同時に変わります。
これを別々のクラスに分けると、**片方だけ更新されて食い違う**バグの余地が生まれます。

同じことがカメラからの受け渡しでも起きます。

```swift
photos.append(contentsOf: fresh)
let ids = fresh.map { $0.id }
selection = Set(ids)
collageOrder = ids
pendingCollageTemplateID = templateID
pendingCollageLayout = layoutOrdinal
selectedTab = .collage
```
> `AppState.swift:204-210`

撮影が終わった瞬間に、**6つの状態が一斉に変わります**。
1つのクラスの中なら、この6行が同時に走ることが保証されます。

> **判断基準**: 一緒に変わるものは、一緒に置く。
> 別々に変わるものだけ、分ける。

### この判断の代償

正直に書くと、**796行は大きすぎます**。実際に読みにくい箇所があります。
アプリがこれ以上育つなら、`MapStore`（地図ピン）と `PurchaseStore`（課金）は
切り出せます。この2つは他と絡んでいないからです。

**今のサイズなら1個のままが有利**、という判断です。
「分けるのが常に正しい」わけではありません。

---

## 4-3. Kotlin との共有 — なぜ、どこまで

ColorHunt は iOS と Android の両方があります。
**色の判定が両OSでズレたら、アプリとして破綻します**。
「Androidだと赤なのに iPhone だと橙」では、同じアプリと呼べません。

そこで、**色の分類とコラージュの座標計算だけを Kotlin で書き、
両OSで同じコードを動かしています**（Kotlin Multiplatform）。

### 境界を「数値と文字列」だけにした理由

```kotlin
object ColorBridge {
    fun bucketKeys(): List<String>              // → Swift では [String]
    fun classifyKey(colorInt: Int): String      // → Swift では String
    fun swatchOf(key: String): Int              // → Swift では Int32
}
```
> `shared/src/commonMain/kotlin/…/ColorBridge.kt`

Swift 側からはこう見えます。

```swift
let order = ColorBridge.shared.bucketKeys()                    // [String]
photos[idx].bucketKey = ColorBridge.shared.classifyKey(colorInt: color)
Color(packed: Int32(truncatingIfNeeded: ColorBridge.shared.swatchOf(key: key)))
```
> `AppState.swift:98, 225` / `HuntView.swift:213`

Kotlin 側には `ColorBucket` という enum があるのに、
**Swift には文字列（"RED" など）で渡しています**。なぜでしょうか。

**Kotlin の enum や sealed class は、Swift から見ると扱いが不安定だからです。**
名前が変わったり、比較がうまく動かなかったりします。
一方 `String` や `Int` や `List<String>` は、**確実に素直な Swift の型になります**。

> **設計の教訓**: 異なる技術の境界には、**一番単純なものだけ**を通す。
> 賢いものを通そうとすると、境界で壊れます。

### 何を共有し、何を共有しないか

| | 共有する（Kotlin） | 共有しない（各OSで別々） |
|---|---|---|
| 内容 | 色の分類、コラージュの座標計算 | 画面、カメラ、保存、課金 |
| 理由 | **ズレたら困る計算** | OSごとに作法がまるで違う |

「全部共有すれば楽」ではありません。画面まで共有しようとすると、
どちらのOSでも不自然なアプリになります。**ズレると困るところだけ**を共有する、
という線引きです。

---

## 4-4. 保存の設計 — 画像とメタデータを分ける

### 素朴なやり方の問題

「`photos` を丸ごと JSON で保存する」——これはできません。
`UIImage`（画像そのもの）は JSON にできず、できたとしても
200枚分で数百MBの JSON になります。

### ColorHunt のやり方

**画像は JPEG ファイル、それ以外は JSON。**

```
Documents/HuntStore/
├── manifest.json          ← 軽い。ID・色・バケツ・選択・順序
├── <UUID1>.jpg            ← 画像1枚ずつ
├── <UUID2>.jpg
└── …
```

```swift
private struct StoredPhoto: Codable {
    let id: UUID
    let hash: Int?
    let color: Int32?
    let bucket: String?
}
```
> `AppState.swift:270-275`

**画像は入っていません。** `id` から
`Documents/HuntStore/<id>.jpg` というファイル名が決まるので、
参照を持つ必要すらありません。

この形の利点は、

- `manifest.json` が小さい（200枚でも数十KB）ので、保存が一瞬で終わる
- 写真1枚だけ消したいとき、そのファイルを消すだけで済む
- 起動時、画像は**必要になってから**読める

### ハッシュに Swift 標準の `Hasher` を使わなかった理由

同じ写真を二重に取り込まないよう、画像から「指紋」を作っています。
このとき **Swift 標準の `Hasher` を使うと壊れます**。

**`Hasher` は、アプリを起動するたびに違う値を返すからです**
（セキュリティ上の理由で、起動ごとに乱数の種が変わります）。

保存した指紋と、次回起動後に計算した指紋が一致しなくなり、
**再起動のたびに全部が「新しい写真」に見えて重複します**。

そこで、起動しても値が変わらない自前のハッシュ（FNV-1a）を使っています。

> **教訓**: 「保存する値」に、標準ライブラリのハッシュを使ってはいけません。
> これは Swift に限らず多くの言語で共通の落とし穴です。

### `@State` は保存されない — 実際に踏んだバグ

コラージュ画面の設定（テンプレ・余白・角丸・背景・トリミング…）は
`CollageView` の `@State` に置いていました。動いている間は完璧です。
ところが **アプリをキルして起動し直すと、全部が初期値に戻る**。

理由は単純で、`@State` は「画面が生きている間の記憶」だからです。
保存先はメモリで、プロセスが終われば消えます。

ここで大事なのは、**3つの箱を意識的に選ぶ**ということです。

| 箱 | 寿命 | ColorHunt での用途 |
|----|------|--------------------|
| `@State` | その画面が表示されている間 | シートの開閉、ドラッグ中の指の位置 |
| `@Published`（AppState） | アプリが起動している間（＋manifestで保存） | 写真・選択・並び順 |
| `UserDefaults` | アプリを消すまで | 設定値（コラージュ設定・通知時刻・Pro） |

**「消えて困るか？」で決めます。** 開閉状態は消えて構いません。
ユーザーが10分かけて整えたコラージュの設定は、消えたら困ります。

直し方も、まとめ方がポイントでした。設定は15個あります。
15個それぞれに保存コードを書くと、**次に設定を1個増やしたとき、
保存を書き忘れて同じバグが再発します**。そこで1個の値に畳みました。

```swift
private struct CollageSettings: Equatable, Codable {
    var layout: Int32 = 0
    var spacing: CGFloat = 4
    …
}
```

こうすると保存側は画面全体で**1行**で済みます。

```swift
.onChange(of: settingsSnapshot) { snapshot in
    CollageSettingsStore.save(snapshot)
}
```

`Equatable` にしてあるので、値が本当に変わったときだけ動きます。

読み戻しは `onAppear` ではなく `init()` でやります。

```swift
init() {
    let saved = CollageSettingsStore.load()
    _spacing = State(initialValue: saved.spacing)   // アンダースコア付きに注意
    …
}
```

`onAppear` は「画面が出たあと」に走るので、**1フレームだけ初期値が見えて
チラつきます**。`init()` なら最初の描画からもう復元後の姿です。
`_spacing` のようにアンダースコアを付けると `@State` の入れ物そのものを
指せて、初期値を差し替えられます。

---

## 4-5. カメラ — SwiftUI で作れないものの扱い

カメラのプレビューは SwiftUI だけでは作れません。
`AVCaptureVideoPreviewLayer` という古い仕組み（UIKit）が必要です。

```swift
var videoPreviewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
```
> `HuntCameraView.swift:366`

`UIViewRepresentable` という仕組みで、UIKit の部品を SwiftUI に埋め込んでいます。

### ここで起きた実際のバグ

フレームモードで枠が広がるとき、**プレビューの動きがワンテンポ遅れる**という
問題が起きました。原因は、

- SwiftUI 側はアニメーションで枠を広げている
- UIKit の `AVCaptureVideoPreviewLayer` は、**自分でも勝手にアニメーションする**
- 2つのアニメーションが競合してズレる

解決策は、UIKit 側のアニメーションを止めることでした。

```swift
CATransaction.setDisableActions(true)
```

> **教訓**: 新しい仕組み（SwiftUI）と古い仕組み（UIKit）の境界では、
> **両方が同じことをやろうとして衝突**します。
> 境界のコードは、どちらの世界のルールも知っている必要があります。

---

## 4-6. 無料と Pro の境界も設計

課金は「機能を止める」だけではなく、**設計の問題**です。

ColorHunt の線引きはこうなっています。

| | 無料 | Pro |
|---|---|---|
| テンプレート | 5種 | +7種（ハーフ・チェキ・シームレス ほか） |
| 書き出し | 透かし付き | 透かしなし・2160px |
| カメラのフレームモード | ✅ 使える | ✅ |

### なぜ「フレームモード」を無料にしたのか

フレームモードはこのアプリ最大の差別化です。
普通に考えれば、ここを Pro にしたくなります。

しかし**無料にしました**。理由は、

- 透かし付きの書き出し画像が SNS に流れる＝**無料ユーザーが広告塔**になる
- 一番おもしろい機能を触れないアプリは、そもそも使われない

### なぜ Pro テンプレは「真似できない」設計なのか

初期の実装では、無料の設定（余白・枠線・背景色）を組み合わせると
**Pro テンプレとほぼ同じものが作れてしまいました**。これでは課金する理由がありません。

そこで Pro テンプレには、設定では作れない**固有の装飾**を持たせました。

```swift
TemplateSignatureDeco   // ハーフの縁、チェキの下部の余白、Y2K の装飾 …
```
> `CollageView.swift`

**「設定の組み合わせ」ではなく「構造的に別物」**にすることで、
Pro の価値が保たれます。

> **教訓**: 課金の境界は、あとから足せません。
> 「無料版の設定を工夫したら同じものが作れる」状態は、設計の失敗です。

---

## 4-7. この設計の弱点（正直な話）

良い設計を学ぶには、**弱点を見る目**が要ります。ColorHunt の現状を正直に挙げます。

### 弱点1: 写真がメモリに載り続ける ★最重要

```swift
struct HuntPhoto: Identifiable, Equatable {
    let image: UIImage      // ← 実物の画像がそのまま
```

200枚だと **900MB 前後**になります。古い iPhone では強制終了の危険があります。

**あるべき姿**: 一覧にはサムネイル（小さい画像）だけを持ち、
原寸は必要なときだけディスクから読む。

**なぜそうしなかったか**: v1.0 の期限を優先しました。
これは意図的な借金で、`IOS_LAUNCH_GUIDE.md` §3.4 に測定手順が書いてあります。
**設計を知っていて先送りするのと、気づいていないのは全く違います。**

### 弱点2: `CollageView.swift` が2058行

プレビュー・編集・書き出し・課金UI・テンプレ定義・設定の保存が1ファイルに
同居しています。
テンプレ定義（`CollageTemplateVM` の一覧）だけでも別ファイルに出せます。

### 弱点3: 自動テストが Kotlin 側にしかない

色の分類には21個のテストがありますが、**Swift 側にはテストがありません**。
`AppState` の重複排除や保存・読み込みは、テストの価値が高い部分です。

### 弱点4: `.xcodeproj` がリポジトリに無い

署名設定・権限の文言・アイコンの割り当てが Mac 上にしか存在しません。
**Mac が壊れると失われます。**

---

## 4-8. 設計を評価する4つの問い

これから自分でコードを書くとき、この4つを自問してください。

1. **一緒に変わるものが、一緒に置かれているか**
   → バラバラだと、片方だけ更新されるバグが出ます

2. **依存は一方通行か**
   → View が AppState を知るのはOK。AppState が View を知り始めたら赤信号です

3. **境界に、単純なものだけを通しているか**
   → Kotlin との境界を `String` と `Int` に絞った理由がこれです

4. **弱点を、自分で言えるか**
   → 言えるなら、それは選択です。言えないなら、まだ理解していません

---

## この章のまとめ

| 決定 | 理由 |
|---|---|
| 3層（画面 / 状態 / 共通ロジック） | 依存を一方通行にして、下層を独立させる |
| `AppState` 1個 | 一緒に変わるものを一緒に置く。ただし796行は限界に近い |
| Kotlin 共有は色と座標だけ | ズレたら困る計算だけ。画面は共有しない |
| 境界は `String` / `Int` のみ | Kotlin の enum は Swift 側で壊れる |
| 画像は JPEG、他は JSON | manifest を軽く保ち、保存を速くする |
| 自前ハッシュ（FNV-1a） | 標準の `Hasher` は起動ごとに値が変わる |
| フレームモードは無料 | 透かし付きの拡散が広告塔になる |
| Pro テンプレは構造的に別物 | 設定の組み合わせで再現できてはいけない |

---

### 次に読む

→ **[05-code-tour.md](05-code-tour.md)** — ボタンを押してから画面が変わるまでを追う
