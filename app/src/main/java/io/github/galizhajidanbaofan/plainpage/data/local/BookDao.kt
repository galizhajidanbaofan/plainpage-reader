package io.github.galizhajidanbaofan.plainpage.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    /**
     * 书架排序：打开过的按最近打开时间倒序在前，没打开过的按导入时间倒序随后。
     * SQLite 里 `lastOpenedAt IS NULL` 求值为 0/1，升序即「非空在前」。
     */
    @Query(
        """
        SELECT * FROM books
        ORDER BY (lastOpenedAt IS NULL) ASC, lastOpenedAt DESC, importedAt DESC
        """,
    )
    fun observeShelf(): Flow<List<BookEntity>>

    @Query(
        """
        SELECT * FROM books
        WHERE lastOpenedAt IS NOT NULL
        ORDER BY lastOpenedAt DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun findById(id: Long): BookEntity?

    /** 单本书的响应式读取：阅读页标题、进度变化都靠它，避免 UI 自己轮询。 */
    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: Long): Flow<BookEntity?>

    @Insert
    suspend fun insert(entity: BookEntity): Long

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE books SET lastOpenedAt = :atMillis WHERE id = :id")
    suspend fun updateLastOpened(id: Long, atMillis: Long)

    @Query("UPDATE books SET readingLocation = :encodedLocation WHERE id = :id")
    suspend fun updateReadingLocation(id: Long, encodedLocation: String?)

    /** 封面缓存文件位置；null 表示这本书没有封面（TXT/PDF 或抽图失败）。 */
    @Query("UPDATE books SET coverPath = :coverPath WHERE id = :id")
    suspend fun updateCover(id: Long, coverPath: String?)
}
