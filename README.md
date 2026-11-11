# ShareGyazo

ギャラリーから画像の共有やスクリーンショットの通知からインテントを受け取り、Gyazoサーバへ画像をアップロードするアプリです。

Gyazoサーバは設定で変更できます。

Gyazoサーバから帰ってきた画像のURLは、

 * クリップボードにコピー
 * ブラウザで開く
 * テキストとして他のアプリへ渡す

などの挙動を選べます。

大きい画像はアップロード時にフルHD程度までリサイズされます。

## ダウンロード

 * https://play.google.com/store/apps/details?id=xtuaok.sharegyazo

## 主な使い方

 1. OSの機能でスクリーンショットを撮ります
 2. 通知バーにスクリーンショットの通知が出るので選択します
 3. ShareGyazo

## Tips

 * トリミングしたいんだけど => ギャラリーでトリミングしてからShare

## 開発

| 項目 | バージョン |
| --- | --- |
| 言語 / UI | Kotlin 2.4 / Jetpack Compose (Material 3) |
| ビルド | Gradle 9.9 (Kotlin DSL + Version Catalog) / Android Gradle Plugin 9.5 |
| SDK | compileSdk / targetSdk 37, minSdk 28 (Android 9) |
| JDK | 17 以上 |

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

主な構成:

 * `ShareGyazo` — 共有インテントの受け口（透過 Activity）。プロファイル選択と WorkManager へのアップロード登録
 * `GyazoPreference` — 設定画面（Compose + Navigation）
 * `upload/UploadWorker` — 画像変換・アップロード・通知（WorkManager, フォアグラウンド実行）
 * `share/ShareShortcuts` — 共有シートの Direct Share にプロファイルを表示
 * 旧バージョン (2.x) の設定・プロファイルはそのまま引き継がれます

## プライバシーポリシー

 * https://xtuaok.sickin.dev/ShareGyazo/
 * ソースは `site/public/ShareGyazo/`。Cloudflare Workers の静的アセットとして配信し、`master` への push で `.github/workflows/site.yml` がデプロイします

## 寄付

寄付を募集しております。

 * Amazonギフト券 https://www.amazon.co.jp/registry/wishlist/1LJRYW9IZ9IU/
  * xtuaok@gmail.com まで

