# リリース手順(Google Play 買い切り公開)

こよみを Google Play で公開するための手順です。開発者アカウント(登録料 $25、
一度きり)はご自身でご用意ください。以下はコマンドと設定の順序です。

## 0. 前提

- Play Console の開発者アカウント
- ローカルに JDK 17 と Android SDK(Android Studio 同梱でも可)
- **Play App Signing を利用します**(推奨)。アップロード鍵で署名した AAB を
  アップロードし、配布用の署名は Google が管理します。アップロード鍵を紛失
  しても復旧できるのが利点です。

## 1. アップロード鍵(keystore)を作成する

リポジトリには鍵もパスワードも含めません。ローカルで一度だけ作成します。

```bash
keytool -genkeypair -v \
  -keystore keystore/release.keystore \
  -alias koyomi \
  -keyalg RSA -keysize 2048 -validity 10000
```

- `keystore/` はリポジトリの `.gitignore` 済みです。**絶対にコミットしないで
  ください。** パスワードとこのファイルは安全な場所に保管します。

## 2. 署名情報を環境変数で渡す

`app/build.gradle.kts` は、鍵ファイルが存在し環境変数がある時だけリリース署名
を有効化します(無い場合は未署名でビルド=CI での R8 検証用)。

```bash
export KOYOMI_KEYSTORE_PASSWORD='＜keystoreのパスワード＞'
export KOYOMI_KEY_ALIAS='koyomi'
export KOYOMI_KEY_PASSWORD='＜鍵のパスワード。未設定ならkeystoreと同じ＞'
```

## 3. リリース AAB をビルドする

```bash
./gradlew bundleRelease
# 生成物: app/build/outputs/bundle/release/app-release.aab
```

R8(コード縮小・難読化)とリソース縮小が有効です。CI でも毎コミット
`bundleRelease` を実行して、縮小してもビルドが壊れないことを検証しています。

## 4. Play Console にアップロード

1. アプリを新規作成(アプリ名「こよみ」、無料/**有料**を選択 → 有料に設定)。
2. **Play App Signing** を有効化(初回アップロード時に案内されます)。
3. 「製品版」トラックに `app-release.aab` をアップロード。
4. バージョン: `versionCode = 1` / `versionName = "1.0.0"`(更新のたびに
   `versionCode` を必ず +1 します)。

## 5. ストア掲載に必要な項目

- **プライバシーポリシー URL**: `docs/privacy-policy.md` を GitHub Pages で公開
  し、その URL を登録(下記参照)。
- **データセーフティ**: `docs/data-safety.md` の回答をそのまま入力。
- **ストア掲載情報**: `docs/store-listing.md` の説明文・素材要件を参照。
- 価格: 有料アプリとして各国の価格を設定。

## 6. プライバシーポリシーを GitHub Pages で公開する

1. GitHub リポジトリの Settings → Pages を開く。
2. Source を「Deploy from a branch」にし、Branch を
   `main`(または既定ブランチ)、フォルダを `/docs` に設定して保存。
3. 数分後、`https://＜ユーザー名＞.github.io/gh_test_repo/privacy-policy` が
   公開されます。この URL を Play Console のプライバシーポリシー欄に入力します。
   (日本語版は末尾に `.ja` を付けた URL です。)

## 7. 段階的公開(推奨)

初回は製品版の「段階的公開」で 20% 程度から開始し、クラッシュや ANR が無いこと
を Play Console の品質指標で確認しながら 100% へ引き上げます。以降の更新も同様に
`versionCode` を増やして AAB を差し替えるだけです。
