package io.github.galizhajidanbaofan.plainpage.domain.engine.epub

import java.io.File
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 测试用的 EPUB 生成器。
 *
 * 为什么在测试里现造而不是往仓库塞一个二进制 fixture：
 * - 改动一个字符就能验一个新场景，不用二进制 diff；
 * - 仓库里不留来源不明的版权样本。
 */
internal object EpubFixtures {

    data class Chapter(val title: String, val body: String, val file: String)

    fun chapter(title: String, body: String, index: Int): Chapter =
        Chapter(title = title, body = body, file = "Text/ch$index.xhtml")

    /** 造一个结构完整的 EPUB2（可选带 nav），返回文件。 */
    fun build(
        directory: File,
        title: String? = "冬夜札记",
        author: String? = "测试作者",
        chapters: List<Chapter> = listOf(chapter("第一章 雪夜", "<p>那年冬天来得格外早。</p>", 1)),
        withNcx: Boolean = true,
        withNav: Boolean = false,
        /** 相对 OPF 目录的封面图路径；给了就按 EPUB3 的 properties="cover-image" 声明。 */
        coverEntry: String? = null,
        extraEntries: Map<String, ByteArray> = emptyMap(),
    ): File {
        val opfDir = "OEBPS"
        val manifest = StringBuilder()
        val spine = StringBuilder()
        chapters.forEachIndexed { index, chapter ->
            manifest.appendLine(
                """    <item id="ch${index + 1}" href="${chapter.file}" media-type="application/xhtml+xml"/>""",
            )
            spine.appendLine("""    <itemref idref="ch${index + 1}"/>""")
        }
        if (withNcx) {
            manifest.appendLine("""    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""")
        }
        if (withNav) {
            manifest.appendLine(
                """    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""",
            )
        }
        if (coverEntry != null) {
            manifest.appendLine(
                """    <item id="cover-image" href="$coverEntry" media-type="image/jpeg" properties="cover-image"/>""",
            )
        }

        val metadata = buildString {
            appendLine("""  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">""")
            if (title != null) appendLine("""    <dc:title>$title</dc:title>""")
            if (author != null) appendLine("""    <dc:creator>$author</dc:creator>""")
            appendLine("  </metadata>")
        }

        val opf = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">""")
            append(metadata)
            appendLine("  <manifest>")
            append(manifest)
            appendLine("  </manifest>")
            appendLine("""  <spine${if (withNcx) " toc=\"ncx\"" else ""}>""")
            append(spine)
            appendLine("  </spine>")
            appendLine("</package>")
        }

        val entries = LinkedHashMap<String, ByteArray>()
        entries["META-INF/container.xml"] = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="$opfDir/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent().toByteArray()

        entries["$opfDir/content.opf"] = opf.toByteArray()

        // 额外文件（插图等）路径相对 OPF 目录，和它们被引用的方式保持一致。
        extraEntries.forEach { (path, bytes) -> entries["$opfDir/$path"] = bytes }

        chapters.forEach { chapter ->
            entries["$opfDir/${chapter.file}"] = """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><title>${chapter.title}</title></head>
                  <body>${chapter.body}</body>
                </html>
            """.trimIndent().toByteArray()
        }

        if (withNcx) {
            // 必须用 buildString 逐行拼：如果用 trimIndent + 插值，
            // 插值块里 0 缩进的行会把最小缩进拉到 0，"<?xml" 前就留下空白，
            // SAX 会报 "Content is not allowed in prolog"。
            val ncx = buildString {
                appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
                appendLine("""<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">""")
                appendLine("  <navMap>")
                chapters.forEachIndexed { index, chapter ->
                    appendLine("""    <navPoint id="np${index + 1}" playOrder="${index + 1}">""")
                    appendLine("""      <navLabel><text>${chapter.title}</text></navLabel>""")
                    appendLine("""      <content src="${chapter.file}"/>""")
                    appendLine("""    </navPoint>""")
                }
                appendLine("  </navMap>")
                appendLine("</ncx>")
            }
            entries["$opfDir/toc.ncx"] = ncx.toByteArray()
        }

        if (withNav) {
            val nav = buildString {
                appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
                appendLine("""<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">""")
                appendLine("  <body>")
                appendLine("""    <nav epub:type="toc"><ol>""")
                chapters.forEach { chapter ->
                    appendLine("""      <li><a href="${chapter.file}">${chapter.title}</a></li>""")
                }
                appendLine("    </ol></nav>")
                appendLine("  </body>")
                appendLine("</html>")
            }
            entries["$opfDir/nav.xhtml"] = nav.toByteArray()
        }

        return writeZip(directory, "fixture-${UUID.randomUUID()}.epub", entries, withMimetype = true)
    }

    /** 直接按「条目名 → 字节」写一个 zip，用于构造畸形文件。 */
    fun writeZip(
        directory: File,
        fileName: String,
        entries: Map<String, ByteArray>,
        withMimetype: Boolean = false,
    ): File {
        directory.mkdirs()
        val file = File(directory, fileName)
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            if (withMimetype) {
                val bytes = "application/epub+zip".toByteArray()
                val crc = CRC32().apply { update(bytes) }
                zip.putNextEntry(
                    ZipEntry("mimetype").apply {
                        method = ZipEntry.STORED
                        size = bytes.size.toLong()
                        compressedSize = bytes.size.toLong()
                        this.crc = crc.value
                    },
                )
                zip.write(bytes)
                zip.closeEntry()
            }
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }
}
