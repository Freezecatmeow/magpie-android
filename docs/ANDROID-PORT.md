# magpie · Android 移植说明（APK）

> 目标：把 https://github.com/yetone/magpie （26.8k★，Go 单二进制，官方支持 macOS/Windows/Linux/Docker/Termux）适配为可安装的 Android APK。

## 1. 结论（先看这里）

- magpie 上游**本身就是跨平台 Go 单二进制**：同一份代码通过构建标签产出 CLI / TUI / Web UI / Wails 桌面壳。官方 README 明确「Runs on macOS, Windows and Linux …, in Docker, and on **Termux**」。因此 Android 的合理形态不是"从零重写"，而是**把 headless Go 二进制 + WebView 外壳打成 APK**（本质是把 Termux 里的 web UI 装进一个 App）。
- 构建口径（上游 Makefile 原文）：`TARGETS = … android/arm64`；`ifeq ($(shell go env GOOS),android) TAGS = nogui`，即 **GOOS=android GOARCH=arm64 且 -tags nogui 可交叉编译**，无需 GTK/Wails。
- **产品局限（务必知晓）**：magpie 的核心价值是"给 Claude Code / Codex / Gemini CLI 等**桌面 CLI agent** 切换模型与供应商"。Android 上没有这些桌面 CLI，因此 APK 里**可用**的是：供应商/账号/模型管理、Web UI 配置、本地网关代理（127.0.0.1）；**不可用**的是：直接驱动桌面编码 agent。它更像"magpie 的移动控制台/配置面板"，而不是桌面版的 1:1 复刻。

## 2. 架构

```
┌─────────────────────────── APK (ai.usemagpie.android) ───────────────────────────┐
│  MainActivity (Java, WebView)                                                     │
│    ├─ 启动时用 ProcessBuilder 拉起 nativeLibraryDir/libmagpie.so                  │
│    │    参数: web --addr 127.0.0.1:3430 --no-open                                  │
│    │    环境: HOME=filesDir, TMPDIR=cacheDir, MAGPIE_WEB_KEY=<固定key>             │
│    ├─ 轮询 127.0.0.1:3430 就绪后，WebView 载入 http://127.0.0.1:3430/?k=<key>      │
│    └─ onDestroy 结束子进程                                                         │
│  lib/arm64-v8a/libmagpie.so  ← Go headless 二进制（GOOS=android GOARCH=arm64）      │
│  AndroidManifest: INTERNET 权限 + usesCleartextTraffic + extractNativeLibs=true    │
└───────────────────────────────────────────────────────────────────────────────────┘
```

关键设计点：
- **二进制放 lib/arm64-v8a/libmagpie.so**：Android 10+ 禁止在 app data 目录 exec（W^X），但 `nativeLibraryDir` 下的 so 可执行；命名须以 `lib` 开头 `.so` 结尾才会被当原生库落地。
- **明文回环**：WebView 访问 http://127.0.0.1 需 `android:usesCleartextTraffic="true"` + INTERNET。
- **登录密钥**：上游 web UI 链接带 `?k=<key>`；用环境变量 `MAGPIE_WEB_KEY` 固定 key，App 端即可拼出稳定 URL。
- **可写 HOME**：magpie 用 os.UserHomeDir 存配置/密钥，Android 上把 HOME 指向 app 私有 filesDir。
- **单 ABI**：仅打包 arm64-v8a（现代 Android 手机均为 arm64）。

## 3. 构建

前置：Go ≥1.27；JDK（11 用于 javac/keytool）；Android SDK（cmdline-tools + platforms;android-34 + build-tools;34.0.0）。

1) 交叉编译 Go 载荷（在 magpie 源码根目录）：
```bash
GOOS=android GOARCH=arm64 CGO_ENABLED=0 \
  go build -tags nogui -trimpath \
  -ldflags "-s -w -X main.version=v0.1.2431-android" \
  -o out/libmagpie.so .
```
2) 组装并签名 APK：
```bash
bash scripts/build-apk2.sh out/libmagpie.so
# 产出 build/magpie.apk（v2+v3 已签名）
```
脚本走 aapt2 compile/link → javac(jdk11, source8, bootclasspath android.jar) → d8 → 打包 classes.dex + lib/arm64-v8a/libmagpie.so → zipalign → apksigner 签名。

## 4. 验收状态

- 静态门槛（可验证）：包结构 / 签名(v2/v3) / zipalign 对齐 / 仅 arm64 / nativeLibraryDir 布局 —— 工具链已验证可产出合规 APK。
- 运行门槛（安装、首启 WebView 达本地服务、崩溃/权限）需要 **arm64 Android 设备或模拟器**；当前构建机无 adb/设备，故这部分**尚待真机验收**（详细清单见 docs/acceptance.md）。
- APK 内 Go 二进制取自 magpie 源码 `GOOS=android GOARCH=arm64 -tags nogui` 构建（headless，含 Web UI，不含桌面 webview / 内置网关插件）。

## 5. 已知限制
- 不含 Wails 桌面壳与内置 gateway 插件（budget/routing/redact 等），因此网关高级路由/配额切换功能可能缺失；配置管理与 Web UI 正常。
- 未内置 agent 适配器（Claude Code/Codex 等）——Android 上本就不存在这些 CLI。
- 初次启动 web 服务需要几秒，App 已做轮询等待。
