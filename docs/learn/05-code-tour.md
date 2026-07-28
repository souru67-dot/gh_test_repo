# 5. コードを追う練習 — ボタンを押してから画面が変わるまで

**コードが読めるようになる、というのは「処理を追える」ということ**です。
この章では3本のルートを、実際の行番号で最初から最後まで追います。

Xcode を開き、**⌘⇧O**（Open Quickly）でファイルを行き来しながら読んでください。

> **Xcode の便利な操作**
> - `⌘⇧O` … ファイル名で開く
> - `⌘クリック` … 定義へジャンプ（一番使います）
> - `⌃⌘←` … 戻る
> - `⌘⇧F` … プロジェクト全体を検索

---

## ツアー1: 「自動で仕分け」を押してから、色別に並ぶまで

一番大事なルートです。**5つのファイルをまたぎます。**

### ① ボタンが押される

`HuntView.swift` の中に、写真を取り込むボタンがあります。押されると
`AppState` の関数を呼びます。

```swift
state.importRecentLibraryPhotos()
```

**ここが「画面層 → 状態層」の境界**です。
View は「取り込んで」と頼むだけで、どうやるかは知りません。

### ② 写真ライブラリの許可を取る

```swift
PHPhotoLibrary.requestAuthorization(for: .readWrite) { [weak self] status in
    guard status == .authorized || status == .limited else {
        Task { @MainActor in self?.importing = false }
        return
    }
```
> `AppState.swift:439-443`

- `.authorized` … 全部の写真にアクセスできる
- `.limited` … ユーザーが選んだ写真だけ

**どちらでも先に進みます**（`||` は「または」）。
拒否された場合は `importing = false` にしてスピナーを止め、何もせず終わります。

> `Task { @MainActor in … }` は「メインスレッドに戻して実行」。
> このクロージャはバックグラウンドで呼ばれるため、
> `@Published` を触るには明示的に戻す必要があります（第3章）。

### ③ 直近200枚を取ってくる

```swift
DispatchQueue.global(qos: .userInitiated).async {
    let options = PHFetchOptions()
    options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
    options.fetchLimit = limit
    let assets = PHAsset.fetchAssets(with: .image, options: options)
```
> `AppState.swift:444-448`

- `sortDescriptors` … **撮影日の新しい順**に並べる（`ascending: false`）
- `fetchLimit = limit` … 上から200枚だけ

`PHAsset` は「写真そのもの」ではなく「写真への参照」です。
実物の画像は次で取り出します。

### ④ 1枚ずつ画像を受け取る

```swift
var images: [UIImage] = []
assets.enumerateObjects { asset, _, _ in
    manager.requestImage(
        for: asset,
        targetSize: CGSize(width: 1200, height: 1200),
        contentMode: .aspectFit,
        options: req
    ) { image, _ in
        if let image { images.append(image) }
    }
}
```
> `AppState.swift:457-467`

`targetSize` で **1200×1200 程度に縮めて**受け取っています。
原寸（4000×3000など）で200枚受け取ったら、メモリが即死します。

`if let image` に注目してください。**画像が返ってこないことがあります**
（iCloud にあってダウンロードに失敗した場合など）。その場合は黙って飛ばします。

### ⑤ 取れなかった枚数を数える

```swift
let collected = images
let missed = max(assets.count - collected.count, 0)
Task { @MainActor in
    self?.add(images: collected, unreadable: missed)
    self?.importing = false
}
```
> `AppState.swift:469-476`

**「200枚ぴったりにならない」の理由の半分がここ**です。
頼んだ枚数（`assets.count`）と受け取れた枚数（`collected.count`）の差を数えて、
後でトーストに出します。

### ⑥ 重複を除いて追加する

```swift
func add(images: [UIImage], unreadable: Int? = nil) {
    Task.detached(priority: .userInitiated) { [weak self] in
        var pairs: [(UIImage, Int)] = []
        for image in images {
            if let h = DominantColor.quickHash(image) { pairs.append((image, h)) }
        }
        …
        await self?.appendUnique(result, unreadable: lost)
    }
}
```
> `AppState.swift:115-128`

バックグラウンドで、全画像の「指紋」を計算します（第3章の黄金パターン）。

```swift
for (image, hash) in pairs where !photoHashes.contains(hash) {
    photoHashes.insert(hash)
    let photo = HuntPhoto(image: image, dominantColor: nil, bucketKey: nil)
    hashByID[photo.id] = hash
    fresh.append(photo)
}
photos.append(contentsOf: fresh)
```
> `AppState.swift:132-138`

`where !photoHashes.contains(hash)` が**重複スキップ**です。
すでに持っている指紋の写真は、この `for` を素通りします。

**「200枚ぴったりにならない」のもう半分がここ**です。

このとき作られる `HuntPhoto` は `dominantColor: nil, bucketKey: nil`。
**まだ色は分かっていません。** ここが次に繋がります。

### ⑦ 1枚ずつ色を解析する

```swift
for photo in fresh {
    persistPhotoFile(photo)
    Task.detached(priority: .userInitiated) { [weak self] in
        let color = DominantColor.extract(from: photo.image)
        await self?.finishAnalysis(id: photo.id, color: color)
    }
}
```
> `AppState.swift:144-150`

写真ごとに、

- `persistPhotoFile` … JPEG としてディスクに保存
- `Task.detached` … **バックグラウンドで色を解析**

`fresh` が180枚なら、**180個の解析が並行して走ります**。
1枚終わるごとに `finishAnalysis` が呼ばれ、画面がその1枚分だけ更新されます。

だから、取り込み中に**写真が1枚ずつ色付いていく**のが見えるのです。

### ⑧ Kotlin に色を判定させる

```swift
private func finishAnalysis(id: UUID, color: Int32?) {
    guard let idx = photos.firstIndex(where: { $0.id == id }) else { return }
    photos[idx].dominantColor = color
    if let color {
        photos[idx].bucketKey = ColorBridge.shared.classifyKey(colorInt: color)
    }
}
```
> `AppState.swift:220-227`

**`ColorBridge.shared.classifyKey` が Kotlin の関数**です。
ここで「状態層 → 共通ロジック層」の境界を越えています。
Android 版もまったく同じ関数を呼ぶので、判定結果が一致します。

`photos[idx].bucketKey = …` で `@Published var photos` が変化 →
**通知が飛び、画面が再計算されます**。

### ⑨ 画面が色ごとに並べ直す

```swift
var groupedByBucket: [(key: String, photos: [HuntPhoto])] {
    let order = ColorBridge.shared.bucketKeys()
    let groups = Dictionary(grouping: photos.filter { $0.bucketKey != nil }, by: { $0.bucketKey! })
    return order.compactMap { key in groups[key].map { (key, $0) } }
}
```
> `AppState.swift:97-101`

1. `bucketKeys()` で**表示順**（赤→橙→…→グレー）を Kotlin から取得
2. `Dictionary(grouping:by:)` で写真をバケツごとに束ねる
3. 表示順に並べ直し、**写真が1枚も無い色は落とす**（`compactMap`）

```swift
ForEach(visibleGroups, id: \.key) { group in
    bucketHeader(group.key, count: group.photos.count)
    LazyVGrid(columns: columns, spacing: 8) {
        ForEach(group.photos) { photo in thumb(photo) }
    }
}
```
> `HuntView.swift:47-52`

これで画面に並びます。

### ルート全体

```
HuntView（ボタン）
  → AppState.importRecentLibraryPhotos()   許可 → 200枚取得
  → AppState.add(images:unreadable:)       バックグラウンドで指紋計算
  → AppState.appendUnique()                重複を除いて photos に追加
  → DominantColor.extract()                バックグラウンドで主役の色を求める
  → ColorBridge.classifyKey()              【Kotlin】12色に分類
  → @Published var photos が変化
  → HuntView.body が再計算 → 画面が並び替わる
```

**あなたは一度も「画面を更新しろ」と書いていません。**

---

## ツアー2: 写真をタップしてコラージュに入るまで

短いですが、**設計の考え方がよく出ている**ルートです。

### ① タップ

```swift
func toggleSelection(_ id: UUID) {
    if selection.contains(id) {
        selection.remove(id)
        collageOrder.removeAll { $0 == id }
    } else {
        selection.insert(id)
        collageOrder.append(id)
    }
}
```
> `AppState.swift:229-237`

**注目**: 選択（`selection`）と順序（`collageOrder`）が**必ず同時に変わります**。

なぜ2つ必要なのでしょうか。

- `selection: Set<UUID>` … **含まれるか**を高速に判定するため（サムネの枠線表示）
- `collageOrder: [UUID]` … **並び順**を持つため（Set は順序を持てない）

役割が違うので両方要ります。そして**片方だけ更新するとバグ**になるので、
1つの関数の中で必ずセットで変えています。

> これが第4章「一緒に変わるものは、一緒に置く」の実例です。

### ② コラージュ画面が順序どおりに写真を並べる

```swift
var orderedSelectedPhotos: [HuntPhoto] {
    let byId = Dictionary(photos.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
    return collageOrder.compactMap { byId[$0] }
}
```
> `AppState.swift:91-94`

1. まず「ID → 写真」の辞書を作る（探すのが速くなる）
2. `collageOrder` の順に、辞書から引く
3. `compactMap` なので、**見つからないIDは黙って飛ばす**

3番目が地味に重要です。写真を消した直後など、
`collageOrder` に無効なIDが残ることがあります。
`compactMap` なら、それでクラッシュせず自然に無視されます。

> **教訓**: 「ありえないはず」の状態でも、**壊れない書き方**を選ぶ。

---

## ツアー3: アプリを再起動して、ハントが戻るまで

### ① 起動と同時に読み込みを始める

```swift
init() {
    Task { await startPro() }
    Task { await loadStore() }
}
```
> `AppState.swift:81-86`

`init()` は `AppState` が作られた瞬間に走ります。
どちらも `Task` で始めるので、**起動は待たされません**。

### ② manifest と画像を読む

```swift
let loadedPairs: [(StoredPhoto, UIImage)] = await Task.detached(priority: .userInitiated) {
    …
    UIImage(contentsOfFile: huntPhotoURL(sp.id).path).map { (sp, $0) }
}
```
> `AppState.swift:357-359`

**バックグラウンドで**画像を読みます。200枚のファイル読み込みをメインでやると、
起動時に画面が固まります。

### ③ 読み込み中は保存しない

```swift
private var restoring = false

private func scheduleSave() {
    guard !restoring else { return }
    …
}
```
> `AppState.swift:265, 286-287`

ここが**うっかりすると壊れる**ところです。

読み込みで `photos` に値を入れると、`didSet { scheduleSave() }` が発火して
**保存が走ってしまいます**。読み込み途中の中途半端な状態が保存されると、
データが壊れかねません。

`restoring = true` の間は保存を無視することで、これを防いでいます。

> **教訓**: 自動で動く仕組み（`didSet`）を入れたら、
> 「**それが動いてほしくない瞬間**」を必ず考える。

---

## 自分で追ってみる練習

慣れるために、自分で追ってみてください。答えは書きません。
**⌘クリックで定義へジャンプ**しながら辿れば必ず着きます。

### 練習1（やさしい）

`TodayColorView` でルーレットを回すと、`AppState` のどの値が変わりますか。
その値は、他のどの画面で使われていますか。

> ヒント: `todayColor` を `⌘⇧F` で全体検索

### 練習2（ふつう）

写真を長押しして色を付け替えると、どの関数が呼ばれますか。
そのとき、**ディスクに保存されるまでに何秒かかりますか**。

> ヒント: `rebucket` → `didSet` → `scheduleSave`

### 練習3（むずかしい）

カメラのフレームモードで4枚撮り終えると、
**なぜ自動でコラージュ画面に移動する**のでしょうか。

> ヒント: `startCollage(with:templateID:layoutOrdinal:)` の最後のほう

---

## 数値をいじって遊ぶ

**壊しても `git checkout .` で戻せます。** 安心して試してください。

| ファイル:行 | 変えるもの | 何が起きるか |
|---|---|---|
| `AppState.swift:290` | `900_000_000` → `100_000_000` | 保存が0.1秒後になる（速すぎて重くなるはず） |
| `ColorHuntApp.swift:159` | `width: 52, height: 52` | カメラボタンの大きさ |
| `HuntView.swift:12` | `minimum: 104` | サムネイルの大きさ（列数が変わる） |
| `ColorHuntApp.swift:180` | `0.96` → `0.7` | ボタンを押したときの縮み方 |
| `HuntView.swift:224` | `duration: 0.15` → `1.0` | フィルターの切り替えがゆっくりに |

**「値を変える → 動かす → どこが変わったか見る」を10回**やると、
コードと画面の対応が体に入ります。読むだけより遥かに速いです。

---

## この章のまとめ

追ってみて分かったこと。

1. **画面はデータを持たない。** すべて `AppState` を経由します
2. **重い処理は必ずバックグラウンド。** 終わったらメインに戻して反映します
3. **画面の更新を命令している場所は、どこにもない。**
   `@Published` の変化がすべてを駆動しています
4. **「壊れない書き方」が随所にある。** `compactMap` で無効なIDを無視する、
   `restoring` で保存を止める、など

この4つが分かっていれば、**あなたはもう自分のアプリのコードを読めます。**

---

## 次のステップ

読めるようになったら、次は書く番です。小さいものから。

1. **数値をいじる**（上の表）— 今日できます
2. **文言を変える** — `Localizable.strings` の日本語を変えてみる
3. **色を足す** — `Brand` に新しい色を定義して、どこかで使ってみる
4. **表示を1つ足す** — 例: ハント画面に「合計◯枚」を出す
5. **状態を1つ足す** — 例: 最後にハントした日付を `AppState` に持たせて表示する

5番までできたら、SwiftUI の基本は身についています。

### 副読本

このシリーズはあなたのアプリに特化しているので、
**一般的な知識**は外部の教材で補うと効率が良いです。どちらも無料です。

- **Apple 公式 [SwiftUI Tutorials](https://developer.apple.com/tutorials/swiftui)**
  手を動かしながら進む公式チュートリアル
- **[100 Days of SwiftUI](https://www.hackingwithswift.com/100/swiftui)**
  1日1時間×100日。世界的に定番の無料コース

---

← [04-architecture.md](04-architecture.md) に戻る ／ [シリーズの目次](README.md)
