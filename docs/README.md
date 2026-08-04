# ColorHunt ドキュメント索引

どれを読めばいいか迷ったら、ここから。

---

## 🚀 リリースしたい

**読むのは [IOS_LAUNCH_GUIDE.md](IOS_LAUNCH_GUIDE.md) の1本だけです。**
現在地・全工程・Step 0〜7 が全て入っています。

**今の状況**: Apple Developer Program 承認済み（2026-08-04）。
提出前の準備（A〜E）は**全て完了**。次にやることは
**[IOS_LAUNCH_GUIDE.md](IOS_LAUNCH_GUIDE.md) §8 Step 0**（Small Business Program の申請）です。

必要になったときだけ開くもの:

| ドキュメント | いつ開くか |
|---|---|
| [APP_STORE_LISTING.md](APP_STORE_LISTING.md) | 掲載情報を入力するとき（Step 5）。4言語の文言をコピペする |
| [V1_1_BACKLOG.md](V1_1_BACKLOG.md) | v1.1 でやりたいことリスト。思いついたら足していく |
| [OWNER_TASKS.md](OWNER_TASKS.md) | 準備作業 A〜E の**記録**。撮り直し・測り直しのときだけ |
| [IP_REVIEW.md](IP_REVIEW.md) | 商標・UI類似の確認記録（🔴3件とも対応済み） |
| [REEL_SCRIPT.md](REEL_SCRIPT.md) | 紹介動画を作るとき。24秒・7カットの台本 |

---

## 📖 Swift を学びたい

**[learn/](learn/)** — ColorHunt のコードを教材にした学習シリーズ（全5本）。
一般的な入門書ではなく、**例文はすべてあなたのアプリの実コード**です。

| # | ドキュメント | 何が分かるか | 目安 |
|---|---|---|---|
| 1 | [learn/01-swift-basics.md](learn/01-swift-basics.md) | Swift の文法 | 3〜4時間 |
| 2 | [learn/02-swiftui.md](learn/02-swiftui.md) | 画面の作り方・状態管理 | 3〜4時間 |
| 3 | [learn/03-concurrency.md](learn/03-concurrency.md) | 非同期処理（固まらない仕組み） | 2時間 |
| 4 | [learn/04-architecture.md](learn/04-architecture.md) | **なぜこの設計なのか**・弱点 | 2時間 |
| 5 | [learn/05-code-tour.md](learn/05-code-tour.md) | 処理を追う練習 | 2時間 |

1日1本、5日で一周が目安です。→ **[learn/README.md](learn/README.md) から開始**

---

## 🔍 中身を知りたい

| ドキュメント | 内容 |
|---|---|
| [ARCHITECTURE_iOS.md](ARCHITECTURE_iOS.md) | どのファイルに何があるかの**リファレンス**。データの流れ、既知の負債 |
| [../iosApp/README.md](../iosApp/README.md) | iOS のビルド手順、Xcode の配線、実装済み機能の一覧 |
| [../iosApp/AppIcon/README.md](../iosApp/AppIcon/README.md) | アイコンの生成方法（Light / Dark / Tinted） |

---

## 🤖 Android 版

> **注意: Android のドキュメントは v1.0 の iOS 仕様より古い状態です。**
> テンプレート5種時代の記述が残り、フレームモード・ハーフ・チェキなどが
> 反映されていません。Android をリリースするときは、先に更新が必要です。

| ドキュメント | 内容 |
|---|---|
| [RELEASE.md](RELEASE.md) | Android のビルド・署名手順 |
| [STORE_LISTING.md](STORE_LISTING.md) | Google Play の申請キット（**要更新**） |
| [TEST_PLAN.md](TEST_PLAN.md) | Android のテスト計画 |

---

## 📁 ドキュメント以外の場所

| 場所 | 中身 |
|---|---|
| `site/` | サポートページとプライバシーポリシーの**実物**。公開手順は OWNER_TASKS.md §B |
| `iosApp/ColorHunt/PrivacyInfo.xcprivacy` | Privacy Manifest。Xcode への追加手順は OWNER_TASKS.md §A |
| `iosApp/AppIcon/` | アイコンの SVG / PNG と生成スクリプト |

---

## よくある「どこ見るんだっけ」

| 知りたいこと | 場所 |
|---|---|
| **次に何をやればいいか** | IOS_LAUNCH_GUIDE.md §0-2（全体像）→ §8（手順） |
| メモリ実測のやり方 | OWNER_TASKS.md §C（判定基準は IOS_LAUNCH_GUIDE.md §3.4） |
| App プライバシー質問票に何と答えるか | APP_STORE_LISTING.md §2 |
| 審査メモに何を書くか | APP_STORE_LISTING.md §4 |
| 課金の製品ID | `com.ykdev.ColorHunt.pro`（APP_STORE_LISTING.md 冒頭） |
| スクリーンショットの構成 | OWNER_TASKS.md §D |
| 提出直前の最終確認 | IOS_LAUNCH_GUIDE.md §7 |
| ビルドエラーの対処 | IOS_LAUNCH_GUIDE.md §8「詰まりやすいポイント」 |
| 価格をいくらにするか | IOS_LAUNCH_GUIDE.md §6 収益化ロードマップ |
