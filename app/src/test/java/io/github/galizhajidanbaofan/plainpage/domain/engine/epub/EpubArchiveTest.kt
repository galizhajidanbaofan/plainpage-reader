package io.github.galizhajidanbaofan.plainpage.domain.engine.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubArchiveTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `路径归一化处理点段`() {
        assertEquals("a/b", ZipPaths.normalize("a/./b"))
        assertEquals("b", ZipPaths.normalize("a/../b"))
        assertEquals("a/b/c", ZipPaths.normalize("a//b///c"))
    }

    @Test
    fun `越界路径归一化为 null`() {
        assertNull(ZipPaths.normalize("../b"))
        assertNull(ZipPaths.normalize("a/../../b"))
        assertNull(ZipPaths.normalize(""))
    }

    @Test
    fun `resolve 按 OPF 目录解析并去掉片段`() {
        assertEquals("OEBPS/Text/ch1.xhtml", ZipPaths.resolve("OEBPS", "Text/ch1.xhtml"))
        assertEquals("OEBPS/Text/ch1.xhtml", ZipPaths.resolve("OEBPS", "Text/ch1.xhtml#part2"))
        assertEquals("Text/ch1.xhtml", ZipPaths.resolve("OEBPS", "../Text/ch1.xhtml"))
        assertEquals("Text/x.xhtml", ZipPaths.resolve("OEBPS", "/Text/x.xhtml"))
    }

    @Test
    fun `resolve 解码百分号编码`() {
        assertEquals("OEBPS/Text/第一章.xhtml", ZipPaths.resolve("OEBPS", "Text/%E7%AC%AC%E4%B8%80%E7%AB%A0.xhtml"))
        assertEquals("OEBPS/Text/ch 1.xhtml", ZipPaths.resolve("OEBPS", "Text/ch%201.xhtml"))
    }

    @Test
    fun `按路径读条目`() {
        val file = EpubFixtures.writeZip(
            temp.root,
            "plain.zip",
            mapOf("OEBPS/a.xhtml" to "<p>hi</p>".toByteArray()),
        )
        EpubArchive(file).use { archive ->
            assertTrue(archive.contains("OEBPS/a.xhtml"))
            assertEquals("<p>hi</p>", archive.readTextIfPresent("OEBPS/a.xhtml", 1024))
            assertNull(archive.readTextIfPresent("OEBPS/missing.xhtml", 1024))
        }
    }

    @Test
    fun `路径穿越条目被忽略`() {
        val file = EpubFixtures.writeZip(
            temp.root,
            "evil.zip",
            mapOf(
                "../../escape.txt" to "恶意".toByteArray(),
                "OEBPS/ok.xhtml" to "<p>ok</p>".toByteArray(),
            ),
        )
        EpubArchive(file).use { archive ->
            assertFalse(archive.contains("../../escape.txt"))
            assertFalse(archive.contains("escape.txt"))
            assertTrue(archive.contains("OEBPS/ok.xhtml"))
        }
    }

    @Test
    fun `超过上限的条目抛 TOO_LARGE`() {
        val big = ByteArray(4096) { 'a'.code.toByte() }
        val file = EpubFixtures.writeZip(temp.root, "big.zip", mapOf("OEBPS/big.xhtml" to big))
        EpubArchive(file).use { archive ->
            val error = runCatching { archive.readBytesIfPresent("OEBPS/big.xhtml", 1024) }.exceptionOrNull()
            assertTrue(error is EpubFormatException)
            assertEquals(EpubFormatException.Kind.TOO_LARGE, (error as EpubFormatException).kind)
        }
    }

    @Test
    fun `不是 zip 时抛 CORRUPTED`() {
        val file = temp.newFile("broken.epub").apply { writeText("这不是 zip") }
        val error = runCatching { EpubArchive(file) }.exceptionOrNull()
        assertTrue(error is EpubFormatException)
        assertEquals(EpubFormatException.Kind.CORRUPTED, (error as EpubFormatException).kind)
    }
}
