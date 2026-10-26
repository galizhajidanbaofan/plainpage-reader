package io.github.galizhajidanbaofan.plainpage.ui.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.galizhajidanbaofan.plainpage.R
import io.github.galizhajidanbaofan.plainpage.di.AppContainer
import io.github.galizhajidanbaofan.plainpage.domain.engine.ReaderException
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.github.galizhajidanbaofan.plainpage.ui.theme.ReadingFonts
import io.github.galizhajidanbaofan.plainpage.ui.LocalAppearance
import io.github.galizhajidanbaofan.plainpage.ui.ReadingMode
import io.github.galizhajidanbaofan.plainpage.ui.theme.LocalTextStyles
import io.github.galizhajidanbaofan.plainpage.ui.theme.LocalThemeTokens
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 阅读页入口：只为拿到 ViewModel 与状态，界面本身交给 [ReaderContent]。
 *
 * 这样拆开的好处是截图测试可以喂一个假的 Ready 状态，不需要真的打开文件。
 */
@Composable
fun ReaderScreen(
    bookId: Long,
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ReaderViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = "reader-$bookId",
        factory = ReaderViewModel.factory(bookId, container),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollFraction by viewModel.scrollFraction.collectAsStateWithLifecycle()
    ReaderContent(
        state = state,
        onBack = onBack,
        onPrevPage = viewModel::prevPage,
        onNextPage = viewModel::nextPage,
        onChapter = viewModel::goToChapter,
        mode = LocalAppearance.current.readingMode,
        scrollFraction = scrollFraction,
        loadChunk = viewModel::textChunk,
        loadPictures = viewModel::picturesInChunk,
        onScrollPosition = viewModel::onScrollPosition,
        onFlushScroll = viewModel::flushScrollProgress,
        modifier = modifier,
    )
}

/**
 * 阅读页只留三样东西：回到书的入口、当前这一章、以及「读到哪里」。
 * 上下各一条发丝线把正文框住，像纸页的天头地脚；其余全部让给字。
 *
 * 翻页方式由 [mode] 决定：
 * - [ReadingMode.PAGED]：固定字符窗口，左右轻点/左右滑动翻页，不响应上下滑动；
 * - [ReadingMode.SCROLL]：整本连续下滚，没有「上一页/下一页」这种东西。
 * 两种模式共用同一套进度坐标（字符偏移），所以随时切换都能接上。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderContent(
    state: ReaderUiState,
    onBack: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onChapter: (Int) -> Unit,
    modifier: Modifier = Modifier,
    mode: ReadingMode = ReadingMode.PAGED,
    scrollFraction: Float = 0f,
    loadChunk: suspend (Int) -> String = { "" },
    loadPictures: suspend (Int) -> List<ReaderPicture> = { emptyList() },
    onScrollPosition: (Int, Int) -> Unit = { _, _ -> },
    onFlushScroll: (Int, Int) -> Unit = { _, _ -> },
) {
    var tocOpen by rememberSaveable { mutableStateOf(false) }
    val ready = state as? ReaderUiState.Ready
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tokens = LocalThemeTokens.current
    val styles = LocalTextStyles.current

    Surface(color = tokens.paper, modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ReaderTopBar(
                title = ready?.title.orEmpty(),
                chapter = ready?.chapterTitle.orEmpty(),
                canOpenToc = ready != null && ready.chapters.isNotEmpty(),
                onBack = onBack,
                onOpenToc = { tocOpen = true },
            )
            HorizontalDivider(color = tokens.divider, thickness = 1.dp)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    ReaderUiState.Loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = tokens.accent,
                    )

                    is ReaderUiState.Failed -> ReaderMessage(
                        text = failureText(state.reason),
                        modifier = Modifier.align(Alignment.Center),
                    )

                    is ReaderUiState.Unsupported -> ReaderMessage(
                        text = stringResource(R.string.reader_unsupported, state.format.name),
                        modifier = Modifier.align(Alignment.Center),
                    )

                    is ReaderUiState.Ready -> when (mode) {
                        ReadingMode.PAGED -> PagedBody(
                            ready = state,
                            onPrevPage = onPrevPage,
                            onNextPage = onNextPage,
                        )

                        ReadingMode.SCROLL -> ScrollBody(
                            ready = state,
                            loadChunk = loadChunk,
                            loadPictures = loadPictures,
                            onScrollPosition = onScrollPosition,
                            onFlushScroll = onFlushScroll,
                        )
                    }
                }
            }
            if (ready != null) {
                ProgressLine(fraction = if (mode == ReadingMode.SCROLL) scrollFraction else ready.fraction)
            }
        }
    }

    if (tocOpen && ready != null) {
        ModalBottomSheet(
            onDismissRequest = { tocOpen = false },
            sheetState = sheetState,
            containerColor = tokens.paper,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 22.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.reader_toc),
                        style = styles.tocTitle,
                        color = tokens.textPrimary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.reader_kicker_chapters, ready.chapters.size),
                        style = styles.kicker,
                        color = tokens.textSecondary,
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 22.dp),
                    color = tokens.divider,
                    thickness = 1.dp,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    itemsIndexed(
                        items = ready.chapters,
                        key = { index, _ -> index },
                    ) { index, chapter ->
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        tocOpen = false
                                        onChapter(index)
                                    }
                                    .padding(horizontal = 22.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = chapter.title,
                                    style = styles.tocChapter,
                                    color = tokens.textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                val position = if (ready.charCount > 0) {
                                    ((chapter.charStart.toFloat() / ready.charCount) * 100f).roundToInt()
                                } else {
                                    0
                                }
                                Text(
                                    text = stringResource(R.string.reader_toc_percent, position),
                                    style = styles.tocPage,
                                    color = tokens.textSecondary,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                            if (index != ready.chapters.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 22.dp),
                                    color = tokens.divider,
                                    thickness = 1.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    chapter: String,
    canOpenToc: Boolean,
    onBack: () -> Unit,
    onOpenToc: () -> Unit,
) {
    val tokens = LocalThemeTokens.current
    val styles = LocalTextStyles.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = stringResource(R.string.action_back),
                style = styles.meta,
                color = tokens.textSecondary,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    style = styles.screenTitle,
                    color = tokens.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (chapter.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = chapter,
                    style = styles.kicker,
                    color = tokens.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (canOpenToc) {
            TextButton(
                onClick = onOpenToc,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.reader_toc),
                    style = styles.screenTitle,
                    color = tokens.accent,
                )
            }
        } else {
            Spacer(modifier = Modifier.width(46.dp))
        }
    }
}

/**
 * 「读到哪里」只剩一条 2dp 的墨线。
 *
 * 原来这里是「上一页 · 3/12 · 下一页」，但翻页已经交给手势，
 * 页数对读者没有意义——他要知道的是「还剩多少」，一条线的长度正好回答这个。
 */
@Composable
private fun ProgressLine(fraction: Float) {
    val tokens = LocalThemeTokens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(tokens.divider),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(tokens.accent),
        )
    }
}

/** 短于此长度的行按章节标题处理，不做段首缩进。 */
private const val HEADING_MAX_CHARS = 24

/**
 * 中文排版的段首缩进：Compose 的 textIndent 只作用于整块文本，所以按行补两个全角空格。
 * 短行（章节标题、署名、诗句）不缩进——用长度做启发式，比给每行加类型标记省事且不易出错。
 */
private fun indentParagraphs(text: String): String = text.lineSequence().joinToString("\n") { line ->
    when {
        line.isBlank() -> line
        line.trim().length <= HEADING_MAX_CHARS -> line.trimStart()
        else -> "　　" + line.trimStart()
    }
}

/**
 * 翻页模式的一屏快照。
 *
 * 为什么要把这一屏的所有内容打成快照再交给 `AnimatedContent`：如果动画里直接读外层的
 * `ready`，翻页动画的两端会一起变成"新的一屏"，退场的那一屏就没了——看起来像硬切。
 */
private data class PageSnapshot(
    val index: Int,
    val text: String,
    val picture: ImageBitmap?,
    val caption: String?,
    val lossyHint: Boolean,
)

/**
 * 翻页模式的正文：一屏一窗口，左右轻点或左右滑动翻页。
 *
 * 上下滑动**故意不接**：这个模式里没有可滚动的内容，
 * 竖向手势留给系统（下拉通知栏、返回手势），比自作主张地"滚半页"更符合直觉。
 *
 * EPUB 里的插图占一整屏，排在它所属正文屏之后：图片不打断字符窗口，
 * 所以不会出现"图把正文挤出屏幕"的情况，代价只是图的上下文要往前翻一屏。
 */
@Composable
private fun PagedBody(
    ready: ReaderUiState.Ready,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
) {
    val tokens = LocalThemeTokens.current
    val styles = LocalTextStyles.current
    val font = LocalAppearance.current.font
    val bodyStyle = remember(font) { ReadingFonts.apply(styles.readingBody, font) }
    val swipeThreshold = with(LocalDensity.current) { 56.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pageTurnGestures(
                swipeThreshold = swipeThreshold,
                onPrev = onPrevPage,
                onNext = onNextPage,
            ),
    ) {
        AnimatedContent(
            targetState = PageSnapshot(
                index = ready.pageIndex,
                text = ready.text,
                picture = ready.picture,
                caption = ready.pictureCaption,
                lossyHint = ready.lossyDecoding,
            ),
            transitionSpec = {
                val forward = targetState.index >= initialState.index
                val enter = slideInHorizontally(animationSpec = tween(220)) { width ->
                    if (forward) width / 4 else -width / 4
                } + fadeIn(animationSpec = tween(160))
                val exit = slideOutHorizontally(animationSpec = tween(160)) { width ->
                    if (forward) -width / 4 else width / 4
                } + fadeOut(animationSpec = tween(120))
                enter togetherWith exit
            },
            label = "page-turn",
        ) { page ->
            if (page.picture != null) {
                PicturePage(
                    picture = page.picture,
                    caption = page.caption,
                    style = bodyStyle,
                    color = tokens.textSecondary,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 26.dp, vertical = 24.dp),
                ) {
                    if (page.lossyHint) {
                        Text(
                            text = stringResource(R.string.reader_lossy),
                            style = styles.meta,
                            color = tokens.accent,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    Text(
                        text = indentParagraphs(page.text),
                        style = bodyStyle,
                        color = tokens.textPrimary,
                    )
                }
            }
        }
    }
}

/**
 * 整页插图：等比例缩放到屏内，上下留白居中；底下用图的 alt 当图注（没有就不画）。
 *
 * 为什么不给图加边框：纸黑风格里没有"图片框"这种东西，留白就是框。
 */
@Composable
private fun PicturePage(
    picture: ImageBitmap,
    caption: String?,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = picture,
            contentDescription = caption,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(picture.width.toFloat() / picture.height.toFloat()),
        )
        if (!caption.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = caption, style = style, color = color)
        }
    }
}

/**
 * 一块正文后面可能跟着一两张插图。
 *
 * 只在真正滚到这一块时才去解码（`LaunchedEffect(index)`），没滚到的图不会占内存；
 * 加载失败就什么都不画——插图坏掉不该影响读正文。
 */
@Composable
private fun ChunkPictures(
    index: Int,
    loadPictures: suspend (Int) -> List<ReaderPicture>,
    captionStyle: androidx.compose.ui.text.TextStyle,
    captionColor: androidx.compose.ui.graphics.Color,
) {
    var pictures by remember(index) { mutableStateOf<List<ReaderPicture>>(emptyList()) }
    LaunchedEffect(index) {
        pictures = loadPictures(index)
    }
    if (pictures.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        pictures.forEach { picture ->
            Spacer(modifier = Modifier.height(18.dp))
            Image(
                bitmap = picture.bitmap,
                contentDescription = picture.caption,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(picture.bitmap.width.toFloat() / picture.bitmap.height.toFloat()),
            )
            if (!picture.caption.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = picture.caption, style = captionStyle, color = captionColor)
            }
        }
    }
}

/**
 * 滚动模式的正文：整本切成定长块，`LazyColumn` 只渲染看得见的几块。
 *
 * 进度由「第一块可见的块」换算成字符偏移写回；进来时按上次的偏移滚到对应块。
 */
@Composable
private fun ScrollBody(
    ready: ReaderUiState.Ready,
    loadChunk: suspend (Int) -> String,
    loadPictures: suspend (Int) -> List<ReaderPicture>,
    onScrollPosition: (Int, Int) -> Unit,
    onFlushScroll: (Int, Int) -> Unit,
) {
    val tokens = LocalThemeTokens.current
    val styles = LocalTextStyles.current
    val font = LocalAppearance.current.font
    val bodyStyle = remember(font) { ReadingFonts.apply(styles.readingBody, font) }
    val chunkCount = ScrollChunkMath.chunkCount(ready.charCount)
    if (chunkCount == 0) {
        ReaderMessage(text = stringResource(R.string.reader_error_empty))
        return
    }

    val initialChunk = remember(ready.charCount) {
        ScrollChunkMath.chunkOf(ready.anchorChar, ready.charCount)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialChunk)

    // 跳章、切换模式后回到「状态里的锚点」；正常滚动不会改 anchorChar，所以不会被拽回去。
    LaunchedEffect(ready.anchorChar, ready.charCount) {
        val target = ScrollChunkMath.chunkOf(ready.anchorChar, ready.charCount)
        if (listState.firstVisibleItemIndex != target) listState.scrollToItem(target)
    }

    LaunchedEffect(listState, ready.charCount) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                onScrollPosition(ScrollChunkMath.charOffsetOf(index), ready.charCount)
            }
    }

    DisposableEffect(listState, ready.charCount) {
        onDispose {
            onFlushScroll(ScrollChunkMath.charOffsetOf(listState.firstVisibleItemIndex), ready.charCount)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 24.dp),
    ) {
        itemsIndexed(
            items = (0 until chunkCount).toList(),
            key = { index, _ -> index },
        ) { index, _ ->
            Column {
                ChunkText(
                    index = index,
                    loadChunk = loadChunk,
                    style = bodyStyle,
                    color = tokens.textPrimary,
                )
                ChunkPictures(
                    index = index,
                    loadPictures = loadPictures,
                    captionStyle = styles.meta,
                    captionColor = tokens.textSecondary,
                )
            }
        }
        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

@Composable
private fun ChunkText(
    index: Int,
    loadChunk: suspend (Int) -> String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
) {
    var text by remember(index) { mutableStateOf<String?>(null) }
    LaunchedEffect(index) { text = loadChunk(index) }
    val loaded = text
    if (loaded.isNullOrEmpty()) {
        // 占位高度约等于一屏，避免加载时列表抖动；空串表示这一块本来就没有内容。
        Spacer(modifier = Modifier.height(if (loaded == null) 240.dp else 0.dp))
    } else {
        Text(text = indentParagraphs(loaded), style = style, color = color)
    }
}

/**
 * 翻页手势：左右滑动翻页，轻点左右两侧翻页，中间不动。
 *
 * 为什么手写 `awaitEachGesture` 而不叠两个 `pointerInput`：
 * `detectTapGestures` 与 `detectHorizontalDragGestures` 各自都要拿第一下触摸的所有权，
 * 叠在一起会出现「滑动被当成点击」或「点击被吞掉」；一次手势里自己判断距离最稳。
 */
private fun Modifier.pageTurnGestures(
    swipeThreshold: Float,
    onPrev: () -> Unit,
    onNext: () -> Unit,
): Modifier = pointerInput(swipeThreshold, onPrev, onNext) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var totalX = 0f
        var totalY = 0f
        var change: PointerInputChange? = null
        do {
            val event = awaitPointerEvent()
            change = event.changes.firstOrNull { it.id == down.id } ?: break
            totalX += change.positionChange().x
            totalY += change.positionChange().y
            change.consume()
        } while (change.pressed)

        when {
            // 滑动优先：横向位移超过阈值就翻页，方向决定翻过去还是翻回来。
            totalX <= -swipeThreshold -> onNext()
            totalX >= swipeThreshold -> onPrev()
            // 否则按轻点处理，只有左右两侧算数。
            abs(totalX) < 24f && abs(totalY) < 24f -> {
                val x = down.position.x
                when {
                    x < size.width * 0.33f -> onPrev()
                    x > size.width * 0.67f -> onNext()
                }
            }
        }
    }
}

@Composable
private fun ReaderMessage(text: String, modifier: Modifier = Modifier) {
    val tokens = LocalThemeTokens.current
    val styles = LocalTextStyles.current
    Column(
        modifier = modifier.padding(34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(1.dp)
                .background(tokens.divider),
        )
        Text(
            text = text,
            style = styles.emptyHint,
            color = tokens.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun failureText(reason: ReaderException.Reason): String = when (reason) {
    ReaderException.Reason.FILE_MISSING -> stringResource(R.string.reader_error_missing)
    ReaderException.Reason.CORRUPTED -> stringResource(R.string.reader_error_corrupted)
    ReaderException.Reason.UNSUPPORTED_ENCODING -> stringResource(R.string.reader_error_encoding)
    ReaderException.Reason.EMPTY_CONTENT -> stringResource(R.string.reader_error_empty)
    ReaderException.Reason.TOO_LARGE -> stringResource(R.string.reader_error_too_large)
    ReaderException.Reason.UNKNOWN -> stringResource(R.string.reader_error_unknown)
}
