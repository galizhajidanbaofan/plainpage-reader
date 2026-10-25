package io.github.galizhajidanbaofan.plainpage.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.galizhajidanbaofan.plainpage.data.importing.BookImporter
import io.github.galizhajidanbaofan.plainpage.data.importing.ImportRejection
import io.github.galizhajidanbaofan.plainpage.data.importing.ImportResult
import io.github.galizhajidanbaofan.plainpage.di.AppContainer
import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.domain.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 导入完成/失败后给用户看的一句话。用枚举而不是字符串，UI 层负责取资源文案。 */
sealed interface LibraryMessage {
    data class Imported(val count: Int) : LibraryMessage
    data class ImportFailed(val reason: ImportRejection, val count: Int) : LibraryMessage
}

/**
 * 书架与最近阅读共用的状态持有者。
 *
 * 只做三件事：把仓库的 Flow 变成 UI 状态、发起导入、发起删除/打开记录。
 * 任何文件 IO 都在 [BookImporter] 里完成（它自己切 IO 线程），这里不碰 File API。
 */
class LibraryViewModel(
    private val bookRepository: BookRepository,
    private val bookImporter: BookImporter,
) : ViewModel() {

    val shelf: StateFlow<List<Book>> = bookRepository.observeShelf()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val recent: StateFlow<List<Book>> = bookRepository.observeRecent(RECENT_LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _message = MutableStateFlow<LibraryMessage?>(null)
    val message: StateFlow<LibraryMessage?> = _message.asStateFlow()

    /**
     * 导入一批 URI。逐个处理，失败不中断其余文件：
     * 用户选 5 本书，坏掉 1 本不该让另外 4 本也不进来。
     */
    fun import(uris: List<Uri>) {
        if (uris.isEmpty() || _importing.value) return
        viewModelScope.launch {
            _importing.value = true
            var imported = 0
            var lastFailure: ImportRejection? = null
            var failed = 0
            for (uri in uris) {
                when (val result = bookImporter.import(uri)) {
                    is ImportResult.Success -> {
                        bookRepository.insert(result.book)
                        imported++
                    }

                    is ImportResult.Rejected -> {
                        failed++
                        lastFailure = result.reason
                    }
                }
            }
            _importing.value = false
            _message.value = when {
                failed == 0 -> LibraryMessage.Imported(imported)
                imported == 0 && lastFailure != null -> LibraryMessage.ImportFailed(lastFailure, failed)
                // 部分成功：仍然报「导入了几本」，失败细节留给后续的错误面板
                else -> LibraryMessage.Imported(imported)
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /**
     * 移出书架：先删数据库记录（UI 立刻响应），再删私有目录副本。
     * 用户的原文件在 SAF 来源里，**永远不动**。
     */
    fun delete(book: Book) {
        viewModelScope.launch {
            bookRepository.delete(book.id)
            withContext(Dispatchers.IO) {
                runCatching { File(book.localPath).delete() }
            }
        }
    }

    /** 打开一本书时立刻记一笔「最近阅读」，书架排序与最近列表都依赖它。 */
    fun markOpened(bookId: Long) {
        viewModelScope.launch {
            bookRepository.markOpened(bookId, System.currentTimeMillis())
        }
    }

    fun observeBook(bookId: Long) = bookRepository.observeBook(bookId)

    companion object {
        private const val RECENT_LIMIT = 20
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LibraryViewModel(container.bookRepository, container.bookImporter)
            }
        }
    }
}
