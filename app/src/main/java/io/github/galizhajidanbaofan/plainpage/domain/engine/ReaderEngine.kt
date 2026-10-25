package io.github.galizhajidanbaofan.plainpage.domain.engine

import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.domain.model.ReadingLocation

/** 目录项。PDF 走页码跳转，因此 level 恒为 0、location 为 Pdf。 */
data class OutlineItem(
    val id: String,
    val title: String,
    val level: Int,
    val location: ReadingLocation,
)

/**
 * 统一的阅读引擎接口：EPUB / TXT / PDF 三种实现对外只暴露这一套语义。
 *
 * 约定：
 * - 所有挂起方法都在 Dispatchers.IO（或引擎自有 dispatcher）执行，调用方不需要再切线程；
 * - [open] 只做"能打开 + 拿到基本信息"，不预加载全文；
 * - 同一个实例只服务一本书，切换书籍请新建实例；
 * - [close] 之后不应再调用其它方法。
 */
interface ReaderEngine {

    /** 打开书籍；失败抛 [ReaderException]（调用方负责转成用户可读提示）。 */
    suspend fun open(book: Book): BookMetadata

    /** 加载目录，按阅读顺序返回；没有目录时返回空列表。 */
    suspend fun loadOutline(): List<OutlineItem>

    /** 恢复上次位置；没有记录返回 null。 */
    suspend fun restoreProgress(): ReadingLocation?

    /** 保存位置；[fraction] 为整本书的阅读百分比（0f..1f），用于书架进度条。 */
    suspend fun saveProgress(location: ReadingLocation, fraction: Float)

    /** 释放底层资源（文件句柄、渲染器、位图缓存）。 */
    fun close()
}

/** 打开书籍后立刻可用的元信息，用于书架补全标题/作者/页数。 */
data class BookMetadata(
    val title: String?,
    val author: String?,
    val pageCount: Int? = null,
)

/** 引擎层的可预期失败，带一个适合直接展示给用户的中文原因。 */
class ReaderException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    enum class Reason {
        FILE_MISSING,
        CORRUPTED,
        UNSUPPORTED_ENCODING,
        EMPTY_CONTENT,
        TOO_LARGE,
        UNKNOWN,
    }
}
