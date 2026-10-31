# magpie Android · WorkBuddy / Qoder 插件移植说明

## 1. 这两个"拓展插件"是什么
- 它们不是内置插件，而是**社区 OpenCode 插件**（npm 包，运行在 magpie 的 Bun 插件宿主里）：
  - `@magpie-community/opencode-workbuddy-auth` → providers `workbuddy` / `workbuddy-ai`，`replaces` workbuddy
  - `@magpie-community/opencode-qoder-auth` → provider `qoder`，`replaces` qoder
  - 见 `internal/plugin/market.json`。
- Agent 侧的 WorkBuddy / Qoder 接线（`internal/agent/workbuddy.go`、`qoder.go`，`agents.go` 注册）**已编译进二进制**，移动版无需额外处理。
- 插件宿主是"一个 Bun 进程跑 host.js"；`internal/plugin/bun.go` 明确支持 **android/arm64**（下载 `bun-linux-aarch64-android.zip`），所以手机可运行这些插件。

## 2. 本次改动（移动版）
在 `app/src/ai/usemagpie/android/MainActivity.java` 增加**首启自动安装**：
- 启动本地 magpie web 服务后，后台线程执行一次：
  `libmagpie.so plugin add workbuddy` 与 `libmagpie.so plugin add Qoder`
  （环境同服务：HOME=filesDir、TMPDIR=cacheDir、PATH 含 nativeLibraryDir）。
- 成功后在 filesDir 写标记 `.magpie_plugins_seeded_v1`，之后不再重复；失败则下次启动重试。
- 安装过程/退出码写 logcat（tag=Magpie，前缀 `[plugin add …]`）。
- 也可随时在 App 内 **Plugins 页**手动安装（走同一 market.json）。

## 3. 运行前提（如实）
- 首次自动安装需要设备**联网**：会下载 Bun 运行时 `bun-linux-aarch64-android`（约数十 MB）与两个 npm 插件包。
- 离线设备：自动安装会失败并重试；可用内置 Plugins 页在联网后再装。若需**完全离线**（连 Bun 一起预置进 APK），属可选的体积换离线方案（未在本次产物中启用）。

## 3.5 「即将上线 / Coming soon」的根因与修复（真机反馈）
- 现象：真机插件页里 WorkBuddy、Qoder、Kiro、Cursor Pro 等社区插件全部显示「即将上线」，点不动。
- 根因（源码）：`internal/gui/assets/plugins.js:493` —— 当插件未安装且 `listed.npm?.version` 为空时，按钮文案设为 `t("Coming soon")`，title 为「Not on npm yet」。即"magpie 向 npm 查询最新版本号失败/为空"。而 `internal/plugin/market.go` 的 npm 查询默认打 `https://registry.npmjs.org`（每个 6s、整体 8s 超时）。手机在**国内网络**访问 npmjs.org 超时 → 版本为空 → 全部「即将上线」。并非插件未发布（实测 npm 上 workbuddy-auth 0.2.9、qoder-auth 0.2.50 均在）。
- 修复（本版已内置）：magpie 本就有「国内镜像」开关（`settings.ChinaMirror` → npmmirror/jsDelivr）与 `settings.NPMRegistry`。App 在**启动服务前**写入 `$HOME/.config/magpie/settings.json`：`{"chinaMirror":true,"npmRegistry":"https://registry.npmmirror.com"}`（读取已有文件后合并）。此后 `/api/plugins/npm` 走 npmmirror → 版本可查到 → 按钮变为「安装」。
- 临时手动办法（不重装也可）：插件页右上角打开「国内镜像」开关，再等列表刷新，即可安装。

## 3.6 完全离线版（预置 Bun + 插件包，推荐）
真机上出现 `npm 没有响应: ... registry.npmmirror.com ... dial tcp: lookup ... [::1]:53 ... connection refused` —— 这是 **Go 纯解析器在 Android 上无法做 DNS**（CGO_ENABLED=0，Android 无 /etc/resolv.conf，回落到 127.0.0.1:53）。因此任何"运行时联网装插件/下 Bun"的路线都不可靠。改为**把 Bun 与插件一起预置进 APK**：

- **插件包**：构建机上用真实 CLI 执行 `magpie plugin add @magpie-community/opencode-workbuddy-auth` 与 `...opencode-qoder-auth`，产出 `plugins.json` + `plugins/`（node_modules 内两包，纯 JS 单文件 index.mjs，无原生依赖）。打进 APK 资产 `assets/seed.zip`（内容为 `.config/magpie/plugins.json` 与 `.config/magpie/plugins/...`）。
- **Bun 运行时**：`bun-linux-aarch64-android`（Bun 1.4.2，zip sha256 `a1c7e2983f1bb6…`，解出 ELF aarch64 /system/bin/linker64）。**作为原生库 `lib/arm64-v8a/libbun.so` 打包**——因为 Android 10+ 禁止在应用数据目录 exec 文件（W^X），而 `nativeLibraryDir` 是可执行的。
- **首启**：`MainActivity.seedOfflineOnce()` 把 seed.zip 解压到 `$HOME`（即 filesDir），并设 `MAGPIE_BUN=nativeLibraryDir/libbun.so`。magpie 读 `plugins.json` 即认为两插件已安装，`HasBun()` 因 `MAGPIE_BUN` 为真，完全不触发下载。
- **结果**：插件页不再显示「即将上线」，WorkBuddy/Qoder 直接呈现为已安装（可点登录）。**"完全离线"指安装与运行不再依赖联网下载**；登录各订阅、拉模型与发请求仍需设备能访问相应厂商（这些请求走插件里的 Bun，不是 Go）。
- 体积：APK 约 58MB（libmagpie 63MB + libbun 87MB，压缩后）。

## 4. 验收
- 源码层 S1–S4 全部 PASS（market.json 两条、plugin add 命令、bun android 映射、agent 已编译），详见 [PLUGIN-ACCEPTANCE.md](PLUGIN-ACCEPTANCE.md)。
- 本产物 APK 静态层：package `ai.usemagpie.android`、targetSdk 34、仅 arm64-v8a、v2+v3 签名、zipalign 通过；classes.dex 内含 `plugin`/`workbuddy`/`Qoder`/`.magpie_plugins_seeded_v1` 字符串（自动安装逻辑已编入）。
- 设备层 D1–D5（安装、`plugin list` 见两项、登录订阅、Agents 页出现、无崩溃）需真机，本机无设备，标 BLOCKED。

## 5. 产物
- 安装包：[magpie-android-arm64-v0.1.2431-plugins.apk](../dist/magpie-android-arm64-v0.1.2431-plugins.apk)（25.5MB，sha256 `4c2957df1e820fcc18f8003564d3fecd706b8d414ac37bc1b604861d50ebb17b`）
- 自动安装源码：[MainActivity.java](../app/src/ai/usemagpie/android/MainActivity.java)
