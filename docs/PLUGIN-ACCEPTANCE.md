# magpie Android · WorkBuddy / Qoder 插件移植验收标准

| 项 | 值 |
|---|---|
| 文档路径 | `E:/deepseek work/magpie-android/docs/PLUGIN-ACCEPTANCE.md` |
| 归属 | 本文件由「插件移植验收」角色维护，只写本路径；不改动 `build/`、`dist/`，不执行最终出包 |
| 验收对象 | 已开发的 magpie Android APK（`ai.usemagpie.android`）集成社区 OpenCode 插件 WorkBuddy 与 Qoder 后的产物 |
| 源码基线 | `E:/deepseek work/mzsrc/github.com/yetone/magpie@v0.1.2431`（Go，v0.1.2431） |
| Android 工程 | `E:/deepseek work/magpie-android/`（`app/` Java WebView 外壳；`scripts/build-apk2.sh` 手工出包） |
| 上一版产物（回归基线） | `dist/magpie-android-arm64-v0.1.2431.apk`（25,526,747 B；SHA-256 `4602e15b…cda3f`） |
| 版本 / 日期 | v1 / 2026-11-01 |
| 结论口径 | **默认 NEEDS WORK**；仅当源码层 S1–S4 全 PASS、APK 层 A1–A4 全 PASS、设备层 D1–D5 全 PASS，才可判「已移植可用」 |

---

## 0. 结论摘要（先看这一屏）

| 层 | 关卡 | 当前状态 | 依据 |
|---|---|---|---|
| 源码层 | S1 market.json 有 workbuddy / qoder 两条 | **PASS（已取证）** | 见 §2.1 |
| 源码层 | S2 `plugin add` 命令存在且支持本地路径 | **PASS（已取证）** | 见 §2.2 |
| 源码层 | S3 bun 支持 android/arm64 映射 | **PASS（已取证）** | 见 §2.3 |
| 源码层 | S4 Agent workbuddy / qoder 已编译进二进制 | **PASS（已取证）** | 见 §2.4 |
| APK 层 | A1 新产物存在且可解析 | **待产物**（现仅有上一版基线） | 见 §3.1 |
| APK 层 | A2 产物含插件安装代码（Web UI Plugins 页 + API） | **PASS（源码/二进制已证）；新产物需复核** | 见 §3.2 |
| APK 层 | A3 包结构不回归 | **基线已测**；新产物需复核 | 见 §3.3 |
| APK 层 | A4 签名不回归 | **基线已测**；新产物需复核 | 见 §3.4 |
| 设备层 | D1 安装 / 首启 | **BLOCKED（无设备）** | 见 §4 |
| 设备层 | D2 `plugin list` 见 workbuddy 与 qoder | **BLOCKED（无设备）** | 见 §4 |
| 设备层 | D3 登录对应订阅 | **BLOCKED（无设备 + 需真实账号）** | 见 §4 |
| 设备层 | D4 Agents 页出现 WorkBuddy / Qoder | **BLOCKED（无设备）** | 见 §4 |
| 设备层 | D5 运行无崩溃 / 无权限异常 | **BLOCKED（无设备）** | 见 §4 |

> **关键事实澄清（勿臆造）**：本机为 Windows 构建机，**无 adb、无 Android 设备/模拟器**（历史核查已确认，见 §1.4）。因此**所有设备层关卡一律标 BLOCKED，BLOCKED ≠ PASS**。源码层与 APK 层可静态取证；设备层必须真机执行后才可判 PASS。

---

## 1. 验收口径

### 1.1 「WorkBuddy/Qoder 已移植可用」的定义

在 Android APK 里，用户能够：①通过 **Plugins 页 / `magpie plugin add`** 安装社区插件 `@magpie-community/opencode-workbuddy-auth`（providers：`workbuddy`、`workbuddy-ai`，replaces `workbuddy`）与 `@magpie-community/opencode-qoder-auth`（provider：`qoder`，replaces `qoder`）；②安装后 `plugin list`（或 `GET /api/plugins`）能看到二者；③能完成对应订阅的**设备登录/授权**；④Agents 页出现 `WorkBuddy` / `Qoder` 行。

### 1.2 三层验收

- **源码层**：上游源码本身是否具备上述能力（与是否出包无关）。
- **APK 层**：交付的 APK 是否把上述能力带进包内，且包结构/签名不回退。
- **设备层**：真机上首启后功能是否真的可用。

### 1.3 判定符号

| 符号 | 含义 |
|---|---|
| PASS | 命令输出符合期望，证据留档 |
| FAIL | 命令输出不符合期望，需修复 |
| BLOCKED | 依赖本机不具备的条件（设备/真实账号/网络），未执行，**不得记为 PASS** |
| WARN | 非阻断项，记录风险 |

### 1.4 环境与基线（本次实测，2026-11-01）

- 工具链：Android SDK 仅 `build-tools/34.0.0` + `platforms/android-34`（位于 `magpie-android/sdk`）；JDK 11（javac/keytool）、JDK（apksigner 用）。`adb` **缺失**，无设备。
- 上一版 APK 基线（`dist/magpie-android-arm64-v0.1.2431.apk`）：
  - `package ai.usemagpie.android`，`versionCode=1`，`versionName=0.1.0`，`minSdk=24`，`targetSdk=34`
  - 权限 `android.permission.INTERNET`；launchable-activity `ai.usemagpie.android.MainActivity`
  - `native-code: 'arm64-v8a'`；包内 `lib/arm64-v8a/libmagpie.so`（63,111,462 B）+ `classes.dex`
  - 签名：v2=true、v3=true；证书 `CN=Android Debug, O=Android, C=US`，SHA-256 `f923d36ccf1b43104d601030d54540ae5ecb668eaa8a880b4ac43a20bb18f921`
  - `zipalign -c -v 4` → `Verification succesful`
  - SHA-256：`4602e15bb07bca9cf3cc5139177c826c21865fe1a00641a1ee1fc203515cda3f`

> 说明：上表是**回归比较基准**。新产物的对应字段应与基线一致（除二进制内容与哈希可因重编而变化）。

---

## 2. 源码层验收（S1–S5）

### 2.1 S1 · market.json 含 WorkBuddy 与 Qoder 两条

- **命令**
```bash
cd "E:/deepseek work/mzsrc/github.com/yetone/magpie@v0.1.2431"
grep -n -iE "workbuddy|qoder" internal/plugin/market.json
```
- **期望**（PASS）
  - 存在 `@magpie-community/opencode-qoder-auth`，`providers:["qoder"]`，`replaces:"qoder"`
  - 存在 `@magpie-community/opencode-workbuddy-auth`，`providers:["workbuddy","workbuddy-ai"]`，`replaces:"workbuddy"`
  - 两条均带 `"community": true`
- **失败判据**：任一包缺失 / provider 或 replaces 不符 / 非 community。
- **本次实测（PASS）**：market.json 第 8–9 行为 Qoder 条目，第 26–27 行为 WorkBuddy 条目，字段完全匹配（原文见 §7.1）。

### 2.2 S2 · `magpie plugin add` 命令存在，且支持本地路径

- **命令**
```bash
grep -n 'case "add", "install"' internal/cli/plugins_cli.go
grep -n 'provider.AddPlugin' internal/cli/plugins_cli.go
grep -n 'func IsPath' internal/plugin/store.go
# 运行期实测（用同 tags 的 host 构建）
cd "E:/deepseek work/magpie-android/out"
HOME=/tmp/pchome USERPROFILE=/tmp/pchome ./magpie-host.exe plugin add   # 无参数 → 打印用法
```
- **期望**（PASS）
  - `plugins_cli.go` 的 `add|install` 分支调用 `provider.AddPlugin`；
  - 用法串包含 `magpie plugin add <name | npm | git | path> install a community plugin by its name (workbuddy, think-tags, Qoder) … ./my-plugin.js …`；
  - `IsPath()` 识别 `file://`、`.`、`/`、`\\`、绝对路径、盘符路径（即支持 `magpie plugin add <本地路径>`）。
- **失败判据**：无 add/install 分支；用法串不含 path；IsPath 不识别本地路径。
- **本次实测（PASS）**：`plugins_cli.go:53` 有 `case "add", "install"`，`:60` 调 `provider.AddPlugin`；`store.go:324` `func IsPath`；host 构建 `plugin add` 打印完整用法，含 `workbuddy`、`Qoder`、`./my-plugin.js`（原文见 §7.2）。

### 2.3 S3 · bun 支持 android/arm64 映射（可在手机跑插件运行时）

- **命令**
```bash
grep -n -iE "android|aarch64|bun-linux" internal/plugin/bun.go
```
- **期望**（PASS）
  - `bunTargetFor()` 的 `case "android":` 产出 `bun-linux-aarch64-android`（arm64）、`bun-linux-x64-android-baseline`（amd64）；
  - 校验和表含 `bun-linux-aarch64-android.zip`。
- **失败判据**：无 android 分支；映射名不符；校验和缺失。
- **本次实测（PASS）**：`bun.go:97-102` 为 `case "android": t := "bun-linux-"+arch+"-android"`（arch 映射 arm64→aarch64）；`bun.go:50` 校验和含 `bun-linux-aarch64-android.zip`（原文见 §7.3）。

### 2.4 S4 · Agent 侧 workbuddy / qoder 已编译进二进制

- **命令**
```bash
grep -n -iE "workbuddy|qoder" internal/agent/agents.go
ls -la internal/agent/workbuddy.go internal/agent/qoder.go
# 运行期实测：同 tags 的 host 构建打印 agent 列表
cd "E:/deepseek work/magpie-android/out"
HOME=/tmp/pchome USERPROFILE=/tmp/pchome ./magpie-host.exe agents | grep -iE "workbuddy|qoder"
```
- **期望**（PASS）
  - `agents.go` 注册 `qoder(home)`、`qoderCN(home)`、`workbuddy(home)`、`workbuddyAI(home)`；
  - 运行期 agents 列表出现 `Qoder`、`WorkBuddy`（及 `Qoder CN`、`WorkBuddy AI`）。
- **失败判据**：注册缺失 / 运行期列表无对应行。
- **本次实测（PASS）**：`agents.go:265-266,270-271` 见注册；host 构建 `magpie agents` 输出含 `Qoder`、`WorkBuddy`（状态 `not detected`，属桌面 App 未安装的正常显示，**不等于缺失**，见 §6.3）（原文见 §7.4）。

### 2.5 S5 ·（支撑项）插件名解析到社区包

- **命令**
```bash
cd "E:/deepseek work/magpie-android/out"
HOME=/tmp/pchome USERPROFILE=/tmp/pchome MAGPIE_BUN=/nonexistent/bun ./magpie-host.exe plugin add workbuddy 2>&1 | head
HOME=/tmp/pchome USERPROFILE=/tmp/pchome MAGPIE_BUN=/nonexistent/bun ./magpie-host.exe plugin add Qoder 2>&1 | head
```
- **期望**（PASS）：`workbuddy` → `@magpie-community/opencode-workbuddy-auth`；`Qoder` → `@magpie-community/opencode-qoder-auth`（随后因 bun 安装失败报错属预期，不构成本项失败）。
- **失败判据**：解析成别的包或报「名称不存在」。
- **本次实测（PASS）**：解析结果见 §7.5（workbuddy→`…workbuddy-auth@latest`；Qoder→`…qoder-auth@latest`）。

---

## 3. APK 层验收（A1–A6）

> 前提：新产物由 Lead 统一出包。下列命令中 `APK="<新产物路径>"`，`BT="E:/deepseek work/magpie-android/sdk/build-tools/34.0.0"`。所有证据 `tee` 到 `docs/evidence/`。

### 3.1 A1 · 新产物存在且可解析

- **命令**
```bash
APK="E:/deepseek work/magpie-android/dist/magpie-android-arm64-<新版本>.apk"
"$BT/aapt2.exe" dump badging "$APK" | head -20 | tee docs/evidence/PA_A1_badging.txt
```
- **期望**：命令退出 0，输出含 `package: name=…`、`sdkVersion`、`targetSdkVersion`、`launchable-activity`。
- **失败判据**：文件不存在 / aapt2 解析报错 / 无 launchable-activity。
- **状态**：**待产物**（现仅上一版基线，见 §1.4）。

### 3.2 A2 · 产物含插件安装代码（可安装 WorkBuddy/Qoder）

插件安装在 APK 中有两条可达通道，二者任一成立即算「有插件安装代码」：

**A2a · Web UI Plugins 页与 API（二进制内自带）**
- **命令**
```bash
# 二进制内嵌插件 UI 与 API（源证 + 串证）
grep -rn "plugins.js" "E:/deepseek work/mzsrc/github.com/yetone/magpie@v0.1.2431/internal/gui/assets/" >/dev/null && echo "assets/plugins.js 存在"
grep -n 'POST /api/plugins/{op}' "E:/deepseek work/mzsrc/github.com/yetone/magpie@v0.1.2431/internal/gui/plugins.go"
grep -n 'addPlugin.*= provider.AddPlugin' "E:/deepseek work/mzsrc/github.com/yetone/magpie@v0.1.2431/internal/gui/plugins.go"
# 交付二进制里确有插件安装串
for s in "plugin add" "opencode-workbuddy-auth" "opencode-qoder-auth" "POST /api/plugins"; do
  printf "%-28s " "$s"; grep -a -c -- "$s" "E:/deepseek work/magpie-android/out/libmagpie.so"
done
```
- **期望**：`plugins.js` 存在；`GET /api/plugins`（已装列表）、`GET /api/plugins/listings`（市场）、`POST /api/plugins/{op}`（add/remove/update）注册；`addPlugin = provider.AddPlugin`；二进制命中相应字符串且计数 > 0。
- **失败判据**：assets 无 plugins.js / 无 plugins API / 二进制无安装串。
- **本次实测（PASS）**：源码与二进制均命中（见 §7.6）。因 Web UI 由 `//go:embed` 打进二进制，APK 只要装了该二进制即可用 Plugins 页安装插件。

**A2b · CLI 通道（`magpie plugin add`）**
- **说明**：CLI 在同一二进制内（源码层 S2 已证）。APK 壳未内置终端，故手机端 CLI 需 adb 直调二进制（见 D2）或用 Web UI。这属于「可用但非 UI 入口」，记录为支撑项。

### 3.3 A3 · 包结构不回归

- **命令**
```bash
APK="<新产物>"
"$BT/aapt2.exe" dump badging "$APK" | grep -E "package:|sdkVersion|targetSdkVersion|launchable-activity|native-code" | tee docs/evidence/PA_A3_badging.txt
unzip -l "$APK" | grep -E "classes.dex|lib/arm64-v8a/libmagpie.so|META-INF" | tee docs/evidence/PA_A3_zip.txt
```
- **期望**（与基线 §1.4 对比，字段一致）
  - package `ai.usemagpie.android`；minSdk 24；targetSdk 34；launchable-activity `…MainActivity`；`native-code: 'arm64-v8a'`（不得出现 armeabi-v7a/x86/x86_64）；
  - 包内有 `classes.dex` 与 `lib/arm64-v8a/libmagpie.so`。
  - `versionCode/versionName` 允许升级（记为 WARN 而非 FAIL，但须在发布说明中注明）。
- **失败判据**：package 改变 / minSdk/targetSdk 回退 / 多 ABI / 缺 classes.dex 或 libmagpie.so / 缺 launchable-activity。

### 3.4 A4 · 签名不回归

- **命令**
```bash
java -jar "$BT/lib/apksigner.jar" verify --verbose --print-certs "$APK" | tee docs/evidence/PA_A4_sign.txt
"$BT/zipalign.exe" -c -v 4 "$APK" | tail -1 | tee docs/evidence/PA_A4_align.txt
```
- **期望**
  - `Verifies`；`v2 scheme: true` 且 `v3 scheme: true`（至少其一为 true，与基线保持一致）；
  - 证书 SHA-256 与基线 `f923d36c…b18f921` **一致**（debug 证书签名；若改用 release 证书，需显式在发布说明登记新指纹，记为 WARN）；
  - zipalign 输出 `Verification succesful`。
- **失败判据**：`DOES NOT VERIFY` / v2 与 v3 全 false / zipalign 失败 / 证书指纹无说明地改变。

### 3.5 A5 ·（支撑项）二进制确为 android/arm64 且含插件与 agent 串

- **命令**
```bash
unzip -p "$APK" lib/arm64-v8a/libmagpie.so > /tmp/libmagpie.so
head -c 20 /tmp/libmagpie.so | xxd | head -2   # e_machine 应为 0xB7 (AArch64)
for s in "opencode-workbuddy-auth" "opencode-qoder-auth" "workbuddy" "qoder" "plugin add"; do
  printf "%-28s " "$s"; grep -a -c -- "$s" /tmp/libmagpie.so
done
```
- **期望**：ELF 头 `02 01 01 … 03 00 b7 00`（AArch64）；各串计数 > 0。
- **失败判据**：非 AArch64 / 关键串计数为 0。

### 3.6 A6 ·（若声明「首启即可见」则必查）插件预置

> 若新产物声称「**首启不装即在 `plugin list` 看到 workbuddy/qoder**」，则必须在包内/首启逻辑里预置插件。否则首启列表为空是**正常**的（需先 `plugin add`）。

- **命令**
```bash
# 静态：包内是否预置 plugins.json / node_modules / bun
unzip -l "$APK" | grep -iE "plugins.json|node_modules|bun" || echo "包内无预置（首启为空属正常）"
# 动态：见 D2（安装后再 list）
```
- **期望**：若声称首启可见 → 包内或首启脚本含 workbuddy/qoder 的 `plugins.json` 条目 + 已装包目录；否则 WARN「需手动安装」。
- **失败判据**：声称首启可见但包内无任何预置，且首启列表为空。

---

## 4. 设备层验收（D1–D5）—— **全部 BLOCKED（本机无设备）**

> 前置：一台 `arm64` Android 8.0+ 真机（或 arm64 模拟器），开启 USB 调试，`adb devices` 可见；设备可访问外网（插件运行时需下载 Bun 与 npm 包，见 §6.1）。**当前构建机无 adb/设备，以下均未执行 → BLOCKED。**

### 4.0 变量
```bash
PKG=ai.usemagpie.android
APK="<新产物>"
KEY="<MainActivity.java 中编译进包的 MAGPIE_WEB_KEY 实际值>"
```

### 4.1 D1 · 安装与首启 —— BLOCKED
- **命令**
```bash
adb install -r "$APK"
adb shell pm path $PKG                       # 期望：package:/data/app/.../base.apk
adb shell am start -n $PKG/.MainActivity
sleep 12
adb shell pidof $PKG                         # 期望：有 PID
adb logcat -d | grep -iE "Magpie|ERR_CLEARTEXT|ERR_CONNECTION|FATAL" | tail -20
```
- **期望**：install 输出 `Success`；`pm path` 有值；进程存在；logcat 出现 `magpie started from …/libmagpie.so`，无 `ERR_CLEARTEXT_NOT_PERMITTED` / `ERR_CONNECTION_REFUSED` / `FATAL EXCEPTION`。
- **失败判据**：安装失败（签名/ABI 不符）／进程起不来／WebView 报明文或连接被拒／崩溃。

### 4.2 D2 · 首启后能看到 workbuddy 与 qoder —— BLOCKED
两条通道，**任一 PASS 即算证据充分**（建议都做）。

**D2a · HTTP 通道（无需终端，推荐）**
- **命令**
```bash
adb forward tcp:3430 tcp:3430
curl -s -c /tmp/jar "http://127.0.0.1:3430/?k=$KEY" -o /dev/null          # 取 Cookie（?k= 登录）
# 市场清单（应列出两条社区插件）
curl -s -b /tmp/jar "http://127.0.0.1:3430/api/plugins/listings" | tee /tmp/listings.json
grep -oiE "opencode-workbuddy-auth|opencode-qoder-auth" /tmp/listings.json | sort -u
# 已安装清单（等价于 magpie plugin list）
curl -s -b /tmp/jar "http://127.0.0.1:3430/api/plugins" | tee /tmp/installed.json
grep -oiE "workbuddy|qoder" /tmp/installed.json | sort -u
# 若未安装，按文档安装方式用 API 安装（也验证了安装代码可用）
curl -s -b /tmp/jar -X POST -H 'Content-Type: application/json' -d '{"Spec":"workbuddy"}' "http://127.0.0.1:3430/api/plugins/add"
curl -s -b /tmp/jar -X POST -H 'Content-Type: application/json' -d '{"Spec":"Qoder"}'     "http://127.0.0.1:3430/api/plugins/add"
```
- **期望**：`/api/plugins/listings` 含两个包；`/api/plugins`（安装后）含 `workbuddy` 与 `qoder` 两个 spec，且 `error` 字段为空。
- **失败判据**：listings 不含 / 安装返回错误 / 安装后 list 仍为空 / 首次安装卡在下载 Bun（见 §6.1）。
- **说明**：若产物为「首启即可见」模式（A6），此步安装前 `/api/plugins` 即应含两条；否则先安装再验 list（与任务给定的 `magpie plugin add` 安装方式一致）。

**D2b · CLI 通道（需 adb shell 可执行二进制）**
- **命令**
```bash
LIB=$(adb shell pm path $PKG | sed 's#base.apk#lib/arm64/libmagpie.so#' | tr -d '\r')
adb shell "HOME=/data/data/$PKG/files TMPDIR=/data/data/$PKG/cache $LIB plugin list"
adb shell "HOME=/data/data/$PKG/files TMPDIR=/data/data/$PKG/cache $LIB plugin add workbuddy"
adb shell "HOME=/data/data/$PKG/files TMPDIR=/data/data/$PKG/cache $LIB plugin add Qoder"
adb shell "HOME=/data/data/$PKG/files TMPDIR=/data/data/$PKG/cache $LIB plugin list"
```
- **期望**：最终 `plugin list` 输出含 `@magpie-community/opencode-workbuddy-auth` 与 `@magpie-community/opencode-qoder-auth`。
- **失败判据**：`plugin add` 报 Bun 下载/启动失败；list 无对应项。
- **注意**：APK 未声明 `android:debuggable`，`run-as` 不可用；若 shell 域执行 `lib*/libmagpie.so` 被 SELinux 拒绝，**以 D2a 为准**。

### 4.3 D3 · 能登录对应订阅 —— BLOCKED（还需真实账号）
- **命令（Web UI 路径）**
```bash
# 安装后，通过 Web UI Plugins 页点对应插件的 Sign in；或走 signin API：
curl -s -b /tmp/jar -X POST -H 'Content-Type: application/json'      -d '{"Provider":"workbuddy"}' "http://127.0.0.1:3430/api/plugin-signin"
curl -s -b /tmp/jar -X POST -H 'Content-Type: application/json'      -d '{"Provider":"qoder"}'     "http://127.0.0.1:3430/api/plugin-signin"
```
  或 CLI：`adb shell "… $LIB plugin login workbuddy"` / `… plugin login qoder`
- **期望**：返回设备登录 URL / 验证码 / 授权提示；按提示完成授权后，`plugin list` 对应条目显示已登录，且 `magpie models` 能列出该订阅模型。
- **失败判据**：signin 无提示 / 授权回调失败 / 登录后仍无模型。
- **前提**：需 **WorkBuddy（CodeBuddy 套餐）与 Qoder 有效订阅账号**，本机不具备 → BLOCKED。

### 4.4 D4 · Agents 页出现 WorkBuddy / Qoder —— BLOCKED
- **命令**
```bash
# Web UI：打开首页 → Agents 页，确认存在 WorkBuddy / Qoder 行
adb exec-out screencap -p > docs/evidence/PA_D4_agents.png
# 或用 API 佐证（agent 列表）
curl -s -b /tmp/jar "http://127.0.0.1:3430/api/plugins/agents" | grep -oiE "workbuddy|qoder" | sort -u
```
- **期望**：Agents 页出现 `WorkBuddy`（含 `WorkBuddy AI`）与 `Qoder`（含 `Qoder CN`）行。
- **失败判据**：Agents 页无对应行。
- **重要澄清**：这些行来自编译进二进制的 agent 适配器（S4 已证）。在 Android 上不会「检测到」桌面版 WorkBuddy/Qoder App，故行内状态显示 `not detected` 是**预期正常**（见 §6.3）。**PASS 判据是「行存在」，不是「已连接」。**

### 4.5 D5 · 运行无崩溃 / 无权限异常 —— BLOCKED
- **命令**
```bash
adb logcat -d | grep -iE "FATAL EXCEPTION|AndroidRuntime|avc: denied.*magpie" | tail -30
adb shell dumpsys package $PKG | grep -A20 "requested permissions"   # 期望仅 INTERNET（及必要的最小集）
adb shell am force-stop $PKG; adb shell am start -n $PKG/.MainActivity; sleep 10; adb shell pidof $PKG
```
- **期望**：无本包 FATAL；无 `avc: denied`；连续冷启 3 次正常；权限最小集。
- **失败判据**：崩溃 / SELinux 拒绝执行 / 权限越权。

---

## 5. 总判定表（出包后逐行填）

| 关卡 | 判定（PASS/FAIL/BLOCKED/WARN） | 证据文件 |
|---|---|---|
| S1 market.json 两条 | PASS | §7.1 |
| S2 plugin add + 本地路径 | PASS | §7.2 |
| S3 bun android 映射 | PASS | §7.3 |
| S4 agent 已编译 | PASS | §7.4 |
| S5 名称解析 | PASS | §7.5 |
| A1 新产物可解析 | 待产物 | PA_A1_badging.txt |
| A2 含插件安装代码 | 待复核 | PA_A2_*.txt |
| A3 包结构不回归 | 待复核 | PA_A3_*.txt |
| A4 签名不回归 | 待复核 | PA_A4_*.txt |
| A5 ELF/串 | 待复核 | — |
| A6 预置（若声明） | WARN/待定 | — |
| D1 安装首启 | BLOCKED | PA_D1_logcat.txt |
| D2 可见 workbuddy/qoder | BLOCKED | listings/installed.json |
| D3 登录订阅 | BLOCKED | — |
| D4 Agents 页 | BLOCKED | PA_D4_agents.png |
| D5 无崩溃/权限 | BLOCKED | PA_D5_*.txt |

**结论口径**：源码层 5 项已 PASS；APK 层须新产物出包后复核；设备层全部 BLOCKED。**整体 = NEEDS WORK（缺新产物 + 缺真机验证）**，不得判「已移植可用」。

---

## 6. 风险与约束（务必知晓）

### 6.1 运行时需下载 Bun 与 npm 包（最大风险）
 `plugin add` 首次会：①从 `https://github.com/oven-sh/bun/releases/download` 下载 `bun-linux-aarch64-android.zip`（`internal/plugin/bun.go:62,272`）；②用 Bun 从 npm 安装插件包。手机侧若**无法访问 GitHub Releases / npm**，安装会失败。
 - 缓解：构包时预置 Bun 并用 `MAGPIE_BUN=<路径>` 指向（`bun.go:242-246` 支持 `$MAGPIE_BUN`）；或预置 `plugins.json` + 已装包（A6）。这与「首启即可见」直接相关。

### 6.2 无终端 UI
 APK 壳只拉起 `magpie web`（`MainActivity`），没有内置 shell。故「`magpie plugin list`」在手机上靠两种方式实现：Web UI（Plugins 页，底层即 `/api/plugins`）或 adb 直调二进制（D2b）。验收文档两条通道都给了。

### 6.3 Agents 行 `not detected` 是正常的
 `magpie agents` 在本机输出 `WorkBuddy AI / WorkBuddy / Qoder / Qoder CN … not detected`（§7.4）。原因是这些 agent 适配器是给**桌面版 App** 写模型配置用的；Android 上没有这些桌面 App，因此永远「未检测到」。**能登录订阅、用其模型** 靠的是**插件（provider plugin）**，不是 agent 适配器。D4 只验「行存在」。

### 6.4 版本号
 上一版 APK `versionName=0.1.0`，与文件名 `v0.1.2431` 不一致。若新产物升级 versionCode/versionName 属正常，但须在发布说明注明（A3 记为 WARN 比较项，不阻断）。

### 6.5 签名
 现为 debug 证书。上线前应换 release 证书并**更新 A4 基线指纹**；doc 中比对逻辑已说明。

---

## 7. 附：本次取证命令与原始输出（2026-11-01）

### 7.1 S1 · market.json
```
$ grep -n -iE "workbuddy|qoder" internal/plugin/market.json
8:    { "package": "@magpie-community/opencode-qoder-auth", "name": "Qoder", "icon": "qoder", "providers": ["qoder"], "community": true, "replaces": "qoder",
9:      "summary": { "en": "Your Qoder subscription, with Qoder's own device sign-in.", "zh": "用 Qoder 自己的设备登录使用 Qoder 订阅。" } },
26:    { "package": "@magpie-community/opencode-workbuddy-auth", "name": "WorkBuddy", "icon": "workbuddy-color", "providers": ["workbuddy", "workbuddy-ai"], "community": true, "replaces": "workbuddy",
27:      "summary": { "en": "WorkBuddy's China build (CodeBuddy plan) and its international build.", "zh": "WorkBuddy 国内版（CodeBuddy 套餐）与国际版。" } },
```

### 7.2 S2 · plugin add 用法（nogui host 构建实测）
```
$ ./magpie-host.exe plugin add
magpie: usage: magpie plugin [list] [--json]
       magpie plugin add <name | npm | git | path> install a community plugin by its name (workbuddy, think-tags, Qoder), an OpenCode provider plugin, a pi package or a gateway hook/middleware (opencode-gemini-auth, pi-antigravity, github:owner/repo, ./my-plugin.js, ./alias.middleware.js, ./recall.hooks.ts); npm:<name> is npm's package of a name a community plugin goes by
       magpie plugin login <provider> [<method>]   sign in to a provider a plugin adds
       …
```
源码：`plugins_cli.go:53 case "add","install"`，`:60 provider.AddPlugin`；`store.go:324 func IsPath`（识别 `file:// ./ / \ 绝对路径 盘符`）。

### 7.3 S3 · bun android（internal/plugin/bun.go）
```
50:  "bun-linux-aarch64-android.zip":      "a1c7e2983f1bb65146beb256a4d72449f23042412bc2cf278aa6397ba27e0274",
51:  "bun-linux-x64-android-baseline.zip": "fe36d8d4795e0eadc22fb6696d44d168491c2e5b9b7cbb12b8c96b0c0c40a4f9",
97:    case "android":
98:      t := "bun-linux-" + arch + "-android"
```
（`arch := map[string]string{"arm64":"aarch64","amd64":"x64"}` → arm64 得 `bun-linux-aarch64-android`）

### 7.4 S4 · agent 已编译（host 构建实测）
```
$ ./magpie-host.exe agents | grep -iE "workbuddy|qoder"
  Qoder                     not detected
  Qoder CN                  not detected
  WorkBuddy                 not detected
  WorkBuddy AI              not detected
```
源码：`internal/agent/agents.go:265 qoder(home)`、`:266 qoderCN(home)`、`:270 workbuddy(home)`、`:271 workbuddyAI(home)`。

### 7.5 S5 · 名称解析（`MAGPIE_BUN` 指向不存在路径，仅看解析结果）
```
$ ./magpie-host.exe plugin add workbuddy
magpie: bun add @magpie-community/opencode-workbuddy-auth@latest: exec: "…/nonexistent/bun": executable file not found …（及 dist 分支 bun add ./dist/magpie-community-opencode-workbuddy-auth-0.2.9.tgz）

$ ./magpie-host.exe plugin add Qoder
magpie: bun add @magpie-community/opencode-qoder-auth@latest: exec: "…/nonexistent/bun": executable file not found …（及 dist 分支 bun add ./dist/magpie-community-opencode-qoder-auth-0.2.50.tgz）
```
→ 解析正确；后续失败仅因假 bun 路径。

### 7.6 A2 · 交付二进制含插件安装串
```
$ for s in "plugin add" "opencode-workbuddy-auth" "opencode-qoder-auth"; do grep -a -c -- "$s" out/libmagpie.so; done
3
2
2
```
源码：`internal/gui/plugins.go:572 addPlugin = provider.AddPlugin`；路由 `GET /api/plugins`、`GET /api/plugins/listings`、`GET /api/plugins/agents`、`POST /api/plugins/{op}`；资源 `internal/gui/assets/plugins.js`。

### 7.7 上一版 APK 基线
```
$ aapt2 dump badging dist/magpie-android-arm64-v0.1.2431.apk | head -3
package: name='ai.usemagpie.android' versionCode='1' versionName='0.1.0' … compileSdkVersion='34'
sdkVersion:'24'
targetSdkVersion:'34'
native-code: 'arm64-v8a'

$ apksigner verify --verbose --print-certs dist/magpie-android-arm64-v0.1.2431.apk | head
Verifies
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Signer #1 certificate DN: CN=Android Debug, O=Android, C=US
Signer #1 certificate SHA-256 digest: f923d36ccf1b43104d601030d54540ae5ecb668eaa8a880b4ac43a20bb18f921

$ zipalign -c -v 4 dist/magpie-android-arm64-v0.1.2431.apk | tail -1
Verification succesful

$ unzip -l dist/magpie-android-arm64-v0.1.2431.apk | grep -E "classes.dex|lib/"
     6644  classes.dex
 63111462  lib/arm64-v8a/libmagpie.so
SHA-256: 4602e15bb07bca9cf3cc5139177c826c21865fe1a00641a1ee1fc203515cda3f
```

---

### 文档边界
本文件只定义「WorkBuddy/Qoder 插件移植可用」的验收标准与取证结论，**不改动 `build/`、`dist/`，不运行最终构建**。源码层结论为静态+host 构建实测；APK 层与设备层须在 Lead 出包、真机到位后按本文命令逐项复核。
