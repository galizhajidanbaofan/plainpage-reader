package io.github.galizhajidanbaofan.plainpage.domain.engine.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubPackageParserTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `解析标题作者与 spine 顺序`() {
        val file = EpubFixtures.build(
            temp.root,
            title = "冬夜札记",
            author = "张三",
            chapters = listOf(
                EpubFixtures.chapter("第一章", "<p>一</p>", 1),
                EpubFixtures.chapter("第二章", "<p>二</p>", 2),
            ),
        )
        EpubArchive(file).use { archive ->
            val info = EpubPackageParser.read(archive)
            assertEquals("冬夜札记", info.title)
            assertEquals("张三", info.author)
            assertEquals(
                listOf("OEBPS/Text/ch1.xhtml", "OEBPS/Text/ch2.xhtml"),
                info.spine,
            )
            assertEquals("OEBPS/toc.ncx", info.ncxPath)
            assertNull(info.navPath)
        }
    }

    @Test
    fun `识别 EPUB3 nav 与缺失的 NCX`() {
        val file = EpubFixtures.build(temp.root, withNcx = false, withNav = true)
        EpubArchive(file).use { archive ->
            val info = EpubPackageParser.read(archive)
            assertNull(info.ncxPath)
            assertEquals("OEBPS/nav.xhtml", info.navPath)
        }
    }

    @Test
    fun `linear 为 no 的资源不进阅读顺序`() {
        val file = writeCustomPackage(
            """
            <itemref idref="ch1"/>
            <itemref idref="notes" linear="no"/>
            """.trimIndent(),
        )
        EpubArchive(file).use { archive ->
            val info = EpubPackageParser.read(archive)
            assertEquals(listOf("OEBPS/Text/ch1.xhtml"), info.spine)
        }
    }

    /**
     * EPUB2 的老写法：`<meta name="cover" content="封面 item 的 id"/>`。
     *
     * 真实书里 content 几乎总是 manifest 的 item id（不是 href），
     * 而且这个 id 经常长得像文件名（例如 `x9784049130829_1_7.png`）——
     * 顺手当成 href 去拼路径也能"偶尔凑巧拼对"，所以这里刻意让两者不同。
     */
    @Test
    fun `EPUB2 的 meta cover 按 manifest id 解析成图片路径`() {
        val file = EpubFixtures.build(
            temp.root,
            coverEntry = "Images/cover.png",
            coverMetaId = "cover-x1",
            extraEntries = mapOf("Images/cover.png" to ByteArray(16) { 1 }),
        )
        EpubArchive(file).use { archive ->
            val info = EpubPackageParser.read(archive)
            assertEquals("OEBPS/Images/cover.png", info.coverImage)
        }
    }

    @Test
    fun `meta cover 指向不存在的 id 时不崩且无封面`() {
        val file = EpubFixtures.build(
            temp.root,
            coverEntry = "Images/cover.png",
            coverMetaId = "cover-x1",
            extraEntries = mapOf("Images/cover.png" to ByteArray(16) { 1 }),
        )
        // 手工把 id 改坏：content 指向一个 manifest 里没有的 id，同时也当不了 href。
        val broken = java.io.File(temp.root, "broken-${System.nanoTime()}.epub")
        java.util.zip.ZipFile(file).use { zip ->
            java.util.zip.ZipOutputStream(broken.outputStream()).use { out ->
                zip.entries().asSequence().forEach { entry ->
                    out.putNextEntry(java.util.zip.ZipEntry(entry.name))
                    val bytes = zip.getInputStream(entry).readBytes()
                    out.write(
                        if (entry.name == "OEBPS/content.opf") {
                            String(bytes, Charsets.UTF_8)
                                .replace("content=\"cover-x1\"", "content=\"no-such-item\"")
                                .toByteArray()
                        } else {
                            bytes
                        },
                    )
                    out.closeEntry()
                }
            }
        }
        EpubArchive(broken).use { archive ->
            assertNull(EpubPackageParser.read(archive).coverImage)
        }
    }

    @Test
    fun `缺少 container 抛 CORRUPTED`() {
        val file = EpubFixtures.writeZip(temp.root, "nocontainer.epub", mapOf("OEBPS/content.opf" to "<package/>".toByteArray()))
        val error = formatError(file)
        assertEquals(EpubFormatException.Kind.CORRUPTED, error.kind)
    }

    @Test
    fun `container 指向的 OPF 不存在时抛 CORRUPTED`() {
        val file = EpubFixtures.writeZip(
            temp.root,
            "noopf.epub",
            mapOf("META-INF/container.xml" to containerXml("OEBPS/missing.opf").toByteArray()),
        )
        assertEquals(EpubFormatException.Kind.CORRUPTED, formatError(file).kind)
    }

    @Test
    fun `spine 为空且没有可用的 xhtml 时抛 EMPTY_CONTENT`() {
        val file = writeCustomPackage(
            spineItems = "",
            manifestItems = """<item id="css" href="style.css" media-type="text/css"/>""",
            withChapters = false,
        )
        assertEquals(EpubFormatException.Kind.EMPTY_CONTENT, formatError(file).kind)
    }

    @Test
    fun `OPF 不是合法 XML 时抛 CORRUPTED`() {
        val file = EpubFixtures.writeZip(
            temp.root,
            "badxml.epub",
            mapOf(
                "META-INF/container.xml" to containerXml("OEBPS/content.opf").toByteArray(),
                "OEBPS/content.opf" to "<package><manifest></package>".toByteArray(),
            ),
        )
        assertEquals(EpubFormatException.Kind.CORRUPTED, formatError(file).kind)
    }

    private fun formatError(file: java.io.File): EpubFormatException {
        val error = runCatching { EpubArchive(file).use { EpubPackageParser.read(it) } }.exceptionOrNull()
        assertTrue("期望抛 EpubFormatException，实际：$error", error is EpubFormatException)
        return error as EpubFormatException
    }

    private fun containerXml(opfPath: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="$opfPath" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
    """.trimIndent()

    private fun writeCustomPackage(
        spineItems: String,
        manifestItems: String = DEFAULT_MANIFEST,
        withChapters: Boolean = true,
    ): java.io.File {
        // buildString 而不是 trimIndent：插值块里的 0 缩进行会把最小缩进拉到 0，
        // 让 "<?xml" 前面留空白，SAX 直接报 prolog 错误。
        val opf = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">""")
            appendLine("""  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>定制</dc:title></metadata>""")
            appendLine("  <manifest>")
            appendLine(manifestItems)
            appendLine("  </manifest>")
            appendLine("  <spine>")
            appendLine(spineItems)
            appendLine("  </spine>")
            appendLine("</package>")
        }
        val entries = LinkedHashMap<String, ByteArray>()
        entries["META-INF/container.xml"] = containerXml("OEBPS/content.opf").toByteArray()
        entries["OEBPS/content.opf"] = opf.toByteArray()
        if (withChapters) {
            entries["OEBPS/Text/ch1.xhtml"] = "<html><body><p>正文</p></body></html>".toByteArray()
            entries["OEBPS/Text/notes.xhtml"] = "<html><body><p>注释</p></body></html>".toByteArray()
        }
        return EpubFixtures.writeZip(temp.root, "custom-${System.nanoTime()}.epub", entries)
    }

    private companion object {
        val DEFAULT_MANIFEST = """
            <item id="ch1" href="Text/ch1.xhtml" media-type="application/xhtml+xml"/>
            <item id="notes" href="Text/notes.xhtml" media-type="application/xhtml+xml"/>
        """.trimIndent()
    }
}
