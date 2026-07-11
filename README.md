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
- 常設ボトムシート:選択日の予定リスト → 詳細 → 編集 / 複製 / 削除(カレンダー本体を隠さない)
- 日付セル長押しで新規予定のクイック作成、予定チップ長押しでドラッグ&ドロップ移動

**予定の作成・編集**
- タイトル・終日・開始/終了(M3 DatePicker / TimePicker)・カレンダー選択・場所・通知・繰り返し(RRULE)・メモ。タイトル+日時だけで即保存可
- 繰り返し:毎日/毎週(曜日指定)/毎月/毎年+終了日。複雑なRRULEは壊さずそのまま保持
- イベント色:Googleアカウントの同期パレット(CalendarContract.Colors)から選択。Googleカレンダー側と同じ色セットで、双方向に同期
- 繰り返しの予定は「この予定のみ / すべての予定」を選んで編集・削除(CalendarProviderの例外イベント)

**週 / 日表示**
- シンプルなタイムライン形式(重なりはレーン分割)。終日行・祝日色対応

**設定**
- 週の開始曜日(日/月)、月表示のスクロール方向、表示するカレンダーの選択、テーマ(システム/ライト/ダーク)

**ウィジェット(Glance)4種**
- 月カレンダー(4x4):当月グリッド+予定のある日にドット、今日ハイライト
- 今日の予定リスト(4x2):今日〜明日の予定を時刻付き表示
- 時計+今日の予定(4x2):TextClockによるライブ時計と今日の予定
- カレンダー+今日の予定(4x4):月グリッドの下に今日の予定
- すべてタップでアプリの該当日を開く。日付変更(深夜0時)と予定変更(WorkManagerのContentUriTrigger)で自動更新、システムテーマ/Dynamic Color追従
- 背景の透明度を設定画面で調整可能(20〜100%)

**タスク(ToDo)**
- 日付に紐づくローカルタスク。日別シートで追加・完了・削除、月グリッドに未完了数を表示
- ※Google Tasksは公開ContentProviderがないため端末ローカル保存(同期にはGoogle Tasks API+OAuthが必要)

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
