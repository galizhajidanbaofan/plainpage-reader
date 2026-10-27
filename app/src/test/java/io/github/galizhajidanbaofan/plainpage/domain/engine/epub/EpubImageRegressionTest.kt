package io.github.galizhajidanbaofan.plainpage.domain.engine.epub

import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.domain.model.BookFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 「书能打开、字能读，但图一张都不显示」的回归集。
 *
 * 这一组用例全部来自真实 EPUB 的常见写法，而不是我们自己造的漂亮 fixture：
 * - 插图包在 `<svg><image xlink:href="..."/></svg>` 里（EPUB3 标准做法，也常见于 EPUB2 转换器产物）；
 * - href 大小写与 zip 条目大小写不一致（Windows 上打包、Linux 上阅读的典型坑）；
 * - 封面只写在 OPF 里，任何正文 XHTML 都不引用它。
 */
class EpubImageRegressionTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** 索引层会筛掉「小图」与「装饰图」，所以测试图要够大、够像素。 */
    private fun pngBytes(): ByteArray = EpubFixtures.bigPng()

    private fun book(path: String) = Book(
        id = 1L,
        title = "回归",
        format = BookFormat.EPUB,
        localPath = path,
        sourceUri = "content://test/book",
        importedAt = 0L,
    )

    private fun open(file: File): EpubReaderEngine = runBlocking {
        EpubReaderEngine().apply { open(book(file.absolutePath)) }
    }

    @Test
    fun svgWrappedImageIsCaptured() {
        val file = EpubFixtures.build(
            temp.root,
            chapters = listOf(
                EpubFixtures.chapter(
                    "第一章",
                    """
                    <p>甲一，雪落下来。</p>
                    <div class="illustration">
                      <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"
                           viewBox="0 0 600 800" width="600" height="800">
                        <image xlink:href="../Images/snow.jpg" width="600" height="800"/>
                      </svg>
                    </div>
                    <p>乙二，雪还在下。</p>
                    """.trimIndent(),
                    1,
                ),
            ),
            extraEntries = mapOf("Images/snow.jpg" to pngBytes()),
        )

        val engine = open(file)
        try {
            assertEquals(1, engine.pictures.size)
            assertEquals("OEBPS/Images/snow.jpg", engine.pictures.first().path)
        } finally {
            engine.close()
        }
    }

    @Test
    fun zipEntryCaseMismatchStillReadsBytes() {
        // href 写小写，压缩包里的条目是大写——按精确匹配取不到，必须有小写兜底。
        val file = EpubFixtures.build(
            temp.root,
            chapters = listOf(
                EpubFixtures.chapter("第一章", """<p>甲一。</p><img src="../images/COVER.JPG"/>""", 1),
            ),
            extraEntries = mapOf("Images/Cover.jpg" to pngBytes()),
        )

        val engine = open(file)
        try {
            assertEquals(1, engine.pictures.size)
            val bytes = runBlocking { engine.pictureBytes(engine.pictures.first().path) }
            assertNotNull("大小写不一致时也必须取得到字节", bytes)
            assertTrue(bytes!!.isNotEmpty())
        } finally {
            engine.close()
        }
    }

    @Test
    fun opfCoverImageBecomesFirstPicture() {
        val file = EpubFixtures.build(
            temp.root,
            chapters = listOf(
                EpubFixtures.chapter("第一章", "<p>甲一，冬天来得早。</p>", 1),
            ),
            coverEntry = "Images/cover.jpg",
            extraEntries = mapOf("Images/cover.jpg" to pngBytes()),
        )

        val engine = open(file)
        try {
            val cover = engine.pictures.firstOrNull { it.path.endsWith("cover.jpg") }
            assertNotNull("OPF 里声明的封面必须被认出来", cover)
            assertEquals("封面排在最前面", 0, engine.pictures.first().at)
        } finally {
            engine.close()
        }
    }

    @Test
    fun coverReferencedInBodyIsNotDuplicated() {
        val file = EpubFixtures.build(
            temp.root,
            chapters = listOf(
                EpubFixtures.chapter(
                    "封面",
                    """<p>封面页</p><img src="../Images/cover.jpg"/>""",
                    1,
                ),
            ),
            coverEntry = "Images/cover.jpg",
            extraEntries = mapOf("Images/cover.jpg" to pngBytes()),
        )

        val engine = open(file)
        try {
            assertEquals(
                "正文已经引过封面时不要再补一张",
                1,
                engine.pictures.count { it.path.endsWith("cover.jpg") },
            )
        } finally {
            engine.close()
        }
    }

    @Test
    fun imageOnlyPageStillContributesItsPicture() {
        // 整页插图页：正文一个字都没有，只有 svg + image。
        // 早先按「正文为空」整章跳过，结果这些页上的图连锚点一起消失。
        val file = EpubFixtures.build(
            temp.root,
            chapters = listOf(
                EpubFixtures.chapter("插页", """<svg xmlns="http://www.w3.org/2000/svg"><image xlink:href="../Images/plate.jpg"/></svg>""", 1),
                EpubFixtures.chapter("第一章", "<p>甲一，雪落下来。</p>", 2),
            ),
            extraEntries = mapOf("Images/plate.jpg" to pngBytes()),
        )

        val engine = open(file)
        try {
            val plate = engine.pictures.firstOrNull { it.path.endsWith("plate.jpg") }
            assertNotNull("纯图页的插图不能被丢掉", plate)
        } finally {
            engine.close()
        }
    }

    @Test
    fun tinyDecorationIsSkipped() {
        val file = EpubFixtures.build(
            temp.root,
            chapters = listOf(
                EpubFixtures.chapter(
                    "第一章",
                    """<p>甲一。</p><img src="../Images/ornament.png"/><p>乙二。</p>""",
                    1,
                ),
            ),
            extraEntries = mapOf("Images/ornament.png" to EpubFixtures.bigPng(width = 40, height = 40)),
        )

        val engine = open(file)
        try {
            assertTrue(
                "40×40 的分隔线不该占一整屏",
                engine.pictures.none { it.path.endsWith("ornament.png") },
            )
        } finally {
            engine.close()
        }
    }

    @Test
    fun repeatedPathIsCountedOnce() {
        // 有的书把同一张装饰图在每章重复引用上百次；按路径去重后只保留第一次出现的位置。
        val body = (1..30).joinToString("") { """<p>第 $it 段。</p><img src="../Images/stamp.png"/>""" }
        val file = EpubFixtures.build(
            temp.root,
            chapters = listOf(EpubFixtures.chapter("第一章", body, 1)),
            extraEntries = mapOf("Images/stamp.png" to pngBytes()),
        )

        val engine = open(file)
        try {
            assertEquals(1, engine.pictures.count { it.path.endsWith("stamp.png") })
        } finally {
            engine.close()
        }
    }

    @Test
    fun epub2MetaCoverIsFound() {
        val container = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent()

        // EPUB2 的封面声明：<meta name="cover" content="封面在 manifest 里的 id"/>
        val opf = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">""")
            appendLine("""  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">""")
            appendLine("""    <dc:title>只有元数据封面</dc:title>""")
            appendLine("""    <meta name="cover" content="cov"/>""")
            appendLine("""  </metadata>""")
            appendLine("""  <manifest>""")
            appendLine("""    <item id="ch1" href="Text/ch1.xhtml" media-type="application/xhtml+xml"/>""")
            appendLine("""    <item id="cov" href="Images/meta-cover.jpg" media-type="image/jpeg"/>""")
            appendLine("""  </manifest>""")
            appendLine("""  <spine><itemref idref="ch1"/></spine>""")
            appendLine("""</package>""")
        }

        val file = EpubFixtures.writeZip(
            temp.root,
            "meta-cover.epub",
            mapOf(
                "META-INF/container.xml" to container.toByteArray(),
                "OEBPS/content.opf" to opf.toByteArray(),
                "OEBPS/Text/ch1.xhtml" to
                    """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml"><body><p>正文</p></body></html>"""
                        .toByteArray(),
                "OEBPS/Images/meta-cover.jpg" to pngBytes(),
            ),
        )

        val engine = open(file)
        try {
            assertEquals(1, engine.pictures.size)
            assertTrue(engine.pictures.first().path.endsWith("meta-cover.jpg"))
        } finally {
            engine.close()
        }
    }
}
