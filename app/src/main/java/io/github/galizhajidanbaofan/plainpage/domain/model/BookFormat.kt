package io.github.galizhajidanbaofan.plainpage.domain.model

/**
 * P0 支持的三种格式。扩展名匹配不区分大小写；未知扩展名返回 null，由导入层提示用户。
 */
enum class BookFormat(
    val extension: String,
    val mimeType: String,
) {
    EPUB("epub", "application/epub+zip"),
    TXT("txt", "text/plain"),
    PDF("pdf", "application/pdf"),
    ;

    companion object {
        fun fromFileName(fileName: String): BookFormat? {
            val ext = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
            return entries.firstOrNull { it.extension == ext }
        }
    }
}
