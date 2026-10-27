# 第三方依赖与许可证清单

本文件是**合规证据台账**。规则：先取证，再引入；未列入此表的依赖不得进入构建。

- 主许可证：Apache-2.0（[LICENSE](../LICENSE)）
- 判定原则：只接受 Apache-2.0 / MIT / BSD；**拒绝 AGPL、SSPL、GPL**；LGPL / MPL 需单独评估并记录结论；许可证不明视为拒绝。
- 取证方式：Maven POM 的 `<licenses>` 块、AAR 内 `META-INF/`、上游仓库 `LICENSE`。证据必须是一个可复核的 URL。

## 一、已确认引入（阶段 2，构建已通过）

`implementation` 的依赖会打进 APK；`testImplementation` / `ksp` 只在开发或构建期使用（KSP 是编译器插件，不进 APK）。

| 名称 | 坐标 / 版本 | 许可证 | 取证 | 作用域 |
|---|---|---|---|---|
| AndroidX Core KTX | `androidx.core:core-ktx:1.19.1` | Apache-2.0 | POM `<licenses>` 已直接核实 | implementation |
| Compose BOM | `androidx.compose:compose-bom:2026.09.00` | Apache-2.0 | BOM 仅版本对齐；其下 ui / material3 同属 AndroidX | implementation |
| Compose UI / Graphics / Tooling Preview | 由 BOM 定版（实测 `ui:1.11.0`） | Apache-2.0 | `ui:1.11.0` POM 已直接核实 | implementation |
| Compose Material 3 | 由 BOM 定版 | Apache-2.0 | 与 ui 同源（AndroidX），未单独取证 | implementation |
| Activity Compose | `androidx.activity:activity-compose:1.13.0` | Apache-2.0 | AndroidX 统一许可，未单独取证 | implementation |
| Lifecycle Runtime/ViewModel Compose | `androidx.lifecycle:*:2.11.0` | Apache-2.0 | 同上 | implementation |
| Navigation Compose | `androidx.navigation:navigation-compose:2.10.2` | Apache-2.0 | POM 已直接核实（`The Apache Software License, Version 2.0`） | implementation |
| Room Runtime / KTX | `androidx.room:room-runtime:2.8.5`、`room-ktx:2.8.5` | Apache-2.0 | POM 已直接核实 | implementation |
| Room Compiler | `androidx.room:room-compiler:2.8.5` | Apache-2.0 | POM 已直接核实 | ksp（构建期，不进 APK） |
| KSP Gradle 插件 / symbol-processing-api | `com.google.devtools.ksp:2.3.12` | Apache-2.0 | POM 已直接核实（插件页与 api 两处均为 `The Apache License, Version 2.0`） | 构建期插件 |
| org.jspecify:jspecify | `org.jspecify:jspecify:1.0.0`（`androidx.annotation` 传递依赖） | Apache-2.0 | POM 已直接核实（1.0.0 / 1.0.1 两版均为 `The Apache License, Version 2.0`） | implementation（编译期注解，运行时无行为） |
| kotlinx-coroutines-android | `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0` | Apache-2.0 | POM 已直接核实 | implementation |
| Kotlin Stdlib | 由 Kotlin 插件注入（**2.3.20**） | Apache-2.0 | JetBrains 统一许可 | implementation |
| JUnit 4 | `junit:junit:4.13.2` | **EPL-1.0** | POM 已直接核实 | **testImplementation**（不进 APK） |
| kotlin-test (JUnit) | `org.jetbrains.kotlin:kotlin-test-junit:2.3.20` | Apache-2.0 | POM 已直接核实 | testImplementation |
| Robolectric | `org.robolectric:robolectric:4.17` | **MIT** | POM 已直接核实（`The MIT License`，https://robolectric.org） | **testImplementation**（JVM 上跑 Android 资源/图形，不进 APK） |
| Roborazzi（roborazzi / roborazzi-compose） | `io.github.takahirom.roborazzi:roborazzi:1.77.0`、`roborazzi-compose:1.77.0` | Apache-2.0 | POM 已直接核实 | **testImplementation**（离屏截图，不进 APK） |
| Compose UI Test（ui-test-junit4 / ui-test-manifest） | 随 Compose BOM `2026.09.00` | Apache-2.0 | Google 官方 androidx 组件 | testImplementation / debugImplementation（不进 release APK） |

### 随应用分发的非代码资源

| 名称 | 来源 / 版本 | 许可证 | 取证 | 位置 |
|---|---|---|---|---|
| LXGW WenKai Lite（霞鹜文楷 Lite） | https://github.com/lxgw/LxgwWenKai-Lite ，Release v1.245（`LXGWWenKaiLite-Regular.ttf`，9,752,448 字节，sha256 `f68f9364…`） | **SIL OFL-1.1** | 上游 `OFL.txt` 全文随包保存于 `app/src/main/assets/licenses/OFL-LXGWWenKaiLite.txt`（4,442 字节）；OFL 允许随应用一起分发，要求保留许可证与版权声明、不得单独售卖字体本身 | `app/src/main/res/font/lxgw_wenkai_lite.ttf`（Android 资源目录，`R.font.lxgw_wenkai_lite`） |

> 字体是**资源**不是依赖：不进 Maven 依赖树，但会因为体积与许可证义务被单独记账。OFL-1.1 不是 copyleft，不要求应用本体开源；但它要求衍生字体（改名、改造字形）继续用 OFL，因此本应用**不修改字形**，只原样嵌入。

> Kotlin 定在 **2.3.20** 而非最新的 2.4.x：KSP 当前最新版 `2.3.12` 是面向 Kotlin 2.3.20 构建的，Maven Central 上不存在 `2.4.x` 版本的 KSP。用 2.4.10 时 `:app:kspDebugKotlin` 直接 `ExceptionInInitializerError`。Room 需要 KSP，所以 Kotlin 跟着 KSP 走；等 KSP 跟进 2.4 再升。`lintDebug` 里 3 条 `NewerVersionAvailable` 警告就是这个已知取舍，**不是遗漏**。

> EPL-1.0 说明：JUnit 4 是弱 copyleft，但仅作开发期测试框架、不随产物分发，因此不影响 Apache-2.0 主许可证与对外分发。已限定为 `testImplementation`。若需更干净的许可证面，可换 TestNG（Apache-2.0）。

> **PDF 没有第三方依赖**：PDF 渲染用平台的 `android.graphics.pdf.PdfRenderer`（AOSP，Apache-2.0，随 Android 系统提供，不打包进 APK）。P0 不引 PdfBox-Android，理由见「三、已评估并放弃引入」。

## 二、计划引入（引入时逐项复核版本与传递依赖）

| 名称 | 用途 | 预期许可证 | 传递依赖风险 |
|---|---|---|---|
| DataStore (Preferences) | 外观设置持久化 | Apache-2.0 | 低 |
| androidx.test（ext:junit / runner） | 仪器测试 | Apache-2.0 | 仅 androidTest，不进 release |

> 注：版本号在 `gradle/libs.versions.toml` 落地后回填到本表，并补上 POM 证据链接。Room + KSP 已从本表转入「已确认引入」。

## 三、已评估并**放弃**引入

记录放弃理由，防止以后重复踩坑。

| 候选 | 许可证 | 结论 | 理由 |
|---|---|---|---|
| Readium Kotlin Toolkit (`org.readium.kotlin-toolkit:readium-shared:3.1.1`) | **BSD-3-Clause**（POM 已核实） | 不用（P0） | 功能强但以 WebView 排版为核心，包体与冷启动/内存代价高；本项目 P0 只需基础渲染 + 目录，改自研最小解析以完全掌控分页、进度与主题 |
| PdfBox-Android (`com.tom-roush:pdfbox-android:2.0.27.0`) | **Apache-2.0**（POM 已核实） | 不用（P0） | 只为读 PDF 书签大纲不值得：其 POM 依赖 `org.bouncycastle:bcprov/bcpkix/bcutil-jdk15to18:1.72` 三件套（Bouncy Castle 自有宽松许可，需署名，体积数 MB 且反射多、R8 难裁）。P0 改为「页码 + 页面跳转」，大纲留待 P1 重新评估 |
| juniversalchardet | **MPL-1.1** | 不用 | 弱 copyleft，且 TXT 编码识别用 BOM + UTF-8 严格校验 + GBK/GB18030 回退即可覆盖绝大多数场景 |
| ICU4J | Unicode/ICU 许可 | 暂不 | 仅当实测确认平台 `Charset` 不支持 GB18030 时才评估，届时单独记录合规结论 |

## 四、待办

- [x] 阶段 2：Room / KSP / Navigation 版本落地，POM 许可证已逐条核实并回填本节
- [x] 跑过 `:app:dependencies --configuration debugRuntimeClasspath`：实测 **119 个 artifact**，除表中已列项外只有 `org.jspecify:jspecify`（已补入上表）；无 AGPL/GPL、无 BouncyCastle、无 Guava
- [ ] 发布前：生成 `THIRD-PARTY-NOTICES` 的最终版并打进 APK 的 `META-INF/`（或提供应用内「开源许可」页）
