# Magpie for Android · v0.2.0 Windows 对齐实施与验收报告

> **假设声明（一句话）**：本次工作在工作区只存在「预编译上游 libmagpie.so(CGO_ENABLED=0) + 无 Go 源码 + 未安装 NDK」的现状下判定——凡需重编 Go 内核(NDK CGO)或真机动态回归的条目按「阻断/BLOCKED」如实登记，不得以静态推断冒充通过；一切结论以对 `dist/magpie-android-arm64-v0.2.0-offline.apk` 的实测为准。

- 规格依据：`docs/WINDOWS-PARITY-SPEC.md`（FR-01…FR-24、BUG-01…BUG-08、T01…T15）
- 基线产物：`dist/magpie-android-arm64-v0.1.2-offline.apk`（60,838,899 B，sha256 c3254c26…9fd8，debug 签名 CN=Android Debug，versionCode 3）
- 本版产物：`dist/magpie-android-arm64-v0.2.0-offline.apk`（60,859,584 B，sha256 385c2010899b9697326175826077cf7b6267e9c7aafe4822fce9ea462046b7c7，versionCode 4 / versionName 0.2.0，正式 Release 签名 CN=Magpie Android Release）

---

## 第一部分 · 开发实现

### 1.1 开发前盘点：规格中「尚未实现 / 实现不完整」的条目
对 v0.1.2 逐条比对规格第 2.4 节（8 项缺陷）与需求清单，确认以下未落地/半残条目为本次开发目标：
- 全链路阻断：BUG-01（Go DNS，CGO=0）——**依赖 Go 源码 + NDK，本环境不可改**。
- Android 宿主侧空心化：BUG-02/03/04/05/07/08（WebView 能力、无保活、首启竞态、配置覆写、硬编码密钥/自更新、暗色与深链）——**本次重点改造**。
- 插件在线/本地生命周期 FR-04/05/06/07/09/10：上联依赖 BUG-01，本次仅补宿主侧入口（SAF/文件选择）。
- WebView 原生桥接 FR-14/15/16/17/18：本次补 Custom Tabs / 文件选择 / 深链 / 剪贴板 / 主题。
- 后台与生态 FR-11/12/13/19/21：本次补前台服务、WakeLock、开机自启、通知频道、磁贴/快捷方式/微件。
- 工程与签名 FR-24：本次切正式 Release 签名并补 Gradle/NDK 骨架。

### 1.2 本版实际代码交付（均在 `app/` 内，Java 框架原生 API，无 AndroidX 依赖，可被 aapt2/javac/d8 直接构建）
| 文件 | 职责 | 对应规格 |
|---|---|---|
| MainActivity.java（重写） | 增强 WebView 宿主：多窗口、文件选择、外链拦截、深链、返回键、渲染进程恢复、系统栏 | BUG-02/08、FR-14/16/17 |
| MagpieWebViewClient.java | shouldOverrideUrlLoading（外链→Custom Tabs）、onRenderProcessGone 恢复 | BUG-02、FR-17 |
| MagpieWebChromeClient.java | onShowFileChooser（SAF 上传）、onCreateWindow（window.open→外链）、控制台日志 | BUG-02、FR-07/15 |
| MagpieGatewayService.java | dataSync 前台服务：有序初始化管线、随机密钥、同步 seed、首启镜像、进程监督退避重启、WakeLock、通知 | BUG-03/04/05/07、FR-11/12/13 |
| AndroidHostBridge.java | 127.0.0.1 随机端口 IPC + Bearer Token 校验，路由 open-url/copy/notify/wake-lock/theme/text-size/quit | FR-03、T05 |
| NotificationHelper.java | 5 个通知频道（service/quota/turn/mask/update）+ 常驻通知三 Action | FR-19 |
| CaBundleExporter.java | 聚合 AndroidCAStore 为 PEM，供 SSL_CERT_FILE/NODE_EXTRA_CA_CERTS 注入 | FR-02（部分） |
| MagpieTileService.java | Quick Settings 磁贴启停网关 | FR-21 |
| MagpieQuotaWidget.java + widget_quota.xml | 桌面微件（展示网关状态/LAN，额度需打开 App） | FR-21（部分） |
| BootReceiver.java | BOOT_COMPLETED 按设置自启前台服务 | FR-12 |
| ApkFileProvider.java + ApkUpdater.java | 独立 APK 安装通道（content:// 交系统安装器，绝不覆盖 nativeLibraryDir） | FR-23（部分） |
| AndroidManifest.xml（重写） | 权限（FOREGROUND_SERVICE_DATA_SYNC/POST_NOTIFICATIONS/BOOT_COMPLETED/WAKE_LOCK 等）、service/receiver/tile/widget/provider、magpie:// 深链、shortcuts | 多变 |
| res/（themes、values-night、xml/shortcuts、xml/widget_info、layout/widget_quota、strings） | 亮/暗主题、快捷方式、微件布局 | FR-17/21 |

### 1.3 规格覆盖对照表（FR-01…FR-24，逐条）
图例：✅通过(静态实测) · 🟡部分实现 · ❌未实现 · ⛔阻断(依赖 Go源码/NDK/真机)

| ID | 需求 | 本版状态 | 实现/证据落点 | 说明与遗留 |
|---|---|---|---|---|
| FR-01 | NDK CGO 修复 Go DNS | ⛔阻断 | `go version -m out/libmagpie.so` → CGO_ENABLED=0, GOOS=android,GOARCH=arm64 | 工作区无 Go 源码、无 NDK，无法重编 libmagpie.so；DNS 仍全断（BUG-01 未除）。已交付 `scripts/build-go-android.sh`（CGO=1、max-page-size=16384）作为落地脚本 |
| FR-02 | CA 链与系统代理同步 | 🟡部分 | CaBundleExporter 导出 AndroidCAStore→PEM；Service 注入 SSL_CERT_FILE/NODE_EXTRA_CA_CERTS | CA 导出/注入已实现；ConnectivityManager 代理热同步未实现；CGO=0 下 Go 侧仍无法验证 |
| FR-03 | 本地服务鉴权加固 | ✅通过(静态) | SecureRandom 32hex 持久化 .magpie_web_key(0600)，dex 无 `magpie-android-local-key`；Bridge Bearer Token 无 token 返 401 | ①dex 无固定密钥：实测 True；②重启持久化：代码属实；③Token 401：已实现。真机未测 |
| FR-04 | 插件市场在线加载 | ⛔阻断 | 依赖 Go DNS（未改） | 未预装插件仍将显示「即将上线」 |
| FR-05 | 在线下载/安装/更新/卸载 | ⛔阻断 | 依赖 Go DNS + Bun | 同上，宿主侧无法绕过 |
| FR-06 | 无 git 的 GitHub Tarball 安装 | ⛔阻断 | Go `store.go/github.go` 未打补丁 | 需 Go 源码侧实现 |
| FR-07 | 本地插件文件/压缩包导入 | 🟡部分 | onShowFileChooser→ACTION_GET_CONTENT 已实现；原生「从本地安装」Picker 依赖 Go webHost | 文件选择器可用；导入到 $HOME+POST /api/plugins/add 的上游链依赖 Go 补丁 |
| FR-08 | 镜像 + 首启解压竞态 | ✅通过(静态) | seedSync 在 startProcess 前同步执行；firstTimeMirror 仅 settings.json 不存在时初始化，绝不覆盖 | 冷启竞态/覆写逻辑已修；镜像开关 UI 属上游 |
| FR-09 | Bun W^X 托管 | 🟡部分 | env MAGPIE_BUN=nativeLibraryDir/libbun.so 已锁；bunupdate 跳过需 Go 补丁 | 宿主侧锁定已做；Go 侧下载拦截未改 |
| FR-10 | 全扩展点与参数配置 | ⛔阻断 | 依赖 FR-04/05 在线下载 + Go/Bun | 中间件/Page/MCP 端到端需底座 |
| FR-11 | 前台服务守护进程 | ✅通过(静态) | manifest service foregroundServiceType=dataSync(0x1)；通知三 Action；supervisor 1/3/10/30s 退避；Activity onDestroy 不停服务 | 真机后台/杀进程存活未测 |
| FR-12 | 保持唤醒 + 开机自启 | ✅通过(静态) | WAKE_LOCK+WifiLock；BootReceiver(BOOT_COMPLETED)；REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 设备层锁屏/重启未测 |
| FR-13 | 局域网共享/浏览器/客户端接入 | 🟡部分 | detectLanIp 探测并在通知展示；--lan 绑定与客户端一键复制/二维码未接 | Go 侧 0.0.0.0 监听与接入助手卡片未做 |
| FR-14 | Custom Tabs OAuth 闭环 | 🟡部分 | WebView 外链/window.open→ACTION_VIEW(Custom Tabs extras)已实现 | Go `/api/open`(OpenURL) 为 no-op，订阅登录发起的跳转需 Go 补丁 |
| FR-15 | 备份/CSV/证书 SAF 桥接 | 🟡部分 | onShowFileChooser 支持上传；导出走 ACTION_CREATE_DOCUMENT 未接 Go export-file | 导入选择可用；导出与分享闭环依赖 Go backup.go 改造 |
| FR-16 | 原生剪贴板 + magpie:// 深链 | 🟡部分 | manifest scheme=magpie(实测)+onNewIntent；ClipboardManager+/host/copy 已实现 | 页面复制按钮走 Go Copy()（no-op），需 Go 补丁；深链已注册 |
| FR-17 | 视觉/主题/Edge-to-Edge/响应式 | 🟡部分 | 亮暗主题资源+applySystemBars+setTextZoom+onRenderProcessGone 已实现 | 未启用真正 Edge-to-Edge Insets 与 android.css 响应式注入（避免与固定系统栏冲突） |
| FR-18 | 网关模式控制 + 返回键 | 🟡部分 | 返回键先派发 Escape→goBack→moveTaskToBack(不杀服务) | 网关模式开关属上游 UI，未加原生入口 |
| FR-19 | 系统通知体系 | ✅通过(静态) | 5 频道创建 + POST_NOTIFICATIONS 申请 + 点击带 ?view | Go 侧告警钩子(quota/mask/turn)未打补丁，端到端触发阻断 |
| FR-20 | AstrLink Guard 本地脱敏 | ⛔阻断 | 依赖 Go DNS 下载 37MB 模型 + Go 推理 | 底座未通 |
| FR-21 | 快捷入口矩阵(磁贴/快捷方式/微件) | 🟡部分 | TileService(QS_TILE)、shortcuts.xml(4 项)、Widget 已实现 | 微件仅展示网关状态/LAN，未拉 /api/usage/quotas 额度 |
| FR-22 | 诊断中心与轻量 CLI | ❌未实现 | 未新增 DiagnosticsActivity | 需后续单独实现日志查看/导出/healthcheck |
| FR-23 | 独立 APK 更新机制 | 🟡部分 | ApkUpdater+ApkFileProvider 以 content:// 拉系统安装器，不覆盖 .so | 缺版本检查/下载 UI；用自定义 provider 替代 AndroidX FileProvider |
| FR-24 | Gradle + 正式 Release 签名 | 🟡部分 | 已切正式 Release keystore：v2+v3 通过、CN=Magpie Android Release、zipalign 通过、单 ABI、含 seed.zip；已补 Gradle 骨架与 build-go-android.sh | 实际出包用等效 aapt2/javac/d8/apksigner 管线（Gradle assembleRelease 因无 AGP/AndroidX 离线缓存未执行） |

### 1.4 缺陷（BUG-01…08）修复对照
| 编号 | 结论 | 依据 |
|---|---|---|
| BUG-01 Go DNS | ⛔未修复 | 交付 .so 仍 CGO_ENABLED=0（go version -m 实测）；需 NDK 重编 |
| BUG-02 WebView 空心化 | ✅已修复 | 新增 WebChromeClient/WebViewClient(onShowFileChooser/onCreateWindow/onRenderProcessGone/外链拦截) |
| BUG-03 无后台保活 | ✅已修复 | MagpieGatewayService(dataSync)+进程监督+通知 |
| BUG-04 首启解压竞态 | ✅已修复 | seedSync 同步前置 |
| BUG-05 每次覆盖配置 | ✅已修复 | firstTimeMirror 仅首启 |
| BUG-06 CA/代理断链 | 🟡部分 | CA 导出+注入已做；代理同步未做 |
| BUG-07 安全/自更新 | 🟡部分 | 随机密钥已替换硬编码(实测 dex 无旧 key)；自更新改 APK 通道(ApkUpdater)，Go 侧自替换禁写需补丁 |
| BUG-08 界面/模式适配 | 🟡部分 | 暗色系统栏+深链已做；Edge-to-Edge/响应式未全 |

---

## 第二部分 · 架构复核（软件架构师角色）

### 2.1 与规格 §5.1 四层架构对照
- **Android UI/入口层**：MainActivity（WebView+Custom Tabs+SAF）、Tile、Widget、Deep Link、通知——✅ 均落地为本版 Java 组件。
- **Android 守护服务层**：MagpieGatewayService(dataSync+WakeLock+BootReceiver)、AndroidHostBridge(127.0.0.1 随机端口+Bearer Token)、ProcessSupervisor(退避重启)——✅ 已实现；NetworkMonitor(代理/CA 热同步) ❌ 缺。
- **Native 载荷层**：libmagpie.so 仍为 CGO=0 上游件（与 WINDOWS-PARITY-SPEC §2.1/§5.2 要求的 CGO=1+16KB 对齐**不符**）——⛔ 关键偏差，因环境无源码/NDK 无法消除。libbun.so 保持动态链接 bionic（符合）。
- **存储层**：HOME→filesDir、TMPDIR→cacheDir、CA→cache/.cache/magpie——✅。

### 2.2 发现的偏差与处置（发现即修正 / 记录遗留）
1. **【已修正】** CA bundle 已导出但未注入子进程：复核发现 MagpieGatewayService 未把 PEM 路径写入 env。→ 已在 startProcess 增加 SSL_CERT_FILE/NODE_EXTRA_CA_CERTS，并重新构建 v0.2.0（本报告产物即修正后重编）。
2. **【已修正】** 硬编码密钥（BUG-07）：复核要求密钥不可在 DEX。→ 改为 SecureRandom 32hex 持久化，实测 dex 无旧串。
3. **【记录遗留】** 采用框架原生 API 而非规格点名的 AndroidX(core/webkit/browser/work)：为在「无 AGP/AndroidX 离线缓存、仅有 android.jar」的环境下可真实构建，改用 framework Intent(Custom Tabs)/View insets/TileService 等等效实现；Gradle 骨架已按 AndroidX 声明，联网 CI 可直接切换。
4. **【记录遗留】** 未启用真正 Edge-to-Edge（保留系统栏并着色以避免内容被刘海/导航条遮挡）；响应式 android.css 未注入。属 FR-17 未竟项。
5. **【记录遗留】** AndroidHostBridge 已就绪但 Go 侧不回调（androidHost 为上游 no-op）：桥接处于「空转待接线」状态，待 FR-01/FR-03 的 Go 补丁落地即生效。
6. **【记录遗留】** Widget 未拉额度、DiagnosticsActivity 未实现、ApkUpdater 缺版本检查 UI。

### 2.3 架构结论
本版**结构性达成**规格 §5.1 的 UI/服务/桥接骨架，Android 宿主侧与规格方向一致、无返工级偏离；**底座核心（Go CGO/DNS）与部分深度联动因缺少 Go 源码与 NDK 无法在本质检环境闭合**，属环境阻断而非设计偏离，已在 §1.3 逐条标注。

---

## 第三部分 · 发布说明（移动发布工程师角色）

### 3.1 产物
- 文件：`dist/magpie-android-arm64-v0.2.0-offline.apk`
- 大小：60,859,584 B（约 58.0 MiB）
- SHA-256：`385c2010899b9697326175826077cf7b6267e9c7aafe4822fce9ea462046b7c7`
- 版本：versionCode 4 / versionName 0.2.0；minSdk 24（Android 7.0）/ targetSdk 34
- 单 ABI：arm64-v8a（native-code 实测仅 'arm64-v8a'）；内含 libmagpie.so(63,111,462)+libbun.so(86,800,440)+assets/seed.zip(135,488)

### 3.2 签名（正式 Release，非 debug）
- 方案：APK Signature Scheme v2 = true、v3 = true（apksigner verify --verbose 实测）；zipalign -c -v 4 = Verification succesful。
- 证书：CN=Magpie Android Release, OU=Android, O=Magpie Project, C=CN；证书 SHA-256 `9e93fa82e1952e818651a61f8d316c27cb6333d505f7670c2cbc4275c8af0bed`（RSA 2048）。
- 密钥管理：keystore 文件与口令置于 `keystore.properties`（已 gitignore，不入库）。

### 3.3 构建流程（本机可复现的等效管线）
1. aapt2 compile/link（android-34/android.jar，生成 R.java + base.apk，含 assets）；
2. javac(JDK11, -source/-target 8, bootclasspath android.jar) 编译 14 个源文件；
3. jar→d8(--min-api 24) 出 classes.dex；
4. python zipfile 将 classes.dex + lib/arm64-v8a/{libmagpie,libbun}.so 并入；
5. zipalign -f -p 4；6) apksigner 正式 Release 签名；7) 静态校验（sha256/apksigner/zipalign/badging/ELF）。
> 标准 Gradle+NDK 工程骨架（settings/build/app/build.gradle.kts、gradle.properties、scripts/build-go-android.sh）已提供；`./gradlew assembleRelease` 需在具备 AGP 8.x + AndroidX 依赖 + NDK 的联网环境执行。

### 3.4 旧版用户迁移指引（debug→正式签名不可覆盖安装）
1. 在 v0.1.2 内导出加密 `.magpie-backup`（或手动记录 Provider/Key）；
2. 卸载 v0.1.2（签名不兼容会报 INSTALL_FAILED_UPDATE_INCOMPATIBLE）；
3. 安装 v0.2.0；
4. 导入备份恢复。

### 3.5 已知限制
- Go 内核仍 CGO=0：**在线插件市场、API Key 直连、models.dev、OAuth 刷新、云同步、联网搜索等所有 Go 侧出网仍不可用**（与 v0.1.2 相同，需后续 NDK 重编）。
- 发布签名由 debug 切正式：与 v0.1.2 不能覆盖升级（见 3.4）。

---

## 第四部分 · 验收测试报告（验收/测试工程师角色）

### 4.1 静态门禁（G1–G5，针对 v0.2.0 APK 实测）
| 关卡 | 检查 | 结果 | 证据 |
|---|---|---|---|
| G1 包结构 | aapt2 badging 可解析、含 package/versionCode/versionName/minSdk/targetSdk/launchable | ✅ PASS | versionCode='4' versionName='0.2.0'，min24/target34，launchable MainActivity |
| G2 签名 | v2 或 v3 通过、可打印证书指纹 | ✅ PASS | v2=true v3=true；CN=Magpie Android Release；cert sha256 9e93fa82… |
| G3 对齐 | zipalign -c -v 4 通过 | ✅ PASS | Verification succesful |
| G4 仅 arm64 | native-code 仅 arm64-v8a、ELF AArch64、go buildinfo GOOS=android/GOARCH=arm64 | ✅ PASS(布局) / ⚠ | native-code 'arm64-v8a'；ELF machine=0xb7 ET_DYN；但 CGO_ENABLED=0（见 FR-01） |
| G5 可执行布局 | .so 位于 lib/arm64-v8a 并从 nativeLibraryDir 执行（非 assets 直 exec） | ✅ PASS(静态) | APK 内 lib/arm64-v8a/{libmagpie,libbun}.so；manifest extractNativeLibs=true |
| G6 可安装 | adb install -r Success | ⛔ BLOCKED | 本机无 adb/无设备 |
| G7 首启达服务 | 冷启≤15s 有进程/无 ERR_CONNECTION_REFUSED/首屏非白屏 | ⛔ BLOCKED | 无设备；且 Go DNS 未修，联网态风险高 |
| G8 崩溃/权限 | 无 FATAL、最小权限 | 🟡 静态部分通过 | 权限集与规格一致；崩溃需真机 |
| G9/G10 后台/持久 | 加分项 | ⛔ BLOCKED | 无设备 |

### 4.2 逐条 FR 验收（方法 / 结果 / 遗留）
> 方法说明：静态类用 aapt2 / apksigner / zipalign / python-zipfile / go version -m / dex 字符串扫描实测；动态类因无 adb 与真机一律 BLOCKED，不冒充 PASS。

| ID | 测试方法 | 结果 | 遗留问题 / 后续处理建议 |
|---|---|---|---|
| FR-01 | go version -m 读 libmagpie.so 构建属性 | ❌ FAIL（阻断） | 安装 NDK r26d/r27c + 取上游源码，用 build-go-android.sh 重编 CGO=1 并 readelf 校验 NEEDED[libc.so]/0x4000 对齐后复测 |
| FR-02 | 审 CaBundleExporter+service env 注入 | 🟡 PARTIAL | 补 ConnectivityManager NetworkCallback 代理热同步；Go CGO 修复后验证 UNABLE_TO_VERIFY_LEAF_SIGNATURE 消失 |
| FR-03 | dex 扫描旧 key + 审 SecureRandom 持久化 + 审 Bridge 401 | ✅ PASS(静态) | 真机验证重启密钥一致与 401 |
| FR-04 | 审依赖链（Go DNS） | ⛔ BLOCKED | 随 FR-01 修复后于插件页实测 8s 内真实版本号 |
| FR-05 | 审依赖链 | ⛔ BLOCKED | 同上，实测安装 1 Provider + 1 中间件 |
| FR-06 | 审 Go 补丁缺失 | ⛔ BLOCKED | 在 store.go 增 codeload tarball 下载路径 |
| FR-07 | 审 onShowFileChooser 流程 | 🟡 PARTIAL | 需 Go Picker/ChooseFolder 解锁原生「从本地安装」按钮，SAF 上传已可用 |
| FR-08 | 审 seedSync 顺序 + firstTimeMirror 条件 | ✅ PASS(静态) | 真机冷启验证预装可见且二次启动不覆盖 |
| FR-09 | 审 MAGPIE_BUN 锁定 | 🟡 PARTIAL | Go bunupdate 增加 android 跳过分支 |
| FR-10 | 审扩展点依赖 | ⛔ BLOCKED | 随 FR-04/05 修复后端到端验证 |
| FR-11 | manifest foregroundServiceType + 审监督逻辑 | ✅ PASS(静态) | 真机退后台/滑卡 30min 复测 /v1/models 200 |
| FR-12 | manifest BOOT_COMPLETED + 审 WakeLock | ✅ PASS(静态) | 真机锁屏/重启验证 |
| FR-13 | 审 detectLanIp + 通知 LAN 展示 | 🟡 PARTIAL | 补 Go --lan 与接入助手卡片（复制/二维码/Termux） |
| FR-14 | 审 WebView 外链拦截实现 | 🟡 PARTIAL | Go OpenURL 接线后实测 Google/Copilot 登录无 403 |
| FR-15 | 审 onShowFileChooser/导出路径 | 🟡 PARTIAL | 补 Go backup.go→host/export-file，实测导出可见+可恢复 |
| FR-16 | manifest scheme=magpie(实测) + 审 Clipboard | 🟡 PARTIAL | Go Copy() 接线后页面复制 100% 生效；深链已注册 |
| FR-17 | 审主题/系统栏/textZoom/render gone | 🟡 PARTIAL | 补 Edge-to-Edge Insets 与 android.css 响应式；真机 360dp 复测 |
| FR-18 | 审返回键处理 | 🟡 PARTIAL | 网关模式开关未加原生入口 |
| FR-19 | 审 5 频道 + POST_NOTIFICATIONS | ✅ PASS(静态) | Go 告警钩子接线后实测弹通知并跳转 |
| FR-20 | 审依赖（Go DNS/AstrLink） | ⛔ BLOCKED | 随 FR-01 修复后下载 37MB 模型验证脱敏 |
| FR-21 | manifest QS_TILE/APPWIDGET_UPDATE + shortcuts(实测) | 🟡 PARTIAL | 微件补 /api/usage/quotas 拉取（WorkManager/HTTP+Key） |
| FR-22 | 审是否有 DiagnosticsActivity | ❌ FAIL（未实现） | 新增 DiagnosticsActivity：日志查看/导出 zip/healthcheck/quota/plugin list |
| FR-23 | 审 ApkUpdater/ApkFileProvider | 🟡 PARTIAL | 补版本检查+下载+SHA256 校验 UI |
| FR-24 | apksigner/zipalign/badging/签名指纹实测 | 🟡 PARTIAL（签名达标，Gradle 未执行） | 联网环境跑 ./gradlew assembleRelease 出同签名包并做 v2+v3/对齐复核 |

### 4.3 验收结论
- **静态门禁 G1–G5：全部 PASS**（本环境可证）；G6–G10 与所有动态项：**BLOCKED（无 adb/真机，BLOCKED≠PASS）**。
- **规格符合度判断**：Android 宿主/服务/桥接骨架与签名发布达标；**核心 FR-01（DNS）及依赖它的 FR-04/05/06/10/20 仍为阻断级不通过**，FR-02/07/09/13/14/15/16/17/18/21/23/24 为部分实现，FR-22 未实现。
- **放行判定：NEEDS WORK（不予「可用」签发）**。不通过条目均已在 4.2 给出原因与后续处理建议，无一条默认通过。
- **达成「可用」的必要下一步**：① 接入 NDK + 上游源码，按 build-go-android.sh 重编 CGO=1 的 libmagpie.so 并 readelf 验证；② 落地 M1 Go 补丁（androidHost/systemca/netproxy/github_tarball/bunupdate/update 跳过）；③ 真机 Android 10/13/14 全量动态回归 G6–G10 与 FR 全链路。
