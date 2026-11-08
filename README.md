# 省回來 SafeMoney

買了覺得太貴的東西？把它設成「回本目標」，每天少花一點，記下省了多少，看著進度條一路走到 100%。

- 平台：Android 8.0+
- 技術：Kotlin · Jetpack Compose · Material 3 · Room
- 開發規劃：見 [PLAN.md](PLAN.md)

## 取得 APK

本專案不在本地編譯，APK 由 GitHub Actions 產生：

1. 打開 repo 的 **Actions** 分頁 → 點選最新一次成功的 **Build**
2. 在頁面下方 **Artifacts** 下載 `safemoney-debug-…`（zip 內含 `app-debug.apk`）
3. 傳到手機安裝（需允許「安裝未知應用程式」）

## 開發流程

- `main` 永遠可編譯；新功能開 `feature/xxx` 分支，透過 PR 合併，CI 通過才合併
- Commit 訊息使用 [Conventional Commits](https://www.conventionalcommits.org/)：`feat:`、`fix:`、`chore:`…
