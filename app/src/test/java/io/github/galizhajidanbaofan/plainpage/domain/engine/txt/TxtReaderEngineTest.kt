package io.github.galizhajidanbaofan.plainpage.domain.engine.txt

import io.github.galizhajidanbaofan.plainpage.domain.engine.ReaderEngine
import io.github.galizhajidanbaofan.plainpage.domain.engine.ReaderException
import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.domain.model.BookFormat
import io.github.galizhajidanbaofan.plainpage.domain.model.ReadingLocation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TxtReaderEngineTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun bookOf(file: File, location: ReadingLocation? = null) = Book(
        id = 1L,
        title = "测试书",
        format = BookFormat.TXT,
        localPath = file.absolutePath,
        sourceUri = "content://test/book.txt",
        byteSize = file.length(),
        importedAt = 0L,
        readingLocation = location,
    )

    private fun textFile(name: String, text: String, charset: String = "UTF-8"): File {
        val file = temp.newFile(name)
        file.writeBytes(text.toByteArray(charset(charset)))
        return file
    }

    private val novel = buildString {
        append("版权信息\n本书由某人整理。\n\n")
        repeat(30) { index ->
            append("第${index + 1}章 标题${index + 1}\n")
            repeat(40) { line -> append("这是第${index + 1}章的第${line + 1}行正文，用来占点体积。\n") }
            append("\n")
        }
    }

    @Test
    fun `index reports chapters and full character count`() = runBlocking {
        val file = textFile("novel.txt", novel)
        val index = TxtBookIndexer().index(file)

        assertEquals(TextEncoding.UTF8, index.encoding)
        assertEquals(novel.length, index.charCount)
        assertEquals(31, index.chapters.size) // 开篇 + 30 章
        assertEquals(TxtChapterScanner.OPENING_CHAPTER_TITLE, index.chapters.first().title)
        assertTrue(index.chapters.all { novel.startsWith(it.title, it.charStart) || it.title == TxtChapterScanner.OPENING_CHAPTER_TITLE })
    }

    @Test
    fun `page reader returns the same text as substring, even across checkpoints`() = runBlocking {
        val file = textFile("novel.txt", novel)
        // 故意把分块与锚点都做得极小，保证窗口一定跨锚点。
        val index = TxtBookIndexer(chunkBytes = 1024, checkpointChars = 256).index(file)
        assertTrue("应该产生多个锚点", index.checkpoints.size > 3)

        TxtPageReader(file, index).use { reader ->
            assertEquals(novel.substring(0, 50), reader.read(0, 50))
            assertEquals(novel.substring(700, 1000), reader.read(700, 300))
            assertEquals(novel.substring(5_000, 5_200), reader.read(5_000, 200))

            val chapterStart = index.chapters[10].charStart
            assertEquals(novel.substring(chapterStart, chapterStart + 80), reader.read(chapterStart, 80))

            // 越界要夹紧而不是崩。
            val tail = reader.read(novel.length - 10, 500)
            assertEquals(novel.substring(novel.length - 10), tail)
            assertEquals("", reader.read(novel.length, 10))
            assertEquals("", reader.read(-5, 0))
        }
    }

    @Test
    fun `gb18030 file is decoded correctly`() = runBlocking {
        val text = "第一章 夜雨\n他推开门，外面下着雨。\n第二章 晴天\n阳光很好。"
        val file = textFile("gbk.txt", text, charset = "GB18030")
        val index = TxtBookIndexer().index(file)

        assertEquals(TextEncoding.GB18030, index.encoding)
        assertEquals(text.length, index.charCount)
        assertEquals(listOf("第一章 夜雨", "第二章 晴天"), index.chapters.map { it.title })
        TxtPageReader(file, index).use { reader ->
            assertEquals(text.substring(0, 12), reader.read(0, 12))
        }
    }

    @Test
    fun `bom is skipped`() = runBlocking {
        val file = temp.newFile("bom.txt")
        file.writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "第一章 开始\n正文".toByteArray())
        val index = TxtBookIndexer().index(file)

        assertEquals(TextEncoding.UTF8, index.encoding)
        assertTrue(index.chapters.first().title.startsWith("第一章"))
        TxtPageReader(file, index).use { reader ->
            assertTrue(reader.read(0, 5).startsWith("第一章"))
        }
    }

    @Test
    fun `missing, empty and oversized files fail with reader exceptions`() = runBlocking {
        val missing = File(temp.root, "gone.txt")
        assertFailure(ReaderException.Reason.FILE_MISSING) { TxtReaderEngine().open(bookOf(missing)) }

        val empty = temp.newFile("empty.txt")
        assertFailure(ReaderException.Reason.EMPTY_CONTENT) { TxtReaderEngine().open(bookOf(empty)) }

        val big = temp.newFile("big.txt")
        big.writeBytes(ByteArray(16) { 'a'.code.toByte() })
        val tinyLimit = TxtReaderEngine(indexer = TxtBookIndexer(maxBytes = 8))
        assertFailure(ReaderException.Reason.TOO_LARGE) { tinyLimit.open(bookOf(big)) }
    }

    @Test
    fun `engine exposes outline, clamps restored progress and reports saves`() = runBlocking {
        val file = textFile("novel.txt", novel)
        val saved = mutableListOf<Pair<ReadingLocation, Float>>()
        val engine = TxtReaderEngine(progressSink = { location, fraction -> saved += location to fraction })

        val metadata = engine.open(bookOf(file, ReadingLocation.Txt(charOffset = 9_999_999, chapterIndex = 99, fraction = 3f)))
        assertEquals("测试书", metadata.title)
        assertEquals(novel.length, engine.charCount)

        val outline = engine.loadOutline()
        assertEquals(31, outline.size)
        assertEquals("txt-0", outline.first().id)
        val firstLocation = outline[1].location as ReadingLocation.Txt
        assertNotNull(firstLocation)
        assertEquals(1, firstLocation.chapterIndex)

        val restored = engine.restoreProgress() as ReadingLocation.Txt
        assertEquals(novel.length - 1, restored.charOffset)
        assertEquals(30, restored.chapterIndex)
        assertEquals(1f, restored.fraction, 0.0001f)

        val position = engine.positionOf(novel.length / 2) as ReadingLocation.Txt?
        assertNotNull(position)
        engine.saveProgress(position!!, position.fraction)
        assertEquals(1, saved.size)
        assertEquals(position, saved.single().first)

        engine.close()
        assertEquals("", engine.readText(0, 10))
    }

    @Test
    fun `engine with no saved progress restores nothing`() = runBlocking {
        val file = textFile("novel.txt", novel)
        val engine = TxtReaderEngine()
        engine.open(bookOf(file))
        assertNull(engine.restoreProgress())
        engine.close()
    }

    private fun assertFailure(reason: ReaderException.Reason, block: suspend () -> Unit) = runBlocking {
        try {
            block()
            throw AssertionError("预期抛 ReaderException($reason)，但成功了")
        } catch (error: ReaderException) {
            assertEquals(reason, error.reason)
        }
    }
}
