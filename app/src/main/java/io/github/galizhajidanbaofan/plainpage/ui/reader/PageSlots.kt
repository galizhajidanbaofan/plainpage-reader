package io.github.galizhajidanbaofan.plainpage.ui.reader

import io.github.galizhajidanbaofan.plainpage.domain.engine.PictureRef

/**
 * 翻页模式下「一屏」的排布算法，和 Compose 无关，所以能单独测。
 *
 * 为什么插图要单独占一屏：正文每次取 [windowChars] 个字符，窗口是均匀的。
 * 图要是插进正文流里，那一屏的正文就会被图挤出可视区、直接被裁掉——
 * 读者会以为书丢了内容。让图独占一屏，任何一屏都不会被挤爆，
 * 代价只是图的上下文要往前翻一屏。
 */
object PageSlots {

    /** 一屏要么是一段正文，要么是一张整页插图。 */
    sealed interface Slot {
        /** 正文屏，[startChar] 是这一屏在全书文本里的起始偏移。 */
        data class Text(val startChar: Int, val page: Int) : Slot

        /** 插图屏，插在 [afterPage] 这一页之后。 */
        data class Picture(val ref: PictureRef, val afterPage: Int) : Slot
    }

    /**
     * 把插图和正文屏排成一条时间线。
     *
     * [pictures] 必须按字符偏移升序；偏移落在某页窗口内的图，排在该页之后。
     * 落在最后一页之后的图依次追加到末尾，不会丢。
     */
    fun build(
        pageCount: Int,
        windowChars: Int,
        pictures: List<PictureRef>,
        startOfPage: (Int) -> Int,
    ): List<Slot> {
        if (pageCount <= 0) {
            return pictures.map { Slot.Picture(it, 0) }
        }
        if (pictures.isEmpty()) {
            return (0 until pageCount).map { Slot.Text(startOfPage(it), it) }
        }
        val result = ArrayList<Slot>(pageCount + pictures.size)
        var cursor = 0
        for (page in 0 until pageCount) {
            val start = startOfPage(page)
            result.add(Slot.Text(start, page))
            val pageEnd = start + windowChars
            while (cursor < pictures.size && pictures[cursor].at < pageEnd) {
                result.add(Slot.Picture(pictures[cursor], page))
                cursor++
            }
        }
        while (cursor < pictures.size) {
            result.add(Slot.Picture(pictures[cursor], pageCount - 1))
            cursor++
        }
        return result
    }

    /** 给定页码，找到它的正文屏下标；找不到时退回 0。 */
    fun slotOfPage(slots: List<Slot>, page: Int): Int {
        val found = slots.indexOfFirst { it is Slot.Text && it.page == page }
        return if (found >= 0) found else 0
    }
}
