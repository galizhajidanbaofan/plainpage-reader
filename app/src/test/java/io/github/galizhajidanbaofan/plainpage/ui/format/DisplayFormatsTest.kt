package io.github.galizhajidanbaofan.plainpage.ui.format

import io.github.galizhajidanbaofan.plainpage.domain.model.ReadingLocation
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DisplayFormatsTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    /** 2026-10-25 14:00:00 +08:00 */
    private val now = 1_792_908_000_000L

    @Test
    fun `bytes 按单位分档并保留一位小数`() {
        assertEquals("0 B", DisplayFormats.bytes(0))
        assertEquals("512 B", DisplayFormats.bytes(512))
        assertEquals("1 KB", DisplayFormats.bytes(1024))
        assertEquals("1.5 KB", DisplayFormats.bytes(1536))
        assertEquals("2 MB", DisplayFormats.bytes(2L * 1024 * 1024))
        assertEquals("1.5 GB", DisplayFormats.bytes((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `bytes 对负数返回占位符而不是崩溃`() {
        assertEquals("—", DisplayFormats.bytes(-1))
    }

    @Test
    fun `lastRead 未打开过`() {
        assertEquals("未打开", DisplayFormats.lastRead(now, null, zone))
    }

    @Test
    fun `lastRead 一分钟内显示刚刚`() {
        assertEquals("刚刚", DisplayFormats.lastRead(now, now - 30_000, zone))
    }

    @Test
    fun `lastRead 一小时内显示分钟数`() {
        assertEquals("5 分钟前", DisplayFormats.lastRead(now, now - 5 * 60_000, zone))
        assertEquals("59 分钟前", DisplayFormats.lastRead(now, now - 59 * 60_000, zone))
    }

    @Test
    fun `lastRead 当天显示今天加时刻`() {
        // 2026-10-25 09:30 +08:00
        assertEquals("今天 09:30", DisplayFormats.lastRead(now, 1_792_891_800_000L, zone))
    }

    @Test
    fun `lastRead 昨天显示昨天加时刻`() {
        // 2026-10-24 14:00 +08:00（时区固定的差值，跨 DST 也成立）
        assertEquals("昨天 14:00", DisplayFormats.lastRead(now, 1_792_821_600_000L, zone))
    }

    @Test
    fun `lastRead 同年更早显示月日`() {
        // 2026-03-08 10:00 +08:00
        assertEquals("3月8日", DisplayFormats.lastRead(now, 1_772_935_200_000L, zone))
    }

    @Test
    fun `lastRead 跨年显示完整日期`() {
        // 2024-12-31 23:00 +08:00
        assertEquals("2024年12月31日", DisplayFormats.lastRead(now, 1_735_657_200_000L, zone))
    }

    @Test
    fun `lastRead 时钟回拨不显示负数`() {
        assertEquals("刚刚", DisplayFormats.lastRead(now, now + 60_000, zone))
    }

    @Test
    fun `progressSummary 三种格式各有说法，没有进度返回 null`() {
        assertNull(DisplayFormats.progressSummary(null))
        assertEquals(
            "第 42% 章节内位置",
            DisplayFormats.progressSummary(ReadingLocation.Epub("ch1.xhtml", 0.42f)),
        )
        assertEquals("第 13 页", DisplayFormats.progressSummary(ReadingLocation.Pdf(pageIndex = 12, fractionInPage = 0.5f)))
        assertEquals("全文 7%", DisplayFormats.progressSummary(ReadingLocation.Txt(charOffset = 700, chapterIndex = 1, fraction = 0.07f)))
    }
}
