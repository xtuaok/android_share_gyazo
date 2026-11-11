# ShareGyazo

Google フォトなどの写真アプリからの共有や、スクリーンショットの通知からの共有で画像を受け取り、Gyazo や imgur へアップロードするアプリです。

## ダウンロード

 * https://play.google.com/store/apps/details?id=xtuaok.sharegyazo

## できること

### アップロード先（サーバプロファイル）

| プロファイル | 内容 |
| --- | --- |
| Gyazo（標準） | Gyazo にログインして、自分のアカウントにアップロード |
| imgur（標準） | imgur にログインして、自分のアカウントにアップロード |
| Gyazo（匿名） | ログインせずに Gyazo にアップロード（以前のバージョンと同じ方式） |
| imgur（匿名） | ログインせずに imgur にアップロード |
| その他のサーバ | Gyazo 互換のサーバ（upload.cgi）にアップロード。Bearer トークンでの認証にも対応 |

 * 匿名・その他のサーバのプロファイルは、「サーバプロファイル」の ＋ ボタンから作れます
 * よく使うプロファイルは、共有メニューのダイレクトシェアにも表示されます
 * デフォルトプロファイルを決めておくと、毎回の選択を省けます

### アップロード後の動作

返ってきた画像の URL は、

 * クリップボードにコピー
 * ブラウザで開く
 * テキストとして他のアプリへ渡す

などの挙動を選べます。完了の通知からも、開く・共有・コピーができます。

### 画像の変換

 * 大きい画像は、長辺が 1920px（フル HD 程度）になるように縮小します
 * 写真の向き（EXIF）を反映してからアップロードします
 * 形式は PNG / JPEG（品質も指定可）をプロファイルごとに選べます

## 主な使い方

 1. OS の機能でスクリーンショットを撮ります
 2. スクリーンショットの通知の「共有」から ShareGyazo を選びます
 3. アップロードが終わると、URL がクリップボードにコピーされます（設定による）

## Tips

 * トリミングしたいんだけど => Google フォトなどの写真アプリでトリミングしてから共有
 * Google や Apple のアカウントで imgur を使っている場合は、先にブラウザで imgur にログインしてから、アプリの「imgur にログイン」を押してください（imgur のアプリ連携画面からは、これらのアカウントでログインできません）

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

ビルドした APK / AAB は `output/ShareGyazo-<versionName>-<versionCode>.{apk,aab}` にもコピーされます。

### リリースビルドの署名

`~/.gradle/gradle.properties` に次を書くと、リリースビルドが署名されます（鍵やパスワードはリポジトリに入れません）。未設定なら未署名でビルドされます。

```properties
sharegyazo.keystore=/path/to/upload-keystore.p12
sharegyazo.keystorePassword=...
sharegyazo.keyAlias=upload
sharegyazo.keyPassword=...
```

### 主な構成

 * `ShareGyazo` — 共有インテントの受け口（透過 Activity）。プロファイル選択と WorkManager へのアップロード登録
 * `GyazoPreference` — 設定画面（Compose + Navigation）
 * `upload/UploadWorker` — 画像変換・アップロード・通知（WorkManager, フォアグラウンド実行）
 * `auth/` — Gyazo・imgur のログイン（OAuth）。シークレットが必要な処理は下記の中継サーバ経由
 * `share/ShareShortcuts` — 共有シートの Direct Share にプロファイルを表示
 * 旧バージョン (2.x) の設定・プロファイルはそのまま引き継がれます

## サイト（プライバシーポリシーとログインの中継サーバ）

`site/` は Cloudflare Workers で https://xtuaok.sickin.dev/ShareGyazo/ に配信しています。`master` への push で `.github/workflows/site.yml` がテストしてからデプロイします。

 * `site/public/ShareGyazo/` — プライバシーポリシー
 * `site/src/worker.js` — ログインの中継サーバ（Gyazo の認可コード交換、imgur のトークン更新、ログイン後にアプリへ戻るページ）。クライアントシークレットはアプリに含めず、Worker の Secret（`GYAZO_CLIENT_SECRET`、`IMGUR_CLIENT_SECRET`）に置きます
 * テスト: `cd site && node --test`

## 寄付

寄付を募集しております。

 * Amazonギフト券 https://www.amazon.co.jp/registry/wishlist/1LJRYW9IZ9IU/
  * xtuaok@gmail.com まで

