package io.github.galizhajidanbaofan.plainpage.domain.model

/**
 * 书架上的一本书。
 *
 * [localPath] 指向应用私有目录内的副本（导入时复制，原文件不动）；
 * [sourceUri] 只是"这本书从哪来"的记录，用于溯源与去重提示，不作为读取入口。
 */
data class Book(
    val id: Long = 0L,
    val title: String,
    val author: String? = null,
    val format: BookFormat,
    val localPath: String,
    val sourceUri: String,
    val coverPath: String? = null,
    val byteSize: Long = 0L,
    val importedAt: Long,
    val lastOpenedAt: Long? = null,
    val readingLocation: ReadingLocation? = null,
)
