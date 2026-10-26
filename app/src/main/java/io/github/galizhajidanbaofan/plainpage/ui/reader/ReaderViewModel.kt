package io.github.galizhajidanbaofan.plainpage.ui.reader

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.galizhajidanbaofan.plainpage.domain.repository.BookRepository
import io.github.galizhajidanbaofan.plainpage.di.AppContainer
import io.github.galizhajidanbaofan.plainpage.domain.engine.PaginatedEngine
import io.github.galizhajidanbaofan.plainpage.domain.engine.PictureRef
import io.github.galizhajidanbaofan.plainpage.domain.engine.PictureSource
import io.github.galizhajidanbaofan.plainpage.domain.engine.ReaderEngine
import io.github.galizhajidanbaofan.plainpage.domain.engine.ReaderException
import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.domain.model.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 目录项：标题 + 在正文里的字符起点（EPUB 以后换成 href）。 */
data class ReaderChapter(val title: String, val charStart: Int)

/** 一屏里的插图：已经解码好、可以直接画。 */
data class ReaderPicture(val bitmap: ImageBitmap, val caption: String?)

sealed interface ReaderUiState {

    data object Loading : ReaderUiState

    data class Failed(val reason: ReaderException.Reason) : ReaderUiState

    /** 该格式的引擎还没接（当前 EPUB/PDF）。 */
    data class Unsupported(val format: BookFormat) : ReaderUiState

    data class Ready(
        val title: String,
        val chapterTitle: String,
        val chapters: List<ReaderChapter>,
        val text: String,
        val pageIndex: Int,
        val pageCount: Int,
        val fraction: Float,
        val canPrev: Boolean,
        val canNext: Boolean,
        val lossyDecoding: Boolean,
        /** 这一屏是整页插图（EPUB 里的图）；此时 [text] 为空，内容看 [picture]。 */
        val picture: ImageBitmap? = null,
        val pictureCaption: String? = null,
        /** 全文字符数；滚动模式据此切块。 */
        val charCount: Int = 0,
        /**
         * 当前这一屏（翻页模式为窗口起点，滚动模式为可见块起点）在全书中字符偏移。
         * 滚动模式用它决定「进来时滚到哪」「跳章后滚到哪」。
         */
        val anchorChar: Int = 0,
    ) : ReaderUiState
}

class ReaderViewModel(
    private val bookId: Long,
    private val repository: BookRepository,
    private val engineFactory: (Book) -> ReaderEngine?,
) : ViewModel() {

    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private val renderLock = Mutex()

    private var engine: ReaderEngine? = null
    private var pager: ReaderPager? = null
    /** 当前书的插图锚点（按字符偏移升序）；TXT 为空。 */
    private var pictures: List<PictureRef> = emptyList()
    /** 翻页模式的屏序列：正文屏 + 插图纸。插图纸排在它所属正文屏之后。 */
    private var slots: List<PageSlots.Slot> = emptyList()
    private var slotIndex: Int = 0
    /** 插图解码缓存：按路径存，容量很小——同时只可能看到一两张。 */
    private val pictureCache = object : LinkedHashMap<String, ImageBitmap>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>): Boolean =
            size > MAX_CACHED_PICTURES
    }
    private var chapters: List<ReaderChapter> = emptyList()
    private var title: String = ""
    private var startChar: Int = 0

    /** 滚动模式顶部的细进度线；翻页模式不读它。 */
    private val _scrollFraction = MutableStateFlow(0f)
    val scrollFraction: StateFlow<Float> = _scrollFraction.asStateFlow()

    /**
     * 滚动模式的块缓存。访问顺序链表，超过上限就丢最久没用的一块——
     * 来回滚动时不至于每次都去读磁盘。
     */
    private val chunkCache = object : LinkedHashMap<Int, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>): Boolean =
            size > MAX_CACHED_CHUNKS
    }
    private val chunkLock = Mutex()
    private var pendingSave: Job? = null

    init {
        viewModelScope.launch { open() }
    }

    private suspend fun open() {
        _state.value = ReaderUiState.Loading
        val book = repository.find(bookId)
        if (book == null) {
            _state.value = ReaderUiState.Failed(ReaderException.Reason.FILE_MISSING)
            return
        }
        title = book.title
        repository.markOpened(bookId, System.currentTimeMillis())

        val created = engineFactory(book)
        if (created == null) {
            _state.value = ReaderUiState.Unsupported(book.format)
            return
        }
        engine = created

        try {
            created.open(book)
        } catch (error: ReaderException) {
            _state.value = ReaderUiState.Failed(error.reason)
            return
        }

        val paginated = created as? PaginatedEngine
        if (paginated == null) {
            _state.value = ReaderUiState.Unsupported(book.format)
            return
        }

        chapters = created.loadOutline().mapNotNull { item ->
            // 目录项的位置可能是 Txt 偏移，也可能是 Epub 的 href：统一由引擎换算成全书偏移。
            val offset = paginated.charOffsetOf(item.location) ?: return@mapNotNull null
            ReaderChapter(title = item.title, charStart = offset)
        }
        if (chapters.isEmpty()) {
            _state.value = ReaderUiState.Failed(ReaderException.Reason.EMPTY_CONTENT)
            return
        }
        val activePager = ReaderPager(paginated.charCount)
        pager = activePager
        pictures = (paginated as? PictureSource)?.pictures.orEmpty()
        slots = PageSlots.build(
            pageCount = activePager.pageCount,
            windowChars = activePager.windowChars,
            pictures = pictures,
            startOfPage = activePager::startOf,
        )

        val restoredOffset = created.restoreProgress()?.let(paginated::charOffsetOf)
        startChar = activePager.clampToPage(restoredOffset ?: 0)
        slotIndex = PageSlots.slotOfPage(slots, activePager.pageOf(startChar))
        render()
    }

    /** 重新读一次当前窗口；翻页、跳章、初次打开都走这里。 */
    private suspend fun render() = renderLock.withLock {
        val paginated = engine as? PaginatedEngine
        val activePager = pager
        if (paginated == null || activePager == null) return@withLock

        val slot = slots.getOrNull(slotIndex)
        // 插图纸没有自己的字符坐标，就用图所在的那个间隙当锚点：进度、目录、章节名都还能对。
        val anchor = when (slot) {
            is PageSlots.Slot.Picture -> slot.ref.at.coerceIn(0, maxOf(0, activePager.charCount - 1))
            is PageSlots.Slot.Text -> slot.startChar
            null -> startChar
        }
        startChar = anchor
        val chapter = chapters.lastOrNull { it.charStart <= anchor } ?: chapters.firstOrNull()
        val fraction = activePager.fraction(anchor)
        paginated.positionOf(anchor)?.let { location ->
            paginated.saveProgress(location, fraction)
        }

        val picture = (slot as? PageSlots.Slot.Picture)?.let { decodePicture(it.ref) }
        // 图片解不出来（损坏、格式不支持）时退回这一屏的正文，不留空白页。
        val text = if (slot is PageSlots.Slot.Picture && picture != null) {
            ""
        } else {
            paginated.readText(anchor, activePager.windowChars)
        }

        _state.value = ReaderUiState.Ready(
            title = title,
            chapterTitle = chapter?.title.orEmpty(),
            chapters = chapters,
            text = text,
            pageIndex = slotIndex,
            pageCount = slots.size,
            fraction = fraction,
            canPrev = slotIndex > 0,
            canNext = slotIndex < slots.lastIndex,
            lossyDecoding = paginated.lossyDecoding,
            picture = picture?.bitmap,
            pictureCaption = picture?.caption,
            charCount = activePager.charCount,
            anchorChar = anchor,
        )
        _scrollFraction.value = fraction
    }

    /** 解一张插图：命中缓存就不碰磁盘；解码按最长边降采样，别让一张 4000px 的图吃掉几十兆。 */
    private suspend fun decodePicture(ref: PictureRef): ReaderPicture? {
        pictureCache[ref.path]?.let { return ReaderPicture(it, ref.alt) }
        val source = engine as? PictureSource ?: return null
        val bytes = source.pictureBytes(ref.path) ?: return null
        val bitmap = withContext(Dispatchers.Default) { decodeScaled(bytes) } ?: return null
        pictureCache[ref.path] = bitmap
        return ReaderPicture(bitmap, ref.alt)
    }

    /** 滚动模式：取出落在这一块里的插图并解码，供块内渲染。 */
    suspend fun picturesInChunk(
        chunkIndex: Int,
        chunkChars: Int = ScrollChunkMath.CHUNK_CHARS,
    ): List<ReaderPicture> {
        if (pictures.isEmpty()) return emptyList()
        val start = chunkIndex * chunkChars
        val end = start + chunkChars
        val hits = pictures.filter { it.at >= start && it.at < end }
        if (hits.isEmpty()) return emptyList()
        return hits.mapNotNull { decodePicture(it) }
    }

    fun nextPage() {
        if (slotIndex >= slots.lastIndex) return
        slotIndex++
        viewModelScope.launch { render() }
    }

    fun prevPage() {
        if (slotIndex <= 0) return
        slotIndex--
        viewModelScope.launch { render() }
    }

    fun goToChapter(index: Int) {
        val target = chapters.getOrNull(index) ?: return
        val activePager = pager ?: return
        // 滚动模式不按页对齐：直接落到章节起点所在的块，少丢一句话。
        startChar = activePager.clampToPage(target.charStart)
        slotIndex = PageSlots.slotOfPage(slots, activePager.pageOf(startChar))
        viewModelScope.launch { render() }
    }

    /**
     * 滚动模式取一块正文。命中缓存直接返回，否则让引擎读一次并缓存。
     *
     * 这里不在锁里做 IO：同一块被两个 item 同时请求时最多重复读一次，
     * 而阻塞住整张缓存表的代价要大得多。
     */
    suspend fun textChunk(index: Int, chunkChars: Int = ScrollChunkMath.CHUNK_CHARS): String {
        chunkLock.withLock { chunkCache[index] }?.let { return it }
        val paginated = engine as? PaginatedEngine ?: return ""
        val text = paginated.readText(index * chunkChars, chunkChars)
        chunkLock.withLock { chunkCache[index] = text }
        return text
    }

    /**
     * 滚动时汇报可见位置的字符偏移：更新进度线，并防抖写库。
     *
     * 为什么要防抖：滚动会连着触发几十次，每次都写一次 Room 既浪费也不必要；
     * 用户停下 300ms 之后写一次，退出时 ViewModel 被清掉之前还会再落一次。
     */
    fun onScrollPosition(charOffset: Int, charCount: Int) {
        val pager = pager ?: return
        val fraction = ScrollChunkMath.fraction(charOffset, maxOf(charCount, pager.charCount))
        _scrollFraction.value = fraction
        val paginated = engine as? PaginatedEngine ?: return
        pendingSave?.cancel()
        pendingSave = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            paginated.positionOf(charOffset)?.let { paginated.saveProgress(it, fraction) }
        }
    }

    /** 退出阅读页时立刻落一次，不等防抖。 */
    fun flushScrollProgress(charOffset: Int, charCount: Int) {
        val paginated = engine as? PaginatedEngine ?: return
        pendingSave?.cancel()
        val fraction = ScrollChunkMath.fraction(charOffset, charCount)
        viewModelScope.launch {
            paginated.positionOf(charOffset)?.let { paginated.saveProgress(it, fraction) }
        }
    }

    /** 一屏的种类。 */
    companion object {
        private const val MAX_CACHED_CHUNKS = 24
        private const val MAX_CACHED_PICTURES = 6
        private const val SAVE_DEBOUNCE_MS = 300L

        /** 插图解码后的最长边上限：约合 1600px，够满屏显示，又不至于把内存吃穿。 */
        private const val MAX_PICTURE_EDGE_PX = 1600

        /** 按最长边做 2 的幂降采样。解码器只认 2 的幂，给别的值会向下取整。 */
        private fun decodeScaled(bytes: ByteArray): ImageBitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > MAX_PICTURE_EDGE_PX ||
                bounds.outHeight / sample > MAX_PICTURE_EDGE_PX
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            return runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
            }.getOrNull()
        }


        fun factory(bookId: Long, container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ReaderViewModel(
                    bookId = bookId,
                    repository = container.bookRepository,
                    engineFactory = container::createEngine,
                )
            }
        }
    }

    override fun onCleared() {
        engine?.close()
        engine = null
    }
}
