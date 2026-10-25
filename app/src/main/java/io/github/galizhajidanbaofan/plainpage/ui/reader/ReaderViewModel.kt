package io.github.galizhajidanbaofan.plainpage.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.galizhajidanbaofan.plainpage.domain.repository.BookRepository
import io.github.galizhajidanbaofan.plainpage.di.AppContainer
import io.github.galizhajidanbaofan.plainpage.domain.engine.PaginatedEngine
import io.github.galizhajidanbaofan.plainpage.domain.engine.ReaderEngine
import io.github.galizhajidanbaofan.plainpage.domain.engine.ReaderException
import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.domain.model.BookFormat
import io.github.galizhajidanbaofan.plainpage.domain.model.ReadingLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 目录项：标题 + 在正文里的字符起点（EPUB 以后换成 href）。 */
data class ReaderChapter(val title: String, val charStart: Int)

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
    private var chapters: List<ReaderChapter> = emptyList()
    private var title: String = ""
    private var startChar: Int = 0

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

        chapters = created.loadOutline().map { item ->
            val offset = (item.location as? ReadingLocation.Txt)?.charOffset ?: 0
            ReaderChapter(title = item.title, charStart = offset)
        }
        pager = ReaderPager(paginated.charCount)

        val restored = created.restoreProgress() as? ReadingLocation.Txt
        startChar = pager?.clampToPage(restored?.charOffset ?: 0) ?: 0
        render()
    }

    /** 重新读一次当前窗口；翻页、跳章、初次打开都走这里。 */
    private suspend fun render() = renderLock.withLock {
        val paginated = engine as? PaginatedEngine
        val activePager = pager
        if (paginated == null || activePager == null) return@withLock

        val chapter = chapters.lastOrNull { it.charStart <= startChar } ?: chapters.firstOrNull()
        if (chapter != null) startChar = activePager.clampToPage(startChar)

        val text = paginated.readText(startChar, activePager.windowChars)
        paginated.positionOf(startChar)?.let { location ->
            paginated.saveProgress(location, activePager.fraction(startChar))
        }

        _state.value = ReaderUiState.Ready(
            title = title,
            chapterTitle = chapter?.title.orEmpty(),
            chapters = chapters,
            text = text,
            pageIndex = activePager.pageOf(startChar),
            pageCount = activePager.pageCount,
            fraction = activePager.fraction(startChar),
            canPrev = activePager.canPrev(startChar),
            canNext = activePager.canNext(startChar),
            lossyDecoding = paginated.lossyDecoding,
        )
    }

    fun nextPage() {
        val activePager = pager ?: return
        if (!activePager.canNext(startChar)) return
        startChar = activePager.startOf(activePager.pageOf(startChar) + 1)
        viewModelScope.launch { render() }
    }

    fun prevPage() {
        val activePager = pager ?: return
        if (!activePager.canPrev(startChar)) return
        startChar = activePager.startOf(activePager.pageOf(startChar) - 1)
        viewModelScope.launch { render() }
    }

    fun goToChapter(index: Int) {
        val activePager = pager ?: return
        val target = chapters.getOrNull(index) ?: return
        startChar = activePager.clampToPage(target.charStart)
        viewModelScope.launch { render() }
    }

    companion object {
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
