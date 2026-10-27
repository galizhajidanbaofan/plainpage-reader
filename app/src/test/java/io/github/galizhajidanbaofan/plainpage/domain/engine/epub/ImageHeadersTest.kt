package io.github.galizhajidanbaofan.plainpage.domain.engine.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * 图片头解析。
 *
 * 为什么值得单独写一套手写解析：索引阶段要判断"这张图够不够大、值不值得占一页"，
 * 而 `BitmapFactory` 属于 Android 运行时，在纯 JVM 的索引层用不上（也不该为了量尺寸
 * 就分配整张位图）。这里只读几十个字节。
 */
class ImageHeadersTest {

    private fun pngBytes(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        out.write(byteArrayOf(0, 0, 0, 13))
        out.write("IHDR".toByteArray())
        out.write(intBytes(width))
        out.write(intBytes(height))
        out.write(byteArrayOf(8, 6, 0, 0, 0))
        out.write(ByteArray(32))
        return out.toByteArray()
    }

    private fun intBytes(value: Int) = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    @Test
    fun readsPngSize() {
        assertEquals(600 to 800, ImageHeaders.sizeOf(pngBytes(600, 800)))
    }

    @Test
    fun readsJpegSize() {
        // SOI + APP0 + SOF0(高 800, 宽 600)
        val jpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10,
        ) + ByteArray(14) + byteArrayOf(
            0xFF.toByte(), 0xC0.toByte(), 0x00, 0x11, 0x08,
            0x03, 0x20, 0x02, 0x58, 0x03,
        ) + ByteArray(20)
        assertEquals(600 to 800, ImageHeaders.sizeOf(jpeg))
    }

    @Test
    fun readsGifSize() {
        val gif = "GIF89a".toByteArray() + byteArrayOf(0x58, 0x02, 0x20, 0x03) + ByteArray(20)
        assertEquals(600 to 800, ImageHeaders.sizeOf(gif))
    }

    @Test
    fun readsLosslessWebpSize() {
        val header = ByteArrayOutputStream()
        header.write("RIFF".toByteArray())
        header.write(byteArrayOf(0, 0, 0, 0))
        header.write("WEBP".toByteArray())
        header.write("VP8L".toByteArray())
        header.write(byteArrayOf(0, 0, 0, 0))
        header.write(byteArrayOf(0x2F.toByte()))
        // 14 位宽-1 / 14 位高-1：宽 600、高 800
        val bits = (600 - 1) or ((800 - 1) shl 14)
        header.write(byteArrayOf(bits.toByte(), (bits ushr 8).toByte(), (bits ushr 16).toByte(), (bits ushr 24).toByte()))
        header.write(ByteArray(16))   // 补齐，让 isWebp 的长度检查通过
        assertEquals(600 to 800, ImageHeaders.sizeOf(header.toByteArray()))
    }

    @Test
    fun unknownFormatReturnsNull() {
        assertNull(ImageHeaders.sizeOf(ByteArray(64) { 0x42 }))
    }

    @Test
    fun truncatedBytesReturnNull() {
        assertNull(ImageHeaders.sizeOf(ByteArray(4)))
    }
}
