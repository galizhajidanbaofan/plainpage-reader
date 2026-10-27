package io.github.galizhajidanbaofan.plainpage.ui.shelf

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.galizhajidanbaofan.plainpage.R
import io.github.galizhajidanbaofan.plainpage.data.importing.SUPPORTED_MIME_TYPES
import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.domain.model.BookFormat
import io.github.galizhajidanbaofan.plainpage.ui.ShelfLayout
import io.github.galizhajidanbaofan.plainpage.ui.format.DisplayFormats
import io.github.galizhajidanbaofan.plainpage.ui.theme.LocalTextStyles
import io.github.galizhajidanbaofan.plainpage.ui.theme.LocalThemeTokens

/** 内容左右留白。整套界面共用这一个基准，避免每屏各自为政。 */
private val PAGE_MARGIN = 22.dp

/**
 * 书架。列表/网格只差渲染结构，数据、点击、导入回调完全相同 —— 布局判断只出现在这里。
 *
 * 视觉取向是纸与墨：没有卡片阴影、没有大面积色块，靠留白、发丝线和字号层级分区；
 * 唯一的强调色出现在"可点的字"和"读到哪儿"的进度线上。
 */
@Composable
fun ShelfScreen(
    layout: ShelfLayout,
    books: List<Book>,
    modifier: Modifier = Modifier,
    /** 读书脊上的封面图；返回 null 时用占位样式（TXT/PDF、抽图失败的 EPUB）。 */
    loadCover: suspend (Book) -> android.graphics.Bitmap? = { null },
    importing: Boolean,
    onImport: (List<android.net.Uri>) -> Unit,
    onOpen: (Book) -> Unit,
    onDelete: (Book) -> Unit,
) {
    val context = LocalContext.current
    val tokens = LocalThemeTokens.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        // 复制完其实不再需要原文件访问权，但持久授权能让「来源」在重启后仍可校验，
        // 个别 provider 不支持持久授权，失败是正常的，不能让它打断导入。
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        onImport(uris)
    }
    val openPicker: () -> Unit = { picker.launch(SUPPORTED_MIME_TYPES) }

    var pendingDelete by remember { mutableStateOf<Book?>(null) }

    Surface(color = tokens.paper, modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ShelfHeader(count = books.size, importing = importing, onImport = openPicker)
            HorizontalDivider(color = tokens.divider, thickness = 1.dp)
            if (books.isEmpty()) {
                EmptyShelf(onImport = openPicker, modifier = Modifier.fillMaxSize())
            } else {
                when (layout) {
                    ShelfLayout.LIST -> BookList(
                        books = books,
                        loadCover = loadCover,
                        onOpen = onOpen,
                        onRequestDelete = { pendingDelete = it },
                    )

                    ShelfLayout.GRID -> BookGrid(
                        books = books,
                        loadCover = loadCover,
                        onOpen = onOpen,
                        onRequestDelete = { pendingDelete = it },
                    )
                }
            }
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.shelf_delete_title)) },
            text = { Text(stringResource(R.string.shelf_delete_message, book.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(book)
                        pendingDelete = null
                    },
                ) { Text(stringResource(R.string.action_delete), color = tokens.accent) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel), color = tokens.textSecondary)
                }
            },
        )
    }
}

/** 页眉：品牌字 + 一句定位信息 + 导入入口。导入放在这里而不是浮动按钮，把手势空间留给书。 */
@Composable
private fun ShelfHeader(count: Int, importing: Boolean, onImport: () -> Unit) {
    val tokens = LocalThemeTokens.current
    val styles = LocalTextStyles.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PAGE_MARGIN, end = 10.dp, top = 22.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.app_name),
                style = styles.brand,
                color = tokens.textPrimary,
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = onImport,
                enabled = !importing,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = if (importing) {
                        stringResource(R.string.shelf_importing)
                    } else {
                        stringResource(R.string.shelf_import)
                    },
                    style = styles.screenTitle,
                    color = if (importing) tokens.textSecondary else tokens.accent,
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.shelf_kicker_count, count),
            style = styles.kicker,
            color = tokens.textSecondary,
        )
    }
}

@Composable
private fun BookList(
    books: List<Book>,
    loadCover: suspend (Book) -> android.graphics.Bitmap?,
    onOpen: (Book) -> Unit,
    onRequestDelete: (Book) -> Unit,
) {
    val tokens = LocalThemeTokens.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        items(books, key = { it.id }) { book ->
            BookListRow(
                book = book,
                loadCover = loadCover,
                onOpen = { onOpen(book) },
                onRequestDelete = { onRequestDelete(book) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = PAGE_MARGIN, end = PAGE_MARGIN),
                color = tokens.divider,
                thickness = 1.dp,
            )
        }
    }
}

@Composable
private fun BookListRow(
    book: Book,
    loadCover: suspend (Book) -> android.graphics.Bitmap?,
    onOpen: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val tokens = LocalThemeTokens.current
    val styles = LocalTextStyles.current
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onRequestDelete)
            .padding(start = PAGE_MARGIN, end = 6.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(
            book = book,
            loadCover = loadCover,
            modifier = Modifier.size(width = 46.dp, height = 62.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = styles.bookTitle,
                color = tokens.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.shelf_meta,
                    book.format.label,
                    DisplayFormats.bytes(book.byteSize),
                    DisplayFormats.lastRead(System.currentTimeMillis(), book.lastOpenedAt),
                ),
                style = styles.meta,
                color = tokens.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val fraction = DisplayFormats.barFraction(book.readingLocation)
            // 有进度条就不再重复"全文 42%"：一根线已经把这句说完了。
            val summary = if (fraction == null) {
                DisplayFormats.progressSummary(book.readingLocation)
            } else {
                null
            }
            if (summary != null) {
                Spacer(modifier = Modifier.height(9.dp))
                Text(
                    text = summary,
                    style = styles.progress,
                    color = tokens.accent,
                )
            }
            if (fraction != null) {
                Spacer(modifier = Modifier.height(6.dp))
                ProgressLine(
                    fraction = fraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 18.dp),
                )
            }
        }
        Box {
            val moreLabel = stringResource(R.string.action_more)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .semantics { contentDescription = moreLabel }
                    .clickable { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "⋯",
                    style = styles.screenTitle,
                    color = tokens.textSecondary,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete), color = tokens.textPrimary) },
                    onClick = {
                        menuOpen = false
                        onRequestDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun BookGrid(
    books: List<Book>,
    loadCover: suspend (Book) -> android.graphics.Bitmap?,
    onOpen: (Book) -> Unit,
    onRequestDelete: (Book) -> Unit,
) {
    val tokens = LocalThemeTokens.current
    val styles = LocalTextStyles.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 136.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = PAGE_MARGIN, end = PAGE_MARGIN, top = 20.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(books, key = { it.id }) { book ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onOpen(book) },
                        onLongClick = { onRequestDelete(book) },
                    ),
            ) {
                BookCover(
                    book = book,
                    loadCover = loadCover,
                    monogram = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.74f),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = book.title,
                    style = styles.bookTitleCompact,
                    color = tokens.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = DisplayFormats.lastRead(System.currentTimeMillis(), book.lastOpenedAt),
                    style = styles.meta,
                    color = tokens.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val fraction = DisplayFormats.barFraction(book.readingLocation)
                if (fraction != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ProgressLine(fraction = fraction, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * 封面占位：纸面底纹 + 一道书脊线 + 格式小字。
 * 色相按书名稳定地偏一点点强调色，书与书之间像同一批纸印出来的，不会各色打架。
 */
@Composable
private fun BookCover(
    book: Book,
    modifier: Modifier = Modifier,
    loadCover: suspend (Book) -> android.graphics.Bitmap? = { null },
    monogram: Boolean = false,
) {
    val tokens = LocalThemeTokens.current
    val styles = LocalTextStyles.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
    // 封面解码走 CoverStore 的缓存，挂在 book.id/coverPath 上：换书或封面补齐后重新取一次。
    val cover by produceState<ImageBitmap?>(
        initialValue = null,
        book.id,
        book.coverPath,
    ) {
        value = loadCover(book)?.asImageBitmap()
    }

    if (cover != null) {
        // 有真封面就直接铺满：不加边框、不加装饰字，让图自己说话。
        Image(
            bitmap = cover!!,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .clip(shape)
                .border(1.dp, tokens.divider, shape),
        )
        return
    }

    val wash = coverWashFor(book, tokens.coverWash, tokens.accent)
    Box(
        modifier = modifier
            .border(1.dp, tokens.divider, shape)
            .background(wash, shape),
    ) {
        // 书脊：左侧一道略深的线，让人一眼看出这是"一本书"而不是一块色块。
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(3.dp)
                .background(tokens.accent.copy(alpha = 0.22f)),
        )
        if (monogram) {
            // 内框 + 首字：像印在封面上的一个装饰字，把大片留白变成版面而不是空白。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .border(1.dp, tokens.divider.copy(alpha = 0.7f), shape),
            )
            Text(
                text = book.title.take(2),
                style = styles.emptyTitle.copy(fontSize = 46.sp, lineHeight = 56.sp),
                color = tokens.accent.copy(alpha = 0.20f),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Text(
            text = book.format.label,
            style = styles.kicker.copy(fontSize = 9.sp, letterSpacing = 0.9.sp),
            color = tokens.textSecondary,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 9.dp, bottom = 7.dp, end = 6.dp),
        )
    }
}

/** 书名哈希决定四档深浅之一，稳定且不刺眼。 */
private fun coverWashFor(book: Book, base: Color, accent: Color): Color {
    val step = (book.title.hashCode().mod(4) + 4) % 4
    return lerp(base, accent, 0.06f + step * 0.035f)
}

/** 读到哪儿：2dp 的墨线，只在手里有真实全文比例时才画（TXT）。 */
@Composable
private fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    val tokens = LocalThemeTokens.current
    Box(
        modifier = modifier
            .height(2.dp)
            .background(tokens.divider),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .height(2.dp)
                .background(tokens.accent),
        )
    }
}

@Composable
private fun EmptyShelf(onImport: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalThemeTokens.current
    val styles = LocalTextStyles.current
    Column(
        modifier = modifier.padding(horizontal = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.shelf_empty_kicker),
            style = styles.kicker,
            color = tokens.accent,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(1.dp)
                .background(tokens.divider),
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.shelf_empty_title),
            style = styles.emptyTitle,
            color = tokens.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.shelf_empty_hint),
            style = styles.emptyHint,
            color = tokens.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp),
        )
        Spacer(modifier = Modifier.height(26.dp))
        OutlinedButton(
            onClick = onImport,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
            border = BorderStroke(1.dp, tokens.accent),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.shelf_import_cta),
                style = styles.screenTitle,
                color = tokens.accent,
            )
        }
    }
}

/** 格式短标签，用于封面占位与列表元信息。 */
val BookFormat.label: String
    get() = when (this) {
        BookFormat.EPUB -> "EPUB"
        BookFormat.TXT -> "TXT"
        BookFormat.PDF -> "PDF"
    }
