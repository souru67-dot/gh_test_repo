# LUT リファレンス分析ツール

参考画像(SNSスクショ)から **カラーグレーディングの特性** を数値計測し、
LUT生成の直接ターゲットにするためのツール群。

シーン(被写体)の再現ではなく「グレードの特性」の抽出が目的。SNSの
UI(アイコン・字幕・黒帯)は計測前にクロップ/マスクで除外する。

## 使い方

```bash
pip install numpy Pillow
python3 tools/lut_analysis/analyze_reference.py \
    参考画像1.png 参考画像2.png \
    [--spec tools/lut_analysis/reference_regions.json] \
    [--dump /tmp/masked]   # マスク後の映像領域をPNG確認用に保存
```

## 計測項目

- **トーン**: ブラック/ホワイトポイント(下位・上位0.5%点)、輝度パーセンタイル、
  最暗部/最明部の平均RGB(黒の浮き量と色み)、コントラスト(輝度std)
- **スプリットトーン**: シャドウ/ミッド/ハイライト帯域別の平均色かぶり
- **色相別**: 8帯域(赤/橙/黄/緑/シアン/青/紫/マゼンタ)の平均彩度と色相シフト
- **2枚比較**: 共通特性(=グレードの芯)とシーン由来の差の分離

## ファイル

- `analyze_reference.py` — 分析本体
- `reference_regions.json` — 画像ごとの映像領域とUIマスク定義(ファイル名の部分一致)
- `generate_and_verify.py` — 計測値をターゲットにLUTを生成→再計測→差分収束(Step2)
