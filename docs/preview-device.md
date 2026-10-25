# 在设备上预览（开发用）

本文只记录**开发机**如何把 debug APK 跑起来看画面。它不影响应用本身的发布产物：
APK 里没有网络权限、没有危险存储权限，也不含任何预览工具代码。

## 1. 已完成的本机配置

DSH 侧「手机预览」插件（`dsh-plugin-android-preview` 0.2.0）的配置写在：

```
C:\Users\19853\.dsh\profiles\desktop\cordis.patch.yml
```

条目内容（`id: android-preview`）：

```yaml
- id: android-preview
  name: dsh-plugin-android-preview
  config:
    projectRoot: C:/Users/19853/Documents/deepseek-harness/default-workspace/plainpage-reader
    appDirectory: .
    apkPath: C:/Users/19853/Documents/deepseek-harness/default-workspace/plainpage-reader/app/build/outputs/apk/debug/app-debug.apk
    applicationId: io.github.galizhajidanbaofan.plainpage
    activity: .ui.MainActivity
    hotReload: false
    watchDirectories: []
    flutterRoot: ""
    adbPath: C:/Users/19853/Documents/deepseek-harness/default-workspace/.toolchain/platform-tools/adb.exe
    scrcpyServer: C:/Users/19853/Documents/deepseek-harness/default-workspace/.toolchain/scrcpy/scrcpy-server-v3.3.3
    serial: emulator-5554
    renderer: auto
    port: 18772
    frameAncestors:
      - dsh-app://app
```

说明：

- 这是 Kotlin/Compose 工程，不走 Flutter 热重载 → `hotReload: false`、`watchDirectories: []`。
- `activity` 用相对包名的短形式；Manifest 里 MainActivity 的 `android:name` 是 `.ui.MainActivity`。
- `apkPath` 指向 Gradle 默认 debug 产物，必须**先构建**再开预览。
- `scrcpy-server` 用的是官方 3.3.3 server（`scrcpy-server-v3.3.3`，90164 字节）。
- 插件只投屏**已经在运行的模拟器**，它不启动模拟器、不构建 APK。

## 2. 目前的本机阻塞点（实测）

模拟器在本机起不来，原因是宿主环境的图形/虚拟化能力不足，不是工程配置问题：

1. `emulator -accel off`（纯 TCG）与默认 WHPX 加速两种方式都试过：qemu 进程打印完启动参数后
   既不产生内核日志（`-show-kernel` 无任何 `Linux version` 行），CPU 时间停在约 0.4 秒，
   `adb devices` 一直是 `emulator-5554 offline`，随后进程退出。
2. 图形侧缺 Vulkan 驱动：不加 `-feature -Vulkan` 时 qemu 直接以 exit 21 退出
   （`emuglConfig_init: vulkan_mode_selected:lavapipe` 之后失败）。
3. 系统库侧缺 UCRT（`api-ms-win-crt-*.dll` 一组），已从 `emulator/lib64/` 拷了 downlevel 版本
   到 `qemu/windows-x86_64/`，此后报错前移到 Vulkan/虚拟化，说明这一步是有效修复。
4. WHPX 在日志里报告 `Windows Hypervisor Platform (WHPX) is operational`，但 guest 始终不执行；
   常见原因是本机自身跑在虚拟机里且未开启嵌套虚拟化。

结论：本机**没有可用的 Android 模拟器**，因此借助模拟器的「预览」暂时不可用。

## 3. 在有虚拟化的机器上怎么跑

1. 确认 SDK 就绪：`platform-tools`、`emulator`、`system-images;android-36;google_apis;x86_64`。
2. 创建 AVD（本机已建过一个名为 `plainpage` 的 AVD，可复用）：

   ```bat
   avdmanager create avd -n plainpage -k "system-images;android-36;google_apis;x86_64" -d pixel_6
   ```

3. 启动：

   ```bat
   set ANDROID_HOME=C:\path\to\sdk
   set ANDROID_USER_HOME=%ANDROID_HOME%\android-home
   set ANDROID_AVD_HOME=%ANDROID_USER_HOME%\avd
   set ANDROID_EMULATOR_HOME=%ANDROID_USER_HOME%
   set TMP=%ANDROID_HOME%\tmp
   set TEMP=%TMP%
   %ANDROID_HOME%\emulator\emulator.exe -avd plainpage -no-snapshot -gpu swiftshader_indirect
   ```

4. `adb devices` 里看到 `emulator-5554  device` 后：

   ```bat
   gradlew.bat :app:assembleDebug
   ```

5. 打开 DSH 右侧「手机预览」页签 → 插件安装并启动 APK。

## 4. 换成真机（可选）

本机 `adb` 可用，但插件 0.2.0 只接受 `emulator-NNNN` 形式的 serial（不支持物理设备）。
真机预览只能手动 `adb install` + 直接看手机屏幕。

## 5. 许可证提示

`dsh-plugin-android-preview` 自身是 **AGPL-3.0-only**。它只在本机作为开发工具运行，
不进入 APK、不与 简页 的 Apache-2.0 代码链接分发；但如果要把整套 DSH 配置/插件目录对外发布，
需要单独评估 AGPL 义务。
