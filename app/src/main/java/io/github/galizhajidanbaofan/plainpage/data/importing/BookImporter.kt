package io.github.galizhajidanbaofan.plainpage.data.importing

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import io.github.galizhajidanbaofan.plainpage.domain.importing.ImportRules
import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.domain.model.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/** 导入结果。失败必须能解释给用户看，所以拒绝原因是枚举而不是布尔值。 */
sealed interface ImportResult {
    data class Success(val book: Book) : ImportResult
    data class Rejected(val reason: ImportRejection) : ImportResult
}

enum class ImportRejection {
    /** 来源 URI 打不开（权限丢失、文件被删、provider 崩了）。 */
    SOURCE_UNAVAILABLE,

    /** 扩展名与 MIME 都不认识。 */
    UNSUPPORTED_FORMAT,

    /** 超过 [ImportRules.MAX_BYTES]。 */
    TOO_LARGE,

    /** 读到 0 字节。 */
    EMPTY_FILE,

    /** 落盘失败。 */
    IO_ERROR,
}

/**
 * 把 SAF 选中的文件复制进应用私有目录。
 *
 * 设计要点：
 * - **流式复制**，边读边累计字节数，超限立刻中止并删临时文件，不会先落一个 2GB 文件再发现超限；
 * - 先写 `.part` 再改名，进程被杀不会留下半截可读文件；
 * - 落盘名用随机 id，避免同名覆盖与奇怪字符；
 * - 全程 [Dispatchers.IO]，主线程只拿到结果。
 */
class BookImporter(
    private val contentResolver: ContentResolver,
    private val booksDir: File,
    private val maxBytes: Long = ImportRules.MAX_BYTES,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val meta = queryMeta(uri)
            ?: return@withContext ImportResult.Rejected(ImportRejection.SOURCE_UNAVAILABLE)

        val format = ImportRules.detectFormat(meta.displayName, meta.mimeType)
            ?: return@withContext ImportResult.Rejected(ImportRejection.UNSUPPORTED_FORMAT)

        // 元数据里已经声明大小的，先便宜地拒绝一次，省掉无谓的复制。
        if (meta.size != null && meta.size > maxBytes) {
            return@withContext ImportResult.Rejected(ImportRejection.TOO_LARGE)
        }

        if (!booksDir.isDirectory && !booksDir.mkdirs()) {
            return@withContext ImportResult.Rejected(ImportRejection.IO_ERROR)
        }

        val id = UUID.randomUUID().toString()
        val tempFile = File(booksDir, "$id.part")
        val targetFile = File(booksDir, ImportRules.storageFileName(id, format))

        val copied = try {
            copyStreaming(uri, tempFile)
        } catch (_: TooLargeException) {
            tempFile.delete()
            return@withContext ImportResult.Rejected(ImportRejection.TOO_LARGE)
        } catch (_: IOException) {
            tempFile.delete()
            return@withContext ImportResult.Rejected(ImportRejection.IO_ERROR)
        } catch (_: SecurityException) {
            tempFile.delete()
            return@withContext ImportResult.Rejected(ImportRejection.SOURCE_UNAVAILABLE)
        }

        if (copied == 0L) {
            tempFile.delete()
            return@withContext ImportResult.Rejected(ImportRejection.EMPTY_FILE)
        }

        if (!tempFile.renameTo(targetFile)) {
            tempFile.delete()
            return@withContext ImportResult.Rejected(ImportRejection.IO_ERROR)
        }

        ImportResult.Success(
            Book(
                title = ImportRules.titleFromFileName(meta.displayName),
                format = format,
                localPath = targetFile.absolutePath,
                sourceUri = uri.toString(),
                byteSize = copied,
                importedAt = now(),
            ),
        )
    }

    /** @return 实际写入的字节数。 */
    private fun copyStreaming(uri: Uri, target: File): Long {
        val input = contentResolver.openInputStream(uri) ?: throw IOException("openInputStream returned null")
        input.use { source ->
            target.outputStream().buffered(COPY_BUFFER).use { sink ->
                val buffer = ByteArray(COPY_BUFFER)
                var total = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw TooLargeException()
                    sink.write(buffer, 0, read)
                }
                return total
            }
        }
    }

    private fun queryMeta(uri: Uri): SourceMeta? = try {
        contentResolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                SourceMeta(
                    displayName = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else null,
                    size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null,
                    mimeType = contentResolver.getType(uri),
                )
            }
        }
    } catch (_: Exception) {
        // provider 抛什么都当「来源不可用」处理：导入失败可以解释，崩溃不行。
        null
    }

    private data class SourceMeta(
        val displayName: String?,
        val size: Long?,
        val mimeType: String?,
    )

    private class TooLargeException : RuntimeException("file exceeds max size")

    private companion object {
        const val COPY_BUFFER = 64 * 1024
        val PROJECTION = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
    }
}

/** 私有目录布局：`filesDir/books/`，与数据库记录一一对应。 */
fun booksDirectory(filesDir: File): File = File(filesDir, "books")

/**
 * 文件选择器的 MIME 过滤。
 *
 * 只用标准 MIME 而不加通配（星号斜杠星号）：加了等于不过滤，用户会选进一堆 zip/docx 再被拒。
 * 少数 provider 对 EPUB 报 `application/octet-stream`，那种情况下用户可能需要换个文件管理器；
 * 这比「任何文件都能选、然后统一报不支持」体验更好。
 */
val SUPPORTED_MIME_TYPES: Array<String> = arrayOf(
    "application/epub+zip",
    "text/plain",
    "application/pdf",
)

/** 供显示与排查用：这本书的格式是否被支持（不读文件，只看扩展名）。 */
fun BookFormat.matchesExtension(fileName: String): Boolean =
    fileName.substringAfterLast('.', "").equals(extension, ignoreCase = true)
