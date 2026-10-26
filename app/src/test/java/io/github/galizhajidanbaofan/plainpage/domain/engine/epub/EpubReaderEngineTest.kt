package io.github.galizhajidanbaofan.plainpage.domain.engine.epub

import io.github.galizhajidanbaofan.plainpage.domain.engine.ReaderException
import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.domain.model.BookFormat
import io.github.galizhajidanbaofan.plainpage.domain.model.ReadingLocation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.abs

class EpubReaderEngineTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun fixture(): File = EpubFixtures.build(
        temp.root,
        title = "冬夜札记",
        author = "张三",
        chapters = listOf(
            EpubFixtures.chapter("第一章 雪夜", "<p>甲一，冬天来得早。</p>", 1),
            EpubFixtures.chapter("第二章 深巷", "<p>乙二，巷子里有灯。</p>", 2),
        ),
    )

    private fun book(path: String = fixture().absolutePath, location: ReadingLocation? = null) = Book(
        id = 1L,
        title = "入目录",
        format = BookFormat.EPUB,
        localPath = path,
        sourceUri = "content://test/book",
        importedAt = 0L,
        readingLocation = location,
    )


    /** 1x1 的合法 PNG：拿来验"能不能从压缩包里取出来解码"。 */
    private fun pngBytes(): ByteArray = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
    )

    /** 带一张插图的 EPUB：正文两段，图挂在第一段之后。 */
    private fun fixtureWithImage(): File = EpubFixtures.build(
        temp.root,
        chapters = listOf(
            EpubFixtures.chapter(
                "第一章 雪夜",
                """<p>甲一，冬天来得早。</p><img src="../Images/snow.png" alt="窗外的雪"/><p>乙二，雪还在下。</p>""",
                1,
            ),
        ),
        extraEntries = mapOf("Images/snow.png" to pngBytes()),
    )

    private fun bookWithImage(location: ReadingLocation? = null) = Book(
        id = 2L,
        title = "带图的书",
        format = BookFormat.EPUB,
        localPath = fixtureWithImage().absolutePath,
        sourceUri = "content://test/image-book",
        importedAt = 0L,
        readingLocation = location,
    )

    @Test
    fun `打开后拿到元信息目录与正文`() = runBlocking {
        val engine = EpubReaderEngine()
        val meta = engine.open(book())
        assertEquals("冬夜札记", meta.title)
        assertEquals("张三", meta.author)
        assertTrue("全文字符数应大于 0", engine.charCount > 0)

        val outline = engine.loadOutline()
        assertEquals(listOf("第一章 雪夜", "第二章 深巷"), outline.map { it.title })
        val first = outline.first().location as ReadingLocation.Epub
        assertEquals("OEBPS/Text/ch1.xhtml", first.href)
        assertEquals(0, engine.charOffsetOf(first))

        val text = engine.readText(0, engine.charCount)
        assertTrue(text.contains("第一章 雪夜"))
        assertTrue(text.contains("甲一"))
        assertTrue(text.contains("第二章 深巷"))
        assertTrue(text.contains("乙二"))
        engine.close()
    }

    @Test
    fun `字符偏移与 epub 位置可往返`() = runBlocking {
        val engine = EpubReaderEngine()
        engine.open(book())
        val mid = engine.charCount / 2

        val location = engine.positionOf(mid)
        assertNotNull(location)
        assertTrue(location is ReadingLocation.Epub)

        val back = engine.charOffsetOf(location!!)
        assertNotNull(back)
        assertTrue("往返误差应在一两个字符内：$mid -> $back", abs(back!! - mid) <= 2)
        engine.close()
    }

    @Test
    fun `按 epub 位置恢复进度`() = runBlocking {
        val engine = EpubReaderEngine()
        engine.open(book(location = ReadingLocation.Epub(href = "OEBPS/Text/ch2.xhtml", fractionInChapter = 0.5f)))

        val restored = engine.restoreProgress()
        assertTrue(restored is ReadingLocation.Epub)
        assertEquals("OEBPS/Text/ch2.xhtml", (restored as ReadingLocation.Epub).href)

        val offset = engine.charOffsetOf(restored)
        assertNotNull(offset)
        assertTrue("恢复点应落在后半本：$offset / ${engine.charCount}", offset!! > engine.charCount / 2)
        engine.close()
    }

    @Test
    fun `位置指向不存在的章节时恢复返回 null`() = runBlocking {
        val engine = EpubReaderEngine()
        engine.open(book(location = ReadingLocation.Epub(href = "OEBPS/Text/nope.xhtml", fractionInChapter = 0.3f)))
        assertEquals(null, engine.restoreProgress())
        engine.close()
    }

    @Test
    fun `保存进度交给注入的 sink`() = runBlocking {
        var savedLocation: ReadingLocation? = null
        var savedFraction = -1f
        val engine = EpubReaderEngine(
            progressSink = { location, fraction ->
                savedLocation = location
                savedFraction = fraction
            },
        )
        engine.open(book())
        engine.saveProgress(ReadingLocation.Epub("OEBPS/Text/ch1.xhtml", 0.25f), 0.25f)

        assertTrue(savedLocation is ReadingLocation.Epub)
        assertEquals(0.25f, savedFraction, 0.0001f)
        engine.close()
    }

    @Test
    fun `文件不存在时报 FILE_MISSING`() = runBlocking {
        val engine = EpubReaderEngine()
        val missing = temp.root.resolve("nope.epub").absolutePath
        val error = runCatching { engine.open(book(path = missing)) }.exceptionOrNull()
        assertTrue(error is ReaderException)
        assertEquals(ReaderException.Reason.FILE_MISSING, (error as ReaderException).reason)
    }

    @Test
    fun `不是 epub 时报 CORRUPTED`() = runBlocking {
        val bad = temp.newFile("bad.epub").apply { writeText("这不是压缩包") }
        val engine = EpubReaderEngine()
        val error = runCatching { engine.open(book(path = bad.absolutePath)) }.exceptionOrNull()
        assertTrue(error is ReaderException)
        assertEquals(ReaderException.Reason.CORRUPTED, (error as ReaderException).reason)
    }

    @Test
    fun `close 可重复调用且之后读不到内容`() = runBlocking {
        val engine = EpubReaderEngine()
        engine.open(book())
        engine.close()
        engine.close()

        assertEquals(0, engine.charCount)
        assertEquals("", engine.readText(0, 10))
        assertTrue(engine.loadOutline().isEmpty())
    }

    @Test
    fun `插图从压缩包里取出来，位置与说明都带上`() = runBlocking {
        val engine = EpubReaderEngine()
        engine.open(bookWithImage())

        val pictures = engine.pictures
        assertEquals(1, pictures.size)
        val picture = pictures.single()
        assertEquals("OEBPS/Images/snow.png", picture.path)
        assertEquals("窗外的雪", picture.alt)
        assertTrue("偏移应落在正文范围内", picture.at in 0..engine.charCount)

        val bytes = engine.pictureBytes(picture.path)
        assertNotNull(bytes)
        assertEquals(pngBytes().size, bytes!!.size)
    }

    @Test
    fun `不存在的插图路径返回 null 而不是抛异常`() = runBlocking {
        val engine = EpubReaderEngine()
        engine.open(bookWithImage())
        assertEquals(null, engine.pictureBytes("OEBPS/Images/nothing.png"))
    }

    @Test
    fun `没有插图的书写成空列表`() = runBlocking {
        val engine = EpubReaderEngine()
        engine.open(book())
        assertTrue(engine.pictures.isEmpty())
    }
}
