# ColorHunt ドキュメント索引

どれを読めばいいか迷ったら、ここから。

---

## 🚀 リリースしたい

**まずこの2つだけ読めば、審査提出までたどり着けます。**

| 読む順 | ドキュメント | 何が書いてあるか |
|---|---|---|
| 1 | **[IOS_LAUNCH_GUIDE.md](IOS_LAUNCH_GUIDE.md)** | 現在地・全工程・承認後の Step 1〜7。**リリースの本体** |
| 2 | **[OWNER_TASKS.md](OWNER_TASKS.md)** | あなたが手を動かす4件（A〜D）のクリック単位の手順 |

必要になったときだけ開くもの:

| ドキュメント | いつ開くか |
|---|---|
| [APP_STORE_LISTING.md](APP_STORE_LISTING.md) | 掲載情報を入力するとき（Step 5）。4言語の文言をコピペする |
| [REEL_SCRIPT.md](REEL_SCRIPT.md) | 紹介動画を作るとき。24秒・7カットの台本 |

**今の状況**: Apple Developer Program の承認待ち。
承認を待つ間に `OWNER_TASKS.md` の A〜D を終わらせておくと、承認後が最短になります。

---

## 🔍 中身を知りたい

| ドキュメント | 内容 |
|---|---|
| [ARCHITECTURE_iOS.md](ARCHITECTURE_iOS.md) | iOS 側の設計。画面構成、状態管理、KMP共有モジュールとの境界 |
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
| メモリ実測のやり方 | OWNER_TASKS.md §C（判定基準は IOS_LAUNCH_GUIDE.md §3.4） |
| App プライバシー質問票に何と答えるか | APP_STORE_LISTING.md §2 |
| 審査メモに何を書くか | APP_STORE_LISTING.md §4 |
| 課金の製品ID | `com.yk-dev.ColorHunt.pro`（APP_STORE_LISTING.md 冒頭） |
| スクリーンショットの構成 | OWNER_TASKS.md §D |
| 提出直前の最終確認 | IOS_LAUNCH_GUIDE.md §7 |
| ビルドエラーの対処 | IOS_LAUNCH_GUIDE.md §8「詰まりやすいポイント」 |
| 価格をいくらにするか | IOS_LAUNCH_GUIDE.md §6 収益化ロードマップ |
