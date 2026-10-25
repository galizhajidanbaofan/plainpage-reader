# 第三方依赖与许可证清单

本文件是**合规证据台账**。规则：先取证，再引入；未列入此表的依赖不得进入构建。

- 主许可证：Apache-2.0（[LICENSE](../LICENSE)）
- 判定原则：只接受 Apache-2.0 / MIT / BSD；**拒绝 AGPL、SSPL、GPL**；LGPL / MPL 需单独评估并记录结论；许可证不明视为拒绝。
- 取证方式：Maven POM 的 `<licenses>` 块、AAR 内 `META-INF/`、上游仓库 `LICENSE`。证据必须是一个可复核的 URL。

## 一、已确认引入（阶段 1 骨架，构建已通过）

`implementation` 的依赖会打进 APK；`testImplementation` 只在开发/CI 上跑，**不随 APK 分发**。

| 名称 | 坐标 / 版本 | 许可证 | 取证 | 作用域 |
|---|---|---|---|---|
| AndroidX Core KTX | `androidx.core:core-ktx:1.19.1` | Apache-2.0 | POM `<licenses>` 已直接核实 | implementation |
| Compose BOM | `androidx.compose:compose-bom:2026.09.00` | Apache-2.0 | BOM 仅版本对齐；其下 ui / material3 同属 AndroidX | implementation |
| Compose UI / Graphics / Tooling Preview | 由 BOM 定版（实测 `ui:1.11.0`） | Apache-2.0 | `ui:1.11.0` POM 已直接核实 | implementation |
| Compose Material 3 | 由 BOM 定版 | Apache-2.0 | 与 ui 同源（AndroidX），未单独取证 | implementation |
| Activity Compose | `androidx.activity:activity-compose:1.13.0` | Apache-2.0 | AndroidX 统一许可，未单独取证 | implementation |
| Lifecycle Runtime/ViewModel Compose | `androidx.lifecycle:*:2.11.0` | Apache-2.0 | 同上 | implementation |
| Navigation Compose | `androidx.navigation:navigation-compose:2.10.2` | Apache-2.0 | 同上 | implementation |
| kotlinx-coroutines-android | `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0` | Apache-2.0 | POM 已直接核实 | implementation |
| Kotlin Stdlib | 由 Kotlin Gradle 插件注入（2.4.10） | Apache-2.0 | JetBrains 统一许可 | implementation |
| JUnit 4 | `junit:junit:4.13.2` | **EPL-1.0** | POM 已直接核实 | **testImplementation**（不进 APK） |
| kotlin-test (JUnit) | `org.jetbrains.kotlin:kotlin-test-junit:2.4.10` | Apache-2.0 | POM 已直接核实 | testImplementation |

> EPL-1.0 说明：JUnit 4 是弱 copyleft，但仅作为开发期测试框架使用、不随产物分发，因此不影响本项目 Apache-2.0 主许可证与对外分发。已在 `app/build.gradle.kts` 中限定为 `testImplementation`，若未来需要更干净的许可证面，可换 TestNG（Apache-2.0）。

## 二、计划引入（引入时逐项复核版本与传递依赖）

| 名称 | 用途 | 预期许可证 | 传递依赖风险 |
|---|---|---|---|
| Room + KSP | 书目 / 进度持久化 | Apache-2.0 | KSP 为 Apache-2.0，需再次核对 |
| DataStore (Preferences) | 外观设置 | Apache-2.0 | 低 |
| kotlinx-serialization（可选） | 进度 locator 序列化 | Apache-2.0 | 若不需要则不引 |
| androidx.test（ext:junit / runner） | 仪器测试 | Apache-2.0 | 仅 androidTest，不进 release |

> 注：以上为「计划」，版本号在 `gradle/libs.versions.toml` 落地后回填到本表，并补上 POM 证据链接。

## 三、已评估并**放弃**引入

记录放弃理由，防止以后重复踩坑。

| 候选 | 许可证 | 结论 | 理由 |
|---|---|---|---|
| Readium Kotlin Toolkit (`org.readium.kotlin-toolkit:readium-shared:3.1.1`) | **BSD-3-Clause**（POM 已核实） | 不用（P0） | 功能强但以 WebView 排版为核心，包体与冷启动/内存代价高；本项目 P0 只需基础渲染 + 目录，改自研最小解析以完全掌控分页、进度与主题 |
| PdfBox-Android (`com.tom-roush:pdfbox-android:2.0.27.0`) | **Apache-2.0**（POM 已核实） | 不用（P0） | 只为读 PDF 书签大纲不值得：其 POM 依赖 `org.bouncycastle:bcprov/bcpkix/bcutil-jdk15to18:1.72` 三件套（Bouncy Castle 自有宽松许可，需署名，体积数 MB 且反射多、R8 难裁）。P0 改为「页码 + 页面跳转」，大纲留待 P1 重新评估 |
| juniversalchardet | **MPL-1.1** | 不用 | 弱 copyleft，且 TXT 编码识别用 BOM + UTF-8 严格校验 + GBK/GB18030 回退即可覆盖绝大多数场景 |
| ICU4J | Unicode/ICU 许可 | 暂不 | 仅当实测确认平台 `Charset` 不支持 GB18030 时才评估，届时单独记录合规结论 |

## 四、待办

- [ ] 阶段 1：`libs.versions.toml` 落地后，把「计划引入」逐条转成「已确认引入」并补 POM 证据
- [ ] 阶段 1：跑一次 `./gradlew :app:dependencies`，确保依赖树里每个 artifact 都出现在本表
- [ ] 发布前：生成 `THIRD-PARTY-NOTICES` 的最终版并打进 APK 的 `META-INF/`（或提供应用内「开源许可」页）
