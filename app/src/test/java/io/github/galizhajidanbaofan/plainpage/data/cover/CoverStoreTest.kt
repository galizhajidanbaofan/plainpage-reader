package io.github.galizhajidanbaofan.plainpage.data.cover

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import io.github.galizhajidanbaofan.plainpage.domain.engine.epub.EpubFixtures
import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.domain.model.BookFormat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/**
 * 书架封面缓存。
 *
 * 这里必须跑 Robolectric：`CoverStore` 依赖 `BitmapFactory` 与 `Bitmap.compress`，
 * 纯 JVM 单测里那些方法会抛 "not mocked"。图不用真图，现画一张再用 JPEG 编码即可。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CoverStoreTest {

    private lateinit var dir: File
    private lateinit var store: CoverStore

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("cover-store").toFile()
        store = CoverStore(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `从 EPUB2 的 meta cover 抽出封面并缩到书架尺寸`() = runBlocking {
        val epub = EpubFixtures.build(
            directory = dir,
            coverEntry = "Images/cover.png",
            coverMetaId = "cover-id",
            extraEntries = mapOf("Images/cover.png" to fakeImageBytes(width = 1600)),
        )

        val path = store.extractFromEpub(epub)

        assertNotNull(path)
        val file = File(path!!)
        assertTrue(file.isFile)
        assertTrue("封面文件应该有内容：${file.length()}", file.length() > 0)
        assertEquals(dir.absolutePath, file.parentFile?.absolutePath)

        val bitmap = store.load(path)
        assertNotNull(bitmap)
        assertEquals(CoverStoreTest.EXPECTED_WIDTH, bitmap!!.width)
        assertTrue(bitmap.height > 0)
    }

    @Test
    fun `没有封面的书返回 null 且不落文件`() = runBlocking {
        val epub = EpubFixtures.build(directory = dir)

        val path = store.extractFromEpub(epub)

        assertNull(path)
        assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".cover.jpg") })
    }

    @Test
    fun `封面字节不是图片时返回 null 而不是抛异常`() = runBlocking {
        val epub = EpubFixtures.build(
            directory = dir,
            coverEntry = "Images/broken.png",
            coverMetaId = "cover-id",
            extraEntries = mapOf("Images/broken.png" to "not an image".toByteArray()),
        )

        assertNull(store.extractFromEpub(epub))
    }

    @Test
    fun `移出书架时封面文件一并删除`() = runBlocking {
        val epub = EpubFixtures.build(
            directory = dir,
            coverEntry = "Images/cover.png",
            coverMetaId = "cover-id",
            extraEntries = mapOf("Images/cover.png" to fakeImageBytes(width = 800)),
        )
        val path = store.extractFromEpub(epub)!!
        assertTrue(File(path).isFile)

        val book = Book(
            id = 7,
            title = "冬夜札记",
            author = null,
            format = BookFormat.EPUB,
            localPath = epub.absolutePath,
            sourceUri = "content://docs/7",
            coverPath = path,
            byteSize = epub.length(),
            importedAt = 0L,
        )
        store.deleteFor(book)

        assertFalse(File(path).exists())
    }

    @Test
    fun `load 对空路径与不存在的文件都返回 null`() = runBlocking {
        assertNull(store.load(null))
        assertNull(store.load(""))
        assertNull(store.load(File(dir, "nope.jpg").absolutePath))
    }

    companion object {
        /** `CoverStore` 的目标宽度是 400px。 */
        const val EXPECTED_WIDTH = 400

        /** 现画一张纯色图再编码成 PNG，避免往仓库塞二进制样本。 */
        fun fakeImageBytes(width: Int, height: Int = width / 4 * 5): ByteArray {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(32, 48, 58))
            canvas.drawRect(
                width * 0.08f,
                height * 0.08f,
                width * 0.92f,
                height * 0.92f,
                Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = width * 0.01f
                    color = Color.rgb(200, 190, 160)
                },
            )
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            return out.toByteArray()
        }
    }
}
