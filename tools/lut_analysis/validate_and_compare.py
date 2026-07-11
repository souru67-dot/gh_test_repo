#!/usr/bin/env python3
"""Step4: 生成した Faded Film LUT を合成テスト素材に適用して破綻を検査し、
元/適用後を並べた比較シート(PNG)を出力する。

被写体条件(緑の植物+暖色被写体/夜景)や色相環・グレーランプに適用し、
- 緑が不自然(ネオン)になっていないか
- 肌・オレンジが沈んでいないか(彩度を過度に落としていないか)
- シャドウが汚れていないか(意図した緑シアン以外へ転んでいないか)
を数値で確認する。パラメータは generate_and_verify.py の収束値を使用。

  python3 tools/lut_analysis/validate_and_compare.py [--out OUT.png]
"""
from __future__ import annotations

import argparse
import colorsys
import importlib.util
import os

import numpy as np
from PIL import Image, ImageDraw

_HERE = os.path.dirname(__file__)


def _load_transform():
    spec = importlib.util.spec_from_file_location(
        "gv", os.path.join(_HERE, "generate_and_verify.py"))
    gv = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(gv)
    # アプリに載せている確定値(v10)で検証する
    return gv, gv.SHIPPED


W = 900


def _gray(h):
    x = np.linspace(0, 1, W)
    return np.tile(x[None, :, None], (h, 1, 3))


def _hue(h, s=0.6, v=0.7):
    x = np.linspace(0, 1, W)
    row = np.array([colorsys.hsv_to_rgb(t, s, v) for t in x])
    return np.tile(row[None, :, :], (h, 1, 1))


def _blocks(h, cols):
    img = np.zeros((h, W, 3))
    step = W // len(cols)
    for i, c in enumerate(cols):
        img[:, i * step:(i + 1) * step] = c
    return img


def _logify(rgb):
    """通常sRGBを S-Cinetone for Mobile 風(Log系フラット・低彩度)へ近似圧縮。

    このプリセットは他プリセット同様 Log 系フラット素材を前提とするため、
    実素材相当の検証はこの前処理を通した入力で行う。
    """
    rgb = np.asarray(rgb, dtype=float)
    flat = 0.16 + 0.66 * rgb
    lum = (0.2126 * flat[..., 0] + 0.7152 * flat[..., 1]
           + 0.0722 * flat[..., 2])[..., None]
    return lum + (flat - lum) * 0.55


def build_sheet(gv, P, out):
    def grade(img):
        return gv.transform(img, P)

    daylight = [(0.10, 0.12, 0.11), (0.14, 0.28, 0.13), (0.30, 0.52, 0.22),
                (0.78, 0.45, 0.20), (0.82, 0.63, 0.51), (0.47, 0.59, 0.78),
                (0.55, 0.54, 0.52)]
    night = [(0.02, 0.03, 0.05), (0.05, 0.07, 0.12), (0.90, 0.70, 0.40),
             (0.12, 0.10, 0.18), (0.30, 0.15, 0.10)]
    # シーン系は S-Cinetone(Log)相当に前処理してから適用(実素材の使用条件)
    rows = [
        ("Gray ramp (direct)", _gray(70)),
        ("Hue wheel S0.6 (direct)", _hue(70)),
        ("Daylight foliage+warm (Log input)", _logify(_blocks(90, daylight))),
        ("Night scene (Log input)", _logify(_blocks(90, night))),
    ]

    def to8(a):
        return (np.clip(a, 0, 1) * 255).astype(np.uint8)

    gap = 8
    pad = 30
    strips = []
    for label, orig in rows:
        g = grade(orig)
        strip = np.concatenate(
            [to8(orig), np.full((gap, W, 3), 20, np.uint8), to8(g)], axis=0)
        strips.append((label, strip))

    total = sum(s.shape[0] + pad for _, s in strips) + 20
    canvas = Image.new("RGB", (W + 20, total), (12, 12, 12))
    d = ImageDraw.Draw(canvas)
    y = 10
    for label, strip in strips:
        d.text((10, y), f"{label}   (top = input / bottom = Faded Film v10)",
               fill=(230, 230, 230))
        y += 20
        canvas.paste(Image.fromarray(strip), (10, y))
        y += strip.shape[0] + pad - 20
    canvas.save(out)
    print(f"saved {out}")


def breakdown_report(gv, P):
    """Log(S-Cinetone相当)入力に適用した結果で破綻を検査する。"""
    def grade1(c):
        ci = _logify(np.array([c]))[0]
        return gv.transform(np.array([ci]), P)[0]

    print("\n=== breakdown check (on S-Cinetone/Log input) ===")
    checks = [
        ("dark foliage", (0.14, 0.28, 0.13)), ("bright foliage", (0.30, 0.52, 0.22)),
        ("orange subject", (0.78, 0.45, 0.20)), ("skin", (0.82, 0.63, 0.51)),
        ("sky", (0.47, 0.59, 0.78)), ("deep shadow", (0.10, 0.12, 0.11)),
    ]
    for nm, c in checks:
        o = grade1(c)
        _, so, _ = colorsys.rgb_to_hsv(*o)
        print(f"  {nm:15s} outS={so:.2f}  out"
              f"({o[0] * 255:.0f},{o[1] * 255:.0f},{o[2] * 255:.0f})")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(_HERE, "faded_v9_compare.png"))
    args = ap.parse_args()
    gv, P = _load_transform()
    build_sheet(gv, P, args.out)
    breakdown_report(gv, P)
