package io.github.galizhajidanbaofan.plainpage.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.domain.model.BookFormat
import io.github.galizhajidanbaofan.plainpage.domain.model.ReadingLocation
import io.github.galizhajidanbaofan.plainpage.domain.model.ReadingLocationCodec

/**
 * 书籍表。刻意存**原始字符串**而不是把 [ReadingLocation] 拆成多张表：
 * 三种格式的定位字段完全不同，拆表会让查询变复杂却得不到好处。
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val author: String?,
    /** 存 [BookFormat] 的枚举名，而非序数——序数会随枚举顺序变化而错位。 */
    val format: String,
    val localPath: String,
    val sourceUri: String,
    val coverPath: String?,
    val byteSize: Long,
    val importedAt: Long,
    val lastOpenedAt: Long?,
    /** [ReadingLocationCodec] 编码后的进度，null 表示还没读过。 */
    val readingLocation: String?,
)

fun BookEntity.toDomain(): Book? {
    val bookFormat = BookFormat.entries.firstOrNull { it.name == format } ?: return null
    return Book(
        id = id,
        title = title,
        author = author,
        format = bookFormat,
        localPath = localPath,
        sourceUri = sourceUri,
        coverPath = coverPath,
        byteSize = byteSize,
        importedAt = importedAt,
        lastOpenedAt = lastOpenedAt,
        readingLocation = ReadingLocationCodec.decode(readingLocation),
    )
}

fun Book.toEntity(): BookEntity = BookEntity(
    id = id,
    title = title,
    author = author,
    format = format.name,
    localPath = localPath,
    sourceUri = sourceUri,
    coverPath = coverPath,
    byteSize = byteSize,
    importedAt = importedAt,
    lastOpenedAt = lastOpenedAt,
    readingLocation = readingLocation?.let(ReadingLocationCodec::encode),
)
