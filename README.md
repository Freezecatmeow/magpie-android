# magpie for Android (unofficial port)

把 macOS/Windows/Linux 的 [magpie](https://github.com/yetone/magpie)（Go 单二进制：统一为多个 AI 编码 agent 选模型/供应商 + 本地网关）适配到 Android 的**非官方移植**，产物为可安装 APK（arm64）。

> 上游 magpie 为 MIT 许可，版权 yetone；本项目是其 Android 封装/移植，遵循同一 MIT 许可。

## 形态
- **headless Go 二进制 + WebView 外壳**：把 magpie 以 `GOOS=android GOARCH=arm64 -tags nogui` 交叉编译为 `lib/arm64-v8a/libmagpie.so`，App 启动时执行 `magpie web --addr 127.0.0.1:3430 --no-open`，WebView 载入 `http://127.0.0.1:3430/?k=<key>`。
- **完全离线**：Bun 运行时（`bun-linux-aarch64-android`）以 `lib/arm64-v8a/libbun.so` 内置；WorkBuddy / Qoder 两个社区插件预装（`assets/seed.zip` → 首次启动解压到 `$HOME/.config/magpie`，`MAGPIE_BUN` 指向 nativeLibraryDir）。

## 目录
- `app/`：Android 工程（AndroidManifest.xml、res/ 图标、src/MainActivity.java、assets/seed.zip）
- `scripts/`：构建脚本（交叉编译 + 手打包 aapt2/javac/d8/zipalign/apksigner；生成 seed）
- `docs/`：移植架构、插件移植、验收标准、UI 修复说明
- `dist/`：APK 产物（体积大，**不随 git 提交**，见 Releases）

## 构建
前置：Go ≥1.27、JDK 11（javac/keytool）、Android SDK（platforms;android-34 + build-tools;34.0.0）。
```bash
# 1) 交叉编译 magpie（在 magpie 源码根目录）
GOOS=android GOARCH=arm64 CGO_ENABLED=0 go build -tags nogui -trimpath \
  -ldflags "-s -w -X main.version=v0.1.2431-android" -o out/libmagpie.so .
# 2) 取 Bun(android/arm64) 解出为 out/libbun.so；生成 assets/seed.zip（见 scripts/seed-build.sh）
# 3) 出包
bash scripts/build-apk3.sh out/libmagpie.so out/libbun.so
```

## 安装
`adb install -r dist/magpie-android-arm64-v0.1.2-offline.apk`（或手机点击安装；需允许未知来源）。

## 已知限制
- Android 上无法驱动桌面 CLI（Claude Code/Codex 等），本项目提供的是供应商/模型管理 + Web UI + 本地网关。
- 完全离线指"安装/运行不联网下载"；登录各订阅、拉模型与请求仍需访问相应厂商。

## 许可
MIT（见 [LICENSE](LICENSE)）。上游：https://github.com/yetone/magpie （Copyright (c) 2026 yetone）。
