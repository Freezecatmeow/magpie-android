# magpie Android 适配 · 「可用 APK」验收标准与测试计划

- 文档路径：E:/deepseek work/magpie-android/docs/acceptance.md
- 归属：本文件由「验收/测试」角色维护，只写本路径，不改动共享构建目录（magpie/、magpie-port/、任何 dist/）。
- 验收对象：把 yetone/magpie 适配 Android 后交付的 APK。
- 版本：v1（2026-11-01）
- 结论口径：**默认 NEEDS WORK**。仅当 G1–G8 全部 PASS，且证据齐备，才可判定「可用 APK」。

---

## 0. 摘要：当前是否可实测

**当前不可实测。** 事实证据（2026-11-01 核查）：

1. 全工作区没有任何 .apk 产物（find 深度 6 结果为空，见 §1）。
2. 没有任何 Android 工程（无 AndroidManifest.xml / build.gradle / settings.gradle，见 §1）。
3. 没有任何 Android 构建/校验工具链（adb、aapt2、apksigner、zipalign、gradle、sdkmanager 全部缺失，ANDROID_HOME/ANDROID_SDK_ROOT/JAVA_HOME 全空，无 SDK 目录、无 ~/.gradle，见 §1）。

因此：G1–G10 全部标记为 **BLOCKED（待产物/待设备）**。本文给出每一关卡的**可执行命令、期望输出、失败判据与证据归档方式**，产物一旦出现即可直接照单执行；本文同时给出**已完成的、无需产物的现实核查**（环境探针 + 仓库架构取证），作为后续验收的基线。

---

## 1. 阶段零：现实核查（已执行，作为基线证据）

### 1.1 工具链探针
命令：逐项 command -v，并打印相关环境变量。

~~~bash
for t in adb aapt2 apksigner zipalign java javac gradle sdkmanager go; do printf "%s -> " "$t"; command -v "$t" 2>/dev/null || echo "(none)"; done
echo "ANDROID_HOME=$ANDROID_HOME"; echo "ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"; echo "JAVA_HOME=$JAVA_HOME"
go version; java -version
ls -d "$LOCALAPPDATA/Android/Sdk" "$HOME/AppData/Local/Android/Sdk" 2>/dev/null
~~~

实测结果：
| 项 | 结果 | 判定 |
|---|---|---|
| go | go1.27.0 windows/amd64 | 可用（可交叉编译 android/arm64） |
| java / javac | 25 (Oracle JDK 25) | 存在，但与 AGP 兼容性存疑（见 §7） |
| adb | (none) | 缺失 → 无法安装/连接设备 |
| aapt2 | (none) | 缺失 → 无法做 badging/权限静态校验 |
| apksigner | (none) | 缺失 → 无法验签 |
| zipalign | (none) | 缺失 → 无法验对齐 |
| gradle | (none) | 缺失 → 无法构建 APK |
| sdkmanager | (none) | 缺失 → 无法安装 SDK 组件 |
| ANDROID_HOME / ANDROID_SDK_ROOT / JAVA_HOME | 均为空 | 未配置 |
| SDK 目录 / ~/.gradle | 均不存在 | 未安装 |

### 1.2 产物与工程探针
~~~bash
find "E:/deepseek work" -maxdepth 6 -iname "*.apk" 2>/dev/null
find "E:/deepseek work" -maxdepth 4 \( -name "AndroidManifest.xml" -o -name "build.gradle" -o -name "build.gradle.kts" -o -name "settings.gradle" -o -name "settings.gradle.kts" \) 2>/dev/null
ls -la "E:/deepseek work/magpie/dist" 2>&1 | head
~~~
实测结果：*.apk = 0 个；Android 工程 = 0 个；magpie/dist 不存在。

> 备注：magpie-port/ 下有若干压缩包与半成品（magpie-main/ 为完整源码解压，magpie-src/ 为空目录，magpie-v0.1.2419.zip 经 python zipfile 校验为 **BadZipFile（损坏/不完整）**，magpie.tar.gz、magpie2.tar.gz 为源码 tarball）。它们都不是 APK，也不构成"产物"。

### 1.3 基线结论
在"无 SDK + 无 APK + 无设备"的三无状态下，任何"通过/合格"的判定都缺乏证据支撑。本文件的使命是把判定标准前置固化，避免后续出现无证据的"已就绪"结论。

---

## 2. 目标架构与验收依据（仓库取证）

以下取证用于锚定"可用 APK"到底要验证什么，全部来自 magpie 仓库 HEAD 源码。

| 事实 | 证据位置 | 对验收的含义 |
|---|---|---|
| magpie 为 Go 单二进制；web UI 通过 go:embed 打进二进制（//go:embed assets） | internal/gui/api.go（//go:embed assets） | APK 的壳只需「二进制 + WebView」，无需外挂前端资源目录 |
| Makefile 的 TARGETS 含 android/arm64；GOOS=android 时 TAGS=nogui（去 GTK/Wails） | Makefile 第 15/32 行（git show HEAD:Makefile） | Android 侧应使用 nogui 构建，CGO_ENABLED=0 可交叉编译 |
| release-cli 产出 dist/magpie-cli-android-arm64 | Makefile 第 97–100 行 | 期望的 Go 载荷命名/locations |
| magpie web 默认监听 127.0.0.1:3430；支持 --addr / --no-open / --lan | internal/cli/web_cli.go（const webAddr / webCmd） | APK 内拉起服务用 --no-open；WebView 打开该地址 |
| 启动后页面链接带登录密钥 ?k=<key>；net.Listen("tcp", addr) | internal/gui/web.go（StartWeb：w.Link = ..."/?k="+key） | WebView 必须带 ?k= 才能进入页面，不是裸根路径 |
| magpie serve --web[=host:port] 同进程提供网关+页面；网关默认 127.0.0.1:3425 | internal/cli/web_cli.go（serveCmd）+ internal/gateway/gateway.go:50 DefaultAddr | 可用 serve --web 一体化，或 web 仅页面 |
| README 明确支持 Android/Termux | README.md 第 45 行 "in Docker, and on Termux" | 上游只到 Termux 终端，不含 APK；APK 属新增封装 |

### 2.1 由架构推出的三个「致命技术点」（直接升级为验收门槛）
1. **exec 限制（对应 G5/G4）**：Android 10+（API 29+）禁止在 app data 目录执行文件（W^X / noexec）。Go 二进制若作为 assets 释放到 /data/data/<pkg>/ 再 exec 会 EACCES。正确做法：将其作为 **lib/arm64-v8a/libmagpie.so** 打包（或 android:extractNativeLibs=true），从 applicationInfo.nativeLibraryDir 执行。
2. **明文回环（对应 G7）**：WebView 访问 http://127.0.0.1:3430 需要 cleartext 放行（android:usesCleartextTraffic=true 或 network_security_config 对 127.0.0.1 放开 cleartext），并声明 INTERNET 权限；否则出现 net::ERR_CLEARTEXT_NOT_PERMITTED / ERR_CONNECTION_REFUSED。
3. **可写 HOME（对应 G10）**：magpie 需要可写配置目录（os.UserHomeDir）。Android 上须把 HOME/相关环境指向 app 私有 files 目录，否则设置与密钥无法持久化。

---

## 3. 「可用 APK」定义与验收门槛（Gates）

**定义**：一个 APK 被判"可用"，必须 G1–G8 全部 PASS。G9/G10 为加分项（不阻断，但记录）。BLOCKED 不等于 PASS；任何一项 FAIL 即整体 NEEDS WORK。

| 关卡 | 检查项 | 判定标准（PASS 条件） | 主要方法 | 需产物? |
|---|---|---|---|---|
| G1 | 包结构 | aapt2 dump badging 可解析；含 package、versionCode、minSdkVersion、targetSdkVersion、launchable-activity | aapt2 dump badging | 是 |
| G2 | 签名 | apksigner verify 通过（v2 或 v3 至少一项）；能打印证书指纹 | apksigner verify --verbose --print-certs | 是 |
| G3 | 对齐 | zipalign -c -v 4 返回 Verification succesful | zipalign -c -v 4 | 是 |
| G4 | 二进制仅 arm64 | native-code 仅 'arm64-v8a'（无 armeabi-v7a/x86/x86_64）；lib/ 下仅 lib/arm64-v8a/；文件为 ELF AArch64；go version -m 显示 GOOS=android GOARCH=arm64 且 tags 含 nogui | aapt2 badging + unzip -l + file/readelf + go version -m | 是 |
| G5 | 可执行布局 | 二进制位于 lib/arm64-v8a/ 并在 nativeLibraryDir 执行（非 assets 直 exec）；启动无 EACCES / avc denied | unzip -l + logcat | 是 |
| G6 | 可安装 | adb install -r 返回 Success；pm path 有值；dumpsys package 能列出 targetSdk 与权限 | adb install / pm / dumpsys | 是+设备 |
| G7 | 首启达本地服务 | 冷启动后 ≤15s：(a) 设备上有 magpie 进程；(b) logcat 无 ERR_CONNECTION_REFUSED / ERR_CLEARTEXT；（c) 截图显示 web UI 首屏（非白屏/非错误页） | am start + ps + logcat + screencap | 是+设备 |
| G8 | 崩溃/权限 | logcat -b crash 无本包 FATAL；连续冷启 3 次均正常；权限仅最小集（INTERNET；如需后台存活再加 FOREGROUND_SERVICE/POST_NOTIFICATIONS），无位置/通讯录/存储越权 | logcat -b crash + dumpsys package | 是+设备 |
| G9 | 后台存活（加分） | 退后台 5 分钟返回，页面仍可交互（前台服务+通知保活） | 手动/脚本计时 + screencap | 是+设备 |
| G10 | 数据持久（加分） | 改一项设置→杀进程→重启仍在（HOME 指向私有目录生效） | am force-stop + 重启 + 读 UI | 是+设备 |

---

## 4. 测试计划（可执行命令清单）

### 4.0 变量与前置
~~~bash
# 依据实际安装位置填写（产物出现后由构建方提供）
APK="E:/deepseek work/magpie-android/dist/magpie.apk"
PKG="com.yetone.magpie"                 # 以 aapt2 badging 实际输出为准
SDK="$LOCALAPPDATA/Android/Sdk"
BT="$SDK/build-tools/35.0.0"           # 以实际版本为准
AAPT2="$BT/aapt2"; APKSIGNER="$BT/apksigner"; ZIPALIGN="$BT/zipalign"
mkdir -p "E:/deepseek work/magpie-android/docs/evidence"
~~~
所有证据落盘到 docs/evidence/，命令中 tee 到对应文件。

### 4.1 G1 · 包结构（静态）
~~~bash
"$AAPT2" dump badging "$APK" | tee docs/evidence/G1_badging.txt
~~~
PASS 判据：输出含 package: name=... versionCode=... / sdkVersion / targetSdkVersion / launchable-activity。
失败即：解析报错或缺少 launchable-activity（壳无入口）。

### 4.2 G2 · 签名（静态）
~~~bash
"$APKSIGNER" verify --verbose --print-certs "$APK" | tee docs/evidence/G2_apksigner.txt
~~~
PASS 判据：出现 Verified using v2/v3 scheme: true，并打印 Signer #1 certificate DN/SHA-256。
失败即：ERROR: Missing META-INF 或 "DOES NOT VERIFY"。_debug 证书可接受（标注为可发布前须换 release 证书）。

### 4.3 G3 · 对齐（静态）
~~~bash
"$ZIPALIGN" -c -v 4 "$APK" | tee docs/evidence/G3_zipalign.txt
~~~
PASS 判据：末行 "Verification succesful"。
失败即：报 "some entries are not aligned"（需 zipalign -f 4 后重签）。

### 4.4 G4 · 二进制仅 arm64（静态，硬门槛）
~~~bash
"$AAPT2" dump badging "$APK" | grep "native-code" | tee docs/evidence/G4_nativecode.txt
unzip -l "$APK" | grep "lib/" | tee docs/evidence/G4_libs.txt
unzip -p "$APK" lib/arm64-v8a/libmagpie.so > /tmp/libmagpie.so
file /tmp/libmagpie.so | tee docs/evidence/G4_file.txt
go version -m /tmp/libmagpie.so | tee docs/evidence/G4_goversion.txt
~~~
PASS 判据（全部满足）：
- native-code 行 = 仅 'arm64-v8a'（不得出现 armeabi-v7a / x86 / x86_64）。
- unzip -l 的 lib/ 下只有 lib/arm64-v8a/（若出现其他 ABI 目录即 FAIL）。
- file 输出含 "ELF 64-bit LSB ... ARM aarch64"。
- go version -m 输出含 GOOS=android、GOARCH=arm64、-tags=nogui。
失败即：出现多 ABI（不符合"仅 arm64"）；或 ELF 为 AArch32/x86；或 go buildinfo 显示非 android。
工具备选：Windows 无 readelf 时，可用 NDK 的 llvm-readelf，或用 python 读 ELF 头 e_machine（偏移 18，值 0xB7=183=AArch64）：
~~~bash
python -c "import struct; d=open('/tmp/libmagpie.so','rb').read(20); print('e_machine=',struct.unpack('<H',d[18:20])[0])"
~~~

### 4.5 G5 · 可执行布局（静态+运行）
~~~bash
unzip -l "$APK" | grep -E "lib/arm64-v8a|assets/" | tee docs/evidence/G5_layout.txt
"$AAPT2" dump xmltree "$APK" AndroidManifest.xml | grep -i extractNativeLibs | tee docs/evidence/G5_manifest_extract.txt
~~~
PASS 判据：二进制在 lib/arm64-v8a/ 下；且（extractNativeLibs=true 或 明确从 nativeLibraryDir 执行）。
运行证据（G7 时一并抓）：logcat 无 "avc: denied"、无 EACCES。

### 4.6 G6 · 安装性（需设备/模拟器）
~~~bash
adb devices | tee docs/evidence/G6_devices.txt
adb install -r --abi arm64-v8a "$APK" | tee docs/evidence/G6_install.txt
adb shell pm path "$PKG" | tee docs/evidence/G6_pmpath.txt
adb shell dumpsys package "$PKG" | grep -E "targetSdk|versionName|granted=true|requested permissions" -A2 | tee docs/evidence/G6_pkginfo.txt
~~~
PASS 判据：install 输出 Success；pm path 返回 apk 路径；dumpsys 有 targetSdk/权限段。
失败即：INSTALL_FAILED_NO_MATCHING_ABIS（说明产物非纯 arm64 或设备非 arm64 → 反证 G4）；INSTALL_PARSE_FAILED（包结构/签名坏）。

### 4.7 G7 · 首启 WebView 达本地服务（核心判据，需设备）
~~~bash
adb logcat -c
adb shell am start -n "$PKG"/.MainActivity      # Activity 名以 badging 的 launchable-activity 为准
sleep 10
adb shell ps -A -o PID,NAME | grep -i magpie | tee docs/evidence/G7_process.txt
adb logcat -d | grep -Ei "chromium|WebView|ERR_CONNECTION_REFUSED|ERR_CLEARTEXT|net::ERR|magpie" | tee docs/evidence/G7_logcat_net.txt
adb exec-out screencap -p > docs/evidence/G7_first_screen.png
~~~
PASS 判据（三条同时满足）：
- ps 能看到 magpie 进程（Go 二进制已拉起）。
- logcat 无 ERR_CONNECTION_REFUSED、无 ERR_CLEARTEXT_NOT_PERMITTED。
- 截图显示 magpie web UI 首屏（有导航/标题，而非白屏或报错页）。
补充（可选、更有力）：应用内 WebView.setWebContentsDebuggingEnabled(true) 后，在宿主 chrome://inspect 查看已加载 URL 与网络请求。

### 4.8 G8 · 崩溃/权限（需设备）
~~~bash
adb logcat -b crash -d | tee docs/evidence/G8_crash.txt
# 连续冷启 3 次
for i in 1 2 3; do adb shell am force-stop "$PKG"; adb shell am start -n "$PKG"/.MainActivity; sleep 8; adb logcat -d -b crash | tail -5; done | tee docs/evidence/G8_3starts.txt
adb shell dumpsys package "$PKG" | grep -i "permission" | tee docs/evidence/G8_perms.txt
~~~
PASS 判据：crash 缓冲区无本包 FATAL；3 次冷启均进入 G7 画面；权限集合为最小集。
失败即：出现 FATAL EXCEPTION / ANR；或请求 READ_CONTACTS/ACCESS_FINE_LOCATION/READ_EXTERNAL_STORAGE 等无关敏感权限。

### 4.9 G9 · 后台存活（加分，需设备）
~~~bash
adb shell input keyevent KEYCODE_HOME
sleep 300
adb shell am start -n "$PKG"/.MainActivity
sleep 3
adb exec-out screencap -p > docs/evidence/G9_after_bg.png
~~~
PASS：返回后页面立即可用（进程未被杀或前台服务重启成功）。

### 4.10 G10 · 数据持久（加分，需设备）
在 UI 改一项可辨识设置→杀进程→重启→读 UI 确认仍在；证据截图 docs/evidence/G10_before.png / G10_after.png。

---

## 5. 判定矩阵与证据归档规范

- 每个关卡状态取值：PASS / FAIL / BLOCKED。BLOCKED 必须注明原因（无产物/无设备/无工具）。
- 最终判定：G1–G8 全 PASS → 可用；任一 FAIL → NEEDS WORK；存在 BLOCKED 且非环境原因 → 不能下"可用"结论。
- 证据文件命名（全部落 docs/evidence/）：
  - G1_badging.txt、G2_apksigner.txt、G3_zipalign.txt
  - G4_nativecode.txt / G4_libs.txt / G4_file.txt / G4_goversion.txt
  - G5_layout.txt / G5_manifest_extract.txt
  - G6_devices.txt / G6_install.txt / G6_pmpath.txt / G6_pkginfo.txt
  - G7_process.txt / G7_logcat_net.txt / G7_first_screen.png
  - G8_crash.txt / G8_3starts.txt / G8_perms.txt
  - G9_after_bg.png / G10_before.png / G10_after.png
- 每份报告末尾附「证据清单」，逐条指向上面文件名，禁止只写结论不附文件。

---

## 6. 当前只能"待产物"的项（明确清单）

在产物与工具链就绪前，以下**全部**只能待验，本文不预判通过：
- G1–G5：需 APK 产物 + build-tools（aapt2/apksigner/zipalign）。
- G6–G10：需 APK + arm64 设备/模拟器 + platform-tools(adb)。
当前状态一律 BLOCKED，原因：无 APK、无 SDK、无设备（§1 已证）。

---

## 7. 阻塞点与解锁前提（交编排方）

1. **缺 Android 工具链**：需安装 cmdline-tools + platform-tools + build-tools + 对应 platform；接受 SDK licenses。安装后 aapt2/apksigner/zipalign/adb 才有。
2. **缺构建器**：无 Gradle / AGP。构建 APK 需 Android Gradle 工程（Kotlin/Java 壳 + WebView + 将 libmagpie.so 打进 jniLibs）。
3. **JDK 兼容风险**：本机 JDK 25。AGP 8.x 官方支持到 JDK 21，JDK 25 很可能不被 Gradle/AGP 接受，建议另装 JDK 17 或 21 并以 org.gradle.java.home 指定。
4. **Go 载荷**：Go 1.27 已具备，可 go build GOOS=android GOARCH=arm64 -tags nogui（CGO_ENABLED=0），产物即 libmagpie.so 来源；无需 NDK（除非采用 gomobile 方案）。
5. **设备**：需 arm64 ABI 的真机或 arm64 模拟器。x86_64 模拟器无法安装纯 arm64 包（会 INSTALL_FAILED_NO_MATCHING_ABIS），可作为 G4 的反向佐证但不能当正样本。
6. **目录纪律**：本文件不改共享构建目录；APK 期望产物位置由构建方在其自管目录产出，验收方只读校验。

---

## 8. 变更记录

- 2026-11-01 v1：建立验收门槛 G1–G10、命令化测试计划、证据归档规范、环境与仓库取证基线。当前无产物，全部用例 BLOCKED。
