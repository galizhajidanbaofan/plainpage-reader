package io.github.galizhajidanbaofan.plainpage.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPagerTest {

    @Test
    fun `空书没有页，也不会除零`() {
        val pager = ReaderPager(charCount = 0, windowChars = 100)

        assertEquals(0, pager.pageCount)
        assertEquals(0, pager.pageOf(0))
        assertEquals(0, pager.startOf(3))
        assertEquals(0f, pager.fraction(500))
        assertFalse(pager.canPrev(0))
        assertFalse(pager.canNext(0))
    }

    @Test
    fun `页数向上取整，最后不足一页也算一页`() {
        assertEquals(1, ReaderPager(charCount = 100, windowChars = 100).pageCount)
        assertEquals(2, ReaderPager(charCount = 101, windowChars = 100).pageCount)
        assertEquals(3, ReaderPager(charCount = 250, windowChars = 100).pageCount)
    }

    @Test
    fun `页码与起点可以互相换算`() {
        val pager = ReaderPager(charCount = 250, windowChars = 100)

        assertEquals(0, pager.startOf(0))
        assertEquals(100, pager.startOf(1))
        assertEquals(200, pager.startOf(2))

        assertEquals(0, pager.pageOf(0))
        assertEquals(0, pager.pageOf(99))
        assertEquals(1, pager.pageOf(100))
        assertEquals(2, pager.pageOf(249))
    }

    @Test
    fun `恢复进度时把任意偏移对齐到页首`() {
        val pager = ReaderPager(charCount = 250, windowChars = 100)

        assertEquals(0, pager.clampToPage(37))
        assertEquals(100, pager.clampToPage(150))
        // 越界输入不能抛，也不能跑出末页
        assertEquals(200, pager.clampToPage(9_999))
        assertEquals(0, pager.clampToPage(-42))
    }

    @Test
    fun `百分比按字符数算，并夹在 0 到 1 之间`() {
        val pager = ReaderPager(charCount = 200, windowChars = 100)

        assertEquals(0f, pager.fraction(0))
        assertEquals(0.5f, pager.fraction(100))
        assertEquals(1f, pager.fraction(200))
        assertEquals(1f, pager.fraction(1_000))
    }

    @Test
    fun `首页不能上一页，末页不能下一页`() {
        val pager = ReaderPager(charCount = 250, windowChars = 100)

        assertFalse(pager.canPrev(0))
        assertTrue(pager.canNext(0))
        assertTrue(pager.canPrev(200))
        assertFalse(pager.canNext(200))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `窗口宽度必须为正`() {
        ReaderPager(charCount = 100, windowChars = 0)
    }
}
