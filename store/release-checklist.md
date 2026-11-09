# 上架 Google Play 步驟清單

App 已經準備好上架需要的東西；這份清單是**真的要上架時**照著做的步驟。Google 的規定常常變動，實際操作時以 Play Console 上的說明為準。

## A. 事前準備（一次性）

- [ ] 註冊 Google Play 開發者帳號（個人帳號，一次性費用 US$25），完成身分驗證
- [ ] 在 GitHub repo 的 Settings → Pages 開啟 GitHub Pages：Source 選 `main` 分支的 `/docs` 資料夾
- [ ] 確認 https://corgi8cm.github.io/SafeMoney/privacy 打得開
- [ ] Google Cloud 的 OAuth 同意畫面：填好首頁與隱私權政策連結，發布狀態改成「正式版」（`drive.appdata` 是非敏感範圍，不需要審查）

## B. 建立 App

- [ ] Play Console →「建立應用程式」：名稱「省回來」、預設語言中文（台灣）、App、免費
- [ ] 「App 內容」照 [`data-safety.md`](data-safety.md) 填：隱私權政策、廣告、存取權、內容分級、目標對象、資料安全性、金融功能
- [ ] 「商店資訊」照 [`listing.md`](listing.md) 填文字、上傳圖示與主題圖片、手機截圖
- [ ] 「應用程式完整性」：使用 Play 應用程式簽署（預設），把我們的金鑰當作「上傳金鑰」

## C. 上傳版本

- [ ] 在 GitHub 推送版本 tag（例如 `v1.0.0`），等 Release workflow 跑完
- [ ] 到該次 workflow 的 Artifacts 下載 `safemoney-play-bundle-v1.0.0`，解壓得到 `SafeMoney-play-v1.0.0.aab`
- [ ] Play Console →「測試」→「封閉測試」建立測試群組，上傳 AAB
- [ ] 複製「應用程式完整性」裡 **App 簽署金鑰** 的 SHA-1 → 在 Google Cloud 新增一個 Android OAuth 用戶端（套件名稱 `io.github.corgi8cm.safemoney`），否則從 Play 安裝的版本不能同步

## D. 測試與正式發布

- [ ] 新的個人開發者帳號：Google 要求先做**封閉測試**，至少 12 位測試人員連續參與 14 天，之後才能申請正式版
- [ ] 測試期間確認：安裝、從舊版升級、記一筆、復原、提醒通知、小工具、備份還原、同步（兩支手機）
- [ ] 申請正式版存取權 → 建立正式版發布 → 送審
- [ ] 審查通過後，README 加上 Play 商店連結

## E. 之後每次更新

1. `app/build.gradle.kts` 的 `versionCode` 加 1、`versionName` 改新版號
2. 合併到 main → 推送 tag → 下載 AAB → Play Console 建立新版本上傳
3. GitHub 版本與 Play 版本用同一個版號，但兩者的簽章不同，**不能互相覆蓋安裝**（要先解除安裝；先匯出備份或開同步）

## 已經在程式裡處理好的 Play 要求

| 要求 | 狀態 |
| --- | --- |
| targetSdk 符合 Play 目前規定（36） | ✅ |
| 上傳格式 AAB、有簽章 | ✅ CI 自動產出 |
| Play 版不能引導到 Play 以外更新 | ✅ `play` 版本沒有新版檢查 |
| 隱私權政策網址、App 內也看得到 | ✅ 設定 → 關於 → 隱私權政策 |
| 使用者可以刪除雲端資料 | ✅ 設定 → 刪除雲端資料 |
| 通知權限在 Android 13+ 先詢問 | ✅ |
| R8 瘦身 | ✅ |
