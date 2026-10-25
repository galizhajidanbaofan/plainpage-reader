package io.github.galizhajidanbaofan.plainpage.domain.model

/**
 * 阅读位置。三种格式各自需要的定位信息不同，这里用封闭类型表达，
 * 避免用"一个百分比 + 一堆可空字段"糊在一起导致恢复位置不准。
 */
sealed interface ReadingLocation {

    /** EPUB：定位到 spine 内某个资源（href）里的相对位置。 */
    data class Epub(
        val href: String,
        /** 章节内相对位置，0f..1f。 */
        val fractionInChapter: Float,
    ) : ReadingLocation

    /** PDF：页码（0 基）+ 该页内的滚动比例。 */
    data class Pdf(
        val pageIndex: Int,
        val fractionInPage: Float,
    ) : ReadingLocation

    /** TXT：字符偏移是主定位（编码识别后按 UTF-16 char 计），章节索引与百分比只做展示。 */
    data class Txt(
        val charOffset: Int,
        val chapterIndex: Int,
        val fraction: Float,
    ) : ReadingLocation
}

/**
 * 位置的存储编码。选自定义文本格式而不是 JSON：
 * 实现只有几行、无额外依赖、出错时能一眼看出坏在哪。
 */
object ReadingLocationCodec {

    private const val SEP = '|'

    fun encode(location: ReadingLocation): String = when (location) {
        is ReadingLocation.Epub -> "epub$SEP${location.href}$SEP${location.fractionInChapter}"
        is ReadingLocation.Pdf -> "pdf$SEP${location.pageIndex}$SEP${location.fractionInPage}"
        is ReadingLocation.Txt ->
            "txt$SEP${location.charOffset}$SEP${location.chapterIndex}$SEP${location.fraction}"
    }

    /** 解析失败返回 null（调用方按"没有进度"处理，不允许抛异常导致打不开书）。 */
    fun decode(raw: String?): ReadingLocation? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(SEP)
        return try {
            when (parts.firstOrNull()) {
                "epub" -> {
                    if (parts.size != 3) return null
                    val fraction = parts[2].toFloatOrNull() ?: return null
                    if (parts[1].isBlank()) return null
                    ReadingLocation.Epub(href = parts[1], fractionInChapter = fraction.coerceIn(0f, 1f))
                }

                "pdf" -> {
                    if (parts.size != 3) return null
                    val page = parts[1].toIntOrNull() ?: return null
                    val fraction = parts[2].toFloatOrNull() ?: return null
                    if (page < 0) return null
                    ReadingLocation.Pdf(pageIndex = page, fractionInPage = fraction.coerceIn(0f, 1f))
                }

                "txt" -> {
                    if (parts.size != 4) return null
                    val offset = parts[1].toIntOrNull() ?: return null
                    val chapter = parts[2].toIntOrNull() ?: return null
                    val fraction = parts[3].toFloatOrNull() ?: return null
                    if (offset < 0 || chapter < 0) return null
                    ReadingLocation.Txt(
                        charOffset = offset,
                        chapterIndex = chapter,
                        fraction = fraction.coerceIn(0f, 1f),
                    )
                }

                else -> null
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
