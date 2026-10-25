package io.github.galizhajidanbaofan.plainpage.domain.engine.txt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtChapterScannerTest {

    private val sample = """
        版权信息
        本书由某人整理。

        第一章 夜雨
        他推开门，外面下着雨。

        第二章 晴天
        阳光很好。

        第三章 归途
        故事结束了。
    """.trimIndent()

    @Test
    fun `splits chapters and keeps offsets aligned with the text`() {
        val chapters = splitTxtChapters(sample)

        assertEquals(listOf("开篇", "第一章 夜雨", "第二章 晴天", "第三章 归途"), chapters.map { it.title })
        assertTrue(chapters.all { it.charEnd >= it.charStart })

        // 每个章节标题都应正好落在 charStart 上，读出来是对的。
        for (chapter in chapters.drop(1)) {
            assertTrue(
                "章节 ${chapter.title} 的偏移不对",
                sample.startsWith(chapter.title, chapter.charStart),
            )
        }
        // 分章必须无缝覆盖全文。
        assertEquals(0, chapters.first().charStart)
        assertEquals(sample.length, chapters.last().charEnd)
        chapters.zipWithNext().forEach { (left, right) ->
            assertEquals(left.charEnd, right.charStart)
        }
    }

    @Test
    fun `streaming in tiny chunks gives the same result`() {
        val whole = splitTxtChapters(sample)
        val scanner = TxtChapterScanner()
        var index = 0
        while (index < sample.length) {
            val end = minOf(sample.length, index + 3)
            scanner.append(sample.substring(index, end))
            index = end
        }
        val streamed = scanner.finish(sample.length).chapters
        assertEquals(whole, streamed)
    }

    @Test
    fun `text without headings becomes a single chapter`() {
        val text = "只是一段很普通的文字。\n没有章节。"
        val chapters = splitTxtChapters(text)
        assertEquals(1, chapters.size)
        assertEquals(TxtChapterScanner.DEFAULT_CHAPTER_TITLE, chapters.single().title)
        assertEquals(0, chapters.single().charStart)
        assertEquals(text.length, chapters.single().charEnd)
    }

    @Test
    fun `sentence mentioning a chapter number is not a heading`() {
        val text = "他在第一章里写过这件事，可是没人记得。\n第二章 真正的开始\n正文"
        val chapters = splitTxtChapters(text)
        assertEquals(listOf("开篇", "第二章 真正的开始"), chapters.map { it.title })
    }

    @Test
    fun `crlf files are handled`() {
        val text = "第一章 开始\r\n内容一\r\n第二章 结束\r\n内容二"
        val chapters = splitTxtChapters(text)
        assertEquals(listOf("第一章 开始", "第二章 结束"), chapters.map { it.title })
        assertTrue(text.startsWith("第二章 结束", chapters[1].charStart))
    }

    @Test
    fun `chapter cap is enforced and reported`() {
        val text = buildString {
            repeat(10) { i ->
                append("第${i + 1}章 标题\n内容\n")
            }
        }
        val scanner = TxtChapterScanner(maxChapters = 3)
        scanner.append(text)
        val result = scanner.finish(text.length)
        assertEquals(3, result.chapters.size)
        assertTrue(result.truncated)
    }

    @Test
    fun `empty text yields one empty chapter`() {
        val chapters = splitTxtChapters("")
        assertEquals(1, chapters.size)
        assertEquals(0, chapters.single().charStart)
        assertEquals(0, chapters.single().charEnd)
    }

    @Test
    fun `heading rules reject ordinary lines`() {
        assertTrue(TxtHeadings.isHeading("第一章 夜雨"))
        assertTrue(TxtHeadings.isHeading("第三回"))
        assertTrue(TxtHeadings.isHeading("楔子"))
        assertTrue(TxtHeadings.isHeading("Chapter 12 The End"))
        assertFalse(TxtHeadings.isHeading("他读到第一章时睡着了。"))
        assertFalse(
            TxtHeadings.isHeading("第一章：这是一个特别特别长的标题，用来测试四十字符的长度上限到底会不会生效，应该已经明显超过了"),
        )
        assertFalse(TxtHeadings.isHeading("正文"))
    }
}
