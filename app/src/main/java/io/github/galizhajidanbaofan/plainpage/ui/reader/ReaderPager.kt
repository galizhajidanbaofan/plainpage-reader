package io.github.galizhajidanbaofan.plainpage.ui.reader

/**
 * 正文按窗口翻页的纯算术，不碰 Android、不碰协程——所以它能被单测直接打穿。
 *
 * 为什么用「固定字符窗口」而不是「按屏幕高度分页」：
 * 前者与字体/字号/屏幕密度无关，同一份进度在任何设备上都能恢复到同一段文字；
 * 后者要在排版完成后才知道一页多少字，进度就得跟着设备变。
 */
class ReaderPager(
    val charCount: Int,
    val windowChars: Int = DEFAULT_WINDOW_CHARS,
) {

    init {
        require(windowChars > 0) { "windowChars 必须为正数" }
    }

    /** 总页数；空书为 0。 */
    val pageCount: Int = if (charCount <= 0) 0 else (charCount + windowChars - 1) / windowChars

    fun pageOf(startChar: Int): Int {
        if (pageCount == 0) return 0
        val offset = startChar.coerceIn(0, maxOf(0, charCount - 1))
        return (offset / windowChars).coerceIn(0, pageCount - 1)
    }

    fun startOf(page: Int): Int {
        if (pageCount == 0) return 0
        return page.coerceIn(0, pageCount - 1) * windowChars
    }

    /** 把任意字符偏移对齐到它所在页的起点（恢复进度时用）。 */
    fun clampToPage(startChar: Int): Int = startOf(pageOf(startChar))

    fun fraction(startChar: Int): Float {
        if (charCount <= 0) return 0f
        return (startChar.coerceIn(0, charCount) .toFloat() / charCount.toFloat()).coerceIn(0f, 1f)
    }

    fun canPrev(startChar: Int): Boolean = pageOf(startChar) > 0

    fun canNext(startChar: Int): Boolean = pageCount > 0 && pageOf(startChar) < pageCount - 1

    companion object {
        /**
         * 一个窗口约等于一屏中文正文（18sp、行距 1.6、400dp 宽 ≈ 每行 22 字 × 45 行）。
         * 宁可多给一点、让用户滚一下，也不要因为字体变大把正文切碎。
         */
        const val DEFAULT_WINDOW_CHARS = 1_200
    }
}
