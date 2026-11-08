# 省回來 SafeMoney — 開發規劃

## 1. 產品概念

買了一件覺得太貴的東西（例如 7000 元的大衣）後，想靠日常「少花一點」把這筆錢省回來。
SafeMoney 把每一筆「想省回來的花費」變成一個**回本目標**，每次少喝一杯飲料、晚餐吃省一點，就記一筆「省下的錢」，App 自動累積並顯示進度，讓省錢這件事不會不了了之。

核心用語：

| 名詞 | 意義 | 例子 |
|---|---|---|
| 回本目標 (Goal) | 一筆想省回來的花費 | 大衣 7000 元 |
| 省錢紀錄 (Saving) | 某一次少花的錢，歸到某個目標 | 今天沒買飲料，省 60 元 → 大衣 |
| 進度 | 已省金額 / 目標金額 | 1,240 / 7,000（17.7%） |

## 2. 技術需求（已確定）

1. **只支援 Android**
2. **APK 由 GitHub Actions 編譯**，本地不編譯
3. **使用 git 做版本控制**，遠端放在 GitHub

## 3. 技術選型

| 項目 | 選擇 | 理由 |
|---|---|---|
| 語言 | Kotlin | Android 官方首選語言 |
| UI | Jetpack Compose + Material 3 | 現代宣告式 UI，程式碼少 |
| 架構 | MVVM（ViewModel + StateFlow） | 官方建議架構，易測試 |
| 本地資料庫 | Room (SQLite) | 資料存在手機，不需帳號、不需伺服器 |
| 相依注入 | 手動注入（初期），之後視需要換 Hilt | 專案小，先保持簡單 |
| 建置 | Gradle (Kotlin DSL) + Version Catalog | 官方標準 |
| 最低版本 | minSdk 26（Android 8.0），targetSdk 最新穩定版 | 涵蓋 95%+ 裝置 |
| CI/CD | GitHub Actions | 雲端編譯 APK，符合需求 2 |

> 為什麼不用 Flutter / React Native：目前只做 Android，原生 Kotlin 最單純、工具鏈最完整。若未來要上 iOS 再評估 Kotlin Multiplatform 或 Flutter。

## 4. 功能規劃

### MVP（第一版必做）

1. **回本目標管理**
   - 新增目標：名稱、金額、購買日期、備註（選填）、圖示/顏色（選填）
   - 編輯、刪除、封存目標
2. **記錄省錢**
   - 選目標 → 輸入省下的金額 + 原因（例：「沒買飲料」）+ 日期（預設今天）
   - 常用原因快捷按鈕（飲料、外食、計程車…），可自訂
3. **進度追蹤**
   - 首頁列出所有目標卡片：進度條、已省 / 目標金額、百分比、還差多少
   - 目標詳細頁：該目標的所有省錢紀錄（可編輯、刪除）
4. **總覽**
   - 所有目標的總花費、總已省、整體進度
5. **完成慶祝**
   - 達成 100% 時顯示完成動畫，目標移到「已回本」區
   - 超出的金額可選擇轉到下一個目標

### 第二版（加分功能）

- 統計圖表：每日 / 每週省錢趨勢、各原因佔比
- 預估完成日：依近 30 天平均省錢速度推算「預計 X 月 X 日回本」
  - 剛開始記錄不到 30 天時，只用「開始記錄那天」到今天計算（開始日 = 目標建立日與最早紀錄中較早者）
  - 天數無條件進位；最近 30 天沒有紀錄時提示「記一筆就能預估」；超過 10 年不顯示日期
- 每日提醒通知（例如晚上 9 點提醒記錄今天省了什麼）
  - 設定頁開關 + 時間（預設 21:00）；設定存在 SharedPreferences，不進備份檔
  - AlarmManager `setAndAllowWhileIdle`（不需精確鬧鐘權限，可能晚幾分鐘），觸發後排隔天；開機、改時間/時區、App 更新、開啟 App 時重新排程
  - 今天已經有紀錄就不發通知；內容提到最接近回本的目標
  - Android 13+ 開啟時才要求通知權限；通知被關掉時設定頁會提示並可前往系統設定
- 資料匯出 / 匯入（JSON 檔），換手機不怕資料遺失
  - 設定頁 → 匯出備份 / 從備份還原，用系統檔案選擇器（不需要儲存權限）
  - 格式：`{"format":"safemoney-backup","version":1,"exportedAt":…,"goals":[…],"savings":[{…,"allocations":[…]}]}`，日期為 ISO-8601 字串
  - 還原 = **取代**全部資料（保留原編號），先完整驗證再寫入，且在同一個 transaction，失敗不影響原資料
  - 還原後依已省金額重新同步目標狀態；未知欄位忽略，較新版本的備份檔會被拒絕並提示更新
- 主畫面小工具 (Widget) 顯示進度
  - Jetpack Glance；顯示總共已省 / 總額、百分比與進度條，空間夠時列出最接近回本的進行中目標（最多 4 個）
  - App 開啟期間監聽資料庫，資料一變動就更新（不靠定時更新）；點小工具開啟 App
- 深色模式（✅ M2 起已跟隨系統設定）

### 往 v1.0 的功能（見第 10 節里程碑）

- 自訂快捷原因、目標顏色/圖示、刪除可復原、所有紀錄頁（可篩選）、從桌面圖示/小工具直接記一筆（✅ v0.7.0）
  - 資料庫第 2 版（自動升級）：`goal.emoji`、`quick_reason` 表；快捷原因依最近 90 天使用次數排序
  - 刪除紀錄、目標、快捷原因後，畫面下方有「復原」可救回（原編號、原狀態）
  - 長按桌面圖示的「記一筆」捷徑（動態捷徑，測試版也能用）；小工具右上角「＋ 記一筆」
- **多裝置同步**：用 Google 帳號登入，資料存在使用者自己 Google 雲端硬碟的 App 專用隱藏資料夾（appDataFolder），不經第三方伺服器
- **新版提醒**：GitHub 版檢查 GitHub Releases；Play 版改用 Google Play 應用程式內更新（Play 規定不可引導到 Play 以外更新）
  - ✅ product flavor `github` / `play`；github 版開 App 時每天最多查一次 `releases/latest`，首頁顯示「有新版本」，可在設定關閉或立即檢查
  - ✅ 測試版改用 repo 內固定的公開 debug 金鑰（Google 登入可註冊固定 SHA-1，也能直接覆蓋安裝）
- 做到可以上架 Google Play 的程度（隱私權說明、資料安全表單內容、AAB、商店素材），但先不實際上架

### 暫不做

- 英文介面
- 多人共享、自架後端伺服器

## 5. 資料模型（Room）

```kotlin
@Entity
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,            // 大衣
    val amount: Long,            // 7000（以「元」為單位的整數，避免浮點誤差）
    val purchaseDate: LocalDate,
    val note: String? = null,
    val color: Int? = null,
    val status: GoalStatus,      // ACTIVE / COMPLETED / ARCHIVED
    val createdAt: Instant,
    val completedAt: Instant? = null,
)

@Entity
data class Saving(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // 總金額不另存，= 其所有 SavingAllocation.amount 的加總（例：100）
    val reason: String,          // 沒叫外送
    val date: LocalDate,
    val createdAt: Instant,
)

// 一筆省錢紀錄可拆分到多個目標（例：100 元 → 大衣 60、耳機 40）
@Entity(
    primaryKeys = ["savingId", "goalId"],
    foreignKeys = [
        ForeignKey(Saving::class, ["id"], ["savingId"], onDelete = CASCADE),
        ForeignKey(Goal::class, ["id"], ["goalId"], onDelete = CASCADE),
    ],
)
data class SavingAllocation(
    val savingId: Long,
    val goalId: Long,
    val amount: Long,            // 分到這個目標的金額
)
```

- 拆分規則：至少分給一個目標、每份金額 > 0、同一目標不可重複（`Allocations.validate`）。
- 紀錄總金額 = 分配加總，因此不會出現「總額與分配不符」；刪除目標時，分給它的部分一起消失，完全沒有分配的紀錄也會被刪除。
- 記錄頁預設只選一個目標（最常見情境）；按「拆分」可加入多個目標，並提供「平均分配」快捷鍵（`Allocations.splitEvenly`，餘數從第一個目標起各多 1 元）。
- 已省金額不另存欄位，用 `SUM(saving_allocation.amount) WHERE goalId = ?` 即時計算，避免資料不一致。
- 目標狀態由 Repository 自動維護：已省 ≥ 目標金額 → `COMPLETED`；之後若刪除紀錄或調高金額而不足 → 退回 `ACTIVE`；`ARCHIVED` 不受影響。
- 超額轉移：不新增紀錄，而是把來源目標「最新的」幾筆分配改分給目標（例：最後一筆 500 元中 300 是多省的 → 改成「來源 200 + 目標 300」的拆分）。紀錄總額不變，來源剛好停在 100%。
- 同步地基（DB 第 3 版，手寫遷移 2 → 3）：
  - 目標、紀錄、快捷原因都有跨裝置唯一的 `uid` 與 `updatedAt`（使用者修改時更新；自動回本狀態不算修改）
  - 刪除仍是真的刪除，另外在 `deletion` 表留下墓碑（uid、類型、刪除時間）；復原或還原時移除墓碑
  - 刪除目標時：目標與「只分給它」的紀錄留墓碑，拆分的紀錄更新 `updatedAt`
  - 還原備份 = 取代：手機上有、備份檔沒有的資料也留墓碑
  - 備份檔第 2 版帶 uid 與 updatedAt；讀第 1 版時自動補上
- 資料庫 schema 匯出到 `app/schemas/` 並進 git，之後改版時用來撰寫與驗證 migration。CI 會檢查 schema 是否已提交。
- 金額一律用整數（元），台幣不需要小數。

## 6. 畫面規劃

```
首頁（目標列表 + 總覽）
 ├─ ＋ 新增目標
 ├─ 目標卡片 → 目標詳細頁（進度、紀錄列表、編輯/刪除）
 └─ 浮動按鈕「記一筆省錢」→ 記錄頁（選目標、金額、原因、日期）
設定頁（每日提醒 ✅、匯出匯入 ✅）— 首頁右上角齒輪
```

## 7. 專案結構

```
SafeMoney/
├─ .github/workflows/
│   ├─ build.yml          # 每次 push / PR：編譯 debug APK + 跑測試
│   └─ release.yml        # 推 v* tag：編譯簽章 release APK 並發佈到 GitHub Releases
├─ app/
│   └─ src/main/java/.../safemoney/
│       ├─ data/          # Room entity、DAO、Database、Repository
│       ├─ ui/
│       │   ├─ home/
│       │   ├─ goal/
│       │   ├─ saving/
│       │   └─ theme/
│       └─ MainActivity.kt
├─ gradle/libs.versions.toml
├─ build.gradle.kts
├─ settings.gradle.kts
├─ .gitignore
├─ README.md
└─ PLAN.md
```

## 8. 版本控制（git）規範

- 遠端：GitHub public repository
- 分支策略（簡化版 GitHub Flow）：
  - `main`：永遠可編譯、可安裝
  - `feature/xxx`：每個功能一條分支，完成後開 PR 合併回 `main`，CI 通過才合併
- Commit 訊息格式（Conventional Commits）：`feat: 新增目標頁`、`fix: 修正進度計算`、`chore: 更新 CI`
- 版本號：語意化版本 `v0.1.0`、`v0.2.0`…，打 tag 觸發 release
- `.gitignore` 排除：`build/`、`.gradle/`、`local.properties`、`*.jks` / `*.keystore`（簽章金鑰絕不進 git）

> 專案放在 OneDrive 同步資料夾內。OneDrive 同步 `.git` 偶爾會造成檔案鎖定或產生衝突副本；若 git 出現奇怪錯誤，先暫停 OneDrive 同步再操作。主要備份是 GitHub。

## 9. GitHub Actions 雲端編譯 APK

### build.yml（每次 push / PR）

1. `actions/checkout`
2. `actions/setup-java`（JDK 17, temurin）
3. `gradle/actions/setup-gradle`（指定 Gradle 版本並含快取；本地不裝 Gradle，因此 repo 不放 wrapper，由 CI 直接提供 Gradle）
4. `gradle testDebugUnitTest assembleDebug`
5. `actions/upload-artifact` 上傳 `app-debug.apk`

→ 到 GitHub 的 Actions 頁面點進該次執行，就能下載 APK 安裝到手機測試。

### release.yml（推 `v*` tag）

1. 同上環境
2. 從 GitHub Secrets 還原簽章金鑰：
   - `KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`
3. `gradle assembleRelease`
4. 建立 GitHub Release 並附上簽章後的 APK

> 簽章金鑰只需產生一次（可用 GitHub Actions 或任何有 JDK 的環境執行 `keytool`），之後每個版本都要用同一把，否則手機無法覆蓋更新。

### 不在本地編譯的影響

- 本地不需要裝 Android Studio / SDK，只需要編輯器 + git
- 缺點：每次確認結果要等 CI（約 3–6 分鐘）。因此 CI 一定要加 Gradle 快取，並盡量用單元測試驗證邏輯（進度計算、預估完成日等）

## 10. 開發里程碑

| 階段 | 內容 | 產出 |
|---|---|---|
| **M0 專案建置** ✅ | git init、建立 GitHub repo、Android 專案骨架（空白 Compose 畫面）、`build.yml` | CI 能產出可安裝的空白 APK |
| **M1 資料層** ✅ | Room entity / DAO / Repository + 單元測試 | 進度計算邏輯有測試保護 |
| **M2 MVP 畫面** ✅ | 首頁、新增/編輯目標、記錄省錢（含拆分）、目標詳細頁 | `v0.1.0`：可以實際拿來用 |
| **M3 完成體驗** ✅ | 完成慶祝、已回本區、超額轉移、總覽 | `v0.2.0` |
| **M4 發佈流程** ✅ | 簽章金鑰、`release.yml`、GitHub Releases | 正式簽章 APK 可長期更新 |
| **M5a 統計與預估** ✅ | 預估回本日（目標詳細頁、統計頁）、統計頁：每日/每週長條圖、原因佔比、連續記錄天數 | `v0.3.0` |
| **M5b 匯出匯入** ✅ | JSON 備份檔匯出 / 匯入，換手機不怕資料遺失 | `v0.4.0` |
| **M5c 每日提醒** ✅ | 設定頁、每天固定時間提醒記錄（通知權限） | `v0.5.0` |
| **M5d 主畫面小工具** ✅ | Widget 顯示整體或單一目標進度 | `v0.6.0` |
| **M6 補齊與順手** ✅ | 自訂快捷原因、目標顏色/圖示、刪除可復原、所有紀錄頁（依月份/原因篩選）、App 捷徑與小工具「記一筆」、新 App 圖示 | `v0.7.0` |
| **M7 資料改版（同步地基）** ✅ | 每筆資料加全域唯一 ID（UUID）、修改時間、刪除標記（tombstone）；Room migration 與遷移測試；備份檔第 2 版（仍可讀第 1 版）；本機錯誤紀錄可匯出 | `v0.8.0` |
| **M8 多裝置同步、新版提醒** | Google 登入 + Drive appDataFolder；變更後與開啟 App 時同步，逐筆以最後修改時間為準；GitHub / Play 兩個 product flavor，GitHub 版檢查新版 | `v0.9.0` |
| **M9 上架準備** | 升級 Gradle/AGP/Kotlin 等並符合 Play 當時的 targetSdk 要求；R8 瘦身；CI 產出 AAB；Compose UI 測試；無障礙與大字體檢查；新手說明；隱私權說明頁（GitHub Pages）；資料安全表單內容、商店素材、上架步驟清單 | `v0.10.0` |
| **v1.0 正式版** | 修正前面發現的問題、更新說明文件；承諾之後版本相容舊資料與舊備份檔 | `v1.0.0` |

## 11. 已確認事項

1. App 名稱：英文 SafeMoney，中文「省回來」（手機上顯示「省回來」）
2. 一筆省錢紀錄**允許拆分到多個目標**（見第 5 節 `SavingAllocation`）
3. GitHub repo 為 **public**
4. 專案放在原本的 OneDrive 資料夾（`桌面\projects\safe_money\SafeMoney`）
5. v1.0 方向（2026/11/08 確認）：
   - 做到可上架 Google Play 的程度，但暫不實際上架
   - 不做英文介面
   - 要連網路：做**多裝置同步**（不只是雲端備份）與新版提醒
