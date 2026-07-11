#!/usr/bin/env python3
"""計測値をターゲットに Faded Film LUT を生成 → 合成チャートで再計測 →
差分を数値で収束させるループ(Step2)。

参考写真は「グレーディング後の出力」しか無く、入力(素材)が不明なので
ヒストグラムの直接一致は取れない。そこでグレードの本質を

  (A) ニュートラル軸(グレーランプ)への振る舞い
      = トーンカーブ(黒/白点)+ 輝度帯域別スプリットトーン
  (B) 色相別の彩度・色相シフト

に落として合成チャート上で計測し、Step1の計測値をターゲットに座標降下で
パラメータを収束させる。得られた定数は Kotlin の FadedFilmParams に移植する。

  python3 tools/lut_analysis/generate_and_verify.py
"""
from __future__ import annotations

import numpy as np

# =====================================================================
# Step1 の計測値から導いたターゲット(2枚の共通=グレードの芯)
#   出典: analyze_reference.py の計測(値は 0..1)
# =====================================================================
TARGETS = {
    # ニュートラル軸: 入力グレー x を LUT に通した出力(R,G,B)。
    # ここは「グレードが中立グレーをどう染めるか」= 被写体非依存に復元できる
    # 本質。最暗部の緑シアンは一部シーン(暗い葉)由来なのでR差はやや控えめに。
    #   x=0 近傍: 黒を軽く浮かせ緑〜シアンへ
    "black":  dict(x=0.00, rgb=(0.040, 0.051, 0.053), w=(3.0, 4.0, 4.0)),
    #   x=0.20 シャドウ: 緑かぶり(ΔG>0, ΔR<0)
    "shadow": dict(x=0.20, rgb=(0.158, 0.190, 0.178), w=(2.0, 3.0, 2.0)),
    #   x=0.50 ミッド: ほぼ中立、わずかに黄緑・低彩度
    "mid":    dict(x=0.50, rgb=(0.498, 0.512, 0.476), w=(1.5, 1.5, 1.5)),
    #   x=0.85 ハイライト: 暖色(青が沈む)
    "high":   dict(x=0.85, rgb=(0.888, 0.872, 0.820), w=(2.0, 2.0, 3.0)),
    #   x=1.00 白: 軽いロールオフ+暖色
    "white":  dict(x=1.00, rgb=(0.962, 0.950, 0.922), w=(2.0, 2.0, 2.5)),
}
# 色相パッチのターゲット。絶対彩度は入力仮定に依存し厳密復元できないため
# 重みは弱め。狙いは方向性: 緑=中彩度で黄寄り(khaki)、暖色=相対的に維持。
HUE_TARGETS = {
    # (入力HSV) -> 望む出力(S, H)
    "green":  dict(in_hsv=(112, 0.50, 0.55), out_s=0.41, out_h=101.0, ws=0.8, wh=0.12),
    "orange": dict(in_hsv=(30, 0.55, 0.72), out_s=0.36, out_h=28.0, ws=0.8, wh=0.05),
    "yellow": dict(in_hsv=(55, 0.48, 0.75), out_s=0.30, out_h=52.0, ws=0.5, wh=0.03),
}
# パラメータの物理的に妥当な範囲(座標降下をこの箱に閉じ込め、意図した符号を保つ)
BOUNDS = dict(
    BP=(0.020, 0.070), WP=(0.900, 0.985), PIV=(0.40, 0.50), CON=(1.05, 1.45),
    SHR=(-0.045, 0.0), SHG=(0.0, 0.05), SHB=(0.0, 0.05),
    HIR=(0.0, 0.05), HIG=(-0.02, 0.03), HIB=(-0.05, 0.0),
    SAT=(0.72, 0.92), GREEN_S=(0.45, 1.0), GHS=(0.0, 0.08), WARM_S=(1.0, 1.4),
)

# アプリに実際に載せている確定値(v10)。色の性格は下の収束(P0→optimize)
# に基づくが、S-Cinetone 等の Log 系フラット素材向けに他プリセット同等の
# 強度(Log→709 復元量)へトーン/彩度を引き上げてある。
SHIPPED = dict(
    BP=0.0240, WP=0.9580, CON=1.6000, PIV=0.4500,
    SHR=-0.0600, SHG=0.0140, SHB=0.0140,
    HIR=0.0650, HIG=0.0330, HIB=-0.0550,
    SAT=1.2600, GREEN_S=0.8000, GHS=0.0700, WARM_S=1.1000,
)

# 収束させるパラメータ(初期値は現行 v8 相当から)
P0 = dict(
    BP=0.050, WP=0.950, PIV=0.44, CON=1.18,
    SHR=-0.020, SHG=0.030, SHB=0.020,
    HIR=0.030, HIG=0.006, HIB=-0.030,
    SAT=0.82,
    GREEN_S=0.62, GHS=0.045,
    WARM_S=1.20,
)
# 座標降下で動かすキーと探索初期ステップ
STEP = dict(
    BP=0.02, WP=0.02, PIV=0.03, CON=0.06,
    SHR=0.02, SHG=0.02, SHB=0.02,
    HIR=0.02, HIG=0.01, HIB=0.02,
    SAT=0.04, GREEN_S=0.05, GHS=0.02, WARM_S=0.06,
)

# =====================================================================
# LUT変換(Kotlin fadedFilm に移植する構造そのもの。ベクトル化)
# =====================================================================


def _luma(r, g, b):
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def _smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def _sat(c, l, f):
    return l + (c - l) * f


def transform(rgb: np.ndarray, P: dict) -> np.ndarray:
    """rgb: (...,3) 0..1 -> graded (...,3) 0..1。"""
    r0, g0, b0 = rgb[..., 0], rgb[..., 1], rgb[..., 2]

    def tone(x):
        # 先にピボット周りのコントラスト(S字)→ その後 [BP,WP] へ圧縮。
        # この順序なら黒の浮き(BP)がコントラストで潰れず、黒は正確にBPへ、
        # 白はWPへ着地する(フェード系グレードの定石)。
        y = np.clip((x - P["PIV"]) * P["CON"] + P["PIV"], 0.0, 1.0)
        return P["BP"] + (P["WP"] - P["BP"]) * y

    r, g, b = tone(r0), tone(g0), tone(b0)

    # スプリットトーン(輝度帯域別に色みを加算)
    l = _luma(r, g, b)
    sw = 1.0 - _smoothstep(0.0, 0.5, l)   # シャドウ重み
    hw = _smoothstep(0.5, 1.0, l)         # ハイライト重み
    r = r + P["SHR"] * sw + P["HIR"] * hw
    g = g + P["SHG"] * sw + P["HIG"] * hw
    b = b + P["SHB"] * sw + P["HIB"] * hw

    # マットなベース低彩度
    l2 = _luma(r, g, b)
    r = _sat(r, l2, P["SAT"])
    g = _sat(g, l2, P["SAT"])
    b = _sat(b, l2, P["SAT"])

    # 緑域: さらに彩度を落とし黄寄りへ(色相選択)
    gw = np.clip((g - np.maximum(r, b)) * 3.0, 0.0, 1.0)
    gl = _luma(r, g, b)
    r = r * (1 - gw) + _sat(r, gl, P["GREEN_S"]) * gw
    g = g * (1 - gw) + _sat(g, gl, P["GREEN_S"]) * gw
    b = b * (1 - gw) + _sat(b, gl, P["GREEN_S"]) * gw
    r = r + P["GHS"] * gw
    b = b - P["GHS"] * 0.5 * gw

    # 暖色(オレンジ・肌)域: 彩度を維持して被写体を沈ませない
    ww = np.clip((r - b) * 3.0, 0.0, 1.0) * _smoothstep(-0.02, 0.06, r - g)
    wl = _luma(r, g, b)
    keep = 1.0 + (P["WARM_S"] - 1.0) * ww
    r = _sat(r, wl, keep)
    g = _sat(g, wl, keep)
    b = _sat(b, wl, keep)

    return np.clip(np.stack([r, g, b], axis=-1), 0.0, 1.0)


# =====================================================================
# 計測(合成チャート上)
# =====================================================================


def _rgb_from_hsv(h, s, v):
    import colorsys
    return np.array(colorsys.hsv_to_rgb(h / 360.0, s, v))


def _hsv_of(rgb):
    import colorsys
    r, g, b = rgb
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    return h * 360.0, s, v


def loss(P: dict) -> tuple[float, dict]:
    total = 0.0
    detail = {}
    # (A) ニュートラル軸ターゲット
    for name, t in TARGETS.items():
        x = t["x"]
        out = transform(np.array([[x, x, x]]), P)[0]
        err = 0.0
        for i in range(3):
            err += t["w"][i] * (out[i] - t["rgb"][i]) ** 2
        total += err
        detail[name] = (out, np.array(t["rgb"]))
    # 単調性ペナルティ(グレー軸)
    xs = np.linspace(0, 1, 64)
    ramp = transform(np.stack([xs, xs, xs], axis=-1), P)
    d = np.diff(ramp, axis=0)
    mono_pen = np.sum(np.clip(-d, 0, None) ** 2) * 50.0
    total += mono_pen
    # (B) 色相ターゲット
    for name, t in HUE_TARGETS.items():
        rin = _rgb_from_hsv(*t["in_hsv"])
        out = transform(rin[None, :], P)[0]
        h, s, v = _hsv_of(out)
        se = t["ws"] * (s - t["out_s"]) ** 2
        dh = ((h - t["out_h"] + 180) % 360) - 180
        he = t["wh"] * (dh / 100.0) ** 2
        total += se + he
        detail[name] = (s, t["out_s"], h, t["out_h"])
    detail["_mono"] = mono_pen
    return total, detail


def _clamp_param(k, v):
    lo, hi = BOUNDS[k]
    return min(hi, max(lo, v))


def optimize(P: dict, iters=60):
    P = {k: _clamp_param(k, v) for k, v in P.items()}
    step = dict(STEP)
    best, _ = loss(P)
    for _ in range(iters):
        improved = False
        for k in step:
            for sign in (+1, -1):
                cand = dict(P)
                cand[k] = _clamp_param(k, P[k] + sign * step[k])
                if cand[k] == P[k]:
                    continue
                lc, _ = loss(cand)
                if lc < best - 1e-9:
                    P, best = cand, lc
                    improved = True
        if not improved:
            for k in step:
                step[k] *= 0.5
            if max(step.values()) < 1e-4:
                break
    return P, best


# =====================================================================
# レポート
# =====================================================================


def report(P, tag):
    lv, d = loss(P)
    print(f"\n===== {tag}  (loss={lv:.5f}, monoPen={d['_mono']:.5f}) =====")
    print("― ニュートラル軸: 出力RGB(/255)  [目標] ―")
    for name in ("black", "shadow", "mid", "high", "white"):
        out, tgt = d[name]
        print(f"  {name:7s} x={TARGETS[name]['x']:.2f}  "
              f"出力({out[0]*255:5.1f},{out[1]*255:5.1f},{out[2]*255:5.1f})  "
              f"目標({tgt[0]*255:5.1f},{tgt[1]*255:5.1f},{tgt[2]*255:5.1f})  "
              f"Δ({(out[0]-tgt[0])*255:+.1f},{(out[1]-tgt[1])*255:+.1f},{(out[2]-tgt[2])*255:+.1f})")
    print("― 色相パッチ: 出力S/H  [目標] ―")
    for name in HUE_TARGETS:
        s, ts, h, th = d[name]
        print(f"  {name:7s} S={s:.3f}[目標{ts:.3f} Δ{s-ts:+.3f}]  "
              f"H={h:5.1f}°[目標{th:.1f}° Δ{((h-th+180)%360)-180:+.1f}°]")


def kotlin_dump(P):
    print("\n===== Kotlin FadedFilmParams(収束値) =====")
    m = {
        "BLACK_POINT": P["BP"], "WHITE_POINT": P["WP"],
        "CONTRAST": P["CON"], "CONTRAST_PIVOT": P["PIV"],
        "SHADOW_TINT_R": P["SHR"], "SHADOW_TINT_G": P["SHG"], "SHADOW_TINT_B": P["SHB"],
        "HIGHLIGHT_TINT_R": P["HIR"], "HIGHLIGHT_TINT_G": P["HIG"], "HIGHLIGHT_TINT_B": P["HIB"],
        "BASE_SATURATION": P["SAT"],
        "GREEN_DESAT": P["GREEN_S"], "GREEN_HUE_SHIFT": P["GHS"],
        "WARM_SAT_KEEP": P["WARM_S"],
    }
    for k, v in m.items():
        print(f"    const val {k} = {v:.4f}f")


if __name__ == "__main__":
    report(P0, "初期値(v8相当)")
    Pbest, lbest = optimize(P0)
    report(Pbest, "収束後")
    kotlin_dump(Pbest)
