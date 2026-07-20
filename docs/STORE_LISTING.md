# ColorHunt — Google Play 申請キット（v1.0.0）

このドキュメントは、Play Console に貼り付けるだけで申請できるよう、実装済みの
アプリ仕様（権限・機能・課金）に合わせて用意した掲載文・申告文です。
`docs/RELEASE.md`（ビルド/署名手順）とセットで使用します。

- applicationId: `com.souru.colorhunt`
- versionName / versionCode: `1.0.0` / `1`
- minSdk 26 / targetSdk 35
- 対応言語: 日本語 / English / 한국어 / 简体中文 / 繁體中文
- アプリ内購入: `colorhunt_pro`（買い切り・非消耗）

---

## 1. ストア掲載情報

### アプリ名（30文字以内）
- 日本語: `ColorHunt ─ 色で集める組写コラージュ`
- English: `ColorHunt — Color Collage & Palette`

### 短い説明（80文字以内）
- 日本語: `テーマ色をハント。写真を色で自動仕分けし、HEXパレット付きの組写コラージュをSNSへ。`
- English: `Hunt theme colors. Auto-sort photos by color and make HEX-palette collages for SNS.`

### 詳しい説明（4000文字以内）

**日本語**
```
ColorHunt は「カラーハンティング」——テーマ色を決めて街で写真を撮り、色でまとめて
投稿する遊び——に特化した写真コラージュアプリです。

■ 色で自動仕分け（ハント）
選んだ写真、または端末の写真を、ドミナントカラー（主役の色）で自動的に仕分け。
赤・橙・黄・黄緑・緑・水色・青・紫・ピンク＋白・黒・グレーの色グループに整理され、
あなただけのカラーホイールが育ちます。判定が違うと感じたら写真を長押しで色を変更。

■ 組写コラージュ
選んだ写真を、トレンドの「組写」スタイルでコラージュに。中央や左右にHEXコードの
カラーパレットを配置したり、写真に半透明で重ねたり。ドラッグで並べ替え、タップで
トリミング（ピンチ拡大縮小）も自在。1:1 / 4:5 / 9:16 のSNSサイズに書き出せます。

■ 雑誌風テンプレート
ホワイト／フィルム／組写／マガジン／シームレス——SNSの流行を取り入れた
ワンタップのテンプレートで、迷わずおしゃれに仕上がります。

■ 今日の色
リング型のカラーホイールをまわして、今日ハントする色を決めましょう。
毎日お好みの時刻にお知らせも届きます。

■ グリッド / カラーマップ
Instagram風のフィードプレビューで投稿の並びを事前確認。位置情報つきの写真は
カラーマップにピンで表示され、色でめぐった軌跡を振り返れます。

■ ColorHunt Pro（買い切り）
・書き出しの透かしを除去
・全SNSサイズ＋16:9
・カラーパレット配置＆HEXチップ（写真に重ねる）
・コラージュ枚数が無制限
・散歩ログ（カラーマップ）を高解像度で書き出し

写真の解析はすべて端末内で行われ、写真や位置情報が外部に送信されることはありません。
```

**English**
```
ColorHunt is a photo-collage app built for "color hunting" — pick a theme color,
shoot it around town, and post your finds grouped by color.

■ Auto-sort by color
Your picked or on-device photos are sorted automatically by their dominant color
into Red, Orange, Yellow, Green, Cyan, Blue, Purple, Pink and White/Black/Gray.
Your personal color wheel grows as you collect. Long-press a photo to re-file it.

■ HEX-palette collages
Arrange your photos in the trending "duo" style. Place a HEX color palette in the
center or on a side, or overlay it translucently on the photos. Drag to reorder,
tap to adjust the crop (pinch to zoom). Export at 1:1 / 4:5 / 9:16.

■ Magazine templates
White / Film / Duo / Magazine / Seamless — one-tap presets tuned to current SNS
trends make a polished result effortless.

■ Today's color
Spin the ring-style color wheel to pick today's hunt color, with an optional
daily reminder at the time you choose.

■ Grid & Color Map
Preview your feed Instagram-style before posting. Geotagged photos appear as
colored pins on your Color Map so you can revisit your color walks.

■ ColorHunt Pro (one-time purchase)
Remove the watermark, unlock all SNS sizes + 16:9, palette placement & on-photo
HEX chips, unlimited collage cells, and high-res walk-log export.

All photo analysis happens on-device; your photos and location never leave it.
```

### カテゴリ / タグ
- カテゴリ: 写真（Photography）
- タグ候補: コラージュ / 写真編集 / カラーパレット / SNS

---

## 2. データセーフティ（Data safety）申告

Play Console の「データセーフティ」フォームでは以下のとおり回答:

- **データ収集**: なし（No data collected）
  - 写真・位置情報はすべて端末内でのみ処理し、当社サーバー等へ送信・収集しません。
- **データ共有**: なし（No data shared）
- **暗号化（転送時）**: 該当なし（データを送信しないため）
- **データ削除リクエスト**: 該当なし
- 補足メモ（任意記入欄）:
  - 地図表示のため OpenStreetMap のタイルサーバーへ標準的な地図タイル取得
    リクエストが送信されます（個人データは含みません）。
  - アプリ内購入は Google Play を通じて処理され、当社は支払い情報を取得しません。

## 3. 権限の用途申告

Play Console は広い写真アクセス（`READ_MEDIA_IMAGES`）に用途宣言を求めます。
「写真とビデオ」権限フォームには次を記入:

> 本アプリの中核機能は、ユーザーの写真を主役の色（ドミナントカラー）で自動分類し、
> 色ごとのコラージュを作成することです。多数の写真を色解析し、Exifの位置情報を
> カラーマップに表示するために、写真ライブラリへのアクセスが必要です。解析は
> すべて端末内で行い、写真を外部送信しません。

- `ACCESS_MEDIA_LOCATION`: 写真のExif GPSをカラーマップに表示するため
- `POST_NOTIFICATIONS`: 「今日の色」の任意の毎日通知のため
- `INTERNET` / `ACCESS_NETWORK_STATE`: 地図タイル取得のため

> 補足: フォトピッカーのみで十分なユーザーには、権限を「あとで」スキップしても
> 手動追加で基本機能が使えます（初回オンボーディングで選択可能）。

## 4. コンテンツのレーティング
- 全年齢向け（暴力・性的表現・ギャンブル等なし）。
- 質問票では「ユーザー生成コンテンツの共有」= 画像を外部アプリへ共有する点のみ該当。

---

## 5. スクリーンショット計画（携帯電話用・最低2枚 / 推奨5〜8枚）

| # | 画面 | キャプション案（日本語） |
|---|---|---|
| 1 | ハント（色グループ表示） | テーマ色で、写真が自動で仕分けられる |
| 2 | コラージュ（組写＋中央HEXパレット） | HEXパレット付きの「組写」コラージュ |
| 3 | テンプレート適用後 | ワンタップの雑誌風テンプレート |
| 4 | 今日の色（リングホイール） | 今日ハントする色を、くるっと決める |
| 5 | カラーマップ（色ピン＋写真バブル） | 色でめぐった街を、地図で振り返る |
| （任意）6 | Paywall / Pro | Proでもっと自由に、もっとおしゃれに |

- 推奨解像度: 1080×1920 以上（9:16）。実機のスクショでOK。
- フィーチャーグラフィック（1024×500）: レインボーのカラーホイール＋アプリ名。

---

## 6. リリースノート（v1.0.0）

**日本語**
```
ColorHunt 初回リリース 🎨
・写真をドミナントカラーで自動仕分け
・HEXパレット付きの組写コラージュ、雑誌風テンプレート
・今日の色（デイリー通知）、Instagram風グリッド、カラーマップ
・日本語/English/한국어/简体/繁體 に対応
```

**English**
```
ColorHunt first release 🎨
- Auto-sort photos by their dominant color
- Duo-style collages with HEX palettes and magazine templates
- Today's color (daily reminder), Instagram-style grid, and a color map
- Available in EN / JA / KO / ZH-CN / ZH-TW
```

---

## 7. 提出前の最終確認（このドキュメント分）
- [ ] ストア名・短い説明・詳しい説明（ja/en、必要なら ko/zh）を貼付
- [ ] データセーフティ = データ収集なし で提出
- [ ] 「写真とビデオ」権限フォームに §3 の用途を記入
- [ ] スクリーンショット5枚＋フィーチャーグラフィックを登録
- [ ] `colorhunt_pro` を作成・有効化（ID一致）
- [ ] プライバシーポリシーURL（RELEASE.md §4.3 の雛形）を設定
- [ ] リリースノート（ja/en）を記入
