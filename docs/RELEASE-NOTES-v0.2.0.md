# v0.2.0 · magpie for Android（unofficial port）

## 下载
- APK：`magpie-android-arm64-v0.2.0-offline.apk`（60,859,584 B，sha256 `385c2010899b9697326175826077cf7b6267e9c7aafe4822fce9ea462046b7c7`）
- 平台：Android 7.0+（minSdk 24），arm64-v8a 单 ABI；完全离线（内置 Bun + WorkBuddy/Qoder 插件）
- 签名：正式 Release 证书 CN=Magpie Android Release（v2+v3），**与 v0.1.2 的 debug 签名不兼容，无法覆盖安装**

## 相对 v0.1.2 的变化（Android 宿主侧）
- 前台 dataSync 服务常驻保活 + 进程崩溃退避重启（BUG-03）
- 首启解压改为同步前置、仅首次初始化国内镜像、不再覆盖用户配置（BUG-04/05）
- 移除硬编码密钥，改 SecureRandom 32hex 持久化；新增本地 Host 桥接 Bearer Token（BUG-07 安全部分）
- WebView 增强：Custom Tabs 外链、SAF 文件选择、window.open 拦截、渲染进程恢复、返回键优先关弹窗（BUG-02/08 部分）
- 新增 Quick Settings 磁贴、4 个 App Shortcuts、桌面状态微件、开机自启、5 类通知频道、magpie:// 深链、APK 独立安装通道

## 已知限制（重要）
- **Go 内核仍为 CGO_ENABLED=0 预编译件（本构建机无 Go 源码/NDK，无法重编）**：插件在线市场/下载、API Key 直连、models.dev、OAuth 刷新、云同步、联网搜索等所有 Go 侧出网**仍不可用**，与 v0.1.2 相同。详见 docs/PARITY-REPORT-v0.2.0.md §4。
- 需 NDK + 上游源码按 scripts/build-go-android.sh 重编后，上述能力方可恢复。

## 迁移（从 v0.1.2）
1) 旧版导出加密 .magpie-backup → 2) 卸载 v0.1.2 → 3) 安装 v0.2.0 → 4) 导入恢复。
