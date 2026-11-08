# 省回來 SafeMoney

買了覺得太貴的東西？把它設成「回本目標」，每天少花一點，記下省了多少，看著進度條一路走到 100%。

- 平台：Android 8.0+
- 技術：Kotlin · Jetpack Compose · Material 3 · Room
- 開發規劃：見 [PLAN.md](PLAN.md)

## 下載安裝

到 [Releases](https://github.com/corgi8cm/SafeMoney/releases/latest) 下載 `SafeMoney-vX.Y.Z.apk`，傳到手機安裝（需允許「安裝未知應用程式」）。
正式版都用同一把金鑰簽章，之後的新版本可以直接覆蓋安裝，資料會保留。

長按主畫面 → 小工具 → 「省回來」可以放上「回本進度」小工具；長按 App 圖示可以直接「記一筆」。

資料只存在手機上。換手機或解除安裝前，到「設定 → 匯出備份」存一份 JSON 檔，之後在新手機用「從備份還原」。

### 測試版（每次 PR / push 的建置）

GitHub Actions 的 **Build** 也會產出 `safemoney-debug-…`，安裝後名稱為「省回來 測試版」：

- 與正式版是不同的 App，可以同時安裝，資料互不相通
- 每次建置的簽章都不同，更新測試版通常需要先解除安裝（測試資料會消失）

本專案不在本地編譯，所有 APK 都由 GitHub Actions 產生。

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
