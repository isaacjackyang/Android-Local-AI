# Local AI（獨立 Android APK）

> 本公開倉庫包含應用程式原始碼、Termux 與 PRoot 相關原始碼，以及 Hermes Android 修改補丁；不包含模型、執行環境封裝、原生二進位、APK、簽署金鑰或個人資料。各模型的授權獨立於本專案程式碼。

套件名稱：`com.jackyang.localai`。獨立簽署、獨立資料目錄，可與原本的 `com.termux` 並存。

本專案把 Termux 終端機元件與原生執行環境、llama.cpp b12052、Hermes v0.22.6，以及原本下載的 LFM3 1.7B Q6_K GGUF 放入單一離線安裝檔。不是官方 Termux 或 Hermes Android 發行版。

使用方式：

1. 安裝 APK，開啟 Local AI。首次按 llama.cpp / Hermes 會解開執行環境並校驗模型。
2. llama.cpp 按鈕開啟 App 內的本機聊天網頁；Hermes 按鈕啟動模型後開啟 Hermes 命令列。
3. 設定頁可調整 CTX、CPU 執行緒數，並選擇或匯入其他 GGUF。預設 CTX 71680、4 執行緒。
4. 「儲存」於下次啟動套用；「儲存並重新啟動」會結束目前模型與終端機工作後套用。
5. 「捷徑」可分別建立 llama.cpp 與 Hermes 的主畫面捷徑。「停止」會停止本 App 啟動的工作。

匯入模型會複製到 Local AI 的內部儲存空間。可從 Android 檔案選擇器選擇 SD 卡上的檔案，但執行時使用 App 內的副本。內建模型及匯入模型可隨時切換；目前不提供刪除模型按鈕。

相容性與容量：

- ARM64，最低 Android 12L / API 32。預設 70K 建議 12 GB RAM；記憶體較少可先使用 8K 或 32K。
- APK 約 2.1 GB，解開後資料約 2.9 GB；建議安裝前保留至少 6 GB 可用內部空間。
- 70K 是設定的 context 上限。首次 Hermes 請求還會處理系統提示與工具說明，手機上可能需要數分鐘。
- llama.cpp 綁定 `127.0.0.1:8081`。原本 Termux 的腳本使用 8080；同時開兩份模型會增加記憶體負擔。
- 基本模型推論與 Hermes 本機聊天可離線使用。網路搜尋、外部服務與新增依賴仍需要網路及對應設定。
- 其他 GGUF 必須由這版 llama.cpp 支援，並符合手機記憶體容量。

Android 相容性修改記錄見 `licenses/NOTICE.txt`；本倉庫的 `patches/hermes-v0.22.6-android.patch` 可套用至所指定的 Hermes 上游版本，取得本 APK 對應的修改版原始碼。完整上游專案採 MIT 授權。
專案原始碼授權 GPL-3.0-only，第三方元件保留各自授權，模型使用獨立的 LFM Open License。

重建：安裝 JDK 21、Gradle 8.11.1、Android SDK 35，設定 `local.properties` 的 sdk.dir。
此倉庫無法單獨產出完整離線 APK：需另行取得先前提供的 1.0.0 離線 APK，或自行準備相容的執行環境、原生函式庫及受各自條款約束的模型。
若有原版 1.0.0 APK，可執行 `python restore-apk-assets.py /path/to/LocalAI-1.0.0-arm64-offline.apk`；工具會先檢查 SHA-256，再取回 runtime、payload、模型與必要原生函式庫。使用 `--check` 可只驗證，不寫入檔案。
這些大型內容不會上傳到本倉庫。`export-runtime.py` 記錄原始手機環境的匯出方式，其中工具與環境路徑針對原設備，並非可直接在任何手機執行的通用建置流程。
自行建立簽署金鑰及 signing.properties（欄位 storeFile、storePassword、keyAlias、keyPassword），
執行 `gradle :app:assembleRelease`。`create-signing-key.py` 是此 Windows 工作站的金鑰產生範例。

Hermes 原始碼與修改：

```sh
git clone --branch v0.22.6 https://github.com/NousResearch/hermes-agent.git
cd hermes-agent
git apply /path/to/Android-Local-AI/patches/hermes-v0.22.6-android.patch
```

上游 commit 為 `f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab`。PRoot 5.1.107.102 原始碼隨本倉庫提供；其他元件的版本、來源與授權見 `licenses/NOTICE.txt`。

**私密檔案**：`local-ai-release.jks` 與 `signing.properties` 只留在此工作站，不包含在 APK 或原始碼封裝。
保留這兩個檔案才能用同一簽署發布後續更新。不要把它們當作分享給其他手機的安裝檔。
