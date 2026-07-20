# ColorHunt — リリース手順書（Android）＋ iOS並行開発方針

最終更新: 2026-07-20

## 0. リリース可否の現状評価（正直な所見）

**判定: 「内部テスト（Internal testing）開始OK」レベル。公開（製品版）リリースは下記3点のクリア後。**

| # | 残課題 | 状態 | 対応 |
|---|---|---|---|
| 1 | 課金の実機確認 | Play Console設定＋署名ビルドでのみ検証可能 | 内部テストトラックで §5 のとおり確認 |
| 2 | プライバシーポリシーURL | 未作成（ストア掲載に必須） | §4.3 の雛形を基に作成・ホスティング |
| 3 | データ永続化 | インメモリ（終了で消える） | v1.0は既知の制限として明記／v1.1でRoom導入 |

強み（Instagramユーザー訴求）: 組写スタイルの中央/オーバーレイHEXパレット、トレンド準拠テンプレ5種、
グリッドプレビュー、9:16無料、色相整列 — 現行トレンド（ミニマル・エディトリアル/フィルム/組写/シームレス）に整合。

## 1. バージョニング

- `versionName`: セマンティック（1.0.0 から開始）／ `versionCode`: リリース毎に+1
- 場所: `app/build.gradle.kts` の `defaultConfig`

## 2. 署名（Play App Signing 前提）

1. アップロード鍵を生成（**リポジトリに絶対コミットしない**）:
   ```bash
   keytool -genkeypair -v -keystore upload-keystore.jks -alias colorhunt \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. リポジトリ直下に `keystore.properties` を作成（`.gitignore` 済み）:
   ```properties
   storeFile=/absolute/path/to/upload-keystore.jks
   storePassword=＊＊＊
   keyAlias=colorhunt
   keyPassword=＊＊＊
   ```
3. `keystore.properties` が存在すればリリースビルドは自動でその鍵で署名される。
   無い環境（CIなど）では**debug鍵でフォールバック署名**され、ビルド自体は常に通る
   （Playへのアップロードはローカルの upload 鍵署名ビルドで行うこと）。

## 3. ビルド

```bash
# 検証一式（ユニットテスト → AAB）
./gradlew :shared:testDebugUnitTest :app:testDebugUnitTest
./gradlew bundleRelease      # => app/build/outputs/bundle/release/app-release.aab
./gradlew assembleRelease    # 手元インストール確認用APK（必要なら）
```

CI（GitHub Actions `Android CI`）は push ごとにテスト＋debug APK＋release AAB を
アーティファクト出力する（AABはdebug鍵署名なので配布不可・検証用）。

## 4. Play Console 手順

### 4.1 初回セットアップ
1. アプリ作成（デフォルト言語: 日本語 / アプリ or ゲーム: アプリ / 有料区分: 無料）
2. **アプリ内アイテム**: プロダクトID `colorhunt_pro`（買い切り・非消耗）を作成し有効化
   ※IDはコード（`BillingManager.PRO_PRODUCT_ID`）と完全一致させること
3. ストア掲載情報:
   - タイトル: ColorHunt ─ 色で集める・組写コラージュ
   - 短い説明: テーマ色をハントして、色で仕分け。HEXパレット付きのおしゃれなコラージュをSNSへ。
   - スクリーンショット: ハント（色分けグリッド）/コラージュ（組写＋中央パレット）/テンプレ適用/今日の色ホイール/マップ を推奨
4. **データセーフティ**フォーム申告内容:
   - 収集: なし（すべて端末内処理。写真・位置情報は端末外へ送信しない）
   - 共有: なし ／ 暗号化: 該当なし ／ 削除リクエスト: 該当なし
   - ※osmdroidの地図タイル取得はOpenStreetMapへの通常のHTTPアクセス（個人データ送信なし）
5. 権限申告: READ_MEDIA_IMAGES / ACCESS_MEDIA_LOCATION（写真の色分類・撮影地マップ表示のため）、
   POST_NOTIFICATIONS（毎日の色通知・任意）

### 4.2 公開フロー（推奨）
1. **内部テスト**に AAB をアップロード → テスターで §5 と TEST_PLAN §2 を全確認
2. クローズドテスト（任意・数日）→ 段階的公開（10%→50%→100%）で**製品版**へ
3. リリースノート（ja/en）を毎回記載

### 4.3 プライバシーポリシー（雛形・要ホスティング）
> ColorHunt はユーザーの写真・位置情報・その他個人データを端末外に送信・収集しません。
> 写真へのアクセスは色分類・コラージュ作成のため端末内でのみ使用します。
> 写真のExif位置情報はマップ表示のため端末内でのみ使用します。
> 地図表示のため OpenStreetMap のタイルサーバーへ標準的な地図タイル取得リクエストが送信されます。
> アプリ内購入は Google Play を通じて処理され、当方が支払い情報を取得することはありません。
> お問い合わせ: souru67.giants.6@gmail.com

GitHub Pages 等でホスティングし、URLをストア掲載情報に設定する。

## 5. リリース直前チェックリスト

- [ ] `versionCode`/`versionName` 更新
- [ ] CI 全緑（テスト＋assembleDebug＋bundleRelease）
- [ ] TEST_PLAN.md §2 手動QA全項目
- [ ] release ビルド（minify有効）での実機スモークテスト — 難読化起因のクラッシュがないこと（特にコラージュ保存・マップ・課金導線）
- [ ] 内部テストで `colorhunt_pro` 購入→復元を確認
- [ ] プライバシーポリシーURL設定済み
- [ ] ストア画像・説明文・データセーフティ提出済み

## 6. iOS 並行開発（今後の開発環境）

- **共有コード**: 色分類コア（`shared/` KMP, `commonMain`）は iOS フレームワーク
  （iosX64/iosArm64/iosSimulatorArm64）として出力済み。コラージュ幾何
  （`CollageGeometry` 等の純Kotlinロジック）も順次 `shared` へ移設予定（v1.1）
- **iOS実装**: `iosApp/README.md` の手順で macOS + Xcode 15+ 上にプロジェクトを構成
  （SwiftUI + Apple MapKit）。この開発コンテナ（Linux）では iOS ビルド検証不可のため、
  **iOSのビルド/実行確認は macOS 環境で行う**運用とする
- **並行の進め方**:
  1. Android は本書 §4 のリリース作業（人手: Play Console／エージェント: コード・CI保守）
  2. iOS は Android の画面仕様を正として SwiftUI で移植（ハント→コラージュ→今日の色→グリッド→マップの順）
  3. 共有ロジックの変更は必ず `shared` に入れ、両OSのCI（Androidは既存 / iOSは将来 macOS ランナー追加）で検証
- **ブランチ運用**: 現行の `claude/colorhunt-android-phase1-bm7u8y` をAndroid v1.0安定化に充て、
  iOS作業は `claude/colorhunt-ios-*` 系ブランチで独立に進める（sharedの変更はAndroid CIで担保）
