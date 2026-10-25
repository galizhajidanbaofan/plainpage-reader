package io.github.galizhajidanbaofan.plainpage.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = ready?.title ?: stringResource(R.string.app_name),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val chapter = ready?.chapterTitle.orEmpty()
                        if (chapter.isNotEmpty()) {
                            Text(
                                text = chapter,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                },
                actions = {
                    if (ready != null && ready.chapters.isNotEmpty()) {
                        TextButton(onClick = { tocOpen = true }) {
                            Text(stringResource(R.string.reader_toc))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (ready != null && ready.pageCount > 0) {
                PageBar(
                    pageIndex = ready.pageIndex,
                    pageCount = ready.pageCount,
                    fraction = ready.fraction,
                    canPrev = ready.canPrev,
                    canNext = ready.canNext,
                    onPrevPage = onPrevPage,
                    onNextPage = onNextPage,
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (state) {
                ReaderUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

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
    }

    if (tocOpen && ready != null) {
        ModalBottomSheet(onDismissRequest = { tocOpen = false }, sheetState = sheetState) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(
                    items = ready.chapters,
                    key = { index, _ -> index },
                ) { index, chapter ->
                    ListItem(
                        headlineContent = { Text(chapter.title) },
                        supportingContent = {
                            val page = (chapter.charStart / ReaderPager.DEFAULT_WINDOW_CHARS) + 1
                            Text(stringResource(R.string.reader_toc_page, page))
                        },
                        modifier = Modifier.clickable {
                            tocOpen = false
                            onChapter(index)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderBody(ready: ReaderUiState.Ready) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        if (ready.lossyDecoding) {
            Text(
                text = stringResource(R.string.reader_lossy),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        Text(
            text = ready.text,
            style = MaterialTheme.typography.bodyLarge.copy(
                lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.75f,
            ),
        )
    }
}

@Composable
private fun PageBar(
    pageIndex: Int,
    pageCount: Int,
    fraction: Float,
    canPrev: Boolean,
    canNext: Boolean,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onPrevPage, enabled = canPrev) {
                Text(stringResource(R.string.reader_prev_page))
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
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onNextPage, enabled = canNext) {
                Text(stringResource(R.string.reader_next_page))
            }
        }
    }
}

@Composable
private fun ReaderMessage(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
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
