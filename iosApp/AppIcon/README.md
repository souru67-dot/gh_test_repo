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

## 3 つの外観（iOS 18+）

| スロット | Xcode に入れる PNG | 元データ | 内容 |
|---|---|---|---|
| **Any Appearance** | `icon-1024.png` | `icon.svg` | Android と同じパステルの地 |
| **Dark** | `icon-dark-1024.png` | `icon-dark.svg` | 同じホイールをアプリの暗い地に載せた版 |
| **Tinted** | `icon-tinted-1024.png` | `icon-tinted.svg` | グレースケール。iOS がユーザーの色を掛けるため、**明度だけで形が読める**よう 0.45〜0.85 に圧縮 |

3 外観とも SVG と PNG の両方を同梱しています（SVG はベクタツールで開くため、
PNG が Xcode に入れる実体）。すべて `gen_icon.py` が同じ図形から生成します。

> Tinted は「iOS が単色を掛ける」仕様なので、色を置いても反映されません。
> リングを素の輝度にすると黄が白飛び・青が沈んで輪が途切れるため、帯域を
> 圧縮したうえで**白いドットだけを最も明るく**残し、指標が消えないようにしています。

## Xcode への設定

1. Xcode で `Assets.xcassets` → `AppIcon` を開く
2. 右のインスペクタ（Attributes）→ **Appearances** を **Any, Dark, Tinted** に設定
3. 3 つのスロットに上表のファイルをドラッグ&ドロップ
   （Xcode 14+ は 1024 一枚で全サイズを自動生成）
4. ビルドして確認:
   - 明・暗の壁紙でホーム画面の視認性
   - 設定 → 画面表示と明るさ → **ダーク** でアイコンが暗い地に切り替わる
   - ホーム画面長押し → 編集 → カスタマイズ → **色合い** で単色版が崩れない

> AppIcon スロットが「Single Size」でない場合は、AppIcon を選択して右の
> インスペクタ（Attributes）→ **iOS** の「Single Size」にチェックすると
> 1024 一枚運用になります。

## バリエーション（App Store 用・任意）

- ダーク/ティンテッドアイコン（iOS 18+）: 背景を透過し、リングを白の
  モノトーンにした版を `AppIcon` の Dark / Tinted スロットへ
- マーケティング用キービジュアルにも同じリングモチーフを流用すると
  ストア掲載の統一感が出ます
