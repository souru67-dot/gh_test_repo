# 3. 非同期処理 — なぜ200枚の解析中も画面が固まらないのか

ColorHunt は写真200枚の色を解析します。1枚あたり数ミリ秒でも、
200枚なら1秒近くかかります。**その間ずっと画面が固まったら、アプリとして失格**です。

この章では、それをどう避けているかを見ます。SwiftUI と並んで、
初心者が最初に戸惑うところです。

---

## 3-1. 「メインスレッド」という1本道

iOS アプリには**メインスレッド**という特別な処理の流れがあります。

- 画面を描くのは、メインスレッドの仕事
- タップに反応するのも、メインスレッドの仕事

**ここは1本道です。** 重い処理をメインスレッドで始めると、
終わるまで描画もタップも止まります。これが「アプリが固まる」の正体です。

```
メインスレッド: [タップ受付] [描画] [色解析 1秒間ずっと] [描画] …
                                    ↑ この間、画面は完全に停止
```

### 解決策

**重い処理は別の流れ（バックグラウンド）に逃がす。**

```
メインスレッド:   [タップ受付] [描画] [描画] [描画] …  ← 止まらない
バックグラウンド:      [色解析 ……………………]     ← 裏で進む
```

ColorHunt がやっているのはこれです。

---

## 3-2. `@MainActor` — 「ここはメインスレッド」の宣言

```swift
@MainActor
final class AppState: ObservableObject {
```
> `AppState.swift:42-43`

`@MainActor` は「**このクラスの中身は、必ずメインスレッドで動く**」という宣言です。

### なぜ必要か

`AppState` の `@Published` が変わると画面が更新されます。
**画面の更新はメインスレッドからしか許されません。**
バックグラウンドから `photos` を書き換えると、アプリが不安定になったり
クラッシュしたりします。

`@MainActor` を付けておけば、**うっかりバックグラウンドから触ったら
コンパイルエラー**になります。実行時のバグを、書いた時点で防げます。

> これが Swift の設計思想です。「気をつける」ではなく「間違えられなくする」。

---

## 3-3. `Task` — 非同期処理を始める

```swift
init() {
    Task { await startPro() }
    Task { await loadStore() }
}
```
> `AppState.swift:81-86`

`Task { }` は「この中身を非同期で始める」という意味です。

`init()`（アプリ起動時）で、

- 課金状態の復元（`startPro`）
- 保存した写真の読み込み（`loadStore`）

を始めています。どちらも時間がかかるので、**起動を待たせないために**
`Task` で始めて、`init()` 自体はすぐ終わります。

### `await` — 「ここで待つ」目印

```swift
await self?.appendUnique(result, unreadable: lost)
```
> `AppState.swift:126`

`await` は「この処理は時間がかかるので、終わるまで待つ」という印です。

**待っている間、メインスレッドは他の仕事ができます。** ここが重要です。
「待つ」と言っても、道を塞いで立ち止まるわけではありません。
順番待ちの札を取って、呼ばれるまで他のことをしている、というイメージです。

> `await` が付けられるのは `async` と宣言された関数だけです。
> コンパイラが対応関係をチェックしてくれるので、
> 「`await` を付け忘れた」というバグは起きません。

---

## 3-4. `Task` と `Task.detached` の違い

ColorHunt には両方が出てきます。**この違いが分かると、設計の意図が読めます。**

```swift
// ① Task — 今の場所を引き継ぐ（＝メインスレッドのまま）
Task { await loadStore() }
// AppState.swift:85

// ② Task.detached — 完全に切り離す（＝バックグラウンドへ）
Task.detached(priority: .userInitiated) { [weak self] in
    let color = DominantColor.extract(from: photo.image)
    await self?.finishAnalysis(id: photo.id, color: color)
}
// AppState.swift:146-149
```

| | `Task` | `Task.detached` |
|---|---|---|
| どこで動くか | 呼び出し元と同じ（ここでは**メイン**） | **バックグラウンド** |
| 使いどころ | 軽い処理・画面の更新 | **重い計算** |

### ②を1行ずつ読む

```swift
Task.detached(priority: .userInitiated) { [weak self] in
```
バックグラウンドで開始。`.userInitiated` は「ユーザーが待っているので優先して」。
`[weak self]` は第1章でやったメモリのお作法です。

```swift
    let color = DominantColor.extract(from: photo.image)
```
**重い処理。** 写真のピクセルを読んで主役の色を求めます。
ここはバックグラウンドなので、画面は固まりません。

```swift
    await self?.finishAnalysis(id: photo.id, color: color)
```
結果を `AppState` に報告します。`finishAnalysis` は `@MainActor` のクラスの
メソッドなので、**Swift が自動的にメインスレッドに戻してくれます**。
だから `await` が付いています。

**この「バックグラウンドで計算 → メインに戻して反映」が、
iOS アプリの最も基本的なパターン**です。ColorHunt では
色解析・画像の保存・写真の読み込みすべてがこの形をしています。

---

## 3-5. 実例: 200枚の取り込み

`add(images:)` を最初から最後まで追ってみます。

```swift
func add(images: [UIImage], unreadable: Int? = nil) {
    Task.detached(priority: .userInitiated) { [weak self] in
        var pairs: [(UIImage, Int)] = []
        for image in images {
            if let h = DominantColor.quickHash(image) { pairs.append((image, h)) }
        }
        let result = pairs
        let lost = unreadable.map { $0 + images.count - pairs.count }
        await self?.appendUnique(result, unreadable: lost)
    }
}
```
> `AppState.swift:115-128`

### 何が起きているか

1. **`Task.detached` でバックグラウンドへ**
   200枚のハッシュ計算はここでやります

2. **`for` ループで全画像のハッシュを取る**
   ハッシュ＝画像を16×16に縮めて作る「指紋」。同じ写真の判定に使います

3. **`await self?.appendUnique(...)` でメインに戻す**
   `appendUnique` は `@MainActor` なので、自動的にメインスレッドで実行されます

4. **`appendUnique` が `photos` に追加**
   `@Published` が変化を通知 → 画面が更新される

**ユーザーから見ると**: ボタンを押す → スピナーが回る（画面は動く）→
写真が並ぶ。この間、一度も固まりません。

### `let result = pairs` は何のため？

```swift
var pairs: [(UIImage, Int)] = []
…
let result = pairs               // ← これ
await self?.appendUnique(result, …)
```

`pairs` は `var`（変更できる変数）です。
**変更できる変数を、非同期の境界をまたいで渡すのは危険**です
（渡した後に誰かが書き換えるかもしれない）。

そこで `let`（変更できない定数）にコピーしてから渡しています。
Swift のコンパイラはこれを厳しくチェックするので、
`let` に写さないとエラーになることがあります。

---

## 3-6. デバウンス — 「落ち着いたら保存する」

```swift
private func scheduleSave() {
    guard !restoring else { return }
    saveTask?.cancel()
    saveTask = Task { [weak self] in
        try? await Task.sleep(nanoseconds: 900_000_000)   // 0.9秒待つ
        guard !Task.isCancelled else { return }
        self?.saveManifest()
    }
}
```
> `AppState.swift:286-294`

これは非常によくできた仕組みなので、丁寧に読みます。

### 解きたい問題

`photos` が変わるたびに保存したい。でも200枚取り込むと、
**200回保存が走ります**。1回ごとにファイル書き込みが起きたら重すぎます。

### 解き方

「変更があったら**0.9秒待って**から保存する。
待っている間に次の変更が来たら、**前の予約はキャンセル**して数え直す」

```
変更 → 0.9秒待機開始
  変更 → 前のをキャンセル、0.9秒待機し直し
    変更 → 前のをキャンセル、0.9秒待機し直し
      …（200回）…
        最後の変更 → 0.9秒待つ → 誰も邪魔しない → 保存！ 1回だけ
```

これを**デバウンス**と呼びます。検索窓の「入力が止まったら検索」と同じ仕組みです。

### 1行ずつ

```swift
guard !restoring else { return }
```
読み込み中は保存しない。読み込みで `photos` に書き込むと `didSet` が
発火してしまうので、それを無視するためのフラグです。

```swift
saveTask?.cancel()
```
**前回の予約をキャンセル。** これがデバウンスの心臓部です。

```swift
try? await Task.sleep(nanoseconds: 900_000_000)
```
0.9秒待つ。`900_000_000` ナノ秒 = 0.9秒です。
Swift では数値に `_` を入れて読みやすくできます。

```swift
guard !Task.isCancelled else { return }
```
待っている間にキャンセルされていたら、何もしない。

```swift
self?.saveManifest()
```
生き残ったものだけが、実際に保存します。

---

## 3-7. `didSet` — 値が変わったら自動で呼ぶ

```swift
@Published var photos: [HuntPhoto] = [] { didSet { scheduleSave() } }
@Published var selection: Set<UUID> = [] { didSet { scheduleSave() } }
@Published var todayColor: Int32? { didSet { scheduleSave() } }
@Published var collageOrder: [UUID] = [] { didSet { scheduleSave() } }
```
> `AppState.swift:48-55`

`didSet` は「この変数が変わった直後に、これを実行する」という指定です。

**おかげで、保存を呼び出す場所を探す必要がありません。**
`photos` を触るコードがアプリのどこにあっても、自動的に保存が予約されます。

「保存し忘れ」というバグが**構造的に起きなくなる**のが、この書き方の価値です。

---

## 3-8. よくあるエラーと読み方

初心者が必ず出会うエラーを挙げておきます。

| エラーメッセージ | 意味 | 直し方 |
|---|---|---|
| `Expression is 'async' but is not marked with 'await'` | 待つ必要がある処理に `await` が無い | `await` を付ける |
| `'async' call in a function that does not support concurrency` | `async` でない関数の中で `await` している | `Task { }` で包む |
| `Main actor-isolated property can not be referenced from a non-isolated context` | バックグラウンドから `@MainActor` のものを触っている | `await` を付ける／`Task { @MainActor in }` で包む |
| `Reference to captured var in concurrently-executing code` | `var` を非同期の境界に渡している | `let` にコピーしてから渡す（3-5参照） |

**これらのエラーは、あなたを守っています。** 昔の iOS 開発では、
同じ間違いが「たまにクラッシュする」という形で表面化し、
原因の特定に何日もかかりました。今はコンパイラが書いた瞬間に教えてくれます。

---

## この章のまとめ

| 概念 | 一言で | ColorHunt での例 |
|---|---|---|
| メインスレッド | 画面を描く1本道。塞いではいけない | — |
| `@MainActor` | 「ここはメインスレッド」の宣言 | `AppState` クラス全体 |
| `Task { }` | 非同期処理を始める（今の場所のまま） | `init()` の起動処理 |
| `Task.detached { }` | バックグラウンドへ完全に逃がす | 色解析・ハッシュ計算 |
| `await` | 時間がかかる処理を待つ（塞がない） | `await self?.finishAnalysis(…)` |
| デバウンス | 落ち着いてから1回だけ実行 | `scheduleSave()` |
| `didSet` | 値が変わったら自動で呼ぶ | `photos` → 保存予約 |

### 黄金パターン

```swift
Task.detached(priority: .userInitiated) { [weak self] in
    let result = 重い計算()              // バックグラウンド
    await self?.反映する(result)         // メインに戻る
}
```

**iOS アプリの非同期処理の8割はこの形です。** これだけ覚えて帰ってください。

---

### 次に読む

→ **[04-architecture.md](04-architecture.md)** — ColorHunt 全体はなぜこの構造なのか
