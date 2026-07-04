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

## 実装済み(フェーズ1・2)

- 月表示:横スワイプページング、6週固定グリッド、予定チップ+「+N」表示、今日ハイライト、日本の祝日・土日の色分け
- 常設ボトムシート:選択日の予定リスト → 詳細 → 編集 / 複製 / 削除(カレンダー本体を隠さない)
- 日付セル長押しで新規予定のクイック作成
- 予定エディタ:タイトル・終日・開始/終了(M3 DatePicker / TimePicker)・カレンダー選択・場所・通知・繰り返し(RRULE)・メモ。タイトル+日時だけで即保存可
- 権限オンボーディング(拒否時も空のカレンダー+設定への導線)
- 日本語 / 英語ロケール、ライト / ダークテーマ、Dynamic Color

## 今後(フェーズ3・4)

- 縦スクロール月表示(設定で切替)、予定のドラッグ&ドロップ移動
- 週 / 日表示(タイムライン)、設定画面(週の開始曜日・表示カレンダー選択・テーマ)
- Glance ウィジェット(月グリッド / 今日の予定リスト)、日付変更・予定変更時の更新

## ビルド

```
./gradlew assembleDebug
```

JDK 17 と Android SDK(compileSdk 35)が必要です。CI(GitHub Actions)でユニットテストと debug APK のビルドを行います。
