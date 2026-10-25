package io.github.galizhajidanbaofan.plainpage.ui.format

import io.github.galizhajidanbaofan.plainpage.domain.model.ReadingLocation
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 展示用格式化。刻意做成**纯函数 + 可注入时钟/时区**，这样能直接单测，
 * 不必为了「昨天该显示什么」去跑仪器测试。
 *
 * 说明：P0 面向中文用户，文案内联在 Kotlin 里；等真的要做多语言时再迁到 strings.xml
 * （那时需要 Context，纯函数就不再合适，会连同调用点一起改）。
 */
object DisplayFormats {

    private val timeOfDay = DateTimeFormatter.ofPattern("HH:mm")
    private val monthDay = DateTimeFormatter.ofPattern("M月d日")
    private val fullDate = DateTimeFormatter.ofPattern("yyyy年M月d日")

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE

    /** 人类可读体积，保留一位小数；B 不显示小数。 */
    fun bytes(size: Long): String {
        if (size < 0) return "—"
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> formatOneDecimal(size / 1024.0, "KB")
            size < 1024L * 1024 * 1024 -> formatOneDecimal(size / (1024.0 * 1024.0), "MB")
            else -> formatOneDecimal(size / (1024.0 * 1024.0 * 1024.0), "GB")
        }
    }

    /**
     * 「上次读到什么时候」：1 分钟内「刚刚」，1 小时内「N 分钟前」，
     * 当天「今天 HH:mm」，昨天「昨天 HH:mm」，同年「M月d日」，跨年「yyyy年M月d日」。
     * [lastOpenedAt] 为 null 表示从未打开。
     */
    fun lastRead(
        nowMillis: Long,
        lastOpenedAt: Long?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (lastOpenedAt == null) return "未打开"
        val delta = nowMillis - lastOpenedAt
        if (delta in 0 until MINUTE) return "刚刚"
        if (delta in MINUTE until HOUR) return "${delta / MINUTE} 分钟前"
        if (delta < 0) return "刚刚" // 时钟回拨：不显示「-3 分钟前」这种话

        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val then = Instant.ofEpochMilli(lastOpenedAt).atZone(zone)
        val thenTime = then.format(timeOfDay)

        val daysApart = now.toLocalDate().toEpochDay() - then.toLocalDate().toEpochDay()
        return when {
            daysApart == 0L -> "今天 $thenTime"
            daysApart == 1L -> "昨天 $thenTime"
            then.year == now.year -> then.format(monthDay)
            else -> then.format(fullDate)
        }
    }

    /**
     * 能画成进度条的比例：只有 TXT 手里是**真·全文比例**。
     * EPUB 目前只存章内位置、PDF 只存页码，分母未知，宁可留白也不画一条会骗人的进度条。
     */
    fun barFraction(location: ReadingLocation?): Float? = when (location) {
        is ReadingLocation.Txt -> location.fraction.coerceIn(0f, 1f)
        else -> null
    }

    /** 阅读进度的一句话摘要；没有进度返回 null。 */
    fun progressSummary(location: ReadingLocation?): String? = when (location) {
        null -> null
        is ReadingLocation.Epub -> "第 ${(location.fractionInChapter * 100).toInt()}% 章节内位置"
        is ReadingLocation.Pdf -> "第 ${location.pageIndex + 1} 页"
        is ReadingLocation.Txt -> "全文 ${(location.fraction * 100).toInt()}%"
    }

    private fun formatOneDecimal(value: Double, unit: String): String {
        val rounded = kotlin.math.round(value * 10) / 10
        val text = if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
        return "$text $unit"
    }
}
