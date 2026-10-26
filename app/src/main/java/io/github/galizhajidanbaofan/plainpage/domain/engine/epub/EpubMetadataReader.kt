package io.github.galizhajidanbaofan.plainpage.domain.engine.epub

import io.github.galizhajidanbaofan.plainpage.domain.engine.BookMetadata
import java.io.File

/**
 * 导入时顺手取书名/作者：只解 container.xml + OPF，不碰正文。
 *
 * 书架上显示「红楼梦」而不是「hongloumeng_final_v3」，靠的就是这一步；
 * 失败一律返回 null，让调用方退回文件名——元数据缺失不该阻断导入。
 */
object EpubMetadataReader {

    fun read(file: File): BookMetadata? = runCatching {
        EpubArchive(file).use { archive ->
            val packageInfo = EpubPackageParser.read(archive)
            val title = packageInfo.title?.takeIf { it.isNotBlank() }
            if (title == null) {
                null
            } else {
                BookMetadata(title = title, author = packageInfo.author, pageCount = null)
            }
        }
    }.getOrNull()
}
