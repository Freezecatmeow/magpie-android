# magpie for Android (unofficial port)

把 macOS/Windows/Linux 的 [magpie](https://github.com/yetone/magpie)（Go 单二进制：统一为多个 AI 编码 agent 选模型/供应商 + 本地网关）适配到 Android 的**非官方移植**，产物为可安装的 **arm64-v8a** APK。

> 上游 magpie 为 MIT 许可，版权 yetone；本项目是其 Android 封装/移植，遵循同一 MIT 许可。

当前版本：**v0.3.0**（versionCode 6）。

## 形态
- **headless Go 内核 + 增强 WebView 宿主壳**：把 magpie 以 `GOOS=android GOARCH=arm64 -tags nogui` 交叉编译为 `lib/arm64-v8a/libmagpie.so`。自 **v0.3.0** 起使用 **Android NDK 以 `CGO_ENABLED=1` 链接 bionic `libc.so`** 编译（`readelf` 见 `NEEDED [libc.so]`、`LOAD` 对齐 `0x4000`），修复了此前 CGO=0 纯 Go 解析器在 Android 上 **DNS 解析失败** 的致命问题。
- App 启动后拉起 `magpie web --addr 127.0.0.1:3430 --no-open`（前台 `dataSync` 常驻服务托管），WebView 载入 `http://127.0.0.1:3430/?k=<key>`。
- **完全离线预置**：Bun 运行时（`bun-linux-aarch64-android`）以 `lib/arm64-v8a/libbun.so` 内置；WorkBuddy / Qoder 两个社区插件预装（`assets/seed.zip` → 首次启动同步解压到 `$HOME/.config/magpie`，`MAGPIE_BUN` 指向 nativeLibraryDir）。

## v0.3.0 主要变更（相对 v0.1.2 / v0.2.x）
- **Go DNS 根治（FR-01/BUG-01）**：`libmagpie.so` 改用 NDK r27 `aarch64-linux-android24-clang` + `CGO_ENABLED=1` 重编，动态链接 `libc.so`、16KB 页对齐。
- **宿主壳重写（v0.2.x 起）**：前台 `MagpieGatewayService` 常驻保活 + 崩溃退避重启；`AndroidHostBridge` 本地带 Token 桥接；Custom Tabs 外链/OAuth、SAF 文件选择、系统剪贴板、通知频道、快捷磁贴/桌面微件、内置诊断中心、`magpie://` 深链；随机持久化 Web Key（移除硬编码密钥）。
- **Go 侧 `androidHost` 桥接**（v0.3.0）：`OpenURL`→Custom Tabs、`Copy`→剪贴板、`ChooseFolder`/`OpenFolder`→SAF；**BUG-07**：Android 下禁止内核自替换只读的 `nativeLibraryDir/libmagpie.so`。
- FR-09/13/02/BUG-06（Bun 更新跳过 / `--lan` / 系统代理 / CA 信任）经核验为上游既有能力，本移植通过环境变量（`MAGPIE_BUN`、`*_PROXY`、`SSL_CERT_FILE`/`NODE_EXTRA_CA_CERTS`）接通。

## 目录
- `settings.gradle.kts` / `build.gradle.kts` / `app/build.gradle.kts` / `gradle.properties`：标准 Gradle 工程（AGP 8.x，`abiFilters=arm64-v8a`，`useLegacyPackaging=true`，正式 Release 签名）。
- `app/`：Android 工程（`AndroidManifest.xml`、`res/`、`assets/seed.zip`、`assets/android.css`、`src/ai/usemagpie/android/*.java` —— MainActivity、MagpieGatewayService、AndroidHostBridge、WebView/Chrome 客户端、通知/磁贴/微件/诊断等）。
- `scripts/`：`build-go-android.sh`（NDK CGO=1 交叉编译）+ 手工出包脚本（aapt2/javac/d8/zipalign/apksigner）+ seed 生成。
- `docs/`：对齐规格（`WINDOWS-PARITY-SPEC.md`）、验收报告（`PARITY-REPORT-v0.2.1.md`，含 v0.3.0 段）、发版说明（`RELEASE-NOTES-v0.2.1.md`）、移植/插件/验收等。
- `dist/`：APK 产物（体积大，**不随 git 提交**，见 [Releases](https://github.com/Freezecatmeow/magpie-android/releases)）。

## 构建
前置：Go ≥1.27、**Android NDK r26d/r27c**（CGO=1 必需）、JDK 17/21、Android SDK（`platforms;android-34` + `build-tools;34.0.0`）。
```bash
# 1) 交叉编译 Go 内核（NDK CGO=1；在 magpie 源码根目录，或见 scripts/build-go-android.sh）
CC="$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android24-clang" \
CGO_ENABLED=1 GOOS=android GOARCH=arm64 \
  go build -tags nogui -trimpath \
  -ldflags "-s -w -X main.version=v0.3.0-android -extldflags=-Wl,-z,max-page-size=16384" \
  -o app/src/main/jniLibs/arm64-v8a/libmagpie.so .

# 2) 取 Bun(android/arm64) 解出为 app/src/main/jniLibs/arm64-v8a/libbun.so；生成 assets/seed.zip（见 scripts/seed-build.sh）

# 3) 出正式 Release 包（二选一）
./gradlew assembleRelease            # 标准 Gradle（需联网/AGP/AndroidX 缓存）
# 或：手工管线（离线可用，产出与 Gradle 等价）
bash scripts/build-apk3.sh app/src/main/jniLibs/arm64-v8a/libmagpie.so app/src/main/jniLibs/arm64-v8a/libbun.so
```
签名：正式 Release keystore（`keystore.properties`/`*.keystore` 已 gitignore，不入库），APK Signature Scheme **v2+v3**。

## 下载与安装
- 最新版：**[Releases · v0.3.0](https://github.com/Freezecatmeow/magpie-android/releases/tag/v0.3.0)**
  - `magpie-android-arm64-v0.3.0-offline.apk`｜60,830,976 B
  - SHA-256 `e2b34e2b508c83f1f3ec1b156970a3e180b2496634fa94286c92e52b4faa182c`
  - versionCode 6 / 0.3.0，arm64-v8a，v2+v3 签名（CN=Magpie Android Release）
- 安装：`adb install -r magpie-android-arm64-v0.3.0-offline.apk`（或手机点击安装，需允许未知来源）。
- 从 v0.1.2（debug 签名）升级：签名不兼容，先导出加密 `.magpie-backup` → 卸载旧版 → 安装新版 → 导入恢复。

## 已知限制
- Android 上无法驱动桌面 CLI（Claude Code/Codex 等），本项目提供的是供应商/模型管理 + Web UI + 本地网关。
- 「完全离线」指安装/运行不联网下载；登录各订阅、拉模型与请求仍需访问相应厂商网络。
- v0.3.0 已构建级修复 Go DNS；**真机动态门禁 G6–G10、真实厂商网络（FR-04/05/10/20）、FR-06（无 git 的 GitHub tarball 安装）与 FR-19（Go 通知告警触发）仍待补齐/真机复测**，详见 `docs/PARITY-REPORT-v0.2.1.md` 的 v0.3.0 段。

## 许可
MIT（见 [LICENSE](LICENSE)）。上游：https://github.com/yetone/magpie （Copyright (c) 2026 yetone）。
