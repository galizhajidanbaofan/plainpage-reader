package io.github.galizhajidanbaofan.plainpage.ui.reader.pdf

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.galizhajidanbaofan.plainpage.di.AppContainer
import io.github.galizhajidanbaofan.plainpage.domain.engine.ReaderException
import io.github.galizhajidanbaofan.plainpage.domain.engine.pdf.PdfPageMath
import io.github.galizhajidanbaofan.plainpage.domain.engine.pdf.PdfReaderEngine
import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.domain.model.ReadingLocation
import io.github.galizhajidanbaofan.plainpage.domain.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** PDF 阅读页状态。和 TXT/EPUB 的字符窗口不同，这里的"一屏"就是"一页位图"。 */
sealed interface PdfReaderUiState {

    data object Loading : PdfReaderUiState

    data class Failed(val reason: ReaderException.Reason) : PdfReaderUiState

    data class Ready(
        val title: String,
        val pageIndex: Int,
        val pageCount: Int,
        val fraction: Float,
        /** 当前页的位图；还没画出来时为 null，UI 显示"正在排版这一页"。 */
        val image: ImageBitmap?,
        /** 页宽高比，占位框按它留高度，避免图片到位时页面跳一下。 */
        val aspect: Float,
    ) : PdfReaderUiState
}

/**
 * PDF 的阅读状态。
 *
 * 为什么不复用 [io.github.galizhajidanbaofan.plainpage.ui.reader.ReaderViewModel]：
 * 那个 ViewModel 的核心是"全书字符偏移 + 定长窗口"，PDF 根本没有字符坐标，硬套只能造出一堆假偏移。
 * 两边共用的是引擎接口、位置编码、主题与手势，而不是状态机。
 */
class PdfReaderViewModel(
    private val bookId: Long,
    private val repository: BookRepository,
    private val engineFactory: (Book) -> PdfReaderEngine?,
) : ViewModel() {

    private val _state = MutableStateFlow<PdfReaderUiState>(PdfReaderUiState.Loading)
    val state: StateFlow<PdfReaderUiState> = _state.asStateFlow()

    /** 滚动模式顶部的细进度线；翻页模式不读它。 */
    private val _scrollFraction = MutableStateFlow(0f)
    val scrollFraction: StateFlow<Float> = _scrollFraction.asStateFlow()

    private var engine: PdfReaderEngine? = null
    private var pageCount = 0
    private var pageIndex = 0
    private var viewportWidth = 0
    private var title = ""
    private var pendingSave: Job? = null

    /** 滚动模式要一次显示很多页，缓存比翻页模式大一些（约 24 MB 上限）。 */
    private val bitmapCache = object : LinkedHashMap<Int, ImageBitmap>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ImageBitmap>): Boolean =
            size > CACHE_SIZE
    }

    init {
        viewModelScope.launch { open() }
    }

    private suspend fun open() {
        _state.value = PdfReaderUiState.Loading
        val book = repository.find(bookId)
        if (book == null) {
            _state.value = PdfReaderUiState.Failed(ReaderException.Reason.FILE_MISSING)
            return
        }
        title = book.title
        repository.markOpened(bookId, System.currentTimeMillis())

        val created = engineFactory(book)
        if (created == null) {
            _state.value = PdfReaderUiState.Failed(ReaderException.Reason.UNKNOWN)
            return
        }
        engine = created
        try {
            created.open(book)
        } catch (error: ReaderException) {
            _state.value = PdfReaderUiState.Failed(error.reason)
            return
        }
        pageCount = created.pageCount
        if (pageCount <= 0) {
            _state.value = PdfReaderUiState.Failed(ReaderException.Reason.EMPTY_CONTENT)
            return
        }
        val restored = (created.restoreProgress() as? ReadingLocation.Pdf)?.pageIndex ?: 0
        pageIndex = PdfPageMath.clampPage(restored, pageCount)
        render()
    }

    /**
     * 屏幕宽度变了（旋转、分屏、字体缩放）才重画。
     * 差 32px 以内不重画：Compose 会给几次几乎相同的宽度，每次都重新解码纯属浪费。
     */
    fun onViewportWidth(widthPx: Int) {
        if (widthPx <= 0) return
        if (viewportWidth != 0 && abs(widthPx - viewportWidth) < WIDTH_CHANGE_THRESHOLD) {
            viewportWidth = widthPx
            return
        }
        viewportWidth = widthPx
        viewModelScope.launch { render() }
    }

    fun nextPage() {
        if (pageIndex >= pageCount - 1) return
        pageIndex++
        viewModelScope.launch { render() }
    }

    fun prevPage() {
        if (pageIndex <= 0) return
        pageIndex--
        viewModelScope.launch { render() }
    }

    fun goToPage(index: Int) {
        val target = PdfPageMath.clampPage(index, pageCount)
        if (target == pageIndex) return
        pageIndex = target
        viewModelScope.launch { render() }
    }

    /** 滚动模式取某一页的位图；命中缓存直接返回。 */
    suspend fun pageBitmap(index: Int, widthPx: Int): ImageBitmap? {
        if (index !in 0 until pageCount) return null
        val width = if (widthPx > 0) widthPx else viewportWidth
        bitmapCache[index]?.let { return it }
        val image = engine?.pageImage(index, width) ?: return null
        bitmapCache[index] = image
        return image
    }

    /** 滚动模式汇报"现在看到第几页"：更新进度线并防抖落库。 */
    fun onScrollPage(index: Int) {
        if (pageCount <= 0) return
        val clamped = PdfPageMath.clampPage(index, pageCount)
        _scrollFraction.value = PdfPageMath.fractionOf(clamped, pageCount)
        pendingSave?.cancel()
        pendingSave = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            save(clamped)
        }
    }

    /** 退出阅读页时立刻落一次，不等防抖。 */
    fun flushPage(index: Int) {
        if (pageCount <= 0) return
        pendingSave?.cancel()
        viewModelScope.launch { save(PdfPageMath.clampPage(index, pageCount)) }
    }

    private suspend fun pdfAspect(index: Int): Float =
        engine?.aspectOf(index) ?: DEFAULT_ASPECT

    private suspend fun render() {
        val active = engine ?: return
        val index = PdfPageMath.clampPage(pageIndex, pageCount)
        val width = if (viewportWidth > 0) viewportWidth else FALLBACK_WIDTH_PX
        val aspect = withContext(Dispatchers.Default) { pdfAspect(index) }

        // 先亮出"正在排版这一页"的骨架，再换成位图：大页 PDF 解码要几十到几百毫秒。
        _state.value = PdfReaderUiState.Ready(
            title = title,
            pageIndex = index,
            pageCount = pageCount,
            fraction = PdfPageMath.fractionOf(index, pageCount),
            image = bitmapCache[index],
            aspect = aspect,
        )
        val image = pageBitmap(index, width)
        _state.value = PdfReaderUiState.Ready(
            title = title,
            pageIndex = index,
            pageCount = pageCount,
            fraction = PdfPageMath.fractionOf(index, pageCount),
            image = image,
            aspect = aspect,
        )
        _scrollFraction.value = PdfPageMath.fractionOf(index, pageCount)
        pendingSave?.cancel()
        pendingSave = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            save(index)
        }
        // 预取下一页，翻过去时不用等解码。
        if (index + 1 < pageCount) {
            viewModelScope.launch { pageBitmap(index + 1, width) }
        }
    }

    private suspend fun save(index: Int) {
        val active = engine ?: return
        val fraction = PdfPageMath.fractionOf(index, pageCount)
        active.saveProgress(ReadingLocation.Pdf(pageIndex = index, fractionInPage = 0f), fraction)
    }

    override fun onCleared() {
        engine?.close()
        engine = null
        bitmapCache.clear()
    }

    companion object {
        private const val CACHE_SIZE = 6
        private const val SAVE_DEBOUNCE_MS = 300L
        private const val WIDTH_CHANGE_THRESHOLD = 32
        private const val FALLBACK_WIDTH_PX = 1080

        /** A4 的高宽比，占位时用。 */
        const val DEFAULT_ASPECT = 210f / 297f

        fun factory(bookId: Long, container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PdfReaderViewModel(
                        bookId = bookId,
                        repository = container.bookRepository,
                        engineFactory = { book -> container.createEngine(book) as? PdfReaderEngine },
                    )
                }
            }
    }
}
