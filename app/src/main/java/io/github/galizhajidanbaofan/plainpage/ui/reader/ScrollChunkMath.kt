package io.github.galizhajidanbaofan.plainpage.ui.reader

/**
 * 滚动模式把整本书切成若干块，每块独立取文、独立渲染。
 *
 * 为什么不在滚动模式下把全文一次读进来：100 MB 级的 TXT 会直接吃掉几百 MB 堆内存，
 * 而且用户只看得到眼前两屏。分块后 `LazyColumn` 只为可见的块持有文本，
 * 引擎的字符偏移坐标仍然是唯一的进度真源——翻页模式与滚动模式因此可以互相接续。
 *
 * 这里只放纯算术，好让单测不需要 Android。
 */
object ScrollChunkMath {

    /**
     * 一块多少字符。取值偏大（约等于两屏正文）：
     * 块越小滚动越顺滑，但边界切在段中间的机会越多、取文次数也越多。
     */
    const val CHUNK_CHARS: Int = 1_500

    /** 空书返回 0（而不是 1）：界面上不该出现一个空块。 */
    fun chunkCount(charCount: Int, chunkChars: Int = CHUNK_CHARS): Int {
        if (charCount <= 0 || chunkChars <= 0) return 0
        return (charCount + chunkChars - 1) / chunkChars
    }

    /** 字符偏移落在第几块；越界夹紧到首块/末块。 */
    fun chunkOf(charOffset: Int, charCount: Int, chunkChars: Int = CHUNK_CHARS): Int {
        val count = chunkCount(charCount, chunkChars)
        if (count == 0) return 0
        val offset = charOffset.coerceIn(0, maxOf(0, charCount - 1))
        return (offset / chunkChars).coerceIn(0, count - 1)
    }

    fun charOffsetOf(chunk: Int, chunkChars: Int = CHUNK_CHARS): Int {
        if (chunk <= 0) return 0
        return chunk * chunkChars
    }

    fun fraction(charOffset: Int, charCount: Int): Float {
        if (charCount <= 0) return 0f
        return (charOffset.coerceIn(0, charCount).toFloat() / charCount.toFloat()).coerceIn(0f, 1f)
    }
}
