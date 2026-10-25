package io.github.galizhajidanbaofan.plainpage.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookFormatTest {

    @Test
    fun `recognises supported extensions regardless of case`() {
        assertEquals(BookFormat.EPUB, BookFormat.fromFileName("三体.epub"))
        assertEquals(BookFormat.TXT, BookFormat.fromFileName("NOTES.TXT"))
        assertEquals(BookFormat.PDF, BookFormat.fromFileName("paper.Pdf"))
    }

    @Test
    fun `keeps dots inside the name`() {
        assertEquals(BookFormat.EPUB, BookFormat.fromFileName("v1.2.final.epub"))
    }

    @Test
    fun `rejects unknown or missing extension`() {
        assertNull(BookFormat.fromFileName("book.mobi"))
        assertNull(BookFormat.fromFileName("book"))
        assertNull(BookFormat.fromFileName(""))
        assertNull(BookFormat.fromFileName("archive.tar.gz"))
    }
}
