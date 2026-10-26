package io.github.galizhajidanbaofan.plainpage.domain.engine.epub

import io.github.galizhajidanbaofan.plainpage.domain.engine.BookMetadata
import io.github.galizhajidanbaofan.plainpage.domain.engine.OutlineItem
import io.github.galizhajidanbaofan.plainpage.domain.engine.PaginatedEngine
import io.github.galizhajidanbaofan.plainpage.domain.engine.PictureRef
import io.github.galizhajidanbaofan.plainpage.domain.engine.PictureSource
import io.github.galizhajidanbaofan.plainpage.domain.engine.ReaderException
import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.domain.model.ReadingLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.ZipException

/**
 * EPUB 阅读引擎（自研最小实现，不引 Readium）。
 *
 * 分工：
 * - [EpubArchive] 管 zip 与编码；
 * - [EpubPackageParser] 管 OPF；
 * - [EpubOutlineParser] 管目录；
 * - [EpubIndexer] 把各章正文拼成带章节区间的全书文本；
 * - 这里只做生命周期、进度换算和错误翻译。
 *
 * 进度持久化同样通过注入的 [progressSink] 交回上层，引擎不认 Room，因此能在纯 JVM 单测里跑。
 */
class EpubReaderEngine(
    private val progressSink: (suspend (ReadingLocation, Float) -> Unit)? = null,
) : PaginatedEngine, PictureSource {

    private var book: Book? = null
    private var archive: EpubArchive? = null
    private var index: EpubIndex? = null
    private var packageInfo: EpubPackageInfo? = null

    override val charCount: Int get() = index?.charCount ?: 0

    /** 正文被截断时也置 true：界面上的"可能不完整"提示对两者都适用。 */
    override val lossyDecoding: Boolean get() = index?.truncated == true

    override val pictures: List<PictureRef> get() = index?.pictures.orEmpty()

    override suspend fun open(book: Book): BookMetadata {
        close()
        val file = File(book.localPath)
        if (!file.isFile) {
            throw ReaderException(
                ReaderException.Reason.FILE_MISSING,
                "文件不存在或已被删除",
                FileNotFoundException(book.localPath),
            )
        }

        val opened = try {
            withContext(Dispatchers.IO) {
                val archive = EpubArchive(file)
                try {
                    val packageInfo = EpubPackageParser.read(archive)
                    val index = EpubIndexer.build(archive, packageInfo)
                    Triple(archive, packageInfo, index)
                } catch (error: Throwable) {
                    archive.close()
                    throw error
                }
            }
        } catch (error: EpubFormatException) {
            throw error.toReaderException()
        } catch (error: ZipException) {
            throw ReaderException(ReaderException.Reason.CORRUPTED, "EPUB 压缩结构损坏", error)
        } catch (error: IOException) {
            throw ReaderException(ReaderException.Reason.CORRUPTED, "EPUB 读取出错", error)
        }

        this.book = book
        this.archive = opened.first
        this.packageInfo = opened.second
        this.index = opened.third

        return BookMetadata(
            title = opened.second.title ?: book.title,
            author = opened.second.author,
            pageCount = null,
        )
    }

    override suspend fun loadOutline(): List<OutlineItem> {
        val current = index ?: return emptyList()
        return current.chapters.mapIndexed { position, chapter ->
            OutlineItem(
                id = "$OUTLINE_ID_PREFIX${chapter.href}#$position",
                title = chapter.title,
                level = chapter.level,
                location = ReadingLocation.Epub(href = chapter.href, fractionInChapter = 0f),
            )
        }
    }

    override suspend fun restoreProgress(): ReadingLocation? {
        val saved = book?.readingLocation as? ReadingLocation.Epub ?: return null
        val current = index ?: return null
        val chapter = current.chapterByHref(saved.href) ?: return null
        return ReadingLocation.Epub(
            href = chapter.href,
            fractionInChapter = saved.fractionInChapter.coerceIn(0f, 1f),
        )
    }

    override suspend fun saveProgress(location: ReadingLocation, fraction: Float) {
        progressSink?.invoke(location, fraction)
    }

    override fun close() {
        archive?.close()
        archive = null
        index = null
        packageInfo = null
        book = null
    }

    /**
     * 取插图字节。图片在 EPUB 里就是普通条目，这里只做「存在性 + 上限」两道拦截：
     * 缺失的图返回 null 让 UI 跳过，超限的图同样跳过而不是让整本书打开失败。
     */
    override suspend fun pictureBytes(path: String): ByteArray? {
        val current = archive ?: return null
        return withContext(Dispatchers.IO) {
            runCatching { current.readBytesIfPresent(path, EpubLimits.MAX_PICTURE_BYTES) }
                .getOrElse { null }
        }
    }

    override suspend fun readText(startChar: Int, count: Int): String {
        val current = index?.text ?: return ""
        if (current.isEmpty() || count <= 0) return ""
        val start = startChar.coerceIn(0, current.length)
        val end = (start + count).coerceAtMost(current.length)
        return current.substring(start, end)
    }

    override fun positionOf(charOffset: Int): ReadingLocation? {
        val current = index ?: return null
        if (current.charCount == 0) return null
        val offset = charOffset.coerceIn(0, current.charCount - 1)
        val chapter = current.chapterAt(offset) ?: return null
        val span = (chapter.charEnd - chapter.charStart).coerceAtLeast(1)
        val fraction = ((offset - chapter.charStart).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        return ReadingLocation.Epub(href = chapter.href, fractionInChapter = fraction)
    }

    override fun charOffsetOf(location: ReadingLocation): Int? {
        val current = index ?: return null
        val epub = location as? ReadingLocation.Epub ?: return null
        val chapter = current.chapterByHref(epub.href) ?: return null
        val span = (chapter.charEnd - chapter.charStart).coerceAtLeast(1)
        val within = (epub.fractionInChapter.coerceIn(0f, 1f) * span).toInt()
        return (chapter.charStart + within).coerceIn(chapter.charStart, chapter.charEnd - 1)
    }

    private fun EpubFormatException.toReaderException(): ReaderException {
        val reason = when (kind) {
            EpubFormatException.Kind.CORRUPTED -> ReaderException.Reason.CORRUPTED
            EpubFormatException.Kind.EMPTY_CONTENT -> ReaderException.Reason.EMPTY_CONTENT
            EpubFormatException.Kind.TOO_LARGE -> ReaderException.Reason.TOO_LARGE
        }
        return ReaderException(reason, message ?: "无法打开这本 EPUB", this)
    }

    private companion object {
        const val OUTLINE_ID_PREFIX = "epub-"
    }
}
