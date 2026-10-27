package io.github.galizhajidanbaofan.plainpage.domain.engine.pdf

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.galizhajidanbaofan.plainpage.domain.engine.BookMetadata
import io.github.galizhajidanbaofan.plainpage.domain.engine.OutlineItem
import io.github.galizhajidanbaofan.plainpage.domain.engine.ReaderEngine
import io.github.galizhajidanbaofan.plainpage.domain.engine.ReaderException
import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.domain.model.ReadingLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF 引擎：用平台自带的 `PdfRenderer` 一页一页画成位图。
 *
 * 为什么不用 PdfBox-Android：它 Apache-2.0 合规，但依赖链拖着 BouncyCastle 三件套
 * （数 MB、反射多、R8 难裁）；P0 只需要"能看"，`PdfRenderer` 是系统自带的，零依赖零包体。
 * 代价是拿不到文字层（不能选词、不能搜索）——这两件事 P0 本来就不做。
 *
 * 线程约定：`PdfRenderer` 不是线程安全的，所有渲染都串到 [renderLock] 上；
 * 单页解码本身在 `Dispatchers.IO`，不占主线程。
 */
class PdfReaderEngine(
    private val progressSink: (suspend (ReadingLocation, Float) -> Unit)? = null,
) : ReaderEngine {

    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var book: Book? = null

    /** 每页的点尺寸（72 dpi），用于 placeholder 占位与实际渲染。 */
    private val pageSizes = HashMap<Int, PagePixelSize>()

    private val renderLock = Mutex()

    /**
     * 已渲染页的位图缓存。翻页时上一页/下一页常常还要回来（来回对比、误触），
     * 留三张就够——再多只是把内存从一个坑挪到另一个坑。
     */
    private val bitmapCache = object : LinkedHashMap<Int, ImageBitmap>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ImageBitmap>): Boolean =
            size > CACHE_SIZE
    }

    /** 总页数；未打开时为 0。 */
    val pageCount: Int
        get() = renderer?.pageCount ?: 0

    override suspend fun open(book: Book): BookMetadata = withContext(Dispatchers.IO) {
        close()

        val file = File(book.localPath)
        if (!file.isFile) {
            throw ReaderException(ReaderException.Reason.FILE_MISSING, "文件不在了：${book.localPath}")
        }
        val opened = runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrElse {
            throw ReaderException(ReaderException.Reason.FILE_MISSING, "打不开文件：${book.localPath}", it)
        }
        val created = try {
            PdfRenderer(opened)
        } catch (error: Exception) {
            // 加密、损坏、非 PDF 内容都会在这里抛：对读者来说都是"这本打不开"。
            runCatching { opened.close() }
            throw ReaderException(ReaderException.Reason.CORRUPTED, "PDF 损坏或加密，打不开", error)
        }
        if (created.pageCount <= 0) {
            runCatching { created.close() }
            runCatching { opened.close() }
            throw ReaderException(ReaderException.Reason.EMPTY_CONTENT, "这个 PDF 没有页面")
        }

        this@PdfReaderEngine.book = book
        descriptor = opened
        renderer = created
        bitmapCache.clear()
        pageSizes.clear()

        BookMetadata(title = null, author = null, pageCount = created.pageCount)
    }

    override suspend fun loadOutline(): List<OutlineItem> {
        val count = pageCount
        if (count <= 0) return emptyList()
        return (0 until count).map { index ->
            OutlineItem(
                id = "page-$index",
                title = "第 ${index + 1} 页",
                level = 0,
                location = ReadingLocation.Pdf(pageIndex = index, fractionInPage = 0f),
            )
        }
    }

    override suspend fun restoreProgress(): ReadingLocation? {
        val saved = book?.readingLocation as? ReadingLocation.Pdf ?: return null
        if (pageCount <= 0) return null
        return ReadingLocation.Pdf(
            pageIndex = PdfPageMath.clampPage(saved.pageIndex, pageCount),
            fractionInPage = saved.fractionInPage.coerceIn(0f, 1f),
        )
    }

    override suspend fun saveProgress(location: ReadingLocation, fraction: Float) {
        progressSink?.invoke(location, fraction)
    }

    override fun close() {
        bitmapCache.clear()
        pageSizes.clear()
        runCatching { renderer?.close() }
        renderer = null
        runCatching { descriptor?.close() }
        descriptor = null
        book = null
    }

    /** 该页的高宽比（宽/高）；读不到时返回 null，UI 用默认 A4 比例占位。 */
    suspend fun aspectOf(index: Int): Float? = withContext(Dispatchers.IO) {
        val size = sizeOf(index) ?: return@withContext null
        size.width.toFloat() / size.height
    }

    /**
     * 把第 [index] 页渲染成位图。宽度由调用方按屏幕宽度给（px）。
     * 同一页重复请求命中缓存；渲染失败返回 null，由 UI 显示占位，不崩。
     */
    suspend fun pageImage(index: Int, targetWidthPx: Int): ImageBitmap? = renderLock.withLock {
        val active = renderer ?: return@withLock null
        if (index !in 0 until active.pageCount) return@withLock null
        bitmapCache[index]?.let { return@withLock it }

        val rendered = withContext(Dispatchers.IO) {
            runCatching {
                active.openPage(index).use { page ->
                    val size = PdfPageMath.bitmapSize(page.width, page.height, targetWidthPx)
                    pageSizes[index] = PagePixelSize(page.width, page.height)
                    val bitmap = createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                    // PDF 页面通常没有背景色，不清白的话深色主题下会变成黑底黑字。
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap()
                }
            }.getOrNull()
        }
        if (rendered != null) bitmapCache[index] = rendered
        rendered
    }

    private suspend fun sizeOf(index: Int): PagePixelSize? = renderLock.withLock {
        val active = renderer ?: return@withLock null
        if (index !in 0 until active.pageCount) return@withLock null
        pageSizes[index]?.let { return@withLock it }
        withContext(Dispatchers.IO) {
            runCatching {
                active.openPage(index).use { page ->
                    PagePixelSize(page.width, page.height).also { pageSizes[index] = it }
                }
            }.getOrNull()
        }
    }

    private companion object {
        const val CACHE_SIZE = 3
    }
}
