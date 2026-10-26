package io.github.galizhajidanbaofan.plainpage.domain.engine.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubMetadataReaderTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `读出标题与作者`() {
        val file = EpubFixtures.build(temp.root, title = "围城之外", author = "钱某某")
        val meta = EpubMetadataReader.read(file)
        assertEquals("围城之外", meta?.title)
        assertEquals("钱某某", meta?.author)
    }

    @Test
    fun `缺少 dc 标题时返回 null（调用方退回文件名）`() {
        val opf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
              <manifest><item id="ch1" href="Text/ch1.xhtml" media-type="application/xhtml+xml"/></manifest>
              <spine><itemref idref="ch1"/></spine>
            </package>
        """.trimIndent()
        val file = EpubFixtures.writeZip(
            temp.root,
            "nometa.epub",
            linkedMapOf(
                "META-INF/container.xml" to CONTAINER.toByteArray(),
                "OEBPS/content.opf" to opf.toByteArray(),
                "OEBPS/Text/ch1.xhtml" to "<html><body><p>正文</p></body></html>".toByteArray(),
            ),
        )
        assertNull(EpubMetadataReader.read(file))
    }

    @Test
    fun `损坏文件返回空元信息而不抛异常`() {
        val bad = temp.newFile("broken.epub").apply { writeText("不是 zip") }
        assertNull(EpubMetadataReader.read(bad))
    }

    @Test
    fun `文件不存在返回空元信息`() {
        assertNull(EpubMetadataReader.read(temp.root.resolve("missing.epub")))
    }

    private companion object {
        val CONTAINER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
            </container>
        """.trimIndent()
    }
}
