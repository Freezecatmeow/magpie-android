# magpie Android · 图标/名称 与返回键修复（v0.1.1）

## 问题
1. 安装后桌面没有 magpie 的图标和名称（显示为系统默认机器人图标/无名称）。
2. 在 App 内按手机返回键直接退出程序，而不是回到上一页。

## 修复
### 图标与名称
- 取 magpie 官方图标 `build/windows/icon-256.png`，用 PIL 生成 5 档密度：
  `res/mipmap-{mdpi 48, hdpi 72, xhdpi 96, xxhdpi 144, xxxhdpi 192}/ic_launcher.png`。
- `AndroidManifest.xml`：`<application android:icon="@mipmap/ic_launcher" android:roundIcon="@mipmap/ic_launcher" android:label="magpie">`，并给 `<activity>` 也加 `android:label="magpie"`。
- 版本号提升：versionCode 2 / versionName 0.1.1（便于覆盖安装识别）。
- 校验：aapt2 badging 显示 `application-label:'magpie'`，`application-icon-160/240/320/480/640` 均指向 ic_launcher.png，launchable-activity label='magpie'。

### 返回键
- `MainActivity.onBackPressed()`：`if (web.canGoBack()) web.goBack(); else super.onBackPressed();`
- 即：网页有历史就回上一页，只有无历史时才退出 App（符合 Android 习惯）。
- 说明：targetSdk 34 下 onBackPressed 虽标注 deprecated，但未启用 predictive back 时仍会被调用，行为正确。

## 3) 顶部黑边去除（v0.1.2）
- 现象：状态栏是系统默认黑底，像一条黑边压在白色页面之上。
- 修复：`MainActivity.applyEdgeToEdge()` 设 `setStatusBarColor(WHITE)`、`setNavigationBarColor(WHITE)`（API26+），并加 `SYSTEM_UI_FLAG_LIGHT_STATUS_BAR / LIGHT_NAVIGATION_BAR`（浅色栏 + 深色图标）；`web.setBackgroundColor(WHITE)`；清单 `application android:theme=Theme.DeviceDefault.Light.NoActionBar`，消除暗色默认底导致的黑带。
- 校验：manifest theme= ...Light.NoActionBar；dex 含 setStatusBarColor；badging 版本 0.1.2。

## 产物
- 最新：[magpie-android-arm64-v0.1.2-offline.apk](../dist/magpie-android-arm64-v0.1.2-offline.apk)（vC3，60.8MB，sha256 `c3254c262832f6bcc03ec9a86e641aee65b272a465d5fa9f3fdea41ff4d99fd8`）——含图标/名称 + 返回键 + 顶部黑边去除。
- 上一版：[magpie-android-arm64-v0.1.1-offline.apk](../dist/magpie-android-arm64-v0.1.1-offline.apk)（sha256 `a8f410b8…`）
  - v2+v3 签名通过，zipalign "Verification succesful"，仅 arm64-v8a
  - 仍为完全离线版：含 assets/seed.zip(插件) + libmagpie.so + libbun.so

## 安装提示
- 与上一版同包名同签名，可直接覆盖安装（`adb install -r` 或点击安装）。
- 若桌面仍显示旧图标，是启动器缓存：把旧图标从桌面移除再重新从应用列表拖出，或重启一次桌面即可。
