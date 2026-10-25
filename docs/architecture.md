# 架构说明

本文说明「简页」的分层、线程模型与可切换 UI 的注入点。更新于 2026-10-25。

## 分层

```
ui/                      Compose 界面与 ViewModel —— 只有它认识 Android 资源与 Context
  PlainPageApp.kt        导航宿主 + 底部导航 + 外观注入
  shelf/ recent/ reader/ settings/
  theme/                 ThemeTokens（纯数据）+ PlainPageTheme（映射到 Material3）
  format/                DisplayFormats（纯函数，可单测）
  library/               LibraryViewModel（书架/最近共用的状态持有者）

domain/                  纯 Kotlin，无 Android 依赖（除了模型里的值类型）
  model/                 Book / BookFormat / ReadingLocation（+ Codec）
  repository/            BookRepository 接口
  importing/             ImportRules（文件名净化、格式判定、存储名）
  engine/                ReaderEngine 接口（open/loadOutline/restoreProgress/saveProgress/close）

data/                    实现层
  local/                 Room：BookEntity / BookDao / PlainPageDatabase
  repository/            RoomBookRepository
  importing/             BookImporter（SAF → 私有目录复制）

di/                      AppContainer：手写组合根（不引 Hilt/Koin）
```

依赖方向单向向内：`ui → domain ← data`。`domain` 不认识 Room、Compose、Uri。

## 线程模型

| 工作 | 线程 |
|---|---|
| Compose 组合、导航、状态读取 | 主线程（只做这些） |
| SAF 查询/复制、文件大小校验 | `Dispatchers.IO`（`BookImporter` 内部 `withContext`） |
| Room 读写 | Room 自身调度（`Flow` 查询 + `suspend` 写） |
| 将来的 EPUB/TXT 解析 | 计划：`Dispatchers.Default` 上分章，配 `Semaphore`/`Mutex` 防并发重复解析 |
| 将来的 PDF 渲染 | 计划：单线程 `newSingleThreadContext`（`PdfRenderer` 非线程安全） |

导入路径的关键约束：**复制用 64 KB 缓冲流式写**，边写边累加字节数，超过上限立即抛异常并清理半成品，不把整个文件读进内存。

## 可切换 UI 的注入点

- `AppearanceState`（`ui/Appearance.kt`）= 主题 + 书架布局 + 翻页方式，一个快照。
- `ThemeTokens`（`ui/theme/ThemeTokens.kt`）= 纯数据配色（paper / surface / textPrimary / accent / divider），三种主题各一套；`PlainPageTheme` 把它映射成 `ColorScheme`，于是按钮、导航栏等 Material3 组件自动跟随主题。
- `LocalAppearance` / `LocalThemeTokens` = 组合局部变量，界面读取它们而不是读全局单例。
- 书架列表/网格：`ShelfScreen` 内部只按 `ShelfLayout` 选渲染结构，数据与回调完全相同。
- **业务层不认识主题**：`domain`、`data`、`ViewModel` 里没有主题/布局判断，只传数据。

阶段 1 里 `AppearanceState` 存在内存 + `rememberSaveable`；阶段 2 会由 `AppearanceRepository`（DataStore）以 Flow 提供，签名不变，UI 无需改动。

## 阅读引擎接口

`domain/engine/ReaderEngine.kt` 定义统一契约，三种格式各自实现：

| 实现 | 解析方式 | 进度定位 |
|---|---|---|
| `TxtReaderEngine` | 流式读取 + 编码探测（BOM → UTF-8 严格校验 → GBK/GB18030 回退）+ 正则分章 | 字符偏移 + 章节序号 + 百分比 |
| `EpubReaderEngine` | 自研最小 EPUB2/3（ZIP + `container.xml` → OPF spine → NCX/nav 目录） | href + 章节内百分比 |
| `PdfReaderEngine` | 平台 `PdfRenderer` 渲染位图 | 页码 + 页内滚动比例 |

### 阅读页的数据流（当前已接入 TXT）

```
Room(book 行) ──find()──▶ ReaderViewModel ──engineFactory──▶ TxtReaderEngine
                              │                                 │
                              │◀── loadOutline() / restoreProgress()
                              │
        ReaderPager(charCount) 把「字符偏移」换算成页码/百分比
                              │
                              ▼
                     ReaderUiState.Ready ──▶ ReaderContent（纯展示）
```

- **翻页不重开文件**：`TxtPageReader` 用随机访问的缓冲读，一次只取 `ReaderPager.DEFAULT_WINDOW_CHARS = 1200` 个字符。
- **进度写回由引擎外抛**：引擎不认识 Room，构造时注入 `progressSink`，组合根把它接到 `BookRepository.saveLocation`。
- **恢复进度先对齐页首**：`ReaderPager.clampToPage(charOffset)` 保证「存下的偏移」和「重开后的窗口起点」是同一个，字体大小变了也不会错位到半句话。
- **UI 只吃状态**：`ReaderContent` 不碰文件、不碰数据库，所以截图测试可以直接喂假状态。

为什么用「固定字符窗口」而不是「按屏幕高度分页」：前者与字体、字号、屏幕密度无关，同一份进度在任何设备上都能恢复到同一段文字；后者必须排版完成才知道一页多少字，进度会跟着设备变。

## 为什么不引这些依赖

| 依赖 | 结论 | 原因 |
|---|---|---|
| Readium Kotlin Toolkit | 不引 | BSD-3-Clause 没问题，但为「最小离线阅读器」带进整套分页排版与导航，包体和复杂度都超出 P0，且会把渲染锁死到 WebView 路线 |
| PdfBox-Android | 不引 | Apache-2.0 合规，但依赖链带 BouncyCastle 三件套（数 MB、反射多、R8 难裁）；P0 不要 PDF 大纲 |
| juniversalchardet | 不引 | MPL-1.1，且 TXT 编码探测自己写更可控 |
| Hilt / Koin | 不引 | 组合根只有几个对象，`AppContainer` 手写足够，省一个注解处理器 |
