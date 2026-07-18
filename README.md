# Koyomi(こよみ)

FirstSeed Calendar にインスパイアされた、月表示中心・片手操作のミニマルな Android カレンダーアプリ。

## 技術スタック

- Kotlin / Jetpack Compose / Material 3(Dynamic Color 対応)
- MVVM + 単方向データフロー(ViewModel + StateFlow)
- データは Android **CalendarProvider** に直結(Google カレンダー / Exchange と同期)。独自 DB は持たず、設定のみ DataStore
- minSdk 26 / targetSdk 35

## モジュール構成

```
app/src/main/java/com/souru/koyomi/
├── KoyomiApplication.kt      # AppContainer(手動DI)
├── MainActivity.kt
├── data/
│   ├── model/                # CalendarInfo / EventInstance / EventDetails / EventDraft
│   ├── CalendarRepository.kt # CalendarProvider の読み書き + ContentObserver Flow
│   ├── SettingsRepository.kt # DataStore(週の開始曜日など)
│   └── holiday/JapaneseHolidays.kt # 日本の祝日をアルゴリズム計算(1980–2099)
├── ui/
│   ├── theme/                # M3 テーマ + 曜日カラー(CompositionLocal)
│   ├── AppNavHost.kt         # onboarding / month / editor
│   ├── month/                # 月表示(HorizontalPager + 6週固定グリッド + 常設ボトムシート)
│   ├── event/                # 予定の作成・編集
│   └── onboarding/           # 権限の説明とリクエスト
└── util/Dates.kt             # 月グリッド計算・ページ⇔月の変換
```

## 機能

**月表示(メイン)**
- 横スワイプページング(設定で縦の連続スクロールに切替可)、6週固定グリッド、予定チップ+「+N」表示、今日ハイライト、日本の祝日・土日の色分け
- 連日の予定はタイトル+細い色ラインの帯で表示(設定でチップ表示に切替可)。カレンダー色は彩度を抑えた落ち着いたトーンで描画
- 常設ボトムシート:選択日の予定リスト → 詳細 → 編集 / 複製 / 削除(カレンダー本体を隠さない)
- 日付セル長押しで新規予定のクイック作成、予定チップ長押しでドラッグ&ドロップ移動

**予定の作成・編集**
- タイトル・終日・開始/終了(M3 DatePicker / TimePicker)・カレンダー選択・場所・通知・繰り返し(RRULE)・メモ。タイトル+日時だけで即保存可
- 繰り返し:毎日/毎週(曜日指定)/毎月/毎年+終了日。複雑なRRULEは壊さずそのまま保持
- イベント色:Googleアカウントの同期パレット(CalendarContract.Colors)から選択。Googleカレンダー側と同じ色セットで、双方向に同期
- 複数カレンダー対応:アカウント内の全カレンダー(「仕事」等のサブカレンダー・祝日等の購読カレンダー含む)を毎回プロバイダから列挙。エディタのカレンダー選択は色ドット+名前+アカウント表示で、前回使ったカレンダーを記憶。設定でONにした非表示カレンダーはプロバイダのVISIBLE/SYNC_EVENTSも有効化
- 繰り返しの予定は「この予定のみ / すべての予定」を選んで編集・削除(CalendarProviderの例外イベント)

**週 / 日表示**
- シンプルなタイムライン形式(重なりはレーン分割)。終日行・祝日色対応

**設定**
- 週の開始曜日(日/月)、月表示のスクロール方向、表示するカレンダーの選択、テーマ(システム/ライト/ダーク)

**ウィジェット(Glance)4種 — ミニマルデザイン**
- 月カレンダー(4x4基準、`SizeMode.Responsive`で4x3にも追従):大きな月名+小さな年、1文字曜日ヘッダー(土日祝は彩度を落とした赤/青)、各日に最大3つの**カレンダー色ドット**、今日は塗りつぶし円のみで強調。日タップでアプリの該当日を開く
- 今日の予定リスト(4x2基準、2x2ではミニマル版=日付+次の予定のみに自動切替):大きな日付+件数、カレンダー色バー+時刻+タイトル、残りゼロなら「今日はこれで終わりです」、明日の最初の予定を薄色で1件プレビュー
- 時計+今日の予定(4x2)、カレンダー+今日の予定(4x4)
- **ウィジェットごとの設定画面**(長押し→再設定):テーマ(システム/ライト固定/ダーク固定)と背景の不透明度(0〜100%、半透明対応)
- 更新トリガー:予定変更(ContentUriTrigger)/深夜0時/タイムゾーン・時刻変更。ピッカーのプレビュー画像も新デザイン

**タスク(ToDo)— Google同期対応**
- タスクは「マーカー付きのカレンダー予定」(`#koyomi-task`)としてタスク用カレンダー(設定で選択)に保存されるため、**Googleカレンダーと同期**される
- 日別シートで追加・チェック・削除。タスクをタップすると予定エディタが開き、**時間指定・通知・色**も設定可能
- 月グリッドには未完了数「☑N」を表示(通常の予定チップとは分離)
- ※Google Tasks(ToDoリスト)そのものとの同期はTasks API+OAuthが必要なため、カレンダー予定方式を採用

**同期と通知**
- WorkManagerによる定期同期リクエスト(15分/30分/1時間/自動のみを設定で選択)+アプリ復帰時の即時同期
- CalendarProviderの`EVENT_REMINDER`を受信して自前のリマインダー通知を表示(Android 13+は`POST_NOTIFICATIONS`をオンボーディング/設定から許可)

**その他**
- 予定チップのドラッグ&ドロップ:ドロップ先で「移動 / 複製」を選択
- Googleカレンダーで設定したユーザー色に対応(プロバイダ色のアルファ欠落を正規化)
- 権限オンボーディング(拒否時も空のカレンダー+設定への導線)
- 日本語 / 英語ロケール、ライト / ダークテーマ、Dynamic Color

## ビルド

```
./gradlew assembleDebug
```

JDK 17 と Android SDK(compileSdk 35)が必要です。CI(GitHub Actions)でユニットテストと debug APK のビルドを行います。
