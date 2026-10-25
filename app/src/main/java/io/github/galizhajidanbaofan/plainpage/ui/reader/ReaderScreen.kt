package io.github.galizhajidanbaofan.plainpage.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.galizhajidanbaofan.plainpage.R
import io.github.galizhajidanbaofan.plainpage.di.AppContainer
import io.github.galizhajidanbaofan.plainpage.domain.engine.ReaderException
import io.github.galizhajidanbaofan.plainpage.ui.theme.LocalTextStyles
import io.github.galizhajidanbaofan.plainpage.ui.theme.LocalThemeTokens
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
    ReaderContent(
        state = state,
        onBack = onBack,
        onPrevPage = viewModel::prevPage,
        onNextPage = viewModel::nextPage,
        onChapter = viewModel::goToChapter,
        modifier = modifier,
    )
}

/**
 * 阅读页只留三样东西：回到书的入口、当前这一章、以及"读到哪里"。
 * 上下各一条发丝线把正文框住，像纸页的天头地脚；其余全部让给字。
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

                    is ReaderUiState.Ready -> ReaderBody(state)
                }
            }
            if (ready != null && ready.pageCount > 0) {
                HorizontalDivider(color = tokens.divider, thickness = 1.dp)
                PageFooter(
                    pageIndex = ready.pageIndex,
                    pageCount = ready.pageCount,
                    fraction = ready.fraction,
                    canPrev = ready.canPrev,
                    canNext = ready.canNext,
                    onPrevPage = onPrevPage,
                    onNextPage = onNextPage,
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
                                val page = (chapter.charStart / ReaderPager.DEFAULT_WINDOW_CHARS) + 1
                                Text(
                                    text = stringResource(R.string.reader_toc_page, page),
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

/** 短于此长度的行按章节标题处理，不做段首缩进。 */
private const val HEADING_MAX_CHARS = 24

@Composable
private fun ReaderBody(ready: ReaderUiState.Ready) {
    val tokens = LocalThemeTokens.current
    val styles = LocalTextStyles.current
    val scroll = rememberScrollState()
    // 中文排版的段首缩进：Compose 的 textIndent 只作用于整块文本，所以按行补两个全角空格。
    // 短行（章节标题、署名、诗句）不缩进 —— 用长度做启发式，比给每行加类型标记省事且不易出错。
    val indented = remember(ready.text) {
        ready.text.lineSequence().joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                line.trim().length <= HEADING_MAX_CHARS -> line.trimStart()
                else -> "　　" + line.trimStart()
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 26.dp, vertical = 24.dp),
    ) {
        if (ready.lossyDecoding) {
            Text(
                text = stringResource(R.string.reader_lossy),
                style = styles.meta,
                color = tokens.accent,
            )
            Spacer(modifier = Modifier.height(14.dp))
        }
        Text(
            text = indented,
            style = styles.readingBody,
            color = tokens.textPrimary,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PageFooter(
    pageIndex: Int,
    pageCount: Int,
    fraction: Float,
    canPrev: Boolean,
    canNext: Boolean,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
) {
    val tokens = LocalThemeTokens.current
    val styles = LocalTextStyles.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrevPage, enabled = canPrev) {
            Text(
                text = stringResource(R.string.reader_prev_page),
                style = styles.pageFooter,
                color = if (canPrev) tokens.accent else tokens.textSecondary.copy(alpha = 0.45f),
            )
        }
        Text(
            text = stringResource(
                R.string.reader_page_progress,
                pageIndex + 1,
                pageCount,
                (fraction * 100).roundToInt(),
            ),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = styles.pageFooter,
            color = tokens.textSecondary,
        )
        TextButton(onClick = onNextPage, enabled = canNext) {
            Text(
                text = stringResource(R.string.reader_next_page),
                style = styles.pageFooter,
                color = if (canNext) tokens.accent else tokens.textSecondary.copy(alpha = 0.45f),
            )
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
