package io.github.galizhajidanbaofan.plainpage.domain.importing

import io.github.galizhajidanbaofan.plainpage.domain.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImportRulesTest {

    @Test
    fun `sanitizeFileName 去掉路径分隔符与控制字符`() {
        assertEquals("a_b_c", ImportRules.sanitizeFileName("a/b\\c"))
        assertEquals("x_y", ImportRules.sanitizeFileName("x\u0000y"))
        assertEquals("tab_here", ImportRules.sanitizeFileName("tab\there"))
    }

    @Test
    fun `sanitizeFileName 空名回退到未命名`() {
        assertEquals(ImportRules.FALLBACK_TITLE, ImportRules.sanitizeFileName(null))
        assertEquals(ImportRules.FALLBACK_TITLE, ImportRules.sanitizeFileName("   "))
        assertEquals(ImportRules.FALLBACK_TITLE, ImportRules.sanitizeFileName("..."))
    }

    @Test
    fun `sanitizeFileName 截断超长名字`() {
        val long = "书".repeat(500)
        assertEquals(120, ImportRules.sanitizeFileName(long).length)
    }

    @Test
    fun `titleFromFileName 去掉最后一个扩展名`() {
        assertEquals("三体", ImportRules.titleFromFileName("三体.epub"))
        // 名字里本来就有点，只砍最后一段
        assertEquals("v1.2 手册", ImportRules.titleFromFileName("v1.2 手册.txt"))
        assertEquals("没有扩展名", ImportRules.titleFromFileName("没有扩展名"))
        assertEquals(ImportRules.FALLBACK_TITLE, ImportRules.titleFromFileName(null))
    }

    @Test
    fun `detectFormat 优先扩展名，其次 MIME`() {
        assertEquals(BookFormat.EPUB, ImportRules.detectFormat("book.EPUB", "application/octet-stream"))
        assertEquals(BookFormat.PDF, ImportRules.detectFormat("book.Pdf", null))
        assertEquals(BookFormat.TXT, ImportRules.detectFormat("notes.txt", null))
    }

    @Test
    fun `detectFormat 扩展名不认识时用 MIME 兜底`() {
        assertEquals(BookFormat.EPUB, ImportRules.detectFormat("book.bin", "application/epub+zip"))
        assertEquals(BookFormat.TXT, ImportRules.detectFormat(null, "text/plain; charset=utf-8"))
        assertEquals(BookFormat.PDF, ImportRules.detectFormat("weird", "application/pdf"))
    }

    @Test
    fun `detectFormat 都不认识时返回 null 而不是猜成 TXT`() {
        assertNull(ImportRules.detectFormat("book.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertNull(ImportRules.detectFormat(null, null))
        assertNull(ImportRules.detectFormat("book", "application/zip"))
    }

    @Test
    fun `storageFileName 用随机 id 加格式扩展名`() {
        assertEquals("abc-123.epub", ImportRules.storageFileName("abc-123", BookFormat.EPUB))
        assertEquals("abc-123.txt", ImportRules.storageFileName("abc-123", BookFormat.TXT))
    }
}
