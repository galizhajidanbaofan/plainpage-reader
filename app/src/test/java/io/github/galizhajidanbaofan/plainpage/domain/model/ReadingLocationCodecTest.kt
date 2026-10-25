package io.github.galizhajidanbaofan.plainpage.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingLocationCodecTest {

    @Test
    fun `epub location round trips`() {
        val original = ReadingLocation.Epub(href = "OEBPS/text/chapter-03.xhtml", fractionInChapter = 0.42f)
        assertEquals(original, ReadingLocationCodec.decode(ReadingLocationCodec.encode(original)))
    }

    @Test
    fun `pdf location round trips`() {
        val original = ReadingLocation.Pdf(pageIndex = 118, fractionInPage = 0.5f)
        assertEquals(original, ReadingLocationCodec.decode(ReadingLocationCodec.encode(original)))
    }

    @Test
    fun `txt location round trips`() {
        val original = ReadingLocation.Txt(charOffset = 1_234_567, chapterIndex = 88, fraction = 0.31f)
        assertEquals(original, ReadingLocationCodec.decode(ReadingLocationCodec.encode(original)))
    }

    @Test
    fun `fraction outside range is clamped instead of rejected`() {
        val decoded = ReadingLocationCodec.decode("pdf|3|-0.4")
        assertEquals(ReadingLocation.Pdf(pageIndex = 3, fractionInPage = 0f), decoded)

        val decodedHigh = ReadingLocationCodec.decode("epub|a.xhtml|9.5")
        assertEquals(ReadingLocation.Epub(href = "a.xhtml", fractionInChapter = 1f), decodedHigh)
    }

    @Test
    fun `broken input decodes to null rather than throwing`() {
        listOf(
            null,
            "",
            "   ",
            "epub",
            "epub|a.xhtml",
            "epub||0.5",
            "pdf|-1|0.2",
            "pdf|abc|0.2",
            "txt|10|2",
            "txt|10|2|not-a-number",
            "mobi|whatever|1",
        ).forEach { raw ->
            assertNull("输入 $raw 应当被判定为无效", ReadingLocationCodec.decode(raw))
        }
    }
}
