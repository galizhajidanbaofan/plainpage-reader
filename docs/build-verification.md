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

---

# 第二轮实测（阶段 2：书架 + 导入 + 最近阅读，2026-10-25）

## 1. 命令与结果

```bash
# 环境（本机工具链都在工作区 .toolchain 下，换成你自己的即可）
export JAVA_HOME=<workspace>/.toolchain/jdk21
export ANDROID_HOME=<workspace>/.toolchain
export ANDROID_USER_HOME=$ANDROID_HOME/android-home
export GRADLE_USER_HOME=$ANDROID_HOME/gradle-home
export JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=<workspace>/.toolchain/tmp

$ANDROID_HOME/gradle-dist/bin/gradle.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

结果：

```
BUILD SUCCESSFUL in 41s
53 actionable tasks: 11 executed, 7 from cache, 35 up-to-date
```

- 单元测试 **37 个全通过**（`tests=37 failures=0 errors=0`）：`BookEntityTest` 5、`BookFormatTest` 3、`DisplayFormatsTest` 11、`ImportRulesTest` 8、`ReadingLocationCodecTest` 5、`ThemeTokensTest` 5。
- `lintDebug`：**0 errors, 4 warnings**。其中 3 条是 `NewerVersionAvailable`（Kotlin 2.3.20 vs 2.4.21），属**已知取舍**（KSP 现版本只支持 Kotlin 2.3.20）；1 条 `ObsoleteSdkInt` 指向 `mipmap-anydpi-v26`，实际是**误报**——把 adaptive icon 移到 `mipmap-anydpi` 后 AAPT 链接阶段直接报 `resource mipmap/ic_launcher not found`（adaptive icon 必须留在 `-v26` 目录），已改回。
- 产物：`app/build/outputs/apk/debug/app-debug.apk`，**11,982,156 字节（11.42 MiB）**。
- APK badging：`minSdkVersion:'26'`、`targetSdkVersion:'37'`、`application-label:'简页'`、无 `INTERNET`。

## 2. 本轮新增的坑

| 现象 | 根因 | 处理 |
|---|---|---|
| `since the JVM garbage collector is thrashing` → 守护进程被杀 | 默认堆在低内存机器上被反复 GC | `gradle.properties` 显式 `-Xmx3g -XX:MaxMetaspaceSize=1g` + Kotlin daemon `-Xmx2g` |
| `Could not resolve kotlin-stdlib:2.3.20` + 连接 `dl.google.com` 超时 | `dependencyResolutionManagement` 里 `google()` 在 `mavenCentral()` 前，纯 Central 构件也先去问 Google 镜像 | 把 `mavenCentral()` 提到 `google()` 前面（`settings.gradle.kts`），并在文件里写了注释说明原因 |
| `e: [ksp] java.lang.ExceptionInInitializerError` | Kotlin 2.4.10 + KSP 2.3.12（KSP 无 2.4.x 版本，2.3.12 面向 Kotlin 2.3.20 构建） | Kotlin 降到 **2.3.20**（`kotlin` / `kotlinTest` 两处），compose 插件同步跟随 |
| `AccessDeniedException: ...\Temp\global_synthetics_intermediate...`（设了 `TEMP` 仍然复现） | AAPT2 走 JVM 的 `java.io.tmpdir`，不读 `TEMP` | `JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=<workspace>/.toolchain/tmp`（对所有 worker JVM 生效） |
| `BookImporter.kt:167 Syntax error: Expecting a top level declaration` ×30 | KDoc 里写了 `` `*/*` ``，其中的 `*/` **提前闭合了注释块** | 改写措辞（不出现 `*/`）。教训：注释里任何 `*/` 都是雷 |
| `Android resource linking failed: resource mipmap/ic_launcher not found` | 见上文 `ObsoleteSdkInt` 误报 | 把 `mipmap-anydpi` 改回 `mipmap-anydpi-v26` |
| `RedundantLabel` lint 警告 | Activity 的 `android:label` 与 Application 重复 | 删掉 Activity 上的 `android:label` |

## 3. 模拟器预览：本机不可用（已尝试并判定）

目标是用 AVD 跑起来截图给用户看。**结论：本机起不来，已如实放弃，不是没试。**

做到哪一步：

- `sdkmanager` 装好 `emulator`（37.2.12）与 `system-images;android-36;google_apis;x86_64`（4.3 GB，含 `system.img` / `vendor.img`）。
- `avdmanager create avd -n plainpage -d pixel_6` 成功（提示 `Could not load devices from ...devices.xml` 但 AVD 已生成，设备配置走默认）。
- `emulator` 能识别 `WHPX ... accelerator is operational`，进程能起来。

卡在哪：

| 尝试 | 结果 |
|---|---|
| 默认 `-gpu swiftshader_indirect` | emulator 进程 exit 1，日志只有 `FATAL \| Running multiple emulators with the same AVD...`（上一次被杀留下的 AVD 锁） |
| 清掉 AVD 锁 / 单实例重跑 | 进入 boot，但 adb 一直 `offline`；qemu 进程 CPU 时间 8 分钟只涨到 1.3 s（`Get-Process` 实测），说明**guest 根本没在跑指令** |
| `-no-window` `-gpu off` `-gpu swiftshader_indirect` `-feature -Netsim` `-read-only` + 全新 `userdata-qemu.img` | 全部同样卡在 `adb offline`；`netsim` 那条报 `called Result::unwrap() on an Err value: Os { code: 5, kind: PermissionDenied }`（`external/netsim+/next/common/src/system/mod.rs:17`） |

判断：该机器上的 qemu 拿不到可用的虚拟化执行环境（WHPX 报告可用但 guest 不执行），属**环境限制**，不是项目代码问题。要预览成果，需要一台真机或另一台能跑 AVD 的机器。

## 3.1 替代方案：Robolectric + Roborazzi 离屏截图（已跑通）

模拟器不可用之后改走这条路：在 **JVM** 上用 Robolectric 的原生图形把 Compose 界面画成 PNG，
不需要 AVD、不需要设备、不需要网络。

- 测试类：`app/src/test/java/io/github/galizhajidanbaofan/plainpage/ui/screenshot/ShelfScreenshotTest.kt`
- 命令：`./gradlew :app:testDebugUnitTest --tests "*ShelfScreenshotTest*"`
- 产物：`app/build/screenshots/*.png`（仓库 `docs/screenshots/` 同步一份）
- 覆盖：书架列表/网格 × 浅色/深色/护眼 + 空书架，共 5 张

两处必须的本机适配（都已写进 `app/build.gradle.kts` 的 `testOptions`）：

| 现象 | 原因 | 处理 |
|---|---|---|
| `Couldn't create lock file C:\Users\19853\.robolectric-download-lock`（拒绝访问） | Robolectric 默认往用户主目录下载 android-all jar 并建锁文件，本机禁止写该目录 | 预先下载 `android-all-instrumented-14-robolectric-10818077-i7.jar` 到 `.toolchain/robolectric/`，设 `robolectric.offline=true` + `robolectric.dependency.dir=<该目录>` |
| `Configuration cache state could not be cached: ... KspAATask` | KSP 9.x 与 Gradle configuration cache 的序列化冲突 | `org.gradle.configuration-cache=false`（换取可重复构建） |

另注：`androidx.test.ext:junit` 当时在 dl.google.com 上解析超时，改用 Robolectric 自带的
`RobolectricTestRunner` 即可，少一个依赖。

## 4. 仍未验证

- 导入 / 书架 / 删除 / 最近阅读的**设备端**实际行为（单测只覆盖纯逻辑与实体映射）。
- 阅读页目前是占位页，"打开书籍"只到占位页为止，正文渲染尚未实现。
- 性能门禁、release 构建、仪器测试：同前一轮，全部未做。
