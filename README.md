# 简页 · PlainPage

> 简页 · 离线 EPUB / TXT / PDF 阅读器

一个**完全离线**、UI 可切换、面向大文件的 Android 阅读器。Kotlin + Jetpack Compose，Apache-2.0。

| 项目 | 值 |
|---|---|
| 应用显示名 | 简页 |
| 英文名 / 仓库名 | PlainPage / `plainpage-reader` |
| Android 包名 | `io.github.galizhajidanbaofan.plainpage` |
| 最低版本 | Android 8.0（minSdk 26） |
| 主许可证 | Apache-2.0（见 [LICENSE](LICENSE)） |
| 网络权限 | **不声明** `android.permission.INTERNET`，飞行模式下功能完整 |

---

## 状态（务必先读）

**本项目处于阶段 0 / 阶段 1 之间：仓库骨架已建立，应用代码尚未落地，从未构建过。**
下表是诚实的进度记录，任何一格写 `未验证` 就意味着——不要相信它。

| 能力 | 代码 | 构建 | 真机验收 |
|---|---|---|---|
| 仓库骨架 / 许可证 / 文档 | 已有 | — | — |
| 构建体系（Gradle / AGP / 版本目录） | 未开始 | 未验证 | — |
| 书架 / 导入 / 三格式打开 | 未开始 | 未验证 | 未验证 |
| 进度记忆 / 目录跳转 | 未开始 | 未验证 | 未验证 |
| 性能目标（冷启动 <2s、大文件不 ANR） | 未开始 | — | 未测量 |

当前阻塞：构建机缺 JDK / Android SDK / adb，导致「构建通过」「不 ANR」这类门禁**无法在本机验证**。详见 [docs/environment.md](docs/environment.md)（建设中）。

---

## 目标范围

### P0（第一个可用版本）

1. **书架**：导入、展示、删除记录；显示标题 / 封面或占位 / 格式 / 最近阅读时间。
2. **最近阅读**：按最近打开时间排序，一键续读。
3. **导入**：SAF 文件选择器（EPUB / TXT / PDF），不申请 `MANAGE_EXTERNAL_STORAGE`，持久化 URI 授权，**默认复制到应用私有目录**并记录原始来源。
4. **打开与目录**：三种格式均可打开、基础渲染、章节目录、点击跳转。
5. **进度记忆**：按书籍保存位置 —— EPUB 用 locator（href + 章节内偏移/百分比），PDF 用页码，TXT 用字符偏移。
6. **异常处理**：损坏文件、编码异常、权限丢失、空目录、超大文件，都要有人话提示而不是崩溃。

### P1

页面设置（字体 / 字号 / 行距 / 页边距 / 对齐 / 主题 / 翻页或滚动 / 亮度）、书架列表与网格切换、阅读主题切换（浅色 / 深色 / 护眼）、阅读模式切换（翻页 / 滚动）。

### 明确不做

OCR、PDF 文本选择与批注、DRM、账号、云同步、网络请求、TTS、翻译、插件系统；不申请危险存储权限，不删除用户原文件，不上传任何数据。

---

## 设计要点（计划）

```
UI (Compose)  ──按配置注入──▶  ThemeTokens / LayoutMode / 书架布局
      │
      ▼
ReaderEngine（统一接口：open / loadOutline / restoreProgress / saveProgress / render / close）
      ├── TxtReaderEngine    自研编码识别 + 分章
      ├── EpubReaderEngine   自研最小 EPUB2/3 解析（ZIP + OPF spine + NCX/nav）
      └── PdfReaderEngine    Android PdfRenderer 渲染（P0 不含大纲）
      │
      ▼
Repository（Room 存书目与进度，DataStore 存外观设置，文件在私有目录）
```

- **UI 可切换是配置，不是 if 分支**：外观/布局通过 `AppearanceRepository` + `ThemeTokens` + `LayoutMode` 注入，业务层不认识主题。
- **主线程只做 UI**：所有 IO / 解析 / 渲染准备都在 Dispatchers.IO 或专用单线程调度器上，大文件流式读取、按章加载、缓存有上限。
- **EPUB 与 PDF 都不引重依赖**：EPUB 自研解析（避免 WebView 排版与包体代价），PDF 用平台 `PdfRenderer`；P0 不引 PdfBox（其依赖链会带进 BouncyCastle 三件套）。

---

## 构建

依赖 JDK 17+、Android SDK（platform + build-tools）、Gradle wrapper。

```bash
git clone https://github.com/galizhajidanbaofan/plainpage-reader.git
cd plainpage-reader
./gradlew :app:assembleDebug        # 构建
./gradlew :app:testDebugUnitTest    # 单元测试
./gradlew :app:lintDebug            # Lint
```

> 说明：构建脚本尚未落地（阶段 1 交付），以上命令是预期形态，目前执行会失败。版本号一律以 `gradle/libs.versions.toml` 为准，不在 README 里写死。

---

## 隐私

- 无网络权限，无任何网络代码路径。
- 书籍文件只存在应用私有目录；导入时记录**来源 URI** 仅供显示与「缺失提示」，不上传。
- 无统计、无崩溃上报、无广告 SDK。

---

## 许可证与第三方

主许可证 Apache-2.0，全文见 [LICENSE](LICENSE)。署名信息见 [NOTICE](NOTICE)，逐条第三方依赖与许可证证据见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。

引入任何依赖前必须：核对 POM/AAR 中的许可证 → 记入清单 → 确认不是 AGPL/GPL。

---

## 发布前提醒

`PlainPage` / `简页` 可能与已有产品重名，上架（尤其 Google Play 与国内商店）前请自行完成**商店名与商标检索**；仓库名使用 `plainpage-reader` 以降低混淆。

---

## 贡献

项目早期，接口仍在变动。欢迎以 Issue 形式提出格式兼容问题（请附最小可复现样本的**来源说明**，勿上传有版权的整本书）。
