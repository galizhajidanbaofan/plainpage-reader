package io.github.galizhajidanbaofan.plainpage.ui.recent

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.galizhajidanbaofan.plainpage.R
import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.ui.format.DisplayFormats
import io.github.galizhajidanbaofan.plainpage.ui.shelf.label
import io.github.galizhajidanbaofan.plainpage.ui.theme.LocalTextStyles
import io.github.galizhajidanbaofan.plainpage.ui.theme.LocalThemeTokens

/**
 * 最近阅读：只列打开过的书，顺序由仓库保证（最近打开时间倒序）。
 *
 * 和书架同一套纸墨语言：没有卡片、没有按钮，时间退到灰色小字，
 * 只把"读到哪儿"用强调色的数字留在视线里 —— 因为来这一页的人只想接着读。
 */
@Composable
fun RecentScreen(
    books: List<Book>,
    onOpen: (Book) -> Unit,
    onDelete: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalThemeTokens.current
    val styles = LocalTextStyles.current
    Surface(color = tokens.paper, modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.recent_kicker, books.size),
                    style = styles.kicker,
                    color = tokens.textSecondary,
                )
            }
            HorizontalDivider(color = tokens.divider, thickness = 1.dp)
            if (books.isEmpty()) {
                EmptyRecent(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    itemsIndexed(books, key = { _, book -> book.id }) { index, book ->
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onOpen(book) },
                                        onLongClick = { onDelete(book) },
                                    )
                                    .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 16.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = book.title,
                                        style = styles.bookTitle,
                                        color = tokens.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row {
                                        Text(
                                            text = DisplayFormats.lastRead(
                                                System.currentTimeMillis(),
                                                book.lastOpenedAt,
                                            ),
                                            style = styles.meta,
                                            color = tokens.textSecondary,
                                        )
                                        DisplayFormats.progressSummary(book.readingLocation)?.let { summary ->
                                            Text(
                                                text = " · $summary",
                                                style = styles.progress,
                                                color = tokens.accent,
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(0.dp))
                                Text(
                                    text = book.format.label,
                                    style = styles.kicker,
                                    color = tokens.textSecondary,
                                    modifier = Modifier.padding(start = 12.dp, top = 3.dp),
                                )
                            }
                            if (index != books.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 22.dp, end = 22.dp),
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
private fun EmptyRecent(modifier: Modifier = Modifier) {
    val tokens = LocalThemeTokens.current
    val styles = LocalTextStyles.current
    Column(
        modifier = modifier.padding(horizontal = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.recent_empty_title),
            style = styles.emptyTitle,
            color = tokens.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.recent_empty_hint),
            style = styles.emptyHint,
            color = tokens.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
