package io.github.galizhajidanbaofan.plainpage.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 滚动模式的分块算术。
 *
 * 这几条断言看着琐碎，但它们护着两件真事：
 * 1. 空书不能出现「一个空块」（否则界面出现一条无意义的滚动区）；
 * 2. 偏移与块的换算必须可逆到「所属块」——进度恢复就靠它。
 */
class ScrollChunkMathTest {

    @Test
    fun `空书没有块`() {
        assertEquals(0, ScrollChunkMath.chunkCount(0))
        assertEquals(0, ScrollChunkMath.chunkCount(-5))
    }

    @Test
    fun `整块整除不多算一块`() {
        assertEquals(1, ScrollChunkMath.chunkCount(1_500))
        assertEquals(2, ScrollChunkMath.chunkCount(3_000))
    }

    @Test
    fun `最后不足一块也算一块`() {
        assertEquals(2, ScrollChunkMath.chunkCount(1_501))
        assertEquals(3, ScrollChunkMath.chunkCount(4_200))
    }

    @Test
    fun `偏移落在正确的块`() {
        val charCount = 5_000
        assertEquals(0, ScrollChunkMath.chunkOf(0, charCount))
        assertEquals(0, ScrollChunkMath.chunkOf(1_499, charCount))
        assertEquals(1, ScrollChunkMath.chunkOf(1_500, charCount))
        assertEquals(3, ScrollChunkMath.chunkOf(4_999, charCount))
    }

    @Test
    fun `越界偏移被夹紧到首块或末块`() {
        val charCount = 5_000
        assertEquals(0, ScrollChunkMath.chunkOf(-100, charCount))
        assertEquals(3, ScrollChunkMath.chunkOf(99_999, charCount))
        // 空书不该崩：夹紧后返回 0
        assertEquals(0, ScrollChunkMath.chunkOf(10, 0))
    }

    @Test
    fun `块号换算回偏移`() {
        assertEquals(0, ScrollChunkMath.charOffsetOf(0))
        assertEquals(1_500, ScrollChunkMath.charOffsetOf(1))
        assertEquals(0, ScrollChunkMath.charOffsetOf(-3))
    }

    @Test
    fun `百分比封顶在 0 到 1 之间`() {
        assertEquals(0f, ScrollChunkMath.fraction(0, 1_000), 0.0001f)
        assertEquals(0.5f, ScrollChunkMath.fraction(500, 1_000), 0.0001f)
        assertEquals(1f, ScrollChunkMath.fraction(5_000, 1_000), 0.0001f)
        assertEquals(0f, ScrollChunkMath.fraction(10, 0), 0.0001f)
    }
}
