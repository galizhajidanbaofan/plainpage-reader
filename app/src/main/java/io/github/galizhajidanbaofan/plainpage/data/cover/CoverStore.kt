package io.github.galizhajidanbaofan.plainpage.data.cover

import android.graphics.Bitmap
import androidx.core.graphics.scale
import android.graphics.BitmapFactory
import io.github.galizhajidanbaofan.plainpage.domain.engine.epub.EpubArchive
import io.github.galizhajidanbaofan.plainpage.domain.engine.epub.EpubLimits
import io.github.galizhajidanbaofan.plainpage.domain.engine.epub.EpubPackageParser
import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 书架封面的缓存：从 EPUB 里抽出封面图，缩到书架够用的尺寸后另存成 JPEG。
 *
 * 为什么不在书架里直接读 EPUB 解压：
 * 1. 一本书的 ZIP 句柄不能长时间开着（书架可能有几百本）；
 * 2. EPUB 里的封面常是 2000px 宽的原图，缩略图只需要 400px 上下，每次进书架都解一遍会白烧内存；
 * 3. 封面图必须能在书被移出书架时一起清掉——独立文件比"再进压缩包删条目"简单得多。
 *
 * 文件命名规则：[coverFileFor] 由书籍文件路径派生，所以不需要额外的索引文件，
 * 数据库里的 `coverPath` 只是缓存下来的结果（用它省掉一次 exists() 判断）。
 */
class CoverStore(private val filesDir: File) {

    private val cache = object : LinkedHashMap<String, Bitmap>(CACHE_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean =
            size > CACHE_ENTRIES
    }

    /** 书封缓存位置：与书籍文件同目录、同名前缀。 */
    fun coverFileFor(bookFile: File): File {
        val stem = bookFile.name.substringBeforeLast('.')
        return File(bookFile.parentFile ?: filesDir, "$stem.cover.jpg")
    }

    /**
     * 从 EPUB 抽封面。抽不到就返回 null——封面只是装饰，不能因为它拖垮导入。
     *
     * 全程在 IO 线程；写入用 `.part` 再改名，进程被杀不会留下半张图。
     */
    suspend fun extractFromEpub(bookFile: File): String? = withContext(Dispatchers.IO) {
        val bitmap = try {
            EpubArchive(bookFile).use { archive ->
                val pkg = EpubPackageParser.read(archive)
                val coverPath = pkg.coverImage ?: return@use null
                val bytes = archive.readBytesIfPresent(coverPath, EpubLimits.MAX_PICTURE_BYTES)
                    ?: return@use null
                decodeDownscaled(bytes, TARGET_WIDTH_PX)
            }
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            // 损坏的 EPUB、解压炸弹、BitmapFactory 在异常字节上抛的 anything：
            // 封面失败不影响这本书能不能读，所以这里吞掉。
            null
        } ?: return@withContext null

        val target = coverFileFor(bookFile)
        val temp = File(target.parentFile, "${target.name}.part")
        try {
            temp.outputStream().buffered().use { sink ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, sink)) {
                    throw IOException("compress failed")
                }
            }
            if (!temp.renameTo(target)) throw IOException("rename failed")
            target.absolutePath
        } catch (_: IOException) {
            temp.delete()
            null
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * 读书架上的封面。[path] 为空或文件不在时返回 null，调用方回退到占位样式。
     *
     * 返回的 Bitmap 归缓存所有，调用方**不要 recycle**。
     */
    suspend fun load(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        synchronized(cache) { cache[path] }?.let { if (!it.isRecycled) return it }
        return withContext(Dispatchers.IO) {
            val file = File(path)
            if (!file.isFile) return@withContext null
            val bitmap = try {
                decodeDownscaled(file.readBytes(), TARGET_WIDTH_PX)
            } catch (_: IOException) {
                null
            } catch (_: OutOfMemoryError) {
                null
            } ?: return@withContext null
            synchronized(cache) { cache[path] = bitmap }
            bitmap
        }
    }

    /** 书被移出书架时把封面文件一起清掉；清不掉也无所谓，大不了下次导入再写一张。 */
    suspend fun deleteFor(book: Book) = withContext(Dispatchers.IO) {
        val paths = buildList {
            book.coverPath?.let(::add)
            add(coverFileFor(File(book.localPath)).absolutePath)
        }
        paths.distinct().forEach { path ->
            synchronized(cache) { cache.remove(path) }
            runCatching { File(path).delete() }
        }
        Unit
    }

    private fun decodeDownscaled(bytes: ByteArray, targetWidthPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        if (width <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(width, targetWidthPx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        if (decoded.width <= targetWidthPx) return decoded

        val height = (decoded.height.toLong() * targetWidthPx / decoded.width).toInt().coerceAtLeast(1)
        val scaled = decoded.scale(targetWidthPx, height)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun sampleSizeFor(width: Int, targetWidthPx: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= targetWidthPx) sample *= 2
        return sample
    }

    private companion object {
        /** 书架列表封面约 52dp、网格约 150dp；按 3x 屏取 400px 足够，再大就是浪费。 */
        const val TARGET_WIDTH_PX = 400
        const val JPEG_QUALITY = 85
        const val CACHE_ENTRIES = 24
    }
}
