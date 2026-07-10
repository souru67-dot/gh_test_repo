#!/usr/bin/env python3
"""Input Transform(各社Log → Rec.709)の数学リファレンス+検証。

公式数式ベース(S-Log3 / Apple Log)は公開仕様の定数から実装し、
グレーランプに当てて Rec.709 として妥当なカーブ(単調・黒0・18%グレー≈0.4付近・
白≈1)へ収束することを確認する。近似(General/D-Log-M/vendor/S-Cinetone)は
単調性と概形のみ検証する。ここで確定した式・定数を Kotlin へ移植する。

出典:
- S-Log3: Sony 公開仕様(log_decoding_SLog3、breakpoint 171.2102946929/1023)
- Apple Log: Apple Log Profile White Paper 定数
- S-Gamut3.Cine→XYZ: 公開マトリクス(colour-science)
"""
from __future__ import annotations

import numpy as np

# --- 色域マトリクス(scene-linear空間、行列は RGB→XYZ) -----------------
# Rec.709 / sRGB primaries, D65
M_709_TO_XYZ = np.array([
    [0.4123908, 0.3575843, 0.1804808],
    [0.2126390, 0.7151687, 0.0721923],
    [0.0193308, 0.1191948, 0.9505322],
])
# S-Gamut3.Cine → XYZ(Sony/colour-science 公開値)
M_SCINE_TO_XYZ = np.array([
    [0.59907348, 0.24892269, 0.10243238],
    [0.21507207, 0.88505846, -0.10013053],
    [-0.03206529, -0.02765808, 1.14862374],
])
# BT.2020 → XYZ(Apple Log は BT.2020 primaries)
M_2020_TO_XYZ = np.array([
    [0.6369580, 0.1446169, 0.1688810],
    [0.2627002, 0.6779981, 0.0593017],
    [0.0000000, 0.0280727, 1.0609851],
])

M_XYZ_TO_709 = np.linalg.inv(M_709_TO_XYZ)
M_SCINE_TO_709 = M_XYZ_TO_709 @ M_SCINE_TO_XYZ
M_2020_TO_709 = M_XYZ_TO_709 @ M_2020_TO_XYZ


# --- 各社 Log → scene-linear(decode) -----------------------------------
def slog3_to_linear(x):
    """Sony S-Log3 公式 decode。x は 0..1(10bit正規化)。返り値 scene-linear。"""
    x = np.asarray(x, float)
    bp = 171.2102946929 / 1023.0
    hi = (10.0 ** (((x * 1023.0) - 420.0) / 261.5)) * 0.19 - 0.01
    lo = ((x * 1023.0) - 95.0) * 0.01125000 / (171.2102946929 - 95.0)
    return np.where(x >= bp, hi, lo)


def apple_log_to_linear(P):
    """Apple Log Profile 公式 decode。P は 0..1。返り値 scene-linear。"""
    P = np.asarray(P, float)
    R0, Rt, c = -0.05641088, 0.01, 47.28711236
    beta, gamma, delta = 0.00964052, 0.08550479, 0.69336945
    Pt = c * (Rt - R0) ** 2
    mid = np.sqrt(np.clip(P, 0, None) / c) + R0
    hi = 2.0 ** ((P - delta) / gamma) - beta
    out = np.where(P < Pt, mid, hi)
    return np.where(P < 0, R0, out)


def general_log_to_linear(P, black=0.09, gain=1.9):
    """未知Log向けの汎用decode(近似)。持ち上がった黒を戻し指数で伸ばす。"""
    P = np.asarray(P, float)
    return (10.0 ** ((P - black) * gain) - 1.0) / (10.0 ** ((1 - black) * gain) - 1.0)


def dlogm_to_linear(P):
    """DJI D-Log-M decode(近似)。OSMO Pocket系。厳密な公開カーブではないため
    単一の連続な対数カーブで概形近似する(黒0.095持ち上げ・中庸ゲイン)。"""
    return general_log_to_linear(P, black=0.095, gain=2.1)


# --- scene-linear → Rec.709(display encode) ----------------------------
def linear_to_r709(lin, gamma=2.2, grey_in=0.18, grey_out=0.44, knee=0.90):
    """scene-linear を表示相当の Rec.709 へ。18%グレーを [grey_out] へ合わせ、
    ハイライトは knee 以上を軽くロールオフして白飛びを抑える。近似だが
    全 Log 入力で共通の 709 ベースラインを作ることが目的(プリセットの一貫性)。"""
    lin = np.clip(np.asarray(lin, float), 0.0, None)
    # 18%グレーが grey_out になるよう露出スケール
    scale = (grey_out ** gamma) / grey_in
    y = (lin * scale) ** (1.0 / gamma)
    # ハイライトのソフトクリップ
    over = y > knee
    y = np.where(over, knee + (1 - np.exp(-(y - knee) / (1 - knee))) * (1 - knee), y)
    return np.clip(y, 0.0, 1.0)


def apply_matrix(rgb, M):
    """(...,3) に 3x3 を適用。負値は 0 でクリップ(709外の色)。"""
    out = rgb @ M.T
    return np.clip(out, 0.0, None)


# --- 入力変換(decode → gamut → encode) --------------------------------
def transform(name, rgb):
    rgb = np.asarray(rgb, float)
    if name == "slog3":
        lin = slog3_to_linear(rgb)
        lin = apply_matrix(lin, M_SCINE_TO_709)
    elif name == "applelog":
        lin = apple_log_to_linear(rgb)
        lin = apply_matrix(lin, M_2020_TO_709)
    elif name == "general":
        lin = general_log_to_linear(rgb)
    elif name == "dlogm":
        lin = dlogm_to_linear(rgb)
    else:
        raise ValueError(name)
    return linear_to_r709(lin)


def verify():
    xs = np.linspace(0, 1, 256)
    ramp = np.stack([xs, xs, xs], -1)
    for name, accuracy in [("slog3", "公式"), ("applelog", "公式"),
                           ("general", "近似"), ("dlogm", "近似")]:
        out = transform(name, ramp)[:, 0]
        mono = bool(np.all(np.diff(out) >= -1e-4))
        inrange = bool(out.min() >= -1e-6 and out.max() <= 1 + 1e-6)
        # ランドマーク: 黒(0), 18%相当のコード値付近, 白(1)
        b = out[0]
        w = out[-1]
        mid = out[128]
        print(f"{name:9s}[{accuracy}] mono={mono} range=[{out.min():.3f},{out.max():.3f}] "
              f"black={b:.3f} mid(0.5)={mid:.3f} white={w:.3f}")
    print("\nS-Gamut3.Cine→709 matrix:\n", np.round(M_SCINE_TO_709, 4))
    print("BT.2020→709 matrix:\n", np.round(M_2020_TO_709, 4))
    # S-Log3 の 18%グレー コード値(≈0.41)が 709 で ~0.44 付近になるか
    slog_grey_cv = 0.41  # S-Log3 18%グレーの概略コード値
    g = transform("slog3", np.array([[slog_grey_cv] * 3]))[0, 0]
    print(f"\nS-Log3 18%グレー(cv≈0.41) → 709 = {g:.3f}(狙い ~0.44)")


if __name__ == "__main__":
    verify()
