package io.github.galizhajidanbaofan.plainpage.ui.reader

import io.github.galizhajidanbaofan.plainpage.domain.engine.PictureRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageSlotsTest {

    private fun picture(at: Int, path: String = "Images/p$at.png") =
        PictureRef(at = at, path = path, alt = null)

    @Test
    fun `没有插图时一页一屏`() {
        val slots = PageSlots.build(3, 100, emptyList()) { page -> page * 100 }
        assertEquals(3, slots.size)
        slots.forEachIndexed { index, slot ->
            assertTrue(slot is PageSlots.Slot.Text)
            assertEquals(index * 100, (slot as PageSlots.Slot.Text).startChar)
        }
    }

    @Test
    fun `插图排在自己所属页之后`() {
        // 三页，窗口 100：插图偏移 150 属于第 1 页（100..199）
        val slots = PageSlots.build(3, 100, listOf(picture(150))) { page -> page * 100 }
        assertEquals(4, slots.size)
        assertTrue(slots[0] is PageSlots.Slot.Text)
        assertTrue(slots[1] is PageSlots.Slot.Text)
        assertEquals(150, (slots[2] as PageSlots.Slot.Picture).ref.at)
        assertEquals(1, (slots[2] as PageSlots.Slot.Picture).afterPage)
        assertEquals(2, (slots[3] as PageSlots.Slot.Text).page)
    }

    @Test
    fun `落在最后一页之后的插图追加在末尾，不丢`() {
        val slots = PageSlots.build(2, 100, listOf(picture(500))) { page -> page * 100 }
        assertEquals(3, slots.size)
        assertEquals(500, (slots.last() as PageSlots.Slot.Picture).ref.at)
        assertEquals(1, (slots.last() as PageSlots.Slot.Picture).afterPage)
    }

    @Test
    fun `同一页的多张图按偏移顺序排在页后`() {
        val slots = PageSlots.build(2, 100, listOf(picture(10), picture(20))) { page -> page * 100 }
        assertEquals(4, slots.size)
        assertEquals(listOf(10, 20), slots.filterIsInstance<PageSlots.Slot.Picture>().map { it.ref.at })
    }

    @Test
    fun `空书只剩插图屏`() {
        val slots = PageSlots.build(0, 100, listOf(picture(0))) { page -> page * 100 }
        assertEquals(1, slots.size)
        assertTrue(slots.single() is PageSlots.Slot.Picture)
    }

    @Test
    fun `页码能找回对应正文屏`() {
        val slots = PageSlots.build(3, 100, listOf(picture(150))) { page -> page * 100 }
        assertEquals(0, PageSlots.slotOfPage(slots, 0))
        assertEquals(1, PageSlots.slotOfPage(slots, 1))
        assertEquals(3, PageSlots.slotOfPage(slots, 2))
    }

    @Test
    fun `页码越界时退回第一屏`() {
        val slots = PageSlots.build(2, 100, emptyList()) { page -> page * 100 }
        assertEquals(0, PageSlots.slotOfPage(slots, 99))
    }
}
