package io.github.galizhajidanbaofan.plainpage.data.local

import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.domain.model.BookFormat
import io.github.galizhajidanbaofan.plainpage.domain.model.ReadingLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookEntityTest {

    private fun book(
        format: BookFormat = BookFormat.EPUB,
        readingLocation: ReadingLocation? = null,
    ) = Book(
        id = 7L,
        title = "三体",
        author = "刘慈欣",
        format = format,
        localPath = "/data/user/0/pkg/files/books/abc.epub",
        sourceUri = "content://com.android.providers.downloads.documents/document/12",
        coverPath = null,
        byteSize = 2_048L,
        importedAt = 1_792_908_000_000L,
        lastOpenedAt = 1_792_908_100_000L,
        readingLocation = readingLocation,
    )

    @Test
    fun `往返转换保留所有字段`() {
        val original = book(readingLocation = ReadingLocation.Epub("OEBPS/ch1.xhtml", 0.25f))
        assertEquals(original, original.toEntity().toDomain())
    }

    @Test
    fun `格式存的是枚举名而不是序数`() {
        assertEquals("TXT", book(format = BookFormat.TXT).toEntity().format)
    }

    @Test
    fun `未读过的书往返后进度仍是 null`() {
        assertNull(book().toEntity().toDomain()?.readingLocation)
    }

    @Test
    fun `库里出现未知格式时丢弃而不是崩`() {
        val entity = book().toEntity().copy(format = "MOBI")
        assertNull(entity.toDomain())
    }

    @Test
    fun `进度串损坏时保留书籍、丢掉进度`() {
        val entity = book(readingLocation = ReadingLocation.Pdf(3, 0.5f)).toEntity().copy(readingLocation = "garbage")
        val domain = entity.toDomain()
        assertEquals("三体", domain?.title)
        assertNull(domain?.readingLocation)
    }
}
