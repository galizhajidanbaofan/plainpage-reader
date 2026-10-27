# 构建验证记录

> 这份文件记录**实际跑过的命令与真实输出**，不是愿望清单。改动构建配置时请更新这里。
> 最近更新：2026-10-27（PDF 引擎）。

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

- 单元测试 **78 个全通过**（`tests=78 failures=0 errors=0`）：`DisplayFormatsTest` 11、`TextEncodingDetectorTest` 8、`TxtChapterScannerTest` 8、`ImportRulesTest` 8、`TxtReaderEngineTest` 7、`ReaderPagerTest` 7、`BookEntityTest` 5、`ReadingLocationCodecTest` 5、`ShelfScreenshotTest` 5、`ThemeTokensTest` 5、`ReaderScreenshotTest` 4、`BookFormatTest` 3、`TxtReadingLoopTest` 2。
- `lintDebug`：**0 errors, 4 warnings**。其中 3 条是 `NewerVersionAvailable`（Kotlin 2.3.20 vs 2.4.21），属**已知取舍**（KSP 现版本只支持 Kotlin 2.3.20）；1 条 `ObsoleteSdkInt` 指向 `mipmap-anydpi-v26`，实际是**误报**——把 adaptive icon 移到 `mipmap-anydpi` 后 AAPT 链接阶段直接报 `resource mipmap/ic_launcher not found`（adaptive icon 必须留在 `-v26` 目录），已改回。
- 产物：`app/build/outputs/apk/debug/app-debug.apk`，**12,087,959 字节（11.53 MiB）**——比上一版多的部分就是 TXT 引擎与阅读页。
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
| `EmptySuperCall` lint 警告 | `ViewModel.onCleared()` 里调了空的 `super.onCleared()` | 直接删掉这行 |
| `UnusedResources`（`R.string.reader_missing`） | 阅读页占位文案被真实阅读页替换后，字符串没人引用 | 删掉该字符串（占位页的 `reader_*` 文案一并清理） |

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

- 导入 / 书架 / 删除 / 最近阅读 / 目录跳转 / 翻页 / 杀进程后进度恢复的**设备端**实际行为（单测只覆盖纯逻辑与实体映射）。
- 性能门禁、release 构建、仪器测试：全部未做。

---

# 第三轮实测（EPUB 引擎，2026-10-26）

## 1. 命令与结果

```bash
export JAVA_HOME=<workspace>/.toolchain/jdk21
export ANDROID_HOME=<workspace>/.toolchain
export ANDROID_USER_HOME=$ANDROID_HOME/android-home
export GRADLE_USER_HOME=$ANDROID_HOME/gradle-home
export JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=<workspace>/.toolchain/tmp

./gradlew :app:assembleDebug :app:lintDebug
./gradlew :app:testDebugUnitTest          # 先删了 app/build/test-results 再跑，避免旧 XML 被计入
```

结果：

```
BUILD SUCCESSFUL in 1m 10s     # assembleDebug + lintDebug
BUILD SUCCESSFUL in 19s        # testDebugUnitTest
```

- 单元测试 **122 个全通过，20 个测试类，failures=0 errors=0**。本轮新增：`HtmlTextExtractorTest` 11、`EpubArchiveTest` 8、`EpubReaderEngineTest` 8、`EpubPackageParserTest` 7、`EpubMetadataReaderTest` 4、`EpubOutlineParserTest` 4。
- `lintDebug`：**0 errors, 4 warnings**（2 条 `NewerVersionAvailable`、1 条 `GradleDependency`、1 条 `ObsoleteSdkInt` 误报），与上一轮同类。
- 产物：`app/build/outputs/apk/debug/app-debug.apk`，**12,175,943 字节（11.61 MiB）**，比阶段 2 多 88 KB——只多了解析器与索引代码，没有新增依赖，没有新增权限。

## 2. 本轮新增的坑

| 现象 | 根因 | 处理 |
|---|---|---|
| 6 个 EPUB 测试全挂在 `SAXParseException: Content is not allowed in prolog` | 测试夹具用 `trimIndent()` 拼 XML，但 `<?xml ...?>` 前面有 `$` 插值留下的缩进/换行 | 夹具改用 `buildString` / 拼接时不依赖 `trimIndent`，并在 `EpubFixtures` 注释里写明"prolog 前不能有任何字符" |
| `XMLConstants.ACCESS_EXTERNAL_DTD` 编译不过（`Unresolved reference`） | Android SDK 里没有 JAXP 1.5 的 `XMLConstants` 对应常量（那是 JDK 的） | 用字符串字面量 `"http://javax.xml.XMLConstants/property/accessExternalDTD"` 设 `DocumentBuilderFactory` 属性，两平台都能跑 |
| 章节区间测试算出来的 `charEnd` 少 1 | 断言按"含尾"写，实际代码按排他上界（注释也写了"空间首尾相接"） | 改断言为排他语义，并在 `EpubIndex` 类注释里再次点明 |
| `EpubDebugTest` 的结果残留在 `test-results` 里，把总数报成 123 | 删了源文件但没删 build 产物，旧 XML 还在被统计 | 统计前 `rm -rf app/build/test-results`；CI 里应从干净 build 目录统计 |

## 3. 仍生效的未验证清单

- EPUB 的设备端行为：导入 → 书架 → 打开 → 目录跳转 → 杀进程 → 恢复（单测只覆盖 JVM 上的引擎与 UI 状态）。
- 真实 EPUB 兼容性：目前只在测试夹具（EPUB2 的 NCX 与 EPUB3 的 nav 各一套）与一份人造样本上验证过，**没有跑过真实出版文件**（含加密、脚注、图片密集的样本）。
- 性能门禁、release 构建、仪器测试：全部未做。

---

# 第四轮实测（阅读模式：左右翻页 / 上下滚动，2026-10-26）

本轮改动的是阅读页的交互骨架，不是引擎：引擎、目录、进度坐标都没动，所以前几轮针对引擎的结论继续有效。

## 1. 命令与结果

```bash
export JAVA_HOME=<workspace>/.toolchain/jdk21
export ANDROID_HOME=<workspace>/.toolchain
export ANDROID_USER_HOME=$ANDROID_HOME/android-home
export GRADLE_USER_HOME=$ANDROID_HOME/gradle-home
export JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=<workspace>/.toolchain/tmp

rm -rf app/build/test-results            # 统计前先清旧 XML，避免把上一轮的结果算进来
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug :app:lintDebug
```

结果：

```
BUILD SUCCESSFUL in 18s     # testDebugUnitTest
BUILD SUCCESSFUL in 1m 11s  # assembleDebug + lintDebug
```

- 单元测试 **136 个全通过，22 个测试类，failures=0 errors=0**。本轮新增：`ReaderGestureTest` 6（左滑翻下页、右滑翻上页、左 1/10 轻点翻上页、右 9/10 轻点翻下页、中间轻点不翻页、滚动模式下左右滑不翻页）、`ScrollChunkMathTest` 7（空书无块、整除不多算、余数多一块、偏移落块、越界夹紧、块号回算、百分比封顶）。
- `lintDebug`：**0 errors, 4 warnings**（`NewerVersionAvailable`×2、`GradleDependency`、`ObsoleteSdkInt` 误报），与上一轮完全一致。
- 产物：`app/build/outputs/apk/debug/app-debug.apk`，**12,215,495 字节（11.65 MiB）**，比 EPUB 那一版多 40 KB——只多了手势与分块代码，没有新增依赖、没有新增权限。

## 2. 本轮新增的坑

| 现象 | 根因 | 处理 |
|---|---|---|
| 翻页与滚动两张截图字节完全相同（md5 一致） | 滚动模式的截图喂的仍是「从 0 开始的同一段文字」，进度线取 `ready.fraction = 0f`，两种模式渲染出来真的一模一样 | 滚动截图改成「锚点在书中段」：`charCount = 24_000`、`anchorChar = 9_000`、`scrollFraction = 0.375f`，块内文字带块序号，两张图从内容到进度线都不同 |
| 想用两个 `pointerInput` 分别接点击与横向拖动 | `detectTapGestures` 与 `detectHorizontalDragGestures` 都要求拿到第一下触摸，会互相吞手势 | 手写 `awaitEachGesture`：同一次手势里累计位移，超阈值算滑动、小于 `24f` 算轻点，按落点分区 |
| 滚动模式的 `LazyColumn` 初版没有 `key` | 切块内容异步加载后列表会错位、复用错块 | `itemsIndexed(..., key = { index, _ -> index })`，每块 `remember(index)` + `LaunchedEffect(index)` 单独取文本，未加载时占位 240dp 防抖动 |

## 3. 仍生效的未验证清单

- 设备端手势手感（阈值 `56.dp` 是否合适、轻点分区是否顺手）、进出阅读页的转场动画（`slideInHorizontally(w/6)` 进入、整屏宽滑出退出）都**只在 JVM 与离屏截图上验证过**。
- 滚动模式在 100 MB 级 TXT 上的实际滚动流畅度与内存占用：分块与 LRU 缓存的设计目标是「只渲染看得见的块」，但**没有在真机上量过**。
- 性能门禁、release 构建、仪器测试：全部未做。

---

# 第五轮实测（界面重设计 + 阅读字体 + EPUB 插图，2026-10-27）

本轮是三条线合并验收：纸黑界面（书架/最近/设置/阅读四屏重写）、内置字体切换、EPUB 插图。三块都只动 UI 与解析层，引擎、目录、进度坐标的旧结论继续有效。

## 1. 命令与结果

```bash
export JAVA_HOME=<workspace>/.toolchain/jdk21
export ANDROID_HOME=<workspace>/.toolchain
export ANDROID_USER_HOME=$ANDROID_HOME/android-home
export GRADLE_USER_HOME=$ANDROID_HOME/gradle-home
export JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=<workspace>/.toolchain/tmp

rm -rf app/build/test-results app/build/screenshots
./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true
./gradlew :app:assembleDebug :app:lintDebug
```

结果：

```
BUILD SUCCESSFUL in 41s     # testDebugUnitTest（顺带产出 13 张截图）
BUILD SUCCESSFUL in 1m 32s  # assembleDebug + lintDebug
```

- 单元测试 **151 个全通过，23 个测试类，failures=0 errors=0**。本轮新增 15 例：`PageSlotsTest` 7（无图时一页一屏、图排在自己那页之后、书末的图追加不丢、同页多图按偏移排、空书只剩图屏、页码找回正文屏、越界退回首屏）、`HtmlTextExtractorTest` 新增 4（图不进正文但记位置与说明、`data:`/空 `src` 跳过、`svg` 内 `image` 不算、偏移不越界）、`EpubReaderEngineTest` 新增 3（图能从压缩包取出且路径/说明/偏移正确、不存在的路径返回 null 不抛、无图书返回空列表）、`ReaderScreenshotTest` 新增 1（整页插图版式）。
- `lintDebug`：**0 errors, 4 warnings**（`NewerVersionAvailable`×2、`GradleDependency`、`ObsoleteSdkInt` 误报），无新增。
- 产物：**18,052,810 字节（17.22 MiB）**，比上一版多 5.84 MB —— 就是内置楷体 `LXGWWenKaiLite-Regular.ttf`（9.75 MB，压进 APK 后约 5.8 MB）。这是目前包体里最大的一块，已在 README 与许可证台账里记账。

## 2. 本轮新增的坑

| 现象 | 根因 | 处理 |
|---|---|---|
| `HtmlTextExtractor.kt` 编译报 `Empty character literal` / `Incorrect character literal` | 用 `'''` 表示单引号字符、用裸私用区字符当占位符，写盘后这几处字面量都坏了 | 字符比较改码点（`quote.code == 0x22 || quote.code == 0x27`），占位符改 `0xE000.toChar()`——不依赖任何转义序列 |
| 插图两侧多出空行 | 占位符后面紧跟块级标签，`breakLine()` 只认 `\n` 结尾，看到占位符就再补一个换行 | `breakLine()` 把占位符也当作「已换行」 |
| 引擎 `pictures` 恒为空 | `EpubIndexer` 根本没用 `extracted.images`（提取层做了、索引层没接） | 在压平循环里解析 `src` → 压缩包路径并换算全书偏移 |
| 测试断言「图的偏移 = 1」 | 自己写错：`ref.at` 是 150，`afterPage` 才是 1 | 断言改成 150 |

## 3. 仍生效的未验证清单

- **真实 EPUB 的插图**没有跑过（测试里的图是现造的 1×1 PNG）：真实书里的 `srcset`、`<picture>`、SVG 封面、灰阶 JPEG 都没覆盖；`<picture>` 只取 `<img>` 的 `src`。
- 字体切换的手感、宋体/等线/打字机在各厂商 ROM 上是否真的存在（系统字体族缺失时会回落默认），只在 JVM 截图上验证过。
- 性能门禁、release 构建、仪器测试：全部未做。

# 第六轮实测（真实 EPUB 插图不显示的三个根因修复，2026-10-27）

用户反馈：真实书籍「书能打开、字能读，但一张图都没有」。合成 fixture 全部通过，说明测试覆盖的是理想写法。
本轮按真实 EPUB 的三种常见写法逐条修复，并补回归用例。

## 1. 命令与结果

```
./gradlew --offline app:testDebugUnitTest app:lintDebug app:assembleDebug
```

- 单元测试 **159 个全通过，24 个测试类，failures=0 errors=0**。本轮新增/改写 8 例：
  - 新增 `EpubImageRegressionTest` 5 例：`<svg><image xlink:href>` 被识别、zip 条目大小写不一致仍能取到字节、OPF `cover-image` 成为第一张图且偏移 0、正文已引封面时不重复插、EPUB2 `<meta name="cover">` 能找到封面；
  - 改写 `HtmlTextExtractorTest` 2 例：原先断言的「svg 整段跳过」「svg 内 image 不算插图」正是 bug 本身，改成「svg 里的矢量文字不进正文，但 image 必须被识别」；
  - 新增 `PageSlotsTest` 1 例：偏移 0 的封面排在第一页正文之前（`BEFORE_FIRST_PAGE = -1`）。
- `lintDebug`：**0 errors, 4 warnings**，无新增。
- 产物：**18,146,084 字节（17.31 MiB）**，比上一版多 93,274 字节（新增代码与资源，无新依赖）。

## 2. 本轮新增的坑

| 现象 | 根因 | 处理 |
|---|---|---|
| 真实书里插图全不显示，合成 fixture 全绿 | `SKIP_TAGS` 里含 `svg`，而 EPUB3 封面/插图普遍写成 `<svg><image xlink:href="…"/></svg>`，整棵 svg 被跳过，图也被跳过 | 从 `SKIP_TAGS` 移除 `svg`，改为只跳过 svg 里的 `<text>` |
| `xlink:href` 取不到值 | `attribute()` 要求属性名前一个字符是空白，`xlink:href` 前面是冒号 | 属性名边界加上 `:` |
| 封面明明在 OPF 里却不显示 | 封面通常不被任何正文 XHTML 引用，索引器只从正文找图 | `EpubPackageParser` 读 `cover-image` / `meta cover` / guide，`EpubIndexer` 补一张 `at = 0` 的图并去重 |
| 大小写不一致时读不到图 | `ZipPaths` 只做精确匹配，`COVER.JPG` 与 `Cover.jpg` 不等 | `EpubArchive` 增加小写兜底表 |
| 小写兜底表永远是空的，且不报错 | `buildMap { entries.forEach { … } }` 里的 `entries` 被解析成正在构建的那张空表（lambda 接收者的成员遮蔽了类成员） | 显式写 `this@EpubArchive.entries` |

## 3. 仍生效的未验证清单

- 修复只在合成 fixture 上验证：真实书里的 `srcset`、`<picture>`、扫描版整页图还没跑过（`<picture>` 只取内层 `<img>` 的 `src`）。
- `EpubLimits.MIN_PICTURE_BYTES`（6 KB 小图标过滤）目前**未启用**：宁可把一枚装饰图标画成一页，也不想因为体积阈值丢掉真正的插图。这个取舍是临时的。
- 性能门禁、release 构建、仪器测试：全部未做。

---

# 第七轮实测（PDF 引擎，2026-10-27）

PDF 是 P0 最后一个格式。这一轮没有引入任何新依赖：用平台的 `android.graphics.pdf.PdfRenderer`（API 21+，minSdk 26 直接可用），因此 `gradle/libs.versions.toml` 一行未改。

## 1. 命令与结果

```
./gradlew --offline app:testDebugUnitTest app:lintDebug app:assembleDebug
```

- 单元测试 **171 个全通过，26 个测试类，failures=0 errors=0**。本轮新增 12 例：
  - 新增 `PdfPageMathTest` 8 例（纯算术，跑在 JVM 上）：按屏宽等比算位图尺寸、宽度夹到 2400px 上限、总像素夹到 400 万、比例尺不低于 0.05、非法尺寸（0 宽、负高）不抛异常而是回退、页码夹紧到 `[0, count-1]`、空文档返回 0 页。
  - 新增 `PdfReaderScreenshotTest` 2 例（Robolectric 原生图形 + Roborazzi）：翻页模式一页铺满 `16-reader-pdf-light.png`、滚动模式连续页 `17-reader-pdf-scroll.png`。真实的 `PdfRenderer` 在 JVM 里没有实现，所以截图喂的是自绘的 A4 位图——**验证的是版式，不是解码**。
  - 另有 2 例改动在既有文件里：`ReaderScreenshotTest` 的「引擎未接入」用例改成 PDF 占位文案，`reader_unsupported` 只剩非三种格式会走到。
- `lintDebug`：**0 errors, 4 warnings**（`GradleDependency` ×1、`NewerVersionAvailable` ×2、`ObsoleteSdkInt` ×1），无新增类型。
  - 中途被 lint 抓到两条新问题，都已修：`PdfReaderScreen` 里在 composition 直接读 `firstVisibleItemIndex`（`FrequentlyChangingValue`）改成快照状态收集；`Bitmap.createBitmap(...)`（`UseKtx`）改成 `androidx.core.graphics.createBitmap(...)`。
- 产物：**18,146,505 字节（17.31 MiB）**，比上一版只多 421 字节（全是代码，无新资源、无新依赖）。

## 2. 本轮设计上的坑与取舍

| 现象 / 风险 | 处理 |
|---|---|
| `PdfRenderer` 不是线程安全的，两个协程同时 `openPage` 会抛 `IllegalStateException` | 引擎内 `Mutex` 串行化渲染；UI 侧的预渲染也走同一个 `renderPage` 入口 |
| 一页位图可能几 MB，来回翻页反复解码很慢 | 3 张的 `LinkedHashMap` LRU（`accessOrder = true`），只留「上一页 / 当前页 / 下一页」 |
| 大屏 / 平板按屏宽算出来 3000px 宽会 OOM | `PdfPageMath` 双重夹紧：宽度 ≤ 2400px、总像素 ≤ 400 万；两者取小后再算比例尺 |
| PDF 页面本身没有背景色，深色主题下是黑的 | 渲染前先把位图清成 `Color.WHITE`，保证纸面永远是纸面 |
| 滚动模式里页眉不知道该说第几页 | `snapshotFlow { listState.firstVisibleItemIndex }` 汇报可见页，退出时 `onDispose` 才 flush 页码落库（不每滚一像素写一次 Room） |
| PDF 没有大纲 | P0 按既定决策只做页码跳转：`loadOutline()` 直接产出「第 N 页」条目，目录抽屉即页码表 |
| 加密 / 损坏 / 非 PDF 文件 | `PdfRenderer` 构造抛异常统一映射成 `ReaderException.Reason.CORRUPTED`，界面给可读提示而不是崩 |

## 3. 仍生效的未验证清单

- **设备端完全没跑过**：`PdfRenderer` 只能在真机/模拟器上用，本机模拟器起不来（见第 3 节），所以「打开真实 PDF、翻页、跳页码、杀进程恢复」全靠代码审查与 JVM 上的算术单测，属于**未验证**。
- 扫描版大 PDF（50 MB 级）的首开耗时与内存峰值：未测量。
- 缩放 / 双指手势、文本层抽取、PDF 内链接、大纲：P0 范围外，未做。
- release 构建与混淆、仪器测试：仍未做。

---

# 第八轮实测（书架封面 + 删除打字机字体 + 退出动画右滑 + 真实 EPUB 验收，2026-10-27）

## 1. 命令与结果

```bash
rm -rf app/build/test-results
./gradlew --offline app:testDebugUnitTest app:lintDebug app:assembleDebug
```

- **BUILD SUCCESSFUL**（clean 之后首跑约 1 分钟，增量 24s）。
- 单元测试：**178 个 / 27 个类，0 失败**（`app/build/test-results/testDebugUnitTest/*.xml` 聚合统计）。比上一轮 171 个多 7 个：`CoverStoreTest` 5 例、`EpubPackageParserTest` 新增 meta cover 两例（按 id 解析、id 指向不存在项不崩）；同时把 `readerTypewriterFont` 换成 `readerDengFont`，截图数不变。
- `lintDebug`：**0 errors / 4 warnings**（`GradleDependency`×1、`NewerVersionAvailable`×2、`ObsoleteSdkInt`×1）。修掉了本轮新引入的两条：`ModifierParameter`（`ShelfScreen` 的 `modifier` 提到第一个可选参数位）、`UseKtx`（`CoverStore` 里 `Bitmap.createScaledBitmap` 换成 `decoded.scale`）。
- 产物：**18,137,290 字节（17.30 MiB）**，比 PDF 那版少 9,215 字节（删掉打字机分支与若干代码，无新资源）。

### 真实 EPUB 验收（用户提供的样本）

样本：`恋入膏肓 (斜线堂有纪).epub`，349,148 字节、18 个 ZIP 条目、**全书只有 1 张图**（`OEBPS/Images/9784049130829_1_7.png`，198,124 字节），OPF 用 EPUB2 的 `<meta name="cover" content="x9784049130829_1_7.png"/>`，其中 content 是 manifest 的 **item id** 而不是 href；这张图不被任何 XHTML/CSS 引用，纯封面。

用临时探针（`RealBookAcceptanceProbe`，验证后删除，样本不进军仓库）跑出的真实输出：

```
PROBE coverPath=...\book.cover.jpg
PROBE coverBytes=50667
PROBE coverSize=400x568
PROBE engine title=恋入膏肓 chars=110281
PROBE pictures=1 first=OEBPS/Images/9784049130829_1_7.png
PROBE pictureBytes=198124
PROBE pictureDecoded=843x1200
```

结论：**封面抽取 → 400px JPEG 落盘 → 书架渲染**与**插图识别 → 解码**两条链路在这本真书上闭环；截图 `01-shelf-list-light.png` / `02-shelf-grid-light.png` 里的深墨绿封面就是用同一套 `CoverStore` 走的假封面，版式与真实封面一致。

## 2. 本轮设计上的坑与取舍

| 现象 / 风险 | 处理 |
|---|---|
| 封面存哪：单独建一张表还是跟文件走 | 与书同目录派生命中 `<书名>.epub` → `<书名>.cover.jpg`，删书时导出路径即可一并删，不引入索引表与额外迁移 |
| 封面解码内存：原图 843×1200 直接进书架会反复解码 | `inJustDecodeBounds` 先量宽 → 2 的幂 `inSampleSize` 降采样 → 仍超目标再 `scale` 到宽 400px；`load()` 外层再挂 24 条 LRU |
| 抽封面失败会不会拖垮导入 | `extractFromEpub` 捕 `IOException`/`RuntimeException` 一律返回 null，导入继续，书架回落纸面占位 |
| 老书（本轮之前导入的）没有 `coverPath` | `LibraryViewModel.init` 里 `backfillCovers()` 只对 `format == EPUB && coverPath == null` 的书补抽，且放在仓库层 `observeShelf().first()` 之后逐本处理 |
| EPUB2 的 meta cover 的 content 是 manifest id，且 id 常长得像文件名 | 先按 id 查 manifest；查不到再当 href 相对 OPF 目录拼接。单测里刻意让 id（`cover-x1`）与文件名（`Images/cover.png`）不同 |
| Robolectric 里 `Paint.measureText` 返回 0 | 截图用的假封面只 `drawText` 不量宽度；真机不受影响 |
| 退出动画原来带 `scaleOut`，像被"弹"回去 | 只保留 `slideOutHorizontally`（260ms）+ 40ms 延后的 `fadeOut`；`popEnter` 从 `-width/14` 滑回，去掉缩放 |
| 阅读页退出方向 | 统一向右滑出（`targetOffsetX = width`），与"翻回书架"的心理模型一致；系统返回键与界面返回共用同一转场 |

## 3. 仍生效的未验证清单

- **设备端完全没跑过**：书架封面的真机解码耗时、滚动列表里 `produceState` 的复用行为、退出动画在低端机上的掉帧，都属于**未验证**（模拟器启动问题见第 3 节）。
- 抽封面在「封面是 WebP / 带 alpha 的 PNG / CMYK JPEG」上的表现：只在本轮这一本真实书上验证过解 PNG。
- 扫描版大 PDF、竖排 EPUB、CSS 复杂排版的插图位置：未测量。
- release 构建与混淆、仪器测试：仍未做。

# 第九轮实测（合集类 EPUB 的正文插图修复，2026-10-27）

## 1. 命令与结果

```bash
rm -rf app/build/test-results
./gradlew --offline app:testDebugUnitTest app:lintDebug app:assembleDebug
```

- **BUILD SUCCESSFUL**（增量 31s）。
- 单元测试：**187 个 / 28 个类，0 失败**。比上一轮 178 个多 9 个：`ImageHeadersTest` 6 例（PNG / JPEG / GIF / WebP / 未知格式 / 截断数据）、`EpubImageRegressionTest` 新增 3 例（纯图页仍出图、40×40 装饰被跳过、同一路径只记一次）。
- `lintDebug`：**0 errors / 4 warnings**（`GradleDependency`×1、`NewerVersionAvailable`×2、`ObsoleteSdkInt`×1），无新增。
- 产物：**18,197,763 字节（17.35 MiB）**。

## 2. 真实样本验收（《全知读者视角》1-10 合集，10.5 MB / 268 条目）

临时探针（跑完即删）输出：

```
PROBE title=全知读者视角 chars=2084452
PROBE chapters=156 first=cover
PROBE pictures=52
PROBE pic at=6 bytes=339592 dims=1400x1986 alt=cover cover.jpg
PROBE pic at=16 bytes=339592 dims=1400x1986 alt=null 1/OEBPS/Images/cover.jpg
PROBE pic at=34 bytes=151699 dims=1400x1963 alt=null 1/OEBPS/Images/titlepage.jpg
PROBE pic at=43 bytes=145295 dims=1400x1963 alt=null 1/OEBPS/Images/content.jpg
PROBE unreadable=0 of 52
```

修之前同一本书：`chapters=94`、`pictures=4`（只有封面 + 3 张 40×40 装饰方块），即 41 张真插图全部丢失。

## 3. 本轮新增的坑

| 现象 | 根因 | 处理 |
|---|---|---|
| 整页插图的图全都不见 | `EpubIndexer` 用「正文为空就跳过整章」判断，而纯图页的正文恰好是空串 | 改成「正文为空**且**没有图片」才跳过；纯图页保留，图与锚点都在 |
| 插图列表被装饰图占满 | 合集类 EPUB 把同一张 `star.png`（3.5 KB）在每章重复引用 1125 次，`MAX_PICTURES=600` 直接被装饰图占满 | 按路径去重，一张图只占一页；同时按体积（< 6 KB）与像素边长（< 96）判定装饰 |
| 40×40 方块 JPEG 有 9 KB，按体积筛不掉 | 体积与"是不是插图"没有稳定关系 | 新增 `ImageHeaders` 读 PNG/JPEG/GIF/WebP 文件头拿宽高，索引阶段不解码位图 |
| `readBytesIfPresent` 读图片头直接抛「内部文件过大」 | 它遇到条目声明体积超过 `maxBytes` 就抛 `TOO_LARGE`（设计给"整文件读取"用） | 新增 `EpubArchive.readPrefixIfPresent`：只读前 N 字节，不因文件大而失败 |
| 旧测试用 1×1 PNG 当插图，加了尺寸筛之后全挂 | 测试 fixture 需要跟着生产规则走 | `EpubFixtures.bigPng(width, height, size)` 造 600×800、≥ 6 KB 的 PNG 头 |

## 4. 仍生效的未验证清单

- 设备端仍未跑过：52 张 1400px 宽的插图纸在真机上翻页的解码耗时只能靠 LRU 与降采样兜底，未实测。
- 扫描版 PDF、竖排 EPUB、CSS 背景图（`background-image`）里的插图：仍未支持，也未测试。


## 第十轮实测（正文占位符与插图错位修复，2026-10-27）

用户反馈「正文里出现 cover / titlepage 之类的占位符文字但没有图，封面图却插到第二页」，用同一本合集样本复现并修复。

### 1. 命令与结果

```bash
export JAVA_HOME=.../jdk21 ANDROID_HOME=.../.toolchain ANDROID_USER_HOME=.../android-home
./gradlew --offline app:testDebugUnitTest app:lintDebug app:assembleDebug
# BUILD SUCCESSFUL
# tests=190 failures=0（28 个类）
# lint: 0 errors / 4 warnings
# APK: 18,198,187 字节（17.36 MiB）
```

### 2. 真实样本探针（修复后，临时测试文件已删除）

```
PROBE chars=2082229 chapters=94 pictures=52
PROBE titles=[Prologue. 在灭亡的世界中存活的三种方法, 第 2 节, Episode 1. 付费服务开始, ...]
PROBE pics=[(0, cover.jpg), (0, titlepage.jpg), (0, content.jpg), (210060, DM.jpg), ...]
PROBE head=Prologue. 在灭亡的世界中存活的三种方法
想要在灭亡的世界中存活下来，有三种方法。...
PROBE junk={titlepage=false, cover.xhtml=false, content.xhtml=false, chapter1=false}
```

### 3. 根因与处理

| 现象 | 根因 | 处理 |
|---|---|---|
| 正文里出现 `cover` / `titlepage` 字样 | 纯图页正文为空，早期版本会补一行「文件名当标题」当正文，图却被锚在那行之后 | 纯图页只记图片、不写标题、不建章节；图锚在当时的全书偏移上 |
| 目录/正文出现 `ch1`、`9` 这种名字 | `EpubOutlineParser` 的 spine 兜底用 `ZipPaths.stemOf(href)` 当标题 | 兜底标题改成「第 N 节」，文件名不进目录也不进正文 |
| 同一句标题连出现两次 | XHTML 首行 `<h2>` 与 NCX 标题相同，两边都拼进正文 | 首行与目录标题完全一致时丢掉正文那一行，并同步扣减图片偏移 |
| 封面图看起来"跑到第二页" | 上面两条叠加：第一页是一条假标题，图被排到它后面 | 同上；修复后纯图页在开头连续成页，正文从第一章开始 |

### 4. 仍生效的未验证清单

- 设备端仍未跑过；52 张插图纸在真机上的解码耗时只能靠 LRU 与降采样兜底。
- EPUB 自带字号/颜色/粗斜体：P0 不解析（纯文本 + 统一排版），属 P1 的排版模型改造，已在 `docs/architecture.md` 写明代价。
