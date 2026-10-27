package io.github.galizhajidanbaofan.plainpage.domain.engine.epub

import io.github.galizhajidanbaofan.plainpage.domain.engine.txt.TextEncodingDetector
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * EPUB 的 zip 容器。
 *
 * 只做三件事：列条目、按归一化路径取字节、按条目自带编码解码成文本。
 * 不缓存全文、不预先解压——一本书按需读，大 EPUB 不会在 open 阶段把内存拉满。
 */
internal class EpubArchive(private val file: File) : Closeable {

    private val zip: ZipFile = try {
        ZipFile(file)
    } catch (error: IOException) {
        throw EpubFormatException(EpubFormatException.Kind.CORRUPTED, "不是有效的 EPUB（zip 结构损坏）", error)
    }

    /** 归一化路径 → 条目。重名条目保留先出现的那个，与阅读器的通行行为一致。 */
    private val entries: Map<String, ZipEntry> = buildMap {
        val iterator = zip.entries()
        while (iterator.hasMoreElements()) {
            val entry = iterator.nextElement()
            if (entry.isDirectory) continue
            val path = ZipPaths.normalize(entry.name) ?: continue
            putIfAbsent(path, entry)
        }
    }

    /** 小写路径 → 条目。真实 EPUB 里 href 写 `Cover.JPG`、zip 条目叫 `cover.jpg` 的事很常见。 */
    private val lowercase: Map<String, ZipEntry> = buildMap {
        // 必须显式限定 `this@EpubArchive.entries`：buildMap 的 lambda 接收者本身是个 MutableMap，
        // 裸写 `entries` 会解析成「正在构建的这张空表」而不是类里的条目表，
        // 结果小写表永远是空的——图还是取不到，而且编译期一点提示都没有。
        this@EpubArchive.entries.forEach { (path, entry) -> putIfAbsent(path.lowercase(Locale.ROOT), entry) }
    }

    val entryCount: Int get() = entries.size

    /** 先精确匹配；对不上再按小写试一次。两次都找不到才算缺文件。 */
    private fun entryFor(path: String): ZipEntry? =
        entries[path] ?: lowercase[path.lowercase(Locale.ROOT)]

    fun contains(path: String): Boolean = entryFor(path) != null

    /** 条目声明的大小；不存在返回 null。用来在解压前筛掉明显是项目符号的小图。 */
    fun sizeOf(path: String): Long? = entryFor(path)?.size

    /** 不存在返回 null；超过 [maxBytes] 视为异常文件，抛 TOO_LARGE 而不是硬读。 */
    fun readBytesIfPresent(path: String, maxBytes: Int): ByteArray? {
        val entry = entryFor(path) ?: return null
        if (entry.size > maxBytes) {
            throw EpubFormatException(
                EpubFormatException.Kind.TOO_LARGE,
                "EPUB 内部文件过大：$path",
            )
        }
        return zip.getInputStream(entry).use { input ->
            // 声明大小可能造假（zip bomb），读取时再按实际字节数卡一次。
            // 不用 InputStream.readNBytes：它到 Android API 33 才有，minSdk 26 上会 NoSuchMethodError。
            val buffer = ByteArray(DEFAULT_BUFFER)
            val sink = java.io.ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) {
                    throw EpubFormatException(
                        EpubFormatException.Kind.TOO_LARGE,
                        "EPUB 内部文件过大：$path",
                    )
                }
                sink.write(buffer, 0, read)
            }
            sink.toByteArray()
        }
    }

    /**
     * 只读文件头部的 [maxBytes] 个字节，用于判断图片尺寸这类"看一眼就够"的场景。
     *
     * 与 [readBytesIfPresent] 的区别：**不会因为文件大就抛异常**。插图动辄几 MB，
     * 但我们只需要前几十 KB 的头部字段。
     */
    fun readPrefixIfPresent(path: String, maxBytes: Int): ByteArray? {
        val entry = entryFor(path) ?: return null
        return zip.getInputStream(entry).use { input ->
            val buffer = ByteArray(maxBytes)
            var total = 0
            while (total < maxBytes) {
                val read = input.read(buffer, total, maxBytes - total)
                if (read < 0) break
                total += read
            }
            buffer.copyOf(total)
        }
    }

    fun readBytes(path: String, maxBytes: Int): ByteArray =
        readBytesIfPresent(path, maxBytes)
            ?: throw EpubFormatException(EpubFormatException.Kind.CORRUPTED, "EPUB 缺少文件：$path")

    /** 按内容识别编码后解码；编码识别失败时有损解码，不让一个坏文件把整本书卡死。 */
    fun readTextIfPresent(path: String, maxBytes: Int): String? {
        val bytes = readBytesIfPresent(path, maxBytes) ?: return null
        val detected = TextEncodingDetector.detect(bytes)
        return String(
            bytes,
            detected.bomLength,
            bytes.size - detected.bomLength,
            detected.encoding.charset,
        )
    }

    override fun close() {
        runCatching { zip.close() }
    }

    private companion object {
        const val DEFAULT_BUFFER = 64 * 1024
    }
}
