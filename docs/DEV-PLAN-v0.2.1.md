# Magpie for Android · v0.2.1 下一阶段开发方案

- **唯一事实来源**：[PARITY-REPORT-v0.2.0.md](./PARITY-REPORT-v0.2.0.md)
- **基线版本**：`v0.2.0`（`versionCode 4`，静态门禁 G1–G5 PASS，动态门禁 G6–G10 BLOCKED，放行判定 `NEEDS WORK`）
- **目标版本**：`v0.2.1`（`versionCode 5`，纯 Android 宿主侧缺口收口版）
- **编制日期**：2026-11-21

---

## 一、版本目标

**下一目标版本与范围判定依据（一句话）**：鉴于 v0.2.0 仅完成 Android 宿主骨架与静态门禁 G1–G5，核心底座 **FR-01/BUG-01**（Go `CGO_ENABLED=0` 致 DNS 全断）及其下游 5 项 FR 与真机动态门禁 G6–G10 均处阻断状态且验收结论为 `NEEDS WORK`，在环境阻断未解除前尚不具备冲击「可用」里程碑（如 `v0.3.0`）的条件，因此下一版本定为 **`v0.2.1`（`versionCode 5`，沿用既有正式 Release 签名支持从 v0.2.0 覆盖升级）**，范围聚焦于**不依赖 Go 源码/NDK/联网构建缓存的纯 Android 宿主侧缺口收口**，为后续解除阻断后冲刺 `v0.3.0` 扫清宿主障碍。

### 依赖关系与阻断链路盘点（全量回溯 FR-01…FR-24、BUG-01…BUG-08、G1…G10）

报告揭示了三类环境阻断源，决定了各项遗留任务“可先行”与“被连锁阻塞”的边界：

| 阻断源 | 报告事实依据 | 连锁阻塞的条目（**严禁排入本阶段可执行清单**） | 不受阻断、可先行的宿主侧范围 |
|---|---|---|---|
| **阻断源 A：无上游 Go 源码 + 未安装 NDK** | `go version -m` 实测 `libmagpie.so` 仍为 `CGO_ENABLED=0`，无法重编 `CGO=1`（+16KB 页对齐）且无法落地 M1 Go 补丁（§1.3、§2.1、§4.2、§4.3） | **直接阻断**：**FR-01 / BUG-01**（Go DNS）、**FR-06**（`store.go/github.go`）、**FR-09 遗留**（`bunupdate` 跳过）、**BUG-07 遗留**（Go 自替换禁写）、**FR-13 遗留**（Go `--lan` 0.0.0.0 监听）<br>**Go DNS 连锁阻断**：**FR-04**、**FR-05**、**FR-10**、**FR-20**<br>**Go 桥接/钩子接线阻断**：**FR-02/BUG-06 Go 侧**（`systemca/netproxy`）、**FR-03**（`AndroidHostBridge` 接线）、**FR-07 遗留**（Go `webHost`/Picker）、**FR-08 遗留**（上游镜像开关 UI）、**FR-14 遗留**（Go `/api/open` 与 OAuth 刷新）、**FR-15 遗留**（Go `backup.go`→`host/export-file`）、**FR-16 遗留**（Go `Copy()`）、**FR-19 遗留**（Go `quota/mask/turn` 告警钩子） | **FR-22**（纯宿主诊断页）、**FR-02 / BUG-06 宿主侧**（`NetworkMonitor` 代理/CA 监测）、**FR-23 宿主侧**（走 Android 系统网络栈，不受 Go DNS 影响）、**FR-21 宿主侧**（本地 HTTP 额度拉取）、**FR-17 / BUG-08 宿主侧**（WebView 安全区与 `android.css` 注入） |
| **阻断源 B：无 `adb` 与真机设备** | 本机无 `adb` 与真机（目标回归 Android 10/13/14），动态门禁与动态验收全数标 `BLOCKED`（§4.1、§4.2、§4.3） | **门禁**：**G6、G7**（同时叠加 Go DNS 风险）、**G8 运行时崩溃项、G9、G10**<br>**所有已静态通过/已修项的动态回归**：**FR-03、FR-08(BUG-04/05)、FR-11(BUG-03)、FR-12、FR-19、BUG-02**，以及上述宿主侧新代码在真机上的实机验证 | 本阶段延续 **G1–G5 静态门禁** + 代码/DEX/Manifest 静态核验，动态项明确留待真机就绪后验收 |
| **阻断源 C：离线环境无 AGP 8.x / AndroidX 缓存** | 仅具 `android-34/android.jar`，无 AGP 8.x 与 AndroidX 缓存（§1.2、§2.2 第 3 条、§3.3、§4.2） | **FR-24 遗留**（`./gradlew assembleRelease` 实跑复核）与 **AndroidX 迁移**（`core/webkit/browser/work`、`AndroidX FileProvider`） | 继续采用已验证通过 G1–G5 的 `aapt2/javac/d8/apksigner` 无 AndroidX 原生框架构建管线出包 |

---

## 二、优先级任务清单（P0/P1/P2）

> **准入原则**：以下清单**仅包含立即可做、不依赖任何被阻断底座的 Android 宿主侧任务**；所有代码均基于现有 Java Framework 原生 API 与 `aapt2/javac/d8/apksigner` 管线交付，不含任何 `⛔阻断` 项、Go 补丁、NDK 编译或真机动态门禁。

### P0（核心架构补洞与排障前置，最优先）

#### 1. 新增诊断中心 `DiagnosticsActivity` —— 对应 **FR-22**

- **报告状态回溯**：§1.3 `❌未实现`、§2.2 第 6 条明确列为宿主遗留、§4.2 `❌ FAIL（未实现）`（全规格唯一完全零落地的非阻断需求）。
- **排序依据**：既是唯一空白模块，也是后续接入真机与重编 Go CGO 内核时排查 G6–G10、DNS 与子进程异常的必备观测工具。
- **开发内容**：
  1. 新增基于原生 Framework API 的 `DiagnosticsActivity` 并在 `AndroidManifest.xml` 注册（支持从快捷方式/通知/菜单进入）。
  2. 实现本地诊断信息聚合：网关进程存活与退避状态、`libmagpie.so`/`libbun.so` 路径与存在性、CA PEM 导出状态（`CaBundleExporter`）、本地随机端口与 `AndroidHostBridge` 状态、运行日志查看。
  3. 实现本地日志与诊断报告 `zip` 打包导出（JDK 原生 `java.util.zip` + 系统文件创建/分享）。
  4. 预置对本地网关 `healthcheck` / `quota` / `plugin list` 的 loopback 查询面板（带 `.magpie_web_key`），并对网关未起或接口不可达提供降级展示。
  5. **信息缺口标注**：轻量 CLI 的具体命令集/触发方式，以及 `healthcheck`、`quota`、`plugin list` 的准确本地 HTTP 路径、端口与 JSON 字段结构——**报告未提供**（实施前须核对规格书 `docs/WINDOWS-PARITY-SPEC.md`，严禁臆造字段）。
- **验收标准（当前环境可验证）**：
  - `DiagnosticsActivity` 编译入 `classes.dex` 且 `aapt2 dump badging` / Manifest 解析确认组件与入口声明正确；
  - 静态审校确认具备日志读取、zip 打包导出、`libmagpie.so`/`libbun.so`/CA PEM 自检及本地接口离线降级逻辑，且无硬编码密钥或违背最小权限原则的冗余权限；
  - 产出 `v0.2.1` APK 通过 **G1–G5** 全部静态门禁（真机实测归入解除阻断源 B 后）。

#### 2. 补齐守护服务层 `NetworkMonitor`（系统代理与 CA 变更监测同步）—— 对应 **FR-02、BUG-06**（仅宿主侧）

- **报告状态回溯**：§1.3 `🟡部分`（`ConnectivityManager 代理热同步未实现`）、§1.4 `BUG-06 🟡部分`、§2.1 四层架构复核明确标注守护服务层 `NetworkMonitor(代理/CA 热同步) ❌ 缺`、§4.2 `补 ConnectivityManager NetworkCallback 代理热同步`。
- **排序依据**：补齐架构 §2.1 中守护服务层唯一标 `❌ 缺` 的组件，使 Android 宿主守护层结构闭环，并为后续 Go CGO 修复后验证代理与 CA 链铺平道路。
- **开发内容**：
  1. 在 `MagpieGatewayService` 守护层新增 `NetworkMonitor`，基于 `ConnectivityManager.registerDefaultNetworkCallback`（minSdk 24 原生支持）监听默认网络切换与 `LinkProperties.getHttpProxy()` 变化。
  2. 同步系统代理配置到子进程启动环境变量，并在网络/信任证书状态变更时按需触发 `CaBundleExporter` 重新导出 PEM，同时预留向运行中子进程传递变更信号的回调接口。
  3. **边界与信息缺口标注**：① Go 侧 `systemca`/`netproxy` 补丁及 `UNABLE_TO_VERIFY_LEAF_SIGNATURE` 消除校验属阻断源 A/B，**不包含在本任务中**；② `AndroidManifest.xml` 是否已声明 `ACCESS_NETWORK_STATE`（§1.2 仅列“……等”）及向运行中 Go 进程热推代理变更的具体 IPC 协议——**报告未提供**，须在实现前核对清单与规格书。
- **验收标准（当前环境可验证）**：
  - `NetworkMonitor` 已集成至 `MagpieGatewayService` 生命周期（随服务注册、随 `onDestroy` 注销无泄漏）；
  - 静态审校确认代理读取（含无代理/直连空值清理）与启动环境注入、`CaBundleExporter` 刷新逻辑完整，权限声明合规；
  - 产出 `v0.2.1` APK 通过 **G1–G5** 静态门禁。

---

### P1（宿主侧未竟核心闭环，紧随 P0 推进）

#### 3. 补齐独立 APK 更新的版本检查、下载与 SHA-256 校验 UI —— 对应 **FR-23、BUG-07**（仅宿主侧）

- **报告状态回溯**：§1.3 `🟡部分`（`缺版本检查/下载 UI`）、§1.4 `BUG-07 🟡部分`、§2.2 第 6 条（`ApkUpdater 缺版本检查 UI`）、§4.2 `补版本检查+下载+SHA256 校验 UI`。
- **排序依据**：v0.2.0 已切换正式 Release 签名（CN=Magpie Android Release），且 Android 宿主层网络请求走系统原生网络栈（bionic DNS），**不受 Go `CGO_ENABLED=0` DNS 断链影响**；补齐后即可支撑 `v0.2.1 → v0.3.0` 的安全自更新分发。
- **开发内容**：
  1. 扩展 `ApkUpdater`，实现远端版本元数据拉取、版本号（`versionCode`）比对、APK 下载进度展示、下载完成后的 SHA-256 完整性校验与校验失败阻断。
  2. 校验通过后衔接既有 `ApkFileProvider`（`content://` URI）拉起系统安装器，严格保持不写/不覆盖 `nativeLibraryDir`。
  3. 提供独立更新弹窗/面板 UI（使用 Framework 原生 API，不引入 AndroidX）。
  4. **边界与信息缺口标注**：① **BUG-07** 中的 Go 侧自替换禁写补丁（`update` 跳过）受阻于阻断源 A，**不包含在本任务中**；② 远端版本检查接口 URL、Release 元数据 JSON 结构及 SHA-256 校验清单格式——**报告未提供**（须先对齐发布规范，不可臆造接口地址与字段）。
- **验收标准（当前环境可验证）**：
  - 静态审校确认下载流→本地临时目录存储→`MessageDigest("SHA-256")` 比对→失败删除/拦截→成功移交 `ApkFileProvider` 拉起安装器的完整链路无绕过；
  - 确认代码中无向 `nativeLibraryDir` 写入 `.so` 的路径，无新增 AndroidX 依赖；
  - 产出 `v0.2.1` APK 通过 **G1–G5** 静态门禁（真机联网下载与安装器唤起归入阻断源 B 解除后）。

#### 4. 补齐桌面微件 `/api/usage/quotas` 额度拉取与降级展示 —— 对应 **FR-21**（仅宿主侧）

- **报告状态回溯**：§1.3 `🟡部分`（`微件仅展示网关状态/LAN，未拉 /api/usage/quotas 额度`）、§2.2 第 6 条（`Widget 未拉额度`）、§4.2 `微件补 /api/usage/quotas 拉取（WorkManager/HTTP+Key）`。
- **排序依据**：纯宿主侧数据拉取与 UI 渲染，复用 FR-03 已落地的 `.magpie_web_key`，不依赖 Go 源码或 NDK，完成后即可收口 FR-21 宿主端全部能力。
- **开发内容**：
  1. 在 `MagpieQuotaWidget` 补充后台异步请求逻辑：读取 `.magpie_web_key`，通过原生 `HttpURLConnection` 请求本地 `127.0.0.1` 的 `/api/usage/quotas`，解析后更新 `widget_quota.xml` 额度视图。
  2. 因当前环境缺失 AndroidX `WorkManager`（阻断源 C），本阶段采用原生 `AppWidgetProvider` 更新周期 + 手动刷新按钮（或原生 `JobScheduler`）实现调度，不引入 AndroidX 依赖。
  3. 明确三种降级态渲染：网关未启动、本地接口异常、Go DNS 断网导致的无远端额度数据（始终保留既有网关状态与 LAN 展示）。
  4. **信息缺口标注**：`/api/usage/quotas` 的具体本地端口、认证 Header 名称及 JSON 响应字段结构——**报告未提供**（实施前须核对规格书确认）。
- **验收标准（当前环境可验证）**：
  - 静态审校确认微件刷新不在主线程执行网络 IO、正确读取 `.magpie_web_key`、具备超时与三种降级态展示；
  - `aapt2` 资源编译与 `widget_quota.xml` / `widget_info` 绑定正常；
  - 产出 `v0.2.1` APK 通过 **G1–G5** 静态门禁（真机桌面添加与刷新归入阻断源 B 解除后）。

---

### P2（低风险前置适配，保留防回归兜底）

#### 5. 补齐 WebView 安全区 WindowInsets 桥接与 `android.css` 响应式注入 —— 对应 **FR-17、BUG-08**（仅宿主侧）

- **报告状态回溯**：§1.3 `🟡部分`（`未启用真正 Edge-to-Edge Insets 与 android.css 响应式注入（避免与固定系统栏冲突）`）、§1.4 `BUG-08 🟡部分`、§2.2 第 4 条、§4.2 `补 Edge-to-Edge Insets 与 android.css 响应式；真机 360dp 复测`。
- **排序依据**：代码完全属于 Android 宿主侧（`MainActivity`/`MagpieWebViewClient`/资源），不被 Go/NDK 阻断；但鉴于 §2.2 第 4 条明示“暂缓真正 Edge-to-Edge 以避免内容被刘海/导航条遮挡”且当前无真机做 360dp 视觉验证，故排于 P2 并要求自带安全兜底。
- **开发内容**：
  1. 在 `MainActivity` 使用 Framework 原生 `View.setOnApplyWindowInsetsListener` 捕获状态栏/导航栏/刘海安全区 Insets，转为 CSS 变量注入 WebView，并提供是否启用全沉浸 Edge-to-Edge 的安全开关（在真机 360dp 复测通过前保持防遮挡兜底）。
  2. 在 `MagpieWebViewClient` 页面加载完成时机按需注入本地内置 `android.css`（通过 `assets` 打包），优化窄屏布局与触控区域。
  3. **信息缺口标注**：`android.css` 需适配的上游页面具体 DOM 结构/选择器与 360dp 排版细则——**报告未提供**（实施前须对照上游页面或规格书确认，避免盲写无效规则）。
- **验收标准（当前环境可验证）**：
  - `assets` 中包含 `android.css`（APK 解包验证存在），`MagpieWebViewClient` 具备防重复注入逻辑；
  - `MainActivity` 正确处理 `WindowInsets` 并在亮/暗主题切换与 `onRenderProcessGone` 重建 WebView 后保持一致；
  - 产出 `v0.2.1` APK 通过 **G1–G5** 静态门禁（360dp 与刘海屏视觉验收归入阻断源 B 解除后）。

---

## 三、解除阻断的前置条件

以下任务**因环境/底座阻断无法在当前阶段启动**，必须先按三组前置条件逐一解除阻断，随后启动面向「可用」签发（如 `v0.3.0`）的联调与验收。

### 阻断源 A：Go 源码 + Android NDK（最关键底座阻断，决定产品能否「可用」）

- **解除阻断所需准备工作**：
  1. **获取上游 Go 源码**（报告未提供具体源码仓库 URL、分支/Tag 及目标 Go 工具链版本，需在启动前补齐）。
  2. **安装 Android NDK r26d 或 r27c**（§4.2 FR-01 明确指定版本范围），配置交叉编译工具链环境。
  3. 使用仓库既有脚本 `scripts/build-go-android.sh`（已预设 `CGO_ENABLED=1`、`max-page-size=16384`）完成首次基线编译，并用两项工具验证解除成功：
     - `go version -m libmagpie.so` 确认 `CGO_ENABLED=1`、`GOOS=android`、`GOARCH=arm64`；
     - `readelf` 确认动态依赖含 `NEEDED[libc.so]` 且段对齐满足 `0x4000`（16KB 对齐）。
- **解除后按依赖顺序启动的任务**：
  1. **底座修复（第 1 顺位）**：**FR-01 / BUG-01**（重编 `CGO=1` 的 `libmagpie.so` 替换旧上游件，消除 Go DNS 全断）。
  2. **M1 Go 侧补丁集（第 2 顺位，与宿主已完成组件接线）**：
     - `androidHost` 接线 → 激活宿主侧已空转就绪的 `AndroidHostBridge`：**FR-03**（桥接鉴权生效）、**FR-14**（Go `/api/open`→Custom Tabs）、**FR-16**（Go `Copy()`→原生剪贴板）、**FR-19**（Go `quota/mask/turn` 告警钩子推送通知）；
     - `systemca` / `netproxy` 补丁 → 闭合 **FR-02 / BUG-06** Go 侧证书与代理链路；
     - `github_tarball`（`store.go/github.go` 增 codeload tarball 路径）→ 落地 **FR-06**；
     - `bunupdate`（增加 android 跳过分支）→ 闭合 **FR-09**；
     - `update` 跳过（禁写自替换）→ 闭合 **BUG-07** Go 侧；
     - `webHost` Picker/ChooseFolder 及本地安装链 → 闭合 **FR-07**；
     - `backup.go` → `host/export-file`（与宿主侧 `ACTION_CREATE_DOCUMENT` 联调）→ 闭合 **FR-15**；
     - Go `--lan`（`0.0.0.0` 监听）→ 打通 **FR-13** 底层能力。
  3. **被 Go DNS 连锁阻塞的下游功能（第 3 顺位，待 FR-01 与 M1 补丁就绪后启动）**：
     - **FR-04**（插件市场在线加载，实测 8s 内返回真实版本号）；
     - **FR-05**（在线下载/安装/更新/卸载，实测安装 1 Provider + 1 中间件）；
     - **FR-10**（全扩展点中间件/Page/MCP 端到端验证，强依赖 FR-04/05）；
     - **FR-20**（AstrLink Guard 37MB 模型下载与本地脱敏验证，依赖 FR-01 + Go 推理）。

### 阻断源 B：`adb` 与多版本 Android 真机环境（决定能否签发「可用」）

- **解除阻断所需准备工作**：
  1. 安装并打通 `adb` 调试环境。
  2. 准备覆盖 **Android 10 / 13 / 14** 的 arm64-v8a 实机（§4.3 明确指定目标系统版本；报告未提供具体机型清单与设备来源）。
- **解除后启动的动态回归任务**：
  1. **动态门禁全量回归**：**G6**（`adb install -r`）、**G7**（冷启 ≤15s、无 `ERR_CONNECTION_REFUSED`、首屏非白屏，需在阻断源 A 修复后连网复测）、**G8**（无 FATAL 崩溃与权限实测）、**G9 / G10**（后台存活与持久化）。
  2. **静态通过项真机补验**：**FR-03**（重启密钥一致与 401）、**FR-08 / BUG-04 / BUG-05**（冷启预装可见且二次启动不覆盖）、**FR-11 / BUG-03**（退后台/滑卡 30min `/v1/models` 返 200）、**FR-12**（锁屏 WakeLock/WifiLock 与开机自启）、**BUG-02**（WebView 文件选择/多窗口/恢复）、**FR-19**（通知点击带 `?view` 跳转）。
  3. **v0.2.1 宿主新功能与全链路验收**：**FR-22** 诊断导出、**FR-02** 代理切换与 `UNABLE_TO_VERIFY_LEAF_SIGNATURE` 消失验证、**FR-23** 联网更新与安装器拉起、**FR-21** 微件刷新、**FR-17 / BUG-08** 360dp 屏幕与刘海屏复测、**FR-14** Google/Copilot OAuth 登录无 403、**FR-15** 导出可见且可恢复。

### 阻断源 C：联网 CI / Gradle + AndroidX 构建环境

- **解除阻断所需准备工作**：
  1. 准备具备外网访问（或已预置离线镜像仓库）的 CI/构建机，安装 **AGP 8.x**、**AndroidX 依赖**（`core/webkit/browser/work`、`FileProvider`）及 **NDK**（报告未提供 AGP 8.x 具体小版本及各 AndroidX 库版本号）。
  2. 安全注入正式 Release keystore 与 `keystore.properties`（保持不入库，签名指纹与 `9e93fa82e1952e818651a61f8d316c27cb6333d505f7670c2cbc4275c8af0bed` 一致）。
- **解除后启动的任务**：
  - **FR-24**（运行 `./gradlew assembleRelease` 产出与当前同等正式 Release 签名的 APK，并复核 v2+v3 签名与 `zipalign` 对齐）。

---

## 四、推迟项与理由

以下任务**明确不建议在当前 `v0.2.1` 阶段开展**，应推迟至前置底座或联网环境就绪后再实施：

| 推迟任务 | 对应 FR/BUG | 不建议现在做的具体理由（回溯报告依据） |
|---|---|---|
| **提前在宿主侧空写 `host/export-file` 与本地插件原生 Picker 桥接路由** | **FR-15、FR-07** | §2.2 第 5 条已明确现有 `AndroidHostBridge` 因 Go 侧 `androidHost` 为 no-op 而处于“空转待接线”状态；且报告未提供 `host/export-file` 与原生 Picker 的具体 IPC 参数契约。在无 Go 源码与 `backup.go`/`webHost` 协同实现前继续堆叠新桥接路由，既无法联调验收，又大概率因协议失配导致二次返工，应待阻断源 A 解除后与 Go 补丁同步开发。 |
| **局域网接入助手卡片（一键复制 / 二维码 / Termux 引导）** | **FR-13** | §1.3 与 §4.2 显示 Go 侧 `--lan`（`0.0.0.0` 监听）尚未实现，且当前 Go 内核无法出网（**BUG-01**）；另外报告未提供接入助手卡片应归属 Android 原生页面还是上游 Web UI，亦未提供预编译 `libmagpie.so` 是否已支持 `--lan` 参数。在底座既不能跨机监听又不能对外请求时先做接入卡片毫无可用价值且存在归属返工风险，应推迟至 Go `--lan` 与 FR-01 落地后实施。 |
| **强行补充「网关模式开关」与「镜像开关」的 Android 原生入口** | **FR-18、FR-08** | §1.3 明确指出“网关模式开关属上游 UI”“镜像开关 UI 属上游”，且报告未提供两项开关对应的本地配置键名、HTTP API 或路由路径。在未拿到上游源码与状态契约前另起原生入口，极易与上游配置状态割裂并违背分层职责，应推迟至可同步审阅/修改上游 UI 时统一决策。 |
| **将现有 Framework 原生实现迁移至 AndroidX（含 `WorkManager`、`AndroidX FileProvider`）及折腾离线 Gradle 构建** | **FR-24、FR-21、FR-23** | §2.2 第 3 条与 §3.3 明确当前环境无 AGP 8.x 与 AndroidX 离线缓存，而现有原生 Framework API + `aapt2/javac/d8/apksigner` 管线已真实可复现且全过 **G1–G5** 门禁，Gradle 骨架也已备好。此时强推 AndroidX 迁移只会破坏当前离线出包能力，应推迟至阻断源 C（联网 CI）就绪时一次性切换验证。 |
| **AstrLink Guard 任何前端/宿主侧预适配，以及多扩展点（中间件/Page/MCP）集成工作** | **FR-20、FR-10** | 报告 §1.3 与 §4.2 明确 **FR-20** 强依赖 Go DNS 下载 37MB 模型及 Go 推理，**FR-10** 强依赖 **FR-04/05**（进而受阻于 **FR-01**）。二者均位于阻断链最末端，即便后续解除阻断源 A，也应排在 FR-01 → M1 补丁 → FR-04/05 验证通过之后，当前阶段严禁提前投入。 |
