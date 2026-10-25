package io.github.galizhajidanbaofan.plainpage.domain.engine.txt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEncodingDetectorTest {

    private fun utf8(text: String) = text.toByteArray(Charsets.UTF_8)

    private fun gb18030(text: String) = text.toByteArray(charset("GB18030"))

    @Test
    fun `utf8 bom wins`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + utf8("第一章 开始")
        val result = TextEncodingDetector.detect(bytes)
        assertEquals(TextEncoding.UTF8, result.encoding)
        assertEquals(3, result.bomLength)
        assertFalse(result.lossy)
    }

    @Test
    fun `utf16 boms are recognised`() {
        val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "第一章".toByteArray(charset("UTF-16LE"))
        val be = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "第一章".toByteArray(charset("UTF-16BE"))

        assertEquals(TextEncoding.UTF16LE, TextEncodingDetector.detect(le).encoding)
        assertEquals(TextEncoding.UTF16BE, TextEncodingDetector.detect(be).encoding)
        assertEquals(2, TextEncodingDetector.detect(le).bomLength)
    }

    @Test
    fun `plain utf8 chinese is utf8`() {
        val result = TextEncodingDetector.detect(utf8("围城\n第一章\n钱锺书"))
        assertEquals(TextEncoding.UTF8, result.encoding)
        assertEquals(0, result.bomLength)
        assertFalse(result.lossy)
    }

    @Test
    fun `gb18030 chinese is detected as gb18030`() {
        val result = TextEncodingDetector.detect(gb18030("第一章 夜雨\n他推开门，外面下着雨。"))
        assertEquals(TextEncoding.GB18030, result.encoding)
        assertFalse(result.lossy)
    }

    @Test
    fun `utf16 without bom is guessed from zero bytes`() {
        val le = "第一章 hello".toByteArray(charset("UTF-16LE"))
        val be = "第一章 hello".toByteArray(charset("UTF-16BE"))

        assertEquals(TextEncoding.UTF16LE, TextEncodingDetector.detect(le).encoding)
        assertEquals(TextEncoding.UTF16BE, TextEncodingDetector.detect(be).encoding)
    }

    @Test
    fun `pure ascii stays utf8`() {
        assertEquals(TextEncoding.UTF8, TextEncodingDetector.detect("hello world".toByteArray()).encoding)
    }

    @Test
    fun `undecodable bytes fall back to lossy`() {
        // 0xFF 既不是合法 UTF-8 也不是合法 GB18030，且不构成任何 BOM。
        val bytes = byteArrayOf(0xFF.toByte(), 0x41, 0xC0.toByte(), 0x20, 0xFF.toByte(), 0x41)
        val result = TextEncodingDetector.detect(bytes)
        assertEquals(TextEncoding.GB18030, result.encoding)
        assertTrue(result.lossy)
    }

    @Test
    fun `empty input does not crash`() {
        val result = TextEncodingDetector.detect(ByteArray(0))
        assertEquals(TextEncoding.UTF8, result.encoding)
        assertFalse(result.lossy)
    }
}
