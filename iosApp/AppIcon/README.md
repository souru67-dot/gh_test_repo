# ColorHunt アプリアイコン

**Android 版ランチャーアイコンと同一デザイン**です。デザインの原本は
Android 側（`app/src/main/res/drawable/ic_launcher_{background,foreground}.xml`）:
パステルの斜めウォッシュ（ラベンダー→ピンク→ピーチ）に、スイープグラデの
カラーホイールリング＋「ハント」を表す白いピッカードット。

`icon.svg` / `icon-1024.png` は `gen_icon.py` が生成します（SVG はスイープ
グラデ非対応のため、リングを 180 セグメントで近似）。デザインを変えるときは
Android 側ドローアブルと `gen_icon.py` を揃えて更新し、再実行してください:

```bash
python3 gen_icon.py   # icon.svg と icon-1024.png を再生成
```

> 角丸は付けないこと（iOS が自動でスクワークルにマスクします）。

## 書き出し済み PNG

**`icon-1024.png`（1024×1024・アルファなし）をこのフォルダに同梱済み**です。
そのまま Xcode にドロップできます。SVG を編集した場合のみ再書き出しが必要:

- **macOS CLI**:
  ```bash
  brew install librsvg
  rsvg-convert -w 1024 -h 1024 icon.svg -o icon-1024.png
  ```
- **Figma / Sketch**: icon.svg をインポート → 1024×1024 PNG でエクスポート
- オンライン変換（CloudConvert 等）でも可

> App Store 提出用アイコンは **アルファチャンネル不可・角丸不可**。同梱の
> PNG は RGB（アルファなし）で書き出し済みです。

## Xcode への設定（3 ステップ）

1. Xcode で `Assets.xcassets` → `AppIcon` を開く
2. **iOS 1024pt (Single Size)** スロットに `icon-1024.png` をドラッグ&ドロップ
   （Xcode 14+ は 1024 一枚で全サイズを自動生成）
3. ビルドして実機/シミュレータのホーム画面で視認性を確認（明・暗の壁紙両方で）

> AppIcon スロットが「Single Size」でない場合は、AppIcon を選択して右の
> インスペクタ（Attributes）→ **iOS** の「Single Size」にチェックすると
> 1024 一枚運用になります。

## バリエーション（App Store 用・任意）

- ダーク/ティンテッドアイコン（iOS 18+）: 背景を透過し、リングを白の
  モノトーンにした版を `AppIcon` の Dark / Tinted スロットへ
- マーケティング用キービジュアルにも同じリングモチーフを流用すると
  ストア掲載の統一感が出ます
