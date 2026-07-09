#!/usr/bin/env python3
"""参考画像(SNSスクショ)から「グレードの特性」を数値計測するツール。

目的はシーン(猫や植物)の再現ではなく、カラーグレーディングの特性
(ブラック/ホワイトポイント、輝度帯域別のスプリットトーン、色相別の
彩度と色相シフト、コントラスト)を抽出して、LUT生成の直接ターゲットに
できる数値レポートを出すこと。

SNSスクショにはUI(アイコン/文字/黒帯)が含まれるため、映像領域のみを
クロップし、さらにアイコン列・字幕帯をマスクしてから計測する。

使い方:
    python3 tools/lut_analysis/analyze_reference.py IMAGE [IMAGE ...] \
        [--spec tools/lut_analysis/reference_regions.json] [--dump OUTDIR]

各画像の映像領域・UIマスクは reference_regions.json にファイル名の
部分一致で定義する(未定義の画像は全面を映像とみなす)。
"""
from __future__ import annotations

import argparse
import json
import os
import sys

import numpy as np
from PIL import Image

# --- 色変換ヘルパ(すべて 0..1 前提) -----------------------------------


def srgb_to_linear(c: np.ndarray) -> np.ndarray:
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def luma_srgb(rgb: np.ndarray) -> np.ndarray:
    """知覚に近い、sRGB(ガンマ載り)上の相対輝度。0..1。"""
    return 0.2126 * rgb[..., 0] + 0.7152 * rgb[..., 1] + 0.0722 * rgb[..., 2]


def rgb_to_hsv(rgb: np.ndarray):
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    mx = np.max(rgb, axis=-1)
    mn = np.min(rgb, axis=-1)
    d = mx - mn
    h = np.zeros_like(mx)
    nz = d > 1e-6
    # 色相(度)
    idx = (mx == r) & nz
    h[idx] = (60 * ((g[idx] - b[idx]) / d[idx]) + 360) % 360
    idx = (mx == g) & nz
    h[idx] = 60 * ((b[idx] - r[idx]) / d[idx]) + 120
    idx = (mx == b) & nz
    h[idx] = 60 * ((r[idx] - g[idx]) / d[idx]) + 240
    s = np.where(mx > 1e-6, d / np.maximum(mx, 1e-6), 0.0)
    v = mx
    return h, s, v


# --- 領域抽出 ----------------------------------------------------------


def load_region(path: str, spec: dict) -> np.ndarray:
    """映像領域のみを取り出し、UIマスク部分をNaNにした float RGB(0..1)を返す。"""
    im = Image.open(path).convert("RGB")
    a = np.asarray(im).astype(np.float64) / 255.0
    h, w, _ = a.shape

    conf = None
    base = os.path.basename(path)
    for key, val in spec.items():
        if key in base:
            conf = val
            break

    if conf is None:
        # 定義が無ければ全面を映像とみなす
        return a

    y0 = int(conf.get("y0", 0.0) * h)
    y1 = int(conf.get("y1", 1.0) * h)
    x0 = int(conf.get("x0", 0.0) * w)
    x1 = int(conf.get("x1", 1.0) * w)
    crop = a[y0:y1, x0:x1].copy()
    ch, cw, _ = crop.shape

    # UIマスク(映像領域内の相対座標 0..1 で矩形指定)を NaN に
    for m in conf.get("masks", []):
        my0 = int(m[1] * ch)
        my1 = int(m[3] * ch)
        mx0 = int(m[0] * cw)
        mx1 = int(m[2] * cw)
        crop[my0:my1, mx0:mx1, :] = np.nan
    return crop


def valid_pixels(region: np.ndarray) -> np.ndarray:
    """NaNマスクを除いた (N,3) の有効ピクセル。"""
    flat = region.reshape(-1, 3)
    ok = ~np.isnan(flat).any(axis=1)
    return flat[ok]


# --- 計測 --------------------------------------------------------------

HUE_BANDS = [
    ("赤 Red", 0),
    ("オレンジ Orange", 30),
    ("イエロー Yellow", 55),
    ("グリーン Green", 110),
    ("シアン Cyan", 180),
    ("ブルー Blue", 225),
    ("パープル Purple", 275),
    ("マゼンタ Magenta", 320),
]


def circular_dist(a, b):
    d = np.abs(a - b) % 360
    return np.minimum(d, 360 - d)


def analyze(region: np.ndarray) -> dict:
    px = valid_pixels(region)
    lum = luma_srgb(px)
    h, s, v = rgb_to_hsv(px)

    def pct(p):
        return float(np.percentile(lum, p))

    # トーン特性
    black_point = pct(0.5)
    white_point = pct(99.5)
    p = {q: pct(q) for q in (1, 5, 25, 50, 75, 95, 99)}
    # 最暗部(下位1%)の平均RGB → シャドウの浮きと色み
    dark_mask = lum <= np.percentile(lum, 1)
    dark_rgb = px[dark_mask].mean(axis=0)
    bright_mask = lum >= np.percentile(lum, 99)
    bright_rgb = px[bright_mask].mean(axis=0)

    # 全体
    mean_chroma = float(s.mean())
    contrast_std = float(lum.std())

    # 輝度帯域別スプリットトーン(shadows/mid/highlights)
    def band_cast(mask):
        sub = px[mask]
        subl = lum[mask]
        m = sub.mean(axis=0)
        # 無彩色(=そのbandの平均輝度のグレー)からの色ずれ
        gray = subl.mean()
        return m, m - gray, float(rgb_to_hsv(m[None, :])[1][0])

    sh = lum <= np.percentile(lum, 25)
    mi = (lum > np.percentile(lum, 25)) & (lum < np.percentile(lum, 75))
    hi = lum >= np.percentile(lum, 75)
    shadow = band_cast(sh)
    mid = band_cast(mi)
    high = band_cast(hi)

    # 色相別: chromaで重み付けした平均彩度・色相シフト
    chroma = s * v  # 近似 chroma
    hue_stats = []
    strong = chroma > 0.06  # 近グレーは色相不定なので除外
    for name, center in HUE_BANDS:
        # 各画素を最も近いband中心に割当
        dists = np.stack(
            [circular_dist(h, c) for _, c in HUE_BANDS], axis=0
        )
        assign = np.argmin(dists, axis=0)
        this = HUE_BANDS.index((name, center))
        sel = strong & (assign == this)
        n = int(sel.sum())
        if n < 30:
            hue_stats.append((name, center, n, None, None, None))
            continue
        msat = float(s[sel].mean())
        # 色相シフト: band中心から実測平均色相へ(chroma重み)
        hh = h[sel]
        ww = chroma[sel]
        # 円平均
        ang = np.deg2rad(hh)
        mx = np.average(np.cos(ang), weights=ww)
        my = np.average(np.sin(ang), weights=ww)
        mean_hue = (np.rad2deg(np.arctan2(my, mx))) % 360
        shift = ((mean_hue - center + 180) % 360) - 180
        frac = n / len(px)
        hue_stats.append((name, center, n, msat, shift, frac))

    return dict(
        n=len(px),
        black_point=black_point,
        white_point=white_point,
        pct=p,
        dark_rgb=dark_rgb,
        bright_rgb=bright_rgb,
        mean_chroma=mean_chroma,
        contrast_std=contrast_std,
        shadow=shadow,
        mid=mid,
        high=high,
        hue_stats=hue_stats,
    )


# --- レポート ----------------------------------------------------------


def fmt_rgb8(rgb):
    return "(" + ", ".join(f"{v * 255:5.1f}" for v in rgb) + ")"


def cast_word(delta):
    r, g, b = delta
    parts = []
    if r - (g + b) / 2 > 0.008:
        parts.append("赤")
    if b - (r + g) / 2 > 0.008:
        parts.append("青")
    if g - (r + b) / 2 > 0.008:
        parts.append("緑")
    if (r + g) / 2 - b > 0.010:
        parts.append("黄(暖色)")
    if (r + b) / 2 - g > 0.008:
        parts.append("マゼンタ")
    return "/".join(parts) if parts else "ほぼ無彩色"


def report(path, a):
    print(f"\n{'=' * 70}\n■ {os.path.basename(path)}  (有効画素 {a['n']:,})\n{'=' * 70}")
    print("― トーン特性 ―")
    print(f"  ブラックポイント(0.5%点輝度): {a['black_point'] * 255:5.1f}/255"
          f"  →黒の浮き {a['black_point'] * 100:.1f}%")
    print(f"  ホワイトポイント(99.5%点輝度): {a['white_point'] * 255:5.1f}/255"
          f"  →白の抑え {(1 - a['white_point']) * 100:.1f}%")
    pp = a["pct"]
    print("  輝度パーセンタイル(/255): " + " ".join(
        f"p{q}={pp[q] * 255:.0f}" for q in (1, 5, 25, 50, 75, 95, 99)))
    print(f"  最暗部1%平均RGB {fmt_rgb8(a['dark_rgb'])}  → {cast_word(a['dark_rgb'] - a['dark_rgb'].mean())}寄り")
    print(f"  最明部1%平均RGB {fmt_rgb8(a['bright_rgb'])}  → {cast_word(a['bright_rgb'] - a['bright_rgb'].mean())}寄り")
    print(f"  コントラスト(輝度std): {a['contrast_std'] * 255:.1f}/255"
          f"   全体平均彩度(HSV S): {a['mean_chroma']:.3f}")

    print("― スプリットトーン(輝度帯域別の色かぶり) ―")
    for label, key in (("シャドウ(下位25%)", "shadow"),
                       ("ミッド(中間50%)", "mid"),
                       ("ハイライト(上位25%)", "high")):
        m, delta, sat = a[key]
        print(f"  {label:18s} 平均RGB {fmt_rgb8(m)}  S={sat:.3f}"
              f"  色かぶり= {cast_word(delta)}  (ΔRGB {delta[0]*255:+.1f},{delta[1]*255:+.1f},{delta[2]*255:+.1f})")

    print("― 色相別 彩度/色相シフト(chroma重み) ―")
    print(f"  {'帯域':<16}{'画素%':>7}{'平均S':>8}{'色相シフト':>12}")
    for name, center, n, msat, shift, frac in a["hue_stats"]:
        if msat is None:
            print(f"  {name:<16}{'--':>7}{'(少)':>8}")
            continue
        arrow = ""
        if abs(shift) >= 3:
            arrow = "→黄寄り" if (name.startswith("グリーン") and shift < 0) else \
                    ("→暖色寄り" if shift < 0 else "→寒色寄り")
        print(f"  {name:<16}{frac * 100:6.1f}%{msat:8.3f}{shift:+9.1f}°  {arrow}")


def compare(paths, results):
    if len(results) < 2:
        return
    print(f"\n{'=' * 70}\n■ 2枚の比較(共通=グレードの本質 / 差=シーン由来)\n{'=' * 70}")
    a, b = results[0], results[1]
    print("― 共通特性(近い=グレードの芯) ―")
    rows = [
        ("黒の浮き(BP/255)", a["black_point"] * 255, b["black_point"] * 255, 6),
        ("白の抑え(WP/255)", a["white_point"] * 255, b["white_point"] * 255, 12),
        ("全体平均彩度S", a["mean_chroma"], b["mean_chroma"], 0.05),
        ("シャドウ ΔR-Δave", (a["shadow"][1][0]) * 255, (b["shadow"][1][0]) * 255, 6),
    ]
    for name, va, vb, tol in rows:
        same = "≈共通" if abs(va - vb) <= tol else "≠差"
        print(f"  {name:<18} 画像1={va:7.3f}  画像2={vb:7.3f}   {same}")
    # シャドウ/ハイライトのかぶり方向の一致
    print("― スプリットトーンの方向一致 ―")
    for label, key in (("シャドウ", "shadow"), ("ハイライト", "high")):
        ca = cast_word(a[key][1])
        cb = cast_word(b[key][1])
        print(f"  {label}: 画像1={ca} / 画像2={cb}  → {'一致' if ca == cb else '傾向確認'}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("images", nargs="+")
    ap.add_argument("--spec", default=os.path.join(os.path.dirname(__file__), "reference_regions.json"))
    ap.add_argument("--dump", default=None, help="マスク後の映像領域をPNG保存する出力先")
    args = ap.parse_args()

    spec = {}
    if os.path.exists(args.spec):
        spec = json.load(open(args.spec))

    results = []
    for path in args.images:
        region = load_region(path, spec)
        if args.dump:
            os.makedirs(args.dump, exist_ok=True)
            vis = np.nan_to_num(region, nan=0.0)
            Image.fromarray((vis * 255).astype(np.uint8)).save(
                os.path.join(args.dump, os.path.basename(path) + ".masked.png"))
        a = analyze(region)
        results.append(a)
        report(path, a)
    compare(args.images, results)


if __name__ == "__main__":
    main()
