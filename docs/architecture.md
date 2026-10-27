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

### 阅读页的数据流（当前已接入 TXT 与 EPUB）

```
Room(book 行) ──find()──▶ ReaderViewModel ──engineFactory──▶ TxtReaderEngine / EpubReaderEngine
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

### 两种阅读模式：一套坐标，两套骨架

`ReadingMode` 从 `AppearanceState` 读出来（`SettingsScreen` 里切换），只影响 `ReaderContent` 用哪套骨架，不影响引擎、进度、目录：

```
ReaderUiState.Ready ──mode==PAGED──▶ PagedBody：AnimatedContent + 固定字符窗口
                    └─mode==SCROLL─▶ ScrollBody：LazyColumn + 定长分块（ScrollChunkMath.CHUNK_CHARS = 1500）
```

- **左右翻页**：`pageTurnGestures` 一次手势里自己判断——横向累计位移超过 `56.dp` 就翻页（左负右正），否则位移小于 `24f` 的算轻点，按落点位于左 1/3 / 右 2/3 决定上一页或下一页，中间不动。纵向滑动不消费、也不滚动正文。
  为什么不叠 `detectTapGestures` + `detectHorizontalDragGestures`：两个检测器都要抢第一下触摸，叠起来会出现滑动被当点击、或点击被吞。
- **上下滚动**：整本按 `1500` 字符切块，`LazyColumn` 只渲染看得见的块；每块内容走 `ReaderViewModel.textChunk(index)`，命中 24 条的 LRU 缓存就不碰文件，未命中才在 IO 线程 `readText`。左右滑动不接任何手势。
- **进度只有一套坐标**：滚动模式用「第一块可见的块」×`1500` 换算成 `anchorChar`，与翻页模式存的是同一个「全书字符偏移」；`_scrollFraction` 只负责底部那条 2dp 进度线。滚动时的写盘做 300ms 防抖，退出阅读页时 `flushScrollProgress` 不等防抖。
- **底栏**：两种模式都只剩一条 2dp 进度线（底衬 `divider` + 填充 `accent`），不再有页码与「上一页 / 下一页」按钮——翻页模式下那两个按钮与左右轻点功能重复，还占掉一行正文。

### EPUB：把「章节资源」压平成一条全书文本

EPUB 的坐标本来是「href + 章内位置」，阅读页只有一套翻页逻辑，所以 `EpubReaderEngine` 打开时做一次**流式压平**：

1. `EpubArchive` 打开 ZIP，只认 `mimetype`/`META-INF/container.xml`；
2. `EpubPackageParser` 解 OPF：`dc:title`、`dc:creator`、manifest、spine（`linear="no"` 跳过）；
3. 按 spine 顺序逐章读 XHTML，`HtmlTextExtractor` 去掉标签（跳过 `head`/`script`/`style`/`iframe`/`object` 与 svg 里的 `<text>`，块级标签换行，实体解码），拼成全书文本并记录每章 `[charStart, charEnd)`；同时把 `<img>` / `<image>` 的地址、`alt` 与「本该出现在第几个字之间」记下来，留给插图用；
4. `EpubOutlineParser` 取目录：NCX → EPUB3 `nav[epub:type=toc]` → spine 文件名兜底；
5. 进度双向换算：`positionOf(offset)` 给出 `ReadingLocation.Epub(href, 章内百分比)`，`charOffsetOf(locator)` 换回全书偏移。

安全性（EPUB 是外部来的不可信文件）：

| 风险 | 处理 |
|---|---|
| ZIP 路径穿越（`../../x`） | `ZipPaths.normalize` 解析成绝对路径的一律丢弃；条目名不一致的也别想读到 |
| 条目解压炸弹 | 单条目上限 `MAX_CHAPTER_BYTES`，XML 上限 `MAX_XML_BYTES`，超限抛 `TOO_LARGE` 而不是 OOM |
| XML 外部实体（XXE） | 关闭外部 DTD/实体，实体解析器返回空串，绝不发起 IO |
| 正文过大 | 全书文本上限 `EpubLimits.MAX_TOTAL_CHARS`，超出后剩余章节不读入并置 `truncated`（对外表现为 `lossyDecoding = true`），界面提示「可能不完整」 |

代价与取舍：样式与脚注跳转在 P0 仍不进文本流（只留文字），换来的是「翻页与进度和 TXT 完全同构」。图片则是例外——它不占字符，但读者要看到它，所以用「锚点 + 独占一屏」的方式接进来，见下。

### EPUB 插图：记锚点，不插进文本流

图片如果直接塞进正文流，那一屏的字符窗口就会被图挤爆（窗口是定长字符，多出来的正文会被裁掉）。所以走「锚点」：

1. `HtmlTextExtractor` 扫到 `<img>` 时在输出里留一个私用区占位符，把 `src`/`alt` 记进 `ExtractedImage`，归一化时再把占位符换成「它在纯文本里的字符下标」；`data:` / `javascript:` / 空地址直接跳过。
   - `<img>` 走 `src`，`<image>` 走 `href` / `xlink:href`——EPUB3 的封面和大量插图是 `<svg><image xlink:href="…"/></svg>` 包起来的，所以 **`svg` 不能整体跳过**，只跳过它里面的 `<text>`（矢量文字）。
2. `EpubIndexer` 把地址相对所在章目录解析成压缩包内绝对路径（`ZipPaths.resolve`，含百分号解码，越界 `..` 直接丢），偏移换算成全书坐标 `bodyStart + offset`，得到 `PictureRef(at, path, alt)`。
3. 封面单独补一张：OPF 的 `properties="cover-image"` → `<meta name="cover">` → guide 的 `<reference type="cover">`（只要指向的是图片）。封面通常**不出现在任何正文 XHTML 里**，只从正文找图的话封面永远不显示；正文已经引过同一张图时不重复插，且封面排在正文第一页之前（`PageSlots.BEFORE_FIRST_PAGE`）。
4. 取图时大小写兜底：href 写 `COVER.JPG`、条目叫 `Cover.jpg` 是跨平台打包的常见坑，`EpubArchive` 先精确匹配、再按小写匹配一次。
5. 翻页模式：`PageSlots.build` 把「正文屏」和「插图纸」排成一条序列——偏移落在某页窗口内的图排在该页之后，落在书末的图追加在最后，**永不丢图**；图屏 → `PicturePage`（等比例 `ContentScale.Fit` + 图注）。
6. 滚动模式：`ChunkPictures` 在所属块之后渲染，`LaunchedEffect(chunkIndex)` 懒加载，没滚到的图不解码。
7. 解码：`EpubReaderEngine.pictureBytes(path)` 读原始字节（`EpubLimits.MAX_PICTURE_BYTES` 上限）→ `BitmapFactory` 先只读尺寸再按 `inSampleSize` 降到约 1600px 解码，4 张的 LRU 缓存；解不出来就当这一屏没有图、退回该位置正文，绝不留白屏。

这套设计的代价是「图的上下文要往前翻一屏」，收益是任何一屏都不会被图挤爆，而且图片坏掉不影响读正文。

### PDF：一张位图一页，独立状态机

PDF 和 TXT/EPUB 没有共同的可读结构：它没有「字符偏移」，只有「第几页 + 页内比例」；渲染一页是几张 MB 的位图，不是几十 KB 的字符串。所以 PDF 不去套字符窗口那套骨架，而是并行一套：

| 层 | 文件 | 职责 |
|---|---|---|
| 引擎 | `domain/engine/pdf/PdfReaderEngine.kt` | 打开 `PdfRenderer`（`ParcelFileDescriptor` 只读），暴露 `pageCount` / `aspectOf` / `renderPage` / `close`；渲染串行化（`Mutex`），已渲染页进 3 张的 LRU 位图缓存 |
| 纯算术 | `domain/engine/pdf/PdfPageMath.kt` | `bitmapSize(pageWidth, pageHeight, targetWidthPx)`：按屏宽等比例算位图尺寸，宽度不超 2400px、总像素不超 400 万（中端机的位图内存红线），比例尺不低于 0.05；`clampPage(page, count)` 夹紧页码 |
| 状态机 | `ui/reader/pdf/PdfReaderViewModel.kt` | `Loading / Ready(pageIndex, pageCount, image, aspect) / Failed(reason)`；翻页、页码跳转、滚动落库、退出时 flush 页码；同样 300ms 防抖 |
| 界面 | `ui/reader/pdf/PdfReaderScreen.kt` | 两种模式共用同一份状态：翻页模式一次渲染一页（`ContentScale.Fit`，纸白底），滚动模式 `LazyColumn` 每页一项、只解码可见页；页码目录抽屉（P0 不做 PDF 大纲，见下） |

几个刻意的取舍：

- **不进 `ReaderEngine` 的 render/flow 抽象**：`ReaderEngine` 的 `render/flow` 签名是围绕字符窗口和 `PictureSource` 设计的，强行复用会让两边都被迫变形；PDF 只复用 `ReaderEngine` 的 `open / close / loadOutline / restoreProgress / saveProgress` 这半边契约（PDF 的 `loadOutline` 产出的就是「第 N 页」条目）。
- **背面清成纸白**：PDF 页面本身通常没有背景色，`renderPage` 先把位图填 `Color.WHITE` 再渲染，否则深色主题下会看到黑底上的透明页。
- **没有大纲**：P0 按用户决定只做页码跳转（PDF 大纲解析要么引 PdfBox，要么自研一套对象解析器，包体与工期都不划算）。目录抽屉里的每一项就是「第 N 页」。
- **不做文字层**：P0 不抽文本、不选中、不搜索；PDF 的进度只有页码与页内比例，够用且不会因为文本层缺失而失真。
- **内存**：一页位图按屏宽等比例算（手机竖屏通常 1080×1500 左右），加 3 张缓存与预渲染相邻页，峰值在 20–40 MB 量级；再大的 PDF 也不整本载入，`PdfRenderer` 是按需打开页的。

## 为什么不引这些依赖

| 依赖 | 结论 | 原因 |
|---|---|---|
| Readium Kotlin Toolkit | 不引 | BSD-3-Clause 没问题，但为「最小离线阅读器」带进整套分页排版与导航，包体和复杂度都超出 P0，且会把渲染锁死到 WebView 路线 |
| PdfBox-Android | 不引 | Apache-2.0 合规，但依赖链带 BouncyCastle 三件套（数 MB、反射多、R8 难裁）；P0 不要 PDF 大纲 |
| juniversalchardet | 不引 | MPL-1.1，且 TXT 编码探测自己写更可控 |
| Hilt / Koin | 不引 | 组合根只有几个对象，`AppContainer` 手写足够，省一个注解处理器 |
