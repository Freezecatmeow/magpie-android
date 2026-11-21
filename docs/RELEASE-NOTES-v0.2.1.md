# v0.2.1 · magpie for Android（宿主侧缺口收口版）

- APK：`magpie-android-arm64-v0.2.1-offline.apk`（60,871,936 B，sha256 `5a02b26ae47902ed8eb09ba22143b8dd1df3cce87d82bb6ba4ad07b91f79517b`）
- versionCode 5 / versionName 0.2.1；arm64-v8a；正式 Release 签名 CN=Magpie Android Release（v2+v3），支持从 v0.2.0 覆盖升级
- 静态门禁 G1–G5：全部 PASS

## 本版新增（纯宿主侧）
- FR-22 诊断中心 DiagnosticsActivity（状态聚合/日志/loopback 查询/诊断 zip 导出）
- FR-02·BUG-06 宿主侧 NetworkMonitor（系统代理+CA 变更监听，注入子进程 HTTP(S)_PROXY）
- FR-23·BUG-07 宿主侧 ApkUpdater 版本检查+下载+SHA-256 校验+ApkFileProvider 安装器 + UpdateActivity 面板
- FR-21 宿主侧桌面微件拉取 /api/usage/quotas（后台线程+三降级+刷新）
- FR-17·BUG-08 宿主侧 WindowInsets→CSS 变量 + assets/android.css 响应式注入

## 仍未解决（阻断源不变）
- Go 内核 CGO_ENABLED=0（无 Go 源码/NDK）：插件市场/API Key/同步/搜索等 Go 侧出网仍不可用（阻断源 A）。
- 真机动态门禁 G6–G10（无 adb/真机，阻断源 B）。
- Gradle+AndroidX 流水线（无 AGP/AndroidX 离线缓存，阻断源 C）。
详见 docs/PARITY-REPORT-v0.2.1.md §4.2。

---

# v0.3.0 · Go 侧补齐版

- APK：magpie-android-arm64-v0.3.0-offline.apk（60,830,976 B，sha256 e2b34e2b508c83f1f3ec1b156970a3e180b2496634fa94286c92e52b4faa182c）
- versionCode 6 / 0.3.0；arm64-v8a；正式 Release 签名 v2+v3（CN=Magpie Android Release）
- 核心：libmagpie.so 已用 Android NDK r27 以 CGO_ENABLED=1 重编——readelf 实测 NEEDED libc.so、4× LOAD 0x4000 对齐，Go DNS 故障（BUG-01/FR-01）在构建级修复。
- 新增 Go androidHost 桥接（internal/gui/android_host.go）：OpenURL→Custom Tabs / Copy→剪贴板 / ChooseFolder / export；BUG-07 加 android 自替换禁写守卫。
- FR-09 / FR-13 / FR-02 / BUG-06 经源码核验为上游既有能力（MAGPIE_BUN / --lan / *_PROXY / SSL_CERT_FILE），无需改码。
- 未完成：FR-06（github tarball）、FR-19（Go 告警触发）仍待补；FR-04/05/10/20 与 G6–G10 需真机与真实网络。
