package io.github.galizhajidanbaofan.plainpage.domain.engine.txt

import io.github.galizhajidanbaofan.plainpage.domain.engine.BookMetadata
import io.github.galizhajidanbaofan.plainpage.domain.engine.OutlineItem
import io.github.galizhajidanbaofan.plainpage.domain.engine.ReaderEngine
import io.github.galizhajidanbaofan.plainpage.domain.engine.ReaderException
import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.domain.model.ReadingLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/**
 * TXT 阅读引擎。
 *
 * 职责边界：
 * - 建立索引与切章（[TxtBookIndexer]）；
 * - 按需取正文（[TxtPageReader]），调用方拿到的永远只是一页/一屏的量；
 * - 进度换算（字符偏移 ↔ 章节 ↔ 百分比）。
 *
 * 进度**持久化不在这里**：引擎把位置交给构造时注入的 [progressSink]，
 * 由组合根决定落到 Room（现在）还是别处（以后）。这样引擎能在纯 JVM 单测里跑。
 */
class TxtReaderEngine(
    private val indexer: TxtBookIndexer = TxtBookIndexer(),
    private val progressSink: (suspend (ReadingLocation, Float) -> Unit)? = null,
) : ReaderEngine {

    private var book: Book? = null
    private var index: TxtIndex? = null
    private var reader: TxtPageReader? = null

    /** 全文长度（字符数）；未打开时为 0。 */
    val charCount: Int get() = index?.charCount ?: 0

    val encoding: TextEncoding? get() = index?.encoding

    /** 解码过程中出现过替换字符，UI 应提示"编码可能不准"。 */
    val lossyDecoding: Boolean get() = index?.lossyDecoding == true

    val chaptersTruncated: Boolean get() = index?.chaptersTruncated == true

    override suspend fun open(book: Book): BookMetadata {
        close()
        val file = File(book.localPath)
        if (!file.isFile) {
            throw ReaderException(ReaderException.Reason.FILE_MISSING, "文件不存在或已被删除", FileNotFoundException(book.localPath))
        }
        val built = try {
            withContext(Dispatchers.IO) { indexer.index(file) }
        } catch (error: TxtIndexException) {
            throw error.toReaderException()
        }
        this.book = book
        this.index = built
        this.reader = TxtPageReader(file, built)
        return BookMetadata(title = book.title, author = null, pageCount = null)
    }

    override suspend fun loadOutline(): List<OutlineItem> {
        val current = index ?: return emptyList()
        return current.chapters.mapIndexed { position, chapter ->
            OutlineItem(
                id = "$OUTLINE_ID_PREFIX$position",
                title = chapter.title,
                level = 0,
                location = locationOf(chapter, position, current),
            )
        }
    }

    override suspend fun restoreProgress(): ReadingLocation? {
        val current = index ?: return null
        val saved = book?.readingLocation as? ReadingLocation.Txt ?: return null
        if (current.charCount == 0) return null
        val offset = saved.charOffset.coerceIn(0, current.charCount - 1)
        val chapterPosition = saved.chapterIndex.coerceIn(0, current.chapters.lastIndex)
        return ReadingLocation.Txt(
            charOffset = offset,
            chapterIndex = chapterPosition,
            fraction = saved.fraction.coerceIn(0f, 1f),
        )
    }

    override suspend fun saveProgress(location: ReadingLocation, fraction: Float) {
        progressSink?.invoke(location, fraction)
    }

    override fun close() {
        reader?.let { runCatching { it.close() } }
        reader = null
        index = null
        book = null
    }

    /** 取一页正文；越界由 [TxtPageReader] 夹紧。 */
    suspend fun read(startChar: Int, count: Int): String {
        val active = reader ?: return ""
        return withContext(Dispatchers.IO) { active.read(startChar, count) }
    }

    /** 由字符偏移反推出可保存的位置（章节索引 + 百分比）。 */
    fun positionOf(charOffset: Int): ReadingLocation.Txt? {
        val current = index ?: return null
        if (current.charCount == 0) return null
        val offset = charOffset.coerceIn(0, current.charCount - 1)
        val chapterIndex = current.chapters.indexOfLast { it.charStart <= offset }.coerceAtLeast(0)
        return ReadingLocation.Txt(
            charOffset = offset,
            chapterIndex = chapterIndex,
            fraction = offset.toFloat() / current.charCount.toFloat(),
        )
    }

    private fun locationOf(chapter: TxtChapter, position: Int, index: TxtIndex): ReadingLocation.Txt =
        ReadingLocation.Txt(
            charOffset = chapter.charStart,
            chapterIndex = position,
            fraction = if (index.charCount == 0) {
                0f
            } else {
                chapter.charStart.toFloat() / index.charCount.toFloat()
            },
        )

    private fun TxtIndexException.toReaderException(): ReaderException {
        val reason = when (this) {
            is TxtIndexException.Missing -> ReaderException.Reason.FILE_MISSING
            is TxtIndexException.Empty -> ReaderException.Reason.EMPTY_CONTENT
            is TxtIndexException.TooLarge -> ReaderException.Reason.TOO_LARGE
        }
        return ReaderException(reason, message ?: "无法读取文件", this)
    }

    private companion object {
        const val OUTLINE_ID_PREFIX = "txt-"
    }
}
