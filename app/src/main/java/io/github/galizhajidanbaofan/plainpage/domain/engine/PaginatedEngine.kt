package io.github.galizhajidanbaofan.plainpage.domain.engine

import io.github.galizhajidanbaofan.plainpage.domain.model.ReadingLocation

/**
 * 「可以按字符窗口取正文」的引擎。TXT 直接满足；EPUB 由章节资源流式解码后也能满足。
 *
 * 为什么把这几个成员单列成子接口，而不是塞进 [ReaderEngine]：
 * PDF 是按页位图渲染的，不存在"字符偏移"这个概念，硬塞会让 PDF 实现被迫返回无意义的值。
 */
interface PaginatedEngine : ReaderEngine {

    /** 全文字符数；未打开时为 0。 */
    val charCount: Int

    /** 解码时出现过替换字符，UI 可提示"编码可能不准"。 */
    val lossyDecoding: Boolean

    /** 取 [startChar] 起、最多 [count] 个字符；越界由实现夹紧。 */
    suspend fun readText(startChar: Int, count: Int): String

    /** 由字符偏移反推可持久化的位置；没有可持久化位置时返回 null。 */
    fun positionOf(charOffset: Int): ReadingLocation?

    /**
     * 把持久化位置换算回字符偏移，用于"从上次位置继续读"。
     *
     * 默认实现只认 [ReadingLocation.Txt]：TXT 的偏移就是它自己的坐标。
     * EPUB 覆盖它，把 href + 章内百分比换回全书偏移——这样阅读页对三种格式只有一套翻页逻辑。
     */
    fun charOffsetOf(location: ReadingLocation): Int? =
        (location as? ReadingLocation.Txt)?.charOffset
}
