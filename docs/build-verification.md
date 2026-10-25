# 构建验证记录

> 这份文件记录**实际跑过的命令与真实输出**，不是愿望清单。改动构建配置时请更新这里。
> 最近更新：2026-10-25。

## 1. 本机工具链（构建机实测）

| 组件 | 版本 | 位置 | 备注 |
|---|---|---|---|
| JDK | Temurin 21.0.12.1+1 | `.toolchain/jdk21`（工作区外，不入库） | AGP 9 要求 JDK 17+，实测 21 通过 |
| Gradle | 9.8.2 | `.toolchain/gradle-dist`，仓库内 wrapper 指向同一版本 | `services.gradle.org/versions/current` 实测返回 9.8.2 |
| Android SDK Platform | `platforms;android-37.0` | `.toolchain/platforms/android-37.0` | compileSdk 37 解析到 **37.0**（不是 37.1，装了 37.1 会报 `Failed to find Platform SDK with path: platforms;android-37.0`） |
| Build-Tools | 36.0.0 / 36.1.0 / 37.0.0 | `.toolchain/build-tools/` | AGP 9.4.1 默认要 **36.0.0**，缺了会报 `Failed to find Build Tools revision 36.0.0` |
| Platform-Tools | adb 37.0.0 | `.toolchain/platform-tools` | 本机当前**没有设备/模拟器**，仪器测试未跑 |

## 2. 构建实测

```bash
export JAVA_HOME=".../.toolchain/jdk21"
export ANDROID_HOME=".../.toolchain"
export ANDROID_USER_HOME=".../.toolchain/android-home"   # 让 debug.keystore 落在沙箱可写处
export GRADLE_USER_HOME=".../.toolchain/gradle-home"
export TEMP=".../.toolchain/.tmp" TMP="$TEMP"            # AAPT2 global synthetics 要写临时目录
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

结果（2026-10-25）：

| 任务 | 结果 | 证据 |
|---|---|---|
| `:app:assembleDebug` | ✅ 通过 | `app/build/outputs/apk/debug/app-debug.apk`，11,646,149 字节 |
| `:app:testDebugUnitTest` | ✅ 13 个测试全通过 | `app/build/test-results/testDebugUnitTest/*.xml`：BookFormatTest 3、ReadingLocationCodecTest 5、ThemeTokensTest 5，`failures=0 errors=0` |
| `:app:lintDebug` | ✅ 通过（无阻断） | `BUILD SUCCESSFUL in 2m 29s` |

APK 静态校验（`aapt2 dump badging`）：

```
package: name='io.github.galizhajidanbaofan.plainpage' versionCode='1' versionName='0.1.0'
compileSdkVersion='37' compileSdkVersionCodename='17'
targetSdkVersion='37'
application-label:'简页'
uses-permission: name='io.github.galizhajidanbaofan.plainpage.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
```

- **没有 `android.permission.INTERNET`**，也没有任何存储权限 —— 仅有的这条是 AndroidX 自动生成的进程内广播保护权限，不是对外权限。
- minSdk 26 由 `app/build.gradle.kts` 的 `minSdk = 26` 保证（badging 的 `sdkVersion` 行未在本次 grep 中输出）。

## 3. 踩过的坑（按出现顺序）

| 现象 | 根因 | 处理 |
|---|---|---|
| 沙箱内 `Gradle build daemon` 起不来：`Could not start 'java.exe'` | workspace-write 沙箱禁止子进程管道，Gradle 需要守护进程/子进程通信 | 构建命令在**沙箱外（danger-full-access）**执行；正常开发者机器无此问题 |
| `The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0` | **AGP 9 内置 Kotlin 支持**，再手动应用 Kotlin 插件会被拒绝 | 删除 `org.jetbrains.kotlin.android`，保留 `org.jetbrains.kotlin.plugin.compose`；`kotlin { compilerOptions { … } }` 仍可用 |
| `Failed to find Build Tools revision 36.0.0` | AGP 9.4.1 默认 build-tools 版本 | 安装 `build-tools;36.0.0`（未在构建脚本里钉死版本） |
| `Failed to find Platform SDK with path: platforms;android-37.0` | compileSdk 37 → 平台目录 `android-37.0`，而本机先装的是 `android-37.1` | 补装 `platforms;android-37.0` |
| `AccessDeniedException: ...\Temp\global_synthetics_intermediate...` | AAPT2 写用户 Temp，沙箱不允许 | `TEMP`/`TMP` 指到工作区内目录 |
| `AccessDeniedException: C:\Users\<用户>\.android\debug.keystore.lock` | 沙箱不许写用户主目录 | `ANDROID_USER_HOME` 指到工作区内目录 |
| `wrapper` 任务失败：`Test of distribution url … failed` | 本机构建机系统代理损坏，构建脚本校验 distributionUrl 时走不通 | `gradle wrapper --no-validate-url` 生成；生成的 `gradle-wrapper.properties` 已移除 `validateDistributionUrl=false`，保持默认校验 |
| `sdkmanager` 报 `Java version 17 or higher is required` / 路径找不到 | `.bat` 在 bash 下叠了 `cmd` 转义层 | 用 `cmd //c '...sdkmanager.bat ...'` 且传**绝对路径**；另设 `SKIP_JDK_VERSION_CHECK=1` |

## 4. 未验证（别当成已通过）

- 真机 / 模拟器运行（本机无设备）：导入、打开、目录跳转、杀进程后进度恢复全部**未跑过**。
- 性能门禁（冷启动 <2s、10MB EPUB / 100MB TXT / 50MB PDF 不 ANR）：**未测量**。
- `assembleRelease`（R8/混淆）与签名：**未跑**，`proguard-rules.pro` 目前是空的占位。
- 仪器测试：仓库内**还没有** `androidTest`。
