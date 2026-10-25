package io.github.galizhajidanbaofan.plainpage.domain.engine.txt

import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.domain.model.BookFormat
import io.github.galizhajidanbaofan.plainpage.domain.model.ReadingLocation
import io.github.galizhajidanbaofan.plainpage.ui.reader.ReaderPager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 「打开 → 取正文 → 存进度 → 换个引擎实例重开 → 回到同一段文字」的闭环。
 *
 * 这是 P0 第 5 条（进度记忆）最小可执行证据：不依赖 Room、不依赖设备，
 * 只用引擎 + 假的 progressSink，就能证明字符偏移存下去、取回来还是一模一样的那一屏。
 */
class TxtReadingLoopTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val novel = buildString {
        append("版权信息\n本书由某人整理。\n\n")
        repeat(20) { index ->
            append("第${index + 1}章 标题${index + 1}\n")
            repeat(30) { line -> append("这是第${index + 1}章的第${line + 1}行正文，用来占点体积。\n") }
            append("\n")
        }
    }

    private fun textFile(): File {
        val file = temp.newFile("loop.txt")
        file.writeBytes(novel.toByteArray(Charsets.UTF_8))
        return file
    }

    private fun bookOf(file: File, location: ReadingLocation? = null) = Book(
        id = 7L,
        title = "闭环测试",
        format = BookFormat.TXT,
        localPath = file.absolutePath,
        sourceUri = "content://test/loop.txt",
        byteSize = file.length(),
        importedAt = 0L,
        readingLocation = location,
    )

    @Test
    fun `存下的进度换个实例能恢复到同一屏正文`() = runBlocking {
        val file = textFile()
        var saved: Pair<ReadingLocation, Float>? = null

        val first = TxtReaderEngine(progressSink = { location, fraction -> saved = location to fraction })
        first.open(bookOf(file))

        val startChar = 6_000
        val text = first.readText(startChar, ReaderPager.DEFAULT_WINDOW_CHARS)
        assertTrue("这一屏应该有正文", text.isNotEmpty())

        val position = requireNotNull(first.positionOf(startChar) as ReadingLocation.Txt?) { "positionOf 不该返回 null" }
        first.saveProgress(position, 0.25f)
        first.close()

        val recorded = requireNotNull(saved) { "progressSink 必须被调用一次" }
        assertEquals(position, recorded.first)
        assertEquals(0.25f, recorded.second, 0.0001f)

        val second = TxtReaderEngine()
        second.open(bookOf(file, location = recorded.first))
        val restored = requireNotNull(second.restoreProgress() as ReadingLocation.Txt?)

        assertEquals(position.charOffset, restored.charOffset)
        assertEquals("恢复后取到的正文必须与保存前一致", text, second.readText(restored.charOffset, ReaderPager.DEFAULT_WINDOW_CHARS))

        val pager = ReaderPager(second.charCount)
        assertEquals(pager.pageOf(startChar), pager.pageOf(restored.charOffset))
        assertEquals("翻页起点要对齐到页首", pager.startOf(pager.pageOf(restored.charOffset)), pager.clampToPage(restored.charOffset))

        second.close()
    }

    @Test
    fun `目录跳章取到的正文以该章标题开头`() = runBlocking {
        val file = textFile()
        val engine = TxtReaderEngine()
        engine.open(bookOf(file))

        val outline = engine.loadOutline()
        assertTrue(outline.size > 3)

        val target = outline[3]
        val location = requireNotNull(target.location as ReadingLocation.Txt?)
        val pageText = engine.readText(location.charOffset, 200)

        assertTrue(
            "跳章后第一屏应以章节标题开头，实际是：${pageText.take(20)}",
            pageText.startsWith(target.title),
        )
        engine.close()
    }
}
