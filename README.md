# 省回來 SafeMoney

買了覺得太貴的東西？把它設成「回本目標」，每天少花一點，記下省了多少，看著進度條一路走到 100%。

- 平台：Android 8.0+
- 技術：Kotlin · Jetpack Compose · Material 3 · Room
- 開發規劃：見 [PLAN.md](PLAN.md)　更新紀錄：見 [CHANGELOG.md](CHANGELOG.md)

## 下載安裝

到 [Releases](https://github.com/corgi8cm/SafeMoney/releases/latest) 下載 `SafeMoney-vX.Y.Z.apk`，傳到手機安裝（需允許「安裝未知應用程式」）。
正式版都用同一把金鑰簽章，之後的新版本可以直接覆蓋安裝，資料會保留。

設定 → 定期省錢 可以設定「平日不買咖啡，每次 60 元」這種固定的省錢，到了那天 App 會跳出通知，關掉就記下來。

長按主畫面 → 小工具 → 「省回來」可以放上「回本進度」小工具；長按 App 圖示可以直接「記一筆」。

資料只存在手機上。換手機或解除安裝前，到「設定 → 匯出備份」存一份 JSON 檔，之後在新手機用「從備份還原」；
或是開啟「雲端同步」（見下方）。

### 相容性承諾（1.0.0 起）

- 新版本一定能直接覆蓋安裝，並自動轉換舊版本的資料（每次改資料格式都有遷移測試）
- 新版本一定能匯入舊版本匯出的備份檔（目前支援備份檔第 1、2 版）
- 舊版本遇到較新格式的備份檔或同步檔時會拒絕並提示更新，不會弄壞資料

### 測試版（每次 PR / push 的建置）

GitHub Actions 的 **Build** 也會產出 `safemoney-debug-…`（`app-github-debug.apk` 是 GitHub 版、`app-play-debug.apk` 是 Play 版），安裝後名稱為「省回來 測試版」：

- 與正式版是不同的 App，可以同時安裝，資料互不相通
- 測試版使用 repo 內固定的公開金鑰（`app/debug.keystore`），新的測試版可以直接覆蓋安裝

本專案不在本地編譯，所有 APK 都由 GitHub Actions 產生。

## 雲端同步（Google 雲端硬碟）

設定 → 雲端同步 →「用 Google 帳號開始同步」。資料存在你自己 Google 雲端硬碟裡 App 專用的隱藏資料夾，
App 只能看到自己建立的那個檔案；多支手機登入同一個帳號就會自動同步。

自己編譯或 fork 這個專案時，需要在 [Google Cloud Console](https://console.cloud.google.com/) 建立：

1. 啟用 **Google Drive API**
2. OAuth 同意畫面（External），權限 `https://www.googleapis.com/auth/drive.appdata`
3. 兩個 **Android** 用戶端：
   - 正式版 `io.github.corgi8cm.safemoney`：正式簽章金鑰的 SHA-1
   - 測試版 `io.github.corgi8cm.safemoney.debug`：`app/debug.keystore` 的 SHA-1（`25:D3:5C:E7:94:BA:2D:90:A7:21:DE:80:B5:05:BE:66:C8:25:EB:E9`）

## 隱私權與上架資料

- 隱私權政策：[`docs/privacy.md`](docs/privacy.md)（開啟 GitHub Pages 後網址為 https://corgi8cm.github.io/SafeMoney/privacy）
- Google Play 上架用：[`store/`](store/) 裡有商店文字、資料安全性表單內容、圖示與主題圖片、[上架步驟清單](store/release-checklist.md)

## 發佈新版本

1. 在 PR 中把 `app/build.gradle.kts` 的 `versionCode` +1、`versionName` 改成新版號，合併到 `main`
2. 在 `main` 上打 tag 並推送：`git tag v0.3.0 && git push origin v0.3.0`
3. **Release** workflow 會跑測試、編譯簽章 APK、驗證簽章，並建立 GitHub Release
   - tag 與 `versionName` 不一致時會直接失敗

簽章金鑰存在 repo 的 GitHub Secrets（`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`），不進 git。
**金鑰遺失就無法再發佈可覆蓋安裝的更新**，請另外備份。

## 開發流程

- `main` 永遠可編譯；新功能開 `feature/xxx` 分支，透過 PR 合併，CI 通過才合併
- Commit 訊息使用 [Conventional Commits](https://www.conventionalcommits.org/)：`feat:`、`fix:`、`chore:`…
