# ColorHunt — App Store 申請キット（v1.0.0）

App Store Connect にそのまま貼り付けられる形で、掲載文・申告内容をまとめたものです。
`docs/IOS_LAUNCH_GUIDE.md` §8（手順）とセットで使います。

> Google Play 版は `docs/STORE_LISTING.md` にありますが、**内容が v1.0 の仕様より
> 古い**（テンプレ5種時代の記述、フレームモード未記載）ため、Android を出すときは
> 先に更新が必要です。

- Bundle ID: Xcode の設定と一致させること（例 `com.yk-dev.ColorHunt`）
- アプリ内課金: `com.ykdev.ColorHunt.pro`（非消耗型・買い切り）
- 対応言語: 日本語 / English / 한국어 / 简体中文
- 最低対応: iOS 16.0 / iPhone のみ（Supported Destinations = iPhone）

---

## 1. 掲載情報

### 文字数の上限

| 項目 | 上限 | 審査なしで変更 |
|---|---|---|
| App名 | 30 | ✗ |
| サブタイトル | 30 | ✗ |
| キーワード | 100 | ✗ |
| プロモーションテキスト | 170 | **✓** |
| 説明 | 4000 | ✗ |

> **プロモーションテキストだけは審査なしで差し替えられます。** キャンペーンや
> 「◯◯で紹介されました」はここに書くのが定石です。

### 日本語

| 項目 | 内容 | 文字数 |
|---|---|---|
| App名 | `ColorHunt-色で集める写真コラージュ` | 22 |
| サブタイトル | `色で自動仕分け・フィルム風カメラ編集` | 18 |
| キーワード | `配色,カラーパレット,色見本,レトロ,インスタント,ハーフ,画像加工,加工アプリ,整理,パレット,HEX,韓国風,おしゃれ,Y2K,デイログ,正方形,組写真,フィルター,現像,日記` | 88 |

**プロモーションテキスト**
```
撮った瞬間、コラージュになるカメラ。カメラロールは色で自動仕分け。
フィルム風・ハーフ・チェキ風など12種のテンプレートで、色で集めた写真をそのまま作品に。
```

**説明**
```
ColorHunt は、写真を「色」で集めて楽しむカメラ＆コラージュアプリです。
テーマ色を決めて街に出て、見つけた色を撮る。それだけで作品になります。

■ 撮った瞬間、コラージュになる
コラージュの枠を画面に当てながら撮影できる「フレームモード」。
シャッターを切るたびにコマが埋まり、撮り終えた時にはコラージュが完成しています。
「あとで組み立てる」必要がありません。

■ カメラロールを、色で自動仕分け
手持ちの写真を、主役の色で自動的に12色のグループへ。
赤・橙・黄・黄緑・緑・水色・青・紫・ピンク・白・黒・グレー。
集めた色が増えるほど、あなただけのカラーコレクションが埋まっていきます。
判定が違うと感じたら、写真を長押しして色を選び直せます。

■ 12種類のテンプレート
4カット／デイログ／パステル／フォトダンプ／Y2K
ハーフ／インスタント／フィルム／マガジン
ホワイト／組写／シームレス

トレンド・レトロ・ミニマルの3カテゴリから、迷わず選べます。
写真はドラッグで並べ替え、タップでトリミング（ピンチで拡大縮小）。
1:1 / 4:5 / 9:16 のSNSサイズで書き出せます。

■ カラーパレット（採集票）
コラージュに、使った色の記録を添えて書き出せます。
色の分類とHEXコードが並ぶ、標本ラベルのようなパレット。
作品の下辺に帯として敷くことも、中央や左右の列にすることもできます。

■ 今日の色
ルーレットを回して、今日ハントする色を決めましょう。
好きな時刻にお知らせが届くので、毎日の外出が少し楽しみになります。

■ カラーマップ
撮影地が記録された写真は、地図の上に色のピンとして並びます。
どの街で、どんな色を集めたのか。色でめぐった記録が残ります。

■ グリッド
SNSのプロフィール風の表示で、投稿の並びを事前に確認できます。

■ ColorHunt Pro（買い切り）
・書き出しの透かしを削除
・Pro限定テンプレート7種（ハーフ・インスタント・シームレス・デイログ・パステル・Y2K・マガジン）
・2160pxの高画質書き出し
・今後追加されるPro機能もすべて

一度のお支払いで、追加料金はありません。

──

写真の解析はすべて端末の中だけで行われます。
写真・位置情報が外部に送信されることは一切ありません。
広告も、アカウント登録も、利用状況の収集もありません。
```

### English

| 項目 | 内容 | 文字数 |
|---|---|---|
| App名 | `ColorHunt: Color Collage` | 24 |
| サブタイトル | `Shoot straight into a collage` | 29 |
| キーワード | `palette,hex,film,retro,instant,vintage,halfframe,grid,layout,scrapbook,y2k,aesthetic,editor,sort` | 96 |

> 「instax」「polaroid」は商標のため、キーワードから外しています。
> 商標語はリジェクト理由になります。

**プロモーションテキスト**
```
A camera that builds the collage as you shoot. Your camera roll, sorted by
colour automatically. 12 templates — film, half-frame, instant and more.
```

**説明**
```
ColorHunt is a camera and collage app built around one idea: collect the
world by colour.

■ The collage builds itself as you shoot
Frame mode puts a collage template right on the viewfinder. Every shutter
press fills the next cell, and by the time you're done, the collage is done.
Nothing to assemble afterwards.

■ Your camera roll, sorted by colour
Photos are grouped automatically by their dominant colour into twelve
buckets — red, orange, yellow, lime, green, mint, cyan, blue, purple, pink,
plus white, black and grey. The more colours you find, the more of your
collection fills in. Long-press any photo to re-file it by hand.

■ Twelve templates
4-Cut / Daylog / Pastel / Photo Dump / Y2K
Half-frame / Instant / Film / Magazine
White / Duo / Seamless

Sorted into Trend, Retro and Minimal so you can find one fast.
Drag to reorder, tap to crop, pinch to zoom. Export at 1:1, 4:5 or 9:16.

■ The colour record
Export your collage with a record of the colours in it — colour class and
hex, set like a specimen label. Run it down a column, or lay it along the
foot of the artwork as a band.

■ Today's colour
Spin the wheel to pick the colour you're hunting today, with an optional
daily reminder at a time you choose.

■ Colour map
Geotagged photos appear as coloured pins on a map, so you can look back at
where each colour came from.

■ Grid
Preview how your posts will line up in a 3-up feed before you post.

■ ColorHunt Pro (one-time purchase)
- Export without the watermark
- Seven Pro templates: Half-frame, Instant, Seamless, Daylog, Pastel, Y2K,
  Magazine
- 2160px high-resolution export
- Every Pro feature added in future updates

Pay once. No subscription.

──

All colour analysis happens on your device. Your photos and location never
leave it. No ads, no account, no tracking.
```

### 한국어

| 項目 | 内容 |
|---|---|
| App名 | `ColorHunt: 색으로 모으는 콜라주` |
| サブタイトル | `찍는 순간 콜라주가 되는 카메라` |
| キーワード | `팔레트,색조합,필름카메라,레트로,네컷사진,하프,사진편집,보정앱,정리,HEX,감성,Y2K,다이어리,인스턴트,꾸미기,색깔,사진정리,빈티지,포토덤프,무드보드` |

**プロモーションテキスト**
```
찍는 순간 콜라주가 되는 카메라. 카메라롤은 색으로 자동 정리.
필름 · 하프 · 즉석사진 등 템플릿 12종으로, 모은 색을 그대로 작품으로.
```

**説明**
```
ColorHunt는 사진을 '색'으로 모으는 카메라 & 콜라주 앱입니다.
오늘의 색을 정하고 거리로 나가, 발견한 색을 찍는다. 그것만으로 작품이 됩니다.

■ 찍는 순간, 콜라주가 됩니다
화면에 콜라주 틀을 띄운 채로 촬영하는 '프레임 모드'.
셔터를 누를 때마다 칸이 채워지고, 다 찍었을 때는 콜라주가 완성되어 있습니다.
나중에 따로 편집할 필요가 없습니다.

■ 카메라롤을 색으로 자동 정리
가지고 있는 사진을 주된 색에 따라 12가지 그룹으로 자동 분류합니다.
빨강 · 주황 · 노랑 · 연두 · 초록 · 하늘 · 파랑 · 보라 · 분홍 · 하양 · 검정 · 회색.
모은 색이 늘어날수록 나만의 컬러 컬렉션이 채워집니다.
분류가 마음에 들지 않으면 사진을 길게 눌러 직접 색을 고를 수 있습니다.

■ 템플릿 12종
네컷사진 / 데이로그 / 파스텔 / 포토덤프 / Y2K
하프 / 즉석사진 / 필름 / 매거진
화이트 / 조합 / 심리스

트렌드 · 레트로 · 미니멀 세 가지 카테고리로 나뉘어 있어 빠르게 고를 수 있습니다.
사진은 드래그로 순서를 바꾸고, 탭하면 자르기가 열립니다(핀치로 확대·축소).
1:1 / 4:5 / 9:16 SNS 사이즈로 내보낼 수 있습니다.

■ 컬러 팔레트
콜라주에 사용된 색의 기록을 함께 담아 내보낼 수 있습니다.
색 분류와 HEX 코드가 나란히 놓인, 표본 라벨 같은 팔레트.
작품 아래에 띠로 깔 수도, 가운데나 양옆에 열로 세울 수도 있습니다.

■ 오늘의 색
룰렛을 돌려 오늘 찾을 색을 정해 보세요.
원하는 시간에 알림이 오기 때문에, 매일의 외출이 조금 더 기다려집니다.

■ 컬러 맵
촬영 위치가 기록된 사진은 지도 위에 색깔 핀으로 표시됩니다.
어느 동네에서 어떤 색을 모았는지, 색으로 다닌 기록이 남습니다.

■ 그리드
SNS 프로필처럼 배열해, 올리기 전에 피드의 흐름을 미리 확인할 수 있습니다.

■ ColorHunt Pro (한 번만 결제)
· 내보내기 워터마크 제거
· Pro 전용 템플릿 7종 (하프 · 즉석사진 · 심리스 · 데이로그 · 파스텔 · Y2K · 매거진)
· 2160px 고화질 내보내기
· 앞으로 추가되는 Pro 기능도 모두

한 번 결제하면 추가 요금은 없습니다.

──

모든 색 분석은 기기 안에서만 이루어집니다.
사진과 위치 정보가 외부로 전송되는 일은 없습니다.
광고도, 회원가입도, 사용 기록 수집도 없습니다.
```

### 简体中文

| 項目 | 内容 |
|---|---|
| App名 | `ColorHunt：用颜色收集照片` |
| サブタイトル | `拍下的瞬间，就成了拼贴` |
| キーワード | `配色,调色板,胶片相机,复古,即时成像,半格,图片编辑,修图,整理,HEX,氛围感,Y2K,手帐,海报,拼贴,拼图,相册整理,滤镜,照片墙,四格,无缝` |

**プロモーションテキスト**
```
拍下的瞬间，就成了拼贴。相机胶卷按颜色自动整理。
胶片、半格、即时成像等12款模板，把收集到的颜色直接变成作品。
```

**説明**
```
ColorHunt 是一款用「颜色」收集照片的相机与拼贴应用。
定下今天的主题色，走上街头，拍下遇见的颜色。只是这样，就成了作品。

■ 拍下的瞬间，就成了拼贴
把拼贴模板直接叠在取景框上的「取景模式」。
每按一次快门就填满一格，拍完的时候拼贴已经完成。
不需要「回头再拼」。

■ 相机胶卷，按颜色自动整理
把手上的照片按主色自动分成12个颜色分组。
红·橙·黄·黄绿·绿·浅蓝·蓝·紫·粉·白·黑·灰。
收集到的颜色越多，属于你的色彩收藏就越完整。
觉得分得不对，长按照片就能自己重新选色。

■ 12款模板
四格 / 日常记录 / 马卡龙 / 照片墙 / Y2K
半格 / 即时成像 / 胶片 / 杂志
留白 / 组图 / 无缝

分为潮流·复古·极简三个类别，不用犹豫就能选到。
照片可以拖动排序，点击进入裁剪（双指缩放）。
支持 1:1 / 4:5 / 9:16 社交尺寸导出。

■ 色彩标本
可以把用到的颜色记录一并导出。
色彩分类与 HEX 色号并排，像标本标签一样的调色板。
既可以铺在作品下方作为色带，也可以立在中间或两侧成列。

■ 今日之色
转动转盘，决定今天要寻找的颜色。
可以在你喜欢的时间收到提醒，让每天的外出多一点期待。

■ 色彩地图
带有拍摄位置的照片，会以彩色图钉排列在地图上。
在哪条街收集了什么颜色——用颜色走过的路线会留下来。

■ 网格
以社交主页的排列方式，在发布前先确认整体的样子。

■ ColorHunt Pro（一次性买断）
· 去除导出水印
· 7款 Pro 专属模板（半格·即时成像·无缝·日常记录·马卡龙·Y2K·杂志）
· 2160px 高清导出
· 今后新增的 Pro 功能也全部包含

一次付费，没有额外费用。

──

所有颜色分析都只在设备本地完成。
照片与位置信息不会被发送到任何外部服务器。
没有广告，无需注册账号，也不收集使用数据。
```

---

## 2. App プライバシー（栄養ラベル）の回答

App Store Connect の「App のプライバシー」で聞かれる最初の質問:

> **このAppまたはサードパーティのパートナーは、このAppからデータを収集しますか？**
> → **いいえ（No）** を選択してください。

これで質問票は終了し、ストアには「**データを収集していません**」と表示されます。

### 「いいえ」で正しい根拠

Apple の定義では「収集（collect）」＝**端末外へ送信すること**です。実装を確認済み:

| 確認項目 | 結果 |
|---|---|
| `URLSession` / HTTP 通信 | **コード中に一切なし** |
| 解析SDK（Firebase・Google Analytics 等） | なし |
| 広告SDK | なし |
| クラッシュレポートの外部送信 | なし（Xcode Organizer 経由のApple標準のみ） |
| アカウント登録・ログイン | 機能自体が存在しない |
| 写真の送信 | なし（解析は端末内、保存はアプリ専用領域） |
| 位置情報の取得 | `CLLocationManager` **不使用**。許可を求めていない |

> **位置情報について**: カラーマップは、利用者が既に持っている写真に埋め込まれた
> Exif GPS を端末内で読むだけです。端末の現在地は取得していません。
> 質問票で位置情報に「はい」と答える必要はありません。

> **地図タイルについて**: MapKit が Apple のサーバーから地図画像を取得しますが、
> これは Apple のフレームワークによる通信であり、開発者によるデータ収集には
> あたりません。申告は不要です。

### Privacy Manifest（必須）

`iosApp/ColorHunt/PrivacyInfo.xcprivacy` を作成済みです。

**Xcode で対象ターゲットに追加してください**（プロジェクトナビゲータへドラッグ →
Target Membership に `ColorHunt` をチェック）。Copy Bundle Resources に入ります。

- これが無いと、アップロード時に **ITMS-91053 (Missing API declaration)** で
  弾かれます（2024年5月以降の必須要件）
- 宣言している内容: `UserDefaults` の使用（理由コード `CA92.1` = 自App内のみで
  読み書き）。`@AppStorage` 5箇所＋`UserDefaults.standard` 4箇所（Pro判定＋
  コラージュ設定の保存/復元）が該当します
- トラッキング＝なし、収集データ＝なし、で宣言しています

---

## 3. 年齢制限（Age Rating）の回答

すべて「なし / No」で回答します。結果は **4+**。

| 質問 | 回答 |
|---|---|
| 暴力・性的表現・不適切な言葉・薬物 | すべて「なし」 |
| ギャンブル | なし |
| コンテスト | なし |
| 制限のないWebアクセス | **なし**（アプリ内ブラウザが存在しないため） |
| ユーザー生成コンテンツ | **なし** ※下記参照 |

> 「ユーザー生成コンテンツ」は、**アプリ内で他のユーザーとコンテンツを共有できる
> 場合**に該当します。ColorHunt は共有シート経由で外部アプリへ渡すだけで、
> アプリ内に投稿機能・他人のコンテンツ表示がないため「なし」が正解です。

---

## 4. 審査メモ（App Review Information → Notes）

そのまま貼り付けてください。

```
ColorHunt は、写真を主役の色で分類し、コラージュを作成するアプリです。
サーバーとの通信を一切行わず、すべての処理は端末内で完結します。
ログインは不要のため、テスト用アカウントはありません。

【動作確認の手順】
1. 起動 → オンボーディング →「はじめる」
2. ハントタブ →「自動で仕分け」→ 写真へのアクセスを許可
   → 直近の写真が色ごとのグループに分類されます
   （シミュレータの初期写真でも動作しますが、実機のほうが分かりやすいです）
3. 中央のカメラボタン → フレームを選んで撮影 → コラージュが自動生成されます
4. コラージュタブ → 写真を選択 → テンプレートを選ぶ → 保存

【アプリ内課金について】
・製品ID: com.ykdev.ColorHunt.pro
・非消耗型の買い切りです（サブスクリプションではありません）
・購入導線: コラージュタブ → Proテンプレートまたは透かし表示部分をタップ
・「購入を復元」ボタンをペイウォール内に実装しています
・無料のままでも、5種類のテンプレートで書き出し（透かし付き）まで利用できます

【権限について】
・カメラ: 撮影機能のため
・写真ライブラリ: 色による分類と、Exifの撮影地を地図に表示するため
・通知: 「今日の色」の任意のリマインダーのため（オフでも全機能が使えます）
・位置情報の許可は求めていません（写真に記録済みの情報を端末内で読むのみ）
```

---

## 5. その他の必須項目

| 項目 | 値 |
|---|---|
| カテゴリ（プライマリ） | 写真/ビデオ（Photo & Video） |
| カテゴリ（セカンダリ） | グラフィック/デザイン（Graphics & Design） |
| 著作権 | `2026 <あなたの氏名または屋号>` |
| サポートURL | `https://souru67-dot.github.io/colorhunt-site/` |
| プライバシーポリシーURL | `https://souru67-dot.github.io/colorhunt-site/privacy.html` |
| マーケティングURL | 任意（未設定でよい） |

### サポートページ / プライバシーポリシーのホスティング

`site/` に2ページ用意済みです。**両方ともストア掲載の必須項目**なので、
承認を待つ間にURLを確定させておくと、承認後の作業が一気に短くなります。

**推奨: 専用の公開リポジトリを作る**

このリポジトリは開発ドキュメント（ビルド設定・リリース計画）を含むため、
そのまま公開すると全部見えてしまいます。別リポジトリが安全です。

```bash
# 1. GitHub で公開リポジトリ colorhunt-site を作成
# 2. site/ の中身だけを push
cd /path/to/gh_test_repo/site
git init && git add . && git commit -m "ColorHunt support and privacy pages"
git branch -M main
git remote add origin https://github.com/<あなた>/colorhunt-site.git
git push -u origin main
# 3. GitHub → Settings → Pages → Source: main / (root)
```

**公開済み**（2026-07-30）:

- サポート: https://souru67-dot.github.io/colorhunt-site/
- プライバシー: https://souru67-dot.github.io/colorhunt-site/privacy.html

> 独自ドメインを持っている場合は Pages の Custom domain に設定できます。
> 無くても審査は通ります。

---

## 6. 提出前チェック（このドキュメント分）

- [ ] `PrivacyInfo.xcprivacy` を Xcode のターゲットに追加した
- [ ] サポートURL / プライバシーポリシーURL が**実際に開ける**ことを確認した
- [ ] App名・サブタイトル・キーワードを4言語分入力した
- [ ] App のプライバシー = 「データを収集していません」で提出した
- [ ] 年齢制限 = 4+ になっていることを確認した
- [ ] 審査メモに §4 を貼った
- [ ] 課金アイテムのIDが `com.ykdev.ColorHunt.pro` で一致している
- [ ] スクリーンショット（6.9インチ）を登録した
