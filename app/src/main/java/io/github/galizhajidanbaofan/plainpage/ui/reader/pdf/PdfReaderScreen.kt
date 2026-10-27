package io.github.galizhajidanbaofan.plainpage.ui.reader.pdf

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.galizhajidanbaofan.plainpage.R
import io.github.galizhajidanbaofan.plainpage.di.AppContainer
import io.github.galizhajidanbaofan.plainpage.ui.LocalAppearance
import io.github.galizhajidanbaofan.plainpage.ui.ReadingMode
import io.github.galizhajidanbaofan.plainpage.ui.reader.ProgressLine
import io.github.galizhajidanbaofan.plainpage.ui.reader.ReaderMessage
import io.github.galizhajidanbaofan.plainpage.ui.reader.ReaderTopBar
import io.github.galizhajidanbaofan.plainpage.ui.reader.failureText
import io.github.galizhajidanbaofan.plainpage.ui.reader.pageTurnGestures
import io.github.galizhajidanbaofan.plainpage.ui.theme.LocalTextStyles
import io.github.galizhajidanbaofan.plainpage.ui.theme.LocalThemeTokens
import kotlin.math.roundToInt

/**
 * PDF 阅读页入口。
 *
 * 与 TXT/EPUB 分开的原因见 [PdfReaderViewModel]：PDF 的"一屏"是整页位图，
 * 没有字符坐标，混进字符窗口状态机只会造出一堆假数据。
 */
@Composable
fun PdfReaderScreen(
    bookId: Long,
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PdfReaderViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = "pdf-reader-$bookId",
        factory = PdfReaderViewModel.factory(bookId, container),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollFraction by viewModel.scrollFraction.collectAsStateWithLifecycle()
    PdfReaderContent(
        state = state,
        mode = LocalAppearance.current.readingMode,
        scrollFraction = scrollFraction,
        onBack = onBack,
        onPrevPage = viewModel::prevPage,
        onNextPage = viewModel::nextPage,
        onGoToPage = viewModel::goToPage,
        onViewportWidth = viewModel::onViewportWidth,
        loadPage = { index, width -> viewModel.pageBitmap(index, width) },
        onScrollPage = viewModel::onScrollPage,
        onFlushPage = viewModel::flushPage,
        modifier = modifier,
    )
}

/**
 * 阅读页外壳与 TXT/EPUB 保持同一套版式：一条发丝线框住页面、底部一条 2dp 进度线，
 * 中间整块让给版面。区别只在"正文"换成了整页位图。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfReaderContent(
    state: PdfReaderUiState,
    onBack: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onGoToPage: (Int) -> Unit,
    onViewportWidth: (Int) -> Unit,
    loadPage: suspend (Int, Int) -> ImageBitmap?,
    onScrollPage: (Int) -> Unit,
    onFlushPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    mode: ReadingMode = ReadingMode.PAGED,
    scrollFraction: Float = 0f,
) {
    val ready = state as? PdfReaderUiState.Ready
    var tocOpen by rememberSaveable { mutableStateOf(false) }
    // 滚动模式下页眉要跟着"现在看到第几页"走，不能一直停在进页面时的页码。
    var scrollPage by remember { mutableStateOf<Int?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tokens = LocalThemeTokens.current

    Surface(color = tokens.paper, modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ReaderTopBar(
                title = ready?.title.orEmpty(),
                chapter = ready?.let {
                    val shown = if (mode == ReadingMode.SCROLL) scrollPage ?: it.pageIndex else it.pageIndex
                    stringResource(R.string.pdf_kicker, shown + 1, it.pageCount)
                }.orEmpty(),
                canOpenToc = ready != null && ready.pageCount > 1,
                onBack = onBack,
                onOpenToc = { tocOpen = true },
            )
            HorizontalDivider(color = tokens.divider, thickness = 1.dp)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    PdfReaderUiState.Loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = tokens.accent,
                    )

                    is PdfReaderUiState.Failed -> ReaderMessage(
                        text = failureText(state.reason),
                        modifier = Modifier.align(Alignment.Center),
                    )

                    is PdfReaderUiState.Ready -> when (mode) {
                        ReadingMode.PAGED -> PdfPagedBody(
                            ready = state,
                            onPrevPage = onPrevPage,
                            onNextPage = onNextPage,
                            onViewportWidth = onViewportWidth,
                        )

                        ReadingMode.SCROLL -> PdfScrollBody(
                            ready = state,
                            loadPage = loadPage,
                            onScrollPage = onScrollPage,
                            onFlushPage = onFlushPage,
                            onVisiblePage = { scrollPage = it },
                        )
                    }
                }
            }
            if (ready != null) {
                ProgressLine(
                    fraction = if (mode == ReadingMode.SCROLL) scrollFraction else ready.fraction,
                )
            }
        }
    }

    if (tocOpen && ready != null) {
        ModalBottomSheet(
            onDismissRequest = { tocOpen = false },
            sheetState = sheetState,
            containerColor = tokens.paper,
        ) {
            PdfTocSheet(
                pageCount = ready.pageCount,
                currentPage = ready.pageIndex,

                onPick = { index ->
                    tocOpen = false
                    onGoToPage(index)
                },
            )
        }
    }
}

/** 翻页模式：一页一屏，左右轻点/左右滑动翻页；竖向手势不拦截。 */
@Composable
private fun PdfPagedBody(
    ready: PdfReaderUiState.Ready,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onViewportWidth: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 56.dp.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pageTurnGestures(
                swipeThreshold = swipeThreshold,
                onPrev = onPrevPage,
                onNext = onNextPage,
            ),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }.roundToInt()
        LaunchedEffect(widthPx) { if (widthPx > 0) onViewportWidth(widthPx) }

        AnimatedContent(
            targetState = PdfSnapshot(ready.pageIndex, ready.image, ready.aspect),
            transitionSpec = {
                val forward = targetState.index >= initialState.index
                val enter = slideInHorizontally(animationSpec = tween(220)) { width ->
                    if (forward) width / 4 else -width / 4
                } + fadeIn(animationSpec = tween(160))
                val exit = slideOutHorizontally(animationSpec = tween(220)) { width ->
                    if (forward) -width / 4 else width / 4
                } + fadeOut(animationSpec = tween(160))
                enter togetherWith exit
            },
            label = "pdf-page",
        ) { snapshot ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                PdfPageSurface(
                    image = snapshot.image,
                    aspect = snapshot.aspect,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 滚动模式：整本连成一列，滚到哪页算哪页。 */
@Composable
private fun PdfScrollBody(
    ready: PdfReaderUiState.Ready,
    loadPage: suspend (Int, Int) -> ImageBitmap?,
    onScrollPage: (Int) -> Unit,
    onFlushPage: (Int) -> Unit,
    onVisiblePage: (Int) -> Unit = {},
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = ready.pageIndex,
    )
    val density = LocalDensity.current

    // 只汇报"第一屏可见的是第几页"，不每滚一像素就写库。
    // 注意：不能在 composition 里直接读 firstVisibleItemIndex（lint: FrequentlyChangingValue），
    // 用快照状态对象收集，退出时才读一次把页码落库。
    val lastPage = remember { mutableIntStateOf(0) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                lastPage.intValue = index
                onVisiblePage(index)
                onScrollPage(index)
            }
    }
    DisposableEffect(Unit) {
        onDispose { onFlushPage(lastPage.intValue) }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(
            items = List(ready.pageCount) { it },
            key = { index, _ -> index },
        ) { index, _ ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                val widthPx = with(density) { maxWidth.toPx() }.roundToInt()
                var image by remember(index) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(index, widthPx) {
                    if (image == null && widthPx > 0) {
                        image = loadPage(index, widthPx)
                    }
                }
                PdfPageSurface(
                    image = image,
                    aspect = ready.aspect,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 一页的画面：有位图就画位图，没有就留一张同比例的纸白占位。 */
@Composable
private fun PdfPageSurface(
    image: ImageBitmap?,
    aspect: Float,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalThemeTokens.current
    val styles = LocalTextStyles.current
    Box(
        modifier = modifier
            .aspectRatio(aspect.takeIf { it > 0f } ?: PdfReaderViewModel.DEFAULT_ASPECT)
            .background(tokens.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = stringResource(R.string.pdf_rendering),
                style = styles.kicker,
                color = tokens.textSecondary,
            )
        }
    }
}

/** 目录：PDF 没有章节，就用页码；一列数字比缩略图省内存，也更容易扫。 */
@Composable
private fun PdfTocSheet(
    pageCount: Int,
    currentPage: Int,
    onPick: (Int) -> Unit,
) {
    val tokens = LocalThemeTokens.current
    val styles = LocalTextStyles.current
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
                text = stringResource(R.string.pdf_toc_kicker, pageCount),
                style = styles.kicker,
                color = tokens.textSecondary,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 22.dp),
            color = tokens.divider,
            thickness = 1.dp,
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            itemsIndexed(
                items = List(pageCount) { it },
                key = { index, _ -> index },
            ) { index, _ ->
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(index) }
                            .padding(horizontal = 22.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.pdf_page, index + 1),
                            style = styles.tocChapter,
                            color = if (index == currentPage) tokens.accent else tokens.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (index == currentPage) {
                            Text(
                                text = stringResource(R.string.pdf_current),
                                style = styles.tocPage,
                                color = tokens.accent,
                            )
                        }
                    }
                    if (index != pageCount - 1) {
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

/**
 * 翻页动画两端的快照：和 TXT 那边同样的理由——动画期间外层状态已经变成新页，
 * 不打包快照的话退场那一页会被"偷走"。
 */
private data class PdfSnapshot(
    val index: Int,
    val image: ImageBitmap?,
    val aspect: Float,
)
