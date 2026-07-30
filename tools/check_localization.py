#!/usr/bin/env python3
"""ColorHunt iOS — Localizable.strings のチェック（Mac不要・依存なし）

  python3 tools/check_localization.py

見るのは4点:

  1. キーの過不足     … en を基準に ja/ko/zh-Hans を突き合わせ
  2. キーの重複       … 同じキーが2回あると後ろが勝ち、前の訳が消える
  3. 壊れた行         … 行末の ; 抜けなど（1行落ちると以降が全部無視される）
  4. 未ローカライズ   … Swift の Text("日本語") のうち .strings に無いもの
                        （英語圏のユーザーにだけ日本語が出る。実際に
                        「フィードプレビュー」がこれで漏れていた）

4 は SwiftUI の LocalizedStringKey の仕組みに合わせて、文字列補間を
書式指定子として扱う: Text("\\(n)枚") のキーは "%lld枚"。型は静的には
決まらないので %lld / %@ などどれかに当たれば OK とみなす。
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STRINGS_DIR = ROOT / "iosApp" / "ColorHunt"
BASE = "en"
LOCALES = ["en", "ja", "ko", "zh-Hans"]

KEY_LINE = re.compile(r'\s*"((?:\\.|[^"\\])*)"\s*=\s*"((?:\\.|[^"\\])*)"\s*;\s*$')
JAPANESE = re.compile(r"[\u3040-\u30ff\u4e00-\u9fff]")
# Text("…") / LocalizedStringKey("…") / Label("…", systemImage:) の第1引数
SWIFT_LITERAL = re.compile(r'(?:Text|LocalizedStringKey|Label)\(\s*"((?:\\.|[^"\\])*)"')

problems: list[str] = []


def fail(msg: str) -> None:
    problems.append(msg)


def parse(locale: str) -> dict[str, str]:
    """1ロケール読み込み。重複・壊れた行はその場で報告する。"""
    path = STRINGS_DIR / f"{locale}.lproj" / "Localizable.strings"
    if not path.exists():
        fail(f"{locale}: ファイルがありません ({path})")
        return {}
    table: dict[str, str] = {}
    for n, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("/*") or line.startswith("//") or line.startswith("*"):
            continue
        m = KEY_LINE.match(raw)
        if not m:
            fail(f'{locale}:{n} 行が壊れています（末尾の ; 抜け？）: {line[:60]}')
            continue
        key, value = m.group(1), m.group(2)
        if key in table:
            fail(f'{locale}:{n} キーが重複しています: "{key}"（後ろが勝つので前の訳が消えます）')
        table[key] = value
    return table


SPECIFIER = r"%(?:lld|ld|d|@|\d+\$@)"

# %@ / %lld / %2$@ / %.1f … 位置指定子は順番が言語で変わってよいので、比較の
# ときだけ落として「型と数」を見る。
FORMAT = re.compile(r"%(\d+\$)?([-+ #0]*[\d.]*)(hh|h|ll|l|q|L|z|t|j)?([@diuxXoOfeEgGcCsSpaAF%])")


def specifiers(text: str) -> list[str]:
    """並び順に依存しない、書式指定子の型リスト（%% は除く）。"""
    out = [(m.group(3) or "") + m.group(4) for m in FORMAT.finditer(text)]
    return sorted(s for s in out if s != "%")


def split_interpolations(literal: str) -> list[str]:
    r"""\(…) を境に literal を分割する。

    正規表現で \\\([^)]*\) とやると \(min(a, b)) のような入れ子で最初の )
    で切れてしまうので、括弧の深さを数えて読み飛ばす。
    """
    parts: list[str] = []
    buf: list[str] = []
    i = 0
    while i < len(literal):
        if literal.startswith("\\(", i):
            parts.append("".join(buf))
            buf = []
            depth = 0
            i += 1  # ( の位置へ
            while i < len(literal):
                if literal[i] == "(":
                    depth += 1
                elif literal[i] == ")":
                    depth -= 1
                    if depth == 0:
                        i += 1
                        break
                i += 1
        else:
            buf.append(literal[i])
            i += 1
    parts.append("".join(buf))
    return parts


def key_pattern(literal: str) -> re.Pattern:
    """補間つきリテラルを、書式指定子にマッチする正規表現へ。

    Text("\\(n)枚") → ^%(?:lld|…)枚$ となり、"%lld枚" に当たる。
    """
    parts = split_interpolations(literal)
    return re.compile(SPECIFIER.join(re.escape(p) for p in parts) + r"\Z")


def main() -> int:
    tables = {loc: parse(loc) for loc in LOCALES}
    base_keys = set(tables[BASE])
    print(f"{BASE}.lproj: {len(base_keys)} キー")

    for loc in LOCALES:
        if loc == BASE:
            continue
        keys = set(tables[loc])
        for k in sorted(base_keys - keys):
            fail(f'{loc}: キーがありません: "{k}"')
        for k in sorted(keys - base_keys):
            fail(f'{loc}: {BASE} に無い余分なキー: "{k}"')

    # 書式指定子の一致（数と型）。ずれると String(format:) が実行時に落ちる
    for loc in LOCALES:
        for key, value in tables[loc].items():
            want, got = specifiers(key), specifiers(value)
            if want != got:
                fail(f'{loc}: 書式指定子が合いません "{key}"'
                     f" — キー {want or '（なし）'} に対し訳は {got or '（なし）'}"
                     f"（String(format:) が実行時に落ちます）")

    # Swift 側の日本語リテラルが .strings に載っているか
    patterns = None
    for swift in sorted(STRINGS_DIR.glob("*.swift")):
        src = swift.read_text(encoding="utf-8")
        for m in SWIFT_LITERAL.finditer(src):
            literal = m.group(1)
            if not JAPANESE.search(literal):
                continue
            if literal in base_keys:
                continue
            if r"\(" in literal:
                if patterns is None:
                    patterns = list(base_keys)
                pat = key_pattern(literal)
                if any(pat.match(k) for k in patterns):
                    continue
            line = src[: m.start()].count("\n") + 1
            fail(f'{swift.name}:{line} 未ローカライズ: "{literal}"'
                 f"（英語/韓国語/中国語のユーザーに日本語が出ます）")

    if problems:
        print(f"\nNG — {len(problems)} 件\n")
        for p in problems:
            print("  -", p)
        return 1
    print("OK — 過不足・重複・壊れた行・未ローカライズ、いずれもありません")
    return 0


if __name__ == "__main__":
    sys.exit(main())
