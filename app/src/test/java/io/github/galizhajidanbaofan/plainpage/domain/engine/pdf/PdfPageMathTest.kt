package io.github.galizhajidanbaofan.plainpage.domain.engine.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageMathTest {

    @Test
    fun `clampPage 把越界页码拉回合法范围`() {
        assertEquals(0, PdfPageMath.clampPage(-5, 10))
        assertEquals(9, PdfPageMath.clampPage(99, 10))
        assertEquals(4, PdfPageMath.clampPage(4, 10))
    }

    @Test
    fun `空书的页码恒为 0 且百分比为 0`() {
        assertEquals(0, PdfPageMath.clampPage(7, 0))
        assertEquals(0f, PdfPageMath.fractionOf(7, 0), 0f)
    }

    @Test
    fun `首页是 0 末页是 1`() {
        assertEquals(0f, PdfPageMath.fractionOf(0, 11), 0.0001f)
        assertEquals(1f, PdfPageMath.fractionOf(10, 11), 0.0001f)
        assertEquals(0.5f, PdfPageMath.fractionOf(5, 11), 0.0001f)
    }

    @Test
    fun `只有一页时进度直接算读完了`() {
        assertEquals(1f, PdfPageMath.fractionOf(0, 1), 0f)
    }

    @Test
    fun `百分比回算页码四舍五入而不是截断`() {
        // 0.99 落在最后一页，不能因为截断掉回倒数第二页
        assertEquals(10, PdfPageMath.pageOf(0.99f, 11))
        assertEquals(0, PdfPageMath.pageOf(0f, 11))
        assertEquals(5, PdfPageMath.pageOf(0.5f, 11))
        assertEquals(0, PdfPageMath.pageOf(-1f, 11))
        assertEquals(10, PdfPageMath.pageOf(2f, 11))
    }

    @Test
    fun `百分比与页码来回换算稳定`() {
        for (page in 0 until 11) {
            val fraction = PdfPageMath.fractionOf(page, 11)
            assertEquals(page, PdfPageMath.pageOf(fraction, 11))
        }
    }

    @Test
    fun `A4 页按宽度算出等比高度`() {
        // 595 x 842 点是标准 A4
        val size = PdfPageMath.bitmapSize(pageWidth = 595, pageHeight = 842, targetWidthPx = 1080)
        assertEquals(1080, size.width)
        assertEquals(1528, size.height)
    }

    @Test
    fun `渲染宽度被夹在上下限内`() {
        val tiny = PdfPageMath.bitmapSize(595, 842, targetWidthPx = 10)
        assertEquals(PdfPageMath.MIN_RENDER_WIDTH_PX, tiny.width)

        // 只有"矮页"才可能真正吃到宽度上限：A4 这种比例在 2400 宽时像素数会先撞到上限
        val huge = PdfPageMath.bitmapSize(1000, 100, targetWidthPx = 99_999)
        assertEquals(PdfPageMath.MAX_RENDER_WIDTH_PX, huge.width)
    }

    @Test
    fun `超长页面的像素总数被限制住`() {
        // 1:20 的条幅页，按 2400 宽渲染会得到 48000 高、1.15 亿像素
        val size = PdfPageMath.bitmapSize(pageWidth = 100, pageHeight = 2000, targetWidthPx = 2400)
        val pixels = size.width.toLong() * size.height
        assertTrue("像素数 $pixels 超过上限", pixels <= PdfPageMath.MAX_BITMAP_PIXELS)
        // 等比缩：宽高比仍然接近 1:20
        val ratio = size.height.toFloat() / size.width
        assertTrue("比例被破坏：$ratio", ratio in 19.0f..21.0f)
    }

    @Test
    fun `非法页尺寸退回方页而不是 0`() {
        val size = PdfPageMath.bitmapSize(pageWidth = 0, pageHeight = -1, targetWidthPx = 720)
        assertEquals(720, size.width)
        assertEquals(720, size.height)
    }
}
