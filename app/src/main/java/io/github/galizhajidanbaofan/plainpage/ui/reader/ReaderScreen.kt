package io.github.galizhajidanbaofan.plainpage.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.galizhajidanbaofan.plainpage.R
import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.ui.format.DisplayFormats
import io.github.galizhajidanbaofan.plainpage.ui.library.LibraryViewModel
import io.github.galizhajidanbaofan.plainpage.ui.shelf.label

/**
 * 阅读页占位。
 *
 * 现在只证明一件事：**从书架打开一本书、记下最近阅读时间、回到书架能看到排序变化**。
 * 真正的 ReaderEngine 渲染（分页/滚动、进度写回）在下一阶段接入，所以这里刻意不假装已有正文。
 */
@Composable
fun ReaderScreen(
    bookId: Long,
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bookFlow = remember(bookId) { viewModel.observeBook(bookId) }
    val book by bookFlow.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(bookId) {
        viewModel.markOpened(bookId)
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val current = book
            if (current == null) {
                Text(
                    text = stringResource(R.string.reader_missing),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.reader_book_meta,
                        current.format.label,
                        DisplayFormats.bytes(current.byteSize),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(20.dp))
                ProgressCard(book = current)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.reader_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.action_back))
            }
        }
    }
}

@Composable
private fun ProgressCard(book: Book) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val summary = DisplayFormats.progressSummary(book.readingLocation)
            Text(
                text = if (summary == null) {
                    stringResource(R.string.reader_progress_none)
                } else {
                    stringResource(R.string.reader_progress_known, summary)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = DisplayFormats.lastRead(System.currentTimeMillis(), book.lastOpenedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
