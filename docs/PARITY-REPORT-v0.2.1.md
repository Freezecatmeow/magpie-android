# Magpie for Android · v0.2.1 四角色实施与验收报告

- 规格来源：docs/DEV-PLAN-v0.2.1.md（唯一事实来源 PARITY-REPORT-v0.2.0.md）
- 基线：v0.2.0（versionCode 4）｜目标：v0.2.1（versionCode 5）
- 本版产物：dist/magpie-android-arm64-v0.2.1-offline.apk ｜ 60,871,936 B ｜ sha256 5a02b26ae47902ed8eb09ba22143b8dd1df3cce87d82bb6ba4ad07b91f79517b
- 范围：仅 DEV-PLAN §二 列出的 5 项宿主侧任务；§三(阻断)/§四(推迟) 逐条登记为「阻断」并给出依赖缺失条件，不实施。

---

## 一、移动应用开发工程师（实现 5 项宿主侧任务）

|| 问题ID | 处理方式 | 实测证据 | 最终状态 |
|---|---|---|---|---|
| FR-22 | 新增 DiagnosticsActivity：聚合网关/端口/Bridge/LAN/代理/CA/libmagpie.so/libbun.so/app.log 状态；app.log 查看；healthcheck/quota/plugin list loopback 查询面板(带 Bearer key，网关未起/不可达降级)；java.util.zip 打包导出 + ACTION_CREATE_DOCUMENT 落盘；Manifest 注册 + Diagnostics 快捷方式入口 | aapt2 xmltree：activities=3、含 .DiagnosticsActivity；dex 含 DiagnosticsActivity/diagnostics.txt/magpie-diag-；资产解包验证（python）T1=True | 已解决（静态；真机导出/分享归阻断源B） |
| FR-02 / BUG-06（宿主侧） | 新增 NetworkMonitor：ConnectivityManager.registerDefaultNetworkCallback 监听默认网络+LinkProperties.getHttpProxy()；变更写 MagpieAppState.proxyHost/Port 并重导出 CA；Service 启动子进程注入 HTTP(S)_PROXY/NO_PROXY；initOnce 注册、shutdown 注销 | dex 含 NetworkMonitor + LinkProperties + HTTP_PROXY；badging 权限含 ACCESS_NETWORK_STATE；Service 生命周期注册/注销代码在案 | 已解决（宿主侧；Go systemca/netproxy 补丁仍阻断源A） |
| FR-23 / BUG-07（宿主侧） | 扩展 ApkUpdater：versionCode 比对(拉 metadata JSON)→下载→MessageDigest(SHA-256) 校验(不符删除拦截)→ApkFileProvider(content://)+系统安装器；新增 UpdateActivity 面板(检查/下载进度/安装)；绝不写 nativeLibraryDir | dex 含 SHA-256 + downloadAndInstall + ApkFileProvider + example.invalid(占位元数据地址)；grep：nativeLibraryDir 出现 3 处均为读取/注释，无写入；ApkUpdater 仅写 cache/updates | 已解决（静态；真机联网下载/安装器归阻断源B） |
| FR-21（宿主侧） | MagpieQuotaWidget：非主线程 HttpURLConnection 请求 /api/usage/quotas(读 .magpie_web_key 作 Bearer)，tolerant JSON 解析；三降级态(网关未启动/接口不可达/格式未知)；widget_refresh 按钮广播刷新；AppWidgetProvider 周期调度 | dex 含 /api/usage/quotas；aapt2 resources 含 widget_refresh；布局含 Button widget_refresh；网络 IO 在 new Thread | 已解决（静态；真机桌面刷新归阻断源B） |
| FR-17 / BUG-08（宿主侧） | MainActivity 新增 setOnApplyWindowInsetsListener 捕获状态栏/导航/IME 安全区→注入 CSS 变量 --magpie-safe-*；MagpieWebViewClient.onPageFinished 按需单次注入 assets/android.css(去重)；recoverWebView 后重置重注 | 资产 assets/android.css 存在(878B)；dex 含 magpie-android-css + --magpie-safe-top；onPageFinished 回调在案 | 已解决（静态；360dp/刘海视觉回归归阻断源B） |

产物级证据（G1–G5 静态门禁，见第三节）：versionCode='5' versionName='0.2.1'；native-code 'arm64-v8a'；v2+v3 签名通过；zipalign 通过；ELF magic 7f454c46 / machine 0xb7 / type 0x3。

---

## 二、软件架构师（对照 §5.1 四层架构复核）

**守护服务层闭合**：v0.2.0 唯一标 ❌ 的 NetworkMonitor 已补入 MagpieGatewayService（随 initOnce 注册、shutdown 注销，无泄漏）——守护层结构闭环。
**新组件分层归属正确**：DiagnosticsActivity/UpdateActivity 属 UI/入口层（framework 原生，无 AndroidX）；ApkUpdater/ApkFileProvider 属桥接/交付层（content:// 不碰 nativeLibraryDir）；Widget 属系统入口层（原生 AppWidgetProvider + 后台线程）。
**复核发现并确认的偏差（均为 DEV-PLAN 已标注的信息缺口，处理为安全降级、未臆造）**：
1. FR-21 额度 JSON 字段未提供 → 采 tolerant 解析（quotas/data/percent/remainingPercent），失败显示「格式未知（降级）」，不伪造数据。
2. FR-23 版本元数据 URL/JSON 结构未提供 → 置占位常量 example.invalid，链路完整但不指向真实地址，注释标注缺口。
3. FR-22 CLI 路径/字段未提供 → loopback 面板尝试 /api/* 并在不可达时降级，不硬编码字段。
**架构结论**：5 项宿主任务与规格四层架构一致、无返工级偏离；涉及 Go 侧接线（systemca/netproxy、androidHost、--lan、告警钩子）与 AndroidX 迁移仍属阻断源，已列入第四节阻断清单，不在本版实施。

---

## 三、移动发布工程师（沿用既有管线）

1) aapt2 compile/link（android-34/android.jar，含 assets/android.css 与 seed.zip）→ 2) javac(JDK11,-source/-target 8) 17 源文件 → 3) jar → 4) d8(--min-api24, classes.dex=66,408B) → 5) python 并入 dex+libmagpie.so+libbun.so → 6) zipalign -f -p 4 → 7) 正式 Release keystore 签名(v2+v3) → 8) 校验。
- 产物：dist/magpie-android-arm64-v0.2.1-offline.apk｜60,871,936 B｜sha256 5a02b26ae47902ed8eb09ba22143b8dd1df3cce87d82bb6ba4ad07b91f79517b
- 签名：v2=true v3=true；证书 CN=Magpie Android Release，SHA-256 9e93fa82e1952e818651a61f8d316c27cb6333d505f7670c2cbc4275c8af0bed（与 v0.2.0 同一正式证书，支持覆盖升级）
- 对齐：zipalign -c -v 4 = Verification succesful
- 内容：lib/arm64-v8a/libmagpie.so、lib/arm64-v8a/libbun.so、assets/seed.zip、assets/android.css 均在包内

---

## 四、验收测试工程师（逐项：方法/结果/遗留）

### 4.1 本轮可验证项（5 项，静态 G1–G5 + 代码/DEX/Manifest 核验）
|| 问题ID | 测试方法 | 结果 | 遗留 |
|---|---|---|---|---|
| FR-22 | aapt2 dump xmltree 查 .DiagnosticsActivity；python 扫 dex 含 DiagnosticsActivity/diagnostics.txt/magpie-diag- | ✅ PASS(静态) | 真机诊断导出/分享 → 阻断源B |
| FR-02/BUG-06 | 查权限 ACCESS_NETWORK_STATE；dex 扫 NetworkMonitor/LinkProperties/HTTP_PROXY；审 Service 注册/注销 | ✅ PASS(静态) | 代理切换后 Go 请求生效验证 → 阻断源A(Go systemca/netproxy) |
| FR-23/BUG-07 | dex 扫 SHA-256/downloadAndInstall/ApkFileProvider；grep nativeLibraryDir 仅读；审 cache/updates 写入 | ✅ PASS(静态) | 真机联网下载+SHA 校验+安装器 → 阻断源B |
| FR-21 | dex 扫 /api/usage/quotas；aapt2 resources 含 widget_refresh；审非主线程 IO+三降级 | ✅ PASS(静态) | 真机桌面添加/刷新 → 阻断源B |
| FR-17/BUG-08 | 解包确认 assets/android.css；dex 扫 magpie-android-css/--magpie-safe-top；审 onPageFinished 去重 | ✅ PASS(静态) | 360dp/刘海视觉 → 阻断源B |

### 4.2 阻断/推迟项清单（逐条：状态 + 一句话依赖缺失）
> 均为 DEV-PLAN §三/§四 明示不可在本阶段执行项，状态一律 BLOCKED/DEFFERED（BLOCKED≠PASS）。

| 条目 | 状态 | 一句话缺失条件 |
|---|---|---|
| FR-01 / BUG-01 Go DNS | 阻断 | 缺上游 Go 源码与 Android NDK，无法重编 CGO_ENABLED=1 的 libmagpie.so |
| FR-06 github tarball | 阻断 | 缺 Go 源码，无法改 store.go/github.go |
| FR-09 遗留 bunupdate 跳过 | 阻断 | 缺 Go 源码，无法加 android 跳过分支 |
| BUG-07 遗留 Go 自替换禁写 | 阻断 | 缺 Go 源码，无法改 internal/gui/update.go |
| FR-13 遗留 --lan 监听 | 阻断 | 缺 Go 源码，无法实现 0.0.0.0 监听 |
| FR-04 / FR-05 / FR-10 / FR-20 | 阻断 | 连锁依赖 FR-01 Go DNS 修复 |
| FR-02/BUG-06 Go 侧 / FR-03 接线 / FR-07 遗留 / FR-08 遗留 / FR-14 遗留 / FR-15 遗留 / FR-16 遗留 / FR-19 遗留 | 阻断 | 均需 Go androidHost/systemca/netproxy/webHost/backup/Copy 等 M1 补丁接线 |
| G6 / G7 / G8运行时 / G9 / G10 | 阻断 | 缺 adb 与 Android 10/13/14 真机 |
| 已静态通过项真机补验(FR-03/FR-08/FR-11/FR-12/FR-19/BUG-02) | 阻断 | 缺真机，仅能静态确认 |
| FR-24 遗留 / AndroidX 迁移 | 阻断 | 缺 AGP 8.x + AndroidX 离线缓存，无法跑 ./gradlew assembleRelease |
| §四推迟：FR-15/FR-07 预写 export-file/Picker、FR-13 接入助手卡片、FR-18/FR-08 原生开关、AndroidX 迁移、FR-20/FR-10 前置集成 | 推迟 | 依赖 Go 底座或上游 UI 契约，按 DEV-PLAN §四明示推迟 |

### 4.3 结论
- 本轮 5 项宿主侧任务：**全部 PASS（静态，G1–G5 通过 + DEX/Manifest/资源核验）**。
- 动态门禁 G6–G10、Go 侧与 AndroidX 迁移：**BLOCKED / 推迟**，每项已附一句话缺失条件，无一条冒充通过。
- 放行判定：v0.2.1 为**宿主侧缺口收口版**，静态验收通过；产品整体仍 NEEDS WORK，直至阻断源 A/B/C 解除。

---

# v0.3.0 · Go 侧补齐（源码在手后的续作）

> 触发：本机已备上游 Go 源码（mzsrc/github.com/yetone/magpie@v0.1.2431）并成功安装 Android NDK r27。以下各项以对 dist/magpie-android-arm64-v0.3.0-offline.apk 及产物的实测为准。
> 产物：v0.3.0（versionCode 6）｜60,830,976 B｜sha256 e2b34e2b508c83f1f3ec1b156970a3e180b2496634fa94286c92e52b4faa182c｜v2+v3｜CN=Magpie Android Release。

## A. 已完成事项（附验证方式）
| 项 | 处理 | 验证方式（实测） | 状态 |
|---|---|---|---|
| FR-01 / BUG-01 Go DNS | 用 NDK r27 aarch64-linux-android24-clang 以 CGO_ENABLED=1 重编 libmagpie.so（-extldflags=-Wl,-z,max-page-size=16384），替换 APK 内旧 CGO=0 件 | go version -m → CGO_ENABLED=1/GOOS=android/GOARCH=arm64；llvm-readelf -d → NEEDED [liblog.so,libdl.so,libc.so]；-l → 4× LOAD 对齐 0x4000；包内同件复测一致 | 已解决（构建级）；真机 Wi-Fi/4G 切换联网复测归阻断源B |
| M1 androidHost 桥接（FR-03/14/16/07/15） | 新增 internal/gui/android_host.go（build android）实现 gui.Windows：OpenURL→/host/open-url(Custom Tabs)、Copy→/host/copy、ChooseFolder→/host/pick-file、OpenFolder→/host/export-file、TintTitleBar→/host/theme、SetTextSize→/host/text-size、Quit→/host/quit，均带 Bearer Token；新增 internal/gui/host_default.go（build !android）；internal/gui/web.go 的 StartWeb 改用 defaultHost(quit) | go build -tags nogui ./internal/gui（android）通过；全量 android 构建通过；二进制含 /host/open-url 与 android bridge not configured 串 | 已解决（编译级）；真机 OAuth/复制/选择器实跑归阻断源B |
| BUG-07 Go 自替换禁写 | internal/gui/update.go place() 顶部加 if goos==android { u.stuck=android; return }（exeStuck 对只读 dir 已返回 not-writable 双保险） | 编译通过 | 已解决（构建级） |
| FR-09 bunupdate 跳过 | 上游 internal/plugin/bunupdate.go:198 CheckBun 在 MAGPIE_BUN 非空时直接 return（不下载）；本 App Shell 已设 MAGPIE_BUN | 源码核验(line109/198)+已编入产物 | 已满足（无需改码） |
| FR-13 --lan 监听 | 上游 internal/cli/web_cli.go:50-52 的 --lan 已把 addr 改为 0.0.0.0:<port> | 源码核验 | 已满足（无需改码；宿主透出开关即可） |
| FR-02 / BUG-06 CA 与代理 | 上游 internal/netproxy/netproxy.go For()→fromEnv 读 *_PROXY；internal/plugin/systemca.go caEnv 在 SSL_CERT_FILE/NODE_EXTRA_CA_CERTS 已设时照用；v0.2.0 起 App Shell 已注入这两类 env | 源码核验+Service 注入代码在案 | 已满足（环境注入链路已通；真机 MITM 复测归阻断源B） |

## B. 仍阻断 / 未完成事项与确切原因
| 项 | 状态 | 确切原因 / 后续 |
|---|---|---|
| FR-06 无 git 的 GitHub Tarball 安装 | 未完成 | 本轮未改 store.go/github.go 的纯 HTTP tarball 下载路径；需在 internal/plugin 增 codeload 下载+解包注册（未验证不列入完成） |
| FR-19 通知告警钩子(quota/mask/turn→/host/notify) | 部分 | androidHost 未在告警发射点调用 /host/notify；宿主桥路由与 5 频道已就绪，Go 触发未接线 |
| FR-04/05/10/20 | 阻断 | 底座 DNS 已构建级修复，但需真机+真实厂商网络实测（无 adb/真机） |
| G6–G10 动态门禁、FR-03/08/11/12/19 等真机补验 | 阻断 | 缺 adb 与 Android 10/13/14 真机 |
| FR-24 Gradle/AndroidX 流水线 | 阻断 | 缺 AGP 8.x + AndroidX 离线缓存，仍用 aapt2/javac/d8/apksigner 管线 |
