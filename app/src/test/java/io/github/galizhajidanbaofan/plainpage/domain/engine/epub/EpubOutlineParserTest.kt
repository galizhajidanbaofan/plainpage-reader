package io.github.galizhajidanbaofan.plainpage.domain.engine.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubOutlineParserTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `按 NCX 读出目录`() {
        val file = EpubFixtures.build(
            temp.root,
            chapters = listOf(
                EpubFixtures.chapter("第一章 雪夜", "<p>一</p>", 1),
                EpubFixtures.chapter("第二章 深巷", "<p>二</p>", 2),
            ),
        )
        EpubArchive(file).use { archive ->
            val entries = EpubOutlineParser.read(archive, EpubPackageParser.read(archive))
            assertEquals(listOf("第一章 雪夜", "第二章 深巷"), entries.map { it.title })
            assertEquals("OEBPS/Text/ch1.xhtml", entries.first().href)
            assertEquals(0, entries.first().level)
        }
    }

    @Test
    fun `嵌套 navPoint 记成层级`() {
        val file = writeCustomNcx(
            """
            <navPoint id="n1" playOrder="1">
              <navLabel><text>第一部分</text></navLabel>
              <content src="Text/ch1.xhtml"/>
              <navPoint id="n1-1" playOrder="2">
                <navLabel><text>小节甲</text></navLabel>
                <content src="Text/ch2.xhtml"/>
              </navPoint>
            </navPoint>
            """.trimIndent(),
        )
        EpubArchive(file).use { archive ->
            val entries = EpubOutlineParser.read(archive, EpubPackageParser.read(archive))
            assertEquals(listOf("第一部分", "小节甲"), entries.map { it.title })
            assertEquals(listOf(0, 1), entries.map { it.level })
        }
    }

    @Test
    fun `没有 NCX 时读 EPUB3 nav`() {
        val file = EpubFixtures.build(temp.root, chapters = TWO_CHAPTERS, withNcx = false, withNav = true)
        EpubArchive(file).use { archive ->
            val entries = EpubOutlineParser.read(archive, EpubPackageParser.read(archive))
            assertEquals(listOf("第一章", "第二章"), entries.map { it.title })
            assertEquals("OEBPS/Text/ch1.xhtml", entries.first().href)
        }
    }

    @Test
    fun `既无 NCX 也无 nav 时按 spine 兜底`() {
        val file = EpubFixtures.build(temp.root, chapters = TWO_CHAPTERS, withNcx = false, withNav = false)
        EpubArchive(file).use { archive ->
            val entries = EpubOutlineParser.read(archive, EpubPackageParser.read(archive))
            assertEquals(listOf("ch1", "ch2"), entries.map { it.title })
            assertTrue(entries.all { it.level == 0 })
        }
    }

    private fun writeCustomNcx(navPoints: String): java.io.File {
        val ncx = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">""")
            appendLine("""  <head><meta name="dtb:uid" content="x"/></head>""")
            appendLine("  <docTitle><text>定制</text></docTitle>")
            appendLine("  <navMap>")
            appendLine(navPoints)
            appendLine("  </navMap>")
            appendLine("</ncx>")
        }
        val opf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>定制</dc:title><dc:identifier id="id">x</dc:identifier>
              </metadata>
              <manifest>
                <item id="ch1" href="Text/ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="ch2" href="Text/ch2.xhtml" media-type="application/xhtml+xml"/>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
              </manifest>
              <spine toc="ncx"><itemref idref="ch1"/><itemref idref="ch2"/></spine>
            </package>
        """.trimIndent()
        return EpubFixtures.writeZip(
            temp.root,
            "ncx-${System.nanoTime()}.epub",
            linkedMapOf(
                "META-INF/container.xml" to CONTAINER.toByteArray(),
                "OEBPS/content.opf" to opf.toByteArray(),
                "OEBPS/toc.ncx" to ncx.toByteArray(),
                "OEBPS/Text/ch1.xhtml" to "<html><body><p>一</p></body></html>".toByteArray(),
                "OEBPS/Text/ch2.xhtml" to "<html><body><p>二</p></body></html>".toByteArray(),
            ),
        )
    }

    private companion object {
        val TWO_CHAPTERS = listOf(
            EpubFixtures.chapter("第一章", "<p>一</p>", 1),
            EpubFixtures.chapter("第二章", "<p>二</p>", 2),
        )

        val CONTAINER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
            </container>
        """.trimIndent()
    }
}
