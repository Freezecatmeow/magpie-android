# v0.1.2 · magpie for Android (unofficial port)

## 下载
- APK：`magpie-android-arm64-v0.1.2-offline.apk`（60.8 MB，sha256 c3254c262832f6bcc03ec9a86e641aee65b272a465d5fa9f3fdea41ff4d99fd8）
- 平台：Android 7.0+，arm64-v8a；完全离线（内置 Bun 与 WorkBuddy/Qoder 插件）

## 内容
- magpie（Go headless，`-tags nogui`，android/arm64）以 `libmagpie.so` 运行，WebView 载入其 Web UI。
- 完全离线：Bun 运行时 `libbun.so` + 预装 `@magpie-community/opencode-workbuddy-auth`、`opencode-qoder-auth`（assets/seed.zip）。
- 图标/名称 magpie；返回键回退上一页；顶部状态栏白色无黑边。
