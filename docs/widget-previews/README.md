# ウィジェット選択画面のプレビュー画像

`app/src/main/res/drawable-*nodpi/widget_preview_*.png` の生成元です。
実物の配色(墨と橙)・サンプル予定で、言語(英語=既定 / 日本語)×ライト/ダークの4通りを描きます
(「ドット」は常に黒地なので言語の2通りだけ)。

再生成手順(Node + Playwright):

```sh
cd docs/widget-previews
npm i @fontsource/noto-sans-jp@5.3.1      # 日本語の描画用フォント
cp ../../app/src/main/res/font/doto_dot.ttf doto.ttf
mkdir -p out && node render.js            # out/<種類>_<言語>_<テーマ>.png
```

| 出力 | 配置先 |
|---|---|
| `*_en_light.png` | `drawable-nodpi/` |
| `*_en_dark.png` | `drawable-night-nodpi/` |
| `*_ja_light.png` | `drawable-ja-nodpi/` |
| `*_ja_dark.png` | `drawable-ja-night-nodpi/` |
| `dot_en_dark.png` / `dot_ja_dark.png` | `drawable-nodpi/` / `drawable-ja-nodpi/` |
